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

    // 0.3.0 设置拆分：同一 Fragment 按传入的 xml 资源加载某一分组，
    // 旧构造（无参数）仍加载完整 settings.xml，兼容旧调用。
    private static final String ARG_XML_RES = "xml_res";

    public static SettingsFragment newInstance(int xmlRes) {
        SettingsFragment f = new SettingsFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putInt(ARG_XML_RES, xmlRes);
        f.setArguments(args);
        return f;
    }

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
        // 从镜像源独立页返回：页内直接写了同一份 prefs，ListPreference 内存里的值
        // 还是旧的，先同步再刷摘要，否则摘要显示旧地址
        MirrorPresetPreference pref = findPreference(KEY_MIRROR_PRESET);
        if (pref != null) {
            String v = getPreferenceManager().getSharedPreferences()
                    .getString(KEY_MIRROR_PRESET, "");
            if (!android.text.TextUtils.equals(v, pref.getValue())) {
                pref.setValue(v);
            }
        }
        refreshMirrorPresetSummary();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        android.os.Bundle args = getArguments();
        int xmlRes = args != null ? args.getInt(ARG_XML_RES, R.xml.settings) : R.xml.settings;
        addPreferencesFromResource(xmlRes);

        // settings.xml 里已没有 "theme" 键（主题改由深色模式控制），
        // findPreference 永远返回 null，mThemePref 相关代码已删除 (L-11)；
        // KEY_THEME 常量保留：Gh4Application.updateTheme/迁移与 HomeActivity 仍读写该键

        Preference accentColorPref = findPreference(KEY_ACCENT_COLOR);
        if (accentColorPref != null) {
            accentColorPref.setOnPreferenceChangeListener(this);
        }

        Preference languagePref = findPreference(KEY_LANGUAGE);
        if (languagePref != null) {
            languagePref.setOnPreferenceChangeListener(this);
        }

        mFontScalePref = findPreference(KEY_FONT_SCALE);
        if (mFontScalePref != null) {
            mFontScalePref.setOnPreferenceChangeListener(this);
        }

        mAboutPref = findPreference(KEY_ABOUT);
        if (mAboutPref != null) {
            mAboutPref.setOnPreferenceClickListener(this);
            mAboutPref.setSummary(getAppName());
        }

        mOpenSourcePref = findPreference(KEY_OPEN_SOURCE_COMPONENTS);
        if (mOpenSourcePref != null) {
            mOpenSourcePref.setOnPreferenceClickListener(this);
        }

        Preference customizeDrawerPref = findPreference(KEY_CUSTOMIZE_DRAWER);
        if (customizeDrawerPref != null) {
            customizeDrawerPref.setOnPreferenceClickListener(this);
        }

        Preference accountManagePref = findPreference(KEY_ACCOUNT_MANAGE);
        if (accountManagePref != null) {
            accountManagePref.setOnPreferenceClickListener(this);
        }

        Preference checkUpdatePref = findPreference(KEY_CHECK_UPDATE);
        if (checkUpdatePref != null) {
            checkUpdatePref.setOnPreferenceClickListener(this);
        }

        Preference translationSettingsPref = findPreference(KEY_TRANSLATION_SETTINGS);
        if (translationSettingsPref != null) {
            translationSettingsPref.setOnPreferenceClickListener(this);
        }

        mNotificationsPref = findPreference(KEY_NOTIFICATIONS);
        if (mNotificationsPref != null) {
            mNotificationsPref.setOnPreferenceChangeListener(this);
        }

        mNotificationIntervalPref = findPreference(KEY_NOTIFICATION_INTERVAL);
        if (mNotificationIntervalPref != null) {
            mNotificationIntervalPref.setOnPreferenceChangeListener(this);
        }

        // 镜像源：点击不弹原生对话框，改走镜像源独立页
        // （MirrorPresetPreference.onClick 已空实现，原生对话框永不弹出）
        MirrorPresetPreference mirrorPresetPref = findPreference(KEY_MIRROR_PRESET);
        if (mirrorPresetPref != null) {
            mirrorPresetPref.setOnPickListener(() -> showMirrorSourcePage());
        }

        mReleaseRadarPref = findPreference(KEY_RELEASE_RADAR_NOTIFICATIONS);
        if (mReleaseRadarPref != null) {
            mReleaseRadarPref.setOnPreferenceChangeListener(this);
        }

        mReleaseRadarIntervalPref = findPreference(KEY_RELEASE_RADAR_INTERVAL);
        if (mReleaseRadarIntervalPref != null) {
            mReleaseRadarIntervalPref.setOnPreferenceChangeListener(this);
        }

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
        // 0.1.38：设置变更链埋点（只记键名，不记新值）。
        com.gh4a.utils.DiagTrace.log(getActivity(), "SETTINGS",
                "key=" + (pref != null ? pref.getKey() : "?"));
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
     * 镜像源管理独立页：选择 + 打开即测速 + 行内增删改。
     * 测完按延迟从低到高自动排序，并自动选中延迟最低的
     * （本次打开内用户手动选过则不再自动改）。
     */
    private void showMirrorSourcePage() {
        if (getActivity() == null) {
            return;
        }
        startActivity(new android.content.Intent(getActivity(),
                com.gh4a.activities.MirrorSourceActivity.class));
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
