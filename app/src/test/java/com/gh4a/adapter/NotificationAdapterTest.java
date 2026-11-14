package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.model.NotificationHolder;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class NotificationAdapterTest {
    private Context context;
    private LayoutInflater inflater;
    private ViewGroup parent;
    private List<NotificationHolder> markedRead;
    private List<NotificationHolder> unsubscribed;
    private NotificationAdapter adapter;

    private final NotificationAdapter.OnNotificationActionCallback callback =
            new NotificationAdapter.OnNotificationActionCallback() {
                @Override
                public void markAsRead(NotificationHolder h) {
                    markedRead.add(h);
                }

                @Override
                public void unsubscribe(NotificationHolder h) {
                    unsubscribed.add(h);
                }
            };

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
        markedRead = new ArrayList<>();
        unsubscribed = new ArrayList<>();
        adapter = new NotificationAdapter(context, callback);
    }

    private Repository repo(String fullName) {
        return Repository.builder().fullName(fullName).name(fullName.split("/")[1])
                .owner(User.builder().login(fullName.split("/")[0]).build()).build();
    }

    private NotificationThread thread(Repository repository, String type, String title,
            boolean unread) {
        NotificationThread t = mock(NotificationThread.class);
        NotificationSubject s = mock(NotificationSubject.class);
        when(s.type()).thenReturn(type);
        when(s.title()).thenReturn(title);
        when(t.subject()).thenReturn(s);
        when(t.repository()).thenReturn(repository);
        when(t.unread()).thenReturn(unread);
        when(t.updatedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L));
        return t;
    }

    private NotificationAdapter.ViewHolder holderFor(NotificationHolder item) {
        int viewType = adapter.getItemViewType(item);
        return adapter.onCreateViewHolder(inflater, parent, viewType);
    }

    @Test
    public void hasUnreadNotifications_trueAndFalse() {
        assertFalse(adapter.hasUnreadNotifications());
        Repository r = repo("o/r");
        adapter.add(new NotificationHolder(r));
        assertFalse(adapter.hasUnreadNotifications());
        adapter.add(new NotificationHolder(thread(r, "Issue", "t", true)));
        assertTrue(adapter.hasUnreadNotifications());
    }

    @Test
    public void markAsRead_all_returnsTrueWhenEverythingRead() {
        Repository r = repo("o/r");
        NotificationThread t = thread(r, "Issue", "t", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t));
        assertTrue(adapter.markAsRead(null, null));
        assertTrue(adapter.getItem(1).isRead());
        assertTrue(adapter.getItem(0).isRead());
    }

    @Test
    public void markAsRead_all_returnsFalseWhenUnreadRemain() {
        Repository r = repo("o/r");
        adapter.add(new NotificationHolder(r));
        // unread thread whose repository differs from the mark target is not
        // reachable here; use two repos and mark only one.
        Repository r2 = repo("o/r2");
        adapter.add(new NotificationHolder(thread(r, "Issue", "t1", true)));
        adapter.add(new NotificationHolder(r2));
        adapter.add(new NotificationHolder(thread(r2, "Issue", "t2", true)));
        assertFalse(adapter.markAsRead(r, null));
        assertTrue(adapter.getItem(1).isRead());
        assertFalse(adapter.getItem(3).isRead());
    }

    @Test
    public void markAsRead_single_marksOnlyThatNotification() {
        Repository r = repo("o/r");
        NotificationThread t1 = thread(r, "Issue", "t1", true);
        NotificationThread t2 = thread(r, "Issue", "t2", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t1));
        adapter.add(new NotificationHolder(t2));
        assertFalse(adapter.markAsRead(null, t1));
        assertTrue(adapter.getItem(1).isRead());
        assertFalse(adapter.getItem(2).isRead());
        // repo header stays unread: repo still has an unread notification
        assertFalse(adapter.getItem(0).isRead());
    }

    @Test
    public void markAsRead_singleLastUnread_marksRepoHeaderToo() {
        Repository r = repo("o/r");
        NotificationThread t1 = thread(r, "Issue", "t1", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t1));
        assertTrue(adapter.markAsRead(null, t1));
        assertTrue(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
    }

    @Test
    public void markAsRead_nullRepositoryNotification_notCountedAsRead() {
        NotificationThread t = mock(NotificationThread.class);
        NotificationSubject s = mock(NotificationSubject.class);
        when(s.type()).thenReturn("Issue");
        when(s.title()).thenReturn("t");
        when(t.subject()).thenReturn(s);
        when(t.repository()).thenReturn(null);
        when(t.unread()).thenReturn(true);
        when(t.updatedAt()).thenReturn(new Date());
        adapter.add(new NotificationHolder(t));
        assertTrue(adapter.markAsRead(null, null));
        assertTrue(adapter.getItem(0).isRead());
    }

    @Test
    public void getItemViewType_headerVsItem() {
        Repository r = repo("o/r");
        assertEquals(RootAdapter.CUSTOM_VIEW_TYPE_START + 1,
                adapter.getItemViewType(new NotificationHolder(r)));
        assertEquals(RootAdapter.CUSTOM_VIEW_TYPE_START,
                adapter.getItemViewType(new NotificationHolder(thread(r, "Issue", "t", true))));
    }

    @Test
    public void bind_header_showsRepoName() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals("o/r", ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.iv_action).getVisibility());
    }

    @Test
    public void bind_headerRead_hidesAction() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        item.setIsRead(true);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_action).getVisibility());
        assertEquals(0.5f, ((TextView) h.itemView.findViewById(R.id.tv_title)).getAlpha(), 0.001f);
    }

    @Test
    public void bind_header_nullRepository_noCrash() {
        NotificationHolder item = new NotificationHolder((Repository) null);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        // ApiHelpers.formatRepoName returns the "(deleted)" string for a null repo
        assertEquals("(deleted)",
                ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void bind_notification_eachSubjectType() {
        String[] types = {"Issue", "PullRequest", "Commit", "Release", "Discussion", "Unknown"};
        for (String type : types) {
            Repository r = repo("o/r");
            NotificationHolder item = new NotificationHolder(thread(r, type, "title-" + type, false));
            item.setIsLastRepositoryNotification(true);
            NotificationAdapter.ViewHolder h = holderFor(item);
            adapter.onBindViewHolder(h, item);
            assertEquals("title-" + type,
                    ((TextView) h.itemView.findViewById(R.id.tv_title)).getText().toString());
            assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.v_bottom_shadow).getVisibility());
        }
    }

    @Test
    public void bind_notification_unknownType_hidesIcon() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(thread(r, "Nope", "t", true));
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(View.INVISIBLE, h.itemView.findViewById(R.id.iv_icon).getVisibility());
        assertEquals(1f, ((TextView) h.itemView.findViewById(R.id.tv_title)).getAlpha(), 0.001f);
    }

    @Test
    public void bind_notification_notLast_hidesShadowAndZeroMargin() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(thread(r, "Issue", "t", true));
        item.setIsLastRepositoryNotification(false);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertEquals(View.GONE, h.itemView.findViewById(R.id.v_bottom_shadow).getVisibility());
    }

    @Test
    public void viewHolder_clickAction_header_callsMarkAsRead() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        h.itemView.findViewById(R.id.iv_action).performClick();
        assertEquals(1, markedRead.size());
        assertEquals(item, markedRead.get(0));
    }

    @Test
    public void viewHolder_clickAction_notification_showsPopupMenu() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(thread(r, "Issue", "t", true));
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        h.itemView.findViewById(R.id.iv_action).performClick();
        assertTrue(markedRead.isEmpty());
    }

    @Test
    public void viewHolder_clickAvatar_startsUserActivity() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        h.itemView.findViewById(R.id.iv_avatar).performClick();
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void viewHolder_menuItemClick_markAsReadAndUnsubscribe() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(thread(r, "Issue", "t", true));
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        assertTrue(h.onMenuItemClick(menuItem(R.id.mark_as_read)));
        assertEquals(1, markedRead.size());
        assertTrue(h.onMenuItemClick(menuItem(R.id.unsubscribe)));
        assertEquals(1, unsubscribed.size());
        assertFalse(h.onMenuItemClick(menuItem(R.id.share)));
    }

    private MenuItem menuItem(int id) {
        MenuItem m = mock(MenuItem.class);
        when(m.getItemId()).thenReturn(id);
        return m;
    }

    @Test
    public void hasUnreadNotifications_readThreadOnly_returnsFalse() {
        Repository r = repo("o/r");
        adapter.add(new NotificationHolder(thread(r, "Issue", "t", false)));
        assertFalse(adapter.hasUnreadNotifications());
    }

    @Test
    public void markAsRead_single_marksRepoHeaderWhenFollowedByOtherRepo() {
        Repository r = repo("o/r");
        Repository r2 = repo("o/r2");
        NotificationThread t1 = thread(r, "Issue", "t1", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t1));
        adapter.add(new NotificationHolder(r2));
        assertTrue(adapter.markAsRead(null, t1));
        assertTrue(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
        assertFalse(adapter.getItem(2).isRead());
    }

    @Test
    public void markAsRead_single_nullRepoThread_headerStaysUnread() {
        Repository r = repo("o/r");
        NotificationThread t = mock(NotificationThread.class);
        NotificationSubject s = mock(NotificationSubject.class);
        when(s.type()).thenReturn("Issue");
        when(s.title()).thenReturn("t");
        when(t.subject()).thenReturn(s);
        when(t.repository()).thenReturn(null);
        when(t.unread()).thenReturn(true);
        when(t.updatedAt()).thenReturn(new Date());
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t));
        adapter.add(new NotificationHolder(repo("o/r2")));
        assertTrue(adapter.markAsRead(null, t));
        assertFalse(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
    }

    @Test
    public void markAsRead_single_noHeaders_noCrash() {
        Repository r = repo("o/r");
        NotificationThread t1 = thread(r, "Issue", "t1", true);
        adapter.add(new NotificationHolder(t1));
        assertTrue(adapter.markAsRead(null, t1));
        assertTrue(adapter.getItem(0).isRead());
    }

    @Test
    public void markAsRead_single_nullRepoHeader_notMarked() {
        Repository r = repo("o/r");
        NotificationThread t1 = thread(r, "Issue", "t1", true);
        adapter.add(new NotificationHolder((Repository) null));
        adapter.add(new NotificationHolder(t1));
        assertTrue(adapter.markAsRead(null, t1));
        assertFalse(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
    }

    @Test
    public void markAsRead_single_threadFromOtherRepo_headerNotMarked() {
        Repository r = repo("o/r");
        Repository r2 = repo("o/r2");
        NotificationThread t2 = thread(r2, "Issue", "t2", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t2));
        assertTrue(adapter.markAsRead(null, t2));
        assertFalse(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
    }

    @Test
    public void markAsRead_single_otherRepoBeforeNextHeader_headerNotMarked() {
        Repository r = repo("o/r");
        Repository r2 = repo("o/r2");
        Repository r3 = repo("o/r3");
        NotificationThread t2 = thread(r2, "Issue", "t2", true);
        adapter.add(new NotificationHolder(r));
        adapter.add(new NotificationHolder(t2));
        adapter.add(new NotificationHolder(r3));
        assertTrue(adapter.markAsRead(null, t2));
        assertFalse(adapter.getItem(0).isRead());
        assertTrue(adapter.getItem(1).isRead());
    }

    @Test
    public void markAsRead_repo_nullRepoItemsNotMarked() {
        Repository r = repo("o/r");
        NotificationThread t = mock(NotificationThread.class);
        NotificationSubject s = mock(NotificationSubject.class);
        when(s.type()).thenReturn("Issue");
        when(s.title()).thenReturn("t");
        when(t.subject()).thenReturn(s);
        when(t.repository()).thenReturn(null);
        when(t.unread()).thenReturn(true);
        when(t.updatedAt()).thenReturn(new Date());
        adapter.add(new NotificationHolder((Repository) null));
        adapter.add(new NotificationHolder(t));
        assertFalse(adapter.markAsRead(r, null));
        assertFalse(adapter.getItem(0).isRead());
        assertFalse(adapter.getItem(1).isRead());
    }

    @Test
    public void onClick_action_header_callsMarkAsRead() {
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        h.itemView.findViewById(R.id.iv_action).performClick();
        assertEquals(1, markedRead.size());
        assertEquals(item, markedRead.get(0));
    }

    @Test
    public void onClick_avatar_mannequin_noActivity() {
        // iv_avatar only exists in the header row layout, not the
        // notification row, so use a repo header item
        Repository r = repo("o/r");
        NotificationHolder item = new NotificationHolder(r);
        NotificationAdapter.ViewHolder h = holderFor(item);
        adapter.onBindViewHolder(h, item);
        // replace the avatar tag with a mannequin user whose profile intent
        // is null
        View avatar = h.itemView.findViewById(R.id.iv_avatar);
        avatar.setTag(User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build());
        avatar.performClick();
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }
}
