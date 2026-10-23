package vn.edu.ueh.thudinh.fashion2hand.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;

public interface ShippingService {
    Task<HttpsCallableResult> quote(String postId);
    Task<HttpsCallableResult> create(String orderId);
    Task<HttpsCallableResult> simulateEvent(String orderId, String status, String description);
}
