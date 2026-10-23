package vn.edu.ueh.thudinh.fashion2hand;

import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;

/** Uses a secondary FirebaseApp so the user's signed-in session is preserved. */
@RunWith(AndroidJUnit4.class)
public class NotificationInboxEmulatorTest {
    private interface Check { boolean ready(MainActivity activity); }
    private void waitFor(ActivityScenario<MainActivity> scenario, Check check) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean done = new AtomicBoolean();
            scenario.onActivity(a -> done.set(check.ready(a)));
            if (done.get()) return;
            Thread.sleep(150);
        }
        fail("Timed out waiting for inbox state");
    }

    private void seed(String project, String uid, String id, String type) throws Exception {
        String endpoint = "http://10.0.2.2:8080/v1/projects/" + project
                + "/databases/(default)/documents/users/" + uid + "/notifications/" + id;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
        connection.setRequestMethod("PATCH"); connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer owner");
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject fields = new JSONObject();
        fields.put("type", new JSONObject().put("stringValue", type));
        fields.put("title", new JSONObject().put("stringValue", "Thông báo kiểm thử " + id));
        fields.put("body", new JSONObject().put("stringValue", "Nội dung từ backend"));
        fields.put("read", new JSONObject().put("booleanValue", false));
        fields.put("createdAt", new JSONObject().put("timestampValue", "2026-10-23T10:00:00Z"));
        try (java.io.OutputStream out = connection.getOutputStream()) {
            out.write(new JSONObject().put("fields", fields).toString().getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, connection.getResponseCode()); connection.disconnect();
    }

    @Test public void inboxStreamsFiltersAndPersistsReadStateForOnlyItsOwner() throws Exception {
        FirebaseApp primary = FirebaseApp.getInstance();
        FirebaseApp isolated = FirebaseApp.initializeApp(primary.getApplicationContext(), primary.getOptions(), "inbox-" + System.nanoTime());
        FirebaseAuth auth = FirebaseAuth.getInstance(isolated); auth.useEmulator("10.0.2.2", 9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance(isolated); db.useEmulator("10.0.2.2", 8080);
        String uid = Tasks.await(auth.signInAnonymously(), 30, TimeUnit.SECONDS).getUser().getUid();
        List<ListenerRegistration> listeners = new ArrayList<>();
        AtomicReference<String> opened = new AtomicReference<>();
        String[] filter = {"all"}; boolean[] hide = {false};
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                try {
                    java.lang.reflect.Method clear = MainActivity.class.getDeclaredMethod("clearScreenListeners");
                    clear.setAccessible(true); clear.invoke(a);
                    java.lang.reflect.Field screen = MainActivity.class.getDeclaredField("currentScreen");
                    screen.setAccessible(true); screen.setInt(a, R.layout.activity_notifications);
                } catch (Exception error) { throw new AssertionError(error); }
                a.setContentView(R.layout.activity_notifications);
                new NotificationInbox(a, new NotificationInbox.Host() {
                    public String uid() { return uid; }
                    public void listen(ListenerRegistration value) { listeners.add(value); }
                    public String filter() { return filter[0]; }
                    public void filter(String value) { filter[0] = value; }
                    public boolean hideRead() { return hide[0]; }
                    public void hideRead(boolean value) { hide[0] = value; }
                    public void open(DocumentSnapshot doc) { opened.set(doc.getId()); }
                }, db).bind();
            });
            waitFor(scenario, a -> "Bạn chưa có thông báo.".contentEquals(((TextView)a.findViewById(R.id.notificationStatus)).getText()));
            String project = primary.getOptions().getProjectId();
            seed(project, uid, "order", "ORDER_UPDATED");
            seed(project, uid, "message", "MESSAGE");
            seed(project, uid, "payment", "PAYMENT_UPDATED");
            seed(project, uid, "request", "FREE_REQUEST");
            seed(project, uid + "-foreign", "foreign", "MESSAGE");
            waitFor(scenario, a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildCount() == 4);
            scenario.onActivity(a -> a.findViewById(R.id.filterNotifMessages).performClick());
            waitFor(scenario, a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildCount() == 1);
            scenario.onActivity(a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildAt(0).performClick());
            waitFor(scenario, a -> "message".equals(opened.get()));
            DocumentSnapshot saved = Tasks.await(db.document("users/" + uid + "/notifications/message").get(Source.SERVER), 30, TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, saved.getBoolean("read")); assertNotNull(saved.getTimestamp("readAt"));
            try {
                Tasks.await(db.document("users/" + uid + "-foreign/notifications/foreign").get(Source.SERVER), 30, TimeUnit.SECONDS);
                fail("Foreign inbox should be denied");
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof FirebaseFirestoreException);
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ((FirebaseFirestoreException)expected.getCause()).getCode());
            }
            scenario.onActivity(a -> a.findViewById(R.id.filterNotifAll).performClick());
            waitFor(scenario, a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildCount() == 4);
            seed(project, uid, "new-event", "SHIPMENT_UPDATED");
            waitFor(scenario, a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildCount() == 5);
            scenario.onActivity(a -> a.findViewById(R.id.btnNotificationsMore).performClick());
            onView(withText("Đánh dấu tất cả đã đọc")).perform(click());
            waitFor(scenario, a -> a.findViewById(R.id.btnNotificationsMore).isEnabled());
            QuerySnapshot allRead = Tasks.await(db.collection("users").document(uid).collection("notifications").get(Source.SERVER), 30, TimeUnit.SECONDS);
            assertEquals(5, allRead.size());
            for (DocumentSnapshot doc : allRead) assertEquals(Boolean.TRUE, doc.getBoolean("read"));
            scenario.onActivity(a -> a.findViewById(R.id.btnNotificationsMore).performClick());
            onView(withText("Ẩn thông báo đã đọc")).perform(click());
            waitFor(scenario, a -> ((LinearLayout)a.findViewById(R.id.notificationList)).getChildCount() == 0);
            Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS);
            waitFor(scenario, a -> ((TextView)a.findViewById(R.id.notificationStatus)).getText().toString().contains("Đang chờ kết nối backend"));
            Tasks.await(db.enableNetwork(), 10, TimeUnit.SECONDS);
            waitFor(scenario, a -> "Không có thông báo phù hợp bộ lọc.".contentEquals(((TextView)a.findViewById(R.id.notificationStatus)).getText()));
        } finally {
            for (ListenerRegistration listener : listeners) listener.remove();
            Tasks.await(db.terminate(), 15, TimeUnit.SECONDS);
            Tasks.await(auth.getCurrentUser().delete(), 15, TimeUnit.SECONDS);
            // Keep the secondary app alive until instrumentation process teardown;
            // the Auth emulator transport can retain it across test classes.
        }
    }
}
