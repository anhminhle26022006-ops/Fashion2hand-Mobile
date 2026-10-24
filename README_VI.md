# Fashion2hand — Bộ triển khai Firebase Firestore (tiếng Việt)

Bộ này dùng **Firebase Firestore**, thay cho hướng SQL Connect trước đó. Firestore là cơ sở dữ liệu dạng tài liệu, không dùng bảng SQL. Bạn không chạy file `.sql`: hãy triển khai Security Rules và Indexes bằng Firebase CLI. Collection được tạo tự động khi ứng dụng ghi document đầu tiên vào collection đó.

## Các file trong bộ

- `firebase.json`: khai báo vị trí file Rules và Indexes.
- `firestore.rules`: quy tắc đọc/ghi dữ liệu theo người dùng.
- `firestore.indexes.json`: các composite index cho truy vấn thường dùng.
- `FIRESTORE_DATA_MODEL_VI.md`: mô hình collections, document và quy tắc nghiệp vụ.

## 1. Chuẩn bị Firebase project

1. Tạo project tại [Firebase Console](https://console.firebase.google.com/) hoặc chọn project đã có.
2. Mở **Build → Firestore Database → Create database**.
3. Chọn **Native mode**, chọn khu vực gần người dùng và hoàn tất tạo database.
4. Mở **Build → Authentication → Sign-in method**, bật **Google** và lưu cấu hình.

## 2. Cài Firebase CLI và chọn project

Cài Node.js nếu máy chưa có, sau đó mở PowerShell và chạy:

```powershell
npm install -g firebase-tools
firebase login
```

Giải nén bộ file này vào một thư mục. Mở PowerShell tại chính thư mục có `firebase.json`, rồi liên kết Firebase project:

```powershell
firebase use --add
```

Chọn project vừa tạo. Có thể kiểm tra project hiện tại bằng:

```powershell
firebase use
```

## 3. Triển khai Rules và Indexes

Tại thư mục chứa `firebase.json`, chạy:

```powershell
firebase deploy --only firestore:rules,firestore:indexes
```

Chờ Firebase CLI báo hoàn tất. Index mới có thể cần vài phút để được tạo. Lỗi truy vấn thiếu index thường kèm đường dẫn tạo index trong log Firebase.

## 4. Kết nối ứng dụng Android và đăng nhập Google

1. Trong Firebase Console, vào **Project settings → Your apps → Add app → Android**.
2. Nhập đúng package name của ứng dụng Android.
3. Thêm SHA-1/SHA-256 của ứng dụng theo yêu cầu cấu hình Google Sign-In.
4. Tải `google-services.json` và đặt vào thư mục module Android, thường là `app/google-services.json`.
5. Thêm Firebase SDK cho Firestore và Authentication, đồng thời cấu hình Google Sign-In theo tài liệu Firebase.
6. Trong Authentication, kiểm tra Google provider vẫn đang bật.

### Chọn Firebase thật hoặc Emulator khi build Android

App mặc định kết nối Firebase thật của project `fashion2hand` trong `app/google-services.json` (cả debug và release). Build APK dùng chung giữa các máy:

```powershell
.\build-debug.ps1
```

Để thử nghiệm bằng Firebase Emulator trên máy phát triển, chạy `start-emulators.ps1` rồi build:

```powershell
.\build-debug.ps1 -UseFirebaseEmulators
# Hoặc: .\gradlew.bat :app:assembleDebug -PuseFirebaseEmulators=true
```

`-RunDeviceTests` tự bật Emulator. Khi chạy kiểm thử thiết bị trực tiếp bằng Gradle, luôn truyền `-PuseFirebaseEmulators=true`. Release luôn dùng Firebase thật.

Hiện **SMS thật đang tạm tắt** để tránh phí; app chỉ bắt buộc xác minh email. Phone Authentication trên project Firebase thật được tắt; client cũng chặn gửi và xác minh OTP ngoài Emulator. SĐT trong địa chỉ nhận hàng chỉ là thông tin liên hệ, không yêu cầu OTP.

Trong bản debug bật Emulator, có thể thử OTP điện thoại tùy chọn. Ứng dụng đọc mã của đúng số và phiên xác minh từ Auth Emulator, rồi hiện thông báo **OTP mô phỏng — Fashion2hand**. Cho phép quyền thông báo trên Android 13 trở lên. Nếu từ chối, mã được hiện trong hộp thoại mô phỏng. Thông báo tự ẩn sau 60 giây hoặc khi xác minh thành công; không cần lấy mã từ terminal. Nếu thông báo hết thời gian, bấm gửi lại để lấy phiên mới. Bản release không chứa phần đọc mã mô phỏng; bản debug dùng Firebase thật cũng không đọc hoặc hiển thị mã này.

### Email xác minh bị đưa vào Spam

Thông báo gửi thành công chỉ xác nhận Firebase chấp nhận yêu cầu gửi, chưa xác nhận Gmail đã nhận vào Inbox. Kiểm tra cả Spam/Thư rác và bấm **Báo cáo không phải thư rác** nếu tìm thấy thư.

Để dùng địa chỉ gửi mang thương hiệu, cần tên miền do bạn quản lý. Trong Firebase Console → Authentication → Templates, chọn mẫu xác minh email và tùy chỉnh tên miền, thêm chính xác các bản ghi DNS xác minh/SPF/DKIM Firebase cung cấp, đợi xác minh rồi áp dụng tên miền cho mẫu thư. Cấu hình DMARC phù hợp với nhà cung cấp gửi thư; không tạo nhiều bản ghi SPF riêng cho cùng một tên miền. Không tự đặt địa chỉ gửi thuộc tên miền chưa xác minh. Cách này giúp cải thiện độ tin cậy, nhưng không bảo đảm mọi thư vào Inbox. Xem [hướng dẫn Firebase](https://firebase.google.com/docs/auth/email-custom-domain) và [yêu cầu của Gmail](https://support.google.com/mail/answer/81126).

Emulator dùng địa chỉ `10.0.2.2` dành cho Android Emulator và không đồng bộ dữ liệu lên Firebase thật. Tài khoản, bài đăng và ảnh đã tạo ở Emulator không tự xuất hiện trên cloud; cần đăng ký/đăng nhập và đăng lại trên bản dùng Firebase thật. Database, Authentication, Storage, Functions, Rules và Indexes trên cloud vẫn cần được triển khai và kiểm tra riêng.

## 5. Tạo dữ liệu ban đầu

Firestore không có lệnh tạo bảng/collection. Khi ứng dụng hoặc script tin cậy ghi document đầu tiên, collection tương ứng sẽ xuất hiện. Ví dụ: ghi document danh mục vào `categories`, hoặc tạo hồ sơ vào `users/{uid}` sau khi người dùng đăng nhập.

Danh mục và bảng giá vận chuyển ban đầu nên được tạo bởi script dùng Firebase Admin SDK hoặc bởi tài khoản quản trị được bảo vệ. Không đưa service account key vào ứng dụng Android.

## Offline, SQLite và xoay màn hình

- Firestore Android có cache offline tích hợp. Dữ liệu đã tải có thể đọc lại khi mất mạng và đồng bộ thay đổi khi có mạng trở lại.
- Nếu yêu cầu bài tập cần SQLite, dùng Room làm cache local do ứng dụng quản lý; Firestore vẫn là dữ liệu máy chủ chính. Đồng bộ qua Repository và xử lý xung đột rõ ràng.
- Khi offline, không hiển thị thanh toán, xác nhận đơn, duyệt yêu cầu nhận đồ hoặc giải quyết khiếu nại như đã thành công trước khi máy chủ xác nhận.
- Xoay màn hình thuộc phần giao diện Android: dùng layout thích ứng, giữ trạng thái màn hình/form qua Activity recreation. Đây không phải cấu hình Firestore.

## Lưu ý bảo mật và phần còn cần triển khai

Rules chặn ghi trực tiếp từ ứng dụng vào đơn hàng, thanh toán, hoàn tiền và chi trả. Các nghiệp vụ checkout, giữ sản phẩm, xác thực callback VNPAY Sandbox, tự hủy quá hạn, người bán chấp nhận/từ chối, tranh chấp và cập nhật trạng thái giao hàng cần xử lý bằng Cloud Functions hoặc backend tin cậy, kết hợp transaction.

Trước khi dùng thật, chạy Firebase Emulator Suite để kiểm tra Rules. Không tin role, giá tiền, kết quả thanh toán hay trạng thái đơn do client tự gửi. Bộ này là file cấu hình/schema triển khai, chưa được deploy lên Firebase project của bạn.

## Tài liệu Firebase

- [Deploy bằng Firebase CLI](https://firebase.google.com/docs/cli)
- [Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)
- [Firestore Indexes](https://firebase.google.com/docs/firestore/query-data/indexing)
- [Firestore offline trên Android](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Google Sign-In với Firebase Authentication trên Android](https://firebase.google.com/docs/auth/android/google-signin)


### Cập nhật giao diện yêu cầu và đăng nhập (25/10/2026)

- Mã yêu cầu hiển thị dạng `YC-` + 10 ký tự, ID tài liệu gốc giữ nguyên.
- Trang sản phẩm và khung chat đọc yêu cầu đã lưu: chưa gửi hiện “Gửi yêu cầu”;
  PENDING hiện “Đã gửi yêu cầu” và mở chi tiết; ACCEPTED mở trạng thái đã chấp nhận.
  Khi hủy/từ chối, món còn khả dụng thì cho gửi mới. Không đánh dấu đã gửi khi callable lỗi.
- Thẻ hồ sơ ghi rõ xác minh danh tính là tùy chọn, tách khỏi xác minh email.
- Google dùng Credential Manager với luồng nút Sign in with Google. Cấu hình Firebase
  đã tải lại, đăng ký SHA-1/SHA-256 của debug keystore trên máy hiện tại.
  Khi dùng khóa ký khác cần thêm SHA tương ứng và tải lại cấu hình.
- Admin cloud `admin@fashion2hand.test` đã tạo theo tài khoản seed emulator, có claim
  admin và hồ sơ ADMIN/ACTIVE. Kiểm tra đăng nhập/password và callable adminSession
  trên Firebase thật PASS, không cần email đã xác minh hoặc SĐT.

Kiểm tra đợt cập nhật: 11/11 unit test PASS; 2/2 Android test địa chỉ/trạng thái
yêu cầu PASS (bao gồm chưa gửi → đã gửi → hủy → gửi mới và giữ trạng thái sau
recreate). Firestore/Storage Rules compile dry-run PASS, inventory 50 Main + 6
Admin / 84 layout PASS. Google đã xác nhận cấu hình OAuth/SHA; đăng nhập Google
bằng tài khoản thật cần người dùng chọn tài khoản và xác nhận trên thiết bị.
