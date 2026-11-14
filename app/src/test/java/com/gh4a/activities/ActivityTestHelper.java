package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Test-only reflection/launch helpers for the activities/ batch (A1). */
final class ActivityTestHelper {
    private ActivityTestHelper() {}

    static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    static <T extends android.app.Activity> ActivityController<T> controller(Class<T> cls, Intent intent) {
        return intent == null
                ? Robolectric.buildActivity(cls)
                : Robolectric.buildActivity(cls, intent);
    }

    static <T extends android.app.Activity> T launch(Class<T> cls, Intent intent) {
        return controller(cls, intent).create().start().resume().get();
    }

    static <T extends android.app.Activity> T launch(Class<T> cls) {
        return launch(cls, null);
    }

    static Object get(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError("get field " + fieldName + " on " + target.getClass(), e);
        }
    }

    static void set(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError("set field " + fieldName + " on " + target.getClass(), e);
        }
    }

    static Object call(Object target, String method, Class<?>[] types, Object... args) {
        try {
            Method m = findMethod(target.getClass(), method, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap: callers assert on the real production exception.
            throw new AssertionError("call " + method + " on " + target.getClass(), e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + method + " on " + target.getClass(), e);
        }
    }

    static Object callStatic(Class<?> cls, String method, Class<?>[] types, Object... args) {
        try {
            Method m = findMethod(cls, method, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call static " + cls.getSimpleName() + "." + method, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call static " + cls.getSimpleName() + "." + method, e);
        }
    }

    /** Run due main-looper tasks for up to {@code millis} (bounded: re-posting
     * tasks such as the diagnostic poller cannot loop forever). */
    /** Poll until {@code condition} holds or {@code timeoutMs} elapses; the
     * caller asserts afterwards, so a timeout surfaces as a normal
     * assertion failure instead of a flaky fixed sleep. */
    static void waitFor(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            idleMain(100);
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
    }

    static void idleMain(long millis) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(millis));
    }

    static Class<?>[] types(Object... types) {
        Class<?>[] out = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = (Class<?>) types[i];
        }
        return out;
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>[] types)
            throws NoSuchMethodException {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    /** Simulate the retry snackbar timing out: notify the currently shown
     * Snackbar's registered callbacks with a TIMEOUT dismiss directly,
     * bypassing the slide animations Robolectric never reliably runs
     * (BaseTransientBottomBar.dispatchDismiss routes through the manager
     * into hideView/animateViewOut, whose completion is racy here).
     * This is what makes the terminal error consumer of
     * RxUtils.wrapWithErrorSnackbar run (it only fires on
     * SWIPE/TIMEOUT/ACTION dismiss). The manager is also told the
     * snackbar is gone, so a queued next snackbar can show and no stale
     * current snackbar leaks into later tests. */
    static void dismissCurrentSnackbarAsTimeout() throws Exception {
        Class<?> smClass = Class.forName("com.google.android.material.snackbar.SnackbarManager");
        java.lang.reflect.Method getInstance = smClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object manager = getInstance.invoke(null);
        java.lang.reflect.Field currentField = smClass.getDeclaredField("currentSnackbar");
        currentField.setAccessible(true);
        Object record = currentField.get(manager);
        org.junit.Assert.assertNotNull("no current snackbar", record);
        java.lang.reflect.Field callbackField = record.getClass().getDeclaredField("callback");
        callbackField.setAccessible(true);
        Object managerCallback = ((java.lang.ref.WeakReference<?>) callbackField.get(record)).get();
        org.junit.Assert.assertNotNull("snackbar callback gone", managerCallback);
        java.lang.reflect.Field this0 = managerCallback.getClass().getDeclaredField("this$0");
        this0.setAccessible(true);
        Object snackbar = this0.get(managerCallback);
        Class<?> btbClass = Class.forName(
                "com.google.android.material.snackbar.BaseTransientBottomBar");
        java.lang.reflect.Field callbacksField = btbClass.getDeclaredField("callbacks");
        callbacksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<Object> callbacks =
                (java.util.List<Object>) callbacksField.get(snackbar);
        // Plain snackbars (no addCallback) have a null list: nothing to notify.
        if (callbacks != null) {
            // BaseCallback<B> is unbounded, so the erased signature is (Object, int).
            java.lang.reflect.Method onDismissed = Class.forName(
                    "com.google.android.material.snackbar.BaseTransientBottomBar$BaseCallback")
                    .getMethod("onDismissed", Object.class, int.class);
            for (Object cb : callbacks) {
                onDismissed.invoke(cb, snackbar,
                        com.google.android.material.snackbar.Snackbar.Callback.DISMISS_EVENT_TIMEOUT);
            }
        }
        java.lang.reflect.Method managerOnDismissed = smClass.getMethod("onDismissed",
                Class.forName("com.google.android.material.snackbar.SnackbarManager$Callback"));
        managerOnDismissed.setAccessible(true);
        managerOnDismissed.invoke(manager, managerCallback);
    }

    /** Wait until the snackbar showing {@code substring} is current,
     * dismissing any foreign snackbar (e.g. an auth-failure notice) that
     * queues ours behind it. Shared by IssueActivityTest and
     * DiffViewerActivityTest. */
    static void waitForSnackbarWithText(android.app.Activity activity, String substring)
            throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 5000);
            android.widget.TextView tv = activity.findViewById(
                    com.google.android.material.R.id.snackbar_text);
            if (tv != null && tv.getText().toString().contains(substring)) {
                return;
            }
            dismissCurrentSnackbarAsTimeout();
            idleMain(500);
        }
        android.widget.TextView tv = activity.findViewById(
                com.google.android.material.R.id.snackbar_text);
        org.junit.Assert.assertNotNull("expected snackbar never appeared", tv);
        org.junit.Assert.assertTrue("wrong snackbar shown: " + (tv == null ? null : tv.getText()),
                tv != null && tv.getText().toString().contains(substring));
    }

    static boolean logContains(String tag, String substring) {
        for (org.robolectric.shadows.ShadowLog.LogItem item :
                org.robolectric.shadows.ShadowLog.getLogsForTag(tag)) {
            if (item.msg != null && item.msg.contains(substring)) {
                return true;
            }
        }
        return false;
    }
}
