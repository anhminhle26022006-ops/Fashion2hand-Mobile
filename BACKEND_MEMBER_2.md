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

Các màn giao dịch đang gọi `MemberTwoRepository` qua `TransactionScreens`; chat có `ChatViewModel`, `ChatRepository`, LiveData và `MessageAdapter`. Xem `MINH_ANH_IMPLEMENTATION.md` để phân biệt phần đã kiểm thử, phần kế thừa và phần còn thiếu.

## Cập nhật 24/10/2026

- Chọn cloud hoặc Emulator bằng build property; mặc định dùng cấu hình Firebase cloud, Emulator chỉ bật trong debug khi yêu cầu rõ ràng.
- `getCartCheckoutQuote/createCartOrders`: tối đa 20 món, gộp theo seller, báo giá từ server, giữ tồn kho và xóa đúng món khỏi giỏ trong cùng transaction. Đơn mới có `items`, `itemCount`, `schemaVersion=2`; `postId` và `postSnapshot` vẫn có để đọc tương thích đơn cũ.
- Thanh toán, hủy, từ chối, hết hạn, hoàn hàng, webhook và quyết định tranh chấp Admin cập nhật tất cả món thuộc đơn. `price` là tổng giá các món; phí giao tính một lần mỗi người bán.
- Tin nhắn có mã chống gửi trùng; thông báo và unread được ghi nguyên tử; người bán cũng mở được chat theo bài đăng. Đọc tin nhắn không làm đổi thứ tự danh sách chat.
- Giao thành công ghi `deliveredAt/inspectionDeadline`. Backend xác nhận điều kiện thanh toán, sự kiện giao, tranh chấp và payout trước khi ghi giải ngân mô phỏng. Sau 3 ngày, scheduler hoặc `refreshOrder` đối soát đơn đủ điều kiện. Đơn cũ thiếu deadline không bị tự suy đoán ngày giao.
- Webhook vận chuyển cần thêm `eventId` duy nhất: `{ eventId, orderId, trackingCode, status, description }`. Ghi `webhookEvents`, chặn replay khác payload và chặn chuyển ngược vận đơn đã kết thúc.
- Đã deploy cloud ngày 24/10/2026 sau khi người dùng đăng nhập lại: 39/39 Functions ACTIVE, rules/indexes/Storage rules đã phát hành. Xem `CLOUD_DEPLOYMENT.md`. Không bật billing tự động; mock vẫn chỉ hoạt động trên Emulator.