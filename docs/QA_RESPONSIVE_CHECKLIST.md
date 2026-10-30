# Kiểm thử & Responsive

Phạm vi: nhánh `thu`, Android API 33 trên AVD riêng `Marketplace_Test`, Firebase Emulator. Dữ liệu và tài khoản kiểm thử tách khỏi production; không đụng dữ liệu Pixel_6.

## Kết quả theo task

| Task | Xác minh | Kết quả |
|---|---|---|
| Gradle và build | `build-debug.ps1` dùng JBR của Android Studio, thư mục socket trong workspace và IPv4; assemble debug + unit test | PASS — build cuối thành công; 3/3 unit test qua |
| Nút, điều hướng, liên kết | Khởi tạo 48 route Main và kiểm tra listener của các nút đang hoạt động; các luồng Marketplace/Admin/Giao dịch chạy E2E | 48 route qua; sửa hai nút trong Chat và nền đơn demo nằm dưới báo cáo |
| Loading, rỗng, lỗi mạng, thử lại | Phân biệt snapshot cache chưa xác nhận với dữ liệu rỗng từ server; tắt/bật mạng Firestore; đổi Functions sang cổng không phục vụ rồi khôi phục và bấm thử lại | Qua bài kiểm thử trạng thái mạng |
| Validation, gửi lặp | Email rỗng/sai, mật khẩu đăng ký, ngày sinh không tồn tại; dialog địa chỉ thiếu trường; khóa nút đăng nhập/đăng ký/lưu hồ sơ/gửi chat và khi backend đang xử lý | Qua các bài validation; địa chỉ checkout dùng ID ổn định để không tạo thêm bản ghi khi gửi lại |
| Xoay và giữ dữ liệu | Xoay thật bằng `setRequestedOrientation`, cùng `ActivityScenario.recreate`; hồ sơ, mô tả và ảnh tranh chấp, nháp cấu hình/ghi chú Admin, dialog địa chỉ | PASS — 7/7 bài Responsive qua trong lần chạy cuối |
| Màn nhỏ, dọc/ngang, cuộn, bàn phím | Màn 320×480dp và 480×320dp; 81 layout × 2 hướng; mở IME ở hồ sơ ngang, kiểm tra vùng nhập còn nhìn thấy; cuộn dialog địa chỉ | 162 lượt inflate qua; sửa XML thiếu kích thước và bỏ khoảng trống 120dp/chiều cao tối thiểu 560dp ở báo cáo |
| E2E và kết quả từng task | Bộ Android gồm 3 Admin + 3 Marketplace + 7 Responsive + 2 Giao dịch + 1 kiểm tra package; 3 unit test | PASS — 16/16 Android, không lỗi/bỏ qua; 3/3 unit test |

Lần chạy cuối ngày 23/10/2026: `BUILD SUCCESSFUL`, 3 phút 38 giây; XML ghi 16 tests, 0 failures, 0 errors, 0 skipped. Không tìm thấy `FATAL EXCEPTION` hoặc `ANR in vn.edu.ueh.thudinh.fashion2hand` trong logcat các test của lần này. Lỗi Auth `unexpected end of stream` ở lần trước được ghi nhận là lỗi kết nối trong bước đổi tài khoản fixture; không bỏ qua hay đổi assertion để tính là qua. Lần chạy lại toàn bộ đã qua.

## Minh chứng từng màn

- [162 lượt kiểm tra layout](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/layout-audit.txt)
- [48 route và kết quả gắn xử lý nút](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/navigation-audit.txt)
- [Hồ sơ ngang khi mở bàn phím](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/profile-land-keyboard.png)
- [Báo cáo dọc trên màn nhỏ](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/report-port-small.png)
- [Nháp cấu hình Admin sau xoay ngang](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/settings-land-small.png)
- [Giỏ hàng offline](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/cart-offline.png)
- [Lỗi kết nối Admin và nút thử lại](D:/Documents/LTM/Fashion2hand/.gradle/qa/Fashion2hand-QA/admin-network-error.png)

Các file trong `.gradle/qa` là artifact cục bộ, được tạo lại bằng lệnh thu thập bên dưới; không nằm trong Git.

## Các thay đổi đã sửa

- `UiFormState` lưu giá trị form nhỏ, lựa chọn và vị trí cuộn; nháp cấu hình Admin giữ revision ban đầu để không ghi đè cấu hình đã bị người khác sửa.
- Hồ sơ không để kết quả tải bất đồng bộ ghi đè trường người dùng đã sửa. Bộ lọc danh sách, chế độ sửa địa chỉ và ngữ cảnh hiện tại được lưu khi tạo lại Activity.
- Ảnh báo cáo và ảnh đại diện từ camera được ghi ra cache rồi lưu URI, tránh đưa Bitmap lớn vào saved-state Binder.
- Dialog địa chỉ, vị trí mô phỏng, từ chối đơn và ghi chú quyết định Admin giữ nội dung qua việc tạo lại màn.
- Giỏ hàng, yêu cầu nhận đồ và feed có trạng thái đang chờ server; không coi cache rỗng khi offline là dữ liệu rỗng đã xác nhận. Có nút thử lại.
- Chat tải tên đối tác qua API hồ sơ công khai và tải món ghim từ Firestore; hai nút Thêm giỏ/Mua ngay được nối đúng luồng, khóa khi món không khả dụng. Gửi tin nhắn chặn bấm lặp trong lúc đang gửi.
- Màn báo cáo trở thành màn cuộn độc lập; bỏ lớp nền đơn demo có các nút không được xử lý, giúp màn nhỏ/ngang có thêm diện tích nhập.
- XML Trung tâm giao dịch bổ sung kích thước còn thiếu cho TextView, tránh crash nếu inflate trực tiếp.
- Nút Back Admin vẫn dùng được khi yêu cầu mạng đang xử lý; callback cũ không sửa màn đã rời đi.

## Chạy lại

Khởi động backend bằng `start-emulators.ps1`, cấp fixture Admin theo `ADMIN_CHECKLIST.md`, mở `Marketplace_Test` và chờ boot xong. Trên terminal PowerShell:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 shell wm size 640x960
& $adb -s emulator-5554 shell wm density 320
.\build-debug.ps1 -RunUnitTests -RunDeviceTests
```

Các file `app/src/androidTest/assets/layout-audit.txt` và `route-audit.txt` ghi danh sách kiểm tra. `ResponsiveFlowTest` xuất bảng chi tiết và ảnh vào `Download/Fashion2hand-QA` của AVD bằng MediaStore để không bị Gradle xóa khi gỡ APK thử nghiệm.

```powershell
& $adb -s emulator-5554 pull /sdcard/Download/Fashion2hand-QA .gradle/qa
& $adb -s emulator-5554 shell wm size reset
& $adb -s emulator-5554 shell wm density reset
```

Gradle xuất XML/HTML kết quả tại `app/build/outputs/androidTest-results/connected/debug` và `app/build/reports/androidTests/connected/debug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Giới hạn kiểm thử và phần còn ngoài phạm vi

### Bổ sung kiểm thử màn Thông báo — 23/10/2026

- Bỏ sáu thông báo mẫu và SharedPreferences `notifications_demo`; dùng listener `users/{uid}/notifications`, sắp xếp thời gian mới nhất trước. Trạng thái đã đọc/đọc tất cả lưu Firestore, tuân thủ rules hiện có.
- Build APK và APK kiểm thử thành công. Unit test: 5/5 đạt, gồm 2 bài kiểm tra ánh xạ loại thông báo sang bộ lọc.
- `NotificationInboxEmulatorTest`: 1/1 đạt trên `emulator-5554`, 41,913 giây. Kiểm tra hộp rỗng, nhận sự kiện mới trực tiếp, lọc tin nhắn, lưu `read/readAt` tại server, chặn đọc hộp tài khoản khác, đánh dấu tất cả, ẩn thông báo đã đọc, mất mạng và kết nối lại.
- Kiểm thử dùng FirebaseApp và tài khoản Auth tách biệt; cài APK bằng `adb install -r`, không chạy tác vụ Gradle gỡ app. Không đổi phiên đăng nhập Firebase mặc định.
- Nhấn thông báo dùng `orderId/chatId/requestId` thực từ backend để điều hướng; bài kiểm thử inbox xác nhận sự kiện mở đúng ID, chưa kiểm thử riêng toàn bộ màn đích qua từng loại thông báo. Đây là thông báo trong ứng dụng; chưa triển khai push FCM khi đóng app.

- Rà soát 48 route xác nhận khởi tạo màn và việc gắn xử lý nút; không thay thế kiểm thử mọi tổ hợp dữ liệu/quyền của mọi nút. Các hành động ghi dữ liệu quan trọng được kiểm tra riêng trong các bộ E2E/backend.
- Không có API nhà cung cấp nên chưa kiểm tra tiền thật, giao hàng thật, GPS trực tiếp, SMS/email production hoặc VNeID. Camera/gallery hệ thống dùng ảnh cung cấp tại điểm trả kết quả; không tự thao tác app bên ngoài. Chưa có thiết bị vật lý, tablet hay kiểm tra font scale lớn.
- Cập nhật sau đợt rà soát backend: màn xác minh và sổ địa chỉ đã thay nguồn cục bộ bằng backend, dùng chung dữ liệu với Admin/checkout. Xem `UI_BACKEND_AUDIT.md` và `QA_BACKEND_UI_RESULTS.md` để biết thay đổi và kiểm thử mới; không dùng giới hạn cũ của hai màn này để đánh giá phiên bản hiện tại.
- AVD của host có lịch sử System UI ANR. Kiểm thử tắt animation và popup lỗi hệ thống; vẫn kiểm tra log crash/ANR của package app và không coi lỗi kết nối là bài test qua. Sau khi xong phải khôi phục cài đặt và tắt AVD.
- Một số lần chụp UiAutomation trả null ngay sau xoay; test thử chụp lại, rồi có thể dùng ảnh của decor view đã render. Ảnh fallback không chứa bàn phím hệ thống; trạng thái IME và vùng nhập được kiểm tra riêng bằng insets/visible rect.
