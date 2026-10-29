package vn.edu.ueh.thudinh.fashion2hand.adapter;

import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.Message;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MessageAdapter extends ListAdapter<Message, MessageAdapter.Holder> {
    private final String uid;
    public MessageAdapter(String uid) {
        super(new DiffUtil.ItemCallback<Message>() {
            public boolean areItemsTheSame(Message a, Message b) { return a.id.equals(b.id); }
            public boolean areContentsTheSame(Message a, Message b) {
                return a.text.equals(b.text) && a.senderId.equals(b.senderId) && a.read == b.read
                        && Objects.equals(a.createdAt, b.createdAt);
            }
        });
        this.uid = uid;
    }
    @Override public int getItemViewType(int position) { return uid.equals(getItem(position).senderId) ? 1 : 0; }
    @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(type == 1
                ? R.layout.item_message_sent : R.layout.item_message_received, parent, false));
    }
    @Override public void onBindViewHolder(Holder holder, int position) {
        Message message = getItem(position);
        holder.content.setText(message.text);
        String time = message.createdAt == null ? "Đang đồng bộ" : new SimpleDateFormat("dd/MM HH:mm", new Locale("vi", "VN")).format(message.createdAt.toDate());
        if (uid.equals(message.senderId)) time += message.read ? " · Đã đọc" : " · Đã gửi";
        holder.time.setText(time);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView content, time;
        Holder(View item) { super(item); content = item.findViewById(R.id.tvMessageContent); time = item.findViewById(R.id.tvMessageTime); }
    }
}
