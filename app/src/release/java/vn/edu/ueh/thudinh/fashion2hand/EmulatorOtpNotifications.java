package vn.edu.ueh.thudinh.fashion2hand;

import androidx.activity.ComponentActivity;

/** Release has no emulator-code reader or simulated OTP notification. */
final class EmulatorOtpNotifications implements AutoCloseable {
    EmulatorOtpNotifications(ComponentActivity activity) { }
    void show(String phone, String verificationId) { }
    void clear() { }
    @Override public void close() { }
}
