package vn.edu.ueh.thudinh.fashion2hand.utils.admin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Locale;

import vn.edu.ueh.thudinh.fashion2hand.R;

/** Các hàm giao diện dùng chung cho khu vực Admin. */
public final class AdminUi {

    public static final int KIND_OK = 0;
    public static final int KIND_WARN = 1;
    public static final int KIND_DANGER = 2;
    public static final int KIND_INFO = 3;
    public static final int KIND_HOLD = 4;
    public static final int KIND_NEUTRAL = 5;

    public static final int BTN_PRIMARY = 0;
    public static final int BTN_OUTLINE = 1;
    public static final int BTN_DANGER = 2;
    public static final int BTN_DANGER_FILL = 3;

    /** {màu nền nhạt, màu chữ đậm} theo từng loại trạng thái. */
    private static final int[][] KIND_COLORS = {
            {R.color.f2h_ok_soft, R.color.f2h_ok},
            {R.color.f2h_warn_soft, R.color.f2h_warn},
            {R.color.f2h_danger_soft, R.color.f2h_danger},
            {R.color.f2h_info_soft, R.color.f2h_info},
            {R.color.f2h_hold_soft, R.color.f2h_hold},
            {R.color.f2h_neutral_soft, R.color.f2h_neutral}
    };

    /** Bảng màu xoay vòng cho avatar chữ cái. */
    private static final int[] AVATAR_KINDS = {KIND_OK, KIND_INFO, KIND_HOLD, KIND_WARN, KIND_DANGER};

    private AdminUi() { }

    public static int softColor(int kind) { return KIND_COLORS[kind][0]; }

    public static int strongColor(int kind) { return KIND_COLORS[kind][1]; }

    public static int avatarKind(int index) { return AVATAR_KINDS[Math.abs(index) % AVATAR_KINDS.length]; }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void tint(View view, int colorRes) {
        view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), colorRes)));
    }

    public static void styleChip(TextView chip, AdminLabels.Label label) {
        if (label == null) {
            chip.setVisibility(View.GONE);
            return;
        }
        chip.setVisibility(View.VISIBLE);
        chip.setText(label.text);
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), strongColor(label.kind)));
        tint(chip, softColor(label.kind));
    }

    public static void styleNotice(TextView notice, String text, int kind) {
        notice.setVisibility(View.VISIBLE);
        notice.setText(text);
        notice.setTextColor(ContextCompat.getColor(notice.getContext(), strongColor(kind)));
        tint(notice, softColor(kind));
    }

    public static void styleButton(TextView button, String text, int style) {
        Context c = button.getContext();
        button.setVisibility(View.VISIBLE);
        button.setEnabled(true);
        button.setAlpha(1f);
        button.setText(text);
        switch (style) {
            case BTN_OUTLINE:
                button.setBackgroundResource(R.drawable.bg_admin_btn_outline);
                button.setTextColor(ContextCompat.getColor(c, R.color.f2h_text));
                break;
            case BTN_DANGER:
                button.setBackgroundResource(R.drawable.bg_admin_btn_danger);
                button.setTextColor(ContextCompat.getColor(c, R.color.f2h_danger));
                break;
            case BTN_DANGER_FILL:
                button.setBackgroundResource(R.drawable.bg_admin_btn_danger_fill);
                button.setTextColor(ContextCompat.getColor(c, R.color.white));
                break;
            default:
                button.setBackgroundResource(R.drawable.bg_admin_btn_primary);
                button.setTextColor(ContextCompat.getColor(c, R.color.white));
        }
    }

    public static String money(long amount) {
        if (amount == 0) return "0₫";
        NumberFormat format = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        return format.format(amount) + "₫";
    }

    /** "Trần Bảo Ngọc" thành "TN". */
    public static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** "0907654321" thành "090****321". */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    /** Bỏ dấu tiếng Việt để tìm kiếm không phân biệt dấu. */
    public static String normalize(String text) {
        if (text == null) return "";
        String noMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /** Hộp xác nhận trước các thao tác khó hoàn tác (khóa tài khoản, gỡ bài...). */
    public static void confirm(Context context, String title, String message, String positive, Runnable onConfirm) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton(positive, (dialog, which) -> onConfirm.run())
                .show();
    }

    public interface TextCallback {
        void onText(String text);
    }

    /** Hộp nhập một dòng chữ (lý do từ chối, tên danh mục...). */
    public static void askText(Context context, String title, String hint, String positive, TextCallback callback) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        int pad = dp(context, 20);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, dp(context, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Hủy", null)
                .setPositiveButton(positive, (dialog, which) -> callback.onText(input.getText().toString().trim()))
                .show();
    }

    public static void bindTopbar(View root, String title, String subtitle, View.OnClickListener onBell) {
        ((TextView) root.findViewById(R.id.txtAdminTitle)).setText(title);
        ((TextView) root.findViewById(R.id.txtAdminSubtitle)).setText(subtitle);
        root.findViewById(R.id.btnAdminBell).setOnClickListener(onBell);
    }

    public static void bindStat(View root, int includeId, String value, String label, String delta) {
        View stat = root.findViewById(includeId);
        ((TextView) stat.findViewById(R.id.txtStatValue)).setText(value);
        ((TextView) stat.findViewById(R.id.txtStatLabel)).setText(label);
        ((TextView) stat.findViewById(R.id.txtStatDelta)).setText(delta);
    }

    public static void bindMenuRow(View root, int includeId, int iconRes, int kind, String title,
                                   String subtitle, AdminLabels.Label count, View.OnClickListener onClick) {
        View row = root.findViewById(includeId);
        ImageView icon = row.findViewById(R.id.imgMenuIcon);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(row.getContext(), strongColor(kind))));
        tint(icon, softColor(kind));
        TextView titleView = row.findViewById(R.id.txtMenuTitle);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(row.getContext(),
                kind == KIND_DANGER ? R.color.f2h_danger : R.color.f2h_text));
        ((TextView) row.findViewById(R.id.txtMenuSubtitle)).setText(subtitle);
        styleChip(row.findViewById(R.id.txtMenuCount), count);
        row.setOnClickListener(onClick);
    }

    public static MaterialSwitch bindSwitchRow(View row, String title, String subtitle, boolean checked,
                                               CompoundButton.OnCheckedChangeListener listener) {
        ((TextView) row.findViewById(R.id.txtSwitchTitle)).setText(title);
        ((TextView) row.findViewById(R.id.txtSwitchSubtitle)).setText(subtitle);
        MaterialSwitch sw = row.findViewById(R.id.switchAdminRow);
        // Các dòng dùng chung id: tắt tự khôi phục khi xoay để không lẫn giá trị / tự bật tắt danh mục.
        sw.setSaveEnabled(false);
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        return sw;
    }

    public static EditText bindInputRow(View row, String title, String subtitle, String value) {
        ((TextView) row.findViewById(R.id.txtInputTitle)).setText(title);
        ((TextView) row.findViewById(R.id.txtInputSubtitle)).setText(subtitle);
        EditText input = row.findViewById(R.id.edtAdminInput);
        input.setSaveEnabled(false);
        input.setText(value);
        return input;
    }

    public static void addKv(LinearLayout container, String key, String value) {
        View row = LayoutInflater.from(container.getContext()).inflate(R.layout.item_admin_kv, container, false);
        ((TextView) row.findViewById(R.id.txtKvKey)).setText(key);
        ((TextView) row.findViewById(R.id.txtKvValue)).setText(value);
        container.addView(row);
    }

    public static void addKvChip(LinearLayout container, String key, AdminLabels.Label label) {
        View row = LayoutInflater.from(container.getContext()).inflate(R.layout.item_admin_kv, container, false);
        ((TextView) row.findViewById(R.id.txtKvKey)).setText(key);
        row.findViewById(R.id.txtKvValue).setVisibility(View.GONE);
        styleChip(row.findViewById(R.id.txtKvChip), label);
        container.addView(row);
    }

    public static void addChip(LinearLayout container, AdminLabels.Label label) {
        TextView chip = (TextView) LayoutInflater.from(container.getContext())
                .inflate(R.layout.item_admin_chip, container, false);
        styleChip(chip, label);
        container.addView(chip);
    }

    /** Hàng ô ảnh bằng chứng giả lập. */
    /** Hàng ô ảnh; trả về các ô để nơi gọi thay bằng ảnh thật khi tải xong (showEvidenceImage). */
    public static TextView[] addEvidenceRow(LinearLayout parent, String... labels) {
        Context c = parent.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] cells = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView cell = (TextView) LayoutInflater.from(c).inflate(R.layout.item_admin_evidence, row, false);
            cell.setText(labels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 88), 1f);
            if (i > 0) lp.setMarginStart(dp(c, 8));
            row.addView(cell, lp);
            cells[i] = cell;
        }
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(c, 10);
        parent.addView(row, rowLp);
        return cells;
    }

    /** Giải mã ảnh, thu nhỏ cạnh dài về khoảng maxSide pixel để không tốn bộ nhớ. */
    public static Bitmap decodeImage(byte[] bytes, int maxSide) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= maxSide) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    /** Đặt ảnh thật (cắt vuông, bo góc) vào ô đầu dòng hoặc ô lớn đầu trang chi tiết thay cho icon. */
    public static void showImage(TextView target, byte[] bytes) {
        Bitmap bitmap = decodeImage(bytes, 320);
        if (bitmap == null) return;
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = ThumbnailUtils.extractThumbnail(bitmap, side, side);
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(target.getResources(), square);
        drawable.setCornerRadius(side * 0.22f);
        target.setText("");
        target.setBackgroundTintList(null);
        target.setBackground(drawable);
    }

    /** Trả ô đầu dòng về nền mặc định (dùng khi RecyclerView tái sử dụng ô đã có ảnh). */
    public static void resetImage(TextView target) {
        target.setBackgroundResource(R.drawable.bg_admin_round);
    }

    /** Thay một ô ảnh giữ chỗ bằng ảnh thật; bấm vào để xem ảnh lớn. */
    public static void showEvidenceImage(TextView cell, byte[] bytes) {
        if (!(cell.getParent() instanceof ViewGroup)) return;
        Bitmap bitmap = decodeImage(bytes, 1200);
        if (bitmap == null) { cell.setText(cell.getText() + "\nKhông đọc được ảnh"); return; }
        Context c = cell.getContext();
        ViewGroup parent = (ViewGroup) cell.getParent();
        int index = parent.indexOfChild(cell);
        ImageView image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundResource(R.drawable.bg_admin_evidence);
        image.setClipToOutline(true);
        image.setImageBitmap(bitmap);
        image.setContentDescription(cell.getText());
        image.setOnClickListener(v -> {
            ImageView large = new ImageView(c);
            large.setAdjustViewBounds(true);
            large.setImageBitmap(bitmap);
            new AlertDialog.Builder(c).setTitle(cell.getText()).setView(large).setPositiveButton("Đóng", null).show();
        });
        parent.removeViewAt(index);
        parent.addView(image, index, cell.getLayoutParams());
    }
}