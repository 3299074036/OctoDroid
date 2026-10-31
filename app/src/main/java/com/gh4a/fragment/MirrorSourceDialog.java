package com.gh4a.fragment;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;
import com.gh4a.utils.MirrorHelper;

import java.util.List;

/** 镜像源管理对话框的会话：列表数据、测速、行内增删改都在这里。
 * 行用 ListView 承载（0.0.42 验证过的 UI，名称单行显示正常）。
 * 编辑中不做任何 notifyDataSetChanged（探针只直改徽章），行内输入框
 * 实例保持稳定；键盘显式唤起。 */
public class MirrorSourceDialog {
    /** 读写当前镜像源预设值；set() 由调用方负责刷新其摘要/行。 */
    public interface PresetStore {
        String get();

        void set(String value);
    }

    /** 供宿主 Fragment 在销毁时主动关闭对话框、取消测速。 */
    public void dismiss() {
        cancel();
        if (mDialog != null) {
            try {
                mDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelSpeedTest() {
        if (mSpeedTestHandle != null) {
            mSpeedTestHandle.cancel();
            mSpeedTestHandle = null;
        }
    }

    private boolean isAlive() {
        if (!(mContext instanceof android.app.Activity)) {
            return true;
        }
        android.app.Activity activity = (android.app.Activity) mContext;
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    /** 按延迟从低到高排序：可用的按延迟升序，不可用的排最后。 */
    private static void sortByLatency(List<MirrorHelper.MirrorSpeedResult> items) {
        java.util.Collections.sort(items, (a, b) -> {
            if (a.isOk() != b.isOk()) {
                return a.isOk() ? -1 : 1;
            }
            return Long.compare(a.latencyMs, b.latencyMs);
        });
    }

    private final android.content.Context mContext;
    private final PresetStore mStore;
    private MirrorHelper.SpeedTestHandle mSpeedTestHandle;
    private final List<MirrorHelper.MirrorSpeedResult> mItems = new java.util.ArrayList<>();
    private final java.util.Set<String> mDoneUrls = new java.util.HashSet<>();
    private AlertDialog mDialog;
    // 0.1.20 F-28: 高度上限复核用的对话框内容视图（show() 里赋值）。
    private android.view.View mDialogContent;
    private android.widget.TextView mSubtitle;
    private android.widget.ListView mListView;
    private MirrorManageAdapter mAdapter;
    private String mCurrentValue;
    private boolean mUserPicked;
    private boolean mTestDone;
    // 底部固定输入条（0.0.47）：随对话框只创建一次，不在列表行里，
    // 不受测速逐行刷新、排序重排影响。mEditingOldUrl 为 null 表示新增。
    private android.view.View mFooterBar;
    private android.widget.EditText mUrlInput;
    private boolean mEditingIsNew;
    private String mEditingOldUrl;

    public MirrorSourceDialog(android.content.Context context, PresetStore store) {
        mContext = context;
        mStore = store;
        mCurrentValue = store != null ? store.get() : null;
    }

    void show() {
        // 新对话框打开前取消上一次未完成的测速，避免回调叠加
        cancelSpeedTest();
        mAdapter = new MirrorManageAdapter();
        android.view.View content = android.view.LayoutInflater.from(mContext)
                .inflate(R.layout.dialog_mirror_manage, null);
        mDialogContent = content;
        mSubtitle = content.findViewById(R.id.subtitle);
        mSubtitle.setText(R.string.mirror_manage_testing);

        mListView = content.findViewById(R.id.list);
        mListView.setAdapter(mAdapter);
        // 点行即选用（输入条展开时忽略，避免误触丢掉未保存的输入）
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (!isFooterOpen()) {
                pick(position);
            }
        });

        // 底部固定输入条：随对话框只创建一次
        mFooterBar = content.findViewById(R.id.footer_bar);
        mUrlInput = content.findViewById(R.id.url);
        content.findViewById(R.id.confirm).setOnClickListener(v -> saveFooter());
        content.findViewById(R.id.cancel).setOnClickListener(v -> cancelFooter());
        mUrlInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveFooter();
                return true;
            }
            return false;
        });

        content.findViewById(R.id.add).setOnClickListener(v -> startAdding());
        content.findViewById(R.id.close).setOnClickListener(v -> {
            cancel();
            mDialog.dismiss();
        });

        mDialog = new AlertDialog.Builder(mContext)
                .setView(content)
                .create();
        mDialog.setOnCancelListener(d -> cancel());
        mDialog.show();
        // 0.1.23: crash breadcrumb counter (see CrashBreadcrumb for coverage).
        com.gh4a.utils.CrashBreadcrumb.noteDialogShown();
        mDialog.setOnDismissListener(d -> com.gh4a.utils.CrashBreadcrumb.noteDialogDismissed());
        if (mDialog.getWindow() != null) {
            mDialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            // 0.1.15：只改弹框尺寸，不动布局与行样式。宽 = 屏幕 90% 且上限 480dp；
            // 高：内容低于 70% 屏高时 wrap content，超出则封顶 70%、列表内部滚动，
            // 标题、底部输入条与 +新增/取消行始终留在框内。
            android.view.Window window = mDialog.getWindow();
            android.util.DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            if (window != null) {
                android.view.WindowManager.LayoutParams lp =
                        new android.view.WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                int maxWidthPx = (int) (480 * dm.density + 0.5f);
                lp.width = Math.min((int) (dm.widthPixels * 0.90f), maxWidthPx);
                window.setAttributes(lp);
            }
            recheckHeightCap();
        }
        refreshAndRetest();
    }

    /**
     * 0.1.20 F-28: 高度上限复核（70% 屏高封顶）——不再只在 show() 的单次 post
     * 里快照。测速收尾（行数/排序变化）与底部输入条显隐后各重跑一次，
     * 小屏 + 镜像多时对话框不再再度超高。
     */
    private void recheckHeightCap() {
        if (mDialog == null || mDialogContent == null) {
            return;
        }
        mDialogContent.post(() -> {
            android.view.Window w = mDialog != null ? mDialog.getWindow() : null;
            if (w == null || mDialogContent == null) {
                return;
            }
            android.util.DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            int cap = (int) (dm.heightPixels * 0.70f);
            if (mDialogContent.getHeight() > cap) {
                android.view.WindowManager.LayoutParams p =
                        new android.view.WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.height = cap;
                w.setAttributes(p);
            }
        });
    }

    /** 点行选用：测速结果仅供参考，随时可点，不用等测完。 */
    private void pick(int position) {
        MirrorHelper.MirrorSpeedResult r = mItems.get(position);
        mUserPicked = true;
        mCurrentValue = r.url;
        if (mStore != null) {
            mStore.set(r.url);
        }
        android.widget.Toast.makeText(mContext,
                mContext.getString(R.string.mirror_speed_selected, r.name),
                android.widget.Toast.LENGTH_SHORT).show();
        cancel();
        mDialog.dismiss();
    }

    /** 增删改后：重建列表、当前选中失效则回退、重新测速。 */
    private void refreshAndRetest() {
        cancelSpeedTest();
        mTestDone = false;
        collapseFooter();
        mItems.clear();
        mDoneUrls.clear();
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(mContext);
        // 上次缓存的延迟：打开即显示并按缓存排序，不用干等；后台再实时刷新
        java.util.Map<String, Long> cached =
                MirrorHelper.getCachedSpeedResults(mContext);
        for (MirrorHelper.MirrorSpeedResult t : targets) {
            Long c = cached.get(t.url);
            mItems.add(c != null
                    ? new MirrorHelper.MirrorSpeedResult(t.name, t.url, t.presetValue, c)
                    : t);
        }
        if (!cached.isEmpty()) {
            sortByLatency(mItems);
        }
        // 当前选中的地址若已被删掉：优先回退默认地址，否则取第一个，否则置空
        if (!containsUrl(mCurrentValue)) {
            mCurrentValue = "";
            for (MirrorHelper.MirrorSpeedResult r : mItems) {
                if (r.url.equalsIgnoreCase(MirrorHelper.DEFAULT_PRESET)) {
                    mCurrentValue = r.url;
                    break;
                }
            }
            if (mCurrentValue.isEmpty() && !mItems.isEmpty()) {
                mCurrentValue = mItems.get(0).url;
            }
            if (mStore != null) {
                mStore.set(mCurrentValue);
            }
        }
        mAdapter.notifyDataSetChanged();
        if (mItems.isEmpty()) {
            // 空列表也不关对话框：用户可点“+ 新增镜像源”重新添加
            mSubtitle.setText(R.string.mirror_empty_hint);
            return;
        }
        mSubtitle.setText(R.string.mirror_manage_testing);
        startSpeedTest();
    }

    private boolean containsUrl(String url) {
        if (url == null) {
            return false;
        }
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (url.equalsIgnoreCase(r.url)) {
                return true;
            }
        }
        return false;
    }

    private void startSpeedTest() {
        final List<MirrorHelper.MirrorSpeedResult> probes =
                new java.util.ArrayList<>(mItems);
        mSpeedTestHandle = MirrorHelper.testAllMirrorsSpeed(mContext, probes,
                new MirrorHelper.SpeedTestCallback() {
                    @Override
                    public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
                        if (!isAlive()) {
                            return;
                        }
                        mDoneUrls.add(result.url);
                        for (int i = 0; i < mItems.size(); i++) {
                            if (mItems.get(i).url.equals(result.url)) {
                                mItems.set(i, result);
                                break;
                            }
                        }
                        // 只直改该行的延迟徽章，不 notify（保住编辑中的输入框）
                        updateRowBadge(result);
                    }

                    @Override
                    public void onAllComplete() {
                        mSpeedTestHandle = null;
                        if (!isAlive()) {
                            return;
                        }
                        // 存缓存，下次打开直接显示
                        MirrorHelper.saveSpeedTestResults(mContext, mItems);
                        mTestDone = true;
                        // 输入条在列表之外：排序重排碰不到它，无需推迟收尾
                        finishTest();
                    }
                });
    }

    /** 测速收尾：按实测延迟重排（低的在上，不可用的在下）；没手动选过就自动选中延迟最低的。 */
    private void finishTest() {
        sortByLatency(mItems);
        MirrorHelper.MirrorSpeedResult fastest = null;
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (r.isOk()) {
                fastest = r;
                break;
            }
        }
        if (fastest != null && !mUserPicked) {
            mCurrentValue = fastest.url;
            if (mStore != null) {
                mStore.set(fastest.url);
            }
            mSubtitle.setText(R.string.mirror_manage_auto_selected);
        } else {
            mSubtitle.setText("");
        }
        mAdapter.notifyDataSetChanged();
        // 0.1.20 F-28: 测速收尾重排后复核高度上限。
        recheckHeightCap();
    }

    /** 探针完成：找到可见行只刷新延迟徽章，不重建。 */
    private void updateRowBadge(MirrorHelper.MirrorSpeedResult result) {
        if (mListView == null) {
            return;
        }
        for (int i = 0; i < mItems.size(); i++) {
            if (!mItems.get(i).url.equals(result.url)) {
                continue;
            }
            int visible = i - mListView.getFirstVisiblePosition();
            if (visible < 0 || visible >= mListView.getChildCount()) {
                return;
            }
            android.view.View row = mListView.getChildAt(visible);
            mAdapter.bindBadge(row, result);
            return;
        }
    }

    private void cancel() {
        cancelSpeedTest();
    }

    /** 点“+ 新增镜像源”：底部输入条展开，输入框清空。 */
    private void startAdding() {
        if (isFooterOpen()) {
            return;
        }
        mEditingIsNew = true;
        mEditingOldUrl = null;
        mUrlInput.setText("");
        showFooter();
    }

    /** 点行内编辑图标：底部输入条展开并预填该行地址（按地址定位，不怕重排）。 */
    private void startEditing(String url) {
        if (isFooterOpen()) {
            return;
        }
        mEditingIsNew = false;
        mEditingOldUrl = url;
        mUrlInput.setText(url);
        mUrlInput.setSelection(mUrlInput.getText().length());
        showFooter();
    }

    private void showFooter() {
        mFooterBar.setVisibility(android.view.View.VISIBLE);
        // 0.1.20 F-28: 输入条展开后复核高度上限（post 到布局完成后执行）。
        recheckHeightCap();
        // 附着后要焦点并显式唤起键盘（直接调可能因未附着而静默失败）
        mUrlInput.post(() -> {
            mUrlInput.requestFocus();
            showKeyboard(mUrlInput);
        });
    }

    private boolean isFooterOpen() {
        return mFooterBar != null
                && mFooterBar.getVisibility() == android.view.View.VISIBLE;
    }

    private void collapseFooter() {
        if (mFooterBar != null) {
            mFooterBar.setVisibility(android.view.View.GONE);
        }
        mEditingOldUrl = null;
        // 0.1.20 F-28: 输入条收起后复核高度上限。
        recheckHeightCap();
    }

    /** 底部输入条保存：新增或修改地址。 */
    private void saveFooter() {
        String url = MirrorHelper.normalizeUrl(mUrlInput.getText().toString());
        if (url.isEmpty()
                || !url.regionMatches(true, 0, "https://", 0, 8)) {
            android.widget.Toast.makeText(mContext,
                    R.string.mirror_custom_url_invalid,
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (!mEditingIsNew && url.equalsIgnoreCase(mEditingOldUrl)) {
            // 地址没改：直接收起输入条
            cancelFooter();
            return;
        }
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (url.equalsIgnoreCase(r.url)) {
                android.widget.Toast.makeText(mContext,
                        R.string.mirror_url_exists,
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
        }
        hideKeyboard();
        collapseFooter();
        if (mEditingIsNew) {
            MirrorHelper.addMirrorUrl(mContext, url);
            android.widget.Toast.makeText(mContext, R.string.mirror_added,
                    android.widget.Toast.LENGTH_SHORT).show();
        } else {
            MirrorHelper.updateMirrorUrl(mContext, mEditingOldUrl, url);
            android.widget.Toast.makeText(mContext, R.string.mirror_updated,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        refreshAndRetest();
    }

    /** 收起底部输入条（点 ✕ 或地址未改）。 */
    private void cancelFooter() {
        hideKeyboard();
        collapseFooter();
    }

    /** 删除行：弹确认框（按地址删，确认框弹出期间列表重排也不怕删错）。 */
    private void deleteAt(int position) {
        if (isFooterOpen()) {
            return;
        }
        final String url = mItems.get(position).url;
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.mirror_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    MirrorHelper.removeMirrorUrl(mContext, url);
                    android.widget.Toast.makeText(mContext, R.string.mirror_deleted,
                            android.widget.Toast.LENGTH_SHORT).show();
                    refreshAndRetest();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void hideKeyboard() {
        android.view.View focus = mDialog.getCurrentFocus();
        if (focus != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            mContext.getSystemService(
                                    android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    /** 显式唤起键盘（flag=0 表示用户主动操作，输入法不会忽略）。 */
    private void showKeyboard(android.widget.EditText input) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        mContext.getSystemService(
                                android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, 0);
        }
    }

    /** 列表适配器：只有普通行；输入条在列表之外，不参与回收。 */
    private class MirrorManageAdapter extends android.widget.BaseAdapter {

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public android.view.View getView(int position,
                android.view.View convertView, android.view.ViewGroup parent) {
            android.view.View v = convertView;
            if (v == null) {
                v = android.view.LayoutInflater.from(mContext).inflate(
                        R.layout.dialog_mirror_source_item, parent, false);
            }
            bindNormalRow(v, position);
            return v;
        }

        private void bindNormalRow(android.view.View v, final int position) {
            MirrorHelper.MirrorSpeedResult r = mItems.get(position);
            android.widget.TextView name = v.findViewById(R.id.name);
            android.widget.RadioButton radio = v.findViewById(R.id.radio);
            name.setText(r.name);
            boolean selected = r.url.equalsIgnoreCase(mCurrentValue);
            radio.setChecked(selected);
            v.setBackground(mContext.getDrawable(selected
                    ? R.drawable.mirror_row_selected : R.drawable.mirror_row_card));
            bindBadge(v, r);
            v.findViewById(R.id.edit).setOnClickListener(
                    eb -> startEditing(r.url));
            v.findViewById(R.id.delete).setOnClickListener(
                    db -> deleteAt(position));
        }

        /** 绑定/刷新延迟徽章（颜色随延迟区间变化）。 */
        void bindBadge(android.view.View v, MirrorHelper.MirrorSpeedResult r) {
            android.widget.TextView status = v.findViewById(R.id.status);
            android.content.res.Resources res = mContext.getResources();
            int badgeColor;
            if (!mDoneUrls.contains(r.url) && r.latencyMs < 0) {
                // 无缓存且本次还没测完
                status.setText(R.string.mirror_source_testing);
                badgeColor = res.getColor(android.R.color.darker_gray);
            } else if (r.isOk()) {
                // 本次实测或上次缓存的延迟：按区间着色（<500绿，<1000橙，否则红）
                status.setText(mContext.getString(
                        R.string.mirror_source_latency, r.latencyMs));
                badgeColor = r.latencyMs < 500
                        ? res.getColor(R.color.primary)
                        : r.latencyMs < 1000
                                ? res.getColor(R.color.accent_orange)
                                : res.getColor(R.color.accent_red);
            } else {
                status.setText(R.string.mirror_source_unavailable);
                badgeColor = res.getColor(android.R.color.darker_gray);
            }
            android.graphics.drawable.Drawable badge = mContext
                    .getDrawable(R.drawable.mirror_latency_badge).mutate();
            badge.setTint(badgeColor);
            status.setBackground(badge);
            status.setTextColor(res.getColor(android.R.color.white));
        }
    }
}
