package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RootAdapter: list bookkeeping, header/footer, click dispatch, filter. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class RootAdapterTest {
    private Context context;
    private TestAdapter adapter;

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(View v) {
            super(v);
            text = (TextView) v;
        }
    }

    static class TestAdapter extends RootAdapter<String, VH> {
        TestAdapter(Context context) {
            super(context);
        }

        @Override
        protected VH onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
            return new VH(new TextView(mContext));
        }

        @Override
        protected void onBindViewHolder(VH holder, String item) {
            holder.text.setText(item);
        }

        @Override
        protected boolean isFiltered(CharSequence filter, String object) {
            return object.toLowerCase().contains(filter.toString().toLowerCase());
        }

        @Override
        protected int getItemViewType(String item) {
            if (item.startsWith("x")) {
                return CUSTOM_VIEW_TYPE_START + 5;
            }
            return super.getItemViewType(item);
        }
    }

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new TestAdapter(context);
    }

    @Test
    public void initialState_empty() {
        assertEquals(0, adapter.getCount());
        assertEquals(0, adapter.getItemCount());
        assertFalse(adapter.hasHeaderView());
        assertTrue(adapter.hasDividers());
        assertFalse(adapter.isCardStyle());
    }

    @Test
    public void add_addAll_remove_clear() {
        adapter.add("a");
        assertEquals(1, adapter.getCount());
        assertEquals("a", adapter.getItem(0));

        adapter.addAll(Arrays.asList("b", "c"));
        assertEquals(3, adapter.getCount());

        adapter.addAll(null);
        assertEquals(3, adapter.getCount());

        adapter.remove("b");
        assertEquals(2, adapter.getCount());
        assertEquals("c", adapter.getItem(1));

        adapter.clear();
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void getItem_outOfBounds_throws() {
        adapter.add("a");
        assertThrows(IndexOutOfBoundsException.class, () -> adapter.getItem(1));
        assertThrows(IndexOutOfBoundsException.class, () -> adapter.getItem(-1));
    }

    @Test
    public void replaceAllSilently_replacesWithoutNotify() {
        adapter.add("old");
        adapter.replaceAllSilently(Arrays.asList("n1", "n2"));
        assertEquals(2, adapter.getCount());
        assertEquals("n1", adapter.getItem(0));
        adapter.replaceAllSilently(null);
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void refreshItem_presentAndAbsent_noCrash() {
        adapter.add("a");
        adapter.refreshItem("a");
        adapter.refreshItem("missing");
    }

    @Test
    public void headerFooter_countsAndViewTypes() {
        adapter.add("a");
        adapter.add("x-special");
        View header = new View(context);
        View footer = new View(context);
        adapter.setHeaderView(header);
        adapter.setFooterView(footer, () -> {});
        assertTrue(adapter.hasHeaderView());
        assertEquals(4, adapter.getItemCount());
        assertEquals(0, adapter.getItemViewType(0));
        assertEquals(1, adapter.getItemViewType(3));
        assertEquals(2, adapter.getItemViewType(1));
        assertEquals(RootAdapter.CUSTOM_VIEW_TYPE_START + 5, adapter.getItemViewType(2));
    }

    @Test
    public void getItemViewType_noHeaderNoFooter() {
        adapter.add("a");
        assertEquals(2, adapter.getItemViewType(0));
    }

    @Test
    public void getItemFromAdapterPosition_headerOffsetAndBounds() {
        adapter.add("a");
        adapter.add("b");
        assertEquals("a", adapter.getItemFromAdapterPosition(0));
        adapter.setHeaderView(new View(context));
        assertEquals("a", adapter.getItemFromAdapterPosition(1));
        assertEquals("b", adapter.getItemFromAdapterPosition(2));
        assertNull(adapter.getItemFromAdapterPosition(0));
        assertNull(adapter.getItemFromAdapterPosition(3));
        assertNull(adapter.getItemFromAdapterPosition(-1));
    }

    @Test
    public void setListeners_afterHolderCreated_throws() {
        ViewGroup parent = AdapterTestSupport.parent(context);
        adapter.onCreateViewHolder(parent, 2);
        assertThrows(IllegalStateException.class,
                () -> adapter.setOnItemClickListener(item -> {}));
        assertThrows(IllegalStateException.class,
                () -> adapter.setOnItemLongClickListener(item -> false));
        assertThrows(IllegalStateException.class,
                () -> adapter.setContextMenuSupported(true));
    }

    @Test
    public void onCreateViewHolder_headerFooterHolders() {
        adapter.setHeaderView(new View(context));
        adapter.setFooterView(new View(context), null);
        ViewGroup parent = AdapterTestSupport.parent(context);
        RecyclerView.ViewHolder headerHolder = adapter.onCreateViewHolder(parent, 0);
        assertEquals(adapter.getItemViewType(0), 0);
        assertTrue(headerHolder.itemView != null);
        RecyclerView.ViewHolder footerHolder = adapter.onCreateViewHolder(parent, 1);
        assertTrue(footerHolder.itemView != null);
    }

    @Test
    public void onBindViewHolder_footer_callsListener_header_noop() {
        final boolean[] called = {false};
        adapter.add("a");
        adapter.setFooterView(new View(context), () -> called[0] = true);
        adapter.setHeaderView(new View(context));
        ViewGroup parent = AdapterTestSupport.parent(context);
        RecyclerView.ViewHolder footerHolder = adapter.onCreateViewHolder(parent, 1);
        adapter.onBindViewHolder(footerHolder, 2);
        assertTrue(called[0]);

        RecyclerView.ViewHolder headerHolder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(headerHolder, 0);
        // no crash, no listener call beyond the footer one
        assertTrue(called[0]);
    }

    @Test
    public void onBindViewHolder_item_bindsAndHighlightRuns() {
        adapter.add("a");
        ViewGroup parent = AdapterTestSupport.parent(context);
        VH holder = (VH) adapter.onCreateViewHolder(parent, 2);
        adapter.highlight(0);
        adapter.onBindViewHolder(holder, 0);
        Shadows.shadowOf(context.getMainLooper()).idle();
        assertEquals("a", holder.text.getText().toString());
        assertFalse(holder.itemView.isPressed());
    }

    @Test
    public void onBindViewHolder_item_outOfRangePosition_noCrash() {
        adapter.add("a");
        ViewGroup parent = AdapterTestSupport.parent(context);
        VH holder = (VH) adapter.onCreateViewHolder(parent, 2);
        adapter.onBindViewHolder(holder, 5);
        assertEquals("", holder.text.getText().toString());
    }

    @Test
    public void clickAndLongClick_dispatchToListeners() {
        final List<String> clicked = new ArrayList<>();
        final List<String> longClicked = new ArrayList<>();
        adapter.setOnItemClickListener(clicked::add);
        adapter.setOnItemLongClickListener(item -> {
            longClicked.add(item);
            return true;
        });
        adapter.add("a");
        adapter.add("b");
        RecyclerView rv = attach(adapter);
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(1);
        assertTrue(holder != null);
        adapter.onClick(holder.itemView);
        assertEquals(Arrays.asList("b"), clicked);
        assertTrue(adapter.onLongClick(holder.itemView));
        assertEquals(Arrays.asList("b"), longClicked);
    }

    @Test
    public void click_noPosition_noDispatch() {
        final List<String> clicked = new ArrayList<>();
        adapter.setOnItemClickListener(clicked::add);
        adapter.add("a");
        ViewGroup parent = AdapterTestSupport.parent(context);
        VH holder = (VH) adapter.onCreateViewHolder(parent, 2);
        // holder is not attached: NO_POSITION -> no dispatch
        adapter.onClick(holder.itemView);
        assertTrue(clicked.isEmpty());
        assertFalse(adapter.onLongClick(holder.itemView));
    }

    @Test
    public void longClick_listenerReturnsFalse() {
        adapter.setOnItemLongClickListener(item -> false);
        adapter.add("a");
        RecyclerView rv = attach(adapter);
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(0);
        assertFalse(adapter.onLongClick(holder.itemView));
    }

    @Test
    public void filter_matchesAndResets() throws Exception {
        adapter.addAll(Arrays.asList("apple", "banana", "apricot"));
        adapter.getFilter().filter("ap");
        waitForCount(2);
        assertEquals("apple", adapter.getItem(0));
        assertEquals("apricot", adapter.getItem(1));

        adapter.getFilter().filter("");
        waitForCount(3);
        assertEquals(3, adapter.getCount());

        // null constraint -> unfiltered
        adapter.getFilter().filter(null);
        waitForCount(3);
    }

    @Test
    public void filter_noMatch_empty() throws Exception {
        adapter.addAll(Arrays.asList("apple", "banana"));
        adapter.getFilter().filter("zzz");
        waitForCount(0);
    }

    @Test
    public void contextMenuSupported_makesItemLongClickable() {
        adapter.setContextMenuSupported(true);
        ViewGroup parent = AdapterTestSupport.parent(context);
        VH holder = (VH) adapter.onCreateViewHolder(parent, 2);
        assertTrue(holder.itemView.isLongClickable());
    }

    private RecyclerView attach(TestAdapter a) {
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(a);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 1920);
        return rv;
    }

    private void waitForCount(int expected) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (adapter.getCount() == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals("filter did not settle", expected, adapter.getCount());
    }

    @Test
    public void getItemViewType_plainItem_returnsItemType() {
        adapter.add("a");
        assertEquals(RootAdapter.CUSTOM_VIEW_TYPE_START, adapter.getItemViewType(0));
    }

    @Test
    public void getItemViewType_rogueViewType_assertFails() {
        TestAdapter rogue = new TestAdapter(context) {
            @Override
            protected int getItemViewType(String item) {
                return CUSTOM_VIEW_TYPE_START - 1;
            }
        };
        rogue.add("a");
        assertThrows(AssertionError.class, () -> rogue.getItemViewType(0));
    }

    @Test
    public void refreshItem_absentItem_noCrash() {
        adapter.add("a");
        adapter.refreshItem("zzz");
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void onBindViewHolder_footer_nullListener_noCrash() {
        adapter.add("a");
        adapter.setFooterView(new View(context), null);
        ViewGroup parent = AdapterTestSupport.parent(context);
        RecyclerView.ViewHolder footerHolder = adapter.onCreateViewHolder(parent, 1);
        adapter.onBindViewHolder(footerHolder, 1);
    }

    @Test
    public void onBindViewHolder_highlight_withBackground_clearsHighlight() {
        adapter.add("a");
        ViewGroup parent = AdapterTestSupport.parent(context);
        VH holder = (VH) adapter.onCreateViewHolder(parent, 2);
        holder.itemView.setBackground(new android.graphics.drawable.ColorDrawable(0xFF000000));
        // View.post() on a detached view queues into the view's own RunQueue
        // and never reaches the main looper; attach first (as a real
        // RecyclerView would) so the highlight-clearing Runnable is posted
        android.app.Activity activity = (android.app.Activity)
                ((android.view.ContextThemeWrapper) context).getBaseContext();
        activity.getWindowManager().addView(holder.itemView,
                new android.view.WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        adapter.highlight(0);
        adapter.onBindViewHolder(holder, 0);
        Shadows.shadowOf(context.getMainLooper()).idle();
        assertFalse(holder.itemView.isPressed());
        assertEquals(-1, (int) AdapterTestSupport.getField(adapter, "mHighlightPosition"));
    }

    @Test
    public void onClick_outOfRangePosition_noDispatch() {
        final List<String> clicked = new ArrayList<>();
        adapter.setOnItemClickListener(clicked::add);
        adapter.add("a");
        VH holder = mock(VH.class);
        when(holder.getBindingAdapterPosition()).thenReturn(99);
        View view = new View(context);
        view.setTag(holder);
        adapter.onClick(view);
        assertTrue(clicked.isEmpty());
    }

    @Test
    public void onLongClick_outOfRangePosition_returnsFalse() {
        adapter.setOnItemLongClickListener(item -> true);
        adapter.add("a");
        VH holder = mock(VH.class);
        when(holder.getBindingAdapterPosition()).thenReturn(99);
        View view = new View(context);
        view.setTag(holder);
        assertFalse(adapter.onLongClick(view));
    }
}
