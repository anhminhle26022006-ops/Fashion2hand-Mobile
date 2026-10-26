package vn.edu.ueh.thudinh.fashion2hand;

import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** GHN codes are selected from the server catalogue, never inferred from Vietnam administrative IDs. */
final class GhnAddressPicker {
    private final AppCompatActivity activity;
    private final MemberTwoRepository repo=MemberTwoRepository.getInstance();
    private final BooleanSupplier active;
    private final Consumer<Exception> failure;
    private final String addressId;
    private AlertDialog dialog;
    GhnAddressPicker(AppCompatActivity activity,String addressId,BooleanSupplier active,Consumer<Exception> failure) {
        this.activity=activity;this.addressId=addressId;this.active=active;this.failure=failure;
    }
    void open(){choose("provinces",0,"Chọn tỉnh/thành theo GHN",province->choose("districts",integer(province,"ProvinceID"),"Chọn quận/huyện theo danh mục GHN",district->
        choose("wards",integer(district,"DistrictID"),"Chọn phường/xã theo GHN",ward->{
            if(!active.getAsBoolean())return;
            new AlertDialog.Builder(activity).setTitle("Xác nhận địa chỉ giao hàng GHN")
                .setMessage(ward.get("WardName")+", "+district.get("DistrictName")+", "+province.get("ProvinceName")+"\n\nGiữ nguyên người nhận, điện thoại và số nhà/đường của địa chỉ đã lưu. Danh mục này là mã riêng của GHN, có thể dùng tên địa giới cũ.")
                .setNegativeButton("Hủy",null).setPositiveButton("Lưu địa chỉ GHN",(d,w)->{
                    AlertDialog progress=new AlertDialog.Builder(activity).setMessage("Đang xác minh địa chỉ GHN…").setCancelable(false).show();
                    repo.saveGhnAddress(addressId,integer(province,"ProvinceID"),integer(district,"DistrictID"),String.valueOf(ward.get("WardCode")))
                        .addOnCompleteListener(result->{progress.dismiss();if(active.getAsBoolean()&&!result.isSuccessful())failure.accept(result.getException());});
                }).show();
        })));
    }
    private int integer(Map<String,Object> item,String key){return ((Number)item.get(key)).intValue();}
    @SuppressWarnings("unchecked") private void choose(String kind,int parent,String title,Consumer<Map<String,Object>> selected){
        if(!active.getAsBoolean())return;
        dialog=new AlertDialog.Builder(activity).setMessage("Đang tải danh mục GHN…").setNegativeButton("Hủy",null).show();
        AlertDialog loading=dialog;
        repo.ghnCatalog(kind,parent).addOnCompleteListener(result->{
            boolean cancelled=!loading.isShowing();loading.dismiss();if(!active.getAsBoolean()||cancelled)return;
            if(!result.isSuccessful()){failure.accept(result.getException());return;}
            Map<String,Object> data=(Map<String,Object>)result.getResult().getData();Object raw=data.get("items");
            if(!(raw instanceof List)||((List<?>)raw).isEmpty()){failure.accept(new IllegalStateException("GHN chưa trả danh mục địa chỉ. Vui lòng thử lại."));return;}
            List<Map<String,Object>> items=(List<Map<String,Object>>)raw;String key=kind.equals("provinces")?"ProvinceName":kind.equals("districts")?"DistrictName":"WardName";
            String[] labels=new String[items.size()];for(int i=0;i<labels.length;i++)labels[i]=String.valueOf(items.get(i).get(key));
            dialog=new AlertDialog.Builder(activity).setTitle(title).setItems(labels,(d,index)->selected.accept(items.get(index))).setNegativeButton("Hủy",null).show();
        });
    }
}
