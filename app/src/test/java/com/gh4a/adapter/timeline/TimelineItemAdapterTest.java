package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.HttpImageGetter;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.Single;
import com.meisolsson.githubsdk.model.GitHubFile;

@RunWith(RobolectricTestRunner.class)
public class TimelineItemAdapterTest {
    private Context context;
    private LayoutInflater inflater;
    private ViewGroup parent;
    private List<GitHubCommentBase> edited;
    private List<GitHubCommentBase> deleted;
    private List<CharSequence> quoted;
    private List<CharSequence> added;
    private List<Long> replySelected;
    private List<String> translateCalls;
    private List<String> getTranslatedCalls;
    private String translatedBody;
    private long selectedReplyId;
    private TimelineItemAdapter adapter;

    private final TimelineItemAdapter.OnCommentAction callback =
            new TimelineItemAdapter.OnCommentAction() {
                @Override
                public void editComment(GitHubCommentBase comment) {
                    edited.add(comment);
                }

                @Override
                public void deleteComment(GitHubCommentBase comment) {
                    deleted.add(comment);
                }

                @Override
                public void quoteText(CharSequence text) {
                    quoted.add(text);
                }

                @Override
                public void addText(CharSequence text) {
                    added.add(text);
                }

                @Override
                public void onReplyCommentSelected(long replyToId) {
                    replySelected.add(replyToId);
                }

                @Override
                public long getSelectedReplyCommentId() {
                    return selectedReplyId;
                }

                @Override
                public String getShareSubject(GitHubCommentBase comment) {
                    return "subject";
                }

                @Override
                public Single<List<Reaction>> loadReactionDetails(GitHubCommentBase comment,
                        boolean bypassCache) {
                    return Single.just(new ArrayList<>());
                }

                @Override
                public Single<Reaction> addReaction(GitHubCommentBase comment, String content) {
                    return Single.error(new IllegalStateException("x"));
                }

                @Override
                public Single<Boolean> deleteReaction(GitHubCommentBase comment, long reactionId) {
                    return Single.just(true);
                }

                @Override
                public void translateBody(String type, long id, String bodyHtml) {
                    translateCalls.add(type + ":" + id);
                }

                @Override
                public String getTranslatedBody(String type, long id) {
                    getTranslatedCalls.add(type + ":" + id);
                    return translatedBody;
                }
            };

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
        edited = new ArrayList<>();
        deleted = new ArrayList<>();
        quoted = new ArrayList<>();
        added = new ArrayList<>();
        replySelected = new ArrayList<>();
        translateCalls = new ArrayList<>();
        getTranslatedCalls = new ArrayList<>();
        translatedBody = null;
        selectedReplyId = 0;
        adapter = new TimelineItemAdapter(context, "owner", "repo", 5, false, false, callback);
    }

    private Date ago(long ms) {
        return new Date(System.currentTimeMillis() - ms);
    }

    private TimelineItem.TimelineComment commentItem(Long id) {
        GitComment gc = GitComment.builder().id(id)
                .user(User.builder().login("octocat").build())
                .bodyHtml("<p>hi</p>")
                .createdAt(ago(1000L)).updatedAt(ago(1000L)).build();
        return new TimelineItem.TimelineComment(gc);
    }

    private TimelineItem.TimelineEvent eventItem() {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(IssueEventType.Reopened);
        when(e.actor()).thenReturn(User.builder().login("octocat").build());
        when(e.createdAt()).thenReturn(ago(1000L));
        return new TimelineItem.TimelineEvent(e);
    }

    private TimelineItem.TimelineReview reviewItem(Long id) {
        Review r = mock(Review.class);
        when(r.user()).thenReturn(User.builder().login("octocat").build());
        when(r.state()).thenReturn(com.meisolsson.githubsdk.model.ReviewState.Approved);
        when(r.body()).thenReturn("b");
        when(r.bodyHtml()).thenReturn("<p>b</p>");
        when(r.submittedAt()).thenReturn(ago(1000L));
        when(r.id()).thenReturn(id);
        return new TimelineItem.TimelineReview(r);
    }

    private TimelineItem.Diff diffItem(Long commentId) {
        ReviewComment rc = AdapterTestSupport.reviewComment(commentId, null, null,
                null, null, null, 1, "a.java", "@@ -1,1 +1,1 @@\n+x",
                "https://api.github.com/repos/owner/repo/pulls/5", null, null);
        return new TimelineItem.Diff(new TimelineItem.TimelineComment(rc));
    }

    private TimelineItem.Reply replyItem(Long commentId) {
        ReviewComment rc = AdapterTestSupport.reviewComment(commentId, null, null,
                null, null, null, null, null, null, null, null, null);
        return new TimelineItem.Reply(new TimelineItem.TimelineComment(rc));
    }

    private TimelineItemAdapter.TimelineItemViewHolder holderFor(TimelineItem item) {
        return adapter.onCreateViewHolder(inflater, parent, adapter.getItemViewType(item));
    }

    @Test
    public void getItemViewType_eachKind() {
        assertEquals(3, adapter.getItemViewType(commentItem(1L)));
        assertEquals(4, adapter.getItemViewType(eventItem()));
        assertEquals(5, adapter.getItemViewType(reviewItem(1L)));
        assertEquals(6, adapter.getItemViewType(diffItem(1L)));
        assertEquals(7, adapter.getItemViewType(replyItem(1L)));
        assertEquals(2, adapter.getItemViewType(mock(TimelineItem.class)));
    }

    @Test
    public void onCreateViewHolder_eachKind() {
        assertTrue(holderFor(commentItem(1L)) instanceof CommentViewHolder);
        assertTrue(holderFor(eventItem()) instanceof EventViewHolder);
        assertTrue(holderFor(reviewItem(1L)) instanceof ReviewViewHolder);
        assertTrue(holderFor(diffItem(1L)) instanceof DiffViewHolder);
        assertTrue(holderFor(replyItem(1L)) instanceof ReplyViewHolder);
    }

    @Test
    public void onCreateViewHolder_unknownType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.onCreateViewHolder(inflater, parent, 999));
    }

    @Test
    public void onBindViewHolder_comment_binds() {
        TimelineItem.TimelineComment item = commentItem(1L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(1f, h.itemView.getAlpha(), 0.001f);
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_timestamp))
                .getText().length() > 0);
    }

    @Test
    public void onBindViewHolder_event_binds() {
        TimelineItem.TimelineEvent item = eventItem();
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_message))
                .getText().length() > 0);
    }

    @Test
    public void onBindViewHolder_review_bindsAndAsksTranslatedBody() {
        TimelineItem.TimelineReview item = reviewItem(9L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(1, getTranslatedCalls.size());
        assertEquals("review:9", getTranslatedCalls.get(0));
    }

    @Test
    public void onBindViewHolder_diff_binds() {
        TimelineItem.Diff item = diffItem(3L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals("a.java",
                ((TextView) h.itemView.findViewById(R.id.tv_file)).getText().toString());
    }

    @Test
    public void onBindViewHolder_reply_binds() {
        TimelineItem.Reply item = replyItem(4L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(context.getString(R.string.reply),
                ((android.widget.Button) h.itemView.findViewById(R.id.btn_reply))
                        .getText().toString());
    }

    @Test
    public void onBindViewHolder_unknownType_noop() {
        TimelineItem item = mock(TimelineItem.class);
        TimelineItemAdapter.TimelineItemViewHolder h =
                (TimelineItemAdapter.TimelineItemViewHolder) holderFor(commentItem(1L));
        adapter.onBindViewHolder(h, item);
    }

    @Test
    public void onBindViewHolder_fadesNonSelectedWhenReplySelected() {
        selectedReplyId = 5L;
        TimelineItem.TimelineComment selected = commentItem(5L);
        TimelineItem.TimelineComment other = commentItem(6L);
        TimelineItemAdapter.TimelineItemViewHolder hs = holderFor(selected);
        TimelineItemAdapter.TimelineItemViewHolder ho = holderFor(other);
        adapter.onBindViewHolder(hs, selected);
        adapter.onBindViewHolder(ho, other);
        assertEquals(1f, hs.itemView.getAlpha(), 0.001f);
        assertEquals(0.5f, ho.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void onBindViewHolder_diffFadesWhenInitialCommentNotSelected() {
        selectedReplyId = 5L;
        TimelineItem.Diff diff = diffItem(6L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(diff);
        adapter.onBindViewHolder(h, diff);
        assertEquals(0.5f, h.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void onBindViewHolder_commentWithParentDiff_usesParentInitialId() {
        selectedReplyId = 6L;
        TimelineItem.Diff diff = diffItem(6L);
        // second comment in the same hunk gets the diff as parent
        ReviewComment rc2 = AdapterTestSupport.reviewComment(9L, null, null,
                ago(500L), null, null, 2, "a.java", null, null, "c", 1);
        TimelineItem.TimelineComment child = new TimelineItem.TimelineComment(rc2);
        diff.comments.add(child);
        AdapterTestSupport.setField(child, "diff", diff);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(child);
        adapter.onBindViewHolder(h, child);
        assertEquals(1f, h.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void getUsers_collectsNonNull() {
        adapter.add(commentItem(1L));
        adapter.add(eventItem());
        GitComment gc = GitComment.builder().id(9L)
                .user(User.builder().login("bob").build())
                .bodyHtml("<p>x</p>")
                .createdAt(ago(1000L)).updatedAt(ago(1000L)).build();
        adapter.add(new TimelineItem.TimelineComment(gc));
        // comment user + event actor are both "octocat" -> deduped to one entry
        assertEquals(2, adapter.getUsers().size());
    }

    @Test
    public void lifecycle_destroyPauseResume_noCrash() {
        adapter.resume();
        adapter.pause();
        adapter.destroy();
    }

    @Test
    public void clear_suppressFlag_skipsCacheClearOnce() {
        adapter.add(commentItem(1L));
        adapter.suppressCacheClearOnNextClear();
        adapter.clear();
        assertEquals(0, adapter.getCount());
        // next clear behaves normally
        adapter.add(commentItem(2L));
        adapter.clear();
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void addAll_resetsSuppressFlag() {
        adapter.suppressCacheClearOnNextClear();
        adapter.addAll(java.util.Collections.singletonList(commentItem(1L)));
        assertEquals(1, adapter.getCount());
        adapter.clear();
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void setLocked_disablesQuoteInCommentBind() {
        adapter.setLocked(true);
        TimelineItem.TimelineComment item = commentItem(1L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertNull(((TextView) h.itemView.findViewById(R.id.tv_desc))
                .getCustomSelectionActionModeCallback());
    }

    @Test
    public void onReactionsUpdated_updatesCommentHolder() {
        TimelineItem.TimelineComment item = commentItem(1L);
        CommentViewHolder h = (CommentViewHolder) holderFor(item);
        adapter.onBindViewHolder(h, item);
        Reactions reactions = AdapterTestSupport.reactions(1);
        adapter.onReactionsUpdated(h, reactions);
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.reactions).getVisibility());
    }

    @Test
    public void commentMenu_editDelete_share_viewInFile() {
        TimelineItem.TimelineComment item = commentItem(1L);
        CommentViewHolder h = (CommentViewHolder) holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertTrue(h.onMenuItemClick(menuItem(R.id.edit)));
        assertEquals(1, edited.size());
        assertTrue(h.onMenuItemClick(menuItem(R.id.delete)));
        assertEquals(1, deleted.size());
        // share with null htmlUrl -> true, no chooser
        assertTrue(h.onMenuItemClick(menuItem(R.id.share)));
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
        // view in file with no file -> makeDiffIntent null -> true, no activity
        assertTrue(h.onMenuItemClick(menuItem(R.id.view_in_file)));
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
        // unknown item -> false
        assertFalse(h.onMenuItemClick(menuItem(R.id.browser)));
    }

    @Test
    public void commentMenu_share_withUrl_startsChooser() {
        GitComment gc = GitComment.builder().id(2L)
                .user(User.builder().login("u").build())
                .bodyHtml("<p>x</p>")
                .htmlUrl("https://github.com/o/r/issues/5#issuecomment-2")
                .createdAt(ago(1000L)).updatedAt(ago(1000L)).build();
        TimelineItem.TimelineComment item = new TimelineItem.TimelineComment(gc);
        CommentViewHolder h = (CommentViewHolder) holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertTrue(h.onMenuItemClick(menuItem(R.id.share)));
        assertEquals(android.content.Intent.ACTION_CHOOSER,
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getAction());
    }

    @Test
    public void commentReactionDelegates_forwardToCallback() {
        TimelineItem.TimelineComment item = commentItem(1L);
        CommentViewHolder h = (CommentViewHolder) holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertTrue(h.loadReactionDetails(h, false).blockingGet().isEmpty());
        h.addReaction(h, "+1").test().assertError(IllegalStateException.class);
        assertTrue(h.deleteReaction(h, 3L).blockingGet());
    }

    @Test
    public void replyClick_selectsReply() {
        TimelineItem.Reply item = replyItem(8L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        h.itemView.findViewById(R.id.btn_reply).performClick();
        assertEquals(1, replySelected.size());
        assertEquals(Long.valueOf(8L), replySelected.get(0));
    }

    @Test
    public void reviewBind_translatedBodyDelegatesWithReviewType() {
        translatedBody = "<p>t</p>";
        TimelineItem.TimelineReview item = reviewItem(4L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals("review:4", getTranslatedCalls.get(0));
    }

    private MenuItem menuItem(int id) {
        MenuItem m = mock(MenuItem.class);
        when(m.getItemId()).thenReturn(id);
        return m;
    }

    @Test
    public void reviewCallback_quoteText_delegates() {
        ReviewViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mReviewCallback");
        cb.quoteText("hello");
        assertEquals(1, quoted.size());
        assertEquals("hello", quoted.get(0).toString());
    }

    @Test
    public void reviewCallback_translateBody_delegatesWithReviewType() {
        ReviewViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mReviewCallback");
        cb.translateBody(7L, "<p>b</p>");
        assertEquals(1, translateCalls.size());
        assertEquals("review:7", translateCalls.get(0));
    }

    @Test
    public void reviewCallback_getTranslatedBody_delegatesWithReviewType() {
        translatedBody = "<p>translated</p>";
        ReviewViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mReviewCallback");
        assertEquals("<p>translated</p>", cb.getTranslatedBody(7L));
        assertEquals(1, getTranslatedCalls.size());
        assertEquals("review:7", getTranslatedCalls.get(0));
    }

    @Test
    public void reviewCallback_canQuote_trueWhenUnlockedAndDetailsShown() {
        TimelineItemAdapter detailsAdapter = new TimelineItemAdapter(context,
                "owner", "repo", 5, false, true, callback);
        ReviewViewHolder.Callback cb =
                AdapterTestSupport.getField(detailsAdapter, "mReviewCallback");
        assertTrue(cb.canQuote());
    }

    @Test
    public void reviewCallback_canQuote_falseWhenLocked() {
        TimelineItemAdapter detailsAdapter = new TimelineItemAdapter(context,
                "owner", "repo", 5, false, true, callback);
        detailsAdapter.setLocked(true);
        ReviewViewHolder.Callback cb =
                AdapterTestSupport.getField(detailsAdapter, "mReviewCallback");
        assertFalse(cb.canQuote());
    }

    @Test
    public void commentCallback_canAddReaction_followsLock() {
        CommentViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mCommentCallback");
        assertTrue(cb.canAddReaction());
        adapter.setLocked(true);
        assertFalse(cb.canAddReaction());
    }

    @Test
    public void commentCallback_quoteTextAndAddText_delegate() {
        CommentViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mCommentCallback");
        cb.quoteText("q");
        cb.addText("a");
        assertEquals(1, quoted.size());
        assertEquals(1, added.size());
    }

    @Test
    public void commentMenu_editAndDelete_callCallback() {
        CommentViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mCommentCallback");
        TimelineItem.TimelineComment item = commentItem(1L);
        MenuItem edit = mock(MenuItem.class);
        when(edit.getItemId()).thenReturn(R.id.edit);
        assertTrue(cb.onMenItemClick(item, edit));
        assertEquals(1, edited.size());
        MenuItem del = mock(MenuItem.class);
        when(del.getItemId()).thenReturn(R.id.delete);
        assertTrue(cb.onMenItemClick(item, del));
        assertEquals(1, deleted.size());
    }

    @Test
    public void commentMenu_viewInFile_withDiffIntent_startsActivity() {
        CommentViewHolder.Callback cb = AdapterTestSupport.getField(adapter, "mCommentCallback");
        ReviewComment rc = AdapterTestSupport.reviewComment(11L,
                User.builder().login("octocat").build(), "<p>hi</p>", null, null, null, 1,
                "a.java", "@@ -1 +1 @@", "https://api.github.com/repos/owner/repo/pulls/5",
                null, null);
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@\n+new");
        TimelineItem.TimelineComment item = new TimelineItem.TimelineComment(rc, file);
        MenuItem viewInFile = mock(MenuItem.class);
        when(viewInFile.getItemId()).thenReturn(R.id.view_in_file);
        assertTrue(cb.onMenItemClick(item, viewInFile));
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.PullRequestDiffViewerActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void onBindViewHolder_diffSelected_noFade() {
        selectedReplyId = 6L;
        TimelineItem.Diff diff = diffItem(6L);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(diff);
        adapter.onBindViewHolder(h, diff);
        assertEquals(1f, h.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void onBindViewHolder_commentWithParentDiff_notSelected_fades() {
        selectedReplyId = 5L;
        TimelineItem.Diff diff = diffItem(6L);
        ReviewComment rc2 = AdapterTestSupport.reviewComment(9L, null, null,
                ago(500L), null, null, 2, "a.java", null, null, "c", 1);
        TimelineItem.TimelineComment child = new TimelineItem.TimelineComment(rc2);
        diff.comments.add(child);
        AdapterTestSupport.setField(child, "diff", diff);
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(child);
        adapter.onBindViewHolder(h, child);
        assertEquals(0.5f, h.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void onBindViewHolder_eventWithReplySelected_noFade() {
        selectedReplyId = 5L;
        TimelineItem.TimelineEvent item = eventItem();
        TimelineItemAdapter.TimelineItemViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(1f, h.itemView.getAlpha(), 0.001f);
    }

    @Test
    public void getUsers_skipsNullUser() {
        adapter.add(commentItem(1L));
        adapter.add(mock(TimelineItem.class));
        assertEquals(1, adapter.getUsers().size());
    }
}
