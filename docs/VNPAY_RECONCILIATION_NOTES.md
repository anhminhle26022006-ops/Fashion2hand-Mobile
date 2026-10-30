# Chẩn đoán thanh toán Sandbox ngày 28/10/2026

- Giao dịch 72.500đ có Return hợp lệ và QueryDR trực tiếp đã xác minh chữ ký, trả responseCode=00, transactionStatus=00.
- Trong log kiểm tra, chưa có IPN đến Function cho giao dịch này. Request kiểm tra công khai không chữ ký nhận HTTP 200/RspCode 97, đúng hành vi từ chối; không phải thử thanh toán thành công.
- Các lượt reconcileVnpayPayment gần thời điểm quay lại ứng dụng trả HTTP 503. Chưa đủ bằng chứng kết luận nguyên nhân mạng Cloud Run; không tự bật NAT, đổi production hay hạ xác minh chữ ký.
- Kiểm tra mới lúc 05:40 (UTC+7): Cloud us-central1 phân giải được DNS Sandbox nhưng kết nối cổng 443 không nhận được TCP connected trước timeout, cả TLS mặc định và TLS 1.2. Cùng instance kết nối Internet đối chứng thành công; máy phát triển kết nối Sandbox thành công. Chưa kết luận bên nào chặn đường mạng; đã chuẩn bị VNPAY_SANDBOX_SUPPORT.md để yêu cầu kiểm tra kết nối và log IPN.
- Đã sửa thiếu tham số logger tại điểm tích hợp index.js để lỗi QueryDR được ghi nhận bằng mã an toàn. Phép thử mạng tạm thời đã gỡ sau khi thu thập bằng chứng.

## Bản sửa

- QueryDR phân biệt lỗi lấy IP, timeout, lỗi mạng, HTTP và chữ ký. Log chỉ chứa mã lỗi, trạng thái HTTP và hash tham chiếu, không chứa khóa, token, số thẻ hay request đã ký.
- App giữ thông báo lỗi qua các lượt poll, không ghi đè bằng dòng chờ chung.
- Phản hồi QueryDR mã 94 không có chữ ký chỉ được dùng để tạm dừng tra cứu 5 phút, không làm bằng chứng thanh toán. Server lưu thời hạn chờ; app giữ thời hạn khi xoay màn hình và thông báo rõ cho người dùng.
- Kiểm tra lại có thể yêu cầu đối soát thủ công qua callable được xác thực, kể cả khi đơn đã đóng/hết hạn. Server vẫn giới hạn tần suất và giữ mọi kiểm tra sở hữu, merchant, chữ ký và số tiền.
- QueryDR thành công sau khi mất quyền giữ hàng chỉ ghi nhận cần đối soát; không mở lại đơn hay chiếm lại món.
- Return không ghi PAID. Chỉ IPN/QueryDR được xác minh mới đi qua bộ xử lý kết quả hiện có.

## Triển khai

```powershell
firebase deploy --project fashion2hand --only functions:reconcileVnpayPayment
```

Cần APK mới để có thông báo và nút kiểm tra đã sửa. Chưa tuyên bố kiểm thử SIT hoặc chứng minh kết nối QueryDR từ Cloud Run đã hết lỗi.

Đã deploy thành công riêng reconcileVnpayPayment lên fashion2hand và cài APK Cloud mới lên emulator-5554. Kiểm thử đạt: 12 provider, 21 Firestore Emulator và 27 Android unit tests. Chưa xác minh giao dịch mới xuyên suốt qua IPN thật.

## Ghi nhận giao dịch đã trả

Sau khi người dùng cho phép rõ ràng, đã truy vấn lại VNPAY và xác minh chữ ký, merchant, tham chiếu và số tiền 72.500đ. Bộ xử lý nguyên tử hiện có trả receiptCode=00: đơn giữ CANCELLED, thanh toán PAID, paymentNeedsReview=true. Không khôi phục hàng, không thu thêm tiền, không dùng Return thay QueryDR. Đây là giao dịch Sandbox, chưa xác nhận kiểm thử SIT đầy đủ.
