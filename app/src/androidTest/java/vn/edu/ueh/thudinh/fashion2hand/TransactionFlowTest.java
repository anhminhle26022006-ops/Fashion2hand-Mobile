package vn.edu.ueh.thudinh.fashion2hand;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;
import static org.junit.Assert.*;

/** Runs against the local Emulator Suite, never production. */
@RunWith(AndroidJUnit4.class)
public class TransactionFlowTest {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final MemberTwoRepository repo = MemberTwoRepository.getInstance();
    private <T> T await(Task<T> task) throws Exception { return Tasks.await(task, 45, TimeUnit.SECONDS); }
    private static final class User { String uid, email; }
    private User user(String label) throws Exception {
        auth.signOut(); User user = new User(); user.email = label + "-" + System.nanoTime() + "@example.com";
        user.uid = await(auth.createUserWithEmailAndPassword(user.email, "Test-only-123!")).getUser().getUid();
        Map<String, Object> profile = new HashMap<>(); profile.put("uid", user.uid); profile.put("name", label); profile.put("email", user.email);
        profile.put("role", "USER"); profile.put("accountStatus", "ACTIVE"); profile.put("createdAt", FieldValue.serverTimestamp()); profile.put("updatedAt", FieldValue.serverTimestamp());
        await(db.collection("users").document(user.uid).set(profile));
        Map<String, Object> address = new HashMap<>(); address.put("recipient", label); address.put("phone", "0901234567");
        address.put("province", "TP.HCM"); address.put("district", "Quận 1"); address.put("ward", "Bến Nghé"); address.put("street", "1 Đường kiểm thử");
        await(db.collection("users").document(user.uid).collection("addresses").document("home").set(address));
        VerifiedAccountFixture.verify(auth);
        return user;
    }
    private void signIn(User user) throws Exception { auth.signOut(); await(auth.signInWithEmailAndPassword(user.email, "Test-only-123!")); }
    private String post(String title, boolean free) throws Exception {
        DocumentReference ref = db.collection("posts").document(); Map<String, Object> data = new HashMap<>();
        data.put("ownerId", auth.getUid()); data.put("title", title); data.put("description", "Kiểm thử giao dịch"); data.put("condition", "GOOD");
        data.put("type", free ? "FREE" : "SALE"); data.put("price", free ? 0 : 120000); data.put("status", "ACTIVE");
        data.put("createdAt", FieldValue.serverTimestamp()); data.put("updatedAt", FieldValue.serverTimestamp()); await(ref.set(data)); return ref.getId();
    }
    private void waitFor(ActivityScenario<MainActivity> scenario, int id) throws Exception {
        waitUntil(scenario, a -> a.findViewById(id) != null, "View " + id);
    }
    private interface Check { boolean check(MainActivity activity); }
    private void waitUntil(ActivityScenario<MainActivity> scenario, Check check, String label) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean(); scenario.onActivity(a -> ready.set(check.check(a)));
            if (ready.get()) return; Thread.sleep(100);
        }
        fail("Timed out: " + label);
    }
    private void click(ActivityScenario<MainActivity> scenario, int id) throws Exception {
        waitUntil(scenario, a -> a.findViewById(id) != null && a.findViewById(id).isEnabled(), "Enabled " + id);
        scenario.onActivity(a -> { View view=a.findViewById(id); if(view instanceof RadioButton){view.performClick();assertTrue(((RadioButton)view).isChecked());}else assertTrue(view.performClick()); });
    }
    private View text(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private void waitText(ActivityScenario<MainActivity> scenario, String value) throws Exception {
        waitUntil(scenario, a -> text(a.findViewById(android.R.id.content), value) != null, value);
    }
    private String orderId(ActivityScenario<MainActivity> scenario) {
        String[] result = {""}; scenario.onActivity(a -> {
            try { java.lang.reflect.Field field = MainActivity.class.getDeclaredField("currentOrderId"); field.setAccessible(true); result[0] = (String) field.get(a); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }); return result[0];
    }
    private void status(String id, String value) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (value.equals(await(db.collection("orders").document(id).get()).getString("orderStatus"))) return;
            Thread.sleep(150);
        }
        fail("Order did not reach " + value);
    }
    private void openOrderFromProfile(ActivityScenario<MainActivity> scenario, String title, boolean seller) throws Exception {
        waitFor(scenario, R.id.navProfile); click(scenario, R.id.navProfile); click(scenario, R.id.btnMyCart);
        if (seller) click(scenario, R.id.tabSeller);
        waitText(scenario, title);
        scenario.onActivity(a -> {
            View view = text(a.findViewById(android.R.id.content), title);
            while (view != null && !view.hasOnClickListeners()) view = view.getParent() instanceof View ? (View) view.getParent() : null;
            assertNotNull(view); assertTrue(view.performClick());
        });
        waitFor(scenario, R.id.liveOrderTimeline);
    }
    @SuppressWarnings("unchecked") private Map<String, Object> result(com.google.firebase.functions.HttpsCallableResult result) { return (Map<String, Object>) result.getData(); }

    @Test public void checkoutBuyerSellerShipmentAndRealHistory() throws Exception {
        User seller = user("UI Seller"); String title = "Đơn UI " + System.nanoTime(); String postId = post(title, false);
        String otherPost = post("Món còn lại trong giỏ", false); User buyer = user("UI Buyer");
        Map<String, Object> item = new HashMap<>(); item.put("title", title); item.put("price", 1);
        await(repo.addCartItem(buyer.uid, postId, item)); item = new HashMap<>(item); item.put("title", "Món còn lại trong giỏ"); await(repo.addCartItem(buyer.uid, otherPost, item));
        String id;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.btnCartHome); click(scenario, R.id.btnCartHome); waitText(scenario, title); waitText(scenario, "Món còn lại trong giỏ");
            scenario.onActivity(a -> {
                View card = a.findViewById(android.R.id.content).findViewWithTag("cart|" + postId); assertNotNull(card);
                assertTrue(text(card, "Thanh toán món này").performClick());
            });
            waitFor(scenario, R.id.btnPlaceOrder); waitText(scenario, "120.000");
            scenario.recreate(); waitFor(scenario, R.id.btnPlaceOrder); click(scenario, R.id.btnPlaceOrder);
            waitFor(scenario, R.id.btnSimulatePaid); id = orderId(scenario); assertFalse(id.isEmpty());
            assertEquals(120000L, await(db.collection("orders").document(id).get()).getLong("price").longValue());
            assertFalse(await(db.collection("users").document(buyer.uid).collection("cartItems").document(postId).get()).exists());
            assertTrue(await(db.collection("users").document(buyer.uid).collection("cartItems").document(otherPost).get()).exists());
            click(scenario, R.id.btnSimulatePaid); status(id, "CONFIRMED"); waitText(scenario, "Đang giữ tiền");
        }
        signIn(seller);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openOrderFromProfile(scenario, title, true); click(scenario, R.id.btnSellerConfirm); status(id, "PREPARING");
            click(scenario, R.id.btnSellerReady); status(id, "READY_TO_SHIP"); click(scenario, R.id.btnCreateShipment); status(id, "SHIPPING");
            click(scenario, R.id.btnTrackShipping); waitFor(scenario, R.id.liveShipmentEvents);
            click(scenario, R.id.btnOpenDeliveryMap); waitText(scenario, "Chưa có vị trí từ backend");
            scenario.onActivity(a -> assertNull(a.findViewById(R.id.btnOpenTrackingMap)));
            click(scenario, R.id.btnTransactionBack); click(scenario, R.id.btnTransactionBack);
            await(repo.simulateShipmentLocation(id, 10.77, 106.7));
            click(scenario, R.id.btnTrackShipping); click(scenario, R.id.btnOpenDeliveryMap); waitText(scenario, "10.77, 106.7");
            waitFor(scenario, R.id.btnOpenTrackingMap); click(scenario, R.id.btnTransactionBack); click(scenario, R.id.btnTransactionBack);
            click(scenario, R.id.btnSimulateDelivered); status(id, "DELIVERED");
        }
        signIn(buyer);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openOrderFromProfile(scenario, title, false); click(scenario, R.id.btnConfirmReceived); status(id, "COMPLETED");
            waitText(scenario, "Đã giải ngân"); click(scenario, R.id.btnTransactionBack); click(scenario, R.id.btnTransactionHistory);
            waitText(scenario, title); waitText(scenario, "Đã giải ngân");
            screenshot("history-paid");
            click(scenario, R.id.filterHistorySell); waitText(scenario, "Chưa có giao dịch phù hợp bộ lọc.");
            click(scenario, R.id.filterHistoryBuy); waitText(scenario,title); scenario.recreate(); waitText(scenario,title);
            scenario.onActivity(a -> assertEquals(R.id.filterHistoryBuy,((RadioGroup)a.findViewById(R.id.historyFilters)).getCheckedRadioButtonId()));
        }
    }

    @Test public void allRequestsAndDisputePhotoArePersisted() throws Exception {
        User seller = user("UI Giver"); String suffix = String.valueOf(System.nanoTime());
        String firstPost = post("Yêu cầu thứ nhất " + suffix, true), secondPost = post("Yêu cầu thứ hai " + suffix, true), thirdPost = post("Yêu cầu thứ ba " + suffix, true);
        User buyer = user("UI Receiver");
        String first = String.valueOf(result(await(repo.createFreeRequest(firstPost, "Mình muốn nhận món thứ nhất", "home"))).get("requestId"));
        String second = String.valueOf(result(await(repo.createFreeRequest(secondPost, "Mình muốn nhận món thứ hai", "home"))).get("requestId"));
        await(repo.createFreeRequest(thirdPost, "Mình muốn nhận món thứ ba", "home"));
        signIn(seller); String id;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.navProfile); click(scenario, R.id.navProfile); click(scenario, R.id.btnMyOrders); click(scenario, R.id.tabGive);
            waitText(scenario, "3 yêu cầu");
            scenario.onActivity(a -> { assertEquals("3",((TextView)a.findViewById(R.id.statPendingRequests)).getText().toString()); assertTrue(a.findViewById(R.id.transactionRoleTabs) instanceof RadioGroup); });
            screenshot("transaction-overview"); click(scenario,R.id.transactionRequestRow);
            scenario.onActivity(a -> {
                View root = a.findViewById(R.id.freeRequestList); View card = root.findViewWithTag("request|" + second); assertNotNull(card);
                assertTrue(card.findViewById(R.id.btnAcceptFreeRequest).performClick());
            });
            waitFor(scenario, R.id.btnSellerConfirm); id = orderId(scenario);
            assertEquals("PENDING", await(db.collection("freeRequests").document(first).get()).getString("status"));
            assertEquals("ACCEPTED", await(db.collection("freeRequests").document(second).get()).getString("status"));
            click(scenario, R.id.btnSellerConfirm); click(scenario, R.id.btnSellerReady); click(scenario, R.id.btnCreateShipment); status(id, "SHIPPING");
        }
        signIn(buyer);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openOrderFromProfile(scenario, "Yêu cầu thứ hai " + suffix, false); click(scenario, R.id.btnReportOrder);
            waitFor(scenario, R.id.btnSubmitReport);
            scenario.onActivity(a -> {
                ((RadioGroup) a.findViewById(R.id.rgReportReason)).check(R.id.reportDamaged);
                ((EditText) a.findViewById(R.id.edtReportDescription)).setText("Ảnh bằng chứng kiểm thử tranh chấp được lưu lên backend.");
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("reportEvidenceItems"); field.setAccessible(true);
                    Bitmap photo = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888); photo.eraseColor(0xFF104536);
                    ((List<Object>) field.get(a)).add(photo);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            click(scenario, R.id.btnSubmitReport); waitFor(scenario, R.id.txtDisputeState); waitText(scenario, "Tranh chấp đang chờ xử lý");
            DocumentSnapshot dispute = await(db.collection("disputes").document(id).get()); assertTrue(dispute.exists());
            assertEquals(1, ((List<?>) dispute.get("evidencePaths")).size()); assertEquals(buyer.uid, dispute.getString("reporterId"));
        }
    }
    @Test public void selectedCartItemsCreateOneSellerOrderAfterRecreation() throws Exception {
        User seller = user("Multi UI Seller");
        String first = post("Multi First " + System.nanoTime(), false), second = post("Multi Second " + System.nanoTime(), false);
        User buyer = user("Multi UI Buyer");
        await(repo.addCartItem(buyer.uid, first, Collections.emptyMap()));
        await(repo.addCartItem(buyer.uid, second, Collections.emptyMap()));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitFor(scenario, R.id.btnCartHome); click(scenario, R.id.btnCartHome);
            for (String id : new String[]{first, second}) {
                waitUntil(scenario, a -> {
                    View card = a.findViewById(android.R.id.content).findViewWithTag("cart|" + id);
                    View choose = card == null ? null : text(card, "Chọn món này"); return choose != null && choose.isEnabled();
                }, "Cart item loaded " + id);
                scenario.onActivity(a -> ((CheckBox) text(a.findViewById(android.R.id.content).findViewWithTag("cart|" + id), "Chọn món này")).setChecked(true));
            }
            click(scenario, R.id.btnCheckout); waitFor(scenario, R.id.btnPlaceOrder); waitText(scenario, "240.000");
            scenario.recreate(); waitFor(scenario, R.id.btnPlaceOrder); waitText(scenario, "240.000"); click(scenario, R.id.btnPlaceOrder);
            waitFor(scenario, R.id.btnSimulatePaid); String id = orderId(scenario);
            DocumentSnapshot order = await(db.document("orders/" + id).get(Source.SERVER));
            assertEquals(2, ((List<?>) order.get("items")).size()); assertEquals(240000L, order.getLong("price").longValue());
            for (String postId : new String[]{first, second}) {
                assertEquals(id, await(db.document("posts/" + postId).get(Source.SERVER)).getString("reservedForOrderId"));
                assertFalse(await(db.document("users/" + buyer.uid + "/cartItems/" + postId).get(Source.SERVER)).exists());
            }
            click(scenario, R.id.btnSimulatePaid); status(id, "CONFIRMED");
            for (String postId : new String[]{first, second}) assertEquals("SOLD", await(db.document("posts/" + postId).get(Source.SERVER)).getString("status"));
            await(repo.cancelOrder(id)); status(id, "CANCELLED");
            for (String postId : new String[]{first, second}) assertEquals("ACTIVE", await(db.document("posts/" + postId).get(Source.SERVER)).getString("status"));
        }
    }
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap image = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        java.io.File directory = new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"design-qa");
        assertTrue(directory.exists() || directory.mkdirs());
        try(java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));}
        image.recycle();
    }
}