package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.text.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class AddressPicker {
    private final VietnamAddressCatalog catalog;
    private final AutoCompleteTextView province, ward;
    AddressPicker(Activity activity, AutoCompleteTextView province, AutoCompleteTextView ward) throws IOException {
        this.province=province;this.ward=ward;
        catalog=new VietnamAddressCatalog(new InputStreamReader(activity.getAssets().open("vietnam-addresses.tsv"),java.nio.charset.StandardCharsets.UTF_8));
        province.setThreshold(0);ward.setThreshold(0);
        province.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,new ArrayList<>(catalog.provinces.keySet())));
        province.setOnClickListener(v->((ArrayAdapter<?>)province.getAdapter()).getFilter().filter(null,count->province.showDropDown()));
        ward.setOnClickListener(v->{if(catalog.provinces.containsKey(province.getText().toString()))((ArrayAdapter<?>)ward.getAdapter()).getFilter().filter(null,count->ward.showDropDown());else province.setError("Chọn tỉnh/thành phố trước");});
        province.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                ward.setText("");
                VietnamAddressCatalog.Province p=catalog.provinces.get(s.toString());
                ward.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,p==null?new ArrayList<>():new ArrayList<>(p.wards.keySet())));
            }
            public void afterTextChanged(Editable s){}
        });
    }
    boolean apply(Map<String,Object> address){
        VietnamAddressCatalog.Province p=catalog.provinces.get(province.getText().toString().trim());
        if(p==null){province.setError("Chọn tỉnh/thành phố trong danh sách hiện hành");return false;}
        String name=ward.getText().toString().trim(),code=p.wards.get(name);
        if(code==null){ward.setError("Chọn phường/xã thuộc tỉnh/thành phố đã chọn");return false;}
        address.put("province",p.name);address.put("provinceCode",p.code);address.put("ward",name);address.put("wardCode",code);
        address.put("district","");address.put("addressVersion","VN_2_LEVEL_2025");return true;
    }
    static String format(Map<String,Object> address){
        List<String> parts=new ArrayList<>();
        for(String key:new String[]{"street","ward","district","province"}){Object value=address.get(key);if(value instanceof String&&!((String)value).trim().isEmpty())parts.add(((String)value).trim());}
        return android.text.TextUtils.join(", ",parts);
    }
}
