# VNPAY SANDBOX — MINH-ANH

## Trạng thái bàn giao

Đã tích hợp code Java/XML, Functions Node.js 22, Firestore và bộ kiểm thử local. **Chưa deploy VNPAY, chưa dùng credential merchant thật, chưa thanh toán sandbox qua ngân hàng và chưa kiểm thử/đạt SIT.** Không cần đăng nhập SIT để viết code và chạy các kiểm thử này. Không bật production, billing, NAT hay giao dịch tiền thật.

Tài liệu này thay thế mô tả “mọi thanh toán đều mô phỏng” trong các tài liệu cũ **đối với đơn mới được tạo khi `PAYMENT_PROVIDER=VNPAY`**. Đơn SIMULATED cũ không bị migration hoặc tự đổi provider. Đơn nhiều seller tiếp tục chia theo seller; mỗi đơn thanh toán riêng, có thể chứa tối đa 20 món. FREE vẫn không thu tiền qua VNPAY; người nhận trả phí giao cho GHN theo thiết kế hiện tại.

## Cấu hình từ PowerShell tại thư mục gốc

### 1. Nhập credential kín

```powershell
firebase functions:secrets:set VNPAY_TMN_CODE --project fashion2hand
firebase functions:secrets:set VNPAY_HASH_SECRET --project fashion2hand
```

Chỉ dán các giá trị VNPAY đã cấp vào **prompt kín của Firebase CLI**. Không gửi vào chat, tham số lệnh, file `.env`, Android, Firestore, Git hoặc log. Không dùng `--debug` khi thao tác credential. Code không chứa credential giả; credential giả chỉ tồn tại trong test.

### 2. Biến không bí mật

Đã ghép bốn biến sau vào file local `functions/.env.fashion2hand`, giữ các cấu hình GHN khác. File example: `functions/vnpay.env.example`.

```dotenv
PAYMENT_PROVIDER=VNPAY
VNPAY_ENV=sandbox
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://us-central1-fashion2hand.cloudfunctions.net/vnpayReturn
```

Giữ `SHIPPING_PROVIDER=GHN`, `GHN_ENV=staging`, `GHN_TOKEN_ENV=staging`, `GHN_PRODUCTION_ENABLED=false` và Token/shop/parcel/seller mapping GHN đã được kiểm tra. Tạo đơn VNPAY cần snapshot báo giá GHN staging hợp lệ; lỗi GHN không được thay bằng phí giả. Thiếu cấu hình VNPAY thì fail rõ ràng, không chuyển sang mô phỏng.

**QueryDR:** bổ sung `VNPAY_QUERY_IP` bằng IP public outbound **thực tế** của server đã xác định với người vận hành/VNPAY. Không dùng IP thiết bị người mua, IP giả hoặc ví dụ TEST-NET trong test. Cloud Run không mặc định bảo đảm IP outbound cố định. Nếu chưa có IP đúng, QueryDR trả lỗi cấu hình và giữ kết quả chưa xác định; IPN vẫn hoạt động. Không có code tự bật NAT/billing. PAY lấy IP từ hop phải nhất do ingress Cloud Functions/Cloud Run thêm vào `X-Forwarded-For`, chỉ khi có `K_SERVICE`; Emulator dùng socket. Gọi callable trực tiếp qua Functions SDK, không thêm proxy/Hosting rewrite phía trước nếu chưa đánh giá lại chuỗi proxy. IP này không phải bằng chứng xác thực thanh toán.

### 3. Deploy giới hạn

```powershell
.\functions\scripts\deploy-vnpay.ps1
```

Script thực hiện đúng lệnh:

```powershell
firebase deploy --project fashion2hand --only "functions:createVnpayPayment,functions:getVnpayPaymentStatus,functions:reconcileVnpayPayment,functions:vnpayIpn,functions:vnpayReturn,functions:getTransactionCapabilities,functions:createPaidOrder,functions:createCartOrders,functions:cancelOrder,functions:rejectOrder,functions:refreshOrder,functions:simulatePaymentResult,functions:updatePaymentFromProvider,functions:updateOrderStatus,functions:expireUnpaidAndUnconfirmedOrders,functions:createShipment,functions:adminDashboard"
```

Các endpoint mới: `createVnpayPayment`, `getVnpayPaymentStatus`, `reconcileVnpayPayment`, `vnpayIpn`, `vnpayReturn`. Các endpoint còn lại trong lệnh được cập nhật để tạo đúng provider, bảo vệ mock/webhook cũ, xử lý hủy/hết hạn/giải ngân và capability/báo cáo.

**Năm Functions bind cả hai Secret VNPAY:** `createVnpayPayment`, `reconcileVnpayPayment`, `vnpayIpn`, `vnpayReturn`, `getTransactionCapabilities`. Khi đổi Secret, deploy lại cả năm (hoặc chạy lại script đầy đủ). Hai hàm create order và `createShipment/refreshOrder` vẫn giữ binding `GHN_TOKEN`; webhook cũ giữ `PAYMENT_WEBHOOK_TOKEN`.

Không deploy toàn project, rules, indexes hoặc Hosting. Các collection mới bị catch-all deny của rules hiện có chặn cả đọc/ghi client; app lấy trạng thái qua callable đã kiểm tra buyer/seller. Chỉ deploy trên project đã có dịch vụ được phép dùng; nếu Firebase yêu cầu bật billing/API tính phí mới, dừng để chủ project xử lý, không tự bật. Script không tự cấp quyền hoặc bật dịch vụ.

### 4. URL gửi VNPAY

**URL dự kiến theo export/region hiện có, chưa xác nhận hoạt động trên Cloud:**

- IPN: `https://us-central1-fashion2hand.cloudfunctions.net/vnpayIpn`
- Return: `https://us-central1-fashion2hand.cloudfunctions.net/vnpayReturn`

Sau khi deploy thành công, đối chiếu URL thực tế Firebase CLI in ra (hoặc `firebase functions:list --project fashion2hand`). Nếu region/export khác phải sửa cấu hình Return và deploy lại trước khi dùng. Không dùng trang chủ `fashion2hand.web.app` làm Return: lượt này không thêm Hosting rewrite/App Links.

Bạn tự trả lời email VNPAY đã cấp credential, gửi **IPN URL HTTPS thực tế sau deploy** và đề nghị họ cấu hình IPN server-to-server cho merchant sandbox. Không gửi HASH_SECRET trong email. Mẫu nội dung: “Nhờ VNPAY cấu hình IPN cho merchant sandbox của Fashion2hand tại URL [URL thực tế]. Return URL là [URL thực tế]. Hiện chưa truy cập được SIT; nhờ hỗ trợ cấp quyền/hướng dẫn chạy bộ SIT.” Codex không tự gửi email.

Kiểm tra GET IPN không có chữ ký hợp lệ phải trả HTTP 200 với `RspCode=97`, không ghi dữ liệu. Đây chỉ là kiểm tra endpoint/protocol, **không chứng minh IPN VNPAY đã được đăng ký hoặc đã nghiệm thu SIT**. GET Return không có callback hợp lệ trả 400, không đổi payment.

## Luồng và bảo vệ dữ liệu

`createPaidOrder/createCartOrders` tạo payment VNPAY với amount VND từ báo giá GHN, môi trường sandbox và deadline giữ hàng 15 phút. `createVnpayPayment` kiểm tra buyer, payment, giá/phí, mọi món RESERVED, quote staging và deadline; bỏ qua amount/sellerId client. Thiếu 30 giây thì không mở phiên. Một phiên PENDING còn hiệu lực được dùng lại khi bấm lặp; sau thất bại mới tạo reference ngẫu nhiên mới. Không gọi mạng trong transaction. URL được ký sau transaction, không lưu signed URL/credential vào Firestore; các attempt giữ tham số server cần tái tạo phiên.

IPN là GET HTTP public, không yêu cầu Firebase Bearer/eventId. HMAC-SHA512 theo PAY 2.1.0, sort/encode tham số như mẫu Node chính thức, loại `vnp_SecureHash`/`vnp_SecureHashType`; từ chối query lặp kể cả key encode, mảng và encoding lỗi. Chỉ chấp nhận thành công khi cả ResponseCode và TransactionStatus là `00`, TmnCode/ref/amount/transaction number khớp. VND nguyên được nhân 100 đúng một lần; giới hạn tích hợp 5.000–9.999.999.999 VND; create/expire dùng GMT+7.

Một transaction Firestore ghi attempt, evidence, order, payment, receipt, history và notification. Chỉ ACK sau commit; lỗi commit trả 99 để VNPAY retry. Callback trùng không tạo thêm ledger/thông báo; callback thất bại cũ không hạ trạng thái đã thành công. Mã giao dịch VNPAY được khóa theo merchant fingerprint để tránh dùng cùng transaction cho hai reference.

Thành công sau hết hạn/hủy hoặc sau khi món thuộc đơn khác: ghi nhận payment và receipt `REVIEW_REQUIRED`, giữ đơn đóng, không lấy lại hàng. Thành công thứ hai được lưu thành receipt riêng `vnpay_extra_*`, giữ khoản thanh toán đầu, đánh dấu cần đối soát. Các trạng thái đảo/nghi ngờ/hoàn trả được ghi nhận cần xem xét, không tự báo đã trả tiền. `paymentNeedsReview` chặn tạo thêm phiên, tạo vận đơn và settlement tự động.

QueryDR ký theo chuỗi pipe **riêng** của API querydr/refund, kiểm chữ ký response gồm cả trường promotion tùy chọn, đối chiếu merchant/reference/type và dùng chung reducer với IPN. Rate limit 60 giây/đơn, chỉ buyer/seller được gọi. Timeout, HTTP lỗi hoặc mã chưa tìm thấy không đánh dấu FAILED. Không triển khai gọi Refund API trong bản này.

Return chỉ xác minh để điều hướng, không ghi PAID và không công khai trạng thái/chi tiết đơn. Link cố định `fashion2hand://payment/{orderId}` chỉ có reference. Android dùng Custom Tabs, giữ reference qua Bundle/preferences, refresh khi resume và poll ngắn khi chờ. Đóng trình duyệt không hủy phiên. Khi app bị hủy tiến trình, mở lại đơn để tiếp tục attempt còn hiệu lực; các đơn và trạng thái từng seller được đọc lại từ server. Deep link chỉ mở màn hình, mọi trạng thái đều lấy qua Firebase Auth/callable, không tự cấp quyền.

## IPN response mapping

| HTTP | RspCode | Ý nghĩa |
|---|---|---|
| 200 | 00 | Đã commit kết quả (kể cả thất bại đã ghi nhận hoặc khoản cần đối soát). |
| 200 | 02 | Kết quả/giao dịch đã xử lý; không tạo receipt trùng. |
| 200 | 01 | Không tìm được attempt/payment/merchant phù hợp. |
| 200 | 04 | Amount không khớp số tiền dự kiến. |
| 200 | 97 | Checksum/query không hợp lệ. |
| 200 | 99 | Cấu hình, dữ liệu đã ký hoặc xử lý/commit gặp lỗi; không ACK thành công. |
| 405 | 99 | Sai method; endpoint dùng GET. |

Theo tài liệu VNPAY, 00/02 kết thúc IPN; 01/04/97/99 hoặc timeout có thể được retry (tối đa 10 lần, cách 5 phút). Không coi HTTP 200 đơn thuần là thanh toán thành công.

## Hoàn tiền, giải ngân và GHN

Sandbox không chuyển tiền thật và không cung cấp bằng chứng về ví/escrow. `HOLDING` chỉ là trạng thái ledger của app. Hủy/từ chối/hết hạn phản hồi đơn VNPAY đã PAID: giữ receipt/payment PAID, đơn CANCELLED, refund `REVIEW_REQUIRED`, chưa REFUNDED. Settlement sau giao hàng có thể hoàn tất đơn, nhưng payout là `REVIEW_REQUIRED`; không ghi đã chuyển tiền. Admin dispute handler hiện từ chối xử lý tiền của provider ngoài SIMULATED/MOCK; việc hoàn/trả VNPAY cần đối soát thủ công hoặc triển khai API riêng sau này. Trạng thái PAID không bị giả bởi mock callable/webhook cũ.

Đơn VNPAY sandbox đã xác minh có thể tiếp tục seller/GHN staging khi đủ trạng thái và có receipt hợp lệ. GHN prepaid dùng `cod_amount=0`, `payment_type_id=1`; không thu lại tiền hàng/phí đã trả. GHN production với đơn PAID vẫn bị chặn. FREE giữ `payment_type_id=2`, không dùng VNPAY.

## Kiểm thử và nghiệm thu còn lại

Kết quả cuối: **49/49 backend tests**, Android build thành công, **14/14 unit tests**. ADB không có thiết bị kết nối nên chưa smoke test thiết bị.

Unit tests dùng credential giả chỉ trong test; IPN/QueryDR được mock theo giao thức, transaction dùng Firestore Emulator demo project. Có kiểm tra HTTP IPN localhost, callback cạnh tranh/trùng/sai thứ tự, late/duplicate success, hai attempt, Return trước/sau IPN, QueryDR phục hồi, mock bypass, nhiều món/đơn cũ, cancel/timeout/settlement và zero COD. Đây **không phải kiểm thử ngân hàng sandbox hay SIT**.

```powershell
cd functions
npm run test:vnpay
# Khi Firestore Emulator đang chạy tại localhost:8080:
npm run test:vnpay:emulator
cd ..
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Sau cấu hình/deploy: đặt đơn mới đủ điều kiện → tạo phiên → mở VNPAY sandbox → dùng thẻ NCB thử trong email VNPAY, nhập thẻ/OTP chỉ trên VNPAY → kiểm tra Return → kiểm tra IPN thực sự đến, payment/ledger/order/notification và Merchant Admin. Thử khách hủy/thanh toán thất bại, đóng trình duyệt, mở lại app và QueryDR khi IPN chậm. Kiểm tra mỗi đơn của mỗi seller riêng; không gắn nhãn cả giỏ PAID. Tiếp tục seller/GHN staging để kiểm zero COD. Chạy bộ SIT sau khi được cấp quyền; không tuyên bố SIT/production đã đạt.

Không log signed URL, callback đầy đủ, Authorization hoặc lỗi provider thô. Lưu ý access log tự động của nền tảng có thể chứa query URL: giới hạn người đọc log và retention theo dự án; không chia sẻ log URL thô.

## Nguồn chính thức đã đối chiếu

- PAY/signature/IPN/Return: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- QueryDR/Refund: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html
- Mã lỗi: https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/
- Functions Secrets: https://firebase.google.com/docs/functions/config-env
- Cloud Functions headers: https://docs.cloud.google.com/functions/docs/reference/headers
- Android Custom Tabs: https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs
