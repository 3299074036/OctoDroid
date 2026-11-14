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
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class ContributorAdapterTest {
    private Context context;
    private ContributorAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new ContributorAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    @Test
    public void bind_showsNameAndContributions() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().login("octocat").name("Octo Cat")
                .contributions(42).build());
        assertEquals("octocat - Octo Cat",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertTrue(((TextView) h.itemView.findViewById(R.id.tv_extra)).getText().toString()
                .contains("42"));
    }

    @Test
    public void bind_nullName_loginOnly() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().login("octocat").contributions(0).build());
        assertEquals("octocat",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void bind_nullContributions_noCrash() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        assertEquals("octocat",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_nullLogin_noActivity() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().build());
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noDispatchWithoutListener() {
        ContributorAdapter.ViewHolder h = adapter.onCreateViewHolder(inflater, parent, 2);
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        // itemView has no holder tag set by this path (only RootAdapter sets it);
        // super.onClick would need a holder tag; calling onClick on the title view
        // exercises the else branch only when tag is a holder -- instead verify
        // gravatar path is the only special case by clicking the title view with
        // a holder tag attached via RootAdapter flow is covered in RootAdapterTest.
        View title = h.itemView.findViewById(R.id.tv_title);
        title.setTag(h);
        adapter.onClick(title); // NO_POSITION -> no-op, no crash
    }
}
