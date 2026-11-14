package com.gh4a.utils;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：SingleChoiceDialogHelper。Robolectric 下用 FragmentActivity
 *（应用主题为 MaterialComponents/AppCompat 系，AlertDialog 可正常创建）。
 * 注意：androidx AlertDialog 不走 ShadowAlertDialog（它只管
 * android.app.AlertDialog），故用 ShadowDialog.getShownDialogs() 取
 * 已显示对话框、真实 ListView.performItemClick() 点选项。
 */
@RunWith(RobolectricTestRunner.class)
public class SingleChoiceDialogHelperTest {

    private final FragmentActivity mActivity =
            Robolectric.buildActivity(FragmentActivity.class).setup().get();

    private static class PickRecorder implements SingleChoiceDialogHelper.OnPick {
        int picks;
        int lastWhich = -1;

        @Override
        public void onPick(int which) {
            picks++;
            lastWhich = which;
        }
    }

    private AlertDialog shownDialog() {
        // getShownDialogs() 会保留已 dismiss 的对话框（各测一次 show 就会多一个），
        // 故只取当前正在显示的那一个；同一时刻应恰好一个。
        List<android.app.Dialog> showing = new ArrayList<>();
        for (android.app.Dialog d : ShadowDialog.getShownDialogs()) {
            if (d.isShowing()) {
                showing.add(d);
            }
        }
        assertEquals(1, showing.size());
        return (AlertDialog) showing.get(0);
    }

    private static String dialogTitle(AlertDialog dialog) {
        // AppCompat 的 Builder.setTitle 不走 Dialog.setTitle，ShadowDialog
        // 的 getTitle() 读不到（恒为空串）；直接读标题 TextView。
        android.widget.TextView titleView =
                dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        assertNotNull(titleView);
        return titleView.getText().toString();
    }

    private static void clickItem(AlertDialog dialog, int which) {
        dialog.getListView().performItemClick(null, which,
                dialog.getListView().getAdapter().getItemId(which));
    }

    @Test
    public void show_pickCallsBackAndDismisses() {
        PickRecorder recorder = new PickRecorder();
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b", "c"}, 0, recorder);
        AlertDialog dialog = shownDialog();
        assertEquals("Choose", dialogTitle(dialog));
        assertTrue(dialog.isShowing());

        clickItem(dialog, 2);
        assertEquals(1, recorder.picks);
        assertEquals(2, recorder.lastWhich);
        assertFalse(dialog.isShowing());
    }

    @Test
    public void show_titleResOverload() {
        PickRecorder recorder = new PickRecorder();
        SingleChoiceDialogHelper.show(mActivity, android.R.string.ok,
                new CharSequence[]{"x", "y"}, 1, recorder);
        AlertDialog dialog = shownDialog();
        assertEquals("OK", dialogTitle(dialog));
        clickItem(dialog, 0);
        assertEquals(0, recorder.lastWhich);
    }

    @Test
    public void show_checkedItemIsPreselected() {
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b", "c"}, 1, new PickRecorder());
        AlertDialog dialog = shownDialog();
        assertEquals(1, dialog.getListView().getCheckedItemPosition());
        assertEquals(3, dialog.getListView().getAdapter().getCount());
    }

    @Test
    public void show_nullOnPickDoesNotCrash() {
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b"}, 0, null);
        AlertDialog dialog = shownDialog();
        clickItem(dialog, 1);
        assertFalse(dialog.isShowing());
    }

    @Test
    public void show_cancelButtonDismissesWithoutCallback() {
        PickRecorder recorder = new PickRecorder();
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b"}, 0, recorder);
        AlertDialog dialog = shownDialog();
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        // 取消按钮的 dismiss 是经 Handler 发消息（AppCompat AlertController），
        // Robolectric 的主线程默认暂停，须跑空消息队列才会真正关表。
        ShadowLooper.idleMainLooper();
        assertEquals(0, recorder.picks);
        assertFalse(dialog.isShowing());
    }

    @Test
    public void show_eachPickFiresExactlyOnce() {
        PickRecorder recorder = new PickRecorder();
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b"}, 0, recorder);
        clickItem(shownDialog(), 1);
        // 对话框已关，再 show 一次可重新选
        SingleChoiceDialogHelper.show(mActivity, "Choose",
                new CharSequence[]{"a", "b"}, 0, recorder);
        clickItem(shownDialog(), 0);
        assertEquals(2, recorder.picks);
        assertEquals(0, recorder.lastWhich);
    }
}
