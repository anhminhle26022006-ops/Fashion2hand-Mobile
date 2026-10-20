package vn.edu.ueh.thudinh.fashion2hand.repository;

import java.util.ArrayList;
import java.util.List;

import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.model.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.VerificationRequest;

/**
 * Nguồn dữ liệu cho khu vực Admin.
 * Giai đoạn frontend: dùng dữ liệu giả lưu trong bộ nhớ.
 * Giai đoạn backend (18/10): thay phần thân các hàm bằng truy vấn Firestore,
 * giữ nguyên tên hàm để ViewModel và giao diện không phải sửa.
 */
public class AdminRepository {

    public static final String ADMIN_EMAIL = "admin@fashion2hand.com";
    private static final String ADMIN_DEMO_PASSWORD = "admin123";

    private static AdminRepository instance;

    private final List<User> users = new ArrayList<>();
    private final List<Post> posts = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<Dispute> disputes = new ArrayList<>();
    private final List<VerificationRequest> verifications = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();

    // Cài đặt hệ thống
    private boolean autoHideReported = true;
    private int reportThreshold = 5;
    private int autoCompleteDays = 3;
    private long maxPrice = 2_000_000;
    private boolean requirePhoneBeforeSelling = true;

    // Chế độ demo: giả lập lỗi tải dữ liệu để xem trạng thái lỗi
    private boolean simulateError = false;

    public static synchronized AdminRepository getInstance() {
        if (instance == null) instance = new AdminRepository();
        return instance;
    }

    private AdminRepository() {
        seedUsers();
        seedPosts();
        seedOrders();
        seedReports();
        seedDisputes();
        seedVerifications();
        seedCategories();
    }

    // ---------------------------------------------------------------- Đăng nhập

    public String getRoleByEmail(String email) {
        User user = findUserByEmail(email);
        return user == null ? User.ROLE_USER : user.getRole();
    }

    public boolean checkAdminPassword(String email, String password) {
        return ADMIN_EMAIL.equalsIgnoreCase(email.trim()) && ADMIN_DEMO_PASSWORD.equals(password);
    }

    public boolean isAdminEmail(String email) {
        return email != null && ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    public User findUserByEmail(String email) {
        for (User u : users) if (u.getEmail().equalsIgnoreCase(email.trim())) return u;
        return null;
    }

    // ---------------------------------------------------------------- Đọc dữ liệu

    public List<User> getUsers() { return users; }
    public List<Post> getPosts() { return posts; }
    public List<Order> getOrders() { return orders; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<Report> getReports() { return reports; }
    public List<Dispute> getDisputes() { return disputes; }
    public List<VerificationRequest> getVerifications() { return verifications; }
    public List<Category> getCategories() { return categories; }

    public User getUser(String userId) {
        for (User u : users) if (u.getUserId().equals(userId)) return u;
        return null;
    }

    public Post getPost(String postId) {
        for (Post p : posts) if (p.getPostId().equals(postId)) return p;
        return null;
    }

    public Order getOrder(String orderId) {
        for (Order o : orders) if (o.getOrderId().equals(orderId)) return o;
        return null;
    }

    public Transaction getTransaction(String transactionId) {
        for (Transaction t : transactions) if (t.getTransactionId().equals(transactionId)) return t;
        return null;
    }

    public Transaction getTransactionByOrder(String orderId) {
        for (Transaction t : transactions) if (t.getOrderId().equals(orderId)) return t;
        return null;
    }

    public Report getReport(String reportId) {
        for (Report r : reports) if (r.getReportId().equals(reportId)) return r;
        return null;
    }

    public Dispute getDispute(String disputeId) {
        for (Dispute d : disputes) if (d.getDisputeId().equals(disputeId)) return d;
        return null;
    }

    public Dispute getDisputeByOrder(String orderId) {
        for (Dispute d : disputes) if (d.getOrderId().equals(orderId)) return d;
        return null;
    }

    public VerificationRequest getVerification(String requestId) {
        for (VerificationRequest v : verifications) if (v.getRequestId().equals(requestId)) return v;
        return null;
    }

    public String getUserName(String userId) {
        User u = getUser(userId);
        return u == null ? "Không rõ" : u.getName();
    }

    public String getPostTitle(String postId) {
        Post p = getPost(postId);
        return p == null ? "Sản phẩm đã xóa" : p.getTitle();
    }

    public int countOpenReports() {
        int n = 0;
        for (Report r : reports) if (r.isOpen()) n++;
        return n;
    }

    public int countOpenDisputes() {
        int n = 0;
        for (Dispute d : disputes) if (d.isOpen()) n++;
        return n;
    }

    public int countPendingVerifications() {
        int n = 0;
        for (VerificationRequest v : verifications) if (VerificationRequest.PENDING.equals(v.getStatus())) n++;
        return n;
    }

    // ---------------------------------------------------------------- Thao tác Admin

    public void setUserLocked(String userId, boolean locked) {
        User u = getUser(userId);
        if (u != null) u.setAccountStatus(locked ? User.ACCOUNT_LOCKED : User.ACCOUNT_ACTIVE);
    }

    public void setPostStatus(String postId, String status) {
        Post p = getPost(postId);
        if (p != null) p.setStatus(status);
    }

    public void setReportStatus(String reportId, String status) {
        Report r = getReport(reportId);
        if (r != null) r.setStatus(status);
    }

    public void requestDisputeEvidence(String disputeId) {
        Dispute d = getDispute(disputeId);
        if (d != null) d.setStatus(Dispute.WAITING_EVIDENCE);
    }

    /** Xử lý khiếu nại: hoàn tiền cho người mua hoặc giải ngân cho người bán. */
    public void resolveDispute(String disputeId, boolean refundBuyer, String now) {
        Dispute d = getDispute(disputeId);
        if (d == null) return;
        d.setStatus(Dispute.RESOLVED);
        d.setResolution(refundBuyer ? Dispute.RESOLUTION_REFUND : Dispute.RESOLUTION_RELEASE);

        Transaction t = getTransactionByOrder(d.getOrderId());
        if (t != null) {
            t.setStatus(refundBuyer ? Transaction.REFUNDED : Transaction.RELEASED);
            t.setReleasedAt(now);
        }
        Order o = getOrder(d.getOrderId());
        if (o != null) {
            o.setOrderStatus(refundBuyer ? Order.STATUS_REFUNDED : Order.STATUS_COMPLETED);
            if (refundBuyer) o.setPaymentStatus(Order.PAYMENT_REFUNDED);
        }
    }

    public void reviewVerification(String requestId, boolean approve, String note) {
        VerificationRequest v = getVerification(requestId);
        if (v == null) return;
        v.setStatus(approve ? VerificationRequest.APPROVED : VerificationRequest.REJECTED);
        v.setNote(note);
        User u = getUser(v.getUserId());
        if (u != null) u.setVerificationStatus(approve ? User.VERIFIED : User.UNVERIFIED);
    }

    public void setCategoryEnabled(String categoryId, boolean enabled) {
        for (Category c : categories) if (c.getCategoryId().equals(categoryId)) c.setEnabled(enabled);
    }

    public void addCategory(String name) {
        categories.add(new Category("c" + (categories.size() + 1), name, 0, true));
    }

    // ---------------------------------------------------------------- Cài đặt

    public boolean isAutoHideReported() { return autoHideReported; }
    public int getReportThreshold() { return reportThreshold; }
    public int getAutoCompleteDays() { return autoCompleteDays; }
    public long getMaxPrice() { return maxPrice; }
    public boolean isRequirePhoneBeforeSelling() { return requirePhoneBeforeSelling; }

    public void saveSettings(boolean autoHide, int threshold, int days, long maxPrice, boolean requirePhone) {
        this.autoHideReported = autoHide;
        this.reportThreshold = threshold;
        this.autoCompleteDays = days;
        this.maxPrice = maxPrice;
        this.requirePhoneBeforeSelling = requirePhone;
    }

    public boolean isSimulateError() { return simulateError; }
    public void setSimulateError(boolean simulateError) { this.simulateError = simulateError; }

    // ---------------------------------------------------------------- Dữ liệu giả

    private void seedUsers() {
        users.add(new User("u00", "Quản trị viên", ADMIN_EMAIL, "0900000000", User.ROLE_ADMIN,
                User.VERIFIED, User.ACCOUNT_ACTIVE, "TP.HCM", "01/09/2026", 0, 0, 0, 0));
        users.add(new User("u01", "Trần Bảo Ngọc", "ngoc.tran@gmail.com", "0901234567", User.ROLE_USER,
                User.VERIFIED, User.ACCOUNT_ACTIVE, "Quận 1, TP.HCM", "12/08/2026", 8, 5, 3, 0));
        users.add(new User("u02", "Lê Hoàng Phúc", "phuc.le@gmail.com", "0907654321", User.ROLE_USER,
                User.VERIFIED, User.ACCOUNT_ACTIVE, "Quận 3, TP.HCM", "02/09/2026", 14, 6, 9, 1));
        users.add(new User("u03", "Nguyễn Thu Vy", "vy.nguyen@gmail.com", "0981112105", User.ROLE_USER,
                User.VERIFY_PENDING, User.ACCOUNT_ACTIVE, "Thủ Đức, TP.HCM", "20/09/2026", 3, 4, 0, 0));
        users.add(new User("u04", "Phạm Gia Khang", "khang.pham@gmail.com", "0912345678", User.ROLE_USER,
                User.UNVERIFIED, User.ACCOUNT_LOCKED, "Bình Thạnh, TP.HCM", "05/09/2026", 2, 1, 1, 3));
        users.add(new User("u05", "Đỗ Minh Hằng", "hang.do@gmail.com", "0938765432", User.ROLE_USER,
                User.VERIFIED, User.ACCOUNT_ACTIVE, "Gò Vấp, TP.HCM", "28/08/2026", 11, 2, 7, 0));
        users.add(new User("u06", "Võ Thanh Tùng", "tung.vo@gmail.com", "0919998478", User.ROLE_USER,
                User.VERIFY_PENDING, User.ACCOUNT_ACTIVE, "Quận 7, TP.HCM", "01/10/2026", 5, 1, 2, 1));
        users.add(new User("u07", "Hồ Ngọc Lan", "lan.ho@gmail.com", "0977001122", User.ROLE_USER,
                User.VERIFIED, User.ACCOUNT_ACTIVE, "Phú Nhuận, TP.HCM", "15/09/2026", 6, 3, 2, 0));
    }

    private void seedPosts() {
        posts.add(new Post("p01", "u02", "Áo khoác denim oversize", "Áo khoác", "M", "Tốt", "Quận 3, TP.HCM",
                Post.TYPE_FOR_SALE, 120_000, Post.STATUS_ACTIVE, 3,
                "Hình ảnh không đúng thực tế (2)\nNghi ngờ lừa đảo (1)", "12/10/2026"));
        posts.add(new Post("p02", "u01", "Áo thun basic", "Áo", "M", "Tốt", "Quận 1, TP.HCM",
                Post.TYPE_FREE, 0, Post.STATUS_GIVEN, 0, "", "08/10/2026"));
        posts.add(new Post("p03", "u05", "Váy hoa vintage", "Váy", "S", "Rất tốt", "Gò Vấp, TP.HCM",
                Post.TYPE_FOR_SALE, 85_000, Post.STATUS_ACTIVE, 0, "", "06/10/2026"));
        posts.add(new Post("p04", "u03", "Quần jeans ống rộng", "Quần", "L", "Tốt", "Thủ Đức, TP.HCM",
                Post.TYPE_FREE, 0, Post.STATUS_ACTIVE, 0, "", "11/10/2026"));
        posts.add(new Post("p05", "u06", "Áo len cổ lọ", "Áo", "M", "Như mới", "Quận 7, TP.HCM",
                Post.TYPE_FOR_SALE, 60_000, Post.STATUS_HIDDEN, 1, "Nội dung vi phạm (1)", "03/10/2026"));
        posts.add(new Post("p06", "u07", "Chân váy xếp ly", "Váy", "S", "Tốt", "Phú Nhuận, TP.HCM",
                Post.TYPE_FOR_SALE, 70_000, Post.STATUS_ACTIVE, 0, "", "10/10/2026"));
        posts.add(new Post("p07", "u04", "Áo sơ mi linen trắng", "Áo", "L", "Tốt", "Bình Thạnh, TP.HCM",
                Post.TYPE_FREE, 0, Post.STATUS_ACTIVE, 1, "Spam bài đăng trùng lặp (1)", "13/10/2026"));
    }

    private void seedOrders() {
        orders.add(new Order("F2H-10234", Order.TYPE_PAID, "u01", "u02", "p01", 120_000, 25_000,
                Order.STATUS_SHIPPING, Order.PAYMENT_PAID, Order.SHIPPING_IN_TRANSIT, "GHN-88213", "12/10/2026 09:12"));
        orders.add(new Order("F2H-10231", Order.TYPE_FREE, "u03", "u01", "p02", 0, 25_000,
                Order.STATUS_COMPLETED, Order.PAYMENT_NOT_REQUIRED, Order.SHIPPING_DELIVERED, "GHN-87990", "10/10/2026 16:30"));
        orders.add(new Order("F2H-10229", Order.TYPE_PAID, "u07", "u05", "p03", 85_000, 25_000,
                Order.STATUS_DISPUTED, Order.PAYMENT_PAID, Order.SHIPPING_DELIVERED, "GHN-87512", "09/10/2026 11:05"));
        orders.add(new Order("F2H-10226", Order.TYPE_PAID, "u06", "u07", "p06", 70_000, 25_000,
                Order.STATUS_PENDING, Order.PAYMENT_PAID, Order.SHIPPING_READY, "", "14/10/2026 20:41"));
        orders.add(new Order("F2H-10220", Order.TYPE_FREE, "u04", "u03", "p04", 0, 22_000,
                Order.STATUS_CANCELLED, Order.PAYMENT_NOT_REQUIRED, Order.SHIPPING_READY, "", "08/10/2026 08:15"));
        orders.add(new Order("F2H-10215", Order.TYPE_PAID, "u01", "u06", "p05", 60_000, 25_000,
                Order.STATUS_COMPLETED, Order.PAYMENT_PAID, Order.SHIPPING_DELIVERED, "GHN-86044", "05/10/2026 19:02"));
        orders.add(new Order("F2H-10210", Order.TYPE_PAID, "u02", "u07", "p06", 45_000, 15_000,
                Order.STATUS_REFUNDED, Order.PAYMENT_REFUNDED, Order.SHIPPING_RETURNED, "GHN-85520", "03/10/2026 10:26"));

        transactions.add(new Transaction("TX-50231", "F2H-10234", 145_000, Transaction.HOLDING, "12/10/2026 09:15", ""));
        transactions.add(new Transaction("TX-50228", "F2H-10229", 110_000, Transaction.DISPUTED, "09/10/2026 11:07", ""));
        transactions.add(new Transaction("TX-50226", "F2H-10226", 95_000, Transaction.HOLDING, "14/10/2026 20:43", ""));
        transactions.add(new Transaction("TX-50215", "F2H-10215", 85_000, Transaction.RELEASED, "05/10/2026 19:04", "08/10/2026 21:10"));
        transactions.add(new Transaction("TX-50210", "F2H-10210", 60_000, Transaction.REFUNDED, "03/10/2026 10:28", "06/10/2026 14:00"));
    }

    private void seedReports() {
        reports.add(new Report("BC-0087", "u01", Report.TARGET_POST, "p01", "Hình ảnh không đúng thực tế",
                "Ảnh trong bài là ảnh mạng, không phải ảnh chụp sản phẩm thật. Mình hỏi qua chat thì người bán không gửi được ảnh thật.",
                Report.PENDING, "15/10/2026 00:12"));
        reports.add(new Report("BC-0086", "u03", Report.TARGET_USER, "u04", "Có hành vi đáng ngờ",
                "Người này nhắn tin yêu cầu chuyển khoản trước, ngoài ứng dụng.",
                Report.PENDING, "14/10/2026 21:40"));
        reports.add(new Report("BC-0085", "u07", Report.TARGET_ORDER, "F2H-10229", "Hàng giao không đúng mô tả",
                "Váy bị rách ở tay áo, bài đăng ghi tình trạng Rất tốt.",
                Report.REVIEWING, "14/10/2026 18:05"));
        reports.add(new Report("BC-0083", "u05", Report.TARGET_POST, "p05", "Nội dung vi phạm",
                "Mô tả có từ ngữ không phù hợp.", Report.RESOLVED, "13/10/2026 09:30"));
        reports.add(new Report("BC-0080", "u04", Report.TARGET_USER, "u06", "Spam tin nhắn",
                "Gửi cùng một tin nhắn quảng cáo nhiều lần.", Report.REJECTED, "12/10/2026 15:20"));
    }

    private void seedDisputes() {
        disputes.add(new Dispute("KN-0031", "F2H-10229", "u07", "Hàng không đúng mô tả",
                "Váy bị rách ở phần tay, trong bài đăng ghi tình trạng Rất tốt.",
                "Váy còn nguyên khi gửi, mình có video đóng gói.",
                Dispute.OPEN, "", "14/10/2026 18:10"));
        disputes.add(new Dispute("KN-0030", "F2H-10234", "u01", "Chưa nhận được hàng",
                "Đã quá ngày giao dự kiến 2 ngày nhưng chưa nhận được hàng.",
                "Mình đã giao cho đơn vị vận chuyển ngày 12/10.",
                Dispute.WAITING_EVIDENCE, "", "15/10/2026 08:00"));
        disputes.add(new Dispute("KN-0025", "F2H-10210", "u02", "Sản phẩm bị lỗi",
                "Chân váy bị bung chỉ ở đường may.",
                "Đồng ý nhận lại hàng.",
                Dispute.RESOLVED, Dispute.RESOLUTION_REFUND, "04/10/2026 13:45"));
    }

    private void seedVerifications() {
        verifications.add(new VerificationRequest("VER-0128", "u03", "Xác minh tài khoản", "CCCD gắn chip",
                true, VerificationRequest.PENDING, "15/10/2026 00:40", ""));
        verifications.add(new VerificationRequest("VER-0127", "u06", "Xác minh người bán", "CCCD gắn chip",
                true, VerificationRequest.PENDING, "14/10/2026 22:15", ""));
        verifications.add(new VerificationRequest("VER-0120", "u01", "Xác minh tài khoản", "Hộ chiếu",
                true, VerificationRequest.APPROVED, "10/10/2026 10:00", ""));
        verifications.add(new VerificationRequest("VER-0118", "u04", "Xác minh người bán", "CCCD gắn chip",
                false, VerificationRequest.REJECTED, "07/10/2026 17:30", "Ảnh giấy tờ bị mờ"));
    }

    private void seedCategories() {
        categories.add(new Category("c1", "Áo", 1210, true));
        categories.add(new Category("c2", "Quần", 864, true));
        categories.add(new Category("c3", "Váy", 642, true));
        categories.add(new Category("c4", "Áo khoác", 498, true));
        categories.add(new Category("c5", "Phụ kiện", 298, false));
    }
}
