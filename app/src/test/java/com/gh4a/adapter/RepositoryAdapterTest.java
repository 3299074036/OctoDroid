package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RepositoryAdapterTest {
    private Context context;
    private RepositoryAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new RepositoryAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private RepositoryAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Repository repo(String name, String desc, String lang, Boolean priv,
            Boolean fork, Integer stars, Integer forks, Integer size) {
        return Repository.builder().name(name).fullName("owner/" + name).description(desc)
                .language(lang).isPrivate(priv).isFork(fork).stargazersCount(stars)
                .forksCount(forks).size(size)
                .owner(User.builder().login("owner").build()).build();
    }

    private String text(RepositoryAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_fullMeta() {
        RepositoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, repo("octo", "A repo", "Java", false, false, 10, 2, 100));
        assertEquals("owner/octo", text(h, R.id.tv_title));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
        assertEquals("A repo", text(h, R.id.tv_desc));
        String meta = text(h, R.id.tv_meta);
        assertTrue(meta.contains("Java"));
        assertTrue(meta.contains("10"));
        assertTrue(meta.contains("2"));
    }

    @Test
    public void bind_privateFork_nulls() {
        RepositoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, repo("x", null, null, true, true, null, null, null));
        String meta = text(h, R.id.tv_meta);
        assertTrue(meta.contains(context.getString(R.string.repo_type_private)));
        assertTrue(meta.contains(context.getString(R.string.fork)));
        assertTrue(meta.contains(context.getString(R.string.unknown)));
        assertTrue(meta.contains("0"));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void bind_blankDescription_hidesDesc() {
        RepositoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, repo("x", "   ", "Kotlin", false, false, 1, 0, 1));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
    }

    @Test
    public void bind_emojiDescription_parsed() {
        RepositoryAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, repo("x", "cool :thumbsup:", null, false, false, 0, 0, 0));
        assertFalse(text(h, R.id.tv_desc).contains(":thumbsup:"));
    }

    @Test
    public void isFiltered_matchesCaseInsensitive() {
        assertTrue(adapter.isFiltered("OCTO", repo("octo", null, null, false, false, 0, 0, 0)));
        assertFalse(adapter.isFiltered("zzz", repo("octo", null, null, false, false, 0, 0, 0)));
    }

    @Test
    public void isFiltered_nullName_neverMatches() {
        assertFalse(adapter.isFiltered("a", repo(null, null, null, false, false, 0, 0, 0)));
    }
}
