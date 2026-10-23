package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;
import vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin.AdminViewModel;

/**
 * Lớp cha cho mọi Activity Admin: tránh tai thỏ/thanh hệ thống, tạo ViewModel,
 * tải dữ liệu từ backend khi mở màn và đưa về màn đăng nhập nếu mất quyền Admin.
 */
public abstract class BaseAdminActivity extends AppCompatActivity {

    protected AdminViewModel viewModel;

    protected void setAdminContent(int layoutId) {
        EdgeToEdge.enable(this);
        setContentView(layoutId);
        viewModel = new ViewModelProvider(this).get(AdminViewModel.class);
        if (requiresAdminSession()) observeBackend();

        ViewGroup content = findViewById(android.R.id.content);
        View root = content.getChildAt(0);
        View bottomTarget = bottomInsetTarget();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            if (bottomTarget == null) {
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                // Thanh điều hướng tự kéo dài xuống dưới thanh hệ thống
                view.setPadding(bars.left, bars.top, bars.right, 0);
                bottomTarget.setPadding(bottomTarget.getPaddingLeft(), bottomTarget.getPaddingTop(),
                        bottomTarget.getPaddingRight(), bars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Màn cần phiên Admin hợp lệ (mặc định có). Màn Từ chối truy cập trả về false. */
    protected boolean requiresAdminSession() {
        return true;
    }

    /** Gọi mỗi khi dữ liệu backend thay đổi (tải xong, lưu xong, lỗi). Màn con vẽ lại tại đây. */
    protected void onAdminDataChanged() { }

    @Override
    protected void onStart() {
        super.onStart();
        if (viewModel != null && requiresAdminSession()) viewModel.refreshIfStale();
    }

    private void observeBackend() {
        viewModel.getDataVersion().observe(this, version -> onAdminDataChanged());
        viewModel.getMessage().observe(this, event -> {
            String text = event == null ? null : event.take();
            if (text != null) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        });
        viewModel.getSessionDenied().observe(this, event -> {
            Boolean denied = event == null ? null : event.take();
            if (denied == null || !denied) return;
            Toast.makeText(this, "Phiên Admin không còn quyền truy cập. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, AdminLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    /** View nhận phần đệm phía dưới thay cho cả màn hình (mặc định: không có). */
    protected View bottomInsetTarget() {
        return null;
    }

    protected void bindBackBar(String title) {
        findViewById(R.id.btnAdminBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        ((TextView) findViewById(R.id.txtAdminBackTitle)).setText(title);
    }

    protected void setBackBarChip(AdminLabels.Label label) {
        AdminUi.styleChip(findViewById(R.id.txtAdminBackChip), label);
    }
}
