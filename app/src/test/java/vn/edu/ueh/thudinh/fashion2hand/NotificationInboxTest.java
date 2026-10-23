package vn.edu.ueh.thudinh.fashion2hand;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NotificationInboxTest {
    @Test public void backendEventsAppearInTheirCorrectFilters() {
        for (String type : new String[]{"ORDER_UPDATED", "ORDER_AWAITING_PAYMENT", "SHIPMENT_UPDATED", "DISPUTE_OPENED"})
            assertEquals(type, "orders", NotificationInbox.category(type));
        for (String type : new String[]{"FREE_REQUEST", "FREE_REQUEST_ACCEPTED", "FREE_REQUEST_REJECTED", "FREE_REQUEST_CANCELLED"})
            assertEquals(type, "requests", NotificationInbox.category(type));
        assertEquals("messages", NotificationInbox.category("MESSAGE"));
        assertEquals("payments", NotificationInbox.category("PAYMENT_UPDATED"));
        assertEquals("payments", NotificationInbox.category("REFUND_UPDATED"));
    }

    @Test public void unknownEventsStayInTheAllInbox() {
        assertEquals("other", NotificationInbox.category(null));
        assertEquals("other", NotificationInbox.category("SYSTEM_ANNOUNCEMENT"));
    }
}
