package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.RecentHistoryManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** RecentHistoryFragment：最近历史（Robolectric，CREATED 态挂载，不触发加载）。 */
@RunWith(RobolectricTestRunner.class)
public class RecentHistoryFragmentTest {
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

    private static RecentHistoryManager.Entry entry(int type, String owner, String repo,
            int number) throws Exception {
        Constructor<RecentHistoryManager.Entry> ctor =
                RecentHistoryManager.Entry.class.getDeclaredConstructor(
                        int.class, String.class, String.class, int.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(type, owner, repo, number, 1000L);
    }

    @Test
    public void newInstance_hasNoArguments() {
        RecentHistoryFragment f = RecentHistoryFragment.newInstance();
        assertNotNull(f);
        assertTrue(f.getArguments() == null || f.getArguments().isEmpty());
    }

    @Test
    public void onCreate_attachedWithoutCrash() {
        attach(RecentHistoryFragment.newInstance());
    }

    @Test
    public void getEmptyTextResId() {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        assertEquals(R.string.no_recent_history, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_returnsEmptyListWhenLoggedOut() {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        List<RecentHistoryManager.Entry> entries =
                f.onCreateDataSingle(false).blockingGet();
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void onItemClick_issueEntryStartsIssueActivity() throws Exception {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        f.onItemClick(entry(RecentHistoryManager.TYPE_ISSUE, "o", "r", 7));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(IssueActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("o", started.getStringExtra("owner"));
        assertEquals("r", started.getStringExtra("repo"));
        assertEquals(7, started.getIntExtra("number", -1));
    }

    @Test
    public void onItemClick_prEntryStartsPullRequestActivity() throws Exception {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        f.onItemClick(entry(RecentHistoryManager.TYPE_PR, "o", "r", 9));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onItemClick_repoEntryStartsRepositoryActivity() throws Exception {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        f.onItemClick(entry(RecentHistoryManager.TYPE_REPO, "o", "r", 0));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("o", started.getStringExtra("owner"));
        assertEquals("r", started.getStringExtra("repo"));
    }

    @Test
    public void onOptionsItemSelected_clearHistoryReturnsTrue() {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        android.view.MenuItem item = org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(R.id.clear_history);
        assertTrue(f.onOptionsItemSelected(item));
    }

    @Test
    public void onOptionsItemSelected_unknownItemReturnsFalse() {
        RecentHistoryFragment f = attach(RecentHistoryFragment.newInstance());
        android.view.MenuItem item = org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(R.id.search);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void onCreateOptionsMenu_inflatesRecentHistoryMenu() {
        RecentHistoryFragment f = attach(new RecentHistoryFragment());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(mActivity);
        f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
        assertNotNull(menu.findItem(R.id.clear_history));
    }

}
