package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** UI fixtures only: no bank, payment callable or shipping-provider request is sent. */
@RunWith(AndroidJUnit4.class)
public class PaymentConfirmationUiTest {
    private static final class Host implements TransactionScreens.Host {
        boolean finished;
        public String uid(){return "buyer";} public String postId(){return "post";}
        public String orderId(){return "payment-ui-order";} public String requestId(){return "";}
        public boolean showing(int layout){return layout==R.layout.activity_order_detail;}
        public boolean signedIn(){return true;} public boolean checkoutFromCart(){return false;}
        public void open(int layout){} public void openOrder(String id){} public void openRequest(String id){}
        public void checkout(String id){} public void checkoutCart(List<String> ids){} public void back(){}
        public void requestFree(String postId,String note){}
        public void orders(boolean seller){assertFalse(seller);finished=true;}
        public void orderSnapshot(DocumentSnapshot order){} public void photo(Map<String,Object> data,ImageView image){}
        public void listen(ListenerRegistration listener){listener.remove();fail("Unexpected live listener");}
        public void error(Exception error){throw new AssertionError(error);} public void chat(String peer,String post){}
    }
    private void field(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private void invoke(Object target,String name,Class<?> type,Object value) throws Exception {
        Method method=type==null?target.getClass().getDeclaredMethod(name):target.getClass().getDeclaredMethod(name,type);
        method.setAccessible(true);if(type==null)method.invoke(target);else method.invoke(target,value);
    }
    private Map<String,Object> order(String payment,String state,boolean review){
        Map<String,Object> data=new HashMap<>();data.put("buyerId","buyer");data.put("sellerId","seller");
        data.put("paymentStatus",payment);data.put("orderStatus",state);data.put("paymentNeedsReview",review);
        data.put("orderType","PAID");data.put("shippingProvider","GHN");data.put("price",120000L);data.put("shippingFee",15000L);return data;
    }
    @Test public void emptyHistoryIsHiddenAndPaidBuyerCanFinishPlacingOrderWithoutCompletingDelivery() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{try{
                invoke(activity,"clearScreenListeners",null,null);field(activity,"currentScreen",R.layout.activity_login);
                Host host=new Host();TransactionScreens screens=new TransactionScreens(activity,host);
                screens.prepare(R.layout.activity_order_detail);
                field(screens,"order",order("PENDING","PENDING_PAYMENT",false));invoke(screens,"renderOrder",null,null);
                assertNull(activity.findViewById(R.id.liveMoneyEvents));assertNull(activity.findViewById(R.id.paymentSuccessMessage));
                assertNull(activity.findViewById(R.id.btnRefreshGhnShipment));
                field(screens,"order",order("PAID","CONFIRMED",true));invoke(screens,"renderOrder",null,null);
                assertNull(activity.findViewById(R.id.paymentSuccessMessage));assertNull(activity.findViewById(R.id.btnPaymentComplete));
                Map<String,Object> paid=order("PAID","CONFIRMED",false);
                field(screens,"order",paid);invoke(screens,"renderOrder",null,null);
                assertNull(activity.findViewById(R.id.liveMoneyEvents));
                assertEquals("Thanh toán thành công",((TextView)activity.findViewById(R.id.paymentSuccessMessage)).getText().toString());
                assertTrue(activity.findViewById(R.id.btnPaymentComplete).performClick());assertTrue(host.finished);
                assertEquals("CONFIRMED",paid.get("orderStatus"));
                Map<String,Object> shipment=new HashMap<>();shipment.put("trackingCode","DEMO-TRACKING");shipment.put("status","READY");
                field(screens,"shipment",shipment);invoke(screens,"renderOrder",null,null);
                assertEquals("Cập nhật vận chuyển",((TextView)activity.findViewById(R.id.btnRefreshGhnShipment)).getText().toString());
            }catch(Exception error){throw new AssertionError(error);}});
        }
    }
    @Test public void vnpayCompletionRequiresVerifiedPaidResultWithoutReviewOrCancelledOrder() throws Exception {
        Intent intent=new Intent(ApplicationProvider.getApplicationContext(),VnpayPaymentActivity.class);
        // No order reference on launch, so the Activity never starts a status request.
        try(ActivityScenario<VnpayPaymentActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(activity->{try{
                field(activity,"orderId","payment-ui-order");
                for(String[] result:new String[][]{{"PENDING","PENDING_PAYMENT"},{"FAILED","PAYMENT_FAILED"},{"PAID","CANCELLED"},{"PAID","PENDING_PAYMENT"}}){
                    Map<String,Object> data=new HashMap<>();data.put("paymentStatus",result[0]);data.put("orderStatus",result[1]);
                    invoke(activity,"render",Map.class,data);
                    assertEquals("Quay lại đơn hàng",((TextView)activity.findViewById(R.id.vnpayBack)).getText().toString());
                }
                Map<String,Object> paid=new HashMap<>();paid.put("paymentStatus","PAID");paid.put("orderStatus","CONFIRMED");paid.put("needsReview",true);
                invoke(activity,"render",Map.class,paid);
                assertEquals("Quay lại đơn hàng",((TextView)activity.findViewById(R.id.vnpayBack)).getText().toString());
                paid.put("needsReview",false);invoke(activity,"render",Map.class,paid);
                assertTrue(((TextView)activity.findViewById(R.id.vnpayStatus)).getText().toString().contains("Thanh toán thành công"));
                assertEquals("Xem đơn hàng",((TextView)activity.findViewById(R.id.vnpayBack)).getText().toString());
                assertEquals(View.GONE,activity.findViewById(R.id.vnpayOpen).getVisibility());
                assertEquals(View.GONE,activity.findViewById(R.id.vnpayRefresh).getVisibility());
                assertEquals("CONFIRMED",paid.get("orderStatus"));
                // Preserve the launch identity so ActivityScenario can track lifecycle cleanup.
                invoke(activity,"onNewIntent",Intent.class,new Intent(activity.getIntent()));
                assertEquals("Quay lại đơn hàng",((TextView)activity.findViewById(R.id.vnpayBack)).getText().toString());
                assertFalse(((TextView)activity.findViewById(R.id.vnpayStatus)).getText().toString().contains("Thanh toán thành công"));
            }catch(Exception error){throw new AssertionError(error);}});
        }
    }
    @Test public void verifiedPaymentAutomaticallyOpensTheCorrectOrderOnce() throws Exception {
        android.app.Instrumentation instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(MainActivity.class.getName(),new android.app.Instrumentation.ActivityResult(0,new Intent()),true);
        Intent launch=new Intent(ApplicationProvider.getApplicationContext(),VnpayPaymentActivity.class);
        try(ActivityScenario<VnpayPaymentActivity> scenario=ActivityScenario.launch(launch)){
            scenario.onActivity(activity->{try{
                field(activity,"orderId","verified-order");
                Map<String,Object> paid=new HashMap<>();paid.put("paymentStatus","PAID");paid.put("orderStatus","CONFIRMED");
                Intent destination=VnpayPaymentActivity.orderIntent(activity,"verified-order");
                assertEquals("verified-order",destination.getStringExtra("paymentOrderId"));
                assertEquals(MainActivity.class.getName(),destination.getComponent().getClassName());
                Method accept=VnpayPaymentActivity.class.getDeclaredMethod("acceptStatus",Map.class,boolean.class);accept.setAccessible(true);
                accept.invoke(activity,paid,false);accept.invoke(activity,paid,false);
                assertEquals(1,monitor.getHits());assertTrue(activity.isFinishing());
            }catch(Exception error){throw new AssertionError(error);}});
        }finally{instrumentation.removeMonitor(monitor);}
    }
}
