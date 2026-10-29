package vn.edu.ueh.thudinh.fashion2hand;

import java.io.*;
import java.util.*;

/** Offline two-level administrative directory. See ADDRESS_DATA.md. */
final class VietnamAddressCatalog {
    static final class Province {
        final String code, name;
        final Map<String,String> wards = new LinkedHashMap<>();
        final Map<String,District> districts = new LinkedHashMap<>();
        Province(String code, String name) { this.code=code; this.name=name; }
    }
    static final class District {
        final String code,name;
        final Map<String,String> wards=new LinkedHashMap<>();
        final Set<String> ambiguousNames=new HashSet<>();
        District(String code,String name){this.code=code;this.name=name;}
        void addWard(String name,String code) throws IOException {
            String previous=wards.get(name);
            if(previous!=null){
                if(previous.equals(code))throw new IOException("Trùng mã phường/xã");
                wards.remove(name);wards.put(name+" · "+previous,previous);ambiguousNames.add(name);
            }
            if(wards.put(ambiguousNames.contains(name)?name+" · "+code:name,code)!=null)throw new IOException("Trùng mã phường/xã");
        }
    }
    final Map<String,Province> provinces = new LinkedHashMap<>();
    VietnamAddressCatalog(Reader source) throws IOException {
        try (BufferedReader reader=new BufferedReader(source)) {
            String line;
            while ((line=reader.readLine())!=null) {
                String[] parts=line.split("\t",-1);
                if(parts.length!=4&&parts.length!=6)throw new IOException("Danh mục địa chỉ không hợp lệ");
                Province p=provinces.get(parts[1]);
                if(p==null){p=new Province(parts[0],parts[1]);provinces.put(p.name,p);}
                if(parts.length==4){if(p.wards.put(parts[3],parts[2])!=null)throw new IOException("Trùng phường/xã");}
                else {
                    District district=p.districts.get(parts[3]);
                    if(district==null){district=new District(parts[2],parts[3]);p.districts.put(district.name,district);}
                    if(!parts[4].isEmpty())district.addWard(parts[5],parts[4]);
                }
            }
        }
    }
}
