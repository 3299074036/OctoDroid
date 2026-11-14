package com.gh4a.activities;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.home.HomeActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.security.MessageDigest;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class Github4AndroidActivityTest {
    @Test
    public void generateCodeVerifier_length64AndCharset() {
        String verifier = (String) callStatic(Github4AndroidActivity.class,
                "generateCodeVerifier", new Class<?>[]{}, new Object[]{});
        assertEquals(64, verifier.length());
        assertTrue(verifier.matches("[A-Za-z0-9\\-._~]{64}"));
        String other = (String) callStatic(Github4AndroidActivity.class,
                "generateCodeVerifier", new Class<?>[]{}, new Object[]{});
        assertFalse(verifier.equals(other));
    }

    @Test
    public void codeChallengeS256_matchesSha256Base64Url() throws Exception {
        String verifier = "test-verifier-123";
        String challenge = (String) callStatic(Github4AndroidActivity.class,
                "codeChallengeS256", new Class<?>[]{String.class}, new Object[]{verifier});
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String expected = android.util.Base64.encodeToString(digest,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING
                        | android.util.Base64.NO_WRAP);
        assertEquals(expected, challenge);
        assertFalse(challenge.contains("+"));
        assertFalse(challenge.contains("/"));
        assertFalse(challenge.contains("="));
    }

    @Test
    public void launch_unauthorized_showsLoginLayout() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        assertFalse(activity.isFinishing());
        assertTrue(activity.findViewById(R.id.login_button) != null);
        assertTrue(activity.findViewById(R.id.welcome_container) != null);
        assertEquals(0, activity.getLeftNavigationDrawerMenuResource());
        assertFalse(activity.canSwipeToRefresh());
    }

    @Test
    public void launch_unauthorized_homeAsUpDisabled() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);
        assertNull(toolbar.getNavigationIcon());
    }

    @Test
    public void onClick_loginButton_showsProgressAndChooser() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.findViewById(R.id.login_button).performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertEquals(View.GONE, activity.findViewById(R.id.welcome_container).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.login_progress_container).getVisibility());
        assertTrue(activity.getSupportFragmentManager()
                .findFragmentByTag("login") != null);
    }

    @Test
    public void onBackPressed_withProgressVisible_hidesProgress() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.findViewById(R.id.login_button).performClick();
        activity.onBackPressed();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.login_progress_container).getVisibility());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onBackPressed_withoutProgress_finishes() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onLoginCanceled_hidesProgress() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.findViewById(R.id.login_button).performClick();
        activity.onLoginCanceled();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
    }

    @Test
    public void handleIntent_nonCallback_returnsFalse() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        Intent plain = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"));
        assertFalse((boolean) call(activity, "handleIntent",
                new Class<?>[]{Intent.class}, new Object[]{plain}));
        Intent noData = new Intent(appContext(), Github4AndroidActivity.class);
        assertFalse((boolean) call(activity, "handleIntent",
                new Class<?>[]{Intent.class}, new Object[]{noData}));
    }

    @Test
    public void handleIntent_callbackWithoutCode_cancelsAndReturnsTrue() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        Intent cb = new Intent(Intent.ACTION_VIEW, Uri.parse("gh4a://oauth?state=x"));
        assertTrue((boolean) call(activity, "handleIntent",
                new Class<?>[]{Intent.class}, new Object[]{cb}));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
    }

    @Test
    public void handleIntent_callbackWithCodeButNoStoredState_cancelsAndReturnsTrue() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        Intent cb = new Intent(Intent.ACTION_VIEW,
                Uri.parse("gh4a://oauth?code=abc&state=mismatch"));
        assertTrue((boolean) call(activity, "handleIntent",
                new Class<?>[]{Intent.class}, new Object[]{cb}));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
    }

    @Test
    public void launchOauthLogin_storesStateAndVerifier() {
        // No browser/custom-tabs provider resolves in the Robolectric
        // environment, so the OAuth URL open falls through to the
        // "no browser" toast; the persisted state/verifier are the
        // verifiable part of the launch.
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        Github4AndroidActivity.launchOauthLogin(activity);
        android.content.SharedPreferences prefs = Gh4Application.securePrefs(activity);
        String state = prefs.getString("oauth_state_pending", null);
        String verifier = prefs.getString("oauth_verifier_pending", null);
        assertTrue(state != null && !state.isEmpty());
        assertEquals(64, verifier.length());
        assertEquals(appContext().getString(R.string.no_browser_found),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onNavigationItemSelected_search_startsSearch() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        // settings item lives in the navigation drawer menu; use a stub item id path:
        android.view.MenuItem item = menu.add(0, R.id.search, 0, "search");
        assertTrue(activity.onNavigationItemSelected(item));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(SearchActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void getInitialLeftDrawerSelection_returnsZero() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertEquals(0, activity.getInitialLeftDrawerSelection(menu));
    }

    // ---- helpers ----

    private void authorize() {
        Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder().login("tester").id(1L).build(), "tok");
        if (!Gh4Application.get().isAuthorized()) {
            Gh4Application.get().addAccount(
                    com.meisolsson.githubsdk.model.User.builder().login("tester").id(1L).build(),
                    "tok");
        }
    }

    private Intent callbackIntent(String code, String state) {
        String uri = "gh4a://oauth?code=" + code + "&state=" + state;
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }

    private void storeSecureState(Github4AndroidActivity activity, String state, String verifier) {
        android.content.SharedPreferences.Editor e =
                Gh4Application.securePrefs(activity).edit();
        if (state != null) {
            e.putString("oauth_state_pending", state);
        }
        if (verifier != null) {
            e.putString("oauth_verifier_pending", verifier);
        }
        e.apply();
    }

    private void storeLegacyState(Github4AndroidActivity activity, String state, String verifier) {
        android.content.SharedPreferences.Editor e = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(activity).edit();
        if (state != null) {
            e.putString("oauth_state_pending", state);
        }
        if (verifier != null) {
            e.putString("oauth_verifier_pending", verifier);
        }
        e.apply();
    }

    private boolean callHandleIntent(Github4AndroidActivity activity, Intent intent) {
        return (boolean) call(activity, "handleIntent",
                new Class<?>[]{Intent.class}, new Object[]{intent});
    }

    private static void assertThrowsNpe(Runnable r, String why) {
        try {
            r.run();
        } catch (Throwable t) {
            Throwable c = t;
            while (c != null) {
                if (c instanceof NullPointerException) {
                    return;
                }
                c = c.getCause();
            }
            throw new AssertionError("expected NullPointerException (" + why + "), got " + t, t);
        }
        throw new AssertionError("expected NullPointerException (" + why + "), nothing thrown");
    }

    private void dispatchSettingsResult(Github4AndroidActivity activity, int resultCode,
            Intent data) {
        androidx.activity.result.ActivityResultRegistry registry =
                activity.getActivityResultRegistry();
        java.util.Map<String, Integer> keyToRc =
                (java.util.Map<String, Integer>) get(registry, "keyToRc");
        java.util.Map<String, Object> keyToCallback =
                (java.util.Map<String, Object>) get(registry, "keyToCallback");
        int rc = -1;
        for (java.util.Map.Entry<String, Object> e : keyToCallback.entrySet()) {
            Object contract = call(e.getValue(), "getContract",
                    new Class<?>[]{}, new Object[]{});
            if (contract instanceof com.gh4a.utils.ActivityResultHelpers.StartSettingsContract) {
                rc = keyToRc.get(e.getKey());
                break;
            }
        }
        assertTrue("settings launcher request code not found", rc >= 0);
        assertTrue(registry.dispatchResult(rc, resultCode, data));
    }

    @Test
    public void launch_authorized_plainIntent_goesHomeAndFinishes() {
        authorize();
        assertTrue(Gh4Application.get().isAuthorized());
        // create() only: a real device never calls onStart/onResume after
        // finish() in onCreate, but Robolectric's controller would run them
        // and BaseActivity.onStart throws for the never-inflated content.
        Github4AndroidActivity activity = controller(Github4AndroidActivity.class,
                new Intent(appContext(), Github4AndroidActivity.class)).create().get();
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(HomeActivity.class.getName(), next.getComponent().getClassName());
        assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    }

    @Test
    public void launch_authorized_callbackWithoutCode_throwsNpe_A1_6() {
        // A1-6: in the authorized branch onCreate never inflates the login
        // layout, so mContent/mProgress stay null; a callback that reaches
        // onLoginCanceled() (no code / state mismatch / no verifier) calls
        // setProgressShown(), which dereferences the null mContent.
        authorize();
        assertTrue(Gh4Application.get().isAuthorized());
        assertThrowsNpe(() -> launch(Github4AndroidActivity.class,
                new Intent(Intent.ACTION_VIEW, Uri.parse("gh4a://oauth?state=x"))),
                "authorized onCreate with a code-less OAuth callback");
    }

    @Test
    public void onNewIntent_nonCallback_delegatesToSuper() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/")));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onNewIntent_callbackWithoutCode_cancels() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.findViewById(R.id.login_button).performClick();
        activity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("gh4a://oauth?state=x")));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void handleIntent_callbackHostMismatch_returnsFalse() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        assertFalse(callHandleIntent(activity,
                new Intent(Intent.ACTION_VIEW, Uri.parse("gh4a://other?code=x&state=y"))));
    }

    @Test
    public void handleIntent_stateInSecureVerifierInLegacy_stateMismatch_cancels() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        storeSecureState(activity, "s1", null);
        storeLegacyState(activity, null, "v1");
        assertTrue(callHandleIntent(activity, callbackIntent("abc", "WRONG")));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
    }

    @Test
    public void handleIntent_verifierInSecureStateInLegacy_stateMismatch_cancels() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        storeSecureState(activity, null, "v1");
        storeLegacyState(activity, "s1", null);
        assertTrue(callHandleIntent(activity, callbackIntent("abc", "WRONG")));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
    }

    @Test
    public void handleIntent_stateMatchesButNoVerifier_cancels() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        storeSecureState(activity, "s1", null);
        assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
        assertNull(Gh4Application.securePrefs(activity).getString("oauth_state_pending", null));
    }

    @Test
    public void handleIntent_fullExchange_success_showsPlaintextDialog() throws Exception {
        com.meisolsson.githubsdk.model.GitHubToken token =
                mock(com.meisolsson.githubsdk.model.GitHubToken.class);
        when(token.accessToken()).thenReturn("tok123");
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        // observeOn(main) inside the mocked Single: the outer chain's
        // subscribeOn(io) would otherwise make the flatMap (and its
        // ServiceFactory.get call) run on the Rx IO thread, where the
        // MockedStatic does not apply.
        when(oauth.getToken(any())).thenReturn(Single.just(Response.success(token))
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        com.meisolsson.githubsdk.service.users.UserService userService =
                mock(com.meisolsson.githubsdk.service.users.UserService.class);
        when(userService.getUser()).thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class);
             MockedStatic<ServiceFactory> mockedFactory = Mockito.mockStatic(ServiceFactory.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            mockedFactory.when(() -> ServiceFactory.get(
                            eq(com.meisolsson.githubsdk.service.users.UserService.class),
                            eq(true), isNull(), eq("tok123"), isNull()))
                    .thenReturn(userService);
            Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            waitFor(() -> !org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().isEmpty(),
                    10000);
            assertTrue(Gh4Application.get().isAuthorized());
            assertEquals(1,
                    org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().size());
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().get(0);
            assertEquals(appContext().getString(R.string.login_token_plaintext_message),
                    ((android.widget.TextView) dialog.findViewById(android.R.id.message))
                            .getText().toString());
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            // AlertDialog buttons dispatch through a Handler; pump the
            // main looper before asserting the click's effect.
            idleMain(200);
            assertTrue(activity.isFinishing());
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(HomeActivity.class.getName(), next.getComponent().getClassName());
        }
    }

    @Test
    public void handleIntent_fullExchange_error_showsErrorView() throws Exception {
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        when(oauth.getToken(any()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue(Boolean.TRUE.equals(get(activity, "mErrorShown")));
            assertFalse(Gh4Application.get().isAuthorized());
        }
    }

    @Test
    public void handleIntent_exchange_successAfterFinish_ignored() throws Exception {
        com.meisolsson.githubsdk.model.GitHubToken token =
                mock(com.meisolsson.githubsdk.model.GitHubToken.class);
        when(token.accessToken()).thenReturn("tok123");
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        io.reactivex.subjects.SingleSubject<Response<com.meisolsson.githubsdk.model.GitHubToken>> subject =
                io.reactivex.subjects.SingleSubject.create();
        // observeOn(main): the subject may replay on the Rx IO thread (where
        // the static mock does not apply); shift the emission to main so the
        // flatMap's ServiceFactory.get really hits the mock.
        when(oauth.getToken(any())).thenReturn(subject.observeOn(
                io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        com.meisolsson.githubsdk.service.users.UserService userService =
                mock(com.meisolsson.githubsdk.service.users.UserService.class);
        when(userService.getUser()).thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class);
             MockedStatic<ServiceFactory> mockedFactory = Mockito.mockStatic(ServiceFactory.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            mockedFactory.when(() -> ServiceFactory.get(
                            eq(com.meisolsson.githubsdk.service.users.UserService.class),
                            eq(true), isNull(), eq("tok123"), isNull()))
                    .thenReturn(userService);
            Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            activity.finish();
            assertTrue(activity.isFinishing());
            subject.onSuccess(Response.success(token));
            idleMain(500);
            assertFalse(Gh4Application.get().isAuthorized());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void handleIntent_exchange_errorAfterFinish_ignored() throws Exception {
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        io.reactivex.subjects.SingleSubject<Response<com.meisolsson.githubsdk.model.GitHubToken>> subject =
                io.reactivex.subjects.SingleSubject.create();
        // observeOn(main): the subject may replay on the Rx IO thread (where
        // the static mock does not apply); shift the emission to main so the
        // flatMap's ServiceFactory.get really hits the mock.
        when(oauth.getToken(any())).thenReturn(subject.observeOn(
                io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            activity.finish();
            subject.onError(new java.io.IOException("boom"));
            idleMain(500);
            assertFalse(Boolean.TRUE.equals(get(activity, "mErrorShown")));
        }
    }

    @Test
    public void onNavigationItemSelected_settings_launchesSettings() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        android.view.MenuItem item = menu.add(0, R.id.settings, 0, "settings");
        assertTrue(activity.onNavigationItemSelected(item));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void onNavigationItemSelected_unknown_returnsFalse() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        android.view.MenuItem item = menu.add(0, 9999, 0, "unknown");
        assertFalse(activity.onNavigationItemSelected(item));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void settingsLauncher_themeChanged_restartsAndFinishes() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onNavigationItemSelected(menu.add(0, R.id.settings, 0, "settings"));
        // The launcher must have been launched first: doDispatch only calls
        // the callback for keys in launchedKeys, otherwise the result is
        // parked as pending and the callback never runs.
        Intent data = new Intent();
        data.putExtra(SettingsActivity.RESULT_EXTRA_THEME_CHANGED, true);
        dispatchSettingsResult(activity, android.app.Activity.RESULT_OK, data);
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Github4AndroidActivity.class.getName(), next.getComponent().getClassName());
        assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    }

    @Test
    public void settingsLauncher_noChange_doesNothing() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onNavigationItemSelected(menu.add(0, R.id.settings, 0, "settings"));
        dispatchSettingsResult(activity, android.app.Activity.RESULT_CANCELED, null);
        assertFalse(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void onClick_nonLoginButton_doesNothing() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onClick(activity.findViewById(R.id.welcome_container));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.welcome_container).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.login_progress_container).getVisibility());
        assertNull(activity.getSupportFragmentManager().findFragmentByTag("login"));
    }

    @Test
    public void onLoginStartOauth_storesStateAndVerifier() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onLoginStartOauth();
        android.content.SharedPreferences prefs = Gh4Application.securePrefs(activity);
        assertTrue(prefs.getString("oauth_state_pending", null) != null);
        assertEquals(64, prefs.getString("oauth_verifier_pending", "").length());
    }

    @Test
    public void onLoginFailed_withError_showsErrorView() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onLoginFailed(new java.io.IOException("boom"));
        assertTrue(Boolean.TRUE.equals(get(activity, "mErrorShown")));
        assertEquals(View.GONE, activity.findViewById(R.id.login_progress_container).getVisibility());
    }

    @Test
    public void onLoginFailed_nullError_doesNotCrash() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        activity.onLoginFailed(null);
        assertTrue(Boolean.TRUE.equals(get(activity, "mErrorShown")));
        assertEquals(View.GONE, activity.findViewById(R.id.login_progress_container).getVisibility());
    }

    @Test
    public void onLoginFinished_direct_plaintextFallback_showsDialog() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        // Initialize secure prefs first: the fallback flag is only set when
        // getSecurePrefs() runs, and nothing has touched it yet in this test.
        Gh4Application.securePrefs(activity);
        assertTrue(Gh4Application.get().isSecurePrefsPlainFallback());
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        activity.onLoginFinished("tok123", user);
        assertTrue(Gh4Application.get().isAuthorized());
        assertEquals(1,
                org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().size());
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog)
                        org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().get(0);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        // AlertDialog buttons dispatch through a Handler; pump the
        // main looper before asserting the click's effect.
        idleMain(200);
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(HomeActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void onLoginFinished_direct_noFallback_goesHome() {
        Github4AndroidActivity activity = launch(Github4AndroidActivity.class);
        // Initialize secure prefs first, then flip the flag: addAccount()
        // inside onLoginFinished would otherwise re-run the init and reset
        // the flag to the plaintext fallback.
        Gh4Application.securePrefs(activity);
        set(Gh4Application.get(), "mSecurePrefsPlainFallback", false);
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        activity.onLoginFinished("tok123", user);
        assertTrue(Gh4Application.get().isAuthorized());
        assertTrue(org.robolectric.shadows.ShadowAlertDialog.getShownDialogs().isEmpty());
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(HomeActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void launch_authorized_callbackValidExchange_skipsHome() throws Exception {
        authorize();
        assertTrue(Gh4Application.get().isAuthorized());
        com.meisolsson.githubsdk.model.GitHubToken token =
                mock(com.meisolsson.githubsdk.model.GitHubToken.class);
        when(token.accessToken()).thenReturn("tok123");
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        when(oauth.getToken(any())).thenReturn(Single.just(Response.success(token))
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        com.meisolsson.githubsdk.service.users.UserService userService =
                mock(com.meisolsson.githubsdk.service.users.UserService.class);
        when(userService.getUser()).thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class);
             MockedStatic<ServiceFactory> mockedFactory = Mockito.mockStatic(ServiceFactory.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            mockedFactory.when(() -> ServiceFactory.get(
                            eq(com.meisolsson.githubsdk.service.users.UserService.class),
                            eq(true), isNull(), eq("tok123"), isNull()))
                    .thenReturn(userService);
            Gh4Application.securePrefs(appContext()).edit()
                    .putString("oauth_state_pending", "s1")
                    .putString("oauth_verifier_pending", "v1").apply();
            // create() only (same reason as the authorized plain launch test).
            Github4AndroidActivity activity = controller(Github4AndroidActivity.class,
                    callbackIntent("abc", "s1")).create().get();
            // handleIntent returned true, so goToToplevelActivity was skipped.
            assertTrue(activity.isFinishing());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            idleMain(500);
        }
    }

    @Test
    public void handleIntent_exchange_successAfterDestroy_ignored() throws Exception {
        com.meisolsson.githubsdk.model.GitHubToken token =
                mock(com.meisolsson.githubsdk.model.GitHubToken.class);
        when(token.accessToken()).thenReturn("tok123");
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("alice").id(7L).build();
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        io.reactivex.subjects.SingleSubject<Response<com.meisolsson.githubsdk.model.GitHubToken>> subject =
                io.reactivex.subjects.SingleSubject.create();
        when(oauth.getToken(any())).thenReturn(subject.observeOn(
                io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        com.meisolsson.githubsdk.service.users.UserService userService =
                mock(com.meisolsson.githubsdk.service.users.UserService.class);
        when(userService.getUser()).thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class);
             MockedStatic<ServiceFactory> mockedFactory = Mockito.mockStatic(ServiceFactory.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            mockedFactory.when(() -> ServiceFactory.get(
                            eq(com.meisolsson.githubsdk.service.users.UserService.class),
                            eq(true), isNull(), eq("tok123"), isNull()))
                    .thenReturn(userService);
            org.robolectric.android.controller.ActivityController<Github4AndroidActivity> c =
                    controller(Github4AndroidActivity.class, null);
            Github4AndroidActivity activity = c.create().start().resume().get();
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            // The exchange chain subscribes on the IO thread (doInBackground);
            // wait until it is attached, otherwise the emission below can
            // land before any observer exists and the consumer never runs.
            waitFor(() -> subject.hasObservers(), 10000);
            c.destroy();
            assertTrue(activity.isDestroyed());
            assertFalse(activity.isFinishing());
            subject.onSuccess(Response.success(token));
            idleMain(500);
            assertFalse(Gh4Application.get().isAuthorized());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void handleIntent_exchange_errorAfterDestroy_ignored() throws Exception {
        com.meisolsson.githubsdk.service.OAuthService oauth =
                mock(com.meisolsson.githubsdk.service.OAuthService.class);
        io.reactivex.subjects.SingleSubject<Response<com.meisolsson.githubsdk.model.GitHubToken>> subject =
                io.reactivex.subjects.SingleSubject.create();
        when(oauth.getToken(any())).thenReturn(subject.observeOn(
                io.reactivex.android.schedulers.AndroidSchedulers.mainThread()));
        try (MockedStatic<com.meisolsson.githubsdk.core.ServiceGenerator> mockedGen =
                     Mockito.mockStatic(com.meisolsson.githubsdk.core.ServiceGenerator.class)) {
            mockedGen.when(() -> com.meisolsson.githubsdk.core.ServiceGenerator.createAuthService())
                    .thenReturn(oauth);
            org.robolectric.android.controller.ActivityController<Github4AndroidActivity> c =
                    controller(Github4AndroidActivity.class, null);
            Github4AndroidActivity activity = c.create().start().resume().get();
            storeSecureState(activity, "s1", "v1");
            assertTrue(callHandleIntent(activity, callbackIntent("abc", "s1")));
            // See the success test: wait for the IO-thread subscription so
            // the error consumer is guaranteed to run after destroy.
            waitFor(() -> subject.hasObservers(), 10000);
            c.destroy();
            assertTrue(activity.isDestroyed());
            subject.onError(new java.io.IOException("boom"));
            idleMain(500);
            assertFalse(Boolean.TRUE.equals(get(activity, "mErrorShown")));
        }
    }
}
