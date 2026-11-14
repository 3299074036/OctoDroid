package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.appcompat.view.menu.MenuBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.PrivateEventListFragment;
import com.gh4a.fragment.PublicTimelineFragment;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class FeedHubFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            assertEquals(R.string.feed_hub, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(2, tabs.length);
            assertEquals(R.string.feed_tab_following, tabs[0]);
            assertEquals(R.string.feed_tab_public, tabs[1]);
        }
    }

    @Test
    public void refreshMenuOnTabSwitch_isTrue() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            assertTrue(factory.refreshMenuOnTabSwitch());
        }
    }

    @Test
    public void makeFragment_followingTab_returnsPrivateEvents() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            // 当前 Tab 为 0（关注页）
            assertEquals(0, activity.getCurrentTabPosition());
            assertTrue(factory.makeFragment(0) instanceof PrivateEventListFragment);
        }
    }

    @Test
    public void makeFragment_publicTab_returnsPublicTimeline() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            assertTrue(factory.makeFragment(1) instanceof PublicTimelineFragment);
        }
    }

    @Test
    public void onCreateOptionsMenu_withoutUserInfo_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            MenuBuilder menu = new MenuBuilder(activity);
            assertFalse(factory.onCreateOptionsMenu(menu));
        }
    }

    @Test
    public void setUserInfo_thenDestroy_doNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            com.meisolsson.githubsdk.model.User user =
                    com.meisolsson.githubsdk.model.User.builder().login("octocat").build();
            factory.setUserInfo(user);
            factory.onDestroy();
            factory.onRefresh();
        }
    }

    @Test
    public void onStartLoadingData_delegatesWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            factory.onStartLoadingData();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNotNull(factory);
        }
    }

    @Test
    public void onRefresh_delegatesWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            factory.onRefresh();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNotNull(factory);
        }
    }

    // ---- A2 第二轮补缺口：FeedHubFactory ----

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesAndReturnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FeedHubFactory factory = new FeedHubFactory(activity, "octocat");
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(factory.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

}
