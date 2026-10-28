package vn.edu.ueh.thudinh.fashion2hand.ui.local;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.function.Consumer;

import vn.edu.ueh.thudinh.fashion2hand.R;
import vn.edu.ueh.thudinh.fashion2hand.repository.local.LocalHistoryRepository;

/**
 * Khu “Tìm gần đây” và “Đã xem gần đây” ở màn Khám phá, đọc từ SQLite trên máy.
 * MainActivity chỉ cần gọi {@link #bind} khi mở màn Khám phá và {@link #recordView} khi mở chi tiết bài.
 */
public final class RecentHistoryUi {

    private RecentHistoryUi() { }

    public static void bind(Activity activity, String uid, Consumer<DocumentSnapshot> openPost) {
        LocalHistoryRepository history = new LocalHistoryRepository(activity);
        EditText search = activity.findViewById(R.id.edtExploreSearch);
        if (search != null) {
            search.setOnEditorActionListener((v, actionId, event) -> {
                // Bấm Tìm/Enter trên bàn phím = một lượt tìm; lưu trùng chỉ cập nhật thời gian.
                history.addSearch(uid, search.getText().toString());
                render(activity, history, uid, openPost);
                return false; // để bàn phím vẫn xử lý như bình thường
            });
        }
        View clear = activity.findViewById(R.id.btnClearLocalHistory);
        if (clear != null) clear.setOnClickListener(v -> {
            history.clear(uid);
            render(activity, history, uid, openPost);
            Toast.makeText(activity, "Đã xóa lịch sử trên máy.", Toast.LENGTH_SHORT).show();
        });
        render(activity, history, uid, openPost);
    }

    /** Ghi lại bài vừa mở; gọi từ openProduct. */
    public static void recordView(Activity activity, String uid, String postId, String title, String priceText) {
        new LocalHistoryRepository(activity).addViewedPost(uid, postId, title, priceText);
    }

    private static void render(Activity activity, LocalHistoryRepository history, String uid, Consumer<DocumentSnapshot> openPost) {
        View section = activity.findViewById(R.id.layoutLocalHistory);
        LinearLayout searches = activity.findViewById(R.id.layoutRecentSearches);
        LinearLayout viewed = activity.findViewById(R.id.layoutRecentViewed);
        if (section == null || searches == null || viewed == null) return;

        List<String> recentSearches = history.getRecentSearches(uid);
        List<LocalHistoryRepository.ViewedPost> recentViewed = history.getRecentViewed(uid);
        section.setVisibility(recentSearches.isEmpty() && recentViewed.isEmpty() ? View.GONE : View.VISIBLE);

        searches.removeAllViews();
        for (String keyword : recentSearches) {
            searches.addView(chip(activity, "⌕ " + keyword, v -> {
                EditText search = activity.findViewById(R.id.edtExploreSearch);
                if (search != null) { search.setText(keyword); search.setSelection(keyword.length()); }
            }));
        }
        View searchTitle = activity.findViewById(R.id.txtRecentSearchTitle);
        if (searchTitle != null) searchTitle.setVisibility(recentSearches.isEmpty() ? View.GONE : View.VISIBLE);

        viewed.removeAllViews();
        for (LocalHistoryRepository.ViewedPost post : recentViewed) {
            String label = TextUtils.isEmpty(post.priceText) ? post.title : post.title + " · " + post.priceText;
            viewed.addView(chip(activity, label, v -> openById(activity, history, uid, post.postId, openPost)));
        }
        View viewedTitle = activity.findViewById(R.id.txtRecentViewedTitle);
        if (viewedTitle != null) viewedTitle.setVisibility(recentViewed.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static void openById(Activity activity, LocalHistoryRepository history, String uid, String postId, Consumer<DocumentSnapshot> openPost) {
        FirebaseFirestore.getInstance().collection("posts").document(postId).get()
                .addOnSuccessListener(doc -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (doc.exists() && "ACTIVE".equals(doc.getString("status"))) { openPost.accept(doc); return; }
                    history.removeViewedPost(uid, postId);
                    render(activity, history, uid, openPost);
                    Toast.makeText(activity, "Bài đăng này không còn hiển thị.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    if (!activity.isFinishing()) Toast.makeText(activity, "Chưa mở được bài đăng. Kiểm tra kết nối rồi thử lại.", Toast.LENGTH_SHORT).show();
                });
    }

    private static TextView chip(Activity activity, String text, View.OnClickListener onClick) {
        float d = activity.getResources().getDisplayMetrics().density;
        TextView chip = new TextView(activity);
        chip.setText(text);
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(Math.round(200 * d));
        chip.setTextSize(13);
        chip.setTextColor(0xFF14513E);
        chip.setBackgroundResource(R.drawable.bg_white_card_rounded);
        chip.setPadding(Math.round(12 * d), Math.round(8 * d), Math.round(12 * d), Math.round(8 * d));
        chip.setMinHeight(Math.round(40 * d));
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Math.round(8 * d));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(onClick);
        return chip;
    }
}
