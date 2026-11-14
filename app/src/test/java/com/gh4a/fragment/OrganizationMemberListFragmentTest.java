package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.reactivex.Single;
import retrofit2.Response;

/** OrganizationMemberListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class OrganizationMemberListFragmentTest {

    public static class NoLoad extends OrganizationMemberListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String org) {
        NoLoad f = new NoLoad();
        f.setArguments(OrganizationMemberListFragment.newInstance(org).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesOrg() {
        OrganizationMemberListFragment f =
                OrganizationMemberListFragment.newInstance("github");
        assertEquals("github", f.getArguments().getString("org"));
    }

    @Test
    public void getEmptyTextResId_isNoOrgMembers() {
        assertEquals(R.string.no_org_members_found,
                new OrganizationMemberListFragment().getEmptyTextResId());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("github");
        Single<Response<Page<User>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("github").onCreateAdapter());
    }

    @Test
    public void onItemClick_startsUserActivity() {
        NoLoad f = attached("github");
        FragmentActivity activity = f.getActivity();
        f.onItemClick(User.builder().login("octocat").build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("login"));
    }

    @Test
    public void onItemClick_nullItemStartsNothing() {
        NoLoad f = attached("github");
        FragmentActivity activity = f.getActivity();
        f.onItemClick(null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
