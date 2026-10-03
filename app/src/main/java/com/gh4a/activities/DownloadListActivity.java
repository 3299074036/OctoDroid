package com.gh4a.activities;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment;

/**
 * 下载管理独立页：原抽屉「下载管理」入口使用频率低，
 * 下沉到设置页，由此 Activity 承载。
 */
public class DownloadListActivity extends FragmentContainerActivity {
    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.download_manager);
    }

    @Override
    protected Fragment onCreateFragment() {
        return DownloadListFragment.newInstance();
    }

    @Override
    protected Intent navigateUp() {
        return getToplevelActivityIntent();
    }
}
