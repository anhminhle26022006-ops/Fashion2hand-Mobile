package vn.edu.ueh.thudinh.fashion2hand.ui.admin;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.Collections;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin.AdminViewModel;

/** Password validation is performed exclusively by Firebase Auth. */
public class AdminLoginActivity extends AppCompatActivity {
    private boolean pending;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_admin_login);
        android.view.View root=findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        EditText email=findViewById(R.id.edtAdminEmail), password=findViewById(R.id.edtAdminPassword);
        TextView status=findViewById(R.id.txtAdminDemoHint);
        status.setText("Đăng nhập bằng tài khoản Firebase được cấp quyền Admin. Không có tài khoản demo mặc định.");
        findViewById(R.id.btnAdminLogin).setOnClickListener(v->{
            if(pending)return;
            String address=email.getText().toString().trim(), secret=password.getText().toString();
            if(!Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("Email không hợp lệ");return;}
            if(secret.isEmpty()){password.setError("Nhập mật khẩu");return;}
            pending=true;v.setEnabled(false);status.setText("Đang xác thực…");
            FirebaseAuth.getInstance().signInWithEmailAndPassword(address,secret).continueWithTask(result->{
                if(!result.isSuccessful())throw result.getException();
                return FirebaseFunctions.getInstance().getHttpsCallable("adminSession").call(Collections.emptyMap());
            }).addOnCompleteListener(result->{
                if(isDestroyed())return;pending=false;v.setEnabled(true);password.setText("");
                if(!result.isSuccessful()){
                    boolean signedIn=FirebaseAuth.getInstance().getCurrentUser()!=null;
                    FirebaseAuth.getInstance().signOut();
                    if(signedIn && notAdmin(result.getException())){
                        startActivity(new Intent(this,AdminAccessDeniedActivity.class).putExtra(AdminAccessDeniedActivity.EXTRA_EMAIL,address));
                        status.setText("Tài khoản này không có quyền Admin.");
                        return;
                    }
                    status.setText("Đăng nhập thất bại hoặc tài khoản không có quyền Admin đang hoạt động. Kiểm tra tài khoản và kết nối Emulator.");return;
                }
                new ViewModelProvider(this).get(AdminViewModel.class).reset();
                startActivity(new Intent(this,AdminMainActivity.class));finish();
            });
        });
        findViewById(R.id.btnBackToUserApp).setOnClickListener(v->finish());
    }

    /** Đăng nhập Firebase đúng nhưng backend từ chối quyền Admin. */
    private static boolean notAdmin(Exception error) {
        Throwable current=error;
        while(current!=null){
            if(current instanceof FirebaseFunctionsException)
                return ((FirebaseFunctionsException)current).getCode()==FirebaseFunctionsException.Code.PERMISSION_DENIED;
            current=current.getCause();
        }
        return false;
    }
}
