package vn.edu.ueh.thudinh.fashion2hand;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ContactVerificationFlowTest {
    private void waitScreen(ActivityScenario<MainActivity> scenario, int layout) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 90000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("currentScreen");
                    field.setAccessible(true); ready.set(field.getInt(activity) == layout);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            if (ready.get()) return;
            Thread.sleep(100);
        }
        fail("Expected screen " + layout);
    }

    @Test public void emailVerificationUnlocksHomeWithoutPhone() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance(); FirebaseUser previous = auth.getCurrentUser();
        FirebaseUser fixture = null;
        try {
            fixture = Tasks.await(auth.createUserWithEmailAndPassword("contact-ui-" + System.nanoTime()
                    + "@example.com", "Test-only-123!"), 45, TimeUnit.SECONDS).getUser();
            Map<String,Object> profile = new HashMap<>();
            profile.put("uid", fixture.getUid()); profile.put("name", "Contact verification test");
            profile.put("email", fixture.getEmail()); profile.put("role", "USER"); profile.put("accountStatus", "ACTIVE");
            profile.put("createdAt", FieldValue.serverTimestamp()); profile.put("updatedAt", FieldValue.serverTimestamp());
            Tasks.await(FirebaseFirestore.getInstance().collection("users").document(fixture.getUid()).set(profile), 45, TimeUnit.SECONDS);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                waitScreen(scenario, R.layout.activity_contact_required);
                scenario.recreate(); waitScreen(scenario, R.layout.activity_contact_required);
                scenario.onActivity(activity -> activity.findViewById(R.id.btnRequiredPhone).performClick());
                waitScreen(scenario, R.layout.activity_verify_phone);
                scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
                waitScreen(scenario, R.layout.activity_contact_required);
                VerifiedAccountFixture.verifyEmail(auth);
                scenario.onActivity(activity -> activity.findViewById(R.id.btnRefreshContactVerification).performClick());
                waitScreen(scenario, R.layout.activity_home);
                assertTrue(auth.getCurrentUser().isEmailVerified()); assertNull(auth.getCurrentUser().getPhoneNumber());
            }
        } finally {
            if (fixture != null) Tasks.await(fixture.delete(), 30, TimeUnit.SECONDS);
            if (previous == null) auth.signOut();
            else Tasks.await(auth.updateCurrentUser(previous), 45, TimeUnit.SECONDS);
        }
    }
}
