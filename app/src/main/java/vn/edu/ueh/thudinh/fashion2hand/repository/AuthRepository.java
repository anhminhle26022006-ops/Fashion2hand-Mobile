package vn.edu.ueh.thudinh.fashion2hand.repository;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.EmailAuthProvider;
import android.app.Activity;
import java.util.concurrent.TimeUnit;
import com.google.android.gms.tasks.Task;

public class AuthRepository {
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String errorMessage);
    }

    // Lấy thông tin user hiện tại đang đăng nhập
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public Task<com.google.firebase.auth.AuthResult> signInWithEmail(String email, String password) {
        return firebaseAuth.signInWithEmailAndPassword(email, password);
    }

    public Task<com.google.firebase.auth.AuthResult> createAccountWithEmail(String email, String password) {
        return firebaseAuth.createUserWithEmailAndPassword(email, password);
    }

    public Task<Void> sendPasswordReset(String email) {
        return firebaseAuth.sendPasswordResetEmail(email);
    }

    public void sendPhoneCode(Activity activity, String phone,
                              PhoneAuthProvider.ForceResendingToken resendToken,
                              PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks) {
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity).setCallbacks(callbacks);
        if (resendToken != null) options.setForceResendingToken(resendToken);
        PhoneAuthProvider.verifyPhoneNumber(options.build());
    }

    public Task<Void> verifyPhone(PhoneAuthCredential credential) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return com.google.android.gms.tasks.Tasks.forException(
                new IllegalStateException("Vui lòng đăng nhập lại."));
        if (user.getPhoneNumber() != null) return user.updatePhoneNumber(credential);
        // Link to the signed-in account; signing in with SMS would switch users.
        return user.linkWithCredential(credential).continueWithTask(result -> {
            if (!result.isSuccessful()) throw result.getException();
            return com.google.android.gms.tasks.Tasks.forResult(null);
        });
    }

    public Task<Void> requestEmailChange(String email) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return com.google.android.gms.tasks.Tasks.forException(
                new IllegalStateException("Vui lòng đăng nhập lại."));
        return user.verifyBeforeUpdateEmail(email);
    }

    public Task<Void> reauthenticate(String password) {
        FirebaseUser user = getCurrentUser();
        if (user == null || user.getEmail() == null) return com.google.android.gms.tasks.Tasks.forException(
                new IllegalStateException("Vui lòng đăng nhập lại."));
        return user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password));
    }

    // Lấy UID của user hiện tại
    public String getCurrentUid() {
        FirebaseUser user = getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    // Đăng nhập bằng Google Credential
    public void signInWithGoogleCredential(AuthCredential credential, AuthCallback callback) {
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> callback.onSuccess(authResult.getUser()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Đăng xuất
    public void signOut() {
        firebaseAuth.signOut();
    }
}
