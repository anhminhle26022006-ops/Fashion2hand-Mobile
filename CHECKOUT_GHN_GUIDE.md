# Địa chỉ, tồn kho và thanh toán

## Địa chỉ GHN

- Bước Địa chỉ nhận hàng cho chọn địa chỉ, thêm địa chỉ và cập nhật GHN.
- Chọn lại tỉnh/quận/phường GHN, sau đó sửa họ tên, điện thoại và số nhà/đường trong hộp xác nhận.
- Bước Thanh toán chỉ hiển thị địa chỉ. Bấm Quay lại bước địa chỉ hoặc nút quay lại để đổi.
- Server kiểm tra lại báo giá và địa chỉ khi tạo đơn. Địa chỉ đổi trên thiết bị khác cũng cần xác nhận lại.
- Điểm lấy hàng người bán thay đổi sẽ vô hiệu hóa liên kết shop cũ. Cập nhật điểm gửi trên GHN, rồi Admin chạy lại `ghnConfigureSeller` với Shop ID và addressId tương ứng. Không sửa snapshot của đơn đã tạo.
- Chỉ sửa tên, số điện thoại hoặc cách viết có dấu/không dấu của cùng đường sẽ giữ ánh xạ đang hoạt động. Tên và điện thoại được cập nhật trong điểm gửi; địa chỉ shop GHN đã đăng ký vẫn được giữ. Mã tỉnh/quận/phường hoặc số nhà/đường thực sự đổi mới cần liên kết lại. Việc lưu địa chỉ không tự bật lại liên kết đã bị tắt.

## Nhận đồ miễn phí

Người xin chọn địa chỉ và tích đồng ý trả đúng phí GHN hiển thị khi gửi yêu cầu. Người tặng chấp nhận sẽ lưu ACCEPTED, tạo đơn READY_TO_SHIP rồi tự gọi GHN với bên trả phí là người nhận (payment_type_id=2), tiền thu hộ sản phẩm bằng 0. Không có bước thanh toán VNPAY hoặc xác nhận phí lần hai nếu phí không đổi.

Yêu cầu và vận đơn được theo dõi trực tiếp ở cả hai tài khoản. Nếu GHN lỗi/timeout, yêu cầu vẫn ACCEPTED; đơn ghi RETRY_REQUIRED. Người tặng mở đơn và dùng Tạo / đối soát vận đơn GHN. Phản hồi không rõ chỉ tra cứu theo mã nội bộ, không gửi tạo lặp.

Trong staging, người tặng bấm chấp nhận sẽ để server lấy báo giá mới và tự tạo vận đơn, kể cả yêu cầu cũ chưa có báo giá. Dùng đúng addressId người xin đã chọn, không tự chọn địa chỉ mặc định. Giữ bản đồng ý phí ban đầu và ghi nguồn STAGING_SERVER_QUOTE riêng; không giả lập việc người xin đã xác nhận mức phí mới. Đơn production vẫn yêu cầu sự đồng ý phí hợp lệ. Yêu cầu thiếu địa chỉ nhận hoặc mã GHN cần bổ sung dữ liệu địa chỉ, không phải xác nhận báo giá lần hai. Những đơn đã chấp nhận trước bản sửa giữ luồng cũ, không tự gọi lấy hàng hàng loạt.

Đang dùng GHN staging, không tự chuyển môi trường production. Backend cần deploy createFreeRequest và respondFreeRequest sau thay đổi này.

## Tồn kho một món

- ACTIVE: có thể đặt một món; không có nhập số lượng nhiều hơn một.
- Tạo đơn trong Firestore transaction: RESERVED và reservedForOrderId; người khác không thể đặt cùng món.
- IPN VNPAY được xác minh thành công: SOLD. Đóng trình duyệt hoặc trang Return không tự đánh dấu đã trả tiền.
- Hủy/hết thời gian giữ hàng sử dụng chính sách server hiện có. Khoản nhận muộn sau khi mất quyền giữ món đi vào đối soát, không chiếm lại món đã cấp cho đơn khác.

## QR và chuyển tiền người bán

QR hiện tại là VNPAY Sandbox: chỉ kiểm thử, không chuyển tiền thật. Không thay QR của phiên VNPAY bằng QR ngân hàng cá nhân vì IPN của phiên đó sẽ không xác nhận khoản chuyển riêng.

Để nhận tiền thật qua VNPAY, cần đăng ký môi trường thật và tài khoản thụ hưởng merchant với VNPAY theo hồ sơ/hợp đồng. Không tự bật production trong bản sửa này.

Luồng dự kiến: người mua thanh toán → xác minh IPN → giao hàng → hết thời gian khiếu nại/đối soát → chi trả người bán → xác nhận kết quả chi trả. Code hiện tại chỉ ghi sổ và đưa chi trả VNPAY Sandbox vào REVIEW_REQUIRED; chưa chuyển khoản ngân hàng tự động và không cung cấp escrow ngân hàng thật.

Chi trả thật cần một quy trình quản trị có bằng chứng chuyển khoản hoặc tích hợp dịch vụ payout có xác minh tài khoản nhận và callback/đối soát. Không đánh dấu đã chuyển tiền chỉ bằng thay trạng thái Firestore.

## Triển khai

### Trạng thái và nút vận chuyển

Khi có mã vận đơn và GHN báo ready_to_pick/READY, hiển thị “Đã tạo vận đơn · chờ GHN lấy hàng”. READY_TO_SHIP là trạng thái chuẩn bị của đơn hàng, không có nghĩa chưa tạo vận đơn. Bấm Cập nhật vận chuyển hoặc Làm mới từ GHN sẽ tra cứu và báo kết quả, kể cả khi trạng thái không đổi. Không ép chuyển trạng thái của hãng.

Ẩn nút tạo khi đã có mã vận đơn. Lịch sử cũ được gom các dòng GHN trùng liên tiếp khi hiển thị; dữ liệu lịch sử cũ không bị xóa. Khi GHN chỉ thay thời gian cập nhật mà trạng thái không đổi, backend cập nhật metadata nhưng không thêm sự kiện hay thông báo mới.

Triển khai phần sửa làm mới/ghi nhận GHN: createShipment, refreshOrder, respondFreeRequest, ghnCallback, cancelGhnShipment. APK mới cần cài để hiển thị nhãn và phản hồi nút.

Phần chỉnh sửa địa chỉ GHN triển khai riêng saveGhnAddress:

```powershell
firebase deploy --project fashion2hand --only functions:saveGhnAddress
```

Không thay Firestore Rules, cấu hình thanh toán, Secret hay môi trường GHN.
