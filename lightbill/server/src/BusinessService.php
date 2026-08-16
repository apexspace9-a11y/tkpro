<?php
declare(strict_types=1);
namespace LightBill;

final class BusinessService
{
    public static function createSale(array $user, array $data): array
    {
        $pdo = DB::pdo();
        $companyId = (int)$user['company_id'];
        $warehouseId = (int)($data['warehouse_id'] ?? 0);
        $customerId = !empty($data['customer_id']) ? (int)$data['customer_id'] : null;
        $items = is_array($data['items'] ?? null) ? $data['items'] : [];
        if (!$warehouseId || !$items) Response::error('Thiếu kho hoặc hàng hóa', 422);
        if (!Util::row('SELECT id FROM warehouses WHERE id=? AND company_id=? AND is_active=1',[$warehouseId,$companyId])) Response::error('Kho không hợp lệ',422);
        if ($customerId && !Util::row('SELECT id FROM customers WHERE id=? AND company_id=? AND is_active=1',[$customerId,$companyId])) Response::error('Khách hàng không hợp lệ',422);

        $pdo->beginTransaction();
        try {
            $subtotal=0.0; $tax=0.0; $prepared=[];
            foreach ($items as $line) {
                $productId=(int)($line['product_id']??0); $qty=Util::qty($line['quantity']??0);
                if ($productId<=0 || $qty<=0) throw new \InvalidArgumentException('Dòng hàng không hợp lệ');
                $product=Util::row('SELECT * FROM products WHERE id=? AND company_id=? AND is_active=1 FOR UPDATE',[$productId,$companyId]);
                if(!$product) throw new \InvalidArgumentException('Không tìm thấy sản phẩm');
                $price=isset($line['unit_price'])?Util::money($line['unit_price']):Util::money($product['sale_price']);
                $vat=isset($line['vat_rate']) && $line['vat_rate']!=='' ? (float)$line['vat_rate'] : (float)($product['vat_rate']??0);
                $discount=Util::money($line['discount_amount']??0);
                $net=max(0, round($qty*$price-$discount,2)); $lineTax=round($net*$vat/100,2); $total=$net+$lineTax;
                $stock=Util::row('SELECT qty,avg_cost FROM stock_balances WHERE company_id=? AND warehouse_id=? AND product_id=? FOR UPDATE',[$companyId,$warehouseId,$productId]);
                $available=(float)($stock['qty']??0);
                if((int)$product['track_stock']===1 && $available+0.0001<$qty) throw new \InvalidArgumentException('Tồn kho không đủ: '.$product['name']);
                $cost=round($qty*(float)($stock['avg_cost']??$product['cost_price']),2);
                $prepared[]=['product'=>$product,'qty'=>$qty,'price'=>$price,'vat'=>$vat,'discount'=>$discount,'net'=>$net,'tax'=>$lineTax,'total'=>$total,'cost'=>$cost];
                $subtotal+=$net; $tax+=$lineTax;
            }
            $subtotal=round($subtotal,2); $tax=round($tax,2); $total=round($subtotal+$tax,2);
            $code=Util::text($data['code']??'',50) ?: Util::code('BH');
            $saleDate=Util::text($data['sale_date']??'',25) ?: Util::now();
            $pdo->prepare('INSERT INTO sales(company_id,branch_id,warehouse_id,customer_id,code,status,sale_date,subtotal,discount_amount,tax_amount,total_amount,paid_amount,payment_method,note,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
                ->execute([$companyId,$user['branch_id']?:null,$warehouseId,$customerId,$code,'confirmed',$saleDate,$subtotal,0,$tax,$total,0,$data['payment_method']??null,Util::text($data['note']??'',255),(int)$user['id']]);
            $saleId=(int)$pdo->lastInsertId();
            foreach($prepared as $p){
                $prod=$p['product'];
                $pdo->prepare('INSERT INTO sale_items(sale_id,product_id,sku,name,unit,quantity,unit_price,discount_amount,vat_rate,tax_amount,line_total,cost_amount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)')
                    ->execute([$saleId,(int)$prod['id'],$prod['sku'],$prod['name'],$prod['unit'],$p['qty'],$p['price'],$p['discount'],$p['vat'],$p['tax'],$p['total'],$p['cost']]);
                if((int)$prod['track_stock']===1){
                    $pdo->prepare('INSERT INTO stock_balances(company_id,warehouse_id,product_id,qty,avg_cost) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE qty=qty-VALUES(qty)')
                        ->execute([$companyId,$warehouseId,(int)$prod['id'],$p['qty'],(float)$prod['cost_price']]);
                    $pdo->prepare('INSERT INTO inventory_movements(company_id,warehouse_id,product_id,movement_type,reference_type,reference_id,qty_change,unit_cost,note,occurred_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)')
                        ->execute([$companyId,$warehouseId,(int)$prod['id'],'sale','sale',$saleId,-$p['qty'],$p['qty']>0?$p['cost']/$p['qty']:0,'Xuất bán '.$code,$saleDate,(int)$user['id']]);
                }
            }
            self::postSaleJournal($companyId,(int)$user['id'],$saleId,$code,$saleDate,$subtotal,$tax,array_sum(array_column($prepared,'cost')));
            $pdo->commit();
            Auth::audit($companyId,(int)$user['id'],'sale.create','sale',(string)$saleId,['code'=>$code,'total'=>$total]);
            return self::saleDetail($companyId,$saleId);
        } catch(\Throwable $e){$pdo->rollBack(); if($e instanceof \InvalidArgumentException) Response::error($e->getMessage(),422); throw $e;}
    }

    public static function createPurchase(array $user, array $data): array
    {
        $pdo=DB::pdo(); $companyId=(int)$user['company_id']; $warehouseId=(int)($data['warehouse_id']??0); $supplierId=!empty($data['supplier_id'])?(int)$data['supplier_id']:null; $items=is_array($data['items']??null)?$data['items']:[];
        if(!$warehouseId||!$items) Response::error('Thiếu kho hoặc hàng nhập',422);
        if(!Util::row('SELECT id FROM warehouses WHERE id=? AND company_id=? AND is_active=1',[$warehouseId,$companyId])) Response::error('Kho không hợp lệ',422);
        if($supplierId && !Util::row('SELECT id FROM suppliers WHERE id=? AND company_id=? AND is_active=1',[$supplierId,$companyId])) Response::error('Nhà cung cấp không hợp lệ',422);
        $pdo->beginTransaction();
        try{
            $subtotal=0.0;$tax=0.0;$prepared=[];
            foreach($items as $line){
                $productId=(int)($line['product_id']??0);$qty=Util::qty($line['quantity']??0);$unitCost=Util::money($line['unit_cost']??0);$vat=(float)($line['vat_rate']??0);
                if($productId<=0||$qty<=0||$unitCost<0) throw new \InvalidArgumentException('Dòng nhập không hợp lệ');
                $product=Util::row('SELECT * FROM products WHERE id=? AND company_id=? FOR UPDATE',[$productId,$companyId]);if(!$product)throw new \InvalidArgumentException('Không tìm thấy sản phẩm');
                $net=round($qty*$unitCost,2);$lineTax=round($net*$vat/100,2);$prepared[]=['product'=>$product,'qty'=>$qty,'cost'=>$unitCost,'vat'=>$vat,'net'=>$net,'tax'=>$lineTax,'total'=>$net+$lineTax];$subtotal+=$net;$tax+=$lineTax;
            }
            $total=round($subtotal+$tax,2);$code=Util::text($data['code']??'',50)?:Util::code('MH');$date=Util::text($data['purchase_date']??'',25)?:Util::now();
            $pdo->prepare('INSERT INTO purchases(company_id,warehouse_id,supplier_id,code,status,purchase_date,supplier_invoice_no,subtotal,tax_amount,total_amount,paid_amount,note,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)')
                ->execute([$companyId,$warehouseId,$supplierId,$code,'received',$date,Util::text($data['supplier_invoice_no']??'',100),round($subtotal,2),round($tax,2),$total,0,Util::text($data['note']??'',255),(int)$user['id']]);
            $purchaseId=(int)$pdo->lastInsertId();
            foreach($prepared as $p){$prod=$p['product'];$pdo->prepare('INSERT INTO purchase_items(purchase_id,product_id,quantity,unit_cost,vat_rate,tax_amount,line_total) VALUES(?,?,?,?,?,?,?)')->execute([$purchaseId,(int)$prod['id'],$p['qty'],$p['cost'],$p['vat'],$p['tax'],$p['total']]);
                if((int)$prod['track_stock']===1){$old=Util::row('SELECT qty,avg_cost FROM stock_balances WHERE company_id=? AND warehouse_id=? AND product_id=? FOR UPDATE',[$companyId,$warehouseId,(int)$prod['id']]);$oldQty=(float)($old['qty']??0);$oldAvg=(float)($old['avg_cost']??0);$newQty=$oldQty+$p['qty'];$newAvg=$newQty>0?round((($oldQty*$oldAvg)+($p['qty']*$p['cost']))/$newQty,2):$p['cost'];
                    $pdo->prepare('INSERT INTO stock_balances(company_id,warehouse_id,product_id,qty,avg_cost) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE qty=VALUES(qty),avg_cost=VALUES(avg_cost)')->execute([$companyId,$warehouseId,(int)$prod['id'],$newQty,$newAvg]);
                    $pdo->prepare('INSERT INTO inventory_movements(company_id,warehouse_id,product_id,movement_type,reference_type,reference_id,qty_change,unit_cost,note,occurred_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)')->execute([$companyId,$warehouseId,(int)$prod['id'],'purchase','purchase',$purchaseId,$p['qty'],$p['cost'],'Nhập mua '.$code,$date,(int)$user['id']]);
                    $pdo->prepare('UPDATE products SET cost_price=? WHERE id=?')->execute([$newAvg,(int)$prod['id']]);
                }}
            self::postPurchaseJournal($companyId,(int)$user['id'],$purchaseId,$code,$date,$subtotal,$tax);
            $pdo->commit();Auth::audit($companyId,(int)$user['id'],'purchase.create','purchase',(string)$purchaseId,['code'=>$code,'total'=>$total]);
            return Util::row('SELECT * FROM purchases WHERE id=?',[$purchaseId])??[];
        }catch(\Throwable $e){$pdo->rollBack();if($e instanceof \InvalidArgumentException)Response::error($e->getMessage(),422);throw $e;}
    }

    public static function createInvoiceFromSale(array $user,int $saleId,array $data=[]): array
    {
        $companyId=(int)$user['company_id'];$sale=self::saleDetail($companyId,$saleId);if(!$sale)Response::error('Không tìm thấy đơn bán',404);$customer=$sale['customer_id']?Util::row('SELECT * FROM customers WHERE id=? AND company_id=?',[(int)$sale['customer_id'],$companyId]):null;
        $pdo=DB::pdo();$pdo->beginTransaction();
        try{$pdo->prepare('INSERT INTO invoices(company_id,sale_id,invoice_type,form_no,symbol,invoice_no,invoice_date,buyer_name,buyer_tax_code,buyer_personal_id,buyer_address,buyer_email,subtotal,tax_amount,total_amount,status,payload_json,issued_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')
            ->execute([$companyId,$saleId,Util::text($data['invoice_type']??'vat',32),Util::text($data['form_no']??'',32)?:null,Util::text($data['symbol']??'',32)?:null,Util::text($data['invoice_no']??'',64)?:null,Util::text($data['invoice_date']??'',25)?:Util::now(),$customer['name']??null,$customer['tax_code']??null,$customer['personal_id']??null,$customer['address']??null,$customer['email']??null,$sale['subtotal'],$sale['tax_amount'],$sale['total_amount'],'draft',json_encode(['legal_basis'=>['70/2025/ND-CP','32/2025/TT-BTC'],'source_sale'=>$saleId],JSON_UNESCAPED_UNICODE),(int)$user['id']]);$invoiceId=(int)$pdo->lastInsertId();$i=1;foreach($sale['items'] as $line){$pdo->prepare('INSERT INTO invoice_items(invoice_id,line_no,item_type,sku,name,unit,quantity,unit_price,vat_rate,tax_amount,line_total) VALUES(?,?,?,?,?,?,?,?,?,?,?)')->execute([$invoiceId,$i++,'goods',$line['sku'],$line['name'],$line['unit'],$line['quantity'],$line['unit_price'],$line['vat_rate'],$line['tax_amount'],$line['line_total']]);}$pdo->commit();Auth::audit($companyId,(int)$user['id'],'invoice.create','invoice',(string)$invoiceId,['sale_id'=>$saleId]);return self::invoiceDetail($companyId,$invoiceId);
        }catch(\Throwable $e){$pdo->rollBack();throw $e;}
    }

    public static function submitInvoice(array $user,int $invoiceId): array
    {
        $companyId=(int)$user['company_id'];$invoice=self::invoiceDetail($companyId,$invoiceId);if(!$invoice)Response::error('Không tìm thấy hóa đơn',404);
        $provider=CompanySettings::get($companyId,'einvoice.provider',Env::get('EINVOICE_PROVIDER',''));$url=CompanySettings::get($companyId,'einvoice.api_url',Env::get('EINVOICE_API_URL',''));$token=CompanySettings::get($companyId,'einvoice.api_token',Env::get('EINVOICE_API_TOKEN',''));
        if(!$provider||!$url||!$token) Response::error('Chưa cấu hình nhà cung cấp hóa đơn điện tử',409);
        $host=parse_url($url,PHP_URL_HOST);$allowed=array_filter(array_map('trim',explode(',',CompanySettings::get($companyId,'einvoice.allowed_hosts',Env::get('EINVOICE_ALLOWED_HOSTS',''))??'')));
        if(parse_url($url,PHP_URL_SCHEME)!=='https'||!$host||($allowed&&!in_array($host,$allowed,true))) Response::error('Địa chỉ nhà cung cấp hóa đơn không hợp lệ',422);
        $payload=['invoice'=>$invoice,'company'=>Util::row('SELECT name,tax_code,address,phone,email FROM companies WHERE id=?',[$companyId])];
        $ch=curl_init($url);curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_RETURNTRANSFER=>true,CURLOPT_HTTPHEADER=>['Content-Type: application/json','Authorization: Bearer '.$token],CURLOPT_POSTFIELDS=>json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),CURLOPT_TIMEOUT=>45,CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2]);$raw=curl_exec($ch);$http=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);curl_close($ch);if($raw===false||$http<200||$http>=300)Response::error('Nhà cung cấp hóa đơn từ chối yêu cầu',502);
        $resp=json_decode((string)$raw,true);if(!is_array($resp))$resp=['raw'=>(string)$raw];$status=in_array(($resp['status']??''),['accepted','issued','submitted'],true)?($resp['status']):'submitted';DB::pdo()->prepare('UPDATE invoices SET status=?,provider=?,provider_ref=?,tax_authority_code=?,response_json=? WHERE id=? AND company_id=?')->execute([$status,$provider,$resp['reference']??$resp['id']??null,$resp['taxAuthorityCode']??null,json_encode($resp,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),$invoiceId,$companyId]);Auth::audit($companyId,(int)$user['id'],'invoice.submit','invoice',(string)$invoiceId,['provider'=>$provider,'status'=>$status]);return self::invoiceDetail($companyId,$invoiceId);
    }

    public static function saleDetail(int $companyId,int $saleId): ?array
    {
        $sale=Util::row('SELECT * FROM sales WHERE id=? AND company_id=?',[$saleId,$companyId]);if(!$sale)return null;$sale['items']=Util::rows('SELECT * FROM sale_items WHERE sale_id=? ORDER BY id',[$saleId]);return $sale;
    }
    public static function invoiceDetail(int $companyId,int $invoiceId): ?array
    {$r=Util::row('SELECT * FROM invoices WHERE id=? AND company_id=?',[$invoiceId,$companyId]);if(!$r)return null;$r['items']=Util::rows('SELECT * FROM invoice_items WHERE invoice_id=? ORDER BY line_no',[$invoiceId]);return $r;}

    private static function postSaleJournal(int $companyId,int $userId,int $saleId,string $saleCode,string $date,float $revenue,float $tax,float $cogs): void
    {
        self::ensureDefaultAccounts($companyId);$pdo=DB::pdo();$code=Util::code('KT');$pdo->prepare('INSERT INTO journal_entries(company_id,code,entry_date,description,reference_type,reference_id,status,created_by) VALUES(?,?,?,?,?,?,?,?)')->execute([$companyId,$code,substr($date,0,10),'Ghi nhận bán hàng '.$saleCode,'sale',$saleId,'posted',$userId]);$id=(int)$pdo->lastInsertId();$lines=[['131',$revenue+$tax,0],['511',0,$revenue]];if($tax>0)$lines[]=['3331',0,$tax];if($cogs>0){$lines[]=['632',$cogs,0];$lines[]=['156',0,$cogs];}foreach($lines as[$a,$d,$c])$pdo->prepare('INSERT INTO journal_lines(journal_entry_id,account_code,debit,credit,description) VALUES(?,?,?,?,?)')->execute([$id,$a,$d,$c,'Tự động từ bán hàng']);
    }
    private static function postPurchaseJournal(int $companyId,int $userId,int $purchaseId,string $code,string $date,float $net,float $tax): void
    {self::ensureDefaultAccounts($companyId);$pdo=DB::pdo();$j=Util::code('KT');$pdo->prepare('INSERT INTO journal_entries(company_id,code,entry_date,description,reference_type,reference_id,status,created_by) VALUES(?,?,?,?,?,?,?,?)')->execute([$companyId,$j,substr($date,0,10),'Ghi nhận mua hàng '.$code,'purchase',$purchaseId,'posted',$userId]);$id=(int)$pdo->lastInsertId();$lines=[['156',$net,0],['331',0,$net+$tax]];if($tax>0)$lines[]=['1331',$tax,0];foreach($lines as[$a,$d,$c])$pdo->prepare('INSERT INTO journal_lines(journal_entry_id,account_code,debit,credit,description) VALUES(?,?,?,?,?)')->execute([$id,$a,$d,$c,'Tự động từ mua hàng']);}
    public static function ensureDefaultAccounts(int $companyId): void
    {$defaults=[['111','Tiền mặt','asset'],['112','Tiền gửi ngân hàng','asset'],['131','Phải thu khách hàng','asset'],['1331','Thuế GTGT được khấu trừ','asset'],['156','Hàng hóa','asset'],['331','Phải trả người bán','liability'],['3331','Thuế GTGT phải nộp','liability'],['411','Vốn chủ sở hữu','equity'],['511','Doanh thu bán hàng và cung cấp dịch vụ','revenue'],['632','Giá vốn hàng bán','expense'],['641','Chi phí bán hàng','expense'],['642','Chi phí quản lý doanh nghiệp','expense']];$stmt=DB::pdo()->prepare('INSERT IGNORE INTO chart_accounts(company_id,code,name,account_type) VALUES(?,?,?,?)');foreach($defaults as $a)$stmt->execute([$companyId,$a[0],$a[1],$a[2]]);}
}
