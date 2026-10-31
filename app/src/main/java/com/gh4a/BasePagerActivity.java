package com.gh4a;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import android.widget.EdgeEffect;
import android.widget.LinearLayout;

import com.gh4a.utils.UiUtils;

import java.lang.reflect.Field;

public abstract class BasePagerActivity extends BaseActivity implements
        ViewPager.OnPageChangeListener {
    private PagerAdapter mAdapter;
    private TabLayout mTabs;
    private ViewPager mPager;
    private int[][] mTabHeaderColors;
    private boolean mScrolling;
    private boolean mErrorViewVisible;
    private int mCurrentHeaderColor;
    private int mLastTabCount;
    // 0.1.11：白底 Tab 行用到的原始样式（首次切入白底模式时抓拍，
    // 切回其他页时原样恢复，不动 tab_bar.xml 的默认样式）。
    private Drawable mTabRowOriginalBackground;
    private ColorStateList mTabRowOriginalTextColors;
    private int mTabRowOriginalIndicatorColor;
    private boolean mTabRowOriginalCaptured;

    // 0.1.11：子类钩子。默认 false（保持原有折叠行为）；动态页
    // （HomeActivity 的 FeedHubFactory）重写为 true，使标题栏永不滚动收起。
    protected boolean isToolbarFixed() {
        return false;
    }

    // 0.1.11：子类钩子。默认 false；动态页重写为 true，使关注/公开
    // Tab 行以白底行出现在绿标题栏下方（趋势时间范围行的样式），而非绿栏内。
    protected boolean useWhiteTabRow() {
        return false;
    }

    // 0.1.17：手指切页开关钩子。默认 true（标准 ViewPager 行为，
    // 仓库/事项等详情页的 Tab 横滑不受影响）。HomeActivity 重写：
    // 只有当前工厂多于 1 个 Tab（动态 关注/公开）时才允许手指切页；
    // 单 Tab 工厂（趋势/Star/搜索/我）关闭手指切页，否则 ViewPager
    // 会在列表收到松手前先拦截横拖，页内左右滑永远算不出位移。
    protected boolean isPagerTouchPagingEnabled() {
        return true;
    }

    private void applyPagerTouchPaging() {
        // setupPager 内调用时 mPager 尚未赋值（调用方用返回值赋值），
        // 故这里不依赖 mPager 字段；setupPager 直接对局部 pager 应用。
        applyPagerTouchPaging(mPager);
    }

    private void applyPagerTouchPaging(ViewPager pager) {
        if (pager instanceof com.gh4a.widget.LockableViewPager) {
            ((com.gh4a.widget.LockableViewPager) pager)
                    .setPagingEnabled(isPagerTouchPagingEnabled());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_pager);
        mPager = setupPager();

        mCurrentHeaderColor = UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary);
        updateTabHeaderColors();
        updateTabVisibility();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        onPageMoved(0, 0);
    }

    protected void adjustTabsForHeaderAlignedFab(boolean fabPresent) {
        int margin = fabPresent
                ? getResources().getDimensionPixelSize(R.dimen.mini_fab_size_with_margin) : 0;

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mTabs.getLayoutParams();
        lp.rightMargin = margin;
        mTabs.setLayoutParams(lp);
    }

    protected void invalidatePages() {
        mAdapter.notifyDataSetChanged();
        updateTabVisibility();
    }

    protected void invalidateTabs() {
        invalidatePages();
        applyPagerTouchPaging();
        updateTabHeaderColors();
        if (mTabHeaderColors != null) {
            onPageMoved(0, 0);
        } else {
            int[] colorAttrs = getHeaderColorAttrs();
            if (colorAttrs != null) {
                transitionHeaderToColor(colorAttrs[0], colorAttrs[1]);
            } else {
                transitionHeaderToColor(androidx.appcompat.R.attr.colorPrimary, androidx.appcompat.R.attr.colorPrimaryDark);
            }
        }
        tryUpdatePagerColor();
    }

    protected ViewPager getPager() {
        return mPager;
    }

    protected boolean hasTabsInToolbar() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    protected void setErrorViewVisibility(boolean visible, Throwable e) {
        mErrorViewVisible = visible;
        updateTabVisibility();
        super.setErrorViewVisibility(visible, e);
    }

    private void updateTabHeaderColors() {
        int[][] colorAttrs = getTabHeaderColorAttrs();
        if (colorAttrs == null) {
            mTabHeaderColors = null;
            return;
        }

        mTabHeaderColors = new int[colorAttrs.length][2];
        for (int i = 0; i < mTabHeaderColors.length; i++) {
            mTabHeaderColors[i][0] = UiUtils.resolveColor(this, colorAttrs[i][0]);
            mTabHeaderColors[i][1] = UiUtils.resolveColor(this, colorAttrs[i][1]);
        }
    }

    private ViewPager setupPager() {
        ViewPager pager = findViewById(R.id.pager);
        mAdapter = createAdapter(pager);
        pager.setAdapter(mAdapter);
        pager.addOnPageChangeListener(this);
        applyPagerTouchPaging(pager);

        mTabs = (TabLayout) getLayoutInflater().inflate(R.layout.tab_bar, null);
        mTabs.setupWithViewPager(pager);

        if (hasTabsInToolbar()) {
            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.addView(mTabs, new Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.MATCH_PARENT));
        } else {
            addHeaderView(mTabs, false);
        }

        return pager;
    }

    private void updateToolbarScrollableState() {
        setToolbarScrollable(!isToolbarFixed()
                && mAdapter.getCount() > 1 && !hasTabsInToolbar());
    }

    // 0.1.13：白 Tab 行（动态页）各 Tab 视图的左右滑监听。只在白底模式下挂上，
    // 切回其他页时在 applyTabRowStyle 的 else 分支里摘掉，避免滑手势漏到别的页。
    // 0.1.16：fling 判定改距离判定——慢速拖动松手不产生 fling，真机上原判定没反应。
    private void attachTabRowSwipe() {
        if (mTabs == null || mTabs.getChildCount() == 0 || mPager == null) return;
        android.view.ViewGroup tabStrip = (android.view.ViewGroup) mTabs.getChildAt(0);
        android.view.View.OnTouchListener swipeListener =
                new com.gh4a.utils.HorizontalSwipeListener(
                        new com.gh4a.utils.HorizontalSwipeListener.Callback() {
                            @Override
                            public void onSwipeLeft() {
                                if (mPager.getAdapter() == null) return;
                                int next = mPager.getCurrentItem() + 1;
                                if (next < mPager.getAdapter().getCount()) {
                                    mPager.setCurrentItem(next);
                                }
                            }

                            @Override
                            public void onSwipeRight() {
                                int next = mPager.getCurrentItem() - 1;
                                if (next >= 0) {
                                    mPager.setCurrentItem(next);
                                }
                            }
                        });
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            tabStrip.getChildAt(i).setOnTouchListener(swipeListener);
        }
    }

    private void detachTabRowSwipe() {
        if (mTabs == null || mTabs.getChildCount() == 0) return;
        android.view.ViewGroup tabStrip = (android.view.ViewGroup) mTabs.getChildAt(0);
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            tabStrip.getChildAt(i).setOnTouchListener(null);
        }
    }

    // 0.1.11：按当前页应用 Tab 行样式。白底模式：白底 + 选中主色绿字/
    // 绿下划线 + 未选中灰字，标题栏同时重新展开；其他页：恢复抓拍的原始样式。
    private void applyTabRowStyle() {
        if (mTabs == null) return;
        boolean whiteRow = useWhiteTabRow() && mAdapter.getCount() > 1 && !mErrorViewVisible;
        if (whiteRow) {
            if (!mTabRowOriginalCaptured) {
                mTabRowOriginalBackground = mTabs.getBackground();
                mTabRowOriginalTextColors = mTabs.getTabTextColors();
                // 0.1.20 F-26: 本版 Material 的 TabLayout 无 indicator getter，
                // 无法从已展开的 tab_bar 布局实测——此处是显式假设而非实测值：
                // 原始值即 tab_bar.xml 里的 app:tabIndicatorColor
                // (@color/tab_indicator_color)。若该 XML 改动，本行须同步改。
                mTabRowOriginalIndicatorColor =
                        ContextCompat.getColor(this, R.color.tab_indicator_color);
                mTabRowOriginalCaptured = true;
            }
            mTabs.setBackgroundColor(
                    UiUtils.resolveColor(this, android.R.attr.colorBackground));
            mTabs.setTabTextColors(
                    UiUtils.resolveColor(this, android.R.attr.textColorSecondary),
                    UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary));
            mTabs.setSelectedTabIndicatorColor(
                    UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary));
            expandAppBar();
            // 0.1.13：白 Tab 行本身也支持左右滑（动态 关注/公开）。TabView 的
            // OnTouchListener 永不消费事件，点按仍走 TabLayout 自身的选择逻辑；
            // 只有符合阈值的主水平拖动（松手位移）才切相邻页，首末页不回绕。
            attachTabRowSwipe();
        } else if (mTabRowOriginalCaptured) {
            // 0.1.13：离开白底模式时摘掉 Tab 行左右滑，避免手势漏到别的页。
            detachTabRowSwipe();
            mTabs.setBackground(mTabRowOriginalBackground);
            mTabs.setTabTextColors(mTabRowOriginalTextColors);
            mTabs.setSelectedTabIndicatorColor(mTabRowOriginalIndicatorColor);
        }
    }

    private void updateTabVisibility() {
        int count = mAdapter.getCount();

        if (count != mLastTabCount) {
            mAdapter.notifyDataSetChanged();
            mLastTabCount = count;
        }

        // We never have many pages, make sure to keep them all alive
        mPager.setOffscreenPageLimit(Math.max(1, count - 1));

        mTabs.setVisibility(count > 1 && !mErrorViewVisible ? View.VISIBLE : View.GONE);
        updateToolbarScrollableState();
        applyTabRowStyle();

        LinearLayout tabStrip = (LinearLayout) mTabs.getChildAt(0);
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            View tab = tabStrip.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tab.getLayoutParams();
            lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            lp.weight = 1;
            tab.setLayoutParams(lp);
        }
    }

    @Override
    protected void setHeaderColor(int color, int statusBarColor) {
        super.setHeaderColor(color, statusBarColor);
        mCurrentHeaderColor = color;
    }

    @Override
    protected void transitionHeaderToColor(int colorAttrId, int statusBarColorAttrId) {
        super.transitionHeaderToColor(colorAttrId, statusBarColorAttrId);
        mCurrentHeaderColor = UiUtils.resolveColor(this, colorAttrId);
    }

    protected abstract PagerAdapter createAdapter(ViewGroup root);

    /* expected format: int[tabCount][2] - 0 is header, 1 is status bar */
    protected int[][] getTabHeaderColorAttrs() {
        return null;
    }

    /* expected format: int[2] - 0 is header, 1 is status bar */
    protected int[] getHeaderColorAttrs() {
        return null;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        onPageMoved(position, positionOffset);
    }

    @Override
    public void onPageSelected(int position) {
        if (!mScrolling) {
            onPageMoved(position, 0);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mScrolling = state != ViewPager.SCROLL_STATE_IDLE;
        if (!mScrolling) {
            tryUpdatePagerColor();
        }
    }

    protected void onPageMoved(int position, float fraction) {
        if (mTabHeaderColors != null) {
            // 0.1.20 F-18: position 本身也要钳制（此前只钳制 nextIndex），
            // 页数与颜色数未来不一致时滑动不再越界。
            int index = Math.max(0, Math.min(position, mTabHeaderColors.length - 1));
            int nextIndex = Math.max(0, Math.min(index + 1, mTabHeaderColors.length - 1));
            int headerColor = UiUtils.mixColors(mTabHeaderColors[index][0],
                    mTabHeaderColors[nextIndex][0], fraction);
            int statusBarColor = UiUtils.mixColors(mTabHeaderColors[index][1],
                    mTabHeaderColors[nextIndex][1], fraction);
            setHeaderColor(headerColor, statusBarColor);
        }
    }

    private void tryUpdatePagerColor() {
        ViewPagerEdgeColorHelper helper =
                (ViewPagerEdgeColorHelper) mPager.getTag(R.id.EdgeColorHelper);
        if (helper == null) {
            helper = new ViewPagerEdgeColorHelper(mPager);
            mPager.setTag(R.id.EdgeColorHelper, helper);
        }
        helper.setColor(mCurrentHeaderColor);
    }

    private static class ViewPagerEdgeColorHelper {
        private final ViewPager mPager;
        private int mColor;
        private EdgeEffect mLeftEffect, mRightEffect;

        private static Field sLeftEffectField, sRightEffectField;

        public ViewPagerEdgeColorHelper(ViewPager pager) {
            mPager = pager;
            mColor = 0;
        }
        public void setColor(int color) {
            mColor = color;
            applyIfPossible();
        }

        private void applyIfPossible() {
            if (!ensureStaticFields()) {
                return;
            }
            if (mLeftEffect == null || mRightEffect == null) {
                try {
                    mLeftEffect = (EdgeEffect) sLeftEffectField.get(mPager);
                    mRightEffect = (EdgeEffect) sRightEffectField.get(mPager);
                } catch (IllegalAccessException e) {
                    mLeftEffect = mRightEffect = null;
                }
            }
            applyColor(mLeftEffect);
            applyColor(mRightEffect);
        }

        private void applyColor(EdgeEffect effect) {
            if (effect != null) {
                final int alpha = Color.alpha(effect.getColor());
                effect.setColor(Color.argb(alpha, Color.red(mColor),
                        Color.green(mColor), Color.blue(mColor)));
            }
        }

        private boolean ensureStaticFields() {
            if (sLeftEffectField != null && sRightEffectField != null) {
                return true;
            }
            try {
                sLeftEffectField = ViewPager.class.getDeclaredField("mLeftEdge");
                sLeftEffectField.setAccessible(true);
                sRightEffectField = ViewPager.class.getDeclaredField("mRightEdge");
                sRightEffectField.setAccessible(true);
                return true;
            } catch (NoSuchFieldException e) {
                // ignored
            }
            return false;
        }
    }
}
