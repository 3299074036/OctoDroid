package com.gh4a.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** ReleaseRadarFragment：Release 雷达（RadarItem + newInstance/onCreate/empty 文本）。 */
@RunWith(RobolectricTestRunner.class)
public class ReleaseRadarFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void radarItem_ctorKeepsFields() {
        ReleaseRadarFragment.RadarItem item = new ReleaseRadarFragment.RadarItem(
                "octo", "cat", 11L, "v1.0", "Release 1.0", 1700000000000L, 1699900000000L);
        assertEquals("octo", item.owner);
        assertEquals("cat", item.repo);
        assertEquals(11L, item.releaseId);
        assertEquals("v1.0", item.tagName);
        assertEquals("Release 1.0", item.releaseName);
        assertEquals(1700000000000L, item.publishedAt);
        assertEquals(1699900000000L, item.createdAt);
    }

    @Test
    public void radarItem_sortTimePrefersPublishedAt() {
        ReleaseRadarFragment.RadarItem withPublished = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 5000L, 3000L);
        assertEquals(5000L, withPublished.sortTime());
        ReleaseRadarFragment.RadarItem withoutPublished = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 0L, 3000L);
        assertEquals(3000L, withoutPublished.sortTime());
        ReleaseRadarFragment.RadarItem neither = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 0L, 0L);
        assertEquals(0L, neither.sortTime());
    }

    @Test
    public void newInstance_storesLogin() {
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLogin() throws Exception {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        Field login = ReleaseRadarFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals("alice", login.get(f));
    }

    @Test
    public void onCreate_nullArgumentsMeansNullLogin() throws Exception {
        ReleaseRadarFragment f = attach(new ReleaseRadarFragment());
        Field login = ReleaseRadarFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals(null, login.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        assertEquals(R.string.no_release_radar_items, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        assertNotNull(f.onCreateAdapter());
    }
}
