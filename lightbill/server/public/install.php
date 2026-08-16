<?php
declare(strict_types=1);

$root = dirname(__DIR__);
$lockFile = $root . '/storage/installed.lock';
$envFile = $root . '/.env';
if (is_file($lockFile) && is_file($envFile)) {
    header('Location: ./', true, 303);
    exit;
}

session_start();
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
if (empty($_SESSION['lightbill_install_csrf'])) $_SESSION['lightbill_install_csrf'] = bin2hex(random_bytes(24));

function h(string $v): string { return htmlspecialchars($v, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
function field(string $key, string $default=''): string { return h((string)($_POST[$key] ?? $default)); }
function envq(string $v): string { return '"' . addcslashes($v, "\\\"\n\r") . '"'; }
function appUrl(): string {
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    $host = preg_replace('/[^A-Za-z0-9.:-]/', '', (string)($_SERVER['HTTP_HOST'] ?? ''));
    $path = rtrim(str_replace('\\','/',dirname((string)($_SERVER['SCRIPT_NAME'] ?? '/install.php'))), '/.');
    return ($https ? 'https://' : 'http://') . $host . ($path ? $path : '');
}
function runSchema(PDO $pdo, string $file): void {
    $sql = file_get_contents($file);
    if ($sql === false) throw new RuntimeException('Không đọc được dữ liệu cài đặt');
    foreach (preg_split('/;\s*(?:\r?\n|$)/', $sql) ?: [] as $statement) {
        $statement = trim($statement);
        if ($statement !== '') $pdo->exec($statement);
    }
}

$error = '';
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    try {
        if (!hash_equals((string)$_SESSION['lightbill_install_csrf'], (string)($_POST['csrf'] ?? ''))) throw new RuntimeException('Phiên cài đặt không hợp lệ.');
        foreach (['pdo_mysql','curl','mbstring','openssl'] as $ext) if (!extension_loaded($ext)) throw new RuntimeException('Hosting chưa đáp ứng yêu cầu chạy LightBill.');
        if (version_compare(PHP_VERSION, '8.2.0', '<')) throw new RuntimeException('Hosting chưa đáp ứng yêu cầu chạy LightBill.');
        $url = trim((string)($_POST['app_url'] ?? appUrl()));
        if (!filter_var($url, FILTER_VALIDATE_URL) || strtolower((string)parse_url($url, PHP_URL_SCHEME)) !== 'https') throw new RuntimeException('Địa chỉ LightBill phải dùng HTTPS.');
        $dbHost = trim((string)($_POST['db_host'] ?? 'localhost'));
        $dbPort = (int)($_POST['db_port'] ?? 3306);
        $dbName = trim((string)($_POST['db_name'] ?? ''));
        $dbUser = trim((string)($_POST['db_user'] ?? ''));
        $dbPass = (string)($_POST['db_pass'] ?? '');
        $company = trim((string)($_POST['company_name'] ?? ''));
        $taxCode = trim((string)($_POST['tax_code'] ?? ''));
        $address = trim((string)($_POST['address'] ?? ''));
        $ownerName = trim((string)($_POST['owner_name'] ?? ''));
        $ownerEmail = strtolower(trim((string)($_POST['owner_email'] ?? '')));
        $ownerPassword = (string)($_POST['owner_password'] ?? '');
        $regime = (string)($_POST['accounting_regime'] ?? 'TT99_2025');
        if ($dbHost===''||$dbName===''||$dbUser===''||$company===''||$ownerName==='') throw new RuntimeException('Vui lòng nhập đủ thông tin bắt buộc.');
        if (!filter_var($ownerEmail,FILTER_VALIDATE_EMAIL) || strlen($ownerPassword)<10) throw new RuntimeException('Email hoặc mật khẩu quản trị chưa hợp lệ.');
        if (!in_array($regime,['TT99_2025','TT58_2026'],true)) $regime='TT99_2025';
        if (!preg_match('/^[A-Za-z0-9_$.-]{1,128}$/',$dbName)) throw new RuntimeException('Tên cơ sở dữ liệu không hợp lệ.');
        $pdo = new PDO("mysql:host={$dbHost};port={$dbPort};dbname={$dbName};charset=utf8mb4",$dbUser,$dbPass,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]);
        runSchema($pdo, $root . '/database/schema.sql');
        $count = (int)$pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
        if ($count > 0) throw new RuntimeException('Cơ sở dữ liệu này đã có dữ liệu LightBill.');

        $appKey = bin2hex(random_bytes(32));
        $env = implode("\n", [
            'APP_ENV=production',
            'APP_VERSION=2.0.0',
            'APP_URL='.envq(rtrim($url,'/')),
            'APP_KEY='.envq($appKey),
            'APP_TIMEZONE=Asia/Ho_Chi_Minh',
            'DB_HOST='.envq($dbHost),
            'DB_PORT='.$dbPort,
            'DB_NAME='.envq($dbName),
            'DB_USER='.envq($dbUser),
            'DB_PASS='.envq($dbPass),
            'DB_CHARSET=utf8mb4',
            'TOKEN_TTL_HOURS=24',
            'CORS_ORIGINS='.envq(rtrim($url,'/')),
            'ZALOPAY_ENV=sandbox',
            'ZALOPAY_APP_ID=2554',
            'ZALOPAY_KEY1='.envq('sdngKKJmqEMzvh5QQcdD2A9XBSKUNaYn'),
            'ZALOPAY_KEY2='.envq('trMrHtvjo6myautxDUiAcYsVtaeQ8nhf'),
            'ZALOPAY_CALLBACK_URL='.envq(rtrim($url,'/').'/api/zalopay/callback'),
            'ZALOPAY_REDIRECT_URL='.envq(rtrim($url,'/').'/zalopay/return'),
            'EINVOICE_PROVIDER=misa_meinvoice',
            'EINVOICE_ENV=sandbox',
            'MISA_SIGN_TYPE=2',
            ''
        ]);
        $tmp = $envFile . '.tmp.' . bin2hex(random_bytes(4));
        if (file_put_contents($tmp,$env,LOCK_EX) === false || !rename($tmp,$envFile)) throw new RuntimeException('Không thể lưu cấu hình. Kiểm tra quyền ghi thư mục server.');
        @chmod($envFile,0600);

        $pdo->beginTransaction();
        try {
            $pdo->prepare('INSERT INTO companies(name,tax_code,address,accounting_regime,plan_code) VALUES(?,?,?,?,?)')->execute([$company,$taxCode!==''?$taxCode:null,$address!==''?$address:null,$regime,'business']);
            $companyId=(int)$pdo->lastInsertId();
            $pdo->prepare('INSERT INTO branches(company_id,code,name,address) VALUES(?,?,?,?)')->execute([$companyId,'CN01','Chi nhánh chính',$address!==''?$address:null]);
            $branchId=(int)$pdo->lastInsertId();
            $pdo->prepare('INSERT INTO warehouses(company_id,branch_id,code,name,address) VALUES(?,?,?,?,?)')->execute([$companyId,$branchId,'KHO01','Kho chính',$address!==''?$address:null]);
            $pdo->prepare('INSERT INTO users(company_id,branch_id,full_name,email,password_hash,role,level) VALUES(?,?,?,?,?,?,?)')->execute([$companyId,$branchId,$ownerName,$ownerEmail,password_hash($ownerPassword,PASSWORD_DEFAULT),'owner',100]);
            $accounts=[['111','Tiền mặt','asset'],['112','Tiền gửi ngân hàng','asset'],['131','Phải thu khách hàng','asset'],['1331','Thuế GTGT được khấu trừ','asset'],['156','Hàng hóa','asset'],['331','Phải trả người bán','liability'],['3331','Thuế GTGT phải nộp','liability'],['411','Vốn chủ sở hữu','equity'],['511','Doanh thu bán hàng và cung cấp dịch vụ','revenue'],['632','Giá vốn hàng bán','expense'],['641','Chi phí bán hàng','expense'],['642','Chi phí quản lý doanh nghiệp','expense']];
            $stmt=$pdo->prepare('INSERT IGNORE INTO chart_accounts(company_id,code,name,account_type) VALUES(?,?,?,?)');foreach($accounts as $a)$stmt->execute([$companyId,$a[0],$a[1],$a[2]]);
            $pdo->commit();
        } catch (Throwable $e) { if ($pdo->inTransaction()) $pdo->rollBack(); @unlink($envFile); throw $e; }

        if (!is_dir($root.'/storage') && !mkdir($root.'/storage',0750,true) && !is_dir($root.'/storage')) throw new RuntimeException('Không thể khóa bộ cài.');
        $lock=['version'=>'2.0.0','installed_at'=>date(DATE_ATOM),'company_id'=>$companyId];
        if (file_put_contents($lockFile,json_encode($lock,JSON_UNESCAPED_SLASHES),LOCK_EX)===false) throw new RuntimeException('Không thể khóa bộ cài.');
        @chmod($lockFile,0640);
        session_regenerate_id(true);
        header('Location: ./?installed=1',true,303);exit;
    } catch (Throwable $e) {
        $error = $e instanceof PDOException ? 'Không kết nối hoặc khởi tạo được cơ sở dữ liệu. Kiểm tra lại thông tin DB.' : $e->getMessage();
    }
}
$defaultUrl=appUrl();
?><!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Cài đặt LightBill</title><style>
*{box-sizing:border-box}body{margin:0;background:#f4f7fb;color:#172033;font:15px system-ui,-apple-system,Segoe UI,sans-serif}.wrap{max-width:900px;margin:48px auto;padding:20px}.brand{font-size:30px;font-weight:800;margin-bottom:6px}.sub{color:#64748b;margin-bottom:24px}.card{background:#fff;border:1px solid #e2e8f0;border-radius:18px;padding:24px;box-shadow:0 12px 40px #0f172a0d}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}h2{font-size:18px;margin:22px 0 12px}.full{grid-column:1/-1}label{display:block;font-weight:650}input,select{width:100%;margin-top:7px;border:1px solid #cbd5e1;border-radius:10px;padding:11px 12px;font:inherit;background:#fff}.btn{width:100%;border:0;border-radius:11px;padding:13px;background:#2563eb;color:#fff;font-weight:750;font-size:15px;cursor:pointer}.error{background:#fff1f2;color:#9f1239;border:1px solid #fecdd3;padding:12px;border-radius:10px;margin-bottom:16px}.hint{font-size:12px;color:#64748b;margin-top:6px}@media(max-width:700px){.wrap{margin:10px auto}.grid{grid-template-columns:1fr}.full{grid-column:auto}}
</style></head><body><main class="wrap"><div class="brand">LightBill</div><div class="sub">Thiết lập hệ thống lần đầu</div><section class="card"><?php if($error!==''):?><div class="error"><?=h($error)?></div><?php endif?><form method="post" autocomplete="off"><input type="hidden" name="csrf" value="<?=h($_SESSION['lightbill_install_csrf'])?>"><div class="grid"><label class="full">Địa chỉ HTTPS<input name="app_url" value="<?=field('app_url',$defaultUrl)?>" required></label><h2 class="full">Cơ sở dữ liệu</h2><label>Máy chủ DB<input name="db_host" value="<?=field('db_host','localhost')?>" required></label><label>Cổng<input name="db_port" type="number" value="<?=field('db_port','3306')?>" required></label><label>Tên database<input name="db_name" value="<?=field('db_name')?>" required></label><label>Tài khoản DB<input name="db_user" value="<?=field('db_user')?>" required></label><label class="full">Mật khẩu DB<input name="db_pass" type="password"></label><h2 class="full">Doanh nghiệp</h2><label>Tên doanh nghiệp / cửa hàng<input name="company_name" value="<?=field('company_name')?>" required></label><label>Mã số thuế<input name="tax_code" value="<?=field('tax_code')?>"></label><label class="full">Địa chỉ<input name="address" value="<?=field('address')?>"></label><label>Chế độ kế toán<select name="accounting_regime"><option value="TT99_2025">Doanh nghiệp</option><option value="TT58_2026">Doanh nghiệp siêu nhỏ</option></select></label><div></div><h2 class="full">Tài khoản chủ sở hữu</h2><label>Họ tên<input name="owner_name" value="<?=field('owner_name')?>" required></label><label>Email đăng nhập<input name="owner_email" type="email" value="<?=field('owner_email')?>" required></label><label class="full">Mật khẩu<input name="owner_password" type="password" minlength="10" required><div class="hint">Tối thiểu 10 ký tự</div></label><div class="full"><button class="btn" type="submit">Cài đặt LightBill</button></div></div></form></section></main></body></html>
