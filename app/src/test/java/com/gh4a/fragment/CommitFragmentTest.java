package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.git.GitCommit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** CommitFragment (F1): header + file stats render from arguments only. */
@RunWith(RobolectricTestRunner.class)
public class CommitFragmentTest {

    private static GitHubFile file(String name, String status, Integer add,
            Integer del, String patch) {
        GitHubFile.Builder b = GitHubFile.builder().filename(name).status(status)
                .additions(add).deletions(del).patch(patch);
        return b.build();
    }

    private static Commit commit(List<GitHubFile> files, String message) {
        Commit.Builder b = Commit.builder().sha("abc123").files(files);
        if (message != null) {
            b.commit(GitCommit.builder().message(message).build());
        }
        return b.build();
    }

    /** GitCommit builder helper with an explicit committer. */
    static class GitCommitHelper {
        static GitCommit commitWithCommitter(String message,
                com.meisolsson.githubsdk.model.git.GitUser committer) {
            return GitCommit.builder().message(message).committer(committer).build();
        }
    }

    private static CommitFragment attached(Commit commitArg, List<GitComment> comments) {
        CommitFragment f = CommitFragment.newInstance(
                "octocat", "hello", "abc123", commitArg, comments);
        FragmentTestHelper.attach(f);
        return f;
    }

    /** Host activity implementing the diff-viewer comment-update callback. */
    public static class ListenerActivity extends FragmentActivity
            implements CommitFragment.CommentUpdateListener {
        int calls;

        @Override
        public void onCommentsUpdated() {
            calls++;
        }
    }

    private static CommitFragment attachedToListener(
            Commit commitArg, List<GitComment> comments) {
        CommitFragment f = CommitFragment.newInstance(
                "octocat", "hello", "abc123", commitArg, comments);
        FragmentTestHelper.attachTo(ListenerActivity.class, f);
        return f;
    }

    private static void deliverLauncherResult(CommitFragment f, int resultCode)
            throws Exception {
        FragmentActivity activity = f.getActivity();
        Field launcherField = CommitFragment.class.getDeclaredField("mDiffViewerLauncher");
        launcherField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ActivityResultLauncher<Intent> launcher =
                (ActivityResultLauncher<Intent>) launcherField.get(f);
        Intent intent = new Intent();
        launcher.launch(intent);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(started);
        Shadows.shadowOf(activity).callOnActivityResult(
                started.requestCode, resultCode, new Intent());
    }

    private static Object getField(Object target, String name) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            return f.get(target);
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

    @Test
    public void newInstance_storesOwnerRepoSha() {
        CommitFragment f = CommitFragment.newInstance("o", "r", "sha1",
                commit(null, null), new ArrayList<>());
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertEquals("sha1", f.getArguments().getString("sha"));
    }

    @Test
    public void onCreate_readsCompressedCommitAndComments() {
        List<GitComment> comments = Arrays.asList(
                GitComment.builder().id(1L).body("c1").path("a.java").build());
        CommitFragment f = attached(commit(null, "msg"), comments);
        assertEquals("octocat", getField(f, "mRepoOwner"));
        assertEquals("abc123", getField(f, "mObjectSha"));
        Commit stored = (Commit) getField(f, "mCommit");
        assertEquals("abc123", stored.sha());
        List<?> storedComments = (List<?>) getField(f, "mComments");
        assertEquals(1, storedComments.size());
    }

    @Test
    public void fillHeader_titleAndBodySplitOnNewline() {
        CommitFragment f = attached(commit(null, "Fix bug\nLonger body here"), null);
        View view = f.getView();
        assertEquals("Fix bug",
                ((TextView) view.findViewById(R.id.tv_title)).getText().toString());
        assertEquals("Longer body here",
                ((TextView) view.findViewById(R.id.tv_message)).getText().toString());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.tv_title).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.tv_message).getVisibility());
    }

    @Test
    public void fillHeader_singleLineMessage_hidesMessageView() {
        CommitFragment f = attached(commit(null, "Only title"), null);
        View view = f.getView();
        assertEquals("Only title",
                ((TextView) view.findViewById(R.id.tv_title)).getText().toString());
        assertEquals(View.GONE, view.findViewById(R.id.tv_message).getVisibility());
    }

    @Test
    public void fillHeader_nullMessage_hidesBothViews() {
        CommitFragment f = attached(commit(null, null), null);
        View view = f.getView();
        assertEquals(View.GONE, view.findViewById(R.id.tv_title).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.tv_message).getVisibility());
    }

    @Test
    public void fillHeader_noAuthorNoCommitter_showsCommitterContainer() {
        // authorEqualsCommitter: both users null and no GitUser emails ->
        // not equal, so the committer container is shown.
        CommitFragment f = attached(commit(null, "msg"), null);
        assertEquals(View.VISIBLE, f.getView().findViewById(R.id.committer).getVisibility());
    }

    @Test
    public void fillHeader_sameGitUserEmail_hidesCommitterContainer() {
        Commit.Builder b = Commit.builder().sha("abc123");
        b.commit(GitCommit.builder().message("msg")
                .author(com.meisolsson.githubsdk.model.git.GitUser.builder()
                        .email("same@example.com").build())
                .committer(com.meisolsson.githubsdk.model.git.GitUser.builder()
                        .email("same@example.com").build())
                .build());
        CommitFragment f = attached(b.build(), null);
        assertEquals(View.GONE, f.getView().findViewById(R.id.committer).getVisibility());
    }

    @Test
    public void fillHeader_messageWithNewlineAndBody_splitsTitleAndMessage() {
        CommitFragment f = attached(commit(null, "title\nbody text"), null);
        View view = f.getView();
        assertEquals("title", ((TextView) view.findViewById(R.id.tv_title)).getText().toString());
        assertEquals("body text", ((TextView) view.findViewById(R.id.tv_message)).getText().toString());
    }

    @Test
    public void fillHeader_messageOnlyWhitespaceAfterNewline_hidesMessage() {
        CommitFragment f = attached(commit(null, "title\n   "), null);
        View view = f.getView();
        assertEquals("title", ((TextView) view.findViewById(R.id.tv_title)).getText().toString());
        assertEquals(View.GONE, view.findViewById(R.id.tv_message).getVisibility());
    }

    @Test
    public void fillHeader_authorWithLogin_setsGravatarTag() {
        Commit.Builder b = Commit.builder().sha("abc123")
                .author(com.meisolsson.githubsdk.model.User.builder()
                        .login("octocat").build())
                .commit(com.meisolsson.githubsdk.model.git.GitCommit.builder()
                        .message("msg").build());
        CommitFragment f = attached(b.build(), null);
        View gravatar = f.getView().findViewById(R.id.iv_gravatar);
        assertEquals("octocat", gravatar.getTag());
        assertNotNull(gravatar.getTag());
    }

    @Test
    public void fillStats_countsByStatusAndTotals() {
        List<GitHubFile> files = Arrays.asList(
                file("a.java", "added", 10, 0, "@@ patch"),
                file("b.java", "modified", 5, 3, "@@ patch"),
                file("c.java", "renamed", 0, 0, null),
                file("d.java", "removed", 0, 4, null),
                file("e.java", "unknown-status", 100, 100, null),
                file("f.java", null, 100, 100, null),
                file("g.java", "added", null, null, "@@ patch"),
                file("photo.png", "added", 0, 0, null));
        CommitFragment f = attached(commit(files, "msg"), null);
        View view = f.getView();
        // photo.png has no patch but is an image in ll_added: the || second
        // operand takes the branch and the row becomes clickable.
        assertEquals(3, ((LinearLayout) view.findViewById(R.id.ll_added)).getChildCount());
        assertEquals(View.VISIBLE, view.findViewById(R.id.card_added).getVisibility());
        assertEquals(View.VISIBLE, view.findViewById(R.id.card_changed).getVisibility());
        assertEquals(View.VISIBLE, view.findViewById(R.id.card_renamed).getVisibility());
        assertEquals(View.VISIBLE, view.findViewById(R.id.card_deleted).getVisibility());
        assertEquals(f.getString(R.string.commit_summary, 6, 15, 7),
                ((TextView) view.findViewById(R.id.tv_desc)).getText().toString());
        // 6 counted files: unknown status and null status are skipped, and
        // null additions/deletions count as zero.
        assertEquals(3, ((LinearLayout) view.findViewById(R.id.ll_added)).getChildCount());
        assertEquals(1, ((LinearLayout) view.findViewById(R.id.ll_changed)).getChildCount());
    }

    @Test
    public void fillStats_noFiles_allCardsGoneAndZeroSummary() {
        CommitFragment f = attached(commit(null, "msg"), null);
        View view = f.getView();
        assertEquals(View.GONE, view.findViewById(R.id.card_added).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.card_changed).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.card_renamed).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.card_deleted).getVisibility());
        assertEquals(f.getString(R.string.commit_summary, 0, 0, 0),
                ((TextView) view.findViewById(R.id.tv_desc)).getText().toString());
    }

    @Test
    public void fillStats_renamedFile_showsOldAndNewName() {
        GitHubFile renamed = GitHubFile.builder().filename("new.java")
                .previousFilename("old.java").status("renamed")
                .additions(0).deletions(0).build();
        CommitFragment f = attached(
                commit(Collections.singletonList(renamed), "msg"), null);
        LinearLayout renamedContainer =
                f.getView().findViewById(R.id.ll_renamed);
        assertEquals(1, renamedContainer.getChildCount());
        TextView nameView = renamedContainer.getChildAt(0).findViewById(R.id.filename);
        assertTrue(nameView.getText().toString().contains("old.java"));
        assertTrue(nameView.getText().toString().contains("new.java"));
    }

    @Test
    public void fillStats_fileWithComments_showsCount() {
        GitHubFile modified = file("a.java", "modified", 1, 0, "@@ patch");
        List<GitComment> comments = Arrays.asList(
                GitComment.builder().id(1L).path("a.java").build(),
                GitComment.builder().id(2L).path("a.java").build(),
                GitComment.builder().id(3L).path("other.java").build());
        CommitFragment f = attached(
                commit(Collections.singletonList(modified), "msg"), comments);
        LinearLayout changedContainer =
                f.getView().findViewById(R.id.ll_changed);
        TextView commentsView =
                changedContainer.getChildAt(0).findViewById(R.id.comments);
        assertEquals(View.VISIBLE, commentsView.getVisibility());
        assertEquals("2", commentsView.getText().toString());
    }

    @Test
    public void fillStats_fileWithoutPatchOrImage_isNotClickable() {
        GitHubFile removed = file("gone.java", "removed", 0, 1, null);
        CommitFragment f = attached(
                commit(Collections.singletonList(removed), "msg"), null);
        LinearLayout deletedContainer =
                f.getView().findViewById(R.id.ll_deleted);
        assertEquals(1, deletedContainer.getChildCount());
        assertEquals(false, deletedContainer.getChildAt(0).isClickable());
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        CommitFragment f = attached(commit(null, "msg"), null);
        FragmentActivity activity = f.getActivity();
        ImageView gravatar = new ImageView(activity);
        gravatar.setId(R.id.iv_gravatar);
        gravatar.setTag("octocat");
        f.onClick(gravatar);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.UserActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("login"));
    }

    @Test
    public void fillStats_commentsOnOtherFiles_hidesCount() {
        // Non-null comments, none matching the file: commentCount stays 0.
        GitHubFile modified = file("a.java", "modified", 1, 0, "@@ patch");
        List<GitComment> comments = Arrays.asList(
                GitComment.builder().id(1L).path("other.java").build());
        CommitFragment f = attached(
                commit(Collections.singletonList(modified), "msg"), comments);
        LinearLayout changedContainer =
                f.getView().findViewById(R.id.ll_changed);
        TextView commentsView =
                changedContainer.getChildAt(0).findViewById(R.id.comments);
        assertEquals(View.GONE, commentsView.getVisibility());
    }

    @Test
    public void onClick_gravatarNullTag_startsNothing() {
        CommitFragment f = attached(commit(null, "msg"), null);
        FragmentActivity activity = f.getActivity();
        ImageView gravatar = new ImageView(activity);
        gravatar.setId(R.id.iv_gravatar);
        gravatar.setTag(null);
        f.onClick(gravatar);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void fillHeader_committerWithDate_usesCommitterDate() {
        com.meisolsson.githubsdk.model.git.GitUser committer =
                com.meisolsson.githubsdk.model.git.GitUser.builder()
                        .name("Committer").email("c@example.com")
                        .date(new java.util.Date(1_700_000_000_000L)).build();
        Commit commitArg = Commit.builder().sha("abc123")
                .commit(com.gh4a.fragment.CommitFragmentTest.GitCommitHelper.commitWithCommitter(
                        "msg", committer))
                .build();
        CommitFragment f = attached(commitArg, null);
        assertEquals(View.VISIBLE,
                f.getView().findViewById(R.id.committer).getVisibility());
    }

    @Test
    public void onClick_fileView_launchesDiffViewer() {
        GitHubFile code = file("Main.java", "modified", 1, 0, "@@ -1 +1 @@");
        List<GitComment> comments = Arrays.asList(
                GitComment.builder().id(5L).path("Main.java").build(),
                GitComment.builder().id(6L).path("Main.java").build());
        CommitFragment f = attached(commit(null, "msg"), comments);
        FragmentActivity activity = f.getActivity();
        View fileView = new View(activity);
        fileView.setId(R.id.filename);
        fileView.setTag(code);
        f.onClick(fileView);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.CommitDiffViewerActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void handleFileClick_imageFile_launchesFileViewer() {
        CommitFragment f = attached(commit(null, "msg"), null);
        FragmentActivity activity = f.getActivity();
        GitHubFile image = file("screenshot.png", "added", 0, 0, null);
        f.handleFileClick(image);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.FileViewerActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void handleFileClick_codeFile_launchesDiffViewer() {
        CommitFragment f = attached(commit(null, "msg"), null);
        FragmentActivity activity = f.getActivity();
        GitHubFile code = file("Main.java", "modified", 1, 0, "@@ -1 +1 @@");
        f.handleFileClick(code);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.CommitDiffViewerActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void commentsForFile_filtersByPath() throws Exception {
        List<GitComment> comments = Arrays.asList(
                GitComment.builder().id(1L).path("a.java").build(),
                GitComment.builder().id(2L).path("b.java").build());
        CommitFragment f = attached(commit(null, "msg"), comments);
        Method m = CommitFragment.class.getDeclaredMethod(
                "commentsForFile", GitHubFile.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ArrayList<GitComment> result = (ArrayList<GitComment>) m.invoke(
                f, file("a.java", "modified", 0, 0, null));
        assertEquals(1, result.size());
        assertEquals(1L, (long) result.get(0).id());
        assertNull(m.invoke(f, file("zzz.java", "modified", 0, 0, null)));
    }

    @Test
    public void commentsForFile_nullComments_returnsNull() throws Exception {
        CommitFragment f = attached(commit(null, "msg"), null);
        Method m = CommitFragment.class.getDeclaredMethod(
                "commentsForFile", GitHubFile.class);
        m.setAccessible(true);
        assertNull(m.invoke(f, file("a.java", "modified", 0, 0, null)));
    }

    @Test
    public void onRefresh_isNoop() {
        attached(commit(null, "msg"), null).onRefresh();
    }

    // ---- ActivityResultLauncher: diff viewer returns ----

    @Test
    public void diffViewerLauncher_resultOk_notifiesListenerHost() throws Exception {
        CommitFragment f = attachedToListener(commit(null, "msg"), null);
        ListenerActivity activity = (ListenerActivity) f.getActivity();
        deliverLauncherResult(f, Activity.RESULT_OK);
        assertEquals(1, activity.calls);
    }

    @Test
    public void diffViewerLauncher_resultOk_plainHost_doesNothing() throws Exception {
        CommitFragment f = attached(commit(null, "msg"), null);
        deliverLauncherResult(f, Activity.RESULT_OK);
    }

    @Test
    public void diffViewerLauncher_resultCanceled_doesNotNotify() throws Exception {
        CommitFragment f = attachedToListener(commit(null, "msg"), null);
        ListenerActivity activity = (ListenerActivity) f.getActivity();
        deliverLauncherResult(f, Activity.RESULT_CANCELED);
        assertEquals(0, activity.calls);
    }
}
