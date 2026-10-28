package vn.edu.ueh.thudinh.fashion2hand;

import org.junit.Test;
import static org.junit.Assert.*;

public class VnpayUiTest {
    @Test public void providerThrottleHasFiveMinuteMessageInsteadOfSignatureFailure(){
        String issue=VnpayUi.queryFailure("RESOURCE_EXHAUSTED","QUERY_RATE_LIMITED");
        assertTrue(issue.contains("5 phút"));assertFalse(issue.contains("chữ ký"));assertFalse(issue.contains("60 giây"));
    }
    @Test public void pendingPollDoesNotEraseQueryErrorOrClaimSuccess(){
        String issue=VnpayUi.queryFailure("UNAVAILABLE");
        assertEquals(issue,VnpayUi.message(false,true,false,issue));
        assertEquals(issue,VnpayUi.message(false,false,false,issue));
        assertTrue(issue.contains("đừng thanh toán lại"));assertFalse(issue.contains("Thanh toán thành công"));
    }
    @Test public void verifiedPaidStateOverridesOldErrorAndReviewStillTakesPriority(){
        String issue=VnpayUi.queryFailure("DEADLINE_EXCEEDED");
        assertTrue(VnpayUi.message(true,false,false,issue).startsWith("Thanh toán thành công"));
        assertFalse(VnpayUi.message(true,false,true,issue).startsWith("Thanh toán thành công"));
    }
}
