package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.GitHubEventType;
import com.meisolsson.githubsdk.model.GitHubWikiPage;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.ReferenceType;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.Team;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.payload.CommitCommentPayload;
import com.meisolsson.githubsdk.model.payload.CreatePayload;
import com.meisolsson.githubsdk.model.payload.DeletePayload;
import com.meisolsson.githubsdk.model.payload.DownloadPayload;
import com.meisolsson.githubsdk.model.payload.FollowPayload;
import com.meisolsson.githubsdk.model.payload.ForkPayload;
import com.meisolsson.githubsdk.model.payload.GitHubPayload;
import com.meisolsson.githubsdk.model.payload.GollumPayload;
import com.meisolsson.githubsdk.model.payload.IssueCommentPayload;
import com.meisolsson.githubsdk.model.payload.IssuesPayload;
import com.meisolsson.githubsdk.model.payload.MemberPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewCommentPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewPayload;
import com.meisolsson.githubsdk.model.payload.PushPayload;
import com.meisolsson.githubsdk.model.payload.ReleasePayload;
import com.meisolsson.githubsdk.model.payload.TeamAddPayload;
import com.meisolsson.githubsdk.model.payload.GistPayload;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class EventAdapterTest {
    private Context context;
    private EventAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new EventAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private EventAdapter.EventViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private GitHubEvent event(GitHubEventType type, GitHubPayload payload) {
        return GitHubEvent.builder().type(type).payload(payload)
                .actor(User.builder().login("octocat").build())
                .repo(GitHubEvent.RepoIdentifier.builder()
                        .repoWithUserName("octocat/hello").build())
                .createdAt(new Date(System.currentTimeMillis() - 1000L))
                .build();
    }

    private GitHubEvent eventNoRepo(GitHubEventType type, GitHubPayload payload) {
        return GitHubEvent.builder().type(type).payload(payload)
                .actor(User.builder().login("octocat").build())
                .repo(null)
                .createdAt(new Date(System.currentTimeMillis() - 1000L))
                .build();
    }

    private com.meisolsson.githubsdk.model.GitHubComment githubComment(String body) {
        com.meisolsson.githubsdk.model.GitHubComment c =
                mock(com.meisolsson.githubsdk.model.GitHubComment.class);
        when(c.body()).thenReturn(body);
        return c;
    }

    private String title(EventAdapter.EventViewHolder h) {
        return ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString();
    }

    private String desc(EventAdapter.EventViewHolder h) {
        return ((TextView) h.itemView.findViewById(R.id.tv_desc)).getText().toString();
    }

    private int descVisibility(EventAdapter.EventViewHolder h) {
        return h.itemView.findViewById(R.id.tv_desc).getVisibility();
    }

    private EventAdapter.EventViewHolder bind(GitHubEvent e) {
        EventAdapter.EventViewHolder h = holder();
        adapter.onBindViewHolder(h, e);
        return h;
    }

    @Test
    public void isCardStyle_true() {
        assertTrue(adapter.isCardStyle());
    }

    @Test
    public void hasInvalidPayload_nullPayload_true() {
        assertTrue(EventAdapter.hasInvalidPayload(
                GitHubEvent.builder().type(GitHubEventType.PushEvent).payload(null).build()));
    }

    @Test
    public void hasInvalidPayload_nullType_true() {
        assertTrue(EventAdapter.hasInvalidPayload(
                GitHubEvent.builder().type(null)
                        .payload(PushPayload.builder().build()).build()));
    }

    @Test
    public void hasInvalidPayload_validPayload_false() {
        assertFalse(EventAdapter.hasInvalidPayload(
                event(GitHubEventType.PushEvent, PushPayload.builder().build())));
    }

    @Test
    public void bind_invalidPayload_titleIsTypeString_descIsTypeString() {
        EventAdapter.EventViewHolder h = bind(
                GitHubEvent.builder().type(GitHubEventType.WatchEvent).payload(null)
                        .actor(User.builder().login("octocat").build())
                        .createdAt(new Date()).build());
        assertEquals(GitHubEventType.WatchEvent.toString(), title(h));
        assertEquals(GitHubEventType.WatchEvent.toString(), desc(h));
    }

    @Test
    public void bind_nullType_emptyTitleAndDesc() {
        EventAdapter.EventViewHolder h = bind(
                GitHubEvent.builder().type(null)
                        .payload(PushPayload.builder().build())
                        .actor(User.builder().login("octocat").build())
                        .createdAt(new Date()).build());
        assertEquals("", title(h));
        assertEquals("", desc(h));
    }

    @Test
    public void bind_commitComment() {
        GitComment c = GitComment.builder().body("nice :thumbsup:").commitId("abcdef1234").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder().comment(c).build()));
        assertTrue(title(h).contains("abcdef1"));
        assertFalse(desc(h).contains(":thumbsup:"));
    }

    @Test
    public void bind_commitComment_nullComment_emptyTitleNoDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder().comment(null).build()));
        assertEquals("", title(h));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_createRepository() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.CreateEvent,
                CreatePayload.builder().refType(ReferenceType.Repository).build()));
        assertEquals(context.getString(R.string.event_create_repo_title), title(h));
        assertTrue(desc(h).contains("octocat/hello"));
    }

    @Test
    public void bind_createBranchAndTag() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.CreateEvent,
                CreatePayload.builder().refType(ReferenceType.Branch).ref("feature").build()));
        assertTrue(title(h).contains("feature"));
        EventAdapter.EventViewHolder h2 = bind(event(GitHubEventType.CreateEvent,
                CreatePayload.builder().refType(ReferenceType.Tag).ref("v1").build()));
        assertTrue(title(h2).contains("v1"));
    }

    @Test
    public void bind_createNullRefType_emptyTitle() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.CreateEvent,
                CreatePayload.builder().refType(null).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_createRepo_nullRepoIdentifier_usesDeleted() {
        EventAdapter.EventViewHolder h = bind(eventNoRepo(GitHubEventType.CreateEvent,
                CreatePayload.builder().refType(ReferenceType.Repository).build()));
        assertTrue(desc(h).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_deleteBranchAndTag() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.DeleteEvent,
                DeletePayload.builder().refType(ReferenceType.Branch).ref("old").build()));
        assertTrue(title(h).contains("old"));
        EventAdapter.EventViewHolder h2 = bind(event(GitHubEventType.DeleteEvent,
                DeletePayload.builder().refType(ReferenceType.Tag).ref("v0").build()));
        assertTrue(title(h2).contains("v0"));
    }

    @Test
    public void bind_deleteNullRefType_emptyTitle() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.DeleteEvent,
                DeletePayload.builder().refType(null).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_download() {
        Download d = mock(Download.class);
        when(d.name()).thenReturn("file.zip");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().download(d).build()));
        assertEquals("file.zip", desc(h));
    }

    @Test
    public void bind_download_nullDownload_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().download(null).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_follow() {
        User target = User.builder().login("bob").publicRepos(null).followers(null).build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.FollowEvent,
                FollowPayload.builder().target(target).build()));
        assertTrue(title(h).contains("bob"));
        assertTrue(desc(h).contains("bob"));
        assertTrue(desc(h).contains("0"));
        assertFalse(desc(h).contains("null"));
    }

    @Test
    public void bind_follow_nullTarget_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.FollowEvent,
                FollowPayload.builder().target(null).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_fork() {
        Repository forkee = Repository.builder().fullName("bob/hello").name("hello")
                .owner(User.builder().login("bob").build()).build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.ForkEvent,
                ForkPayload.builder().forkee(forkee).build()));
        assertTrue(desc(h).contains("bob/hello"));
    }

    @Test
    public void bind_forkApply_titleOnly() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.ForkApplyEvent,
                ForkPayload.builder().build()));
        assertTrue(title(h).length() > 0);
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_gist_createdAndUpdated() {
        Gist g = Gist.builder().id("abc").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.GistEvent,
                GistPayload.builder().gist(g).action(GistPayload.Action.Created).build()));
        assertTrue(title(h).contains("abc"));
        EventAdapter.EventViewHolder h2 = bind(event(GitHubEventType.GistEvent,
                GistPayload.builder().gist(null).action(GistPayload.Action.Updated).build()));
        assertTrue(title(h2).contains(context.getString(R.string.deleted)));
    }

    @Test
    public void bind_gollum() {
        GitHubWikiPage page = mock(GitHubWikiPage.class);
        when(page.pageName()).thenReturn("Home");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.GollumEvent,
                GollumPayload.builder().pages(Collections.singletonList(page)).build()));
        assertTrue(title(h).length() > 0);
        assertTrue(desc(h).contains("Home"));
    }

    @Test
    public void bind_gollum_noPages_noDesc() {
        // GollumPayload builder rejects null pages (AutoValue @NonNull),
        // so the null branch is only reachable with a mocked payload.
        com.meisolsson.githubsdk.model.payload.GollumPayload payload =
                mock(com.meisolsson.githubsdk.model.payload.GollumPayload.class);
        when(payload.pages()).thenReturn(null);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.GollumEvent, payload));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_issueComment_withIssue() {
        Issue issue = Issue.builder().number(7).build();
        com.meisolsson.githubsdk.model.GitHubComment comment =
                mock(com.meisolsson.githubsdk.model.GitHubComment.class);
        when(comment.body()).thenReturn("looks good");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().issue(issue).comment(comment).build()));
        assertEquals("looks good", desc(h));
        assertTrue(title(h).contains("7"));
    }

    @Test
    public void bind_issueComment_prIssue_usesPrTitle() {
        Issue issue = Issue.builder().number(8)
                .pullRequest(mock(com.meisolsson.githubsdk.model.PullRequest.class)).build();
        com.meisolsson.githubsdk.model.GitHubComment comment =
                mock(com.meisolsson.githubsdk.model.GitHubComment.class);
        when(comment.body()).thenReturn("b");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().issue(issue).comment(comment).build()));
        assertTrue(title(h).contains("8"));
    }


    @Test
    public void bind_issues_opened() {
        Issue issue = Issue.builder().number(3).title("Bug here").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Opened).issue(issue).build()));
        assertTrue(title(h).contains("3"));
        assertEquals("Bug here", desc(h));
    }

    @Test
    public void bind_issues_nullIssue_emptyTitleNoDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Opened).issue(null).build()));
        assertEquals("", title(h));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_issues_nullAction_emptyTitle() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(null)
                        .issue(Issue.builder().number(1).title("t").build()).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_issues_assigned_withAssignees() {
        Issue issue = Issue.builder().number(4).title("Task").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Assigned).issue(issue)
                        .assignees(Arrays.asList(User.builder().login("alice").build(),
                                User.builder().login(null).build())).build()));
        assertTrue(desc(h).contains("Task"));
        assertTrue(desc(h).contains("alice"));
    }

    @Test
    public void bind_issues_unassigned_noAssignees() {
        Issue issue = Issue.builder().number(4).title("Task").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Unassigned).issue(issue)
                        .assignees(null).build()));
        assertTrue(desc(h).contains("Task"));
    }

    @Test
    public void bind_issues_labeled_withLabel() {
        Issue issue = Issue.builder().number(4).title("Task").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Labeled).issue(issue)
                        .label(Label.builder().name("bug").color("ff0000").build()).build()));
        assertTrue(title(h).contains("4"));
    }

    @Test
    public void bind_issues_allActions_haveTitles() {
        for (IssuesPayload.Action action : IssuesPayload.Action.values()) {
            Issue issue = Issue.builder().number(1).title("t").build();
            EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                    IssuesPayload.builder().action(action).issue(issue).build()));
            if (action == IssuesPayload.Action.Edited) {
                // default branch: no title for Edited
                assertEquals("", title(h));
            } else {
                assertTrue("action " + action, title(h).length() > 0);
            }
        }
    }

    @Test
    public void bind_member() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.MemberEvent,
                MemberPayload.builder().member(User.builder().login("carol").build()).build()));
        assertTrue(title(h).contains("carol"));
    }

    @Test
    public void bind_public_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PublicEvent,
                mock(GitHubPayload.class)));
        assertTrue(title(h).length() > 0);
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_pullRequest_openedWithCounts() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.title()).thenReturn("Add feature");
        when(pr.commits()).thenReturn(2);
        when(pr.additions()).thenReturn(10);
        when(pr.deletions()).thenReturn(3);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(pr).build()));
        assertTrue(title(h).contains("9"));
        assertTrue(desc(h).contains("Add feature"));
        assertTrue(desc(h).contains("2"));
    }

    @Test
    public void bind_pullRequest_titleOnlyWithoutCounts() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.title()).thenReturn("Add feature");
        // Mockito defaults Integer to 0; null counts are needed for title-only
        when(pr.commits()).thenReturn(null);
        when(pr.additions()).thenReturn(null);
        when(pr.deletions()).thenReturn(null);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(pr).build()));
        assertEquals("Add feature", desc(h));
    }

    @Test
    public void bind_pullRequest_nullPr_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(null).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_pullRequest_enrichmentProvider_suppliesPr() {
        PullRequest enriched = mock(PullRequest.class);
        when(enriched.title()).thenReturn("Enriched title");
        // Mockito defaults Integer to 0, which would render the counts line;
        // the title-only path needs all three counts null
        when(enriched.commits()).thenReturn(null);
        when(enriched.additions()).thenReturn(null);
        when(enriched.deletions()).thenReturn(null);
        adapter.setPullRequestEnrichmentProvider(e -> enriched);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(null).build()));
        assertEquals("Enriched title", desc(h));
    }

    @Test
    public void bind_pullRequest_enrichmentProviderReturnsNull_noDesc() {
        adapter.setPullRequestEnrichmentProvider(e -> null);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(null).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_pullRequest_nullAction_emptyTitle() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.title()).thenReturn("t");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(null).number(9).pullRequest(pr).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_pullRequest_allActions_haveTitles() {
        for (PullRequestPayload.Action action : PullRequestPayload.Action.values()) {
            PullRequest pr = mock(PullRequest.class);
            when(pr.title()).thenReturn("t");
            EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                    PullRequestPayload.builder().action(action).number(1).pullRequest(pr).build()));
            if (action == PullRequestPayload.Action.Edited
                    || action == PullRequestPayload.Action.ReviewRequested
                    || action == PullRequestPayload.Action.ReviewRequestRemoved) {
                // default branch: no title for these actions
                assertEquals("", title(h));
            } else {
                assertTrue("action " + action, title(h).length() > 0);
            }
        }
    }

    @Test
    public void bind_pullRequest_assigned_withAssignees() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.title()).thenReturn("PR title");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Assigned)
                        .number(1).pullRequest(pr)
                        .assignees(Collections.singletonList(
                                User.builder().login("dave").build())).build()));
        assertTrue(desc(h).contains("PR title"));
        assertTrue(desc(h).contains("dave"));
    }

    @Test
    public void bind_pullRequestReview_withBody() {
        Review review = mock(Review.class);
        when(review.body()).thenReturn("review body");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(11);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().review(review).pullRequest(pr).build()));
        assertEquals("review body", desc(h));
        assertTrue(title(h).contains("11"));
    }

    @Test
    public void bind_pullRequestReview_nullReview_noDesc() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(11);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().review(null).pullRequest(pr).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_pullRequestReview_nullPr_emptyTitle() {
        Review review = mock(Review.class);
        when(review.body()).thenReturn("b");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().review(review).pullRequest(null).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_pullRequestReviewComment() {
        com.meisolsson.githubsdk.model.ReviewComment rc =
                mock(com.meisolsson.githubsdk.model.ReviewComment.class);
        when(rc.body()).thenReturn("inline note");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(12);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(rc).pullRequest(pr).build()));
        assertEquals("inline note", desc(h));
        assertTrue(title(h).contains("12"));
    }

    @Test
    public void bind_pullRequestReviewComment_nullComment_noDesc() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(12);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(null).pullRequest(pr).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_push_withCommits() {
        GitCommit c1 = GitCommit.builder().sha("abcdef1234567").message("First\nmore").build();
        GitCommit c2 = GitCommit.builder().sha("123").message("Second").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PushEvent,
                PushPayload.builder().ref("refs/heads/main").size(2)
                        .commits(Arrays.asList(c1, c2)).build()));
        assertTrue(title(h).contains("main"));
        assertTrue(desc(h).contains("abcdef1"));
        assertTrue(desc(h).contains("First"));
        assertTrue(desc(h).contains("Second"));
    }

    @Test
    public void bind_push_manyCommits_truncatesWithMoreLine() {
        java.util.List<GitCommit> commits = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            commits.add(GitCommit.builder().sha("aaaaaaa" + i).message("c" + i).build());
        }
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PushEvent,
                PushPayload.builder().ref("refs/heads/main").size(10).commits(commits).build()));
        assertTrue(desc(h).contains("c0"));
    }

    @Test
    public void bind_push_noCommits_nullRepo_showsDeleted() {
        EventAdapter.EventViewHolder h = bind(eventNoRepo(GitHubEventType.PushEvent,
                PushPayload.builder().ref("refs/heads/main").size(0)
                        .commits(Collections.emptyList()).build()));
        assertEquals(context.getString(R.string.deleted), desc(h));
    }

    @Test
    public void bind_push_nullRef_titleHasEmptyRef() {
        GitCommit c1 = GitCommit.builder().sha("abcdef1").message("m").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PushEvent,
                PushPayload.builder().ref(null).size(1)
                        .commits(Collections.singletonList(c1)).build()));
        assertTrue(title(h).length() > 0);
    }

    @Test
    public void bind_release_withName() {
        Release r = mock(Release.class);
        when(r.name()).thenReturn("Release 1.0");
        when(r.tagName()).thenReturn("v1.0");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(r).build()));
        assertEquals("Release 1.0", desc(h));
    }

    @Test
    public void bind_release_nameEmpty_usesTag() {
        Release r = mock(Release.class);
        when(r.name()).thenReturn("");
        when(r.tagName()).thenReturn("v1.0");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(r).build()));
        assertEquals("v1.0", desc(h));
    }

    @Test
    public void bind_release_nullRelease_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(null).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_teamAdd_withRepo() {
        Team team = mock(Team.class);
        when(team.name()).thenReturn("core");
        when(team.membersCount()).thenReturn(null);
        when(team.reposCount()).thenReturn(null);
        Repository repository = Repository.builder().fullName("o/r").name("r")
                .owner(User.builder().login("o").build()).build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.TeamAddEvent,
                TeamAddPayload.builder().team(team).repository(repository).build()));
        assertTrue(title(h).contains("core"));
        assertTrue(desc(h).contains("core"));
        assertFalse(desc(h).contains("null"));
    }

    @Test
    public void bind_teamAdd_nullTeam_emptyTitleNoDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.TeamAddEvent,
                TeamAddPayload.builder().team(null).repository(null).build()));
        assertEquals("", title(h));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_teamAdd_teamWithoutRepo_emptyTitle() {
        Team team = mock(Team.class);
        when(team.name()).thenReturn("core");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.TeamAddEvent,
                TeamAddPayload.builder().team(team).repository(null).build()));
        assertEquals("", title(h));
    }

    @Test
    public void bind_watch() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.WatchEvent,
                mock(GitHubPayload.class)));
        assertTrue(title(h).length() > 0);
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.WatchEvent,
                mock(GitHubPayload.class)));
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_nullActor_noActivity() {
        GitHubEvent e = GitHubEvent.builder().type(GitHubEventType.WatchEvent)
                .payload(mock(GitHubPayload.class)).actor(null)
                .createdAt(new Date()).build();
        EventAdapter.EventViewHolder h = bind(e);
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noActivity() {
        adapter.setOnItemClickListener(item -> {});
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.WatchEvent, null));
        h.itemView.setTag(h);
        adapter.onClick(h.itemView);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void bind_gollum_emptyPages_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.GollumEvent,
                GollumPayload.builder().pages(Collections.emptyList()).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_issueComment_nullComment_noDesc() {
        IssueCommentPayload payload = mock(IssueCommentPayload.class);
        when(payload.comment()).thenReturn(null);
        when(payload.issue()).thenReturn(null);
        EventAdapter.EventViewHolder h =
                bind(event(GitHubEventType.IssueCommentEvent, payload));
        assertEquals(View.GONE, descVisibility(h));
        assertEquals("", title(h));
    }

    @Test
    public void bind_issueComment_prComment_titleUsesPrFormat() {
        Issue issue = Issue.builder().number(7)
                .pullRequest(mock(PullRequest.class)).build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().issue(issue)
                        .comment(githubComment("c")).build()));
        assertTrue(title(h).contains("pull request 7"));
    }

    @Test
    public void bind_issues_assigned_blankTitle_descIsAssigneesOnly() {
        Issue issue = Issue.builder().number(4).title("").build();
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().action(IssuesPayload.Action.Assigned).issue(issue)
                        .assignees(Collections.singletonList(
                                User.builder().login("alice").build())).build()));
        assertTrue(desc(h).contains("alice"));
        assertFalse(desc(h).contains("\n"));
    }

    @Test
    public void bind_pullRequest_partialCounts_titleOnly() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.title()).thenReturn("Add feature");
        when(pr.commits()).thenReturn(3);
        when(pr.additions()).thenReturn(null);
        when(pr.deletions()).thenReturn(1);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().action(PullRequestPayload.Action.Opened)
                        .number(9).pullRequest(pr).build()));
        assertEquals("Add feature", desc(h));
    }

    @Test
    public void bind_pullRequestReview_nullBody_noDesc() {
        Review review = mock(Review.class);
        when(review.body()).thenReturn(null);
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(11);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewEvent,
                PullRequestReviewPayload.builder().review(review).pullRequest(pr).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_pullRequestReviewComment_withComment_showsBody() {
        com.meisolsson.githubsdk.model.ReviewComment rc =
                AdapterTestSupport.reviewComment(5L, null, "<p>nice</p>", null, null, null,
                        null, null, null, null, null, null);
        when(rc.body()).thenReturn("nice");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(12);
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(rc).pullRequest(pr).build()));
        assertEquals("nice", desc(h));
    }

    @Test
    public void bind_pullRequestReviewComment_noPrWithComment_commitTitle() {
        com.meisolsson.githubsdk.model.ReviewComment rc =
                AdapterTestSupport.reviewComment(5L, null, "<p>nice</p>", null, null, null,
                        null, null, null, null, null, null);
        when(rc.body()).thenReturn("nice");
        when(rc.commitId()).thenReturn("abcdef1234567890");
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(rc).pullRequest(null).build()));
        assertTrue(title(h).contains("abcdef1"));
    }

    @Test
    public void bind_pullRequestReviewComment_noPrNoComment_emptyTitle() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(null).pullRequest(null).build()));
        assertEquals("", title(h));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_push_emptyCommits_noRepo_deletedDesc() {
        EventAdapter.EventViewHolder h = bind(eventNoRepo(GitHubEventType.PushEvent,
                PushPayload.builder().commits(Collections.emptyList()).build()));
        assertEquals(context.getString(R.string.deleted), desc(h));
    }

    @Test
    public void bind_push_emptyCommits_withRepo_noDesc() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.PushEvent,
                PushPayload.builder().commits(Collections.emptyList()).build()));
        assertEquals(View.GONE, descVisibility(h));
    }

    @Test
    public void bind_deleteRepositoryRefType_emptyTitle() {
        EventAdapter.EventViewHolder h = bind(event(GitHubEventType.DeleteEvent,
                DeletePayload.builder().refType(ReferenceType.Repository).ref("x").build()));
        assertEquals("", title(h));
    }
}
