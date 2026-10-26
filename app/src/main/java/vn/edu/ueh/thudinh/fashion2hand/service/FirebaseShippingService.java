package vn.edu.ueh.thudinh.fashion2hand.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;

/** Provider selection and all credentials stay on the trusted Functions backend. */
public final class FirebaseShippingService implements ShippingService {
    private final MemberTwoRepository repository;
    public FirebaseShippingService(MemberTwoRepository repository) { this.repository = repository; }
    public Task<HttpsCallableResult> quote(String postId) { return repository.quote(postId); }
    public Task<HttpsCallableResult> create(String orderId) { return repository.createShipment(orderId, "", ""); }
    public Task<HttpsCallableResult> simulateEvent(String orderId, String status, String description) {
        return repository.simulateShipmentUpdate(orderId, status, description);
    }
}
