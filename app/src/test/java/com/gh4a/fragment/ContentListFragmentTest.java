package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ApiRequestException;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
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

import java.util.ArrayList;
import java.util.Collections;
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

        @Override
        public Set<String> getSubModuleNames(ContentListFragment fragment) {
            return Collections.emptySet();
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
}
