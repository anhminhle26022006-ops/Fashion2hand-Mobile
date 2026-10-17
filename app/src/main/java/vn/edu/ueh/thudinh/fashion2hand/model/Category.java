package vn.edu.ueh.thudinh.fashion2hand.model;

/** Danh mục sản phẩm, Admin bật/tắt trong Cài đặt hệ thống. */
public class Category {
    private String categoryId;
    private String name;
    private int postCount;
    private boolean enabled;

    public Category() { }

    public Category(String categoryId, String name, int postCount, boolean enabled) {
        this.categoryId = categoryId;
        this.name = name;
        this.postCount = postCount;
        this.enabled = enabled;
    }

    public String getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public int getPostCount() { return postCount; }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
