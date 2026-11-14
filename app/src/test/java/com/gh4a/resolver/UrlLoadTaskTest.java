package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.RepositoryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class UrlLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat");

    /** Concrete UrlLoadTask whose Single is supplied by the test. */
    static class StubTask extends UrlLoadTask {
        private final Single<Optional<Intent>> mSingle;

        StubTask(FragmentActivity activity, Uri url, Single<Optional<Intent>> single) {
            super(activity, url);
            mSingle = single;
        }

        @Override
        protected Single<Optional<Intent>> getSingle() {
            return mSingle;
        }
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private static void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private Intent repoIntent(FragmentActivity activity) {
        return RepositoryActivity.makeIntent(activity, "octo", "cat");
    }

    @Test
    public void doInBackground_successReturnsIntent() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.of(repoIntent(activity))));
        Optional<Intent> result = task.doInBackground();
        assertTrue(result.isPresent());
        assertEquals(RepositoryActivity.class.getName(),
                result.get().getComponent().getClassName());
    }

    @Test
    public void doInBackground_errorReturnsEmpty() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL,
                Single.error(new RuntimeException("boom")));
        assertFalse(task.doInBackground().isPresent());
    }

    @Test
    public void doInBackground_emptySingleReturnsEmpty() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        assertFalse(task.doInBackground().isPresent());
    }

    @Test
    public void onPreExecute_showsProgressDialog() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.onPreExecute();
        idleMainLooper();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("progress"));
    }

    @Test
    public void onPreExecute_finishingActivity_showsNothing() {
        FragmentActivity activity = activity();
        activity.finish();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.onPreExecute();
        idleMainLooper();
        assertNull(activity.getSupportFragmentManager().findFragmentByTag("progress"));
    }

    @Test
    public void onPostExecute_presentIntent_startsItAndRunsCallback() {
        FragmentActivity activity = activity();
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        StubTask task = new StubTask(activity, URL, Single.just(Optional.of(repoIntent(activity))));
        task.setIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        task.setCompletionCallback(() -> callbackRan.set(true));
        task.onPostExecute(Optional.of(repoIntent(activity)));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue(callbackRan.get());
    }

    @Test
    public void onPostExecute_emptyResult_launchesBrowser() {
        FragmentActivity activity = activity();
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.setCompletionCallback(() -> callbackRan.set(true));
        task.onPostExecute(Optional.empty());
        // launchBrowser resolves no browser in Robolectric -> toast, no
        // started activity; the callback must still run.
        assertTrue(callbackRan.get());
    }

    @Test
    public void onPostExecute_emptyResultWithCustomTabFlag_doesNotCrash() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.setOpenUnresolvedUriInCustomTab(0xFF0000);
        task.onPostExecute(Optional.empty());
    }

    @Test
    public void onPostExecute_finishingActivity_doesNothing() {
        FragmentActivity activity = activity();
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        StubTask task = new StubTask(activity, URL, Single.just(Optional.of(repoIntent(activity))));
        task.setCompletionCallback(() -> callbackRan.set(true));
        activity.finish();
        task.onPostExecute(Optional.of(repoIntent(activity)));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertFalse(callbackRan.get());
    }

    @Test
    public void onPostExecute_dismissesProgressDialog() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.onPreExecute();
        idleMainLooper();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("progress"));
        task.onPostExecute(Optional.empty());
        idleMainLooper();
        assertNull(activity.getSupportFragmentManager().findFragmentByTag("progress"));
    }

    @Test
    public void progressDialogFragment_onCreateDialogWithoutActivity_returnsNull() {
        UrlLoadTask.ProgressDialogFragment f = new UrlLoadTask.ProgressDialogFragment();
        assertNull(f.onCreateDialog(null));
    }

    @Test
    public void progressDialogFragment_onCreateDialogWithActivity_returnsDialog() {
        FragmentActivity activity = activity();
        UrlLoadTask.ProgressDialogFragment f = new UrlLoadTask.ProgressDialogFragment();
        activity.getSupportFragmentManager().beginTransaction().add(f, "progress").commitNow();
        assertNotNull(f.onCreateDialog(new Bundle()));
    }

    @Test
    public void setters_doNotThrowBeforeExecute() {
        FragmentActivity activity = activity();
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.setIntentFlags(123);
        task.setCompletionCallback(() -> {});
        task.setOpenUnresolvedUriInCustomTab(0);
    }

    private FragmentActivity destroyedActivity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().destroy().get();
    }

    @Test
    public void onPreExecute_destroyedActivity_doesNotShowDialog() {
        FragmentActivity activity = destroyedActivity();
        assertTrue(activity.isDestroyed());
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.onPreExecute();
        idleMainLooper();
        assertNull(activity.getSupportFragmentManager().findFragmentByTag("progress"));
    }

    @Test
    public void onPostExecute_destroyedActivity_doesNotRunCallback() {
        FragmentActivity activity = destroyedActivity();
        assertTrue(activity.isDestroyed());
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        StubTask task = new StubTask(activity, URL,
                Single.just(Optional.of(repoIntent(activity))));
        task.setCompletionCallback(() -> callbackRan.set(true));
        task.onPostExecute(Optional.of(repoIntent(activity)));
        assertFalse(callbackRan.get());
    }

    @Test
    public void onPostExecute_dialogRemoved_doesNotDismissAndRunsCallback() {
        FragmentActivity activity = activity();
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        StubTask task = new StubTask(activity, URL, Single.just(Optional.empty()));
        task.setCompletionCallback(() -> callbackRan.set(true));
        task.onPreExecute();
        idleMainLooper();
        androidx.fragment.app.Fragment dialog =
                activity.getSupportFragmentManager().findFragmentByTag("progress");
        assertNotNull(dialog);
        activity.getSupportFragmentManager().beginTransaction().remove(dialog).commitNow();
        assertFalse(dialog.isAdded());
        task.onPostExecute(Optional.empty());
        assertTrue(callbackRan.get());
    }

}
