# Kết quả rà soát giao diện và backend — 23/10/2026

Phạm vi: nhánh `thu`, Firebase Emulator, Android `emulator-5554` API 33. Không deploy production, không push Git. Inventory chi tiết: [UI_BACKEND_AUDIT.md](UI_BACKEND_AUDIT.md).

## Phát hiện và sửa

| Phần | Trước | Sau |
|---|---|---|
| Địa chỉ | SharedPreferences, hai địa chỉ cục bộ và dữ liệu mẫu | Firestore `users/{uid}/addresses`; thêm/sửa/xóa, một mặc định; checkout đọc cùng collection |
| Xác minh danh tính | Chỉ đổi cờ `pending` cục bộ, ảnh chưa upload, case ID mẫu | Upload ba ảnh JPEG riêng tư; tạo `verificationRequests`, case ID thật, trạng thái backend, Admin duyệt/từ chối/gửi lại |
| Thông tin nhận tiền | Bấm lưu chỉ quay về hồ sơ | `savePayoutAccount/getAccountData`; lưu và tải lại dữ liệu riêng tư; luôn ghi rõ chưa xác minh ngân hàng |
| Hỗ trợ | Hotline/email minh họa, không lưu yêu cầu | Form gửi `reports(type=SUPPORT)`; mã yêu cầu, lịch sử, trạng thái và phản hồi Admin |
| Bảo mật | Email/số điện thoại và cờ xác minh mẫu | Thông tin từ Firebase Auth, nút dẫn đến luồng đổi/xác minh thật |
| Chat | Đọc riêng tư `users/{peerId}`; badge cố định; tab mua/bán không lọc | `getChatContext` kiểm tra thành viên, chỉ trả trường công khai, vai mua/bán và số chưa đọc từ backend |
| Hồ sơ | Bộ đếm dùng nhầm trường trạng thái của đơn | Đọc `orderStatus` theo schema backend; gộp hai vai và loại trùng ID |
| Đơn hàng | Snapshot cache rỗng có thể hiện như 0 đơn thật | Phân biệt đang chờ backend, lỗi tải và dữ liệu rỗng đã nhận server; nhấn thử lại |
| Xuất bản | Nhãn còn nói chỉ lưu tin demo trên máy | Mô tả đúng bước xuất bản ghi Firestore/Storage |
| Đăng xuất/nháp | Cài đặt chỉ mở login, nháp xác minh có thể còn trong controller | Đăng xuất Firebase Auth thật; xóa tham chiếu nháp nhạy cảm khi logout, chỉ khôi phục state đúng UID |

## Xác minh

- `node scripts/audit-ui-backend.cjs`: PASS — 49 màn Main (gồm splash), 6 màn Admin, 81 XML; không còn `shipping_address`, `account_verification` hoặc `notifications_demo` làm nguồn trạng thái nghiệp vụ trong Main.
- Build ứng dụng và APK kiểm thử: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest` thành công; APK ứng dụng được build lại sau sửa cuối về postId khi mở chat từ danh sách.
- Unit test: 5/5 PASS.
- Backend `account.emulator.test.js`: 4/4 PASS cho địa chỉ, payout, xác minh và hỗ trợ. Bài chat context bổ sung: 1/1 PASS; kiểm tra đúng vai mua/bán, không lộ liên hệ/địa chỉ và chặn người ngoài cuộc trò chuyện.
- Hồi quy Admin `Reports, warnings and verification decisions persist and cannot be overwritten`: 1/1 PASS sau thay đổi gửi thông báo kết quả.
- Android `AccountScreensEmulatorTest`: PASS riêng, sau đó PASS trên APK cuối trong lượt chạy chung; lưu/mở lại địa chỉ và payout, gửi đủ ba ảnh rồi xem case chờ duyệt, gửi hỗ trợ rồi tải lại lịch sử; kiểm tra phiên Auth mặc định giữ nguyên.
- Android `BackendLayoutAuditTest`: PASS 81 XML × 2 hướng = 162 lượt inflate, trên APK cuối. Bài này kiểm tra inflate theo cấu hình resource; kết quả xoay thiết bị/bàn phím thực tế của bộ trước nằm trong `QA_RESPONSIVE_CHECKLIST.md`.
- Lượt chạy chung cuối: **3/3 PASS, 0 failures**, 49,921 giây. Log: `.gradle/qa/backend-ui-final.txt`. Gồm `BackendLayoutAuditTest`, `AccountScreensEmulatorTest`, `NotificationInboxEmulatorTest`.

Các lượt chạy chung trước đó phát hiện bộ kiểm thử xóa FirebaseApp phụ quá sớm (`FirebaseApp was deleted`) và test host giữ ngữ cảnh splash khiến callback khởi động đổi màn inbox. Đã sửa teardown và cô lập ngữ cảnh màn test; không đổi assertion nghiệp vụ. Một lần backend fixture gặp gRPC `CANCELLED`; chạy lại nguyên assertion của bài chat context đã PASS. Một lần Android Emulator không attach được tiến trình; force-stop app rồi chạy lại thành công, không xóa dữ liệu.

## Giới hạn và cách hiểu kết quả

- Rà soát kết nối source và inflate không chứng minh mọi tổ hợp thao tác/quyền đều chạy E2E. Các luồng mới được kiểm tra Android + backend riêng như trên; không chạy lại toàn bộ bộ 16 bài cũ làm đổi Auth mặc định hoặc tự gỡ APK trên AVD người dùng.
- Test dùng FirebaseApp/tài khoản phụ; cài APK bằng `adb install -r`; không wipe/reset AVD hoặc khởi động lại Firebase Emulator.
- Chưa có API nhà cung cấp: không thanh toán/giao hàng/GPS/VNeID/xác minh ngân hàng thật. Không có push FCM khi đóng app, gửi ảnh chat hoặc form gửi đánh giá. Không hiển thị số đánh giá/online/thời gian phản hồi giả.
- Các màn FAQ/chính sách/ngôn ngữ/splash chỉ có nội dung tĩnh hoặc điều hướng, không cần gọi API.
- Cấu hình Admin đã lưu backend; phí ship có hiệu lực. Các chính sách tự ẩn theo ngưỡng báo cáo, yêu cầu điện thoại trước bán và tự hoàn tất theo số ngày chưa thi hành đầy đủ. Đây là giới hạn nghiệp vụ, không được coi đã hoàn tất tự động chỉ vì giao diện đã lưu cấu hình.
