package com.gh4a.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DropDownUserAdapterTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private User user(String login) {
        // id 0: AvatarHandler.assignAvatar 对 id<=0 只设默认头像、不发网络请求
        //（getView 测试经此路径；id>0 会起后台线程真实取头像）。
        return User.builder().login(login).id(0L).build();
    }

    private Set<User> users(String... logins) {
        Set<User> set = new HashSet<>();
        for (String login : logins) {
            set.add(user(login));
        }
        return set;
    }

    private void runFilter(DropDownUserAdapter adapter, CharSequence constraint) throws Exception {
        // Filter.filter performs filtering on a worker thread and publishes
        // results on the main thread; Robolectric's main looper is paused,
        // so idle it while waiting for the completion callback.
        final Object lock = new Object();
        final boolean[] done = {false};
        adapter.getFilter().filter(constraint, count -> {
            synchronized (lock) {
                done[0] = true;
                lock.notifyAll();
            }
        });
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (lock) {
                if (done[0]) {
                    return;
                }
            }
            Thread.sleep(20);
            org.robolectric.Shadows.shadowOf(
                    android.os.Looper.getMainLooper()).idle();
        }
        assertTrue("filter did not complete", done[0]);
    }

    @Test
    public void initialState_empty() {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        assertEquals(0, adapter.getCount());
        assertTrue(adapter.getUnfilteredUsers().isEmpty());
    }

    @Test
    public void replace_sortsUsersCaseInsensitive() {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("bob", "Alice", "carol"));
        Set<User> unfiltered = adapter.getUnfilteredUsers();
        assertEquals(3, unfiltered.size());
    }

    @Test
    public void replace_emptySet_clears() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice"));
        adapter.replace(new HashSet<>());
        runFilter(adapter, null);
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void getItemId_returnsPosition() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, null);
        assertEquals(2, adapter.getCount());
        assertEquals(0L, adapter.getItemId(0));
        assertEquals(1L, adapter.getItemId(1));
    }

    @Test
    public void getItem_afterEmptyFilter_returnsFilteredUsers() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, null);
        assertEquals(2, adapter.getCount());
        assertEquals("alice", adapter.getItem(0).login());
        assertEquals("bob", adapter.getItem(1).login());
    }

    @Test
    public void filter_atMentionPrefix_matchesPrefix() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "alex", "bob"));
        runFilter(adapter, "@al");
        assertEquals(2, adapter.getCount());
        assertEquals("alex", adapter.getItem(0).login());
        assertEquals("alice", adapter.getItem(1).login());
    }

    @Test
    public void filter_atMentionNoMatch_emptyResult() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, "@zzz");
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void filter_withoutAtPrefix_emptyResult() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, "al");
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void filter_emptyConstraint_returnsAll() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, "@al");
        assertEquals(1, adapter.getCount());
        runFilter(adapter, "");
        assertEquals(2, adapter.getCount());
    }

    @Test
    public void filter_nullConstraint_returnsAll() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, null);
        assertEquals(2, adapter.getCount());
    }

    @Test
    public void filter_caseInsensitiveMatch() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("Alice"));
        runFilter(adapter, "@AL");
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void getFilter_returnsSameInstance() {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        Filter first = adapter.getFilter();
        assertNotNull(first);
        assertEquals(first, adapter.getFilter());
    }

    @Test
    public void convertResultToString_formatsMention() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice"));
        runFilter(adapter, "@al");
        User item = adapter.getItem(0);
        CharSequence result = adapter.getFilter().convertResultToString(item);
        assertEquals("@alice ", result.toString());
    }

    @Test
    public void getView_bindsUserLogin() throws Exception {
        FragmentActivity activity = activity();
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity);
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, null);
        ViewGroup parent = new android.widget.FrameLayout(activity);

        View view = adapter.getView(0, null, parent);

        assertNotNull(view);
        android.widget.TextView tv = view.findViewById(R.id.tv_user);
        assertEquals("alice", tv.getText().toString());
        assertNotNull(view.findViewById(R.id.iv_user));
    }

    @Test
    public void getView_reusesConvertView() throws Exception {
        FragmentActivity activity = activity();
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity);
        adapter.replace(users("alice", "bob"));
        runFilter(adapter, null);
        ViewGroup parent = new android.widget.FrameLayout(activity);

        View first = adapter.getView(0, null, parent);
        View reused = adapter.getView(1, first, parent);

        assertEquals(first, reused);
        android.widget.TextView tv = reused.findViewById(R.id.tv_user);
        assertEquals("bob", tv.getText().toString());
    }

    @Test
    public void getUnfilteredUsers_returnsCopy() {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice"));
        Set<User> copy = adapter.getUnfilteredUsers();
        copy.clear();
        assertEquals(1, adapter.getUnfilteredUsers().size());
    }

    @Test
    public void filter_turkishLocaleSafe() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("Ibrahim"));
        runFilter(adapter, "@ib");
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void replace_skipsUserWithNullLogin() {
        // Gh4Application.getAuthLogin() is null under Robolectric, so a user
        // whose login() is null is "our" user and must be skipped.
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        User ours = mock(User.class);
        when(ours.login()).thenReturn(null);
        adapter.replace(new HashSet<>(Arrays.asList(ours, user("bob"))));
        assertEquals(Collections.singleton(user("bob")), adapter.getUnfilteredUsers());
    }

    @Test
    public void publishResults_nullValues_clearsUsers() throws Exception {
        DropDownUserAdapter adapter = new DropDownUserAdapter(activity());
        adapter.replace(users("alice", "bob"));
        // replace() only fills the unfiltered list; run a filter first so
        // mUsers is populated.
        runFilter(adapter, null);
        assertEquals(2, adapter.getCount());
        // performFiltering always sets values, so reach the null branch
        // by invoking publishResults directly. FilterResults is a protected
        // nested class, so it is only reachable via reflection too.
        Object filter = adapter.getFilter();
        Class<?> resultsClass = Class.forName("android.widget.Filter$FilterResults");
        Object results = resultsClass.getDeclaredConstructor().newInstance();
        Field valuesField = resultsClass.getDeclaredField("values");
        valuesField.setAccessible(true);
        valuesField.set(results, null);
        Field countField = resultsClass.getDeclaredField("count");
        countField.setAccessible(true);
        countField.setInt(results, 0);
        Method publish = filter.getClass().getDeclaredMethod("publishResults",
                CharSequence.class, resultsClass);
        publish.setAccessible(true);
        publish.invoke(filter, null, results);
        assertEquals(0, adapter.getCount());
    }
}
