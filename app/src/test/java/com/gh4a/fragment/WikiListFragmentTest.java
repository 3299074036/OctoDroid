package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.WikiActivity;
import com.gh4a.model.Feed;

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
import static org.mockito.Mockito.mock;
import com.gh4a.adapter.RootAdapter;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

/** WikiListFragment：Wiki 更新列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class WikiListFragmentTest {
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
    public void newInstance_storesOwnerRepoAndInitialPage() {
        WikiListFragment f = WikiListFragment.newInstance("octo", "cat", "Home");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
        assertEquals("Home", f.getArguments().getString("initial_page"));
    }

    @Test
    public void onCreate_readsArgsAndRemovesInitialPage() throws Exception {
        WikiListFragment f = attach(WikiListFragment.newInstance("octo", "cat", "Home"));
        Field owner = WikiListFragment.class.getDeclaredField("mUserLogin");
        Field repo = WikiListFragment.class.getDeclaredField("mRepoName");
        Field page = WikiListFragment.class.getDeclaredField("mInitialPage");
        owner.setAccessible(true);
        repo.setAccessible(true);
        page.setAccessible(true);
        assertEquals("octo", owner.get(f));
        assertEquals("cat", repo.get(f));
        assertEquals("Home", page.get(f));
        // onCreate 把 initial_page 从参数里移除，避免旋转后重复打开。
        assertFalse(f.getArguments().containsKey("initial_page"));
    }

    @Test
    public void onCreate_nullInitialPageIsKeptNull() throws Exception {
        WikiListFragment f = attach(WikiListFragment.newInstance("o", "r", null));
        Field page = WikiListFragment.class.getDeclaredField("mInitialPage");
        page.setAccessible(true);
        assertEquals(null, page.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        WikiListFragment f = attach(WikiListFragment.newInstance("o", "r", null));
        assertEquals(R.string.no_wiki_updates_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        WikiListFragment f = attach(WikiListFragment.newInstance("o", "r", null));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_returnsLazySingleWithoutSubscribing() {
        WikiListFragment f = attach(WikiListFragment.newInstance("o", "r", null));
        // 只建 Single 不订阅：feed 拉取在订阅前不发真实请求。
        assertNotNull(f.onCreateDataSingle(false));
        assertNotNull(f.onCreateDataSingle(true));
    }

    @Test
    public void onItemClick_startsWikiActivity() {
        WikiListFragment f = attach(WikiListFragment.newInstance("octo", "cat", null));
        f.onItemClick(mock(Feed.class));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(WikiActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }

    @Test
    public void onAddData_initialPageMatchOpensViewerOnce() {
        WikiListFragment f = attach(WikiListFragment.newInstance("octo", "cat", "Home"));
        RootAdapter<Feed, ?> adapter = f.onCreateAdapter();
        Feed other = mock(Feed.class);
        when(other.getId()).thenReturn("Other");
        Feed home = mock(Feed.class);
        when(home.getId()).thenReturn("Home");
        f.onAddData(adapter, Arrays.asList(other, home));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(WikiActivity.class.getName(), started.getComponent().getClassName());
        // mInitialPage 已清掉：再次 onAddData 不再开页面
        f.onAddData(adapter, Arrays.asList(home));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onAddData_initialPageWithoutMatchClearsInitialPage() {
        WikiListFragment f = attach(WikiListFragment.newInstance("octo", "cat", "Nope"));
        RootAdapter<Feed, ?> adapter = f.onCreateAdapter();
        Feed feed = mock(Feed.class);
        when(feed.getId()).thenReturn("Home");
        f.onAddData(adapter, Collections.singletonList(feed));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void onAddData_nullInitialPageJustAddsData() {
        WikiListFragment f = attach(WikiListFragment.newInstance("octo", "cat", null));
        RootAdapter<Feed, ?> adapter = f.onCreateAdapter();
        Feed feed = mock(Feed.class);
        when(feed.getId()).thenReturn("Home");
        f.onAddData(adapter, Collections.singletonList(feed));
        assertEquals(1, adapter.getItemCount());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

}
