package com.gh4a.utils;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * 0.1.20 D-07: lambda 友好的单选对话框助手。此前我页重写了单选
 *（AlertDialog.Builder.setSingleChoiceItems + 关闭 + 取消按钮）而不用
 * 现成 SingleChoiceDialogFragment，只因想要 lambda 而非 Activity 实现回调；
 * 同款内联单选还出现在 StarMergedFragment 的本地分组指派对话框。
 * 现统一为本助手：点选即关表并回调，取消按钮保留；调用方只传 lambda。
 */
public class SingleChoiceDialogHelper {
    /** 点选回调（which 为选项下标，助手已先关表）。 */
    public interface OnPick {
        void onPick(int which);
    }

    public static void show(Context context, int titleRes, CharSequence[] items,
            int checked, OnPick onPick) {
        show(context, context.getString(titleRes), items, checked, onPick);
    }

    public static void show(Context context, CharSequence title, CharSequence[] items,
            int checked, OnPick onPick) {
        androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    if (onPick != null) {
                        onPick.onPick(which);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        // 0.1.23: report to the crash breadcrumb counter (dismiss fires the
        // listener exactly once per show, including the pick-then-dismiss path).
        com.gh4a.utils.CrashBreadcrumb.noteDialogShown();
        dialog.setOnDismissListener(d -> com.gh4a.utils.CrashBreadcrumb.noteDialogDismissed());
    }
}
