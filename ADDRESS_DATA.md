Danh mục địa chỉ Việt Nam hai cấp
================================

Nguồn dữ liệu: https://provinces.open-api.vn/api/v2/?depth=2
Tải ngày 25/10/2026: 34 tỉnh/thành phố, 3.321 phường/xã/đặc khu.
Đây là danh mục do dự án Vietnam Provinces Open API tổng hợp, không phải dịch vụ do cơ quan nhà nước vận hành.

`functions/data/vietnam-addresses.json` là bản chụp nguồn. Android dùng bản TSV
`app/src/main/assets/vietnam-addresses.tsv` (mã tỉnh, tên tỉnh, mã xã, tên xã),
được tạo từ cùng bản chụp. Danh sách được đóng gói để chọn địa chỉ không phụ thuộc API trực tuyến.
Khi cập nhật danh mục phải cập nhật cả hai bản, kiểm tra mã duy nhất và quan hệ tỉnh–xã.
Chạy `node scripts/update-address-directory.cjs` để cập nhật cả hai bản có kiểm tra số lượng/mã.

Địa chỉ mới lưu mã tỉnh, mã xã, tên chuẩn và `addressVersion=VN_2_LEVEL_2025`;
không yêu cầu quận/huyện. Backend chuẩn hóa tên theo mã và từ chối mã xã sai tỉnh.
Địa chỉ cũ và địa chỉ đã chốt trong đơn hàng được giữ nguyên; khi sửa địa chỉ cũ,
người dùng chọn lại đơn vị hiện hành. Không tự đoán chuyển đổi địa chỉ cũ.
Mỗi tài khoản có thể lưu tối đa 50 địa chỉ; tên/SĐT sửa riêng từng địa chỉ,
không thay đổi hồ sơ. Giao dịch lưu địa chỉ bảo đảm một địa chỉ mặc định.

Triển khai và kiểm tra ngày 25/10/2026:

- Đã triển khai riêng `saveAddress`, `createPaidOrder`, `createCartOrders`,
  `createFreeRequest` tại `us-central1`. Bản triển khai lấy từ source cloud trước đó,
  chỉ bổ sung danh mục và thay đổi địa chỉ, không phát hành các thay đổi backend khác.
- Test Auth/Firestore Emulator kiểm tra nhiều người nhận, sửa địa chỉ, chuyển/xóa
  mặc định, từ chối xã sai tỉnh; mua hàng, xin đồ và checkout giỏ bằng địa chỉ hai cấp PASS.
- Kiểm tra callable trên Firebase thật: lưu hai địa chỉ, sửa người nhận, chuẩn hóa
  tên theo mã và chuyển mặc định PASS. Tài khoản/dữ liệu thử tạm thời đã được xóa.
- 9/9 unit test PASS. 4/4 Android test PASS trên Pixel 6 API 33: form địa chỉ,
  xác minh email không SĐT và hai ca OTP mô phỏng. Test form xác nhận tự điền tên/SĐT,
  sửa người nhận, xóa phường khi đổi tỉnh, khôi phục các trường sau khi recreate.
