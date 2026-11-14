package com.gh4a.utils;

import android.content.Context;
import android.net.Uri;
import android.text.style.StyleSpan;

import com.gh4a.ApiRequestException;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchPage;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.git.GitUser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 269：ApiHelpers 的空守卫路径（115 系）此前零单测覆盖。
 * orZero/orFalse 是全树拆箱的收敛点，先锁定其 null/非 null 行为。
 * A 组扩充：作者/提交者、登录名、仓库名、标签色、URI 归一化、SHA-256、
 * 分页（DummyPage/SearchPageAdapter/PageIterator）与 throwOnFailure 族。
 * 需 Context.getString，走 Robolectric；模型用 builder，SearchPage 与
 * GitHubCommentBase 无 builder，用 Mockito mock。
 */
@RunWith(RobolectricTestRunner.class)
public class ApiHelpersTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void orZero_integerNullReturnsZero() {
        assertEquals(0, ApiHelpers.orZero((Integer) null));
    }

    @Test
    public void orZero_integerValuePassesThrough() {
        assertEquals(5, ApiHelpers.orZero(Integer.valueOf(5)));
        assertEquals(-3, ApiHelpers.orZero(Integer.valueOf(-3)));
    }

    @Test
    public void orZero_longNullReturnsZero() {
        assertEquals(0L, ApiHelpers.orZero((Long) null));
    }

    @Test
    public void orZero_longValuePassesThrough() {
        assertEquals(7L, ApiHelpers.orZero(Long.valueOf(7L)));
    }

    @Test
    public void orFalse_nullReturnsFalse() {
        assertFalse(ApiHelpers.orFalse(null));
    }

    @Test
    public void orFalse_valuePassesThrough() {
        assertTrue(ApiHelpers.orFalse(Boolean.TRUE));
        assertFalse(ApiHelpers.orFalse(Boolean.FALSE));
    }

    // ---- getAuthorName / getAuthorLogin / getCommitterName ----

    @Test
    public void getAuthorName_authorLoginWins() {
        Commit commit = Commit.builder()
                .author(User.builder().login("octocat").build())
                .build();
        assertEquals("octocat", ApiHelpers.getAuthorName(context(), commit));
    }

    @Test
    public void getAuthorName_emptyLoginFallsBackToGitAuthorName() {
        Commit commit = Commit.builder()
                .author(User.builder().login("").build())
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("Gitty").build())
                        .build())
                .build();
        assertEquals("Gitty", ApiHelpers.getAuthorName(context(), commit));
    }

    @Test
    public void getAuthorName_noAuthorNoCommitReturnsUnknown() {
        Commit commit = Commit.builder().build();
        assertEquals(context().getString(com.gh4a.R.string.unknown),
                ApiHelpers.getAuthorName(context(), commit));
    }

    @Test
    public void getAuthorName_gitAuthorNameEmptyReturnsUnknown() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("").build())
                        .build())
                .build();
        assertEquals(context().getString(com.gh4a.R.string.unknown),
                ApiHelpers.getAuthorName(context(), commit));
    }

    @Test
    public void getAuthorLogin_authorPresentReturnsLogin() {
        Commit commit = Commit.builder()
                .author(User.builder().login("octocat").build())
                .build();
        assertEquals("octocat", ApiHelpers.getAuthorLogin(commit));
    }

    @Test
    public void getAuthorLogin_noAuthorReturnsNull() {
        assertNull(ApiHelpers.getAuthorLogin(Commit.builder().build()));
    }

    @Test
    public void getCommitterName_committerLoginWins() {
        Commit commit = Commit.builder()
                .committer(User.builder().login("bob").build())
                .build();
        assertEquals("bob", ApiHelpers.getCommitterName(context(), commit));
    }

    @Test
    public void getCommitterName_noCommitterFallsBackToGitCommitterName() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .committer(GitUser.builder().name("Git Bob").build())
                        .build())
                .build();
        assertEquals("Git Bob", ApiHelpers.getCommitterName(context(), commit));
    }

    @Test
    public void getCommitterName_nothingReturnsUnknown() {
        assertEquals(context().getString(com.gh4a.R.string.unknown),
                ApiHelpers.getCommitterName(context(), Commit.builder().build()));
    }

    // ---- authorEqualsCommitter ----

    @Test
    public void authorEqualsCommitter_sameLoginIsTrue() {
        Commit commit = Commit.builder()
                .author(User.builder().login("alice").build())
                .committer(User.builder().login("alice").build())
                .build();
        assertTrue(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_differentLoginIsFalse() {
        Commit commit = Commit.builder()
                .author(User.builder().login("alice").build())
                .committer(User.builder().login("bob").build())
                .build();
        assertFalse(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_loginCompareIsCaseSensitive() {
        Commit commit = Commit.builder()
                .author(User.builder().login("Alice").build())
                .committer(User.builder().login("alice").build())
                .build();
        assertFalse(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_noUsersNoCommitIsFalse() {
        assertFalse(ApiHelpers.authorEqualsCommitter(Commit.builder().build()));
    }

    @Test
    public void authorEqualsCommitter_gitUsersSameEmailIsTrue() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("A").email("a@x.com").build())
                        .committer(GitUser.builder().name("B").email("a@x.com").build())
                        .build())
                .build();
        assertTrue(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_gitUsersDifferentEmailSameNameIsFalse() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("A").email("a@x.com").build())
                        .committer(GitUser.builder().name("A").email("b@x.com").build())
                        .build())
                .build();
        assertFalse(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_gitUsersNullEmailsFallBackToName() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("Same").build())
                        .committer(GitUser.builder().name("Same").build())
                        .build())
                .build();
        assertTrue(ApiHelpers.authorEqualsCommitter(commit));
    }

    @Test
    public void authorEqualsCommitter_oneGitUserNullIsFalse() {
        Commit commit = Commit.builder()
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("A").build())
                        .build())
                .build();
        assertFalse(ApiHelpers.authorEqualsCommitter(commit));
    }

    // ---- getUserLogin / getUserLoginWithType ----

    @Test
    public void getUserLogin_plainLoginPassesThrough() {
        assertEquals("octocat", ApiHelpers.getUserLogin(context(),
                User.builder().login("octocat").build()));
    }

    @Test
    public void getUserLogin_nullUserReturnsDeleted() {
        assertEquals(context().getString(com.gh4a.R.string.deleted),
                ApiHelpers.getUserLogin(context(), null));
    }

    @Test
    public void getUserLogin_nullLoginReturnsDeleted() {
        assertEquals(context().getString(com.gh4a.R.string.deleted),
                ApiHelpers.getUserLogin(context(), User.builder().build()));
    }

    @Test
    public void getUserLogin_botSuffixStrippedWhenTypeNull() {
        assertEquals("botty", ApiHelpers.getUserLogin(context(),
                User.builder().login("botty[bot]").build()));
    }

    @Test
    public void getUserLogin_botSuffixStrippedWhenTypeBot() {
        assertEquals("botty", ApiHelpers.getUserLogin(context(),
                User.builder().login("botty[bot]").type(UserType.Bot).build()));
    }

    @Test
    public void getUserLogin_botSuffixKeptWhenTypeIsUser() {
        assertEquals("botty[bot]", ApiHelpers.getUserLogin(context(),
                User.builder().login("botty[bot]").type(UserType.User).build()));
    }

    @Test
    public void getUserLoginWithType_plainUserHasNoAppendix() {
        assertEquals("octocat", ApiHelpers.getUserLoginWithType(context(),
                User.builder().login("octocat").build()).toString());
    }

    @Test
    public void getUserLoginWithType_boldifyAddsStyleSpan() {
        android.text.SpannableStringBuilder builder =
                ApiHelpers.getUserLoginWithType(context(),
                        User.builder().login("octocat").build(), true);
        assertEquals("octocat", builder.toString());
        assertEquals(1, builder.getSpans(0, builder.length(), StyleSpan.class).length);
    }

    @Test
    public void getUserLoginWithType_botAppendsBotLabel() {
        android.text.SpannableStringBuilder builder =
                ApiHelpers.getUserLoginWithType(context(),
                        User.builder().login("botty[bot]").build());
        assertTrue(builder.toString().contains("botty"));
        assertTrue(builder.toString().contains(
                context().getString(com.gh4a.R.string.user_type_bot)));
    }

    @Test
    public void getUserLoginWithType_mannequinAppendsMannequinLabel() {
        android.text.SpannableStringBuilder builder =
                ApiHelpers.getUserLoginWithType(context(),
                        User.builder().login("ghost").type(UserType.Mannequin).build());
        assertTrue(builder.toString().contains("ghost"));
        assertTrue(builder.toString().contains(
                context().getString(com.gh4a.R.string.user_type_mannequin)));
    }

    // ---- formatRepoName / colorForLabel / userEquals / loginEquals ----

    @Test
    public void formatRepoName_ownerAndName() {
        Repository repo = Repository.builder().name("Hello-World")
                .owner(User.builder().login("octocat").build()).build();
        assertEquals("octocat/Hello-World",
                ApiHelpers.formatRepoName(context(), repo));
    }

    @Test
    public void formatRepoName_nullRepoReturnsDeleted() {
        assertEquals(context().getString(com.gh4a.R.string.deleted),
                ApiHelpers.formatRepoName(context(), null));
    }

    @Test
    public void formatRepoName_nullNameReturnsDeleted() {
        Repository repo = Repository.builder()
                .owner(User.builder().login("octocat").build()).build();
        assertEquals(context().getString(com.gh4a.R.string.deleted),
                ApiHelpers.formatRepoName(context(), repo));
    }

    @Test
    public void formatRepoName_nullOwnerReturnsDeletedPrefix() {
        Repository repo = Repository.builder().name("r").build();
        assertEquals(context().getString(com.gh4a.R.string.deleted) + "/r",
                ApiHelpers.formatRepoName(context(), repo));
    }

    @Test
    public void colorForLabel_nullLabelReturnsDefaultGrey() {
        assertEquals(0xFF999999, ApiHelpers.colorForLabel(null));
    }

    @Test
    public void colorForLabel_nullColorReturnsDefaultGrey() {
        assertEquals(0xFF999999, ApiHelpers.colorForLabel(Label.builder().build()));
    }

    @Test
    public void colorForLabel_validColorParsed() {
        assertEquals(0xFFFF0000, ApiHelpers.colorForLabel(
                Label.builder().color("ff0000").build()));
        assertEquals(0xFF00FF00, ApiHelpers.colorForLabel(
                Label.builder().color("00ff00").build()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void colorForLabel_invalidColorThrows() {
        ApiHelpers.colorForLabel(Label.builder().color("zzzzzz").build());
    }

    @Test
    public void userEquals_sameLoginIgnoringCase() {
        assertTrue(ApiHelpers.userEquals(
                User.builder().login("Alice").build(),
                User.builder().login("alice").build()));
        assertFalse(ApiHelpers.userEquals(
                User.builder().login("alice").build(),
                User.builder().login("bob").build()));
        assertFalse(ApiHelpers.userEquals(null,
                User.builder().login("alice").build()));
        assertFalse(ApiHelpers.userEquals(
                User.builder().login("alice").build(), null));
    }

    @Test
    public void loginEquals_userAndString() {
        assertTrue(ApiHelpers.loginEquals(
                User.builder().login("Alice").build(), "alice"));
        assertFalse(ApiHelpers.loginEquals(
                User.builder().login("Alice").build(), "bob"));
        assertFalse(ApiHelpers.loginEquals((User) null, "alice"));
        assertFalse(ApiHelpers.loginEquals(
                User.builder().login(null).build(), "alice"));
    }

    @Test
    public void loginEquals_stringPair() {
        assertTrue(ApiHelpers.loginEquals("Alice", "alice"));
        assertFalse(ApiHelpers.loginEquals("alice", "bob"));
        assertFalse(ApiHelpers.loginEquals((String) null, "alice"));
        assertFalse(ApiHelpers.loginEquals("alice", null));
    }

    // ---- normalizeUri ----

    @Test
    public void normalizeUri_nullReturnsNull() {
        assertNull(ApiHelpers.normalizeUri(null));
    }

    @Test
    public void normalizeUri_noAuthorityReturnsAsIs() {
        Uri uri = Uri.parse("/repos/o/r");
        assertEquals(uri, ApiHelpers.normalizeUri(uri));
    }

    @Test
    public void normalizeUri_nonApiUriUnchanged() {
        Uri uri = Uri.parse("https://github.com/o/r/issues/1");
        assertEquals(uri.toString(), ApiHelpers.normalizeUri(uri).toString());
    }

    @Test
    public void normalizeUri_apiHostPrefixStripped() {
        assertEquals("https://github.com/o/r",
                ApiHelpers.normalizeUri(
                        Uri.parse("https://api.github.com/repos/o/r")).toString());
    }

    @Test
    public void normalizeUri_reposSegmentNotStrippedTwice() {
        // 263：owner 登录名恰为 repos 时，只削首段 "/repos"
        assertEquals("https://github.com/repos/myrepo",
                ApiHelpers.normalizeUri(Uri.parse(
                        "https://api.github.com/repos/repos/myrepo")).toString());
    }

    @Test
    public void normalizeUri_commitsAndPullsSingularized() {
        assertEquals("https://github.com/o/r/commit/abc",
                ApiHelpers.normalizeUri(Uri.parse(
                        "https://api.github.com/repos/o/r/commits/abc")).toString());
        assertEquals("https://github.com/o/r/pull/5",
                ApiHelpers.normalizeUri(Uri.parse(
                        "https://api.github.com/repos/o/r/pulls/5")).toString());
    }

    @Test
    public void normalizeUri_enterpriseApiPathKeepsAuthority() {
        // 企业版只保 authority（api.company.com 不被削成 company.com），
        // 但路径上的 /api/v3/ 与 /repos/ 仍按同一规则剥掉，Web 路径为 /o/r。
        assertEquals("https://api.company.com/o/r",
                ApiHelpers.normalizeUri(Uri.parse(
                        "https://api.company.com/api/v3/repos/o/r")).toString());
    }

    @Test
    public void normalizeUri_apiHostOnlyStripsPrefix() {
        Uri normalized = ApiHelpers.normalizeUri(Uri.parse("https://api.github.com"));
        assertEquals("github.com", normalized.getAuthority());
    }

    // ---- extractRepoOwnerAndNameFromIssue ----

    @Test
    public void extractRepoOwnerAndName_nullIssueReturnsNullPair() {
        android.util.Pair<String, String> pair =
                ApiHelpers.extractRepoOwnerAndNameFromIssue(null);
        assertNull(pair.first);
        assertNull(pair.second);
    }

    @Test
    public void extractRepoOwnerAndName_fromFullName() {
        Issue issue = Issue.builder()
                .repository(Repository.builder().fullName("o/r").build()).build();
        android.util.Pair<String, String> pair =
                ApiHelpers.extractRepoOwnerAndNameFromIssue(issue);
        assertEquals("o", pair.first);
        assertEquals("r", pair.second);
    }

    @Test
    public void extractRepoOwnerAndName_fromUrlWhenNoFullName() {
        Issue issue = Issue.builder()
                .url("https://api.github.com/repos/o/r/issues/1").build();
        android.util.Pair<String, String> pair =
                ApiHelpers.extractRepoOwnerAndNameFromIssue(issue);
        assertEquals("o", pair.first);
        assertEquals("r", pair.second);
    }

    @Test
    public void extractRepoOwnerAndName_unparseableReturnsNullPair() {
        Issue issue = Issue.builder()
                .repository(Repository.builder().fullName("single").build())
                .build();
        android.util.Pair<String, String> pair =
                ApiHelpers.extractRepoOwnerAndNameFromIssue(issue);
        assertNull(pair.first);
        assertNull(pair.second);
        Issue noData = Issue.builder().build();
        android.util.Pair<String, String> pair2 =
                ApiHelpers.extractRepoOwnerAndNameFromIssue(noData);
        assertNull(pair2.first);
        assertNull(pair2.second);
    }

    // ---- sha256Of / getTotalPagesCount ----

    @Test
    public void sha256Of_knownVectors() {
        assertEquals("", ApiHelpers.sha256Of(null));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                ApiHelpers.sha256Of(""));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApiHelpers.sha256Of("abc"));
    }

    @Test
    public void getTotalPagesCount_nullPageIsOne() {
        assertEquals(1, ApiHelpers.getTotalPagesCount(null));
    }

    @Test
    public void getTotalPagesCount_lastPageValue() {
        Page<String> page = Page.<String>builder()
                .items(Arrays.asList("a")).last(5).build();
        assertEquals(5, ApiHelpers.getTotalPagesCount(page));
    }

    @Test
    public void getTotalPagesCount_nullLastIsOne() {
        Page<String> page = Page.<String>builder()
                .items(Arrays.asList("a")).build();
        assertEquals(1, ApiHelpers.getTotalPagesCount(page));
    }

    // ---- COMMENT_COMPARATOR ----

    @Test
    public void commentComparator_earlierBeforeLater() {
        GitHubCommentBase earlier = mock(GitHubCommentBase.class);
        GitHubCommentBase later = mock(GitHubCommentBase.class);
        when(earlier.createdAt()).thenReturn(new Date(1000));
        when(later.createdAt()).thenReturn(new Date(2000));
        assertTrue(ApiHelpers.COMMENT_COMPARATOR.compare(earlier, later) < 0);
        assertTrue(ApiHelpers.COMMENT_COMPARATOR.compare(later, earlier) > 0);
        assertEquals(0, ApiHelpers.COMMENT_COMPARATOR.compare(earlier, earlier));
    }

    @Test
    public void commentComparator_nullDatesSortLast() {
        GitHubCommentBase dated = mock(GitHubCommentBase.class);
        GitHubCommentBase undated = mock(GitHubCommentBase.class);
        when(dated.createdAt()).thenReturn(new Date(1000));
        when(undated.createdAt()).thenReturn(null);
        assertEquals(1, ApiHelpers.COMMENT_COMPARATOR.compare(undated, dated));
        assertEquals(-1, ApiHelpers.COMMENT_COMPARATOR.compare(dated, undated));
        GitHubCommentBase undated2 = mock(GitHubCommentBase.class);
        when(undated2.createdAt()).thenReturn(null);
        assertEquals(1, ApiHelpers.COMMENT_COMPARATOR.compare(undated, undated2));
    }

    // ---- DummyPage / SearchPageAdapter ----

    @Test
    public void dummyPage_allNullsAndEmptyItems() {
        ApiHelpers.DummyPage<String> page = new ApiHelpers.DummyPage<>();
        assertNull(page.next());
        assertNull(page.last());
        assertNull(page.first());
        assertNull(page.prev());
        assertTrue(page.items().isEmpty());
    }

    @Test
    public void searchPageAdapter_mapsItemsAndPassesPagination() {
        @SuppressWarnings("unchecked")
        SearchPage<String> source = mock(SearchPage.class);
        when(source.items()).thenReturn(Arrays.asList("a", "b"));
        when(source.next()).thenReturn(2);
        when(source.last()).thenReturn(9);
        when(source.first()).thenReturn(1);
        when(source.prev()).thenReturn(null);
        ApiHelpers.SearchPageAdapter<String, String> adapter =
                new ApiHelpers.SearchPageAdapter<>(source, String::toUpperCase);
        assertEquals(Arrays.asList("A", "B"), adapter.items());
        assertEquals(Integer.valueOf(2), adapter.next());
        assertEquals(Integer.valueOf(9), adapter.last());
        assertEquals(Integer.valueOf(1), adapter.first());
        assertNull(adapter.prev());
    }

    @Test
    public void searchPageAdapter_nullItemsBecomesEmptyList() {
        @SuppressWarnings("unchecked")
        SearchPage<String> source = mock(SearchPage.class);
        when(source.items()).thenReturn(null);
        ApiHelpers.SearchPageAdapter<String, String> adapter =
                new ApiHelpers.SearchPageAdapter<>(source, String::toUpperCase);
        assertTrue(adapter.items().isEmpty());
    }

    // ---- throwOnFailure family ----

    @Test
    public void throwOnFailure_successReturnsBody() throws Exception {
        assertEquals("body",
                ApiHelpers.throwOnFailure(Response.success("body")));
    }

    @Test(expected = ApiRequestException.class)
    public void throwOnFailure_errorThrows() throws Exception {
        ApiHelpers.throwOnFailure(Response.error(404,
                ResponseBody.create(MediaType.parse("text/plain"), "")));
    }

    @Test
    public void mapToTrueOnSuccess_successReturnsTrue() throws Exception {
        assertTrue(ApiHelpers.mapToTrueOnSuccess(Response.success(null)));
    }

    @Test(expected = ApiRequestException.class)
    public void mapToTrueOnSuccess_errorThrows() throws Exception {
        ApiHelpers.mapToTrueOnSuccess(Response.error(404,
                ResponseBody.create(MediaType.parse("text/plain"), "")));
    }

    @Test
    public void mapToBooleanOrThrowOnFailure_successIsTrue() throws Exception {
        assertTrue(ApiHelpers.mapToBooleanOrThrowOnFailure(Response.success(null)));
    }

    @Test
    public void mapToBooleanOrThrowOnFailure_notFoundIsFalse() throws Exception {
        assertFalse(ApiHelpers.mapToBooleanOrThrowOnFailure(Response.error(404,
                ResponseBody.create(MediaType.parse("text/plain"), ""))));
    }

    @Test(expected = ApiRequestException.class)
    public void mapToBooleanOrThrowOnFailure_serverErrorThrows() throws Exception {
        ApiHelpers.mapToBooleanOrThrowOnFailure(Response.error(500,
                ResponseBody.create(MediaType.parse("text/plain"), "")));
    }

    // ---- PageIterator ----

    private ApiHelpers.PageIterator.PageProducer<String> twoPageProducer() {
        return page -> {
            if (page == 1) {
                return Single.just(Response.success(Page.<String>builder()
                        .items(Arrays.asList("a", "b")).next(2).build()));
            }
            return Single.just(Response.success(Page.<String>builder()
                    .items(Arrays.asList("c")).next(null).build()));
        };
    }

    @Test
    public void pageIteratorToSingle_collectsAllPages() {
        TestObserver<List<String>> observer =
                ApiHelpers.PageIterator.toSingle(twoPageProducer()).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(Arrays.asList("a", "b", "c"));
    }

    @Test
    public void pageIteratorToObservable_emitsEachPage() {
        TestObserver<List<String>> observer =
                ApiHelpers.PageIterator.toObservable(twoPageProducer()).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValues(
                Arrays.asList("a", "b"), Arrays.asList("c"));
    }

    // 疑似生产 bug（A 组 2026-11-10，测试保持失败态，不迁就）：
    // 204 空 body 时 evaluateError 的 map 返回 response.body() == null，
    // RxJava2 的 map 拒 null（"The mapper function returned a null value"），
    // 直接走 onError；toObservable 里 responsePage == null 的判空是死代码，
    // 永远到不了。按注释本意应返回空列表并结束分页。
    @Test
    public void pageIteratorToSingle_nullBodyPageIsEmptyList() {
        ApiHelpers.PageIterator.PageProducer<String> producer =
                page -> Single.just(Response.success((Page<String>) null));
        TestObserver<List<String>> observer =
                ApiHelpers.PageIterator.toSingle(producer).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(Arrays.asList());
    }

    @Test
    public void pageIteratorToSingle_nullItemsPageIsSkipped() {
        // Page.items() 是 @NonNull，builder 拒 null；缺 items 的页只能
        // 用 mock 造（Moshi 反序列化同样绕过 builder 产出 null items）。
        @SuppressWarnings("unchecked")
        Page<String> page = mock(Page.class);
        when(page.items()).thenReturn(null);
        when(page.next()).thenReturn(null);
        ApiHelpers.PageIterator.PageProducer<String> producer =
                p -> Single.just(Response.success(page));
        TestObserver<List<String>> observer =
                ApiHelpers.PageIterator.toSingle(producer).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(Arrays.asList());
    }

    @Test
    public void pageIteratorFirst_findsMatchingItemOnSecondPage() {
        TestObserver<java.util.Optional<String>> observer =
                ApiHelpers.PageIterator.first(twoPageProducer(), "c"::equals).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(java.util.Optional.of("c"));
    }

    @Test
    public void pageIteratorFirst_noMatchReturnsEmpty() {
        TestObserver<java.util.Optional<String>> observer =
                ApiHelpers.PageIterator.first(twoPageProducer(), "zzz"::equals).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(java.util.Optional.empty());
    }

    @Test
    public void pageIteratorToSingle_errorResponseFailsWithApiException() {
        ApiHelpers.PageIterator.PageProducer<String> producer =
                page -> Single.just(Response.error(404,
                        ResponseBody.create(MediaType.parse("text/plain"), "")));
        TestObserver<List<String>> observer =
                ApiHelpers.PageIterator.toSingle(producer).test();
        observer.assertError(ApiRequestException.class);
    }
}
