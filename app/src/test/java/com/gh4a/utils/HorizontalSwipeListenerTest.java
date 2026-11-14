package com.gh4a.utils;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：HorizontalSwipeListener。阈值 100dp（Robolectric 下 density
 * 为 1，故 100px 即阈值）；位移取 500px（远超阈值，不依赖具体 density）、
 * 10px（远低于阈值）以避免密度假设。
 */
@RunWith(RobolectricTestRunner.class)
public class HorizontalSwipeListenerTest {

    private static class CountingCallback implements HorizontalSwipeListener.Callback {
        int left;
        int right;

        @Override
        public void onSwipeLeft() {
            left++;
        }

        @Override
        public void onSwipeRight() {
            right++;
        }
    }

    private static MotionEvent down(float x, float y) {
        return MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
    }

    private static MotionEvent up(float x, float y) {
        return MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0);
    }

    private static MotionEvent cancel() {
        return MotionEvent.obtain(0, 50, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    }

    /** 双指事件：pointers[i] = {id, x, y}，action 已含 pointer index 位。 */
    private static MotionEvent multi(int action, float[][] pointers) {
        MotionEvent.PointerProperties[] props =
                new MotionEvent.PointerProperties[pointers.length];
        MotionEvent.PointerCoords[] coords =
                new MotionEvent.PointerCoords[pointers.length];
        for (int i = 0; i < pointers.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = (int) pointers[i][0];
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = pointers[i][1];
            coords[i].y = pointers[i][2];
        }
        return MotionEvent.obtain(0, 0, action, pointers.length, props, coords,
                0, 0, 1f, 1f, 0, 0, 0, 0);
    }

    @Test
    public void swipeLeft_largeMoveLeft() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(500, 100));
        l.handle(up(0, 100));
        assertEquals(1, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void swipeRight_largeMoveRight() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(0, 100));
        l.handle(up(500, 100));
        assertEquals(0, cb.left);
        assertEquals(1, cb.right);
    }

    @Test
    public void belowThreshold_noCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(100, 100));
        l.handle(up(110, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void verticalDominant_noCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(100, 100));
        l.handle(up(400, 600));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void equalDxDy_noCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(0, 0));
        l.handle(up(500, 500));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void tap_noCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(100, 100));
        l.handle(up(100, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void upWithoutDown_noCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(up(500, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void cancel_suppressesSwipe() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(0, 100));
        l.handle(cancel());
        l.handle(up(500, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void secondSwipeAfterFirst_works() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(down(500, 100));
        l.handle(up(0, 100));
        l.handle(down(0, 100));
        l.handle(up(500, 100));
        assertEquals(1, cb.left);
        assertEquals(1, cb.right);
    }

    @Test
    public void onTouch_neverConsumes() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        assertFalse(l.onTouch(null, down(500, 100)));
        assertFalse(l.onTouch(null, up(0, 100)));
        assertEquals(1, cb.left);
    }

    @Test
    public void activePointerUp_rebasesToRemainingPointer() {
        // F-29：活动指针抬起后以另一手指当前位置重定基准继续跟踪。
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(multi(MotionEvent.ACTION_DOWN, new float[][]{{0, 0, 100}}));
        l.handle(multi(MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][]{{0, 0, 100}, {1, 500, 100}}));
        // pointer 0 抬起：基准重定到 pointer 1 的 x=500
        l.handle(multi(MotionEvent.ACTION_POINTER_UP
                | (0 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][]{{0, 0, 100}, {1, 500, 100}}));
        // pointer 1 松手在 x=700：相对新基准 dx=+200 → 右滑
        l.handle(multi(MotionEvent.ACTION_UP, new float[][]{{1, 700, 100}}));
        assertEquals(0, cb.left);
        assertEquals(1, cb.right);
    }

    @Test
    public void nonActivePointerUp_keepsOriginalBaseline() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(multi(MotionEvent.ACTION_DOWN, new float[][]{{0, 0, 100}}));
        l.handle(multi(MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][]{{0, 0, 100}, {1, 500, 100}}));
        // pointer 1 抬起：基准仍是 pointer 0 的 x=0
        l.handle(multi(MotionEvent.ACTION_POINTER_UP
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][]{{0, 0, 100}, {1, 500, 100}}));
        l.handle(multi(MotionEvent.ACTION_UP, new float[][]{{0, 500, 100}}));
        assertEquals(0, cb.left);
        assertEquals(1, cb.right);
    }

    @Test
    public void activePointerUpWithNoRemaining_cancelsTracking() {
        // 仅剩活动指针时抬起（ACTION_POINTER_UP 且无其他手指）：取消跟踪，
        // 之后 ACTION_UP 的坐标跳变不得误切。
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener l = new HorizontalSwipeListener(cb);
        l.handle(multi(MotionEvent.ACTION_DOWN, new float[][]{{0, 0, 100}}));
        l.handle(multi(MotionEvent.ACTION_POINTER_UP
                | (0 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][]{{0, 0, 100}}));
        l.handle(up(500, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }

    @Test
    public void forRecyclerView_interceptsWithoutConsuming() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener.ForRecyclerView rv =
                new HorizontalSwipeListener.ForRecyclerView(cb);
        assertFalse(rv.onInterceptTouchEvent(null, down(500, 100)));
        assertFalse(rv.onInterceptTouchEvent(null, up(0, 100)));
        assertEquals(1, cb.left);
        // 其余两个回调为空实现，不抛异常
        rv.onTouchEvent(null, up(0, 100));
        rv.onRequestDisallowInterceptTouchEvent(true);
        assertEquals(1, cb.left);
    }

    @Test
    public void forRecyclerView_belowThresholdNoCallback() {
        CountingCallback cb = new CountingCallback();
        HorizontalSwipeListener.ForRecyclerView rv =
                new HorizontalSwipeListener.ForRecyclerView(cb);
        rv.onInterceptTouchEvent(null, down(100, 100));
        rv.onInterceptTouchEvent(null, up(110, 100));
        assertEquals(0, cb.left);
        assertEquals(0, cb.right);
    }
}
