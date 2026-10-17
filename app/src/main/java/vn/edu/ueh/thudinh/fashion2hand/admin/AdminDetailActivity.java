package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.VerificationRequest;

/**
 * Màn chi tiết dùng chung (activity_admin_detail.xml) cho 6 loại:
 * người dùng, bài đăng, đơn hàng, báo cáo, khiếu nại, hồ sơ xác minh.
 */
public class AdminDetailActivity extends BaseAdminActivity {

    public static final String EXTRA_TYPE = "admin_detail_type";
    public static final String EXTRA_ID = "admin_detail_id";

    public static final String TYPE_USER = "user";
    public static final String TYPE_POST = "post";
    public static final String TYPE_ORDER = "order";
    public static final String TYPE_REPORT = "report";
    public static final String TYPE_DISPUTE = "dispute";
    public static final String TYPE_VERIFICATION = "verification";

    private String type;
    private String id;

    private View heroCard;
    private TextView heroLeading;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private LinearLayout heroChips;
    private TextView notice;
    private TextView infoTitle;
    private LinearLayout info;
    private TextView timelineTitle;
    private LinearLayout timeline;
    private TextView evidenceTitle;
    private LinearLayout evidence;
    private TextView decisionTitle;
    private RadioGroup decision;
    private View actionDivider;
    private View actions;
    private TextView btnSecondary;
    private TextView btnPrimary;

    public static void open(Context context, String type, String id) {
        Intent intent = new Intent(context, AdminDetailActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_ID, id);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_detail);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        id = getIntent().getStringExtra(EXTRA_ID);

        heroCard = findViewById(R.id.cardDetailHero);
        heroLeading = findViewById(R.id.txtDetailLeading);
        heroTitle = findViewById(R.id.txtDetailTitle);
        heroSubtitle = findViewById(R.id.txtDetailSubtitle);
        heroChips = findViewById(R.id.layoutDetailChips);
        notice = findViewById(R.id.txtDetailNotice);
        infoTitle = findViewById(R.id.txtDetailInfoTitle);
        info = findViewById(R.id.layoutDetailInfo);
        timelineTitle = findViewById(R.id.txtDetailTimelineTitle);
        timeline = findViewById(R.id.layoutDetailTimeline);
        evidenceTitle = findViewById(R.id.txtDetailEvidenceTitle);
        evidence = findViewById(R.id.layoutDetailEvidence);
        decisionTitle = findViewById(R.id.txtDetailDecisionTitle);
        decision = findViewById(R.id.radioDetailDecision);
        actionDivider = findViewById(R.id.viewDetailActionDivider);
        actions = findViewById(R.id.layoutDetailActions);
        btnSecondary = findViewById(R.id.btnDetailSecondary);
        btnPrimary = findViewById(R.id.btnDetailPrimary);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        reset();
        if (type == null || id == null) {
            showMissing();
            return;
        }
        switch (type) {
            case TYPE_USER: renderUser(); break;
            case TYPE_POST: renderPost(); break;
            case TYPE_ORDER: renderOrder(); break;
            case TYPE_REPORT: renderReport(); break;
            case TYPE_DISPUTE: renderDispute(); break;
            case TYPE_VERIFICATION: renderVerification(); break;
            default: showMissing();
        }
    }

    // ================================================================ Người dùng

    private void renderUser() {
        User u = viewModel.getUser(id);
        if (u == null) { showMissing(); return; }
        bindBackBar("Chi tiết người dùng");
        setBackBarChip(AdminLabels.account(u.getAccountStatus()));
        setHero(AdminUi.initials(u.getName()), AdminUi.KIND_INFO, u.getName(), u.getEmail());
        AdminUi.addChip(heroChips, new AdminLabels.Label(u.getRole(), AdminUi.KIND_NEUTRAL));
        AdminUi.addChip(heroChips, AdminLabels.verification(u.getVerificationStatus()));

        AdminUi.addKv(info, "Số điện thoại", AdminUi.maskPhone(u.getPhone()));
        AdminUi.addKv(info, "Khu vực", u.getArea());
        AdminUi.addKv(info, "Ngày tham gia", u.getJoinedAt());
        AdminUi.addKv(info, "Bài đăng", String.valueOf(u.getPostCount()));
        AdminUi.addKv(info, "Đơn mua / Đơn bán", u.getBuyCount() + " / " + u.getSellCount());
        AdminUi.addKv(info, "Số lần bị báo cáo", String.valueOf(u.getReportedCount()));

        if (u.isLocked()) {
            AdminUi.styleNotice(notice, "Tài khoản đang bị khóa: người dùng không thể đăng nhập, đăng bài hay giao dịch.",
                    AdminUi.KIND_DANGER);
            setActions("Gửi cảnh báo", AdminUi.BTN_OUTLINE, this::sendWarning,
                    "Mở khóa tài khoản", AdminUi.BTN_PRIMARY, () -> AdminUi.confirm(this,
                            "Mở khóa tài khoản?", u.getName() + " sẽ đăng nhập và giao dịch lại bình thường.",
                            "Mở khóa", () -> {
                                viewModel.setUserLocked(u.getUserId(), false);
                                AdminUi.toast(this, "Đã mở khóa tài khoản");
                                render();
                            }));
        } else {
            setActions("Gửi cảnh báo", AdminUi.BTN_OUTLINE, this::sendWarning,
                    "Khóa tài khoản", AdminUi.BTN_DANGER_FILL, () -> AdminUi.confirm(this,
                            "Khóa tài khoản?", u.getName() + " sẽ không thể đăng nhập cho đến khi được mở khóa.",
                            "Khóa", () -> {
                                viewModel.setUserLocked(u.getUserId(), true);
                                AdminUi.toast(this, "Đã khóa tài khoản");
                                render();
                            }));
        }
    }

    private void sendWarning() {
        AdminUi.askText(this, "Gửi cảnh báo", "Nội dung cảnh báo", "Gửi", text ->
                AdminUi.toast(this, text.isEmpty() ? "Chưa nhập nội dung cảnh báo" : "Đã gửi cảnh báo (demo)"));
    }

    // ================================================================ Bài đăng

    private void renderPost() {
        Post p = viewModel.getPost(id);
        if (p == null) { showMissing(); return; }
        bindBackBar("Chi tiết bài đăng");
        setBackBarChip(AdminLabels.postStatus(p.getStatus()));
        setHero(AdminViewModel.categoryEmoji(p.getCategory()), p.isFree() ? AdminUi.KIND_OK : AdminUi.KIND_INFO,
                p.getTitle(), "Người đăng: " + viewModel.getUserName(p.getUserId()));
        heroLeading.setTextSize(34);
        AdminUi.addChip(heroChips, AdminLabels.listingType(p.getListingType()));
        if (p.getReportCount() > 0) {
            AdminUi.addChip(heroChips, new AdminLabels.Label("Bị báo cáo " + p.getReportCount(), AdminUi.KIND_DANGER));
        }

        AdminUi.addKv(info, "Giá", p.isFree() ? "Miễn phí" : AdminUi.money(p.getPrice()));
        AdminUi.addKv(info, "Danh mục", p.getCategory());
        AdminUi.addKv(info, "Size · Tình trạng", p.getSize() + " · " + p.getCondition());
        AdminUi.addKv(info, "Khu vực", p.getLocation());
        AdminUi.addKv(info, "Ngày đăng", p.getCreatedAt());

        if (p.getReportCount() > 0) {
            AdminUi.styleNotice(notice, "Lý do bị báo cáo\n" + p.getReportReasons(), AdminUi.KIND_DANGER);
        } else if (p.isFree()) {
            AdminUi.styleNotice(notice, "Bài cho tặng miễn phí: Admin chỉ kiểm duyệt nội dung, không chọn người nhận.",
                    AdminUi.KIND_NEUTRAL);
        }

        if (Post.STATUS_REMOVED.equals(p.getStatus())) {
            AdminUi.styleNotice(notice, "Bài đăng đã bị gỡ khỏi hệ thống.", AdminUi.KIND_NEUTRAL);
            hideActions();
            return;
        }
        boolean hidden = Post.STATUS_HIDDEN.equals(p.getStatus());
        setActions(hidden ? "Hiện lại bài" : "Ẩn bài đăng", AdminUi.BTN_OUTLINE, () -> AdminUi.confirm(this,
                        hidden ? "Hiện lại bài đăng?" : "Ẩn bài đăng?",
                        hidden ? "Bài sẽ hiển thị lại cho người dùng." : "Người dùng khác sẽ không thấy bài này. Có thể hiện lại sau.",
                        hidden ? "Hiện lại" : "Ẩn", () -> {
                            viewModel.setPostStatus(p.getPostId(), hidden ? Post.STATUS_ACTIVE : Post.STATUS_HIDDEN);
                            AdminUi.toast(this, hidden ? "Đã hiện lại bài đăng" : "Đã ẩn bài đăng");
                            render();
                        }),
                "Gỡ bài đăng", AdminUi.BTN_DANGER_FILL, () -> AdminUi.confirm(this,
                        "Gỡ bài đăng?", "Bài sẽ bị gỡ vĩnh viễn và người đăng nhận thông báo vi phạm.", "Gỡ", () -> {
                            viewModel.setPostStatus(p.getPostId(), Post.STATUS_REMOVED);
                            AdminUi.toast(this, "Đã gỡ bài đăng");
                            render();
                        }));
    }

    // ================================================================ Đơn hàng (chỉ xem)

    private void renderOrder() {
        Order o = viewModel.getOrder(id);
        if (o == null) { showMissing(); return; }
        bindBackBar("Đơn " + o.getOrderId());
        setBackBarChip(new AdminLabels.Label("Chỉ xem", AdminUi.KIND_NEUTRAL));
        setHero("#", o.isFree() ? AdminUi.KIND_OK : AdminUi.KIND_INFO, viewModel.getPostTitle(o.getPostId()),
                "Tạo lúc " + o.getCreatedAt());
        AdminUi.addChip(heroChips, AdminLabels.orderType(o.getOrderType()));
        AdminUi.addChip(heroChips, AdminLabels.orderStatus(o.getOrderStatus()));

        AdminUi.styleNotice(notice, "Admin chỉ theo dõi đơn hàng, không thao tác mua bán thay người dùng.",
                AdminUi.KIND_INFO);

        if (o.isFree()) {
            AdminUi.addKv(info, "Người cho", viewModel.getUserName(o.getSellerId()));
            AdminUi.addKv(info, "Người nhận", viewModel.getUserName(o.getBuyerId()));
        } else {
            AdminUi.addKv(info, "Người mua", viewModel.getUserName(o.getBuyerId()));
            AdminUi.addKv(info, "Người bán", viewModel.getUserName(o.getSellerId()));
        }
        AdminUi.addKvChip(info, "Đơn hàng", AdminLabels.orderStatus(o.getOrderStatus()));
        AdminUi.addKvChip(info, "Thanh toán", AdminLabels.payment(o.getPaymentStatus()));
        AdminUi.addKvChip(info, "Vận chuyển", AdminLabels.shipping(o.getShippingStatus()));
        Transaction t = viewModel.getTransactionByOrder(o.getOrderId());
        if (t != null) AdminUi.addKvChip(info, "Giữ tiền (mô phỏng)", AdminLabels.transaction(t.getStatus()));
        AdminUi.addKv(info, "Mã vận đơn", o.getTrackingCode().isEmpty() ? "Chưa tạo" : o.getTrackingCode());
        AdminUi.addKv(info, "Giá sản phẩm", o.isFree() ? "0₫ · Miễn phí" : AdminUi.money(o.getPrice()));
        AdminUi.addKv(info, "Phí vận chuyển", AdminUi.money(o.getShippingFee()));
        AdminUi.addKv(info, "Tổng thanh toán", AdminUi.money(o.getTotalAmount()));

        buildOrderTimeline(o);

        Dispute d = viewModel.getDisputeByOrder(o.getOrderId());
        if (d != null) {
            btnSecondary.setVisibility(View.GONE);
            AdminUi.styleButton(btnPrimary, "Xem khiếu nại " + d.getDisputeId(), AdminUi.BTN_OUTLINE);
            btnPrimary.setOnClickListener(v -> open(this, TYPE_DISPUTE, d.getDisputeId()));
        } else {
            hideActions();
        }
    }

    private void buildOrderTimeline(Order o) {
        String[] steps = o.isFree()
                ? new String[]{"Người cho chọn người nhận", "Tạo đơn cho tặng", "Đóng gói và bàn giao vận chuyển",
                "Đang vận chuyển", "Đã giao đến người nhận", "Người nhận xác nhận, hoàn tất"}
                : new String[]{"Tạo đơn hàng", "Thanh toán thành công, tiền chuyển sang Holding",
                "Người bán xác nhận và đóng gói", "Đang vận chuyển", "Đã giao đến người mua",
                "Người mua xác nhận, giải ngân cho người bán"};
        int done;
        switch (o.getOrderStatus()) {
            case Order.STATUS_PENDING: done = 2; break;
            case Order.STATUS_CONFIRMED: done = 3; break;
            case Order.STATUS_SHIPPING: done = 4; break;
            case Order.STATUS_DELIVERED:
            case Order.STATUS_DISPUTED:
            case Order.STATUS_REFUNDED: done = 5; break;
            case Order.STATUS_COMPLETED: done = steps.length; break;
            default: done = 1;
        }
        timelineTitle.setVisibility(View.VISIBLE);
        timelineTitle.setText(o.getTrackingCode().isEmpty() ? "Hành trình đơn" : "Hành trình đơn · " + o.getTrackingCode());
        timeline.setVisibility(View.VISIBLE);

        boolean extra = Order.STATUS_CANCELLED.equals(o.getOrderStatus())
                || Order.STATUS_DISPUTED.equals(o.getOrderStatus())
                || Order.STATUS_REFUNDED.equals(o.getOrderStatus());
        int shown = extra ? done : steps.length;
        for (int i = 0; i < shown; i++) {
            boolean isDone = i < done;
            boolean last = i == shown - 1 && !extra;
            addTimeline(steps[i], i == 0 ? o.getCreatedAt() : (isDone ? "Đã hoàn thành" : "Chưa diễn ra"),
                    isDone, last, AdminUi.KIND_OK);
        }
        if (Order.STATUS_CANCELLED.equals(o.getOrderStatus())) {
            addTimeline("Đơn đã bị hủy", "Không phát sinh giao hàng", true, true, AdminUi.KIND_NEUTRAL);
        } else if (Order.STATUS_DISPUTED.equals(o.getOrderStatus())) {
            addTimeline("Người mua mở khiếu nại, tiền tạm giữ", "Chờ Admin xử lý", true, true, AdminUi.KIND_DANGER);
        } else if (Order.STATUS_REFUNDED.equals(o.getOrderStatus())) {
            addTimeline("Hoàn tiền cho người mua", "Đã xử lý khiếu nại", true, true, AdminUi.KIND_WARN);
        }
    }

    // ================================================================ Báo cáo

    private void renderReport() {
        Report r = viewModel.getReport(id);
        if (r == null) { showMissing(); return; }
        bindBackBar("Báo cáo " + r.getReportId());
        setBackBarChip(AdminLabels.report(r.getStatus()));

        String targetType = AdminLabels.targetType(r.getTargetType());
        setHero(targetLeading(r), AdminUi.KIND_DANGER, viewModel.getTargetName(r), targetType + " bị báo cáo");
        AdminUi.addChip(heroChips, new AdminLabels.Label("Bấm để xem " + targetType.toLowerCase(), AdminUi.KIND_NEUTRAL));
        heroCard.setClickable(true);
        heroCard.setOnClickListener(v -> openTarget(r));

        AdminUi.addKv(info, "Người báo cáo", viewModel.getUserName(r.getReporterId()));
        AdminUi.addKv(info, "Lý do", r.getReason());
        AdminUi.addKv(info, "Thời gian", r.getCreatedAt());

        showEvidenceTitle("Mô tả và bằng chứng");
        addParagraph(r.getDescription());
        AdminUi.addEvidenceRow(evidence, "Ảnh 1", "Ảnh chụp tin nhắn");

        if (!r.isOpen()) {
            AdminUi.styleNotice(notice, Report.RESOLVED.equals(r.getStatus())
                    ? "Báo cáo đã được xử lý, vi phạm đã được xác nhận."
                    : "Báo cáo đã bị bác bỏ, đối tượng không vi phạm.", AdminUi.KIND_NEUTRAL);
            hideActions();
            return;
        }
        String acceptText;
        String acceptMessage;
        switch (r.getTargetType()) {
            case Report.TARGET_USER:
                acceptText = "Khóa tài khoản & đóng";
                acceptMessage = "Tài khoản bị báo cáo sẽ bị khóa và báo cáo được đóng.";
                break;
            case Report.TARGET_ORDER:
                acceptText = "Xác nhận vi phạm";
                acceptMessage = "Báo cáo được đóng. Nếu cần hoàn tiền, xử lý ở mục Khiếu nại của đơn.";
                break;
            default:
                acceptText = "Ẩn bài & đóng";
                acceptMessage = "Bài đăng bị báo cáo sẽ bị ẩn và báo cáo được đóng.";
        }
        setActions("Bác bỏ", AdminUi.BTN_OUTLINE, () -> AdminUi.confirm(this, "Bác bỏ báo cáo?",
                        "Đối tượng được xem là không vi phạm.", "Bác bỏ", () -> {
                            viewModel.rejectReport(r.getReportId());
                            AdminUi.toast(this, "Đã bác bỏ báo cáo");
                            render();
                        }),
                acceptText, Report.TARGET_USER.equals(r.getTargetType()) ? AdminUi.BTN_DANGER_FILL : AdminUi.BTN_PRIMARY,
                () -> AdminUi.confirm(this, "Xác nhận vi phạm?", acceptMessage, "Xác nhận", () -> {
                    viewModel.acceptReport(r);
                    AdminUi.toast(this, "Đã xử lý báo cáo");
                    render();
                }));
    }

    private String targetLeading(Report r) {
        switch (r.getTargetType()) {
            case Report.TARGET_USER: return AdminUi.initials(viewModel.getTargetName(r));
            case Report.TARGET_ORDER: return "#";
            default:
                Post p = viewModel.getPost(r.getTargetId());
                return p == null ? "?" : AdminViewModel.categoryEmoji(p.getCategory());
        }
    }

    private void openTarget(Report r) {
        switch (r.getTargetType()) {
            case Report.TARGET_USER: open(this, TYPE_USER, r.getTargetId()); break;
            case Report.TARGET_ORDER: open(this, TYPE_ORDER, r.getTargetId()); break;
            default: open(this, TYPE_POST, r.getTargetId());
        }
    }

    // ================================================================ Khiếu nại

    private void renderDispute() {
        Dispute d = viewModel.getDispute(id);
        if (d == null) { showMissing(); return; }
        Order o = viewModel.getOrder(d.getOrderId());
        Transaction t = viewModel.getTransactionByOrder(d.getOrderId());
        bindBackBar("Khiếu nại " + d.getDisputeId());
        setBackBarChip(AdminLabels.dispute(d.getStatus()));

        setHero("KN", AdminUi.KIND_WARN, o == null ? d.getOrderId() : viewModel.getPostTitle(o.getPostId()),
                "Đơn " + d.getOrderId() + " · bấm để xem đơn");
        if (t != null) AdminUi.addChip(heroChips, AdminLabels.transaction(t.getStatus()));
        heroCard.setClickable(true);
        heroCard.setOnClickListener(v -> open(this, TYPE_ORDER, d.getOrderId()));

        if (o != null) {
            AdminUi.addKv(info, "Người mua", viewModel.getUserName(o.getBuyerId()));
            AdminUi.addKv(info, "Người bán", viewModel.getUserName(o.getSellerId()));
            AdminUi.addKvChip(info, "Vận chuyển", AdminLabels.shipping(o.getShippingStatus()));
        }
        AdminUi.addKv(info, "Lý do", d.getReason());
        AdminUi.addKv(info, "Ngày mở", d.getCreatedAt());
        if (t != null) {
            AdminUi.addKv(info, "Số tiền liên quan", AdminUi.money(t.getAmount()));
            AdminUi.addKvChip(info, "Giao dịch", AdminLabels.transaction(t.getStatus()));
        }

        showEvidenceTitle("Ý kiến hai bên");
        addStatement("Người mua", o == null ? "" : viewModel.getUserName(o.getBuyerId()), d.getBuyerStatement(),
                "Ảnh 1", "Ảnh 2");
        addStatement("Người bán", o == null ? "" : viewModel.getUserName(o.getSellerId()), d.getSellerStatement(),
                "Video đóng gói");

        if (!d.isOpen()) {
            boolean refund = Dispute.RESOLUTION_REFUND.equals(d.getResolution());
            AdminUi.styleNotice(notice, "Đã giải quyết: " + (refund
                    ? "hoàn tiền cho người mua." : "giải ngân cho người bán."), AdminUi.KIND_OK);
            hideActions();
            return;
        }

        AdminUi.styleNotice(notice, "Trong thời gian khiếu nại, tiền vẫn được giữ và chưa chuyển cho người bán (Payment Holding Simulation).",
                AdminUi.KIND_WARN);
        decisionTitle.setVisibility(View.VISIBLE);
        decision.setVisibility(View.VISIBLE);

        boolean waiting = Dispute.WAITING_EVIDENCE.equals(d.getStatus());
        setActions(waiting ? "Đang chờ bằng chứng" : "Yêu cầu bổ sung", AdminUi.BTN_OUTLINE, () -> {
                    viewModel.requestDisputeEvidence(d.getDisputeId());
                    AdminUi.toast(this, "Đã gửi yêu cầu bổ sung bằng chứng cho hai bên");
                    render();
                },
                "Xác nhận quyết định", AdminUi.BTN_PRIMARY, () -> {
                    int checked = decision.getCheckedRadioButtonId();
                    if (checked == View.NO_ID) {
                        AdminUi.toast(this, "Chọn Hoàn tiền hoặc Giải ngân trước");
                        return;
                    }
                    boolean refund = checked == R.id.radioDecisionRefund;
                    AdminUi.confirm(this, "Xác nhận quyết định?",
                            refund ? "Hoàn tiền cho người mua. Thao tác này không thể hoàn tác."
                                    : "Giải ngân cho người bán. Thao tác này không thể hoàn tác.",
                            "Xác nhận", () -> {
                                viewModel.resolveDispute(d.getDisputeId(), refund);
                                AdminUi.toast(this, refund ? "Đã hoàn tiền cho người mua" : "Đã giải ngân cho người bán");
                                render();
                            });
                });
        if (waiting) {
            btnSecondary.setEnabled(false);
            btnSecondary.setAlpha(0.5f);
        }
    }

    // ================================================================ Xác minh

    private void renderVerification() {
        VerificationRequest v = viewModel.getVerification(id);
        if (v == null) { showMissing(); return; }
        User u = viewModel.getUser(v.getUserId());
        String name = viewModel.getUserName(v.getUserId());
        bindBackBar("Hồ sơ " + v.getRequestId());
        setBackBarChip(AdminLabels.verificationRequest(v.getStatus()));
        setHero(AdminUi.initials(name), AdminUi.KIND_INFO, name, v.getRequestType());
        if (u != null) AdminUi.addChip(heroChips, AdminLabels.verification(u.getVerificationStatus()));

        AdminUi.styleNotice(notice, "Xác minh thủ công (Verification Simulation). Chưa tích hợp VNeID, Admin duyệt dựa trên hồ sơ người dùng gửi.",
                AdminUi.KIND_INFO);

        AdminUi.addKv(info, "Loại xác minh", v.getRequestType());
        AdminUi.addKv(info, "Giấy tờ", v.getDocumentType());
        if (u != null) AdminUi.addKv(info, "Số điện thoại", AdminUi.maskPhone(u.getPhone()));
        AdminUi.addKvChip(info, "OTP số điện thoại", v.isPhoneVerified()
                ? new AdminLabels.Label("Đã xác thực", AdminUi.KIND_OK)
                : new AdminLabels.Label("Chưa xác thực", AdminUi.KIND_WARN));
        AdminUi.addKv(info, "Thời gian gửi", v.getCreatedAt());
        if (v.getNote() != null && !v.getNote().isEmpty()) AdminUi.addKv(info, "Ghi chú", v.getNote());

        showEvidenceTitle("Ảnh hồ sơ");
        AdminUi.addEvidenceRow(evidence, "Ảnh chân dung", "Mặt trước giấy tờ", "Mặt sau giấy tờ");

        if (!VerificationRequest.PENDING.equals(v.getStatus())) {
            hideActions();
            return;
        }
        setActions("Từ chối", AdminUi.BTN_DANGER, () -> AdminUi.askText(this, "Lý do từ chối",
                        "Ví dụ: Ảnh giấy tờ bị mờ", "Từ chối", reason -> {
                            if (reason.isEmpty()) {
                                AdminUi.toast(this, "Nhập lý do để người dùng biết cần bổ sung gì");
                                return;
                            }
                            viewModel.reviewVerification(v.getRequestId(), false, reason);
                            AdminUi.toast(this, "Đã từ chối hồ sơ");
                            render();
                        }),
                "Duyệt", AdminUi.BTN_PRIMARY, () -> AdminUi.confirm(this, "Duyệt hồ sơ?",
                        name + " sẽ được gắn nhãn Đã xác minh.", "Duyệt", () -> {
                            viewModel.reviewVerification(v.getRequestId(), true, "");
                            AdminUi.toast(this, "Đã duyệt hồ sơ xác minh");
                            render();
                        }));
    }

    // ================================================================ Hàm phụ

    private void reset() {
        heroChips.removeAllViews();
        info.removeAllViews();
        timeline.removeAllViews();
        evidence.removeAllViews();
        heroCard.setOnClickListener(null);
        heroCard.setClickable(false);
        heroLeading.setTextSize(26);
        notice.setVisibility(View.GONE);
        infoTitle.setVisibility(View.VISIBLE);
        timelineTitle.setVisibility(View.GONE);
        timeline.setVisibility(View.GONE);
        evidenceTitle.setVisibility(View.GONE);
        evidence.setVisibility(View.GONE);
        decisionTitle.setVisibility(View.GONE);
        decision.setVisibility(View.GONE);
        actionDivider.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
    }

    private void showMissing() {
        bindBackBar("Không tìm thấy");
        setHero("?", AdminUi.KIND_NEUTRAL, "Dữ liệu không còn tồn tại", "Mục này có thể đã bị xóa.");
        infoTitle.setVisibility(View.GONE);
        hideActions();
    }

    private void setHero(String leading, int kind, String title, String subtitle) {
        heroLeading.setText(leading);
        heroLeading.setTextColor(ContextCompat.getColor(this, AdminUi.strongColor(kind)));
        AdminUi.tint(heroLeading, AdminUi.softColor(kind));
        heroTitle.setText(title);
        heroSubtitle.setText(subtitle);
    }

    private void setActions(String secondaryText, int secondaryStyle, Runnable secondary,
                            String primaryText, int primaryStyle, Runnable primary) {
        AdminUi.styleButton(btnSecondary, secondaryText, secondaryStyle);
        btnSecondary.setOnClickListener(v -> secondary.run());
        AdminUi.styleButton(btnPrimary, primaryText, primaryStyle);
        btnPrimary.setOnClickListener(v -> primary.run());
    }

    private void hideActions() {
        actionDivider.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
    }

    private void showEvidenceTitle(String title) {
        evidenceTitle.setText(title);
        evidenceTitle.setVisibility(View.VISIBLE);
        evidence.setVisibility(View.VISIBLE);
    }

    private void addParagraph(String text) {
        TextView paragraph = new TextView(this);
        paragraph.setText(text);
        paragraph.setTextSize(13);
        paragraph.setLineSpacing(0, 1.3f);
        paragraph.setTextColor(ContextCompat.getColor(this, R.color.f2h_text));
        paragraph.setBackgroundResource(R.drawable.bg_admin_card);
        int pad = AdminUi.dp(this, 14);
        paragraph.setPadding(pad, pad, pad, pad);
        evidence.addView(paragraph);
    }

    private void addStatement(String role, String name, String statement, String... evidenceLabels) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_admin_card);
        int pad = AdminUi.dp(this, 14);
        box.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(this);
        header.setText(role + " · " + name);
        header.setTextSize(13);
        header.setTextColor(ContextCompat.getColor(this, R.color.f2h_text));
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(header);

        TextView body = new TextView(this);
        body.setText(statement);
        body.setTextSize(13);
        body.setLineSpacing(0, 1.3f);
        body.setTextColor(ContextCompat.getColor(this, R.color.f2h_text_muted));
        body.setPadding(0, AdminUi.dp(this, 6), 0, 0);
        box.addView(body);

        AdminUi.addEvidenceRow(box, evidenceLabels);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (evidence.getChildCount() > 0) lp.topMargin = AdminUi.dp(this, 10);
        evidence.addView(box, lp);
    }

    private void addTimeline(String title, String time, boolean done, boolean last, int kind) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_admin_timeline, timeline, false);
        View dot = item.findViewById(R.id.viewTimelineDot);
        View line = item.findViewById(R.id.viewTimelineLine);
        TextView titleView = item.findViewById(R.id.txtTimelineTitle);
        TextView timeView = item.findViewById(R.id.txtTimelineTime);

        AdminUi.tint(dot, done ? AdminUi.strongColor(kind) : R.color.f2h_border);
        line.setBackgroundColor(ContextCompat.getColor(this, done ? AdminUi.strongColor(kind) : R.color.f2h_border));
        line.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(this, done ? R.color.f2h_text : R.color.f2h_text_muted));
        timeView.setText(time);
        timeline.addView(item);
    }
}
