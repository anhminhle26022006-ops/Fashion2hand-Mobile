package vn.edu.ueh.thudinh.fashion2hand;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CartSelectionTest {
    @Test public void checkoutContainsExactlyTickedAvailableItemsAndCurrentPrices(){
        CartSelection cart=new CartSelection();cart.update("a",59000,true);cart.update("b",25000,true);
        cart.choose("a",true);assertEquals(Collections.singletonList("a"),cart.selectedIds());assertEquals(59000,cart.total());
        cart.update("a",60000,true);assertEquals(60000,cart.total());
        cart.update("a",60000,false);assertTrue(cart.selectedIds().isEmpty());assertEquals(0,cart.total());
    }
    @Test public void selectAllIncludesRowsVerifiedLaterAndCanBeUntickedIndividually(){
        CartSelection cart=new CartSelection();cart.update("a",59000,true);cart.chooseAll(true);
        cart.update("b",25000,true);assertEquals(2,cart.selectedIds().size());assertTrue(cart.allChecked());
        cart.choose("a",false);assertFalse(cart.allChecked());assertEquals(Collections.singletonList("b"),cart.selectedIds());
        cart.update("c",10000,true);assertFalse(cart.checked("c"));
        cart.chooseAll(true);assertEquals(94000,cart.total());cart.chooseAll(false);assertEquals(0,cart.total());
    }
    @Test public void cachedAndRemovedItemsNeverReachCheckoutAndSavedSelectionRestoresAfterVerification(){
        CartSelection cart=new CartSelection();cart.restore(Arrays.asList("a","deleted"));
        assertTrue(cart.selectedIds().isEmpty());cart.retain(Arrays.asList("a","b"));cart.update("a",59000,true);
        assertEquals(Collections.singletonList("a"),cart.selectedIds());cart.pending("a");assertTrue(cart.selectedIds().isEmpty());
        cart.update("a",59000,true);assertEquals(59000,cart.total());
        cart.retain(Collections.singletonList("b"));assertTrue(cart.selectedIds().isEmpty());
    }
}
