package com.gh4a.activities;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloadListActivityTest {
    @Test
    public void launch_titleAndFragment() {
        DownloadListActivity activity = launch(DownloadListActivity.class);
        assertEquals(appContext().getString(R.string.download_manager),
                activity.getSupportActionBar().getTitle().toString());
        assertTrue(activity.getFragment() instanceof DownloadListFragment);
    }

    @Test
    public void navigateUp_returnsToplevelIntent() {
        DownloadListActivity activity = launch(DownloadListActivity.class);
        assertNotNull(activity.navigateUp());
    }
}
