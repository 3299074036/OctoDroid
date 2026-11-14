package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.AdapterView;

import androidx.appcompat.view.menu.MenuBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.PrivateEventListFragment;
import com.meisolsson.githubsdk.model.User;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class NewsFeedFactoryTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
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

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            assertEquals(R.string.user_news_feed, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.user_news_feed, tabs[0]);
        }
    }

    @Test
    public void makeFragment_withoutOrg_passesNullOrg() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            PrivateEventListFragment f =
                    (PrivateEventListFragment) factory.makeFragment(0);
            assertEquals("octocat", f.getArguments().getString("login"));
            assertEquals(null, f.getArguments().getString("org"));
        }
    }

    @Test
    public void makeFragment_withSelectedOrg_passesOrgLogin() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User org = User.builder().login("someorg").build();
            setField(factory, "mSelectedOrganization", org);
            PrivateEventListFragment f =
                    (PrivateEventListFragment) factory.makeFragment(0);
            assertEquals("someorg", f.getArguments().getString("org"));
        }
    }

    @Test
    public void onCreateOptionsMenu_withoutScopes_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            MenuBuilder menu = new MenuBuilder(activity);
            assertFalse(factory.onCreateOptionsMenu(menu));
        }
    }

    @Test
    public void onCreateOptionsMenu_withScopes_inflatesSelectorAndReturnsTrue()
            throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User self = User.builder().login("octocat").build();
            List<User> scopes = Arrays.asList(
                    User.builder().login("org1").build(),
                    User.builder().login("org2").build());
            setField(factory, "mSelf", self);
            setField(factory, "mUserScopes", scopes);
            MenuBuilder menu = new MenuBuilder(activity);
            assertTrue(factory.onCreateOptionsMenu(menu));
            assertTrue(menu.findItem(R.id.selector) != null);
        }
    }

    @Test
    public void onItemSelected_positionZero_clearsOrg() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User org = User.builder().login("org1").build();
            setField(factory, "mSelectedOrganization", org);
            setField(factory, "mUserScopes",
                    Arrays.asList(User.builder().login("org1").build()));
            factory.onItemSelected(null, null, 0, 0);
            assertEquals(null, getField(factory, "mSelectedOrganization"));
        }
    }

    @Test
    public void onItemSelected_selectOrg_setsOrg() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User org1 = User.builder().login("org1").build();
            User org2 = User.builder().login("org2").build();
            setField(factory, "mUserScopes", Arrays.asList(org1, org2));
            factory.onItemSelected(null, null, 2, 0);
            User selected = getField(factory, "mSelectedOrganization");
            assertEquals("org2", selected.login());
        }
    }

    @Test
    public void onItemSelected_sameOrg_doesNotChange() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User org1 = User.builder().login("org1").build();
            setField(factory, "mUserScopes", Arrays.asList(org1));
            setField(factory, "mSelectedOrganization", org1);
            // 同一个组织（equals 比较）再次选中：不重建页面
            factory.onItemSelected(null, null, 1, 0);
            assertEquals("org1",
                    ((User) getField(factory, "mSelectedOrganization")).login());
        }
    }

    @Test
    public void onNothingSelected_withOrg_clearsOrg() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            setField(factory, "mSelectedOrganization",
                    User.builder().login("org1").build());
            factory.onNothingSelected(null);
            assertEquals(null, getField(factory, "mSelectedOrganization"));
        }
    }

    @Test
    public void onNothingSelected_withoutOrg_isNoOp() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            factory.onNothingSelected(null);
            assertEquals(null, getField(factory, "mSelectedOrganization"));
        }
    }

    @Test
    public void setUserInfo_storesSelf() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            User self = User.builder().login("octocat").build();
            factory.setUserInfo(self);
            assertEquals("octocat", ((User) getField(factory, "mSelf")).login());
        }
    }

    @Test
    public void onDestroy_withoutSubscription_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            factory.onDestroy();
        }
    }

    @Test
    public void userAdapter_countAndItems() throws Exception {
        // 私有静态 UserAdapter：count = 组织数 + 1（第 0 位是本人），
        // getItem(0) 本人、其余按序取组织；getItemId 恒为 0。
        Class<?> adapterClass = null;
        for (Class<?> inner : NewsFeedFactory.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("UserAdapter")) {
                adapterClass = inner;
            }
        }
        assertTrue(adapterClass != null);
        User self = User.builder().login("octocat").build();
        List<User> users = Arrays.asList(User.builder().login("org1").build(),
                User.builder().login("org2").build());
        java.lang.reflect.Constructor<?> ctor = adapterClass.getDeclaredConstructor(
                android.content.Context.class, User.class, List.class);
        ctor.setAccessible(true);
        android.widget.BaseAdapter adapter = (android.widget.BaseAdapter) ctor.newInstance(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                self, users);
        assertEquals(3, adapter.getCount());
        assertEquals("octocat", ((User) adapter.getItem(0)).login());
        assertEquals("org1", ((User) adapter.getItem(1)).login());
        assertEquals("org2", ((User) adapter.getItem(2)).login());
        assertEquals(0L, adapter.getItemId(2));
    }

    @Test
    public void onRefresh_loadsOrganizationsWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            factory.onRefresh();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNotNull(factory);
        }
    }

    @Test
    public void onStartLoadingData_loadsOrganizationsWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            factory.onStartLoadingData();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNotNull(factory);
        }
    }

    @Test
    public void userAdapter_getView_returnsAvatarView() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        User self = User.builder().login("octocat").build();
        List<User> users = Arrays.asList(User.builder().login("octocat").build());
        Class<?> cls = Class.forName("com.gh4a.activities.home.NewsFeedFactory$UserAdapter");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(
                Context.class, User.class, List.class);
        ctor.setAccessible(true);
        Object adapter = ctor.newInstance(context, self, users);
        android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
        android.view.View view = (android.view.View) cls.getMethod("getView", int.class,
                android.view.View.class, android.view.ViewGroup.class)
                .invoke(adapter, 0, null, parent);
        assertNotNull(view);
        assertTrue(view instanceof android.widget.ImageView);
    }

    @Test
    public void userAdapter_getDropDownView_setsLogin() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        User self = User.builder().login("octocat").build();
        List<User> users = Arrays.asList(User.builder().login("octocat").build());
        Class<?> cls = Class.forName("com.gh4a.activities.home.NewsFeedFactory$UserAdapter");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(
                Context.class, User.class, List.class);
        ctor.setAccessible(true);
        Object adapter = ctor.newInstance(context, self, users);
        android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        android.widget.ImageView avatar = new android.widget.ImageView(context);
        avatar.setId(R.id.iv_gravatar);
        android.widget.TextView nameView = new android.widget.TextView(context);
        nameView.setId(R.id.tv_title);
        row.addView(avatar);
        row.addView(nameView);
        android.view.View view = (android.view.View) cls.getMethod("getDropDownView", int.class,
                android.view.View.class, android.view.ViewGroup.class)
                .invoke(adapter, 0, row, parent);
        assertNotNull(view);
        assertEquals("octocat", nameView.getText().toString());
    }

    // ---- A2 第二轮补缺口：NewsFeedFactory ----

    @Test
    public void onItemSelected_differentOrganization_switchesSelection() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            com.meisolsson.githubsdk.model.User org =
                    com.meisolsson.githubsdk.model.User.builder().login("myorg").build();
            setField(factory, "mUserScopes",
                    java.util.Collections.singletonList(org));
            setField(factory, "mSelectedOrganization", null);
            factory.onItemSelected(null, null, 1, 1);
            assertEquals(org, getField(factory, "mSelectedOrganization"));
        }
    }

    @Test
    public void onItemSelected_sameOrganization_keepsSelection() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NewsFeedFactory factory = new NewsFeedFactory(activity, "octocat");
            com.meisolsson.githubsdk.model.User org =
                    com.meisolsson.githubsdk.model.User.builder().login("myorg").build();
            setField(factory, "mUserScopes",
                    java.util.Collections.singletonList(org));
            setField(factory, "mSelectedOrganization", org);
            factory.onItemSelected(null, null, 1, 1);
            assertEquals(org, getField(factory, "mSelectedOrganization"));
        }
    }

}
