package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;
import java.util.*;

/** Live, owner-scoped inbox. Read state is stored in Firestore, never preferences. */
final class NotificationInbox {
    interface Host {
        String uid();
        void listen(ListenerRegistration registration);
        String filter();
        void filter(String value);
        boolean hideRead();
        void hideRead(boolean value);
        void open(DocumentSnapshot notification);
    }

    private final Activity activity;
    private final Host host;
    private final CollectionReference inbox;
    private final LinearLayout list;
    private final TextView status;
    private final Button retry;
    private final View more;
    private final List<DocumentSnapshot> documents = new ArrayList<>();
    private final Set<String> reading = new HashSet<>();
    private ListenerRegistration listener;
    private boolean bulkBusy, cached, loaded;
    private String error;
    private final int[] chips = {R.id.filterNotifAll, R.id.filterNotifOrders, R.id.filterNotifMessages,
            R.id.filterNotifRequests, R.id.filterNotifPayments};
    private final String[] filters = {"all", "orders", "messages", "requests", "payments"};

    NotificationInbox(Activity activity, Host host) {
        this(activity, host, FirebaseFirestore.getInstance());
    }

    NotificationInbox(Activity activity, Host host, FirebaseFirestore firestore) {
        this.activity = activity;
        this.host = host;
        inbox = firestore.collection("users").document(host.uid()).collection("notifications");
        list = activity.findViewById(R.id.notificationList);
        status = activity.findViewById(R.id.notificationStatus);
        retry = activity.findViewById(R.id.notificationRetry);
        more = activity.findViewById(R.id.btnNotificationsMore);
    }

    private boolean active() {
        return !activity.isDestroyed() && list == activity.findViewById(R.id.notificationList);
    }

    void bind() {
        for (int i = 0; i < chips.length; i++) {
            final String filter = filters[i];
            activity.findViewById(chips[i]).setOnClickListener(v -> { host.filter(filter); render(); });
        }
        retry.setOnClickListener(v -> subscribe());
        more.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setItems(new String[]{"Đánh dấu tất cả đã đọc", host.hideRead() ? "Hiện thông báo đã đọc" : "Ẩn thông báo đã đọc"},
                        (dialog, which) -> {
                            if (!active()) return;
                            if (which == 0) markAllRead();
                            else { host.hideRead(!host.hideRead()); render(); }
                        }).show());
        subscribe();
    }

    private void subscribe() {
        if (listener != null) listener.remove();
        error = null;
        loaded = false;
        documents.clear();
        render();
        listener = inbox.orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, failure) -> {
                    if (!active()) return;
                    if (failure != null) {
                        error = "Không tải được thông báo. Kiểm tra kết nối và thử lại.";
                    } else if (snapshot != null) {
                        error = null;
                        loaded = true;
                        cached = snapshot.getMetadata().isFromCache();
                        documents.clear();
                        documents.addAll(snapshot.getDocuments());
                    }
                    render();
                });
        host.listen(listener);
    }

    static String category(String type) {
        if (type == null) return "other";
        if (type.equals("MESSAGE")) return "messages";
        if (type.startsWith("FREE_REQUEST")) return "requests";
        if (type.startsWith("PAYMENT") || type.startsWith("REFUND")) return "payments";
        if (type.startsWith("ORDER") || type.startsWith("SHIPMENT") || type.startsWith("DISPUTE")) return "orders";
        return "other";
    }

    private void render() {
        if (!active()) return;
        for (int i = 0; i < chips.length; i++) {
            TextView chip = activity.findViewById(chips[i]);
            boolean selected = filters[i].equals(host.filter());
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF173B32);
        }
        retry.setVisibility(error != null || cached ? View.VISIBLE : View.GONE);
        list.removeAllViews();
        for (DocumentSnapshot document : documents) {
            boolean read = Boolean.TRUE.equals(document.getBoolean("read"));
            if (host.hideRead() && read) continue;
            if (!"all".equals(host.filter()) && !host.filter().equals(category(document.getString("type")))) continue;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setBackgroundColor(read ? 0xFFFFFFFF : 0xFFEAF3ED);
            row.setTag(document.getId());
            TextView title = text((read ? "" : "●  ") + value(document, "title", "Thông báo"), 14, 0xFF173B32);
            title.setTypeface(null, read ? Typeface.NORMAL : Typeface.BOLD);
            row.addView(title);
            row.addView(text(value(document, "body", ""), 13, 0xFF52665E));
            com.google.firebase.Timestamp timestamp = document.getTimestamp("createdAt");
            if (timestamp != null) row.addView(text(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp.toDate()), 11, 0xFF75857F));
            row.setEnabled(!bulkBusy && !reading.contains(document.getId()));
            row.setOnClickListener(v -> markReadAndOpen(document));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            list.addView(row, params);
        }
        more.setEnabled(!bulkBusy);
        String message = error != null ? error : !loaded ? "Đang tải thông báo…"
                : cached ? "Đang chờ kết nối backend · Dữ liệu lưu trên thiết bị có thể chưa cập nhật."
                : list.getChildCount() == 0 ? ("all".equals(host.filter()) && !host.hideRead()
                    ? "Bạn chưa có thông báo." : "Không có thông báo phù hợp bộ lọc.") : "";
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void markReadAndOpen(DocumentSnapshot document) {
        if (bulkBusy || !reading.add(document.getId())) return;
        if (Boolean.TRUE.equals(document.getBoolean("read"))) {
            reading.remove(document.getId());
            host.open(document);
            return;
        }
        render();
        document.getReference().update(readFields()).addOnCompleteListener(task -> {
            reading.remove(document.getId());
            if (!active()) return;
            render();
            if (task.isSuccessful()) host.open(document);
            else Toast.makeText(activity, "Chưa lưu được trạng thái đã đọc. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
        });
    }

    private void markAllRead() {
        if (bulkBusy || !reading.isEmpty()) return;
        List<DocumentSnapshot> unread = new ArrayList<>();
        for (DocumentSnapshot document : documents)
            if (!Boolean.TRUE.equals(document.getBoolean("read"))) unread.add(document);
        if (unread.isEmpty()) return;
        bulkBusy = true;
        render();
        List<Task<Void>> tasks = new ArrayList<>();
        // Stay below Firestore's write-batch limit; do not truncate a large inbox.
        for (int i = 0; i < unread.size(); i += 450) {
            WriteBatch batch = inbox.getFirestore().batch();
            for (int j = i; j < Math.min(i + 450, unread.size()); j++) batch.update(unread.get(j).getReference(), readFields());
            tasks.add(batch.commit());
        }
        Tasks.whenAll(tasks).addOnCompleteListener(task -> {
            bulkBusy = false;
            if (!active()) return;
            render();
            Toast.makeText(activity, task.isSuccessful() ? "Đã đánh dấu tất cả đã đọc."
                    : "Có thông báo chưa lưu được trạng thái. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
        });
    }

    private Map<String, Object> readFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("read", true);
        fields.put("readAt", FieldValue.serverTimestamp());
        return fields;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private String value(DocumentSnapshot doc, String field, String fallback) {
        String value = doc.getString(field);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
