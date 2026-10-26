package vn.edu.ueh.thudinh.fashion2hand;

import java.net.URI;

/** Navigation only: no callback result is interpreted as payment evidence. */
final class VnpayLinks {
    static boolean validOrderId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,128}");}
    static String orderFromLink(String value){
        try{
            URI uri=new URI(value);
            if(!"fashion2hand".equals(uri.getScheme())||!"payment".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)return "";
            String path=uri.getPath();if(path==null||!path.startsWith("/"))return "";
            String id=path.substring(1);return validOrderId(id)?id:"";
        }catch(Exception error){return "";}
    }
    static boolean paymentUrlAllowed(String value){
        try{URI uri=new URI(value);return "https".equals(uri.getScheme())&&"sandbox.vnpayment.vn".equals(uri.getHost())&&uri.getPort()==-1&&uri.getUserInfo()==null&&uri.getFragment()==null&&"/paymentv2/vpcpay.html".equals(uri.getPath());}
        catch(Exception error){return false;}
    }
}
