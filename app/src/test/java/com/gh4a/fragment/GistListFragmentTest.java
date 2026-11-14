package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.GistActivity;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.Page;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

/** GistListFragment (F1): starred and unstarred branches. */
@RunWith(RobolectricTestRunner.class)
public class GistListFragmentTest {

    public static class NoLoad extends GistListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String user, boolean starred) {
        NoLoad f = new NoLoad();
        f.setArguments(GistListFragment.newInstance(user, starred).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesUserAndStarredFlag() {
        GistListFragment f = GistListFragment.newInstance("octocat", true);
        assertEquals("octocat", f.getArguments().getString("user"));
        assertEquals(true, f.getArguments().getBoolean("starred"));
        GistListFragment g = GistListFragment.newInstance("octocat", false);
        assertEquals(false, g.getArguments().getBoolean("starred"));
    }

    @Test
    public void getEmptyTextResId_starredVsPlain() {
        assertEquals(R.string.no_starred_gists_found,
                attached("octocat", true).getEmptyTextResId());
        assertEquals(R.string.no_gists_found,
                attached("octocat", false).getEmptyTextResId());
    }

    @Test
    public void loadPage_starred_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("octocat", true);
        Single<Response<Page<Gist>>> single = f.loadPage(1, false);
        assertNotNull(single);
    }

    @Test
    public void loadPage_unstarred_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("octocat", false);
        Single<Response<Page<Gist>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("octocat", false).onCreateAdapter());
        assertNotNull(attached("octocat", true).onCreateAdapter());
    }

    @Test
    public void onItemClick_startsGistActivity() {
        NoLoad f = attached("octocat", false);
        FragmentActivity activity = f.getActivity();
        Gist gist = Gist.builder().id("abc123").build();
        f.onItemClick(gist);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(GistActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("abc123", started.getExtras().getString("id"));
    }
}
