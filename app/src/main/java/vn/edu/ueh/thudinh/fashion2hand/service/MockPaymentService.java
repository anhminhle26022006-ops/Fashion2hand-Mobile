package vn.edu.ueh.thudinh.fashion2hand.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;

/** Coursework adapter. The callable itself enforces Emulator mode and the feature flag. */
public final class MockPaymentService implements PaymentService {
    private final MemberTwoRepository repository;
    public MockPaymentService(MemberTwoRepository repository) { this.repository = repository; }
    public Task<HttpsCallableResult> pay(String orderId) { return repository.simulatePaymentResult(orderId, true); }
    public Task<HttpsCallableResult> simulateFailure(String orderId) { return repository.simulatePaymentResult(orderId, false); }
}
