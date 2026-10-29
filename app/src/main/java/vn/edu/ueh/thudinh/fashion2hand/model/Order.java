package vn.edu.ueh.thudinh.fashion2hand.model;

/**
 * Đơn hàng. Tách riêng 3 trạng thái: orderStatus, paymentStatus, shippingStatus.
 * Đơn FREE: buyerId là người nhận, sellerId là người cho.
 */
public class Order {
    public static final String TYPE_PAID = "PAID";
    public static final String TYPE_FREE = "FREE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_SHIPPING = "SHIPPING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DISPUTED = "DISPUTED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    public static final String PAYMENT_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_PAID = "PAID";
    public static final String PAYMENT_REFUNDED = "REFUNDED";

    public static final String SHIPPING_READY = "READY";
    public static final String SHIPPING_IN_TRANSIT = "IN_TRANSIT";
    public static final String SHIPPING_DELIVERED = "DELIVERED";
    public static final String SHIPPING_RETURNED = "RETURNED";

    private String orderId;
    private String orderType;
    private String buyerId;
    private String sellerId;
    private String postId;
    private long price;
    private long shippingFee;
    private String orderStatus;
    private String paymentStatus;
    private String shippingStatus;
    private String trackingCode;
    private String createdAt;

    public Order() { }

    public Order(String orderId, String orderType, String buyerId, String sellerId, String postId,
                 long price, long shippingFee, String orderStatus, String paymentStatus,
                 String shippingStatus, String trackingCode, String createdAt) {
        this.orderId = orderId;
        this.orderType = orderType;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.postId = postId;
        this.price = price;
        this.shippingFee = shippingFee;
        this.orderStatus = orderStatus;
        this.paymentStatus = paymentStatus;
        this.shippingStatus = shippingStatus;
        this.trackingCode = trackingCode;
        this.createdAt = createdAt;
    }

    public String getOrderId() { return orderId; }
    public String getOrderType() { return orderType; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public String getPostId() { return postId; }
    public long getPrice() { return price; }
    public long getShippingFee() { return shippingFee; }
    public long getTotalAmount() { return price + shippingFee; }
    public String getOrderStatus() { return orderStatus; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getShippingStatus() { return shippingStatus; }
    public String getTrackingCode() { return trackingCode; }
    public String getCreatedAt() { return createdAt; }

    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setShippingStatus(String shippingStatus) { this.shippingStatus = shippingStatus; }

    public boolean isFree() { return TYPE_FREE.equals(orderType); }
}
