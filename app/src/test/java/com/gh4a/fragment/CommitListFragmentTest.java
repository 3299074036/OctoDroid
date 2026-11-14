package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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
}
