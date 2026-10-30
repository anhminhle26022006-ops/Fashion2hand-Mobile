# Tích hợp GHN — Fashion2hand

Mã nguồn tích hợp dùng Firebase project **fashion2hand**, Node.js 22 và Android Java/XML hiện có. Chưa deploy các Functions GHN hoặc gọi GHN thật khi chưa có credential. Không tạo/hủy đơn production trong kiểm thử. Thanh toán, hoàn tiền, giải ngân vẫn **SIMULATED**.

Các file chính:

| File | Vai trò |
|---|---|
| `functions/ghn-provider.js` | API GHN, endpoint theo môi trường, timeout/lỗi đã lọc, danh mục và trạng thái |
| `functions/ghn-checkout.js` | Báo giá, snapshot, giỏ nhiều seller, idempotency và yêu cầu FREE |
| `functions/ghn-shipments.js` | Giữ quyền tạo, phục hồi UNKNOWN, tra cứu, hủy và callback đã đối chiếu GHN |
| `functions/ghn-integration.js`, `functions/index.js` | Nối callable hiện có, bind Secret, cấu hình Admin, chặn mock/webhook cũ sửa đơn GHN |
| `MemberTwoRepository.java`, `TransactionScreens.java` | Chọn địa chỉ, báo giá/tổng tiền, tạo/làm mới/hủy vận đơn trong màn hiện có |
| `GhnAddressPicker.java`, `FirebaseShippingService.java`, `FreeRequestFlow.java`, `MainActivity.java` | Chọn mã GHN, backend adapter và cập nhật lại phí FREE |
| `functions/scripts/ghn-admin.ps1`, `functions/ghn.env.example` | Cấu hình Shop ID, điểm gửi, kiện hàng; không chứa Token |

Các file Java ở `app/src/main/java/vn/edu/ueh/thudinh/fashion2hand/`, repository ở thư mục con `repository`, adapter ở `service`.

## 1. Nhập Token trong terminal của chính bạn

Chạy từ thư mục gốc repository, nơi có `firebase.json`:

```powershell
firebase functions:secrets:set GHN_TOKEN --project fashion2hand
```

Tại prompt nhập giá trị Secret của Firebase CLI, dán **Token GHN staging** của bạn rồi Enter. Không đưa Token vào lệnh, `.env`, JSON cấu hình, Android, Firestore, Git, log hoặc chat. Không cần gửi Token cho Codex. Khi đổi Token, chạy lại lệnh trên và deploy lại các hàm có bind Secret.

Firebase sử dụng `defineSecret('GHN_TOKEN')`; chỉ những hàm có gọi GHN được bind `secrets: [GHN_TOKEN]`. Tên Token không đồng nghĩa với môi trường: bạn phải nhập đúng Token tương ứng với `GHN_ENV`.

## 2. Chọn môi trường và Shop ID

```powershell
if (!(Test-Path functions/.env.fashion2hand)) { Copy-Item functions/ghn.env.example functions/.env.fashion2hand }
notepad functions/.env.fashion2hand
```

Giữ các biến cấu hình khác đã có trong file; chỉ thêm/cập nhật những dòng GHN dưới đây. Giữ staging và điền Shop ID **thực tế** thuộc cùng tài khoản staging:

```dotenv
SHIPPING_PROVIDER=GHN
GHN_ENV=staging
GHN_TOKEN_ENV=staging
GHN_SHOP_ID=<SHOP_ID_STAGING_THỰC_TẾ>
GHN_PRODUCTION_ENABLED=false
```

Thay toàn bộ `<...>` bằng số Shop ID, không giữ dấu ngoặc. Shop ID không phải Token; có thể cung cấp cho Codex để cấu hình. `GHN_SHOP_ID` là shop dùng để tra danh mục địa chỉ. Shop gửi từng seller được lưu bằng ánh xạ riêng phía server.

Không tự chuyển sang production. Để chuyển sau khi đã nghiệm thu, người vận hành phải **chủ động** thay cả `GHN_ENV=production`, `GHN_TOKEN_ENV=production`, Shop ID production, nhập lại `GHN_TOKEN` production và deploy lại. Chỉ đặt `GHN_PRODUCTION_ENABLED=true` nếu thực sự muốn tạo/hủy vận đơn thật và chấp nhận phí GHN. Không tự thay đổi billing Firebase.

Trong phiên bản này, đơn PAID production bị chặn vì thanh toán/COD tiền hàng chưa được tích hợp thật. Staging cho phép kiểm thử PAID với thanh toán mô phỏng trên Emulator. Đơn FREE dùng `payment_type_id=2` (người nhận trả phí), `cod_amount=0`. PAID staging dùng `payment_type_id=1`, COD bằng 0 để không thu lại tiền hàng đã trả trước. Không có luồng nhận trực tiếp trong checkout hiện tại nên không thêm giả một phương thức nhận trực tiếp mới.

## 3. Deploy đúng các Functions liên quan

Sau khi đã nhập Secret và điền `.env.fashion2hand`:

```powershell
firebase deploy --project fashion2hand --only "functions:getCheckoutQuote,functions:getCartCheckoutQuote,functions:createPaidOrder,functions:createCartOrders,functions:createFreeRequest,functions:respondFreeRequest,functions:createShipment,functions:refreshOrder,functions:getTransactionCapabilities,functions:cancelGhnShipment,functions:ghnCallback,functions:ghnAdminShops,functions:ghnAddressCatalog,functions:saveGhnAddress,functions:ghnConfigureSeller,functions:ghnConfigureParcel,functions:simulateShipmentUpdate,functions:updateShipmentFromCarrier"
```

Không chạy lệnh deploy toàn bộ project. Không cần thay Rules/indexes: các collection GHN mới bị deny bởi catch-all hiện có; orders/shipments đã cấm client ghi. `users/{uid}/addresses` vẫn do chủ tài khoản quản lý, vì vậy backend **luôn kiểm tra mã địa chỉ bằng danh mục GHN**, không tin cờ xác minh hoặc tên do client tự ghi.

URL callback **dự kiến sau khi deploy thành công**:

```text
https://us-central1-fashion2hand.cloudfunctions.net/ghnCallback
```

Đây chưa phải tuyên bố endpoint đã được triển khai. Lấy URL thực tế từ Firebase CLI/Console sau deploy và kiểm tra trước khi khai báo GHN.

## 4. Địa chỉ của buyer và điểm gửi của seller

1. Lưu địa chỉ thông thường trong app. Khi mở checkout, chọn địa chỉ thuộc tài khoản của bạn.
2. Bấm **Chọn / cập nhật địa chỉ giao hàng GHN**. Chọn tỉnh → quận/huyện → phường theo danh mục **GHN**, xác nhận điểm nhận đúng số nhà/đường đã lưu.
3. Seller mở **Trung tâm giao dịch → Tặng/Bán → Thiết lập địa chỉ lấy hàng GHN**, không cần có đơn đầu tiên. Cũng có thể chọn từ chi tiết đơn đã có. Sau đó quản trị viên ánh xạ địa chỉ này tới shop GHN đăng ký đúng điểm lấy hàng thực tế.

Ứng dụng giữ nguyên địa chỉ Việt Nam hệ mới đã có. Mapping GHN dùng danh mục GHN theo district/ward riêng (`GHN_LEGACY`), không lấy `provinceCode`, `wardCode` của Việt Nam hoặc ID document làm mã GHN. Không tự suy diễn tên cũ/mới; người dùng phải xác nhận địa điểm. Tích hợp chưa tự chuyển sang API danh mục GHN hệ mới 2 cấp.

### Công cụ cấu hình server/admin

`functions/scripts/ghn-admin.ps1` nhập email trong terminal và đọc mật khẩu bằng `Read-Host -AsSecureString`, không phải tài khoản GHN. Mật khẩu được giữ nguyên (không Trim), serialize bằng `ConvertTo-Json`, gửi byte UTF-8; script không in/lưu password, Firebase ID token hoặc GHN Token. Tài khoản phải có claim `admin=true`, hồ sơ `role=ADMIN`, `accountStatus=ACTIVE`; backend kiểm tra lại Auth hiện tại để chặn claim đã thu hồi. Script không tạo/xóa tài khoản hoặc tự cấp quyền Admin.

Script chọn client Firebase theo `applicationId` trong Gradle, đối chiếu project với `.firebaserc`, không lấy mù `client[0]`. Kiểm tra cấu hình an toàn, không hỏi mật khẩu/gọi API:

```powershell
.\functions\scripts\ghn-admin.ps1 -CheckConfig -Target Cloud
.\functions\scripts\ghn-admin.ps1 -CheckConfig -Target Emulator
```

`Cloud` là mặc định. `-Target Emulator` (hoặc cờ cũ `-UseEmulators`) chỉ dùng tài khoản Auth Emulator ở localhost; không đọc tài khoản Auth cloud. Hai chế độ này là môi trường **Firebase**, độc lập với `GHN_ENV=staging/production`. Script không tự chuyển chế độ theo biến môi trường hay theo GHN staging. Sau đăng nhập, script kiểm tra project trong ID token và chỉ báo cờ Admin True/False; không in ID token. Lỗi có dòng `Stage | HTTP | Code` đã lọc, không in response thô.

Đã kiểm tra đọc cấu hình cloud: project/package/API key khớp file Android local và tài nguyên đã build. Request kiểm tra Firebase Auth không chứa email/mật khẩu trả `MISSING_EMAIL`, không đăng nhập hay sửa tài khoản. `ghnAdminShops` và `ghnCallback` hiện ACTIVE ở staging, Shop ID 2543108 và có bind GHN_TOKEN theo metadata cloud. Chưa dùng mật khẩu Admin thật để nghiệm thu liệt kê shop.

Liệt kê shop để đối chiếu Shop ID, địa chỉ và môi trường:

```powershell
.\functions\scripts\ghn-admin.ps1 -Action ghnAdminShops -Target Cloud
```

Nếu có nhiều hơn 50 shop, tạo JSON `{"offset": <last_offset>}` theo kết quả và truyền `-DataFile` để xem trang tiếp theo.

Tạo file JSON không chứa Token, ví dụ `.gradle/ghn-seller.json` với giá trị **thực tế**:

```json
{
  "sellerId": "UID_FIREBASE_CUA_SELLER",
  "addressId": "ID_DIA_CHI_DA_LUU_CUA_SELLER",
  "shopId": 0,
  "confirmRegisteredPickup": true
}
```

**Thay `0` bằng Shop ID thật trước khi chạy**; 0 bị backend từ chối. `addressId` chỉ để đọc địa chỉ thuộc seller; mã vận chuyển vẫn là mã GHN đã xác minh. `confirmRegisteredPickup=true` là xác nhận có chủ đích rằng địa chỉ đăng ký của shop đúng điểm lấy hàng của seller.

```powershell
.\functions\scripts\ghn-admin.ps1 -Action ghnConfigureSeller -DataFile .gradle/ghn-seller.json
```

Hàm đối chiếu shop thuộc Token, district/ward của điểm gửi, lấy địa chỉ đầy đủ từ hồ sơ GHN rồi lưu snapshot server tại `ghnSellerShops/{environment}_{sellerUid}`. Không mặc định lấy hàng tại địa chỉ chủ app. Với tài khoản chỉ có một shop, chỉ ánh xạ seller thực sự gửi tại shop đó; các seller khác bị chặn. Có thể cấu hình nhiều shop đã đăng ký hợp lệ trong cùng tài khoản GHN; chưa tự tạo shop hay onboarding người bán bên ngoài tài khoản GHN.

### Kiện hàng ước tính có cấu hình rõ ràng

Tạo `.gradle/ghn-parcel.json` sau khi đo/ước tính một món quần áo đóng gói. Ví dụ để bạn **tự xác nhận và điều chỉnh**, không phải giá trị được code tự bật:

```json
{"parcelEstimate":{"estimated":true,"weight":300,"length":25,"width":20,"height":3}}
```

`weight` tính bằng **gram**, kích thước bằng **cm**. Chạy:

```powershell
.\functions\scripts\ghn-admin.ps1 -Action ghnConfigureParcel -DataFile .gradle/ghn-parcel.json
```

Lưu tại `shippingConfig/ghn_{environment}`. Một seller → một kiện: cộng khối lượng, cộng chiều cao các món xếp chồng, giữ chiều dài/rộng theo cấu hình. Đây là ước tính được gắn `estimated=true`, chưa phải số đo riêng từng bài đăng. Chặn kiện từ 20kg, chiều vượt 200cm và giá trị khai báo trên 5 triệu; chưa hỗ trợ nhiều kiện/service type 5. Thiếu cấu hình sẽ báo lỗi, không dùng phí hoặc kích thước mặc định ngầm.

## 5. Báo giá và đặt đơn

- Server đọc posts, giá, seller, địa chỉ thuộc buyer, mapping shop của từng seller, cấu hình kiện và dịch vụ GHN khả dụng; không tin giá/Shop ID do Android gửi.
- `shippingQuotes` lưu fingerprint các đầu vào, snapshot, phí theo từng seller và hạn 5 phút. Android hiển thị tổng của từng đơn và tổng checkout.
- Đặt đơn yêu cầu `quoteId`; kiểm tra lại đầu vào và phí GHN **ngoài transaction**, sau đó kiểm tra cùng phiên bản đầu vào **trong transaction** trước khi giữ tồn kho/xóa đúng cart item. Không gọi GHN bên trong callback transaction.
- Thay địa chỉ, món, giá, cấu hình kiện, shop hoặc phí: báo giá cũ bị từ chối; buyer lấy phí mới rồi xác nhận lại. Retry idempotency trả cùng order IDs.
- FREE: requester xác nhận báo giá khi gửi. Khi giver chấp nhận, backend tính lại và chỉ giữ cùng phí/đầu vào; không tăng tiền ngầm. Hạn 5 phút chỉ được bỏ qua cho **yêu cầu FREE đã gửi trước đó**, vẫn recheck phí và fingerprint. Nếu đổi phí/địa chỉ, requester bấm **Cập nhật địa chỉ / xác nhận lại phí GHN** rồi gửi cập nhật yêu cầu PENDING.

## 6. Tạo, đối soát, hủy và callback

- Chỉ seller được tạo ở READY_TO_SHIP, với điều kiện thanh toán hợp lệ. Order cũ thiếu GHN snapshot bị chặn, không bịa địa chỉ/kiện.
- `ghnOperations` giữ quyền tạo nguyên tử trước khi gọi mạng; mã `client_order_code` ổn định, tối đa 43 ký tự. Hai request đồng thời không phát hai lệnh tạo.
- Trước lệnh tạo vận đơn, tính lại phí một lần nữa ngoài transaction. Nếu khác phí đã xác nhận, chặn để đối soát/người mua xác nhận lại; không tự tăng tiền hoặc gửi đơn. Đơn PAID còn phải có bản ghi thanh toán PAID và ledger HOLDING, không chỉ cờ trạng thái trên order.
- Timeout/5xx/mất response: trạng thái UNKNOWN; request tiếp theo **chỉ tra cứu** `detail-by-client-code`. Không tự retry tạo. Lỗi chắc chắn 4xx có thể retry có chủ đích với cùng mã tham chiếu.
- GHN tạo xong nhưng Firestore chưa lưu được: operation đã tồn tại trước request; `refreshOrder` tra cứu và khôi phục liên kết. Callback đến sớm dùng ClientOrderCode để tìm operation đã có.
- Callback không tin `Status`, `Time`, `OrderID` của người gửi. Chỉ vận đơn có operation/backend mapping hợp lệ được xử lý; backend đọc chi tiết GHN bằng Secret, đối chiếu mã tham chiếu, Shop ID, môi trường, COD và bên trả phí. Không tự bịa chữ ký HMAC/Bearer cho GHN.
- Dedupe theo trạng thái/thời điểm **GHN trả qua API**; chặn snapshot cũ, cùng timestamp nhưng trạng thái mâu thuẫn, và chuyển ngược trạng thái kết thúc. `deliveredAt/inspectionDeadline` chỉ được ghi lần đầu, không kéo dài bởi callback trùng. Timeline và thông báo ghi cùng transaction với trạng thái.
- Hủy vận đơn chỉ seller trước khi lấy hàng, vẫn phụ thuộc GHN chấp nhận. Chỉ ghi CANCELLED sau khi tra cứu xác nhận. Hủy **vận đơn** không tự giả ghi hủy/hoàn tiền **đơn mua**. Luồng hủy đơn cũ chỉ cho phép trước lúc chuẩn bị nên không hủy đơn đang có vận đơn một cách ngầm định.
- Giao thất bại tạm thời không phải hoàn về. Hoàn/hủy/lost/damage/exception/scrap đặt `shippingNeedsReview`; không tự hoàn tiền thật, giải phóng tồn kho hoặc kết thúc tranh chấp. Cần quản trị viên đối soát khi có sự cố; escrow chỉ đủ điều kiện khi shipment DELIVERED và các điều kiện thanh toán/tranh chấp cũ đều đạt.
- Webhook nội bộ cũ và thao tác mô phỏng bị chặn với order GHN. `refreshOrder` vẫn chạy kiểm tra thời hạn/escrow hiện có sau khi đồng bộ GHN.

### Khai báo callback trong GHN

Sau deploy, đăng nhập Developer Portal **đúng môi trường** → menu tài khoản → **Cấu hình webhook** → **Order** → URL `ghnCallback` → tạo/lưu webhook. Bật sự kiện trạng thái và phí cần theo dõi; có thể đặt timeout 30 giây để đủ thời gian tra cứu GHN và retry 3 lần trong Portal. Staging/production cấu hình riêng, không tự chuyển. Nếu tài khoản còn dùng cổng GHN cũ, cung cấp URL, ClientID và môi trường cho hỗ trợ GHN theo tài liệu của tài khoản đó. Không điền URL trang giới thiệu Hosting làm callback vận chuyển.

## 7. Thử một đơn staging

1. Nhập Token staging vào Secret, điền `.env.fashion2hand`, deploy đúng danh sách hàm.
2. Kiểm tra shop bằng `ghnAdminShops`; chọn mã GHN cho địa chỉ buyer/seller; cấu hình seller/shop và kiện ước tính bằng Admin.
3. Buyer chọn một món FREE để thử trên cloud (không phụ thuộc thanh toán tiền hàng) hoặc PAID trên Emulator có mô phỏng thanh toán. Checkout phải ghi GHN staging và phí thật **của API staging**; kiểm tra tổng rồi gửi/đặt đơn.
4. Giver chấp nhận FREE hoặc seller xác nhận PAID đã thanh toán → chuẩn bị → sẵn sàng giao → **Tạo / đối soát vận đơn GHN**.
5. Đối chiếu mã vận đơn trong tài khoản GHN staging. Nhấn làm mới; kiểm tra providerStatus và ETA nếu GHN trả. Không giả lập tọa độ tài xế.
6. Dùng cơ chế kiểm thử của GHN staging/hỗ trợ GHN để đổi trạng thái; kiểm tra callback hoặc bấm làm mới. Không tự sửa Firestore để giả chứng minh API GHN đã giao hàng.
7. Thử yêu cầu hủy trước khi GHN lấy hàng, và retry làm mới khi timeout. Đơn production không nằm trong các bước kiểm thử này.

## 8. Kiểm thử và giới hạn

Test provider dùng fetch giả lập; test nghiệp vụ dùng Firestore Emulator và provider giả lập. **Không có Token thật trong test**. Chạy:

```powershell
node --test functions/test/ghn-provider.test.js
# Test script Admin bằng HTTP/credential giả, không đổi tài khoản thật:
powershell.exe -NoProfile -File functions/test/ghn-admin-script.test.ps1
# Bật Firestore Emulator ở 127.0.0.1:8080 trước:
node --test functions/test/ghn.emulator.test.js
.\scripts\build-debug.ps1 -RunUnitTests
```

Test Emulator dùng Admin SDK trực tiếp để kiểm tra transaction, không phải bằng chứng đã gọi GHN staging hoặc hoàn thành nghiệm thu UI thiết bị. Chưa kiểm tra API GHN thật, callback từ tài khoản thật, quyền tài khoản GHN, phí hợp đồng hoặc đơn production khi thiếu credential. Kết quả chạy cụ thể được ghi khi bàn giao.

Kết quả thực tế ngày 26/10/2026: **23/23 backend test đạt** (9 provider + 14 nghiệp vụ trên Firestore Emulator). Đã kiểm tra metadata Firebase SDK xác nhận `GHN_TOKEN` được bind vào các hàm dùng GHN, cú pháp script PowerShell hợp lệ. Node dùng để chạy test cục bộ là **v24.21.0**, runtime deploy của project vẫn **nodejs22**. Android build + 11 unit test đã đạt; không chạy instrumentation GHN trên thiết bị và không gọi GHN thật trong lượt kiểm tra này.

## Tài liệu chính thức đã đối chiếu

- [GHN tạo đơn, địa chỉ, đơn vị, bên trả phí và COD](https://developer.ghn.vn/vi/docs/order/create)
- [Tính phí](https://developer.ghn.vn/vi/docs/order/calculate-fee), [dịch vụ tuyến](https://developer.ghn.vn/vi/docs/master-data/get-service)
- [Tra cứu theo mã nội bộ](https://developer.ghn.vn/vi/docs/order/info-by-client-code), [chi tiết](https://developer.ghn.vn/vi/docs/order/info), [hủy](https://developer.ghn.vn/vi/docs/order/cancel)
- [Shop](https://developer.ghn.vn/vi/docs/shop/get-shop), [trạng thái](https://developer.ghn.vn/vi/docs/master-data/order-status), [callback](https://developer.ghn.vn/vi/docs/webhook/callback-order-status)
- [Firebase Secret parameters và binding](https://firebase.google.com/docs/functions/config-env)


## Sửa cấu hình danh mục GHN — 27/10/2026

Log Cloud xác nhận GHN HTTP 400 tại /master-data/province khi hai Function địa chỉ còn dùng GHN_SHOP_ID=2543108. Đã đồng bộ Shop ID 232736 theo ảnh người dùng vào .env.fashion2hand và file .gradle/ghn-seller.json; deploy riêng ghnAddressCatalog và saveGhnAddress thành công, giữ GHN_ENV/GHN_TOKEN_ENV=staging và Secret GHN_TOKEN version 5. Chưa xác minh thành công phản hồi danh mục GHN; cần đối chiếu shop thuộc đúng staging (không suy luận từ ảnh không có tên miền). Không đổi Token hoặc chuyển production.

Người dùng đã xác nhận shop 232736 thuộc 5sao.ghn.dev / developer.ghn.dev (staging). Metadata sau deploy: cả hai Function ACTIVE, ShopId=232736, environment=staging, GHN_TOKEN version 5. Còn cần thử lại bộ chọn trên app để xác minh phản hồi GHN.

UI lưu địa chỉ GHN: sau khi callable lưu thành công, app thông báo rõ và tải lại màn hiện tại. Luồng seller ghi rõ điểm lấy hàng của người bán; luồng buyer ghi địa chỉ nhận hàng. Trung tâm giao dịch phía bán hiển thị các địa chỉ đã lưu mã GHN, không suy luận rằng shop đã được Admin liên kết. Danh sách địa chỉ có nhãn đã lưu/chưa có mã GHN.
