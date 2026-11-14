package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.utils.DrawerManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class DrawerEditAdapterTest {
    private Context context;
    private List<DrawerManager.DrawerItemDef> items;
    private DrawerEditAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        items = new ArrayList<>(DrawerManager.getDefaultItems());
        adapter = new DrawerEditAdapter(context, items);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private DrawerEditAdapter.ViewHolder holder() {
        // ViewHolder is package-private static; same package access from test
        // is not possible for the constructor, so go through onCreateViewHolder.
        return adapter.onCreateViewHolder(parent, 0);
    }

    @Test
    public void getItemCount_matchesList() {
        assertEquals(items.size(), adapter.getItemCount());
        assertTrue(items.size() > 0);
    }

    @Test
    public void moveItem_swaps() {
        DrawerManager.DrawerItemDef first = items.get(0);
        DrawerManager.DrawerItemDef second = items.get(1);
        adapter.moveItem(0, 1);
        assertEquals(second, items.get(0));
        assertEquals(first, items.get(1));
    }

    @Test
    public void moveItem_outOfBounds_noop() {
        List<DrawerManager.DrawerItemDef> before = new ArrayList<>(items);
        adapter.moveItem(-1, 1);
        adapter.moveItem(0, -1);
        adapter.moveItem(0, items.size());
        adapter.moveItem(items.size(), 0);
        assertEquals(before, items);
    }

    @Test
    public void moveItem_samePosition_noop() {
        DrawerManager.DrawerItemDef first = items.get(0);
        adapter.moveItem(0, 0);
        assertEquals(first, items.get(0));
    }

    @Test
    public void bind_setsTitleIconAndSwitchState() {
        DrawerEditAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, 0);
        assertEquals(context.getString(items.get(0).titleRes),
                ((android.widget.TextView) h.itemView.findViewById(R.id.title))
                        .getText().toString());
        SwitchCompat sw = h.itemView.findViewById(R.id.visibility_switch);
        assertTrue(sw.isChecked());
    }

    @Test
    public void bind_switchToggle_updatesVisibility() {
        DrawerEditAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, 0);
        SwitchCompat sw = h.itemView.findViewById(R.id.visibility_switch);
        String key = items.get(0).key;
        assertTrue(DrawerManager.isVisible(context, key));
        sw.setChecked(false);
        assertFalse(DrawerManager.isVisible(context, key));
        sw.setChecked(true);
        assertTrue(DrawerManager.isVisible(context, key));
    }

    @Test
    public void bind_hiddenItem_switchUnchecked() {
        DrawerManager.setVisible(context, items.get(1).key, false);
        try {
            DrawerEditAdapter.ViewHolder h = holder();
            adapter.onBindViewHolder(h, 1);
            SwitchCompat sw = h.itemView.findViewById(R.id.visibility_switch);
            assertFalse(sw.isChecked());
        } finally {
            DrawerManager.setVisible(context, items.get(1).key, true);
        }
    }

    @Test
    public void dragHandle_touchDown_withoutHelper_noCrash() {
        DrawerEditAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, 0);
        View handle = h.itemView.findViewById(R.id.drag_handle);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertFalse(handle.dispatchTouchEvent(down));
        down.recycle();
    }

    @Test
    public void dragHandle_touchDown_withHelper_startsDrag() {
        ItemTouchHelper helper = Mockito.mock(ItemTouchHelper.class);
        adapter.setTouchHelper(helper);
        DrawerEditAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, 0);
        View handle = h.itemView.findViewById(R.id.drag_handle);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        handle.dispatchTouchEvent(down);
        down.recycle();
        Mockito.verify(helper).startDrag(h);
    }

    @Test
    public void dragHandle_touchMove_doesNotStartDrag() {
        ItemTouchHelper helper = Mockito.mock(ItemTouchHelper.class);
        adapter.setTouchHelper(helper);
        DrawerEditAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, 0);
        View handle = h.itemView.findViewById(R.id.drag_handle);
        MotionEvent move = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 0, 0);
        handle.dispatchTouchEvent(move);
        move.recycle();
        Mockito.verify(helper, Mockito.never()).startDrag(Mockito.any(RecyclerView.ViewHolder.class));
    }
}
