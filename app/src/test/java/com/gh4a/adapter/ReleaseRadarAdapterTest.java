package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.fragment.ReleaseRadarFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ReleaseRadarAdapterTest {
    private Context context;
    private ReleaseRadarAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new ReleaseRadarAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private ReleaseRadarAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private ReleaseRadarFragment.RadarItem item(String releaseName, long publishedAt,
            long createdAt) {
        return AdapterTestSupport.newInstance(ReleaseRadarFragment.RadarItem.class,
                new Class<?>[]{String.class, String.class, long.class, String.class,
                        String.class, long.class, long.class},
                "octocat", "hello", 1L, "v1.0", releaseName, publishedAt, createdAt);
    }

    private String text(ReleaseRadarAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_withReleaseName_usesPublishedAt() {
        long now = System.currentTimeMillis();
        ReleaseRadarAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("Release 1.0", now - 1000L, now - 2000L));
        assertEquals("octocat/hello", text(h, R.id.tv_repo));
        assertEquals("Release 1.0", text(h, R.id.tv_release));
        assertEquals("v1.0", text(h, R.id.tv_tag));
        assertTrue(text(h, R.id.tv_time).length() > 0);
    }

    @Test
    public void bind_emptyReleaseName_fallsBackToTag() {
        long now = System.currentTimeMillis();
        ReleaseRadarAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("", 0L, now - 1000L));
        assertEquals("v1.0", text(h, R.id.tv_release));
        assertTrue(text(h, R.id.tv_time).length() > 0);
    }

    @Test
    public void bind_nullReleaseName_fallsBackToTag() {
        long now = System.currentTimeMillis();
        ReleaseRadarAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item(null, now - 1000L, 0L));
        assertEquals("v1.0", text(h, R.id.tv_release));
    }

    @Test
    public void bind_zeroTimes_stillShowsEpochTime() {
        ReleaseRadarAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("r", 0L, 0L));
        // bind always formats the time (publishedAt 0 falls back to createdAt 0 = epoch)
        assertEquals(com.gh4a.utils.StringUtils.formatRelativeTime(context, 0L, true),
                text(h, R.id.tv_time));
    }
}
