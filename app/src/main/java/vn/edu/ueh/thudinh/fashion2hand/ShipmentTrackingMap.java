package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Address markers and recorded checkpoints; no GPS permissions or fabricated route. */
final class ShipmentTrackingMap extends LinearLayout {
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String PAGE = "https://" + ASSET_HOST + "/tracking/map.html";
    private static final java.util.concurrent.Executor GEOCODING = Executors.newFixedThreadPool(2);
    interface Resolver { void resolve(String address, Consumer<double[]> result); }

    static Resolver geocoder(Activity activity) {
        android.content.Context context = activity.getApplicationContext();
        return (address, result) -> {
            if (!Geocoder.isPresent()) { result.accept(null); return; }
            Geocoder geocoder = new Geocoder(context, new Locale("vi", "VN"));
            Consumer<List<Address>> resolved = addresses -> {
                Address first = addresses == null || addresses.isEmpty() ? null : addresses.get(0);
                result.accept(first != null && first.hasLatitude() && first.hasLongitude()
                        && (first.getCountryCode() == null || "VN".equalsIgnoreCase(first.getCountryCode()))
                        ? new double[]{first.getLatitude(), first.getLongitude()} : null);
            };
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocationName(address + ", Việt Nam", 1, new Geocoder.GeocodeListener() {
                    public void onGeocode(List<Address> addresses) { resolved.accept(addresses); }
                    public void onError(String message) { result.accept(null); }
                });
            } else GEOCODING.execute(() -> {
                try { resolved.accept(geocoder.getFromLocationName(address + ", Việt Nam", 1)); }
                catch (IOException | RuntimeException error) { result.accept(null); }
            });
        };
    }

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView state;
    private final JSONArray points = new JSONArray();
    private final boolean[] finished = {false, false};
    private WebView web;
    private boolean pageReady, mapRendered, mapFailed, disposed;
    private int renderVersion;

    ShipmentTrackingMap(Activity activity, Map<String,Object> pickup, Map<String,Object> delivery,
                        Map<String,Object> checkpoint, Resolver resolver, Map<String,double[]> cache) {
        super(activity); this.activity = activity;
        setOrientation(VERTICAL);
        state = label("Đang định vị điểm gửi và điểm nhận…"); state.setId(R.id.shipmentMapState);
        addView(state);
        String from = AddressPicker.format(pickup), to = AddressPicker.format(delivery);
        address("G · Điểm gửi", from, R.id.shipmentPickupAddress);
        address("N · Điểm nhận", to, R.id.shipmentDeliveryAddress);
        if (!from.isEmpty() && !to.isEmpty()) {
            Button directions = new Button(activity); directions.setId(R.id.btnShipmentDirections);
            directions.setText("Mở chỉ đường gửi → nhận"); directions.setAllCaps(false);
            directions.setOnClickListener(v -> open("https://www.google.com/maps/dir/?api=1&origin="
                    + Uri.encode(from) + "&destination=" + Uri.encode(to)));
            addView(directions, new LayoutParams(-1, -2));
        }
        TextView legend = label("G: điểm gửi · N: điểm nhận · ●: mốc cập nhật\nVị trí tham khảo theo địa chỉ. Các mốc mô phỏng được ghi riêng.");
        addView(legend);
        Object lat = checkpoint.get("lat"), lng = checkpoint.get("lng");
        if (lat instanceof Number && lng instanceof Number) addPoint(new double[]{((Number)lat).doubleValue(), ((Number)lng).doubleValue()},
                "checkpoint", "SIMULATED".equals(checkpoint.get("source")) ? "Mốc cập nhật mô phỏng" : "Mốc cập nhật vận chuyển");
        locate(from, 0, "pickup", "Điểm gửi", resolver, cache);
        locate(to, 1, "delivery", "Điểm nhận", resolver, cache);
    }

    private TextView label(String value) {
        TextView text = new TextView(activity); text.setText(value); text.setTextColor(0xFF315447);
        text.setTextSize(13); text.setPadding(0, dp(6), 0, dp(6)); return text;
    }
    private void address(String title, String address, int id) {
        TextView text = label(title + "\n" + (address.isEmpty() ? "Chưa lưu địa chỉ cho điểm này." : address));
        text.setId(id); addView(text);
        if (!address.isEmpty()) text.setOnClickListener(v -> open("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address)));
    }
    private void open(String url) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException error) { Toast.makeText(activity, "Chưa có ứng dụng mở bản đồ.", Toast.LENGTH_LONG).show(); }
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private void locate(String address, int index, String kind, String title, Resolver resolver, Map<String,double[]> cache) {
        if (address.isEmpty()) { finished[index] = true; updateState(); return; }
        Consumer<double[]> complete = coordinates -> main.post(() -> {
            if (disposed || finished[index]) return;
            finished[index] = true;
            if (valid(coordinates)) {
                if (cache.size() >= 32) cache.clear(); cache.put(address, coordinates.clone());
                addPoint(coordinates, kind, title + ": " + address);
            }
            updateState();
        });
        if (cache.containsKey(address)) complete.accept(cache.get(address));
        else {
            main.postDelayed(() -> complete.accept(null), 12000);
            try { resolver.resolve(address, complete); }
            catch (RuntimeException error) { complete.accept(null); }
        }
    }
    static boolean valid(double[] point) {
        return point != null && point.length == 2 && Double.isFinite(point[0]) && Double.isFinite(point[1])
                && Math.abs(point[0]) <= 90 && Math.abs(point[1]) <= 180;
    }
    private void addPoint(double[] coordinates, String kind, String title) {
        if (!valid(coordinates) || disposed) return;
        try {
            JSONObject point = new JSONObject(); point.put("lat", coordinates[0]); point.put("lng", coordinates[1]);
            point.put("kind", kind); point.put("label", title); points.put(point);
        } catch (org.json.JSONException impossible) { throw new IllegalArgumentException(impossible); }
        if (web == null) createWebView();
        updateMap();
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void createWebView() {
        web = new WebView(activity); web.setId(R.id.shipmentMap); web.setContentDescription("Bản đồ điểm gửi và điểm nhận");
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setGeolocationEnabled(false); web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setUserAgentString(web.getSettings().getUserAgentString() + " Fashion2hand/" + BuildConfig.VERSION_NAME);
        web.setVerticalScrollBarEnabled(false); web.setHorizontalScrollBarEnabled(false);
        web.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != MotionEvent.ACTION_UP && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            return false;
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !ASSET_HOST.equals(uri.getHost())) return null;
                String name = uri.getLastPathSegment();
                if (Arrays.asList("map.html", "map.js", "map.css", "leaflet.js", "leaflet.css").contains(name)) {
                    try { return new WebResourceResponse(name.endsWith(".js") ? "application/javascript" : name.endsWith(".css") ? "text/css" : "text/html",
                            "UTF-8", getResources().getAssets().open("tracking/" + name)); }
                    catch (IOException ignored) { }
                }
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (PAGE.equals(request.getUrl().toString())) return false;
                if ("https".equals(request.getUrl().getScheme())) open(request.getUrl().toString());
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!disposed && PAGE.equals(url)) { pageReady = true; updateMap(); }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!disposed && request.isForMainFrame()) { mapFailed = true; updateState(); }
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (web == view) {
                    web = null; removeView(view); view.destroy(); mapFailed = true; updateState();
                }
                return true;
            }
        });
        addView(web, 0, new LayoutParams(-1, dp(320))); web.loadUrl(PAGE);
    }
    private void updateMap() {
        if (disposed || !pageReady || web == null) return;
        WebView current = web; int version = ++renderVersion;
        current.evaluateJavascript("typeof window.renderTracking === 'function' ? window.renderTracking(" + points + ") : -1", count -> {
            if (disposed || web != current || version != renderVersion) return;
            mapRendered = String.valueOf(points.length()).equals(count);
            mapFailed = !mapRendered; updateState();
        });
    }
    private void updateState() {
        if (!finished[0] || !finished[1]) return;
        if (mapFailed) { state.setText("Không tải được bản đồ. Chạm địa chỉ để mở trên ứng dụng bản đồ."); return; }
        if (points.length() > 0 && !mapRendered) { state.setText("Đang tải bản đồ điểm gửi và điểm nhận…"); return; }
        state.setText(points.length() == 0 ? "Chưa định vị được địa chỉ. Chạm địa chỉ để mở trên ứng dụng bản đồ."
                : "Chạm các điểm trên bản đồ để xem địa chỉ. Lịch sử cập nhật ở bên dưới.");
    }
    void close() {
        disposed = true; main.removeCallbacksAndMessages(null);
        if (web != null) { WebView old = web; web = null; removeView(old); old.stopLoading(); old.destroy(); }
    }
    @Override protected void onDetachedFromWindow() { close(); super.onDetachedFromWindow(); }
}
