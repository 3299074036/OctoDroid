/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.gh4a.utils.ActivityResultHelpers;
import com.google.android.material.appbar.AppBarLayout;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import com.gh4a.BaseActivity;
import com.gh4a.BuildConfig;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.fragment.LoginModeChooserFragment;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.core.ServiceGenerator;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.RequestToken;
import com.meisolsson.githubsdk.service.OAuthService;
import com.meisolsson.githubsdk.service.users.UserService;

import io.reactivex.Single;

/**
 * The Github4Android activity.
 */
public class Github4AndroidActivity extends BaseActivity implements
        View.OnClickListener, LoginModeChooserFragment.ParentCallback {
    private static final String OAUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CODE = "code";
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_CALLBACK_URI = "redirect_uri";
    private static final String PARAM_STATE = "state";
    private static final String PARAM_CODE_CHALLENGE = "code_challenge";
    private static final String PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method";
    /** 未完成的 OAuth 流程的随机 state，存 prefs 防进程被杀丢失。 */
    private static final String PREF_OAUTH_STATE = "oauth_state_pending";
    /** 同一流程的 PKCE code_verifier，换 token 时提交。 */
    private static final String PREF_OAUTH_VERIFIER = "oauth_verifier_pending";

    private static final Uri CALLBACK_URI = Uri.parse("gh4a://oauth");

    private View mContent;
    private View mProgress;

    private final ActivityResultLauncher<Void> mSettingsLauncher = registerForActivityResult(
            new ActivityResultHelpers.StartSettingsContract(),
            themeChange -> {
                if (themeChange) {
                    Intent intent = new Intent(getIntent());
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Gh4Application app = Gh4Application.get();
        if (app.isAuthorized()) {
            if (!handleIntent(getIntent())) {
                goToToplevelActivity();
            }
            finish();
        } else {
            setContentView(R.layout.main);

            AppBarLayout abl = findViewById(R.id.header);
            abl.setEnabled(false);

            FrameLayout contentContainer = (FrameLayout) findViewById(R.id.content).getParent();
            contentContainer.setForeground(null);

            findViewById(R.id.login_button).setOnClickListener(this);
            mContent = findViewById(R.id.welcome_container);
            mProgress = findViewById(R.id.login_progress_container);

            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (!handleIntent(intent)) {
            super.onNewIntent(intent);
        }
    }

    private boolean handleIntent(Intent intent) {
        Uri data = intent.getData();
        // 常量在前比较：外部可传入无 scheme/host 的 data，直接 equals 会 NPE (L-1)
        if (data != null
                && CALLBACK_URI.getScheme().equals(data.getScheme())
                && CALLBACK_URI.getHost().equals(data.getHost())) {
            final String code = data.getQueryParameter(PARAM_CODE);
            if (code == null) {
                onLoginCanceled();
                return true;
            }
            // 校验 state：拒绝伪造的回调 intent（登录 CSRF，CR-2）。
            // 无 state 或对不上的一律视为取消，不拿 code 换 token。
            SharedPreferences prefs = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(this);
            String expectedState = prefs.getString(PREF_OAUTH_STATE, null);
            String verifier = prefs.getString(PREF_OAUTH_VERIFIER, null);
            prefs.edit().remove(PREF_OAUTH_STATE).remove(PREF_OAUTH_VERIFIER).apply();
            String actualState = data.getQueryParameter(PARAM_STATE);
            if (expectedState == null || !expectedState.equals(actualState)) {
                android.util.Log.w("Github4AndroidActivity",
                        "OAuth callback state mismatch, ignoring");
                onLoginCanceled();
                return true;
            }

            OAuthService service = ServiceGenerator.createAuthService();
            RequestToken.Builder tokenBuilder = RequestToken.builder()
                    .clientId(BuildConfig.CLIENT_ID)
                    .clientSecret(BuildConfig.CLIENT_SECRET)
                    .code(code);
            if (verifier != null) {
                tokenBuilder.codeVerifier(verifier);
            }
            RequestToken request = tokenBuilder.build();

            service.getToken(request)
                    .map(ApiHelpers::throwOnFailure)
                    .flatMap(token -> {
                        UserService userService = ServiceFactory.get(UserService.class, true,
                                null, token.accessToken(), null);
                        Single<User> userSingle = userService.getUser()
                                .map(ApiHelpers::throwOnFailure);
                        return Single.zip(Single.just(token), userSingle,
                                (t, user) -> Pair.create(t.accessToken(), user));
                    })
                    .compose(RxUtils::doInBackground)
                    .subscribe(pair -> onLoginFinished(pair.first, pair.second), this::handleLoadFailure);
            return true;
        }

        return false;
    }

    @Override
    protected int getLeftNavigationDrawerMenuResource() {
        return R.menu.home_nav_drawer;
    }

    @IdRes
    protected int getInitialLeftDrawerSelection(Menu menu) {
        menu.setGroupCheckable(R.id.navigation, false, false);
        menu.setGroupVisible(R.id.my_items, false);
        return super.getInitialLeftDrawerSelection(menu);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        super.onNavigationItemSelected(item);
        switch (item.getItemId()) {
            case R.id.settings:
                mSettingsLauncher.launch(null);
                return true;
            case R.id.search:
                startActivity(SearchActivity.makeIntent(this));
                return true;
        }
        return false;
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.login_button) {
            LoginModeChooserFragment.newInstance().show(getSupportFragmentManager(), "login");
            setProgressShown(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (mProgress.getVisibility() == View.VISIBLE) {
            setProgressShown(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onLoginStartOauth() {
        launchOauthLogin(this);
    }

    @Override
    public void onLoginFinished(String token, User user) {
        Gh4Application.get().addAccount(user, token);
        goToToplevelActivity();
        finish();
    }

    @Override
    public void onLoginFailed(Throwable error) {
        handleLoadFailure(error);
        setProgressShown(false);
    }

    @Override
    public void onLoginCanceled() {
        setProgressShown(false);
    }

    private void setProgressShown(boolean show) {
        mContent.setVisibility(show ? View.GONE : View.VISIBLE);
        mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static void launchOauthLogin(Activity activity) {
        // 随机 state 防登录 CSRF：回调时必须原样返回，否则拒绝 (CR-2)
        String state = java.util.UUID.randomUUID().toString();
        // PKCE (RFC 7636)：GitHub 2025-07 起支持 S256。verifier 只存本地，
        // 换 token 时提交；即使授权码被其他应用截获也无法换 token (CR-1 纵深)
        String verifier = generateCodeVerifier();
        String challenge = codeChallengeS256(verifier);
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putString(PREF_OAUTH_STATE, state)
                .putString(PREF_OAUTH_VERIFIER, verifier)
                .apply();
        Uri uri = Uri.parse(OAUTH_URL)
                .buildUpon()
                .appendQueryParameter(PARAM_CLIENT_ID, BuildConfig.CLIENT_ID)
                .appendQueryParameter(PARAM_SCOPE, LoginModeChooserFragment.SCOPES)
                .appendQueryParameter(PARAM_CALLBACK_URI, CALLBACK_URI.toString())
                .appendQueryParameter(PARAM_STATE, state)
                .appendQueryParameter(PARAM_CODE_CHALLENGE, challenge)
                .appendQueryParameter(PARAM_CODE_CHALLENGE_METHOD, "S256")
                .build();
        IntentUtils.openInCustomTabOrBrowser(activity, uri);
    }

    /** 生成 64 字符的 code_verifier（RFC 7636 unreserved 字符集）。 */
    private static String generateCodeVerifier() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(64);
        final String alphabet =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        for (int i = 0; i < 64; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** code_challenge = BASE64URL-ENCODE(SHA256(verifier))，无 padding。 */
    private static String codeChallengeS256(String verifier) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(
                    verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return android.util.Base64.encodeToString(digest,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING
                            | android.util.Base64.NO_WRAP);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
