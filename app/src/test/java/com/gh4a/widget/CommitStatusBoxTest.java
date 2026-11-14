package com.gh4a.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.DiagnosticLogActivity;
import com.gh4a.model.StatusWrapper;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.Status;
import com.meisolsson.githubsdk.model.StatusState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CommitStatusBoxTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private StatusWrapper status(String context, String description, String targetUrl,
            StatusState state) {
        Status s = mock(Status.class);
        when(s.context()).thenReturn(context);
        when(s.description()).thenReturn(description);
        when(s.targetUrl()).thenReturn(targetUrl);
        when(s.state()).thenReturn(state);
        return new StatusWrapper(s);
    }

    private TextView statusLabel(CommitStatusBox box) {
        return box.findViewById(R.id.merge_status_label);
    }

    private TextView summary(CommitStatusBox box) {
        return box.findViewById(R.id.merge_commit_summary);
    }

    private ViewGroup container(CommitStatusBox box) {
        return box.findViewById(R.id.merge_commit_status_container);
    }

    private View header(CommitStatusBox box) {
        return box.findViewById(R.id.commit_status_header);
    }

    @Test
    public void fillStatus_clean_noStatuses_showsMergable() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Clean);
        assertEquals(activity.getString(R.string.pull_merge_status_mergable),
                statusLabel(box).getText().toString());
        assertEquals(activity.getString(R.string.pull_no_commit_status),
                summary(box).getText().toString());
        assertEquals(View.GONE, container(box).getVisibility());
    }

    @Test
    public void fillStatus_clean_withStatuses_showsClean() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "ok", null, StatusState.Success)),
                PullRequest.MergeableState.Clean);
        assertEquals(activity.getString(R.string.pull_merge_status_clean),
                statusLabel(box).getText().toString());
        // fillStatus ends with setStatusesExpanded(false): the container is
        // collapsed by default even when statuses exist; the rows are there.
        assertEquals(View.GONE, container(box).getVisibility());
        assertEquals(1, container(box).getChildCount());
    }

    @Test
    public void fillStatus_behind_showsBehind() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Behind);
        assertEquals(activity.getString(R.string.pull_merge_status_behind),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_blocked_showsBlocked() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Blocked);
        assertEquals(activity.getString(R.string.pull_merge_status_blocked),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_dirty_showsDirty() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Dirty);
        assertEquals(activity.getString(R.string.pull_merge_status_dirty),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_draft_showsDraft() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Draft);
        assertEquals(activity.getString(R.string.pull_merge_status_draft),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_unstable_showsUnstable() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "ok", null, StatusState.Success)),
                PullRequest.MergeableState.Unstable);
        assertEquals(activity.getString(R.string.pull_merge_status_unstable),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_unstable_withPending_showsPending() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "waiting", null, StatusState.Pending)),
                PullRequest.MergeableState.Unstable);
        assertEquals(activity.getString(R.string.pull_merge_status_pending),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_unknown_noStatuses_hidesBox() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), PullRequest.MergeableState.Unknown);
        assertEquals(View.GONE, box.getVisibility());
    }

    @Test
    public void fillStatus_nullState_treatedAsUnknown_noStatuses_hidesBox() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.emptyList(), null);
        assertEquals(View.GONE, box.getVisibility());
    }

    @Test
    public void fillStatus_unknown_withStatuses_showsUnknown() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "?", null, StatusState.Pending)),
                PullRequest.MergeableState.Unknown);
        assertEquals(activity.getString(R.string.pull_merge_status_unknown),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_nullState_withStatuses_showsUnknown() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "?", null, StatusState.Pending)), null);
        assertEquals(activity.getString(R.string.pull_merge_status_unknown),
                statusLabel(box).getText().toString());
    }

    @Test
    public void fillStatus_mixedStates_summaryCountsAll() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        List<StatusWrapper> statuses = Arrays.asList(
                status("a", "fail", null, StatusState.Failure),
                status("b", "err", null, StatusState.Error),
                status("c", "ok", null, StatusState.Success),
                status("d", "wait", null, StatusState.Pending),
                status("e", "?", null, null));
        box.fillStatus(statuses, PullRequest.MergeableState.Clean);

        String summaryText = summary(box).getText().toString();
        // checks_summary is "%1$s checks": the count only selects the plural
        // form, the summary string itself carries the numbers.
        assertEquals("2 failing, 2 pending, 1 successful checks", summaryText);
        assertEquals(5, container(box).getChildCount());
    }

    @Test
    public void fillStatus_statusRows_showLabelAndDescription() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("my-ci", "all good", null, StatusState.Success)),
                PullRequest.MergeableState.Clean);
        View row = container(box).getChildAt(0);
        assertEquals("my-ci", ((TextView) row.findViewById(R.id.tv_label)).getText().toString());
        assertEquals("all good",
                ((TextView) row.findViewById(R.id.tv_desc)).getText().toString());
    }

    @Test
    public void fillStatus_statusWithoutTargetUrl_rowNotClickable() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "ok", null, StatusState.Success)),
                PullRequest.MergeableState.Clean);
        View row = container(box).getChildAt(0);
        assertNull(row.getTag());
        assertFalse(row.hasOnClickListeners());
    }

    @Test
    public void onClick_header_togglesContainerVisibility() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "ok", null, StatusState.Success)),
                PullRequest.MergeableState.Clean);
        // Collapsed by default (fillStatus ends with setStatusesExpanded(false));
        // the header click expands, the next click collapses again.
        assertEquals(View.GONE, container(box).getVisibility());

        header(box).performClick();
        assertEquals(View.VISIBLE, container(box).getVisibility());

        header(box).performClick();
        assertEquals(View.GONE, container(box).getVisibility());
    }

    @Test
    public void onClick_statusRowWithTargetUrl_triesToOpenBrowser() {
        DiagnosticLogActivity activity =
                Robolectric.buildActivity(DiagnosticLogActivity.class).setup().get();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "ok", "https://example.com/build/1", StatusState.Success)),
                PullRequest.MergeableState.Clean);
        View row = container(box).getChildAt(0);

        row.performClick();

        // Robolectric has no external browser installed, so the open attempt
        // ends in the "no browser found" toast instead of a started intent.
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(activity.getString(R.string.no_browser_found),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertNotNull(new CommitStatusBox(activity, null));
        assertNotNull(new CommitStatusBox(activity, null, 0));
    }

    @Test
    public void fillStatus_nullState_countsAsPending() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.fillStatus(Collections.singletonList(
                status("ci", "waiting", null, null)),
                PullRequest.MergeableState.Unstable);
        assertEquals(activity.getString(R.string.pull_merge_status_pending),
                statusLabel(box).getText().toString());
    }

    @Test
    public void onClick_unknownView_doesNothing() {
        FragmentActivity activity = activity();
        CommitStatusBox box = new CommitStatusBox(activity);
        box.onClick(new View(activity));
    }
}
