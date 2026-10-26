package vn.edu.ueh.thudinh.fashion2hand;
import org.junit.Test;
import static org.junit.Assert.*;
public class AddressSearchTest {
    @Test public void findsHoChiMinhWithAccentsWithoutAccentsAndCommonAliases(){for(String query:new String[]{"Hồ Chí Minh","hồ chí minh","ho chi minh","HO CHI MINH","HCM","TP.HCM","tp hcm","TP Hồ Chí Minh","hochiminh","Sài Gòn"})assertTrue(query,AddressSearch.matches("Thành phố Hồ Chí Minh",query));}
    @Test public void supportsPartialNamesAndVietnameseD(){assertTrue(AddressSearch.matches("Tỉnh Đắk Lắk","dak lak"));assertTrue(AddressSearch.matches("Thành phố Hồ Chí Minh","chi minh"));assertTrue(AddressSearch.matches("Quận Bình Thạnh","binh thanh"));assertFalse(AddressSearch.matches("Thành phố Hà Nội","hcm"));}
    @Test public void allProvincesCanBeFoundByTheirOwnUnaccentedNames() throws Exception {
        VietnamAddressCatalog catalog=new VietnamAddressCatalog(new java.io.FileReader("src/main/assets/vietnam-addresses-legacy.tsv",java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(63,catalog.provinces.size());for(String name:catalog.provinces.keySet())assertTrue(name,AddressSearch.matches(name,AddressSearch.normalize(name)));
    }
}
