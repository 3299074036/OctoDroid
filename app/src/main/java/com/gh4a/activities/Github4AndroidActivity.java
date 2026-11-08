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

        // 0.1.19：登录页左上角什么都不留。BaseActivity.onCreate 会按默认
        // 规则把 ActionBar 的向上按钮打开（登录页 intent 既不是 NO_HISTORY
        // 也不是新任务 intent），抽屉下架后这个返回箭头仍在；这里在
        // super 之后直接关掉它并清空导航图标。其他页的返回箭头不动。
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
        }

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
            // S-L6：state/verifier 存加密存储（之前是默认 prefs 明文）。
            // 读不到时回落一次默认 prefs（升级前已发起的登录流程），两处都删。
            SharedPreferences prefs = Gh4Application.securePrefs(this);
            String expectedState = prefs.getString(PREF_OAUTH_STATE, null);
            String verifier = prefs.getString(PREF_OAUTH_VERIFIER, null);
            prefs.edit().remove(PREF_OAUTH_STATE).remove(PREF_OAUTH_VERIFIER).apply();
            if (expectedState == null || verifier == null) {
                SharedPreferences legacyPrefs = androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(this);
                if (expectedState == null) {
                    expectedState = legacyPrefs.getString(PREF_OAUTH_STATE, null);
                }
                if (verifier == null) {
                    verifier = legacyPrefs.getString(PREF_OAUTH_VERIFIER, null);
                }
                legacyPrefs.edit().remove(PREF_OAUTH_STATE)
                        .remove(PREF_OAUTH_VERIFIER).apply();
            }
            String actualState = data.getQueryParameter(PARAM_STATE);
            if (expectedState == null || !expectedState.equals(actualState)) {
                // 0.1.38：回调校验埋点（不记 state 与 code）。
                com.gh4a.utils.DiagTrace.log(this, "OAUTH_CALLBACK", "state_mismatch");
                android.util.Log.w("Github4AndroidActivity",
                        "OAuth callback state mismatch, ignoring");
                onLoginCanceled();
                return true;
            }
            // SEC-1 (hardening, secret retained — see below): PKCE verifier
            // is mandatory. Never fall back to a secret-only exchange: a
            // missing verifier means this callback did not come from our own
            // launchOauthLogin() flow, and exchanging the code on the secret
            // alone would give an intercepted code its full value.
            if (verifier == null) {
                // 0.1.38：回调校验埋点（不记 verifier 与 code）。
                com.gh4a.utils.DiagTrace.log(this, "OAUTH_CALLBACK", "no_verifier");
                android.util.Log.w("Github4AndroidActivity",
                        "OAuth callback without PKCE verifier, ignoring");
                onLoginCanceled();
                return true;
            }

            OAuthService service = ServiceGenerator.createAuthService();
            // SEC-1 DEFERRED: CLIENT_SECRET is still sent here. The exchange
            // does send code_verifier (above), but GitHub's web-application
            // flow docs still list client_secret as Required for
            // POST /login/oauth/access_token, and this OAuth-app registration
            // has no "public client" mode that waives it — so a secret-less
            // exchange cannot be assumed to succeed, and removing the secret
            // risks breaking login outright. The secret stays in BuildConfig
            // (gitignored client.properties, never logged) until a secret-less
            // exchange is proven against this registration on device.
            RequestToken.Builder tokenBuilder = RequestToken.builder()
                    .clientId(BuildConfig.CLIENT_ID)
                    .clientSecret(BuildConfig.CLIENT_SECRET)
                    .code(code)
                    .codeVerifier(verifier);
            RequestToken request = tokenBuilder.build();

            // 0.1.38：换 token 开始埋点（不记 code 与 verifier）。
            com.gh4a.utils.DiagTrace.log(this, "OAUTH_EXCHANGE", "start");
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
        // 0.1.18：登录页下架左侧边栏。返回 0 后 BaseActivity.setupNavigationDrawer
        // 会把左抽屉锁定关闭且不显示汉堡图标，登录页不再有左抽屉入口；
        // 登录流程与 OAuth 处理不受影响（onNavigationItemSelected 只是不再触发）。
        return 0;
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
        // 0.1.38：登录链埋点（只记事件，不记 token 与用户信息）。
        com.gh4a.utils.DiagTrace.log(this, "OAUTH_LOGIN", "finished");
        Gh4Application.get().addAccount(user, token);
        // SEC-5: if encrypted storage was unavailable, the token just landed
        // in the plaintext fallback file — say so visibly at login instead
        // of letting the user believe it is encrypted.
        if (Gh4Application.get().isSecurePrefsPlainFallback()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("注意：令牌未加密存储")
                    .setMessage("本设备加密存储不可用，本次登录的 GitHub 令牌已存入未加密文件。"
                            + "换一台加密存储正常的设备重新登录后，该明文文件会自动迁移并删除。")
                    .setPositiveButton("知道了", (d, w) -> {
                        goToToplevelActivity();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        goToToplevelActivity();
        finish();
    }

    @Override
    public void onLoginFailed(Throwable error) {
        // 0.1.38：登录失败埋点（只记异常类名，不记消息与内容）。
        com.gh4a.utils.DiagTrace.log(this, "OAUTH_LOGIN",
                "failed " + (error != null ? error.getClass().getSimpleName() : "?"));
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
        // S-L6：state/verifier 存加密存储（之前是默认 prefs 明文；单次有效、
        // 回调后立即删除，但加密存储与 token 同款，无额外成本）。
        Gh4Application.securePrefs(activity)
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
