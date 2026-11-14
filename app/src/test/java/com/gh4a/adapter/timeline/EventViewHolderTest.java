package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.model.TimelineItem;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueStateReason;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.Rename;
import com.meisolsson.githubsdk.model.Team;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import com.meisolsson.githubsdk.model.PullRequest;

@RunWith(RobolectricTestRunner.class)
public class EventViewHolderTest {
    private Context context;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
    }

    private EventViewHolder holder(boolean isPr) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.row_timeline_event, null, false);
        return new EventViewHolder(view, "owner", "repo", isPr);
    }

    private IssueEvent event(IssueEventType type) {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(type);
        when(e.actor()).thenReturn(User.builder().login("octocat").build());
        when(e.createdAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L));
        return e;
    }

    private TimelineItem.TimelineEvent item(IssueEvent e) {
        return new TimelineItem.TimelineEvent(e);
    }

    private String message(EventViewHolder h) {
        TextView tv = h.itemView.findViewById(R.id.tv_message);
        return tv.getText() == null ? null : tv.getText().toString();
    }

    @Test
    public void bind_nullEventType_noIconNoMessage() {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(null);
        when(e.actor()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_event_icon).getVisibility());
        assertEquals("", message(h));
    }

    @Test
    public void bind_closed_issue_completed() {
        EventViewHolder h = holder(false);
        h.bind(item(event(IssueEventType.Closed)));
        assertTrue(message(h).contains("octocat"));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.iv_event_icon).getVisibility());
    }

    @Test
    public void bind_closed_pr_usesPrIconAndText() {
        EventViewHolder h = holder(true);
        h.bind(item(event(IssueEventType.Closed)));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_closed_notPlanned_withCommit() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.NotPlanned);
        when(e.commitId()).thenReturn("abcdef1234567");
        when(e.commitUrl()).thenReturn("https://api.github.com/repos/owner/repo/commits/abcdef1234567");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
        assertFalse(message(h).contains("[commit]"));
    }

    @Test
    public void bind_closed_duplicate_withCommitInOtherRepo() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.Duplicate);
        when(e.commitId()).thenReturn("abc");
        when(e.commitUrl()).thenReturn("https://api.github.com/repos/other/other-repo/commits/abc");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("other/other-repo@abc"));
    }

    @Test
    public void bind_closed_shortCommitId_noCrash() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.commitId()).thenReturn("ab");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("ab"));
    }

    @Test
    public void bind_eachSimpleType_hasMessageAndIcon() {
        IssueEventType[] types = {
                IssueEventType.Reopened, IssueEventType.Merged, IssueEventType.Referenced,
                IssueEventType.Labeled, IssueEventType.Unlabeled, IssueEventType.Locked,
                IssueEventType.Unlocked, IssueEventType.Milestoned, IssueEventType.Demilestoned,
                IssueEventType.Renamed, IssueEventType.CommentDeleted, IssueEventType.ReviewDismissed,
                IssueEventType.HeadRefDeleted, IssueEventType.HeadRefRestored,
                IssueEventType.HeadRefForcePushed, IssueEventType.ConvertToDraft,
                IssueEventType.AutoMergeEnabled, IssueEventType.AutoSquashEnabled,
                IssueEventType.AutoRebaseEnabled, IssueEventType.AutoMergeDisabled,
                IssueEventType.AddedToMergeQueue, IssueEventType.RemovedFromMergeQueue,
                IssueEventType.ReviewRequested, IssueEventType.ReadyForReview,
                IssueEventType.ReviewRequestRemoved, IssueEventType.CrossReferenced,
                IssueEventType.Transferred,
        };
        for (IssueEventType type : types) {
            EventViewHolder h = holder(false);
            h.bind(item(event(type)));
            assertTrue("type " + type, message(h) != null && message(h).length() > 0);
            assertEquals("type " + type, View.VISIBLE,
                    h.itemView.findViewById(R.id.iv_event_icon).getVisibility());
        }
    }

    @Test
    public void bind_unhandledType_noMessageNoIcon() {
        EventViewHolder h = holder(false);
        h.bind(item(event(IssueEventType.Commented)));
        assertEquals("", message(h));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_event_icon).getVisibility());
    }

    @Test
    public void bind_assigned_self_vs_other() {
        IssueEvent self = event(IssueEventType.Assigned);
        when(self.assignee()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(self));
        assertTrue(message(h).contains("octocat"));

        IssueEvent other = event(IssueEventType.Assigned);
        when(other.assignee()).thenReturn(User.builder().login("bob").build());
        EventViewHolder h2 = holder(false);
        h2.bind(item(other));
        assertTrue(message(h2).contains("octocat"));
        assertTrue(message(h2).contains("bob"));
    }

    @Test
    public void bind_unassigned_self() {
        IssueEvent e = event(IssueEventType.Unassigned);
        when(e.assignee()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_locked_withReason() {
        IssueEvent e = event(IssueEventType.Locked);
        when(e.lockReason()).thenReturn("too heated");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("too heated"));
    }

    @Test
    public void bind_milestoned_nullMilestone_usesDeleted() {
        IssueEvent e = event(IssueEventType.Milestoned);
        when(e.milestone()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_milestoned_withMilestone() {
        IssueEvent e = event(IssueEventType.Milestoned);
        when(e.milestone()).thenReturn(Milestone.builder().title("v2").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("v2"));
    }

    @Test
    public void bind_renamed_nullRename_usesDeleted() {
        IssueEvent e = event(IssueEventType.Renamed);
        when(e.rename()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_renamed_withRename() {
        IssueEvent e = event(IssueEventType.Renamed);
        Rename rename = mock(Rename.class);
        when(rename.from()).thenReturn("old title");
        when(rename.to()).thenReturn("new title");
        when(e.rename()).thenReturn(rename);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("old title"));
        assertTrue(message(h).contains("new title"));
    }


    @Test
    public void bind_reviewRequested_withReviewerList() {
        IssueEvent e = event(IssueEventType.ReviewRequested);
        when(e.requestedTeam()).thenReturn(null);
        when(e.requestedReviewers()).thenReturn(Arrays.asList(
                User.builder().login("alice").build(), User.builder().login("bob").build()));
        when(e.reviewRequester()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("alice"));
        assertTrue(message(h).contains("bob"));
    }

    @Test
    public void bind_reviewRequested_singleReviewer() {
        IssueEvent e = event(IssueEventType.ReviewRequested);
        when(e.requestedTeam()).thenReturn(null);
        when(e.requestedReviewers()).thenReturn(null);
        when(e.requestedReviewer()).thenReturn(User.builder().login("carol").build());
        when(e.reviewRequester()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("carol"));
    }

    @Test
    public void bind_reviewDismissed_withCommit() {
        IssueEvent e = event(IssueEventType.ReviewDismissed);
        IssueEvent.DismissedReview dr = mock(IssueEvent.DismissedReview.class);
        when(dr.dismissalCommitId()).thenReturn("deadbee1234567");
        when(e.dismissedReview()).thenReturn(dr);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("deadbee"));
    }

    @Test
    public void bind_reviewDismissed_withoutReview() {
        IssueEvent e = event(IssueEventType.ReviewDismissed);
        when(e.dismissedReview()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).length() > 0);
    }

    @Test
    public void bind_crossReferenced_sameRepo_showsNumber() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(42)
                .url("https://api.github.com/repos/owner/repo/issues/42").build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("#42"));
        assertFalse(message(h).contains("[source]"));
    }

    @Test
    public void bind_crossReferenced_otherRepo_showsRepoAndNumber() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(7)
                .url("https://api.github.com/repos/other/other-repo/issues/7").build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("other/other-repo#7"));
    }

    @Test
    public void bind_crossReferenced_nullSource_removesPlaceholder() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        when(e.source()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[source]"));
    }

    @Test
    public void bind_crossReferenced_nullNumber_usesDeletedNoLink() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(null)
                .url("https://api.github.com/repos/owner/repo/issues/9").build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_assignerPreferredOverActor() {
        IssueEvent e = event(IssueEventType.Assigned);
        when(e.assigner()).thenReturn(User.builder().login("assigner").build());
        when(e.assignee()).thenReturn(User.builder().login("bob").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("assigner"));
    }

    @Test
    public void bind_closed_nullCreatedAt_timePlaceholderRemoved() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.createdAt()).thenReturn(null);
        when(e.commitId()).thenReturn("abcdef1");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[time]"));
        assertTrue(message(h).contains("abcdef1"));
    }

    @Test
    public void bind_botActor_appendsBotSuffix() {
        IssueEvent e = event(IssueEventType.Reopened);
        when(e.actor()).thenReturn(User.builder().login("botty")
                .type(com.meisolsson.githubsdk.model.UserType.Bot).build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("botty"));
        assertFalse(message(h).contains("[bot]"));
    }

    @Test
    public void bind_labelPlaceholder_replaced() {
        IssueEvent e = event(IssueEventType.Labeled);
        when(e.label()).thenReturn(Label.builder().name("bug").color("ff0000").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).length() > 0);
    }

    @Test
    public void onClick_avatarContainer_startsUserActivity() {
        EventViewHolder h = holder(false);
        h.bind(item(event(IssueEventType.Reopened)));
        h.itemView.findViewById(R.id.avatar_container).performClick();
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_otherView_noop() {
        EventViewHolder h = holder(false);
        h.bind(item(event(IssueEventType.Reopened)));
        h.onClick(h.itemView.findViewById(R.id.tv_message));
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void bind_closed_prWithCommit() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(true);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
        assertFalse(message(h).contains("[commit]"));
    }

    @Test
    public void bind_closed_notPlanned_withoutCommit() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.NotPlanned);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("not planned"));
    }

    @Test
    public void bind_closed_duplicate_withoutCommit() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.Duplicate);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("duplicate"));
    }

    @Test
    public void bind_reopened_pr() {
        EventViewHolder h = holder(true);
        h.bind(item(event(IssueEventType.Reopened)));
        assertTrue(message(h).contains("reopened"));
    }

    @Test
    public void bind_merged_withCommit() {
        IssueEvent e = event(IssueEventType.Merged);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
    }

    @Test
    public void bind_referenced_prWithCommit() {
        IssueEvent e = event(IssueEventType.Referenced);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(true);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
    }

    @Test
    public void bind_referenced_prWithoutCommit() {
        EventViewHolder h = holder(true);
        h.bind(item(event(IssueEventType.Referenced)));
        assertTrue(message(h).length() > 0);
        assertFalse(message(h).contains("[commit]"));
    }

    @Test
    public void bind_referenced_issueWithCommit() {
        IssueEvent e = event(IssueEventType.Referenced);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
    }

    @Test
    public void bind_assigned_self_pr() {
        IssueEvent e = event(IssueEventType.Assigned);
        when(e.assignee()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(true);
        h.bind(item(e));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_assigned_nullActor_usesDeleted() {
        IssueEvent e = event(IssueEventType.Assigned);
        when(e.actor()).thenReturn(null);
        when(e.assignee()).thenReturn(User.builder().login("bob").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
        assertTrue(message(h).contains("bob"));
    }

    @Test
    public void bind_assigned_nullAssignee_usesDeleted() {
        IssueEvent e = event(IssueEventType.Assigned);
        when(e.assignee()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_unassigned_other() {
        IssueEvent e = event(IssueEventType.Unassigned);
        when(e.assignee()).thenReturn(User.builder().login("bob").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("octocat"));
        assertTrue(message(h).contains("bob"));
    }

    @Test
    public void bind_milestoned_nullTitle_usesDeleted() {
        IssueEvent e = event(IssueEventType.Milestoned);
        Milestone m = mock(Milestone.class);
        when(m.title()).thenReturn(null);
        when(e.milestone()).thenReturn(m);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_renamed_nullFromAndTo() {
        IssueEvent e = event(IssueEventType.Renamed);
        Rename rename = mock(Rename.class);
        when(rename.from()).thenReturn(null);
        when(rename.to()).thenReturn("new-name");
        when(e.rename()).thenReturn(rename);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("new-name"));
        assertTrue(message(h).contains(context.getString(R.string.deleted)));
        IssueEvent e2 = event(IssueEventType.Renamed);
        Rename rename2 = mock(Rename.class);
        when(rename2.from()).thenReturn("old-name");
        when(rename2.to()).thenReturn(null);
        when(e2.rename()).thenReturn(rename2);
        EventViewHolder h2 = holder(false);
        h2.bind(item(e2));
        assertTrue(message(h2).contains("old-name"));
    }

    @Test
    public void bind_reviewRequested_withTeam() {
        IssueEvent e = event(IssueEventType.ReviewRequested);
        Team team = mock(Team.class);
        when(team.name()).thenReturn("team-x");
        when(e.requestedTeam()).thenReturn(team);
        when(e.reviewRequester()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("team-x"));
    }

    @Test
    public void bind_reviewRequestRemoved_withTeam() {
        IssueEvent e = event(IssueEventType.ReviewRequestRemoved);
        Team team = mock(Team.class);
        when(team.name()).thenReturn("core");
        when(e.requestedTeam()).thenReturn(team);
        when(e.reviewRequester()).thenReturn(User.builder().login("octocat").build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("owner/core"));
    }

    @Test
    public void bind_crossReferenced_pr() {
        EventViewHolder h = holder(true);
        h.bind(item(event(IssueEventType.CrossReferenced)));
        assertTrue(message(h).contains("pull request"));
    }

    @Test
    public void bind_labeled_withCommitId_noCommitPlaceholder() {
        // commitId set but the template has no [commit]: early return.
        IssueEvent e = event(IssueEventType.Labeled);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[commit]"));
        assertFalse(message(h).contains("abcdef1"));
    }

    @Test
    public void bind_closed_commitUrlNotMatchingPattern_usesSameRepo() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.NotPlanned);
        when(e.commitId()).thenReturn("abcdef1234567");
        when(e.commitUrl()).thenReturn("https://example.com/nope");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("abcdef1"));
        assertFalse(message(h).contains("example.com"));
    }

    @Test
    public void bind_closed_commitSameOwnerOtherRepo_showsRepo() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.stateReason()).thenReturn(IssueStateReason.Duplicate);
        when(e.commitId()).thenReturn("abc");
        when(e.commitUrl()).thenReturn("https://api.github.com/repos/owner/other-repo/commits/abc");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("owner/other-repo@abc"));
    }

    @Test
    public void bind_botActor_replacesBotPlaceholder() {
        IssueEvent e = event(IssueEventType.Labeled);
        when(e.actor()).thenReturn(User.builder().login("dependabot[bot]")
                .type(com.meisolsson.githubsdk.model.UserType.Bot).build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[bot]"));
        assertTrue(message(h).contains("dependabot"));
    }

    @Test
    public void bind_crossReferenced_nullIssue_removesPlaceholder() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        when(source.issue()).thenReturn(null);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[source]"));
    }

    @Test
    public void bind_crossReferenced_sameOwnerOtherRepo_showsRepoAndNumber() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(7)
                .url("https://api.github.com/repos/owner/other-repo/issues/7").build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertTrue(message(h).contains("owner/other-repo#7"));
    }

    @Test
    public void bind_nullCreatedAt_timePlaceholderEmpty() {
        IssueEvent e = event(IssueEventType.Labeled);
        when(e.createdAt()).thenReturn(null);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        assertFalse(message(h).contains("[time]"));
    }

    @Test
    public void spanClick_commit_startsCommitActivity() {
        IssueEvent e = event(IssueEventType.Closed);
        when(e.commitId()).thenReturn("abcdef1234567");
        EventViewHolder h = holder(false);
        h.bind(item(e));
        TextView tv = h.itemView.findViewById(R.id.tv_message);
        android.text.Spanned spanned = (android.text.Spanned) tv.getText();
        com.gh4a.widget.IntentSpan[] spans = spanned.getSpans(0, spanned.length(),
                com.gh4a.widget.IntentSpan.class);
        assertTrue(spans.length >= 1);
        spans[0].onClick(tv);
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.CommitActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void spanClick_sourceIssue_startsIssueActivity() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(42)
                .url("https://api.github.com/repos/owner/repo/issues/42").build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        TextView tv = h.itemView.findViewById(R.id.tv_message);
        android.text.Spanned spanned = (android.text.Spanned) tv.getText();
        com.gh4a.widget.IntentSpan[] spans = spanned.getSpans(0, spanned.length(),
                com.gh4a.widget.IntentSpan.class);
        assertTrue(spans.length >= 1);
        spans[0].onClick(tv);
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.IssueActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void spanClick_sourcePullRequest_startsPullRequestActivity() {
        IssueEvent e = event(IssueEventType.CrossReferenced);
        IssueEvent.CrossReferenceSource source = mock(IssueEvent.CrossReferenceSource.class);
        Issue sourceIssue = Issue.builder().number(42)
                .url("https://api.github.com/repos/owner/repo/issues/42")
                .pullRequest(mock(PullRequest.class)).build();
        when(source.issue()).thenReturn(sourceIssue);
        when(e.source()).thenReturn(source);
        EventViewHolder h = holder(false);
        h.bind(item(e));
        TextView tv = h.itemView.findViewById(R.id.tv_message);
        android.text.Spanned spanned = (android.text.Spanned) tv.getText();
        com.gh4a.widget.IntentSpan[] spans = spanned.getSpans(0, spanned.length(),
                com.gh4a.widget.IntentSpan.class);
        assertTrue(spans.length >= 1);
        spans[0].onClick(tv);
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.PullRequestActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void onClick_avatar_mannequin_noActivity() {
        IssueEvent e = event(IssueEventType.Labeled);
        when(e.actor()).thenReturn(User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build());
        EventViewHolder h = holder(false);
        h.bind(item(e));
        h.itemView.findViewById(R.id.avatar_container).performClick();
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
