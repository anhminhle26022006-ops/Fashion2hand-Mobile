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
| `freeRequests/{requestId}` | ID bài đăng/chủ/người yêu cầu, lời nhắn, địa chỉ nhận tùy chọn, trạng thái PENDING/ACCEPTED/REJECTED/CANCELLED/UNAVAILABLE, order ID nếu được nhận, timestamp. Chủ bài chỉ nhận một yêu cầu bằng transaction; yêu cầu còn lại được đánh dấu UNAVAILABLE. |
| `orders/{orderId}` | ID người mua/người bán, `orderType` PAID/FREE, tiền hàng/phí ship/tổng tiền VND, trạng thái đơn/thanh toán/vận chuyển, snapshot sản phẩm và địa chỉ nhận tùy chọn, hạn giữ hàng và hạn phản hồi, các mốc thời gian. Mỗi đơn chỉ có một người bán. |
| `orders/{orderId}/items/{itemId}` | ID bài đăng và snapshot tiêu đề/ảnh/tình trạng/giá; hàng secondhand có số lượng 1. |
| `orders/{orderId}/statusEvents/{eventId}` | Trạng thái trước/sau, ID/loại người thực hiện, lý do, thời gian. Server ghi lịch sử, không sửa/xóa sự kiện cũ. |
| `payments/{paymentId}` | Đơn, người trả, SIMULATED (chờ cấu hình cổng thật), số tiền, trạng thái, mã tham chiếu mô phỏng/nhà cung cấp, timestamp. Chỉ server được ghi. |
| `moneyTransactions/{id}` | Đơn/thanh toán, PAYMENT_RECEIVED/REFUND/SELLER_PAYOUT, số tiền, trạng thái và chế độ SIMULATED; không di chuyển tiền thật. |
| `refunds/{id}` và `payouts/{id}` | Đơn, thanh toán/người bán, số tiền hoàn toàn phần, lý do/trạng thái, chế độ SIMULATED và thời điểm hoàn tất. Không lưu thông tin tài khoản ngân hàng thật. |
| `shipments/{shipmentId}` | Đơn, MOCK_CARRIER, mã vận đơn, trạng thái, phí ship mô phỏng và timestamp. |
| `shipments/{id}/trackingEvents/{eventId}` | Trạng thái, mô tả, địa điểm, nguồn MOCK và thời điểm sự kiện. |
| `chats/{chatId}` | Danh sách ID thành viên, ID bài đăng và order tùy chọn, tin nhắn gần nhất, thời gian cập nhật. |
| `chats/{chatId}/messages/{messageId}` | ID người gửi, loại TEXT/IMAGE, nội dung hoặc đường dẫn Storage, thời điểm gửi/đọc. |
| `reviews/{reviewId}` | ID đơn/người mua/người bán, số sao, nhận xét, thời gian. Server chỉ cho đánh giá đơn hoàn tất và một đánh giá mỗi đơn. |
| `users/{uid}/notifications/{id}` | Loại, tiêu đề/nội dung, loại và ID đối tượng liên quan, trạng thái đã đọc và timestamp. |
| `reports/{reportId}` | Người báo cáo, loại/ID đối tượng, mã lý do, mô tả, trạng thái và thông tin xử lý của admin. |
| `disputes/{disputeId}` | Đơn, người mở, lý do, đường dẫn ảnh bằng chứng trong Storage, trạng thái và quyết định REFUND_FULL hoặc RELEASE_PAYOUT. Admin quyết định. |
| `verificationRequests/{id}` | Hồ sơ xác minh thủ công: userId, họ tên/ngày sinh/loại/số giấy tờ, fingerprint chống gửi trùng, evidencePaths/evidenceBucket, PENDING/APPROVED/REJECTED, ghi chú và người duyệt. Ảnh riêng tư trong Storage; test Emulator dùng giấy tờ/ảnh kiểm thử. Không tích hợp VNeID. |
| `privateAccounts/{uid}` | Ngân hàng, chủ tài khoản, số tài khoản, consentAt, updatedAt; chỉ callable của chủ tài khoản đọc/ghi, client không được đọc collection trực tiếp. Luôn verified=false khi chưa có API ngân hàng. |
| `webhookEvents/{provider_eventId}` | Nhà cung cấp, hash payload, trạng thái xử lý và timestamp; dùng chống xử lý callback trùng. |
| `shippingTariffs/{version}` | Nhà vận chuyển MOCK_CARRIER, phí tối thiểu 15.000 VND, 3.000 VND/km, tiền tệ, phiên bản/trạng thái. Server chụp biểu phí vào đơn. |

## Quy tắc nghiệp vụ đã thống nhất

- Giữ bài đăng chưa thanh toán 15 phút. Sau khi thanh toán thành công, người bán có 48 giờ phản hồi; hết hạn thì hủy đơn, mở lại bài đăng và ghi nhận hoàn tiền mô phỏng.
- Người mua chỉ được hủy trước khi người bán chuyển đơn sang `PREPARING`.
- Phí vận chuyển hiện mô phỏng bằng `shippingTariffs/active.minFee` (mặc định 15.000 VND); chưa tính quãng đường hay gọi hãng vận chuyển. Người nhận trả phí ship với món FREE.
- Sau khi giao thành công, người mua có 3 ngày mở khiếu nại. Admin chọn hoàn toàn phần hoặc giải ngân mô phỏng; phiên bản này không hoàn một phần và không chuyển tiền thật.
- Lưu lịch sử trạng thái, thông báo, giỏ hàng và nhiều địa chỉ. Chat gắn với bài đăng, có thể gắn thêm đơn hàng.
- Firestore không tự bảo đảm unique hay ràng buộc nhiều document. Dùng document ID xác định và transaction/server code để bảo đảm một đánh giá mỗi đơn, một địa chỉ mặc định, và chỉ một người nhận món FREE. Luồng thành viên 2 được xử lý qua Cloud Functions; client chỉ đọc dữ liệu liên quan đến chính họ.

## Mở rộng đơn nhiều món — MINH-ANH, 24/10/2026

Được chủ dự án cho phép trong phiên triển khai. Không migration/xóa đơn cũ.

- `orders.items`: danh sách snapshot `{postId, quantity: 1, price, title, category, size, condition, location}`; tối đa 20 món, chỉ một seller trong mỗi đơn.
- `schemaVersion: 2`, `itemCount`, `subtotal`, `totalAmount`; `price` tiếp tục là tổng giá món để các phép hoàn tiền/giải ngân cũ tương thích.
- `postId` giữ ID món đầu tiên, `postSnapshot.title` dùng nhãn tổng hợp. Backend dùng `order-items.js` để đọc mọi món; nếu thiếu `items`, dùng `postId` của đơn cũ.
- `orderRequests/cart_{hash}` lưu `buyerId, postIds, addressId, orderIds, createdAt` để chống tạo trùng toàn bộ lượt checkout. Client không có quyền ghi collection này.
- Mỗi bài đăng giữ `reservedForOrderId` của đơn theo seller. Mỗi đơn có một payment và một shipment; phí giao tính theo đơn.
- `deliveredAt`, `inspectionDeadline` chỉ backend ghi khi có sự kiện giao. Sau 3 ngày và không có tranh chấp đang mở, có thể tự hoàn tất/giải ngân mô phỏng.
## Bổ sung GHN — mã nguồn 26/10/2026, chưa deploy GHN

- `shippingQuotes/{id}`: buyerId, addressId, postIds, environment, sourceHash chuẩn hóa thứ tự field, snapshot nguồn, groups theo seller, phí và expiresAt. Chỉ server đọc/ghi; client lấy dữ liệu đã lọc bằng callable.
- `shippingConfig/ghn_{environment}`: chính sách kiện ước tính được Admin xác nhận, không có Token.
- `ghnSellerShops/{environment}_{sellerUid}`: shopId, snapshot pickup đã đối chiếu hồ sơ GHN và cờ enabled; chỉ server/Admin callable quản lý.
- `ghnOperations/{reference}`: quyền tạo, payload không có Token, trạng thái CREATING/UNKNOWN/REJECTED/LINKED và mã tra cứu ổn định.
- `ghnTracking/{environment}_{trackingCode}`: ánh xạ mã GHN về operation/order do backend ghi.
- `shipments/{orderId}` bổ sung provider/environment, providerStatus/normalizedStatus, providerUpdatedAt, snapshot địa chỉ/kiện, phí báo giá/phí hãng và ETA; read vẫn giới hạn buyer/seller, write vẫn server-only.
- `orders/{id}` bổ sung shippingQuote, shippingProvider, shippingEnvironment, pickupAddress, paymentMode và shippingNeedsReview. Các snapshot không tự đổi theo hồ sơ sau khi xác nhận.
- `webhookEvents/ghn_{hash}` và notification IDs ổn định dùng chống trùng theo kết quả tra cứu GHN; không tin payload callback để tự đánh dấu giao thành công.

Các collection GHN mới được catch-all deny của rules hiện có bảo vệ, không mở quyền client và không cần composite index mới. Xem `GHN_INTEGRATION.md` để cấu hình bằng Secret/Admin và biết giới hạn thanh toán mô phỏng, mapping seller và ước tính kiện.
