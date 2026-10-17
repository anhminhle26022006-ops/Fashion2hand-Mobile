package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.model.Transaction;

/** Màn danh sách con: Báo cáo, Khiếu nại, Xác minh, Giao dịch, Thông báo. */
public class AdminListActivity extends BaseAdminActivity {

    public static final String EXTRA_TYPE = "admin_list_type";
    public static final String TYPE_REPORTS = "reports";
    public static final String TYPE_DISPUTES = "disputes";
    public static final String TYPE_VERIFICATIONS = "verifications";
    public static final String TYPE_TRANSACTIONS = "transactions";
    public static final String TYPE_NOTIFICATIONS = "notifications";

    private AdminListController controller;
    private String type;

    public static void open(Context context, String type) {
        Intent intent = new Intent(context, AdminListActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_list);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null) type = TYPE_REPORTS;
        android.view.View root = findViewById(android.R.id.content);

        switch (type) {
            case TYPE_DISPUTES:
                bindBackBar("Khiếu nại giao dịch");
                controller = new AdminListController(root, "Tìm mã khiếu nại, mã đơn, lý do",
                        AdminViewModel.DISPUTE_FILTERS, viewModel::getDisputeRows, viewModel::isSimulateError,
                        row -> AdminDetailActivity.open(this, AdminDetailActivity.TYPE_DISPUTE, row.id));
                controller.showNotice("Trong thời gian khiếu nại, tiền của đơn vẫn ở trạng thái giữ và chưa chuyển cho người bán.",
                        AdminUi.KIND_WARN);
                controller.setEmptyText("Không có khiếu nại", "Không có khiếu nại nào ở trạng thái này.");
                break;
            case TYPE_VERIFICATIONS:
                bindBackBar("Xác minh tài khoản");
                controller = new AdminListController(root, "Tìm theo tên, loại giấy tờ",
                        AdminViewModel.VERIFY_FILTERS, viewModel::getVerificationRows, viewModel::isSimulateError,
                        row -> AdminDetailActivity.open(this, AdminDetailActivity.TYPE_VERIFICATION, row.id));
                controller.showNotice("Xác minh thủ công (Verification Simulation). Chưa tích hợp VNeID, Admin duyệt dựa trên hồ sơ người dùng gửi.",
                        AdminUi.KIND_INFO);
                controller.setEmptyText("Không có hồ sơ", "Không có hồ sơ xác minh nào ở trạng thái này.");
                break;
            case TYPE_TRANSACTIONS:
                bindBackBar("Theo dõi giao dịch");
                controller = new AdminListController(root, "Tìm mã giao dịch, mã đơn",
                        AdminViewModel.TRANSACTION_FILTERS, viewModel::getTransactionRows, viewModel::isSimulateError,
                        row -> {
                            Transaction t = viewModel.getTransaction(row.id);
                            if (t != null) AdminDetailActivity.open(this, AdminDetailActivity.TYPE_ORDER, t.getOrderId());
                        });
                controller.showNotice("Payment Holding Simulation. Trạng thái tiền được mô phỏng trong hệ thống, chưa kết nối cổng thanh toán thật.",
                        AdminUi.KIND_HOLD);
                controller.setEmptyText("Không có giao dịch", "Không có giao dịch nào ở trạng thái này.");
                break;
            case TYPE_NOTIFICATIONS:
                bindBackBar("Thông báo quản trị");
                controller = new AdminListController(root, "Tìm thông báo",
                        AdminViewModel.NOTIFICATION_FILTERS, viewModel::getNotificationRows, viewModel::isSimulateError,
                        this::openNotification);
                controller.setEmptyText("Không có thông báo mới", "Mọi báo cáo, khiếu nại và hồ sơ xác minh đã được xử lý.");
                break;
            default:
                bindBackBar("Báo cáo vi phạm");
                controller = new AdminListController(root, "Tìm theo lý do, đối tượng, người báo cáo",
                        AdminViewModel.REPORT_FILTERS, viewModel::getReportRows, viewModel::isSimulateError,
                        row -> AdminDetailActivity.open(this, AdminDetailActivity.TYPE_REPORT, row.id));
                controller.setEmptyText("Không có báo cáo", "Không có báo cáo nào ở trạng thái này.");
        }
        controller.reload(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (TYPE_TRANSACTIONS.equals(type)) {
            long[] totals = viewModel.getTransactionTotals();
            controller.showSummary(
                    new String[]{"Đang giữ", "Đã giải ngân", "Đã hoàn tiền", "Tranh chấp"},
                    new String[]{AdminUi.money(totals[0]), AdminUi.money(totals[1]),
                            AdminUi.money(totals[2]), AdminUi.money(totals[3])},
                    new int[]{AdminUi.KIND_HOLD, AdminUi.KIND_OK, AdminUi.KIND_WARN, AdminUi.KIND_DANGER});
        }
        controller.refresh();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) controller.release();
        super.onDestroy();
    }

    private void openNotification(AdminRow row) {
        String[] parts = row.id.split(":", 2);
        if (parts.length < 2) return;
        String detailType;
        switch (parts[0]) {
            case "dispute": detailType = AdminDetailActivity.TYPE_DISPUTE; break;
            case "verification": detailType = AdminDetailActivity.TYPE_VERIFICATION; break;
            default: detailType = AdminDetailActivity.TYPE_REPORT;
        }
        AdminDetailActivity.open(this, detailType, parts[1]);
    }
}
