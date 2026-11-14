package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.model.Trend;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** TrendingFragment：趋势榜（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class TrendingFragmentTest {
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
    public void newInstance_nullTypeReturnsNull() {
        assertNull(TrendingFragment.newInstance(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstance_unknownTypeThrows() {
        TrendingFragment.newInstance("bogus");
    }

    @Test
    public void newInstance_dailyStoresTypeAndStarsTemplate() {
        TrendingFragment f = TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY);
        assertEquals("daily", f.getArguments().getString("type"));
        assertEquals(R.string.trend_stars_today,
                f.getArguments().getInt("stars_template", 0));
    }

    @Test
    public void newInstance_weeklyAndMonthlyTemplates() {
        assertEquals(R.string.trend_stars_week,
                TrendingFragment.newInstance(TrendingFragment.TYPE_WEEKLY)
                        .getArguments().getInt("stars_template", 0));
        assertEquals(R.string.trend_stars_month,
                TrendingFragment.newInstance(TrendingFragment.TYPE_MONTHLY)
                        .getArguments().getInt("stars_template", 0));
    }

    @Test
    public void onCreate_readsTypeAndStarsTemplate() throws Exception {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_WEEKLY));
        Field type = TrendingFragment.class.getDeclaredField("mType");
        Field template = TrendingFragment.class.getDeclaredField("mStarsTemplate");
        type.setAccessible(true);
        template.setAccessible(true);
        assertEquals("weekly", type.get(f));
        assertEquals(R.string.trend_stars_week, template.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        assertEquals(R.string.no_trends_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_returnsLazySinglesWithoutSubscribing() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        // 只建 Single 不订阅：趋势拉取在订阅前不发真实请求。
        assertNotNull(f.onCreateDataSingle(false));
        assertNotNull(f.onCreateDataSingle(true));
    }

    @Test
    public void makeRangeSwipeListener_returnsListener() throws Exception {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        java.lang.reflect.Method m =
                TrendingFragment.class.getDeclaredMethod("makeRangeSwipeListener");
        m.setAccessible(true);
        assertNotNull(m.invoke(f));
    }

    @Test
    public void onItemClick_withOwnerAndNameStartsRepositoryActivity() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        f.onItemClick(new Trend("octo", "cat", "desc", "Java", 100, 5, 10));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }

    @Test
    public void onItemClick_nullOwnerStartsNothing() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        f.onItemClick(new Trend(null, "cat", "desc", "Java", 100, 5, 10));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        f.onItemClick(new Trend("octo", null, "desc", "Java", 100, 5, 10));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }
}
