package vn.edu.ueh.thudinh.fashion2hand.repository.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.VerificationRequest;

/**
 * Nguồn dữ liệu khu vực Admin, lấy từ Cloud Functions (adminList, adminSettings, adminMutate).
 * Giữ bản sao trong bộ nhớ để ViewModel và giao diện đọc đồng bộ như trước.
 * Mọi thay đổi chỉ được coi là thành công sau khi backend xác nhận, rồi tải lại dữ liệu.
 */
public class AdminRepository {

    public static final String ADMIN_EMAIL = "admin@fashion2hand.test";

    private static final String[] COLLECTIONS = {
            "users", "posts", "orders", "reports", "disputes", "verificationRequests", "moneyTransactions", "categories"};
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;
    private static final long STALE_AFTER_MS = 10_000;

    /** Sự kiện chỉ xử lý một lần (thông báo, mất quyền) để màn quay lại không hiện lặp. */
    public static class Event<T> {
        private final T value;
        private boolean handled;
        public Event(T value) { this.value = value; }
        public T take() {
            if (handled) return null;
            handled = true;
            return value;
        }
    }

    private static AdminRepository instance;

    private final List<User> users = new ArrayList<>();
    private final List<Post> posts = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<Dispute> disputes = new ArrayList<>();
    private final List<VerificationRequest> verifications = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final Map<String, Long> createdMillis = new HashMap<>();
    private final Map<String, Map<String, Object>> rawUsers = new HashMap<>();
    private final Map<String, Map<String, Object>> rawRecords = new HashMap<>();
    /** Ảnh đã tải (bài đăng, hồ sơ xác minh), giữ tối đa 60 ảnh gần nhất để cuộn danh sách không tải lại. */
    private final Map<String, byte[]> imageCache = new java.util.LinkedHashMap<String, byte[]>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) { return size() > 60; }
    };
    private final java.util.Set<String> supportTickets = new java.util.HashSet<>();

    private final MutableLiveData<Integer> dataVersion = new MutableLiveData<>(0);
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> sessionDenied = new MutableLiveData<>();

    private boolean autoHideReported;
    private int reportThreshold = 5;
    private int autoCompleteDays = 3;
    private long maxPrice = 2_000_000;
    private boolean requirePhoneBeforeSelling;
    private long shippingFee = 15_000;
    private long settingsRevision;

    private boolean loaded;
    private boolean loading;
    private Exception loadError;
    private boolean simulateError;
    private long loadedAt;
    private String loadedUid = "";
    private long generation;

    public static synchronized AdminRepository getInstance() {
        if (instance == null) instance = new AdminRepository();
        return instance;
    }

    private AdminRepository() { }

    // ---------------------------------------------------------------- Trạng thái cho giao diện

    public LiveData<Integer> getDataVersion() { return dataVersion; }
    public LiveData<Event<String>> getMessage() { return message; }
    public LiveData<Event<Boolean>> getSessionDenied() { return sessionDenied; }
    public boolean isLoaded() { return loaded; }
    public boolean isLoading() { return loading && !loaded; }
    public boolean hasLoadError() { return simulateError || (loadError != null && !loading); }
    public boolean isSimulateError() { return hasLoadError(); }
    public void setSimulateError(boolean value) { simulateError = value; changed(); }

    public boolean isAdminEmail(String email) {
        return email != null && ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    public String getAdminEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }

    // ---------------------------------------------------------------- Tải dữ liệu

    public void refreshIfStale() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user == null ? "" : user.getUid();
        // Đổi tài khoản thì bỏ dữ liệu của phiên trước, không cho người sau thấy.
        if (!uid.equals(loadedUid)) clear();
        if (!loaded || System.currentTimeMillis() - loadedAt > STALE_AFTER_MS) refresh();
    }

    public void refresh() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            denySession();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        long token = ++generation;
        loading = true;
        changed();

        List<Task<List<Map<String, Object>>>> pages = new ArrayList<>();
        for (String collection : COLLECTIONS) pages.add(loadCollection(collection, "", new ArrayList<>(), 0));
        Task<Map<String, Object>> settings = call("adminSettings", new HashMap<>());

        List<Task<?>> all = new ArrayList<>(pages);
        all.add(settings);
        Tasks.whenAllComplete(all).addOnCompleteListener(done -> {
            if (token != generation) return;
            loading = false;
            for (Task<?> task : all) {
                if (!task.isSuccessful()) {
                    Exception error = task.getException();
                    if (isPermissionError(error)) {
                        denySession();
                    } else {
                        loadError = error;
                        changed();
                    }
                    return;
                }
            }
            Map<String, List<Map<String, Object>>> records = new HashMap<>();
            for (int i = 0; i < COLLECTIONS.length; i++) records.put(COLLECTIONS[i], pages.get(i).getResult());
            apply(records, settings.getResult());
            loadError = null;
            loaded = true;
            loadedAt = System.currentTimeMillis();
            loadedUid = uid;
            changed();
        });
    }

    private Task<List<Map<String, Object>>> loadCollection(String collection, String cursor,
                                                           List<Map<String, Object>> collected, int page) {
        Map<String, Object> data = new HashMap<>();
        data.put("collection", collection);
        data.put("limit", PAGE_SIZE);
        if (!cursor.isEmpty()) data.put("cursor", cursor);
        return call("adminList", data).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            Map<String, Object> result = task.getResult();
            Object list = result.get("records");
            if (list instanceof List) for (Object item : (List<?>) list) collected.add(map(item));
            String next = str(result, "nextCursor");
            if (next.isEmpty() || page + 1 >= MAX_PAGES) return Tasks.forResult(collected);
            return loadCollection(collection, next, collected, page + 1);
        });
    }

    private Task<Map<String, Object>> call(String name, Map<String, Object> data) {
        return FirebaseFunctions.getInstance().getHttpsCallable(name).call(data).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return map(task.getResult().getData());
        });
    }

    /** Package-private để unit test nạp dữ liệu mẫu mà không cần gọi backend. */
    void apply(Map<String, List<Map<String, Object>>> records, Map<String, Object> config) {
        users.clear(); posts.clear(); orders.clear(); transactions.clear();
        reports.clear(); disputes.clear(); verifications.clear(); categories.clear();
        createdMillis.clear(); rawUsers.clear(); rawRecords.clear(); supportTickets.clear();

        for (Map<String, Object> r : records.get("users")) rawUsers.put(str(r, "id"), r);

        Map<String, Dispute> openDisputeByOrder = new HashMap<>();
        for (Map<String, Object> r : records.get("disputes")) {
            Dispute d = mapDispute(r);
            disputes.add(d);
            if (d.isOpen()) openDisputeByOrder.put(d.getOrderId(), d);
        }
        for (Map<String, Object> r : records.get("reports")) reports.add(mapReport(r));
        for (Map<String, Object> r : records.get("orders")) orders.add(mapOrder(r, openDisputeByOrder));
        for (Map<String, Object> r : records.get("posts")) posts.add(mapPost(r));
        for (Map<String, Object> r : records.get("users")) users.add(mapUser(r));
        for (Map<String, Object> r : records.get("verificationRequests")) verifications.add(mapVerification(r));
        for (Map<String, Object> r : records.get("moneyTransactions")) {
            if (!"PAYMENT_RECEIVED".equals(str(r, "type")) && !str(r, "id").startsWith("received_")) continue;
            transactions.add(mapTransaction(r, openDisputeByOrder));
        }
        for (Map<String, Object> r : records.get("categories")) categories.add(mapCategory(r));

        users.sort(newestFirst(u -> createdMillis.get("users:" + u.getUserId())));
        posts.sort(newestFirst(p -> createdMillis.get("posts:" + p.getPostId())));
        orders.sort(newestFirst(o -> createdMillis.get("orders:" + o.getOrderId())));
        reports.sort(newestFirst(r -> createdMillis.get("reports:" + r.getReportId())));
        disputes.sort(newestFirst(d -> createdMillis.get("disputes:" + d.getDisputeId())));
        verifications.sort(newestFirst(v -> createdMillis.get("verificationRequests:" + v.getRequestId())));
        transactions.sort(newestFirst(t -> createdMillis.get("moneyTransactions:" + t.getTransactionId())));

        autoHideReported = bool(config, "autoHideReported");
        requirePhoneBeforeSelling = bool(config, "requirePhoneBeforeSelling");
        reportThreshold = (int) num(config, "reportThreshold", 5);
        autoCompleteDays = (int) num(config, "autoCompleteDays", 3);
        maxPrice = num(config, "maxPrice", 2_000_000);
        shippingFee = num(config, "shippingFee", 15_000);
        settingsRevision = num(config, "revision", 0);
    }

    // ---------------------------------------------------------------- Đổi dữ liệu backend sang model

    private User mapUser(Map<String, Object> r) {
        String id = str(r, "id");
        int postCount = 0, buyCount = 0, sellCount = 0, reportedCount = 0;
        for (Post p : posts) if (id.equals(p.getUserId())) postCount++;
        for (Order o : orders) {
            if (id.equals(o.getBuyerId())) buyCount++;
            if (id.equals(o.getSellerId())) sellCount++;
        }
        for (Report rep : reports) {
            if (supportTickets.contains(rep.getReportId())) continue;
            if (Report.TARGET_USER.equals(rep.getTargetType()) && id.equals(rep.getTargetId())) reportedCount++;
        }
        remember("users", id, r);
        String area = str(r, "region");
        if (area.isEmpty()) area = str(r, "address");
        return new User(id, or(str(r, "name"), "Thành viên"), str(r, "email"), str(r, "phone"),
                or(str(r, "role"), User.ROLE_USER), or(str(r, "verificationStatus"), User.UNVERIFIED),
                or(str(r, "accountStatus"), User.ACCOUNT_ACTIVE), or(area, "Chưa cập nhật"), date(r, "createdAt"),
                postCount, buyCount, sellCount, reportedCount);
    }

    private Post mapPost(Map<String, Object> r) {
        String id = str(r, "id");
        int reportCount = 0;
        List<String> reasons = new ArrayList<>();
        for (Report rep : reports) {
            if (Report.TARGET_POST.equals(rep.getTargetType()) && id.equals(rep.getTargetId())) {
                reportCount++;
                reasons.add("• " + rep.getReason());
            }
        }
        remember("posts", id, r);
        String owner = or(str(r, "ownerId"), str(r, "userId"));
        String type = "SALE".equals(str(r, "type")) ? Post.TYPE_FOR_SALE : Post.TYPE_FREE;
        return new Post(id, owner, or(str(r, "title"), "(Không có tiêu đề)"), or(str(r, "category"), "Khác"),
                or(str(r, "size"), "—"), conditionLabel(str(r, "condition")), or(str(r, "location"), "Chưa cập nhật"),
                type, num(r, "price", 0), str(r, "status"), reportCount, String.join("\n", reasons), date(r, "createdAt"));
    }

    private Order mapOrder(Map<String, Object> r, Map<String, Dispute> openDisputeByOrder) {
        String id = str(r, "id");
        remember("orders", id, r);
        String status = orderStatus(str(r, "orderStatus"));
        if (openDisputeByOrder.containsKey(id)) status = Order.STATUS_DISPUTED;
        String payment = str(r, "paymentStatus");
        if ("RELEASED".equals(payment)) payment = Order.PAYMENT_PAID;
        String postId = str(r, "postId");
        return new Order(id, or(str(r, "orderType"), Order.TYPE_PAID), str(r, "buyerId"), str(r, "sellerId"), postId,
                num(r, "price", 0), num(r, "shippingFee", 0), status, payment,
                str(r, "shippingStatus"), str(r, "trackingCode"), date(r, "createdAt"));
    }

    private static String orderStatus(String code) {
        switch (code) {
            case "PENDING_PAYMENT": return Order.STATUS_PENDING;
            case "PAID":
            case "PREPARING":
            case "READY_TO_SHIP": return Order.STATUS_CONFIRMED;
            case "EXPIRED":
            case "REJECTED": return Order.STATUS_CANCELLED;
            case "": return Order.STATUS_PENDING;
            default: return code;
        }
    }

    /**
     * Backend ghi nhiều trạng thái cho khoản tiền nhận (HOLDING, REVIEW_REQUIRED của VNPAY, REFUND_REVIEW_REQUIRED...).
     * Chỉ đã giải ngân và đã hoàn tiền là tiền đã rời hệ thống; mọi trạng thái còn lại là tiền vẫn đang giữ,
     * nên gom về HOLDING để ô tổng, bộ lọc và nhãn của từng dòng luôn khớp nhau.
     */
    private static String transactionStatus(String code) {
        switch (code) {
            case "RELEASED": case "PAYOUT_COMPLETED_SIMULATED": return Transaction.RELEASED;
            case "REFUNDED": return Transaction.REFUNDED;
            case "DISPUTED": return Transaction.DISPUTED;
            default: return Transaction.HOLDING;
        }
    }

    private Transaction mapTransaction(Map<String, Object> r, Map<String, Dispute> openDisputeByOrder) {
        String id = str(r, "id");
        remember("moneyTransactions", id, r);
        String orderId = str(r, "orderId");
        String status = transactionStatus(str(r, "status"));
        if (Transaction.HOLDING.equals(status) && openDisputeByOrder.containsKey(orderId)) status = Transaction.DISPUTED;
        String finished = date(r, "releasedAt");
        if (finished.isEmpty()) finished = date(r, "refundedAt");
        return new Transaction(id, orderId, num(r, "amount", 0), status, date(r, "createdAt"), finished);
    }

    private Report mapReport(Map<String, Object> r) {
        String id = str(r, "id");
        remember("reports", id, r);
        String reason = str(r, "reason");
        if ("SUPPORT".equals(str(r, "type"))) {
            supportTickets.add(id);
            reason = "Yêu cầu hỗ trợ: " + or(str(r, "title"), reason);
        }
        return new Report(id, str(r, "reporterId"), or(str(r, "targetType"), Report.TARGET_POST), str(r, "targetId"),
                or(reason, "Không ghi lý do"), or(str(r, "description"), "Người báo cáo không ghi mô tả."),
                or(str(r, "status"), Report.PENDING), date(r, "createdAt"));
    }

    private Dispute mapDispute(Map<String, Object> r) {
        String id = str(r, "id");
        remember("disputes", id, r);
        String reporter = str(r, "reporterId");
        boolean fromBuyer = reporter.isEmpty() || reporter.equals(str(r, "buyerId"));
        String description = str(r, "description");
        String buyerStatement = fromBuyer ? description : str(r, "buyerStatement");
        String sellerStatement = fromBuyer ? str(r, "sellerStatement") : description;
        String waiting = str(r, "evidenceRequest");
        if (!waiting.isEmpty()) buyerStatement = buyerStatement + "\n\nAdmin yêu cầu bổ sung: " + waiting;
        return new Dispute(id, or(str(r, "orderId"), id), reporter, disputeReason(str(r, "reason")),
                or(buyerStatement, "Chưa có ý kiến."), or(sellerStatement, "Người bán chưa phản hồi."),
                or(str(r, "status"), Dispute.OPEN), str(r, "resolution"), date(r, "createdAt"));
    }

    private VerificationRequest mapVerification(Map<String, Object> r) {
        String id = str(r, "id");
        remember("verificationRequests", id, r);
        String userId = str(r, "userId");
        Map<String, Object> user = rawUsers.get(userId);
        boolean phoneVerified = user != null && (bool(user, "phoneVerified") || !str(user, "phone").isEmpty());
        String created = date(r, "createdAt");
        if (created.isEmpty()) created = date(r, "submittedAt");
        return new VerificationRequest(id, userId, requestTypeLabel(str(r, "requestType")),
                or(str(r, "documentType"), "Chưa ghi loại giấy tờ"), phoneVerified,
                or(str(r, "status"), VerificationRequest.PENDING), created, str(r, "note"));
    }

    private Category mapCategory(Map<String, Object> r) {
        String name = str(r, "name");
        int count = 0;
        for (Post p : posts) if (name.equals(p.getCategory())) count++;
        Object enabled = r.get("enabled");
        return new Category(str(r, "id"), name, count, !Boolean.FALSE.equals(enabled));
    }

    private void remember(String collection, String id, Map<String, Object> r) {
        rawRecords.put(collection + ":" + id, r);
        Long millis = millis(r.get("createdAt"));
        if (millis == null) millis = millis(r.get("submittedAt"));
        if (millis == null) millis = millis(r.get("updatedAt"));
        if (millis != null) createdMillis.put(collection + ":" + id, millis);
    }

    // ---------------------------------------------------------------- Đọc dữ liệu (giữ nguyên tên hàm cũ)

    public List<User> getUsers() { return users; }
    public List<Post> getPosts() { return posts; }
    public List<Order> getOrders() { return orders; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<Report> getReports() { return reports; }
    public List<Dispute> getDisputes() { return disputes; }
    public List<VerificationRequest> getVerifications() { return verifications; }
    public List<Category> getCategories() { return categories; }

    /** Danh sách chuỗi trong một trường của bản ghi gốc (ví dụ imageUrls, evidencePaths). */
    public List<String> getStrings(String collection, String id, String field) {
        List<String> values = new ArrayList<>();
        Map<String, Object> record = rawRecords.get(collection + ":" + id);
        Object raw = record == null ? null : record.get(field);
        if (raw instanceof List) for (Object item : (List<?>) raw) if (item instanceof String && !((String) item).isEmpty()) values.add((String) item);
        return values;
    }

    /** Một trường chữ của bản ghi gốc, chuỗi rỗng nếu không có. */
    public String getField(String collection, String id, String field) {
        Map<String, Object> record = rawRecords.get(collection + ":" + id);
        return record == null ? "" : str(record, field);
    }

    /**
     * Tải ảnh qua backend (adminEvidence): Admin xem được cả ảnh bài đã ẩn/gỡ và ảnh hồ sơ xác minh riêng tư,
     * những ảnh mà Storage Rules không cho đọc trực tiếp.
     */
    public Task<byte[]> loadImage(String collection, String id, String path) {
        String key = collection + ":" + id + ":" + path;
        synchronized (imageCache) {
            byte[] cached = imageCache.get(key);
            if (cached != null) return Tasks.forResult(cached);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("collection", collection);
        data.put("id", id);
        data.put("path", path);
        return call("adminEvidence", data).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            byte[] bytes = android.util.Base64.decode(str(task.getResult(), "base64"), android.util.Base64.DEFAULT);
            synchronized (imageCache) { imageCache.put(key, bytes); }
            return bytes;
        });
    }

    /** Thời điểm tạo (millis) của một bản ghi, dùng cho biểu đồ và hoạt động gần đây. */
    public Long getCreatedMillis(String collection, String id) { return createdMillis.get(collection + ":" + id); }

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

    // ---------------------------------------------------------------- Thao tác Admin (đi qua adminMutate)

    public void setUserLocked(String userId, boolean locked) {
        Map<String, Object> value = new HashMap<>();
        value.put("locked", locked);
        mutate("userLock", userId, value, locked ? "Khóa tài khoản từ màn Admin" : "Mở khóa tài khoản từ màn Admin",
                locked ? "Đã khóa tài khoản" : "Đã mở khóa tài khoản", null);
    }

    public void sendWarning(String userId, String text) {
        mutate("userWarning", userId, new HashMap<>(), text, "Đã gửi cảnh báo cho người dùng", null);
    }

    public void setPostStatus(String postId, String status) {
        mutate("postStatus", postId, single("status", status), "Cập nhật trạng thái bài: " + status,
                Post.STATUS_ACTIVE.equals(status) ? "Đã hiện lại bài đăng"
                        : Post.STATUS_HIDDEN.equals(status) ? "Đã ẩn bài đăng" : "Đã gỡ bài đăng", null);
    }

    public void setReportStatus(String reportId, String status, String note) {
        mutate("report", reportId, single("status", status), note,
                Report.REJECTED.equals(status) ? "Đã bác bỏ báo cáo" : "Đã cập nhật báo cáo", null);
    }

    /** Xác nhận vi phạm: xử lý đối tượng trước (ẩn bài / khóa tài khoản), thành công mới đóng báo cáo. */
    public void acceptReport(Report report) {
        Runnable close = () -> mutate("report", report.getReportId(), single("status", Report.RESOLVED),
                "Xác nhận vi phạm", "Đã xử lý báo cáo", null);
        if (isSupportTicket(report.getReportId())) {
            close.run();
        } else if (Report.TARGET_POST.equals(report.getTargetType()) && getPost(report.getTargetId()) != null) {
            mutate("postStatus", report.getTargetId(), single("status", Post.STATUS_HIDDEN),
                    "Ẩn bài theo báo cáo " + report.getReportId(), null, close);
        } else if (Report.TARGET_USER.equals(report.getTargetType()) && getUser(report.getTargetId()) != null) {
            Map<String, Object> value = new HashMap<>();
            value.put("locked", true);
            mutate("userLock", report.getTargetId(), value,
                    "Khóa tài khoản theo báo cáo " + report.getReportId(), null, close);
        } else {
            close.run();
        }
    }

    /** Yêu cầu hỗ trợ của người dùng (reports type=SUPPORT): chỉ đóng, không khóa người gửi. */
    public boolean isSupportTicket(String reportId) { return supportTickets.contains(reportId); }

    public void requestDisputeEvidence(String disputeId) {
        mutate("disputeEvidence", disputeId, new HashMap<>(),
                "Admin yêu cầu hai bên bổ sung ảnh và mô tả để làm rõ khiếu nại.",
                "Đã gửi yêu cầu bổ sung bằng chứng", null);
    }

    public void resolveDispute(String disputeId, boolean refundBuyer) {
        mutate("disputeResolve", disputeId, single("resolution", refundBuyer ? "REFUND" : "RELEASE"),
                refundBuyer ? "Admin quyết định hoàn tiền cho người mua sau khi xem xét khiếu nại."
                        : "Admin quyết định giải ngân cho người bán sau khi xem xét khiếu nại.",
                refundBuyer ? "Đã hoàn tiền cho người mua (mô phỏng)" : "Đã giải ngân cho người bán (mô phỏng)", null);
    }

    public void reviewVerification(String requestId, boolean approve, String note) {
        Map<String, Object> value = new HashMap<>();
        value.put("approve", approve);
        mutate("verification", requestId, value, note == null ? "" : note,
                approve ? "Đã duyệt hồ sơ xác minh" : "Đã từ chối hồ sơ xác minh", null);
    }

    public void setCategoryEnabled(String categoryId, boolean enabled) {
        Map<String, Object> value = new HashMap<>();
        for (Category c : categories) if (c.getCategoryId().equals(categoryId)) value.put("name", c.getName());
        value.put("enabled", enabled);
        mutate("category", categoryId, value, enabled ? "Bật danh mục" : "Tắt danh mục",
                enabled ? "Đã bật danh mục" : "Đã tắt danh mục", null);
    }

    public void addCategory(String name) {
        Map<String, Object> value = new HashMap<>();
        value.put("name", name);
        value.put("enabled", true);
        mutate("category", UUID.randomUUID().toString(), value, "Thêm danh mục " + name, "Đã thêm danh mục " + name, null);
    }

    // ---------------------------------------------------------------- Cài đặt

    public boolean isAutoHideReported() { return autoHideReported; }
    public int getReportThreshold() { return reportThreshold; }
    public int getAutoCompleteDays() { return autoCompleteDays; }
    public long getMaxPrice() { return maxPrice; }
    public boolean isRequirePhoneBeforeSelling() { return requirePhoneBeforeSelling; }
    public long getShippingFee() { return shippingFee; }

    public void saveSettings(boolean autoHide, int threshold, int days, long maxPrice, boolean requirePhone,
                             Runnable onSaved) {
        Map<String, Object> value = new HashMap<>();
        value.put("autoHideReported", autoHide);
        value.put("reportThreshold", threshold);
        value.put("autoCompleteDays", days);
        value.put("maxPrice", maxPrice);
        value.put("requirePhoneBeforeSelling", requirePhone);
        value.put("shippingFee", shippingFee);
        value.put("revision", settingsRevision);
        mutate("settings", "active", value, "Cập nhật cấu hình hệ thống", "Đã lưu cài đặt", onSaved);
    }

    // ---------------------------------------------------------------- Đăng xuất

    /** Xóa dữ liệu đã tải (khi đăng nhập Admin mới) để không hiện dữ liệu của phiên trước. */
    public void reset() {
        generation++;
        clear();
    }

    public void logout() {
        generation++;
        clear();
        FirebaseAuth.getInstance().signOut();
    }

    private void clear() {
        synchronized (imageCache) { imageCache.clear(); }
        users.clear(); posts.clear(); orders.clear(); transactions.clear();
        reports.clear(); disputes.clear(); verifications.clear(); categories.clear();
        createdMillis.clear(); rawUsers.clear(); rawRecords.clear(); supportTickets.clear();
        loaded = false;
        loading = false;
        loadError = null;
        loadedAt = 0;
        loadedUid = "";
    }

    // ---------------------------------------------------------------- Hàm phụ

    /**
     * Gọi adminMutate với mã thao tác riêng (chống gửi trùng).
     * successText == null: không báo, dùng khi đây là bước giữa của một chuỗi thao tác.
     */
    private void mutate(String action, String id, Map<String, Object> value, String note,
                        String successText, Runnable then) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("id", id);
        data.put("value", value);
        data.put("note", note);
        data.put("requestId", UUID.randomUUID().toString());
        call("adminMutate", data).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                if (isPermissionError(error)) {
                    denySession();
                    return;
                }
                message.setValue(new Event<>("Không lưu được: " + readable(error)));
                refresh();
                return;
            }
            if (successText != null) message.setValue(new Event<>(successText));
            if (then != null) then.run();
            refresh();
        });
    }

    private void denySession() {
        generation++;
        clear();
        FirebaseAuth.getInstance().signOut();
        sessionDenied.setValue(new Event<>(true));
        changed();
    }

    private void changed() {
        Integer v = dataVersion.getValue();
        dataVersion.setValue(v == null ? 1 : v + 1);
    }

    private static boolean isPermissionError(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FirebaseFunctionsException) {
                FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) current).getCode();
                return code == FirebaseFunctionsException.Code.PERMISSION_DENIED
                        || code == FirebaseFunctionsException.Code.UNAUTHENTICATED;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Mới nhất lên đầu; bản ghi thiếu thời gian xếp cuối. */
    private static <T> Comparator<T> newestFirst(Function<T, Long> createdAt) {
        return Comparator.comparing(createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static String readable(Exception error) {
        if (error == null) return "Lỗi kết nối";
        String text = error.getMessage();
        return text == null || text.isEmpty() ? "Lỗi kết nối" : text;
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    private static String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String or(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static long num(Map<String, Object> data, String key, long fallback) {
        Object value = data.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    private static Long millis(Object value) {
        if (value == null) return null;
        try {
            return Instant.parse(String.valueOf(value)).toEpochMilli();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String date(Map<String, Object> data, String key) {
        Long value = millis(data.get(key));
        return value == null ? "" : new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(new Date(value));
    }

    private static String conditionLabel(String code) {
        switch (code) {
            case "NEW": return "Mới";
            case "LIKE_NEW": return "Như mới";
            case "GOOD": return "Tốt";
            case "FAIR": return "Khá";
            default: return code.isEmpty() ? "—" : code;
        }
    }

    private static String disputeReason(String code) {
        switch (code) {
            case "NOT_RECEIVED": return "Chưa nhận được hàng";
            case "DAMAGED": return "Hàng bị hư hỏng";
            case "MISMATCH": return "Không đúng mô tả";
            case "WRONG_ITEM": return "Giao sai sản phẩm";
            case "OTHER": return "Lý do khác";
            default: return code.isEmpty() ? "Không ghi lý do" : code;
        }
    }

    private static String requestTypeLabel(String code) {
        switch (code) {
            case "PHONE": return "Xác minh số điện thoại";
            case "":
            case "IDENTITY": return "Xác minh danh tính";
            default: return code;
        }
    }
}