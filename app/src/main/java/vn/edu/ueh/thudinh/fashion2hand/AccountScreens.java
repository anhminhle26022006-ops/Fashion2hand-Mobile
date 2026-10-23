package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import java.io.*;
import java.util.*;

/** Backend-backed account forms; drafts only are kept on the device. */
final class AccountScreens {
    interface Host { String uid(); void open(int screen); void listen(ListenerRegistration listener); void pick(String slot); boolean restoringDraft(); }
    private final Activity a;
    private final Host host;
    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;
    private int generation;
    private String addressId = "", verificationKey = "", supportKey = "";
    private final Map<String, String> identityPhotos = new HashMap<>();
    private final String[] banks = {"Vietcombank", "Techcombank", "MB Bank", "BIDV", "ACB"};

    AccountScreens(Activity a, Host host) { this(a, host, FirebaseFirestore.getInstance(), FirebaseFunctions.getInstance()); }
    AccountScreens(Activity a, Host host, FirebaseFirestore db, FirebaseFunctions functions) { this.a=a; this.host=host; this.db=db; this.functions=functions; }
    void invalidate() { generation++; }
    void clearSession() { invalidate(); addressId=""; verificationKey=""; supportKey=""; identityPhotos.clear(); }
    private boolean valid(int token) { return token==generation && !a.isDestroyed(); }
    Bundle save() {
        Bundle state=new Bundle(); state.putString("address",addressId); state.putString("verification",verificationKey); state.putString("support",supportKey);
        for(Map.Entry<String,String> entry:identityPhotos.entrySet())state.putString("photo:"+entry.getKey(),entry.getValue()); return state;
    }
    void restore(Bundle state) {
        if(state==null)return; addressId=state.getString("address","");verificationKey=state.getString("verification","");supportKey=state.getString("support","");
        for(String slot:new String[]{"front","back","selfie"}){String path=state.getString("photo:"+slot);if(path!=null&&new File(path).isFile())identityPhotos.put(slot,path);}
    }
    private Task<HttpsCallableResult> call(String name, Map<String,Object> data) { return functions.getHttpsCallable(name).call(data); }
    private Map<String,Object> args(Object... values) { Map<String,Object> data=new HashMap<>();for(int i=0;i<values.length;i+=2)data.put((String)values[i],values[i+1]);return data; }
    @SuppressWarnings("unchecked") private Map<String,Object> result(HttpsCallableResult value) { return value.getData() instanceof Map?(Map<String,Object>)value.getData():new HashMap<>(); }
    private String string(Map<String,Object> data,String key,String fallback){Object value=data.get(key);return value instanceof String&&!((String)value).isEmpty()?(String)value:fallback;}
    private void error(Exception error){Toast.makeText(a,error.getMessage()==null?"Không kết nối được backend. Vui lòng thử lại.":error.getMessage(),Toast.LENGTH_LONG).show();}
    private TextView label(String value){TextView t=new TextView(a);t.setText(value);t.setTextColor(0xFF173B32);t.setTextSize(14);t.setPadding(12,12,12,12);return t;}
    private Button button(String value,Runnable action){Button b=new Button(a);b.setText(value);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private void text(int id,String value){TextView view=a.findViewById(id);if(view!=null)view.setText(value);}
    private void click(int id,Runnable action){View view=a.findViewById(id);if(view!=null)view.setOnClickListener(v->action.run());}
    private EditText input(int id){return a.findViewById(id);}
    private String value(int id){return input(id).getText().toString().trim();}
    private boolean checked(int id){return ((CheckBox)a.findViewById(id)).isChecked();}

    void addresses() {
        final int token=++generation;
        LinearLayout cards=a.findViewById(R.id.addressCards);cards.removeAllViews();cards.addView(label("Đang tải địa chỉ…"));
        click(R.id.btnAddAddress,()->{addressId=UUID.randomUUID().toString();host.open(R.layout.activity_address_form);});
        host.listen(db.collection("users").document(host.uid()).collection("addresses").addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,failure)->{
            if(!valid(token))return;cards.removeAllViews();
            if(failure!=null){cards.addView(label("Không tải được địa chỉ."));cards.addView(button("Thử lại",()->host.open(R.layout.activity_addresses)));return;}
            if(snapshot==null)return;
            if(snapshot.getMetadata().isFromCache())cards.addView(label("Đang chờ kết nối backend…"));
            if(snapshot.isEmpty()&&!snapshot.getMetadata().isFromCache())cards.addView(label("Bạn chưa có địa chỉ nhận hàng."));
            for(DocumentSnapshot doc:snapshot.getDocuments()){
                Map<String,Object> data=doc.getData();LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(8,12,8,12);
                card.addView(label(("WORK".equals(doc.getString("label"))?"Văn phòng":"Nhà riêng")+(Boolean.TRUE.equals(doc.getBoolean("isDefault"))?" · Mặc định":"")));
                card.addView(label(string(data,"recipient","")+" · "+string(data,"phone","")+"\n"+string(data,"street","")+", "+string(data,"ward","")+"\n"+string(data,"district","")+", "+string(data,"province","")));
                card.addView(button("Chỉnh sửa",()->{addressId=doc.getId();host.open(R.layout.activity_address_form);}));
                card.addView(button("Xóa",()->new AlertDialog.Builder(a).setMessage("Xóa địa chỉ này?").setNegativeButton("Hủy",null).setPositiveButton("Xóa",(dialog,which)->call("deleteAddress",args("id",doc.getId())).addOnFailureListener(this::error)).show()));cards.addView(card);
            }
        }));
    }

    private Set<Integer> track(int... ids){Set<Integer> edited=new HashSet<>();for(int id:ids)input(id).addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){edited.add(id);}public void afterTextChanged(Editable s){}});return edited;}
    void addressForm() {
        final int token=++generation;
        if(addressId.isEmpty())addressId=UUID.randomUUID().toString();
        final String id=addressId;final String uid=host.uid();
        int[] ids={R.id.edtAddressRecipient,R.id.edtAddressPhone,R.id.edtAddressProvince,R.id.edtAddressDistrict,R.id.edtAddressWard,R.id.edtAddressStreet};
        String[] keys={"recipient","phone","province","district","ward","street"};
        Set<Integer> edited=track(ids);Button save=a.findViewById(R.id.btnSaveAddress);save.setEnabled(false);
        text(R.id.txtAddressFormTitle,"Địa chỉ nhận hàng");
        db.document("users/"+uid+"/addresses/"+id).get(Source.SERVER).addOnSuccessListener(doc->{
            if(!valid(token))return;save.setEnabled(true);
            if(doc.exists()){
                for(int i=0;i<ids.length;i++)if(!edited.contains(ids[i]))input(ids[i]).setText(doc.getString(keys[i]));
                if(!host.restoringDraft()){
                    ((RadioGroup)a.findViewById(R.id.rgAddressType)).check("WORK".equals(doc.getString("label"))?R.id.radioAddressWork:R.id.radioAddressHome);
                    ((CheckBox)a.findViewById(R.id.checkAddressDefault)).setChecked(Boolean.TRUE.equals(doc.getBoolean("isDefault")));
                }
            }
        }).addOnFailureListener(failure->{if(valid(token)){error(failure);text(R.id.txtAddressFormTitle,"Không tải được địa chỉ · Quay lại để thử lại");}});
        click(R.id.btnSaveAddress,()->{
            Map<String,Object> data=new HashMap<>();for(int i=0;i<ids.length;i++){String v=value(ids[i]);if(v.isEmpty()){input(ids[i]).setError("Vui lòng nhập thông tin");return;}data.put(keys[i],v);}
            if(MarketplaceLinks.normalizePhone(value(R.id.edtAddressPhone)).isEmpty()){input(R.id.edtAddressPhone).setError("Số điện thoại không hợp lệ");return;}
            data.put("label",((RadioGroup)a.findViewById(R.id.rgAddressType)).getCheckedRadioButtonId()==R.id.radioAddressWork?"WORK":"HOME");data.put("isDefault",checked(R.id.checkAddressDefault));
            save.setEnabled(false);call("saveAddress",args("id",id,"address",data)).addOnSuccessListener(done->{if(valid(token)){addressId="";host.open(R.layout.activity_addresses);}})
                .addOnFailureListener(failure->{if(valid(token)){save.setEnabled(true);error(failure);}});
        });
    }

    void payout() {
        final int token=++generation;Spinner spinner=a.findViewById(R.id.spinnerPayoutBank);spinner.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,banks));
        Set<Integer> edited=track(R.id.edtPayoutHolder,R.id.edtPayoutAccountNumber);Button save=a.findViewById(R.id.btnSavePayoutInfo);save.setEnabled(false);text(R.id.txtPayoutStatus,"Đang tải thông tin…");
        call("getAccountData",args()).addOnSuccessListener(response->{
            if(!valid(token))return;save.setEnabled(true);Object saved=result(response).get("payout");
            if(saved instanceof Map){Map<String,Object> data=(Map<String,Object>)saved;
                if(!edited.contains(R.id.edtPayoutHolder))input(R.id.edtPayoutHolder).setText(string(data,"holder",""));
                if(!edited.contains(R.id.edtPayoutAccountNumber))input(R.id.edtPayoutAccountNumber).setText(string(data,"accountNumber",""));
                if(!host.restoringDraft())for(int i=0;i<banks.length;i++)if(banks[i].equals(data.get("bank")))spinner.setSelection(i);
            }
            text(R.id.txtPayoutStatus,saved instanceof Map?"Đã lưu backend · Chưa xác minh ngân hàng":"Chưa lưu tài khoản nhận tiền");
        }).addOnFailureListener(failure->{if(valid(token)){text(R.id.txtPayoutStatus,"Không tải được. Nhấn để thử lại.");a.findViewById(R.id.txtPayoutStatus).setOnClickListener(v->host.open(R.layout.activity_payment_info));error(failure);}});
        click(R.id.btnSavePayoutInfo,()->{
            String holder=value(R.id.edtPayoutHolder),number=value(R.id.edtPayoutAccountNumber);
            if(holder.length()<2){input(R.id.edtPayoutHolder).setError("Nhập tên chủ tài khoản");return;}
            if(!number.matches("[0-9]{6,30}")){input(R.id.edtPayoutAccountNumber).setError("Số tài khoản gồm 6–30 chữ số");return;}
            if(!checked(R.id.checkPayoutConsent)){Toast.makeText(a,"Vui lòng xác nhận thông tin tài khoản.",Toast.LENGTH_SHORT).show();return;}
            save.setEnabled(false);call("savePayoutAccount",args("bank",spinner.getSelectedItem().toString(),"holder",holder,"accountNumber",number,"consent",true))
                .addOnSuccessListener(done->{if(valid(token)){text(R.id.txtPayoutStatus,"Đã lưu backend · Chưa xác minh ngân hàng");Toast.makeText(a,"Đã lưu tài khoản nhận tiền.",Toast.LENGTH_SHORT).show();}})
                .addOnFailureListener(failure->{if(valid(token))error(failure);}).addOnCompleteListener(done->{if(valid(token))save.setEnabled(true);});
        });
    }

    void verificationForm() {
        ++generation;if(verificationKey.isEmpty())verificationKey=UUID.randomUUID().toString();
        Spinner type=a.findViewById(R.id.spinnerIdentityDocument);type.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"CCCD gắn chip","CCCD mã vạch","Hộ chiếu"}));
        for(String slot:new String[]{"front","back","selfie"}){final String key=slot;click(photoId(slot),()->host.pick(key));photoLabel(slot);}
        click(R.id.btnSubmitVerification,()->{
            final int token=generation;String name=value(R.id.edtLegalName),birth=value(R.id.edtLegalBirthDate),number=value(R.id.edtIdentityNumber);
            if(name.isEmpty()||birth.isEmpty()||number.isEmpty()){Toast.makeText(a,"Điền đủ họ tên, ngày sinh và số giấy tờ.",Toast.LENGTH_LONG).show();return;}
            if(identityPhotos.size()!=3){Toast.makeText(a,"Chọn đủ ảnh mặt trước, mặt sau và chân dung.",Toast.LENGTH_LONG).show();return;}
            if(!checked(R.id.checkIdentityAccurate)||!checked(R.id.checkIdentityPrivacy)){Toast.makeText(a,"Vui lòng xác nhận thông tin và đồng ý xử lý dữ liệu.",Toast.LENGTH_LONG).show();return;}
            Button submit=a.findViewById(R.id.btnSubmitVerification);submit.setEnabled(false);submit.setText("Đang gửi hồ sơ…");
            Map<String,Object> data=args("requestId",verificationKey,"legalName",name,"birthDate",birth,"identityNumber",number,"documentType",type.getSelectedItem().toString(),"accurate",true,"consent",true);
            Map<String,String> paths=new HashMap<>(identityPhotos);
            Tasks.call(java.util.concurrent.ForkJoinPool.commonPool(),()->{
                Map<String,Object> photos=new HashMap<>();for(Map.Entry<String,String> entry:paths.entrySet()){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(entry.getValue())){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
                    photos.put(entry.getKey(),android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
                }data.put("photos",photos);return data;
            }).continueWithTask(prepared->{if(!prepared.isSuccessful())throw prepared.getException();return call("submitVerification",prepared.getResult());})
                .addOnSuccessListener(done->{if(valid(token)){identityPhotos.clear();verificationKey="";host.open(R.layout.activity_verification_status);}})
                .addOnFailureListener(failure->{if(valid(token))error(failure);})
                .addOnCompleteListener(done->{if(valid(token)){submit.setEnabled(true);submit.setText("Gửi hồ sơ xác minh");}});
        });
    }

    private int photoId(String slot){return "front".equals(slot)?R.id.btnIdentityFront:"back".equals(slot)?R.id.btnIdentityBack:R.id.btnIdentitySelfie;}
    private void photoLabel(String slot){if(identityPhotos.containsKey(slot))text(photoId(slot),"✓ Đã chọn ảnh "+("front".equals(slot)?"mặt trước":"back".equals(slot)?"mặt sau":"chân dung"));}
    void selectPhoto(String slot,Uri uri,Bitmap bitmap) {
        try{
            Bitmap image=bitmap;
            if(image==null){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=a.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
                try(InputStream in=a.getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,options);}}
            if(image==null)throw new IOException("Không đọc được ảnh.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,75,out);
            if(out.size()>1200000)throw new IOException("Ảnh quá lớn. Chọn ảnh dưới 1,2 MB.");
            File file=new File(a.getCacheDir(),"identity-"+UUID.randomUUID()+".jpg");try(FileOutputStream stream=new FileOutputStream(file)){stream.write(out.toByteArray());}
            identityPhotos.put(slot,file.getAbsolutePath());photoLabel(slot);
        }catch(Exception failure){error(failure);}
    }

    void verificationStatus() {
        final int token=++generation;
        text(R.id.txtVerificationStatusTitle,"Đang tải hồ sơ xác minh…");text(R.id.txtVerificationLegalName,"—");text(R.id.txtVerificationDocumentType,"—");text(R.id.txtVerificationCaseNumber,"—");text(R.id.txtVerificationStatusValue,"—");text(R.id.txtVerificationStatusDescription,"");
        final Runnable[] load=new Runnable[1];load[0]=()->call("getAccountData",args()).addOnSuccessListener(response->{
            if(!valid(token))return;Map<String,Object> data=result(response);Object record=data.get("verification");
            if(!(record instanceof Map)){
                boolean verified="VERIFIED".equals(data.get("verificationStatus"));text(R.id.txtVerificationStatusTitle,verified?"Tài khoản đã xác minh":"Chưa gửi hồ sơ xác minh");text(R.id.txtVerificationStatusValue,verified?"Đã duyệt":"Chưa gửi");
                text(R.id.btnVerificationStatusProfile,verified?"Về hồ sơ":"Gửi hồ sơ xác minh");click(R.id.btnVerificationStatusProfile,()->host.open(verified?R.layout.activity_profile:R.layout.activity_account_verification));return;
            }
            Map<String,Object> saved=(Map<String,Object>)record;String status=string(saved,"status","");
            text(R.id.txtVerificationStatusTitle,"APPROVED".equals(status)?"Tài khoản đã xác minh":"REJECTED".equals(status)?"Hồ sơ chưa được duyệt":"Hồ sơ đang chờ duyệt");
            text(R.id.txtVerificationStatusValue,"APPROVED".equals(status)?"Đã duyệt":"REJECTED".equals(status)?"Từ chối":"Chờ duyệt");
            text(R.id.txtVerificationStatusDescription,string(saved,"note","Kết quả được cập nhật từ backend. Nhấn tiêu đề để tải lại."));
            text(R.id.txtVerificationLegalName,string(saved,"legalName","—"));text(R.id.txtVerificationDocumentType,string(saved,"documentType","—"));text(R.id.txtVerificationCaseNumber,string(saved,"id","—"));
            if("REJECTED".equals(status)){text(R.id.btnVerificationStatusProfile,"Gửi lại hồ sơ");click(R.id.btnVerificationStatusProfile,()->host.open(R.layout.activity_account_verification));}
        }).addOnFailureListener(failure->{if(valid(token)){text(R.id.txtVerificationStatusTitle,"Không tải được hồ sơ · Nhấn để thử lại");error(failure);}});
        click(R.id.txtVerificationStatusTitle,()->load[0].run());click(R.id.btnVerificationStatusProfile,()->host.open(R.layout.activity_profile));load[0].run();
    }

    void privacy() {
        ++generation;FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        text(R.id.txtPrivacyEmail,user==null||user.getEmail()==null?"Chưa có email":user.getEmail()+(user.isEmailVerified()?" · Đã xác minh":" · Chưa xác minh"));
        text(R.id.txtPrivacyPhone,user==null||user.getPhoneNumber()==null?"Chưa xác minh số điện thoại":user.getPhoneNumber()+" · Đã xác minh");
        click(R.id.btnPrivacyEmail,()->host.open(R.layout.activity_change_email));click(R.id.btnPrivacyPhone,()->host.open(R.layout.activity_verify_phone));click(R.id.btnPrivacyIdentityVerification,()->host.open(R.layout.activity_verification_status));
    }

    void support() {
        final int token=++generation;if(supportKey.isEmpty())supportKey=UUID.randomUUID().toString();
        EditText title=input(R.id.edtSupportTitle),description=input(R.id.edtSupportDescription);Button send=a.findViewById(R.id.btnSendSupport);TextView status=a.findViewById(R.id.txtSupportStatus);
        status.setText("Đang tải các yêu cầu đã gửi…");
        call("getAccountData",args()).addOnSuccessListener(response->{if(valid(token)){
            Object tickets=result(response).get("tickets");StringBuilder value=new StringBuilder("Yêu cầu của bạn:\n");
            if(tickets instanceof List)for(Object item:(List<?>)tickets)if(item instanceof Map){Map<String,Object> ticket=(Map<String,Object>)item;value.append(string(ticket,"title","")).append(" · ").append(string(ticket,"status","")).append("\n");String note=string(ticket,"resolutionNote","");if(!note.isEmpty())value.append(note).append("\n");}
            status.setText(tickets instanceof List&&((List<?>)tickets).isEmpty()?"Bạn chưa gửi yêu cầu hỗ trợ.":value.toString());
        }}).addOnFailureListener(failure->{if(valid(token)){status.setText("Không tải được lịch sử · Nhấn để thử lại.");status.setOnClickListener(v->host.open(R.layout.activity_support_contact));}});
        send.setOnClickListener(v->{if(title.getText().toString().trim().length()<3){title.setError("Tiêu đề tối thiểu 3 ký tự");return;}if(description.getText().toString().trim().length()<10){description.setError("Nội dung tối thiểu 10 ký tự");return;}
            send.setEnabled(false);call("submitSupportTicket",args("requestId",supportKey,"title",title.getText().toString().trim(),"description",description.getText().toString().trim()))
                .addOnSuccessListener(response->{if(valid(token)){supportKey=UUID.randomUUID().toString();title.setText("");description.setText("");status.setText("Đã gửi yêu cầu: "+result(response).get("id")+"\nBạn có thể xem trạng thái khi mở lại màn này.");}})
                .addOnFailureListener(failure->{if(valid(token))error(failure);}).addOnCompleteListener(done->{if(valid(token))send.setEnabled(true);});
        });
    }
}
