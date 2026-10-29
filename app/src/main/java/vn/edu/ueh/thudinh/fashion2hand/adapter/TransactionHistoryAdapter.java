package vn.edu.ueh.thudinh.fashion2hand.adapter;

import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.*;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import vn.edu.ueh.thudinh.fashion2hand.R;

/** Presentation only; photos and navigation are supplied by the screen controller. */
public final class TransactionHistoryAdapter extends ListAdapter<TransactionHistoryAdapter.Row, TransactionHistoryAdapter.Holder> {
    public static final class Row {
        public final String id, postId, title, meta, status, amount, badge;
        public Row(String id, String postId, String title, String meta, String status, String amount, String badge) {
            this.id=id; this.postId=postId; this.title=title; this.meta=meta; this.status=status; this.amount=amount; this.badge=badge;
        }
    }
    private final Consumer<Row> open;
    private final BiConsumer<Row,ImageView> photo;
    public TransactionHistoryAdapter(Consumer<Row> open, BiConsumer<Row,ImageView> photo) {
        super(new DiffUtil.ItemCallback<Row>() {
            public boolean areItemsTheSame(Row a,Row b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(Row a,Row b){return a.title.equals(b.title)&&a.meta.equals(b.meta)&&a.status.equals(b.status)&&a.amount.equals(b.amount)&&a.badge.equals(b.badge)&&a.postId.equals(b.postId);}
        });
        this.open=open;this.photo=photo;
    }
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_history,parent,false));}
    @Override public void onBindViewHolder(Holder holder,int position){
        Row row=getItem(position);
        holder.title.setText(row.title);holder.meta.setText(row.meta);holder.status.setText(row.status);holder.amount.setText(row.amount);holder.badge.setText(row.badge);
        holder.itemView.setTag("history|"+row.id);holder.itemView.setContentDescription(row.title+", "+row.status+", "+row.badge+", mã đơn "+row.id);
        holder.itemView.setOnClickListener(view->open.accept(row));
        if(!Objects.equals(holder.image.getTag(),row.id)){
            holder.image.setTag(row.id);holder.image.setImageResource(R.drawable.img_product_placeholder);photo.accept(row,holder.image);
        }
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title,meta,status,amount,badge;final ImageView image;
        Holder(View root){super(root);title=root.findViewById(R.id.historyProductTitle);meta=root.findViewById(R.id.historyMeta);status=root.findViewById(R.id.historyOrderStatus);amount=root.findViewById(R.id.historyAmount);badge=root.findViewById(R.id.historyPaymentBadge);image=root.findViewById(R.id.historyProductImage);}
    }
}
