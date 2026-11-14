package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.git.GitUser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class CommitAdapterTest {
    private Context context;
    private CommitAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new CommitAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private CommitAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private Commit commitWithAuthor(String login) {
        return Commit.builder()
                .author(User.builder().login(login).build())
                .sha("abcdef1234567890")
                .commit(GitCommit.builder().message("Fix bug").build())
                .build();
    }

    private TextView tv(CommitAdapter.ViewHolder h, int id) {
        return h.itemView.findViewById(id);
    }

    @Test
    public void bind_authorWithLogin_messageAndSha() {
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, commitWithAuthor("octocat"));
        assertEquals("Fix bug", tv(h, R.id.tv_desc).getText().toString());
        assertEquals("abcdef1234", tv(h, R.id.tv_sha).getText().toString());
        assertEquals(View.GONE, tv(h, R.id.tv_comments).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_description_indicator).getVisibility());
    }

    @Test
    public void bind_multilineMessage_firstLineOnlyAndIndicatorVisible() {
        Commit c = Commit.builder().sha("abc")
                .commit(GitCommit.builder().message("First line\nsecond line").build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals("First line", tv(h, R.id.tv_desc).getText().toString());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.iv_description_indicator).getVisibility());
        assertEquals("abc", tv(h, R.id.tv_sha).getText().toString());
    }

    @Test
    public void bind_nullCommitObject_emptyMessageAndSha() {
        Commit c = Commit.builder().build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals("", tv(h, R.id.tv_desc).getText().toString());
        assertEquals("", tv(h, R.id.tv_sha).getText().toString());
        assertEquals(View.GONE, tv(h, R.id.tv_comments).getVisibility());
    }

    @Test
    public void bind_shortSha_notTruncated() {
        Commit c = Commit.builder().sha("abc12")
                .commit(GitCommit.builder().message("m").build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals("abc12", tv(h, R.id.tv_sha).getText().toString());
    }

    @Test
    public void bind_exactlyTenCharSha() {
        Commit c = Commit.builder().sha("0123456789")
                .commit(GitCommit.builder().message("m").build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals("0123456789", tv(h, R.id.tv_sha).getText().toString());
    }

    @Test
    public void bind_commentCount_showsCount() {
        Commit c = Commit.builder().sha("abc")
                .commit(GitCommit.builder().message("m").commentCount(3).build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals(View.VISIBLE, tv(h, R.id.tv_comments).getVisibility());
        assertEquals("3", tv(h, R.id.tv_comments).getText().toString());
    }

    @Test
    public void bind_authorWithoutLogin_fallsBackToGitAuthor() {
        Commit c = Commit.builder()
                .author(User.builder().login("").build())
                .sha("abcdef1234567890")
                .commit(GitCommit.builder().message("m")
                        .author(GitUser.builder().name("Gitty").email("g@x.y").build())
                        .build())
                .build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        // falls back to DefaultAvatarDrawable, no crash; desc still bound
        assertEquals("m", tv(h, R.id.tv_desc).getText().toString());
        assertTrue(((android.widget.ImageView) h.itemView.findViewById(R.id.iv_gravatar))
                .getDrawable() != null);
    }

    @Test
    public void bind_nullMessageAndNullDate_noCrash() {
        Commit c = Commit.builder().sha("abc")
                .commit(GitCommit.builder()
                        .author(GitUser.builder().name("G").date(null).build()).build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertEquals("", tv(h, R.id.tv_desc).getText().toString());
        assertEquals("", tv(h, R.id.tv_timestamp).getText().toString());
    }

    @Test
    public void bind_withDate_showsRelativeTime() {
        Date past = new Date(System.currentTimeMillis() - 3600_000L);
        Commit c = Commit.builder().sha("abc")
                .commit(GitCommit.builder().message("m")
                        .author(GitUser.builder().name("G").date(past).build()).build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        assertTrue(tv(h, R.id.tv_timestamp).getText().length() > 0);
    }

    @Test
    public void bind_messageStartingWithNewline_indicatorHidden() {
        Commit c = Commit.builder().sha("abc")
                .commit(GitCommit.builder().message("\nsecond").build()).build();
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, c);
        // pos == 0 -> not > 0, no truncation, indicator gone
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_description_indicator).getVisibility());
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, commitWithAuthor("octocat"));
        View gravatar = h.itemView.findViewById(R.id.iv_gravatar);
        adapter.onClick(gravatar);
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_nullLogin_noActivityStarted() {
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, Commit.builder().build());
        View gravatar = h.itemView.findViewById(R.id.iv_gravatar);
        adapter.onClick(gravatar);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noActivity() {
        // RootAdapter only sets itemView's tag (used by onClick) when a click
        // listener is registered
        adapter.setOnItemClickListener(item -> {});
        CommitAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, commitWithAuthor("octocat"));
        h.itemView.setTag(h);
        adapter.onClick(h.itemView);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
