package vn.edu.ueh.thudinh.fashion2hand;

import java.util.*;

/** Shipment READY means a carrier accepted it; order READY_TO_SHIP is a separate state. */
final class ShipmentUi {
    static String ready(boolean linked) {return linked?"Đã tạo vận đơn · chờ GHN lấy hàng":"Chưa tạo vận đơn";}
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> compact(List<?> source,String statusKey,String detailKey,boolean onlyGhn) {
        List<Map<String,Object>> result=new ArrayList<>();List<Object> previous=null;
        for(Object raw:source) {
            if(!(raw instanceof Map))continue;
            Map<String,Object> event=(Map<String,Object>)raw;
            Object actor=event.containsKey("actorId")?event.get("actorId"):event.get("actor");
            boolean eligible=!onlyGhn||"GHN".equals(actor);
            List<Object> key=eligible?Arrays.asList(event.get(statusKey),event.get(detailKey),event.get("warehouse")):null;
            if(key==null||!key.equals(previous))result.add(event);
            previous=key;
        }
        return result;
    }
}
