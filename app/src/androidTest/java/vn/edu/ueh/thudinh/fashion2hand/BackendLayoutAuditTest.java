package vn.edu.ueh.thudinh.fashion2hand;

import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Inflation regression for all layouts; does not change Auth or write backend records. */
@RunWith(AndroidJUnit4.class)
public class BackendLayoutAuditTest {
    @Test public void allLayoutsStillInflateInPortraitAndLandscapeAfterBackendWiring() throws Exception {
        List<String> layouts=new ArrayList<>(),failures=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("layout-audit.txt")))){String line;while((line=reader.readLine())!=null)if(!line.trim().isEmpty())layouts.add(line.trim());}
        assertEquals(81,layouts.size());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{
                for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}){
                    Configuration config=new Configuration(activity.getResources().getConfiguration());config.orientation=orientation;
                    ContextThemeWrapper context=new ContextThemeWrapper(activity.createConfigurationContext(config),R.style.Theme_Fashion2hand);
                    LayoutInflater inflater=activity.getLayoutInflater().cloneInContext(context);
                    for(String name:layouts){try{int id=context.getResources().getIdentifier(name,"layout",activity.getPackageName());assertTrue(name,id!=0);inflater.inflate(id,new FrameLayout(context),false);}catch(Throwable error){failures.add(name+" orientation="+orientation+": "+error);}}
                }
            });
        }
        assertTrue(android.text.TextUtils.join("\n",failures),failures.isEmpty());
    }
}
