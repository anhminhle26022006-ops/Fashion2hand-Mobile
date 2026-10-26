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
    private final boolean pickup;
    private final Runnable saved;
    private AlertDialog dialog;
    GhnAddressPicker(AppCompatActivity activity,String addressId,BooleanSupplier active,Consumer<Exception> failure) {
        this(activity,addressId,active,failure,false,()->{});
    }
    GhnAddressPicker(AppCompatActivity activity,String addressId,BooleanSupplier active,Consumer<Exception> failure,boolean pickup,Runnable saved) {
        this.activity=activity;this.addressId=addressId;this.active=active;this.failure=failure;
        this.pickup=pickup;this.saved=saved;
    }
    void open(){choose("provinces",0,pickup?"Tỉnh/thành lấy hàng · Người bán":"Tỉnh/thành nhận hàng · Người mua",province->choose("districts",integer(province,"ProvinceID"),pickup?"Quận/huyện lấy hàng · Người bán":"Quận/huyện nhận hàng · Người mua",district->
        choose("wards",integer(district,"DistrictID"),pickup?"Phường/xã lấy hàng · Người bán":"Phường/xã nhận hàng · Người mua",ward->{
            if(!active.getAsBoolean())return;
            final String selected=ward.get("WardName")+", "+district.get("DistrictName")+", "+province.get("ProvinceName");
            new AlertDialog.Builder(activity).setTitle(pickup?"Địa chỉ lấy hàng của người bán":"Địa chỉ nhận hàng của người mua")
                .setMessage(selected+"\n\n"+(pickup?"Đây là nơi GHN đến lấy hàng từ người bán khi tạo vận đơn. ":"Đây là nơi GHN giao hàng cho người nhận. ")+"Giữ nguyên tên, điện thoại và số nhà/đường đã lưu. Kiểm tra đúng tỉnh, quận và phường trước khi xác nhận.")
                .setNegativeButton("Hủy",null).setPositiveButton(pickup?"Lưu địa chỉ lấy hàng":"Lưu địa chỉ nhận hàng",(d,w)->{
                    AlertDialog progress=new AlertDialog.Builder(activity).setMessage("Đang xác minh địa chỉ GHN…").setCancelable(false).show();
                    repo.saveGhnAddress(addressId,integer(province,"ProvinceID"),integer(district,"DistrictID"),String.valueOf(ward.get("WardCode")))
                        .addOnCompleteListener(result->{
                            progress.dismiss();if(!active.getAsBoolean())return;
                            if(!result.isSuccessful()){failure.accept(result.getException());return;}
                            android.widget.Toast.makeText(activity,pickup?"Đã lưu địa chỉ lấy hàng của người bán":"Đã lưu địa chỉ nhận hàng",android.widget.Toast.LENGTH_LONG).show();
                            saved.run();
                            if(pickup)new AlertDialog.Builder(activity).setTitle("Đã lưu địa chỉ lấy hàng của người bán")
                                .setMessage(selected+"\n\nMã địa chỉ đã được lưu. Nếu chưa liên kết shop, Admin cần cấu hình shop GHN cho người bán. Thao tác này chưa tạo vận đơn hoặc gọi lấy hàng.")
                                .setPositiveButton("Đã hiểu",null).show();
                        });
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
