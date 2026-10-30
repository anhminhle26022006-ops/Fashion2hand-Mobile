# Yêu cầu kiểm tra kết nối VNPAY Sandbox

Nội dung có thể gửi bộ phận hỗ trợ VNPAY; không đính kèm HashSecret, token, mật khẩu hoặc URL đã ký.

**Tiêu đề:** Fashion2hand – Cloud Functions không kết nối được Sandbox, chưa nhận IPN

Chào đội hỗ trợ VNPAY,

Fashion2hand tích hợp Sandbox trên Firebase Cloud Functions thế hệ 2, Node.js 22, vùng us-central1.

- URL QueryDR: https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
- IPN đã đăng ký: https://us-central1-fashion2hand.cloudfunctions.net/vnpayIpn
- Return: https://us-central1-fashion2hand.cloudfunctions.net/vnpayReturn
- Giao dịch thử gần nhất: b0562a86fd1e99e6c0cbdbe56385c9ff, số tiền 72.500đ, thời điểm thanh toán 28/10/2026 05:31:49 (UTC+7).
- Return báo responseCode=00 và transactionStatus=00, nhưng chưa tìm thấy request IPN cho giao dịch này trong log Cloud.
- Lúc 05:32, QueryDR từ Cloud trả lỗi timeout cho ứng dụng. Thử kết nối độc lập lúc 05:38 và 05:40: Cloud phân giải được DNS Sandbox nhưng chưa nhận được sự kiện TCP connected tới cổng 443 và cuối cùng timeout (cả TLS mặc định lẫn giới hạn TLS 1.2); cùng môi trường kết nối TCP/TLS tới api.ipify.org thành công. Máy phát triển ở Việt Nam kết nối TCP/TLS tới Sandbox thành công.

Nhờ VNPAY kiểm tra đường kết nối từ Google Cloud us-central1 đến Sandbox có bị giới hạn theo mạng/IP/khu vực không, và kiểm tra log gửi IPN của giao dịch trên (URL đích, HTTP status, cơ chế gửi lại). Hệ thống hiện dùng IP outbound động, chưa cấu hình NAT/IP cố định.

Chúng tôi không dùng Return làm bằng chứng thanh toán; chỉ cập nhật sau IPN hoặc QueryDR có chữ ký và dữ liệu khớp hợp lệ. Xin hướng dẫn cấu hình Sandbox phù hợp, không chuyển sang production để thử.
