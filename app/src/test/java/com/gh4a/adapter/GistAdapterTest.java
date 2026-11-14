package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GistFile;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

@RunWith(RobolectricTestRunner.class)
public class GistAdapterTest {
    private Context context;
    private GistAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new GistAdapter(context, "octocat");
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private GistAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Gist gist(User owner, String desc, Boolean isPublic, int fileCount) {
        return Gist.builder().owner(owner).description(desc).isPublic(isPublic)
                .createdAt(new Date(System.currentTimeMillis() - 1000L))
                .id("abc123").files(new HashMap<String, GistFile>() {{
                    for (int i = 0; i < fileCount; i++) put("f" + i, null);
                }}).build();
    }

    private String text(GistAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_self_hidesCreator() {
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, gist(User.builder().login("octocat").build(),
                "hello", true, 2));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_creator).getVisibility());
        assertEquals("hello", text(h, R.id.tv_title));
        assertEquals("abc123", text(h, R.id.tv_sha));
        assertEquals("2", text(h, R.id.tv_files));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_private).getVisibility());
    }

    @Test
    public void bind_other_showsCreatorAndPrivateBadge() {
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, gist(User.builder().login("someone").build(),
                "desc", false, 1));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_creator).getVisibility());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_private).getVisibility());
        assertEquals("1", text(h, R.id.tv_files));
    }

    @Test
    public void bind_nullOwner_notSelf_showsCreator() {
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, gist(null, "desc", true, 0));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_creator).getVisibility());
        assertEquals("0", text(h, R.id.tv_files));
    }

    @Test
    public void bind_emptyDescription_showsNoDescriptionString() {
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, gist(User.builder().login("x").build(), "", true, 0));
        assertEquals(context.getString(R.string.gist_no_description), text(h, R.id.tv_title));
    }

    @Test
    public void bind_nullDescription_showsNoDescriptionString() {
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, gist(User.builder().login("x").build(), null, true, 0));
        assertEquals(context.getString(R.string.gist_no_description), text(h, R.id.tv_title));
    }

    @Test
    public void bind_nullFiles_zeroFiles() {
        Gist g = Gist.builder().owner(User.builder().login("x").build())
                .description("d").isPublic(null).createdAt(null).id("id1").files(null).build();
        GistAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, g);
        assertEquals("0", text(h, R.id.tv_files));
        // isPublic null -> not TRUE -> private badge visible
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_private).getVisibility());
        assertEquals("", text(h, R.id.tv_timestamp));
    }
}
