package com.gh4a.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.ThemeTrace;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowClipboardManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class DiagnosticLogActivityTest {
    @org.junit.Before
    public void cleanCrashFiles() {
        // The app itself appends APP_CREATED/ATTACH lines to the trace and
        // theme files on every launch, so those files are never a clean
        // slate: tests must assert on distinctive content, never on exact
        // counts. Crash files are different: nothing creates them at
        // startup, so delete leftovers to keep crash counts deterministic.
        File dir = DiagTrace.dir(appContext());
        File[] crashes = dir.listFiles((d, name) ->
                name.startsWith("crash-") && name.endsWith(".txt"));
        if (crashes != null) {
            for (File f : crashes) {
                f.delete();
            }
        }
    }

    @Test
    public void launch_titleAndLiveDefaults() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        assertEquals(activity.getString(R.string.diagnostic_log),
                activity.getSupportActionBar().getTitle().toString());
        assertFalse(activity.canSwipeToRefresh());
        assertTrue((boolean) get(activity, "mLive"));
        assertEquals(0, get(activity, "mFilter"));
    }

    @Test
    public void crashTimeFromName_validName_parsesTime() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        assertEquals("12:34:56", call(activity, "crashTimeFromName",
                new Class<?>[]{String.class}, new Object[]{"crash-20261110-123456-789.txt"}));
        assertEquals("00:00:01", call(activity, "crashTimeFromName",
                new Class<?>[]{String.class}, new Object[]{"crash-20260101-000001-000.txt"}));
    }

    @Test
    public void crashTimeFromName_invalidName_fallsBackToCurrentTime() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        String time = (String) call(activity, "crashTimeFromName",
                new Class<?>[]{String.class}, new Object[]{"weird.txt"});
        assertTrue(Pattern.matches("\\d{2}:\\d{2}:\\d{2}", time));
        String shortName = (String) call(activity, "crashTimeFromName",
                new Class<?>[]{String.class}, new Object[]{"crash-1.txt"});
        assertTrue(Pattern.matches("\\d{2}:\\d{2}:\\d{2}", shortName));
    }

    @Test
    public void loadCrashEntry_exceptionLineBecomesDetail() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("crash-20261110-123456-789", ".txt", activity.getCacheDir());
        java.nio.file.Files.write(f.toPath(), "header line\n[crash breadcrumb] ignored\njava.lang.RuntimeException: boom\nmore\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Object entry = call(activity, "loadCrashEntry",
                new Class<?>[]{File.class}, new Object[]{f});
        assertEquals(1, get(entry, "kind"));
        assertEquals("12:34:56", get(entry, "time"));
        assertEquals("java.lang.RuntimeException: boom", get(entry, "detail"));
        assertTrue(((String) get(entry, "full")).contains("boom"));
    }

    @Test
    public void loadCrashEntry_noExceptionLine_firstLineBecomesDetail() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("crash-20261110-010203-000", ".txt", activity.getCacheDir());
        java.nio.file.Files.write(f.toPath(), "first meaningful line\nsecond\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Object entry = call(activity, "loadCrashEntry",
                new Class<?>[]{File.class}, new Object[]{f});
        assertEquals("first meaningful line", get(entry, "detail"));
    }

    @Test
    public void loadTraceFile_parsesTimeAndDetailSkipsBlanks() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("trace", ".txt", activity.getCacheDir());
        java.nio.file.Files.write(f.toPath(), "10:00:01 TAG some message here\n\nbare line\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<Object> out = new ArrayList<>();
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class}, new Object[]{out, f});
        assertEquals(2, out.size());
        assertEquals(0, get(out.get(0), "kind"));
        assertEquals("10:00:01", get(out.get(0), "time"));
        assertEquals("some message here", get(out.get(0), "detail"));
        assertEquals("", get(out.get(1), "time"));
        assertEquals("bare line", get(out.get(1), "detail"));
    }

    @Test
    public void loadTraceFile_missingFile_addsNothing() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        List<Object> out = new ArrayList<>();
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class},
                new Object[]{out, new File(activity.getCacheDir(), "does-not-exist.txt")});
        assertTrue(out.isEmpty());
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class}, new Object[]{out, null});
        assertTrue(out.isEmpty());
    }

    @Test
    public void filterChips_filterTraceAndCrashLists() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        Object trace = entryViaReflection(activity, 0, "10:00:01", "trace detail");
        Object crash = entryViaReflection(activity, 1, "10:00:02", "crash detail");
        List<Object> all = (List<Object>) get(activity, "mAll");
        all.add(trace);
        all.add(crash);
        activity.findViewById(R.id.diag_filter_trace).performClick();
        assertEquals(1, ((List<?>) get(activity, "mShown")).size());
        assertEquals("trace detail", get(((List<?>) get(activity, "mShown")).get(0), "detail"));
        activity.findViewById(R.id.diag_filter_crash).performClick();
        assertEquals(1, ((List<?>) get(activity, "mShown")).size());
        assertEquals("crash detail", get(((List<?>) get(activity, "mShown")).get(0), "detail"));
        activity.findViewById(R.id.diag_filter_all).performClick();
        assertEquals(2, ((List<?>) get(activity, "mShown")).size());
    }

    @Test
    public void setLive_togglesTextAndButton() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        activity.findViewById(R.id.diag_live_button).performClick();
        assertFalse((boolean) get(activity, "mLive"));
        assertEquals(activity.getString(R.string.diagnostic_log_paused),
                ((TextView) activity.findViewById(R.id.diag_live_text)).getText().toString());
        assertEquals(activity.getString(R.string.diagnostic_log_resume),
                ((android.widget.Button) activity.findViewById(R.id.diag_live_button)).getText().toString());
        activity.findViewById(R.id.diag_live_button).performClick();
        assertTrue((boolean) get(activity, "mLive"));
    }

    @Test
    public void stateSaveRestore_keepsFilterAndLive() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        activity.findViewById(R.id.diag_filter_crash).performClick();
        activity.findViewById(R.id.diag_live_button).performClick();
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals(2, out.getInt("filter", -1));
        assertFalse(out.getBoolean("live", true));
    }

    @Test
    public void deleteQuietly_nullMissingAndExisting() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        call(activity, "deleteQuietly", new Class<?>[]{File.class}, new Object[]{null});
        call(activity, "deleteQuietly", new Class<?>[]{File.class},
                new Object[]{new File(activity.getCacheDir(), "nope.txt")});
        File f = File.createTempFile("del", ".txt", activity.getCacheDir());
        assertTrue(f.exists());
        call(activity, "deleteQuietly", new Class<?>[]{File.class}, new Object[]{f});
        assertFalse(f.exists());
    }

    @Test
    public void applyFilter_emptyShowsEmptyView() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        ((List<?>) get(activity, "mAll")).clear();
        call(activity, "applyFilter", new Class<?>[]{}, new Object[]{});
        assertEquals(View.VISIBLE, activity.findViewById(R.id.diag_empty).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.diag_list).getVisibility());
    }

    private Object entryViaReflection(DiagnosticLogActivity activity, int kind, String time, String detail)
            throws Exception {
        Class<?> entryClass = null;
        for (Class<?> c : DiagnosticLogActivity.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("Entry")) {
                entryClass = c;
            }
        }
        java.lang.reflect.Constructor<?> ctor = entryClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object e = ctor.newInstance();
        set(e, "kind", kind);
        set(e, "time", time);
        set(e, "detail", detail);
        return e;
    }

    // ---------- helpers ----------

    private File traceFile() {
        return DiagTrace.traceFile(appContext());
    }

    private File themeFile() {
        return ThemeTrace.file(appContext());
    }

    private File crashFile(String name) {
        return new File(DiagTrace.dir(appContext()), name);
    }

    private void write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** Wait for a condition that needs a real background thread (Background
     * runs on a cached pool): give it real time and keep pumping the main
     * looper so the result post can run. */
    private void waitForBackground(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMain(100);
            if (condition.getAsBoolean()) {
                return;
            }
        }
        assertTrue("condition not met within " + timeoutMs + "ms",
                condition.getAsBoolean());
    }

    private int shownSize(DiagnosticLogActivity activity) {
        return ((List<?>) get(activity, "mShown")).size();
    }

    private int allSize(DiagnosticLogActivity activity) {
        return ((List<?>) get(activity, "mAll")).size();
    }

    private boolean shownContains(DiagnosticLogActivity activity, String detail) {
        for (Object e : (List<?>) get(activity, "mShown")) {
            if (detail.equals(get(e, "detail"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDetail(List<Object> entries, String detail) {
        for (Object e : entries) {
            if (detail.equals(get(e, "detail"))) {
                return true;
            }
        }
        return false;
    }

    /** Stop the 1s poller from interfering: live stays on, but the field
     * flip makes every tick a no-op. */
    private void freezePoller(DiagnosticLogActivity activity) {
        set(activity, "mLive", false);
    }

    // ---------- poller / lifecycle ----------

    @Test
    public void poller_liveTrue_reloadsWhenFingerprintChanges() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        Runnable poller = (Runnable) get(activity, "mPoller");
        set(activity, "mLive", true);
        poller.run();
        waitForBackground(() -> shownContains(activity, "second"), 5000);
    }

    @Test
    public void poller_liveFalse_doesNothing() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        set(activity, "mLive", false);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        Runnable poller = (Runnable) get(activity, "mPoller");
        poller.run();
        Thread.sleep(300);
        idleMain(200);
        assertFalse(shownContains(activity, "second"));
        assertFalse((boolean) get(activity, "mReloadInFlight"));
    }

    @Test
    public void poller_looperDrivenTick_picksUpChanges() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        // The poller re-posts itself every 1000ms on the main looper.
        waitForBackground(() -> shownContains(activity, "second"), 8000);
    }

    @Test
    public void onPause_removesPoller() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        org.robolectric.android.controller.ActivityController<DiagnosticLogActivity> c =
                controller(DiagnosticLogActivity.class, null);
        DiagnosticLogActivity activity = c.create().start().resume().get();
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        c.pause();
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        idleMain(2500);
        Thread.sleep(300);
        idleMain(200);
        assertFalse(shownContains(activity, "second"));
    }

    @Test
    public void restore_savedFilterAndLive_restoredOnRecreate() {
        Bundle b = new Bundle();
        b.putInt("filter", 2);
        b.putBoolean("live", false);
        DiagnosticLogActivity activity = controller(DiagnosticLogActivity.class, null)
                .create(b).start().resume().get();
        assertEquals(2, get(activity, "mFilter"));
        assertFalse((boolean) get(activity, "mLive"));
        assertEquals(activity.getString(R.string.diagnostic_log_paused),
                ((TextView) activity.findViewById(R.id.diag_live_text)).getText().toString());
        assertEquals(activity.getString(R.string.diagnostic_log_resume),
                ((android.widget.Button) activity.findViewById(R.id.diag_live_button))
                        .getText().toString());
    }

    // ---------- fingerprint / reloadIfChanged / reload ----------

    @Test
    public void fingerprint_missingFiles_reportsNegative() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        // Let the startup log writes drain, then delete: nothing logs again
        // until the next lifecycle event, so the fingerprint sees -1.
        idleMain(1000);
        Thread.sleep(200);
        traceFile().delete();
        themeFile().delete();
        String fp = (String) call(activity, "fingerprint", new Class<?>[]{}, new Object[]{});
        assertTrue(fp.contains("diag-trace.txt:-1:-1"));
        assertTrue(fp.contains("theme-trace.txt:-1:-1"));
    }

    @Test
    public void fingerprint_existingFiles_reportsSizes() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        idleMain(1000);
        Thread.sleep(200);
        write(traceFile(), "0123456789");
        write(themeFile(), "abc");
        String fp = (String) call(activity, "fingerprint", new Class<?>[]{}, new Object[]{});
        assertTrue(fp.contains("diag-trace.txt:10:"));
        assertTrue(fp.contains("theme-trace.txt:3:"));
    }

    @Test
    public void reloadIfChanged_fingerprintChanged_reloads() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        call(activity, "reloadIfChanged", new Class<?>[]{}, new Object[]{});
        waitForBackground(() -> shownContains(activity, "second"), 5000);
    }

    @Test
    public void reloadIfChanged_fingerprintUnchanged_noReload() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        String before = (String) get(activity, "mFingerprint");
        call(activity, "reloadIfChanged", new Class<?>[]{}, new Object[]{});
        Thread.sleep(200);
        idleMain(200);
        assertFalse((boolean) get(activity, "mReloadInFlight"));
        assertEquals(before, get(activity, "mFingerprint"));
        assertTrue(shownContains(activity, "first"));
    }

    @Test
    public void reload_inFlight_returnsEarly() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        waitForBackground(() -> !(boolean) get(activity, "mReloadInFlight"), 5000);
        int sizeBefore = allSize(activity);
        set(activity, "mReloadInFlight", true);
        String before = (String) get(activity, "mFingerprint");
        call(activity, "reload", new Class<?>[]{}, new Object[]{});
        assertTrue((boolean) get(activity, "mReloadInFlight"));
        assertEquals(before, get(activity, "mFingerprint"));
        assertEquals(sizeBefore, allSize(activity));
    }

    @Test
    public void reload_nonLinearLayoutManager_defaultsAtTop() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        RecyclerView list = activity.findViewById(R.id.diag_list);
        // StaggeredGridLayoutManager is not a LinearLayoutManager.
        list.setLayoutManager(new androidx.recyclerview.widget.StaggeredGridLayoutManager(
                2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL));
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        call(activity, "reload", new Class<?>[]{}, new Object[]{});
        waitForBackground(() -> shownContains(activity, "second"), 5000);
    }

    @Test
    public void reload_notAtTop_doesNotScroll() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append(String.format("10:00:%02d TAG line %d\n", i, i));
        }
        write(traceFile(), sb.toString());
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "line 30"), 5000);
        freezePoller(activity);
        RecyclerView list = activity.findViewById(R.id.diag_list);
        androidx.recyclerview.widget.LinearLayoutManager mockManager =
                org.mockito.Mockito.mock(
                        androidx.recyclerview.widget.LinearLayoutManager.class);
        org.mockito.Mockito.when(mockManager.findFirstVisibleItemPosition())
                .thenReturn(20);
        list.setLayoutManager(mockManager);
        write(traceFile(), sb.toString() + "10:00:31 TAG line 31\n");
        call(activity, "reload", new Class<?>[]{}, new Object[]{});
        waitForBackground(() -> shownContains(activity, "line 31"), 5000);
    }

    @Test
    public void reload_destroyed_discardsResult() throws Exception {
        write(traceFile(), "10:00:01 TAG first\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        call(activity, "reload", new Class<?>[]{}, new Object[]{});
        activity.finish();
        waitForBackground(() -> !(boolean) get(activity, "mReloadInFlight"), 5000);
        assertTrue(shownContains(activity, "first"));
        assertFalse(shownContains(activity, "second"));
    }

    @Test
    public void reload_destroyedWithoutFinish_discardsResult() throws Exception {
        // The post's isFinishing() || isDestroyed() check: the finish() path
        // (isFinishing true) is covered by reload_destroyed_discardsResult.
        // This covers the other arm: destroyed by the system without
        // finish(), so isFinishing() is false and isDestroyed() is true.
        write(traceFile(), "10:00:01 TAG first\n");
        ActivityController<DiagnosticLogActivity> controller =
                controller(DiagnosticLogActivity.class, null);
        DiagnosticLogActivity activity = controller.create().start().resume().get();
        waitForBackground(() -> shownContains(activity, "first"), 5000);
        freezePoller(activity);
        write(traceFile(), "10:00:01 TAG first\n10:00:02 TAG second\n");
        call(activity, "reload", new Class<?>[]{}, new Object[]{});
        controller.pause().stop().destroy();
        assertFalse(activity.isFinishing());
        assertTrue(activity.isDestroyed());
        waitForBackground(() -> !(boolean) get(activity, "mReloadInFlight"), 5000);
        assertTrue(shownContains(activity, "first"));
        assertFalse(shownContains(activity, "second"));
    }

    // ---------- loadEntries / loadTraceFile / loadCrashEntry ----------

    @Test
    public void loadEntries_combinesTraceThemeAndCrash() throws Exception {
        write(traceFile(), "10:00:01 TAG trace one\n10:00:02 TAG trace two\n");
        write(themeFile(), "10:00:03 THEME theme one\n");
        write(crashFile("crash-20261110-123456-789.txt"),
                "header\njava.lang.RuntimeException: boom\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        List<Object> out = (List<Object>) call(activity, "loadEntries",
                new Class<?>[]{Context.class}, new Object[]{appContext()});
        assertTrue(containsDetail(out, "trace one"));
        assertTrue(containsDetail(out, "trace two"));
        assertTrue(containsDetail(out, "theme one"));
        Object crash = null;
        for (Object e : out) {
            if ((int) get(e, "kind") == 1) {
                crash = e;
            }
        }
        assertNotNull("crash entry missing", crash);
        assertEquals("12:34:56", get(crash, "time"));
        assertEquals("java.lang.RuntimeException: boom", get(crash, "detail"));
    }

    @Test
    public void loadTraceFile_emptyFile_addsNothing() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("trace-empty", ".txt", activity.getCacheDir());
        List<Object> out = new ArrayList<>();
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class}, new Object[]{out, f});
        assertTrue(out.isEmpty());
    }

    @Test
    public void loadTraceFile_noSpaceLine_wholeLineAsDetail() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("trace-nospace", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), "nospace\n".getBytes(StandardCharsets.UTF_8));
        List<Object> out = new ArrayList<>();
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class}, new Object[]{out, f});
        assertEquals(1, out.size());
        assertEquals("", get(out.get(0), "time"));
        assertEquals("nospace", get(out.get(0), "detail"));
    }

    @Test
    public void loadTraceFile_leadingSpaceLine_wholeLineAsDetail() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("trace-leading", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), " leading space\n".getBytes(StandardCharsets.UTF_8));
        List<Object> out = new ArrayList<>();
        call(activity, "loadTraceFile",
                new Class<?>[]{List.class, File.class}, new Object[]{out, f});
        assertEquals(1, out.size());
        assertEquals("", get(out.get(0), "time"));
        assertEquals(" leading space", get(out.get(0), "detail"));
    }

    @Test
    public void loadCrashEntry_errorLineBecomesDetail() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("crash-20261110-010203-000", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), "first\njava.lang.Error: bad\n".getBytes(StandardCharsets.UTF_8));
        Object entry = call(activity, "loadCrashEntry",
                new Class<?>[]{File.class}, new Object[]{f});
        assertEquals("java.lang.Error: bad", get(entry, "detail"));
    }

    @Test
    public void loadCrashEntry_emptyLinesSkipped() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("crash-20261110-010203-000", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), "\n\nfirst line\n".getBytes(StandardCharsets.UTF_8));
        Object entry = call(activity, "loadCrashEntry",
                new Class<?>[]{File.class}, new Object[]{f});
        assertEquals("first line", get(entry, "detail"));
    }

    @Test
    public void loadCrashEntry_onlyBreadcrumbs_detailStaysFilename() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("crash-20261110-010203-000", ".txt", activity.getCacheDir());
        Files.write(f.toPath(),
                "[crash breadcrumb] one\n[crash breadcrumb] two\n"
                        .getBytes(StandardCharsets.UTF_8));
        Object entry = call(activity, "loadCrashEntry",
                new Class<?>[]{File.class}, new Object[]{f});
        assertEquals(f.getName(), get(entry, "detail"));
    }

    // ---------- confirmClear ----------

    @Test
    public void confirmClear_positive_deletesFilesAndShowsToast() throws Exception {
        write(traceFile(), "10:00:01 TAG x\n");
        write(themeFile(), "theme\n");
        write(crashFile("crash-20261110-123456-789.txt"), "boom\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        waitForBackground(() -> shownContains(activity, "x"), 5000);
        activity.findViewById(R.id.diag_clear_button).performClick();
        idleMain(200);
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        ((androidx.appcompat.app.AlertDialog) dialogs.get(0))
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        idleMain(200);
        assertFalse(traceFile().exists());
        assertFalse(themeFile().exists());
        assertFalse(crashFile("crash-20261110-123456-789.txt").exists());
        assertEquals(activity.getString(R.string.diagnostic_log_cleared),
                ShadowToast.getTextOfLatestToast());
        waitForBackground(() -> !(boolean) get(activity, "mReloadInFlight"), 5000);
        assertEquals(0, allSize(activity));
        assertFalse(shownContains(activity, "x"));
    }

    @Test
    public void confirmClear_negative_keepsFiles() throws Exception {
        write(traceFile(), "10:00:01 TAG x\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        activity.findViewById(R.id.diag_clear_button).performClick();
        idleMain(200);
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        ((androidx.appcompat.app.AlertDialog) dialogs.get(0))
                .getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        idleMain(200);
        assertTrue(traceFile().exists());
    }

    // ---------- export via the options dialog ----------

    private void clickExportItem(DiagnosticLogActivity activity, int which) {
        clickExportItem(activity, which, true);
    }

    private void clickExportItem(DiagnosticLogActivity activity, int which,
            boolean idleAfter) {
        activity.findViewById(R.id.diag_export_button).performClick();
        idleMain(200);
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog) dialogs.get(0);
        android.widget.ListView lv = dialog.getListView();
        lv.performItemClick(lv.getAdapter().getView(which, null, lv), which,
                lv.getAdapter().getItemId(which));
        if (idleAfter) {
            idleMain(200);
        }
    }

    @Test
    public void showExportOptions_share_showsFailedToast_A1_8() throws Exception {
        // A1-8: share can never succeed -- FileProvider.getUriForFile throws
        // StringIndexOutOfBoundsException because file_paths.xml exposes the
        // export file itself as the root (see the shareExport direct test).
        // The production catch converts it to the failed toast, so that is
        // the behaviour this test locks in.
        write(traceFile(), "10:00:01 TAG x\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        clickExportItem(activity, 0);
        waitForBackground(() -> ShadowToast.getTextOfLatestToast() != null, 8000);
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void showExportOptions_copy_copiesToClipboard() throws Exception {
        write(traceFile(), "10:00:01 TAG hello\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        clickExportItem(activity, 2);
        waitForBackground(() -> {
            ClipboardManager cm = (ClipboardManager)
                    appContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm.getPrimaryClip();
            return clip != null && clip.getItemAt(0).getText() != null;
        }, 8000);
        ClipboardManager cm = (ClipboardManager)
                appContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertTrue(clip.getItemAt(0).getText().toString().contains("10:00:01 TAG hello"));
        assertEquals(activity.getString(R.string.diagnostic_log_copied),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void showExportOptions_download_insertNull_showsFailedToast() throws Exception {
        registerDownloadsProvider(null, null, false);
        write(traceFile(), "10:00:01 TAG x\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        clickExportItem(activity, 1);
        waitForBackground(() -> ShadowToast.getTextOfLatestToast() != null, 8000);
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void makeExportFile_destroyed_discardsResult() throws Exception {
        write(traceFile(), "10:00:01 TAG x\n");
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        // No idle after the click: the background export must still be in
        // flight when finish() runs, so the result post hits the destroyed
        // check instead of copying. The copy path is used because share
        // never produces an observable effect (A1-8).
        clickExportItem(activity, 2, false);
        activity.finish();
        // Wait until the export button is re-enabled: the post ran, and the
        // destroyed check must have skipped the copy.
        waitForBackground(
                () -> activity.findViewById(R.id.diag_export_button).isEnabled(), 8000);
        ClipboardManager cm = (ClipboardManager)
                appContext().getSystemService(Context.CLIPBOARD_SERVICE);
        assertNull(cm.getPrimaryClip());
    }

    @Test
    public void makeExportFile_destroyedWithoutFinish_discardsResult() throws Exception {
        // Same destroyed check as makeExportFile_destroyed_discardsResult,
        // but destroyed by the system without finish(): isFinishing() is
        // false, isDestroyed() is true. Copy path again (share has no
        // observable effect, A1-8).
        write(traceFile(), "10:00:01 TAG x\n");
        ActivityController<DiagnosticLogActivity> controller =
                controller(DiagnosticLogActivity.class, null);
        DiagnosticLogActivity activity = controller.create().start().resume().get();
        freezePoller(activity);
        clickExportItem(activity, 2, false);
        controller.pause().stop().destroy();
        assertFalse(activity.isFinishing());
        assertTrue(activity.isDestroyed());
        waitForBackground(
                () -> activity.findViewById(R.id.diag_export_button).isEnabled(), 8000);
        ClipboardManager cm = (ClipboardManager)
                appContext().getSystemService(Context.CLIPBOARD_SERVICE);
        assertNull(cm.getPrimaryClip());
    }

    // ---------- direct export methods ----------

    @Test
    public void shareExport_nullAndMissingFile_showsFailedToast() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        call(activity, "shareExport", new Class<?>[]{File.class}, new Object[]{null});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
        call(activity, "shareExport", new Class<?>[]{File.class},
                new Object[]{new File(activity.getCacheDir(), "nope.txt")});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void shareExport_fileOutsideProviderPaths_showsFailedToast() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File other = new File(activity.getCacheDir(), "other.txt");
        Files.write(other.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        // Not under the configured cache-path diag-export.txt: FileProvider
        // throws, the catch shows the failed toast.
        call(activity, "shareExport", new Class<?>[]{File.class}, new Object[]{other});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void shareExport_validFile_showsFailedToast_A1_8() {
        // A1-8: getUriForFile throws StringIndexOutOfBoundsException for the
        // export file itself (it equals the configured FileProvider root),
        // so even a valid export file ends in the failed toast, never a
        // chooser. See ProbeDiagTest output in the session notes for the
        // stack trace (FileProvider.java:876, real androidx code).
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File export = DiagTrace.exportFile(appContext());
        assertNotNull(export);
        call(activity, "shareExport", new Class<?>[]{File.class}, new Object[]{export});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void saveToDownloads_nullAndMissingFile_showsFailedToast() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{null});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
        call(activity, "saveToDownloads", new Class<?>[]{File.class},
                new Object[]{new File(activity.getCacheDir(), "nope.txt")});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void saveToDownloads_success_savesFileAndShowsSavedToast() throws Exception {
        android.net.Uri target =
                android.net.Uri.parse("content://media/external/downloads/1");
        registerDownloadsProvider(target, null, false);
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File export = DiagTrace.exportFile(appContext());
        assertNotNull(export);
        String expected = new String(Files.readAllBytes(export.toPath()), StandardCharsets.UTF_8);
        call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{export});
        assertEquals(activity.getString(R.string.diagnostic_log_saved),
                ShadowToast.getTextOfLatestToast());
        assertNotNull(TestDownloadsProvider.sWrittenFile);
        assertEquals(expected, new String(
                Files.readAllBytes(TestDownloadsProvider.sWrittenFile.toPath()),
                StandardCharsets.UTF_8));
    }

    @Test
    public void saveToDownloads_insertThrows_showsFailedToast() {
        registerDownloadsProvider(null, new RuntimeException("no provider"), false);
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File export = DiagTrace.exportFile(appContext());
        assertNotNull(export);
        call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{export});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void saveToDownloads_outputStreamNull_showsFailedToast() {
        android.net.Uri target =
                android.net.Uri.parse("content://media/external/downloads/1");
        registerDownloadsProvider(target, null, true);
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File export = DiagTrace.exportFile(appContext());
        assertNotNull(export);
        call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{export});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void saveToDownloads_writeFails_showsFailedToast() throws Exception {
        // openFile hands back a read-only descriptor, so the copy loop's
        // first write throws: the try-with-resources close runs on the
        // exception path and the catch shows the failed toast.
        android.net.Uri target =
                android.net.Uri.parse("content://media/external/downloads/1");
        registerDownloadsProvider(target, null, false);
        TestDownloadsProvider.sOpenFileReadOnly = true;
        try {
            DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
            File export = DiagTrace.exportFile(appContext());
            assertNotNull(export);
            call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{export});
            assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                    ShadowToast.getTextOfLatestToast());
        } finally {
            TestDownloadsProvider.sOpenFileReadOnly = false;
        }
    }

    @Test
    public void saveToDownloads_sdkBelowQ_showsUnsupportedToast() throws Exception {
        // SDK_INT is a non-constant static field, so it can be flipped via
        // reflection for this one call. A separate @Config(sdk = 28) class
        // was tried first and removed: its second sandbox in the same JVM
        // makes the next test's Robolectric setup fail
        // (DefaultNativeRuntimeLoader.maybeCopyFonts re-opens an already-open
        // zip filesystem: FileSystemAlreadyExistsException).
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File export = DiagTrace.exportFile(appContext());
        assertNotNull(export);
        Field sdkInt = android.os.Build.VERSION.class.getField("SDK_INT");
        sdkInt.setAccessible(true);
        int original = sdkInt.getInt(null);
        try {
            sdkInt.setInt(null, 28);
            call(activity, "saveToDownloads", new Class<?>[]{File.class}, new Object[]{export});
            assertEquals(activity.getString(R.string.diagnostic_log_download_unsupported),
                    ShadowToast.getTextOfLatestToast());
        } finally {
            sdkInt.setInt(null, original);
        }
    }

    @Test
    public void copyExport_nullAndMissingFile_showsFailedToast() {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        call(activity, "copyExport", new Class<?>[]{File.class}, new Object[]{null});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
        call(activity, "copyExport", new Class<?>[]{File.class},
                new Object[]{new File(activity.getCacheDir(), "nope.txt")});
        assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void copyExport_success_copiesText() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        File f = File.createTempFile("copy", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), "copy me".getBytes(StandardCharsets.UTF_8));
        call(activity, "copyExport", new Class<?>[]{File.class}, new Object[]{f});
        ClipboardManager cm = (ClipboardManager)
                appContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("copy me", clip.getItemAt(0).getText().toString());
        assertEquals(activity.getString(R.string.diagnostic_log_copied),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void copyExport_readThrows_showsFailedToast() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        File f = File.createTempFile("copy", ".txt", activity.getCacheDir());
        Files.write(f.toPath(), "copy me".getBytes(StandardCharsets.UTF_8));
        // Static mocks are thread-local: readText is called on the main
        // thread (the copy runs synchronously), so the mock is active there.
        try (MockedStatic<DiagTrace> mock = Mockito.mockStatic(DiagTrace.class)) {
            mock.when(() -> DiagTrace.readText(any(File.class), anyInt()))
                    .thenThrow(new RuntimeException("boom"));
            call(activity, "copyExport", new Class<?>[]{File.class}, new Object[]{f});
            assertEquals(activity.getString(R.string.diagnostic_log_export_failed),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    // ---------- MediaStore test provider ----------

    private void registerDownloadsProvider(android.net.Uri insertResult,
            RuntimeException insertError, boolean openFileNull) {
        TestDownloadsProvider.sInsertResult = insertResult;
        TestDownloadsProvider.sInsertError = insertError;
        TestDownloadsProvider.sOpenFileNull = openFileNull;
        TestDownloadsProvider.sOpenFileReadOnly = false;
        TestDownloadsProvider.sWrittenFile = null;
        // A provider registered without attachInfo has a null mContext and
        // openOutputStream NPEs on getUserId(); attached with a null
        // ProviderInfo its authority is null and openOutputStream fails the
        // authority check instead. Attach with authority "media".
        TestDownloadsProvider provider = new TestDownloadsProvider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "media";
        provider.attachInfo(appContext(), info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
                "media", provider);
    }

    /** Minimal provider for MediaStore.Downloads; behaviour is driven by the
     * static fields so each test controls its own insert/openFile outcome. */
    public static class TestDownloadsProvider extends android.content.ContentProvider {
        static android.net.Uri sInsertResult;
        static RuntimeException sInsertError;
        static boolean sOpenFileNull;
        static boolean sOpenFileReadOnly;
        static File sWrittenFile;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public android.database.Cursor query(android.net.Uri uri, String[] projection,
                String selection, String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override
        public String getType(android.net.Uri uri) {
            return "text/plain";
        }

        @Override
        public android.net.Uri insert(android.net.Uri uri,
                android.content.ContentValues values) {
            if (sInsertError != null) {
                throw sInsertError;
            }
            return sInsertResult;
        }

        @Override
        public int delete(android.net.Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(android.net.Uri uri, android.content.ContentValues values,
                String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public android.os.ParcelFileDescriptor openFile(android.net.Uri uri, String mode)
                throws java.io.FileNotFoundException {
            if (sOpenFileNull) {
                return null;
            }
            try {
                sWrittenFile = File.createTempFile("download-out", ".txt",
                        appContext().getCacheDir());
                int openMode = sOpenFileReadOnly
                        ? android.os.ParcelFileDescriptor.MODE_READ_ONLY
                        : android.os.ParcelFileDescriptor.MODE_READ_WRITE;
                return android.os.ParcelFileDescriptor.open(sWrittenFile, openMode);
            } catch (java.io.IOException e) {
                throw new java.io.FileNotFoundException(e.getMessage());
            }
        }
    }

    // ---------- DiagAdapter ----------

    private Object entryFull(DiagnosticLogActivity activity, int kind, String time,
            String detail, String full, boolean expanded) throws Exception {
        Object e = entryViaReflection(activity, kind, time, detail);
        set(e, "full", full);
        set(e, "expanded", expanded);
        return e;
    }

    private void setShown(DiagnosticLogActivity activity, Object... entries) {
        List<Object> all = (List<Object>) get(activity, "mAll");
        all.clear();
        for (Object e : entries) {
            all.add(e);
        }
        call(activity, "applyFilter", new Class<?>[]{}, new Object[]{});
    }

    private Object bindRow(DiagnosticLogActivity activity, int position) throws Exception {
        Object adapter = get(activity, "mAdapter");
        RecyclerView list = activity.findViewById(R.id.diag_list);
        Object holder = call(adapter, "onCreateViewHolder",
                new Class<?>[]{android.view.ViewGroup.class, int.class},
                new Object[]{list, 0});
        Class<?> holderClass = null;
        for (Class<?> c : adapter.getClass().getDeclaredClasses()) {
            if (c.getSimpleName().equals("Holder")) {
                holderClass = c;
            }
        }
        assertNotNull(holderClass);
        call(adapter, "onBindViewHolder",
                new Class<?>[]{holderClass, int.class}, new Object[]{holder, position});
        return holder;
    }

    @Test
    public void adapter_bindsTraceRow() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        setShown(activity, entryViaReflection(activity, 0, "10:00:01", "trace detail"));
        assertEquals(1, ((RecyclerView.Adapter<?>) get(activity, "mAdapter")).getItemCount());
        Object holder = bindRow(activity, 0);
        assertEquals("10:00:01", ((TextView) get(holder, "time")).getText().toString());
        assertEquals(activity.getString(R.string.diagnostic_log_filter_trace),
                ((TextView) get(holder, "tag")).getText().toString());
        assertEquals("trace detail", ((TextView) get(holder, "detail")).getText().toString());
        assertEquals(2, ((TextView) get(holder, "detail")).getMaxLines());
        assertEquals(View.GONE, ((View) get(holder, "full")).getVisibility());
    }

    @Test
    public void adapter_bindsCrashRow_expandedShowsFull() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        setShown(activity, entryFull(activity, 1, "12:34:56", "boom detail",
                "full crash text", true));
        Object holder = bindRow(activity, 0);
        assertEquals(activity.getString(R.string.diagnostic_log_filter_crash),
                ((TextView) get(holder, "tag")).getText().toString());
        assertEquals(Integer.MAX_VALUE, ((TextView) get(holder, "detail")).getMaxLines());
        assertEquals(View.VISIBLE, ((View) get(holder, "full")).getVisibility());
        assertEquals("full crash text", ((TextView) get(holder, "full")).getText().toString());
    }

    @Test
    public void adapter_bindsCrashRow_collapsedHidesFull() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        setShown(activity, entryFull(activity, 1, "12:34:56", "boom detail",
                "full crash text", false));
        Object holder = bindRow(activity, 0);
        assertEquals(2, ((TextView) get(holder, "detail")).getMaxLines());
        assertEquals(View.GONE, ((View) get(holder, "full")).getVisibility());
    }

    @Test
    public void adapter_click_togglesExpanded_noPosition() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        Object entry = entryFull(activity, 0, "10:00:01", "trace detail", "full", false);
        setShown(activity, entry);
        Object holder = bindRow(activity, 0);
        // Manually bound holder is not attached: getAdapterPosition() is
        // NO_POSITION, so the click flips the flag without notifying.
        ((RecyclerView.ViewHolder) holder).itemView.performClick();
        assertTrue((boolean) get(entry, "expanded"));
        ((RecyclerView.ViewHolder) holder).itemView.performClick();
        assertFalse((boolean) get(entry, "expanded"));
    }

    @Test
    public void adapter_click_togglesExpanded_attached() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        // Wait for the launch reload to finish first: if it lands after the
        // layout, its notifyDataSetChanged marks the holder INVALID and
        // getAdapterPosition() returns NO_POSITION, so notifyItemChanged
        // never runs. With the poller frozen no further reload can land.
        waitForBackground(() -> !(boolean) get(activity, "mReloadInFlight"), 5000);
        Object entry = entryFull(activity, 0, "10:00:01", "trace detail", "full", false);
        setShown(activity, entry);
        RecyclerView list = activity.findViewById(R.id.diag_list);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        idleMain(300);
        assertEquals(1, list.getChildCount());
        RecyclerView.ViewHolder holder =
                list.getChildViewHolder(list.getChildAt(0));
        assertEquals(0, holder.getAdapterPosition());
        list.getChildAt(0).performClick();
        assertTrue((boolean) get(entry, "expanded"));
        // notifyItemChanged rebinds with expanded=true: the detail view
        // switches to full height. The rebind needs an explicit layout pass
        // here (the traversal posted by notifyItemChanged does not run on
        // idleMain alone under Robolectric), and the bounds must change or
        // View.layout() skips onLayout entirely.
        list.layout(0, 0, 1080, 1921);
        RecyclerView.ViewHolder rebound =
                list.getChildViewHolder(list.getChildAt(0));
        assertEquals(Integer.MAX_VALUE,
                ((TextView) get(rebound, "detail")).getMaxLines());
    }

    @Test
    public void adapter_getItemCount_matchesShown() throws Exception {
        DiagnosticLogActivity activity = launch(DiagnosticLogActivity.class);
        freezePoller(activity);
        assertEquals(0, ((RecyclerView.Adapter<?>) get(activity, "mAdapter")).getItemCount());
        setShown(activity,
                entryViaReflection(activity, 0, "10:00:01", "one"),
                entryViaReflection(activity, 1, "10:00:02", "two"));
        assertEquals(2, ((RecyclerView.Adapter<?>) get(activity, "mAdapter")).getItemCount());
    }
}
