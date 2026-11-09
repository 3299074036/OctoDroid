package com.gh4a.resolver;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import android.util.Log;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.UiUtils;

import java.util.Optional;

import io.reactivex.Single;

public abstract class UrlLoadTask extends AsyncTask<Void, Void, Optional<Intent>> {
    protected final FragmentActivity mActivity;
    private ProgressDialogFragment mProgressDialog;
    private final Uri mUrlToResolve;
    private int mIntentFlags;
    private Runnable mCompletionCallback;
    private boolean mUseCustomTabForUnresolvedUri = false;
    private int mCustomTabHeaderColor;

    public UrlLoadTask(FragmentActivity activity, Uri urlToResolve) {
        super();
        mActivity = activity;
        mUrlToResolve = urlToResolve;
    }

    public void setIntentFlags(int flags) {
        mIntentFlags = flags;
    }

    public void setOpenUnresolvedUriInCustomTab(int headerColor) {
        mUseCustomTabForUnresolvedUri = true;
        mCustomTabHeaderColor = headerColor;
    }

    /**
     * Must be called BEFORE executing the task, otherwise the callback might not get executed.
     */
    public void setCompletionCallback(Runnable callback) {
        mCompletionCallback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // 136：show() 在 state-saved 后抛 IllegalStateException，Activity 已
        // 销毁时也不应再弹框；改 commitAllowingStateLoss
        if (mActivity.isFinishing() || mActivity.isDestroyed()) {
            return;
        }
        mProgressDialog = new ProgressDialogFragment();
        FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
        ft.add(mProgressDialog, "progress");
        ft.commitAllowingStateLoss();
    }

    @Override
    protected Optional<Intent> doInBackground(Void... params) {
        try {
            return getSingle().blockingGet();
        } catch (Exception e) {
            Log.e(Gh4Application.LOG_TAG, "Failure during intent resolving", e);
            return Optional.empty();
        }
    }

    @Override
    protected void onPostExecute(Optional<Intent> result) {
        // 136：只判 isFinishing() 不够，destroyed 的 Activity 调 startActivity
        // 会抛异常
        if (mActivity.isFinishing() || mActivity.isDestroyed()) {
            return;
        }

        if (result.isPresent()) {
            mActivity.startActivity(result.get().setFlags(mIntentFlags));
        } else if (mUseCustomTabForUnresolvedUri) {
            IntentUtils.openInCustomTabOrBrowser(mActivity, mUrlToResolve, mCustomTabHeaderColor);
        } else {
            IntentUtils.launchBrowser(mActivity, mUrlToResolve, mIntentFlags);
        }

        if (mProgressDialog != null && mProgressDialog.isAdded()) {
            mProgressDialog.dismissAllowingStateLoss();
        }

        if (mCompletionCallback != null) {
            mCompletionCallback.run();
        }
    }

    protected abstract Single<Optional<Intent>> getSingle();

    public static class ProgressDialogFragment extends DialogFragment {
        @androidx.annotation.Nullable
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // 136：getActivity() 可为 null，createProgressDialog 对 null 返回 null，
            // DialogFragment 容忍空 Dialog（仅不显示），不抛 NPE
            return UiUtils.createProgressDialog(getActivity(), R.string.loading_msg);
        }
    }
}
