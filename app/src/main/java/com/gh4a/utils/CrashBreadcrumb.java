package com.gh4a.utils;

/**
 * 0.1.23: minimal dialog-visibility counter for crash breadcrumbs.
 * Only the app's central dialog helpers report here (SingleChoiceDialogHelper,
 * BottomSheetListBuilder, MirrorSourceDialog, the crash dialog itself); other
 * ad-hoc AlertDialogs are NOT tracked, so isAnyDialogShowing() is a lower
 * bound, not a guarantee of "no dialog open". Counter only — no content,
 * no names, nothing user-identifying — so it is safe to include in the
 * crash report header.
 */
public class CrashBreadcrumb {
    private static volatile int sShownDialogs = 0;

    // 0.1.24: 最近一次 App 内 finish() 调用（BaseActivity.finish() 覆写处
    // 记录）。只记 Activity 简名＋调用栈顶 App 方法名，不记参数、内容、
    // 凭据与隐私信息。系统清栈（CLEAR_TOP/CLEAR_TASK）不走 finish() 方法，
    // 故 "none" 且 destroyingFinishing=true 时即指向系统侧。
    private static volatile String sLastFinish = "none";

    public static void noteFinish(String activitySimpleName) {
        String caller = "?";
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cls = e.getClassName();
            // 跳过取证自身与 finish() 覆写帧，取其后第一个 App 包帧。
            if (cls.equals(CrashBreadcrumb.class.getName())) {
                continue;
            }
            if (cls.equals("com.gh4a.BaseActivity") && e.getMethodName().equals("finish")) {
                continue;
            }
            if (cls.startsWith("com.gh4a.")) {
                caller = e.getMethodName();
                break;
            }
        }
        sLastFinish = activitySimpleName + "." + caller;
    }

    public static String getLastFinish() {
        return sLastFinish;
    }

    public static void noteDialogShown() {
        sShownDialogs++;
    }

    public static void noteDialogDismissed() {
        if (sShownDialogs > 0) {
            sShownDialogs--;
        }
    }

    public static boolean isAnyDialogShowing() {
        return sShownDialogs > 0;
    }
}
