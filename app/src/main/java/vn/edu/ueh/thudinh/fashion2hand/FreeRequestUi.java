package vn.edu.ueh.thudinh.fashion2hand;

import java.util.Locale;

/** Display labels only; the full server identifier remains the document key. */
final class FreeRequestUi {
    static String code(String id) {
        String compact=id==null?"":id.replaceAll("[^A-Za-z0-9]","");
        return "YC-"+compact.substring(0,Math.min(10,compact.length())).toUpperCase(Locale.ROOT);
    }
    static boolean alreadySent(String status) { return "PENDING".equals(status)||"ACCEPTED".equals(status); }
    static String action(String status) { return "ACCEPTED".equals(status)?"Đã chấp nhận":"Đã gửi yêu cầu"; }
}
