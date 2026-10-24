package vn.edu.ueh.thudinh.fashion2hand;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FreeRequestStateFlowTest {
    private void write(String path,Map<String,Object> values) throws Exception {
        JSONObject fields=new JSONObject();
        for(Map.Entry<String,Object> entry:values.entrySet())fields.put(entry.getKey(),new JSONObject().put(entry.getValue() instanceof Number?"integerValue":"stringValue",entry.getValue().toString()));
        HttpURLConnection c=(HttpURLConnection)new URL("http://10.0.2.2:8080/v1/projects/fashion2hand/databases/(default)/documents/"+path).openConnection();
        c.setRequestMethod("PATCH");c.setDoOutput(true);c.setConnectTimeout(30000);c.setReadTimeout(30000);c.setRequestProperty("Authorization","Bearer owner");c.setRequestProperty("Content-Type","application/json");
        try{try(java.io.OutputStream out=c.getOutputStream()){out.write(new JSONObject().put("fields",fields).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertEquals(200,c.getResponseCode());}finally{c.disconnect();}
    }
    private void open(ActivityScenario<MainActivity> scenario,String method,Class<?> type,Object value){scenario.onActivity(a->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(a,value);}catch(Exception e){throw new AssertionError(e);}});}
    private void waitText(ActivityScenario<MainActivity> scenario,int id,String expected) throws Exception {
        long end=android.os.SystemClock.elapsedRealtime()+60000;AtomicBoolean found=new AtomicBoolean();
        while(!found.get()&&android.os.SystemClock.elapsedRealtime()<end){scenario.onActivity(a->{TextView view=a.findViewById(id);found.set(view!=null&&expected.equals(view.getText().toString()));});Thread.sleep(100);}assertTrue("Expected "+expected,found.get());
    }
    @Test public void sentRequestPersistsAcrossReopenAndCancellationEnablesNewRequest() throws Exception {
        FirebaseAuth auth=FirebaseAuth.getInstance();FirebaseUser previous=auth.getCurrentUser(),fixture=null;
        String suffix=UUID.randomUUID().toString(),postId="request-state-"+suffix,requestId="ea99aa38d4"+suffix.replace("-","");
        try{
            fixture=Tasks.await(auth.createUserWithEmailAndPassword("request-ui-"+suffix+"@example.com","Test-only-123!"),45,TimeUnit.SECONDS).getUser();VerifiedAccountFixture.verifyEmail(auth);
            Map<String,Object> profile=new HashMap<>();profile.put("uid",fixture.getUid());profile.put("name","Request test");profile.put("email",fixture.getEmail());profile.put("role","USER");profile.put("accountStatus","ACTIVE");
            Tasks.await(FirebaseFirestore.getInstance().document("users/"+fixture.getUid()).set(profile),45,TimeUnit.SECONDS);
            Map<String,Object> post=new HashMap<>();post.put("ownerId","giver-"+suffix);post.put("title","Gift request test");post.put("type","FREE");post.put("price",0);post.put("status","ACTIVE");post.put("description","Test gift");post.put("condition","GOOD");write("posts/"+postId,post);
            Map<String,Object> request=new HashMap<>();request.put("postId",postId);request.put("ownerId","giver-"+suffix);request.put("requesterId",fixture.getUid());request.put("status","PENDING");
            DocumentSnapshot gift=Tasks.await(FirebaseFirestore.getInstance().document("posts/"+postId).get(Source.SERVER),45,TimeUnit.SECONDS);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                Thread.sleep(3000);open(scenario,"openProduct",DocumentSnapshot.class,gift);
                waitText(scenario,R.id.btnRequestFree,"Gửi yêu cầu");
                write("freeRequests/"+requestId,request);
                waitText(scenario,R.id.btnRequestFree,"Đã gửi yêu cầu");
                scenario.recreate();waitText(scenario,R.id.btnRequestFree,"Đã gửi yêu cầu");
                scenario.onActivity(a->a.findViewById(R.id.btnRequestFree).performClick());
                Thread.sleep(1500);
                scenario.onActivity(a->{try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("currentScreen");field.setAccessible(true);assertEquals(R.layout.activity_request_status,field.getInt(a));}catch(Exception e){throw new AssertionError(e);}});
                request.put("status","CANCELLED");write("freeRequests/"+requestId,request);
                open(scenario,"openProduct",DocumentSnapshot.class,gift);waitText(scenario,R.id.btnRequestFree,"Gửi yêu cầu");
                scenario.onActivity(a->assertTrue(a.findViewById(R.id.btnRequestFree).isEnabled()));
                open(scenario,"showScreen",int.class,R.layout.activity_profile);
                waitText(scenario,R.id.txtVerificationCardTitle,"Xác minh danh tính (tùy chọn)");
            }
        }finally{
            if(fixture!=null)Tasks.await(fixture.delete(),30,TimeUnit.SECONDS);
            if(previous==null)auth.signOut();else Tasks.await(auth.updateCurrentUser(previous),45,TimeUnit.SECONDS);
        }
    }
}
