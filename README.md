# Tiết Kiệm Pro V3

Ứng dụng quản lý tài chính cá nhân Android theo hướng offline-first, với AI tùy chọn online.

## V3

- Giao diện mới phong cách liquid glass: nền động nhẹ, card trong mờ, floating navigation và animation Compose.
- Dashboard Financial OS: tài sản ròng, safe-to-spend, health score, cảnh báo và dự báo 6 tháng.
- Giao dịch Pro: payee, tags, split transaction, subscription flag, ảnh hóa đơn và OCR on-device bằng ML Kit.
- Budget Engine 2.0: rollover, carry amount, envelope target và planned vs actual.
- Mục tiêu: đóng góp riêng, tiến độ, số tiền nên tiết kiệm mỗi tháng và liên kết ví.
- Debt Planner: ghi nhận thanh toán, lãi/gốc, Snowball và Avalanche.
- What-if Forecast: mô phỏng thay đổi thu nhập và chi phí phát sinh.
- AI Financial Copilot: endpoint/model/API key do admin cấu hình; API key mã hóa bằng Android Keystore.
- Admin CP: lần đầu dùng `/admincp setup`, sau đó `/admincp <khóa>` trong chat AI.
- Premium Free / Plus / Pro và luồng yêu cầu thanh toán chuyển khoản ngân hàng cho bản direct APK.
- Cảnh báo local: ngân sách, nợ quá hạn, mục tiêu và giao dịch định kỳ.
- Privacy mode, PIN, sinh trắc học, FLAG_SECURE.
- Backup schema 2; vẫn import được backup schema 1 từ V1/V2.
- Room migration 1 → 2 chỉ thêm bảng, không destructive migration.

## Thanh toán

Luồng bank Premium trong V3 được thiết kế cho APK phân phối trực tiếp/sideload. Nếu phát hành qua Google Play, cần áp dụng phương thức billing phù hợp với chính sách và chương trình alternative billing tại thị trường phát hành.

## Nền tảng

- Android 8.0+ (minSdk 26)
- compileSdk / targetSdk 36
- Kotlin + Jetpack Compose + Room + WorkManager
- ML Kit Text Recognition model đóng gói trong APK cho OCR Latin.
- Dữ liệu tài chính cốt lõi hoạt động offline.
- INTERNET chỉ phục vụ tính năng AI khi người dùng cấu hình và sử dụng.
- CI: GitHub Actions build APK từ `main` và giữ signing key ổn định từ V2.
