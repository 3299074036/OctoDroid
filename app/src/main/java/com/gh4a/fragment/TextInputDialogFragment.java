package com.gh4a.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.gh4a.R;

import java.util.List;

/**
 * 239：单行文本输入对话框（DialogFragment 版，原 TextInputDialogHelper）。
 * 旋转后由 FragmentManager 恢复重弹，输入内容经 onSaveInstanceState 保留；
 * 回调走父 Fragment 的 Callback 接口（不用捕获旧 Fragment 的 lambda），
 * 旋转重建后仍有效。外观文案与原助手一致。
 */
public class TextInputDialogFragment extends DialogFragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_INITIAL = "initial";
    private static final String ARG_PASSTHROUGH = "passthrough";
    private static final String STATE_TEXT = "text";

    public interface Callback {
        void onTextInputConfirmed(String tag, String passthrough, String initial, String text);
    }

    public static void show(FragmentManager fm, String tag, String title,
            String initial, String passthrough) {
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_INITIAL, initial);
        args.putString(ARG_PASSTHROUGH, passthrough);
        TextInputDialogFragment f = new TextInputDialogFragment();
        f.setArguments(args);
        f.showNow(fm, tag);
    }

    private EditText mInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        mInput = new EditText(requireContext());
        mInput.setId(android.R.id.edit);
        mInput.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        mInput.setPadding(padding, padding, padding, padding);
        String text = savedInstanceState != null
                ? savedInstanceState.getString(STATE_TEXT) : args.getString(ARG_INITIAL);
        if (text != null) {
            mInput.setText(text);
        }
        mInput.setSelectAllOnFocus(true);
        return new AlertDialog.Builder(requireContext())
                .setTitle(args.getString(ARG_TITLE))
                .setView(mInput)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    Callback cb = findCallback();
                    if (cb != null) {
                        cb.onTextInputConfirmed(getTag(),
                                args.getString(ARG_PASSTHROUGH),
                                args.getString(ARG_INITIAL),
                                mInput.getText().toString().trim());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }


    // 239：回调宿主不是 getParentFragment()（对话框挂在 Activity 的
    // FragmentManager 上，与 SingleChoiceDialogFragment 同款），而是在
    // Activity 内找实现 Callback 的 Fragment；两个宿主（Star 页／仓库页）
    // 分属不同 Activity，不会撞上。倒序找：后加入的优先。
    @Nullable
    private Callback findCallback() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return null;
        }
        List<Fragment> fragments =
                activity.getSupportFragmentManager().getFragments();
        for (int i = fragments.size() - 1; i >= 0; i--) {
            if (fragments.get(i) instanceof Callback) {
                return (Callback) fragments.get(i);
            }
        }
        return null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mInput != null) {
            outState.putString(STATE_TEXT, mInput.getText().toString());
        }
    }
}
