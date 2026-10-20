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
