package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.*;
import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.UiFormState;

/** Server-authorized Admin screens. No business state is committed optimistically. */
public abstract class LiveAdminActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = new Runnable() { public void run() { checkSession(false); handler.postDelayed(this, 30000); } };
    private LinearLayout content;
    private TextView message;
    private long generation;
    private boolean active, busy;
    private final List<Map<String, Object>> records = new ArrayList<>();
    private String cursor = "", query = "";
    private LinearLayout rows;
    private Button more;
    private String pendingFingerprint = "", pendingRequestId = "";
    private Bundle settingsDraft;
    private long settingsRevision;
    private boolean settingsRendered, settingsDirty;
    private String dialogTitle = "", dialogText = "";
    private android.app.AlertDialog activeDialog;
    private EditText dialogInput;

    protected String collection() { return ""; }
    protected String recordId() { return ""; }
    protected boolean settingsScreen() { return false; }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object value) { return value instanceof Map ? (Map<String,Object>) value : new HashMap<>(); }
    static String str(Map<String,Object> data, String key) { Object value = data.get(key); return value == null ? "" : String.valueOf(value); }
    static Map<String,Object> args(Object... pairs) { Map<String,Object> data = new HashMap<>(); for (int i=0;i<pairs.length;i+=2) data.put((String)pairs[i], pairs[i+1]); return data; }
    Task<Map<String,Object>> call(String name, Map<String,Object> data) {
        return FirebaseFunctions.getInstance().getHttpsCallable(name).call(data).continueWith(result -> {
            if (!result.isSuccessful()) throw result.getException(); return map(result.getResult().getData());
        });
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { pendingFingerprint=state.getString("action", ""); pendingRequestId=state.getString("key", ""); query=state.getString("query", ""); settingsDraft=state.getBundle("settingsDraft"); settingsDirty=settingsDraft!=null; settingsRevision=state.getLong("settingsRevision"); dialogTitle=state.getString("dialogTitle",""); dialogText=state.getString("dialogText",""); }
        shell();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        captureDrafts(); state.putBundle("settingsDraft",settingsDraft); state.putLong("settingsRevision",settingsRevision);
        state.putString("dialogTitle",dialogTitle); state.putString("dialogText",dialogText);
        super.onSaveInstanceState(state); state.putString("action",pendingFingerprint); state.putString("key",pendingRequestId); state.putString("query",query);
    }
    @Override protected void onStart() { super.onStart(); active=true; content.removeAllViews(); text(content,"Đang kiểm tra quyền Admin…"); checkSession(true); handler.postDelayed(recheck,30000); }
    @Override protected void onStop() { captureDrafts(); active=false; generation++; handler.removeCallbacks(recheck); super.onStop(); }
    private void captureDrafts() {
        if(settingsRendered && settingsDirty) settingsDraft=UiFormState.capture(content);
        if(activeDialog!=null && activeDialog.isShowing() && dialogInput!=null) dialogText=dialogInput.getText().toString();
    }
    private boolean valid(long token) { return active && !isDestroyed() && generation==token; }
    private void checkSession(boolean load) {
        if (FirebaseAuth.getInstance().getCurrentUser()==null) { deny("Vui lòng đăng nhập Admin."); return; }
        long token=generation;
        call("adminSession",args()).addOnCompleteListener(result -> {
            if (!valid(token)) return;
            if (!result.isSuccessful()) { if (permission(result.getException())) deny("Phiên Admin không còn quyền truy cập."); else error(result.getException(),load ? this::reload : () -> checkSession(false)); return; }
            if(load) reload();
        });
    }
    private boolean permission(Exception error) {
        while (error!=null) { if(error instanceof FirebaseFunctionsException) { FirebaseFunctionsException.Code c=((FirebaseFunctionsException)error).getCode(); return c==FirebaseFunctionsException.Code.PERMISSION_DENIED || c==FirebaseFunctionsException.Code.UNAUTHENTICATED; } error=error.getCause() instanceof Exception?(Exception)error.getCause():null; } return false;
    }
    private void deny(String reason) {
        generation++; busy=false; content.removeAllViews(); text(content,reason); button(content,"Đăng nhập Admin",() -> { startActivity(new Intent(this,AdminLoginActivity.class)); finish(); });
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private void shell() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(0xFFF8F6F0);
        LinearLayout header=new LinearLayout(this); header.setGravity(16); header.setPadding(dp(12),dp(4),dp(12),dp(4));
        Button back=button(header,"‹",this::finish); back.setContentDescription("Quay lại"); back.setLayoutParams(new LinearLayout.LayoutParams(dp(52),dp(52)));
        back.setOnClickListener(v->finish());
        TextView title=text(header,"Quản trị Fashion2hand"); title.setTextSize(19); title.setTypeface(null,1);
        root.addView(header); ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(1); content.setPadding(dp(16),dp(8),dp(16),dp(24)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{ androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        text(content,"Đang kiểm tra quyền Admin…");
    }
    private TextView text(LinearLayout parent,String value) { TextView view=new TextView(this); view.setText(value); view.setTextSize(15); view.setTextColor(0xFF234536); view.setPadding(0,dp(7),0,dp(7)); parent.addView(view); return view; }
    private void title(LinearLayout parent,String value) { TextView view=text(parent,value); view.setTextSize(20); view.setTypeface(null,1); }
    private Button button(LinearLayout parent,String value,Runnable action) { Button view=new Button(this); view.setText(value); view.setAllCaps(false); view.setMinHeight(dp(48)); view.setTextColor(-1); view.setBackgroundTintList(ColorStateList.valueOf(0xFF104536)); view.setOnClickListener(v->{if(!busy)action.run();}); parent.addView(view,new LinearLayout.LayoutParams(-1,-2)); return view; }
    private LinearLayout card(LinearLayout parent) { LinearLayout panel=new LinearLayout(this); panel.setOrientation(1); panel.setPadding(dp(14),dp(8),dp(14),dp(12)); panel.setBackgroundResource(R.drawable.bg_admin_card); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(12); parent.addView(panel,params); return panel; }
    private void enable(View view,boolean value) { view.setEnabled(value); if(view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view; for(int i=0;i<group.getChildCount();i++)enable(group.getChildAt(i),value); } }
    private void error(Exception error,Runnable retry) {
        if(permission(error)) { deny("Quyền Admin đã bị thu hồi hoặc tài khoản bị khóa."); return; }
        busy=false; enable(content,true); text(content,"Không hoàn tất: "+(error==null?"Lỗi kết nối":error.getMessage())); button(content,"Thử lại",retry);
    }
    private void reload() {
        generation++; busy=false; content.removeAllViews(); message=text(content,"Đang tải dữ liệu backend…");
        settingsRendered=false;
        if(settingsScreen()) loadSettings(); else if(!recordId().isEmpty()) loadDetail(); else if(!collection().isEmpty()) loadList(); else loadDashboard();
    }
    private void loadDashboard() {
        long token=generation; call("adminDashboard",args()).addOnCompleteListener(result->{
            if(!valid(token))return; if(!result.isSuccessful()){error(result.getException(),this::reload);return;}
            content.removeAllViews(); title(content,"Dashboard"); Map<String,Object> data=result.getResult();
            for(String key:new String[]{"users","activeUsers","posts","activePosts","orders","reports","disputes","verifications"}) text(content,label(key)+": "+str(data,key));
            text(content,"Cập nhật từ backend: "+str(data,"updatedAt"));
            text(content,"Thanh toán và giao hàng đang dùng môi trường mô phỏng.");
            for(String name:new String[]{"users","posts","orders","reports","disputes","verificationRequests","moneyTransactions","categories","adminAudit"}) button(content,label(name),()->AdminListActivity.open(this,name));
            button(content,"Cấu hình hệ thống",()->startActivity(new Intent(this,AdminSettingsActivity.class)));
            button(content,"Tải lại",this::reload); button(content,"Đăng xuất",()->{ FirebaseAuth.getInstance().signOut(); startActivity(new Intent(this,AdminLoginActivity.class)); finish(); });
        });
    }
    private void loadList() {
        content.removeAllViews(); title(content,label(collection())); records.clear();cursor="";
        EditText search=new EditText(this); search.setHint("Lọc các bản ghi đã tải theo tên, mã, trạng thái"); search.setText(query); content.addView(search);
        rows=new LinearLayout(this);rows.setOrientation(1);content.addView(rows);
        more=button(content,"Đang tải…",this::nextPage);more.setEnabled(false);
        button(content,"Tải lại danh sách",this::reload);
        if(collection().equals("categories")) button(content,"Thêm danh mục",()->ask("Tên danh mục",name->mutate("category",UUID.randomUUID().toString(),args("name",name,"enabled",true),"Thêm danh mục"))).setTag("dialog:Tên danh mục");
        restoreDialog();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();renderRows();}public void afterTextChanged(Editable s){}});nextPage();
    }
    @SuppressWarnings("unchecked") private void nextPage() {
        more.setEnabled(false);long token=generation;
        call("adminList",args("collection",collection(),"cursor",cursor,"limit",50)).addOnCompleteListener(result->{
            if(!valid(token))return;if(!result.isSuccessful()){more.setEnabled(true);error(result.getException(),this::nextPage);return;}
            Object values=result.getResult().get("records");if(values instanceof List)for(Object value:(List<?>)values)records.add(map(value));
            cursor=str(result.getResult(),"nextCursor");more.setText(cursor.isEmpty()?"Đã tải hết ("+records.size()+")":"Tải thêm · đã tải "+records.size());more.setEnabled(!cursor.isEmpty());renderRows();
        });
    }
    private void renderRows() {
        rows.removeAllViews(); int shown=0;String term=query.trim().toLowerCase(Locale.ROOT);
        for(Map<String,Object> record:records){if(!record.toString().toLowerCase(Locale.ROOT).contains(term))continue;shown++;LinearLayout panel=card(rows);panel.setTag("admin|"+str(record,"id"));
            String heading=str(record,"name");if(heading.isEmpty())heading=str(record,"title");if(heading.isEmpty())heading=str(record,"reason");if(heading.isEmpty())heading=str(record,"id");title(panel,heading);
            text(panel,"#"+str(record,"id")+"\n"+summary(record)); button(panel,"Xem chi tiết",()->AdminDetailActivity.open(this,collection(),str(record,"id")));
        }if(shown==0)text(rows,"Không có bản ghi phù hợp trong dữ liệu đã tải.");
    }
    private String summary(Map<String,Object> record){List<String> values=new ArrayList<>();for(String key:new String[]{"email","status","accountStatus","orderStatus","paymentStatus","verificationStatus","createdAt","orderId","action"})if(!str(record,key).isEmpty())values.add(label(key)+": "+str(record,key));return android.text.TextUtils.join("\n",values);}
    private void loadDetail() {
        long token=generation;call("adminDetail",args("collection",collection(),"id",recordId())).addOnCompleteListener(result->{
            if(!valid(token))return;if(!result.isSuccessful()){error(result.getException(),this::reload);return;}
            content.removeAllViews();Map<String,Object> data=result.getResult(),record=map(data.get("record")); title(content,label(collection())+" · "+recordId());
            showFields(card(content),record); evidence(record,token); actions(record);
            for(String key:new String[]{"order","timeline","shipment","money"})if(data.get(key)!=null){title(content,label(key));showValue(card(content),data.get(key));}
            button(content,"Tải lại chi tiết",this::reload);
            restoreDialog();
        });
    }
    private void showFields(LinearLayout parent,Map<String,Object> record){for(String key:new TreeSet<>(record.keySet())){if(key.equals("evidencePaths"))continue;Object value=record.get(key);text(parent,label(key)+": "+format(value));}}
    private String format(Object value){if(value==null)return "Chưa có";if(value instanceof Map){StringBuilder out=new StringBuilder();for(Map.Entry<String,Object> entry:map(value).entrySet())out.append("\n").append(label(entry.getKey())).append(": ").append(format(entry.getValue()));return out.toString();}if(value instanceof List){StringBuilder out=new StringBuilder();for(Object item:(List<?>)value)out.append("\n• ").append(format(item));return out.toString();}return String.valueOf(value);}
    private void showValue(LinearLayout parent,Object value){if(value instanceof List){if(((List<?>)value).isEmpty())text(parent,"Chưa có dữ liệu.");for(Object item:(List<?>)value)showValue(parent,item);}else if(value instanceof Map)showFields(parent,map(value));else text(parent,format(value));}
    private void evidence(Map<String,Object> record,long token){Object paths=record.get("evidencePaths");if(!(paths instanceof List))return;title(content,"Bằng chứng riêng tư");for(Object raw:(List<?>)paths){String path=String.valueOf(raw);LinearLayout panel=card(content);text(panel,path);button(panel,"Xem ảnh bằng chứng",()->{
        call("adminEvidence",args("collection",collection(),"id",recordId(),"path",path)).addOnCompleteListener(result->{if(!valid(token))return;if(!result.isSuccessful()){error(result.getException(),this::reload);return;}byte[] bytes=android.util.Base64.decode(str(result.getResult(),"base64"),android.util.Base64.DEFAULT);ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));panel.addView(image,new LinearLayout.LayoutParams(-1,dp(220)));});
    });}}
    private void actions(Map<String,Object> record){String id=recordId(),name=collection(),status=str(record,"status");
        if(name.equals("users")&&!"ADMIN".equals(str(record,"role"))){boolean locked="LOCKED".equals(str(record,"accountStatus")); decision(locked?"Mở khóa tài khoản":"Khóa tài khoản","userLock",args("locked",!locked));decision("Gửi cảnh báo","userWarning",args());}
        if(name.equals("posts")){decision("Duyệt / hiển thị bài","postStatus",args("status","ACTIVE"));decision("Ẩn bài","postStatus",args("status","HIDDEN"));decision("Gỡ bài","postStatus",args("status","REMOVED"));}
        if(name.equals("reports")&&Arrays.asList("PENDING","REVIEWING").contains(status)){decision("Đang xem xét","report",args("status","REVIEWING"));decision("Đóng báo cáo — xác nhận vi phạm","report",args("status","RESOLVED"));decision("Từ chối báo cáo","report",args("status","REJECTED"));text(content,"Khóa tài khoản hoặc ẩn bài là thao tác riêng, có nhật ký riêng.");String target=str(record,"targetType");String targetCollection=target.equals("USER")?"users":target.equals("ORDER")?"orders":"posts";button(content,"Mở đối tượng bị báo cáo",()->AdminDetailActivity.open(this,targetCollection,str(record,"targetId")));}
        if(name.equals("disputes")&&Arrays.asList("OPEN","WAITING_EVIDENCE").contains(status)){decision("Yêu cầu bổ sung bằng chứng","disputeEvidence",args());decision("Giải quyết — hoàn tiền người mua (mô phỏng)","disputeResolve",args("resolution","REFUND"));decision("Giải quyết — giải ngân người bán (mô phỏng)","disputeResolve",args("resolution","RELEASE"));}
        if(name.equals("verificationRequests")&&status.equals("PENDING")){decision("Duyệt xác minh thủ công","verification",args("approve",true));decision("Từ chối xác minh","verification",args("approve",false));text(content,"Duyệt hồ sơ thủ công; không xác thực danh tính qua VNeID.");}
        if(name.equals("categories"))decision(Boolean.FALSE.equals(record.get("enabled"))?"Bật danh mục":"Tắt danh mục","category",args("name",str(record,"name"),"enabled",Boolean.FALSE.equals(record.get("enabled"))));
    }
    private void decision(String title,String action,Map<String,Object> value){String prompt=title+" — nhập lý do / ghi chú";button(content,title,()->ask(prompt,note->mutate(action,recordId(),value,note))).setTag("dialog:"+prompt);}
    private interface TextResult{void accept(String text);}
    private void restoreDialog(){if(dialogTitle.isEmpty())return;View trigger=content.findViewWithTag("dialog:"+dialogTitle);if(trigger!=null)trigger.performClick();}
    private void ask(String title,TextResult accepted){
        EditText input=new EditText(this);input.setMinLines(2);if(title.equals(dialogTitle))input.setText(dialogText);
        dialogTitle=title;dialogInput=input;
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Hủy",(d,w)->{dialogTitle="";dialogText="";}).setPositiveButton("Xác nhận",null).create();
        activeDialog=dialog;dialog.setOnCancelListener(d->{dialogTitle="";dialogText="";});
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String value=input.getText().toString().trim();if(value.length()<3){input.setError("Nhập ít nhất 3 ký tự");return;}dialogTitle="";dialogText="";dialog.dismiss();accepted.accept(value);}));dialog.show();
    }
    private void mutate(String action,String id,Map<String,Object> value,String note){
        String fingerprint=action+id+new TreeMap<>(value)+note;if(!fingerprint.equals(pendingFingerprint)){pendingFingerprint=fingerprint;pendingRequestId=UUID.randomUUID().toString();}
        busy=true;enable(content,false);long token=generation;Map<String,Object> payload=args("action",action,"id",id,"value",value,"note",note,"requestId",pendingRequestId);
        call("adminMutate",payload).addOnCompleteListener(result->{if(!valid(token))return;busy=false;enable(content,true);if(!result.isSuccessful()){error(result.getException(),()->mutate(action,id,value,note));return;}pendingFingerprint="";pendingRequestId="";if(action.equals("settings")){settingsDraft=null;settingsDirty=false;}Toast.makeText(this,"Đã lưu trên backend và ghi nhật ký.",Toast.LENGTH_LONG).show();reload();});
    }
    private void loadSettings(){long token=generation;call("adminSettings",args()).addOnCompleteListener(result->{if(!valid(token))return;if(!result.isSuccessful()){error(result.getException(),this::reload);return;}content.removeAllViews();title(content,"Cấu hình hệ thống");Map<String,Object> data=result.getResult();Map<String,EditText> inputs=new HashMap<>();
        if(settingsDraft==null)settingsRevision=((Number)data.get("revision")).longValue();
        for(String key:new String[]{"shippingFee","maxPrice","reportThreshold","autoCompleteDays"}){text(content,label(key));EditText input=new EditText(this);input.setTag("draft:"+key);input.setInputType(2);input.setText(str(data,key));content.addView(input);inputs.put(key,input);}
        CheckBox phone=new CheckBox(this);phone.setText("Yêu cầu xác minh SĐT trước khi đăng bán");phone.setChecked(Boolean.TRUE.equals(data.get("requirePhoneBeforeSelling")));content.addView(phone);
        CheckBox hide=new CheckBox(this);hide.setText("Tự động ẩn bài theo ngưỡng báo cáo");hide.setChecked(Boolean.TRUE.equals(data.get("autoHideReported")));content.addView(hide);
        phone.setTag("draft:phone");hide.setTag("draft:hide");UiFormState.restore(content,settingsDraft);settingsRendered=true;
        for(EditText input:inputs.values())input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){settingsDirty=true;}public void afterTextChanged(Editable s){}});
        phone.setOnCheckedChangeListener((b,checked)->settingsDirty=true);hide.setOnCheckedChangeListener((b,checked)->settingsDirty=true);
        text(content,"Phí giao áp dụng cho báo giá đơn mới. Các tùy chọn chính sách được lưu trên backend; tác vụ tự động cần chạy bằng scheduler/trigger tương ứng.");
        button(content,"Lưu cấu hình",()->{Map<String,Object> value=new HashMap<>();for(String key:inputs.keySet()){try{value.put(key,Long.parseLong(inputs.get(key).getText().toString().trim()));}catch(NumberFormatException e){inputs.get(key).setError("Nhập số nguyên hợp lệ");return;}}value.put("requirePhoneBeforeSelling",phone.isChecked());value.put("autoHideReported",hide.isChecked());value.put("revision",settingsRevision);mutate("settings","active",value,"Cập nhật cấu hình hệ thống");});button(content,"Tải lại",()->{settingsDraft=null;settingsDirty=false;reload();});
    });}
    static String label(String key){switch(key){case "users":return "Người dùng";case "activeUsers":return "Tài khoản hoạt động";case "posts":return "Bài đăng";case "activePosts":return "Bài đang hiển thị";case "orders":return "Đơn hàng";case "reports":return "Báo cáo";case "disputes":return "Khiếu nại";case "verificationRequests":case "verifications":return "Yêu cầu xác minh";case "moneyTransactions":case "money":return "Bút toán giao dịch";case "categories":return "Danh mục";case "adminAudit":return "Nhật ký quản trị";case "timeline":return "Lịch sử sự kiện đơn";case "shipment":return "Vận đơn và lịch sử vận chuyển";case "order":return "Đơn hàng liên quan";case "shippingFee":return "Phí giao hàng (0–1.000.000₫)";case "maxPrice":return "Giá bán tối đa (10.000–1.000.000.000₫)";case "reportThreshold":return "Ngưỡng báo cáo (1–20)";case "autoCompleteDays":return "Số ngày tự hoàn tất (1–14)";case "accountStatus":return "Trạng thái tài khoản";case "orderStatus":return "Trạng thái đơn";case "paymentStatus":return "Thanh toán";case "status":return "Trạng thái";case "createdAt":return "Ngày tạo";case "name":return "Tên";case "title":return "Tiêu đề";case "description":return "Mô tả";case "reason":return "Lý do";case "role":return "Quyền";case "phone":return "Số điện thoại";case "verificationStatus":return "Xác minh";default:return key;}}
}
