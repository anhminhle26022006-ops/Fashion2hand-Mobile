package vn.edu.ueh.thudinh.fashion2hand.utils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuẩn hóa chữ để tìm kiếm tiếng Việt: không phân biệt hoa/thường, có dấu/không dấu
 * và dạng Unicode (dựng sẵn hay tổ hợp), nên gõ "ao khoac" vẫn ra "Áo khoác".
 */
public final class SearchText {

    private SearchText() { }

    public static String key(String value) {
        if (value == null) return "";
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Ghép các trường của bài đăng thành một chuỗi đã chuẩn hóa để tìm. */
    public static String keyOf(String... parts) {
        StringBuilder all = new StringBuilder();
        for (String part : parts) if (part != null && !part.isEmpty()) all.append(part).append(' ');
        return key(all.toString());
    }

    /** Mọi từ trong câu tìm đều phải xuất hiện (không cần đúng thứ tự). Câu tìm rỗng thì khớp tất cả. */
    public static boolean matches(String searchableKey, String query) {
        String q = key(query);
        if (q.isEmpty()) return true;
        String haystack = searchableKey == null ? "" : searchableKey;
        for (String word : q.split(" ")) if (!haystack.contains(word)) return false;
        return true;
    }
}
