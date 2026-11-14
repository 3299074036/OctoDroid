package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

/** ContributorListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class ContributorListFragmentTest {

    public static class NoLoad extends ContributorListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String owner, String repo) {
        NoLoad f = new NoLoad();
        f.setArguments(ContributorListFragment.newInstance(owner, repo).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesOwnerAndRepo() {
        ContributorListFragment f = ContributorListFragment.newInstance("o", "r");
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
    }

    @Test
    public void getEmptyTextResId_isNoContributors() {
        assertEquals(R.string.no_contributors_found,
                new ContributorListFragment().getEmptyTextResId());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r");
        Single<Response<Page<User>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(3, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("o", "r").onCreateAdapter());
    }

    @Test
    public void onItemClick_startsUserActivity() {
        NoLoad f = attached("o", "r");
        FragmentActivity activity = f.getActivity();
        f.onItemClick(User.builder().login("octocat").build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("login"));
    }

    @Test
    public void onItemClick_nullIntentStartsNothing() {
        // Mannequin users have no profile page: makeIntent returns null.
        NoLoad f = attached("o", "r");
        FragmentActivity activity = f.getActivity();
        User mannequin = User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build();
        f.onItemClick(mannequin);
        assertEquals(null, Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
