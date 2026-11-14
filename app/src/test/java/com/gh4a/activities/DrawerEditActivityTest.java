package com.gh4a.activities;

import android.content.Intent;
import android.view.MenuItem;

import com.gh4a.R;
import com.gh4a.utils.DrawerManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.gh4a.activities.ActivityTestHelper.call;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DrawerEditActivityTest {
    @Test
    public void makeIntent_setsComponent() {
        Intent intent = DrawerEditActivity.makeIntent(appContext());
        assertEquals(DrawerEditActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void launch_titleAndItemsMatchDefaults() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        assertEquals(appContext().getString(R.string.customize_drawer),
                activity.getSupportActionBar().getTitle().toString());
        assertFalse(activity.canSwipeToRefresh());
        java.util.List<?> items = (java.util.List<?>) get(activity, "mItems");
        assertEquals(DrawerManager.getDefaultItems().size(), items.size());
        assertEquals(DrawerManager.getDefaultItems().get(0).key,
                ((DrawerManager.DrawerItemDef) items.get(0)).key);
    }

    @Test
    public void onCreateOptionsMenu_containsResetItem() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onCreateOptionsMenu(menu));
        assertTrue(menu.findItem(R.id.reset_default) != null);
    }

    @Test
    public void onOptionsItemSelected_resetDefault_restoresDefaultsAndReturnsTrue() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.reset_default);
        assertTrue(activity.onOptionsItemSelected(item));
        java.util.List<?> items = (java.util.List<?>) get(activity, "mItems");
        assertEquals(DrawerManager.getDefaultItems().size(), items.size());
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        MenuItem item = menu.add(0, 999999, 0, "nope");
        assertFalse(activity.onOptionsItemSelected(item));
    }

    private androidx.recyclerview.widget.ItemTouchHelper.Callback touchCallback(
            DrawerEditActivity activity) {
        Object adapter = get(activity, "mAdapter");
        Object helper = get(adapter, "mTouchHelper");
        return (androidx.recyclerview.widget.ItemTouchHelper.Callback) get(helper, "mCallback");
    }

    @Test
    public void saveOrder_persistsKeys() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        call(activity, "saveOrder", new Class<?>[]{}, new Object[]{});
        java.util.List<?> items = (java.util.List<?>) get(activity, "mItems");
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (Object o : items) {
            keys.add(((DrawerManager.DrawerItemDef) o).key);
        }
        assertEquals(keys, DrawerManager.getOrderedKeys(appContext()));
    }

    @Test
    public void touchCallback_onMove_movesItem() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.recyclerview.widget.ItemTouchHelper.Callback cb = touchCallback(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder from =
                mock(androidx.recyclerview.widget.RecyclerView.ViewHolder.class);
        androidx.recyclerview.widget.RecyclerView.ViewHolder to =
                mock(androidx.recyclerview.widget.RecyclerView.ViewHolder.class);
        when(from.getAdapterPosition()).thenReturn(0);
        when(to.getAdapterPosition()).thenReturn(1);
        java.util.List<?> before = new java.util.ArrayList<>((java.util.List<?>) get(activity, "mItems"));
        assertTrue(cb.onMove(mock(androidx.recyclerview.widget.RecyclerView.class), from, to));
        java.util.List<?> after = (java.util.List<?>) get(activity, "mItems");
        assertEquals(before.get(0), after.get(1));
        assertEquals(before.get(1), after.get(0));
    }

    @Test
    public void touchCallback_onSwiped_isNoOp() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.recyclerview.widget.ItemTouchHelper.Callback cb = touchCallback(activity);
        int count = ((java.util.List<?>) get(activity, "mItems")).size();
        cb.onSwiped(mock(androidx.recyclerview.widget.RecyclerView.ViewHolder.class), 0);
        assertEquals(count, ((java.util.List<?>) get(activity, "mItems")).size());
    }

    @Test
    public void touchCallback_clearView_persistsOrder() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        androidx.recyclerview.widget.ItemTouchHelper.Callback cb = touchCallback(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                new androidx.recyclerview.widget.RecyclerView.ViewHolder(
                        new android.view.View(appContext())) {};
        cb.clearView(mock(androidx.recyclerview.widget.RecyclerView.class), holder);
        java.util.List<?> items = (java.util.List<?>) get(activity, "mItems");
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (Object o : items) {
            keys.add(((DrawerManager.DrawerItemDef) o).key);
        }
        assertEquals(keys, DrawerManager.getOrderedKeys(appContext()));
    }

    @Test
    public void touchCallback_isLongPressDragEnabled_false() {
        DrawerEditActivity activity = launch(DrawerEditActivity.class);
        assertFalse(touchCallback(activity).isLongPressDragEnabled());
    }
}
