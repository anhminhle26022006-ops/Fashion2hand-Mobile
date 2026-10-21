package vn.edu.ueh.thudinh.fashion2hand.utils.admin;

import vn.edu.ueh.thudinh.fashion2hand.model.admin.Dispute;
import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.Report;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;
import vn.edu.ueh.thudinh.fashion2hand.model.User;
import vn.edu.ueh.thudinh.fashion2hand.model.admin.VerificationRequest;

/** Đổi mã trạng thái (lưu trong dữ liệu) thành nhãn tiếng Việt + màu chip. */
public final class AdminLabels {

    public static final class Label {
        public final String text;
        public final int kind;

        public Label(String text, int kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    private AdminLabels() { }

    public static Label verification(String code) {
        if (User.VERIFIED.equals(code)) return new Label("Đã xác minh", AdminUi.KIND_OK);
        if (User.VERIFY_PENDING.equals(code)) return new Label("Chờ xác minh", AdminUi.KIND_INFO);
        return new Label("Chưa xác minh", AdminUi.KIND_NEUTRAL);
    }

    public static Label account(String code) {
        if (User.ACCOUNT_LOCKED.equals(code)) return new Label("Bị khóa", AdminUi.KIND_DANGER);
        return new Label("Đang hoạt động", AdminUi.KIND_OK);
    }

    public static Label listingType(String code) {
        if (Post.TYPE_FREE.equals(code)) return new Label("Miễn phí", AdminUi.KIND_OK);
        return new Label("Đang bán", AdminUi.KIND_INFO);
    }

    public static Label postStatus(String code) {
        switch (code) {
            case Post.STATUS_HIDDEN: return new Label("Đã ẩn", AdminUi.KIND_NEUTRAL);
            case Post.STATUS_REMOVED: return new Label("Đã gỡ", AdminUi.KIND_DANGER);
            case Post.STATUS_GIVEN: return new Label("Đã có người nhận", AdminUi.KIND_INFO);
            default: return new Label("Đang hiển thị", AdminUi.KIND_OK);
        }
    }

    public static Label orderType(String code) {
        if (Order.TYPE_FREE.equals(code)) return new Label("Cho tặng", AdminUi.KIND_OK);
        return new Label("Mua bán", AdminUi.KIND_INFO);
    }

    public static Label orderStatus(String code) {
        switch (code) {
            case Order.STATUS_PENDING: return new Label("Chờ xác nhận", AdminUi.KIND_WARN);
            case Order.STATUS_CONFIRMED: return new Label("Đã xác nhận", AdminUi.KIND_INFO);
            case Order.STATUS_SHIPPING: return new Label("Đang giao", AdminUi.KIND_INFO);
            case Order.STATUS_DELIVERED: return new Label("Đã giao", AdminUi.KIND_OK);
            case Order.STATUS_COMPLETED: return new Label("Hoàn tất", AdminUi.KIND_OK);
            case Order.STATUS_CANCELLED: return new Label("Đã hủy", AdminUi.KIND_NEUTRAL);
            case Order.STATUS_DISPUTED: return new Label("Tranh chấp", AdminUi.KIND_DANGER);
            case Order.STATUS_REFUNDED: return new Label("Đã hoàn tiền", AdminUi.KIND_WARN);
            default: return new Label(code, AdminUi.KIND_NEUTRAL);
        }
    }

    public static Label payment(String code) {
        switch (code) {
            case Order.PAYMENT_PENDING: return new Label("Chờ thanh toán", AdminUi.KIND_WARN);
            case Order.PAYMENT_PAID: return new Label("Đã thanh toán", AdminUi.KIND_OK);
            case Order.PAYMENT_REFUNDED: return new Label("Đã hoàn tiền", AdminUi.KIND_WARN);
            default: return new Label("Không áp dụng", AdminUi.KIND_NEUTRAL);
        }
    }

    public static Label shipping(String code) {
        switch (code) {
            case Order.SHIPPING_IN_TRANSIT: return new Label("Đang vận chuyển", AdminUi.KIND_INFO);
            case Order.SHIPPING_DELIVERED: return new Label("Đã giao", AdminUi.KIND_OK);
            case Order.SHIPPING_RETURNED: return new Label("Đã hoàn hàng", AdminUi.KIND_NEUTRAL);
            default: return new Label("Chờ lấy hàng", AdminUi.KIND_WARN);
        }
    }

    public static Label transaction(String code) {
        switch (code) {
            case Transaction.RELEASED: return new Label("Đã giải ngân", AdminUi.KIND_OK);
            case Transaction.REFUNDED: return new Label("Đã hoàn tiền", AdminUi.KIND_WARN);
            case Transaction.DISPUTED: return new Label("Tranh chấp", AdminUi.KIND_DANGER);
            default: return new Label("Đang giữ tiền", AdminUi.KIND_HOLD);
        }
    }

    public static Label report(String code) {
        switch (code) {
            case Report.REVIEWING: return new Label("Đang xem xét", AdminUi.KIND_WARN);
            case Report.RESOLVED: return new Label("Đã xử lý", AdminUi.KIND_OK);
            case Report.REJECTED: return new Label("Bác bỏ", AdminUi.KIND_NEUTRAL);
            default: return new Label("Chờ xử lý", AdminUi.KIND_DANGER);
        }
    }

    public static Label dispute(String code) {
        switch (code) {
            case Dispute.WAITING_EVIDENCE: return new Label("Chờ bằng chứng", AdminUi.KIND_INFO);
            case Dispute.RESOLVED: return new Label("Đã giải quyết", AdminUi.KIND_OK);
            default: return new Label("Đang mở", AdminUi.KIND_WARN);
        }
    }

    public static Label verificationRequest(String code) {
        switch (code) {
            case VerificationRequest.APPROVED: return new Label("Đã duyệt", AdminUi.KIND_OK);
            case VerificationRequest.REJECTED: return new Label("Từ chối", AdminUi.KIND_DANGER);
            default: return new Label("Chờ duyệt", AdminUi.KIND_INFO);
        }
    }

    public static String targetType(String code) {
        switch (code) {
            case Report.TARGET_USER: return "Người dùng";
            case Report.TARGET_ORDER: return "Đơn hàng";
            default: return "Bài đăng";
        }
    }
}
