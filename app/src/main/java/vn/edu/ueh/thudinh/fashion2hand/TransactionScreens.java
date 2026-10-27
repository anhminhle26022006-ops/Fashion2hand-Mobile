package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Live transaction UI. Every business transition is enforced by a callable on the server. */
final class TransactionScreens {
    interface Host {
        String uid(); String postId(); String orderId(); String requestId();
        boolean showing(int layout); boolean signedIn(); boolean checkoutFromCart();
        void open(int layout); void openOrder(String id); void openRequest(String id);
        void checkout(String postId); void orders(boolean seller); void back();
        void requestFree(String postId, String note);
        void checkoutCart(java.util.List<String> postIds);
        void orderSnapshot(DocumentSnapshot order); void listen(ListenerRegistration listener);
        void error(Exception error); void chat(String peerId, String postId);
        void photo(Map<String, Object> post, ImageView image);
    }
    private final AppCompatActivity a;
    private final Host host;
    private final FreeRequestFlow freeRequests;
    private ShipmentTrackingMap shipmentMap;
    private final Map<String,double[]> shipmentGeocodes = new LinkedHashMap<>();
    private final MemberTwoRepository repo = MemberTwoRepository.getInstance();
    private final vn.edu.ueh.thudinh.fashion2hand.service.PaymentService payments =
            new vn.edu.ueh.thudinh.fashion2hand.service.MockPaymentService(repo);
    private final vn.edu.ueh.thudinh.fashion2hand.service.ShippingService shippingService =
            new vn.edu.ueh.thudinh.fashion2hand.service.FirebaseShippingService(repo);
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ArrayList<Button> buttons = new ArrayList<>();
    private static final java.util.concurrent.Executor FILES = Executors.newFixedThreadPool(2);
    private LinearLayout content;
    private long generation;
    private boolean busy, ownerView;
    private int screen, historyFilter;
    private Map<String, Object> capabilities = new HashMap<>(), quote = new HashMap<>();
    private long quoteRequest;
    private boolean quoteLoading;
    private String quoteError = "", quotedAddressVersion = "";
    private String checkoutContext = "", checkoutKey = "", addressId = "", freeNote = "";
    private ArrayList<String> cartCheckoutIds = new ArrayList<>();
    void selectCartCheckout(java.util.List<String> ids) { cartCheckoutIds = new ArrayList<>(ids); Collections.sort(cartCheckoutIds); }
    void selectFreeRequestDraft(String postId,String note) { checkoutContext=host.uid()+":"+postId;checkoutKey=UUID.randomUUID().toString();freeNote=note;addressId="";quote.clear(); }
    private boolean groupedCheckout() { return host.postId().isEmpty() && !cartCheckoutIds.isEmpty(); }
    private final ArrayList<DocumentSnapshot> addresses = new ArrayList<>();
    private Map<String, Object> order = new HashMap<>(), payment = new HashMap<>(), shipment = new HashMap<>(), dispute = new HashMap<>();
    private List<DocumentSnapshot> timeline = new ArrayList<>(), ledger = new ArrayList<>();
    private String boundPayment = "", boundDispute = "";
    private boolean shipmentBound;
    private final Map<String, DocumentSnapshot> historyBuyer = new HashMap<>(), historySeller = new HashMap<>();
    private final Map<String, List<DocumentSnapshot>> historyMoney = new HashMap<>();
    private final Map<String, ListenerRegistration> moneyListeners = new HashMap<>();
    private vn.edu.ueh.thudinh.fashion2hand.adapter.TransactionHistoryAdapter historyAdapter;
    private final Map<String, Map<String, Object>> historyPhotos = new HashMap<>();
    private boolean historyBuyerLoaded, historySellerLoaded;
    private String historyError = "";
    private String disputeOrder = "", uploadKey = "";
    private ArrayList<String> uploadedEvidence = new ArrayList<>();
    private String dialogType = "", newAddressKey = "";
    private Bundle dialogDraft;
    private android.app.AlertDialog activeDialog;
    private View dialogForm;

    private void trackDialog(String type, android.app.AlertDialog dialog, View form) {
        if(type.equals(dialogType)) UiFormState.restore(form,dialogDraft);
        dialogType=type;activeDialog=dialog;dialogForm=form;
        dialog.setOnDismissListener(d->{dialogType="";dialogDraft=null;activeDialog=null;dialogForm=null;});
    }
    private void restoreDialog() {
        if(activeDialog!=null && activeDialog.isShowing())return;
        if(dialogType.equals("address") && (screen==R.layout.activity_checkout || screen==R.layout.activity_requestfree))newAddress();
        else if(dialogType.equals("location") && screen==R.layout.activity_order_detail)locationForm();
        else if(dialogType.equals("reject") && screen==R.layout.activity_order_detail)rejectOrder();
    }

    TransactionScreens(AppCompatActivity activity, Host host) { this.a = activity; this.host = host; this.freeRequests = new FreeRequestFlow(activity, host); }

    boolean owns(int layout) {
        return layout == R.layout.activity_free_requests || layout == R.layout.activity_free_request_people || layout == R.layout.activity_transactions || layout == R.layout.activity_request_status
                || layout == R.layout.activity_requestfree || layout == R.layout.activity_cart
                || layout == R.layout.activity_checkout || layout == R.layout.activity_order_detail
                || layout == R.layout.activity_delivery || layout == R.layout.activity_delivery_map
                || layout == R.layout.activity_transaction_history || layout == R.layout.activity_cancel_order;
    }
    void invalidate() { generation++; busy = false; freeRequests.invalidate(); closeShipmentMap(); }
    private void closeShipmentMap() { if (shipmentMap != null) { shipmentMap.close(); shipmentMap = null; } }
    private boolean valid(long token) { return !a.isDestroyed() && token == generation; }
    private int dp(int value) { return (int) (value * a.getResources().getDisplayMetrics().density + .5f); }
    private static String text(Map<String, Object> data, String key) { Object value = data.get(key); return value == null ? "" : String.valueOf(value); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object data) { return data instanceof Map ? (Map<String, Object>) data : new HashMap<>(); }
    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    private static String money(Object value) { return String.format(new Locale("vi", "VN"), "%,d đ", number(value)); }
    private static String title(Map<String, Object> data) { String title = text(map(data.get("postSnapshot")), "title"); return title.isEmpty() ? text(data, "postId") : title; }
    private static String date(Object value) {
        Date date = value instanceof Timestamp ? ((Timestamp) value).toDate() : value instanceof Date ? (Date) value : null;
        if (date == null && value instanceof String) try { date = Date.from(java.time.Instant.parse((String) value)); } catch (Exception ignored) { }
        return date == null ? "Chưa có thời gian" : new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN")).format(date);
    }
    static String label(String status) {
        switch (status) {
            case "PENDING": return "Đang chờ"; case "ACCEPTED": return "Đã chấp nhận";
            case "REJECTED": return "Đã từ chối"; case "UNAVAILABLE": return "Món đồ không còn khả dụng";
            case "PENDING_PAYMENT": return "Chờ thanh toán"; case "CONFIRMED": return "Chờ người bán xác nhận";
            case "PREPARING": return "Đang chuẩn bị"; case "READY_TO_SHIP": return "Sẵn sàng giao";
            case "SHIPPING": case "IN_TRANSIT": return "Đang giao"; case "DELIVERED": return "Đã giao";
            case "COMPLETED": return "Hoàn tất"; case "CANCELLED": return "Đã hủy";
            case "PAYMENT_FAILED": case "FAILED": return "Thanh toán thất bại";
            case "PAID": return "Đã thanh toán"; case "REFUNDED": return "Đã hoàn tiền";
            case "RETURNED": return "Đã hoàn hàng"; case "EXPIRED": return "Đã hết hạn";
            case "NOT_REQUIRED": return "Không thanh toán tiền món đồ"; case "READY": return "Chưa tạo vận đơn";
            case "PICKING": return "GHN đang lấy hàng"; case "DELIVERY_FAILED": return "Giao chưa thành công"; case "RETURNING": return "Đang hoàn hàng"; case "RETURN_FAILED": return "Hoàn hàng chưa thành công"; case "LOST": return "Thất lạc — cần đối soát"; case "DAMAGED": return "Hư hỏng — cần đối soát"; case "EXCEPTION": return "Sự cố — cần đối soát"; case "SCRAPPED": return "Đã tiêu hủy — cần đối soát";
            case "OPEN": return "Tranh chấp đang chờ xử lý"; case "HOLDING": return "Đang giữ tiền";
            case "RELEASED": return "Đã giải ngân"; default: return status.isEmpty() ? "Chưa có dữ liệu" : status;
        }
    }

    void prepare(int layout) {
        screen = layout; generation++; busy = false; buttons.clear();
        if (layout == R.layout.activity_transactions || layout == R.layout.activity_transaction_history || layout == R.layout.activity_free_requests || layout == R.layout.activity_free_request_people || layout == R.layout.activity_request_status) {
            a.setContentView(layout == R.layout.activity_request_status ? R.layout.activity_free_request_detail : layout);
            content = a.findViewById(R.id.transactionContent);
            a.findViewById(R.id.btnTransactionBack).setOnClickListener(view -> host.back());
            View history = a.findViewById(R.id.btnTransactionHistory);
            if (layout == R.layout.activity_transactions) history.setOnClickListener(view -> host.open(R.layout.activity_transaction_history));
            else history.setVisibility(View.INVISIBLE);
            return;
        }
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF8F6F0);
        LinearLayout header = new LinearLayout(a); header.setGravity(android.view.Gravity.CENTER_VERTICAL); header.setPadding(dp(12), 0, dp(12), 0);
        Button back = new Button(a); back.setId(R.id.btnTransactionBack); back.setText("‹"); back.setContentDescription("Quay lại"); back.setOnClickListener(v -> host.back());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = new TextView(a); title.setId(R.id.txtTransactionTitle); title.setTextColor(0xFF173B32); title.setTextSize(19); title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        ScrollView scroll = new ScrollView(a); scroll.setId(R.id.transactionScroll); scroll.setFillViewport(true);
        content = new LinearLayout(a); content.setId(R.id.transactionContent); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); a.setContentView(root);
    }
    private void heading(String value) { ((TextView) a.findViewById(R.id.txtTransactionTitle)).setText(value); }
    private void reset() { closeShipmentMap(); content.removeAllViews(); buttons.clear(); }
    private TextView info(LinearLayout parent, String value) {
        TextView view = new TextView(a); view.setText(value); view.setTextSize(14); view.setTextColor(0xFF52645A); view.setPadding(0, dp(6), 0, dp(6)); parent.addView(view); return view;
    }
    private void section(LinearLayout parent, String title) { TextView view = info(parent, title); view.setTextSize(18); view.setTextColor(0xFF173B32); view.setTypeface(null, 1); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(a); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(12)); card.setBackgroundResource(R.drawable.bg_white_card_rounded);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12); parent.addView(card, params); return card;
    }
    private Button button(LinearLayout parent, int id, String title, Runnable action) {
        Button button = new Button(a); if (id != 0) button.setId(id); button.setText(title); button.setTextSize(13); button.setAllCaps(false);
        button.setMinHeight(dp(48)); button.setBackgroundTintList(ColorStateList.valueOf(0xFF104536)); button.setTextColor(0xFFFFFFFF);
        button.setEnabled(!busy); button.setOnClickListener(v -> { if (!busy) action.run(); }); parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); buttons.add(button); return button;
    }
    private EditText input(LinearLayout parent, int id, String hint, String value) {
        EditText input = new EditText(a); if (id != 0) input.setId(id); input.setHint(hint); input.setText(value); input.setTextSize(15);
        input.setMinHeight(dp(48)); parent.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    private void setBusy(boolean value) { busy = value; for (Button button : buttons) button.setEnabled(!value);
        if(screen==R.layout.activity_checkout||screen==R.layout.activity_requestfree){View submit=a.findViewById(screen==R.layout.activity_checkout?R.id.btnPlaceOrder:R.id.btnSubmitFreeRequest);if(submit!=null)submit.setEnabled(!value&&!quoteLoading&&!quote.isEmpty()&&!addressId.isEmpty());}
    }
    private void error(Exception error) { host.error(error); }
    private void failure(String message, Runnable retry) { reset(); info(content, message); button(content, 0, "Thử lại", retry); }
    private void execute(Task<?> task, Consumer<Object> success) {
        long token = generation; setBusy(true);
        task.addOnCompleteListener(result -> {
            if (!valid(token)) return;
            setBusy(false);
            if (!result.isSuccessful()) { error(result.getException()); return; }
            Object value = result.getResult(); success.accept(value instanceof HttpsCallableResult ? ((HttpsCallableResult) value).getData() : value);
        });
    }
    private void capabilities(Runnable render) {
        long token = generation;
        repo.capabilities().addOnSuccessListener(result -> { if (valid(token)) { capabilities = map(result.getData()); render.run(); } })
                .addOnFailureListener(error -> { if (valid(token)) error(error); });
    }

    void bind(int layout) {
        if (!host.signedIn()) return;
        if (layout == R.layout.activity_transactions) bindOverview();
        else if (layout == R.layout.activity_request_status || layout == R.layout.activity_free_requests || layout == R.layout.activity_free_request_people) freeRequests.bind(layout, content, ownerView);
        else if (layout == R.layout.activity_checkout || layout == R.layout.activity_requestfree) bindCheckout(layout == R.layout.activity_requestfree);
        else if (layout == R.layout.activity_cart) bindCart();
        else if (layout == R.layout.activity_order_detail) bindOrder();
        else if (layout == R.layout.activity_delivery || layout == R.layout.activity_delivery_map) bindShipment(layout == R.layout.activity_delivery_map);
        else if (layout == R.layout.activity_transaction_history) bindHistory();
        else if (layout == R.layout.activity_cancel_order) {
            heading("Hủy đơn hàng"); info(content, "Chỉ người mua được hủy trước khi người bán chuẩn bị. Đơn VNPAY sandbox đã thanh toán sẽ chờ đối soát hoàn tiền; chưa tự hoàn tiền. Đơn mô phỏng giữ luồng mô phỏng.");
            button(content, R.id.btnCancelOrder, "Xác nhận hủy đơn", () -> execute(repo.cancelOrder(host.orderId()), value -> host.back()));
        }
    }

    private void setLabel(int id, String value) { ((TextView) a.findViewById(id)).setText(value); }
    private void bindOverview() {
        heading("Trung tâm giao dịch"); long token = generation; final boolean owner = ownerView;
        capabilities(() -> { if(valid(token)&&owner&&"GHN".equals(text(capabilities,"shippingProvider"))){
            button(content,0,"Thiết lập địa chỉ lấy hàng GHN",this::selectGhnPickupAddress);
            TextView pickupStatus=info(content,"Đang tải trạng thái điểm lấy hàng GHN…");
            db.collection("users").document(host.uid()).collection("addresses").get(Source.SERVER).addOnCompleteListener(result->{
                if(!valid(token))return;
                if(!result.isSuccessful()){pickupStatus.setText("Chưa tải được trạng thái điểm lấy hàng. Bấm thiết lập để thử lại.");return;}
                List<String> savedLocations=new ArrayList<>();
                for(DocumentSnapshot address:result.getResult().getDocuments())if(address.get("ghn") instanceof Map){
                    Map<String,Object> display=map(address.get("ghnDisplay"));
                    savedLocations.add(display.isEmpty()?addressLabel(address):text(display,"ward")+", "+text(display,"district")+", "+text(display,"province"));
                }
                pickupStatus.setText(savedLocations.isEmpty()?"Chưa có địa chỉ được lưu mã GHN. Thiết lập điểm lấy hàng của người bán ở trên.":"Địa chỉ đã lưu mã GHN:\n"+android.text.TextUtils.join("\n",savedLocations)+"\nLiên kết shop do Admin cấu hình riêng; chưa tự tạo vận đơn.");
            });
        } });
        RadioGroup roles = a.findViewById(R.id.transactionRoleTabs);
        roles.check(owner ? R.id.tabGive : R.id.tabReceive);
        for (int id : new int[]{R.id.tabReceive, R.id.tabGive}) {
            TextView tab = a.findViewById(id); String value = tab.getText().toString(); int newline = value.indexOf('\n');
            android.text.SpannableString styled = new android.text.SpannableString(value);
            if (newline >= 0) {
                styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,newline,0);
                styled.setSpan(new android.text.style.AbsoluteSizeSpan(9,true),newline+1,value.length(),0);
            }
            tab.setText(styled);
        }
        roles.setOnCheckedChangeListener((group, id) -> { boolean selectedOwner = id == R.id.tabGive;
            if (selectedOwner != ownerView) { ownerView = selectedOwner; host.open(screen); }
        });
        setLabel(R.id.txtTransactionHeading, owner ? "Món đồ bạn đang tặng và bán" : "Món đồ bạn đang nhận và mua");
        setLabel(R.id.txtPurchaseSection, owner ? "Bán hàng" : "Mua hàng");
        ArrayList<DocumentSnapshot> requests = new ArrayList<>(), orders = new ArrayList<>();
        boolean[] loaded = {false,false}, cached = {true,true}; String[] errors = {"",""};
        LinearLayout requestList = a.findViewById(R.id.freeRequestList);
        TextView state = a.findViewById(R.id.transactionOverviewState);
        state.setOnClickListener(view -> { if (!errors[0].isEmpty() || !errors[1].isEmpty()) host.open(screen); });
        a.findViewById(R.id.transactionRequestRow).setOnClickListener(view -> {
            host.open(R.layout.activity_free_requests);
        });
        a.findViewById(R.id.transactionPurchaseRow).setOnClickListener(view -> host.orders(owner));
        a.findViewById(R.id.transactionIssueRow).setOnClickListener(view -> {
            ArrayList<DocumentSnapshot> issues = new ArrayList<>();
            for (DocumentSnapshot order : orders) if (Arrays.asList("OPEN","WAITING_EVIDENCE").contains(order.getString("disputeStatus"))) issues.add(order);
            if (issues.isEmpty()) { Toast.makeText(a,"Không có tranh chấp đang mở.",Toast.LENGTH_SHORT).show(); return; }
            if (issues.size() == 1) { host.openOrder(issues.get(0).getId()); return; }
            String[] titles = new String[issues.size()]; for (int i=0;i<titles.length;i++) titles[i]=title(issues.get(i).getData());
            new android.app.AlertDialog.Builder(a).setTitle("Đơn hàng cần chú ý").setItems(titles,(dialog,index)->host.openOrder(issues.get(index).getId())).show();
        });
        Runnable render = () -> {
            if (!valid(token)) return;
            String error = !errors[0].isEmpty() ? errors[0] : errors[1];
            String message = !error.isEmpty() ? error + " Nhấn để thử lại."
                    : !loaded[0] || !loaded[1] ? "Đang tải giao dịch…"
                    : cached[0] || cached[1] ? "Dữ liệu đã lưu · đang chờ đồng bộ" : "";
            state.setText(message); state.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
            long pending = requests.stream().filter(doc->"PENDING".equals(doc.getString("status"))).count();
            setLabel(R.id.statPendingRequests, loaded[0]?String.valueOf(pending):"—");
            setLabel(R.id.statAwaitingPayment, loaded[1]?String.valueOf(orders.stream().filter(doc->"PENDING_PAYMENT".equals(doc.getString("orderStatus"))).count()):"—");
            setLabel(R.id.statShippingOrders, loaded[1]?String.valueOf(orders.stream().filter(doc->"SHIPPING".equals(doc.getString("orderStatus"))).count()):"—");
            setLabel(R.id.statDeliveredOrders, loaded[1]?String.valueOf(orders.stream().filter(doc->Arrays.asList("DELIVERED","COMPLETED").contains(doc.getString("orderStatus"))).count()):"—");
            setLabel(R.id.txtRequestCount, String.valueOf(pending));
            DocumentSnapshot request = requests.stream().filter(doc->"PENDING".equals(doc.getString("status"))).findFirst().orElse(requests.isEmpty()?null:requests.get(0));
            setLabel(R.id.txtRequestPreview,request==null?"Chưa có yêu cầu nhận đồ":title(request.getData()));
            setLabel(R.id.txtRequestPreviewStatus,request==null?"Các yêu cầu sẽ xuất hiện tại đây":label(request.getString("status")) + " · " + requests.size() + " yêu cầu");
            buttons.clear(); requestList.removeAllViews();
            if (requests.isEmpty()) info(requestList,loaded[0]?"Chưa có yêu cầu nhận đồ.":"Đang tải yêu cầu…");

            setLabel(R.id.txtPurchaseCount,String.valueOf(orders.size()));
            DocumentSnapshot latest = orders.isEmpty()?null:orders.get(0);
            setLabel(R.id.txtPurchasePreview,latest==null?"Chưa có đơn hàng":title(latest.getData()));
            setLabel(R.id.txtPurchasePreviewStatus,latest==null?(owner?"Xem đơn bán và tặng đồ":"Xem đơn mua và nhận đồ"):label(latest.getString("orderStatus")));
            long issues = orders.stream().filter(doc->Arrays.asList("OPEN","WAITING_EVIDENCE").contains(doc.getString("disputeStatus"))).count();
            setLabel(R.id.txtIssueCount,String.valueOf(issues));
            setLabel(R.id.txtIssuePreview,issues==0?"Không có tranh chấp đang mở":"Tranh chấp đang xem xét");
            setLabel(R.id.txtIssuePreviewStatus,issues==0?"Các cập nhật cần chú ý sẽ xuất hiện tại đây":issues+" trường hợp cần xử lý");
        };
        com.google.firebase.firestore.EventListener<QuerySnapshot> requestListener = (snapshot,error) -> {
            if(!valid(token))return;
            errors[0]=error==null?"":"Không tải được yêu cầu.";
            if(snapshot!=null){requests.clear();requests.addAll(snapshot.getDocuments());cached[0]=snapshot.getMetadata().isFromCache();loaded[0]=!cached[0]||!snapshot.isEmpty();}
            render.run();
        };
        com.google.firebase.firestore.EventListener<QuerySnapshot> orderListener = (snapshot,error) -> {
            if(!valid(token))return;
            errors[1]=error==null?"":"Không tải được đơn hàng.";
            if(snapshot!=null){orders.clear();orders.addAll(snapshot.getDocuments());cached[1]=snapshot.getMetadata().isFromCache();loaded[1]=!cached[1]||!snapshot.isEmpty();}
            render.run();
        };
        host.listen(owner?repo.listenFreeRequestsAsOwner(host.uid(),requestListener):repo.listenFreeRequestsAsRequester(host.uid(),requestListener));
        host.listen(owner?repo.listenOrdersAsSeller(host.uid(),orderListener):repo.listenOrdersAsBuyer(host.uid(),orderListener));
        render.run();
    }
    private void bindCart() {
        heading("Giỏ hàng"); info(content, "Chọn các món để đặt hàng. Backend tự tách đơn theo người bán, mỗi món có số lượng 1.");
        Set<String> selected = new HashSet<>();
        Button checkoutSelected = button(content, R.id.btnCheckout, "Thanh toán các món đã chọn", () -> {
            if (!selected.isEmpty()) host.checkoutCart(new ArrayList<>(selected));
        }); checkoutSelected.setEnabled(false);
        TextView total = info(content, "Đang tải tổng tiền…");
        LinearLayout list = new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL); content.addView(list); long token = generation;
        Map<String, ListenerRegistration> postListeners = new HashMap<>();
        Map<String, Long> prices = new HashMap<>();
        Runnable sum = () -> { long amount = 0; for (String id : selected) amount += prices.getOrDefault(id, 0L);
            total.setText("Đã chọn " + selected.size() + " món: " + money(amount) + " · Phí giao được tính tại checkout.");
            checkoutSelected.setEnabled(!busy && !selected.isEmpty()); };
        host.listen(repo.listenCart(host.uid(), (snapshot, error) -> {
            if (!valid(token)) return;
            for (ListenerRegistration listener : postListeners.values()) listener.remove();
            postListeners.clear(); prices.clear(); list.removeAllViews();
            Set<String> remaining = new HashSet<>(); if (snapshot != null) for (DocumentSnapshot item : snapshot) remaining.add(item.getId());
            selected.retainAll(remaining); sum.run();
            if (error != null) { failure("Không tải được giỏ hàng.", () -> host.open(screen)); return; }
            if (snapshot != null && snapshot.getMetadata().isFromCache()) {
                info(list,"Đang chờ kết nối với server; dữ liệu trên thiết bị có thể chưa cập nhật.");
                if (snapshot.isEmpty()) return;
            }
            if (snapshot == null || snapshot.isEmpty()) { info(list, "Giỏ hàng đang trống."); return; }
            for (DocumentSnapshot item : snapshot.getDocuments()) {
                LinearLayout card = card(list); card.setTag("cart|" + item.getId());
                TextView title = info(card, text(item.getData(), "title")); title.setTextSize(18);
                TextView detail = info(card, "Đang kiểm tra bài đăng…");
                CheckBox choose = new CheckBox(a); choose.setText("Chọn món này"); choose.setEnabled(false); card.addView(choose);
                choose.setChecked(selected.contains(item.getId()));
                choose.setOnCheckedChangeListener((view, checked) -> { if (checked) selected.add(item.getId()); else selected.remove(item.getId()); sum.run(); });
                Button checkout = button(card, 0, "Thanh toán món này", () -> host.checkout(item.getId())); checkout.setEnabled(false);
                button(card, 0, "Xóa khỏi giỏ", () -> execute(repo.removeCartItem(host.uid(), item.getId()), value -> { }));
                ListenerRegistration registration = repo.listenPost(item.getId(), (post, postError) -> {
                    if (!valid(token) || !card.isAttachedToWindow()) return;
                    boolean available = postError == null && post != null && post.exists()
                            && "ACTIVE".equals(post.getString("status"))
                            && !host.uid().equals(post.getString("ownerId"))
                            && Arrays.asList("SALE", "FOR_SALE").contains(post.getString("type") == null ? post.getString("listingType") : post.getString("type"))
                            && number(post.get("price")) > 0;
                    if (available) {
                        title.setText(text(post.getData(), "title"));
                        detail.setText("Giá hiện tại: " + money(post.get("price")) + " · Người bán: " + text(item.getData(), "sellerId"));
                        prices.put(item.getId(), number(post.get("price")));
                    } else { detail.setText("Món đồ không còn khả dụng hoặc chưa tải được bài đăng."); prices.remove(item.getId()); selected.remove(item.getId()); choose.setChecked(false); }
                    choose.setEnabled(available && !post.getMetadata().isFromCache());
                    checkout.setEnabled(available && !busy && !post.getMetadata().isFromCache()); sum.run();
                });
                postListeners.put(item.getId(), registration); host.listen(registration);
            }
        }));
    }
    private void bindCheckout(boolean free) {
        heading(free ? "Gửi yêu cầu nhận đồ" : "Thanh toán");
        String context = host.uid() + ":" + (groupedCheckout() ? cartCheckoutIds.toString() : host.postId());
        if (!context.equals(checkoutContext)) { checkoutContext = context; checkoutKey = UUID.randomUUID().toString(); quote.clear(); addressId = ""; freeNote = ""; }
        long token = generation;
        quote.clear(); quoteError=""; quoteLoading=false; quotedAddressVersion="";
        capabilities(() -> { if(valid(token))renderCheckout(free); });
        renderCheckout(free);
        host.listen(db.collection("users").document(host.uid()).collection("addresses").addSnapshotListener((snapshot, error) -> {
            if (!valid(token)) return;
            if (error != null) { error(error); return; }
            addresses.clear(); if (snapshot != null) addresses.addAll(snapshot.getDocuments());
            if (addresses.stream().noneMatch(address -> address.getId().equals(addressId))) {
                addressId = addresses.isEmpty() ? "" : addresses.get(0).getId();
                for (DocumentSnapshot address : addresses) if (Boolean.TRUE.equals(address.getBoolean("isDefault"))) addressId = address.getId();
            }
            String addressVersion="";
            for(DocumentSnapshot address:addresses)if(address.getId().equals(addressId))addressVersion=addressId+address.getData().toString();
            if(!addressVersion.equals(quotedAddressVersion)){quotedAddressVersion=addressVersion;loadCheckoutQuote(free);}
            else renderCheckout(free);
        }));
    }
    private void loadCheckoutQuote(boolean free) {
        final long token=generation, request=++quoteRequest;
        quote.clear();quoteError="";quoteLoading=!addressId.isEmpty();renderCheckout(free);
        if(addressId.isEmpty())return;
        repo.quoteShipping(host.postId(),groupedCheckout()?cartCheckoutIds:Collections.emptyList(),addressId).addOnCompleteListener(result->{
            if(!valid(token)||request!=quoteRequest)return;quoteLoading=false;
            if(!result.isSuccessful()){quoteError=result.getException().getLocalizedMessage();renderCheckout(free);return;}
            quote=map(result.getResult().getData());
            if(quote.get("existingOrderIds") instanceof List){Toast.makeText(a,"Các món đã có đơn chờ thanh toán. Mở danh sách đơn để tiếp tục.",Toast.LENGTH_LONG).show();host.orders(false);return;}
            if(!text(quote,"existingOrderId").isEmpty()){host.openOrder(text(quote,"existingOrderId"));return;}
            if(free != "FREE".equals(text(quote,"type"))){quote.clear();quoteError="Loại bài đăng không phù hợp với thao tác này.";}
            renderCheckout(free);
        });
    }
    private String addressLabel(DocumentSnapshot address) {
        Map<String, Object> data = address.getData(); if (data == null) return address.getId();
        return text(data, "recipient") + " · " + text(data, "phone") + "\n" + AddressPicker.format(data);
    }
    private void renderCheckout(boolean free) {
        reset(); LinearLayout summary = card(content); section(summary, text(quote, "title"));
        if(!quote.isEmpty()) {
        info(summary, "Giá món đồ: " + money(quote.get("price")));
        info(summary, "Phí vận chuyển: " + money(quote.get("shippingFee")));
        TextView total = info(summary, "Tổng dự kiến: " + money(quote.get("total"))); total.setId(R.id.txtCheckoutQuote);
        info(summary, free ? ("GHN".equals(text(quote,"provider"))?"Món tặng miễn phí. Người nhận thanh toán phí giao hàng trực tiếp cho GHN; app không thu khoản này.":"Món đồ được tặng miễn phí. Phí vận chuyển hiển thị để tham khảo; app chưa thu phí này.")
                : "VNPAY".equals(text(capabilities,"paymentProvider"))?"VNPAY Sandbox – thanh toán thử nghiệm từng đơn theo người bán. Một đơn thành công không có nghĩa cả giỏ đã thanh toán.":"Thanh toán mô phỏng. Các món cùng người bán được gộp trong một đơn; phí giao tính cho từng đơn.");
        if (quote.get("groups") instanceof List) for (Object raw : (List<?>) quote.get("groups")) {
            Map<String, Object> group = map(raw); section(summary, "Người bán: " + text(group, "sellerId"));
            for (Object item : (List<?>) group.get("items")) { Map<String, Object> data = map(item); info(summary, text(data, "title") + " × 1 · " + money(data.get("price"))); }
            info(summary, "Phí giao đơn này: " + money(group.get("shippingFee")));
        }
        if("GHN".equals(text(quote,"provider"))) {
            info(summary,"GHN · "+("staging".equals(text(quote,"environment"))?"THỬ NGHIỆM STAGING":"VẬN CHUYỂN THẬT")+" · Báo giá có hiệu lực 5 phút.");
            info(summary,"Kiện hàng dùng thông số ước tính do quản trị viên cấu hình. Không thu COD lại tiền hàng đã trả trước.");
        }
        } else info(summary,quoteLoading?"Đang tính phí vận chuyển…":addressId.isEmpty()?"Chọn địa chỉ nhận hàng để lấy báo giá.":"Chưa có báo giá hợp lệ.");
        if(!quoteError.isEmpty())info(summary,quoteError);
        if(!addressId.isEmpty())button(summary,0,"Lấy lại báo giá",()->loadCheckoutQuote(free)).setEnabled(!busy&&!quoteLoading);
        LinearLayout destination = card(content); section(destination, "Địa chỉ nhận hàng");
        if (addresses.isEmpty()) info(destination, "Chưa có địa chỉ. Thêm địa chỉ để tiếp tục.");
        else {
            Spinner spinner = new Spinner(a); spinner.setId(R.id.addressChoice);
            ArrayList<String> choices = new ArrayList<>(); int selected = 0;
            for (int i = 0; i < addresses.size(); i++) { choices.add(addressLabel(addresses.get(i))); if (addresses.get(i).getId().equals(addressId)) selected = i; }
            spinner.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, choices)); spinner.setSelection(selected); destination.addView(spinner);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(AdapterView<?> parent) { }
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (position < addresses.size()) {
                    String selectedAddress=addresses.get(position).getId();
                    if(!selectedAddress.equals(addressId)){addressId=selectedAddress;quotedAddressVersion=addressId+addresses.get(position).getData().toString();loadCheckoutQuote(free);}
                } }
            });
        }
        button(destination, R.id.btnNewCheckoutAddress, "Thêm địa chỉ", this::newAddress);
        if("GHN".equals(text(capabilities,"shippingProvider"))&&!addressId.isEmpty())button(destination,0,"Chọn / cập nhật địa chỉ nhận hàng GHN",()->{
            long pickerToken=generation;new GhnAddressPicker(a,addressId,()->valid(pickerToken),this::error,false,()->{if(valid(pickerToken))host.open(screen);}).open();
        });
        for(DocumentSnapshot address:addresses)if(address.getId().equals(addressId)&&address.get("ghnDisplay") instanceof Map){Map<String,Object> display=map(address.get("ghnDisplay"));info(destination,"Địa chỉ theo GHN: "+text(display,"ward")+", "+text(display,"district")+", "+text(display,"province"));}
        if (free) {
            EditText note = input(content, R.id.edtFreeRequestNote, "Lời nhắn cho người tặng", freeNote);
            note.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { freeNote = s.toString(); }
                public void afterTextChanged(Editable s) { }
            });
        }
        Button submit = button(content, free ? R.id.btnSubmitFreeRequest : R.id.btnPlaceOrder, free ? "Gửi yêu cầu nhận đồ" : "Tạo đơn — tiếp tục thanh toán", () -> {
            if (addressId.isEmpty()) return;
            if (free) execute(repo.requestFreeWithQuote(host.postId(), freeNote, addressId, quote), value -> host.openRequest(text(map(value), "requestId")));
            else if (groupedCheckout()) {
                execute(repo.checkoutShipping("", cartCheckoutIds, checkoutKey, addressId, quote), value -> {
                    Object ids = map(value).get("orderIds");
                    if (!(ids instanceof List) || ((List<?>) ids).isEmpty()) { error(new IllegalStateException("Server không trả mã đơn hàng.")); return; }
                    List<?> orderIds = (List<?>) ids;
                    if (orderIds.size() == 1) host.openOrder(String.valueOf(orderIds.get(0)));
                    else { Toast.makeText(a, "Đã tạo " + orderIds.size() + " đơn theo người bán. Chọn từng đơn để thanh toán riêng; mỗi đơn có kết quả riêng.", Toast.LENGTH_LONG).show(); host.orders(false); }
                });
            } else {
                final String postId = host.postId(); final boolean fromCart = host.checkoutFromCart();
                execute(repo.checkoutShipping(postId, Collections.emptyList(), checkoutKey, addressId, quote), value -> {
                    String id = text(map(value), "orderId"); if (id.isEmpty()) { error(new IllegalStateException("Server không trả mã đơn hàng.")); return; }
                    if (fromCart) repo.removeCartItem(host.uid(), postId).addOnFailureListener(this::error);
                    host.openOrder(id);
                });
            }
        }); submit.setEnabled(!busy && !quoteLoading && !quote.isEmpty() && !addressId.isEmpty());restoreDialog();
    }
    private void newAddress() {
        LinearLayout form = new LinearLayout(a); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(16), dp(8), dp(16), 0);
        String[] keys = {"recipient", "phone", "province", "district", "ward", "street"};
        String[] hints = {"Tên người nhận", "Số điện thoại", "Chọn trong 63 tỉnh / thành trước sáp nhập", "Chọn quận / huyện", "Chọn phường / xã", "Số nhà, đường"};
        EditText[] inputs = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            if(i>=2&&i<=4){android.widget.AutoCompleteTextView field=new android.widget.AutoCompleteTextView(a);field.setHint(hints[i]);field.setTextSize(14);form.addView(field,new LinearLayout.LayoutParams(-1,dp(54)));inputs[i]=field;}
            else inputs[i] = input(form, 0, hints[i], "");
        }
        final AddressPicker picker;
        try{picker=new AddressPicker(a,(android.widget.AutoCompleteTextView)inputs[2],(android.widget.AutoCompleteTextView)inputs[3],(android.widget.AutoCompleteTextView)inputs[4]);}
        catch(java.io.IOException failure){error(failure);return;}
        com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if(user!=null){inputs[0].setText(user.getDisplayName());inputs[1].setText(user.getPhoneNumber());}
        final boolean[] edited={false,false};
        for(int i=0;i<2;i++){final int field=i;inputs[i].addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){edited[field]=true;}public void afterTextChanged(android.text.Editable s){}
        });}
        long profileToken=generation;
        db.document("users/"+host.uid()).get(Source.SERVER).addOnSuccessListener(profile->{if(!valid(profileToken))return;
            for(int i=0;i<2;i++){String v=profile.getString(i==0?"name":"phone");if(!edited[i]&&v!=null&&!v.trim().isEmpty())inputs[i].setText(v);}
        }).addOnFailureListener(failure->{ /* Editable Auth defaults remain available. */ });
        inputs[1].setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        ScrollView scroll = new ScrollView(a); scroll.addView(form);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a).setTitle("Thêm địa chỉ nhận hàng").setView(scroll)
                .setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        trackDialog("address",dialog,form);if(newAddressKey.isEmpty())newAddressKey=UUID.randomUUID().toString();
        long token = generation;
        dialog.setOnShowListener(unused -> dialog.getButton(-1).setOnClickListener(v -> {
            Map<String, Object> address = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                String value = inputs[i].getText().toString().trim(); if (value.isEmpty()&&(i<2||i==5)) { inputs[i].setError("Vui lòng nhập thông tin"); return; } address.put(keys[i], value);
            }
            if (MarketplaceLinks.normalizePhone(String.valueOf(address.get("phone"))).isEmpty()) { inputs[1].setError("Số điện thoại không hợp lệ"); return; }
            if(!picker.apply(address))return;
            dialog.getButton(-1).setEnabled(false); DocumentReference ref = db.collection("users").document(host.uid()).collection("addresses").document(newAddressKey);
            address.put("isDefault", addresses.isEmpty()); address.put("label", "HOME");
            Map<String,Object> payload=new HashMap<>();payload.put("id",newAddressKey);payload.put("address",address);
            com.google.firebase.functions.FirebaseFunctions.getInstance().getHttpsCallable("saveAddress").call(payload).addOnSuccessListener(value -> { if (valid(token)) { addressId = ref.getId(); newAddressKey="";dialog.dismiss(); host.open(screen); } })
                    .addOnFailureListener(error -> { if (valid(token)) { dialog.getButton(-1).setEnabled(true); error(error); } });
        })); dialog.show();
    }

    private void bindOrder() {
        heading("Chi tiết đơn hàng"); order.clear(); payment.clear(); shipment.clear(); dispute.clear(); timeline.clear(); ledger.clear(); boundPayment = ""; boundDispute = ""; shipmentBound = false;
        String id = host.orderId(); long token = generation; info(content, "Đang tải đơn hàng…");
        if (id.isEmpty()) { failure("Chưa chọn đơn hàng.", host::back); return; }
        repo.refreshOrder(id).addOnFailureListener(error -> { if (valid(token)) error(error); });
        capabilities(this::renderOrder);
        host.listen(repo.listenOrder(id, (snapshot, error) -> {
            if (!valid(token)) return;
            if (error != null || snapshot == null || !snapshot.exists()) { failure("Không tìm thấy đơn hoặc bạn không có quyền xem.", () -> host.open(screen)); return; }
            host.orderSnapshot(snapshot); order = snapshot.getData();
            // Reading a missing private shipment is denied by its resource-based rules.
            // Wait for the atomic order/shipment creation before attaching this listener.
            if (!shipmentBound && !text(order, "trackingCode").isEmpty()) {
                shipmentBound = true;
                host.listen(repo.listenShipment(id, (record, shipmentError) -> {
                    if (!valid(token)) return;
                    if (shipmentError != null) { error(shipmentError); return; }
                    shipment = record != null && record.exists() ? record.getData() : new HashMap<>(); renderOrder();
                }));
            }
            String paymentId = text(order, "paymentId");
            if (!paymentId.isEmpty() && !paymentId.equals(boundPayment)) {
                boundPayment = paymentId;
                host.listen(repo.listenPayment(paymentId, (pay, payError) -> { if (valid(token) && pay != null && pay.exists()) { payment = pay.getData(); renderOrder(); } }));
            }
            String disputeId = text(order, "disputeId");
            if (!disputeId.isEmpty() && !disputeId.equals(boundDispute)) {
                boundDispute = disputeId;
                host.listen(db.collection("disputes").document(disputeId).addSnapshotListener((record, recordError) -> {
                    if (valid(token) && record != null && record.exists()) { dispute = record.getData(); renderOrder(); }
                }));
            }
            renderOrder();
        }));
        host.listen(repo.listenOrderStatusEvents(id, (snapshot, error) -> {
            if (!valid(token)) return; if (error != null) { error(error); return; }
            timeline = snapshot == null ? new ArrayList<>() : snapshot.getDocuments(); renderOrder();
        }));
        host.listen(repo.listenMoneyTransactions(id, (snapshot, error) -> {
            if (!valid(token)) return; if (error != null) { error(error); return; }
            ledger = snapshot == null ? new ArrayList<>() : snapshot.getDocuments(); renderOrder();
        }));
    }
    private void renderOrder() {
        if (order.isEmpty() || screen != R.layout.activity_order_detail) return;
        reset(); String status = text(order, "orderStatus"); boolean buyer = host.uid().equals(text(order, "buyerId"));
        section(content, title(order)); info(content, "Đơn #" + host.orderId() + " · " + (buyer ? "Người mua / nhận" : "Người bán / tặng"));
        if (order.get("items") instanceof List) for (Object raw : (List<?>) order.get("items")) {
            Map<String, Object> item = map(raw); info(content, text(item, "title") + " × 1 · " + money(item.get("price")));
        }
        info(content, label(status) + " · " + date(order.get("createdAt")));
        if (order.get("inspectionDeadline") != null && "DELIVERED".equals(status))
            info(content, "Hạn kiểm tra và mở tranh chấp: " + date(order.get("inspectionDeadline")) + ("VNPAY".equals(text(order,"paymentProvider"))?". Hết hạn, đơn có thể hoàn tất; trả tiền người bán vẫn chờ đối soát.":". Hết hạn, backend chỉ giải ngân mô phỏng khi không có tranh chấp."));
        LinearLayout paymentCard = card(content); section(paymentCard, "Thanh toán");
        TextView payState = info(paymentCard, label(text(order, "paymentStatus"))); payState.setId(R.id.txtLivePaymentState);
        info(paymentCard, "Giá món: " + money(order.get("price")) + " · Phí giao: " + money(order.get("shippingFee")));
        info(paymentCard, "Tổng: " + money(number(order.get("price")) + number(order.get("shippingFee"))));
        if (buyer && "PAID".equals(text(order, "paymentStatus"))
                && !Boolean.TRUE.equals(order.get("paymentNeedsReview")) && !Boolean.TRUE.equals(payment.get("needsReview"))
                && Arrays.asList("CONFIRMED", "PREPARING", "READY_TO_SHIP", "SHIPPING", "DELIVERED", "COMPLETED").contains(status)) {
            TextView success = info(paymentCard, "Thanh toán thành công"); success.setId(R.id.paymentSuccessMessage);
            success.setTextColor(0xFF1F6B48); success.setTypeface(null, android.graphics.Typeface.BOLD);
            info(paymentCard, "Bạn đã hoàn tất đặt hàng. Người bán sẽ tiếp tục xử lý đơn.");
            button(paymentCard, R.id.btnPaymentComplete, "Hoàn tất", () -> host.orders(false));
        }
        info(paymentCard, "FREE".equals(text(order, "orderType")) ? "Món tặng không thu tiền qua app; phí giao hàng chưa được thu."
                : "VNPAY".equals(text(order,"paymentProvider")) ? "VNPAY Sandbox – thanh toán thử nghiệm. Hoàn tiền và trả người bán cần đối soát, chưa tự động chuyển tiền." : "THANH TOÁN MÔ PHỎNG — không chuyển tiền thật.");
        if(Boolean.TRUE.equals(order.get("paymentNeedsReview"))||!text(order,"refundStatus").isEmpty())info(paymentCard,"Thanh toán/hoàn tiền cần đối soát: "+text(order,"refundStatus"));
        if (order.get("paymentDeadline") != null && "PENDING_PAYMENT".equals(status)) info(paymentCard, "Hạn thanh toán: " + date(order.get("paymentDeadline")));
        if (buyer && "PENDING_PAYMENT".equals(status)) {
            if ("VNPAY".equals(text(order,"paymentProvider"))) {
                Button pay=button(paymentCard,0,"Thanh toán ngay",()->a.startActivity(new android.content.Intent(a,VnpayPaymentActivity.class).putExtra("orderId",host.orderId()).putExtra("startPayment",true)));
                pay.setEnabled(!busy&&Boolean.TRUE.equals(capabilities.get("vnpayConfigured")));
                if(!Boolean.TRUE.equals(capabilities.get("vnpayConfigured")))info(paymentCard,"VNPAY sandbox chưa được cấu hình đầy đủ trên server.");
            } else if (Boolean.TRUE.equals(capabilities.get("simulatedPayments"))) {
                button(paymentCard, R.id.btnSimulatePaid, "Mô phỏng thanh toán thành công", () -> execute(payments.pay(host.orderId()), value -> { }));
                button(paymentCard, R.id.btnSimulatePaymentFailed, "Mô phỏng thanh toán thất bại", () -> execute(payments.simulateFailure(host.orderId()), value -> { }));
            } else info(paymentCard, "Chưa bật thanh toán mô phỏng trên Emulator; chưa cấu hình cổng thanh toán thật.");
        }
        LinearLayout events = card(content); events.setId(R.id.liveOrderTimeline); section(events, "Timeline đơn hàng");
        if (timeline.isEmpty()) info(events, "Chưa có sự kiện được ghi nhận.");
        for (DocumentSnapshot event : timeline) {
            Map<String, Object> data = event.getData(); info(events, date(data.get("createdAt")) + " · " + label(text(data, "to")) + "\n" + text(data, "reason"));
        }
        if (!ledger.isEmpty()) {
            LinearLayout money = card(content); money.setId(R.id.liveMoneyEvents); section(money, "Lịch sử thanh toán");
            for (DocumentSnapshot event : ledger) moneyEvent(money, event);
        }
        LinearLayout shipping = card(content); section(shipping, "Vận chuyển");
        if (shipment.isEmpty()) {
            info(shipping, "Chưa có vận đơn.");
            button(shipping, R.id.btnOpenDeliveryMap, "Xem bản đồ điểm gửi – nhận", () -> host.open(R.layout.activity_delivery_map));
        } else {
            info(shipping, text(shipment, "trackingCode") + " · " + label(text(shipment, "status")));
            button(shipping, R.id.btnTrackShipping, "Chi tiết vận đơn và lịch sử", () -> host.open(R.layout.activity_delivery));
        }
        Map<String, Object> address = map(order.get("shippingAddress"));
        info(shipping, address.isEmpty() ? "Đơn cũ chưa lưu địa chỉ." : text(address, "recipient") + " · " + text(address, "phone") + "\n" + AddressPicker.format(address));
        if("GHN".equals(text(order,"shippingProvider"))&&!text(shipment,"trackingCode").isEmpty())
            button(shipping,R.id.btnRefreshGhnShipment,"Cập nhật vận chuyển",()->execute(repo.refreshOrder(host.orderId()),value->{}));
        if (!buyer) {
            if ("CONFIRMED".equals(status)) button(content, R.id.btnSellerConfirm, "Xác nhận đơn và chuẩn bị hàng", () -> execute(repo.updateOrderStatus(host.orderId(), "PREPARING"), value -> { }));
            if ("PREPARING".equals(status)) button(content, R.id.btnSellerReady, "Đã chuẩn bị — sẵn sàng giao", () -> execute(repo.updateOrderStatus(host.orderId(), "READY_TO_SHIP"), value -> { }));
            if ("PENDING_PAYMENT".equals(status) || "CONFIRMED".equals(status)) button(content, R.id.btnSellerReject, "Từ chối đơn hàng", this::rejectOrder);
            if ("READY_TO_SHIP".equals(status)) {
                if (Boolean.TRUE.equals(capabilities.get("simulatedShipping"))) button(content, R.id.btnCreateShipment, "Tạo vận đơn mô phỏng", () -> execute(shippingService.create(host.orderId()), value -> { }));
                else if("GHN".equals(text(capabilities,"shippingProvider"))) {
                    if(!"CANCELLED".equals(text(shipment,"status")))button(content,R.id.btnCreateShipment,"Tạo / đối soát vận đơn GHN",()->execute(shippingService.create(host.orderId()),value->{}));
                    else info(content,"Vận đơn GHN đã hủy. Đơn cần được đối soát trước khi xử lý tiếp.");
                } else info(content, "Chưa cấu hình hãng vận chuyển.");
            }
            if ("SHIPPING".equals(status) && Boolean.TRUE.equals(capabilities.get("simulatedShipping"))) {
                button(content, R.id.btnSimulateLocation, "Cập nhật vị trí mô phỏng", this::locationForm);
                button(content, R.id.btnSimulateDelivered, "Mô phỏng hãng giao thành công", () -> execute(shippingService.simulateEvent(host.orderId(), "DELIVERED", "Đã giao hàng trong môi trường mô phỏng"), value -> { }));
                button(content, R.id.btnSimulateReturned, "Mô phỏng hoàn hàng", () -> execute(shippingService.simulateEvent(host.orderId(), "RETURNED", "Hoàn hàng trong môi trường mô phỏng"), value -> { }));
            }
        }
        boolean openDispute = Arrays.asList("OPEN", "WAITING_EVIDENCE").contains(text(order, "disputeStatus"));
        if (openDispute || !dispute.isEmpty()) {
            LinearLayout panel = card(content); TextView state = info(panel, label(text(order, "disputeStatus"))); state.setId(R.id.txtDisputeState);
            info(panel, text(dispute, "reason") + "\n" + text(dispute, "description"));
            for (Object path : dispute.get("evidencePaths") instanceof List ? (List<?>) dispute.get("evidencePaths") : Collections.emptyList()) {
                ImageView image = new ImageView(a); image.setAdjustViewBounds(true); image.setContentDescription("Ảnh bằng chứng tranh chấp"); panel.addView(image, new LinearLayout.LayoutParams(-1, dp(180)));
                long token = generation;
                FirebaseStorage.getInstance().getReference().child(String.valueOf(path)).getBytes(5 * 1024 * 1024)
                        .addOnSuccessListener(bytes -> { if (valid(token) && image.isAttachedToWindow()) image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length)); });
            }
        }
        if (buyer && "DELIVERED".equals(status) && !openDispute) button(content, R.id.btnConfirmReceived, "Xác nhận đã nhận hàng", () -> execute(repo.updateOrderStatus(host.orderId(), "COMPLETED"), value -> { }));
        if (buyer && ("PENDING_PAYMENT".equals(status) || "CONFIRMED".equals(status))) button(content, R.id.btnCancelOrder, "Hủy đơn hàng", () -> host.open(R.layout.activity_cancel_order));
        if (("SHIPPING".equals(status) || "DELIVERED".equals(status)) && !openDispute) button(content, R.id.btnReportOrder, "Báo cáo tranh chấp", () -> host.open(R.layout.activity_report_issue));
        button(content, R.id.btnChatOrderSeller, "Chat với bên còn lại", () -> host.chat(text(order, buyer ? "sellerId" : "buyerId"), text(order, "postId")));
        if("GHN".equals(text(order,"shippingProvider"))) {
            info(content,"GHN · "+("staging".equals(text(order,"shippingEnvironment"))?"THỬ NGHIỆM STAGING":"VẬN CHUYỂN THẬT")+" · "+("VNPAY".equals(text(order,"paymentProvider"))?"VNPAY Sandbox":"Thanh toán mô phỏng"));
            if(!buyer)button(content,0,"Chọn mã GHN cho địa chỉ lấy hàng của tôi",this::selectGhnPickupAddress);
            if(!buyer&&Arrays.asList("READY","PICKING").contains(text(shipment,"status")))button(content,0,"Hủy vận đơn GHN",()->new android.app.AlertDialog.Builder(a)
                .setTitle("Yêu cầu GHN hủy vận đơn?").setMessage("Chỉ xác nhận hủy khi GHN trả trạng thái đã hủy. Thao tác này không tự hoàn tiền hoặc hủy đơn mua.")
                .setNegativeButton("Quay lại",null).setPositiveButton("Yêu cầu hủy",(dialog,which)->execute(repo.cancelGhnShipment(host.orderId()),value->{})).show());
            if(Boolean.TRUE.equals(order.get("shippingNeedsReview")))info(content,"Vận chuyển cần đối soát. Chưa tự đóng đơn hoặc hoàn tiền do sự cố giao/hoàn/hủy.");
        }
        restoreDialog();
    }
    private void selectGhnPickupAddress() {
        long token=generation;
        db.collection("users").document(host.uid()).collection("addresses").get().addOnCompleteListener(result->{
            if(!valid(token))return;if(!result.isSuccessful()){error(result.getException());return;}
            List<DocumentSnapshot> own=result.getResult().getDocuments();
            if(own.isEmpty()){newAddress();return;}
            String[] names=new String[own.size()];for(int i=0;i<names.length;i++)names[i]=addressLabel(own.get(i))+(own.get(i).get("ghn") instanceof Map?"\nĐã lưu mã GHN · Bấm để cập nhật":"\nChưa có mã GHN · Bấm để thiết lập");
            new android.app.AlertDialog.Builder(a).setTitle("Chọn địa chỉ lấy hàng của người bán")
                .setItems(names,(dialog,index)->new GhnAddressPicker(a,own.get(index).getId(),()->valid(token),this::error,true,()->{if(valid(token))host.open(screen);}).open()).setNegativeButton("Đóng",null).show();
        });
    }
    private void rejectOrder() {
        LinearLayout form = new LinearLayout(a); form.setOrientation(LinearLayout.VERTICAL); EditText reason = input(form, 0, "Lý do từ chối", "");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(a).setTitle("Từ chối đơn hàng").setView(form).setNegativeButton("Quay lại", null).setPositiveButton("Từ chối",null).create();
        trackDialog("reject",dialog,form);
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String note=reason.getText().toString().trim();if(note.length()<3){reason.setError("Nhập lý do từ chối ít nhất 3 ký tự");return;}dialog.dismiss();execute(repo.rejectOrder(host.orderId(),note),value->{});}));dialog.show();
    }
    private void locationForm() {
        LinearLayout form = new LinearLayout(a); form.setOrientation(LinearLayout.VERTICAL);
        info(form, "Chỉ dùng tọa độ kiểm thử, không phải GPS của hãng giao hàng.");
        EditText lat = input(form, R.id.edtSimulatedLat, "Vĩ độ (-90 đến 90)", "");
        EditText lng = input(form, R.id.edtSimulatedLng, "Kinh độ (-180 đến 180)", "");
        lat.setInputType(12290); lng.setInputType(12290);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a).setTitle("Vị trí mô phỏng").setView(form).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        trackDialog("location",dialog,form);
        dialog.setOnShowListener(d -> { dialog.getButton(-1).setId(R.id.btnSaveSimulatedLocation); dialog.getButton(-1).setOnClickListener(v -> {
            try {
                double latitude = Double.parseDouble(lat.getText().toString()), longitude = Double.parseDouble(lng.getText().toString());
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new NumberFormatException();
                dialog.dismiss(); execute(repo.simulateShipmentLocation(host.orderId(), latitude, longitude), value -> { });
            } catch (NumberFormatException error) { lat.setError("Nhập tọa độ hợp lệ"); }
        }); }); dialog.show();
    }

    private void bindShipment(boolean mapPage) {
        heading(mapPage ? "Điểm gửi, nhận và lịch sử" : "Chi tiết vận đơn");
        long token = generation; info(content, "Đang tải vận đơn…");
        Map<String,Object> orderData = new HashMap<>();
        DocumentSnapshot[] record = {null}; boolean[] loaded = {false, false}; String[] errors = {"", ""};
        Runnable render = () -> {
            if (!valid(token)) return;
            if (!errors[0].isEmpty() || !errors[1].isEmpty()) { failure("Không tải được vận đơn hoặc địa chỉ. Kiểm tra kết nối và thử lại.", () -> host.open(screen)); return; }
            if (!loaded[0] || !loaded[1]) return;
            reset();
            if (record[0] == null || !record[0].exists()) {
                if (mapPage) {
                    section(content, "Điểm gửi và điểm nhận của đơn");
                    info(content, "Chưa tạo vận đơn. Bản đồ chỉ hiển thị địa chỉ đã lưu trong đơn.");
                    shipmentMap = new ShipmentTrackingMap(a, map(orderData.get("pickupAddress")), map(orderData.get("shippingAddress")),
                            Collections.emptyMap(), ShipmentTrackingMap.geocoder(a), shipmentGeocodes);
                    content.addView(shipmentMap, new LinearLayout.LayoutParams(-1, -2));
                    LinearLayout events = card(content); events.setId(R.id.liveShipmentEvents);
                    section(events, "Lịch sử vận chuyển"); info(events, "Chưa có mốc vận chuyển được ghi nhận.");
                } else info(content, "Đơn này chưa có vận đơn.");
                return;
            }
            Map<String,Object> data = record[0].getData(); section(content, text(data, "trackingCode"));
            boolean simulated = "SIMULATED".equals(text(data, "mode")) || "MOCK_CARRIER".equals(text(data, "carrier"));
            info(content, text(data, "carrier") + " · " + (simulated ? "VẬN CHUYỂN MÔ PHỎNG" : "Dữ liệu hãng vận chuyển"));
            if ("GHN".equals(text(data, "provider"))) {
                info(content, "Môi trường GHN: " + text(data, "environment") + " · " + text(data, "providerStatus"));
                if (data.get("expectedDeliveryTime") != null) info(content, "Giao dự kiến: " + date(data.get("expectedDeliveryTime")));
                info(content, "Phí báo giá: " + money(data.get("quotedFee")) + (data.get("providerFee") instanceof Number ? " · Phí hãng: " + money(data.get("providerFee")) : ""));
                button(content, 0, "Làm mới từ GHN", () -> execute(repo.refreshOrder(host.orderId()), value -> { }));
            }
            TextView status = info(content, label(text(data, "status"))); status.setId(R.id.txtLiveShipmentState);
            Map<String,Object> location = map(data.get("location"));
            if (mapPage) {
                Map<String,Object> pickup = map(data.get("pickupAddress")), delivery = map(data.get("deliveryAddress"));
                if (delivery.isEmpty()) delivery = map(data.get("shippingAddress"));
                if (delivery.isEmpty()) delivery = map(orderData.get("shippingAddress"));
                shipmentMap = new ShipmentTrackingMap(a, pickup, delivery, location, ShipmentTrackingMap.geocoder(a), shipmentGeocodes);
                content.addView(shipmentMap, new LinearLayout.LayoutParams(-1, -2));
                Object latitude = location.get("lat"), longitude = location.get("lng");
                if (latitude instanceof Number && longitude instanceof Number) {
                    double lat = ((Number)latitude).doubleValue(), lng = ((Number)longitude).doubleValue();
                    if (ShipmentTrackingMap.valid(new double[]{lat, lng})) {
                        info(content, "SIMULATED".equals(text(location, "source")) ? "Mốc mô phỏng do người bán nhập." : "Mốc cập nhật từ đơn vị vận chuyển.");
                        info(content, lat + ", " + lng + "\nCập nhật: " + date(location.get("recordedAt")));
                        button(content, R.id.btnOpenTrackingMap, "Mở mốc cập nhật trên bản đồ", () -> openTrackingPoint(lat, lng));
                    }
                }
            } else button(content, R.id.btnOpenDeliveryMap, "Xem bản đồ điểm gửi – nhận và lịch sử", () -> host.open(R.layout.activity_delivery_map));
            LinearLayout events = card(content); events.setId(R.id.liveShipmentEvents); section(events, "Lịch sử vận chuyển");
            List<?> records = data.get("events") instanceof List ? (List<?>)data.get("events") : Collections.emptyList();
            if (records.isEmpty()) info(events, "Chưa có sự kiện vận chuyển.");
            for (int i = records.size() - 1; i >= 0; i--) {
                Map<String,Object> event = map(records.get(i));
                LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundResource(R.drawable.bg_transaction_card); events.addView(row);
                section(row, (i == records.size() - 1 ? "● " : "○ ") + label(text(event, "status")));
                info(row, date(event.get("createdAt")) + (simulated ? " · Mô phỏng" : ""));
                if (!text(event, "description").isEmpty()) info(row, text(event, "description"));
                if (!text(event, "warehouse").isEmpty()) info(row, "Điểm xử lý: " + text(event, "warehouse"));
            }
        };
        host.listen(repo.listenOrder(host.orderId(), (snapshot, error) -> {
            if (!valid(token)) return;
            Map<String,Object> incoming = snapshot != null && snapshot.exists() ? snapshot.getData() : Collections.emptyMap();
            if (loaded[0] && error == null && errors[0].isEmpty() && orderData.equals(incoming)) return;
            loaded[0] = true; errors[0] = error == null ? "" : "order"; orderData.clear(); orderData.putAll(incoming);
            render.run();
        }));
        host.listen(repo.listenShipment(host.orderId(), (snapshot, error) -> {
            if (!valid(token)) return;
            if (loaded[1] && error == null && errors[1].isEmpty() && record[0] != null && snapshot != null
                    && Objects.equals(record[0].getData(), snapshot.getData())) return;
            loaded[1] = true; errors[1] = error == null ? "" : "shipment"; record[0] = snapshot;
            render.run();
        }));
    }

    private void openTrackingPoint(double lat, double lng) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng))); }
        catch (android.content.ActivityNotFoundException missing) {
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lng + "#map=16/" + lat + "/" + lng))); }
            catch (android.content.ActivityNotFoundException absent) { error(absent); }
        }
    }

    private void bindHistory() {
        heading("Lịch sử giao dịch"); historyBuyer.clear(); historySeller.clear(); historyMoney.clear(); moneyListeners.clear(); historyPhotos.clear();
        historyBuyerLoaded=false;historySellerLoaded=false;historyError="";long token=generation;
        androidx.recyclerview.widget.RecyclerView list=a.findViewById(R.id.transactionHistoryList);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(a));
        historyAdapter=new vn.edu.ueh.thudinh.fashion2hand.adapter.TransactionHistoryAdapter(row->host.openOrder(row.id),(row,image)->loadHistoryPhoto(row.postId,row.id,image,token));
        list.setAdapter(historyAdapter);
        RadioGroup filters=a.findViewById(R.id.historyFilters);
        int[] ids={R.id.filterHistoryAll,R.id.filterHistoryBuy,R.id.filterHistorySell,R.id.filterHistoryFree};
        historyFilter=Math.max(0,Math.min(3,historyFilter));filters.check(ids[historyFilter]);
        filters.setOnCheckedChangeListener((group,id)->{for(int i=0;i<ids.length;i++)if(ids[i]==id)historyFilter=i;renderHistory();});
        a.findViewById(R.id.historyState).setOnClickListener(view->{if(!historyError.isEmpty())host.open(screen);});
        host.listen(repo.listenOrdersAsBuyer(host.uid(),(snapshot,error)->{if(valid(token)){historyBuyerLoaded=snapshot!=null&&!snapshot.getMetadata().isFromCache();historyChanged(historyBuyer,snapshot,error,token);}}));
        host.listen(repo.listenOrdersAsSeller(host.uid(),(snapshot,error)->{if(valid(token)){historySellerLoaded=snapshot!=null&&!snapshot.getMetadata().isFromCache();historyChanged(historySeller,snapshot,error,token);}}));
        renderHistory();
    }
    private void historyChanged(Map<String, DocumentSnapshot> target, QuerySnapshot snapshot, Exception error, long token) {
        if(error!=null){historyError="Không tải được lịch sử. Nhấn để thử lại.";renderHistory();return;}
        target.clear();if(snapshot!=null)for(DocumentSnapshot doc:snapshot)target.put(doc.getId(),doc);
        Map<String,DocumentSnapshot> all=new HashMap<>(historyBuyer);all.putAll(historySeller);
        for(String id:new ArrayList<>(moneyListeners.keySet()))if(!all.containsKey(id)){moneyListeners.remove(id).remove();historyMoney.remove(id);}
        for(String id:all.keySet())if(!moneyListeners.containsKey(id)){
            ListenerRegistration listener=repo.listenMoneyTransactions(id,(money,moneyError)->{
                if(!valid(token))return;
                if(moneyError!=null){historyError="Một số thông tin thanh toán chưa tải được. Nhấn để thử lại.";renderHistory();return;}
                historyMoney.put(id,money==null?new ArrayList<>():money.getDocuments());renderHistory();
            });moneyListeners.put(id,listener);host.listen(listener);
        }
        renderHistory();
    }
    private String historyBadge(Map<String,Object> data,List<DocumentSnapshot> events){
        if("FREE".equals(text(data,"orderType")))return "Chia sẻ miễn phí";
        if("VNPAY".equals(text(data,"paymentProvider"))) {
            if(Boolean.TRUE.equals(data.get("paymentNeedsReview"))||"REVIEW_REQUIRED".equals(text(data,"payoutStatus")))return "Cần đối soát\nVNPAY Sandbox";
            return label(text(data,"paymentStatus"))+"\nVNPAY Sandbox";
        }
        if(events!=null)for(DocumentSnapshot event:events)if("SELLER_PAYOUT".equals(event.getString("type")))return "Đã giải ngân\nMô phỏng";
        if(events!=null)for(DocumentSnapshot event:events)if("REFUND".equals(event.getString("type")))return "Đã hoàn tiền\nMô phỏng";
        if(events!=null)for(DocumentSnapshot event:events)if("HOLDING".equals(event.getString("status")))return "Đang giữ tiền\nMô phỏng";
        return label(text(data,"paymentStatus"));
    }
    private void renderHistory() {
        if(screen!=R.layout.activity_transaction_history||historyAdapter==null)return;
        Map<String,DocumentSnapshot> all=new HashMap<>(historyBuyer);all.putAll(historySeller);
        ArrayList<DocumentSnapshot> documents=new ArrayList<>(all.values());documents.sort((left,right)->Long.compare(time(right),time(left)));
        ArrayList<vn.edu.ueh.thudinh.fashion2hand.adapter.TransactionHistoryAdapter.Row> rows=new ArrayList<>();
        for(DocumentSnapshot doc:documents){
            Map<String,Object> data=doc.getData();boolean free="FREE".equals(text(data,"orderType")),buyer=host.uid().equals(text(data,"buyerId"));
            if(historyFilter==1&&(free||!buyer)||historyFilter==2&&(free||buyer)||historyFilter==3&&!free)continue;
            String meta="#"+doc.getId().substring(0,Math.min(10,doc.getId().length()))+" · "+date(data.get("createdAt"));
            long amount=free?0:number(data.get("price"))+number(data.get("shippingFee"));
            rows.add(new vn.edu.ueh.thudinh.fashion2hand.adapter.TransactionHistoryAdapter.Row(doc.getId(),text(data,"postId"),title(data),meta,label(text(data,"orderStatus")),free?"Miễn phí":money(amount),historyBadge(data,historyMoney.get(doc.getId()))));
        }
        historyAdapter.submitList(rows);
        TextView state=a.findViewById(R.id.historyState);
        String message=!historyError.isEmpty()?historyError:!historyBuyerLoaded||!historySellerLoaded?(rows.isEmpty()?"Đang tải lịch sử…":"Dữ liệu đã lưu · đang đồng bộ"):rows.isEmpty()?"Chưa có giao dịch phù hợp bộ lọc.":"";
        state.setText(message);state.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void loadHistoryPhoto(String postId,String orderId,ImageView image,long token){
        if(postId.isEmpty())return;
        if(historyPhotos.containsKey(postId)){host.photo(historyPhotos.get(postId),image);return;}
        repo.getPost(postId).addOnSuccessListener(post->{
            if(!valid(token)||!post.exists()||post.getData()==null)return;
            historyPhotos.put(postId,post.getData());
            if(orderId.equals(image.getTag()))host.photo(post.getData(),image);
        });
    }
    private static long time(DocumentSnapshot doc) { Timestamp timestamp = doc.getTimestamp("createdAt"); return timestamp == null ? 0 : timestamp.toDate().getTime(); }
    private void moneyEvent(LinearLayout parent, DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData(); String type = text(data, "type");
        String description = "PAYMENT_RECEIVED".equals(type) ? "Thanh toán nhận vào" : "REFUND".equals(type) ? "Hoàn tiền" : "SELLER_PAYOUT".equals(type) ? "Giải ngân người bán" : type;
        String status = text(data, "status");
        info(parent, description + " · " + money(data.get("amount")) + " · " + (status.isEmpty() ? "Đã ghi nhận giao dịch" : label(status)) + "\n" + date(data.get("createdAt")) + " · " + text(data, "mode"));
    }

    void submitDispute(List<Object> evidence) {
        if (busy || !host.signedIn()) return;
        RadioGroup group = a.findViewById(R.id.rgReportReason); EditText description = a.findViewById(R.id.edtReportDescription);
        String reason = group.getCheckedRadioButtonId() == R.id.reportNotReceived ? "NOT_RECEIVED"
                : group.getCheckedRadioButtonId() == R.id.reportDamaged ? "DAMAGED"
                : group.getCheckedRadioButtonId() == R.id.reportMismatch ? "MISMATCH"
                : group.getCheckedRadioButtonId() == R.id.reportWrongItem ? "WRONG_ITEM"
                : group.getCheckedRadioButtonId() == R.id.reportOther ? "OTHER" : "";
        if (reason.isEmpty()) { error(new IllegalArgumentException("Chọn lý do báo cáo.")); return; }
        String detail = description.getText().toString().trim();
        if (detail.length() < 10 || detail.length() > 2000) { description.setError("Mô tả từ 10 đến 2000 ký tự"); return; }
        if (evidence.size() > 5) { error(new IllegalArgumentException("Tối đa 5 ảnh.")); return; }
        String context = host.uid() + ":" + host.orderId();
        if (!context.equals(disputeOrder)) { disputeOrder = context; uploadKey = UUID.randomUUID().toString(); uploadedEvidence.clear(); }
        Button submit = a.findViewById(R.id.btnSubmitReport); submit.setEnabled(false); busy = true;
        final String orderId = host.orderId(); final String uid = host.uid(); final long token = generation;
        ArrayList<Task<String>> uploads = new ArrayList<>();
        Task<List<String>> uploaded;
        if (uploadedEvidence.size() == evidence.size() && !uploadedEvidence.isEmpty()) uploaded = Tasks.forResult(new ArrayList<>(uploadedEvidence));
        else {
            for (int i = 0; i < evidence.size(); i++) {
                final Object item = evidence.get(i); final String path = "disputeEvidence/" + orderId + "/" + uid + "/" + uploadKey + "/" + i + ".jpg";
                uploads.add(Tasks.call(FILES, () -> evidenceBytes(item)).continueWithTask(bytes -> {
                    if (!bytes.isSuccessful()) throw bytes.getException();
                    return FirebaseStorage.getInstance().getReference().child(path).putBytes(bytes.getResult(), new StorageMetadata.Builder().setContentType("image/jpeg").build());
                }).continueWith(result -> { if (!result.isSuccessful()) throw result.getException(); return path; }));
            }
            uploaded = Tasks.whenAllSuccess(uploads);
        }
        uploaded.continueWithTask(result -> {
            if (!result.isSuccessful()) throw result.getException();
            uploadedEvidence = new ArrayList<>(result.getResult());
            return repo.createDispute(orderId, reason, detail, uploadKey, uploadedEvidence);
        }).addOnCompleteListener(result -> {
            if (!valid(token)) return; busy = false; submit.setEnabled(true);
            if (!result.isSuccessful()) { error(result.getException()); return; }
            Toast.makeText(a, "Đã lưu tranh chấp và bằng chứng.", Toast.LENGTH_LONG).show(); host.back();
        });
    }
    private byte[] evidenceBytes(Object item) throws java.io.IOException {
        Bitmap bitmap;
        if (item instanceof Bitmap) bitmap = (Bitmap) item;
        else if (item instanceof Uri) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (java.io.InputStream input = a.getContentResolver().openInputStream((Uri) item)) { BitmapFactory.decodeStream(input, null, options); }
            options.inSampleSize = 1; while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (java.io.InputStream input = a.getContentResolver().openInputStream((Uri) item)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        } else throw new java.io.IOException("Không đọc được ảnh bằng chứng.");
        if (bitmap == null) throw new java.io.IOException("Ảnh bằng chứng bị lỗi.");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes) || bytes.size() >= 5 * 1024 * 1024) throw new java.io.IOException("Ảnh phải nhỏ hơn 5 MB.");
        return bytes.toByteArray();
    }

    Bundle save() {
        if(activeDialog!=null && activeDialog.isShowing() && dialogForm!=null)dialogDraft=UiFormState.capture(dialogForm);
        Bundle state = new Bundle(); freeRequests.save(state); state.putString("context", checkoutContext); state.putString("key", checkoutKey); state.putString("address", addressId); state.putString("note", freeNote);
        state.putString("dialogType",dialogType);state.putBundle("dialogDraft",dialogDraft);state.putString("newAddressKey",newAddressKey);
        state.putStringArrayList("cartCheckoutIds", cartCheckoutIds);
        state.putInt("historyFilter", historyFilter); state.putBoolean("owner", ownerView); state.putString("disputeOrder", disputeOrder); state.putString("uploadKey", uploadKey); state.putStringArrayList("evidence", uploadedEvidence); return state;
    }
    void restore(Bundle state) {
        if (state == null) return; freeRequests.restore(state); checkoutContext = state.getString("context", ""); checkoutKey = state.getString("key", ""); addressId = state.getString("address", ""); freeNote = state.getString("note", "");
        dialogType=state.getString("dialogType","");dialogDraft=state.getBundle("dialogDraft");newAddressKey=state.getString("newAddressKey","");
        ArrayList<String> cartIds = state.getStringArrayList("cartCheckoutIds"); cartCheckoutIds = cartIds == null ? new ArrayList<>() : cartIds;
        historyFilter = state.getInt("historyFilter",0); ownerView = state.getBoolean("owner"); disputeOrder = state.getString("disputeOrder", ""); uploadKey = state.getString("uploadKey", "");
        ArrayList<String> evidence = state.getStringArrayList("evidence"); uploadedEvidence = evidence == null ? new ArrayList<>() : evidence;
    }
}
