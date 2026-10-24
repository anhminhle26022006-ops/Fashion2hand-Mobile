package vn.edu.ueh.thudinh.fashion2hand;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Test-only trusted Auth Emulator fixture; never packaged in the application. */
final class VerifiedAccountFixture {
    static void verify(FirebaseAuth auth) throws Exception {
        verify(auth, true);
    }

    static void verifyEmail(FirebaseAuth auth) throws Exception {
        verify(auth, false);
    }

    private static void verify(FirebaseAuth auth, boolean includePhone) throws Exception {
        String uid = auth.getUid();
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://10.0.2.2:9099/identitytoolkit.googleapis.com/v1/projects/fashion2hand/accounts:update").openConnection();
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setConnectTimeout(30000); connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer owner");
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject().put("localId", uid).put("emailVerified", true);
        if (includePhone) body.put("phoneNumber", "+1" + (1000000000L + Math.floorMod(System.nanoTime(), 9000000000L)));
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != 200) throw new AssertionError("Auth fixture: " + connection.getResponseCode());
        } finally { connection.disconnect(); }
        Tasks.await(auth.getCurrentUser().reload(), 30, TimeUnit.SECONDS);
        Tasks.await(auth.getCurrentUser().getIdToken(true), 30, TimeUnit.SECONDS);
    }
}
