package com.gh4a.utils;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 0.1.16：页内 Tab 左右滑的距离判定监听器。
 *
 * 取代此前五处各自的 GestureDetector.onFling 判定：onFling 只在快速甩动时触发，
 * 用户正常慢速拖动、松手不会产生 fling，真机上趋势/Star 两页因此完全没反应。
 * 本监听器只记按下点，松手时算总位移：水平位移 >= 100dp 且水平大于竖直即切
 * 相邻 Tab，不要求速度，慢滑、快甩都切。永不消费事件（onTouch 返回 false），
 * 所以点按、竖向滚动、下拉刷新都不受影响；首末 Tab 不回绕由回调方判断。
 */
public class HorizontalSwipeListener implements View.OnTouchListener {

    /** 手指向左滑（切下一 Tab）/ 向右滑（切上一 Tab）。 */
    public interface Callback {
        void onSwipeLeft();

        void onSwipeRight();
    }

    private static final float MIN_DISTANCE_DP = 100f;

    private final Callback mCallback;
    private final float mMinDistancePx;
    private float mDownX;
    private float mDownY;
    private boolean mTracking;
    // 0.1.20 F-29: 跟踪活动指针 id——多点触控下 pointer 0 可能属于已抬起的手指。
    private int mActivePointerId = -1;

    public HorizontalSwipeListener(@NonNull Callback callback) {
        mCallback = callback;
        // 触摸坐标是 px，阈值是 dp，必须换算，否则在高密度屏上 100dp 会被误算成 100px。
        mMinDistancePx = MIN_DISTANCE_DP
                * android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    /** 供 RecyclerView.OnItemTouchListener 在 onInterceptTouchEvent 里转发事件。 */
    public void handle(@NonNull MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mActivePointerId = e.getPointerId(0);
            mDownX = e.getX(0);
            mDownY = e.getY(0);
            mTracking = true;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN && mTracking) {
            // 新手指落下不改变基准——继续跟踪原活动指针。
        } else if (action == MotionEvent.ACTION_POINTER_UP && mTracking) {
            int upId = e.getPointerId(e.getActionIndex());
            if (upId == mActivePointerId) {
                // 0.1.20 F-29: 活动指针抬起——若还有其他手指，以其当前位置
                // 重定基准继续跟踪；否则取消跟踪，避免松手坐标跳变误切。
                int remaining = -1;
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) != upId) {
                        remaining = e.getPointerId(i);
                        break;
                    }
                }
                if (remaining == -1) {
                    mTracking = false;
                    mActivePointerId = -1;
                } else {
                    mActivePointerId = remaining;
                    int idx = e.findPointerIndex(remaining);
                    mDownX = e.getX(idx);
                    mDownY = e.getY(idx);
                }
            }
        } else if (action == MotionEvent.ACTION_UP && mTracking) {
            mTracking = false;
            mActivePointerId = -1;
            // ACTION_UP 只在最后一指抬起时到，getX()/getY() 即该手指坐标。
            float dx = e.getX() - mDownX;
            float dy = e.getY() - mDownY;
            if (Math.abs(dx) >= mMinDistancePx
                    && Math.abs(dx) > Math.abs(dy)) {
                if (dx < 0) {
                    mCallback.onSwipeLeft();
                } else {
                    mCallback.onSwipeRight();
                }
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mTracking = false;
            mActivePointerId = -1;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        handle(e);
        return false; // 永不消费，只旁听
    }

    /** RecyclerView 版：把同一个距离判定挂到列表的 OnItemTouchListener 上。 */
    public static class ForRecyclerView implements RecyclerView.OnItemTouchListener {
        private final HorizontalSwipeListener mListener;

        public ForRecyclerView(@NonNull Callback callback) {
            mListener = new HorizontalSwipeListener(callback);
        }

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                @NonNull MotionEvent e) {
            mListener.handle(e);
            return false; // 永不消费，只旁听
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallow) {
        }
    }
}
