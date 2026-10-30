# Ghi chú sử dụng báo cáo Fashion2hand

Nội dung đối chiếu mã nguồn tại commit 701a31f98ee1aae4bf69ee000a083800d1912eac và đề cương người dùng gửi. Bản Word có đủ các mục Chương 1–7, phụ lục và tài liệu tham khảo. Bản Markdown là nguồn để sửa nội dung; script build_report.py hỗ trợ tạo lại Word, sau đó cần cập nhật mục lục và kiểm tra phân trang.

Thông tin còn cần nhóm bổ sung trước khi nộp:

- Tên học phần, họ tên/MSSV, chuyên ngành/khóa, giảng viên và mẫu bìa chính thức. Đầu vào hiện chỉ có đề cương văn bản, chưa có tệp Word mẫu để đối chiếu chính xác bố cục bìa.
- Ảnh thực tế từ đúng APK và môi trường chạy, kèm phân tích tại vị trí tương ứng Chương 3/6. Bản này có hai sơ đồ dựng theo nghiệp vụ, không có ảnh giả lập kết quả chạy ứng dụng.
- Thiết bị thật, phiên bản Android, thời điểm chạy, tài khoản thử và kết quả từng luồng. Bảng kiểm thử giữ riêng những mục chưa chạy lại.
- Issue/PR/review và phân công thực tế trên GitHub; tỷ lệ hoàn thành theo đầu việc. Chưa tự đặt link Issue/PR hoặc phần trăm.
- Kiểm tra commit/tag nộp cuối nếu mã nguồn thay đổi sau mốc đối chiếu.

Đã chạy bốn bộ Node test độc lập, 31/31 đạt; log ở KIEM_THU_DOC_LAP.txt. Đây là test provider/policy/catalog với dữ liệu kiểm thử, không phải toàn bộ E2E. Không triển khai cloud, tạo giao dịch tiền thật hoặc vận đơn production để biên soạn báo cáo.

Một số điểm đã điều chỉnh so với mô tả thiết kế cũ: không nhận là đã có Room/SQLite nếu chưa có triển khai; phân biệt MOCK_CARRIER với GHN; phân biệt ảnh trên imageUrls với subcollection images; phân biệt mảng items/events đang dùng với subcollection được dự kiến; ghi rõ giới hạn tìm kiếm 50 bài và nhánh hoàn/chi VNPAY cần đối soát. Chức năng đánh giá, gửi ảnh chat và push khi đóng ứng dụng không được tự ghi là hoàn thành.

DOI_CHIEU_NGUON.json là danh mục 255 tệp mã/layout/test/tài liệu được lập dấu vết kèm SHA-256. Danh mục không bao gồm thư viện nhà cung cấp, binary, build cache hoặc tệp cấu hình bí mật; số tệp không phải thước đo chất lượng kiểm thử.
