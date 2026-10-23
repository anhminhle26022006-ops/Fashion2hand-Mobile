# Rà soát kết nối backend toàn bộ giao diện

Phạm vi: 49 màn Main (gồm splash) + 6 màn Admin = 55 màn đang dùng; 81 XML gồm cả thành phần và layout cũ. Firebase Emulator, không production.

Bảng dưới xác nhận đường đọc/ghi trong source. Kết quả E2E được ghi riêng; không coi việc có API trong source là đã kiểm thử mọi nhánh nghiệp vụ.

## Từng màn người dùng

| Màn | Nguồn backend / hành động | Kết luận rà soát |
|---|---|---|
| `activity_account_verification` | submitVerification/getAccountData; verificationRequests + private Storage evidence | Gửi ba ảnh, case ID thật, chờ duyệt/từ chối/duyệt và gửi lại; dùng chung hồ sơ với Admin. |
| `activity_address_form` | users/{uid}/addresses; saveAddress/deleteAddress | Không còn địa chỉ mẫu hay SharedPreferences; đồng bộ với checkout, kiểm tra địa chỉ mặc định. |
| `activity_addresses` | users/{uid}/addresses; saveAddress/deleteAddress | Không còn địa chỉ mẫu hay SharedPreferences; đồng bộ với checkout, kiểm tra địa chỉ mặc định. |
| `activity_cancel_order` | orders/payments; refreshOrder/updateOrderStatus/cancelOrder/rejectOrder | Trạng thái và thao tác do backend quyết định, có kiểm tra vai và idempotency. |
| `activity_cart` | cart; addresses; getCheckoutQuote; createPaidOrder/createFreeRequest | Checkout dùng cùng sổ địa chỉ Firestore; mỗi món tạo đơn riêng theo khả năng backend. |
| `activity_change_email` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_chat` | chats/messages; getChatContext; createChat/sendMessage/markChatRead | Tên, vai mua/bán, số chưa đọc từ backend; hiện chỉ hỗ trợ chat văn bản, không báo online giả. |
| `activity_checkout` | cart; addresses; getCheckoutQuote; createPaidOrder/createFreeRequest | Checkout dùng cùng sổ địa chỉ Firestore; mỗi món tạo đơn riêng theo khả năng backend. |
| `activity_community_policy` | Nội dung tĩnh / điều hướng | Không cần backend: ứng dụng chỉ có tiếng Việt; FAQ/chính sách hiện đóng gói trong app. |
| `activity_delivery` | shipments; trackingEvents; lastLocation | Đọc vận đơn/lịch sử/tọa độ backend. Nhà vận chuyển và cập nhật GPS thật chưa có API; luồng mô phỏng được ghi rõ. |
| `activity_delivery_map` | shipments; trackingEvents; lastLocation | Đọc vận đơn/lịch sử/tọa độ backend. Nhà vận chuyển và cập nhật GPS thật chưa có API; luồng mô phỏng được ghi rõ. |
| `activity_edit_profile` | users/{uid}; Firebase Storage avatar; Firebase Auth contact | Lưu hồ sơ và avatar backend; tên/giới thiệu/khu vực và liên hệ không lấy dữ liệu mẫu. |
| `activity_explore` | posts (ACTIVE), users/{uid}/favorites; hồ sơ người dùng | Tin đăng realtime; loading, rỗng, lỗi và thử lại. |
| `activity_favorites` | users/{uid}/favorites + posts | Đọc/lưu/bỏ yêu thích trên Firestore; bài không còn quyền xem không được hiện như còn bán. |
| `activity_forgot_password` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_home` | posts (ACTIVE), users/{uid}/favorites; hồ sơ người dùng | Tin đăng realtime; loading, rỗng, lỗi và thử lại. |
| `activity_language` | Nội dung tĩnh / điều hướng | Không cần backend: ứng dụng chỉ có tiếng Việt; FAQ/chính sách hiện đóng gói trong app. |
| `activity_login` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_messages` | chats/messages; getChatContext; createChat/sendMessage/markChatRead | Tên, vai mua/bán, số chưa đọc từ backend; hiện chỉ hỗ trợ chat văn bản, không báo online giả. |
| `activity_my_listings` | posts WHERE ownerId; saveListing/setListingStatus | Danh sách và thao tác sửa/ẩn bài ghi backend. |
| `activity_new_password` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_notifications` | users/{uid}/notifications; read/readAt | Realtime, lọc, đọc/đọc tất cả lưu server; chưa triển khai push FCM khi app đóng. |
| `activity_order_detail` | orders/payments; refreshOrder/updateOrderStatus/cancelOrder/rejectOrder | Trạng thái và thao tác do backend quyết định, có kiểm tra vai và idempotency. |
| `activity_orders` | orders buyerId/sellerId; orderStatus | Danh sách, tìm kiếm, bộ lọc; cache/lỗi không được coi là server xác nhận 0 đơn. |
| `activity_otp_verification` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_password_reset_success` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_payment_info` | getAccountData/savePayoutAccount; privateAccounts/{uid} | Lưu/tải lại số tài khoản riêng tư; chưa xác minh ngân hàng hoặc chuyển tiền thật. |
| `activity_privacy_security` | Firebase Authentication + verification_status | Email/số điện thoại thật và trạng thái xác minh Auth; các nút vào luồng xác minh thật. |
| `activity_product_detail` | posts; getSellerProfile; favorites; cart; createChat | Hồ sơ công khai; không đọc hồ sơ riêng của người đăng, không dùng điểm/nhịp phản hồi mẫu. |
| `activity_profile` | users/{uid}; posts ownerId; orders buyerId/sellerId | Đếm tin/đơn thật và loại trùng ID khi gộp hai vai; số đánh giá mẫu đã bỏ, chưa có form gửi đánh giá. |
| `activity_register` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |
| `activity_report_issue` | createDispute + disputeEvidence + disputes | Lưu tranh chấp và ảnh bằng chứng riêng tư; Admin đọc qua endpoint kiểm tra quyền. |
| `activity_request_status` | freeRequests; respondToFreeRequest/cancelFreeRequest | Toàn bộ danh sách yêu cầu theo vai và trạng thái. |
| `activity_requestfree` | cart; addresses; getCheckoutQuote; createPaidOrder/createFreeRequest | Checkout dùng cùng sổ địa chỉ Firestore; mỗi món tạo đơn riêng theo khả năng backend. |
| `activity_sell` | Nháp form → MemberOneRepository.saveListing + Firebase Storage | Các bước nhập/xem trước giữ nháp trên máy có chủ đích; chỉ xuất bản khi ghi server thành công. |
| `activity_sell_details` | Nháp form → MemberOneRepository.saveListing + Firebase Storage | Các bước nhập/xem trước giữ nháp trên máy có chủ đích; chỉ xuất bản khi ghi server thành công. |
| `activity_sell_preview` | Nháp form → MemberOneRepository.saveListing + Firebase Storage | Các bước nhập/xem trước giữ nháp trên máy có chủ đích; chỉ xuất bản khi ghi server thành công. |
| `activity_sell_success` | Kết quả thành công của saveListing | Màn kết quả; không tự tạo tin hay xác nhận thành công trước backend. |
| `activity_sell_type` | Nháp form → MemberOneRepository.saveListing + Firebase Storage | Các bước nhập/xem trước giữ nháp trên máy có chủ đích; chỉ xuất bản khi ghi server thành công. |
| `activity_seller_profile` | posts; getSellerProfile; favorites; cart; createChat | Hồ sơ công khai; không đọc hồ sơ riêng của người đăng, không dùng điểm/nhịp phản hồi mẫu. |
| `activity_settings` | Điều hướng đến các luồng đã nối; FirebaseAuth.signOut | Màn điều hướng, không có dữ liệu nghiệp vụ riêng cần lưu. |
| `activity_splash` | Nội dung tĩnh / điều hướng | Không cần backend: ứng dụng chỉ có tiếng Việt; FAQ/chính sách hiện đóng gói trong app. |
| `activity_support` | Điều hướng đến các luồng đã nối; FirebaseAuth.signOut | Màn điều hướng, không có dữ liệu nghiệp vụ riêng cần lưu. |
| `activity_support_contact` | submitSupportTicket/getAccountData; reports(type=SUPPORT) | Yêu cầu, mã, trạng thái và phản hồi Admin lưu backend; không hiển thị hotline/email minh họa. |
| `activity_support_faq` | Nội dung tĩnh / điều hướng | Không cần backend: ứng dụng chỉ có tiếng Việt; FAQ/chính sách hiện đóng gói trong app. |
| `activity_transaction_history` | orders + moneyTransactions | Lịch sử giao dịch và bút toán từ server. |
| `activity_transactions` | freeRequests; respondToFreeRequest/cancelFreeRequest | Toàn bộ danh sách yêu cầu theo vai và trạng thái. |
| `activity_verification_status` | submitVerification/getAccountData; verificationRequests + private Storage evidence | Gửi ba ảnh, case ID thật, chờ duyệt/từ chối/duyệt và gửi lại; dùng chung hồ sơ với Admin. |
| `activity_verify_phone` | Firebase Authentication; syncAccountContact | Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh. |

## Từng màn Admin

| Màn | Nguồn backend / hành động | Kết luận rà soát |
|---|---|---|
| `AdminLoginActivity` | Firebase Auth + adminSession | Không có tài khoản/mật khẩu demo; kiểm tra claim và role server. |
| `AdminMainActivity` | adminSession/adminDashboard | Số liệu count từ Firestore, không dùng thẻ thống kê mẫu. |
| `AdminListActivity` | adminList | Người dùng, bài, đơn, báo cáo, khiếu nại, xác minh, tiền, danh mục, nhật ký; phân trang server. |
| `AdminDetailActivity` | adminDetail/adminEvidence/adminMutate | Chi tiết, ảnh riêng tư, lưu quyết định và audit; phản hồi xác minh/hỗ trợ gửi thông báo. |
| `AdminSettingsActivity` | adminSettings/adminMutate(settings) | Lưu backend, revision và idempotency. Phí ship được áp dụng; các chính sách tự động còn giới hạn bên dưới. |
| `AdminAccessDeniedActivity` | Kết quả từ guard quyền Admin | Màn thông báo từ chối và điều hướng, không có dữ liệu nghiệp vụ để sửa. |

## XML và layout cũ

Bốn màn Main/List/Detail/Settings của Admin dựng UI trong `LiveAdminActivity`; XML/Fragment Admin cũ không phải màn runtime. Mười màn giao dịch dựng bằng `TransactionScreens`. Nội dung mẫu trong layout cũ không được dùng làm dữ liệu giao dịch runtime.

| XML | Vai trò |
|---|---|
| `activity_account_verification.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_address_form.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_addresses.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_admin_access_denied.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_admin_detail.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_admin_list.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_admin_login.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_admin_main.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_admin_settings.xml` | Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin |
| `activity_cancel_order.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_cart.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_change_email.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_chat.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_checkout.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_community_policy.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_delivery.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_delivery_map.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_edit_profile.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_explore.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_favorites.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_forgot_password.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_home.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_language.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_login.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_messages.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_my_listings.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_new_password.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_notifications.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_order_detail.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_orders.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_otp_verification.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_password_reset_success.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_payment_info.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_privacy_security.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_product_detail.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_profile.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_register.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_report_issue.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_request_status.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_requestfree.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_sell.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_sell_details.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_sell_preview.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_sell_success.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_sell_type.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_seller_profile.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_settings.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_splash.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_support.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_support_contact.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_support_faq.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_transaction_history.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_transactions.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_verification_status.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `activity_verify_phone.xml` | Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime) |
| `bottom_navigation.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `fragment_admin_list.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `fragment_admin_more.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `fragment_admin_overview.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_chip.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_evidence.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_input_row.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_kv.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_menu_row.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_row.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_stat.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_switch_row.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_admin_timeline.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_delivery_timeline.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_message.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_message_received.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_message_sent.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_order_list_purchase.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_order_list_status_row.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_order_timeline.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_order_update.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `item_security_notice.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `view_admin_backbar.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `view_admin_list_content.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `view_admin_state.xml` | Thành phần UI / Fragment cũ, không phải route riêng |
| `view_admin_topbar.xml` | Thành phần UI / Fragment cũ, không phải route riêng |

## Giới hạn chức năng đã xác định

- Không có API nhà cung cấp: thanh toán, vận chuyển, vị trí mô phỏng trên Emulator; chưa chuyển tiền, xác minh ngân hàng, GPS trực tiếp hoặc VNeID thật.
- Chưa có push FCM khi đóng app, gửi ảnh trong chat hoặc form gửi đánh giá. UI không hiển thị điểm đánh giá/online/thời gian phản hồi giả; nút đính kèm chat chưa hỗ trợ được ẩn.
- Ngôn ngữ chỉ có tiếng Việt; FAQ/chính sách là nội dung tĩnh. Không cần thêm API chỉ để hiển thị những màn này.
- Cấu hình Admin được lưu. Tự ẩn theo ngưỡng báo cáo, bắt xác minh điện thoại trước đăng bài và tự hoàn tất theo số ngày chưa được thi hành đầy đủ; không tính là chính sách tự động hoàn tất chỉ vì đã lưu cấu hình.

## Tái kiểm tra source inventory

```powershell
node scripts/audit-ui-backend.cjs
```

Script kiểm tra mọi route/layout có trong inventory và chặn việc dùng lại các kho trạng thái nghiệp vụ cục bộ cũ. Đọc thêm `QA_BACKEND_UI_RESULTS.md` về build và kết quả kiểm thử thực tế.
