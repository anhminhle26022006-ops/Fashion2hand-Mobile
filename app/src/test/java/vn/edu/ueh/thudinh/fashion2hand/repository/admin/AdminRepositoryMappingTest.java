package vn.edu.ueh.thudinh.fashion2hand.repository.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.edu.ueh.thudinh.fashion2hand.model.Order;
import vn.edu.ueh.thudinh.fashion2hand.model.Post;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;

/** Dữ liệu backend (adminList) được đổi đúng sang model mà giao diện Admin đọc. */
public class AdminRepositoryMappingTest {

    private static Map<String, Object> doc(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    @SafeVarargs
    private static List<Map<String, Object>> list(Map<String, Object>... docs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : docs) result.add(d);
        return result;
    }

    private AdminRepository load() {
        String now = "2026-10-24T01:00:00.000Z";
        Map<String, List<Map<String, Object>>> records = new HashMap<>();
        records.put("users", list(
                doc("id", "seller", "name", "Người bán", "role", "USER", "accountStatus", "ACTIVE", "createdAt", now),
                doc("id", "buyer", "name", "Người mua", "role", "USER", "accountStatus", "ACTIVE", "createdAt", now)));
        records.put("posts", list(doc("id", "p1", "ownerId", "seller", "title", "Giày", "type", "SALE", "price", 250000L,
                "category", "Giày", "status", "RESERVED", "condition", "FAIR", "createdAt", now)));
        records.put("orders", list(doc("id", "o1", "orderType", "PAID", "buyerId", "buyer", "sellerId", "seller",
                "postId", "p1", "price", 250000L, "shippingFee", 15000L, "orderStatus", "DELIVERED",
                "disputeStatus", "OPEN", "paymentStatus", "PAID", "createdAt", now)));
        records.put("disputes", list(doc("id", "o1", "orderId", "o1", "buyerId", "buyer", "sellerId", "seller",
                "reporterId", "buyer", "reason", "DAMAGED", "description", "Bong đế", "status", "OPEN", "createdAt", now)));
        records.put("reports", list(
                doc("id", "support1", "type", "SUPPORT", "title", "Không đăng nhập được", "reporterId", "buyer",
                        "targetType", "USER", "targetId", "buyer", "status", "PENDING", "createdAt", now),
                doc("id", "r1", "reporterId", "buyer", "targetType", "USER", "targetId", "seller",
                        "reason", "Spam", "status", "PENDING", "createdAt", now)));
        records.put("verificationRequests", list());
        records.put("moneyTransactions", list(
                doc("id", "received_o1", "orderId", "o1", "type", "PAYMENT_RECEIVED", "amount", 265000L,
                        "status", "HOLDING", "createdAt", now),
                doc("id", "payout_x", "orderId", "x", "type", "SELLER_PAYOUT", "amount", 1L, "createdAt", now)));
        records.put("categories", list(doc("id", "c1", "name", "Giày", "enabled", false)));

        AdminRepository repository = AdminRepository.getInstance();
        repository.apply(records, doc("reportThreshold", 4L, "revision", 2L));
        return repository;
    }

    @Test
    public void openDisputeMarksOrderAndHeldMoneyAsDisputed() {
        AdminRepository repository = load();
        assertEquals(Order.STATUS_DISPUTED, repository.getOrder("o1").getOrderStatus());
        assertEquals(1, repository.getTransactions().size());
        assertEquals(Transaction.DISPUTED, repository.getTransactionByOrder("o1").getStatus());
        assertEquals("Hàng bị hư hỏng", repository.getDispute("o1").getReason());
    }

    @Test
    public void supportTicketIsNotTreatedAsReportAgainstItsSender() {
        AdminRepository repository = load();
        assertTrue(repository.isSupportTicket("support1"));
        assertFalse(repository.isSupportTicket("r1"));
        assertEquals(0, repository.getUser("buyer").getReportedCount());
        assertEquals(1, repository.getUser("seller").getReportedCount());
    }

    @Test
    public void postsCategoriesAndSettingsUseBackendValues() {
        AdminRepository repository = load();
        assertEquals(Post.TYPE_FOR_SALE, repository.getPost("p1").getListingType());
        assertEquals("Khá", repository.getPost("p1").getCondition());
        assertEquals(1, repository.getCategories().get(0).getPostCount());
        assertFalse(repository.getCategories().get(0).isEnabled());
        assertEquals(4, repository.getReportThreshold());
    }
}
