package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs only with the Auth Emulator; never sends a real SMS. */
@RunWith(AndroidJUnit4.class)
public class EmulatorOtpNotificationTest {
    @Test public void ignoresCodesForOtherPhonesAndSessions() throws Exception {
        JSONObject payload = new JSONObject("{\"verificationCodes\":["
                + "{\"phoneNumber\":\"+16505550123\",\"sessionInfo\":\"old\",\"code\":\"111111\"},"
                + "{\"phoneNumber\":\"+16505550124\",\"sessionInfo\":\"current\",\"code\":\"222222\"},"
                + "{\"phoneNumber\":\"+16505550123\",\"sessionInfo\":\"current\",\"code\":\"012345\"}]}");
        assertEquals("012345", EmulatorOtpNotifications.findCode(payload, "+16505550123", "current"));
        assertNull(EmulatorOtpNotifications.findCode(payload, "+16505550123", "missing"));
    }

    @Test public void notificationContainsUsableOtpAndClearsAfterVerification() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS);
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (Build.VERSION.SDK_INT >= 33) {
            try (java.io.InputStream command = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("pm grant " + context.getPackageName()
                                    + " android.permission.POST_NOTIFICATIONS"))) {
                byte[] buffer = new byte[256]; while (command.read(buffer) != -1) { }
            }
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser previous = auth.getCurrentUser();
        FirebaseUser fixture = null;
        String phone = "+16505550123";
        try {
            fixture = Tasks.await(auth.createUserWithEmailAndPassword(
                    "otp-notification-" + System.nanoTime() + "@example.test", "Test-only-123!"),
                    30, TimeUnit.SECONDS).getUser();
            String uid = fixture.getUid();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                waitFor(scenario, R.id.btnRequiredPhone);
                scenario.onActivity(a -> a.findViewById(R.id.btnRequiredPhone).performClick());
                waitFor(scenario, R.id.btnSendPhoneOtp);
                scenario.onActivity(a -> {
                    ((EditText) a.findViewById(R.id.edtVerifyPhoneNumber)).setText(phone);
                    a.findViewById(R.id.btnSendPhoneOtp).performClick();
                });
                waitFor(scenario, R.id.edtOtpCode);
                java.lang.reflect.Field sessionField = MainActivity.class.getDeclaredField("phoneVerificationId");
                sessionField.setAccessible(true);
                String[] session = {""};
                scenario.onActivity(a -> {
                    try { session[0] = (String) sessionField.get(a); }
                    catch (Exception error) { throw new AssertionError(error); }
                });
                HttpURLConnection connection = (HttpURLConnection) new URL("http://"
                        + BuildConfig.FIREBASE_EMULATOR_HOST
                        + ":9099/emulator/v1/projects/fashion2hand/verificationCodes").openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
                String code;
                try (java.io.InputStream in = connection.getInputStream();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    code = EmulatorOtpNotifications.findCode(new JSONObject(out.toString("UTF-8")), phone, session[0]);
                } finally { connection.disconnect(); }
                assertNotNull(code);
                long deadline = android.os.SystemClock.elapsedRealtime() + 15000;
                Notification notification = null;
                while (notification == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                    for (StatusBarNotification item : manager.getActiveNotifications())
                        if (item.getId() == EmulatorOtpNotifications.NOTIFICATION_ID) notification = item.getNotification();
                    Thread.sleep(100);
                }
                assertNotNull("OTP notification did not appear", notification);
                assertTrue(notification.extras.getString(Notification.EXTRA_TITLE).contains("OTP mô phỏng"));
                assertTrue(notification.extras.getString(Notification.EXTRA_TEXT).contains(code));
                assertTrue(notification.getTimeoutAfter() > 0 && notification.getTimeoutAfter() <= 60000);
                scenario.onActivity(a -> {
                    ((EditText) a.findViewById(R.id.edtOtpCode)).setText(code);
                    a.findViewById(R.id.btnSubmitOtp).performClick();
                });
                deadline = android.os.SystemClock.elapsedRealtime() + 15000;
                while (!phone.equals(auth.getCurrentUser().getPhoneNumber())
                        && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
                assertEquals(phone, auth.getCurrentUser().getPhoneNumber());
                assertEquals(uid, auth.getUid());
                deadline = android.os.SystemClock.elapsedRealtime() + 5000;
                boolean active;
                do {
                    active = false;
                    for (StatusBarNotification item : manager.getActiveNotifications())
                        if (item.getId() == EmulatorOtpNotifications.NOTIFICATION_ID) active = true;
                    if (active) Thread.sleep(100);
                } while (active && android.os.SystemClock.elapsedRealtime() < deadline);
                assertFalse("OTP notification should clear after verification", active);
            }
        } finally {
            manager.cancel(EmulatorOtpNotifications.NOTIFICATION_ID);
            try { if (fixture != null) Tasks.await(fixture.delete(), 30, TimeUnit.SECONDS); }
            finally {
                if (previous == null) auth.signOut();
                else Tasks.await(auth.updateCurrentUser(previous), 30, TimeUnit.SECONDS);
            }
        }
    }

    private void waitFor(ActivityScenario<MainActivity> scenario, int id) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean found = new AtomicBoolean();
            scenario.onActivity(a -> found.set(a.findViewById(id) != null));
            if (found.get()) return;
            Thread.sleep(100);
        }
        fail("Expected view " + id);
    }
}
