package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueState;
import com.gh4a.utils.IntentUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** IssueFragmentBase (F1): static args builder + interesting-events set. */
@RunWith(RobolectricTestRunner.class)
public class IssueFragmentBaseTest {

    @Test
    public void buildArgs_storesAllFields() {
        Issue issue = Issue.builder().number(7).state(IssueState.Open).build();
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(123L);
        android.os.Bundle args = IssueFragmentBase.buildArgs(
                "octocat", "hello", issue, true, marker);
        assertEquals("octocat", args.getString("owner"));
        assertEquals("hello", args.getString("repo"));
        assertNotNull(args.getParcelable("issue"));
        assertEquals(true, args.getBoolean("collaborator"));
        assertNotNull(args.getParcelable("initial_comment"));
    }

    @Test
    public void buildArgs_nullIssueAndMarker_storesNulls() {
        android.os.Bundle args = IssueFragmentBase.buildArgs(
                "o", "r", null, false, null);
        assertEquals("o", args.getString("owner"));
        assertNull(args.getParcelable("issue"));
        assertEquals(false, args.getBoolean("collaborator"));
        assertNull(args.getParcelable("initial_comment"));
    }

    @Test
    public void interestingEvents_hasExpectedMembers() {
        assertEquals(31, IssueFragmentBase.INTERESTING_EVENTS.size());
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Closed));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Reopened));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Merged));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Commented));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Labeled));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Renamed));
        assertTrue(IssueFragmentBase.INTERESTING_EVENTS.contains(IssueEventType.Transferred));
    }

    @Test
    public void interestingEvents_excludesUninterestingTypes() {
        // Subscribed / Mentioned / Pinned etc. are not timeline items.
        assertEquals(false, IssueFragmentBase.INTERESTING_EVENTS
                .contains(IssueEventType.Subscribed));
        assertEquals(false, IssueFragmentBase.INTERESTING_EVENTS
                .contains(IssueEventType.Mentioned));
    }
}
