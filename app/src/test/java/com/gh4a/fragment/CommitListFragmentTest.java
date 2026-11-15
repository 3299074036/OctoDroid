package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.CommitActivity;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.ContentType;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.gh4a.ServiceFactory;
import com.gh4a.adapter.CommitAdapter;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.widget.ContextMenuAwareRecyclerView;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;

import org.mockito.MockedStatic;
import org.robolectric.Robolectric;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** CommitListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class CommitListFragmentTest {

    public static class NoLoad extends CommitListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String owner, String repo, String ref,
            String path, ContentType type) {
        NoLoad f = new NoLoad();
        f.setArguments(CommitListFragment.newInstance(owner, repo, ref, path, type)
                .getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_fromRepo_blankRefUsesDefaultBranch() {
        Repository repo = Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").defaultBranch("main").build();
        CommitListFragment f = CommitListFragment.newInstance(repo, "");
        assertEquals("octocat", f.getArguments().getString("owner"));
        assertEquals("hello", f.getArguments().getString("repo"));
        assertEquals("main", f.getArguments().getString("ref"));
    }

    @Test
    public void newInstance_fromRepo_givenRefWins() {
        Repository repo = Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").defaultBranch("main").build();
        CommitListFragment f = CommitListFragment.newInstance(repo, "dev");
        assertEquals("dev", f.getArguments().getString("ref"));
    }

    @Test
    public void newInstance_fullArgs_storesAll() {
        CommitListFragment f = CommitListFragment.newInstance(
                "o", "r", "main", "src/Foo.java", ContentType.File);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertEquals("main", f.getArguments().getString("ref"));
        assertEquals("src/Foo.java", f.getArguments().getString("path"));
        assertEquals(ContentType.File, f.getArguments().getSerializable("type"));
    }

    @Test
    public void getEmptyTextResId_isNoCommits() {
        assertEquals(R.string.no_commits_found,
                attached("o", "r", "main", null, null).getEmptyTextResId());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r", "main", null, null);
        Single<Response<Page<Commit>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("o", "r", "main", null, null).onCreateAdapter());
    }

    @Test
    public void onSaveInstanceState_persistsFollowRenamesFlag() {
        NoLoad f = attached("o", "r", "main", null, null);
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("follow_renames"));
        assertFalse(out.getBoolean("follow_renames"));
        assertTrue(out.containsKey("rename_follow_data"));
        assertTrue(out.containsKey("oldest_commit"));
    }

    @Test
    public void resetSubject_clearsRenameFollowState() {
        NoLoad f = attached("o", "r", "main", null, null);
        f.resetSubject();
        // After reset, loadPage still builds a Single (uses mRef, not follow data).
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onCreateMenu_fileType_inflatesMenu() {
        NoLoad f = attached("o", "r", "main", "a.java", ContentType.File);
        android.view.Menu menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        f.onCreateMenu(menu, f.getActivity().getMenuInflater());
        assertNotNull(menu.findItem(R.id.follow_renames));
    }

    @Test
    public void onCreateMenu_nullType_inflatesNothing() {
        NoLoad f = attached("o", "r", "main", null, null);
        android.view.Menu menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        f.onCreateMenu(menu, f.getActivity().getMenuInflater());
        assertEquals(0, menu.size());
    }

    @Test
    public void onPrepareMenu_checksFollowItem() {
        NoLoad f = attached("o", "r", "main", "a.java", ContentType.File);
        android.view.Menu menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        f.onCreateMenu(menu, f.getActivity().getMenuInflater());
        f.onPrepareMenu(menu);
        assertFalse(menu.findItem(R.id.follow_renames).isChecked());
    }

    @Test
    public void onMenuItemSelected_otherItem_returnsFalse() {
        NoLoad f = attached("o", "r", "main", null, null);
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(999999);
        assertFalse(f.onMenuItemSelected(item));
    }

    @Test
    public void onItemClick_nullCommit_doesNothing() {
        NoLoad f = attached("o", "r", "main", null, null);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_commitWithoutUrlOrSha_doesNothing() {
        NoLoad f = attached("o", "r", "main", null, null);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(Commit.builder().build());
        f.onItemClick(Commit.builder().sha("abc").build());
        f.onItemClick(Commit.builder()
                .url("https://api.github.com/repos/o/r/commits/abc").build());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_shortUrl_doesNothing() {
        NoLoad f = attached("o", "r", "main", null, null);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(Commit.builder().sha("abc")
                .url("https://api.github.com/x").build());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_validCommit_launchesCommitActivity() {
        NoLoad f = attached("o", "r", "main", null, null);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(Commit.builder().sha("abc123")
                .url("https://api.github.com/repos/octocat/hello/commits/abc123")
                .build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(CommitActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("owner"));
        assertEquals("hello", started.getExtras().getString("repo"));
        assertEquals("abc123", started.getExtras().getString("sha"));
    }

    @Test
    public void onContextItemSelected_nonRecyclerMenuInfo_returnsFalse() {
        NoLoad f = attached("o", "r", "main", null, null);
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.select_as_branch_ref);
        when(item.getMenuInfo()).thenReturn(null);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onCreateContextMenu_addsUseAsRefItem() {
        NoLoad f = attached("o", "r", "main", null, null);
        android.view.ContextMenu menu = mock(android.view.ContextMenu.class);
        f.onCreateContextMenu(menu, f.getView(), null);
        org.mockito.Mockito.verify(menu).add(
                android.view.Menu.NONE, R.id.select_as_branch_ref,
                android.view.Menu.NONE, R.string.commit_use_as_ref);
    }

    // ---------- §6 close-out: residuals ----------

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

    private static void setField(Object target, String name, Object value) throws Exception {
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

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < 150 && !cond.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            idleMain();
        }
        assertTrue(cond.getAsBoolean());
    }

    private static ContextMenuAwareRecyclerView.RecyclerContextMenuInfo menuInfo(int position)
            throws Exception {
        java.lang.reflect.Constructor<ContextMenuAwareRecyclerView
                .RecyclerContextMenuInfo> c =
                ContextMenuAwareRecyclerView.RecyclerContextMenuInfo.class
                        .getDeclaredConstructor(int.class, long.class);
        c.setAccessible(true);
        return c.newInstance(position, 0L);
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> fileRenameFollowDataClass() throws Exception {
        return (Class<Object>) Class.forName(
                "com.gh4a.fragment.CommitListFragment$FileRenameFollowData");
    }

    private static Object fileRenameFollowData(String sha, String fileName) throws Exception {
        java.lang.reflect.Constructor<?> c =
                fileRenameFollowDataClass().getDeclaredConstructor(String.class, String.class);
        c.setAccessible(true);
        return c.newInstance(sha, fileName);
    }

    private static CommitAdapter adapterOf(CommitListFragment f) throws Exception {
        return (CommitAdapter) field(f, "mAdapter");
    }

    private static Page<Commit> pageOf(List<Commit> items, Integer next) {
        Page.Builder<Commit> b = Page.builder();
        b.items(items);
        if (next != null) {
            b.next(next);
        }
        return b.build();
    }

    private static final class MockedService implements AutoCloseable {
        final MockedStatic<ServiceFactory> mocked;
        final RepositoryCommitService service;
        final java.util.concurrent.atomic.AtomicInteger getCommitsCalls =
                new java.util.concurrent.atomic.AtomicInteger();

        MockedService() {
            service = mock(RepositoryCommitService.class);
            mocked = mockStatic(ServiceFactory.class);
            mocked.when(() -> ServiceFactory.get(
                    eq(RepositoryCommitService.class), anyBoolean())).thenReturn(service);
        }

        void stubCommits(Response<Page<Commit>> response) {
            when(service.getCommits(anyString(), anyString(), anyString(),
                    nullable(String.class), anyLong()))
                    .thenAnswer(inv -> {
                        getCommitsCalls.incrementAndGet();
                        return Single.just(response);
                    });
        }

        int invocationCount() {
            return getCommitsCalls.get();
        }

        @Override
        public void close() {
            mocked.close();
        }
    }

    /** Host implementing ContextSelectionCallback, for the select-as-base path. */
    public static class CallbackHost extends FragmentActivity
            implements CommitListFragment.ContextSelectionCallback {
        Commit selectedBase;

        @Override
        public boolean baseSelectionAllowed() {
            return true;
        }

        @Override
        public void onCommitSelectedAsBase(Commit commit) {
            selectedBase = commit;
        }
    }

    @Test
    public void onCreate_restoresFollowStateFromSavedInstanceState() throws Exception {
        Object renameData = fileRenameFollowData("sha1", "src/Old.java");
        Commit oldest = Commit.builder().sha("old1")
                .url("https://api.github.com/repos/o/r/commits/old1").build();
        Bundle saved = new Bundle();
        saved.putBoolean("follow_renames", true);
        saved.putParcelable("rename_follow_data", (Parcelable) renameData);
        saved.putParcelable("oldest_commit", oldest);
        NoLoad f = new NoLoad();
        f.setArguments(CommitListFragment.newInstance(
                "o", "r", "main", "src/Foo.java", ContentType.File).getArguments());
        java.lang.reflect.Constructor<?> c =
                Fragment.SavedState.class.getDeclaredConstructor(Bundle.class);
        c.setAccessible(true);
        f.setInitialSavedState((Fragment.SavedState) c.newInstance(saved));
        // Attach only to CREATED: onCreate has run (state restored) but
        // onViewCreated/resetSubject() has not yet wiped the restored fields.
        FragmentActivity activity =
                Robolectric.buildActivity(FragmentActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        assertTrue((Boolean) field(f, "mFollowRenames"));
        Object data = field(f, "mMostRecentRenameFollowData");
        assertNotNull(data);
        assertEquals("sha1", field(data, "commitSha"));
        assertEquals("src/Old.java", field(data, "fileName"));
        Commit restored = (Commit) field(f, "mOldestLoadedCommit");
        assertNotNull(restored);
        assertEquals("old1", restored.sha());
    }

    @Test
    public void onCreateMenu_submoduleType_inflatesMenu() {
        NoLoad f = attached("o", "r", "main", "a.java", ContentType.Submodule);
        android.view.Menu menu =
                new androidx.appcompat.view.menu.MenuBuilder(f.getActivity());
        f.onCreateMenu(menu, f.getActivity().getMenuInflater());
        assertNotNull(menu.findItem(R.id.follow_renames));
    }

    @Test
    public void onMenuItemSelected_followRenames_togglesAndRefreshes() throws Exception {
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(Collections.emptyList(), null)));
            CommitListFragment f = CommitListFragment.newInstance(
                    "o", "r", "main", "a.java", ContentType.File);
            FragmentActivity activity = FragmentTestHelper.attach(f);
            // The initial load assigns mSubscription; the toggle must trigger
            // onRefresh() -> load(true), which disposes it and assigns a new
            // one. (The RxLoader transformer never delivers the load under
            // Robolectric, so the refresh is observed via the reassigned
            // subscription rather than a service call.)
            Object subscriptionBefore = field(f, "mSubscription");
            assertNotNull(subscriptionBefore);
            android.view.Menu menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            f.onCreateMenu(menu, activity.getMenuInflater());
            MenuItem item = menu.findItem(R.id.follow_renames);
            assertNotNull(item);
            assertTrue(f.onMenuItemSelected(item));
            assertTrue((Boolean) field(f, "mFollowRenames"));
            assertTrue(item.isChecked());
            assertNotSame(subscriptionBefore, field(f, "mSubscription"));
            // Toggle back: covers the other direction of the boolean
            // inversion on the toggle line.
            Object subscriptionBeforeSecond = field(f, "mSubscription");
            assertTrue(f.onMenuItemSelected(item));
            assertFalse((Boolean) field(f, "mFollowRenames"));
            assertFalse(item.isChecked());
            assertNotSame(subscriptionBeforeSecond, field(f, "mSubscription"));
        }
    }

    @Test
    public void onDestroyView_afterDetach_skipsMenuProviderRemoval() {
        NoLoad f = attached("o", "r", "main", null, null);
        FragmentActivity activity = f.getActivity();
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        assertFalse(f.isAdded());
        assertNull(f.getActivity());
        // getActivity() == null: the removeMenuProvider branch is skipped.
        f.onDestroyView();
    }

    @Test
    public void onItemClick_validCommit_resultOk_triggersRefresh() throws Exception {
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(Collections.emptyList(), null)));
            CommitListFragment f = CommitListFragment.newInstance(
                    "o", "r", "main", null, null);
            FragmentActivity activity = FragmentTestHelper.attach(f);
            // The initial load assigns mSubscription; the RESULT_OK callback
            // must trigger onRefresh() -> load(true), which disposes it and
            // assigns a new one (see the note in the follow-renames test).
            Object subscriptionBefore = field(f, "mSubscription");
            assertNotNull(subscriptionBefore);
            f.onItemClick(Commit.builder().sha("abc123")
                    .url("https://api.github.com/repos/octocat/hello/commits/abc123")
                    .build());
            Intent started = Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertNotNull(started);
            Shadows.shadowOf(activity).receiveResult(
                    started, Activity.RESULT_OK, new Intent());
            idleMain();
            assertNotSame(subscriptionBefore, field(f, "mSubscription"));
        }
    }

    private static NoLoad attachedWithCommits() throws Exception {
        NoLoad f = attached("o", "r", "main", null, null);
        CommitAdapter adapter = adapterOf(f);
        assertNotNull(adapter);
        adapter.addAll(Arrays.asList(
                Commit.builder().sha("c1")
                        .url("https://api.github.com/repos/o/r/commits/c1").build(),
                Commit.builder().sha("c2")
                        .url("https://api.github.com/repos/o/r/commits/c2").build()));
        return f;
    }

    private static MenuItem contextItem(int itemId,
            ContextMenuAwareRecyclerView.RecyclerContextMenuInfo info) {
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getMenuInfo()).thenReturn(info);
        return item;
    }

    @Test
    public void onContextItemSelected_matchingInfo_negativePosition_returnsFalse()
            throws Exception {
        NoLoad f = attachedWithCommits();
        assertFalse(f.onContextItemSelected(
                contextItem(R.id.select_as_branch_ref, menuInfo(-1))));
    }

    @Test
    public void onContextItemSelected_matchingInfo_positionBeyondCount_returnsFalse()
            throws Exception {
        NoLoad f = attachedWithCommits();
        int count = adapterOf(f).getItemCount();
        assertFalse(f.onContextItemSelected(
                contextItem(R.id.select_as_branch_ref, menuInfo(count))));
    }

    @Test
    public void onContextItemSelected_matchingInfo_footerPosition_nullCommit_returnsFalse()
            throws Exception {
        NoLoad f = attachedWithCommits();
        int count = adapterOf(f).getItemCount();
        // Last position is the footer view: in bounds, but maps to no item.
        assertFalse(f.onContextItemSelected(
                contextItem(R.id.select_as_branch_ref, menuInfo(count - 1))));
    }

    @Test
    public void onContextItemSelected_matchingInfo_validPosition_noCallback_returnsFalse()
            throws Exception {
        NoLoad f = attachedWithCommits();
        assertFalse(f.onContextItemSelected(
                contextItem(R.id.select_as_branch_ref, menuInfo(0))));
    }

    @Test
    public void onContextItemSelected_matchingInfo_validPosition_withCallback_selectsBase()
            throws Exception {
        NoLoad f = new NoLoad();
        f.setArguments(CommitListFragment.newInstance(
                "o", "r", "main", null, null).getArguments());
        CallbackHost host = FragmentTestHelper.attachTo(CallbackHost.class, f);
        CommitAdapter adapter = adapterOf(f);
        adapter.addAll(Arrays.asList(
                Commit.builder().sha("c1")
                        .url("https://api.github.com/repos/o/r/commits/c1").build()));
        assertTrue(f.onContextItemSelected(
                contextItem(R.id.select_as_branch_ref, menuInfo(0))));
        assertNotNull(host.selectedBase);
        assertEquals("c1", host.selectedBase.sha());
    }

    @Test
    public void onContextItemSelected_matchingInfo_validPosition_otherItem_delegatesToSuper()
            throws Exception {
        NoLoad f = attachedWithCommits();
        assertFalse(f.onContextItemSelected(contextItem(999999, menuInfo(0))));
    }

    @Test
    public void loadPage_successWithNextPage_setsOldestCommit() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("c1").url("u1").build(),
                Commit.builder().sha("c2").url("u2").build());
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(commits, 2)));
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            assertEquals(2, result.body().items().size());
            assertEquals("c2", ((Commit) field(f, "mOldestLoadedCommit")).sha());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_successEmptyItems_doesNotSetOldest() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(Collections.emptyList(), null)));
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            assertTrue(result.body().items().isEmpty());
            assertNull(field(f, "mOldestLoadedCommit"));
        }
    }

    @Test
    public void loadPage_nullItems_treatedAsEmpty() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        @SuppressWarnings("unchecked")
        Page<Commit> nullPage = mock(Page.class);
        when(nullPage.items()).thenReturn(null);
        when(nullPage.next()).thenReturn(null);
        when(nullPage.prev()).thenReturn(null);
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(nullPage));
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            assertNull(field(f, "mOldestLoadedCommit"));
        }
    }

    @Test
    public void loadPage_unsuccessfulResponse_passesThrough() throws Exception {
        NoLoad f = attached("o", "r", "main", null, null);
        @SuppressWarnings("unchecked")
        Response<Page<Commit>> error = mock(Response.class);
        when(error.isSuccessful()).thenReturn(false);
        when(error.code()).thenReturn(500);
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(error);
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertFalse(result.isSuccessful());
            assertEquals(500, result.code());
        }
    }

    @Test
    public void loadPage_conflict409_returnsDummyPage() throws Exception {
        NoLoad f = attached("o", "r", "main", null, null);
        @SuppressWarnings("unchecked")
        Response<Page<Commit>> conflict = mock(Response.class);
        when(conflict.isSuccessful()).thenReturn(false);
        when(conflict.code()).thenReturn(409);
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(conflict);
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            assertTrue(result.body() instanceof ApiHelpers.DummyPage);
        }
    }

    private static Commit renamedCommit() {
        GitHubFile renamed = mock(GitHubFile.class);
        when(renamed.filename()).thenReturn("src/Foo.java");
        when(renamed.previousFilename()).thenReturn("src/Old.java");
        Commit parent = Commit.builder().sha("parentSha").build();
        return Commit.builder().sha("abc").files(Arrays.asList(renamed))
                .parents(Arrays.asList(parent)).build();
    }

    @Test
    public void loadPage_lastPageFollowRenames_renameDetected_returnsFollowPage()
            throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        Commit actual = renamedCommit();
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(commits, null)));
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(Response.success(actual)));
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            assertEquals(1, result.body().items().size());
            assertEquals(Integer.valueOf(1), result.body().next());
            Object data = field(f, "mMostRecentRenameFollowData");
            assertNotNull(data);
            assertEquals("parentSha", field(data, "commitSha"));
            assertEquals("src/Old.java", field(data, "fileName"));
            verify(ms.service).getCommit(eq("o"), eq("r"), eq("abc"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_noRename_returnsOriginal() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        GitHubFile notRenamed = mock(GitHubFile.class);
        when(notRenamed.filename()).thenReturn("src/Other.java");
        when(notRenamed.previousFilename()).thenReturn("src/Old.java");
        Commit actual = Commit.builder().sha("abc")
                .files(Arrays.asList(notRenamed))
                .parents(Arrays.asList(Commit.builder().sha("p").build())).build();
        try (MockedService ms = new MockedService()) {
            Response<Page<Commit>> original = Response.success(pageOf(commits, null));
            ms.stubCommits(original);
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(Response.success(actual)));
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_matchingFileWithoutPrevious_returnsOriginal()
            throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        // Filename matches the tracked path, but there is no previous
        // filename: the rename filter rejects the file.
        GitHubFile matchedNoPrevious = mock(GitHubFile.class);
        when(matchedNoPrevious.filename()).thenReturn("src/Foo.java");
        when(matchedNoPrevious.previousFilename()).thenReturn(null);
        Commit actual = Commit.builder().sha("abc")
                .files(Arrays.asList(matchedNoPrevious))
                .parents(Arrays.asList(Commit.builder().sha("p").build())).build();
        try (MockedService ms = new MockedService()) {
            Response<Page<Commit>> original = Response.success(pageOf(commits, null));
            ms.stubCommits(original);
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(Response.success(actual)));
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_missingFilesOrParents_returnsOriginal()
            throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        Commit noFiles = Commit.builder().sha("abc").build();
        Commit noParents = Commit.builder().sha("abc")
                .files(Arrays.asList(mock(GitHubFile.class))).build();
        Commit emptyParents = Commit.builder().sha("abc")
                .files(Arrays.asList(mock(GitHubFile.class)))
                .parents(Collections.emptyList()).build();
        try (MockedService ms = new MockedService()) {
            Response<Page<Commit>> original = Response.success(pageOf(commits, null));
            ms.stubCommits(original);
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(Response.success(noFiles)))
                    .thenReturn(Single.just(Response.success(noParents)))
                    .thenReturn(Single.just(Response.success(emptyParents)));
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_commitUnsuccessful_returnsOriginal()
            throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        @SuppressWarnings("unchecked")
        Response<Commit> failed = mock(Response.class);
        when(failed.isSuccessful()).thenReturn(false);
        try (MockedService ms = new MockedService()) {
            Response<Page<Commit>> original = Response.success(pageOf(commits, null));
            ms.stubCommits(original);
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(failed));
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_nullCommitBody_returnsOriginal() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        List<Commit> commits = Arrays.asList(
                Commit.builder().sha("abc").url("u").build());
        try (MockedService ms = new MockedService()) {
            Response<Page<Commit>> original = Response.success(pageOf(commits, null));
            ms.stubCommits(original);
            when(ms.service.getCommit(anyString(), anyString(), anyString()))
                    .thenReturn(Single.just(Response.success(null)));
            assertSame(original, f.loadPage(1, false).blockingGet());
            assertNull(field(f, "mMostRecentRenameFollowData"));
        }
    }

    @Test
    public void loadPage_lastPageFollowRenames_oldestNull_skipsRename() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mFollowRenames", true);
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(Collections.emptyList(), null)));
            Response<Page<Commit>> result = f.loadPage(1, false).blockingGet();
            assertTrue(result.isSuccessful());
            verify(ms.service, never()).getCommit(anyString(), anyString(), anyString());
        }
    }

    @Test
    public void loadPage_withRenameFollowData_usesFollowRefAndPath() throws Exception {
        NoLoad f = attached("o", "r", "main", "src/Foo.java", ContentType.File);
        setField(f, "mMostRecentRenameFollowData",
                fileRenameFollowData("followSha", "src/Old.java"));
        try (MockedService ms = new MockedService()) {
            ms.stubCommits(Response.success(pageOf(Collections.emptyList(), null)));
            f.loadPage(1, false).blockingGet();
            verify(ms.service).getCommits(
                    eq("o"), eq("r"), eq("followSha"), eq("src/Old.java"), eq(1L));
        }
    }

    @Test
    public void fileRenameFollowData_parcelableRoundTrip() throws Exception {
        Object data = fileRenameFollowData("sha9", "a/b.java");
        java.lang.reflect.Method describe =
                fileRenameFollowDataClass().getDeclaredMethod("describeContents");
        describe.setAccessible(true);
        assertEquals(0, describe.invoke(data));
        Parcel parcel = Parcel.obtain();
        java.lang.reflect.Method write =
                fileRenameFollowDataClass().getDeclaredMethod(
                        "writeToParcel", Parcel.class, int.class);
        write.setAccessible(true);
        write.invoke(data, parcel, 0);
        parcel.setDataPosition(0);
        java.lang.reflect.Field creatorField =
                fileRenameFollowDataClass().getDeclaredField("CREATOR");
        creatorField.setAccessible(true);
        Parcelable.Creator<?> creator = (Parcelable.Creator<?>) creatorField.get(null);
        Object restored = creator.createFromParcel(parcel);
        assertEquals("sha9", field(restored, "commitSha"));
        assertEquals("a/b.java", field(restored, "fileName"));
        Object[] array = creator.newArray(3);
        assertEquals(3, array.length);
        parcel.recycle();
    }
}
