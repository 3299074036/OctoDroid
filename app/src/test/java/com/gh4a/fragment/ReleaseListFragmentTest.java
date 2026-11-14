package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.ReleaseInfoActivity;
import com.meisolsson.githubsdk.model.Release;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ReleaseListFragment：Release 列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class ReleaseListFragmentTest {
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

    private static String field(ReleaseListFragment f, String name) throws Exception {
        Field field = ReleaseListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(f);
    }

    @Test
    public void newInstance_storesOwnerAndRepo() {
        ReleaseListFragment f = ReleaseListFragment.newInstance("octo", "cat");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
    }

    @Test
    public void onCreate_readsOwnerAndRepoFromArguments() throws Exception {
        ReleaseListFragment f = attach(ReleaseListFragment.newInstance("octo", "cat"));
        assertEquals("octo", field(f, "mUserLogin"));
        assertEquals("cat", field(f, "mRepoName"));
    }

    @Test
    public void getEmptyTextResId() {
        ReleaseListFragment f = attach(ReleaseListFragment.newInstance("o", "r"));
        assertEquals(R.string.no_releases_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        ReleaseListFragment f = attach(ReleaseListFragment.newInstance("o", "r"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        ReleaseListFragment f = attach(ReleaseListFragment.newInstance("o", "r"));
        // 只建 Single 不订阅：Retrofit 调用在订阅前不发真实请求。
        assertNotNull(f.loadPage(1, false));
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onItemClick_startsReleaseInfoActivityWithId() {
        ReleaseListFragment f = attach(ReleaseListFragment.newInstance("octo", "cat"));
        Release release = mock(Release.class);
        when(release.id()).thenReturn(123L);
        f.onItemClick(release);
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(ReleaseInfoActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
        assertEquals(123L, started.getLongExtra("id", -1));
    }
}
