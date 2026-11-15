package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Looper;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.widget.OverviewRow;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.service.organizations.OrganizationMemberService;
import com.meisolsson.githubsdk.service.organizations.OrganizationService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;
import com.meisolsson.githubsdk.service.users.UserFollowerService;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import retrofit2.Response;

/** UserFragment：用户详情页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class UserFragmentTest {
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

    private static int orZero(Integer value) throws Exception {
        Method m = UserFragment.class.getDeclaredMethod("orZero", Integer.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, value);
    }

    private static boolean canFollowUser(UserFragment f) throws Exception {
        Method m = UserFragment.class.getDeclaredMethod("canFollowUser");
        m.setAccessible(true);
        return (Boolean) m.invoke(f);
    }

    @Test
    public void newInstance_withUserStoresParcelable() {
        User user = User.builder().login("octocat").build();
        UserFragment f = UserFragment.newInstance(user);
        assertSame(user, f.getArguments().getParcelable("user"));
        assertNull(f.getArguments().getString("login"));
    }

    @Test
    public void newInstance_withLoginStoresLogin() {
        UserFragment f = UserFragment.newInstance("octocat");
        assertEquals("octocat", f.getArguments().getString("login"));
        assertNull(f.getArguments().getParcelable("user"));
    }

    @Test
    public void onCreate_withLoginKeepsLoginAndNullUser() throws Exception {
        UserFragment f = attach(UserFragment.newInstance("octocat"));
        java.lang.reflect.Field login = UserFragment.class.getDeclaredField("mLogin");
        java.lang.reflect.Field user = UserFragment.class.getDeclaredField("mUser");
        java.lang.reflect.Field isSelf = UserFragment.class.getDeclaredField("mIsSelf");
        login.setAccessible(true);
        user.setAccessible(true);
        isSelf.setAccessible(true);
        assertEquals("octocat", login.get(f));
        assertNull(user.get(f));
        assertFalse((Boolean) isSelf.get(f));
    }

    @Test
    public void onCreate_withUserKeepsUser() throws Exception {
        User user = User.builder().login("octocat").build();
        UserFragment f = attach(UserFragment.newInstance(user));
        java.lang.reflect.Field userField = UserFragment.class.getDeclaredField("mUser");
        userField.setAccessible(true);
        assertEquals("octocat", ((User) userField.get(f)).login());
    }

    @Test
    public void orZero_nullIsZeroAndValuePassesThrough() throws Exception {
        assertEquals(0, orZero(null));
        assertEquals(0, orZero(0));
        assertEquals(42, orZero(42));
        assertEquals(-3, orZero(-3));
    }

    @Test
    public void canFollowUser_whenLoggedOutIsFalse() throws Exception {
        UserFragment f = attach(UserFragment.newInstance("someone"));
        assertFalse(canFollowUser(f));
        UserFragment withUser = attach(UserFragment.newInstance(
                User.builder().login("someone").build()));
        assertFalse(canFollowUser(withUser));
    }

    // ---- fillData / fillTopRepos / fillOrganizations（布局 R.layout.user） ----

    private static Object field(UserFragment f, String name) throws Exception {
        java.lang.reflect.Field field = UserFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(UserFragment f, String name, Object value)
            throws Exception {
        java.lang.reflect.Field field = UserFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static void callVoid(UserFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = UserFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(f, args);
    }

    private static User richUser() {
        return User.builder()
                .login("octo")
                .name("Octo Cat")
                .email("octo@example.com")
                .blog("https://example.com")
                .company("Acme")
                .location("Shanghai")
                .bio("Hello world")
                .type(com.meisolsson.githubsdk.model.UserType.User)
                .followers(5)
                .following(3)
                .publicRepos(7)
                .totalPrivateRepos(2)
                .publicGists(4)
                .privateGists(1)
                .createdAt(new java.util.Date(1700000000000L))
                .build();
    }

    private UserFragment attachWithContent(User user) throws Exception {
        UserFragment f = attach(UserFragment.newInstance(user));
        mActivity.setTheme(com.gh4a.R.style.AppTheme);
        android.view.View content = android.view.LayoutInflater.from(mActivity)
                .inflate(com.gh4a.R.layout.user, null);
        setField(f, "mContentView", content);
        return f;
    }

    private static String text(android.view.View root, int id) {
        return ((android.widget.TextView) root.findViewById(id)).getText().toString();
    }

    // OverviewRow 的文本在内部 mLabel（R.id.label）
    private static String rowText(android.view.View root, int id) {
        android.view.View row = root.findViewById(id);
        return ((android.widget.TextView) row.findViewById(com.gh4a.R.id.label))
                .getText().toString();
    }

    @Test
    public void fillData_richUserFillsViews() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals("Octo Cat", text(content, com.gh4a.R.id.tv_name));
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.tv_email).getVisibility());
        assertEquals("octo@example.com", text(content, com.gh4a.R.id.tv_email));
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.followers_row).getVisibility());
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.join_date_row).getVisibility());
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.gists_row).getVisibility());
        // 普通用户不显示类型行
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.type_row).getVisibility());
        // 仓库数 = 公开 7 + 私有 2
        assertTrue(rowText(content, com.gh4a.R.id.repos_row).contains("9"));
        // Gist 数 = 公开 4 + 私有 1
        assertTrue(rowText(content, com.gh4a.R.id.gists_row).contains("5"));
    }

    @Test
    public void fillData_orgUserShowsMembersRowAndType() throws Exception {
        User org = User.builder()
                .login("acme-org")
                .name("Acme Org")
                .type(com.meisolsson.githubsdk.model.UserType.Organization)
                .build();
        UserFragment f = attachWithContent(org);
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.followers_row).getVisibility());
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.members_row).getVisibility());
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.type_row).getVisibility());
        assertEquals(mActivity.getString(com.gh4a.R.string.user_type_org),
                rowText(content, com.gh4a.R.id.type_row));
    }

    @Test
    public void fillData_botUserShowsBotType() throws Exception {
        User bot = User.builder()
                .login("bot")
                .type(com.meisolsson.githubsdk.model.UserType.Bot)
                .build();
        UserFragment f = attachWithContent(bot);
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.type_row).getVisibility());
        assertEquals(mActivity.getString(com.gh4a.R.string.user_type_bot),
                rowText(content, com.gh4a.R.id.type_row));
    }

    @Test
    public void fillData_nullTypeHidesTypeRow() throws Exception {
        UserFragment f = attachWithContent(User.builder().login("x").build());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.type_row).getVisibility());
    }

    @Test
    public void fillData_selfHidesGistsRow() throws Exception {
        UserFragment f = attachWithContent(richUser());
        setField(f, "mIsSelf", true);
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.gists_row).getVisibility());
    }

    @Test
    public void fillData_blankNameFallsBackToLogin() throws Exception {
        UserFragment f = attachWithContent(User.builder().login("octo").build());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals("octo", text(content, com.gh4a.R.id.tv_name));
    }

    @Test
    public void fillTextView_blankHidesAndTextShows() throws Exception {
        UserFragment f = attachWithContent(richUser());
        android.view.View content = (android.view.View) field(f, "mContentView");
        callVoid(f, "fillTextView",
                new Class<?>[]{int.class, String.class},
                com.gh4a.R.id.tv_email, "");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.tv_email).getVisibility());
        callVoid(f, "fillTextView",
                new Class<?>[]{int.class, String.class},
                com.gh4a.R.id.tv_email, "a@b.c");
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.tv_email).getVisibility());
        assertEquals("a@b.c", text(content, com.gh4a.R.id.tv_email));
    }

    private static com.meisolsson.githubsdk.model.Repository topRepo(
            String fullName, int stars, int forks) {
        String[] parts = fullName.split("/", 2);
        return com.meisolsson.githubsdk.model.Repository.builder()
                .fullName(fullName)
                .owner(User.builder().login(parts[0]).build())
                .name(parts[1])
                .description("desc " + parts[1])
                .stargazersCount(stars)
                .forksCount(forks)
                .build();
    }

    @Test
    public void fillTopRepos_withReposAddsRows() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                java.util.Arrays.asList(topRepo("o/a", 10, 2), topRepo("o/b", 5, 1)));
        android.view.View content = (android.view.View) field(f, "mContentView");
        android.widget.LinearLayout ll =
                content.findViewById(com.gh4a.R.id.ll_top_repos);
        assertEquals(2, ll.getChildCount());
        assertTrue(text(ll.getChildAt(0), com.gh4a.R.id.tv_title).contains("o/a"));
        assertEquals("10", text(ll.getChildAt(0), com.gh4a.R.id.tv_stars));
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.btn_repos).getVisibility());
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.pb_top_repos).getVisibility());
    }

    @Test
    public void fillTopRepos_emptyShowsHint() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                new java.util.ArrayList<>());
        android.view.View content = (android.view.View) field(f, "mContentView");
        android.widget.LinearLayout ll =
                content.findViewById(com.gh4a.R.id.ll_top_repos);
        assertEquals(1, ll.getChildCount());
        assertEquals(mActivity.getString(com.gh4a.R.string.user_no_repos),
                ((android.widget.TextView) ll.getChildAt(0)).getText().toString());
    }

    @Test
    public void fillTopRepos_nullContentViewIsNoop() throws Exception {
        UserFragment f = attach(UserFragment.newInstance(richUser()));
        setField(f, "mContentView", null);
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                new java.util.ArrayList<>());
    }

    @Test
    public void fillOrganizations_withOrgsAddsRows() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillOrganizations", new Class<?>[]{java.util.List.class},
                java.util.Arrays.asList(
                        User.builder().login("org1").build(),
                        User.builder().login("org2").build()));
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.VISIBLE,
                content.findViewById(com.gh4a.R.id.ll_orgs).getVisibility());
        android.widget.LinearLayout llOrg =
                content.findViewById(com.gh4a.R.id.ll_org);
        assertEquals(2, llOrg.getChildCount());
        assertEquals("org1", text(llOrg.getChildAt(0), com.gh4a.R.id.tv_title));
    }

    @Test
    public void fillOrganizations_emptyHidesContainer() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillOrganizations", new Class<?>[]{java.util.List.class},
                new java.util.ArrayList<>());
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.ll_orgs).getVisibility());
    }

    @Test
    public void updateFollowingUi_followingSubtractsSelf() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsFollowing", true);
        callVoid(f, "updateFollowingUi", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        // 关注后粉丝数含自己，显示 5-1=4
        assertTrue(rowText(content, com.gh4a.R.id.followers_row).contains("4"));
        assertTrue(((com.gh4a.widget.OverviewRow)
                content.findViewById(com.gh4a.R.id.followers_row)) != null);
    }

    @Test
    public void updateFollowingUi_notFollowingShowsFollowers() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsFollowing", false);
        callVoid(f, "updateFollowingUi", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, com.gh4a.R.id.followers_row).contains("5"));
    }

    @Test
    public void updateFollowingUi_nullFollowersShowsZero() throws Exception {
        UserFragment f = attachWithContent(User.builder().login("octo")
                .type(com.meisolsson.githubsdk.model.UserType.User).build());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsFollowing", false);
        callVoid(f, "updateFollowingUi", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, com.gh4a.R.id.followers_row).contains("0"));
    }

    @Test
    public void onClick_btnReposStartsRepositoryList() throws Exception {
        UserFragment f = attachWithContent(richUser());
        android.view.View btn = new android.view.View(mActivity);
        btn.setId(com.gh4a.R.id.btn_repos);
        f.onClick(btn);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName()
                .contains("RepositoryListActivity"));
    }

    @Test
    public void onClick_repoTagStartsRepositoryActivity() throws Exception {
        UserFragment f = attachWithContent(richUser());
        android.view.View row = new android.view.View(mActivity);
        row.setTag(topRepo("o/a", 1, 0));
        f.onClick(row);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("RepositoryActivity"));
    }

    @Test
    public void onClick_userTagStartsUserActivity() throws Exception {
        UserFragment f = attachWithContent(richUser());
        android.view.View row = new android.view.View(mActivity);
        row.setTag(User.builder().login("someone").build());
        f.onClick(row);
        android.content.Intent intent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("UserActivity"));
    }

    @Test
    public void onIconClick_withFollowingStateToggles() throws Exception {
        com.meisolsson.githubsdk.service.users.UserFollowerService service =
                mock(com.meisolsson.githubsdk.service.users.UserFollowerService.class);
        when(service.followUser(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success((Void) null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.users.UserFollowerService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(service);
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            com.gh4a.widget.OverviewRow followersRow =
                    (com.gh4a.widget.OverviewRow) field(f, "mFollowersRow");
            f.onIconClick(followersRow);
            long deadline = System.currentTimeMillis() + 5000;
            while (!Boolean.TRUE.equals(field(f, "mIsFollowing"))) {
                org.robolectric.Shadows.shadowOf(
                        android.os.Looper.getMainLooper()).idle();
                if (System.currentTimeMillis() > deadline) break;
                Thread.sleep(20);
            }
            assertTrue((Boolean) field(f, "mIsFollowing"));
            // 关注后粉丝数 5 → 6
            assertEquals(6, (int) ((User) field(f, "mUser")).followers());
        }
    }

    @Test
    public void toggleFollowingState_errorKeepsState() throws Exception {
        com.meisolsson.githubsdk.service.users.UserFollowerService service =
                mock(com.meisolsson.githubsdk.service.users.UserFollowerService.class);
        when(service.followUser(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("no net")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.users.UserFollowerService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(service);
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            Thread.sleep(300);
            org.robolectric.Shadows.shadowOf(
                    android.os.Looper.getMainLooper()).idle();
            assertFalse((Boolean) field(f, "mIsFollowing"));
        }
    }

    @Test
    public void loadIsFollowingStateIfNeeded_notFollowableSetsFalse() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        callVoid(f, "loadIsFollowingStateIfNeeded",
                new Class<?>[]{boolean.class}, false);
        assertFalse((Boolean) field(f, "mIsFollowing"));
    }

    @Test
    public void loadOrganizationMemberCountIfOrg_nonOrgIsNoop() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "loadOrganizationMemberCountIfOrg",
                new Class<?>[]{boolean.class}, false);
    }

    @Test
    public void loadOrganizationsIfUser_nonUserFillsNull() throws Exception {
        User org = User.builder()
                .login("acme-org")
                .type(com.meisolsson.githubsdk.model.UserType.Organization)
                .build();
        UserFragment f = attachWithContent(org);
        callVoid(f, "loadOrganizationsIfUser", new Class<?>[]{boolean.class}, false);
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(android.view.View.GONE,
                content.findViewById(com.gh4a.R.id.ll_orgs).getVisibility());
    }

    @Test
    public void onRefresh_withUserResetsFollowingState() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsFollowing", true);
        // onRefresh 的 loadTopRepositories/loadOrganizations 走 RxLoader，
        // 测试环境只断言同步部分：mIsFollowing 复位 + 未登录不拉关注态
        com.meisolsson.githubsdk.service.repositories.RepositoryService repoService =
                mock(com.meisolsson.githubsdk.service.repositories.RepositoryService.class);
        com.meisolsson.githubsdk.model.Page<com.meisolsson.githubsdk.model.Repository> page =
                mock(com.meisolsson.githubsdk.model.Page.class);
        when(page.items()).thenReturn(new java.util.ArrayList<>());
        when(repoService.getUserRepositories(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.just(
                        retrofit2.Response.success(page)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.repositories.RepositoryService.class),
                            org.mockito.ArgumentMatchers.anyBoolean(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(repoService);
            mocked.when(() -> com.gh4a.ServiceFactory.getForFullPagedLists(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.organizations.OrganizationService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(mock(
                            com.meisolsson.githubsdk.service.organizations.OrganizationService.class));
            f.onRefresh();
            // onRefresh 先把 mIsFollowing 置 null，随后 loadIsFollowingStateIfNeeded
            // 在未登录时直接置 false（同步完成）
            assertFalse((Boolean) field(f, "mIsFollowing"));
        }
    }

    // ---- 缺口补齐：懒加载 / onViewCreated / 关注态 / 各 load* ----

    private static void idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(Duration duration) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(duration);
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
            idleMain();
        }
        assertTrue(condition.getAsBoolean());
    }

    private static Object fieldAny(Object target, String name) throws Exception {
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

    private static UserFragment attachResumed(UserFragment f) {
        FragmentTestHelper.attachTo(FragmentActivity.class, f);
        return f;
    }

    private static void removeFragment(UserFragment f) {
        f.getParentFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
    }

    private static RepositoryService mockRepoServiceNever() {
        RepositoryService service = mock(RepositoryService.class);
        when(service.getUserRepositories(anyString(), anyMap(), anyLong()))
                .thenReturn(Single.never());
        when(service.getUserRepositories(anyMap(), anyLong()))
                .thenReturn(Single.never());
        when(service.getOrganizationRepositories(anyString(), anyMap(), anyLong()))
                .thenReturn(Single.never());
        return service;
    }

    private static OrganizationService mockOrgServiceNever() {
        OrganizationService service = mock(OrganizationService.class);
        when(service.getUserPublicOrganizations(anyString(), anyLong()))
                .thenReturn(Single.never());
        when(service.getMyOrganizations(anyLong()))
                .thenReturn(Single.never());
        return service;
    }

    private static UserFollowerService mockFollowerService() {
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.isFollowing(anyString())).thenReturn(Single.never());
        when(service.followUser(anyString()))
                .thenReturn(Single.just(Response.success(null)));
        when(service.unfollowUser(anyString()))
                .thenReturn(Single.just(Response.success(null)));
        return service;
    }

    private static MockedStatic<ServiceFactory> mockServices(
            RepositoryService repo, OrganizationService org,
            UserService user, UserFollowerService follower,
            OrganizationMemberService member) {
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        if (repo != null) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryService.class),
                    anyBoolean(), eq(5))).thenReturn(repo);
        }
        if (org != null) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(OrganizationService.class), anyBoolean())).thenReturn(org);
        }
        if (user != null) {
            mocked.when(() -> ServiceFactory.get(eq(UserService.class), anyBoolean()))
                    .thenReturn(user);
        }
        if (follower != null) {
            mocked.when(() -> ServiceFactory.get(eq(UserFollowerService.class), anyBoolean()))
                    .thenReturn(follower);
        }
        if (member != null) {
            mocked.when(() -> ServiceFactory.get(eq(OrganizationMemberService.class),
                    anyBoolean(), eq(1))).thenReturn(member);
        }
        return mocked;
    }

    @Test
    public void onCreate_selfUserSetsIsSelf() throws Exception {
        Gh4Application.get().addAccount(
                User.builder().login("octo").build(), "fake-token");
        try {
            UserFragment f = attach(UserFragment.newInstance(richUser()));
            assertTrue((Boolean) field(f, "mIsSelf"));
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void onViewCreated_withUserFillsData() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(), null, null, null)) {
            UserFragment f = attachResumed(UserFragment.newInstance(richUser()));
            View content = (View) field(f, "mContentView");
            assertEquals("Octo Cat", text(content, R.id.tv_name));
            assertTrue((Boolean) fieldAny(f, "mContentShown"));
        }
    }

    @Test
    public void onViewCreated_loginOnlyLoadsUser() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.getUser(anyString()))
                .thenReturn(Single.just(Response.success(richUser())));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(),
                userService, null, null)) {
            UserFragment f = attachResumed(UserFragment.newInstance("octo"));
            waitUntil(() -> {
                try {
                    return field(f, "mUser") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            View content = (View) field(f, "mContentView");
            assertEquals("Octo Cat", text(content, R.id.tv_name));
            assertTrue((Boolean) fieldAny(f, "mContentShown"));
        }
    }

    @Test
    public void onViewCreated_loginOnlyErrorKeepsNullUser() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.getUser(anyString()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(),
                userService, null, null)) {
            UserFragment f = attachResumed(UserFragment.newInstance("octo"));
            idleFor(Duration.ofSeconds(2));
            assertNull(field(f, "mUser"));
        }
    }

    @Test
    public void onViewCreated_noUserNoLoginShowsBlankContent() throws Exception {
        UserFragment noLogin = attachResumed(UserFragment.newInstance((User) null));
        assertTrue((Boolean) fieldAny(noLogin, "mContentShown"));
        UserFragment emptyLogin = attachResumed(UserFragment.newInstance(""));
        assertTrue((Boolean) fieldAny(emptyLogin, "mContentShown"));
    }

    @Test
    public void onRefresh_loginOnlyCallsLoadUser() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.getUser(anyString()))
                .thenReturn(Single.just(Response.success(richUser())));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, userService, null, null)) {
            UserFragment f = attach(UserFragment.newInstance("octo"));
            f.onRefresh();
            verify(userService).getUser("octo");
        }
    }

    @Test
    public void onRefresh_noUserNoLoginReturnsImmediately() throws Exception {
        UserFragment noLogin = attach(UserFragment.newInstance((User) null));
        noLogin.onRefresh();
        assertNull(field(noLogin, "mIsFollowing"));
        UserFragment emptyLogin = attach(UserFragment.newInstance(""));
        emptyLogin.onRefresh();
        assertNull(field(emptyLogin, "mIsFollowing"));
    }

    @Test
    public void canFollowUser_authorizedVariants() throws Exception {
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try {
            assertFalse(canFollowUser(attach(UserFragment.newInstance("someone"))));
            assertFalse(canFollowUser(attach(UserFragment.newInstance(
                    User.builder().login("acme-org")
                            .type(UserType.Organization).build()))));
            assertTrue(canFollowUser(attach(UserFragment.newInstance(
                    User.builder().login("someone").type(UserType.User).build()))));
            assertFalse(canFollowUser(attach(UserFragment.newInstance(
                    User.builder().login("octocat").type(UserType.User).build()))));
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void fillData_authorizedSetsFollowersIconListener() throws Exception {
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            android.view.View content = (android.view.View) field(f, "mContentView");
            try (MockedStatic<ServiceFactory> mocked = mockServices(
                    null, null, null, mockFollowerService(), null)) {
                // 点粉丝行的图标：若 fillData 未接监听，performClick 是空操作
                content.findViewById(R.id.followers_row)
                        .findViewById(R.id.icon).performClick();
                waitUntil(() -> {
                    try {
                        return Boolean.TRUE.equals(field(f, "mIsFollowing"));
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void fillData_mannequinTypeRowStaysGone() throws Exception {
        User mannequin = User.builder().login("m")
                .type(UserType.Mannequin).build();
        UserFragment f = attachWithContent(mannequin);
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertEquals(View.GONE,
                content.findViewById(R.id.type_row).getVisibility());
    }

    @Test
    public void onClick_orgReposButtonPassesIsOrg() throws Exception {
        User org = User.builder().login("acme-org").name("Acme Org")
                .type(UserType.Organization).build();
        UserFragment f = attachWithContent(org);
        android.view.View btn = new android.view.View(mActivity);
        btn.setId(R.id.btn_repos);
        f.onClick(btn);
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getBooleanExtra("is_org", false));
        assertTrue(intent.getComponent().getClassName()
                .contains("RepositoryListActivity"));
    }

    @Test
    public void onClick_plainViewDoesNothing() throws Exception {
        UserFragment f = attachWithContent(richUser());
        f.onClick(new android.view.View(mActivity));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onIconClick_nullFollowingStateIsNoop() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        f.onIconClick((OverviewRow) content.findViewById(R.id.followers_row));
        assertNull(field(f, "mIsFollowing"));
    }

    @Test
    public void fillTopRepos_nullShowsHint() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                (Object) null);
        android.view.View content = (android.view.View) field(f, "mContentView");
        android.widget.LinearLayout ll =
                content.findViewById(R.id.ll_top_repos);
        assertEquals(1, ll.getChildCount());
        assertEquals(mActivity.getString(R.string.user_no_repos),
                ((android.widget.TextView) ll.getChildAt(0)).getText().toString());
    }

    @Test
    public void fillTopRepos_repoWithoutDescriptionHidesDesc() throws Exception {
        Repository repo = Repository.builder()
                .fullName("o/nodesc")
                .owner(User.builder().login("o").build())
                .name("nodesc")
                .isPrivate(false)
                .isFork(false)
                .size(0)
                .stargazersCount(3)
                .forksCount(1)
                .build();
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                Collections.singletonList(repo));
        android.view.View content = (android.view.View) field(f, "mContentView");
        android.widget.LinearLayout ll =
                content.findViewById(R.id.ll_top_repos);
        assertEquals(1, ll.getChildCount());
        assertEquals(View.GONE,
                ll.getChildAt(0).findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void fillTopRepos_noMoreButtonStillRenders() throws Exception {
        mActivity.setTheme(R.style.AppTheme);
        UserFragment f = attach(UserFragment.newInstance(richUser()));
        android.widget.LinearLayout content = new android.widget.LinearLayout(mActivity);
        android.widget.LinearLayout ll = new android.widget.LinearLayout(mActivity);
        ll.setId(R.id.ll_top_repos);
        android.view.View progress = new android.view.View(mActivity);
        progress.setId(R.id.pb_top_repos);
        content.addView(ll);
        content.addView(progress);
        setField(f, "mContentView", content);
        callVoid(f, "fillTopRepos", new Class<?>[]{java.util.Collection.class},
                Collections.singletonList(topRepo("o/a", 10, 2)));
        assertEquals(1, ll.getChildCount());
        assertEquals(View.VISIBLE, ll.getVisibility());
        assertEquals(View.GONE, progress.getVisibility());
    }

    @Test
    public void toggleFollowingState_unfollowFlipsState() throws Exception {
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.unfollowUser(anyString()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, null, service, null)) {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", true);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            waitUntil(() -> {
                try {
                    return Boolean.FALSE.equals(field(f, "mIsFollowing"));
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(4, (int) ((User) field(f, "mUser")).followers());
        }
    }

    @Test
    public void updateFollowingUi_nullFollowingShowsFollowers() throws Exception {
        UserFragment f = attachWithContent(richUser());
        callVoid(f, "fillData", new Class<?>[]{});
        setField(f, "mIsFollowing", null);
        callVoid(f, "updateFollowingUi", new Class<?>[]{});
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, R.id.followers_row).contains("5"));
    }

    @Test
    public void toggleFollowingState_successAfterDetachIsIgnored() throws Exception {
        SingleSubject<Response<Void>> subject = SingleSubject.create();
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.followUser(anyString())).thenReturn(subject);
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, null, service, null)) {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            // 先等 IO 侧订阅落位，再 detach、再发射：顺序确定，消费者必跑且必见 detached
            waitUntil(subject::hasObservers);
            removeFragment(f);
            subject.onSuccess(Response.success(null));
            idleMain();
            assertFalse((Boolean) field(f, "mIsFollowing"));
        }
    }

    @Test
    public void toggleFollowingState_successWithNullUserSkipsFlip() throws Exception {
        SingleSubject<Response<Void>> subject = SingleSubject.create();
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.followUser(anyString())).thenReturn(subject);
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, null, service, null)) {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            waitUntil(subject::hasObservers);
            setField(f, "mUser", null);
            subject.onSuccess(Response.success(null));
            idleMain();
            assertFalse((Boolean) field(f, "mIsFollowing"));
        }
    }

    @Test
    public void toggleFollowingState_successWithNullFollowingSkipsFlip() throws Exception {
        SingleSubject<Response<Void>> subject = SingleSubject.create();
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.followUser(anyString())).thenReturn(subject);
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, null, service, null)) {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            waitUntil(subject::hasObservers);
            setField(f, "mIsFollowing", null);
            subject.onSuccess(Response.success(null));
            idleMain();
            assertNull(field(f, "mIsFollowing"));
        }
    }

    @Test
    public void toggleFollowingState_errorAfterDetachIsIgnored() throws Exception {
        SingleSubject<Response<Void>> subject = SingleSubject.create();
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.followUser(anyString())).thenReturn(subject);
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                null, null, null, service, null)) {
            UserFragment f = attachWithContent(richUser());
            callVoid(f, "fillData", new Class<?>[]{});
            setField(f, "mIsFollowing", false);
            callVoid(f, "toggleFollowingState", new Class<?>[]{});
            waitUntil(subject::hasObservers);
            removeFragment(f);
            subject.onError(new RuntimeException("no net"));
            idleMain();
            assertFalse((Boolean) field(f, "mIsFollowing"));
        }
    }

    @Test
    public void loadTopRepositories_selfUsesSelfOverload() throws Exception {
        RepositoryService repoService = mockRepoServiceNever();
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                repoService, null, null, null, null)) {
            UserFragment f = attachWithContent(richUser());
            setField(f, "mIsSelf", true);
            callVoid(f, "loadTopRepositories", new Class<?>[]{boolean.class}, false);
            verify(repoService).getUserRepositories(anyMap(), eq(1L));
        }
    }

    @Test
    public void loadTopRepositories_orgUsesOrgOverload() throws Exception {
        RepositoryService repoService = mockRepoServiceNever();
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                repoService, null, null, null, null)) {
            User org = User.builder().login("acme-org")
                    .type(UserType.Organization).build();
            UserFragment f = attachWithContent(org);
            callVoid(f, "loadTopRepositories", new Class<?>[]{boolean.class}, false);
            verify(repoService).getOrganizationRepositories(
                    eq("acme-org"), anyMap(), eq(1L));
        }
    }

    @Test
    public void loadOrganizations_selfUsesMyOrganizations() throws Exception {
        OrganizationService orgService = mockOrgServiceNever();
        Gh4Application.get().addAccount(
                User.builder().login("octo").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), orgService, null, null, null)) {
            attachResumed(UserFragment.newInstance(richUser()));
            verify(orgService, Mockito.timeout(5000)).getMyOrganizations(anyLong());
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void loadOrganizations_otherUserUsesPublicOrganizations() throws Exception {
        OrganizationService orgService = mockOrgServiceNever();
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), orgService, null, null, null)) {
            attachResumed(UserFragment.newInstance(richUser()));
            verify(orgService, Mockito.timeout(5000))
                    .getUserPublicOrganizations(eq("octo"), anyLong());
        }
    }

    private static Page<User> memberPage(Integer last, List<User> users) {
        Page<User> page = mock(Page.class);
        when(page.last()).thenReturn(last);
        when(page.items()).thenReturn(users);
        return page;
    }

    private UserFragment attachOrgWithMemberPage(Page<User> page, String expected)
            throws Exception {
        OrganizationMemberService memberService = mock(OrganizationMemberService.class);
        when(memberService.getMembers(anyString(), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(), null, null, memberService)) {
            User org = User.builder().login("acme-org")
                    .type(UserType.Organization).build();
            UserFragment f = attachResumed(UserFragment.newInstance(org));
            waitUntil(() -> {
                try {
                    android.view.View content = (android.view.View) field(f, "mContentView");
                    return content != null
                            && rowText(content, R.id.members_row).contains(expected);
                } catch (Exception e) {
                    return false;
                }
            });
            return f;
        }
    }

    @Test
    public void loadOrganizationMemberCount_singlePageCountsItems() throws Exception {
        Page<User> page = memberPage(null, java.util.Arrays.asList(
                User.builder().login("u1").build(),
                User.builder().login("u2").build()));
        UserFragment f = attachOrgWithMemberPage(page, "2");
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, R.id.members_row).contains("2"));
    }

    @Test
    public void loadOrganizationMemberCount_nullItemsCountsZero() throws Exception {
        Page<User> page = memberPage(null, null);
        UserFragment f = attachOrgWithMemberPage(page, "0");
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, R.id.members_row).contains("0"));
    }

    @Test
    public void loadOrganizationMemberCount_multiPageReturnsPageCount() throws Exception {
        Page<User> page = memberPage(5, Collections.singletonList(
                User.builder().login("u1").build()));
        UserFragment f = attachOrgWithMemberPage(page, "5");
        android.view.View content = (android.view.View) field(f, "mContentView");
        assertTrue(rowText(content, R.id.members_row).contains("5"));
    }

    @Test
    public void loadIsFollowingState_followableLoadsState() throws Exception {
        UserFollowerService followerService = mock(UserFollowerService.class);
        when(followerService.isFollowing(anyString()))
                .thenReturn(Single.just(Response.success(null)));
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(), null, followerService, null)) {
            UserFragment f = attachResumed(UserFragment.newInstance(richUser()));
            waitUntil(() -> {
                try {
                    return Boolean.TRUE.equals(field(f, "mIsFollowing"));
                } catch (Exception e) {
                    return false;
                }
            });
            android.view.View content = (android.view.View) field(f, "mContentView");
            assertTrue(rowText(content, R.id.followers_row).contains("4"));
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void loadIsFollowingState_errorKeepsNull() throws Exception {
        UserFollowerService followerService = mock(UserFollowerService.class);
        when(followerService.isFollowing(anyString()))
                .thenReturn(Single.error(new RuntimeException("no net")));
        Gh4Application.get().addAccount(
                User.builder().login("octocat").build(), "fake-token");
        try (MockedStatic<ServiceFactory> mocked = mockServices(
                mockRepoServiceNever(), mockOrgServiceNever(), null, followerService, null)) {
            UserFragment f = attachResumed(UserFragment.newInstance(richUser()));
            idleFor(Duration.ofSeconds(2));
            assertNull(field(f, "mIsFollowing"));
        } finally {
            Gh4Application.get().logout();
        }
    }
}
