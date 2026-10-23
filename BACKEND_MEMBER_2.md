# Backend thành viên 2

Phạm vi: Chats/Messages, FreeRequests, Orders, Payments, Shipments và Notifications. Android gọi các callable trong `MemberTwoRepository`; Cloud Functions dùng Admin SDK để thực hiện thay đổi nhiều tài liệu trong một transaction. Firestore Rules chỉ cấp quyền đọc theo thành viên, mọi ghi nghiệp vụ đều đi qua Functions.

## Luồng đã có

- `createChat`, `sendMessage`, `markChatRead`: tạo cuộc trò chuyện ổn định theo hai thành viên và bài đăng; kiểm tra thành viên trước khi gửi; cập nhật tin nhắn cuối, số chưa đọc và thông báo; đánh dấu đã đọc theo lô an toàn.
- `createFreeRequest`, `respondFreeRequest`, `cancelFreeRequest`: người nhận gửi/hủy yêu cầu; người đăng chấp nhận hoặc từ chối. Chấp nhận giữ bài và tạo đúng một đơn FREE nguyên tử; các yêu cầu còn lại được chuyển sang `UNAVAILABLE`. Có thể gắn địa chỉ nhận nếu địa chỉ đã được lưu trong Firestore.
- `createPaidOrder`: kiểm tra bài SALE còn hoạt động, khóa món trong lúc chờ thanh toán, tạo đơn/thanh toán và hỗ trợ idempotency. Phí ship lấy từ `shippingTariffs/active.minFee` (mặc định 15.000đ), không tin giá/phí do client gửi.
- `simulatePaymentResult`: chuyển trạng thái thanh toán mô phỏng; mặc định bị tắt. Thanh toán thành công chuyển đơn sang chờ người bán, ghi tiền ở trạng thái HOLDING và mở hạn phản hồi 48 giờ. Thất bại giải phóng bài đăng.
- `cancelOrder`, `rejectOrder`, `updateOrderStatus`: người mua hủy hoặc người bán từ chối trước khi chuẩn bị; hoàn tiền mô phỏng nếu đã thanh toán; áp dụng chuyển trạng thái hợp lệ cho người bán/người mua; ghi lịch sử trạng thái. Hoàn tất đơn tạo bút toán giải ngân mô phỏng.
- `createShipment`, `simulateShipmentUpdate`: người bán tạo vận đơn MOCK_CARRIER; cập nhật giao/hoàn, thông báo hai bên và ghi hoàn tiền mô phỏng khi đơn bị hoàn.
- `updatePaymentFromProvider`, `updateShipmentFromCarrier`: webhook có token bí mật, chống xử lý callback thanh toán trùng bằng `webhookEvents`.
- `expireUnpaidAndUnconfirmedOrders`: tự hủy đơn chưa thanh toán sau 15 phút; sau 48 giờ người bán không phản hồi thì ghi hoàn tiền mô phỏng và mở lại bài.
- `markNotificationRead` cùng các listener đọc realtime cho chat, tin nhắn, yêu cầu, đơn, vận đơn và thông báo.

Các khoản thanh toán/hoàn tiền/giải ngân hiện là **mô phỏng**, không di chuyển tiền thật; vận chuyển dùng **MOCK_CARRIER**, chưa gọi hãng giao hàng. Hai callable mô phỏng bị khóa trừ khi môi trường Functions được bật rõ ràng bằng `ENABLE_SIMULATED_PAYMENTS=true` và `ENABLE_SIMULATED_SHIPPING=true`. Không bật hai cờ này trên production.

## Triển khai Firebase

1. Cài dependencies trong thư mục `functions` bằng `npm install`.
2. Deploy Rules/indexes/Functions bằng Firebase CLI: `firebase deploy --only functions,firestore:rules,firestore:indexes`.
3. Đặt `PAYMENT_WEBHOOK_TOKEN` và `SHIPMENT_WEBHOOK_TOKEN` bằng Firebase Functions Secrets trước khi cấu hình webhook.
4. Tạo `shippingTariffs/active` bằng tài khoản quản trị/server, ví dụ `{ "minFee": 15000 }`.
5. Bật Cloud Scheduler cho hàm hết hạn đơn; Firebase có thể yêu cầu bật billing khi triển khai scheduler.

Webhook phải gửi `Authorization: Bearer <secret>`. Thanh toán yêu cầu JSON `{ "eventId", "orderId", "providerReference", "status": "PAID|FAILED|REFUNDED" }`. Vận chuyển yêu cầu `{ "orderId", "trackingCode", "status": "IN_TRANSIT|DELIVERED|RETURNED", "description" }`.

## Lưu ý tích hợp

Các màn hình app hiện chưa gọi `MemberTwoRepository` và còn dùng sản phẩm/đơn demo; cần kết nối giao diện với `postId`, `sellerId`, `orderId` thật và thống nhất schema bài đăng/người dùng với luồng thành viên 1. Repository đã có callable/listener. Cổng thanh toán thật và API hãng vận chuyển cần thông tin merchant/API trước khi tích hợp; hiện webhook là hợp đồng server tổng quát, còn payment/carrier và hoàn tiền/giải ngân vẫn chưa thực sự kết nối nhà cung cấp.
