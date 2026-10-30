package vn.edu.ueh.thudinh.fashion2hand.utils;

/**
 * Một nguồn danh mục duy nhất cho Đăng bán, Trang chủ và Khám phá.
 * Bài đăng lưu danh mục chi tiết (Áo thun, Giày dép...), còn bộ lọc dùng nhóm (Áo, Giày dép...).
 * Một nhóm khớp mọi danh mục có cùng tiền tố, nên dữ liệu cũ ("Áo", "Giày", "Túi") vẫn lọc ra được.
 */
public final class ProductCategories {

    private ProductCategories() { }

    public static final String ALL = "Tất cả";

    /** Danh mục khi đăng bán. Giữ nguyên các giá trị này để bài đăng cũ không bị lệch. */
    public static final String[] LISTING = {"Áo khoác", "Áo thun", "Áo sơ mi", "Quần", "Váy", "Đầm", "Giày dép", "Túi xách", "Phụ kiện"};

    /** Nhóm dùng chung cho bộ lọc Khám phá và chip Trang chủ. */
    public static final String[] FILTERS = {ALL, "Áo", "Quần", "Váy & Đầm", "Giày dép", "Túi xách", "Phụ kiện"};

    /** Tiền tố (đã bỏ dấu) mà danh mục của bài đăng phải bắt đầu bằng, theo từng nhóm. */
    private static String[] prefixes(String filter) {
        switch (SearchText.key(filter)) {
            case "ao": return new String[]{"ao"};
            case "quan": return new String[]{"quan"};
            case "vay & dam": case "vay": case "dam": return new String[]{"vay", "dam"};
            case "giay dep": case "giay": return new String[]{"giay"};
            case "tui xach": case "tui": return new String[]{"tui"};
            case "phu kien": return new String[]{"phu kien"};
            default: return new String[]{SearchText.key(filter)};
        }
    }

    /** Bài đăng có thuộc nhóm đang lọc không. Bài thiếu danh mục thì đoán theo tên sản phẩm. */
    public static boolean matches(String filter, String category, String productName) {
        if (filter == null || filter.isEmpty() || ALL.equals(filter)) return true;
        String source = SearchText.key(category);
        if (source.isEmpty()) source = SearchText.key(productName);
        for (String prefix : prefixes(filter)) {
            if (source.equals(prefix) || source.startsWith(prefix + " ")) return true;
        }
        return false;
    }
}