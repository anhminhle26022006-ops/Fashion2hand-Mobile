package vn.edu.ueh.thudinh.fashion2hand.admin;

import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.model.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.VerificationRequest;
import vn.edu.ueh.thudinh.fashion2hand.repository.AdminRepository;

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

    // ---------------------------------------------------------------- Đăng nhập

    public String getRoleByEmail(String email) { return repository.getRoleByEmail(email); }

    public boolean checkAdminPassword(String email, String password) {
        return repository.checkAdminPassword(email, password);
    }

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

    private String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(new Date());
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
                    .setChip2(v.isPhoneVerified()
                            ? new AdminLabels.Label("OTP đã xác thực", AdminUi.KIND_OK)
                            : new AdminLabels.Label("Chưa xác thực SĐT", AdminUi.KIND_WARN));
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

    public String getTargetName(Report report) { return targetName(report); }

    // ---------------------------------------------------------------- Thao tác

    public void setUserLocked(String userId, boolean locked) { repository.setUserLocked(userId, locked); }

    public void setPostStatus(String postId, String status) { repository.setPostStatus(postId, status); }

    public void rejectReport(String reportId) { repository.setReportStatus(reportId, Report.REJECTED); }

    /** Xác nhận vi phạm: xử lý đối tượng bị báo cáo rồi đóng báo cáo. */
    public void acceptReport(Report report) {
        if (Report.TARGET_POST.equals(report.getTargetType())) {
            repository.setPostStatus(report.getTargetId(), Post.STATUS_HIDDEN);
        } else if (Report.TARGET_USER.equals(report.getTargetType())) {
            repository.setUserLocked(report.getTargetId(), true);
        }
        repository.setReportStatus(report.getReportId(), Report.RESOLVED);
    }

    public void requestDisputeEvidence(String disputeId) { repository.requestDisputeEvidence(disputeId); }

    public void resolveDispute(String disputeId, boolean refundBuyer) {
        repository.resolveDispute(disputeId, refundBuyer, now());
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

    public void saveSettings(boolean autoHide, int threshold, int days, long maxPrice, boolean requirePhone) {
        repository.saveSettings(autoHide, threshold, days, maxPrice, requirePhone);
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
