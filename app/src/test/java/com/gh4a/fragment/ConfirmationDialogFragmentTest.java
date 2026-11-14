package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/** ConfirmationDialogFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class ConfirmationDialogFragmentTest {

    /** Parent fragment that records confirmations. */
    public static class CallbackParent extends Fragment
            implements ConfirmationDialogFragment.Callback {
        final List<String> tags = new ArrayList<>();
        final List<Parcelable> datas = new ArrayList<>();

        @Override
        public void onConfirmed(String tag, Parcelable data) {
            tags.add(tag);
            datas.add(data);
        }
    }

    private static class TestData implements Parcelable {
        final String value;

        TestData(String value) {
            this.value = value;
        }

        protected TestData(Parcel in) {
            value = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(value);
        }

        public static final Creator<TestData> CREATOR = new Creator<TestData>() {
            @Override
            public TestData createFromParcel(Parcel in) {
                return new TestData(in);
            }

            @Override
            public TestData[] newArray(int size) {
                return new TestData[size];
            }
        };
    }

    /** Host activity implementing Callback (for the Activity-parent show() overloads). */
    public static class CallbackActivity extends FragmentActivity
            implements ConfirmationDialogFragment.Callback {
        final List<String> tags = new ArrayList<>();
        final List<Parcelable> datas = new ArrayList<>();

        @Override
        public void onConfirmed(String tag, Parcelable data) {
            tags.add(tag);
            datas.add(data);
        }
    }

    private static CallbackActivity attachedActivity() {
        return org.robolectric.Robolectric.buildActivity(CallbackActivity.class)
                .setup().get();
    }

    private static CallbackParent attachedParent() {
        CallbackParent parent = new CallbackParent();
        FragmentTestHelper.attach(parent);
        return parent;
    }

    @Test
    public void show_stringText_showsDialogWithArgs() {
        CallbackParent parent = attachedParent();
        ConfirmationDialogFragment.show(parent, "Delete this?",
                R.string.delete, null, "del-tag");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("del-tag");
        assertNotNull(f);
        assertEquals("Delete this?", f.getArguments().getString("text"));
        assertEquals(R.string.delete,
                f.getArguments().getInt("confirmButtonTextResId"));
        assertEquals(false, f.getArguments().getBoolean("showWarning"));
        assertNull(f.getArguments().getParcelable("data"));
    }

    @Test
    public void show_resIdText_resolvesString() {
        CallbackParent parent = attachedParent();
        ConfirmationDialogFragment.show(parent, R.string.delete_comment_message,
                R.string.delete, null, "t2");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t2");
        assertNotNull(f);
        assertEquals(parent.getString(R.string.delete_comment_message),
                f.getArguments().getString("text"));
    }

    @Test
    public void show_withWarningAndData_storesBoth() {
        CallbackParent parent = attachedParent();
        TestData data = new TestData("x");
        ConfirmationDialogFragment.show(parent, "Sure?", R.string.delete,
                true, data, "t3");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t3");
        assertEquals(true, f.getArguments().getBoolean("showWarning"));
        TestData stored = f.getArguments().getParcelable("data");
        assertNotNull(stored);
        assertEquals("x", stored.value);
    }

    @Test
    public void show_resIdTextWithWarning_storesWarning() {
        CallbackParent parent = attachedParent();
        TestData data = new TestData("w");
        ConfirmationDialogFragment.show(parent, R.string.delete_comment_message,
                R.string.delete, true, data, "t-res-warn");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t-res-warn");
        assertNotNull(f);
        assertEquals(parent.getString(R.string.delete_comment_message),
                f.getArguments().getString("text"));
        assertEquals(true, f.getArguments().getBoolean("showWarning"));
        assertEquals("w", ((TestData) f.getArguments().getParcelable("data")).value);
    }

    @Test
    public void show_activityResId_showsDialogWithArgs() {
        CallbackActivity activity = attachedActivity();
        ConfirmationDialogFragment.show(activity, R.string.delete_comment_message,
                R.string.delete, null, "a1");
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("a1");
        assertNotNull(f);
        assertEquals(activity.getString(R.string.delete_comment_message),
                f.getArguments().getString("text"));
        assertEquals(false, f.getArguments().getBoolean("showWarning"));
    }

    @Test
    public void show_activityResIdWithWarning_storesWarning() {
        CallbackActivity activity = attachedActivity();
        ConfirmationDialogFragment.show(activity, R.string.delete_comment_message,
                R.string.delete, true, null, "a2");
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("a2");
        assertNotNull(f);
        assertEquals(true, f.getArguments().getBoolean("showWarning"));
    }

    @Test
    public void show_activityString_showsDialogWithArgs() {
        CallbackActivity activity = attachedActivity();
        ConfirmationDialogFragment.show(activity, "Delete?",
                R.string.delete, null, "a3");
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("a3");
        assertNotNull(f);
        assertEquals("Delete?", f.getArguments().getString("text"));
    }

    @Test
    public void show_activityStringWithWarning_storesWarning() {
        CallbackActivity activity = attachedActivity();
        TestData data = new TestData("aw");
        ConfirmationDialogFragment.show(activity, "Delete?",
                R.string.delete, true, data, "a4");
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("a4");
        assertNotNull(f);
        assertEquals(true, f.getArguments().getBoolean("showWarning"));
        assertEquals("aw", ((TestData) f.getArguments().getParcelable("data")).value);
    }

    @Test
    public void onClick_activityCallback_receivesTagAndData() {
        CallbackActivity activity = attachedActivity();
        TestData data = new TestData("ad");
        ConfirmationDialogFragment.show(activity, "Go?", R.string.delete, data, "a5");
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("a5");
        f.onClick(null, DialogInterface.BUTTON_POSITIVE);
        assertEquals(1, activity.tags.size());
        assertEquals("a5", activity.tags.get(0));
        assertEquals("ad", ((TestData) activity.datas.get(0)).value);
    }

    @Test
    public void onCreateDialog_setsMessageAndButtons() {
        CallbackParent parent = attachedParent();
        ConfirmationDialogFragment.show(parent, "Are you sure?",
                R.string.delete, null, "t4");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t4");
        Dialog dialog = f.onCreateDialog(null);
        assertTrue(dialog instanceof AlertDialog);
        AlertDialog alert = (AlertDialog) dialog;
        alert.show();
        TextView message = alert.findViewById(android.R.id.message);
        assertNotNull(message);
        assertEquals("Are you sure?", message.getText().toString());
        assertEquals(parent.getString(R.string.delete),
                alert.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
        assertEquals(parent.getString(R.string.cancel),
                alert.getButton(DialogInterface.BUTTON_NEGATIVE).getText().toString());
    }

    @Test
    public void onClick_callsTargetFragmentCallbackWithTagAndData() {
        CallbackParent parent = attachedParent();
        TestData data = new TestData("d1");
        ConfirmationDialogFragment.show(parent, "Go?", R.string.delete, data, "t5");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t5");
        f.onClick(null, DialogInterface.BUTTON_POSITIVE);
        assertEquals(1, parent.tags.size());
        assertEquals("t5", parent.tags.get(0));
        assertEquals("d1", ((TestData) parent.datas.get(0)).value);
    }

    @Test
    public void onClick_noCallbackTarget_doesNothing() {
        // Attached directly (no target fragment, plain activity is not a Callback).
        ConfirmationDialogFragment f = new ConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putString("text", "x");
        args.putInt("confirmButtonTextResId", R.string.delete);
        args.putBoolean("showWarning", false);
        f.setArguments(args);
        FragmentTestHelper.attach(f);
        f.onClick(null, DialogInterface.BUTTON_POSITIVE);
    }

    @Test
    public void onClick_nullData_passesNullToCallback() {
        CallbackParent parent = attachedParent();
        ConfirmationDialogFragment.show(parent, "Go?", R.string.delete, null, "t6");
        parent.getParentFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) parent
                .getParentFragmentManager().findFragmentByTag("t6");
        f.onClick(null, DialogInterface.BUTTON_POSITIVE);
        assertEquals(1, parent.tags.size());
        assertNull(parent.datas.get(0));
    }
}
