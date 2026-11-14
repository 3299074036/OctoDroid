package com.gh4a.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
}
