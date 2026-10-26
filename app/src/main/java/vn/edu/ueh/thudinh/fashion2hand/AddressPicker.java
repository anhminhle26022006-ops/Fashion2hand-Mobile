package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Context;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;

final class AddressPicker {
    private VietnamAddressCatalog catalog;
    private final Context activity;
    private final AutoCompleteTextView province,district,ward;
    private boolean twoLevel=false;
    AddressPicker(Context activity,AutoCompleteTextView province,AutoCompleteTextView district,AutoCompleteTextView ward) throws IOException {
        this.activity=activity;this.province=province;this.district=district;this.ward=ward;
        load(false);
        for(AutoCompleteTextView field:new AutoCompleteTextView[]{province,district,ward})field.setThreshold(0);
        province.setOnClickListener(v->show(province));
        district.setOnClickListener(v->{if(selectedProvince()!=null)show(district);else province.setError("Chọn tỉnh/thành phố trước");});
        ward.setOnClickListener(v->{if(selectedProvince()!=null&&(twoLevel||selectedDistrict()!=null))show(ward);else (twoLevel?province:district).setError("Chọn đơn vị cấp trên trước");});
        province.addTextChangedListener(watcher(()->{district.setText("");ward.setText("");refreshDistricts();refreshWards();}));
        district.addTextChangedListener(watcher(()->{ward.setText("");refreshWards();}));
        refreshDistricts();refreshWards();
    }
    private TextWatcher watcher(Runnable changed){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){changed.run();}public void afterTextChanged(Editable s){}};}
    private void load(boolean current) throws IOException {
        twoLevel=current;
        catalog=new VietnamAddressCatalog(new InputStreamReader(activity.getAssets().open(current?"vietnam-addresses.tsv":"vietnam-addresses-legacy.tsv"),java.nio.charset.StandardCharsets.UTF_8));
        district.setVisibility(current?View.GONE:View.VISIBLE);
        province.setAdapter(adapter(catalog.provinces.keySet()));
    }
    void useSavedVersion(Map<String,Object> source) throws IOException {
        boolean current="VN_2_LEVEL_2025".equals(source.get("addressVersion"));
        if(current!=twoLevel){load(current);refreshDistricts();refreshWards();}
    }
    private ArrayAdapter<String> adapter(Collection<String> items){return new AddressSearchAdapter(activity,items);}
    private void show(AutoCompleteTextView field){if(field.isEnabled()&&field.getAdapter()!=null){String query=field.getText().toString();ArrayAdapter<?> adapter=(ArrayAdapter<?>)field.getAdapter();if(adapter instanceof AddressSearchAdapter&&((AddressSearchAdapter)adapter).isExactEntry(query))query="";adapter.getFilter().filter(query,count->field.showDropDown());}}
    private VietnamAddressCatalog.Province selectedProvince(){return catalog.provinces.get(province.getText().toString().trim());}
    private VietnamAddressCatalog.District selectedDistrict(){VietnamAddressCatalog.Province p=selectedProvince();return p==null?null:p.districts.get(district.getText().toString().trim());}
    private void refreshDistricts(){VietnamAddressCatalog.Province p=selectedProvince();district.setAdapter(adapter(p==null?Collections.emptyList():p.districts.keySet()));district.setEnabled(p!=null&&!twoLevel);}
    private void refreshWards(){
        VietnamAddressCatalog.Province p=selectedProvince();VietnamAddressCatalog.District d=selectedDistrict();
        Collection<String> wards=twoLevel?(p==null?Collections.emptyList():p.wards.keySet()):(d==null?Collections.emptyList():d.wards.keySet());
        ward.setAdapter(adapter(wards));ward.setEnabled(twoLevel?p!=null:d!=null&&!d.wards.isEmpty());
        ward.setHint(!twoLevel&&d!=null&&d.wards.isEmpty()?"Huyện không có đơn vị cấp xã":"Chọn hoặc tìm phường / xã");
    }
    boolean wardRequired(){VietnamAddressCatalog.District d=selectedDistrict();return twoLevel||d==null||!d.wards.isEmpty();}
    void restoreWardCode(Map<String,Object> source){
        VietnamAddressCatalog.Province p=selectedProvince();VietnamAddressCatalog.District d=selectedDistrict();
        Map<String,String> options=twoLevel?(p==null?Collections.emptyMap():p.wards):(d==null?Collections.emptyMap():d.wards);
        for(Map.Entry<String,String> entry:options.entrySet())if(entry.getValue().equals(String.valueOf(source.get("wardCode")))){ward.setText(entry.getKey(),false);return;}
    }
    boolean apply(Map<String,Object> address){
        VietnamAddressCatalog.Province p=selectedProvince();if(p==null){province.setError("Chọn tỉnh/thành phố trong danh sách");return false;}
        VietnamAddressCatalog.District d=selectedDistrict();if(!twoLevel&&d==null){district.setError("Chọn quận/huyện thuộc tỉnh/thành phố");return false;}
        String name=ward.getText().toString().trim(),code=twoLevel?p.wards.get(name):d.wards.get(name);
        if(code==null&&wardRequired()){ward.setError("Chọn phường/xã thuộc đơn vị đã chọn");return false;}
        address.put("province",p.name);address.put("provinceCode",p.code);address.put("ward",code==null?"":name);address.put("wardCode",code==null?"":code);
        address.put("district",twoLevel?"":d.name);if(!twoLevel)address.put("districtCode",d.code);
        address.put("addressVersion",twoLevel?"VN_2_LEVEL_2025":"VN_3_LEVEL_LEGACY");return true;
    }
    static String format(Map<String,Object> address){
        List<String> parts=new ArrayList<>();for(String key:new String[]{"street","ward","district","province"}){Object value=address.get(key);if(value instanceof String&&!((String)value).trim().isEmpty())parts.add(((String)value).trim());}
        return android.text.TextUtils.join(", ",parts);
    }
}
