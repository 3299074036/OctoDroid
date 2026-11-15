package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
