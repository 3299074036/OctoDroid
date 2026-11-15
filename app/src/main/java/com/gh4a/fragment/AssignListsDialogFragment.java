package com.gh4a.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 239：“加入列表”多选对话框（DialogFragment 版，原 UserListDialogHelper
 * .showAssignDialog）。复选框列出全部官方列表，底部左“新建列表…”、右取消／
 * 确定；副文案与复选框列表同在 dialog_assign_lists.xml 自定义布局里
 * （AlertDialog 的 setMessage() 与 setMultiChoiceItems() 只能显示其一）。
 * 旋转后由 FragmentManager 恢复重弹，勾选状态经 onSaveInstanceState 保留；
 * 取消（返回／点框外／取消按钮）走 onAssignListsCancelled——旋转销毁不触发
 * onCancel，与 126 的“取消进 ViewModel 已消费态”语义一致。外观文案不变。
 */
public class AssignListsDialogFragment extends DialogFragment {
    private static final String ARG_NAMES = "names";
    private static final String ARG_IDS = "ids";
    private static final String ARG_PRE_CHECKED = "preChecked";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_REPO = "repoFullName";
    private static final String STATE_CHECKED = "checked";

    public interface Callback {
        void onAssignListsConfirmed(String tag, String repoFullName, List<String> selectedIds);
        void onAssignListsCreateNew(String tag, String repoFullName);
        void onAssignListsCancelled(String tag, String repoFullName);
    }

    public static void show(FragmentManager fm, String tag, List<String> names,
            List<String> ids, Set<String> preCheckedIds, String message, String repoFullName) {
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_NAMES, new ArrayList<>(names));
        args.putStringArrayList(ARG_IDS, new ArrayList<>(ids));
        args.putStringArrayList(ARG_PRE_CHECKED,
                preCheckedIds != null ? new ArrayList<>(preCheckedIds) : new ArrayList<>());
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_REPO, repoFullName);
        AssignListsDialogFragment f = new AssignListsDialogFragment();
        f.setArguments(args);
        f.showNow(fm, tag);
    }

    private boolean[] mChecked;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        ArrayList<String> names = args.getStringArrayList(ARG_NAMES);
        ArrayList<String> ids = args.getStringArrayList(ARG_IDS);
        if (savedInstanceState != null) {
            mChecked = savedInstanceState.getBooleanArray(STATE_CHECKED);
        }
        if (mChecked == null || mChecked.length != names.size()) {
            mChecked = new boolean[names.size()];
            ArrayList<String> pre = args.getStringArrayList(ARG_PRE_CHECKED);
            if (pre != null) {
                for (int i = 0; i < ids.size(); i++) {
                    mChecked[i] = pre.contains(ids.get(i));
                }
            }
        }
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_assign_lists, null);
        TextView subtitle = view.findViewById(R.id.tv_subtitle);
        String message = args.getString(ARG_MESSAGE);
        if (message != null) {
            subtitle.setText(message);
        } else {
            subtitle.setVisibility(View.GONE);
        }
        ListView listView = view.findViewById(R.id.list);
        listView.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_multiple_choice, names));
        for (int i = 0; i < mChecked.length; i++) {
            listView.setItemChecked(i, mChecked[i]);
        }
        listView.setOnItemClickListener((parent, v, position, id) ->
                mChecked[position] = listView.isItemChecked(position));
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assign_official_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    List<String> selectedIds = new ArrayList<>();
                    for (int i = 0; i < mChecked.length; i++) {
                        if (mChecked[i]) {
                            selectedIds.add(ids.get(i));
                        }
                    }
                    Callback cb = findCallback();
                    if (cb != null) {
                        cb.onAssignListsConfirmed(getTag(),
                                args.getString(ARG_REPO), selectedIds);
                    }
                })
                .setNeutralButton(R.string.assign_official_create_new, (dialog, which) -> {
                    Callback cb = findCallback();
                    if (cb != null) {
                        cb.onAssignListsCreateNew(getTag(),
                                args.getString(ARG_REPO));
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> notifyCancelled())
                .create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        notifyCancelled();
    }

    private void notifyCancelled() {
        Callback cb = findCallback();
        if (cb != null) {
            Bundle args = getArguments();
            cb.onAssignListsCancelled(getTag(), args.getString(ARG_REPO));
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
        if (mChecked != null) {
            outState.putBooleanArray(STATE_CHECKED, mChecked);
        }
    }
}
