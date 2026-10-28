package vn.edu.ueh.thudinh.fashion2hand.repository.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Chạy trên máy ảo với SQLite thật, dùng cơ sở dữ liệu trong bộ nhớ nên không đụng dữ liệu của app. */
@RunWith(AndroidJUnit4.class)
public class LocalHistoryRepositoryTest {

    private LocalHistoryDbHelper helper;
    private LocalHistoryRepository repo;

    @Before public void setUp() {
        helper = new LocalHistoryDbHelper(ApplicationProvider.getApplicationContext(), null);
        repo = new LocalHistoryRepository(helper);
    }

    @After public void tearDown() { helper.close(); }

    @Test public void searchesAreNormalizedDeduplicatedAndNewestFirst() {
        repo.addSearch("u1", "Áo khoác");
        repo.addSearch("u1", "giày");
        repo.addSearch("u1", "  áo   KHOÁC ");
        repo.addSearch("u1", "a");           // quá ngắn, bỏ qua
        List<String> result = repo.getRecentSearches("u1");
        assertEquals(2, result.size());
        assertEquals("áo khoác", result.get(0));
        assertEquals("giày", result.get(1));
    }

    @Test public void historyKeepsOnlyTheNewestEntries() {
        for (int i = 0; i < LocalHistoryRepository.MAX_SEARCHES + 4; i++) repo.addSearch("u1", "từ khóa " + i);
        List<String> result = repo.getRecentSearches("u1");
        assertEquals(LocalHistoryRepository.MAX_SEARCHES, result.size());
        assertEquals("từ khóa " + (LocalHistoryRepository.MAX_SEARCHES + 3), result.get(0));

        for (int i = 0; i < LocalHistoryRepository.MAX_VIEWED + 3; i++) repo.addViewedPost("u1", "post" + i, "Bài " + i, "100.000 đ");
        assertEquals(LocalHistoryRepository.MAX_VIEWED, repo.getRecentViewed("u1").size());
    }

    @Test public void reopeningAPostMovesItToTheFront() {
        repo.addViewedPost("u1", "p1", "Áo sơ mi", "80.000 đ");
        repo.addViewedPost("u1", "p2", "Váy hoa", "0 đ");
        repo.addViewedPost("u1", "p1", "Áo sơ mi", "80.000 đ");
        List<LocalHistoryRepository.ViewedPost> viewed = repo.getRecentViewed("u1");
        assertEquals(2, viewed.size());
        assertEquals("p1", viewed.get(0).postId);
        assertEquals("80.000 đ", viewed.get(0).priceText);
    }

    @Test public void accountsAreSeparatedAndClearOnlyAffectsOneAccount() {
        repo.addSearch("u1", "túi xách");
        repo.addSearch("u2", "quần jean");
        repo.addViewedPost(null, "p9", "Khăn len", "0 đ");    // khách chưa đăng nhập
        assertEquals(1, repo.getRecentSearches("u2").size());
        assertEquals("p9", repo.getRecentViewed("").get(0).postId);

        repo.clear("u1");
        assertTrue(repo.getRecentSearches("u1").isEmpty());
        assertEquals("quần jean", repo.getRecentSearches("u2").get(0));

        repo.addViewedPost("u2", "p3", "Giày", "250.000 đ");
        repo.removeViewedPost("u2", "p3");
        assertTrue(repo.getRecentViewed("u2").isEmpty());
    }
}
