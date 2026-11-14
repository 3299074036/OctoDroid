package com.gh4a.utils;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import org.robolectric.RuntimeEnvironment;

import com.gh4a.fragment.SettingsFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ThemeTrace: pure helpers run synchronously; log()/logState() write on a
 * single background thread, so file assertions poll briefly for the line.
 */
@RunWith(RobolectricTestRunner.class)
public class ThemeTraceTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void delegateLabel_allModes() {
        assertEquals("YES", ThemeTrace.delegateLabel(AppCompatDelegate.MODE_NIGHT_YES));
        assertEquals("NO", ThemeTrace.delegateLabel(AppCompatDelegate.MODE_NIGHT_NO));
        assertEquals("FOLLOW_SYSTEM",
                ThemeTrace.delegateLabel(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        assertEquals("AUTO_BATTERY",
                ThemeTrace.delegateLabel(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY));
    }

    @Test
    public void delegateLabel_unknownReturnsNumber() {
        assertEquals("99", ThemeTrace.delegateLabel(99));
        assertEquals("7", ThemeTrace.delegateLabel(7));
    }

    @Test
    public void instance_nullReturnsQuestionMark() {
        assertEquals("?", ThemeTrace.instance(null));
    }

    @Test
    public void instance_containsClassNameAndIdentityHash() {
        Object o = new Object();
        String label = ThemeTrace.instance(o);
        assertEquals("Object@" + Integer.toHexString(System.identityHashCode(o)), label);
    }

    @Test
    public void instance_sameObjectSameLabel() {
        ThemeTraceTest self = this;
        assertEquals(ThemeTrace.instance(self), ThemeTrace.instance(self));
    }

    @Test
    public void file_isThemeTraceTxtInCacheDir() {
        File f = ThemeTrace.file(context());
        assertEquals("theme-trace.txt", f.getName());
        assertEquals(context().getCacheDir(), f.getParentFile());
    }

    @Test
    public void uiModeLabel_knownValueForDefaultConfig() {
        String label = ThemeTrace.uiModeLabel(context());
        int mask = context().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (mask == Configuration.UI_MODE_NIGHT_YES) {
            assertEquals("YES", label);
        } else if (mask == Configuration.UI_MODE_NIGHT_NO) {
            assertEquals("NO", label);
        } else {
            assertEquals("UNKNOWN", label);
        }
    }

    @Test
    public void uiModeLabel_nullContextReturnsQuestionMark() {
        assertEquals("?", ThemeTrace.uiModeLabel(null));
    }

    @Test
    public void state_containsAllFourFields() {
        Context ctx = context();
        ctx.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(DarkModeScheduler.KEY_DARK_MODE, "system")
                .putInt(SettingsFragment.KEY_THEME, 3)
                .apply();
        String state = ThemeTrace.state(ctx);
        assertTrue(state.contains("mode=system"));
        assertTrue(state.contains("theme=3"));
        assertTrue(state.contains("delegate="));
        assertTrue(state.contains("page="));
    }

    @Test
    public void state_missingPrefsUsesDefaults() {
        String state = ThemeTrace.state(context());
        assertTrue(state.startsWith("mode=? theme=-1 delegate="));
    }

    @Test
    public void state_nullContextReturnsFallback() {
        assertEquals("state=?", ThemeTrace.state(null));
    }

    @Test
    public void log_nullContextIsNoop() {
        ThemeTrace.log(null, "tag", "detail");
    }

    @Test
    public void log_writesTagAndDetailLine() throws Exception {
        File f = ThemeTrace.file(context());
        if (f.exists()) {
            assertTrue(f.delete());
        }
        ThemeTrace.log(context(), "TEST-TAG", "some detail here");
        String content = null;
        for (int i = 0; i < 100; i++) {
            if (f.exists() && f.length() > 0) {
                content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                break;
            }
            Thread.sleep(50);
        }
        assertTrue("log line never appeared in " + f, content != null);
        assertTrue(content.contains("TEST-TAG some detail here"));
        // Timestamp prefix: HH:mm:ss.SSS
        assertTrue(content.matches("(?s)^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*"));
    }

    @Test
    public void log_appendsSecondLine() throws Exception {
        File f = ThemeTrace.file(context());
        if (f.exists()) {
            assertTrue(f.delete());
        }
        ThemeTrace.log(context(), "FIRST", "one");
        ThemeTrace.log(context(), "SECOND", "two");
        String content = null;
        for (int i = 0; i < 100; i++) {
            if (f.exists()) {
                String c = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (c.contains("FIRST one") && c.contains("SECOND two")) {
                    content = c;
                    break;
                }
            }
            Thread.sleep(50);
        }
        assertTrue("both log lines never appeared", content != null);
        assertTrue(content.indexOf("FIRST") < content.indexOf("SECOND"));
    }

    @Test
    public void logState_writesStateLine() throws Exception {
        File f = ThemeTrace.file(context());
        if (f.exists()) {
            assertTrue(f.delete());
        }
        ThemeTrace.logState(context(), "STATE-TAG");
        String content = null;
        for (int i = 0; i < 100; i++) {
            if (f.exists() && f.length() > 0) {
                content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                break;
            }
            Thread.sleep(50);
        }
        assertTrue("logState line never appeared", content != null);
        assertTrue(content.contains("STATE-TAG"));
        assertTrue(content.contains("mode="));
    }
}
