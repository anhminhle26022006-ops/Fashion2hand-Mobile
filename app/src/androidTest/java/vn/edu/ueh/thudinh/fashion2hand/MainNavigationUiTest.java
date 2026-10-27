package vn.edu.ueh.thudinh.fashion2hand;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainNavigationUiTest {
    private Object field(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private void screen(MainActivity activity,int layout) throws Exception {
        Field field=MainActivity.class.getDeclaredField("currentScreen");field.setAccessible(true);field.set(activity,layout);
    }
    private Object invoke(Object target,String name) throws Exception {
        Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target);
    }
    private void isolate(MainActivity activity) throws Exception {
        invoke(activity,"clearScreenListeners");screen(activity,R.layout.activity_login);
    }
    private int count(View view,int id){
        int result=view.getId()==id?1:0;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result+=count(((ViewGroup)view).getChildAt(i),id);
        return result;
    }

    @Test public void navigationStaysOutsideScrollingContentAndAllTabsWork() {
        AtomicInteger selected=new AtomicInteger(),navTop=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{try{
                isolate(activity);
                ScrollView scroll=new ScrollView(activity);scroll.setId(R.id.transactionScroll);
                TextView content=new TextView(activity);content.setText("Chi tiết đơn hàng");content.setMinHeight(4000);scroll.addView(content);
                activity.setContentView(scroll);
                MainNavigation.attach(activity,R.layout.activity_order_detail,selected::set);
                MainNavigation.attach(activity,R.layout.activity_order_detail,selected::set);
            }catch(Exception error){throw new AssertionError(error);}});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                View nav=activity.findViewById(R.id.mainBottomNavigation),scroll=activity.findViewById(R.id.transactionScroll);
                assertEquals(1,count(activity.findViewById(android.R.id.content),R.id.mainBottomNavigation));
                assertTrue(nav.getHeight()>0);assertTrue(scroll.getBottom()<=nav.getTop());
                navTop.set(nav.getTop());((ScrollView)scroll).fullScroll(View.FOCUS_DOWN);
                int[] ids={R.id.navHome,R.id.navExplore,R.id.navCreate,R.id.navMessages,R.id.navProfile};
                int[] layouts={R.layout.activity_home,R.layout.activity_explore,R.layout.activity_sell,R.layout.activity_messages,R.layout.activity_profile};
                for(int i=0;i<ids.length;i++){assertTrue(activity.findViewById(ids[i]).performClick());assertEquals(layouts[i],selected.get());}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->assertEquals(navTop.get(),activity.findViewById(R.id.mainBottomNavigation).getTop()));
        }
    }

    @Test public void existingNavigationIsReusedAndAuthenticationScreensStaySeparate(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{try{
                isolate(activity);
                for(int layout:new int[]{R.layout.activity_home,R.layout.activity_explore,R.layout.activity_profile,R.layout.activity_messages,R.layout.activity_favorites,R.layout.activity_my_listings}){
                    activity.setContentView(layout);View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                    MainNavigation.attach(activity,layout,value->{});
                    assertSame(root,((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0));
                    assertEquals(1,count(root,R.id.mainBottomNavigation));
                }
                for(int layout:new int[]{R.layout.activity_login,R.layout.activity_register,R.layout.activity_splash,R.layout.activity_otp_verification,R.layout.activity_contact_required}){
                    activity.setContentView(layout);MainNavigation.attach(activity,layout,value->fail("Unexpected navigation"));
                    assertNull(activity.findViewById(R.id.mainBottomNavigation));
                }
            }catch(Exception error){throw new AssertionError(error);}});
        }
    }

    @Test public void transactionScreenCanSwitchTabsWithoutWalkingBackThroughScreens(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{try{
                isolate(activity);screen(activity,R.layout.activity_order_detail);
                TransactionScreens screens=(TransactionScreens)field(activity,"transactionScreens");screens.prepare(R.layout.activity_order_detail);
                invoke(activity,"bindNavigation");
                @SuppressWarnings("unchecked") Deque<Object> backStack=(Deque<Object>)field(activity,"backStack");
                backStack.add(invoke(activity,"captureScreen"));
                assertNotNull(activity.findViewById(R.id.mainBottomNavigation));
                assertTrue(activity.findViewById(R.id.navExplore).performClick());
                assertEquals(R.layout.activity_explore,field(activity,"currentScreen"));
                assertTrue(backStack.isEmpty());assertTrue(activity.findViewById(R.id.navExplore).isSelected());
                invoke(activity,"clearScreenListeners");
            }catch(Exception error){throw new AssertionError(error);}});
        }
    }
}
