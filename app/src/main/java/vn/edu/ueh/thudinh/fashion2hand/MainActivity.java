package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Main entry point for the Fashion2hand frontend prototype. Uses local sample data only. */
public class MainActivity extends AppCompatActivity {
    private int currentScreen = R.layout.activity_login;
    private int chatReturnScreen = R.layout.activity_order_detail;
    private int productReturnScreen = R.layout.activity_explore;
    private int cartReturnScreen = R.layout.activity_home;
    private int checkoutReturnScreen = R.layout.activity_cart;
    private boolean editingAddress;
    private boolean editingSecondaryAddress;
    private String otpFlow = "password_reset";
    private String otpTarget = "";
    private boolean currentProductIsFree;
    private String currentProductName = "";
    private String currentProductEmoji = "👕";
    private String currentProductPrice = "";
    private Bitmap selectedProfilePhoto;
    private String selectedCategory = "Tất cả";
    private Uri selectedListingPhoto;
    private Bitmap capturedListingPhoto;
    private ActivityResultLauncher<String> galleryPicker;
    private ActivityResultLauncher<Void> cameraPicker;
    private ActivityResultLauncher<String> identityGalleryPicker;
    private ActivityResultLauncher<Void> identityCameraPicker;
    private ActivityResultLauncher<String> profilePhotoPicker;
    private ActivityResultLauncher<Void> profileCameraPicker;
    private ActivityResultLauncher<String> reportEvidencePicker;
    private ActivityResultLauncher<Void> reportCameraPicker;
    private String identityPhotoSlot = "";
    private String messageFilter = "all";
    private String messageSearchQuery = "";
    private String orderRoleFilter = "buyer";
    private String orderStatusFilter = "all";
    private String orderSearchQuery = "";
    private boolean updatingCartSelection;
    private boolean editingListing;
    private String notificationFilter = "all";
    private boolean hideReadNotifications;
    private int reportEvidenceCount;
    private final java.util.ArrayList<Object> reportEvidenceItems = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        galleryPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedListingPhoto = uri;
                capturedListingPhoto = null;
                showSelectedListingPhoto();
            }
        });
        cameraPicker = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
                capturedListingPhoto = bitmap;
                selectedListingPhoto = null;
                showSelectedListingPhoto();
            }
        });
        identityGalleryPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) showIdentityPhotoSelected(uri, null);
        });
        identityCameraPicker = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) showIdentityPhotoSelected(null, bitmap);
        });
        profilePhotoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) showProfilePhotoSelected(uri, null);
        });
        profileCameraPicker = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) showProfilePhotoSelected(null, bitmap);
        });
        reportEvidencePicker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                int remaining = 5 - reportEvidenceItems.size();
                for (int i = 0; i < Math.min(remaining, uris.size()); i++) reportEvidenceItems.add(uris.get(i));
                reportEvidenceCount = reportEvidenceItems.size();
                updateReportEvidenceLabel();
                renderReportEvidencePreviews();
            }
        });
        reportCameraPicker = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null && reportEvidenceItems.size() < 5) {
                reportEvidenceItems.add(bitmap);
                reportEvidenceCount = reportEvidenceItems.size();
                updateReportEvidenceLabel();
                renderReportEvidencePreviews();
            }
        });
        EdgeToEdge.enable(this);
        showScreen(R.layout.activity_splash);
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (currentScreen == R.layout.activity_splash) showScreen(R.layout.activity_login);
        }, 1200);
    }

    private void showScreen(int layoutId) {
        currentScreen = layoutId;
        setContentView(layoutId);
        applySystemBarInsets();
        if (layoutId == R.layout.activity_login) bindLogin();
        if (layoutId == R.layout.activity_admin_login) bindAdminLogin();
        if (layoutId == R.layout.activity_register) bindRegister();
        bindNavigation();
        if (layoutId == R.layout.activity_home) bindHome();
        if (layoutId == R.layout.activity_explore) bindExplore();
        if (layoutId == R.layout.activity_favorites) bindFavorites();
        if (layoutId == R.layout.activity_my_listings) bindMyListings();
        if (layoutId == R.layout.activity_orders) bindOrders();
        if (layoutId == R.layout.activity_order_detail) bindOrderDetail();
        if (layoutId == R.layout.activity_report_issue) bindReportIssue();
        if (layoutId == R.layout.activity_cancel_order) bindCancelOrder();
        if (layoutId == R.layout.activity_transaction_history) bindTransactionHistory();
        if (layoutId == R.layout.activity_transactions) bindTransactions();
        if (layoutId == R.layout.activity_chat) bindOrderChat();
        if (layoutId == R.layout.activity_delivery) bindDelivery();
        if (layoutId == R.layout.activity_delivery_map) bindDeliveryMap();
        if (layoutId == R.layout.activity_cart) bindCart();
        if (layoutId == R.layout.activity_notifications) bindNotifications();
        if (layoutId == R.layout.activity_checkout) bindCheckout();
        if (layoutId == R.layout.activity_addresses) bindAddresses();
        if (layoutId == R.layout.activity_address_form) bindAddressForm();
        if (layoutId == R.layout.activity_edit_profile) bindEditProfile();
        if (layoutId == R.layout.activity_payment_info) bindPaymentInfo();
        if (layoutId == R.layout.activity_settings) bindSettings();
        if (layoutId == R.layout.activity_privacy_security) bindPrivacySecurity();
        if (layoutId == R.layout.activity_language) bindLanguage();
        if (layoutId == R.layout.activity_forgot_password) bindForgotPassword();
        if (layoutId == R.layout.activity_otp_verification) bindOtpVerification();
        if (layoutId == R.layout.activity_new_password) bindNewPassword();
        if (layoutId == R.layout.activity_change_email) bindChangeEmail();
        if (layoutId == R.layout.activity_verify_phone) bindVerifyPhone();
        if (layoutId == R.layout.activity_password_reset_success) bindPasswordResetSuccess();
        if (layoutId == R.layout.activity_support) bindSupport();
        if (layoutId == R.layout.activity_support_faq) bindSupportFaq();
        if (layoutId == R.layout.activity_community_policy) bindCommunityPolicy();
        if (layoutId == R.layout.activity_support_contact) bindSupportContact();
        if (layoutId == R.layout.activity_request_status) bindRequestStatus();
        if (layoutId == R.layout.activity_sell_success) bindSellSuccess();
        if (layoutId == R.layout.activity_sell) bindSell();
        if (layoutId == R.layout.activity_sell_details) bindSellDetails();
        if (layoutId == R.layout.activity_sell_type) bindSellType();
        if (layoutId == R.layout.activity_sell_preview) bindSellPreview();
        if (layoutId == R.layout.activity_requestfree) bindFreeRequest();
        if (layoutId == R.layout.activity_messages) bindMessages();
        if (layoutId == R.layout.activity_profile) bindProfile();
        if (layoutId == R.layout.activity_account_verification) bindAccountVerification();
        if (layoutId == R.layout.activity_verification_status) bindVerificationStatus();
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

            Toast.makeText(this, "Đăng nhập demo thành công.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_home);
        });

        bindClick(R.id.btnOpenRegister, () -> showScreen(R.layout.activity_register));

        bindClick(R.id.btnOpenAdminLogin, () -> showScreen(R.layout.activity_admin_login));

        bindClick(R.id.btnGoogleLogin, () -> {
            Toast.makeText(this, "Mô phỏng đăng nhập Google ở frontend.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_home);
        });

        bindClick(R.id.btnForgotPassword, () ->
                showScreen(R.layout.activity_forgot_password));
    }

    private void bindRegister() {
        bindClick(R.id.btnOpenLogin, () -> showScreen(R.layout.activity_login));
        bindClick(R.id.btnCreateAccount, () -> {
            EditText name = findViewById(R.id.edtRegisterName);
            EditText email = findViewById(R.id.edtRegisterEmail);
            EditText password = findViewById(R.id.edtRegisterPassword);
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng nhập họ tên"); return; }
            if (email.getText().toString().trim().isEmpty()) { email.setError("Vui lòng nhập email"); return; }
            if (password.getText().length() < 6) { password.setError("Mật khẩu cần ít nhất 6 ký tự"); return; }
            Toast.makeText(this, "Đăng ký demo thành công. Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_login);
        });
    }

    private void bindAdminLogin() {
        bindClick(R.id.btnAdminLoginBack, () -> showScreen(R.layout.activity_login));
        bindClick(R.id.btnAdminLogin, () -> {
            EditText email = findViewById(R.id.edtAdminEmail);
            EditText password = findViewById(R.id.edtAdminPassword);
            String emailValue = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (!"admin@fashion2hand.com".equalsIgnoreCase(emailValue)) {
                email.setError("Email quản trị không hợp lệ");
                return;
            }
            if (passwordValue.trim().isEmpty()) {
                password.setError("Vui lòng nhập mật khẩu");
                return;
            }
            startActivity(new Intent(this, AdminActivity.class));
        });
    }

    private void bindForgotPassword() {
        EditText email = findViewById(R.id.edtRecoveryEmail);
        if (!otpTarget.isEmpty() && "password_reset".equals(otpFlow)) email.setText(otpTarget);
        bindClick(R.id.btnForgotBack, () -> showScreen(R.layout.activity_login));
        bindClick(R.id.btnRecoveryContinue, () -> {
            String value = email.getText().toString().trim();
            if (!value.contains("@") || !value.contains(".")) { email.setError("Nhập email hợp lệ"); return; }
            otpFlow = "password_reset";
            otpTarget = value;
            showScreen(R.layout.activity_otp_verification);
        });
    }

    private void bindOtpVerification() {
        TextView title = findViewById(R.id.txtOtpTitle);
        TextView description = findViewById(R.id.txtOtpDescription);
        TextView target = findViewById(R.id.txtOtpTarget);
        if ("email_change".equals(otpFlow)) {
            title.setText("Xác nhận email mới");
            description.setText("Nhập mã xác thực được gửi đến email mới:");
        } else if ("phone_verify".equals(otpFlow) || "phone_change".equals(otpFlow)) {
            title.setText("Xác minh số điện thoại");
            description.setText("Nhập mã OTP được gửi đến số điện thoại:");
        } else {
            title.setText("Nhập mã xác thực");
            description.setText("Nhập mã xác thực được gửi đến email:");
        }
        target.setText(otpTarget);
        bindClick(R.id.btnOtpBack, () -> {
            if ("email_change".equals(otpFlow)) showScreen(R.layout.activity_change_email);
            else if ("phone_verify".equals(otpFlow) || "phone_change".equals(otpFlow)) showScreen(R.layout.activity_verify_phone);
            else showScreen(R.layout.activity_forgot_password);
        });
        bindClick(R.id.btnResendOtp, () -> Toast.makeText(this, "Đã gửi lại mã (demo).", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnSubmitOtp, () -> {
            EditText code = findViewById(R.id.edtOtpCode);
            if (code.getText().length() != 6) { code.setError("Nhập đủ 6 chữ số"); return; }
            android.content.SharedPreferences.Editor account = getSharedPreferences("account_contact", MODE_PRIVATE).edit();
            if ("password_reset".equals(otpFlow)) {
                showScreen(R.layout.activity_new_password);
            } else if ("email_change".equals(otpFlow)) {
                account.putString("email", otpTarget).apply();
                showScreen(R.layout.activity_edit_profile);
                Toast.makeText(this, "Email đã được cập nhật (demo).", Toast.LENGTH_SHORT).show();
            } else {
                account.putString("phone", otpTarget).putBoolean("phone_verified", true).apply();
                showScreen(R.layout.activity_edit_profile);
                Toast.makeText(this, "Số điện thoại đã được xác minh (demo).", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindNewPassword() {
        bindClick(R.id.btnNewPasswordBack, () -> showScreen(R.layout.activity_otp_verification));
        bindClick(R.id.btnSaveNewPassword, () -> {
            EditText password = findViewById(R.id.edtNewPassword);
            EditText confirmation = findViewById(R.id.edtConfirmNewPassword);
            if (password.getText().length() < 6) { password.setError("Cần ít nhất 6 ký tự"); return; }
            if (!password.getText().toString().equals(confirmation.getText().toString())) { confirmation.setError("Mật khẩu chưa khớp"); return; }
            getSharedPreferences("account_contact", MODE_PRIVATE).edit().putString("password", password.getText().toString()).apply();
            showScreen(R.layout.activity_password_reset_success);
        });
    }

    private void bindPasswordResetSuccess() {
        bindClick(R.id.btnReturnLogin, () -> showScreen(R.layout.activity_login));
    }

    private void bindChangeEmail() {
        TextView current = findViewById(R.id.txtCurrentEmail);
        current.setText(getSharedPreferences("account_contact", MODE_PRIVATE).getString("email", "thudinh.31241026994@st.ueh.edu.vn"));
        EditText newEmail = findViewById(R.id.edtNewEmail);
        if ("email_change".equals(otpFlow) && !otpTarget.isEmpty()) newEmail.setText(otpTarget);
        bindClick(R.id.btnChangeEmailBack, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.btnContinueChangeEmail, () -> {
            EditText email = findViewById(R.id.edtNewEmail);
            String value = email.getText().toString().trim();
            if (!value.contains("@") || !value.contains(".")) { email.setError("Nhập email hợp lệ"); return; }
            otpFlow = "email_change";
            otpTarget = value;
            showScreen(R.layout.activity_otp_verification);
        });
    }

    private void bindVerifyPhone() {
        EditText phone = findViewById(R.id.edtVerifyPhoneNumber);
        phone.setText(("phone_verify".equals(otpFlow) || "phone_change".equals(otpFlow)) && !otpTarget.isEmpty()
                ? otpTarget : getSharedPreferences("account_contact", MODE_PRIVATE).getString("phone", "0901 234 567"));
        TextView title = findViewById(R.id.txtVerifyPhoneTitle);
        title.setText("phone_change".equals(otpFlow) ? "Đổi số điện thoại" : "Xác minh số điện thoại");
        bindClick(R.id.btnVerifyPhoneBack, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.btnSendPhoneOtp, () -> {
            String value = phone.getText().toString().trim();
            if (value.replaceAll("\\D", "").length() < 9) { phone.setError("Nhập số điện thoại hợp lệ"); return; }
            otpTarget = value;
            showScreen(R.layout.activity_otp_verification);
        });
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
        bindClick(R.id.btnNotificationsHome, () -> showScreen(R.layout.activity_notifications));
        bindClick(R.id.btnWomenCategory, () -> openExploreWithCategory("Nữ"));
        bindClick(R.id.btnMenCategory, () -> openExploreWithCategory("Nam"));
        bindClick(R.id.btnKidsCategory, () -> openExploreWithCategory("Trẻ em"));
        bindClick(R.id.btnShoesCategory, () -> openExploreWithCategory("Giày"));
        bindClick(R.id.btnBagsCategory, () -> openExploreWithCategory("Túi"));
        bindClick(R.id.btnCartHome, this::openCart);
        bindClick(R.id.btnExploreHero, () -> showScreen(R.layout.activity_explore));
        bindClick(R.id.productCard1, () -> openProduct("Áo thun basic", "👕", "Miễn phí · 0đ"));
        bindClick(R.id.productCard2, () -> openProduct("Váy hoa vintage", "👗", "Miễn phí · 0đ"));
        bindClick(R.id.productCard3, () -> openProduct("Quần jeans xanh", "👖", "Miễn phí · 0đ"));
        bindClick(R.id.productCard4, () -> openProduct("Áo khoác denim", "🥼", "120.000 đ"));
        int[] heartIds = {R.id.likeProduct1, R.id.likeProduct2, R.id.likeProduct3, R.id.likeProduct4};
        android.content.SharedPreferences favorites = getSharedPreferences("favorite_products", MODE_PRIVATE);
        for (int i = 0; i < heartIds.length; i++) {
            final int index = i + 1;
            TextView heart = findViewById(heartIds[i]);
            String key = "home_favorite_" + index;
            updateHomeFavoriteHeart(heart, favorites.getBoolean(key, false));
            heart.setOnClickListener(v -> {
                boolean saved = !favorites.getBoolean(key, false);
                favorites.edit().putBoolean(key, saved).apply();
                updateHomeFavoriteHeart(heart, saved);
                Toast.makeText(this, saved ? "Đã lưu sản phẩm." : "Đã bỏ lưu sản phẩm.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateHomeFavoriteHeart(TextView heart, boolean saved) {
        if (heart == null) return;
        heart.setText(saved ? "♥" : "♡");
        heart.setTextColor(saved ? 0xFFF0785D : 0xFF274238);
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

    private void bindFavorites() {
        bindClick(R.id.btnFavoritesBack, () -> showScreen(R.layout.activity_profile));
        RadioGroup filters = findViewById(R.id.favoriteFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderFavoriteProducts(checkedId));
        renderFavoriteProducts(R.id.filterFavoritesAll);
    }

    private void renderFavoriteProducts(int filterId) {
        GridLayout grid = findViewById(R.id.favoriteProductGrid);
        if (grid == null) return;
        grid.removeAllViews();
        String[][] products = {
                {"👕", "Áo thun basic", "0 đ", "Tốt · M · Quận 3", "FREE · 0đ", "#E4F0E8", "available", "home_favorite_1"},
                {"👗", "Váy hoa vintage", "0 đ", "Tốt · S · Thủ Đức", "FREE · 0đ", "#EEE9F7", "available", "home_favorite_2"},
                {"👖", "Quần jeans xanh", "0 đ", "Tốt · L · Bình Thạnh", "FREE · 0đ", "#E7EEF8", "unavailable", "home_favorite_3"},
                {"🥼", "Áo khoác denim", "120.000 đ", "Tốt · M · Quận 1", "FOR SALE", "#F4EBDD", "available", "home_favorite_4"}
        };
        android.content.SharedPreferences favorites = getSharedPreferences("favorite_products", MODE_PRIVATE);
        int favoriteCount = 0;
        for (String[] product : products) {
            if (!favorites.getBoolean(product[7], false)) continue;
            if (filterId == R.id.filterFavoritesAvailable && !"available".equals(product[6])) continue;
            if (filterId == R.id.filterFavoritesUnavailable && !"unavailable".equals(product[6])) continue;
            favoriteCount++;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, 0, 0, dp(8));
            card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            card.setClipToOutline(true);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, dp(8), dp(14));
            card.setLayoutParams(params);

            FrameLayout photo = new FrameLayout(this);
            photo.setBackgroundColor(android.graphics.Color.parseColor(product[5]));
            TextView emoji = new TextView(this);
            emoji.setText(product[0]); emoji.setTextSize(48); emoji.setGravity(17);
            photo.addView(emoji, new FrameLayout.LayoutParams(-1, dp(150)));
            if ("unavailable".equals(product[6])) {
                View dim = new View(this);
                dim.setBackgroundColor(0x55333333);
                photo.addView(dim, new FrameLayout.LayoutParams(-1, dp(150)));
            }
            TextView badge = new TextView(this);
            badge.setText("unavailable".equals(product[6]) ? "ĐÃ BÁN" : product[4]);
            badge.setTextSize(8); badge.setTextColor(0xFFFFFFFF); badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(7), dp(4), dp(7), dp(4)); badge.setBackgroundResource(R.drawable.bg_button_primary);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.START);
            badgeParams.setMargins(dp(7), 0, 0, dp(7)); photo.addView(badge, badgeParams);
            TextView heart = new TextView(this);
            heart.setText("♥"); heart.setTextSize(17); heart.setTextColor(0xFFF0785D); heart.setGravity(17); heart.setBackgroundResource(R.drawable.bg_white_card_rounded);
            FrameLayout.LayoutParams heartParams = new FrameLayout.LayoutParams(dp(32), dp(32), android.view.Gravity.TOP | android.view.Gravity.END);
            heartParams.setMargins(0, dp(7), dp(7), 0); photo.addView(heart, heartParams);
            heart.setOnClickListener(v -> {
                favorites.edit().putBoolean(product[7], false).apply();
                renderFavoriteProducts(filterId);
                Toast.makeText(this, "Đã xóa khỏi sản phẩm đã lưu.", Toast.LENGTH_SHORT).show();
            });
            card.addView(photo);
            TextView name = new TextView(this);
            name.setText(product[1]); name.setTextSize(11); name.setMaxLines(2); name.setTextColor(0xFF173B32); name.setTypeface(null, android.graphics.Typeface.BOLD); name.setPadding(dp(8), dp(8), dp(8), dp(2)); card.addView(name);
            TextView price = new TextView(this);
            price.setText(product[2]); price.setTextSize(10); price.setTextColor(0xFFE66F55); price.setPadding(dp(8), dp(2), dp(8), dp(2)); card.addView(price);
            TextView meta = new TextView(this);
            meta.setText(product[3]); meta.setTextSize(9); meta.setTextColor(0xFF75857F); meta.setPadding(dp(8), dp(2), dp(8), 0); card.addView(meta);
            card.setOnClickListener(v -> openProduct(product[1], product[0], product[2]));
            grid.addView(card);
        }
        if (favoriteCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có sản phẩm đã lưu. Chạm vào ♡ trên món đồ bạn thích.");
            empty.setTextColor(0xFF75857F); empty.setTextSize(12); empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(24), dp(36), dp(24), dp(36));
            GridLayout.LayoutParams emptyParams = new GridLayout.LayoutParams();
            emptyParams.width = -1; emptyParams.columnSpec = GridLayout.spec(0, 2); empty.setLayoutParams(emptyParams);
            grid.addView(empty);
        }
    }

    private void bindMyListings() {
        bindClick(R.id.btnListingsBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnCreateListing, () -> showScreen(R.layout.activity_sell));
        RadioGroup filters = findViewById(R.id.listingFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderMyListings(checkedId));
        renderMyListings(R.id.filterListingAll);
    }

    private void bindOrders() {
        bindClick(R.id.btnOrdersBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnTransactionHistory, () -> showScreen(R.layout.activity_transaction_history));
        addSupplementalOrderCard("ORD10318", "unpaid", "Áo thun basic", "Chờ thanh toán");
        addSupplementalOrderCard("ORD10316", "confirm", "Túi đeo vai vintage", "Chờ xác nhận");
        for (int id : new int[]{R.id.orderCard1, R.id.orderCard2, R.id.orderCard3, R.id.orderCard4, R.id.orderCard5,
                R.id.btnOrderDetail1, R.id.btnOrderDetail2, R.id.btnOrderDetail3, R.id.btnOrderDetail4, R.id.btnOrderDetail5}) {
            bindClick(id, () -> showScreen(R.layout.activity_order_detail));
        }
        bindClick(R.id.btnTrackOrder1, () -> showScreen(R.layout.activity_delivery));
        bindClick(R.id.tabBuyer, () -> setOrderRoleFilter("buyer"));
        bindClick(R.id.tabSeller, () -> setOrderRoleFilter("seller"));
        int[] statusIds = {R.id.filterOrdersAll, R.id.filterOrdersUnpaid, R.id.filterOrdersConfirm,
                R.id.filterOrdersPrepare, R.id.filterOrdersShipping, R.id.filterOrdersDone};
        String[] statuses = {"all", "unpaid", "confirm", "prepare", "shipping", "done"};
        for (int i = 0; i < statusIds.length; i++) {
            final String status = statuses[i];
            bindClick(statusIds[i], () -> setOrderStatusFilter(status));
        }
        EditText search = findViewById(R.id.edtOrderSearch);
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                orderSearchQuery = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                applyOrderFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        bindClick(R.id.btnOrderAdvancedFilter, () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Lọc theo trạng thái")
                .setSingleChoiceItems(new String[]{"Tất cả", "Chờ thanh toán", "Chờ xác nhận", "Đang chuẩn bị", "Đang giao", "Đã giao"},
                        orderStatusIndex(), (dialog, which) -> {
                            setOrderStatusFilter(statuses[which]);
                            dialog.dismiss();
                        }).show());
        updateOrderTabs();
        updateOrderStatusChips();
        applyOrderFilters();
    }

    private void addSupplementalOrderCard(String orderId, String status, String product, String statusLabel) {
        LinearLayout container = findViewById(R.id.orderListContainer);
        TextView empty = findViewById(R.id.txtOrdersEmpty);
        if (container == null || empty == null) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_white_card_rounded);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        card.setTag("buyer|" + status + "|" + orderId.toLowerCase(java.util.Locale.ROOT) + " " + product.toLowerCase(java.util.Locale.ROOT));
        TextView title = new TextView(this);
        title.setText("#" + orderId + "  ·  " + statusLabel);
        title.setTextColor(0xFF173B32); title.setTextSize(11); title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView detail = new TextView(this);
        detail.setText(product + "  ·  Đơn mua / nhận"); detail.setTextColor(0xFF75857F); detail.setTextSize(10);
        detail.setPadding(0, dp(5), 0, 0);
        card.addView(title); card.addView(detail);
        card.setOnClickListener(v -> showScreen(R.layout.activity_order_detail));
        container.addView(card, container.indexOfChild(empty));
    }

    private void setOrderRoleFilter(String role) {
        orderRoleFilter = role;
        updateOrderTabs();
        applyOrderFilters();
    }

    private void setOrderStatusFilter(String status) {
        orderStatusFilter = status;
        updateOrderStatusChips();
        applyOrderFilters();
    }

    private int orderStatusIndex() {
        String[] statuses = {"all", "unpaid", "confirm", "prepare", "shipping", "done"};
        for (int i = 0; i < statuses.length; i++) if (statuses[i].equals(orderStatusFilter)) return i;
        return 0;
    }

    private void updateOrderTabs() {
        View buyer = findViewById(R.id.tabBuyer);
        View seller = findViewById(R.id.tabSeller);
        if (buyer != null) buyer.setAlpha("buyer".equals(orderRoleFilter) ? 1f : .62f);
        if (seller != null) seller.setAlpha("seller".equals(orderRoleFilter) ? 1f : .62f);
    }

    private void updateOrderStatusChips() {
        int[] ids = {R.id.filterOrdersAll, R.id.filterOrdersUnpaid, R.id.filterOrdersConfirm,
                R.id.filterOrdersPrepare, R.id.filterOrdersShipping, R.id.filterOrdersDone};
        String[] statuses = {"all", "unpaid", "confirm", "prepare", "shipping", "done"};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = findViewById(ids[i]);
            if (chip == null) continue;
            boolean selected = statuses[i].equals(orderStatusFilter);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF26332D);
        }
    }

    private void applyOrderFilters() {
        int visible = 0;
        LinearLayout container = findViewById(R.id.orderListContainer);
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);
            if (!(card.getTag() instanceof String) || !String.valueOf(card.getTag()).contains("|")) continue;
            String[] parts = String.valueOf(card.getTag()).split("\\|", 3);
            String role = parts.length > 0 ? parts[0] : "";
            String status = parts.length > 1 ? parts[1] : "";
            String searchable = parts.length > 2 ? parts[2] : "";
            boolean matches = orderRoleFilter.equals(role)
                    && ("all".equals(orderStatusFilter) || orderStatusFilter.equals(status))
                    && (orderSearchQuery.isEmpty() || searchable.contains(orderSearchQuery));
            card.setVisibility(matches ? View.VISIBLE : View.GONE);
            if (matches) visible++;
        }
        TextView empty = findViewById(R.id.txtOrdersEmpty);
        if (empty != null) empty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        TextView count = findViewById(R.id.txtOrderCount);
        if (count != null) count.setText(String.valueOf(visible));
        TextView section = findViewById(R.id.txtOrderListSection);
        if (section != null) section.setText("buyer".equals(orderRoleFilter) ? "ĐƠN ĐẾN" : "ĐƠN ĐI");
    }

    private void bindTransactionHistory() {
        bindClick(R.id.btnHistoryBack, () -> showScreen(R.layout.activity_orders));
        RadioGroup filters = findViewById(R.id.historyFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderTransactionHistory(checkedId));
        renderTransactionHistory(R.id.filterHistoryAll);
    }

    private void bindTransactions() {
        bindClick(R.id.btnTransactionsBack, () -> showScreen(R.layout.activity_profile));
        TextView heading = findViewById(R.id.txtTransactionHeading);
        bindClick(R.id.tabReceive, () -> { if (heading != null) heading.setText("Món đồ bạn đang nhận và mua"); });
        bindClick(R.id.tabGive, () -> { if (heading != null) heading.setText("Món đồ bạn đang tặng và bán"); });
        bindClick(R.id.transactionRequestRow, () -> showScreen(R.layout.activity_request_status));
        bindClick(R.id.transactionPurchaseRow, () -> showScreen(R.layout.activity_orders));
        bindClick(R.id.transactionIssueRow, () -> showScreen(R.layout.activity_order_detail));
    }

    private void bindAddresses() {
        bindClick(R.id.btnAddressesBack, () -> showScreen(R.layout.activity_profile));
        android.content.SharedPreferences address = getSharedPreferences("shipping_address", MODE_PRIVATE);
        String saved = address.getString("formatted", "Nguyễn Minh Anh · 0901 234 567\n12 Nguyễn Thị Minh Khai, Phường 6\nQuận 3, TP. Hồ Chí Minh");
        TextView content = findViewById(R.id.txtAddressContent);
        TextView typeLabel = findViewById(R.id.txtAddressTypeLabel);
        if (content != null) content.setText(saved);
        if (typeLabel != null) typeLabel.setText((address.getBoolean("work", false) ? "▣  Văn phòng" : "⌂  Nhà riêng")
                + (address.getBoolean("default", true) ? "                         MẶC ĐỊNH" : ""));
        LinearLayout cards = findViewById(R.id.addressCards);
        if (cards != null) {
            for (int i = cards.getChildCount() - 1; i > 0; i--) cards.removeViewAt(i);
            String extraAddress = address.getString("formatted_secondary", "");
            if (!extraAddress.isEmpty()) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(15), dp(15), dp(15), dp(8));
                card.setBackgroundResource(R.drawable.bg_white_card_rounded);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.topMargin = dp(10);
                card.setLayoutParams(cardParams);
                TextView label = new TextView(this);
                label.setText((address.getBoolean("work_secondary", false) ? "▣  Văn phòng" : "⌂  Nhà riêng")
                        + (address.getBoolean("default_secondary", false) ? "                         MẶC ĐỊNH" : ""));
                label.setTextColor(0xFF173B32); label.setTextSize(12); label.setTypeface(null, android.graphics.Typeface.BOLD);
                TextView details = new TextView(this);
                details.setText(extraAddress); details.setTextColor(0xFF65756D); details.setTextSize(11); details.setLineSpacing(dp(3), 1f);
                LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2); detailsParams.topMargin = dp(8);
                TextView edit = new TextView(this);
                edit.setText("Chỉnh sửa địa chỉ"); edit.setTextColor(0xFF23795C); edit.setTextSize(10); edit.setGravity(android.view.Gravity.CENTER_VERTICAL);
                edit.setOnClickListener(v -> { editingAddress = true; editingSecondaryAddress = true; showScreen(R.layout.activity_address_form); });
                card.addView(label); card.addView(details, detailsParams); card.addView(edit, new LinearLayout.LayoutParams(-2, dp(40)));
                cards.addView(card);
            }
        }
        bindClick(R.id.btnEditAddress, () -> { editingAddress = true; editingSecondaryAddress = false; showScreen(R.layout.activity_address_form); });
        bindClick(R.id.btnAddAddress, () -> { editingAddress = false; editingSecondaryAddress = false; showScreen(R.layout.activity_address_form); });
    }

    private void bindAddressForm() {
        android.content.SharedPreferences address = getSharedPreferences("shipping_address", MODE_PRIVATE);
        TextView title = findViewById(R.id.txtAddressFormTitle);
        title.setText(editingAddress ? "Chỉnh sửa địa chỉ" : "Thêm địa chỉ mới");
        EditText recipient = findViewById(R.id.edtAddressRecipient);
        EditText phone = findViewById(R.id.edtAddressPhone);
        EditText province = findViewById(R.id.edtAddressProvince);
        EditText district = findViewById(R.id.edtAddressDistrict);
        EditText ward = findViewById(R.id.edtAddressWard);
        EditText street = findViewById(R.id.edtAddressStreet);
        if (editingAddress) {
            String suffix = editingSecondaryAddress ? "_secondary" : "";
            recipient.setText(address.getString("recipient" + suffix, "Nguyễn Minh Anh"));
            phone.setText(address.getString("phone" + suffix, "0901 234 567"));
            province.setText(address.getString("province" + suffix, "TP. Hồ Chí Minh"));
            district.setText(address.getString("district" + suffix, "Quận 3"));
            ward.setText(address.getString("ward" + suffix, "Phường 6"));
            street.setText(address.getString("street" + suffix, "12 Nguyễn Thị Minh Khai"));
            ((RadioGroup) findViewById(R.id.rgAddressType)).check(address.getBoolean("work" + suffix, false) ? R.id.radioAddressWork : R.id.radioAddressHome);
            ((CheckBox) findViewById(R.id.checkAddressDefault)).setChecked(address.getBoolean("default" + suffix, !editingSecondaryAddress));
        }
        bindClick(R.id.btnAddressFormBack, () -> showScreen(R.layout.activity_addresses));
        bindClick(R.id.btnSaveAddress, () -> {
            if (recipient.getText().toString().trim().isEmpty()) { recipient.setError("Nhập tên người nhận"); return; }
            if (phone.getText().toString().trim().isEmpty()) { phone.setError("Nhập số điện thoại"); return; }
            if (province.getText().toString().trim().isEmpty() || district.getText().toString().trim().isEmpty()
                    || ward.getText().toString().trim().isEmpty() || street.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ địa chỉ.", Toast.LENGTH_SHORT).show();
                return;
            }
            String formatted = recipient.getText().toString().trim() + " · " + phone.getText().toString().trim() + "\n"
                    + street.getText().toString().trim() + ", " + ward.getText().toString().trim() + "\n"
                    + district.getText().toString().trim() + ", " + province.getText().toString().trim();
            String suffix = (editingAddress && editingSecondaryAddress) || !editingAddress ? "_secondary" : "";
            boolean isDefault = ((CheckBox) findViewById(R.id.checkAddressDefault)).isChecked();
            android.content.SharedPreferences.Editor editor = address.edit()
                    .putString("recipient" + suffix, recipient.getText().toString().trim())
                    .putString("phone" + suffix, phone.getText().toString().trim())
                    .putString("province" + suffix, province.getText().toString().trim())
                    .putString("district" + suffix, district.getText().toString().trim())
                    .putString("ward" + suffix, ward.getText().toString().trim())
                    .putString("street" + suffix, street.getText().toString().trim())
                    .putString("formatted" + suffix, formatted)
                    .putBoolean("work" + suffix, ((RadioGroup) findViewById(R.id.rgAddressType)).getCheckedRadioButtonId() == R.id.radioAddressWork)
                    .putBoolean("default" + suffix, isDefault);
            if (isDefault && !suffix.isEmpty()) editor.putBoolean("default", false);
            if (isDefault && suffix.isEmpty()) editor.putBoolean("default_secondary", false);
            editor.apply();
            showScreen(R.layout.activity_addresses);
        });
    }

    private void bindEditProfile() {
        setSpinnerOptions(R.id.spinnerProfileGender, new String[]{"Nữ", "Nam", "Khác", "Không muốn tiết lộ"});
        android.content.SharedPreferences contacts = getSharedPreferences("account_contact", MODE_PRIVATE);
        TextView emailValue = findViewById(R.id.txtProfileEmail);
        TextView phoneValue = findViewById(R.id.txtProfilePhone);
        TextView emailStatus = findViewById(R.id.txtProfileEmailStatus);
        TextView phoneStatus = findViewById(R.id.txtProfilePhoneStatus);
        if (emailValue != null) emailValue.setText(contacts.getString("email", "thudinh.31241026994@st.ueh.edu.vn"));
        if (phoneValue != null) phoneValue.setText(contacts.getString("phone", "0901 234 567"));
        if (selectedProfilePhoto != null) {
            TextView avatar = findViewById(R.id.txtEditProfileAvatar);
            if (avatar != null) { avatar.setText(""); avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), selectedProfilePhoto)); }
        }
        if (emailStatus != null && contacts.contains("email")) emailStatus.setText("✓  Email đã xác minh");
        if (phoneStatus != null && contacts.getBoolean("phone_verified", true)) phoneStatus.setText("✓  Số điện thoại đã xác minh");
        bindClick(R.id.btnEditProfileBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnEditProfileSave, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnEditProfileVerify, () -> showScreen(R.layout.activity_account_verification));
        bindClick(R.id.btnEditProfilePayment, () -> showScreen(R.layout.activity_payment_info));
        bindClick(R.id.btnChangeProfilePhoto, () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Đổi ảnh đại diện")
                .setItems(new String[]{"Chụp ảnh bằng camera", "Chọn từ thư viện"}, (dialog, which) -> {
                    if (which == 0) profileCameraPicker.launch(null);
                    else profilePhotoPicker.launch("image/*");
                }).show());
        bindClick(R.id.btnChangeProfileEmail, () -> showScreen(R.layout.activity_change_email));
        bindClick(R.id.btnVerifyPhone, () -> { otpFlow = "phone_verify"; otpTarget = ""; showScreen(R.layout.activity_verify_phone); });
        bindClick(R.id.btnChangeProfilePhone, () -> { otpFlow = "phone_change"; otpTarget = ""; showScreen(R.layout.activity_verify_phone); });
    }

    private void showProfilePhotoSelected(Uri uri, Bitmap bitmap) {
        TextView avatar = findViewById(R.id.txtEditProfileAvatar);
        if (avatar == null) return;
        try {
            Bitmap photo = bitmap;
            if (photo == null && uri != null) {
                try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
                    photo = android.graphics.BitmapFactory.decodeStream(stream);
                }
            }
            if (photo != null) {
                selectedProfilePhoto = photo;
                avatar.setText("");
                avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), photo));
                avatar.setClipToOutline(true);
            }
            Toast.makeText(this, "Đã chọn ảnh đại diện (chỉ lưu trên màn hình demo).", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không đọc được ảnh đã chọn.", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindPaymentInfo() {
        setSpinnerOptions(R.id.spinnerPayoutBank, new String[]{"Vietcombank", "Techcombank", "MB Bank", "BIDV", "ACB"});
        bindClick(R.id.btnPaymentInfoBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnSavePayoutInfo, () -> {
            CheckBox consent = findViewById(R.id.checkPayoutConsent);
            if (consent != null && !consent.isChecked()) {
                Toast.makeText(this, "Vui lòng xác nhận thông tin tài khoản.", Toast.LENGTH_SHORT).show();
                return;
            }
            showScreen(R.layout.activity_profile);
        });
    }

    private void bindSettings() {
        bindClick(R.id.btnSettingsBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.settingAccount, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.settingNotifications, () -> showScreen(R.layout.activity_notifications));
        bindClick(R.id.settingPrivacy, () -> showScreen(R.layout.activity_privacy_security));
        bindClick(R.id.settingLanguage, () -> showScreen(R.layout.activity_language));
        bindClick(R.id.settingLogout, () -> showScreen(R.layout.activity_login));
    }

    private void bindPrivacySecurity() {
        bindClick(R.id.btnPrivacySecurityBack, () -> showScreen(R.layout.activity_settings));
        bindClick(R.id.btnPrivacyIdentityVerification, () -> {
            String status = getSharedPreferences("account_verification", MODE_PRIVATE).getString("status", "none");
            showScreen("none".equals(status) ? R.layout.activity_account_verification : R.layout.activity_verification_status);
        });
    }

    private void bindLanguage() {
        bindClick(R.id.btnLanguageBack, () -> showScreen(R.layout.activity_settings));
    }

    private void bindSupport() {
        bindClick(R.id.btnSupportBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnSupportFaq, () -> showScreen(R.layout.activity_support_faq));
        bindClick(R.id.btnSupportPolicy, () -> showScreen(R.layout.activity_community_policy));
        bindClick(R.id.btnSupportContact, () -> showScreen(R.layout.activity_support_contact));
    }

    private void bindSupportFaq() {
        bindClick(R.id.btnFaqBack, () -> showScreen(R.layout.activity_support));
    }

    private void bindCommunityPolicy() {
        bindClick(R.id.btnPolicyBack, () -> showScreen(R.layout.activity_support));
    }

    private void bindSupportContact() {
        bindClick(R.id.btnSupportContactBack, () -> showScreen(R.layout.activity_support));
    }

    private void bindRequestStatus() {
        bindClick(R.id.btnBack, () -> showScreen(R.layout.activity_product_detail));
        bindClick(R.id.btnChatWithDonor, () -> {
            chatReturnScreen = R.layout.activity_request_status;
            showScreen(R.layout.activity_chat);
        });
    }

    private void bindSellSuccess() {
        bindClick(R.id.btnViewPublishedListing, () -> showScreen(R.layout.activity_my_listings));
        bindClick(R.id.btnManagePublishedListings, () -> showScreen(R.layout.activity_my_listings));
    }

    private void renderTransactionHistory(int filterId) {
        LinearLayout list = findViewById(R.id.transactionHistoryList);
        if (list == null) return;
        list.removeAllViews();
        String[][] transactions = {
                {"🧥", "TXN-F2H-2026-09-28 · 28/09/2026", "Vintage Denim Jacket", "145.000đ", "ĐANG GIỮ", "buy"},
                {"👚", "TXN-F2H-2026-09-24 · 24/09/2026", "Áo sơ mi linen trắng", "25.000đ", "SHIPPING ONLY", "free"},
                {"👖", "TXN-F2H-2026-09-21 · 21/09/2026", "Quần jeans ống rộng", "110.000đ", "RELEASED", "sell"}
        };
        for (String[] transaction : transactions) {
            if (filterId == R.id.filterHistoryBuy && !"buy".equals(transaction[5])) continue;
            if (filterId == R.id.filterHistorySell && !"sell".equals(transaction[5])) continue;
            if (filterId == R.id.filterHistoryFree && !"free".equals(transaction[5])) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            card.setPadding(dp(9), dp(8), dp(9), dp(8));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(8);
            card.setLayoutParams(cardParams);
            TextView photo = new TextView(this);
            photo.setText(transaction[0]); photo.setTextSize(27); photo.setGravity(17); photo.setBackgroundColor(0xFFE8E6E0);
            card.addView(photo, new LinearLayout.LayoutParams(dp(58), dp(62)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(8), 0, dp(4), 0);
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView id = new TextView(this);
            id.setText(transaction[1]); id.setTextSize(6); id.setTextColor(0xFF89958E); info.addView(id);
            TextView name = new TextView(this);
            name.setText(transaction[2]); name.setTextSize(9); name.setMaxLines(1); name.setTextColor(0xFF26332D); name.setTypeface(null, android.graphics.Typeface.BOLD); name.setPadding(0, dp(3), 0, 0); info.addView(name);
            TextView status = new TextView(this);
            status.setText("sell".equals(transaction[5]) ? "Hoàn thành" : ("buy".equals(transaction[5]) ? "Đang giữ" : "Hoàn thành"));
            status.setTextSize(7); status.setTextColor(0xFF348363); status.setPadding(0, dp(4), 0, 0); info.addView(status);
            LinearLayout amountAndStatus = new LinearLayout(this);
            amountAndStatus.setGravity(android.view.Gravity.TOP | android.view.Gravity.END);
            amountAndStatus.setOrientation(LinearLayout.VERTICAL);
            TextView amount = new TextView(this);
            amount.setText(transaction[3]); amount.setTextSize(8); amount.setTextColor(0xFFE97859); amount.setTypeface(null, android.graphics.Typeface.BOLD); amount.setGravity(android.view.Gravity.END); amountAndStatus.addView(amount);
            TextView stage = new TextView(this);
            stage.setText(transaction[4]); stage.setTextSize(5); stage.setTextColor(0xFF348363); stage.setGravity(android.view.Gravity.END); stage.setPadding(0, dp(3), 0, 0); amountAndStatus.addView(stage);
            card.addView(amountAndStatus, new LinearLayout.LayoutParams(dp(64), -2));
            list.addView(card);
        }
    }

    private void bindOrderDetail() {
        bindClick(R.id.btnOrderDetailBack, () -> showScreen(R.layout.activity_orders));
        bindClick(R.id.btnChatOrderSeller, () -> {
            chatReturnScreen = R.layout.activity_order_detail;
            showScreen(R.layout.activity_chat);
        });
        bindClick(R.id.btnTrackShipping, () -> showScreen(R.layout.activity_delivery));
        bindClick(R.id.btnReportOrder, () -> showScreen(R.layout.activity_report_issue));
        bindClick(R.id.btnCancelOrder, () -> showScreen(R.layout.activity_cancel_order));
    }

    private void bindReportIssue() {
        RadioGroup reasons = findViewById(R.id.rgReportReason);
        bindClick(R.id.btnCloseReport, () -> showScreen(R.layout.activity_order_detail));
        updateReportEvidenceLabel();
        renderReportEvidencePreviews();
        bindClick(R.id.btnAttachReportEvidence, () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Thêm bằng chứng")
                .setItems(new String[]{"Chụp ảnh bằng camera", "Chọn từ thư viện"}, (dialog, which) -> {
                    if (reportEvidenceCount >= 5) {
                        Toast.makeText(this, "Bạn đã chọn tối đa 5 ảnh.", Toast.LENGTH_SHORT).show();
                    } else if (which == 0) reportCameraPicker.launch(null);
                    else reportEvidencePicker.launch("image/*");
                }).show());
        bindClick(R.id.btnSubmitReport, () -> {
            if (reasons.getCheckedRadioButtonId() == -1) {
                Toast.makeText(this, "Vui lòng chọn lý do báo cáo.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Đã gửi báo cáo tranh chấp (demo).", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_order_detail);
        });
    }

    private void updateReportEvidenceLabel() {
        TextView button = findViewById(R.id.btnAttachReportEvidence);
        if (button == null) return;
        button.setText(reportEvidenceCount == 0
                ? "▣  Thêm ảnh hoặc bằng chứng\nTối đa 5 ảnh"
                : "✓  Đã chọn " + reportEvidenceCount + "/5 ảnh\nChạm để thêm ảnh khác");
    }

    private void renderReportEvidencePreviews() {
        LinearLayout previews = findViewById(R.id.reportEvidencePreviewContainer);
        View strip = findViewById(R.id.reportEvidencePreviewScroll);
        if (previews == null || strip == null) return;
        previews.removeAllViews();
        for (Object item : reportEvidenceItems) {
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundResource(R.drawable.bg_white_card_rounded);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(56));
            params.setMargins(0, 0, dp(7), 0);
            if (item instanceof Uri) thumbnail.setImageURI((Uri) item);
            else if (item instanceof Bitmap) thumbnail.setImageBitmap((Bitmap) item);
            previews.addView(thumbnail, params);
        }
        strip.setVisibility(reportEvidenceItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void bindCancelOrder() {
        RadioGroup reasons = findViewById(R.id.rgCancelReason);
        EditText description = findViewById(R.id.edtCancelDescription);
        Button submit = findViewById(R.id.btnSubmitCancel);
        Runnable updateSubmitState = () -> {
            boolean ready = reasons.getCheckedRadioButtonId() != -1 && !description.getText().toString().trim().isEmpty();
            submit.setEnabled(ready);
            submit.setAlpha(ready ? 1f : 0.55f);
            submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ready ? 0xFF104536 : 0xFFE4E8E5));
            submit.setTextColor(ready ? 0xFFFFFFFF : 0xFF75857F);
        };
        reasons.setOnCheckedChangeListener((group, checkedId) -> updateSubmitState.run());
        description.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSubmitState.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        bindClick(R.id.btnCloseCancel, () -> showScreen(R.layout.activity_order_detail));
        bindClick(R.id.btnSubmitCancel, () -> {
            if (reasons.getCheckedRadioButtonId() == -1 || description.getText().toString().trim().isEmpty()) return;
            Toast.makeText(this, "Đã gửi yêu cầu hủy đơn (demo).", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_order_detail);
        });
    }

    private void bindOrderChat() {
        bindClick(R.id.btnBack, () -> showScreen(chatReturnScreen));
        bindClick(R.id.btnSendMessage, () -> {
            EditText messageInput = findViewById(R.id.edtMessageInput);
            if (messageInput == null || messageInput.getText().toString().trim().isEmpty()) return;
            Toast.makeText(this, "Tin nhắn demo đã gửi.", Toast.LENGTH_SHORT).show();
            messageInput.setText("");
        });
    }

    private void bindDelivery() {
        bindClick(R.id.btnDeliveryBack, () -> showScreen(R.layout.activity_order_detail));
        bindClick(R.id.btnOpenDeliveryMap, () -> showScreen(R.layout.activity_delivery_map));
    }

    private void bindDeliveryMap() {
        bindClick(R.id.btnMapBack, () -> showScreen(R.layout.activity_delivery));
    }

    private void openCart() {
        cartReturnScreen = currentScreen;
        showScreen(R.layout.activity_cart);
    }

    private void bindCart() {
        bindClick(R.id.btnBack, () -> showScreen(cartReturnScreen));
        CheckBox shop = findViewById(R.id.cbShop);
        CheckBox item1 = findViewById(R.id.cbItem1);
        CheckBox item2 = findViewById(R.id.cbItem2);
        android.content.SharedPreferences cart = getSharedPreferences("cart_demo", MODE_PRIVATE);
        setCartItemVisibility(item1, !cart.getBoolean("removed_1", false));
        setCartItemVisibility(item2, !cart.getBoolean("removed_2", false));
        item1.setChecked(cart.getBoolean("selected_1", true));
        item2.setChecked(cart.getBoolean("selected_2", true));
        android.widget.CompoundButton.OnCheckedChangeListener itemListener = (button, checked) -> {
            if (updatingCartSelection) return;
            cart.edit().putBoolean(button == item1 ? "selected_1" : "selected_2", checked).apply();
            updatingCartSelection = true;
            boolean firstVisible = isCartItemVisible(item1);
            boolean secondVisible = isCartItemVisible(item2);
            boolean any = firstVisible || secondVisible;
            shop.setChecked(any && (!firstVisible || item1.isChecked()) && (!secondVisible || item2.isChecked()));
            updatingCartSelection = false;
            updateCartSummary();
        };
        item1.setOnCheckedChangeListener(itemListener);
        item2.setOnCheckedChangeListener(itemListener);
        shop.setOnCheckedChangeListener((button, checked) -> {
            if (updatingCartSelection) return;
            updatingCartSelection = true;
            if (isCartItemVisible(item1)) { item1.setChecked(checked); cart.edit().putBoolean("selected_1", checked).apply(); }
            if (isCartItemVisible(item2)) { item2.setChecked(checked); cart.edit().putBoolean("selected_2", checked).apply(); }
            updatingCartSelection = false;
            updateCartSummary();
        });
        bindClick(R.id.btnDelete1, () -> removeCartItem(R.id.cbItem1));
        bindClick(R.id.btnDelete2, () -> removeCartItem(R.id.cbItem2));
        bindClick(R.id.btnCheckout, () -> {
            if (selectedCartItemCount() == 0) {
                Toast.makeText(this, "Chọn ít nhất một sản phẩm để thanh toán.", Toast.LENGTH_SHORT).show();
                return;
            }
            checkoutReturnScreen = R.layout.activity_cart;
            showScreen(R.layout.activity_checkout);
        });
        bindClick(R.id.btnExplore, () -> showScreen(R.layout.activity_explore));
        updateCartSummary();
    }

    private int selectedCartItemCount() {
        int count = 0;
        CheckBox item1 = findViewById(R.id.cbItem1);
        CheckBox item2 = findViewById(R.id.cbItem2);
        if (item1 != null && isCartItemVisible(item1) && item1.isChecked()) count++;
        if (item2 != null && isCartItemVisible(item2) && item2.isChecked()) count++;
        return count;
    }

    private boolean isCartItemVisible(CheckBox item) {
        return item != null && item.getParent() instanceof View && ((View) item.getParent()).getVisibility() == View.VISIBLE;
    }

    private void updateCartSummary() {
        CheckBox item1 = findViewById(R.id.cbItem1);
        CheckBox item2 = findViewById(R.id.cbItem2);
        if (item1 == null || item2 == null) return;
        boolean firstVisible = isCartItemVisible(item1);
        boolean secondVisible = isCartItemVisible(item2);
        int count = selectedCartItemCount();
        int subtotal = (firstVisible && item1.isChecked() ? 120000 : 0) + (secondVisible && item2.isChecked() ? 85000 : 0);
        int total = subtotal + (count > 0 ? 25000 : 0);
        TextView subtotalView = findViewById(R.id.txtCartSubtotal);
        TextView totalView = findViewById(R.id.txtCartTotal);
        TextView countView = findViewById(R.id.txtCartSelectedCount);
        Button checkout = findViewById(R.id.btnCheckout);
        if (subtotalView != null) subtotalView.setText(String.format(java.util.Locale.getDefault(), "%,d đ", subtotal));
        if (totalView != null) totalView.setText(String.format(java.util.Locale.getDefault(), "%,d đ", total));
        if (countView != null) countView.setText(count + " sản phẩm");
        if (checkout != null) checkout.setEnabled(count > 0);
        CheckBox shop = findViewById(R.id.cbShop);
        if (shop != null) {
            updatingCartSelection = true;
            shop.setChecked((firstVisible || secondVisible) && (!firstVisible || item1.isChecked()) && (!secondVisible || item2.isChecked()));
            updatingCartSelection = false;
        }
        View withItems = findViewById(R.id.layoutCartWithItems);
        View empty = findViewById(R.id.layoutCartEmpty);
        if (!firstVisible && !secondVisible) {
            if (withItems != null) withItems.setVisibility(View.GONE);
            if (empty != null) empty.setVisibility(View.VISIBLE);
        }
    }

    private void removeCartItem(int checkboxId) {
        CheckBox checkbox = findViewById(checkboxId);
        if (checkbox == null) return;
        boolean first = checkboxId == R.id.cbItem1;
        getSharedPreferences("cart_demo", MODE_PRIVATE).edit()
                .putBoolean(first ? "removed_1" : "removed_2", true).apply();
        setCartItemVisibility(checkbox, false);
        updateCartSummary();
        Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng.", Toast.LENGTH_SHORT).show();
    }

    private void setCartItemVisibility(CheckBox checkbox, boolean visible) {
        if (checkbox == null) return;
        View row = (View) checkbox.getParent();
        row.setVisibility(visible ? View.VISIBLE : View.GONE);
        ViewGroup parent = (ViewGroup) row.getParent();
        int index = parent.indexOfChild(row);
        if (index + 1 < parent.getChildCount()) parent.getChildAt(index + 1).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bindNotifications() {
        bindClick(R.id.btnNotificationsBack, () -> showScreen(R.layout.activity_home));
        int[] filterIds = {R.id.filterNotifAll, R.id.filterNotifOrders, R.id.filterNotifMessages,
                R.id.filterNotifRequests, R.id.filterNotifPayments};
        String[] filters = {"all", "orders", "messages", "requests", "payments"};
        for (int i = 0; i < filterIds.length; i++) {
            final String filter = filters[i];
            bindClick(filterIds[i], () -> {
                notificationFilter = filter;
                applyNotificationFilter(filterIds, filters);
            });
        }
        int[] rowIds = {R.id.notifRowFreeRequest, R.id.notifRowMessage, R.id.notifRowShipping,
                R.id.notifRowPayment, R.id.notifRowRequestDone, R.id.notifRowDispute};
        int[] unreadIds = {R.id.notifUnread1, R.id.notifUnread2, R.id.notifUnread3};
        for (int i = 0; i < rowIds.length; i++) {
            final int rowId = rowIds[i];
            final int unreadId = i < unreadIds.length ? unreadIds[i] : View.NO_ID;
            View row = findViewById(rowId);
            boolean read = getSharedPreferences("notifications_demo", MODE_PRIVATE).getBoolean("read_" + rowId, false);
            if (read && unreadId != View.NO_ID) findViewById(unreadId).setVisibility(View.GONE);
            if (read) row.setBackgroundColor(0xFFFFFFFF);
            bindClick(rowId, () -> {
                getSharedPreferences("notifications_demo", MODE_PRIVATE).edit().putBoolean("read_" + rowId, true).apply();
                if (unreadId != View.NO_ID) findViewById(unreadId).setVisibility(View.GONE);
                row.setBackgroundColor(0xFFFFFFFF);
                if (rowId == R.id.notifRowFreeRequest || rowId == R.id.notifRowRequestDone) showScreen(R.layout.activity_transactions);
                else if (rowId == R.id.notifRowMessage) {
                    chatReturnScreen = R.layout.activity_messages;
                    showScreen(R.layout.activity_messages);
                } else if (rowId == R.id.notifRowPayment) showScreen(R.layout.activity_payment_info);
                else showScreen(R.layout.activity_order_detail);
            });
        }
        applyNotificationFilter(filterIds, filters);
        bindClick(R.id.btnNotificationsMore, () -> new android.app.AlertDialog.Builder(this)
                .setItems(new String[]{"Đánh dấu tất cả đã đọc", hideReadNotifications ? "Hiện tất cả thông báo" : "Ẩn thông báo đã đọc"}, (dialog, which) -> {
                    android.content.SharedPreferences prefs = getSharedPreferences("notifications_demo", MODE_PRIVATE);
                    if (which == 0) {
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        for (int id : rowIds) editor.putBoolean("read_" + id, true);
                        editor.apply();
                        for (int id : unreadIds) findViewById(id).setVisibility(View.GONE);
                        for (int id : rowIds) findViewById(id).setBackgroundColor(0xFFFFFFFF);
                        applyNotificationFilter(filterIds, filters);
                    } else {
                        hideReadNotifications = !hideReadNotifications;
                        applyNotificationFilter(filterIds, filters);
                    }
                }).show());
    }

    private void applyNotificationFilter(int[] filterIds, String[] filters) {
        for (int i = 0; i < filterIds.length; i++) {
            TextView chip = findViewById(filterIds[i]);
            boolean active = filters[i].equals(notificationFilter);
            chip.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
            chip.setTextColor(active ? 0xFFFFFFFF : 0xFF173B32);
        }
        int[] rowIds = {R.id.notifRowFreeRequest, R.id.notifRowMessage, R.id.notifRowShipping,
                R.id.notifRowPayment, R.id.notifRowRequestDone, R.id.notifRowDispute};
        for (int id : rowIds) {
            View row = findViewById(id);
            String category = String.valueOf(row.getTag());
            boolean read = getSharedPreferences("notifications_demo", MODE_PRIVATE).getBoolean("read_" + id, false);
            boolean categoryMatch = "all".equals(notificationFilter) || notificationFilter.equals(category);
            row.setVisibility(categoryMatch && !(hideReadNotifications && read) ? View.VISIBLE : View.GONE);
        }
    }

    private void bindCheckout() {
        bindClick(R.id.btnCheckoutBack, () -> showScreen(checkoutReturnScreen));
        bindClick(R.id.btnPlaceOrder, () -> {
            Toast.makeText(this, "Đã ghi nhận đơn hàng (demo).", Toast.LENGTH_SHORT).show();
            showScreen(R.layout.activity_orders);
        });
    }

    private void renderMyListings(int filterId) {
        LinearLayout container = findViewById(R.id.myListingsContainer);
        if (container == null) return;
        container.removeAllViews();
        String[][] listings = {
                {"👚", "Áo sơ mi linen trắng", "FREE · 0đ", "3 yêu cầu", "Đang hoạt động", "selling", "gift"},
                {"🧥", "Vintage Denim Jacket", "120.000 đ", "86 lượt xem · 12 lượt thích", "Đã đặt trước", "pending", "sale"},
                {"👖", "Quần jeans ống rộng", "85.000 đ", "124 lượt xem · 18 lượt thích", "Đã bán", "sold", "sale"},
                {"👗", "Váy hoa mùa hè", "FREE · 0đ", "16 lượt xem", "Đã tặng", "gifted", "gift"}
        };
        for (String[] listing : listings) {
            boolean matches = filterId == R.id.filterListingAll
                    || filterId == R.id.filterListingSelling && "selling".equals(listing[5])
                    || filterId == R.id.filterListingPending && "pending".equals(listing[5])
                    || filterId == R.id.filterListingSold && "sold".equals(listing[5])
                    || filterId == R.id.filterListingGift && "gifted".equals(listing[5]);
            if (!matches) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            card.setPadding(dp(9), dp(9), dp(9), dp(5));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(9);
            card.setLayoutParams(cardParams);

            LinearLayout detailsRow = new LinearLayout(this);
            detailsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            detailsRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView photo = new TextView(this);
            photo.setText(listing[0]); photo.setTextSize(32); photo.setGravity(17); photo.setBackgroundColor(0xFFE8E6E0);
            detailsRow.addView(photo, new LinearLayout.LayoutParams(dp(66), dp(78)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(9), 0, 0, 0);
            detailsRow.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView priceTag = new TextView(this);
            priceTag.setText(listing[2]); priceTag.setTextSize(8); priceTag.setTextColor("gift".equals(listing[6]) ? 0xFF23795C : 0xFFE66F55);
            priceTag.setTypeface(null, android.graphics.Typeface.BOLD); priceTag.setPadding(dp(6), dp(4), dp(6), dp(4)); priceTag.setBackgroundResource("gift".equals(listing[6]) ? R.drawable.bg_green_light_card : R.drawable.bg_badge_red); info.addView(priceTag);
            TextView title = new TextView(this);
            title.setText(listing[1]); title.setTextSize(10); title.setTextColor(0xFF173B32); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setMaxLines(1); title.setPadding(0, dp(4), 0, 0); info.addView(title);
            TextView stats = new TextView(this);
            stats.setText(listing[3]); stats.setTextSize(8); stats.setTextColor(0xFF75857F); stats.setPadding(0, dp(3), 0, 0); info.addView(stats);
            TextView status = new TextView(this);
            status.setText(listing[4]); status.setTextSize(8); status.setTextColor("sold".equals(listing[5]) ? 0xFFE66F55 : 0xFF23795C); status.setPadding(0, dp(6), 0, 0); info.addView(status);
            card.addView(detailsRow);
            View divider = new View(this); divider.setBackgroundColor(0xFFE9EDE9);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1)); dividerParams.topMargin = dp(7); card.addView(divider, dividerParams);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
            String[] labels = {"Chỉnh sửa", "Xem", "selling".equals(listing[5]) ? "Yêu cầu" : "Ẩn tin", "Xóa"};
            for (int i = 0; i < labels.length; i++) {
                final int action = i;
                TextView button = new TextView(this);
                button.setText(labels[i]); button.setTextSize(8); button.setGravity(17);
                button.setTextColor(i == 3 ? 0xFFE66F55 : 0xFF14513E); button.setPadding(dp(2), dp(8), dp(2), dp(5));
                actions.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
                button.setOnClickListener(v -> {
                    if (action == 1) openProduct(listing[1], listing[0], listing[2]);
                    else if (action == 3) { container.removeView(card); Toast.makeText(this, "Đã xóa tin đăng.", Toast.LENGTH_SHORT).show(); }
                    else if (action == 2 && "selling".equals(listing[5])) showScreen(R.layout.activity_transactions);
                    else if (action == 2) { card.setVisibility(View.GONE); Toast.makeText(this, "Đã ẩn tin đăng.", Toast.LENGTH_SHORT).show(); }
                    else {
                        editingListing = true;
                        currentProductName = listing[1];
                        showScreen(R.layout.activity_sell_details);
                    }
                });
            }
            card.addView(actions);
            container.addView(card);
        }
    }

    private void showExploreFilterSheet() {
        String[] categories = {"Tất cả", "Áo", "Quần", "Váy", "Nữ", "Nam", "Trẻ em", "Giày", "Túi"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Lọc theo danh mục")
                .setItems(categories, (dialog, which) -> applyExploreFilter(categories[which]))
                .show();
    }

    private void addSampleProducts(GridLayout grid) {
        String[][] products = {
                {"🧥", "Áo khoác denim vintage", "Như mới · M", "120.000 đ", "FOR SALE", "#D9D6CC", "Nam"},
                {"👚", "Áo sơ mi linen trắng", "Tốt · S", "0 đ", "FREE · 0đ", "#E9E4DA", "Nữ"},
                {"👖", "Quần jeans ống rộng", "Rất tốt · L", "85.000 đ", "FOR SALE", "#DFE5E5", "Nam"},
                {"👟", "Sneaker cổ thấp", "Tốt · Size 38", "150.000 đ", "FOR SALE", "#E8DDD9", "Giày"},
                {"👜", "Túi đeo vai vintage", "Như mới", "0 đ", "FREE · 0đ", "#E8E0D4", "Túi"},
                {"👗", "Váy hoa mùa hè", "Tốt · M", "95.000 đ", "FOR SALE", "#E8DFE3", "Nữ"},
                {"🧥", "Áo khoác trẻ em", "Tốt · Size 120", "45.000 đ", "FOR SALE", "#E6EBDD", "Trẻ em"}
        };
        for (String[] product : products) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, 0, 0, dp(8));
            card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            card.setClipToOutline(true);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, dp(8), dp(12));
            card.setLayoutParams(params);
            FrameLayout photo = new FrameLayout(this);
            photo.setBackgroundColor(android.graphics.Color.parseColor(product[5]));
            TextView emoji = new TextView(this);
            emoji.setText(product[0]); emoji.setTextSize(48); emoji.setGravity(17);
            photo.addView(emoji, new FrameLayout.LayoutParams(-1, dp(142)));
            TextView badge = new TextView(this);
            badge.setText(product[4]); badge.setTextSize(8); badge.setTextColor(0xFFFFFFFF);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(7), dp(4), dp(7), dp(4));
            badge.setBackgroundResource(R.drawable.bg_button_primary);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.START);
            badgeParams.setMargins(dp(7), 0, 0, dp(7));
            photo.addView(badge, badgeParams);
            TextView favorite = new TextView(this);
            favorite.setText("♡"); favorite.setTextSize(20); favorite.setTextColor(0xFF274238); favorite.setGravity(17);
            favorite.setBackgroundResource(R.drawable.bg_white_card_rounded);
            FrameLayout.LayoutParams favoriteParams = new FrameLayout.LayoutParams(dp(32), dp(32), android.view.Gravity.TOP | android.view.Gravity.END);
            favoriteParams.setMargins(0, dp(7), dp(7), 0);
            photo.addView(favorite, favoriteParams);
            favorite.setOnClickListener(v -> favorite.setText("♥".contentEquals(favorite.getText()) ? "♡" : "♥"));
            card.addView(photo);
            TextView name = new TextView(this); name.setText(product[1]); name.setTextSize(11); name.setMaxLines(2); name.setTextColor(0xFF173B32); name.setPadding(dp(8), dp(8), dp(8), dp(3));
            card.addView(name);
            TextView price = new TextView(this); price.setText(product[3]); price.setTextSize(10); price.setTextColor(0xFFE66F55); price.setPadding(dp(8), dp(2), dp(8), dp(3)); card.addView(price);
            TextView meta = new TextView(this); meta.setText(product[2]); meta.setTextSize(9); meta.setTextColor(0xFF75857F); meta.setPadding(dp(8), 0, dp(8), 0); card.addView(meta);
            card.setTag(product[1]);
            card.setContentDescription(product[6]);
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
            String category = String.valueOf(child.getContentDescription());
            boolean matchesCategory = "Tất cả".equals(selectedCategory)
                    || selectedCategory.equals(category)
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
        editingListing = false;
        Spinner condition = findViewById(R.id.spinnerCondition);
        if (condition != null) {
            String[] conditions = {"Tốt", "Rất tốt", "Như mới", "Có dấu hiệu sử dụng"};
            condition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, conditions));
        }
        bindClick(R.id.photoPickerArea, this::showPhotoSourcePicker);
        bindClick(R.id.btnSellBack, () -> showScreen(R.layout.activity_home));
        bindClick(R.id.btnSellNext, () -> showScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnPublishListing, () -> {
            EditText name = findViewById(R.id.edtProductName);
            if (name == null || name.getText().toString().trim().isEmpty()) {
                if (name != null) name.setError("Vui lòng nhập tên sản phẩm");
                return;
            }
            Toast.makeText(this, "Đã kiểm tra thông tin mẫu. Chưa đăng lên hệ thống.", Toast.LENGTH_LONG).show();
        });
    }

    private void showPhotoSourcePicker() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Thêm ảnh sản phẩm")
                .setItems(new String[]{"Chụp ảnh bằng camera", "Chọn từ thư viện"}, (dialog, which) -> {
                    if (which == 0) cameraPicker.launch(null);
                    else galleryPicker.launch("image/*");
                })
                .show();
    }

    private void showSelectedListingPhoto() {
        ImageView image = findViewById(R.id.imgSellPhoto);
        TextView placeholder = findViewById(R.id.btnChoosePhotos);
        TextView hint = findViewById(R.id.txtSelectedPhoto);
        if (image == null) return;
        image.setVisibility(View.VISIBLE);
        if (capturedListingPhoto != null) image.setImageBitmap(capturedListingPhoto);
        else if (selectedListingPhoto != null) image.setImageURI(selectedListingPhoto);
        if (placeholder != null) placeholder.setVisibility(View.GONE);
        if (hint != null) hint.setText("Đã chọn 1 ảnh · Chạm vào ảnh để thay đổi");
    }

    private void bindSellDetails() {
        EditText productName = findViewById(R.id.edtProductName);
        if (editingListing && productName != null) productName.setText(currentProductName);
        setSpinnerOptions(R.id.spinnerCategory, new String[]{"Áo khoác", "Áo thun", "Áo sơ mi", "Quần", "Váy", "Đầm", "Giày dép", "Túi xách", "Phụ kiện"});
        setSpinnerOptions(R.id.spinnerSize, new String[]{"XS", "S", "M", "L", "XL", "XXL", "Free size"});
        setSpinnerOptions(R.id.spinnerCondition, new String[]{"Mới có tag", "Như mới", "Tốt", "Đã qua sử dụng"});
        setSpinnerOptions(R.id.spinnerLocation, new String[]{"Quận 1, TP.HCM", "Quận 3, TP.HCM", "Bình Thạnh, TP.HCM", "Thủ Đức, TP.HCM", "Hà Nội", "Đà Nẵng", "Cần Thơ"});
        bindClick(R.id.btnSellDetailsBack, () -> showScreen(editingListing ? R.layout.activity_my_listings : R.layout.activity_sell));
        bindClick(R.id.btnSellDetailsPrevious, () -> showScreen(editingListing ? R.layout.activity_my_listings : R.layout.activity_sell));
        bindClick(R.id.btnSellDetailsNext, () -> showScreen(R.layout.activity_sell_type));
    }

    private void bindSellType() {
        RadioGroup listingType = findViewById(R.id.rgListingType);
        EditText price = findViewById(R.id.edtProductPrice);
        if (listingType != null && price != null) {
            listingType.setOnCheckedChangeListener((group, checkedId) -> {
                boolean selling = checkedId == R.id.radioSell;
                price.setEnabled(selling);
                price.setAlpha(selling ? 1f : 0.45f);
            });
        }
        bindClick(R.id.btnSellTypeBack, () -> showScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnSellTypePrevious, () -> showScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnSellTypeNext, () -> showScreen(R.layout.activity_sell_preview));
    }

    private void bindSellPreview() {
        ImageView previewImage = findViewById(R.id.imgPreviewPhoto);
        TextView previewPlaceholder = findViewById(R.id.txtPreviewPhoto);
        if (previewImage != null && (capturedListingPhoto != null || selectedListingPhoto != null)) {
            previewImage.setVisibility(View.VISIBLE);
            if (capturedListingPhoto != null) previewImage.setImageBitmap(capturedListingPhoto);
            else previewImage.setImageURI(selectedListingPhoto);
            if (previewPlaceholder != null) previewPlaceholder.setVisibility(View.GONE);
        }
        bindClick(R.id.btnSellPreviewBack, () -> showScreen(R.layout.activity_sell_type));
        bindClick(R.id.btnSellPreviewPrevious, () -> showScreen(R.layout.activity_sell_type));
        bindClick(R.id.btnSaveSellDraft, () -> Toast.makeText(this, "Đã lưu bản nháp trên thiết bị.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnPublishListing, () -> {
            editingListing = false;
            showScreen(R.layout.activity_sell_success);
        });
    }

    private void setSpinnerOptions(int viewId, String[] options) {
        Spinner spinner = findViewById(viewId);
        if (spinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    private void bindFreeRequest() {
        TextView productName = findViewById(R.id.txtFreeRequestProductName);
        if (productName != null && !currentProductName.isEmpty()) productName.setText(currentProductName);
        bindClick(R.id.btnBack, () -> showScreen(R.layout.activity_product_detail));
        bindClick(R.id.btnSubmitRequest, () -> showScreen(R.layout.activity_request_status));
    }

    private void bindMessages() {
        EditText search = findViewById(R.id.edtSearchMessage);
        int[] conversationIds = {R.id.conversation1, R.id.conversation2, R.id.conversation3};
        String[] conversationTags = {"buy lan chi ao khoac jacket", "sell minh tuan anh san pham", "buy thao vy cam on"};
        for (int i = 0; i < conversationIds.length; i++) findViewById(conversationIds[i]).setTag(conversationTags[i]);
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                messageSearchQuery = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                applyMessageFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        bindClick(R.id.btnSearchTop, () -> {
            if (search != null) {
                search.requestFocus();
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        bindClick(R.id.btnFilter, () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Lọc hội thoại")
                .setSingleChoiceItems(new String[]{"Tất cả", "Đang mua", "Đang bán"}, "all".equals(messageFilter) ? 0 : "buy".equals(messageFilter) ? 1 : 2, (dialog, which) -> {
                    messageFilter = which == 0 ? "all" : which == 1 ? "buy" : "sell";
                    dialog.dismiss();
                    updateMessageTabs(which);
                    applyMessageFilters();
                }).show());
        bindClick(R.id.tvTabAll, () -> setMessageFilter("all", 0));
        bindClick(R.id.tvTabBuying, () -> setMessageFilter("buy", 1));
        bindClick(R.id.tvTabSelling, () -> setMessageFilter("sell", 2));
        for (int id : conversationIds) bindClick(id, () -> {
            chatReturnScreen = R.layout.activity_messages;
            showScreen(R.layout.activity_chat);
        });
        updateMessageTabs("all".equals(messageFilter) ? 0 : "buy".equals(messageFilter) ? 1 : 2);
        applyMessageFilters();
    }

    private void setMessageFilter(String filter, int tab) {
        messageFilter = filter;
        updateMessageTabs(tab);
        applyMessageFilters();
    }

    private void updateMessageTabs(int selected) {
        int[] ids = {R.id.tvTabAll, R.id.tvTabBuying, R.id.tvTabSelling};
        for (int i = 0; i < ids.length; i++) {
            TextView tab = findViewById(ids[i]);
            tab.setTextColor(i == selected ? 0xFF111111 : 0xFF777777);
            tab.setTypeface(null, i == selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void applyMessageFilters() {
        int[] ids = {R.id.conversation1, R.id.conversation2, R.id.conversation3};
        for (int id : ids) {
            View row = findViewById(id);
            String tag = String.valueOf(row.getTag());
            boolean categoryMatch = "all".equals(messageFilter) || tag.startsWith(messageFilter + " ");
            boolean searchMatch = messageSearchQuery.isEmpty() || tag.contains(messageSearchQuery);
            row.setVisibility(categoryMatch && searchMatch ? View.VISIBLE : View.GONE);
        }
    }

    private void bindProfile() {
        android.content.SharedPreferences verification = getSharedPreferences("account_verification", MODE_PRIVATE);
        String status = verification.getString("status", "none");
        TextView title = findViewById(R.id.txtVerificationCardTitle);
        TextView subtitle = findViewById(R.id.txtVerificationCardSubtitle);
        TextView profileLocation = findViewById(R.id.txtProfileLocation);
        TextView profileAvatar = findViewById(R.id.imgProfileAvatar);
        if (profileAvatar != null && selectedProfilePhoto != null) {
            profileAvatar.setText("");
            profileAvatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), selectedProfilePhoto));
        }
        if ("pending".equals(status)) {
            title.setText("Đang chờ duyệt");
            subtitle.setText("Hồ sơ đã gửi · đang được xem xét");
            profileLocation.setText("Hồ sơ đang chờ duyệt · Quận 3");
        } else if ("approved".equals(status)) {
            title.setText("Thành viên đáng tin cậy");
            subtitle.setText("Tài khoản đã được xác minh");
            profileLocation.setText("✓  Hồ sơ đã xác minh · Quận 3");
        } else {
            title.setText("Cần xác minh");
            subtitle.setText("Xác minh danh tính để tăng độ tin cậy");
            profileLocation.setText("Chưa xác minh · Quận 3");
        }
        View verificationCard = findViewById(R.id.verificationCard);
        if (verificationCard != null) verificationCard.setOnClickListener(v -> {
            if ("none".equals(status)) showScreen(R.layout.activity_account_verification);
            else showScreen(R.layout.activity_verification_status);
        });
        bindClick(R.id.btnOpenVerification, () -> {
            if ("none".equals(status)) showScreen(R.layout.activity_account_verification);
            else showScreen(R.layout.activity_verification_status);
        });
        bindClick(R.id.btnEditProfile, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.btnMyListings, () -> showScreen(R.layout.activity_my_listings));
        bindClick(R.id.btnMyOrders, () -> showScreen(R.layout.activity_transactions));
        bindClick(R.id.btnMyCart, () -> showScreen(R.layout.activity_orders));
        bindClick(R.id.btnMyHistory, () -> showScreen(R.layout.activity_transaction_history));
        bindClick(R.id.btnFavorites, () -> showScreen(R.layout.activity_favorites));
        bindClick(R.id.btnAddresses, () -> showScreen(R.layout.activity_addresses));
        bindClick(R.id.btnSettingsMenu, () -> showScreen(R.layout.activity_settings));
        bindClick(R.id.btnSupport, () -> showScreen(R.layout.activity_support));
        bindClick(R.id.btnOpenPaymentInfo, () -> showScreen(R.layout.activity_payment_info));
        bindClick(R.id.btnNotifications, () -> showScreen(R.layout.activity_notifications));
        bindClick(R.id.btnSettings, () -> showScreen(R.layout.activity_settings));
        bindClick(R.id.btnLogout, () -> Toast.makeText(this, "Frontend hiện chưa tích hợp đăng nhập.", Toast.LENGTH_SHORT).show());
    }

    private void bindAccountVerification() {
        Spinner documentType = findViewById(R.id.spinnerIdentityDocument);
        String[] documentTypes = {"CCCD gắn chip", "CCCD mã vạch", "Hộ chiếu"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, documentTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        documentType.setAdapter(adapter);
        bindClick(R.id.btnVerificationBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnVerificationLater, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnIdentityFront, () -> showIdentityPicker("front"));
        bindClick(R.id.btnIdentityBack, () -> showIdentityPicker("back"));
        bindClick(R.id.btnIdentitySelfie, () -> showIdentityPicker("selfie"));
        bindClick(R.id.btnSubmitVerification, () -> {
            EditText name = findViewById(R.id.edtLegalName);
            EditText birthDate = findViewById(R.id.edtLegalBirthDate);
            EditText identityNumber = findViewById(R.id.edtIdentityNumber);
            CheckBox accurate = findViewById(R.id.checkIdentityAccurate);
            CheckBox privacy = findViewById(R.id.checkIdentityPrivacy);
            if (name.getText().toString().trim().isEmpty() || birthDate.getText().toString().trim().isEmpty()
                    || identityNumber.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đủ thông tin trên giấy tờ.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!accurate.isChecked() || !privacy.isChecked()) {
                Toast.makeText(this, "Vui lòng xác nhận thông tin và đồng ý xử lý dữ liệu.", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences("account_verification", MODE_PRIVATE).edit()
                    .putString("status", "pending")
                    .putString("legal_name", name.getText().toString().trim())
                    .putString("document_type", documentType.getSelectedItem().toString())
                    .apply();
            showScreen(R.layout.activity_verification_status);
        });
    }

    private void showIdentityPicker(String slot) {
        identityPhotoSlot = slot;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Thêm ảnh xác minh")
                .setItems(new String[]{"Chụp ảnh bằng camera", "Chọn từ thư viện"}, (dialog, which) -> {
                    if (which == 0) identityCameraPicker.launch(null);
                    else identityGalleryPicker.launch("image/*");
                })
                .show();
    }

    private void showIdentityPhotoSelected(Uri uri, Bitmap bitmap) {
        int id = "front".equals(identityPhotoSlot) ? R.id.btnIdentityFront
                : "back".equals(identityPhotoSlot) ? R.id.btnIdentityBack : R.id.btnIdentitySelfie;
        Button button = findViewById(id);
        if (button != null) {
            String label = "front".equals(identityPhotoSlot) ? "Ảnh mặt trước CCCD"
                    : "back".equals(identityPhotoSlot) ? "Ảnh mặt sau CCCD" : "Ảnh chân dung cầm giấy tờ";
            button.setText("✓\nĐã chọn " + label);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFDDECE1));
            try {
                android.graphics.drawable.Drawable preview = bitmap != null
                        ? new android.graphics.drawable.BitmapDrawable(getResources(), bitmap)
                        : android.graphics.drawable.Drawable.createFromStream(getContentResolver().openInputStream(uri), null);
                if (preview != null) {
                    int size = dp(38);
                    preview.setBounds(0, 0, size, size);
                    button.setCompoundDrawables(null, preview, null, null);
                }
            } catch (Exception ignored) {
                // Selection still succeeds when the provider does not expose a preview stream.
            }
        }
    }

    private void bindVerificationStatus() {
        android.content.SharedPreferences verification = getSharedPreferences("account_verification", MODE_PRIVATE);
        boolean approved = "approved".equals(verification.getString("status", "pending"));
        ((TextView) findViewById(R.id.txtVerificationStatusTitle)).setText(approved ? "Tài khoản đã được xác minh" : "Hồ sơ đang được xem xét");
        ((TextView) findViewById(R.id.txtVerificationStatusDescription)).setText(approved
                ? "Danh tính của bạn đã được xác minh. Thẻ thành viên đáng tin cậy đã được cập nhật."
                : "Chúng tôi sẽ thông báo kết quả trong mục thông báo. Thời gian xử lý dự kiến: 1–2 ngày làm việc.");
        ((TextView) findViewById(R.id.txtVerificationStatusValue)).setText(approved ? "Đã duyệt" : "Đang chờ duyệt");
        ((TextView) findViewById(R.id.txtVerificationLegalName)).setText(verification.getString("legal_name", "Nguyễn Minh Anh"));
        ((TextView) findViewById(R.id.txtVerificationDocumentType)).setText(verification.getString("document_type", "CCCD gắn chip"));
        bindClick(R.id.btnVerificationStatusBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnVerificationStatusProfile, () -> showScreen(R.layout.activity_profile));
    }

    private void openProduct(String name, String emoji, String price) {
        productReturnScreen = currentScreen;
        currentProductName = name == null ? "" : name;
        currentProductEmoji = emoji == null ? "👕" : emoji;
        String normalizedPrice = price == null ? "" : price.toLowerCase();
        currentProductIsFree = normalizedPrice.contains("free") || normalizedPrice.contains("miễn phí")
                || normalizedPrice.contains("0đ") || normalizedPrice.contains("0 đ");
        currentProductPrice = price == null ? "" : price;
        showScreen(R.layout.activity_product_detail);
    }

    private void bindProductDetail() {
        TextView productName = findViewById(R.id.txtProductName);
        TextView productEmoji = findViewById(R.id.txtProductEmoji);
        TextView productPrice = findViewById(R.id.txtProductPrice);
        if (productName != null && !currentProductName.isEmpty()) productName.setText(currentProductName);
        if (productEmoji != null) productEmoji.setText(currentProductEmoji);
        if (productPrice != null && !currentProductPrice.isEmpty()) productPrice.setText(currentProductIsFree ? "FREE" : currentProductPrice);
        TextView category = findViewById(R.id.txtProductCategory);
        if (category != null) category.setText(productCategoryFor(currentProductName));
        TextView size = findViewById(R.id.txtProductSize);
        if (size != null) size.setText(currentProductName.toLowerCase().contains("linen") ? "S" : "M");
        TextView location = findViewById(R.id.txtProductLocation);
        if (location != null) location.setText(currentProductName.toLowerCase().contains("linen") ? "Thủ Đức" : "Quận 3");
        TextView seller = findViewById(R.id.txtProductSeller);
        if (seller != null) seller.setText(currentProductIsFree ? "Người tặng · Lan Chi  ✓" : "Lan Chi  ✓");
        bindClick(R.id.btnProductBack, () -> showScreen(productReturnScreen));
        bindClick(R.id.btnProductLike, () -> {
            TextView like = findViewById(R.id.btnProductLike);
            if (like != null) like.setText("♥".contentEquals(like.getText()) ? "♡" : "♥");
        });
        bindClick(R.id.btnProductShare, () -> Toast.makeText(this, "Đã sao chép liên kết sản phẩm.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnProductChat, () -> {
            chatReturnScreen = R.layout.activity_product_detail;
            showScreen(R.layout.activity_chat);
        });
        bindClick(R.id.btnAddToCart, this::openCart);
        bindClick(R.id.btnBuyNow, () -> {
            checkoutReturnScreen = R.layout.activity_product_detail;
            showScreen(R.layout.activity_checkout);
        });
        bindClick(R.id.btnRequestFree, () -> showScreen(R.layout.activity_requestfree));
        bindClick(R.id.btnViewSellerProfile, () -> Toast.makeText(this, "Hồ sơ người đăng.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnProductReadMore, () -> {
            TextView description = findViewById(R.id.txtProductDescription);
            TextView toggle = findViewById(R.id.btnProductReadMore);
            if (description != null && toggle != null) {
                boolean expanded = description.getMaxLines() <= 4;
                description.setMaxLines(expanded ? Integer.MAX_VALUE : 4);
                toggle.setText(expanded ? "Thu gọn" : "Xem thêm");
            }
        });
        applyProductKindUi();
    }

    private void applyProductKindUi() {
        TextView badge = findViewById(R.id.txtProductTypeBadge);
        Button chat = findViewById(R.id.btnProductChat);
        Button addToCart = findViewById(R.id.btnAddToCart);
        Button buyNow = findViewById(R.id.btnBuyNow);
        Button requestFree = findViewById(R.id.btnRequestFree);
        View freeNotice = findViewById(R.id.freeProductNotice);
        if (badge == null || chat == null || addToCart == null || buyNow == null || requestFree == null) return;
        badge.setText(currentProductIsFree ? "TẶNG MIỄN PHÍ" : "ĐANG BÁN");
        badge.setTextColor(currentProductIsFree ? 0xFF23795C : 0xFFB95242);
        badge.setBackgroundResource(currentProductIsFree ? R.drawable.bg_green_light_card : R.drawable.bg_badge_red);
        addToCart.setVisibility(currentProductIsFree ? View.GONE : View.VISIBLE);
        buyNow.setVisibility(currentProductIsFree ? View.GONE : View.VISIBLE);
        requestFree.setVisibility(currentProductIsFree ? View.VISIBLE : View.GONE);
        chat.setText(currentProductIsFree ? "Chat với người tặng" : "□");
        chat.setTextSize(currentProductIsFree ? 9 : 17);
        LinearLayout.LayoutParams chatParams = (LinearLayout.LayoutParams) chat.getLayoutParams();
        chatParams.width = currentProductIsFree ? 0 : dp(42);
        chatParams.weight = currentProductIsFree ? 1 : 0;
        chat.setLayoutParams(chatParams);
        LinearLayout.LayoutParams requestParams = (LinearLayout.LayoutParams) requestFree.getLayoutParams();
        requestParams.weight = currentProductIsFree ? 1 : 0;
        requestFree.setLayoutParams(requestParams);
        if (freeNotice != null) freeNotice.setVisibility(currentProductIsFree ? View.VISIBLE : View.GONE);
    }

    private String productCategoryFor(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("jeans") || lower.contains("quần")) return "Quần";
        if (lower.contains("váy") || lower.contains("đầm")) return "Váy";
        if (lower.contains("giày") || lower.contains("sneaker")) return "Giày dép";
        if (lower.contains("túi")) return "Túi xách";
        return "Áo khoác";
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
