package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Bundle;

import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.Page;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * PrivateEventListFragment: the EventListFragment subclass for a user's
 * received events, with an optional organization scope. Shared onItemClick
 * behavior is covered by PublicEventListFragmentTest; this file covers
 * only what is specific to this subclass.
 */
@RunWith(RobolectricTestRunner.class)
public class PrivateEventListFragmentTest {

    public static class NoLoad extends PrivateEventListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String login, String org) {
        NoLoad f = new NoLoad();
        f.setArguments(PrivateEventListFragment.newInstance(login, org).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    private static Object field(Object target, Class<?> owner, String name) {
        try {
            java.lang.reflect.Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void newInstance_storesLoginAndOrg() {
        PrivateEventListFragment f =
                PrivateEventListFragment.newInstance("octocat", "github");
        assertEquals("octocat", f.getArguments().getString("login"));
        assertEquals("github", f.getArguments().getString("org"));
    }

    @Test
    public void newInstance_nullOrg_storesNullOrg() {
        PrivateEventListFragment f =
                PrivateEventListFragment.newInstance("octocat", null);
        assertEquals("octocat", f.getArguments().getString("login"));
        assertNull(f.getArguments().getString("org"));
    }

    @Test
    public void onCreate_readsLoginAndOrgFromArguments() {
        NoLoad f = attached("octocat", "github");
        assertEquals("octocat", field(f, PrivateEventListFragment.class, "mLogin"));
        assertEquals("github", field(f, PrivateEventListFragment.class, "mOrganization"));
    }

    @Test
    public void onCreate_nullOrg_readsNullOrganization() {
        NoLoad f = attached("octocat", null);
        assertEquals("octocat", field(f, PrivateEventListFragment.class, "mLogin"));
        assertNull(field(f, PrivateEventListFragment.class, "mOrganization"));
    }

    @Test
    public void loadRawPage_withOrg_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("octocat", "github");
        Single<Response<Page<GitHubEvent>>> single = f.loadRawPage(1, false);
        assertNotNull(single);
    }

    @Test
    public void loadRawPage_withoutOrg_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("octocat", null);
        Single<Response<Page<GitHubEvent>>> single = f.loadRawPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadRawPage(2, true));
    }

    @Test
    public void getEmptyTextResId_isNoEvents() {
        assertEquals(com.gh4a.R.string.no_events_found,
                attached("octocat", null).getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("octocat", "github").onCreateAdapter());
    }
}
