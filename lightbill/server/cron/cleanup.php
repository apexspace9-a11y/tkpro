<?php
declare(strict_types=1);
require dirname(__DIR__) . '/src/bootstrap.php';
use LightBill\DB;
if(PHP_SAPI!=='cli'){http_response_code(403);exit;}
$pdo=DB::pdo();
$pdo->exec("DELETE FROM api_tokens WHERE expires_at < NOW() - INTERVAL 7 DAY");
$pdo->exec("DELETE FROM login_attempts WHERE created_at < NOW() - INTERVAL 30 DAY");
echo "ok\n";
