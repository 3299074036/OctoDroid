package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** RepositorySearchFragment：仓库内搜索（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class RepositorySearchFragmentTest {
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

    @Test
    public void newInstance_storesUserLogin() {
        RepositorySearchFragment f = RepositorySearchFragment.newInstance("octo");
        assertEquals("octo", f.getArguments().getString("user"));
    }

    @Test
    public void setQuery_storesQueryWithoutRefreshWhenDetached() {
        RepositorySearchFragment f = RepositorySearchFragment.newInstance("octo");
        assertFalse(f.isAdded());
        f.setQuery("hello");
        assertEquals("hello", f.getArguments().getString("query"));
        f.setQuery(null);
        // Bundle.putString(key, null) 不删键，只把值置 null。
        assertNull(f.getArguments().getString("query"));
    }

    @Test
    public void getEmptyTextResId() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        assertEquals(R.string.no_search_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_emptyQueryReturnsEmptyDummyPage() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        retrofit2.Response<com.meisolsson.githubsdk.model.Page<Repository>> response =
                f.loadPage(1, false).blockingGet();
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().items().isEmpty());
    }

    @Test
    public void loadPage_nullQueryReturnsEmptyDummyPage() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        f.getArguments().putString("query", null);
        assertTrue(f.loadPage(1, false).blockingGet().body().items().isEmpty());
    }

    @Test
    public void loadPage_withQueryReturnsLazySingleWithoutSubscribing() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        f.setQuery("android");
        // 只建 Single 不订阅：Retrofit 调用在订阅前不发真实请求。
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        RepositorySearchFragment f = attach(RepositorySearchFragment.newInstance("octo"));
        Repository item = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .build();
        f.onItemClick(item);
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }
}
