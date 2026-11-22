package com.gh4a.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PullRequestFilesFragment：PR 文件列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class PullRequestFilesFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static Object field(PullRequestFilesFragment f, String name) throws Exception {
        Class<?> c = PullRequestFilesFragment.class;
        while (c != null) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(f);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    public void newInstance_storesAllArguments() {
        PullRequestFilesFragment f =
                PullRequestFilesFragment.newInstance("octo", "cat", 42, "abc123");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
        assertEquals(42, f.getArguments().getInt("number"));
        assertEquals("abc123", f.getArguments().getString("head"));
    }

    @Test
    public void onCreate_readsArgumentsIntoFields() throws Exception {
        PullRequestFilesFragment f = attach(
                PullRequestFilesFragment.newInstance("octo", "cat", 42, "abc123"));
        assertEquals("octo", field(f, "mRepoOwner"));
        assertEquals("cat", field(f, "mRepoName"));
        assertEquals(42, field(f, "mPullRequestNumber"));
        assertEquals("abc123", field(f, "mHeadSha"));
    }

    @Test
    public void newInstance_nullHeadShaIsKeptNull() {
        PullRequestFilesFragment f =
                PullRequestFilesFragment.newInstance("o", "r", 1, null);
        assertNotNull(f);
        assertEquals(null, f.getArguments().getString("head"));
    }

    // ---- 缺口补齐：onViewCreated / populateUiIfReady / onRefresh /
    // handleFileClick / loadFiles / loadComments / launcher 回调 ----

    /** 弹 CommentUpdateListener 回调的主机（记录 onCommentsUpdated）。 */
    public static class ListenerHostActivity extends FragmentActivity
            implements PullRequestFilesFragment.CommentUpdateListener {
        static boolean updated;

        @Override
        public void onCommentsUpdated() {
            updated = true;
        }
    }

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

    private static void setField(PullRequestFilesFragment f, String name, Object value)
            throws Exception {
        Field field = PullRequestFilesFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    /** RESUMED 挂载（onViewCreated 会跑加载，需先开 mockStatic）。返回主机。 */
    private static FragmentActivity attachResumed(PullRequestFilesFragment f) {
        FragmentActivity activity =
                Robolectric.buildActivity(FragmentActivity.class).setup().get();
        activity.setTheme(R.style.AppTheme);
        activity.getSupportFragmentManager().beginTransaction()
                .add(f, "test").commitNow();
        return activity;
    }

    private static PullRequestService mockPrServiceNever() {
        PullRequestService service = mock(PullRequestService.class);
        when(service.getPullRequestFiles(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.never());
        return service;
    }

    private static PullRequestReviewCommentService mockCommentServiceNever() {
        PullRequestReviewCommentService service = mock(PullRequestReviewCommentService.class);
        when(service.getPullRequestComments(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.never());
        return service;
    }

    private static MockedStatic<ServiceFactory> mockServices(
            PullRequestService prService, PullRequestReviewCommentService commentService) {
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        if (prService != null) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestService.class), eq(false))).thenReturn(prService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestService.class), eq(true))).thenReturn(prService);
        }
        if (commentService != null) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(commentService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(true)))
                    .thenReturn(commentService);
        }
        return mocked;
    }

    private static GitHubFile mockFile(String name, String status, int add, int del) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.filename()).thenReturn(name);
        when(f.status()).thenReturn(status);
        when(f.additions()).thenReturn(add);
        when(f.deletions()).thenReturn(del);
        when(f.previousFilename()).thenReturn(null);
        when(f.patch()).thenReturn(null);
        return f;
    }

    private static ReviewComment mockComment(String path, Integer position) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.path()).thenReturn(path);
        when(c.position()).thenReturn(position);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static <T> Page<T> pageOf(List<T> items) {
        Page<T> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        when(page.next()).thenReturn(null);
        return page;
    }

    @Test
    public void onViewCreated_hidesHeaderViewsAndLoads() throws Exception {
        PullRequestService prService = mockPrServiceNever();
        PullRequestReviewCommentService commentService = mockCommentServiceNever();
        try (MockedStatic<ServiceFactory> mocked =
                     mockServices(prService, commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            FragmentActivity activity = attachResumed(f);
            View content = (View) field(f, "mContentView");
            assertNotNull(content);
            assertEquals(View.GONE, content.findViewById(R.id.iv_gravatar).getVisibility());
            assertEquals(View.GONE, content.findViewById(R.id.tv_author).getVisibility());
            assertEquals(View.GONE, content.findViewById(R.id.tv_timestamp).getVisibility());
            assertEquals(View.GONE, content.findViewById(R.id.tv_title).getVisibility());
            assertEquals(View.GONE,
                    content.findViewById(R.id.iv_commit_gravatar).getVisibility());
            assertEquals(View.GONE,
                    content.findViewById(R.id.tv_commit_extra).getVisibility());
            assertEquals(View.GONE, content.findViewById(R.id.tv_message).getVisibility());
            assertFalse(f.isContentShown());
            verify(prService, Mockito.timeout(5000))
                    .getPullRequestFiles(eq("octo"), eq("cat"), eq(42L), eq(1L));
            verify(commentService, Mockito.timeout(5000))
                    .getPullRequestComments(eq("octo"), eq("cat"), eq(42L), eq(1L));
        }
    }

    @Test
    public void populateUiIfReady_bothNullIsNoop() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            callPopulateUiIfReady(f);
            assertFalse(f.isContentShown());
        }
    }

    @Test
    public void populateUiIfReady_filesOnlyIsNoop() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            setField(f, "mFiles", Collections.singletonList(
                    mockFile("a.java", "added", 5, 1)));
            callPopulateUiIfReady(f);
            assertFalse(f.isContentShown());
        }
    }

    @Test
    public void populateUiIfReady_commentsOnlyIsNoop() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            setField(f, "mComments", Collections.singletonList(
                    mockComment("a.java", 0)));
            callPopulateUiIfReady(f);
            assertFalse(f.isContentShown());
        }
    }

    @Test
    public void populateUiIfReady_bothSetFillsStatsAndShows() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            FragmentActivity activity = attachResumed(f);
            setField(f, "mFiles", Arrays.asList(
                    mockFile("a.java", "added", 5, 1),
                    mockFile("b.java", "modified", 1, 1)));
            setField(f, "mComments", Collections.singletonList(
                    mockComment("a.java", 0)));
            callPopulateUiIfReady(f);
            assertTrue(f.isContentShown());
            View content = (View) field(f, "mContentView");
            assertEquals(View.VISIBLE,
                    content.findViewById(R.id.card_added).getVisibility());
            assertEquals(View.VISIBLE,
                    content.findViewById(R.id.card_changed).getVisibility());
            assertEquals(View.GONE,
                    content.findViewById(R.id.card_renamed).getVisibility());
            assertEquals(View.GONE,
                    content.findViewById(R.id.card_deleted).getVisibility());
            assertEquals(activity.getString(R.string.commit_summary, 2, 6, 2),
                    ((android.widget.TextView) content.findViewById(R.id.tv_desc))
                            .getText().toString());
        }
    }

    private static void callPopulateUiIfReady(PullRequestFilesFragment f)
            throws Exception {
        java.lang.reflect.Method m =
                PullRequestFilesFragment.class.getDeclaredMethod("populateUiIfReady");
        m.setAccessible(true);
        m.invoke(f);
    }

    @Test
    public void onRefresh_clearsCommentsAndReloads() throws Exception {
        PullRequestService prService = mockPrServiceNever();
        PullRequestReviewCommentService commentService = mockCommentServiceNever();
        try (MockedStatic<ServiceFactory> mocked =
                     mockServices(prService, commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            // 确定性修复（第 3 轮）：初始加载经 RxUtils.doInBackground 在 IO
            // 线程订阅；若 onRefresh 的 force 重载在初始订阅落点前 dispose
            // 掉它，服务调用只有 onRefresh 的 1 次，times(2) 在 chunk 负载
            // 下偶发不足。先等初始调用落点（各 1 次），再 onRefresh，
            // 之后总数必为 2。超时放宽到 10 秒。
            verify(prService, Mockito.timeout(10000).times(1))
                    .getPullRequestFiles(eq("octo"), eq("cat"), eq(42L), eq(1L));
            verify(commentService, Mockito.timeout(10000).times(1))
                    .getPullRequestComments(eq("octo"), eq("cat"), eq(42L), eq(1L));
            setField(f, "mComments", Collections.singletonList(
                    mockComment("a.java", 0)));
            f.onRefresh();
            assertNull(field(f, "mComments"));
            assertFalse(f.isContentShown());
            // 初始加载（force=false）＋ onRefresh（force=true）各一次
            verify(prService, Mockito.timeout(10000).times(2))
                    .getPullRequestFiles(eq("octo"), eq("cat"), eq(42L), eq(1L));
            verify(commentService, Mockito.timeout(10000).times(2))
                    .getPullRequestComments(eq("octo"), eq("cat"), eq(42L), eq(1L));
        }
    }

    @Test
    public void handleFileClick_imageOpensFileViewer() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            FragmentActivity activity = attachResumed(f);
            setField(f, "mComments", Collections.emptyList());
            GitHubFile image = mockFile("pic.png", "added", 0, 0);
            when(image.patch()).thenReturn("@@ -1 +1 @@");
            f.handleFileClick(image);
            Intent intent = Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertNotNull(intent);
            assertTrue(intent.getComponent().getClassName()
                    .contains("FileViewerActivity"));
        }
    }

    @Test
    public void handleFileClick_codeOpensDiffViewer() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            FragmentActivity activity = attachResumed(f);
            setField(f, "mComments", Collections.emptyList());
            GitHubFile code = mockFile("src/Main.java", "modified", 5, 1);
            when(code.patch()).thenReturn("@@ -1 +1 @@");
            f.handleFileClick(code);
            Intent intent = Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertNotNull(intent);
            assertTrue(intent.getComponent().getClassName()
                    .contains("PullRequestDiffViewerActivity"));
        }
    }

    @Test
    public void loadFiles_successSetsFiles() throws Exception {
        PullRequestService prService = mock(PullRequestService.class);
        GitHubFile file = mockFile("a.java", "added", 5, 1);
        Page<GitHubFile> page = pageOf(Collections.singletonList(file));
        when(prService.getPullRequestFiles(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                prService, mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return field(f, "mFiles") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(Collections.singletonList(file), field(f, "mFiles"));
        }
    }

    @Test
    public void loadFiles_errorKeepsNull() throws Exception {
        PullRequestService prService = mock(PullRequestService.class);
        when(prService.getPullRequestFiles(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                prService, mockCommentServiceNever())) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            idleFor(Duration.ofSeconds(2));
            assertNull(field(f, "mFiles"));
        }
    }

    @Test
    public void loadComments_successFiltersByPosition() throws Exception {
        PullRequestReviewCommentService commentService =
                mock(PullRequestReviewCommentService.class);
        ReviewComment cNull = mockComment("a.java", null);
        ReviewComment cNeg = mockComment("a.java", -1);
        ReviewComment c0 = mockComment("a.java", 0);
        ReviewComment c5 = mockComment("b.java", 5);
        Page<ReviewComment> page = pageOf(Arrays.asList(cNull, cNeg, c0, c5));
        when(commentService.getPullRequestComments(
                anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return field(f, "mComments") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(Arrays.asList(c0, c5), field(f, "mComments"));
        }
    }

    @Test
    public void loadComments_errorKeepsNull() throws Exception {
        PullRequestReviewCommentService commentService =
                mock(PullRequestReviewCommentService.class);
        when(commentService.getPullRequestComments(
                anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            attachResumed(f);
            idleFor(Duration.ofSeconds(2));
            assertNull(field(f, "mComments"));
        }
    }

    @Test
    public void diffViewerLauncher_resultOkReloadsCommentsWithoutListener()
            throws Exception {
        PullRequestReviewCommentService commentService = mockCommentServiceNever();
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            FragmentActivity activity = attachResumed(f);
            launchDiffViewer(f, activity);
            idleMain();
            // 初始加载（force=false）＋ 回调重载（force=true）各一次
            verify(commentService, Mockito.timeout(5000).times(2))
                    .getPullRequestComments(eq("octo"), eq("cat"), eq(42L), eq(1L));
        }
    }

    @Test
    public void diffViewerLauncher_resultOkNotifiesListenerHost() throws Exception {
        ListenerHostActivity.updated = false;
        PullRequestReviewCommentService commentService = mockCommentServiceNever();
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockPrServiceNever(), commentService)) {
            PullRequestFilesFragment f = PullRequestFilesFragment.newInstance(
                    "octo", "cat", 42, "abc123");
            ListenerHostActivity activity = Robolectric
                    .buildActivity(ListenerHostActivity.class).setup().get();
            activity.setTheme(R.style.AppTheme);
            activity.getSupportFragmentManager().beginTransaction()
                    .add(f, "test").commitNow();
            launchDiffViewer(f, activity);
            idleMain();
            assertTrue(ListenerHostActivity.updated);
        }
    }

    private static void launchDiffViewer(PullRequestFilesFragment f,
            FragmentActivity activity) throws Exception {
        Field launcherField = PullRequestFilesFragment.class
                .getDeclaredField("mDiffViewerLauncher");
        launcherField.setAccessible(true);
        @SuppressWarnings("unchecked")
        androidx.activity.result.ActivityResultLauncher<Intent> launcher =
                (androidx.activity.result.ActivityResultLauncher<Intent>)
                        launcherField.get(f);
        launcher.launch(new Intent(activity,
                com.gh4a.activities.FileViewerActivity.class));
        idleMain();
        android.content.Intent started = Shadows.shadowOf(activity)
                .getNextStartedActivityForResult().intent;
        Shadows.shadowOf(activity).receiveResult(
                started, Activity.RESULT_OK, new Intent());
        idleMain();
    }
}
