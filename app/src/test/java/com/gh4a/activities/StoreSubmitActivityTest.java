package com.gh4a.activities;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import com.gh4a.R;
import com.gh4a.fragment.StorePrefs;

import java.lang.reflect.Method;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gh4a.fragment.StoreRepo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class StoreSubmitActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static String[] parseOwnerRepo(String input) throws Exception {
        Method m = StoreSubmitActivity.class.getDeclaredMethod("parseOwnerRepo", String.class);
        m.setAccessible(true);
        return (String[]) m.invoke(null, input);
    }

    // ---- parseOwnerRepo（私有静态纯逻辑，反射覆盖） ----

    @Test
    public void parseOwnerRepo_nullAndEmpty_returnsNull() throws Exception {
        assertNull(parseOwnerRepo(null));
        assertNull(parseOwnerRepo(""));
        assertNull(parseOwnerRepo("   "));
    }

    @Test
    public void parseOwnerRepo_bareOwnerRepo() throws Exception {
        assertArrayEquals(new String[]{"octocat", "hello"}, parseOwnerRepo("octocat/hello"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("  octocat/hello  "));
    }

    @Test
    public void parseOwnerRepo_httpsUrl() throws Exception {
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("https://github.com/octocat/hello"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("HTTPS://github.com/octocat/hello"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("https://github.com/octocat/hello/"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("https://github.com/octocat/hello.git"));
    }

    @Test
    public void parseOwnerRepo_httpAndBareDomain() throws Exception {
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("http://github.com/octocat/hello"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("github.com/octocat/hello"));
    }

    @Test
    public void parseOwnerRepo_invalidShapes_returnsNull() throws Exception {
        assertNull(parseOwnerRepo("octocat"));
        assertNull(parseOwnerRepo("octocat/"));
        assertNull(parseOwnerRepo("/hello"));
        assertNull(parseOwnerRepo("a/b/c"));
        assertNull(parseOwnerRepo("https://github.com/octocat"));
        assertNull(parseOwnerRepo("https://example.com/octocat/hello"));
    }

    @Test
    public void parseOwnerRepo_trailingSlashesAndGitSuffix() throws Exception {
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("octocat/hello///"));
        assertArrayEquals(new String[]{"octocat", "hello"},
                parseOwnerRepo("octocat/hello.git"));
    }

    // ---- Activity 行为 ----

    @Test
    public void makeIntent_targetsActivity() {
        assertEquals(StoreSubmitActivity.class.getName(),
                StoreSubmitActivity.makeIntent(context()).getComponent().getClassName());
    }

    @Test
    public void launches_titleIsStoreSubmit() {
        StorePrefs.removeFollowed(context(), "octocat/hello");
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_submit),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void invalidInput_showsInvalidRepoToast() {
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            EditText input = findEditText(activity);
            assertNotNull(input);
            input.setText("not-a-repo");
            // 点预览按钮（第 2 个 Button 是预览按钮，第 1 个在内容区之后；
            // 更稳妥：直接触发 IME DONE，等价于点预览）
            input.onEditorAction(EditorInfo.IME_ACTION_DONE);
            assertEquals(activity.getString(R.string.store_invalid_repo),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void editorAction_nonDoneAction_returnsFalseWithoutToast() {
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            EditText input = findEditText(activity);
            input.setText("bad");
            // 非 DONE 的 action 不触发 fetchPreview、不弹 Toast
            ShadowToast.reset();
            input.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
            assertNull(ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void renderFollowed_emptyShowsEmptyText() {
        for (String name : StorePrefs.getFollowed(context())) {
            StorePrefs.removeFollowed(context(), name);
        }
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            assertTrue(collectText(activity).contains(
                    activity.getString(R.string.store_followed_empty)));
        }
    }

    @Test
    public void renderFollowed_withFollowedShowsName() {
        for (String name : StorePrefs.getFollowed(context())) {
            StorePrefs.removeFollowed(context(), name);
        }
        StorePrefs.addFollowed(context(), "octocat/hello");
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            assertTrue(collectText(activity).contains("octocat/hello"));
            assertTrue(collectText(activity).contains(
                    activity.getString(R.string.store_unfollow)));
        } finally {
            StorePrefs.removeFollowed(context(), "octocat/hello");
        }
    }

    @Test
    public void onRefresh_rerendersFollowedWithoutCrash() {
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    private EditText findEditText(StoreSubmitActivity activity) {
        android.view.ViewGroup root =
                (android.view.ViewGroup) activity.findViewById(android.R.id.content);
        return findEditText(root);
    }

    private EditText findEditText(android.view.View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String collectText(StoreSubmitActivity activity) {
        android.view.ViewGroup root =
                (android.view.ViewGroup) activity.findViewById(android.R.id.content);
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }

    private void collectText(android.view.View view, StringBuilder sb) {
        if (view instanceof android.widget.TextView) {
            sb.append(((android.widget.TextView) view).getText()).append('\n');
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), sb);
            }
        }
    }

    private ActivityController<StoreSubmitActivity> launch() {
        return Robolectric.buildActivity(StoreSubmitActivity.class);
    }

    private static StoreRepo repo(String fullName, String name) {
        return new StoreRepo(fullName, "octocat", name,
                "A demo app", "Kotlin", null, 123, 45, 1000L,
                new ArrayList<>(), new java.util.HashSet<>(), "v1.0", 0L,
                EnumSet.noneOf(com.gh4a.fragment.StoreClassifier.Platform.class),
                3, 4.5, 100.0, 0.5, new ArrayList<>(), "tools");
    }

    private static void callRenderPreview(StoreSubmitActivity activity, StoreRepo item)
            throws Exception {
        Method m = StoreSubmitActivity.class.getDeclaredMethod("renderPreview", StoreRepo.class);
        m.setAccessible(true);
        m.invoke(activity, item);
    }

    private static void callFetchPreview(StoreSubmitActivity activity) throws Exception {
        Method m = StoreSubmitActivity.class.getDeclaredMethod("fetchPreview");
        m.setAccessible(true);
        m.invoke(activity);
    }

    private static EditText urlInput(StoreSubmitActivity activity) throws Exception {
        Field f = StoreSubmitActivity.class.getDeclaredField("mUrlInput");
        f.setAccessible(true);
        return (EditText) f.get(activity);
    }

    private static android.widget.Button findButtonByText(android.view.ViewGroup root,
            String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child instanceof android.widget.Button
                    && text.equals(((android.widget.Button) child).getText().toString())) {
                return (android.widget.Button) child;
            }
            if (child instanceof android.view.ViewGroup) {
                android.widget.Button found =
                        findButtonByText((android.view.ViewGroup) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void renderPreview_withStoreRepo_showsNameMetaAndFollowButton() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            callRenderPreview(activity, repo("octocat/unique-preview-a", "unique-preview-a"));
            String text = collectText(activity);
            assertTrue(text.contains("octocat/unique-preview-a"));
            assertTrue(text.contains("Kotlin"));
            assertTrue(text.contains("123"));
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.Button follow = findButtonByText(root,
                    activity.getString(R.string.store_follow));
            assertNotNull(follow);
        }
    }

    @Test
    public void renderPreview_followButtonClick_marksFollowed() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            callRenderPreview(activity, repo("octocat/unique-preview-b", "unique-preview-b"));
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.Button follow = findButtonByText(root,
                    activity.getString(R.string.store_follow));
            assertNotNull(follow);
            follow.performClick();
            assertTrue(StorePrefs.isFollowed(activity, "octocat/unique-preview-b"));
            assertFalse(follow.isEnabled());
            assertEquals(activity.getString(R.string.store_followed),
                    follow.getText().toString());
        }
    }

    @Test
    public void fetchPreview_invalidUrl_showsInvalidRepoToast() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            urlInput(activity).setText("not a repo");
            callFetchPreview(activity);
            assertEquals(activity.getString(R.string.store_invalid_repo),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void fetchPreview_validUrl_networkFailure_showsErrorView() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            urlInput(activity).setText("octocat/hello");
            callFetchPreview(activity);
            boolean found = false;
            for (int i = 0; i < 30 && !found; i++) {
                ShadowLooper.idleMainLooper();
                Thread.sleep(500);
                found = collectText(activity)
                        .contains(activity.getString(R.string.store_fetch_failed));
            }
            assertTrue(found);
        }
    }

    // ---- A2 第二轮补缺口：StoreSubmitActivity ----

    private static StoreRepo repoWithPlatforms(String fullName, String name) {
        return new StoreRepo(fullName, "octocat", name,
                "A demo app", "Kotlin", null, 123, 45, 1000L,
                new ArrayList<>(), new java.util.HashSet<>(), "v1.0", 0L,
                EnumSet.of(com.gh4a.fragment.StoreClassifier.Platform.ANDROID),
                3, 4.5, 100.0, 0.5, new ArrayList<>(), "tools");
    }

    private static StoreRepo repoWithLanguage(String fullName, String name, String language) {
        return new StoreRepo(fullName, "octocat", name,
                "A demo app", language, null, 123, 45, 1000L,
                new ArrayList<>(), new java.util.HashSet<>(), "v1.0", 0L,
                EnumSet.noneOf(com.gh4a.fragment.StoreClassifier.Platform.class),
                3, 4.5, 100.0, 0.5, new ArrayList<>(), "tools");
    }

    private static android.widget.TextView findTextViewByText(android.view.ViewGroup root, String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child instanceof android.widget.TextView
                    && text.equals(((android.widget.TextView) child).getText().toString())) {
                return (android.widget.TextView) child;
            }
            if (child instanceof android.view.ViewGroup) {
                android.widget.TextView found =
                        findTextViewByText((android.view.ViewGroup) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void previewButtonClick_invalidInput_showsInvalidRepoToast() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            urlInput(activity).setText("not a repo");
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.Button preview = findButtonByText(root,
                    activity.getString(R.string.store_fetch_preview));
            assertNotNull(preview);
            preview.performClick();
            assertEquals(activity.getString(R.string.store_invalid_repo),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void fetchPreview_errorViewClick_retriesAndShowsLoading() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            urlInput(activity).setText("octocat/hello");
            callFetchPreview(activity);
            boolean found = false;
            for (int i = 0; i < 30 && !found; i++) {
                ShadowLooper.idleMainLooper();
                Thread.sleep(500);
                found = collectText(activity)
                        .contains(activity.getString(R.string.store_fetch_failed));
            }
            assertTrue(found);
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.TextView errorView = findTextViewByText(root,
                    activity.getString(R.string.store_fetch_failed));
            assertNotNull(errorView);
            errorView.performClick();
            assertTrue(collectText(activity)
                    .contains(activity.getString(R.string.loading_msg)));
        }
    }

    @Test
    public void renderPreview_languageNull_showsUnknown() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            callRenderPreview(activity, repoWithLanguage("octocat/unique-preview-lang",
                    "unique-preview-lang", null));
            assertTrue(collectText(activity)
                    .contains(activity.getString(R.string.unknown)));
        }
    }

    @Test
    public void renderPreview_withPlatforms_showsAssetsText() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            callRenderPreview(activity, repoWithPlatforms("octocat/unique-preview-assets",
                    "unique-preview-assets"));
            assertTrue(collectText(activity).contains(
                    activity.getString(R.string.store_preview_assets, 3)));
        }
    }

    @Test
    public void renderPreview_alreadyFollowed_showsFollowedDisabled() throws Exception {
        try (ActivityController<StoreSubmitActivity> c = launch()) {
            StoreSubmitActivity activity = c.setup().get();
            StorePrefs.addFollowed(activity, "octocat/unique-preview-c");
            callRenderPreview(activity, repo("octocat/unique-preview-c", "unique-preview-c"));
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.Button follow = findButtonByText(root,
                    activity.getString(R.string.store_followed));
            assertNotNull(follow);
            assertFalse(follow.isEnabled());
        } finally {
            StorePrefs.removeFollowed(context(), "octocat/unique-preview-c");
        }
    }

    @Test
    public void followedRow_removeClick_unfollows() {
        for (String name : StorePrefs.getFollowed(context())) {
            StorePrefs.removeFollowed(context(), name);
        }
        StorePrefs.addFollowed(context(), "octocat/hello");
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.TextView remove = findTextViewByText(root,
                    activity.getString(R.string.store_unfollow));
            assertNotNull(remove);
            remove.performClick();
            assertFalse(StorePrefs.isFollowed(activity, "octocat/hello"));
            assertEquals(activity.getString(R.string.store_removed_followed),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void followedRow_rowClick_startsRepositoryActivity() {
        for (String name : StorePrefs.getFollowed(context())) {
            StorePrefs.removeFollowed(context(), name);
        }
        StorePrefs.addFollowed(context(), "octocat/hello");
        try (ActivityController<StoreSubmitActivity> c =
                Robolectric.buildActivity(StoreSubmitActivity.class,
                        StoreSubmitActivity.makeIntent(context()))) {
            StoreSubmitActivity activity = c.setup().get();
            android.view.ViewGroup root =
                    (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            android.widget.TextView name = findTextViewByText(root, "octocat/hello");
            assertNotNull(name);
            android.view.View row = (android.view.View) name.getParent();
            assertNotNull(row);
            row.performClick();
            assertEquals(RepositoryActivity.class.getName(),
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity()
                            .getComponent().getClassName());
        } finally {
            StorePrefs.removeFollowed(context(), "octocat/hello");
        }
    }

}
