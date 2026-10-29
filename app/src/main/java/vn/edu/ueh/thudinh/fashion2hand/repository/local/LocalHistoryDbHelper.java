package vn.edu.ueh.thudinh.fashion2hand.repository.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Cơ sở dữ liệu SQLite trên máy: lịch sử tìm kiếm và bài đăng đã xem gần đây.
 * Dữ liệu chỉ phục vụ tiện ích cá nhân nên lưu cục bộ, không đồng bộ lên Firebase.
 */
public class LocalHistoryDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "fashion2hand_local.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_SEARCH = "search_history";
    public static final String TABLE_VIEWED = "recent_views";

    public static final String COL_UID = "uid";
    public static final String COL_QUERY = "keyword";
    public static final String COL_POST_ID = "post_id";
    public static final String COL_TITLE = "title";
    public static final String COL_PRICE = "price_text";
    public static final String COL_TIME = "updated_at";

    private static LocalHistoryDbHelper instance;

    public static synchronized LocalHistoryDbHelper getInstance(Context context) {
        if (instance == null) instance = new LocalHistoryDbHelper(context.getApplicationContext(), DB_NAME);
        return instance;
    }

    /** Dùng trực tiếp trong test với tên file riêng (null = cơ sở dữ liệu trong bộ nhớ). */
    LocalHistoryDbHelper(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SEARCH + " ("
                + COL_UID + " TEXT NOT NULL, "
                + COL_QUERY + " TEXT NOT NULL, "
                + COL_TIME + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_UID + ", " + COL_QUERY + "))");
        db.execSQL("CREATE TABLE " + TABLE_VIEWED + " ("
                + COL_UID + " TEXT NOT NULL, "
                + COL_POST_ID + " TEXT NOT NULL, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_PRICE + " TEXT NOT NULL, "
                + COL_TIME + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_UID + ", " + COL_POST_ID + "))");
        db.execSQL("CREATE INDEX idx_search_time ON " + TABLE_SEARCH + " (" + COL_UID + ", " + COL_TIME + " DESC)");
        db.execSQL("CREATE INDEX idx_viewed_time ON " + TABLE_VIEWED + " (" + COL_UID + ", " + COL_TIME + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Chỉ là dữ liệu tiện ích, nâng cấp an toàn nhất là tạo lại bảng.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCH);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VIEWED);
        onCreate(db);
    }
}
