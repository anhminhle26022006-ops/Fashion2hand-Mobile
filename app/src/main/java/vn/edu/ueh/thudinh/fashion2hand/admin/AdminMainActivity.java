package vn.edu.ueh.thudinh.fashion2hand.admin;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.navigation.NavigationBarView;

import vn.edu.ueh.thudinh.fashion2hand.R;

/**
 * Màn chính Admin chứa 5 tab. Dọc: BottomNavigationView, ngang: NavigationRailView
 * (cả hai đều là NavigationBarView nên dùng chung một đoạn code).
 */
public class AdminMainActivity extends BaseAdminActivity {

    private NavigationBarView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdminContent(R.layout.activity_admin_main);

        nav = findViewById(R.id.adminNav);
        nav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        nav.setOnItemReselectedListener(item -> { });

        if (savedInstanceState == null) showTab(R.id.nav_admin_overview);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (nav.getSelectedItemId() != R.id.nav_admin_overview) {
                    nav.setSelectedItemId(R.id.nav_admin_overview);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        int todo = viewModel.countTodo();
        BadgeDrawable badge = nav.getOrCreateBadge(R.id.nav_admin_more);
        badge.setVisible(todo > 0);
        badge.setNumber(todo);
    }

    @Override
    protected View bottomInsetTarget() {
        return findViewById(R.id.adminNav);
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_admin_users) {
            fragment = AdminListFragment.newInstance(AdminListFragment.TYPE_USERS);
        } else if (itemId == R.id.nav_admin_posts) {
            fragment = AdminListFragment.newInstance(AdminListFragment.TYPE_POSTS);
        } else if (itemId == R.id.nav_admin_orders) {
            fragment = AdminListFragment.newInstance(AdminListFragment.TYPE_ORDERS);
        } else if (itemId == R.id.nav_admin_more) {
            fragment = new AdminMoreFragment();
        } else {
            fragment = new AdminOverviewFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .commit();
    }
}
