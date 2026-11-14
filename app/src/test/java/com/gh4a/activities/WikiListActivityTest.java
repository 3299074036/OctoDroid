package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import org.mockito.Mockito;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.GitHubWikiPage;

@RunWith(RobolectricTestRunner.class)
public class WikiListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void makeIntent_withoutInitialPage_extraIsNull() {
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", null);
        assertEquals(WikiListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertNull(intent.getStringExtra("initial_page"));
    }

    @Test
    public void makeIntent_withInitialPage_carriesSha() {
        GitHubWikiPage page = Mockito.mock(GitHubWikiPage.class);
        Mockito.when(page.sha()).thenReturn("abc123");
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", page);
        assertEquals("abc123", intent.getStringExtra("initial_page"));
    }

    @Test
    public void makeIntent_withInitialPageWithoutSha_extraIsNull() {
        GitHubWikiPage page = Mockito.mock(GitHubWikiPage.class);
        Mockito.when(page.sha()).thenReturn(null);
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", page);
        assertNull(intent.getStringExtra("initial_page"));
    }

    @Test
    public void launches_titleAndSubtitle() {
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiListActivity> c =
                Robolectric.buildActivity(WikiListActivity.class, intent)) {
            WikiListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.recent_wiki),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isWikiListFragmentWithArgs() {
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiListActivity> c =
                Robolectric.buildActivity(WikiListActivity.class, intent)) {
            WikiListActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.WikiListFragment);
            android.os.Bundle args = activity.getFragment().getArguments();
            assertNotNull(args);
            assertEquals("octocat", args.getString("owner"));
            assertEquals("hello", args.getString("repo"));
        }
    }

    @Test
    public void navigateUp_returnsRepositoryActivityIntent() {
        Intent intent = WikiListActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiListActivity> c =
                Robolectric.buildActivity(WikiListActivity.class, intent)) {
            WikiListActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(RepositoryActivity.class.getName(),
                    up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("owner"));
            assertEquals("hello", up.getStringExtra("repo"));
        }
    }
}
