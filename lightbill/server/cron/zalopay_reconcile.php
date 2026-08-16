<?php
declare(strict_types=1);

use LightBill\{DB,Env,Util,ZaloPayService};

require dirname(__DIR__) . '/src/bootstrap.php';
require dirname(__DIR__) . '/src/BusinessService.php';
require dirname(__DIR__) . '/src/ZaloPayService.php';

$rows = Util::rows("SELECT company_id,app_trans_id FROM zalopay_transactions WHERE status='pending' AND created_at >= (NOW() - INTERVAL 1 DAY) ORDER BY id ASC LIMIT 100");
foreach ($rows as $row) {
    try {
        (new ZaloPayService((int)$row['company_id']))->queryAndSync((string)$row['app_trans_id']);
    } catch (Throwable $e) {
        error_log('[LightBill ZaloPay Reconcile] ' . $e->getMessage());
    }
}
