package vn.edu.ueh.thudinh.fashion2hand.ui.report;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/** Hộp thoại báo cáo vi phạm cho bài đăng hoặc người dùng; gửi qua Cloud Function submitReport. */
public final class ReportDialog {

    public static final String TARGET_POST = "POST";
    public static final String TARGET_USER = "USER";

    private static final String[] POST_REASONS = {
            "Hàng giả / hàng nhái", "Lừa đảo", "Nội dung sai sự thật",
            "Hình ảnh hoặc ngôn từ không phù hợp", "Spam / quảng cáo", "Khác"};
    private static final String[] USER_REASONS = {
            "Lừa đảo", "Spam / quảng cáo", "Quấy rối hoặc xúc phạm", "Mạo danh người khác", "Khác"};

    private ReportDialog() { }

    public static void show(Activity activity, String targetType, String targetId) {
        if (targetId == null || targetId.isEmpty()) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(activity, "Vui lòng đăng nhập để báo cáo.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] reasons = TARGET_USER.equals(targetType) ? USER_REASONS : POST_REASONS;
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad / 2, pad, 0);

        RadioGroup group = new RadioGroup(activity);
        for (int i = 0; i < reasons.length; i++) {
            RadioButton option = new RadioButton(activity);
            option.setId(i + 1);
            option.setText(reasons[i]);
            option.setMinHeight(Math.round(44 * activity.getResources().getDisplayMetrics().density));
            group.addView(option);
        }
        body.addView(group);

        EditText description = new EditText(activity);
        description.setHint("Mô tả thêm (không bắt buộc)");
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setMinLines(2);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        body.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView error = new TextView(activity);
        error.setTextColor(0xFFB95242);
        body.addView(error);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(TARGET_USER.equals(targetType) ? "Báo cáo người dùng" : "Báo cáo bài đăng")
                .setView(body)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi báo cáo", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            send.setOnClickListener(v -> {
                int checked = group.getCheckedRadioButtonId();
                if (checked < 1) { error.setText("Chọn một lý do báo cáo."); return; }
                String reason = reasons[checked - 1];
                String detail = description.getText().toString().trim();
                if ("Khác".equals(reason) && detail.length() < 10) { error.setText("Mô tả ít nhất 10 ký tự khi chọn \"Khác\"."); return; }
                error.setText("");
                send.setEnabled(false);
                submit(activity, dialog, send, error, targetType, targetId, reason, detail);
            });
        });
        dialog.show();
    }

    private static void submit(Activity activity, AlertDialog dialog, Button send, TextView error,
                               String targetType, String targetId, String reason, String detail) {
        Map<String, Object> args = new HashMap<>();
        args.put("targetType", targetType);
        args.put("targetId", targetId);
        args.put("reason", reason);
        args.put("description", detail);
        FirebaseFunctions.getInstance().getHttpsCallable("submitReport").call(args)
                .addOnSuccessListener(result -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    dialog.dismiss();
                    Toast.makeText(activity, "Đã gửi báo cáo. Quản trị viên sẽ xem xét sớm.", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(failure -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    send.setEnabled(true);
                    String message = failure.getLocalizedMessage();
                    error.setText(message == null || message.isEmpty() ? "Chưa gửi được báo cáo. Vui lòng thử lại." : message);
                });
    }
}
