package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

/** Configures Firebase services before any Activity or repository uses them. */
public class Fashion2handApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Remove the plaintext value left by the old frontend-only password demo.
        getSharedPreferences("account_contact", MODE_PRIVATE).edit().remove("password").apply();
        if (BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            String host = BuildConfig.FIREBASE_EMULATOR_HOST;
            FirebaseFirestore.getInstance().useEmulator(host, 8080);
            FirebaseAuth.getInstance().useEmulator(host, 9099);
            FirebaseStorage.getInstance().useEmulator(host, 9199);
            FirebaseFunctions.getInstance().useEmulator(host, 5001);
        }
    }
}
