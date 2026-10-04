package com.gh4a.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.os.LocaleListCompat;
import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.TranslationSettingsActivity;
import com.gh4a.activities.IssueListActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.worker.NotificationsWorker;
import com.gh4a.worker.ReleaseRadarWorker;
import com.gh4a.utils.MirrorHelper;
import com.gh4a.widget.IntegerListPreference;

import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        DarkModeScheduleDialogFragment.SettingsRefreshListener {
    public interface OnStateChangeListener {
        void onThemeChanged();
        void onFontScaleChanged();
    }

    public static final String PREF_NAME = "Gh4a-pref";

    public static final String KEY_THEME = "theme";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_START_PAGE = "start_page";
    public static final String KEY_TEXT_SIZE = "webview_initial_zoom";
    public static final String KEY_FONT_SCALE = "font_scale";
    public static final String KEY_GIF_LOADING = "http_gif_load_mode";
    public static final String KEY_CUSTOM_TABS = "use_custom_tabs";
    public static final String KEY_NOTIFICATIONS = "notifications";
    public static final String KEY_NOTIFICATION_INTERVAL = "notification_interval";
    public static final String KEY_MIRROR_ENABLED = "mirror_enabled";
    public static final String KEY_MIRROR_PRESET = "mirror_preset";
    public static final String KEY_RELEASE_RADAR_NOTIFICATIONS = "release_radar_notifications";
    public static final String KEY_RELEASE_RADAR_INTERVAL = "release_radar_interval";
    private static final String KEY_ABOUT = "about";
    private static final String KEY_CUSTOMIZE_DRAWER = "customize_drawer";
    private static final String KEY_ACCOUNT_MANAGE = "account_manage";
    private static final String KEY_TRANSLATION_SETTINGS = "translation_settings";
    private static final String KEY_CHECK_UPDATE = "check_update";
    public static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_DARK_MODE_SCHEDULE_TIME = "dark_mode_schedule_time";
    private static final String KEY_BACKUP_RESTORE = "backup_restore";
    private static final String KEY_DOWNLOAD_MANAGER = "download_manager";

    public static boolean isAutoCheckUpdateEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CHECK_UPDATE, true);
    }
    private static final String KEY_OPEN_SOURCE_COMPONENTS = "open_source_components";

    private OnStateChangeListener mListener;
    private ListPreference mFontScalePref;
    private Preference mAboutPref;
    private Preference mOpenSourcePref;
    private TwoStatePreference mNotificationsPref;
    private IntegerListPreference mNotificationIntervalPref;
    private TwoStatePreference mReleaseRadarPref;
    private IntegerListPreference mReleaseRadarIntervalPref;
    private Preference mDarkModeScheduleTimePref;
    private ListPreference mDarkModePref;
    /** 进行中的镜像测速任务，销毁时取消 (M-10)。 */
    private MirrorHelper.SpeedTestHandle mSpeedTestHandle;
    /** 测速中的进度对话框，存为字段以便销毁时主动 dismiss，避免 leaked window。 */
    private AlertDialog mSpeedTestDialog;

    private final androidx.activity.result.ActivityResultLauncher<String[]> mRestoreLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null && isAdded()) {
                            confirmAndApplyRestore(uri);
                        }
                    });

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof OnStateChangeListener)) {
            throw new IllegalArgumentException("Activity must implement OnStateChangeListener");
        }
        mListener = (OnStateChangeListener) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMirrorPresetSummary();
    }

    @Override
    public void onDestroy() {
        // 测速中途退出：取消任务，避免回调强引用已销毁的 Fragment (M-10)
        if (mSpeedTestHandle != null) {
            mSpeedTestHandle.cancel();
            mSpeedTestHandle = null;
        }
        // 进度对话框是局部变量时销毁后无法主动 dismiss，会 leaked window
        if (mSpeedTestDialog != null) {
            try {
                mSpeedTestDialog.dismiss();
            } catch (Exception ignored) {
            }
            mSpeedTestDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.settings);

        // settings.xml 里已没有 "theme" 键（主题改由深色模式控制），
        // findPreference 永远返回 null，mThemePref 相关代码已删除 (L-11)；
        // KEY_THEME 常量保留：Gh4Application.updateTheme/迁移与 HomeActivity 仍读写该键

        Preference accentColorPref = findPreference(KEY_ACCENT_COLOR);
        accentColorPref.setOnPreferenceChangeListener(this);

        Preference languagePref = findPreference(KEY_LANGUAGE);
        languagePref.setOnPreferenceChangeListener(this);

        mFontScalePref = findPreference(KEY_FONT_SCALE);
        mFontScalePref.setOnPreferenceChangeListener(this);

        mAboutPref = findPreference(KEY_ABOUT);
        mAboutPref.setOnPreferenceClickListener(this);
        mAboutPref.setSummary(getAppName());

        mOpenSourcePref = findPreference(KEY_OPEN_SOURCE_COMPONENTS);
        mOpenSourcePref.setOnPreferenceClickListener(this);

        Preference customizeDrawerPref = findPreference(KEY_CUSTOMIZE_DRAWER);
        customizeDrawerPref.setOnPreferenceClickListener(this);

        Preference accountManagePref = findPreference(KEY_ACCOUNT_MANAGE);
        accountManagePref.setOnPreferenceClickListener(this);

        Preference checkUpdatePref = findPreference(KEY_CHECK_UPDATE);
        checkUpdatePref.setOnPreferenceClickListener(this);

        Preference translationSettingsPref = findPreference(KEY_TRANSLATION_SETTINGS);
        if (translationSettingsPref != null) {
            translationSettingsPref.setOnPreferenceClickListener(this);
        }

        mNotificationsPref = findPreference(KEY_NOTIFICATIONS);
        mNotificationsPref.setOnPreferenceChangeListener(this);

        mNotificationIntervalPref = findPreference(KEY_NOTIFICATION_INTERVAL);
        mNotificationIntervalPref.setOnPreferenceChangeListener(this);

        // 镜像源：点击不弹原生对话框，改走“选择+实时测速”合并对话框
        // （MirrorPresetPreference.onClick 已空实现，原生对话框永不弹出）
        MirrorPresetPreference mirrorPresetPref = findPreference(KEY_MIRROR_PRESET);
        if (mirrorPresetPref != null) {
            mirrorPresetPref.setOnPickListener(() -> showMirrorSourceDialog());
        }

        mReleaseRadarPref = findPreference(KEY_RELEASE_RADAR_NOTIFICATIONS);
        mReleaseRadarPref.setOnPreferenceChangeListener(this);

        mReleaseRadarIntervalPref = findPreference(KEY_RELEASE_RADAR_INTERVAL);
        mReleaseRadarIntervalPref.setOnPreferenceChangeListener(this);

        mDarkModePref = findPreference(KEY_DARK_MODE);
        if (mDarkModePref != null) {
            mDarkModePref.setOnPreferenceChangeListener(this);
        }
        mDarkModeScheduleTimePref = findPreference(KEY_DARK_MODE_SCHEDULE_TIME);
        if (mDarkModeScheduleTimePref != null) {
            mDarkModeScheduleTimePref.setOnPreferenceClickListener(this);
            refreshDarkModeScheduleSummary();
        }

        Preference backupRestorePref = findPreference(KEY_BACKUP_RESTORE);
        if (backupRestorePref != null) {
            backupRestorePref.setOnPreferenceClickListener(this);
        }

        Preference downloadManagerPref = findPreference(KEY_DOWNLOAD_MANAGER);
        if (downloadManagerPref != null) {
            downloadManagerPref.setOnPreferenceClickListener(this);
        }
    }

    public static void applyLanguage(String languageTag) {
        LocaleListCompat locales = languageTag == null || languageTag.isEmpty()
                ? LocaleListCompat.create(new java.util.Locale[0])
                : LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (KEY_ACCENT_COLOR.equals(pref.getKey())) {
            // Accent color needs a full restart like theme change
            mListener.onThemeChanged();
            return true;
        }
        if (KEY_LANGUAGE.equals(pref.getKey())) {
            applyLanguage((String) newValue);
            mListener.onThemeChanged(); // restart stack to apply
            return true;
        }
        if (pref == mFontScalePref) {
            // Tell the activity: it flags the change for the caller (so the
            // whole task stack restarts, like a theme change) and recreates
            // itself to apply the new scale right away.
            mListener.onFontScaleChanged();
            return true;
        }
        if (pref == mNotificationsPref) {
            if ((boolean) newValue) {
                NotificationsWorker.createNotificationChannels(getActivity());
                NotificationsWorker.schedule(getContext(),
                        Integer.valueOf(mNotificationIntervalPref.getValue()));
                // On Android 13 and up, notification permissions must be granted manually
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        getActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    getActivity().requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
                }
            } else {
                NotificationsWorker.cancel(getContext());
            }
            return true;
        }
        if (pref == mNotificationIntervalPref) {
            if (mNotificationsPref.isChecked()) {
                NotificationsWorker.schedule(getContext(), Integer.parseInt((String) newValue));
            }
            return true;
        }
        if (pref == mReleaseRadarPref) {
            if ((boolean) newValue) {
                ReleaseRadarWorker.createNotificationChannels(getActivity());
                ReleaseRadarWorker.schedule(getContext(),
                        Integer.valueOf(mReleaseRadarIntervalPref.getValue()));
                // On Android 13 and up, notification permissions must be granted manually
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        getActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    getActivity().requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
                }
            } else {
                ReleaseRadarWorker.cancel(getContext());
            }
            return true;
        }
        if (pref == mReleaseRadarIntervalPref) {
            if (mReleaseRadarPref.isChecked()) {
                ReleaseRadarWorker.schedule(getContext(), Integer.parseInt((String) newValue));
            }
            return true;
        }
        if (KEY_DARK_MODE.equals(pref.getKey())) {
            // onPreferenceChange 触发时新值尚未持久化，必须用 newValue 而不是读 prefs，
            // 否则 apply() 会读到旧值导致切换不生效/闹钟状态反向
            com.gh4a.utils.DarkModeScheduler.apply(getContext(), (String) newValue);
            refreshDarkModeScheduleSummary();
            // recreate to apply the new night mode immediately
            mListener.onThemeChanged();
            return true;
        }
        return false;
    }

    /** Updates the "深色时段" summary and enables it only in scheduled mode. */
    public void refreshDarkModeScheduleSummary() {
        if (mDarkModeScheduleTimePref == null) {
            return;
        }
        android.content.Context context = getContext();
        boolean scheduled = com.gh4a.utils.DarkModeScheduler.isScheduled(context);
        mDarkModeScheduleTimePref.setEnabled(scheduled);
        if (scheduled) {
            mDarkModeScheduleTimePref.setSummary(getString(
                    R.string.dark_mode_schedule_summary,
                    com.gh4a.utils.DarkModeScheduler.formatMinutes(
                            com.gh4a.utils.DarkModeScheduler.getStartMinutes(context)),
                    com.gh4a.utils.DarkModeScheduler.formatMinutes(
                            com.gh4a.utils.DarkModeScheduler.getEndMinutes(context))));
        } else {
            mDarkModeScheduleTimePref.setSummary("");
        }
    }

    /**
     * 镜像源选择对话框：预设镜像 + 自定义地址，打开即全并行测速，
     * 每完成一个实时刷新该行延迟，点击直接选用。测速与选择合二为一。
     */
    /**
     * 镜像源管理对话框：选择 + 打开即测速 + 行内增删改。
     * 测完按延迟从低到高自动排序，并自动选中延迟最低的
     * （本次打开内用户手动选过则不再自动改）。
     */
    private void showMirrorSourceDialog() {
        if (getActivity() == null) {
            return;
        }
        new MirrorManageDialog(getActivity(), findPreference(KEY_MIRROR_PRESET)).show();
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

    private void cancelSpeedTest() {
        if (mSpeedTestHandle != null) {
            mSpeedTestHandle.cancel();
            mSpeedTestHandle = null;
        }
    }

    /** 镜像源设置项的摘要：显示当前选中地址的 host。 */
    private void refreshMirrorPresetSummary() {
        MirrorPresetPreference pref = findPreference(KEY_MIRROR_PRESET);
        if (pref == null) {
            return;
        }
        String v = pref.getValue();
        pref.setSummary(v == null || v.isEmpty() ? "" : MirrorHelper.displayHost(v));
    }

    /** 镜像源管理对话框的会话：列表数据、测速、行内增删改都在这里。
     * 行用 ListView 承载（0.0.42 验证过的 UI，名称单行显示正常）。
     * 编辑中不做任何 notifyDataSetChanged（探针只直改徽章），行内输入框
     * 实例保持稳定；键盘显式唤起。 */
    private class MirrorManageDialog {
        private final android.content.Context mContext;
        private final ListPreference mPresetPref;
        private final List<MirrorHelper.MirrorSpeedResult> mItems = new java.util.ArrayList<>();
        private final java.util.Set<String> mDoneUrls = new java.util.HashSet<>();
        private AlertDialog mDialog;
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

        MirrorManageDialog(android.content.Context context, ListPreference presetPref) {
            mContext = context;
            mPresetPref = presetPref;
            mCurrentValue = presetPref != null ? presetPref.getValue() : null;
        }

        void show() {
            // 新对话框打开前取消上一次未完成的测速，避免回调叠加
            cancelSpeedTest();
            mAdapter = new MirrorManageAdapter();
            android.view.View content = android.view.LayoutInflater.from(mContext)
                    .inflate(R.layout.dialog_mirror_manage, null);
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
            if (mDialog.getWindow() != null) {
                mDialog.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            // 存为字段：Fragment 销毁时若任务还在跑，可主动 cancel/dismiss
            mSpeedTestDialog = mDialog;
            refreshAndRetest();
        }

        /** 点行选用：测速结果仅供参考，随时可点，不用等测完。 */
        private void pick(int position) {
            MirrorHelper.MirrorSpeedResult r = mItems.get(position);
            mUserPicked = true;
            mCurrentValue = r.url;
            if (mPresetPref != null) {
                mPresetPref.setValue(r.url);
            }
            refreshMirrorPresetSummary();
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
                if (mPresetPref != null) {
                    mPresetPref.setValue(mCurrentValue);
                }
                refreshMirrorPresetSummary();
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
                            if (!isAdded()) {
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
                            if (!isAdded()) {
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
                if (mPresetPref != null) {
                    mPresetPref.setValue(fastest.url);
                }
                refreshMirrorPresetSummary();
                mSubtitle.setText(R.string.mirror_manage_auto_selected);
            } else {
                mSubtitle.setText("");
            }
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

    private void showBackupRestoreDialog() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.backup_restore)
                .setItems(new CharSequence[]{
                        getString(R.string.backup), getString(R.string.backup_restore_action)}, (dialog, which) -> {
                    if (which == 0) {
                        doBackup();
                    } else {
                        mRestoreLauncher.launch(new String[]{"application/json"});
                    }
                })
                .show();
    }

    private void doBackup() {
        // Context 和文案在主线程快照，线程里不再碰 getContext() (M-4 同类问题)
        final android.content.Context appContext = getActivity().getApplicationContext();
        final String savedFormat = getString(R.string.backup_saved);
        final String failedFormat = getString(R.string.backup_failed);
        final org.json.JSONObject backup;
        try {
            // collectBackup 只是读 prefs，很快，主线程直接做，以便先检查是否含凭据
            backup = com.gh4a.utils.SettingsBackupManager.collectBackup(appContext);
        } catch (Exception e) {
            showToast(String.format(failedFormat, e.getMessage()));
            return;
        }
        // 备份含翻译 API 凭据（明文）时先弹窗告知，确认后才落盘 (#4)
        if (com.gh4a.utils.SettingsBackupManager.backupContainsTranslationCredentials(backup)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.backup_contains_credentials_title)
                    .setMessage(R.string.backup_contains_credentials_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.backup_continue, (dialog, which) ->
                            writeBackupInBackground(appContext, backup, savedFormat, failedFormat))
                    .show();
        } else {
            writeBackupInBackground(appContext, backup, savedFormat, failedFormat);
        }
    }

    private void writeBackupInBackground(final android.content.Context appContext,
            final org.json.JSONObject backup, final String savedFormat,
            final String failedFormat) {
        new Thread(() -> {
            try {
                String fileName =
                        com.gh4a.utils.SettingsBackupManager.writeBackupFile(appContext, backup);
                showToast(savedFormat + ": " + fileName);
            } catch (Exception e) {
                showToast(String.format(failedFormat, e.getMessage()));
            }
        }).start();
    }

    private void confirmAndApplyRestore(final android.net.Uri uri) {
        new AlertDialog.Builder(getActivity())
                .setMessage(R.string.restore_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> doRestore(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doRestore(final android.net.Uri uri) {
        // applicationContext 快照进线程：恢复完成前离开设置页，getContext() 会是 null (M-4)
        final android.content.Context appContext = getActivity().getApplicationContext();
        new Thread(() -> {
            boolean ok;
            try {
                org.json.JSONObject backup =
                        com.gh4a.utils.SettingsBackupManager.readBackupFile(appContext, uri);
                ok = com.gh4a.utils.SettingsBackupManager.applyBackup(appContext, backup);
                if (ok) {
                    // restored dark-mode setting takes effect on next start
                    com.gh4a.utils.DarkModeScheduler.apply(appContext);
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean result = ok;
            if (isAdded()) {
                getActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(getActivity(),
                            result ? R.string.restore_success : R.string.restore_failed,
                            android.widget.Toast.LENGTH_LONG).show();
                    // 恢复成功后刷新当前界面主题（深色模式切换时已有此先例）
                    if (result && mListener != null) {
                        mListener.onThemeChanged();
                    }
                });
            }
        }).start();
    }

    private void showToast(final String message) {
        if (!isAdded()) {
            return;
        }
        getActivity().runOnUiThread(() -> android.widget.Toast.makeText(
                getActivity(), message, android.widget.Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mAboutPref) {            boolean loggedIn = Gh4Application.get().isAuthorized();
            AboutDialogFragment.newInstance(getAppName(), loggedIn)
                    .show(getChildFragmentManager(), "about");
            return true;
        } else if (pref == mOpenSourcePref) {
            new OpenSourceComponentListDialogFragment()
                    .show(getChildFragmentManager(), "opensource");
            return true;
        } else if (KEY_CUSTOMIZE_DRAWER.equals(pref.getKey())) {
            startActivity(com.gh4a.activities.DrawerEditActivity.makeIntent(getActivity()));
            return true;
        } else if (KEY_ACCOUNT_MANAGE.equals(pref.getKey())) {
            startActivity(com.gh4a.activities.AccountManageActivity.makeIntent(getActivity()));
            return true;
        } else if (KEY_CHECK_UPDATE.equals(pref.getKey())) {
            com.gh4a.utils.UpdateCheckUi.checkManually(
                    (com.gh4a.BaseActivity) getActivity());
            return true;
        } else if (KEY_TRANSLATION_SETTINGS.equals(pref.getKey())) {
            TranslationSettingsActivity.start(getActivity());
            return true;
        } else if (KEY_BACKUP_RESTORE.equals(pref.getKey())) {
            showBackupRestoreDialog();
            return true;
        } else if (KEY_DOWNLOAD_MANAGER.equals(pref.getKey())) {
            startActivity(new android.content.Intent(getActivity(),
                    com.gh4a.activities.DownloadListActivity.class));
            return true;
        } else if (KEY_DARK_MODE_SCHEDULE_TIME.equals(pref.getKey())) {
            DarkModeScheduleDialogFragment.newInstance()
                    .show(getChildFragmentManager(), "dark_schedule");
            return true;
        }
        return false;
    }

    private String getAppName() {
        String version = getAppVersion();
        return getString(R.string.app_name) + " v" + version;
    }

    private String getAppVersion() {
        try {
            PackageManager pm = getActivity().getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(getActivity().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // shouldn't happen
            return "";
        }
    }

    public static class AboutDialogFragment extends DialogFragment {
        public static AboutDialogFragment newInstance(String title, boolean loggedIn) {
            AboutDialogFragment f = new AboutDialogFragment();
            Bundle args = new Bundle();
            args.putString("title", title);
            args.putBoolean("loggedIn", loggedIn);
            f.setArguments(args);
            return f;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String title = getArguments().getString("title");
            boolean loggedIn = getArguments().getBoolean("loggedIn");
            return new AboutDialog(getContext(), title, loggedIn);
        }
    }

    private static class AboutDialog extends AppCompatDialog implements View.OnClickListener {
        public AboutDialog(Context context, String title, boolean loggedIn) {
            super(context);

            setContentView(R.layout.about_dialog);
            setTitle(title);

            TextView tvCopyright = findViewById(R.id.copyright);
            tvCopyright.setText(R.string.copyright_notice);

            findViewById(R.id.btn_by_email).setOnClickListener(this);

            View newIssueButton = findViewById(R.id.btn_by_gh4a);
            if (loggedIn) {
                newIssueButton.setOnClickListener(this);
            } else {
                newIssueButton.setVisibility(View.GONE);
            }

            findViewById(R.id.btn_gh4a).setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Context context = getContext();
            int id = view.getId();

            if (id == R.id.btn_by_email) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{
                        context.getString(R.string.my_email)
                });
                sendIntent.setType("message/rfc822");

                Intent chooserIntent = Intent.createChooser(sendIntent,
                        context.getString(R.string.send_email_title));
                context.startActivity(chooserIntent);
            } else if (id == R.id.btn_by_gh4a) {
                Intent intent = IssueListActivity.makeIntent(context,
                        context.getString(R.string.my_username),
                        context.getString(R.string.my_repo));
                context.startActivity(intent);
            } else if (id == R.id.btn_gh4a) {
                Intent intent = RepositoryActivity.makeIntent(context,
                        context.getString(R.string.my_username),
                        context.getString(R.string.my_repo));
                context.startActivity(intent);
            }
        }
    }

    public static class OpenSourceComponentListDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            RecyclerView rv = (RecyclerView) inflater.inflate(R.layout.open_source_component_list, null);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new OpenSourceComponentAdapter(getContext()));

            return new AlertDialog.Builder(getContext())
                    .setView(rv)
                    .setTitle(R.string.open_source_components)
                    .setPositiveButton(R.string.ok, null)
                    .create();
        }
    }

    private static class OpenSourceComponentAdapter extends RecyclerView.Adapter<OpenSourceComponentViewHolder> {
        private static final String[][] COMPONENTS = new String[][] {
            { "android-gif-drawable", "https://github.com/koral--/android-gif-drawable" },
            { "AndroidSVG", "https://github.com/BigBadaboom/androidsvg" },
            { "AndroidX", "https://github.com/androidx/androidx" },
            { "emoji-java", "https://github.com/vdurmont/emoji-java" },
            { "GitHubSdk", "https://github.com/maniac103/GitHubSdk" },
            { "HoloColorPicker", "https://github.com/LarsWerkman/HoloColorPicker" },
            { "MarkdownEdit", "https://github.com/Tunous/MarkdownEdit" },
            { "Material Design Icons", "https://github.com/google/material-design-icons" },
            { "PrettyTime", "https://github.com/ocpsoft/prettytime" },
            { "Recycler Fast Scroll", "https://github.com/pluscubed/recycler-fast-scroll" },
            { "Retrofit", "https://github.com/square/retrofit" },
            { "RxAndroid", "https://github.com/ReactiveX/RxAndroid" },
            { "RxJava", "https://github.com/ReactiveX/RxJava" },
            { "RxLoader", "https://github.com/maniac103/RxLoader" },
            { "SmoothProgressBar", "https://github.com/castorflex/SmoothProgressBar" },
        };

        private final LayoutInflater mInflater;

        public OpenSourceComponentAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public OpenSourceComponentViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            View itemView = mInflater.inflate(R.layout.open_source_component_item, parent, false);
            return new OpenSourceComponentViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull OpenSourceComponentViewHolder holder, int position) {
            final String[] item = COMPONENTS[position];
            holder.bind(item[0], item[1]);
        }

        @Override
        public int getItemCount() {
            return COMPONENTS.length;
        }
    }

    private static class OpenSourceComponentViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitleView;
        private final TextView mUrlView;

        public OpenSourceComponentViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitleView = itemView.findViewById(R.id.title);
            mUrlView = itemView.findViewById(R.id.url);
        }

        public void bind(String title, String url) {
            mTitleView.setText(title);
            mUrlView.setText(url);
        }
    }
}
