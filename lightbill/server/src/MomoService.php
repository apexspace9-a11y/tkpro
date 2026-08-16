<?php
declare(strict_types=1);
namespace LightBill;

final class MomoService
{
    private int $companyId;
    private string $partnerCode;
    private string $accessKey;
    private string $secretKey;
    private string $baseUrl;
    private string $redirectUrl;
    private string $ipnUrl;
    private string $env;

    public function __construct(int $companyId)
    {
        if ($companyId <= 0) {
            throw new \InvalidArgumentException('Doanh nghiệp không hợp lệ');
        }
        $this->companyId = $companyId;
        $this->partnerCode = trim((string) CompanySettings::get($companyId, 'momo.partner_code', Env::get('MOMO_PARTNER_CODE', '')));
        $this->accessKey = trim((string) CompanySettings::get($companyId, 'momo.access_key', Env::get('MOMO_ACCESS_KEY', '')));
        $this->secretKey = trim((string) CompanySettings::get($companyId, 'momo.secret_key', Env::get('MOMO_SECRET_KEY', '')));
        $this->env = strtolower(trim((string) CompanySettings::get($companyId, 'momo.environment', Env::get('MOMO_ENV', 'sandbox'))));
        if (!in_array($this->env, ['sandbox','production'], true)) {
            $this->env = 'sandbox';
        }
        if ($this->partnerCode === '' || $this->accessKey === '' || $this->secretKey === '') {
            Response::error('Chưa cấu hình thông tin tích hợp MoMo Business', 409);
        }
        $this->baseUrl = $this->env === 'production' ? 'https://payment.momo.vn' : 'https://test-payment.momo.vn';
        $appUrl = rtrim((string) Env::get('APP_URL', ''), '/');
        $this->redirectUrl = trim((string) CompanySettings::get($companyId, 'momo.redirect_url', Env::get('MOMO_REDIRECT_URL', $appUrl . '/momo/return')));
        $this->ipnUrl = trim((string) CompanySettings::get($companyId, 'momo.ipn_url', Env::get('MOMO_IPN_URL', $appUrl . '/api/momo/ipn')));
        if (!$this->isHttpsUrl($this->redirectUrl) || !$this->isHttpsUrl($this->ipnUrl)) {
            Response::error('URL MoMo phải sử dụng HTTPS', 422);
        }
    }

    public static function fromIpn(array $payload): self
    {
        $extra = (string)($payload['extraData'] ?? '');
        $decoded = base64_decode($extra, true);
        $meta = $decoded !== false ? json_decode($decoded, true) : null;
        $companyId = is_array($meta) ? (int)($meta['companyId'] ?? 0) : 0;
        if ($companyId <= 0) {
            throw new \RuntimeException('Không xác định được doanh nghiệp từ IPN');
        }
        return new self($companyId);
    }

    public function createPayment(array $user, int $amount, ?int $saleId = null, ?string $orderInfo = null): array
    {
        if ((int)$user['company_id'] !== $this->companyId) {
            Response::error('Doanh nghiệp không hợp lệ', 403);
        }
        if ($amount < 1000 || $amount > 50000000) {
            Response::error('Số tiền MoMo nằm ngoài giới hạn cấu hình', 422);
        }
        $pdo = DB::pdo();
        $orderId = 'LB' . date('YmdHis') . strtoupper(bin2hex(random_bytes(3)));
        $requestId = 'LBR' . strtoupper(bin2hex(random_bytes(10)));
        $orderInfo = $orderInfo ?: "Thanh toán LightBill {$orderId}";
        $extraData = base64_encode(json_encode([
            'companyId' => $this->companyId,
            'saleId' => $saleId,
            'nonce' => bin2hex(random_bytes(8)),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        $requestType = 'captureWallet';
        $rawSignature = 'accessKey=' . $this->accessKey
            . '&amount=' . $amount
            . '&extraData=' . $extraData
            . '&ipnUrl=' . $this->ipnUrl
            . '&orderId=' . $orderId
            . '&orderInfo=' . $orderInfo
            . '&partnerCode=' . $this->partnerCode
            . '&redirectUrl=' . $this->redirectUrl
            . '&requestId=' . $requestId
            . '&requestType=' . $requestType;
        $signature = hash_hmac('sha256', $rawSignature, $this->secretKey);

        $payload = [
            'partnerCode' => $this->partnerCode,
            'requestType' => $requestType,
            'ipnUrl' => $this->ipnUrl,
            'redirectUrl' => $this->redirectUrl,
            'orderId' => $orderId,
            'amount' => $amount,
            'orderInfo' => $orderInfo,
            'requestId' => $requestId,
            'extraData' => $extraData,
            'lang' => 'vi',
            'autoCapture' => true,
            'signature' => $signature,
        ];
        $response = $this->post('/v2/gateway/api/create', $payload);
        $resultCode = isset($response['resultCode']) ? (int) $response['resultCode'] : -1;
        $status = $resultCode === 0 ? 'pending' : 'failed';
        $pdo->prepare('INSERT INTO momo_transactions(company_id,sale_id,order_id,request_id,amount,environment,result_code,message,trans_id,pay_url,deeplink,qr_code_url,raw_response,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
            ->execute([$this->companyId,$saleId,$orderId,$requestId,$amount,$this->env,$resultCode,Util::text($response['message'] ?? '',255),isset($response['transId'])?(string)$response['transId']:null,$response['payUrl']??null,$response['deeplink']??null,$response['qrCodeUrl']??null,json_encode($response,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),$status]);
        Auth::audit($this->companyId, (int)$user['id'], 'momo.create', 'momo_transaction', $orderId, ['amount'=>$amount,'sale_id'=>$saleId,'environment'=>$this->env]);
        return $response + ['lightbillOrderId' => $orderId, 'environment' => $this->env];
    }

    public function verifyIpn(array $payload): bool
    {
        if ((string)($payload['partnerCode'] ?? '') !== $this->partnerCode) {
            return false;
        }
        $keys = ['accessKey','amount','extraData','message','orderId','orderInfo','orderType','partnerCode','payType','requestId','responseTime','resultCode','transId'];
        $pairs = [];
        foreach ($keys as $key) {
            $value = $key === 'accessKey' ? $this->accessKey : ($payload[$key] ?? '');
            $pairs[] = $key . '=' . $value;
        }
        $expected = hash_hmac('sha256', implode('&', $pairs), $this->secretKey);
        return isset($payload['signature']) && hash_equals($expected, (string)$payload['signature']);
    }

    public function processIpn(array $payload): array
    {
        if (!$this->verifyIpn($payload)) {
            throw new \RuntimeException('Invalid MoMo signature');
        }
        $orderId = (string)($payload['orderId'] ?? '');
        $requestId = (string)($payload['requestId'] ?? '');
        if ($orderId === '' || $requestId === '') {
            throw new \RuntimeException('Invalid MoMo identifiers');
        }
        $pdo = DB::pdo();
        $pdo->beginTransaction();
        try {
            $stmt = $pdo->prepare('SELECT * FROM momo_transactions WHERE company_id=? AND order_id=? AND request_id=? FOR UPDATE');
            $stmt->execute([$this->companyId, $orderId, $requestId]);
            $tx = $stmt->fetch();
            if (!$tx) {
                throw new \RuntimeException('Unknown MoMo order');
            }
            if ((int)($payload['amount'] ?? -1) !== (int)$tx['amount']) {
                throw new \RuntimeException('MoMo amount mismatch');
            }
            $resultCode = (int)($payload['resultCode'] ?? -1);
            $status = $resultCode === 0 ? 'paid' : 'failed';
            $alreadyPaid = (string)$tx['status'] === 'paid';
            $pdo->prepare('UPDATE momo_transactions SET result_code=?,message=?,trans_id=?,raw_response=?,status=? WHERE id=?')
                ->execute([$resultCode,Util::text($payload['message']??'',255),isset($payload['transId'])?(string)$payload['transId']:null,json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),$alreadyPaid ? 'paid' : $status,(int)$tx['id']]);
            if ($resultCode === 0 && !$alreadyPaid && $tx['sale_id']) {
                $sale = Util::row('SELECT * FROM sales WHERE id=? AND company_id=? FOR UPDATE', [(int)$tx['sale_id'], $this->companyId]);
                if ($sale) {
                    $amount = (float)$tx['amount'];
                    $newPaid = min((float)$sale['total_amount'], (float)$sale['paid_amount'] + $amount);
                    $saleStatus = $newPaid >= (float)$sale['total_amount'] ? 'paid' : 'partially_paid';
                    $pdo->prepare('UPDATE sales SET paid_amount=?,status=?,payment_method=? WHERE id=? AND company_id=?')->execute([$newPaid,$saleStatus,'momo',(int)$sale['id'],$this->companyId]);
                    $pdo->prepare('INSERT INTO payments(company_id,direction,reference_type,reference_id,method,amount,transaction_ref,paid_at) VALUES(?,?,?,?,?,?,?,NOW())')
                        ->execute([$this->companyId,'in','sale',(int)$sale['id'],'momo',$amount,(string)($payload['transId']??$orderId)]);
                }
            }
            $pdo->commit();
            Auth::audit($this->companyId, null, 'momo.ipn', 'momo_transaction', $orderId, ['result_code'=>$resultCode,'duplicate'=>$alreadyPaid]);
            return ['orderId'=>$orderId,'status'=>$alreadyPaid ? 'paid' : $status];
        } catch (\Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $e;
        }
    }

    private function post(string $path, array $payload): array
    {
        $ch = curl_init($this->baseUrl . $path);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => ['Content-Type: application/json; charset=UTF-8'],
            CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_TIMEOUT => 35,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
        ]);
        $raw = curl_exec($ch);
        $http = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($raw === false || $err !== '') {
            throw new \RuntimeException('Không kết nối được MoMo');
        }
        $decoded = json_decode($raw, true);
        if (!is_array($decoded)) {
            throw new \RuntimeException('MoMo trả dữ liệu không hợp lệ');
        }
        if ($http < 200 || $http >= 300) {
            throw new \RuntimeException('MoMo từ chối yêu cầu thanh toán');
        }
        return $decoded;
    }

    private function isHttpsUrl(string $url): bool
    {
        return $url !== '' && parse_url($url, PHP_URL_SCHEME) === 'https' && (string)parse_url($url, PHP_URL_HOST) !== '';
    }
}
