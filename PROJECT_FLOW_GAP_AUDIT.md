# Rà soát luồng toàn project — 24/10/2026

## Phạm vi và cách đọc

Đối chiếu các yêu cầu nhìn được trong ảnh với 49 màn Main, 6 màn Admin, controller runtime, repository, Functions, Firestore/Storage Rules và các ca kiểm thử hiện có. Phần cuối ảnh bị cắt không được suy đoán thêm.

Đây là rà soát source và bằng chứng kiểm thử đã lưu, không phải một lượt chạy lại toàn bộ E2E. Lệnh `node scripts/audit-ui-backend.cjs` chạy lại và PASS: 49 + 6 routes, 81 XML. Kết quả đó xác nhận inventory/kết nối source, không chứng minh mọi nhánh nghiệp vụ chạy đúng. Không dùng phần trăm hoàn thành vì chưa có danh sách acceptance test đầy đủ.

Phạm vi người dùng đã chốt: Firebase Emulator, thanh toán và vận chuyển mô phỏng. Chưa có API nhà cung cấp không đồng nghĩa chưa có backend.

## Đối chiếu yêu cầu trong ảnh

| Nhóm | Hiện có | Phần còn thiếu / giới hạn |
|---|---|---|
| Auth, Users, Storage | Đăng ký/đăng nhập thật; hồ sơ/avatar; OTP và đổi email qua Auth; liên hệ đồng bộ server | Cấu hình app đang cố định Emulator; chưa xác nhận dịch vụ production |
| Posts, Favorites, CRUD | Đăng/sửa/xem tin, ảnh Storage, yêu thích Firestore, ẩn/mở tin; xuất bản ngay đúng yêu cầu đã chốt | Nút xóa thực tế ẩn tin; Rules không cho xóa |
| FREE/SALE, giá, trạng thái, hàng một-một | Giá FREE = 0/SALE > 0; backend giữ hàng, ngăn nhận trùng; bắt buộc email và SĐT đã xác minh | Chưa áp dụng giá trần; chủ bài còn sửa được nội dung khi RESERVED nếu giữ nguyên status |
| Chats, Messages | Tạo hội thoại, gửi chữ, đọc tin, unread, ngữ cảnh mua/bán từ backend | Chưa gửi ảnh/tệp; chưa có push khi app đóng; chưa có idempotency server cho gửi tin |
| FreeRequests | Gửi/hiển thị danh sách, chọn người nhận, từ chối/hủy; chấp nhận tạo đơn nguyên tử | Chấp nhận chỉ cập nhật tối đa 450 yêu cầu đang chờ khác: cần xử lý phần vượt giới hạn |
| Orders, Payments, Shipments | Đơn một món; thanh toán thành công/thất bại mô phỏng; giữ/hoàn/giải ngân; vận đơn, trả hàng, timeline thật | Chưa gom nhiều món thành một checkout/đơn; chưa có nhà cung cấp thật |
| Tracking, bản đồ | Lịch sử shipment, tọa độ backend, người bán nhập tọa độ mô phỏng; mở ứng dụng bản đồ/web theo tọa độ | Chưa có GPS/realtime từ hãng hoặc bản đồ tương tác tích hợp trong app |
| Notifications | Inbox realtime, lọc, đánh dấu đọc và đọc tất cả lưu backend | Chưa có FCM/background push; cần kiểm tra đích mở cho từng loại sự kiện mới |
| Reports, Disputes | Support ticket → Admin; tranh chấp đơn và ảnh riêng tư; quyết định hoàn/giải ngân | Chưa có report bài/người dùng từ màn sản phẩm/hồ sơ; chưa có vòng bổ sung bằng chứng |
| Verification, Admin | Gửi ba ảnh; Admin duyệt/từ chối; role/claim/active guard; dữ liệu thật, audit log, lưu cấu hình | Rules tạo verification/report còn cho đi tắt validation; danh mục và nhiều chính sách chưa đi tới app người dùng |
| Navigation, responsive, QA | Có backstack/lưu nháp và kiểm thử layout/chọn lọc luồng đã ghi nhận | Chưa chứng minh mọi luồng, mọi nhánh lỗi và mọi form qua xoay thật/process death; inflate XML không đủ để kết luận hoàn tất |

## Các việc cần làm, theo thứ tự

### P1 — Hoàn thiện nghiệp vụ và quyền ghi trước khi nghiệm thu

**Cập nhật yêu cầu 24/10:** người dùng đã chốt không cần Admin duyệt bài. Điều kiện thay thế là Firebase Auth xác minh đủ email + SĐT cho đăng bài/mua/xin đồ. Mục tự xuất bản dưới đây vì vậy không còn là thiếu sót của yêu cầu; quy tắc mới được ghi ở CONTACT_VERIFICATION_POLICY.md. Các phát hiện khác vẫn cần xử lý riêng.

- [x] **Chốt xuất bản ngay, không chờ Admin.** Người dùng đã xác nhận chính sách này. Chỉ tài khoản xác minh đủ email + SĐT được ghi bài/ảnh; không tính chuyển DRAFT/PENDING_REVIEW → ACTIVE là vượt kiểm duyệt. Cờ khóa sau khi Admin ẩn vẫn được giữ.
- [ ] **Chặn tạo hồ sơ xác minh/báo cáo sai schema qua Firestore.** `firestore.rules:143–145` chỉ kiểm tra reporterId/userId khi create. Client có thể bỏ qua validation, ảnh bắt buộc và trạng thái đầu vào của `functions/account.js:77`. Admin chỉ được tin hồ sơ được server tạo/kiểm tra; không kết luận người dùng tự cấp được role Admin, vì quyền role đã được chặn riêng.
- [ ] **Nối yêu cầu bổ sung bằng chứng hai chiều.** `functions/admin.js:140` ghi WAITING_EVIDENCE; `functions/index.js:118` chỉ tạo tranh chấp lần đầu; `storage.rules` khóa upload khi order có disputeId. Cần UI đọc yêu cầu → tải ảnh mới → API bổ sung → Admin đọc lại → quyết định. Hiện Admin có nút yêu cầu nhưng người dùng không hoàn thành bước tiếp theo được.
- [ ] **Có luồng báo cáo bài đăng/người dùng.** Hiện màn report là tranh chấp đơn (`MainActivity.java:1696`), còn support tạo reports loại SUPPORT (`functions/account.js:125`). Chưa thấy nút/form và backend validation đầy đủ cho report từ sản phẩm/hồ sơ → theo dõi → phản hồi. Admin xử lý report có sẵn không chứng minh người dùng tạo được report đúng luồng.
- [ ] **Đồng bộ danh mục Admin với đăng bài/khám phá.** Admin ghi categories; `MainActivity.java:1999,2088` vẫn dùng danh mục cố định. Thêm/tắt danh mục chưa tác động tới lựa chọn người dùng. Cần một nguồn danh mục và validation tương ứng.
- [ ] **Áp dụng các cấu hình tự động còn lại.** maxPrice, autoHideReported, reportThreshold, autoCompleteDays chưa có logic áp dụng đầy đủ. Phí ship đã nối báo giá. Email + SĐT nay bắt buộc, không thể tắt bằng cấu hình Admin.
- [ ] **Khóa sửa dữ liệu món hàng khi đang giữ/đã giao dịch.** Rules update posts cho giữ nguyên status đồng thời sửa title/price/type/ảnh; Storage ảnh cũng chỉ kiểm tra chủ bài. Order có snapshot nên không mặc nhiên đổi tiền đơn cũ, nhưng dữ liệu bài và món giao dịch có thể lệch. Cần giới hạn trường được sửa theo trạng thái và bảo vệ ảnh đã dùng trong giao dịch.

### P2 — Hoàn thiện trải nghiệm và quy mô dữ liệu

- [ ] **Tìm kiếm và phân trang toàn bộ sản phẩm.** `MemberTwoRepository.java:152` chỉ tải 50 bài mới nhất; bộ lọc ở MainActivity chạy trên dữ liệu đã tải. Tin cũ hơn có thể không xuất hiện khi tìm. Yêu thích đã có đường đọc riêng, không suy ra tìm kiếm toàn kho đã có.
- [ ] **Giải quyết rõ yêu cầu checkout nhiều món.** Hiện cart nhiều món nhưng mỗi lần checkout một món/đơn. Nếu nghiệm thu yêu cầu thanh toán chung nhiều món, cần báo giá/tách người bán/giữ hàng/hoàn tiền tương ứng; nếu chấp nhận một món thì ghi rõ tiêu chí.
- [ ] **Chốt xóa mềm hay xóa thật.** `MainActivity.java:1278` thực hiện HIDDEN; `firestore.rules` deny delete. Không ghi hoàn tất CRUD-delete nếu tiêu chí bắt buộc xóa vĩnh viễn; cân nhắc giữ lịch sử đơn và bằng chứng.
- [ ] **Lưu/khôi phục dữ liệu Emulator.** `start-emulators.ps1` hiện không truyền import/export. Khởi động lại dữ liệu trắng có thể mất tài khoản Admin, đơn và tin kiểm thử. Cần thư mục snapshot và quy trình import/export có chủ đích.
- [ ] **Kiểm thử tự hết hạn khi không mở app.** Có scheduled function hết hạn thanh toán/chờ seller và refreshOrder khi mở đơn (`functions/index.js:91,833`). Chưa được coi refresh khi mở màn là tác vụ chạy nền liên tục trong Emulator; cần runner/ca kiểm thử chủ động cho môi trường local.
- [ ] **Xử lý yêu cầu FREE vượt 450.** `functions/index.js:399` giới hạn truy vấn cập nhật UNAVAILABLE. Giữ hàng nguyên tử vẫn ngăn nhận trùng, nhưng phần dư có thể còn hiển thị PENDING. Cần batch/job tiếp nối hoặc ràng buộc số lượng rõ ràng.
- [ ] **Bổ sung acceptance test cho các khoảng trống trên**, gồm cố ghi Firestore trực tiếp, đăng PENDING rồi tự công khai, sửa món RESERVED, thêm/tắt danh mục, bổ sung bằng chứng, >50 bài, >450 yêu cầu, restart Emulator, lỗi mạng và gửi lặp.
- [ ] **Lập ma trận E2E từng màn/nhánh trên dọc-ngang thật.** Kiểm tra back/navigation, IME tiếng Việt, bàn phím che form, loading/rỗng/lỗi/thử lại, chống bấm lặp, xoay lúc upload/checkout/OTP và khôi phục tiến trình. Các test inflate layout hiện có không bao phủ toàn bộ hành vi này.

### Chưa có, nhưng cần chốt phạm vi trước khi tính là thiếu bắt buộc

- [ ] Form đánh giá sau giao dịch và tính điểm hồ sơ. `firestore.rules:142` reviews chỉ đọc, cấm ghi; hiện hồ sơ không dùng điểm giả. Yêu cầu trong phần ảnh nhìn được chưa nêu rõ reviews.
- [ ] Chat ảnh/tệp, trạng thái online, chặn người dùng, FCM/background push. Chat chữ và inbox realtime đã có; đây là các tính năng bổ sung nếu đề bài yêu cầu.
- [ ] Bản đồ nhúng/GPS, thanh toán/escrow/hoàn tiền/chuyển khoản thật, vận chuyển thật và xác minh ngân hàng/danh tính tự động. Ngoài phạm vi Emulator đã chốt, không cần chờ API để nghiệm thu mô phỏng.

## Hai chuỗi chính: đã có đường đi, chưa đồng nghĩa mọi nhánh đã nghiệm thu

**FREE:** đăng FREE → gửi yêu cầu kèm địa chỉ → chủ bài chọn một người → tạo đơn CONFIRMED/giữ món → chuẩn bị → sẵn sàng giao → tạo vận đơn mô phỏng → cập nhật giao hàng → người nhận xác nhận → COMPLETED và bài GIVEN. Source phía UI: TransactionScreens; server: createFreeRequest/respondFreeRequest/createShipment/simulateShipmentUpdate/updateOrderStatus. Có test backend chọn nhận đồng thời và các test giao dịch; cần thêm một lượt UI xuyên suốt riêng toàn chuỗi FREE để đóng acceptance.

**SALE:** đăng SALE → sản phẩm → cart → checkout một món → PENDING_PAYMENT → mô phỏng trả tiền → giữ tiền/CONFIRMED → seller chuẩn bị → giao hàng mô phỏng/tracking → DELIVERED → buyer xác nhận → COMPLETED và bút toán payout mô phỏng. Có test backend lifecycle hai vai, các nhánh fail/reject/cancel/return và test UI chọn lọc đã lưu. Không coi đây là chuyển tiền ngân hàng thật, checkout gộp nhiều món hoặc bằng chứng mọi màn đã chạy lại hôm nay.

**Ngoại lệ đang đứt rõ nhất:** mở tranh chấp → Admin yêu cầu thêm bằng chứng → người dùng không có đường bổ sung. **Quản trị chưa xuyên suốt:** đổi danh mục/cấu hình → hành vi phía người dùng chưa đổi đầy đủ.

## Đánh giá Rules theo skill auditor (phân tích source, chưa chạy exploit mới)

```json
{
  "score": 2,
  "summary": "Quyền Admin và giao dịch server-owned đã có guard, nhưng còn đường bỏ qua validation tạo hồ sơ và chính sách kiểm duyệt chưa cưỡng chế.",
  "findings": [
    {"check":"Type Safety / Authority Source","severity":"major","issue":"Create reports và verificationRequests chỉ kiểm tra UID, không khóa schema, trạng thái hoặc bằng chứng theo API tin cậy.","recommendation":"Chỉ cho server tạo hoặc validation đầy đủ với trường quản trị server-owned."},
    {"check":"The Update Bypass","severity":"major","issue":"Nội dung/ảnh bài RESERVED vẫn có thể bị chủ sửa trong khi đơn đã chụp snapshot.","recommendation":"Khóa trường nghiệp vụ/ảnh theo trạng thái giao dịch; test cả Firestore và Storage."},
    {"check":"Storage Abuse / Type Safety","severity":"minor","issue":"Nhiều dữ liệu trực tiếp như address/cart/profile chưa kiểm tra đầy đủ kiểu, giới hạn độ dài và trường cho phép; upload ảnh chỉ giới hạn từng file, chưa có quota tổng.","recommendation":"Bổ sung schema, giới hạn và kiểm thử payload sai trên cả create/update."}
  ]
}
```

## Bằng chứng và giới hạn kiểm tra

- Tài liệu liên quan: UI_BACKEND_AUDIT.md, MARKETPLACE_CHECKLIST.md, TRANSACTION_CHECKLIST.md, ADMIN_CHECKLIST.md, QA_BACKEND_UI_RESULTS.md, QA_RESPONSIVE_CHECKLIST.md.
- Tests hiện có: functions/test/{marketplace,transactions,admin,account}.emulator.test.js và các Android test. Có độ phủ thực tế cho nhiều nghiệp vụ, nhưng dữ liệu fixture được seed trong test không thay thế form tạo dữ liệu của người dùng.
- Đợt rà soát này không chạy lại toàn bộ tests, không thay đổi account/quyền, không reset Emulator và không deploy production. Chỉ thêm báo cáo; các sửa Admin login đang có trong workspace được giữ nguyên.
