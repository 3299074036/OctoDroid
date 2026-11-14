package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ItemsWithDescriptionAdapterTest {
    private Context context;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        parent = AdapterTestSupport.parent(context);
    }

    private ItemsWithDescriptionAdapter adapter(List<ItemsWithDescriptionAdapter.Item> items) {
        return new ItemsWithDescriptionAdapter(context, items);
    }

    @Test
    public void getView_withDescription_visible() {
        ItemsWithDescriptionAdapter a = adapter(Arrays.asList(
                new ItemsWithDescriptionAdapter.Item("Title", "Desc")));
        View row = a.getView(0, null, parent);
        assertEquals("Title", ((TextView) row.findViewById(R.id.title)).getText().toString());
        assertEquals("Desc", ((TextView) row.findViewById(R.id.description)).getText().toString());
        assertEquals(View.VISIBLE, row.findViewById(R.id.description).getVisibility());
    }

    @Test
    public void getView_emptyDescription_gone() {
        ItemsWithDescriptionAdapter a = adapter(Arrays.asList(
                new ItemsWithDescriptionAdapter.Item("Title", "")));
        View row = a.getView(0, null, parent);
        assertEquals(View.GONE, row.findViewById(R.id.description).getVisibility());
    }

    @Test
    public void getView_nullDescription_gone() {
        ItemsWithDescriptionAdapter a = adapter(Arrays.asList(
                new ItemsWithDescriptionAdapter.Item("Title", null)));
        View row = a.getView(0, null, parent);
        assertEquals(View.GONE, row.findViewById(R.id.description).getVisibility());
    }

    @Test
    public void getView_reusesConvertView() {
        ItemsWithDescriptionAdapter a = adapter(Arrays.asList(
                new ItemsWithDescriptionAdapter.Item("T1", "D1"),
                new ItemsWithDescriptionAdapter.Item("T2", "D2")));
        View first = a.getView(0, null, parent);
        View reused = a.getView(1, first, parent);
        assertEquals(first, reused);
        assertEquals("T2", ((TextView) reused.findViewById(R.id.title)).getText().toString());
    }

    @Test
    public void getView_nullItem_clearsRow() {
        List<ItemsWithDescriptionAdapter.Item> items = new ArrayList<>();
        items.add(null);
        ItemsWithDescriptionAdapter a = adapter(items);
        View row = a.getView(0, null, parent);
        assertEquals("", ((TextView) row.findViewById(R.id.title)).getText().toString());
        assertEquals(View.GONE, row.findViewById(R.id.description).getVisibility());
    }

    @Test
    public void getView_outOfRange_throws() {
        // T-ADP-02 (suspicious): the null-guard comment claims getItem() returns
        // null out of range, but ArrayAdapter.getItem() throws instead; the null
        // branch is only reachable via an explicit null element (tested above).
        ItemsWithDescriptionAdapter a = adapter(Arrays.asList(
                new ItemsWithDescriptionAdapter.Item("T", "D")));
        assertThrows(IndexOutOfBoundsException.class, () -> a.getView(5, null, parent));
    }
}
