package com.gh4a.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.MirrorHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 镜像源独立页（0.1.62，设计稿 mirror-source-page.html 落地）：
 * 替代 MirrorSourceDialog 弹框，从“我”页镜像源行与设置页镜像源项进入。
 * 列表数据、测速、行内增删改与对话框同一套逻辑；另加自动选择开关与批量删除。
 * 点行即选后留在页内（对话框是选完即关）；单选框已去掉，选中行右侧打勾标“当前”。
 */
public class MirrorSourceActivity extends BaseActivity {
    private static final int MENU_BATCH = 1;

    // P9：旋转保存/恢复用的 Bundle 键（编辑态、批量态、上轮测速结果）。
    private static final String KEY_BATCH_MODE = "mirror_batch_mode";
    private static final String KEY_CHECKED = "mirror_checked";
    private static final String KEY_EDITING_IS_NEW = "mirror_editing_is_new";
    private static final String KEY_EDITING_OLD_URL = "mirror_editing_old_url";
    private static final String KEY_TEST_DONE = "mirror_test_done";
    private static final String KEY_DONE_URLS = "mirror_done_urls";
    private static final String KEY_ITEM_URLS = "mirror_item_urls";
    private static final String KEY_ITEM_LATENCIES = "mirror_item_latencies";
    private static final String KEY_ADD_CARD_OPEN = "mirror_add_card_open";
    private static final String KEY_ADD_CARD_TEXT = "mirror_add_card_text";

    private SharedPreferences mPrefs;
    private MirrorHelper.SpeedTestHandle mSpeedTestHandle;
    // 0.2.4（94）：测速轮次计数——旧轮回调只能清掉自己那轮的句柄（见
    // startSpeedTest 里 onAllComplete 的比对），新一轮的句柄不再被旧回调置空。
    private int mSpeedTestGeneration;
    private final List<MirrorHelper.MirrorSpeedResult> mItems = new ArrayList<>();
    private final Set<String> mDoneUrls = new HashSet<>();
    private MirrorManageAdapter mAdapter;
    private String mCurrentValue;
    private boolean mUserPicked;
    private boolean mTestDone;
    private boolean mBatchMode;
    private final Set<String> mChecked = new HashSet<>();
    private View mAddCard;
    private EditText mUrlInput;
    private TextView mAddTitle;
    private boolean mEditingIsNew;
    private String mEditingOldUrl;
    private TextView mAutoSubtitle;
    private TextView mCurrentLine;
    private TextView mStatsTotal;
    private TextView mStatsOk;
    private TextView mStatsTimeout;
    private TextView mStatsUpdated;
    private SwitchCompat mAutoSwitch;
    private boolean mIgnoreSwitchEvent;
    private ListView mListView;
    private TextView mEmptyHint;
    private View mBatchBar;
    private TextView mBatchCount;
    private Button mBatchDeleteBtn;

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.mirror_preset);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_source);

        mPrefs = getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE);
        mCurrentValue = mPrefs.getString(SettingsFragment.KEY_MIRROR_PRESET,
                MirrorHelper.DEFAULT_PRESET);
        mAdapter = new MirrorManageAdapter();

        mAutoSubtitle = findViewById(R.id.auto_subtitle);
        mCurrentLine = findViewById(R.id.current_line);
        mStatsTotal = findViewById(R.id.stats_total);
        mStatsOk = findViewById(R.id.stats_ok);
        mStatsTimeout = findViewById(R.id.stats_timeout);
        mStatsUpdated = findViewById(R.id.stats_updated);
        mAutoSwitch = findViewById(R.id.auto_switch);
        // 恢复上次的自动选择开关（默认开）；手动选过则恢复为关，mUserPicked 同步恢复。
        boolean autoSelect = mPrefs.getBoolean(
                SettingsFragment.KEY_MIRROR_AUTO_SELECT, true);
        mUserPicked = !autoSelect;
        mAutoSwitch.setChecked(autoSelect);
        mAutoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mIgnoreSwitchEvent) {
                return;
            }
            setAuto(isChecked);
        });

        mListView = findViewById(R.id.list);
        mListView.setAdapter(mAdapter);
        // 点行即选（批量模式下点行改勾选）；输入卡片展开时忽略，避免误触丢掉未保存的输入
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (isAddCardOpen()) {
                return;
            }
            if (mBatchMode) {
                toggleChecked(mItems.get(position).url);
            } else {
                pick(mItems.get(position));
            }
        });

        mEmptyHint = findViewById(R.id.empty_hint);

        // 新增/编辑输入卡片：平时收起，点“+ 新增”或行内铅笔时展开
        mAddCard = findViewById(R.id.add_card);
        mAddTitle = findViewById(R.id.add_title);
        mUrlInput = findViewById(R.id.url);
        findViewById(R.id.add).setOnClickListener(v -> startAdding());
        findViewById(R.id.add_cancel).setOnClickListener(v -> cancelAddCard());
        findViewById(R.id.add_save).setOnClickListener(v -> saveAddCard());
        mUrlInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAddCard();
                return true;
            }
            return false;
        });

        // 批量删除底部栏
        mBatchBar = findViewById(R.id.batch_bar);
        mBatchCount = findViewById(R.id.batch_count);
        mBatchDeleteBtn = findViewById(R.id.batch_delete);
        findViewById(R.id.batch_cancel).setOnClickListener(v -> exitBatchMode());
        mBatchDeleteBtn.setOnClickListener(v -> askBatchDelete());

        // P9：旋转后复用上轮测速结果 + 编辑态，不无条件重跑全部探测
        // （原先 onCreate 无条件 refreshAndRetest，71 条镜像每转一次
        // 就重新并行探测一轮，编辑中的输入卡片/批量勾选也全丢）。
        if (savedInstanceState != null) {
            restoreFromSavedState(savedInstanceState);
        } else {
            refreshAndRetest();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // P9：存编辑态（新增/修改 + 旧地址 + 输入框文字）、批量态
        // （批量模式 + 已勾选）、上轮测速结果（每条延迟 + 已测完集合
        // + 是否整轮测完）。mCurrentValue/mUserPicked 已落 prefs，不用存。
        outState.putBoolean(KEY_BATCH_MODE, mBatchMode);
        outState.putStringArrayList(KEY_CHECKED, new ArrayList<>(mChecked));
        outState.putBoolean(KEY_EDITING_IS_NEW, mEditingIsNew);
        outState.putString(KEY_EDITING_OLD_URL, mEditingOldUrl);
        outState.putBoolean(KEY_TEST_DONE, mTestDone);
        outState.putStringArrayList(KEY_DONE_URLS, new ArrayList<>(mDoneUrls));
        ArrayList<String> urls = new ArrayList<>();
        long[] latencies = new long[mItems.size()];
        for (int i = 0; i < mItems.size(); i++) {
            urls.add(mItems.get(i).url);
            latencies[i] = mItems.get(i).latencyMs;
        }
        outState.putStringArrayList(KEY_ITEM_URLS, urls);
        outState.putLongArray(KEY_ITEM_LATENCIES, latencies);
        outState.putBoolean(KEY_ADD_CARD_OPEN, isAddCardOpen());
        outState.putString(KEY_ADD_CARD_TEXT,
                mUrlInput != null ? mUrlInput.getText().toString() : "");
    }

    /**
     * P9：旋转后恢复——列表按保存的延迟重建（先取保存值，缺失再取
     * 持久化测速缓存），测完的上轮不再重跑探测；编辑卡片与批量勾选
     * 原样恢复。注意：不走 refreshAndRetest（它会 collapseAddCard
     * 并清掉编辑态），也不碰 94 号的测速句柄守卫行。
     */
    private void restoreFromSavedState(Bundle state) {
        mBatchMode = state.getBoolean(KEY_BATCH_MODE, false);
        ArrayList<String> checked = state.getStringArrayList(KEY_CHECKED);
        mChecked.clear();
        if (checked != null) {
            mChecked.addAll(checked);
        }
        mEditingIsNew = state.getBoolean(KEY_EDITING_IS_NEW, true);
        mEditingOldUrl = state.getString(KEY_EDITING_OLD_URL);
        mTestDone = state.getBoolean(KEY_TEST_DONE, false);
        ArrayList<String> done = state.getStringArrayList(KEY_DONE_URLS);
        mDoneUrls.clear();
        if (done != null) {
            mDoneUrls.addAll(done);
        }
        ArrayList<String> urls = state.getStringArrayList(KEY_ITEM_URLS);
        long[] latencies = state.getLongArray(KEY_ITEM_LATENCIES);
        Map<String, Long> saved = new HashMap<>();
        if (urls != null && latencies != null) {
            for (int i = 0; i < urls.size() && i < latencies.length; i++) {
                saved.put(urls.get(i), latencies[i]);
            }
        }
        Map<String, Long> cached = MirrorHelper.getCachedSpeedResults(this);
        mItems.clear();
        for (MirrorHelper.MirrorSpeedResult t : MirrorHelper.getSpeedTestTargets(this)) {
            Long latency = saved.get(t.url);
            if (latency == null) {
                latency = cached.get(t.url);
            }
            mItems.add(latency != null
                    ? new MirrorHelper.MirrorSpeedResult(t.name, t.url, t.presetValue, latency)
                    : t);
        }
        if (!saved.isEmpty() || !cached.isEmpty()) {
            sortByLatency(mItems);
        }
        updateCurrentLine();
        mAdapter.notifyDataSetChanged();
        updateStatsBar();
        if (mItems.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mEmptyHint.setVisibility(View.VISIBLE);
            mAutoSubtitle.setText("");
        } else {
            mListView.setVisibility(View.VISIBLE);
            mEmptyHint.setVisibility(View.GONE);
            if (mTestDone) {
                // 上轮已测完：复用结果，不重跑探测；副标题口径与 finishTest 一致。
                boolean hasOk = false;
                for (MirrorHelper.MirrorSpeedResult r : mItems) {
                    if (r.isOk()) {
                        hasOk = true;
                        break;
                    }
                }
                if (!mUserPicked && hasOk) {
                    mAutoSubtitle.setText(R.string.mirror_manage_auto_selected);
                } else if (mUserPicked) {
                    mAutoSubtitle.setText(R.string.mirror_manual_subtitle);
                } else {
                    mAutoSubtitle.setText("");
                }
            } else {
                // 测速中途旋转：已测完的保留结果，未测完的重跑本轮探测
                // （startSpeedTest 仍按整轮探测，见汇报残留说明）。
                mAutoSubtitle.setText(R.string.mirror_manage_testing);
                startSpeedTest();
            }
        }
        if (mBatchMode) {
            mBatchBar.setVisibility(View.VISIBLE);
            updateBatchBar();
        }
        if (state.getBoolean(KEY_ADD_CARD_OPEN, false)) {
            mAddTitle.setText(mEditingIsNew
                    ? R.string.mirror_add : R.string.me_mirror_edit);
            String text = state.getString(KEY_ADD_CARD_TEXT, "");
            mUrlInput.setText(text);
            mUrlInput.setSelection(mUrlInput.getText().length());
            mAddCard.setVisibility(View.VISIBLE);
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_BATCH, Menu.NONE, mBatchMode
                ? getString(android.R.string.cancel)
                : getString(R.string.mirror_batch_delete))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_BATCH) {
            if (mBatchMode) {
                exitBatchMode();
            } else {
                enterBatchMode();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mBatchMode) {
            exitBatchMode();
            return;
        }
        if (isAddCardOpen()) {
            cancelAddCard();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancelSpeedTest();
        super.onDestroy();
    }

    private void cancelSpeedTest() {
        if (mSpeedTestHandle != null) {
            mSpeedTestHandle.cancel();
            mSpeedTestHandle = null;
        }
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

    /** 延迟最低且可用的镜像；exclude 里的地址跳过；没有返回 null。 */
    private String fastestOkUrl(Set<String> exclude) {
        String best = null;
        long bestLatency = Long.MAX_VALUE;
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (!r.isOk() || r.latencyMs >= bestLatency) {
                continue;
            }
            if (exclude != null) {
                boolean excluded = false;
                for (String u : exclude) {
                    if (u.equalsIgnoreCase(r.url)) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) {
                    continue;
                }
            }
            bestLatency = r.latencyMs;
            best = r.url;
        }
        return best;
    }

    private void storeCurrent() {
        mPrefs.edit().putString(SettingsFragment.KEY_MIRROR_PRESET, mCurrentValue).apply();
    }

    private void storeAutoSelect(boolean auto) {
        mPrefs.edit().putBoolean(SettingsFragment.KEY_MIRROR_AUTO_SELECT, auto).apply();
    }

    /** 点行选用：测速结果仅供参考，随时可点，不用等测完；选完留在页内，同时关掉自动选择。 */
    private void pick(MirrorHelper.MirrorSpeedResult r) {
        mUserPicked = true;
        mCurrentValue = r.url;
        storeCurrent();
        storeAutoSelect(false);
        Toast.makeText(this, getString(R.string.mirror_speed_selected, r.name),
                Toast.LENGTH_SHORT).show();
        setAutoSwitchChecked(false);
        mAutoSubtitle.setText(R.string.mirror_manual_subtitle);
        updateCurrentLine();
        mAdapter.notifyDataSetChanged();
    }

    /** 自动选择开关：打开时重选延迟最低的；关闭等于手动选择。 */
    private void setAuto(boolean on) {
        mUserPicked = !on;
        storeAutoSelect(on);
        if (on) {
            String fastest = fastestOkUrl(null);
            if (fastest != null) {
                mCurrentValue = fastest;
                storeCurrent();
                mAutoSubtitle.setText(R.string.mirror_manage_auto_selected);
                Toast.makeText(this, R.string.mirror_manage_auto_selected,
                        Toast.LENGTH_SHORT).show();
            } else {
                mAutoSubtitle.setText("");
            }
            updateCurrentLine();
            mAdapter.notifyDataSetChanged();
        } else {
            mAutoSubtitle.setText(R.string.mirror_manual_subtitle);
        }
    }

    private void setAutoSwitchChecked(boolean checked) {
        mIgnoreSwitchEvent = true;
        mAutoSwitch.setChecked(checked);
        mIgnoreSwitchEvent = false;
    }

    /** 增删改后：重建列表、当前选中失效则回退、重新测速。 */
    private void refreshAndRetest() {
        cancelSpeedTest();
        mTestDone = false;
        collapseAddCard();
        mItems.clear();
        mDoneUrls.clear();
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(this);
        // 上次缓存的延迟：打开即显示并按缓存排序，不用干等；后台再实时刷新
        java.util.Map<String, Long> cached =
                MirrorHelper.getCachedSpeedResults(this);
        for (MirrorHelper.MirrorSpeedResult t : targets) {
            Long c = cached.get(t.url);
            mItems.add(c != null
                    ? new MirrorHelper.MirrorSpeedResult(t.name, t.url, t.presetValue, c)
                    : t);
        }
        if (!cached.isEmpty()) {
            sortByLatency(mItems);
        }
        // 当前选中的地址若已被删掉：优先回退默认地址，否则取第一个，否则置空。
        // 0.2.2：当前为空（手动清空）时不自动回填——空就是用户的选择，
        // 之后再增删改也不应把它填回来；旧的一次性 mKeepCurrentOnRefresh
        // 标志因此不再需要（删正在用的镜像时 deleteUrls 已按稿子规则定好
        // 新当前值，这里只处理“非空但不在列表里”的过期值）。
        if (!mCurrentValue.isEmpty() && !containsUrl(mCurrentValue)) {
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
            storeCurrent();
        }
        updateCurrentLine();
        mAdapter.notifyDataSetChanged();
        updateStatsBar();
        if (mItems.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mEmptyHint.setVisibility(View.VISIBLE);
            mAutoSubtitle.setText("");
            return;
        }
        mListView.setVisibility(View.VISIBLE);
        mEmptyHint.setVisibility(View.GONE);
        mAutoSubtitle.setText(R.string.mirror_manage_testing);
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
                new ArrayList<>(mItems);
        // 0.2.4（94）：本轮的代数——回调里只认自己这代，旧轮的 onAllComplete
        // 哪怕晚到，也只跳过置空，不会把新一轮的句柄清掉（之后 onDestroy
        // 的 cancelSpeedTest 才能取消到新测速，也不会两轮并发重跑）。
        mSpeedTestGeneration++;
        final int generation = mSpeedTestGeneration;
        mSpeedTestHandle = MirrorHelper.testAllMirrorsSpeed(this, probes,
                new MirrorHelper.SpeedTestCallback() {
                    @Override
                    public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
                        // 161：与 onAllComplete 同款代数守卫——旧轮探针晚到时直接
                        // 返回，不覆盖新一轮的行数据。
                        if (generation != mSpeedTestGeneration) {
                            return;
                        }
                        if (isFinishing() || isDestroyed()) {
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
                        updateCurrentLine();
                        updateStatsBar();
                    }

                    @Override
                    public void onAllComplete() {
                        // 0.2.4（94 收尾）：代数守卫覆盖整条收尾路径——旧轮
                        // 回调晚到时直接返回，既不置空新一轮句柄，也不跑
                        // save + finish（旧轮的缓存写入会覆盖新一轮的实测
                        // 结果，finishTest 会按旧数据重排并可能改选当前
                        // 镜像）。目前旧轮到这一步前已被 cancel 拦下、尚
                        // 不可达，此处是纵深防御。
                        if (generation != mSpeedTestGeneration) {
                            return;
                        }
                        mSpeedTestHandle = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        // 存缓存，下次打开直接显示
                        MirrorHelper.saveSpeedTestResults(MirrorSourceActivity.this, mItems);
                        mTestDone = true;
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
            storeCurrent();
            mAutoSubtitle.setText(R.string.mirror_manage_auto_selected);
        } else if (mUserPicked) {
            mAutoSubtitle.setText(R.string.mirror_manual_subtitle);
        } else {
            mAutoSubtitle.setText("");
        }
        updateCurrentLine();
        updateStatsBar();
        mAdapter.notifyDataSetChanged();
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
            View row = mListView.getChildAt(visible);
            mAdapter.bindBadge(row, result);
            return;
        }
    }

    /** 当前镜像行：主机名 + 延迟（未测完显示测试中、不可用显示不可用、未选显示无）。 */
    private void updateCurrentLine() {
        if (mCurrentValue == null || mCurrentValue.isEmpty()) {
            mCurrentLine.setText(getString(R.string.store_mirror_current,
                    getString(R.string.mirror_current_none)));
            return;
        }
        String latency = getString(R.string.mirror_source_testing);
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (r.url.equalsIgnoreCase(mCurrentValue)) {
                if (r.isOk()) {
                    latency = getString(R.string.mirror_source_latency, r.latencyMs);
                } else if (mTestDone || mDoneUrls.contains(r.url)) {
                    latency = getString(R.string.mirror_source_unavailable);
                }
                break;
            }
        }
        mCurrentLine.setText(getString(R.string.mirror_current_line,
                MirrorHelper.displayHost(mCurrentValue), latency));
    }

    /** 顶部统计条：总数 = 列表条数，正常/超时 = 本次测速结果，更新于 = 上次测速完成时间。 */
    private void updateStatsBar() {
        int total = mItems.size();
        int ok = 0;
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (r.isOk()) {
                ok++;
            }
        }
        mStatsTotal.setText(String.valueOf(total));
        mStatsOk.setText(String.valueOf(ok));
        mStatsTimeout.setText(String.valueOf(total - ok));
        long t = MirrorHelper.getSpeedTestTime(this);
        if (t > 0) {
            mStatsUpdated.setVisibility(View.VISIBLE);
            mStatsUpdated.setText(getString(R.string.mirror_stats_updated,
                    new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                            .format(new java.util.Date(t))));
        } else {
            // 从未测过：先不显示更新时间，测完第一轮后出现
            mStatsUpdated.setVisibility(View.GONE);
        }
    }

    /** 点“+ 新增镜像源”：输入卡片展开，输入框清空。 */
    private void startAdding() {
        if (isAddCardOpen()) {
            return;
        }
        mEditingIsNew = true;
        mEditingOldUrl = null;
        mAddTitle.setText(R.string.mirror_add);
        mUrlInput.setText("");
        showAddCard();
    }

    /** 点行内编辑图标：输入卡片展开并预填该行地址（按地址定位，不怕重排）。 */
    private void startEditing(String url) {
        if (isAddCardOpen()) {
            return;
        }
        mEditingIsNew = false;
        mEditingOldUrl = url;
        mAddTitle.setText(R.string.me_mirror_edit);
        mUrlInput.setText(url);
        mUrlInput.setSelection(mUrlInput.getText().length());
        showAddCard();
    }

    private void showAddCard() {
        mAddCard.setVisibility(View.VISIBLE);
        mUrlInput.post(() -> {
            mUrlInput.requestFocus();
            showKeyboard(mUrlInput);
        });
    }

    private boolean isAddCardOpen() {
        return mAddCard != null && mAddCard.getVisibility() == View.VISIBLE;
    }

    private void collapseAddCard() {
        if (mAddCard != null) {
            mAddCard.setVisibility(View.GONE);
        }
        mEditingOldUrl = null;
    }

    /** 输入卡片保存：新增或修改地址。 */
    private void saveAddCard() {
        String url = MirrorHelper.normalizeUrl(mUrlInput.getText().toString());
        if (url.isEmpty()
                || !url.regionMatches(true, 0, "https://", 0, 8)) {
            Toast.makeText(this, R.string.mirror_custom_url_invalid,
                    Toast.LENGTH_LONG).show();
            return;
        }
        // 93：只验 https:// 前缀不够——回环地址会在 add/update 里静默拒写，
        // UI 却 toast“已添加”。这里用集中校验先拦住。
        if (!MirrorHelper.isValidMirrorUrl(url)) {
            Toast.makeText(this, R.string.mirror_custom_url_invalid,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!mEditingIsNew && url.equalsIgnoreCase(mEditingOldUrl)) {
            // 地址没改：直接收起输入卡片
            cancelAddCard();
            return;
        }
        for (MirrorHelper.MirrorSpeedResult r : mItems) {
            if (url.equalsIgnoreCase(r.url)) {
                Toast.makeText(this, R.string.mirror_url_exists,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        hideKeyboard();
        boolean wasCurrent = !mEditingIsNew
                && mEditingOldUrl != null
                && mEditingOldUrl.equalsIgnoreCase(mCurrentValue);
        collapseAddCard();
        if (mEditingIsNew) {
            MirrorHelper.addMirrorUrl(this, url);
            Toast.makeText(this, R.string.mirror_added, Toast.LENGTH_SHORT).show();
        } else {
            MirrorHelper.updateMirrorUrl(this, mEditingOldUrl, url);
            Toast.makeText(this, R.string.mirror_updated, Toast.LENGTH_SHORT).show();
            if (wasCurrent) {
                // 改正在用的镜像地址：新地址接替当前选中
                mCurrentValue = url;
                storeCurrent();
            }
        }
        refreshAndRetest();
    }

    /** 收起输入卡片（点取消或地址未改）。 */
    private void cancelAddCard() {
        hideKeyboard();
        collapseAddCard();
    }

    /** 删除行：弹确认框（按地址删，绑定后列表重排也不怕删错）。 */
    private void deleteByUrl(String url) {
        if (isAddCardOpen()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.mirror_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    deleteUrls(java.util.Collections.singletonList(url));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 删镜像（单条或批量共用）：删正在用的按稿子规则——自动开着重选延迟最低的，
     * 手动选的当前置空；其余情况沿用原回退逻辑。
     */
    private void deleteUrls(List<String> urls) {
        boolean removedCurrent = false;
        for (String url : urls) {
            if (url.equalsIgnoreCase(mCurrentValue)) {
                removedCurrent = true;
            }
        }
        // 先按删前的延迟算好新当前（要排除正要删的地址），再删；
        // 否则最快的若正是被删的那个，会被重新选上。
        String newCurrent = null;
        if (removedCurrent) {
            if (!mUserPicked) {
                newCurrent = fastestOkUrl(new HashSet<>(urls));
            } else {
                newCurrent = "";
            }
        }
        for (String url : urls) {
            MirrorHelper.removeMirrorUrl(this, url);
        }
        Toast.makeText(this, R.string.mirror_deleted, Toast.LENGTH_SHORT).show();
        if (removedCurrent) {
            mCurrentValue = newCurrent != null ? newCurrent : "";
            storeCurrent();
        }
        refreshAndRetest();
    }

    /** 进入批量删除：行首出现复选框，铅笔/垃圾桶隐藏，底部栏出现。 */
    private void enterBatchMode() {
        if (mBatchMode) {
            return;
        }
        mBatchMode = true;
        mChecked.clear();
        collapseAddCard();
        mBatchBar.setVisibility(View.VISIBLE);
        updateBatchBar();
        mAdapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    private void exitBatchMode() {
        if (!mBatchMode) {
            return;
        }
        mBatchMode = false;
        mChecked.clear();
        mBatchBar.setVisibility(View.GONE);
        mAdapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    private void toggleChecked(String url) {
        if (mChecked.contains(url)) {
            mChecked.remove(url);
        } else {
            mChecked.add(url);
        }
        updateBatchBar();
        mAdapter.notifyDataSetChanged();
    }

    private void updateBatchBar() {
        mBatchCount.setText(getString(R.string.mirror_batch_selected, mChecked.size()));
        mBatchDeleteBtn.setEnabled(!mChecked.isEmpty());
    }

    /** 批量删除确认：列出选中的域名，确认后删除并退出批量模式。 */
    private void askBatchDelete() {
        if (mChecked.isEmpty()) {
            return;
        }
        List<String> hosts = new ArrayList<>();
        for (String url : mChecked) {
            hosts.add(MirrorHelper.displayHost(url));
        }
        String names = android.text.TextUtils.join("、", hosts);
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.mirror_batch_delete_confirm, mChecked.size())
                        + "\n" + names)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    List<String> urls = new ArrayList<>(mChecked);
                    exitBatchMode();
                    deleteUrls(urls);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    /** 显式唤起键盘（flag=0 表示用户主动操作，输入法不会忽略）。 */
    private void showKeyboard(EditText input) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, 0);
        }
    }

    /** 列表适配器：普通行与批量行共用；输入卡片在列表之外，不参与回收。 */
    private class MirrorManageAdapter extends BaseAdapter {

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
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(
                        R.layout.activity_mirror_source_item, parent, false);
            }
            bindRow(v, position);
            return v;
        }

        private void bindRow(View v, final int position) {
            MirrorHelper.MirrorSpeedResult r = mItems.get(position);
            TextView name = v.findViewById(R.id.name);
            TextView url = v.findViewById(R.id.url);
            TextView pill = v.findViewById(R.id.current_pill);
            ImageView check = v.findViewById(R.id.check);
            ImageButton edit = v.findViewById(R.id.edit);
            ImageButton delete = v.findViewById(R.id.delete);
            CheckBox cbox = v.findViewById(R.id.cbox);
            name.setText(r.name);
            url.setText(r.url);
            boolean selected = r.url.equalsIgnoreCase(mCurrentValue);
            pill.setVisibility(selected ? View.VISIBLE : View.GONE);
            check.setVisibility(selected && !mBatchMode ? View.VISIBLE : View.GONE);
            if (mBatchMode) {
                cbox.setVisibility(View.VISIBLE);
                cbox.setChecked(mChecked.contains(r.url));
                edit.setVisibility(View.GONE);
                delete.setVisibility(View.GONE);
                // 198：原 0xFFF5F5F5 浅灰在深色下配浅字不可读；改用
                // mirror_row_selected（#2465b345 透明绿，与非批量
                // 选中态同款，深浅模式均可读）。
                v.setBackgroundResource(mChecked.contains(r.url)
                        ? R.drawable.mirror_row_selected : 0);
            } else {
                cbox.setVisibility(View.GONE);
                edit.setVisibility(View.VISIBLE);
                delete.setVisibility(View.VISIBLE);
                v.setBackgroundResource(selected
                        ? R.drawable.mirror_row_selected : 0);
            }
            bindBadge(v, r);
            edit.setOnClickListener(eb -> startEditing(r.url));
            delete.setOnClickListener(db -> deleteByUrl(r.url));
        }

        /** 绑定/刷新延迟徽章（颜色随延迟区间变化）。 */
        void bindBadge(View v, MirrorHelper.MirrorSpeedResult r) {
            TextView status = v.findViewById(R.id.status);
            android.content.res.Resources res = getResources();
            int badgeColor;
            if (!mDoneUrls.contains(r.url) && r.latencyMs < 0) {
                // 无缓存且本次还没测完
                status.setText(R.string.mirror_source_testing);
                badgeColor = res.getColor(android.R.color.darker_gray);
            } else if (r.isOk()) {
                // 本次实测或上次缓存的延迟：按区间着色（<500绿，<1000橙，否则红）
                status.setText(getString(R.string.mirror_source_latency, r.latencyMs));
                badgeColor = r.latencyMs < 500
                        ? res.getColor(R.color.primary)
                        : r.latencyMs < 1000
                                ? res.getColor(R.color.accent_orange)
                                : res.getColor(R.color.accent_red);
            } else {
                status.setText(R.string.mirror_source_unavailable);
                badgeColor = res.getColor(android.R.color.darker_gray);
            }
            android.graphics.drawable.Drawable badge = androidx.core.content.ContextCompat
                    .getDrawable(MirrorSourceActivity.this,
                            R.drawable.mirror_latency_badge).mutate();
            badge.setTint(badgeColor);
            status.setBackground(badge);
            status.setTextColor(res.getColor(android.R.color.white));
        }
    }
}
