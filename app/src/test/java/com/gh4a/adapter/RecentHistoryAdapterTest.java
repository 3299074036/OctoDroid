package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.RecentHistoryManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RecentHistoryAdapterTest {
    private Context context;
    private RecentHistoryAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new RecentHistoryAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private RecentHistoryAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private RecentHistoryManager.Entry entry(int type, int number) {
        return AdapterTestSupport.newInstance(RecentHistoryManager.Entry.class,
                new Class<?>[]{int.class, String.class, String.class, int.class, long.class},
                type, "octocat", "hello", number, System.currentTimeMillis() - 1000L);
    }

    private String text(RecentHistoryAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_issue() {
        RecentHistoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, entry(RecentHistoryManager.TYPE_ISSUE, 12));
        assertEquals("octocat/hello", text(h, R.id.tv_title));
        assertTrue(text(h, R.id.tv_subtitle).contains("12"));
        assertTrue(text(h, R.id.tv_time).length() > 0);
    }

    @Test
    public void bind_pr() {
        RecentHistoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, entry(RecentHistoryManager.TYPE_PR, 34));
        assertEquals("octocat/hello", text(h, R.id.tv_title));
        assertTrue(text(h, R.id.tv_subtitle).contains("34"));
    }

    @Test
    public void bind_repo_defaultBranch() {
        RecentHistoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, entry(0, 0));
        assertEquals("octocat/hello", text(h, R.id.tv_title));
        assertEquals(context.getString(R.string.recent_history_repo), text(h, R.id.tv_subtitle));
    }

    @Test
    public void bind_unknownType_defaultBranch() {
        RecentHistoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, entry(99, 0));
        assertEquals(context.getString(R.string.recent_history_repo), text(h, R.id.tv_subtitle));
    }
}
