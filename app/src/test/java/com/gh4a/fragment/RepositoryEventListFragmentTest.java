package com.gh4a.fragment;

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/** RepositoryEventListFragment：仓库动态列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class RepositoryEventListFragmentTest {
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

    @Test
    public void newInstance_storesRepositoryParcelable() {
        Repository repository = repo();
        RepositoryEventListFragment f = RepositoryEventListFragment.newInstance(repository);
        assertSame(repository, f.getArguments().getParcelable("repository"));
    }

    @Test
    public void onCreate_readsRepositoryFromArguments() throws Exception {
        Repository repository = repo();
        RepositoryEventListFragment f = attach(RepositoryEventListFragment.newInstance(repository));
        Field field = RepositoryEventListFragment.class.getDeclaredField("mRepository");
        field.setAccessible(true);
        Repository stored = (Repository) field.get(f);
        assertNotNull(stored);
        assertEquals("octo/cat", stored.fullName());
    }

    @Test
    public void loadRawPage_returnsLazySingleWithoutSubscribing() {
        RepositoryEventListFragment f = attach(RepositoryEventListFragment.newInstance(repo()));
        // 只建 Single 不订阅：Retrofit 调用在订阅前不发真实请求。
        assertNotNull(f.loadRawPage(1, false));
        assertNotNull(f.loadRawPage(3, true));
    }
}
