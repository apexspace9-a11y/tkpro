# Tiết Kiệm Pro V2

Ứng dụng quản lý chi tiêu và tiết kiệm offline cho Android.

## V2 có gì mới

- Bộ lọc giao dịch theo loại, 7 ngày, tháng hiện tại, 3 tháng, ví và danh mục.
- Tổng hợp Thu / Chi / Dòng tiền theo đúng bộ lọc đang chọn.
- Phân tích dòng tiền 6 tháng và dự báo chi tiêu cuối tháng.
- Điểm sức khỏe tài chính dựa trên tiết kiệm, ngân sách, tài sản ròng, mục tiêu và nợ quá hạn.
- Cảnh báo chủ động cho nguy cơ vượt ngân sách, chi vượt thu, nợ quá hạn, mục tiêu sắp đến hạn và giao dịch định kỳ.
- Xuất toàn bộ giao dịch ra CSV để mở bằng Excel/Google Sheets.
- Giữ nguyên schema dữ liệu V1, nên dữ liệu hiện có tiếp tục dùng được khi nâng cấp.

## Nền tảng

- Android 8.0+ (minSdk 26)
- compileSdk / targetSdk 36
- Kotlin + Jetpack Compose + Room
- Hoạt động offline; backup JSON và CSV do người dùng chủ động xuất.
