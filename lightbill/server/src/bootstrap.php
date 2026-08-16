<?php
declare(strict_types=1);

namespace LightBill;

final class Env
{
    private static array $values = [];

    public static function load(string $file): void
    {
        if (!is_file($file)) {
            return;
        }
        foreach (file($file, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
            $line = trim($line);
            if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
                continue;
            }
            [$key, $value] = array_map('trim', explode('=', $line, 2));
            if (($value[0] ?? '') === '"' && str_ends_with($value, '"')) {
                $value = stripcslashes(substr($value, 1, -1));
            }
            self::$values[$key] = $value;
        }
    }

    public static function get(string $key, ?string $default = null): ?string
    {
        $value = $_ENV[$key] ?? getenv($key);
        if ($value !== false && $value !== null && $value !== '') {
            return (string) $value;
        }
        return self::$values[$key] ?? $default;
    }

    public static function require(string $key): string
    {
        $value = self::get($key);
        if ($value === null || $value === '') {
            throw new \RuntimeException("Missing required configuration: {$key}");
        }
        return $value;
    }
}

final class Crypto
{
    private static function key(): string
    {
        return hash('sha256', Env::require('APP_KEY'), true);
    }

    public static function encrypt(string $plain): string
    {
        if ($plain === '') {
            return '';
        }
        $iv = random_bytes(12);
        $tag = '';
        $cipher = openssl_encrypt($plain, 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, $iv, $tag, '', 16);
        if ($cipher === false) {
            throw new \RuntimeException('Không thể mã hóa dữ liệu bảo mật');
        }
        return 'v1:' . base64_encode($iv . $tag . $cipher);
    }

    public static function decrypt(string $encoded): string
    {
        if ($encoded === '') {
            return '';
        }
        if (!str_starts_with($encoded, 'v1:')) {
            throw new \RuntimeException('Định dạng dữ liệu bảo mật không hợp lệ');
        }
        $raw = base64_decode(substr($encoded, 3), true);
        if ($raw === false || strlen($raw) < 29) {
            throw new \RuntimeException('Dữ liệu bảo mật bị hỏng');
        }
        $iv = substr($raw, 0, 12);
        $tag = substr($raw, 12, 16);
        $cipher = substr($raw, 28);
        $plain = openssl_decrypt($cipher, 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, $iv, $tag);
        if ($plain === false) {
            throw new \RuntimeException('Không thể giải mã dữ liệu bảo mật');
        }
        return $plain;
    }
}

final class DB
{
    private static ?\PDO $pdo = null;

    public static function pdo(): \PDO
    {
        if (self::$pdo instanceof \PDO) {
            return self::$pdo;
        }
        $host = Env::require('DB_HOST');
        $port = Env::get('DB_PORT', '3306');
        $name = Env::require('DB_NAME');
        $charset = Env::get('DB_CHARSET', 'utf8mb4');
        $dsn = "mysql:host={$host};port={$port};dbname={$name};charset={$charset}";
        self::$pdo = new \PDO($dsn, Env::require('DB_USER'), Env::get('DB_PASS', ''), [
            \PDO::ATTR_ERRMODE => \PDO::ERRMODE_EXCEPTION,
            \PDO::ATTR_DEFAULT_FETCH_MODE => \PDO::FETCH_ASSOC,
            \PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        return self::$pdo;
    }
}

final class CompanySettings
{
    public static function get(int $companyId, string $key, ?string $default = null): ?string
    {
        $stmt = DB::pdo()->prepare('SELECT setting_value,is_secret FROM settings WHERE company_id=? AND setting_key=? LIMIT 1');
        $stmt->execute([$companyId, $key]);
        $row = $stmt->fetch();
        if (!$row) {
            return $default;
        }
        $value = (string)($row['setting_value'] ?? '');
        if ((int)($row['is_secret'] ?? 0) === 1 && $value !== '') {
            return Crypto::decrypt($value);
        }
        return $value;
    }

    public static function set(int $companyId, string $key, ?string $value, bool $secret = false): void
    {
        if ($companyId <= 0 || !preg_match('/^[a-z0-9._-]{2,100}$/i', $key)) {
            throw new \InvalidArgumentException('Cấu hình không hợp lệ');
        }
        $stored = (string)($value ?? '');
        if ($secret && $stored !== '') {
            $stored = Crypto::encrypt($stored);
        }
        DB::pdo()->prepare('INSERT INTO settings(company_id,setting_key,setting_value,is_secret) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),is_secret=VALUES(is_secret)')
            ->execute([$companyId, $key, $stored, $secret ? 1 : 0]);
    }
}

final class Response
{
    public static function json(array $data, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    public static function error(string $message, int $status = 400, array $details = []): never
    {
        self::json(['ok' => false, 'message' => $message, 'details' => $details], $status);
    }

    public static function ok(array $data = [], int $status = 200): never
    {
        self::json(['ok' => true] + $data, $status);
    }
}

final class Request
{
    public static function body(): array
    {
        $raw = file_get_contents('php://input') ?: '';
        if ($raw === '') {
            return $_POST;
        }
        $decoded = json_decode($raw, true);
        if (is_array($decoded)) {
            return $decoded;
        }
        parse_str($raw, $form);
        return is_array($form) ? $form : [];
    }

    public static function bearer(): ?string
    {
        $auth = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
        if (preg_match('/^Bearer\s+(.+)$/i', $auth, $m)) {
            return trim($m[1]);
        }
        return null;
    }

    public static function ip(): string
    {
        return substr((string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown'), 0, 64);
    }
}

final class Auth
{
    public static function rolePermissions(string $role): array
    {
        $map = [
            'owner' => ['*'],
            'admin' => ['dashboard','catalog.*','inventory.*','sales.*','purchases.*','customers.*','suppliers.*','payments.*','invoices.*','accounting.*','tax.*','reports.*','users.*','settings.*','zalopay.*','audit.read'],
            'accountant' => ['dashboard','catalog.read','inventory.read','sales.read','purchases.read','customers.read','suppliers.read','payments.*','invoices.*','accounting.*','tax.*','reports.*','zalopay.read'],
            'warehouse' => ['dashboard','catalog.*','inventory.*','purchases.*','sales.read','customers.read','suppliers.read','reports.inventory'],
            'cashier' => ['dashboard','catalog.read','inventory.read','sales.*','customers.*','payments.create','invoices.create','invoices.read','zalopay.*'],
            'viewer' => ['dashboard','catalog.read','inventory.read','sales.read','purchases.read','customers.read','suppliers.read','invoices.read','reports.read'],
        ];
        return $map[$role] ?? [];
    }

    public static function can(array $user, string $permission): bool
    {
        foreach (self::rolePermissions((string) $user['role']) as $granted) {
            if ($granted === '*' || $granted === $permission) {
                return true;
            }
            if (str_ends_with($granted, '.*') && str_starts_with($permission, substr($granted, 0, -1))) {
                return true;
            }
        }
        return false;
    }

    public static function requireUser(?string $permission = null): array
    {
        $token = Request::bearer();
        if (!$token) {
            Response::error('Chưa đăng nhập', 401);
        }
        $hash = hash('sha256', $token);
        $sql = "SELECT u.*, c.name company_name, c.accounting_regime, c.plan_code
                FROM api_tokens t JOIN users u ON u.id=t.user_id JOIN companies c ON c.id=u.company_id
                WHERE t.token_hash=? AND t.expires_at>NOW() AND u.is_active=1 AND c.is_active=1 LIMIT 1";
        $stmt = DB::pdo()->prepare($sql);
        $stmt->execute([$hash]);
        $user = $stmt->fetch();
        if (!$user) {
            Response::error('Phiên đăng nhập đã hết hạn', 401);
        }
        DB::pdo()->prepare('UPDATE api_tokens SET last_used_at=NOW() WHERE token_hash=?')->execute([$hash]);
        if ($permission !== null && !self::can($user, $permission)) {
            Response::error('Không đủ quyền thực hiện thao tác này', 403);
        }
        return $user;
    }

    public static function login(string $email, string $password): array
    {
        $pdo = DB::pdo();
        $ip = Request::ip();
        $limit = $pdo->prepare("SELECT COUNT(*) FROM login_attempts WHERE email=? AND ip_address=? AND success=0 AND created_at > (NOW() - INTERVAL 15 MINUTE)");
        $limit->execute([$email, $ip]);
        if ((int) $limit->fetchColumn() >= 8) {
            Response::error('Tạm khóa đăng nhập 15 phút do thử sai quá nhiều lần', 429);
        }

        $stmt = $pdo->prepare('SELECT * FROM users WHERE email=? AND is_active=1 LIMIT 1');
        $stmt->execute([$email]);
        $user = $stmt->fetch();
        $ok = $user && password_verify($password, (string) $user['password_hash']);
        $pdo->prepare('INSERT INTO login_attempts(email,ip_address,success) VALUES(?,?,?)')->execute([$email, $ip, $ok ? 1 : 0]);
        if (!$ok) {
            self::audit(null, null, 'auth.login_failed', 'user', null, ['email' => $email]);
            Response::error('Email hoặc mật khẩu không đúng', 401);
        }

        $plain = rtrim(strtr(base64_encode(random_bytes(48)), '+/', '-_'), '=');
        $hash = hash('sha256', $plain);
        $ttl = max(1, min(720, (int) Env::get('TOKEN_TTL_HOURS', '24')));
        $pdo->prepare("INSERT INTO api_tokens(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL {$ttl} HOUR))")
            ->execute([(int) $user['id'], $hash]);
        $pdo->prepare('UPDATE users SET last_login_at=NOW() WHERE id=?')->execute([(int) $user['id']]);
        self::audit((int) $user['company_id'], (int) $user['id'], 'auth.login', 'user', (string) $user['id']);
        unset($user['password_hash']);
        return ['token' => $plain, 'user' => $user];
    }

    public static function audit(?int $companyId, ?int $userId, string $action, ?string $entityType = null, ?string $entityId = null, array $metadata = []): void
    {
        try {
            DB::pdo()->prepare('INSERT INTO audit_logs(company_id,user_id,action,entity_type,entity_id,ip_address,user_agent,metadata_json) VALUES(?,?,?,?,?,?,?,?)')
                ->execute([$companyId, $userId, $action, $entityType, $entityId, Request::ip(), substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255), json_encode($metadata, JSON_UNESCAPED_UNICODE)]);
        } catch (\Throwable) {
        }
    }
}

final class Util
{
    public static function money(mixed $v): float { return round((float) $v, 2); }
    public static function qty(mixed $v): float { return round((float) $v, 3); }
    public static function text(mixed $v, int $max = 255): string { $v=trim((string)$v); return function_exists('mb_substr') ? mb_substr($v,0,$max) : substr($v,0,$max); }
    public static function now(): string { return date('Y-m-d H:i:s'); }
    public static function code(string $prefix): string { return $prefix . date('YmdHis') . strtoupper(bin2hex(random_bytes(2))); }
    public static function uuid4(): string
    {
        $d = random_bytes(16);
        $d[6] = chr((ord($d[6]) & 0x0f) | 0x40);
        $d[8] = chr((ord($d[8]) & 0x3f) | 0x80);
        $h = bin2hex($d);
        return substr($h,0,8).'-'.substr($h,8,4).'-'.substr($h,12,4).'-'.substr($h,16,4).'-'.substr($h,20);
    }

    public static function moneyToWords(float|int $amount): string
    {
        $n = (int)round((float)$amount);
        if ($n < 0) return 'Âm ' . lcfirst(self::moneyToWords(-$n));
        if ($n === 0) return 'Không đồng.';
        $units = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];
        $groups = [];
        while ($n > 0) { $groups[] = $n % 1000; $n = intdiv($n, 1000); }
        $parts = [];
        for ($i=count($groups)-1; $i>=0; $i--) {
            if ($groups[$i] === 0) continue;
            $full = $i < count($groups)-1 && $groups[$i] < 100;
            $chunk = self::readThreeDigits($groups[$i], $full);
            if ($units[$i] ?? '') $chunk .= ' ' . $units[$i];
            $parts[] = $chunk;
        }
        $text = implode(' ', $parts) . ' đồng.';
        return ucfirst($text);
    }

    private static function readThreeDigits(int $n, bool $full = false): string
    {
        $digit = ['không','một','hai','ba','bốn','năm','sáu','bảy','tám','chín'];
        $hundreds = intdiv($n,100); $tens = intdiv($n%100,10); $ones=$n%10; $out=[];
        if ($hundreds > 0 || $full) { $out[]=$digit[$hundreds]; $out[]='trăm'; }
        if ($tens > 1) { $out[]=$digit[$tens]; $out[]='mươi'; }
        elseif ($tens === 1) { $out[]='mười'; }
        elseif ($ones > 0 && ($hundreds > 0 || $full)) { $out[]='lẻ'; }
        if ($ones > 0) {
            if ($ones === 1 && $tens > 1) $out[]='mốt';
            elseif ($ones === 5 && $tens > 0) $out[]='lăm';
            elseif ($ones === 4 && $tens > 1) $out[]='tư';
            else $out[]=$digit[$ones];
        }
        return implode(' ', $out);
    }

    public static function rows(string $sql, array $params = []): array
    {
        $stmt = DB::pdo()->prepare($sql); $stmt->execute($params); return $stmt->fetchAll() ?: [];
    }
    public static function row(string $sql, array $params = []): ?array
    {
        $stmt = DB::pdo()->prepare($sql); $stmt->execute($params); $r = $stmt->fetch(); return $r ?: null;
    }
}

Env::load(dirname(__DIR__) . '/.env');
date_default_timezone_set(Env::get('APP_TIMEZONE', 'Asia/Ho_Chi_Minh') ?? 'Asia/Ho_Chi_Minh');
