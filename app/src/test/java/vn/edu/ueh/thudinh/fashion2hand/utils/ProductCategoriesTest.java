package vn.edu.ueh.thudinh.fashion2hand.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProductCategoriesTest {
    @Test public void everyListingCategoryIsReachableFromAFilter() {
        for (String category : ProductCategories.LISTING) {
            boolean found = false;
            for (String filter : ProductCategories.FILTERS)
                if (!ProductCategories.ALL.equals(filter) && ProductCategories.matches(filter, category, "")) found = true;
            assertTrue("Không lọc ra được danh mục " + category, found);
        }
    }

    @Test public void groupsCoverDetailedAndLegacyCategories() {
        assertTrue(ProductCategories.matches("Áo", "Áo thun", ""));
        assertTrue(ProductCategories.matches("Áo", "Áo sơ mi", ""));
        assertTrue(ProductCategories.matches("Áo", "Áo", ""));
        assertTrue(ProductCategories.matches("Váy & Đầm", "Đầm", ""));
        assertTrue(ProductCategories.matches("Váy & Đầm", "Váy", ""));
        assertTrue(ProductCategories.matches("Giày dép", "Giày", ""));
        assertTrue(ProductCategories.matches("Túi xách", "Túi", ""));
        assertTrue(ProductCategories.matches("Phụ kiện", "Phụ kiện", ""));
    }

    @Test public void groupsDoNotLeakIntoEachOther() {
        assertFalse(ProductCategories.matches("Áo", "Quần", "Quần jeans"));
        assertFalse(ProductCategories.matches("Quần", "Áo khoác", "Áo khoác denim"));
        assertFalse(ProductCategories.matches("Giày dép", "Túi xách", ""));
    }

    @Test public void allAndMissingCategory() {
        assertTrue(ProductCategories.matches(ProductCategories.ALL, "Quần", ""));
        assertTrue(ProductCategories.matches("Áo", "", "Áo khoác denim"));
        assertFalse(ProductCategories.matches("Áo", "", "Quần jeans"));
    }
}