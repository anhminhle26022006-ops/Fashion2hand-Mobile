package vn.edu.ueh.thudinh.fashion2hand;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;
import java.util.*;

/** Owner request inbox: products -> applicants -> request. Mutations remain server-authoritative. */
final class FreeRequestFlow {
    private final AppCompatActivity activity;
    private final TransactionScreens.Host host;
    private final MemberTwoRepository repo = MemberTwoRepository.getInstance();
    private final Map<String, Map<String,Object>> profiles = new HashMap<>();
    private LinearLayout body;
    private String postId = "", filter = "Mới nhất";
    private long version;
    private int layout;
    private boolean busy;
    private android.app.AlertDialog confirmation;
    private final List<Button> actions = new ArrayList<>();

    FreeRequestFlow(AppCompatActivity activity, TransactionScreens.Host host) { this.activity=activity; this.host=host; }
    void invalidate() { version++; if(confirmation!=null) { confirmation.dismiss(); confirmation=null; } }
    void save(Bundle state) { state.putString("freeInboxPost",postId);state.putString("freeInboxFilter",filter); }
    void restore(Bundle state) {postId=state.getString("freeInboxPost","");filter=state.getString("freeInboxFilter","Mới nhất");}
    private boolean valid(long token) { return token==version && !activity.isDestroyed() && host.showing(layout); }
    private int dp(int n) {return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private String str(Map<String,Object> data,String key) {Object value=data.get(key);return value==null?"":String.valueOf(value);}
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) {return value instanceof Map?(Map<String,Object>)value:Collections.emptyMap();}
    private String title(Map<String,Object> data) {String value=str(map(data.get("postSnapshot")),"title");return value.isEmpty()?"Món đồ miễn phí":value;}
    private String condition(String value) {
        switch(value){case "NEW":return "Mới";case "LIKE_NEW":return "Như mới";case "GOOD":return "Tốt";case "FAIR":return "Đã qua sử dụng";default:return value.isEmpty()?"Món đồ tặng":value;}
    }
    private String amount(Object value) {return String.format(new Locale("vi","VN"),"%,d đ",value instanceof Number?((Number)value).longValue():0L);}
    private long time(DocumentSnapshot doc) {Timestamp value=doc.getTimestamp("createdAt");return value==null?0:value.toDate().getTime();}
    private String date(Object value) {return value instanceof Timestamp?new java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm",new Locale("vi","VN")).format(((Timestamp)value).toDate()):"Đang đồng bộ thời gian";}
    private GradientDrawable background(int color,int stroke,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout box(LinearLayout parent) {
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackground(background(0xFFFFFFFF,0xFFE1E6E1,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(card,p);return card;
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold) {
        TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);if(bold)view.setTypeface(null,Typeface.BOLD);
        view.setPadding(0,dp(3),0,dp(5));parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;
    }
    private TextView muted(LinearLayout parent,String value) {return text(parent,value,11,0xFF74867C,false);}
    private Button action(LinearLayout parent,String value,int id,int kind,Runnable run) {
        Button button=new androidx.appcompat.widget.AppCompatButton(activity);button.setText(value);button.setAllCaps(false);button.setTextSize(14);button.setTypeface(null,Typeface.BOLD);
        if(id!=0)button.setId(id);button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));button.setPadding(dp(12),dp(8),dp(12),dp(8));
        button.setBackground(background(kind==0?0xFF143F32:kind==1?0xFFFFFFFF:0xFFEDF4EF,kind==1?0xFFC8D3CB:0,14));
        button.setBackgroundTintList(null);button.setElevation(0);button.setStateListAnimator(null);button.setTextColor(kind==0?0xFFFFFFFF:0xFF173F34);button.setEnabled(!busy);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);parent.addView(button,p);
        button.setOnClickListener(v->{if(!busy)run.run();});actions.add(button);return button;
    }
    private void clear() {body.removeAllViews();actions.clear();}
    private void failure(String value,Runnable retry) {clear();muted(body,value);action(body,"Thử lại",0,2,retry);}
    void bind(int layout,LinearLayout body,boolean owner) {
        invalidate();this.layout=layout;this.body=body;busy=false;long token=version;
        ((TextView)activity.findViewById(R.id.txtTransactionTitle)).setText(layout==R.layout.activity_free_request_people?"Người yêu cầu nhận đồ":layout==R.layout.activity_request_status?"Chi tiết yêu cầu":"Yêu cầu nhận đồ");
        muted(body,"Đang tải yêu cầu…");
        if(layout==R.layout.activity_request_status) {detail(token);return;}
        host.listen((owner?repo.listenFreeRequestsAsOwner(host.uid(),(snapshot,error)->{
            if(!valid(token))return;
            if(error!=null){failure("Không tải được yêu cầu. Kiểm tra kết nối và thử lại.",()->host.open(layout));return;}
            if(snapshot==null)return;
            List<DocumentSnapshot> docs=new ArrayList<>(snapshot.getDocuments());
            if(layout==R.layout.activity_free_request_people) {docs.removeIf(doc->!postId.equals(doc.getString("postId")));people(docs,token);}
            else products(docs,token,true);
        }):repo.listenFreeRequestsAsRequester(host.uid(),(snapshot,error)->{
            if(!valid(token))return;
            if(error!=null){failure("Không tải được yêu cầu đã gửi.",()->host.open(layout));return;}
            if(snapshot!=null)products(snapshot.getDocuments(),token,false);
        })));
    }
    private LinearLayout product(LinearLayout parent,Map<String,Object> data,String id,long token) {
        LinearLayout card=box(parent);card.setTag("free-product|"+id);
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);
        com.google.android.material.imageview.ShapeableImageView photo=new com.google.android.material.imageview.ShapeableImageView(activity);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setImageResource(R.drawable.img_product_placeholder);photo.setTag(id);
        photo.setShapeAppearanceModel(photo.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(10)).build());row.addView(photo,new LinearLayout.LayoutParams(dp(66),dp(80)));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(12);row.addView(words,p);
        text(words,"FREE ITEM",9,0xFF256756,true);text(words,title(data),14,0xFF173B32,true);
        Map<String,Object> snap=map(data.get("postSnapshot"));String condition=condition(str(snap,"condition")), region=str(snap,"location");
        TextView meta=muted(words,(condition.isEmpty()?"Món đồ tặng":condition)+(region.isEmpty()?"":" · "+region));
        text(words,"0đ · Miễn phí",14,0xFFEF765A,true);
        repo.getPost(id).addOnSuccessListener(post->{if(!valid(token)||!photo.isAttachedToWindow()||!post.exists())return;host.photo(post.getData(),photo);
            String place=str(post.getData(),"location");String conditionText=condition(str(post.getData(),"condition"));meta.setText(conditionText+(place.isEmpty()?"":" · "+place));
        });
        return card;
    }
    private void products(List<DocumentSnapshot> docs,long token,boolean owner) {
        clear();if(docs.isEmpty()){muted(body,owner?"Chưa có món đồ nào được yêu cầu nhận.":"Bạn chưa gửi yêu cầu nhận đồ.");return;}
        if(!owner){for(DocumentSnapshot doc:docs){LinearLayout card=product(body,doc.getData(),doc.getString("postId"),token);muted(card,TransactionScreens.label(doc.getString("status")));card.setOnClickListener(v->host.openRequest(doc.getId()));}return;}
        LinkedHashMap<String,List<DocumentSnapshot>> groups=new LinkedHashMap<>();
        for(DocumentSnapshot doc:docs)groups.computeIfAbsent(doc.getString("postId"),id->new ArrayList<>()).add(doc);
        text(body,groups.size()+" món đồ có người yêu cầu",14,0xFF173F34,true);
        for(Map.Entry<String,List<DocumentSnapshot>> entry:groups.entrySet()){
            List<DocumentSnapshot> requests=entry.getValue();LinearLayout card=product(body,requests.get(0).getData(),entry.getKey(),token);
            long pending=requests.stream().filter(d->"PENDING".equals(d.getString("status"))).count();
            muted(card,requests.size()+" người đã yêu cầu · "+pending+" đang chờ");
            if(requests.stream().anyMatch(d->"ACCEPTED".equals(d.getString("status"))))text(card,"✓ Người nhận đã được chọn",12,0xFF24634D,true);
            card.setContentDescription(title(requests.get(0).getData())+" · "+requests.size()+" người yêu cầu");
            card.setOnClickListener(v->{postId=entry.getKey();filter="Mới nhất";host.open(R.layout.activity_free_request_people);});
        }
    }
    private void people(List<DocumentSnapshot> docs,long token) {
        clear();if(docs.isEmpty()){muted(body,"Không có yêu cầu cho món đồ này.");return;}
        product(body,docs.get(0).getData(),postId,token);
        DocumentSnapshot chosen=docs.stream().filter(d->"ACCEPTED".equals(d.getString("status"))).findFirst().orElse(null);
        LinearLayout summary=box(body);text(summary,"FREE · 0đ",10,0xFF286453,true);
        text(summary,chosen==null?docs.size()+" người đã yêu cầu món đồ này":"Người nhận đã được chọn",20,0xFF173B32,false);
        TextView caption=muted(summary,chosen==null?"Hãy xem xét và chọn một người phù hợp để nhận món đồ.":"Món đồ đã được giữ cho người nhận. Các yêu cầu khác đã đóng.");
        if(chosen!=null)profile(chosen.getString("requesterId"),token,data->caption.setText("Món đồ đang được giữ cho "+name(data)+". Các yêu cầu khác đã đóng."));
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);body.addView(scroll);
        LinearLayout chips=new LinearLayout(activity);chips.setPadding(0,0,0,dp(12));scroll.addView(chips);
        for(String option:new String[]{"Mới nhất","Sớm nhất","Có lời nhắn"}){
            TextView chip=new TextView(activity);chip.setText(option);chip.setTextSize(11);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(14),dp(10),dp(14),dp(10));
            chip.setTextColor(filter.equals(option)?0xFFFFFFFF:0xFF315447);chip.setBackground(background(filter.equals(option)?0xFF143F32:0xFFFFFFFF,0xFFE0E6E0,24));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.rightMargin=dp(6);chips.addView(chip,p);chip.setOnClickListener(v->{filter=option;people(docs,token);});
        }
        List<DocumentSnapshot> sorted=new ArrayList<>(docs);sorted.sort((left,right)->Long.compare(time(right),time(left))*(filter.equals("Sớm nhất")?-1:1));
        if(chosen!=null){sorted.remove(chosen);sorted.add(0,chosen);}
        int count=0;
        for(DocumentSnapshot doc:sorted){
            Map<String,Object> data=doc.getData();if(filter.equals("Có lời nhắn")&&str(data,"note").trim().isEmpty())continue;count++;
            LinearLayout card=box(body);card.setTag("request|"+doc.getId());
            person(card,doc.getString("requesterId"),token,false);
            muted(card,date(data.get("createdAt")));text(card,str(data,"note").isEmpty()?"Không có lời nhắn.":str(data,"note"),13,0xFF365B4C,false);
            String status=str(data,"status");boolean closed=Arrays.asList("UNAVAILABLE","REJECTED","CANCELLED").contains(status);
            if(!status.equals("PENDING"))text(card,status.equals("UNAVAILABLE")?"KHÔNG ĐƯỢC CHỌN":TransactionScreens.label(status).toUpperCase(new Locale("vi","VN")),10,0xFF456D5B,true);
            if(closed){card.setAlpha(.55f);muted(card,"Đã đóng");}
            else {
                Button viewRequest=action(card,"Xem yêu cầu",0,2,()->host.openRequest(doc.getId()));
                viewRequest.setTextSize(11);viewRequest.setMinHeight(dp(34));viewRequest.setMinimumHeight(dp(34));viewRequest.setPadding(dp(12),0,dp(12),0);
                viewRequest.setLayoutParams(new LinearLayout.LayoutParams(dp(112),dp(36)));
            }
        }
        if(count==0)muted(body,"Không có yêu cầu phù hợp bộ lọc.");
        if(chosen!=null&&!str(chosen.getData(),"orderId").isEmpty())orderSummary(str(chosen.getData(),"orderId"),token,body);
    }
    private String name(Map<String,Object> profile){String value=str(profile,"name");return value.isEmpty()?"Thành viên":value;}
    private void profile(String uid,long token,java.util.function.Consumer<Map<String,Object>> render){
        if(uid==null||uid.isEmpty())return;
        if(profiles.containsKey(uid)){render.accept(profiles.get(uid));return;}
        FirebaseFunctions.getInstance().getHttpsCallable("getSellerProfile").call(Collections.singletonMap("sellerId",uid))
            .addOnSuccessListener(result->{if(!valid(token))return;Map<String,Object> data=map(result.getData());profiles.put(uid,data);render.accept(data);});
    }
    private void person(LinearLayout parent,String uid,long token,boolean large){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row);
        com.google.android.material.imageview.ShapeableImageView avatar=new com.google.android.material.imageview.ShapeableImageView(activity);
        avatar.setImageResource(R.drawable.img_avatar_placeholder);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setTag(uid);
        avatar.setShapeAppearanceModel(avatar.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(40)).build());row.addView(avatar,new LinearLayout.LayoutParams(dp(large?64:44),dp(large?64:44)));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(12);row.addView(words,p);
        TextView name=text(words,"Thành viên",large?19:14,0xFF173B32,!large);TextView region=muted(words,"Hồ sơ thành viên");
        profile(uid,token,data->{if(!name.isAttachedToWindow())return;name.setText(name(data));region.setText(str(data,"region").isEmpty()?"Hồ sơ thành viên":str(data,"region"));
            String photo=str(data,"photoUrl");if(!photo.isEmpty())host.photo(Collections.singletonMap("imageUrls",Collections.singletonList(photo)),avatar);
        });
    }
    private void detail(long token){
        final String requestId=host.requestId();
        host.listen(repo.listenFreeRequest(requestId,(doc,error)->{
            if(!valid(token))return;
            if(error!=null){failure("Không tải được chi tiết yêu cầu.",()->host.open(layout));return;}
            clear();if(doc==null||!doc.exists()){muted(body,"Yêu cầu không còn tồn tại.");return;}
            Map<String,Object> data=doc.getData();boolean owner=host.uid().equals(str(data,"ownerId"));String peer=str(data,owner?"requesterId":"ownerId");
            person(box(body),peer,token,true);
            LinearLayout note=box(body);text(note,"Lời nhắn yêu cầu",14,0xFF173B32,false);text(note,str(data,"note").isEmpty()?"Không có lời nhắn.":"“"+str(data,"note")+"”",14,0xFF173B32,false);muted(note,"Đã gửi "+date(data.get("createdAt")));
            LinearLayout item=product(body,data,str(data,"postId"),token);
            TextView itemHeading=text(item,"Món đồ miễn phí",14,0xFF173B32,false);item.removeView(itemHeading);item.addView(itemHeading,0);
            LinearLayout delivery=box(body);text(delivery,"Thông tin giao nhận",14,0xFF173B32,false);TextView fee=text(delivery,"Giao hàng tiêu chuẩn · Đang tải phí dự kiến…",13,0xFF173B32,true);
            muted(delivery,"Người nhận chi trả phí giao hàng nếu áp dụng.");
            if(data.get("shippingQuote") instanceof Map){Map<String,Object> savedQuote=map(data.get("shippingQuote"));fee.setText("GHN · "+str(savedQuote,"environment")+" · phí đã xác nhận "+amount(savedQuote.get("shippingFee")));}
            else repo.capabilities().addOnSuccessListener(result->{if(valid(token)&&fee.isAttachedToWindow()){Map<String,Object> c=map(result.getData());fee.setText(c.get("shippingFee") instanceof Number?"Giao hàng tiêu chuẩn · dự kiến "+amount(c.get("shippingFee")):"Phí giao hàng sẽ hiển thị trong đơn được tạo.");}})
                .addOnFailureListener(e->{if(valid(token)&&fee.isAttachedToWindow())fee.setText("Chưa tải được phí giao hàng dự kiến.");});
            String status=str(data,"status");
            if(status.equals("PENDING")){
                if(owner){action(body,"Chấp nhận yêu cầu",R.id.btnAcceptFreeRequest,0,()->confirm(doc,true,token));action(body,"Từ chối yêu cầu",R.id.btnRejectFreeRequest,1,()->confirm(doc,false,token));}
                else action(body,"Hủy yêu cầu",R.id.btnCancelFreeRequest,1,()->{
                    new android.app.AlertDialog.Builder(activity).setTitle("Hủy yêu cầu nhận đồ?").setNegativeButton("Quay lại",null).setPositiveButton("Hủy yêu cầu",(d,w)->mutate(doc,false,true,token)).show();
                });
            }else{
                LinearLayout statusCard=box(body);statusCard.setBackground(background(0xFFEDF5EF,0xFFD6E8DC,14));
                text(statusCard,status.equals("ACCEPTED")?"✓  Người nhận đã được chọn":status.equals("REJECTED")?"Yêu cầu đã bị từ chối":status.equals("UNAVAILABLE")?"Yêu cầu đã đóng · Không được chọn":TransactionScreens.label(status),14,0xFF21533E,true);
                TextView description=muted(statusCard,status.equals("ACCEPTED")?"Món đồ đã được giữ cho người nhận.":"Yêu cầu này không còn chờ xử lý.");
                if(status.equals("ACCEPTED"))profile(str(data,"requesterId"),token,p->{if(description.isAttachedToWindow())description.setText("Món đồ đang được giữ cho "+name(p)+".");});
            }
            if(!owner&&status.equals("PENDING")&&str(data,"shippingProvider").equals("GHN"))action(body,"Cập nhật địa chỉ / xác nhận lại phí GHN",0,2,()->host.requestFree(str(data,"postId"),str(data,"note")));
            action(body,"Trò chuyện",0,2,()->host.chat(peer,str(data,"postId")));
            if(!str(data,"orderId").isEmpty())action(body,"Xem đơn miễn phí",0,1,()->host.openOrder(str(data,"orderId")));
        }));
    }
    private void confirm(DocumentSnapshot doc,boolean accept,long token){
        Map<String,Object> cached=profiles.getOrDefault(doc.getString("requesterId"),Collections.emptyMap());
        confirmation=new android.app.AlertDialog.Builder(activity)
            .setTitle(accept?"Trao món đồ này cho "+name(cached)+"?":"Từ chối yêu cầu của "+name(cached)+"?")
            .setMessage(accept?"Bạn chỉ có thể chọn một người nhận. Món đồ sẽ được giữ cho người này và các yêu cầu khác sẽ được đóng.":"Yêu cầu này sẽ được đóng. Bạn vẫn có thể chọn một người khác nhận món đồ.")
            .setNegativeButton("Hủy",null).setPositiveButton(accept?"Xác nhận người nhận":"Xác nhận từ chối",(d,w)->mutate(doc,accept,false,token)).create();
        confirmation.show();
        if(confirmation.getWindow()!=null)confirmation.getWindow().setBackgroundDrawable(background(0xFFFFFFFF,0,24));
    }
    private void mutate(DocumentSnapshot doc,boolean accept,boolean cancel,long token){
        if(busy||!valid(token))return;busy=true;for(Button b:actions)b.setEnabled(false);
        (cancel?repo.cancelFreeRequest(doc.getId()):repo.respondFreeRequest(doc.getId(),accept)).addOnCompleteListener(result->{
            if(!valid(token))return;busy=false;for(Button b:actions)b.setEnabled(true);
            if(!result.isSuccessful()){host.error(result.getException());return;}
            // The live request listener replaces the action buttons with the confirmed server state.
        });
    }
    private void orderSummary(String id,long token,LinearLayout parent){
        LinearLayout summary=box(parent);summary.setTag("free-order|"+id);text(summary,"Đơn miễn phí đã được tạo",14,0xFF173B32,true);
        TextView state=muted(summary,"Đang tải đơn…");LinearLayout totals=new LinearLayout(activity);totals.setOrientation(LinearLayout.VERTICAL);summary.addView(totals);
        // One-shot reads avoid accumulating listeners each time a filter or snapshot redraws the list.
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("orders").document(id).get().addOnSuccessListener(order->{
            if(!valid(token)||!summary.isAttachedToWindow())return;if(!order.exists()){state.setText("Không tìm thấy đơn.");return;}
            state.setText("ĐƠN HÀNG: "+TransactionScreens.label(order.getString("orderStatus"))+"\nTHANH TOÁN: Chỉ phí giao hàng\nVẬN CHUYỂN: "+TransactionScreens.label(order.getString("shippingStatus")));
            text(totals,"Giá sản phẩm                         0đ — FREE",12,0xFF365B4C,false);
            text(totals,"Phí vận chuyển                  "+amount(order.get("shippingFee")),12,0xFF365B4C,false);
            text(totals,"Tổng thanh toán                "+amount(order.get("shippingFee")),17,0xFFEF765A,true);
            muted(totals,"Không có phí cho sản phẩm. Tổng tiền chỉ bao gồm giao hàng.");
        }).addOnFailureListener(e->{if(valid(token)&&state.isAttachedToWindow())state.setText("Chưa tải được đơn. Mở chi tiết để thử lại.");});
        action(summary,"Xem đơn hàng",0,2,()->host.openOrder(id));
    }
}
