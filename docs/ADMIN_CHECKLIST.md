# Admin & Hệ thống — Firebase Emulator

## Hoàn thành

- [x] Đăng nhập bằng Firebase Authentication email/password; bỏ kiểm tra mật khẩu demo trong app.
- [x] Mọi màn dữ liệu Admin kiểm tra phiên với backend trước khi tải, sau khi quay lại màn và định kỳ 30 giây khi đang mở. Backend kiểm tra tài khoản Auth còn tồn tại/không bị vô hiệu hóa, custom claim `admin: true`, hồ sơ `role: ADMIN` và `accountStatus: ACTIVE` ở mỗi API. Không cấp quyền dựa vào email hay Intent.
- [x] Dashboard đếm dữ liệu thật bằng Firestore aggregate queries: người dùng/hoạt động, bài đăng/đang hiển thị, đơn hàng, báo cáo/khiếu nại/xác minh chờ xử lý. Không sử dụng biểu đồ hoặc số doanh thu demo.
- [x] Danh sách và chi tiết người dùng, bài đăng, đơn hàng, báo cáo, khiếu nại, xác minh, bút toán, danh mục và nhật ký. Phân trang 50 bản ghi/lần, có tải thêm và lọc trong phần đã tải. Chi tiết đơn và khiếu nại kèm sự kiện đơn, vận đơn và bút toán thực tế.
- [x] Khóa/mở tài khoản lưu Firestore, bật/tắt Auth user và thu hồi refresh tokens khi khóa; callable, Firestore Rules và Storage Rules chặn thao tác bằng token cũ. Không cho Admin tự khóa hoặc khóa Admin khác qua màn này.
- [x] Duyệt/ẩn/gỡ bài lưu backend và gắn cờ kiểm duyệt; chủ bài không thể tự đổi lại cờ hoặc mở lại bài đã bị Admin ẩn. Backend từ chối thay đổi bài đang có đơn hoặc đã hoàn tất.
- [x] Báo cáo có trạng thái xem xét/đã xử lý/từ chối và ghi chú. Xử lý đối tượng bị báo cáo là thao tác riêng: mở đối tượng để khóa tài khoản hoặc ẩn bài; không âm thầm ghép hai hành động.
- [x] Khiếu nại có yêu cầu bổ sung và quyết định hoàn tiền/giải ngân; cập nhật nguyên tử khiếu nại, đơn, thanh toán, bút toán và timeline. Cả `OPEN` và `WAITING_EVIDENCE` đều chặn người mua tự giải ngân. Quyết định tài chính chỉ hỗ trợ tiền mô phỏng đang giữ, không xử lý nhà cung cấp thật.
- [x] Xác minh thủ công cập nhật hồ sơ yêu cầu và trạng thái người dùng; từ chối phải có lý do. Không thay thế xác thực danh tính/VNeID.
- [x] Xem ảnh bằng chứng qua API Admin có kiểm tra hồ sơ và đường dẫn đã đăng ký; không tạo URL công khai hoặc mở rộng quyền đọc Storage cho tất cả người dùng.
- [x] Cấu hình lưu `systemConfig/active`, kiểm tra kiểu/phạm vi ở server, có revision chống ghi đè thay đổi từ phiên khác. Phí giao đồng bộ sang `shippingTariffs/active`, áp dụng cho báo giá đơn mới.
- [x] Mỗi thao tác thành công ghi `adminAudit` với người thực hiện, đối tượng, thời gian server, lý do và dữ liệu trước/sau. Mã thao tác chống gửi trùng; giao diện chỉ báo thành công sau phản hồi server.

## Chạy thử

Khởi động Emulator:

```powershell
.\scripts\start-emulators.ps1
```

Tạo hoặc cập nhật tài khoản Admin **cục bộ** ở terminal khác, dùng email và mật khẩu bạn chọn:

```powershell
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080'
node functions/provision-emulator-admin.js EMAIL PASSWORD
```

Script từ chối chạy nếu hai biến không trỏ đúng Emulator localhost. App không tự cấp quyền và không có tài khoản Admin mặc định. Cần tạo lại tài khoản nếu khởi động Emulator với dữ liệu trắng. Tài khoản kiểm thử Android là fixture riêng `admin-ui@example.com`, được tạo bằng script trên với mật khẩu `Test-only-123!` chỉ trong Emulator.

```powershell
node --test --test-concurrency=1 functions/test/admin.emulator.test.js functions/test/marketplace.emulator.test.js functions/test/transactions.emulator.test.js
.\scripts\build-debug.ps1 -RunUnitTests -RunDeviceTests
```

## Phạm vi và lưu ý triển khai

- Đợt này hoàn thiện trên Firebase Emulator; chưa deploy production. APK hiện nối `10.0.2.2` như checklist trước.
- Các màn chính/list/detail/settings Admin sử dụng `LiveAdminActivity`; các fragment/repository prototype cũ được giữ để tránh xóa mã ngoài phạm vi, nhưng không nằm trong luồng hoạt động và không khởi tạo dữ liệu demo.
- Cấu hình `maxPrice`, `requirePhoneBeforeSelling`, `autoHideReported`, `reportThreshold`, `autoCompleteDays` được lưu và đọc lại. Việc xây dựng các chính sách/tác vụ tự động tương ứng không nằm trong mục “lưu cấu hình”; UI nêu rõ điểm này. Phí giao đã được nối trực tiếp với báo giá.
- Yêu cầu bổ sung bằng chứng được ghi nhận, còn luồng tải thêm bằng chứng sau lần gửi tranh chấp đầu tiên chưa được mở cho hai bên. Bằng chứng đã gửi vẫn khóa sửa và Admin có thể xem để quyết định.
- API Admin đọc PII qua callable đã xác thực; không mở quyền Firestore đọc toàn bộ hồ sơ người dùng. Quyền `role`, claim và cờ khóa chỉ được cấp từ môi trường server tin cậy.
- Khóa hồ sơ và cập nhật Auth là hai dịch vụ khác nhau: hồ sơ khóa chặn ngay các thao tác; nếu Auth lỗi, UI báo lỗi và có thể gửi lại cùng mã thao tác để hoàn tất đồng bộ.
- Script khởi động đặt `FUNCTIONS_DISCOVERY_TIMEOUT=90` giây rồi khôi phục biến môi trường khi thoát, vì worker Node trên Windows có thể khởi động quá giới hạn mặc định 30 giây. Nên chạy backend trước, Android sau để tránh tranh tài nguyên khi khởi động worker.

## Kiểm thử backend

Ngày 23/10/2026: toàn bộ 16/16 nhóm backend qua trong một lần chạy tuần tự (6 Admin, 4 Marketplace, 6 Giao dịch). Bao gồm trường hợp thiếu claim/role, thu hồi quyền khi token cũ còn hiệu lực, khóa và mở khóa Auth, truy cập trực tiếp trái phép, báo cáo/xác minh, cấu hình xung đột phiên bản, hoàn tiền/giải ngân đúng một lần và quyền đọc bằng chứng.

Build debug thành công; 3/3 unit test và 9/9 Android test qua, gồm 3 Admin, 3 Marketplace, 2 Giao dịch và 1 kiểm tra package mẫu. Admin kiểm tra đăng nhập sai/đúng bằng Firebase, từ chối truy cập trực tiếp cả bốn màn dữ liệu cho USER, khóa/mở khóa sau khi tạo lại Activity và tăng revision cấu hình thật khi bấm lưu.

Kiểm thử khóa bổ sung sau bản sửa cuối cũng qua: gửi lại mã mở khóa cũ không thể vô hiệu hóa quyết định khóa mới hơn; Auth được đồng bộ theo hồ sơ server hiện tại.

Security Rules được sửa ở mức prototype cho Emulator, giữ dữ liệu riêng tư theo chủ sở hữu và chặn tài khoản khóa/cờ kiểm duyệt giả. Theo hướng dẫn skill: "I've set up prototype Security Rules to keep the data in Firestore safe. They are designed to be secure for server-owned roles, locked-account enforcement and moderation flags. However, you should review and verify them before broadly sharing your app. If you'd like, I can help you harden these rules."
