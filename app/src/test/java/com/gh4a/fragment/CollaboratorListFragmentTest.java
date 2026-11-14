package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;
import com.gh4a.activities.UserActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * CollaboratorListFragment (F1 smoke test for the paged-fragment harness):
 * newInstance args, empty text, adapter creation and loadPage returning a
 * Single without subscribing (no network).
 */
@RunWith(RobolectricTestRunner.class)
public class CollaboratorListFragmentTest {

    /** No-load subclass: initial load suppressed so attach stays offline. */
    public static class NoLoad extends CollaboratorListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String owner, String repo) {
        NoLoad f = new NoLoad();
        f.setArguments(CollaboratorListFragment.newInstance(owner, repo).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesOwnerAndRepo() {
        CollaboratorListFragment f = CollaboratorListFragment.newInstance("octocat", "hello");
        assertEquals("octocat", f.getArguments().getString("owner"));
        assertEquals("hello", f.getArguments().getString("repo"));
    }

    @Test
    public void getEmptyTextResId_isNoCollaborators() {
        assertEquals(R.string.no_collaborators_found,
                new CollaboratorListFragment().getEmptyTextResId());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r");
        Single<Response<com.meisolsson.githubsdk.model.Page<User>>> single =
                f.loadPage(1, false);
        assertNotNull(single);
        Single<Response<com.meisolsson.githubsdk.model.Page<User>>> singleCached =
                f.loadPage(2, true);
        assertNotNull(singleCached);
    }

    @Test
    public void onCreateAdapter_returnsAdapterWithClickListener() {
        NoLoad f = attached("o", "r");
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onItemClick_startsUserActivity() {
        NoLoad f = attached("o", "r");
        FragmentActivity activity = f.getActivity();
        User user = User.builder().login("octocat").build();
        f.onItemClick(user);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(started.getExtras().containsKey("login"));
        assertEquals("octocat", started.getExtras().getString("login"));
    }
}
