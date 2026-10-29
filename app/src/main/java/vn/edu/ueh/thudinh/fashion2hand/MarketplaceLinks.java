package vn.edu.ueh.thudinh.fashion2hand;

/** Shared validation for SMS numbers and links received from outside the app. */
public final class MarketplaceLinks {
    private MarketplaceLinks() { }

    public static String normalizePhone(String input) {
        String phone = input == null ? "" : input.replaceAll("[\\s().-]", "");
        if (phone.startsWith("0")) phone = "+84" + phone.substring(1);
        if (!phone.matches("\\+[1-9][0-9]{7,14}")) return "";
        return phone;
    }

    public static boolean validPostId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,128}");
    }

    public static String productLink(String postId) {
        if (!validPostId(postId)) throw new IllegalArgumentException("Mã bài đăng không hợp lệ.");
        return "fashion2hand://post/" + postId;
    }
}
