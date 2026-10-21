package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.materialswitch.MaterialSwitch;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;

/** Cài đặt hệ thống: bật/tắt danh mục, quy tắc kiểm duyệt. */
public class AdminSettingsActivity extends BaseAdminActivity {

    private LinearLayout categoryList;
    private MaterialSwitch autoHide;
    private MaterialSwitch requirePhone;
    private EditText threshold;
    private EditText completeDays;
    private EditText maxPrice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_settings);
        bindBackBar("Cài đặt hệ thống");

        categoryList = findViewById(R.id.layoutAdminCategories);
        renderCategories();

        autoHide = AdminUi.bindSwitchRow(findViewById(R.id.rowSettingAutoHide), "Tự động ẩn bài bị báo cáo",
                "Ẩn bài khi số báo cáo đạt ngưỡng bên dưới", viewModel.isAutoHideReported(), null);
        threshold = AdminUi.bindInputRow(findViewById(R.id.rowSettingThreshold), "Ngưỡng báo cáo",
                "Số lần bị báo cáo (1 đến 20)", String.valueOf(viewModel.getReportThreshold()));
        completeDays = AdminUi.bindInputRow(findViewById(R.id.rowSettingCompleteDays), "Tự hoàn tất đơn",
                "Số ngày sau khi giao nếu người mua không phản hồi", String.valueOf(viewModel.getAutoCompleteDays()));
        maxPrice = AdminUi.bindInputRow(findViewById(R.id.rowSettingMaxPrice), "Giá bán tối đa (₫)",
                "Áp dụng cho bài đăng bán", String.valueOf(viewModel.getMaxPrice()));
        requirePhone = AdminUi.bindSwitchRow(findViewById(R.id.rowSettingRequirePhone), "Bắt buộc xác minh SĐT",
                "Người dùng phải xác thực OTP trước khi đăng bán", viewModel.isRequirePhoneBeforeSelling(), null);

        findViewById(R.id.btnAdminAddCategory).setOnClickListener(v ->
                AdminUi.askText(this, "Thêm danh mục", "Ví dụ: Giày dép", "Thêm", name -> {
                    if (name.isEmpty()) {
                        AdminUi.toast(this, "Tên danh mục không được để trống");
                        return;
                    }
                    viewModel.addCategory(name);
                    renderCategories();
                    AdminUi.toast(this, "Đã thêm danh mục " + name);
                }));

        findViewById(R.id.btnAdminSaveSettings).setOnClickListener(v -> save());
    }

    private void renderCategories() {
        categoryList.removeAllViews();
        for (Category c : viewModel.getCategories()) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_admin_switch_row, categoryList, false);
            AdminUi.bindSwitchRow(row, c.getName(), c.getPostCount() + " bài đăng", c.isEnabled(),
                    (button, checked) -> viewModel.setCategoryEnabled(c.getCategoryId(), checked));
            categoryList.addView(row);
        }
    }

    private void save() {
        int thresholdValue = parseInt(threshold);
        int daysValue = parseInt(completeDays);
        long priceValue = parseLong(maxPrice);
        if (thresholdValue < 1 || thresholdValue > 20) {
            threshold.setError("Nhập từ 1 đến 20");
            return;
        }
        if (daysValue < 1 || daysValue > 14) {
            completeDays.setError("Nhập từ 1 đến 14 ngày");
            return;
        }
        if (priceValue < 10_000) {
            maxPrice.setError("Tối thiểu 10.000₫");
            return;
        }
        viewModel.saveSettings(autoHide.isChecked(), thresholdValue, daysValue, priceValue, requirePhone.isChecked());
        AdminUi.toast(this, "Đã lưu cài đặt");
        finish();
    }

    private int parseInt(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long parseLong(EditText input) {
        try {
            return Long.parseLong(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
