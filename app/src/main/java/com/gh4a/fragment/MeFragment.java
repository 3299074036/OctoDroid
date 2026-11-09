package com.gh4a.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.FollowerFollowingListActivity;
import com.gh4a.activities.RepositoryListActivity;
import com.gh4a.activities.TranslationSettingsActivity;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.UiUtils;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.DarkModeScheduler;
import com.gh4a.utils.MirrorHelper;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.SingleFactory;
import com.gh4a.model.NotificationHolder;
import com.gh4a.utils.UpdateCheckUi;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.service.users.UserService;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

/**
 * 0.1.1 我页单页（Home 底部“我”内嵌，无 Tab、无返回箭头、无底部仓库区）。
 * 依次为：头像行（头像 + 名称 + 登录名 + 加入时间，整行点开切换账号底部
 * 动作表，绿底切换账号按钮已删）、计数格（仓库 / 关注者 / 正在关注，
 * 3 格；动态计数格已删，动态改为功能行）、功能区（通知 / 动态 / 事项 /
 * 主题 / 最近浏览，各进二级页面；动态与最近浏览在本页内嵌打开子 Fragment，
 * 返回键或“‹”按钮回到我页；通知/事项/主题走 Home 聚合页）、下方内联
 * 设置（可折叠分组，默认只展开外观；按 settings.xml 原分组，读写同一
 * SharedPreferences key）。已删：通知设置整组、下载管理、动态页 GIF 加载
 * 设置（GIF 始终加载）、深色模式定时、自动检查更新开关（功能默认开启）、
 * Star 更新通知开关与间隔（改为底栏 Star 角标，默认开启）。
 * 不搬的两项不在此出现：自定义抽屉（抽屉已下架致其失效）、账号管理
 * （头像行已有切换账号）。原 SettingsActivity 保留在树里，但我页/
 * 底部/抽屉不再有入口。
 */
public class MeFragment extends Fragment {
    public static MeFragment newInstance(String login) {
        MeFragment f = new MeFragment();
        Bundle args = new Bundle();
        args.putString("login", login);
        f.setArguments(args);
        return f;
    }

    private String mLogin;
    private User mUser;
    private SharedPreferences mPrefs;
    // BUG-11: view-scoped, not fragment-scoped. Disposed in onDestroyView
    // and recreated in onCreateView, so a recreated view can load again
    // (a disposed CompositeDisposable rejects every later add()).
    private CompositeDisposable mDisposables = new CompositeDisposable();
    private TextView mNotificationsValueView;
    /** 我页镜像源行的摘要视图：从独立页返回时在 onResume 里刷新。 */
    private TextView mMirrorPresetValue;

    private final ActivityResultLauncher<String[]> mRestoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null && isAdded()) {
                            confirmAndApplyRestore(uri);
                        }
                    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogin = getArguments() != null ? getArguments().getString("login") : null;
        // 0.1.14: Me sections are always expanded — no collapse state is
        // saved or restored, so rotation/re-entry can never restore a
        // collapsed section.
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_me, container, false);
        mPrefs = requireActivity().getSharedPreferences(SettingsFragment.PREF_NAME,
                Context.MODE_PRIVATE);
        // BUG-11: a fresh view needs a fresh (undisposed) composite.
        if (mDisposables.isDisposed()) {
            mDisposables = new CompositeDisposable();
        }
        // BUG-20: first Me read migrates a stored "scheduled" dark mode
        // to "system" (see DarkModeScheduler.migrateScheduledToSystem);
        // startup already migrates via Gh4Application.updateTheme, so
        // this is the same one-sided migration, not a second mechanism.
        DarkModeScheduler.migrateScheduledToSystem(requireContext());
        // BUG-19 (normalise-once variant, not summary mapping): a stored
        // start page that is not one of the bottom-5 options — legacy
        // keys ("last", "repos", …) — is rewritten once to its bottom-5
        // equivalent, or to discover_hub, so summary, dialog check and
        // the next startup all agree.
        normalizeStartPage();

        view.findViewById(R.id.me_account_header).setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).showAccountSwitcher();
            }
        });
        view.findViewById(R.id.me_count_repos).setOnClickListener(v -> {
            if (mUser != null && getActivity() != null) {
                startActivity(RepositoryListActivity.makeIntent(getActivity(), mUser.login(),
                        mUser.type() == UserType.Organization));
            }
        });
        view.findViewById(R.id.me_count_followers).setOnClickListener(v -> {
            if (mUser != null && getActivity() != null) {
                startActivity(FollowerFollowingListActivity.makeIntent(getActivity(),
                        mUser.login(), true));
            }
        });
        view.findViewById(R.id.me_count_following).setOnClickListener(v -> {
            if (mUser != null && getActivity() != null) {
                startActivity(FollowerFollowingListActivity.makeIntent(getActivity(),
                        mUser.login(), false));
            }
        });
        buildFunctionRows(view);
        buildSettingsRows(view);
        // 0.1.36：诊断追踪（我页建行时刻的状态）。
        com.gh4a.utils.ThemeTrace.logState(requireContext(),
                "ME_BUILT inst=" + com.gh4a.utils.ThemeTrace.instance(getActivity()));
        loadUnreadNotificationCount();
        setupSectionToggles(view);
        if (!TextUtils.isEmpty(mLogin)) {
            // 0.1.10：先秒出上次缓存的头部（有缓存时），后台再拉新数据替换；
            // 无缓存时先出骨架屏，首个结果到货前头部不再空白。
            MeHeaderCache.Header cached = MeHeaderCache.load(requireContext(), mLogin);
            if (cached != null) {
                fillUserFromCache(view, cached);
            } else {
                view.findViewById(R.id.me_header_skeleton).setVisibility(View.VISIBLE);
                view.findViewById(R.id.me_account_header).setVisibility(View.GONE);
            }
            loadUser();
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUnreadNotificationCount();
        // 从镜像源独立页返回：摘要可能变了（换了当前镜像），只刷新这一行
        if (mMirrorPresetValue != null) {
            mMirrorPresetValue.setText(mirrorPresetSummary());
        }
    }

    @Override
    public void onDestroyView() {
        mDisposables.dispose();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mDisposables.dispose();
        super.onDestroy();
    }

    // ---------- user header / counts ----------

    private void loadUser() {
        UserService service = ServiceFactory.get(UserService.class, false);
        mDisposables.add(service.getUser(mLogin)
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils::doInBackground)
                .subscribe(user -> {
                    if (!isAdded()) return;
                    mUser = user;
                    if (getActivity() != null) {
                        MeHeaderCache.save(getActivity(), new MeHeaderCache.Header(
                                mUser.login(),
                                mUser.name(),
                                mUser.id(),
                                mUser.avatarUrl(),
                                mUser.createdAt() != null ? mUser.createdAt().getTime() : -1,
                                orZero(mUser.publicRepos()) + orZero(mUser.totalPrivateRepos()),
                                orZero(mUser.followers()),
                                orZero(mUser.following())));
                    }
                    fillUser();
                }, error -> {
                    // 0.1.10：已有缓存头部时失败不清空（缓存继续显示）；
                    // 无缓存时仍显示登录名与 0 计数，不崩溃。
                    if (!isAdded()) return;
                    if (getActivity() != null
                            && MeHeaderCache.load(getActivity(), mLogin) != null) {
                        return;
                    }
                    fillUserFallback();
                }));
    }

    /** 0.1.10：显示头部内容（隐藏骨架屏）。缓存填充、正常填充、兜底填充共用。 */
    private void showHeaderContent(View view) {
        if (view == null) return;
        View skeleton = view.findViewById(R.id.me_header_skeleton);
        if (skeleton != null) {
            skeleton.setVisibility(View.GONE);
        }
        View header = view.findViewById(R.id.me_account_header);
        if (header != null) {
            header.setVisibility(View.VISIBLE);
        }
    }

    /** 0.1.10：用缓存秒出头部（头像、名字、登录名、加入时间、3 格计数）。 */
    private void fillUserFromCache(View view, MeHeaderCache.Header cached) {
        if (view == null || cached == null) return;
        showHeaderContent(view);
        ImageView avatar = view.findViewById(R.id.me_avatar);
        AvatarHandler.assignAvatar(avatar, cached.login, cached.userId, cached.avatarUrl);
        String name = StringUtils.isBlank(cached.name) ? cached.login : cached.name;
        ((TextView) view.findViewById(R.id.me_name)).setText(name);
        ((TextView) view.findViewById(R.id.me_login)).setText(cached.login);
        TextView joined = view.findViewById(R.id.me_joined);
        if (cached.joinedMillis >= 0) {
            joined.setText(getString(R.string.user_created_at,
                    DateFormat.getMediumDateFormat(requireActivity())
                            .format(new java.util.Date(cached.joinedMillis))));
            joined.setVisibility(View.VISIBLE);
        } else {
            joined.setVisibility(View.GONE);
        }
        setCount(view, R.id.me_count_repos_value, cached.repos);
        setCount(view, R.id.me_count_followers_value, cached.followers);
        setCount(view, R.id.me_count_following_value, cached.following);
    }

    private void fillUserFallback() {
        View view = getView();
        if (view == null) return;
        showHeaderContent(view);
        ((TextView) view.findViewById(R.id.me_name)).setText(mLogin);
        ((TextView) view.findViewById(R.id.me_login)).setText(mLogin);
        setCount(view, R.id.me_count_repos_value, 0);
        setCount(view, R.id.me_count_followers_value, 0);
        setCount(view, R.id.me_count_following_value, 0);
    }

    private void fillUser() {
        View view = getView();
        if (view == null || mUser == null) return;
        showHeaderContent(view);
        ImageView avatar = view.findViewById(R.id.me_avatar);
        AvatarHandler.assignAvatar(avatar, mUser);
        String name = StringUtils.isBlank(mUser.name()) ? mUser.login() : mUser.name();
        ((TextView) view.findViewById(R.id.me_name)).setText(name);
        ((TextView) view.findViewById(R.id.me_login)).setText(mUser.login());
        TextView joined = view.findViewById(R.id.me_joined);
        if (mUser.createdAt() != null) {
            joined.setText(getString(R.string.user_created_at,
                    DateFormat.getMediumDateFormat(requireActivity()).format(mUser.createdAt())));
            joined.setVisibility(View.VISIBLE);
        } else {
            joined.setVisibility(View.GONE);
        }
        setCount(view, R.id.me_count_repos_value,
                orZero(mUser.publicRepos()) + orZero(mUser.totalPrivateRepos()));
        setCount(view, R.id.me_count_followers_value, orZero(mUser.followers()));
        setCount(view, R.id.me_count_following_value, orZero(mUser.following()));
    }

    // 115：本地 orZero 收敛为共享实现，见 ApiHelpers.orZero
    private static int orZero(Integer count) {
        return ApiHelpers.orZero(count);
    }

    private static void setCount(View view, int id, int count) {
        ((TextView) view.findViewById(id)).setText(String.valueOf(count));
    }

    // ---------- row builders ----------

    private void buildFunctionRows(View view) {
        LinearLayout container = view.findViewById(R.id.me_func_container);
        // 0.1.12：每行前面加图标（统一浅绿圆角方块 + 主色线条图标，见 addRow）。
        View notifRow = addNavRow(container, R.string.notifications, R.id.notifications,
                R.drawable.icon_notifications);
        mNotificationsValueView = notifRow.findViewById(R.id.me_setting_value);
        // 0.1.2：动态、最近浏览改为 Home 聚合页（同通知页形态：绿条标题、无返回键、
        // 底栏可见、点底栏“我”返回），不再是我页内嵌页。
        addNavRow(container, R.string.feed_hub, R.id.my_public_events,
                R.drawable.icon_news_feed);
        addNavRow(container, R.string.my_items, R.id.issues_prs,
                R.drawable.icon_issues);
        addNavRow(container, R.string.topic_discovery, R.id.topic_discovery,
                R.drawable.me_ic_compass);
        addNavRow(container, R.string.recent_history, R.id.my_recent_history,
                R.drawable.icon_history);
    }

    // 0.1.12：通知行显示未读数。计数来自与通知页同一数据源
    // （SingleFactory.getNotifications(all=false)，即未读通知全量分页结果中
    // unread() 为真的条数），不是首页布尔指示器；0 条时恢复原来的“›”。
    private void loadUnreadNotificationCount() {
        if (mNotificationsValueView == null) return;
        // P1：走 NotificationCountProvider，与 HomeActivity.updateMeBadge
        // 共享同一次拉取（TTL 2 分钟 + 在途单飞），冷启动 4 次调用只发 1 次网。
        mDisposables.add(com.gh4a.utils.NotificationCountProvider.getInstance()
                .getNotifications()
                .compose(RxUtils::doInBackground)
                .subscribe(result -> {
                    if (!isAdded() || mNotificationsValueView == null) return;
                    // 0.1.20 D-04/Observer: 计数走 HomeActivity.countUnread，
                    // 同步到底栏走 holder 发布（不再直接 setMeBadge）。
                    int unread = com.gh4a.activities.home.HomeActivity
                            .countUnread(result.notifications);
                    if (getActivity() instanceof com.gh4a.activities.home.HomeActivity) {
                        ((com.gh4a.activities.home.HomeActivity) getActivity())
                                .getBadgeState().setNotificationCount(unread);
                    }
                    if (unread > 0) {
                        mNotificationsValueView.setText(String.valueOf(unread));
                        mNotificationsValueView.setTextColor(UiUtils.resolveColor(requireContext(),
                                androidx.appcompat.R.attr.colorPrimary));
                        mNotificationsValueView.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        mNotificationsValueView.setText("›");
                        mNotificationsValueView.setTextColor(ContextCompat.getColor(requireContext(),
                                android.R.color.secondary_text_light));
                        mNotificationsValueView.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }
                }, error -> {
                    // 失败时保持原来的“›”，不显示假数字
                }));
    }

    // 0.1.14: Me sections are always expanded — plain green labels, no
    // collapse toggle, no ▾/▸ indicators, no click listener on headers.
    private void setupSectionToggles(View view) {
        setupSectionHeader(view, R.id.me_section_appearance_header,
                R.id.me_settings_appearance, R.string.settings_category_appearance);
        setupSectionHeader(view, R.id.me_section_browsing_header,
                R.id.me_settings_browsing, R.string.me_section_browsing_network);
        setupSectionHeader(view, R.id.me_section_about_header,
                R.id.me_settings_about, R.string.settings_category_about_updates);
    }

    private void setupSectionHeader(View view, int headerId, int containerId, int baseRes) {
        View header = view.findViewById(headerId);
        View container = view.findViewById(containerId);
        if (header == null || container == null) return;
        container.setVisibility(View.VISIBLE);
        if (header instanceof TextView) {
            ((TextView) header).setText(getString(baseRes));
        }
        header.setOnClickListener(null);
        header.setClickable(false);
    }

    private View addNavRow(LinearLayout container, int labelRes, final int homePageId,
            int iconRes) {
        return addRow(container, getString(labelRes), "›", v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).navigateToHomePage(homePageId);
            }
        }, iconRes);
    }

    private void buildSettingsRows(View view) {
        // 外观（顺序按 v5 清单：启动页、深色模式、主题色、字体大小、语言、翻译）
        LinearLayout appearance = view.findViewById(R.id.me_settings_appearance);
        addValueRow(appearance, R.string.start_page, startPageSummary(), v -> showStartPageDialog(), R.drawable.me_ic_home);
        addValueRow(appearance, R.string.dark_mode, darkModeSummary(), v -> showDarkModeDialog(), R.drawable.icon_theme);
        addValueRow(appearance, R.string.accent_color, accentSummary(), v -> showAccentDialog(), R.drawable.me_ic_palette);
        addValueRow(appearance, R.string.font_size, fontSizeSummary(), v -> showFontSizeDialog(), R.drawable.me_ic_fontsize);
        addValueRow(appearance, R.string.language, languageSummary(), v -> showLanguageDialog(), R.drawable.me_ic_globe);
        addRow(appearance, getString(R.string.translation_settings), "›", v -> {
            if (getActivity() != null) {
                TranslationSettingsActivity.start(getActivity());
            }
        }, R.drawable.me_ic_translate);
        // 浏览与网络（顺序：镜像加速、镜像源、备份和恢复、内置浏览器；
        // 下载管理与 GIF 加载设置已删）
        LinearLayout browsing = view.findViewById(R.id.me_settings_browsing);
        final TextView[] mirrorPresetValue = new TextView[1];
        addSwitchRow(browsing, R.string.mirror_enabled, R.drawable.me_ic_bolt,
                mPrefs.getBoolean(SettingsFragment.KEY_MIRROR_ENABLED, false),
                checked -> {
                    mPrefs.edit().putBoolean(SettingsFragment.KEY_MIRROR_ENABLED, checked).apply();
                    if (mirrorPresetValue[0] != null) {
                        mirrorPresetValue[0].setEnabled(checked);
                    }
                });
        mirrorPresetValue[0] = addValueRow(browsing, R.string.mirror_preset,
                mirrorPresetSummary(), v -> showMirrorSourcePage(), R.drawable.me_ic_database);
        mMirrorPresetValue = mirrorPresetValue[0];
        mirrorPresetValue[0].setEnabled(mPrefs.getBoolean(SettingsFragment.KEY_MIRROR_ENABLED, false));
        addRow(browsing, getString(R.string.backup_restore), "›", v -> showBackupRestoreDialog(), R.drawable.download_small);
        addSwitchRow(browsing, R.string.use_custom_tabs, R.drawable.me_ic_browser,
                mPrefs.getBoolean(SettingsFragment.KEY_CUSTOM_TABS, true),
                checked -> mPrefs.edit()
                        .putBoolean(SettingsFragment.KEY_CUSTOM_TABS, checked).apply());
        // 0.1.2：更新组解散，检查更新移到关于组第一行，组名改为“关于与更新”；
        // Star 提醒行已删（不再显示），自动检查仍默认开启（开关已删，不再可关）。
        mPrefs.edit().putBoolean(SettingsFragment.KEY_AUTO_CHECK_UPDATE, true).apply();
        // 关于与更新（第一行：检查更新；0.1.0 的简化关于框与开源组件列表保留）
        LinearLayout about = view.findViewById(R.id.me_settings_about);
        addRow(about, getString(R.string.check_update), "›", v -> {
            if (getActivity() instanceof BaseActivity) {
                UpdateCheckUi.checkManually((BaseActivity) getActivity());
            }
        }, R.drawable.me_ic_refresh);
        // 0.1.40：诊断日志入口（检查更新下方，交互稿 diagnostic-log-ui.html）。
        addRow(about, getString(R.string.diagnostic_log), "›", v -> {
            if (getActivity() != null) {
                startActivity(new android.content.Intent(getActivity(),
                        com.gh4a.activities.DiagnosticLogActivity.class));
            }
        }, R.drawable.icon_history);
        addRow(about, getString(R.string.about), "›", v -> showAboutDialog(), R.drawable.me_ic_info);
        addRow(about, getString(R.string.open_source_components), "›", v -> {
            new SettingsFragment.OpenSourceComponentListDialogFragment()
                    .show(getChildFragmentManager(), "opensource");
        }, R.drawable.md_code);
    }

    // 0.1.12：图标统一为 32dp 浅色圆角方块 + 主色图标（主色取当前主题的
    // colorPrimary，随主题色走；开关行点开关只切换开关，不触发行点击）。
    private void setRowIcon(View row, int iconRes) {
        View chip = row.findViewById(R.id.me_setting_icon_chip);
        ImageView icon = row.findViewById(R.id.me_setting_icon);
        if (chip == null || icon == null || iconRes == 0) {
            if (chip != null) chip.setVisibility(View.GONE);
            return;
        }
        int primary = UiUtils.resolveColor(row.getContext(),
                androidx.appcompat.R.attr.colorPrimary);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(9 * row.getResources().getDisplayMetrics().density);
        bg.setColor((primary & 0x00FFFFFF) | 0x14000000);
        chip.setBackground(bg);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(primary));
    }

    private View addRow(LinearLayout container, String label, String value,
            View.OnClickListener onClick, int iconRes) {
        // 0.1.20 D-10: 展开 + 图标走 inflateMeRow（与 addSwitchRow 共用）。
        View row = inflateMeRow(container, R.layout.row_me_setting, iconRes);
        ((TextView) row.findViewById(R.id.me_setting_label)).setText(label);
        TextView valueView = row.findViewById(R.id.me_setting_value);
        valueView.setText(value);
        row.setOnClickListener(onClick);
        container.addView(row);
        return row;
    }

    private TextView addValueRow(LinearLayout container, int labelRes, String value,
            View.OnClickListener onClick, int iconRes) {
        View row = addRow(container, getString(labelRes), value, onClick, iconRes);
        return row.findViewById(R.id.me_setting_value);
    }

    private void addSwitchRow(LinearLayout container, int labelRes, int iconRes, boolean checked,
            java.util.function.Consumer<Boolean> onChecked) {
        // 0.1.20 D-10: 展开 + 图标步骤委托给共用展开步骤（原先与 addRow
        // 重复约 8 行）；开关绑定仍留本方法（行布局不同：switch 布局）。
        View row = inflateMeRow(container, R.layout.row_me_setting_switch, iconRes);
        ((TextView) row.findViewById(R.id.me_setting_label)).setText(getString(labelRes));
        SwitchCompat sw = row.findViewById(R.id.me_setting_switch);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onChecked.accept(isChecked));
        container.addView(row);
    }

    /** 0.1.20 D-10: 我页行共用展开步骤（展开布局 + 设图标），addRow 与 addSwitchRow 共用。 */
    private View inflateMeRow(LinearLayout container, int layoutRes, int iconRes) {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        View row = inflater.inflate(layoutRes, container, false);
        setRowIcon(row, iconRes);
        return row;
    }

    // ---------- settings summaries ----------

    private String darkModeSummary() {
        String mode = DarkModeScheduler.getMode(requireContext());
        String base;
        if (DarkModeScheduler.MODE_SYSTEM.equals(mode)) {
            base = getString(R.string.dark_mode_system);
        } else if (DarkModeScheduler.MODE_SCHEDULED.equals(mode)) {
            base = getString(R.string.dark_mode_scheduled);
        } else {
            int theme = mPrefs.getInt(SettingsFragment.KEY_THEME, 1);
            base = theme == 0 ? getString(R.string.dark_mode_dark) : getString(R.string.dark_mode_light);
        }
        // 0.1.35：0.1.34 诊断行已完成使命，随修复版删掉，摘要恢复原样。
        return base;
    }

    private String accentSummary() {
        String value = mPrefs.getString(SettingsFragment.KEY_ACCENT_COLOR, "green");
        String[] items = getResources().getStringArray(R.array.accent_color_items);
        String[] values = getResources().getStringArray(R.array.accent_color_values);
        for (int i = 0; i < values.length && i < items.length; i++) {
            if (values[i].equals(value)) return items[i];
        }
        return value;
    }

    private String fontSizeSummary() {
        String value = mPrefs.getString(SettingsFragment.KEY_FONT_SCALE, "1.0");
        String[] items = getResources().getStringArray(R.array.font_scale_items);
        String[] values = getResources().getStringArray(R.array.font_scale_values);
        for (int i = 0; i < values.length && i < items.length; i++) {
            if (values[i].equals(value)) return items[i];
        }
        return value;
    }

    private String languageSummary() {
        String value = mPrefs.getString(SettingsFragment.KEY_LANGUAGE, "");
        String[] items = getResources().getStringArray(R.array.language_items);
        String[] values = getResources().getStringArray(R.array.language_values);
        for (int i = 0; i < values.length && i < items.length; i++) {
            if (values[i].equals(value)) return items[i];
        }
        return value.isEmpty() ? items[0] : value;
    }

    // BUG-19: resolve a stored start page the way
    // HomeActivity.determineInitialPage() does ("last" -> last_selected
    // page; legacy keys -> their aggregate page), then keep only values
    // the Me dialog can actually offer (the bottom 5). Anything else
    // (e.g. "repos" -> my_repos, "issues" -> issues_prs, "topics" ->
    // topic_discovery, unknown keys like "gists") falls back to
    // discover_hub, mirroring determineInitialPage()'s unknown fallback.
    private void normalizeStartPage() {
        String stored = mPrefs.getString(SettingsFragment.KEY_START_PAGE, "discover_hub");
        for (String[] pair : startPageOptions()) {
            if (pair[1].equals(stored)) return;
        }
        String resolved = stored;
        if (TextUtils.equals(resolved, "last")) {
            resolved = mPrefs.getString("last_selected_home_page", "discover_hub");
        }
        String mapped;
        switch (resolved) {
            case "newsfeed":
            case "timeline":
                mapped = "feed_hub";
                break;
            case "recent_history":
                mapped = "search";
                break;
            case "bookmarks":
            case "star_groups":
            case "release_radar":
                mapped = "star_hub";
                break;
            case "trends":
            case "blog":
                mapped = "discover_hub";
                break;
            default:
                // Already a bottom-5 key, or a non-bottom-5 / unknown key:
                // bottom-5 keys were handled above, so anything left here
                // (repos, notifications, my_items, topics, gists, …) has
                // no Me-dialog equivalent and falls back to trend.
                mapped = "discover_hub";
                break;
        }
        // A resolved bottom-5 key (e.g. "last" -> "star_hub") must be kept
        // verbatim, not forced through the legacy switch's default.
        for (String[] pair : startPageOptions()) {
            if (pair[1].equals(resolved)) mapped = resolved;
        }
        if (!TextUtils.equals(mapped, stored)) {
            mPrefs.edit().putString(SettingsFragment.KEY_START_PAGE, mapped).apply();
        }
    }

    private String startPageSummary() {
        String value = mPrefs.getString(SettingsFragment.KEY_START_PAGE, "discover_hub");
        for (String[] pair : startPageOptions()) {
            if (pair[1].equals(value)) return pair[0];
        }
        return value;
    }

    private List<String[]> startPageOptions() {
        // 0.1.1：启动页只列底栏 5 项（趋势 / 动态 / 搜索 / Star / 我），不再列其他页。
        List<String[]> options = new ArrayList<>();
        options.add(new String[] { getString(R.string.discover_hub), "discover_hub" });
        options.add(new String[] { getString(R.string.feed_hub), "feed_hub" });
        options.add(new String[] { getString(R.string.search), "search" });
        options.add(new String[] { getString(R.string.star_hub), "star_hub" });
        options.add(new String[] { getString(R.string.my_profile), "profile" });
        return options;
    }

    private String mirrorPresetSummary() {
        String value = mPrefs.getString(SettingsFragment.KEY_MIRROR_PRESET, "");
        if (TextUtils.isEmpty(value)) return "";
        return MirrorHelper.displayHost(value);
    }

    // ---------- settings dialogs ----------

    // 0.1.20 D-07: 单选走 lambda 友好的 SingleChoiceDialogHelper；
    // 本方法仅作薄委托（5 处调用点传的 lambda 不变）。
    private void showSingleChoiceDialog(int titleRes, CharSequence[] items, int checked,
            com.gh4a.utils.SingleChoiceDialogHelper.OnPick onPick) {
        com.gh4a.utils.SingleChoiceDialogHelper.show(requireActivity(), titleRes,
                items, checked, onPick);
    }

    private void recreateActivity() {
        // 0.1.38：主题色/字号重建链埋点（只记事件）。
        com.gh4a.utils.DiagTrace.log(getActivity(), "SETTINGS", "recreate called");
        // 0.1.21 Item 1: recreate 不得在对话框点击回调里同步执行——单选助手是
        // 先 dismiss 再回调，窗口尚未完全摘掉时销毁 Activity 会在个别 ROM 上撞出
        // DecorView not attached to window manager（纯系统堆栈、无 App 帧）。
        // 改成 post 到主线程下一轮，等对话框窗口彻底摘掉再销毁；与
        // SettingsActivity.onFontScaleChanged 已有的写法一致。
        // 0.1.22：本方法为主题色/字号两个单选对话框与备份恢复路径的漏斗；
        // 语言改走整栈重启（见 restartStackOnMeTab）。0.1.31 起深色模式
        // 对话框也不再走它：0.1.27–0.1.30 取证证明 recreate()/relaunch
        // 重建路在本机已死（旧实例被系统侧重建销毁后交不出深色新实例），
        // 深色模式改走整栈重启（见 showDarkModeDialog）。runnable 执行时
        // 再判一次存活：系统侧重建可能已先销毁本 Activity，对已销毁实例
        // 再 recreate 会复现同款 DecorView 崩溃。
        android.app.Activity activity = getActivity();
        if (activity != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (!activity.isDestroyed() && !activity.isFinishing()) {
                    activity.recreate();
                }
            });
        }
    }

    private void showDarkModeDialog() {
        // 0.1.1：深色模式只剩跟随系统 / 浅色 / 深色，定时选项已下架；
        // 存量定时值仍在摘要里如实显示，点选任一新选项即覆盖它。
        CharSequence[] items = new CharSequence[] {
                getString(R.string.dark_mode_system),
                getString(R.string.dark_mode_light),
                getString(R.string.dark_mode_dark) };
        String mode = DarkModeScheduler.getMode(requireContext());
        int checked;
        if (DarkModeScheduler.MODE_SYSTEM.equals(mode)
                || DarkModeScheduler.MODE_SCHEDULED.equals(mode)) {
            checked = 0;
        } else {
            checked = mPrefs.getInt(SettingsFragment.KEY_THEME, 1) == 0 ? 2 : 1;
        }
        showSingleChoiceDialog(R.string.dark_mode, items, checked, which -> {
            Context ctx = requireContext();
            // 0.1.36：诊断追踪（只记不改行为）。
            com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_PICK which=" + which);
            switch (which) {
                case 0:
                    DarkModeScheduler.apply(ctx, DarkModeScheduler.MODE_SYSTEM);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_APPLY system");
                    // 0.1.33：跟随系统档此前根本没存模式（apply 不落盘），
                    // 整栈重启杀进程后模式会被打回旧值；此处同步落盘。
                    mPrefs.edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                            DarkModeScheduler.MODE_SYSTEM).commit();
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_COMMIT system");
                    // 0.1.35：点选直接定开关，不再只靠监听（0.1.34 录像证明：
                    // 跟随系统→浅色主题 1→1 不变，主题监听不触发，开关滞留跟随系统）。
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_SET system");
                    break;
                case 1:
                    DarkModeScheduler.apply(ctx, DarkModeScheduler.MODE_MANUAL);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_APPLY light");
                    // 0.1.33：模式与主题同一 editor 一次 commit() 同步落盘，
                    // 落盘完成才发重启——apply 只改内存，重启杀进程会丢值。
                    mPrefs.edit()
                            .putString(DarkModeScheduler.KEY_DARK_MODE,
                                    DarkModeScheduler.MODE_MANUAL)
                            .putInt(SettingsFragment.KEY_THEME, 1).commit();
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_COMMIT light");
                    // 0.1.35：同上，手动浅色直接定 NO，主题不变也不靠监听。
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_SET light");
                    break;
                default:
                    DarkModeScheduler.apply(ctx, DarkModeScheduler.MODE_MANUAL);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_APPLY dark");
                    // 0.1.33：同上，同步落盘后再重启。
                    mPrefs.edit()
                            .putString(DarkModeScheduler.KEY_DARK_MODE,
                                    DarkModeScheduler.MODE_MANUAL)
                            .putInt(SettingsFragment.KEY_THEME, 0).commit();
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_COMMIT dark");
                    // 0.1.35：手动深色直接定 YES。
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                    com.gh4a.utils.ThemeTrace.logState(ctx, "DIALOG_AFTER_SET dark");
                    break;
            }
            // 0.1.31：不再走 recreateActivity()。0.1.27–0.1.30 取证证明
            // recreate()/relaunch 重建路在本机已死：写偏好、监听、开关翻转
            // 全通，旧实例却被系统侧重建销毁后交不出深色新实例，等多久
            // 都不变。改走语言切换同款整栈重启（restartStackOnMeTab），
            // 整个任务清掉、起全新实例并落回我页，绕开这条死的重建路。
            restartStackOnMeTab();
        });
    }

    private void showAccentDialog() {
        String[] items = getResources().getStringArray(R.array.accent_color_items);
        String[] values = getResources().getStringArray(R.array.accent_color_values);
        String current = mPrefs.getString(SettingsFragment.KEY_ACCENT_COLOR, "green");
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        showSingleChoiceDialog(R.string.accent_color, items, checked, which -> {
            mPrefs.edit().putString(SettingsFragment.KEY_ACCENT_COLOR, values[which]).apply();
            recreateActivity();
        });
    }

    private void showFontSizeDialog() {
        String[] items = getResources().getStringArray(R.array.font_scale_items);
        String[] values = getResources().getStringArray(R.array.font_scale_values);
        String current = mPrefs.getString(SettingsFragment.KEY_FONT_SCALE, "1.0");
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        showSingleChoiceDialog(R.string.font_size, items, checked, which -> {
            mPrefs.edit().putString(SettingsFragment.KEY_FONT_SCALE, values[which]).apply();
            recreateActivity();
        });
    }

    private void showLanguageDialog() {
        String[] items = getResources().getStringArray(R.array.language_items);
        String[] values = getResources().getStringArray(R.array.language_values);
        String current = mPrefs.getString(SettingsFragment.KEY_LANGUAGE, "");
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        showSingleChoiceDialog(R.string.language, items, checked, which -> {
            mPrefs.edit().putString(SettingsFragment.KEY_LANGUAGE, values[which]).apply();
            SettingsFragment.applyLanguage(values[which]);
            restartStackOnMeTab();
        });
    }

    // 0.1.21 Item 2: 语言切换不再只靠单次 recreate()——底栏是在
    // HomeActivity.setupBottomNav() 里一次性 inflate 的，单次 recreate 后旧
    // locale 的底栏仍在位。改成重启整个任务栈（与设置页主题/语言切换注释
    // “restart stack to apply”一致），新 HomeActivity 在新 locale 下重建底栏；
    // 用 HomeActivity 已有的 "initial_page" 机制（"profile"）落回我页，不回默认
    // 起始页。同样 post 执行（Item 1 的对话框窗口竞态）。
    // 0.1.32：改从 Application Context 发——0.1.31 录像证明，setDefaultNightMode
    // 先排的系统侧重建可能已先销毁本 Activity，post 到下一轮再从已销毁
    // Activity 发 startActivity 会被系统丢掉，任务空了只剩桌面；应用上下文
    // 不依赖旧实例死活，NEW_TASK | CLEAR_TASK 本就要求新任务启动。
    private void restartStackOnMeTab() {
        android.app.Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        android.content.Intent intent = HomeActivity.makeIntent(activity, R.id.profile);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> {
                    // 0.1.36：诊断追踪。
                    com.gh4a.utils.ThemeTrace.logState(activity,
                            "RESTART_FIRED me inst=" + com.gh4a.utils.ThemeTrace.instance(activity));
                    activity.getApplicationContext().startActivity(intent);
                });
    }

    private void showStartPageDialog() {
        List<String[]> options = startPageOptions();
        CharSequence[] items = new CharSequence[options.size()];
        String current = mPrefs.getString(SettingsFragment.KEY_START_PAGE, "discover_hub");
        int checked = 0;
        for (int i = 0; i < options.size(); i++) {
            items[i] = options.get(i)[0];
            if (options.get(i)[1].equals(current)) checked = i;
        }
        showSingleChoiceDialog(R.string.start_page, items, checked, which -> {
            mPrefs.edit().putString(SettingsFragment.KEY_START_PAGE, options.get(which)[1]).apply();
            Toast.makeText(getActivity(), R.string.start_page_saved, Toast.LENGTH_SHORT).show();
            if (getView() != null) {
                // 刷新本页的起始页摘要（行是动态建的，重新建设置区最稳妥）
                rebuildSettingsRows();
            }
        });
    }

    private void rebuildSettingsRows() {
        View view = getView();
        if (view == null) return;
        for (int id : new int[] { R.id.me_settings_appearance, R.id.me_settings_browsing,
                R.id.me_settings_about }) {
            LinearLayout container = view.findViewById(id);
            if (container != null) container.removeAllViews();
        }
        buildSettingsRows(view);
    }

    /** 我页镜像源行：打开镜像源独立页（0.1.62 起不再弹对话框）。 */
    private void showMirrorSourcePage() {
        if (getActivity() == null) {
            return;
        }
        startActivity(new android.content.Intent(requireActivity(),
                com.gh4a.activities.MirrorSourceActivity.class));
    }

    private void showAboutDialog() {
        String version = "";
        try {
            android.content.pm.PackageInfo info = requireActivity().getPackageManager()
                    .getPackageInfo(requireActivity().getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
        }
        // 内联页的简化关于框（旧设置页的 AboutDialog 含账号相关入口，未整体搬运）
        new AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.app_name) + " v" + version)
                .setMessage(R.string.copyright_notice)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---------- backup / restore (same manager as old settings page) ----------

    private void showBackupRestoreDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.backup_restore)
                .setItems(new CharSequence[] {
                        getString(R.string.backup), getString(R.string.backup_restore_action) },
                        (dialog, which) -> {
                            if (which == 0) {
                                doBackup();
                            } else {
                                mRestoreLauncher.launch(new String[] { "application/json" });
                            }
                        })
                .show();
    }

    private void doBackup() {
        final Context appContext = requireActivity().getApplicationContext();
        final String savedFormat = getString(R.string.backup_saved);
        final String failedFormat = getString(R.string.backup_failed);
        final org.json.JSONObject backup;
        try {
            backup = com.gh4a.utils.SettingsBackupManager.collectBackup(appContext);
        } catch (Exception e) {
            showToast(String.format(failedFormat, e.getMessage()));
            return;
        }
        if (com.gh4a.utils.SettingsBackupManager.backupContainsTranslationCredentials(backup)) {
            new AlertDialog.Builder(requireActivity())
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

    private void writeBackupInBackground(final Context appContext,
            final org.json.JSONObject backup, final String savedFormat, final String failedFormat) {
        new Thread(() -> {
            try {
                String fileName = com.gh4a.utils.SettingsBackupManager.writeBackupFile(appContext, backup);
                showToast(savedFormat + ": " + fileName);
            } catch (Exception e) {
                showToast(String.format(failedFormat, e.getMessage()));
            }
        }).start();
    }

    private void confirmAndApplyRestore(final android.net.Uri uri) {
        new AlertDialog.Builder(requireActivity())
                .setMessage(R.string.restore_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> doRestore(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doRestore(final android.net.Uri uri) {
        final Context appContext = requireActivity().getApplicationContext();
        new Thread(() -> {
            try {
                org.json.JSONObject backup =
                        com.gh4a.utils.SettingsBackupManager.readBackupFile(appContext, uri);
                boolean ok = com.gh4a.utils.SettingsBackupManager.applyBackup(appContext, backup);
                // 125：后台线程直接 getString()，detach 后抛 IllegalStateException；
                // 改用 applicationContext 取文案
                showToast(ok ? appContext.getString(R.string.restore_success)
                        : appContext.getString(R.string.restore_failed));
                // 125：getActivity() 在判空与使用之间可能变 null，先取局部变量
                final android.app.Activity activity = getActivity();
                if (ok && activity != null) {
                    activity.runOnUiThread(this::recreateActivity);
                }
            } catch (Exception e) {
                showToast(appContext.getString(R.string.restore_failed));
            }
        }).start();
    }

    private void showToast(final String text) {
        // 125：先判 getActivity() != null、随后 lambda 里又读一次是 TOCTOU，
        // detach 后即 NPE；进 lambda 前取局部变量
        final android.app.Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() ->
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }
}
