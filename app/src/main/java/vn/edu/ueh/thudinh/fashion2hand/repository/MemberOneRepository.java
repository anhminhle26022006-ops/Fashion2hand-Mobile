package vn.edu.ueh.thudinh.fashion2hand.repository;

import android.graphics.Bitmap;
import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Firebase data access for Member 1: users, listings, favorites, and listing photos. */
public final class MemberOneRepository {
    private static MemberOneRepository instance;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public static synchronized MemberOneRepository getInstance() {
        if (instance == null) instance = new MemberOneRepository();
        return instance;
    }

    private MemberOneRepository() { }

    public String newPostId() { return firestore.collection("posts").document().getId(); }

    public Task<Void> saveListing(String uid, String postId, Map<String, Object> listing,
                                  List<Object> photos, boolean existingPost) {
        Map<String, Object> data = new HashMap<>(listing);
        data.put("ownerId", uid);
        data.put("updatedAt", FieldValue.serverTimestamp());
        DocumentReference post = firestore.collection("posts").document(postId);
        if (!data.containsKey("createdAt")) data.put("createdAt", FieldValue.serverTimestamp());
        Object finalStatus = data.get("status");
        boolean hasPhotos = photos != null && !photos.isEmpty();
        if (!hasPhotos) return post.set(data, com.google.firebase.firestore.SetOptions.merge());

        // New listings start as DRAFT so Storage rules can verify ownership before photo upload.
        if (!existingPost) data.put("status", "DRAFT");
        return post.set(data, com.google.firebase.firestore.SetOptions.merge()).continueWithTask(created -> {
            if (!created.isSuccessful()) throw created.getException();
            return uploadPhotos(postId, photos);
        }).continueWithTask(uploaded -> {
            if (!uploaded.isSuccessful()) throw uploaded.getException();
            Map<String, Object> updates = new HashMap<>();
            updates.put("imageUrls", uploaded.getResult());
            updates.put("status", finalStatus);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            return post.update(updates);
        });
    }

    private Task<List<String>> uploadPhotos(String postId, List<Object> photos) {
        List<Task<String>> uploads = new ArrayList<>();
        if (photos == null || photos.size() > 8) return com.google.android.gms.tasks.Tasks.forException(new IllegalArgumentException("Tối đa 8 ảnh cho mỗi tin đăng."));
        for (int i = 0; i < photos.size(); i++) {
            Object photo = photos.get(i);
            uploads.add(uploadPhoto(postId, i, photo instanceof Uri ? (Uri) photo : null,
                    photo instanceof Bitmap ? (Bitmap) photo : null));
        }
        return com.google.android.gms.tasks.Tasks.whenAllSuccess(uploads);
    }

    private Task<String> uploadPhoto(String postId, int index, Uri photoUri, Bitmap photoBitmap) {
        StorageReference ref = storage.getReference().child("posts/" + postId + "/images/" + index + ".jpg");
        TaskCompletionSource<String> result = new TaskCompletionSource<>();
        com.google.firebase.storage.UploadTask upload;
        if (photoUri != null) {
            String mime = com.google.firebase.FirebaseApp.getInstance().getApplicationContext().getContentResolver().getType(photoUri);
            com.google.firebase.storage.StorageMetadata metadata = new com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(mime != null ? mime : "image/jpeg").build();
            upload = ref.putFile(photoUri, metadata);
        }
        else {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            photoBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
            upload = ref.putBytes(output.toByteArray(), new com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg").build());
        }
        upload.continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return ref.getDownloadUrl();
        }).addOnSuccessListener(uri -> result.setResult(ref.toString()))
                .addOnFailureListener(result::setException);
        return result.getTask();
    }

    public ListenerRegistration listenMyListings(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("posts").whereEqualTo("ownerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, listener);
    }

    public Task<Void> deleteListing(String postId) {
        return firestore.collection("posts").document(postId).delete();
    }

    public Task<Void> setListingStatus(String postId, String status) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("updatedAt", FieldValue.serverTimestamp());
        return firestore.collection("posts").document(postId).update(update);
    }

    public Task<Void> saveUserProfile(String uid, Map<String, Object> profile, Uri avatarUri, Bitmap avatarBitmap) {
        Map<String, Object> updates = new HashMap<>(profile);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        Task<String> avatarTask;
        if (avatarUri != null || avatarBitmap != null) {
            StorageReference ref = storage.getReference().child("users/" + uid + "/avatar/profile.jpg");
            com.google.firebase.storage.UploadTask upload;
            if (avatarUri != null) upload = ref.putFile(avatarUri);
            else {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                avatarBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
                upload = ref.putBytes(output.toByteArray());
            }
            avatarTask = upload.continueWithTask(task -> {
                if (!task.isSuccessful()) throw task.getException();
                return ref.getDownloadUrl();
            }).continueWith(task -> {
                if (!task.isSuccessful()) throw task.getException();
                return task.getResult().toString();
            });
        } else avatarTask = com.google.android.gms.tasks.Tasks.forResult(null);
        return avatarTask.continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            if (task.getResult() != null) updates.put("photoUrl", task.getResult());
            return firestore.collection("users").document(uid).update(updates);
        });
    }

    public Task<Void> setFavorite(String uid, String postId, boolean favorite) {
        DocumentReference ref = firestore.collection("users").document(uid)
                .collection("favorites").document(postId);
        if (favorite) {
            Map<String, Object> value = new HashMap<>();
            value.put("postId", postId);
            value.put("createdAt", FieldValue.serverTimestamp());
            return ref.set(value);
        }
        return ref.delete();
    }

    public ListenerRegistration listenFavorites(String uid, EventListener<QuerySnapshot> listener) {
        return firestore.collection("users").document(uid).collection("favorites")
                .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, listener);
    }
}
