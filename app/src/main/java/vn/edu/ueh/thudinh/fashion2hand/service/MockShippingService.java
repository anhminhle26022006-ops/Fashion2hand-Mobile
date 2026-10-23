package vn.edu.ueh.thudinh.fashion2hand.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;

/** Fees and tracking records come from the trusted backend, never invented client coordinates. */
public final class MockShippingService implements ShippingService {
    private final MemberTwoRepository repository;
    public MockShippingService(MemberTwoRepository repository) { this.repository = repository; }
    public Task<HttpsCallableResult> quote(String postId) { return repository.quote(postId); }
    public Task<HttpsCallableResult> create(String orderId) { return repository.createShipment(orderId, "MOCK_CARRIER", ""); }
    public Task<HttpsCallableResult> simulateEvent(String orderId, String status, String description) {
        return repository.simulateShipmentUpdate(orderId, status, description);
    }
}
