package com.gh4a.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

/**
 * 0.1.17：可锁定的 ViewPager。默认与标准 ViewPager 完全一致；调用
 * setPagingEnabled(false) 后，手指横滑不再被 ViewPager 拦截——
 * 子视图（列表、标签行）能收到完整的按下/松手序列，页内左右滑
 * （HorizontalSwipeListener 的距离判定）才算得出位移。
 * 程序切页（setCurrentItem）不受此开关影响，仍可正常工作。
 * 本类只在 HomeActivity 对单 Tab 工厂关闭手指切页（见
 * BasePagerActivity.isPagerTouchPagingEnabled）；其他
 * BasePagerActivity 子类默认保持开启，行为不变。
 */
public class LockableViewPager extends ViewPager {
    private boolean mPagingEnabled = true;

    public LockableViewPager(Context context) {
        super(context);
    }

    public LockableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPagingEnabled(boolean enabled) {
        mPagingEnabled = enabled;
    }

    public boolean isPagingEnabled() {
        return mPagingEnabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mPagingEnabled) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mPagingEnabled) {
            return false;
        }
        return super.onTouchEvent(ev);
    }
}
