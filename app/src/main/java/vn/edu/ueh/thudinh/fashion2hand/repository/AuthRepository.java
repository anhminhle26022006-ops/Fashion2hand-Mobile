package vn.edu.ueh.thudinh.fashion2hand.repository;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
