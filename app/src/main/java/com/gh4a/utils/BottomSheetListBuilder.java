package com.gh4a.utils;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * 0.1.20 D-05: 三处底部动作表的共用骨架（此前每处约 20 行重复）：
 * HomeActivity.showAccountSwitcher、SearchFragment.showTypeSheet、
 * SearchFragment.showSortSheet。骨架 = 竖向 LinearLayout、16dp 内边距、
 * 16sp 粗体标题、pad/2 行、取消行。仅用于上述三处；我页行是另一套
 *（XML 展开 row_me_setting 的 addRow/addValueRow/addSwitchRow），保持独立。
 *
 * 行分两类：addTextRow（纯文字行，Home 账号表用）与 addRow（调用方自建
 * 的富行，如类型表的图标+双行文字、排序表的单选+标签，构造器只负责
 * 挂接“点按先关表再执行”与 selectable 背景）。点按动作一律先 dismiss
 * 再执行，与三处原先行为一致。
 */
public class BottomSheetListBuilder {
    private final Context mContext;
    private final BottomSheetDialog mSheet;
    private final LinearLayout mContent;
    private final int mPad;

    public BottomSheetListBuilder(Context context, CharSequence title) {
        mContext = context;
        mSheet = new BottomSheetDialog(context);
        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mPad = (int) (16 * context.getResources().getDisplayMetrics().density);
        mContent.setPadding(mPad, mPad, mPad, mPad);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, mPad / 2);
        mContent.addView(titleView);
    }

    /** 16dp 内边距（像素值），供调用方自建富行时复用同一 pad。 */
    public int pad() {
        return mPad;
    }

    /** 底表本身（极少数调用方仍需直接操作时用）。 */
    public BottomSheetDialog sheet() {
        return mSheet;
    }

    /** 纯文字行：15sp、上下 pad/2；action 为 null 时不可点（当前账号行）。 */
    public TextView addTextRow(CharSequence text, View.OnClickListener action) {
        TextView row = new TextView(mContext);
        row.setText(text);
        row.setTextSize(15);
        row.setPadding(0, mPad / 2, 0, mPad / 2);
        if (action != null) {
            UiUtils.setSelectableBackground(row);
            row.setOnClickListener(v -> {
                mSheet.dismiss();
                action.onClick(v);
            });
        }
        mContent.addView(row);
        return row;
    }

    /** 调用方自建的富行：挂 selectable 背景与“先关表再执行”的点按，加入内容区。 */
    public void addRow(View row, View.OnClickListener action) {
        if (action != null) {
            UiUtils.setSelectableBackground(row);
            row.setOnClickListener(v -> {
                mSheet.dismiss();
                action.onClick(v);
            });
        }
        mContent.addView(row);
    }

    /** 取消行（Search 两表原先的 addSheetCancel：15sp、上 pad、下 pad/2）。 */
    public void addCancel() {
        TextView cancel = new TextView(mContext);
        cancel.setText(android.R.string.cancel);
        cancel.setTextSize(15);
        cancel.setPadding(0, mPad, 0, mPad / 2);
        UiUtils.setSelectableBackground(cancel);
        cancel.setOnClickListener(v -> mSheet.dismiss());
        mContent.addView(cancel);
    }

    public void show() {
        mSheet.setContentView(mContent);
        mSheet.show();
        // 0.1.23: crash breadcrumb counter (see CrashBreadcrumb for coverage).
        com.gh4a.utils.CrashBreadcrumb.noteDialogShown();
        mSheet.setOnDismissListener(d -> com.gh4a.utils.CrashBreadcrumb.noteDialogDismissed());
    }
}
