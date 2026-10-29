package vn.edu.ueh.thudinh.fashion2hand;

import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

/** Fail before the test Application starts if a cloud APK was selected accidentally. */
public final class EmulatorTestRunner extends AndroidJUnitRunner {
    @Override public void onCreate(Bundle arguments) {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATORS)
            throw new IllegalStateException("Integration tests require -PuseFirebaseEmulators=true");
        super.onCreate(arguments);
    }
}
