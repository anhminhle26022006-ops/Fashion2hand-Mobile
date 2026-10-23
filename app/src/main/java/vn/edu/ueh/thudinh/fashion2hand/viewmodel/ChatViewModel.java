package vn.edu.ueh.thudinh.fashion2hand.viewmodel;

import androidx.lifecycle.ViewModel;
import vn.edu.ueh.thudinh.fashion2hand.repository.ChatRepository;

public final class ChatViewModel extends ViewModel {
    private String chatId = "";
    private ChatRepository.MessageFeed feed;
    private String pendingChat = "", pendingText = "", pendingKey = "";
    public com.google.android.gms.tasks.Task<com.google.firebase.functions.HttpsCallableResult> send(String id, String text) {
        if (!pendingChat.equals(id) || !pendingText.equals(text) || pendingKey.isEmpty()) {
            pendingChat = id; pendingText = text; pendingKey = java.util.UUID.randomUUID().toString();
        }
        String key = pendingKey;
        return vn.edu.ueh.thudinh.fashion2hand.repository.MemberTwoRepository.getInstance().sendMessage(id, text, key)
                .addOnSuccessListener(result -> { if (pendingKey.equals(key)) pendingKey = ""; });
    }
    public ChatRepository.MessageFeed open(String id) {
        if (feed == null || !chatId.equals(id)) {
            chatId = id; feed = new ChatRepository().messages(id);
        }
        return feed;
    }
}
