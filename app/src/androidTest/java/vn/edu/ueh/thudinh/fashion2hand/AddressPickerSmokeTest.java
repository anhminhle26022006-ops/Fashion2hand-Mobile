package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.AutoCompleteTextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Offline UI controls only: no Auth signup, account change or Firebase writes. */
@RunWith(AndroidJUnit4.class)
public class AddressPickerSmokeTest {
    @Test public void searchesCompleteDirectoryWithAccentsAliasesAndResetsToAll63() throws Exception {
        java.util.concurrent.atomic.AtomicReference<AddressSearchAdapter> reference=new java.util.concurrent.atomic.AtomicReference<>();
        onMain(()->{try{
            VietnamAddressCatalog catalog=new VietnamAddressCatalog(new java.io.InputStreamReader(context().getAssets().open("vietnam-addresses-legacy.tsv"),java.nio.charset.StandardCharsets.UTF_8));
            reference.set(new AddressSearchAdapter(context(),catalog.provinces.keySet()));
        }catch(Exception error){throw new AssertionError(error);}});
        for(String query:new String[]{"Hồ Chí Minh","ho chi minh","TP.HCM","Sài Gòn","Hà Nội",""}){
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
            onMain(()->reference.get().getFilter().filter(query,n->{count.set(n);done.countDown();}));
            assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(query,count.get()>0);
            onMain(()->{if(query.isEmpty())assertEquals(63,reference.get().getCount());else if(!query.equals("Hà Nội"))assertEquals("Thành phố Hồ Chí Minh",reference.get().getItem(0));});
        }
    }
    private void onMain(Runnable check){InstrumentationRegistry.getInstrumentation().runOnMainSync(check);}
    private Context context(){return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_Fashion2hand);}
    @Test public void legacyPickerShows63AndClearsChildrenWhenParentChanges(){onMain(()->{
        Context c=context();AutoCompleteTextView p=new AutoCompleteTextView(c),d=new AutoCompleteTextView(c),w=new AutoCompleteTextView(c);
        try{
            AddressPicker picker=new AddressPicker(c,p,d,w);assertEquals(63,p.getAdapter().getCount());
            p.setText("Thành phố Hồ Chí Minh",false);assertTrue(d.isEnabled());d.setText("Quận 8",false);w.setText("Phường 5",false);
            Map<String,Object> saved=new HashMap<>();assertTrue(picker.apply(saved));assertEquals("VN_3_LEVEL_LEGACY",saved.get("addressVersion"));assertEquals("Quận 8",saved.get("district"));assertNotNull(saved.get("districtCode"));
            d.setText("Quận 7",false);assertEquals("",w.getText().toString());
            p.setText("Thành phố Hà Nội",false);assertEquals("",d.getText().toString());assertEquals("",w.getText().toString());assertFalse(w.isEnabled());
        }catch(Exception error){throw new AssertionError(error);}
    });}
    @Test public void existingCurrentDirectoryStillLoads34AndKeepsTwoLevelCodes(){onMain(()->{
        Context c=context();AutoCompleteTextView p=new AutoCompleteTextView(c),d=new AutoCompleteTextView(c),w=new AutoCompleteTextView(c);
        try{
            AddressPicker picker=new AddressPicker(c,p,d,w);Map<String,Object> old=new HashMap<>();old.put("addressVersion","VN_2_LEVEL_2025");picker.useSavedVersion(old);
            assertEquals(34,p.getAdapter().getCount());assertEquals(View.GONE,d.getVisibility());p.setText("Thành phố Hồ Chí Minh",false);w.setText("Phường Sài Gòn",false);
            Map<String,Object> saved=new HashMap<>();assertTrue(picker.apply(saved));assertEquals("VN_2_LEVEL_2025",saved.get("addressVersion"));assertEquals("",saved.get("district"));
        }catch(Exception error){throw new AssertionError(error);}
    });}
    @Test public void wardlessIslandDistrictDoesNotRequireInventedWard(){onMain(()->{
        Context c=context();AutoCompleteTextView p=new AutoCompleteTextView(c),d=new AutoCompleteTextView(c),w=new AutoCompleteTextView(c);
        try{
            AddressPicker picker=new AddressPicker(c,p,d,w);p.setText("Tỉnh Bà Rịa - Vũng Tàu",false);d.setText("Huyện Côn Đảo",false);
            assertFalse(picker.wardRequired());assertFalse(w.isEnabled());Map<String,Object> saved=new HashMap<>();assertTrue(picker.apply(saved));assertEquals("",saved.get("wardCode"));
        }catch(Exception error){throw new AssertionError(error);}
    });}
}
