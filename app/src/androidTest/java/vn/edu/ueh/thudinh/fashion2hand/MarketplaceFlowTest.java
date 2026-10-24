package vn.edu.ueh.thudinh.fashion2hand;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Requires Auth, Firestore, Storage and Functions emulators on the host. */
@RunWith(AndroidJUnit4.class)
public class MarketplaceFlowTest {
    private void createAccount() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance(); auth.signOut();
        String uid = Tasks.await(auth.createUserWithEmailAndPassword("android-" + System.nanoTime() + "@example.com", "Test-only-123!"), 30, TimeUnit.SECONDS).getUser().getUid();
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid); user.put("name", "Marketplace test"); user.put("email", auth.getCurrentUser().getEmail());
        user.put("role", "USER"); user.put("accountStatus", "ACTIVE");
        user.put("createdAt", FieldValue.serverTimestamp()); user.put("updatedAt", FieldValue.serverTimestamp());
        Tasks.await(FirebaseFirestore.getInstance().collection("users").document(uid).set(user), 30, TimeUnit.SECONDS);
        VerifiedAccountFixture.verify(auth);
    }

    private void waitFor(ActivityScenario<MainActivity> scenario, int id) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean exists = new AtomicBoolean();
            scenario.onActivity(a -> exists.set(a.findViewById(id) != null));
            if (exists.get()) return;
            Thread.sleep(100);
        }
        fail("View did not appear: " + id);
    }

    private void click(ActivityScenario<MainActivity> scenario, int id) {
        scenario.onActivity(a -> { View view = a.findViewById(id); assertNotNull(view); assertTrue(view.performClick()); });
    }

    private View text(View view, String label) {
        if (view instanceof TextView && label.equals(((TextView) view).getText().toString())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }

    private void waitText(ActivityScenario<MainActivity> scenario, String label) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean exists = new AtomicBoolean();
            scenario.onActivity(a -> exists.set(text(a.findViewById(android.R.id.content), label) != null));
            if (exists.get()) return;
            Thread.sleep(100);
        }
        fail("Text did not appear: " + label);
    }

    private void waitEnabled(ActivityScenario<MainActivity> scenario, int id) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean enabled = new AtomicBoolean();
            scenario.onActivity(a -> { View view = a.findViewById(id); enabled.set(view != null && view.isEnabled()); });
            if (enabled.get()) return;
            Thread.sleep(100);
        }
        fail("Button stayed disabled: " + id);
    }

    @Test public void smsVerificationRejectsWrongCodeAndKeepsAccount() throws Exception {
        createAccount();
        String uid = FirebaseAuth.getInstance().getUid();
        String phone = "+849" + String.valueOf(System.currentTimeMillis()).substring(5);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.navProfile); click(scenario, R.id.navProfile);
            click(scenario, R.id.btnEditProfile); click(scenario, R.id.btnVerifyPhone);
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtVerifyPhoneNumber)).setText(phone));
            click(scenario, R.id.btnSendPhoneOtp); waitFor(scenario, R.id.edtOtpCode);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(
                    "http://10.0.2.2:9099/emulator/v1/projects/fashion2hand/verificationCodes").openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            String payload;
            try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                payload = bytes.toString("UTF-8");
            } finally { connection.disconnect(); }
            org.json.JSONArray codes = new org.json.JSONObject(payload).getJSONArray("verificationCodes");
            String code = null;
            for (int i = 0; i < codes.length(); i++) if (phone.equals(codes.getJSONObject(i).getString("phoneNumber"))) code = codes.getJSONObject(i).getString("code");
            assertNotNull(code); final String validCode = code;
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtOtpCode)).setText(validCode.equals("000000") ? "111111" : "000000"));
            click(scenario, R.id.btnSubmitOtp); waitEnabled(scenario, R.id.btnSubmitOtp);
            assertNull(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());
            scenario.recreate(); waitFor(scenario, R.id.edtOtpCode);
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtOtpCode)).setText(validCode));
            click(scenario, R.id.btnSubmitOtp); waitFor(scenario, R.id.btnEditProfileSave);
            assertEquals(uid, FirebaseAuth.getInstance().getUid());
            assertEquals(phone, FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());
            waitText(scenario, "✓ Số điện thoại đã xác minh");
        }
    }

    @Test public void toolbarAndSystemBackSurviveActivityRecreation() throws Exception {
        createAccount();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.navProfile); click(scenario, R.id.navProfile);
            click(scenario, R.id.btnEditProfile); click(scenario, R.id.btnChangeProfileEmail);
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtNewEmail)).setText("pending@example.com"));
            scenario.recreate(); waitFor(scenario, R.id.edtNewEmail);
            scenario.onActivity(a -> assertEquals("pending@example.com", ((EditText) a.findViewById(R.id.edtNewEmail)).getText().toString()));
            click(scenario, R.id.btnChangeEmailBack); waitFor(scenario, R.id.btnEditProfileSave);
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            waitFor(scenario, R.id.btnEditProfile);
        }
    }

    @Test public void createEditOwnListingAndFavoriteThroughApp() throws Exception {
        createAccount();
        String title = "Áo Android " + System.nanoTime(); String edited = title + " đã sửa";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.navCreate); click(scenario, R.id.navCreate);
            // Supply the Bitmap returned by the external camera contract, then exercise the actual form and upload.
            scenario.onActivity(a -> {
                try {
                    Bitmap photo = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888); photo.eraseColor(0xFF14513E);
                    java.lang.reflect.Field photos = MainActivity.class.getDeclaredField("selectedListingPhotos"); photos.setAccessible(true);
                    ((ArrayList<Object>) photos.get(a)).add(photo);
                    java.lang.reflect.Field captured = MainActivity.class.getDeclaredField("capturedListingPhoto"); captured.setAccessible(true); captured.set(a, photo);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            scenario.recreate(); waitFor(scenario, R.id.btnSellNext);
            click(scenario, R.id.btnSellNext);
            scenario.onActivity(a -> {
                ((EditText) a.findViewById(R.id.edtProductName)).setText(title);
                ((EditText) a.findViewById(R.id.edtProductDescription)).setText("Ảnh và bài đăng kiểm thử cục bộ");
            });
            click(scenario, R.id.btnSellDetailsNext);
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtProductPrice)).setText("120000"));
            click(scenario, R.id.btnSellTypeNext); click(scenario, R.id.btnPublishListing);
            waitFor(scenario, R.id.btnManagePublishedListings); click(scenario, R.id.btnManagePublishedListings);
            waitText(scenario, title);
            scenario.onActivity(a -> assertTrue(text(a.findViewById(android.R.id.content), "Chỉnh sửa").performClick()));
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.edtProductName)).setText(edited));
            click(scenario, R.id.btnSellDetailsNext); click(scenario, R.id.btnSellTypeNext); click(scenario, R.id.btnPublishListing);
            waitFor(scenario, R.id.btnManagePublishedListings); click(scenario, R.id.btnManagePublishedListings);
            waitText(scenario, edited);
            scenario.onActivity(a -> assertTrue(text(a.findViewById(android.R.id.content), "Xem").performClick()));
            waitFor(scenario, R.id.btnProductLike);
            scenario.onActivity(a -> {
                TextView badge=a.findViewById(R.id.txtProductTypeBadge);
                android.graphics.drawable.GradientDrawable background=(android.graphics.drawable.GradientDrawable)badge.getBackground();
                assertNotNull(background.getColor());
                assertTrue("Product badge contrast",androidx.core.graphics.ColorUtils.calculateContrast(badge.getCurrentTextColor(),background.getColor().getDefaultColor())>=4.5);
                android.widget.Button chat=a.findViewById(R.id.btnProductChat);
                assertEquals("Chat",chat.getText().toString());
                assertTrue("Chat label contrast",androidx.core.graphics.ColorUtils.calculateContrast(chat.getCurrentTextColor(),chat.getBackgroundTintList().getDefaultColor())>=4.5);
            });
            Bitmap screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            java.io.File directory=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"design-qa");
            assertTrue(directory.exists()||directory.mkdirs());
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"product-contrast.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
            click(scenario, R.id.btnProductLike);
            waitText(scenario, "♥");
            click(scenario, R.id.btnViewSellerProfile); waitText(scenario, "Marketplace test"); waitText(scenario, edited);
            scenario.onActivity(a -> assertTrue(((View) text(a.findViewById(android.R.id.content), edited).getParent()).performClick()));
            waitFor(scenario, R.id.btnProductBack); click(scenario, R.id.btnProductBack);
            waitFor(scenario, R.id.btnSellerBack); click(scenario, R.id.btnSellerBack);
            waitFor(scenario, R.id.btnProductShare); click(scenario, R.id.btnProductShare);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Sao chép liên kết"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            scenario.onActivity(a -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                assertNotNull(clipboard.getPrimaryClip());
                assertTrue(clipboard.getPrimaryClip().getItemAt(0).getText().toString().startsWith("fashion2hand://post/"));
            });
            click(scenario, R.id.btnProductBack);
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            waitFor(scenario, R.id.btnFavorites); click(scenario, R.id.btnFavorites);
            waitText(scenario, edited);
            scenario.onActivity(a -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                String link = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                android.content.Intent launchIntent = a.getIntent();
                a.onNewIntent(new android.content.Intent(launchIntent).setAction(android.content.Intent.ACTION_VIEW).setData(android.net.Uri.parse(link)));
                // ActivityScenario matches lifecycle events using its original launch intent.
                a.setIntent(launchIntent);
            });
            waitFor(scenario, R.id.btnProductBack); waitText(scenario, edited);
            click(scenario, R.id.btnProductBack); waitFor(scenario, R.id.favoriteProductGrid);
        }
    }
}
