package vn.edu.ueh.thudinh.fashion2hand;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Development only: reads the current Auth Emulator session, never real SMS. */
final class EmulatorOtpNotifications implements AutoCloseable {
    static final String CHANNEL = "emulator_otp";
    static final int NOTIFICATION_ID = 9099;
    private final ComponentActivity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> permission;
    private final FirebaseAuth.AuthStateListener authListener;
    private int generation;
    private boolean closed;
    private String owner = "";
    private String session = "";
    private String pendingPhone;
    private String pendingCode;
    private long expiresAt;

    EmulatorOtpNotifications(ComponentActivity activity) {
        this.activity = activity;
        permission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!current() || pendingCode == null) return;
            if (granted) post();
            else new AlertDialog.Builder(activity).setTitle("OTP mô phỏng — chưa bật thông báo")
                    .setMessage(pendingPhone + "\nMã OTP: " + pendingCode
                            + "\nCho phép thông báo của ứng dụng để xem mã trên thanh thông báo.")
                    .setPositiveButton("Đóng", null).show();
        });
        authListener = auth -> { if (!owner.isEmpty() && !owner.equals(auth.getUid())) clear(); };
        if (enabled()) FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    private boolean enabled() { return BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS; }

    void show(String phone, String verificationId) {
        if (!enabled() || closed || verificationId.isEmpty() || verificationId.equals(session)) return;
        clear();
        owner = FirebaseAuth.getInstance().getUid();
        if (owner == null) { owner = ""; return; }
        session = verificationId;
        expiresAt = SystemClock.elapsedRealtime() + 60000;
        int request = generation;
        String project = FirebaseApp.getInstance().getOptions().getProjectId();
        worker.execute(() -> {
            try {
                JSONObject payload = readCodes(project);
                String code = findCode(payload, phone, verificationId);
                main.post(() -> {
                    if (request != generation || !current() || code == null) return;
                    pendingPhone = phone; pendingCode = code;
                    if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS);
                    else post();
                });
            } catch (Exception ignored) {
                main.post(() -> {
                    if (request == generation && current()) android.widget.Toast.makeText(activity,
                            "Không lấy được OTP mô phỏng. Kiểm tra Auth Emulator rồi gửi lại mã.",
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean current() {
        return enabled() && !closed && !activity.isDestroyed() && !activity.isFinishing()
                && !owner.isEmpty() && owner.equals(FirebaseAuth.getInstance().getUid())
                && SystemClock.elapsedRealtime() < expiresAt;
    }

    static String findCode(JSONObject payload, String phone, String session) throws Exception {
        JSONArray codes = payload.getJSONArray("verificationCodes");
        for (int i = 0; i < codes.length(); i++) {
            JSONObject item = codes.getJSONObject(i);
            String code = item.optString("code");
            if (phone.equals(item.optString("phoneNumber")) && session.equals(item.optString("sessionInfo"))
                    && code.matches("[0-9]{6}")) return code;
        }
        return null;
    }

    private JSONObject readCodes(String project) throws Exception {
        // This endpoint exists only in the local Auth Emulator.
        URI uri = new URI("http", null, BuildConfig.FIREBASE_EMULATOR_HOST, 9099,
                "/emulator/v1/projects/" + project + "/verificationCodes", null, null);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Auth Emulator unavailable");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 1024 * 1024) throw new IOException("Response too large");
                    out.write(buffer, 0, count);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    private void post() {
        if (!current() || pendingCode == null) return;
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "OTP mô phỏng (Firebase Emulator)",
                NotificationManager.IMPORTANCE_HIGH));
        Intent intent = new Intent(activity, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(activity, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = pendingPhone + " · Mã OTP: " + pendingCode;
        Notification notification = new Notification.Builder(activity, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("OTP mô phỏng — Fashion2hand")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setTimeoutAfter(Math.max(1, expiresAt - SystemClock.elapsedRealtime())).build();
        manager.notify(NOTIFICATION_ID, notification);
        pendingCode = null;
    }

    void clear() {
        generation++; session = ""; owner = ""; pendingCode = null; pendingPhone = null;
        if (enabled()) activity.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    @Override public void close() {
        clear(); closed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        if (enabled()) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
    }
}
