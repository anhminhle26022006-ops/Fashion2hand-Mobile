# Mô hình dữ liệu Firestore — Fashion2hand

Firestore lưu dữ liệu thành collection và document, không phải bảng quan hệ. Dùng ID document để liên kết dữ liệu; sao chép một số trường mô tả cần thiết vào đơn hàng để giữ lịch sử khi bài đăng thay đổi.

## Collections và trường dữ liệu chính

| Đường dẫn | Dữ liệu chính / mục đích |
|---|---|
| `users/{uid}` | `uid`, tên hiển thị, email, số điện thoại, đường dẫn ảnh đại diện, role, trạng thái tài khoản/xác minh, thời gian tạo/cập nhật. Chủ tài khoản đọc hồ sơ; role do server quản lý. |
| `users/{uid}/addresses/{addressId}` | Tên/số điện thoại nhận hàng, tỉnh/quận/phường/đường, ghi chú, latitude/longitude, địa chỉ mặc định, thời gian tạo/cập nhật. Chỉ một địa chỉ mặc định; cập nhật bằng transaction. |
| `categories/{categoryId}` | Tên, slug, `parentId`, thứ tự hiển thị, trạng thái hoạt động, đường dẫn ảnh. Quản trị viên quản lý. |
| `posts/{postId}` | ID người đăng/danh mục, tiêu đề/mô tả, SALE hoặc FREE, giá VND dạng số nguyên (FREE có giá 0), tình trạng, nhãn hiệu/size/màu, tỉnh/quận, trạng thái, thời hạn giữ sản phẩm, lượt xem/yêu thích, timestamp. |
| `posts/{postId}/images/{imageId}` | Đường dẫn Firebase Storage, thứ tự ảnh, thời gian tạo. File ảnh lưu ở Storage, không lưu trực tiếp trong document. |
| `users/{uid}/favorites/{postId}` | ID bài đăng và thời điểm yêu thích. Dùng post ID làm document ID để tránh yêu thích trùng. |
| `users/{uid}/cartItems/{postId}` | ID bài đăng, snapshot người bán, thời gian thêm. Giỏ có thể chứa nhiều người bán; checkout chia thành một đơn cho mỗi người bán. |
| `freeRequests/{requestId}` | ID bài đăng/chủ/người yêu cầu, lời nhắn, trạng thái PENDING/ACCEPTED/REJECTED/CANCELLED, phản hồi, order ID nếu được nhận, timestamp. Chủ bài nhận đúng một yêu cầu bằng transaction. |
| `orders/{orderId}` | ID người mua/người bán, SALE/FREE, tiền hàng/phí ship/tổng tiền VND, trạng thái đơn/thanh toán/vận chuyển, snapshot báo giá và biểu phí vận chuyển, snapshot địa chỉ giao/nhận, hạn giữ hàng và hạn phản hồi, các mốc thời gian. Mỗi đơn chỉ có một người bán. |
| `orders/{orderId}/items/{itemId}` | ID bài đăng và snapshot tiêu đề/ảnh/tình trạng/giá; hàng secondhand có số lượng 1. |
| `orders/{orderId}/statusEvents/{eventId}` | Trạng thái trước/sau, ID/loại người thực hiện, lý do, thời gian. Server ghi lịch sử, không sửa/xóa sự kiện cũ. |
| `payments/{paymentId}` | Đơn, người trả, VNPAY_SANDBOX, số tiền, trạng thái, mã giao dịch phía cổng, idempotency key, timestamp. Chỉ server được ghi. |
| `moneyTransactions/{id}` | Đơn/thanh toán, PAYMENT_RECEIVED/REFUND/SELLER_PAYOUT, số tiền, chế độ SIMULATED, idempotency key và lịch sử. |
| `refunds/{id}` và `payouts/{id}` | Đơn, thanh toán/người bán, số tiền hoàn toàn phần, lý do/trạng thái, chế độ SIMULATED và thời điểm hoàn tất. Không lưu thông tin tài khoản ngân hàng thật. |
| `shipments/{shipmentId}` | Đơn, MOCK_CARRIER, mã vận đơn, trạng thái, phí ship, dự kiến giao và timestamp. |
| `shipments/{id}/trackingEvents/{eventId}` | Trạng thái, mô tả, địa điểm, nguồn MOCK và thời điểm sự kiện. |
| `chats/{chatId}` | Danh sách ID thành viên, ID bài đăng và order tùy chọn, tin nhắn gần nhất, thời gian cập nhật. |
| `chats/{chatId}/messages/{messageId}` | ID người gửi, loại TEXT/IMAGE, nội dung hoặc đường dẫn Storage, thời điểm gửi/đọc. |
| `reviews/{reviewId}` | ID đơn/người mua/người bán, số sao, nhận xét, thời gian. Server chỉ cho đánh giá đơn hoàn tất và một đánh giá mỗi đơn. |
| `users/{uid}/notifications/{id}` | Loại, tiêu đề/nội dung, loại và ID đối tượng liên quan, trạng thái đã đọc và timestamp. |
| `reports/{reportId}` | Người báo cáo, loại/ID đối tượng, mã lý do, mô tả, trạng thái và thông tin xử lý của admin. |
| `disputes/{disputeId}` | Đơn, người mở, lý do, đường dẫn ảnh bằng chứng trong Storage, trạng thái và quyết định REFUND_FULL hoặc RELEASE_PAYOUT. Admin quyết định. |
| `verificationRequests/{id}` | Người dùng, trạng thái/mô tả xác minh mô phỏng, người duyệt và timestamp. Không lưu giấy tờ tùy thân thật. |
| `webhookEvents/{provider_eventId}` | Nhà cung cấp, hash payload, trạng thái xử lý và timestamp; dùng chống xử lý callback trùng. |
| `shippingTariffs/{version}` | Nhà vận chuyển MOCK_CARRIER, phí tối thiểu 15.000 VND, 3.000 VND/km, tiền tệ, phiên bản/trạng thái. Server chụp biểu phí vào đơn. |

## Quy tắc nghiệp vụ đã thống nhất

- Giữ bài đăng chưa thanh toán 15 phút. Sau khi thanh toán thành công, người bán có 48 giờ phản hồi; hết hạn thì hủy đơn, mở lại bài đăng và ghi nhận hoàn tiền mô phỏng.
- Người mua chỉ được hủy trước khi người bán chuyển đơn sang `PREPARING`.
- Phí vận chuyển mô phỏng: `max(15.000 VND, quãng đường thẳng km × 3.000 VND)`, tính từ tọa độ địa chỉ đã lưu và lưu snapshot giá/phiên bản vào đơn. Người nhận trả phí ship với món FREE.
- Sau khi giao thành công, người mua có 3 ngày mở khiếu nại. Admin chọn hoàn toàn phần hoặc giải ngân mô phỏng; phiên bản này không hoàn một phần và không chuyển tiền thật.
- Lưu lịch sử trạng thái, thông báo, giỏ hàng và nhiều địa chỉ. Chat gắn với bài đăng, có thể gắn thêm đơn hàng.
- Firestore không tự bảo đảm unique hay ràng buộc nhiều document. Dùng document ID xác định và transaction/server code để bảo đảm một đánh giá mỗi đơn, một địa chỉ mặc định, và chỉ một người nhận món FREE.
