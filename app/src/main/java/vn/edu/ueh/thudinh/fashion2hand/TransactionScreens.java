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
        void orderSnapshot(DocumentSnapshot order); void listen(ListenerRegistration listener);
        void error(Exception error); void chat(String peerId, String postId);
    }
    private final AppCompatActivity a;
    private final Host host;
    private final MemberTwoRepository repo = MemberTwoRepository.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ArrayList<Button> buttons = new ArrayList<>();
    private static final java.util.concurrent.Executor FILES = Executors.newFixedThreadPool(2);
    private LinearLayout content;
    private long generation;
    private boolean busy, ownerView;
    private int screen, historyFilter;
    private Map<String, Object> capabilities = new HashMap<>(), quote = new HashMap<>();
    private String checkoutContext = "", checkoutKey = "", addressId = "", freeNote = "";
    private final ArrayList<DocumentSnapshot> addresses = new ArrayList<>();
    private Map<String, Object> order = new HashMap<>(), payment = new HashMap<>(), shipment = new HashMap<>(), dispute = new HashMap<>();
    private List<DocumentSnapshot> timeline = new ArrayList<>(), ledger = new ArrayList<>();
    private String boundPayment = "", boundDispute = "";
    private boolean shipmentBound;
    private final Map<String, DocumentSnapshot> historyBuyer = new HashMap<>(), historySeller = new HashMap<>();
    private final Map<String, List<DocumentSnapshot>> historyMoney = new HashMap<>();
    private final Map<String, ListenerRegistration> moneyListeners = new HashMap<>();
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

    TransactionScreens(AppCompatActivity activity, Host host) { this.a = activity; this.host = host; }

    boolean owns(int layout) {
        return layout == R.layout.activity_transactions || layout == R.layout.activity_request_status
                || layout == R.layout.activity_requestfree || layout == R.layout.activity_cart
                || layout == R.layout.activity_checkout || layout == R.layout.activity_order_detail
                || layout == R.layout.activity_delivery || layout == R.layout.activity_delivery_map
                || layout == R.layout.activity_transaction_history || layout == R.layout.activity_cancel_order;
    }
    void invalidate() { generation++; busy = false; }
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
            case "OPEN": return "Tranh chấp đang chờ xử lý"; case "HOLDING": return "Đang giữ tiền";
            case "RELEASED": return "Đã giải ngân"; default: return status.isEmpty() ? "Chưa có dữ liệu" : status;
        }
    }

    void prepare(int layout) {
        screen = layout; generation++; busy = false; buttons.clear();
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
    private void reset() { content.removeAllViews(); buttons.clear(); }
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
    private void setBusy(boolean value) { busy = value; for (Button button : buttons) button.setEnabled(!value); }
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
        if (layout == R.layout.activity_transactions) bindRequests(false);
        else if (layout == R.layout.activity_request_status) bindRequests(true);
        else if (layout == R.layout.activity_checkout || layout == R.layout.activity_requestfree) bindCheckout(layout == R.layout.activity_requestfree);
        else if (layout == R.layout.activity_cart) bindCart();
        else if (layout == R.layout.activity_order_detail) bindOrder();
        else if (layout == R.layout.activity_delivery || layout == R.layout.activity_delivery_map) bindShipment(layout == R.layout.activity_delivery_map);
        else if (layout == R.layout.activity_transaction_history) bindHistory();
        else if (layout == R.layout.activity_cancel_order) {
            heading("Hủy đơn hàng"); info(content, "Chỉ người mua được hủy trước khi người bán chuẩn bị. Đơn đã thanh toán sẽ ghi hoàn tiền mô phỏng.");
            button(content, R.id.btnCancelOrder, "Xác nhận hủy đơn", () -> execute(repo.cancelOrder(host.orderId()), value -> host.back()));
        }
    }

    private void bindRequests(boolean detail) {
        heading(detail ? "Yêu cầu nhận đồ" : "Trung tâm giao dịch");
        long token = generation;
        if (detail) {
            host.listen(repo.listenFreeRequest(host.requestId(), (snapshot, error) -> {
                if (!valid(token)) return;
                reset(); if (error != null) { failure("Không tải được yêu cầu.", () -> host.open(screen)); return; }
                if (snapshot == null || !snapshot.exists()) { info(content, "Yêu cầu không còn tồn tại."); return; }
                requestCard(content, snapshot, true);
            }));
            return;
        }
        button(content, R.id.tabReceive, "Yêu cầu tôi đã gửi", () -> { ownerView = false; host.open(screen); });
        button(content, R.id.tabGive, "Yêu cầu nhận đồ của tôi", () -> { ownerView = true; host.open(screen); });
        button(content, R.id.transactionPurchaseRow, ownerView ? "Xem đơn đã bán / tặng" : "Xem đơn đã mua / nhận", () -> host.orders(ownerView));
        button(content, R.id.btnTransactionHistory, "Lịch sử giao dịch", () -> host.open(R.layout.activity_transaction_history));
        LinearLayout list = new LinearLayout(a); list.setId(R.id.freeRequestList); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        info(list, "Đang tải yêu cầu…");
        com.google.firebase.firestore.EventListener<QuerySnapshot> listener = (snapshot, error) -> {
            if (!valid(token)) return;
            list.removeAllViews();
            if (error != null) { info(list, "Không tải được yêu cầu."); button(list, 0, "Thử lại", () -> host.open(screen)); return; }
            if(snapshot!=null && snapshot.getMetadata().isFromCache()){
                info(list,"Đang chờ kết nối với server; danh sách có thể chưa cập nhật.");button(list,0,"Thử lại",()->host.open(screen));if(snapshot.isEmpty())return;
            }
            int count = snapshot == null ? 0 : snapshot.size(); section(list, count + " yêu cầu · " + (ownerView ? "Người tặng" : "Người nhận"));
            if (count == 0) info(list, "Chưa có yêu cầu nhận đồ.");
            else for (DocumentSnapshot request : snapshot.getDocuments()) requestCard(list, request, false);
        };
        host.listen(ownerView ? repo.listenFreeRequestsAsOwner(host.uid(), listener) : repo.listenFreeRequestsAsRequester(host.uid(), listener));
    }
    private void requestCard(LinearLayout parent, DocumentSnapshot request, boolean detail) {
        Map<String, Object> data = request.getData(); if (data == null) return;
        LinearLayout card = card(parent); card.setTag("request|" + request.getId());
        section(card, title(data)); info(card, "Yêu cầu #" + request.getId());
        info(card, label(text(data, "status")) + " · " + date(data.get("createdAt")));
        info(card, text(data, "note"));
        boolean owner = host.uid().equals(text(data, "ownerId"));
        info(card, owner ? "Người yêu cầu: " + text(data, "requesterId") : "Người đăng: " + text(data, "ownerId"));
        if ("PENDING".equals(text(data, "status"))) {
            if (owner) {
                button(card, R.id.btnAcceptFreeRequest, "Chấp nhận yêu cầu", () -> execute(repo.respondFreeRequest(request.getId(), true), result -> host.openOrder(text(map(result), "orderId"))));
                button(card, R.id.btnRejectFreeRequest, "Từ chối yêu cầu", () -> execute(repo.respondFreeRequest(request.getId(), false), result -> { if (detail) host.open(screen); }));
            } else button(card, R.id.btnCancelFreeRequest, "Hủy yêu cầu", () -> execute(repo.cancelFreeRequest(request.getId()), result -> { if (detail) host.open(screen); }));
        }
        if (!text(data, "orderId").isEmpty()) button(card, 0, "Xem đơn hàng", () -> host.openOrder(text(data, "orderId")));
        if (!detail) button(card, 0, "Chi tiết yêu cầu", () -> host.openRequest(request.getId()));
    }

    private void bindCart() {
        heading("Giỏ hàng"); info(content, "Mỗi đơn gồm một món. Chọn Thanh toán tại món bạn muốn mua; các món khác vẫn ở trong giỏ.");
        LinearLayout list = new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL); content.addView(list); long token = generation;
        host.listen(repo.listenCart(host.uid(), (snapshot, error) -> {
            if (!valid(token)) return; list.removeAllViews();
            if (error != null) { failure("Không tải được giỏ hàng.", () -> host.open(screen)); return; }
            if(snapshot!=null && snapshot.getMetadata().isFromCache()){
                info(list,"Đang chờ kết nối với server; dữ liệu lưu trên thiết bị có thể chưa cập nhật.");
                button(list,0,"Thử lại",()->host.open(screen));
                if(snapshot.isEmpty())return;
            }
            if (snapshot == null || snapshot.isEmpty()) info(list, "Giỏ hàng đang trống.");
            else for (DocumentSnapshot item : snapshot.getDocuments()) {
                LinearLayout card = card(list); card.setTag("cart|" + item.getId()); section(card, text(item.getData(), "title"));
                info(card, "Giá đã lưu: " + money(item.get("price")) + ". Giá và tình trạng sẽ được server kiểm tra lại.");
                button(card, 0, "Thanh toán món này", () -> host.checkout(item.getId()));
                button(card, 0, "Xóa khỏi giỏ", () -> execute(repo.removeCartItem(host.uid(), item.getId()), value -> { }));
            }
        }));
    }
    private void bindCheckout(boolean free) {
        heading(free ? "Gửi yêu cầu nhận đồ" : "Checkout một món");
        String context = host.uid() + ":" + host.postId();
        if (!context.equals(checkoutContext)) { checkoutContext = context; checkoutKey = UUID.randomUUID().toString(); quote.clear(); addressId = ""; freeNote = ""; }
        long token = generation;
        quote.clear(); info(content, "Đang lấy giá và phí vận chuyển từ server…");
        repo.quote(host.postId()).addOnSuccessListener(result -> {
            if (!valid(token)) return; quote = map(result.getData());
            if (!text(quote, "existingOrderId").isEmpty()) { host.openOrder(text(quote, "existingOrderId")); return; }
            if (free != "FREE".equals(text(quote, "type"))) { failure("Loại bài đăng không phù hợp với thao tác này.", host::back); return; }
            renderCheckout(free);
        }).addOnFailureListener(error -> { if (valid(token)) { failure(error.getLocalizedMessage(), () -> host.open(screen)); } });
        host.listen(db.collection("users").document(host.uid()).collection("addresses").addSnapshotListener((snapshot, error) -> {
            if (!valid(token)) return;
            if (error != null) { error(error); return; }
            addresses.clear(); if (snapshot != null) addresses.addAll(snapshot.getDocuments());
            if (addresses.stream().noneMatch(address -> address.getId().equals(addressId))) {
                addressId = addresses.isEmpty() ? "" : addresses.get(0).getId();
                for (DocumentSnapshot address : addresses) if (Boolean.TRUE.equals(address.getBoolean("isDefault"))) addressId = address.getId();
            }
            if (!quote.isEmpty() && text(quote, "existingOrderId").isEmpty()) renderCheckout(free);
        }));
    }
    private String addressLabel(DocumentSnapshot address) {
        Map<String, Object> data = address.getData(); if (data == null) return address.getId();
        return text(data, "recipient") + " · " + text(data, "phone") + "\n" + text(data, "street") + ", " + text(data, "ward") + ", " + text(data, "district") + ", " + text(data, "province");
    }
    private void renderCheckout(boolean free) {
        reset(); LinearLayout summary = card(content); section(summary, text(quote, "title"));
        info(summary, "Giá món đồ: " + money(quote.get("price")));
        info(summary, "Phí vận chuyển: " + money(quote.get("shippingFee")));
        TextView total = info(summary, "Tổng dự kiến: " + money(quote.get("total"))); total.setId(R.id.txtCheckoutQuote);
        info(summary, free ? "Món đồ được tặng miễn phí. Phí vận chuyển hiển thị để tham khảo; app chưa thu phí này."
                : "Môi trường kiểm thử: chưa kết nối thanh toán thật. Mỗi món tạo một đơn riêng.");
        LinearLayout destination = card(content); section(destination, "Địa chỉ nhận hàng");
        if (addresses.isEmpty()) info(destination, "Chưa có địa chỉ. Thêm địa chỉ để tiếp tục.");
        else {
            Spinner spinner = new Spinner(a); spinner.setId(R.id.addressChoice);
            ArrayList<String> choices = new ArrayList<>(); int selected = 0;
            for (int i = 0; i < addresses.size(); i++) { choices.add(addressLabel(addresses.get(i))); if (addresses.get(i).getId().equals(addressId)) selected = i; }
            spinner.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, choices)); spinner.setSelection(selected); destination.addView(spinner);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(AdapterView<?> parent) { }
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (position < addresses.size()) addressId = addresses.get(position).getId(); }
            });
        }
        button(destination, R.id.btnNewCheckoutAddress, "Thêm địa chỉ", this::newAddress);
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
            if (free) execute(repo.createFreeRequest(host.postId(), freeNote, addressId), value -> host.openRequest(text(map(value), "requestId")));
            else {
                final String postId = host.postId(); final boolean fromCart = host.checkoutFromCart();
                execute(repo.checkout(postId, checkoutKey, addressId, number(quote.get("price")), number(quote.get("shippingFee"))), value -> {
                    String id = text(map(value), "orderId"); if (id.isEmpty()) { error(new IllegalStateException("Server không trả mã đơn hàng.")); return; }
                    if (fromCart) repo.removeCartItem(host.uid(), postId).addOnFailureListener(this::error);
                    host.openOrder(id);
                });
            }
        }); submit.setEnabled(!busy && !addressId.isEmpty());restoreDialog();
    }
    private void newAddress() {
        LinearLayout form = new LinearLayout(a); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(16), dp(8), dp(16), 0);
        String[] keys = {"recipient", "phone", "province", "district", "ward", "street"};
        String[] hints = {"Tên người nhận", "Số điện thoại", "Tỉnh / thành phố", "Quận / huyện", "Phường / xã", "Số nhà, đường"};
        EditText[] inputs = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) inputs[i] = input(form, 0, hints[i], "");
        inputs[1].setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        ScrollView scroll = new ScrollView(a); scroll.addView(form);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a).setTitle("Thêm địa chỉ nhận hàng").setView(scroll)
                .setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        trackDialog("address",dialog,form);if(newAddressKey.isEmpty())newAddressKey=UUID.randomUUID().toString();
        long token = generation;
        dialog.setOnShowListener(unused -> dialog.getButton(-1).setOnClickListener(v -> {
            Map<String, Object> address = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                String value = inputs[i].getText().toString().trim(); if (value.isEmpty()) { inputs[i].setError("Vui lòng nhập thông tin"); return; } address.put(keys[i], value);
            }
            if (MarketplaceLinks.normalizePhone(String.valueOf(address.get("phone"))).isEmpty()) { inputs[1].setError("Số điện thoại không hợp lệ"); return; }
            dialog.getButton(-1).setEnabled(false); DocumentReference ref = db.collection("users").document(host.uid()).collection("addresses").document(newAddressKey);
            address.put("isDefault", addresses.isEmpty()); address.put("createdAt", FieldValue.serverTimestamp());
            ref.set(address).addOnSuccessListener(value -> { if (valid(token)) { addressId = ref.getId(); newAddressKey="";dialog.dismiss(); host.open(screen); } })
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
        info(content, label(status) + " · " + date(order.get("createdAt")));
        LinearLayout paymentCard = card(content); section(paymentCard, "Thanh toán");
        TextView payState = info(paymentCard, label(text(order, "paymentStatus"))); payState.setId(R.id.txtLivePaymentState);
        info(paymentCard, "Giá món: " + money(order.get("price")) + " · Phí giao: " + money(order.get("shippingFee")));
        info(paymentCard, "Tổng: " + money(number(order.get("price")) + number(order.get("shippingFee"))));
        info(paymentCard, "FREE".equals(text(order, "orderType")) ? "Món tặng không thu tiền qua app; phí giao hàng chưa được thu."
                : "THANH TOÁN MÔ PHỎNG — không chuyển tiền thật.");
        if (order.get("paymentDeadline") != null && "PENDING_PAYMENT".equals(status)) info(paymentCard, "Hạn thanh toán: " + date(order.get("paymentDeadline")));
        if (!payment.isEmpty()) info(paymentCard, "Mã thanh toán: " + boundPayment + " · " + text(payment, "provider") + "\nTrạng thái bản ghi: " + label(text(payment, "status")));
        if (buyer && "PENDING_PAYMENT".equals(status)) {
            if (Boolean.TRUE.equals(capabilities.get("simulatedPayments"))) {
                button(paymentCard, R.id.btnSimulatePaid, "Mô phỏng thanh toán thành công", () -> execute(repo.simulatePaymentResult(host.orderId(), true), value -> { }));
                button(paymentCard, R.id.btnSimulatePaymentFailed, "Mô phỏng thanh toán thất bại", () -> execute(repo.simulatePaymentResult(host.orderId(), false), value -> { }));
            } else info(paymentCard, "Chưa bật thanh toán mô phỏng trên Emulator; chưa cấu hình cổng thanh toán thật.");
        }
        LinearLayout events = card(content); events.setId(R.id.liveOrderTimeline); section(events, "Timeline đơn hàng");
        if (timeline.isEmpty()) info(events, "Chưa có sự kiện được ghi nhận.");
        for (DocumentSnapshot event : timeline) {
            Map<String, Object> data = event.getData(); info(events, date(data.get("createdAt")) + " · " + label(text(data, "to")) + "\n" + text(data, "reason"));
        }
        LinearLayout money = card(content); money.setId(R.id.liveMoneyEvents); section(money, "Bút toán từ backend");
        if (ledger.isEmpty()) info(money, "Chưa phát sinh bút toán.");
        for (DocumentSnapshot event : ledger) moneyEvent(money, event);
        LinearLayout shipping = card(content); section(shipping, "Vận chuyển");
        if (shipment.isEmpty()) info(shipping, "Chưa có vận đơn."); else {
            info(shipping, text(shipment, "trackingCode") + " · " + label(text(shipment, "status")));
            button(shipping, R.id.btnTrackShipping, "Chi tiết vận đơn và lịch sử", () -> host.open(R.layout.activity_delivery));
        }
        Map<String, Object> address = map(order.get("shippingAddress"));
        info(shipping, address.isEmpty() ? "Đơn cũ chưa lưu địa chỉ." : text(address, "recipient") + " · " + text(address, "phone") + "\n" + text(address, "street") + ", " + text(address, "ward") + ", " + text(address, "district") + ", " + text(address, "province"));
        if (!buyer) {
            if ("CONFIRMED".equals(status)) button(content, R.id.btnSellerConfirm, "Xác nhận đơn và chuẩn bị hàng", () -> execute(repo.updateOrderStatus(host.orderId(), "PREPARING"), value -> { }));
            if ("PREPARING".equals(status)) button(content, R.id.btnSellerReady, "Đã chuẩn bị — sẵn sàng giao", () -> execute(repo.updateOrderStatus(host.orderId(), "READY_TO_SHIP"), value -> { }));
            if ("PENDING_PAYMENT".equals(status) || "CONFIRMED".equals(status)) button(content, R.id.btnSellerReject, "Từ chối đơn hàng", this::rejectOrder);
            if ("READY_TO_SHIP".equals(status)) {
                if (Boolean.TRUE.equals(capabilities.get("simulatedShipping"))) button(content, R.id.btnCreateShipment, "Tạo vận đơn mô phỏng", () -> execute(repo.createShipment(host.orderId(), "MOCK_CARRIER", ""), value -> { }));
                else info(content, "Chưa bật vận chuyển mô phỏng; chưa kết nối hãng giao hàng thật.");
            }
            if ("SHIPPING".equals(status) && Boolean.TRUE.equals(capabilities.get("simulatedShipping"))) {
                button(content, R.id.btnSimulateLocation, "Cập nhật vị trí mô phỏng", this::locationForm);
                button(content, R.id.btnSimulateDelivered, "Mô phỏng hãng giao thành công", () -> execute(repo.simulateShipmentUpdate(host.orderId(), "DELIVERED", "Đã giao hàng trong môi trường mô phỏng"), value -> { }));
                button(content, R.id.btnSimulateReturned, "Mô phỏng hoàn hàng", () -> execute(repo.simulateShipmentUpdate(host.orderId(), "RETURNED", "Hoàn hàng trong môi trường mô phỏng"), value -> { }));
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
        restoreDialog();
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
        heading(mapPage ? "Vị trí vận chuyển" : "Chi tiết vận đơn"); long token = generation; info(content, "Đang tải vận đơn…");
        host.listen(repo.listenShipment(host.orderId(), (snapshot, error) -> {
            if (!valid(token)) return;
            if (error != null) { failure("Không tải được vận đơn.", () -> host.open(screen)); return; }
            reset();
            if (snapshot == null || !snapshot.exists()) { info(content, "Đơn này chưa có vận đơn."); return; }
            Map<String, Object> data = snapshot.getData(); section(content, text(data, "trackingCode"));
            info(content, text(data, "carrier") + " · " + ("SIMULATED".equals(text(data, "mode")) || "MOCK_CARRIER".equals(text(data, "carrier")) ? "VẬN CHUYỂN MÔ PHỎNG" : "Dữ liệu hãng vận chuyển"));
            TextView status = info(content, label(text(data, "status"))); status.setId(R.id.txtLiveShipmentState);
            Map<String, Object> location = map(data.get("location"));
            if (mapPage) {
                Object latitude = location.get("lat"), longitude = location.get("lng");
                if (!(latitude instanceof Number) || !(longitude instanceof Number)) info(content, "Chưa có vị trí từ backend. App không vẽ tuyến đường hoặc vị trí ước lượng.");
                else {
                    double lat = ((Number) latitude).doubleValue(), lng = ((Number) longitude).doubleValue();
                    if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) { info(content, "Tọa độ không hợp lệ."); return; }
                    info(content, "SIMULATED".equals(text(location, "source")) ? "Tọa độ kiểm thử do người bán nhập; không phải vị trí giao hàng thật." : "Vị trí cập nhật từ hãng vận chuyển.");
                    info(content, lat + ", " + lng + "\nCập nhật: " + date(location.get("recordedAt")));
                    button(content, R.id.btnOpenTrackingMap, "Mở vị trí này trên bản đồ", () -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng));
                        try { a.startActivity(intent); }
                        catch (android.content.ActivityNotFoundException missing) {
                            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lng + "#map=16/" + lat + "/" + lng))); }
                            catch (android.content.ActivityNotFoundException absent) { error(absent); }
                        }
                    });
                }
            } else {
                button(content, R.id.btnOpenDeliveryMap, "Xem vị trí từ backend", () -> host.open(R.layout.activity_delivery_map));
                LinearLayout events = card(content); events.setId(R.id.liveShipmentEvents); section(events, "Lịch sử vận chuyển");
                List<?> records = data.get("events") instanceof List ? (List<?>) data.get("events") : Collections.emptyList();
                if (records.isEmpty()) info(events, "Chưa có sự kiện vận chuyển.");
                for (Object raw : records) { Map<String, Object> event = map(raw); info(events, date(event.get("createdAt")) + " · " + label(text(event, "status")) + "\n" + text(event, "description")); }
            }
        }));
    }

    private void bindHistory() {
        heading("Lịch sử giao dịch"); historyBuyer.clear(); historySeller.clear(); historyMoney.clear(); moneyListeners.clear(); historyFilter = 0;
        long token = generation;
        host.listen(repo.listenOrdersAsBuyer(host.uid(), (snapshot, error) -> { if (valid(token)) historyChanged(historyBuyer, snapshot, error, token); }));
        host.listen(repo.listenOrdersAsSeller(host.uid(), (snapshot, error) -> { if (valid(token)) historyChanged(historySeller, snapshot, error, token); }));
        renderHistory();
    }
    private void historyChanged(Map<String, DocumentSnapshot> target, QuerySnapshot snapshot, Exception error, long token) {
        if (error != null) { error(error); return; }
        target.clear(); if (snapshot != null) for (DocumentSnapshot doc : snapshot.getDocuments()) target.put(doc.getId(), doc);
        Map<String, DocumentSnapshot> all = new HashMap<>(historyBuyer); all.putAll(historySeller);
        for (String id : new ArrayList<>(moneyListeners.keySet())) if (!all.containsKey(id)) { moneyListeners.remove(id).remove(); historyMoney.remove(id); }
        for (String id : all.keySet()) if (!moneyListeners.containsKey(id)) {
            ListenerRegistration listener = repo.listenMoneyTransactions(id, (money, moneyError) -> {
                if (!valid(token)) return; if (moneyError != null) { error(moneyError); return; }
                historyMoney.put(id, money == null ? new ArrayList<>() : money.getDocuments()); renderHistory();
            }); moneyListeners.put(id, listener); host.listen(listener);
        }
        renderHistory();
    }
    private void renderHistory() {
        reset(); String[] filters = {"Tất cả", "Mua", "Bán", "Đồ tặng"};
        int[] ids = {R.id.filterHistoryAll, R.id.filterHistoryBuy, R.id.filterHistorySell, R.id.filterHistoryFree};
        for (int i = 0; i < filters.length; i++) { final int index = i; button(content, ids[i], (historyFilter == i ? "✓ " : "") + filters[i], () -> { historyFilter = index; renderHistory(); }); }
        LinearLayout list = new LinearLayout(a); list.setId(R.id.transactionHistoryList); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        Map<String, DocumentSnapshot> all = new HashMap<>(historyBuyer); all.putAll(historySeller);
        ArrayList<DocumentSnapshot> rows = new ArrayList<>(all.values()); rows.sort((left, right) -> Long.compare(time(right), time(left)));
        int shown = 0;
        for (DocumentSnapshot doc : rows) {
            Map<String, Object> data = doc.getData(); boolean free = "FREE".equals(text(data, "orderType")); boolean buyer = host.uid().equals(text(data, "buyerId"));
            if (historyFilter == 1 && (free || !buyer) || historyFilter == 2 && (free || buyer) || historyFilter == 3 && !free) continue;
            shown++; LinearLayout panel = card(list); panel.setTag("history|" + doc.getId()); section(panel, title(data));
            info(panel, "#" + doc.getId() + " · " + date(data.get("createdAt")) + "\n" + (buyer ? "Mua / nhận" : "Bán / tặng") + " · " + label(text(data, "orderStatus")));
            info(panel, "Thanh toán: " + label(text(data, "paymentStatus")) + " · Giá món: " + money(data.get("price")) + " · Phí giao: " + money(data.get("shippingFee")));
            List<DocumentSnapshot> events = historyMoney.get(doc.getId());
            if (events == null) info(panel, "Đang tải bút toán…"); else if (events.isEmpty()) info(panel, "Chưa có bút toán thanh toán qua app."); else for (DocumentSnapshot event : events) moneyEvent(panel, event);
            button(panel, 0, "Chi tiết đơn hàng", () -> host.openOrder(doc.getId()));
        }
        if (shown == 0) info(list, "Chưa có giao dịch phù hợp bộ lọc.");
    }
    private static long time(DocumentSnapshot doc) { Timestamp timestamp = doc.getTimestamp("createdAt"); return timestamp == null ? 0 : timestamp.toDate().getTime(); }
    private void moneyEvent(LinearLayout parent, DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData(); String type = text(data, "type");
        String description = "PAYMENT_RECEIVED".equals(type) ? "Thanh toán nhận vào" : "REFUND".equals(type) ? "Hoàn tiền" : "SELLER_PAYOUT".equals(type) ? "Giải ngân người bán" : type;
        String status = text(data, "status");
        info(parent, description + " · " + money(data.get("amount")) + " · " + (status.isEmpty() ? "Đã ghi nhận bút toán" : label(status)) + "\n" + date(data.get("createdAt")) + " · " + text(data, "mode"));
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
        Bundle state = new Bundle(); state.putString("context", checkoutContext); state.putString("key", checkoutKey); state.putString("address", addressId); state.putString("note", freeNote);
        state.putString("dialogType",dialogType);state.putBundle("dialogDraft",dialogDraft);state.putString("newAddressKey",newAddressKey);
        state.putBoolean("owner", ownerView); state.putString("disputeOrder", disputeOrder); state.putString("uploadKey", uploadKey); state.putStringArrayList("evidence", uploadedEvidence); return state;
    }
    void restore(Bundle state) {
        if (state == null) return; checkoutContext = state.getString("context", ""); checkoutKey = state.getString("key", ""); addressId = state.getString("address", ""); freeNote = state.getString("note", "");
        dialogType=state.getString("dialogType","");dialogDraft=state.getBundle("dialogDraft");newAddressKey=state.getString("newAddressKey","");
        ownerView = state.getBoolean("owner"); disputeOrder = state.getString("disputeOrder", ""); uploadKey = state.getString("uploadKey", "");
        ArrayList<String> evidence = state.getStringArrayList("evidence"); uploadedEvidence = evidence == null ? new ArrayList<>() : evidence;
    }
}
