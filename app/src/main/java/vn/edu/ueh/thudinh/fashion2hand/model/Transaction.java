package vn.edu.ueh.thudinh.fashion2hand.model;

/** Trạng thái tiền của một đơn mua bán (Payment Holding Simulation, chưa có cổng thanh toán thật). */
public class Transaction {
    public static final String HOLDING = "HOLDING";
    public static final String RELEASED = "RELEASED";
    public static final String REFUNDED = "REFUNDED";
    public static final String DISPUTED = "DISPUTED";

    private String transactionId;
    private String orderId;
    private long amount;
    private String status;
    private String createdAt;
    private String releasedAt;

    public Transaction() { }

    public Transaction(String transactionId, String orderId, long amount, String status,
                       String createdAt, String releasedAt) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.releasedAt = releasedAt;
    }

    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getReleasedAt() { return releasedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setReleasedAt(String releasedAt) { this.releasedAt = releasedAt; }
}
