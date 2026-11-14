package com.gh4a.fragment;

import android.content.Intent;

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
import static org.junit.Assert.assertNotNull;

/** WatchedRepositoryListFragment：关注仓库列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class WatchedRepositoryListFragmentTest {
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
        WatchedRepositoryListFragment f = WatchedRepositoryListFragment.newInstance("octo");
        assertEquals("octo", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLogin() throws Exception {
        WatchedRepositoryListFragment f =
                attach(WatchedRepositoryListFragment.newInstance("octo"));
        Field login = WatchedRepositoryListFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals("octo", login.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        WatchedRepositoryListFragment f =
                attach(WatchedRepositoryListFragment.newInstance("octo"));
        assertEquals(R.string.no_watched_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        WatchedRepositoryListFragment f =
                attach(WatchedRepositoryListFragment.newInstance("octo"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        WatchedRepositoryListFragment f =
                attach(WatchedRepositoryListFragment.newInstance("octo"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        WatchedRepositoryListFragment f =
                attach(WatchedRepositoryListFragment.newInstance("octo"));
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
