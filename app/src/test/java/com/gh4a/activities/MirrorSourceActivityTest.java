package com.gh4a.activities;

import android.os.Bundle;
import android.view.View;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.MirrorHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MirrorSourceActivityTest {
    /** Launch through the saved-state path so no live speed test runs. */
    private MirrorSourceActivity launchRestored(boolean testDone, boolean batchMode) {
        Bundle state = new Bundle();
        state.putBoolean("mirror_batch_mode", batchMode);
        state.putStringArrayList("mirror_checked", new ArrayList<>());
        state.putBoolean("mirror_editing_is_new", true);
        state.putBoolean("mirror_test_done", testDone);
        state.putStringArrayList("mirror_done_urls", new ArrayList<>());
        state.putStringArrayList("mirror_item_urls", new ArrayList<>());
        state.putLongArray("mirror_item_latencies", new long[0]);
        state.putBoolean("mirror_add_card_open", false);
        state.putString("mirror_add_card_text", "");
        ActivityController<MirrorSourceActivity> c = controller(MirrorSourceActivity.class, null);
        return c.create(state).start().resume().get();
    }

    /** Launch with the decor view attached to a window (.visible()).
     * Without it the content view has no AttachInfo, so View.post() defers
     * runnables forever and requestFocus() is a no-op. Only the focus/keyboard
     * tests use this path; findViewById(R.id.url) is unreliable here once the
     * ListView lays out rows (the row's url TextView shadows the input), so
     * these tests read the input via the mUrlInput field instead. */
    private MirrorSourceActivity launchVisibleRestored() {
        Bundle state = new Bundle();
        state.putBoolean("mirror_batch_mode", false);
        state.putStringArrayList("mirror_checked", new ArrayList<>());
        state.putBoolean("mirror_editing_is_new", true);
        state.putBoolean("mirror_test_done", true);
        state.putStringArrayList("mirror_done_urls", new ArrayList<>());
        state.putStringArrayList("mirror_item_urls", new ArrayList<>());
        state.putLongArray("mirror_item_latencies", new long[0]);
        state.putBoolean("mirror_add_card_open", false);
        state.putString("mirror_add_card_text", "");
        ActivityController<MirrorSourceActivity> c = controller(MirrorSourceActivity.class, null);
        return c.create(state).start().resume().visible().get();
    }

    /** Simulate the window having focus: Robolectric never grants it, so a
     * posted requestFocus() is deferred (mViewRequestingFocus) instead. */
    private void grantWindowFocus(MirrorSourceActivity activity) {
        Object attachInfo = get((android.widget.EditText) get(activity, "mUrlInput"),
                "mAttachInfo");
        set(attachInfo, "mHasWindowFocus", true);
    }

    private boolean softInputVisible(MirrorSourceActivity activity) {
        return org.robolectric.Shadows.shadowOf((android.view.inputmethod.InputMethodManager)
                activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .isSoftInputVisible();
    }

    private android.content.SharedPreferences prefs() {
        return appContext().getSharedPreferences(SettingsFragment.PREF_NAME,
                android.content.Context.MODE_PRIVATE);
    }

    private MirrorSourceActivity launchWithState(Bundle state) {
        ActivityController<MirrorSourceActivity> c = controller(MirrorSourceActivity.class, null);
        return c.create(state).start().resume().get();
    }

    /** Saved-state bundle; only the given keys are present. */
    private Bundle state(boolean testDone, boolean batchMode, boolean editingIsNew,
            boolean addCardOpen, String addCardText,
            List<String> itemUrls, long[] latencies) {
        Bundle b = new Bundle();
        b.putBoolean("mirror_batch_mode", batchMode);
        b.putBoolean("mirror_editing_is_new", editingIsNew);
        b.putBoolean("mirror_test_done", testDone);
        b.putBoolean("mirror_add_card_open", addCardOpen);
        b.putString("mirror_add_card_text", addCardText);
        if (itemUrls != null) {
            b.putStringArrayList("mirror_item_urls", new ArrayList<>(itemUrls));
        }
        if (latencies != null) {
            b.putLongArray("mirror_item_latencies", latencies);
        }
        return b;
    }

    /** Mocks MirrorHelper.testAllMirrorsSpeed; other MirrorHelper calls run for real. */
    private static class SpeedTestMocks implements AutoCloseable {
        final MockedStatic<MirrorHelper> helper;
        final ArgumentCaptor<MirrorHelper.SpeedTestCallback> callbackCaptor;
        final MirrorHelper.SpeedTestHandle handle;

        SpeedTestMocks() {
            helper = Mockito.mockStatic(MirrorHelper.class,
                    Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
            handle = mock(MirrorHelper.SpeedTestHandle.class);
            callbackCaptor = ArgumentCaptor.forClass(MirrorHelper.SpeedTestCallback.class);
            helper.when(() -> MirrorHelper.testAllMirrorsSpeed(any(), anyList(),
                    callbackCaptor.capture())).thenReturn(handle);
        }

        MirrorHelper.SpeedTestCallback callback() {
            return callbackCaptor.getValue();
        }

        @Override
        public void close() {
            helper.close();
        }
    }

    private MirrorHelper.MirrorSpeedResult result(String name, String url, long latency) {
        return new MirrorHelper.MirrorSpeedResult(name, url, latency);
    }

    private List<MirrorHelper.MirrorSpeedResult> itemsOf(MirrorSourceActivity activity) {
        return (List<MirrorHelper.MirrorSpeedResult>) get(activity, "mItems");
    }

    private MirrorHelper.MirrorSpeedResult findItem(MirrorSourceActivity activity, String url) {
        for (MirrorHelper.MirrorSpeedResult r : itemsOf(activity)) {
            if (r.url.equals(url)) {
                return r;
            }
        }
        return null;
    }

    private void setItems(MirrorSourceActivity activity, MirrorHelper.MirrorSpeedResult... results) {
        List<MirrorHelper.MirrorSpeedResult> items =
                (List<MirrorHelper.MirrorSpeedResult>) get(activity, "mItems");
        items.clear();
        for (MirrorHelper.MirrorSpeedResult r : results) {
            items.add(r);
        }
        ((android.widget.BaseAdapter) get(activity, "mAdapter")).notifyDataSetChanged();
    }

    @Test
    public void launch_restoredState_doesNotStartSpeedTest() {
        MirrorSourceActivity activity = launchRestored(true, false);
        assertEquals(activity.getString(R.string.mirror_preset),
                activity.getSupportActionBar().getTitle().toString());
        assertFalse(activity.canSwipeToRefresh());
        assertTrue((boolean) get(activity, "mTestDone"));
        assertNull(get(activity, "mSpeedTestHandle"));
    }

    @Test
    public void launch_restoredBatchMode_showsBatchBar() {
        MirrorSourceActivity activity = launchRestored(true, true);
        assertTrue((boolean) get(activity, "mBatchMode"));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.batch_bar).getVisibility());
    }

    @Test
    public void sortByLatency_okFirstThenByLatencyFailedLast() {
        MirrorHelper.MirrorSpeedResult slow =
                new MirrorHelper.MirrorSpeedResult("slow", "https://slow/", 900);
        MirrorHelper.MirrorSpeedResult fast =
                new MirrorHelper.MirrorSpeedResult("fast", "https://fast/", 50);
        MirrorHelper.MirrorSpeedResult failed =
                new MirrorHelper.MirrorSpeedResult("bad", "https://bad/", -1);
        List<MirrorHelper.MirrorSpeedResult> items = new ArrayList<>();
        items.add(slow);
        items.add(failed);
        items.add(fast);
        callStatic(MirrorSourceActivity.class, "sortByLatency",
                new Class<?>[]{List.class}, new Object[]{items});
        assertEquals("https://fast/", items.get(0).url);
        assertEquals("https://slow/", items.get(1).url);
        assertEquals("https://bad/", items.get(2).url);
    }

    @Test
    public void fastestOkUrl_picksLowestLatencySkippingExcludedAndFailed() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity,
                new MirrorHelper.MirrorSpeedResult("a", "https://a/", 300),
                new MirrorHelper.MirrorSpeedResult("b", "https://b/", 100),
                new MirrorHelper.MirrorSpeedResult("c", "https://c/", -1));
        assertEquals("https://b/", call(activity, "fastestOkUrl",
                new Class<?>[]{Set.class}, new Object[]{null}));
        Set<String> exclude = new HashSet<>();
        exclude.add("https://B/");
        assertEquals("https://a/", call(activity, "fastestOkUrl",
                new Class<?>[]{Set.class}, new Object[]{exclude}));
    }

    @Test
    public void fastestOkUrl_noOkResult_returnsNull() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, new MirrorHelper.MirrorSpeedResult("c", "https://c/", -1));
        assertNull(call(activity, "fastestOkUrl",
                new Class<?>[]{Set.class}, new Object[]{null}));
        setItems(activity);
        assertNull(call(activity, "fastestOkUrl",
                new Class<?>[]{Set.class}, new Object[]{null}));
    }

    @Test
    public void containsUrl_caseInsensitiveAndNull() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, new MirrorHelper.MirrorSpeedResult("a", "https://Example.COM/", 10));
        assertTrue((boolean) call(activity, "containsUrl",
                new Class<?>[]{String.class}, new Object[]{"https://example.com/"}));
        assertFalse((boolean) call(activity, "containsUrl",
                new Class<?>[]{String.class}, new Object[]{"https://other/"}));
        assertFalse((boolean) call(activity, "containsUrl",
                new Class<?>[]{String.class}, new Object[]{null}));
    }

    @Test
    public void toggleChecked_addsAndRemoves() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "toggleChecked", new Class<?>[]{String.class}, new Object[]{"https://a/"});
        assertTrue(((Set<?>) get(activity, "mChecked")).contains("https://a/"));
        call(activity, "toggleChecked", new Class<?>[]{String.class}, new Object[]{"https://a/"});
        assertFalse(((Set<?>) get(activity, "mChecked")).contains("https://a/"));
    }

    @Test
    public void enterAndExitBatchMode_toggleBarAndFlag() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        assertTrue((boolean) get(activity, "mBatchMode"));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.batch_bar).getVisibility());
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        assertTrue((boolean) get(activity, "mBatchMode"));
        call(activity, "exitBatchMode", new Class<?>[]{}, new Object[]{});
        assertFalse((boolean) get(activity, "mBatchMode"));
        assertEquals(View.GONE, activity.findViewById(R.id.batch_bar).getVisibility());
        assertTrue(((Set<?>) get(activity, "mChecked")).isEmpty());
        call(activity, "exitBatchMode", new Class<?>[]{}, new Object[]{});
        assertFalse((boolean) get(activity, "mBatchMode"));
    }

    @Test
    public void onBackPressed_batchMode_exitsBatchInsteadOfFinishing() {
        MirrorSourceActivity activity = launchRestored(true, true);
        activity.onBackPressed();
        assertFalse((boolean) get(activity, "mBatchMode"));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onBackPressed_normalMode_finishes() {
        MirrorSourceActivity activity = launchRestored(true, false);
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onCreateOptionsMenu_batchItemLabelFollowsMode() {
        MirrorSourceActivity activity = launchRestored(true, false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(activity);
        activity.onCreateOptionsMenu(menu);
        assertEquals(activity.getString(R.string.mirror_batch_delete),
                menu.findItem(1).getTitle().toString());
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        androidx.appcompat.view.menu.MenuBuilder menu2 =
                new androidx.appcompat.view.menu.MenuBuilder(activity);
        activity.onCreateOptionsMenu(menu2);
        assertEquals(activity.getString(android.R.string.cancel),
                menu2.findItem(1).getTitle().toString());
    }

    @Test
    public void onSaveInstanceState_roundTripsBatchAndTestState() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mBatchMode", true);
        ((Set<String>) get(activity, "mChecked")).add("https://a/");
        set(activity, "mTestDone", true);
        ((Set<String>) get(activity, "mDoneUrls")).add("https://a/");
        setItems(activity, new MirrorHelper.MirrorSpeedResult("a", "https://a/", 123));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertTrue(out.getBoolean("mirror_batch_mode"));
        assertEquals(java.util.Arrays.asList("https://a/"), out.getStringArrayList("mirror_checked"));
        assertTrue(out.getBoolean("mirror_test_done"));
        assertEquals(java.util.Arrays.asList("https://a/"), out.getStringArrayList("mirror_done_urls"));
        assertEquals(java.util.Arrays.asList("https://a/"), out.getStringArrayList("mirror_item_urls"));
        assertEquals(123L, out.getLongArray("mirror_item_latencies")[0]);
    }

    @Test
    public void updateStatsBar_countsOkAndTimeout() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity,
                new MirrorHelper.MirrorSpeedResult("a", "https://a/", 10),
                new MirrorHelper.MirrorSpeedResult("b", "https://b/", -1),
                new MirrorHelper.MirrorSpeedResult("c", "https://c/", 20));
        call(activity, "updateStatsBar", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.stats_total))
                .getText().toString().contains("3"));
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.stats_ok))
                .getText().toString().contains("2"));
    }

    // ---------- Batch A: onCreate / restoreFromSavedState ----------

    @Test
    public void onCreate_freshLaunch_startsSpeedTest() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            assertFalse((boolean) get(activity, "mTestDone"));
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
            assertFalse(itemsOf(activity).isEmpty());
            assertEquals(activity.getString(R.string.mirror_manage_testing),
                    ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                            .getText().toString());
        }
    }

    @Test
    public void onCreate_autoSelectFalse_setsUserPicked() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, false).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        assertTrue((boolean) get(activity, "mUserPicked"));
        assertFalse(((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).isChecked());
    }

    @Test
    public void onCreate_autoSelectTrue_clearsUserPicked() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        assertFalse((boolean) get(activity, "mUserPicked"));
        assertTrue(((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).isChecked());
    }

    @Test
    public void switchListener_ignoreEvent_doesNothing() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mIgnoreSwitchEvent", true);
        String before = (String) get(activity, "mCurrentValue");
        ((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).setChecked(false);
        // The listener returned early: setAuto never ran, so the picked flag
        // and the current value are unchanged (subtitle stays "" as at launch).
        assertFalse((boolean) get(activity, "mUserPicked"));
        assertEquals(before, get(activity, "mCurrentValue"));
        assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                .getText().toString());
    }

    @Test
    public void switchListener_toggleOn_autoSelectsFastest() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, false).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 300), result("b", "https://b/", 100));
        ((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).setChecked(true);
        assertEquals("https://b/", get(activity, "mCurrentValue"));
        assertFalse((boolean) get(activity, "mUserPicked"));
        assertEquals(activity.getString(R.string.mirror_manage_auto_selected),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void switchListener_toggleOn_noFastest_clearsSubtitle() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, false).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("c", "https://c/", -1));
        ((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).setChecked(true);
        assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                .getText().toString());
    }

    @Test
    public void switchListener_toggleOff_setsManualSubtitle() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        ((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).setChecked(false);
        assertTrue((boolean) get(activity, "mUserPicked"));
        assertEquals(activity.getString(R.string.mirror_manual_subtitle),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void listClick_addCardOpen_ignored() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        String before = (String) get(activity, "mCurrentValue");
        android.widget.ListView list = activity.findViewById(R.id.list);
        list.performItemClick(list.getAdapter().getView(0, null, list), 0,
                list.getAdapter().getItemId(0));
        assertEquals(before, get(activity, "mCurrentValue"));
        assertTrue(((Set<?>) get(activity, "mChecked")).isEmpty());
    }

    @Test
    public void listClick_batchMode_togglesChecked() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        android.widget.ListView list = activity.findViewById(R.id.list);
        list.performItemClick(list.getAdapter().getView(0, null, list), 0,
                list.getAdapter().getItemId(0));
        assertTrue(((Set<?>) get(activity, "mChecked")).contains("https://a/"));
    }

    @Test
    public void listClick_normalMode_picks() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 50));
        android.widget.ListView list = activity.findViewById(R.id.list);
        list.performItemClick(list.getAdapter().getView(1, null, list), 1,
                list.getAdapter().getItemId(1));
        assertEquals("https://b/", get(activity, "mCurrentValue"));
        assertTrue((boolean) get(activity, "mUserPicked"));
    }

    @Test
    public void editorAction_done_savesCard() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
        android.widget.EditText input = activity.findViewById(R.id.url);
        input.setText("https://new-mirror.example.com/");
        android.widget.TextView.OnEditorActionListener listener =
                org.robolectric.Shadows.shadowOf(input).getOnEditorActionListener();
        assertTrue(listener.onEditorAction(input,
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE, null));
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        // normalizeUrl strips the trailing slash before saving.
        assertTrue(MirrorHelper.getMirrorUrls(appContext())
                .contains("https://new-mirror.example.com"));
    }

    @Test
    public void editorAction_other_returnsFalse() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
        android.widget.EditText input = activity.findViewById(R.id.url);
        android.widget.TextView.OnEditorActionListener listener =
                org.robolectric.Shadows.shadowOf(input).getOnEditorActionListener();
        assertFalse(listener.onEditorAction(input,
                android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH, null));
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
    }

    @Test
    public void restore_missingCheckedAndDoneKeys_toleratesNull() {
        Bundle b = state(true, false, true, false, "", null, null);
        MirrorSourceActivity activity = launchWithState(b);
        assertTrue(((Set<?>) get(activity, "mChecked")).isEmpty());
        assertTrue(((Set<?>) get(activity, "mDoneUrls")).isEmpty());
    }

    @Test
    public void restore_urlsWithoutLatencies_usesTargetLatency() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        assertFalse(targets.isEmpty());
        String url = targets.get(0).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Collections.singletonList(url), null);
        MirrorSourceActivity activity = launchWithState(b);
        MirrorHelper.MirrorSpeedResult item = findItem(activity, url);
        assertNotNull(item);
        // No saved latency and no cache: the target itself (latency -1) is used.
        assertEquals(-1L, item.latencyMs);
    }

    @Test
    public void restore_savedLatencyUsedAndSorted() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        assertTrue(targets.size() >= 2);
        String slowUrl = targets.get(0).url;
        String fastUrl = targets.get(1).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Arrays.asList(slowUrl, fastUrl), new long[]{900, 50});
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(50L, findItem(activity, fastUrl).latencyMs);
        assertEquals(900L, findItem(activity, slowUrl).latencyMs);
        // Sorted by latency: the fast item comes first.
        assertEquals(fastUrl, itemsOf(activity).get(0).url);
    }

    @Test
    public void restore_latencyFromCache() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        assertFalse(targets.isEmpty());
        String url = targets.get(0).url;
        MirrorHelper.saveSpeedTestResults(appContext(),
                java.util.Collections.singletonList(result("x", url, 42)));
        Bundle b = state(true, false, true, false, "", null, null);
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(42L, findItem(activity, url).latencyMs);
    }

    @Test
    public void restore_emptyTargets_showsEmptyHint() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST, "[]").apply();
        Bundle b = state(true, false, true, false, "", null, null);
        MirrorSourceActivity activity = launchWithState(b);
        assertTrue(itemsOf(activity).isEmpty());
        assertEquals(android.view.View.GONE, activity.findViewById(R.id.list).getVisibility());
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.empty_hint).getVisibility());
    }

    @Test
    public void restore_testDoneFalse_startsSpeedTest() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            Bundle b = state(false, false, true, false, "", null, null);
            MirrorSourceActivity activity = launchWithState(b);
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
            assertFalse((boolean) get(activity, "mTestDone"));
            assertEquals(activity.getString(R.string.mirror_manage_testing),
                    ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                            .getText().toString());
        }
    }

    @Test
    public void restore_testDone_subtitleAutoWhenNotPickedAndOk() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        String url = targets.get(0).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Collections.singletonList(url), new long[]{100});
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(activity.getString(R.string.mirror_manage_auto_selected),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void restore_testDone_subtitleManualWhenPicked() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, false).apply();
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        String url = targets.get(0).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Collections.singletonList(url), new long[]{100});
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(activity.getString(R.string.mirror_manual_subtitle),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void restore_testDone_noOkAndAuto_clearsSubtitle() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        String url = targets.get(0).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Collections.singletonList(url), new long[]{-1});
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                .getText().toString());
    }

    @Test
    public void restore_addCardOpenNew_restoresText() {
        Bundle b = state(true, false, true, true, "https://typed.example.com/", null, null);
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertEquals(activity.getString(R.string.mirror_add),
                ((android.widget.TextView) activity.findViewById(R.id.add_title))
                        .getText().toString());
        assertEquals("https://typed.example.com/",
                ((android.widget.EditText) activity.findViewById(R.id.url))
                        .getText().toString());
    }

    @Test
    public void restore_addCardOpenEdit_restoresText() {
        Bundle b = state(true, false, false, true, "https://old.example.com/", null, null);
        // The editing-old-url key is not set by state(); set it via a second bundle entry.
        b.putString("mirror_editing_old_url", "https://old.example.com/");
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertEquals(activity.getString(R.string.me_mirror_edit),
                ((android.widget.TextView) activity.findViewById(R.id.add_title))
                        .getText().toString());
    }

    @Test
    public void onSaveInstanceState_nullUrlInput_storesEmptyText() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mUrlInput", null);
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals("", out.getString("mirror_add_card_text"));
    }




    // ---------- Batch B: menu / back / destroy / pick / setAuto / refresh ----------

    private android.view.MenuItem menuItem(MirrorSourceActivity activity, int id) {
        return new androidx.appcompat.view.menu.MenuBuilder(activity)
                .add(android.view.Menu.NONE, id, android.view.Menu.NONE, "x");
    }

    @Test
    public void onOptionsItemSelected_batch_entersBatchMode() {
        MirrorSourceActivity activity = launchRestored(true, false);
        assertTrue(activity.onOptionsItemSelected(menuItem(activity, 1)));
        assertTrue((boolean) get(activity, "mBatchMode"));
    }

    @Test
    public void onOptionsItemSelected_batch_exitsBatchMode() {
        MirrorSourceActivity activity = launchRestored(true, true);
        assertTrue(activity.onOptionsItemSelected(menuItem(activity, 1)));
        assertFalse((boolean) get(activity, "mBatchMode"));
    }

    @Test
    public void onOptionsItemSelected_unknown_returnsFalse() {
        MirrorSourceActivity activity = launchRestored(true, false);
        assertFalse(activity.onOptionsItemSelected(menuItem(activity, 999)));
    }

    @Test
    public void onBackPressed_addCardOpen_cancelsCard() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        activity.onBackPressed();
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onDestroy_cancelsSpeedTest() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
            c.destroy();
            verify(mocks.handle).cancel();
            assertNull(get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void cancelSpeedTest_nullHandle_doesNothing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        assertNull(get(activity, "mSpeedTestHandle"));
        call(activity, "cancelSpeedTest", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mSpeedTestHandle"));
    }

    @Test
    public void pick_setsCurrentAndDisablesAuto() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 300), result("b", "https://b/", 100));
        call(activity, "pick", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("b", "https://b/", 100)});
        assertEquals("https://b/", get(activity, "mCurrentValue"));
        assertTrue((boolean) get(activity, "mUserPicked"));
        assertFalse(prefs().getBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true));
        assertEquals("https://b/",
                prefs().getString(SettingsFragment.KEY_MIRROR_PRESET, null));
        assertFalse(((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).isChecked());
        assertEquals(activity.getString(R.string.mirror_manual_subtitle),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains("b"));
    }

    @Test
    public void setAuto_on_withFastest_updatesCurrent() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 300), result("b", "https://b/", 100));
        call(activity, "setAuto", new Class<?>[]{boolean.class}, new Object[]{true});
        assertEquals("https://b/", get(activity, "mCurrentValue"));
        assertFalse((boolean) get(activity, "mUserPicked"));
        assertTrue(prefs().getBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, false));
    }

    @Test
    public void setAuto_on_noFastest_keepsCurrent() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("c", "https://c/", -1));
        set(activity, "mCurrentValue", "https://c/");
        call(activity, "setAuto", new Class<?>[]{boolean.class}, new Object[]{true});
        assertEquals("https://c/", get(activity, "mCurrentValue"));
        assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                .getText().toString());
    }

    @Test
    public void setAuto_off_setsManual() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "setAuto", new Class<?>[]{boolean.class}, new Object[]{false});
        assertTrue((boolean) get(activity, "mUserPicked"));
        assertFalse(prefs().getBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true));
        assertEquals(activity.getString(R.string.mirror_manual_subtitle),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void setAutoSwitchChecked_suppressesListener() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, true).apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        // The switch is on; flipping it programmatically via the helper must
        // not fire the listener (mUserPicked stays false, current unchanged).
        call(activity, "setAutoSwitchChecked", new Class<?>[]{boolean.class},
                new Object[]{false});
        assertFalse(((androidx.appcompat.widget.SwitchCompat)
                activity.findViewById(R.id.auto_switch)).isChecked());
        assertFalse((boolean) get(activity, "mUserPicked"));
    }

    @Test
    public void refreshAndRetest_currentNotInList_fallsBackToDefault() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://gone.example.com/");
            call(activity, "refreshAndRetest", new Class<?>[]{}, new Object[]{});
            assertFalse((boolean) get(activity, "mTestDone"));
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
            // The default preset is among the targets, so it is the fallback.
            assertEquals(MirrorHelper.DEFAULT_PRESET, get(activity, "mCurrentValue"));
            assertEquals(MirrorHelper.DEFAULT_PRESET,
                    prefs().getString(SettingsFragment.KEY_MIRROR_PRESET, null));
        }
    }

    @Test
    public void refreshAndRetest_currentEmpty_staysEmpty() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "");
            call(activity, "refreshAndRetest", new Class<?>[]{}, new Object[]{});
            assertEquals("", get(activity, "mCurrentValue"));
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void refreshAndRetest_noDefault_fallsBackToFirst() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://gone.example.com/");
            call(activity, "refreshAndRetest", new Class<?>[]{}, new Object[]{});
            assertEquals("https://a.example.com", get(activity, "mCurrentValue"));
            assertEquals(2, itemsOf(activity).size());
        }
    }

    @Test
    public void refreshAndRetest_emptyTargets_showsEmptyHintAndNoTest() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST, "[]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            call(activity, "refreshAndRetest", new Class<?>[]{}, new Object[]{});
            assertTrue(itemsOf(activity).isEmpty());
            assertEquals(android.view.View.GONE,
                    activity.findViewById(R.id.list).getVisibility());
            assertEquals(android.view.View.VISIBLE,
                    activity.findViewById(R.id.empty_hint).getVisibility());
            assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                    .getText().toString());
            assertNull(get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void refreshAndRetest_cachedLatenciesShownAndSorted() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        assertTrue(targets.size() >= 2);
        MirrorHelper.saveSpeedTestResults(appContext(), java.util.Arrays.asList(
                result("x", targets.get(0).url, 900),
                result("y", targets.get(1).url, 50)));
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            call(activity, "refreshAndRetest", new Class<?>[]{}, new Object[]{});
            assertEquals(50L, findItem(activity, targets.get(1).url).latencyMs);
            assertEquals(targets.get(1).url, itemsOf(activity).get(0).url);
        }
    }


    // ---------- Batch C: speed-test callbacks / finishTest / row + stats ----------

    private void startTest(MirrorSourceActivity activity) {
        call(activity, "startSpeedTest", new Class<?>[]{}, new Object[]{});
    }

    @Test
    public void onProbeComplete_updatesItemAndDoneSet() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            setItems(activity, result("a", "https://a/", -1), result("b", "https://b/", -1));
            startTest(activity);
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            cb.onProbeComplete(result("a", "https://a/", 120));
            assertEquals(120L, findItem(activity, "https://a/").latencyMs);
            assertTrue(((Set<?>) get(activity, "mDoneUrls")).contains("https://a/"));
            assertEquals("2", ((android.widget.TextView)
                    activity.findViewById(R.id.stats_total)).getText().toString());
            assertEquals("1", ((android.widget.TextView)
                    activity.findViewById(R.id.stats_ok)).getText().toString());
        }
    }

    @Test
    public void onProbeComplete_unknownUrl_onlyMarksDone() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            setItems(activity, result("a", "https://a/", -1));
            startTest(activity);
            mocks.callback().onProbeComplete(result("x", "https://unknown/", 50));
            assertTrue(((Set<?>) get(activity, "mDoneUrls")).contains("https://unknown/"));
            assertEquals(1, itemsOf(activity).size());
            assertEquals(-1L, findItem(activity, "https://a/").latencyMs);
        }
    }

    @Test
    public void onProbeComplete_staleGeneration_ignored() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            setItems(activity, result("a", "https://a/", -1));
            startTest(activity);
            MirrorHelper.SpeedTestCallback oldCb = mocks.callback();
            startTest(activity);
            MirrorHelper.SpeedTestCallback newCb = mocks.callbackCaptor.getAllValues().get(1);
            oldCb.onProbeComplete(result("a", "https://a/", 120));
            assertEquals(-1L, findItem(activity, "https://a/").latencyMs);
            assertTrue(((Set<?>) get(activity, "mDoneUrls")).isEmpty());
            newCb.onProbeComplete(result("a", "https://a/", 120));
            assertEquals(120L, findItem(activity, "https://a/").latencyMs);
        }
    }

    @Test
    public void onProbeComplete_destroyedActivity_ignored() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            c.destroy();
            cb.onProbeComplete(result("a", "https://a/", 120));
            assertTrue(((Set<?>) get(activity, "mDoneUrls")).isEmpty());
        }
    }

    @Test
    public void onAllComplete_savesResultsAndFinishes() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            setItems(activity, result("a", "https://a/", -1), result("b", "https://b/", -1));
            set(activity, "mUserPicked", false);
            startTest(activity);
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            cb.onProbeComplete(result("a", "https://a/", 300));
            cb.onProbeComplete(result("b", "https://b/", 100));
            cb.onAllComplete();
            assertNull(get(activity, "mSpeedTestHandle"));
            assertTrue((boolean) get(activity, "mTestDone"));
            // Sorted: b first; auto-select picks the fastest.
            assertEquals("https://b/", itemsOf(activity).get(0).url);
            assertEquals("https://b/", get(activity, "mCurrentValue"));
            assertEquals("https://b/",
                    prefs().getString(SettingsFragment.KEY_MIRROR_PRESET, null));
            // Results were cached.
            assertEquals(100L, (long) MirrorHelper.getCachedSpeedResults(appContext())
                    .get("https://b/"));
            assertTrue(MirrorHelper.getSpeedTestTime(appContext()) > 0);
        }
    }

    @Test
    public void onAllComplete_staleGeneration_ignored() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            setItems(activity, result("slow", "https://slow/", 900),
                    result("fast", "https://fast/", 50));
            startTest(activity);
            MirrorHelper.SpeedTestCallback oldCb = mocks.callback();
            startTest(activity);
            oldCb.onAllComplete();
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
            // The stale callback returned before finishTest: the list is
            // still in its original (unsorted) order.
            assertEquals("https://slow/", itemsOf(activity).get(0).url);
        }
    }

    @Test
    public void onAllComplete_destroyedActivity_nullsHandleButSkipsFinish() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            c.destroy();
            cb.onAllComplete();
            assertNull(get(activity, "mSpeedTestHandle"));
            assertFalse((boolean) get(activity, "mTestDone"));
        }
    }

    @Test
    public void finishTest_userPicked_setsManualSubtitle() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 300), result("b", "https://b/", 100));
        set(activity, "mUserPicked", true);
        set(activity, "mCurrentValue", "https://a/");
        call(activity, "finishTest", new Class<?>[]{}, new Object[]{});
        assertEquals("https://a/", get(activity, "mCurrentValue"));
        assertEquals(activity.getString(R.string.mirror_manual_subtitle),
                ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                        .getText().toString());
    }

    @Test
    public void finishTest_noOkAndNotPicked_clearsSubtitle() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        set(activity, "mUserPicked", false);
        call(activity, "finishTest", new Class<?>[]{}, new Object[]{});
        assertEquals("", ((android.widget.TextView) activity.findViewById(R.id.auto_subtitle))
                .getText().toString());
    }

    @Test
    public void updateRowBadge_nullListView_doesNothing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        set(activity, "mListView", null);
        call(activity, "updateRowBadge", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("a", "https://a/", 100)});
    }

    @Test
    public void updateRowBadge_unknownUrl_doesNothing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        call(activity, "updateRowBadge", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("x", "https://unknown/", 50)});
    }

    @Test
    public void updateRowBadge_visibleRow_showsLatency() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        android.widget.ListView list = activity.findViewById(R.id.list);
        list.layout(0, 0, 1080, 1920);
        idleMain(100);
        call(activity, "updateRowBadge", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("a", "https://a/", 250)});
        android.view.View row = list.getChildAt(0);
        assertNotNull(row);
        assertTrue(((android.widget.TextView) row.findViewById(R.id.status))
                .getText().toString().contains("250"));
    }

    @Test
    public void updateCurrentLine_emptyCurrent_showsNone() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mCurrentValue", "");
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_current_none)));
    }

    @Test
    public void updateCurrentLine_nullCurrent_showsNone() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mCurrentValue", null);
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_current_none)));
    }

    @Test
    public void updateCurrentLine_okItem_showsLatency() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 120));
        set(activity, "mCurrentValue", "https://a/");
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains("120"));
    }

    @Test
    public void updateCurrentLine_failedItemDone_showsUnavailable() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        set(activity, "mCurrentValue", "https://a/");
        set(activity, "mTestDone", true);
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_source_unavailable)));
    }

    @Test
    public void updateCurrentLine_failedItemInDoneSet_showsUnavailable() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        set(activity, "mCurrentValue", "https://a/");
        set(activity, "mTestDone", false);
        ((Set<String>) get(activity, "mDoneUrls")).add("https://a/");
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_source_unavailable)));
    }

    @Test
    public void updateCurrentLine_failedItemTesting_showsTesting() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        set(activity, "mCurrentValue", "https://a/");
        set(activity, "mTestDone", false);
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_source_testing)));
    }

    @Test
    public void updateCurrentLine_currentNotInList_showsTesting() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 120));
        set(activity, "mCurrentValue", "https://other.example.com/");
        call(activity, "updateCurrentLine", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.current_line))
                .getText().toString().contains(
                        activity.getString(R.string.mirror_source_testing)));
    }

    @Test
    public void updateStatsBar_withSavedTime_showsUpdated() {
        MirrorSourceActivity activity = launchRestored(true, false);
        MirrorHelper.saveSpeedTestResults(appContext(),
                java.util.Collections.singletonList(result("a", "https://a/", 10)));
        setItems(activity, result("a", "https://a/", 10));
        call(activity, "updateStatsBar", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.stats_updated).getVisibility());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.stats_updated))
                .getText().toString().length() > 0);
    }

    @Test
    public void updateStatsBar_noSavedTime_hidesUpdated() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 10));
        call(activity, "updateStatsBar", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.stats_updated).getVisibility());
    }


    // ---------- Batch D: add/edit card + delete ----------

    private void openAddCard(MirrorSourceActivity activity) {
        call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
    }

    private void setUrlInput(MirrorSourceActivity activity, String text) {
        ((android.widget.EditText) activity.findViewById(R.id.url)).setText(text);
    }

    private void clickSave(MirrorSourceActivity activity) {
        activity.findViewById(R.id.add_save).performClick();
    }

    private androidx.appcompat.app.AlertDialog shownDialog() {
        return (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowAlertDialog
                .getShownDialogs().get(0);
    }

    @Test
    public void startAdding_opensCardCleared() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertEquals(activity.getString(R.string.mirror_add),
                ((android.widget.TextView) activity.findViewById(R.id.add_title))
                        .getText().toString());
        assertEquals("", ((android.widget.EditText) activity.findViewById(R.id.url))
                .getText().toString());
        assertTrue((boolean) get(activity, "mEditingIsNew"));
        assertNull(get(activity, "mEditingOldUrl"));
    }

    @Test
    public void startAdding_whenAlreadyOpen_keepsText() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        setUrlInput(activity, "https://typed.example.com/");
        openAddCard(activity);
        assertEquals("https://typed.example.com/",
                ((android.widget.EditText) activity.findViewById(R.id.url))
                        .getText().toString());
    }

    @Test
    public void startEditing_opensCardPrefilled() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "startEditing", new Class<?>[]{String.class},
                new Object[]{"https://a/"});
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertEquals(activity.getString(R.string.me_mirror_edit),
                ((android.widget.TextView) activity.findViewById(R.id.add_title))
                        .getText().toString());
        assertEquals("https://a/", ((android.widget.EditText) activity.findViewById(R.id.url))
                .getText().toString());
        assertFalse((boolean) get(activity, "mEditingIsNew"));
        assertEquals("https://a/", get(activity, "mEditingOldUrl"));
    }

    @Test
    public void startEditing_whenAlreadyOpen_keepsOldUrl() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "startEditing", new Class<?>[]{String.class},
                new Object[]{"https://a/"});
        call(activity, "startEditing", new Class<?>[]{String.class},
                new Object[]{"https://b/"});
        assertEquals("https://a/", get(activity, "mEditingOldUrl"));
        assertEquals("https://a/", ((android.widget.EditText) activity.findViewById(R.id.url))
                .getText().toString());
    }

    @Test
    public void saveAddCard_emptyInput_showsInvalidToast() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        setUrlInput(activity, "");
        clickSave(activity);
        assertEquals(activity.getString(R.string.mirror_custom_url_invalid),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
    }

    @Test
    public void saveAddCard_nonHttps_showsInvalidToast() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        setUrlInput(activity, "http://a.example.com/");
        clickSave(activity);
        assertEquals(activity.getString(R.string.mirror_custom_url_invalid),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
    }

    @Test
    public void saveAddCard_loopback_showsInvalidToast() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        setUrlInput(activity, "https://127.0.0.1/");
        clickSave(activity);
        assertEquals(activity.getString(R.string.mirror_custom_url_invalid),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
    }

    @Test
    public void saveAddCard_duplicate_showsExistsToast() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\"]").apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        setUrlInput(activity, "https://a.example.com/");
        clickSave(activity);
        assertEquals(activity.getString(R.string.mirror_url_exists),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
    }

    @Test
    public void saveAddCard_editUnchanged_cancelsCard() {
        MirrorSourceActivity activity = launchRestored(true, false);
        // The URL must be slash-free: saveAddCard normalizes the input
        // (stripping the trailing slash) before comparing with mEditingOldUrl,
        // so a trailing slash would miss the unchanged path.
        call(activity, "startEditing", new Class<?>[]{String.class},
                new Object[]{"https://a"});
        clickSave(activity);
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertNull(get(activity, "mEditingOldUrl"));
        // The unchanged URL was not re-added or duplicated.
        assertFalse(MirrorHelper.getMirrorUrls(appContext()).contains("https://a"));
    }

    @Test
    public void saveAddCard_addNew_addsAndRetests() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            openAddCard(activity);
            setUrlInput(activity, "https://new-mirror.example.com/");
            clickSave(activity);
            assertEquals(android.view.View.GONE,
                    activity.findViewById(R.id.add_card).getVisibility());
            assertEquals(activity.getString(R.string.mirror_added),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertTrue(MirrorHelper.getMirrorUrls(appContext())
                    .contains("https://new-mirror.example.com"));
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
        }
    }

    // A1-9 (confirmed production defect): editing a mirror never replaces the
    // old URL. saveAddCard calls collapseAddCard() -- which clears
    // mEditingOldUrl -- before passing mEditingOldUrl to updateMirrorUrl, so
    // updateMirrorUrl receives null as the old URL and appends the new one
    // instead of replacing. The old URL stays in the list while the toast
    // still says "Saved".
    @Test
    public void saveAddCard_editExisting_oldUrlRemains_A1_9() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            call(activity, "startEditing", new Class<?>[]{String.class},
                    new Object[]{"https://a.example.com"});
            setUrlInput(activity, "https://renamed.example.com/");
            clickSave(activity);
            assertEquals(android.view.View.GONE,
                    activity.findViewById(R.id.add_card).getVisibility());
            assertEquals(activity.getString(R.string.mirror_updated),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            java.util.List<String> urls = MirrorHelper.getMirrorUrls(appContext());
            assertTrue(urls.contains("https://a.example.com"));
            assertTrue(urls.contains("https://renamed.example.com"));
            assertEquals(3, urls.size());
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void saveAddCard_editCurrent_updatesCurrent() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://a.example.com");
            call(activity, "startEditing", new Class<?>[]{String.class},
                    new Object[]{"https://a.example.com"});
            setUrlInput(activity, "https://renamed.example.com/");
            clickSave(activity);
            assertEquals("https://renamed.example.com", get(activity, "mCurrentValue"));
            assertEquals("https://renamed.example.com",
                    prefs().getString(SettingsFragment.KEY_MIRROR_PRESET, null));
        }
    }

    @Test
    public void cancelAddCard_closesCard() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        activity.findViewById(R.id.add_cancel).performClick();
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertNull(get(activity, "mEditingOldUrl"));
    }

    @Test
    public void deleteByUrl_addCardOpen_ignored() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        call(activity, "deleteByUrl", new Class<?>[]{String.class},
                new Object[]{"https://a/"});
        assertEquals(0, org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().size());
    }

    @Test
    public void deleteByUrl_confirm_removesAndRetests() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            call(activity, "deleteByUrl", new Class<?>[]{String.class},
                    new Object[]{"https://a.example.com"});
            androidx.appcompat.app.AlertDialog dialog = shownDialog();
            assertEquals(activity.getString(R.string.mirror_delete_confirm),
                    ((android.widget.TextView) dialog.findViewById(android.R.id.message))
                            .getText().toString());
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            idleMain(200);
            assertFalse(MirrorHelper.getMirrorUrls(appContext())
                    .contains("https://a.example.com"));
            assertEquals(activity.getString(R.string.mirror_deleted),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void deleteByUrl_cancel_keepsUrl() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "deleteByUrl", new Class<?>[]{String.class},
                new Object[]{"https://a.example.com"});
        androidx.appcompat.app.AlertDialog dialog = shownDialog();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMain(200);
        assertTrue(MirrorHelper.getMirrorUrls(appContext())
                .contains("https://a.example.com"));
    }

    private void seedThreeMirrorsWithCache() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\", "
                        + "\"https://c.example.com/\"]").apply();
        MirrorHelper.saveSpeedTestResults(appContext(), java.util.Arrays.asList(
                result("a", "https://a.example.com", 300),
                result("b", "https://b.example.com", 100),
                result("c", "https://c.example.com", -1)));
    }

    @Test
    public void deleteUrls_removesCurrent_autoReselectsFastest() {
        seedThreeMirrorsWithCache();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://a.example.com");
            set(activity, "mUserPicked", false);
            call(activity, "deleteUrls", new Class<?>[]{java.util.List.class},
                    new Object[]{java.util.Collections.singletonList("https://a.example.com")});
            // b (100ms) is the fastest remaining ok mirror.
            assertEquals("https://b.example.com", get(activity, "mCurrentValue"));
            assertEquals("https://b.example.com",
                    prefs().getString(SettingsFragment.KEY_MIRROR_PRESET, null));
            assertFalse(MirrorHelper.getMirrorUrls(appContext())
                    .contains("https://a.example.com"));
        }
    }

    @Test
    public void deleteUrls_removesCurrent_userPicked_clearsCurrent() {
        seedThreeMirrorsWithCache();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://a.example.com");
            set(activity, "mUserPicked", true);
            call(activity, "deleteUrls", new Class<?>[]{java.util.List.class},
                    new Object[]{java.util.Collections.singletonList("https://a.example.com")});
            assertEquals("", get(activity, "mCurrentValue"));
        }
    }

    @Test
    public void deleteUrls_removesCurrent_noOkLeft_clearsCurrent() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\"]").apply();
        MirrorHelper.saveSpeedTestResults(appContext(), java.util.Collections.singletonList(
                result("a", "https://a.example.com", -1)));
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://a.example.com");
            set(activity, "mUserPicked", false);
            call(activity, "deleteUrls", new Class<?>[]{java.util.List.class},
                    new Object[]{java.util.Collections.singletonList("https://a.example.com")});
            assertEquals("", get(activity, "mCurrentValue"));
        }
    }

    @Test
    public void deleteUrls_keepsOtherCurrent() {
        seedThreeMirrorsWithCache();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            set(activity, "mCurrentValue", "https://b.example.com");
            set(activity, "mUserPicked", false);
            call(activity, "deleteUrls", new Class<?>[]{java.util.List.class},
                    new Object[]{java.util.Collections.singletonList("https://a.example.com")});
            assertEquals("https://b.example.com", get(activity, "mCurrentValue"));
        }
    }







    // ---------- Batch E: batch delete + adapter ----------

    private android.widget.BaseAdapter adapterOf(MirrorSourceActivity activity) {
        return (android.widget.BaseAdapter) get(activity, "mAdapter");
    }

    private android.view.View rowView(MirrorSourceActivity activity, int position) {
        return adapterOf(activity).getView(position, null,
                activity.findViewById(R.id.list));
    }

    @Test
    public void updateBatchBar_empty_disablesDelete() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        call(activity, "updateBatchBar", new Class<?>[]{}, new Object[]{});
        assertFalse(((android.widget.Button) activity.findViewById(R.id.batch_delete))
                .isEnabled());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.batch_count))
                .getText().toString().contains("0"));
    }

    @Test
    public void updateBatchBar_withChecked_enablesDelete() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        ((Set<String>) get(activity, "mChecked")).add("https://a/");
        ((Set<String>) get(activity, "mChecked")).add("https://b/");
        call(activity, "updateBatchBar", new Class<?>[]{}, new Object[]{});
        assertTrue(((android.widget.Button) activity.findViewById(R.id.batch_delete))
                .isEnabled());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.batch_count))
                .getText().toString().contains("2"));
    }

    @Test
    public void enterBatchMode_collapsesAddCard() {
        MirrorSourceActivity activity = launchRestored(true, false);
        openAddCard(activity);
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertTrue((boolean) get(activity, "mBatchMode"));
    }

    @Test
    public void askBatchDelete_empty_doesNothing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        call(activity, "askBatchDelete", new Class<?>[]{}, new Object[]{});
        assertEquals(0, org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().size());
    }

    @Test
    public void askBatchDelete_confirm_deletesAndExitsBatch() {
        seedThreeMirrorsWithCache();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
            call(activity, "toggleChecked", new Class<?>[]{String.class},
                    new Object[]{"https://a.example.com"});
            call(activity, "toggleChecked", new Class<?>[]{String.class},
                    new Object[]{"https://b.example.com"});
            call(activity, "askBatchDelete", new Class<?>[]{}, new Object[]{});
            androidx.appcompat.app.AlertDialog dialog = shownDialog();
            String message = ((android.widget.TextView)
                    dialog.findViewById(android.R.id.message)).getText().toString();
            assertTrue(message.contains("2"));
            assertTrue(message.contains("a.example.com"));
            assertTrue(message.contains("b.example.com"));
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            idleMain(200);
            assertFalse((boolean) get(activity, "mBatchMode"));
            java.util.List<String> urls = MirrorHelper.getMirrorUrls(appContext());
            assertFalse(urls.contains("https://a.example.com"));
            assertFalse(urls.contains("https://b.example.com"));
            assertTrue(urls.contains("https://c.example.com"));
            assertEquals(mocks.handle, get(activity, "mSpeedTestHandle"));
        }
    }

    @Test
    public void askBatchDelete_cancel_keepsUrlsAndBatchMode() {
        seedThreeMirrorsWithCache();
        MirrorSourceActivity activity = launchRestored(true, false);
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        call(activity, "toggleChecked", new Class<?>[]{String.class},
                new Object[]{"https://a.example.com"});
        call(activity, "askBatchDelete", new Class<?>[]{}, new Object[]{});
        androidx.appcompat.app.AlertDialog dialog = shownDialog();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMain(200);
        assertTrue((boolean) get(activity, "mBatchMode"));
        assertTrue(MirrorHelper.getMirrorUrls(appContext())
                .contains("https://a.example.com"));
    }

    @Test
    public void adapter_getCountGetItemGetItemId() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 200));
        android.widget.BaseAdapter adapter = adapterOf(activity);
        assertEquals(2, adapter.getCount());
        assertEquals("https://a/",
                ((MirrorHelper.MirrorSpeedResult) adapter.getItem(0)).url);
        assertEquals(1L, adapter.getItemId(1));
    }

    @Test
    public void adapter_getView_normalMode_selectedRow() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 200));
        set(activity, "mCurrentValue", "https://a/");
        android.view.View row = rowView(activity, 0);
        assertEquals("a", ((android.widget.TextView) row.findViewById(R.id.name))
                .getText().toString());
        assertEquals("https://a/", ((android.widget.TextView) row.findViewById(R.id.url))
                .getText().toString());
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.current_pill).getVisibility());
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.check).getVisibility());
        assertEquals(android.view.View.GONE, row.findViewById(R.id.cbox).getVisibility());
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.edit).getVisibility());
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.delete).getVisibility());
        assertNotNull(row.getBackground());
    }

    @Test
    public void adapter_getView_normalMode_unselectedRow() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 200));
        set(activity, "mCurrentValue", "https://a/");
        android.view.View row = rowView(activity, 1);
        assertEquals(android.view.View.GONE, row.findViewById(R.id.current_pill).getVisibility());
        assertEquals(android.view.View.GONE, row.findViewById(R.id.check).getVisibility());
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.edit).getVisibility());
        assertNull(row.getBackground());
    }

    @Test
    public void adapter_getView_batchMode_checkedRow() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 200));
        set(activity, "mCurrentValue", "https://a/");
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        call(activity, "toggleChecked", new Class<?>[]{String.class},
                new Object[]{"https://a/"});
        android.view.View row = rowView(activity, 0);
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.cbox).getVisibility());
        assertTrue(((android.widget.CheckBox) row.findViewById(R.id.cbox)).isChecked());
        assertEquals(android.view.View.GONE, row.findViewById(R.id.edit).getVisibility());
        assertEquals(android.view.View.GONE, row.findViewById(R.id.delete).getVisibility());
        assertNotNull(row.getBackground());
    }

    @Test
    public void adapter_getView_batchMode_uncheckedRow() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100), result("b", "https://b/", 200));
        call(activity, "enterBatchMode", new Class<?>[]{}, new Object[]{});
        android.view.View row = rowView(activity, 0);
        assertEquals(android.view.View.VISIBLE, row.findViewById(R.id.cbox).getVisibility());
        assertFalse(((android.widget.CheckBox) row.findViewById(R.id.cbox)).isChecked());
        assertNull(row.getBackground());
    }

    @Test
    public void adapter_getView_reusesConvertView() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        android.view.View first = rowView(activity, 0);
        android.view.View second = adapterOf(activity).getView(0, first,
                activity.findViewById(R.id.list));
        assertEquals(first, second);
    }

    @Test
    public void adapter_editButton_startsEditing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        android.view.View row = rowView(activity, 0);
        row.findViewById(R.id.edit).performClick();
        assertEquals(android.view.View.VISIBLE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertEquals("https://a/", ((android.widget.EditText) activity.findViewById(R.id.url))
                .getText().toString());
        assertEquals("https://a/", get(activity, "mEditingOldUrl"));
    }

    @Test
    public void adapter_deleteButton_showsConfirm() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        android.view.View row = rowView(activity, 0);
        row.findViewById(R.id.delete).performClick();
        androidx.appcompat.app.AlertDialog dialog = shownDialog();
        assertEquals(activity.getString(R.string.mirror_delete_confirm),
                ((android.widget.TextView) dialog.findViewById(android.R.id.message))
                        .getText().toString());
    }

    @Test
    public void adapter_bindBadge_testing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        android.view.View row = rowView(activity, 0);
        assertEquals(activity.getString(R.string.mirror_source_testing),
                ((android.widget.TextView) row.findViewById(R.id.status))
                        .getText().toString());
        assertNotNull(row.findViewById(R.id.status).getBackground());
    }

    @Test
    public void adapter_bindBadge_okFast_showsLatency() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 100));
        android.view.View row = rowView(activity, 0);
        assertTrue(((android.widget.TextView) row.findViewById(R.id.status))
                .getText().toString().contains("100"));
    }

    @Test
    public void adapter_bindBadge_okMedium_showsLatency() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 700));
        android.view.View row = rowView(activity, 0);
        assertTrue(((android.widget.TextView) row.findViewById(R.id.status))
                .getText().toString().contains("700"));
    }

    @Test
    public void adapter_bindBadge_okSlow_showsLatency() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", 1500));
        android.view.View row = rowView(activity, 0);
        assertTrue(((android.widget.TextView) row.findViewById(R.id.status))
                .getText().toString().contains("1500"));
    }

    @Test
    public void adapter_bindBadge_failedDone_showsUnavailable() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1));
        ((Set<String>) get(activity, "mDoneUrls")).add("https://a/");
        android.view.View row = rowView(activity, 0);
        assertEquals(activity.getString(R.string.mirror_source_unavailable),
                ((android.widget.TextView) row.findViewById(R.id.status))
                        .getText().toString());
    }


    // ---------- Batch F: remaining branch arms ----------

    @Test
    public void restore_urlsShorterThanLatencies_stopsAtUrls() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        String url = targets.get(0).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Collections.singletonList(url), new long[]{100, 200});
        MirrorSourceActivity activity = launchWithState(b);
        // Only the one saved URL got a latency; the extra latency is ignored.
        assertEquals(100L, findItem(activity, url).latencyMs);
    }

    @Test
    public void onProbeComplete_finishingActivity_ignored() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            activity.finish();
            assertTrue(activity.isFinishing());
            cb.onProbeComplete(result("a", "https://a/", 120));
            assertTrue(((Set<?>) get(activity, "mDoneUrls")).isEmpty());
        }
    }

    @Test
    public void onAllComplete_finishingActivity_nullsHandleSkipsFinish() {
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            ActivityController<MirrorSourceActivity> c =
                    controller(MirrorSourceActivity.class, null);
            MirrorSourceActivity activity = c.create(null).start().resume().get();
            MirrorHelper.SpeedTestCallback cb = mocks.callback();
            activity.finish();
            cb.onAllComplete();
            assertNull(get(activity, "mSpeedTestHandle"));
            assertFalse((boolean) get(activity, "mTestDone"));
        }
    }

    @Test
    public void updateRowBadge_rowAboveVisible_ignored() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1), result("b", "https://b/", -1),
                result("c", "https://c/", -1));
        android.widget.ListView mockList = mock(android.widget.ListView.class);
        org.mockito.Mockito.when(mockList.getFirstVisiblePosition()).thenReturn(2);
        org.mockito.Mockito.when(mockList.getChildCount()).thenReturn(1);
        set(activity, "mListView", mockList);
        call(activity, "updateRowBadge", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("a", "https://a/", 100)});
        org.mockito.Mockito.verify(mockList, org.mockito.Mockito.never()).getChildAt(
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void updateRowBadge_rowBelowVisible_ignored() {
        MirrorSourceActivity activity = launchRestored(true, false);
        setItems(activity, result("a", "https://a/", -1), result("b", "https://b/", -1),
                result("c", "https://c/", -1));
        android.widget.ListView mockList = mock(android.widget.ListView.class);
        org.mockito.Mockito.when(mockList.getFirstVisiblePosition()).thenReturn(0);
        org.mockito.Mockito.when(mockList.getChildCount()).thenReturn(1);
        set(activity, "mListView", mockList);
        call(activity, "updateRowBadge", new Class<?>[]{MirrorHelper.MirrorSpeedResult.class},
                new Object[]{result("c", "https://c/", 100)});
        org.mockito.Mockito.verify(mockList, org.mockito.Mockito.never()).getChildAt(
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void isAddCardOpen_nullCard_returnsFalse() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mAddCard", null);
        assertFalse((boolean) call(activity, "isAddCardOpen",
                new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void collapseAddCard_nullCard_doesNothing() {
        MirrorSourceActivity activity = launchRestored(true, false);
        set(activity, "mAddCard", null);
        call(activity, "collapseAddCard", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditingOldUrl"));
    }

    @Test
    public void showAddCard_focusesInput() {
        MirrorSourceActivity activity = launchVisibleRestored();
        openAddCard(activity);
        grantWindowFocus(activity);
        idleMain(200);
        android.widget.EditText input = (android.widget.EditText) get(activity, "mUrlInput");
        assertTrue(input.hasFocus());
        // The posted runnable also shows the keyboard.
        assertTrue(softInputVisible(activity));
    }

    @Test
    public void hideKeyboard_focusedView_hides() {
        MirrorSourceActivity activity = launchVisibleRestored();
        openAddCard(activity);
        grantWindowFocus(activity);
        idleMain(200);
        android.widget.EditText input = (android.widget.EditText) get(activity, "mUrlInput");
        assertTrue(input.hasFocus());
        assertTrue(softInputVisible(activity));
        // Robolectric's Activity.getCurrentFocus() stays null even for a
        // focused view (ShadowActivity tracks it separately), so register
        // the focus explicitly for hideKeyboard's getCurrentFocus() call.
        org.robolectric.Shadows.shadowOf(activity).setCurrentFocus(input);
        call(activity, "cancelAddCard", new Class<?>[]{}, new Object[]{});
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.add_card).getVisibility());
        assertFalse(softInputVisible(activity));
    }

    @Test
    public void saveAddCard_editWithNullOldUrl_appends() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://a.example.com/\", \"https://b.example.com/\"]").apply();
        try (SpeedTestMocks mocks = new SpeedTestMocks()) {
            MirrorSourceActivity activity = launchRestored(true, false);
            // Simulate the A1-9 state: editing mode with a null old URL.
            set(activity, "mEditingIsNew", false);
            set(activity, "mEditingOldUrl", null);
            call(activity, "startAdding", new Class<?>[]{}, new Object[]{});
            // startAdding resets mEditingIsNew; restore the editing state.
            set(activity, "mEditingIsNew", false);
            set(activity, "mEditingOldUrl", null);
            setUrlInput(activity, "https://new.example.com/");
            clickSave(activity);
            assertTrue(MirrorHelper.getMirrorUrls(appContext())
                    .contains("https://new.example.com"));
            // wasCurrent was false (old URL null), so the save itself did not
            // move the current mirror to the new URL; refreshAndRetest then
            // re-derived it from the targets (first target, since the
            // previous current is not in the list).
            assertEquals("https://a.example.com", get(activity, "mCurrentValue"));
        }
    }


    @Test
    public void restore_latenciesShorterThanUrls_stopsAtLatencies() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(appContext());
        String url0 = targets.get(0).url;
        String url1 = targets.get(1).url;
        Bundle b = state(true, false, true, false, "",
                java.util.Arrays.asList(url0, url1), new long[]{100});
        MirrorSourceActivity activity = launchWithState(b);
        assertEquals(100L, findItem(activity, url0).latencyMs);
        // The second URL had no saved latency (loop stopped at latencies).
        assertEquals(-1L, findItem(activity, url1).latencyMs);
    }


    @Test
    public void showKeyboard_nullImm_doesNothing() {
        MirrorSourceActivity activity = launchVisibleRestored();
        android.widget.EditText input = (android.widget.EditText) get(activity, "mUrlInput");
        // The input-method service is always present on a real device and
        // Robolectric always returns a shadow for it; the null arm is only
        // reachable by stubbing getSystemService on a spy.
        MirrorSourceActivity spy = Mockito.spy(activity);
        Mockito.when(spy.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .thenReturn(null);
        call(spy, "showKeyboard", new Class<?>[]{android.widget.EditText.class},
                new Object[]{input});
        assertFalse(softInputVisible(activity));
    }

    @Test
    public void hideKeyboard_nullImm_keepsKeyboard() {
        MirrorSourceActivity activity = launchVisibleRestored();
        openAddCard(activity);
        grantWindowFocus(activity);
        idleMain(200);
        assertTrue(softInputVisible(activity));
        android.widget.EditText input = (android.widget.EditText) get(activity, "mUrlInput");
        MirrorSourceActivity spy = Mockito.spy(activity);
        Mockito.when(spy.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .thenReturn(null);
        org.robolectric.Shadows.shadowOf(spy).setCurrentFocus(input);
        call(spy, "hideKeyboard", new Class<?>[]{}, new Object[]{});
        // The hide was a no-op (imm null), so the keyboard stays visible.
        assertTrue(softInputVisible(activity));
    }

}
