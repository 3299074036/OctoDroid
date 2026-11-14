package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class IssueAdapterTest {
    private Context context;
    private IssueAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new IssueAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private IssueAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Issue issue(Integer number, String title, Integer comments, Milestone milestone) {
        return Issue.builder().number(number).title(title)
                .user(User.builder().login("octocat").build())
                .createdAt(new Date(System.currentTimeMillis() - 1000L))
                .comments(comments).milestone(milestone)
                .labels(Arrays.asList(Label.builder().name("bug").color("ff0000").build()))
                .build();
    }

    private String text(IssueAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_fullIssue() {
        IssueAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, issue(5, "Crash on start", 3,
                Milestone.builder().title("v1.0").build()));
        assertEquals("#5", text(h, R.id.tv_number));
        assertEquals("Crash on start", text(h, R.id.tv_desc));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_comments).getVisibility());
        assertEquals("3", text(h, R.id.tv_comments));
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.tv_milestone).getVisibility());
        assertEquals("v1.0", text(h, R.id.tv_milestone));
    }

    @Test
    public void bind_noCommentsNoMilestone_hidesBoth() {
        IssueAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, issue(1, "t", null, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_comments).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_milestone).getVisibility());
    }

    @Test
    public void bind_zeroComments_hidesComments() {
        IssueAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, issue(1, "t", 0, null));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_comments).getVisibility());
    }

    @Test
    public void bind_nullUser_noCrash() {
        IssueAdapter.ViewHolder h = holder();
        Issue i = Issue.builder().number(2).title("t").user(null)
                .createdAt(null).comments(null).milestone(null).labels(null).build();
        adapter.onBindViewHolder(h, i);
        assertEquals("#2", text(h, R.id.tv_number));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.tv_comments).getVisibility());
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        IssueAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, issue(5, "t", 0, null));
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_nullUser_noActivity() {
        IssueAdapter.ViewHolder h = holder();
        Issue i = Issue.builder().number(2).title("t").user(null).build();
        adapter.onBindViewHolder(h, i);
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noActivity() {
        adapter.setOnItemClickListener(item -> {});
        IssueAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, Issue.builder().number(1)
                .user(User.builder().login("octocat").build()).build());
        h.itemView.setTag(h);
        adapter.onClick(h.itemView);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
