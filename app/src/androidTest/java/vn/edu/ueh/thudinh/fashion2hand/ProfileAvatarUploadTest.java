package vn.edu.ueh.thudinh.fashion2hand;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.ueh.thudinh.fashion2hand.repository.MemberOneRepository;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProfileAvatarUploadTest {
    @Test public void cameraBitmapUploadsAsJpegAndSavesProfile() throws Exception {
        assertTrue("This test requires Firebase Emulator", BuildConfig.USE_FIREBASE_EMULATORS);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser previous = auth.getCurrentUser(), fixture = null;
        StorageReference avatar = null;
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        try {
            fixture = Tasks.await(auth.createUserWithEmailAndPassword(
                    "avatar-camera-" + System.nanoTime() + "@example.test", "Test-only-123!"), 30, TimeUnit.SECONDS).getUser();
            VerifiedAccountFixture.verifyEmail(auth);
            String uid = fixture.getUid();
            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", uid); profile.put("name", "Camera avatar test");
            profile.put("role", "USER"); profile.put("accountStatus", "ACTIVE");
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Tasks.await(db.document("users/" + uid).set(profile), 30, TimeUnit.SECONDS);
            avatar = FirebaseStorage.getInstance().getReference().child("users/" + uid + "/avatar/profile.jpg");
            Map<String, Object> updates = new HashMap<>(); updates.put("name", "Camera avatar saved");
            Tasks.await(MemberOneRepository.getInstance().saveUserProfile(uid, updates, null, bitmap), 45, TimeUnit.SECONDS);
            assertEquals("image/jpeg", Tasks.await(avatar.getMetadata(), 30, TimeUnit.SECONDS).getContentType());
            assertTrue(Tasks.await(db.document("users/" + uid).get(), 30, TimeUnit.SECONDS).getString("photoUrl").startsWith("http"));
            assertEquals("Camera avatar saved", Tasks.await(db.document("users/" + uid).get(), 30, TimeUnit.SECONDS).getString("name"));
        } finally {
            bitmap.recycle();
            try {
                if (avatar != null) Tasks.await(avatar.delete(), 30, TimeUnit.SECONDS);
                if (fixture != null) Tasks.await(fixture.delete(), 30, TimeUnit.SECONDS);
            } finally {
                if (previous == null) auth.signOut();
                else Tasks.await(auth.updateCurrentUser(previous), 30, TimeUnit.SECONDS);
            }
        }
    }
}
