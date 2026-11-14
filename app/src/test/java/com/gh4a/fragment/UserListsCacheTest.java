package com.gh4a.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/** UserListsCache：官方列表本地缓存（Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class UserListsCacheTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static UserListsGraphQL.UserList list(String id, String name, String desc,
            UserListsGraphQL.Repo... repos) {
        return new UserListsGraphQL.UserList(id, name, desc, new ArrayList<>(Arrays.asList(repos)));
    }

    @Test
    public void load_nullGuardsReturnNull() {
        assertNull(UserListsCache.load(null, "alice"));
        assertNull(UserListsCache.load(mContext, null));
    }

    @Test
    public void load_noCacheReturnsNullNotEmpty() {
        assertNull(UserListsCache.load(mContext, "nobody-has-this-login"));
    }

    @Test
    public void save_nullGuardsAreNoops() {
        UserListsCache.save(null, "alice", Collections.emptyList());
        UserListsCache.save(mContext, null, Collections.emptyList());
        UserListsCache.save(mContext, "alice", null);
        assertNull(UserListsCache.load(mContext, "alice"));
    }

    @Test
    public void saveAndLoad_roundtripPreservesListsAndRepos() {
        List<UserListsGraphQL.UserList> lists = Arrays.asList(
                list("L1", "工具", "my tools",
                        new UserListsGraphQL.Repo("octo/cat", "nice"),
                        new UserListsGraphQL.Repo("foo/bar", "")),
                list("L2", "空组", "", new UserListsGraphQL.Repo[0]));
        UserListsCache.save(mContext, "alice", lists);
        List<UserListsGraphQL.UserList> loaded = UserListsCache.load(mContext, "alice");
        assertEquals(2, loaded.size());
        assertEquals("L1", loaded.get(0).id);
        assertEquals("工具", loaded.get(0).name);
        assertEquals("my tools", loaded.get(0).description);
        assertEquals(2, loaded.get(0).repos.size());
        assertEquals("octo/cat", loaded.get(0).repos.get(0).fullName);
        assertEquals("nice", loaded.get(0).repos.get(0).description);
        assertEquals("foo/bar", loaded.get(0).repos.get(1).fullName);
        assertEquals("", loaded.get(0).repos.get(1).description);
        assertEquals("L2", loaded.get(1).id);
        assertTrue(loaded.get(1).repos.isEmpty());
    }

    @Test
    public void save_emptyListRoundtripsAsEmptyList() {
        UserListsCache.save(mContext, "alice", Collections.emptyList());
        List<UserListsGraphQL.UserList> loaded = UserListsCache.load(mContext, "alice");
        assertTrue(loaded != null && loaded.isEmpty());
    }

    @Test
    public void save_repoWithEmptyFullNameIsDroppedOnDecode() {
        List<UserListsGraphQL.UserList> lists = Collections.singletonList(
                list("L1", "g", "",
                        new UserListsGraphQL.Repo("", "no name"),
                        new UserListsGraphQL.Repo("o/r", "kept")));
        UserListsCache.save(mContext, "alice", lists);
        List<UserListsGraphQL.UserList> loaded = UserListsCache.load(mContext, "alice");
        assertEquals(1, loaded.get(0).repos.size());
        assertEquals("o/r", loaded.get(0).repos.get(0).fullName);
    }

    @Test
    public void save_isolatedPerLogin() {
        UserListsCache.save(mContext, "alice",
                Collections.singletonList(list("L1", "g", "")));
        assertNull(UserListsCache.load(mContext, "bob"));
        assertEquals(1, UserListsCache.load(mContext, "alice").size());
    }

    @Test
    public void save_overwritesPreviousCache() {
        UserListsCache.save(mContext, "alice",
                Collections.singletonList(list("L1", "old", "")));
        UserListsCache.save(mContext, "alice",
                Collections.singletonList(list("L2", "new", "")));
        assertEquals("L2", UserListsCache.load(mContext, "alice").get(0).id);
    }

    @Test
    public void age_nullGuardsReturnMinusOne() {
        assertEquals(-1L, UserListsCache.age(null, "alice"));
        assertEquals(-1L, UserListsCache.age(mContext, null));
    }

    @Test
    public void age_noCacheReturnsMinusOne() {
        assertEquals(-1L, UserListsCache.age(mContext, "nobody-has-this-login"));
    }

    @Test
    public void age_afterSaveIsSmallNonNegative() {
        UserListsCache.save(mContext, "alice",
                Collections.singletonList(list("L1", "g", "")));
        long age = UserListsCache.age(mContext, "alice");
        assertTrue("age=" + age, age >= 0 && age < 60_000L);
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<UserListsCache> c = UserListsCache.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }


    @Test
    public void load_listWithoutReposKeyDecodesWithEmptyRepos() {
        putRawCache("alice", "[{\"id\":\"1\",\"name\":\"n\",\"description\":\"d\"}]");
        List<UserListsGraphQL.UserList> lists = UserListsCache.load(mContext, "alice");
        assertNotNull(lists);
        assertEquals(1, lists.size());
        assertTrue(lists.get(0).repos.isEmpty());
    }

    @Test
    public void load_nullRepoElementIsSkipped() {
        putRawCache("alice", "[{\"id\":\"1\",\"name\":\"n\",\"description\":\"d\","
                + "\"repos\":[null,{\"fullName\":\"o/r\",\"description\":\"\"}]}]");
        List<UserListsGraphQL.UserList> lists = UserListsCache.load(mContext, "alice");
        assertNotNull(lists);
        assertEquals(1, lists.get(0).repos.size());
        assertEquals("o/r", lists.get(0).repos.get(0).fullName);
    }

    private void putRawCache(String login, String json) {
        mContext.getSharedPreferences("user_lists_cache", Context.MODE_PRIVATE)
                .edit().putString("lists_" + login, json).apply();
    }

}
