package com.gh4a.widget;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SearchViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void initialState_notExpanded() {
        SearchView view = new SearchView(activity());
        assertFalse(view.isExpanded());
    }

    @Test
    public void onActionViewExpanded_setsExpanded() {
        SearchView view = new SearchView(activity());
        view.onActionViewExpanded();
        assertTrue(view.isExpanded());
    }

    @Test
    public void onActionViewCollapsed_clearsExpanded() {
        SearchView view = new SearchView(activity());
        view.onActionViewExpanded();
        assertTrue(view.isExpanded());
        view.onActionViewCollapsed();
        assertFalse(view.isExpanded());
    }

    @Test
    public void onActionViewCollapsed_whenNotExpanded_staysCollapsed() {
        SearchView view = new SearchView(activity());
        view.onActionViewCollapsed();
        assertFalse(view.isExpanded());
    }

    @Test
    public void constructors_attrsVariant_initiallyCollapsed() {
        SearchView view = new SearchView(activity(), null);
        assertFalse(view.isExpanded());
    }

    @Test
    public void constructors_attrsAndStyleVariant_initiallyCollapsed() {
        SearchView view = new SearchView(activity(), null, 0);
        assertFalse(view.isExpanded());
    }

    @Test
    public void expandCollapseTwice_tracksState() {
        SearchView view = new SearchView(activity());
        view.onActionViewExpanded();
        view.onActionViewCollapsed();
        view.onActionViewExpanded();
        assertTrue(view.isExpanded());
        view.onActionViewCollapsed();
        assertFalse(view.isExpanded());
    }
}
