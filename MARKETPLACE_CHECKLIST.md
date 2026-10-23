# Người dùng & Marketplace — 23/10/2026

Phạm vi: code trên nhánh `thu`, cấu hình Firebase Emulator hiện tại. Không deploy Firebase production.

## Các thay đổi

- [x] Back trên toolbar và Back hệ thống dùng cùng lịch sử màn hình; giữ lịch sử, nội dung form, thông tin sản phẩm và phiên OTP khi Activity được tạo lại.
- [x] OTP SMS dùng Firebase Phone Auth, hỗ trợ gửi lại có thời gian chờ, kiểm tra mã thật và xác thực lại tài khoản khi cần. Liên kết số điện thoại vào tài khoản hiện tại, không tạo/chuyển sang UID khác.
- [x] Đổi email dùng `verifyBeforeUpdateEmail`: email hiện tại chỉ thay đổi sau khi mở liên kết xác nhận. App kiểm tra lại với Auth khi quay lại hoặc nhấn nút kiểm tra; xử lý trường hợp phải đăng nhập lại bằng email mới.
- [x] Trạng thái email/điện thoại lấy từ Firebase Auth. `syncAccountContact` trên server lấy thông tin từ Admin Auth, không tin các cờ xác minh do client gửi.
- [x] Hồ sơ người đăng hiển thị tên, ảnh, khu vực, giới thiệu và bài ACTIVE; có trạng thái tải, rỗng, lỗi và thử lại. `getSellerProfile` chỉ trả các trường công khai, không mở quyền đọc tài liệu users.
- [x] Chia sẻ qua Android Sharesheet và sao chép liên kết thật vào clipboard. Liên kết `fashion2hand://post/<postId>` mở bài đăng trong app đã cài; bài đã gỡ/ẩn có thông báo lỗi.
- [x] Sửa lưu bài nháp khi chỉnh sửa, ngăn nhấn lưu nhiều lần, dùng lại ID bài khi retry upload.
- [x] Thêm MIME metadata khi upload ảnh và hỗ trợ ảnh gs://, URL Storage production, URL Emulator; ảnh sai không làm crash màn hình.
- [x] Yêu thích tải từng bài đã lưu, không bị giới hạn bởi 50 bài mới nhất. Bài người dùng không được phép đọc sẽ không hiển thị.
- [x] Sửa crash layout Yêu thích: đặt grid và trạng thái rỗng trong cùng một phần tử con của ScrollView.

## Chạy lại kiểm thử

Kết quả cuối: build APK debug thành công; 3 unit test, 4 Android instrumented test (gồm 3 luồng Marketplace và 1 test mẫu), 4 integration test backend đều qua. Báo cáo Android: `app/build/reports/androidTests/connected/debug/index.html`.

Mở một terminal ở thư mục dự án, giữ Firebase Emulator chạy:

```powershell
.\start-emulators.ps1
```

Mở máy ảo Android, rồi chạy ở terminal khác:

```powershell
.\build-debug.ps1 -RunUnitTests -RunDeviceTests
node --test functions/test/marketplace.emulator.test.js
```

`MarketplaceLinksTest` kiểm tra chuẩn hóa số điện thoại và chống mã bài đăng không hợp lệ.

`MarketplaceFlowTest` kiểm tra Back sau khi tạo lại Activity; nhập OTP sai rồi đúng sau khi tạo lại Activity và giữ UID; đăng bài có upload ảnh → sửa bài → bài của tôi → hồ sơ người đăng → sao chép liên kết → yêu thích. Test cung cấp Bitmap tại điểm trả kết quả camera; không tự thao tác ứng dụng camera/gallery của hệ thống.

Test cũng tạo lại Activity sau khi chọn ảnh, mở sản phẩm từ liên kết vừa sao chép và quay lại Yêu thích. Android xác nhận intent `fashion2hand://post/...` được ánh xạ tới MainActivity.

Integration tests backend kiểm tra hồ sơ chỉ có trường công khai, chặn truy cập riêng tư, OTP đúng/sai, xác nhận email, bỏ qua cờ xác minh giả, sửa bài trái phép và thêm/xóa yêu thích qua rules thật trên Emulator.

## Giới hạn môi trường

Kiểm thử Android dùng máy ảo riêng `Marketplace_Test` (API 33) trong `.gradle/avds`, giữ nguyên dữ liệu Pixel_6. Máy kiểm thử tắt animation và popup lỗi hệ thống do Android/System UI của môi trường này bị ANR; log crash và kết quả kiểm thử của package Fashion2hand vẫn được kiểm tra. Máy ảo phải boot xong trước khi chạy test.

SMS/email được xác minh bằng Firebase Emulator; chưa kiểm tra gửi SMS hoặc email qua nhà cung cấp production. Dùng production cần cấu hình Phone provider, SHA/app verification, SMS region policy và deploy hai callable mới. Các liên kết sản phẩm hiện mở app đã cài, chưa có trang web dự phòng.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
