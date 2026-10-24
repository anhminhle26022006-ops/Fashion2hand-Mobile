package vn.edu.ueh.thudinh.fashion2hand;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VietnamAddressCatalogTest {
    @Test public void bundledDirectoryHasUniqueCodesAndCurrentCityWards() throws Exception {
        VietnamAddressCatalog directory=new VietnamAddressCatalog(new InputStreamReader(new FileInputStream("src/main/assets/vietnam-addresses.tsv"),"UTF-8"));
        assertEquals(34,directory.provinces.size());Set<String> codes=new HashSet<>();int count=0;
        for(VietnamAddressCatalog.Province p:directory.provinces.values())for(String code:p.wards.values()){assertTrue(codes.add(code));count++;}
        assertEquals(3321,count);
        assertTrue(directory.provinces.get("Thành phố Hồ Chí Minh").wards.containsKey("Phường Sài Gòn"));
        assertFalse(directory.provinces.get("Thành phố Hà Nội").wards.containsKey("Phường Sài Gòn"));
    }
}
