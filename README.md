# Tiết Kiệm Pro V4

Ứng dụng quản lý tài chính cá nhân Android theo kiến trúc online-first.

## V4

- Giao diện được thiết kế lại hoàn toàn: sạch, rộng, tương phản rõ, typography lớn và responsive cho điện thoại/màn hình rộng.
- 4 khu vực chính: Tổng quan, Giao dịch, Kế hoạch, Thêm; các màn AI, tài khoản, phân tích, Premium, cài đặt và Admin CP tách riêng để tránh chật.
- Đăng ký/đăng nhập bằng tài khoản online; không mở dữ liệu tài chính khi không kết nối được máy chủ.
- Cloud snapshot có revision để hạn chế ghi đè dữ liệu khi đồng bộ.
- Dữ liệu V3 hiện có được upload lên cloud khi tài khoản mới chưa có snapshot.
- Pending sync được giữ lại nếu mất mạng giữa lúc lưu và được ưu tiên đẩy khi kết nối lại.
- Admin CP xác thực khóa với server; admin key/token được lưu mã hóa bằng Android Keystore.
- Test thông báo trực tiếp trong Admin CP.
- AI Financial Copilot chạy qua backend, không đặt AI API key trong APK.
- Premium, yêu cầu chuyển khoản và duyệt Premium được lưu/xử lý phía server.
- Giữ các chức năng V3: payee, tags, split transaction, OCR hóa đơn, budget rollover/envelope, mục tiêu, đóng góp, Debt Planner, Snowball/Avalanche, What-if Forecast, giao dịch định kỳ, backup JSON và CSV.
- Room vẫn tồn tại như cache/migration bridge; server là nguồn dữ liệu chính của V4.

## Backend

Backend nằm trong thư mục `server/` và dùng Node.js 24 + SQLite.

Biến môi trường bắt buộc:

- `TKPRO_TOKEN_SECRET`
- `TKPRO_ADMIN_KEY`

Tùy chọn:

- `TKPRO_AI_API_KEY`
- `TKPRO_DB_PATH`
- `PORT`

Có `server/Dockerfile` để triển khai lên một máy chủ/container có HTTPS. Sau khi triển khai, nhập URL máy chủ vào màn đăng nhập của ứng dụng.

## Android

- Android 8.0+ (minSdk 26)
- compileSdk / targetSdk 36
- Kotlin + Jetpack Compose + Room + WorkManager
- ML Kit Text Recognition cho OCR hóa đơn
- versionCode 4 / versionName 4.0.0
- GitHub Actions giữ signing key ổn định từ V2 để V4 có thể cài nâng cấp lên V3.

## CI

Workflow kiểm tra cả backend bằng Docker smoke test và build APK Android. Artifact APK được tạo từ nhánh `main` hoặc `v4-upgrade` khi build thành công.
