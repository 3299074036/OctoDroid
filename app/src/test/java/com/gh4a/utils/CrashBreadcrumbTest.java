package com.gh4a.utils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 组：CrashBreadcrumb 是纯静态计数器 + 栈取证，无 Android 依赖，走纯 JVM 测试。
 * 计数器是进程级静态状态，@Before 先排空，避免测试间互相干扰。
 */
public class CrashBreadcrumbTest {

    @Before
    public void drainDialogCounter() {
        while (CrashBreadcrumb.isAnyDialogShowing()) {
            CrashBreadcrumb.noteDialogDismissed();
        }
    }

    @Test
    public void isAnyDialogShowing_initiallyFalse() {
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void noteDialogShown_setsShowing() {
        CrashBreadcrumb.noteDialogShown();
        assertTrue(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void noteDialogDismissed_afterTwoShownOneDismissed_stillShowing() {
        CrashBreadcrumb.noteDialogShown();
        CrashBreadcrumb.noteDialogShown();
        CrashBreadcrumb.noteDialogDismissed();
        assertTrue(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void noteDialogDismissed_balanced_returnsToFalse() {
        CrashBreadcrumb.noteDialogShown();
        CrashBreadcrumb.noteDialogShown();
        CrashBreadcrumb.noteDialogDismissed();
        CrashBreadcrumb.noteDialogDismissed();
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void noteDialogDismissed_belowZero_clampsAtZero() {
        CrashBreadcrumb.noteDialogDismissed();
        CrashBreadcrumb.noteDialogDismissed();
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
        // 钳住后一次 shown 仍只需一次 dismissed 即可归零
        CrashBreadcrumb.noteDialogShown();
        CrashBreadcrumb.noteDialogDismissed();
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void noteFinish_recordsActivityAndCallerMethod() {
        CrashBreadcrumb.noteFinish("SettingsActivity");
        assertEquals("SettingsActivity.noteFinish_recordsActivityAndCallerMethod",
                CrashBreadcrumb.getLastFinish());
    }

    @Test
    public void noteFinish_secondCallOverwritesFirst() {
        CrashBreadcrumb.noteFinish("FirstActivity");
        CrashBreadcrumb.noteFinish("SecondActivity");
        assertEquals("SecondActivity.noteFinish_secondCallOverwritesFirst",
                CrashBreadcrumb.getLastFinish());
    }

    @Test
    public void noteFinish_nullActivityName_recordsLiteralNull() {
        CrashBreadcrumb.noteFinish(null);
        assertEquals("null.noteFinish_nullActivityName_recordsLiteralNull",
                CrashBreadcrumb.getLastFinish());
    }
}
