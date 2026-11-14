package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.CommitListFragment;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.ContentType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CommitHistoryActivityTest {
    private Intent intent(boolean baseSelectable) {
        return CommitHistoryActivity.makeIntent(appContext(), "octo", "repo",
                "main", "src/Foo.java", ContentType.File, baseSelectable);
    }

    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = intent(true);
        assertEquals(CommitHistoryActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("owner"));
        assertEquals("repo", intent.getStringExtra("repo"));
        assertEquals("main", intent.getStringExtra("ref"));
        assertEquals("src/Foo.java", intent.getStringExtra("path"));
        assertEquals(ContentType.File, intent.getSerializableExtra("type"));
        assertTrue(intent.getBooleanExtra("base_selectable", false));
    }

    @Test
    public void makeIntent_baseSelectableFalse() {
        assertFalse(intent(false).getBooleanExtra("base_selectable", true));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        CommitHistoryActivity activity = launch(CommitHistoryActivity.class, intent(false));
        assertEquals(appContext().getString(R.string.history),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("src/Foo.java", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof CommitListFragment);
    }

    @Test
    public void navigateUp_goesToRepositoryWithRef() {
        CommitHistoryActivity activity = launch(CommitHistoryActivity.class, intent(false));
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("octo", up.getStringExtra("owner"));
        assertEquals("repo", up.getStringExtra("repo"));
        assertEquals("main", up.getStringExtra("ref"));
    }

    @Test
    public void baseSelectionAllowed_reflectsExtra() {
        assertTrue(launch(CommitHistoryActivity.class, intent(true)).baseSelectionAllowed());
        assertFalse(launch(CommitHistoryActivity.class, intent(false)).baseSelectionAllowed());
    }

    @Test
    public void onCommitSelectedAsBase_returnsCommitAndFinishes() {
        CommitHistoryActivity activity = launch(CommitHistoryActivity.class, intent(true));
        Commit commit = org.mockito.Mockito.mock(Commit.class);
        activity.onCommitSelectedAsBase(commit);
        assertTrue(activity.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK, org.robolectric.Shadows.shadowOf(activity).getResultCode());
        assertEquals(commit, org.robolectric.Shadows.shadowOf(activity).getResultIntent().getParcelableExtra("commit"));
    }
}
