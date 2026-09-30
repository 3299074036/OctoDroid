package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.adapter.DrawerEditAdapter;
import com.gh4a.utils.DrawerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows the user to customize the navigation drawer: reorder items via drag
 * and toggle visibility. Changes are saved immediately and applied the next
 * time HomeActivity is created.
 */
public class DrawerEditActivity extends BaseActivity {
    public static Intent makeIntent(android.content.Context context) {
        return new Intent(context, DrawerEditActivity.class);
    }

    private DrawerEditAdapter mAdapter;
    private List<DrawerManager.DrawerItemDef> mItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawer_edit);

        mItems = new ArrayList<>();
        // Load in current user order
        java.util.Map<String, DrawerManager.DrawerItemDef> map = new java.util.LinkedHashMap<>();
        for (DrawerManager.DrawerItemDef def : DrawerManager.getDefaultItems()) {
            map.put(def.key, def);
        }
        for (String key : DrawerManager.getOrderedKeys(this)) {
            DrawerManager.DrawerItemDef def = map.get(key);
            if (def != null) {
                mItems.add(def);
            }
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new DrawerEditAdapter(this, mItems);
        recyclerView.setAdapter(mAdapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                mAdapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                // no swipe
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Drag finished, persist the new order
                saveOrder();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // we use the drag handle
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);
        mAdapter.setTouchHelper(touchHelper);
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (DrawerManager.DrawerItemDef def : mItems) {
            keys.add(def.key);
        }
        DrawerManager.saveOrder(this, keys);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.drawer_edit_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.reset_default) {
            DrawerManager.resetToDefault(this);
            // Reload
            mItems.clear();
            mItems.addAll(DrawerManager.getDefaultItems());
            mAdapter.notifyDataSetChanged();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.customize_drawer);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }
}
