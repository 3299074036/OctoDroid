package com.gh4a.widget;

import android.view.MotionEvent;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LockableViewPagerTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private MotionEvent downEvent() {
        return MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
    }

    @Test
    public void defaultState_pagingEnabled() {
        LockableViewPager pager = new LockableViewPager(activity());
        assertTrue(pager.isPagingEnabled());
    }

    @Test
    public void setPagingEnabled_false_disables() {
        LockableViewPager pager = new LockableViewPager(activity());
        pager.setPagingEnabled(false);
        assertFalse(pager.isPagingEnabled());
    }

    @Test
    public void setPagingEnabled_true_reEnables() {
        LockableViewPager pager = new LockableViewPager(activity());
        pager.setPagingEnabled(false);
        pager.setPagingEnabled(true);
        assertTrue(pager.isPagingEnabled());
    }

    @Test
    public void onInterceptTouchEvent_disabled_returnsFalse() {
        LockableViewPager pager = new LockableViewPager(activity());
        pager.setPagingEnabled(false);
        assertFalse(pager.onInterceptTouchEvent(downEvent()));
    }

    @Test
    public void onTouchEvent_disabled_returnsFalse() {
        LockableViewPager pager = new LockableViewPager(activity());
        pager.setPagingEnabled(false);
        assertFalse(pager.onTouchEvent(downEvent()));
    }

    @Test
    public void onInterceptTouchEvent_enabled_delegatesToSuper_noPagesReturnsFalse() {
        LockableViewPager pager = new LockableViewPager(activity());
        assertFalse(pager.onInterceptTouchEvent(downEvent()));
    }

    @Test
    public void onTouchEvent_enabled_delegatesToSuper_noPagesReturnsFalse() {
        LockableViewPager pager = new LockableViewPager(activity());
        assertFalse(pager.onTouchEvent(downEvent()));
    }

    @Test
    public void constructors_attrsVariant_defaultEnabled() {
        LockableViewPager pager = new LockableViewPager(activity(), null);
        assertTrue(pager.isPagingEnabled());
    }
}
