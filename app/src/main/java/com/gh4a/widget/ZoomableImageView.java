/*
 * ZoomableImageView
 *
 * An ImageView with pinch-to-zoom, one-finger drag and double-tap zoom toggle,
 * implemented on top of the ImageView's image matrix. Inspired by the classic
 * TouchImageView, written from scratch as a single self-contained file.
 */
package com.gh4a.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView implements View.OnTouchListener {
    private static final float DOUBLE_TAP_SCALE = 2.0f;
    // Max zoom is relative to the fit scale: an absolute cap would barely allow
    // zooming into small images that already fit at a large scale.
    private static final float MAX_SCALE_FACTOR = 4.0f;
    private static final float MIN_MAX_SCALE = 5.0f;

    private final Matrix mMatrix = new Matrix();
    private final float[] mMatrixValues = new float[9];

    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;

    private final PointF mLastTouch = new PointF();

    // Used for double-tap zoom animation.
    private float mAnimStartScale = 1f;
    private float mAnimTargetScale = 1f;
    private PointF mAnimFocus = new PointF();
    private boolean mAnimating = false;
    private long mAnimStartTime = 0;

    // View size, kept up to date for centering/bounds checks.
    private int mViewWidth;
    private int mViewHeight;

    // The scale that fits the whole image inside the view; used as the
    // minimum zoom level so the image can never get smaller than "fit".
    private float mFitScale = 1f;

    // Whether the view currently has a drawable it can zoom.
    private boolean mHasDrawable = false;

    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setClickable(true);
        setScaleType(ScaleType.MATRIX);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        setOnTouchListener(this);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        mHasDrawable = drawable != null;
        mAnimating = false;
        resetZoom();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mViewWidth = w;
        mViewHeight = h;
        if (w > 0 && h > 0 && mHasDrawable) {
            fitImageToView();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean handledScale = mScaleDetector.onTouchEvent(event);
        boolean handledGesture = mGestureDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        }
        return handledScale || handledGesture || super.onTouchEvent(event);
    }

    private float getCurrentScale() {
        mMatrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X];
    }

    private float getMaxScale() {
        return Math.max(MIN_MAX_SCALE, mFitScale * MAX_SCALE_FACTOR);
    }

    private void resetZoom() {
        mMatrix.reset();
        if (mViewWidth > 0 && mViewHeight > 0 && mHasDrawable) {
            fitImageToView();
        } else {
            setImageMatrix(mMatrix);
        }
    }

    /**
     * Scale the image so it fits inside the view and center it.
     */
    private void fitImageToView() {
        Drawable drawable = getDrawable();
        if (drawable == null || mViewWidth == 0 || mViewHeight == 0) {
            return;
        }
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        mMatrix.reset();
        float scale = Math.min(
                (float) mViewWidth / (float) drawableWidth,
                (float) mViewHeight / (float) drawableHeight);
        mFitScale = scale;
        mMatrix.postScale(scale, scale);
        float redundantX = (mViewWidth - drawableWidth * scale) / 2f;
        float redundantY = (mViewHeight - drawableHeight * scale) / 2f;
        mMatrix.postTranslate(redundantX, redundantY);
        setImageMatrix(mMatrix);
    }

    private void fixTranslation() {
        mMatrix.getValues(mMatrixValues);
        float transX = mMatrixValues[Matrix.MTRANS_X];
        float transY = mMatrixValues[Matrix.MTRANS_Y];

        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }
        float scale = mMatrixValues[Matrix.MSCALE_X];
        float contentWidth = drawable.getIntrinsicWidth() * scale;
        float contentHeight = drawable.getIntrinsicHeight() * scale;

        float fixX = getFixTranslation(transX, mViewWidth, contentWidth);
        float fixY = getFixTranslation(transY, mViewHeight, contentHeight);
        if (fixX != 0 || fixY != 0) {
            mMatrix.postTranslate(fixX, fixY);
            setImageMatrix(mMatrix);
        }
    }

    private float getFixTranslation(float trans, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            // Center content smaller than the view.
            return (viewSize - contentSize) / 2f - trans;
        }
        float minTrans = viewSize - contentSize;
        float maxTrans = 0;
        if (trans < minTrans) {
            return minTrans - trans;
        }
        if (trans > maxTrans) {
            return maxTrans - trans;
        }
        return 0;
    }

    private void scaleImage(float deltaScale, float focusX, float focusY) {
        float scale = getCurrentScale();
        float newScale = scale * deltaScale;
        float maxScale = getMaxScale();
        if (newScale < mFitScale) {
            deltaScale = mFitScale / scale;
        } else if (newScale > maxScale) {
            deltaScale = maxScale / scale;
        }
        mMatrix.postScale(deltaScale, deltaScale, focusX, focusY);
        fixTranslation();
        setImageMatrix(mMatrix);
    }

    private void dragImage(float dx, float dy) {
        mMatrix.postTranslate(dx, dy);
        fixTranslation();
        setImageMatrix(mMatrix);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mAnimating = false;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            mAnimating = false;
            mLastTouch.set(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!mHasDrawable) {
                return false;
            }
            // Only intercept drags while zoomed in; let the parent handle flings otherwise.
            if (getCurrentScale() > mFitScale * 1.01f) {
                dragImage(-distanceX, -distanceY);
                return true;
            }
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // The view's OnTouchListener consumes all touch events, so the normal
            // click handling in View.onTouchEvent() never runs; forward the tap
            // explicitly so OnClickListener (tap to close) keeps working.
            ZoomableImageView.this.performClick();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (!mHasDrawable) {
                return false;
            }
            mAnimStartScale = getCurrentScale();
            float maxScale = getMaxScale();
            mAnimTargetScale = mAnimStartScale < (mFitScale + maxScale) / 2f
                    ? Math.max(DOUBLE_TAP_SCALE, mFitScale) : mFitScale;
            mAnimFocus.set(e.getX(), e.getY());
            mAnimating = true;
            mAnimStartTime = System.currentTimeMillis();
            post(new ZoomAnimationRunnable());
            return true;
        }
    }

    private class ZoomAnimationRunnable implements Runnable {
        private static final long DURATION_MS = 220;

        @Override
        public void run() {
            if (!mAnimating) {
                return;
            }
            float t = Math.min(1f,
                    (float) (System.currentTimeMillis() - mAnimStartTime) / (float) DURATION_MS);
            // Ease-in-out interpolation.
            float interpolated = (float) (Math.cos((t + 1) * Math.PI) / 2.0 + 0.5);
            float targetScale = mAnimStartScale
                    + (mAnimTargetScale - mAnimStartScale) * interpolated;
            float delta = targetScale / getCurrentScale();
            mMatrix.postScale(delta, delta, mAnimFocus.x, mAnimFocus.y);
            fixTranslation();
            setImageMatrix(mMatrix);
            if (t < 1f) {
                post(this);
            } else {
                mAnimating = false;
            }
        }
    }
}
