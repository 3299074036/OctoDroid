package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ApiRequestException;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.CommitHistoryActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.DownloadUtils;
import com.gh4a.utils.FileUtils;
import com.gh4a.utils.IntentUtils;
import com.gh4a.widget.ContextMenuAwareRecyclerView;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.Content;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.ContentType;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.meisolsson.githubsdk.service.repositories.RepositoryContentService;

import io.reactivex.Single;
import retrofit2.Response;

import java.util.Arrays;

import okhttp3.ResponseBody;

/** ContentListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class ContentListFragmentTest {

    /** Host activity implementing the fragment's ParentCallback. */
    public static class ContentHostActivity extends FragmentActivity
            implements ContentListFragment.ParentCallback {
        final List<Content> treesSelected = new ArrayList<>();
        final List<List<Content>> contentsLoaded = new ArrayList<>();
        final List<Commit> commitsSelected = new ArrayList<>();

        @Override
        public void onContentsLoaded(ContentListFragment fragment, List<Content> contents) {
            contentsLoaded.add(contents);
        }

        @Override
        public void onTreeSelected(Content content) {
            treesSelected.add(content);
        }

        @Override
        public void onCommitSelected(Commit commit) {
            commitsSelected.add(commit);
        }

        Set<String> subModules = Collections.emptySet();

        @Override
        public Set<String> getSubModuleNames(ContentListFragment fragment) {
            return subModules;
        }
    }

    public static class NoLoad extends ContentListFragment {
        boolean useRealSingle;
        boolean useRealInitialData;

        @Override
        protected List<Content> onGetInitialData() {
            if (useRealInitialData) {
                return super.onGetInitialData();
            }
            return null;
        }

        @Override
        protected io.reactivex.Observable<List<Content>> onCreateDataObservable(
                boolean bypassCache) {
            return null;
        }

        @Override
        protected Single<List<Content>> onCreateDataSingle(boolean bypassCache) {
            if (useRealSingle) {
                return super.onCreateDataSingle(bypassCache);
            }
            return Single.never();
        }

        Single<List<Content>> realSingle(boolean bypassCache) {
            useRealSingle = true;
            try {
                return onCreateDataSingle(bypassCache);
            } finally {
                useRealSingle = false;
            }
        }

        List<Content> realInitialData() {
            useRealInitialData = true;
            try {
                return onGetInitialData();
            } finally {
                useRealInitialData = false;
            }
        }
    }

    private static Repository repo() {
        return Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").defaultBranch("main").build();
    }

    /** Shared fixture for other fragment tests: the "octocat/hello" repo. */
    static Repository repoForTest() {
        return repo();
    }

    private static Content mockContent(String name, ContentType type, String path) {
        Content c = mock(Content.class);
        when(c.name()).thenReturn(name);
        when(c.type()).thenReturn(type);
        when(c.path()).thenReturn(path);
        return c;
    }

    private static ContentHostActivity attach(NoLoad f) {
        ContentHostActivity activity = org.robolectric.Robolectric
                .buildActivity(ContentHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(f, "test").commitNow();
        return activity;
    }

    private static NoLoad withContents(ArrayList<Content> contents, String ref) {
        NoLoad f = new NoLoad();
        f.setArguments(ContentListFragment.newInstance(repo(), "", contents, ref)
                .getArguments());
        return f;
    }

    @Test
    public void newInstance_nullPathBecomesEmpty() {
        ContentListFragment f = ContentListFragment.newInstance(
                repo(), null, new ArrayList<>(), "main");
        assertEquals("", f.getArguments().getString("path"));
        assertEquals("main", f.getArguments().getString("ref"));
        assertNotNull(f.getArguments().getParcelable("repo"));
    }

    @Test
    public void newInstance_storesPathAndContents() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("README.md", ContentType.File, "README.md"));
        ContentListFragment f = ContentListFragment.newInstance(
                repo(), "docs", contents, "dev");
        assertEquals("docs", f.getArguments().getString("path"));
        assertEquals(1, f.getArguments().<Content>getParcelableArrayList("contents").size());
    }

    @Test
    public void onCreate_blankRefFallsBackToDefaultBranch() throws Exception {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "");
        attach(f);
        assertEquals("", f.getPath());
        java.lang.reflect.Field refField =
                ContentListFragment.class.getDeclaredField("mRef");
        refField.setAccessible(true);
        assertEquals("main", refField.get(f));
    }

    @Test
    public void onCreate_givenRefKeepsIt() throws Exception {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "dev");
        attach(f);
        java.lang.reflect.Field refField =
                ContentListFragment.class.getDeclaredField("mRef");
        refField.setAccessible(true);
        assertEquals("dev", refField.get(f));
    }

    @Test
    public void getEmptyTextResId_isNoFiles() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        assertEquals(R.string.no_files_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onGetInitialData_withContents_returnsThem() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        List<Content> initial = f.realInitialData();
        assertEquals(1, initial.size());
        assertEquals("a.txt", initial.get(0).name());
    }

    @Test
    public void onGetInitialData_emptyContents_returnsNull() {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        assertNull(f.realInitialData());
    }

    @Test
    public void onCreateDataSingle_returnsNonNullSingleWithoutNetwork() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        assertNotNull(f.realSingle(false));
        assertNotNull(f.realSingle(true));
    }

    @Test
    public void onItemClick_delegatesToCallback() {
        ArrayList<Content> contents = new ArrayList<>();
        Content dir = mockContent("src", ContentType.Directory, "src");
        contents.add(dir);
        NoLoad f = withContents(contents, "main");
        ContentHostActivity activity = attach(f);
        f.onItemClick(dir);
        assertEquals(1, activity.treesSelected.size());
        assertEquals(dir, activity.treesSelected.get(0));
    }

    @Test
    public void onAddData_notifiesCallback() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        ContentHostActivity activity = attach(f);
        com.gh4a.adapter.RootAdapter<Content, ?> adapter =
                (com.gh4a.adapter.RootAdapter<Content, ?>) f.onCreateAdapter();
        List<Content> data = Collections.singletonList(
                mockContent("b.txt", ContentType.File, "b.txt"));
        f.onAddData(adapter, data);
        assertEquals(1, activity.contentsLoaded.size());
        assertEquals(data, activity.contentsLoaded.get(0));
    }

    @Test
    public void onSubModuleNamesChanged_withAdapter_doesNotCrash() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        f.onCreateAdapter();
        f.onSubModuleNamesChanged(new HashSet<>(Collections.singletonList("sub")));
        f.onSubModuleNamesChanged(Collections.emptySet());
    }

    @Test
    public void onCreateContextMenu_nonRecyclerInfo_buildsNothing() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        f.onCreateAdapter();
        android.view.ContextMenu menu = mock(android.view.ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), null);
        org.mockito.Mockito.verifyNoInteractions(menu);
    }

    @Test
    public void onContextItemSelected_nonRecyclerInfo_returnsFalse() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        attach(f);
        f.onCreateAdapter();
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getMenuInfo()).thenReturn(null);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onAttach_withoutCallback_throws() {
        ContentListFragment f = ContentListFragment.newInstance(
                repo(), "", new ArrayList<>(), "main");
        FragmentTestHelper.assertThrows(ClassCastException.class,
                () -> FragmentTestHelper.attach(f));
    }

    @Test
    public void getPath_returnsArgPath() {
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(mockContent("a.txt", ContentType.File, "a.txt"));
        NoLoad f = withContents(contents, "main");
        // getPath needs onCreate; attach with initial contents (no network).
        NoLoad g = new NoLoad();
        Bundle args = ContentListFragment.newInstance(repo(), "docs", contents, "main")
                .getArguments();
        g.setArguments(args);
        attach(g);
        assertEquals("docs", g.getPath());
    }

    // ---- onCreateDataSingle with a mocked RepositoryContentService ----

    @SuppressWarnings("unchecked")
    private static Page<Content> contentPage(List<Content> items, Integer next) {
        Page<Content> p = mock(Page.class);
        when(p.items()).thenReturn(items);
        when(p.next()).thenReturn(next);
        return p;
    }

    @Test
    public void realSingle_success_returnsSortedContents() {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        Content file = mockContent("z.txt", ContentType.File, "z.txt");
        Content dir = mockContent("a-dir", ContentType.Directory, "a-dir");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryContentService service = mock(RepositoryContentService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                    .thenReturn(service);
            Page<Content> page1 = contentPage(Arrays.asList(file, dir), null);
            when(service.getDirectoryContents("octocat", "hello", "", "main", 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(dir, file))
                    .assertComplete();
            verify(service).getDirectoryContents("octocat", "hello", "", "main", 1L);
        }
    }

    @Test
    public void realSingle_twoPages_concatenatesAndSorts() {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        Content file = mockContent("b.txt", ContentType.File, "b.txt");
        Content dir = mockContent("a-dir", ContentType.Directory, "a-dir");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryContentService service = mock(RepositoryContentService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                    .thenReturn(service);
            Page<Content> page1 = contentPage(Arrays.asList(file), 2);
            Page<Content> page2 = contentPage(Arrays.asList(dir), null);
            when(service.getDirectoryContents("octocat", "hello", "", "main", 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            when(service.getDirectoryContents("octocat", "hello", "", "main", 2L))
                    .thenReturn(Single.just(Response.success(page2)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(dir, file))
                    .assertComplete();
            verify(service).getDirectoryContents("octocat", "hello", "", "main", 2L);
        }
    }

    @Test
    public void realSingle_404_mapsToEmptyList() {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryContentService service = mock(RepositoryContentService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                    .thenReturn(service);
            when(service.getDirectoryContents("octocat", "hello", "", "main", 1L))
                    .thenReturn(Single.error(new ApiRequestException(
                            Response.error(404, ResponseBody.create(null, "")))));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList())
                    .assertComplete();
        }
    }

    // ---- COMPARATOR (null-safe name ordering) ----

    @SuppressWarnings("unchecked")
    private static Comparator<Content> comparator() throws Exception {
        Field field = ContentListFragment.class.getDeclaredField("COMPARATOR");
        field.setAccessible(true);
        return (Comparator<Content>) field.get(null);
    }

    @Test
    public void comparator_dirBeforeFile_returnsNegative() throws Exception {
        assertEquals(-1, comparator().compare(
                mockContent("a", ContentType.Directory, "a"),
                mockContent("b", ContentType.File, "b")));
    }

    @Test
    public void comparator_fileAfterDir_returnsPositive() throws Exception {
        assertEquals(1, comparator().compare(
                mockContent("a", ContentType.File, "a"),
                mockContent("b", ContentType.Directory, "b")));
    }

    @Test
    public void comparator_sameTypeNames_alphabetic() throws Exception {
        Comparator<Content> cmp = comparator();
        assertTrue(cmp.compare(mockContent("a", ContentType.File, "a"),
                mockContent("b", ContentType.File, "b")) < 0);
        assertTrue(cmp.compare(mockContent("b", ContentType.File, "b"),
                mockContent("a", ContentType.File, "a")) > 0);
    }

    @Test
    public void comparator_nullLhsName_sortsFirst() throws Exception {
        assertEquals(-1, comparator().compare(
                mockContent(null, ContentType.File, "a"),
                mockContent("b", ContentType.File, "b")));
    }

    @Test
    public void comparator_bothNullNames_equal() throws Exception {
        assertEquals(0, comparator().compare(
                mockContent(null, ContentType.File, "a"),
                mockContent(null, ContentType.File, "b")));
    }

    @Test
    public void comparator_nullRhsName_sortsLast() throws Exception {
        assertEquals(1, comparator().compare(
                mockContent("b", ContentType.File, "b"),
                mockContent(null, ContentType.File, "a")));
    }

    @Test
    public void comparator_bothDirs_alphabetic() throws Exception {
        assertTrue(comparator().compare(
                mockContent("a", ContentType.Directory, "a"),
                mockContent("b", ContentType.Directory, "b")) < 0);
    }

    // ---- history launcher result callback ----

    @SuppressWarnings("unchecked")
    private static ActivityResultLauncher<Intent> historyLauncher(ContentListFragment f)
            throws Exception {
        Field field = ContentListFragment.class.getDeclaredField("mFileHistoryLauncher");
        field.setAccessible(true);
        return (ActivityResultLauncher<Intent>) field.get(f);
    }

    private static Object registryField(ActivityResultRegistry registry, String name)
            throws Exception {
        Field field = ActivityResultRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(registry);
    }

    /** Dispatch a result to the launcher that was launched last. */
    @SuppressWarnings("unchecked")
    private static boolean dispatchLaunchedResult(Activity activity, int resultCode, Intent data)
            throws Exception {
        ActivityResultRegistry registry =
                ((androidx.activity.ComponentActivity) activity).getActivityResultRegistry();
        Collection<String> launchedKeys =
                (Collection<String>) registryField(registry, "launchedKeys");
        assertNotNull(launchedKeys);
        assertFalse(launchedKeys.isEmpty());
        String key = launchedKeys.iterator().next();
        Map<String, Integer> keyToRc = (Map<String, Integer>) registryField(registry, "keyToRc");
        assertTrue(keyToRc.containsKey(key));
        return registry.dispatchResult(keyToRc.get(key), resultCode, data);
    }

    private static void launchHistory(ContentListFragment f, Activity activity) throws Exception {
        Intent intent = CommitHistoryActivity.makeIntent(activity,
                "octocat", "hello", "main", "a.txt", ContentType.File, true);
        historyLauncher(f).launch(intent);
    }

    @Test
    public void historyResult_canceled_doesNothing() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        launchHistory(f, activity);
        assertTrue(dispatchLaunchedResult(activity, Activity.RESULT_CANCELED, null));
        ShadowLooper.idleMainLooper();
        assertTrue(activity.commitsSelected.isEmpty());
    }

    @Test
    public void historyResult_okNullData_doesNothing() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        launchHistory(f, activity);
        assertTrue(dispatchLaunchedResult(activity, Activity.RESULT_OK, null));
        ShadowLooper.idleMainLooper();
        assertTrue(activity.commitsSelected.isEmpty());
    }

    @Test
    public void historyResult_okNoCommitExtra_doesNothing() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        launchHistory(f, activity);
        assertTrue(dispatchLaunchedResult(activity, Activity.RESULT_OK, new Intent()));
        ShadowLooper.idleMainLooper();
        assertTrue(activity.commitsSelected.isEmpty());
    }

    @Test
    public void historyResult_okWithCommit_callsCallback() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        launchHistory(f, activity);
        Commit commit = mock(Commit.class);
        Intent data = new Intent();
        data.putExtra("commit", commit);
        assertTrue(dispatchLaunchedResult(activity, Activity.RESULT_OK, data));
        ShadowLooper.idleMainLooper();
        assertEquals(1, activity.commitsSelected.size());
        assertEquals(commit, activity.commitsSelected.get(0));
    }

    @Test
    public void historyResult_okWithCommit_afterDetach_skipsCallback() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        launchHistory(f, activity);
        Commit commit = mock(Commit.class);
        Intent data = new Intent();
        data.putExtra("commit", commit);
        assertTrue(dispatchLaunchedResult(activity, Activity.RESULT_OK, data));
        // Detach before the posted runnable runs: the callback must not fire.
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        ShadowLooper.idleMainLooper();
        assertTrue(activity.commitsSelected.isEmpty());
    }

    // ---- context menu ----

    private static ContextMenuAwareRecyclerView.RecyclerContextMenuInfo menuInfo(int position)
            throws Exception {
        Constructor<ContextMenuAwareRecyclerView.RecyclerContextMenuInfo> ctor =
                ContextMenuAwareRecyclerView.RecyclerContextMenuInfo.class
                        .getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(position, 0L);
    }

    private static MenuItem menuItem(int id, ContextMenu.ContextMenuInfo info) {
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(id);
        when(item.getMenuInfo()).thenReturn(info);
        return item;
    }

    @Test
    public void onCreateContextMenu_fileNonSubmodule_addsHistoryAndDownload() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        ContextMenu menu = mock(ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), menuInfo(0));
        verify(menu).add(Menu.NONE, R.id.history, Menu.NONE, R.string.history);
        verify(menu).add(Menu.NONE, R.id.download, Menu.NONE, R.string.download);
    }

    @Test
    public void onCreateContextMenu_directory_addsHistoryOnly() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("src", ContentType.Directory, "src")));
        ContextMenu menu = mock(ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), menuInfo(0));
        verify(menu).add(Menu.NONE, R.id.history, Menu.NONE, R.string.history);
        verify(menu, never()).add(Menu.NONE, R.id.download, Menu.NONE, R.string.download);
    }

    @Test
    public void onCreateContextMenu_fileSubmodule_addsHistoryOnly() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        activity.subModules = new HashSet<>(Collections.singletonList("a.txt"));
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        ContextMenu menu = mock(ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), menuInfo(0));
        verify(menu).add(Menu.NONE, R.id.history, Menu.NONE, R.string.history);
        verify(menu, never()).add(Menu.NONE, R.id.download, Menu.NONE, R.string.download);
    }

    @Test
    public void onCreateContextMenu_headerPosition_buildsNothing() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        adapter.setHeaderView(new View(activity));
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        ContextMenu menu = mock(ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), menuInfo(0));
        org.mockito.Mockito.verifyNoInteractions(menu);
    }

    @Test
    public void onCreateContextMenu_callbackNull_buildsNothing() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        Field callback = ContentListFragment.class.getDeclaredField("mCallback");
        callback.setAccessible(true);
        callback.set(f, null);
        ContextMenu menu = mock(ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), menuInfo(0));
        org.mockito.Mockito.verifyNoInteractions(menu);
    }

    // ---- context item selection ----

    @Test
    public void onContextItemSelected_negativePosition_returnsFalse() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        assertFalse(f.onContextItemSelected(menuItem(R.id.history, menuInfo(-1))));
    }

    @Test
    public void onContextItemSelected_positionPastEnd_returnsFalse() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        assertEquals(1, adapter.getItemCount());
        assertFalse(f.onContextItemSelected(menuItem(R.id.history, menuInfo(1))));
    }

    @Test
    public void onContextItemSelected_headerPosition_returnsFalse() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        adapter.setHeaderView(new View(activity));
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        assertFalse(f.onContextItemSelected(menuItem(R.id.history, menuInfo(0))));
    }

    @Test
    public void onContextItemSelected_history_launchesAndReturnsTrue() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        assertTrue(f.onContextItemSelected(menuItem(R.id.history, menuInfo(0))));
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(CommitHistoryActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void onContextItemSelected_download_enqueuesAndReturnsTrue() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        try (MockedStatic<DownloadUtils> mocked = mockStatic(DownloadUtils.class)) {
            assertTrue(f.onContextItemSelected(menuItem(R.id.download, menuInfo(0))));
            mocked.verify(() -> DownloadUtils.enqueueDownloadWithPermissionCheck(
                    null,
                    IntentUtils.createRawFileUrl("octocat", "hello", "main", "a.txt"),
                    FileUtils.getMimeTypeFor("a.txt"), "a.txt", null));
        }
    }

    @Test
    public void onContextItemSelected_unknownId_returnsFalse() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        f.onAddData(adapter, Collections.singletonList(
                mockContent("a.txt", ContentType.File, "a.txt")));
        assertFalse(f.onContextItemSelected(menuItem(R.id.search, menuInfo(0))));
    }

    // ---- single branch halves ----

    @Test
    public void onSubModuleNamesChanged_noAdapter_isNoop() {
        NoLoad f = new NoLoad();
        f.setArguments(ContentListFragment.newInstance(repo(), "", new ArrayList<>(), "main")
                .getArguments());
        f.onSubModuleNamesChanged(Collections.singleton("sub"));
    }

    @Test
    public void onAddData_callbackNull_doesNotNotify() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        ContentHostActivity activity = attach(f);
        RootAdapter<Content, ?> adapter = (RootAdapter<Content, ?>) f.onCreateAdapter();
        Field callback = ContentListFragment.class.getDeclaredField("mCallback");
        callback.setAccessible(true);
        callback.set(f, null);
        List<Content> data = Collections.singletonList(
                mockContent("b.txt", ContentType.File, "b.txt"));
        f.onAddData(adapter, data);
        assertTrue(activity.contentsLoaded.isEmpty());
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void onCreateDataSingle_nullRef_fallsBackToDefaultBranch() throws Exception {
        NoLoad f = withContents(new ArrayList<>(), "main");
        attach(f);
        Field ref = ContentListFragment.class.getDeclaredField("mRef");
        ref.setAccessible(true);
        ref.set(f, null);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryContentService service = mock(RepositoryContentService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                    .thenReturn(service);
            Page<Content> page = contentPage(Collections.emptyList(), null);
            when(service.getDirectoryContents("octocat", "hello", "", "main", 1L))
                    .thenReturn(Single.just(Response.success(page)));
            f.realSingle(false).test()
                    .assertValue(Collections.emptyList())
                    .assertComplete();
            verify(service).getDirectoryContents("octocat", "hello", "", "main", 1L);
        }
    }
}
