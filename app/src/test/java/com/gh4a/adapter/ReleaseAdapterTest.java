package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Release;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class ReleaseAdapterTest {
    private Context context;
    private ReleaseAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new ReleaseAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private ReleaseAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Release release(String name, String tag, Boolean draft, Boolean prerelease,
            Date publishedAt, Date createdAt) {
        Release r = mock(Release.class);
        when(r.name()).thenReturn(name);
        when(r.tagName()).thenReturn(tag);
        when(r.draft()).thenReturn(draft);
        when(r.prerelease()).thenReturn(prerelease);
        when(r.publishedAt()).thenReturn(publishedAt);
        when(r.createdAt()).thenReturn(createdAt);
        return r;
    }

    private String text(ReleaseAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_namedFinalRelease_usesPublishedDate() {
        Date pub = new Date(System.currentTimeMillis() - 1000L);
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("v1.0", "v1.0", false, false, pub, pub));
        assertEquals("v1.0", text(h, R.id.tv_title));
        assertEquals(context.getString(R.string.release_type_final), text(h, R.id.tv_type));
        assertTrue(text(h, R.id.tv_created_at).length() > 0);
    }

    @Test
    public void bind_emptyName_fallsBackToTag() {
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("", "v2.0", null, null, null,
                new Date(System.currentTimeMillis() - 1000L)));
        assertEquals("v2.0", text(h, R.id.tv_title));
        assertEquals(context.getString(R.string.release_type_final), text(h, R.id.tv_type));
    }

    @Test
    public void bind_nullName_fallsBackToTag() {
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release(null, "v3.0", null, null, null,
                new Date(System.currentTimeMillis() - 1000L)));
        assertEquals("v3.0", text(h, R.id.tv_title));
    }

    @Test
    public void bind_draft_showsDraftType() {
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("d", "d", true, false, null, new Date()));
        assertEquals(context.getString(R.string.release_type_draft), text(h, R.id.tv_type));
    }

    @Test
    public void bind_prerelease_showsPrereleaseType() {
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("p", "p", false, true, null, new Date()));
        assertEquals(context.getString(R.string.release_type_prerelease), text(h, R.id.tv_type));
    }

    @Test
    public void bind_draftBeatsPrerelease() {
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("d", "d", true, true, null, new Date()));
        assertEquals(context.getString(R.string.release_type_draft), text(h, R.id.tv_type));
    }

    @Test
    public void bind_noPublishedAt_usesCreatedDate() {
        Date created = new Date(System.currentTimeMillis() - 2000L);
        ReleaseAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, release("v", "v", false, false, null, created));
        assertTrue(text(h, R.id.tv_created_at).length() > 0);
    }
}
