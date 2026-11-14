package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CompareActivity;
import com.gh4a.activities.GistActivity;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.ReleaseInfoActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.ReviewActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.activities.WikiListActivity;
import com.meisolsson.githubsdk.model.Discussion;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.GitHubEventType;
import com.meisolsson.githubsdk.model.GitHubWikiPage;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.ReferenceType;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.Team;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.payload.CreatePayload;
import com.meisolsson.githubsdk.model.payload.DeletePayload;
import com.meisolsson.githubsdk.model.payload.DiscussionPayload;
import com.meisolsson.githubsdk.model.payload.FollowPayload;
import com.meisolsson.githubsdk.model.payload.ForkPayload;
import com.meisolsson.githubsdk.model.payload.GistPayload;
import com.meisolsson.githubsdk.model.payload.GitHubPayload;
import com.meisolsson.githubsdk.model.payload.GollumPayload;
import com.meisolsson.githubsdk.model.payload.IssueCommentPayload;
import com.meisolsson.githubsdk.model.payload.IssuesPayload;
import com.meisolsson.githubsdk.model.payload.MemberPayload;
import com.meisolsson.githubsdk.model.payload.MembershipPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewCommentPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewPayload;
import com.meisolsson.githubsdk.model.payload.PushPayload;
import com.meisolsson.githubsdk.model.payload.ReleasePayload;
import com.meisolsson.githubsdk.model.payload.TeamAddPayload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

/**
 * EventNavigation.resolveIntent (F1): every GitHubEventType branch.
 * Events are built with the SDK AutoValue builders; models without a
 * builder (Release/Discussion/GitHubWikiPage/Review) are Mockito mocks.
 */
@RunWith(RobolectricTestRunner.class)
public class EventNavigationTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static GitHubEvent event(GitHubEventType type, GitHubPayload payload) {
        return eventWithRepo(type, payload, "octocat/hello");
    }

    private static GitHubEvent eventWithRepo(GitHubEventType type, GitHubPayload payload,
            String repo) {
        GitHubEvent.Builder b = GitHubEvent.builder().type(type).id("e1").payload(payload);
        if (repo != null) {
            b.repo(GitHubEvent.RepoIdentifier.builder().repoWithUserName(repo).build());
        }
        return b.build();
    }

    private static User user(String login) {
        return User.builder().login(login).build();
    }

    private static void assertRepoIntent(Intent intent, String owner, String repo) {
        assertNotNull(intent);
        assertEquals(RepositoryActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(owner, intent.getExtras().getString("owner"));
        assertEquals(repo, intent.getExtras().getString("repo"));
    }

    // ---- guards ----

    @Test
    public void resolve_nullContext_returnsNull() {
        assertNull(EventNavigation.resolveIntent(null,
                event(GitHubEventType.WatchEvent, mock(MemberPayload.class))));
    }

    @Test
    public void resolve_nullEvent_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), null));
    }

    @Test
    public void resolve_nullType_returnsNull() {
        GitHubEvent e = GitHubEvent.builder().id("e")
                .payload(mock(MemberPayload.class)).build();
        assertNull(EventNavigation.resolveIntent(context(), e));
    }

    @Test
    public void resolve_nullPayload_returnsNull() {
        GitHubEvent e = GitHubEvent.builder().type(GitHubEventType.WatchEvent).id("e").build();
        assertNull(EventNavigation.resolveIntent(context(), e));
    }

    // NOTE: hasInvalidPayload's exact-base-class branch (payload.getClass() ==
    // GitHubPayload.class) is unreachable in unit tests: GitHubPayload is
    // abstract, so no public constructor/builder/mock can produce an instance
    // whose runtime class is exactly GitHubPayload. The null-payload and
    // null-type guards above cover the reachable invalid cases.

    @Test
    public void resolve_nullRepo_fallsBackToEmptyOwnerAndName() {
        GitHubEvent e = eventWithRepo(GitHubEventType.WatchEvent,
                mock(MemberPayload.class), null);
        assertRepoIntent(EventNavigation.resolveIntent(context(), e), "", "");
    }

    @Test
    public void resolve_malformedRepo_fallsBackToEmptyOwnerAndName() {
        GitHubEvent e = eventWithRepo(GitHubEventType.WatchEvent,
                mock(MemberPayload.class), "no-slash");
        assertRepoIntent(EventNavigation.resolveIntent(context(), e), "", "");
    }

    // ---- async / no-destination types ----

    @Test
    public void resolve_commitCommentEvent_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.CommitCommentEvent,
                mock(com.meisolsson.githubsdk.model.payload.CommitCommentPayload.class))));
    }

    @Test
    public void resolve_downloadEvent_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DownloadEvent,
                mock(com.meisolsson.githubsdk.model.payload.DownloadPayload.class))));
    }

    @Test
    public void resolve_pullRequestReviewCommentEvent_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().build())));
    }

    // ---- repo-destination types ----

    @Test
    public void resolve_deleteEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DeleteEvent, DeletePayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_deploymentEvents_returnRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DeploymentEvent,
                mock(com.meisolsson.githubsdk.model.payload.DeploymentPayload.class))),
                "octocat", "hello");
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DeploymentStatusEvent,
                mock(com.meisolsson.githubsdk.model.payload.DeploymentStatusPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_forkApplyEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ForkApplyEvent,
                mock(com.meisolsson.githubsdk.model.payload.ForkApplyPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_memberEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.MemberEvent, MemberPayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_pageBuildEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PageBuildEvent,
                mock(com.meisolsson.githubsdk.model.payload.PageBuildPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_publicEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PublicEvent,
                mock(com.meisolsson.githubsdk.model.payload.PublicPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_repositoryEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.RepositoryEvent,
                mock(com.meisolsson.githubsdk.model.payload.RepositoryPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_statusEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.StatusEvent,
                mock(com.meisolsson.githubsdk.model.payload.StatusPayload.class))),
                "octocat", "hello");
    }

    @Test
    public void resolve_watchEvent_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.WatchEvent, mock(MemberPayload.class))),
                "octocat", "hello");
    }

    // ---- CreateEvent ----

    @Test
    public void resolve_createBranch_returnsRepoIntentWithRef() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.CreateEvent, CreatePayload.builder()
                        .refType(ReferenceType.Branch).ref("main").build()));
        assertRepoIntent(intent, "octocat", "hello");
        assertEquals("main", intent.getExtras().getString("ref"));
    }

    @Test
    public void resolve_createTag_returnsRepoIntentWithRef() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.CreateEvent, CreatePayload.builder()
                        .refType(ReferenceType.Tag).ref("v1.0").build()));
        assertEquals("v1.0", intent.getExtras().getString("ref"));
    }

    @Test
    public void resolve_createRepository_returnsRepoIntentWithoutRef() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.CreateEvent, CreatePayload.builder()
                        .refType(ReferenceType.Repository).ref("main").build()));
        assertRepoIntent(intent, "octocat", "hello");
        assertNull(intent.getExtras().getString("ref"));
    }

    @Test
    public void resolve_createNullRefType_returnsRepoIntentWithoutRef() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.CreateEvent, CreatePayload.builder().ref("x").build()));
        assertRepoIntent(intent, "octocat", "hello");
        assertNull(intent.getExtras().getString("ref"));
    }

    // ---- Discussion / Follow / Fork / Gist / Gollum ----

    @Test
    public void resolve_discussionWithUrl_returnsViewIntent() {
        Discussion discussion = mock(Discussion.class);
        when(discussion.htmlUrl()).thenReturn("https://github.com/o/r/discussions/1");
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DiscussionEvent,
                DiscussionPayload.builder().discussion(discussion).build()));
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://github.com/o/r/discussions/1",
                intent.getData().toString());
    }

    @Test
    public void resolve_discussionWithoutUrl_returnsRepoIntent() {
        Discussion discussion = mock(Discussion.class);
        when(discussion.htmlUrl()).thenReturn(null);
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DiscussionEvent,
                DiscussionPayload.builder().discussion(discussion).build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_discussionNull_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.DiscussionEvent,
                DiscussionPayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_followEvent_returnsUserIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.FollowEvent,
                FollowPayload.builder().target(user("octocat")).build()));
        assertNotNull(intent);
        assertEquals(UserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octocat", intent.getExtras().getString("login"));
    }

    @Test
    public void resolve_followEventNullTarget_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.FollowEvent, FollowPayload.builder().build())));
    }

    @Test
    public void resolve_forkEvent_returnsForkeeRepoIntent() {
        Repository forkee = Repository.builder()
                .owner(user("someone")).name("forked").build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ForkEvent,
                ForkPayload.builder().forkee(forkee).build()));
        assertRepoIntent(intent, "someone", "forked");
    }

    @Test
    public void resolve_forkEventNullForkee_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ForkEvent, ForkPayload.builder().build())));
    }

    @Test
    public void resolve_gistEvent_returnsGistIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.GistEvent,
                GistPayload.builder().gist(Gist.builder().id("abc").build()).build()));
        assertNotNull(intent);
        assertEquals(GistActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("abc", intent.getExtras().getString("id"));
    }

    @Test
    public void resolve_gistEventNullGist_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.GistEvent, GistPayload.builder().build())));
    }

    @Test
    public void resolve_gollumEventWithPage_returnsWikiIntentWithPage() {
        GitHubWikiPage page = mock(GitHubWikiPage.class);
        when(page.sha()).thenReturn("page-sha");
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.GollumEvent, GollumPayload.builder()
                        .pages(Collections.singletonList(page)).build()));
        assertNotNull(intent);
        assertEquals(WikiListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("page-sha", intent.getExtras().getString("initial_page"));
    }

    @Test
    public void resolve_gollumEventNullPages_returnsWikiIntentWithoutPage() {
        // pages() is @NonNull in the AutoValue builder, so null can only be
        // observed via a mock (e.g. a deserialized payload missing the field).
        GollumPayload payload = mock(GollumPayload.class);
        when(payload.pages()).thenReturn(null);
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.GollumEvent, payload));
        assertEquals(WikiListActivity.class.getName(), intent.getComponent().getClassName());
        assertNull(intent.getExtras().getString("initial_page"));
    }

    @Test
    public void resolve_gollumEventEmptyPages_returnsWikiIntentWithoutPage() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.GollumEvent, GollumPayload.builder()
                        .pages(Collections.emptyList()).build()));
        assertNull(intent.getExtras().getString("initial_page"));
    }

    // ---- IssueComment / Issues ----

    @Test
    public void resolve_issueCommentWithPr_returnsPullRequestIntentWithMarkerPage() {
        PullRequest pr = PullRequest.builder()
                .htmlUrl("https://github.com/o/r/pull/5").build();
        Issue issue = Issue.builder().number(5).pullRequest(pr).build();
        GitHubComment comment = GitHubComment.builder().id(99L).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent, IssueCommentPayload.builder()
                        .issue(issue).comment(comment).build()));
        assertNotNull(intent);
        assertEquals(PullRequestActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(5, intent.getExtras().getInt("number"));
        assertEquals(PullRequestActivity.PAGE_CONVERSATION,
                intent.getExtras().getInt("initial_page"));
        assertNotNull(intent.getExtras().getParcelable("initial_comment"));
    }

    @Test
    public void resolve_issueCommentWithPrNoComment_returnsPullRequestIntentPageMinusOne() {
        PullRequest pr = PullRequest.builder()
                .htmlUrl("https://github.com/o/r/pull/5").build();
        Issue issue = Issue.builder().number(5).pullRequest(pr).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().issue(issue).build()));
        assertEquals(PullRequestActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(-1, intent.getExtras().getInt("initial_page"));
    }

    @Test
    public void resolve_issueCommentEventPrWithoutHtmlUrl_returnsIssueIntent() {
        // pullRequest present but htmlUrl null: the && second operand fails,
        // so the IssueActivity branch is taken instead of PullRequestActivity.
        Issue issue = Issue.builder().number(7)
                .pullRequest(PullRequest.builder().build()).build();
        GitHubComment comment = GitHubComment.builder().id(11L).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent, IssueCommentPayload.builder()
                        .issue(issue).comment(comment).build()));
        assertNotNull(intent);
        assertEquals(IssueActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void resolve_issueCommentWithoutPr_returnsIssueIntent() {
        Issue issue = Issue.builder().number(7).build();
        GitHubComment comment = GitHubComment.builder().id(11L).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent, IssueCommentPayload.builder()
                        .issue(issue).comment(comment).build()));
        assertNotNull(intent);
        assertEquals(IssueActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(7, intent.getExtras().getInt("number"));
        assertNotNull(intent.getExtras().getParcelable("initial_comment"));
    }

    @Test
    public void resolve_issueCommentCommentIdNull_markerIsNull() {
        Issue issue = Issue.builder().number(7).build();
        GitHubComment comment = GitHubComment.builder().build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent, IssueCommentPayload.builder()
                        .issue(issue).comment(comment).build()));
        assertNotNull(intent);
        assertNull(intent.getExtras().getParcelable("initial_comment"));
    }

    @Test
    public void resolve_issueCommentNullIssue_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder()
                        .comment(GitHubComment.builder().id(1L).build()).build())));
    }

    @Test
    public void resolve_issueCommentIssueNumberNull_returnsNull() {
        Issue issue = Issue.builder().build();
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().issue(issue).build())));
    }

    @Test
    public void resolve_issuesEvent_returnsIssueIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssuesEvent, IssuesPayload.builder()
                        .issue(Issue.builder().number(9).build()).build()));
        assertNotNull(intent);
        assertEquals(IssueActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(9, intent.getExtras().getInt("number"));
    }

    @Test
    public void resolve_issuesEventNullIssue_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssuesEvent, IssuesPayload.builder().build())));
    }

    @Test
    public void resolve_issuesEventNullNumber_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.IssuesEvent, IssuesPayload.builder()
                        .issue(Issue.builder().build()).build())));
    }

    // ---- Membership / PullRequest / Review / Push / Release / TeamAdd ----

    @Test
    public void resolve_membershipWithMember_returnsUserIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.MembershipEvent,
                MembershipPayload.builder().member(user("newbie")).build()));
        assertEquals(UserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("newbie", intent.getExtras().getString("login"));
    }

    @Test
    public void resolve_membershipWithoutMember_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.MembershipEvent, MembershipPayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_pullRequestEvent_returnsPullRequestIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().number(12).build()));
        assertEquals(PullRequestActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(12, intent.getExtras().getInt("number"));
    }

    @Test
    public void resolve_pullRequestEventNullNumber_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestEvent, PullRequestPayload.builder().build())));
    }

    @Test
    public void resolve_pullRequestReviewEvent_returnsReviewIntent() {
        Review review = mock(Review.class);
        PullRequest pr = PullRequest.builder().number(3).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().pullRequest(pr).review(review).build()));
        assertNotNull(intent);
        assertEquals(ReviewActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(3, intent.getExtras().getInt("issue_number"));
        assertTrue(intent.getExtras().getParcelable("review") == review);
    }

    @Test
    public void resolve_pullRequestReviewEventNullPr_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().build())));
    }

    @Test
    public void resolve_pullRequestReviewEventNullNumber_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder()
                        .pullRequest(PullRequest.builder().build()).build())));
    }

    @Test
    public void resolve_pushSingleCommit_returnsCommitIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PushEvent, PushPayload.builder()
                        .commits(Collections.singletonList(
                                GitCommit.builder().sha("abc123").build()))
                        .before("b").head("abc123").build()));
        assertNotNull(intent);
        assertEquals(CommitActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("abc123", intent.getExtras().getString("sha"));
    }

    @Test
    public void resolve_pushMultiCommit_returnsCompareIntent() {
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PushEvent, PushPayload.builder()
                        .commits(Arrays.asList(
                                GitCommit.builder().sha("a1").build(),
                                GitCommit.builder().sha("a2").build()))
                        .before("base1").head("head1").build()));
        assertNotNull(intent);
        assertEquals(CompareActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("base1", intent.getExtras().getString("base"));
        assertEquals("head1", intent.getExtras().getString("head"));
    }

    @Test
    public void resolve_pushNoCommits_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PushEvent, PushPayload.builder()
                        .commits(Collections.emptyList()).build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_pushNullCommits_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.PushEvent, PushPayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_releaseEvent_returnsReleaseIntent() {
        Release release = mock(Release.class);
        when(release.id()).thenReturn(77L);
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(release).build()));
        assertNotNull(intent);
        assertEquals(ReleaseInfoActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(77L, intent.getExtras().getLong("id"));
    }

    @Test
    public void resolve_releaseEventNullRelease_returnsNull() {
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ReleaseEvent, ReleasePayload.builder().build())));
    }

    @Test
    public void resolve_releaseEventNullId_returnsNull() {
        Release release = mock(Release.class);
        when(release.id()).thenReturn(null);
        assertNull(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(release).build())));
    }

    @Test
    public void resolve_teamAddWithOrg_returnsUserIntent() {
        Team team = Team.builder().organization(user("github")).build();
        Intent intent = EventNavigation.resolveIntent(context(), event(
                GitHubEventType.TeamAddEvent,
                TeamAddPayload.builder().team(team).build()));
        assertEquals(UserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("github", intent.getExtras().getString("login"));
    }

    @Test
    public void resolve_teamAddNullTeam_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.TeamAddEvent, TeamAddPayload.builder().build())),
                "octocat", "hello");
    }

    @Test
    public void resolve_teamAddTeamWithoutOrg_returnsRepoIntent() {
        assertRepoIntent(EventNavigation.resolveIntent(context(), event(
                GitHubEventType.TeamAddEvent,
                TeamAddPayload.builder().team(Team.builder().build()).build())),
                "octocat", "hello");
    }
}
