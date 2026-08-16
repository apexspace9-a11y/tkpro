<?php
declare(strict_types=1);
require dirname(__DIR__) . '/src/bootstrap.php';
use LightBill\{DB,BusinessService};
require dirname(__DIR__) . '/src/BusinessService.php';

if (PHP_SAPI !== 'cli') { http_response_code(403); exit("CLI only\n"); }
$name = $argv[1] ?? 'LightBill';
$tax = $argv[2] ?? null;
$email = $argv[3] ?? 'admin@example.com';
$password = $argv[4] ?? '';
if (strlen($password) < 10) { fwrite(STDERR, "Password must be at least 10 characters\n"); exit(1); }
$pdo=DB::pdo();$pdo->beginTransaction();
try{
 $pdo->prepare('INSERT INTO companies(name,tax_code) VALUES(?,?)')->execute([$name,$tax?:null]);$cid=(int)$pdo->lastInsertId();
 $pdo->prepare('INSERT INTO branches(company_id,code,name) VALUES(?,?,?)')->execute([$cid,'MAIN','Trụ sở chính']);$bid=(int)$pdo->lastInsertId();
 $pdo->prepare('INSERT INTO warehouses(company_id,branch_id,code,name) VALUES(?,?,?,?)')->execute([$cid,$bid,'KHO1','Kho chính']);
 $pdo->prepare('INSERT INTO users(company_id,branch_id,full_name,email,password_hash,role,level) VALUES(?,?,?,?,?,?,?)')->execute([$cid,$bid,'Chủ doanh nghiệp',strtolower($email),password_hash($password,PASSWORD_DEFAULT),'owner',100]);
 BusinessService::ensureDefaultAccounts($cid);$pdo->commit();echo "Created company and owner account.\n";
}catch(Throwable $e){$pdo->rollBack();fwrite(STDERR,$e->getMessage()."\n");exit(1);}
