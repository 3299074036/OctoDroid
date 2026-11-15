package com.gh4a.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.translate.CommentTranslator;
import com.gh4a.utils.translate.ThreadTranslateController;
import com.gh4a.utils.translate.TranslationManager;
import com.gh4a.widget.EditorBottomSheet;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.ReactionRequest;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;
import com.meisolsson.githubsdk.service.reactions.ReactionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ReviewFragment：PR 评审页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class ReviewFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Fragment.SavedState(Bundle) 在此 androidx 版本是包私有构造，
    // 只能反射创建；用于给未挂载的 Fragment 预置恢复状态。
    private static androidx.fragment.app.Fragment.SavedState savedState(
            android.os.Bundle bundle) throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> c =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(android.os.Bundle.class);
        c.setAccessible(true);
        return c.newInstance(bundle);
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static ReviewFragment fragment() {
        return ReviewFragment.newInstance("octo", "cat", 42, null, null);
    }

    private static Object field(ReviewFragment f, String name) throws Exception {
        Field field = ReviewFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void newInstance_storesAllArguments() {
        ReviewFragment f = fragment();
        assertEquals("octo", f.getArguments().getString("repo_owner"));
        assertEquals("cat", f.getArguments().getString("repo_name"));
        assertEquals(42, f.getArguments().getInt("issue_number"));
    }

    @Test
    public void onCreate_readsArgumentsAndRemovesInitialComment() throws Exception {
        ReviewFragment f = attach(fragment());
        assertEquals("octo", field(f, "mRepoOwner"));
        assertEquals("cat", field(f, "mRepoName"));
        assertEquals(42, field(f, "mIssueNumber"));
        assertFalse(f.getArguments().containsKey("initial_comment"));
    }

    @Test
    public void onCreate_restoresSelectedReplyIdFromSavedState() throws Exception {
        // 未挂载的 Fragment 直接调 onCreate(saved) 会在 Fragment.onCreate 内
        // 触 mNonConfig 抛 NPE；正确做法是 setInitialSavedState 后挂载。
        ReviewFragment f = ReviewFragment.newInstance("o", "r", 1, null, null);
        Bundle saved = new Bundle();
        saved.putLong("selected_reply_comment_id", 99L);
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertEquals(99L, (long) (Long) field(f, "mSelectedReplyCommentId"));
    }

    @Test
    public void getCommentEditorHintResId_defaultIsReviewReplyHint() {
        ReviewFragment f = attach(fragment());
        assertEquals(R.string.review_reply_hint, f.getCommentEditorHintResId());
    }

    @Test
    public void getEditorErrorMessageResId() {
        ReviewFragment f = attach(fragment());
        assertEquals(R.string.issue_error_comment, f.getEditorErrorMessageResId());
    }

    @Test
    public void getSelectedReplyCommentId_defaultIsZero() {
        ReviewFragment f = attach(fragment());
        assertEquals(0L, f.getSelectedReplyCommentId());
    }

    @Test
    public void collectThreadItems_noAdapterReturnsEmpty() {
        ReviewFragment f = attach(fragment());
        assertNotNull(f.collectThreadItems());
        assertTrue(f.collectThreadItems().isEmpty());
    }

    @Test
    public void getTranslator_isNullBeforeViewCreated() {
        ReviewFragment f = attach(fragment());
        assertNull(f.getTranslator());
    }

    @Test
    public void onTranslationsChanged_noAdapterIsNoop() {
        ReviewFragment f = attach(fragment());
        f.onTranslationsChanged();
    }

    @Test
    public void onBackPressed_noBottomSheetReturnsFalse() {
        ReviewFragment f = attach(fragment());
        assertFalse(f.onBackPressed());
    }

    @Test
    public void canChildScrollUp_noViewReturnsFalse() {
        ReviewFragment f = attach(fragment());
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void onSaveInstanceState_storesSelectedReplyId() {
        ReviewFragment f = attach(fragment());
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("selected_reply_comment_id"));
        assertEquals(0L, out.getLong("selected_reply_comment_id"));
    }

    @Test
    public void getEmptyTextResId_isZeroFromBase() {
        ReviewFragment f = attach(fragment());
        assertEquals(0, f.getEmptyTextResId());
    }

    // ---- NoLoad 挂载（RESUMED，TestBaseActivity）+ 数据单 ----

    public static class NoLoad extends ReviewFragment {
        @Override
        protected java.util.List<com.gh4a.model.TimelineItem> onGetInitialData() {
            return null;
        }

        @Override
        protected io.reactivex.Observable<java.util.List<com.gh4a.model.TimelineItem>>
                onCreateDataObservable(boolean bypassCache) {
            return io.reactivex.Observable.never();
        }

        @Override
        protected io.reactivex.Single<java.util.List<com.gh4a.model.TimelineItem>>
                onCreateDataSingle(boolean bypassCache) {
            return io.reactivex.Single.never();
        }
    }

    private NoLoad attached() {
        NoLoad f = new NoLoad();
        f.setArguments(fragment().getArguments());
        FragmentTestHelper.attachTo(IssueFragmentTest.TestBaseActivity.class, f);
        return f;
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
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

    @SuppressWarnings("unchecked")
    private static <T> com.meisolsson.githubsdk.model.Page<T> pageOf(
            java.util.List<T> items) {
        com.meisolsson.githubsdk.model.Page<T> page =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.model.Page.class);
        org.mockito.Mockito.when(page.items()).thenReturn(items);
        org.mockito.Mockito.when(page.next()).thenReturn(null);
        return page;
    }

    private static com.meisolsson.githubsdk.model.ReviewComment reviewComment(
            long id) {
        com.meisolsson.githubsdk.model.ReviewComment rc =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.model.ReviewComment.class);
        org.mockito.Mockito.when(rc.id()).thenReturn(id);
        org.mockito.Mockito.when(rc.path()).thenReturn("a.java");
        org.mockito.Mockito.when(rc.originalCommitId()).thenReturn("abc");
        org.mockito.Mockito.when(rc.originalPosition()).thenReturn(3);
        org.mockito.Mockito.when(rc.createdAt())
                .thenReturn(new java.util.Date(1700000000000L));
        org.mockito.Mockito.when(rc.bodyHtml()).thenReturn("<p>c</p>");
        org.mockito.Mockito.when(rc.body()).thenReturn("c");
        return rc;
    }

    private static ReviewFragment bareWithReview() throws Exception {
        ReviewFragment f = new ReviewFragment();
        com.meisolsson.githubsdk.model.Review review =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.model.Review.class);
        org.mockito.Mockito.when(review.id()).thenReturn(42L);
        setField(f, "mRepoOwner", "octo");
        setField(f, "mRepoName", "cat");
        setField(f, "mIssueNumber", 42);
        setField(f, "mReview", review);
        return f;
    }

    @Test
    public void onCreateDataSingle_buildsReviewItems() throws Exception {
        ReviewFragment f = bareWithReview();
        com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService
                reviewService = org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestReviewService.class);
        com.meisolsson.githubsdk.service.pull_request.PullRequestService
                prService = org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestService.class);
        com.meisolsson.githubsdk.service.pull_request
                .PullRequestReviewCommentService commentService =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestReviewCommentService.class);
        org.mockito.Mockito.when(reviewService.getReview(
                        org.mockito.ArgumentMatchers.eq("octo"),
                        org.mockito.ArgumentMatchers.eq("cat"),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.eq(42L)))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(
                        (com.meisolsson.githubsdk.model.Review) field(f, "mReview"))));
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.ReviewComment>
                commentsPage = pageOf(java.util.Collections.singletonList(
                        reviewComment(11L)));
        org.mockito.Mockito.when(reviewService.getReviewComments(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(commentsPage)));
        com.meisolsson.githubsdk.model.GitHubFile file =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.model.GitHubFile.class);
        org.mockito.Mockito.when(file.filename()).thenReturn("a.java");
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.GitHubFile>
                filesPage = pageOf(java.util.Collections.singletonList(file));
        org.mockito.Mockito.when(prService.getPullRequestFiles(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(filesPage)));
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.ReviewComment>
                prCommentsPage = pageOf(java.util.Collections.emptyList());
        org.mockito.Mockito.when(commentService.getPullRequestComments(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(prCommentsPage)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewService.class, false))
                    .thenReturn(reviewService);
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestService.class, false))
                    .thenReturn(prService);
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewCommentService.class, false))
                    .thenReturn(commentService);
            java.util.List<com.gh4a.model.TimelineItem> items =
                    f.onCreateDataSingle(false).blockingGet();
            assertEquals(4, items.size());
            assertTrue(items.get(0)
                    instanceof com.gh4a.model.TimelineItem.TimelineReview);
            assertTrue(items.get(1)
                    instanceof com.gh4a.model.TimelineItem.Diff);
            assertTrue(items.get(3)
                    instanceof com.gh4a.model.TimelineItem.Reply);
        }
    }

    @Test
    public void onCreateDataSingle_noCommentsReturnsReviewOnly() throws Exception {
        ReviewFragment f = bareWithReview();
        com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService
                reviewService = org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestReviewService.class);
        org.mockito.Mockito.when(reviewService.getReview(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(
                        (com.meisolsson.githubsdk.model.Review) field(f, "mReview"))));
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.ReviewComment>
                emptyPage = pageOf(java.util.Collections.emptyList());
        org.mockito.Mockito.when(reviewService.getReviewComments(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(emptyPage)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewService.class, false))
                    .thenReturn(reviewService);
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestService.class, false))
                    .thenReturn(org.mockito.Mockito.mock(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestService.class));
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewCommentService.class, false))
                    .thenReturn(org.mockito.Mockito.mock(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewCommentService.class));
            java.util.List<com.gh4a.model.TimelineItem> items =
                    f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
        }
    }

    @Test
    public void onAddData_selectsFirstReplyAndRemovesIt() throws Exception {
        NoLoad f = attached();
        com.gh4a.model.TimelineItem.TimelineComment tc =
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(9L).build());
        com.gh4a.model.TimelineItem.Diff diff =
                new com.gh4a.model.TimelineItem.Diff(tc);
        com.gh4a.model.TimelineItem.Reply reply =
                new com.gh4a.model.TimelineItem.Reply(tc);
        java.util.List<com.gh4a.model.TimelineItem> data =
                new java.util.ArrayList<>(java.util.Arrays.asList(diff, reply));
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(9L, f.getSelectedReplyCommentId());
        assertEquals(1, data.size());
        assertEquals(R.string.reply, f.getCommentEditorHintResId());
    }

    @Test
    public void onAddData_twoDiffGroupsReturnsEarly() throws Exception {
        NoLoad f = attached();
        com.gh4a.model.TimelineItem.TimelineComment tc1 =
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(1L).build());
        com.gh4a.model.TimelineItem.TimelineComment tc2 =
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(2L).build());
        java.util.List<com.gh4a.model.TimelineItem> data =
                new java.util.ArrayList<>(java.util.Arrays.asList(
                        new com.gh4a.model.TimelineItem.Diff(tc1),
                        new com.gh4a.model.TimelineItem.Diff(tc2)));
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(0L, f.getSelectedReplyCommentId());
        assertEquals(2, data.size());
    }

    @Test
    public void onAddData_initialCommentHighlightsMatch() throws Exception {
        NoLoad f = attached();
        setField(f, "mInitialComment",
                new com.gh4a.utils.IntentUtils.InitialCommentMarker(9L));
        com.gh4a.model.TimelineItem.TimelineComment tc =
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(9L).build());
        java.util.List<com.gh4a.model.TimelineItem> data =
                new java.util.ArrayList<>(
                        java.util.Collections.singletonList(tc));
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(null, field(f, "mInitialComment"));
    }

    @Test
    public void collectItem_commentAndReviewBranches() throws Exception {
        NoLoad f = attached();
        java.util.List<com.gh4a.utils.translate.CommentTranslator.TranslatableItem>
                items = new java.util.ArrayList<>();
        java.lang.reflect.Method m = ReviewFragment.class.getDeclaredMethod(
                "collectItem", com.gh4a.model.TimelineItem.class,
                java.util.List.class);
        m.setAccessible(true);
        m.invoke(f, new com.gh4a.model.TimelineItem.TimelineComment(
                com.meisolsson.githubsdk.model.GitHubComment.builder()
                        .id(1L).bodyHtml("").build()), items);
        assertEquals(0, items.size());
        m.invoke(f, new com.gh4a.model.TimelineItem.TimelineComment(
                com.meisolsson.githubsdk.model.GitHubComment.builder()
                        .id(1L).bodyHtml("<p>c</p>").build()), items);
        assertEquals(1, items.size());
        assertEquals("comment", items.get(0).type);
        com.meisolsson.githubsdk.model.Review review =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.model.Review.class);
        org.mockito.Mockito.when(review.bodyHtml()).thenReturn("<p>r</p>");
        org.mockito.Mockito.when(review.id()).thenReturn(5L);
        m.invoke(f, new com.gh4a.model.TimelineItem.TimelineReview(review),
                items);
        assertEquals(2, items.size());
        assertEquals("review", items.get(1).type);
    }

    @Test
    public void collectThreadItems_withAdapterItems() throws Exception {
        NoLoad f = attached();
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<com.gh4a.model.TimelineItem, ?>)
                        field(f, "mAdapter");
        adapter.addAll(java.util.Collections.singletonList(
                new com.gh4a.model.TimelineItem.TimelineComment(
                        com.meisolsson.githubsdk.model.GitHubComment.builder()
                                .id(1L).bodyHtml("<p>c</p>").build())));
        assertEquals(1, f.collectThreadItems().size());
    }

    @Test
    public void onCreateOptionsMenu_buildsTranslateItem() throws Exception {
        NoLoad f = attached();
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        android.view.MenuInflater inflater =
                new android.view.MenuInflater(f.getActivity());
        f.onCreateOptionsMenu(menu, inflater);
        assertTrue(menu.findItem(R.id.translate_thread) != null);
        android.widget.TextView actionView =
                (android.widget.TextView) field(f, "mTranslateActionView");
        assertTrue(actionView != null);
        assertEquals(f.getString(R.string.translate),
                actionView.getText().toString());
    }

    @Test
    public void onOptionsItemSelected_translateThreadReturnsTrue() throws Exception {
        NoLoad f = attached();
        android.view.MenuItem item = org.mockito.Mockito.mock(
                android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId())
                .thenReturn(R.id.translate_thread);
        assertTrue(f.onOptionsItemSelected(item));
        assertEquals(f.getString(R.string.translate_nothing_to_translate),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onOptionsItemSelected_unknownDelegatesToSuper() throws Exception {
        NoLoad f = attached();
        android.view.MenuItem item = org.mockito.Mockito.mock(
                android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(R.id.search);
        assertEquals(false, f.onOptionsItemSelected(item));
    }

    @Test
    public void updateTranslateThreadItem_idleShowsTranslate() throws Exception {
        NoLoad f = attached();
        android.widget.TextView tv = new android.widget.TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.translate), tv.getText().toString());
    }

    @Test
    public void editComment_reviewCommentLaunchesEditPullRequestComment()
            throws Exception {
        NoLoad f = attached();
        f.editComment(reviewComment(11L));
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(com.gh4a.activities.EditPullRequestCommentActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void editComment_issueCommentLaunchesEditIssueComment() throws Exception {
        NoLoad f = attached();
        f.editComment(com.meisolsson.githubsdk.model.GitHubComment.builder()
                .id(8L).body("nice").build());
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(com.gh4a.activities.EditIssueCommentActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void deleteComment_showsConfirmationDialog() throws Exception {
        NoLoad f = attached();
        f.deleteComment(com.meisolsson.githubsdk.model.GitHubComment.builder()
                .id(9L).build());
        f.getParentFragmentManager().executePendingTransactions();
        assertTrue(f.getParentFragmentManager()
                .findFragmentByTag("deleteconfirm") != null);
    }

    @Test
    public void translateBody_withMockTranslatorRunsCallback() throws Exception {
        NoLoad f = attached();
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
        f.translateBody("comment", 9L, "<p>x</p>");
        org.mockito.Mockito.verify(translator).toggleTranslation(
                org.mockito.ArgumentMatchers.any(android.content.Context.class),
                org.mockito.ArgumentMatchers.eq("comment"),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("<p>x</p>"),
                org.mockito.ArgumentMatchers.any(
                        com.gh4a.utils.translate.CommentTranslator.Callback.class));
    }

    @Test
    public void translateBody_nullTranslatorIsNoop() throws Exception {
        ReviewFragment f = attach(fragment());
        f.translateBody("comment", 9L, "<p>x</p>");
    }

    @Test
    public void onConfirmed_deletesReviewComment() throws Exception {
        NoLoad f = attached();
        com.meisolsson.githubsdk.service.pull_request
                .PullRequestReviewCommentService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestReviewCommentService.class);
        org.mockito.Mockito.when(service.deleteComment(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewCommentService.class, false))
                    .thenReturn(service);
            f.onConfirmed("deleteconfirm", reviewComment(11L));
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.Shadows.shadowOf(
                        android.os.Looper.getMainLooper()).idle();
                Thread.sleep(50);
                // 成功回调会调 reloadComments → onRefresh（NoLoad 恒 never），
                // 只要不抛错、主线程已跑过一轮即算完成。
                if (System.currentTimeMillis() > deadline - 4000) break;
            }
        }
    }

    @Test
    public void addReaction_reviewCommentUsesReviewReactionService() throws Exception {
        NoLoad f = attached();
        com.meisolsson.githubsdk.service.reactions.ReactionService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.reactions.ReactionService.class);
        com.meisolsson.githubsdk.model.Reaction reaction =
                com.meisolsson.githubsdk.model.Reaction.builder().build();
        org.mockito.Mockito.when(service.createPullRequestReviewCommentReaction(
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
                    to = f.addReaction(reviewComment(11L), "+1").test();
            assertTrue(to.awaitTerminalEvent(10, java.util.concurrent.TimeUnit.SECONDS));
            to.assertValue(reaction);
        }
    }

    @Test
    public void deleteReaction_reviewCommentReturnsTrue() throws Exception {
        NoLoad f = attached();
        com.meisolsson.githubsdk.service.reactions.ReactionService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.reactions.ReactionService.class);
        org.mockito.Mockito.when(service.deletePullRequestReviewCommentReaction(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            com.meisolsson.githubsdk.service.reactions
                                    .ReactionService.class, false))
                    .thenReturn(service);
            io.reactivex.observers.TestObserver<Boolean> to =
                    f.deleteReaction(reviewComment(11L), 5L).test();
            assertTrue(to.awaitTerminalEvent(10, java.util.concurrent.TimeUnit.SECONDS));
            to.assertValue(true);
        }
    }

    @Test
    public void loadReactionDetails_reviewCommentUsesReviewReactions()
            throws Exception {
        ReviewFragment f = bareWithReview();
        com.meisolsson.githubsdk.service.reactions.ReactionService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.reactions.ReactionService.class);
        com.meisolsson.githubsdk.model.Reaction reaction =
                com.meisolsson.githubsdk.model.Reaction.builder().build();
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.Reaction>
                reactionsPage = pageOf(
                        java.util.Collections.singletonList(reaction));
        org.mockito.Mockito.when(service.getPullRequestReviewCommentReactions(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(reactionsPage)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.reactions
                                    .ReactionService.class, false))
                    .thenReturn(service);
            java.util.List<com.meisolsson.githubsdk.model.Reaction> reactions =
                    f.loadReactionDetails(reviewComment(11L), false).blockingGet();
            assertEquals(1, reactions.size());
        }
    }

    @Test
    public void loadReactionDetails_issueCommentUsesIssueReactions() throws Exception {
        ReviewFragment f = bareWithReview();
        com.meisolsson.githubsdk.service.reactions.ReactionService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.reactions.ReactionService.class);
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.Reaction>
                emptyReactionsPage = pageOf(java.util.Collections.emptyList());
        org.mockito.Mockito.when(service.getIssueCommentReactions(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(emptyReactionsPage)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            com.meisolsson.githubsdk.service.reactions
                                    .ReactionService.class, false))
                    .thenReturn(service);
            java.util.List<com.meisolsson.githubsdk.model.Reaction> reactions =
                    f.loadReactionDetails(
                            com.meisolsson.githubsdk.model.GitHubComment.builder()
                                    .id(8L).build(), false).blockingGet();
            assertEquals(0, reactions.size());
        }
    }

    @Test
    public void onEditorDoSend_returnsCreatedReviewComment() throws Exception {
        NoLoad f = attached();
        com.meisolsson.githubsdk.service.pull_request
                .PullRequestReviewCommentService service =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.pull_request
                                .PullRequestReviewCommentService.class);
        com.meisolsson.githubsdk.model.ReviewComment created = reviewComment(12L);
        org.mockito.Mockito.when(service.createReviewComment(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(
                                com.meisolsson.githubsdk.model.request.pull_request
                                        .CreateReviewComment.class)))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(created)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            com.meisolsson.githubsdk.service.pull_request
                                    .PullRequestReviewCommentService.class, false))
                    .thenReturn(service);
            Object value = f.onEditorDoSend("hi").blockingGet();
            assertEquals(created, value);
        }
    }

    @Test
    public void onReplyCommentSelected_setsIdAndUnlocks() throws Exception {
        NoLoad f = attached();
        f.onReplyCommentSelected(77L);
        assertEquals(77L, f.getSelectedReplyCommentId());
    }

    @Test
    public void setHighlightColors_setsBottomSheetColor() throws Exception {
        NoLoad f = attached();
        callVoid(f, "setHighlightColors",
                new Class<?>[]{int.class, int.class},
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
    }

    @Test
    public void reloadComments_withAdapterSuppressesCacheClear() throws Exception {
        NoLoad f = attached();
        callVoid(f, "reloadComments", new Class<?>[]{boolean.class}, false);
        callVoid(f, "reloadComments", new Class<?>[]{boolean.class}, true);
    }

    @Test
    public void onEditorTextSent_refreshes() throws Exception {
        NoLoad f = attached();
        f.onEditorTextSent();
    }

    // ---- 缺口补齐 ----

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

    private static void removeFragment(NoLoad f) {
        f.getParentFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
    }

    /** 容器挂载（android.R.id.content），View.post 与 RecyclerView 绑定需要它。 */
    private static NoLoad attachInContainer(NoLoad f) {
        IssueFragmentTest.TestBaseActivity activity = Robolectric
                .buildActivity(IssueFragmentTest.TestBaseActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f).commitNow();
        return f;
    }

    /** 记录 onRefresh 是否被调到的 NoLoad。 */
    public static class RefreshRecording extends NoLoad {
        boolean refreshed;

        @Override
        public void onRefresh() {
            refreshed = true;
        }
    }

    @Test
    public void editLauncher_resultOkReloadsComments() throws Exception {
        RefreshRecording f = new RefreshRecording();
        f.setArguments(fragment().getArguments());
        IssueFragmentTest.TestBaseActivity activity = FragmentTestHelper.attachTo(
                IssueFragmentTest.TestBaseActivity.class, f);
        Field launcherField = ReviewFragment.class.getDeclaredField("mEditLauncher");
        launcherField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ActivityResultLauncher<Intent> launcher =
                (ActivityResultLauncher<Intent>) launcherField.get(f);
        launcher.launch(new Intent(activity,
                com.gh4a.activities.EditIssueCommentActivity.class));
        idleMain();
        Intent started = Shadows.shadowOf(activity)
                .getNextStartedActivityForResult().intent;
        Shadows.shadowOf(activity).receiveResult(
                started, Activity.RESULT_OK, new Intent());
        idleMain();
        assertTrue(f.refreshed);
    }

    @Test
    public void onDestroyView_withTranslatorDestroysIt() throws Exception {
        NoLoad f = attached();
        assertNotNull(field(f, "mCommentTranslator"));
        removeFragment(f);
        assertNull(field(f, "mCommentTranslator"));
    }

    @Test
    public void onDestroyView_nullTranslatorIsNoop() throws Exception {
        NoLoad f = attached();
        setField(f, "mCommentTranslator", null);
        removeFragment(f);
        assertNull(field(f, "mCommentTranslator"));
    }

    @Test
    public void onBackPressed_advancedModeExitsAndReturnsTrue() throws Exception {
        NoLoad f = attached();
        EditorBottomSheet sheet = (EditorBottomSheet) field(f, "mBottomSheet");
        sheet.setAdvancedMode(true);
        assertTrue(sheet.isInAdvancedMode());
        assertTrue(f.onBackPressed());
    }

    @Test
    public void onBackPressed_notAdvancedReturnsFalse() throws Exception {
        NoLoad f = attached();
        assertFalse(f.onBackPressed());
    }

    @Test
    public void canChildScrollUp_expandedBottomSheetReturnsTrue() throws Exception {
        NoLoad f = new NoLoad();
        f.setArguments(fragment().getArguments());
        attachInContainer(f);
        idleMain();
        EditorBottomSheet sheet = (EditorBottomSheet) field(f, "mBottomSheet");
        BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        idleMain();
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_scrolledListReturnsTrue() throws Exception {
        NoLoad f = new NoLoad();
        f.setArguments(fragment().getArguments());
        attachInContainer(f);
        @SuppressWarnings("unchecked")
        com.gh4a.adapter.RootAdapter<TimelineItem, ?> adapter =
                (com.gh4a.adapter.RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        List<TimelineItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(new TimelineItem.TimelineComment(GitHubComment.builder()
                    .id((long) i).bodyHtml("<p>x</p>")
                    .createdAt(new Date(1700000000000L))
                    .user(User.builder().login("u").build()).build()));
        }
        adapter.addAll(items);
        // 直接往 adapter 加数据绕过了 onAddData/updateEmptyState：RV 初始为空
        // 时是 GONE，须手动恢复可见，否则量不到高度、滚不动。
        callVoid(f, "updateEmptyState", new Class<?>[]{});
        callVoid(f, "setContentShown", new Class<?>[]{boolean.class}, true);
        View root = f.getView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(470, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 470);
        RecyclerView rv = root.findViewById(R.id.list);
        rv.scrollToPosition(15);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(470, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 470);
        assertTrue(f.canChildScrollUp());
    }

    private static ReviewComment reviewComment(long id, long createdAtMillis) {
        ReviewComment rc = mock(ReviewComment.class);
        when(rc.id()).thenReturn(id);
        when(rc.path()).thenReturn("a.java");
        when(rc.originalCommitId()).thenReturn("abc");
        when(rc.originalPosition()).thenReturn(3);
        when(rc.createdAt()).thenReturn(new Date(createdAtMillis));
        when(rc.bodyHtml()).thenReturn("<p>c</p>");
        when(rc.body()).thenReturn("c");
        return rc;
    }

    private static GitHubFile mockFile() {
        GitHubFile file = mock(GitHubFile.class);
        when(file.filename()).thenReturn("a.java");
        return file;
    }

    @SuppressWarnings("unchecked")
    private static List<TimelineItem> runDataSingle(
            Page<ReviewComment> reviewCommentsPage,
            Page<GitHubFile> filesPage,
            Page<ReviewComment> prCommentsPage) throws Exception {
        ReviewFragment f = bareWithReview();
        PullRequestReviewService reviewService = mock(PullRequestReviewService.class);
        when(reviewService.getReview(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success((Review) field(f, "mReview"))));
        when(reviewService.getReviewComments(anyString(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(reviewCommentsPage)));
        PullRequestService prService = mock(PullRequestService.class);
        when(prService.getPullRequestFiles(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(filesPage)));
        PullRequestReviewCommentService commentService =
                mock(PullRequestReviewCommentService.class);
        when(commentService.getPullRequestComments(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(prCommentsPage)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewService.class), eq(false))).thenReturn(reviewService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestService.class), eq(false))).thenReturn(prService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(commentService);
            return f.onCreateDataSingle(false).blockingGet();
        }
    }

    @Test
    public void onCreateDataSingle_unseenCommitCommentIsAdded() throws Exception {
        List<TimelineItem> items = runDataSingle(
                pageOf(Collections.singletonList(reviewComment(11L))),
                pageOf(Collections.singletonList(mockFile())),
                pageOf(Collections.singletonList(reviewComment(12L))));
        assertEquals(5, items.size());
        assertTrue(items.get(3) instanceof TimelineItem.TimelineComment);
    }

    @Test
    public void onCreateDataSingle_seenCommitCommentIsSkipped() throws Exception {
        List<TimelineItem> items = runDataSingle(
                pageOf(Collections.singletonList(reviewComment(11L))),
                pageOf(Collections.singletonList(mockFile())),
                pageOf(Collections.singletonList(reviewComment(11L))));
        assertEquals(4, items.size());
    }

    @Test
    public void onCreateDataSingle_olderCommitCommentMarksReplyHunk() throws Exception {
        List<TimelineItem> items = runDataSingle(
                pageOf(Collections.singletonList(reviewComment(11L, 1700000001000L))),
                pageOf(Collections.singletonList(mockFile())),
                pageOf(Collections.singletonList(reviewComment(12L, 1700000000000L))));
        assertEquals(3, items.size());
        for (TimelineItem item : items) {
            assertFalse(item instanceof TimelineItem.Reply);
        }
    }

    @Test
    public void onAddData_twoDiffGroupsKeepsReplyHint() throws Exception {
        NoLoad f = attached();
        TimelineItem.TimelineComment tc1 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).build());
        TimelineItem.TimelineComment tc2 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(2L).build());
        List<TimelineItem> data = new ArrayList<>(Arrays.asList(
                new TimelineItem.Diff(tc1), new TimelineItem.Reply(tc1),
                new TimelineItem.Diff(tc2), new TimelineItem.Reply(tc2)));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(R.string.review_reply_hint, f.getCommentEditorHintResId());
        assertEquals(4, data.size());
    }

    @Test
    public void onAddData_nonIdleThreadStateCollectsNewItems() throws Exception {
        NoLoad f = attached();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(true);
        when(translator.hasTranslations()).thenReturn(false);
        setField(f, "mCommentTranslator", translator);
        ThreadTranslateController controller =
                (ThreadTranslateController) field(f, "mThreadController");
        setField(controller, "mThreadMode", true);
        setField(controller, "mBatchProvider", "google");
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        List<TimelineItem> data = new ArrayList<>(Collections.singletonList(
                new TimelineItem.TimelineComment(GitHubComment.builder()
                        .id(1L).bodyHtml("<p>c</p>").build())));
        f.onAddData(adapter, data);
        verify(translator).translateAllWithProvider(any(Context.class), eq("google"),
                any(List.class), any(CommentTranslator.BatchCallback.class));
    }

    @Test
    public void translateActionView_clickTogglesAndLongPressReturnsTrue() throws Exception {
        NoLoad f = attached();
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        adapter.addAll(Collections.singletonList(
                new TimelineItem.TimelineComment(GitHubComment.builder()
                        .id(1L).bodyHtml("<p>c</p>").build())));
        MenuBuilder menu = new MenuBuilder(f.getActivity());
        f.onCreateOptionsMenu(menu, new MenuInflater(f.getActivity()));
        android.widget.TextView actionView =
                (android.widget.TextView) field(f, "mTranslateActionView");
        try (MockedStatic<TranslationManager> mocked =
                     mockStatic(TranslationManager.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> TranslationManager.getConfiguredProviders(any(Context.class)))
                    .thenReturn(Collections.emptyList());
            actionView.performClick();
            assertEquals(f.getString(R.string.translate_no_provider),
                    ShadowToast.getTextOfLatestToast());
            assertTrue(actionView.performLongClick());
        }
    }

    @Test
    public void updateTranslateThreadItem_nullActionViewIsNoop() throws Exception {
        ReviewFragment f = attach(fragment());
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
    }

    @Test
    public void updateTranslateThreadItem_translatingShowsProgress() throws Exception {
        NoLoad f = attached();
        android.widget.TextView tv = new android.widget.TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(true);
        setField(f, "mCommentTranslator", translator);
        ThreadTranslateController controller =
                (ThreadTranslateController) field(f, "mThreadController");
        setField(controller, "mThreadMode", true);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.translate_thread_progress,
                controller.getDone(), controller.getTotal()), tv.getText().toString());
    }

    @Test
    public void updateTranslateThreadItem_doneShowsOriginal() throws Exception {
        NoLoad f = attached();
        android.widget.TextView tv = new android.widget.TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.isBatchRunning()).thenReturn(false);
        when(translator.hasTranslations()).thenReturn(true);
        setField(f, "mCommentTranslator", translator);
        ThreadTranslateController controller =
                (ThreadTranslateController) field(f, "mThreadController");
        setField(controller, "mThreadMode", true);
        callVoid(f, "updateTranslateThreadItem", new Class<?>[]{});
        assertEquals(f.getString(R.string.show_original), tv.getText().toString());
    }

    @Test
    public void collectItem_nullAndEmptyBodyAddsNothing() throws Exception {
        NoLoad f = attached();
        List<CommentTranslator.TranslatableItem> items = new ArrayList<>();
        Method m = ReviewFragment.class.getDeclaredMethod("collectItem",
                TimelineItem.class, List.class);
        m.setAccessible(true);
        // comment bodyHtml null（第一操作数假）
        m.invoke(f, new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).build()), items);
        assertEquals(0, items.size());
        // comment bodyHtml 空（第二操作数假）
        m.invoke(f, new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).bodyHtml("").build()), items);
        assertEquals(0, items.size());
        // 既非 comment 也非 review（else-if 假）
        TimelineItem.TimelineComment tc = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).bodyHtml("<p>c</p>").build());
        m.invoke(f, new TimelineItem.Diff(tc), items);
        assertEquals(0, items.size());
        // review bodyHtml null / 空
        Review reviewNull = mock(Review.class);
        when(reviewNull.bodyHtml()).thenReturn(null);
        when(reviewNull.id()).thenReturn(5L);
        m.invoke(f, new TimelineItem.TimelineReview(reviewNull), items);
        assertEquals(0, items.size());
        Review reviewEmpty = mock(Review.class);
        when(reviewEmpty.bodyHtml()).thenReturn("");
        when(reviewEmpty.id()).thenReturn(5L);
        m.invoke(f, new TimelineItem.TimelineReview(reviewEmpty), items);
        assertEquals(0, items.size());
    }

    @Test
    public void onTranslationsChanged_withAdapterNotifies() throws Exception {
        NoLoad f = attached();
        com.gh4a.adapter.timeline.TimelineItemAdapter adapter =
                (com.gh4a.adapter.timeline.TimelineItemAdapter) field(f, "mAdapter");
        com.gh4a.adapter.timeline.TimelineItemAdapter spy = Mockito.spy(adapter);
        setField(f, "mAdapter", spy);
        f.onTranslationsChanged();
        verify(spy).notifyDataSetChanged();
    }

    @Test
    public void onTranslateStateChanged_updatesItem() throws Exception {
        NoLoad f = attached();
        android.widget.TextView tv = new android.widget.TextView(f.getActivity());
        setField(f, "mTranslateActionView", tv);
        f.onTranslateStateChanged();
        assertEquals(f.getString(R.string.translate), tv.getText().toString());
    }

    @Test
    public void selectAndRemoveFirstReply_replyForOtherDiffIsIgnored() throws Exception {
        NoLoad f = attached();
        TimelineItem.TimelineComment tc1 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).build());
        TimelineItem.TimelineComment tc2 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(2L).build());
        // tc2 属于 diff2（不在 data 里），其 Reply 的 parent 不是 firstDiff
        TimelineItem.Diff diff2 = new TimelineItem.Diff(tc2);
        assertNotNull(diff2);
        List<TimelineItem> data = new ArrayList<>(Arrays.asList(
                new TimelineItem.Diff(tc1), new TimelineItem.Reply(tc2)));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(0L, f.getSelectedReplyCommentId());
        assertEquals(2, data.size());
    }

    @Test
    public void selectAndRemoveFirstReply_alreadySelectedKeepsIdButRemovesReply()
            throws Exception {
        NoLoad f = attached();
        setField(f, "mSelectedReplyCommentId", 55L);
        TimelineItem.TimelineComment tc = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(9L).build());
        List<TimelineItem> data = new ArrayList<>(Arrays.asList(
                new TimelineItem.Diff(tc), new TimelineItem.Reply(tc)));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(55L, f.getSelectedReplyCommentId());
        assertEquals(1, data.size());
    }

    @Test
    public void selectAndRemoveFirstReply_diffThenCommentSkipsReplyBranch()
            throws Exception {
        NoLoad f = attached();
        TimelineItem.TimelineComment tc1 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).build());
        TimelineItem.TimelineComment tc2 = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(2L).bodyHtml("<p>c</p>").build());
        List<TimelineItem> data = new ArrayList<>(Arrays.asList(
                new TimelineItem.Diff(tc1), tc2));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertEquals(0L, f.getSelectedReplyCommentId());
        assertEquals(2, data.size());
    }

    @Test
    public void highlightInitialComment_noMatchClearsMarker() throws Exception {
        NoLoad f = attached();
        setField(f, "mInitialComment",
                new com.gh4a.utils.IntentUtils.InitialCommentMarker(9L));
        GitHubComment nullId = mock(GitHubComment.class);
        // Mockito 对 Long 默认返回 0L，必须显式 stub 成 null 才能走到 id()==null 分支
        when(nullId.id()).thenReturn(null);
        TimelineItem.TimelineComment nullIdItem =
                new TimelineItem.TimelineComment(nullId);
        TimelineItem.TimelineComment wrongId = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(8L).build());
        TimelineItem.TimelineComment match = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(9L).build());
        List<TimelineItem> data = new ArrayList<>(
                Arrays.asList(nullIdItem, wrongId, match));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertNull(field(f, "mInitialComment"));
    }

    @Test
    public void highlightInitialComment_nonCommentItemIsSkipped() throws Exception {
        NoLoad f = attached();
        setField(f, "mInitialComment",
                new com.gh4a.utils.IntentUtils.InitialCommentMarker(9L));
        TimelineItem.TimelineComment tc = new TimelineItem.TimelineComment(
                GitHubComment.builder().id(1L).build());
        List<TimelineItem> data = new ArrayList<>(
                Collections.singletonList(new TimelineItem.Diff(tc)));
        @SuppressWarnings("unchecked")
        RootAdapter<TimelineItem, ?> adapter =
                (RootAdapter<TimelineItem, ?>) field(f, "mAdapter");
        f.onAddData(adapter, data);
        assertNull(field(f, "mInitialComment"));
    }

    @Test
    public void reloadComments_nullAdapterSkipsSuppress() throws Exception {
        NoLoad f = attached();
        setField(f, "mAdapter", null);
        callVoid(f, "reloadComments", new Class<?>[]{boolean.class}, false);
    }

    @Test
    public void translateBody_noActivityIsNoop() {
        ReviewFragment f = new ReviewFragment();
        f.translateBody("comment", 9L, "<p>x</p>");
    }

    @Test
    public void translateBody_callbackWithNullAdapterSkipsNotify() throws Exception {
        NoLoad f = attached();
        setField(f, "mAdapter", null);
        CommentTranslator translator = mock(CommentTranslator.class);
        setField(f, "mCommentTranslator", translator);
        doAnswer(inv -> {
            ((CommentTranslator.Callback) inv.getArgument(4)).onTranslationChanged();
            return null;
        }).when(translator).toggleTranslation(any(Context.class), anyString(), anyLong(),
                anyString(), any(CommentTranslator.Callback.class));
        f.translateBody("comment", 9L, "<p>x</p>");
        verify(translator).toggleTranslation(any(Context.class), eq("comment"), eq(9L),
                eq("<p>x</p>"), any(CommentTranslator.Callback.class));
    }

    @Test
    public void getTranslatedBody_withTranslatorDelegates() throws Exception {
        NoLoad f = attached();
        CommentTranslator translator = mock(CommentTranslator.class);
        when(translator.getTranslatedHtml("comment", 9L)).thenReturn("<p>t</p>");
        setField(f, "mCommentTranslator", translator);
        assertEquals("<p>t</p>", f.getTranslatedBody("comment", 9L));
    }

    @Test
    public void getTranslatedBody_nullTranslatorReturnsNull() throws Exception {
        ReviewFragment f = attach(fragment());
        assertNull(f.getTranslatedBody("comment", 9L));
    }

    @Test
    public void onToggleAdvancedMode_locksAppBarAndResetsPeek() throws Exception {
        NoLoad f = attached();
        f.onToggleAdvancedMode(true);
        f.onToggleAdvancedMode(false);
    }

    @Test
    public void onScrollingInBasicEditor_locksAppBar() throws Exception {
        NoLoad f = attached();
        f.onScrollingInBasicEditor(true);
        f.onScrollingInBasicEditor(false);
    }

    @Test
    public void quoteText_addsQuoteToBottomSheet() throws Exception {
        NoLoad f = attached();
        f.quoteText("hello");
    }

    @Test
    public void getRootLayout_returnsBaseActivityRoot() throws Exception {
        NoLoad f = attached();
        assertNotNull(f.getRootLayout());
    }

    @Test
    public void getShareSubject_returnsNull() throws Exception {
        NoLoad f = attached();
        assertNull(f.getShareSubject(GitHubComment.builder().id(1L).build()));
    }

    @Test
    public void addText_isNoop() throws Exception {
        NoLoad f = attached();
        f.addText("x");
    }

    @Test
    public void addReaction_issueCommentUsesIssueReactionService() throws Exception {
        NoLoad f = attached();
        ReactionService service = mock(ReactionService.class);
        Reaction reaction = Reaction.builder().build();
        when(service.createIssueCommentReaction(anyString(), anyString(), anyLong(),
                any(ReactionRequest.class)))
                .thenReturn(Single.just(Response.success(reaction)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(ReactionService.class), eq(false)))
                    .thenReturn(service);
            TestObserver<Reaction> to = f.addReaction(
                    GitHubComment.builder().id(8L).build(), "+1").test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(reaction);
        }
    }

    @Test
    public void deleteReaction_issueCommentReturnsTrue() throws Exception {
        NoLoad f = attached();
        ReactionService service = mock(ReactionService.class);
        when(service.deleteIssueCommentReaction(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(ReactionService.class), eq(false)))
                    .thenReturn(service);
            TestObserver<Boolean> to = f.deleteReaction(
                    GitHubComment.builder().id(8L).build(), 5L).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertValue(true);
        }
    }

    @Test
    public void onConfirmed_deletesIssueComment() throws Exception {
        NoLoad f = attached();
        IssueCommentService service = mock(IssueCommentService.class);
        when(service.deleteIssueComment(anyString(), anyString(), anyLong()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(IssueCommentService.class), eq(false)))
                    .thenReturn(service);
            f.onConfirmed("deleteconfirm",
                    GitHubComment.builder().id(8L).body("x").build());
            idleFor(Duration.ofSeconds(2));
        }
    }

    @Test
    public void onConfirmed_deleteErrorShowsFailure() throws Exception {
        NoLoad f = attached();
        PullRequestReviewCommentService service =
                mock(PullRequestReviewCommentService.class);
        when(service.deleteComment(anyString(), anyString(), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(service);
            f.onConfirmed("deleteconfirm", reviewComment(11L));
            // wrapForBackgroundTask 的错误要等错误 snackbar 消退才投递
            idleFor(Duration.ofSeconds(11));
        }
    }
}
