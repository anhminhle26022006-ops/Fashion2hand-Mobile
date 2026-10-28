package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.material.materialswitch.MaterialSwitch;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.Category;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;

/** Cài đặt hệ thống: bật/tắt danh mục, quy tắc kiểm duyệt. Lưu trên backend (adminMutate settings). */
public class AdminSettingsActivity extends BaseAdminActivity {

 private LinearLayout categoryList;
 private MaterialSwitch autoHide;
 private MaterialSwitch requirePhone;
 private EditText threshold;
 private EditText completeDays;
 private EditText maxPrice;
 private boolean formFilled;

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
  // Nhóm đã tắt SMS/OTP và backend luôn lưu false, nên ẩn công tắc để Admin không bật nhầm.
  findViewById(R.id.rowSettingRequirePhone).setVisibility(android.view.View.GONE);

  formFilled = viewModel.isLoaded();
  if (savedInstanceState != null) {
   formFilled = true;
   autoHide.setChecked(savedInstanceState.getBoolean("autoHide"));
   requirePhone.setChecked(savedInstanceState.getBoolean("requirePhone"));
   threshold.setText(savedInstanceState.getString("threshold", ""));
   completeDays.setText(savedInstanceState.getString("completeDays", ""));
   maxPrice.setText(savedInstanceState.getString("maxPrice", ""));
  }

  findViewById(R.id.btnAdminAddCategory).setOnClickListener(v ->
          AdminUi.askText(this, "Thêm danh mục", "Ví dụ: Giày dép", "Thêm", name -> {
           if (name.trim().isEmpty()) {
            AdminUi.toast(this, "Tên danh mục không được để trống");
            return;
           }
           viewModel.addCategory(name.trim());
          }));

  findViewById(R.id.btnAdminSaveSettings).setOnClickListener(v -> save());
 }

 /** Giữ số đang sửa khi xoay màn (các ô dùng chung id nên không để Android tự khôi phục). */
 @Override
 protected void onSaveInstanceState(@NonNull Bundle outState) {
  super.onSaveInstanceState(outState);
  outState.putBoolean("autoHide", autoHide.isChecked());
  outState.putBoolean("requirePhone", requirePhone.isChecked());
  outState.putString("threshold", threshold.getText().toString());
  outState.putString("completeDays", completeDays.getText().toString());
  outState.putString("maxPrice", maxPrice.getText().toString());
 }

 /** Dữ liệu backend về: vẽ lại danh mục; điền form một lần để không ghi đè số đang sửa. */
 @Override
 protected void onAdminDataChanged() {
  if (categoryList == null) return;
  renderCategories();
  if (!formFilled && viewModel.isLoaded()) {
   formFilled = true;
   autoHide.setChecked(viewModel.isAutoHideReported());
   requirePhone.setChecked(viewModel.isRequirePhoneBeforeSelling());
   threshold.setText(String.valueOf(viewModel.getReportThreshold()));
   completeDays.setText(String.valueOf(viewModel.getAutoCompleteDays()));
   maxPrice.setText(String.valueOf(viewModel.getMaxPrice()));
  }
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
  if (!viewModel.isLoaded()) {
   AdminUi.toast(this, "Đang tải cấu hình, thử lại sau giây lát");
   return;
  }
  viewModel.saveSettings(autoHide.isChecked(), thresholdValue, daysValue, priceValue, requirePhone.isChecked(),
          this::finish);
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
