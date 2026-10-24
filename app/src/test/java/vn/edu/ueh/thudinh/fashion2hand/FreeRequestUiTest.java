package vn.edu.ueh.thudinh.fashion2hand;

import org.junit.Test;
import static org.junit.Assert.*;

public class FreeRequestUiTest {
    @Test public void pendingAndAcceptedRequestsOpenStatusInsteadOfSubmittingAgain(){
        assertTrue(FreeRequestUi.alreadySent("PENDING"));assertEquals("Đã gửi yêu cầu",FreeRequestUi.action("PENDING"));
        assertTrue(FreeRequestUi.alreadySent("ACCEPTED"));
        for(String status:new String[]{"CANCELLED","REJECTED","UNAVAILABLE",""})assertFalse(FreeRequestUi.alreadySent(status));
    }
    @Test public void fullDocumentIdIsOnlyShortenedForDisplay(){
        String id="ea99aa38d4ea49bd5f3cb050d0b32ff5387af082527a1cdc7e4d3470c5be33d3";
        assertEquals("YC-EA99AA38D4",FreeRequestUi.code(id));assertEquals(13,FreeRequestUi.code(id).length());
    }
}
