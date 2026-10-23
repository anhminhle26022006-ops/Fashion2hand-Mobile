package vn.edu.ueh.thudinh.fashion2hand.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;

/** Implementations delegate verification to the server; Android holds no merchant secret. */
public interface PaymentService {
    Task<HttpsCallableResult> pay(String orderId);
    Task<HttpsCallableResult> simulateFailure(String orderId);
}
