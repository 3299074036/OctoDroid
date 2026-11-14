package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.utils.IntentUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import android.os.Looper;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
public class BrowseFilterTest {
    private BrowseFilter launchWithData(String uriString) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (uriString != null) {
            intent.setData(Uri.parse(uriString));
        }
        return Robolectric.buildActivity(BrowseFilter.class, intent).setup().get();
    }

    @Test
    public void makeRedirectionIntent_setsDataAndMarkerExtra() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(42L);
        Intent intent = BrowseFilter.makeRedirectionIntent(activity,
                Uri.parse("https://github.com/octo/cat"), marker);
        assertEquals(BrowseFilter.class.getName(), intent.getComponent().getClassName());
        assertEquals(Uri.parse("https://github.com/octo/cat"), intent.getData());
        assertTrue(intent.hasExtra("initial_comment"));
    }

    @Test
    public void onCreate_nullData_finishesWithoutStartingActivity() {
        BrowseFilter activity = launchWithData(null);
        assertTrue(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onCreate_httpScheme_finishes() {
        BrowseFilter activity = launchWithData("http://github.com/octo/cat");
        assertTrue(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onCreate_nonGitHubHost_finishes() {
        BrowseFilter activity = launchWithData("https://example.com/octo/cat");
        assertTrue(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onCreate_gistHost_finishesWithoutAppActivity() {
        // gist.github.com is accepted by the host check, but the gist root
        // link parses to null -> browser fallback, no in-app activity started.
        BrowseFilter activity = launchWithData("https://gist.github.com");
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onCreate_githubRoot_finishesViaBrowserFallback() {
        BrowseFilter activity = launchWithData("https://github.com");
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onCreate_repoLink_startsRepositoryActivityAndFinishes() {
        BrowseFilter activity = launchWithData("https://github.com/octo/cat");
        assertTrue(activity.isFinishing());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onCreate_userLink_startsUserActivityAndFinishes() {
        BrowseFilter activity = launchWithData("https://github.com/octocat");
        assertTrue(activity.isFinishing());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onCreate_blogHost_finishesViaBrowserFallback() {
        BrowseFilter activity = launchWithData("https://blog.github.com/some-post");
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onCreate_newTaskFlag_addsMultipleTaskFlag() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/octo/cat"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BrowseFilter activity = Robolectric.buildActivity(BrowseFilter.class, intent).setup().get();
        assertTrue(activity.isFinishing());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
    }

    @Test
    @Config(sdk = 32)
    public void onCreate_preTiramisu_usesLegacyParcelableApi() {
        BrowseFilter activity = launchWithData("https://github.com/octo/cat");
        assertTrue(activity.isFinishing());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test(timeout = 120000)
    @LooperMode(LooperMode.Mode.LEGACY)
    public void onCreate_userReposTab_executesLoadTaskAndFinishes() {
        // LEGACY looper mode: AsyncTask.execute() runs doInBackground
        // synchronously, so the load task completes during setup().
        BrowseFilter activity = launchWithData("https://github.com/octo?tab=repositories");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.isFinishing());
    }

}
