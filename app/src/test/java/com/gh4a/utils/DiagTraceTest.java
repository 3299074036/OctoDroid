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
import static org.junit.Assert.assertTrue;

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
        assertEquals(context.getCacheDir(), out.getParentFile());
        String text = DiagTrace.readText(out, 512 * 1024);
        assertTrue(text.contains("=== 诊断追踪 diag-trace.txt ==="));
        assertTrue(text.contains("EXPORT_TAG export-detail"));
        assertTrue(text.contains("=== 主题追踪 theme-trace.txt ==="));
        assertTrue(text.contains("EXPORT_CRASH_HEADER"));
        assertTrue(text.contains("export-crash-body"));
    }
}
