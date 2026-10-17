package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.thudinh.fashion2hand.R;

/** Tab danh sách dùng chung cho Người dùng, Bài đăng, Đơn hàng. */
public class AdminListFragment extends Fragment {

    public static final String TYPE_USERS = "users";
    public static final String TYPE_POSTS = "posts";
    public static final String TYPE_ORDERS = "orders";
    private static final String ARG_TYPE = "type";

    private AdminListController controller;

    public static AdminListFragment newInstance(String type) {
        AdminListFragment fragment = new AdminListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AdminViewModel vm = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        String type = requireArguments().getString(ARG_TYPE, TYPE_USERS);
        View.OnClickListener onBell =
                v -> AdminListActivity.open(requireContext(), AdminListActivity.TYPE_NOTIFICATIONS);

        if (TYPE_POSTS.equals(type)) {
            AdminUi.bindTopbar(view, "Bài đăng", "Kiểm duyệt nội dung", onBell);
            controller = new AdminListController(view, "Tìm theo tên sản phẩm, người đăng",
                    AdminViewModel.POST_FILTERS, vm::getPostRows, vm::isSimulateError,
                    row -> AdminDetailActivity.open(requireContext(), AdminDetailActivity.TYPE_POST, row.id));
            controller.setEmptyText("Không có bài đăng", "Không có bài đăng nào khớp bộ lọc này.");
        } else if (TYPE_ORDERS.equals(type)) {
            AdminUi.bindTopbar(view, "Đơn hàng", "Chế độ theo dõi, Admin chỉ xem", onBell);
            controller = new AdminListController(view, "Tìm mã đơn, người mua, người bán",
                    AdminViewModel.ORDER_FILTERS, vm::getOrderRows, vm::isSimulateError,
                    row -> AdminDetailActivity.open(requireContext(), AdminDetailActivity.TYPE_ORDER, row.id));
            controller.setEmptyText("Không có đơn hàng", "Không có đơn hàng nào ở trạng thái này.");
        } else {
            AdminUi.bindTopbar(view, "Người dùng", "Quản lý tài khoản và xác minh", onBell);
            controller = new AdminListController(view, "Tìm theo tên, email",
                    AdminViewModel.USER_FILTERS, vm::getUserRows, vm::isSimulateError,
                    row -> AdminDetailActivity.open(requireContext(), AdminDetailActivity.TYPE_USER, row.id));
            controller.setEmptyText("Không có người dùng", "Không tìm thấy tài khoản phù hợp.");
        }
        controller.reload(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null) controller.refresh();
    }

    @Override
    public void onDestroyView() {
        if (controller != null) controller.release();
        super.onDestroyView();
    }
}
