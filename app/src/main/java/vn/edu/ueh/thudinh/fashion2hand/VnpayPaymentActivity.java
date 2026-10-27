package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Map;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;

/** Deep links carry only a reference. Authenticated server reads decide the result. */
public final class VnpayPaymentActivity extends AppCompatActivity {
    private final MemberTwoRepository repo=MemberTwoRepository.getInstance();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String orderId="",attemptId=""; private boolean busy=false,visible=false;
    private int polls=0; private long generation=0;
    private boolean autoStart=false,paymentOpened=false,navigated=false;
    private long lastQueryAt=-60000;
    private TextView status; private Button open, complete;
    private final Runnable poll=()->{if(visible&&!busy&&polls++<24)refresh();};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);androidx.activity.EdgeToEdge.enable(this);setContentView(R.layout.activity_vnpay_payment);
        MainNavigation.attach(this,R.layout.activity_vnpay_payment,destination->{
            navigated=true;generation++;handler.removeCallbacks(poll);
            startActivity(new Intent(this,MainActivity.class).putExtra("navigationScreen",destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        MainNavigation.applyInsets(this);
        status=findViewById(R.id.vnpayStatus);open=findViewById(R.id.vnpayOpen);
        complete=findViewById(R.id.vnpayBack);
        orderId=state==null?readOrder(getIntent()):state.getString("orderId","");
        attemptId=state==null?"":state.getString("attemptId","");
        autoStart=getIntent().getBooleanExtra("startPayment",false);
        paymentOpened=state!=null&&state.getBoolean("paymentOpened");
        open.setOnClickListener(v->startPayment());
        findViewById(R.id.vnpayRefresh).setOnClickListener(v->{polls=0;refresh();});
        complete.setOnClickListener(v->{if(VnpayLinks.validOrderId(orderId))openOrder();else finish();});
    }
    private static String readOrder(Intent intent) {
        Uri uri=intent.getData();String id;
        if(uri!=null)return VnpayLinks.orderFromLink(uri.toString());
        id=intent.getStringExtra("orderId");
        return VnpayLinks.validOrderId(id)?id:"";
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);generation++;handler.removeCallbacks(poll);
        orderId=readOrder(intent);attemptId="";busy=false;polls=0;
        autoStart=intent.getBooleanExtra("startPayment",false);paymentOpened=false;navigated=false;lastQueryAt=-60000;
        status.setText("Đang xác nhận thanh toán…");status.setTextColor(0xFF173B32);complete.setText("Quay lại đơn hàng");
        open.setVisibility(View.VISIBLE);findViewById(R.id.vnpayRefresh).setVisibility(View.VISIBLE);
        refresh();
    }
    @Override protected void onResume(){super.onResume();visible=true;polls=0;refresh();}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("orderId",orderId);state.putString("attemptId",attemptId);state.putBoolean("paymentOpened",paymentOpened);super.onSaveInstanceState(state);}
    private boolean ready(){
        if(orderId.isEmpty()){status.setText("Liên kết đơn hàng không hợp lệ.");open.setEnabled(false);return false;}
        if(FirebaseAuth.getInstance().getCurrentUser()==null){status.setText("Hãy đăng nhập Fashion2hand rồi mở lại đơn hàng để kiểm tra thanh toán.");open.setEnabled(false);return false;}
        return true;
    }
    private void loading(boolean value){busy=value;findViewById(R.id.vnpayProgress).setVisibility(value?View.VISIBLE:View.GONE);open.setEnabled(!value);findViewById(R.id.vnpayRefresh).setEnabled(!value);}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){return value instanceof Map?(Map<String,Object>)value:java.util.Collections.emptyMap();}
    private void failed(Exception error){status.setText("Chưa thể xác nhận kết quả thanh toán. Ứng dụng sẽ kiểm tra lại. Bạn có thể xem đơn hàng bên dưới.");schedulePoll();}
    private void schedulePoll(){handler.removeCallbacks(poll);if(visible&&polls<24)handler.postDelayed(poll,5000);}
    static boolean confirmed(Map<String,Object> data){
        return "PAID".equals(data.get("paymentStatus"))&&!Boolean.TRUE.equals(data.get("needsReview"))
                &&java.util.Arrays.asList("CONFIRMED","PREPARING","READY_TO_SHIP","SHIPPING","DELIVERED","COMPLETED").contains(data.get("orderStatus"));
    }
    static Intent orderIntent(android.content.Context context,String id){
        return new Intent(context,MainActivity.class).putExtra("paymentOrderId",id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    private void openOrder(){if(navigated)return;navigated=true;handler.removeCallbacks(poll);startActivity(orderIntent(this,orderId));finish();}
    private void acceptStatus(Map<String,Object> data,boolean mayQuery){
        render(data);
        if(confirmed(data)){if(visible)openOrder();return;}
        if(!"PENDING_PAYMENT".equals(data.get("orderStatus"))||"PAID".equals(data.get("paymentStatus"))||Boolean.TRUE.equals(data.get("needsReview")))return;
        if(autoStart&&!paymentOpened){autoStart=false;startPayment();return;}
        if(mayQuery&&!attemptId.isEmpty()&&android.os.SystemClock.elapsedRealtime()-lastQueryAt>=60000){reconcile();return;}
        schedulePoll();
    }
    private void refresh(){
        if(busy||!ready())return;loading(true);long token=generation;
        repo.vnpayStatus(orderId).addOnCompleteListener(task->{
            if(isDestroyed()||generation!=token)return;loading(false);
            if(!task.isSuccessful()){failed(task.getException());return;}
            acceptStatus(map(task.getResult().getData()),true);
        });
    }
    private void render(Map<String,Object> data){
        String payment=String.valueOf(data.get("paymentStatus")),order=String.valueOf(data.get("orderStatus"));
        attemptId=String.valueOf(data.getOrDefault("attemptId",""));
        boolean paid="PAID".equals(payment),pending="PENDING_PAYMENT".equals(order)&&!paid;
        boolean confirmed=confirmed(data);
        status.setText(Boolean.TRUE.equals(data.get("needsReview"))?"Kết quả thanh toán đang được kiểm tra. Vui lòng xem lại đơn hàng sau.":confirmed?"Thanh toán thành công.\nBạn đã hoàn tất đặt hàng.":pending?"Đang chờ xác nhận thanh toán. Kết quả sẽ tự cập nhật khi bạn quay lại ứng dụng.":"Đơn hàng chưa hoàn tất thanh toán. Xem chi tiết đơn để tiếp tục.");
        status.setTextColor(confirmed?0xFF1F6B48:0xFF173B32);
        complete.setText(confirmed?"Xem đơn hàng":"Quay lại đơn hàng");
        open.setVisibility(confirmed?View.GONE:View.VISIBLE);
        findViewById(R.id.vnpayRefresh).setVisibility(confirmed?View.GONE:View.VISIBLE);
        open.setEnabled(pending);
        handler.removeCallbacks(poll);
    }
    private void startPayment(){
        if(busy||!ready())return;loading(true);long token=generation;
        repo.createVnpayPayment(orderId).addOnCompleteListener(task->{
            if(isDestroyed()||generation!=token)return;loading(false);
            if(!task.isSuccessful()){failed(task.getException());return;}
            Map<String,Object> data=map(task.getResult().getData());Uri uri=Uri.parse(String.valueOf(data.get("paymentUrl")));
            if(!VnpayLinks.paymentUrlAllowed(uri.toString())){status.setText("Chưa thể mở trang thanh toán. Vui lòng thử lại.");return;}
            attemptId=String.valueOf(data.getOrDefault("attemptId",""));
            // Only references persist, never signed URLs, card data or Firebase tokens.
            getSharedPreferences("vnpay_"+FirebaseAuth.getInstance().getUid(),MODE_PRIVATE).edit().putString("orderId",orderId).putString("attemptId",attemptId).apply();
            try{paymentOpened=true;new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri);}catch(android.content.ActivityNotFoundException e){paymentOpened=false;status.setText("Không tìm thấy trình duyệt. Cài trình duyệt rồi tiếp tục thanh toán.");}
        });
    }
    private void reconcile(){
        if(busy||!ready())return;loading(true);long token=generation;
        lastQueryAt=android.os.SystemClock.elapsedRealtime();
        repo.reconcileVnpay(orderId,attemptId).addOnCompleteListener(task->{if(isDestroyed()||generation!=token)return;loading(false);if(task.isSuccessful())acceptStatus(map(task.getResult().getData()),false);else failed(task.getException());});
    }
}
