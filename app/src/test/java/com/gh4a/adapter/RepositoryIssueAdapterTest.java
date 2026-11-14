package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Issue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RepositoryIssueAdapterTest {
    private Context context;
    private RepositoryIssueAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new RepositoryIssueAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private IssueAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    @Test
    public void bind_fullUrl_showsRepoAndNumber() {
        IssueAdapter.ViewHolder h = holder();
        Issue issue = Issue.builder().number(132).title("t")
                .url("https://api.github.com/repos/batterseapower/pinyin-toolkit/issues/132")
                .build();
        adapter.onBindViewHolder(h, issue);
        assertEquals("#132 on batterseapower/pinyin-toolkit",
                ((TextView) h.itemView.findViewById(R.id.tv_number)).getText().toString());
    }

    @Test
    public void bind_shortUrl_fallsBackToNumberOnly() {
        IssueAdapter.ViewHolder h = holder();
        Issue issue = Issue.builder().number(7).title("t").url("https://x/y").build();
        adapter.onBindViewHolder(h, issue);
        assertEquals("#7",
                ((TextView) h.itemView.findViewById(R.id.tv_number)).getText().toString());
    }

    @Test
    public void bind_nullUrl_fallsBackToNumberOnly() {
        IssueAdapter.ViewHolder h = holder();
        Issue issue = Issue.builder().number(9).title("t").url(null).build();
        adapter.onBindViewHolder(h, issue);
        assertEquals("#9",
                ((TextView) h.itemView.findViewById(R.id.tv_number)).getText().toString());
    }

    @Test
    public void bind_nullNumber_showsZero() {
        IssueAdapter.ViewHolder h = holder();
        Issue issue = Issue.builder().number(null).title("t").url(null).build();
        adapter.onBindViewHolder(h, issue);
        assertEquals("#0",
                ((TextView) h.itemView.findViewById(R.id.tv_number)).getText().toString());
    }

    @Test
    public void bind_exactlySixParts_fallsBack() {
        // url split into exactly 6 parts is not > 5... 6 > 5 is true, but
        // [4]/[5] must exist; use a 6-part url to cover the > 5 branch boundary.
        IssueAdapter.ViewHolder h = holder();
        Issue issue = Issue.builder().number(3).title("t")
                .url("a/b/c/d/e/f").build();
        adapter.onBindViewHolder(h, issue);
        assertEquals("#3 on e/f",
                ((TextView) h.itemView.findViewById(R.id.tv_number)).getText().toString());
    }
}
