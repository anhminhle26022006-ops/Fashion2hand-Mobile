package vn.edu.ueh.thudinh.fashion2hand.ui.admin;
import android.content.Context;
import android.content.Intent;
public class AdminDetailActivity extends LiveAdminActivity {
 public static final String EXTRA_TYPE="admin_detail_type", EXTRA_ID="admin_detail_id";
 public static final String TYPE_USER="users", TYPE_POST="posts", TYPE_ORDER="orders", TYPE_REPORT="reports", TYPE_DISPUTE="disputes", TYPE_VERIFICATION="verificationRequests";
 public static void open(Context context,String type,String id){context.startActivity(new Intent(context,AdminDetailActivity.class).putExtra(EXTRA_TYPE,type).putExtra(EXTRA_ID,id));}
 @Override protected String collection(){String type=getIntent().getStringExtra(EXTRA_TYPE);return type==null?"":type;}
 @Override protected String recordId(){String id=getIntent().getStringExtra(EXTRA_ID);return id==null?"":id;}
}
