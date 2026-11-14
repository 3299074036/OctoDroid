package com.gh4a.fragment;

import android.app.Dialog;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** SingleChoiceDialogFragment：单选对话框（Robolectric + 回调 Activity）。 */
@RunWith(RobolectricTestRunner.class)
public class SingleChoiceDialogFragmentTest {

    /** 实现 Callback 的宿主 Activity，记录回调。 */
    public static class TestActivity extends FragmentActivity
            implements SingleChoiceDialogFragment.Callback {
        String lastTag;
        int lastPosition = -1;
        String lastEntry;

        @Override
        public void onItemSelected(String tag, int position, String entry) {
            lastTag = tag;
            lastPosition = position;
            lastEntry = entry;
        }
    }

    private TestActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(TestActivity.class).setup().get();
    }

    @Test
    public void show_displaysDialogFragmentWithTag() {
        List<String> entries = Arrays.asList("Alpha", "Beta", "Gamma");
        SingleChoiceDialogFragment.show(mActivity, entries,
                R.string.search_sort_best_match, 1, "pick");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("pick");
        assertNotNull(f);
        assertEquals("pick", f.getTag());
        Dialog dialog = f.getDialog();
        assertNotNull(dialog);
        assertTrue(dialog instanceof AlertDialog);
    }

    @Test
    public void show_storesEntriesTitleAndSelectionInArguments() {
        List<String> entries = Arrays.asList("A", "B");
        SingleChoiceDialogFragment.show(mActivity, entries, R.string.cancel, 0, "t");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("t");
        assertNotNull(f);
        assertEquals(new ArrayList<>(entries),
                f.getArguments().getStringArrayList("entries"));
        assertEquals(R.string.cancel, f.getArguments().getInt("titleResId"));
        assertEquals(0, f.getArguments().getInt("selectedPosition"));
    }

    @Test
    public void onClick_reportsSelectedEntryToCallback() {
        List<String> entries = Arrays.asList("Alpha", "Beta", "Gamma");
        SingleChoiceDialogFragment.show(mActivity, entries,
                R.string.search_sort_best_match, 0, "pick");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("pick");
        assertNotNull(f);
        DialogInterface dialog = mock(DialogInterface.class);
        f.onClick(dialog, 2);
        assertEquals("pick", mActivity.lastTag);
        assertEquals(2, mActivity.lastPosition);
        assertEquals("Gamma", mActivity.lastEntry);
        verify(dialog).dismiss();
    }

    @Test
    public void onClick_firstEntryReportsPositionZero() {
        SingleChoiceDialogFragment.show(mActivity, Arrays.asList("Only"),
                R.string.cancel, 0, "one");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("one");
        f.onClick(mock(DialogInterface.class), 0);
        assertEquals(0, mActivity.lastPosition);
        assertEquals("Only", mActivity.lastEntry);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void onClick_outOfRangeIndexThrows() {
        SingleChoiceDialogFragment.show(mActivity, Arrays.asList("A", "B"),
                R.string.cancel, 0, "t");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("t");
        f.onClick(mock(DialogInterface.class), 5);
    }

    @Test
    public void show_emptyEntriesStillCreatesDialog() {
        SingleChoiceDialogFragment.show(mActivity, new ArrayList<>(),
                R.string.cancel, -1, "empty");
        mActivity.getSupportFragmentManager().executePendingTransactions();
        SingleChoiceDialogFragment f = (SingleChoiceDialogFragment)
                mActivity.getSupportFragmentManager().findFragmentByTag("empty");
        assertNotNull(f);
        assertNotNull(f.getDialog());
    }
}
