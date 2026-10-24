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
import android.content.Intent;
import vn.edu.ueh.thudinh.fashion2hand.ui.admin.AdminLoginActivity;
import vn.edu.ueh.thudinh.fashion2hand.repository.admin.AdminRepository;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import vn.edu.ueh.thudinh.fashion2hand.repository.AuthRepository;
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
import com.google.firebase.functions.FirebaseFunctions;
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
    private final TransactionScreens transactionScreens = new TransactionScreens(this, new TransactionScreens.Host() {
        public String uid() { return currentUid(); }
        public String postId() { return currentPostId; }
        public String orderId() { return currentOrderId; }
        public String requestId() { return currentFreeRequestId; }
        public boolean showing(int layout) { return currentScreen == layout; }
        public boolean signedIn() { return requireSignedIn(); }
        public boolean checkoutFromCart() { return checkoutReturnScreen == R.layout.activity_cart; }
        public void open(int layout) {
            if (layout == R.layout.activity_report_issue && !currentOrderId.equals(reportEvidenceOrderId)) {
                reportEvidenceOrderId = currentOrderId; reportEvidenceItems.clear(); reportEvidenceCount = 0;
            }
            showScreen(layout);
        }
        public void openOrder(String id) { openTransactionDestination(R.layout.activity_order_detail, () -> { currentOrderId = id; activeOrder = null; }); }
        public void openRequest(String id) { openTransactionDestination(R.layout.activity_request_status, () -> currentFreeRequestId = id); }
        public void checkout(String id) { openTransactionDestination(R.layout.activity_checkout, () -> { currentPostId = id; checkoutReturnScreen = R.layout.activity_cart; }); }
        public void checkoutCart(java.util.List<String> ids) { openTransactionDestination(R.layout.activity_checkout, () -> {
            currentPostId = ""; checkoutReturnScreen = R.layout.activity_cart; transactionScreens.selectCartCheckout(ids);
        }); }
        public void orders(boolean seller) { orderRoleFilter = seller ? "seller" : "buyer"; showScreen(R.layout.activity_orders); }
        public void back() { goBack(); }
        public void orderSnapshot(DocumentSnapshot order) { activeOrder = order; }
        public void listen(ListenerRegistration listener) { addScreenListener(listener); }
        public void error(Exception error) { showFirebaseError(error); }
        public void photo(Map<String, Object> data, ImageView target) {
            Object raw = data.get("imageUrls");
            if (!(raw instanceof List) || ((List<?>) raw).isEmpty() || !(((List<?>) raw).get(0) instanceof String)) return;
            com.google.firebase.storage.StorageReference ref = storageImageReference((String) ((List<?>) raw).get(0));
            if (ref == null) return;
            Object tag = target.getTag();
            ref.getBytes(5 * 1024 * 1024).addOnSuccessListener(bytes -> {
                if (!target.isAttachedToWindow() || !java.util.Objects.equals(tag, target.getTag())) return;
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) target.setImageBitmap(bitmap);
            });
        }
        public void chat(String peerId, String postId) { startChatWithPeer(peerId, postId, currentScreen); }
    });
    private String reportEvidenceOrderId = "";
    private final java.util.ArrayDeque<ScreenEntry> backStack = new java.util.ArrayDeque<>();
    private boolean restoringScreen;
    private String phoneVerificationId = "";
    private com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken phoneResendToken;
    private long phoneResendAt;
    private boolean contactBusy;
    private EmulatorOtpNotifications emulatorOtpNotifications;
    private String verificationRoleUid = "";
    private boolean verificationRoleResolved;
    private boolean verificationRoleLoading;
    private boolean verificationRoleAdmin;
    private int verificationRoleGeneration;
    private boolean savingListing;
    private String pendingProductLink = "";
    private String viewedSellerId = "";
    private final ArrayList<ListenerRegistration> favoritePostListeners = new ArrayList<>();
    private final ArrayList<DocumentSnapshot> favoritePosts = new ArrayList<>();

    private static final class ScreenEntry {
        int layout;
        String postId, sellerId, viewedSellerId, name, emoji, price, chatId, peerId, orderId, requestId;
        boolean free;
        Map<String, Object> product;
        android.util.SparseArray<android.os.Parcelable> views;
        Bundle draft;
    }

    private final AuthRepository authRepository = new AuthRepository();
    private final MemberTwoRepository memberTwoRepository = MemberTwoRepository.getInstance();
    private final MemberOneRepository memberOneRepository = MemberOneRepository.getInstance();
    private final AccountScreens accountScreens = new AccountScreens(this, new AccountScreens.Host() {
        public String uid() { return currentUid(); }
        public void open(int screen) { showScreen(screen); }
        public void listen(ListenerRegistration listener) { addScreenListener(listener); }
        public void pick(String slot) { showIdentityPicker(slot); }
        public boolean restoringDraft() { return restoredForm != null; }
    });
    private final ArrayList<ListenerRegistration> screenListeners = new ArrayList<>();
    private vn.edu.ueh.thudinh.fashion2hand.repository.ChatRepository.MessageFeed chatFeed;
    private final ArrayList<DocumentSnapshot> activePosts = new ArrayList<>();
    private boolean postsAwaitingServer;
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
    private Bundle restoredForm;
    private boolean authenticationBusy, profileBusy, messageBusy;
    private String messageFilter = "all";
    private boolean ordersAwaitingServer=true;
    private String ordersLoadError="";
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
        super.onCreate(savedInstanceState);
        emulatorOtpNotifications = new EmulatorOtpNotifications(this);

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
        if (savedInstanceState != null) { transactionScreens.restore(savedInstanceState.getBundle("transactions")); if(java.util.Objects.equals(savedInstanceState.getString("sessionUid"),currentUid()))accountScreens.restore(savedInstanceState.getBundle("accountScreens")); identityPhotoSlot=savedInstanceState.getString("identityPhotoSlot", "front"); }
        if (savedInstanceState != null && savedInstanceState.getBundle("currentEntry") != null
                && java.util.Objects.equals(savedInstanceState.getString("sessionUid"), currentUid())) {
            otpFlow = savedInstanceState.getString("otpFlow", "password_reset");
            otpTarget = savedInstanceState.getString("otpTarget", "");
            phoneVerificationId = savedInstanceState.getString("phoneVerificationId", "");
            phoneResendToken = savedInstanceState.getParcelable("phoneResendToken");
            phoneResendAt = savedInstanceState.getLong("phoneResendAt");
            pendingProductLink = savedInstanceState.getString("pendingProductLink", "");
            checkoutReturnScreen = savedInstanceState.getInt("checkoutReturnScreen", R.layout.activity_cart);
            orderRoleFilter=savedInstanceState.getString("orderRoleFilter","buyer"); orderStatusFilter=savedInstanceState.getString("orderStatusFilter","all"); orderSearchQuery=savedInstanceState.getString("orderSearchQuery","");
            messageFilter=savedInstanceState.getString("messageFilter","all"); messageSearchQuery=savedInstanceState.getString("messageSearchQuery","");
            selectedCategory=savedInstanceState.getString("selectedCategory","Tất cả"); notificationFilter=savedInstanceState.getString("notificationFilter","all"); hideReadNotifications=savedInstanceState.getBoolean("hideReadNotifications");
            editingAddress=savedInstanceState.getBoolean("editingAddress"); editingSecondaryAddress=savedInstanceState.getBoolean("editingSecondaryAddress");
            reportEvidenceOrderId=savedInstanceState.getString("reportEvidenceOrderId","");
            ArrayList<android.os.Parcelable> evidence=savedInstanceState.getParcelableArrayList("reportEvidence"); if(evidence!=null)reportEvidenceItems.addAll(evidence);reportEvidenceCount=reportEvidenceItems.size();
            selectedProfilePhotoUri=savedInstanceState.getParcelable("profilePhoto");
            if(selectedProfilePhotoUri!=null)try(java.io.InputStream stream=getContentResolver().openInputStream(selectedProfilePhotoUri)){selectedProfilePhoto=android.graphics.BitmapFactory.decodeStream(stream);}catch(java.io.IOException ignored){}
            listingDraft = unpackMap(savedInstanceState.getBundle("listingDraft"));
            editingListing = savedInstanceState.getBoolean("editingListing");
            editingPostId = savedInstanceState.getString("editingPostId", "");
            ArrayList<android.os.Parcelable> photos = savedInstanceState.getParcelableArrayList("listingPhotos");
            if (photos != null) selectedListingPhotos.addAll(photos);
            selectedListingPhoto = savedInstanceState.getParcelable("listingPhoto");
            capturedListingPhoto = savedInstanceState.getParcelable("capturedListingPhoto");
            ArrayList<Bundle> history = savedInstanceState.getParcelableArrayList("screenHistory");
            if (history != null) for (Bundle entry : history) backStack.addLast(unpackScreen(entry));
            ScreenEntry screen = unpackScreen(savedInstanceState.getBundle("currentEntry"));
            if (screen.layout == R.layout.activity_splash) screen.layout = authRepository.getCurrentUser() == null ? R.layout.activity_login : R.layout.activity_home;
            restoreScreenEntry(screen);
        } else {
            showScreen(R.layout.activity_splash);
            openIncomingProduct(getIntent());
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed() && currentScreen == R.layout.activity_splash) {
                    showScreen(authRepository.getCurrentUser() == null ? R.layout.activity_login : R.layout.activity_home);
                    openPendingProduct();
                }
            }, 1200);
        }

        // Đăng ký bộ xử lý nút Back theo chuẩn AndroidX mới
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
    }

    private void showScreen(int layoutId) {
        FirebaseUser contactUser = authRepository.getCurrentUser();
        boolean protectedAction = layoutId == R.layout.activity_home || layoutId == R.layout.activity_sell
                || layoutId == R.layout.activity_sell_type || layoutId == R.layout.activity_sell_details
                || layoutId == R.layout.activity_sell_preview || layoutId == R.layout.activity_checkout
                || layoutId == R.layout.activity_requestfree;
        if ((protectedAction || layoutId == R.layout.activity_contact_required)
                && contactUser != null && !contactUser.isEmailVerified()) {
            String uid = contactUser.getUid();
            if (!uid.equals(verificationRoleUid)) { resetVerificationRole(); verificationRoleUid = uid; }
            if (!verificationRoleResolved) {
                if (!verificationRoleLoading) {
                    verificationRoleLoading = true;
                    int generation = verificationRoleGeneration, destination = layoutId;
                    showScreen(R.layout.activity_splash);
                    authRepository.isActiveAdmin()
                            .addOnSuccessListener(admin -> finishVerificationRole(generation, uid, destination, admin))
                            .addOnFailureListener(error -> finishVerificationRole(generation, uid, destination, false));
                }
                return;
            }
            if (!verificationRoleAdmin) layoutId = R.layout.activity_contact_required;
            else if (layoutId == R.layout.activity_contact_required) layoutId = R.layout.activity_home;
        } else if (layoutId == R.layout.activity_contact_required && contactUser != null && contactUser.isEmailVerified()) {
            layoutId = R.layout.activity_home;
        }
        if(layoutId==R.layout.activity_login && authRepository.getCurrentUser()==null) {
            accountScreens.clearSession(); resetVerificationRole();
        }
        if (!restoringScreen && currentScreen != layoutId && currentScreen != R.layout.activity_splash) {
            if (layoutId == R.layout.activity_home || layoutId == R.layout.activity_login
                    || layoutId == R.layout.activity_contact_required) backStack.clear();
            else if (!backStack.isEmpty() && backStack.peek().layout == layoutId) {
                goBack();
                return;
            } else backStack.push(captureScreen());
        }
        clearScreenListeners();
        currentScreen = layoutId;
        if(!restoringScreen)restoredForm=null;
        if (transactionScreens.owns(layoutId)) transactionScreens.prepare(layoutId);
        else setContentView(layoutId);
        applySystemBarInsets();
        if (layoutId == R.layout.activity_contact_required) { bindContactRequired(); return; }
        if (transactionScreens.owns(layoutId)) {
            transactionScreens.bind(layoutId);
            bindVisibleBackButtons(findViewById(android.R.id.content));
            return;
        }
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
        if (layoutId == R.layout.activity_seller_profile) bindSellerProfile();
        bindVisibleBackButtons(findViewById(android.R.id.content));
    }

    private ScreenEntry captureScreen() {
        ScreenEntry entry = new ScreenEntry();
        entry.layout = currentScreen;
        entry.postId = currentPostId; entry.sellerId = currentSellerId;
        entry.viewedSellerId = viewedSellerId;
        entry.name = currentProductName; entry.emoji = currentProductEmoji; entry.price = currentProductPrice;
        entry.free = currentProductIsFree; entry.product = new HashMap<>(currentProductData);
        entry.chatId = currentChatId; entry.peerId = currentPeerId; entry.orderId = currentOrderId;
        entry.requestId = currentFreeRequestId;
        entry.draft = UiFormState.capture(findViewById(android.R.id.content));
        entry.views = new android.util.SparseArray<>();
        findViewById(android.R.id.content).saveHierarchyState(entry.views);
        return entry;
    }

    private void goBack() {
        // Preserve listing fields before leaving a step, including toolbar Back.
        if (currentScreen == R.layout.activity_sell_details) captureSellDetails();
        if (currentScreen == R.layout.activity_sell_type) captureSellType();
        if (backStack.isEmpty()) {
            if (currentScreen == R.layout.activity_home || currentScreen == R.layout.activity_login) finish();
            else navigateRoot(authRepository.getCurrentUser() == null ? R.layout.activity_login : R.layout.activity_home);
            return;
        }
        ScreenEntry entry = backStack.pop();
        restoreScreenEntry(entry);
    }

    private void restoreScreenEntry(ScreenEntry entry) {
        restoredForm=entry.draft;
        currentPostId = entry.postId; currentSellerId = entry.sellerId; viewedSellerId = entry.viewedSellerId;
        currentProductName = entry.name; currentProductEmoji = entry.emoji; currentProductPrice = entry.price;
        currentProductIsFree = entry.free; currentProductData = entry.product;
        currentChatId = entry.chatId; currentPeerId = entry.peerId; currentOrderId = entry.orderId;
        currentFreeRequestId = entry.requestId;
        restoringScreen = true;
        showScreen(entry.layout);
        restoringScreen = false;
        if (entry.views != null) findViewById(android.R.id.content).restoreHierarchyState(entry.views);
        UiFormState.restore(findViewById(android.R.id.content),entry.draft);
    }

    private static Bundle packMap(Map<String, Object> data) {
        Bundle result = new Bundle();
        for (Map.Entry<String, Object> item : data.entrySet()) {
            Object value = item.getValue(); String key = item.getKey();
            if (value instanceof String) result.putString(key, (String) value);
            else if (value instanceof Boolean) result.putBoolean(key, (Boolean) value);
            else if (value instanceof Double || value instanceof Float) result.putDouble(key, ((Number) value).doubleValue());
            else if (value instanceof Number) result.putLong(key, ((Number) value).longValue());
            else if (value instanceof com.google.firebase.Timestamp) {
                Bundle timestamp = new Bundle(); timestamp.putLong("seconds", ((com.google.firebase.Timestamp) value).getSeconds());
                timestamp.putInt("nanoseconds", ((com.google.firebase.Timestamp) value).getNanoseconds()); result.putBundle(key, timestamp);
            } else if (value instanceof List) {
                ArrayList<String> strings = new ArrayList<>();
                for (Object element : (List<?>) value) if (element instanceof String) strings.add((String) element);
                result.putStringArrayList(key, strings);
            }
        }
        return result;
    }

    private static Map<String, Object> unpackMap(Bundle bundle) {
        Map<String, Object> data = new HashMap<>();
        if (bundle != null) for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof Bundle) value = new com.google.firebase.Timestamp(((Bundle) value).getLong("seconds"), ((Bundle) value).getInt("nanoseconds"));
            data.put(key, value);
        }
        return data;
    }

    private Bundle packScreen(ScreenEntry entry) {
        Bundle bundle = new Bundle(); bundle.putInt("layout", entry.layout);
        bundle.putString("postId", entry.postId); bundle.putString("sellerId", entry.sellerId);
        bundle.putString("viewedSellerId", entry.viewedSellerId); bundle.putString("name", entry.name);
        bundle.putString("emoji", entry.emoji); bundle.putString("price", entry.price); bundle.putBoolean("free", entry.free);
        bundle.putString("chatId", entry.chatId); bundle.putString("peerId", entry.peerId); bundle.putString("orderId", entry.orderId);
        bundle.putString("requestId", entry.requestId);
        bundle.putBundle("product", packMap(entry.product)); bundle.putSparseParcelableArray("views", entry.views);
        bundle.putBundle("draft",entry.draft);return bundle;
    }

    private ScreenEntry unpackScreen(Bundle bundle) {
        ScreenEntry entry = new ScreenEntry();
        entry.layout = bundle.getInt("layout", R.layout.activity_home);
        entry.postId = bundle.getString("postId", ""); entry.sellerId = bundle.getString("sellerId", "");
        entry.viewedSellerId = bundle.getString("viewedSellerId", ""); entry.name = bundle.getString("name", "");
        entry.emoji = bundle.getString("emoji", "👕"); entry.price = bundle.getString("price", ""); entry.free = bundle.getBoolean("free");
        entry.chatId = bundle.getString("chatId", ""); entry.peerId = bundle.getString("peerId", ""); entry.orderId = bundle.getString("orderId", "");
        entry.requestId = bundle.getString("requestId", "");
        entry.product = unpackMap(bundle.getBundle("product")); entry.views = bundle.getSparseParcelableArray("views");
        entry.draft=bundle.getBundle("draft");
        return entry;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("orderRoleFilter",orderRoleFilter);state.putString("orderStatusFilter",orderStatusFilter);state.putString("orderSearchQuery",orderSearchQuery);
        state.putString("messageFilter",messageFilter);state.putString("messageSearchQuery",messageSearchQuery);state.putString("selectedCategory",selectedCategory);state.putString("notificationFilter",notificationFilter);state.putBoolean("hideReadNotifications",hideReadNotifications);
        state.putBoolean("editingAddress",editingAddress);state.putBoolean("editingSecondaryAddress",editingSecondaryAddress);
        state.putString("reportEvidenceOrderId",reportEvidenceOrderId);state.putParcelableArrayList("reportEvidence",cachePhotos(reportEvidenceItems,"report-state"));
        ArrayList<Object> profileImages=new ArrayList<>();if(selectedProfilePhotoUri!=null)profileImages.add(selectedProfilePhotoUri);else if(selectedProfilePhoto!=null)profileImages.add(selectedProfilePhoto);
        ArrayList<android.os.Parcelable> profileSaved=cachePhotos(profileImages,"profile-state");if(!profileSaved.isEmpty())state.putParcelable("profilePhoto",profileSaved.get(0));
        state.putBundle("transactions", transactionScreens.save());
        state.putBundle("accountScreens", accountScreens.save());state.putString("identityPhotoSlot",identityPhotoSlot);
        state.putInt("checkoutReturnScreen", checkoutReturnScreen);
        state.putString("sessionUid", currentUid());
        state.putString("otpFlow", otpFlow); state.putString("otpTarget", otpTarget);
        state.putString("phoneVerificationId", phoneVerificationId); state.putParcelable("phoneResendToken", phoneResendToken);
        state.putLong("phoneResendAt", phoneResendAt); state.putString("pendingProductLink", pendingProductLink);
        state.putBundle("listingDraft", packMap(listingDraft)); state.putBoolean("editingListing", editingListing);
        state.putString("editingPostId", editingPostId);
        ArrayList<android.os.Parcelable> photos = new ArrayList<>();
        // Keep large bitmaps out of the saved-state Binder transaction.
        for (int i = 0; i < selectedListingPhotos.size(); i++) {
            Object photo = selectedListingPhotos.get(i);
            if (photo instanceof Uri) photos.add((Uri) photo);
            else if (photo instanceof Bitmap) {
                java.io.File file = new java.io.File(getCacheDir(), "listing-state-" + i + ".jpg");
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    ((Bitmap) photo).compress(Bitmap.CompressFormat.JPEG, 88, output);
                    photos.add(Uri.fromFile(file));
                } catch (java.io.IOException ignored) { /* The form can ask the user to select this photo again. */ }
            }
        }
        state.putParcelable("listingPhoto", photos.isEmpty() ? selectedListingPhoto : photos.get(0));
        state.putParcelableArrayList("listingPhotos", photos);
        ArrayList<Bundle> history = new ArrayList<>(); for (ScreenEntry entry : backStack) history.add(packScreen(entry));
        state.putParcelableArrayList("screenHistory", history); state.putBundle("currentEntry", packScreen(captureScreen()));
        super.onSaveInstanceState(state);
    }

    private ArrayList<android.os.Parcelable> cachePhotos(java.util.List<Object> images,String prefix){
        ArrayList<android.os.Parcelable> saved=new ArrayList<>();
        for(int i=0;i<images.size();i++){Object item=images.get(i);if(item instanceof Uri)saved.add((Uri)item);else if(item instanceof Bitmap){java.io.File file=new java.io.File(getCacheDir(),prefix+"-"+i+".jpg");try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){if(((Bitmap)item).compress(Bitmap.CompressFormat.JPEG,88,stream))saved.add(Uri.fromFile(file));}catch(java.io.IOException ignored){}}}
        return saved;
    }

    private void navigateRoot(int layoutId) {
        backStack.clear();
        restoringScreen = true;
        showScreen(layoutId);
        restoringScreen = false;
    }

    private void openTransactionDestination(int layoutId, Runnable update) {
        backStack.push(captureScreen());
        update.run();
        restoringScreen = true;
        showScreen(layoutId);
        restoringScreen = false;
    }

    private void bindVisibleBackButtons(View view) {
        if (view.getId() != View.NO_ID) {
            String name;
            try { name = getResources().getResourceEntryName(view.getId()); }
            catch (android.content.res.Resources.NotFoundException ignored) { name = ""; }
            if (view.getId() != R.id.btnIdentityBack && (name.equals("btnBack") || name.endsWith("Back") || name.endsWith("Previous")))
                view.setOnClickListener(v -> goBack());
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            bindVisibleBackButtons(((ViewGroup) view).getChildAt(i));
    }

    private void addScreenListener(ListenerRegistration listener) {
        if (listener != null) screenListeners.add(listener);
    }

    private void clearScreenListeners() {
        if (chatFeed != null) { chatFeed.removeObservers(this); chatFeed = null; }
        transactionScreens.invalidate();
        accountScreens.invalidate();
        for (ListenerRegistration listener : screenListeners) listener.remove();
        screenListeners.clear();
        for (ListenerRegistration listener : favoritePostListeners) listener.remove();
        favoritePostListeners.clear();
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
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + Math.max(bars.bottom,keyboard.bottom)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void resetVerificationRole() {
        verificationRoleGeneration++;
        verificationRoleUid = "";
        verificationRoleResolved = false;
        verificationRoleLoading = false;
        verificationRoleAdmin = false;
    }

    private void finishVerificationRole(int generation, String uid, int destination, boolean admin) {
        if (generation != verificationRoleGeneration || !uid.equals(currentUid()) || isFinishing() || isDestroyed()) return;
        verificationRoleResolved = true;
        verificationRoleLoading = false;
        verificationRoleAdmin = admin;
        if (currentScreen == R.layout.activity_splash) showScreen(destination);
    }

    private void bindLogin() {
        EditText email = findViewById(R.id.edtLoginEmail);
        EditText password = findViewById(R.id.edtLoginPassword);

        bindClick(R.id.btnLogin, () -> {
            if(authenticationBusy)return;
            String emailValue = email == null ? "" : email.getText().toString().trim();
            String passwordValue = password == null ? "" : password.getText().toString();

            if (emailValue.isEmpty()) {
                if (email != null) email.setError("Vui lòng nhập email");
                return;
            }
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()){email.setError("Email không hợp lệ");return;}
            if (passwordValue.isEmpty()) {
                if (password != null) password.setError("Vui lòng nhập mật khẩu");
                return;
            }

            if (AdminRepository.getInstance().isAdminEmail(emailValue)) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                return;
            }

            authenticationBusy=true;findViewById(R.id.btnLogin).setEnabled(false);
            authRepository.signInWithEmail(emailValue, passwordValue)
                    .addOnSuccessListener(result -> ensureUserProfile(result.getUser(), "", () -> showScreen(R.layout.activity_home)))
                    .addOnFailureListener(this::showFirebaseError)
                    .addOnCompleteListener(done->{authenticationBusy=false;View button=findViewById(R.id.btnLogin);if(button!=null)button.setEnabled(true);});
        });

        bindClick(R.id.btnOpenRegister, () -> showScreen(R.layout.activity_register));
        bindClick(R.id.btnOpenAdminLogin, () -> startActivity(new Intent(this, AdminLoginActivity.class)));
        bindClick(R.id.btnGoogleLogin, this::startGoogleSignIn);
        bindClick(R.id.btnForgotPassword, () -> showScreen(R.layout.activity_forgot_password));
    }

    private void startGoogleSignIn() {
        if(authenticationBusy)return;
        int clientIdResource = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (clientIdResource == 0) {
            Toast.makeText(this, "Đăng nhập Google chưa sẵn sàng. Vui lòng thử lại sau.", Toast.LENGTH_LONG).show();
            return;
        }
        String serverClientId = getString(clientIdResource);
        GetSignInWithGoogleOption googleOption = new GetSignInWithGoogleOption.Builder(serverClientId).build();
        authenticationBusy=true;
        View googleButton=findViewById(R.id.btnGoogleLogin);if(googleButton!=null)googleButton.setEnabled(false);
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
        CredentialManager credentialManager = CredentialManager.create(this);
        credentialManager.getCredentialAsync(this, request, new CancellationSignal(), getMainExecutor(),
                new CredentialManagerCallback<androidx.credentials.GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(androidx.credentials.GetCredentialResponse response) {
                        if(isFinishing()||isDestroyed())return;
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            finishGoogleSignInUi();
                            Toast.makeText(MainActivity.this, "Không nhận được thông tin tài khoản Google hợp lệ.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        final GoogleIdTokenCredential googleCredential;
                        try{googleCredential = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());}
                        catch(Exception error){finishGoogleSignInUi();Toast.makeText(MainActivity.this,"Không đọc được tài khoản Google. Vui lòng thử lại.",Toast.LENGTH_LONG).show();return;}
                        com.google.firebase.auth.AuthCredential firebaseCredential = com.google.firebase.auth.GoogleAuthProvider
                                .getCredential(googleCredential.getIdToken(), null);
                        authRepository.signInWithGoogleCredential(firebaseCredential, new AuthRepository.AuthCallback() {
                            @Override public void onSuccess(FirebaseUser user) {
                                finishGoogleSignInUi();if(isFinishing()||isDestroyed())return;
                                ensureUserProfile(user, googleCredential.getDisplayName(), () -> showScreen(R.layout.activity_home));
                            }
                            @Override public void onFailure(String message) {
                                finishGoogleSignInUi();if(isFinishing()||isDestroyed())return;
                                Toast.makeText(MainActivity.this, message == null ? "Đăng nhập Google thất bại." : message, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                    @Override public void onError(GetCredentialException exception) {
                        finishGoogleSignInUi();if(isFinishing()||isDestroyed())return;
                        if(exception instanceof androidx.credentials.exceptions.GetCredentialCancellationException)return;
                        String message=exception instanceof androidx.credentials.exceptions.NoCredentialException
                                ?"Thêm tài khoản Google vào thiết bị rồi thử lại.":"Không thể đăng nhập Google. Kiểm tra kết nối và thử lại.";
                        Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void finishGoogleSignInUi(){
        authenticationBusy=false;
        View button=findViewById(R.id.btnGoogleLogin);if(button!=null)button.setEnabled(true);
    }

    private void bindRegister() {
        bindClick(R.id.btnOpenLogin, () -> showScreen(R.layout.activity_login));
        bindClick(R.id.btnCreateAccount, () -> {
            if(authenticationBusy)return;
            EditText name = findViewById(R.id.edtRegisterName);
            EditText email = findViewById(R.id.edtRegisterEmail);
            EditText password = findViewById(R.id.edtRegisterPassword);
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng nhập họ tên"); return; }
            if (email.getText().toString().trim().isEmpty()) { email.setError("Vui lòng nhập email"); return; }
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()){email.setError("Email không hợp lệ");return;}
            if (password.getText().length() < 6) { password.setError("Mật khẩu cần ít nhất 6 ký tự"); return; }
            authenticationBusy=true;findViewById(R.id.btnCreateAccount).setEnabled(false);
            authRepository.createAccountWithEmail(email.getText().toString().trim(), password.getText().toString())
                    .addOnSuccessListener(result -> ensureUserProfile(result.getUser(), name.getText().toString().trim(),
                            () -> {
                                showScreen(R.layout.activity_home);
                                result.getUser().sendEmailVerification().addOnSuccessListener(unused -> Toast.makeText(this,
                                        "Đã gửi email xác minh. Kiểm tra hộp thư và thư rác, rồi quay lại bấm kiểm tra.", Toast.LENGTH_LONG).show())
                                        .addOnFailureListener(this::showFirebaseError);
                            }))
                    .addOnFailureListener(this::showFirebaseError)
                    .addOnCompleteListener(done->{authenticationBusy=false;View button=findViewById(R.id.btnCreateAccount);if(button!=null)button.setEnabled(true);});
        });
    }

    private void ensureUserProfile(FirebaseUser user, String displayName, Runnable onReady) {
        resetVerificationRole();
        if (user == null) {
            Toast.makeText(this, "Không lấy được tài khoản Firebase.", Toast.LENGTH_LONG).show();
            return;
        }
        com.google.firebase.firestore.DocumentReference ref = FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                onReady.run();
                syncVerifiedContact(); openPendingProduct();
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
            ref.set(profile).addOnSuccessListener(unused -> { onReady.run(); syncVerifiedContact(); openPendingProduct(); }).addOnFailureListener(this::showFirebaseError);
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

    private android.content.SharedPreferences contactPreferences() {
        return getSharedPreferences("verified_contact_" + currentUid(), MODE_PRIVATE);
    }

    private void setContactBusy(boolean busy) {
        contactBusy = busy;
        for (int id : new int[]{R.id.btnSubmitOtp, R.id.btnResendOtp, R.id.btnSendPhoneOtp, R.id.btnContinueChangeEmail}) {
            View button = findViewById(id);
            if (button != null) button.setEnabled(!busy);
        }
    }

    private void contactError(Exception error, Runnable retry) {
        setContactBusy(false);
        if (error instanceof com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            boolean passwordProvider = false;
            FirebaseUser user = authRepository.getCurrentUser();
            if (user != null) for (com.google.firebase.auth.UserInfo provider : user.getProviderData())
                if ("password".equals(provider.getProviderId())) passwordProvider = true;
            if (!passwordProvider) {
                new android.app.AlertDialog.Builder(this).setTitle("Xác nhận tài khoản Google")
                        .setMessage("Đăng nhập Google lại để tiếp tục cập nhật thông tin.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Tiếp tục", (d, w) -> startGoogleReauthentication(retry)).show();
                return;
            }
            EditText password = new EditText(this);
            password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            password.setHint("Mật khẩu hiện tại");
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("Xác nhận tài khoản")
                    .setView(password).setNegativeButton("Hủy", null).setPositiveButton("Xác nhận", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
                if (password.getText().length() == 0) { password.setError("Nhập mật khẩu hiện tại"); return; }
                dialog.getButton(-1).setEnabled(false);
                authRepository.reauthenticate(password.getText().toString())
                        .addOnSuccessListener(unused -> { dialog.dismiss(); retry.run(); })
                        .addOnFailureListener(e -> { dialog.getButton(-1).setEnabled(true); password.setError("Xác thực thất bại. Vui lòng thử lại."); });
            }));
            dialog.show();
        } else showFirebaseError(error);
    }

    private void startGoogleReauthentication(Runnable retry) {
        int clientId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (clientId == 0) { showFirebaseError(new IllegalStateException("Chưa cấu hình đăng nhập Google.")); return; }
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(
                new GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(getString(clientId)).build()).build();
        CredentialManager.create(this).getCredentialAsync(this, request, new CancellationSignal(), getMainExecutor(),
                new CredentialManagerCallback<androidx.credentials.GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(androidx.credentials.GetCredentialResponse response) {
                        try {
                            Credential raw = response.getCredential();
                            if (!(raw instanceof CustomCredential) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(raw.getType()))
                                throw new IllegalStateException("Thông tin Google không hợp lệ.");
                            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(((CustomCredential) raw).getData());
                            FirebaseUser user = authRepository.getCurrentUser();
                            if (user == null) return;
                            user.reauthenticate(com.google.firebase.auth.GoogleAuthProvider.getCredential(google.getIdToken(), null))
                                    .addOnSuccessListener(unused -> retry.run()).addOnFailureListener(MainActivity.this::showFirebaseError);
                        } catch (Exception e) { showFirebaseError(e); }
                    }
                    @Override public void onError(GetCredentialException e) { showFirebaseError(e); }
                });
    }

    private void bindOtpVerification() {
        boolean emailChange = "email_change".equals(otpFlow);
        setTextIfPresent(R.id.txtOtpTitle, emailChange ? "Xác nhận email mới" : "Xác minh số điện thoại");
        setTextIfPresent(R.id.txtOtpDescription, emailChange
                ? "Mở liên kết trong email mới, sau đó quay lại nhấn kiểm tra."
                : BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS
                        ? "OTP mô phỏng được hiển thị trên thanh thông báo. Nhập mã 6 chữ số để tiếp tục:"
                        : "Nhập mã OTP 6 chữ số được gửi qua SMS:");
        setTextIfPresent(R.id.txtOtpTarget, otpTarget);
        findViewById(R.id.edtOtpCode).setVisibility(emailChange ? View.GONE : View.VISIBLE);
        setTextIfPresent(R.id.btnSubmitOtp, emailChange ? "Tôi đã xác nhận — kiểm tra" : "Xác nhận");
        setTextIfPresent(R.id.btnResendOtp, emailChange ? "Gửi lại email xác nhận" : "Gửi lại mã SMS");
        bindClick(R.id.btnResendOtp, () -> {
            if (contactBusy) return;
            if (emailChange) requestEmailChange(); else sendPhoneOtp(true);
        });
        bindClick(R.id.btnSubmitOtp, () -> {
            if (contactBusy) return;
            if (emailChange) { checkChangedEmail(true); return; }
            EditText code = findViewById(R.id.edtOtpCode);
            if (!code.getText().toString().matches("[0-9]{6}")) { code.setError("Nhập đủ 6 chữ số"); return; }
            if (phoneVerificationId.isEmpty()) { code.setError("Phiên OTP đã hết hạn. Vui lòng gửi lại mã."); return; }
            completePhoneVerification(com.google.firebase.auth.PhoneAuthProvider.getCredential(phoneVerificationId, code.getText().toString()));
        });
        setContactBusy(contactBusy);
        if (!emailChange && !phoneVerificationId.isEmpty())
            emulatorOtpNotifications.show(otpTarget, phoneVerificationId);
    }

    private void sendPhoneOtp(boolean resend) {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATORS) {
            Toast.makeText(this, "SMS thật đang tạm tắt. Bạn chỉ cần xác minh email.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!requireSignedIn() || contactBusy) return;
        if (android.os.SystemClock.elapsedRealtime() < phoneResendAt) {
            Toast.makeText(this, "Vui lòng chờ 60 giây trước khi gửi lại mã.", Toast.LENGTH_SHORT).show(); return;
        }
        String owner = currentUid();
        String requestedPhone = otpTarget;
        emulatorOtpNotifications.clear();
        phoneVerificationId = "";
        setContactBusy(true);
        authRepository.sendPhoneCode(this, requestedPhone, resend ? phoneResendToken : null,
                new com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override public void onVerificationCompleted(com.google.firebase.auth.PhoneAuthCredential credential) {
                        if (!owner.equals(currentUid()) || !requestedPhone.equals(otpTarget)) return;
                        completePhoneVerification(credential);
                    }
                    @Override public void onVerificationFailed(com.google.firebase.FirebaseException error) {
                        if (!owner.equals(currentUid())) return;
                        setContactBusy(false); showFirebaseError(error);
                    }
                    @Override public void onCodeSent(String verificationId, com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken token) {
                        if (!owner.equals(currentUid()) || !requestedPhone.equals(otpTarget)) return;
                        phoneVerificationId = verificationId; phoneResendToken = token;
                        phoneResendAt = android.os.SystemClock.elapsedRealtime() + 60000;
                        setContactBusy(false);
                        if (currentScreen == R.layout.activity_verify_phone || currentScreen == R.layout.activity_otp_verification)
                            showScreen(R.layout.activity_otp_verification);
                        emulatorOtpNotifications.show(requestedPhone, verificationId);
                    }
                    @Override public void onCodeAutoRetrievalTimeOut(String verificationId) { setContactBusy(false); }
                });
    }

    private void completePhoneVerification(com.google.firebase.auth.PhoneAuthCredential credential) {
        if (!requireSignedIn()) return;
        setContactBusy(true);
        authRepository.verifyPhone(credential).addOnSuccessListener(unused -> {
            emulatorOtpNotifications.clear();
            setContactBusy(false); phoneVerificationId = ""; phoneResendToken = null;
            syncVerifiedContact();
            authRepository.getCurrentUser().getIdToken(true).addOnSuccessListener(token -> showScreen(R.layout.activity_home))
                    .addOnFailureListener(this::showFirebaseError);
            Toast.makeText(this, "Số điện thoại đã được Firebase xác minh.", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(error -> contactError(error, () -> completePhoneVerification(credential)));
    }

    private void bindNewPassword() {
        // Password resets are completed with Firebase's emailed action link.
        navigateRoot(R.layout.activity_forgot_password);
    }

    private void bindPasswordResetSuccess() {
        bindClick(R.id.btnReturnLogin, () -> navigateRoot(R.layout.activity_login));
    }

    private void bindChangeEmail() {
        if (!requireSignedIn()) return;
        setTextIfPresent(R.id.txtCurrentEmail, authRepository.getCurrentUser().getEmail());
        EditText email = findViewById(R.id.edtNewEmail);
        String pending = contactPreferences().getString("pending_email", "");
        if (!pending.isEmpty()) email.setText(pending);
        bindClick(R.id.btnContinueChangeEmail, () -> {
            String value = email.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.setError("Nhập email hợp lệ"); return; }
            if (value.equalsIgnoreCase(authRepository.getCurrentUser().getEmail())) { email.setError("Email mới trùng email hiện tại"); return; }
            otpFlow = "email_change"; otpTarget = value; requestEmailChange();
        });
    }

    private void requestEmailChange() {
        if (!requireSignedIn() || contactBusy) return;
        setContactBusy(true);
        final String requestedEmail = otpTarget;
        authRepository.requestEmailChange(requestedEmail).addOnSuccessListener(unused -> {
            setContactBusy(false);
            contactPreferences().edit().putString("pending_email", requestedEmail).apply();
            showScreen(R.layout.activity_otp_verification);
            Toast.makeText(this, "Đã gửi liên kết xác nhận đến email mới.", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(error -> contactError(error, this::requestEmailChange));
    }

    private void checkChangedEmail(boolean notifyPending) {
        FirebaseUser user = authRepository.getCurrentUser();
        if (user == null || contactBusy) return;
        String pending = contactPreferences().getString("pending_email", "");
        if (pending.isEmpty()) return;
        setContactBusy(true);
        user.reload().addOnSuccessListener(unused -> {
            setContactBusy(false);
            if (pending.equalsIgnoreCase(user.getEmail()) && user.isEmailVerified()) {
                contactPreferences().edit().remove("pending_email").apply();
                syncVerifiedContact();
                navigateRoot(R.layout.activity_profile); showScreen(R.layout.activity_edit_profile);
                Toast.makeText(this, "Email mới đã được xác nhận.", Toast.LENGTH_SHORT).show();
            } else if (notifyPending) Toast.makeText(this, "Chưa xác nhận. Hãy mở liên kết trong email mới.", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(error -> {
            setContactBusy(false);
            if (error instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                authRepository.signOut(); navigateRoot(R.layout.activity_login);
                Toast.makeText(this, "Phiên đăng nhập đã hết hạn. Đăng nhập bằng email mới sau khi xác nhận.", Toast.LENGTH_LONG).show();
            } else showFirebaseError(error);
        });
    }

    private void syncVerifiedContact() {
        FirebaseFunctions.getInstance().getHttpsCallable("syncAccountContact").call()
                .addOnFailureListener(this::showFirebaseError);
    }

    private void bindContactRequired() {
        FirebaseUser user = authRepository.getCurrentUser();
        if (user == null) { showScreen(R.layout.activity_login); return; }
        setTextIfPresent(R.id.txtContactRequiredStatus, "Email: " + user.getEmail()
                + (user.isEmailVerified() ? " — đã xác minh" : " — chưa xác minh")
                + "\nKhông bắt buộc xác minh số điện thoại. SMS thật đang tạm tắt.");
        findViewById(R.id.btnRequiredPhone).setVisibility(
                BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS ? View.VISIBLE : View.GONE);
        setTextIfPresent(R.id.btnRequiredPhone, "Thử OTP số điện thoại (mô phỏng)");
        findViewById(R.id.btnSendVerificationEmail).setEnabled(!user.isEmailVerified());
        bindClick(R.id.btnSendVerificationEmail, () -> {
            View button = findViewById(R.id.btnSendVerificationEmail); button.setEnabled(false);
            user.sendEmailVerification().addOnSuccessListener(unused -> Toast.makeText(this,
                    "Đã gửi liên kết. Mở email xác minh rồi bấm kiểm tra lại.", Toast.LENGTH_LONG).show())
                    .addOnFailureListener(this::showFirebaseError)
                    .addOnCompleteListener(done -> { if (currentScreen == R.layout.activity_contact_required) button.setEnabled(true); });
        });
        bindClick(R.id.btnRequiredPhone, () -> { otpFlow = "phone_verify"; otpTarget = ""; showScreen(R.layout.activity_verify_phone); });
        bindClick(R.id.btnRefreshContactVerification, () -> {
            View button = findViewById(R.id.btnRefreshContactVerification); button.setEnabled(false);
            user.reload().continueWithTask(task -> {
                if (!task.isSuccessful()) throw task.getException();
                return user.getIdToken(true);
            }).addOnSuccessListener(result -> {
                if (!isFinishing() && currentScreen == R.layout.activity_contact_required) {
                    if (user.isEmailVerified()) {
                        syncVerifiedContact();
                        showScreen(R.layout.activity_home);
                    } else Toast.makeText(this, "Email chưa được xác minh. Mở liên kết trong thư rồi kiểm tra lại.", Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(this::showFirebaseError)
                    .addOnCompleteListener(done -> button.setEnabled(true));
        });
        bindClick(R.id.btnRequiredSignOut, () -> { authRepository.signOut(); navigateRoot(R.layout.activity_login); });
    }

    private void bindVerifyPhone() {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATORS) {
            Toast.makeText(this, "SMS thật đang tạm tắt. Bạn chỉ cần xác minh email.", Toast.LENGTH_LONG).show();
            showScreen(R.layout.activity_home);
            return;
        }
        if (!requireSignedIn()) return;
        EditText phone = findViewById(R.id.edtVerifyPhoneNumber);
        String existing = authRepository.getCurrentUser().getPhoneNumber();
        phone.setText(!otpTarget.isEmpty() && !"email_change".equals(otpFlow) ? otpTarget : existing == null ? "" : existing);
        setTextIfPresent(R.id.txtVerifyPhoneTitle, "phone_change".equals(otpFlow) ? "Đổi số điện thoại" : "Xác minh số điện thoại");
        bindClick(R.id.btnSendPhoneOtp, () -> {
            String value = MarketplaceLinks.normalizePhone(phone.getText().toString());
            if (value.isEmpty()) { phone.setError("Nhập số Việt Nam 0… hoặc số quốc tế +…"); return; }
            if (!value.equals(otpTarget)) { phoneResendToken = null; phoneResendAt = 0; }
            otpTarget = value; sendPhoneOtp(false);
        });
        setContactBusy(contactBusy);
    }

    private void bindNavigation() {
        bindClick(R.id.navHome, () -> navigateRoot(R.layout.activity_home));
        bindClick(R.id.navExplore, () -> navigateRoot(R.layout.activity_explore));
        bindClick(R.id.navCreate, () -> {
            editingListing = false; editingPostId = ""; listingDraft.clear(); selectedListingPhotos.clear();
            selectedListingPhoto = null; capturedListingPhoto = null;
            showScreen(R.layout.activity_sell);
        });
        bindClick(R.id.navMessages, () -> navigateRoot(R.layout.activity_messages));
        bindClick(R.id.navProfile, () -> navigateRoot(R.layout.activity_profile));
    }

    private void bindHome() {
        TextView greeting = findViewById(R.id.txtHomeGreeting);
        FirebaseUser signedInUser = authRepository.getCurrentUser();
        if (greeting != null && signedInUser != null) {
            String fallbackName = signedInUser.getDisplayName();
            if (fallbackName == null || fallbackName.trim().isEmpty()) {
                String email = signedInUser.getEmail();
                fallbackName = email == null || email.trim().isEmpty() ? "bạn" : email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
            }
            greeting.setText("Hi, " + fallbackName.trim() + " 👋");
            FirebaseFirestore.getInstance().collection("users").document(signedInUser.getUid()).get()
                    .addOnSuccessListener(profile -> {
                        if (!greeting.isAttachedToWindow()) return;
                        String profileName = profile.getString("name");
                        if (profileName != null && !profileName.trim().isEmpty()) {
                            greeting.setText("Hi, " + profileName.trim() + " 👋");
                        }
                    });
        }
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
        postsAwaitingServer=true;
        showProductLoadState("Đang tải tin đăng…",false);
        addScreenListener(memberTwoRepository.listenActivePosts((snapshots, error) -> {
            if (currentScreen != R.layout.activity_home && currentScreen != R.layout.activity_explore) return;
            if (error != null) { showProductLoadState("Không tải được tin đăng. Kiểm tra kết nối rồi thử lại.",true); return; }
            if(snapshots!=null && snapshots.isEmpty() && snapshots.getMetadata().isFromCache()){postsAwaitingServer=true;showProductLoadState("Đang chờ kết nối với server…",true);return;}
            postsAwaitingServer=false;
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
            if(postsAwaitingServer)return;
            if (currentScreen == R.layout.activity_home) renderHomeProducts();
            else if (currentScreen == R.layout.activity_explore) renderExploreProducts();
        }));
    }

    private void showProductLoadState(String message,boolean retry){
        GridLayout grid=findViewById(currentScreen==R.layout.activity_home?R.id.productGrid:R.id.exploreProductGrid);if(grid==null)return;grid.removeAllViews();addEmptyProductsMessage(grid,message);
        if(retry){Button button=new Button(this);button.setText("Thử lại");button.setOnClickListener(v->{ScreenEntry entry=captureScreen();restoreScreenEntry(entry);});GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=-1;params.columnSpec=GridLayout.spec(0,2);grid.addView(button,params);}
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
        com.google.firebase.storage.StorageReference image = storageImageReference((String) url);
        if (image == null) return;
        image
                .getBytes(10 * 1024 * 1024).addOnSuccessListener(bytes -> {
                    if (!target.isAttachedToWindow()) return;
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) { target.setImageBitmap(bitmap); target.setVisibility(View.VISIBLE); fallback.setVisibility(View.GONE); }
                });
    }

    private com.google.firebase.storage.StorageReference storageImageReference(String value) {
        try {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            Uri uri = Uri.parse(value);
            List<String> path = uri.getPathSegments();
            // Download URLs returned by the local emulator have a local HTTP host.
            // Resolve their bucket/object path through the configured SDK instead.
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && path.size() == 5
                    && "v0".equals(path.get(0)) && "b".equals(path.get(1)) && "o".equals(path.get(3))
                    && storage.getReference().getBucket().equals(path.get(2))) {
                return storage.getReference().child(path.get(4));
            }
            return storage.getReferenceFromUrl(value);
        } catch (IllegalArgumentException ignored) {
            // A removed or malformed image must not crash the product list.
            return null;
        }
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
        RadioGroup filters = findViewById(R.id.favoriteFilters);
        if (filters != null) filters.setOnCheckedChangeListener((group, checkedId) -> renderFavoriteProducts(checkedId));
        favoritePosts.clear();
        if (requireSignedIn()) addScreenListener(memberOneRepository.listenFavorites(currentUid(), (snapshot, error) -> {
            if (currentScreen != R.layout.activity_favorites) return;
            if (error != null) { showFirebaseError(error); return; }
            for (ListenerRegistration listener : favoritePostListeners) listener.remove();
            favoritePostListeners.clear(); favoritePosts.clear(); favoritePostIds.clear();
            if (snapshot != null) for (DocumentSnapshot favorite : snapshot.getDocuments()) {
                String postId = favorite.getId(); favoritePostIds.add(postId);
                favoritePostListeners.add(FirebaseFirestore.getInstance().collection("posts").document(postId)
                        .addSnapshotListener((post, postError) -> {
                            if (currentScreen != R.layout.activity_favorites) return;
                            favoritePosts.removeIf(existing -> existing.getId().equals(postId));
                            if (postError == null && post != null && post.exists()) favoritePosts.add(post);
                            renderFavoriteProducts(filters == null ? R.id.filterFavoritesAll : filters.getCheckedRadioButtonId());
                        }));
            }
            renderFavoriteProducts(filters == null ? R.id.filterFavoritesAll : filters.getCheckedRadioButtonId());
        }));
        renderFavoriteProducts(R.id.filterFavoritesAll);
    }

    private void renderFavoriteProducts(int filterId) {
        GridLayout grid = findViewById(R.id.favoriteProductGrid);
        if (grid == null) return;
        grid.removeAllViews();
        int count = 0;
        for (DocumentSnapshot post : favoritePosts) {
            String status = textValue(post.getData(), "status", "ACTIVE");
            if (filterId == R.id.filterFavoritesAvailable && !"ACTIVE".equalsIgnoreCase(status)) continue;
            if (filterId == R.id.filterFavoritesUnavailable && "ACTIVE".equalsIgnoreCase(status)) continue;
            grid.addView(createProductCard(post, false)); count++;
        }
        TextView empty = findViewById(R.id.txtFavoritesEmpty);
        if (empty != null) {
            empty.setText("Chưa có sản phẩm yêu thích hiển thị. Bài đã ẩn hoặc bị gỡ sẽ không hiển thị.");
            empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
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
        activeOrders.clear();ordersAwaitingServer=true;ordersLoadError="";
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
        if(currentScreen!=R.layout.activity_orders)return;
        ordersLoadError=error==null?"":"Không tải được đơn hàng. Nhấn để thử lại.";
        ordersAwaitingServer=error==null&&(snapshot==null||snapshot.getMetadata().isFromCache());
        activeOrders.clear();
        if(snapshot!=null&&error==null)activeOrders.addAll(snapshot.getDocuments());
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
        empty.setText(!ordersLoadError.isEmpty()?ordersLoadError:ordersAwaitingServer?"Đang chờ dữ liệu từ backend…":"Không tìm thấy đơn hàng phù hợp.");
        empty.setOnClickListener(v->{if(!ordersLoadError.isEmpty()||ordersAwaitingServer)showScreen(R.layout.activity_orders);});
        TextView count = findViewById(R.id.txtOrderCount);
        if(count!=null)count.setText((ordersAwaitingServer||!ordersLoadError.isEmpty())&&shown==0?"—":String.valueOf(shown));
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
        transactionScreens.bind(R.layout.activity_transaction_history);
    }

    private void bindTransactions() {
        transactionScreens.bind(R.layout.activity_transactions);
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
        transactionScreens.bind(R.layout.activity_transactions);
    }

    private TextView findFirstTextChild(View parent, int index) {
        ArrayList<TextView> views = new ArrayList<>(); collectTextViews(parent, views);
        return index >= 0 && index < views.size() ? views.get(index) : null;
    }

    private void bindAddresses() {
        bindClick(R.id.btnAddressesBack, this::goBack);
        if (requireSignedIn()) accountScreens.addresses();
    }
    private void bindAddressForm() {
        bindClick(R.id.btnAddressFormBack, this::goBack);
        if (requireSignedIn()) accountScreens.addressForm();
    }

    private void bindEditProfile() {
        final Set<Integer> editedFields=new HashSet<>();
        for(int id:new int[]{R.id.edtProfileName,R.id.edtProfileBirthDate,R.id.edtProfileRegion,R.id.edtProfileAddress,R.id.edtProfileBio}){
            EditText field=findViewById(id);field.setText("");
            field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){editedFields.add(id);}public void afterTextChanged(android.text.Editable s){}});
        }
        java.util.function.BiConsumer<Integer,String> populate=(id,value)->{if(!editedFields.contains(id))setEditTextValue(id,value);};
        setSpinnerOptions(R.id.spinnerProfileGender, new String[]{"Nữ", "Nam", "Khác", "Không muốn tiết lộ"});
        FirebaseUser account = authRepository.getCurrentUser();
        setTextIfPresent(R.id.txtProfileEmail, account == null || account.getEmail() == null ? "Chưa có email" : account.getEmail());
        setTextIfPresent(R.id.txtProfilePhone, account == null || account.getPhoneNumber() == null ? "Chưa có số điện thoại" : account.getPhoneNumber());
        setTextIfPresent(R.id.txtProfileEmailStatus, account != null && account.isEmailVerified() ? "✓ Email đã xác minh" : "Email chưa xác minh");
        boolean simulatedPhone = BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS;
        setTextIfPresent(R.id.txtProfilePhoneStatus, simulatedPhone ? "OTP chỉ mô phỏng, không bắt buộc" : "SMS thật đang tạm tắt, không bắt buộc xác minh SĐT");
        for (int id : new int[]{R.id.btnVerifyPhone, R.id.btnChangeProfilePhone}) {
            View button = findViewById(id);
            if (button != null) button.setVisibility(simulatedPhone ? View.VISIBLE : View.GONE);
        }
        if (requireSignedIn()) FirebaseFirestore.getInstance().collection("users").document(currentUid()).get()
                .addOnSuccessListener(user -> {
                    if (isDestroyed() || !user.exists() || currentScreen != R.layout.activity_edit_profile) return;
                    populate.accept(R.id.edtProfileName, user.getString("name"));
                    populate.accept(R.id.edtProfileBirthDate, user.getString("birthDate"));
                    populate.accept(R.id.edtProfileRegion, user.getString("region"));
                    populate.accept(R.id.edtProfileAddress, user.getString("address"));
                    populate.accept(R.id.edtProfileBio, user.getString("bio"));
                    String gender = user.getString("gender");
                    Spinner genderSpinner = findViewById(R.id.spinnerProfileGender);
                    if (restoredForm==null && gender != null && genderSpinner != null && genderSpinner.getAdapter() != null)
                        for (int i = 0; i < genderSpinner.getAdapter().getCount(); i++) if (gender.equals(genderSpinner.getAdapter().getItem(i))) genderSpinner.setSelection(i);
                    loadAvatarIntoTextView(user.getString("photoUrl"), findViewById(R.id.txtEditProfileAvatar));
                });
        if (selectedProfilePhoto != null) {
            TextView avatar = findViewById(R.id.txtEditProfileAvatar);
            if (avatar != null) { avatar.setText(""); avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), selectedProfilePhoto)); }
        }
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
        if (profileBusy || !requireSignedIn()) return;
        EditText name = findViewById(R.id.edtProfileName);
        EditText birthDate = findViewById(R.id.edtProfileBirthDate);
        EditText region = findViewById(R.id.edtProfileRegion);
        EditText address = findViewById(R.id.edtProfileAddress);
        EditText bio = findViewById(R.id.edtProfileBio);
        if (name == null || name.getText().toString().trim().isEmpty()) {
            if (name != null) name.setError("Vui lòng nhập họ tên");
            return;
        }
        if(name.length()>100){name.setError("Họ tên tối đa 100 ký tự");return;}
        if(bio!=null && bio.length()>2000){bio.setError("Giới thiệu tối đa 2000 ký tự");return;}
        if(birthDate!=null && !birthDate.getText().toString().trim().isEmpty())try{
            java.time.LocalDate date=java.time.LocalDate.parse(birthDate.getText().toString().trim(),java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT));
            if(date.isAfter(java.time.LocalDate.now())||date.getYear()<1900)throw new java.time.DateTimeException("out of range");
        }catch(java.time.DateTimeException invalid){birthDate.setError("Ngày sinh hợp lệ theo dd/mm/yyyy, từ 1900 đến hiện tại");return;}
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name.getText().toString().trim());
        profile.put("gender", spinnerText(R.id.spinnerProfileGender, ""));
        profile.put("birthDate", birthDate == null ? "" : birthDate.getText().toString().trim());
        profile.put("region", region == null ? "" : region.getText().toString().trim());
        profile.put("address", address == null ? "" : address.getText().toString().trim());
        profile.put("bio", bio == null ? "" : bio.getText().toString().trim());
        profileBusy=true;findViewById(R.id.btnEditProfileSave).setEnabled(false);
        memberOneRepository.saveUserProfile(currentUid(), profile, selectedProfilePhotoUri, selectedProfilePhoto)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã lưu hồ sơ.", Toast.LENGTH_SHORT).show();
                    selectedProfilePhotoUri = null; selectedProfilePhoto = null;
                    showScreen(R.layout.activity_profile);
                }).addOnFailureListener(this::showFirebaseError).addOnCompleteListener(done->{profileBusy=false;View button=findViewById(R.id.btnEditProfileSave);if(button!=null)button.setEnabled(true);});
    }

    private void setEditTextValue(int viewId, String value) {
        EditText field = findViewById(viewId);
        if (field != null && value != null) field.setText(value);
    }

    private void loadAvatarIntoTextView(String url, TextView target) {
        if (url == null || url.isEmpty() || target == null) return;
        com.google.firebase.storage.StorageReference image = storageImageReference(url);
        if (image == null) return;
        image
                .getBytes(5 * 1024 * 1024).addOnSuccessListener(bytes -> {
                    if (!target.isAttachedToWindow()) return;
                    Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) { target.setText(""); target.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap)); }
                });
    }

    private void bindPaymentInfo() {
        bindClick(R.id.btnPaymentInfoBack, this::goBack);
        if (requireSignedIn()) accountScreens.payout();
    }

    private void bindSettings() {
        bindClick(R.id.btnSettingsBack, () -> showScreen(R.layout.activity_profile));
        bindClick(R.id.settingAccount, () -> showScreen(R.layout.activity_edit_profile));
        bindClick(R.id.settingNotifications, () -> showScreen(R.layout.activity_notifications));
        bindClick(R.id.settingPrivacy, () -> showScreen(R.layout.activity_privacy_security));
        bindClick(R.id.settingLanguage, () -> showScreen(R.layout.activity_language));
        bindClick(R.id.settingLogout, () -> { authRepository.signOut(); showScreen(R.layout.activity_login); });
    }

    private void bindPrivacySecurity() {
        bindClick(R.id.btnPrivacySecurityBack, this::goBack);
        if (requireSignedIn()) accountScreens.privacy();
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
        bindClick(R.id.btnSupportContactBack, this::goBack);
        if (requireSignedIn()) accountScreens.support();
    }

    private void bindRequestStatus() {
        transactionScreens.bind(R.layout.activity_request_status);
    }

    private void bindSellSuccess() {
        bindClick(R.id.btnViewPublishedListing, () -> showScreen(R.layout.activity_my_listings));
        bindClick(R.id.btnManagePublishedListings, () -> showScreen(R.layout.activity_my_listings));
    }

    private void renderTransactionHistory(int filterId) {
        transactionScreens.bind(R.layout.activity_transaction_history);
    }

    private void bindOrderDetail() {
        transactionScreens.bind(R.layout.activity_order_detail);
    }

    private void populateOrderDetail() {
        transactionScreens.bind(R.layout.activity_order_detail);
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
            transactionScreens.submitDispute(new ArrayList<>(reportEvidenceItems));
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
        if (chatFeed != null) { chatFeed.removeObservers(this); chatFeed = null; }
        setTextIfPresent(R.id.tvChatStatus,"Cuộc trò chuyện");
        View attach=findViewById(R.id.btnAttach);if(attach!=null)attach.setVisibility(View.GONE);
        bindClick(R.id.btnMenu,()->new android.app.AlertDialog.Builder(this).setItems(new String[]{"Xem hồ sơ thành viên","Thông báo"},(dialog,which)->{if(which==1)showScreen(R.layout.activity_notifications);else if(!currentPeerId.isEmpty()){viewedSellerId=currentPeerId;showScreen(R.layout.activity_seller_profile);}}).show());
        bindClick(R.id.btnBack, () -> showScreen(chatReturnScreen));
        TextView chatName = findViewById(R.id.tvChatName);
        if (chatName != null && !currentPeerId.isEmpty()) FirebaseFunctions.getInstance().getHttpsCallable("getSellerProfile").call(java.util.Collections.singletonMap("sellerId",currentPeerId))
                .addOnSuccessListener(result -> {if(chatName.isAttachedToWindow() && result.getData() instanceof Map)chatName.setText(textValue((Map<String,Object>)result.getData(),"name","Cuộc trò chuyện"));})
                .addOnFailureListener(error->{if(chatName.isAttachedToWindow())chatName.setText("Không tải được người trò chuyện");});
        Button cartButton=findViewById(R.id.btnAddToCart),buyButton=findViewById(R.id.btnBuyNow);
        cartButton.setEnabled(false);buyButton.setEnabled(false);
        setTextIfPresent(R.id.tvPinnedProductName,currentPostId.isEmpty()?"Chưa có sản phẩm liên quan":"Đang tải sản phẩm…");setTextIfPresent(R.id.tvPinnedProductPrice,"");
        final String chatPostId=currentPostId;
        if(!chatPostId.isEmpty())FirebaseFirestore.getInstance().collection("posts").document(chatPostId).get().addOnSuccessListener(post->{
            if(isDestroyed() || currentScreen!=R.layout.activity_chat || !chatPostId.equals(currentPostId))return;
            if(!post.exists()){setTextIfPresent(R.id.tvPinnedProductName,"Sản phẩm không còn tồn tại");return;}
            currentProductData=post.getData();currentProductName=textValue(currentProductData,"title","Sản phẩm");currentSellerId=textValue(currentProductData,"ownerId","");currentProductIsFree="FREE".equals(textValue(currentProductData,"type","SALE"));
            setTextIfPresent(R.id.tvPinnedProductName,currentProductName);setTextIfPresent(R.id.tvPinnedProductPrice,currentProductIsFree?"Miễn phí":String.format(java.util.Locale.getDefault(),"%,d đ",numberValue(post.get("price"))));
            boolean available="ACTIVE".equals(post.getString("status"))&&!currentUid().equals(currentSellerId);cartButton.setEnabled(available&&!currentProductIsFree);buyButton.setEnabled(available);buyButton.setText(currentProductIsFree?"Yêu cầu nhận":"Mua ngay");
            if(currentProductIsFree)bindFreeRequestAction(buyButton,chatPostId,available);
        }).addOnFailureListener(error->{if(currentScreen==R.layout.activity_chat)setTextIfPresent(R.id.tvPinnedProductName,"Không tải được sản phẩm. Quay lại để thử lại.");});
        bindClick(R.id.btnAddToCart,()->{if(currentPostId.isEmpty()||!requireSignedIn())return;cartButton.setEnabled(false);memberTwoRepository.addCartItem(currentUid(),currentPostId,cartDataForCurrentProduct()).addOnSuccessListener(done->Toast.makeText(this,"Đã thêm vào giỏ hàng.",Toast.LENGTH_SHORT).show()).addOnFailureListener(this::showFirebaseError).addOnCompleteListener(done->{if(cartButton.isAttachedToWindow())cartButton.setEnabled(true);});});
        bindClick(R.id.btnBuyNow,()->{if(currentPostId.isEmpty()||!requireSignedIn())return;checkoutReturnScreen=R.layout.activity_chat;showScreen(currentProductIsFree?R.layout.activity_requestfree:R.layout.activity_checkout);});
        androidx.recyclerview.widget.RecyclerView messages = findViewById(R.id.chatMessagesContainer);
        vn.edu.ueh.thudinh.fashion2hand.viewmodel.ChatViewModel chatModel =
                new androidx.lifecycle.ViewModelProvider(this).get(vn.edu.ueh.thudinh.fashion2hand.viewmodel.ChatViewModel.class);
        if (messages != null && !currentChatId.isEmpty()) {
            final String boundChat = currentChatId;
            vn.edu.ueh.thudinh.fashion2hand.adapter.MessageAdapter adapter =
                    new vn.edu.ueh.thudinh.fashion2hand.adapter.MessageAdapter(currentUid());
            androidx.recyclerview.widget.LinearLayoutManager manager = new androidx.recyclerview.widget.LinearLayoutManager(this);
            manager.setStackFromEnd(true);
            messages.setLayoutManager(manager); messages.setAdapter(adapter);
            chatFeed = chatModel.open(boundChat);
            Button older = findViewById(R.id.btnOlderMessages);
            older.setOnClickListener(view -> { if (chatFeed != null) chatFeed.loadOlder(); });
            final boolean[] markingRead = {false};
            chatFeed.observe(this, state -> {
                if (currentScreen != R.layout.activity_chat || !boundChat.equals(currentChatId)) return;
                boolean atBottom = !messages.canScrollVertically(1);
                String previousLast = adapter.getItemCount() == 0 ? "" : adapter.getCurrentList().get(adapter.getItemCount() - 1).id;
                String nextLast = state.messages.isEmpty() ? "" : state.messages.get(state.messages.size() - 1).id;
                boolean newTail = !previousLast.equals(nextLast);
                adapter.submitList(state.messages, () -> {
                    if (atBottom && newTail && adapter.getItemCount() > 0) messages.scrollToPosition(adapter.getItemCount() - 1);
                });
                setTextIfPresent(R.id.tvMessageState, state.error != null ? "Không tải được tin nhắn. Nhấn để thử lại."
                        : state.loading ? "Đang tải tin nhắn…"
                        : state.fromCache ? "Dữ liệu đã lưu trên máy · đang chờ kết nối"
                        : state.messages.isEmpty() ? "Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện." : "");
                findViewById(R.id.tvMessageState).setOnClickListener(view -> {
                    if (state.error != null) bindOrderChat();
                });
                older.setVisibility(state.hasMore ? View.VISIBLE : View.GONE);
                older.setEnabled(!state.loading);
                boolean unread = false;
                for (vn.edu.ueh.thudinh.fashion2hand.model.Message message : state.messages)
                    if (!message.senderId.equals(currentUid()) && !message.read) { unread = true; break; }
                if (unread && !state.fromCache && !markingRead[0]) {
                    markingRead[0] = true;
                    memberTwoRepository.markChatRead(boundChat).addOnCompleteListener(done -> markingRead[0] = false);
                }
            });
        }
        bindClick(R.id.btnSendMessage, () -> {
            if(messageBusy)return;
            EditText messageInput = findViewById(R.id.edtMessageInput);
            if(messageInput==null)return;
            if(messageInput.getText().toString().trim().isEmpty()){messageInput.setError("Nhập nội dung tin nhắn");return;}
            if(currentChatId.isEmpty()){messageInput.setError("Chưa chọn cuộc trò chuyện");return;}
            String text = messageInput.getText().toString().trim();
            messageBusy=true;findViewById(R.id.btnSendMessage).setEnabled(false);
            chatModel.send(currentChatId, text)
                    .addOnSuccessListener(result -> {if(text.equals(messageInput.getText().toString().trim()))messageInput.setText("");}).addOnFailureListener(this::showFirebaseError)
                    .addOnCompleteListener(done->{messageBusy=false;View send=findViewById(R.id.btnSendMessage);if(send!=null)send.setEnabled(true);});
        });
    }

    private void bindDelivery() {
        transactionScreens.bind(R.layout.activity_delivery);
    }

    private void bindDeliveryMap() {
        transactionScreens.bind(R.layout.activity_delivery_map);
    }

    private void openCart() {
        cartReturnScreen = currentScreen;
        showScreen(R.layout.activity_cart);
    }

    private void bindCart() {
        transactionScreens.bind(R.layout.activity_cart);
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
        bindClick(R.id.btnNotificationsBack, this::goBack);
        if (!requireSignedIn()) return;
        new NotificationInbox(this, new NotificationInbox.Host() {
            public String uid() { return currentUid(); }
            public void listen(ListenerRegistration registration) { addScreenListener(registration); }
            public String filter() { return notificationFilter; }
            public void filter(String value) { notificationFilter = value; }
            public boolean hideRead() { return hideReadNotifications; }
            public void hideRead(boolean value) { hideReadNotifications = value; }
            public void open(DocumentSnapshot notification) { openNotification(notification); }
        }).bind();
    }

    private void openNotification(DocumentSnapshot notification) {
        String orderId = notification.getString("orderId");
        String chatId = notification.getString("chatId");
        if (orderId != null && !orderId.isEmpty()) {
            currentOrderId = orderId;
            showScreen(R.layout.activity_order_detail);
        } else if (chatId != null && !chatId.isEmpty()) {
            final View inbox = findViewById(R.id.notificationList);
            FirebaseFirestore.getInstance().collection("chats").document(chatId).get()
                    .addOnSuccessListener(chat -> {
                        if (inbox != findViewById(R.id.notificationList)) return;
                        Object members = chat.get("members");
                        if (!(members instanceof List)) members = chat.get("participantIds");
                        if (!(members instanceof List) || !((List<?>) members).contains(currentUid())) {
                            Toast.makeText(this, "Cuộc trò chuyện không còn khả dụng.", Toast.LENGTH_SHORT).show(); return;
                        }
                        currentChatId = chatId; currentPeerId = "";
                        for (Object member : (List<?>) members) if (member instanceof String && !currentUid().equals(member)) currentPeerId = (String) member;
                        currentPostId = textValue(chat.getData(), "postId", "");
                        chatReturnScreen = R.layout.activity_notifications;
                        showScreen(R.layout.activity_chat);
                    }).addOnFailureListener(this::showFirebaseError);
        } else if (notification.getString("requestId") != null) {
            currentFreeRequestId = notification.getString("requestId");
            showScreen(R.layout.activity_transactions);
        } else {
            new android.app.AlertDialog.Builder(this).setTitle(textValue(notification.getData(), "title", "Thông báo"))
                    .setMessage(textValue(notification.getData(), "body", "")).setPositiveButton("Đóng", null).show();
        }
    }

    private void bindCheckout() {
        transactionScreens.bind(R.layout.activity_checkout);
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
        if (!requireSignedIn() || savingListing) return;
        if (listingDraft.get("title") == null || listingDraft.get("type") == null || listingDraft.get("price") == null) {
            Toast.makeText(this, "Vui lòng hoàn tất thông tin sản phẩm.", Toast.LENGTH_SHORT).show(); return;
        }
        String uid = currentUid();
        if (uid == null) return;
        if (editingPostId.isEmpty()) editingPostId = memberOneRepository.newPostId();
        String postId = editingPostId;
        boolean wasEditing = editingListing;
        Map<String, Object> data = new HashMap<>(listingDraft);
        data.remove("conditionLabel");
        String previousStatus = String.valueOf(listingDraft.getOrDefault("status", "DRAFT"));
        data.put("status", editingListing && !previousStatus.equals("DRAFT") && !previousStatus.equals("PENDING_REVIEW")
                ? previousStatus : (asDraft ? "DRAFT" : "ACTIVE"));
        if (!listingDraft.containsKey("createdAt")) data.put("createdAt", FieldValue.serverTimestamp());
        savingListing = true;
        setListingSaveEnabled(false);
        memberOneRepository.saveListing(uid, postId, data, new ArrayList<>(selectedListingPhotos), editingListing)
                .addOnSuccessListener(unused -> {
                    savingListing = false;
                    currentPostId = postId; editingListing = false; editingPostId = "";
                    Toast.makeText(this, wasEditing ? "Đã cập nhật tin đăng." : (asDraft ? "Đã lưu bản nháp." : "Đăng tin thành công."), Toast.LENGTH_SHORT).show();
                    navigateRoot(R.layout.activity_profile);
                    showScreen(asDraft ? R.layout.activity_my_listings : R.layout.activity_sell_success);
                }).addOnFailureListener(error -> { savingListing = false; setListingSaveEnabled(true); showFirebaseError(error); });
    }

    private void setListingSaveEnabled(boolean enabled) {
        for (int id : new int[]{R.id.btnSaveSellDraft, R.id.btnPublishListing}) {
            View button = findViewById(id); if (button != null) button.setEnabled(enabled);
        }
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
        transactionScreens.bind(R.layout.activity_requestfree);
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
            long unreadTotal=0;for(DocumentSnapshot chat:activeChats){Object counts=chat.get("unreadCounts");if(counts instanceof Map)unreadTotal+=numberValue(((Map<?,?>)counts).get(currentUid()));}
            TextView badge=findViewById(R.id.txtMessageUnreadCount);if(badge!=null){badge.setText(String.valueOf(unreadTotal));badge.setVisibility(unreadTotal>0?View.VISIBLE:View.GONE);}
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
                Object unreadMap=chat.get("unreadCounts");long unread=unreadMap instanceof Map?numberValue(((Map<?,?>)unreadMap).get(currentUid())):0;
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
                preview.setText((unread>0?"["+unread+" chưa đọc] ":"")+previewText);
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
                    currentPostId = textValue(chat.getData(), "postId", "");
                    chatReturnScreen = R.layout.activity_messages;
                    showScreen(R.layout.activity_chat);
                });

                list.addView(row);

                FirebaseFunctions.getInstance().getHttpsCallable("getChatContext").call(java.util.Collections.singletonMap("chatId", chatId))
                        .addOnSuccessListener(response -> {
                            if(!name.isAttachedToWindow() || !(response.getData() instanceof Map)) return;
                            Map<String,Object> profile=(Map<String,Object>)response.getData();name.setText(textValue(profile,"name","Thành viên"));loadAvatarIntoTextView(textValue(profile,"photoUrl",""),avatar);
                            row.setTag("firebase_chat|"+(name.getText()+" "+previewText).toLowerCase(java.util.Locale.ROOT));row.setTag(R.id.tvTabBuying,textValue(profile,"role","other"));applyMessageFilters();
                        }).addOnFailureListener(failure -> { if(name.isAttachedToWindow())name.setText("Không tải được tên thành viên"); });
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
            boolean roleMatch="all".equals(messageFilter)||messageFilter.equals(row.getTag(R.id.tvTabBuying));
            row.setVisibility(searchMatch && roleMatch ? View.VISIBLE : View.GONE);
        }
    }

    private void bindProfile() {
        bindClick(R.id.verificationCard, () -> showScreen(R.layout.activity_verification_status));
        bindClick(R.id.btnOpenVerification, () -> showScreen(R.layout.activity_verification_status));
        bindProfileBackend();
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

    private void bindProfileBackend() {
        if (!requireSignedIn()) return;
        final View root = findViewById(R.id.profileRoot);
        final String uid = currentUid();
        addScreenListener(FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener((user, error) -> {
                    if (root != findViewById(R.id.profileRoot)) return;
                    if (error != null || user == null || !user.exists()) {
                        ((TextView) findViewById(R.id.txtProfileName)).setText("Chưa tải được hồ sơ");
                        return;
                    }
                    ((TextView) findViewById(R.id.txtProfileName)).setText(textValue(user.getData(), "name", "Thành viên"));
                    ((TextView) findViewById(R.id.txtProfileLocation)).setText(textValue(user.getData(), "region", "Chưa cập nhật khu vực"));
                    loadAvatarIntoTextView(user.getString("photoUrl"), findViewById(R.id.imgProfileAvatar));
                    boolean verified = "VERIFIED".equals(user.getString("verificationStatus"));
                    boolean pending = "PENDING".equals(user.getString("verificationStatus"));
                    ((TextView) findViewById(R.id.txtVerificationCardTitle)).setText(verified ? "Đã xác minh danh tính" : pending ? "Đã gửi hồ sơ danh tính" : "Xác minh danh tính (tùy chọn)");
                    ((TextView) findViewById(R.id.txtVerificationCardSubtitle)).setText(verified
                            ? "Hồ sơ danh tính đã được duyệt" : pending ? "Đang chờ quản trị viên duyệt" : "Chưa gửi hồ sơ · không bắt buộc để mua/nhận đồ");
                }));
        addScreenListener(memberOneRepository.listenMyListings(uid, (snapshot, error) -> {
            if (root != findViewById(R.id.profileRoot)) return;
            TextView count = findViewById(R.id.txtProfilePostCount);
            TextView menu = findViewById(R.id.btnMyListings);
            if (error != null || snapshot == null) {
                count.setText("—\nTin đăng");
                menu.setText("Tin đăng của tôi  ›\nChưa tải được · Nhấn để thử lại");
                return;
            }
            int active = 0;
            for (DocumentSnapshot post : snapshot.getDocuments())
                if ("ACTIVE".equals(post.getString("status"))) active++;
            count.setText(snapshot.size() + "\nTin đăng");
            menu.setText("Tin đăng của tôi  ›\n" + active + " đang hoạt động");
        }));
        final Map<String, DocumentSnapshot> buyer = new HashMap<>(), seller = new HashMap<>();
        final boolean[] loaded = {false, false}, failed = {false, false};
        java.util.function.BiConsumer<Integer, QuerySnapshot> update = (side, snapshot) -> {
            if (root != findViewById(R.id.profileRoot)) return;
            loaded[side] = true;
            failed[side] = snapshot == null;
            Map<String, DocumentSnapshot> target = side == 0 ? buyer : seller;
            target.clear();
            if (snapshot != null) for (DocumentSnapshot order : snapshot.getDocuments()) target.put(order.getId(), order);
            if (!loaded[0] || !loaded[1]) return;
            TextView completedView = findViewById(R.id.txtProfileCompletedCount);
            TextView menu = findViewById(R.id.btnMyCart);
            if (failed[0] || failed[1]) {
                completedView.setText("—\nHoàn thành");
                menu.setText("Đơn hàng của tôi  ›\nChưa tải được · Nhấn để thử lại");
                return;
            }
            Map<String, DocumentSnapshot> all = new HashMap<>(buyer);
            all.putAll(seller);
            int completed = 0, shipping = 0;
            for (DocumentSnapshot order : all.values()) {
                if ("COMPLETED".equals(order.getString("orderStatus"))) completed++;
                if ("SHIPPING".equals(order.getString("orderStatus"))) shipping++;
            }
            completedView.setText(completed + "\nHoàn thành");
            menu.setText("Đơn hàng của tôi  ›\n" + all.size() + " đơn · " + shipping + " đang giao");
        };
        addScreenListener(memberTwoRepository.listenOrdersAsBuyer(uid, (snapshot, error) -> update.accept(0, error == null ? snapshot : null)));
        addScreenListener(memberTwoRepository.listenOrdersAsSeller(uid, (snapshot, error) -> update.accept(1, error == null ? snapshot : null)));
    }

    private void bindAccountVerification() {
        bindClick(R.id.btnVerificationBack, this::goBack);
        bindClick(R.id.btnVerificationLater, () -> showScreen(R.layout.activity_profile));
        if (requireSignedIn()) accountScreens.verificationForm();
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
        accountScreens.selectPhoto(identityPhotoSlot, uri, bitmap);
    }

    private void bindVerificationStatus() {
        bindClick(R.id.btnVerificationStatusBack, this::goBack);
        if (requireSignedIn()) accountScreens.verificationStatus();
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
        ScreenEntry origin = captureScreen();
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
        backStack.push(origin);
        restoringScreen = true;
        showScreen(R.layout.activity_product_detail);
        restoringScreen = false;
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
        if (descView != null) descView.setText(textValue(currentProductData,"description","Chưa có mô tả."));
        if (seller != null && !currentSellerId.isEmpty()) {
            Map<String, Object> args = new HashMap<>(); args.put("sellerId", currentSellerId);
            FirebaseFunctions.getInstance().getHttpsCallable("getSellerProfile").call(args)
                    .addOnSuccessListener(result -> {
                        if (seller.isAttachedToWindow() && result.getData() instanceof Map)
                            seller.setText(textValue((Map<String, Object>) result.getData(), "name", "Người bán"));
                    }).addOnFailureListener(error -> { if (seller.isAttachedToWindow()) seller.setText("Không tải được người đăng"); });
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
        bindClick(R.id.btnProductShare, this::shareCurrentProduct);
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
        bindClick(R.id.btnViewSellerProfile, () -> {
            if (currentSellerId.isEmpty() || !requireSignedIn()) return;
            viewedSellerId = currentSellerId;
            showScreen(R.layout.activity_seller_profile);
        });
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
        if(currentProductIsFree)bindFreeRequestAction(findViewById(R.id.btnRequestFree),currentPostId,"ACTIVE".equals(textValue(currentProductData,"status","ACTIVE"))&&!currentUid().equals(currentSellerId));
    }

    private void bindFreeRequestAction(Button button,String postId,boolean available) {
        if(button==null||postId.isEmpty())return;
        final int origin=currentScreen;final String uid=currentUid();
        if(uid.isEmpty()){button.setText("Gửi yêu cầu");button.setEnabled(available);return;}
        button.setEnabled(false);button.setText("Đang kiểm tra yêu cầu…");
        addScreenListener(memberTwoRepository.listenMyFreeRequest(uid,postId,(snapshot,error)->{
            if(isDestroyed()||currentScreen!=origin||!postId.equals(currentPostId)||!uid.equals(currentUid())||!button.isAttachedToWindow())return;
            if(error!=null){button.setText("Không tải được yêu cầu");button.setEnabled(false);return;}
            DocumentSnapshot existing=null;
            if(snapshot!=null)for(DocumentSnapshot request:snapshot.getDocuments())if(FreeRequestUi.alreadySent(request.getString("status"))){existing=request;break;}
            if(existing!=null){
                final String requestId=existing.getId();button.setText(FreeRequestUi.action(existing.getString("status")));button.setEnabled(true);
                button.setOnClickListener(v->openTransactionDestination(R.layout.activity_request_status,()->currentFreeRequestId=requestId));
            }else{
                boolean ready=snapshot!=null&&!snapshot.getMetadata().isFromCache();button.setText(ready?"Gửi yêu cầu":"Đang kiểm tra yêu cầu…");button.setEnabled(ready&&available);
                button.setOnClickListener(v->{if(!requireSignedIn())return;checkoutReturnScreen=origin;showScreen(R.layout.activity_requestfree);});
            }
        }));
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
        badge.setTextColor(currentProductIsFree ? 0xFF23795C : 0xFF9C3D28);
        badge.setBackgroundResource(currentProductIsFree ? R.drawable.bg_green_light_card : R.drawable.bg_product_sale_badge);
        addToCart.setVisibility(currentProductIsFree ? View.GONE : View.VISIBLE);
        buyNow.setVisibility(currentProductIsFree ? View.GONE : View.VISIBLE);
        requestFree.setVisibility(currentProductIsFree ? View.VISIBLE : View.GONE);
        chat.setText(currentProductIsFree ? "Chat với người tặng" : "Chat");
        chat.setTextSize(currentProductIsFree ? 11 : 10);
        chat.setPadding(0, 0, 0, 0);
        chat.setMinWidth(0);
        chat.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEAF0EC));
        chat.setTextColor(0xFF14513E);
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

    private void shareCurrentProduct() {
        if (!MarketplaceLinks.validPostId(currentPostId)) {
            Toast.makeText(this, "Sản phẩm chưa có liên kết chia sẻ.", Toast.LENGTH_SHORT).show(); return;
        }
        String link = MarketplaceLinks.productLink(currentPostId);
        new android.app.AlertDialog.Builder(this).setTitle("Chia sẻ sản phẩm")
                .setItems(new String[]{"Chia sẻ qua ứng dụng", "Sao chép liên kết"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, currentProductName)
                                .putExtra(Intent.EXTRA_TEXT, currentProductName + "\n" + link);
                        startActivity(Intent.createChooser(share, "Chia sẻ sản phẩm"));
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Liên kết sản phẩm", link));
                        Toast.makeText(this, "Đã sao chép liên kết sản phẩm.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void bindSellerProfile() {
        if (viewedSellerId.isEmpty() || !requireSignedIn()) return;
        final String sellerId = viewedSellerId;
        bindClick(R.id.btnRetrySeller, () -> showScreen(R.layout.activity_seller_profile));
        Map<String, Object> args = new HashMap<>(); args.put("sellerId", sellerId);
        FirebaseFunctions.getInstance().getHttpsCallable("getSellerProfile").call(args)
                .addOnSuccessListener(result -> {
                    if (currentScreen != R.layout.activity_seller_profile || !sellerId.equals(viewedSellerId)) return;
                    if (!(result.getData() instanceof Map)) return;
                    Map<String, Object> profile = (Map<String, Object>) result.getData();
                    setTextIfPresent(R.id.txtSellerName, textValue(profile, "name", "Thành viên"));
                    setTextIfPresent(R.id.txtSellerRegion, textValue(profile, "region", "").isEmpty() ? "Chưa cập nhật khu vực" : textValue(profile, "region", ""));
                    setTextIfPresent(R.id.txtSellerBio, textValue(profile, "bio", "").isEmpty() ? "Chưa có giới thiệu." : textValue(profile, "bio", ""));
                    loadAvatarIntoTextView(textValue(profile, "photoUrl", ""), findViewById(R.id.txtSellerAvatar));
                    addScreenListener(FirebaseFirestore.getInstance().collection("posts")
                            .whereEqualTo("ownerId", sellerId).whereEqualTo("status", "ACTIVE")
                            .addSnapshotListener((snapshot, error) -> {
                                if (currentScreen != R.layout.activity_seller_profile || !sellerId.equals(viewedSellerId)) return;
                                if (error != null) { sellerProfileError(error); return; }
                                GridLayout grid = findViewById(R.id.sellerProductGrid); grid.removeAllViews();
                                if (snapshot != null) for (DocumentSnapshot post : snapshot.getDocuments()) grid.addView(createProductCard(post, false));
                                setTextIfPresent(R.id.txtSellerState, grid.getChildCount() == 0 ? "Chưa có bài đăng đang hoạt động." : grid.getChildCount() + " bài đăng");
                            }));
                }).addOnFailureListener(error -> {
                    if (currentScreen == R.layout.activity_seller_profile && sellerId.equals(viewedSellerId)) sellerProfileError(error);
                });
    }

    private void sellerProfileError(Exception error) {
        setTextIfPresent(R.id.txtSellerState, "Không tải được hồ sơ. Vui lòng thử lại.");
        View retry = findViewById(R.id.btnRetrySeller); if (retry != null) retry.setVisibility(View.VISIBLE);
        showFirebaseError(error);
    }

    private void openIncomingProduct(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !"fashion2hand".equals(uri.getScheme()) || !"post".equals(uri.getHost())) return;
        if (uri.getPathSegments().size() != 1 || !MarketplaceLinks.validPostId(uri.getLastPathSegment())) {
            Toast.makeText(this, "Liên kết sản phẩm không hợp lệ.", Toast.LENGTH_SHORT).show(); return;
        }
        pendingProductLink = uri.getLastPathSegment();
        openPendingProduct();
    }

    private void openPendingProduct() {
        if (pendingProductLink.isEmpty() || currentScreen == R.layout.activity_splash || currentScreen == R.layout.activity_login) return;
        String id = pendingProductLink; pendingProductLink = "";
        FirebaseFirestore.getInstance().collection("posts").document(id).get()
                .addOnSuccessListener(post -> {
                    if (!post.exists()) Toast.makeText(this, "Bài đăng không còn tồn tại.", Toast.LENGTH_LONG).show();
                    else openProduct(post);
                }).addOnFailureListener(error -> Toast.makeText(this, "Bài đăng không còn công khai hoặc không thể tải.", Toast.LENGTH_LONG).show());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); openIncomingProduct(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (currentScreen == R.layout.activity_otp_verification && "email_change".equals(otpFlow)) checkChangedEmail(false);
    }

    @Override protected void onDestroy() {
        if (emulatorOtpNotifications != null) emulatorOtpNotifications.close();
        clearScreenListeners();
        super.onDestroy();
    }

    private void bindClick(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
