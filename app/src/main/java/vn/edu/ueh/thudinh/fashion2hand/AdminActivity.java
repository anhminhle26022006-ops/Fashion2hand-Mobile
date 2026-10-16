package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Admin dashboard and its prototype screens, kept separate from the user application flow. */
public class AdminActivity extends AppCompatActivity {
    private String adminPage = "overview";
    private final boolean[] adminCategoryEnabled = {true, true, true, true, false};
    private boolean adminAutoHideReported = true;
    private boolean adminRequirePhone = true;
    private String adminReportThreshold = "5";
    private String adminCompleteAfterDays = "3";
    private String adminMaximumPrice = "2000000";
    private EditText adminReportThresholdInput;
    private EditText adminCompleteAfterInput;
    private EditText adminMaximumPriceInput;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        renderAdminPage();
    }
    private void renderAdminPage() {
        android.widget.FrameLayout root = findViewById(R.id.adminRoot);
        if (root == null) return;
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF7F8F6);
        root.addView(page, new android.widget.FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(15), dp(8), dp(15), dp(8));
        header.setBackgroundColor(0xFFFFFFFF);
        TextView shield = adminText("⛨", 19, 0xFFFFFFFF, true);
        shield.setGravity(android.view.Gravity.CENTER);
        shield.setBackground(adminShape(0xFF25815F, 12));
        header.addView(shield, adminParams(38, 38));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0);
        TextView title = adminText(adminTitle(adminPage), 18, 0xFF17231E, true);
        TextView role = adminText("Vai trò ADMIN", 10, 0xFF74827B, false);
        heading.addView(title); heading.addView(role);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView bell = adminText("♧", 18, 0xFF23795C, true);
        bell.setGravity(android.view.Gravity.CENTER);
        bell.setBackground(adminShape(0xFFF1F4F2, 12));
        bell.setOnClickListener(v -> { adminPage = "notifications"; renderAdminPage(); });
        header.addView(bell, adminParams(38, 38));
        TextView avatar = adminText("A", 15, 0xFF256348, true);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setBackground(adminShape(0xFFDCEBE0, 30));
        LinearLayout.LayoutParams avatarParams = adminParams(34, 34); avatarParams.leftMargin = dp(9); header.addView(avatar, avatarParams);
        page.addView(header, adminParams(-1, 60));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(13), dp(14), dp(18));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if ("overview".equals(adminPage)) adminOverview(body);
        else if ("users".equals(adminPage)) adminUsers(body);
        else if ("user_detail".equals(adminPage)) adminUserDetail(body);
        else if ("posts".equals(adminPage)) adminPosts(body);
        else if ("post_detail".equals(adminPage)) adminPostDetail(body);
        else if ("orders".equals(adminPage)) adminOrders(body);
        else if ("order_detail".equals(adminPage)) adminOrderDetail(body);
        else if ("reports".equals(adminPage)) adminQueue(body, "reports");
        else if ("complaints".equals(adminPage)) adminQueue(body, "complaints");
        else if ("verification_queue".equals(adminPage)) adminQueue(body, "verification_queue");
        else if ("report_detail".equals(adminPage)) adminQueueDetail(body, "report_detail");
        else if ("complaint_detail".equals(adminPage)) adminQueueDetail(body, "complaint_detail");
        else if ("verification_detail".equals(adminPage)) adminQueueDetail(body, "verification_detail");
        else if ("notifications".equals(adminPage)) adminNotifications(body);
        else if ("settings".equals(adminPage)) adminSettings(body);
        else adminOther(body);
        page.addView(adminBottomNav(), adminParams(-1, 64));
    }

    private String adminTitle(String page) {
        switch (page) {
            case "users": return "Người dùng"; case "user_detail": return "Chi tiết người dùng";
            case "posts": return "Bài đăng"; case "post_detail": return "Chi tiết bài đăng";
            case "orders": return "Đơn hàng"; case "order_detail": return "Chi tiết đơn hàng";
            case "reports": return "Báo cáo vi phạm"; case "complaints": return "Khiếu nại giao dịch";
            case "verification_queue": return "Yêu cầu xác minh"; case "report_detail": return "Chi tiết báo cáo";
            case "complaint_detail": return "Chi tiết khiếu nại"; case "verification_detail": return "Hồ sơ xác minh";
            case "other": return "Khác"; case "notifications": return "Thông báo";
            case "settings": return "Cài đặt"; default: return "Tổng quan";
        }
    }

    private LinearLayout adminBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(0xFFFFFFFF);
        String[] labels = {"⌂\nTổng quan", "♙\nNgười dùng", "◇\nBài đăng", "▣\nĐơn hàng", "•••\nKhác"};
        String[] keys = {"overview", "users", "posts", "orders", "other"};
        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            boolean queuePage = "reports".equals(adminPage) || "complaints".equals(adminPage) || "verification_queue".equals(adminPage) || "report_detail".equals(adminPage) || "complaint_detail".equals(adminPage) || "verification_detail".equals(adminPage);
            boolean isSelected = adminPage.equals(key) || adminPage.startsWith(key.substring(0, Math.min(4, key.length()))) || (("notifications".equals(adminPage) || "settings".equals(adminPage) || queuePage) && "other".equals(key));
            TextView item = adminText(labels[i], 9, isSelected ? 0xFF187654 : 0xFF69756F, true);
            item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(0, dp(6), 0, dp(6));
            item.setOnClickListener(v -> { adminPage = key; renderAdminPage(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return nav;
    }

    private void adminOverview(LinearLayout body) {
        adminLabel(body, "CHÀO BUỔI SÁNG, ADMIN", 9, 0xFF21805F, true, 0, 0);
        adminLabel(body, "Tổng quan hệ thống", 21, 0xFF17231E, true, 4, 2);
        adminLabel(body, "Dữ liệu minh họa cho prototype · cập nhật 10:30", 10, 0xFF74827B, false, 0, 12);
        String[][] metrics = {{"1.248", "Người dùng"}, {"864", "Đang hoạt động"}, {"3.512", "Bài đăng"}, {"642", "Đơn hàng"}, {"18,4 tr", "Doanh số mô phỏng"}, {"287", "Cho tặng miễn phí"}};
        for (int i = 0; i < metrics.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            adminMetric(row, metrics[i][0], metrics[i][1]); adminMetric(row, metrics[i + 1][0], metrics[i + 1][1]);
            LinearLayout.LayoutParams rp = adminParams(-1, -2); rp.bottomMargin = dp(8); body.addView(row, rp);
        }
        adminSection(body, "Cần xử lý", "25 mục");
        adminActionRow(body, "12", "Báo cáo chờ xử lý", "reports");
        adminActionRow(body, "4", "Khiếu nại đang mở", "complaints");
        adminActionRow(body, "9", "Yêu cầu xác minh", "verification_queue");
        adminSection(body, "Đơn hàng 7 ngày", "126 đơn · +14,8%");
        LinearLayout bars = new LinearLayout(this); bars.setGravity(android.view.Gravity.BOTTOM); bars.setPadding(dp(8), dp(10), dp(8), dp(6));
        int[] heights = {44, 65, 54, 86, 75, 100, 80}; String[] days = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (int i = 0; i < heights.length; i++) { LinearLayout col = new LinearLayout(this); col.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL); col.setOrientation(LinearLayout.VERTICAL); TextView bar = adminText("", 8, 0xFFFFFFFF, false); bar.setBackground(adminShape(i == 5 ? 0xFF25815F : 0xFFB9DFCB, 7)); col.addView(bar, adminParams(25, heights[i])); TextView d = adminText(days[i], 8, 0xFF74827B, false); d.setGravity(android.view.Gravity.CENTER); col.addView(d, adminParams(-1, 18)); bars.addView(col, new LinearLayout.LayoutParams(0, -2, 1)); }
        adminCard(body, bars);
        adminSection(body, "Hoạt động gần đây", "");
        adminSimpleRow(body, "🛡️", "Báo cáo mới được gửi", "2 phút trước", null);
        adminSimpleRow(body, "♙", "Người dùng mới đăng ký", "10 phút trước", null);
        adminSimpleRow(body, "◇", "Đơn hàng đã hoàn tất", "15 phút trước", null);
        adminSimpleRow(body, "⚑", "Tranh chấp mới được mở", "20 phút trước", null);
    }

    private void adminUsers(LinearLayout body) {
        EditText search = adminSearch("Tìm tên, email, số điện thoại..."); body.addView(search, adminParams(-1, 46));
        LinearLayout filters = new LinearLayout(this); filters.setPadding(0, dp(8), 0, dp(8));
        String[] names = {"Tất cả", "Đã xác minh", "Chưa xác minh", "Bị khóa"};
        for (int i = 0; i < names.length; i++) { final int selected = i; TextView chip = adminChip(names[i], i == 0); chip.setOnClickListener(v -> filterAdminList(body, selected, "users")); filters.addView(chip); }
        body.addView(filters);
        String[][] data = {{"👩🏻", "Trần Bảo Ngọc", "ngoc.tran@gmail.com", "Đã xác minh · Đang hoạt động", "verified"}, {"👩🏼", "Lê Hoàng Phúc", "phuc.le@gmail.com", "Đã xác minh · Đang hoạt động", "verified"}, {"👩🏻", "Nguyễn Thu Vy", "vy.nguyen@gmail.com", "Chưa xác minh · Đang hoạt động", "unverified"}, {"👨🏻", "Phạm Gia Khang", "khang.pham@gmail.com", "Đã xác minh · Bị khóa", "blocked"}, {"👩🏻", "Đỗ Minh Hằng", "hang.do@gmail.com", "Chưa xác minh · Đang hoạt động", "unverified"}};
        LinearLayout list = adminList(body);
        for (String[] d : data) { LinearLayout r = adminPersonRow(d[0], d[1], d[2], d[3], "Chi tiết", "user_detail"); r.setTag(d[4]); list.addView(r); }
        search.addTextChangedListener(adminWatcher(list));
    }

    private void adminPosts(LinearLayout body) {
        EditText search = adminSearch("Tìm tên bài đăng, người bán..."); body.addView(adminSearchWithFilter(search, "Bộ lọc bài đăng"), adminParams(-1, 46));
        LinearLayout filters = new LinearLayout(this); filters.setPadding(0, dp(8), 0, dp(8));
        String[] names = {"Tất cả", "Miễn phí", "Đang bán", "Bị báo cáo", "Đã ẩn"};
        for (int i = 0; i < names.length; i++) { final int selected = i; TextView chip = adminChip(names[i], i == 0); chip.setOnClickListener(v -> filterAdminList(body, selected, "posts")); filters.addView(chip); }
        body.addView(filters);
        LinearLayout list = adminList(body);
        adminPostRow(list, "🧥", "Vintage Denim Jacket", "Người bán: Lan Chi · Đang hoạt động", "120.000 ₫", "reported");
        adminPostRow(list, "👚", "Áo sơ mi linen trắng", "Người bán: Lan Chi · Đang hoạt động", "Miễn phí · Admin chỉ giám sát", "free");
        adminPostRow(list, "👖", "Quần jeans ống rộng", "Người bán: Lan Chi · Đang hoạt động", "85.000 ₫", "active");
        search.addTextChangedListener(adminWatcher(list));
    }

    private void adminOrders(LinearLayout body) {
        adminNotice(body, "⛨  Chỉ theo dõi\nAdmin không thao tác mua bán thay người dùng.");
        EditText search = adminSearch("Tìm mã đơn, người mua, người bán..."); body.addView(adminSearchWithFilter(search, "Bộ lọc đơn hàng"), adminParams(-1, 46));
        LinearLayout filters = new LinearLayout(this); filters.setPadding(0, dp(8), 0, dp(8)); String[] names = {"Tất cả", "Đang giao", "Hoàn tất", "Tranh chấp"};
        for (int i = 0; i < names.length; i++) { final int selected = i; TextView chip = adminChip(names[i], i == 0); chip.setOnClickListener(v -> filterAdminList(body, selected, "orders")); filters.addView(chip); } body.addView(filters);
        LinearLayout list = adminList(body);
        adminOrderRow(list, "#F2H-10234", "Vintage Denim Jacket", "Trần Bảo Ngọc · Lê Hoàng Phúc", "Đang giao · Đang giữ tiền", "shipping");
        adminOrderRow(list, "#F2H-10219", "Áo sơ mi linen trắng", "Nguyễn Thu Vy · Lan Chi", "Hoàn tất · Đã thanh toán", "done");
        adminOrderRow(list, "#F2H-10208", "Váy hoa vintage", "Đỗ Minh Hằng · Thảo Vy", "Tranh chấp · Đang xem xét", "dispute");
        search.addTextChangedListener(adminWatcher(list));
    }

    private void adminOther(LinearLayout body) {
        LinearLayout account = new LinearLayout(this); account.setGravity(android.view.Gravity.CENTER_VERTICAL); account.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView avatar = adminText("A", 16, 0xFF256348, true); avatar.setGravity(android.view.Gravity.CENTER); avatar.setBackground(adminShape(0xFFDCEBE0, 30)); account.addView(avatar, adminParams(42, 42));
        TextView info = adminText("Quản trị viên\nadmin@fashion2hand.com", 12, 0xFF17231E, true); info.setPadding(dp(10), 0, 0, 0); account.addView(info, new LinearLayout.LayoutParams(0, -2, 1)); adminTextInto(account, "ADMIN", 8, 0xFF25815F, true); adminCard(body, account);
        LinearLayout list = adminList(body);
        adminSimpleRow(list, "⚑", "Báo cáo vi phạm", "Chờ xử lý · 12", () -> openAdminPage("reports"));
        adminSimpleRow(list, "⚠", "Khiếu nại giao dịch", "Tiền đang được giữ · 4", () -> openAdminPage("complaints"));
        adminSimpleRow(list, "✓", "Xác minh tài khoản", "Chờ duyệt · 9", () -> openAdminPage("verification_queue"));
        adminSimpleRow(list, "▣", "Theo dõi giao dịch", "Payment Holding Simulation", () -> Toast.makeText(this, "Dữ liệu giữ tiền mô phỏng", Toast.LENGTH_SHORT).show());
        adminSimpleRow(list, "♧", "Thông báo quản trị", "Báo cáo, tranh chấp, xác minh", () -> { adminPage = "notifications"; renderAdminPage(); });
        adminSimpleRow(list, "⚙", "Cài đặt hệ thống", "Danh mục và quy tắc kiểm duyệt", () -> { adminPage = "settings"; renderAdminPage(); });
        adminSimpleRow(list, "↪", "Đăng xuất", "Rời khu vực quản trị", () -> finish());
        adminSection(body, "Prototype states", ""); adminLabel(body, "Trạng thái danh sách Admin", 12, 0xFF17231E, true, 12, 2); adminLabel(body, "Chọn trạng thái để kiểm tra danh sách quản trị.", 10, 0xFF74827B, false, 0, 8);
        LinearLayout states = new LinearLayout(this); for (String state : new String[]{"Có dữ liệu", "Đang tải", "Rỗng", "Lỗi"}) { TextView chip = adminChip(state, state.equals("Có dữ liệu")); chip.setOnClickListener(v -> Toast.makeText(this, "Trạng thái: " + state, Toast.LENGTH_SHORT).show()); states.addView(chip); } body.addView(states);
    }

    private void adminUserDetail(LinearLayout body) {
        adminBackHeader(body, "users", "Người dùng");
        LinearLayout profile = new LinearLayout(this); profile.setOrientation(LinearLayout.VERTICAL); profile.setGravity(android.view.Gravity.CENTER); profile.setPadding(dp(12), dp(14), dp(12), dp(14));
        TextView avatar = adminText("👩🏻", 31, 0xFF23795C, true); avatar.setGravity(android.view.Gravity.CENTER); avatar.setBackground(adminShape(0xFFE4EFE7, 50)); profile.addView(avatar, adminParams(64, 64));
        adminLabel(profile, "Lê Hoàng Phúc", 18, 0xFF17231E, false, 6, 4);
        adminLabel(profile, "USER     ĐÃ XÁC MINH     ĐANG HOẠT ĐỘNG", 9, 0xFF21805F, true, 0, 3);
        adminLabel(profile, "Tham gia 02/09/2026", 9, 0xFF74827B, false, 0, 0); adminCard(body, profile);
        adminKeyValue(body, "Email", "phuc.le@gmail.com"); adminKeyValue(body, "Số điện thoại", "090 ••• •321"); adminKeyValue(body, "Khu vực", "Quận 3, TP.HCM"); adminKeyValue(body, "Tài khoản", "ĐANG HOẠT ĐỘNG");
        LinearLayout stats = new LinearLayout(this); String[][] counts = {{"14", "Bài đăng"}, {"6", "Đơn mua"}, {"9", "Đơn bán"}, {"0", "Bị báo cáo"}};
        for (int i = 0; i < counts.length; i += 2) { LinearLayout r = new LinearLayout(this); for (int j = i; j < i + 2; j++) { LinearLayout cell = new LinearLayout(this); cell.setGravity(android.view.Gravity.CENTER); cell.setOrientation(LinearLayout.VERTICAL); cell.setPadding(dp(5), dp(11), dp(5), dp(11)); cell.addView(adminText(counts[j][0] + "  " + counts[j][1], 10, 0xFF17231E, true)); adminCard(r, cell); LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) cell.getLayoutParams(); cp.width = 0; cp.weight = 1; cell.setLayoutParams(cp); if (j == i) r.addView(new View(this), new LinearLayout.LayoutParams(dp(6), 1)); } LinearLayout.LayoutParams rp = adminParams(-1, -2); rp.topMargin = dp(3); body.addView(r, rp); }
        adminSection(body, "Hoạt động gần đây", ""); adminSimpleRow(body, "✓", "Đăng bài “Áo khoác denim”", "12/10/2026 · 14:20", null); adminSimpleRow(body, "◇", "Hoàn tất đơn #F2H-10198", "08/10/2026 · 09:05", null); adminSimpleRow(body, "♡", "Tặng miễn phí một sản phẩm", "02/10/2026 · 17:40", null);
        adminButton(body, "Khóa tài khoản", 0xFFB53D37, () -> new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Khóa tài khoản?").setMessage("Người dùng sẽ không thể đăng nhập trong thời gian bị khóa.").setNegativeButton("Hủy", null).setPositiveButton("Xác nhận", (d, w) -> Toast.makeText(this, "Đã cập nhật trạng thái demo", Toast.LENGTH_SHORT).show()).show());
        adminNotice(body, "⛨  Mật khẩu được quản lý bởi Firebase Authentication và không thể chỉnh sửa tại đây.");
    }

    private void openAdminPage(String target) { adminPage = target; renderAdminPage(); }

    private void adminQueue(LinearLayout body, String kind) {
        String title = "reports".equals(kind) ? "Báo cáo vi phạm" : "complaints".equals(kind) ? "Khiếu nại giao dịch" : "Yêu cầu xác minh";
        String detailPage = "reports".equals(kind) ? "report_detail" : "complaints".equals(kind) ? "complaint_detail" : "verification_detail";
        adminBackHeader(body, "overview", "Tổng quan");
        EditText search = adminSearch("Tìm mã, đối tượng, lý do..."); body.addView(search, adminParams(-1, 44));
        LinearLayout filters = new LinearLayout(this); filters.setPadding(0, dp(7), 0, dp(7));
        String[] labels = "reports".equals(kind) ? new String[]{"Chờ xử lý", "Đang xem xét", "Đã xử lý", "Bác bỏ"} : "complaints".equals(kind) ? new String[]{"Chờ xử lý", "Đang xem xét", "Đã giải quyết", "Đóng"} : new String[]{"Chờ duyệt", "Đang xem xét", "Đã duyệt", "Cần bổ sung"};
        for (int i = 0; i < labels.length; i++) { TextView chip = adminChip(labels[i], i == 0); chip.setOnClickListener(v -> { for (int j = 0; j < filters.getChildCount(); j++) filters.getChildAt(j).setBackground(adminShape(j == filters.indexOfChild(chip) ? 0xFF25815F : 0xFFFFFFFF, 22)); }); filters.addView(chip); } body.addView(filters);
        LinearLayout list = adminList(body);
        if ("reports".equals(kind)) {
            adminSimpleRow(list, "⛨", "BC-0082 · Người dùng", "Có hành vi quấy rối · Người báo cáo: Phạm Gia Khang · Đang xem xét", () -> openAdminPage(detailPage));
            adminSimpleRow(list, "⚑", "BC-0079 · Bài đăng", "Ảnh không đúng mô tả · Chờ xử lý", () -> openAdminPage(detailPage));
        } else if ("complaints".equals(kind)) {
            adminSimpleRow(list, "⛨", "KN-0031 · Đơn #F2H-10234", "Người mua chưa nhận hàng · Đang xem xét", () -> openAdminPage(detailPage));
            adminSimpleRow(list, "⛨", "KN-0028 · Đơn #F2H-10198", "Sai khác mô tả sản phẩm · Chờ xử lý", () -> openAdminPage(detailPage));
        } else {
            adminSimpleRow(list, "✓", "VER-2026-00128 · Nguyễn Thu Vy", "CCCD gắn chip · Đã xác minh số điện thoại · Chờ duyệt", () -> openAdminPage(detailPage));
            adminSimpleRow(list, "✓", "VER-2026-00125 · Trần Bảo Ngọc", "Hộ chiếu · Đang xem xét", () -> openAdminPage(detailPage));
        }
        search.addTextChangedListener(adminWatcher(list));
    }

    private void adminQueueDetail(LinearLayout body, String kind) {
        String parent = "report_detail".equals(kind) ? "reports" : "complaint_detail".equals(kind) ? "complaints" : "verification_queue";
        adminBackHeader(body, parent, "Danh sách");
        if ("report_detail".equals(kind)) {
            adminLabel(body, "BÁO CÁO VI PHẠM", 9, 0xFF21805F, true, 3, 4); adminSection(body, "BC-0082 · Người dùng", "Đang xem xét");
            adminKeyValue(body, "Người bị báo cáo", "Phạm Gia Khang"); adminKeyValue(body, "Người báo cáo", "Nguyễn Thu Vy"); adminKeyValue(body, "Lý do", "Có hành vi quấy rối"); adminKeyValue(body, "Thời gian", "12/10/2026 · 14:20");
            adminNotice(body, "Nội dung báo cáo và bằng chứng đính kèm được hiển thị tại đây để Admin xem xét.");
            adminButton(body, "Xác nhận vi phạm", 0xFF173B32, () -> Toast.makeText(this, "Đã ghi nhận báo cáo demo", Toast.LENGTH_SHORT).show()); adminButton(body, "Bác bỏ báo cáo", 0xFFB53D37, () -> Toast.makeText(this, "Đã bác bỏ báo cáo demo", Toast.LENGTH_SHORT).show());
        } else if ("complaint_detail".equals(kind)) {
            adminLabel(body, "KHIẾU NẠI GIAO DỊCH", 9, 0xFF21805F, true, 3, 4); adminSection(body, "Người mua chưa nhận được hàng", "KN-0031");
            adminKeyValue(body, "Đơn hàng", "#F2H-10234"); adminKeyValue(body, "Người mua", "Trần Bảo Ngọc"); adminKeyValue(body, "Người bán", "Lê Hoàng Phúc"); adminKeyValue(body, "Số tiền đang giữ", "145.000đ"); adminNotice(body, "Kiện hàng chưa được giao theo thời gian dự kiến. Có 2 tệp bằng chứng đính kèm.");
            adminButton(body, "Yêu cầu bổ sung bằng chứng", 0xFF173B32, () -> Toast.makeText(this, "Đã gửi yêu cầu bổ sung demo", Toast.LENGTH_SHORT).show()); adminButton(body, "Đánh dấu đã giải quyết", 0xFF25815F, () -> Toast.makeText(this, "Đã giải quyết khiếu nại demo", Toast.LENGTH_SHORT).show());
        } else {
            adminLabel(body, "HỒ SƠ XÁC MINH", 9, 0xFF21805F, true, 3, 4); adminSection(body, "Nguyễn Thu Vy", "VER-2026-00128 · Chờ duyệt");
            adminKeyValue(body, "Họ tên pháp lý", "Nguyễn Thu Vy"); adminKeyValue(body, "Ngày sinh", "14/06/2001"); adminKeyValue(body, "Loại giấy tờ", "CCCD gắn chip"); adminKeyValue(body, "Số CCCD", "0792 •••• 1234"); adminKeyValue(body, "Số điện thoại", "Đã xác minh"); adminNotice(body, "Ảnh giấy tờ và ảnh chân dung đã gửi. Kiểm tra thông tin trước khi duyệt.");
            adminButton(body, "Duyệt xác minh", 0xFF173B32, () -> Toast.makeText(this, "Đã duyệt hồ sơ demo", Toast.LENGTH_SHORT).show()); adminButton(body, "Yêu cầu bổ sung", 0xFFB53D37, () -> Toast.makeText(this, "Đã yêu cầu bổ sung demo", Toast.LENGTH_SHORT).show());
        }
    }

    private void adminPostDetail(LinearLayout body) {
        adminBackHeader(body, "posts", "Bài đăng"); adminLabel(body, "🧥", 60, 0xFF53685D, true, 8, 8);
        adminSection(body, "Vintage Denim Jacket", "120.000 ₫"); adminKeyValue(body, "Người bán", "Lan Chi · Đã xác minh SĐT"); adminKeyValue(body, "Danh mục", "Áo khoác · Size M"); adminKeyValue(body, "Tình trạng", "Như mới"); adminKeyValue(body, "Vị trí", "Bình Thạnh, TP.HCM"); adminKeyValue(body, "Ngày tạo", "28/09/2026"); adminKeyValue(body, "Trạng thái", "ĐANG HIỂN THỊ");
        adminNotice(body, "LÝ DO BỊ BÁO CÁO\nHình ảnh không đúng mô tả · 2 báo cáo\nNghi ngờ ảnh bài đăng không trùng sản phẩm.");
        adminButton(body, "Ẩn bài đăng", 0xFF173B32, () -> Toast.makeText(this, "Bài đăng đã được ẩn trong demo", Toast.LENGTH_SHORT).show());
        adminButton(body, "Gỡ bài đăng", 0xFFB53D37, () -> new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Gỡ bài đăng?").setMessage("Thao tác mẫu để mô phỏng kiểm duyệt nội dung.").setNegativeButton("Hủy", null).setPositiveButton("Gỡ", (d, w) -> Toast.makeText(this, "Đã ghi nhận gỡ bài demo", Toast.LENGTH_SHORT).show()).show());
    }

    private void adminOrderDetail(LinearLayout body) {
        adminBackHeader(body, "orders", "Đơn hàng"); adminSection(body, "🧥  Vintage Denim Jacket", "120.000 ₫"); adminKeyValue(body, "Đơn hàng", "Đang giao"); adminKeyValue(body, "Thanh toán", "Đang giữ tiền"); adminKeyValue(body, "Vận chuyển", "Đang vận chuyển");
        adminNotice(body, "TRẠNG THÁI GIỮ TIỀN\n145.000đ đang được giữ\nPayment Holding Simulation · Chờ kết quả cuối cùng của đơn hàng.");
        adminSection(body, "Người mua", "Người bán"); adminKeyValue(body, "Trần Bảo Ngọc · Đã xác minh", "Lê Hoàng Phúc · Đã xác minh");
        adminSection(body, "Bảng tiền", "Đơn #F2H-10234"); adminKeyValue(body, "Giá sản phẩm", "120.000 ₫"); adminKeyValue(body, "Phí vận chuyển", "25.000 ₫"); adminKeyValue(body, "Tổng thanh toán", "145.000 ₫");
        adminSection(body, "Lịch sử trạng thái", ""); adminSimpleRow(body, "✓", "Đơn hàng được tạo", "28/09 · 09:10", null); adminSimpleRow(body, "✓", "Người bán đã gửi hàng", "28/09 · 14:20", null); adminSimpleRow(body, "●", "Đang vận chuyển", "29/09 · 10:35", null);
        adminButton(body, "Theo dõi vận chuyển", 0xFF173B32, () -> Toast.makeText(this, "Đây là màn hình theo dõi mẫu", Toast.LENGTH_SHORT).show());
    }

    private void adminNotifications(LinearLayout body) {
        adminBackHeader(body, "other", "Khác");
        adminLabel(body, "Thông báo quản trị", 16, 0xFF17231E, true, 2, 8);
        adminSimpleRow(body, "⛨", "Báo cáo mới", "Một tin đăng vừa bị báo cáo. · 2 phút trước", () -> openAdminPage("reports"));
        adminSimpleRow(body, "⛨", "Tranh chấp mới", "Người mua vừa mở tranh chấp. · 15 phút trước", () -> openAdminPage("complaints"));
        adminSimpleRow(body, "✓", "Yêu cầu xác minh", "Một hồ sơ đang chờ xem xét. · 30 phút trước", () -> openAdminPage("verification_queue"));
        adminSimpleRow(body, "◇", "Tin đăng cần kiểm duyệt", "Nội dung có thể vi phạm. · 1 giờ trước", () -> { adminPage = "posts"; renderAdminPage(); });
        TextView markRead = adminText("Đánh dấu tất cả đã đọc", 10, 0xFF23795C, true);
        markRead.setPadding(dp(10), dp(14), dp(10), dp(14));
        markRead.setOnClickListener(v -> Toast.makeText(this, "Đã đánh dấu thông báo là đã đọc.", Toast.LENGTH_SHORT).show());
        body.addView(markRead);
    }

    private void adminSettings(LinearLayout body) {
        adminReportThresholdInput = null; adminCompleteAfterInput = null; adminMaximumPriceInput = null;
        adminBackHeader(body, "other", "Khác");
        LinearLayout categoriesCard = new LinearLayout(this); categoriesCard.setOrientation(LinearLayout.VERTICAL); categoriesCard.setPadding(dp(11), dp(4), dp(11), dp(7));
        LinearLayout catHeader = new LinearLayout(this); catHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView catTitle = adminText("Danh mục sản phẩm", 12, 0xFF17231E, true); catHeader.addView(catTitle, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView addCategory = adminText("＋ Thêm danh mục", 9, 0xFF21805F, true); catHeader.addView(addCategory); categoriesCard.addView(catHeader);
        addCategory.setOnClickListener(v -> {
            EditText input = new EditText(this); input.setHint("Tên danh mục mới");
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Thêm danh mục")
                    .setView(input).setNegativeButton("Hủy", null)
                    .setPositiveButton("Thêm", (dialog, which) -> Toast.makeText(this, "Đã thêm danh mục mẫu: " + input.getText().toString(), Toast.LENGTH_SHORT).show()).show();
        });
        String[] categories = {"Áo", "Quần", "Váy", "Áo khoác", "Phụ kiện"};
        for (int i = 0; i < categories.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(0), dp(7), dp(0), dp(7));
            TextView icon = adminText("◇", 15, 0xFF23795C, true); icon.setGravity(android.view.Gravity.CENTER); icon.setBackground(adminShape(0xFFF1F4F2, 11)); row.addView(icon, adminParams(36, 36));
            TextView label = adminText(categories[i], 11, 0xFF17231E, true); label.setPadding(dp(10), 0, 0, 0); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.CheckBox toggle = new android.widget.CheckBox(this); toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF25815F)); toggle.setChecked(adminCategoryEnabled[i]); toggle.setOnCheckedChangeListener((button, checked) -> adminCategoryEnabled[index] = checked); row.addView(toggle);
            categoriesCard.addView(row); if (i < categories.length - 1) { View divider = new View(this); divider.setBackgroundColor(0xFFE1E7E3); categoriesCard.addView(divider, adminParams(-1, 1)); }
        }
        adminCard(body, categoriesCard);
        LinearLayout rulesCard = new LinearLayout(this); rulesCard.setOrientation(LinearLayout.VERTICAL); rulesCard.setPadding(dp(11), dp(4), dp(11), dp(7));
        TextView rulesTitle = adminText("Quy tắc kiểm duyệt", 12, 0xFF17231E, true); rulesTitle.setPadding(0, dp(11), 0, dp(11)); rulesCard.addView(rulesTitle);
        adminSettingsRule(rulesCard, "Tự ẩn bài khi bị báo cáo", "Số lượt báo cáo tối thiểu", adminReportThreshold + " lần");
        adminSettingsRule(rulesCard, "Tự hoàn tất đơn", "Sau khi giao hàng thành công", adminCompleteAfterDays + " ngày");
        adminSettingsRule(rulesCard, "Giá bán tối đa", "Áp dụng cho mọi bài đăng bán", adminMaximumPrice + " đ");
        LinearLayout verifyRow = new LinearLayout(this); verifyRow.setGravity(android.view.Gravity.CENTER_VERTICAL); verifyRow.setPadding(0, dp(10), 0, dp(5)); LinearLayout verifyText = new LinearLayout(this); verifyText.setOrientation(LinearLayout.VERTICAL); verifyText.addView(adminText("Bắt buộc xác minh SĐT", 10, 0xFF17231E, true)); verifyText.addView(adminText("Trước khi người dùng đăng bán", 9, 0xFF74827B, false)); verifyRow.addView(verifyText, new LinearLayout.LayoutParams(0, -2, 1)); android.widget.Switch verify = new android.widget.Switch(this); verify.setChecked(adminRequirePhone); verify.setOnCheckedChangeListener((button, checked) -> adminRequirePhone = checked); verifyRow.addView(verify); rulesCard.addView(verifyRow); adminCard(body, rulesCard);
        adminButton(body, "Lưu thay đổi", 0xFF25815F, () -> {
            if (adminReportThresholdInput != null) adminReportThreshold = adminReportThresholdInput.getText().toString();
            if (adminCompleteAfterInput != null) adminCompleteAfterDays = adminCompleteAfterInput.getText().toString();
            if (adminMaximumPriceInput != null) adminMaximumPrice = adminMaximumPriceInput.getText().toString();
            Toast.makeText(this, "Đã lưu cài đặt quản trị mẫu.", Toast.LENGTH_SHORT).show();
        });
    }

    private interface AdminToggle { void changed(boolean checked); }

    private void adminSwitchRow(LinearLayout parent, String title, String subtitle, boolean checked, AdminToggle listener) {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(11), dp(9), dp(10), dp(9));
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.addView(adminText(title, 10, 0xFF17231E, true)); text.addView(adminText(subtitle, 9, 0xFF74827B, false)); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Switch toggle = new android.widget.Switch(this); toggle.setChecked(checked); toggle.setOnCheckedChangeListener((button, value) -> listener.changed(value)); row.addView(toggle); adminCard(parent, row);
    }

    private LinearLayout adminValueRow(String title, String value, String unit) {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(11), dp(9), dp(10), dp(9));
        TextView label = adminText(title, 10, 0xFF17231E, true); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(value); input.setTextSize(10); input.setGravity(android.view.Gravity.CENTER); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setBackground(adminShape(0xFFFFFFFF, 10)); input.setPadding(dp(5), 0, dp(5), 0); row.addView(input, adminParams(76, 40));
        if ("Số lượt báo cáo tối thiểu".equals(title)) adminReportThresholdInput = input;
        else if ("Tự hoàn tất đơn".equals(title)) adminCompleteAfterInput = input;
        else if ("Giá bán tối đa".equals(title)) adminMaximumPriceInput = input;
        TextView suffix = adminText(unit, 9, 0xFF74827B, false); suffix.setPadding(dp(5), 0, 0, 0); row.addView(suffix); row.setBackground(adminShape(0xFFFFFFFF, 13)); return row;
    }

    private LinearLayout adminSearchWithFilter(EditText search, String filterLabel) {
        LinearLayout box = new LinearLayout(this); box.setGravity(android.view.Gravity.CENTER_VERTICAL); box.setPadding(dp(7), 0, dp(6), 0); box.setBackground(adminShape(0xFFF1F4F2, 13));
        TextView icon = adminText("⌕", 18, 0xFF233C32, false); icon.setGravity(android.view.Gravity.CENTER); box.addView(icon, adminParams(28, -1));
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT); search.setPadding(dp(4), 0, dp(4), 0); box.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
        TextView filter = adminText("≡", 14, 0xFF23795C, true); filter.setGravity(android.view.Gravity.CENTER); filter.setBackground(adminShape(0xFFFFFFFF, 8)); filter.setOnClickListener(v -> Toast.makeText(this, filterLabel, Toast.LENGTH_SHORT).show()); box.addView(filter, adminParams(30, 30)); return box;
    }

    private void adminSettingsRule(LinearLayout parent, String title, String subtitle, String value) {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.addView(adminText(title, 10, 0xFF17231E, true)); text.addView(adminText(subtitle, 9, 0xFF74827B, false)); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = adminText(value, 10, 0xFF17231E, true); amount.setGravity(android.view.Gravity.CENTER); amount.setPadding(dp(8), dp(8), dp(8), dp(8)); amount.setBackground(adminShape(0xFFFFFFFF, 9)); row.addView(amount, adminParams(82, 38)); parent.addView(row);
        View divider = new View(this); divider.setBackgroundColor(0xFFE1E7E3); parent.addView(divider, adminParams(-1, 1));
    }

    private void adminBackHeader(LinearLayout body, String page, String label) { TextView back = adminText("‹   " + label, 14, 0xFF23795C, true); back.setPadding(0, 0, 0, dp(10)); back.setOnClickListener(v -> { adminPage = page; renderAdminPage(); }); body.addView(back); }
    private void adminMetric(LinearLayout parent, String value, String label) { LinearLayout card = new LinearLayout(this); card.setGravity(android.view.Gravity.CENTER_VERTICAL); card.setPadding(dp(10), dp(10), dp(8), dp(10)); TextView icon = adminText("◇", 16, 0xFF23795C, true); icon.setGravity(android.view.Gravity.CENTER); icon.setBackground(adminShape(0xFFE7F4ED, 10)); card.addView(icon, adminParams(34, 34)); LinearLayout text = new LinearLayout(this); text.setPadding(dp(8), 0, 0, 0); text.setOrientation(LinearLayout.VERTICAL); text.addView(adminText(value, 17, 0xFF17231E, true)); text.addView(adminText(label, 9, 0xFF74827B, false)); card.addView(text); adminCard(parent, card); LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)card.getLayoutParams(); p.width=0; p.weight=1; p.height=dp(82); p.leftMargin=dp(3); p.rightMargin=dp(3); card.setLayoutParams(p); }
    private void adminSection(LinearLayout parent, String title, String detail) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); TextView h=adminText(title + (detail.isEmpty()?"":"     "+detail),12,0xFF17231E,true); h.setPadding(dp(12),dp(12),dp(12),dp(12)); card.addView(h); adminCard(parent,card); LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)card.getLayoutParams(); p.topMargin=dp(10); card.setLayoutParams(p); }
    private void adminActionRow(LinearLayout parent,String count,String label,String target){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView c=adminText(count,9,0xFFB53D37,true);c.setGravity(android.view.Gravity.CENTER);c.setBackground(adminShape(0xFFFFE4E2,8));row.addView(c,adminParams(28,28));TextView t=adminText(label,11,0xFF17231E,true);t.setPadding(dp(10),0,0,0);row.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));TextView a=adminText("›",20,0xFF74827B,false);row.addView(a);row.setOnClickListener(v->{adminPage=target;renderAdminPage();});adminCard(parent,row);}
    private LinearLayout adminList(LinearLayout parent){LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);adminCard(parent,list);return list;}
    private LinearLayout adminPersonRow(String emoji,String name,String email,String status,String action,String target){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(6),dp(10));TextView pic=adminText(emoji,20,0xFF23795C,true);pic.setGravity(android.view.Gravity.CENTER);pic.setBackground(adminShape(0xFFE4EFE7,30));row.addView(pic,adminParams(43,43));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(9),0,dp(4),0);text.addView(adminText(name,11,0xFF17231E,true));text.addView(adminText(email,9,0xFF74827B,false));text.addView(adminText(status,9,status.contains("Bị khóa")?0xFFB53D37:0xFF21805F,true));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView btn=adminText(action,9,0xFF137451,true);row.addView(btn);row.setOnClickListener(v->{adminPage=target;renderAdminPage();});return row;}
    private void adminPostRow(LinearLayout list,String emoji,String name,String seller,String price,String state){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(9),dp(7),dp(9));TextView image=adminText(emoji,23,0xFF23795C,true);image.setGravity(android.view.Gravity.CENTER);image.setBackground(adminShape(0xFFEAF3ED,12));row.addView(image,adminParams(44,44));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(9),0,dp(4),0);text.addView(adminText(state.equals("reported")?"BỊ BÁO CÁO":state.equals("free")?"FREE · 0đ":"FOR SALE",7,state.equals("reported")?0xFFB53D37:0xFF21805F,true));text.addView(adminText(name,10,0xFF17231E,true));text.addView(adminText(seller,8,0xFF74827B,false));if(!"free".equals(state))text.addView(adminText(price,9,0xFF21805F,true));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(adminText("›",15,0xFF74827B,false));row.setTag(state);row.setOnClickListener(v->{adminPage="post_detail";renderAdminPage();});list.addView(row);}
    private void adminOrderRow(LinearLayout list,String id,String name,String people,String status,String state){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(10),dp(7),dp(10));TextView image=adminText("shipping".equals(state)?"🧥":"done".equals(state)?"👚":"👗",22,0xFF23795C,true);image.setGravity(android.view.Gravity.CENTER);image.setBackground(adminShape(0xFFEAF3ED,12));row.addView(image,adminParams(44,44));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(9),0,dp(4),0);text.addView(adminText(id+" · "+name,10,0xFF17231E,true));text.addView(adminText(status,8,0xFF74827B,false));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(adminText("›",15,0xFF74827B,false));row.setTag(state);row.setOnClickListener(v->{adminPage="order_detail";renderAdminPage();});list.addView(row);}
    private void adminSimpleRow(LinearLayout parent,String icon,String title,String subtitle,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));TextView i=adminText(icon,15,0xFF23795C,true);i.setGravity(android.view.Gravity.CENTER);i.setBackground(adminShape(0xFFE7F4ED,10));row.addView(i,adminParams(34,34));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(9),0,0,0);text.addView(adminText(title,10,0xFF17231E,true));if(subtitle!=null)text.addView(adminText(subtitle,9,0xFF74827B,false));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));if(action!=null)row.setOnClickListener(v->action.run());adminCard(parent,row);}
    private void adminKeyValue(LinearLayout parent,String key,String value){LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(11),dp(10),dp(11),dp(10));row.addView(adminText(key,10,0xFF74827B,false),new LinearLayout.LayoutParams(0,-2,1));TextView val=adminText(value,10,0xFF17231E,true);val.setGravity(android.view.Gravity.RIGHT);row.addView(val,new LinearLayout.LayoutParams(0,-2,1));adminCard(parent,row);}
    private void adminNotice(LinearLayout parent,String text){TextView notice=adminText(text,10,0xFF52645A,false);notice.setPadding(dp(12),dp(12),dp(12),dp(12));notice.setBackground(adminShape(0xFFEAF4EE,14));LinearLayout.LayoutParams p=adminParams(-1,-2);p.topMargin=dp(8);parent.addView(notice,p);}
    private void adminButton(LinearLayout parent,String label,int color,Runnable action){TextView button=adminText(label,12,color==0xFF173B32?0xFFFFFFFF:color,true);button.setGravity(android.view.Gravity.CENTER);button.setPadding(dp(10),dp(12),dp(10),dp(12));button.setBackground(adminShape(color==0xFF173B32?color:0xFFFFFFFF,13));button.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=adminParams(-1,-2);p.topMargin=dp(8);parent.addView(button,p);}
    private EditText adminSearch(String hint){EditText edit=new EditText(this);edit.setSingleLine(true);edit.setTextSize(11);edit.setHint(hint);edit.setPadding(dp(12),0,dp(10),0);edit.setBackground(adminShape(0xFFF1F4F2,13));return edit;}
    private TextView adminChip(String label,boolean selected){TextView chip=adminText(label,9,selected?0xFFFFFFFF:0xFF43534B,true);chip.setGravity(android.view.Gravity.CENTER);chip.setPadding(dp(10),dp(8),dp(10),dp(8));chip.setBackground(adminShape(selected?0xFF25815F:0xFFFFFFFF,22));return chip;}
    private void filterAdminList(LinearLayout body,int filter,String kind){LinearLayout list=null;for(int i=0;i<body.getChildCount();i++)if(body.getChildAt(i) instanceof LinearLayout&&body.getChildAt(i).getTag()==null){LinearLayout candidate=(LinearLayout)body.getChildAt(i);if(candidate.getChildCount()>0){list=candidate;}}if(list==null)return;for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);String state=String.valueOf(row.getTag());boolean show=filter==0;if(kind.equals("users")){show=filter==0||(filter==1&&state.equals("verified"))||(filter==2&&state.equals("unverified"))||(filter==3&&state.equals("blocked"));}else if(kind.equals("posts")){show=filter==0||(filter==1&&state.equals("free"))||(filter==2&&state.equals("active"))||(filter==3&&state.equals("reported"))||(filter==4&&state.equals("hidden"));}else{show=filter==0||(filter==1&&state.equals("shipping"))||(filter==2&&state.equals("done"))||(filter==3&&state.equals("dispute"));}row.setVisibility(show?View.VISIBLE:View.GONE);}}
    private TextWatcher adminWatcher(LinearLayout list){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().toLowerCase();for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);StringBuilder text=new StringBuilder();appendVisibleText(row,text);row.setVisibility(text.toString().toLowerCase().contains(q)?View.VISIBLE:View.GONE);}}public void afterTextChanged(android.text.Editable e){}};}
    private void adminLabel(LinearLayout parent,String text,int size,int color,boolean bold,int top,int bottom){TextView v=adminText(text,size,color,bold);LinearLayout.LayoutParams p=adminParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);parent.addView(v,p);}
    private void adminTextInto(LinearLayout parent,String text,int size,int color,boolean bold){TextView v=adminText(text,size,color,bold);v.setPadding(dp(8),dp(5),dp(8),dp(5));v.setBackground(adminShape(0xFFE7F4ED,8));parent.addView(v);}
    private TextView adminText(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private void adminCard(LinearLayout parent,View child){child.setBackground(adminShape(0xFFFFFFFF,15));LinearLayout.LayoutParams p=adminParams(-1,-2);p.bottomMargin=dp(1);parent.addView(child,p);}
    private android.graphics.drawable.GradientDrawable adminShape(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xFFE1E7E3);return d;}
    private LinearLayout.LayoutParams adminParams(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}

    private void appendVisibleText(View view, StringBuilder content) {
        if (view instanceof TextView) content.append(((TextView) view).getText()).append(' ');
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) appendVisibleText(group.getChildAt(i), content);
        }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onBackPressed() {
        if ("user_detail".equals(adminPage)) adminPage = "users";
        else if ("post_detail".equals(adminPage)) adminPage = "posts";
        else if ("order_detail".equals(adminPage)) adminPage = "orders";
        else if ("report_detail".equals(adminPage)) adminPage = "reports";
        else if ("complaint_detail".equals(adminPage)) adminPage = "complaints";
        else if ("verification_detail".equals(adminPage)) adminPage = "verification_queue";
        else if ("reports".equals(adminPage) || "complaints".equals(adminPage) || "verification_queue".equals(adminPage)) adminPage = "overview";
        else if ("notifications".equals(adminPage) || "settings".equals(adminPage)) adminPage = "other";
        else if (!"overview".equals(adminPage)) adminPage = "overview";
        else { super.onBackPressed(); return; }
        renderAdminPage();
    }
}
