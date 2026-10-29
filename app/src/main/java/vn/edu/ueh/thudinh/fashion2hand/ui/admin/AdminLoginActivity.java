package vn.edu.ueh.thudinh.fashion2hand.ui.admin;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.Collections;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin.AdminViewModel;

/** Password validation is performed exclusively by Firebase Auth. */
public class AdminLoginActivity extends AppCompatActivity {
    private boolean pending;
    private boolean checkingPermissions;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_admin_login);
        android.view.View root=findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        EditText email=findViewById(R.id.edtAdminEmail), password=findViewById(R.id.edtAdminPassword);
        CheckBox showPassword=findViewById(R.id.checkShowAdminPassword);
        showPassword.setOnCheckedChangeListener((button,checked)->{
            int selection=password.getSelectionStart();
            password.setTransformationMethod(checked?null:PasswordTransformationMethod.getInstance());
            password.setSelection(Math.max(0,Math.min(selection,password.length())));
        });
        TextView status=findViewById(R.id.txtAdminDemoHint);
        status.setText("Đăng nhập bằng tài khoản quản trị đã được cấp quyền.");
        findViewById(R.id.btnAdminLogin).setOnClickListener(v->{
            if(pending)return;
            String address=email.getText().toString().trim(), secret=password.getText().toString();
            if(!Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("Email không hợp lệ");return;}
            if(secret.isEmpty()){password.setError("Nhập mật khẩu");return;}
            pending=true;checkingPermissions=false;v.setEnabled(false);status.setText("Đang xác thực email và mật khẩu…");
            FirebaseAuth.getInstance().signInWithEmailAndPassword(address,secret).continueWithTask(result->{
                if(!result.isSuccessful())throw result.getException();
                checkingPermissions=true;
                status.setText("Đã xác thực · Đang kiểm tra quyền Admin…");
                return FirebaseFunctions.getInstance().getHttpsCallable("adminSession").call(Collections.emptyMap());
            }).addOnCompleteListener(result->{
                if(isDestroyed())return;pending=false;v.setEnabled(true);password.setText("");
                if(!result.isSuccessful()){
                    Exception error=result.getException();
                    String code=error instanceof FirebaseAuthException?((FirebaseAuthException)error).getErrorCode():error instanceof FirebaseFunctionsException?((FirebaseFunctionsException)error).getCode().name():error==null?"UNKNOWN":error.getClass().getSimpleName();
                    Log.w("AdminLogin","Login failed at "+(checkingPermissions?"adminSession":"authentication")+": "+code);
                    boolean signedIn=FirebaseAuth.getInstance().getCurrentUser()!=null;
                    FirebaseAuth.getInstance().signOut();
                    if(signedIn && notAdmin(result.getException())){
                        startActivity(new Intent(this,AdminAccessDeniedActivity.class).putExtra(AdminAccessDeniedActivity.EXTRA_EMAIL,address));
                        status.setText("Tài khoản này không có quyền Admin.");
                        return;
                    }
                    status.setText(loginError(error,checkingPermissions));return;
                }
                new ViewModelProvider(this).get(AdminViewModel.class).reset();
                password.setText("");showPassword.setChecked(false);
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

    static String loginError(Exception error,boolean checkingPermissions) {
        String prefix="Đăng nhập thất bại: ";
        if(error instanceof FirebaseNetworkException)return prefix+"không kết nối được dịch vụ xác thực. Giữ backend Emulator chạy rồi thử lại.";
        if(error instanceof FirebaseAuthException){
            String code=((FirebaseAuthException)error).getErrorCode();
            if("ERROR_USER_DISABLED".equals(code))return prefix+"tài khoản đã bị vô hiệu hóa.";
            if("ERROR_TOO_MANY_REQUESTS".equals(code))return prefix+"đã thử quá nhiều lần. Vui lòng chờ rồi thử lại.";
            return prefix+"email hoặc mật khẩu không đúng. Kiểm tra chữ hoa/thường, khoảng trắng và ký tự cuối mật khẩu.";
        }
        if(error instanceof FirebaseFunctionsException){
            FirebaseFunctionsException.Code code=((FirebaseFunctionsException)error).getCode();
            if(code==FirebaseFunctionsException.Code.PERMISSION_DENIED)return prefix+"email và mật khẩu đúng nhưng tài khoản chưa có quyền Admin đang hoạt động.";
            if(code==FirebaseFunctionsException.Code.NOT_FOUND)return prefix+"chưa tìm thấy dịch vụ kiểm tra quyền Admin. Kiểm tra Functions Emulator.";
            if(code==FirebaseFunctionsException.Code.UNAUTHENTICATED)return prefix+"phiên xác thực chưa hợp lệ. Vui lòng đăng nhập lại.";
            return prefix+"chưa kết nối được dịch vụ kiểm tra quyền Admin. Kiểm tra mạng rồi thử lại.";
        }
        return prefix+(checkingPermissions?"chưa kiểm tra được quyền Admin. Vui lòng thử lại.":"chưa xác thực được tài khoản. Kiểm tra kết nối rồi thử lại.");
    }
}
