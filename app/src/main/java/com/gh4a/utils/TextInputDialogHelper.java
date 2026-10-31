package com.gh4a.utils;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;

/**
 * 0.1.20 D-08: 文本输入对话框助手。此前每处约 12–15 行重复（建 EditText、
 * 设内边距、setTitle、去空白、确认处理），仅确认后动作不同：
 * StarMergedFragment.showCreateOfficialListDialog、
 * StarMergedFragment.showCreateAndAssignDialog、
 * StarGroupFragment.showNameInputDialog。现统一为本助手：单行文本输入、
 * 16dp 内边距、确认传去空白后的文本（空串亦原样传回，由调用方决定
 * 是否忽略，与原先“空则 return”的调用方逻辑一致）、取消按钮保留。
 */
public class TextInputDialogHelper {
    /** 确认回调（text 已去空白）。 */
    public interface OnConfirm {
        void onConfirm(String text);
    }

    public static void show(Context context, int titleRes, String initial, OnConfirm onConfirm) {
        show(context, context.getString(titleRes), initial, onConfirm);
    }

    public static void show(Context context, CharSequence title, String initial,
            OnConfirm onConfirm) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        if (initial != null) {
            input.setText(initial);
        }
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (onConfirm != null) {
                        onConfirm.onConfirm(input.getText().toString().trim());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
