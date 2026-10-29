package vn.edu.ueh.thudinh.fashion2hand;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AddressFormFlowTest {
    @Test public void profileDefaultsStayEditableAndProvinceChangeClearsWard() throws Exception {
        FirebaseAuth auth=FirebaseAuth.getInstance();FirebaseUser previous=auth.getCurrentUser(),fixture=null;
        try {
            fixture=Tasks.await(auth.createUserWithEmailAndPassword("address-ui-"+System.nanoTime()+"@example.com","Test-only-123!"),45,TimeUnit.SECONDS).getUser();
            VerifiedAccountFixture.verify(auth);
            Tasks.await(auth.getCurrentUser().updateProfile(new UserProfileChangeRequest.Builder().setDisplayName("Profile recipient").build()),30,TimeUnit.SECONDS);
            Map<String,Object> profile=new HashMap<>();profile.put("uid",fixture.getUid());profile.put("name","Profile recipient");profile.put("email",fixture.getEmail());profile.put("role","USER");profile.put("accountStatus","ACTIVE");
            Tasks.await(FirebaseFirestore.getInstance().document("users/"+fixture.getUid()).set(profile),45,TimeUnit.SECONDS);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                Thread.sleep(3000);
                scenario.onActivity(activity->{try{java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("showScreen",int.class);open.setAccessible(true);open.invoke(activity,R.layout.activity_address_form);}catch(Exception e){throw new AssertionError(e);}});
                long deadline=android.os.SystemClock.elapsedRealtime()+45000;AtomicBoolean ready=new AtomicBoolean();
                while(!ready.get()&&android.os.SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->{android.view.View save=a.findViewById(R.id.btnSaveAddress);ready.set(save!=null&&save.isEnabled());});Thread.sleep(100);}
                assertTrue("Address form loaded",ready.get());
                scenario.onActivity(a->{
                    assertEquals("Profile recipient",((EditText)a.findViewById(R.id.edtAddressRecipient)).getText().toString());
                    assertFalse(((EditText)a.findViewById(R.id.edtAddressPhone)).getText().toString().isEmpty());
                    ((EditText)a.findViewById(R.id.edtAddressRecipient)).setText("Another recipient");
                    ((EditText)a.findViewById(R.id.edtAddressPhone)).setText("0987654321");
                    AutoCompleteTextView province=a.findViewById(R.id.edtAddressProvince),district=a.findViewById(R.id.edtAddressDistrict),ward=a.findViewById(R.id.edtAddressWard);
                    assertEquals(63,province.getAdapter().getCount());
                    province.setText("Thành phố Hà Nội",false);district.setText("Quận Ba Đình",false);ward.setText("Phường Phúc Xá",false);
                    assertTrue(ward.getAdapter().getCount()>0);
                    province.setText("Thành phố Hồ Chí Minh",false);assertEquals("",district.getText().toString());assertEquals("",ward.getText().toString());
                    district.setText("Quận 8",false);ward.setText("Phường 5",false);
                });
                scenario.recreate();Thread.sleep(2000);
                scenario.onActivity(a->{
                    assertEquals("Another recipient",((EditText)a.findViewById(R.id.edtAddressRecipient)).getText().toString());
                    assertEquals("0987654321",((EditText)a.findViewById(R.id.edtAddressPhone)).getText().toString());
                    assertEquals("Thành phố Hồ Chí Minh",((EditText)a.findViewById(R.id.edtAddressProvince)).getText().toString());
                    assertEquals("Quận 8",((EditText)a.findViewById(R.id.edtAddressDistrict)).getText().toString());
                    assertEquals("Phường 5",((EditText)a.findViewById(R.id.edtAddressWard)).getText().toString());
                });
            }
        } finally {
            if(fixture!=null)Tasks.await(fixture.delete(),30,TimeUnit.SECONDS);
            if(previous==null)auth.signOut();else Tasks.await(auth.updateCurrentUser(previous),45,TimeUnit.SECONDS);
        }
    }
}
