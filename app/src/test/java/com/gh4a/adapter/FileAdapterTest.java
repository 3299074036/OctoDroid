package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Content;
import com.meisolsson.githubsdk.model.ContentType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class FileAdapterTest {
    private Context context;
    private FileAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new FileAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private FileAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Content content(String name, ContentType type, Integer size) {
        Content c = mock(Content.class);
        when(c.name()).thenReturn(name);
        when(c.type()).thenReturn(type);
        when(c.size()).thenReturn(size);
        return c;
    }

    @Test
    public void hasDividers_false() {
        assertFalse(adapter.hasDividers());
    }

    @Test
    public void bind_file_showsSize() {
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("Main.java", ContentType.File, 2048));
        assertEquals("Main.java",
                ((TextView) h.itemView.findViewById(R.id.tv_text)).getText().toString());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_size)).getText().length() > 0);
    }

    @Test
    public void bind_directory_hidesSize() {
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("src", ContentType.Directory, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_submodule_hidesSizeEvenForFile() {
        Set<String> subs = new HashSet<>();
        subs.add("lib");
        adapter.setSubModuleNames(subs);
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("lib", ContentType.File, 100));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_imageFile_showsSize() {
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("logo.png", ContentType.File, 10));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_nullType_hidesSize() {
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("x", null, 5));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_fileWithZeroSize_formatsZero() {
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("a.txt", ContentType.File, 0));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_fileWithNullSize_throwsNpe() {
        // T-ADP-04 (suspicious): Content.size() is @Nullable in the SDK model;
        // bind unboxes it without a null check, so a null size NPEs here.
        // The GitHub contents API always sends a size, so this is not
        // reachable from real API data.
        FileAdapter.ViewHolder h = holder();
        assertThrows(NullPointerException.class,
                () -> adapter.onBindViewHolder(h, content("a.txt", ContentType.File, null)));
    }

    @Test
    public void bind_emptySubmoduleSet_fileShowsSize() {
        adapter.setSubModuleNames(Collections.emptySet());
        FileAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, content("a.txt", ContentType.File, 7));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_size).getVisibility());
    }

    @Test
    public void bind_afterNullSubModuleSet_throwsNpe() {
        // T-ADP-01 (suspicious, Low): setSubModuleNames(null) is accepted but the
        // next bind dereferences the set; production callers never pass null.
        adapter.setSubModuleNames(null);
        FileAdapter.ViewHolder h = holder();
        assertThrows(NullPointerException.class,
                () -> adapter.onBindViewHolder(h, content("a.txt", ContentType.File, 7)));
    }
}
