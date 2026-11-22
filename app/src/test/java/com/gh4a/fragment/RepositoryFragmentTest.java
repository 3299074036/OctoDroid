package com.gh4a.fragment;

import android.os.Bundle;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.gh4a.utils.HttpImageGetter;
import com.gh4a.utils.translate.TranslationManager;
import com.gh4a.utils.translate.Translator;
import com.gh4a.widget.IntentSpan;
import com.meisolsson.githubsdk.model.Permissions;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchPage;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.repositories.RepositoryContentService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RepositoryFragment：仓库详情页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class RepositoryFragmentTest {
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

    private static Repository repo() {
        return Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                // T-021 (fixed)：hasWiki/hasDiscussions 留 null，
                // fillData 用 Boolean.TRUE.equals 判空，wiki/discussions 行隐藏。
                .build();
    }

    private static Object field(RepositoryFragment f, String name) throws Exception {
        Field field = RepositoryFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(RepositoryFragment f, String name, Object value)
            throws Exception {
        Field field = RepositoryFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static void callVoid(RepositoryFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = RepositoryFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(f, args);
    }

    private static Object call(RepositoryFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = RepositoryFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static Repository richRepo() {
        Permissions permissions = mock(Permissions.class);
        when(permissions.push()).thenReturn(true);
        Repository parent = Repository.builder()
                .fullName("upstream/cat")
                .owner(User.builder().login("upstream").build())
                .name("cat").build();
        return Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .description("A great repo")
                .homepage("https://example.com")
                .topics(Arrays.asList("android", "github"))
                .isFork(true).parent(parent)
                .isPrivate(true)
                .language("Java")
                .hasIssues(true)
                .forksCount(7)
                .stargazersCount(42)
                .subscribersCount(3)
                .permissions(permissions)
                .hasWiki(true)
                .hasDiscussions(true)
                .build();
    }

    private RepositoryFragment attachWithContent(Repository repository) throws Exception {
        RepositoryFragment f = attach(RepositoryFragment.newInstance(repository, "main"));
        // repository.xml 里的 CardView 用 style="?attr/cardViewTheme" 供
        // layout_width/height，该 attr 只在 AppTheme 下解析；先切主题再 inflate。
        mActivity.setTheme(R.style.AppTheme);
        View content = LayoutInflater.from(mActivity)
                .inflate(R.layout.repository, null);
        setField(f, "mContentView", content);
        setField(f, "mReadmeView", content.findViewById(R.id.readme));
        setField(f, "mLoadingView", content.findViewById(R.id.pb_readme));
        setField(f, "mReadmeTitleView", content.findViewById(R.id.readme_title));
        return f;
    }

    private static String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }

    // OverviewRow 的文本在内部 mLabel（R.id.label），不是 TextView 本身
    private static String rowText(View root, int id) {
        View row = root.findViewById(id);
        return ((TextView) row.findViewById(R.id.label)).getText().toString();
    }

    @Test
    public void newInstance_storesRepositoryAndRef() {
        Repository repository = repo();
        RepositoryFragment f = RepositoryFragment.newInstance(repository, "main");
        assertSame(repository, f.getArguments().getParcelable("repo"));
        assertEquals("main", f.getArguments().getString("ref"));
    }

    @Test
    public void newInstance_nullRefIsKeptNull() {
        RepositoryFragment f = RepositoryFragment.newInstance(repo(), null);
        assertEquals(null, f.getArguments().getString("ref"));
    }

    @Test
    public void onCreate_readsRepositoryAndRef() throws Exception {
        Repository repository = repo();
        RepositoryFragment f = attach(RepositoryFragment.newInstance(repository, "dev"));
        assertEquals("octo/cat", ((Repository) field(f, "mRepository")).fullName());
        assertEquals("dev", field(f, "mRef"));
        assertNotNull(field(f, "mMoveViewModel"));
    }

    @Test
    public void setRef_updatesFieldAndArgumentsWithoutReload() throws Exception {
        RepositoryFragment f = attach(RepositoryFragment.newInstance(repo(), "main"));
        assertFalse((Boolean) field(f, "mIsReadmeLoaded"));
        f.setRef("feature-x");
        assertEquals("feature-x", field(f, "mRef"));
        assertEquals("feature-x", f.getArguments().getString("ref"));
    }

    @Test
    public void onSaveInstanceState_storesReadmeFlags() {
        RepositoryFragment f = attach(RepositoryFragment.newInstance(repo(), "main"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("is_readme_expanded"));
        assertTrue(out.containsKey("is_readme_loaded"));
        assertFalse(out.getBoolean("is_readme_expanded"));
        assertFalse(out.getBoolean("is_readme_loaded"));
    }

    // ---- fillData（内容视图直接 inflate，不走 onViewCreated 的网络加载） ----

    @Test
    public void fillData_richRepoFillsViews() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals("octo/cat", text(content, R.id.tv_repo_name));
        assertEquals("A great repo", text(content, R.id.tv_desc));
        assertEquals("https://example.com", text(content, R.id.tv_url));
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.tv_topics).getVisibility());
        assertTrue(text(content, R.id.tv_topics).contains("android"));
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.fork_parent_row).getVisibility());
        assertTrue(rowText(content, R.id.fork_parent_row).contains("upstream/cat"));
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.private_row).getVisibility());
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.language_row).getVisibility());
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.issues_row).getVisibility());
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.tv_collaborators_label).getVisibility());
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.tv_wiki_label).getVisibility());
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.tv_discussions_label).getVisibility());
    }

    @Test
    public void fillData_minimalRepoHidesOptionalRows() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_desc).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_url).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_topics).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.fork_parent_row).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.private_row).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.language_row).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.issues_row).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_collaborators_label).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_wiki_label).getVisibility());
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_discussions_label).getVisibility());
    }

    @Test
    public void fillData_forkWithoutParentHidesRow() throws Exception {
        Repository forkNoParent = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat").isFork(true).parent(null)
                .hasWiki(false).hasDiscussions(false).build();
        RepositoryFragment f = attachWithContent(forkNoParent);
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals(View.GONE,
                content.findViewById(R.id.fork_parent_row).getVisibility());
    }

    @Test
    public void updateStargazerAndWatcherUi_reflectToggleState() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsStarring", true);
        setField(f, "mIsWatching", false);
        callVoid(f, "updateStargazerUi", new Class<?>[]{});
        callVoid(f, "updateWatcherUi", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertTrue(rowText(content, R.id.stars_row).contains("42"));
        assertTrue(rowText(content, R.id.watchers_row).contains("3"));
    }

    @Test
    public void toggleReadmeExpanded_flipsAndUpdatesVisibility() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        // 先置已加载，避免展开分支调 loadReadme 发真实请求
        setField(f, "mIsReadmeLoaded", true);
        assertFalse((Boolean) field(f, "mIsReadmeExpanded"));
        callVoid(f, "toggleReadmeExpanded", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mIsReadmeExpanded"));
        View content = (View) field(f, "mContentView");
        assertEquals(View.VISIBLE, content.findViewById(R.id.readme).getVisibility());
        callVoid(f, "toggleReadmeExpanded", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mIsReadmeExpanded"));
        assertEquals(View.GONE, content.findViewById(R.id.readme).getVisibility());
    }

    @Test
    public void updateReadmeVisibility_states() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        View content = (View) field(f, "mContentView");
        setField(f, "mIsReadmeExpanded", false);
        setField(f, "mIsReadmeLoaded", false);
        callVoid(f, "updateReadmeVisibility", new Class<?>[]{});
        assertEquals(View.GONE, content.findViewById(R.id.readme).getVisibility());
        assertEquals(View.GONE, content.findViewById(R.id.pb_readme).getVisibility());
        setField(f, "mIsReadmeExpanded", true);
        callVoid(f, "updateReadmeVisibility", new Class<?>[]{});
        assertEquals(View.GONE, content.findViewById(R.id.readme).getVisibility());
        assertEquals(View.VISIBLE, content.findViewById(R.id.pb_readme).getVisibility());
        setField(f, "mIsReadmeLoaded", true);
        callVoid(f, "updateReadmeVisibility", new Class<?>[]{});
        assertEquals(View.VISIBLE, content.findViewById(R.id.readme).getVisibility());
        assertEquals(View.GONE, content.findViewById(R.id.pb_readme).getVisibility());
    }

    // ---- 翻译按钮 / 其它信息行 / 移组回调（无网络） ----

    private static void awaitNotInFlight(RepositoryFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while ((Boolean) field(f, "mStarWatchInFlight")) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    private void setTranslateButton(RepositoryFragment f, android.widget.Button button)
            throws Exception {
        setField(f, "mTranslateButton", button);
    }

    @Test
    public void updateTranslateButton_nullButtonIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "updateTranslateButton", new Class<?>[]{});
    }

    @Test
    public void updateTranslateButton_noOriginalHidesButton() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        android.widget.Button button = new android.widget.Button(mActivity);
        setTranslateButton(f, button);
        callVoid(f, "updateTranslateButton", new Class<?>[]{});
        assertEquals(View.GONE, button.getVisibility());
    }

    @Test
    public void updateTranslateButton_withOriginalShowsTranslate() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        android.widget.Button button = new android.widget.Button(mActivity);
        setTranslateButton(f, button);
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "updateTranslateButton", new Class<?>[]{});
        assertEquals(View.VISIBLE, button.getVisibility());
        assertEquals(mActivity.getString(R.string.translate),
                button.getText().toString());
    }

    @Test
    public void updateTranslateButton_translatingShowsTranslating() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        android.widget.Button button = new android.widget.Button(mActivity);
        setTranslateButton(f, button);
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        setField(f, "mIsTranslating", true);
        callVoid(f, "updateTranslateButton", new Class<?>[]{});
        assertEquals(mActivity.getString(R.string.translating),
                button.getText().toString());
    }

    @Test
    public void updateTranslateButton_showingTranslatedShowsOriginal() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        android.widget.Button button = new android.widget.Button(mActivity);
        setTranslateButton(f, button);
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        setField(f, "mShowingTranslated", true);
        callVoid(f, "updateTranslateButton", new Class<?>[]{});
        assertEquals(mActivity.getString(R.string.show_original),
                button.getText().toString());
    }

    @Test
    public void onTranslateButtonClicked_cancelsOngoingTranslation() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        com.gh4a.utils.translate.ReadmeTranslator translator =
                mock(com.gh4a.utils.translate.ReadmeTranslator.class);
        setField(f, "mReadmeTranslator", translator);
        setField(f, "mIsTranslating", true);
        callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
        org.mockito.Mockito.verify(translator).cancel();
        assertFalse((Boolean) field(f, "mIsTranslating"));
        assertEquals(null, field(f, "mReadmeTranslator"));
    }

    @Test
    public void startWithDefaultProvider_noOriginalIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "startWithDefaultProvider", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mIsTranslating"));
    }

    @Test
    public void startWithDefaultProvider_translatingIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        setField(f, "mIsTranslating", true);
        callVoid(f, "startWithDefaultProvider", new Class<?>[]{});
    }

    @Test
    public void pickProviderAndRetranslate_noOriginalIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "pickProviderAndRetranslate", new Class<?>[]{});
    }

    @Test
    public void startReadmeTranslation_noOriginalIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class}, "google");
    }

    @Test
    public void startReadmeTranslation_unknownProviderFallsBackToGoogle() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        // createTranslator 的 default 分支对未知 provider 回落 Google，不抛错
        callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                "nonexistent-provider");
        assertTrue((Boolean) field(f, "mIsTranslating"));
        assertNotNull(field(f, "mReadmeTranslator"));
        // 停掉后台翻译，避免测试结束后继续发网络请求
        ((com.gh4a.utils.translate.ReadmeTranslator) field(f, "mReadmeTranslator"))
                .cancel();
    }

    @Test
    public void onOtherInfoLabelClick_contributorsStartsActivity() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_contributors_label);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("ContributorListActivity"));
    }

    @Test
    public void onOtherInfoLabelClick_wikiStartsActivity() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_wiki_label);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("WikiListActivity"));
    }

    @Test
    public void onOtherInfoLabelClick_releasesStartsActivity() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_releases_label);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("ReleaseListActivity"));
    }

    @Test
    public void onIconClick_nullStatesAreNoop() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        com.gh4a.widget.OverviewRow starsRow =
                (com.gh4a.widget.OverviewRow) field(f, "mStarsRow");
        com.gh4a.widget.OverviewRow watcherRow =
                (com.gh4a.widget.OverviewRow) field(f, "mWatcherRow");
        callVoid(f, "onIconClick",
                new Class<?>[]{com.gh4a.widget.OverviewRow.class}, starsRow);
        callVoid(f, "onIconClick",
                new Class<?>[]{com.gh4a.widget.OverviewRow.class}, watcherRow);
        assertFalse((Boolean) field(f, "mStarWatchInFlight"));
    }

    @Test
    public void showRestrictedListDialog_showsDialog() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showRestrictedListDialog",
                new Class<?>[]{int.class, int.class, android.content.Intent.class},
                R.string.app_name, R.string.app_name,
                new android.content.Intent(android.content.Intent.ACTION_VIEW));
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onSingleOpChanged_nullOpIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, (Object) null);
    }

    @Test
    public void onSingleOpChanged_consumedOpIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(true);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markSingleOpConsumed(org.mockito.Mockito.any());
    }

    @Test
    public void onStarListsLoaded_nullResultIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class},
                (Object) null);
    }

    @Test
    public void onStarListsLoaded_consumedResultIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(
                        1L, new java.util.ArrayList<>(), false);
        when(vm.isStarListsConsumed(result)).thenReturn(true);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markStarListsConsumed(org.mockito.Mockito.any());
    }

    @Test
    public void onStarListsLoaded_failedResultMarksConsumed() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, null, true);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
    }

    @Test
    public void showStarToGroupDialog_nullViewModelIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mMoveViewModel", null);
        callVoid(f, "showStarToGroupDialog", new Class<?>[]{});
    }

    // ---- star/watch 切换（Starring/WatchingService 挡住，不发网络） ----

    private static org.mockito.MockedStatic<com.gh4a.ServiceFactory> mockServices(
            com.meisolsson.githubsdk.service.activity.StarringService starring,
            com.meisolsson.githubsdk.service.activity.WatchingService watching,
            com.meisolsson.githubsdk.service.search.SearchService search,
            RepositoryContentService content) {
        org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS);
        if (starring != null) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.activity.StarringService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(starring);
        }
        if (watching != null) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.activity.WatchingService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(watching);
        }
        if (search != null) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.search.SearchService.class),
                            org.mockito.ArgumentMatchers.anyBoolean(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(search);
        }
        if (content != null) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    RepositoryContentService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(content);
        }
        return mocked;
    }

    // SearchService：totalCount 固定 5（openIssuesCount 10 的仓 → issues 5、pulls 5）
    private static com.meisolsson.githubsdk.service.search.SearchService searchService() {
        com.meisolsson.githubsdk.service.search.SearchService service =
                mock(com.meisolsson.githubsdk.service.search.SearchService.class);
        SearchPage page = mock(SearchPage.class);
        when(page.totalCount()).thenReturn(5L);
        when(service.searchIssues(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L)))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(page)));
        return service;
    }

    private static RepositoryContentService readmeService(String html) {
        RepositoryContentService service = mock(RepositoryContentService.class);
        when(service.getReadmeHtml(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(html)));
        return service;
    }

    private static RepositoryContentService readmeNotFoundService() {
        RepositoryContentService service = mock(RepositoryContentService.class);
        when(service.getReadmeHtml(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.error(404,
                                ResponseBody.create(null, new byte[0]))));
        return service;
    }

    // RESUMED 挂载：onCreateView/onViewCreated 真跑（AppTheme 下 inflate
    // repository.xml）；onViewCreated 里的 loadPullRequestCount 需先挡 search。
    private RepositoryFragment attachResumed(Repository repository) {
        mActivity.setTheme(R.style.AppTheme);
        RepositoryFragment f = RepositoryFragment.newInstance(repository, "main");
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private static Repository repoWithIssues() {
        return Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .hasWiki(false).hasDiscussions(false)
                .hasIssues(true).openIssuesCount(10)
                .build();
    }

    private static void awaitReadmeLoaded(RepositoryFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!(Boolean) field(f, "mIsReadmeLoaded")) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    private static void awaitFieldNonNull(RepositoryFragment f, String name)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (field(f, name) == null) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    private static void awaitRowText(RepositoryFragment f, int rowId, String expected)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        View content = (View) field(f, "mContentView");
        while (!expected.equals(rowText(content, rowId))) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    @Test
    public void toggleStarringState_nullStateIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        callVoid(f, "toggleStarringState", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mStarWatchInFlight"));
    }

    @Test
    public void toggleStarringState_unstarSucceeds() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        when(service.unstarRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", true);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertFalse((Boolean) field(f, "mIsStarring"));
            // 取消加星：星数 42 → 41
            assertEquals(41, (int) ((Repository) field(f, "mRepository"))
                    .stargazersCount());
        }
    }

    @Test
    public void toggleStarringState_starErrorKeepsState() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        when(service.starRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("no net")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", false);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertFalse((Boolean) field(f, "mIsStarring"));
        }
    }

    @Test
    public void toggleWatchingState_nullStateIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        callVoid(f, "toggleWatchingState", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mStarWatchInFlight"));
    }

    @Test
    public void toggleWatchingState_subscribeSucceeds() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        com.meisolsson.githubsdk.model.Subscription subscription =
                mock(com.meisolsson.githubsdk.model.Subscription.class);
        when(subscription.subscribed()).thenReturn(true);
        when(service.setRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(subscription)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", false);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertTrue((Boolean) field(f, "mIsWatching"));
            // 订阅成功：关注数 3 → 4
            assertEquals(4, (int) ((Repository) field(f, "mRepository"))
                    .subscribersCount());
        }
    }

    @Test
    public void loadStarringState_unauthorizedIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "loadStarringState", new Class<?>[]{boolean.class}, true);
        assertEquals(null, field(f, "mIsStarring"));
    }

    @Test
    public void loadWatchingState_unauthorizedIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "loadWatchingState", new Class<?>[]{boolean.class}, true);
        assertEquals(null, field(f, "mIsWatching"));
    }

    // ---- onRefresh / loadReadme / loadPullRequestCount（RxLoader 链） ----

    @Test
    public void onRefresh_reloadsCountsAndResetsStarWatch() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            // RESUMED 挂载：loader 只在 STARTED 后才投递，CREATED 态不回填
            RepositoryFragment f = attachResumed(repoWithIssues());
            // 真 HttpImageGetter.bind 在 Robolectric 下会抛，换 mock；
            // Fragment 侧的 encode/bind 调用行照常覆盖
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsReadmeLoaded", true);
            // 展开态：onRefresh 里 loading 行（181 行）才走
            setField(f, "mIsReadmeExpanded", true);
            setField(f, "mIsStarring", true);
            setField(f, "mIsWatching", false);
            callVoid(f, "onRefresh", new Class<?>[]{});
            // star/watch 先被清 null（未授权时 load* 直接返回，不再回填）
            assertEquals(null, field(f, "mIsStarring"));
            assertEquals(null, field(f, "mIsWatching"));
            awaitRowText(f, R.id.issues_row, "5 issues");
            View content = (View) field(f, "mContentView");
            assertEquals("5 issues", rowText(content, R.id.issues_row));
            assertEquals("5 pull requests", rowText(content, R.id.pulls_row));
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
            org.mockito.Mockito.verify((HttpImageGetter) field(f, "mImageGetter"))
                    .bind(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.eq("<p>readme</p>"),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void onRefresh_loadReadmeSuccessBindsReadme() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsReadmeLoaded", true);
            callVoid(f, "onRefresh", new Class<?>[]{});
            awaitReadmeLoaded(f);
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
            assertEquals("<p>readme</p>", field(f, "mOriginalReadmeHtml"));
            assertFalse((Boolean) field(f, "mShowingTranslated"));
            View readme = (View) field(f, "mReadmeView");
            // 未展开时 readme 仍隐藏（updateReadmeVisibility：展开+已加载才可见）
            assertEquals(View.GONE, readme.getVisibility());
            org.mockito.Mockito.verify((HttpImageGetter) field(f, "mImageGetter"))
                    .bind(org.mockito.ArgumentMatchers.eq((TextView) readme),
                            org.mockito.ArgumentMatchers.eq("<p>readme</p>"),
                            org.mockito.ArgumentMatchers.any());
            android.widget.Button button =
                    (android.widget.Button) field(f, "mTranslateButton");
            assertEquals(View.VISIBLE, button.getVisibility());
            assertEquals(mActivity.getString(R.string.translate),
                    button.getText().toString());
        }
    }

    @Test
    public void onRefresh_loadReadmeNotFoundShowsNoReadme() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeNotFoundService())) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mIsReadmeLoaded", true);
            callVoid(f, "onRefresh", new Class<?>[]{});
            awaitReadmeLoaded(f);
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
            assertEquals(null, field(f, "mOriginalReadmeHtml"));
            assertEquals(mActivity.getString(R.string.repo_no_readme),
                    ((TextView) field(f, "mReadmeView")).getText().toString());
        }
    }

    @Test
    public void onViewCreated_loadsPullRequestCount() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), null)) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            awaitRowText(f, R.id.pulls_row, "5 pull requests");
            View content = (View) field(f, "mContentView");
            assertEquals("5 issues", rowText(content, R.id.issues_row));
            assertEquals("5 pull requests", rowText(content, R.id.pulls_row));
        }
    }

    // ---- loadStarringState / loadWatchingState（已授权分支） ----

    @Test
    public void loadStarringState_authorizedSetsStarred() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        when(service.checkIfRepositoryIsStarred(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        com.gh4a.Gh4Application mockApp = mock(com.gh4a.Gh4Application.class);
        when(mockApp.isAuthorized()).thenReturn(true);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, searchService(), null)) {
            RepositoryFragment f =
                    attachResumed(richRepo().toBuilder().openIssuesCount(10).build());
            try (org.mockito.MockedStatic<com.gh4a.Gh4Application> mockedApp =
                         org.mockito.Mockito.mockStatic(com.gh4a.Gh4Application.class,
                                 org.mockito.Mockito.CALLS_REAL_METHODS)) {
                mockedApp.when(com.gh4a.Gh4Application::get).thenReturn(mockApp);
                callVoid(f, "loadStarringState", new Class<?>[]{boolean.class}, true);
                awaitFieldNonNull(f, "mIsStarring");
                assertTrue((Boolean) field(f, "mIsStarring"));
                View content = (View) field(f, "mContentView");
                assertTrue(rowText(content, R.id.stars_row).contains("42"));
            }
        }
    }

    @Test
    public void loadWatchingState_authorizedSetsNotWatching() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        com.meisolsson.githubsdk.model.Subscription subscription =
                mock(com.meisolsson.githubsdk.model.Subscription.class);
        when(subscription.subscribed()).thenReturn(false);
        when(service.getRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(subscription)));
        com.gh4a.Gh4Application mockApp = mock(com.gh4a.Gh4Application.class);
        when(mockApp.isAuthorized()).thenReturn(true);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, searchService(), null)) {
            RepositoryFragment f =
                    attachResumed(richRepo().toBuilder().openIssuesCount(10).build());
            try (org.mockito.MockedStatic<com.gh4a.Gh4Application> mockedApp =
                         org.mockito.Mockito.mockStatic(com.gh4a.Gh4Application.class,
                                 org.mockito.Mockito.CALLS_REAL_METHODS)) {
                mockedApp.when(com.gh4a.Gh4Application::get).thenReturn(mockApp);
                callVoid(f, "loadWatchingState", new Class<?>[]{boolean.class}, true);
                awaitFieldNonNull(f, "mIsWatching");
                assertFalse((Boolean) field(f, "mIsWatching"));
                View content = (View) field(f, "mContentView");
                assertTrue(rowText(content, R.id.watchers_row).contains("3"));
            }
        }
    }

    // ---- 翻译按钮：已译缓存 / 切回原文 ----

    @Test
    public void onTranslateButtonClicked_showsCachedTranslation() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        HttpImageGetter getter = mock(HttpImageGetter.class);
        setField(f, "mImageGetter", getter);
        setField(f, "mOriginalReadmeHtml", "<p>orig</p>");
        setField(f, "mTranslatedReadmeHtml", "<p>trans</p>");
        callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mShowingTranslated"));
        org.mockito.Mockito.verify(getter).rebind(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq((TextView) field(f, "mReadmeView")),
                org.mockito.ArgumentMatchers.eq("<p>trans</p>"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void onTranslateButtonClicked_switchBackToOriginal() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        HttpImageGetter getter = mock(HttpImageGetter.class);
        setField(f, "mImageGetter", getter);
        setField(f, "mOriginalReadmeHtml", "<p>orig</p>");
        setField(f, "mTranslatedReadmeHtml", "<p>trans</p>");
        setField(f, "mShowingTranslated", true);
        callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mShowingTranslated"));
        org.mockito.Mockito.verify(getter).rebind(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq((TextView) field(f, "mReadmeView")),
                org.mockito.ArgumentMatchers.eq("<p>orig</p>"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void startWithDefaultProvider_googleConfiguredStartsTranslation()
            throws Exception {
        // Google 无需凭据、恒为已配置：点翻译直接以默认 provider 开译
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "startWithDefaultProvider", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mIsTranslating"));
        assertNotNull(field(f, "mReadmeTranslator"));
        // 停掉后台翻译，避免测试结束后继续发网络请求
        ((com.gh4a.utils.translate.ReadmeTranslator) field(f, "mReadmeTranslator"))
                .cancel();
    }

    @Test
    public void pickProviderAndRetranslate_singleProviderTranslatesDirectly()
            throws Exception {
        // 只有一个可用 provider 时不弹选择框，直接回调开译
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "pickProviderAndRetranslate", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mIsTranslating"));
        assertNotNull(field(f, "mReadmeTranslator"));
        ((com.gh4a.utils.translate.ReadmeTranslator) field(f, "mReadmeTranslator"))
                .cancel();
    }

    // ---- 图标点击：非空状态走切换 ----

    @Test
    public void onIconClick_starRowTogglesStarring() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        when(service.unstarRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", true);
            com.gh4a.widget.OverviewRow starsRow =
                    (com.gh4a.widget.OverviewRow) field(f, "mStarsRow");
            callVoid(f, "onIconClick",
                    new Class<?>[]{com.gh4a.widget.OverviewRow.class}, starsRow);
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mIsStarring"));
        }
    }

    @Test
    public void onIconClick_watcherRowTogglesWatching() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        when(service.deleteRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            com.gh4a.widget.OverviewRow watcherRow =
                    (com.gh4a.widget.OverviewRow) field(f, "mWatcherRow");
            callVoid(f, "onIconClick",
                    new Class<?>[]{com.gh4a.widget.OverviewRow.class}, watcherRow);
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mIsWatching"));
        }
    }

    // ---- 移组回调 / 入组委托 ----

    @Test
    public void onStarListsLoaded_emptyListsShowsCreateDialog() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(
                        1L, new ArrayList<>(), false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onStarListsLoaded_nonEmptyShowsAssignDialog() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onSingleOpChanged_successMarksConsumed() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm).markSingleOpConsumed(op);
    }

    @Test
    public void onSingleOpChanged_failedMarksConsumed() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, false, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm).markSingleOpConsumed(op);
    }

    @Test
    public void assignStarredRepoToLists_delegatesToViewModel() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        List<String> ids = Arrays.asList("l1", "l2");
        callVoid(f, "assignStarredRepoToLists",
                new Class<?>[]{String.class, List.class}, "octo/cat", ids);
        org.mockito.Mockito.verify(vm).runSingleOp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(R.string.assign_official_success),
                org.mockito.ArgumentMatchers.eq(R.string.assign_official_failed),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    public void showStarToGroupDialog_withViewModelFetchesLists() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showStarToGroupDialog", new Class<?>[]{});
        org.mockito.Mockito.verify(vm).fetchStarLists(
                org.mockito.ArgumentMatchers.any());
    }

    // ---- 第二批：剩余缺口（点击回调 / 翻译回调 / 对话框回调 / detach 守卫） ----

    private static AlertDialog latestDialog() {
        java.util.List<android.app.Dialog> shown =
                org.robolectric.shadows.ShadowDialog.getShownDialogs();
        return (AlertDialog) shown.get(shown.size() - 1);
    }

    private static void idleMainLooper() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void awaitTranslatingDone(RepositoryFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while ((Boolean) field(f, "mIsTranslating")) {
            idleMainLooper();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    private static void awaitShowingTranslated(RepositoryFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!(Boolean) field(f, "mShowingTranslated")) {
            idleMainLooper();
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            Thread.sleep(20);
        }
    }

    private static org.mockito.MockedStatic<TranslationManager> mockTranslator(
            Translator translator) {
        org.mockito.MockedStatic<TranslationManager> mocked =
                org.mockito.Mockito.mockStatic(TranslationManager.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> TranslationManager.createTranslator(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(translator);
        return mocked;
    }

    private static Translator translatorReturning(String text) throws Exception {
        Translator translator = mock(Translator.class);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(text);
        return translator;
    }

    private static Translator translatorFailing() throws Exception {
        Translator translator = mock(Translator.class);
        // 先注册通用失败，再注册探活（"Hello"）成功——后注册的优先匹配
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("boom"));
        when(translator.translate(
                org.mockito.ArgumentMatchers.eq("Hello"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("Hello");
        return translator;
    }

    private static io.reactivex.Single<retrofit2.Response<Void>> latchSingle(
            CountDownLatch release, boolean error) {
        return io.reactivex.Single.create(emitter -> {
            if (!release.await(5, TimeUnit.SECONDS)) {
                emitter.onError(new RuntimeException("timeout"));
                return;
            }
            if (error) {
                emitter.onError(new RuntimeException("boom"));
            } else {
                emitter.onSuccess(retrofit2.Response.success((Void) null));
            }
        });
    }

    @Test
    public void toggleReadmeExpanded_expandLoadsReadme() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsReadmeLoaded", false);
            callVoid(f, "toggleReadmeExpanded", new Class<?>[]{});
            awaitReadmeLoaded(f);
            assertTrue((Boolean) field(f, "mIsReadmeExpanded"));
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
            assertEquals(View.VISIBLE,
                    ((View) field(f, "mReadmeView")).getVisibility());
        }
    }

    @Test
    public void onViewCreated_savedStateRestoresReadmeFlags() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            Bundle saved = new Bundle();
            saved.putBoolean("is_readme_expanded", true);
            saved.putBoolean("is_readme_loaded", true);
            // SavedState(Bundle) 是包内构造器，反射构造后经 setInitialSavedState 喂给
            // FragmentManager，onViewCreated 即收到该 bundle
            java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> ctor =
                    androidx.fragment.app.Fragment.SavedState.class
                            .getDeclaredConstructor(Bundle.class);
            ctor.setAccessible(true);
            androidx.fragment.app.Fragment.SavedState state = ctor.newInstance(saved);
            RepositoryFragment f = RepositoryFragment.newInstance(repoWithIssues(), "main");
            f.setInitialSavedState(state);
            mActivity.setTheme(R.style.AppTheme);
            mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                    .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            awaitReadmeLoaded(f);
            assertTrue((Boolean) field(f, "mIsReadmeExpanded"));
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void setRef_loadedAndExpandedReloadsReadme() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsReadmeLoaded", true);
            setField(f, "mIsReadmeExpanded", true);
            f.setRef("feature-x");
            awaitReadmeLoaded(f);
            assertEquals("feature-x", field(f, "mRef"));
            assertEquals("feature-x", f.getArguments().getString("ref"));
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void loadReadme_noImageGetterSkipsBind() throws Exception {
        RepositoryContentService content = readmeService("<p>readme</p>");
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), content)) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            // 成功分支的守卫（mImageGetter == null）提前返回，不 bind、不置已加载
            setField(f, "mImageGetter", null);
            callVoid(f, "loadReadme", new Class<?>[]{boolean.class}, true);
            Thread.sleep(1000);
            idleMainLooper();
            org.mockito.Mockito.verify(content).getReadmeHtml(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());
            assertFalse((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void translateButton_clickStartsTranslation() throws Exception {
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translatorReturning("TRANSLATED"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            android.widget.Button button =
                    (android.widget.Button) field(f, "mTranslateButton");
            button.performClick();
            awaitShowingTranslated(f);
            assertTrue((Boolean) field(f, "mShowingTranslated"));
            assertTrue(((String) field(f, "mTranslatedReadmeHtml"))
                    .contains("TRANSLATED"));
            assertEquals(mActivity.getString(R.string.show_original),
                    button.getText().toString());
        }
    }

    @Test
    public void translateButton_longClickPicksProvider() throws Exception {
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translatorReturning("TRANSLATED"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            android.widget.Button button =
                    (android.widget.Button) field(f, "mTranslateButton");
            button.performLongClick();
            awaitShowingTranslated(f);
            assertTrue((Boolean) field(f, "mShowingTranslated"));
        }
    }

    @Test
    public void onDestroyView_cancelsTranslator() throws Exception {
        RepositoryFragment f = attachResumed(repoWithIssues());
        com.gh4a.utils.translate.ReadmeTranslator translator =
                mock(com.gh4a.utils.translate.ReadmeTranslator.class);
        setField(f, "mReadmeTranslator", translator);
        mActivity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        org.mockito.Mockito.verify(translator).cancel();
        assertEquals(null, field(f, "mReadmeTranslator"));
    }

    @Test
    public void starsRow_clickShowsRestrictedDialog() throws Exception {
        RepositoryFragment f = attachResumed(richRepo());
        com.gh4a.widget.OverviewRow starsRow =
                (com.gh4a.widget.OverviewRow) field(f, "mStarsRow");
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        starsRow.performClick();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
        // 积极按钮：仍可尝试打开（445 行）；AlertDialog 按钮经 ButtonHandler
        // 发消息到主 looper，须 idle 后回调才跑
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idleMainLooper();
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("StargazerListActivity"));
    }

    @Test
    public void watcherRow_clickShowsRestrictedDialog() throws Exception {
        RepositoryFragment f = attachResumed(richRepo());
        com.gh4a.widget.OverviewRow watcherRow =
                (com.gh4a.widget.OverviewRow) field(f, "mWatcherRow");
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        watcherRow.performClick();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idleMainLooper();
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("WatcherListActivity"));
    }

    @Test
    public void readmeTitle_clickTogglesExpanded() throws Exception {
        RepositoryFragment f = attachResumed(repoWithIssues());
        setField(f, "mIsReadmeLoaded", true);
        View title = (View) field(f, "mReadmeTitleView");
        title.performClick();
        assertTrue((Boolean) field(f, "mIsReadmeExpanded"));
        assertEquals(View.VISIBLE,
                ((View) field(f, "mReadmeView")).getVisibility());
    }

    @Test
    public void fillTopicsView_topicChipClickSearches() throws Exception {
        RepositoryFragment f = attachResumed(richRepo());
        TextView topics = f.getView().findViewById(R.id.tv_topics);
        Spanned text = (Spanned) topics.getText();
        IntentSpan[] spans =
                text.getSpans(0, text.length(), IntentSpan.class);
        assertTrue(spans.length > 0);
        spans[0].onClick(topics);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("SearchActivity"));
    }

    @Test
    public void onOtherInfoLabelClick_discussionsOpensCustomTab() throws Exception {
        // Robolectric 下无浏览器可解析，createBrowserIntent 返回 null、
        // 不发 intent；只断言分支跑通不抛错
        RepositoryFragment f = attachWithContent(
                richRepo().toBuilder().htmlUrl("https://github.com/octo/cat").build());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_discussions_label);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
    }

    @Test
    public void onOtherInfoLabelClick_collaboratorsStartsActivity() throws Exception {
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_collaborators_label);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("CollaboratorListActivity"));
    }

    @Test
    public void startWithDefaultProvider_noProvidersToasts() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     org.mockito.Mockito.mockStatic(TranslationManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> TranslationManager.getConfiguredProviders(
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new ArrayList<>());
            callVoid(f, "startWithDefaultProvider", new Class<?>[]{});
            assertFalse((Boolean) field(f, "mIsTranslating"));
        }
    }

    @Test
    public void startReadmeTranslation_createTranslatorThrowsToasts() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     org.mockito.Mockito.mockStatic(TranslationManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> TranslationManager.createTranslator(
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new RuntimeException("no provider"));
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertFalse((Boolean) field(f, "mIsTranslating"));
            assertEquals(null, field(f, "mReadmeTranslator"));
        }
    }

    @Test
    public void startReadmeTranslation_errorShowsToast() throws Exception {
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translatorFailing())) {
            RepositoryFragment f = attachWithContent(repo());
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            awaitTranslatingDone(f);
            assertFalse((Boolean) field(f, "mIsTranslating"));
            assertEquals(null, field(f, "mTranslatedReadmeHtml"));
            assertFalse((Boolean) field(f, "mShowingTranslated"));
            // onError 体内发了失败 toast（688/690 行的直接证据）
            assertTrue(org.robolectric.shadows.ShadowToast.showedToast(
                    mActivity.getString(R.string.translate_failed) + ": boom"));
        }
    }

    @Test
    public void startReadmeTranslation_completeWhileNotTranslatingReturns()
            throws Exception {
        Translator translator = mock(Translator.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    if (!"Hello".equals(inv.getArgument(0))) {
                        entered.countDown();
                    }
                    return "TRANSLATED";
                });
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translator)) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            // 等后台线程把 onProgress/onComplete 排进主 looper，再翻 mIsTranslating，
            // 使两个回调都在守卫处提前返回（650、663-664 行）
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread.sleep(400);
            setField(f, "mIsTranslating", false);
            idleMainLooper();
            assertFalse((Boolean) field(f, "mShowingTranslated"));
            assertEquals(null, field(f, "mTranslatedReadmeHtml"));
        }
    }

    @Test
    public void startReadmeTranslation_progressWhileNotTranslatingReturns()
            throws Exception {
        Translator translator = mock(Translator.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    if ("Hello".equals(inv.getArgument(0))) {
                        return "Hello";
                    }
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return "TRANSLATED";
                });
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translator)) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            // 点取消：mIsTranslating 置 false；放行后 onProgress 在守卫处返回
            callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
            assertFalse((Boolean) field(f, "mIsTranslating"));
            release.countDown();
            Thread.sleep(400);
            idleMainLooper();
            assertFalse((Boolean) field(f, "mShowingTranslated"));
        }
    }

    @Test
    public void toggleStarringState_starShowsGroupDialog() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        when(service.starRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
            setField(f, "mMoveViewModel", vm);
            setField(f, "mIsStarring", false);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            awaitNotInFlight(f);
            assertTrue((Boolean) field(f, "mIsStarring"));
            assertEquals(43, (int) ((Repository) field(f, "mRepository"))
                    .stargazersCount());
            org.mockito.Mockito.verify(vm).fetchStarLists(
                    org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void toggleWatchingState_errorKeepsState() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        when(service.deleteRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("boom")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertTrue((Boolean) field(f, "mIsWatching"));
        }
    }

    @Test
    public void toggleStarringState_successAfterDetachIsNoop() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.unstarRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, false));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", true);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            // detach 后成功回调在 isAdded 守卫处返回，星态不变
            assertTrue((Boolean) field(f, "mIsStarring"));
        }
    }

    @Test
    public void toggleStarringState_errorAfterDetachIsNoop() throws Exception {
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.starRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, true));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", false);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertFalse((Boolean) field(f, "mIsStarring"));
        }
    }

    @Test
    public void toggleWatchingState_errorAfterDetachIsNoop() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.deleteRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, true));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertTrue((Boolean) field(f, "mIsWatching"));
        }
    }

    @Test
    public void onStarListsLoaded_nullListsShowsCreateDialog() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, null, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onStarListsLoaded_createDialogConfirmCreatesAndAssigns()
            throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(
                        1L, new ArrayList<>(), false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("My List");
        idleMainLooper();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm).createListAndAssign(
                "octo/cat", "My List", false);
    }

    @Test
    public void onStarListsLoaded_assignDialogConfirmAssigns() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        ListView list = dialog.findViewById(R.id.list);
        list.performItemClick(list.getAdapter().getView(0, null, list), 0,
                list.getAdapter().getItemId(0));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idleMainLooper();
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm).runSingleOp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(R.string.assign_official_success),
                org.mockito.ArgumentMatchers.eq(R.string.assign_official_failed),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    public void onStarListsLoaded_assignDialogCreateNewShowsCreateDialog()
            throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idleMainLooper();
        AlertDialog createDialog = latestDialog();
        EditText input = createDialog.findViewById(R.id.et_list_name);
        input.setText("New List");
        idleMainLooper();
        createDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idleMainLooper();
        // 注意：新建分支的 onConfirmed 里只有 createListAndAssign，不再 mark
        //（mark 只在点“新建列表…”时做过一次）；取消分支才再 mark
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm).createListAndAssign(
                "octo/cat", "New List", false);
    }

    @Test
    public void onStarListsLoaded_assignDialogCancelMarksConsumed() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMainLooper();
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never()).runSingleOp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    public void onStarListsLoaded_notAddedIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(
                        1L, new ArrayList<>(), false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markStarListsConsumed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void onSingleOpChanged_notAddedIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markSingleOpConsumed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void assignStarredRepoToLists_nullViewModelIsNoop() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mMoveViewModel", null);
        callVoid(f, "assignStarredRepoToLists",
                new Class<?>[]{String.class, List.class}, "octo/cat",
                Arrays.asList("l1"));
    }

    @Test
    public void showStarToGroupDialog_noActivityIsNoop() throws Exception {
        RepositoryFragment f = new RepositoryFragment();
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showStarToGroupDialog", new Class<?>[]{});
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never()).fetchStarLists(
                org.mockito.ArgumentMatchers.any());
    }

    // ---- 第三批：最后 9 行缺口 ----

    @Test
    public void fillData_repoNameClickOpensUser() throws Exception {
        RepositoryFragment f = attachResumed(richRepo());
        TextView name = f.getView().findViewById(R.id.tv_repo_name);
        Spanned text = (Spanned) name.getText();
        IntentSpan[] spans = text.getSpans(0, text.length(), IntentSpan.class);
        assertTrue(spans.length > 0);
        spans[0].onClick(name);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("UserActivity"));
    }

    @Test
    public void loadReadme_nullRefUsesDefaultBranch() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            mActivity.setTheme(R.style.AppTheme);
            RepositoryFragment f =
                    RepositoryFragment.newInstance(repoWithIssues(), null);
            mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                    .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsReadmeLoaded", true);
            callVoid(f, "onRefresh", new Class<?>[]{});
            awaitReadmeLoaded(f);
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
            assertEquals("<p>readme</p>", field(f, "mOriginalReadmeHtml"));
        }
    }

    @Test
    public void startReadmeTranslation_errorNullMessageShowsToastWithoutSuffix()
            throws Exception {
        // onError 里两处三元分支的另一半：e.getMessage() 为 null 时 msg 为 ""，
        // toast 不带 ": ..." 后缀
        Translator translator = mock(Translator.class);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    if ("Hello".equals(inv.getArgument(0))) {
                        return "Hello";
                    }
                    throw new RuntimeException();
                });
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translator)) {
            RepositoryFragment f = attachWithContent(repo());
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            awaitTranslatingDone(f);
            assertFalse((Boolean) field(f, "mIsTranslating"));
            assertTrue(org.robolectric.shadows.ShadowToast.showedToast(
                    mActivity.getString(R.string.translate_failed)));
        }
    }

    @Test
    public void startReadmeTranslation_errorWhileNotTranslatingReturns()
            throws Exception {
        Translator translator = mock(Translator.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    if ("Hello".equals(inv.getArgument(0))) {
                        return "Hello";
                    }
                    entered.countDown();
                    throw new RuntimeException("boom");
                });
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translator)) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            // 等后台线程把 onError 排进主 looper，再翻 mIsTranslating，
            // 使 onError 在守卫处提前返回（684 行）
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread.sleep(400);
            setField(f, "mIsTranslating", false);
            idleMainLooper();
            assertFalse((Boolean) field(f, "mShowingTranslated"));
            assertEquals(null, field(f, "mTranslatedReadmeHtml"));
        }
    }

    @Test
    public void toggleWatchingState_successAfterDetachIsNoop() throws Exception {
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.deleteRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, false));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            // detach 后成功回调在 isAdded 守卫处返回，关注态不变
            assertTrue((Boolean) field(f, "mIsWatching"));
        }
    }

    @Test
    public void onStarListsLoaded_createDialogCancelMarksConsumed() throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(
                        1L, new ArrayList<>(), false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMainLooper();
        org.mockito.Mockito.verify(vm).markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .createListAndAssign(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    public void onStarListsLoaded_assignCreateDialogCancelMarksConsumed()
            throws Exception {
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        when(vm.isStarListsConsumed(result)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idleMainLooper();
        AlertDialog createDialog = latestDialog();
        createDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMainLooper();
        // 点“新建列表…” mark 一次，创建框点取消再 mark 一次
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.times(2))
                .markStarListsConsumed(result);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .createListAndAssign(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ---- A 组补缺（2026-11-22）：RepositoryFragment 剩余分支 ----

    private static void setAdded(RepositoryFragment f, boolean added) throws Exception {
        // Fragment.isAdded() 读 mHost != null && mAdded；只翻 mAdded 可在
        // 保持 getActivity() 非空的前提下模拟“未挂载”。
        java.lang.reflect.Field fld =
                androidx.fragment.app.Fragment.class.getDeclaredField("mAdded");
        fld.setAccessible(true);
        fld.setBoolean(f, added);
    }

    private static java.lang.reflect.Method readmeSubscribeLambda() throws Exception {
        // loadReadme 的成功回调 lambda：返回值 void、末参 Optional。
        // map 阶段的 lambda 返回 Optional，可据此区分。
        for (java.lang.reflect.Method m :
                RepositoryFragment.class.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda$loadReadme")
                    && m.getReturnType() == void.class
                    && m.getParameterTypes().length >= 1
                    && m.getParameterTypes()[m.getParameterTypes().length - 1]
                            == java.util.Optional.class) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("readme subscribe lambda not found");
    }

    private static java.lang.reflect.Method readmeMapLambda() throws Exception {
        // loadReadme 的 map 阶段 lambda：返回值 Optional、末参 Optional。
        // javap 核对签名：
        // lambda$loadReadme$8(String, String, Context, Long, Optional)。
        // 与返回 void 的订阅 lambda（lambda$loadReadme$10）以返回值区分。
        for (java.lang.reflect.Method m :
                RepositoryFragment.class.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda$loadReadme")
                    && m.getReturnType() == java.util.Optional.class
                    && m.getParameterTypes().length >= 1
                    && m.getParameterTypes()[m.getParameterTypes().length - 1]
                            == java.util.Optional.class) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("readme map lambda not found");
    }

    private static java.lang.reflect.Method pickProviderLambda() throws Exception {
        for (java.lang.reflect.Method m :
                RepositoryFragment.class.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda$pickProviderAndRetranslate")
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == String.class) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("pick-provider lambda not found");
    }

    private static com.meisolsson.githubsdk.service.search.SearchService
            neverSearchService() {
        com.meisolsson.githubsdk.service.search.SearchService service =
                mock(com.meisolsson.githubsdk.service.search.SearchService.class);
        when(service.searchIssues(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(0L)))
                .thenReturn(io.reactivex.Single.never());
        return service;
    }

    @Test
    public void fillData_privateRowShowsDivider() throws Exception {
        // L310：无 fork、仅私有行可见时分隔线仍显示（|| 的第二臂）。
        Repository repo = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .isPrivate(true)
                .build();
        RepositoryFragment f = attachWithContent(repo);
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.repository_overview_row_divider)
                        .getVisibility());
    }

    @Test
    public void fillData_languageRowShowsDivider() throws Exception {
        // L311：无 fork、不私有、仅语言行可见时分隔线仍显示（|| 的第三臂）。
        Repository repo = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .language("Java")
                .build();
        RepositoryFragment f = attachWithContent(repo);
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.repository_overview_row_divider)
                        .getVisibility());
    }

    @Test
    public void fillData_authorizedSkipsStarWatchUiUpdates() throws Exception {
        // L343：已登录时不刷 star/watch 行的文本与开关态。
        com.gh4a.Gh4Application mockApp = mock(com.gh4a.Gh4Application.class);
        when(mockApp.isAuthorized()).thenReturn(true);
        RepositoryFragment f = attachWithContent(richRepo());
        try (org.mockito.MockedStatic<com.gh4a.Gh4Application> mockedApp =
                     org.mockito.Mockito.mockStatic(com.gh4a.Gh4Application.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mockedApp.when(com.gh4a.Gh4Application::get).thenReturn(mockApp);
            callVoid(f, "fillData", new Class<?>[]{});
            View content = (View) field(f, "mContentView");
            assertFalse(rowText(content, R.id.stars_row).contains("42"));
            assertFalse(rowText(content, R.id.watchers_row).contains("3"));
        }
    }

    @Test
    public void fillData_permissionsPushFalseHidesCollaborators() throws Exception {
        // L353：permissions 非空但 push 为假时协作者标签隐藏。
        Permissions permissions = mock(Permissions.class);
        when(permissions.push()).thenReturn(false);
        Repository repo = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .permissions(permissions)
                .build();
        RepositoryFragment f = attachWithContent(repo);
        callVoid(f, "fillData", new Class<?>[]{});
        View content = (View) field(f, "mContentView");
        assertEquals(View.GONE,
                content.findViewById(R.id.tv_collaborators_label).getVisibility());
    }

    @Test
    public void fillTextView_withStringIdFormatsText() throws Exception {
        // L411/L412：stringId 非 0 时按格式串设文本。
        RepositoryFragment f = attachWithContent(repo());
        callVoid(f, "fillTextView",
                new Class<?>[]{int.class, int.class, String.class},
                R.id.tv_desc, R.string.repo_language, "Java");
        View content = (View) field(f, "mContentView");
        assertEquals("Written in Java", text(content, R.id.tv_desc));
        assertEquals(View.VISIBLE,
                content.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void onOtherInfoLabelClick_unknownIdIsNoop() throws Exception {
        // L468（releases 条件为假）+ L472（intent 为 null 时不跳转）。
        RepositoryFragment f = attachWithContent(richRepo());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View label = new android.view.View(mActivity);
        label.setId(R.id.tv_repo_name);
        callVoid(f, "onOtherInfoLabelClick", new Class<?>[]{android.view.View.class},
                label);
        assertEquals(null, org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity());
    }

    @Test
    public void onRefresh_nullViewsSkipResets() throws Exception {
        // L177/L183/L197 + L200（未加载）：视图字段全空时 onRefresh 只跑加载。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, neverSearchService(), null)) {
            RepositoryFragment f = attachWithContent(repo());
            setField(f, "mReadmeView", null);
            setField(f, "mLoadingView", null);
            setField(f, "mContentView", null);
            setField(f, "mImageGetter", null);
            setField(f, "mIsReadmeLoaded", false);
            callVoid(f, "onRefresh", new Class<?>[]{});
            idleMainLooper();
            assertEquals(null, field(f, "mIsStarring"));
            assertEquals(null, field(f, "mIsWatching"));
            assertEquals(null, field(f, "mOriginalReadmeHtml"));
        }
    }

    @Test
    public void onRefresh_nullLoadingViewWithExpandedSkips() throws Exception {
        // L180：mLoadingView 为 null 时即使展开也不碰 loading 行。
        // 注意：不能同时置 mIsReadmeLoaded=true——onRefresh 会调 loadReadme(true)，
        // 其 doOnSubscribe 里的 updateReadmeVisibility() 对 mLoadingView 无守卫
        // （RepositoryFragment.java:499-501，与 L180/L267 的守卫不一致），
        // 空 loading 视图会在订阅阶段 NPE，订阅静默失败、mIsReadmeLoaded 永不
        // 为 true。见报告 A2-1。该测试只覆盖 L180 本身，故令 readme 未加载。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", null);
            setField(f, "mIsReadmeLoaded", false);
            setField(f, "mIsReadmeExpanded", true);
            setField(f, "mLoadingView", null);
            setField(f, "mIsWatching", false);
            setField(f, "mIsStarring", true);
            callVoid(f, "onRefresh", new Class<?>[]{});
            idleMainLooper();
            assertEquals(null, field(f, "mIsWatching"));
            assertEquals(null, field(f, "mIsStarring"));
            assertFalse((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void onRefresh_nullWatcherRowSkipsSetText() throws Exception {
        // L189：mIsWatching 非空但行空时不调 setText，仍清 null。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsWatching", false);
            setField(f, "mWatcherRow", null);
            callVoid(f, "onRefresh", new Class<?>[]{});
            assertEquals(null, field(f, "mIsWatching"));
        }
    }

    @Test
    public void onRefresh_nullStarsRowSkipsSetText() throws Exception {
        // L193：mIsStarring 非空但行空时不调 setText，仍清 null。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mIsStarring", true);
            setField(f, "mStarsRow", null);
            callVoid(f, "onRefresh", new Class<?>[]{});
            assertEquals(null, field(f, "mIsStarring"));
        }
    }

    @Test
    public void onViewCreated_savedStateLoadedOnlyLoadsReadme() throws Exception {
        // L227：仅 mIsReadmeLoaded 为真（未展开）时 onViewCreated 也加载。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            Bundle saved = new Bundle();
            saved.putBoolean("is_readme_loaded", true);
            java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> ctor =
                    androidx.fragment.app.Fragment.SavedState.class
                            .getDeclaredConstructor(Bundle.class);
            ctor.setAccessible(true);
            androidx.fragment.app.Fragment.SavedState state = ctor.newInstance(saved);
            RepositoryFragment f = RepositoryFragment.newInstance(repoWithIssues(), "main");
            f.setInitialSavedState(state);
            mActivity.setTheme(R.style.AppTheme);
            mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                    .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            awaitReadmeLoaded(f);
            assertFalse((Boolean) field(f, "mIsReadmeExpanded"));
            assertTrue((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void loadReadme_mapStageSkipsEncodeWhenNotAdded() throws Exception {
        // L526：map 阶段 isAdded() 为假时不调 encode。
        HttpImageGetter getter = mock(HttpImageGetter.class);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachWithContent(repo());
            setField(f, "mImageGetter", getter);
            setAdded(f, false);
            callVoid(f, "loadReadme", new Class<?>[]{boolean.class}, false);
            Thread.sleep(1000);
            idleMainLooper();
            org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                    .encode(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    public void loadReadme_mapStageSkipsEncodeWhenImageGetterNull() throws Exception {
        // L526：map 阶段 isAdded() 为真但 mImageGetter 为 null 时不调 encode。
        // 与 loadReadme_mapStageSkipsEncodeWhenNotAdded 互补（那个覆 isAdded()
        // 为假臂）。空 getter 时订阅回调同样提前返回，故只断言无崩溃且
        // mOriginalReadmeHtml 未写入。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachWithContent(repo());
            setField(f, "mImageGetter", null);
            callVoid(f, "loadReadme", new Class<?>[]{boolean.class}, false);
            Thread.sleep(1000);
            idleMainLooper();
            assertEquals(null, field(f, "mOriginalReadmeHtml"));
        }
    }

    @Test
    public void loadReadme_subscribeSkipsBindWhenNotAdded() throws Exception {
        // L541：成功回调里 !isAdded() 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        java.lang.reflect.Method lambda = readmeSubscribeLambda();
        lambda.invoke(f, new Object[]{null, java.util.Optional.of("<p>hi</p>")});
        assertEquals(null, field(f, "mOriginalReadmeHtml"));
    }

    @Test
    public void loadReadme_subscribeSkipsBindWhenViewNull() throws Exception {
        // L541：CREATED 态（getView() 为 null）时成功回调直接返回。
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mImageGetter", mock(HttpImageGetter.class));
        java.lang.reflect.Method lambda = readmeSubscribeLambda();
        lambda.invoke(f, new Object[]{1L, java.util.Optional.of("<p>hi</p>")});
        assertEquals(null, field(f, "mOriginalReadmeHtml"));
        assertFalse((Boolean) field(f, "mIsReadmeLoaded"));
    }

    @Test
    public void loadReadme_subscribeSkipsBindWhenReadmeViewNull() throws Exception {
        // L541：mReadmeView 为 null 时成功回调直接返回。
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, null, searchService(), readmeService("<p>readme</p>"))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mReadmeView", null);
            java.lang.reflect.Method lambda = readmeSubscribeLambda();
            lambda.invoke(f, new Object[]{1L, java.util.Optional.of("<p>hi</p>")});
            assertEquals(null, field(f, "mOriginalReadmeHtml"));
            assertFalse((Boolean) field(f, "mIsReadmeLoaded"));
        }
    }

    @Test
    public void onTranslateButtonClicked_translatingWithoutTranslatorResets()
            throws Exception {
        // L566：翻译中但 translator 已空时直接复位，不调 cancel。
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mReadmeTranslator", null);
        setField(f, "mIsTranslating", true);
        callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mIsTranslating"));
        assertTrue(org.robolectric.shadows.ShadowToast.showedToast(
                mActivity.getString(R.string.translate_cancelled)));
    }

    @Test
    public void onTranslateButtonClicked_switchBackWithNullOriginalSkipsRebind()
            throws Exception {
        // L578：切回原文但原文为空时不 rebind。
        RepositoryFragment f = attachWithContent(repo());
        HttpImageGetter getter = mock(HttpImageGetter.class);
        setField(f, "mImageGetter", getter);
        setField(f, "mOriginalReadmeHtml", null);
        setField(f, "mShowingTranslated", true);
        callVoid(f, "onTranslateButtonClicked", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mShowingTranslated"));
        org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                .rebind(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void startWithDefaultProvider_noActivityIsNoop() throws Exception {
        // L601：getActivity() 为 null 时直接返回。
        RepositoryFragment f = RepositoryFragment.newInstance(repo(), "main");
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "startWithDefaultProvider", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mIsTranslating"));
        assertEquals(null, field(f, "mReadmeTranslator"));
    }

    @Test
    public void pickProviderAndRetranslate_noActivityIsNoop() throws Exception {
        // L616：getActivity() 为 null 时直接返回。
        RepositoryFragment f = RepositoryFragment.newInstance(repo(), "main");
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "pickProviderAndRetranslate", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mIsTranslating"));
        assertEquals(null, field(f, "mReadmeTranslator"));
    }

    @Test
    public void pickProviderAndRetranslate_translatingIsNoop() throws Exception {
        // L616：翻译中时直接返回，不弹选择框。
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        setField(f, "mIsTranslating", true);
        callVoid(f, "pickProviderAndRetranslate", new Class<?>[]{});
        assertEquals(null, field(f, "mReadmeTranslator"));
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void startReadmeTranslation_noActivityIsNoop() throws Exception {
        // L630：getActivity() 为 null 时直接返回。
        RepositoryFragment f = RepositoryFragment.newInstance(repo(), "main");
        setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
        callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class}, "google");
        assertFalse((Boolean) field(f, "mIsTranslating"));
        assertEquals(null, field(f, "mReadmeTranslator"));
    }

    @Test
    public void pickProviderCallback_notAddedSkipsToast() throws Exception {
        // L621：provider 回调里 isAdded() 为假时不发切换 toast。
        RepositoryFragment f = attachWithContent(repo());
        setAdded(f, false);
        java.lang.reflect.Method lambda = pickProviderLambda();
        lambda.invoke(f, "google");
        assertEquals(null,
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertFalse((Boolean) field(f, "mIsTranslating"));
    }

    private static Translator blockingTranslator(CountDownLatch entered,
            CountDownLatch release) throws Exception {
        Translator translator = mock(Translator.class);
        when(translator.translate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    if ("Hello".equals(inv.getArgument(0))) {
                        return "Hello";
                    }
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return "TRANSLATED";
                });
        return translator;
    }

    @Test
    public void startReadmeTranslation_progressDetachedReturns() throws Exception {
        // L649：翻译中途宿主被拆时 onProgress 在 getActivity 守卫处返回。
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(blockingTranslator(entered, release))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            Thread.sleep(400);
            idleMainLooper();
            assertFalse((Boolean) field(f, "mShowingTranslated"));
            assertEquals(null, field(f, "mTranslatedReadmeHtml"));
        }
    }

    @Test
    public void startReadmeTranslation_progressWithNullReadmeViewReturns()
            throws Exception {
        // L649（mReadmeView 为 null）+ L673/L674（同）：onProgress 不动按钮，
        // onComplete 不 rebind，但翻译态仍置真。
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(blockingTranslator(entered, release))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            HttpImageGetter getter = mock(HttpImageGetter.class);
            setField(f, "mImageGetter", getter);
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            setField(f, "mReadmeView", null);
            release.countDown();
            Thread.sleep(400);
            idleMainLooper();
            assertTrue((Boolean) field(f, "mShowingTranslated"));
            org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                    .rebind(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void startReadmeTranslation_completeWithNullImageGetterSkipsRebind()
            throws Exception {
        // L673/L674：onComplete 时 mImageGetter 为 null 则不 rebind。
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(blockingTranslator(entered, release))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            HttpImageGetter getter = mock(HttpImageGetter.class);
            setField(f, "mImageGetter", getter);
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            setField(f, "mImageGetter", null);
            release.countDown();
            Thread.sleep(400);
            idleMainLooper();
            assertTrue((Boolean) field(f, "mShowingTranslated"));
            org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                    .rebind(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void startReadmeTranslation_completeWithNullViewSkipsRebind()
            throws Exception {
        // L673/L674：onComplete 时 getView() 为 null 则不 rebind。
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translatorReturning("TRANSLATED"))) {
            RepositoryFragment f = attachWithContent(repo());
            HttpImageGetter getter = mock(HttpImageGetter.class);
            setField(f, "mImageGetter", getter);
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            Thread.sleep(1000);
            idleMainLooper();
            assertTrue((Boolean) field(f, "mShowingTranslated"));
            org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                    .rebind(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void startReadmeTranslation_completeNotAddedSkipsRebind() throws Exception {
        // L673/L674：onComplete 时 isAdded() 为假则不 rebind。
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(blockingTranslator(entered, release))) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            HttpImageGetter getter = mock(HttpImageGetter.class);
            setField(f, "mImageGetter", getter);
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            setAdded(f, false);
            release.countDown();
            Thread.sleep(400);
            idleMainLooper();
            assertTrue((Boolean) field(f, "mShowingTranslated"));
            org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                    .rebind(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void startReadmeTranslation_errorNotAddedSkipsToast() throws Exception {
        // L687：onError 时 isAdded() 为假则不发失败 toast。
        try (org.mockito.MockedStatic<TranslationManager> mocked =
                     mockTranslator(translatorFailing())) {
            RepositoryFragment f = attachResumed(repoWithIssues());
            setField(f, "mImageGetter", mock(HttpImageGetter.class));
            setField(f, "mOriginalReadmeHtml", "<p>hi</p>");
            callVoid(f, "startReadmeTranslation", new Class<?>[]{String.class},
                    "google");
            // 等后台线程把 onError 排进主 looper（未执行），再翻 mAdded
            Thread.sleep(500);
            setAdded(f, false);
            idleMainLooper();
            assertFalse((Boolean) field(f, "mIsTranslating"));
            assertFalse(org.robolectric.shadows.ShadowToast.showedToast(
                    mActivity.getString(R.string.translate_failed) + ": boom"));
        }
    }

    @Test
    public void toggleStarringState_inFlightIsNoop() throws Exception {
        // L744：在途时不发新的 star/unstar 请求。
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsStarring", true);
            setField(f, "mStarWatchInFlight", true);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                    .starRepository(org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                    .unstarRepository(org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    public void toggleStarringState_successWithNullStarringSkipsFlip()
            throws Exception {
        // L766：成功回包时 mIsStarring 已空则不翻态、不弹分组框。
        com.meisolsson.githubsdk.service.activity.StarringService service =
                mock(com.meisolsson.githubsdk.service.activity.StarringService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.unstarRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, false));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(service, null, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
            setField(f, "mMoveViewModel", vm);
            setField(f, "mIsStarring", true);
            callVoid(f, "toggleStarringState", new Class<?>[]{});
            setField(f, "mIsStarring", null);
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertEquals(null, field(f, "mIsStarring"));
            org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                    .fetchStarLists(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void toggleWatchingState_inFlightIsNoop() throws Exception {
        // L956：在途时不发新的订阅/退订请求。
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            setField(f, "mStarWatchInFlight", true);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                    .deleteRepositorySubscription(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                    .setRepositorySubscription(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void toggleWatchingState_successWithNullWatchingSkipsFlip()
            throws Exception {
        // L989：成功回包时 mIsWatching 已空则不翻态、不刷行。
        com.meisolsson.githubsdk.service.activity.WatchingService service =
                mock(com.meisolsson.githubsdk.service.activity.WatchingService.class);
        CountDownLatch release = new CountDownLatch(1);
        when(service.deleteRepositorySubscription(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(latchSingle(release, false));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockServices(null, service, null, null)) {
            RepositoryFragment f = attachWithContent(richRepo());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsWatching", true);
            callVoid(f, "toggleWatchingState", new Class<?>[]{});
            setField(f, "mIsWatching", null);
            release.countDown();
            awaitNotInFlight(f);
            assertFalse((Boolean) field(f, "mStarWatchInFlight"));
            assertEquals(null, field(f, "mIsWatching"));
        }
    }

    @Test
    public void onCreateListNameConfirmed_nullViewModelIsNoop() throws Exception {
        // L838/L839：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        callVoid(f, "onCreateListNameConfirmed",
                new Class<?>[]{String.class, String.class, String.class, boolean.class},
                "repo_create", "octo/cat", "New List", false);
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onCreateListNameConfirmed_afterAssignSkipsMarkConsumed()
            throws Exception {
        // L844：repo_create_after_assign 只调 createListAndAssign，不 mark。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onCreateListNameConfirmed",
                new Class<?>[]{String.class, String.class, String.class, boolean.class},
                "repo_create_after_assign", "octo/cat", "New List", true);
        org.mockito.Mockito.verify(vm).createListAndAssign("octo/cat", "New List", true);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markStarListsConsumed(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void onCreateListNameCancelled_nullViewModelIsNoop() throws Exception {
        // L851：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        callVoid(f, "onCreateListNameCancelled",
                new Class<?>[]{String.class, String.class}, "repo_create", "octo/cat");
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onAssignListsConfirmed_nullViewModelIsNoop() throws Exception {
        // L859/L860：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        callVoid(f, "onAssignListsConfirmed",
                new Class<?>[]{String.class, String.class, java.util.List.class},
                "assign_repo", "octo/cat", new ArrayList<String>());
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onAssignListsCreateNew_nullViewModelIsNoop() throws Exception {
        // L868/L869：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        callVoid(f, "onAssignListsCreateNew",
                new Class<?>[]{String.class, String.class}, "assign_repo", "octo/cat");
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onAssignListsCreateNew_nullPendingShowsCreateDialog()
            throws Exception {
        // L874 + L817：mPendingStarListsResult 为 null 时 listNames(null) 得
        // 空表，仍弹出新建列表框，并把 null 结果标已消费。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onAssignListsCreateNew",
                new Class<?>[]{String.class, String.class}, "assign_repo", "octo/cat");
        idleMainLooper();
        assertNotNull(latestDialog());
        org.mockito.Mockito.verify(vm).markStarListsConsumed(
                (StarBatchMoveViewModel.StarListsResult) null);
    }

    @Test
    public void onAssignListsCancelled_nullViewModelIsNoop() throws Exception {
        // L881：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        callVoid(f, "onAssignListsCancelled",
                new Class<?>[]{String.class, String.class}, "assign_repo", "octo/cat");
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onStarListsLoaded_nullViewModelIsNoop() throws Exception {
        // L897：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, new ArrayList<>(), false);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onStarListsLoaded_assignDialogAlreadyOpenSkipsShow()
            throws Exception {
        // L912：assign_repo 框已开时不再重弹。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(new androidx.fragment.app.Fragment(), "assign_repo").commitNow();
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        idleMainLooper();
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onStarListsLoaded_createDialogAlreadyOpenSkipsShow()
            throws Exception {
        // L913：repo_create 框已开时不再重弹。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(new androidx.fragment.app.Fragment(), "repo_create").commitNow();
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        idleMainLooper();
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onStarListsLoaded_createAfterAssignDialogAlreadyOpenSkipsShow()
            throws Exception {
        // L914/L916：repo_create_after_assign 框已开时不再重弹。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(new androidx.fragment.app.Fragment(), "repo_create_after_assign")
                .commitNow();
        List<com.gh4a.fragment.UserListsGraphQL.UserList> lists = new ArrayList<>();
        lists.add(new com.gh4a.fragment.UserListsGraphQL.UserList(
                "l1", "L1", null, new ArrayList<>()));
        StarBatchMoveViewModel.StarListsResult result =
                new StarBatchMoveViewModel.StarListsResult(1L, lists, false);
        callVoid(f, "onStarListsLoaded",
                new Class<?>[]{StarBatchMoveViewModel.StarListsResult.class}, result);
        idleMainLooper();
        assertEquals(0,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onSingleOpChanged_nullViewModelIsNoop() throws Exception {
        // L934：mMoveViewModel 为 null 时直接返回。
        RepositoryFragment f = new RepositoryFragment();
        StarBatchMoveViewModel.SingleOp op =
                new StarBatchMoveViewModel.SingleOp(1L, true, R.string.app_name, null);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        assertEquals(null,
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void listIds_nullReturnsEmpty() throws Exception {
        // L827：listIds(null) 得空表（生产里无 null 调用方，直接测私有方法）。
        RepositoryFragment f = new RepositoryFragment();
        Object ids = call(f, "listIds", new Class<?>[]{java.util.List.class},
                (Object) null);
        assertTrue(((java.util.List<?>) ids).isEmpty());
    }

    @Test
    public void loadReadme_mapStageNotAdded_skipsEncodeDirect() throws Exception {
        // L526（第 3 轮）：map 阶段 isAdded() 为假臂。直接调 map lambda——
        // R1 的整链版可能空转：attach 后若已有同 id 加载在途，force=false
        // 会复用在途 loader 不再订阅上游，map 根本不跑，verify(never) 是
        // 空转通过。直接调 lambda 则必然执行：返回值 present 即证明跑过。
        // mRef 置非空，避免 mRepository.defaultBranch() 在 bare fragment
        // 上 NPE（mRepository 为 null）。
        HttpImageGetter getter = mock(HttpImageGetter.class);
        RepositoryFragment f = new RepositoryFragment();
        setField(f, "mImageGetter", getter);
        setField(f, "mRef", "main");
        java.lang.reflect.Method lambda = readmeMapLambda();
        Object result = lambda.invoke(f, new Object[]{"octo", "cat",
                null, 1L, java.util.Optional.of("<p>hi</p>")});
        assertTrue(((java.util.Optional<?>) result).isPresent());
        org.mockito.Mockito.verify(getter, org.mockito.Mockito.never())
                .encode(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void loadReadme_mapStageNullGetter_skipsEncodeDirect() throws Exception {
        // L526（第 3 轮）：map 阶段 isAdded() 为真、mImageGetter 为 null 臂。
        // 同上，直接调 lambda，避免整链版复用在途 loader 的空转。
        RepositoryFragment f = attachWithContent(repo());
        setField(f, "mImageGetter", null);
        setField(f, "mRef", "main");
        java.lang.reflect.Method lambda = readmeMapLambda();
        Object result = lambda.invoke(f, new Object[]{"octo", "cat",
                mActivity, 1L, java.util.Optional.of("<p>hi</p>")});
        assertTrue(((java.util.Optional<?>) result).isPresent());
    }

    @Test
    public void onCreateListNameConfirmed_unknownTagIsNoop() throws Exception {
        // L844（第 3 轮）：tag 两个分支都不匹配时静默返回（else-if 链末尾
        // fall-through）。TAG_CREATE_REPO 真臂与 AFTER_ASSIGN 真臂已有
        // 既有测试覆盖。
        RepositoryFragment f = attachWithContent(repo());
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onCreateListNameConfirmed",
                new Class<?>[]{String.class, String.class, String.class, boolean.class},
                "unknown_tag", "octo/cat", "New List", false);
        org.mockito.Mockito.verifyNoInteractions(vm);
    }
}
