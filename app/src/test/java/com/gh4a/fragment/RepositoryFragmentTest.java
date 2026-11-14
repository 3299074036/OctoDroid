package com.gh4a.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
                .build();
    }

    private static Object field(RepositoryFragment f, String name) throws Exception {
        Field field = RepositoryFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
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
}
