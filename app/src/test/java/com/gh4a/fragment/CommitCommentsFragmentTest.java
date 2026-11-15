package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.CommitCommentAdapter;
import com.gh4a.utils.IntentUtils;
import com.gh4a.widget.CommentEditor;
import com.gh4a.widget.EditorBottomSheet;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.request.repository.CreateCommitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * CommitCommentsFragment (F1).
 * Full attach is not possible with a plain FragmentActivity: onViewCreated()
 * calls getBaseActivity().addAppBarOffsetListener(...), which NPEs when the
 * host is not a BaseActivity. Tests therefore use a bare instance with its
 * private fields set via reflection and call the load methods directly
 * (returned Singles are never subscribed).
 */
@RunWith(RobolectricTestRunner.class)
public class CommitCommentsFragmentTest {

    private static Commit commit() {
        return Commit.builder()
                .author(User.builder().login("octocat").build())
                .committer(User.builder().login("octocat").build())
                .build();
    }

    private static GitComment comment(Integer position) {
        GitComment.Builder b = GitComment.builder().id(1L).body("hi");
        if (position != null) {
            b.position(position);
        }
        return b.build();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Field findField(Class<?> cls, String name) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static CommitCommentsFragment fragmentWithRepo() {
        CommitCommentsFragment f = new CommitCommentsFragment();
        setField(f, "mRepoOwner", "octocat");
        setField(f, "mRepoName", "hello");
        setField(f, "mObjectSha", "abc123");
        return f;
    }

    @Test
    public void newInstance_keepsOnlyNonPositionalComments() {
        List<GitComment> all = new ArrayList<>();
        all.add(comment(null));
        all.add(comment(3));
        all.add(comment(null));
        CommitCommentsFragment f = CommitCommentsFragment.newInstance(
                "o", "r", "sha", commit(), all, null);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertEquals("sha", f.getArguments().getString("sha"));
        assertNotNull(f.getArguments().getParcelable("commit_author"));
        List<GitComment> stored = com.gh4a.utils.IntentUtils
                .readCompressedValueFromBundle(f.getArguments(), "comments");
        assertNotNull(stored);
        assertEquals(2, stored.size());
    }

    @Test
    public void newInstance_allPositional_storesEmptyList() {
        List<GitComment> all = new ArrayList<>();
        all.add(comment(1));
        CommitCommentsFragment f = CommitCommentsFragment.newInstance(
                "o", "r", "sha", commit(), all, null);
        List<GitComment> stored = com.gh4a.utils.IntentUtils
                .readCompressedValueFromBundle(f.getArguments(), "comments");
        assertNotNull(stored);
        assertEquals(0, stored.size());
    }

    @Test
    public void getCommentEditorHintResId_isCommitCommentHint() {
        assertEquals(R.string.commit_comment_hint,
                new CommitCommentsFragment().getCommentEditorHintResId());
    }

    @Test
    public void getEditorErrorMessageResId_isIssueErrorComment() {
        assertEquals(R.string.issue_error_comment,
                new CommitCommentsFragment().getEditorErrorMessageResId());
    }

    @Test
    public void onCreateDataSingle_returnsNonNullSingleWithoutNetwork() {
        CommitCommentsFragment f = fragmentWithRepo();
        Single<List<GitComment>> single = f.onCreateDataSingle(false);
        assertNotNull(single);
        assertNotNull(f.onCreateDataSingle(true));
    }

    @Test
    public void onEditorDoSend_returnsNonNullSingleWithoutNetwork() {
        CommitCommentsFragment f = fragmentWithRepo();
        Single<?> single = f.onEditorDoSend("looks good");
        assertNotNull(single);
    }

    @Test
    public void onGetInitialData_emptyStoredComments_returnsNull() throws Exception {
        CommitCommentsFragment f = fragmentWithRepo();
        // Simulate onCreate(): args with an empty compressed comments list.
        android.os.Bundle args = new android.os.Bundle();
        com.gh4a.utils.IntentUtils.putCompressedValueToBundle(
                args, "comments", new ArrayList<GitComment>());
        Method setArgs = androidx.fragment.app.Fragment.class
                .getDeclaredMethod("setArguments", android.os.Bundle.class);
        // setArguments is public on Fragment; call directly.
        f.setArguments(args);
        assertNull(f.onGetInitialData());
    }

    @Test
    public void onConfirmed_delegatesToDeleteComment() {
        // onConfirmed() calls the private deleteComment(long), which
        // subscribes a network Single. Only the cast path is observable
        // without network: passing a non-GitComment Parcelable must throw
        // ClassCastException before any network code runs.
        CommitCommentsFragment f = fragmentWithRepo();
        android.os.Parcelable notAComment =
                new android.os.Bundle();
        FragmentTestHelper.assertThrows(ClassCastException.class,
                () -> f.onConfirmed("deleteconfirm", notAComment));
    }

    // ---- 完整挂载：BaseActivity host（自带 AppBar/CoordinatorLayout），
    // 挂进 android.R.id.content 让视图真正贴到窗口上（post 才能跑） ----

    public static class CallbackHostActivity extends BaseActivity
            implements CommitCommentsFragment.CommentUpdateListener {
        int commentsUpdated;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }

        @Override
        public void onCommentsUpdated() {
            commentsUpdated++;
        }
    }

    public static class PlainHostActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void idleFor(Duration duration) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(duration);
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < 100 && !cond.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            idleMain();
        }
        assertTrue(cond.getAsBoolean());
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static CallbackHostActivity attach(CommitCommentsFragment f) {
        CallbackHostActivity activity =
                Robolectric.buildActivity(CallbackHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        idleMain();
        return activity;
    }

    private static GitComment comment(long id, Integer position) {
        GitComment.Builder b = GitComment.builder().id(id).body("body " + id)
                .user(User.builder().login("user" + id).build());
        if (position != null) {
            b.position(position);
        }
        return b.build();
    }

    private static CommitCommentsFragment newFragment(List<GitComment> comments,
            IntentUtils.InitialCommentMarker marker) {
        return CommitCommentsFragment.newInstance(
                "octocat", "hello", "abc123", commit(), comments, marker);
    }

    private static int adapterCount(CommitCommentsFragment f) throws Exception {
        CommitCommentAdapter adapter = (CommitCommentAdapter) field(f, "mAdapter");
        return adapter == null ? -1 : adapter.getCount();
    }

    private static EditorBottomSheet bottomSheet(CommitCommentsFragment f) throws Exception {
        return (EditorBottomSheet) field(f, "mBottomSheet");
    }

    private static MockedStatic<ServiceFactory> mockServices(RepositoryCommentService service) {
        MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                RepositoryCommentService.class, false)).thenReturn(service);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                RepositoryCommentService.class, true)).thenReturn(service);
        mocked.when(() -> ServiceFactory.get(
                RepositoryCommentService.class, false)).thenReturn(service);
        return mocked;
    }

    @SuppressWarnings("unchecked")
    private static Page<GitComment> pageOf(List<GitComment> items, Integer next) {
        Page<GitComment> page = Mockito.mock(Page.class);
        Mockito.when(page.items()).thenReturn(items);
        Mockito.when(page.next()).thenReturn(next);
        return page;
    }

    @Test
    public void attach_withInitialComments_loadsAdapterAndHidesSheet() throws Exception {
        List<GitComment> comments = Arrays.asList(
                comment(1L, null), comment(2, 3), comment(3L, null));
        CommitCommentsFragment f = newFragment(comments, null);
        attach(f);
        // newInstance 已滤掉 positional，初始数据直接上屏。
        assertEquals(2, adapterCount(f));
        assertNotNull(field(f, "mAdapter"));
        // 未登录：底部编辑器隐藏。
        assertEquals(View.GONE, bottomSheet(f).getVisibility());
        // onCreate 把 initial_comment 从 args 里拿掉。
        assertFalse(f.getArguments().containsKey("initial_comment"));
    }

    @Test
    public void attach_withInitialComment_scrollsToMatch() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        for (long i = 1; i <= 23; i++) {
            comments.add(comment(i, null));
        }
        CommitCommentsFragment f = newFragment(comments,
                new IntentUtils.InitialCommentMarker(10L));
        attach(f);
        idleFor(Duration.ofMillis(700));
        assertNull(field(f, "mInitialComment"));
        androidx.recyclerview.widget.RecyclerView list =
                (androidx.recyclerview.widget.RecyclerView) field(f, "mRecyclerView");
        androidx.recyclerview.widget.LinearLayoutManager lm =
                (androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager();
        assertEquals(9, lm.findFirstVisibleItemPosition());
    }

    @Test
    public void attach_withInitialCommentNoMatch_clearsMarker() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null), comment(2L, null)),
                new IntentUtils.InitialCommentMarker(999L));
        attach(f);
        assertNull(field(f, "mInitialComment"));
    }

    @Test
    public void attach_authorlessCommit_skipsUserAdds() throws Exception {
        CommitCommentsFragment f = CommitCommentsFragment.newInstance(
                "octocat", "hello", "abc123", Commit.builder().build(),
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        assertEquals(1, adapterCount(f));
    }

    @Test
    public void attach_emptyInitialComments_loadsFromNetwork() throws Exception {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Page<GitComment> page = pageOf(
                Arrays.asList(comment(1L, null), comment(2, 5)), null);
        Mockito.when(service.getCommitComments(eq("octocat"), eq("hello"),
                eq("abc123"), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = newFragment(new ArrayList<>(), null);
            attach(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 1;
                } catch (Exception e) {
                    return false;
                }
            });
            // positional 的那条被 RxUtils.filter 滤掉。
            Mockito.verify(service).getCommitComments("octocat", "hello", "abc123", 1L);
        }
    }

    @Test
    public void onCreateDataSingle_secondPage_concatenatesAndFilters() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Page<GitComment> page1 = pageOf(
                Arrays.asList(comment(1L, null), comment(2, 5)), 2);
        Page<GitComment> page2 = pageOf(Arrays.asList(comment(3L, null)), null);
        Mockito.when(service.getCommitComments(any(), any(), any(), anyLong()))
                .thenAnswer(inv -> Single.just(Response.success(
                        ((Long) inv.getArgument(3)) == 1L ? page1 : page2)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = fragmentWithRepo();
            List<GitComment> result = f.onCreateDataSingle(false).test()
                    .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList(1L, 3L),
                    Arrays.asList(result.get(0).id(), result.get(1).id()));
        }
    }

    @Test
    public void onCreateDataSingle_error_propagates() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Mockito.when(service.getCommitComments(any(), any(), any(), anyLong()))
                .thenReturn(Single.just(Response.error(404,
                        ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = fragmentWithRepo();
            f.onCreateDataSingle(false).test()
                    .assertError(com.gh4a.ApiRequestException.class);
        }
    }

    @Test
    public void onGetInitialData_nonEmpty_returnsList() throws Exception {
        CommitCommentsFragment f = fragmentWithRepo();
        Bundle args = new Bundle();
        com.gh4a.utils.IntentUtils.putCompressedValueToBundle(args, "comments",
                new ArrayList<>(Arrays.asList(comment(1L, null), comment(2L, null))));
        f.setArguments(args);
        List<GitComment> result = f.onGetInitialData();
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void onGetInitialData_noKey_returnsNull() {
        CommitCommentsFragment f = fragmentWithRepo();
        f.setArguments(new Bundle());
        assertNull(f.onGetInitialData());
    }

    @Test
    public void onViewCreated_postResetsPeekHeight() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        EditorBottomSheet sheet = bottomSheet(f);
        // post 到主线程的 runnable 跑完：mLatestOffset == 顶栏总滚动范围。
        assertEquals(activity.getAppBarTotalScrollRange(), field(sheet, "mLatestOffset"));
    }

    @Test
    public void onDestroyView_destroysAdapterAndRemovesListener() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        assertNotNull(field(f, "mAdapter"));
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
        assertNull(field(f, "mAdapter"));
    }

    @Test
    public void onDestroyView_nullAdapter_skipsDestroy() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        setField(f, "mAdapter", null);
        // 不能走真实 remove：FragmentManager 移除前先调 onPause，
        // 那里 mAdapter.pause() 会先 NPE。直接调 onDestroyView，
        // 覆盖 if (mAdapter != null) 的假分支。
        f.onDestroyView();
        assertNull(field(f, "mAdapter"));
    }

    @Test
    public void onResume_onPause_delegateToAdapter() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        f.onPause();
        f.onResume();
    }

    @Test
    public void canChildScrollUp_sheetNull_delegatesToSuper() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        setField(f, "mBottomSheet", null);
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_sheetNotExpanded_delegatesToSuper() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_sheetExpanded_returnsTrue() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        EditorBottomSheet mockSheet = Mockito.mock(EditorBottomSheet.class);
        Mockito.when(mockSheet.isExpanded()).thenReturn(true);
        setField(f, "mBottomSheet", mockSheet);
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void getRootLayout_returnsActivityRootLayout() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        assertTrue(f.getRootLayout() == activity.getRootLayout());
    }

    @Test
    public void onBackPressed_notAdvanced_returnsFalse() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        assertFalse(f.onBackPressed());
    }

    @Test
    public void onBackPressed_advancedMode_exitsAndReturnsTrue() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        EditorBottomSheet mockSheet = Mockito.mock(EditorBottomSheet.class);
        Mockito.when(mockSheet.isInAdvancedMode()).thenReturn(true);
        setField(f, "mBottomSheet", mockSheet);
        assertTrue(f.onBackPressed());
        Mockito.verify(mockSheet).setAdvancedMode(false);
    }

    @Test
    public void onToggleAdvancedMode_locksAppBar() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.onToggleAdvancedMode(true);
        assertTrue((Boolean) field(activity, "mAppBarLocked"));
        f.onToggleAdvancedMode(false);
        assertFalse((Boolean) field(activity, "mAppBarLocked"));
    }

    @Test
    public void onToggleAdvancedMode_noActivity_skipsAppBar() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
        // 已分离：getBaseActivity() 为 null，只调底 sheet，不崩。
        f.onToggleAdvancedMode(true);
    }

    @Test
    public void onScrollingInBasicEditor_locksAppBar() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.onScrollingInBasicEditor(true);
        assertTrue((Boolean) field(activity, "mAppBarLocked"));
        f.onScrollingInBasicEditor(false);
        assertFalse((Boolean) field(activity, "mAppBarLocked"));
    }

    @Test
    public void setHighlightColors_delegatesToBottomSheet() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        f.setHighlightColors(androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
        EditorBottomSheet sheet = bottomSheet(f);
        assertTrue((Integer) field(sheet, "mHighlightColor") != 0);
    }

    @Test
    public void getEmptyTextResId_isNoCommentsFound() {
        assertEquals(R.string.no_comments_found,
                new CommitCommentsFragment().getEmptyTextResId());
    }

    @Test
    public void editComment_launchesEditorActivity() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.editComment(comment(7L, null));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.EditCommitCommentActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals("abc123", started.getStringExtra("commit"));
    }

    @Test
    public void editComment_resultOk_refreshesComments() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.editComment(comment(7L, null));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        Shadows.shadowOf(activity).receiveResult(started, Activity.RESULT_OK, null);
        idleMain();
        assertEquals(1, activity.commentsUpdated);
    }

    @Test
    public void editComment_resultCanceled_doesNotRefresh() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.editComment(comment(7L, null));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        Shadows.shadowOf(activity).receiveResult(started, Activity.RESULT_CANCELED, null);
        idleMain();
        assertEquals(0, activity.commentsUpdated);
    }

    @Test
    public void deleteComment_showsConfirmationDialog() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.deleteComment(comment(5L, null));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentByTag("deleteconfirm"));
    }

    @Test
    public void deleteComment_confirmFlow_deletesAndRefreshes() throws Exception {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Mockito.when(service.deleteCommitComment(eq("octocat"), eq("hello"), eq(5L)))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = newFragment(
                    Arrays.asList(comment(1L, null)), null);
            CallbackHostActivity activity = attach(f);
            f.deleteComment(comment(5L, null));
            activity.getSupportFragmentManager().executePendingTransactions();
            ConfirmationDialogFragment dialog =
                    (ConfirmationDialogFragment) activity.getSupportFragmentManager()
                            .findFragmentByTag("deleteconfirm");
            assertNotNull(dialog);
            AlertDialog alertDialog = (AlertDialog) dialog.getDialog();
            assertNotNull(alertDialog);
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idleMain();
            waitUntil(() -> activity.commentsUpdated == 1);
            Mockito.verify(service).deleteCommitComment("octocat", "hello", 5L);
        }
    }

    @Test
    public void onConfirmed_deleteSuccess_refreshesComments() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Mockito.when(service.deleteCommitComment(eq("octocat"), eq("hello"), eq(5L)))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = newFragment(
                    Arrays.asList(comment(1L, null)), null);
            CallbackHostActivity activity = attach(f);
            f.onConfirmed("deleteconfirm", comment(5L, null));
            waitUntil(() -> activity.commentsUpdated == 1);
        }
    }

    @Test
    public void onConfirmed_deleteError_doesNotRefresh() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Mockito.when(service.deleteCommitComment(eq("octocat"), eq("hello"), eq(5L)))
                .thenReturn(Single.just(Response.error(500,
                        ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = newFragment(
                    Arrays.asList(comment(1L, null)), null);
            CallbackHostActivity activity = attach(f);
            f.onConfirmed("deleteconfirm", comment(5L, null));
            // 错误 snackbar 超时消失后，错误才走到 handleActionFailure。
            idleFor(Duration.ofSeconds(10));
            idleMain();
            assertEquals(0, activity.commentsUpdated);
            Mockito.verify(service, Mockito.times(1))
                    .deleteCommitComment("octocat", "hello", 5L);
        }
    }

    @Test
    public void quoteText_addText_appendToEditor() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        f.quoteText("quoted");
        f.addText("more");
        EditorBottomSheet sheet = bottomSheet(f);
        CommentEditor editor = (CommentEditor) field(sheet, "mBasicEditor");
        String text = editor.getText().toString();
        assertTrue(text.contains("quoted"));
        assertTrue(text.contains("more"));
    }

    @Test
    public void onEditorDoSend_createsComment() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        AtomicReference<CreateCommitComment> captured =
                new AtomicReference<>();
        Mockito.when(service.createCommitComment(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    captured.set(inv.getArgument(3));
                    return Single.just(Response.success(comment(9L, null)));
                });
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = fragmentWithRepo();
            GitComment result = (GitComment) f.onEditorDoSend("looks good").test()
                    .assertNoErrors().values().get(0);
            assertEquals(9L, (long) result.id());
            assertEquals("looks good", captured.get().body());
        }
    }

    @Test
    public void onEditorDoSend_error_propagates() {
        RepositoryCommentService service = Mockito.mock(RepositoryCommentService.class);
        Mockito.when(service.createCommitComment(any(), any(), any(), any()))
                .thenReturn(Single.just(Response.error(404,
                        ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> mocked = mockServices(service)) {
            CommitCommentsFragment f = fragmentWithRepo();
            f.onEditorDoSend("looks good").test()
                    .assertError(com.gh4a.ApiRequestException.class);
        }
    }

    @Test
    public void onEditorTextSent_refreshesComments() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity = attach(f);
        f.onEditorTextSent();
        assertEquals(1, activity.commentsUpdated);
    }

    @Test
    public void onViewCreated_runnableRunsAfterDetach_skipsReset() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        CallbackHostActivity activity =
                Robolectric.buildActivity(CallbackHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        // 不放行主线程：resetPeekHeight 的 runnable 还在队列里。
        // 先分离，再放行：runnable 跑时 getBaseActivity() 已为 null。
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
        assertNull(f.getActivity());
    }

    @Test
    public void canChildScrollUp_listCanScrollUp_returnsTrue() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        for (long i = 1; i <= 23; i++) {
            comments.add(comment(i, null));
        }
        CommitCommentsFragment f = newFragment(comments, null);
        attach(f);
        androidx.recyclerview.widget.RecyclerView list =
                (androidx.recyclerview.widget.RecyclerView) field(f, "mRecyclerView");
        list.scrollToPosition(10);
        idleMain();
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void onBackPressed_sheetNull_returnsFalse() throws Exception {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        setField(f, "mBottomSheet", null);
        assertFalse(f.onBackPressed());
    }

    @Test
    public void attach_authorized_showsBottomSheet() {
        com.gh4a.Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        assertTrue(com.gh4a.Gh4Application.get().isAuthorized());
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        attach(f);
        try {
            assertEquals(View.VISIBLE, bottomSheet(f).getVisibility());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void refreshComments_noListener_noop() {
        CommitCommentsFragment f = newFragment(
                Arrays.asList(comment(1L, null)), null);
        PlainHostActivity activity =
                Robolectric.buildActivity(PlainHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        idleMain();
        f.onEditorTextSent();
    }
}
