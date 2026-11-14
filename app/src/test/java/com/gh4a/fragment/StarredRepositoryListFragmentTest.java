package com.gh4a.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** StarredRepositoryListFragment：星标仓库列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class StarredRepositoryListFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void newInstance_storesLogin() {
        StarredRepositoryListFragment f = StarredRepositoryListFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLoginAndDefaults() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Field login = StarredRepositoryListFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals("alice", login.get(f));
        assertEquals("created", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
        assertNotNull(StarredRepositoryListFragment.class
                .getDeclaredField("mMoveViewModel"));
    }

    @Test
    public void onCreate_initializesSortHelper() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Field helper = StarredRepositoryListFragment.class.getDeclaredField("mSortHelper");
        helper.setAccessible(true);
        assertNotNull(helper.get(f));
    }

    @Test
    public void onSaveInstanceState_persistsSortOrderAndDirection() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("created", out.getString("sort_order"));
        assertEquals("desc", out.getString("sort_direction"));
    }

    @Test
    public void getEmptyTextResId() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertEquals(R.string.no_starred_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onOptionsItemSelected_unknownItemReturnsFalse() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.search);
        assertFalse(f.onOptionsItemSelected(item));
        // 未触发刷新：排序保持默认。
        assertEquals("created", f.getSortOrder());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Repository item = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .build();
        f.onItemClick(item);
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }
}
