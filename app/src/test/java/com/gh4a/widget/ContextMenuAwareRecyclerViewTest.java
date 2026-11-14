package com.gh4a.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ContextMenuAwareRecyclerViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ContextMenuAwareRecyclerView recyclerView(FragmentActivity activity, int count) {
        ContextMenuAwareRecyclerView rv = new ContextMenuAwareRecyclerView(activity);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 100));
                return new RecyclerView.ViewHolder(tv) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView).setText("item " + position);
            }

            @Override
            public int getItemCount() {
                return count;
            }

            @Override
            public long getItemId(int position) {
                return position * 10L + 5;
            }
        };
        // setHasStableIds must be called before setAdapter: once the adapter
        // has registered observers RecyclerView refuses the change.
        adapter.setHasStableIds(true);
        rv.setAdapter(adapter);
        activity.setContentView(rv);
        rv.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 500, 1000);
        return rv;
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertNotNull(new ContextMenuAwareRecyclerView(activity));
        assertNotNull(new ContextMenuAwareRecyclerView(activity, null));
        assertNotNull(new ContextMenuAwareRecyclerView(activity, null, 0));
    }

    @Test
    public void getContextMenuInfo_initiallyNull() {
        ContextMenuAwareRecyclerView rv = new ContextMenuAwareRecyclerView(activity());
        assertNull(rv.getContextMenuInfo());
    }

    @Test
    public void showContextMenuForChild_boundChild_setsMenuInfo() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);
        assertTrue(rv.getChildCount() > 1);
        View child = rv.getChildAt(1);

        rv.showContextMenuForChild(child);

        ContextMenuAwareRecyclerView.RecyclerContextMenuInfo info =
                (ContextMenuAwareRecyclerView.RecyclerContextMenuInfo) rv.getContextMenuInfo();
        assertNotNull(info);
        assertEquals(1, info.position);
        assertEquals(15L, info.id);
    }

    @Test
    public void showContextMenuForChild_firstChild_setsMenuInfoWithPositionZero() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);
        View child = rv.getChildAt(0);

        rv.showContextMenuForChild(child);

        ContextMenuAwareRecyclerView.RecyclerContextMenuInfo info =
                (ContextMenuAwareRecyclerView.RecyclerContextMenuInfo) rv.getContextMenuInfo();
        assertNotNull(info);
        assertEquals(0, info.position);
        assertEquals(5L, info.id);
    }

    @Test
    public void showContextMenuForChild_unboundChild_returnsFalseAndKeepsInfoNull() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);
        TextView unbound = new TextView(activity);
        unbound.setLayoutParams(new RecyclerView.LayoutParams(100, 100));

        boolean result = rv.showContextMenuForChild(unbound);

        assertFalse(result);
        assertNull(rv.getContextMenuInfo());
    }

    @Test
    public void showContextMenuForChild_nonRecyclerLayoutParams_returnsFalse() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);
        TextView view = new TextView(activity);
        view.setLayoutParams(new ViewGroup.LayoutParams(100, 100));

        boolean result = rv.showContextMenuForChild(view);

        assertFalse(result);
        assertNull(rv.getContextMenuInfo());
    }

    @Test
    public void showContextMenuForChild_twice_updatesMenuInfo() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);

        rv.showContextMenuForChild(rv.getChildAt(0));
        rv.showContextMenuForChild(rv.getChildAt(2));

        ContextMenuAwareRecyclerView.RecyclerContextMenuInfo info =
                (ContextMenuAwareRecyclerView.RecyclerContextMenuInfo) rv.getContextMenuInfo();
        assertEquals(2, info.position);
        assertEquals(25L, info.id);
    }

    @Test
    public void showContextMenuForChild_noPositionChild_returnsFalseAndKeepsInfoNull() {
        FragmentActivity activity = activity();
        ContextMenuAwareRecyclerView rv = recyclerView(activity, 3);
        View child = rv.getChildAt(0);
        // A data-set change invalidates the holders; until the next layout
        // pass their adapter positions are NO_POSITION.
        rv.getAdapter().notifyDataSetChanged();

        assertFalse(rv.showContextMenuForChild(child));
        assertNull(rv.getContextMenuInfo());
    }
}
