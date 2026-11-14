package com.gh4a.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ZoomableImageViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ZoomableImageView view(FragmentActivity activity) {
        return new ZoomableImageView(activity);
    }

    private BitmapDrawable drawable(FragmentActivity activity, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        return new BitmapDrawable(activity.getResources(), bitmap);
    }

    private void layout(ZoomableImageView view, int size) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, size, size);
    }

    private void layout(ZoomableImageView view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private float[] matrixValues(ZoomableImageView view) {
        float[] values = new float[9];
        view.getImageMatrix().getValues(values);
        return values;
    }

    private float scale(ZoomableImageView view) {
        return matrixValues(view)[Matrix.MSCALE_X];
    }

    private float transX(ZoomableImageView view) {
        return matrixValues(view)[Matrix.MTRANS_X];
    }

    private Object innerListener(ZoomableImageView view, String simpleName) throws Exception {
        Class<?> clazz = Class.forName("com.gh4a.widget.ZoomableImageView$" + simpleName);
        Constructor<?> ctor = clazz.getDeclaredConstructor(ZoomableImageView.class);
        ctor.setAccessible(true);
        return ctor.newInstance(view);
    }

    private Object invokePrivate(ZoomableImageView view, String name, Class<?>[] types,
            Object[] args) throws Exception {
        Method method = ZoomableImageView.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(view, args);
    }


    private Method accessibleMethod(Object target, String name, Class<?>... types)
            throws Exception {
        // Inner listener classes are private, so their public methods need
        // setAccessible before invoke.
        Method method = target.getClass().getMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private void setField(ZoomableImageView view, String name, Object value) throws Exception {
        Field field = ZoomableImageView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(view, value);
    }

    private Object getField(ZoomableImageView view, String name) throws Exception {
        Field field = ZoomableImageView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }

    @Test
    public void setImageDrawable_beforeLayout_matrixIsIdentity() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void layout_fitsImageAndCenters() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        assertEquals(2f, scale(view), 0.001f);
        assertEquals(0f, transX(view), 0.001f);
        assertEquals(50f, matrixValues(view)[Matrix.MTRANS_Y], 0.001f);
    }

    @Test
    public void layout_squareImage_fitScaleOne() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 200);
        assertEquals(1f, scale(view), 0.001f);
        assertEquals(0f, transX(view), 0.001f);
    }

    @Test
    public void setImageDrawable_null_clearsDrawableAndResetsMatrix() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        assertEquals(2f, scale(view), 0.001f);

        view.setImageDrawable(null);

        assertEquals(null, view.getDrawable());
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void setImageDrawable_again_resetsZoomToFit() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        BitmapDrawable d = drawable(activity, 100, 50);
        view.setImageDrawable(d);
        layout(view, 200);
        // Zoom in through the scale listener first.
        Object scaleListener = innerListener(view, "ScaleListener");
        ScaleGestureDetector detector = mock(ScaleGestureDetector.class);
        when(detector.getScaleFactor()).thenReturn(2f);
        when(detector.getFocusX()).thenReturn(100f);
        when(detector.getFocusY()).thenReturn(100f);
        Method onScale = accessibleMethod(scaleListener, "onScale", ScaleGestureDetector.class);
        onScale.invoke(scaleListener, detector);
        assertEquals(4f, scale(view), 0.001f);

        view.setImageDrawable(d);

        assertEquals(2f, scale(view), 0.001f);
    }

    @Test
    public void onTouch_down_returnsTrue() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);

        assertTrue(view.onTouch(view, down));
    }

    @Test
    public void onTouch_upAndCancel_doNotThrow() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        view.onTouch(view, MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0));
        view.onTouch(view, MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 110, 110, 0));
        view.onTouch(view, MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 110, 110, 0));
        view.onTouch(view, MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 110, 110, 0));
    }

    @Test
    public void scaleListener_onScaleBegin_returnsTrueAndCancelsAnimation() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        Object scaleListener = innerListener(view, "ScaleListener");
        Method onScaleBegin = accessibleMethod(scaleListener,
                "onScaleBegin", ScaleGestureDetector.class);

        assertTrue((boolean) onScaleBegin.invoke(scaleListener, mock(ScaleGestureDetector.class)));
        assertFalse((boolean) getField(view, "mAnimating"));
    }

    @Test
    public void scaleListener_onScale_zoomsAroundFocus() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        Object scaleListener = innerListener(view, "ScaleListener");
        ScaleGestureDetector detector = mock(ScaleGestureDetector.class);
        when(detector.getScaleFactor()).thenReturn(2f);
        when(detector.getFocusX()).thenReturn(100f);
        when(detector.getFocusY()).thenReturn(100f);
        Method onScale = accessibleMethod(scaleListener, "onScale", ScaleGestureDetector.class);

        assertTrue((boolean) onScale.invoke(scaleListener, detector));
        assertEquals(4f, scale(view), 0.001f);
    }

    @Test
    public void scaleImage_belowFit_clampsToFit() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{0.1f, 100f, 100f});
        assertEquals(2f, scale(view), 0.001f);
    }

    @Test
    public void scaleImage_aboveMax_clampsToMax() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        // fit=2, max=max(5, 2*4)=8
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{10f, 100f, 100f});
        assertEquals(8f, scale(view), 0.001f);
    }

    @Test
    public void scaleImage_withinRange_appliesDelta() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{1.5f, 100f, 100f});
        assertEquals(3f, scale(view), 0.001f);
    }

    @Test
    public void getFixTranslation_contentSmaller_centers() throws Exception {
        ZoomableImageView view = view(activity());
        float fix = (float) invokePrivate(view, "getFixTranslation",
                new Class[]{float.class, float.class, float.class},
                new Object[]{10f, 200f, 50f});
        assertEquals(65f, fix, 0.001f);
    }

    @Test
    public void getFixTranslation_transBelowMin_clampsToMin() throws Exception {
        ZoomableImageView view = view(activity());
        float fix = (float) invokePrivate(view, "getFixTranslation",
                new Class[]{float.class, float.class, float.class},
                new Object[]{-150f, 200f, 300f});
        assertEquals(50f, fix, 0.001f);
    }

    @Test
    public void getFixTranslation_transAboveMax_clampsToMax() throws Exception {
        ZoomableImageView view = view(activity());
        float fix = (float) invokePrivate(view, "getFixTranslation",
                new Class[]{float.class, float.class, float.class},
                new Object[]{10f, 200f, 300f});
        assertEquals(-10f, fix, 0.001f);
    }

    @Test
    public void getFixTranslation_transInRange_noFix() throws Exception {
        ZoomableImageView view = view(activity());
        float fix = (float) invokePrivate(view, "getFixTranslation",
                new Class[]{float.class, float.class, float.class},
                new Object[]{-50f, 200f, 300f});
        assertEquals(0f, fix, 0.001f);
    }

    @Test
    public void dragImage_movesWithinBounds() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{2f, 100f, 100f});
        assertEquals(4f, scale(view), 0.001f);
        float before = transX(view);

        invokePrivate(view, "dragImage",
                new Class[]{float.class, float.class}, new Object[]{10f, 0f});

        assertEquals(before + 10f, transX(view), 0.001f);
    }

    @Test
    public void dragImage_clampsAtContentEdge() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{2f, 100f, 100f});
        // content width 400 in a 200 view -> minTrans = -200
        invokePrivate(view, "dragImage",
                new Class[]{float.class, float.class}, new Object[]{-500f, 0f});
        assertEquals(-200f, transX(view), 0.001f);
    }

    @Test
    public void gestureListener_onDown_returnsTrue() throws Exception {
        ZoomableImageView view = view(activity());
        Object listener = innerListener(view, "GestureListener");
        Method onDown = accessibleMethod(listener, "onDown", MotionEvent.class);
        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10, 20, 0);
        assertTrue((boolean) onDown.invoke(listener, e));
    }

    @Test
    public void gestureListener_onScroll_notZoomed_returnsFalse() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        Object listener = innerListener(view, "GestureListener");
        Method onScroll = accessibleMethod(listener, "onScroll",
                MotionEvent.class, MotionEvent.class, float.class, float.class);
        MotionEvent e1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        MotionEvent e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 90, 100, 0);
        assertFalse((boolean) onScroll.invoke(listener, e1, e2, 10f, 0f));
    }

    @Test
    public void gestureListener_onScroll_zoomed_dragsImage() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{2f, 100f, 100f});
        float before = transX(view);
        Object listener = innerListener(view, "GestureListener");
        Method onScroll = accessibleMethod(listener, "onScroll",
                MotionEvent.class, MotionEvent.class, float.class, float.class);
        MotionEvent e1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        MotionEvent e2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 90, 100, 0);

        assertTrue((boolean) onScroll.invoke(listener, e1, e2, 10f, 0f));
        assertEquals(before - 10f, transX(view), 0.001f);
    }

    @Test
    public void gestureListener_onSingleTapConfirmed_performsClick() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        AtomicBoolean clicked = new AtomicBoolean(false);
        view.setOnClickListener(v -> clicked.set(true));
        Object listener = innerListener(view, "GestureListener");
        Method method = accessibleMethod(listener,
                "onSingleTapConfirmed", MotionEvent.class);
        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 100, 100, 0);

        assertTrue((boolean) method.invoke(listener, e));
        assertTrue(clicked.get());
    }

    @Test
    public void gestureListener_onDoubleTap_noDrawable_returnsFalse() throws Exception {
        ZoomableImageView view = view(activity());
        Object listener = innerListener(view, "GestureListener");
        Method method = accessibleMethod(listener, "onDoubleTap", MotionEvent.class);
        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        assertFalse((boolean) method.invoke(listener, e));
    }

    @Test
    public void gestureListener_onDoubleTap_withDrawable_startsAnimation() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 200);
        Object listener = innerListener(view, "GestureListener");
        Method method = accessibleMethod(listener, "onDoubleTap", MotionEvent.class);
        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);

        assertTrue((boolean) method.invoke(listener, e));
        assertTrue((boolean) getField(view, "mAnimating"));
    }

    @Test
    public void zoomAnimationRunnable_notAnimating_returnsImmediately() throws Exception {
        ZoomableImageView view = view(activity());
        Object runnable = innerListener(view, "ZoomAnimationRunnable");
        setField(view, "mAnimating", false);
        float before = scale(view);
        ((Runnable) runnable).run();
        assertEquals(before, scale(view), 0.001f);
    }

    @Test
    public void zoomAnimationRunnable_finishedAnimation_setsTargetScale() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 200);
        Object runnable = innerListener(view, "ZoomAnimationRunnable");
        setField(view, "mAnimating", true);
        setField(view, "mAnimStartScale", 1f);
        setField(view, "mAnimTargetScale", 2f);
        android.graphics.PointF focus = new android.graphics.PointF(100f, 100f);
        setField(view, "mAnimFocus", focus);
        setField(view, "mAnimStartTime", System.currentTimeMillis() - 1000L);

        ((Runnable) runnable).run();

        assertFalse((boolean) getField(view, "mAnimating"));
        assertEquals(2f, scale(view), 0.001f);
    }

    @Test
    public void savedState_parcelRoundTrip_restoresZoomAfterRelayout() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        BitmapDrawable d = drawable(activity, 100, 50);
        view.setImageDrawable(d);
        layout(view, 200);
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{2f, 100f, 100f});
        assertEquals(4f, scale(view), 0.001f);

        Parcelable state = view.onSaveInstanceState();
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Class<?> ssClass = Class.forName("com.gh4a.widget.ZoomableImageView$SavedState");
        Parcelable.Creator<?> creator =
                (Parcelable.Creator<?>) ssClass.getField("CREATOR").get(null);
        Parcelable restored = (Parcelable) creator.createFromParcel(parcel);

        ZoomableImageView newView = view(activity);
        newView.onRestoreInstanceState(restored);
        newView.setImageDrawable(d);
        layout(newView, 200);

        assertEquals(4f, scale(newView), 0.001f);
    }

    @Test
    public void onRestoreInstanceState_nonSavedState_delegatesToSuper() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 100, 50));
        layout(view, 200);
        // EMPTY_STATE is not a SavedState, so the else branch runs and the
        // current (fit) matrix is left untouched.
        view.onRestoreInstanceState(android.view.View.BaseSavedState.EMPTY_STATE);
        assertEquals(2f, scale(view), 0.001f);
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertTrue(new ZoomableImageView(activity, null) instanceof View);
        assertTrue(new ZoomableImageView(activity, null, 0) instanceof View);
    }

    // ---------- onSizeChanged / resetZoom / fitImageToView gaps ----------

    @Test
    public void onSizeChanged_noDrawable_doesNotFit() throws Exception {
        ZoomableImageView view = view(activity());
        layout(view, 500);
        assertEquals(1f, (float) getField(view, "mFitScale"), 0.001f);
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void onSizeChanged_zeroHeight_doesNotFit() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 500, 0);
        assertEquals(1f, (float) getField(view, "mFitScale"), 0.001f);
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void onSizeChanged_zeroWidth_doesNotFit() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 0, 500);
        assertEquals(1f, (float) getField(view, "mFitScale"), 0.001f);
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void resetZoom_layoutThenNullDrawable_resetsMatrix() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 500);
        assertEquals(2.5f, scale(view), 0.001f);
        view.setImageDrawable(null);
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void resetZoom_zeroHeightThenDrawable_keepsIdentityMatrix() {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        layout(view, 500, 0);
        view.setImageDrawable(drawable(activity, 200, 200));
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void fitImageToView_noDrawable_returnsEarly() throws Exception {
        ZoomableImageView view = view(activity());
        invokePrivate(view, "fitImageToView", new Class[]{}, new Object[]{});
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void fitImageToView_zeroWidth_returnsEarly() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        invokePrivate(view, "fitImageToView", new Class[]{}, new Object[]{});
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void fitImageToView_zeroHeight_returnsEarly() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 500, 0);
        invokePrivate(view, "fitImageToView", new Class[]{}, new Object[]{});
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void fitImageToView_zeroIntrinsicSize_returnsEarly() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        layout(view, 500);
        // ColorDrawable reports -1/-1 intrinsic size.
        view.setImageDrawable(new ColorDrawable(0xFFFF0000));
        assertEquals(1f, (float) getField(view, "mFitScale"), 0.001f);
    }

    @Test
    public void fitImageToView_zeroIntrinsicHeight_returnsEarly() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        layout(view, 500);
        Drawable noHeight = new Drawable() {
            @Override public void draw(Canvas canvas) {}
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.OPAQUE; }
            @Override public int getIntrinsicWidth() { return 100; }
            @Override public int getIntrinsicHeight() { return -1; }
        };
        view.setImageDrawable(noHeight);
        assertEquals(1f, (float) getField(view, "mFitScale"), 0.001f);
    }

    @Test
    public void fixTranslation_noDrawable_returnsEarly() throws Exception {
        ZoomableImageView view = view(activity());
        invokePrivate(view, "fixTranslation", new Class[]{}, new Object[]{});
        assertEquals(1f, scale(view), 0.001f);
    }

    @Test
    public void fixTranslation_centeredImage_leavesMatrixUnchanged() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 500);
        float[] before = matrixValues(view);
        invokePrivate(view, "fixTranslation", new Class[]{}, new Object[]{});
        float[] after = matrixValues(view);
        assertEquals(before[Matrix.MTRANS_X], after[Matrix.MTRANS_X], 0.001f);
        assertEquals(before[Matrix.MTRANS_Y], after[Matrix.MTRANS_Y], 0.001f);
    }

    // ---------- onTouch / gesture / animation gaps ----------

    @Test
    public void onTouch_scaleDetectorFalse_downHandledByGesture() throws Exception {
        ZoomableImageView view = view(activity());
        ScaleGestureDetector scaleDetector = mock(ScaleGestureDetector.class);
        when(scaleDetector.onTouchEvent(any())).thenReturn(false);
        setField(view, "mScaleDetector", scaleDetector);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        assertTrue(view.onTouch(view, down));
    }

    @Test
    public void onTouch_bothDetectorsFalse_downHandledBySuper() throws Exception {
        ZoomableImageView view = view(activity());
        ScaleGestureDetector scaleDetector = mock(ScaleGestureDetector.class);
        when(scaleDetector.onTouchEvent(any())).thenReturn(false);
        setField(view, "mScaleDetector", scaleDetector);
        GestureDetector gestureDetector = mock(GestureDetector.class);
        when(gestureDetector.onTouchEvent(any())).thenReturn(false);
        setField(view, "mGestureDetector", gestureDetector);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        assertTrue(view.onTouch(view, down));
    }

    @Test
    public void onTouch_bothDetectorsFalse_notClickable_returnsFalse() throws Exception {
        ZoomableImageView view = view(activity());
        ScaleGestureDetector scaleDetector = mock(ScaleGestureDetector.class);
        when(scaleDetector.onTouchEvent(any())).thenReturn(false);
        setField(view, "mScaleDetector", scaleDetector);
        GestureDetector gestureDetector = mock(GestureDetector.class);
        when(gestureDetector.onTouchEvent(any())).thenReturn(false);
        setField(view, "mGestureDetector", gestureDetector);
        view.setClickable(false);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        assertFalse(view.onTouch(view, down));
    }

    @Test
    public void gestureListener_onScroll_noDrawable_returnsFalse() throws Exception {
        ZoomableImageView view = view(activity());
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        MotionEvent move = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 100, 200, 0);
        assertTrue(view.onTouch(view, down));
        // The scroll itself is rejected (no drawable), but the view still
        // consumes the event through the detectors / super.
        assertTrue(view.onTouch(view, move));
    }

    @Test
    public void gestureListener_onDoubleTap_zoomedIn_targetsFitScale() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 200);
        // Fit scale is 1, max scale is 5, so the midpoint is 3. Zoom to 4
        // so the double-tap targets the fit scale instead of zooming in.
        invokePrivate(view, "scaleImage",
                new Class[]{float.class, float.class, float.class},
                new Object[]{4f, 100f, 100f});
        assertEquals(4f, scale(view), 0.001f);
        Object listener = innerListener(view, "GestureListener");
        Method method = accessibleMethod(listener, "onDoubleTap", MotionEvent.class);
        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
        assertTrue((boolean) method.invoke(listener, e));
        assertEquals(1f, (float) getField(view, "mAnimTargetScale"), 0.001f);
    }

    @Test
    public void zoomAnimationRunnable_midAnimation_repostsItself() throws Exception {
        FragmentActivity activity = activity();
        ZoomableImageView view = view(activity);
        view.setImageDrawable(drawable(activity, 200, 200));
        layout(view, 200);
        Object runnable = innerListener(view, "ZoomAnimationRunnable");
        setField(view, "mAnimating", true);
        setField(view, "mAnimStartScale", 1f);
        setField(view, "mAnimTargetScale", 2f);
        android.graphics.PointF focus = new android.graphics.PointF(100f, 100f);
        setField(view, "mAnimFocus", focus);
        setField(view, "mAnimStartTime", System.currentTimeMillis());

        ((Runnable) runnable).run();

        // t < 1: the animation is still running and re-posted itself.
        assertTrue((boolean) getField(view, "mAnimating"));
    }

    @Test
    public void savedState_creator_newArray_returnsSizedArray() {
        assertEquals(3, ZoomableImageView.SavedState.CREATOR.newArray(3).length);
    }
}
