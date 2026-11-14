package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.SingleFactory;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.request.issue.IssueRequest;
import com.meisolsson.githubsdk.service.issues.IssueLabelService;
import com.meisolsson.githubsdk.service.issues.IssueMilestoneService;
import com.meisolsson.githubsdk.service.issues.IssueService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCollaboratorService;

import android.app.Dialog;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;

import io.reactivex.Single;
import retrofit2.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import com.gh4a.R;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.PullRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.AppBarLayout;

import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.dismissCurrentSnackbarAsTimeout;
import static com.gh4a.activities.ActivityTestHelper.logContains;
import static com.gh4a.activities.ActivityTestHelper.waitForSnackbarWithText;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gh4a.ApiRequestException;
import com.meisolsson.githubsdk.model.Content;
import com.meisolsson.githubsdk.model.ContentType;
import com.meisolsson.githubsdk.service.repositories.RepositoryContentService;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
public class IssueEditActivityTest {
    private void authorize() {
        // Retry loop: a single addAccount is intermittently ineffective
        // across tests in the same class run (secure-prefs backend jitter).
        Gh4Application app = Gh4Application.get();
        for (int i = 0; i < 3
                && (!app.isAuthorized() || !"tester".equals(app.getAuthLogin())); i++) {
            app.addAccount(User.builder().login("tester").id(1L).build(), "tok");
            idleMain(100);
        }
    }

    private Issue issue(int number, boolean isPr) {
        Issue.Builder b = Issue.builder().number(number).title("T").body("B");
        if (isPr) {
            b.pullRequest(PullRequest.builder().build());
        }
        return b.build();
    }

    private Class<?> templateClass() throws Exception {
        for (Class<?> c : IssueEditActivity.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("IssueTemplate")) {
                return c;
            }
        }
        throw new AssertionError("IssueTemplate not found");
    }

    private Object newTemplate(String content) throws Exception {
        Constructor<?> ctor = templateClass().getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(content);
    }

    @Test
    public void makeCreateIntent_hasNoTitleBodyOrIssue() {
        Intent i = IssueEditActivity.makeCreateIntent(appContext(), "o", "r");
        assertEquals(IssueEditActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertFalse(i.hasExtra("title"));
        assertFalse(i.hasExtra("body"));
        assertFalse(i.hasExtra("issue"));
    }

    @Test
    public void makeCreateIntent_withTitleBody_setsExtras() {
        Intent i = IssueEditActivity.makeCreateIntent(appContext(), "o", "r", "T", "B");
        assertEquals("T", i.getStringExtra("title"));
        assertEquals("B", i.getStringExtra("body"));
    }

    @Test
    public void makeEditIntent_setsIssueExtra() {
        Issue issue = issue(5, false);
        Intent i = IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue);
        assertEquals(issue, i.getParcelableExtra("issue"));
    }

    @Test
    public void launch_createMode_titleIsNewIssue() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        assertEquals(appContext().getString(R.string.issue_create),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
        assertFalse(activity.canSwipeToRefresh());
    }

    @Test
    public void launch_createWithTitleBody_prefillsFields() throws Exception {
        authorize();
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r", "My title", "My body"));
        if (activity.isFinishing()) {
            // Authorization was lost across the launch, so onCreate returned
            // early and the header (with the title field) was never built.
            authorize();
            activity = launch(IssueEditActivity.class,
                    IssueEditActivity.makeCreateIntent(appContext(), "o", "r", "My title", "My body"));
        }
        // The title field lives in the header view, not the activity content.
        IssueEditActivity launched = activity;
        waitFor(() -> get(launched, "mTitleView") != null, 10000);
        assertEquals("My title",
                ((android.widget.EditText) get(launched, "mTitleView")).getText().toString());
    }

    @Test
    public void launch_editIssue_titleIsEditIssue() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
        assertEquals(appContext().getString(R.string.issue_edit_title, 12),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void launch_editPullRequest_titleIsEditPr() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(9, true)));
        assertEquals(appContext().getString(R.string.pull_request_edit_title, 9),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void navigateUp_create_goesToIssueList() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        Intent up = activity.navigateUp();
        assertEquals(IssueListActivity.class.getName(), up.getComponent().getClassName());
    }

    @Test
    public void navigateUp_editIssue_goesToIssue() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
        Intent up = activity.navigateUp();
        assertEquals(IssueActivity.class.getName(), up.getComponent().getClassName());
        assertEquals(12, up.getIntExtra("number", -1));
    }

    @Test
    public void navigateUp_editPr_goesToPullRequest() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(9, true)));
        Intent up = activity.navigateUp();
        assertEquals(PullRequestActivity.class.getName(), up.getComponent().getClassName());
        assertEquals(9, up.getIntExtra("number", -1));
    }

    @Test
    public void onSaveInstanceState_storesIssueParcels() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals(12, ((Issue) out.getParcelable("issue")).number().intValue());
        assertTrue(out.containsKey("original_issue"));
    }

    @Test
    public void onRefresh_resetsCachedSingles() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        activity.onRefresh();
        assertNull(get(activity, "mAssigneeSingle"));
        assertNull(get(activity, "mLabelSingle"));
        assertNull(get(activity, "mMilestoneSingle"));
        assertFalse((boolean) get(activity, "mIsCollaborator"));
    }

    @Test
    public void onFocusChange_titleFocus_togglesMarkdownButtons() {
        authorize();
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        android.view.View title = (android.view.View) get(activity, "mTitleView");
        android.view.View buttons = (android.view.View) get(activity, "mMarkdownButtons");
        activity.onFocusChange(title, true);
        assertEquals(android.view.View.GONE, buttons.getVisibility());
        activity.onFocusChange(title, false);
        assertEquals(android.view.View.VISIBLE, buttons.getVisibility());
    }

    @Test
    public void editPagerAdapter_countDependsOnCollaborator() throws Exception {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        Object adapter = call(activity, "createAdapter",
                new Class<?>[]{android.view.ViewGroup.class},
                new Object[]{new android.widget.FrameLayout(appContext())});
        Method getCount = adapter.getClass().getMethod("getCount");
        getCount.setAccessible(true);
        assertEquals(2, getCount.invoke(adapter));
        com.gh4a.activities.ActivityTestHelper.set(activity, "mIsCollaborator", true);
        assertEquals(3, getCount.invoke(adapter));
        Method getPageTitle = adapter.getClass().getMethod("getPageTitle", int.class);
        getPageTitle.setAccessible(true);
        assertEquals(appContext().getString(R.string.issue_body), getPageTitle.invoke(adapter, 0).toString());
        assertEquals(appContext().getString(R.string.preview), getPageTitle.invoke(adapter, 1).toString());
        assertEquals(appContext().getString(R.string.settings), getPageTitle.invoke(adapter, 2).toString());
    }

    @Test
    public void template_parsesFrontMatter() throws Exception {
        Object t = newTemplate("---\nname: Bug report\nabout: Something broke\n"
                + "title: \"[Bug] \"\nlabels: bug, help wanted\nassignees: alice, bob\n---\nBody here");
        assertEquals("Bug report", get(t, "name"));
        assertEquals("Something broke", get(t, "description"));
        assertEquals("[Bug] ", get(t, "title"));
        assertEquals("Body here", get(t, "content"));
        assertEquals(java.util.Arrays.asList("bug", "help wanted"), get(t, "defaultLabels"));
        assertEquals(java.util.Arrays.asList("alice", "bob"), get(t, "defaultAssignees"));
    }

    @Test
    public void template_noFrontMatter_wholeContentKept() throws Exception {
        Object t = newTemplate("plain body, no front matter");
        assertEquals("plain body, no front matter", get(t, "content"));
        assertNull(get(t, "name"));
        assertTrue(((List<?>) get(t, "defaultLabels")).isEmpty());
    }

    @Test
    public void template_nullContent_staysNull() throws Exception {
        Object t = newTemplate(null);
        assertNull(get(t, "content"));
    }

    @Test
    public void template_emptyValueLine_skipped() throws Exception {
        Object t = newTemplate("---\nname: \nabout: x\n---\nBody");
        assertNull(get(t, "name"));
        assertEquals("x", get(t, "description"));
    }

    @Test
    public void template_unclosedQuote_stripsOnlyOpeningQuote() throws Exception {
        Object t = newTemplate("---\nname: \"abc\n---\nBody");
        assertEquals("abc", get(t, "name"));
    }

    @Test
    public void template_oversizeContent_skipsFrontMatterParse() throws Exception {
        StringBuilder sb = new StringBuilder("---\nname: Big\n---\n");
        while (sb.length() <= 256 * 1024 + 10) {
            sb.append("0123456789");
        }
        Object t = newTemplate(sb.toString());
        assertNull(get(t, "name"));
        assertTrue(((String) get(t, "content")).startsWith("---\nname: Big"));
    }

    @Test
    public void template_parcelRoundTrip_preservesFields() throws Exception {
        Object t = newTemplate("---\nname: Bug\nlabels: a, b\n---\nBody text");
        Parcel parcel = Parcel.obtain();
        try {
            ((android.os.Parcelable) t).writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            android.os.Parcelable.Creator<?> creator =
                    (android.os.Parcelable.Creator<?>) templateClass().getField("CREATOR").get(null);
            Object back = creator.createFromParcel(parcel);
            assertEquals("Bug", get(back, "name"));
            assertEquals("Body text", get(back, "content"));
            assertEquals(java.util.Arrays.asList("a", "b"), get(back, "defaultLabels"));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void splitAndFillList_trimsAndSkipsEmpties() throws Exception {
        List<String> list = new ArrayList<>();
        Method m = null;
        for (Class<?> c : IssueEditActivity.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("IssueTemplate")) {
                m = c.getDeclaredMethod("splitAndFillList", String.class, List.class);
            }
        }
        m.setAccessible(true);
        m.invoke(null, "a, b,,  c ,", list);
        assertEquals(java.util.Arrays.asList("a", "b", "c"), list);
    }

    // ---------- lifecycle / onCreate ----------

    @Test
    public void launch_unauthorized_redirectsToLoginAndFinishes() {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        assertTrue(activity.isFinishing());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Github4AndroidActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void launch_withSavedInstanceState_restoresIssue() throws Exception {
        authorize();
        Bundle saved = new Bundle();
        Issue restored = Issue.builder().number(99).title("Restored").body("RB").build();
        saved.putParcelable("issue", restored);
        saved.putParcelable("original_issue",
                Issue.builder().title("T").body("B").build());
        ActivityController<IssueEditActivity> c = controller(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
        IssueEditActivity activity = c.create(saved).start().resume().get();
        // The restored issue wins over the intent's issue (onInitExtras only
        // reads the intent when mEditIssue is null).
        assertEquals(99, ((Issue) get(activity, "mEditIssue")).number().intValue());
        assertEquals("Restored",
                ((android.widget.EditText) get(activity, "mTitleView")).getText().toString());
        assertEquals(appContext().getString(R.string.issue_edit_title, 99),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void launch_editAuthorized_skipsTemplateLoad() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        // Template loading (create mode only) would disable the fields first.
        assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
        assertEquals("T",
                ((android.widget.EditText) get(activity, "mTitleView")).getText().toString());
    }

    @Test
    public void launch_createTitleOnly_contentNotGivenViaIntent() {
        authorize();
        // Title without body: isContentGivenViaIntent() is false via the
        // second && arm, so template loading runs (fields disabled first).
        Intent intent = new Intent(appContext(), IssueEditActivity.class)
                .putExtra("owner", "o")
                .putExtra("repo", "r")
                .putExtra("title", "Only title");
        IssueEditActivity activity = launch(IssueEditActivity.class, intent);
        assertEquals("Only title",
                ((android.widget.EditText) get(activity, "mTitleView")).getText().toString());
    }

    @Test
    public void launch_landscape_shadowsHidden() {
        authorize();
        String originalQualifiers = RuntimeEnvironment.getQualifiers();
        RuntimeEnvironment.setQualifiers("+land");
        try {
            IssueEditActivity activity = launch(IssueEditActivity.class,
                    IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
            View topLeftShadow = activity.findViewById(R.id.markdown_buttons_top_left_shadow);
            View topShadow = activity.findViewById(R.id.markdown_buttons_top_shadow);
            assertNotNull(topLeftShadow);
            assertNotNull(topShadow);
            assertEquals(View.GONE, topLeftShadow.getVisibility());
            assertEquals(View.GONE, topShadow.getVisibility());
        } finally {
            RuntimeEnvironment.setQualifiers(originalQualifiers);
        }
    }

    @Test
    public void onOffsetChanged_setsBottomPadding() throws Exception {
        authorize();
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue(12, false)));
        ViewGroup root = (ViewGroup) get(activity, "mRootView");
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        AppBarLayout appBar = mock(AppBarLayout.class);
        when(appBar.getTotalScrollRange()).thenReturn(100);
        activity.onOffsetChanged(appBar, -30);
        assertEquals(left, root.getPaddingLeft());
        assertEquals(top, root.getPaddingTop());
        assertEquals(right, root.getPaddingRight());
        assertEquals(70, root.getPaddingBottom());
    }

    @Test
    public void onFocusChange_nonTitleView_doesNothing() {
        authorize();
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        android.view.View buttons = (android.view.View) get(activity, "mMarkdownButtons");
        int before = buttons.getVisibility();
        android.view.View desc = (android.view.View) get(activity, "mDescView");
        activity.onFocusChange(desc, true);
        activity.onFocusChange(desc, false);
        assertEquals(before, buttons.getVisibility());
    }

    // ---------- IssueTemplate branch arms ----------

    @Test
    public void template_lineWithoutColon_skipped() throws Exception {
        Object t = newTemplate("---\njust a comment line\nname: Bug\n---\nBody");
        assertEquals("Bug", get(t, "name"));
        assertEquals("Body", get(t, "content"));
    }

    @Test
    public void template_singleQuotedValue_stripsQuotes() throws Exception {
        Object t = newTemplate("---\nname: 'Bug report'\n---\nBody");
        assertEquals("Bug report", get(t, "name"));
    }

    @Test
    public void template_loneQuoteValue_isEmpty() throws Exception {
        // Value is a single quote char: length == colonPos + 3, so the
        // closing-quote check's first arm is false and only the opening
        // quote is stripped (leaving an empty value).
        Object t = newTemplate("---\nname: \"\n---\nBody");
        assertEquals("", get(t, "name"));
    }

    @Test
    public void template_unknownKey_ignored() throws Exception {
        Object t = newTemplate("---\nfoo: bar\nname: Bug\n---\nBody");
        assertEquals("Bug", get(t, "name"));
        assertEquals("Body", get(t, "content"));
    }

    @Test
    public void template_describeContents_isZeroAndNewArray() throws Exception {
        Object t = newTemplate("---\nname: Bug\n---\nBody");
        assertEquals(0, ((android.os.Parcelable) t).describeContents());
        android.os.Parcelable.Creator<?> creator =
                (android.os.Parcelable.Creator<?>) templateClass().getField("CREATOR").get(null);
        Object[] arr = (Object[]) creator.newArray(3);
        assertEquals(3, arr.length);
    }


    // ---------- shared mocks ----------

    /** Stubs the services the activity loads from. Caller owns the mocks. */
    private static class Mocks implements AutoCloseable {
        final MockedStatic<ServiceFactory> svc;
        final MockedStatic<SingleFactory> single;
        final IssueMilestoneService milestoneService = mock(IssueMilestoneService.class);
        final IssueLabelService labelService = mock(IssueLabelService.class);
        final RepositoryCollaboratorService collaboratorService =
                mock(RepositoryCollaboratorService.class);
        final IssueService issueService = mock(IssueService.class);
        final RepositoryContentService contentService =
                mock(RepositoryContentService.class);

        Mocks(boolean collaborator) {
            svc = Mockito.mockStatic(ServiceFactory.class,
                    Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
            single = Mockito.mockStatic(SingleFactory.class,
                    Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
            try {
                svc.when(() -> ServiceFactory.getForFullPagedLists(
                        IssueMilestoneService.class, false)).thenReturn(milestoneService);
                svc.when(() -> ServiceFactory.getForFullPagedLists(
                        IssueLabelService.class, false)).thenReturn(labelService);
                svc.when(() -> ServiceFactory.getForFullPagedLists(
                        RepositoryCollaboratorService.class, false))
                        .thenReturn(collaboratorService);
                svc.when(() -> ServiceFactory.get(IssueService.class, false))
                        .thenReturn(issueService);
                svc.when(() -> ServiceFactory.get(RepositoryContentService.class, false))
                        .thenReturn(contentService);
                single.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", false))
                        .thenReturn(Single.just(collaborator));
                single.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", true))
                        .thenReturn(Single.just(collaborator));
            } catch (RuntimeException e) {
                svc.close();
                single.close();
                throw e;
            }
        }

        @Override
        public void close() {
            svc.close();
            single.close();
        }
    }

    private Content content(String name, String path, ContentType type) {
        Content c = mock(Content.class);
        when(c.name()).thenReturn(name);
        when(c.path()).thenReturn(path);
        when(c.type()).thenReturn(type);
        return c;
    }

    private ApiRequestException apiError(int status) {
        return new ApiRequestException(Response.error(status,
                ResponseBody.create(MediaType.parse("application/json"), "{}")));
    }

    private Milestone milestone(int number, String title) {
        return Milestone.builder().number(number).title(title).build();
    }

    private Label label(String name, String color) {
        return Label.builder().name(name).color(color).build();
    }

    private <T> Page<T> singlePage(java.util.List<T> items) {
        return Page.<T>builder().items(items).build();
    }

    private IssueEditActivity launchEdit(Issue issue) {
        return launch(IssueEditActivity.class,
                IssueEditActivity.makeEditIntent(appContext(), "o", "r", issue));
    }

    /** Launch in edit mode, relaunching if authorization was lost across the
     * launch (onCreate returns early then, leaving the header fields null). */
    private IssueEditActivity launchEditChecked(Issue issue) throws Exception {
        IssueEditActivity activity = launchEdit(issue);
        for (int attempt = 0; attempt < 3
                && (activity.isFinishing() || get(activity, "mTitleView") == null); attempt++) {
            authorize();
            activity = launchEdit(issue);
        }
        IssueEditActivity launched = activity;
        waitFor(() -> get(launched, "mTitleView") != null, 10000);
        return activity;
    }

    private IssueEditActivity launchCreateChecked(String title, String body) throws Exception {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r", title, body));
        for (int attempt = 0; attempt < 3
                && (activity.isFinishing() || get(activity, "mTitleView") == null); attempt++) {
            authorize();
            activity = launch(IssueEditActivity.class,
                    IssueEditActivity.makeCreateIntent(appContext(), "o", "r", title, body));
        }
        IssueEditActivity launched = activity;
        waitFor(() -> get(launched, "mTitleView") != null, 10000);
        return activity;
    }

    /** Create-mode launch with no title/body extras at all, so the activity
     * loads issue templates. The 4-arg makeCreateIntent puts null extras,
     * which still count for hasExtra() and would skip template loading. */
    private IssueEditActivity launchCreateBlank() throws Exception {
        IssueEditActivity activity = launch(IssueEditActivity.class,
                IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        for (int attempt = 0; attempt < 3
                && (activity.isFinishing() || get(activity, "mTitleView") == null); attempt++) {
            authorize();
            activity = launch(IssueEditActivity.class,
                    IssueEditActivity.makeCreateIntent(appContext(), "o", "r"));
        }
        IssueEditActivity launched = activity;
        waitFor(() -> get(launched, "mTitleView") != null, 10000);
        return activity;
    }

    private void waitForFragment(IssueEditActivity activity, String tag) throws Exception {
        waitFor(() -> activity.getSupportFragmentManager().findFragmentByTag(tag) != null, 10000);
    }

    // ---------- onClick: milestone dialog ----------

    @Test
    public void onClick_milestoneContainer_showsMilestoneDialog() throws Exception {
        authorize();
        java.util.List<Milestone> milestones = Arrays.asList(
                milestone(1, "v1.0"), milestone(2, "v2.0"));
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(milestones))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            IssueEditActivity.MilestoneEditDialogFragment f =
                    (IssueEditActivity.MilestoneEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("milestoneedit");
            assertNotNull(f);
            AlertDialog dialog = (AlertDialog) f.getDialog();
            assertNotNull(dialog);
            ListView list = dialog.getListView();
            assertEquals(3, list.getAdapter().getCount());
            assertEquals(appContext().getString(R.string.issue_clear_milestone),
                    list.getAdapter().getItem(0).toString());
            assertEquals("v1.0", list.getAdapter().getItem(1).toString());
            assertEquals("v2.0", list.getAdapter().getItem(2).toString());
        }
    }

    @Test
    public void milestoneDialog_selectMilestone_updatesIssue() throws Exception {
        authorize();
        java.util.List<Milestone> milestones = Arrays.asList(
                milestone(1, "v1.0"), milestone(2, "v2.0"));
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(milestones))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            IssueEditActivity.MilestoneEditDialogFragment f =
                    (IssueEditActivity.MilestoneEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("milestoneedit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            ListView list = dialog.getListView();
            list.performItemClick(list.getAdapter().getView(2, null, list), 2,
                    list.getAdapter().getItemId(2));
            idleMain(200);
            assertEquals(Integer.valueOf(2),
                    ((Issue) get(activity, "mEditIssue")).milestone().number());
            assertEquals("v2.0",
                    ((Issue) get(activity, "mEditIssue")).milestone().title());
        }
    }

    @Test
    public void milestoneDialog_selectClear_clearsMilestone() throws Exception {
        authorize();
        Issue withMilestone = Issue.builder().number(12).title("T").body("B")
                .milestone(milestone(1, "v1.0")).build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            singlePage(Arrays.asList(milestone(1, "v1.0"))))));
            IssueEditActivity activity = launchEditChecked(withMilestone);
            assertEquals("v1.0",
                    ((android.widget.TextView) get(activity, "mSelectedMilestoneView"))
                            .getText().toString());
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            IssueEditActivity.MilestoneEditDialogFragment f =
                    (IssueEditActivity.MilestoneEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("milestoneedit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            ListView list = dialog.getListView();
            // The issue's current milestone (v1.0) is pre-selected at position 1.
            assertEquals(1, list.getCheckedItemPosition());
            list.performItemClick(list.getAdapter().getView(0, null, list), 0,
                    list.getAdapter().getItemId(0));
            idleMain(200);
            assertNull(((Issue) get(activity, "mEditIssue")).milestone());
            assertEquals(appContext().getString(R.string.issue_clear_milestone),
                    ((android.widget.TextView) get(activity, "mSelectedMilestoneView"))
                            .getText().toString());
        }
    }

    @Test
    public void milestoneDialog_manageButton_launchesListAndResetsSingle() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            singlePage(Arrays.asList(milestone(1, "v1.0"))))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            IssueEditActivity.MilestoneEditDialogFragment f =
                    (IssueEditActivity.MilestoneEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("milestoneedit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            assertNotNull(get(activity, "mMilestoneSingle"));
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick();
            idleMain(200);
            ShadowActivity.IntentForResult result =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(result);
            assertEquals(IssueMilestoneListActivity.class.getName(),
                    result.intent.getComponent().getClassName());
            // The manager launcher's success callback clears the cached Single.
            activity.getActivityResultRegistry().dispatchResult(
                    result.requestCode, android.app.Activity.RESULT_OK, null);
            assertNull(get(activity, "mMilestoneSingle"));
        }
    }

    // ---------- onClick: assignee dialog ----------

    @Test
    public void onClick_assigneeContainer_showsAssigneeDialog() throws Exception {
        authorize();
        java.util.List<User> users = Arrays.asList(
                User.builder().login("alice").id(1L).build(),
                User.builder().login("bob").id(2L).build());
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(users))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            IssueEditActivity.AssigneeEditDialogFragment f =
                    (IssueEditActivity.AssigneeEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("assigneeedit");
            assertNotNull(f);
            AlertDialog dialog = (AlertDialog) f.getDialog();
            assertNotNull(dialog);
            ListView list = dialog.getListView();
            assertEquals(2, list.getAdapter().getCount());
            assertEquals("alice", list.getAdapter().getItem(0).toString());
            assertEquals("bob", list.getAdapter().getItem(1).toString());
        }
    }

    @Test
    public void assigneeDialog_toggleAndOk_updatesAssignees() throws Exception {
        authorize();
        java.util.List<User> users = Arrays.asList(
                User.builder().login("alice").id(1L).build(),
                User.builder().login("bob").id(2L).build());
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(users))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            IssueEditActivity.AssigneeEditDialogFragment f =
                    (IssueEditActivity.AssigneeEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("assigneeedit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            ListView list = dialog.getListView();
            list.performItemClick(list.getAdapter().getView(1, null, list), 1,
                    list.getAdapter().getItemId(1));
            idleMain(100);
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idleMain(200);
            java.util.List<User> assignees = ((Issue) get(activity, "mEditIssue")).assignees();
            assertEquals(1, assignees.size());
            assertEquals("bob", assignees.get(0).login());
        }
    }

    @Test
    public void loadPotentialAssignees_addsCreatorIfMissing() throws Exception {
        authorize();
        // The edited issue's creator (alice) is not in the collaborator list.
        Issue createdByAlice = Issue.builder().number(12).title("T").body("B")
                .user(User.builder().login("alice").id(1L).build()).build();
        java.util.List<User> users = Arrays.asList(
                User.builder().login("bob").id(2L).build());
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(users))));
            IssueEditActivity activity = launchEditChecked(createdByAlice);
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            IssueEditActivity.AssigneeEditDialogFragment f =
                    (IssueEditActivity.AssigneeEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("assigneeedit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            ListView list = dialog.getListView();
            assertEquals(2, list.getAdapter().getCount());
            assertEquals("bob", list.getAdapter().getItem(0).toString());
            assertEquals("alice", list.getAdapter().getItem(1).toString());
        }
    }

    // ---------- onClick: label dialog ----------

    @Test
    public void onClick_labelContainer_showsLabelDialog() throws Exception {
        authorize();
        java.util.List<Label> labels = Arrays.asList(
                label("bug", "d73a4a"), label("enhancement", "a2eeef"));
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(labels))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            IssueEditActivity.LabelEditDialogFragment f =
                    (IssueEditActivity.LabelEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("labeledit");
            assertNotNull(f);
            AlertDialog dialog = (AlertDialog) f.getDialog();
            assertNotNull(dialog);
            androidx.recyclerview.widget.RecyclerView labelList =
                    dialog.findViewById(R.id.label_list);
            assertNotNull(labelList);
            labelList.layout(0, 0, 1080, 1920);
            idleMain(100);
            assertEquals(2, labelList.getAdapter().getItemCount());
            android.view.View row0 = labelList.getChildAt(0);
            assertEquals("bug",
                    ((android.widget.TextView) row0.findViewById(R.id.tv_title))
                            .getText().toString());
        }
    }

    @Test
    public void labelDialog_toggleAndOk_updatesLabels() throws Exception {
        authorize();
        java.util.List<Label> labels = Arrays.asList(
                label("bug", "d73a4a"), label("enhancement", "a2eeef"));
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(singlePage(labels))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            IssueEditActivity.LabelEditDialogFragment f =
                    (IssueEditActivity.LabelEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("labeledit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            androidx.recyclerview.widget.RecyclerView labelList =
                    dialog.findViewById(R.id.label_list);
            labelList.layout(0, 0, 1080, 1920);
            idleMain(100);
            android.view.View row1 = labelList.getChildAt(1);
            row1.findViewById(R.id.tv_title).performClick();
            idleMain(100);
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idleMain(200);
            java.util.List<Label> selected = ((Issue) get(activity, "mEditIssue")).labels();
            assertEquals(1, selected.size());
            assertEquals("enhancement", selected.get(0).name());
        }
    }

    @Test
    public void labelDialog_manageButton_launchesListAndResetsSingle() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            singlePage(Arrays.asList(label("bug", "d73a4a"))))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            IssueEditActivity.LabelEditDialogFragment f =
                    (IssueEditActivity.LabelEditDialogFragment) activity
                            .getSupportFragmentManager().findFragmentByTag("labeledit");
            AlertDialog dialog = (AlertDialog) f.getDialog();
            assertNotNull(get(activity, "mLabelSingle"));
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick();
            idleMain(200);
            ShadowActivity.IntentForResult result =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(result);
            assertEquals(IssueLabelListActivity.class.getName(),
                    result.intent.getComponent().getClassName());
            activity.getActivityResultRegistry().dispatchResult(
                    result.requestCode, android.app.Activity.RESULT_OK, null);
            assertNull(get(activity, "mLabelSingle"));
        }
    }

    @Test
    public void loadLabels_cachedSingle_fetchesOnce() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            singlePage(Arrays.asList(label("bug", "d73a4a"))))));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            // Dismiss and reopen: the cached Single is reused, no second fetch.
            activity.getSupportFragmentManager().beginTransaction()
                    .remove(activity.getSupportFragmentManager()
                            .findFragmentByTag("labeledit")).commitNow();
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            verify(mocks.labelService, timeout(5000).times(1))
                    .getRepositoryLabels(eq("o"), eq("r"), anyLong());
        }
    }


    // ---------- saveIssue ----------

    private void clickFab(IssueEditActivity activity) {
        activity.onClick((android.view.View) get(activity, "mFab"));
    }

    @Test
    public void saveIssue_create_success_finishesWithResult() throws Exception {
        authorize();
        Issue created = Issue.builder().number(42).title("My title").body("My body").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.createIssue(eq("o"), eq("r"), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(created)));
            IssueEditActivity activity = launchCreateChecked("My title", "My body");
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            assertTrue(activity.isFinishing());
            assertEquals(android.app.Activity.RESULT_OK,
                    Shadows.shadowOf(activity).getResultCode());
            Intent resultIntent = Shadows.shadowOf(activity).getResultIntent();
            assertNotNull(resultIntent);
            assertEquals(42,
                    ((Issue) resultIntent.getParcelableExtra("issue")).number().intValue());
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).createIssue(eq("o"), eq("r"), captor.capture());
            assertEquals("My title", captor.getValue().title());
            assertEquals("My body", captor.getValue().body());
        }
    }

    @Test
    public void saveIssue_edit_titleChanged_sendsOnlyTitle() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("New title").body("B").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            ((android.widget.EditText) get(activity, "mTitleView")).setText("New title");
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertEquals("New title", captor.getValue().title());
            assertNull(captor.getValue().body());
            assertNull(captor.getValue().milestone());
            assertNull(captor.getValue().assignees());
            assertNull(captor.getValue().labels());
        }
    }

    @Test
    public void saveIssue_edit_bodyChanged_sendsOnlyBody() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("T").body("New body").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            ((android.widget.EditText) get(activity, "mDescView")).setText("New body");
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertNull(captor.getValue().title());
            assertEquals("New body", captor.getValue().body());
        }
    }

    @Test
    public void saveIssue_edit_milestoneChanged_sendsOnlyMilestone() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("T").body("B")
                .milestone(milestone(2, "v2.0")).build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            call(activity, "updateMilestone", new Class<?>[]{Milestone.class},
                    new Object[]{milestone(2, "v2.0")});
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertNull(captor.getValue().title());
            assertEquals(Integer.valueOf(2), captor.getValue().milestone());
        }
    }

    @Test
    public void saveIssue_edit_milestoneCleared_sendsNullMilestone() throws Exception {
        authorize();
        Issue withMilestone = Issue.builder().number(12).title("T").body("B")
                .milestone(milestone(1, "v1.0")).build();
        Issue updated = Issue.builder().number(12).title("T").body("B").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(withMilestone);
            call(activity, "updateMilestone", new Class<?>[]{Milestone.class},
                    new Object[]{null});
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertNull(captor.getValue().milestone());
        }
    }

    @Test
    public void saveIssue_edit_assigneesChanged_sendsAssignees() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("T").body("B").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            java.util.List<User> assignees = Arrays.asList(
                    User.builder().login("alice").id(1L).build(),
                    User.builder().login("bob").id(2L).build());
            call(activity, "updateAssignees", new Class<?>[]{java.util.List.class},
                    new Object[]{assignees});
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertEquals(Arrays.asList("alice", "bob"), captor.getValue().assignees());
        }
    }

    @Test
    public void saveIssue_edit_labelsChanged_sendsLabels() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("T").body("B").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            call(activity, "updateLabels", new Class<?>[]{java.util.List.class},
                    new Object[]{Arrays.asList(label("bug", "d73a4a"))});
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertEquals(Arrays.asList("bug"), captor.getValue().labels());
        }
    }

    @Test
    public void saveIssue_edit_noChanges_sendsEmptyRequest() throws Exception {
        authorize();
        Issue updated = Issue.builder().number(12).title("T").body("B").build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.just(Response.success(updated)));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            clickFab(activity);
            waitFor(() -> activity.isFinishing(), 10000);
            ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
            verify(mocks.issueService).editIssue(eq("o"), eq("r"), eq(12L), captor.capture());
            assertNull(captor.getValue().title());
            assertNull(captor.getValue().body());
            assertNull(captor.getValue().milestone());
            assertNull(captor.getValue().assignees());
            assertNull(captor.getValue().labels());
        }
    }

    @Test
    public void saveIssue_create_failure_showsErrorSnackbar() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.createIssue(eq("o"), eq("r"), any(IssueRequest.class)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            IssueEditActivity activity = launchCreateChecked("My title", "My body");
            clickFab(activity);
            waitForSnackbarWithText(activity,
                    appContext().getString(R.string.issue_error_create));
            dismissCurrentSnackbarAsTimeout();
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void saveIssue_edit_failure_showsErrorSnackbar() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.issueService.editIssue(eq("o"), eq("r"), eq(12L), any(IssueRequest.class)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            clickFab(activity);
            waitForSnackbarWithText(activity,
                    appContext().getString(R.string.issue_error_edit, 12));
            dismissCurrentSnackbarAsTimeout();
            assertFalse(activity.isFinishing());
        }
    }


    // ---------- loadCollaboratorStatus / EditPagerAdapter ----------

    @Test
    public void loadCollaboratorStatus_success_setsCollaborator() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mIsCollaborator")), 10000);
            assertTrue((boolean) get(activity, "mIsCollaborator"));
        }
    }

    @Test
    public void loadCollaboratorStatus_failure_showsErrorView() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            mocks.single.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", false))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue((boolean) get(activity, "mErrorShown"));
        }
    }

    @Test
    public void editPagerAdapter_instantiateItem_returnsViews() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        androidx.viewpager.widget.ViewPager pager =
                (androidx.viewpager.widget.ViewPager) call(activity, "getPager",
                        new Class<?>[]{}, new Object[]{});
        Object adapter = pager.getAdapter();
        assertNotNull(adapter);
        android.view.ViewGroup root = (android.view.ViewGroup) get(activity, "mRootView");
        java.lang.reflect.Method instantiate = adapter.getClass()
                .getMethod("instantiateItem", android.view.ViewGroup.class, int.class);
        instantiate.setAccessible(true);
        assertEquals(activity.findViewById(R.id.editor_container),
                instantiate.invoke(adapter, root, 0));
        assertEquals(activity.findViewById(R.id.preview),
                instantiate.invoke(adapter, root, 1));
        assertEquals(activity.findViewById(R.id.options),
                instantiate.invoke(adapter, root, 2));
        assertNull(instantiate.invoke(adapter, root, 5));
    }

    @Test
    public void editPagerAdapter_isViewFromObject() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        androidx.viewpager.widget.ViewPager pager =
                (androidx.viewpager.widget.ViewPager) call(activity, "getPager",
                        new Class<?>[]{}, new Object[]{});
        Object adapter = pager.getAdapter();
        java.lang.reflect.Method isView = adapter.getClass()
                .getMethod("isViewFromObject", android.view.View.class, Object.class);
        isView.setAccessible(true);
        android.view.View v = new android.view.View(appContext());
        assertTrue((boolean) isView.invoke(adapter, v, v));
        assertFalse((boolean) isView.invoke(adapter, v, new android.view.View(appContext())));
    }

    // ---------- dialog fragments, direct ----------

    private IssueEditActivity.MilestoneEditDialogFragment showMilestoneDialog(
            IssueEditActivity activity, Milestone selected, java.util.List<Milestone> all)
            throws Exception {
        IssueEditActivity.MilestoneEditDialogFragment f =
                IssueEditActivity.MilestoneEditDialogFragment.newInstance(selected, all);
        activity.getSupportFragmentManager().beginTransaction().add(f, "m").commitNow();
        Dialog dialog = f.onCreateDialog(null);
        dialog.show();
        return f;
    }

    @Test
    public void milestoneDialog_preselectsCurrentMilestone() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        IssueEditActivity.MilestoneEditDialogFragment f = showMilestoneDialog(activity,
                milestone(2, "v2.0"),
                Arrays.asList(milestone(1, "v1.0"), milestone(2, "v2.0")));
        AlertDialog dialog = (AlertDialog) f.getDialog();
        // The framework-created dialog (from commitNow) is the one shown.
        if (dialog == null) {
            dialog = (AlertDialog) f.onCreateDialog(null);
            dialog.show();
        }
        assertEquals(2, dialog.getListView().getCheckedItemPosition());
    }

    @Test
    public void milestoneDialog_nullNumberMilestone_matchesByNullNumber() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        Milestone noNumber = Milestone.builder().title("v1.0").build();
        Milestone selectedNoNumber = Milestone.builder().title("v1.0").build();
        assertNull(noNumber.number());
        IssueEditActivity.MilestoneEditDialogFragment f = showMilestoneDialog(activity,
                selectedNoNumber, Arrays.asList(noNumber, milestone(2, "v2.0")));
        AlertDialog dialog = (AlertDialog) f.getDialog();
        if (dialog == null) {
            dialog = (AlertDialog) f.onCreateDialog(null);
            dialog.show();
        }
        // ObjectsCompat.equals(null, null) is true, so the null-number
        // milestone is pre-selected at position 1.
        assertEquals(1, dialog.getListView().getCheckedItemPosition());
    }

    @Test
    public void assigneeDialog_preselectsCurrentAssignees() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        java.util.List<User> selected = Arrays.asList(
                User.builder().login("alice").id(1L).build());
        java.util.List<User> all = Arrays.asList(
                User.builder().login("alice").id(1L).build(),
                User.builder().login("bob").id(2L).build());
        IssueEditActivity.AssigneeEditDialogFragment f =
                IssueEditActivity.AssigneeEditDialogFragment.newInstance(selected, all);
        activity.getSupportFragmentManager().beginTransaction().add(f, "a").commitNow();
        idleMain(100);
        AlertDialog alert = (AlertDialog) f.getDialog();
        if (alert == null) {
            alert = (AlertDialog) f.onCreateDialog(null);
            alert.show();
        }
        assertTrue(alert.getListView().isItemChecked(0));
        assertFalse(alert.getListView().isItemChecked(1));
    }

    @Test
    public void labelDialog_selectedRowIsBoldUnselectedIsNormal() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        java.util.List<Label> selected = Arrays.asList(label("bug", "d73a4a"));
        java.util.List<Label> all = Arrays.asList(
                label("bug", "d73a4a"), label("enhancement", "a2eeef"));
        IssueEditActivity.LabelEditDialogFragment f =
                IssueEditActivity.LabelEditDialogFragment.newInstance(selected, all);
        activity.getSupportFragmentManager().beginTransaction().add(f, "l").commitNow();
        Dialog dialog = f.onCreateDialog(null);
        dialog.show();
        androidx.recyclerview.widget.RecyclerView labelList =
                dialog.findViewById(R.id.label_list);
        labelList.layout(0, 0, 1080, 1920);
        idleMain(100);
        android.view.View row0 = labelList.getChildAt(0);
        android.view.View row1 = labelList.getChildAt(1);
        assertEquals(android.graphics.Typeface.BOLD,
                ((android.widget.TextView) row0.findViewById(R.id.tv_title))
                        .getTypeface().getStyle());
        assertEquals(android.graphics.Typeface.NORMAL,
                ((android.widget.TextView) row1.findViewById(R.id.tv_title))
                        .getTypeface().getStyle());
    }


    // ---------- loadIssueTemplates / handleIssueTemplateSelected ----------

    private static final String BUG_TEMPLATE =
            "---\nname: Bug report\ntitle: \"[Bug] \"\nlabels: bug\nassignees: alice\n---\n"
                    + "Please describe the bug";
    private static final String FEATURE_TEMPLATE =
            "---\nname: Feature request\ntitle: \"[Feature] \"\n---\nI'd like a new feature";

    private void stubTemplates(Mocks mocks, String path, java.util.List<Content> dir,
            java.util.Map<String, String> rawByPath) {
        when(mocks.contentService.getDirectoryContents(eq("o"), eq("r"), eq(path),
                isNull(), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Content>builder().items(dir).build())));
        for (java.util.Map.Entry<String, String> e : rawByPath.entrySet()) {
            when(mocks.contentService.getContentsRaw(eq("o"), eq("r"), eq(e.getKey()), isNull()))
                    .thenReturn(Single.just(Response.success(
                            e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
    }

    private void stubEmptyDir(Mocks mocks, String path) {
        when(mocks.contentService.getDirectoryContents(eq("o"), eq("r"), eq(path),
                isNull(), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Content>builder()
                                .items(new ArrayList<Content>()).build())));
    }

    @Test
    public void loadIssueTemplates_singleTemplate_appliesIt() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            // The collaborator list must contain alice for the template's
            // default assignee to be valid.
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build(),
                                    User.builder().login("bob").id(2L).build())).build())));
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"),
                                    label("enhancement", "a2eeef"))).build())));
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template", ".github/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            stubTemplates(mocks, ".github/issue_template",
                    Arrays.asList(content("bug.md", ".github/issue_template/bug.md",
                            ContentType.File)),
                    java.util.Collections.singletonMap(
                            ".github/issue_template/bug.md", BUG_TEMPLATE));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
            assertEquals("Please describe the bug",
                    ((android.widget.EditText) get(activity, "mDescView")).getText().toString());
            waitFor(() -> {
                Issue e = (Issue) get(activity, "mEditIssue");
                return e != null && e.assignees() != null && e.assignees().size() == 1
                        && e.labels() != null && e.labels().size() == 1;
            }, 10000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertEquals("alice", edited.assignees().get(0).login());
            assertEquals("bug", edited.labels().get(0).name());
        }
    }

    @Test
    public void loadIssueTemplates_multipleTemplates_showsSelectionDialog() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template", ".github/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            java.util.Map<String, String> raw = new java.util.HashMap<>();
            raw.put(".github/issue_template/bug.md", BUG_TEMPLATE);
            raw.put(".github/issue_template/feature.md", FEATURE_TEMPLATE);
            stubTemplates(mocks, ".github/issue_template",
                    Arrays.asList(
                            content("bug.md", ".github/issue_template/bug.md", ContentType.File),
                            content("feature.md", ".github/issue_template/feature.md",
                                    ContentType.File)),
                    raw);
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> activity.getSupportFragmentManager()
                    .findFragmentByTag("template-selection") != null, 10000);
            DialogFragment frag = (DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("template-selection");
            AlertDialog dialog = (AlertDialog) frag.getDialog();
            assertEquals(3, dialog.getListView().getAdapter().getCount());
            // Click the first named template (Bug report, sorted first).
            dialog.getListView().performItemClick(
                    dialog.getListView().getAdapter().getView(1, null, dialog.getListView()),
                    1, dialog.getListView().getAdapter().getItemId(1));
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_noTemplates_startsBlank() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubEmptyDir(mocks, "/.github");
            stubEmptyDir(mocks, "");
            stubEmptyDir(mocks, "/docs");
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> ((android.widget.EditText) get(activity, "mTitleView")).isEnabled(),
                    10000);
            assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
            assertTrue(((android.widget.EditText) get(activity, "mDescView")).isEnabled());
            assertEquals("", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_github404_fallsBackToRoot() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.contentService.getDirectoryContents(eq("o"), eq("r"), eq("/.github"),
                    isNull(), anyLong()))
                    .thenReturn(Single.error(apiError(404)));
            stubTemplates(mocks, "",
                    Arrays.asList(content("issue_template", "issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            stubTemplates(mocks, "issue_template",
                    Arrays.asList(content("bug.md", "issue_template/bug.md", ContentType.File)),
                    java.util.Collections.singletonMap("issue_template/bug.md", BUG_TEMPLATE));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_rootEmpty_fallsBackToDocs() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubEmptyDir(mocks, "/.github");
            stubEmptyDir(mocks, "");
            stubTemplates(mocks, "/docs",
                    Arrays.asList(content("issue_template", "/docs/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            stubTemplates(mocks, "/docs/issue_template",
                    Arrays.asList(content("bug.md", "/docs/issue_template/bug.md",
                            ContentType.File)),
                    java.util.Collections.singletonMap(
                            "/docs/issue_template/bug.md", BUG_TEMPLATE));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_non404Error_startsBlankAndLogs() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.contentService.getDirectoryContents(eq("o"), eq("r"), eq("/.github"),
                    isNull(), anyLong()))
                    .thenReturn(Single.error(apiError(500)));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> ((android.widget.EditText) get(activity, "mTitleView")).isEnabled(),
                    10000);
            assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
            assertTrue(logContains(com.gh4a.Gh4Application.LOG_TAG,
                    "Loading issue templates failed"));
        }
    }

    @Test
    public void loadIssueTemplates_rawFetchError_startsBlankAndLogs() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template", ".github/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            Content bugFile = content("bug.md", ".github/issue_template/bug.md",
                    ContentType.File);
            when(mocks.contentService.getDirectoryContents(eq("o"), eq("r"),
                    eq(".github/issue_template"), isNull(), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Content>builder().items(Arrays.asList(bugFile)).build())));
            when(mocks.contentService.getContentsRaw(eq("o"), eq("r"),
                    eq(".github/issue_template/bug.md"), isNull()))
                    .thenReturn(Single.error(apiError(500)));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> ((android.widget.EditText) get(activity, "mTitleView")).isEnabled(),
                    10000);
            assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
            assertTrue(logContains(com.gh4a.Gh4Application.LOG_TAG,
                    "Loading issue templates failed"));
        }
    }

    @Test
    public void loadIssueTemplates_nonMdFiles_ignored() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template", ".github/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            stubTemplates(mocks, ".github/issue_template",
                    Arrays.asList(
                            content("notes.txt", ".github/issue_template/notes.txt",
                                    ContentType.File),
                            content(null, ".github/issue_template/unnamed.md",
                                    ContentType.File),
                            content("sub", ".github/issue_template/sub",
                                    ContentType.Directory)),
                    java.util.Collections.emptyMap());
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> ((android.widget.EditText) get(activity, "mTitleView")).isEnabled(),
                    10000);
            assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
            assertEquals("", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
            // No selection dialog: zero named templates means a blank start.
            assertNull(activity.getSupportFragmentManager()
                    .findFragmentByTag("template-selection"));
        }
    }

    @Test
    public void handleIssueTemplateSelected_null_enablesFieldsOnly() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        call(activity, "handleIssueTemplateSelected", new Class<?>[]{templateClass()},
                new Object[]{null});
        assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
        assertTrue(((android.widget.EditText) get(activity, "mDescView")).isEnabled());
        assertEquals("T", ((android.widget.EditText) get(activity, "mTitleView"))
                .getText().toString());
    }

    @Test
    public void handleIssueTemplateSelected_withDefaults_appliesValidOnes() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build(),
                                    User.builder().login("bob").id(2L).build())).build())));
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"),
                                    label("enhancement", "a2eeef"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            Object template = newTemplate(
                    "---\nname: Bug\nlabels: bug, missing\nassignees: alice, mallory\n---\nBody");
            call(activity, "handleIssueTemplateSelected",
                    new Class<?>[]{templateClass()}, new Object[]{template});
            waitFor(() -> {
                Issue e = (Issue) get(activity, "mEditIssue");
                return e != null && e.assignees() != null && e.assignees().size() == 1
                        && e.labels() != null && e.labels().size() == 1;
            }, 10000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertEquals("alice", edited.assignees().get(0).login());
            assertEquals("bug", edited.labels().get(0).name());
            assertEquals("Body", ((android.widget.EditText) get(activity, "mDescView"))
                    .getText().toString());
        }
    }

    // ---------- IssueEditActivity.IssueTemplateSelectionDialogFragment ----------

    private IssueEditActivity.IssueTemplateSelectionDialogFragment showTemplateSelectionDialog(
            IssueEditActivity activity, java.util.List templates) {
        IssueEditActivity.IssueTemplateSelectionDialogFragment f =
                IssueEditActivity.IssueTemplateSelectionDialogFragment.newInstance(templates);
        activity.getSupportFragmentManager().beginTransaction().add(f, "t").commitNow();
        idleMain(100);
        return f;
    }

    @Test
    public void templateSelectionDialog_newInstance_hasBlankFirstItem() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        IssueEditActivity.IssueTemplateSelectionDialogFragment f = showTemplateSelectionDialog(activity,
                Arrays.asList(newTemplate(BUG_TEMPLATE)));
        AlertDialog dialog = (AlertDialog) f.getDialog();
        assertNotNull(dialog);
        assertEquals(2, dialog.getListView().getAdapter().getCount());
        // The first item is the blank entry.
        assertEquals("Blank issue",
                ((com.gh4a.adapter.ItemsWithDescriptionAdapter.Item) dialog.getListView()
                        .getAdapter().getItem(0)).title());
    }

    @Test
    public void templateSelectionDialog_selectBlank_startsBlank() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        IssueEditActivity.IssueTemplateSelectionDialogFragment f = showTemplateSelectionDialog(activity,
                Arrays.asList(newTemplate(BUG_TEMPLATE)));
        AlertDialog dialog = (AlertDialog) f.getDialog();
        dialog.getListView().performItemClick(
                dialog.getListView().getAdapter().getView(0, null, dialog.getListView()),
                0, dialog.getListView().getAdapter().getItemId(0));
        idleMain(200);
        assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
        assertEquals("T", ((android.widget.EditText) get(activity, "mTitleView"))
                .getText().toString());
    }

    @Test
    public void templateSelectionDialog_onCancel_startsBlank() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        IssueEditActivity.IssueTemplateSelectionDialogFragment f = showTemplateSelectionDialog(activity,
                Arrays.asList(newTemplate(BUG_TEMPLATE)));
        f.onCancel(null);
        idleMain(200);
        assertTrue(((android.widget.EditText) get(activity, "mTitleView")).isEnabled());
        assertEquals("T", ((android.widget.EditText) get(activity, "mTitleView"))
                .getText().toString());
    }

    // ---------- Batch F: remaining branch arms ----------

    @Test
    public void onClick_unknownView_doesNothing() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        activity.onClick(new android.view.View(appContext()));
        // No dialog, no save, no crash.
        assertNull(activity.getSupportFragmentManager()
                .findFragmentByTag("milestoneedit"));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void showAssigneesDialog_existingAssignees_preselected() throws Exception {
        authorize();
        Issue withAssignees = Issue.builder().number(12).title("T").body("B")
                .assignees(Arrays.asList(User.builder().login("alice").id(1L).build()))
                .build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build(),
                                    User.builder().login("bob").id(2L).build())).build())));
            IssueEditActivity activity = launchEditChecked(withAssignees);
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            AlertDialog dialog = (AlertDialog) ((DialogFragment) activity
                    .getSupportFragmentManager().findFragmentByTag("assigneeedit")).getDialog();
            assertTrue(dialog.getListView().isItemChecked(0));
            assertFalse(dialog.getListView().isItemChecked(1));
        }
    }

    @Test
    public void showLabelDialog_existingLabels_preselected() throws Exception {
        authorize();
        Issue withLabels = Issue.builder().number(12).title("T").body("B")
                .labels(Arrays.asList(label("bug", "d73a4a"))).build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"),
                                    label("enhancement", "a2eeef"))).build())));
            IssueEditActivity activity = launchEditChecked(withLabels);
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            Dialog dialog = ((DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("labeledit")).getDialog();
            androidx.recyclerview.widget.RecyclerView labelList =
                    dialog.findViewById(R.id.label_list);
            labelList.layout(0, 0, 1080, 1920);
            idleMain(100);
            android.view.View row0 = labelList.getChildAt(0);
            assertEquals(android.graphics.Typeface.BOLD,
                    ((android.widget.TextView) row0.findViewById(R.id.tv_title))
                            .getTypeface().getStyle());
        }
    }

    @Test
    public void manageMilestones_pullRequestIntent_hasFromPrTrue() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Milestone>builder().items(Arrays.asList(
                                    milestone(1, "v1.0"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, true));
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            AlertDialog dialog = (AlertDialog) ((DialogFragment) activity
                    .getSupportFragmentManager().findFragmentByTag("milestoneedit")).getDialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            idleMain(200);
            android.content.Intent intent = org.robolectric.Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertEquals(IssueMilestoneListActivity.class.getName(),
                    intent.getComponent().getClassName());
            assertTrue(intent.getBooleanExtra("from_pr", false));
        }
    }

    @Test
    public void manageLabels_pullRequestIntent_hasFromPrTrue() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, true));
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            Dialog dialog = ((DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("labeledit")).getDialog();
            dialog.findViewById(android.R.id.button3).performClick();
            idleMain(200);
            android.content.Intent intent = org.robolectric.Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertEquals(IssueLabelListActivity.class.getName(),
                    intent.getComponent().getClassName());
            assertTrue(intent.getBooleanExtra("from_pr", false));
        }
    }

    @Test
    public void updateAssignees_emptyList_showsClearRow() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        call(activity, "updateAssignees", new Class<?>[]{java.util.List.class},
                new Object[]{new ArrayList<User>()});
        android.view.ViewGroup container =
                (android.view.ViewGroup) get(activity, "mSelectedAssigneeContainer");
        assertEquals(1, container.getChildCount());
        android.widget.TextView tv = container.getChildAt(0).findViewById(R.id.tv_assignee);
        assertEquals(appContext().getString(R.string.issue_clear_assignee), tv.getText().toString());
    }

    @Test
    public void updateLabels_emptyList_showsNoLabels() throws Exception {
        authorize();
        IssueEditActivity activity = launchEditChecked(issue(12, false));
        call(activity, "updateLabels", new Class<?>[]{java.util.List.class},
                new Object[]{new ArrayList<Label>()});
        android.widget.TextView labelsView =
                (android.widget.TextView) get(activity, "mLabelsView");
        assertEquals(appContext().getString(R.string.issue_no_labels),
                labelsView.getText().toString());
    }

    @Test
    public void loadMilestones_secondOpen_usesCachedSingle() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.milestoneService.getRepositoryMilestones(
                    eq("o"), eq("r"), eq("open"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Milestone>builder().items(Arrays.asList(
                                    milestone(1, "v1.0"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            // Close the dialog, then open it again: the cached Single is reused.
            ((DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("milestoneedit")).dismiss();
            idleMain(100);
            activity.onClick(activity.findViewById(R.id.milestone_container));
            waitForFragment(activity, "milestoneedit");
            verify(mocks.milestoneService, timeout(5000).times(1))
                    .getRepositoryMilestones(eq("o"), eq("r"), eq("open"), anyLong());
        }
    }

    @Test
    public void loadPotentialAssignees_secondOpen_usesCachedSingle() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("bob").id(2L).build())).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            ((DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("assigneeedit")).dismiss();
            idleMain(100);
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            verify(mocks.collaboratorService, timeout(5000).times(1))
                    .getCollaborators(eq("o"), eq("r"), anyLong());
        }
    }

    @Test
    public void loadPotentialAssignees_creatorAlreadyListed_notDuplicated() throws Exception {
        authorize();
        User alice = User.builder().login("alice").id(1L).build();
        Issue withCreator = Issue.builder().number(12).title("T").body("B")
                .user(alice).build();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    alice,
                                    User.builder().login("bob").id(2L).build())).build())));
            IssueEditActivity activity = launchEditChecked(withCreator);
            activity.onClick(activity.findViewById(R.id.assignee_container));
            waitForFragment(activity, "assigneeedit");
            AlertDialog dialog = (AlertDialog) ((DialogFragment) activity
                    .getSupportFragmentManager().findFragmentByTag("assigneeedit")).getDialog();
            // No duplicate alice: exactly the two collaborators.
            assertEquals(2, dialog.getListView().getAdapter().getCount());
        }
    }

    @Test
    public void loadIssueTemplates_githubListingSkipsNonTemplates() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"))).build())));
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build())).build())));
            stubTemplates(mocks, "/.github",
                    Arrays.asList(
                            content(null, ".github/unnamed", ContentType.File),
                            content("other.txt", ".github/other.txt", ContentType.File),
                            content("issue_template", ".github/issue_template",
                                    ContentType.Directory)),
                    java.util.Collections.emptyMap());
            stubTemplates(mocks, ".github/issue_template",
                    Arrays.asList(content("bug.md", ".github/issue_template/bug.md",
                            ContentType.File)),
                    java.util.Collections.singletonMap(
                            ".github/issue_template/bug.md", BUG_TEMPLATE));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_issueTemplateAsFile_appliesIt() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"))).build())));
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build())).build())));
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template.md", ".github/issue_template.md",
                            ContentType.File)),
                    java.util.Collections.singletonMap(
                            ".github/issue_template.md", BUG_TEMPLATE));
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> "[Bug] ".equals(
                    ((android.widget.EditText) get(activity, "mTitleView")).getText().toString()),
                    10000);
            assertEquals("[Bug] ", ((android.widget.EditText) get(activity, "mTitleView"))
                    .getText().toString());
        }
    }

    @Test
    public void loadIssueTemplates_unnamedTemplate_filteredFromDialog() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            stubTemplates(mocks, "/.github",
                    Arrays.asList(content("issue_template", ".github/issue_template",
                            ContentType.Directory)),
                    java.util.Collections.emptyMap());
            java.util.Map<String, String> raw = new java.util.HashMap<>();
            raw.put(".github/issue_template/bug.md", BUG_TEMPLATE);
            raw.put(".github/issue_template/unnamed.md",
                    "---\ntitle: \"[X] \"\n---\nNo name here");
            stubTemplates(mocks, ".github/issue_template",
                    Arrays.asList(
                            content("bug.md", ".github/issue_template/bug.md", ContentType.File),
                            content("unnamed.md", ".github/issue_template/unnamed.md",
                                    ContentType.File)),
                    raw);
            IssueEditActivity activity = launchCreateBlank();
            waitFor(() -> activity.getSupportFragmentManager()
                    .findFragmentByTag("template-selection") != null, 10000);
            DialogFragment frag = (DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("template-selection");
            AlertDialog dialog = (AlertDialog) frag.getDialog();
            // Blank entry + the one named template; the unnamed one is filtered out.
            assertEquals(2, dialog.getListView().getAdapter().getCount());
        }
    }

    @Test
    public void handleIssueTemplateSelected_labelsOnly_appliesLabelsNotAssignees()
            throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            Object template = newTemplate("---\nname: X\nlabels: bug\n---\nBody");
            call(activity, "handleIssueTemplateSelected",
                    new Class<?>[]{templateClass()}, new Object[]{template});
            waitFor(() -> {
                Issue e = (Issue) get(activity, "mEditIssue");
                return e != null && e.labels() != null && e.labels().size() == 1;
            }, 10000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertEquals("bug", edited.labels().get(0).name());
            assertNull(edited.assignees());
        }
    }

    @Test
    public void handleIssueTemplateSelected_assigneesOnly_appliesAssigneesNotLabels()
            throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build())).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            Object template = newTemplate("---\nname: X\nassignees: alice\n---\nBody");
            call(activity, "handleIssueTemplateSelected",
                    new Class<?>[]{templateClass()}, new Object[]{template});
            waitFor(() -> {
                Issue e = (Issue) get(activity, "mEditIssue");
                return e != null && e.assignees() != null && e.assignees().size() == 1;
            }, 10000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertEquals("alice", edited.assignees().get(0).login());
            assertNull(edited.labels());
        }
    }

    @Test
    public void handleIssueTemplateSelected_allAssigneesInvalid_keepsNull() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.collaboratorService.getCollaborators(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<User>builder().items(Arrays.asList(
                                    User.builder().login("alice").id(1L).build(),
                                    User.builder().login("bob").id(2L).build())).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            Object template = newTemplate("---\nname: X\nassignees: mallory\n---\nBody");
            call(activity, "handleIssueTemplateSelected",
                    new Class<?>[]{templateClass()}, new Object[]{template});
            idleMain(3000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertNull(edited.assignees());
            assertEquals("Body", ((android.widget.EditText) get(activity, "mDescView"))
                    .getText().toString());
        }
    }

    @Test
    public void handleIssueTemplateSelected_allLabelsInvalid_keepsNull() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            Object template = newTemplate("---\nname: X\nlabels: missing\n---\nBody");
            call(activity, "handleIssueTemplateSelected",
                    new Class<?>[]{templateClass()}, new Object[]{template});
            idleMain(3000);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertNull(edited.labels());
            assertEquals("Body", ((android.widget.EditText) get(activity, "mDescView"))
                    .getText().toString());
        }
    }

    @Test
    public void labelDialog_clickSelectedLabel_deselectsIt() throws Exception {
        authorize();
        try (Mocks mocks = new Mocks(true)) {
            when(mocks.labelService.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                    .thenReturn(Single.just(Response.success(
                            Page.<Label>builder().items(Arrays.asList(
                                    label("bug", "d73a4a"),
                                    label("enhancement", "a2eeef"))).build())));
            IssueEditActivity activity = launchEditChecked(issue(12, false));
            call(activity, "updateLabels", new Class<?>[]{java.util.List.class},
                    new Object[]{Arrays.asList(label("bug", "d73a4a"))});
            activity.onClick(activity.findViewById(R.id.label_container));
            waitForFragment(activity, "labeledit");
            Dialog dialog = ((DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("labeledit")).getDialog();
            androidx.recyclerview.widget.RecyclerView labelList =
                    dialog.findViewById(R.id.label_list);
            labelList.layout(0, 0, 1080, 1920);
            idleMain(100);
            // The bug row starts selected (bold); clicking it deselects it.
            // (The intermediate typeface flip cannot be asserted here: under
            // Robolectric, TextView.setTypeface(bold, NORMAL) is a no-op for
            // the style, so the row still reports BOLD. The deselection itself
            // is proven by the OK result below.)
            android.view.View row0 = labelList.getChildAt(0);
            row0.findViewById(R.id.tv_title).performClick();
            idleMain(100);
            dialog.findViewById(android.R.id.button1).performClick();
            idleMain(200);
            Issue edited = (Issue) get(activity, "mEditIssue");
            assertTrue(edited.labels() == null || edited.labels().isEmpty());
        }
    }





}
