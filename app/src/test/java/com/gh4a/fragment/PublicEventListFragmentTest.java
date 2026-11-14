package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.model.GitHubEventType;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.payload.PushPayload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Collections;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * PublicEventListFragment / PrivateEventListFragment / PublicTimelineFragment
 * (F1): the three EventListFragment subclasses. Shared onItemClick behavior
 * is covered through PublicEventListFragment with a PushEvent.
 */
@RunWith(RobolectricTestRunner.class)
public class PublicEventListFragmentTest {

    public static class NoLoadPublic extends PublicEventListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    public static class NoLoadPrivate extends PrivateEventListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    public static class NoLoadTimeline extends PublicTimelineFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoadPublic attachedPublic(User user) {
        NoLoadPublic f = new NoLoadPublic();
        f.setArguments(PublicEventListFragment.newInstance(user).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    private static NoLoadPublic attachedPublic(String login) {
        NoLoadPublic f = new NoLoadPublic();
        f.setArguments(PublicEventListFragment.newInstance(login).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    private static NoLoadPrivate attachedPrivate(String login, String org) {
        NoLoadPrivate f = new NoLoadPrivate();
        f.setArguments(PrivateEventListFragment.newInstance(login, org).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    private static NoLoadTimeline attachedTimeline() {
        NoLoadTimeline f = new NoLoadTimeline();
        f.setArguments(PublicTimelineFragment.newInstance().getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    static GitHubEvent pushEvent(String repo) {
        return GitHubEvent.builder()
                .type(GitHubEventType.PushEvent)
                .id("evt1")
                .repo(GitHubEvent.RepoIdentifier.builder().repoWithUserName(repo).build())
                .payload(PushPayload.builder()
                        .commits(Collections.singletonList(
                                GitCommit.builder().sha("abc123").build()))
                        .before("0000000").head("abc123").build())
                .build();
    }

    @Test
    public void newInstance_fromUser_storesLoginAndOrgFlag() {
        User org = User.builder().login("github").type(UserType.Organization).build();
        PublicEventListFragment f = PublicEventListFragment.newInstance(org);
        assertEquals("github", f.getArguments().getString("login"));
        assertEquals(true, f.getArguments().getBoolean("org"));

        User user = User.builder().login("octocat").type(UserType.User).build();
        PublicEventListFragment g = PublicEventListFragment.newInstance(user);
        assertEquals("octocat", g.getArguments().getString("login"));
        assertEquals(false, g.getArguments().getBoolean("org"));
    }

    @Test
    public void newInstance_fromLogin_storesLoginNotOrg() {
        PublicEventListFragment f = PublicEventListFragment.newInstance("octocat");
        assertEquals("octocat", f.getArguments().getString("login"));
        assertEquals(false, f.getArguments().getBoolean("org"));
    }

    @Test
    public void newInstance_private_storesLoginAndOrg() {
        PrivateEventListFragment f = PrivateEventListFragment.newInstance("octocat", "github");
        assertEquals("octocat", f.getArguments().getString("login"));
        assertEquals("github", f.getArguments().getString("org"));
        PrivateEventListFragment g = PrivateEventListFragment.newInstance("octocat", null);
        assertEquals(null, g.getArguments().getString("org"));
    }

    @Test
    public void newInstance_timeline_hasEmptyArgs() {
        assertNotNull(PublicTimelineFragment.newInstance().getArguments());
    }

    @Test
    public void loadRawPage_public_returnsNonNullSingleWithoutNetwork() {
        NoLoadPublic f = attachedPublic("octocat");
        Single<Response<Page<GitHubEvent>>> single = f.loadRawPage(1, false);
        assertNotNull(single);
    }

    @Test
    public void loadRawPage_publicOrg_returnsNonNullSingleWithoutNetwork() {
        User org = User.builder().login("github").type(UserType.Organization).build();
        assertNotNull(attachedPublic(org).loadRawPage(1, false));
    }

    @Test
    public void loadRawPage_privateWithOrg_returnsNonNullSingleWithoutNetwork() {
        assertNotNull(attachedPrivate("octocat", "github").loadRawPage(1, false));
    }

    @Test
    public void loadRawPage_privateWithoutOrg_returnsNonNullSingleWithoutNetwork() {
        NoLoadPrivate f = attachedPrivate("octocat", null);
        Single<Response<Page<GitHubEvent>>> single = f.loadRawPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadRawPage(2, true));
    }

    @Test
    public void loadRawPage_timeline_returnsNonNullSingleWithoutNetwork() {
        assertNotNull(attachedTimeline().loadRawPage(1, false));
    }

    @Test
    public void getEmptyTextResId_isNoEvents() {
        assertEquals(R.string.no_events_found, attachedPublic("o").getEmptyTextResId());
        assertEquals(R.string.no_events_found, attachedPrivate("o", null).getEmptyTextResId());
        assertEquals(R.string.no_events_found, attachedTimeline().getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attachedPublic("o").onCreateAdapter());
        assertNotNull(attachedTimeline().onCreateAdapter());
    }

    @Test
    public void onItemClick_pushEvent_startsCommitActivity() {
        NoLoadPublic f = attachedPublic("octocat");
        FragmentActivity activity = f.getActivity();
        f.onItemClick(pushEvent("octocat/hello"));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.CommitActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals("abc123", started.getExtras().getString("sha"));
    }

    @Test
    public void onItemClick_pushEventWithoutRepo_showsToastStartsNothing() {
        NoLoadPublic f = attachedPublic("octocat");
        FragmentActivity activity = f.getActivity();
        GitHubEvent event = GitHubEvent.builder()
                .type(GitHubEventType.PushEvent).id("evt2")
                .payload(PushPayload.builder()
                        .commits(Collections.singletonList(
                                GitCommit.builder().sha("abc123").build()))
                        .before("0000000").head("abc123").build())
                .build();
        f.onItemClick(event);
        assertEquals(null, Shadows.shadowOf(activity).getNextStartedActivity());
        String toast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast();
        assertNotNull("repo-missing push event must show a toast", toast);
        assertEquals(false, toast.isEmpty());
    }

    @Test
    public void onItemClick_nullPayloadStartsNothing() {
        NoLoadPublic f = attachedPublic("octocat");
        FragmentActivity activity = f.getActivity();
        GitHubEvent event = GitHubEvent.builder()
                .type(GitHubEventType.PushEvent).id("evt3")
                .repo(GitHubEvent.RepoIdentifier.builder().repoWithUserName("o/r").build())
                .build();
        f.onItemClick(event);
        assertEquals(null, Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
