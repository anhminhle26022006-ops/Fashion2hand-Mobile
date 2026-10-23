package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.ueh.thudinh.fashion2hand.ui.admin.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

/** Requires the local-only admin-ui@example.com fixture provisioned by the host script. */
@RunWith(AndroidJUnit4.class)
public class AdminFlowTest {
 private final FirebaseAuth auth=FirebaseAuth.getInstance();
 private final FirebaseFirestore db=FirebaseFirestore.getInstance();
 private void admin()throws Exception{auth.signOut();Tasks.await(auth.signInWithEmailAndPassword("admin-ui@example.com","Test-only-123!"),30,TimeUnit.SECONDS);}
 private String ordinary()throws Exception{auth.signOut();String email="admin-ui-target-"+System.nanoTime()+"@example.com";String uid=Tasks.await(auth.createUserWithEmailAndPassword(email,"Test-only-123!"),30,TimeUnit.SECONDS).getUser().getUid();Map<String,Object> profile=new HashMap<>();profile.put("uid",uid);profile.put("email",email);profile.put("name","UI Admin Target");profile.put("role","USER");profile.put("accountStatus","ACTIVE");profile.put("createdAt",FieldValue.serverTimestamp());Tasks.await(db.collection("users").document(uid).set(profile),30,TimeUnit.SECONDS);return uid;}
 private View text(View root,String value){if(root instanceof TextView && value.equals(((TextView)root).getText().toString()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
 private boolean contains(View root,String value){if(root instanceof TextView && ((TextView)root).getText().toString().contains(value))return true;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(contains(((ViewGroup)root).getChildAt(i),value))return true;return false;}
 private <A extends Activity> void waitText(ActivityScenario<A> scenario,String value)throws Exception{long until=android.os.SystemClock.elapsedRealtime()+45000;while(android.os.SystemClock.elapsedRealtime()<until){AtomicBoolean found=new AtomicBoolean();scenario.onActivity(a->found.set(contains(a.findViewById(android.R.id.content),value)));if(found.get())return;Thread.sleep(100);}fail("Missing text: "+value);}
 private <A extends Activity> void clickText(ActivityScenario<A> scenario,String value)throws Exception{waitText(scenario,value);scenario.onActivity(a->{View view=text(a.findViewById(android.R.id.content),value);assertNotNull(view);assertTrue(view.isEnabled());assertTrue(view.performClick());});}
 private Intent detail(String collection,String id){return new Intent(ApplicationProvider.getApplicationContext(),AdminDetailActivity.class).putExtra(AdminDetailActivity.EXTRA_TYPE,collection).putExtra(AdminDetailActivity.EXTRA_ID,id);}
 private long revision()throws Exception{return ((Number)((Map<?,?>)Tasks.await(FirebaseFunctions.getInstance().getHttpsCallable("adminSettings").call(Collections.emptyMap()),45,TimeUnit.SECONDS).getData()).get("revision")).longValue();}
 @Test public void everyAdminEntryRejectsOrdinaryUsers()throws Exception{
  String uid=ordinary();
  try(ActivityScenario<AdminMainActivity> s=ActivityScenario.launch(AdminMainActivity.class)){waitText(s,"Phiên Admin không còn quyền truy cập");s.onActivity(a->assertNull(text(a.findViewById(android.R.id.content),"Dashboard")));}
  try(ActivityScenario<AdminDetailActivity> s=ActivityScenario.launch(detail("users",uid))){waitText(s,"Phiên Admin không còn quyền truy cập");}
  try(ActivityScenario<AdminListActivity> s=ActivityScenario.launch(new Intent(ApplicationProvider.getApplicationContext(),AdminListActivity.class).putExtra(AdminListActivity.EXTRA_TYPE,"users"))){waitText(s,"Phiên Admin không còn quyền truy cập");}
  try(ActivityScenario<AdminSettingsActivity> s=ActivityScenario.launch(AdminSettingsActivity.class)){waitText(s,"Phiên Admin không còn quyền truy cập");}
 }
 @Test public void realLoginRejectsWrongPasswordThenLoadsDashboard()throws Exception{
  auth.signOut();
  try(ActivityScenario<AdminLoginActivity> s=ActivityScenario.launch(AdminLoginActivity.class)){
   s.onActivity(a->{((EditText)a.findViewById(R.id.edtAdminEmail)).setText("admin-ui@example.com");((EditText)a.findViewById(R.id.edtAdminPassword)).setText("incorrect-password");a.findViewById(R.id.btnAdminLogin).performClick();});
   waitText(s,"Đăng nhập thất bại");assertNull(auth.getCurrentUser());
   s.onActivity(a->{((EditText)a.findViewById(R.id.edtAdminPassword)).setText("Test-only-123!");a.findViewById(R.id.btnAdminLogin).performClick();});
   long until=android.os.SystemClock.elapsedRealtime()+45000;while(s.getState()!=Lifecycle.State.DESTROYED&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);
   assertEquals(Lifecycle.State.DESTROYED,s.getState());assertNotNull(auth.getCurrentUser());assertEquals("admin-ui@example.com",auth.getCurrentUser().getEmail());
  }
  try(ActivityScenario<AdminMainActivity> s=ActivityScenario.launch(AdminMainActivity.class)){waitText(s,"Dashboard");waitText(s,"Cập nhật từ backend:");waitText(s,"Nhật ký quản trị");s.recreate();waitText(s,"Dashboard");}
 }
 @Test public void lockAndUnlockAreSavedAndSurviveRecreation()throws Exception{
  String uid=ordinary();admin();
  try(ActivityScenario<AdminDetailActivity> s=ActivityScenario.launch(detail("users",uid))){
   clickText(s,"Khóa tài khoản");onView(isAssignableFrom(EditText.class)).perform(replaceText("Khóa tài khoản kiểm thử"),closeSoftKeyboard());onView(withText("Xác nhận")).perform(click());
   waitText(s,"Mở khóa tài khoản");s.recreate();waitText(s,"Mở khóa tài khoản");waitText(s,"LOCKED");
   clickText(s,"Mở khóa tài khoản");onView(isAssignableFrom(EditText.class)).perform(replaceText("Mở lại sau kiểm thử"),closeSoftKeyboard());onView(withText("Xác nhận")).perform(click());waitText(s,"Khóa tài khoản");waitText(s,"ACTIVE");
  }
  long before=revision();
  try(ActivityScenario<AdminSettingsActivity> s=ActivityScenario.launch(AdminSettingsActivity.class)){
   waitText(s,"Lưu cấu hình");clickText(s,"Lưu cấu hình");
   long until=android.os.SystemClock.elapsedRealtime()+45000;while(revision()==before&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);
   assertEquals(before+1,revision());s.recreate();waitText(s,"Lưu cấu hình");
  }
 }
}
