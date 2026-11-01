package com.gh4a.fragment;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.UiUtils;

import java.text.NumberFormat;
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

    /** 平台徽章（描边小圆角，如 APK / EXE / DMG）。 */
    public static TextView badge(Context context, String label) {
        TextView badge = new TextView(context);
        badge.setText(label);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setTextColor(secondaryText(context));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(context, 4));
        bg.setStroke(dp(context, 1), secondaryText(context));
        badge.setBackground(bg);
        badge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        return badge;
    }

    /** 筛选 chip（选中为主题色实底白字，未选中为描边）。 */
    public static TextView chip(Context context, String label, boolean selected,
            View.OnClickListener listener) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(context, 20));
        if (selected) {
            bg.setColor(primaryColor(context));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setStroke(dp(context, 1), 0xFFBDBDBD);
            bg.setColor(Color.TRANSPARENT);
            chip.setTextColor(secondaryText(context));
        }
        chip.setBackground(bg);
        chip.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        // 背景与按压反馈分层（Komi 同款思路）：pill 背景留在 background，
        // ripple 放 foreground。原来这里调 setSelectableBackground，会把
        // pill 背景整个换成透明 ripple，选中态白字落在透明底上就隐身了。
        // minSdk 23，View.setForeground 与 Context.getDrawable 均可用。
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true);
        if (outValue.resourceId != 0) {
            chip.setForeground(context.getDrawable(outValue.resourceId));
        }
        chip.setOnClickListener(listener);
        return chip;
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

    /** 仓库行：名 + 描述 + ★／语言／版本 + 平台徽章。 */
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
            FlowLayout badges = new FlowLayout(context);
            badges.setPadding(0, dp(context, 8), 0, 0);
            for (StoreClassifier.Platform platform : item.platforms) {
                TextView badge = badge(context, platformLabel(context, platform));
                FlowLayout.LayoutParams lp = new FlowLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, dp(context, 6), dp(context, 6));
                badges.addView(badge, lp);
            }
            row.addView(badges);
        }
        return row;
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

    /** 自动换行的容器（徽章行、标签云用）。 */
    public static class FlowLayout extends ViewGroup {
        public FlowLayout(Context context) {
            super(context);
        }

        public static class LayoutParams extends MarginLayoutParams {
            public LayoutParams(int width, int height) {
                super(width, height);
            }
        }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        @Override
        public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }

        @Override
        protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
            return new MarginLayoutParams(p);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int paddingH = getPaddingLeft() + getPaddingRight();
            int paddingV = getPaddingTop() + getPaddingBottom();
            int lineWidth = 0;
            int lineHeight = 0;
            int totalHeight = paddingV;
            int maxLineWidth = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                        paddingH + lp.leftMargin + lp.rightMargin, lp.width);
                int childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                        paddingV + lp.topMargin + lp.bottomMargin, lp.height);
                child.measure(childWidthSpec, childHeightSpec);
                int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (lineWidth > 0 && lineWidth + childWidth > width - paddingH) {
                    totalHeight += lineHeight;
                    maxLineWidth = Math.max(maxLineWidth, lineWidth);
                    lineWidth = 0;
                    lineHeight = 0;
                }
                lineWidth += childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
            totalHeight += lineHeight;
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
            setMeasuredDimension(
                    resolveSize(maxLineWidth + paddingH, widthMeasureSpec),
                    resolveSize(totalHeight, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int x = getPaddingLeft();
            int y = getPaddingTop();
            int lineHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (x > getPaddingLeft() && x + childWidth > width - getPaddingRight()) {
                    x = getPaddingLeft();
                    y += lineHeight;
                    lineHeight = 0;
                }
                child.layout(x + lp.leftMargin, y + lp.topMargin,
                        x + lp.leftMargin + child.getMeasuredWidth(),
                        y + lp.topMargin + child.getMeasuredHeight());
                x += childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }
    }
}
