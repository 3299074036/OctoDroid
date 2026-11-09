package com.gh4a.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.gh4a.R;
import com.gh4a.fragment.UserListsGraphQL;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 0.1.64 Star 分组共用对话框：官方列表归属（“加入列表”多选）与新建列表
 * （名称 + “设为私密”开关，默认公开）。StarMergedFragment（分组页多选
 * 移组、列表页长按单仓归属）与 RepositoryFragment（加星后入组）共用
 * 同一套 UI，与列表页长按已有的“加入列表”弹窗保持一致。
 */
public class UserListDialogHelper {

    /** 归属确认回调：选中的官方列表 id 集合（空集合 = 不进任何分组）。 */
    public interface OnAssignConfirm {
        void onConfirm(List<String> listIds);
    }

    /** 新建成功回调：新建的官方列表（调用方决定后续动作）。 */
    public interface OnListCreated {
        void onCreated(UserListsGraphQL.UserList list);
    }

    /**
     * “加入列表”多选对话框：复选框列出全部官方列表，底部左“新建列表…”
     * （onCreateNew）、右取消 / 确定。preCheckedIds 为预勾选的列表 id。
     * message 可为空（列表页长按的现有弹窗无副文案）。
     * <p>
     * 0.1.65：副文案与复选框列表必须同在自定义布局里。AlertDialog 的
     * setMessage() 与 setMultiChoiceItems() 只能显示其一（副文案会把
     * 列表顶掉，0.1.64 移组/入组弹窗因此一片空白），所以这里不用它们，
     * 而是 inflate dialog_assign_lists.xml：副文案 TextView（message
     * 为空时隐藏）+ 多选 ListView（行布局与 setMultiChoiceItems 内部
     * 相同：android.R.layout.simple_list_item_multiple_choice）。
     */
    public static void showAssignDialog(Context context,
            List<UserListsGraphQL.UserList> lists, Set<String> preCheckedIds,
            String message, Runnable onCreateNew, OnAssignConfirm onConfirm) {
        showAssignDialog(context, lists, preCheckedIds, message,
                onCreateNew, onConfirm, null);
    }

    /**
     * 同上，另带 onCancel：用户点“取消”/按返回/点框外关闭时回调。
     * <p>
     * 0.2.4（126 收尾）：取消必须进 ViewModel 状态机——调用方在
     * onCancel 里把 StarListsResult 标已消费，否则取消后立即旋转，
     * LiveData 重放未消费的结果，分组框会重弹。注意不用
     * OnDismissListener：旋转销毁 Activity 时对话框 dismiss 也会触发
     * 它，会把“框开着旋转应重弹”的 0.2.3 行为也吃掉；取消按钮与
     * onCancel 互斥（按钮关闭不走 onCancel），回调恰好一次。
     */
    public static void showAssignDialog(Context context,
            List<UserListsGraphQL.UserList> lists, Set<String> preCheckedIds,
            String message, Runnable onCreateNew, OnAssignConfirm onConfirm,
            Runnable onCancel) {
        List<String> names = new ArrayList<>();
        boolean[] checked = new boolean[lists.size()];
        for (int i = 0; i < lists.size(); i++) {
            names.add(lists.get(i).name);
            checked[i] = preCheckedIds != null && preCheckedIds.contains(lists.get(i).id);
        }
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_assign_lists, null);
        TextView subtitle = view.findViewById(R.id.tv_subtitle);
        if (message != null) {
            subtitle.setText(message);
        } else {
            subtitle.setVisibility(View.GONE);
        }
        ListView listView = view.findViewById(R.id.list);
        listView.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_multiple_choice, names));
        for (int i = 0; i < checked.length; i++) {
            listView.setItemChecked(i, checked[i]);
        }
        listView.setOnItemClickListener((parent, v, position, id) ->
                checked[position] = listView.isItemChecked(position));
        new AlertDialog.Builder(context)
                .setTitle(R.string.assign_official_title)
                .setView(view)
                .setOnCancelListener(dialog -> {
                    if (onCancel != null) onCancel.run();
                })
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    List<String> selectedIds = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) selectedIds.add(lists.get(i).id);
                    }
                    if (onConfirm != null) onConfirm.onConfirm(selectedIds);
                })
                .setNeutralButton(R.string.assign_official_create_new,
                        (dialog, which) -> {
                            if (onCreateNew != null) onCreateNew.run();
                        })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    /**
     * “新建列表”对话框：名称输入 + “设为私密”开关（默认公开）。
     * existingLists 用于重名校验；disposables 可为空（为空时不追踪订阅，
     * 回调里由调用方自行守卫 isAdded）。onCreated 在创建成功后回调。
     * 保存按钮在校验失败或创建失败时保持对话框打开（创建中禁用）。
     */
    public static void showCreateListDialog(Context context,
            List<UserListsGraphQL.UserList> existingLists,
            CompositeDisposable disposables, OnListCreated onCreated) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_create_list, null);
        EditText nameInput = view.findViewById(R.id.et_list_name);
        SwitchCompat privateSwitch = view.findViewById(R.id.sw_private);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.star_list_create_title)
                .setView(view)
                .setPositiveButton(R.string.star_list_save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.star_list_name_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existingLists != null) {
                    for (UserListsGraphQL.UserList list : existingLists) {
                        // B-L8：重名校验不分大小写（之前 "My List" 与 "my list"
                        // 会通过本地校验交由服务端裁决）。
                        if (name.equalsIgnoreCase(list.name)) {
                            Toast.makeText(context, R.string.star_list_name_dup,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                save.setEnabled(false);
                io.reactivex.disposables.Disposable sub =
                        UserListsGraphQL.createList(name, privateSwitch.isChecked())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(list -> {
                                    dialog.dismiss();
                                    if (onCreated != null) onCreated.onCreated(list);
                                }, error -> {
                                    save.setEnabled(true);
                                    Toast.makeText(context, R.string.star_list_create_failed,
                                            Toast.LENGTH_LONG).show();
                                });
                if (disposables != null) disposables.add(sub);
            });
        });
        dialog.show();
    }

    /** 新建确认回调：输入的名称与“设为私密”开关值（调用方自行发起创建请求）。 */
    public interface OnCreateNameConfirmed {
        void onConfirmed(String name, boolean isPrivate);
    }

    /**
     * “新建列表”对话框（只收集名称）：与 showCreateListDialog 同一套输入
     * UI 与校验（空名／重名不分大小写），确认后立即关闭并把名称与私密开关
     * 交回调用方；本身不发起任何网络请求。调用方把创建请求挂在 ViewModel
     * 里，旋转不中断（126）；原 showCreateListDialog 保持不动（其余调用方
     * 的“创建中禁用保存／失败重开”行为不变）。
     */
    public static void showCreateListNameDialog(Context context,
            List<UserListsGraphQL.UserList> existingLists,
            OnCreateNameConfirmed onConfirmed) {
        showCreateListNameDialog(context, existingLists, onConfirmed, null);
    }

    /**
     * 同上，另带 onCancel（126 收尾，语义同 showAssignDialog 的
     * onCancel：取消进 ViewModel 已消费态，旋转不再重弹）。
     */
    public static void showCreateListNameDialog(Context context,
            List<UserListsGraphQL.UserList> existingLists,
            OnCreateNameConfirmed onConfirmed, Runnable onCancel) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_create_list, null);
        EditText nameInput = view.findViewById(R.id.et_list_name);
        SwitchCompat privateSwitch = view.findViewById(R.id.sw_private);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.star_list_create_title)
                .setView(view)
                .setPositiveButton(R.string.star_list_save, null)
                .setNegativeButton(R.string.cancel, (d, which) -> {
                    if (onCancel != null) onCancel.run();
                })
                .create();
        // 126 收尾：返回/点框外取消也走 onCancel；旋转销毁不触发
        // onCancel，只触发 onDismiss，故此处不用 OnDismissListener。
        dialog.setOnCancelListener(d -> {
            if (onCancel != null) onCancel.run();
        });
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.star_list_name_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existingLists != null) {
                    for (UserListsGraphQL.UserList list : existingLists) {
                        // B-L8：重名校验不分大小写（与 showCreateListDialog 同款）。
                        if (name.equalsIgnoreCase(list.name)) {
                            Toast.makeText(context, R.string.star_list_name_dup,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                dialog.dismiss();
                if (onConfirmed != null) {
                    onConfirmed.onConfirmed(name, privateSwitch.isChecked());
                }
            });
        });
        dialog.show();
    }
}
