package vn.edu.ueh.thudinh.fashion2hand.repository.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static vn.edu.ueh.thudinh.fashion2hand.repository.local.LocalHistoryDbHelper.*;

/** Đọc/ghi lịch sử cục bộ theo từng tài khoản; mỗi danh sách chỉ giữ số mục mới nhất. */
public class LocalHistoryRepository {

    public static final int MAX_SEARCHES = 8;
    public static final int MAX_VIEWED = 10;
    private static final String GUEST = "guest";

    /** Một bài đăng đã xem, đủ để hiện thẻ và mở lại chi tiết. */
    public static class ViewedPost {
        public final String postId, title, priceText;
        public final long viewedAt;
        public ViewedPost(String postId, String title, String priceText, long viewedAt) {
            this.postId = postId; this.title = title; this.priceText = priceText; this.viewedAt = viewedAt;
        }
    }

    private final LocalHistoryDbHelper helper;

    public LocalHistoryRepository(Context context) { this(LocalHistoryDbHelper.getInstance(context)); }

    LocalHistoryRepository(LocalHistoryDbHelper helper) { this.helper = helper; }

    private static String owner(String uid) { return uid == null || uid.trim().isEmpty() ? GUEST : uid; }

    /** Chuẩn hóa để "Áo Khoác " và "áo khoác" là một lượt tìm. */
    static String normalize(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public void addSearch(String uid, String query) {
        String value = normalize(query);
        if (value.length() < 2 || value.length() > 100) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues row = new ContentValues();
        row.put(COL_UID, owner(uid)); row.put(COL_QUERY, value); row.put(COL_TIME, System.currentTimeMillis());
        db.beginTransaction();
        try {
            db.insertWithOnConflict(TABLE_SEARCH, null, row, SQLiteDatabase.CONFLICT_REPLACE);
            trim(db, TABLE_SEARCH, uid, MAX_SEARCHES);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<String> getRecentSearches(String uid) {
        List<String> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(TABLE_SEARCH, new String[]{COL_QUERY},
                COL_UID + "=?", new String[]{owner(uid)}, null, null, COL_TIME + " DESC, rowid DESC", String.valueOf(MAX_SEARCHES))) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    public void addViewedPost(String uid, String postId, String title, String priceText) {
        if (postId == null || postId.trim().isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues row = new ContentValues();
        row.put(COL_UID, owner(uid)); row.put(COL_POST_ID, postId);
        row.put(COL_TITLE, title == null ? "" : title); row.put(COL_PRICE, priceText == null ? "" : priceText);
        row.put(COL_TIME, System.currentTimeMillis());
        db.beginTransaction();
        try {
            db.insertWithOnConflict(TABLE_VIEWED, null, row, SQLiteDatabase.CONFLICT_REPLACE);
            trim(db, TABLE_VIEWED, uid, MAX_VIEWED);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<ViewedPost> getRecentViewed(String uid) {
        List<ViewedPost> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(TABLE_VIEWED, new String[]{COL_POST_ID, COL_TITLE, COL_PRICE, COL_TIME},
                COL_UID + "=?", new String[]{owner(uid)}, null, null, COL_TIME + " DESC, rowid DESC", String.valueOf(MAX_VIEWED))) {
            while (c.moveToNext()) result.add(new ViewedPost(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        return result;
    }

    /** Bài đã bị ẩn/xóa trên Firebase thì bỏ khỏi danh sách đã xem. */
    public void removeViewedPost(String uid, String postId) {
        helper.getWritableDatabase().delete(TABLE_VIEWED, COL_UID + "=? AND " + COL_POST_ID + "=?", new String[]{owner(uid), postId});
    }

    public void clear(String uid) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(TABLE_SEARCH, COL_UID + "=?", new String[]{owner(uid)});
        db.delete(TABLE_VIEWED, COL_UID + "=?", new String[]{owner(uid)});
    }

    /** Xóa các dòng cũ, chỉ giữ {@code keep} dòng mới nhất của tài khoản. */
    private static void trim(SQLiteDatabase db, String table, String uid, int keep) {
        db.execSQL("DELETE FROM " + table + " WHERE " + COL_UID + "=? AND rowid NOT IN (SELECT rowid FROM " + table
                        + " WHERE " + COL_UID + "=? ORDER BY " + COL_TIME + " DESC, rowid DESC LIMIT " + keep + ")",
                new Object[]{owner(uid), owner(uid)});
    }
}
