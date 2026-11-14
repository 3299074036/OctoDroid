package com.gh4a.model;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.CheckRun;
import com.meisolsson.githubsdk.model.CheckRunConclusion;
import com.meisolsson.githubsdk.model.CheckRunState;
import com.meisolsson.githubsdk.model.Status;
import com.meisolsson.githubsdk.model.StatusState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class StatusWrapperTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Status status(StatusState state) {
        Status s = mock(Status.class);
        when(s.context()).thenReturn("ci/travis");
        when(s.description()).thenReturn("The build passed");
        when(s.targetUrl()).thenReturn("https://example.com/build/1");
        when(s.state()).thenReturn(state);
        return s;
    }

    private static CheckRun checkRun(CheckRunState state, CheckRunConclusion conclusion,
            Date startedAt, Date completedAt, String title) {
        CheckRun c = mock(CheckRun.class);
        when(c.name()).thenReturn("build");
        when(c.detailsUrl()).thenReturn("https://example.com/run/1");
        when(c.state()).thenReturn(state);
        when(c.conclusion()).thenReturn(conclusion);
        when(c.startedAt()).thenReturn(startedAt);
        when(c.completedAt()).thenReturn(completedAt);
        if (title != null) {
            CheckRun.Output output = mock(CheckRun.Output.class);
            when(output.title()).thenReturn(title);
            when(c.output()).thenReturn(output);
        } else {
            when(c.output()).thenReturn(null);
        }
        return c;
    }

    // ---- Status constructor ----

    @Test
    public void status_success() {
        StatusWrapper w = new StatusWrapper(status(StatusState.Success));
        assertEquals(StatusWrapper.State.Success, w.state());
        assertEquals("ci/travis", w.label());
        assertEquals("The build passed", w.description());
        assertEquals("https://example.com/build/1", w.targetUrl());
    }

    @Test
    public void status_error_isFailed() {
        assertEquals(StatusWrapper.State.Failed,
                new StatusWrapper(status(StatusState.Error)).state());
    }

    @Test
    public void status_failure_isFailed() {
        assertEquals(StatusWrapper.State.Failed,
                new StatusWrapper(status(StatusState.Failure)).state());
    }

    @Test
    public void status_pending_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown,
                new StatusWrapper(status(StatusState.Pending)).state());
    }

    @Test
    public void status_expected_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown,
                new StatusWrapper(status(StatusState.Expected)).state());
    }

    @Test
    public void status_nullState_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown,
                new StatusWrapper(status(null)).state());
    }

    @Test
    public void status_nullFields_passThrough() {
        Status s = mock(Status.class);
        when(s.state()).thenReturn(StatusState.Success);
        StatusWrapper w = new StatusWrapper(s);
        assertNull(w.label());
        assertNull(w.description());
        assertNull(w.targetUrl());
    }

    // ---- CheckRun constructor: pending states ----

    @Test
    public void checkRun_waiting_pendingDescription() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Waiting, null, null, null, null));
        assertEquals(StatusWrapper.State.Unknown, w.state());
        assertEquals("Waiting for run", w.description());
        assertEquals("build", w.label());
        assertEquals("https://example.com/run/1", w.targetUrl());
    }

    @Test
    public void checkRun_requested_pendingDescription() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Requested, null, null, null, null));
        assertEquals(StatusWrapper.State.Unknown, w.state());
        assertEquals("Waiting for run", w.description());
    }

    @Test
    public void checkRun_queued_pendingDescription() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Queued, null, null, null, null));
        assertEquals(StatusWrapper.State.Unknown, w.state());
        assertEquals("Waiting for run", w.description());
    }

    @Test
    public void checkRun_inProgress_runningDescription() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.InProgress, null, null, null, null));
        assertEquals(StatusWrapper.State.Unknown, w.state());
        assertEquals("Running", w.description());
    }

    @Test
    public void checkRun_nullState_pendingDescription() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(null, null, null, null, null));
        assertEquals(StatusWrapper.State.Unknown, w.state());
        assertEquals("Waiting for run", w.description());
    }

    // ---- CheckRun constructor: completed ----

    @Test
    public void checkRun_completedSuccess_noTitleNoRuntime() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, null, null, null));
        assertEquals(StatusWrapper.State.Success, w.state());
        assertNull(w.description());
    }

    @Test
    public void checkRun_completedFailure_isFailed() {
        assertEquals(StatusWrapper.State.Failed, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Failure, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedTimedOut_isFailed() {
        assertEquals(StatusWrapper.State.Failed, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.TimedOut, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedCancelled_isFailed() {
        assertEquals(StatusWrapper.State.Failed, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Cancelled, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedNeutral_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Neutral, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedSkipped_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Skipped, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedActionRequired_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.ActionRequired, null, null, null))
                .state());
    }

    @Test
    public void checkRun_completedNullConclusion_isUnknown() {
        assertEquals(StatusWrapper.State.Unknown, new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, null, null, null, null)).state());
    }

    @Test
    public void checkRun_completedTitleOnly_descriptionIsTitle() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success,
                        null, null, "All checks passed"));
        assertEquals("All checks passed", w.description());
    }

    @Test
    public void checkRun_completedRuntimeSecondsOnly() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 45 * 1000L);
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, null));
        assertEquals("Finished after 45 s", w.description());
    }

    @Test
    public void checkRun_completedRuntimeMinutesOnly() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 125 * 1000L);
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, null));
        assertEquals("Finished after 2 min 5 s", w.description());
    }

    @Test
    public void checkRun_completedRuntimeHours() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 3665 * 1000L);
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, null));
        assertEquals("Finished after 1 h 1 min", w.description());
    }

    @Test
    public void checkRun_completedRuntimeExactlyOneHour() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 3600 * 1000L);
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, null));
        assertEquals("Finished after 1 h 0 min", w.description());
    }

    @Test
    public void checkRun_completedTitleAndRuntime_joined() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 30 * 1000L);
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, "Done"));
        assertEquals("Done — Finished after 30 s", w.description());
    }

    @Test
    public void checkRun_completedOnlyStartDate_noRuntime() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success,
                        new Date(1000000L), null, "T"));
        assertEquals("T", w.description());
    }

    @Test
    public void checkRun_completedOnlyEndDate_noRuntime() {
        StatusWrapper w = new StatusWrapper(context(),
                checkRun(CheckRunState.Completed, CheckRunConclusion.Success,
                        null, new Date(1000000L), "T"));
        assertEquals("T", w.description());
    }

    @Test
    public void checkRun_outputNull_titleAbsent_runtimeOnly() {
        Date start = new Date(1000000L);
        Date end = new Date(1000000L + 5 * 1000L);
        CheckRun c = checkRun(CheckRunState.Completed, CheckRunConclusion.Success, start, end, null);
        StatusWrapper w = new StatusWrapper(context(), c);
        assertEquals("Finished after 5 s", w.description());
    }

    @Test
    public void checkRun_runtimeFormatStrings_matchResources() {
        Context ctx = context();
        assertEquals("3 s", ctx.getString(R.string.check_runtime_format_seconds, 3L));
        assertEquals("2 min 5 s",
                ctx.getString(R.string.check_runtime_format_minutes, 2L, 5L));
        assertEquals("1 h 30 min",
                ctx.getString(R.string.check_runtime_format_hours, 1L, 30L));
    }
}
