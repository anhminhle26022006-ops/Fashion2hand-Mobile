package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

/** Lớp cha cho mọi Activity Admin: tránh tai thỏ/thanh hệ thống và tạo ViewModel. */
public abstract class BaseAdminActivity extends AppCompatActivity {

    protected AdminViewModel viewModel;

    protected void setAdminContent(int layoutId) {
        EdgeToEdge.enable(this);
        setContentView(layoutId);
        viewModel = new ViewModelProvider(this).get(AdminViewModel.class);

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
