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
    private Map<String,Object> address = new HashMap<>();
    GhnAddressPicker(AppCompatActivity activity,String addressId,BooleanSupplier active,Consumer<Exception> failure) {
        this(activity,addressId,active,failure,false,()->{});
    }
    GhnAddressPicker(AppCompatActivity activity,String addressId,BooleanSupplier active,Consumer<Exception> failure,boolean pickup,Runnable saved) {
        this.activity=activity;this.addressId=addressId;this.active=active;this.failure=failure;
        this.pickup=pickup;this.saved=saved;
    }
    void open(){
        String uid=com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if(uid==null)return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).collection("addresses").document(addressId).get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener(result->{
            if(!active.getAsBoolean())return;
            if(!result.isSuccessful()){failure.accept(result.getException());return;}
            if(!result.getResult().exists()){failure.accept(new IllegalStateException("Địa chỉ không còn tồn tại."));return;}
            address=result.getResult().getData();chooseLocation();
        });
    }
    private void chooseLocation(){choose("provinces",0,pickup?"Tỉnh/thành lấy hàng · Người bán":"Tỉnh/thành nhận hàng · Người mua",province->choose("districts",integer(province,"ProvinceID"),pickup?"Quận/huyện lấy hàng · Người bán":"Quận/huyện nhận hàng · Người mua",district->
        choose("wards",integer(district,"DistrictID"),pickup?"Phường/xã lấy hàng · Người bán":"Phường/xã nhận hàng · Người mua",ward->{
            if(!active.getAsBoolean())return;
            final String selected=ward.get("WardName")+", "+district.get("DistrictName")+", "+province.get("ProvinceName");
            android.widget.LinearLayout form=new android.widget.LinearLayout(activity);form.setOrientation(1);form.setPadding(32,8,32,8);
            String[] keys={"recipient","phone","street"},hints={"Họ tên liên hệ","Số điện thoại","Số nhà, đường (có thể sửa)"};
            android.widget.EditText[] fields=new android.widget.EditText[3];
            for(int i=0;i<3;i++){fields[i]=new android.widget.EditText(activity);fields[i].setHint(hints[i]);fields[i].setText(String.valueOf(address.getOrDefault(keys[i],"")));form.addView(fields[i]);}
            fields[1].setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            new AlertDialog.Builder(activity).setTitle(pickup?"Địa chỉ lấy hàng của người bán":"Địa chỉ nhận hàng của người mua")
                .setMessage(selected+"\n\n"+(pickup?"Đây là nơi GHN đến lấy hàng. Khi lưu, tên, số điện thoại và địa chỉ này sẽ được gửi đến GHN để thiết lập điểm lấy hàng cho tài khoản của bạn. Chưa tạo vận đơn hoặc gọi lấy hàng.":"Đây là nơi GHN giao hàng cho người nhận.")).setView(form)
                .setNegativeButton("Hủy",null).setPositiveButton(pickup?"Lưu địa chỉ lấy hàng":"Lưu địa chỉ nhận hàng",(d,w)->{
                    AlertDialog progress=new AlertDialog.Builder(activity).setMessage("Đang xác minh địa chỉ GHN…").setCancelable(false).show();
                    Map<String,Object> contact=new HashMap<>();for(int i=0;i<3;i++)contact.put(keys[i],fields[i].getText().toString());
                    repo.saveGhnAddress(addressId,integer(province,"ProvinceID"),integer(district,"DistrictID"),String.valueOf(ward.get("WardCode")),contact)
                        .onSuccessTask(result->{
                            if(pickup){progress.setMessage("Đang thiết lập điểm lấy hàng GHN…");return repo.setupGhnSellerPickup(addressId);}
                            return com.google.android.gms.tasks.Tasks.forResult(result);
                        })
                        .addOnCompleteListener(result->{
                            progress.dismiss();if(!active.getAsBoolean())return;
                            if(!result.isSuccessful()){failure.accept(result.getException());return;}
                            android.widget.Toast.makeText(activity,pickup?"Đã liên kết điểm lấy hàng GHN cho bạn":"Đã lưu địa chỉ nhận hàng",android.widget.Toast.LENGTH_LONG).show();
                            saved.run();
                            if(pickup)new AlertDialog.Builder(activity).setTitle("Đã lưu địa chỉ lấy hàng của người bán")
                                .setMessage(selected+"\n\nĐiểm lấy hàng của bạn đã sẵn sàng. Thao tác này chưa tạo vận đơn hoặc gọi lấy hàng.")
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
