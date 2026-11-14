package com.gh4a.activities;

import android.content.Intent;
import android.widget.Spinner;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.request.pull_request.CreateReview;
import com.meisolsson.githubsdk.model.request.pull_request.SubmitReview;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;
import static com.gh4a.activities.ActivityTestHelper.get;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class CreateReviewActivityTest {
    private Intent intent(Review pending) {
        return CreateReviewActivity.makeIntent(appContext(), "o", "r", 42, false, pending);
    }

    @Test
    public void makeIntent_setsExtras() {
        Intent i = intent(null);
        assertEquals(CreateReviewActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertEquals(42, i.getIntExtra("pr_number", -1));
        assertFalse(i.getBooleanExtra("draft_pr", true));
        assertNull(i.getParcelableExtra("pending_review"));
    }

    @Test
    public void makeIntent_draftPr_setsFlag() {
        Intent i = CreateReviewActivity.makeIntent(appContext(), "o", "r", 1, true, null);
        assertTrue(i.getBooleanExtra("draft_pr", false));
    }

    @Test
    public void launch_titleAndSpinnerHasThreeEvents() {
        CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(null));
        TextView title = activity.findViewById(R.id.review_dialog_title);
        assertEquals(appContext().getString(R.string.pull_request_review_dialog_title, 42),
                title.getText().toString());
        Spinner spinner = activity.findViewById(R.id.pull_request_review_event);
        assertEquals(3, spinner.getAdapter().getCount());
        assertEquals(appContext().getString(R.string.pull_request_review_event_comment),
                spinner.getAdapter().getItem(0).toString());
        assertEquals(appContext().getString(R.string.pull_request_review_event_approve),
                spinner.getAdapter().getItem(1).toString());
        assertEquals(appContext().getString(R.string.pull_request_review_event_request_changes),
                spinner.getAdapter().getItem(2).toString());
    }

    @Test
    public void launch_pendingReview_prefillsCommentText() {
        Review pending = mock(Review.class);
        when(pending.id()).thenReturn(9L);
        when(pending.body()).thenReturn("draft text");
        CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(pending));
        assertEquals("draft text",
                ((android.widget.EditText) activity.findViewById(R.id.et_basic_editor)).getText().toString());
    }

    @Test
    public void launch_withoutExtras_throwsNpe_A1_3() {
        // A1-3 (suspected, low): onCreate dereferences getIntent().getExtras()
        // without a null check, so launching without extras NPEs; every
        // production caller goes through makeIntent, which always sets extras.
        try {
            launch(CreateReviewActivity.class);
            fail("expected NullPointerException for missing extras");
        } catch (NullPointerException expected) {
            // getExtras() returned null
        }
    }

    @Test
    public void getCommentEditorHintResId_isZero() {
        assertEquals(0, launch(CreateReviewActivity.class, intent(null)).getCommentEditorHintResId());
    }

    @Test
    public void getEditorErrorMessageResId_isReviewCreateError() {
        assertEquals(R.string.review_create_error,
                launch(CreateReviewActivity.class, intent(null)).getEditorErrorMessageResId());
    }

    @Test
    public void onEditorTextSent_setsOkAndFinishes() {
        CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(null));
        activity.onEditorTextSent();
        assertTrue(activity.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK,
                org.robolectric.Shadows.shadowOf(activity).getResultCode());
    }

    @Test
    public void onEditorDoSend_noPending_callsCreateReview() {
        PullRequestReviewService svc = mock(PullRequestReviewService.class);
        when(svc.createReview(eq("o"), eq("r"), eq(42L), any()))
                .thenReturn(Single.just(Response.success(mock(Review.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewService.class, false)).thenReturn(svc);
            CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(null));
            activity.onEditorDoSend("looks good").blockingGet();
            ArgumentCaptor<CreateReview> captor = ArgumentCaptor.forClass(CreateReview.class);
            verify(svc).createReview(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals("looks good", captor.getValue().body());
            assertEquals(CreateReview.Event.Comment, captor.getValue().event());
        }
    }

    @Test
    public void onEditorDoSend_pendingWithoutId_callsCreateReview() {
        PullRequestReviewService svc = mock(PullRequestReviewService.class);
        when(svc.createReview(eq("o"), eq("r"), eq(42L), any()))
                .thenReturn(Single.just(Response.success(mock(Review.class))));
        Review pending = mock(Review.class);
        when(pending.id()).thenReturn(null);
        when(pending.body()).thenReturn("draft");
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewService.class, false)).thenReturn(svc);
            CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(pending));
            activity.onEditorDoSend("x").blockingGet();
            verify(svc).createReview(eq("o"), eq("r"), eq(42L), any());
        }
    }

    @Test
    public void onEditorDoSend_pendingWithId_callsSubmitReview() {
        PullRequestReviewService svc = mock(PullRequestReviewService.class);
        when(svc.submitReview(eq("o"), eq("r"), eq(42L), eq(9L), any()))
                .thenReturn(Single.just(Response.success(mock(Review.class))));
        Review pending = mock(Review.class);
        when(pending.id()).thenReturn(9L);
        when(pending.body()).thenReturn("draft");
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewService.class, false)).thenReturn(svc);
            CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(pending));
            activity.onEditorDoSend("approved").blockingGet();
            ArgumentCaptor<SubmitReview> captor = ArgumentCaptor.forClass(SubmitReview.class);
            verify(svc).submitReview(eq("o"), eq("r"), eq(42L), eq(9L), captor.capture());
            assertEquals("approved", captor.getValue().body());
            assertEquals(SubmitReview.Event.Comment, captor.getValue().event());
        }
    }

    @Test
    public void launch_draftPr_setsHighlightColorPath() {
        CreateReviewActivity activity = launch(CreateReviewActivity.class,
                CreateReviewActivity.makeIntent(appContext(), "o", "r", 42, true, null));
        assertNotNull(get(activity, "mEditorSheet"));
    }

    @Test
    public void getActivity_returnsSelf() {
        CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(null));
        assertEquals(activity, activity.getActivity());
    }

    @Test
    public void getRootLayout_returnsCoordinatorLayout() {
        CreateReviewActivity activity = launch(CreateReviewActivity.class, intent(null));
        assertNotNull(activity.getRootLayout());
    }

    @Test
    public void onCreate_pendingReviewWithSavedState_doesNotPrefillText() {
        Review pending = mock(Review.class);
        when(pending.id()).thenReturn(9L);
        when(pending.body()).thenReturn("draft text");
        CreateReviewActivity activity = controller(CreateReviewActivity.class, intent(pending))
                .create(new android.os.Bundle()).start().resume().get();
        assertEquals("", ((android.widget.EditText) activity.findViewById(R.id.et_basic_editor))
                .getText().toString());
    }
}
