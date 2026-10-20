package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.thudinh.fashion2hand.R;

/** Tab Tổng quan: số liệu, việc cần xử lý, biểu đồ đơn hàng 7 ngày, hoạt động gần đây. */
public class AdminOverviewFragment extends Fragment {

    private static final int[] CHART_VALUES = {38, 52, 44, 70, 58, 86, 64};
    private static final String[] CHART_DAYS = {"T6", "T7", "CN", "T2", "T3", "T4", "T5"};

    private AdminViewModel viewModel;
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        AdminUi.bindTopbar(view, "Tổng quan", viewModel.getTodayText(),
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_NOTIFICATIONS));

        AdminUi.bindStat(view, R.id.statUsers, "1.248", "Người dùng", "+32 tuần này");
        AdminUi.bindStat(view, R.id.statActive, "864", "Đang hoạt động", "69% tổng số");
        AdminUi.bindStat(view, R.id.statPosts, "3.512", "Bài đăng", "2.970 đang hiển thị");
        AdminUi.bindStat(view, R.id.statOrders, "642", "Đơn hàng", "+48 tuần này");
        AdminUi.bindStat(view, R.id.statRevenue, "18,4 tr₫", "Doanh số (mô phỏng)", "Đơn mua bán");
        AdminUi.bindStat(view, R.id.statFree, "287", "Cho tặng miễn phí", "Đơn hoàn tất");

        buildChart(view.findViewById(R.id.layoutAdminChart));
        int total = 0;
        for (int v : CHART_VALUES) total += v;
        ((TextView) view.findViewById(R.id.txtAdminChartTotal))
                .setText("Tổng " + total + " đơn trong 7 ngày · Dữ liệu minh họa cho prototype");

        LinearLayout recent = view.findViewById(R.id.layoutAdminRecent);
        AdminUi.addKv(recent, "Báo cáo bài “Áo khoác denim”", "2 phút trước");
        AdminUi.addKv(recent, "Khiếu nại đơn F2H-10234", "15 phút trước");
        AdminUi.addKv(recent, "Hồ sơ xác minh mới", "40 phút trước");
        AdminUi.addKv(recent, "Đơn F2H-10231 hoàn tất", "Hôm qua");
    }

    @Override
    public void onResume() {
        super.onResume();
        int reports = viewModel.countOpenReports();
        int disputes = viewModel.countOpenDisputes();
        int verifications = viewModel.countPendingVerifications();
        AdminUi.bindMenuRow(root, R.id.rowTodoReports, R.drawable.ic_admin_flag, AdminUi.KIND_DANGER,
                "Báo cáo vi phạm", "Chờ xem xét", countLabel(reports, AdminUi.KIND_DANGER),
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_REPORTS));
        AdminUi.bindMenuRow(root, R.id.rowTodoDisputes, R.drawable.ic_admin_alert, AdminUi.KIND_WARN,
                "Khiếu nại giao dịch", "Tiền đang bị giữ", countLabel(disputes, AdminUi.KIND_WARN),
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_DISPUTES));
        AdminUi.bindMenuRow(root, R.id.rowTodoVerify, R.drawable.ic_admin_shield, AdminUi.KIND_INFO,
                "Yêu cầu xác minh", "Chờ duyệt", countLabel(verifications, AdminUi.KIND_INFO),
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_VERIFICATIONS));
        root.findViewById(R.id.viewAdminBellDot)
                .setVisibility(reports + disputes + verifications > 0 ? View.VISIBLE : View.GONE);
    }

    private AdminLabels.Label countLabel(int count, int kind) {
        return count > 0 ? new AdminLabels.Label(String.valueOf(count), kind) : null;
    }

    /** Biểu đồ cột đơn giản dựng bằng View, cột cuối (hôm nay) tô đậm. */
    private void buildChart(LinearLayout chart) {
        Context c = requireContext();
        int max = 0;
        for (int v : CHART_VALUES) max = Math.max(max, v);
        for (int i = 0; i < CHART_VALUES.length; i++) {
            LinearLayout column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            TextView value = new TextView(c);
            value.setText(String.valueOf(CHART_VALUES[i]));
            value.setTextSize(10);
            value.setTextColor(ContextCompat.getColor(c, R.color.f2h_text_muted));
            column.addView(value);

            View bar = new View(c);
            bar.setBackgroundResource(R.drawable.bg_admin_chip);
            AdminUi.tint(bar, i == CHART_VALUES.length - 1 ? R.color.f2h_primary_light : R.color.f2h_primary_soft);
            int height = AdminUi.dp(c, 80) * CHART_VALUES[i] / max;
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(AdminUi.dp(c, 22), height);
            barLp.topMargin = AdminUi.dp(c, 4);
            column.addView(bar, barLp);

            TextView day = new TextView(c);
            day.setText(CHART_DAYS[i]);
            day.setTextSize(11);
            day.setTextColor(ContextCompat.getColor(c, R.color.f2h_text_muted));
            day.setPadding(0, AdminUi.dp(c, 6), 0, 0);
            column.addView(day);

            chart.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }
}
