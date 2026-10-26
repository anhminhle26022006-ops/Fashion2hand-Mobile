# Giao dịch & Vận chuyển — Firebase Emulator

Phạm vi đã chốt: chưa có API thanh toán/vận chuyển. Dữ liệu nghiệp vụ được lưu và kiểm tra trên Firebase Emulator; tiền, vận đơn và vị trí vận chuyển được mô phỏng có nhãn rõ ràng.

## Checklist

- [x] Danh sách yêu cầu nhận đồ đầy đủ, từng dòng có ID riêng; người tặng chấp nhận/từ chối, người nhận hủy và xem đơn tương ứng.
- [x] Checkout **một món mỗi đơn**. Giỏ có nhiều món, chọn thanh toán từng món; chỉ xóa món đã tạo đơn thành công. Giá và phí giao lấy từ backend, bắt buộc địa chỉ đã lưu, chống tạo trùng và tiếp tục thanh toán đơn đang chờ.
- [x] Thanh toán mô phỏng thành công/thất bại; hiển thị trạng thái thanh toán, giữ tiền, hoàn tiền và giải ngân từ backend. Callable mô phỏng chỉ bật khi Functions chạy Emulator và có cờ cho phép.
- [x] Timeline lấy từ `orders/{id}/statusEvents`, có thời gian và lý do thực tế. Mở đơn sẽ đối soát hạn thanh toán/xác nhận bằng đồng hồ server vì Emulator không tự chạy lịch scheduler.
- [x] Vận đơn có mã, hãng, trạng thái và toàn bộ lịch sử sự kiện lưu tại backend; hỗ trợ mô phỏng giao thành công/hoàn hàng.
- [x] Lịch sử giao dịch lấy đơn mua/bán và bút toán từ Firestore, có bộ lọc và liên kết tới đơn; bỏ dữ liệu giao dịch hardcode khỏi luồng hiển thị.
- [x] Màn theo dõi chỉ mở tọa độ có trong backend. Có thể nhập tọa độ mô phỏng trên Emulator; thiếu tọa độ sẽ hiển thị trạng thái chưa có vị trí.
- [x] Báo cáo tranh chấp lưu bằng callable có kiểm tra vai trò/trạng thái; tối đa 5 ảnh JPEG, mỗi ảnh dưới 5 MB, upload Storage riêng tư. Chỉ hai bên đọc được ảnh; khóa sửa ảnh sau khi gửi; tranh chấp đang mở chặn giải ngân.
- [x] Kiểm thử quyền và vòng đời hai vai: tạo đơn, xác nhận, từ chối, hủy, chuẩn bị, giao hàng, nhận hàng, hoàn hàng/hoàn tiền; kiểm tra yêu cầu cạnh tranh và gửi lặp.

## Chạy và kiểm thử

Terminal thứ nhất:

```powershell
.\start-emulators.ps1
```

Mở Android Emulator, sau đó ở terminal khác:

```powershell
.\build-debug.ps1 -RunUnitTests -RunDeviceTests
node --test functions/test/transactions.emulator.test.js
```

APK debug dùng Auth/Firestore/Functions/Storage Emulator tại `10.0.2.2`; máy Android thật cần cấu hình kết nối khác. Backend dùng project `fashion2hand`, Storage bucket `fashion2hand.firebasestorage.app`.

`transactions.emulator.test.js`: 6 nhóm integration test, gồm giá/địa chỉ/idempotency, vòng đời và phân quyền, hủy/từ chối/hoàn tiền, nhiều yêu cầu đồng thời, Storage và tranh chấp, hết hạn phía server.

`TransactionFlowTest`: 2 bài Android, kiểm tra giỏ nhiều món → checkout sau khi tạo lại Activity → đổi vai bán/mua → vận đơn/vị trí → nhận hàng → lịch sử; và danh sách ba yêu cầu → chấp nhận yêu cầu thứ hai → giao hàng → gửi tranh chấp có upload ảnh. Ảnh test được cung cấp tại điểm trả kết quả camera, không thao tác camera/gallery hệ thống.

Kiểm thử dùng AVD riêng `Marketplace_Test` API 33, giữ nguyên Pixel_6. Do System UI của host bị ANR, máy kiểm thử tắt animation/popup lỗi hệ thống; kết quả test và crash của app vẫn được kiểm tra.

Kết quả xác minh ngày 23/10/2026: build debug thành công; 3/3 unit test và 6/6 Android test qua (gồm regression Marketplace). Backend giao dịch: 5 nhóm đầu qua trong lần chạy đầy đủ ban đầu, nhóm đối soát hết hạn bổ sung qua trong lần chạy riêng. Tổng cộng 6/6 nhóm đã qua. Kiểm thử Android đã phát hiện và xác nhận bản sửa icon vector màn danh sách đơn cùng listener vận đơn trước/sau khi tạo.

## Chưa thuộc phạm vi hoàn thành

- Thanh toán thật, webhook đã xác thực từ nhà cung cấp và đối soát tiền thật.
- Đặt giao hàng với hãng, GPS trực tiếp hoặc bản đồ nhúng có API.
- Một lần checkout nhiều món thành một đơn; hiện chính sách là một món mỗi đơn.
- Xử lý/phán quyết tranh chấp của quản trị viên thuộc checklist Admin; phần này hoàn thiện việc gửi, lưu và bảo vệ bằng chứng.

Không deploy production trong đợt này.

## VNPAY sandbox — kiểm chứng 27/10/2026

- [x] PAY signing/checksum/encoding, GMT+7, đơn vị tiền và sandbox fail closed.
- [x] Attempt idempotency, nhiều món/đơn cũ, tách seller, quyền buyer và snapshot amount.
- [x] IPN HTTP local và Firestore Emulator: chữ ký/TMN/ref/amount sai, query trùng, callback cạnh tranh/sai thứ tự/trùng, success đến trễ và thanh toán trùng được lưu đối soát, không báo ACK trước commit.
- [x] Return trước/sau IPN không ghi PAID; mock webhook/callable không giả được VNPAY.
- [x] QueryDR mock kiểm chữ ký và phục hồi; timeout không suy luận FAILED, rate limit/quyền người gọi.
- [x] Handler thật cancel/reject/refresh/scheduler và settlement không giả REFUNDED/chuyển tiền VNPAY.
- [x] GHN staging/FREE regression, prepaid COD=0; GHN production vẫn bị chặn.
- [x] 49/49 backend tests; Android assembleDebug và 14/14 unit tests pass.
- [ ] Smoke test thiết bị: ADB hiện không có thiết bị kết nối.
- [ ] Nhập credential merchant kín, xác định VNPAY_QUERY_IP thực tế, deploy 17 Functions theo VNPAY_INTEGRATION.md.
- [ ] Xác minh URL deploy thật, gửi IPN URL cho VNPAY và xác nhận họ đã đăng ký.
- [ ] Thanh toán NCB sandbox thủ công + IPN VNPAY thật/Return/QueryDR/Merchant Admin.
- [ ] SIT: chưa đăng nhập được, chưa chạy, không tuyên bố đã đạt.
- [ ] Refund/payout API và production: chưa triển khai/bật; vẫn cần đối soát thủ công cho VNPAY sandbox.
