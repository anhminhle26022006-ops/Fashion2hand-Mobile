package vn.edu.ueh.thudinh.fashion2hand.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** Immutable projection of the team's chats/{chatId}/messages schema. */
public final class Message {
    public final String id, senderId, text;
    public final Timestamp createdAt;
    public final boolean read;

    public Message(String id, String senderId, String text, Timestamp createdAt, boolean read) {
        this.id = id; this.senderId = senderId; this.text = text;
        this.createdAt = createdAt; this.read = read;
    }

    public static Message from(DocumentSnapshot document) {
        String sender = document.getString("senderId"), text = document.getString("text");
        return new Message(document.getId(), sender == null ? "" : sender, text == null ? "" : text,
                document.getTimestamp("createdAt"), Boolean.TRUE.equals(document.getBoolean("read")));
    }
}
