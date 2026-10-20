package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import android.os.CancellationSignal;
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
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;
import vn.edu.ueh.thudinh.fashion2hand.admin.AdminLoginActivity;
import vn.edu.ueh.thudinh.fashion2hand.repository.AuthRepository;
import vn.edu.ueh.thudinh.fashion2hand.repository.AdminRepository;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberOneRepository;

// --- Bổ sung các import Firebase còn thiếu ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main entry point for Fashion2hand's Firebase-backed user flows. */
public class MainActivity extends AppCompatActivity {
    private final AuthRepository authRepository = new AuthRepository();
    private final MemberTwoRepository memberTwoRepository = MemberTwoRepository.getInstance();
    private final MemberOneRepository memberOneRepository = MemberOneRepository.getInstance();
    private final ArrayList<ListenerRegistration> screenListeners = new ArrayList<>();
    private final ArrayList<DocumentSnapshot> activePosts = new ArrayList<>();
    private final Set<String> favoritePostIds = new HashSet<>();
    private final ArrayList<DocumentSnapshot> activeCart = new ArrayList<>();
    private final ArrayList<DocumentSnapshot> activeOrders = new ArrayList<>();
    private final ArrayList<DocumentSnapshot> activeChats = new ArrayList<>();
    private final ArrayList<DocumentSnapshot> myListings = new ArrayList<>();
    private DocumentSnapshot activeOrder;
    private String currentPostId = "";
    private String currentSellerId = "";
    private String currentChatId = "";
    private String currentPeerId = "";
    private String currentOrderId = "";
    private String currentFreeRequestId = "";
    private Map<String, Object> currentProductData = new HashMap<>();
    private final Set<String> selectedCartPostIds = new HashSet<>();
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
    private int currentProductImageIndex;
    private Bitmap selectedProfilePhoto;
    private Uri selectedProfilePhotoUri;
    private String selectedCategory = "Tất cả";
    private Uri selectedListingPhoto;
    private Bitmap capturedListingPhoto;
    private final ArrayList<Object> selectedListingPhotos = new ArrayList<>();
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
    private String editingPostId = "";
    private Map<String, Object> listingDraft = new HashMap<>();
    private String notificationFilter = "all";
    private boolean hideReadNotifications;
    private int reportEvidenceCount;
    private final java.util.ArrayList<Object> reportEvidenceItems = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Kết nối ứng dụng Android với bộ giả lập (Emulator) chạy trên máy tính
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080);
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
        FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199);
        super.onCreate(savedInstanceState);

        galleryPicker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                int remaining = 8 - selectedListingPhotos.size();
                for (int i = 0; i < Math.min(remaining, uris.size()); i++) selectedListingPhotos.add(uris.get(i));
                updateListingPhotoSelection();
                showSelectedListingPhoto();
                if (uris.size() > remaining) Toast.makeText(this, "Mỗi tin đăng tối đa 8 ảnh.", Toast.LENGTH_SHORT).show();
            }
        });
        cameraPicker = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null && selectedListingPhotos.size() < 8) {
                selectedListingPhotos.add(bitmap);
                updateListingPhotoSelection();
                showSelectedListingPhoto();
            } else if (bitmap != null) Toast.makeText(this, "Mỗi tin đăng tối đa 8 ảnh.", Toast.LENGTH_SHORT).show();
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
            if (currentScreen == R.layout.activity_splash) {
                showScreen(authRepository.getCurrentUser() == null ? R.layout.activity_login : R.layout.activity_home);
            }
        }, 1200);

        // Đăng ký bộ xử lý nút Back theo chuẩn AndroidX mới
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == R.layout.activity_home || currentScreen == R.layout.activity_login) {
                    finish();
                } else {
                    showScreen(R.layout.activity_home);
                }
            }
        });
    }

    private void showScreen(int layoutId) {
        clearScreenListeners();
        currentScreen = layoutId;
        setContentView(layoutId);
        applySystemBarInsets();
        if (layoutId == R.layout.activity_login) bindLogin();
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

    private void addScreenListener(ListenerRegistration listener) {
        if (listener != null) screenListeners.add(listener);
    }

    private void clearScreenListeners() {
        for (ListenerRegistration listener : screenListeners) listener.remove();
        screenListeners.clear();
    }

    private String currentUid() {
        return authRepository.getCurrentUid();
    }

    private boolean requireSignedIn() {
        if (currentUid() != null) return true;
        Toast.makeText(this, "Vui lòng đăng nhập để tiếp tục.", Toast.LENGTH_SHORT).show();
        showScreen(R.layout.activity_login);
        return false;
    }

    private void showFirebaseError(Exception error) {
        String message = error == null || error.getLocalizedMessage() == null
                ? "Thao tác chưa thành công. Vui lòng thử lại."
                : error.getLocalizedMessage();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void applySystemBarInsets() {
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null || root.getChildCount() == 0) return;
        View content = root.getChildAt(0);
        int initialLeft = content.getPaddingLeft();
        int initialTop = content.getPaddingTop();
        int initialRight = content.getPaddingRight();
        int initialBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom
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

            if (AdminRepository.getInstance().isAdminEmail(emailValue)) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                return;
            }

            authRepository.signInWithEmail(emailValue, passwordValue)
                    .addOnSuccessListener(result -> ensureUserProfile(result.getUser(), "", () -> showScreen(R.layout.activity_home)))
                    .addOnFailureListener(this::showFirebaseError);
        });

        bindClick(R.id.btnOpenRegister, () -> showScreen(R.layout.activity_register));
        bindClick(R.id.btnOpenAdminLogin, () -> startActivity(new Intent(this, AdminLoginActivity.class)));
        bindClick(R.id.btnGoogleLogin, this::startGoogleSignIn);
        bindClick(R.id.btnForgotPassword, () -> showScreen(R.layout.activity_forgot_password));
    }

    private void startGoogleSignIn() {
        int clientIdResource = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (clientIdResource == 0) {
            Toast.makeText(this, "Tải google-services.json mới sau khi bật Google trong Firebase rồi thay file trong app/.", Toast.LENGTH_LONG).show();
            return;
        }
        String serverClientId = getString(clientIdResource);
        GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
        CredentialManager credentialManager = CredentialManager.create(this);
        credentialManager.getCredentialAsync(this, request, new CancellationSignal(), getMainExecutor(),
                new CredentialManagerCallback<androidx.credentials.GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(androidx.credentials.GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            Toast.makeText(MainActivity.this, "Không nhận được thông tin tài khoản Google hợp lệ.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                            com.google.firebase.auth.AuthCredential firebaseCredential = com.google.firebase.auth.GoogleAuthProvider
                                    .getCredential(googleCredential.getIdToken(), null);
                            authRepository.signInWithGoogleCredential(firebaseCredential, new AuthRepository.AuthCallback() {
                                @Override public void onSuccess(FirebaseUser user) {
                                    ensureUserProfile(user, googleCredential.getDisplayName(), () -> showScreen(R.layout.activity_home));
                                }
                                @Override public void onFailure(String message) {
                                    Toast.makeText(MainActivity.this, message == null ? "Đăng nhập Google thất bại." : message, Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (GoogleIdTokenParsingException exception) {
                            showFirebaseError(exception);
                        }
                    }
                    @Override public void onError(GetCredentialException exception) {
                        if (isFinishing()) return;
                        String message = exception.getMessage();
                        Toast.makeText(MainActivity.this, message == null ? "Không thể đăng nhập bằng Google." : message, Toast.LENGTH_LONG).show();
                    }
                });
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
            authRepository.createAccountWithEmail(email.getText().toString().trim(), password.getText().toString())
                    .addOnSuccessListener(result -> ensureUserProfile(result.getUser(), name.getText().toString().trim(),
                            () -> showScreen(R.layout.activity_home)))
                    .addOnFailureListener(this::showFirebaseError);
        });
    }

    private void ensureUserProfile(FirebaseUser user, String displayName, Runnable onReady) {
        if (user == null) {
            Toast.makeText(this, "Không lấy được tài khoản Firebase.", Toast.LENGTH_LONG).show();
            return;
        }
        com.google.firebase.firestore.DocumentReference ref = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                onReady.run();
                return;
            }
            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", user.getUid());
            profile.put("name", displayName == null || displayName.trim().isEmpty() ? user.getDisplayName() : displayName.trim());
            profile.put("email", user.getEmail());
            profile.put("role", "USER");
            profile.put("accountStatus", "ACTIVE");
            profile.put("createdAt", FieldValue.serverTimestamp());
            profile.put("updatedAt", FieldValue.serverTimestamp());
            ref.set(profile).addOnSuccessListener(unused -> onReady.run()).addOnFailureListener(this::showFirebaseError);
        }).addOnFailureListener(this::showFirebaseError);
    }

    private void bindForgotPassword() {
        EditText email = findViewById(R.id.edtRecoveryEmail);
        if (!otpTarget.isEmpty() && "password_reset".equals(otpFlow)) email.setText(otpTarget);
        bindClick(R.id.btnForgotBack, () -> showScreen(R.layout.activity_login));
        bindClick(R.id.btnRecoveryContinue, () -> {
            String value = email.getText().toString().trim();
            if (!value.contains("@") || !value.contains(".")) { email.setError("Nhập email hợp lệ"); return; }
            authRepository.sendPasswordReset(value)
                    .addOnSuccessListener(unused -> {
                        otpTarget = value;
                        showScreen(R.layout.activity_password_reset_success);
                    })
                    .addOnFailureListener(this::showFirebaseError);
        });
    }

    private void bindOtpVerification() {
        TextView title = findViewById(R.id.txtOtpTitle);
        TextView descView = findViewById(R.id.txtOtpDescription);
        TextView target = findViewById(R.id.txtOtpTarget);
        if ("email_change".equals(otpFlow)) {
            title.setText("Xác nhận email mới");
            descView.setText("Nhập mã xác thực được gửi đến email mới:");
        } else if ("phone_verify".equals(otpFlow) || "phone_change".equals(otpFlow)) {
            title.setText("Xác minh số điện thoại");
            descView.setText("Nhập mã OTP được gửi đến số điện thoại:");
        } else {
            title.setText("Nhập mã xác thực");
            descView.setText("Nhập mã xác thực được gửi đến email:");
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
        listenForActivePosts();
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
        listenForActivePosts();
        applyExploreFilter(selectedCategory);
    }

    private void listenForActivePosts() {
        addScreenListener(memberTwoRepository.listenActivePosts((snapshots, error) -> {
            if (currentScreen != R.layout.activity_home && currentScreen != R.layout.activity_explore) return;
            if (error != null) { showFirebaseError(error); return; }
            activePosts.clear();
            if (snapshots != null) activePosts.addAll(snapshots.getDocuments());
            if (currentScreen == R.layout.activity_home) renderHomeProducts();
            else renderExploreProducts();
        }));
        String uid = currentUid();
        if (uid != null) addScreenListener(memberOneRepository.listenFavorites(uid, (snapshots, error) -> {
            if (error != null) return;
            favoritePostIds.clear();
            if (snapshots != null) for (DocumentSnapshot favorite : snapshots.getDocuments()) favoritePostIds.add(favorite.getId());
            if (currentScreen == R.layout.activity_home) renderHomeProducts();
            else if (currentScreen == R.layout.activity_explore) renderExploreProducts();
        }));
    }

    private void renderHomeProducts() {
        GridLayout grid = findViewById(R.id.productGrid);
        if (grid == null) return;
        grid.removeAllViews();
        int added = 0;
        for (DocumentSnapshot post : activePosts) {
            if (added++ >= 4) break;
            grid.addView(createProductCard(post, false));
        }
        if (added == 0) addEmptyProductsMessage(grid, "Chưa có tin đăng đang hoạt động.");
    }

    private void renderExploreProducts() {
        GridLayout grid = findViewById(R.id.exploreProductGrid);
        if (grid == null) return;
        grid.removeAllViews();
        for (DocumentSnapshot post : activePosts) grid.addView(createProductCard(post, true));
        if (activePosts.isEmpty()) addEmptyProductsMessage(grid, "Chưa có tin đăng đang hoạt động.");
        applyExploreFilter(selectedCategory);
    }

    private void addEmptyProductsMessage(GridLayout grid, String message) {
        TextView emptyView = new TextView(this);
        emptyView.setText(message); emptyView.setTextColor(0xFF75857F); emptyView.setTextSize(12); emptyView.setGravity(17);
        emptyView.setPadding(dp(20), dp(28), dp(20), dp(28));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = -1; params.columnSpec = GridLayout.spec(0, 2);
        grid.addView(emptyView, params);
    }

    private View createProductCard(DocumentSnapshot post, boolean explore) {
        Map<String, Object> data = post.getData() == null ? new HashMap<>() : post.getData();
        String name = textValue(data, "title", "Sản phẩm");
        String category = textValue(data, "categoryName", textValue(data, "category", ""));
        String type = textValue(data, "type", textValue(data, "listingType", ""));
        boolean free = "FREE".equalsIgnoreCase(type);
        long priceValue = numberValue(data.get("price"));
        String price = free ? "Miễn phí · 0đ" : String.format(java.util.Locale.getDefault(), "%,d đ", priceValue);
        String emojiValue = emojiFor(name);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(0, 0, 0, dp(8));
        card.setBackgroundResource(R.drawable.bg_white_card_rounded); card.setClipToOutline(true);
        if (explore) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(0, 0, dp(8), dp(12));
            card.setLayoutParams(params);
        } else {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(0, 0, dp(6), dp(12));
            card.setLayoutParams(params);
        }
        FrameLayout photo = new FrameLayout(this);
        photo.setBackgroundColor(0xFFE9E5DC);
        ImageView remotePhoto = new ImageView(this); remotePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.addView(remotePhoto, new FrameLayout.LayoutParams(-1, dp(explore ? 142 : 125)));
        TextView emoji = new TextView(this); emoji.setText(emojiValue); emoji.setTextSize(44); emoji.setGravity(17);
        photo.addView(emoji, new FrameLayout.LayoutParams(-1, dp(explore ? 142 : 125)));
        loadPostPhoto(data, 0, remotePhoto, emoji);
        TextView badge = new TextView(this); badge.setText(free ? "FREE · 0đ" : "FOR SALE"); badge.setTextSize(8);
        badge.setTextColor(0xFFFFFFFF); badge.setTypeface(null, android.graphics.Typeface.BOLD); badge.setPadding(dp(7), dp(4), dp(7), dp(4));
        badge.setBackgroundResource(R.drawable.bg_button_primary);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.START);
        badgeParams.setMargins(dp(7), 0, 0, dp(7)); photo.addView(badge, badgeParams);
        TextView heart = new TextView(this); heart.setText(favoritePostIds.contains(post.getId()) ? "♥" : "♡"); heart.setTextSize(20);
        heart.setTextColor(0xFFF0785D); heart.setGravity(17); heart.setBackgroundResource(R.drawable.bg_white_card_rounded);
        FrameLayout.LayoutParams heartParams = new FrameLayout.LayoutParams(dp(32), dp(32), android.view.Gravity.TOP | android.view.Gravity.END);
        heartParams.setMargins(0, dp(7), dp(7), 0); photo.addView(heart, heartParams);
        heart.setOnClickListener(v -> {
            if (!requireSignedIn()) return;
            boolean save = !favoritePostIds.contains(post.getId());
            memberOneRepository.setFavorite(currentUid(), post.getId(), save)
                    .addOnSuccessListener(unused -> {
                        if (save) favoritePostIds.add(post.getId()); else favoritePostIds.remove(post.getId());
                        heart.setText(save ? "♥" : "♡");
                    }).addOnFailureListener(this::showFirebaseError);
        });
        card.addView(photo);
        TextView title = new TextView(this); title.setText(name); title.setTextSize(11); title.setMaxLines(2);
        title.setTextColor(0xFF173B32); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setPadding(dp(8), dp(8), dp(8), dp(2)); card.addView(title);
        TextView amount = new TextView(this); amount.setText(free ? "0 đ" : price); amount.setTextSize(10); amount.setTextColor(0xFFE66F55);
        amount.setPadding(dp(8), dp(2), dp(8), dp(2)); card.addView(amount);
        TextView meta = new TextView(this); meta.setText(textValue(data, "condition", "") + (textValue(data, "size", "").isEmpty() ? "" : " · " + textValue(data, "size", "")));
        meta.setTextSize(9); meta.setTextColor(0xFF75857F); meta.setPadding(dp(8), dp(2), dp(8), 0); card.addView(meta);
        card.setTag(name); card.setContentDescription(category);
        card.setOnClickListener(v -> openProduct(post));
        return card;
    }

    private String textValue(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private void loadPostPhoto(Map<String, Object> data, int index, ImageView target, View fallback) {
        Object raw = data.get("imageUrls");
        if (!(raw instanceof List) || ((List<?>) raw).size() <= index) return;
        Object url = ((List<?>) raw).get(index);
        if (!(url instanceof String) || ((String) url).isEmpty()) return;
        target.setVisibility(View.INVISIBLE); fallback.setVisibility(View.VISIBLE);
        FirebaseStorage.getInstance().getReferenceFromUrl((String) url)
                .getBytes(10 * 1024 * 1024).addOnSuccessListener(bytes -> {
                    if (!target.isAttachedToWindow()) return;
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) { target.setImageBitmap(bitmap); target.setVisibility(View.VISIBLE); fallback.setVisibility(View.GONE); }
                });
    }

    private long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String emojiFor(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("quần") || lower.contains("jeans")) return "👖";
        if (lower.contains("váy") || lower.contains("đầm")) return "👗";
        if (lower.contains("giày") || lower.contains("sneaker")) return "👟";
        if (lower.contains("túi")) return "👜";
        if (lower.contains("áo khoác") || lower.contains("jacket")) return "🧥";
        return "👕";
    }

    private void bindFavorites() {
        bindClick(R.id.btnFavoritesBack, () -> showScreen(R.layout.activity_profile));
        RadioGroup filters = findViewById(R.id.favoriteFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderFavoriteProducts(checkedId));
        if (requireSignedIn()) {
            addScreenListener(memberTwoRepository.listenActivePosts((snapshot, error) -> {
                if (error != null) { showFirebaseError(error); return; }
                activePosts.clear(); if (snapshot != null) activePosts.addAll(snapshot.getDocuments()); renderFavoriteProducts(R.id.filterFavoritesAll);
            }));
            addScreenListener(memberOneRepository.listenFavorites(currentUid(), (snapshot, error) -> {
                if (error != null) { showFirebaseError(error); return; }
                favoritePostIds.clear(); if (snapshot != null) for (DocumentSnapshot favorite : snapshot.getDocuments()) favoritePostIds.add(favorite.getId());
                renderFavoriteProducts(R.id.filterFavoritesAll);
            }));
        }
        renderFavoriteProducts(R.id.filterFavoritesAll);
    }

    private void renderFavoriteProducts(int filterId) {
        GridLayout grid = findViewById(R.id.favoriteProductGrid);
        if (grid == null) return;
        grid.removeAllViews();
        int count = 0;
        for (DocumentSnapshot post : activePosts) {
            if (!favoritePostIds.contains(post.getId())) continue;
            String status = textValue(post.getData(), "status", "ACTIVE");
            if (filterId == R.id.filterFavoritesAvailable && !"ACTIVE".equalsIgnoreCase(status)) continue;
            if (filterId == R.id.filterFavoritesUnavailable && "ACTIVE".equalsIgnoreCase(status)) continue;
            grid.addView(createProductCard(post, false)); count++;
        }
        TextView empty = findViewById(R.id.txtFavoritesEmpty);
        if (empty != null) empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void bindMyListings() {
        bindClick(R.id.btnListingsBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnCreateListing, () -> {
            editingListing = false; editingPostId = ""; listingDraft.clear();
            selectedListingPhoto = null; capturedListingPhoto = null; selectedListingPhotos.clear();
            showScreen(R.layout.activity_sell);
        });
        RadioGroup filters = findViewById(R.id.listingFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderFirebaseMyListings(checkedId));
        if (requireSignedIn()) addScreenListener(memberOneRepository.listenMyListings(currentUid(), (snapshot, error) -> {
            if (error != null) { showFirebaseError(error); return; }
            myListings.clear(); if (snapshot != null) myListings.addAll(snapshot.getDocuments());
            renderFirebaseMyListings(filters == null ? R.id.filterListingAll : filters.getCheckedRadioButtonId());
        }));
        else renderFirebaseMyListings(R.id.filterListingAll);
    }

    private void renderFirebaseMyListings(int filterId) {
        LinearLayout container = findViewById(R.id.myListingsContainer);
        if (container == null) return;
        container.removeAllViews();
        int shown = 0;
        for (DocumentSnapshot post : myListings) {
            String status = textValue(post.getData(), "status", "DRAFT");
            boolean matches = filterId == R.id.filterListingAll
                    || filterId == R.id.filterListingSelling && "ACTIVE".equals(status)
                    || filterId == R.id.filterListingPending && ("PENDING_REVIEW".equals(status) || "DRAFT".equals(status))
                    || filterId == R.id.filterListingSold && "SOLD".equals(status)
                    || filterId == R.id.filterListingGift && "GIVEN".equals(status);
            if (!matches) continue;
            shown++;
            String titleText = textValue(post.getData(), "title", "Sản phẩm");
            boolean free = "FREE".equalsIgnoreCase(textValue(post.getData(), "type", "SALE"));
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(8)); card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.bottomMargin = dp(10); card.setLayoutParams(cardParams);
            TextView title = new TextView(this); title.setText(titleText); title.setTextColor(0xFF173B32); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD); card.addView(title);
            TextView meta = new TextView(this);
            meta.setText((free ? "Miễn phí" : String.format(java.util.Locale.getDefault(), "%,d đ", numberValue(post.get("price")))) + " · " + status);
            meta.setTextColor(0xFF75857F); meta.setTextSize(11); meta.setPadding(0, dp(5), 0, dp(5)); card.addView(meta);
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView edit = listingAction("Chỉnh sửa"); TextView view = listingAction("Xem"); TextView hide = listingAction("ACTIVE".equals(status) ? "Ẩn tin" : "Hiện tin"); TextView delete = listingAction("Xóa");
            actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1)); actions.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(hide, new LinearLayout.LayoutParams(0, -2, 1)); actions.addView(delete, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(actions);
            edit.setOnClickListener(v -> {
                editingListing = true; editingPostId = post.getId();
                listingDraft = new HashMap<>(post.getData() == null ? new HashMap<>() : post.getData());
                currentProductName = titleText; selectedListingPhoto = null; capturedListingPhoto = null; selectedListingPhotos.clear();
                showScreen(R.layout.activity_sell_details);
            });
            view.setOnClickListener(v -> openProduct(post));
            hide.setOnClickListener(v -> memberOneRepository.setListingStatus(post.getId(), "ACTIVE".equals(status) ? "HIDDEN" : "ACTIVE")
                    .addOnFailureListener(this::showFirebaseError));
            delete.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Ẩn tin đăng?")
                    .setMessage("Tin sẽ được ẩn khỏi trang khám phá và vẫn lưu trong lịch sử của bạn.")
                    .setNegativeButton("Hủy", null).setPositiveButton("Ẩn tin", (d, w) -> memberOneRepository.setListingStatus(post.getId(), "HIDDEN").addOnFailureListener(this::showFirebaseError)).show());
            container.addView(card);
        }
        if (shown == 0) {
            TextView empty = new TextView(this); empty.setText("Chưa có tin đăng trong mục này."); empty.setTextColor(0xFF75857F); empty.setGravity(17); empty.setPadding(dp(16), dp(28), dp(16), dp(28)); container.addView(empty);
        }
    }

    private TextView listingAction(String label) {
        TextView action = new TextView(this); action.setText(label); action.setTextColor(0xFF14513E); action.setTextSize(10); action.setGravity(17); action.setPadding(dp(2), dp(9), dp(2), dp(8)); return action;
    }

    private void bindOrders() {
        bindClick(R.id.btnOrdersBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnTransactionHistory, () -> showScreen(R.layout.activity_transaction_history));
        LinearLayout orderContainer = findViewById(R.id.orderListContainer);
        TextView emptyOrders = findViewById(R.id.txtOrdersEmpty);
        if (orderContainer != null) {
            for (int id : new int[]{R.id.orderCard1, R.id.orderCard2, R.id.orderCard3, R.id.orderCard4, R.id.orderCard5}) {
                View sample = orderContainer.findViewById(id);
                if (sample != null) sample.setVisibility(View.GONE);
            }
        }
        if (requireSignedIn()) {
            ListenerRegistration ordersListener = "seller".equals(orderRoleFilter)
                    ? memberTwoRepository.listenOrdersAsSeller(currentUid(), (snapshot, error) -> onOrdersChanged(snapshot, error))
                    : memberTwoRepository.listenOrdersAsBuyer(currentUid(), (snapshot, error) -> onOrdersChanged(snapshot, error));
            addScreenListener(ordersListener);
        } else if (emptyOrders != null) emptyOrders.setVisibility(View.VISIBLE);
        bindClick(R.id.tabBuyer, () -> setOrderRoleFilter("buyer"));
        bindClick(R.id.tabSeller, () -> setOrderRoleFilter("seller"));
        int[] statusIds = {R.id.filterOrdersAll, R.id.filterOrdersUnpaid, R.id.filterOrdersConfirm,
                R.id.filterOrdersPrepare, R.id.filterOrdersShipping, R.id.filterOrdersDone};
        String[] statuses = {"all", "unpaid", "confirm", "prepare", "shipping", "done"};
        for (int i = 0; i < statusIds.length; i++) {
            final String status = statuses[i]; // Khai báo final rõ ràng
            final int statusId = statusIds[i];
            bindClick(statusId, () -> setOrderStatusFilter(status));
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

    private void onOrdersChanged(QuerySnapshot snapshot, FirebaseFirestoreException error) {
        if (error != null) { showFirebaseError(error); return; }
        activeOrders.clear();
        if (snapshot != null) activeOrders.addAll(snapshot.getDocuments());
        renderOrders();
    }

    private void renderOrders() {
        LinearLayout container = findViewById(R.id.orderListContainer);
        TextView empty = findViewById(R.id.txtOrdersEmpty);
        if (container == null || empty == null) return;
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof String && ((String) tag).startsWith("firebase_order|")) container.removeViewAt(i);
        }
        int shown = 0;
        for (DocumentSnapshot order : activeOrders) {
            Map<String, Object> data = order.getData() == null ? new HashMap<>() : order.getData();
            String status = textValue(data, "orderStatus", textValue(data, "status", "PENDING_PAYMENT"));
            String statusGroup = orderStatusGroup(status);
            Map<String, Object> postSnapshot = data.get("postSnapshot") instanceof Map ? (Map<String, Object>) data.get("postSnapshot") : new HashMap<>();
            String title = textValue(postSnapshot, "title", textValue(data, "postTitle", "Đơn hàng Fashion2hand"));
            long total = numberValue(data.get("price")) + numberValue(data.get("shippingFee"));
            String normalized = (order.getId() + " " + title + " " + status).toLowerCase(java.util.Locale.ROOT);
            if (!"all".equals(orderStatusFilter) && !orderStatusFilter.equals(statusGroup)) continue;
            if (!orderSearchQuery.isEmpty() && !normalized.contains(orderSearchQuery)) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(12), dp(13), dp(12));
            card.setBackgroundResource(R.drawable.bg_white_card_rounded);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            card.setLayoutParams(params);
            card.setTag("firebase_order|" + order.getId());
            TextView titleView = new TextView(this);
            titleView.setText("#" + order.getId() + "  ·  " + orderStatusLabel(status));
            titleView.setTextColor(0xFF173B32); titleView.setTextSize(11); titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView productView = new TextView(this);
            productView.setText(title + "  ·  " + String.format(java.util.Locale.getDefault(), "%,d đ", total));
            productView.setTextColor(0xFF75857F); productView.setTextSize(10); productView.setPadding(0, dp(5), 0, 0);
            card.addView(titleView); card.addView(productView);
            card.setOnClickListener(v -> { activeOrder = order; currentOrderId = order.getId(); showScreen(R.layout.activity_order_detail); });
            container.addView(card, Math.max(0, container.indexOfChild(empty)));
            shown++;
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        TextView count = findViewById(R.id.txtOrderCount);
        if (count != null) count.setText(String.valueOf(shown));
        TextView section = findViewById(R.id.txtOrderListSection);
        if (section != null) section.setText("seller".equals(orderRoleFilter) ? "ĐƠN ĐẾN" : "ĐƠN ĐÃ ĐẶT");
    }

    private String orderStatusGroup(String status) {
        switch (status) {
            case "PENDING_PAYMENT": return "unpaid";
            case "PENDING_CONFIRMATION": case "CONFIRMED": return "confirm";
            case "PREPARING": case "READY_TO_SHIP": return "prepare";
            case "SHIPPING": return "shipping";
            default: return "done";
        }
    }

    private String orderStatusLabel(String status) {
        switch (status) {
            case "PENDING_PAYMENT": return "Chờ thanh toán";
            case "PENDING_CONFIRMATION": return "Chờ xác nhận";
            case "CONFIRMED": case "PREPARING": return "Đang chuẩn bị";
            case "READY_TO_SHIP": return "Chờ bàn giao vận chuyển";
            case "SHIPPING": return "Đang giao";
            case "DELIVERED": return "Đã giao";
            case "COMPLETED": return "Hoàn thành";
            case "CANCELLED": return "Đã hủy";
            default: return status;
        }
    }

    private void setOrderRoleFilter(String role) {
        orderRoleFilter = role;
        updateOrderTabs();
        renderOrders();
        if (requireSignedIn()) {
            clearScreenListeners();
            showScreen(R.layout.activity_orders);
        }
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
        renderOrders();
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
        bindClick(R.id.tabReceive, () -> {
            if (heading != null) heading.setText("Món đồ bạn đang nhận và mua");
            clearScreenListeners(); listenToFreeRequests(false);
        });
        bindClick(R.id.tabGive, () -> {
            if (heading != null) heading.setText("Món đồ bạn đang tặng và bán");
            clearScreenListeners(); listenToFreeRequests(true);
        });
        bindClick(R.id.transactionRequestRow, () -> {
            if (currentFreeRequestId.isEmpty()) { Toast.makeText(this, "Chưa có yêu cầu nhận đồ.", Toast.LENGTH_SHORT).show(); return; }
            if (currentFreeRequestOwnerView) {
                new android.app.AlertDialog.Builder(this).setTitle("Xử lý yêu cầu")
                        .setItems(new String[]{"Chấp nhận", "Từ chối"}, (dialog, which) -> memberTwoRepository.respondFreeRequest(currentFreeRequestId, which == 0)
                                .addOnSuccessListener(result -> {
                                    Object raw = result.getData();
                                    if (which == 0 && raw instanceof Map && ((Map<?, ?>) raw).get("orderId") instanceof String) {
                                        currentOrderId = (String) ((Map<?, ?>) raw).get("orderId");
                                        orderRoleFilter = "seller";
                                        showScreen(R.layout.activity_orders);
                                    }
                                }).addOnFailureListener(this::showFirebaseError)).show();
            } else showScreen(R.layout.activity_request_status);
        });
        bindClick(R.id.transactionPurchaseRow, () -> { orderRoleFilter = "buyer"; showScreen(R.layout.activity_orders); });
        bindClick(R.id.transactionIssueRow, () -> showScreen(R.layout.activity_order_detail));
        currentFreeRequestOwnerView = false;
        if (requireSignedIn()) listenToFreeRequests(false);
    }

    private boolean currentFreeRequestOwnerView;

    private void listenToFreeRequests(boolean ownerView) {
        if (!requireSignedIn()) return;
        currentFreeRequestOwnerView = ownerView;
        ListenerRegistration registration = ownerView
                ? memberTwoRepository.listenFreeRequestsAsOwner(currentUid(), (snapshot, error) -> renderFreeRequestRow(snapshot, error, true))
                : memberTwoRepository.listenFreeRequestsAsRequester(currentUid(), (snapshot, error) -> renderFreeRequestRow(snapshot, error, false));
        addScreenListener(registration);
    }

    private void renderFreeRequestRow(QuerySnapshot snapshot, FirebaseFirestoreException error, boolean ownerView) {
        if (error != null) { showFirebaseError(error); return; }
        View row = findViewById(R.id.transactionRequestRow);
        if (row == null) return;
        if (snapshot == null || snapshot.isEmpty()) {
            currentFreeRequestId = ""; row.setVisibility(View.GONE); return;
        }
        row.setVisibility(View.VISIBLE);
        DocumentSnapshot request = snapshot.getDocuments().get(0);
        currentFreeRequestId = request.getId();
        currentPostId = textValue(request.getData(), "postId", "");
        currentSellerId = textValue(request.getData(), "ownerId", "");
        TextView title = findFirstTextChild(row, 1);
        TextView subtitle = findFirstTextChild(row, 2);
        if (title != null) title.setText(ownerView ? "Yêu cầu nhận món đồ của bạn" : "Yêu cầu nhận đồ đã gửi");
        if (subtitle != null) subtitle.setText(textValue(request.getData(), "status", "PENDING") + " · " + textValue(request.getData(), "note", "Không kèm lời nhắn"));
    }

    private TextView findFirstTextChild(View parent, int index) {
        ArrayList<TextView> views = new ArrayList<>(); collectTextViews(parent, views);
        return index >= 0 && index < views.size() ? views.get(index) : null;
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
        if (requireSignedIn()) FirebaseFirestore.getInstance().collection("users").document(currentUid()).get()
                .addOnSuccessListener(user -> {
                    if (!user.exists() || currentScreen != R.layout.activity_edit_profile) return;
                    setEditTextValue(R.id.edtProfileName, user.getString("name"));
                    setEditTextValue(R.id.edtProfileBirthDate, user.getString("birthDate"));
                    setEditTextValue(R.id.edtProfileRegion, user.getString("region"));
                    setEditTextValue(R.id.edtProfileAddress, user.getString("address"));
                    setEditTextValue(R.id.edtProfileBio, user.getString("bio"));
                    String gender = user.getString("gender");
                    Spinner genderSpinner = findViewById(R.id.spinnerProfileGender);
                    if (gender != null && genderSpinner != null && genderSpinner.getAdapter() != null)
                        for (int i = 0; i < genderSpinner.getAdapter().getCount(); i++) if (gender.equals(genderSpinner.getAdapter().getItem(i))) genderSpinner.setSelection(i);
                    loadAvatarIntoTextView(user.getString("photoUrl"), findViewById(R.id.txtEditProfileAvatar));
                });
        if (selectedProfilePhoto != null) {
            TextView avatar = findViewById(R.id.txtEditProfileAvatar);
            if (avatar != null) { avatar.setText(""); avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), selectedProfilePhoto)); }
        }
        if (emailStatus != null && contacts.contains("email")) emailStatus.setText("✓  Email đã xác minh");
        if (phoneStatus != null && contacts.getBoolean("phone_verified", true)) phoneStatus.setText("✓  Số điện thoại đã xác minh");
        bindClick(R.id.btnEditProfileBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.btnEditProfileSave, this::saveProfileToFirebase);
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
                selectedProfilePhotoUri = uri;
                avatar.setText("");
                avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), photo));
                avatar.setClipToOutline(true);
            }
            Toast.makeText(this, "Đã chọn ảnh đại diện. Nhấn Lưu thay đổi để tải lên.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không đọc được ảnh đã chọn.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProfileToFirebase() {
        if (!requireSignedIn()) return;
        EditText name = findViewById(R.id.edtProfileName);
        EditText birthDate = findViewById(R.id.edtProfileBirthDate);
        EditText region = findViewById(R.id.edtProfileRegion);
        EditText address = findViewById(R.id.edtProfileAddress);
        EditText bio = findViewById(R.id.edtProfileBio);
        if (name == null || name.getText().toString().trim().isEmpty()) {
            if (name != null) name.setError("Vui lòng nhập họ tên");
            return;
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name.getText().toString().trim());
        profile.put("gender", spinnerText(R.id.spinnerProfileGender, ""));
        profile.put("birthDate", birthDate == null ? "" : birthDate.getText().toString().trim());
        profile.put("region", region == null ? "" : region.getText().toString().trim());
        profile.put("address", address == null ? "" : address.getText().toString().trim());
        profile.put("bio", bio == null ? "" : bio.getText().toString().trim());
        memberOneRepository.saveUserProfile(currentUid(), profile, selectedProfilePhotoUri, selectedProfilePhoto)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã lưu hồ sơ.", Toast.LENGTH_SHORT).show();
                    selectedProfilePhotoUri = null; selectedProfilePhoto = null;
                    showScreen(R.layout.activity_profile);
                }).addOnFailureListener(this::showFirebaseError);
    }

    private void setEditTextValue(int viewId, String value) {
        EditText field = findViewById(viewId);
        if (field != null && value != null) field.setText(value);
    }

    private void loadAvatarIntoTextView(String url, TextView target) {
        if (url == null || url.isEmpty() || target == null) return;
        FirebaseStorage.getInstance().getReferenceFromUrl(url)
                .getBytes(5 * 1024 * 1024).addOnSuccessListener(bytes -> {
                    if (!target.isAttachedToWindow()) return;
                    Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) { target.setText(""); target.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap)); }
                });
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
        if (!currentFreeRequestId.isEmpty()) addScreenListener(memberTwoRepository.listenFreeRequest(currentFreeRequestId, (request, error) -> {
            if (error != null) { showFirebaseError(error); return; }
            TextView status = findViewById(R.id.txtRequestStatus);
            if (status != null && request != null && request.exists()) {
                String state = textValue(request.getData(), "status", "PENDING");
                status.setText("PENDING".equals(state) ? "Đang chờ người tặng xác nhận"
                        : "ACCEPTED".equals(state) ? "Người tặng đã chấp nhận yêu cầu"
                        : "REJECTED".equals(state) ? "Người tặng chưa thể nhận yêu cầu này"
                        : "UNAVAILABLE".equals(state) ? "Sản phẩm đã có người nhận" : "Yêu cầu đã được cập nhật");
            }
        }));
        bindClick(R.id.btnChatWithDonor, () -> {
            startChatWithPeer(currentSellerId, currentPostId, R.layout.activity_request_status);
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
        if (!currentOrderId.isEmpty()) addScreenListener(memberTwoRepository.listenOrder(currentOrderId, (snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) return;
            activeOrder = snapshot;
            populateOrderDetail();
        }));
        populateOrderDetail();
        bindClick(R.id.btnChatOrderSeller, () -> {
            if (activeOrder == null) return;
            Map<String, Object> data = activeOrder.getData();
            String peer = currentUid().equals(textValue(data, "buyerId", "")) ? textValue(data, "sellerId", "") : textValue(data, "buyerId", "");
            startChatWithPeer(peer, textValue(data, "postId", ""), R.layout.activity_order_detail);
        });
        bindClick(R.id.btnTrackShipping, () -> { if (activeOrder != null) showScreen(R.layout.activity_delivery); });
        bindClick(R.id.btnReportOrder, () -> showScreen(R.layout.activity_report_issue));
        bindClick(R.id.btnCancelOrder, () -> showScreen(R.layout.activity_cancel_order));
        bindClick(R.id.btnConfirmReceived, () -> {
            if (currentOrderId.isEmpty() || !requireSignedIn()) return;
            memberTwoRepository.updateOrderStatus(currentOrderId, "COMPLETED")
                    .addOnSuccessListener(result -> Toast.makeText(this, "Đã xác nhận nhận hàng.", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(this::showFirebaseError);
        });
    }

    private void populateOrderDetail() {
        if (activeOrder == null) return;
        Map<String, Object> data = activeOrder.getData();
        setTextIfPresent(R.id.txtOrderDetailTitle, "Đơn hàng #" + activeOrder.getId());
        Map<String, Object> postSnapshot = data.get("postSnapshot") instanceof Map ? (Map<String, Object>) data.get("postSnapshot") : new HashMap<>();
        String status = textValue(data, "orderStatus", textValue(data, "status", ""));
        setTextIfPresent(R.id.txtOrderHeroStatus, orderStatusLabel(status));
        setTextIfPresent(R.id.txtOrderStatusSummary, orderStatusLabel(status));
        setTextIfPresent(R.id.txtOrderDetailProductName, textValue(postSnapshot, "title", "Sản phẩm"));
        setTextIfPresent(R.id.txtOrderDetailType, "FREE".equalsIgnoreCase(textValue(data, "orderType", "PAID")) ? "Tặng miễn phí" : "Đang bán");
        setTextIfPresent(R.id.txtOrderProductPrice, String.format(java.util.Locale.getDefault(), "%,d đ", numberValue(data.get("price"))));
        setTextIfPresent(R.id.txtOrderTotal, String.format(java.util.Locale.getDefault(), "%,d đ", numberValue(data.get("price")) + numberValue(data.get("shippingFee"))));
        String buyerId = textValue(data, "buyerId", ""), sellerId = textValue(data, "sellerId", "");
        String peerId = currentUid().equals(buyerId) ? sellerId : buyerId;
        if (!peerId.isEmpty()) FirebaseFirestore.getInstance().collection("users").document(peerId).get().addOnSuccessListener(user -> {
            setTextIfPresent(R.id.txtOrderDetailSeller, textValue(user.getData(), "name", "Thành viên"));
        });
    }

    private void setTextIfPresent(int id, String value) {
        TextView view = findViewById(id);
        if (view != null) view.setText(value == null ? "" : value);
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
        EditText descInput = findViewById(R.id.edtCancelDescription);
        Button submit = findViewById(R.id.btnSubmitCancel);
        Runnable updateSubmitState = () -> {
            boolean ready = reasons.getCheckedRadioButtonId() != -1 && !descInput.getText().toString().trim().isEmpty();
            submit.setEnabled(ready);
            submit.setAlpha(ready ? 1f : 0.55f);
            submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ready ? 0xFF104536 : 0xFFE4E8E5));
            submit.setTextColor(ready ? 0xFFFFFFFF : 0xFF75857F);
        };
        reasons.setOnCheckedChangeListener((group, checkedId) -> updateSubmitState.run());
        descInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSubmitState.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        bindClick(R.id.btnCloseCancel, () -> showScreen(R.layout.activity_order_detail));
        bindClick(R.id.btnSubmitCancel, () -> {
            if (reasons.getCheckedRadioButtonId() == -1 || descInput.getText().toString().trim().isEmpty()) return;
            if (currentOrderId.isEmpty() || !requireSignedIn()) return;
            memberTwoRepository.cancelOrder(currentOrderId).addOnSuccessListener(result -> {
                Toast.makeText(this, "Đã hủy đơn.", Toast.LENGTH_SHORT).show();
                showScreen(R.layout.activity_order_detail);
            }).addOnFailureListener(this::showFirebaseError);
        });
    }

    private void bindOrderChat() {
        bindClick(R.id.btnBack, () -> showScreen(chatReturnScreen));
        TextView chatName = findViewById(R.id.tvChatName);
        if (chatName != null && !currentPeerId.isEmpty()) FirebaseFirestore.getInstance().collection("users").document(currentPeerId).get()
                .addOnSuccessListener(user -> chatName.setText(textValue(user.getData(), "name", "Cuộc trò chuyện")));
        LinearLayout messages = findViewById(R.id.chatMessagesContainer);
        if (messages != null && !currentChatId.isEmpty()) {
            messages.removeAllViews();
            addScreenListener(memberTwoRepository.listenMessages(currentChatId, (snapshot, error) -> {
                if (error != null) { showFirebaseError(error); return; }
                messages.removeAllViews();
                if (snapshot == null) return;
                for (DocumentSnapshot message : snapshot.getDocuments()) {
                    TextView bubble = new TextView(this);
                    bubble.setText(textValue(message.getData(), "text", ""));
                    bubble.setTextSize(14); bubble.setTextColor(0xFF173B32); bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
                    boolean mine = currentUid().equals(textValue(message.getData(), "senderId", ""));
                    bubble.setBackgroundResource(mine ? R.drawable.bg_button_primary : R.drawable.bg_white_card_rounded);
                    if (mine) bubble.setTextColor(0xFFFFFFFF);
                    LinearLayout row = new LinearLayout(this); row.setGravity(mine ? android.view.Gravity.END : android.view.Gravity.START);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.bottomMargin = dp(7);
                    row.addView(bubble, new LinearLayout.LayoutParams(-2, -2)); messages.addView(row, rowParams);
                }
                messages.post(() -> { if (messages.getParent() instanceof android.widget.ScrollView) ((android.widget.ScrollView) messages.getParent()).fullScroll(View.FOCUS_DOWN); });
            }));
            memberTwoRepository.markChatRead(currentChatId).addOnFailureListener(this::showFirebaseError);
        }
        bindClick(R.id.btnSendMessage, () -> {
            EditText messageInput = findViewById(R.id.edtMessageInput);
            if (messageInput == null || messageInput.getText().toString().trim().isEmpty() || currentChatId.isEmpty()) return;
            String text = messageInput.getText().toString().trim();
            memberTwoRepository.sendMessage(currentChatId, text)
                    .addOnSuccessListener(result -> messageInput.setText("")).addOnFailureListener(this::showFirebaseError);
        });
    }

    private void bindDelivery() {
        bindClick(R.id.btnDeliveryBack, () -> showScreen(R.layout.activity_order_detail));
        bindClick(R.id.btnOpenDeliveryMap, () -> showScreen(R.layout.activity_delivery_map));
        if (!currentOrderId.isEmpty()) addScreenListener(memberTwoRepository.listenShipment(currentOrderId, (shipment, error) -> {
            if (error != null || shipment == null || !shipment.exists()) return;
            String shipmentStatus = textValue(shipment.getData(), "status", "Đang chuẩn bị");
            setTextIfPresent(R.id.txtDeliveryHeadline, shipmentStatus);
        }));
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
        CheckBox item3 = findViewById(R.id.cbItem3);
        if (item3 != null) setCartItemVisibility(item3, false);
        if (requireSignedIn()) addScreenListener(memberTwoRepository.listenCart(currentUid(), (snapshot, error) -> {
            if (error != null) { showFirebaseError(error); return; }
            activeCart.clear(); if (snapshot != null) activeCart.addAll(snapshot.getDocuments());
            for (DocumentSnapshot item : activeCart) if (Boolean.TRUE.equals(item.get("selected"))) selectedCartPostIds.add(item.getId());
            renderCartItems();
        }));
        android.widget.CompoundButton.OnCheckedChangeListener itemListener = (button, checked) -> {
            if (updatingCartSelection) return;
            String postId = cartPostIdFor(button.getId());
            if (!postId.isEmpty()) { if (checked) selectedCartPostIds.add(postId); else selectedCartPostIds.remove(postId); }
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
            for (DocumentSnapshot cartItem : activeCart) { if (checked) selectedCartPostIds.add(cartItem.getId()); else selectedCartPostIds.remove(cartItem.getId()); }
            if (isCartItemVisible(item1)) item1.setChecked(checked);
            if (isCartItemVisible(item2)) item2.setChecked(checked);
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
            if (selectedCartPostIds.size() != 1) {
                Toast.makeText(this, "Vui lòng chọn từng sản phẩm để tạo đơn riêng.", Toast.LENGTH_SHORT).show(); return;
            }
            String postId = selectedCartPostIds.iterator().next();
            for (DocumentSnapshot item : activeCart) if (item.getId().equals(postId)) {
                currentPostId = postId;
                currentProductName = textValue(item.getData(), "title", "Sản phẩm");
                currentProductData = item.getData() == null ? new HashMap<>() : new HashMap<>(item.getData());
                break;
            }
            checkoutReturnScreen = R.layout.activity_cart;
            showScreen(R.layout.activity_checkout);
        });
        bindClick(R.id.btnExplore, () -> showScreen(R.layout.activity_explore));
        updateCartSummary();
    }

    private String cartPostIdFor(int checkboxId) {
        int index = checkboxId == R.id.cbItem1 ? 0 : checkboxId == R.id.cbItem2 ? 1 : -1;
        return index >= 0 && index < activeCart.size() ? activeCart.get(index).getId() : "";
    }

    private void renderCartItems() {
        int[] checkboxIds = {R.id.cbItem1, R.id.cbItem2};
        for (int i = 0; i < checkboxIds.length; i++) {
            CheckBox checkbox = findViewById(checkboxIds[i]);
            boolean exists = i < activeCart.size();
            setCartItemVisibility(checkbox, exists);
            if (!exists) continue;
            DocumentSnapshot cartItem = activeCart.get(i);
            String title = textValue(cartItem.getData(), "title", "Sản phẩm");
            String condition = textValue(cartItem.getData(), "condition", "");
            String size = textValue(cartItem.getData(), "size", "");
            String secondary = (condition + (size.isEmpty() ? "" : " · " + size)).trim();
            long price = numberValue(cartItem.get("price"));
            View row = (View) checkbox.getParent();
            ArrayList<TextView> textViews = new ArrayList<>(); collectTextViews(row, textViews);
            if (textViews.size() >= 4) {
                textViews.get(0).setText(title);
                textViews.get(1).setText(secondary);
                textViews.get(2).setText(String.format(java.util.Locale.getDefault(), "%,d đ", price));
                textViews.get(3).setText("Còn hàng");
            }
            checkbox.setChecked(selectedCartPostIds.contains(cartItem.getId()));
        }
        updateCartSummary();
    }

    private void collectTextViews(View view, List<TextView> out) {
        if (view instanceof CheckBox) return;
        if (view instanceof TextView) { out.add((TextView) view); return; }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) collectTextViews(((ViewGroup) view).getChildAt(i), out);
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
        int subtotal = 0;
        for (int i = 0; i < activeCart.size() && i < 2; i++) {
            CheckBox checkbox = i == 0 ? item1 : item2;
            if (checkbox.isChecked()) subtotal += numberValue(activeCart.get(i).get("price"));
        }
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
        String postId = checkbox == null ? "" : cartPostIdFor(checkboxId);
        if (postId.isEmpty() || !requireSignedIn()) return;
        memberTwoRepository.removeCartItem(currentUid(), postId).addOnSuccessListener(unused -> selectedCartPostIds.remove(postId))
                .addOnFailureListener(this::showFirebaseError);
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
            final String filter = filters[i]; // Khai báo final
            final int filterId = filterIds[i];
            bindClick(filterId, () -> {
                notificationFilter = filter;
                applyNotificationFilter(filterIds, filters);
            });
        }
        int[] rowIds = {R.id.notifRowFreeRequest, R.id.notifRowMessage, R.id.notifRowShipping,
                R.id.notifRowPayment, R.id.notifRowRequestDone, R.id.notifRowDispute};
        int[] unreadIds = {R.id.notifUnread1, R.id.notifUnread2, R.id.notifUnread3};
        for (int i = 0; i < rowIds.length; i++) {
            final int rowId = rowIds[i]; // Khai báo final
            final int unreadId = i < unreadIds.length ? unreadIds[i] : View.NO_ID;
            View row = findViewById(rowId);
            boolean read = getSharedPreferences("notifications_demo", MODE_PRIVATE).getBoolean("read_" + rowId, false);
            if (read && unreadId != View.NO_ID) findViewById(unreadId).setVisibility(View.GONE);
            if (read) row.setBackgroundColor(0xFFFFFFFF);
            bindClick(rowId, () -> {
                getSharedPreferences("notifications_demo", MODE_PRIVATE).edit().putBoolean("read_" + rowId, true).apply();
                if (unreadId != View.NO_ID) findViewById(unreadId).setVisibility(View.GONE);
                row.setBackgroundColor(0xFFFFFFFF);
                if (rowId == R.id.notifRowFreeRequest || rowId == R.id.notifRowRequestDone)
                    showScreen(R.layout.activity_transactions);
                else if (rowId == R.id.notifRowMessage) {
                    chatReturnScreen = R.layout.activity_messages;
                    showScreen(R.layout.activity_messages);
                } else if (rowId == R.id.notifRowPayment)
                    showScreen(R.layout.activity_payment_info);
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
            if (currentPostId.isEmpty() || !requireSignedIn()) {
                Toast.makeText(this, "Không tìm thấy bài đăng để tạo đơn.", Toast.LENGTH_SHORT).show(); return;
            }
            memberTwoRepository.createPaidOrder(currentPostId, UUID.randomUUID().toString())
                    .addOnSuccessListener(result -> {
                        Object raw = result.getData();
                        if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get("orderId") instanceof String)) {
                            Toast.makeText(this, "Không nhận được mã đơn hàng.", Toast.LENGTH_LONG).show(); return;
                        }
                        currentOrderId = (String) ((Map<?, ?>) raw).get("orderId");
                        if (checkoutReturnScreen == R.layout.activity_cart) {
                            memberTwoRepository.removeCartItem(currentUid(), currentPostId)
                                    .addOnFailureListener(this::showFirebaseError);
                        }
                        FirebaseFirestore.getInstance().collection("orders").document(currentOrderId).get()
                                .addOnSuccessListener(order -> { activeOrder = order; showScreen(R.layout.activity_order_detail); })
                                .addOnFailureListener(this::showFirebaseError);
                    }).addOnFailureListener(this::showFirebaseError);
        });
    }

    private void showExploreFilterSheet() {
        String[] categories = {"Tất cả", "Áo", "Quần", "Váy", "Nữ", "Nam", "Trẻ em", "Giày", "Túi"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Lọc theo danh mục")
                .setItems(categories, (dialog, which) -> applyExploreFilter(categories[which]))
                .show();
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
        Spinner condition = findViewById(R.id.spinnerCondition);
        if (condition != null) {
            String[] conditions = {"Tốt", "Rất tốt", "Như mới", "Có dấu hiệu sử dụng"};
            condition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, conditions));
        }
        bindClick(R.id.photoPickerArea, this::showPhotoSourcePicker);
        bindClick(R.id.btnSellBack, () -> showScreen(editingListing ? R.layout.activity_my_listings : R.layout.activity_home));
        bindClick(R.id.btnSellNext, () -> {
            if (!editingListing && selectedListingPhotos.isEmpty()) {
                Toast.makeText(this, "Vui lòng thêm ít nhất một ảnh sản phẩm.", Toast.LENGTH_SHORT).show(); return;
            }
            showScreen(R.layout.activity_sell_details);
        });
        if (!selectedListingPhotos.isEmpty()) showSelectedListingPhoto();
    }

    private void showPhotoSourcePicker() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
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
        if (!selectedListingPhotos.isEmpty() && selectedListingPhotos.get(0) instanceof Bitmap) image.setImageBitmap((Bitmap) selectedListingPhotos.get(0));
        else if (!selectedListingPhotos.isEmpty() && selectedListingPhotos.get(0) instanceof Uri) image.setImageURI((Uri) selectedListingPhotos.get(0));
        else if (capturedListingPhoto != null) image.setImageBitmap(capturedListingPhoto);
        else if (selectedListingPhoto != null) image.setImageURI(selectedListingPhoto);
        if (placeholder != null) placeholder.setVisibility(View.GONE);
        if (hint != null) hint.setText("Đã chọn " + selectedListingPhotos.size() + "/8 ảnh · Chạm để thêm ảnh");
    }

    private void updateListingPhotoSelection() {
        selectedListingPhoto = null; capturedListingPhoto = null;
        if (!selectedListingPhotos.isEmpty()) {
            Object first = selectedListingPhotos.get(0);
            if (first instanceof Uri) selectedListingPhoto = (Uri) first;
            else if (first instanceof Bitmap) capturedListingPhoto = (Bitmap) first;
        }
    }

    private void bindSellDetails() {
        EditText productName = findViewById(R.id.edtProductName);
        EditText productDesc = findViewById(R.id.edtProductDescription);
        if (productName != null && (editingListing || listingDraft.containsKey("title"))) productName.setText(String.valueOf(listingDraft.getOrDefault("title", currentProductName)));
        if (productDesc != null && listingDraft.containsKey("description")) productDesc.setText(String.valueOf(listingDraft.get("description")));
        setSpinnerOptions(R.id.spinnerCategory, new String[]{"Áo khoác", "Áo thun", "Áo sơ mi", "Quần", "Váy", "Đầm", "Giày dép", "Túi xách", "Phụ kiện"});
        setSpinnerOptions(R.id.spinnerSize, new String[]{"XS", "S", "M", "L", "XL", "XXL", "Free size"});
        setSpinnerOptions(R.id.spinnerCondition, new String[]{"Mới có tag", "Như mới", "Tốt", "Đã qua sử dụng"});
        setSpinnerOptions(R.id.spinnerLocation, new String[]{"Quận 1, TP.HCM", "Quận 3, TP.HCM", "Bình Thạnh, TP.HCM", "Thủ Đức, TP.HCM", "Hà Nội", "Đà Nẵng", "Cần Thơ"});
        setSpinnerValue(R.id.spinnerCategory, "category"); setSpinnerValue(R.id.spinnerSize, "size");
        if (!listingDraft.containsKey("conditionLabel") && listingDraft.containsKey("condition"))
            listingDraft.put("conditionLabel", conditionLabel(String.valueOf(listingDraft.get("condition"))));
        setSpinnerValue(R.id.spinnerCondition, "conditionLabel"); setSpinnerValue(R.id.spinnerLocation, "location");
        bindClick(R.id.btnSellDetailsBack, () -> showScreen(editingListing ? R.layout.activity_my_listings : R.layout.activity_sell));
        bindClick(R.id.btnSellDetailsPrevious, () -> { captureSellDetails(); showScreen(editingListing ? R.layout.activity_my_listings : R.layout.activity_sell); });
        bindClick(R.id.btnSellDetailsNext, () -> { if (captureSellDetails()) showScreen(R.layout.activity_sell_type); });
    }

    private void setSpinnerValue(int spinnerId, String key) {
        Spinner spinner = findViewById(spinnerId); Object value = listingDraft.get(key);
        if (spinner == null || value == null || spinner.getAdapter() == null) return;
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) if (String.valueOf(spinner.getAdapter().getItem(i)).equalsIgnoreCase(String.valueOf(value))) { spinner.setSelection(i); break; }
    }

    private boolean captureSellDetails() {
        EditText name = findViewById(R.id.edtProductName); EditText productDesc = findViewById(R.id.edtProductDescription);
        if (name == null || name.getText().toString().trim().isEmpty()) { if (name != null) name.setError("Vui lòng nhập tên sản phẩm"); return false; }
        listingDraft.put("title", name.getText().toString().trim()); currentProductName = name.getText().toString().trim();
        listingDraft.put("description", productDesc == null ? "" : productDesc.getText().toString().trim());
        putSpinnerDraft("spinnerCategory", "category"); putSpinnerDraft("spinnerSize", "size");
        String condition = spinnerText(R.id.spinnerCondition, "Tốt");
        listingDraft.put("conditionLabel", condition); listingDraft.put("condition", toConditionCode(condition));
        putSpinnerDraft("spinnerLocation", "location");
        return true;
    }

    private String spinnerText(int id, String fallback) { Spinner s = findViewById(id); return s == null || s.getSelectedItem() == null ? fallback : String.valueOf(s.getSelectedItem()); }
    private void putSpinnerDraft(String idName, String key) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        if (id != 0) listingDraft.put(key, spinnerText(id, ""));
    }
    private String toConditionCode(String label) {
        if (label.contains("tag")) return "NEW";
        if (label.equals("Như mới")) return "LIKE_NEW";
        if (label.equals("Tốt")) return "GOOD";
        return "FAIR";
    }
    private String conditionLabel(String code) {
        if ("NEW".equals(code)) return "Mới có tag";
        if ("LIKE_NEW".equals(code)) return "Như mới";
        if ("FAIR".equals(code)) return "Đã qua sử dụng";
        return "Tốt";
    }

    private void bindSellType() {
        RadioGroup listingType = findViewById(R.id.rgListingType);
        EditText price = findViewById(R.id.edtProductPrice);
        if (listingDraft.containsKey("type") && listingType != null) listingType.check("FREE".equals(listingDraft.get("type")) ? R.id.radioFree : R.id.radioSell);
        if (price != null && listingDraft.containsKey("price")) price.setText(String.valueOf(listingDraft.get("price")));
        if (listingType != null && price != null) {
            listingType.setOnCheckedChangeListener((group, checkedId) -> {
                boolean selling = checkedId == R.id.radioSell;
                price.setEnabled(selling);
                price.setAlpha(selling ? 1f : 0.45f);
            });
        }
        bindClick(R.id.btnSellTypeBack, () -> showScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnSellTypePrevious, () -> showScreen(R.layout.activity_sell_details));
        bindClick(R.id.btnSellTypeNext, () -> { if (captureSellType()) showScreen(R.layout.activity_sell_preview); });
    }

    private boolean captureSellType() {
        RadioGroup group = findViewById(R.id.rgListingType); EditText priceInput = findViewById(R.id.edtProductPrice);
        boolean free = group != null && group.getCheckedRadioButtonId() == R.id.radioFree;
        long price = 0;
        if (!free) {
            try { price = Long.parseLong(priceInput == null ? "" : priceInput.getText().toString().trim()); }
            catch (NumberFormatException e) { if (priceInput != null) priceInput.setError("Nhập giá bán hợp lệ"); return false; }
            if (price <= 0) { if (priceInput != null) priceInput.setError("Giá bán phải lớn hơn 0"); return false; }
        }
        listingDraft.put("type", free ? "FREE" : "SALE"); listingDraft.put("price", price);
        return true;
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
        TextView previewName = findViewById(R.id.txtPreviewName); if (previewName != null) previewName.setText(String.valueOf(listingDraft.getOrDefault("title", "Sản phẩm")));
        TextView previewDescription = findViewById(R.id.txtPreviewDescription);
        if (previewDescription != null && listingDraft.get("description") != null) { previewDescription.setText(String.valueOf(listingDraft.get("description"))); previewDescription.setVisibility(View.VISIBLE); }
        boolean isFree = "FREE".equals(listingDraft.get("type"));
        TextView previewType = findViewById(R.id.txtPreviewType); if (previewType != null) previewType.setText(isFree ? "TẶNG MIỄN PHÍ" : "FOR SALE");
        TextView previewPrice = findViewById(R.id.txtPreviewPrice); if (previewPrice != null) previewPrice.setText(isFree ? "FREE" : String.format(java.util.Locale.getDefault(), "%,d đ", numberValue(listingDraft.get("price"))));
        TextView previewMeta = findViewById(R.id.txtPreviewMeta); if (previewMeta != null) previewMeta.setText(listingDraft.getOrDefault("conditionLabel", "Tốt") + " · " + listingDraft.getOrDefault("size", "M") + " · " + listingDraft.getOrDefault("location", ""));
        bindClick(R.id.btnSellPreviewBack, () -> showScreen(R.layout.activity_sell_type));
        bindClick(R.id.btnSellPreviewPrevious, () -> showScreen(R.layout.activity_sell_type));
        bindClick(R.id.btnSaveSellDraft, () -> saveListingToFirebase(true));
        bindClick(R.id.btnPublishListing, () -> saveListingToFirebase(false));
    }

    private void saveListingToFirebase(boolean asDraft) {
        if (!requireSignedIn()) return;
        if (listingDraft.get("title") == null || listingDraft.get("type") == null || listingDraft.get("price") == null) {
            Toast.makeText(this, "Vui lòng hoàn tất thông tin sản phẩm.", Toast.LENGTH_SHORT).show(); return;
        }
        String uid = currentUid();
        if (uid == null) return;
        String postId = editingListing && !editingPostId.isEmpty() ? editingPostId : memberOneRepository.newPostId();
        boolean wasEditing = editingListing;
        Map<String, Object> data = new HashMap<>(listingDraft);
        data.remove("conditionLabel");
        data.put("status", editingListing ? String.valueOf(listingDraft.getOrDefault("status", "ACTIVE")) : (asDraft ? "DRAFT" : "ACTIVE"));
        if (!listingDraft.containsKey("createdAt")) data.put("createdAt", FieldValue.serverTimestamp());
        memberOneRepository.saveListing(uid, postId, data, new ArrayList<>(selectedListingPhotos), editingListing)
                .addOnSuccessListener(unused -> {
                    currentPostId = postId; editingListing = false; editingPostId = "";
                    Toast.makeText(this, wasEditing ? "Đã cập nhật tin đăng." : (asDraft ? "Đã lưu bản nháp." : "Đăng tin thành công."), Toast.LENGTH_SHORT).show();
                    showScreen(asDraft ? R.layout.activity_my_listings : R.layout.activity_sell_success);
                }).addOnFailureListener(this::showFirebaseError);
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
        bindClick(R.id.btnSubmitRequest, () -> {
            if (currentPostId.isEmpty() || !requireSignedIn()) return;
            memberTwoRepository.createFreeRequest(currentPostId, "")
                    .addOnSuccessListener(result -> {
                        Object raw = result.getData();
                        if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get("requestId") instanceof String)) {
                            Toast.makeText(this, "Không nhận được mã yêu cầu.", Toast.LENGTH_LONG).show(); return;
                        }
                        currentFreeRequestId = (String) ((Map<?, ?>) raw).get("requestId");
                        showScreen(R.layout.activity_request_status);
                    }).addOnFailureListener(this::showFirebaseError);
        });
    }

    private void bindMessages() {
        EditText search = findViewById(R.id.edtSearchMessage);
        int[] conversationIds = {R.id.conversation1, R.id.conversation2, R.id.conversation3};
        View first = findViewById(conversationIds[0]);
        ViewGroup list = first != null && first.getParent() instanceof ViewGroup ? (ViewGroup) first.getParent() : null;
        for (int id : conversationIds) { View sample = findViewById(id); if (sample != null) sample.setVisibility(View.GONE); }
        if (requireSignedIn()) addScreenListener(memberTwoRepository.listenChats(currentUid(), (snapshot, error) -> {
            if (error != null) { showFirebaseError(error); return; }
            activeChats.clear(); if (snapshot != null) activeChats.addAll(snapshot.getDocuments());
            if (list == null) return;
            for (int i = list.getChildCount() - 1; i >= 0; i--) {
                Object tag = list.getChildAt(i).getTag();
                if (tag instanceof String && ((String) tag).startsWith("firebase_chat|")) list.removeViewAt(i);
            }
            for (DocumentSnapshot chat : activeChats) {
                List<String> participants = (List<String>) chat.get("participantIds");
                if (participants == null) continue;

                String peerIdTemp = "";
                for (String participant : participants) {
                    if (!participant.equals(currentUid())) {
                        peerIdTemp = participant;
                    }
                }
                if (peerIdTemp.isEmpty()) continue;

                // Khai báo các biến final / effectively final để dùng an toàn trong lambda
                final String chatId = chat.getId();
                final String peerId = peerIdTemp;

                String previewText = textValue(chat.getData(), "lastMessage", "Bắt đầu cuộc trò chuyện");
                String searchTag = (peerId + " " + previewText).toLowerCase(java.util.Locale.ROOT);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(14), dp(14), dp(14));
                row.setBackgroundResource(R.drawable.bg_white_card_rounded);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(10);
                row.setLayoutParams(params);
                row.setTag("firebase_chat|" + searchTag);

                TextView avatar = new TextView(this);
                avatar.setText("●");
                avatar.setTextSize(24);
                avatar.setGravity(17);
                avatar.setBackgroundResource(R.drawable.bg_green_circle_soft);
                row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(dp(12), 0, 0, 0);

                TextView name = new TextView(this);
                name.setText("Thành viên");
                name.setTextColor(0xFF173B32);
                name.setTextSize(14);
                name.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView preview = new TextView(this);
                preview.setText(previewText);
                preview.setTextColor(0xFF75857F);
                preview.setTextSize(12);
                preview.setMaxLines(1);

                info.addView(name);
                info.addView(preview);
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                // Sử dụng chatId và peerId đã khai báo final ở trên
                row.setOnClickListener(v -> {
                    currentChatId = chatId;
                    currentPeerId = peerId;
                    chatReturnScreen = R.layout.activity_messages;
                    showScreen(R.layout.activity_chat);
                });

                list.addView(row);

                FirebaseFirestore.getInstance().collection("users").document(peerId).get()
                        .addOnSuccessListener(user -> name.setText(textValue(user.getData(), "name", "Thành viên")));
            }
            applyMessageFilters();
        }));
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
        View first = findViewById(R.id.conversation1);
        ViewGroup list = first != null && first.getParent() instanceof ViewGroup ? (ViewGroup) first.getParent() : null;
        if (list == null) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i); Object value = row.getTag();
            if (!(value instanceof String) || !((String) value).startsWith("firebase_chat|")) continue;
            String tag = (String) value;
            boolean searchMatch = messageSearchQuery.isEmpty() || tag.contains(messageSearchQuery);
            row.setVisibility(searchMatch ? View.VISIBLE : View.GONE);
        }
    }

    private void bindProfile() {
        android.content.SharedPreferences verification = getSharedPreferences("account_verification", MODE_PRIVATE);
        String status = verification.getString("status", "none");
        TextView title = findViewById(R.id.txtVerificationCardTitle);
        TextView subtitle = findViewById(R.id.txtVerificationCardSubtitle);
        TextView profileLocation = findViewById(R.id.txtProfileLocation);
        TextView profileAvatar = findViewById(R.id.imgProfileAvatar);
        if (requireSignedIn()) FirebaseFirestore.getInstance().collection("users").document(currentUid()).get()
                .addOnSuccessListener(user -> {
                    if (!user.exists() || currentScreen != R.layout.activity_profile) return;
                    TextView name = findViewById(R.id.txtProfileName);
                    if (name != null && user.getString("name") != null) name.setText(user.getString("name"));
                    loadAvatarIntoTextView(user.getString("photoUrl"), findViewById(R.id.imgProfileAvatar));
                });
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
        bindClick(R.id.btnLogout, () -> {
            authRepository.signOut();
            showScreen(R.layout.activity_login);
        });
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
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
        currentPostId = "";
        currentSellerId = "";
        currentProductData = new HashMap<>();
        currentProductName = name == null ? "" : name;
        currentProductEmoji = emoji == null ? "👕" : emoji;
        String normalizedPrice = price == null ? "" : price.toLowerCase();
        currentProductIsFree = normalizedPrice.contains("free") || normalizedPrice.contains("miễn phí")
                || normalizedPrice.contains("0đ") || normalizedPrice.contains("0 đ");
        currentProductPrice = price == null ? "" : price;
        showScreen(R.layout.activity_product_detail);
    }

    private void openProduct(DocumentSnapshot post) {
        if (post == null || !post.exists()) return;
        productReturnScreen = currentScreen;
        currentPostId = post.getId();
        currentProductData = post.getData() == null ? new HashMap<>() : new HashMap<>(post.getData());
        currentSellerId = textValue(currentProductData, "ownerId", textValue(currentProductData, "userId", ""));
        currentProductName = textValue(currentProductData, "title", "Sản phẩm");
        currentProductEmoji = emojiFor(currentProductName);
        String type = textValue(currentProductData, "type", textValue(currentProductData, "listingType", ""));
        currentProductIsFree = "FREE".equalsIgnoreCase(type);
        long price = numberValue(currentProductData.get("price"));
        currentProductPrice = currentProductIsFree ? "0 đ" : String.format(java.util.Locale.getDefault(), "%,d đ", price);
        showScreen(R.layout.activity_product_detail);
    }

    private void startChatWithPeer(String peerId, String postId, int returnScreen) {
        if (!requireSignedIn()) return;
        if (peerId == null || peerId.isEmpty() || peerId.equals(currentUid())) {
            Toast.makeText(this, "Không thể mở cuộc trò chuyện với người đăng này.", Toast.LENGTH_SHORT).show();
            return;
        }
        memberTwoRepository.createChat(peerId, postId)
                .addOnSuccessListener(result -> {
                    Object data = result.getData();
                    if (!(data instanceof Map) || !(((Map<?, ?>) data).get("chatId") instanceof String)) {
                        Toast.makeText(this, "Không lấy được mã cuộc trò chuyện.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    currentChatId = (String) ((Map<?, ?>) data).get("chatId");
                    currentPeerId = peerId;
                    chatReturnScreen = returnScreen;
                    showScreen(R.layout.activity_chat);
                }).addOnFailureListener(this::showFirebaseError);
    }

    private Map<String, Object> cartDataForCurrentProduct() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", currentProductName);
        data.put("sellerId", currentSellerId);
        data.put("price", numberValue(currentProductData.get("price")));
        data.put("type", textValue(currentProductData, "type", textValue(currentProductData, "listingType", "SALE")));
        data.put("condition", textValue(currentProductData, "condition", ""));
        data.put("size", textValue(currentProductData, "size", ""));
        data.put("selected", true);
        return data;
    }

    private void bindProductDetail() {
        TextView productName = findViewById(R.id.txtProductName);
        TextView productEmoji = findViewById(R.id.txtProductEmoji);
        if (productEmoji != null && productEmoji.getParent() instanceof FrameLayout) {
            FrameLayout photoFrame = (FrameLayout) productEmoji.getParent();
            ImageView remotePhoto = new ImageView(this); remotePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photoFrame.addView(remotePhoto, 0, new FrameLayout.LayoutParams(-1, -1));
            currentProductImageIndex = 0;
            TextView imageIndex = findViewById(R.id.txtProductPhotoIndex);
            updateProductImageIndex(imageIndex);
            android.view.GestureDetector imageGestures = new android.view.GestureDetector(this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDown(android.view.MotionEvent event) { return true; }
                        @Override public boolean onFling(android.view.MotionEvent start, android.view.MotionEvent end, float velocityX, float velocityY) {
                            if (start == null || end == null || Math.abs(end.getX() - start.getX()) <= Math.abs(end.getY() - start.getY())) return false;
                            Object raw = currentProductData.get("imageUrls");
                            if (!(raw instanceof List) || ((List<?>) raw).size() <= 1) return false;
                            int count = ((List<?>) raw).size();
                            currentProductImageIndex = (currentProductImageIndex + (end.getX() < start.getX() ? 1 : count - 1)) % count;
                            updateProductImageIndex(imageIndex);
                            loadPostPhoto(currentProductData, currentProductImageIndex, remotePhoto, productEmoji);
                            return true;
                        }
                    });
            photoFrame.setOnTouchListener((view, event) -> imageGestures.onTouchEvent(event));
            loadPostPhoto(currentProductData, 0, remotePhoto, productEmoji);
        }
        TextView productPrice = findViewById(R.id.txtProductPrice);
        if (productName != null && !currentProductName.isEmpty()) productName.setText(currentProductName);
        if (productEmoji != null) productEmoji.setText(currentProductEmoji);
        if (productPrice != null && !currentProductPrice.isEmpty()) productPrice.setText(currentProductIsFree ? "FREE" : currentProductPrice);
        TextView category = findViewById(R.id.txtProductCategory);
        TextView size = findViewById(R.id.txtProductSize);
        TextView location = findViewById(R.id.txtProductLocation);
        TextView seller = findViewById(R.id.txtProductSeller);
        TextView condition = findViewById(R.id.txtProductCondition);
        if (condition != null) condition.setText(textValue(currentProductData, "condition", "Tốt"));
        if (category != null) category.setText(textValue(currentProductData, "categoryName",
                textValue(currentProductData, "category", productCategoryFor(currentProductName))));
        if (size != null) size.setText(textValue(currentProductData, "size", "Chưa cung cấp"));
        if (location != null) location.setText(textValue(currentProductData, "location", "Chưa cung cấp"));
        TextView descView = findViewById(R.id.txtProductDescription);
        if (descView != null && currentProductData.containsKey("description")) descView.setText(String.valueOf(currentProductData.get("description")));
        if (seller != null && !currentSellerId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(currentSellerId).get()
                    .addOnSuccessListener(user -> {
                        if (user.exists() && seller.isAttachedToWindow()) seller.setText(textValue(user.getData(), "name", "Người bán"));
                    });
        }
        bindClick(R.id.btnProductBack, () -> showScreen(productReturnScreen));
        bindClick(R.id.btnProductLike, () -> {
            TextView like = findViewById(R.id.btnProductLike);
            if (like == null || currentPostId.isEmpty() || !requireSignedIn()) return;
            boolean save = !favoritePostIds.contains(currentPostId);
            memberOneRepository.setFavorite(currentUid(), currentPostId, save)
                    .addOnSuccessListener(unused -> {
                        if (save) favoritePostIds.add(currentPostId); else favoritePostIds.remove(currentPostId);
                        like.setText(save ? "♥" : "♡");
                    }).addOnFailureListener(this::showFirebaseError);
        });
        bindClick(R.id.btnProductShare, () -> Toast.makeText(this, "Đã sao chép liên kết sản phẩm.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnProductChat, () -> startChatWithPeer(currentSellerId, currentPostId, R.layout.activity_product_detail));
        bindClick(R.id.btnAddToCart, () -> {
            if (currentPostId.isEmpty() || !requireSignedIn()) return;
            memberTwoRepository.addCartItem(currentUid(), currentPostId, cartDataForCurrentProduct())
                    .addOnSuccessListener(unused -> Toast.makeText(this, "Đã thêm vào giỏ hàng.", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(this::showFirebaseError);
        });
        bindClick(R.id.btnBuyNow, () -> {
            if (currentPostId.isEmpty() || !requireSignedIn()) return;
            checkoutReturnScreen = R.layout.activity_product_detail;
            showScreen(R.layout.activity_checkout);
        });
        bindClick(R.id.btnRequestFree, () -> {
            if (currentPostId.isEmpty() || !requireSignedIn()) return;
            showScreen(R.layout.activity_requestfree);
        });
        bindClick(R.id.btnViewSellerProfile, () -> Toast.makeText(this, "Hồ sơ người đăng.", Toast.LENGTH_SHORT).show());
        bindClick(R.id.btnProductReadMore, () -> {
            TextView descText = findViewById(R.id.txtProductDescription);
            TextView toggle = findViewById(R.id.btnProductReadMore);
            if (descText != null && toggle != null) {
                boolean expanded = descText.getMaxLines() <= 4;
                descText.setMaxLines(expanded ? Integer.MAX_VALUE : 4);
                toggle.setText(expanded ? "Thu gọn" : "Xem thêm");
            }
        });
        applyProductKindUi();
    }

    private void updateProductImageIndex(TextView indicator) {
        if (indicator == null) return;
        Object raw = currentProductData.get("imageUrls");
        int count = raw instanceof List ? ((List<?>) raw).size() : 0;
        indicator.setText(count == 0 ? "1 / 1" : (currentProductImageIndex + 1) + " / " + count);
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
}