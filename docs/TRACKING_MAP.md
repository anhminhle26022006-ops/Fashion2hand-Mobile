# Bản đồ điểm gửi – nhận và lịch sử vận chuyển

Trong chi tiết vận đơn, chọn **Xem bản đồ điểm gửi – nhận và lịch sử**. Với đơn nội bộ chưa có vận đơn, chọn **Xem bản đồ điểm gửi – nhận** ngay ở phần Vận chuyển trong chi tiết đơn; thao tác này chỉ đọc địa chỉ, không gọi tạo vận đơn GHN.

- G: địa chỉ gửi được lưu khi tạo vận đơn, lấy từ địa chỉ mặc định của người bán; nếu chưa chọn mặc định thì dùng địa chỉ đầu tiên theo ID.
- N: địa chỉ nhận đã chụp lại khi đặt hàng. Thay đổi sổ địa chỉ sau đó không di chuyển các điểm của vận đơn cũ.
- Mốc cập nhật: tọa độ do backend lưu, nếu có. Mốc mô phỏng được ghi rõ.
- Lịch sử: sự kiện trong vận đơn, mới nhất ở trên. Không tạo thêm các mốc lấy/giao hàng khi backend chưa xác nhận.

Chạm địa chỉ để tìm trên ứng dụng bản đồ, hoặc chọn **Mở chỉ đường gửi → nhận**. Tọa độ địa chỉ do Android Geocoder phân giải, chỉ mang tính tham khảo. Nếu dịch vụ định vị không khả dụng hoặc hết thời gian chờ, màn hình vẫn hiển thị địa chỉ và lịch sử; không tự đặt điểm lên bản đồ. Vận đơn cũ chưa có địa chỉ gửi sẽ ghi rõ điểm gửi chưa được lưu; địa chỉ nhận có thể đọc từ đơn hàng liên quan.

## Triển khai

Leaflet 1.9.4 được đóng gói trong `app/src/main/assets/tracking`, kèm giấy phép BSD. Nền bản đồ dùng OpenStreetMap qua HTTPS, có attribution, User-Agent nhận diện ứng dụng và cache mặc định của WebView. Không tải trước bản đồ, không xin quyền GPS và không cần API key. Nền bản đồ cần Internet; lưu ý [chính sách tile OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/) khi triển khai quy mô lớn.

WebView chỉ chạy script đóng gói trong ứng dụng, không có JavaScript bridge hay quyền đọc file. Nhãn địa chỉ dùng `textContent`. Snapshot địa chỉ trên vận đơn chỉ chứa trường địa lý, không sao chép số điện thoại hay tên người nhận sang dữ liệu bản đồ.

Đây là màn bản đồ dùng dữ liệu vận đơn hiện có, bao gồm snapshot điểm gửi và nhận từ adapter GHN mới. GHN cần được cấu hình môi trường, Secret, shop và kiện hàng theo `GHN_INTEGRATION.md`. Vận đơn `MOCK_CARRIER` được đánh dấu mô phỏng; vận đơn GHN hiển thị môi trường, phí, thời gian dự kiến và nút làm mới. Dữ liệu này không thể hiện GPS trực tiếp của shipper.

## Kiểm thử

- `ShipmentTrackingMapTest`: hai điểm trên WebView, nhãn địa chỉ an toàn, thiếu địa chỉ hoặc geocoding thất bại không tạo tọa độ giả.
- `TransactionFlowTest`: mở bản đồ và lịch sử trong luồng giao dịch; giữ khả năng mở mốc tọa độ do backend lưu.
- `transactions.emulator.test.js`: snapshot đúng địa chỉ mặc định, không nhận địa chỉ giả từ caller, không lộ trường liên hệ, giữ snapshot khi sổ địa chỉ thay đổi và xử lý người bán chưa có địa chỉ.

WebView chỉ báo sẵn sàng sau khi JavaScript xác nhận số điểm đã dựng; khi tải trang hoặc renderer gặp lỗi, địa chỉ vẫn có thể mở qua ứng dụng bản đồ. Test component dùng màn kiểm tra cô lập và chờ callback thay cho khoảng ngủ cố định, để không bị chuyển màn đăng nhập hoặc khởi tạo WebView lần đầu làm sai kết quả. Hai test component đã đạt trên Pixel 6 / Android 13 ngày 27/10/2026.
