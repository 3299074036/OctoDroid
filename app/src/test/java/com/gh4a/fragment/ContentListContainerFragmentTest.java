package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** ContentListContainerFragment (F1): newInstance + .gitmodules parsing.
 * Full attach would subscribe the content-list network load, so view
 * behavior is intentionally left to manual verification. */
@RunWith(RobolectricTestRunner.class)
public class ContentListContainerFragmentTest {

    private static Repository repo() {
        return Repository.builder()
                .owner(User.builder().login("octocat").build())
                .name("hello").defaultBranch("main").build();
    }

    @Test
    public void newInstance_storesAllArgs() {
        ContentListContainerFragment f = ContentListContainerFragment.newInstance(
                repo(), "dev", "docs/guide");
        assertNotNull(f.getArguments().getParcelable("repository"));
        assertEquals("dev", f.getArguments().getString("ref"));
        assertEquals("docs/guide", f.getArguments().getString("initialpath"));
    }

    @Test
    public void handleBackPress_emptyStack_returnsFalse() {
        // Bare instance: dir stack is empty, so there is nothing to pop.
        assertFalse(new ContentListContainerFragment().handleBackPress());
    }

    @Test
    public void onAttach_withoutCallback_throws() {
        ContentListContainerFragment f =
                ContentListContainerFragment.newInstance(repo(), "main", null);
        FragmentTestHelper.assertThrows(ClassCastException.class,
                () -> FragmentTestHelper.attach(f));
    }

    // ---- parseModuleMap (private) ----

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, String>> parse(String text) {
        try {
            Method m = ContentListContainerFragment.class
                    .getDeclaredMethod("parseModuleMap", Optional.class);
            m.setAccessible(true);
            Optional<byte[]> input = text == null ? Optional.empty()
                    : Optional.of(text.getBytes(StandardCharsets.UTF_8));
            return (Optional<Map<String, String>>) m.invoke(
                    new ContentListContainerFragment(), input);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void parseModuleMap_emptyInput_returnsEmpty() {
        assertFalse(parse(null).isPresent());
        assertFalse(parse("").isPresent());
        assertFalse(parse("   \n  ").isPresent());
    }

    @Test
    public void parseModuleMap_httpsUrl_parsesOwnerAndRepo() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("octocat/hello", result.get().get("lib/foo"));
    }

    @Test
    public void parseModuleMap_sshUrl_parsesOwnerAndRepo() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = git@github.com:octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals("octocat/hello", result.get().get("lib/foo"));
    }

    @Test
    public void parseModuleMap_nonGithubUrl_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://gitlab.com/octocat/hello.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_urlWithoutTwoSegments_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n"
                + "\turl = https://github.com/octocat\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_pathWithoutUrl_isSkipped() {
        String text = "[submodule \"lib/foo\"]\n"
                + "\tpath = lib/foo\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void parseModuleMap_multipleSubmodules_allParsed() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "\turl = https://github.com/o1/r1.git\n"
                + "[submodule \"b\"]\n"
                + "\tpath = b\n"
                + "\turl = git@github.com:o2/r2.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals("o1/r1", result.get().get("a"));
        assertEquals("o2/r2", result.get().get("b"));
    }

    @Test
    public void parseModuleMap_repoSuffixStrippedOnlyAtLastDot() {
        String text = "[submodule \"a\"]\n"
                + "\tpath = a\n"
                + "\turl = https://github.com/o/r.name.git\n";
        Optional<Map<String, String>> result = parse(text);
        assertEquals("o/r.name", result.get().get("a"));
    }
}
