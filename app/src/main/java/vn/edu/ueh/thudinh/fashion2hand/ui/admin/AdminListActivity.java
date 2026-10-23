package vn.edu.ueh.thudinh.fashion2hand.ui.admin;
import android.content.Context;
import android.content.Intent;
public class AdminListActivity extends LiveAdminActivity {
 public static final String EXTRA_TYPE="admin_list_type", TYPE_REPORTS="reports", TYPE_DISPUTES="disputes", TYPE_VERIFICATIONS="verificationRequests", TYPE_TRANSACTIONS="moneyTransactions", TYPE_NOTIFICATIONS="adminAudit";
 public static void open(Context context,String type){context.startActivity(new Intent(context,AdminListActivity.class).putExtra(EXTRA_TYPE,type));}
 @Override protected String collection(){String type=getIntent().getStringExtra(EXTRA_TYPE);return type==null?TYPE_REPORTS:type;}
}
