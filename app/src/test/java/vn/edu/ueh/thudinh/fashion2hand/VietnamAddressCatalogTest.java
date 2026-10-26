package vn.edu.ueh.thudinh.fashion2hand;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VietnamAddressCatalogTest {
    @Test public void legacyDirectoryHas63ProvincesAndValidDistrictWardHierarchy() throws Exception {
        VietnamAddressCatalog directory=new VietnamAddressCatalog(new InputStreamReader(new FileInputStream("src/main/assets/vietnam-addresses-legacy.tsv"),"UTF-8"));
        assertEquals(63,directory.provinces.size());Set<String> districts=new HashSet<>(),wards=new HashSet<>();int wardless=0;
        for(VietnamAddressCatalog.Province p:directory.provinces.values())for(VietnamAddressCatalog.District d:p.districts.values()){
            assertTrue(districts.add(d.code));if(d.wards.isEmpty())wardless++;
            for(String code:d.wards.values())assertTrue(wards.add(code));
        }
        assertEquals(696,districts.size());assertEquals(10051,wards.size());assertEquals(5,wardless);
        assertTrue(directory.provinces.containsKey("Tỉnh Bình Dương"));assertTrue(directory.provinces.containsKey("Tỉnh Bà Rịa - Vũng Tàu"));
        assertTrue(directory.provinces.get("Thành phố Hồ Chí Minh").districts.containsKey("Quận 8"));
        assertTrue(directory.provinces.get("Thành phố Hồ Chí Minh").districts.get("Quận 8").wards.containsKey("Phường 5"));
        assertFalse(directory.provinces.get("Thành phố Hà Nội").districts.containsKey("Quận 8"));
    }
    @Test public void bundledDirectoryHasUniqueCodesAndCurrentCityWards() throws Exception {
        VietnamAddressCatalog directory=new VietnamAddressCatalog(new InputStreamReader(new FileInputStream("src/main/assets/vietnam-addresses.tsv"),"UTF-8"));
        assertEquals(34,directory.provinces.size());Set<String> codes=new HashSet<>();int count=0;
        for(VietnamAddressCatalog.Province p:directory.provinces.values())for(String code:p.wards.values()){assertTrue(codes.add(code));count++;}
        assertEquals(3321,count);
        assertTrue(directory.provinces.get("Thành phố Hồ Chí Minh").wards.containsKey("Phường Sài Gòn"));
        assertFalse(directory.provinces.get("Thành phố Hà Nội").wards.containsKey("Phường Sài Gòn"));
    }
}
