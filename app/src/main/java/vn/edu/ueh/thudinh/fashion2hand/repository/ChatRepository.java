package vn.edu.ueh.thudinh.fashion2hand.repository;

import androidx.lifecycle.LiveData;
import com.google.firebase.firestore.*;
import vn.edu.ueh.thudinh.fashion2hand.model.Message;
import java.util.*;

/** Cursor pagination plus a bounded realtime tail; no alternate message collection. */
public final class ChatRepository {
    public static final int PAGE_SIZE = 40;
    private final FirebaseFirestore firestore;
    public ChatRepository() { this(FirebaseFirestore.getInstance()); }
    public ChatRepository(FirebaseFirestore firestore) { this.firestore = firestore; }

    public static final class State {
        public final List<Message> messages;
        public final boolean loading, hasMore, fromCache;
        public final Exception error;
        State(List<Message> messages, boolean loading, boolean hasMore, boolean fromCache, Exception error) {
            this.messages = Collections.unmodifiableList(messages); this.loading = loading;
            this.hasMore = hasMore; this.fromCache = fromCache; this.error = error;
        }
    }

    public MessageFeed messages(String chatId) { return new MessageFeed(firestore, chatId); }

    public static final class MessageFeed extends LiveData<State> {
        private final Query query;
        private final Map<String, Message> records = new HashMap<>();
        private DocumentSnapshot oldest;
        private ListenerRegistration listener;
        private boolean loading, paging, hasMore, receivedServer, fromCache = true;
        private long generation;

        private MessageFeed(FirebaseFirestore firestore, String chatId) {
            query = firestore.collection("chats").document(chatId)
                    .collection("messages").orderBy("createdAt", Query.Direction.DESCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING);
        }

        @Override protected void onActive() {
            // Restart from the newest page after backgrounding so there cannot be an unread gap.
            records.clear(); oldest = null; hasMore = false; loading = true;
            paging = false; receivedServer = false; fromCache = true;
            publish(null);
            long token = ++generation;
            listener = query.limit(PAGE_SIZE).addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                if (token != generation) return;
                loading = false;
                if (error != null) { publish(error); return; }
                if (snapshot == null) return;
                fromCache = snapshot.getMetadata().isFromCache();
                if (!fromCache && !receivedServer) {
                    records.clear(); oldest = last(snapshot);
                    hasMore = snapshot.size() == PAGE_SIZE; receivedServer = true;
                }
                merge(snapshot);
                publish(null);
            });
        }

        @Override protected void onInactive() {
            generation++;
            if (listener != null) listener.remove();
            listener = null;
        }

        public void loadOlder() {
            if (loading || paging || !hasMore || oldest == null || !hasActiveObservers()) return;
            paging = true; publish(null);
            long token = generation;
            query.startAfter(oldest).limit(PAGE_SIZE).get(Source.SERVER).addOnCompleteListener(task -> {
                if (token != generation) return;
                paging = false;
                if (!task.isSuccessful()) { publish(task.getException()); return; }
                QuerySnapshot page = task.getResult(); merge(page);
                if (!page.isEmpty()) oldest = last(page);
                hasMore = page.size() == PAGE_SIZE;
                publish(null);
            });
        }

        private DocumentSnapshot last(QuerySnapshot snapshot) {
            return snapshot.isEmpty() ? null : snapshot.getDocuments().get(snapshot.size() - 1);
        }
        private void merge(QuerySnapshot snapshot) {
            for (DocumentSnapshot document : snapshot) records.put(document.getId(), Message.from(document));
        }
        private void publish(Exception error) {
            ArrayList<Message> result = new ArrayList<>(records.values());
            result.sort((a, b) -> {
                int comparison = a.createdAt == null ? (b.createdAt == null ? 0 : -1)
                        : b.createdAt == null ? 1 : a.createdAt.compareTo(b.createdAt);
                return comparison == 0 ? a.id.compareTo(b.id) : comparison;
            });
            setValue(new State(result, loading || paging, hasMore, fromCache, error));
        }
    }
}
