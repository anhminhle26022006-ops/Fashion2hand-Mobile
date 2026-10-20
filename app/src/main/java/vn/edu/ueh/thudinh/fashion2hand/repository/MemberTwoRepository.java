package vn.edu.ueh.thudinh.fashion2hand.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Map;

/** Client adapter for the trusted Member 2 Cloud Functions and read-only Firestore streams. */
public final class MemberTwoRepository {
    private static MemberTwoRepository instance;
    private final FirebaseFunctions functions = FirebaseFunctions.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public static synchronized MemberTwoRepository getInstance() {
        if (instance == null) instance = new MemberTwoRepository();
        return instance;
    }

    private MemberTwoRepository() { }

    private Task<HttpsCallableResult> call(String name, Map<String, Object> payload) {
        return functions.getHttpsCallable(name).call(payload);
    }

    private Map<String, Object> payload(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    public Task<HttpsCallableResult> createChat(String otherUserId, String postId) {
        return call("createChat", payload("otherUserId", otherUserId, "postId", postId == null ? "" : postId));
    }

    public Task<HttpsCallableResult> sendMessage(String chatId, String text) {
        return call("sendMessage", payload("chatId", chatId, "text", text));
    }

    public Task<HttpsCallableResult> markChatRead(String chatId) {
        return call("markChatRead", payload("chatId", chatId));
    }

    public Task<HttpsCallableResult> createFreeRequest(String postId, String note) {
        return call("createFreeRequest", payload("postId", postId, "note", note == null ? "" : note));
    }

    public Task<HttpsCallableResult> createFreeRequest(String postId, String note, String addressId) {
        return call("createFreeRequest", payload("postId", postId, "note", note == null ? "" : note,
                "addressId", addressId == null ? "" : addressId));
    }

    public Task<HttpsCallableResult> respondFreeRequest(String requestId, boolean accept) {
        return call("respondFreeRequest", payload("requestId", requestId, "accept", accept));
    }

    public Task<HttpsCallableResult> cancelFreeRequest(String requestId) {
        return call("cancelFreeRequest", payload("requestId", requestId));
    }

    public Task<HttpsCallableResult> createPaidOrder(String postId, String idempotencyKey) {
        return call("createPaidOrder", payload("postId", postId, "idempotencyKey", idempotencyKey));
    }

    public Task<HttpsCallableResult> createPaidOrder(String postId, String idempotencyKey, String addressId) {
        return call("createPaidOrder", payload("postId", postId, "idempotencyKey", idempotencyKey,
                "addressId", addressId == null ? "" : addressId));
    }

    /** Test/coursework simulation only. The server rejects this unless explicitly enabled. */
    public Task<HttpsCallableResult> simulatePaymentResult(String orderId, boolean paid) {
        return call("simulatePaymentResult", payload("orderId", orderId, "status", paid ? "PAID" : "FAILED"));
    }

    public Task<HttpsCallableResult> cancelOrder(String orderId) {
        return call("cancelOrder", payload("orderId", orderId));
    }

    public Task<HttpsCallableResult> rejectOrder(String orderId, String reason) {
        return call("rejectOrder", payload("orderId", orderId, "reason", reason == null ? "" : reason));
    }

    public Task<HttpsCallableResult> updateOrderStatus(String orderId, String status) {
        return call("updateOrderStatus", payload("orderId", orderId, "status", status));
    }

    public Task<HttpsCallableResult> createShipment(String orderId, String carrier, String trackingCode) {
        return call("createShipment", payload("orderId", orderId, "carrier", carrier, "trackingCode", trackingCode));
    }

    /** Test/coursework simulation only. The server rejects this unless explicitly enabled. */
    public Task<HttpsCallableResult> simulateShipmentUpdate(String orderId, String status, String description) {
        return call("simulateShipmentUpdate", payload("orderId", orderId, "status", status, "description", description == null ? "" : description));
    }

    public Task<HttpsCallableResult> markNotificationRead(String notificationId) {
        return call("markNotificationRead", payload("notificationId", notificationId));
    }

    public Task<Void> addCartItem(String uid, String postId, Map<String, Object> item) {
        Map<String, Object> data = new HashMap<>(item);
        data.put("postId", postId);
        data.put("addedAt", FieldValue.serverTimestamp());
        return firestore.collection("users").document(uid).collection("cartItems").document(postId).set(data);
    }

    public Task<Void> removeCartItem(String uid, String postId) {
        return firestore.collection("users").document(uid).collection("cartItems").document(postId).delete();
    }

    public Task<Void> setFavorite(String uid, String postId, boolean favorite) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("users").document(uid)
                .collection("favorites").document(postId);
        return firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot existing = transaction.get(ref);
            if (favorite && !existing.exists()) transaction.set(ref,
                    payload("postId", postId, "createdAt", FieldValue.serverTimestamp()));
            else if (!favorite && existing.exists()) transaction.delete(ref);
            return null;
        });
    }

    public ListenerRegistration listenActivePosts(EventListener<QuerySnapshot> listener) {
        return firestore.collection("posts").whereEqualTo("status", "ACTIVE")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50).addSnapshotListener(listener);
    }

    public ListenerRegistration listenCart(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("users").document(uid).collection("cartItems")
                .orderBy("addedAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenFavorites(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("users").document(uid).collection("favorites")
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenFreeRequest(String requestId, EventListener<DocumentSnapshot> listener) {
        return firestore.collection("freeRequests").document(requestId).addSnapshotListener(listener);
    }

    public ListenerRegistration listenChats(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("chats").whereArrayContains("participantIds", uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenMessages(String chatId, EventListener<QuerySnapshot> listener) {
        return firestore.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenFreeRequestsAsRequester(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("freeRequests").whereEqualTo("requesterId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenFreeRequestsAsOwner(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("freeRequests").whereEqualTo("ownerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenOrdersAsBuyer(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("orders").whereEqualTo("buyerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenOrdersAsSeller(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("orders").whereEqualTo("sellerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenOrder(String orderId, EventListener<DocumentSnapshot> listener) {
        return firestore.collection("orders").document(orderId).addSnapshotListener(listener);
    }

    public ListenerRegistration listenOrderStatusEvents(String orderId, EventListener<QuerySnapshot> listener) {
        return firestore.collection("orders").document(orderId).collection("statusEvents")
                .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenPayment(String paymentId, EventListener<DocumentSnapshot> listener) {
        return firestore.collection("payments").document(paymentId).addSnapshotListener(listener);
    }

    public ListenerRegistration listenMoneyTransactions(String orderId, EventListener<QuerySnapshot> listener) {
        return firestore.collection("moneyTransactions").whereEqualTo("orderId", orderId)
                .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenNotifications(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("users").document(uid).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(listener);
    }

    public ListenerRegistration listenShipment(String orderId, EventListener<DocumentSnapshot> listener) {
        return firestore.collection("shipments").document(orderId).addSnapshotListener(listener);
    }
}
