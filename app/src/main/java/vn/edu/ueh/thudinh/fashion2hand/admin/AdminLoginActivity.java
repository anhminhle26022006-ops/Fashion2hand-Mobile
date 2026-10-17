package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.User;

/** Đăng nhập Admin + kiểm tra phân quyền (RBAC): chỉ role ADMIN được vào. */
public class AdminLoginActivity extends BaseAdminActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_login);

        EditText email = findViewById(R.id.edtAdminEmail);
        EditText password = findViewById(R.id.edtAdminPassword);

        findViewById(R.id.btnAdminLogin).setOnClickListener(v -> {
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();

            if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                email.setError("Email không hợp lệ");
                return;
            }
            if (passwordValue.isEmpty()) {
                password.setError("Vui lòng nhập mật khẩu");
                return;
            }
            if (!User.ROLE_ADMIN.equals(viewModel.getRoleByEmail(emailValue))) {
                Intent intent = new Intent(this, AdminAccessDeniedActivity.class);
                intent.putExtra(AdminAccessDeniedActivity.EXTRA_EMAIL, emailValue);
                startActivity(intent);
                return;
            }
            if (!viewModel.checkAdminPassword(emailValue, passwordValue)) {
                password.setError("Sai mật khẩu (tài khoản demo: admin123)");
                return;
            }
            Intent intent = new Intent(this, AdminMainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        findViewById(R.id.btnBackToUserApp).setOnClickListener(v -> finish());
    }
}
