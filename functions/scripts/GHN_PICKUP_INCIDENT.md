# Nguyên nhân lỗi liên kết điểm lấy hàng – 30/10/2026

## Bằng chứng

- Lần thiết lập thật của người bán đã tạo shop 235476 nhưng chưa lưu mapping; operation dừng ở UNKNOWN. Log cũ chỉ có HTTP 503, không có bước lỗi chi tiết.
- Kiểm tra 8 lần đọc lại cùng địa chỉ: phiên bản tài liệu giữ nguyên và JSON không khác. Không đủ bằng chứng để quy lỗi sự cố này cho thứ tự trường JSON.
- Sau khi người dùng cho phép rõ ràng, đã tạo đúng một shop kiểm thử staging `F2-CONTRACT-STAGING-20261030` (ID 235478), bằng điểm lấy hàng đã đăng ký; không tạo vận đơn hoặc đổi mapping của người bán.
- GHN register trả thành công, data là object có `shop_id` kiểu number. Tuy nhiên 4 lần GET danh sách ngay sau đó đều không thấy shop. Lượt đọc sau mới thấy shop 235478. Đây là hành vi chưa đồng bộ giữa API tạo và API danh sách, đã tái hiện thật.

## Lỗi trong code và bản sửa

Code cũ yêu cầu tìm thấy shop mới trong danh sách ngay sau khi register. Vì danh sách còn cũ, code báo lỗi và bỏ dở liên kết dù GHN đã tạo shop.

Code mới lưu `registeredShopId`, `creationConfirmed` và dấu vân tay địa chỉ ngay sau phản hồi tạo hợp lệ. Ánh xạ được hoàn tất từ kết quả tạo do chính server nhận; không nhận Shop ID từ client và không giả tạo thành công khi GHN timeout hoặc từ chối.

Khi lấy báo giá mà danh sách còn cũ, chỉ receipt do server lưu, đúng seller/Shop ID/địa chỉ/staging và đã LINKED mới được dùng. Dịch vụ/phí vẫn được truy vấn bằng Token và Shop ID thực tế. Nếu danh sách đã có shop nhưng thông tin không khớp, vẫn chặn.

Kết quả không xác định vẫn chỉ được phục hồi bằng shop/receipt đã có, không gửi lại lệnh tạo mù. Thêm log theo bước và kiểu dữ liệu phản hồi, không log Token hoặc địa chỉ/điện thoại.

Kiểm thử hồi quy bao gồm trường hợp danh sách không bao giờ cập nhật ngay, mất bước lưu mapping, receipt sai người bán/Shop ID/địa chỉ và báo giá xuyên luồng trong thời gian danh sách còn cũ.
