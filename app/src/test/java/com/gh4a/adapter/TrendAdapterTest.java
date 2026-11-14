package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.model.Trend;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TrendAdapterTest {
    private Context context;
    private TrendAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new TrendAdapter(context, R.string.trend_stars_today);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private TrendAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private String text(TrendAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_unattached_rankEmpty() {
        TrendAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, new Trend("octocat", "hello", "desc", "Java", 100, 5, 3));
        assertEquals("", text(h, R.id.tv_rank));
        assertEquals("octocat/hello", text(h, R.id.tv_title));
        assertEquals("desc", text(h, R.id.tv_desc));
        assertEquals("Java", text(h, R.id.tv_lang));
        assertEquals("5 today, 100 total", text(h, R.id.tv_stars));
        assertEquals("3", text(h, R.id.tv_forks));
    }

    @Test
    public void bind_nullOwnerName_emptyPrefix() {
        TrendAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, new Trend(null, null, null, null, 0, 0, 0));
        assertEquals("/", text(h, R.id.tv_title));
        assertEquals(context.getString(R.string.unknown), text(h, R.id.tv_lang));
        assertEquals("0 today, 0 total", text(h, R.id.tv_stars));
    }

    @Test
    public void bind_emojiDescription_parsed() {
        TrendAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, new Trend("o", "r", "cool :fire:", "", 1, 1, 0));
        assertEquals(false, text(h, R.id.tv_desc).contains(":fire:"));
    }

    @Test
    public void bind_attached_firstThreeRankedBold() {
        adapter.add(new Trend("a", "r1", "d", "Java", 10, 1, 0));
        adapter.add(new Trend("a", "r2", "d", "Java", 10, 1, 0));
        adapter.add(new Trend("a", "r3", "d", "Java", 10, 1, 0));
        adapter.add(new Trend("a", "r4", "d", "Java", 10, 1, 0));
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 1920);
        TrendAdapter.ViewHolder h1 =
                (TrendAdapter.ViewHolder) rv.findViewHolderForAdapterPosition(0);
        TrendAdapter.ViewHolder h4 =
                (TrendAdapter.ViewHolder) rv.findViewHolderForAdapterPosition(3);
        assertEquals("1", ((TextView) h1.itemView.findViewById(R.id.tv_rank)).getText().toString());
        assertEquals("4", ((TextView) h4.itemView.findViewById(R.id.tv_rank)).getText().toString());
        // BOLD vs NORMAL cannot be asserted under Robolectric: setTypeface(null,
        // style) resolves via native Typeface.defaultFromStyle, which returns
        // null here, so getTypeface() is null for both. Both branches still run.
    }

    @Test
    public void bind_attached_withHeader_rankSkipsHeader() {
        adapter.setHeaderView(new View(context));
        adapter.add(new Trend("a", "r1", "d", "Java", 10, 1, 0));
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 1920);
        TrendAdapter.ViewHolder h =
                (TrendAdapter.ViewHolder) rv.findViewHolderForAdapterPosition(1);
        assertEquals("1", ((TextView) h.itemView.findViewById(R.id.tv_rank)).getText().toString());
    }
}
