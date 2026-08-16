<?php
declare(strict_types=1);
namespace LightBill;

final class MisaEInvoiceService
{
    private int $companyId;
    private string $environment;
    private string $baseHost;
    private string $appId;
    private string $taxCode;
    private string $username;
    private string $password;
    private string $invSeries;
    private int $signType;

    public function __construct(int $companyId)
    {
        if ($companyId <= 0) throw new \InvalidArgumentException('Doanh nghiệp không hợp lệ');
        $this->companyId = $companyId;
        $this->environment = strtolower(trim((string)CompanySettings::get($companyId, 'einvoice.environment', Env::get('EINVOICE_ENV', 'sandbox'))));
        if (!in_array($this->environment, ['sandbox','production'], true)) $this->environment = 'sandbox';
        $this->baseHost = $this->environment === 'production' ? 'https://api.meinvoice.vn' : 'https://testapi.meinvoice.vn';
        $company = Util::row('SELECT tax_code FROM companies WHERE id=?', [$companyId]);
        $this->appId = trim((string)CompanySettings::get($companyId, 'einvoice.misa.app_id', Env::get('MISA_APP_ID', '')));
        $this->taxCode = trim((string)CompanySettings::get($companyId, 'einvoice.misa.tax_code', Env::get('MISA_TAX_CODE', (string)($company['tax_code'] ?? ''))));
        $this->username = trim((string)CompanySettings::get($companyId, 'einvoice.misa.username', Env::get('MISA_USERNAME', '')));
        $this->password = (string)CompanySettings::get($companyId, 'einvoice.misa.password', Env::get('MISA_PASSWORD', ''));
        $this->invSeries = trim((string)CompanySettings::get($companyId, 'einvoice.misa.inv_series', Env::get('MISA_INV_SERIES', '')));
        $this->signType = (int)CompanySettings::get($companyId, 'einvoice.misa.sign_type', Env::get('MISA_SIGN_TYPE', '2'));
        if (!in_array($this->signType, [2,5], true)) $this->signType = 2;
    }

    public function status(): array
    {
        return [
            'provider' => 'misa_meinvoice',
            'environment' => $this->environment,
            'app_id' => $this->appId,
            'tax_code' => $this->taxCode,
            'username' => $this->username,
            'inv_series' => $this->invSeries,
            'sign_type' => $this->signType,
            'app_id_configured' => $this->appId !== '',
            'password_configured' => $this->password !== '',
            'ready' => $this->appId !== '' && $this->taxCode !== '' && $this->username !== '' && $this->password !== '' && $this->invSeries !== '',
        ];
    }

    public function testConnection(): array
    {
        $token = $this->token();
        return ['connected' => $token !== '', 'provider' => 'misa_meinvoice', 'environment' => $this->environment];
    }

    public function submit(array $user, int $invoiceId): array
    {
        if ((int)$user['company_id'] !== $this->companyId) Response::error('Doanh nghiệp không hợp lệ', 403);
        $invoice = BusinessService::invoiceDetail($this->companyId, $invoiceId);
        if (!$invoice) Response::error('Không tìm thấy hóa đơn', 404);
        if (!in_array((string)$invoice['status'], ['draft','submitted','rejected'], true)) Response::error('Trạng thái hóa đơn không cho phép phát hành', 409);
        if ($this->signType === 5 && (string)$invoice['invoice_type'] !== 'cash_register') Response::error('SignType 5 chỉ dùng cho hóa đơn máy tính tiền', 422);
        $this->assertReady();

        $refId = trim((string)($invoice['provider_request_id'] ?? ''));
        if ($refId === '') {
            $refId = Util::uuid4();
            DB::pdo()->prepare('UPDATE invoices SET provider_request_id=?,provider=? WHERE id=? AND company_id=?')
                ->execute([$refId, 'misa_meinvoice', $invoiceId, $this->companyId]);
            $invoice['provider_request_id'] = $refId;
        }

        $payload = [
            'SignType' => $this->signType,
            'InvoiceData' => [$this->buildInvoiceData($invoice)],
            'PublishInvoiceData' => null,
        ];
        $token = $this->token();
        $response = $this->postJson('/api/integration/invoice', $payload, $token);
        $success = (bool)($response['success'] ?? $response['Success'] ?? false);
        if (!$success) {
            $message = (string)($response['descriptionErrorCode'] ?? $response['DescriptionErrorCode'] ?? $response['errorCode'] ?? $response['ErrorCode'] ?? 'MISA từ chối yêu cầu');
            DB::pdo()->prepare('UPDATE invoices SET status=?,provider=?,response_json=? WHERE id=? AND company_id=?')
                ->execute(['rejected','misa_meinvoice',json_encode($response,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),$invoiceId,$this->companyId]);
            Auth::audit($this->companyId,(int)$user['id'],'invoice.submit_failed','invoice',(string)$invoiceId,['provider'=>'misa_meinvoice','environment'=>$this->environment]);
            Response::error('Phát hành hóa đơn thất bại: ' . Util::text($message, 220), 422);
        }

        $publishedRaw = $response['publishInvoiceResult'] ?? $response['PublishInvoiceResult'] ?? null;
        $published = is_string($publishedRaw) ? json_decode($publishedRaw, true) : $publishedRaw;
        $result = is_array($published) && isset($published[0]) && is_array($published[0]) ? $published[0] : [];
        $errorCode = trim((string)($result['ErrorCode'] ?? $result['errorCode'] ?? ''));
        $status = $errorCode === '' ? 'issued' : 'rejected';
        $providerRef = (string)($result['TransactionID'] ?? $result['transactionID'] ?? $refId);
        $invoiceNo = trim((string)($result['InvNo'] ?? $result['invNo'] ?? '')) ?: null;
        $taxCode = trim((string)($result['InvCode'] ?? $result['invCode'] ?? '')) ?: null;
        DB::pdo()->prepare('UPDATE invoices SET status=?,provider=?,provider_ref=?,invoice_no=COALESCE(?,invoice_no),tax_authority_code=COALESCE(?,tax_authority_code),response_json=? WHERE id=? AND company_id=?')
            ->execute([$status,'misa_meinvoice',$providerRef,$invoiceNo,$taxCode,json_encode($response,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),$invoiceId,$this->companyId]);
        Auth::audit($this->companyId,(int)$user['id'],'invoice.submit','invoice',(string)$invoiceId,['provider'=>'misa_meinvoice','environment'=>$this->environment,'status'=>$status]);
        return BusinessService::invoiceDetail($this->companyId, $invoiceId) ?? [];
    }

    private function buildInvoiceData(array $invoice): array
    {
        $lines = [];
        $taxGroups = [];
        foreach ($invoice['items'] as $line) {
            $qty = (float)($line['quantity'] ?? 0);
            $unitPrice = (float)($line['unit_price'] ?? 0);
            $tax = (float)($line['tax_amount'] ?? 0);
            $beforeTax = round(max(0, (float)$line['line_total'] - $tax), 2);
            if ($qty > 0 && $unitPrice > 0 && abs($beforeTax - ($qty * $unitPrice)) > 0.02) {
                $beforeTax = round($qty * $unitPrice, 2);
            }
            $vatName = self::vatRateName($line['vat_rate'] ?? 0, (string)($line['vat_category'] ?? 'taxable'));
            $lines[] = [
                'ItemType' => 1,
                'LineNumber' => (int)$line['line_no'],
                'SortOrder' => (int)$line['line_no'],
                'ItemCode' => (string)($line['sku'] ?? ''),
                'ItemName' => (string)$line['name'],
                'UnitName' => (string)($line['unit'] ?? ''),
                'Quantity' => $qty,
                'UnitPrice' => $unitPrice,
                'DiscountRate' => 0,
                'DiscountAmountOC' => 0,
                'DiscountAmount' => 0,
                'AmountOC' => $beforeTax,
                'Amount' => $beforeTax,
                'AmountWithoutVATOC' => $beforeTax,
                'AmountWithoutVAT' => $beforeTax,
                'VATRateName' => $vatName,
                'VATAmountOC' => $tax,
                'VATAmount' => $tax,
            ];
            if (!isset($taxGroups[$vatName])) $taxGroups[$vatName] = ['VATRateName'=>$vatName,'AmountWithoutVATOC'=>0.0,'VATAmountOC'=>0.0];
            $taxGroups[$vatName]['AmountWithoutVATOC'] = round($taxGroups[$vatName]['AmountWithoutVATOC'] + $beforeTax, 2);
            $taxGroups[$vatName]['VATAmountOC'] = round($taxGroups[$vatName]['VATAmountOC'] + $tax, 2);
        }
        $sale = !empty($invoice['sale_id']) ? BusinessService::saleDetail($this->companyId, (int)$invoice['sale_id']) : null;
        $paymentMethod = self::paymentMethodName((string)($sale['payment_method'] ?? ''));
        $data = [
            'RefID' => (string)$invoice['provider_request_id'],
            'InvSeries' => $this->invSeries,
            'InvDate' => substr((string)$invoice['invoice_date'],0,10),
            'CurrencyCode' => 'VND',
            'ExchangeRate' => 1,
            'PaymentMethodName' => $paymentMethod,
            'IsSendEmail' => trim((string)($invoice['buyer_email'] ?? '')) !== '',
            'ReceiverName' => (string)($invoice['buyer_name'] ?? ''),
            'ReceiverEmail' => (string)($invoice['buyer_email'] ?? ''),
            'BuyerLegalName' => (string)($invoice['buyer_name'] ?? ''),
            'BuyerTaxCode' => (string)($invoice['buyer_tax_code'] ?? ''),
            'BuyerAddress' => (string)($invoice['buyer_address'] ?? ''),
            'BuyerEmail' => (string)($invoice['buyer_email'] ?? ''),
            'TotalSaleAmountOC' => (float)$invoice['subtotal'],
            'TotalSaleAmount' => (float)$invoice['subtotal'],
            'TotalAmountWithoutVATOC' => (float)$invoice['subtotal'],
            'TotalAmountWithoutVAT' => (float)$invoice['subtotal'],
            'DiscountRate' => 0,
            'TotalDiscountAmountOC' => 0,
            'TotalDiscountAmount' => 0,
            'TotalVATAmountOC' => (float)$invoice['tax_amount'],
            'TotalVATAmount' => (float)$invoice['tax_amount'],
            'TotalAmountOC' => (float)$invoice['total_amount'],
            'TotalAmount' => (float)$invoice['total_amount'],
            'TotalAmountInWords' => Util::moneyToWords((float)$invoice['total_amount']),
            'IsInvoiceCalculatingMachine' => (string)$invoice['invoice_type'] === 'cash_register',
            'OriginalInvoiceDetail' => $lines,
            'TaxRateInfo' => array_values($taxGroups),
            'OptionUserDefined' => [
                'MainCurrency' => 'VND',
                'AmountDecimalDigits' => '0',
                'AmountOCDecimalDigits' => '0',
                'UnitPriceOCDecimalDigits' => '0',
                'UnitPriceDecimalDigits' => '0',
                'QuantityDecimalDigits' => '3',
            ],
        ];
        return $data;
    }

    private function token(): string
    {
        $this->assertCredentials();
        $response = $this->postJson('/api/integration/auth/token', [
            'appid' => $this->appId,
            'taxcode' => $this->taxCode,
            'username' => $this->username,
            'password' => $this->password,
        ]);
        $success = (bool)($response['Success'] ?? $response['success'] ?? false);
        $token = trim((string)($response['Data'] ?? $response['data'] ?? ''));
        if (!$success || $token === '') {
            $code = (string)($response['ErrorCode'] ?? $response['errorCode'] ?? 'Không xác thực được');
            Response::error('Không kết nối được MISA meInvoice: ' . Util::text($code, 160), 422);
        }
        return $token;
    }

    private function postJson(string $path, array $payload, ?string $bearer = null): array
    {
        $headers = ['Content-Type: application/json','Accept: application/json'];
        if ($bearer) $headers[] = 'Authorization: Bearer ' . $bearer;
        $ch = curl_init($this->baseHost . $path);
        curl_setopt_array($ch,[
            CURLOPT_POST=>true,
            CURLOPT_RETURNTRANSFER=>true,
            CURLOPT_HTTPHEADER=>$headers,
            CURLOPT_POSTFIELDS=>json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),
            CURLOPT_CONNECTTIMEOUT=>10,
            CURLOPT_TIMEOUT=>45,
            CURLOPT_SSL_VERIFYPEER=>true,
            CURLOPT_SSL_VERIFYHOST=>2,
        ]);
        $raw=curl_exec($ch);$http=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);$err=curl_error($ch);curl_close($ch);
        if($raw===false||$http<200||$http>=300) throw new \RuntimeException('MISA meInvoice HTTP error '.$http.($err?': '.$err:''));
        $data=json_decode((string)$raw,true);
        if(!is_array($data)) throw new \RuntimeException('Phản hồi MISA meInvoice không hợp lệ');
        return $data;
    }

    private function assertCredentials(): void
    {
        if ($this->appId === '' || $this->taxCode === '' || $this->username === '' || $this->password === '') Response::error('Chưa cấu hình tài khoản MISA meInvoice',409);
    }

    private function assertReady(): void
    {
        $this->assertCredentials();
        if ($this->invSeries === '') Response::error('Chưa cấu hình ký hiệu hóa đơn MISA',409);
    }

    private static function vatRateName(mixed $rate, string $category='taxable'): string
    {
        if($category==='not_subject') return 'KCT';
        if($category==='non_taxable') return 'KKKNT';
        $n=(float)$rate;
        if(in_array(round($n,2),[0.0,5.0,10.0],true)) return (string)(int)round($n).'%';
        return 'KHAC:'.number_format($n,2,'.','').'%';
    }

    private static function paymentMethodName(string $method): string
    {
        return match($method){'cash'=>'TM','bank','zalopay','card'=>'CK',default=>'TM/CK'};
    }
}
