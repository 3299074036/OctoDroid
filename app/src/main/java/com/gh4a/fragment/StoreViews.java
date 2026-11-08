package com.gh4a.fragment;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.UiUtils;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * 软件商店的共用行组件（StoreView 与详情／列表／提交／已下架页共用）。
 * 颜色全部走主题属性（colorPrimary、textColorPrimary/Secondary），深色模式可用。
 */
public class StoreViews {
    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int primaryColor(Context context) {
        return UiUtils.resolveColor(context, androidx.appcompat.R.attr.colorPrimary);
    }

    public static int primaryText(Context context) {
        return UiUtils.resolveColor(context, android.R.attr.textColorPrimary);
    }

    public static int secondaryText(Context context) {
        return UiUtils.resolveColor(context, android.R.attr.textColorSecondary);
    }

    public static int backgroundColor(Context context) {
        int color = UiUtils.resolveColor(context, android.R.attr.colorBackground);
        return color != 0 ? color : Color.WHITE;
    }

    public static int dividerColor(Context context) {
        int color = UiUtils.resolveColor(context, android.R.attr.colorControlHighlight);
        return color != 0 ? color : 0x1F000000;
    }

    public static String formatStars(int stars) {
        return NumberFormat.getInstance(Locale.US).format(stars);
    }

    public static TextView text(Context context, int sp, boolean bold) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        tv.setTextColor(primaryText(context));
        return tv;
    }

    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(dividerColor(context));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }

    /** 分区标题（16sp 粗体，上下留白）。 */
    public static TextView sectionHeader(Context context, String title) {
        TextView header = text(context, 16, true);
        header.setText(title);
        int side = dp(context, 16);
        header.setPadding(side, dp(context, 14), side, dp(context, 8));
        return header;
    }

    /** 居中的次要说明（加载中／空态／失败重试共用）。 */
    public static TextView infoText(Context context, String message) {
        TextView tv = text(context, 14, false);
        tv.setText(message);
        tv.setTextColor(secondaryText(context));
        tv.setGravity(Gravity.CENTER);
        int side = dp(context, 16);
        tv.setPadding(side, dp(context, 20), side, dp(context, 20));
        return tv;
    }

    /** 入口行（标题 + 副标题 + 右箭头，如提交仓库／已下架记录／镜像源设置）。 */
    public static View entryRow(Context context, String title, String subtitle,
            View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int side = dp(context, 16);
        row.setPadding(side, dp(context, 14), side, dp(context, 14));
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(listener);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView main = text(context, 15, false);
        main.setText(title);
        texts.addView(main);
        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = text(context, 13, false);
            sub.setText(subtitle);
            sub.setTextColor(secondaryText(context));
            texts.addView(sub);
        }
        row.addView(texts);
        TextView arrow = text(context, 18, false);
        arrow.setText("›");
        arrow.setTextColor(secondaryText(context));
        row.addView(arrow);
        return row;
    }

    /** 仓库行：名 + 描述 + ★／语言／版本 + 平台图标。 */
    public static View repoRow(Context context, StoreRepo item, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        int side = dp(context, 16);
        row.setPadding(side, dp(context, 12), side, dp(context, 12));
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(listener);
        TextView name = text(context, 16, true);
        name.setText(item.fullName());
        row.addView(name);
        if (!TextUtils.isEmpty(item.description)) {
            TextView desc = text(context, 14, false);
            desc.setText(item.description);
            desc.setTextColor(secondaryText(context));
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(desc);
        }
        StringBuilder meta = new StringBuilder("★ ").append(formatStars(item.stars()));
        String language = item.language;
        meta.append(" · ").append(language != null ? language
                : context.getString(R.string.unknown));
        if (!item.version().isEmpty()) {
            meta.append(" · ").append(item.version());
        }
        TextView metaView = text(context, 13, false);
        metaView.setText(meta.toString());
        metaView.setTextColor(secondaryText(context));
        row.addView(metaView);
        if (!item.platforms.isEmpty()) {
            LinearLayout icons = new LinearLayout(context);
            icons.setOrientation(LinearLayout.HORIZONTAL);
            icons.setPadding(0, dp(context, 8), 0, 0);
            boolean first = true;
            for (StoreClassifier.Platform platform : item.platforms) {
                if (!first) {
                    View gap = new View(context);
                    gap.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 10), 0));
                    icons.addView(gap);
                }
                first = false;
                icons.addView(platformIcon(context, platform));
            }
            row.addView(icons);
        }
        return row;
    }

    public static int platformIconRes(StoreClassifier.Platform platform) {
        switch (platform) {
            case ANDROID:
                return R.drawable.ic_store_android;
            case IOS:
                return R.drawable.ic_store_ios;
            case WINDOWS:
                return R.drawable.ic_store_windows;
            case MACOS:
                return R.drawable.ic_store_apple;
            case LINUX:
                return R.drawable.ic_store_linux;
            case HARMONY:
                return R.drawable.ic_store_harmony;
            case EXTENSION:
                return R.drawable.ic_store_extension;
            case ARCHIVE:
            default:
                // 0.1.66：压缩包行图标照 hiwiki 用六边形（与系统网格“其他”同款）。
                return R.drawable.ic_store_other;
        }
    }

    /** 列表行里的平台小图标（20dp，次要文字色）。 */
    public static ImageView platformIcon(Context context, StoreClassifier.Platform platform) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(platformIconRes(platform));
        icon.setColorFilter(secondaryText(context), PorterDuff.Mode.SRC_IN);
        icon.setContentDescription(platformLabel(context, platform));
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));
        return icon;
    }

    /** 按压涟漪（foreground 用，与 chip() 同款取法）。 */
    public static Drawable rippleDrawable(Context context) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId != 0 ? context.getDrawable(outValue.resourceId) : null;
    }

    /** 带图标的胶囊 chip（排序用，一行等权排布）：选中绿底白字，未选中描边 + 次要文字色。 */
    /** 纯文字 chip（0.1.61：排序 chips 去图标只留文字，四字全显不截断）。 */
    public static View textChip(Context context, String label,
            boolean selected, View.OnClickListener listener) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER);
        int padH = dp(context, 10);
        chip.setPadding(padH, dp(context, 6), padH, dp(context, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(context, 14));
        if (selected) {
            bg.setColor(primaryColor(context));
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(context, 1), secondaryText(context));
        }
        chip.setBackground(bg);
        chip.setForeground(rippleDrawable(context));
        chip.setClickable(true);
        chip.setOnClickListener(listener);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.setTypeface(null, Typeface.BOLD);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setTextColor(selected ? Color.WHITE : secondaryText(context));
        chip.addView(text);
        return chip;
    }

    public static final int TINT_AUTO = 0;
    public static final int TINT_BRAND = 1;
    public static final int TINT_NONE = 2;

    /** 图标网格的一格：图标 + 粗体名 + 灰色数；tintMode 决定图标着色。 */
    public static class IconCell {
        public final int iconRes;
        public final String name;
        public final int count;
        public final boolean selected;
        public final View.OnClickListener listener;
        public final int tintMode;

        public IconCell(int iconRes, String name, int count, boolean selected,
                View.OnClickListener listener, int tintMode) {
            this.iconRes = iconRes;
            this.name = name;
            this.count = count;
            this.selected = selected;
            this.listener = listener;
            this.tintMode = tintMode;
        }
    }

    /** 三列图标网格（系统/平台、用途分类用）：每行 3 格，选中格浅绿底。 */
    public static LinearLayout iconGrid(Context context, List<IconCell> cells) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int start = 0; start < cells.size(); start += 3) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = start; i < Math.min(start + 3, cells.size()); i++) {
                row.addView(iconCellView(context, cells.get(i)),
                        new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            // 末行不足 3 格时补透明占位格，固定 3 列，跨行列对齐
            // （否则 2 格的末行每格占 1/2 宽，"工具" 偏右）。
            for (int i = Math.min(start + 3, cells.size()); i < start + 3; i++) {
                View placeholder = new View(context);
                row.addView(placeholder, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            grid.addView(row);
        }
        return grid;
    }

    private static LinearLayout iconCellView(Context context, IconCell cell) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(context, 8));
        if (cell.selected) {
            // 主题色 14% 透明底（设计稿浅绿 #e8f5e3 的主题化等价）。
            bg.setColor((primaryColor(context) & 0x00FFFFFF) | (36 << 24));
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        view.setBackground(bg);
        view.setForeground(rippleDrawable(context));
        view.setClickable(true);
        view.setOnClickListener(cell.listener);
        ImageView icon = new ImageView(context);
        icon.setImageResource(cell.iconRes);
        if (cell.tintMode == TINT_BRAND) {
            icon.setColorFilter(primaryColor(context), PorterDuff.Mode.SRC_IN);
        } else if (cell.tintMode == TINT_AUTO) {
            icon.setColorFilter(cell.selected ? primaryColor(context) : primaryText(context),
                    PorterDuff.Mode.SRC_IN);
        }
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));
        view.addView(icon);
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 6), 0));
        view.addView(gap);
        TextView name = new TextView(context);
        name.setText(cell.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(cell.selected ? primaryColor(context) : primaryText(context));
        // 名字占剩余宽度（weight 1），数字总有位置，不再被挤掉。
        view.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View gap2 = new View(context);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 4), 0));
        view.addView(gap2);
        TextView count = new TextView(context);
        count.setText(String.valueOf(cell.count));
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setSingleLine(true);
        count.setEllipsize(TextUtils.TruncateAt.END);
        count.setTextColor(secondaryText(context));
        view.addView(count);
        return view;
    }

    public static String platformLabel(Context context, StoreClassifier.Platform platform) {
        switch (platform) {
            case ANDROID:
                return "APK";
            case IOS:
                return "IPA";
            case WINDOWS:
                return context.getString(R.string.store_platform_windows);
            case MACOS:
                return context.getString(R.string.store_platform_macos);
            case LINUX:
                return context.getString(R.string.store_platform_linux);
            case HARMONY:
                return context.getString(R.string.store_platform_harmony);
            case EXTENSION:
                return context.getString(R.string.store_platform_extension);
            case ARCHIVE:
            default:
                return context.getString(R.string.store_platform_archive);
        }
    }

    public static String platformName(Context context, StoreClassifier.Platform platform) {
        switch (platform) {
            case ANDROID:
                return "Android";
            case IOS:
                return "iOS";
            case WINDOWS:
                return context.getString(R.string.store_platform_windows);
            case MACOS:
                return context.getString(R.string.store_platform_macos);
            case LINUX:
                return context.getString(R.string.store_platform_linux);
            case HARMONY:
                return context.getString(R.string.store_platform_harmony);
            case EXTENSION:
                return context.getString(R.string.store_platform_extension);
            case ARCHIVE:
            default:
                return context.getString(R.string.store_platform_archive);
        }
    }

    /** 安装指南文案（按附件格式）。 */
    public static int guideResForFormat(String format) {
        switch (format) {
            case "apk":
                return R.string.store_guide_apk;
            case "ipa":
                return R.string.store_guide_ipa;
            case "exe":
                return R.string.store_guide_exe;
            case "dmg":
                return R.string.store_guide_dmg;
            case "pkg":
                return R.string.store_guide_pkg;
            case "appimage":
                return R.string.store_guide_appimage;
            case "deb":
                return R.string.store_guide_deb;
            case "rpm":
                return R.string.store_guide_rpm;
            case "zip":
            default:
                return R.string.store_guide_zip;
        }
    }
}
