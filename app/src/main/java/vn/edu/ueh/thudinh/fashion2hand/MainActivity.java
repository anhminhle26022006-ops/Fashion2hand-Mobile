package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Intent;
import vn.edu.ueh.thudinh.fashion2hand.admin.AdminLoginActivity;
import vn.edu.ueh.thudinh.fashion2hand.repository.AdminRepository;

/** Main entry point for the Fashion2hand frontend prototype. Uses local sample data only. */
public class MainActivity extends AppCompatActivity {
    private int currentScreen = R.layout.activity_login;
    private String selectedCategory = "Tất cả";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        showScreen(R.layout.activity_login);
    }

    private void showScreen(int layoutId) {
        currentScreen = layoutId;
        setContentView(layoutId);
        applySystemBarInsets();
        if (layoutId == R.layout.activity_login) bindLogin();
        bindNavigation();
        if (layoutId == R.layout.activity_home) bindHome();
        if (layoutId == R.layout.activity_explore) bindExplore();
        if (layoutId == R.layout.activity_sell) bindSell();
        if (layoutId == R.layout.activity_request) bindRequest();
        if (layoutId == R.layout.activity_messages) bindMessages();
        if (layoutId == R.layout.activity_profile) bindProfile();
        if (layoutId == R.layout.activity_product_detail) bindProductDetail();
    }

    private void applySystemBarInsets() {
        android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root == null || root.getChildCount() == 0) return;
        View content = root.getChildAt(0);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    bars.top,
                    view.getPaddingRight(),
                    bars.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void bindLogin() {
        EditText email = findViewById(R.id.edtLoginEmail);
        EditText password = findViewById(R.id.edtLoginPassword);

        bindClick(R.id.btnLogin, () -> {
            String emailValue = email == null ? "" : email.getText().toString().trim();
            String passwordValue = password == null ? "" : password.getText().toString();

            if (emailValue.isEmpty()) {
                if (email != null) email.setError("Vui lòng nhập email");
                return;
            }
            if (passwordValue.isEmpty()) {
                if (password != null) password.setError("Vui lòng nhập mật khẩu");
                return;
            }

            // Phân quyền: tài khoản ADMIN được chuyển sang khu vực quản trị
            if (AdminRepository.getInstance().isAdminEmail(emailValue)) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                return;
            }

            Toast.makeText(this, "Đăng nhập demo thành công.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_home);
        });

        bindClick(R.id.btnOpenAdminLogin, () ->
                startActivity(new Intent(this, AdminLoginActivity.class)));

        bindClick(R.id.btnOpenRegister, () ->
                Toast.makeText(this, "Màn hình đăng ký sẽ được triển khai sau.", Toast.LENGTH_SHORT).show());

        bindClick(R.id.btnForgotPassword, () ->
                Toast.makeText(this, "Chức năng quên mật khẩu sẽ được triển khai sau.", Toast.LENGTH_SHORT).show());
    }

    private void bindNavigation() {
        bindClick(R.id.navHome, () -> showScreen(R.layout.activity_home));
        bindClick(R.id.navExplore, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.navCreate, () -> showScreen(R.layout.activity_sell));
        bindClick(R.id.navMessages, () -> showScreen(R.layout.activity_messages));
        bindClick(R.id.navProfile, () -> showScreen(R.layout.activity_profile));
    }

    private void bindHome() {
        EditText search = findViewById(R.id.edtSearch);
        if (search != null) {
            search.setOnClickListener(view -> {
                showScreen(R.layout.activity_explore);
                EditText exploreSearch = findViewById(R.id.edtExploreSearch);
                if (exploreSearch != null) exploreSearch.requestFocus();
            });
            search.setOnEditorActionListener((v, actionId, event) -> {
                showScreen(R.layout.activity_explore);
                EditText exploreSearch = findViewById(R.id.edtExploreSearch);
                if (exploreSearch != null) exploreSearch.requestFocus();
                return true;
            });
        }
        bindClick(R.id.btnSeeAllHome, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.btnNotificationsHome, () -> Toast.makeText(this, "Bạn chưa có thông báo mới.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnAll, () -> openExploreWithCategory("Tất cả"));
        bindClick(R.id.btnAo, () -> openExploreWithCategory("Áo"));
        bindClick(R.id.btnQuan, () -> openExploreWithCategory("Quần"));
        bindClick(R.id.btnVay, () -> openExploreWithCategory("Váy"));
        bindClick(R.id.productCard1, () -> openProduct("Áo thun basic", "👕", "Miễn phí · 0đ"));
        bindClick(R.id.productCard2, () -> openProduct("Váy hoa vintage", "👗", "Miễn phí · 0đ"));
        bindClick(R.id.productCard3, () -> openProduct("Quần jeans xanh", "👖", "Miễn phí · 0đ"));
        bindClick(R.id.productCard4, () -> openProduct("Áo khoác denim", "🥼", "Miễn phí · 0đ"));
    }

    private void openExploreWithCategory(String category) {
        selectedCategory = category;
        showScreen(R.layout.activity_explore);
        applyExploreFilter(category);
    }

    private void bindExplore() {
        EditText search = findViewById(R.id.edtExploreSearch);
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterProductCards(s.toString()); }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        bindClick(R.id.btnOpenFilterSheet, this::showExploreFilterSheet);
        GridLayout grid = findViewById(R.id.exploreProductGrid);
        if (grid != null && grid.getChildCount() == 0) addSampleProducts(grid);
        applyExploreFilter(selectedCategory);
    }

    private void showExploreFilterSheet() {
        String[] categories = {"Tất cả", "Áo", "Quần", "Váy"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Lọc theo danh mục")
                .setItems(categories, (dialog, which) -> applyExploreFilter(categories[which]))
                .show();
    }

    private void addSampleProducts(GridLayout grid) {
        String[][] products = {
                {"👕", "Áo thun basic", "Size M · Tốt", "0đ"},
                {"👗", "Váy hoa vintage", "Size S · Rất tốt", "0đ"},
                {"👖", "Quần jeans xanh", "Size L · Tốt", "0đ"},
                {"🥼", "Áo khoác denim", "Size M · Tốt", "120.000đ"}
        };
        for (String[] product : products) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(10, 12, 10, 12);
            card.setBackgroundColor(0xFFFFFFFF);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, 10, 12);
            card.setLayoutParams(params);
            TextView emoji = new TextView(this);
            emoji.setText(product[0]); emoji.setTextSize(42); emoji.setGravity(17);
            emoji.setBackgroundColor(0xFFDDF0E4);
            card.addView(emoji, new LinearLayout.LayoutParams(-1, dp(105)));
            TextView name = new TextView(this); name.setText(product[1]); name.setTextSize(14); name.setTextColor(0xFF173B32); name.setPadding(0, dp(8), 0, dp(3));
            card.addView(name);
            TextView meta = new TextView(this); meta.setText(product[2]); meta.setTextSize(11); meta.setTextColor(0xFF75857F); card.addView(meta);
            TextView price = new TextView(this); price.setText(product[3]); price.setTextSize(13); price.setTextColor(0xFF277B5C); price.setPadding(0, dp(5), 0, 0); card.addView(price);
            card.setTag(product[1]);
            card.setOnClickListener(v -> openProduct(product[1], product[0], product[3]));
            grid.addView(card);
        }
    }

    private void applyExploreFilter(String category) {
        selectedCategory = category;
        filterProductCards(findViewById(R.id.edtExploreSearch) instanceof EditText
                ? ((EditText) findViewById(R.id.edtExploreSearch)).getText().toString() : "");
    }

    private void filterProductCards(String query) {
        GridLayout grid = findViewById(R.id.exploreProductGrid);
        if (grid == null) return;
        int visible = 0;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            String name = String.valueOf(child.getTag());
            boolean matchesCategory = "Tất cả".equals(selectedCategory)
                    || ("Áo".equals(selectedCategory) && name.startsWith("Áo"))
                    || ("Quần".equals(selectedCategory) && name.startsWith("Quần"))
                    || ("Váy".equals(selectedCategory) && name.startsWith("Váy"));
            boolean matchesQuery = name.toLowerCase().contains(query.toLowerCase().trim());
            child.setVisibility(matchesCategory && matchesQuery ? View.VISIBLE : View.GONE);
            if (matchesCategory && matchesQuery) visible++;
        }
        TextView count = findViewById(R.id.txtExploreCount);
        if (count != null) count.setText(visible + " sản phẩm phù hợp");
    }

    private void bindSell() {
        Spinner condition = findViewById(R.id.spinnerCondition);
        if (condition != null) {
            String[] conditions = {"Tốt", "Rất tốt", "Như mới", "Có dấu hiệu sử dụng"};
            condition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, conditions));
        }
        bindClick(R.id.btnChoosePhotos, () -> Toast.makeText(this, "Chức năng chọn ảnh sẽ được tích hợp ở bước tiếp theo.", Toast.LENGTH_LONG).show());
        bindClick(R.id.btnPublishListing, () -> {
            EditText name = findViewById(R.id.edtProductName);
            if (name == null || name.getText().toString().trim().isEmpty()) {
                if (name != null) name.setError("Vui lòng nhập tên sản phẩm");
                return;
            }
            Toast.makeText(this, "Đã kiểm tra thông tin mẫu. Chưa đăng lên hệ thống.", Toast.LENGTH_LONG).show();
        });
    }

    private void bindRequest() {
        bindClick(R.id.btnRequestAccept, () -> Toast.makeText(this, "Đây là giao diện demo; chưa gửi yêu cầu thật.", Toast.LENGTH_LONG).show());
    }

    private void bindMessages() {
        EditText search = findViewById(R.id.edtSearchMessage);
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                for (int id : new int[]{R.id.conversation1, R.id.conversation2, R.id.conversation3}) {
                    View row = findViewById(id);
                    if (row instanceof LinearLayout) {
                        LinearLayout conversation = (LinearLayout) row;
                        StringBuilder content = new StringBuilder();
                        for (int childIndex = 0; childIndex < conversation.getChildCount(); childIndex++) {
                            View child = conversation.getChildAt(childIndex);
                            if (child instanceof TextView) content.append(((TextView) child).getText()).append(' ');
                        }
                        boolean matches = content.toString().toLowerCase().contains(s.toString().toLowerCase());
                        row.setVisibility(matches ? View.VISIBLE : View.GONE);
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        for (int id : new int[]{R.id.conversation1, R.id.conversation2, R.id.conversation3}) bindClick(id, () -> Toast.makeText(this, "Màn hình trò chuyện sẽ được bổ sung trong phần tiếp theo.", Toast.LENGTH_SHORT).show());
    }

    private void bindProfile() {
        bindClick(R.id.btnEditProfile, () -> Toast.makeText(this, "Chỉnh sửa hồ sơ (dữ liệu mẫu).", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnMyListings, () -> showScreen(R.layout.activity_sell));
        bindClick(R.id.btnMyOrders, () -> showScreen(R.layout.activity_request));
        bindClick(R.id.btnFavorites, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.btnNotifications, () -> Toast.makeText(this, "Bạn chưa có thông báo mới.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnSettings, () -> Toast.makeText(this, "Cài đặt tài khoản sẽ được triển khai sau.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnLogout, () -> Toast.makeText(this, "Frontend hiện chưa tích hợp đăng nhập.", Toast.LENGTH_SHORT).show());
    }

    private void openProduct(String name, String emoji, String price) {
        showScreen(R.layout.activity_product_detail);
        TextView productName = findViewById(R.id.txtProductName);
        TextView productEmoji = findViewById(R.id.txtProductEmoji);
        TextView productPrice = findViewById(R.id.txtProductPrice);
        if (productName != null) productName.setText(name);
        if (productEmoji != null) productEmoji.setText(emoji);
        if (productPrice != null) productPrice.setText(price);
    }

    private void bindProductDetail() {
        bindClick(R.id.btnProductLike, () -> Toast.makeText(this, "Đã cập nhật trạng thái yêu thích trên giao diện.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnProductAction, () -> showScreen(R.layout.activity_request));
    }

    private void bindClick(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    public void onBackPressed() {
        if (currentScreen == R.layout.activity_home || currentScreen == R.layout.activity_login) {
            super.onBackPressed();
        } else {
            showScreen(R.layout.activity_home);
        }
    }
}
