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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.RootAdapter;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;
import org.mockito.MockedStatic;
import java.util.Arrays;
import io.reactivex.Single;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RepositoryListFragment：仓库列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class RepositoryListFragmentTest {
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> filterData(RepositoryListFragment f) throws Exception {
        Field field = RepositoryListFragment.class.getDeclaredField("mFilterData");
        field.setAccessible(true);
        return (Map<String, String>) field.get(f);
    }

    private static String stringField(RepositoryListFragment f, String name) throws Exception {
        Field field = RepositoryListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(f);
    }

    @Test
    public void newInstance_storesAllArguments() {
        RepositoryListFragment f = RepositoryListFragment.newInstance(
                "octo", true, "owner", "created", "desc");
        assertEquals("octo", f.getArguments().getString("user"));
        assertTrue(f.getArguments().getBoolean("is_org"));
        assertEquals("owner", f.getArguments().getString("repo_type"));
        assertEquals("created", f.getArguments().getString("sort_order"));
        assertEquals("desc", f.getArguments().getString("sort_direction"));
    }

    @Test
    public void onCreate_sourcesTypeMapsToAllFilter() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "sources", "full_name", "asc"));
        assertEquals("all", filterData(f).get("type"));
        assertEquals("sources", stringField(f, "mRepoType"));
    }

    @Test
    public void onCreate_forksTypeMapsToAllFilter() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "forks", "full_name", "asc"));
        assertEquals("all", filterData(f).get("type"));
    }

    @Test
    public void onCreate_ownerTypeKeepsFilter() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "owner", "full_name", "asc"));
        assertEquals("owner", filterData(f).get("type"));
    }

    @Test
    public void onCreate_allTypeForOrgPutsNoFilter() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "someorg", true, "all", "full_name", "asc"));
        // org + all 时不放 type/affiliation；sort/direction 仍由 onCreate 写入。
        assertFalse(filterData(f).containsKey("type"));
        assertFalse(filterData(f).containsKey("affiliation"));
        assertEquals("full_name", filterData(f).get("sort"));
        assertEquals("asc", filterData(f).get("direction"));
    }

    @Test
    public void onCreate_readsLoginAndSortArgs() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "pushed", "desc"));
        assertEquals("octo", stringField(f, "mLogin"));
        // 本类没有 mSortOrder/mSortDirection 字段，排序存在 mFilterData 里。
        assertEquals("pushed", filterData(f).get("sort"));
        assertEquals("desc", filterData(f).get("direction"));
    }

    @Test
    public void getEmptyTextResId() {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
        assertEquals(R.string.no_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onAddData_emptyListDoesNotCrash() {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
        f.onAddData(f.onCreateAdapter(), Collections.emptyList());
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
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

    @Test
    public void onCreate_selfWithAllTypePutsAffiliation() throws Exception {
        setAuthLogin("octo");
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", "full_name", "asc"));
        assertEquals("owner,collaborator", filterData(f).get("affiliation"));
        assertFalse(filterData(f).containsKey("type"));
    }

    @Test
    public void onCreate_selfWithOwnerTypeKeepsTypeFilter() throws Exception {
        setAuthLogin("octo");
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "owner", "full_name", "asc"));
        assertEquals("owner", filterData(f).get("type"));
        assertFalse(filterData(f).containsKey("affiliation"));
    }

    @Test
    public void onCreate_nullSortOrderPutsNoSortKeys() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "all", null, null));
        assertFalse(filterData(f).containsKey("sort"));
        assertFalse(filterData(f).containsKey("direction"));
    }

    @Test
    public void onAddData_sourcesTypeKeepsOnlyNonForks() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "sources", "full_name", "asc"));
        RootAdapter<Repository, ?> adapter = f.onCreateAdapter();
        Repository fork = mock(Repository.class);
        when(fork.isFork()).thenReturn(true);
        Repository source = mock(Repository.class);
        when(source.isFork()).thenReturn(false);
        f.onAddData(adapter, Arrays.asList(fork, source));
        assertEquals(1, adapter.getItemCount());
        assertEquals(source, adapter.getItem(0));
    }

    @Test
    public void onAddData_forksTypeKeepsOnlyForks() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "forks", "full_name", "asc"));
        RootAdapter<Repository, ?> adapter = f.onCreateAdapter();
        Repository fork = mock(Repository.class);
        when(fork.isFork()).thenReturn(true);
        Repository source = mock(Repository.class);
        when(source.isFork()).thenReturn(false);
        f.onAddData(adapter, Arrays.asList(fork, source));
        assertEquals(1, adapter.getItemCount());
        assertEquals(fork, adapter.getItem(0));
    }

    @Test
    public void onAddData_otherTypeAddsAll() throws Exception {
        RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                "octo", false, "owner", "full_name", "asc"));
        RootAdapter<Repository, ?> adapter = f.onCreateAdapter();
        Repository a = mock(Repository.class);
        Repository b = mock(Repository.class);
        f.onAddData(adapter, Arrays.asList(a, b));
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void loadPage_selfUsesSelfEndpoint() throws Exception {
        setAuthLogin("octo");
        RepositoryService service = mock(RepositoryService.class);
        when(service.getUserRepositories(anyMap(), eq(1L))).thenReturn(Single.never());
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryService.class), eq(false)))
                    .thenReturn(service);
            RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                    "octo", false, "all", "full_name", "asc"));
            assertNotNull(f.loadPage(1, false));
            verify(service).getUserRepositories(anyMap(), eq(1L));
            verify(service, never()).getUserRepositories(anyString(), anyMap(), eq(1L));
        }
    }

    @Test
    public void loadPage_otherUserUsesLoginEndpoint() throws Exception {
        RepositoryService service = mock(RepositoryService.class);
        when(service.getUserRepositories(anyString(), anyMap(), eq(1L)))
                .thenReturn(Single.never());
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryService.class), eq(false)))
                    .thenReturn(service);
            RepositoryListFragment f = attach(RepositoryListFragment.newInstance(
                    "octo", false, "all", "full_name", "asc"));
            assertNotNull(f.loadPage(1, false));
            verify(service).getUserRepositories(eq("octo"), anyMap(), eq(1L));
        }
    }

    private void setAuthLogin(String login) {
        // Gh4Application.getAuthLogin() 读 Gh4a-pref 的 active_login。
        android.content.Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("active_login", login).apply();
    }

}
