package vn.edu.ueh.thudinh.fashion2hand;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.ueh.thudinh.fashion2hand.repository.ChatRepository;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChatPaginationEmulatorTest {
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void waitFor(AtomicReference<ChatRepository.State> value, int count) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            ChatRepository.State state = value.get();
            if (state != null && state.error != null) throw state.error;
            if (state != null && !state.loading && !state.fromCache && state.messages.size() == count) return;
            Thread.sleep(100);
        }
        fail("Expected " + count + " messages; got " + (value.get() == null ? "no state" : value.get().messages.size()));
    }
    private JSONObject string(String value) throws Exception { return new JSONObject().put("stringValue", value); }
    private void seed(String project, String chatId, String sender) throws Exception {
        JSONArray writes = new JSONArray();
        for (int i = 0; i < 85; i++) {
            JSONObject fields = new JSONObject().put("senderId", string(sender)).put("text", string("History " + i))
                    .put("read", new JSONObject().put("booleanValue", false))
                    .put("createdAt", new JSONObject().put("timestampValue", "2020-01-01T00:00:00Z"));
            writes.put(new JSONObject().put("update", new JSONObject().put("name", "projects/" + project
                    + "/databases/(default)/documents/chats/" + chatId + "/messages/" + String.format(Locale.ROOT,"%03d",i)).put("fields", fields)));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL("http://10.0.2.2:8080/v1/projects/" + project + "/databases/(default)/documents:commit").openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
        connection.setRequestProperty("Authorization", "Bearer owner"); connection.setRequestProperty("Content-Type", "application/json"); connection.setDoOutput(true);
        try (java.io.OutputStream out = connection.getOutputStream()) { out.write(new JSONObject().put("writes",writes).toString().getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, connection.getResponseCode()); connection.disconnect();
    }
    @Test public void pagesRealtimeOfflineAndListenerReattachment() throws Exception {
        FirebaseApp primary = FirebaseApp.getInstance();
        FirebaseApp isolated = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(), primary.getOptions(), "chat-pages-" + System.nanoTime());
        FirebaseAuth auth = FirebaseAuth.getInstance(isolated); auth.useEmulator("10.0.2.2",9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance(isolated); db.useEmulator("10.0.2.2",8080);
        FirebaseFunctions functions = FirebaseFunctions.getInstance(isolated); functions.useEmulator("10.0.2.2",5001);
        String sender = Tasks.await(auth.signInAnonymously(),30,TimeUnit.SECONDS).getUser().getUid();
        Map<String,Object> profile = new HashMap<>(); profile.put("uid",sender); profile.put("name","Chat pagination test"); profile.put("role","USER"); profile.put("accountStatus","ACTIVE");
        Tasks.await(db.document("users/"+sender).set(profile),30,TimeUnit.SECONDS);
        // A second account is created on an isolated app; the user's default Auth session is untouched.
        FirebaseApp peerApp = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(), primary.getOptions(), "chat-peer-" + System.nanoTime());
        FirebaseAuth peerAuth = FirebaseAuth.getInstance(peerApp); peerAuth.useEmulator("10.0.2.2",9099);
        String peer = Tasks.await(peerAuth.signInAnonymously(),30,TimeUnit.SECONDS).getUser().getUid();
        FirebaseFirestore peerDb = FirebaseFirestore.getInstance(peerApp); peerDb.useEmulator("10.0.2.2",8080);
        profile.put("uid",peer); Tasks.await(peerDb.document("users/"+peer).set(profile),30,TimeUnit.SECONDS);
        Map<String,Object> chat = new HashMap<>(); chat.put("otherUserId",peer);
        Map<?,?> result = (Map<?,?>) Tasks.await(functions.getHttpsCallable("createChat").call(chat),30,TimeUnit.SECONDS).getData();
        String chatId = (String) result.get("chatId"); seed(primary.getOptions().getProjectId(),chatId,sender);
        AtomicReference<ChatRepository.State> state = new AtomicReference<>();
        Observer<ChatRepository.State> observer = state::set;
        ChatRepository.MessageFeed feed = new ChatRepository(db).messages(chatId);
        try {
            main(() -> feed.observeForever(observer)); waitFor(state,40);
            main(feed::loadOlder); waitFor(state,80);
            main(feed::loadOlder); waitFor(state,85);
            assertFalse(state.get().hasMore);
            Set<String> ids = new HashSet<>(); state.get().messages.forEach(message -> ids.add(message.id)); assertEquals(85,ids.size());
            Map<String,Object> message = new HashMap<>(); message.put("chatId",chatId); message.put("text","Live message"); message.put("clientMessageId","live");
            Tasks.await(functions.getHttpsCallable("sendMessage").call(message),30,TimeUnit.SECONDS); waitFor(state,86);
            Tasks.await(db.disableNetwork(),10,TimeUnit.SECONDS);
            long deadline = android.os.SystemClock.elapsedRealtime()+15000;
            while (!state.get().fromCache && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(100);
            assertTrue(state.get().fromCache);
            Tasks.await(db.enableNetwork(),10,TimeUnit.SECONDS); waitFor(state,86);
            main(() -> feed.removeObserver(observer)); assertFalse(feed.hasObservers());
            main(() -> feed.observeForever(observer)); waitFor(state,40);
            main(feed::loadOlder); waitFor(state,80);
            main(feed::loadOlder); waitFor(state,86);
        } finally {
            main(() -> feed.removeObserver(observer));
            Tasks.await(db.terminate(),15,TimeUnit.SECONDS); Tasks.await(peerDb.terminate(),15,TimeUnit.SECONDS);
            Tasks.await(auth.getCurrentUser().delete(),15,TimeUnit.SECONDS); Tasks.await(peerAuth.getCurrentUser().delete(),15,TimeUnit.SECONDS);
        }
    }
}