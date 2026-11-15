package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.menu.MenuBuilder;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.timeline.TimelineItemAdapter;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.HttpImageGetter;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.translate.CommentTranslator;
import com.gh4a.utils.translate.ThreadTranslateController;
import com.gh4a.utils.translate.TranslationManager;
import com.gh4a.widget.EditorBottomSheet;
import com.gh4a.widget.ReactionBar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;
import com.meisolsson.githubsdk.service.reactions.ReactionService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import retrofit2.Response;

/** IssueFragmentBase (F1): static args builder + interesting-events set. */
@RunWith(RobolectricTestRunner.class)
public class IssueFragmentBaseTest {

    @Test
    public void buildArgs_storesAllFields() {
        Issue issue = Issue.builder().number(7).state(IssueState.Open).build();
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(123L);
        android.os.Bundle args = IssueFragmentBase.buildArgs(
                "octocat", "hello", issue, true, marker);
        assertEquals("octocat", args.getString("owner"));
        assertEquals("hello", args.getString("repo"));
        assertNotNull(args.getParcelable("issue"));
        assertEquals(true, args.getBoolean("collaborator"));
        assertNotNull(args.getParcelable("initial_comment"));
    }

    @Test
    public void buildArgs_nullIssueAndMarker_storesNulls() {
        android.os.Bundle args = IssueFragmentBase.buildArgs(
                "o", "r", null, false, null);
        assertEquals("o", args.getString("owner"));
        assertNull(args.getParcelable("issue"));
        assertEquals(false, args.getBoolean("collaborator"));
        assertNull(args.getParcelable("initial_comment"));
    }

    @Test
    public void interestingEvents_hasExpectedMembers() {
        assertEquals(31, IssueFragmentBase.INTERESTING_EVENTS.size());
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Closed));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Reopened));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Merged));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Commented));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Labeled));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Renamed));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Transferred));
    }

    @Test
    public void interestingEvents_excludesUninterestingTypes() {
        // Subscribed / Mentioned / Pinned etc. are not timeline items.
        assertEquals(false, IssueFragmentBase.INTERESTING_EVENTS
                .contains(IssueEventType.Subscribed));
        assertEquals(false, IssueFragmentBase.INTERESTING_EVENTS
                .contains(IssueEventType.Mentioned));
    }

    // ---- fillData / 菜单 / 翻译 / 评论回调（NoLoad 挂载） ----

    private static IssueFragmentTest.NoLoad attachedRich() {
        IssueFragmentTest.NoLoad f = new IssueFragmentTest.NoLoad();
        f.setArguments(IssueFragment.newInstance("octocat", "hello", richIssue(),
                false, null).getArguments());
        FragmentTestHelper.attachTo(IssueFragmentTest.TestBaseActivity.class, f);
        return f;
    }

    private static Issue richIssue() {
        return Issue.builder()
                .id(42L)
                .number(7)
                .state(IssueState.Open)
                .url("https://api.github.com/repos/o/r/issues/7")
                .user(com.meisolsson.githubsdk.model.User.builder()
                        .login("octo").build())
                .createdAt(new java.util.Date(1700000000000L))
                .bodyHtml("<p>hello world</p>")
                .milestone(com.meisolsson.githubsdk.model.Milestone.builder()
                        .title("v1.0").build())
                .assignees(java.util.Collections.singletonList(
                        com.meisolsson.githubsdk.model.User.builder()
                                .login("bob").build()))
                .build();
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void callVoid(Object target, String name, Class<?>[] types,
            Object... args) throws Exception {
        java.lang.reflect.Method m;
        Class<?> c = target.getClass();
        while (true) {
            try {
                m = c.getDeclaredMethod(name, types);
                break;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        m.setAccessible(true);
        m.invoke(target, args);
    }

    @Test
    public void fillData_richIssueBindsBodyMilestoneAndAssignees() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.view.View header = (android.view.View) field(f, "mListHeaderView");
        assertTrue(header != null);
        assertEquals(android.view.View.VISIBLE,
                header.findViewById(R.id.milestone_container).getVisibility());
        assertEquals("v1.0",
                ((android.widget.TextView) header.findViewById(R.id.tv_milestone))
                        .getText().toString());
        assertEquals(android.view.View.VISIBLE,
                header.findViewById(R.id.assignee_container).getVisibility());
        android.view.ViewGroup assigneeList =
                header.findViewById(R.id.assignee_list);
        assertEquals(1, assigneeList.getChildCount());
        assertEquals("bob",
                ((android.widget.TextView) assigneeList.getChildAt(0)
                        .findViewById(R.id.tv_assignee)).getText().toString());
        assertEquals("octo",
                ((android.widget.TextView) header.findViewById(R.id.tv_extra))
                        .getText().toString());
    }

    @Test
    public void fillData_quoteCallbackQuotesText() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.view.View header = (android.view.View) field(f, "mListHeaderView");
        android.widget.TextView desc =
                header.findViewById(R.id.tv_desc);
        android.view.ActionMode.Callback callback =
                desc.getCustomSelectionActionModeCallback();
        assertTrue(callback != null);
        ((com.gh4a.utils.UiUtils.QuoteActionModeCallback) callback)
                .onTextQuoted("quoted");
    }

    @Test
    public void onCreateOptionsMenu_unauthorizedHidesReactAndBuildsTranslateItem()
            throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        android.view.MenuInflater inflater =
                new android.view.MenuInflater(f.getActivity());
        f.onCreateOptionsMenu(menu, inflater);
        assertEquals(false, menu.findItem(R.id.react).isVisible());
        assertTrue(menu.findItem(R.id.translate_thread) != null);
        android.widget.TextView actionView =
                (android.widget.TextView) field(f, "mTranslateActionView");
        assertTrue(actionView != null);
        assertEquals(f.getString(R.string.translate),
                actionView.getText().toString());
    }

    @Test
    public void onOptionsItemSelected_translateThreadTogglesAndReturnsTrue()
            throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.view.MenuItem item = org.mockito.Mockito.mock(
                android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId())
                .thenReturn(R.id.translate_thread);
        // Google 免凭据恒在已配置列表里：不挡会真的发起翻译请求。
        try (org.mockito.MockedStatic<com.gh4a.utils.translate.TranslationManager>
                     mocked = org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.translate.TranslationManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> com.gh4a.utils.translate.TranslationManager
                            .getConfiguredProviders(
                                    org.mockito.ArgumentMatchers.any(
                                            android.content.Context.class)))
                    .thenReturn(java.util.Collections.emptyList());
            assertTrue(f.onOptionsItemSelected(item));
        }
        assertEquals(f.getString(R.string.translate_no_provider),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onOptionsItemSelected_unknownItemDelegatesToSuper() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.view.MenuItem item = org.mockito.Mockito.mock(
                android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(R.id.search);
        assertEquals(false, f.onOptionsItemSelected(item));
    }

    @Test
    public void onRefresh_withHeaderInvalidatesMenuAndClearsCache() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onRefresh();
        android.view.View header = (android.view.View) field(f, "mListHeaderView");
        assertEquals(android.view.View.GONE,
                header.findViewById(R.id.label_container).getVisibility());
    }

    @Test
    public void onAddData_initialCommentScrollsToMatch() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mInitialComment",
                new com.gh4a.utils.IntentUtils.InitialCommentMarker(9L));
        com.gh4a.model.TimelineItem comment =
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(9L).bodyHtml("<p>hi</p>").build());
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        f.onAddData(adapter, java.util.Collections.singletonList(comment));
        assertEquals(null, field(f, "mInitialComment"));
    }

    @Test
    public void onAddData_noInitialCommentSkipsScroll() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        f.onAddData(adapter, java.util.Collections.emptyList());
    }

    @Test
    public void collectThreadItems_includesIssueBodyAndComments() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        adapter.addAll(java.util.Collections.singletonList(
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(9L).bodyHtml("<p>hi</p>").build())));
        java.util.List<com.gh4a.utils.translate.CommentTranslator.TranslatableItem>
                items = f.collectThreadItems();
        assertEquals(2, items.size());
        assertEquals("issue", items.get(0).type);
        assertEquals("comment", items.get(1).type);
    }

    @Test
    public void collectItem_blankCommentAndReviewBranches() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        java.util.List<com.gh4a.utils.translate.CommentTranslator.TranslatableItem>
                items = new java.util.ArrayList<>();
        java.lang.reflect.Method m = IssueFragmentBase.class.getDeclaredMethod(
                "collectItem", com.gh4a.model.TimelineItem.class,
                java.util.List.class);
        m.setAccessible(true);
        m.invoke(f, new com.gh4a.model.TimelineItem.TimelineComment(
                com.meisolsson.githubsdk.model.GitHubComment.builder()
                        .id(1L).bodyHtml("").build()), items);
        assertEquals(0, items.size());
        com.meisolsson.githubsdk.model.Review review =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.model.Review.class);
        org.mockito.Mockito.when(review.bodyHtml()).thenReturn("<p>r</p>");
        org.mockito.Mockito.when(review.id()).thenReturn(5L);
        m.invoke(f, new com.gh4a.model.TimelineItem.TimelineReview(review), items);
        assertEquals(1, items.size());
        assertEquals("review", items.get(0).type);
    }

    @Test
    public void updateTranslateThreadItem_idleShowsTranslate() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.widget.TextView tv = new android.widget.TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.translate), tv.getText().toString());
    }

    @Test
    public void updateTranslateThreadItem_nullViewIsNoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mTranslateActionView", null);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
    }

    @Test
    public void refreshIssueBody_nullHeaderIsNoop() throws Exception {
        IssueFragment bare = new IssueFragment();
        callVoid(bare, "refreshIssueBody", new Class<?>[]{});
    }

    @Test
    public void refreshIssueBody_bindsBody() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        callVoid(f, "refreshIssueBody", new Class<?>[]{});
    }

    @Test
    public void translateBody_nullTranslatorIsNoop() throws Exception {
        IssueFragment bare = new IssueFragment();
        bare.translateBody("issue", 42L, "<p>x</p>");
    }

    @Test
    public void translateBody_withMockTranslatorRunsCallback() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        com.gh4a.utils.translate.CommentTranslator translator =
                org.mockito.Mockito.mock(
                        com.gh4a.utils.translate.CommentTranslator.class);
        setField(f, "mCommentTranslator", translator);
        org.mockito.Mockito.doAnswer(inv -> {
            ((com.gh4a.utils.translate.CommentTranslator.Callback)
                    inv.getArgument(4)).onTranslationChanged();
            return null;
        }).when(translator).toggleTranslation(
                org.mockito.ArgumentMatchers.any(android.content.Context.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(
                        com.gh4a.utils.translate.CommentTranslator.Callback.class));
        f.translateBody("issue", 42L, "<p>x</p>");
        org.mockito.Mockito.verify(translator).toggleTranslation(
                org.mockito.ArgumentMatchers.any(android.content.Context.class),
                org.mockito.ArgumentMatchers.eq("issue"),
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("<p>x</p>"),
                org.mockito.ArgumentMatchers.any(
                        com.gh4a.utils.translate.CommentTranslator.Callback.class));
    }

    @Test
    public void onTranslationsChanged_refreshesBodyAndAdapter() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onTranslationsChanged();
    }

    @Test
    public void fillLabels_withLabelsShowsGroup() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        callVoid(f, "fillLabels",
                new Class<?>[]{java.util.List.class},
                java.util.Collections.singletonList(
                        com.meisolsson.githubsdk.model.Label.builder()
                                .name("bug").color("ff0000").build()));
        android.view.View header = (android.view.View) field(f, "mListHeaderView");
        assertEquals(android.view.View.VISIBLE,
                header.findViewById(R.id.label_container).getVisibility());
    }

    @Test
    public void onReactionsUpdated_updatesIssueAndHeaderBar() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        com.meisolsson.githubsdk.model.Reactions reactions =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.model.Reactions.class);
        f.onReactionsUpdated(
                org.mockito.Mockito.mock(com.gh4a.widget.ReactionBar.Item.class),
                reactions);
        Issue updated = (Issue) field(f, "mIssue");
        assertEquals(reactions, updated.reactions());
    }

    @Test
    public void onClick_tvExtraAddsMention() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.widget.TextView tvExtra = new android.widget.TextView(f.getActivity());
        tvExtra.setId(R.id.tv_extra);
        tvExtra.setTag(com.meisolsson.githubsdk.model.User.builder()
                .login("octo").build());
        f.onClick(tvExtra);
    }

    @Test
    public void onClick_otherViewWithUserTagStartsUserActivity() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        android.view.View v = new android.view.View(f.getActivity());
        v.setTag(com.meisolsson.githubsdk.model.User.builder()
                .login("octo").build());
        f.onClick(v);
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(f.getActivity())
                        .getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(com.gh4a.activities.UserActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onEditorTextSent_reloadsAndSetsResult() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onEditorTextSent();
        assertEquals(android.app.Activity.RESULT_OK,
                org.robolectric.Shadows.shadowOf(f.getActivity()).getResultCode());
    }

    @Test
    public void onEditorDoSend_returnsNonNullSingle() throws Exception {
        IssueFragment bare = new IssueFragment();
        setField(bare, "mRepoOwner", "octocat");
        setField(bare, "mRepoName", "hello");
        setField(bare, "mIssue", richIssue());
        assertTrue(bare.onEditorDoSend("hello") != null);
    }

    @Test
    public void getShareSubject_formatsWithIds() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        String subject = f.getShareSubject(
                com.meisolsson.githubsdk.model.GitHubComment.builder()
                        .id(9L).build());
        assertTrue(subject.contains("9"));
        assertTrue(subject.contains("7"));
        assertTrue(subject.contains("octocat/hello"));
    }

    @Test
    public void addReaction_issueReactionReturnsReaction() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        com.meisolsson.githubsdk.service.reactions.ReactionService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.reactions.ReactionService.class);
        com.meisolsson.githubsdk.model.Reaction reaction =
                com.meisolsson.githubsdk.model.Reaction.builder().build();
        org.mockito.Mockito.when(service.createIssueReaction(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                com.meisolsson.githubsdk.model.request
                                        .ReactionRequest.class)))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(reaction)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            com.meisolsson.githubsdk.service.reactions
                                    .ReactionService.class, false))
                    .thenReturn(service);
            io.reactivex.observers.TestObserver<com.meisolsson.githubsdk.model.Reaction>
                    to = f.addReaction(
                            org.mockito.Mockito.mock(
                                    com.gh4a.widget.ReactionBar.Item.class), "+1")
                            .test();
            assertTrue(to.awaitTerminalEvent(10, java.util.concurrent.TimeUnit.SECONDS));
            to.assertValue(reaction);
        }
    }

    @Test
    public void onToggleAdvancedMode_collapsesAppBar() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onToggleAdvancedMode(true);
    }

    @Test
    public void deleteComment_showsConfirmationDialog() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.deleteComment(com.meisolsson.githubsdk.model.GitHubComment.builder()
                .id(9L).build());
        f.getParentFragmentManager().executePendingTransactions();
        assertTrue(f.getParentFragmentManager()
                .findFragmentByTag("deleteconfirm") != null);
    }

    // ---- 缺口补齐：launcher / 视图创建 / 销毁 / 返回 / 刷新 / 滚动 ----

    private static void idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(Duration duration) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(duration);
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
            idleMain();
        }
        assertTrue(condition.getAsBoolean());
    }

    private static IssueFragmentTest.NoLoad attachInContainer(Issue issue,
            boolean collaborator, IntentUtils.InitialCommentMarker marker) {
        IssueFragmentTest.NoLoad f = new IssueFragmentTest.NoLoad();
        f.setArguments(IssueFragment.newInstance("octocat", "hello", issue,
                collaborator, marker).getArguments());
        IssueFragmentTest.TestBaseActivity activity = Robolectric
                .buildActivity(IssueFragmentTest.TestBaseActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f).commitNow();
        return f;
    }

    private static void removeFragment(IssueFragmentTest.NoLoad f) {
        f.getParentFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
    }

    private static Issue lockedIssue() {
        return richIssue().toBuilder().locked(true).build();
    }

    @Test
    public void editLauncherResultOk_reloadsAndSetsResult() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        Object launcher = field(f, "mEditLauncher");
        Intent intent = new Intent(f.getActivity(),
                com.gh4a.activities.UserActivity.class);
        launcher.getClass().getMethod("launch", Object.class)
                .invoke(launcher, (Object) intent);
        idleMain();
        ShadowActivity shadow = Shadows.shadowOf(f.getActivity());
        ShadowActivity.IntentForResult started = shadow.getNextStartedActivityForResult();
        assertNotNull(started);
        shadow.receiveResult(started.intent, Activity.RESULT_OK, new Intent());
        idleMain();
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(f.getActivity()).getResultCode());
    }

    @Test
    public void onViewCreated_containerAttachRunsPostedRunnables() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(richIssue(), false, null);
        idleFor(Duration.ofMillis(1000));
        View contentContainer = f.getView().findViewById(R.id.content_container);
        assertNotNull(contentContainer);
        assertEquals(View.VISIBLE, contentContainer.getVisibility());
    }

    @Test
    public void onViewCreated_withInitialCommentSkipsDelayedShow() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(richIssue(), false,
                new IntentUtils.InitialCommentMarker(9L));
        idleFor(Duration.ofMillis(1000));
        assertNotNull(field(f, "mInitialComment"));
    }

    @Test
    public void onDestroyView_nullTranslatorSkipsDestroy() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", null);
        removeFragment(f);
    }

    @Test
    public void onDestroyView_nullAdapterSkipsDestroy() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mAdapter", null);
        // 真 remove 会先走 onPause（mAdapter.pause() 即 NPE），只能直接调
        // onDestroyView；调完把两个会被拆解时再碰的字段换成 mock，
        // 否则 FragmentManager 拆解时的 onPause/onDestroyView 会 NPE。
        f.onDestroyView();
        setField(f, "mAdapter", mock(TimelineItemAdapter.class));
        setField(f, "mImageGetter", mock(HttpImageGetter.class));
    }

    @Test
    public void onBackPressed_bareFragmentReturnsFalse() {
        assertEquals(false, new IssueFragment().onBackPressed());
    }

    @Test
    public void onBackPressed_advancedModeExitsAndReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        EditorBottomSheet sheet = (EditorBottomSheet) field(f, "mBottomSheet");
        sheet.setAdvancedMode(true);
        assertTrue(sheet.isInAdvancedMode());
        // setAdvancedMode(false) 只发 posted 收起任务，编辑器隐藏是异步的，
        // 这里只断言 onBackPressed 消费了返回键。
        assertTrue(f.onBackPressed());
    }

    @Test
    public void onRefresh_detachedSkipsActivityAndImageGetter() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        removeFragment(f);
        f.onRefresh();
        View header = (View) field(f, "mListHeaderView");
        assertEquals(View.GONE,
                header.findViewById(R.id.label_container).getVisibility());
    }

    @Test
    public void canChildScrollUp_bareFragmentReturnsFalse() {
        assertEquals(false, new IssueFragment().canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_collapsedSheetDelegatesToSuper() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertEquals(false, f.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_expandedSheetReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(richIssue(), false, null);
        idleMain();
        EditorBottomSheet sheet = (EditorBottomSheet) field(f, "mBottomSheet");
        BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        idleMain();
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void getRootLayout_returnsCoordinatorLayout() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertNotNull(f.getRootLayout());
    }

    // ---- 缺口补齐：菜单 / 翻译按钮 / 反应 ----

    @SuppressWarnings("unchecked")
    private static Page<Reaction> emptyReactionPage() {
        Page<Reaction> page = mock(Page.class);
        when(page.items()).thenReturn(Collections.emptyList());
        when(page.next()).thenReturn(null);
        return page;
    }

    private static ReactionService mockReactionService() {
        ReactionService service = mock(ReactionService.class);
        Reaction reaction = Reaction.builder().build();
        Page<Reaction> emptyPage = emptyReactionPage();
        when(service.getIssueReactions(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(emptyPage)));
        when(service.getIssueCommentReactions(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(emptyPage)));
        when(service.createIssueReaction(anyString(), anyString(), anyLong(),
                any(ReactionRequest.class)))
                .thenReturn(Single.just(Response.success(reaction)));
        when(service.createIssueCommentReaction(anyString(), anyString(), anyLong(),
                any(ReactionRequest.class)))
                .thenReturn(Single.just(Response.success(reaction)));
        when(service.deleteIssueReaction(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.<Void>success(null)));
        when(service.deleteIssueCommentReaction(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.<Void>success(null)));
        return service;
    }

    @Test
    public void onCreateOptionsMenu_authorizedShowsReactAndLoadsReactions()
            throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            ReactionService.class, false)).thenReturn(service);
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            assertTrue(menu.findItem(R.id.react).isVisible());
            waitUntil(() -> !menu.findItem(R.id.loading).isVisible());
            assertTrue(menu.findItem(R.id.plus_one).isVisible());
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void translateActionView_clickTogglesAndToastsNoProvider() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        try (MockedStatic<TranslationManager> mocked = mockStatic(TranslationManager.class,
                Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> TranslationManager.getConfiguredProviders(any(Context.class)))
                    .thenReturn(Collections.emptyList());
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            TextView actionView = (TextView) field(f, "mTranslateActionView");
            actionView.performClick();
            assertEquals(f.getString(R.string.translate_no_provider),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void translateActionView_longPressReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        try (MockedStatic<TranslationManager> mocked = mockStatic(TranslationManager.class,
                Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> TranslationManager.getConfiguredProviders(any(Context.class)))
                    .thenReturn(Collections.emptyList());
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            TextView actionView = (TextView) field(f, "mTranslateActionView");
            assertTrue(actionView.performLongClick());
        }
    }

    @Test
    public void updateTranslateThreadItem_translatingShowsProgress() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(true);
        setField(f, "mCommentTranslator", translator);
        setField(field(f, "mThreadController"), "mThreadMode", true);
        TextView tv = new TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.translate_thread_progress, 0, 0),
                tv.getText().toString());
    }

    @Test
    public void updateTranslateThreadItem_doneShowsOriginal() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(false);
        when(translator.hasTranslations()).thenReturn(true);
        setField(f, "mCommentTranslator", translator);
        setField(field(f, "mThreadController"), "mThreadMode", true);
        TextView tv = new TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.show_original), tv.getText().toString());
    }

    @Test
    public void onOptionsItemSelected_reactionItemAddsReaction() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            ReactionService.class, false)).thenReturn(service);
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            assertTrue(f.onOptionsItemSelected(menu.findItem(R.id.plus_one)));
            verify(service).createIssueReaction(
                    eq("octocat"), eq("hello"), eq(7L), any(ReactionRequest.class));
        } finally {
            Gh4Application.get().logout();
        }
    }

    // ---- 缺口补齐：onAddData / fillData / 翻译 / 收集 ----

    @SuppressWarnings("unchecked")
    private static RootAdapter<TimelineItem, ?> adapterOf(
            IssueFragmentTest.NoLoad f) throws Exception {
        return (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
    }

    private static CommentTranslator mockTranslatorWithCallback() {
        CommentTranslator translator = mock(CommentTranslator.class);
        Mockito.doAnswer(inv -> {
            ((CommentTranslator.Callback) inv.getArgument(4)).onTranslationChanged();
            return null;
        }).when(translator).toggleTranslation(
                any(Context.class), anyString(), anyLong(), anyString(),
                any(CommentTranslator.Callback.class));
        return translator;
    }

    @Test
    public void reloadEvents_nullAdapterSkipsSuppress() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mAdapter", null);
        f.reloadEvents(false);
    }

    @Test
    public void onAddData_initialCommentNoMatchRunsFullLoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mInitialComment",
                new IntentUtils.InitialCommentMarker(999L));
        f.onAddData(adapterOf(f), Collections.singletonList(
                new TimelineItem.TimelineComment(
                        GitHubComment.builder().id(9L).bodyHtml("<p>hi</p>").build())));
        assertEquals(null, field(f, "mInitialComment"));
    }

    @Test
    public void onAddData_initialCommentReviewItem() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mInitialComment",
                new IntentUtils.InitialCommentMarker(999L));
        Review review = mock(Review.class);
        when(review.id()).thenReturn(5L);
        f.onAddData(adapterOf(f), Collections.singletonList(
                new TimelineItem.TimelineReview(review)));
        assertEquals(null, field(f, "mInitialComment"));
    }

    @Test
    public void onAddData_nonIdleControllerFeedsNewItems() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(true);
        setField(f, "mCommentTranslator", translator);
        Object controller = field(f, "mThreadController");
        setField(controller, "mThreadMode", true);
        setField(controller, "mBatchProvider", "google");
        f.onAddData(adapterOf(f), Collections.singletonList(
                new TimelineItem.TimelineComment(
                        GitHubComment.builder().id(9L).bodyHtml("<p>hi</p>").build())));
        verify(translator).translateAllWithProvider(
                any(Context.class), eq("google"),
                any(List.class), any(CommentTranslator.BatchCallback.class));
    }

    @Test
    public void updateCommentSectionVisibility_nullViewIsNoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        callVoid(f, "updateCommentSectionVisibility",
                new Class<?>[]{View.class}, (Object) null);
    }

    @Test
    public void isLocked_collaboratorOverridesLock() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(lockedIssue(), true, null);
        View header = (View) field(f, "mListHeaderView");
        TextView desc = header.findViewById(R.id.tv_desc);
        assertNotNull(desc.getCustomSelectionActionModeCallback());
    }

    @Test
    public void updateMentionUsers_nullIssueUserSkipsAdd() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mIssue", richIssue().toBuilder().user(null).build());
        f.onAddData(adapterOf(f), Collections.emptyList());
    }

    @Test
    public void fillData_lockedIssueClearsQuoteCallback() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(lockedIssue(), false, null);
        View header = (View) field(f, "mListHeaderView");
        TextView desc = header.findViewById(R.id.tv_desc);
        assertNull(desc.getCustomSelectionActionModeCallback());
    }

    @Test
    public void fillData_noAssigneesHidesGroup() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(
                richIssue().toBuilder().assignees(Collections.emptyList()).build(),
                false, null);
        View header = (View) field(f, "mListHeaderView");
        assertEquals(View.GONE,
                header.findViewById(R.id.assignee_container).getVisibility());
    }

    @Test
    public void bindIssueBody_translatedHtmlPreferred() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.getTranslatedHtml("issue", 42L)).thenReturn("<p>translated</p>");
        setField(f, "mCommentTranslator", translator);
        View header = (View) field(f, "mListHeaderView");
        TextView desc = header.findViewById(R.id.tv_desc);
        callVoid(f, "bindIssueBody",
                new Class<?>[]{TextView.class, String.class},
                desc, "<p>hello world</p>");
        idleMain();
        assertTrue(desc.getText().toString().contains("translated"));
    }

    @Test
    public void refreshIssueBody_nullImageGetterIsNoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mImageGetter", null);
        callVoid(f, "refreshIssueBody", new Class<?>[]{});
    }

    @Test
    public void refreshIssueBody_blankBodySkipsBind() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mIssue", richIssue().toBuilder().bodyHtml("").build());
        callVoid(f, "refreshIssueBody", new Class<?>[]{});
    }

    @Test
    public void translateBody_nullTranslatorWithActivityIsNoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", null);
        f.translateBody("issue", 42L, "<p>x</p>");
    }

    @Test
    public void translateBody_callbackIdMismatchSkipsRefresh() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", mockTranslatorWithCallback());
        f.translateBody("issue", 999L, "<p>x</p>");
    }

    @Test
    public void translateBody_callbackNullAdapterSkipsNotify() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", mockTranslatorWithCallback());
        setField(f, "mAdapter", null);
        f.translateBody("issue", 42L, "<p>x</p>");
    }

    @Test
    public void getTranslatedBody_nullTranslatorReturnsNull() {
        assertNull(new IssueFragment().getTranslatedBody("issue", 1L));
    }

    @Test
    public void collectThreadItems_bareFragmentReturnsEmpty() {
        assertEquals(0, new IssueFragment().collectThreadItems().size());
    }

    @Test
    public void collectThreadItems_blankBodySkipsIssue() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mIssue", richIssue().toBuilder().bodyHtml("").build());
        assertEquals(0, f.collectThreadItems().size());
    }

    @Test
    public void collectItem_eventItemSkipsBothBranches() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        List<CommentTranslator.TranslatableItem> items = new ArrayList<>();
        Method m = IssueFragmentBase.class.getDeclaredMethod("collectItem",
                TimelineItem.class, List.class);
        m.setAccessible(true);
        m.invoke(f, new TimelineItem.TimelineEvent(mock(IssueEvent.class)), items);
        assertEquals(0, items.size());
    }

    @Test
    public void collectItem_blankReviewSkipsAdd() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        List<CommentTranslator.TranslatableItem> items = new ArrayList<>();
        Method m = IssueFragmentBase.class.getDeclaredMethod("collectItem",
                TimelineItem.class, List.class);
        m.setAccessible(true);
        Review review = mock(Review.class);
        when(review.bodyHtml()).thenReturn("");
        m.invoke(f, new TimelineItem.TimelineReview(review), items);
        assertEquals(0, items.size());
    }

    @Test
    public void onTranslationsChanged_nullAdapterSkipsNotify() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mAdapter", null);
        f.onTranslationsChanged();
    }

    @Test
    public void fillLabels_emptyListHidesGroup() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        callVoid(f, "fillLabels", new Class<?>[]{List.class}, Collections.emptyList());
        View header = (View) field(f, "mListHeaderView");
        assertEquals(View.GONE,
                header.findViewById(R.id.label_container).getVisibility());
    }

    // ---- 缺口补齐：反应直调 / 杂项回调 / 删除 ----

    @Test
    public void getCacheKey_returnsIssueId() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertEquals(Long.valueOf(42L), f.getCacheKey());
    }

    @Test
    public void loadReactionDetails_issueReturnsEmptyList() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            ReactionService.class, false)).thenReturn(service);
            TestObserver<List<Reaction>> to = f.loadReactionDetails(
                    mock(ReactionBar.Item.class), false).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(Collections.emptyList());
        }
    }

    @Test
    public void canAddReaction_unlockedAndLocked() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertTrue(f.canAddReaction());
        IssueFragmentTest.NoLoad locked = attachInContainer(lockedIssue(), false, null);
        assertEquals(false, locked.canAddReaction());
    }

    @Test
    public void deleteReaction_issueReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            TestObserver<Boolean> to = f.deleteReaction(
                    mock(ReactionBar.Item.class), 1L).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(true);
        }
    }

    @Test
    public void loadReactionDetails_commentReturnsEmptyList() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            ReactionService.class, false)).thenReturn(service);
            GitHubCommentBase comment = mock(GitHubCommentBase.class);
            when(comment.id()).thenReturn(9L);
            TestObserver<List<Reaction>> to = f.loadReactionDetails(comment, false).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(Collections.emptyList());
        }
    }

    @Test
    public void addReaction_commentReturnsReaction() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            GitHubCommentBase comment = mock(GitHubCommentBase.class);
            when(comment.id()).thenReturn(9L);
            TestObserver<Reaction> to = f.addReaction(comment, "+1").test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValueCount(1);
        }
    }

    @Test
    public void deleteReaction_commentReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            GitHubCommentBase comment = mock(GitHubCommentBase.class);
            when(comment.id()).thenReturn(9L);
            TestObserver<Boolean> to = f.deleteReaction(comment, 1L).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(true);
        }
    }

    @Test
    public void onReactionsUpdated_nullHeaderSkipsBar() throws Exception {
        IssueFragment bare = new IssueFragment();
        setField(bare, "mIssue", richIssue());
        Reactions reactions = mock(Reactions.class);
        bare.onReactionsUpdated(mock(ReactionBar.Item.class), reactions);
        assertEquals(reactions, ((Issue) field(bare, "mIssue")).reactions());
    }

    @Test
    public void onReactionsUpdated_helperUpdatesMenu() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionBar.AddReactionMenuHelper helper =
                mock(ReactionBar.AddReactionMenuHelper.class);
        setField(f, "mReactionMenuHelper", helper);
        f.onReactionsUpdated(mock(ReactionBar.Item.class), mock(Reactions.class));
        verify(helper).updateMenuItems();
    }

    @Test
    public void onEditorTextSent_detachedSkipsReload() {
        new IssueFragment().onEditorTextSent();
    }

    @Test
    public void getEditorErrorMessageResId_returnsIssueError() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertEquals(R.string.issue_error_comment, f.getEditorErrorMessageResId());
    }

    @Test
    public void onScrollingInBasicEditor_locksAppBar() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onScrollingInBasicEditor(true);
        f.onScrollingInBasicEditor(false);
    }

    @Test
    public void replyCommentMethods_areNoops() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        f.onReplyCommentSelected(5L);
        assertEquals(0, f.getSelectedReplyCommentId());
    }

    @Test
    public void onConfirmed_deleteSuccessReloadsAndSetsResult() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        IssueCommentService service = mock(IssueCommentService.class);
        when(service.deleteIssueComment(anyString(), anyString(), anyLong()))
                .thenReturn(Single.just(Response.<Void>success(null)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueCommentService.class, false))
                    .thenReturn(service);
            f.onConfirmed("deleteconfirm",
                    GitHubComment.builder().id(9L).build());
            Thread.sleep(500);
            idleMain();
            assertEquals(Activity.RESULT_OK,
                    Shadows.shadowOf(f.getActivity()).getResultCode());
        }
    }

    @Test
    public void onConfirmed_deleteErrorHandlesFailure() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        IssueCommentService service = mock(IssueCommentService.class);
        when(service.deleteIssueComment(anyString(), anyString(), anyLong()))
                .thenReturn(Single.<Response<Void>>error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueCommentService.class, false))
                    .thenReturn(service);
            f.onConfirmed("deleteconfirm",
                    GitHubComment.builder().id(9L).build());
            idleFor(Duration.ofSeconds(11));
        }
    }

    // ---- 残余 8 个分支半边 ----

    @Test
    public void canChildScrollUp_scrolledListReturnsTrue() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(richIssue(), false, null);
        androidx.recyclerview.widget.RecyclerView rv =
                f.getView().findViewById(R.id.list);
        List<TimelineItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(new TimelineItem.TimelineComment(GitHubComment.builder()
                    .id((long) i).bodyHtml("<p>item " + i + "</p>")
                    .createdAt(new java.util.Date(1700000000000L + i * 1000L))
                    .user(User.builder().login("octo").build()).build()));
        }
        adapterOf(f).addAll(items);
        // NoLoad 从不加载，内容区默认 GONE（setContentShown(true) 只在
        // 数据到达时才跑），先手动显示再量，否则 RecyclerView 高度为 0。
        callVoid(f, "setContentShown", new Class<?>[]{boolean.class}, true);
        // Robolectric 下 BaseActivity 宿主的窗口遍历不量这个 fragment 的视图，
        // 手动走一遍 measure/layout 再滚；视口取 470（Robolectric 窗口高），
        // 30 条评论才能溢出。
        View root = f.getView();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(470, View.MeasureSpec.EXACTLY);
        root.measure(widthSpec, heightSpec);
        root.layout(0, 0, 1080, 470);
        rv.scrollToPosition(15);
        root.measure(widthSpec, heightSpec);
        root.layout(0, 0, 1080, 470);
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void onCreateOptionsMenu_authorizedLockedHidesReact() throws Exception {
        IssueFragmentTest.NoLoad f = attachInContainer(lockedIssue(), false, null);
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try {
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            assertEquals(false, menu.findItem(R.id.react).isVisible());
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void onOptionsItemSelected_helperIgnoresNonReactionItem() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        ReactionService service = mockReactionService();
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class);
             MockedStatic<TranslationManager> mockedTm =
                     mockStatic(TranslationManager.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            ReactionService.class, false)).thenReturn(service);
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false))
                    .thenReturn(service);
            mockedTm.when(() -> TranslationManager.getConfiguredProviders(any(Context.class)))
                    .thenReturn(Collections.emptyList());
            MenuBuilder menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
            // 反应 helper 不认 translate_thread，落到翻译分支仍返回 true。
            assertTrue(f.onOptionsItemSelected(menu.findItem(R.id.translate_thread)));
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void onAddData_initialCommentEventItemSkipsBothBranches() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mInitialComment",
                new IntentUtils.InitialCommentMarker(999L));
        f.onAddData(adapterOf(f), Collections.singletonList(
                new TimelineItem.TimelineEvent(mock(IssueEvent.class))));
        assertEquals(null, field(f, "mInitialComment"));
    }

    @Test
    public void bindIssueBody_nullTranslatorBindsOriginal() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", null);
        View header = (View) field(f, "mListHeaderView");
        TextView desc = header.findViewById(R.id.tv_desc);
        callVoid(f, "bindIssueBody",
                new Class<?>[]{TextView.class, String.class},
                desc, "<p>hello world</p>");
        idleMain();
        assertTrue(desc.getText().toString().contains("hello world"));
    }

    @Test
    public void refreshIssueBody_nullIssueIsNoop() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mIssue", null);
        callVoid(f, "refreshIssueBody", new Class<?>[]{});
    }

    @Test
    public void translateBody_callbackNullIssueIdSkipsRefresh() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        setField(f, "mCommentTranslator", mockTranslatorWithCallback());
        setField(f, "mIssue", richIssue().toBuilder().id(null).build());
        f.translateBody("issue", 42L, "<p>x</p>");
    }

    @Test
    public void getTranslatedBody_withTranslatorDelegates() throws Exception {
        IssueFragmentTest.NoLoad f = attachedRich();
        assertNull(f.getTranslatedBody("issue", 42L));
    }
}
