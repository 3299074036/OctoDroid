package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import java.util.Arrays;
import java.util.List;

/**
 * 239：新建列表对话框（DialogFragment 版）的行为测试，重点是旋转后输入不丢。
 */
@RunWith(RobolectricTestRunner.class)
public class CreateListNameDialogFragmentTest {
    public static class HostFragment extends Fragment
            implements CreateListNameDialogFragment.Callback {
        String confirmedTag;
        String confirmedPassthrough;
        String confirmedName;
        boolean confirmedPrivate;
        String cancelledTag;

        @Override
        public void onCreateListNameConfirmed(String tag, String passthrough,
                String name, boolean isPrivate) {
            confirmedTag = tag;
            confirmedPassthrough = passthrough;
            confirmedName = name;
            confirmedPrivate = isPrivate;
        }

        @Override
        public void onCreateListNameCancelled(String tag, String passthrough) {
            cancelledTag = tag;
        }
    }

    private ActivityController<FragmentActivity> mController;
    private FragmentActivity mActivity;
    private HostFragment mHost;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(FragmentActivity.class).setup();
        mActivity = mController.get();
        mHost = new HostFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(mHost, "host").commitNow();
    }

    private CreateListNameDialogFragment showDialog() {
        CreateListNameDialogFragment.show(mHost.getParentFragmentManager(), "test",
                Arrays.asList("Existing", "Other"), "pass");
        idle();
        FragmentManager fm = mActivity.getSupportFragmentManager();
        CreateListNameDialogFragment f =
                (CreateListNameDialogFragment) fm.findFragmentByTag("test");
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    private void idle() {
        mActivity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private EditText nameInput(CreateListNameDialogFragment f) {
        return ((AlertDialog) f.getDialog()).findViewById(com.gh4a.R.id.et_list_name);
    }

    @Test
    public void confirmValidCallsBackWithTrimmedNameAndPrivateFlag() {
        CreateListNameDialogFragment f = showDialog();
        EditText input = nameInput(f);
        input.setText("  New List  ");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("test", mHost.confirmedTag);
        assertEquals("pass", mHost.confirmedPassthrough);
        assertEquals("New List", mHost.confirmedName);
        assertFalse(mHost.confirmedPrivate);
    }

    @Test
    public void emptyNameShowsToastAndStaysOpen() {
        CreateListNameDialogFragment f = showDialog();
        nameInput(f).setText("   ");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_name_empty),
                ShadowToast.getTextOfLatestToast());
        assertNull(mHost.confirmedName);
        assertTrue(((AlertDialog) f.getDialog()).isShowing());
    }

    @Test
    public void duplicateNameCaseInsensitiveShowsToastAndStaysOpen() {
        CreateListNameDialogFragment f = showDialog();
        nameInput(f).setText("eXiStInG");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_name_dup),
                ShadowToast.getTextOfLatestToast());
        assertNull(mHost.confirmedName);
    }

    @Test
    public void cancelNotifiesCancelled() {
        CreateListNameDialogFragment f = showDialog();
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertEquals("test", mHost.cancelledTag);
        assertNull(mHost.confirmedName);
    }

    @Test
    public void rotationPreservesTypedName() {
        CreateListNameDialogFragment f = showDialog();
        nameInput(f).setText("Typed Before Rotate");
        mController.recreate();
        mActivity = mController.get();
        idle();
        HostFragment host = (HostFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag("host");
        assertNotNull(host);
        CreateListNameDialogFragment restored = (CreateListNameDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(restored);
        assertNotNull(restored.getDialog());
        assertEquals("Typed Before Rotate",
                ((EditText) ((AlertDialog) restored.getDialog())
                        .findViewById(com.gh4a.R.id.et_list_name)).getText().toString());
        // 恢复后的确认仍回调到新的宿主 Fragment
        ((AlertDialog) restored.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
                idle();
        assertEquals("Typed Before Rotate", host.confirmedName);
    }

    private CreateListNameDialogFragment showDialogWith(List<String> existing) {
        CreateListNameDialogFragment.show(mHost.getParentFragmentManager(), "test",
                existing, "pass");
        idle();
        FragmentManager fm = mActivity.getSupportFragmentManager();
        CreateListNameDialogFragment f =
                (CreateListNameDialogFragment) fm.findFragmentByTag("test");
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    private CreateListNameDialogFragment showDialogNoHost(String tag) {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        CreateListNameDialogFragment.show(activity.getSupportFragmentManager(), tag,
                Arrays.asList("Existing"), "pass");
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CreateListNameDialogFragment f = (CreateListNameDialogFragment)
                activity.getSupportFragmentManager().findFragmentByTag(tag);
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    @Test
    public void showWithNullExistingNames_allowsAnyName() {
        // L52：existingNames == null → 空列表兜底。
        CreateListNameDialogFragment f = showDialogWith(null);
        nameInput(f).setText("Brand New");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("Brand New", mHost.confirmedName);
    }

    @Test
    public void onCreateDialog_emptySavedState_skipsRestore() {
        // L71：savedInstanceState 非空但无 text → 不恢复文本。
        // 直接调用 onCreateDialog 返回的对话框尚未 show，视图层级未挂载，
        // findViewById 取不到输入框；先 show 再查（框架真实流程也是如此）。
        CreateListNameDialogFragment f = showDialog();
        android.app.Dialog dialog = f.onCreateDialog(new Bundle());
        dialog.show();
        EditText input = ((AlertDialog) dialog)
                .findViewById(com.gh4a.R.id.et_list_name);
        assertNotNull(input);
        assertEquals("", input.getText().toString());
        dialog.dismiss();
    }

    @Test
    public void confirmWithoutExistingNamesArg_skipsDuplicateCheck() {
        // L92/L104/L141/L146：args 里没有 existingNames 且无 Callback 宿主。
        CreateListNameDialogFragment f = new CreateListNameDialogFragment();
        Bundle args = new Bundle();
        args.putString("passthrough", "pass");
        f.setArguments(args);
        FragmentActivity activity = FragmentTestHelper.attach(f);
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) f.getDialog();
        assertNotNull(dialog);
        EditText input = dialog.findViewById(com.gh4a.R.id.et_list_name);
        assertNotNull(input);
        input.setText("No Dup Check");
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertFalse(dialog.isShowing());
        assertNull(mHost.confirmedName);
    }

    @Test
    public void cancelWithHost_notifiesCancelled() {
        // L122：有 Callback 宿主时取消走 cb != null 真臂，通知宿主。
        // 与 cancelWithoutHost_doesNothing 互补（那个覆假臂）。
        CreateListNameDialogFragment f = showDialog();
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertEquals("test", mHost.cancelledTag);
    }

    @Test
    public void cancelWithoutHost_doesNothing() {
        // L122：无 Callback 宿主时取消不回调、不崩溃。
        // 注意：取消经 DialogFragment 的 OnCancelListener 分发，不 idle 则
        // notifyCancelled 根本不跑、断言空转通过；必须 idle 后再断言。
        CreateListNameDialogFragment f = showDialogNoHost("nohost-cancel");
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertNull(mHost.cancelledTag);
    }

    @Test
    public void findCallback_withoutActivity_returnsNull() throws Exception {
        // L136/L137：未挂载时 getActivity() 为 null。
        CreateListNameDialogFragment f = new CreateListNameDialogFragment();
        java.lang.reflect.Method m =
                CreateListNameDialogFragment.class.getDeclaredMethod("findCallback");
        m.setAccessible(true);
        assertNull(m.invoke(f));
    }

    @Test
    public void onSaveInstanceState_withoutViews_storesNothing() {
        // L152/L155：mNameInput/mPrivateSwitch 为 null 时不写 state。
        CreateListNameDialogFragment f = new CreateListNameDialogFragment();
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertFalse(out.containsKey("text"));
        assertFalse(out.containsKey("private"));
    }
}
