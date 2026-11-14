package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.RepositoryListActivity;
import com.gh4a.fragment.RepositoryListContainerFragment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class UserReposLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octocat?tab=repositories");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private User user(UserType type, String login) {
        User u = mock(User.class);
        when(u.type()).thenReturn(type);
        when(u.login()).thenReturn(login);
        return u;
    }

    @Test
    public void constructor_storesShowStars() {
        FragmentActivity activity = activity();
        UserReposLoadTask stars = new UserReposLoadTask(activity, URL, "octo", true);
        assertTrue(stars.mShowStars);
        assertEquals("octo", stars.mUserLogin);
        assertFalse(new UserReposLoadTask(activity, URL, "octo", false).mShowStars);
    }

    @Test
    public void getIntent_organization_opensRepoListAsOrgNoFilter() {
        FragmentActivity activity = activity();
        UserReposLoadTask task = new UserReposLoadTask(activity, URL, "someorg", true);
        Intent intent = task.getIntent(user(UserType.Organization, "someorg"));
        assertEquals(RepositoryListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("someorg", intent.getStringExtra("user"));
        assertTrue(intent.getBooleanExtra("is_org", false));
        assertNull(intent.getStringExtra("filter_type"));
    }

    @Test
    public void getIntent_organizationWithoutStars_opensRepoListAsOrg() {
        FragmentActivity activity = activity();
        UserReposLoadTask task = new UserReposLoadTask(activity, URL, "someorg", false);
        Intent intent = task.getIntent(user(UserType.Organization, "someorg"));
        assertTrue(intent.getBooleanExtra("is_org", false));
        assertNull(intent.getStringExtra("filter_type"));
    }

    @Test
    public void getIntent_regularUserWithStars_usesStarredFilter() {
        FragmentActivity activity = activity();
        UserReposLoadTask task = new UserReposLoadTask(activity, URL, "octo", true);
        Intent intent = task.getIntent(user(UserType.User, "octo"));
        assertEquals(RepositoryListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("user"));
        assertFalse(intent.getBooleanExtra("is_org", true));
        assertEquals(RepositoryListContainerFragment.FILTER_TYPE_STARRED,
                intent.getStringExtra("filter_type"));
    }

    @Test
    public void getIntent_regularUserWithoutStars_noFilter() {
        FragmentActivity activity = activity();
        UserReposLoadTask task = new UserReposLoadTask(activity, URL, "octo", false);
        Intent intent = task.getIntent(user(UserType.User, "octo"));
        assertFalse(intent.getBooleanExtra("is_org", true));
        assertNull(intent.getStringExtra("filter_type"));
    }

    @Test
    public void getIntent_nullType_treatedAsRegularUser() {
        FragmentActivity activity = activity();
        UserReposLoadTask task = new UserReposLoadTask(activity, URL, "octo", true);
        Intent intent = task.getIntent(user(null, "octo"));
        assertFalse(intent.getBooleanExtra("is_org", true));
        assertEquals(RepositoryListContainerFragment.FILTER_TYPE_STARRED,
                intent.getStringExtra("filter_type"));
    }
}
