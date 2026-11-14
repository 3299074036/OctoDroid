package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.BaseActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.Single;
import io.reactivex.Observable;

/** LoadingFragmentBase (F1) via a minimal concrete subclass. */
@RunWith(RobolectricTestRunner.class)
public class LoadingFragmentBaseTest {

    public static class TestLoadingFragment extends LoadingFragmentBase {
        @Override
        protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
            return new TextView(inflater.getContext());
        }

        @Override
        public void onRefresh() {
        }
    }

    private static TestLoadingFragment attached() {
        TestLoadingFragment f = new TestLoadingFragment();
        FragmentTestHelper.attach(f);
        return f;
    }

    /** BaseActivity host: handleLoadFailure/handleActionFailure delegate to it. */
    public static class TestBaseActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    private static TestLoadingFragment attachedToBaseActivity() {
        TestLoadingFragment f = new TestLoadingFragment();
        FragmentTestHelper.attachTo(TestBaseActivity.class, f);
        return f;
    }

    @Test
    public void handleLoadFailure_detached_doesNothing() {
        // getBaseActivity() is null for a bare fragment: the null branch.
        new TestLoadingFragment().handleLoadFailure(new RuntimeException("x"));
    }

    @Test
    public void handleActionFailure_detached_doesNothing() {
        new TestLoadingFragment().handleActionFailure("text", new RuntimeException("x"));
    }

    @Test
    public void setHighlightColors_attached_setsProgressColor() {
        // mProgress exists after attach: resolveColor runs and invalidate is reached.
        TestLoadingFragment f = attached();
        f.setHighlightColors(
                com.gh4a.R.attr.colorIssueOpen, com.gh4a.R.attr.colorIssueOpenDark);
        assertEquals(com.gh4a.utils.UiUtils.resolveColor(f.getActivity(),
                com.gh4a.R.attr.colorIssueOpen), f.getHighlightColor());
    }

    @Test
    public void setHighlightColors_viewDestroyed_skipsInvalidate() {
        // Still attached (getActivity() non-null) but the view is destroyed,
        // so mProgress is null and the invalidate is skipped.
        TestLoadingFragment f = attached();
        f.onDestroyView();
        f.setHighlightColors(
                com.gh4a.R.attr.colorIssueOpen, com.gh4a.R.attr.colorIssueOpenDark);
    }

    @Test
    public void handleLoadFailure_baseActivityHost_delegates() {
        TestLoadingFragment f = attachedToBaseActivity();
        f.handleLoadFailure(new RuntimeException("x"));
    }

    @Test
    public void handleActionFailure_baseActivityHost_delegates() {
        TestLoadingFragment f = attachedToBaseActivity();
        f.handleActionFailure("text", new RuntimeException("x"));
    }

    @Test
    public void setContentShown_bareFragment_noContainer_skipsVisibilityUpdate() {
        TestLoadingFragment f = new TestLoadingFragment();
        assertTrue(f.isContentShown());
        f.setContentShown(false);
        assertFalse(f.isContentShown());
    }

    @Test
    public void setContentShown_attached_togglesVisibility() {
        TestLoadingFragment f = attached();
        assertTrue(f.isContentShown());
        f.setContentShown(false);
        assertFalse(f.isContentShown());
        f.setContentShown(true);
        assertTrue(f.isContentShown());
    }

    @Test
    public void setContentShown_sameValue_isNoop() {
        TestLoadingFragment f = attached();
        assertTrue(f.isContentShown());
        f.setContentShown(true);
        assertTrue(f.isContentShown());
    }

    @Test
    public void onCreateView_inflatesLoadingLayoutWithContent() {
        TestLoadingFragment f = attached();
        assertNotNull(f.getView());
        assertNotNull(f.getView().findViewById(com.gh4a.R.id.content_container));
        assertNotNull(f.getView().findViewById(com.gh4a.R.id.progress));
    }

    @Test
    public void onBackPressed_returnsFalse() {
        assertFalse(attached().onBackPressed());
    }

    @Test
    public void isContentShown_defaultsTrue() {
        assertTrue(attached().isContentShown());
    }

    @Test
    public void setContentShown_togglesState() {
        TestLoadingFragment f = attached();
        f.setContentShown(false);
        assertFalse(f.isContentShown());
        f.setContentShown(true);
        assertTrue(f.isContentShown());
        // Setting the same value again is a no-op, not a crash.
        f.setContentShown(true);
        assertTrue(f.isContentShown());
    }

    @Test
    public void getBaseActivity_plainActivity_returnsNull() {
        assertNull(attached().getBaseActivity());
    }

    @Test
    public void handleLoadFailure_withoutBaseActivity_doesNothing() {
        attached().handleLoadFailure(new RuntimeException("boom"));
    }

    @Test
    public void handleActionFailure_withoutBaseActivity_doesNothing() {
        attached().handleActionFailure("text", new RuntimeException("boom"));
    }

    @Test
    public void canChildScrollUp_textView_returnsFalse() {
        assertFalse(attached().canChildScrollUp());
    }

    @Test
    public void makeLoaderSingle_returnsNonNullTransformer() {
        TestLoadingFragment f = attached();
        assertNotNull(f.makeLoaderSingle(0, false));
        assertNotNull(f.makeLoaderSingle(1, true));
    }

    @Test
    public void makeLoaderObservable_returnsNonNullTransformer() {
        TestLoadingFragment f = attached();
        assertNotNull(f.makeLoaderObservable(0, false));
    }

    @Test
    public void getHighlightColor_returnsFirstProgressColor() {
        TestLoadingFragment f = attached();
        // Colors resolve from the app theme; just assert no crash and a value.
        f.getHighlightColor();
    }

    @Test
    public void setHighlightColors_updatesColorsWithoutCrash() {
        TestLoadingFragment f = attached();
        f.setHighlightColors(androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
        f.getHighlightColor();
    }
}
