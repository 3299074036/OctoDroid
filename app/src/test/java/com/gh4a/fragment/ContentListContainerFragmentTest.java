package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.BaseActivity;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.Content;
import com.meisolsson.githubsdk.model.ContentType;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.repositories.RepositoryContentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import io.reactivex.Single;
import retrofit2.Response;

/** ContentListContainerFragment (F1): newInstance + .gitmodules parsing.
 * Full attach would subscribe the content-list network load, so view
 * behavior is intentionally left to manual verification. */
@RunWith(RobolectricTestRunner.class)
public class ContentListContainerFragmentTest {

    public static class CallbackHostActivity extends BaseActivity
            implements ContentListContainerFragment.CommitSelectionCallback {
        Commit selectedCommit;
        // loadModuleMap 的 error 消费者（activity::handleLoadFailure）：
        // 真机布局里有 R.id.content，测试宿主是空 FrameLayout，super 会 NPE；
        // 重写只记录，不调 super。
        Throwable loadFailure;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }

        @Override
        public void onCommitSelectedAsBase(Commit commit) {
            selectedCommit = commit;
        }

        @Override
        public void handleLoadFailure(Throwable e) {
            loadFailure = e;
        }
    }

    public static class PlainHostActivity extends FragmentActivity
            implements ContentListContainerFragment.CommitSelectionCallback {
        @Override
        public void onCommitSelectedAsBase(Commit commit) {
        }
    }

    private static Repository repo() {
        return Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").defaultBranch("main").build();
    }

    private static Content content(String name, String path, ContentType type) {
        Content c = mock(Content.class);
        when(c.name()).thenReturn(name);
        when(c.path()).thenReturn(path);
        when(c.type()).thenReturn(type);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Page<Content> pageOf(List<Content> items) {
        Page<Content> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        when(page.next()).thenReturn(null);
        return page;
    }

    // 子列表加载走 RepositoryContentService.getDirectoryContents（PageIterator
    // 读 next() 为 null 即停页）；getContentsRaw 供 .gitmodules 用。
    private static MockedStatic<ServiceFactory> mockContentService(
            RepositoryContentService service) {
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                .thenReturn(service);
        mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, true))
                .thenReturn(service);
        return mocked;
    }

    private static RepositoryContentService serviceReturning(List<Content> contents) {
        RepositoryContentService service = mock(RepositoryContentService.class);
        Page<Content> page = pageOf(contents);
        when(service.getDirectoryContents(any(), any(), any(), any(), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        return service;
    }

    // 加载永不投递：读 onCreate 里解析出的初始路径，不被 onContentsLoaded 改掉。
    private static RepositoryContentService serviceNever() {
        RepositoryContentService service = mock(RepositoryContentService.class);
        when(service.getDirectoryContents(any(), any(), any(), any(), anyLong()))
                .thenReturn(Single.never());
        return service;
    }

    // 按路径返回不同内容（初始路径链式导航用）。
    private static RepositoryContentService serviceByPath(
            List<Content> rootContents, List<Content> docsContents) {
        RepositoryContentService service = mock(RepositoryContentService.class);
        Page<Content> rootPage = pageOf(rootContents);
        Page<Content> docsPage = pageOf(docsContents);
        when(service.getDirectoryContents(any(), any(), any(), any(), anyLong()))
                .thenAnswer(inv -> {
                    String path = inv.getArgument(2);
                    return Single.just(Response.success(
                            "docs".equals(path) ? docsPage : rootPage));
                });
        return service;
    }

    private static CallbackHostActivity attach(ContentListContainerFragment f) {
        CallbackHostActivity activity =
                FragmentTestHelper.attachTo(CallbackHostActivity.class, f);
        f.getChildFragmentManager().executePendingTransactions();
        idleMain();
        return activity;
    }

    private static PlainHostActivity attachPlain(ContentListContainerFragment f) {
        PlainHostActivity activity =
                FragmentTestHelper.attachTo(PlainHostActivity.class, f);
        f.getChildFragmentManager().executePendingTransactions();
        idleMain();
        return activity;
    }

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMain();
        }
    }

    // 子列表投递后 onContentsLoaded 把根路径写进缓存，以此等加载完成。
    private static void waitForContents(ContentListContainerFragment f) throws Exception {
        waitUntil(() -> {
            try {
                ContentListCacheFragment cache =
                        (ContentListCacheFragment) field(f, "mCacheFragment");
                return cache.getFromCache("") != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field fld = c.getDeclaredField(name);
                fld.setAccessible(true);
                return fld.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field fld = c.getDeclaredField(name);
                fld.setAccessible(true);
                fld.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    private static Stack<String> dirStack(ContentListContainerFragment f) throws Exception {
        return (Stack<String>) field(f, "mDirStack");
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<String> initialPathToLoad(ContentListContainerFragment f)
            throws Exception {
        return (ArrayList<String>) field(f, "mInitialPathToLoad");
    }

    // ---- Chunk A：onCreate / onViewCreated / onSaveInstanceState ----

    @Test
    public void onCreate_noInitialPath_stackRootOnly() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            // onCreate 里读的字段：挂载后立刻读，子列表异步加载还没跑。
            assertEquals(1, dirStack(f).size());
            assertEquals("", dirStack(f).peek());
            assertNull(initialPathToLoad(f));
        }
    }

    @Test
    public void onCreate_initialPath_parsesSegments() throws Exception {
        RepositoryContentService service = serviceNever();
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", "docs/guide");
            attach(f);
            assertEquals(Arrays.asList("docs", "docs/guide"), initialPathToLoad(f));
        }
    }

    @Test
    public void onCreate_initialPathSingleSegment_oneEntry() throws Exception {
        RepositoryContentService service = serviceNever();
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", "docs");
            attach(f);
            assertEquals(Arrays.asList("docs"), initialPathToLoad(f));
        }
    }

    @Test
    public void onCreate_restore_restoresStackAndPath() throws Exception {
        RepositoryContentService service = serviceNever();
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            Bundle saved = new Bundle();
            saved.putStringArrayList("dir_stack", new ArrayList<>(Arrays.asList("", "docs")));
            saved.putStringArrayList("initial_path",
                    new ArrayList<>(Arrays.asList("docs/guide")));
            f.setInitialSavedState(savedState(saved));
            attach(f);
            assertEquals(Arrays.asList("", "docs"), new ArrayList<>(dirStack(f)));
            assertEquals(Arrays.asList("docs/guide"), initialPathToLoad(f));
        }
    }

    private static androidx.fragment.app.Fragment.SavedState savedState(Bundle bundle)
            throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> ctor =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(Bundle.class);
        ctor.setAccessible(true);
        return ctor.newInstance(bundle);
    }

    @Test
    public void onCreateView_inflatesBreadcrumbsAndContainer() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            assertNotNull(field(f, "mBreadcrumbs"));
            assertNotNull(f.getView().findViewById(com.gh4a.R.id.content_list_container));
        }
    }

    @Test
    public void onSaveInstanceState_savesStackAndPath() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", "docs/guide");
            attach(f);
            waitForContents(f);
            // 根目录没 "docs"：初始路径匹配失败，被置 null；栈仍是根。
            assertNull(initialPathToLoad(f));
            Bundle out = new Bundle();
            f.onSaveInstanceState(out);
            assertEquals(Arrays.asList(""), out.getStringArrayList("dir_stack"));
            assertNull(out.getStringArrayList("initial_path"));
            assertTrue((Boolean) field(f, "mStateSaved"));
        }
    }

    @Test
    public void canChildScrollUp_noChild_returnsFalse() {
        assertFalse(new ContentListContainerFragment().canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_withChild_delegates() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            ContentListFragment mockChild = mock(ContentListFragment.class);
            when(mockChild.canChildScrollUp()).thenReturn(true);
            setField(f, "mContentListFragment", mockChild);
            assertTrue(f.canChildScrollUp());
            when(mockChild.canChildScrollUp()).thenReturn(false);
            assertFalse(f.canChildScrollUp());
        }
    }

    @Test
    public void onRefresh_resetsToRef() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            // 先进一个目录，再刷新：栈清回根、缓存清空。
            ContentListFragment child =
                    (ContentListFragment) field(f, "mContentListFragment");
            assertNotNull(child);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            assertEquals(2, dirStack(f).size());
            f.onRefresh();
            assertEquals(1, dirStack(f).size());
            assertEquals("", dirStack(f).peek());
            assertNull(((ContentListCacheFragment) field(f, "mCacheFragment"))
                    .getFromCache(""));
            assertEquals("main", field(f, "mSelectedRef"));
        }
    }

    // ---- Chunk B：setRef / handleBackPress / onTreeSelected / 面包屑 / 子模块名 ----

    @Test
    public void setRef_changesRefAndResetsStack() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            assertEquals(2, dirStack(f).size());
            f.setRef("dev");
            assertEquals("dev", field(f, "mSelectedRef"));
            assertEquals("dev", f.getArguments().getString("ref"));
            // 旧栈 [\"\", \"docs\"] 变成待加载路径，栈清回根。
            assertEquals(Arrays.asList("docs"), initialPathToLoad(f));
            assertEquals(1, dirStack(f).size());
            assertEquals("", dirStack(f).peek());
            assertNull(field(f, "mGitModuleMap"));
            assertNull(((ContentListCacheFragment) field(f, "mCacheFragment"))
                    .getFromCache(""));
        }
    }

    @Test
    public void handleBackPress_atRoot_returnsFalse() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            assertFalse(f.handleBackPress());
            assertEquals(1, dirStack(f).size());
        }
    }

    @Test
    public void handleBackPress_inDir_popsAndReturnsTrue() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            f.getChildFragmentManager().executePendingTransactions();
            idleMain();
            assertEquals(2, dirStack(f).size());
            assertTrue(f.handleBackPress());
            assertEquals(1, dirStack(f).size());
            assertEquals("", dirStack(f).peek());
            // 回退后当前子列表是根目录那一个。
            ContentListFragment child =
                    (ContentListFragment) field(f, "mContentListFragment");
            assertEquals("", child.getPath());
        }
    }

    @Test
    public void onTreeSelected_directory_pushesStackAndChild() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            assertEquals(Arrays.asList("", "docs"), new ArrayList<>(dirStack(f)));
            f.getChildFragmentManager().executePendingTransactions();
            idleMain();
            ContentListFragment child =
                    (ContentListFragment) field(f, "mContentListFragment");
            assertEquals("docs", child.getPath());
        }
    }

    @Test
    public void onTreeSelected_file_opensFileViewer() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(com.gh4a.activities.FileViewerActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("octocat", started.getStringExtra("owner"));
            assertEquals("hello", started.getStringExtra("repo"));
            assertEquals("README.md", started.getStringExtra("path"));
            assertEquals("main", started.getStringExtra("ref"));
        }
    }

    @Test
    public void onTreeSelected_fileInModuleMap_opensRepository() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            java.util.Map<String, String> moduleMap = new java.util.HashMap<>();
            moduleMap.put("lib/foo", "octocat/hello");
            setField(f, "mGitModuleMap", moduleMap);
            f.onTreeSelected(content("foo", "lib/foo", ContentType.File));
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("octocat", started.getStringExtra("owner"));
            assertEquals("hello", started.getStringExtra("repo"));
        }
    }

    @Test
    public void onTreeSelected_fileWithNullRepoChain_noIntent() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            setField(f, "mRepository", null);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void onCommitSelected_delegatesToHost() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            Commit commit = mock(Commit.class);
            f.onCommitSelected(commit);
            assertTrue(activity.selectedCommit == commit);
        }
    }

    @Test
    public void getSubModuleNames_nullMap_returnsEmpty() {
        assertTrue(new ContentListContainerFragment()
                .getSubModuleNames(mock(ContentListFragment.class)).isEmpty());
    }

    @Test
    public void getSubModuleNames_rootAndSubdir() throws Exception {
        java.util.Map<String, String> moduleMap = new java.util.HashMap<>();
        moduleMap.put("lib/foo", "o/r");
        moduleMap.put("lib/bar/baz", "o/r2");
        moduleMap.put("top", "o/r3");
        ContentListContainerFragment f = new ContentListContainerFragment();
        setField(f, "mGitModuleMap", moduleMap);
        ContentListFragment root = mock(ContentListFragment.class);
        when(root.getPath()).thenReturn("");
        assertEquals(new java.util.HashSet<>(Arrays.asList("top")),
                f.getSubModuleNames(root));
        ContentListFragment sub = mock(ContentListFragment.class);
        when(sub.getPath()).thenReturn("lib");
        assertEquals(new java.util.HashSet<>(Arrays.asList("foo", "bar/baz")),
                f.getSubModuleNames(sub));
    }

    @Test
    public void onCrumbSelection_popsToCrumb() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            f.onTreeSelected(content("sub", "docs/sub", ContentType.Directory));
            assertEquals(3, dirStack(f).size());
            f.onCrumbSelection("", 0, 1);
            assertEquals(1, dirStack(f).size());
            assertEquals("", dirStack(f).peek());
        }
    }

    @Test
    public void onCrumbSelection_atCurrentTop_noPop() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            assertEquals(2, dirStack(f).size());
            // 目标就是栈顶：一个也不弹。
            f.onCrumbSelection("docs", 1, 2);
            assertEquals(2, dirStack(f).size());
            assertEquals("docs", dirStack(f).peek());
        }
    }

    // ---- Chunk C：onContentsLoaded（缓存 / .gitmodules / 初始路径）----

    private static void waitForPath(ContentListContainerFragment f, String path)
            throws Exception {
        waitUntil(() -> {
            try {
                ContentListCacheFragment cache =
                        (ContentListCacheFragment) field(f, "mCacheFragment");
                return cache.getFromCache(path) != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Test
    public void onContentsLoaded_nullContents_returns() throws Exception {
        ContentListContainerFragment f = new ContentListContainerFragment();
        ContentListFragment child = mock(ContentListFragment.class);
        // contents == null 时第一行就返回，不碰 cache。
        f.onContentsLoaded(child, null);
    }

    @Test
    public void onContentsLoaded_rootContents_cached() throws Exception {
        Content file = content("README.md", "README.md", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(file));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            ContentListCacheFragment cache =
                    (ContentListCacheFragment) field(f, "mCacheFragment");
            assertEquals(1, cache.getFromCache("").size());
            assertTrue(cache.getFromCache("").get(0) == file);
        }
    }

    @Test
    public void onContentsLoaded_docsContents_cachedByPath() throws Exception {
        Content file = content("README.md", "README.md", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(file));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            waitForPath(f, "docs");
            ContentListCacheFragment cache =
                    (ContentListCacheFragment) field(f, "mCacheFragment");
            assertEquals(1, cache.getFromCache("docs").size());
        }
    }

    @Test
    public void onContentsLoaded_rootGitmodules_triggersModuleMapLoad() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(gitmodules));
        String gitmodulesText = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat/hello.git\n";
        when(service.getContentsRaw(any(), any(), any(), any())).thenReturn(
                Single.just(Response.success(
                        gitmodulesText.getBytes(StandardCharsets.UTF_8))));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            verify(service).getContentsRaw("octocat", "hello", ".gitmodules", "main");
            waitUntil(() -> {
                try {
                    return field(f, "mGitModuleMap") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) field(f, "mGitModuleMap");
            assertEquals("octocat/hello", map.get("lib/foo"));
        }
    }

    @Test
    public void onContentsLoaded_initialPathDirChain_navigates() throws Exception {
        Content docsDir = content("docs", "docs", ContentType.Directory);
        Content guideDir = content("guide", "docs/guide", ContentType.Directory);
        RepositoryContentService service = serviceByPath(
                Arrays.asList(docsDir), Arrays.asList(guideDir));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                    repo(), "main", "docs/guide");
            attach(f);
            waitUntil(() -> {
                try {
                    return dirStack(f).size() == 3;
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(Arrays.asList("", "docs", "docs/guide"),
                    new ArrayList<>(dirStack(f)));
            assertTrue(initialPathToLoad(f).isEmpty());
            ContentListFragment child =
                    (ContentListFragment) field(f, "mContentListFragment");
            assertEquals("docs/guide", child.getPath());
        }
    }

    @Test
    public void onContentsLoaded_initialPathFileMatch_opensViewer() throws Exception {
        Content readme = content("README.md", "README.md", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(readme));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                    repo(), "main", "README.md");
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            waitUntil(() -> {
                try {
                    return initialPathToLoad(f).isEmpty();
                } catch (Exception e) {
                    return false;
                }
            });
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(com.gh4a.activities.FileViewerActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("README.md", started.getStringExtra("path"));
        }
    }

    @Test
    public void onContentsLoaded_initialPathNoMatch_clearsList() throws Exception {
        Content readme = content("README.md", "README.md", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(readme));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                    repo(), "main", "missing");
            attach(f);
            waitForContents(f);
            assertNull(initialPathToLoad(f));
        }
    }

    @Test
    public void onContentsLoaded_nonRoot_noGitmodulesScan() throws Exception {
        Content gitmodules = content(".gitmodules", "docs/.gitmodules", ContentType.File);
        Content readme = content("README.md", "README.md", ContentType.File);
        RepositoryContentService service = serviceByPath(
                Arrays.asList(readme), Arrays.asList(gitmodules));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            f.onTreeSelected(content("docs", "docs", ContentType.Directory));
            waitForPath(f, "docs");
            // 非根目录不扫 .gitmodules。
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void onContentsLoaded_stateSaved_skipsInitialPath() throws Exception {
        ContentListContainerFragment f = new ContentListContainerFragment();
        setField(f, "mCacheFragment", new ContentListCacheFragment());
        setField(f, "mInitialPathToLoad", new ArrayList<>(Arrays.asList("docs")));
        setField(f, "mStateSaved", true);
        ContentListFragment child = mock(ContentListFragment.class);
        when(child.getPath()).thenReturn("");
        Content file = content("README.md", "README.md", ContentType.File);
        f.onContentsLoaded(child, Arrays.asList(file));
        // mStateSaved 时初始路径不动。
        assertEquals(Arrays.asList("docs"), initialPathToLoad(f));
        ContentListCacheFragment cache =
                (ContentListCacheFragment) field(f, "mCacheFragment");
        assertEquals(1, cache.getFromCache("").size());
    }

    // ---- Chunk D：loadModuleMap ----

    @Test
    public void loadModuleMap_nullRepoChain_skipsService() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service = mock(RepositoryContentService.class);
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = new ContentListContainerFragment();
            setField(f, "mCacheFragment", new ContentListCacheFragment());
            setField(f, "mRepository", null);
            ContentListFragment child = mock(ContentListFragment.class);
            when(child.getPath()).thenReturn("");
            f.onContentsLoaded(child, Arrays.asList(gitmodules));
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void loadModuleMap_nonBaseActivityHost_skipsService() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(gitmodules));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attachPlain(f);
            waitForContents(f);
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void loadModuleMap_404_keepsNullMap() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(gitmodules));
        when(service.getContentsRaw(any(), any(), any(), any())).thenReturn(
                Single.just(Response.error(404,
                        okhttp3.ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            // 成功消费者跑完的信号：子列表收到空子模块名通知（404 映射为空）。
            ContentListFragment mockChild = mock(ContentListFragment.class);
            java.util.concurrent.atomic.AtomicBoolean notified =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            Mockito.doAnswer(inv -> {
                notified.set(true);
                return null;
            }).when(mockChild).onSubModuleNamesChanged(any());
            setField(f, "mContentListFragment", mockChild);
            waitUntil(notified::get);
            assertNull(field(f, "mGitModuleMap"));
        }
    }

    @Test
    public void loadModuleMap_error_recordsLoadFailure() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(gitmodules));
        when(service.getContentsRaw(any(), any(), any(), any())).thenReturn(
                Single.<Response<byte[]>>error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            waitUntil(() -> activity.loadFailure != null);
            assertTrue(activity.loadFailure instanceof RuntimeException);
        }
    }

    // ---- parseModuleMap 剩余分支 ----

    @Test
    public void parseModuleMap_incompleteFirstSubmodule_flushSkipped() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "[submodule \"b\"]\n"
                + "\tpath = b\n"
                + "\turl = https://github.com/o2/r2.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("o2/r2", result.get().get("b"));
        assertNull(result.get().get("a"));
    }

    @Test
    public void parseModuleMap_junkLine_ignored() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "# a comment\n"
                + "\turl = https://github.com/o/r.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("o/r", result.get().get("a"));
    }

    @Test
    public void parseModuleMap_urlWithoutDotSuffix_keptAsIs() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "\turl = https://github.com/o/r\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("o/r", result.get().get("a"));
    }

    @Test
    public void parseModuleMap_urlWithoutPath_notStored() {
        String text = "[submodule \"a\"]\n"
                + "\turl = https://github.com/o/r.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    // ---- 收口：剩余分支 ----

    @Test
    public void onCreate_cacheFragmentExists_reusesIt() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListCacheFragment preExisting = new ContentListCacheFragment();
            CallbackHostActivity activity = org.robolectric.Robolectric
                    .buildActivity(CallbackHostActivity.class).setup().get();
            activity.getSupportFragmentManager().beginTransaction()
                    .add(preExisting, "content_list_cache").commitNow();
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            activity.getSupportFragmentManager().beginTransaction()
                    .add(f, "test").commitNow();
            f.getChildFragmentManager().executePendingTransactions();
            idleMain();
            // onCreate 找到已有的缓存 fragment，不新建。
            assertTrue(field(f, "mCacheFragment") == preExisting);
            waitForContents(f);
        }
    }

    @Test
    public void onContentsLoaded_initialPathSubmoduleType_noMatch() throws Exception {
        Content submodule = content("foo", "lib/foo", ContentType.Submodule);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(submodule));
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                    repo(), "main", "lib/foo");
            attach(f);
            waitForContents(f);
            // 路径对得上但类型既不是目录也不是文件：不打开，初始路径清空。
            assertNull(initialPathToLoad(f));
            assertEquals(1, dirStack(f).size());
        }
    }

    @Test
    public void onTreeSelected_fileNotInModuleMap_opensFileViewer() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            java.util.Map<String, String> moduleMap = new java.util.HashMap<>();
            moduleMap.put("lib/foo", "octocat/hello");
            setField(f, "mGitModuleMap", moduleMap);
            f.onTreeSelected(content("other.txt", "other.txt", ContentType.File));
            // map 里没有这个文件：走文件查看器分支，不走仓库分支。
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(com.gh4a.activities.FileViewerActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("other.txt", started.getStringExtra("path"));
        }
    }

    @Test
    public void onTreeSelected_fileWithNullOwner_noIntent() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            Repository noOwner = Repository.builder()
                    .name("hello").defaultBranch("main").build();
            setField(f, "mRepository", noOwner);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void onTreeSelected_fileWithNullLogin_noIntent() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            Repository noLogin = Repository.builder()
                    .owner(User.builder().build())
                    .name("hello").defaultBranch("main").build();
            setField(f, "mRepository", noLogin);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void onTreeSelected_fileWithNullName_noIntent() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            Repository noName = Repository.builder()
                    .owner(User.builder().login("octocat").build())
                    .defaultBranch("main").build();
            setField(f, "mRepository", noName);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void getCurrentRef_nullRef_returnsDefaultBranch() throws Exception {
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), null, null);
            CallbackHostActivity activity = attach(f);
            waitForContents(f);
            f.onTreeSelected(content("README.md", "README.md", ContentType.File));
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            // ref 为空：getCurrentRef 走默认分支 main。
            assertEquals("main", started.getStringExtra("ref"));
        }
    }

    @Test
    public void loadModuleMap_guardOwnerNull_skipsService() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service = mock(RepositoryContentService.class);
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = new ContentListContainerFragment();
            setField(f, "mCacheFragment", new ContentListCacheFragment());
            setField(f, "mRepository", Repository.builder()
                    .name("hello").defaultBranch("main").build());
            ContentListFragment child = mock(ContentListFragment.class);
            when(child.getPath()).thenReturn("");
            f.onContentsLoaded(child, Arrays.asList(gitmodules));
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void loadModuleMap_guardLoginNull_skipsService() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service = mock(RepositoryContentService.class);
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = new ContentListContainerFragment();
            setField(f, "mCacheFragment", new ContentListCacheFragment());
            setField(f, "mRepository", Repository.builder()
                    .owner(User.builder().build())
                    .name("hello").defaultBranch("main").build());
            ContentListFragment child = mock(ContentListFragment.class);
            when(child.getPath()).thenReturn("");
            f.onContentsLoaded(child, Arrays.asList(gitmodules));
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void loadModuleMap_guardNameNull_skipsService() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service = mock(RepositoryContentService.class);
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f = new ContentListContainerFragment();
            setField(f, "mCacheFragment", new ContentListCacheFragment());
            setField(f, "mRepository", Repository.builder()
                    .owner(User.builder().login("octocat").build())
                    .defaultBranch("main").build());
            ContentListFragment child = mock(ContentListFragment.class);
            when(child.getPath()).thenReturn("");
            f.onContentsLoaded(child, Arrays.asList(gitmodules));
            verify(service, Mockito.never()).getContentsRaw(any(), any(), any(), any());
        }
    }

    @Test
    public void loadModuleMap_success_nullChild_skipsNotify() throws Exception {
        Content gitmodules = content(".gitmodules", ".gitmodules", ContentType.File);
        RepositoryContentService service =
                serviceReturning(Arrays.asList(gitmodules));
        String gitmodulesText = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat/hello.git\n";
        io.reactivex.subjects.SingleSubject<Response<byte[]>> subject =
                io.reactivex.subjects.SingleSubject.create();
        when(service.getContentsRaw(any(), any(), any(), any())).thenReturn(subject);
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            // 投递前把子列表置空：成功消费者里 mContentListFragment == null，
            // 只设 map、不通知。
            setField(f, "mContentListFragment", null);
            subject.onSuccess(Response.success(
                    gitmodulesText.getBytes(StandardCharsets.UTF_8)));
            waitUntil(() -> {
                try {
                    return field(f, "mGitModuleMap") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) field(f, "mGitModuleMap");
            assertEquals("octocat/hello", map.get("lib/foo"));
        }
    }

    @Test
    public void onContentsLoaded_initialPathSubmoduleType_direct_noMatch() throws Exception {
        ContentListContainerFragment f = new ContentListContainerFragment();
        setField(f, "mCacheFragment", new ContentListCacheFragment());
        setField(f, "mInitialPathToLoad", new ArrayList<>(Arrays.asList("lib/foo")));
        ContentListFragment child = mock(ContentListFragment.class);
        when(child.getPath()).thenReturn("");
        Content submodule = content("foo", "lib/foo", ContentType.Submodule);
        f.onContentsLoaded(child, Arrays.asList(submodule));
        assertNull(initialPathToLoad(f));
    }

    @Test
    public void newInstance_storesAllArgs() {
        ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                repo(), "dev", "docs/guide");
        assertNotNull(f.getArguments().getParcelable("repository"));
        assertEquals("dev", f.getArguments().getString("ref"));
        assertEquals("docs/guide", f.getArguments().getString("initialpath"));
    }

    @Test
    public void handleBackPress_emptyStack_returnsFalse() {
        // Bare instance: dir stack is empty, so there is nothing to pop.
        assertFalse(new ContentListContainerFragment().handleBackPress());
    }

    @Test
    public void onAttach_withoutCallback_throws() {
        ContentListContainerFragment f =
                ContentListContainerFragment.newInstance(repo(), "main", null);
        FragmentTestHelper.assertThrows(ClassCastException.class,
                () -> FragmentTestHelper.attach(f));
    }

    // ---- parseModuleMap (private) ----

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, String>> parse(String text) {
        try {
            Method m = ContentListContainerFragment.class
                    .getDeclaredMethod("parseModuleMap", Optional.class);
            m.setAccessible(true);
            Optional<byte[]> input = text == null ? Optional.empty()
                    : Optional.of(text.getBytes(StandardCharsets.UTF_8));
            return (Optional<Map<String, String>>) m.invoke(
                    new ContentListContainerFragment(), input);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void parseModuleMap_emptyInput_returnsEmpty() {
        assertFalse(parse(null).isPresent());
        assertFalse(parse("").isPresent());
        assertFalse(parse("   \n  ").isPresent());
    }

    @Test
    public void parseModuleMap_httpsUrl_parsesOwnerAndRepo() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("octocat/hello", result.get().get("lib/foo"));
    }

    @Test
    public void parseModuleMap_sshUrl_parsesOwnerAndRepo() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = git@github.com:octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("octocat/hello", result.get().get("lib/foo"));
    }

    @Test
    public void parseModuleMap_nonGithubUrl_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://gitlab.com/octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_urlWithoutTwoSegments_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_pathWithoutUrl_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_multipleSubmodules_allParsed() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "\turl = https://github.com/o1/r1.git\n"
                + "[submodule \"b\"]\n"
                + "\tpath = b\n"
                + "\turl = git@github.com:o2/r2.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals("o1/r1", result.get().get("a"));
        assertEquals("o2/r2", result.get().get("b"));
    }

    @Test
    public void parseModuleMap_repoSuffixStrippedOnlyAtLastDot() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "\turl = https://github.com/o/r.name.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertEquals("o/r.name", result.get().get("a"));
    }

    @Test
    public void addFragmentForTopOfStack_nullPath_skipsBackStack() throws Exception {
        // L304：栈顶为 null（损坏的恢复状态）时不进 back stack。
        // 注意：子列表 path 不是 null——ContentListFragment.newInstance
        // 把 null 归一化为 ""（ContentListFragment.java:135），故断言 ""。
        RepositoryContentService service = serviceReturning(new ArrayList<>());
        try (MockedStatic<ServiceFactory> services = mockContentService(service)) {
            ContentListContainerFragment f =
                    ContentListContainerFragment.newInstance(repo(), "main", null);
            attach(f);
            waitForContents(f);
            int backStackBefore =
                    f.getChildFragmentManager().getBackStackEntryCount();
            dirStack(f).push(null);
            Method m = ContentListContainerFragment.class
                    .getDeclaredMethod("addFragmentForTopOfStack");
            m.setAccessible(true);
            m.invoke(f);
            f.getChildFragmentManager().executePendingTransactions();
            idleMain();
            assertEquals(backStackBefore,
                    f.getChildFragmentManager().getBackStackEntryCount());
            ContentListFragment child =
                    (ContentListFragment) field(f, "mContentListFragment");
            assertNotNull(child);
            assertEquals("", child.getPath());
        }
    }
}
