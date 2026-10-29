// Inventory audit only: this complements, and does not replace, Emulator E2E tests.
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
const read = file => fs.readFileSync(path.join(root, file), "utf8");
const java = "app/src/main/java/vn/edu/ueh/thudinh/fashion2hand/";
const main = read(java + "MainActivity.java");
for (const obsolete of ["notifications_demo", "shipping_address", 'getSharedPreferences("account_verification"'])
  if (main.includes(obsolete)) throw new Error(`Obsolete local business state: ${obsolete}`);

const groups = [
  ["contact_required", "Firebase Authentication; syncAccountContact", "Người dùng thường cần email đã xác minh; Admin đang hoạt động được miễn. SĐT tùy chọn, SMS thật tắt. Không yêu cầu duyệt CCCD hoặc duyệt bài."],
  ["login register forgot_password otp_verification new_password password_reset_success change_email verify_phone", "Firebase Authentication; syncAccountContact", "Auth backend; các bước OTP/reset sử dụng cùng luồng Auth, không tự đặt cờ xác minh."],
  ["home explore", "posts (ACTIVE), users/{uid}/favorites; hồ sơ người dùng", "Tin đăng realtime; loading, rỗng, lỗi và thử lại."],
  ["favorites", "users/{uid}/favorites + posts", "Đọc/lưu/bỏ yêu thích trên Firestore; bài không còn quyền xem không được hiện như còn bán."],
  ["my_listings", "posts WHERE ownerId; saveListing/setListingStatus", "Danh sách và thao tác sửa/ẩn bài ghi backend."],
  ["product_detail seller_profile", "posts; getSellerProfile; favorites; cart; createChat", "Hồ sơ công khai; không đọc hồ sơ riêng của người đăng, không dùng điểm/nhịp phản hồi mẫu."],
  ["sell sell_details sell_type sell_preview", "Nháp form → MemberOneRepository.saveListing + Firebase Storage", "Các bước nhập/xem trước giữ nháp trên máy có chủ đích; chỉ xuất bản khi ghi server thành công."],
  ["sell_success", "Kết quả thành công của saveListing", "Màn kết quả; không tự tạo tin hay xác nhận thành công trước backend."],
  ["messages chat", "chats/messages; getChatContext; createChat/sendMessage/markChatRead", "Tên, vai mua/bán, số chưa đọc từ backend; hiện chỉ hỗ trợ chat văn bản, không báo online giả."],
  ["profile", "users/{uid}; posts ownerId; orders buyerId/sellerId", "Đếm tin/đơn thật và loại trùng ID khi gộp hai vai; số đánh giá mẫu đã bỏ, chưa có form gửi đánh giá."],
  ["edit_profile", "users/{uid}; Firebase Storage avatar; Firebase Auth contact", "Lưu hồ sơ và avatar backend; tên/giới thiệu/khu vực và liên hệ không lấy dữ liệu mẫu."],
  ["notifications", "users/{uid}/notifications; read/readAt", "Realtime, lọc, đọc/đọc tất cả lưu server; chưa triển khai push FCM khi app đóng."],
  ["orders", "orders buyerId/sellerId; orderStatus", "Danh sách, tìm kiếm, bộ lọc; cache/lỗi không được coi là server xác nhận 0 đơn."],
  ["transactions request_status", "freeRequests; respondToFreeRequest/cancelFreeRequest", "Toàn bộ danh sách yêu cầu theo vai và trạng thái."],
  ["cart checkout requestfree", "cart; addresses; getCheckoutQuote; createPaidOrder/createFreeRequest", "Checkout dùng cùng sổ địa chỉ Firestore; mỗi món tạo đơn riêng theo khả năng backend."],
  ["order_detail cancel_order", "orders/payments; refreshOrder/updateOrderStatus/cancelOrder/rejectOrder", "Trạng thái và thao tác do backend quyết định, có kiểm tra vai và idempotency."],
  ["delivery delivery_map", "shipments; trackingEvents; lastLocation", "Đọc vận đơn/lịch sử/tọa độ backend. Nhà vận chuyển và cập nhật GPS thật chưa có API; luồng mô phỏng được ghi rõ."],
  ["transaction_history", "orders + moneyTransactions", "Lịch sử giao dịch và bút toán từ server."],
  ["report_issue", "createDispute + disputeEvidence + disputes", "Lưu tranh chấp và ảnh bằng chứng riêng tư; Admin đọc qua endpoint kiểm tra quyền."],
  ["addresses address_form", "users/{uid}/addresses; saveAddress/deleteAddress", "Không còn địa chỉ mẫu hay SharedPreferences; đồng bộ với checkout, kiểm tra địa chỉ mặc định."],
  ["payment_info", "getAccountData/savePayoutAccount; privateAccounts/{uid}", "Lưu/tải lại số tài khoản riêng tư; chưa xác minh ngân hàng hoặc chuyển tiền thật."],
  ["account_verification verification_status", "submitVerification/getAccountData; verificationRequests + private Storage evidence", "Gửi ba ảnh, case ID thật, chờ duyệt/từ chối/duyệt và gửi lại; dùng chung hồ sơ với Admin."],
  ["privacy_security", "Firebase Authentication + verification_status", "Email/số điện thoại thật và trạng thái xác minh Auth; các nút vào luồng xác minh thật."],
  ["support_contact", "submitSupportTicket/getAccountData; reports(type=SUPPORT)", "Yêu cầu, mã, trạng thái và phản hồi Admin lưu backend; không hiển thị hotline/email minh họa."],
  ["settings support", "Điều hướng đến các luồng đã nối; FirebaseAuth.signOut", "Màn điều hướng, không có dữ liệu nghiệp vụ riêng cần lưu."],
  ["language support_faq community_policy splash", "Nội dung tĩnh / điều hướng", "Không cần backend: ứng dụng chỉ có tiếng Việt; FAQ/chính sách hiện đóng gói trong app."],
];
const mapping = new Map();
for (const [screens, backend, note] of groups) for (const screen of screens.split(" ")) {
  const key = `activity_${screen}`;
  if (mapping.has(key)) throw new Error(`Duplicate screen: ${key}`);
  mapping.set(key, [backend, note]);
}
const routes = read("app/src/androidTest/assets/route-audit.txt").trim().split(/\r?\n/).map(x => x.trim());
routes.push("activity_splash");
for (const route of routes) if (!mapping.has(route)) throw new Error(`Unreviewed route: ${route}`);
for (const route of mapping.keys()) if (!routes.includes(route)) throw new Error(`Stale mapping: ${route}`);
const layouts = fs.readdirSync(path.join(root, "app/src/main/res/layout")).filter(file => file.endsWith(".xml")).sort();
const assetLayouts = read("app/src/androidTest/assets/layout-audit.txt").trim().split(/\r?\n/).map(x => `${x.trim()}.xml`).sort();
if (JSON.stringify(layouts) !== JSON.stringify(assetLayouts)) throw new Error("Layout audit asset is out of date");
for (const [file, tokens] of [
  ["AccountScreens.java", ["saveAddress", "savePayoutAccount", "submitVerification", "submitSupportTicket"]],
  ["NotificationInbox.java", ['collection("notifications")', "readAt"]],
  ["ui/admin/LiveAdminActivity.java", ["adminSession", "adminDashboard", "adminList", "adminDetail", "adminMutate", "adminSettings"]]
]) for (const token of tokens) if (!read(java + file).includes(token)) throw new Error(`${file} missing ${token}`);
const admin = [
  ["AdminLoginActivity", "Firebase Auth + adminSession", "Không có tài khoản/mật khẩu demo; kiểm tra claim và role server."],
  ["AdminMainActivity", "adminSession/adminDashboard", "Số liệu count từ Firestore, không dùng thẻ thống kê mẫu."],
  ["AdminListActivity", "adminList", "Người dùng, bài, đơn, báo cáo, khiếu nại, xác minh, tiền, danh mục, nhật ký; phân trang server."],
  ["AdminDetailActivity", "adminDetail/adminEvidence/adminMutate", "Chi tiết, ảnh riêng tư, lưu quyết định và audit; phản hồi xác minh/hỗ trợ gửi thông báo."],
  ["AdminSettingsActivity", "adminSettings/adminMutate(settings)", "Lưu backend, revision và idempotency. Phí ship được áp dụng; các chính sách tự động còn giới hạn bên dưới."],
  ["AdminAccessDeniedActivity", "Kết quả từ guard quyền Admin", "Màn thông báo từ chối và điều hướng, không có dữ liệu nghiệp vụ để sửa."],
];
const declaredActivities = [...read("app/src/main/AndroidManifest.xml").matchAll(/<activity\b[^>]*android:name="([^"]+)"/g)]
  .map(match => match[1].split(".").at(-1));
const auditedActivities = new Set(["MainActivity", ...admin.map(([name]) => name)]);
for (const activity of declaredActivities) if (!auditedActivities.has(activity)) throw new Error(`Unreviewed manifest activity: ${activity}`);
for (const match of main.matchAll(/R\.layout\.(activity_\w+)/g))
  if (!mapping.has(match[1])) throw new Error(`Unreviewed Main layout reference: ${match[1]}`);
const lines = [
  "# Rà soát kết nối backend toàn bộ giao diện", "",
  `Phạm vi: ${routes.length} màn Main (gồm splash) + ${admin.length} màn Admin = ${routes.length + admin.length} màn đang dùng; ${layouts.length} XML gồm cả thành phần và layout cũ. Firebase Emulator, không production.`, "",
  "Bảng dưới xác nhận đường đọc/ghi trong source. Kết quả E2E được ghi riêng; không coi việc có API trong source là đã kiểm thử mọi nhánh nghiệp vụ.", "",
  "## Từng màn người dùng", "", "| Màn | Nguồn backend / hành động | Kết luận rà soát |", "|---|---|---|",
  ...routes.sort().map(route => `| \`${route}\` | ${mapping.get(route)[0]} | ${mapping.get(route)[1]} |`), "",
  "## Từng màn Admin", "", "| Màn | Nguồn backend / hành động | Kết luận rà soát |", "|---|---|---|",
  ...admin.map(([screen, backend, note]) => `| \`${screen}\` | ${backend} | ${note} |`), "",
  "## XML và layout cũ", "",
  "Bốn màn Main/List/Detail/Settings của Admin dựng UI trong `LiveAdminActivity`; XML/Fragment Admin cũ không phải màn runtime. Mười màn giao dịch dựng bằng `TransactionScreens`. Nội dung mẫu trong layout cũ không được dùng làm dữ liệu giao dịch runtime.", "",
  "| XML | Vai trò |", "|---|---|",
  ...layouts.map(file => `| \`${file}\` | ${mapping.has(file.replace(/\.xml$/, "")) ? "Màn Main; đối chiếu bảng trên (giao dịch dùng controller runtime)" : file.startsWith("activity_admin_") ? "Màn Admin hoặc XML Admin cũ; đối chiếu bảng Admin" : "Thành phần UI / Fragment cũ, không phải route riêng"} |`), "",
  "## Giới hạn chức năng đã xác định", "",
  "- Không có API nhà cung cấp: thanh toán, vận chuyển, vị trí mô phỏng trên Emulator; chưa chuyển tiền, xác minh ngân hàng, GPS trực tiếp hoặc VNeID thật.",
  "- Chưa có push FCM khi đóng app, gửi ảnh trong chat hoặc form gửi đánh giá. UI không hiển thị điểm đánh giá/online/thời gian phản hồi giả; nút đính kèm chat chưa hỗ trợ được ẩn.",
  "- Ngôn ngữ chỉ có tiếng Việt; FAQ/chính sách là nội dung tĩnh. Không cần thêm API chỉ để hiển thị những màn này.",
  "- Người dùng thường cần xác minh email để đăng bài/mua/xin đồ; Admin có claim và hồ sơ hợp lệ được miễn. Không bắt buộc SĐT, SMS thật tắt; bài được công khai ngay. Tự ẩn theo ngưỡng báo cáo và tự hoàn tất theo số ngày còn chưa được thi hành đầy đủ.", "",
  "## Tái kiểm tra source inventory", "", "```powershell", "node scripts/audit-ui-backend.cjs", "```", "",
  "Script kiểm tra mọi route/layout có trong inventory và chặn việc dùng lại các kho trạng thái nghiệp vụ cục bộ cũ. Đọc thêm `QA_BACKEND_UI_RESULTS.md` về build và kết quả kiểm thử thực tế.", ""
];
fs.writeFileSync(path.join(root, "UI_BACKEND_AUDIT.md"), lines.join("\n"));
console.log(`AUDIT OK: ${routes.length} Main + ${admin.length} Admin routes; ${layouts.length} layouts; no obsolete local business stores.`);
