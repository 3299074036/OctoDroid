package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class UserAdapterTest {
    private Context context;
    private UserAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new UserAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private UserAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private String subtitle(UserAdapter.ViewHolder h) {
        return ((TextView) h.itemView.findViewById(R.id.tv_subtitle)).getText().toString();
    }

    private int subtitleVisibility(UserAdapter.ViewHolder h) {
        return h.itemView.findViewById(R.id.tv_subtitle).getVisibility();
    }

    @Test
    public void bind_nameDiffersFromLogin_showsMentionSubtitle() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("octocat").name("Octo Cat").build());
        assertEquals("octocat - Octo Cat",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertEquals(View.VISIBLE, subtitleVisibility(h));
        assertEquals("@octocat", subtitle(h));
    }

    @Test
    public void bind_nameEqualsLogin_showsTypeSubtitle() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("octocat").name("octocat")
                .type(UserType.User).build());
        assertEquals(View.VISIBLE, subtitleVisibility(h));
        assertEquals("User", subtitle(h));
    }

    @Test
    public void bind_nullName_showsTypeSubtitle() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("botty").type(UserType.Bot).build());
        assertEquals("Bot", subtitle(h));
    }

    @Test
    public void bind_nullNameNullType_subtitleGone() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        assertEquals(View.GONE, subtitleVisibility(h));
    }

    @Test
    public void bind_nullLogin_titleEmpty() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().name("Nameless").build());
        assertEquals("Nameless",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertEquals("@null", subtitle(h));
    }

    @Test
    public void onClick_gravatar_startsUserActivity() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_mannequin_noActivity() {
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("m").type(UserType.Mannequin).build());
        adapter.onClick(h.itemView.findViewById(R.id.iv_gravatar));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_nonGravatar_delegatesToSuper_noActivity() {
        adapter.setOnItemClickListener(item -> {});
        UserAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, User.builder().login("octocat").build());
        h.itemView.setTag(h);
        adapter.onClick(h.itemView);
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
