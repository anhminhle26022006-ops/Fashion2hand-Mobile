# Quy tắc xác minh tài khoản — 24/10/2026

## Cập nhật 25/10: miễn xác minh cho Admin

Admin đang hoạt động được miễn xác minh email/SĐT khi đồng thời có custom claim
`admin=true` từ Firebase Auth và hồ sơ máy chủ `role=ADMIN`, `accountStatus=ACTIVE`.
Chỉ có một trong hai dấu hiệu không đủ quyền. App kiểm tra claim và hồ sơ máy chủ
trước khi bỏ màn xác minh; tài khoản người dùng thường vẫn cần xác minh email.
Backend kiểm tra trạng thái Auth hiện tại; tài khoản Auth bị vô hiệu hóa vẫn bị chặn.

5/5 ca backend email-only/Admin và 2/2 ca Rules đăng bài đã chạy thành công.
Các ca bao gồm Admin chưa xác minh, thiếu claim, thiếu role, tài khoản khóa và
người dùng thường chỉ xác minh email không có SĐT. Ngoại lệ này đã cập nhật trong
mã nguồn Rules/Functions; cloud hiện hành vốn không có cổng xác minh email/SĐT.

- Tài khoản đủ điều kiện đăng bài, mua hàng và xin đồ khi Firebase Auth có **email đã xác minh**. **SĐT không bắt buộc**. SMS thật được tạm tắt theo yêu cầu tránh phí; không tự bật Blaze hoặc bật lại Phone Authentication. Không yêu cầu Admin duyệt CCCD để thực hiện ba thao tác này.
- Đăng ký email/password tạo tài khoản ở trạng thái chưa hoàn tất; app gửi email xác minh và mở màn hoàn tất xác minh. Người dùng mở liên kết email (kiểm tra cả Spam) rồi kiểm tra lại. Nếu thoát giữa chừng, đăng nhập lại vẫn tiếp tục xác minh.
- Bài đăng được công khai ngay sau thao tác xuất bản hợp lệ; không chờ Admin duyệt. Admin vẫn có quyền ẩn/gỡ bài vi phạm.
- Tài khoản đã tồn tại vẫn phải xác minh email; thiếu SĐT không chặn sử dụng.
- UI chặn các màn đăng bài/checkout/xin đồ khi email chưa xác minh; backend createPaidOrder/createCartOrders/createFreeRequest đọc trạng thái Auth hiện tại. Firestore/Storage Rules kiểm tra claim email_verified khi ghi bài/ảnh, không yêu cầu phone_number. Cờ hồ sơ do client tự khai không cấp quyền. Sau xác minh app reload và refresh ID token để Rules nhận claims mới.
- Luồng xác minh giấy tờ bằng ảnh hiện có là chức năng riêng. Không được dùng chữ VERIFIED của hồ sơ giấy tờ để thay thế xác minh email.
- OTP SĐT chỉ được thử tùy chọn trên debug + Firebase Emulator, có thông báo **OTP mô phỏng**. Không gửi SMS thật và không phát sinh phí SMS. Bản release không chứa phần đọc mã mô phỏng; client Firebase thật chặn gọi API OTP, kể cả gọi trực tiếp Repository.

## Kiểm thử

Các test trong functions/test/transactions.emulator.test.js và marketplace.emulator.test.js kiểm tra email chưa xác minh bị từ chối dù có SĐT hoặc cờ hồ sơ giả; email đã xác minh được đăng bài/tạo đơn/xin đồ mà không có SĐT. Token cũ chưa có claim email_verified không được xuất bản. Android ContactVerificationFlowTest kiểm tra vào Home sau khi xác minh email mà chưa liên kết SĐT. EmulatorOtpNotificationTest kiểm tra mã khớp đúng phiên, xuất hiện trong thông báo và xác minh được, rồi thông báo được xóa.

Fixture xác minh bằng Admin SDK nằm trong functions/test/verified-fixture.js; fixture Android tương ứng là VerifiedAccountFixture trong androidTest. Chúng chỉ dành cho Emulator, không đóng gói trong APK ứng dụng; ứng dụng không có API tự bật trạng thái xác minh.

Khi chạy Emulator, liên kết email được in ở terminal Firebase, OTP mô phỏng hiện trong thông báo của app. Email Firebase thật đã được kiểm thử nhận thư, có thể bị Gmail đưa vào Spam. SMS thật đang tắt trên cấu hình Firebase Auth.

## Kết quả kiểm thử chính sách email-only

- Firebase thật: `signIn.phoneNumber.enabled=false`, đăng nhập email vẫn bật. Không bật Blaze, không gửi SMS thật.
- Backend: 3/3 test `email-only-policy.emulator.test.js` PASS; kiểm tra email chưa xác minh/cờ giả bị từ chối, tài khoản email đã xác minh không có SĐT được tạo đơn/xin đồ/checkout giỏ hàng, tài khoản Auth bị vô hiệu hóa vẫn bị từ chối.
- Rules: test đăng bài/sửa bài/truy vấn tin của tôi/yêu thích với token đã xác minh email **không có claim SĐT** PASS; token email cũ chưa xác minh và sửa bài của người khác bị từ chối. Firestore và Storage Rules đều biên dịch thành công bằng deploy dry-run.
- Android: 2/2 test OTP mô phỏng PASS; test `emailVerificationUnlocksHomeWithoutPhone` PASS trên APK mới nhất. Build Firebase thật, compile release và 8/8 unit test PASS.
- Lượt test Android đầu dùng APK chưa chứa bản sửa nút làm mới email nên timeout ở Home; đã build lại và chạy lại PASS, không bỏ assertion.
- Cloud Rules/Functions hiện hành không có yêu cầu bắt buộc SĐT nên không phát hành các thay đổi code khác lên cloud trong đợt này. Cấu hình Phone Auth tắt đã được áp dụng và đọc lại xác nhận. Mã nguồn backend/Rules đã đồng bộ email-only để dùng cho lần triển khai tiếp theo.

## Lịch sử kiểm thử trước khi chuyển sang email-only

Kết quả 24/10: build debug + unit test PASS; Android test ContactVerificationFlowTest PASS (36,112 giây), bao gồm giữ bước xác minh khi recreate, mở/Back từ form SĐT và vào home sau khi đủ hai yếu tố. Test lưu và khôi phục phiên Auth trước đó. Các ca đăng bài và mua/xin đồ với cờ hồ sơ giả/thiếu yếu tố cũng PASS trên Rules/backend thật của Emulator. Lượt regression đầu gặp cold-start timeout; cần phân biệt với các assertion nghiệp vụ.

Lượt chạy lại tuần tự sau khi worker khởi động: **17/17 backend PASS** (6 Admin, 4 Marketplace, 7 Giao dịch), 428,817 giây; **5/5 unit PASS**. Không giảm các assertion quyền/nghiệp vụ để xử lý timeout. APK kiểm thử cũng build thành công; APK app cài bằng `adb install -r` để giữ dữ liệu.
