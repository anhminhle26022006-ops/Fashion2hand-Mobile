package vn.edu.ueh.thudinh.fashion2hand;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Keeps app navigation outside each screen's scrollable content. */
final class MainNavigation {
    private MainNavigation() {}

    static boolean destination(int layout) {
        return layout==R.layout.activity_home||layout==R.layout.activity_explore
                ||layout==R.layout.activity_sell||layout==R.layout.activity_messages||layout==R.layout.activity_profile;
    }

    static boolean visible(int layout) {
        return layout!=R.layout.activity_splash&&layout!=R.layout.activity_login&&layout!=R.layout.activity_register
                &&layout!=R.layout.activity_forgot_password&&layout!=R.layout.activity_new_password
                &&layout!=R.layout.activity_password_reset_success&&layout!=R.layout.activity_otp_verification
                &&layout!=R.layout.activity_contact_required;
    }

    static void applyInsets(Activity activity){
        ViewGroup root=activity.findViewById(android.R.id.content);
        if(root==null||root.getChildCount()==0)return;
        View content=root.getChildAt(0);
        int left=content.getPaddingLeft(),top=content.getPaddingTop(),right=content.getPaddingRight(),bottom=content.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets keyboard=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+Math.max(bars.bottom,keyboard.bottom));
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }

    static void attach(Activity activity,int screen,IntConsumer navigate) {
        if(!visible(screen))return;
        ViewGroup window=activity.findViewById(android.R.id.content);
        if(window==null||window.getChildCount()==0)return;
        if(activity.findViewById(R.id.mainBottomNavigation)==null){
            View content=window.getChildAt(0);window.removeView(content);
            LinearLayout shell=new LinearLayout(activity);shell.setId(R.id.mainNavigationShell);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            activity.getLayoutInflater().inflate(R.layout.bottom_navigation,shell,true);
            window.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        }
        int[] ids={R.id.navHome,R.id.navExplore,R.id.navCreate,R.id.navMessages,R.id.navProfile};
        int[] layouts={R.layout.activity_home,R.layout.activity_explore,R.layout.activity_sell,R.layout.activity_messages,R.layout.activity_profile};
        for(int i=0;i<ids.length;i++){
            View tab=activity.findViewById(ids[i]);final int target=layouts[i];
            tab.setOnClickListener(v->navigate.accept(target));
            tab.setSelected(screen==target);tab.setActivated(screen==target);
            if(tab instanceof ViewGroup){
                ViewGroup group=(ViewGroup)tab;
                for(int j=0;j<group.getChildCount();j++)if(group.getChildAt(j) instanceof TextView){
                    TextView text=(TextView)group.getChildAt(j);
                    text.setTypeface(null,screen==target?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                }
            }
        }
    }
}
