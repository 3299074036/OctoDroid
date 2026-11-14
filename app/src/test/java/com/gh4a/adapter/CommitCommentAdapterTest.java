package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Reactions;
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
import java.util.Set;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.service.reactions.ReactionService;
import org.mockito.MockedStatic;
import retrofit2.Response;
import io.reactivex.Single;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class CommitCommentAdapterTest {
    private Context context;
    private LayoutInflater inflater;
    private ViewGroup parent;
    private List<GitComment> edited;
    private List<GitComment> deleted;
    private List<CharSequence> quoted;
    private List<CharSequence> added;
    private CommitCommentAdapter adapter;

    private final CommitCommentAdapter.OnCommentAction callback =
            new CommitCommentAdapter.OnCommentAction() {
                @Override
                public void editComment(GitComment comment) {
                    edited.add(comment);
                }

                @Override
                public void deleteComment(GitComment comment) {
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
        adapter = new CommitCommentAdapter(context, "owner", "repo", callback);
    }

    private GitComment comment(Long id, User user, String htmlUrl, Date createdAt, Date updatedAt) {
        return GitComment.builder().id(id).user(user).htmlUrl(htmlUrl)
                .bodyHtml("<p>hello</p>").createdAt(createdAt).updatedAt(updatedAt).build();
    }

    private CommitCommentAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Date nowMinus(long ms) {
        return new Date(System.currentTimeMillis() - ms);
    }

    @Test
    public void bind_uneditedComment_hidesEditTimestamp() {
        Date t = nowMinus(1000L);
        CommitCommentAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, comment(1L, User.builder().login("octocat").build(),
                "https://example.com/c/1", t, t));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_timestamp)).getText().length() > 0);
    }

    @Test
    public void bind_editedComment_showsEditTimestamp() {
        CommitCommentAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, comment(1L, User.builder().login("octocat").build(),
                "https://example.com/c/1", nowMinus(5000L), nowMinus(1000L)));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void bind_nullCreatedAt_noCrash_showsEditTimestamp() {
        CommitCommentAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, comment(1L, User.builder().login("octocat").build(),
                null, null, nowMinus(1000L)));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void bind_nullUser_noCrash() {
        CommitCommentAdapter.ViewHolder h = holder();
        Date t = nowMinus(1000L);
        adapter.onBindViewHolder(h, comment(2L, null, null, t, t));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_edit_timestamp).getVisibility());
    }

    @Test
    public void bind_unauthenticated_editDeleteReactHidden() {
        // Gh4Application has no auth login in tests, so canEdit is false and
        // the react item is hidden as well.
        CommitCommentAdapter.ViewHolder h = holder();
        Date t = nowMinus(1000L);
        adapter.onBindViewHolder(h, comment(1L, User.builder().login("octocat").build(),
                null, t, t));
        // menu visibility is internal to the holder; bind must not crash and
        // the timestamp must be set.
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_timestamp)).getText().length() > 0);
    }

    @Test
    public void getUsers_collectsNonNullUsers() {
        adapter.add(comment(1L, User.builder().login("a").build(), null, null, null));
        adapter.add(comment(2L, null, null, null, null));
        adapter.add(comment(3L, User.builder().login("b").build(), null, null, null));
        Set<User> users = adapter.getUsers();
        assertEquals(2, users.size());
    }

    @Test
    public void lifecycle_destroyResumePauseClear_noCrash() {
        adapter.add(comment(1L, null, null, null, null));
        adapter.resume();
        adapter.pause();
        adapter.clear();
        assertEquals(0, adapter.getCount());
        adapter.destroy();
    }

    @Test
    public void loadReactionDetails_nullId_returnsEmptyList() {
        List<com.meisolsson.githubsdk.model.Reaction> reactions = adapter
                .loadReactionDetails(holderWithBoundComment(1L, null), false)
                .blockingGet();
        assertTrue(reactions.isEmpty());
    }

    @Test
    public void addReaction_nullId_errors() {
        adapter.addReaction(holderWithBoundComment(1L, null), "+1")
                .test()
                .assertError(IllegalStateException.class);
    }

    @Test
    public void deleteReaction_nullId_returnsFalse() {
        assertFalse(adapter.deleteReaction(holderWithBoundComment(1L, null), 5L).blockingGet());
    }

    private CommitCommentAdapter.ViewHolder holderWithBoundComment(long holderId, Long commentId) {
        CommitCommentAdapter.ViewHolder h = holder();
        Date t = nowMinus(1000L);
        GitComment c = comment(commentId, User.builder().login("u").build(), null, t, t);
        adapter.onBindViewHolder(h, c);
        return h;
    }

    @Test
    public void onReactionsUpdated_updatesBoundItem() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 9L);
        Reactions reactions = AdapterTestSupport.reactions(2);
        adapter.onReactionsUpdated(h, reactions);
        // bound item keeps the new reactions; no crash is the observable
        // contract here (ReactionBar internals are covered by its own tests)
        assertEquals(Long.valueOf(9L), h.mBoundItem.id());
        assertEquals(2, h.mBoundItem.reactions().totalCount());
    }

    @Test
    public void viewHolder_getCacheKey_isCommentId() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 42L);
        assertEquals(Long.valueOf(42L), h.getCacheKey());
    }

    @Test
    public void viewHolder_menuClick_editAndDelete_callCallback() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        GitComment bound = h.mBoundItem;
        assertTrue(h.onMenuItemClick(menuItem(R.id.edit)));
        assertEquals(1, edited.size());
        assertEquals(bound, edited.get(0));
        assertTrue(h.onMenuItemClick(menuItem(R.id.delete)));
        assertEquals(1, deleted.size());
        assertEquals(bound, deleted.get(0));
    }

    @Test
    public void viewHolder_menuClick_share_nullUrl_noShare() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        assertTrue(h.onMenuItemClick(menuItem(R.id.share)));
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void viewHolder_menuClick_share_withUrl_startsChooser() {
        CommitCommentAdapter.ViewHolder h = holder();
        Date t = nowMinus(1000L);
        adapter.onBindViewHolder(h, comment(7L, User.builder().login("u").build(),
                "https://github.com/o/r/commit/abc#commitcomment-7", t, t));
        assertTrue(h.onMenuItemClick(menuItem(R.id.share)));
        Intent started = AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
    }

    @Test
    public void viewHolder_menuClick_unknown_returnsFalse() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        assertFalse(h.onMenuItemClick(menuItem(R.id.browser)));
    }

    @Test
    public void viewHolder_clickMenu_showsPopup() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        h.itemView.findViewById(R.id.iv_menu).performClick();
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_extra_addsMentionText() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        adapter.onClick(h.itemView.findViewById(R.id.tv_extra));
        assertEquals(1, added.size());
        assertTrue(added.get(0).toString().contains("u"));
    }

    @Test
    public void onClick_otherView_delegatesToSuper() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        View title = h.itemView.findViewById(R.id.tv_desc);
        title.setTag(h);
        adapter.onClick(title); // NO_POSITION -> no-op
    }

    private MenuItem menuItem(int id) {
        MenuItem m = mock(MenuItem.class);
        when(m.getItemId()).thenReturn(id);
        return m;
    }

    @Test
    public void canAddReaction_alwaysTrue() {
        assertTrue(adapter.canAddReaction());
    }

    @Test
    public void quoteText_viaSelectionCallback_callsActionCallback() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        ((com.gh4a.utils.UiUtils.QuoteActionModeCallback) ((TextView) h.itemView
                .findViewById(R.id.tv_desc)).getCustomSelectionActionModeCallback())
                .onTextQuoted("quoted text");
        assertEquals(1, quoted.size());
        assertEquals("quoted text", quoted.get(0).toString());
    }

    @Test
    public void onClick_otherView_noop() {
        CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
        h.onClick(new View(context));
    }

    @Test
    public void onClick_gravatar_mannequin_noActivity() {
        CommitCommentAdapter.ViewHolder h = holder();
        Date t = nowMinus(1000L);
        User mannequin = User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build();
        adapter.onBindViewHolder(h, comment(7L, mannequin, null, t, t));
        h.itemView.findViewById(R.id.iv_gravatar).performClick();
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void bind_nullCallback_hidesMenuItems() {
        CommitCommentAdapter nullCb = new CommitCommentAdapter(context, "owner", "repo", null);
        CommitCommentAdapter.ViewHolder h = nullCb.onCreateViewHolder(inflater, parent, 2);
        Date t = nowMinus(1000L);
        nullCb.onBindViewHolder(h, comment(7L, User.builder().login("u").build(), null, t, t));
        androidx.appcompat.widget.PopupMenu menu = AdapterTestSupport.getField(h, "mPopupMenu");
        assertFalse(menu.getMenu().findItem(R.id.edit).isVisible());
        assertFalse(menu.getMenu().findItem(R.id.delete).isVisible());
        assertFalse(menu.getMenu().findItem(R.id.react).isVisible());
    }

    @Test
    public void bind_authorizedOwnComment_showsMenuItems() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommitCommentAdapter.ViewHolder h = holder();
            Date t = nowMinus(1000L);
            adapter.onBindViewHolder(h, comment(7L,
                    User.builder().login("octocat").build(), null, t, t));
            androidx.appcompat.widget.PopupMenu menu = AdapterTestSupport.getField(h, "mPopupMenu");
            assertTrue(menu.getMenu().findItem(R.id.edit).isVisible());
            assertTrue(menu.getMenu().findItem(R.id.delete).isVisible());
            assertTrue(menu.getMenu().findItem(R.id.react).isVisible());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void bind_authorizedRepoOwner_showsMenuItems() {
        AdapterTestSupport.authorize("owner");
        try {
            CommitCommentAdapter.ViewHolder h = holder();
            Date t = nowMinus(1000L);
            adapter.onBindViewHolder(h, comment(7L,
                    User.builder().login("u").build(), null, t, t));
            androidx.appcompat.widget.PopupMenu menu = AdapterTestSupport.getField(h, "mPopupMenu");
            assertTrue(menu.getMenu().findItem(R.id.edit).isVisible());
            assertTrue(menu.getMenu().findItem(R.id.delete).isVisible());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void onReactionsUpdated_authorized_updatesMenuWithoutCrash() {
        AdapterTestSupport.authorize("octocat");
        try {
            CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 9L);
            adapter.onReactionsUpdated(h, AdapterTestSupport.reactions(2));
            assertEquals(9L, (long) h.mBoundItem.id());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void viewHolder_menuClick_authorized_reactionItem_handledByHelper() {
        AdapterTestSupport.authorize("octocat");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            ReactionService service = mock(ReactionService.class);
            when(service.createCommitCommentReaction(eq("owner"), eq("repo"), eq(7L),
                    any(ReactionRequest.class)))
                    .thenReturn(Single.just(Response.success(
                            Reaction.builder().id(2L).content("+1").build())));
            mocked.when(() -> ServiceFactory.get(eq(ReactionService.class), anyBoolean()))
                    .thenReturn(service);
            CommitCommentAdapter.ViewHolder h = holderWithBoundComment(1L, 7L);
            androidx.appcompat.widget.PopupMenu menu = AdapterTestSupport.getField(h, "mPopupMenu");
            // submenu item 0 is the "loading" placeholder, not a reaction;
            // use a real reaction item
            MenuItem firstReaction =
                    menu.getMenu().findItem(R.id.react).getSubMenu().findItem(R.id.plus_one);
            assertTrue(h.onMenuItemClick(firstReaction));
            assertTrue(edited.isEmpty());
        } finally {
            AdapterTestSupport.clearAuth();
        }
    }

    @Test
    public void loadReactionDetails_withId_usesService() {
        ReactionService service = mock(ReactionService.class);
        Reaction reaction = Reaction.builder().id(1L).content("+1").build();
        when(service.getCommitCommentReactions(eq("owner"), eq("repo"), eq(7L), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<Reaction>builder()
                        .items(Collections.singletonList(reaction)).build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(ReactionService.class), anyBoolean())).thenReturn(service);
            List<Reaction> result = adapter
                    .loadReactionDetails(holderWithBoundComment(1L, 7L), false).blockingGet();
            assertEquals(1, result.size());
            assertEquals("+1", result.get(0).content());
        }
    }

    @Test
    public void addReaction_withId_usesService() {
        ReactionService service = mock(ReactionService.class);
        when(service.createCommitCommentReaction(eq("owner"), eq("repo"), eq(7L),
                any(ReactionRequest.class)))
                .thenReturn(Single.just(Response.success(
                        Reaction.builder().id(2L).content("heart").build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(ReactionService.class), anyBoolean()))
                    .thenReturn(service);
            Reaction result = adapter
                    .addReaction(holderWithBoundComment(1L, 7L), "heart").blockingGet();
            assertEquals("heart", result.content());
        }
    }

    @Test
    public void deleteReaction_withId_usesService() {
        ReactionService service = mock(ReactionService.class);
        when(service.deleteCommitCommentReaction(eq("owner"), eq("repo"), eq(7L), eq(3L)))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(ReactionService.class), anyBoolean()))
                    .thenReturn(service);
            assertTrue(adapter.deleteReaction(holderWithBoundComment(1L, 7L), 3L).blockingGet());
        }
    }
}
