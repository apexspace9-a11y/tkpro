<?php
declare(strict_types=1);
require dirname(__DIR__) . '/src/bootstrap.php';
require dirname(__DIR__) . '/src/BusinessService.php';
use LightBill\{BusinessService,DB,Env,Util};

$pdo=DB::pdo();
if((int)$pdo->query('SELECT COUNT(*) FROM users')->fetchColumn()>0){http_response_code(404);exit;}
$expected=Env::get('APP_SETUP_TOKEN','');
$error='';$done=false;
if($_SERVER['REQUEST_METHOD']==='POST'){
 $token=(string)($_POST['setup_token']??'');
 if(!$expected || !hash_equals($expected,$token)){$error='Mã thiết lập không đúng.';}
 else{
  $name=Util::text($_POST['company_name']??'',190);$tax=Util::text($_POST['tax_code']??'',32);$email=strtolower(Util::text($_POST['email']??'',190));$password=(string)($_POST['password']??'');
  if($name===''||!filter_var($email,FILTER_VALIDATE_EMAIL)||strlen($password)<10){$error='Thông tin chưa hợp lệ. Mật khẩu tối thiểu 10 ký tự.';}
  else{$pdo->beginTransaction();try{
   $pdo->prepare('INSERT INTO companies(name,tax_code,address,phone,email) VALUES(?,?,?,?,?)')->execute([$name,$tax?:null,Util::text($_POST['address']??'',255),Util::text($_POST['phone']??'',32),$email]);$cid=(int)$pdo->lastInsertId();
   $pdo->prepare('INSERT INTO branches(company_id,code,name,address) VALUES(?,?,?,?)')->execute([$cid,'MAIN','Trụ sở chính',Util::text($_POST['address']??'',255)]);$bid=(int)$pdo->lastInsertId();
   $pdo->prepare('INSERT INTO warehouses(company_id,branch_id,code,name,address) VALUES(?,?,?,?,?)')->execute([$cid,$bid,'KHO1','Kho chính',Util::text($_POST['address']??'',255)]);
   $pdo->prepare('INSERT INTO users(company_id,branch_id,full_name,email,phone,password_hash,role,level) VALUES(?,?,?,?,?,?,?,?,?)')->execute([$cid,$bid,Util::text($_POST['full_name']??'Chủ doanh nghiệp',190),$email,Util::text($_POST['phone']??'',32),password_hash($password,PASSWORD_DEFAULT),'owner',100]);
   BusinessService::ensureDefaultAccounts($cid);$pdo->commit();$done=true;
  }catch(Throwable $e){$pdo->rollBack();$error='Không thể hoàn tất thiết lập.';}}
 }
}
?><!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Thiết lập LightBill</title><style>body{margin:0;background:#f4f7fb;font:15px system-ui;color:#172033;display:grid;place-items:center;min-height:100vh}.box{width:min(620px,calc(100% - 30px));background:#fff;padding:28px;border-radius:20px;box-shadow:0 18px 60px #0f172a20;box-sizing:border-box}.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}label{font-size:13px;color:#475569}input{width:100%;box-sizing:border-box;margin-top:6px;padding:11px;border:1px solid #dbe2ea;border-radius:10px}.full{grid-column:1/-1}button,a{display:inline-block;border:0;border-radius:10px;padding:12px 16px;background:#2563eb;color:#fff;text-decoration:none;font-weight:700}.err{color:#dc2626;margin:10px 0}.ok{color:#166534;background:#dcfce7;padding:15px;border-radius:12px}@media(max-width:600px){.grid{grid-template-columns:1fr}.full{grid-column:auto}}</style></head><body><div class="box"><h1>LightBill</h1><?php if($done):?><div class="ok">Thiết lập hoàn tất. Hãy xóa APP_SETUP_TOKEN khỏi .env và đăng nhập.</div><p><a href="./">Mở LightBill</a></p><?php else:?><p>Khởi tạo doanh nghiệp và tài khoản chủ sở hữu.</p><?php if($error):?><div class="err"><?=htmlspecialchars($error,ENT_QUOTES,'UTF-8')?></div><?php endif;?><form method="post" class="grid"><label class="full">Mã thiết lập<input name="setup_token" type="password" required></label><label>Tên doanh nghiệp<input name="company_name" required></label><label>Mã số thuế<input name="tax_code"></label><label>Họ tên chủ tài khoản<input name="full_name" required></label><label>Email<input name="email" type="email" required></label><label>Điện thoại<input name="phone"></label><label>Mật khẩu<input name="password" type="password" minlength="10" required></label><label class="full">Địa chỉ<input name="address"></label><div class="full"><button>Khởi tạo</button></div></form><?php endif;?></div></body></html>
