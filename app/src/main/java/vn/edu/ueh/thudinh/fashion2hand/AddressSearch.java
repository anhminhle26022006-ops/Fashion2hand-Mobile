package vn.edu.ueh.thudinh.fashion2hand;

import java.text.Normalizer;
import java.util.Locale;

final class AddressSearch {
    static String normalize(String text){
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .toLowerCase(Locale.ROOT).replace('đ','d').replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
    static boolean matches(String label,String query){
        String name=normalize(label),search=normalize(query);
        if(search.isEmpty()||name.contains(search))return true;
        String bare=search.replaceFirst("^(thanh pho|tinh|tp|quan|huyen|phuong|xa) +","");
        if(!bare.isEmpty()&&name.contains(bare))return true;
        if(name.equals("thanh pho ho chi minh")){
            String compact=bare.replace(" ","");
            return compact.equals("hcm")||compact.equals("tphcm")||compact.equals("hochiminh")||compact.equals("saigon")||compact.equals("sg");
        }
        return false;
    }
}
