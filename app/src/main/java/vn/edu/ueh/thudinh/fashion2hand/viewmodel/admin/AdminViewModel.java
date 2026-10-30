package vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.ueh.thudinh.fashion2hand.adapter.admin.AdminRow;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;
import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.VerificationRequest;
import vn.edu.ueh.thudinh.fashion2hand.repository.admin.AdminRepository;

/**
 * Tầng ViewModel của khu vực Admin (UI → ViewModel → Repository).
 * Lấy dữ liệu từ Repository, lọc/tìm kiếm và đổi sang AdminRow để giao diện hiển thị.
 * Giao diện không gọi Repository trực tiếp.
 */
public class AdminViewModel extends ViewModel {

    public static final String[] USER_FILTERS = {"Tất cả", "Đã xác minh", "Chờ xác minh", "Chưa xác minh", "Bị khóa"};
    public static final String[] POST_FILTERS = {"Tất cả", "Miễn phí", "Đang bán", "Bị báo cáo", "Đã ẩn", "Đã gỡ"};
    public static final String[] ORDER_FILTERS = {"Tất cả", "Chờ xác nhận", "Đang giao", "Hoàn tất", "Tranh chấp", "Đã hủy"};
    public static final String[] REPORT_FILTERS = {"Đang mở", "Chờ xử lý", "Đang xem xét", "Đã xử lý", "Bác bỏ", "Tất cả"};
    public static final String[] DISPUTE_FILTERS = {"Đang mở", "Chờ bằng chứng", "Đã giải quyết", "Tất cả"};
    public static final String[] VERIFY_FILTERS = {"Chờ duyệt", "Đã duyệt", "Từ chối", "Tất cả"};
    public static final String[] TRANSACTION_FILTERS = {"Tất cả", "Đang giữ", "Đã giải ngân", "Đã hoàn tiền", "Tranh chấp"};
    public static final String[] NOTIFICATION_FILTERS = {"Tất cả", "Báo cáo", "Khiếu nại", "Xác minh"};

    private final AdminRepository repository = AdminRepository.getInstance();

    // ---------------------------------------------------------------- Phiên và trạng thái tải

    public void refresh() { repository.refresh(); }
    public void refreshIfStale() { repository.refreshIfStale(); }
    public boolean isLoaded() { return repository.isLoaded(); }
    public boolean isLoading() { return repository.isLoading(); }
    public LiveData<Integer> getDataVersion() { return repository.getDataVersion(); }
    public LiveData<AdminRepository.Event<String>> getMessage() { return repository.getMessage(); }
    public LiveData<AdminRepository.Event<Boolean>> getSessionDenied() { return repository.getSessionDenied(); }
    public String getAdminEmail() { return repository.getAdminEmail(); }
    public void logout() { repository.logout(); }
    public void reset() { repository.reset(); }

    // ---------------------------------------------------------------- Đếm cho Tổng quan và badge

    public int countOpenReports() { return repository.countOpenReports(); }
    public int countOpenDisputes() { return repository.countOpenDisputes(); }
    public int countPendingVerifications() { return repository.countPendingVerifications(); }

    public int countTodo() {
        return countOpenReports() + countOpenDisputes() + countPendingVerifications();
    }

    public String getTodayText() {
        return new SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(new Date());
    }


    // ---------------------------------------------------------------- Số liệu Tổng quan (tính từ dữ liệu backend)

    public int countUsers() {
        int n = 0;
        for (User u : repository.getUsers()) if (!User.ROLE_ADMIN.equals(u.getRole())) n++;
        return n;
    }

    public int countActiveUsers() {
        int n = 0;
        for (User u : repository.getUsers()) if (!User.ROLE_ADMIN.equals(u.getRole()) && !u.isLocked()) n++;
        return n;
    }

    public int countPosts() { return repository.getPosts().size(); }

    public int countActivePosts() {
        int n = 0;
        for (Post p : repository.getPosts()) if (Post.STATUS_ACTIVE.equals(p.getStatus())) n++;
        return n;
    }

    /**
     * Đơn bị hủy (hết hạn thanh toán, bị từ chối...) không tính vào số đơn: người mua thường đặt lại đúng món đó,
     * nếu đếm cả đơn hủy thì một món bị tính nhiều lần.
     */
    private static boolean countable(Order o) { return !Order.STATUS_CANCELLED.equals(o.getOrderStatus()); }

    public int countOrders() {
        int n = 0;
        for (Order o : repository.getOrders()) if (countable(o)) n++;
        return n;
    }

    public int countCancelledOrders() { return repository.getOrders().size() - countOrders(); }

    public int countOrdersSince(int days) {
        long from = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        int n = 0;
        for (Order o : repository.getOrders()) {
            if (!countable(o)) continue;
            Long created = repository.getCreatedMillis("orders", o.getOrderId());
            if (created != null && created >= from) n++;
        }
        return n;
    }

    /** Doanh số mô phỏng: tổng giá các đơn mua bán đã hoàn tất. */
    public long getCompletedSales() {
        long total = 0;
        for (Order o : repository.getOrders())
            if (!o.isFree() && Order.STATUS_COMPLETED.equals(o.getOrderStatus())) total += o.getPrice();
        return total;
    }

    public int countCompletedSales() {
        int n = 0;
        for (Order o : repository.getOrders())
            if (!o.isFree() && Order.STATUS_COMPLETED.equals(o.getOrderStatus())) n++;
        return n;
    }

    public int countFreeOrders() {
        int n = 0;
        for (Order o : repository.getOrders()) if (o.isFree()) n++;
        return n;
    }

    public int countCompletedFreeOrders() {
        int n = 0;
        for (Order o : repository.getOrders())
            if (o.isFree() && Order.STATUS_COMPLETED.equals(o.getOrderStatus())) n++;
        return n;
    }

    /** Số đơn tạo mỗi ngày trong 7 ngày gần nhất, phần tử cuối là hôm nay. */
    public int[] getOrdersLast7Days() {
        int[] counts = new int[7];
        java.util.Calendar today = startOfDay(System.currentTimeMillis());
        for (Order o : repository.getOrders()) {
            if (!countable(o)) continue;
            Long created = repository.getCreatedMillis("orders", o.getOrderId());
            if (created == null) continue;
            long diff = today.getTimeInMillis() - startOfDay(created).getTimeInMillis();
            int daysAgo = (int) Math.round(diff / (24.0 * 60 * 60 * 1000));
            if (daysAgo >= 0 && daysAgo < 7) counts[6 - daysAgo]++;
        }
        return counts;
    }

    /** Nhãn thứ cho 7 ngày gần nhất: T2 ... CN, phần tử cuối là hôm nay. */
    public String[] getLast7DayLabels() {
        String[] names = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        String[] labels = new String[7];
        java.util.Calendar day = startOfDay(System.currentTimeMillis());
        day.add(java.util.Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            labels[i] = names[day.get(java.util.Calendar.DAY_OF_WEEK) - 1];
            day.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return labels;
    }

    /** Hoạt động gần đây: {tiêu đề, thời gian} của 4 sự kiện mới nhất. */
    public List<String[]> getRecentActivities() {
        List<Object[]> events = new ArrayList<>();
        for (Report r : repository.getReports())
            addEvent(events, repository.getCreatedMillis("reports", r.getReportId()), "Báo cáo: " + r.getReason());
        for (Dispute d : repository.getDisputes())
            addEvent(events, repository.getCreatedMillis("disputes", d.getDisputeId()), "Khiếu nại đơn " + shortId(d.getOrderId()));
        for (VerificationRequest v : repository.getVerifications())
            addEvent(events, repository.getCreatedMillis("verificationRequests", v.getRequestId()),
                    "Hồ sơ xác minh: " + repository.getUserName(v.getUserId()));
        for (Order o : repository.getOrders())
            addEvent(events, repository.getCreatedMillis("orders", o.getOrderId()),
                    (o.isFree() ? "Đơn cho tặng: " : "Đơn mới: ") + repository.getPostTitle(o.getPostId()));
        java.util.Collections.sort(events, (a, b) -> Long.compare((Long) b[0], (Long) a[0]));
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < events.size() && i < 4; i++)
            result.add(new String[]{(String) events.get(i)[1], timeAgo((Long) events.get(i)[0])});
        return result;
    }

    private static void addEvent(List<Object[]> events, Long millis, String title) {
        if (millis != null) events.add(new Object[]{millis, title});
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? String.valueOf(id) : id.substring(0, 8);
    }

    private static String timeAgo(long millis) {
        long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60000);
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        long hours = minutes / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        return days == 1 ? "Hôm qua" : days + " ngày trước";
    }

    private static java.util.Calendar startOfDay(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c;
    }

    // ---------------------------------------------------------------- Danh sách

    public List<AdminRow> getUserRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        int index = 0;
        for (User u : repository.getUsers()) {
            if (User.ROLE_ADMIN.equals(u.getRole())) continue;
            index++;
            boolean keep;
            switch (filter) {
                case 1: keep = User.VERIFIED.equals(u.getVerificationStatus()); break;
                case 2: keep = User.VERIFY_PENDING.equals(u.getVerificationStatus()); break;
                case 3: keep = User.UNVERIFIED.equals(u.getVerificationStatus()); break;
                case 4: keep = u.isLocked(); break;
                default: keep = true;
            }
            if (!keep) continue;
            AdminRow row = new AdminRow(u.getUserId())
                    .setLeading(AdminUi.initials(u.getName()), AdminUi.avatarKind(index))
                    .setTitle(u.getName())
                    .setSubtitle(u.getEmail() + (u.getReportedCount() > 0 ? "\nBị báo cáo " + u.getReportedCount() + " lần" : ""))
                    .setChip(AdminLabels.verification(u.getVerificationStatus()))
                    .setChip2(u.isLocked() ? AdminLabels.account(u.getAccountStatus()) : null);
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getPostRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        for (Post p : repository.getPosts()) {
            boolean keep;
            switch (filter) {
                case 1: keep = p.isFree(); break;
                case 2: keep = !p.isFree(); break;
                case 3: keep = p.getReportCount() > 0; break;
                case 4: keep = Post.STATUS_HIDDEN.equals(p.getStatus()); break;
                case 5: keep = Post.STATUS_REMOVED.equals(p.getStatus()); break;
                default: keep = true;
            }
            if (!keep) continue;
            AdminRow row = new AdminRow(p.getPostId())
                    .setLeading(categoryEmoji(p.getCategory()), p.isFree() ? AdminUi.KIND_OK : AdminUi.KIND_INFO)
                    .setImage("posts", p.getPostId(), getPostCover(p.getPostId()))
                    .setTitle(p.getTitle())
                    .setSubtitle(repository.getUserName(p.getUserId()) + " · Size " + p.getSize() + " · " + p.getCondition())
                    .setChip(AdminLabels.postStatus(p.getStatus()))
                    .setChip2(p.getReportCount() > 0
                            ? new AdminLabels.Label("Bị báo cáo " + p.getReportCount(), AdminUi.KIND_DANGER) : null)
                    .setTrailing(p.isFree() ? "Miễn phí" : AdminUi.money(p.getPrice()));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getOrderRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        for (Order o : repository.getOrders()) {
            String s = o.getOrderStatus();
            boolean keep;
            switch (filter) {
                case 1: keep = Order.STATUS_PENDING.equals(s) || Order.STATUS_CONFIRMED.equals(s); break;
                case 2: keep = Order.STATUS_SHIPPING.equals(s) || Order.STATUS_DELIVERED.equals(s); break;
                case 3: keep = Order.STATUS_COMPLETED.equals(s); break;
                case 4: keep = Order.STATUS_DISPUTED.equals(s); break;
                case 5: keep = Order.STATUS_CANCELLED.equals(s) || Order.STATUS_REFUNDED.equals(s); break;
                default: keep = true;
            }
            if (!keep) continue;
            AdminRow row = new AdminRow(o.getOrderId())
                    .setLeading("#", o.isFree() ? AdminUi.KIND_OK : AdminUi.KIND_INFO)
                    .setTitle(o.getOrderId() + " · " + repository.getPostTitle(o.getPostId()))
                    .setSubtitle(partiesText(o))
                    .setChip(AdminLabels.orderType(o.getOrderType()))
                    .setChip2(AdminLabels.orderStatus(s))
                    .setTrailing(AdminUi.money(o.getTotalAmount()));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getReportRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        for (Report r : repository.getReports()) {
            String s = r.getStatus();
            boolean keep;
            switch (filter) {
                case 0: keep = r.isOpen(); break;
                case 1: keep = Report.PENDING.equals(s); break;
                case 2: keep = Report.REVIEWING.equals(s); break;
                case 3: keep = Report.RESOLVED.equals(s); break;
                case 4: keep = Report.REJECTED.equals(s); break;
                default: keep = true;
            }
            if (!keep) continue;
            AdminRow row = new AdminRow(r.getReportId())
                    .setLeading(targetLetter(r.getTargetType()), AdminUi.KIND_DANGER)
                    .setTitle(r.getReason())
                    .setSubtitle(AdminLabels.targetType(r.getTargetType()) + " · " + targetName(r)
                            + "\nNgười báo cáo: " + repository.getUserName(r.getReporterId()) + " · " + r.getCreatedAt())
                    .setChip(AdminLabels.report(s));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getDisputeRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        for (Dispute d : repository.getDisputes()) {
            String s = d.getStatus();
            boolean keep;
            switch (filter) {
                case 0: keep = Dispute.OPEN.equals(s); break;
                case 1: keep = Dispute.WAITING_EVIDENCE.equals(s); break;
                case 2: keep = Dispute.RESOLVED.equals(s); break;
                default: keep = true;
            }
            if (!keep) continue;
            Order o = repository.getOrder(d.getOrderId());
            Transaction t = repository.getTransactionByOrder(d.getOrderId());
            AdminRow row = new AdminRow(d.getDisputeId())
                    .setLeading("KN", AdminUi.KIND_WARN)
                    .setTitle(d.getDisputeId() + " · " + (o == null ? "" : repository.getPostTitle(o.getPostId())))
                    .setSubtitle(d.getReason() + "\nĐơn " + d.getOrderId())
                    .setChip(AdminLabels.dispute(s))
                    .setChip2(t == null ? null : AdminLabels.transaction(t.getStatus()))
                    .setTrailing(t == null ? "" : AdminUi.money(t.getAmount()));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getVerificationRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        int index = 0;
        for (VerificationRequest v : repository.getVerifications()) {
            index++;
            String s = v.getStatus();
            boolean keep;
            switch (filter) {
                case 0: keep = VerificationRequest.PENDING.equals(s); break;
                case 1: keep = VerificationRequest.APPROVED.equals(s); break;
                case 2: keep = VerificationRequest.REJECTED.equals(s); break;
                default: keep = true;
            }
            if (!keep) continue;
            String name = repository.getUserName(v.getUserId());
            AdminRow row = new AdminRow(v.getRequestId())
                    .setLeading(AdminUi.initials(name), AdminUi.avatarKind(index))
                    .setTitle(name)
                    .setSubtitle(v.getRequestType() + " · " + v.getDocumentType() + "\n" + v.getCreatedAt())
                    .setChip(AdminLabels.verificationRequest(s))
                    // App chỉ xác minh email (SĐT không bắt buộc, OTP SMS đã tắt), nên nhãn phụ là trạng thái email.
                    .setChip2(isEmailVerified(v.getUserId())
                            ? new AdminLabels.Label("Email đã xác thực", AdminUi.KIND_OK)
                            : new AdminLabels.Label("Email chưa xác thực", AdminUi.KIND_WARN));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    public List<AdminRow> getTransactionRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        String[] codes = {null, Transaction.HOLDING, Transaction.RELEASED, Transaction.REFUNDED, Transaction.DISPUTED};
        for (Transaction t : repository.getTransactions()) {
            if (filter > 0 && !codes[filter].equals(t.getStatus())) continue;
            AdminLabels.Label label = AdminLabels.transaction(t.getStatus());
            AdminRow row = new AdminRow(t.getTransactionId())
                    .setLeading("₫", label.kind)
                    .setTitle(t.getTransactionId())
                    .setSubtitle("Đơn " + t.getOrderId() + " · " + t.getCreatedAt())
                    .setChip(label)
                    .setTrailing(AdminUi.money(t.getAmount()));
            addIfMatch(rows, row, query);
        }
        return rows;
    }

    /** Thông báo quản trị: gom các việc đang chờ. id dạng "loại:mã" để biết mở màn nào. */
    public List<AdminRow> getNotificationRows(int filter, String query) {
        List<AdminRow> rows = new ArrayList<>();
        if (filter == 0 || filter == 1) {
            for (Report r : repository.getReports()) {
                if (!r.isOpen()) continue;
                addIfMatch(rows, new AdminRow("report:" + r.getReportId())
                        .setLeading(targetLetter(r.getTargetType()), AdminUi.KIND_DANGER)
                        .setTitle("Báo cáo mới: " + r.getReason())
                        .setSubtitle(AdminLabels.targetType(r.getTargetType()) + " · " + targetName(r) + "\n" + r.getCreatedAt())
                        .setChip(AdminLabels.report(r.getStatus())), query);
            }
        }
        if (filter == 0 || filter == 2) {
            for (Dispute d : repository.getDisputes()) {
                if (!d.isOpen()) continue;
                addIfMatch(rows, new AdminRow("dispute:" + d.getDisputeId())
                        .setLeading("KN", AdminUi.KIND_WARN)
                        .setTitle("Khiếu nại: " + d.getReason())
                        .setSubtitle("Đơn " + d.getOrderId() + "\n" + d.getCreatedAt())
                        .setChip(AdminLabels.dispute(d.getStatus())), query);
            }
        }
        if (filter == 0 || filter == 3) {
            for (VerificationRequest v : repository.getVerifications()) {
                if (!VerificationRequest.PENDING.equals(v.getStatus())) continue;
                String name = repository.getUserName(v.getUserId());
                addIfMatch(rows, new AdminRow("verification:" + v.getRequestId())
                        .setLeading(AdminUi.initials(name), AdminUi.KIND_INFO)
                        .setTitle("Hồ sơ xác minh: " + name)
                        .setSubtitle(v.getRequestType() + "\n" + v.getCreatedAt())
                        .setChip(AdminLabels.verificationRequest(v.getStatus())), query);
            }
        }
        return rows;
    }

    /** Số liệu cho 4 ô tổng hợp ở màn Theo dõi giao dịch: đang giữ, đã giải ngân, đã hoàn tiền, tranh chấp. */
    public long[] getTransactionTotals() {
        long[] totals = new long[4];
        String[] codes = {Transaction.HOLDING, Transaction.RELEASED, Transaction.REFUNDED, Transaction.DISPUTED};
        for (Transaction t : repository.getTransactions()) {
            for (int i = 0; i < codes.length; i++) if (codes[i].equals(t.getStatus())) totals[i] += t.getAmount();
        }
        return totals;
    }

    // ---------------------------------------------------------------- Chi tiết

    public User getUser(String id) { return repository.getUser(id); }
    public Post getPost(String id) { return repository.getPost(id); }
    public Order getOrder(String id) { return repository.getOrder(id); }
    public Transaction getTransaction(String id) { return repository.getTransaction(id); }
    public Transaction getTransactionByOrder(String orderId) { return repository.getTransactionByOrder(orderId); }
    public Report getReport(String id) { return repository.getReport(id); }
    public Dispute getDispute(String id) { return repository.getDispute(id); }
    public Dispute getDisputeByOrder(String orderId) { return repository.getDisputeByOrder(orderId); }
    public VerificationRequest getVerification(String id) { return repository.getVerification(id); }
    public String getUserName(String userId) { return repository.getUserName(userId); }
    public String getPostTitle(String postId) { return repository.getPostTitle(postId); }

    // ---------------------------------------------------------------- Ảnh và dữ liệu gốc

    public List<String> getPostImages(String postId) { return repository.getStrings("posts", postId, "imageUrls"); }

    public String getPostCover(String postId) {
        List<String> images = getPostImages(postId);
        return images.isEmpty() ? "" : images.get(0);
    }

    /** Đường dẫn 3 ảnh hồ sơ xác minh theo thứ tự: chân dung, mặt trước, mặt sau (rỗng nếu thiếu). */
    public String[] getVerificationEvidence(String requestId) {
        String[] slots = {"selfie", "front", "back"}, paths = {"", "", ""};
        for (String path : repository.getStrings("verificationRequests", requestId, "evidencePaths")) {
            String file = path.substring(path.lastIndexOf('/') + 1);
            for (int i = 0; i < slots.length; i++) if (file.startsWith(slots[i] + "-")) paths[i] = path;
        }
        return paths;
    }

    public String getVerificationField(String requestId, String field) {
        return repository.getField("verificationRequests", requestId, field);
    }

    public boolean isEmailVerified(String userId) { return "true".equals(repository.getField("users", userId, "emailVerified")); }

    public com.google.android.gms.tasks.Task<byte[]> loadImage(String collection, String id, String path) {
        return repository.loadImage(collection, id, path);
    }

    public String getTargetName(Report report) { return targetName(report); }

    // ---------------------------------------------------------------- Thao tác

    public void setUserLocked(String userId, boolean locked) { repository.setUserLocked(userId, locked); }

    public void setPostStatus(String postId, String status) { repository.setPostStatus(postId, status); }

    public void sendWarning(String userId, String text) { repository.sendWarning(userId, text); }

    public boolean isSupportTicket(String reportId) { return repository.isSupportTicket(reportId); }

    public void rejectReport(String reportId) {
        repository.setReportStatus(reportId, Report.REJECTED, "Bác bỏ báo cáo sau khi xem xét");
    }

    /** Xác nhận vi phạm: xử lý đối tượng bị báo cáo, thành công mới đóng báo cáo (làm ở Repository). */
    public void acceptReport(Report report) { repository.acceptReport(report); }

    public void requestDisputeEvidence(String disputeId) { repository.requestDisputeEvidence(disputeId); }

    public void resolveDispute(String disputeId, boolean refundBuyer) {
        repository.resolveDispute(disputeId, refundBuyer);
    }

    public void reviewVerification(String requestId, boolean approve, String note) {
        repository.reviewVerification(requestId, approve, note);
    }

    // ---------------------------------------------------------------- Cài đặt

    public List<Category> getCategories() { return repository.getCategories(); }
    public void setCategoryEnabled(String id, boolean enabled) { repository.setCategoryEnabled(id, enabled); }
    public void addCategory(String name) { repository.addCategory(name); }
    public boolean isAutoHideReported() { return repository.isAutoHideReported(); }
    public int getReportThreshold() { return repository.getReportThreshold(); }
    public int getAutoCompleteDays() { return repository.getAutoCompleteDays(); }
    public long getMaxPrice() { return repository.getMaxPrice(); }
    public boolean isRequirePhoneBeforeSelling() { return repository.isRequirePhoneBeforeSelling(); }

    public void saveSettings(boolean autoHide, int threshold, int days, long maxPrice, boolean requirePhone,
                             Runnable onSaved) {
        repository.saveSettings(autoHide, threshold, days, maxPrice, requirePhone, onSaved);
    }

    public boolean isSimulateError() { return repository.isSimulateError(); }
    public void setSimulateError(boolean value) { repository.setSimulateError(value); }

    // ---------------------------------------------------------------- Hàm phụ

    public String partiesText(Order o) {
        String buyer = repository.getUserName(o.getBuyerId());
        String seller = repository.getUserName(o.getSellerId());
        return o.isFree() ? "Người cho: " + seller + " · Người nhận: " + buyer
                : "Người mua: " + buyer + " · Người bán: " + seller;
    }

    public static String categoryEmoji(String category) {
        switch (category) {
            case "Quần": return "👖";
            case "Váy": return "👗";
            case "Áo khoác": return "🧥";
            default: return "👕";
        }
    }

    private String targetName(Report r) {
        switch (r.getTargetType()) {
            case Report.TARGET_USER: return repository.getUserName(r.getTargetId());
            case Report.TARGET_ORDER: return "Đơn " + r.getTargetId();
            default: return repository.getPostTitle(r.getTargetId());
        }
    }

    private static String targetLetter(String targetType) {
        switch (targetType) {
            case Report.TARGET_USER: return "ND";
            case Report.TARGET_ORDER: return "ĐH";
            default: return "BĐ";
        }
    }

    private static void addIfMatch(List<AdminRow> rows, AdminRow row, String query) {
        String q = AdminUi.normalize(query);
        if (q.isEmpty() || row.searchText().contains(q)) rows.add(row);
    }
}