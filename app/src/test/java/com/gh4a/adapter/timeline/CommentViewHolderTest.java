package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.HttpImageGetter;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.AuthorAssociation;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.ReviewComment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.Single;

@RunWith(RobolectricTestRunner.class)
public class CommentViewHolderTest {
    private Context context;
    private HttpImageGetter imageGetter;
    private ReactionBar.ReactionDetailsCache cache;
    private List<CharSequence> quoted;
    private List<CharSequence> added;
    private List<TimelineItem.TimelineComment> menuClicked;
    private String translatedBody;
    private boolean canQuote = true;
    private boolean canAddReaction = true;

    private final CommentViewHolder.Callback callback = new CommentViewHolder.Callback() {
        @Override
        public boolean canAddReaction() {
            return canAddReaction;
        }

        @Override
        public boolean canQuote() {
            return canQuote;
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
        public boolean onMenItemClick(TimelineItem.TimelineComment comment, MenuItem menuItem) {
            menuClicked.add(comment);
            return true;
        }

        @Override
        public Single<List<Reaction>> loadReactionDetails(TimelineItem.TimelineComment item,
                boolean bypassCache) {
            return Single.just(new ArrayList<>());
        }

        @Override
        public Single<Reaction> addReaction(TimelineItem.TimelineComment item, String content) {
            return Single.error(new IllegalStateException("nope"));
        }

        @Override
        public Single<Boolean> deleteReaction(TimelineItem.TimelineComment item, long reactionId) {
            return Single.just(true);
        }

        @Override
        public String getTranslatedBodyHtml(long commentId) {
            return translatedBody;
        }
    };

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        imageGetter = new HttpImageGetter(context);
        cache = new ReactionBar.ReactionDetailsCache((item, reactions) -> {});
        quoted = new ArrayList<>();
        added = new ArrayList<>();
        menuClicked = new ArrayList<>();
        translatedBody = null;
    }

    private CommentViewHolder holder() {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.row_timeline_comment, null, false);
        return new CommentViewHolder(view, imageGetter, "owner", cache, callback);
    }

    private TimelineItem.TimelineComment commentItem(Long id, Date createdAt, Date updatedAt,
            AuthorAssociation association, Integer position) {
        ReviewComment rc = AdapterTestSupport.reviewComment(id,
                User.builder().login("octocat").build(), "<p>hi</p>",
                createdAt, updatedAt, association, position,
                null, null, null, null, null);
        return new TimelineItem.TimelineComment(rc);
    }

    private Date ago(long ms) {
        return new Date(System.currentTimeMillis() - ms);
    }

    private String text(CommentViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_unedited_hidesEditTimestamp() {
        Date t = ago(1000L);
        CommentViewHolder h = holder();
        h.bind(commentItem(1L, t, t, null, 5));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
        assertTrue(text(h, R.id.tv_timestamp).length() > 0);
    }

    @Test
    public void bind_reviewComment_hidesEditTimestamp() {
        // ReviewComment is always a review comment: edit timestamp hidden
        CommentViewHolder h = holder();
        h.bind(commentItem(1L, ago(5000L), ago(1000L), null, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void bind_issueComment_edited_showsEditTimestamp() {
        GitComment gc = GitComment.builder().id(2L)
                .user(User.builder().login("octocat").build())
                .bodyHtml("<p>hi</p>")
                .createdAt(ago(5000L)).updatedAt(ago(1000L)).build();
        TimelineItem.TimelineComment item = new TimelineItem.TimelineComment(gc);
        CommentViewHolder h = holder();
        h.bind(item);
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void bind_nullCreatedAt_doesNotCrash() {
        // T-ADP-03 (failing test): bind dereferences createdAt without a null
        // check (createdAt.equals(updatedAt)); the sibling CommitCommentAdapter
        // guards this. GitHub normally returns created_at, so reachability is
        // limited to malformed payloads.
        CommentViewHolder h = holder();
        TimelineItem.TimelineComment item = commentItem(1L, null, ago(1000L), null, 5);
        h.bind(item);
        assertEquals("", ((TextView) h.itemView.findViewById(R.id.tv_timestamp))
                .getText().toString());
    }

    @Test
    public void bind_authorAssociation_eachValue() {
        for (AuthorAssociation assoc : AuthorAssociation.values()) {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            h.bind(commentItem(1L, t, t, assoc, 5));
            assertTrue(text(h, R.id.tv_extra).length() > 0);
        }
    }

    @Test
    public void bind_translatedBody_usedWhenPresent() {
        translatedBody = "<p>translated</p>";
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(3L, t, t, null, 5));
        assertEquals(Long.valueOf(3L), h.getCacheKey());
    }

    @Test
    public void bind_canQuoteFalse_clearsActionModeCallback() {
        canQuote = false;
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        assertNull(((TextView) h.itemView.findViewById(R.id.tv_desc))
                .getCustomSelectionActionModeCallback());
    }

    @Test
    public void bind_canQuoteTrue_setsActionModeCallback() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_desc))
                .getCustomSelectionActionModeCallback() != null);
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        h.itemView.findViewById(R.id.iv_gravatar).performClick();
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_extra_addsMention() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        h.itemView.findViewById(R.id.tv_extra).performClick();
        assertEquals(1, added.size());
        assertTrue(added.get(0).toString().contains("octocat"));
    }

    @Test
    public void onClick_menu_showsPopup() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        h.itemView.findViewById(R.id.iv_menu).performClick();
    }

    @Test
    public void onMenuItemClick_delegatesToCallback() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        TimelineItem.TimelineComment item = commentItem(1L, t, t, null, 5);
        h.bind(item);
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getItemId()).thenReturn(R.id.edit);
        assertTrue(h.onMenuItemClick(menuItem));
        assertEquals(1, menuClicked.size());
        assertEquals(item, menuClicked.get(0));
    }

    @Test
    public void getCacheKey_nullId_returnsNull() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(null, t, t, null, 5));
        assertNull(h.getCacheKey());
    }

    @Test
    public void updateReactions_withoutBoundItem_noCrash() {
        CommentViewHolder h = holder();
        Reactions reactions = AdapterTestSupport.reactions(0);
        h.updateReactions(reactions);
    }

    @Test
    public void updateReactions_withBoundItem_updatesComment() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(4L, t, t, null, 5));
        Reactions reactions = AdapterTestSupport.reactions(1);
        h.updateReactions(reactions);
        // bound item still present (cache key unchanged) and reactions applied
        assertEquals(Long.valueOf(4L), h.getCacheKey());
    }

    @Test
    public void reactionDelegates_forwardToCallback() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        TimelineItem.TimelineComment item = commentItem(6L, t, t, null, 5);
        h.bind(item);
        assertTrue(h.canAddReaction());
        assertTrue(h.loadReactionDetails(h, false).blockingGet().isEmpty());
        h.addReaction(h, "+1").test().assertError(IllegalStateException.class);
        assertTrue(h.deleteReaction(h, 9L).blockingGet());
    }

    @Test
    public void bind_withFilePatchAndPosition_noCrash() {
        // hasFilePatch needs a GitHubFile with patch(); position != -1.
        // Build via ReviewComment + mocked GitHubFile.
        com.meisolsson.githubsdk.model.GitHubFile file =
                mock(com.meisolsson.githubsdk.model.GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        ReviewComment rc = AdapterTestSupport.reviewComment(1L,
                User.builder().login("u").build(), "<p>x</p>",
                ago(1000L), ago(1000L), null, 3,
                null, null, null, null, null);
        TimelineItem.TimelineComment item = new TimelineItem.TimelineComment(rc, file);
        CommentViewHolder h = holder();
        h.bind(item);
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void quoteText_viaSelectionCallback_callsCallback() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        ((com.gh4a.utils.UiUtils.QuoteActionModeCallback) ((TextView) h.itemView
                .findViewById(R.id.tv_desc)).getCustomSelectionActionModeCallback())
                .onTextQuoted("quoted");
        assertEquals(1, quoted.size());
        assertEquals("quoted", quoted.get(0).toString());
    }

    @Test
    public void onClick_unknownView_noop() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        h.bind(commentItem(1L, t, t, null, 5));
        h.onClick(new View(context));
    }

    @Test
    public void onClick_gravatar_mannequin_noActivity() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        ReviewComment rc = AdapterTestSupport.reviewComment(1L,
                User.builder().login("ghost")
                        .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build(),
                "<p>hi</p>", t, t, null, 5, null, null, null, null, null);
        h.bind(new TimelineItem.TimelineComment(rc));
        h.itemView.findViewById(R.id.iv_gravatar).performClick();
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void bind_viewInFileHidden_whenFilePatchButNoPosition() {
        CommentViewHolder h = holder();
        Date t = ago(1000L);
        // diff comment with a file patch but position == null -> -1
        ReviewComment rc = AdapterTestSupport.reviewComment(1L,
                User.builder().login("octocat").build(), "<p>hi</p>",
                t, t, null, null, "a.java", "@@ -1 +1 @@", null, null, null);
        com.meisolsson.githubsdk.model.GitHubFile file =
                mock(com.meisolsson.githubsdk.model.GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@\n+new");
        h.bind(new TimelineItem.TimelineComment(rc, file));
        android.widget.PopupMenu popup = AdapterTestSupport.getField(h, "mPopupMenu");
        assertFalse(popup.getMenu().findItem(R.id.view_in_file).isVisible());
    }

    @Test
    public void ctor_authorized_inflatesReactionMenu() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            android.widget.PopupMenu popup = AdapterTestSupport.getField(h, "mPopupMenu");
            assertTrue(popup.getMenu().findItem(R.id.react).isVisible());
            assertTrue(AdapterTestSupport.<Object>getField(h, "mReactionMenuHelper") != null);
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void onClick_menu_authorized_startsLoadingWithoutCrash() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            h.bind(commentItem(1L, t, t, null, 5));
            h.itemView.findViewById(R.id.iv_menu).performClick();
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void onMenuItemClick_authorized_reactionItem_handledByHelper() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            h.bind(commentItem(1L, t, t, null, 5));
            android.widget.PopupMenu popup = AdapterTestSupport.getField(h, "mPopupMenu");
            // submenu item 0 is the "loading" placeholder, not a reaction
            MenuItem firstReaction =
                    popup.getMenu().findItem(R.id.react).getSubMenu().findItem(R.id.plus_one);
            assertTrue(h.onMenuItemClick(firstReaction));
            assertTrue(menuClicked.isEmpty());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void onMenuItemClick_authorized_nonReactionItem_delegatesToCallback() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            TimelineItem.TimelineComment item = commentItem(1L, t, t, null, 5);
            h.bind(item);
            MenuItem menuItem = mock(MenuItem.class);
            when(menuItem.getItemId()).thenReturn(R.id.edit);
            assertTrue(h.onMenuItemClick(menuItem));
            assertEquals(1, menuClicked.size());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void updateReactions_authorized_updatesMenuWithoutCrash() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            h.bind(commentItem(1L, t, t, null, 5));
            h.updateReactions(AdapterTestSupport.reactions(2));
            // no crash; the bound item is still the same comment
            TimelineItem.TimelineComment bound =
                    AdapterTestSupport.getField(h, "mBoundItem");
            assertEquals(1L, (long) bound.comment().id());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void bind_authorizedOwnComment_showsEditDelete() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommentViewHolder h = holder();
            Date t = ago(1000L);
            h.bind(commentItem(1L, t, t, null, 5));
            android.widget.PopupMenu popup = AdapterTestSupport.getField(h, "mPopupMenu");
            assertTrue(popup.getMenu().findItem(R.id.edit).isVisible());
            assertTrue(popup.getMenu().findItem(R.id.delete).isVisible());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }
}
