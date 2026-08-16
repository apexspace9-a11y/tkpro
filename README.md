# LightBill 2.0

LightBill là hệ thống hóa đơn điện tử, bán hàng, quản lý kho, kế toán và thuế đa ngành. Web quản trị và REST API chạy bằng PHP/MySQL trên shared hosting; Android và Windows dùng cùng máy chủ HTTPS.

## Cài đặt web một lần

1. Tạo database MySQL trống trên hosting.
2. Upload thư mục `lightbill/server` và trỏ document root vào `lightbill/server/public`.
3. Mở `https://tenmien/install`.
4. Nhập database, thông tin doanh nghiệp và tài khoản chủ sở hữu rồi bấm **Cài đặt LightBill**.

Bộ cài tự import schema, sinh APP_KEY, tạo chi nhánh chính, kho chính, hệ thống tài khoản kế toán và tài khoản owner. Sau khi hoàn tất, installer tự khóa và không chạy lại.

## ZaloPay

LightBill 2.0 sử dụng ZaloPay. Sandbox được cấu hình mặc định bằng bộ thông tin thử nghiệm công khai của ZaloPay. Khi chuyển production, nhập App ID, Key 1 và Key 2 của merchant trong **Cấu hình → ZaloPay**.

Callback: `/api/zalopay/callback`. Giao dịch pending có thể đồng bộ thủ công trên web hoặc đặt cron `php lightbill/server/cron/zalopay_reconcile.php` mỗi 5 phút.

## Hóa đơn điện tử

Adapter đầu tiên là **MISA meInvoice** với hai môi trường sandbox/production. Cấu hình tại **Cấu hình → MISA meInvoice** gồm App ID, mã số thuế, tài khoản, mật khẩu, ký hiệu hóa đơn và kiểu phát hành.

- SignType 2: phát hành bằng HSM.
- SignType 5: hóa đơn khởi tạo từ máy tính tiền không ký số.

Hóa đơn nháp có nút phát hành trực tiếp; mã yêu cầu được giữ ổn định để chống phát hành trùng khi retry.

## Kế toán và thuế

- Chế độ kế toán doanh nghiệp: TT99/2025/TT-BTC.
- Doanh nghiệp siêu nhỏ: TT58/2026/TT-BTC.
- Cấu trúc HĐĐT cập nhật theo Nghị định 254/2026/NĐ-CP và Thông tư 91/2026/TT-BTC.
- VAT lưu theo từng dòng hàng; có sổ nhật ký chung, cân đối phát sinh, báo cáo lãi lỗ và VAT đầu vào/đầu ra.

## Thành phần

- `lightbill/server`: web, API, database, installer, cron.
- `lightbill/android`: Android client.
- `lightbill/windows`: Windows client.

## Yêu cầu

PHP 8.2+, PDO MySQL, cURL, mbstring, OpenSSL, MySQL 8+/MariaDB tương thích InnoDB và HTTPS.
