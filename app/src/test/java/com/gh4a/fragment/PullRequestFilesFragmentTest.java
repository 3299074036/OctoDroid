package com.gh4a.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** PullRequestFilesFragment：PR 文件列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class PullRequestFilesFragmentTest {
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

    private static Object field(PullRequestFilesFragment f, String name) throws Exception {
        Field field = PullRequestFilesFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void newInstance_storesAllArguments() {
        PullRequestFilesFragment f =
                PullRequestFilesFragment.newInstance("octo", "cat", 42, "abc123");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
        assertEquals(42, f.getArguments().getInt("number"));
        assertEquals("abc123", f.getArguments().getString("head"));
    }

    @Test
    public void onCreate_readsArgumentsIntoFields() throws Exception {
        PullRequestFilesFragment f = attach(
                PullRequestFilesFragment.newInstance("octo", "cat", 42, "abc123"));
        assertEquals("octo", field(f, "mRepoOwner"));
        assertEquals("cat", field(f, "mRepoName"));
        assertEquals(42, field(f, "mPullRequestNumber"));
        assertEquals("abc123", field(f, "mHeadSha"));
    }

    @Test
    public void newInstance_nullHeadShaIsKeptNull() {
        PullRequestFilesFragment f =
                PullRequestFilesFragment.newInstance("o", "r", 1, null);
        assertNotNull(f);
        assertEquals(null, f.getArguments().getString("head"));
    }
}
