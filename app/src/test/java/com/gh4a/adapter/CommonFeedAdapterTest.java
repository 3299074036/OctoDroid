package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.model.Feed;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class CommonFeedAdapterTest {
    private Context context;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private Feed feed(String title, String preview, String author, int userId,
            String avatarUrl, Date published, Date updated) {
        Feed f = new Feed();
        AdapterTestSupport.setField(f, "title", title);
        AdapterTestSupport.setField(f, "preview", preview);
        AdapterTestSupport.setField(f, "author", author);
        AdapterTestSupport.setField(f, "userId", userId);
        AdapterTestSupport.setField(f, "avatarUrl", avatarUrl);
        AdapterTestSupport.setField(f, "published", published);
        AdapterTestSupport.setField(f, "updated", updated);
        return f;
    }

    private CommonFeedAdapter.ViewHolder holder(CommonFeedAdapter adapter) {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private String text(CommonFeedAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_showExtra_withAuthorAndDate() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        Date pub = new Date(System.currentTimeMillis() - 1000L);
        adapter.onBindViewHolder(h, feed("Title", "Preview", "alice", 0, null, pub, null));
        assertEquals("Title", text(h, R.id.tv_title));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_title).getVisibility());
        assertEquals("Preview", text(h, R.id.tv_desc));
        // TextView.setGravity adds START when no horizontal gravity is given
        assertEquals(Gravity.TOP | Gravity.START,
                ((TextView) h.itemView.findViewById(R.id.tv_desc)).getGravity());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_gravatar).getVisibility());
        assertEquals("alice", text(h, R.id.tv_extra));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_extra).getVisibility());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_timestamp).getVisibility());
        assertTrue(text(h, R.id.tv_timestamp).length() > 0);
    }

    @Test
    public void bind_showExtra_nullTitle_hidesTitle() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        adapter.onBindViewHolder(h, feed(null, "p", "alice", 0, null, null, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_title).getVisibility());
    }

    @Test
    public void bind_showExtra_nullAuthor_hidesExtra() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        adapter.onBindViewHolder(h, feed("T", "p", null, 0, null, null, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_extra).getVisibility());
        assertEquals("", text(h, R.id.tv_timestamp));
    }

    @Test
    public void bind_showExtra_usesUpdatedWhenPublishedNull() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        Date upd = new Date(System.currentTimeMillis() - 5000L);
        adapter.onBindViewHolder(h, feed("T", "p", "a", 0, null, null, upd));
        assertTrue(text(h, R.id.tv_timestamp).length() > 0);
    }

    @Test
    public void bind_noExtra_hidesExtraAndTimestamp_centersDesc() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, false);
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        adapter.onBindViewHolder(h, feed("T", "p", "alice", 5, null, new Date(), null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_extra).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_timestamp).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_gravatar).getVisibility());
        assertEquals(Gravity.CENTER_VERTICAL | Gravity.START,
                ((TextView) h.itemView.findViewById(R.id.tv_desc)).getGravity());
    }

    @Test
    public void bind_showExtra_withUserId_loadsAvatar() {
        // Avatar fetch goes to an unroutable local address so the background
        // fetch fails fast; the visible outcome is the avatar view + tag.
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        Feed f = feed("T", "p", "alice", 7, "http://127.0.0.1:9/a.png", null, null);
        adapter.onBindViewHolder(h, f);
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.iv_gravatar).getVisibility());
        assertEquals(f, h.itemView.findViewById(R.id.iv_gravatar).getTag());
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        adapter.onBindViewHolder(h, feed("T", "p", "alice", 0, null, null, null));
        // avatar is GONE with userId 0, but the click handler only needs the tag;
        // set the tag the way bind does when an avatar is shown.
        View gravatar = h.itemView.findViewById(R.id.iv_gravatar);
        Feed f = feed("T", "p", "alice", 0, null, null, null);
        gravatar.setTag(f);
        adapter.onClick(gravatar);
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_nullAuthor_noActivity() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        View gravatar = h.itemView.findViewById(R.id.iv_gravatar);
        gravatar.setTag(feed("T", "p", null, 0, null, null, null));
        adapter.onClick(gravatar);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noActivity() {
        CommonFeedAdapter adapter = new CommonFeedAdapter(context, true);
        adapter.setOnItemClickListener(item -> {});
        CommonFeedAdapter.ViewHolder h = holder(adapter);
        adapter.onBindViewHolder(h, feed("T", "p", "alice", 0, null,
                new Date(System.currentTimeMillis() - 1000L), null));
        h.itemView.setTag(h);
        adapter.onClick(h.itemView);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
