# Triển khai Giao dịch & Vận chuyển — MINH-ANH

**Bổ sung ngày 26/10/2026:** đã viết tích hợp GHN backend/Android trong mã nguồn; xem [GHN_INTEGRATION.md](GHN_INTEGRATION.md). 23/23 test backend mock HTTP + Firestore Emulator đạt. Chưa deploy các hàm GHN hoặc gọi GHN thật vì người dùng sẽ tự nhập Secret. Nội dung nghiệm thu ngày 24/10 bên dưới là mốc lịch sử trước GHN.

Ngày: 24/10/2026. Nhánh làm việc: `MINH-ANH`. Chưa commit hoặc push. Đã deploy cloud theo yêu cầu bổ sung; xem [CLOUD_DEPLOYMENT.md](CLOUD_DEPLOYMENT.md). Không tạo project Firebase, bật billing hoặc thực hiện thanh toán thật.

## 1. Kết quả và giới hạn nghiệm thu

Đã bổ sung code chạy được và kiểm thử trên Firebase Emulator + Android AVD. **Đã deploy Firebase cloud ngày 24/10/2026**: 39/39 Functions ACTIVE, rules/indexes đã phát hành, 7 kiểm tra quyền cloud đạt. Chưa nghiệm thu toàn bộ luồng Android bằng hai tài khoản thực trên cloud. Các hạn chế kiến trúc/giao diện bên dưới vẫn còn, không coi toàn bộ prompt đã hoàn tất.

- `app/google-services.json` có project `fashion2hand`, package Android khớp `vn.edu.ueh.thudinh.fashion2hand`; Gradle plugin, Auth/Firestore/Functions/Storage đã có. Không tạo cấu hình giả.
- Phát hiện ứng dụng luôn gọi `useEmulator`; đã sửa để cloud là mặc định, Emulator chỉ bật trong debug bằng property rõ ràng.
- Đã xác minh cloud database là Standard edition sau khi đăng nhập lại. Firestore Emulator thực tế khởi động ở Standard edition. Không provision/migration database cloud.
- Có sẵn backend transaction, rules, indexes và tích hợp `TransactionScreens`; tài liệu cũ nói chưa nối UI đã lỗi thời và được cập nhật.
- Có sẵn `Post`, `User`, `Order`, `Transaction`, `MemberOneRepository`, `AuthRepository`; dùng lại, không viết lại đăng nhập/sản phẩm/Admin.
- Các nguồn đang dùng: `chats/{id}/messages`, `freeRequests`, `orders`, `payments`, `moneyTransactions`, `shipments`, `disputes`, `users/{uid}/cartItems`, `users/{uid}/notifications`, `users/{uid}/addresses`. Không tạo nguồn dữ liệu song song.

## 2. Theo từng phase

| Phase | File mới | File sửa / tái sử dụng | Kết quả và bằng chứng |
|---|---|---|---|
| 1 — Audit & Firebase | Báo cáo này | `app/build.gradle.kts`, `Fashion2handApplication.java`, `build-debug.ps1`, `start-emulators.ps1`, `.gitignore` | Cấu hình cloud/Emulator tách biệt; build cả hai chế độ. SDK 34 không đáp ứng dependency; nâng compileSdk lên 36 đã cài, giữ minSdk/targetSdk. |
| 2 — Data layer | `model/Message.java`, `repository/ChatRepository.java`, `functions/cart-checkout.js`, `functions/order-items.js` | `MemberTwoRepository.java`, `functions/index.js` | Cursor chat 40 tin/trang; quote và checkout 1–20 món từ server; group theo seller; giữ tồn kho, payment, thông báo và xóa đúng cart item nguyên tử; idempotency cho tin nhắn/lượt checkout. |
| 3 — UI integration | `viewmodel/ChatViewModel.java`, `adapter/MessageAdapter.java` | `MainActivity.java`, `TransactionScreens.java`, `activity_chat.xml` | Chat dùng MVVM/LiveData/RecyclerView và layout bong bóng sẵn có; giữ header/thẻ sản phẩm/thanh nhập chat. Giỏ chọn nhiều món, tổng giá realtime; checkout liệt kê từng seller/món/phí; đơn hiển thị đủ items; giữ ID/checkout key khi recreate Activity. |
| 4 — Business logic | `functions/escrow.js`, bốn class `service/PaymentService`, `MockPaymentService`, `ShippingService`, `MockShippingService` | `functions/index.js`, `functions/admin.js`, `TransactionScreens.java` | Hủy/từ chối/thanh toán thất bại/hết hạn/hoàn hàng/Admin xử lý tất cả items. Giải ngân mô phỏng kiểm tra payment, ledger, bằng chứng shipment, tranh chấp; hạn kiểm tra 3 ngày; scheduler và refreshOrder dùng chung logic. |
| 5 — Security & test | `ChatPaginationEmulatorTest.java`, `EmulatorTestRunner.java`, `functions/test/chat.emulator.test.js`, `shipment-webhook.emulator.test.js` | `TransactionFlowTest.java`, `transactions.emulator.test.js`, `firestore.indexes.json`, `functions/package.json` | Test chặn đọc/gửi chat trái quyền, client giả ghi message, inventory race, idempotency, payout/dispute, webhook replay. Runner từ chối instrumentation khi APK không bật Emulator. Rules hiện có được giữ và kiểm tra qua request có token thật của Auth Emulator. |
| 6 — External API preparation | Interfaces/adapters mô phỏng ở Phase 4 | `BACKEND_MEMBER_2.md`, `FIRESTORE_DATA_MODEL_VI.md` | Các adapter đang gọi trusted backend, không giữ merchant/server secret trong Android. Chưa nối VNPAY/GHN/GHTK/Google Maps/FCM. Không gắn nhãn API thật cho mock. |

Các đường dẫn Java ở bảng trên nằm dưới `app/src/main/java/vn/edu/ueh/thudinh/fashion2hand/`; Android tests nằm dưới package tương ứng trong `app/src/androidTest/java/`.

## 3. Quyết định schema được chủ dự án cho phép

Người dùng đã chọn **cho phép mở rộng đơn nhiều món** trong phiên làm việc.

- Đơn cũ không đổi: thiếu `items` thì backend dùng `postId`.
- Đơn mới: `items` là snapshot, `quantity=1`, tối đa 20 món/lượt; mỗi order chỉ có một seller. Giỏ nhiều seller tạo nhiều order/payment/shipment riêng.
- `price` là tổng giá món, `shippingFee` tính một lần mỗi seller. Bổ sung `subtotal`, `totalAmount`, `itemCount`, `schemaVersion=2`.
- `postId` vẫn giữ món đầu tiên, `postSnapshot.title` có nhãn tổng hợp để danh sách đơn cũ/Admin tiếp tục hoạt động.
- `orderRequests/cart_{hash}` lưu toàn bộ kết quả để retry không tạo đơn trùng. Giá, trạng thái và địa chỉ đều đọc server. Báo giá thay đổi sẽ yêu cầu tải lại.
- Mỗi `posts/{id}` đều có `reservedForOrderId` của đúng order; helper chung cập nhật tất cả món khi hoàn/hủy.
- Sửa Admin tối thiểu ở xử lý tranh chấp để cập nhật mọi món; giao diện Admin đã có renderer map/list nên không cần viết lại.

## 4. Kiểm thử thực tế

| Bộ kiểm thử | Kết quả |
|---|---|
| Build debug + unit test | Build thành công; 5/5 unit test qua. Có build chế độ Emulator và build mặc định cloud. |
| Backend tổng hợp | 14/14 qua: 3 chat, 10 giao dịch gồm multi-item/race/lifecycle, 1 webhook. |
| Backend bổ sung Admin multi-item | 1/1 qua: REFUND/RELEASE áp dụng tất cả món, retry không ghi thêm giao dịch. |
| Android cuối — ChatPaginationEmulatorTest, TransactionFlowTest, MarketplaceFlowTest | 7/7 qua trên Pixel_7 API 37; build thành công. |
| Android toàn bộ ở lượt đầu | 14/20 qua, 6 lỗi; không coi toàn bộ suite đạt. |

Bài Android mới xác minh: 40→80→85 tin lịch sử có timestamp trùng, nhận tin mới thành 86, cache khi mất mạng, tái gắn listener; giỏ hai món → chọn → checkout → recreate → một đơn hai món → thanh toán mô phỏng → hủy → cả hai món khả dụng trở lại.

Bộ backend kiểm tra thêm: hai buyers tranh một món không tạo đơn dở dang; group theo seller; snapshot và tổng tiền từ server; cùng key trả đúng các order cũ; giữ món chưa chọn trong giỏ; fail/cancel/reject/return/expire mở lại mọi món; payout dùng tổng subtotal; dispute thật chặn payout kể cả khi cờ trên order thiếu.

6 lỗi ở lượt toàn bộ: AccountScreensEmulatorTest timeout trạng thái UI; AdminFlowTest thiếu fixture admin và login không hoàn tất; NotificationInboxEmulatorTest lỗi window focus; hai ResponsiveFlowTest thiếu fixture admin. Không sửa các module ngoài phạm vi để làm xanh báo cáo. Một lượt backend giữa lúc Functions reload gặp ECONNRESET; chạy lại nguyên assertion khi source ổn định đã đạt.

Lưu ý: kiểm thử Emulator không chứng minh các service đã deploy/cấu hình trên cloud. Scheduler không tự chạy trong Emulator; đã kiểm tra cùng logic qua `refreshOrder`. Webhook là hợp đồng server tổng quát đã test bằng secret riêng của fixture; chưa phải chữ ký callback VNPAY/GHN đã được nhà cung cấp xác nhận.

## 5. Bảng nghiệm thu theo màn hình

“Emulator” ở cột Firestore nghĩa là dùng SDK/Firestore thật chạy cục bộ, **không phải đã nghiệm thu Firebase cloud**. “Kế thừa” nghĩa là tái sử dụng code có sẵn, không phải class mới viết trong lượt này.

| Màn hình | Model | Repository | ViewModel | Adapter | Firestore | Hoạt động / giới hạn |
|---|---|---|---|---|---|---|
| Danh sách chat | DocumentSnapshot kế thừa | MemberTwoRepository | Chưa tách riêng | Renderer MainActivity | Emulator | Listener/dữ liệu cuối/bộ lọc kế thừa; backend quyền đã test; chưa có ChatAdapter riêng. |
| Chi tiết chat | Message | ChatRepository + MemberTwoRepository | ChatViewModel | MessageAdapter | Emulator | Đã test realtime, lịch sử/cursor, cache, read status, retry, chống gửi trùng; chưa gửi ảnh. |
| Yêu cầu nhận miễn phí | Snapshot kế thừa | MemberTwoRepository | Chưa tách riêng | TransactionScreens renderer | Emulator | Gửi/hủy/chấp nhận/từ chối, cạnh tranh một món, tạo đơn FREE đã test. |
| Giỏ hàng | Post/cart snapshot | MemberTwoRepository | Chưa tách riêng | TransactionScreens renderer | Emulator | Thêm/xóa, giá/tình trạng realtime, tổng tiền, chọn nhiều món; test lưu/checkout/recreate. |
| Checkout | Quote/items/order snapshot | MemberTwoRepository + Functions | Chưa tách riêng | TransactionScreens renderer | Emulator | Group theo seller, server pricing/address, reserve nguyên tử, idempotency; 7 bài Android cuối có luồng mới. |
| Trung tâm/danh sách đơn | Order/snapshot kế thừa | MemberTwoRepository | Chưa tách riêng | MainActivity/TransactionScreens | Emulator | Hai vai và tiêu đề group tương thích; backend/Android giao dịch đã test. |
| Chi tiết đơn | Order/items snapshot | MemberTwoRepository | Chưa tách riêng | TransactionScreens renderer | Emulator | Hiển thị mọi món, timeline, payment, các quyền thao tác; thông tin 3 ngày kiểm tra. |
| Thanh toán/lịch sử | Transaction/payment/ledger snapshot | MemberTwoRepository + PaymentService | Chưa tách riêng | TransactionScreens renderer | Emulator | Thanh toán/giữ/hoàn/giải ngân mô phỏng đã test; không chuyển tiền thật. |
| Vận đơn/timeline | Shipment/events snapshot | MemberTwoRepository + ShippingService | Chưa tách riêng | TransactionScreens renderer | Emulator | Dữ liệu hãng mock, timeline realtime; webhook có eventId và chặn chuyển ngược. |
| Bản đồ vận chuyển | Shipment.location kế thừa | MemberTwoRepository | Chưa tách riêng | View/controller kế thừa | Emulator | Không có vị trí thì thông báo; tọa độ demo do người dùng nhập được gắn nhãn; chưa nhúng Google Maps/GPS thật. |
| Thông báo | Inbox snapshot kế thừa | MemberTwoRepository/NotificationInbox | Chưa tách riêng | NotificationInbox renderer | Emulator | Lưu lịch sử đúng UID; message/group-order/settlement ghi nguyên tử; chưa có push khi đóng app. |

## 6. Những yêu cầu còn chưa hoàn tất

- Đã deploy và kiểm tra quyền trên cloud; vẫn chưa xác minh E2E Android bằng hai tài khoản thực trên cloud.
- MVVM đầy đủ mới áp dụng cho chi tiết chat; các màn còn lại vẫn dùng controller/renderer hiện có và snapshot maps, chưa có đủ typed model/ViewModel/RecyclerView adapter riêng như yêu cầu toàn bộ prompt.
- `TransactionScreens.prepare()` vốn dựng giao diện động thay nhiều XML nghiệp vụ có sẵn. Lượt này giữ/hoàn thiện layout chat và mở rộng controller giao dịch hiện có; chưa chuyển toàn bộ màn giao dịch về bind nguyên các XML thiết kế ban đầu. Không coi điều kiện giữ nguyên tất cả giao diện đã đạt.
- Chưa có gửi ảnh chat, thumbnails đầy đủ trong giỏ, FCM push, VNPAY Sandbox, GHN/GHTK và Google Maps SDK.
- Mô phỏng trạng thái giao vẫn là công cụ seller trên **Emulator có cờ cho phép**. Ngoài Emulator backend chặn công cụ này; chưa phải phân hệ carrier/operator độc lập cho demo.
- Phí ship kế thừa cấu hình `shippingTariffs/active.minFee`. Chưa áp dụng khoảng cách đường chim bay vì chưa có luồng tọa độ gửi/nhận được xác thực; không bịa tọa độ.
- Chưa chạy lại toàn bộ suite sau khi chuẩn bị các fixture Admin; không đánh dấu các lỗi toàn bộ-suite đã sửa.

## 7. Chạy và cấu hình

Cloud (không bật Emulator):

```powershell
.\build-debug.ps1 -RunUnitTests
```

Emulator (AVD dùng host mặc định `10.0.2.2`; máy thật có thể truyền Gradle property `firebaseEmulatorHost`):

```powershell
.\start-emulators.ps1
.\build-debug.ps1 -UseFirebaseEmulators
.\build-debug.ps1 -RunDeviceTests -TestClass 'vn.edu.ueh.thudinh.fashion2hand.ChatPaginationEmulatorTest,vn.edu.ueh.thudinh.fashion2hand.TransactionFlowTest,vn.edu.ueh.thudinh.fashion2hand.MarketplaceFlowTest'
node --test functions/test/chat.emulator.test.js functions/test/transactions.emulator.test.js functions/test/shipment-webhook.emulator.test.js
```

Script Emulator bật cờ mock rõ ràng, import `emulator-data` nếu có và export khi dừng bình thường. Dữ liệu export không đưa vào Git. Runner bảo vệ không cho chạy instrumentation trên APK cloud.

Các mục dưới đây là checklist cấu hình; phiên CLI và việc deploy ở mục 1–3 đã được thực hiện trong yêu cầu bổ sung, xem `CLOUD_DEPLOYMENT.md`:

1. `firebase login --reauth`, xác minh quyền project `fashion2hand` và Firestore `(default)`.
2. Xác minh nhà cung cấp Authentication đang bật đúng luồng app dùng; có database/Storage phù hợp. Không cần tạo collection rỗng.
3. Review rồi deploy source Functions hiện tại và rules/indexes: `firebase deploy --only functions,firestore:rules,firestore:indexes,storage`. Đặc biệt cần index `orderStatus + inspectionDeadline`. Nếu Firebase yêu cầu billing, cần chủ dự án quyết định; agent chưa bật.
4. Cấu hình `PAYMENT_WEBHOOK_TOKEN`, `SHIPMENT_WEBHOOK_TOKEN` qua Secret Manager; không đặt trong APK. Carrier callback nay bắt buộc `eventId` duy nhất. Các secret test chỉ ở process kiểm thử.
5. Thiết lập tariff bằng tài khoản server/Admin. Bổ sung merchant/API credentials của nhà cung cấp để triển khai adapter thật, không dùng webhook tổng quát như tích hợp đã được chứng nhận.
6. Bổ sung cấu hình FCM và Google Maps khi triển khai các chức năng còn thiếu.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Báo cáo Android: `app/build/reports/androidTests/connected/debug/index.html`; unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`. Thay đổi catalog `gradle/libs.versions.toml` xuất hiện đồng thời trong workspace được giữ nguyên, không nhận là code agent tạo.
