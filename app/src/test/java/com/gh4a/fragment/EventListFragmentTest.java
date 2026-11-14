package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.util.Pair;

import com.meisolsson.githubsdk.model.GitHubEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** EventListFragment.parseRepoIdentifierToOwnerAndName (F1, package-visible static). */
@RunWith(RobolectricTestRunner.class)
public class EventListFragmentTest {

    private static GitHubEvent.RepoIdentifier id(String repoWithUserName) {
        return GitHubEvent.RepoIdentifier.builder()
                .repoWithUserName(repoWithUserName).build();
    }

    @Test
    public void parse_nullIdentifier_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(null));
    }

    @Test
    public void parse_nullRepoName_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id(null)));
    }

    @Test
    public void parse_validIdentifier_returnsOwnerAndName() {
        Pair<String, String> pair =
                EventListFragment.parseRepoIdentifierToOwnerAndName(id("octocat/hello"));
        assertEquals("octocat", pair.first);
        assertEquals("hello", pair.second);
    }

    @Test
    public void parse_noSlash_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("octocat")));
    }

    @Test
    public void parse_emptyString_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("")));
    }

    @Test
    public void parse_threeSegments_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("a/b/c")));
    }

    @Test
    public void parse_trailingSlash_returnsNull() {
        // "o/" splits to ["o"] in Java (trailing empties dropped).
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("o/")));
    }

    @Test
    public void parse_leadingSlash_returnsNull() {
        // "/r" splits to ["", "r"]: owner is empty but length is 2, so it parses.
        Pair<String, String> pair =
                EventListFragment.parseRepoIdentifierToOwnerAndName(id("/r"));
        assertEquals("", pair.first);
        assertEquals("r", pair.second);
    }
}
