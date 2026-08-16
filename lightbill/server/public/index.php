<?php
declare(strict_types=1);

use LightBill\{Auth,BusinessService,CompanySettings,Crypto,DB,Env,MisaEInvoiceService,Request,Response,Util,ZaloPayService};

$root = dirname(__DIR__);
$prePath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
if ((!is_file($root . '/.env') || !is_file($root . '/storage/installed.lock')) && !preg_match('#/install(?:\.php)?/?$#', $prePath)) {
    $installBase=rtrim(str_replace('\\','/',dirname((string)($_SERVER['SCRIPT_NAME']??'/index.php'))), '/.');
    header('Location: '.($installBase?:'').'/install', true, 302);
    exit;
}
require $root . '/src/bootstrap.php';
require $root . '/src/BusinessService.php';
require $root . '/src/ZaloPayService.php';
require $root . '/src/MisaEInvoiceService.php';

$origin = $_SERVER['HTTP_ORIGIN'] ?? '';
$allowedOrigins = array_filter(array_map('trim', explode(',', Env::get('CORS_ORIGINS', Env::get('APP_URL','')) ?? '')));
if ($origin && in_array($origin, $allowedOrigins, true)) {
    header('Access-Control-Allow-Origin: ' . $origin);
    header('Vary: Origin');
    header('Access-Control-Allow-Headers: Authorization, Content-Type');
    header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') { http_response_code(204); exit; }
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: SAMEORIGIN');
header('Referrer-Policy: strict-origin-when-cross-origin');
header("Permissions-Policy: camera=(), microphone=(), geolocation=()");
if ((Env::get('APP_ENV','production') ?? 'production') === 'production') {
    header("Content-Security-Policy: default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'self'");
}

$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$basePath = rtrim((string) parse_url(Env::get('APP_URL','') ?? '', PHP_URL_PATH), '/');
if ($basePath && str_starts_with($path, $basePath)) $path = substr($path, strlen($basePath)) ?: '/';

try {
    if ($path === '/api/health' && $method === 'GET') {
        DB::pdo()->query('SELECT 1'); Response::ok(['service'=>'lightbill','version'=>Env::get('APP_VERSION','2.0.0')]);
    }
    if ($path === '/api/auth/login' && $method === 'POST') {
        $b=Request::body(); $email=strtolower(Util::text($b['email']??'',190)); $password=(string)($b['password']??'');
        if(!filter_var($email,FILTER_VALIDATE_EMAIL)||$password==='') Response::error('Thông tin đăng nhập không hợp lệ',422);
        Response::ok(Auth::login($email,$password));
    }
    if ($path === '/api/auth/logout' && $method === 'POST') {
        $u=Auth::requireUser(); $t=Request::bearer(); if($t) DB::pdo()->prepare('DELETE FROM api_tokens WHERE token_hash=?')->execute([hash('sha256',$t)]); Auth::audit((int)$u['company_id'],(int)$u['id'],'auth.logout','user',(string)$u['id']); Response::ok();
    }
    if ($path === '/api/me' && $method === 'GET') {
        $u=Auth::requireUser(); unset($u['password_hash']); Response::ok(['user'=>$u,'permissions'=>Auth::rolePermissions((string)$u['role'])]);
    }
    if ($path === '/api/dashboard' && $method === 'GET') {
        $u=Auth::requireUser('dashboard');$cid=(int)$u['company_id'];
        $today=Util::row("SELECT COALESCE(SUM(total_amount),0) revenue,COUNT(*) orders FROM sales WHERE company_id=? AND status<>'cancelled' AND DATE(sale_date)=CURDATE()",[$cid]);
        $month=Util::row("SELECT COALESCE(SUM(total_amount),0) revenue,COALESCE(SUM(tax_amount),0) tax,COUNT(*) orders FROM sales WHERE company_id=? AND status<>'cancelled' AND YEAR(sale_date)=YEAR(CURDATE()) AND MONTH(sale_date)=MONTH(CURDATE())",[$cid]);
        $stock=Util::row('SELECT COALESCE(SUM(sb.qty*sb.avg_cost),0) stock_value,COUNT(DISTINCT sb.product_id) products FROM stock_balances sb WHERE sb.company_id=?',[$cid]);
        $low=Util::rows('SELECT p.id,p.sku,p.name,COALESCE(SUM(sb.qty),0) qty,p.min_stock FROM products p LEFT JOIN stock_balances sb ON sb.product_id=p.id AND sb.company_id=p.company_id WHERE p.company_id=? AND p.track_stock=1 GROUP BY p.id HAVING qty<=p.min_stock ORDER BY qty ASC LIMIT 10',[$cid]);
        Response::ok(['today'=>$today,'month'=>$month,'stock'=>$stock,'low_stock'=>$low]);
    }

    $listMap = [
        '/api/products'=>['table'=>'products','perm'=>'catalog','order'=>'id DESC','fields'=>['sku','barcode','name','unit','cost_price','sale_price','vat_rate','vat_category','min_stock','track_stock','category_id','is_active']],
        '/api/customers'=>['table'=>'customers','perm'=>'customers','order'=>'id DESC','fields'=>['code','name','tax_code','personal_id','email','phone','address','credit_limit','opening_balance','is_active']],
        '/api/suppliers'=>['table'=>'suppliers','perm'=>'suppliers','order'=>'id DESC','fields'=>['code','name','tax_code','email','phone','address','opening_balance','is_active']],
        '/api/warehouses'=>['table'=>'warehouses','perm'=>'inventory','order'=>'id DESC','fields'=>['branch_id','code','name','address','is_active']],
    ];
    if(isset($listMap[$path])){
        $m=$listMap[$path];$perm=$m['perm'].($method==='GET'?'.read':'.write');$u=Auth::requireUser($perm);$cid=(int)$u['company_id'];$pdo=DB::pdo();
        if($method==='GET'){$q=Util::text($_GET['q']??'',100);$sql='SELECT * FROM '.$m['table'].' WHERE company_id=?';$params=[$cid];if($q!==''&&in_array('name',$m['fields'],true)){$sql.=' AND (name LIKE ?'.(in_array('sku',$m['fields'],true)?' OR sku LIKE ?':'').')';$params[]='%'.$q.'%';if(in_array('sku',$m['fields'],true))$params[]='%'.$q.'%';}$sql.=' ORDER BY '.$m['order'].' LIMIT 300';Response::ok(['items'=>Util::rows($sql,$params)]);}
        if($method==='POST'){$b=Request::body();if(trim((string)($b['name']??''))==='')Response::error('Thiếu tên',422);if($m['table']==='products'&&trim((string)($b['sku']??''))==='')Response::error('Thiếu mã SKU',422);if($m['table']==='warehouses'&&trim((string)($b['code']??''))==='')Response::error('Thiếu mã kho',422);if($m['table']==='warehouses'&&!empty($b['branch_id'])&&!Util::row('SELECT id FROM branches WHERE id=? AND company_id=? AND is_active=1',[(int)$b['branch_id'],$cid]))Response::error('Chi nhánh không hợp lệ',422);if($m['table']==='products'&&!empty($b['category_id'])&&!Util::row('SELECT id FROM categories WHERE id=? AND company_id=?',[(int)$b['category_id'],$cid]))Response::error('Danh mục không hợp lệ',422);if($m['table']==='products'){foreach(['cost_price','sale_price','min_stock'] as $n){if(isset($b[$n])&&(float)$b[$n]<0)Response::error('Giá trị sản phẩm không hợp lệ',422);}if(isset($b['vat_rate'])&&$b['vat_rate']!==''&&((float)$b['vat_rate']<0||(float)$b['vat_rate']>100))Response::error('Thuế suất không hợp lệ',422);}$cols=['company_id'];$vals=[$cid];$qs=['?'];foreach($m['fields'] as $f){if(array_key_exists($f,$b)){$cols[]=$f;$vals[]=$b[$f]===''?null:$b[$f];$qs[]='?';}}$sql='INSERT INTO '.$m['table'].'('.implode(',',$cols).') VALUES('.implode(',',$qs).')';try{$pdo->prepare($sql)->execute($vals);}catch(PDOException $e){if($e->getCode()==='23000')Response::error('Mã hoặc dữ liệu đã tồn tại',409);throw $e;}$id=(int)$pdo->lastInsertId();Auth::audit($cid,(int)$u['id'],$m['table'].'.create',$m['table'],(string)$id);Response::ok(['item'=>Util::row('SELECT * FROM '.$m['table'].' WHERE id=? AND company_id=?',[$id,$cid])],201);}
    }

    if (preg_match('#^/api/(products|customers|suppliers|warehouses)/(\d+)$#',$path,$m)) {
        $cfg=$listMap['/api/'.$m[1]];$id=(int)$m[2];$u=Auth::requireUser($cfg['perm'].'.write');$cid=(int)$u['company_id'];
        if($method==='PUT'||$method==='PATCH'){$b=Request::body();$sets=[];$vals=[];foreach($cfg['fields'] as $f){if(array_key_exists($f,$b)){$sets[]=$f.'=?';$vals[]=$b[$f]===''?null:$b[$f];}}if(!$sets)Response::error('Không có dữ liệu thay đổi',422);$vals[]=$id;$vals[]=$cid;DB::pdo()->prepare('UPDATE '.$cfg['table'].' SET '.implode(',',$sets).' WHERE id=? AND company_id=?')->execute($vals);Auth::audit($cid,(int)$u['id'],$cfg['table'].'.update',$cfg['table'],(string)$id);Response::ok(['item'=>Util::row('SELECT * FROM '.$cfg['table'].' WHERE id=? AND company_id=?',[$id,$cid])]);}
        if($method==='DELETE'){DB::pdo()->prepare('UPDATE '.$cfg['table'].' SET is_active=0 WHERE id=? AND company_id=?')->execute([$id,$cid]);Auth::audit($cid,(int)$u['id'],$cfg['table'].'.archive',$cfg['table'],(string)$id);Response::ok();}
    }

    if($path==='/api/inventory'&&$method==='GET'){$u=Auth::requireUser('inventory.read');$cid=(int)$u['company_id'];$rows=Util::rows('SELECT w.id warehouse_id,w.name warehouse,p.id product_id,p.sku,p.name,p.unit,p.min_stock,COALESCE(sb.qty,0) qty,COALESCE(sb.avg_cost,p.cost_price) avg_cost,COALESCE(sb.qty*sb.avg_cost,0) stock_value FROM products p CROSS JOIN warehouses w LEFT JOIN stock_balances sb ON sb.company_id=p.company_id AND sb.product_id=p.id AND sb.warehouse_id=w.id WHERE p.company_id=? AND w.company_id=? AND p.is_active=1 AND w.is_active=1 ORDER BY p.name,w.name LIMIT 2000',[$cid,$cid]);Response::ok(['items'=>$rows]);}
    if($path==='/api/inventory/adjust'&&$method==='POST'){$u=Auth::requireUser('inventory.write');$cid=(int)$u['company_id'];$b=Request::body();$wid=(int)($b['warehouse_id']??0);$pid=(int)($b['product_id']??0);$qty=Util::qty($b['qty_change']??0);if(!$wid||!$pid||abs($qty)<0.0001)Response::error('Dữ liệu điều chỉnh không hợp lệ',422);if(!Util::row('SELECT id FROM warehouses WHERE id=? AND company_id=? AND is_active=1',[$wid,$cid])||!Util::row('SELECT id FROM products WHERE id=? AND company_id=? AND is_active=1',[$pid,$cid]))Response::error('Kho hoặc sản phẩm không hợp lệ',422);$pdo=DB::pdo();$pdo->beginTransaction();try{$stock=Util::row('SELECT qty,avg_cost FROM stock_balances WHERE company_id=? AND warehouse_id=? AND product_id=? FOR UPDATE',[$cid,$wid,$pid]);$new=(float)($stock['qty']??0)+$qty;if($new<0)throw new RuntimeException('Tồn kho không thể âm');$cost=(float)($stock['avg_cost']??0);$pdo->prepare('INSERT INTO stock_balances(company_id,warehouse_id,product_id,qty,avg_cost) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE qty=VALUES(qty)')->execute([$cid,$wid,$pid,$new,$cost]);$pdo->prepare('INSERT INTO inventory_movements(company_id,warehouse_id,product_id,movement_type,qty_change,unit_cost,note,occurred_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)')->execute([$cid,$wid,$pid,'adjustment',$qty,$cost,Util::text($b['note']??'',255),Util::now(),(int)$u['id']]);$pdo->commit();Auth::audit($cid,(int)$u['id'],'inventory.adjust','product',(string)$pid,['qty_change'=>$qty]);Response::ok();}catch(Throwable $e){$pdo->rollBack();Response::error($e->getMessage(),422);}}

    if($path==='/api/sales'&&$method==='GET'){$u=Auth::requireUser('sales.read');$cid=(int)$u['company_id'];Response::ok(['items'=>Util::rows('SELECT s.*,c.name customer_name,w.name warehouse_name FROM sales s LEFT JOIN customers c ON c.id=s.customer_id JOIN warehouses w ON w.id=s.warehouse_id WHERE s.company_id=? ORDER BY s.sale_date DESC,s.id DESC LIMIT 500',[$cid])]);}
    if($path==='/api/sales'&&$method==='POST'){$u=Auth::requireUser('sales.create');Response::ok(['item'=>BusinessService::createSale($u,Request::body())],201);}
    if(preg_match('#^/api/sales/(\d+)$#',$path,$m)&&$method==='GET'){$u=Auth::requireUser('sales.read');$r=BusinessService::saleDetail((int)$u['company_id'],(int)$m[1]);if(!$r)Response::error('Không tìm thấy đơn bán',404);Response::ok(['item'=>$r]);}

    if($path==='/api/purchases'&&$method==='GET'){$u=Auth::requireUser('purchases.read');$cid=(int)$u['company_id'];Response::ok(['items'=>Util::rows('SELECT p.*,s.name supplier_name,w.name warehouse_name FROM purchases p LEFT JOIN suppliers s ON s.id=p.supplier_id JOIN warehouses w ON w.id=p.warehouse_id WHERE p.company_id=? ORDER BY p.purchase_date DESC,p.id DESC LIMIT 500',[$cid])]);}
    if($path==='/api/purchases'&&$method==='POST'){$u=Auth::requireUser('purchases.create');Response::ok(['item'=>BusinessService::createPurchase($u,Request::body())],201);}

    if($path==='/api/invoices'&&$method==='GET'){$u=Auth::requireUser('invoices.read');$cid=(int)$u['company_id'];Response::ok(['items'=>Util::rows('SELECT * FROM invoices WHERE company_id=? ORDER BY invoice_date DESC,id DESC LIMIT 500',[$cid])]);}
    if($path==='/api/invoices/from-sale'&&$method==='POST'){$u=Auth::requireUser('invoices.create');$b=Request::body();Response::ok(['item'=>BusinessService::createInvoiceFromSale($u,(int)($b['sale_id']??0),$b)],201);}
    if(preg_match('#^/api/invoices/(\d+)/submit$#',$path,$m)&&$method==='POST'){$u=Auth::requireUser('invoices.submit');Response::ok(['item'=>BusinessService::submitInvoice($u,(int)$m[1])]);}
    if(preg_match('#^/api/invoices/(\d+)$#',$path,$m)&&$method==='GET'){$u=Auth::requireUser('invoices.read');$r=BusinessService::invoiceDetail((int)$u['company_id'],(int)$m[1]);if(!$r)Response::error('Không tìm thấy hóa đơn',404);Response::ok(['item'=>$r]);}

    if($path==='/api/accounting/accounts'&&$method==='GET'){$u=Auth::requireUser('accounting.read');BusinessService::ensureDefaultAccounts((int)$u['company_id']);Response::ok(['items'=>Util::rows('SELECT * FROM chart_accounts WHERE company_id=? ORDER BY code',[(int)$u['company_id']])]);}
    if($path==='/api/accounting/journals'&&$method==='GET'){$u=Auth::requireUser('accounting.read');$cid=(int)$u['company_id'];Response::ok(['items'=>Util::rows("SELECT je.*,u.full_name created_by_name,(SELECT SUM(debit) FROM journal_lines jl WHERE jl.journal_entry_id=je.id) total_debit FROM journal_entries je LEFT JOIN users u ON u.id=je.created_by WHERE je.company_id=? ORDER BY je.entry_date DESC,je.id DESC LIMIT 500",[$cid])]);}
    if($path==='/api/accounting/journals'&&$method==='POST'){$u=Auth::requireUser('accounting.write');$cid=(int)$u['company_id'];$b=Request::body();$lines=is_array($b['lines']??null)?$b['lines']:[];if(count($lines)<2)Response::error('Bút toán cần ít nhất hai dòng',422);BusinessService::ensureDefaultAccounts($cid);$debit=0.0;$credit=0.0;$prepared=[];foreach($lines as $line){$account=Util::text($line['account_code']??'',32);$d=max(0,Util::money($line['debit']??0));$c=max(0,Util::money($line['credit']??0));if($account===''||($d>0&&$c>0)||($d<=0&&$c<=0)||!Util::row('SELECT id FROM chart_accounts WHERE company_id=? AND code=? AND is_active=1',[$cid,$account]))Response::error('Dòng bút toán không hợp lệ',422);$debit+=$d;$credit+=$c;$prepared[]=[$account,$d,$c,Util::text($line['description']??'',255)];}if(abs($debit-$credit)>0.01||$debit<=0)Response::error('Tổng Nợ và Có phải bằng nhau',422);$pdo=DB::pdo();$pdo->beginTransaction();try{$code=Util::text($b['code']??'',50)?:Util::code('KT');$date=Util::text($b['entry_date']??date('Y-m-d'),10);$desc=Util::text($b['description']??'',255);if($desc==='')Response::error('Thiếu diễn giải bút toán',422);$pdo->prepare('INSERT INTO journal_entries(company_id,code,entry_date,description,status,created_by) VALUES(?,?,?,?,?,?)')->execute([$cid,$code,$date,$desc,'posted',(int)$u['id']]);$jid=(int)$pdo->lastInsertId();$st=$pdo->prepare('INSERT INTO journal_lines(journal_entry_id,account_code,debit,credit,description) VALUES(?,?,?,?,?)');foreach($prepared as $line)$st->execute([$jid,$line[0],$line[1],$line[2],$line[3]]);$pdo->commit();Auth::audit($cid,(int)$u['id'],'journal.create','journal_entry',(string)$jid,['debit'=>$debit]);Response::ok(['id'=>$jid],201);}catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}}
    if($path==='/api/accounting/trial-balance'&&$method==='GET'){$u=Auth::requireUser('accounting.read');$cid=(int)$u['company_id'];$from=Util::text($_GET['from']??date('Y-01-01'),10);$to=Util::text($_GET['to']??date('Y-m-d'),10);$rows=Util::rows("SELECT jl.account_code,COALESCE(ca.name,'') account_name,SUM(jl.debit) debit,SUM(jl.credit) credit,SUM(jl.debit-jl.credit) balance FROM journal_lines jl JOIN journal_entries je ON je.id=jl.journal_entry_id LEFT JOIN chart_accounts ca ON ca.company_id=je.company_id AND ca.code=jl.account_code WHERE je.company_id=? AND je.status='posted' AND je.entry_date BETWEEN ? AND ? GROUP BY jl.account_code,ca.name ORDER BY jl.account_code",[$cid,$from,$to]);Response::ok(['from'=>$from,'to'=>$to,'items'=>$rows]);}
    if($path==='/api/reports/profit-loss'&&$method==='GET'){$u=Auth::requireUser('reports.read');$cid=(int)$u['company_id'];$from=Util::text($_GET['from']??date('Y-01-01'),10);$to=Util::text($_GET['to']??date('Y-m-d'),10);$row=Util::row("SELECT COALESCE(SUM(CASE WHEN ca.account_type='revenue' THEN jl.credit-jl.debit ELSE 0 END),0) revenue,COALESCE(SUM(CASE WHEN ca.account_type='expense' THEN jl.debit-jl.credit ELSE 0 END),0) expense FROM journal_lines jl JOIN journal_entries je ON je.id=jl.journal_entry_id JOIN chart_accounts ca ON ca.company_id=je.company_id AND ca.code=jl.account_code WHERE je.company_id=? AND je.status='posted' AND je.entry_date BETWEEN ? AND ?",[$cid,$from,$to]);$row['profit']=(float)$row['revenue']-(float)$row['expense'];Response::ok(['from'=>$from,'to'=>$to,'summary'=>$row]);}
    if($path==='/api/tax/vat-report'&&$method==='GET'){$u=Auth::requireUser('tax.read');$cid=(int)$u['company_id'];$from=Util::text($_GET['from']??date('Y-m-01'),10);$to=Util::text($_GET['to']??date('Y-m-t'),10);$out=Util::row("SELECT COALESCE(SUM(tax_amount),0) tax,COALESCE(SUM(subtotal),0) taxable FROM sales WHERE company_id=? AND status<>'cancelled' AND DATE(sale_date) BETWEEN ? AND ?",[$cid,$from,$to]);$in=Util::row("SELECT COALESCE(SUM(tax_amount),0) tax,COALESCE(SUM(subtotal),0) taxable FROM purchases WHERE company_id=? AND status<>'cancelled' AND DATE(purchase_date) BETWEEN ? AND ?",[$cid,$from,$to]);Response::ok(['from'=>$from,'to'=>$to,'output'=>$out,'input'=>$in,'payable'=>max(0,(float)$out['tax']-(float)$in['tax'])]);}

    if($path==='/api/users'&&$method==='GET'){$u=Auth::requireUser('users.read');$cid=(int)$u['company_id'];Response::ok(['items'=>Util::rows('SELECT id,branch_id,full_name,email,phone,role,level,is_active,last_login_at,created_at FROM users WHERE company_id=? ORDER BY level DESC,id DESC',[$cid])]);}
    if($path==='/api/users'&&$method==='POST'){$u=Auth::requireUser('users.write');$cid=(int)$u['company_id'];$b=Request::body();$email=strtolower(Util::text($b['email']??'',190));$role=Util::text($b['role']??'viewer',32);$roles=['owner','admin','accountant','warehouse','cashier','viewer'];$branchId=!empty($b['branch_id'])?(int)$b['branch_id']:null;if(!filter_var($email,FILTER_VALIDATE_EMAIL)||!in_array($role,$roles,true)||strlen((string)($b['password']??''))<8||trim((string)($b['full_name']??''))==='')Response::error('Thông tin tài khoản không hợp lệ',422);if($branchId&&!Util::row('SELECT id FROM branches WHERE id=? AND company_id=? AND is_active=1',[$branchId,$cid]))Response::error('Chi nhánh không hợp lệ',422);try{DB::pdo()->prepare('INSERT INTO users(company_id,branch_id,full_name,email,phone,password_hash,role,level,is_active) VALUES(?,?,?,?,?,?,?,?,1)')->execute([$cid,$branchId,Util::text($b['full_name']??'',190),$email,Util::text($b['phone']??'',32),password_hash((string)$b['password'],PASSWORD_DEFAULT),$role,(int)($b['level']??10)]);}catch(PDOException $e){if($e->getCode()==='23000')Response::error('Email đã tồn tại',409);throw $e;}$id=(int)DB::pdo()->lastInsertId();Auth::audit($cid,(int)$u['id'],'user.create','user',(string)$id,['role'=>$role]);Response::ok(['id'=>$id],201);}

    if($path==='/api/settings'&&$method==='GET'){$u=Auth::requireUser('settings.read');$cid=(int)$u['company_id'];$rows=Util::rows('SELECT setting_key,CASE WHEN is_secret=1 THEN NULL ELSE setting_value END setting_value,is_secret,updated_at FROM settings WHERE company_id=? ORDER BY setting_key',[$cid]);Response::ok(['items'=>$rows,'company'=>Util::row('SELECT id,name,tax_code,address,phone,email,accounting_regime,fiscal_year_start_month,plan_code FROM companies WHERE id=?',[$cid])]);}
    if($path==='/api/settings'&&$method==='POST'){$u=Auth::requireUser('settings.write');$cid=(int)$u['company_id'];$b=Request::body();$key=preg_replace('/[^a-zA-Z0-9_.-]/','',Util::text($b['key']??'',100));if(!$key)Response::error('Khóa cấu hình không hợp lệ',422);$secret=!empty($b['is_secret']);CompanySettings::set($cid,$key,(string)($b['value']??''),$secret);Auth::audit($cid,(int)$u['id'],'settings.update','setting',$key);Response::ok();}

    if($path==='/api/integrations'&&$method==='GET'){$u=Auth::requireUser('settings.read');$cid=(int)$u['company_id'];$zp=['environment'=>CompanySettings::get($cid,'zalopay.environment',Env::get('ZALOPAY_ENV','sandbox')),'app_id'=>CompanySettings::get($cid,'zalopay.app_id',Env::get('ZALOPAY_APP_ID','2554')),'key1_configured'=>(CompanySettings::get($cid,'zalopay.key1',Env::get('ZALOPAY_KEY1',''))??'')!=='','key2_configured'=>(CompanySettings::get($cid,'zalopay.key2',Env::get('ZALOPAY_KEY2',''))??'')!==''];$ei=(new MisaEInvoiceService($cid))->status();Response::ok(['zalopay'=>$zp,'einvoice'=>$ei]);}
    if($path==='/api/integrations'&&$method==='POST'){$u=Auth::requireUser('settings.write');$cid=(int)$u['company_id'];$b=Request::body();$type=(string)($b['type']??'');if($type==='zalopay'){$env=in_array(($b['environment']??''),['sandbox','production'],true)?$b['environment']:'sandbox';CompanySettings::set($cid,'zalopay.environment',$env);if(isset($b['app_id'])&&trim((string)$b['app_id'])!=='')CompanySettings::set($cid,'zalopay.app_id',(string)(int)$b['app_id']);if(trim((string)($b['key1']??''))!=='')CompanySettings::set($cid,'zalopay.key1',(string)$b['key1'],true);if(trim((string)($b['key2']??''))!=='')CompanySettings::set($cid,'zalopay.key2',(string)$b['key2'],true);Auth::audit($cid,(int)$u['id'],'integration.update','integration','zalopay',['environment'=>$env]);Response::ok();}if($type==='einvoice'){$env=in_array(($b['environment']??''),['sandbox','production'],true)?$b['environment']:'sandbox';$signType=in_array((int)($b['sign_type']??2),[2,5],true)?(int)$b['sign_type']:2;CompanySettings::set($cid,'einvoice.provider','misa_meinvoice');CompanySettings::set($cid,'einvoice.environment',$env);foreach(['app_id','tax_code','username','inv_series'] as $k){if(array_key_exists($k,$b))CompanySettings::set($cid,'einvoice.misa.'.$k,Util::text($b[$k]??'',190));}if(trim((string)($b['password']??''))!=='')CompanySettings::set($cid,'einvoice.misa.password',(string)$b['password'],true);CompanySettings::set($cid,'einvoice.misa.sign_type',(string)$signType);Auth::audit($cid,(int)$u['id'],'integration.update','integration','misa_meinvoice',['environment'=>$env,'sign_type'=>$signType]);Response::ok();}Response::error('Loại tích hợp không hợp lệ',422);}
    if($path==='/api/integrations/einvoice/test'&&$method==='POST'){$u=Auth::requireUser('settings.write');Response::ok((new MisaEInvoiceService((int)$u['company_id']))->testConnection());}

    if($path==='/api/zalopay/create'&&$method==='POST'){$u=Auth::requireUser('zalopay.create');$b=Request::body();$amount=(int)($b['amount']??0);$saleId=!empty($b['sale_id'])?(int)$b['sale_id']:null;if($saleId){$sale=BusinessService::saleDetail((int)$u['company_id'],$saleId);if(!$sale)Response::error('Không tìm thấy đơn bán',404);$amount=(int)round((float)$sale['total_amount']-(float)$sale['paid_amount']);}$r=(new ZaloPayService((int)$u['company_id']))->createPayment($u,$amount,$saleId,Util::text($b['description']??'',250)?:null);Response::ok(['payment'=>$r]);}
    if($path==='/api/zalopay/callback'&&$method==='POST'){$b=Request::body();try{ZaloPayService::fromCallback($b)->processCallback($b);Response::json(['return_code'=>1,'return_message'=>'Thành công']);}catch(Throwable $e){error_log('[LightBill ZaloPay Callback] '.$e->getMessage());Response::json(['return_code'=>2,'return_message'=>'Thất bại']);}}
    if($path==='/api/zalopay/query'&&$method==='POST'){$u=Auth::requireUser('zalopay.read');$b=Request::body();$r=(new ZaloPayService((int)$u['company_id']))->queryAndSync((string)($b['app_trans_id']??''),(int)$u['id']);Response::ok(['payment'=>$r]);}
    if($path==='/api/zalopay/transactions'&&$method==='GET'){$u=Auth::requireUser('zalopay.read');Response::ok(['items'=>Util::rows('SELECT id,sale_id,app_trans_id,amount,environment,return_code,return_message,zp_trans_id,order_url,status,created_at,updated_at FROM zalopay_transactions WHERE company_id=? ORDER BY id DESC LIMIT 300',[(int)$u['company_id']])]);}
    if($path==='/zalopay/return'&&$method==='GET'){header('Location: ' . rtrim(Env::get('APP_URL','/')??'/', '/') . '/?payment=return');exit;}

    if($path==='/api/audit'&&$method==='GET'){$u=Auth::requireUser('audit.read');Response::ok(['items'=>Util::rows('SELECT a.*,u.full_name FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id WHERE a.company_id=? ORDER BY a.id DESC LIMIT 500',[(int)$u['company_id']])]);}

    if($path==='/'||$path==='/index.php'){
        header('Content-Type: text/html; charset=utf-8');
        readfile(__DIR__.'/app.html'); exit;
    }
    Response::error('Không tìm thấy đường dẫn',404);
} catch (Throwable $e) {
    error_log('[LightBill] '.$e->getMessage().' '.$e->getTraceAsString());
    Response::error('Hệ thống không thể xử lý yêu cầu',500);
}
