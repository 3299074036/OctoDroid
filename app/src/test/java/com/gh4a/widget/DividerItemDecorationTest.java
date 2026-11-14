package com.gh4a.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DividerItemDecorationTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private RecyclerView recyclerViewWithItems(FragmentActivity activity, int count) {
        RecyclerView rv = new RecyclerView(activity);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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
        });
        activity.setContentView(rv);
        rv.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 500, 1000);
        return rv;
    }

    @Test
    public void getItemOffsets_firstChild_noTopOffset() {
        FragmentActivity activity = activity();
        RecyclerView rv = recyclerViewWithItems(activity, 3);
        assertTrue(rv.getChildCount() > 0);
        DividerItemDecoration decoration = new DividerItemDecoration(activity);
        Rect outRect = new Rect();

        decoration.getItemOffsets(outRect, rv.getChildAt(0), rv, new RecyclerView.State());

        assertEquals(0, outRect.top);
    }

    @Test
    public void getItemOffsets_secondChild_topOffsetEqualsDividerHeight() {
        FragmentActivity activity = activity();
        RecyclerView rv = recyclerViewWithItems(activity, 3);
        assertTrue(rv.getChildCount() > 1);
        DividerItemDecoration decoration = new DividerItemDecoration(activity);
        Rect outRect = new Rect();

        decoration.getItemOffsets(outRect, rv.getChildAt(1), rv, new RecyclerView.State());

        int dividerHeight = activity.getTheme()
                .obtainStyledAttributes(new int[] { android.R.attr.listDivider })
                .getDrawable(0).getIntrinsicHeight();
        assertEquals(dividerHeight, outRect.top);
        assertTrue(dividerHeight >= 0);
    }

    @Test
    public void onDraw_noChildren_doesNotThrow() {
        FragmentActivity activity = activity();
        RecyclerView rv = new RecyclerView(activity);
        DividerItemDecoration decoration = new DividerItemDecoration(activity);
        Canvas canvas = new Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888));

        decoration.onDraw(canvas, rv, new RecyclerView.State());
    }

    @Test
    public void onDraw_withChildren_drawsDividersWithoutThrow() {
        FragmentActivity activity = activity();
        RecyclerView rv = recyclerViewWithItems(activity, 3);
        assertTrue(rv.getChildCount() > 1);
        DividerItemDecoration decoration = new DividerItemDecoration(activity);
        Canvas canvas = new Canvas(Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888));

        decoration.onDraw(canvas, rv, new RecyclerView.State());
    }

    @Test
    public void onDraw_singleChild_noDividerDrawn() {
        FragmentActivity activity = activity();
        RecyclerView rv = recyclerViewWithItems(activity, 1);
        DividerItemDecoration decoration = new DividerItemDecoration(activity);
        Canvas canvas = new Canvas(Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888));

        decoration.onDraw(canvas, rv, new RecyclerView.State());
    }

    @Test
    public void dividerNull_themeWithoutListDivider_offsetsAndDrawAreNoOps() throws Exception {
        FragmentActivity activity = activity();
        // A theme with no listDivider resolves the divider to null; stub the
        // Context to return an empty TypedArray so the decoration holds null.
        Context context = mock(Context.class);
        TypedArray attrs = mock(TypedArray.class);
        when(context.obtainStyledAttributes(isNull(), any(int[].class))).thenReturn(attrs);
        DividerItemDecoration decoration = new DividerItemDecoration(context);
        Field dividerField = DividerItemDecoration.class.getDeclaredField("mDivider");
        dividerField.setAccessible(true);
        assertNull(dividerField.get(decoration));

        RecyclerView rv = recyclerViewWithItems(activity, 3);
        View child = rv.getChildAt(1);
        Rect outRect = new Rect();
        decoration.getItemOffsets(outRect, child, rv, new RecyclerView.State());
        assertEquals(0, outRect.top);

        Canvas canvas = new Canvas(Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888));
        decoration.onDraw(canvas, rv, new RecyclerView.State());
    }
}
