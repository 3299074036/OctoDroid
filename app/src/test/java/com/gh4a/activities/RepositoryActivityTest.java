package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Branch;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import androidx.appcompat.view.menu.MenuBuilder;
import com.gh4a.fragment.CommitListFragment;
import com.gh4a.fragment.ContentListContainerFragment;
import com.gh4a.fragment.RepositoryEventListFragment;
import com.gh4a.fragment.RepositoryFragment;
import java.lang.reflect.Method;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.assertFalse;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class RepositoryActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Repository repo(String login, String name) {
        Repository.Builder b = Repository.builder();
        if (login != null) {
            b.owner(User.builder().login(login).build());
        }
        if (name != null) {
            b.name(name);
        }
        return b.build();
    }

    private ActivityController<RepositoryActivity> launch(String owner, String repo) {
        return Robolectric.buildActivity(RepositoryActivity.class,
                RepositoryActivity.makeIntent(context(), owner, repo));
    }

    // ---- makeIntent ----

    @Test
    public void makeIntent_ownerRepo_carriesExtrasWithDefaults() {
        Intent intent = RepositoryActivity.makeIntent(context(), "octocat", "hello");
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertNull(intent.getStringExtra("ref"));
        assertNull(intent.getStringExtra("initial_path"));
        assertEquals(RepositoryActivity.PAGE_REPO_OVERVIEW,
                intent.getIntExtra("initial_page", -99));
    }

    @Test
    public void makeIntent_emptyRefBecomesNull() {
        Intent intent = RepositoryActivity.makeIntent(context(), "o", "r", "");
        assertNull(intent.getStringExtra("ref"));
        Intent intent2 = RepositoryActivity.makeIntent(context(), "o", "r", "main");
        assertEquals("main", intent2.getStringExtra("ref"));
    }

    @Test
    public void makeIntent_full_carriesPathAndPage() {
        Intent intent = RepositoryActivity.makeIntent(context(), "o", "r", "dev",
                "src/main", RepositoryActivity.PAGE_FILES);
        assertEquals("dev", intent.getStringExtra("ref"));
        assertEquals("src/main", intent.getStringExtra("initial_path"));
        assertEquals(RepositoryActivity.PAGE_FILES,
                intent.getIntExtra("initial_page", -99));
    }

    @Test
    public void makeIntent_nullRepo_mapsToEmptyStrings() {
        Intent intent = RepositoryActivity.makeIntent(context(), (Repository) null);
        assertEquals("", intent.getStringExtra("owner"));
        assertEquals("", intent.getStringExtra("repo"));
    }

    @Test
    public void makeIntent_repoWithNullOwnerAndName_mapsToEmptyStrings() {
        Intent intent = RepositoryActivity.makeIntent(context(), repo(null, null));
        assertEquals("", intent.getStringExtra("owner"));
        assertEquals("", intent.getStringExtra("repo"));
    }

    @Test
    public void makeIntent_repoWithNullOwnerLogin_mapsOwnerToEmpty() {
        Repository r = Repository.builder()
                .owner(User.builder().build()).name("hello").build();
        Intent intent = RepositoryActivity.makeIntent(context(), r);
        assertEquals("", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
    }

    @Test
    public void makeIntent_repoWithRef_keepsRef() {
        Intent intent = RepositoryActivity.makeIntent(context(), repo("octocat", "hello"), "v1");
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertEquals("v1", intent.getStringExtra("ref"));
    }

    @Test
    public void pageConstants_areOrdered() {
        assertEquals(0, RepositoryActivity.PAGE_REPO_OVERVIEW);
        assertEquals(1, RepositoryActivity.PAGE_FILES);
        assertEquals(2, RepositoryActivity.PAGE_COMMITS);
        assertEquals(3, RepositoryActivity.PAGE_ACTIVITY);
    }

    // ---- Activity 行为（离线：loadRepository 失败走错误页，不崩） ----

    @Test
    public void launches_titleIsOwnerSlashRepo() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getTabTitleResIds_nullBeforeRepoLoads() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            assertNull(activity.getTabTitleResIds());
        }
    }

    @Test
    public void makeFragment_overviewFilesActivity_okBeforeLoad() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            assertTrue(activity.makeFragment(0)
                    instanceof com.gh4a.fragment.RepositoryFragment);
            assertTrue(activity.makeFragment(1)
                    instanceof com.gh4a.fragment.ContentListContainerFragment);
            assertTrue(activity.makeFragment(3)
                    instanceof com.gh4a.fragment.RepositoryEventListFragment);
            assertNull(activity.makeFragment(4));
        }
    }

    @Test
    public void makeFragment_commitsTab_requiresLoadedRepo() {
        // CommitListFragment.newInstance(Repository, ref) 直接解 repo.owner()，
        // 加载完成前 mRepository 为 null 时即 NPE；页签只在加载后才建，
        // UI 上不可达，此处只把前置条件钉死。
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            try {
                activity.makeFragment(2);
                throw new AssertionError("expected NullPointerException");
            } catch (NullPointerException expected) {
                // expected
            }
        }
    }

    @Test
    public void onSaveInstanceState_roundTripsSelectedRef() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            Field f = RepositoryActivity.class.getDeclaredField("mSelectedRef");
            f.setAccessible(true);
            f.set(activity, "feature-x");
            Bundle out = new Bundle();
            activity.onSaveInstanceState(out);
            assertEquals("feature-x", out.getString("selected_ref"));
        }
    }

    @Test
    public void onOptionsItemSelected_search_startsSearchActivity() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem item = menu.findItem(R.id.search);
            assertNotNull(item);
            assertTrue(activity.onOptionsItemSelected(item));
            ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent started = shadow.getNextStartedActivity();
            assertNotNull(started);
            assertEquals(SearchActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("repo:octocat/hello ",
                    started.getStringExtra("initial_search"));
            assertEquals(SearchActivity.SEARCH_TYPE_CODE,
                    started.getIntExtra("search_type", -1));
        }
    }

    @Test
    public void onOptionsItemSelected_copyCloneUrl_copiesToClipboard() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem item = menu.findItem(R.id.copy_clone_url);
            assertNotNull(item);
            assertTrue(activity.onOptionsItemSelected(item));
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals("https://github.com/octocat/hello.git",
                    cm.getPrimaryClip().getItemAt(0).getText().toString());
        }
    }

    @Test
    public void onOptionsItemSelected_browser_noBrowserShowsToast() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem item = menu.findItem(R.id.browser);
            assertNotNull(item);
            assertTrue(activity.onOptionsItemSelected(item));
            assertEquals(activity.getString(R.string.no_browser_found),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void onPrepareOptionsMenu_beforeLoad_removesRepoOnlyItems() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onPrepareOptionsMenu(menu);
            assertNull(menu.findItem(R.id.ref));
            assertNull(menu.findItem(R.id.fork));
            assertNull(menu.findItem(R.id.zip_download));
            assertNull(menu.findItem(R.id.copy_clone_url));
        }
    }

    @Test
    public void onOptionsItemSelected_zipDownload_beforeLoad_guardedByMenuRemoval() {
        // onPrepareOptionsMenu 已移除该项；直接调方法时守卫只判 mRepository.url()，
        // mRepository 本身为 null 会 NPE——UI 不可达，此处记录该前置条件。
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem item = menu.findItem(R.id.zip_download);
            assertNotNull(item);
            try {
                activity.onOptionsItemSelected(item);
                throw new AssertionError("expected NullPointerException");
            } catch (NullPointerException expected) {
                // expected: 见 A2 备注——UI 不可达（菜单项加载前已被移除）
            }
        }
    }

    @Test
    public void navigateUp_returnsUserActivityIntent() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(UserActivity.class.getName(), up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("login"));
        }
    }

    @Test
    public void onRefresh_doesNotCrash() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    @Test
    public void onBackPressed_withoutContentFragment_finishesActivity() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            activity.onBackPressed();
            assertTrue(activity.isFinishing());
        }
    }

    // ---- BranchSelectionDialogFragment / BranchAndTagAdapter ----

    private static Branch branch(String name, String sha) {
        Branch b = mock(Branch.class);
        when(b.name()).thenReturn(name);
        if (sha != null) {
            Commit commit = mock(Commit.class);
            when(commit.sha()).thenReturn(sha);
            when(b.commit()).thenReturn(commit);
        }
        return b;
    }

    @Test
    public void branchDialog_newInstance_carriesArgs() {
        List<Branch> branches = Arrays.asList(branch("main", "a1"), branch("dev", "b2"));
        List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
        RepositoryActivity.BranchSelectionDialogFragment f =
                RepositoryActivity.BranchSelectionDialogFragment.newInstance(
                        branches, tags, "dev", "main");
        Bundle args = f.getArguments();
        assertEquals(2, args.getParcelableArrayList("branches").size());
        assertEquals(1, args.getParcelableArrayList("tags").size());
        assertEquals("dev", args.getString("selectedRef"));
        assertEquals("main", args.getString("defaultBranch"));
    }

    @Test
    public void branchAndTagAdapter_countItemsAndView() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            List<Branch> branches = Arrays.asList(branch("main", "a1"), branch("dev", "b2"));
            List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
            // 反射取私有静态内部类（同包测试可直接 new，但类是 private）
            Class<?> adapterClass = null;
            for (Class<?> inner : RepositoryActivity.class.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("BranchAndTagAdapter")) {
                    adapterClass = inner;
                }
            }
            assertNotNull(adapterClass);
            try {
                java.lang.reflect.Constructor<?> ctor = adapterClass.getDeclaredConstructor(
                        Context.class, List.class, List.class);
                ctor.setAccessible(true);
                android.widget.BaseAdapter adapter =
                        (android.widget.BaseAdapter) ctor.newInstance(activity, branches, tags);
                assertEquals(3, adapter.getCount());
                assertEquals("main", ((Branch) adapter.getItem(0)).name());
                assertEquals("v1.0", ((Branch) adapter.getItem(2)).name());
                assertEquals(2L, adapter.getItemId(2));
                View row = adapter.getView(0, null,
                        new android.widget.FrameLayout(activity));
                assertNotNull(row);
                assertEquals("main",
                        ((android.widget.TextView) row.findViewById(R.id.title))
                                .getText().toString());
                View tagRow = adapter.getView(2, null,
                        new android.widget.FrameLayout(activity));
                assertEquals("v1.0",
                        ((android.widget.TextView) tagRow.findViewById(R.id.title))
                                .getText().toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Repository mockRepo() {
        Repository r = mock(Repository.class);
        when(r.fullName()).thenReturn("octocat/hello");
        when(r.defaultBranch()).thenReturn("main");
        when(r.htmlUrl()).thenReturn("https://github.com/octocat/hello");
        when(r.url()).thenReturn("https://api.github.com/repos/octocat/hello");
        when(r.owner()).thenReturn(User.builder().login("octocat").build());
        when(r.name()).thenReturn("hello");
        return r;
    }

    private static void setRepoField(RepositoryActivity activity, String name, Object value)
            throws Exception {
        Field f = RepositoryActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(activity, value);
    }

    @Test
    public void onFragmentInstantiated_recordsEachPosition() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            RepositoryFragment repoFragment = mock(RepositoryFragment.class);
            ContentListContainerFragment contentFragment =
                    mock(ContentListContainerFragment.class);
            CommitListFragment commitFragment = mock(CommitListFragment.class);
            RepositoryEventListFragment eventFragment =
                    mock(RepositoryEventListFragment.class);
            activity.onFragmentInstantiated(repoFragment, 0);
            activity.onFragmentInstantiated(contentFragment, 1);
            activity.onFragmentInstantiated(commitFragment, 2);
            activity.onFragmentInstantiated(eventFragment, 3);
            assertFalse(activity.fragmentNeedsRefresh(commitFragment));
            activity.onFragmentDestroyed(commitFragment);
            assertTrue(activity.fragmentNeedsRefresh(commitFragment));
        }
    }

    @Test
    public void getTabTitleResIds_withRepository_returnsThreeTitles() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            int[] titles = activity.getTabTitleResIds();
            assertNotNull(titles);
            assertEquals(4, titles.length);
        }
    }

    @Test
    public void baseSelectionAllowed_returnsTrue() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            assertTrue(activity.baseSelectionAllowed());
        }
    }

    @Test
    public void onCommitSelectedAsBase_updatesTitleAndSubtitle() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            Commit commit = mock(Commit.class);
            when(commit.sha()).thenReturn("abc123");
            activity.onCommitSelectedAsBase(commit);
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("abc123",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onPrepareOptionsMenu_withRepository_keepsRefItem() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            MenuBuilder menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onPrepareOptionsMenu(menu));
            assertTrue(menu.findItem(R.id.ref).isVisible());
            assertTrue(menu.findItem(R.id.zip_download).isVisible());
        }
    }

    @Test
    public void showRefSelectionDialog_withBranches_showsDialog() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            Branch branch = mock(Branch.class);
            when(branch.name()).thenReturn("main");
            when(branch.commit()).thenReturn(null);
            setRepoField(activity, "mBranches", Arrays.asList(branch));
            setRepoField(activity, "mTags", Collections.emptyList());
            setRepoField(activity, "mSelectedRef", "main");
            Method m = RepositoryActivity.class.getDeclaredMethod("showRefSelectionDialog");
            m.setAccessible(true);
            m.invoke(activity);
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertFalse(ShadowDialog.getShownDialogs().isEmpty());
        }
    }

    @Test
    public void onOptionsItemSelected_fork_showsConfirmDialog() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.fork, 0, "Fork");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.fork)));
            assertFalse(ShadowAlertDialog.getShownDialogs().isEmpty());
        }
    }

    @Test
    public void onOptionsItemSelected_share_showsChooser() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.share, 0, "Share");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
            assertEquals(Intent.ACTION_CHOOSER,
                    Shadows.shadowOf(activity).getNextStartedActivity().getAction());
        }
    }

    @Test
    public void onOptionsItemSelected_refWithBranches_showsDialog() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            Branch branch = mock(Branch.class);
            when(branch.name()).thenReturn("main");
            when(branch.commit()).thenReturn(null);
            setRepoField(activity, "mBranches", Arrays.asList(branch));
            setRepoField(activity, "mTags", Collections.emptyList());
            setRepoField(activity, "mSelectedRef", "main");
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.ref, 0, "Ref");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.ref)));
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertFalse(ShadowDialog.getShownDialogs().isEmpty());
        }
    }

    // ---- A2 第二轮补缺口：RepositoryActivity ----

    private static Object getRepoField(RepositoryActivity activity, String name) throws Exception {
        Field f = RepositoryActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(activity);
    }

    @Test
    public void recreate_restoresSelectedRef() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mSelectedRef", "dev");
            c.recreate();
            assertEquals("dev", getRepoField(c.get(), "mSelectedRef"));
        }
    }

    @Test
    public void getCurrentRef_noSelectedRef_returnsDefaultBranch() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            setRepoField(activity, "mSelectedRef", null);
            Method m = RepositoryActivity.class.getDeclaredMethod("getCurrentRef");
            m.setAccessible(true);
            assertEquals("main", m.invoke(activity));
        }
    }

    @Test
    public void onFragmentDestroyed_eachType_clearsField() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            com.gh4a.fragment.RepositoryFragment repoFrag =
                    mock(com.gh4a.fragment.RepositoryFragment.class);
            com.gh4a.fragment.ContentListContainerFragment contentFrag =
                    mock(com.gh4a.fragment.ContentListContainerFragment.class);
            com.gh4a.fragment.CommitListFragment commitFrag =
                    mock(com.gh4a.fragment.CommitListFragment.class);
            com.gh4a.fragment.RepositoryEventListFragment eventFrag =
                    mock(com.gh4a.fragment.RepositoryEventListFragment.class);
            setRepoField(activity, "mRepositoryFragment", repoFrag);
            setRepoField(activity, "mContentListFragment", contentFrag);
            setRepoField(activity, "mCommitListFragment", commitFrag);
            setRepoField(activity, "mActivityFragment", eventFrag);
            activity.onFragmentDestroyed(repoFrag);
            assertNull(getRepoField(activity, "mRepositoryFragment"));
            activity.onFragmentDestroyed(contentFrag);
            assertNull(getRepoField(activity, "mContentListFragment"));
            activity.onFragmentDestroyed(commitFrag);
            assertNull(getRepoField(activity, "mCommitListFragment"));
            activity.onFragmentDestroyed(eventFrag);
            assertNull(getRepoField(activity, "mActivityFragment"));
        }
    }

    @Test
    public void fragmentNeedsRefresh_destroyedTypes_returnsTrue() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            assertTrue(activity.fragmentNeedsRefresh(
                    mock(com.gh4a.fragment.CommitListFragment.class)));
            assertTrue(activity.fragmentNeedsRefresh(
                    mock(com.gh4a.fragment.ContentListContainerFragment.class)));
            assertTrue(activity.fragmentNeedsRefresh(
                    mock(com.gh4a.fragment.RepositoryFragment.class)));
            assertTrue(activity.fragmentNeedsRefresh(
                    mock(com.gh4a.fragment.RepositoryEventListFragment.class)));
            setRepoField(activity, "mCommitListFragment",
                    mock(com.gh4a.fragment.CommitListFragment.class));
            assertFalse(activity.fragmentNeedsRefresh(
                    mock(com.gh4a.fragment.CommitListFragment.class)));
        }
    }

    @Test
    public void onOptionsItemSelected_zipDownload_urlNull_returnsTrue() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            Repository repo = mockRepo();
            when(repo.url()).thenReturn(null);
            setRepoField(activity, "mRepository", repo);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.zip_download, 0, "Zip");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.zip_download)));
        }
    }

    @Test
    public void onOptionsItemSelected_zipDownload_success_returnsTrue() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.zip_download, 0, "Zip");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.zip_download)));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void branchDialog_selectedRefIsSha_matchesBySha() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            List<Branch> branches = Arrays.asList(branch("main", "a1"), branch("dev", "b2"));
            List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
            RepositoryActivity.BranchSelectionDialogFragment f =
                    RepositoryActivity.BranchSelectionDialogFragment.newInstance(
                            branches, tags, "b2", "main");
            f.show(activity.getSupportFragmentManager(), "ref");
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ShadowDialog.getShownDialogs().get(0);
            assertEquals(1, dialog.getListView().getCheckedItemPosition());
        }
    }

    @Test
    public void branchDialog_nullSelectedRef_selectsDefaultBranch() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            List<Branch> branches = Arrays.asList(branch("main", "a1"), branch("dev", "b2"));
            List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
            RepositoryActivity.BranchSelectionDialogFragment f =
                    RepositoryActivity.BranchSelectionDialogFragment.newInstance(
                            branches, tags, null, "main");
            f.show(activity.getSupportFragmentManager(), "ref");
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ShadowDialog.getShownDialogs().get(0);
            assertEquals(0, dialog.getListView().getCheckedItemPosition());
        }
    }

    // A2-4（确认，生产 bug，Low）：branches 含 null 项时 onCreateDialog 的
    // 选 branches 循环有判空（464 行 if (item == null) continue），但
    // BranchAndTagAdapter.getView（529 行）直接取 item.name()，对话框
    // 布局量测即 NPE。复现：show 含 null 项的分支选择对话框。
    @Test(expected = NullPointerException.class)
    public void branchDialog_nullItemInList_crashesInAdapter() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            List<Branch> branches = new java.util.ArrayList<>(
                    Arrays.asList(branch("main", "a1"), null));
            List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
            RepositoryActivity.BranchSelectionDialogFragment f =
                    RepositoryActivity.BranchSelectionDialogFragment.newInstance(
                            branches, tags, "main", "main");
            f.show(activity.getSupportFragmentManager(), "ref");
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
        }
    }

    @Test
    public void branchDialog_itemClick_setsSelectedRefAndDismisses() throws Exception {
        try (ActivityController<RepositoryActivity> c = launch("octocat", "hello")) {
            RepositoryActivity activity = c.setup().get();
            setRepoField(activity, "mRepository", mockRepo());
            List<Branch> branches = Arrays.asList(branch("main", "a1"), branch("dev", "b2"));
            List<Branch> tags = Collections.singletonList(branch("v1.0", "c3"));
            RepositoryActivity.BranchSelectionDialogFragment f =
                    RepositoryActivity.BranchSelectionDialogFragment.newInstance(
                            branches, tags, "dev", "main");
            f.show(activity.getSupportFragmentManager(), "ref");
            ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ShadowDialog.getShownDialogs().get(0);
            android.widget.ListView listView = dialog.getListView();
            listView.performItemClick(listView.getAdapter().getView(0, null, listView), 0, 0);
            assertEquals("main", getRepoField(activity, "mSelectedRef"));
            assertFalse(dialog.isShowing());
        }
    }

}
