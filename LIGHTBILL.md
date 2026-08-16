# LightBill

LightBill là hệ thống quản lý doanh nghiệp đa ngành gồm web quản trị PHP/MySQL, API cho ứng dụng, Android APK và Windows EXE.

## Thành phần

- `lightbill/server`: web quản trị + REST API chạy trên shared hosting PHP/MySQL.
- `lightbill/android`: ứng dụng Android kết nối HTTPS tới server LightBill.
- `lightbill/windows`: ứng dụng Windows kết nối HTTPS tới server LightBill.
- `.github/workflows/lightbill-android.yml`: build APK sandbox.
- `.github/workflows/lightbill-windows.yml`: build EXE Windows.

## Yêu cầu server

- PHP 8.2 trở lên, extension PDO MySQL, cURL, mbstring, OpenSSL.
- MySQL 8+ hoặc MariaDB tương thích InnoDB.
- HTTPS bắt buộc cho app Android/Windows và callback thanh toán.
- Document root trỏ vào `lightbill/server/public`.

## Cài đặt shared hosting

1. Tạo database và import `lightbill/server/database/schema.sql` bằng phpMyAdmin.
2. Sao chép `lightbill/server/.env.example` thành `lightbill/server/.env`, điền database, APP_URL, APP_KEY và APP_SETUP_TOKEN.
3. Cấu hình domain/subdomain để document root vào thư mục `public`.
4. Mở `/setup.php` một lần để tạo doanh nghiệp, kho chính và tài khoản owner.
5. Sau khi thiết lập thành công, xóa giá trị `APP_SETUP_TOKEN` trong `.env`.
6. Đặt cron gọi `php lightbill/server/cron/cleanup.php` mỗi ngày nếu hosting hỗ trợ.

## MoMo Business sandbox

Giữ `MOMO_ENV=sandbox`, sau đó điền `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_REDIRECT_URL`, `MOMO_IPN_URL` bằng bộ mã test được cấp cho tài khoản MoMo Business. Secret Key chỉ lưu ở server.

## Hóa đơn điện tử

LightBill lập và lưu dữ liệu hóa đơn, dòng hàng, thông tin người mua, thuế, trạng thái và audit trail. Để gửi phát hành thực tế, cấu hình nhà cung cấp HĐĐT qua `EINVOICE_PROVIDER`, `EINVOICE_API_URL`, `EINVOICE_API_TOKEN`, `EINVOICE_ALLOWED_HOSTS`. Adapter sử dụng HTTPS và allowlist hostname.

## Kế toán và thuế

Cấu trúc dữ liệu hỗ trợ chế độ kế toán doanh nghiệp theo TT99/2025/TT-BTC và chế độ doanh nghiệp siêu nhỏ theo TT58/2026/TT-BTC ở mức cấu hình doanh nghiệp. Thuế suất VAT lưu theo từng sản phẩm và từng giao dịch, không đóng cứng một mức thuế duy nhất.

## Build

Push vào branch `lightbill` sẽ chạy GitHub Actions để tạo artifact APK và EXE. APK workflow hiện tạo bản sandbox/debug để kiểm thử MoMo. Bản Android phát hành chính thức cần keystore riêng của chủ ứng dụng và quy trình ký release ổn định.
