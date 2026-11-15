package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

/**
 * 239：单行文本输入对话框（DialogFragment 版）的行为测试，重点是旋转后输入不丢。
 */
@RunWith(RobolectricTestRunner.class)
public class TextInputDialogFragmentTest {
    public static class HostFragment extends Fragment
            implements TextInputDialogFragment.Callback {
        String confirmedTag;
        String confirmedPassthrough;
        String confirmedInitial;
        String confirmedText;

        @Override
        public void onTextInputConfirmed(String tag, String passthrough,
                String initial, String text) {
            confirmedTag = tag;
            confirmedPassthrough = passthrough;
            confirmedInitial = initial;
            confirmedText = text;
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

    private TextInputDialogFragment showDialog() {
        TextInputDialogFragment.show(mHost.getParentFragmentManager(), "test",
                "Rename", "Old Name", "list-1");
        idle();
        TextInputDialogFragment f = (TextInputDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    private void idle() {
        mActivity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private EditText input(TextInputDialogFragment f) {
        return ((AlertDialog) f.getDialog()).findViewById(android.R.id.edit);
    }

    @Test
    public void confirmReturnsTrimmedText() {
        TextInputDialogFragment f = showDialog();
        EditText edit = input(f);
        edit.setText("  New Name  ");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("test", mHost.confirmedTag);
        assertEquals("list-1", mHost.confirmedPassthrough);
        assertEquals("Old Name", mHost.confirmedInitial);
        assertEquals("New Name", mHost.confirmedText);
    }

    @Test
    public void cancelDoesNotCallBack() {
        TextInputDialogFragment f = showDialog();
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertNull(mHost.confirmedText);
    }

    @Test
    public void rotationPreservesTypedText() {
        TextInputDialogFragment f = showDialog();
        input(f).setText("Typed Before Rotate");
        mController.recreate();
        mActivity = mController.get();
        idle();
        HostFragment host = (HostFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag("host");
        assertNotNull(host);
        TextInputDialogFragment restored = (TextInputDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(restored);
        assertNotNull(restored.getDialog());
        assertEquals("Typed Before Rotate", input(restored).getText().toString());
        ((AlertDialog) restored.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
                idle();
        assertEquals("Typed Before Rotate", host.confirmedText);
    }
}
