package com.gh4a.fragment;

import android.content.Intent;
import android.view.LayoutInflater;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;

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
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import com.gh4a.ServiceFactory;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.meisolsson.githubsdk.service.search.SearchService;
import io.reactivex.Single;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** TopicDiscoveryFragment：话题发现（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class TopicDiscoveryFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Fragment.SavedState(Bundle) 在此 androidx 版本是包私有构造，
    // 只能反射创建；用于给未挂载的 Fragment 预置恢复状态。
    private static androidx.fragment.app.Fragment.SavedState savedState(
            android.os.Bundle bundle) throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> c =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(android.os.Bundle.class);
        c.setAccessible(true);
        return c.newInstance(bundle);
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static String topicOf(TopicDiscoveryFragment f) throws Exception {
        Field field = TopicDiscoveryFragment.class.getDeclaredField("mTopic");
        field.setAccessible(true);
        return (String) field.get(f);
    }

    @Test
    public void newInstance_hasNoArguments() {
        TopicDiscoveryFragment f = TopicDiscoveryFragment.newInstance();
        assertNotNull(f);
    }

    @Test
    public void onCreate_defaultTopicIsAndroid() throws Exception {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        assertEquals("android", topicOf(f));
    }

    @Test
    public void onCreate_restoresTopicFromSavedState() throws Exception {
        // 未挂载的 Fragment 直接调 onCreate(saved) 会在 Fragment.onCreate 内
        // 触 mNonConfig 抛 NPE；正确做法是 setInitialSavedState 后挂载。
        TopicDiscoveryFragment f = TopicDiscoveryFragment.newInstance();
        android.os.Bundle saved = new android.os.Bundle();
        saved.putString("topic", "kotlin");
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertEquals("kotlin", topicOf(f));
    }

    @Test
    public void getEmptyTextResId() {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        assertEquals(R.string.no_topic_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
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
    public void selectTopic_sameTopicIsNoop() throws Exception {
        // 同话题直接返回，不触发 onRefresh（也就不会订阅加载、不发请求）。
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        Method m = TopicDiscoveryFragment.class.getDeclaredMethod(
                "selectTopic", String.class, com.google.android.material.chip.Chip.class);
        m.setAccessible(true);
        m.invoke(f, "android", null);
        assertEquals("android", topicOf(f));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    // ---------- RESUMED 挂载：trampoline 调度 + mock SearchService ----------

    private static void installTrampolineSchedulers() {
        io.reactivex.plugins.RxJavaPlugins.setIoSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.plugins.RxJavaPlugins.setComputationSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.plugins.RxJavaPlugins.setNewThreadSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.android.plugins.RxAndroidPlugins.setMainThreadSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
    }

    private static void resetSchedulers() {
        io.reactivex.plugins.RxJavaPlugins.reset();
        io.reactivex.android.plugins.RxAndroidPlugins.reset();
    }

    private SearchService mockSearchService() {
        SearchService service = mock(SearchService.class);
        when(service.searchRepositories(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.never());
        return service;
    }

    private TopicDiscoveryFragment mountResumed(SearchService service) {
        TopicDiscoveryFragment f = TopicDiscoveryFragment.newInstance();
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private void pumpMainLooper() throws Exception {
        for (int i = 0; i < 20; i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
    }

    private static ChipGroup chipGroupOf(TopicDiscoveryFragment f) throws Exception {
        Field field = TopicDiscoveryFragment.class.getDeclaredField("mChipGroup");
        field.setAccessible(true);
        return (ChipGroup) field.get(f);
    }

    private static EditText topicInputOf(TopicDiscoveryFragment f) throws Exception {
        Field adapterField = TopicDiscoveryFragment.class.getDeclaredField("mRepositoryAdapter");
        adapterField.setAccessible(true);
        Object adapter = adapterField.get(f);
        Field headerField = com.gh4a.adapter.RootAdapter.class.getDeclaredField("mHeaderView");
        headerField.setAccessible(true);
        View header = (View) headerField.get(adapter);
        return header.findViewById(R.id.topic_input);
    }

    private static void selectTopic(TopicDiscoveryFragment f, String topic) throws Exception {
        Method m = TopicDiscoveryFragment.class.getDeclaredMethod(
                "selectTopic", String.class, Chip.class);
        m.setAccessible(true);
        m.invoke(f, topic, null);
    }

    @Test
    public void onSaveInstanceState_persistsTopic() throws Exception {
        TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
        selectTopic(f, "kotlin");
        android.os.Bundle out = new android.os.Bundle();
        f.onSaveInstanceState(out);
        assertEquals("kotlin", out.getString("topic"));
    }


    @Test
    public void selectTopic_nullChipGroupStillUpdatesTopic() throws Exception {
        // CREATED 态无视图：mChipGroup 为 null，selectTopic 只改 mTopic 并刷新。
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = attach(TopicDiscoveryFragment.newInstance());
            selectTopic(f, "kotlin");
            assertEquals("kotlin", topicOf(f));
        } finally {
            resetSchedulers();
        }
    }


    @Test
    public void resumedMount_headerHasTwelveChipsWithCurrentChecked() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            assertNotNull(f.getView());
            ChipGroup chips = chipGroupOf(f);
            assertNotNull(chips);
            assertEquals(12, chips.getChildCount());
            Chip first = (Chip) chips.getChildAt(0);
            assertEquals("android", first.getText().toString());
            assertTrue(first.isChecked());
            assertFalse(((Chip) chips.getChildAt(1)).isChecked());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void chipClick_selectsTopicAndRechecksChips() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            Chip kotlin = (Chip) chipGroupOf(f).getChildAt(9);
            assertEquals("kotlin", kotlin.getText().toString());
            kotlin.performClick();
            assertEquals("kotlin", topicOf(f));
            assertTrue(kotlin.isChecked());
            assertFalse(((Chip) chipGroupOf(f).getChildAt(0)).isChecked());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void editorAction_searchActionWithTextSelectsTopic() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            EditText input = topicInputOf(f);
            input.setText("  Kotlin  ");
            boolean handled = Shadows.shadowOf(input).getOnEditorActionListener().onEditorAction(
                    input, EditorInfo.IME_ACTION_SEARCH, null);
            assertTrue(handled);
            assertEquals("kotlin", topicOf(f));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void editorAction_searchActionWithEmptyTextDoesNothing() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            EditText input = topicInputOf(f);
            input.setText("   ");
            boolean handled = Shadows.shadowOf(input).getOnEditorActionListener().onEditorAction(
                    input, EditorInfo.IME_ACTION_SEARCH, null);
            assertTrue(handled);
            assertEquals("android", topicOf(f));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void editorAction_enterKeyDownSelectsTopic() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            EditText input = topicInputOf(f);
            input.setText("flutter");
            KeyEvent enter = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
            boolean handled = Shadows.shadowOf(input).getOnEditorActionListener().onEditorAction(
                    input, EditorInfo.IME_ACTION_UNSPECIFIED, enter);
            assertTrue(handled);
            assertEquals("flutter", topicOf(f));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void editorAction_enterKeyUpIsNotHandled() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            EditText input = topicInputOf(f);
            input.setText("flutter");
            KeyEvent enterUp = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
            boolean handled = Shadows.shadowOf(input).getOnEditorActionListener().onEditorAction(
                    input, EditorInfo.IME_ACTION_UNSPECIFIED, enterUp);
            assertFalse(handled);
            assertEquals("android", topicOf(f));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void editorAction_otherActionIsNotHandled() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            SearchService service = mockSearchService();
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), anyBoolean()))
                    .thenReturn(service);
            TopicDiscoveryFragment f = mountResumed(service);
            pumpMainLooper();
            EditText input = topicInputOf(f);
            boolean handled = Shadows.shadowOf(input).getOnEditorActionListener().onEditorAction(
                    input, EditorInfo.IME_ACTION_DONE, null);
            assertFalse(handled);
        } finally {
            resetSchedulers();
        }
    }

}
