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


Cập nhật 27/10/2026 — danh mục 63 tỉnh/thành trước sáp nhập
=======================================================

Theo lựa chọn của chủ dự án, địa chỉ mới mặc định dùng bộ ba cấp trước 07/2025:
63 tỉnh/thành, 696 quận/huyện, 10.051 phường/xã. Nguồn snapshot tải một lần:
https://provinces.open-api.vn/api/v1/?depth=3 (dự án cộng đồng, không phải API nhà nước).

- Backend: functions/data/vietnam-addresses-legacy.json.
- Android: app/src/main/assets/vietnam-addresses-legacy.tsv, 6 cột: mã/tên tỉnh, mã/tên huyện, mã/tên xã.
- Cập nhật chủ động: node scripts/update-address-directory.cjs --legacy. Không tự gọi mạng khi người dùng mở form.
- Địa chỉ mới có addressVersion=VN_3_LEVEL_LEGACY, provinceCode/districtCode/wardCode. Server chuẩn hóa tên theo mã, kiểm tra tỉnh → huyện → xã.
- Đổi tỉnh xóa huyện/xã, đổi huyện xóa xã; áp dụng cả form tài khoản và hộp thêm địa chỉ checkout.
- Năm huyện đảo trong snapshot không có cấp xã: TSV giữ hàng mã xã rỗng để không làm mất huyện; form không buộc nhập xã giả. Việc GHN có hỗ trợ giao tới đó vẫn do GHN quyết định.
- Nếu xã cùng huyện trùng tên nhưng khác mã (ví dụ Đông Khê ở Thanh Hóa), picker hiển thị thêm mã để phân biệt; không gộp hoặc bỏ mã.
- Giữ nguyên bộ 34 tỉnh/3.321 xã và VN_2_LEVEL_2025 cho địa chỉ đã lưu. Khi sửa địa chỉ loại này, form giữ đúng danh mục hai cấp. Không migration địa chỉ/đơn hàng cũ.
- Mã hành chính không phải mã GHN. Người dùng vẫn cần chọn/xác minh mã GHN riêng; không suy đoán hoặc ánh xạ tự động theo tên.

Kiểm tra: 55/55 backend tests (địa chỉ, VNPAY, GHN) pass trên Emulator; Android build, test APK compile và 15/15 unit test pass. Chưa chạy instrumentation trên thiết bị trong lượt này.

Cloud: đã deploy thành công riêng saveAddress, createPaidOrder, createCartOrders và createFreeRequest tại us-central1 ngày 27/10/2026. Không phát hành rules/Hosting, không thay dữ liệu địa chỉ đã lưu.

Kiểm tra thiết bị bổ sung: AddressPickerSmokeTest chạy offline trên máy ảo, 3/3 ca pass (63 tỉnh và cascade; địa chỉ 34 tỉnh cũ; huyện đảo không có xã). Không tạo tài khoản hoặc ghi Firebase. APK Emulator dùng cho test đã được thay lại bằng APK Cloud, không xóa dữ liệu ứng dụng.

Màn đăng sản phẩm (27/10/2026): mục Vị trí đã bỏ Spinner bảy lựa chọn cố định. Mở hộp chọn tỉnh → huyện → xã từ cùng danh mục 63 tỉnh/thành, có tìm kiếm, giữ vị trí bài cũ khi chưa đổi. Bài mới lưu location cùng locationProvinceCode/locationDistrictCode/locationWardCode và locationVersion; đây là vị trí công khai của món đồ, không thay địa chỉ lấy hàng GHN. Không cần thay backend/rules cho thay đổi UI này.

Sửa tìm kiếm 27/10/2026: thay ArrayAdapter filter mặc định bằng tìm theo cụm từ không phân biệt dấu/hoa thường, hỗ trợ HCM/TP.HCM/Sài Gòn. Luôn lọc từ toàn bộ danh mục gốc, không lọc tiếp từ kết quả trước; bấm lại lựa chọn chính xác sẽ hiện đủ danh sách. Áp dụng chung cho vị trí sản phẩm và địa chỉ nhận hàng.

Kiểm tra tìm kiếm trên máy ảo: AddressPickerSmokeTest 4/4 pass, gồm các truy vấn Hồ Chí Minh/ho chi minh/TP.HCM/Sài Gòn và xóa query để lấy lại đủ 63 tỉnh/thành. Không tạo tài khoản hoặc ghi Firebase.
