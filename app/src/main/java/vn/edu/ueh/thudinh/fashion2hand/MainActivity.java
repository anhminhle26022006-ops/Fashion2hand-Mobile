package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Main entry point for the Fashion2hand frontend prototype. Uses local sample data only. */
public class MainActivity extends AppCompatActivity {
    private int currentScreen = R.layout.activity_splash;
    private final Map<Integer, Integer> previousScreenForLayout = new HashMap<>();
    private boolean suppressScreenHistory;
    private String selectedCategory = "Tất cả";
    private int selectedExploreType;
    private String selectedProductName = "Áo thun basic";
    private String selectedProductEmoji = "👕";
    private String selectedProductPrice = "Miễn phí";
    private String selectedProductMeta = "Size M · Tình trạng tốt";
    private boolean requestFromProductDetail;
    private boolean editListingMode;
    private String draftListingName = "";
    private String draftListingDescription = "";
    private String draftListingLocation = "Bình Thạnh, TP.HCM";
    private String draftListingPrice = "120000";
    private String draftListingCategory = "Áo khoác";
    private String draftListingSize = "M";
    private String draftListingCondition = "Tốt";
    private String draftListingPhoto = "";
    private boolean draftListingForSale;
    private boolean sellerOrderTab;
    private String profileName = "Nguyễn Minh Anh";
    private String profileRegion = "Quận 3, TP.HCM";
    private String profileAddress = "";
    private String shippingAddress = "12 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP. Hồ Chí Minh";
    private String profileBio = "Yêu thời trang bền vững và những món đồ có câu chuyện.";
    private String profileGender = "Không muốn cung cấp";
    private String profileBirthDate = "";
    private String profileEmail = "minhanh.demo@email.com";
    private String profilePhone = "0901 234 567";
    private boolean emailVerified = true;
    private boolean phoneVerified = true;
    private boolean profilePhotoSelected;
    private String verificationStatus = "Chưa xác minh";
    private String verificationLegalName = "Nguyễn Minh Anh";
    private String verificationBirthDate = "";
    private String verificationDocumentType = "Căn cước công dân gắn chip";
    private String verificationDocumentNumber = "";
    private boolean identityFrontSelected;
    private boolean identityBackSelected;
    private boolean identitySelfieSelected;
    private String payoutBank = "";
    private String payoutHolder = "";
    private String payoutAccountNumber = "";
    private boolean payoutSaved;
    private int notificationReturnScreen = R.layout.activity_profile;
    private String selectedOrderFilter = "all";
    private int selectedOrderIndex;
    private final String[] orderNumbers = {"ORD10283", "ORD10271", "ORD10248", "ORD10304", "ORD10299"};
    private final String[] orderNames = {"Vintage Denim Jacket", "Áo sơ mi linen trắng", "Váy hoa vintage", "Áo khoác denim", "Váy hoa vintage"};
    private final String[] orderSellers = {"Lan Chi", "Mai Phương", "Thảo Vy", "Minh Anh", "Thảo Vy"};
    private final String[] orderPrices = {"120.000đ", "0đ", "0đ", "120.000đ", "0đ"};
    private final String[] orderTotals = {"145.000đ", "25.000đ", "25.000đ", "145.000đ", "25.000đ"};
    private final String[] orderStatuses = {"Đang giao", "Đang chuẩn bị", "Đã giao", "Đang chuẩn bị", "Đang giao"};
    private final String[] orderShippingStatuses = {"Đang vận chuyển", "Chuẩn bị hàng", "Đã giao", "Chờ gửi hàng", "Đang vận chuyển"};
    private final String[] orderEmojis = {"🧥", "👚", "👗", "🧥", "👗"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        showScreen(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (currentScreen == R.layout.activity_splash) showScreen(R.layout.activity_login);
        }, 900);
    }

    private void showScreen(int layoutId) {
        if (layoutId == R.layout.activity_admin) { openAdminActivity(); return; }
        if (!suppressScreenHistory && currentScreen != layoutId && currentScreen != R.layout.activity_splash) {
            previousScreenForLayout.put(layoutId, currentScreen);
        }
        currentScreen = layoutId;
        setContentView(layoutId);
        applySystemBarInsets();
        if (layoutId == R.layout.activity_login) bindLogin();
        if (layoutId == R.layout.activity_register) bindRegister();
        if (layoutId == R.layout.activity_notifications) bindNotifications();
        if (layoutId == R.layout.activity_settings) bindSettings();
        bindNavigation();
        if (layoutId == R.layout.activity_home) bindHome();
        if (layoutId == R.layout.activity_explore) bindExplore();
        if (layoutId == R.layout.activity_sell) bindSell();
        if (layoutId == R.layout.activity_sell_details) bindSellDetails();
        if (layoutId == R.layout.activity_sell_type) bindSellType();
        if (layoutId == R.layout.activity_sell_preview) bindSellPreview();
        if (layoutId == R.layout.activity_sell_success) bindSellSuccess();
        if (layoutId == R.layout.activity_request) bindRequest();
        if (layoutId == R.layout.activity_messages) bindMessages();
        if (layoutId == R.layout.activity_profile) bindProfile();
        if (layoutId == R.layout.activity_edit_profile) bindEditProfile();
        if (layoutId == R.layout.activity_account_verification) bindAccountVerification();
        if (layoutId == R.layout.activity_verification_status) bindVerificationStatus();
        if (layoutId == R.layout.activity_payment_info) bindPaymentInfo();
        if (layoutId == R.layout.activity_product_detail) bindProductDetail();
        if (layoutId == R.layout.activity_order_detail) bindOrderDetail();
        if (layoutId == R.layout.activity_cart) bindCart();
        if (layoutId == R.layout.activity_checkout) bindCheckout();
        if (layoutId == R.layout.activity_orders) bindOrders();
        if (layoutId == R.layout.activity_delivery) bindDelivery();
        if (layoutId == R.layout.activity_delivery_map) bindDeliveryMap();
        if (layoutId == R.layout.activity_chat) bindChat();
        if (layoutId == R.layout.activity_admin_login) bindAdminLogin();
        if (layoutId == R.layout.activity_my_listings) bindMyListings();
        if (layoutId == R.layout.activity_favorites) bindFavorites();
        if (layoutId == R.layout.activity_request_status) bindRequestStatus();
        if (layoutId == R.layout.activity_transaction_history) bindTransactionHistory();
        if (layoutId == R.layout.activity_transactions) bindTransactions();
        if (layoutId == R.layout.activity_addresses) bindAddresses();
        if (layoutId == R.layout.activity_support) bindSupport();
    }

    private void showPreviousScreen(int fallbackScreen) {
        int previous = previousScreenForLayout.getOrDefault(currentScreen, fallbackScreen);
        previousScreenForLayout.remove(currentScreen);
        suppressScreenHistory = true;
        showScreen(previous);
        suppressScreenHistory = false;
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

            if (emailValue.equalsIgnoreCase("admin@fashion2hand.com")) {
                openAdminActivity();
            } else {
                Toast.makeText(this, "Đăng nhập demo thành công.", Toast.LENGTH_SHORT).show();
                showScreen(R.layout.activity_home);
            }
        });

        bindClick(R.id.btnOpenRegister, () -> showScreen(R.layout.activity_register));
        bindClick(R.id.btnOpenAdminLogin, () -> showScreen(R.layout.activity_admin_login));
        bindClick(R.id.btnGoogleLogin, () -> {
            Toast.makeText(this, "Đăng nhập Google demo thành công.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_home);
        });
        bindClick(R.id.btnForgotPassword, () ->
                Toast.makeText(this, "Chức năng quên mật khẩu sẽ được triển khai sau.", Toast.LENGTH_SHORT).show());
    }

    private void bindRegister() {
        bindClick(R.id.btnCreateAccount, () -> {
            EditText name = findViewById(R.id.edtRegisterName);
            EditText email = findViewById(R.id.edtRegisterEmail);
            EditText password = findViewById(R.id.edtRegisterPassword);
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng nhập họ tên"); return; }
            if (email.getText().toString().trim().isEmpty()) { email.setError("Vui lòng nhập email"); return; }
            if (password.getText().toString().length() < 6) { password.setError("Mật khẩu cần ít nhất 6 ký tự"); return; }
            Toast.makeText(this, "Tạo tài khoản demo thành công.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_home);
        });
        bindClick(R.id.btnOpenLogin, () -> showScreen(R.layout.activity_login));
    }

    private void bindNavigation() {
        bindClick(R.id.navHome, () -> showScreen(R.layout.activity_home));
        bindClick(R.id.navExplore, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.navCreate, () -> showScreen(R.layout.activity_sell));
        bindClick(R.id.navMessages, () -> showScreen(R.layout.activity_messages));
        bindClick(R.id.navProfile, () -> showScreen(R.layout.activity_profile));
        updateBottomNavigationSelection();
    }

    private void updateBottomNavigationSelection() {
        int selectedId = currentScreen == R.layout.activity_home ? R.id.navHome
                : currentScreen == R.layout.activity_explore ? R.id.navExplore
                : currentScreen == R.layout.activity_sell || currentScreen == R.layout.activity_create ? R.id.navCreate
                : currentScreen == R.layout.activity_messages || currentScreen == R.layout.activity_chat ? R.id.navMessages
                : currentScreen == R.layout.activity_profile ? R.id.navProfile : View.NO_ID;
        int[] navIds = {R.id.navHome, R.id.navExplore, R.id.navCreate, R.id.navMessages, R.id.navProfile};
        for (int id : navIds) {
            View item = findViewById(id);
            if (item == null) continue;
            boolean selected = id == selectedId;
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(selected ? 0xFFE4F0E8 : android.graphics.Color.TRANSPARENT);
            background.setCornerRadius(dp(22));
            item.setBackground(background);
            if (item instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) item;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView text = (TextView) child;
                        boolean plusIcon = id == R.id.navCreate && i == 0;
                        text.setTextColor(plusIcon ? 0xFFFFFFFF : selected ? 0xFF14513E : 0xFF75857F);
                        if (i == 1) text.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                    }
                }
            }
        }
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
        bindClick(R.id.btnNotificationsHome, () -> { notificationReturnScreen = R.layout.activity_home; showScreen(R.layout.activity_notifications); });
        bindClick(R.id.btnAll, () -> filterHomeByCategory("Tất cả"));
        bindClick(R.id.btnAo, () -> filterHomeByCategory("Áo"));
        bindClick(R.id.btnQuan, () -> filterHomeByCategory("Quần"));
        bindClick(R.id.btnVay, () -> filterHomeByCategory("Váy"));
        bindClick(R.id.btnSeeAllHome, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.productCard1, () -> openProduct("Áo thun basic", "👕", "Miễn phí · 0đ"));
        bindClick(R.id.productCard2, () -> openProduct("Váy hoa vintage", "👗", "Miễn phí · 0đ"));
        bindClick(R.id.productCard3, () -> openProduct("Quần jeans xanh", "👖", "Miễn phí · 0đ"));
        bindClick(R.id.productCard4, () -> openProduct("Áo khoác denim", "🥼", "120.000đ"));
        String[] names = {"Áo thun basic", "Váy hoa vintage", "Quần jeans xanh", "Áo khoác denim"};
        int[] cardIds = {R.id.productCard1, R.id.productCard2, R.id.productCard3, R.id.productCard4};
        for (int i = 0; i < cardIds.length; i++) {
            View card = findViewById(cardIds[i]);
            if (card != null) card.setTag(names[i]);
        }
        bindClick(R.id.likeProduct1, () -> toggleFavorite(R.id.likeProduct1));
        bindClick(R.id.likeProduct2, () -> toggleFavorite(R.id.likeProduct2));
        bindClick(R.id.likeProduct3, () -> toggleFavorite(R.id.likeProduct3));
        bindClick(R.id.likeProduct4, () -> toggleFavorite(R.id.likeProduct4));
        filterHomeByCategory(selectedCategory);
    }

    private void filterHomeByCategory(String category) {
        selectedCategory = category;
        int[] cardIds = {R.id.productCard1, R.id.productCard2, R.id.productCard3, R.id.productCard4};
        int visible = 0;
        for (int id : cardIds) {
            View card = findViewById(id);
            if (card == null) continue;
            String name = String.valueOf(card.getTag());
            boolean matches = "Tất cả".equals(category)
                    || ("Áo".equals(category) && name.startsWith("Áo"))
                    || ("Quần".equals(category) && name.startsWith("Quần"))
                    || ("Váy".equals(category) && name.startsWith("Váy"));
            card.setVisibility(matches ? View.VISIBLE : View.GONE);
            if (matches) visible++;
        }
        TextView count = findViewById(R.id.txtHomeCount);
        if (count != null) count.setText(visible + " bài");
        int[] chipIds = {R.id.btnAll, R.id.btnAo, R.id.btnQuan, R.id.btnVay};
        String[] labels = {"Tất cả", "Áo", "Quần", "Váy"};
        for (int i = 0; i < chipIds.length; i++) {
            TextView chip = findViewById(chipIds[i]);
            if (chip != null) {
                boolean selected = labels[i].equals(category);
                chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
                chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF26332D);
            }
        }
    }

    private void toggleFavorite(int id) {
        TextView heart = findViewById(id);
        if (heart != null) {
            boolean active = "♥".contentEquals(heart.getText());
            heart.setText(active ? "♡" : "♥");
            heart.setTextColor(active ? 0xFF274238 : 0xFFE65B65);
        }
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
        GridLayout grid = findViewById(R.id.exploreProductGrid);
        if (grid != null && grid.getChildCount() == 0) addSampleProducts(grid);
        bindClick(R.id.btnOpenFilterSheet, this::showExploreFilterSheet);
        applyExploreFilter(selectedCategory);
    }

    private void showExploreFilterSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(8));
        TextView title = new TextView(this); title.setText("Bộ lọc"); title.setTextSize(21); title.setTextColor(0xFF173B32); title.setPadding(0, 0, 0, dp(12)); content.addView(title);
        TextView typeTitle = new TextView(this); typeTitle.setText("Loại tin"); typeTitle.setTextColor(0xFF173B32); content.addView(typeTitle);
        android.widget.RadioGroup typeGroup = new android.widget.RadioGroup(this); typeGroup.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        String[] types = {"Tất cả", "Miễn phí", "Đang bán"};
        for (int i = 0; i < types.length; i++) { android.widget.RadioButton option = new android.widget.RadioButton(this); option.setId(View.generateViewId()); option.setText(types[i]); typeGroup.addView(option); if (i == selectedExploreType) typeGroup.check(option.getId()); }
        content.addView(typeGroup);
        TextView categoryTitle = new TextView(this); categoryTitle.setText("Danh mục"); categoryTitle.setTextColor(0xFF173B32); categoryTitle.setPadding(0, dp(10), 0, 0); content.addView(categoryTitle);
        Spinner category = new Spinner(this); String[] categories = {"Tất cả", "Áo", "Quần", "Váy", "Áo khoác", "Phụ kiện"}; category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        int categoryIndex = java.util.Arrays.asList(categories).indexOf(selectedCategory); if (categoryIndex >= 0) category.setSelection(categoryIndex); content.addView(category);
        TextView size = new TextView(this); size.setText("Kích cỡ:  XS      S      M      L      XL      Khác"); size.setTextColor(0xFF173B32); size.setPadding(0, dp(14), 0, dp(6)); content.addView(size);
        TextView condition = new TextView(this); condition.setText("Tình trạng:  Mới       Như mới       Tốt       Đã sử dụng"); condition.setTextColor(0xFF173B32); condition.setPadding(0, dp(6), 0, dp(8)); content.addView(condition);
        TextView priceRange = new TextView(this); priceRange.setText("Khoảng giá:  0đ  —  500.000đ"); priceRange.setTextColor(0xFF173B32); content.addView(priceRange);
        android.widget.SeekBar seek = new android.widget.SeekBar(this); seek.setMax(500000); seek.setProgress(500000); content.addView(seek);
        TextView location = new TextView(this); location.setText("Vị trí:  Gần tôi       Quận       Thành phố"); location.setTextColor(0xFF173B32); location.setPadding(0, dp(8), 0, 0); content.addView(location);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).setView(content)
                .setNegativeButton("Đặt lại", (d, w) -> { selectedExploreType = 0; applyExploreFilter("Tất cả"); })
                .setPositiveButton("Xem sản phẩm", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            for (int i = 0; i < typeGroup.getChildCount(); i++) if (((android.widget.RadioButton) typeGroup.getChildAt(i)).isChecked()) selectedExploreType = i;
            applyExploreFilter(String.valueOf(category.getSelectedItem())); dialog.dismiss();
        }));
        dialog.show();
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
                    || ("Áo khoác".equals(selectedCategory) && name.toLowerCase().contains("denim"))
                    || ("Áo".equals(selectedCategory) && name.startsWith("Áo"))
                    || ("Quần".equals(selectedCategory) && name.startsWith("Quần"))
                    || ("Váy".equals(selectedCategory) && name.startsWith("Váy"));
            boolean matchesQuery = name.toLowerCase().contains(query.toLowerCase().trim());
            boolean isForSale = name.contains("denim") || name.contains("jeans");
            boolean matchesType = selectedExploreType == 0 || (selectedExploreType == 1 && !isForSale) || (selectedExploreType == 2 && isForSale);
            child.setVisibility(matchesCategory && matchesQuery && matchesType ? View.VISIBLE : View.GONE);
            if (matchesCategory && matchesQuery && matchesType) visible++;
        }
        TextView count = findViewById(R.id.txtExploreCount);
        if (count != null) count.setText(visible + " sản phẩm phù hợp");
    }

    private void bindSell() {
        bindClick(R.id.btnSellBack, () -> showPreviousScreen(R.layout.activity_home));
        bindClick(R.id.btnChoosePhotos, () -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Chọn ảnh quần áo"), 202);
        });
        TextView photoStatus = findViewById(R.id.txtSelectedPhoto);
        if (photoStatus != null && !draftListingPhoto.isEmpty()) photoStatus.setText("✓  Đã chọn ảnh sản phẩm");
        bindClick(R.id.btnSellNext, () -> showScreen(R.layout.activity_sell_details));
    }

    private void bindSellDetails() {
        setSpinner(R.id.spinnerCategory, new String[]{"Áo", "Quần", "Váy", "Áo khoác", "Phụ kiện"}, draftListingCategory);
        setSpinner(R.id.spinnerSize, new String[]{"XS", "S", "M", "L", "XL", "Free size"}, draftListingSize);
        setSpinner(R.id.spinnerCondition, new String[]{"Mới", "Như mới", "Tốt", "Đã sử dụng"}, draftListingCondition);
        EditText name = findViewById(R.id.edtProductName);
        EditText description = findViewById(R.id.edtProductDescription);
        EditText location = findViewById(R.id.edtProductLocation);
        if (name != null) name.setText(draftListingName.isEmpty() && editListingMode ? "Áo khoác denim vintage" : draftListingName);
        if (description != null) description.setText(draftListingDescription);
        if (location != null) location.setText(draftListingLocation);
        bindClick(R.id.btnSellDetailsBack, () -> showPreviousScreen(R.layout.activity_sell));
        bindClick(R.id.btnSellDetailsNext, () -> {
            if (name == null || name.getText().toString().trim().isEmpty()) {
                if (name != null) name.setError("Vui lòng nhập tên sản phẩm");
                return;
            }
            draftListingName = name.getText().toString().trim();
            draftListingDescription = description == null ? "" : description.getText().toString().trim();
            draftListingLocation = location == null ? "" : location.getText().toString().trim();
            draftListingCategory = selectedSpinner(R.id.spinnerCategory, draftListingCategory);
            draftListingSize = selectedSpinner(R.id.spinnerSize, draftListingSize);
            draftListingCondition = selectedSpinner(R.id.spinnerCondition, draftListingCondition);
            showScreen(R.layout.activity_sell_type);
        });
    }

    private void setSpinner(int id, String[] values, String selected) {
        Spinner spinner = findViewById(id);
        if (spinner == null) return;
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) { spinner.setSelection(i); break; }
    }

    private String selectedSpinner(int id, String fallback) {
        Spinner spinner = findViewById(id);
        return spinner == null || spinner.getSelectedItem() == null ? fallback : spinner.getSelectedItem().toString();
    }

    private void bindSellType() {
        android.widget.RadioGroup group = findViewById(R.id.rgListingType);
        EditText price = findViewById(R.id.edtProductPrice);
        if (price != null) { price.setText(draftListingPrice); price.setVisibility(draftListingForSale ? View.VISIBLE : View.GONE); }
        if (group != null) {
            group.check(draftListingForSale ? R.id.radioSell : R.id.radioFree);
            group.setOnCheckedChangeListener((g, checked) -> { draftListingForSale = checked == R.id.radioSell; if (price != null) price.setVisibility(draftListingForSale ? View.VISIBLE : View.GONE); });
        }
        bindClick(R.id.btnSellTypeBack, () -> showPreviousScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnSellTypeNext, () -> {
            draftListingForSale = group != null && group.getCheckedRadioButtonId() == R.id.radioSell;
            if (price != null && draftListingForSale) draftListingPrice = price.getText().toString().trim();
            if (draftListingForSale && draftListingPrice.isEmpty()) { if (price != null) price.setError("Nhập giá bán"); return; }
            showScreen(R.layout.activity_sell_preview);
        });
    }

    private void bindSellPreview() {
        TextView name = findViewById(R.id.txtPreviewName);
        TextView description = findViewById(R.id.txtPreviewDescription);
        TextView price = findViewById(R.id.txtPreviewPrice);
        TextView meta = findViewById(R.id.txtPreviewMeta);
        TextView type = findViewById(R.id.txtPreviewType);
        TextView photo = findViewById(R.id.txtPreviewPhoto);
        android.widget.ImageView photoImage = findViewById(R.id.imgPreviewPhoto);
        if (name != null) name.setText(draftListingName);
        if (description != null) description.setText(draftListingDescription.isEmpty() ? "Người đăng chưa thêm mô tả." : draftListingDescription);
        if (price != null) price.setText(draftListingForSale ? String.format(java.util.Locale.getDefault(), "%,dđ", parsePrice(draftListingPrice)) : "Miễn phí · 0đ");
        if (meta != null) meta.setText(draftListingCondition + " · Size " + draftListingSize + " · " + draftListingLocation);
        if (type != null) type.setText(draftListingForSale ? "FOR SALE" : "FREE · 0đ");
        if (photo != null) photo.setVisibility(draftListingPhoto.isEmpty() ? View.VISIBLE : View.GONE);
        if (photoImage != null) {
            if (draftListingPhoto.isEmpty()) photoImage.setVisibility(View.GONE);
            else {
                try { photoImage.setImageURI(android.net.Uri.parse(draftListingPhoto)); photoImage.setVisibility(View.VISIBLE); }
                catch (RuntimeException ignored) { photoImage.setVisibility(View.GONE); if (photo != null) photo.setVisibility(View.VISIBLE); }
            }
        }
        bindClick(R.id.btnSellPreviewBack, () -> showPreviousScreen(R.layout.activity_sell_type));
        bindClick(R.id.btnPublishListing, () -> { editListingMode = false; showScreen(R.layout.activity_sell_success); });
    }

    private int parsePrice(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) { return 0; }
    }

    private void bindSellSuccess() {
        bindClick(R.id.btnViewPublishedListing, () -> { selectedProductName = draftListingName; selectedProductPrice = draftListingForSale ? String.format(java.util.Locale.getDefault(), "%,dđ", parsePrice(draftListingPrice)) : "Miễn phí"; showScreen(R.layout.activity_product_detail); });
        bindClick(R.id.btnManagePublishedListings, () -> showScreen(R.layout.activity_my_listings));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 202 && resultCode == RESULT_OK && data != null) {
            draftListingPhoto = data.getData() == null ? "selected" : data.getData().toString();
            TextView upload = findViewById(R.id.btnChoosePhotos);
            if (upload != null) upload.setText("✓  Đã chọn ảnh quần áo");
            TextView photoStatus = findViewById(R.id.txtSelectedPhoto);
            if (photoStatus != null) photoStatus.setText("✓  Ảnh đã được thêm vào bản nháp");
        } else if (requestCode == 201 && resultCode == RESULT_OK) {
            profilePhotoSelected = true;
            TextView avatar = findViewById(R.id.txtEditProfileAvatar);
            if (avatar != null) avatar.setText("✓");
        } else if (resultCode == RESULT_OK && data != null && (requestCode == 303 || requestCode == 304 || requestCode == 305)) {
            if (requestCode == 303) identityFrontSelected = true;
            if (requestCode == 304) identityBackSelected = true;
            if (requestCode == 305) identitySelfieSelected = true;
            updateIdentityPhotoLabels();
        }
    }

    private void bindRequest() {
        TextView productName = findViewById(R.id.txtRequestProductName);
        TextView productEmoji = findViewById(R.id.txtRequestProductEmoji);
        TextView productPrice = findViewById(R.id.txtRequestPrice);
        TextView productMeta = findViewById(R.id.txtRequestProductMeta);
        if (productName != null) productName.setText(selectedProductName);
        if (productEmoji != null) productEmoji.setText(selectedProductEmoji);
        if (productPrice != null) productPrice.setText(selectedProductPrice);
        if (productMeta != null) productMeta.setText(selectedProductMeta);
        Spinner contact = findViewById(R.id.spinnerContactMethod);
        if (contact != null) contact.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Chat trong ứng dụng", "Điện thoại", "Email"}));
        bindClick(R.id.btnRequestBack, () -> showPreviousScreen(requestFromProductDetail
                ? R.layout.activity_product_detail : R.layout.activity_home));
        bindClick(R.id.btnRequestAccept, () -> {
            TextView status = findViewById(R.id.txtRequestStatus);
            if (status != null) status.setText("Trạng thái: Đã gửi yêu cầu nhận " + selectedProductName);
            Toast.makeText(this, "Đã ghi nhận yêu cầu mẫu của bạn.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_request_status);
        });
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
                            appendVisibleText(child, content);
                        }
                        boolean matches = content.toString().toLowerCase().contains(s.toString().toLowerCase());
                        row.setVisibility(matches ? View.VISIBLE : View.GONE);
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        for (int id : new int[]{R.id.conversation1, R.id.conversation2, R.id.conversation3}) bindClick(id, () -> showScreen(R.layout.activity_chat));
    }

    private void bindProfile() {
        TextView name = findViewById(R.id.txtProfileName);
        TextView location = findViewById(R.id.txtProfileLocation);
        if (name != null) name.setText(profileName);
        if (location != null) location.setText("◉  " + profileRegion + " · Danh tính " + verificationStatus.toLowerCase());
        bindClick(R.id.btnEditProfile, () -> showScreen(R.layout.activity_edit_profile));
        TextView verificationButton = findViewById(R.id.btnOpenVerification);
        if (verificationButton != null) {
            verificationButton.setText("Đang chờ duyệt".equals(verificationStatus) ? "Xem trạng thái xác minh"
                    : "Đã xác minh".equals(verificationStatus) ? "Tài khoản đã xác minh" : "Xác minh danh tính");
        }
        bindClick(R.id.btnOpenVerification, this::openVerificationFlow);
        bindClick(R.id.btnOpenPaymentInfo, () -> showScreen(R.layout.activity_payment_info));
        bindClick(R.id.btnMyListings, () -> showScreen(R.layout.activity_my_listings));
        bindClick(R.id.btnMyOrders, () -> showScreen(R.layout.activity_transactions));
        bindClick(R.id.btnMyCart, () -> showScreen(R.layout.activity_orders));
        bindClick(R.id.btnFavorites, () -> showScreen(R.layout.activity_favorites));
        bindClick(R.id.btnMyHistory, () -> showScreen(R.layout.activity_transaction_history));
        bindClick(R.id.btnAddresses, () -> showScreen(R.layout.activity_addresses));
        bindClick(R.id.btnSupport, () -> showScreen(R.layout.activity_support));
        bindClick(R.id.btnNotifications, () -> { notificationReturnScreen = R.layout.activity_profile; showScreen(R.layout.activity_notifications); });
        bindClick(R.id.btnSettings, () -> showScreen(R.layout.activity_settings));
        bindClick(R.id.btnSettingsMenu, () -> showScreen(R.layout.activity_settings));
        bindClick(R.id.btnLogout, () -> showScreen(R.layout.activity_login));
    }

    private void bindNotifications() {
        bindClick(R.id.btnNotificationsBack, () -> showPreviousScreen(notificationReturnScreen));
        bindClick(R.id.btnNotificationsMore, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(new String[]{"Đánh dấu tất cả đã đọc", "Tùy chọn thông báo"}, (dialog, which) -> {
                    if (which == 0) {
                        for (int id : new int[]{R.id.notifUnread1, R.id.notifUnread2, R.id.notifUnread3}) {
                            View dot = findViewById(id); if (dot != null) dot.setVisibility(View.GONE);
                        }
                        Toast.makeText(this, "Đã đánh dấu thông báo là đã đọc.", Toast.LENGTH_SHORT).show();
                    } else showScreen(R.layout.activity_settings);
                }).show());
        int[] filters = {R.id.filterNotifAll, R.id.filterNotifOrders, R.id.filterNotifMessages, R.id.filterNotifRequests, R.id.filterNotifPayments};
        String[] categories = {"all", "orders", "messages", "requests", "payments"};
        for (int i = 0; i < filters.length; i++) {
            final int selected = i;
            bindClick(filters[i], () -> {
                for (int j = 0; j < filters.length; j++) {
                    TextView chip = findViewById(filters[j]);
                    if (chip != null) {
                        boolean active = j == selected;
                        chip.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
                        chip.setTextColor(active ? 0xFFFFFFFF : 0xFF173B32);
                    }
                }
                int[] rows = {R.id.notifRowFreeRequest, R.id.notifRowMessage, R.id.notifRowShipping, R.id.notifRowPayment, R.id.notifRowRequestDone, R.id.notifRowDispute};
                for (int rowId : rows) {
                    View row = findViewById(rowId);
                    if (row != null) row.setVisibility(selected == 0 || categories[selected].equals(String.valueOf(row.getTag())) ? View.VISIBLE : View.GONE);
                }
            });
        }
        bindNotificationRow(R.id.notifRowFreeRequest, R.id.notifUnread1, "Yêu cầu nhận đồ miễn phí");
        bindNotificationRow(R.id.notifRowMessage, R.id.notifUnread2, "Tin nhắn mới từ Lan Chi");
        bindNotificationRow(R.id.notifRowShipping, R.id.notifUnread3, "Cập nhật vận chuyển đơn hàng");
        bindNotificationRow(R.id.notifRowPayment, -1, "Thông tin thanh toán đang được giữ an toàn");
        bindNotificationRow(R.id.notifRowRequestDone, -1, "Yêu cầu nhận đồ đã được chấp nhận");
        bindNotificationRow(R.id.notifRowDispute, -1, "Cập nhật tranh chấp đơn hàng");
    }

    private void bindNotificationRow(int rowId, int unreadId, String message) {
        bindClick(rowId, () -> {
            if (unreadId != -1) { View dot = findViewById(unreadId); if (dot != null) dot.setVisibility(View.GONE); }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void bindSettings() {
        bindClick(R.id.btnSettingsBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.settingAccount, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.settingNotifications, () -> { notificationReturnScreen = R.layout.activity_settings; showScreen(R.layout.activity_notifications); });
        bindClick(R.id.settingPrivacy, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Riêng tư & bảo mật")
                .setItems(new String[]{"Đổi mật khẩu", "Xác minh số điện thoại", "Quản lý thông tin xác minh"}, (dialog, which) -> {
                    if (which == 0) Toast.makeText(this, "Mở luồng đổi mật khẩu demo.", Toast.LENGTH_SHORT).show();
                    else if (which == 1) changeContact(false);
                    else showScreen(R.layout.activity_account_verification);
                }).show());
        bindClick(R.id.settingLanguage, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Ngôn ngữ").setSingleChoiceItems(new String[]{"Tiếng Việt", "English"}, 0, (dialog, which) -> {
                    Toast.makeText(this, which == 0 ? "Đã chọn Tiếng Việt." : "English is a demo option.", Toast.LENGTH_SHORT).show(); dialog.dismiss();
                }).setNegativeButton("Đóng", null).show());
        bindClick(R.id.settingLogout, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Đăng xuất khỏi thiết bị?").setNegativeButton("Ở lại", null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> showScreen(R.layout.activity_login)).show());
    }

    private void bindEditProfile() {
        bindClick(R.id.btnEditProfileBack, () -> showPreviousScreen(R.layout.activity_profile));
        EditText name = findViewById(R.id.edtProfileName);
        EditText region = findViewById(R.id.edtProfileRegion);
        EditText address = findViewById(R.id.edtProfileAddress);
        EditText bio = findViewById(R.id.edtProfileBio);
        EditText birthDate = findViewById(R.id.edtProfileBirthDate);
        if (name != null) name.setText(profileName);
        if (region != null) region.setText(profileRegion);
        if (address != null) address.setText(profileAddress);
        if (bio != null) bio.setText(profileBio);
        if (birthDate != null) birthDate.setText(profileBirthDate);
        TextView avatar = findViewById(R.id.txtEditProfileAvatar);
        if (avatar != null && profilePhotoSelected) avatar.setText("✓");
        Spinner gender = findViewById(R.id.spinnerProfileGender);
        if (gender != null) {
            String[] options = {"Không muốn cung cấp", "Nữ", "Nam", "Khác"};
            gender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
            int selected = java.util.Arrays.asList(options).indexOf(profileGender);
            if (selected >= 0) gender.setSelection(selected);
        }
        TextView phone = findViewById(R.id.txtProfilePhone);
        TextView phoneStatus = findViewById(R.id.txtProfilePhoneStatus);
        TextView email = findViewById(R.id.txtProfileEmail);
        TextView emailStatus = findViewById(R.id.txtProfileEmailStatus);
        if (phone != null) phone.setText(profilePhone);
        if (phoneStatus != null) phoneStatus.setText((phoneVerified ? "✓  Số điện thoại đã xác minh" : "!  Số điện thoại chưa xác minh"));
        if (email != null) email.setText(profileEmail);
        if (emailStatus != null) emailStatus.setText(emailVerified ? "✓  Email đã xác minh" : "!  Email chưa xác minh");
        bindClick(R.id.btnVerifyPhone, this::verifyPhoneWithOtp);
        bindClick(R.id.btnChangeProfilePhone, () -> changeContact(false));
        bindClick(R.id.btnChangeProfileEmail, () -> changeContact(true));
        bindClick(R.id.btnChangeProfilePhoto, () -> chooseImage(201));
        bindClick(R.id.btnEditProfileVerify, this::openVerificationFlow);
        bindClick(R.id.btnEditProfilePayment, () -> showScreen(R.layout.activity_payment_info));
        bindClick(R.id.btnEditProfileSave, () -> {
            profileName = name == null ? profileName : name.getText().toString().trim();
            profileRegion = region == null ? profileRegion : region.getText().toString().trim();
            profileAddress = address == null ? profileAddress : address.getText().toString().trim();
            profileBio = bio == null ? profileBio : bio.getText().toString().trim();
            profileBirthDate = birthDate == null ? profileBirthDate : birthDate.getText().toString().trim();
            if (!profileAddress.isEmpty()) shippingAddress = profileAddress;
            if (gender != null && gender.getSelectedItem() != null) profileGender = gender.getSelectedItem().toString();
            if (profileName.isEmpty()) { if (name != null) name.setError("Vui lòng nhập họ tên"); return; }
            Toast.makeText(this, "Đã lưu hồ sơ mẫu.", Toast.LENGTH_SHORT).show();
            showPreviousScreen(R.layout.activity_profile);
        });
    }

    private void verifyPhoneWithOtp() {
        EditText otp = new EditText(this);
        otp.setHint("Nhập mã demo 123456");
        otp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác minh số điện thoại")
                .setMessage("Mã xác minh được gửi đến " + profilePhone)
                .setView(otp)
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    if ("123456".contentEquals(otp.getText())) {
                        phoneVerified = true;
                        TextView status = findViewById(R.id.txtProfilePhoneStatus);
                        if (status != null) status.setText("✓  Số điện thoại đã xác minh");
                        Toast.makeText(this, "Số điện thoại đã xác minh (demo).", Toast.LENGTH_SHORT).show();
                    } else {
                        otp.setError("Mã demo không đúng");
                        Toast.makeText(this, "Mã demo là 123456.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void changeContact(boolean isEmail) {
        EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setHint(isEmail ? "name@example.com" : "Số điện thoại mới");
        value.setInputType(isEmail
                ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                : android.text.InputType.TYPE_CLASS_PHONE);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(isEmail ? "Đổi email" : "Đổi số điện thoại")
                .setView(value)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    String contact = value.getText().toString().trim();
                    if (contact.isEmpty()) { value.setError("Vui lòng nhập thông tin"); return; }
                    if (isEmail) { profileEmail = contact; emailVerified = false; }
                    else { profilePhone = contact; phoneVerified = false; }
                    Toast.makeText(this, "Đã cập nhật liên hệ mẫu. Nhập OTP 123456 để xác minh.", Toast.LENGTH_LONG).show();
                    if (isEmail) verifyEmailWithOtp(); else verifyPhoneWithOtp();
                })
                .show();
    }

    private void verifyEmailWithOtp() {
        EditText otp = new EditText(this);
        otp.setHint("Nhập mã demo 123456");
        otp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác minh email")
                .setMessage("Mã xác minh được gửi đến " + profileEmail)
                .setView(otp)
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    if ("123456".contentEquals(otp.getText())) {
                        emailVerified = true;
                        Toast.makeText(this, "Email đã xác minh (demo).", Toast.LENGTH_SHORT).show();
                    } else Toast.makeText(this, "Mã demo là 123456.", Toast.LENGTH_SHORT).show();
                    TextView email = findViewById(R.id.txtProfileEmail);
                    TextView emailStatus = findViewById(R.id.txtProfileEmailStatus);
                    if (email != null) email.setText(profileEmail + (emailVerified ? " · Đã xác minh" : " · Chưa xác minh"));
                    if (emailStatus != null) emailStatus.setText(emailVerified ? "✓  Email đã xác minh" : "!  Email chưa xác minh");
                })
                .show();
    }

    private void openVerificationFlow() {
        if ("Đang chờ duyệt".equals(verificationStatus) || "Đã xác minh".equals(verificationStatus)) {
            showScreen(R.layout.activity_verification_status);
        } else {
            showScreen(R.layout.activity_account_verification);
        }
    }

    private void bindAccountVerification() {
        bindClick(R.id.btnVerificationBack, () -> showPreviousScreen(R.layout.activity_edit_profile));
        bindClick(R.id.btnVerificationLater, () -> showPreviousScreen(R.layout.activity_edit_profile));
        EditText legalName = findViewById(R.id.edtLegalName);
        EditText birthDate = findViewById(R.id.edtLegalBirthDate);
        EditText documentNumber = findViewById(R.id.edtIdentityNumber);
        Spinner documentType = findViewById(R.id.spinnerIdentityDocument);
        if (legalName != null) legalName.setText(verificationLegalName);
        if (birthDate != null) birthDate.setText(verificationBirthDate);
        bindClick(R.id.edtLegalBirthDate, () -> {
            java.util.Calendar today = java.util.Calendar.getInstance();
            android.app.DatePickerDialog datePicker = new android.app.DatePickerDialog(this, (picker, year, month, day) -> {
                EditText date = findViewById(R.id.edtLegalBirthDate);
                if (date != null) date.setText(String.format(java.util.Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year));
            }, today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH));
            datePicker.getDatePicker().setMaxDate(System.currentTimeMillis());
            datePicker.show();
        });
        if (documentNumber != null) documentNumber.setText(verificationDocumentNumber);
        if (documentType != null) {
            String[] types = {"Căn cước công dân gắn chip", "Chứng minh nhân dân", "Hộ chiếu"};
            documentType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
            int selected = java.util.Arrays.asList(types).indexOf(verificationDocumentType);
            if (selected >= 0) documentType.setSelection(selected);
        }
        updateIdentityPhotoLabels();
        bindClick(R.id.btnIdentityFront, () -> chooseImage(303));
        bindClick(R.id.btnIdentityBack, () -> chooseImage(304));
        bindClick(R.id.btnIdentitySelfie, () -> chooseImage(305));
        bindClick(R.id.btnSubmitVerification, () -> {
            CheckBox accurate = findViewById(R.id.checkIdentityAccurate);
            CheckBox privacy = findViewById(R.id.checkIdentityPrivacy);
            if (legalName == null || legalName.getText().toString().trim().isEmpty()) { if (legalName != null) legalName.setError("Nhập họ tên trên giấy tờ"); return; }
            if (birthDate == null || birthDate.getText().toString().trim().isEmpty()) { if (birthDate != null) birthDate.setError("Nhập ngày sinh"); return; }
            if (documentNumber == null || documentNumber.getText().toString().trim().isEmpty()) { if (documentNumber != null) documentNumber.setError("Nhập số giấy tờ"); return; }
            if (!identityFrontSelected || !identityBackSelected || !identitySelfieSelected) { Toast.makeText(this, "Vui lòng chọn đủ ảnh CCCD hai mặt và ảnh chân dung.", Toast.LENGTH_LONG).show(); return; }
            if (accurate == null || !accurate.isChecked() || privacy == null || !privacy.isChecked()) { Toast.makeText(this, "Vui lòng xác nhận thông tin và đồng ý xử lý dữ liệu.", Toast.LENGTH_LONG).show(); return; }
            verificationLegalName = legalName.getText().toString().trim();
            verificationBirthDate = birthDate.getText().toString().trim();
            verificationDocumentNumber = documentNumber.getText().toString().trim();
            if (documentType != null && documentType.getSelectedItem() != null) verificationDocumentType = documentType.getSelectedItem().toString();
            verificationStatus = "Đang chờ duyệt";
            Toast.makeText(this, "Đã gửi hồ sơ xác minh mẫu.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_verification_status);
        });
    }

    private void updateIdentityPhotoLabels() {
        TextView front = findViewById(R.id.btnIdentityFront);
        TextView back = findViewById(R.id.btnIdentityBack);
        TextView selfie = findViewById(R.id.btnIdentitySelfie);
        if (front != null) front.setText(identityFrontSelected ? "✓\nĐã chọn mặt trước" : "＋\nẢnh mặt trước CCCD");
        if (back != null) back.setText(identityBackSelected ? "✓\nĐã chọn mặt sau" : "＋\nẢnh mặt sau CCCD");
        if (selfie != null) selfie.setText(identitySelfieSelected ? "✓  Đã chọn ảnh chân dung" : "＋  Ảnh chân dung cầm giấy tờ");
    }

    private void bindVerificationStatus() {
        bindClick(R.id.btnVerificationStatusBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.btnVerificationStatusProfile, () -> showScreen(R.layout.activity_profile));
        TextView title = findViewById(R.id.txtVerificationStatusTitle);
        TextView description = findViewById(R.id.txtVerificationStatusDescription);
        TextView status = findViewById(R.id.txtVerificationStatusValue);
        TextView name = findViewById(R.id.txtVerificationLegalName);
        TextView type = findViewById(R.id.txtVerificationDocumentType);
        if (name != null) name.setText(verificationLegalName);
        if (type != null) type.setText(verificationDocumentType);
        if (status != null) status.setText(verificationStatus);
        if ("Đã xác minh".equals(verificationStatus)) {
            if (title != null) title.setText("Tài khoản đã xác minh");
            if (description != null) description.setText("Danh tính của bạn đã được xác minh trong bản demo.");
            if (status != null) status.setTextColor(0xFF23795C);
        }
    }

    private void bindPaymentInfo() {
        bindClick(R.id.btnPaymentInfoBack, () -> showPreviousScreen(R.layout.activity_edit_profile));
        Spinner bank = findViewById(R.id.spinnerPayoutBank);
        EditText holder = findViewById(R.id.edtPayoutHolder);
        EditText account = findViewById(R.id.edtPayoutAccountNumber);
        TextView status = findViewById(R.id.txtPayoutStatus);
        if (bank != null) {
            String[] banks = {"Chọn ngân hàng", "Vietcombank", "Techcombank", "MB Bank", "ACB", "Ngân hàng khác"};
            bank.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, banks));
            int selection = java.util.Arrays.asList(banks).indexOf(payoutBank.isEmpty() ? "Chọn ngân hàng" : payoutBank);
            if (selection >= 0) bank.setSelection(selection);
        }
        if (holder != null) holder.setText(payoutHolder);
        if (account != null) account.setText(payoutAccountNumber);
        if (status != null && payoutSaved) status.setText("✓  Đã lưu · Chờ đối chiếu");
        bindClick(R.id.btnSavePayoutInfo, () -> {
            if (bank == null || bank.getSelectedItemPosition() == 0) { Toast.makeText(this, "Vui lòng chọn ngân hàng.", Toast.LENGTH_SHORT).show(); return; }
            if (holder == null || holder.getText().toString().trim().isEmpty()) { if (holder != null) holder.setError("Nhập tên chủ tài khoản"); return; }
            if (account == null || account.getText().toString().trim().isEmpty()) { if (account != null) account.setError("Nhập số tài khoản"); return; }
            CheckBox consent = findViewById(R.id.checkPayoutConsent);
            if (consent == null || !consent.isChecked()) { Toast.makeText(this, "Vui lòng xác nhận tài khoản thuộc về bạn.", Toast.LENGTH_SHORT).show(); return; }
            payoutBank = bank.getSelectedItem().toString();
            payoutHolder = holder.getText().toString().trim();
            payoutAccountNumber = account.getText().toString().trim();
            payoutSaved = true;
            if (status != null) status.setText("✓  Đã lưu · Chờ đối chiếu");
            Toast.makeText(this, "Đã lưu thông tin nhận tiền mẫu.", Toast.LENGTH_SHORT).show();
        });
    }

    private void chooseImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh"), requestCode);
    }

    private void openProduct(String name, String emoji, String price) {
        selectedProductName = name;
        selectedProductEmoji = emoji;
        selectedProductPrice = price;
        if ("Váy hoa vintage".equals(name)) selectedProductMeta = "Size S · Tình trạng rất tốt";
        else if ("Quần jeans xanh".equals(name)) selectedProductMeta = "Size L · Tình trạng tốt";
        else selectedProductMeta = "Size M · Tình trạng tốt";
        showScreen(R.layout.activity_product_detail);
        TextView productName = findViewById(R.id.txtProductName);
        TextView productEmoji = findViewById(R.id.txtProductEmoji);
        TextView productPrice = findViewById(R.id.txtProductPrice);
        if (productName != null) productName.setText(name);
        if (productEmoji != null) productEmoji.setText(emoji);
        if (productPrice != null) productPrice.setText(price);
    }

    private void bindProductDetail() {
        bindClick(R.id.btnProductLike, () -> {
            TextView button = findViewById(R.id.btnProductLike);
            if (button == null) return;
            boolean saved = button.getText().toString().contains("Đã lưu");
            button.setText(saved ? "♡  Lưu sản phẩm" : "♥  Đã lưu yêu thích");
            button.setTextColor(saved ? 0xFF277B5C : 0xFFE65B65);
        });
        bindClick(R.id.btnProductAction, () -> {
            requestFromProductDetail = true;
            showScreen(R.layout.activity_request);
        });
    }

    private void showEditProfileDialog() {
        showScreen(R.layout.activity_edit_profile);
    }

    private void bindCart() {
        bindClick(R.id.btnBack, () -> showPreviousScreen(R.layout.activity_home));
        bindClick(R.id.btnCheckout, () -> showScreen(R.layout.activity_checkout));
        bindClick(R.id.btnExplore, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.btnDelete1, () -> Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng mẫu.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnDelete2, () -> Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng mẫu.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.tvRemoveUnavailable, () -> Toast.makeText(this, "Đã xóa sản phẩm hết hàng.", Toast.LENGTH_SHORT).show());
    }

    private void bindCheckout() {
        bindClick(R.id.btnCheckoutBack, () -> showPreviousScreen(R.layout.activity_cart));
        bindClick(R.id.btnPlaceOrder, () -> {
            Toast.makeText(this, "Đặt hàng thành công (bản xem trước).", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_orders);
        });
    }

    private void bindOrders() {
        bindClick(R.id.btnOrdersBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.btnTransactionHistory, () -> showScreen(R.layout.activity_transaction_history));
        bindClick(R.id.btnOrderAdvancedFilter, () -> Toast.makeText(this, "Dùng các trạng thái để lọc đơn hàng.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.tabBuyer, () -> setOrderRole(false));
        bindClick(R.id.tabSeller, () -> setOrderRole(true));
        bindOrderFilter(R.id.filterOrdersAll, "all");
        bindOrderFilter(R.id.filterOrdersUnpaid, "unpaid");
        bindOrderFilter(R.id.filterOrdersConfirm, "confirm");
        bindOrderFilter(R.id.filterOrdersPrepare, "prepare");
        bindOrderFilter(R.id.filterOrdersShipping, "shipping");
        bindOrderFilter(R.id.filterOrdersDone, "done");
        int[] detailButtons = {R.id.btnOrderDetail1, R.id.btnOrderDetail2, R.id.btnOrderDetail3, R.id.btnOrderDetail4, R.id.btnOrderDetail5};
        int[] trackButtons = {R.id.btnTrackOrder1, R.id.btnOrderDetail2, R.id.btnOrderDetail3, R.id.btnOrderDetail4, R.id.btnOrderDetail5};
        int[] cardIds = {R.id.orderCard1, R.id.orderCard2, R.id.orderCard3, R.id.orderCard4, R.id.orderCard5};
        for (int i = 0; i < detailButtons.length; i++) {
            final int orderIndex = i;
            bindClick(detailButtons[i], () -> openOrderDetail(orderIndex));
            if (i == 0) bindClick(trackButtons[i], () -> openOrderDetail(orderIndex));
            bindClick(cardIds[i], () -> openOrderDetail(orderIndex));
        }
        EditText search = findViewById(R.id.edtOrderSearch);
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterOrderCards(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        setOrderRole(sellerOrderTab);
        setSelectedOrderFilter(selectedOrderFilter);
        filterOrderCards("");
    }

    private void bindOrderFilter(int viewId, String filter) {
        bindClick(viewId, () -> {
            selectedOrderFilter = filter;
            setSelectedOrderFilter(filter);
            EditText search = findViewById(R.id.edtOrderSearch);
            filterOrderCards(search == null ? "" : search.getText().toString());
        });
    }

    private void setSelectedOrderFilter(String filter) {
        int[] ids = {R.id.filterOrdersAll, R.id.filterOrdersUnpaid, R.id.filterOrdersConfirm, R.id.filterOrdersPrepare, R.id.filterOrdersShipping, R.id.filterOrdersDone};
        String[] values = {"all", "unpaid", "confirm", "prepare", "shipping", "done"};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = findViewById(ids[i]);
            if (chip == null) continue;
            boolean active = values[i].equals(filter);
            chip.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
            chip.setTextColor(active ? 0xFFFFFFFF : 0xFF26332D);
        }
    }

    private void setOrderRole(boolean seller) {
        sellerOrderTab = seller;
        LinearLayout buyerTab = findViewById(R.id.tabBuyer);
        LinearLayout sellerTab = findViewById(R.id.tabSeller);
        if (buyerTab != null) {
            if (seller) buyerTab.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            else buyerTab.setBackgroundResource(R.drawable.bg_white_card_rounded);
        }
        if (sellerTab != null) {
            if (seller) sellerTab.setBackgroundResource(R.drawable.bg_white_card_rounded);
            else sellerTab.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        TextView section = findViewById(R.id.txtOrderListSection);
        if (section != null) section.setText(seller ? "ĐƠN ĐI" : "ĐƠN ĐẾN");
        updateOrderCardStatusLabels();
        EditText search = findViewById(R.id.edtOrderSearch);
        filterOrderCards(search == null ? "" : search.getText().toString());
    }

    private void updateOrderCardStatusLabels() {
        int[] cardIds = {R.id.orderCard1, R.id.orderCard2, R.id.orderCard3, R.id.orderCard4, R.id.orderCard5};
        int[] labelIds = {R.id.txtOrderStatusCard1, R.id.txtOrderStatusCard2, R.id.txtOrderStatusCard3, R.id.txtOrderStatusCard4, R.id.txtOrderStatusCard5};
        for (int i = 0; i < cardIds.length; i++) {
            View card = findViewById(cardIds[i]);
            TextView label = findViewById(labelIds[i]);
            if (card == null || label == null) continue;
            String status = orderStatuses[i];
            label.setText("● " + status);
            String category = "Đã giao".equals(status) ? "done"
                    : "Đang giao".equals(status) ? "shipping"
                    : "Đang chuẩn bị".equals(status) ? "prepare" : "confirm";
            String tag = String.valueOf(card.getTag());
            String[] parts = tag.split("\\|", 3);
            if (parts.length == 3) card.setTag(parts[0] + "|" + category + "|" + parts[2]);
        }
    }

    private void filterOrderCards(String query) {
        int[] cardIds = {R.id.orderCard1, R.id.orderCard2, R.id.orderCard3, R.id.orderCard4, R.id.orderCard5};
        int visibleCount = 0;
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        for (int id : cardIds) {
            View card = findViewById(id);
            if (card == null) continue;
            String tag = String.valueOf(card.getTag());
            String[] parts = tag.split("\\|", 3);
            if (parts.length < 3) continue;
            boolean roleMatches = (sellerOrderTab ? "seller" : "buyer").equals(parts[0]);
            boolean statusMatches = "all".equals(selectedOrderFilter) || selectedOrderFilter.equals(parts[1]);
            boolean queryMatches = parts[2].toLowerCase().contains(normalizedQuery);
            boolean visible = roleMatches && statusMatches && queryMatches;
            card.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) visibleCount++;
        }
        TextView count = findViewById(R.id.txtOrderCount);
        if (count != null) count.setText(String.valueOf(visibleCount));
        TextView empty = findViewById(R.id.txtOrdersEmpty);
        if (empty != null) empty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void openOrderDetail(int orderIndex) {
        selectedOrderIndex = orderIndex;
        showScreen(R.layout.activity_order_detail);
    }

    private void bindOrderDetail() {
        bindClick(R.id.btnOrderDetailBack, () -> showPreviousScreen(R.layout.activity_orders));
        bindClick(R.id.btnTrackShipping, () -> showScreen(R.layout.activity_delivery));
        bindClick(R.id.btnChatOrderSeller, () -> showScreen(R.layout.activity_chat));
        bindClick(R.id.btnConfirmReceived, this::confirmOrderReceived);
        bindClick(R.id.btnReportOrder, this::reportOrderIssue);
        bindClick(R.id.btnCancelOrder, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Yêu cầu hủy đơn")
                .setMessage("Gửi yêu cầu hủy đơn này để người bán xác nhận?")
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Gửi yêu cầu", (dialog, which) ->
                        Toast.makeText(this, "Đã gửi yêu cầu hủy đơn mẫu.", Toast.LENGTH_SHORT).show())
                .show());

        TextView title = findViewById(R.id.txtOrderDetailTitle);
        TextView badge = findViewById(R.id.txtOrderTypeBadge);
        TextView heroStatus = findViewById(R.id.txtOrderHeroStatus);
        TextView orderStatus = findViewById(R.id.txtOrderStatusSummary);
        TextView shippingStatus = findViewById(R.id.txtOrderShippingSummary);
        TextView productName = findViewById(R.id.txtOrderDetailProductName);
        TextView productEmoji = findViewById(R.id.txtOrderDetailProductEmoji);
        TextView seller = findViewById(R.id.txtOrderDetailSeller);
        TextView type = findViewById(R.id.txtOrderDetailType);
        TextView productPrice = findViewById(R.id.txtOrderProductPrice);
        TextView shippingPrice = findViewById(R.id.txtOrderShippingPrice);
        TextView total = findViewById(R.id.txtOrderTotal);
        TextView deliveryStatus = findViewById(R.id.txtOrderDeliveryStatus);
        if (title != null) title.setText("Đơn hàng #" + orderNumbers[selectedOrderIndex]);
        if (badge != null) badge.setText("0đ".equals(orderPrices[selectedOrderIndex]) ? "FREE" : "PURCHASE");
        if (heroStatus != null) heroStatus.setText(orderStatuses[selectedOrderIndex]);
        if (orderStatus != null) orderStatus.setText(orderStatuses[selectedOrderIndex]);
        if (shippingStatus != null) shippingStatus.setText(orderShippingStatuses[selectedOrderIndex]);
        if (productName != null) productName.setText(orderNames[selectedOrderIndex]);
        if (productEmoji != null) productEmoji.setText(orderEmojis[selectedOrderIndex]);
        if (seller != null) seller.setText(orderSellers[selectedOrderIndex]);
        if (type != null) type.setText("0đ".equals(orderPrices[selectedOrderIndex]) ? "Free" : "For Sale");
        if (productPrice != null) productPrice.setText(orderPrices[selectedOrderIndex]);
        if (shippingPrice != null) shippingPrice.setText("25.000đ");
        if (total != null) total.setText(orderTotals[selectedOrderIndex]);
        if (deliveryStatus != null) deliveryStatus.setText(orderShippingStatuses[selectedOrderIndex]);
        TextView currentStep = findViewById(R.id.txtTimelineCurrentTitle);
        boolean complete = "Đã giao".equals(orderStatuses[selectedOrderIndex]);
        if (currentStep != null) currentStep.setText(complete ? "Đã giao đến bạn" : orderShippingStatuses[selectedOrderIndex]);
        TextView timelineCurrentIcon = findViewById(R.id.txtTimelineCurrentIcon);
        TextView timelineOutIcon = findViewById(R.id.txtTimelineOutIcon);
        TextView timelineDeliveredIcon = findViewById(R.id.txtTimelineDeliveredIcon);
        if (complete) {
            if (timelineCurrentIcon != null) timelineCurrentIcon.setText("✓");
            if (timelineOutIcon != null) timelineOutIcon.setText("✓");
            if (timelineDeliveredIcon != null) timelineDeliveredIcon.setText("●");
        }
        TextView heroIcon = findViewById(R.id.txtOrderHeroIcon);
        if (heroIcon != null) heroIcon.setText(complete ? "✓" : "▣");
        android.widget.Button confirm = findViewById(R.id.btnConfirmReceived);
        if (confirm != null) {
            confirm.setText(complete ? "Đơn hàng đã hoàn tất" : "Xác nhận đã nhận hàng");
            confirm.setEnabled(!complete);
            confirm.setAlpha(complete ? 0.65f : 1f);
        }
    }

    private void confirmOrderReceived() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác nhận nhận hàng")
                .setMessage("Bạn xác nhận đã nhận được " + orderNames[selectedOrderIndex] + "?")
                .setNegativeButton("Chưa nhận", null)
                .setPositiveButton("Đã nhận hàng", (dialog, which) -> {
                    TextView hero = findViewById(R.id.txtOrderHeroStatus);
                    TextView status = findViewById(R.id.txtOrderStatusSummary);
                    TextView shipping = findViewById(R.id.txtOrderShippingSummary);
                    TextView delivery = findViewById(R.id.txtOrderDeliveryStatus);
                    TextView current = findViewById(R.id.txtTimelineCurrentTitle);
                    TextView currentIcon = findViewById(R.id.txtTimelineCurrentIcon);
                    TextView outIcon = findViewById(R.id.txtTimelineOutIcon);
                    TextView deliveredIcon = findViewById(R.id.txtTimelineDeliveredIcon);
                    if (hero != null) hero.setText("Đã giao");
                    if (status != null) status.setText("Đã giao");
                    if (shipping != null) shipping.setText("Đã giao");
                    if (delivery != null) delivery.setText("Đã giao");
                    if (current != null) current.setText("Đã giao đến bạn");
                    if (currentIcon != null) currentIcon.setText("✓");
                    if (outIcon != null) outIcon.setText("✓");
                    if (deliveredIcon != null) deliveredIcon.setText("●");
                    TextView heroIcon = findViewById(R.id.txtOrderHeroIcon);
                    if (heroIcon != null) heroIcon.setText("✓");
                    android.widget.Button confirm = findViewById(R.id.btnConfirmReceived);
                    if (confirm != null) { confirm.setText("Đơn hàng đã hoàn tất"); confirm.setEnabled(false); }
                    orderStatuses[selectedOrderIndex] = "Đã giao";
                    orderShippingStatuses[selectedOrderIndex] = "Đã giao";
                    updateOrderCardStatusLabels();
                    Toast.makeText(this, "Đã xác nhận nhận hàng.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void reportOrderIssue() {
        EditText description = new EditText(this);
        description.setHint("Mô tả vấn đề với đơn hàng");
        description.setMinLines(3);
        description.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int padding = dp(20);
        description.setPadding(padding, padding, padding, padding);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Báo cáo vấn đề")
                .setView(description)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi báo cáo", (dialog, which) ->
                        Toast.makeText(this, "Đã ghi nhận báo cáo đơn hàng mẫu.", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void bindDelivery() {
        bindClick(R.id.btnDeliveryBack, () -> showPreviousScreen(R.layout.activity_order_detail));
        bindClick(R.id.btnOpenDeliveryMap, () -> showScreen(R.layout.activity_delivery_map));
    }

    private void bindDeliveryMap() {
        bindClick(R.id.btnMapBack, () -> showPreviousScreen(R.layout.activity_delivery));
    }

    private void bindMyListings() {
        bindClick(R.id.btnListingsBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.btnCreateListing, () -> {
            editListingMode = false;
            showScreen(R.layout.activity_sell);
        });
        bindClick(R.id.btnEditListing1, () -> {
            editListingMode = true;
            showScreen(R.layout.activity_sell);
        });
        bindClick(R.id.btnEditListing2, () -> {
            editListingMode = true;
            showScreen(R.layout.activity_sell);
        });
    }

    private void bindFavorites() {
        bindClick(R.id.btnFavoritesBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.favoriteProduct1, () -> openProduct("Váy hoa vintage", "👗", "Miễn phí · 0đ"));
        bindClick(R.id.favoriteProduct2, () -> openProduct("Quần jeans xanh", "👖", "Miễn phí · 0đ"));
    }

    private void bindRequestStatus() {
        bindClick(R.id.btnBack, () -> showPreviousScreen(R.layout.activity_transactions));
        bindClick(R.id.btnChatWithDonor, () -> showScreen(R.layout.activity_chat));
    }

    private void bindTransactionHistory() {
        bindClick(R.id.btnHistoryBack, () -> showPreviousScreen(R.layout.activity_profile));
    }

    private void bindTransactions() {
        bindClick(R.id.btnTransactionsBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.transactionRequestRow, () -> showScreen(R.layout.activity_request_status));
        bindClick(R.id.transactionPurchaseRow, () -> openOrderDetail(0));
        bindClick(R.id.transactionIssueRow, () -> openOrderDetail(0));
        bindClick(R.id.tabReceive, () -> setTransactionRole(false));
        bindClick(R.id.tabGive, () -> setTransactionRole(true));
    }

    private void setTransactionRole(boolean giving) {
        TextView heading = findViewById(R.id.txtTransactionHeading);
        TextView receive = findViewById(R.id.tabReceive);
        TextView give = findViewById(R.id.tabGive);
        if (heading != null) heading.setText(giving ? "Món đồ bạn đang tặng và bán" : "Món đồ bạn đang nhận và mua");
        if (receive != null) receive.setBackgroundTintList(android.content.res.ColorStateList.valueOf(giving ? 0xFFE8ECE8 : 0xFFFFFFFF));
        if (give != null) give.setBackgroundTintList(android.content.res.ColorStateList.valueOf(giving ? 0xFFFFFFFF : 0xFFE8ECE8));
        LinearLayout requestRow = findViewById(R.id.transactionRequestRow);
        if (requestRow != null && requestRow.getChildCount() > 1 && requestRow.getChildAt(1) instanceof LinearLayout) {
            LinearLayout text = (LinearLayout) requestRow.getChildAt(1);
            if (text.getChildCount() > 0 && text.getChildAt(0) instanceof TextView) {
                ((TextView) text.getChildAt(0)).setText(giving ? "Yêu cầu nhận áo sơ mi linen" : "Yêu cầu áo blazer màu kem");
            }
        }
        Toast.makeText(this, giving ? "Đang xem giao dịch tặng và bán." : "Đang xem giao dịch nhận và mua.", Toast.LENGTH_SHORT).show();
    }

    private void bindAddresses() {
        bindClick(R.id.btnAddressesBack, () -> showPreviousScreen(R.layout.activity_profile));
        TextView addressText = findViewById(R.id.txtAddressContent);
        if (addressText != null) addressText.setText(profileName + " · " + profilePhone + "\n" + shippingAddress);
        bindClick(R.id.btnEditAddress, () -> editShippingAddress(addressText));
        bindClick(R.id.btnAddAddress, () -> editShippingAddress(addressText));
    }

    private void editShippingAddress(TextView addressText) {
        EditText input = new EditText(this);
        input.setText(shippingAddress);
        input.setMinLines(2);
        input.setGravity(android.view.Gravity.TOP);
        input.setHint("Số nhà, đường, phường, quận, thành phố");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Địa chỉ nhận hàng")
                .setView(input)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    shippingAddress = input.getText().toString().trim();
                    if (addressText != null) addressText.setText(profileName + " · " + profilePhone + "\n" + shippingAddress);
                    Toast.makeText(this, "Đã lưu địa chỉ mẫu.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void bindSupport() {
        bindClick(R.id.btnSupportBack, () -> showPreviousScreen(R.layout.activity_profile));
        bindClick(R.id.btnSupportFaq, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Câu hỏi thường gặp")
                .setItems(new String[]{"Làm sao để đăng tin?", "Thanh toán được bảo vệ thế nào?", "Theo dõi đơn hàng ở đâu?"}, (dialog, which) -> {
                    String[] answers = {
                            "Chọn Đăng bán, thêm ảnh, thông tin món đồ, hình thức và xuất bản.",
                            "Khoản thanh toán mẫu được giữ trong Payment Holding đến khi đơn hoàn tất.",
                            "Mở Tài khoản → Đơn hàng của tôi → Theo dõi."
                    };
                    new androidx.appcompat.app.AlertDialog.Builder(this).setMessage(answers[which]).setPositiveButton("Đóng", null).show();
                }).show());
        bindClick(R.id.btnSupportPolicy, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chính sách cộng đồng").setMessage("Đăng thông tin và hình ảnh trung thực. Không giao dịch hàng cấm; giữ trao đổi và thanh toán trong ứng dụng.").setPositiveButton("Đã hiểu", null).show());
        bindClick(R.id.btnSupportContact, () -> showScreen(R.layout.activity_messages));
    }

    private void bindChat() {
        bindClick(R.id.btnBack, () -> showPreviousScreen(R.layout.activity_messages));
        bindClick(R.id.btnAddToCart, () -> showScreen(R.layout.activity_cart));
        bindClick(R.id.btnBuyNow, () -> showScreen(R.layout.activity_checkout));
        bindClick(R.id.btnAttach, () -> Toast.makeText(this, "Chọn ảnh sẽ được kết nối ở bản tích hợp tiếp theo.", Toast.LENGTH_SHORT).show());
        LinearLayout messages = findViewById(R.id.chatMessagesContainer);
        if (messages != null) {
            addChatBubble(messages, "Chào bạn, mình quan tâm chiếc áo khoác này.", false);
            addChatBubble(messages, "Chào Minh Anh! Áo vẫn còn và tình trạng rất tốt nhé.", true);
            addChatBubble(messages, "Bạn có thể gửi thêm ảnh phần tay áo không?", false);
        }
        EditText input = findViewById(R.id.edtMessageInput);
        bindClick(R.id.btnSendMessage, () -> {
            if (input == null || input.getText().toString().trim().isEmpty()) return;
            if (messages != null) addChatBubble(messages, input.getText().toString().trim(), true);
            input.setText("");
        });
    }

    private void addChatBubble(LinearLayout messages, String message, boolean sent) {
        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(14);
        bubble.setTextColor(sent ? 0xFFFFFFFF : 0xFF26332D);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackgroundResource(sent ? R.drawable.bg_bubble_sent : R.drawable.bg_bubble_received);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels * 3 / 4,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = sent ? android.view.Gravity.END : android.view.Gravity.START;
        params.bottomMargin = dp(10);
        bubble.setLayoutParams(params);
        messages.addView(bubble);
    }

    private void bindAdminLogin() {
        bindClick(R.id.btnAdminLoginBack, () -> showScreen(R.layout.activity_login));
        EditText adminEmail = findViewById(R.id.edtAdminEmail);
        if (adminEmail != null && adminEmail.getText().toString().trim().isEmpty()) {
            adminEmail.setText("admin@fashion2hand.com");
        }
        bindClick(R.id.btnAdminLogin, () -> {
            EditText email = findViewById(R.id.edtAdminEmail);
            EditText password = findViewById(R.id.edtAdminPassword);
            if (email == null || email.getText().toString().trim().isEmpty()) {
                if (email != null) email.setError("Vui lòng nhập email quản trị");
                return;
            }
            if (password == null || password.getText().toString().isEmpty()) {
                if (password != null) password.setError("Vui lòng nhập mật khẩu");
                return;
            }
            if (!"admin@fashion2hand.com".equalsIgnoreCase(email.getText().toString().trim())) {
                email.setError("Dùng tài khoản demo admin@fashion2hand.com");
                return;
            }
            openAdminActivity();
        });
    }

    private void openAdminActivity() {
        startActivity(new Intent(this, AdminActivity.class));
    }

    private void bindClick(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private void appendVisibleText(View view, StringBuilder content) {
        if (view instanceof TextView) content.append(((TextView) view).getText()).append(' ');
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) appendVisibleText(group.getChildAt(i), content);
        }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    public void onBackPressed() {
        if (currentScreen == R.layout.activity_home || currentScreen == R.layout.activity_login || currentScreen == R.layout.activity_splash) {
            super.onBackPressed();
        } else {
            int fallback = currentScreen == R.layout.activity_register ? R.layout.activity_login
                    : currentScreen == R.layout.activity_order_detail ? R.layout.activity_orders
                    : currentScreen == R.layout.activity_delivery_map ? R.layout.activity_delivery
                    : currentScreen == R.layout.activity_delivery ? R.layout.activity_order_detail
                    : currentScreen == R.layout.activity_account_verification ? R.layout.activity_edit_profile
                    : currentScreen == R.layout.activity_notifications ? notificationReturnScreen
                    : currentScreen == R.layout.activity_transactions || currentScreen == R.layout.activity_orders
                    || currentScreen == R.layout.activity_transaction_history || currentScreen == R.layout.activity_my_listings
                    || currentScreen == R.layout.activity_favorites || currentScreen == R.layout.activity_addresses
                    || currentScreen == R.layout.activity_support ? R.layout.activity_profile : R.layout.activity_home;
            showPreviousScreen(fallback);
        }
    }
}
