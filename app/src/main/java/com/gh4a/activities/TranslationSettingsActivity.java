package com.gh4a.activities;

import android.os.Bundle;
import androidx.annotation.Nullable;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.fragment.TranslationSettingsFragment;

public class TranslationSettingsActivity extends BaseActivity {
    public static void start(android.content.Context context) {
        context.startActivity(new android.content.Intent(context, TranslationSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_container, new TranslationSettingsFragment())
                    .commit();
        }
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.translation_settings);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }
}
