package com.gh4a.widget;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.git.GitReference;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PullRequestBranchInfoViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private Repository repo(String ownerLogin, String name) {
        return Repository.builder()
                .owner(User.builder().login(ownerLogin).build())
                .name(name)
                .build();
    }

    private PullRequestMarker marker(String label, String ref, Repository repo) {
        PullRequestMarker marker = mock(PullRequestMarker.class);
        when(marker.label()).thenReturn(label);
        when(marker.ref()).thenReturn(ref);
        when(marker.repo()).thenReturn(repo);
        return marker;
    }

    private TextView sourceView(PullRequestBranchInfoView view) {
        return view.findViewById(R.id.tv_pr_from);
    }

    private TextView targetView(PullRequestBranchInfoView view) {
        return view.findViewById(R.id.tv_pr_to);
    }

    @Test
    public void bind_bothMarkers_setsBranchTexts() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        Repository repo = repo("octocat", "Hello-World");
        view.bind(marker("octocat:feature", "feature", repo),
                marker("octocat:main", "main", repo), null);

        assertEquals("From octocat:feature", sourceView(view).getText().toString());
        assertEquals("Into octocat:main", targetView(view).getText().toString());
    }

    @Test
    public void bind_emptyLabel_usesRef() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("", "feature-branch", repo("octocat", "Hello-World")),
                marker("", "main", repo("octocat", "Hello-World")), null);

        assertEquals("From feature-branch", sourceView(view).getText().toString());
        assertEquals("Into main", targetView(view).getText().toString());
    }

    @Test
    public void bind_nullLabelAndRef_leavesEmptyLabel() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker(null, null, repo("octocat", "Hello-World")),
                marker(null, null, repo("octocat", "Hello-World")), null);

        assertEquals("From ", sourceView(view).getText().toString());
        assertEquals("Into ", targetView(view).getText().toString());
    }

    @Test
    public void bind_nullMarkers_leavesEmptyLabels() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(null, null, null);

        assertEquals("From ", sourceView(view).getText().toString());
        assertEquals("Into ", targetView(view).getText().toString());
    }

    @Test
    public void bind_markerWithRepo_makesViewClickable() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        // The source side is only made clickable when a source reference is
        // present (bind's third argument); with null it stays non-clickable.
        view.bind(marker("octocat:feature", "feature", repo("octocat", "Hello-World")),
                marker("octocat:main", "main", repo("octocat", "Hello-World")),
                mock(GitReference.class));

        assertTrue(sourceView(view).isClickable());
        assertTrue(targetView(view).isClickable());
    }

    @Test
    public void bind_markerWithoutRepo_notClickable() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("octocat:feature", "feature", null),
                marker("octocat:main", "main", null), null);

        assertFalse(sourceView(view).isClickable());
        assertFalse(targetView(view).isClickable());
    }

    @Test
    public void bind_nullMarker_notClickable() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(null, null, null);

        assertFalse(sourceView(view).isClickable());
        assertFalse(targetView(view).isClickable());
    }

    @Test
    public void bind_sourceReferenceNull_doesNotAffectTargetClickability() {
        // bind(source, target, sourceReference): sourceReference only gates the
        // source's makeClickable flag; target is always clickable when it has a repo.
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("octocat:feature", "feature", repo("octocat", "Hello-World")),
                marker("octocat:main", "main", repo("octocat", "Hello-World")), null);

        assertTrue(targetView(view).isClickable());
    }

    @Test
    public void onClick_sourceBranch_startsRepositoryActivity() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("octocat:feature", "feature", repo("octocat", "Hello-World")),
                marker("octocat:main", "main", repo("octocat", "Hello-World")), null);

        sourceView(view).performClick();

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals("octocat", started.getStringExtra("owner"));
        assertEquals("Hello-World", started.getStringExtra("repo"));
    }

    @Test
    public void onClick_targetBranch_startsRepositoryActivity() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("octocat:feature", "feature", repo("octocat", "Hello-World")),
                marker("octocat:main", "main", repo("other", "Other-Repo")), null);

        targetView(view).performClick();

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals("other", started.getStringExtra("owner"));
        assertEquals("Other-Repo", started.getStringExtra("repo"));
    }

    @Test
    public void onClick_nullMarker_doesNotStartActivity() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(null, null, null);

        sourceView(view).performClick();
        targetView(view).performClick();

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_markerWithoutRepo_doesNotStartActivity() {
        FragmentActivity activity = activity();
        PullRequestBranchInfoView view = new PullRequestBranchInfoView(activity);
        view.bind(marker("octocat:feature", "feature", null),
                marker("octocat:main", "main", null), null);

        sourceView(view).performClick();

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertNotNull(new PullRequestBranchInfoView(activity, null));
        assertNotNull(new PullRequestBranchInfoView(activity, null, 0));
    }
}
