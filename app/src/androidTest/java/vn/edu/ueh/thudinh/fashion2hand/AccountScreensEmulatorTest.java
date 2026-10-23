package vn.edu.ueh.thudinh.fashion2hand;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.FirebaseFunctions;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AccountScreensEmulatorTest {
    interface Check { boolean ready(MainActivity activity); }
    private void waitFor(ActivityScenario<MainActivity> scenario, Check check) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+120000;
        while(android.os.SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();scenario.onActivity(a->ready.set(check.ready(a)));if(ready.get())return;Thread.sleep(150);}fail("Account UI state did not arrive");
    }
    private void isolate(MainActivity a,int layout) {
        try {
            java.lang.reflect.Method clear=MainActivity.class.getDeclaredMethod("clearScreenListeners");clear.setAccessible(true);clear.invoke(a);
            java.lang.reflect.Field screen=MainActivity.class.getDeclaredField("currentScreen");screen.setAccessible(true);screen.setInt(a,layout);a.setContentView(layout);
        }catch(Exception error){throw new AssertionError(error);}
    }
    private void fill(MainActivity a,int id,String value){((EditText)a.findViewById(id)).setText(value);}

    @Test public void accountFormsPersistAddressesPayoutVerificationAndSupportWithoutChangingTheUserSession() throws Exception {
        FirebaseApp primary=FirebaseApp.getInstance();String signedInUid=FirebaseAuth.getInstance().getUid();
        FirebaseApp isolated=FirebaseApp.initializeApp(primary.getApplicationContext(),primary.getOptions(),"account-ui-"+System.nanoTime());
        FirebaseAuth auth=FirebaseAuth.getInstance(isolated);auth.useEmulator("10.0.2.2",9099);
        FirebaseFirestore db=FirebaseFirestore.getInstance(isolated);db.useEmulator("10.0.2.2",8080);
        FirebaseFunctions functions=FirebaseFunctions.getInstance(isolated);functions.useEmulator("10.0.2.2",5001);
        String uid=Tasks.await(auth.createUserWithEmailAndPassword("account-ui-"+System.nanoTime()+"@example.com","Test-only-123!"),30,TimeUnit.SECONDS).getUser().getUid();
        Map<String,Object> profile=new HashMap<>();profile.put("uid",uid);profile.put("name","Account UI QA");profile.put("email",auth.getCurrentUser().getEmail());profile.put("role","USER");profile.put("accountStatus","ACTIVE");profile.put("createdAt",FieldValue.serverTimestamp());Tasks.await(db.document("users/"+uid).set(profile),30,TimeUnit.SECONDS);
        AtomicInteger destination=new AtomicInteger();AtomicReference<AccountScreens> controller=new AtomicReference<>();List<ListenerRegistration> listeners=new ArrayList<>();
        AccountScreens.Host host=new AccountScreens.Host(){public String uid(){return uid;}public void open(int screen){destination.set(screen);}public void listen(ListenerRegistration listener){listeners.add(listener);}public void pick(String slot){}public boolean restoringDraft(){return false;}};
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{isolate(a,R.layout.activity_address_form);AccountScreens screens=new AccountScreens(a,host,db,functions);controller.set(screens);screens.addressForm();});
            waitFor(scenario,a->a.findViewById(R.id.btnSaveAddress).isEnabled());
            scenario.onActivity(a->{fill(a,R.id.edtAddressRecipient,"Tên người nhận thật");fill(a,R.id.edtAddressPhone,"0901234567");fill(a,R.id.edtAddressProvince,"TP HCM");fill(a,R.id.edtAddressDistrict,"Quận 1");fill(a,R.id.edtAddressWard,"Phường 1");fill(a,R.id.edtAddressStreet,"1 Đường kiểm thử");a.findViewById(R.id.btnSaveAddress).performClick();});
            waitFor(scenario,a->destination.get()==R.layout.activity_addresses);
            QuerySnapshot addresses=Tasks.await(db.collection("users").document(uid).collection("addresses").get(Source.SERVER),30,TimeUnit.SECONDS);assertEquals(1,addresses.size());assertEquals("Tên người nhận thật",addresses.getDocuments().get(0).getString("recipient"));
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_addresses);controller.get().addresses();});
            waitFor(scenario,a->((LinearLayout)a.findViewById(R.id.addressCards)).getChildCount()>0&&((LinearLayout)a.findViewById(R.id.addressCards)).getChildAt(0) instanceof LinearLayout);
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_payment_info);controller.get().payout();});
            waitFor(scenario,a->a.findViewById(R.id.btnSavePayoutInfo).isEnabled());
            scenario.onActivity(a->{fill(a,R.id.edtPayoutHolder,"CHU TAI KHOAN");fill(a,R.id.edtPayoutAccountNumber,"0123456789");((CheckBox)a.findViewById(R.id.checkPayoutConsent)).setChecked(true);a.findViewById(R.id.btnSavePayoutInfo).performClick();});
            waitFor(scenario,a->((TextView)a.findViewById(R.id.txtPayoutStatus)).getText().toString().startsWith("Đã lưu backend"));
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_payment_info);controller.get().payout();});
            waitFor(scenario,a->"0123456789".contentEquals(((EditText)a.findViewById(R.id.edtPayoutAccountNumber)).getText()));
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_account_verification);controller.get().verificationForm();fill(a,R.id.edtLegalName,"Người xác minh UI");fill(a,R.id.edtLegalBirthDate,"01/01/2000");fill(a,R.id.edtIdentityNumber,"012345678901");((CheckBox)a.findViewById(R.id.checkIdentityAccurate)).setChecked(true);((CheckBox)a.findViewById(R.id.checkIdentityPrivacy)).setChecked(true);Bitmap image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);for(String slot:new String[]{"front","back","selfie"})controller.get().selectPhoto(slot,null,image);image.recycle();a.findViewById(R.id.btnSubmitVerification).performClick();});
            waitFor(scenario,a->destination.get()==R.layout.activity_verification_status);
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_verification_status);controller.get().verificationStatus();});
            waitFor(scenario,a->"Chờ duyệt".contentEquals(((TextView)a.findViewById(R.id.txtVerificationStatusValue)).getText()));
            scenario.onActivity(a->{assertEquals("Người xác minh UI",((TextView)a.findViewById(R.id.txtVerificationLegalName)).getText().toString());assertFalse(((TextView)a.findViewById(R.id.txtVerificationCaseNumber)).getText().toString().equals("—"));controller.get().invalidate();isolate(a,R.layout.activity_support_contact);controller.get().support();fill(a,R.id.edtSupportTitle,"Yêu cầu hỗ trợ UI");fill(a,R.id.edtSupportDescription,"Nội dung cần hỗ trợ được gửi từ giao diện kiểm thử.");a.findViewById(R.id.btnSendSupport).performClick();});
            waitFor(scenario,a->((TextView)a.findViewById(R.id.txtSupportStatus)).getText().toString().startsWith("Đã gửi yêu cầu:"));
            scenario.onActivity(a->{controller.get().invalidate();isolate(a,R.layout.activity_support_contact);controller.get().support();});
            waitFor(scenario,a->((TextView)a.findViewById(R.id.txtSupportStatus)).getText().toString().contains("Yêu cầu hỗ trợ UI"));
            assertEquals(signedInUid,FirebaseAuth.getInstance().getUid());
        }finally{for(ListenerRegistration listener:listeners)listener.remove();Tasks.await(db.terminate(),15,TimeUnit.SECONDS);Tasks.await(auth.getCurrentUser().delete(),15,TimeUnit.SECONDS);
            // Auth's emulator transport may retain app references across test classes.
            // Let instrumentation process teardown release this secondary app.
        }
    }
}
