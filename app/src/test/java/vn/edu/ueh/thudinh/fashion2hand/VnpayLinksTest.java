package vn.edu.ueh.thudinh.fashion2hand;
import org.junit.Test;
import static org.junit.Assert.*;
public class VnpayLinksTest {
    @Test public void deepLinkCarriesOnlyReferenceAndIgnoresForgedSuccess(){assertEquals("ORDER123",VnpayLinks.orderFromLink("fashion2hand://payment/ORDER123?status=PAID&vnp_ResponseCode=00"));}
    @Test public void invalidReferencesAndAuthoritiesRejected(){for(String link:new String[]{"fashion2hand://payment/../orders","fashion2hand://payment/A%2FB","fashion2hand://evil/A","fashion2hand://user@payment/A","https://payment/A","fashion2hand://payment:123/A"})assertEquals("",VnpayLinks.orderFromLink(link));}
    @Test public void onlyExactSandboxPaymentEndpointAllowed(){assertTrue(VnpayLinks.paymentUrlAllowed("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=X"));for(String link:new String[]{"https://vnpayment.vn/paymentv2/vpcpay.html","https://sandbox.vnpayment.vn.evil.test/paymentv2/vpcpay.html","http://sandbox.vnpayment.vn/paymentv2/vpcpay.html","https://user@sandbox.vnpayment.vn/paymentv2/vpcpay.html","https://sandbox.vnpayment.vn:8443/paymentv2/vpcpay.html"})assertFalse(VnpayLinks.paymentUrlAllowed(link));}
}
