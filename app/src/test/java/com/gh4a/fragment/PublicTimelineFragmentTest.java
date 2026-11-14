package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.Page;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * PublicTimelineFragment: the EventListFragment subclass for the public
 * timeline. Shared onItemClick behavior is covered by
 * PublicEventListFragmentTest; this file covers only what is specific to
 * this subclass.
 */
@RunWith(RobolectricTestRunner.class)
public class PublicTimelineFragmentTest {

    public static class NoLoad extends PublicTimelineFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached() {
        NoLoad f = new NoLoad();
        f.setArguments(PublicTimelineFragment.newInstance().getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_hasEmptyArgs() {
        assertNotNull(PublicTimelineFragment.newInstance().getArguments());
        assertTrue(PublicTimelineFragment.newInstance().getArguments().isEmpty());
    }

    @Test
    public void onCreate_setsHasOptionsMenu() {
        assertTrue(attached().hasOptionsMenu());
    }

    @Test
    public void loadRawPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached();
        Single<Response<Page<GitHubEvent>>> single = f.loadRawPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadRawPage(3, true));
    }

    @Test
    public void getEmptyTextResId_isNoEvents() {
        assertEquals(com.gh4a.R.string.no_events_found, attached().getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached().onCreateAdapter());
    }
}
