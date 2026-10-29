# Trang giới thiệu Fashion2hand

Project hiện có: `fashion2hand`.

URL chính: https://fashion2hand.web.app
URL tương đương: https://fashion2hand.firebaseapp.com

Nội dung nằm trong `hosting/public/index.html`, giao diện trong `hosting/public/styles.css`.
Trang tĩnh không dùng Firebase SDK, không gọi Functions hoặc truy cập Firestore.
Thông tin liên hệ công khai: 0378750255 và anhminhle26022006@gmail.com, cùng mục Liên hệ hỗ trợ trong ứng dụng. Chỉnh sửa phần `#contact` trong HTML khi cần cập nhật.

Deploy lại riêng Hosting từ thư mục gốc repository:

```powershell
firebase deploy --only hosting --project fashion2hand
```

Lệnh này không triển khai Functions, Firestore Rules/Indexes hoặc Storage Rules. Không cần khởi tạo lại dự án, không cần build Android hoặc thay đổi gói thanh toán.
