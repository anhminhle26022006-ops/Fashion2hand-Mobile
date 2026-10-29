package vn.edu.ueh.thudinh.fashion2hand.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminLabels;
import vn.edu.ueh.thudinh.fashion2hand.utils.admin.AdminUi;
import vn.edu.ueh.thudinh.fashion2hand.viewmodel.admin.AdminViewModel;

/** Tab Khác: tài khoản admin, kiểm duyệt, giao dịch, cài đặt, đăng xuất, chế độ demo. */
public class AdminMoreFragment extends Fragment {

    private AdminViewModel viewModel;
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        AdminUi.bindTopbar(view, "Khác", "Kiểm duyệt và cấu hình hệ thống",
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_NOTIFICATIONS));
        ((TextView) view.findViewById(R.id.txtMoreAdminName)).setText("Quản trị viên");
        ((TextView) view.findViewById(R.id.txtMoreAdminEmail)).setText(viewModel.getAdminEmail());
        AdminUi.styleChip(view.findViewById(R.id.txtMoreAdminRole), new AdminLabels.Label("ADMIN", AdminUi.KIND_OK));

        AdminUi.bindMenuRow(view, R.id.rowMoreTransactions, R.drawable.ic_admin_wallet, AdminUi.KIND_HOLD,
                "Theo dõi giao dịch", "Payment Holding Simulation", null,
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_TRANSACTIONS));
        AdminUi.bindMenuRow(view, R.id.rowMoreSettings, R.drawable.ic_admin_settings, AdminUi.KIND_NEUTRAL,
                "Cài đặt hệ thống", "Danh mục và quy tắc kiểm duyệt", null,
                v -> startActivity(new Intent(requireContext(), AdminSettingsActivity.class)));
        AdminUi.bindMenuRow(view, R.id.rowMoreLogout, R.drawable.ic_admin_logout, AdminUi.KIND_DANGER,
                "Đăng xuất", "Rời khu vực quản trị", null, v -> confirmLogout());

        AdminUi.bindSwitchRow(view.findViewById(R.id.rowMoreSimulateError), "Giả lập lỗi tải dữ liệu",
                "Bật để xem trạng thái lỗi trên các danh sách", viewModel.isSimulateError(),
                (button, checked) -> {
                    viewModel.setSimulateError(checked);
                    AdminUi.toast(requireContext(), checked ? "Đã bật giả lập lỗi" : "Đã tắt giả lập lỗi");
                });

        viewModel.getDataVersion().observe(getViewLifecycleOwner(), version -> bindCounts());
    }

    @Override
    public void onResume() {
        super.onResume();
        bindCounts();
    }

    private void bindCounts() {
        if (root == null || !isAdded()) return;
        int reports = viewModel.countOpenReports();
        int disputes = viewModel.countOpenDisputes();
        int verifications = viewModel.countPendingVerifications();
        AdminUi.bindMenuRow(root, R.id.rowMoreReports, R.drawable.ic_admin_flag, AdminUi.KIND_DANGER,
                "Báo cáo vi phạm", "Bài đăng, người dùng, đơn hàng bị báo cáo",
                reports > 0 ? new AdminLabels.Label(String.valueOf(reports), AdminUi.KIND_DANGER) : null,
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_REPORTS));
        AdminUi.bindMenuRow(root, R.id.rowMoreDisputes, R.drawable.ic_admin_alert, AdminUi.KIND_WARN,
                "Khiếu nại giao dịch", "Tiền đang được giữ",
                disputes > 0 ? new AdminLabels.Label(String.valueOf(disputes), AdminUi.KIND_WARN) : null,
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_DISPUTES));
        AdminUi.bindMenuRow(root, R.id.rowMoreVerify, R.drawable.ic_admin_shield, AdminUi.KIND_INFO,
                "Xác minh tài khoản", "Hồ sơ chờ duyệt",
                verifications > 0 ? new AdminLabels.Label(String.valueOf(verifications), AdminUi.KIND_INFO) : null,
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_VERIFICATIONS));
    }

    private void confirmLogout() {
        AdminUi.confirm(requireContext(), "Đăng xuất?", "Bạn sẽ rời khu vực quản trị.", "Đăng xuất", () -> {
            viewModel.logout();
            Intent intent = new Intent(requireContext(), AdminLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
