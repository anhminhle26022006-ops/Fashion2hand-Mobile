package vn.edu.ueh.thudinh.fashion2hand.model;

/** Hồ sơ xác minh tài khoản (Verification Simulation, chưa tích hợp VNeID). */
public class VerificationRequest {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private String requestId;
    private String userId;
    private String requestType;
    private String documentType;
    private boolean phoneVerified;
    private String status;
    private String createdAt;
    private String note;

    public VerificationRequest() { }

    public VerificationRequest(String requestId, String userId, String requestType,
                               String documentType, boolean phoneVerified, String status,
                               String createdAt, String note) {
        this.requestId = requestId;
        this.userId = userId;
        this.requestType = requestType;
        this.documentType = documentType;
        this.phoneVerified = phoneVerified;
        this.status = status;
        this.createdAt = createdAt;
        this.note = note;
    }

    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public String getRequestType() { return requestType; }
    public String getDocumentType() { return documentType; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getNote() { return note; }

    public void setStatus(String status) { this.status = status; }
    public void setNote(String note) { this.note = note; }
}
