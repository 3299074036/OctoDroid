package com.gh4a.widget;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.IssueState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IssueStateTrackingFloatingActionButtonTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private static boolean hasState(int[] states, int state) {
        for (int s : states) {
            if (s == state) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void defaultState_noExtraState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        int[] states = fab.getDrawableState();
        assertFalse(hasState(states, R.attr.state_closed));
        assertFalse(hasState(states, R.attr.state_merged));
        assertFalse(hasState(states, R.attr.state_draft));
    }

    @Test
    public void setState_closed_addsClosedState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setState(IssueState.Closed);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_closed));
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_merged));
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_draft));
    }

    @Test
    public void setState_open_removesClosedState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setState(IssueState.Closed);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_closed));
        fab.setState(IssueState.Open);
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_closed));
    }

    @Test
    public void setMerged_addsMergedStateAndBeatsClosed() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setState(IssueState.Closed);
        fab.setMerged(true);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_merged));
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_closed));
    }

    @Test
    public void setMerged_false_removesMergedState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setMerged(true);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_merged));
        fab.setMerged(false);
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_merged));
    }

    @Test
    public void setDraft_addsDraftState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setState(IssueState.Open);
        fab.setDraft(true);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_draft));
    }

    @Test
    public void setDraft_closedStateWinsOverDraft() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setState(IssueState.Closed);
        fab.setDraft(true);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_closed));
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_draft));
    }

    @Test
    public void setDraft_false_removesDraftState() {
        IssueStateTrackingFloatingActionButton fab =
                new IssueStateTrackingFloatingActionButton(activity());
        fab.setDraft(true);
        assertTrue(hasState(fab.getDrawableState(), R.attr.state_draft));
        fab.setDraft(false);
        assertFalse(hasState(fab.getDrawableState(), R.attr.state_draft));
    }

    @Test
    public void constructors_attrsVariants_create() {
        FragmentActivity activity = activity();
        IssueStateTrackingFloatingActionButton a =
                new IssueStateTrackingFloatingActionButton(activity, null);
        IssueStateTrackingFloatingActionButton b =
                new IssueStateTrackingFloatingActionButton(activity, null, 0);
        assertFalse(hasState(a.getDrawableState(), R.attr.state_closed));
        assertFalse(hasState(b.getDrawableState(), R.attr.state_closed));
    }
}
