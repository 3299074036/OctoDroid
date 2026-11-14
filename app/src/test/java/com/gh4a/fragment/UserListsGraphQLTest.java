package com.gh4a.fragment;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
