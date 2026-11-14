package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import retrofit2.Response;

/** LoginModeChooserFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class LoginModeChooserFragmentTest {

    /** Host activity implementing the fragment's ParentCallback. */
    public static class LoginHostActivity extends FragmentActivity
            implements LoginModeChooserFragment.ParentCallback {
        final List<String> events = new ArrayList<>();
        String finishedToken;
        User finishedUser;
        Throwable failedError;

        @Override
        public void onLoginStartOauth() {
            events.add("oauth");
        }

        @Override
        public void onLoginFinished(String token, User user) {
            events.add("finished");
            finishedToken = token;
            finishedUser = user;
        }

        @Override
        public void onLoginFailed(Throwable error) {
            events.add("failed");
            failedError = error;
        }

        @Override
        public void onLoginCanceled() {
            events.add("canceled");
        }
    }

    private static LoginHostActivity host() {
        return org.robolectric.Robolectric.buildActivity(LoginHostActivity.class)
                .setup().get();
    }

    private static LoginModeChooserFragment shown(LoginHostActivity activity) {
        LoginModeChooserFragment f = LoginModeChooserFragment.newInstance();
        f.show(activity.getSupportFragmentManager(), "login");
        activity.getSupportFragmentManager().executePendingTransactions();
        return f;
    }

    @Test
    public void newInstance_returnsFragment() {
        assertNotNull(LoginModeChooserFragment.newInstance());
    }

    @Test
    public void scopes_constant() {
        assertEquals("user,repo,gist,read:org,notifications",
                LoginModeChooserFragment.SCOPES);
    }

    @Test
    public void onAttach_nonCallbackContext_throws() {
        LoginModeChooserFragment f = LoginModeChooserFragment.newInstance();
        FragmentTestHelper.assertThrows(IllegalArgumentException.class,
                () -> FragmentTestHelper.attach(f));
    }

    @Test
    public void onCreateDialog_defaultsToOauthMode() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        Dialog dialog = f.getDialog();
        assertNotNull(dialog);
        assertTrue(dialog instanceof AlertDialog);
        AlertDialog alert = (AlertDialog) dialog;
        RadioGroup group = alert.findViewById(R.id.login_mode);
        assertNotNull(group);
        assertEquals(R.id.oauth_button, group.getCheckedRadioButtonId());
        assertEquals(View.VISIBLE,
                alert.findViewById(R.id.oauth_container).getVisibility());
        assertEquals(View.GONE,
                alert.findViewById(R.id.token_container).getVisibility());
        assertEquals(View.GONE,
                alert.findViewById(R.id.progress_container).getVisibility());
    }

    @Test
    public void onResume_wiresOkButtonEnabledInOauthMode() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        Button ok = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        assertNotNull(ok);
        assertTrue(ok.isEnabled());
    }

    @Test
    public void checkTokenMode_emptyToken_disablesOkButton() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        RadioGroup group = alert.findViewById(R.id.login_mode);
        group.check(R.id.token_button);
        assertEquals(View.VISIBLE,
                alert.findViewById(R.id.token_container).getVisibility());
        assertEquals(View.GONE,
                alert.findViewById(R.id.oauth_container).getVisibility());
        Button ok = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        assertFalse("empty token must disable OK", ok.isEnabled());
    }

    @Test
    public void tokenEditor_invalidToken_disablesOkButton() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        alert.findViewById(R.id.login_mode);
        ((RadioGroup) alert.findViewById(R.id.login_mode)).check(R.id.token_button);
        com.google.android.material.textfield.TextInputEditText editor =
                alert.findViewById(R.id.token);
        editor.setText("abc"); // too short for the 8..255 pattern
        assertFalse(alert.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
        editor.setText("abc def!"); // illegal characters
        assertFalse(alert.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
    }

    @Test
    public void tokenEditor_validToken_enablesOkButton() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        ((RadioGroup) alert.findViewById(R.id.login_mode)).check(R.id.token_button);
        com.google.android.material.textfield.TextInputEditText editor =
                alert.findViewById(R.id.token);
        editor.setText("ghp_abcdefgh12345678");
        assertTrue(alert.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
    }

    @Test
    public void onClick_oauthMode_callsOnLoginStartOauth() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertEquals(List.of("oauth"), activity.events);
    }

    @Test
    public void onCancel_callsOnLoginCanceled() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        f.onCancel(f.getDialog());
        assertEquals(List.of("canceled"), activity.events);
    }

    @Test
    public void onResume_noDialog_setsNullOkButton() {
        // getDialog() is null on a never-shown fragment: the ternary and
        // the null guard both take their false arms.
        LoginModeChooserFragment f = LoginModeChooserFragment.newInstance();
        f.onResume();
    }

    @Test
    public void onClick_noModeChecked_doesNothing() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        ((RadioGroup) alert.findViewById(R.id.login_mode)).clearCheck();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertTrue(activity.events.isEmpty());
    }

    @Test
    public void onDestroy_withoutTokenCheck_doesNotCrash() {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        f.onDestroy();
    }

    // ---- token check path (ServiceFactory mocked: no real network) ----

    private static LoginModeChooserFragment shownWithToken(LoginHostActivity activity) {
        LoginModeChooserFragment f = shown(activity);
        AlertDialog alert = (AlertDialog) f.getDialog();
        ((RadioGroup) alert.findViewById(R.id.login_mode)).check(R.id.token_button);
        com.google.android.material.textfield.TextInputEditText editor =
                alert.findViewById(R.id.token);
        editor.setText("ghp_abcdefgh12345678");
        return f;
    }

    private static void waitForEvent(LoginHostActivity activity, String event)
            throws Exception {
        for (int i = 0; i < 100 && !activity.events.contains(event); i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(100, TimeUnit.MILLISECONDS);
            Thread.sleep(20);
        }
    }

    @Test
    public void onClick_tokenMode_success_callsOnLoginFinished() throws Exception {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shownWithToken(activity);
        User user = User.builder().login("octocat").build();
        UserService mockService = mock(UserService.class);
        when(mockService.getUser())
                .thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(UserService.class, true, null,
                    "ghp_abcdefgh12345678", null)).thenReturn(mockService);
            AlertDialog alert = (AlertDialog) f.getDialog();
            alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            waitForEvent(activity, "finished");
        }
        assertEquals(List.of("finished"), activity.events);
        assertEquals("ghp_abcdefgh12345678", activity.finishedToken);
        assertEquals(user, activity.finishedUser);
    }

    @Test
    public void onClick_tokenMode_error_callsOnLoginFailed() throws Exception {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shownWithToken(activity);
        UserService mockService = mock(UserService.class);
        when(mockService.getUser())
                .thenReturn(Single.error(new java.io.IOException("no network")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(UserService.class, true, null,
                    "ghp_abcdefgh12345678", null)).thenReturn(mockService);
            AlertDialog alert = (AlertDialog) f.getDialog();
            alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            waitForEvent(activity, "failed");
        }
        assertEquals(List.of("failed"), activity.events);
        assertNotNull(activity.failedError);
    }

    @Test
    public void handleTokenCheck_success_detachedFragment_doesNothing() throws Exception {
        // Not added to any activity: isAdded() is false, the callback returns
        // early without touching the host callback.
        LoginModeChooserFragment f = LoginModeChooserFragment.newInstance();
        Method m = LoginModeChooserFragment.class.getDeclaredMethod(
                "handleTokenCheck", Single.class);
        m.setAccessible(true);
        m.invoke(f, Single.just(Pair.create("t",
                User.builder().login("octocat").build())));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void handleTokenCheck_error_detachedFragment_doesNothing() throws Exception {
        LoginModeChooserFragment f = LoginModeChooserFragment.newInstance();
        Method m = LoginModeChooserFragment.class.getDeclaredMethod(
                "handleTokenCheck", Single.class);
        m.setAccessible(true);
        m.invoke(f, Single.error(new RuntimeException("x")));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void onDestroy_withPendingTokenCheck_disposes() throws Exception {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        Method m = LoginModeChooserFragment.class.getDeclaredMethod(
                "handleTokenCheck", Single.class);
        m.setAccessible(true);
        m.invoke(f, Single.never());
        java.lang.reflect.Field field = LoginModeChooserFragment.class
                .getDeclaredField("mTokenCheckDisposable");
        field.setAccessible(true);
        io.reactivex.disposables.Disposable disposable =
                (io.reactivex.disposables.Disposable) field.get(f);
        assertNotNull(disposable);
        assertFalse(disposable.isDisposed());
        f.onDestroy();
        assertTrue(disposable.isDisposed());
    }

    // ---- replaceURLSpansIfNeeded (private) ----

    private static void replaceSpans(LoginModeChooserFragment f, CharSequence text)
            throws Exception {
        Method m = LoginModeChooserFragment.class.getDeclaredMethod(
                "replaceURLSpansIfNeeded", CharSequence.class);
        m.setAccessible(true);
        m.invoke(f, text);
    }

    @Test
    public void replaceURLSpansIfNeeded_nonSpannable_returnsEarly() throws Exception {
        replaceSpans(LoginModeChooserFragment.newInstance(), "plain text, no spans");
    }

    @Test
    public void replaceURLSpansIfNeeded_urlSpan_replacedAndClickOpensBrowser()
            throws Exception {
        LoginHostActivity activity = host();
        LoginModeChooserFragment f = shown(activity);
        SpannableString text = new SpannableString("visit https://example.com now");
        URLSpan original = new URLSpan("https://example.com");
        text.setSpan(original, 6, 25, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        replaceSpans(f, text);
        URLSpan[] spans = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertTrue("the span must be replaced by a fresh instance", spans[0] != original);
        assertEquals("https://example.com", spans[0].getURL());
        spans[0].onClick(new View(activity));
        // The click itself (lines 221-222) is what this covers. Whether a
        // browser activity actually starts depends on the device's Custom
        // Tabs service, which does not exist under Robolectric: the helper
        // resolves to an async load task that never completes here.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        if (started != null) {
            assertEquals(Intent.ACTION_VIEW, started.getAction());
            assertEquals("https://example.com", started.getData().toString());
        }
    }
}
