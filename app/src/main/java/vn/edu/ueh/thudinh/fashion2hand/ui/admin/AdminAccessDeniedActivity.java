package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import vn.edu.ueh.thudinh.fashion2hand.MainActivity;
import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;

/** Màn hiện khi tài khoản USER cố vào khu vực Admin. */
public class AdminAccessDeniedActivity extends BaseAdminActivity {

    public static final String EXTRA_EMAIL = "denied_email";

    @Override
    protected boolean requiresAdminSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_access_denied);

        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        LinearLayout info = findViewById(R.id.layoutDeniedInfo);
        AdminUi.addKv(info, "Tài khoản", email == null ? "Không rõ" : email);
        AdminUi.addKvChip(info, "Vai trò hiện tại", new AdminLabels.Label("USER", AdminUi.KIND_NEUTRAL));
        AdminUi.addKvChip(info, "Yêu cầu", new AdminLabels.Label("ADMIN", AdminUi.KIND_OK));

        findViewById(R.id.btnDeniedBackToUser).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.btnDeniedOtherAccount).setOnClickListener(v -> finish());
    }
}
