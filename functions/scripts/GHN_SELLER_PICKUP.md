# Điểm lấy hàng riêng cho người bán C2C

Luồng mới chỉ hoạt động trên **GHN staging**. Không bật production hoặc dịch vụ tính phí.

Người bán đăng nhập bằng tài khoản đã xác minh email, vào Trung tâm giao dịch → Tặng/Bán → Thiết lập địa chỉ lấy hàng GHN. Chọn địa chỉ đã lưu của chính mình, chọn tỉnh/quận/phường GHN và kiểm tra tên, điện thoại, số nhà/đường. Khi bấm lưu, app thông báo sẽ gửi thông tin liên hệ và địa chỉ tới GHN để đăng ký/liên kết điểm lấy hàng. Không tạo vận đơn ở bước này.

Backend `setupGhnSellerPickup` xác minh tài khoản, địa chỉ thuộc UID gọi và môi trường staging; client không được chọn UID hoặc Shop ID để liên kết. Shop đã liên kết đúng điểm được tái sử dụng. Điểm mới được tạo bằng `/v2/shop/register`, dùng shop nền `GHN_SHOP_ID` thuộc Token hiện có. Kết quả tạo thành công được lưu bền vững trước khi bật ánh xạ `ghnSellerShops/staging_<uid>`; không yêu cầu danh sách shop phải đồng bộ tức thì.

- GHN_TOKEN tiếp tục nằm trong Firebase Functions Secrets, không đi qua ứng dụng.
- Dùng tên shop xác định theo UID và dấu vân tay địa chỉ, khóa yêu cầu đồng thời và lưu trạng thái đăng ký phía server.
- Khi kết quả đăng ký không chắc chắn, chỉ tra danh sách và liên kết lại shop đã tạo; không tự gửi lại lệnh tạo. Nếu tiếp tục không tìm thấy, cần kiểm tra với GHN/Admin, không tạo liên tục.
- Lúc lấy báo giá, backend tự kiểm tra đăng ký chưa hoàn tất và liên kết shop đã có, chỉ khi dấu vân tay địa chỉ và hồ sơ hoặc kết quả tạo shop đã lưu khớp. Không tạo shop từ tài khoản người mua. Trong khoảng danh sách GHN chưa đồng bộ, chỉ ánh xạ có kết quả tạo thành công do chính server lưu (đúng UID, Shop ID, địa chỉ, môi trường) được tiếp tục lấy dịch vụ/báo giá bằng Token và Shop ID. Nếu hồ sơ GHN đã xuất hiện nhưng địa chỉ không khớp, vẫn chặn.
- Giới hạn đăng ký điểm mới: tối đa 5/ngày/tài khoản, cách nhau tối thiểu 60 giây. Liên kết lại điểm đã tồn tại không tạo shop mới.
- Đổi điểm lấy hàng chỉ tác động các báo giá/đơn mới; không sửa snapshot địa chỉ trong đơn cũ. Cần lấy lại báo giá sau khi đổi.
- Dữ liệu đăng ký nội bộ được chặn bởi quy tắc deny mặc định hiện có; không cần đổi Security Rules.

Triển khai từ thư mục gốc:

```powershell
firebase deploy --project fashion2hand --only functions:setupGhnSellerPickup,functions:getCheckoutQuote,functions:getCartCheckoutQuote
```

`GHN_SHOP_ID` phải là shop nền đang hoạt động và thuộc Token staging đang dùng. Mỗi người bán không cần đăng nhập website GHN hay gửi Token cho app.

Tài liệu GHN: https://developer.ghn.vn/vi/docs/shop/create-shop

Kiểm thử mock GHN trên Firestore Emulator riêng: `node --test --test-isolation=none test/ghn-seller-onboarding.emulator.test.js` trong `functions/`. Các test không tạo shop trên GHN thật. Cần thử lưu điểm lấy hàng từ app sau deploy để xác nhận quyền đăng ký shop trên tài khoản GHN hiện có.
