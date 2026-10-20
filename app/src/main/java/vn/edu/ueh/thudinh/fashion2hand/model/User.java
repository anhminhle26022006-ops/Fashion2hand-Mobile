package vn.edu.ueh.thudinh.fashion2hand.model;

/** Người dùng hệ thống. Theo class diagram: tham chiếu đối tượng khác bằng id dạng String. */
public class User {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String VERIFIED = "VERIFIED";
    public static final String VERIFY_PENDING = "PENDING";
    public static final String UNVERIFIED = "UNVERIFIED";

    public static final String ACCOUNT_ACTIVE = "ACTIVE";
    public static final String ACCOUNT_LOCKED = "LOCKED";

    private String userId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String verificationStatus;
    private String accountStatus;
    private String area;
    private String joinedAt;
    private int postCount;
    private int buyCount;
    private int sellCount;
    private int reportedCount;

    public User() { }

    public User(String userId, String name, String email, String phone, String role,
                String verificationStatus, String accountStatus, String area, String joinedAt,
                int postCount, int buyCount, int sellCount, int reportedCount) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.verificationStatus = verificationStatus;
        this.accountStatus = accountStatus;
        this.area = area;
        this.joinedAt = joinedAt;
        this.postCount = postCount;
        this.buyCount = buyCount;
        this.sellCount = sellCount;
        this.reportedCount = reportedCount;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getAccountStatus() { return accountStatus; }
    public String getArea() { return area; }
    public String getJoinedAt() { return joinedAt; }
    public int getPostCount() { return postCount; }
    public int getBuyCount() { return buyCount; }
    public int getSellCount() { return sellCount; }
    public int getReportedCount() { return reportedCount; }

    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public boolean isLocked() { return ACCOUNT_LOCKED.equals(accountStatus); }
}
