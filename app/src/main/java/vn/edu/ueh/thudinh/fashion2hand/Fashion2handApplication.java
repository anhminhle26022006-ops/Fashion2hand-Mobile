package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

/** Configures local Firebase services before any Activity or repository uses them. */
public class Fashion2handApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Remove the plaintext value left by the old frontend-only password demo.
        getSharedPreferences("account_contact", MODE_PRIVATE).edit().remove("password").apply();
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080);
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
        FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199);
        FirebaseFunctions.getInstance().useEmulator("10.0.2.2", 5001);
    }
}
