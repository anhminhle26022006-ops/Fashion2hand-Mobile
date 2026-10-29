package vn.edu.ueh.thudinh.fashion2hand;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShipmentUiTest {
    private Map<String,Object> event(String status,String description,String at) {
        Map<String,Object> value=new HashMap<>();value.put("status",status);value.put("description",description);value.put("createdAt",at);return value;
    }
    @Test public void existingTrackingCannotDisplayNotCreated(){
        assertEquals("Chưa tạo vận đơn",ShipmentUi.ready(false));
        assertEquals("Đã tạo vận đơn · chờ GHN lấy hàng",ShipmentUi.ready(true));
    }
    @Test public void repeatedReadDoesNotHideActualStateChangesOrWarehouseMoves(){
        Map<String,Object> first=event("READY","GHN: ready_to_pick","first");
        Map<String,Object> repeat=event("READY","GHN: ready_to_pick","second");
        Map<String,Object> picked=event("PICKING","GHN: picking","third");
        Map<String,Object> warehouse=event("PICKING","GHN: picking","fourth");warehouse.put("warehouse","New hub");
        assertEquals(Arrays.asList(first,picked,warehouse),ShipmentUi.compact(Arrays.asList(first,repeat,picked,warehouse),"status","description",false));
    }
    @Test public void orderActionsArePreservedWhileRepeatedGhnPollsCollapse(){
        Map<String,Object> selected=new HashMap<>();selected.put("actor","seller");selected.put("to","READY_TO_SHIP");selected.put("reason","Selected");
        Map<String,Object> carrier=new HashMap<>();carrier.put("actor","GHN");carrier.put("to","READY_TO_SHIP");carrier.put("reason","GHN ready_to_pick");
        assertEquals(2,ShipmentUi.compact(Arrays.asList(selected,carrier,new HashMap<>(carrier)),"to","reason",true).size());
    }
}
