package vn.edu.ueh.thudinh.fashion2hand.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchTextTest {

    @Test public void removesAccentsCaseAndExtraSpaces() {
        assertEquals("ao khoac jean", SearchText.key("  Áo   KHOÁC jean "));
        assertEquals("dam du tiec", SearchText.key("Đầm dự tiệc"));
        assertEquals("", SearchText.key(null));
    }

    @Test public void composedAndDecomposedUnicodeAreEqual() {
        String composed = "Váy hoa nhí";
        String decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD);
        assertEquals(SearchText.key(composed), SearchText.key(decomposed));
    }

    @Test public void everyWordMustMatchInAnyOrder() {
        String card = SearchText.keyOf("Áo khoác jean vintage", "Áo", "Size M · còn tốt");
        assertTrue(SearchText.matches(card, "ao"));
        assertTrue(SearchText.matches(card, "jean áo"));
        assertTrue(SearchText.matches(card, "VINTAGE"));
        assertTrue(SearchText.matches(card, "  "));
        assertFalse(SearchText.matches(card, "ao giay"));
        assertFalse(SearchText.matches(null, "ao"));
    }
}
