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
    private TextView status; private Button open;
    private final Runnable poll=()->{if(visible&&!busy&&polls++<24)refresh();};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_vnpay_payment);
        status=findViewById(R.id.vnpayStatus);open=findViewById(R.id.vnpayOpen);
        orderId=state==null?readOrder(getIntent()):state.getString("orderId","");
        attemptId=state==null?"":state.getString("attemptId","");
        open.setOnClickListener(v->startPayment());
        findViewById(R.id.vnpayRefresh).setOnClickListener(v->{polls=0;refresh();});
        findViewById(R.id.vnpayReconcile).setOnClickListener(v->reconcile());
        findViewById(R.id.vnpayBack).setOnClickListener(v->finish());
    }
    private static String readOrder(Intent intent) {
        Uri uri=intent.getData();String id;
        if(uri!=null)return VnpayLinks.orderFromLink(uri.toString());
        id=intent.getStringExtra("orderId");
        return VnpayLinks.validOrderId(id)?id:"";
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);generation++;orderId=readOrder(intent);attemptId="";busy=false;polls=0;refresh();}
    @Override protected void onResume(){super.onResume();visible=true;polls=0;refresh();}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("orderId",orderId);state.putString("attemptId",attemptId);super.onSaveInstanceState(state);}
    private boolean ready(){
        if(orderId.isEmpty()){status.setText("Liên kết đơn hàng không hợp lệ.");open.setEnabled(false);return false;}
        if(FirebaseAuth.getInstance().getCurrentUser()==null){status.setText("Hãy đăng nhập Fashion2hand rồi mở lại đơn hàng để kiểm tra thanh toán.");open.setEnabled(false);return false;}
        return true;
    }
    private void loading(boolean value){busy=value;findViewById(R.id.vnpayProgress).setVisibility(value?View.VISIBLE:View.GONE);open.setEnabled(!value);findViewById(R.id.vnpayRefresh).setEnabled(!value);findViewById(R.id.vnpayReconcile).setEnabled(!value);}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value){return value instanceof Map?(Map<String,Object>)value:java.util.Collections.emptyMap();}
    private void failed(Exception error){status.setText(error==null?"Không thể lấy kết quả. Hãy làm mới.":error.getLocalizedMessage());}
    private void refresh(){
        if(busy||!ready())return;loading(true);long token=generation;
        repo.vnpayStatus(orderId).addOnCompleteListener(task->{
            if(isDestroyed()||generation!=token)return;loading(false);
            if(!task.isSuccessful()){failed(task.getException());return;}
            render(map(task.getResult().getData()));
        });
    }
    private void render(Map<String,Object> data){
        String payment=String.valueOf(data.get("paymentStatus")),order=String.valueOf(data.get("orderStatus"));
        attemptId=String.valueOf(data.getOrDefault("attemptId",""));
        boolean paid="PAID".equals(payment),pending="PENDING_PAYMENT".equals(order)&&!paid;
        status.setText(Boolean.TRUE.equals(data.get("needsReview"))?"Server đã ghi nhận kết quả; thanh toán cần đối soát. Chưa tự hoàn tiền.":paid?"Server đã xác minh thanh toán VNPAY Sandbox.":pending?"Đang xác nhận thanh toán. Bạn có thể tiếp tục phiên đang có hoặc làm mới.":"Trạng thái đơn: "+order+" · Thanh toán: "+payment);
        open.setEnabled(pending);
        handler.removeCallbacks(poll);if(pending&&visible&&polls<24)handler.postDelayed(poll,5000);
    }
    private void startPayment(){
        if(busy||!ready())return;loading(true);long token=generation;
        repo.createVnpayPayment(orderId).addOnCompleteListener(task->{
            if(isDestroyed()||generation!=token)return;loading(false);
            if(!task.isSuccessful()){failed(task.getException());return;}
            Map<String,Object> data=map(task.getResult().getData());Uri uri=Uri.parse(String.valueOf(data.get("paymentUrl")));
            if(!VnpayLinks.paymentUrlAllowed(uri.toString())){status.setText("Server trả URL thanh toán không hợp lệ.");return;}
            attemptId=String.valueOf(data.getOrDefault("attemptId",""));
            // Only references persist, never signed URLs, card data or Firebase tokens.
            getSharedPreferences("vnpay_"+FirebaseAuth.getInstance().getUid(),MODE_PRIVATE).edit().putString("orderId",orderId).putString("attemptId",attemptId).apply();
            try{new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri);}catch(android.content.ActivityNotFoundException e){status.setText("Không tìm thấy trình duyệt. Cài trình duyệt rồi tiếp tục phiên thanh toán.");}
        });
    }
    private void reconcile(){
        if(busy||!ready())return;loading(true);long token=generation;
        repo.reconcileVnpay(orderId,attemptId).addOnCompleteListener(task->{if(isDestroyed()||generation!=token)return;loading(false);if(task.isSuccessful())render(map(task.getResult().getData()));else failed(task.getException());});
    }
}
