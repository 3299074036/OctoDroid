package com.gh4a.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 239：“新建列表”对话框（DialogFragment 版，原 UserListDialogHelper
 * .showCreateListNameDialog）。名称输入 + “设为私密”开关（默认公开），
 * 与 showCreateListDialog 同一套输入 UI 与校验（空名／重名不分大小写）；
 * 本身不发起网络请求，确认后把名称与私密开关交回调用方，创建请求挂在
 * 调用方的 ViewModel 里，旋转不中断。旋转后由 FragmentManager 恢复重弹，
 * 输入内容与开关状态经 onSaveInstanceState 保留；取消（返回／点框外／
 * 取消按钮）走 onCreateListNameCancelled——旋转销毁不触发 onCancel，
 * 与 126 的“取消进 ViewModel 已消费态”语义一致。外观文案不变。
 */
public class CreateListNameDialogFragment extends DialogFragment {
    private static final String ARG_EXISTING_NAMES = "existingNames";
    private static final String ARG_PASSTHROUGH = "passthrough";
    private static final String STATE_TEXT = "text";
    private static final String STATE_PRIVATE = "private";

    public interface Callback {
        void onCreateListNameConfirmed(String tag, String passthrough,
                String name, boolean isPrivate);
        void onCreateListNameCancelled(String tag, String passthrough);
    }

    public static void show(FragmentManager fm, String tag,
            List<String> existingNames, String passthrough) {
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_EXISTING_NAMES,
                existingNames != null ? new ArrayList<>(existingNames) : new ArrayList<>());
        args.putString(ARG_PASSTHROUGH, passthrough);
        CreateListNameDialogFragment f = new CreateListNameDialogFragment();
        f.setArguments(args);
        f.showNow(fm, tag);
    }

    private EditText mNameInput;
    private SwitchCompat mPrivateSwitch;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_list, null);
        mNameInput = view.findViewById(R.id.et_list_name);
        mPrivateSwitch = view.findViewById(R.id.sw_private);
        if (savedInstanceState != null) {
            String text = savedInstanceState.getString(STATE_TEXT);
            if (text != null) {
                mNameInput.setText(text);
            }
            mPrivateSwitch.setChecked(savedInstanceState.getBoolean(STATE_PRIVATE, false));
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.star_list_create_title)
                .setView(view)
                .setPositiveButton(R.string.star_list_save, null)
                .setNegativeButton(R.string.cancel, (d, which) -> notifyCancelled())
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String name = mNameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.star_list_name_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> existing = getArguments().getStringArrayList(ARG_EXISTING_NAMES);
                if (existing != null) {
                    for (String e : existing) {
                        // B-L8：重名校验不分大小写。
                        if (name.equalsIgnoreCase(e)) {
                            Toast.makeText(requireContext(), R.string.star_list_name_dup,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                dialog.dismiss();
                Callback cb = findCallback();
                if (cb != null) {
                    cb.onCreateListNameConfirmed(getTag(),
                            getArguments().getString(ARG_PASSTHROUGH),
                            name, mPrivateSwitch.isChecked());
                }
            });
        });
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        notifyCancelled();
    }

    private void notifyCancelled() {
        Callback cb = findCallback();
        if (cb != null) {
            cb.onCreateListNameCancelled(getTag(),
                    getArguments().getString(ARG_PASSTHROUGH));
        }
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
        if (mNameInput != null) {
            outState.putString(STATE_TEXT, mNameInput.getText().toString());
        }
        if (mPrivateSwitch != null) {
            outState.putBoolean(STATE_PRIVATE, mPrivateSwitch.isChecked());
        }
    }
}
