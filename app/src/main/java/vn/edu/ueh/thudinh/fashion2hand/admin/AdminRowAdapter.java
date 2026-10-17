package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import vn.edu.ueh.thudinh.fashion2hand.R;

/** Adapter dùng chung cho mọi danh sách Admin. */
public class AdminRowAdapter extends RecyclerView.Adapter<AdminRowAdapter.RowHolder> {

    public interface OnRowClickListener {
        void onRowClick(AdminRow row);
    }

    private final List<AdminRow> rows = new ArrayList<>();
    private final OnRowClickListener listener;

    public AdminRowAdapter(OnRowClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<AdminRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_row, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class RowHolder extends RecyclerView.ViewHolder {
        private final TextView leading;
        private final TextView title;
        private final TextView subtitle;
        private final TextView chip;
        private final TextView chip2;
        private final TextView trailing;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            leading = itemView.findViewById(R.id.txtRowLeading);
            title = itemView.findViewById(R.id.txtRowTitle);
            subtitle = itemView.findViewById(R.id.txtRowSubtitle);
            chip = itemView.findViewById(R.id.txtRowChip);
            chip2 = itemView.findViewById(R.id.txtRowChip2);
            trailing = itemView.findViewById(R.id.txtRowTrailing);
        }

        void bind(AdminRow row) {
            leading.setText(row.leading);
            leading.setTextColor(ContextCompat.getColor(itemView.getContext(), AdminUi.strongColor(row.leadingKind)));
            AdminUi.tint(leading, AdminUi.softColor(row.leadingKind));
            title.setText(row.title);
            subtitle.setText(row.subtitle);
            subtitle.setVisibility(row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            AdminUi.styleChip(chip, row.chip);
            AdminUi.styleChip(chip2, row.chip2);
            trailing.setText(row.trailing);
            trailing.setVisibility(row.trailing.isEmpty() ? View.GONE : View.VISIBLE);
            itemView.setOnClickListener(v -> listener.onRowClick(row));
        }
    }
}
