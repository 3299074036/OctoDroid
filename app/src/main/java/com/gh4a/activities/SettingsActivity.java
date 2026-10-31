package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

public class SettingsActivity extends BaseActivity implements
        SettingsFragment.OnStateChangeListener {
    public static final String RESULT_EXTRA_THEME_CHANGED = "theme_changed";
    public static final String RESULT_EXTRA_FONT_SCALE_CHANGED = "font_scale_changed";
    private static final String STATE_KEY_RESULT = "result";

    private Intent mResultIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            mResultIntent = new Intent();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_container, new SettingsFragment())
                    .commit();
        } else {
            mResultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT);
        }

        setResult(RESULT_OK, mResultIntent);
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.settings);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        // we don't have any loaded content
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_KEY_RESULT, mResultIntent);
    }

    @Override
    public void onThemeChanged() {
        mResultIntent.putExtra(RESULT_EXTRA_THEME_CHANGED, true);
        // 0.1.21 Item 1: 主题色/语言均为 ListPreference 对话框，change 回调在
        // 对话框窗口仍在位时触发；与下方 onFontScaleChanged 一致，post 再 recreate。
        // 0.1.22：执行时再判存活，已销毁/正在结束就跳过。
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isDestroyed() && !isFinishing()) {
                recreate();
            }
        });
    }

    @Override
    public void onFontScaleChanged() {
        // Flag the change for the caller so it restarts the whole task stack
        // (same as a theme change). onPreferenceChange() runs BEFORE the new
        // value is persisted, so the recreate is posted to run after
        // persistence; otherwise the recreated activity could still read the
        // old font scale.
        mResultIntent.putExtra(RESULT_EXTRA_FONT_SCALE_CHANGED, true);
        // 0.1.22：执行时再判存活，已销毁/正在结束就跳过。
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isDestroyed() && !isFinishing()) {
                recreate();
            }
        });
    }
}
