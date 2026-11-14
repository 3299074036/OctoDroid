package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.Milestone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class MilestoneAdapterTest {
    private Context context;
    private MilestoneAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new MilestoneAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private MilestoneAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private String text(MilestoneAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_openWithDescriptionAndDueDate() {
        MilestoneAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, Milestone.builder().title("v1.0").description("first")
                .state(IssueState.Open).openIssues(3).closedIssues(5)
                .dueOn(new Date()).build());
        assertEquals("v1.0", text(h, R.id.tv_title));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
        assertEquals("first", text(h, R.id.tv_desc));
        assertTrue(text(h, R.id.tv_open).contains("3"));
        assertTrue(text(h, R.id.tv_closed).contains("5"));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_due).getVisibility());
    }

    @Test
    public void bind_closed_blankDescription_noDueDate_nullCounts() {
        MilestoneAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, Milestone.builder().title("v0.9").description("  ")
                .state(IssueState.Closed).openIssues(null).closedIssues(null)
                .dueOn(null).build());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_desc).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_due).getVisibility());
        assertTrue(text(h, R.id.tv_open).contains("0"));
        assertTrue(text(h, R.id.tv_closed).contains("0"));
    }

    @Test
    public void bind_nullState_usesPrimaryColor() {
        MilestoneAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, Milestone.builder().title("t").state(null).build());
        assertEquals("t", text(h, R.id.tv_title));
    }
}
