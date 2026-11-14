package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;

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
}
