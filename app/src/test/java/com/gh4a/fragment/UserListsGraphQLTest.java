package com.gh4a.fragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/** UserListsGraphQL：官方列表 GraphQL 层（反射测私有解析 + 懒 Single 构造）。 */
@RunWith(RobolectricTestRunner.class)
public class UserListsGraphQLTest {

    private static boolean isCancelled(Exception e) throws Exception {
        Method m = UserListsGraphQL.class.getDeclaredMethod("isCancelled", Exception.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, e);
    }

    @SuppressWarnings("unchecked")
    private static void collectRepos(JSONObject items, Map<String, UserListsGraphQL.Repo> byName)
            throws Exception {
        Method m = UserListsGraphQL.class.getDeclaredMethod(
                "collectRepos", JSONObject.class, Map.class);
        m.setAccessible(true);
        m.invoke(null, items, byName);
    }

    private static Object pageInfo(JSONObject connection) throws Exception {
        Class<?> clazz = null;
        for (Class<?> c : UserListsGraphQL.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("PageInfo")) {
                clazz = c;
            }
        }
        assertNotNull(clazz);
        Constructor<?> ctor = clazz.getDeclaredConstructor(JSONObject.class);
        ctor.setAccessible(true);
        return ctor.newInstance(connection);
    }

    private static boolean pageHasNext(Object pageInfo) throws Exception {
        java.lang.reflect.Field f = pageInfo.getClass().getDeclaredField("hasNextPage");
        f.setAccessible(true);
        return f.getBoolean(pageInfo);
    }

    private static String pageEndCursor(Object pageInfo) throws Exception {
        java.lang.reflect.Field f = pageInfo.getClass().getDeclaredField("endCursor");
        f.setAccessible(true);
        return (String) f.get(pageInfo);
    }

    @Test
    public void repo_ctorKeepsFields() {
        UserListsGraphQL.Repo r = new UserListsGraphQL.Repo("o/r", "desc");
        assertEquals("o/r", r.fullName);
        assertEquals("desc", r.description);
    }

    @Test
    public void userList_ctorKeepsFields() {
        List<UserListsGraphQL.Repo> repos = new ArrayList<>(
                Arrays.asList(new UserListsGraphQL.Repo("o/r", "")));
        UserListsGraphQL.UserList l =
                new UserListsGraphQL.UserList("L1", "g", "d", repos);
        assertEquals("L1", l.id);
        assertEquals("g", l.name);
        assertEquals("d", l.description);
        assertEquals(repos, l.repos);
    }

    @Test
    public void isCancelled_interruptedIoExceptionIsTrue() throws Exception {
        assertTrue(isCancelled(new InterruptedIOException("interrupted")));
    }

    @Test
    public void isCancelled_canceledMessageIsTrue() throws Exception {
        assertTrue(isCancelled(new RuntimeException("Canceled")));
        assertTrue(isCancelled(new java.io.IOException("Canceled")));
    }

    @Test
    public void isCancelled_otherFailuresAreFalse() throws Exception {
        assertFalse(isCancelled(new RuntimeException("boom")));
        assertFalse(isCancelled(new java.io.IOException("timeout")));
        assertFalse(isCancelled(new NullPointerException()));
    }

    @Test
    public void collectRepos_nullItemsIsNoop() throws Exception {
        Map<String, UserListsGraphQL.Repo> byName = new LinkedHashMap<>();
        collectRepos(null, byName);
        collectRepos(new JSONObject(), byName);
        collectRepos(new JSONObject().put("nodes", org.json.JSONArray.class.cast(null)), byName);
        assertTrue(byName.isEmpty());
    }

    @Test
    public void collectRepos_collectsInOrderAndDedupes() throws Exception {
        JSONObject items = new JSONObject(
                "{\"nodes\":[{\"nameWithOwner\":\"a/b\",\"description\":\"1\"},"
                + "{\"nameWithOwner\":\"c/d\",\"description\":\"2\"},"
                + "{\"nameWithOwner\":\"a/b\",\"description\":\"dup\"}]}");
        Map<String, UserListsGraphQL.Repo> byName = new LinkedHashMap<>();
        collectRepos(items, byName);
        assertEquals(Arrays.asList("a/b", "c/d"), new ArrayList<>(byName.keySet()));
        assertEquals("1", byName.get("a/b").description); // 首个胜出，重复被丢弃
        assertEquals("2", byName.get("c/d").description);
    }

    @Test
    public void collectRepos_skipsEmptyNameAndNullNodes() throws Exception {
        JSONObject items = new JSONObject(
                "{\"nodes\":[null,{\"nameWithOwner\":\"\"},{\"description\":\"no-name\"},"
                + "{\"nameWithOwner\":\"o/r\"}]}");
        Map<String, UserListsGraphQL.Repo> byName = new LinkedHashMap<>();
        collectRepos(items, byName);
        assertEquals(Arrays.asList("o/r"), new ArrayList<>(byName.keySet()));
        assertEquals("", byName.get("o/r").description);
    }

    @Test
    public void pageInfo_nullConnectionMeansNoNextPage() throws Exception {
        Object pi = pageInfo(null);
        assertFalse(pageHasNext(pi));
        assertNull(pageEndCursor(pi));
    }

    @Test
    public void pageInfo_readsHasNextAndCursor() throws Exception {
        Object pi = pageInfo(new JSONObject(
                "{\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"abc\"}}"));
        assertTrue(pageHasNext(pi));
        assertEquals("abc", pageEndCursor(pi));
        Object pi2 = pageInfo(new JSONObject(
                "{\"pageInfo\":{\"hasNextPage\":false}}"));
        assertFalse(pageHasNext(pi2));
        assertNull(pageEndCursor(pi2));
        Object pi3 = pageInfo(new JSONObject("{}"));
        assertFalse(pageHasNext(pi3));
        assertNull(pageEndCursor(pi3));
    }

    @Test
    public void assignRepo_invalidFullNameEmitsError() {
        Single<Boolean> single = UserListsGraphQL.assignRepo("noslash", new ArrayList<>());
        assertNotNull(single);
        single.test()
                .assertError(RuntimeException.class)
                .assertErrorMessage("invalid repo full name");
    }

    @Test
    public void assignRepo_emptyFullNameEmitsError() {
        UserListsGraphQL.assignRepo("", new ArrayList<>()).test()
                .assertError(RuntimeException.class);
    }

    @Test
    public void assignRepo_threeSegmentNameIsLazySingleWithoutSubscribing() {
        // split("/", 2) 只切两段，"a/b/c" 是合法 owner/name；只建不订阅，
        // 订阅会走真实 GraphQL 网络，故不断言订阅结果。
        assertNotNull(UserListsGraphQL.assignRepo("a/b/c", new ArrayList<>()));
    }

    @Test
    public void fetchMethods_returnLazySinglesWithoutNetwork() {
        // 全部是 Single.fromCallable：只建不订阅，不发真实请求。
        assertNotNull(UserListsGraphQL.fetchLists("alice"));
        assertNotNull(UserListsGraphQL.fetchRepositoryNodeId("o", "r"));
        assertNotNull(UserListsGraphQL.updateListsForRepo("node1", new ArrayList<>()));
        assertNotNull(UserListsGraphQL.createList("g", true));
        assertNotNull(UserListsGraphQL.createList("g"));
        assertNotNull(UserListsGraphQL.renameList("L1", "new"));
        assertNotNull(UserListsGraphQL.deleteList("L1"));
    }

    // ---- 订阅级测试：mockStatic GraphQLClient.execute，按查询分发 canned JSON ----

    private static MockedStatic<GraphQLClient> mockGraphQL(Answer<String> answer) {
        MockedStatic<GraphQLClient> mocked = Mockito.mockStatic(GraphQLClient.class);
        mocked.when(() -> GraphQLClient.execute(any(), any(), any()))
                .thenAnswer(answer);
        return mocked;
    }

    private static String dispatch(InvocationOnMock inv,
            Map<String, String> listsPagesByAfter,
            Map<String, String> itemsPagesByAfter,
            String repoIdJson, String createJson, String renameJson) throws Exception {
        String query = inv.getArgument(0);
        JSONObject vars = inv.getArgument(1);
        if (query.contains("lists(first:")) {
            String page = listsPagesByAfter.get(vars.optString("after", null));
            if (page == null) {
                throw new RuntimeException("unexpected lists page");
            }
            return page;
        }
        if (query.contains("node(id:")) {
            String page = itemsPagesByAfter.get(vars.optString("after", null));
            if (page == null) {
                throw new RuntimeException("unexpected items page");
            }
            return page;
        }
        if (query.contains("repository(owner:")) {
            return repoIdJson;
        }
        if (query.contains("updateUserListsForItem")) {
            return "{}";
        }
        if (query.contains("createUserList")) {
            return createJson;
        }
        if (query.contains("updateUserList")) {
            return renameJson;
        }
        if (query.contains("deleteUserList")) {
            return "{}";
        }
        throw new RuntimeException("unexpected query");
    }

    private static MockedStatic<GraphQLClient> mockGraphQLSimple(String listsPageJson) {
        Map<String, String> listsPages = new HashMap<>();
        listsPages.put(null, listsPageJson);
        return mockGraphQL(inv -> dispatch(inv, listsPages, new HashMap<>(),
                "{\"data\":{\"repository\":{\"id\":\"R_1\"}}}",
                "{\"data\":{\"createUserList\":{\"list\":{\"id\":\"L9\",\"name\":\"g\","
                        + "\"description\":\"d\"}}}}",
                "{\"data\":{\"updateUserList\":{\"list\":{\"id\":\"L1\",\"name\":\"new\","
                        + "\"description\":\"\"}}}}"));
    }

    private static final String LISTS_PAGE_TWO_LISTS =
            "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
            + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"d1\",\"items\":{\"nodes\":["
            + "{\"nameWithOwner\":\"a/b\",\"description\":\"1\"},"
            + "{\"nameWithOwner\":\"c/d\",\"description\":\"2\"},"
            + "{\"nameWithOwner\":\"a/b\",\"description\":\"dup\"}],"
            + "\"pageInfo\":{\"hasNextPage\":false}}},"
            + "{\"id\":\"L2\",\"name\":\"g2\",\"description\":\"\"}"
            + "],\"pageInfo\":{\"hasNextPage\":false}}}}}";

    @Test
    public void defaultCtor_instantiates() {
        assertNotNull(new UserListsGraphQL());
    }

    @Test
    public void fetchLists_singlePage_twoLists() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(LISTS_PAGE_TWO_LISTS)) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().assertValueCount(1).values().get(0);
            assertEquals(2, lists.size());
            assertEquals("L1", lists.get(0).id);
            assertEquals("g1", lists.get(0).name);
            assertEquals("d1", lists.get(0).description);
            // 首屏 items 去重保序；L2 没有 items 字段，视为空列表。
            assertEquals(Arrays.asList("a/b", "c/d"),
                    repoNames(lists.get(0).repos));
            assertEquals("1", lists.get(0).repos.get(0).description);
            assertTrue(lists.get(1).repos.isEmpty());
        }
    }

    private static List<String> repoNames(List<UserListsGraphQL.Repo> repos) {
        List<String> names = new ArrayList<>();
        for (UserListsGraphQL.Repo r : repos) {
            names.add(r.fullName);
        }
        return names;
    }

    @Test
    public void fetchLists_listsPagination() {
        String page1 = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":[],"
                + "\"pageInfo\":{\"hasNextPage\":false}}}]"
                + ",\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"c1\"}}}}}";
        String page2 = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L2\",\"name\":\"g2\",\"description\":\"\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        Map<String, String> pages = new HashMap<>();
        pages.put(null, page1);
        pages.put("c1", page2);
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> dispatch(inv, pages, new HashMap<>(), "", "", ""))) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().assertValueCount(1).values().get(0);
            assertEquals(Arrays.asList("L1", "L2"), listIds(lists));
            // 第二页带了 after 游标。
            mocked.verify(() -> GraphQLClient.execute(any(), any(), any()),
                    Mockito.times(2));
        }
    }

    private static List<String> listIds(List<UserListsGraphQL.UserList> lists) {
        List<String> ids = new ArrayList<>();
        for (UserListsGraphQL.UserList l : lists) {
            ids.add(l.id);
        }
        return ids;
    }

    @Test
    public void fetchLists_listsPageHasNextWithoutCursor_breaks() {
        String page1 = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":true}}}}}";
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(page1)) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().assertValueCount(1).values().get(0);
            // 服务端称有下一页却没给游标：避免死循环，返回已取部分。
            assertEquals(Arrays.asList("L1"), listIds(lists));
        }
    }

    @Test
    public void fetchLists_nullDataUserLists_returnsEmpty() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple("{}")) {
            assertTrue(UserListsGraphQL.fetchLists("alice").test()
                    .assertNoErrors().values().get(0).isEmpty());
        }
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQLSimple("{\"data\":{}}")) {
            assertTrue(UserListsGraphQL.fetchLists("alice").test()
                    .assertNoErrors().values().get(0).isEmpty());
        }
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQLSimple("{\"data\":{\"user\":{}}}")) {
            assertTrue(UserListsGraphQL.fetchLists("alice").test()
                    .assertNoErrors().values().get(0).isEmpty());
        }
        // nodes 缺失：直接看 pageInfo，无下一页即结束。
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(
                "{\"data\":{\"user\":{\"lists\":{\"pageInfo\":{\"hasNextPage\":false}}}}}")) {
            assertTrue(UserListsGraphQL.fetchLists("alice").test()
                    .assertNoErrors().values().get(0).isEmpty());
        }
    }

    @Test
    public void fetchLists_skipsNullNodeAndEmptyIdOrName() {
        String page = "{\"data\":{\"user\":{\"lists\":{\"nodes\":[null,"
                + "{\"id\":\"\",\"name\":\"x\"},"
                + "{\"id\":\"L1\",\"name\":\"\"},"
                + "{\"id\":\"L2\",\"name\":\"g2\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(page)) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("L2"), listIds(lists));
        }
    }

    @Test
    public void fetchLists_itemsPagination() {
        String listsPage = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"a/b\",\"description\":\"1\"}],"
                + "\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"ic1\"}}}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        String itemsPage2 = "{\"data\":{\"node\":{\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"c/d\",\"description\":\"2\"}],"
                + "\"pageInfo\":{\"hasNextPage\":false}}}}}";
        Map<String, String> listsPages = new HashMap<>();
        listsPages.put(null, listsPage);
        Map<String, String> itemsPages = new HashMap<>();
        itemsPages.put("ic1", itemsPage2);
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(
                inv -> dispatch(inv, listsPages, itemsPages, "", "", ""))) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("a/b", "c/d"), repoNames(lists.get(0).repos));
        }
    }

    @Test
    public void fetchLists_itemsPageFailure_truncatesList() {
        String listsPage = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"a/b\",\"description\":\"1\"}],"
                + "\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"ic1\"}}},"
                + "{\"id\":\"L2\",\"name\":\"g2\",\"description\":\"\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        Map<String, String> listsPages = new HashMap<>();
        listsPages.put(null, listsPage);
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            String query = inv.getArgument(0);
            if (query.contains("node(id:")) {
                throw new RuntimeException("items page boom");
            }
            return dispatch(inv, listsPages, new HashMap<>(), "", "", "");
        })) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            // 续页失败只截断该列表，其余列表照常。
            assertEquals(Arrays.asList("a/b"), repoNames(lists.get(0).repos));
            assertEquals("L2", lists.get(1).id);
        }
    }

    @Test
    public void fetchLists_itemsPageCancelled_throws() {
        String listsPage = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"a/b\"}],"
                + "\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"ic1\"}}}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        Map<String, String> listsPages = new HashMap<>();
        listsPages.put(null, listsPage);
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            String query = inv.getArgument(0);
            if (query.contains("node(id:")) {
                throw new InterruptedIOException("canceled");
            }
            return dispatch(inv, listsPages, new HashMap<>(), "", "", "");
        })) {
            UserListsGraphQL.fetchLists("alice").test()
                    .assertError(InterruptedIOException.class);
        }
    }

    @Test
    public void fetchLists_listsPageFailureFirstPage_throws() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("lists boom");
        })) {
            UserListsGraphQL.fetchLists("alice").test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("lists boom");
        }
    }

    @Test
    public void fetchLists_listsPageFailureSecondPage_returnsPartial() {
        String page1 = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"c1\"}}}}}";
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            JSONObject vars = inv.getArgument(1);
            if (vars.optString("after", null) != null) {
                throw new RuntimeException("page2 boom");
            }
            return page1;
        })) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            // 首屏已取到：中途失败返回部分列表，不抛错。
            assertEquals(Arrays.asList("L1"), listIds(lists));
        }
    }

    @Test
    public void fetchLists_listsPageCancelledWithPartial_throws() {
        String page1 = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\"}]"
                + ",\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"c1\"}}}}}";
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            JSONObject vars = inv.getArgument(1);
            if (vars.optString("after", null) != null) {
                throw new InterruptedIOException("canceled");
            }
            return page1;
        })) {
            UserListsGraphQL.fetchLists("alice").test()
                    .assertError(InterruptedIOException.class);
        }
    }

    @Test
    public void fetchLists_itemsNextPageNull_breaks() {
        String listsPage = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"a/b\"}],"
                + "\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"ic1\"}}}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        Map<String, String> listsPages = new HashMap<>();
        listsPages.put(null, listsPage);
        Map<String, String> itemsPages = new HashMap<>();
        itemsPages.put("ic1", "{\"data\":{\"node\":null}}");
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(
                inv -> dispatch(inv, listsPages, itemsPages, "", "", ""))) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("a/b"), repoNames(lists.get(0).repos));
        }
        itemsPages.put("ic1", "{\"data\":{\"node\":{}}}");
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(
                inv -> dispatch(inv, listsPages, itemsPages, "", "", ""))) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("a/b"), repoNames(lists.get(0).repos));
        }
        // data 本身缺失：itemData 为 null，同样截断。
        itemsPages.put("ic1", "{}");
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(
                inv -> dispatch(inv, listsPages, itemsPages, "", "", ""))) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("a/b"), repoNames(lists.get(0).repos));
        }
    }

    @Test
    public void fetchLists_itemsHasNextWithoutCursor_noSecondFetch() {
        String listsPage = "{\"data\":{\"user\":{\"lists\":{\"nodes\":["
                + "{\"id\":\"L1\",\"name\":\"g1\",\"description\":\"\",\"items\":{\"nodes\":["
                + "{\"nameWithOwner\":\"a/b\"}],"
                + "\"pageInfo\":{\"hasNextPage\":true}}}]"
                + ",\"pageInfo\":{\"hasNextPage\":false}}}}}";
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(listsPage)) {
            List<UserListsGraphQL.UserList> lists =
                    UserListsGraphQL.fetchLists("alice").test()
                            .assertNoErrors().values().get(0);
            assertEquals(Arrays.asList("a/b"), repoNames(lists.get(0).repos));
            // items 有下一页但无游标：while 条件不成立，只发一次请求。
            mocked.verify(() -> GraphQLClient.execute(any(), any(), any()),
                    Mockito.times(1));
        }
    }

    @Test
    public void fetchRepositoryNodeId_returnsId() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(LISTS_PAGE_TWO_LISTS)) {
            UserListsGraphQL.fetchRepositoryNodeId("o", "r").test()
                    .assertNoErrors().assertValue("R_1");
        }
    }

    @Test
    public void fetchRepositoryNodeId_nullDataOrRepo_returnsEmpty() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> "{}")) {
            UserListsGraphQL.fetchRepositoryNodeId("o", "r").test()
                    .assertNoErrors().assertValue("");
        }
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{\"repository\":null}}")) {
            UserListsGraphQL.fetchRepositoryNodeId("o", "r").test()
                    .assertNoErrors().assertValue("");
        }
    }

    @Test
    public void fetchRepositoryNodeId_executeThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.fetchRepositoryNodeId("o", "r").test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void updateListsForRepo_returnsTrueAndSendsVars() throws Exception {
        java.util.concurrent.atomic.AtomicReference<JSONObject> capturedVars =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            capturedVars.set(inv.getArgument(1));
            return "{}";
        })) {
            UserListsGraphQL.updateListsForRepo("node1", Arrays.asList("L1", "L2")).test()
                    .assertNoErrors().assertValue(true);
            assertEquals("node1", capturedVars.get().getString("itemId"));
            JSONArray ids = capturedVars.get().getJSONArray("listIds");
            assertEquals(2, ids.length());
            assertEquals("L1", ids.getString(0));
            assertEquals("L2", ids.getString(1));
        }
    }

    @Test
    public void updateListsForRepo_emptyListIds_returnsTrue() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> "{}")) {
            UserListsGraphQL.updateListsForRepo("node1", new ArrayList<>()).test()
                    .assertNoErrors().assertValue(true);
        }
    }

    @Test
    public void updateListsForRepo_executeThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.updateListsForRepo("node1", new ArrayList<>()).test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void createList_returnsList() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(LISTS_PAGE_TWO_LISTS)) {
            UserListsGraphQL.UserList list =
                    UserListsGraphQL.createList("g", true).test()
                            .assertNoErrors().values().get(0);
            assertEquals("L9", list.id);
            assertEquals("g", list.name);
            assertEquals("d", list.description);
            assertTrue(list.repos.isEmpty());
        }
    }

    @Test
    public void createList_noList_throws() {
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{\"createUserList\":{}}}")) {
            UserListsGraphQL.createList("g", true).test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("GraphQL: createUserList returned no list");
        }
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> "{}")) {
            UserListsGraphQL.createList("g", true).test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("GraphQL: createUserList returned no list");
        }
    }

    @Test
    public void createList_executeThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.createList("g", true).test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void createList_singleArg_defaultsPublic() throws Exception {
        java.util.concurrent.atomic.AtomicReference<JSONObject> capturedVars =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            capturedVars.set(inv.getArgument(1));
            return "{\"data\":{\"createUserList\":{\"list\":{\"id\":\"L9\",\"name\":\"g\"}}}}";
        })) {
            UserListsGraphQL.createList("g").test().assertNoErrors().assertValueCount(1);
            assertFalse(capturedVars.get().getBoolean("isPrivate"));
        }
    }

    @Test
    public void renameList_returnsList() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(LISTS_PAGE_TWO_LISTS)) {
            UserListsGraphQL.UserList list =
                    UserListsGraphQL.renameList("L1", "new").test()
                            .assertNoErrors().values().get(0);
            assertEquals("L1", list.id);
            assertEquals("new", list.name);
            assertTrue(list.repos.isEmpty());
        }
    }

    @Test
    public void renameList_noList_throws() {
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{\"updateUserList\":{}}}")) {
            UserListsGraphQL.renameList("L1", "new").test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("GraphQL: updateUserList returned no list");
        }
        // data 缺失与 payload 缺失都走同一抛错。
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> "{}")) {
            UserListsGraphQL.renameList("L1", "new").test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("GraphQL: updateUserList returned no list");
        }
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{}}")) {
            UserListsGraphQL.renameList("L1", "new").test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("GraphQL: updateUserList returned no list");
        }
    }

    @Test
    public void renameList_executeThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.renameList("L1", "new").test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void deleteList_returnsTrue() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQLSimple(LISTS_PAGE_TWO_LISTS)) {
            UserListsGraphQL.deleteList("L1").test()
                    .assertNoErrors().assertValue(true);
        }
    }

    @Test
    public void deleteList_executeThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.deleteList("L1").test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void assignRepo_success() throws Exception {
        java.util.concurrent.atomic.AtomicReference<JSONObject> capturedVars =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            String query = inv.getArgument(0);
            if (query.contains("updateUserListsForItem")) {
                capturedVars.set(inv.getArgument(1));
                return "{}";
            }
            return "{\"data\":{\"repository\":{\"id\":\"R_1\"}}}";
        })) {
            UserListsGraphQL.assignRepo("o/r", Arrays.asList("L1")).test()
                    .assertNoErrors().assertValue(true);
            assertEquals("R_1", capturedVars.get().getString("itemId"));
        }
    }

    @Test
    public void assignRepo_emptyNodeId_errors() {
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{\"repository\":{\"id\":\"\"}}}")) {
            UserListsGraphQL.assignRepo("o/r", new ArrayList<>()).test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("empty node id");
        }
        try (MockedStatic<GraphQLClient> mocked =
                     mockGraphQL(inv -> "{\"data\":{\"repository\":null}}")) {
            UserListsGraphQL.assignRepo("o/r", new ArrayList<>()).test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("empty node id");
        }
    }

    @Test
    public void assignRepo_nodeIdFetchThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            throw new RuntimeException("boom");
        })) {
            UserListsGraphQL.assignRepo("o/r", new ArrayList<>()).test()
                    .assertError(RuntimeException.class);
        }
    }

    @Test
    public void assignRepo_updateThrows_errors() {
        try (MockedStatic<GraphQLClient> mocked = mockGraphQL(inv -> {
            String query = inv.getArgument(0);
            if (query.contains("updateUserListsForItem")) {
                throw new RuntimeException("mut boom");
            }
            return "{\"data\":{\"repository\":{\"id\":\"R_1\"}}}";
        })) {
            UserListsGraphQL.assignRepo("o/r", new ArrayList<>()).test()
                    .assertError(RuntimeException.class)
                    .assertErrorMessage("mut boom");
        }
    }
}
