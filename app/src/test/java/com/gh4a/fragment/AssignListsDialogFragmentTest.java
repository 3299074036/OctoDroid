package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.widget.ListView;

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * 239：“加入列表”多选对话框（DialogFragment 版）的行为测试，重点是旋转后勾选不丢。
 */
@RunWith(RobolectricTestRunner.class)
public class AssignListsDialogFragmentTest {
    public static class HostFragment extends Fragment
            implements AssignListsDialogFragment.Callback {
        String confirmedTag;
        String confirmedRepo;
        List<String> confirmedIds;
        String createNewTag;
        String cancelledTag;

        @Override
        public void onAssignListsConfirmed(String tag, String repoFullName,
                List<String> selectedIds) {
            confirmedTag = tag;
            confirmedRepo = repoFullName;
            confirmedIds = selectedIds;
        }

        @Override
        public void onAssignListsCreateNew(String tag, String repoFullName) {
            createNewTag = tag;
        }

        @Override
        public void onAssignListsCancelled(String tag, String repoFullName) {
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

    private AssignListsDialogFragment showDialog() {
        AssignListsDialogFragment.show(mHost.getParentFragmentManager(), "test",
                Arrays.asList("A", "B", "C"), Arrays.asList("id-a", "id-b", "id-c"),
                new HashSet<>(Arrays.asList("id-b")), null, "o/r");
        idle();
        AssignListsDialogFragment f = (AssignListsDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    private void idle() {
        mActivity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private ListView listView(AssignListsDialogFragment f) {
        return ((AlertDialog) f.getDialog()).findViewById(com.gh4a.R.id.list);
    }

    @Test
    public void confirmReturnsCheckedIdsPreservingPreChecked() {
        AssignListsDialogFragment f = showDialog();
        ListView list = listView(f);
        assertTrue(list.isItemChecked(1));
        list.setItemChecked(0, true);
        list.getOnItemClickListener().onItemClick(list, null, 0, 0);
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("test", mHost.confirmedTag);
        assertEquals("o/r", mHost.confirmedRepo);
        assertEquals(Arrays.asList("id-a", "id-b"), mHost.confirmedIds);
    }

    @Test
    public void createNewNotifies() {
        AssignListsDialogFragment f = showDialog();
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        assertEquals("test", mHost.createNewTag);
        assertNull(mHost.confirmedIds);
    }

    @Test
    public void cancelNotifiesCancelled() {
        AssignListsDialogFragment f = showDialog();
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertEquals("test", mHost.cancelledTag);
        assertNull(mHost.confirmedIds);
    }

    @Test
    public void rotationPreservesCheckedItems() {
        AssignListsDialogFragment f = showDialog();
        ListView list = listView(f);
        list.setItemChecked(2, true);
        list.getOnItemClickListener().onItemClick(list, null, 2, 2);
        mController.recreate();
        mActivity = mController.get();
        idle();
        HostFragment host = (HostFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag("host");
        assertNotNull(host);
        AssignListsDialogFragment restored = (AssignListsDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(restored);
        assertNotNull(restored.getDialog());
        ListView restoredList = listView(restored);
        assertTrue(restoredList.isItemChecked(1));
        assertTrue(restoredList.isItemChecked(2));
        ((AlertDialog) restored.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
                idle();
        assertEquals(Arrays.asList("id-b", "id-c"), host.confirmedIds);
    }
}
