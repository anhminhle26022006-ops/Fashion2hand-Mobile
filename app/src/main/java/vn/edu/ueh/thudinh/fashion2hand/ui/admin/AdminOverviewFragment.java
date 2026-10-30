package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

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

import java.util.List;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;
import vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin.AdminViewModel;

/** Tab Tổng quan: số liệu, việc cần xử lý, biểu đồ đơn hàng 7 ngày, hoạt động gần đây (dữ liệu backend). */
public class AdminOverviewFragment extends Fragment {

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

        viewModel.getDataVersion().observe(getViewLifecycleOwner(), version -> bind());
    }

    @Override
    public void onResume() {
        super.onResume();
        bind();
    }

    private void bind() {
        if (root == null || !isAdded()) return;
        bindStats();
        bindTodo();
        bindChart();
        bindRecent();
    }

    private void bindStats() {
        if (!viewModel.isLoaded()) {
            String dash = "—";
            String state = viewModel.isSimulateError() ? "Không tải được" : "Đang tải…";
            AdminUi.bindStat(root, R.id.statUsers, dash, "Người dùng", state);
            AdminUi.bindStat(root, R.id.statActive, dash, "Đang hoạt động", state);
            AdminUi.bindStat(root, R.id.statPosts, dash, "Bài đăng", state);
            AdminUi.bindStat(root, R.id.statOrders, dash, "Đơn hàng", state);
            AdminUi.bindStat(root, R.id.statRevenue, dash, "Doanh số (mô phỏng)", state);
            AdminUi.bindStat(root, R.id.statFree, dash, "Cho tặng miễn phí", state);
            return;
        }
        int users = viewModel.countUsers();
        int active = viewModel.countActiveUsers();
        int percent = users == 0 ? 0 : Math.round(active * 100f / users);
        AdminUi.bindStat(root, R.id.statUsers, String.valueOf(users), "Người dùng", "Không tính Admin");
        AdminUi.bindStat(root, R.id.statActive, String.valueOf(active), "Đang hoạt động", percent + "% tổng số");
        AdminUi.bindStat(root, R.id.statPosts, String.valueOf(viewModel.countPosts()), "Bài đăng",
                viewModel.countActivePosts() + " đang hiển thị");
        AdminUi.bindStat(root, R.id.statOrders, String.valueOf(viewModel.countOrders()), "Đơn hàng",
                "+" + viewModel.countOrdersSince(7) + " trong 7 ngày"
                        + (viewModel.countCancelledOrders() > 0 ? " · " + viewModel.countCancelledOrders() + " đã hủy" : ""));
        AdminUi.bindStat(root, R.id.statRevenue, AdminUi.money(viewModel.getCompletedSales()), "Doanh số (mô phỏng)",
                viewModel.countCompletedSales() + " đơn mua bán hoàn tất");
        AdminUi.bindStat(root, R.id.statFree, String.valueOf(viewModel.countFreeOrders()), "Cho tặng miễn phí",
                viewModel.countCompletedFreeOrders() + " đơn đã hoàn tất");
    }

    private void bindTodo() {
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

    /** Biểu đồ cột số đơn tạo mỗi ngày trong 7 ngày, cột cuối (hôm nay) tô đậm. */
    private void bindChart() {
        LinearLayout chart = root.findViewById(R.id.layoutAdminChart);
        chart.removeAllViews();
        int[] values = viewModel.getOrdersLast7Days();
        String[] days = viewModel.getLast7DayLabels();
        Context c = requireContext();
        int max = 1;
        int total = 0;
        for (int v : values) {
            max = Math.max(max, v);
            total += v;
        }
        for (int i = 0; i < values.length; i++) {
            LinearLayout column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            TextView value = new TextView(c);
            value.setText(String.valueOf(values[i]));
            value.setTextSize(10);
            value.setTextColor(ContextCompat.getColor(c, R.color.f2h_text_muted));
            column.addView(value);

            View bar = new View(c);
            bar.setBackgroundResource(R.drawable.bg_admin_chip);
            AdminUi.tint(bar, i == values.length - 1 ? R.color.f2h_primary_light : R.color.f2h_primary_soft);
            int height = Math.max(AdminUi.dp(c, 4), AdminUi.dp(c, 80) * values[i] / max);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(AdminUi.dp(c, 22), height);
            barLp.topMargin = AdminUi.dp(c, 4);
            column.addView(bar, barLp);

            TextView day = new TextView(c);
            day.setText(days[i]);
            day.setTextSize(11);
            day.setTextColor(ContextCompat.getColor(c, R.color.f2h_text_muted));
            day.setPadding(0, AdminUi.dp(c, 6), 0, 0);
            column.addView(day);

            chart.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        TextView summary = root.findViewById(R.id.txtAdminChartTotal);
        boolean failed = !viewModel.isLoaded() && viewModel.isSimulateError();
        summary.setText(viewModel.isLoaded() ? "Tổng " + total + " đơn trong 7 ngày · Dữ liệu từ Firebase"
                : failed ? "Không tải được dữ liệu. Chạm vào đây để thử lại." : "Đang tải dữ liệu đơn hàng…");
        summary.setOnClickListener(failed ? v -> viewModel.refresh() : null);
        summary.setClickable(failed);
    }

    private void bindRecent() {
        LinearLayout recent = root.findViewById(R.id.layoutAdminRecent);
        recent.removeAllViews();
        List<String[]> items = viewModel.getRecentActivities();
        if (items.isEmpty()) {
            AdminUi.addKv(recent, viewModel.isLoaded() ? "Chưa có hoạt động" : "Đang tải…", "");
            return;
        }
        for (String[] item : items) AdminUi.addKv(recent, item[0], item[1]);
    }
}