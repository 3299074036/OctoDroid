package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

/** ForkListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class ForkListFragmentTest {

    public static class NoLoad extends ForkListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String owner, String repo) {
        NoLoad f = new NoLoad();
        f.setArguments(ForkListFragment.newInstance(owner, repo).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesOwnerAndRepo() {
        ForkListFragment f = ForkListFragment.newInstance("o", "r");
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
    }

    @Test
    public void getEmptyTextResId_isNoForks() {
        assertEquals(R.string.no_forks_found, new ForkListFragment().getEmptyTextResId());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r");
        Single<Response<Page<Repository>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("o", "r").onCreateAdapter());
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        NoLoad f = attached("o", "r");
        FragmentActivity activity = f.getActivity();
        Repository repo = Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").build();
        f.onItemClick(repo);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("owner"));
        assertEquals("hello", started.getExtras().getString("repo"));
    }
}
