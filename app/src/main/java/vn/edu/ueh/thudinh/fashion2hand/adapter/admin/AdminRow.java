package vn.edu.ueh.thudinh.fashion2hand.adapter.admin;

import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;

/** Dữ liệu hiển thị cho một dòng trong danh sách Admin (đã đổi sang nhãn tiếng Việt). */
public class AdminRow {
    public final String id;
    public String leading = "";
    public int leadingKind = AdminUi.KIND_OK;
    public String title = "";
    public String subtitle = "";
    public AdminLabels.Label chip;
    public AdminLabels.Label chip2;
    public String trailing = "";

    public AdminRow(String id) {
        this.id = id;
    }

    public AdminRow setLeading(String text, int kind) {
        this.leading = text;
        this.leadingKind = kind;
        return this;
    }

    public AdminRow setTitle(String title) {
        this.title = title;
        return this;
    }

    public AdminRow setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public AdminRow setChip(AdminLabels.Label chip) {
        this.chip = chip;
        return this;
    }

    public AdminRow setChip2(AdminLabels.Label chip2) {
        this.chip2 = chip2;
        return this;
    }

    public AdminRow setTrailing(String trailing) {
        this.trailing = trailing;
        return this;
    }

    /** Chuỗi gộp để tìm kiếm không dấu. */
    public String searchText() {
        return AdminUi.normalize(title + " " + subtitle + " " + trailing
                + " " + (chip == null ? "" : chip.text) + " " + (chip2 == null ? "" : chip2.text));
    }
}
