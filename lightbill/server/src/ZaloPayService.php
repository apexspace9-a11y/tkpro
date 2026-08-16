<?php
declare(strict_types=1);
namespace LightBill;

final class ZaloPayService
{
    private int $companyId;
    private int $appId;
    private string $key1;
    private string $key2;
    private string $environment;
    private string $baseUrl;
    private string $callbackUrl;
    private string $redirectUrl;

    public function __construct(int $companyId)
    {
        if ($companyId <= 0) {
            throw new \InvalidArgumentException('Doanh nghiệp không hợp lệ');
        }
        $this->companyId = $companyId;
        $this->environment = strtolower(trim((string)CompanySettings::get($companyId, 'zalopay.environment', Env::get('ZALOPAY_ENV', 'sandbox'))));
        if (!in_array($this->environment, ['sandbox', 'production'], true)) {
            $this->environment = 'sandbox';
        }

        // ZaloPay công khai bộ thông tin dùng thử này trong tài liệu sandbox.
        $defaultAppId = $this->environment === 'sandbox' ? '2554' : '';
        $defaultKey1 = $this->environment === 'sandbox' ? 'sdngKKJmqEMzvh5QQcdD2A9XBSKUNaYn' : '';
        $defaultKey2 = $this->environment === 'sandbox' ? 'trMrHtvjo6myautxDUiAcYsVtaeQ8nhf' : '';

        $this->appId = (int)CompanySettings::get($companyId, 'zalopay.app_id', Env::get('ZALOPAY_APP_ID', $defaultAppId));
        $this->key1 = trim((string)CompanySettings::get($companyId, 'zalopay.key1', Env::get('ZALOPAY_KEY1', $defaultKey1)));
        $this->key2 = trim((string)CompanySettings::get($companyId, 'zalopay.key2', Env::get('ZALOPAY_KEY2', $defaultKey2)));
        if ($this->appId <= 0 || $this->key1 === '' || $this->key2 === '') {
            Response::error('Chưa cấu hình ZaloPay', 409);
        }

        $this->baseUrl = $this->environment === 'production' ? 'https://openapi.zalopay.vn' : 'https://sb-openapi.zalopay.vn';
        $appUrl = rtrim((string)Env::get('APP_URL', ''), '/');
        $this->callbackUrl = trim((string)CompanySettings::get($companyId, 'zalopay.callback_url', Env::get('ZALOPAY_CALLBACK_URL', $appUrl . '/api/zalopay/callback')));
        $this->redirectUrl = trim((string)CompanySettings::get($companyId, 'zalopay.redirect_url', Env::get('ZALOPAY_REDIRECT_URL', $appUrl . '/zalopay/return')));
        if (!$this->isHttpsUrl($this->callbackUrl) || !$this->isHttpsUrl($this->redirectUrl)) {
            Response::error('Callback/redirect ZaloPay phải dùng HTTPS', 422);
        }
    }

    public static function fromCallback(array $payload): self
    {
        $data = json_decode((string)($payload['data'] ?? ''), true);
        if (!is_array($data)) {
            throw new \RuntimeException('Callback ZaloPay không có dữ liệu hợp lệ');
        }
        $embed = json_decode((string)($data['embed_data'] ?? '{}'), true);
        $companyId = is_array($embed) ? (int)($embed['lightbill_company_id'] ?? 0) : 0;
        if ($companyId <= 0) {
            throw new \RuntimeException('Không xác định được doanh nghiệp từ callback ZaloPay');
        }
        return new self($companyId);
    }

    public function createPayment(array $user, int $amount, ?int $saleId = null, ?string $description = null): array
    {
        if ((int)$user['company_id'] !== $this->companyId) {
            Response::error('Doanh nghiệp không hợp lệ', 403);
        }
        if ($amount <= 0) {
            Response::error('Số tiền thanh toán không hợp lệ', 422);
        }

        $pdo = DB::pdo();
        $datePrefix = date('ymd');
        $appTransId = $datePrefix . '_LB' . strtoupper(bin2hex(random_bytes(6)));
        $appUser = 'lightbill_' . (int)$user['id'];
        $appTime = (int)floor(microtime(true) * 1000);
        $description = Util::text($description ?: 'LightBill - Thanh toán ' . $appTransId, 250);

        $items = [];
        if ($saleId) {
            $sale = BusinessService::saleDetail($this->companyId, $saleId);
            if (!$sale) {
                Response::error('Không tìm thấy đơn bán', 404);
            }
            foreach ($sale['items'] as $line) {
                $items[] = [
                    'itemid' => (string)($line['product_id'] ?? ''),
                    'itemname' => Util::text($line['name'] ?? 'Sản phẩm', 120),
                    'itemprice' => (int)round((float)$line['unit_price']),
                    'itemquantity' => (float)$line['quantity'],
                ];
            }
        }
        $itemJson = json_encode($items, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        $embedData = json_encode([
            'redirecturl' => $this->redirectUrl,
            'lightbill_company_id' => $this->companyId,
            'lightbill_sale_id' => $saleId,
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if ($itemJson === false || $embedData === false) {
            throw new \RuntimeException('Không thể tạo dữ liệu thanh toán');
        }

        $macInput = $this->appId . '|' . $appTransId . '|' . $appUser . '|' . $amount . '|' . $appTime . '|' . $embedData . '|' . $itemJson;
        $payload = [
            'app_id' => $this->appId,
            'app_user' => $appUser,
            'app_trans_id' => $appTransId,
            'app_time' => $appTime,
            'expire_duration_seconds' => 900,
            'amount' => $amount,
            'description' => $description,
            'callback_url' => $this->callbackUrl,
            'item' => $itemJson,
            'embed_data' => $embedData,
            'bank_code' => '',
            'mac' => hash_hmac('sha256', $macInput, $this->key1),
        ];

        $response = $this->post('/v2/create', $payload);
        $returnCode = (int)($response['return_code'] ?? 0);
        $status = $returnCode === 1 ? 'pending' : 'failed';
        $pdo->prepare('INSERT INTO zalopay_transactions(company_id,sale_id,app_trans_id,app_user,amount,environment,return_code,return_message,zp_trans_id,zp_trans_token,order_url,qr_code,raw_response,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
            ->execute([
                $this->companyId, $saleId, $appTransId, $appUser, $amount, $this->environment,
                $returnCode, Util::text($response['return_message'] ?? '', 255),
                isset($response['zp_trans_id']) ? (string)$response['zp_trans_id'] : null,
                $response['zp_trans_token'] ?? null, $response['order_url'] ?? null,
                $response['qr_code'] ?? null,
                json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), $status,
            ]);
        Auth::audit($this->companyId, (int)$user['id'], 'zalopay.create', 'zalopay_transaction', $appTransId, ['amount' => $amount, 'sale_id' => $saleId, 'environment' => $this->environment]);
        return $response + ['lightbill_app_trans_id' => $appTransId, 'environment' => $this->environment];
    }

    public function processCallback(array $payload): array
    {
        $dataRaw = (string)($payload['data'] ?? '');
        $requestMac = (string)($payload['mac'] ?? '');
        $expected = hash_hmac('sha256', $dataRaw, $this->key2);
        if ($dataRaw === '' || $requestMac === '' || !hash_equals($expected, $requestMac)) {
            throw new \RuntimeException('Invalid ZaloPay callback MAC');
        }
        $data = json_decode($dataRaw, true);
        if (!is_array($data)) {
            throw new \RuntimeException('Invalid ZaloPay callback data');
        }
        $appTransId = (string)($data['app_trans_id'] ?? '');
        if ($appTransId === '') {
            throw new \RuntimeException('Missing ZaloPay app_trans_id');
        }

        $pdo = DB::pdo();
        $pdo->beginTransaction();
        try {
            $stmt = $pdo->prepare('SELECT * FROM zalopay_transactions WHERE company_id=? AND app_trans_id=? FOR UPDATE');
            $stmt->execute([$this->companyId, $appTransId]);
            $tx = $stmt->fetch();
            if (!$tx) {
                throw new \RuntimeException('Unknown ZaloPay transaction');
            }
            if ((int)($data['amount'] ?? -1) !== (int)$tx['amount']) {
                throw new \RuntimeException('ZaloPay amount mismatch');
            }
            $alreadyPaid = (string)$tx['status'] === 'paid';
            $zpTransId = isset($data['zp_trans_id']) ? (string)$data['zp_trans_id'] : null;
            $pdo->prepare('UPDATE zalopay_transactions SET zp_trans_id=?,return_code=1,return_message=?,raw_response=?,status=? WHERE id=?')
                ->execute([$zpTransId, 'Thanh toán thành công', json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'paid', (int)$tx['id']]);
            if (!$alreadyPaid && $tx['sale_id']) {
                $this->applySalePayment((int)$tx['sale_id'], (float)$tx['amount'], $zpTransId ?: $appTransId);
            }
            $pdo->commit();
            Auth::audit($this->companyId, null, 'zalopay.callback', 'zalopay_transaction', $appTransId, ['duplicate' => $alreadyPaid, 'zp_trans_id' => $zpTransId]);
            return ['app_trans_id' => $appTransId, 'status' => 'paid'];
        } catch (\Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $e;
        }
    }

    public function queryAndSync(string $appTransId, ?int $userId = null): array
    {
        $appTransId = Util::text($appTransId, 40);
        $tx = Util::row('SELECT * FROM zalopay_transactions WHERE company_id=? AND app_trans_id=?', [$this->companyId, $appTransId]);
        if (!$tx) {
            Response::error('Không tìm thấy giao dịch ZaloPay', 404);
        }
        $macInput = $this->appId . '|' . $appTransId . '|' . $this->key1;
        $response = $this->post('/v2/query', [
            'app_id' => $this->appId,
            'app_trans_id' => $appTransId,
            'mac' => hash_hmac('sha256', $macInput, $this->key1),
        ]);
        $code = (int)($response['return_code'] ?? 0);
        $status = $code === 1 ? 'paid' : ($code === 3 ? 'pending' : 'failed');
        $pdo = DB::pdo();
        $pdo->beginTransaction();
        try {
            $stmt = $pdo->prepare('SELECT * FROM zalopay_transactions WHERE company_id=? AND app_trans_id=? FOR UPDATE');
            $stmt->execute([$this->companyId, $appTransId]);
            $locked = $stmt->fetch();
            $alreadyPaid = $locked && (string)$locked['status'] === 'paid';
            $zpTransId = isset($response['zp_trans_id']) ? (string)$response['zp_trans_id'] : ($locked['zp_trans_id'] ?? null);
            $pdo->prepare('UPDATE zalopay_transactions SET return_code=?,return_message=?,zp_trans_id=?,raw_response=?,status=? WHERE company_id=? AND app_trans_id=?')
                ->execute([$code, Util::text($response['return_message'] ?? '', 255), $zpTransId, json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), $alreadyPaid ? 'paid' : $status, $this->companyId, $appTransId]);
            if ($code === 1 && !$alreadyPaid && $locked && $locked['sale_id']) {
                if ((int)($response['amount'] ?? -1) !== (int)$locked['amount']) {
                    throw new \RuntimeException('ZaloPay amount mismatch');
                }
                $this->applySalePayment((int)$locked['sale_id'], (float)$locked['amount'], $zpTransId ?: $appTransId);
            }
            $pdo->commit();
            Auth::audit($this->companyId, $userId, 'zalopay.query', 'zalopay_transaction', $appTransId, ['return_code' => $code]);
        } catch (\Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            throw $e;
        }
        return $response + ['status' => $status, 'environment' => $this->environment];
    }

    private function applySalePayment(int $saleId, float $amount, string $transactionRef): void
    {
        $pdo = DB::pdo();
        $sale = Util::row('SELECT * FROM sales WHERE id=? AND company_id=? FOR UPDATE', [$saleId, $this->companyId]);
        if (!$sale) return;
        $newPaid = min((float)$sale['total_amount'], (float)$sale['paid_amount'] + $amount);
        $saleStatus = $newPaid >= (float)$sale['total_amount'] ? 'paid' : 'partially_paid';
        $pdo->prepare('UPDATE sales SET paid_amount=?,status=?,payment_method=? WHERE id=? AND company_id=?')
            ->execute([$newPaid, $saleStatus, 'zalopay', $saleId, $this->companyId]);
        try {
            $pdo->prepare('INSERT INTO payments(company_id,direction,reference_type,reference_id,method,amount,transaction_ref,paid_at) VALUES(?,?,?,?,?,?,?,NOW())')
                ->execute([$this->companyId, 'in', 'sale', $saleId, 'zalopay', $amount, $transactionRef]);
        } catch (\PDOException $e) {
            if ($e->getCode() !== '23000') throw $e;
        }
    }

    private function post(string $path, array $payload): array
    {
        $ch = curl_init($this->baseUrl . $path);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => ['Content-Type: application/json', 'Accept: application/json'],
            CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_TIMEOUT => 35,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
        ]);
        $raw = curl_exec($ch);
        $http = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($raw === false || $http < 200 || $http >= 300) {
            throw new \RuntimeException('ZaloPay HTTP error ' . $http . ($err ? ': ' . $err : ''));
        }
        $data = json_decode((string)$raw, true);
        if (!is_array($data)) {
            throw new \RuntimeException('Phản hồi ZaloPay không hợp lệ');
        }
        return $data;
    }

    private function isHttpsUrl(string $url): bool
    {
        return filter_var($url, FILTER_VALIDATE_URL) !== false && strtolower((string)parse_url($url, PHP_URL_SCHEME)) === 'https';
    }
}
