package vn.edu.ueh.thudinh.fashion2hand;

import android.webkit.WebView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShipmentTrackingMapTest {
    private Map<String,Object> address(String street) {
        Map<String,Object> data = new HashMap<>(); data.put("street", street); data.put("province", "TP.HCM"); return data;
    }
    private void showMap(MainActivity activity, ShipmentTrackingMap map) {
        try {
            java.lang.reflect.Method clear = MainActivity.class.getDeclaredMethod("clearScreenListeners");
            clear.setAccessible(true); clear.invoke(activity);
            java.lang.reflect.Field screen = MainActivity.class.getDeclaredField("currentScreen");
            screen.setAccessible(true); screen.setInt(activity, R.layout.activity_login);
            activity.setContentView(map);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private String javascript(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>("No WebView/callback");
        scenario.onActivity(activity -> {
            WebView map = activity.findViewById(R.id.shipmentMap);
            if (map == null) { completed.countDown(); return; }
            map.evaluateJavascript(script, result -> { value.set(result); completed.countDown(); });
        });
        completed.await(3, TimeUnit.SECONDS);
        return value.get();
    }
    @Test public void bundledMapShowsBothEndpointsAndEscapesAddressLabels() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1800);
            String hostile = "Điểm gửi <img src=x onerror=alert(1)>";
            scenario.onActivity(a -> showMap(a, new ShipmentTrackingMap(a, address(hostile), address("Điểm nhận"), Collections.emptyMap(),
                    (value, done) -> done.accept(value.startsWith("Điểm gửi") ? new double[]{10.77,106.7} : new double[]{10.79,106.72}), new HashMap<>())));
            String count = "Not loaded";
            long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
            while (android.os.SystemClock.elapsedRealtime() < deadline && !"2".equals(count)) {
                count = javascript(scenario, "typeof markers === 'undefined' ? 0 : markers.getLayers().length");
                if (!"2".equals(count)) Thread.sleep(150);
            }
            assertEquals("Bundled map did not render: " + count, "2", count);
            assertEquals("true", javascript(scenario,
                    "markers.getLayers()[0].getPopup().getContent().textContent.includes('<img') && document.querySelectorAll('.leaflet-popup img').length === 0"));
            scenario.onActivity(a -> assertTrue(((TextView)a.findViewById(R.id.shipmentMapState)).getText().toString().contains("Chạm các điểm")));
            android.graphics.Bitmap screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            java.io.File directory = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("qa");
            assertNotNull(screenshot); assertNotNull(directory);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, "tracking-map.png"))) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } finally { screenshot.recycle(); }
        }
    }
    @Test public void unresolvedOrMissingAddressKeepsTextAndNeverInventsCoordinates() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1800);
            scenario.onActivity(a -> showMap(a, new ShipmentTrackingMap(a, Collections.emptyMap(), address("Địa chỉ chưa định vị"), Collections.emptyMap(),
                    (value, done) -> done.accept(null), new HashMap<>())));
            Thread.sleep(300);
            scenario.onActivity(a -> {
                assertNull(a.findViewById(R.id.shipmentMap));
                assertNull(a.findViewById(R.id.btnShipmentDirections));
                assertTrue(((TextView)a.findViewById(R.id.shipmentDeliveryAddress)).getText().toString().contains("Địa chỉ chưa định vị"));
                assertTrue(((TextView)a.findViewById(R.id.shipmentMapState)).getText().toString().contains("Chưa định vị"));
            });
        }
    }
}
