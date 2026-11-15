package com.gh4a.utils;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A 组：DiagTrace 写私有文件目录，走 Robolectric。
 * log() 是异步单线程写盘，测试里轮询等文件落盘；recordCrash/exportFile 是同步的。
 */
@RunWith(RobolectricTestRunner.class)
public class DiagTraceTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        File dir = DiagTrace.dir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        File trace = DiagTrace.traceFile(context);
        if (trace.exists()) {
            trace.delete();
        }
    }

    private void waitForTraceContent(String needle, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            File trace = DiagTrace.traceFile(context);
            if (trace.exists()) {
                String text = DiagTrace.readText(trace, 64 * 1024);
                if (text.contains(needle)) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("trace file did not contain <" + needle + "> within "
                + timeoutMs + "ms");
    }

    @Test
    public void dir_createsDiagnosticDirectory() {
        File dir = DiagTrace.dir(context);
        assertTrue(dir.isDirectory());
        assertEquals("diagnostic", dir.getName());
        assertEquals(context.getFilesDir(), dir.getParentFile());
    }

    @Test
    public void traceFile_livesInsideDiagnosticDir() {
        File trace = DiagTrace.traceFile(context);
        assertEquals("diag-trace.txt", trace.getName());
        assertEquals(DiagTrace.dir(context), trace.getParentFile());
    }

    @Test
    public void log_nullContextIsNoop() {
        DiagTrace.log(null, "TAG", "detail");
    }

    @Test
    public void log_writesTagAndDetailToTraceFile() throws Exception {
        DiagTrace.log(context, "TEST_TAG_X", "some-detail-123");
        waitForTraceContent("TEST_TAG_X some-detail-123", 5000);
    }

    @Test
    public void log_multipleLinesAllPersistedInOrder() throws Exception {
        DiagTrace.log(context, "ORDER_TAG", "first-line");
        DiagTrace.log(context, "ORDER_TAG", "second-line");
        waitForTraceContent("second-line", 5000);
        String text = DiagTrace.readText(DiagTrace.traceFile(context), 64 * 1024);
        assertTrue(text.indexOf("first-line") < text.indexOf("second-line"));
    }

    @Test
    public void recordCrash_nullContextIsNoop() {
        DiagTrace.recordCrash(null, "h", "t");
    }

    @Test
    public void recordCrash_createsFileContainingHeaderAndBody() throws Exception {
        DiagTrace.recordCrash(context, "CRASH_HEADER_X", "crash-body-abc");
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(1, crashes.size());
        assertTrue(crashes.get(0).getName().startsWith("crash-"));
        String text = DiagTrace.readText(crashes.get(0), 64 * 1024);
        assertTrue(text.startsWith("CRASH_HEADER_X"));
        assertTrue(text.contains("crash-body-abc"));
    }

    @Test
    public void recordCrash_nullTextWritesHeaderOnly() throws Exception {
        DiagTrace.recordCrash(context, "HEADER_ONLY", null);
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(1, crashes.size());
        String text = DiagTrace.readText(crashes.get(0), 64 * 1024);
        assertEquals("HEADER_ONLY\n", text);
    }

    @Test
    public void recordCrash_keepsAtMostThreeFiles() throws Exception {
        for (int i = 0; i < 5; i++) {
            DiagTrace.recordCrash(context, "H" + i, "body" + i);
            Thread.sleep(15);
        }
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(3, crashes.size());
    }

    @Test
    public void crashFiles_newestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            DiagTrace.recordCrash(context, "H" + i, "body" + i);
            Thread.sleep(15);
        }
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(3, crashes.size());
        for (int i = 0; i < crashes.size() - 1; i++) {
            assertTrue(crashes.get(i).lastModified()
                    >= crashes.get(i + 1).lastModified());
        }
    }

    @Test
    public void readText_capTruncates() throws Exception {
        File f = new File(context.getFilesDir(), "readcap.txt");
        try (FileWriter w = new FileWriter(f)) {
            w.write("0123456789abcdef");
        }
        assertEquals("01234", DiagTrace.readText(f, 5));
        assertEquals("0123456789abcdef", DiagTrace.readText(f, 1024));
    }

    @Test
    public void readText_missingFileReturnsEmpty() {
        File missing = new File(context.getFilesDir(), "does-not-exist.txt");
        assertFalse(missing.exists());
        assertEquals("", DiagTrace.readText(missing, 100));
    }

    @Test
    public void exportFile_containsTraceAndCrashSections() throws Exception {
        DiagTrace.log(context, "EXPORT_TAG", "export-detail");
        waitForTraceContent("EXPORT_TAG export-detail", 5000);
        DiagTrace.recordCrash(context, "EXPORT_CRASH_HEADER", "export-crash-body");
        File out = DiagTrace.exportFile(context);
        assertNotNull(out);
        assertTrue(out.exists());
        assertEquals("diag-export.txt", out.getName());
        // T-008: the export file lives in the diag-export/ subdir now.
        assertEquals(new File(context.getCacheDir(), "diag-export"), out.getParentFile());
        String text = DiagTrace.readText(out, 512 * 1024);
        assertTrue(text.contains("=== 诊断追踪 diag-trace.txt ==="));
        assertTrue(text.contains("EXPORT_TAG export-detail"));
        assertTrue(text.contains("=== 主题追踪 theme-trace.txt ==="));
        assertTrue(text.contains("EXPORT_CRASH_HEADER"));
        assertTrue(text.contains("export-crash-body"));
    }

    // ---- 缺口补齐 ----

    // 把 diagnostic 目录换成同名普通文件，使 dir() 返回一个非目录：
    // 其下的任何写盘/列目录操作都会失败。每个用它的测试都在 finally 里恢复。
    private File installDiagnosticBlocker() throws Exception {
        File dir = DiagTrace.dir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        assertTrue(dir.delete());
        File blocker = new File(context.getFilesDir(), "diagnostic");
        assertTrue(blocker.createNewFile());
        return blocker;
    }

    private void removeDiagnosticBlocker(File blocker) {
        if (blocker != null) {
            blocker.delete();
        }
        DiagTrace.dir(context);
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new DiagTrace());
    }

    @Test
    public void log_oversizeTraceFileDeletedBeforeRewrite() throws Exception {
        // 种子一个超 512KB 的 trace 文件：下一次 log 应先删旧文件再写新行。
        File trace = DiagTrace.traceFile(context);
        try (FileWriter w = new FileWriter(trace)) {
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < 1024; i++) {
                chunk.append("SEED_X");
            }
            for (int i = 0; i < 100; i++) {
                w.write(chunk.toString());
            }
        }
        assertTrue(trace.length() > 512 * 1024);
        DiagTrace.log(context, "OVER_TAG", "over-detail");
        waitForTraceContent("OVER_TAG over-detail", 5000);
        String text = DiagTrace.readText(trace, 64 * 1024);
        assertFalse("oversize file must be deleted, not appended to",
                text.contains("SEED_X"));
        assertTrue(trace.length() < 512 * 1024);
    }

    @Test
    public void log_writeFailureIsSwallowed() throws Exception {
        // 先写一条再等落盘，排空写盘队列，保证下面这条失败的写一定执行过。
        DiagTrace.log(context, "DRAIN_MARKER", "drain");
        waitForTraceContent("DRAIN_MARKER drain", 5000);
        File blocker = installDiagnosticBlocker();
        try {
            DiagTrace.log(context, "FAIL_TAG", "never-written");
            Thread.sleep(1000);
            assertFalse(DiagTrace.traceFile(context).exists());
        } finally {
            removeDiagnosticBlocker(blocker);
        }
    }

    @Test
    public void recordCrash_longTextTruncatedToCap() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70000; i++) {
            sb.append('x');
        }
        String longText = sb.toString();
        DiagTrace.recordCrash(context, "LONG_HDR", longText);
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(1, crashes.size());
        String text = DiagTrace.readText(crashes.get(0), 256 * 1024);
        assertEquals("LONG_HDR\n" + longText.substring(0, 64 * 1024), text);
    }

    @Test
    public void recordCrash_nonCrashFilesIgnoredByFilter() throws Exception {
        // 同名异后缀与无关文件都不应进入崩溃列表；recordCrash 与
        // crashFiles 的两处同款 filter 都要对它们求值。
        File dir = DiagTrace.dir(context);
        try (FileWriter w = new FileWriter(new File(dir, "crash-foo.dat"))) {
            w.write("not a crash file");
        }
        try (FileWriter w = new FileWriter(new File(dir, "other.txt"))) {
            w.write("unrelated");
        }
        DiagTrace.recordCrash(context, "FILTER_HDR", "filter-body");
        List<File> crashes = DiagTrace.crashFiles(context);
        assertEquals(1, crashes.size());
        assertTrue(crashes.get(0).getName().startsWith("crash-"));
        assertTrue(crashes.get(0).getName().endsWith(".txt"));
    }

    @Test
    public void crashFiles_directoryUnlistableReturnsEmpty() throws Exception {
        File blocker = installDiagnosticBlocker();
        try {
            assertTrue(DiagTrace.crashFiles(context).isEmpty());
        } finally {
            removeDiagnosticBlocker(blocker);
        }
    }

    @Test
    public void crashFiles_contextFailureReturnsEmpty() {
        Context failing = mock(Context.class);
        when(failing.getFilesDir()).thenThrow(new RuntimeException("boom"));
        assertTrue(DiagTrace.crashFiles(failing).isEmpty());
    }

    @Test
    public void recordCrash_writeFailureIsSwallowed() throws Exception {
        File blocker = installDiagnosticBlocker();
        try {
            DiagTrace.recordCrash(context, "FAIL_HDR", "never-written");
            assertTrue(DiagTrace.crashFiles(context).isEmpty());
        } finally {
            removeDiagnosticBlocker(blocker);
        }
    }

    @Test
    public void readCapped_eofBeforeFillingReturnsPartial() throws Exception {
        // length() 虚报 100 字节的 5 字节文件：第二遍 read() 返回 -1，
        // 循环经 EOF 退出而非读满。
        File real = new File(context.getFilesDir(), "short.txt");
        try (FileWriter w = new FileWriter(real)) {
            w.write("hello");
        }
        File lyingLength = new File(real.getPath()) {
            @Override
            public long length() {
                return 100;
            }
        };
        assertEquals("hello", DiagTrace.readText(lyingLength, 1024));
    }

    @Test
    public void readText_emptyFileReturnsEmpty() throws Exception {
        File empty = new File(context.getFilesDir(), "empty.txt");
        assertTrue(empty.createNewFile());
        assertEquals("", DiagTrace.readText(empty, 100));
    }

    @Test
    public void exportFile_missingTraceShowsPlaceholder() throws Exception {
        assertFalse(DiagTrace.traceFile(context).exists());
        File out = DiagTrace.exportFile(context);
        assertNotNull(out);
        String text = DiagTrace.readText(out, 512 * 1024);
        assertTrue(text.contains("(\u65e0)"));
        assertTrue(text.contains("\u6700\u65b0 0 \u6761"));
    }

    @Test
    public void exportFile_missingThemeShowsPlaceholder() throws Exception {
        // 主题段的 "(无)" 臂：必须显式删掉主题文件再导出，且按段断言，
        // 不能只断言全文含 "(无)"（trace 段的 "(无)" 会误 cover）。
        File theme = new File(context.getCacheDir(), "theme-trace.txt");
        theme.delete();
        assertFalse(DiagTrace.traceFile(context).exists());
        File out = DiagTrace.exportFile(context);
        assertNotNull(out);
        String text = DiagTrace.readText(out, 512 * 1024);
        assertTrue(text.contains("=== \u4e3b\u9898\u8ffd\u8e2a theme-trace.txt ===\n(\u65e0)"));
    }

    @Test
    public void exportFile_themeFileContentIncluded() throws Exception {
        File theme = new File(context.getCacheDir(), "theme-trace.txt");
        theme.delete();
        try (FileWriter w = new FileWriter(theme)) {
            w.write("THEME_TRACE_X");
        }
        try {
            File out = DiagTrace.exportFile(context);
            assertNotNull(out);
            String text = DiagTrace.readText(out, 512 * 1024);
            assertTrue(text.contains("THEME_TRACE_X"));
        } finally {
            theme.delete();
        }
    }

    @Test
    public void exportFile_multipleCrashesSortedAndIncluded() throws Exception {
        File dir = DiagTrace.dir(context);
        try (FileWriter w = new FileWriter(new File(dir, "crash-foo.dat"))) {
            w.write("not a crash file");
        }
        DiagTrace.recordCrash(context, "EXP_HDR_1", "exp-body-1");
        Thread.sleep(15);
        DiagTrace.recordCrash(context, "EXP_HDR_2", "exp-body-2");
        File out = DiagTrace.exportFile(context);
        assertNotNull(out);
        String text = DiagTrace.readText(out, 512 * 1024);
        assertTrue(text.contains("EXP_HDR_1"));
        assertTrue(text.contains("exp-body-1"));
        assertTrue(text.contains("EXP_HDR_2"));
        assertTrue(text.contains("exp-body-2"));
        assertFalse(text.contains("not a crash file"));
    }

    @Test
    public void exportFile_directoryUnlistableStillExports() throws Exception {
        File blocker = installDiagnosticBlocker();
        try {
            File out = DiagTrace.exportFile(context);
            assertNotNull(out);
            String text = DiagTrace.readText(out, 512 * 1024);
            assertTrue(text.contains("\u6700\u65b0 0 \u6761"));
        } finally {
            removeDiagnosticBlocker(blocker);
        }
    }

    // T-022 (fixed): DiagTrace.exportFile used to call context.getCacheDir()
    // OUTSIDE the try block, so a throwing getCacheDir() propagated instead
    // of returning null like every other failure mode. The call is now
    // inside the try, so this returns null.
    @Test
    public void exportFile_getCacheDirThrows_propagatesDefect() {
        Context failing = mock(Context.class);
        when(failing.getCacheDir()).thenThrow(new RuntimeException("boom"));
        assertNull(DiagTrace.exportFile(failing));
    }

    @Test
    public void exportFile_unwritableOutReturnsNull() throws Exception {
        // getCacheDir() returns a regular file: FileWriter cannot create
        // the export file under it, the exception lands in the catch.
        File notADir = new File(context.getFilesDir(), "not-a-dir");
        assertTrue(notADir.createNewFile());
        try {
            Context failing = mock(Context.class);
            when(failing.getCacheDir()).thenReturn(notADir);
            assertNull(DiagTrace.exportFile(failing));
        } finally {
            notADir.delete();
        }
    }
}
