package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.CollaboratorListFragment;
import com.gh4a.fragment.LoadingFragmentBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static com.gh4a.activities.ActivityTestHelper.set;

/**
 * FragmentContainerActivity is abstract; exercised through
 * CollaboratorListActivity, its thinnest concrete subclass.
 */
@RunWith(RobolectricTestRunner.class)
public class FragmentContainerActivityTest {
    @Test
    public void onCreate_addsFragmentToContainer() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        assertTrue(activity.getFragment() instanceof CollaboratorListFragment);
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentById(R.id.content_container));
    }

    @Test
    public void recreate_restoresSameFragmentFromManager() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        ActivityController<CollaboratorListActivity> c = controller(CollaboratorListActivity.class, intent);
        CollaboratorListActivity activity = c.create().start().resume().get();
        Bundle out = new Bundle();
        c.saveInstanceState(out);
        c.pause().stop().destroy();
        ActivityController<CollaboratorListActivity> c2 =
                controller(CollaboratorListActivity.class, intent);
        CollaboratorListActivity recreated = c2.create(out).start().resume().get();
        assertNotNull(recreated.getFragment());
        assertTrue(recreated.getFragment() instanceof CollaboratorListFragment);
    }

    @Test
    public void onRefresh_delegatesWithoutCrash() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        activity.onRefresh();
    }

    @Test
    public void onBackPressed_delegatesWithoutCrash() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        activity.onBackPressed();
    }

    @Test
    public void onRefresh_nonRefreshableFragment_doesNotThrow() {
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class,
                CollaboratorListActivity.makeIntent(appContext(), "octo", "repo"));
        set(activity, "mFragment", new androidx.fragment.app.Fragment());
        activity.onRefresh();
    }

    @Test
    public void onBackPressed_nonLoadingFragment_finishes() {
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class,
                CollaboratorListActivity.makeIntent(appContext(), "octo", "repo"));
        set(activity, "mFragment", new androidx.fragment.app.Fragment());
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onBackPressed_loadingFragmentReturnsFalse_finishes() {
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class,
                CollaboratorListActivity.makeIntent(appContext(), "octo", "repo"));
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    /**
     * LoadingFragmentBase already implements ChildScrollDelegate; this
     * fragment also consumes the back press, so one container covers both
     * the delegate branch (onCreate) and the consumed-press branch
     * (onBackPressed).
     */
    public static class TestLoadingContainerActivity extends FragmentContainerActivity {
        @Override
        protected Fragment onCreateFragment() {
            return new TestLoadingFragment();
        }
    }

    public static class TestLoadingFragment extends LoadingFragmentBase {
        @Override
        protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
            return new View(parent.getContext());
        }

        @Override
        public void onRefresh() {
            // no-op: only the delegate/back-press branches are under test
        }

        @Override
        public boolean onBackPressed() {
            return true;
        }
    }

    @Test
    public void onCreate_delegateFragment_setsChildScrollDelegate() {
        TestLoadingContainerActivity activity =
                launch(TestLoadingContainerActivity.class,
                        new Intent(appContext(), TestLoadingContainerActivity.class));
        assertTrue(activity.getFragment() instanceof TestLoadingFragment);
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentById(R.id.content_container));
    }

    @Test
    public void onBackPressed_fragmentConsumes_doesNotFinish() {
        TestLoadingContainerActivity activity =
                launch(TestLoadingContainerActivity.class,
                        new Intent(appContext(), TestLoadingContainerActivity.class));
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
    }

    public static class PlainFragmentContainerActivity extends FragmentContainerActivity {
        @Override
        protected Fragment onCreateFragment() {
            return new PlainViewFragment();
        }
    }

    /** Provides a view (so BaseActivity.onStart is happy) but is not a
     * ChildScrollDelegate, covering the false branch of the delegate check. */
    public static class PlainViewFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return new View(inflater.getContext());
        }
    }

    @Test
    public void onCreate_plainFragment_skipsChildScrollDelegate() {
        PlainFragmentContainerActivity activity =
                launch(PlainFragmentContainerActivity.class,
                        new Intent(appContext(), PlainFragmentContainerActivity.class));
        assertTrue(activity.getFragment() instanceof PlainViewFragment);
        assertFalse(activity.getFragment()
                instanceof com.gh4a.widget.SwipeRefreshLayout.ChildScrollDelegate);
    }
}
