package vn.edu.ueh.thudinh.fashion2hand;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarketplaceLinksTest {
    @Test public void vietnameseAndInternationalPhoneNumbers() {
        assertEquals("+84901234567", MarketplaceLinks.normalizePhone("0901 234 567"));
        assertEquals("+84901234567", MarketplaceLinks.normalizePhone("+84 (901) 234-567"));
        assertEquals("+14155552671", MarketplaceLinks.normalizePhone("+1 415 555 2671"));
        assertEquals("", MarketplaceLinks.normalizePhone("090abc1234567"));
        assertEquals("", MarketplaceLinks.normalizePhone("123"));
        assertEquals("", MarketplaceLinks.normalizePhone("+0123456789"));
    }

    @Test public void productLinksRejectTraversalAndQueryInjection() {
        assertEquals("fashion2hand://post/abc_123-X", MarketplaceLinks.productLink("abc_123-X"));
        assertFalse(MarketplaceLinks.validPostId("../users/private"));
        assertFalse(MarketplaceLinks.validPostId("id?token=secret"));
        assertFalse(MarketplaceLinks.validPostId(""));
        assertFalse(MarketplaceLinks.validPostId(null));
    }
}
