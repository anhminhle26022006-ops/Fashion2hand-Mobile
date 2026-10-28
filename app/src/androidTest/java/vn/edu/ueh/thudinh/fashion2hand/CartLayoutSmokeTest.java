package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Context;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Offline layout rendering only: no account access, cart writes or checkout calls. */
@RunWith(AndroidJUnit4.class)
public class CartLayoutSmokeTest {
    @Test public void cardsAndPinnedCheckoutFitSmallAndNormalPhones() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_Fashion2hand);
            float density=context.getResources().getDisplayMetrics().density;
            for(int widthDp:new int[]{360,411}) {
                int width=(int)(widthDp*density),height=(int)(740*density);
                LinearLayout root=new LinearLayout(context);root.setOrientation(1);root.setBackgroundColor(0xFFF8F6F0);
                TextView heading=new TextView(context);heading.setText("Giỏ hàng");heading.setTextSize(20);heading.setPadding((int)(20*density),0,0,0);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading,new LinearLayout.LayoutParams(-1,(int)(56*density)));
                ScrollView scroll=new ScrollView(context);LinearLayout list=new LinearLayout(context);list.setOrientation(1);list.setPadding((int)(16*density),(int)(12*density),(int)(16*density),0);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
                View first=null;
                for(String title:new String[]{"Dotty cute dress","Vintage flower shirts (combo 2)"}) {
                    View item=LayoutInflater.from(context).inflate(R.layout.view_cart_item,list,false);
                    ((TextView)item.findViewById(R.id.cartItemTitle)).setText(title);
                    ((TextView)item.findViewById(R.id.cartItemSeller)).setText("Người bán: Đinh Thị Anh Thu");
                    ((TextView)item.findViewById(R.id.cartItemDetail)).setText("Số lượng: 1 · M");
                    ((TextView)item.findViewById(R.id.cartItemPrice)).setText("59.000 đ");
                    ((CheckBox)item.findViewById(R.id.cartItemChecked)).setChecked(true);
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=(int)(12*density);list.addView(item,params);if(first==null)first=item;
                }
                View footer=LayoutInflater.from(context).inflate(R.layout.view_cart_footer,root,false);root.addView(footer);
                ((TextView)footer.findViewById(R.id.cartTotalAmount)).setText("118.000 đ");((CheckBox)footer.findViewById(R.id.cartSelectAll)).setChecked(true);
                Button checkout=footer.findViewById(R.id.btnCheckout);checkout.setText("Thanh toán (2)");
                View nav=LayoutInflater.from(context).inflate(R.layout.bottom_navigation,root,false);root.addView(nav);
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
                assertTrue(scroll.getBottom()<=footer.getTop());assertTrue(footer.getBottom()<=nav.getTop());
                assertTrue(first.findViewById(R.id.cartItemImage).getRight()<first.findViewById(R.id.cartItemRemove).getLeft());
                assertTrue(first.findViewById(R.id.cartItemTitle).getWidth()>100*density);
                assertEquals(1,checkout.getLineCount());assertEquals(1,((TextView)footer.findViewById(R.id.cartTotalAmount)).getLineCount());
                if(widthDp==411) {
                    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"cart-layout-preview.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}catch(Exception error){throw new AssertionError(error);}
                    bitmap.recycle();
                }
            }
        });
    }
}
