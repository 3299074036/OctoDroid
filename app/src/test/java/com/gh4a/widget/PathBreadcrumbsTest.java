package com.gh4a.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(RobolectricTestRunner.class)
public class PathBreadcrumbsTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ViewGroup childFrame(PathBreadcrumbs view) {
        return (ViewGroup) view.getChildAt(0);
    }

    private TextView crumbLabel(PathBreadcrumbs view, int index) {
        ViewGroup crumb = (ViewGroup) childFrame(view).getChildAt(index);
        return (TextView) crumb.getChildAt(0);
    }

    private void measureAndLayout(PathBreadcrumbs view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 500, 100);
    }

    @Test
    public void setPath_nestedPath_createsRootPlusSegments() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b/c");
        assertEquals(4, childFrame(view).getChildCount());
        assertEquals("/", crumbLabel(view, 0).getText().toString());
        assertEquals("a", crumbLabel(view, 1).getText().toString());
        assertEquals("b", crumbLabel(view, 2).getText().toString());
        assertEquals("c", crumbLabel(view, 3).getText().toString());
    }

    @Test
    public void setPath_nestedPath_marksLastCrumbActive() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b/c");
        assertFalse(crumbLabel(view, 0).isActivated());
        assertFalse(crumbLabel(view, 2).isActivated());
        assertTrue(crumbLabel(view, 3).isActivated());
    }

    @Test
    public void setPath_singleSegment_twoCrumbs() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("docs");
        assertEquals(2, childFrame(view).getChildCount());
        assertTrue(crumbLabel(view, 1).isActivated());
    }

    @Test
    public void setPath_emptyPath_rootCrumbOnly_active() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("");
        assertEquals(2, childFrame(view).getChildCount());
        assertTrue(crumbLabel(view, 0).isActivated());
    }

    @Test
    public void setPath_twice_replacesCrumbs() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b/c");
        view.setPath("x");
        assertEquals(2, childFrame(view).getChildCount());
        assertEquals("x", crumbLabel(view, 1).getText().toString());
        assertTrue(crumbLabel(view, 1).isActivated());
    }

    @Test
    public void onClick_crumb_notifiesCallbackWithAbsolutePath() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b/c");
        List<String> paths = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        view.setCallback((absolutePath, index, count) -> {
            paths.add(absolutePath);
            indices.add(index);
            counts.add(count);
        });

        childFrame(view).getChildAt(2).performClick();

        assertEquals(1, paths.size());
        assertEquals("a/b", paths.get(0));
        assertEquals(2, (int) indices.get(0));
        assertEquals(4, (int) counts.get(0));
    }

    @Test
    public void onClick_rootCrumb_notifiesEmptyPath() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b");
        List<String> paths = new ArrayList<>();
        view.setCallback((absolutePath, index, count) -> paths.add(absolutePath));

        childFrame(view).getChildAt(0).performClick();

        assertEquals(1, paths.size());
        assertEquals("", paths.get(0));
    }

    @Test
    public void onClick_noCallback_doesNotThrow() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b");
        childFrame(view).getChildAt(1).performClick();
    }

    @Test
    public void setPath_thenLayout_doesNotThrow() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a/b/c");
        measureAndLayout(view);
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertEquals(0, childFrame(new PathBreadcrumbs(activity)).getChildCount());
        assertEquals(0, childFrame(new PathBreadcrumbs(activity, null)).getChildCount());
        assertEquals(0, childFrame(new PathBreadcrumbs(activity, null, 0)).getChildCount());
    }

    @Test
    public void setCallback_replacedCallback_receivesClicks() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        view.setPath("a");
        List<String> paths = new ArrayList<>();
        view.setCallback((p, i, c) -> paths.add("first"));
        view.setCallback((p, i, c) -> paths.add("second"));

        childFrame(view).getChildAt(1).performClick();

        assertEquals(1, paths.size());
        assertEquals("second", paths.get(0));
    }

    @Test
    public void setPath_trailingSlash_activeStaysAtRoot() throws Exception {
        FragmentActivity activity = activity();
        PathBreadcrumbs view = new PathBreadcrumbs(activity);
        view.setPath("a/b/");
        // "a/b/" is not among the crumbs ("", "a", "a/b"), so indexOf is -1
        // and mActive keeps its initial value (0).
        Field activeField = PathBreadcrumbs.class.getDeclaredField("mActive");
        activeField.setAccessible(true);
        assertEquals(0, activeField.getInt(view));
    }

    @Test
    public void onLayout_noCrumbs_doesNotThrow() {
        PathBreadcrumbs view = new PathBreadcrumbs(activity());
        measureAndLayout(view);
    }

    @Test
    public void onClick_outOfRangeTag_doesNotCallBack() {
        FragmentActivity activity = activity();
        PathBreadcrumbs view = new PathBreadcrumbs(activity);
        view.setPath("a/b");
        PathBreadcrumbs.SelectionCallback cb = mock(PathBreadcrumbs.SelectionCallback.class);
        view.setCallback(cb);
        View tooBig = new View(activity);
        tooBig.setTag(999);
        view.onClick(tooBig);
        View negative = new View(activity);
        negative.setTag(-1);
        view.onClick(negative);
        verifyNoInteractions(cb);
    }
}
