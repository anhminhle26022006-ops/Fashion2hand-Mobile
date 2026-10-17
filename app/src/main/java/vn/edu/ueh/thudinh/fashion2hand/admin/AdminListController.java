package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.function.BooleanSupplier;

import vn.edu.ueh.thudinh.fashion2hand.R;

/**
 * Điều khiển phần thân danh sách (view_admin_list_content.xml):
 * ô tìm kiếm, chip lọc, RecyclerView và 3 trạng thái đang tải / rỗng / lỗi.
 * Dùng chung cho tab (Fragment) và màn danh sách con (Activity).
 */
public class AdminListController {

    public interface RowSource {
        List<AdminRow> load(int filterIndex, String query);
    }

    private static final int STATE_CONTENT = 0;
    private static final int STATE_LOADING = 1;
    private static final int STATE_EMPTY = 2;
    private static final int STATE_ERROR = 3;

    private final Context context;
    private final RowSource source;
    private final BooleanSupplier errorCheck;
    private final AdminRowAdapter adapter;
    private final RecyclerView recycler;
    private final EditText search;
    private final View stateLayout;
    private final ProgressBar stateProgress;
    private final ImageView stateImage;
    private final TextView stateTitle;
    private final TextView stateMessage;
    private final View stateRetry;
    private final TextView notice;
    private final View summaryGrid;
    private final TextView[] summaryCells;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int filterIndex = 0;
    private boolean loadedOnce = false;
    private String emptyTitle = "Không có kết quả";
    private String emptyMessage = "Thử đổi bộ lọc hoặc từ khóa tìm kiếm.";

    public AdminListController(View root, String searchHint, String[] filters, RowSource source,
                               BooleanSupplier errorCheck, AdminRowAdapter.OnRowClickListener onRowClick) {
        this.context = root.getContext();
        this.source = source;
        this.errorCheck = errorCheck;

        recycler = root.findViewById(R.id.recyclerAdmin);
        search = root.findViewById(R.id.edtAdminSearch);
        ChipGroup chipGroup = root.findViewById(R.id.chipGroupAdminFilters);
        stateLayout = root.findViewById(R.id.layoutAdminState);
        stateProgress = root.findViewById(R.id.progressAdminState);
        stateImage = root.findViewById(R.id.imgAdminState);
        stateTitle = root.findViewById(R.id.txtAdminStateTitle);
        stateMessage = root.findViewById(R.id.txtAdminStateMessage);
        stateRetry = root.findViewById(R.id.btnAdminStateRetry);
        notice = root.findViewById(R.id.txtAdminListNotice);
        summaryGrid = root.findViewById(R.id.gridAdminSummary);
        summaryCells = new TextView[]{
                root.findViewById(R.id.txtAdminSummary1), root.findViewById(R.id.txtAdminSummary2),
                root.findViewById(R.id.txtAdminSummary3), root.findViewById(R.id.txtAdminSummary4)};

        int span = context.getResources().getInteger(R.integer.admin_grid_span);
        recycler.setLayoutManager(new GridLayoutManager(context, span));
        adapter = new AdminRowAdapter(onRowClick);
        recycler.setAdapter(adapter);

        search.setHint(searchHint);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (loadedOnce) render();
            }
        });

        for (int i = 0; i < filters.length; i++) {
            Chip chip = new Chip(context);
            chip.setId(View.generateViewId());
            chip.setText(filters[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.admin_chip_bg);
            chip.setTextColor(ContextCompat.getColorStateList(context, R.color.admin_chip_text));
            chip.setChipStrokeColorResource(R.color.f2h_border);
            chip.setChipStrokeWidth(AdminUi.dp(context, 1));
            chipGroup.addView(chip);
            if (i == 0) chip.setChecked(true);
            final int index = i;
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    filterIndex = index;
                    render();
                }
            });
        }

        stateRetry.setOnClickListener(v -> reload(true));
    }

    public void setEmptyText(String title, String message) {
        emptyTitle = title;
        emptyMessage = message;
    }

    public void showNotice(String text, int kind) {
        AdminUi.styleNotice(notice, text, kind);
    }

    /** 4 ô tổng hợp phía trên danh sách (dùng cho màn Theo dõi giao dịch). */
    public void showSummary(String[] labels, String[] values, int[] kinds) {
        summaryGrid.setVisibility(View.VISIBLE);
        for (int i = 0; i < summaryCells.length && i < labels.length; i++) {
            summaryCells[i].setText(labels[i] + "\n" + values[i]);
            summaryCells[i].setTextColor(ContextCompat.getColor(context, AdminUi.strongColor(kinds[i])));
            AdminUi.tint(summaryCells[i], AdminUi.softColor(kinds[i]));
        }
    }

    /** Lần đầu mở: hiện trạng thái đang tải rồi mới hiện dữ liệu. */
    public void reload(boolean showLoading) {
        loadedOnce = true;
        handler.removeCallbacksAndMessages(null);
        if (!showLoading) {
            render();
            return;
        }
        showState(STATE_LOADING);
        handler.postDelayed(this::render, 450);
    }

    /** Gọi trong onResume để cập nhật sau khi Admin thao tác ở màn chi tiết. */
    public void refresh() {
        if (loadedOnce) render();
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
    }

    private void render() {
        if (errorCheck.getAsBoolean()) {
            showState(STATE_ERROR);
            return;
        }
        List<AdminRow> rows = source.load(filterIndex, search.getText().toString());
        adapter.submit(rows);
        showState(rows.isEmpty() ? STATE_EMPTY : STATE_CONTENT);
    }

    private void showState(int state) {
        recycler.setVisibility(state == STATE_CONTENT ? View.VISIBLE : View.GONE);
        stateLayout.setVisibility(state == STATE_CONTENT ? View.GONE : View.VISIBLE);
        stateProgress.setVisibility(state == STATE_LOADING ? View.VISIBLE : View.GONE);
        stateImage.setVisibility(state == STATE_LOADING ? View.GONE : View.VISIBLE);
        stateRetry.setVisibility(state == STATE_ERROR ? View.VISIBLE : View.GONE);
        if (state == STATE_LOADING) {
            stateTitle.setText("Đang tải dữ liệu");
            stateMessage.setText("Vui lòng chờ trong giây lát.");
        } else if (state == STATE_EMPTY) {
            stateImage.setImageResource(R.drawable.ic_admin_inbox);
            stateImage.setImageTintList(ContextCompat.getColorStateList(context, R.color.f2h_primary_light));
            AdminUi.tint(stateImage, R.color.f2h_primary_soft);
            stateTitle.setText(emptyTitle);
            stateMessage.setText(emptyMessage);
        } else if (state == STATE_ERROR) {
            stateImage.setImageResource(R.drawable.ic_admin_wifi_off);
            stateImage.setImageTintList(ContextCompat.getColorStateList(context, R.color.f2h_danger));
            AdminUi.tint(stateImage, R.color.f2h_danger_soft);
            stateTitle.setText("Không tải được dữ liệu");
            stateMessage.setText("Kiểm tra kết nối mạng rồi bấm Thử lại.");
        }
    }
}
