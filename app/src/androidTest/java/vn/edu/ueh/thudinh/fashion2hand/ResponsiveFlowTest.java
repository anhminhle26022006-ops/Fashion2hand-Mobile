package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.ueh.thudinh.fashion2hand.ui.admin.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Run on a 320x480dp AVD; records coverage and screenshots under externalFiles/qa. */
@RunWith(AndroidJUnit4.class)
public class ResponsiveFlowTest {
 private final FirebaseAuth auth=FirebaseAuth.getInstance();
 private final FirebaseFirestore db=FirebaseFirestore.getInstance();
 private interface Check<A extends Activity>{boolean ready(A activity);}
 private <A extends Activity> void waitFor(ActivityScenario<A> scenario,Check<A> check,String label)throws Exception{
  long deadline=android.os.SystemClock.elapsedRealtime()+90000;
  while(android.os.SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();scenario.onActivity(a->ready.set(check.ready(a)));if(ready.get())return;Thread.sleep(100);}fail("Timed out: "+label);
 }
 private boolean contains(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return true;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(contains(((ViewGroup)root).getChildAt(i),text))return true;return false;}
 private View exact(View root,String text){if(root instanceof TextView&&text.equals(((TextView)root).getText().toString()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=exact(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;}
 private <A extends Activity> void waitText(ActivityScenario<A> s,String text)throws Exception{waitFor(s,a->contains(a.findViewById(android.R.id.content),text),text);}
 private <A extends Activity> void clickId(ActivityScenario<A> s,int id)throws Exception{waitFor(s,a->a.findViewById(id)!=null&&a.findViewById(id).isEnabled(),"enabled "+id);s.onActivity(a->assertTrue(a.findViewById(id).performClick()));}
 private <A extends Activity> void rotate(ActivityScenario<A> s,boolean landscape)throws Exception{
  s.onActivity(a->a.setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
  waitFor(s,a->a.getResources().getConfiguration().orientation==(landscape?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT),"orientation");
 }
 private void show(MainActivity a,int layout){try{Method method=MainActivity.class.getDeclaredMethod("showScreen",int.class);method.setAccessible(true);method.invoke(a,layout);}catch(Exception e){throw new AssertionError(e);}}
 private void field(MainActivity a,String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
 private Object field(MainActivity a,String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(a);}catch(Exception e){throw new AssertionError(e);}}
 private String user()throws Exception{
  auth.signOut();String email="qa-"+System.nanoTime()+"@example.com";String uid=Tasks.await(auth.createUserWithEmailAndPassword(email,"Test-only-123!"),45,TimeUnit.SECONDS).getUser().getUid();
  Map<String,Object> data=new HashMap<>();data.put("uid",uid);data.put("name","QA Tester");data.put("email",email);data.put("role","USER");data.put("accountStatus","ACTIVE");data.put("createdAt",FieldValue.serverTimestamp());Tasks.await(db.collection("users").document(uid).set(data),45,TimeUnit.SECONDS);return uid;
 }
 private void admin()throws Exception{auth.signOut();Tasks.await(auth.signInWithEmailAndPassword("admin-ui@example.com","Test-only-123!"),45,TimeUnit.SECONDS);}
 private List<String> asset(String name)throws Exception{try(BufferedReader input=new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)))){List<String> lines=new ArrayList<>();String line;while((line=input.readLine())!=null)if(!line.trim().isEmpty())lines.add(line.trim());return lines;}}
 private void artifact(String name,byte[] bytes)throws Exception{
  android.content.ContentResolver resolver=ApplicationProvider.getApplicationContext().getContentResolver();android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,name.endsWith(".png")?"image/png":"text/plain");values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/Fashion2hand-QA");values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);android.net.Uri uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);try(OutputStream out=resolver.openOutputStream(uri)){assertNotNull(out);out.write(bytes);}values.clear();values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0);resolver.update(uri,values,null,null);
 }
 private void report(String name,List<String> lines)throws Exception{artifact(name,android.text.TextUtils.join("\n",lines).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private void screenshot(String name)throws Exception{
  Bitmap image=null;for(int attempt=0;attempt<4&&image==null;attempt++){image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(image==null)Thread.sleep(200);}
  if(image==null){java.util.concurrent.atomic.AtomicReference<Bitmap> snapshot=new java.util.concurrent.atomic.AtomicReference<>();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)){View root=activity.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));snapshot.set(bitmap);break;}});image=snapshot.get();}
  assertNotNull("No rendered Activity for screenshot",image);ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));artifact(name,out.toByteArray());
 }

 @Test public void allXmlLayoutsInflateInBothResourceOrientations()throws Exception{
  auth.signOut();List<String> failures=new ArrayList<>(),results=new ArrayList<>();List<String> layouts=asset("layout-audit.txt");
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   for(boolean landscape:new boolean[]{false,true}){rotate(s,landscape);s.onActivity(a->{for(String name:layouts){try{int id=a.getResources().getIdentifier(name,"layout",a.getPackageName());assertTrue(id!=0);a.getLayoutInflater().inflate(id,new FrameLayout(a),false);results.add((landscape?"LAND":"PORT")+" PASS "+name);}catch(Throwable error){Throwable root=error;while(root.getCause()!=null)root=root.getCause();String message=(landscape?"LAND":"PORT")+" FAIL "+name+": "+root;failures.add(message);results.add(message);}}});}
  }report("layout-audit.txt",results);assertTrue(android.text.TextUtils.join("\n",failures),failures.isEmpty());
 }
 @Test public void profileAndReportDraftsSurviveRotationWithKeyboardAndPhotos()throws Exception{
  user();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnCartHome)!=null,"home");s.onActivity(a->show(a,R.layout.activity_edit_profile));waitFor(s,a->"QA Tester".equals(((EditText)a.findViewById(R.id.edtProfileName)).getText().toString()),"loaded profile");
   s.onActivity(a->{((EditText)a.findViewById(R.id.edtProfileName)).setText("Tên chưa lưu khi xoay");((EditText)a.findViewById(R.id.edtProfileBio)).setText("Nội dung nháp nhiều dòng\nDòng thứ hai");});
   rotate(s,true);waitText(s,"Tên chưa lưu khi xoay");Thread.sleep(400);s.onActivity(a->assertEquals("Tên chưa lưu khi xoay",((EditText)a.findViewById(R.id.edtProfileName)).getText().toString()));
   onView(withId(R.id.edtProfileBio)).perform(scrollTo(),click());
   waitFor(s,a->androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(android.R.id.content))!=null&&androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(android.R.id.content)).isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()),"keyboard shown");
   s.onActivity(a->{Rect visible=new Rect();assertTrue(a.findViewById(R.id.edtProfileBio).getGlobalVisibleRect(visible));assertTrue(visible.height()>20);});screenshot("profile-land-keyboard.png");onView(withId(R.id.edtProfileBio)).perform(closeSoftKeyboard());
   rotate(s,false);waitText(s,"Tên chưa lưu khi xoay");
   s.onActivity(a->{field(a,"currentOrderId","qa-report");field(a,"reportEvidenceOrderId","qa-report");show(a,R.layout.activity_report_issue);((RadioGroup)a.findViewById(R.id.rgReportReason)).check(R.id.reportDamaged);((EditText)a.findViewById(R.id.edtReportDescription)).setText("Bằng chứng nháp phải còn sau xoay");((List<Object>)field(a,"reportEvidenceItems")).add(Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888));});
   rotate(s,true);waitText(s,"Bằng chứng nháp phải còn sau xoay");s.onActivity(a->{assertEquals(R.id.reportDamaged,((RadioGroup)a.findViewById(R.id.rgReportReason)).getCheckedRadioButtonId());assertEquals(1,((List<?>)field(a,"reportEvidenceItems")).size());assertTrue(((List<?>)field(a,"reportEvidenceItems")).get(0) instanceof android.net.Uri);});
   rotate(s,false);screenshot("report-port-small.png");
  }
 }
 @Test public void adminSettingsAndDecisionDialogKeepUnsavedInput()throws Exception{
  String target=user();admin();
  try(ActivityScenario<AdminSettingsActivity> s=ActivityScenario.launch(AdminSettingsActivity.class)){
   waitText(s,"Lưu cấu hình");s.onActivity(a->((EditText)a.findViewById(android.R.id.content).findViewWithTag("draft:maxPrice")).setText("4500000"));rotate(s,true);waitText(s,"Lưu cấu hình");
   s.onActivity(a->assertEquals("4500000",((EditText)a.findViewById(android.R.id.content).findViewWithTag("draft:maxPrice")).getText().toString()));screenshot("settings-land-small.png");rotate(s,false);waitText(s,"4500000");
  }
  Intent intent=new Intent(ApplicationProvider.getApplicationContext(),AdminDetailActivity.class).putExtra(AdminDetailActivity.EXTRA_TYPE,"users").putExtra(AdminDetailActivity.EXTRA_ID,target);
  try(ActivityScenario<AdminDetailActivity> s=ActivityScenario.launch(intent)){
   waitText(s,"Khóa tài khoản");s.onActivity(a->exact(a.findViewById(android.R.id.content),"Khóa tài khoản").performClick());onView(isAssignableFrom(EditText.class)).perform(replaceText("Lý do nháp vẫn còn"),closeSoftKeyboard());rotate(s,true);waitText(s,"Khóa tài khoản");onView(isAssignableFrom(EditText.class)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("Lý do nháp vẫn còn")));onView(withText("Hủy")).perform(click());
  }
 }
 @Test public void emptyCacheNetworkFailureAndRetryHaveVisibleStates()throws Exception{
  user();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnCartHome)!=null,"home");clickId(s,R.id.btnCartHome);waitText(s,"Giỏ hàng đang trống");
   Tasks.await(db.disableNetwork(),15,TimeUnit.SECONDS);s.onActivity(a->show(a,R.layout.activity_cart));waitText(s,"Đang chờ kết nối với server");waitText(s,"Thử lại");screenshot("cart-offline.png");Tasks.await(db.enableNetwork(),15,TimeUnit.SECONDS);waitText(s,"Giỏ hàng đang trống");
  }finally{Tasks.await(db.enableNetwork(),15,TimeUnit.SECONDS);}
  admin();FirebaseFunctions.getInstance().useEmulator("10.0.2.2",4999);
  try(ActivityScenario<AdminMainActivity> s=ActivityScenario.launch(AdminMainActivity.class)){
   waitText(s,"Không hoàn tất");waitText(s,"Thử lại");screenshot("admin-network-error.png");FirebaseFunctions.getInstance().useEmulator("10.0.2.2",5001);s.onActivity(a->exact(a.findViewById(android.R.id.content),"Thử lại").performClick());waitText(s,"Dashboard");
  }finally{FirebaseFunctions.getInstance().useEmulator("10.0.2.2",5001);}
 }
 @Test public void allMainRoutesBindVisibleActionsWithoutCrashing()throws Exception{
  String owner=user();Map<String,Object> post=new HashMap<>();post.put("ownerId",owner);post.put("title","QA route product");post.put("description","Navigation fixture");post.put("type","SALE");post.put("price",120000);post.put("condition","GOOD");post.put("status","ACTIVE");post.put("createdAt",FieldValue.serverTimestamp());post.put("updatedAt",FieldValue.serverTimestamp());String postId=Tasks.await(db.collection("posts").add(post),45,TimeUnit.SECONDS).getId();user();
  List<String> routes=asset("route-audit.txt"),results=new ArrayList<>(),failures=new ArrayList<>();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnCartHome)!=null,"home");
   for(String route:routes){s.onActivity(a->{try{field(a,"currentPostId",postId);field(a,"currentSellerId",owner);field(a,"viewedSellerId",owner);field(a,"currentOrderId","qa-missing-order");field(a,"currentFreeRequestId","qa-missing-request");field(a,"restoringScreen",true);show(a,a.getResources().getIdentifier(route,"layout",a.getPackageName()));field(a,"restoringScreen",false);List<String> gaps=new ArrayList<>();unbound(a,a.findViewById(android.R.id.content),gaps);results.add(route+" : "+(gaps.isEmpty()?"PASS":"UNBOUND "+gaps));if(!gaps.isEmpty())failures.add(route+" "+gaps);}catch(Throwable error){failures.add(route+" "+error);results.add(route+" FAIL "+error);}});}
  }report("navigation-audit.txt",results);assertTrue(android.text.TextUtils.join("\n",failures),failures.isEmpty());
 }
 private void unbound(Activity a,View view,List<String> gaps){if(view.getVisibility()!=View.VISIBLE)return;if(view.isClickable()&&view.isEnabled()&&!(view instanceof EditText)&&!(view instanceof Spinner)&&!(view instanceof CompoundButton)&&!view.hasOnClickListeners()){String name=view.getId()==View.NO_ID?view.getClass().getSimpleName():a.getResources().getResourceEntryName(view.getId());gaps.add(name);}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)unbound(a,((ViewGroup)view).getChildAt(i),gaps);}

 @Test public void invalidAuthAndProfileInputDoesNotSubmit()throws Exception{
  auth.signOut();try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnLogin)!=null,"login");clickId(s,R.id.btnLogin);s.onActivity(a->assertNotNull(((EditText)a.findViewById(R.id.edtLoginEmail)).getError()));
   s.onActivity(a->{((EditText)a.findViewById(R.id.edtLoginEmail)).setText("invalid-email");((EditText)a.findViewById(R.id.edtLoginPassword)).setText("Test-only-123!");a.findViewById(R.id.btnLogin).performClick();assertNotNull(((EditText)a.findViewById(R.id.edtLoginEmail)).getError());});assertNull(auth.getCurrentUser());
   clickId(s,R.id.btnOpenRegister);s.onActivity(a->{((EditText)a.findViewById(R.id.edtRegisterName)).setText("QA");((EditText)a.findViewById(R.id.edtRegisterEmail)).setText("bad-email");((EditText)a.findViewById(R.id.edtRegisterPassword)).setText("123");a.findViewById(R.id.btnCreateAccount).performClick();assertNotNull(((EditText)a.findViewById(R.id.edtRegisterEmail)).getError());});assertNull(auth.getCurrentUser());
  }
  user();try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnCartHome)!=null,"home");s.onActivity(a->show(a,R.layout.activity_edit_profile));waitText(s,"QA Tester");s.onActivity(a->{((EditText)a.findViewById(R.id.edtProfileBirthDate)).setText("31/02/2026");a.findViewById(R.id.btnEditProfileSave).performClick();assertNotNull(((EditText)a.findViewById(R.id.edtProfileBirthDate)).getError());assertTrue(a.findViewById(R.id.btnEditProfileSave).isEnabled());});
  }
 }
 @Test public void checkoutAddressDialogKeepsDraftAndCreatesOneRecord()throws Exception{
  String owner=user();Map<String,Object> post=new HashMap<>();post.put("ownerId",owner);post.put("title","QA checkout dialog");post.put("description","Test");post.put("type","SALE");post.put("price",120000);post.put("condition","GOOD");post.put("status","ACTIVE");post.put("createdAt",FieldValue.serverTimestamp());post.put("updatedAt",FieldValue.serverTimestamp());String postId=Tasks.await(db.collection("posts").add(post),45,TimeUnit.SECONDS).getId();String buyer=user();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitFor(s,a->a.findViewById(R.id.btnCartHome)!=null,"home");s.onActivity(a->{field(a,"currentPostId",postId);show(a,R.layout.activity_checkout);});clickId(s,R.id.btnNewCheckoutAddress);
   onView(withText("Lưu")).perform(click());onView(withHint("Tên người nhận")).check((view,error)->assertNotNull(((EditText)view).getError()));
   onView(withHint("Tên người nhận")).perform(replaceText("Người nhận QA"),closeSoftKeyboard());onView(withHint("Số điện thoại")).perform(replaceText("0901234567"),closeSoftKeyboard());rotate(s,true);waitText(s,"Checkout một món");onView(withHint("Tên người nhận")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("Người nhận QA")));
   for(String hint:new String[]{"Tỉnh / thành phố","Quận / huyện","Phường / xã","Số nhà, đường"})onView(withHint(hint)).perform(scrollTo(),replaceText("Địa chỉ QA"),closeSoftKeyboard());
   onView(withText("Lưu")).perform(click());waitFor(s,a->a.findViewById(R.id.addressChoice)!=null,"address saved");assertEquals(1,Tasks.await(db.collection("users").document(buyer).collection("addresses").get(),30,TimeUnit.SECONDS).size());s.recreate();waitFor(s,a->a.findViewById(R.id.btnPlaceOrder)!=null&&a.findViewById(R.id.btnPlaceOrder).isEnabled(),"checkout address restored");
  }
 }
}
