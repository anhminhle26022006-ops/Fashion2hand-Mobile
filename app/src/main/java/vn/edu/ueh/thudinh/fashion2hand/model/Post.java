package vn.edu.ueh.thudinh.fashion2hand.model;

/** Bài đăng quần áo. listingType: FREE (cho tặng) hoặc FOR_SALE (đăng bán). */
public class Post {
    public static final String TYPE_FREE = "FREE";
    public static final String TYPE_FOR_SALE = "FOR_SALE";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_HIDDEN = "HIDDEN";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_GIVEN = "GIVEN";

    private String postId;
    private String userId;
    private String title;
    private String category;
    private String size;
    private String condition;
    private String location;
    private String listingType;
    private long price;
    private String status;
    private int reportCount;
    private String reportReasons;
    private String createdAt;

    public Post() { }

    public Post(String postId, String userId, String title, String category, String size,
                String condition, String location, String listingType, long price, String status,
                int reportCount, String reportReasons, String createdAt) {
        this.postId = postId;
        this.userId = userId;
        this.title = title;
        this.category = category;
        this.size = size;
        this.condition = condition;
        this.location = location;
        this.listingType = listingType;
        this.price = price;
        this.status = status;
        this.reportCount = reportCount;
        this.reportReasons = reportReasons;
        this.createdAt = createdAt;
    }

    public String getPostId() { return postId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSize() { return size; }
    public String getCondition() { return condition; }
    public String getLocation() { return location; }
    public String getListingType() { return listingType; }
    public long getPrice() { return price; }
    public String getStatus() { return status; }
    public int getReportCount() { return reportCount; }
    public String getReportReasons() { return reportReasons; }
    public String getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }

    public boolean isFree() { return TYPE_FREE.equals(listingType); }
}
