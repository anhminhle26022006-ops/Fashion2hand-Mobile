package vn.edu.ueh.thudinh.fashion2hand;

import java.io.*;
import java.util.*;

/** Offline two-level administrative directory. See ADDRESS_DATA.md. */
final class VietnamAddressCatalog {
    static final class Province {
        final String code, name;
        final Map<String,String> wards = new LinkedHashMap<>();
        Province(String code, String name) { this.code=code; this.name=name; }
    }
    final Map<String,Province> provinces = new LinkedHashMap<>();
    VietnamAddressCatalog(Reader source) throws IOException {
        try (BufferedReader reader=new BufferedReader(source)) {
            String line;
            while ((line=reader.readLine())!=null) {
                String[] parts=line.split("\t",-1);
                if(parts.length!=4)throw new IOException("Danh mục địa chỉ không hợp lệ");
                Province p=provinces.get(parts[1]);
                if(p==null){p=new Province(parts[0],parts[1]);provinces.put(p.name,p);}
                if(p.wards.put(parts[3],parts[2])!=null)throw new IOException("Trùng phường/xã");
            }
        }
    }
}
