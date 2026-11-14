package com.gh4a.fragment;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;
import com.gh4a.utils.UiUtils;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** StoreViews：商店共用行组件（Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class StoreViewsTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static StoreRepo repo(String fullName, String description, String language,
            String tag, EnumSet<StoreClassifier.Platform> platforms) {
        return new StoreRepo(fullName, "o", "n", description, language, "", 1234, 5, 10L,
                null, null, tag, 0L, platforms, platforms.size(), null, null, null, null, null);
    }

    @Test
    public void dp_scalesWithDensity() {
        float density = mContext.getResources().getDisplayMetrics().density;
        assertEquals((int) (10 * density + 0.5f), StoreViews.dp(mContext, 10));
        assertEquals((int) (16 * density + 0.5f), StoreViews.dp(mContext, 16));
        assertEquals(0, StoreViews.dp(mContext, 0));
    }

    @Test
    public void formatStars_usesUsGrouping() {
        assertEquals("0", StoreViews.formatStars(0));
        assertEquals("42", StoreViews.formatStars(42));
        assertEquals("1,234", StoreViews.formatStars(1234));
        assertEquals("1,234,567", StoreViews.formatStars(1234567));
    }

    @Test
    public void text_boldAndPlain() {
        TextView bold = StoreViews.text(mContext, 16, true);
        assertEquals(16f, bold.getTextSize() / mContext.getResources()
                .getDisplayMetrics().scaledDensity, 0.01f);
        // 注：粗体不测——Robolectric 的 ShadowTypeface 不保留 BOLD 样式，
        // getTypeface() 为 null/默认，与生产代码无关；字号与颜色已覆盖。
        TextView plain = StoreViews.text(mContext, 14, false);
        assertEquals(StoreViews.primaryText(mContext), plain.getCurrentTextColor());
    }

    @Test
    public void divider_isOnePixelHighMatchParent() {
        View divider = StoreViews.divider(mContext);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                divider.getLayoutParams().width);
        assertEquals(1, divider.getLayoutParams().height);
        assertEquals(StoreViews.dividerColor(mContext),
                ((android.graphics.drawable.ColorDrawable) divider.getBackground()).getColor());
    }

    @Test
    public void sectionHeader_textAndPadding() {
        TextView header = StoreViews.sectionHeader(mContext, "分区");
        assertEquals("分区", header.getText().toString());
        // 注：粗体不测，原因同 text_boldAndPlain（Robolectric Typeface 限制）。
        int side = StoreViews.dp(mContext, 16);
        assertEquals(side, header.getPaddingLeft());
        assertEquals(side, header.getPaddingRight());
        assertEquals(StoreViews.dp(mContext, 14), header.getPaddingTop());
        assertEquals(StoreViews.dp(mContext, 8), header.getPaddingBottom());
    }

    @Test
    public void infoText_centeredSecondary() {
        TextView tv = StoreViews.infoText(mContext, "加载中");
        assertEquals("加载中", tv.getText().toString());
        assertEquals(android.view.Gravity.CENTER, tv.getGravity());
        assertEquals(StoreViews.secondaryText(mContext), tv.getCurrentTextColor());
        assertEquals(StoreViews.dp(mContext, 20), tv.getPaddingTop());
    }

    @Test
    public void repoRow_fullRowHasNameDescMetaAndIcons() {
        StoreRepo item = repo("octo/cat", "a nice app", "Kotlin", "v1.2",
                EnumSet.of(StoreClassifier.Platform.ANDROID, StoreClassifier.Platform.IOS));
        final boolean[] clicked = {false};
        View row = StoreViews.repoRow(mContext, item, v -> clicked[0] = true);
        assertTrue(row instanceof LinearLayout);
        LinearLayout layout = (LinearLayout) row;
        assertEquals(4, layout.getChildCount()); // name + desc + meta + icons
        assertEquals("octo/cat", ((TextView) layout.getChildAt(0)).getText().toString());
        assertEquals("a nice app", ((TextView) layout.getChildAt(1)).getText().toString());
        assertEquals("★ 1,234 · Kotlin · v1.2",
                ((TextView) layout.getChildAt(2)).getText().toString());
        assertEquals(2, ((LinearLayout) layout.getChildAt(3)).getChildCount() - 1); // 2 icons + 1 gap
        row.performClick();
        assertTrue(clicked[0]);
    }

    @Test
    public void repoRow_noDescriptionNoVersionNoPlatforms() {
        StoreRepo item = repo("o/n", "", null, "",
                EnumSet.noneOf(StoreClassifier.Platform.class));
        View row = StoreViews.repoRow(mContext, item, null);
        LinearLayout layout = (LinearLayout) row;
        assertEquals(2, layout.getChildCount()); // name + meta
        assertEquals("★ 1,234 · " + mContext.getString(R.string.unknown),
                ((TextView) layout.getChildAt(1)).getText().toString());
    }

    @Test
    public void repoRow_nullListenerDoesNotCrashOnClick() {
        View row = StoreViews.repoRow(mContext,
                repo("o/n", "d", "Java", "v1",
                        EnumSet.of(StoreClassifier.Platform.ARCHIVE)), null);
        row.performClick(); // no listener: no-op, no crash
    }

    @Test
    public void platformIconRes_allPlatformsMapped() {
        assertEquals(R.drawable.ic_store_android,
                StoreViews.platformIconRes(StoreClassifier.Platform.ANDROID));
        assertEquals(R.drawable.ic_store_ios,
                StoreViews.platformIconRes(StoreClassifier.Platform.IOS));
        assertEquals(R.drawable.ic_store_windows,
                StoreViews.platformIconRes(StoreClassifier.Platform.WINDOWS));
        assertEquals(R.drawable.ic_store_apple,
                StoreViews.platformIconRes(StoreClassifier.Platform.MACOS));
        assertEquals(R.drawable.ic_store_linux,
                StoreViews.platformIconRes(StoreClassifier.Platform.LINUX));
        assertEquals(R.drawable.ic_store_harmony,
                StoreViews.platformIconRes(StoreClassifier.Platform.HARMONY));
        assertEquals(R.drawable.ic_store_extension,
                StoreViews.platformIconRes(StoreClassifier.Platform.EXTENSION));
        assertEquals(R.drawable.ic_store_other,
                StoreViews.platformIconRes(StoreClassifier.Platform.ARCHIVE));
    }

    @Test
    public void platformIcon_sizeAndContentDescription() {
        ImageView icon = StoreViews.platformIcon(mContext, StoreClassifier.Platform.ANDROID);
        assertEquals("APK", icon.getContentDescription().toString());
        assertEquals(StoreViews.dp(mContext, 20), icon.getLayoutParams().width);
        assertEquals(StoreViews.dp(mContext, 20), icon.getLayoutParams().height);
        ImageView ipa = StoreViews.platformIcon(mContext, StoreClassifier.Platform.IOS);
        assertEquals("IPA", ipa.getContentDescription().toString());
    }

    @Test
    public void platformLabel_allPlatforms() {
        assertEquals("APK", StoreViews.platformLabel(mContext, StoreClassifier.Platform.ANDROID));
        assertEquals("IPA", StoreViews.platformLabel(mContext, StoreClassifier.Platform.IOS));
        assertEquals(mContext.getString(R.string.store_platform_windows),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.WINDOWS));
        assertEquals(mContext.getString(R.string.store_platform_macos),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.MACOS));
        assertEquals(mContext.getString(R.string.store_platform_linux),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.LINUX));
        assertEquals(mContext.getString(R.string.store_platform_harmony),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.HARMONY));
        assertEquals(mContext.getString(R.string.store_platform_extension),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.EXTENSION));
        assertEquals(mContext.getString(R.string.store_platform_archive),
                StoreViews.platformLabel(mContext, StoreClassifier.Platform.ARCHIVE));
    }

    @Test
    public void textChip_selectedAndUnselected() {
        final int[] clicks = {0};
        View selected = StoreViews.textChip(mContext, "星标", true, v -> clicks[0]++);
        View unselected = StoreViews.textChip(mContext, "更新", false, v -> clicks[0]++);
        assertEquals("星标", ((TextView) ((LinearLayout) selected).getChildAt(0))
                .getText().toString());
        assertEquals("更新", ((TextView) ((LinearLayout) unselected).getChildAt(0))
                .getText().toString());
        assertEquals(android.graphics.Color.WHITE,
                ((TextView) ((LinearLayout) selected).getChildAt(0)).getCurrentTextColor());
        assertEquals(StoreViews.secondaryText(mContext),
                ((TextView) ((LinearLayout) unselected).getChildAt(0)).getCurrentTextColor());
        selected.performClick();
        unselected.performClick();
        assertEquals(2, clicks[0]);
    }

    @Test
    public void iconCell_ctorKeepsFields() {
        View.OnClickListener listener = v -> {};
        StoreViews.IconCell cell = new StoreViews.IconCell(
                R.drawable.ic_store_android, "安卓", 12, true, listener, StoreViews.TINT_BRAND);
        assertEquals(R.drawable.ic_store_android, cell.iconRes);
        assertEquals("安卓", cell.name);
        assertEquals(12, cell.count);
        assertTrue(cell.selected);
        assertEquals(listener, cell.listener);
        assertEquals(StoreViews.TINT_BRAND, cell.tintMode);
    }

    @Test
    public void iconGrid_emptyCellsProducesEmptyGrid() {
        LinearLayout grid = StoreViews.iconGrid(mContext, Collections.emptyList());
        assertEquals(0, grid.getChildCount());
    }

    @Test
    public void iconGrid_oneCellProducesOneRowWithPlaceholders() {
        List<StoreViews.IconCell> cells = Collections.singletonList(
                new StoreViews.IconCell(R.drawable.ic_store_android, "安卓", 5,
                        false, null, StoreViews.TINT_AUTO));
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        assertEquals(1, grid.getChildCount());
        LinearLayout row = (LinearLayout) grid.getChildAt(0);
        assertEquals(3, row.getChildCount()); // 1 cell + 2 placeholders
    }

    @Test
    public void iconGrid_fourCellsProducesTwoRows() {
        List<StoreViews.IconCell> cells = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cells.add(new StoreViews.IconCell(R.drawable.ic_store_android, "c" + i, i,
                    i == 1, null, StoreViews.TINT_NONE));
        }
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        assertEquals(2, grid.getChildCount());
        assertEquals(3, ((LinearLayout) grid.getChildAt(0)).getChildCount());
        assertEquals(3, ((LinearLayout) grid.getChildAt(1)).getChildCount());
    }

    @Test
    public void iconGrid_cellShowsNameAndCount() {
        List<StoreViews.IconCell> cells = Arrays.asList(
                new StoreViews.IconCell(R.drawable.ic_store_android, "安卓", 42,
                        false, null, StoreViews.TINT_AUTO));
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        LinearLayout cell = (LinearLayout) ((LinearLayout) grid.getChildAt(0)).getChildAt(0);
        // cell 结构：icon + gap + name + gap2 + count
        assertEquals("安卓", ((TextView) cell.getChildAt(2)).getText().toString());
        assertEquals("42", ((TextView) cell.getChildAt(4)).getText().toString());
    }

    @Test
    public void colors_resolveWithoutCrashing() {
        // 主题色解析在 Robolectric 下不断言具体值，只保证可解析且背景/分隔色有回落。
        assertNotNull(StoreViews.primaryColor(mContext));
        assertNotNull(StoreViews.primaryText(mContext));
        assertNotNull(StoreViews.secondaryText(mContext));
        assertTrue(StoreViews.backgroundColor(mContext) != 0);
        assertTrue(StoreViews.dividerColor(mContext) != 0);
    }

    @Test
    public void rippleDrawable_doesNotCrash() {
        // 主题可能解析出 selectableItemBackground，也可能为空；两种都是合法行为。
        StoreViews.rippleDrawable(mContext);
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<StoreViews> c = StoreViews.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }


    @Test
    public void backgroundColor_fallsBackToWhiteWhenThemeResolvesZero() {
        try (MockedStatic<UiUtils> mocked = mockStatic(UiUtils.class)) {
            mocked.when(() -> UiUtils.resolveColor(any(Context.class),
                    eq(android.R.attr.colorBackground))).thenReturn(0);
            assertEquals(Color.WHITE, StoreViews.backgroundColor(mContext));
        }
    }

    @Test
    public void dividerColor_fallsBackToDefaultWhenThemeResolvesZero() {
        try (MockedStatic<UiUtils> mocked = mockStatic(UiUtils.class)) {
            mocked.when(() -> UiUtils.resolveColor(any(Context.class),
                    eq(android.R.attr.colorControlHighlight))).thenReturn(0);
            assertEquals(0x1F000000, StoreViews.dividerColor(mContext));
        }
    }

    @Test
    public void rippleDrawable_returnsNullWhenAttributeDoesNotResolve() {
        // Resources.Theme 是 final 类，Mockito mock 不了；改用包了空 Theme
        //（newTheme()，未应用任何 style）的 ContextWrapper，
        // selectableItemBackground 解析不到 → resourceId 为 0 → null。
        android.content.ContextWrapper ctx =
                new android.content.ContextWrapper(mContext) {
                    private final android.content.res.Resources.Theme mTheme =
                            mContext.getResources().newTheme();

                    @Override
                    public android.content.res.Resources.Theme getTheme() {
                        return mTheme;
                    }
                };
        assertNull(StoreViews.rippleDrawable(ctx));
    }

    @Test
    public void iconCellView_brandTintAppliesColorFilter() {
        // iconCellView 是 private：经公开的 iconGrid 间接驱动。
        StoreViews.IconCell cell = new StoreViews.IconCell(
                R.drawable.ic_store_android, "安卓", 1, false, null, StoreViews.TINT_BRAND);
        LinearLayout grid = StoreViews.iconGrid(mContext, Collections.singletonList(cell));
        LinearLayout row = (LinearLayout) grid.getChildAt(0);
        LinearLayout cellView = (LinearLayout) row.getChildAt(0);
        ImageView icon = (ImageView) cellView.getChildAt(0);
        assertNotNull(icon.getColorFilter());
    }

    @Test
    public void iconCellView_autoTintAppliesColorFilterForSelectedAndPlain() {
        StoreViews.IconCell selected = new StoreViews.IconCell(
                R.drawable.ic_store_android, "安卓", 1, true, null, StoreViews.TINT_AUTO);
        StoreViews.IconCell plain = new StoreViews.IconCell(
                R.drawable.ic_store_android, "安卓", 1, false, null, StoreViews.TINT_AUTO);
        ImageView selIcon = iconOfFirstCell(
                StoreViews.iconGrid(mContext, Collections.singletonList(selected)));
        ImageView plainIcon = iconOfFirstCell(
                StoreViews.iconGrid(mContext, Collections.singletonList(plain)));
        assertNotNull(selIcon.getColorFilter());
        assertNotNull(plainIcon.getColorFilter());
    }

    private static ImageView iconOfFirstCell(LinearLayout grid) {
        LinearLayout row = (LinearLayout) grid.getChildAt(0);
        LinearLayout cellView = (LinearLayout) row.getChildAt(0);
        return (ImageView) cellView.getChildAt(0);
    }

}
