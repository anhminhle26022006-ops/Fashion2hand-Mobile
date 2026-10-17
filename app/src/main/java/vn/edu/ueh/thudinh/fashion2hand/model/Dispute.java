package vn.edu.ueh.thudinh.fashion2hand.model;

/** Khiếu nại giao dịch. Khi đang mở, tiền của đơn vẫn bị giữ. */
public class Dispute {
    public static final String OPEN = "OPEN";
    public static final String WAITING_EVIDENCE = "WAITING_EVIDENCE";
    public static final String RESOLVED = "RESOLVED";

    public static final String RESOLUTION_REFUND = "REFUND";
    public static final String RESOLUTION_RELEASE = "RELEASE";

    private String disputeId;
    private String orderId;
    private String userId;
    private String reason;
    private String buyerStatement;
    private String sellerStatement;
    private String status;
    private String resolution;
    private String createdAt;

    public Dispute() { }

    public Dispute(String disputeId, String orderId, String userId, String reason,
                   String buyerStatement, String sellerStatement, String status,
                   String resolution, String createdAt) {
        this.disputeId = disputeId;
        this.orderId = orderId;
        this.userId = userId;
        this.reason = reason;
        this.buyerStatement = buyerStatement;
        this.sellerStatement = sellerStatement;
        this.status = status;
        this.resolution = resolution;
        this.createdAt = createdAt;
    }

    public String getDisputeId() { return disputeId; }
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getReason() { return reason; }
    public String getBuyerStatement() { return buyerStatement; }
    public String getSellerStatement() { return sellerStatement; }
    public String getStatus() { return status; }
    public String getResolution() { return resolution; }
    public String getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public boolean isOpen() { return !RESOLVED.equals(status); }
}
