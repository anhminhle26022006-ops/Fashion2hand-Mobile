package vn.edu.ueh.thudinh.fashion2hand.model;

/** Báo cáo vi phạm. targetType cho biết targetId là postId, userId hay orderId. */
public class Report {
    public static final String TARGET_POST = "POST";
    public static final String TARGET_USER = "USER";
    public static final String TARGET_ORDER = "ORDER";

    public static final String PENDING = "PENDING";
    public static final String REVIEWING = "REVIEWING";
    public static final String RESOLVED = "RESOLVED";
    public static final String REJECTED = "REJECTED";

    private String reportId;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String description;
    private String status;
    private String createdAt;

    public Report() { }

    public Report(String reportId, String reporterId, String targetType, String targetId,
                  String reason, String description, String status, String createdAt) {
        this.reportId = reportId;
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getReportId() { return reportId; }
    public String getReporterId() { return reporterId; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }

    public boolean isOpen() { return PENDING.equals(status) || REVIEWING.equals(status); }
}
