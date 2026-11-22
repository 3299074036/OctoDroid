package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
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

import java.util.ArrayList;
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

    private AssignListsDialogFragment showDialogWith(java.util.Set<String> preChecked,
            String message) {
        AssignListsDialogFragment.show(mHost.getParentFragmentManager(), "test",
                Arrays.asList("A", "B", "C"), Arrays.asList("id-a", "id-b", "id-c"),
                preChecked, message, "o/r");
        idle();
        AssignListsDialogFragment f = (AssignListsDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("test");
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    private AssignListsDialogFragment showDialogNoHost(String tag) {
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        AssignListsDialogFragment.show(activity.getSupportFragmentManager(), tag,
                Arrays.asList("A", "B"), Arrays.asList("id-a", "id-b"),
                new HashSet<>(Arrays.asList("id-a")), null, "o/r");
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        AssignListsDialogFragment f = (AssignListsDialogFragment)
                activity.getSupportFragmentManager().findFragmentByTag(tag);
        assertNotNull(f);
        assertNotNull(f.getDialog());
        return f;
    }

    @Test
    public void showWithNullPreCheckedIds_startsUnchecked() {
        // L55：preCheckedIds == null → 空列表兜底，不预勾选。
        AssignListsDialogFragment f = showDialogWith(null, "pick lists");
        ListView list = listView(f);
        assertFalse(list.isItemChecked(0));
        assertFalse(list.isItemChecked(1));
        assertFalse(list.isItemChecked(2));
    }

    @Test
    public void onCreateDialog_savedStateWrongLength_reinitializesFromPreChecked() {
        // L74：savedInstanceState 里的 checked 长度与列表不一致 → 按 preChecked 重建。
        AssignListsDialogFragment f = new AssignListsDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("names", new ArrayList<>(Arrays.asList("A", "B", "C")));
        args.putStringArrayList("ids", new ArrayList<>(Arrays.asList("id-a", "id-b", "id-c")));
        args.putStringArrayList("preChecked", new ArrayList<>(Arrays.asList("id-b")));
        args.putString("message", null);
        args.putString("repoFullName", "o/r");
        f.setArguments(args);
        FragmentTestHelper.attach(f);
        Bundle saved = new Bundle();
        saved.putBooleanArray("checked", new boolean[]{true});
        android.app.Dialog dialog = f.onCreateDialog(saved);
        // 直接调用 onCreateDialog 返回的对话框尚未 show，视图层级未挂载，
        // findViewById 取不到 list；先 show 再查（框架真实流程也是如此）。
        dialog.show();
        ListView list = ((AlertDialog) dialog).findViewById(com.gh4a.R.id.list);
        assertNotNull(list);
        assertFalse(list.isItemChecked(0));
        assertTrue(list.isItemChecked(1));
        assertFalse(list.isItemChecked(2));
        dialog.dismiss();
    }

    @Test
    public void onCreateDialog_noPreCheckedInArgs_skipsPreCheck() {
        // L77：args 里没有 preChecked → pre == null，不预勾选。
        AssignListsDialogFragment f = new AssignListsDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("names", new ArrayList<>(Arrays.asList("A", "B", "C")));
        args.putStringArrayList("ids", new ArrayList<>(Arrays.asList("id-a", "id-b", "id-c")));
        args.putString("repoFullName", "o/r");
        f.setArguments(args);
        FragmentTestHelper.attach(f);
        ListView list = listView(f);
        assertFalse(list.isItemChecked(0));
        assertFalse(list.isItemChecked(1));
        assertFalse(list.isItemChecked(2));
    }

    @Test
    public void confirmWithoutHost_doesNothing() {
        // L111/L154/L159：无 Callback 宿主时确认不回调、不崩溃。
        // 注意：AlertDialog 按钮点击经 Handler 分发监听器，不 idle 则
        // findCallback 根本不跑、断言空转通过；必须 idle 后再断言。
        AssignListsDialogFragment f = showDialogNoHost("nohost-confirm");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertNull(mHost.confirmedIds);
    }

    @Test
    public void createNewWithoutHost_doesNothing() {
        // L118：无 Callback 宿主时“新建列表”不回调、不崩溃。
        AssignListsDialogFragment f = showDialogNoHost("nohost-create");
        ((AlertDialog) f.getDialog()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        assertNull(mHost.createNewTag);
    }

    @Test
    public void cancelWithoutHost_doesNothing() {
        // L135：无 Callback 宿主时取消不回调、不崩溃。
        AssignListsDialogFragment f = showDialogNoHost("nohost-cancel");
        ((AlertDialog) f.getDialog()).cancel();
        idle();
        assertNull(mHost.cancelledTag);
    }

    @Test
    public void findCallback_withoutActivity_returnsNull() throws Exception {
        // L149/L150：未挂载时 getActivity() 为 null。
        AssignListsDialogFragment f = new AssignListsDialogFragment();
        java.lang.reflect.Method m =
                AssignListsDialogFragment.class.getDeclaredMethod("findCallback");
        m.setAccessible(true);
        assertNull(m.invoke(f));
    }

    @Test
    public void onSaveInstanceState_withoutDialog_storesNothing() {
        // L165：mChecked 为 null 时不写 checked。
        AssignListsDialogFragment f = new AssignListsDialogFragment();
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertFalse(out.containsKey("checked"));
    }
}
