package com.gh4a.adapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;


import androidx.test.core.app.ApplicationProvider;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import com.meisolsson.githubsdk.model.AuthorAssociation;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;

import org.mockito.Mockito;

import java.util.Date;

/** Shared helpers for adapter tests. Test-only code, no production use. */
public final class AdapterTestSupport {
    private AdapterTestSupport() {}

    private static Activity sActivity;

    /**
     * Layouts use ?attr/selectableItemBackground etc. The raw application
     * context under Robolectric carries the default DeviceDefault theme,
     * which cannot resolve app attrs -> InflateException. And production
     * code calls context.startActivity() without FLAG_ACTIVITY_NEW_TASK,
     * which throws from a non-Activity context. So tests use a real
     * (Robolectric) Activity wrapped in the app theme, like the
     * activities/fragment groups do with buildActivity().
     */
    public static Context context() {
        sActivity = org.robolectric.Robolectric.buildActivity(Activity.class)
                .setup().get();
        return new ContextThemeWrapper(sActivity, R.style.AppTheme);
    }

    public static org.robolectric.shadows.ShadowActivity shadowApp(Context context) {
        return org.robolectric.Shadows.shadowOf(sActivity);
    }

    /**
     * AutoValue Reactions builder requires all 8 counts to be set.
     */
    public static Reactions reactions(int plusOne) {
        return Reactions.builder().plusOne(plusOne).minusOne(0).laugh(0).hooray(0)
                .confused(0).heart(0).rocket(0).eyes(0).build();
    }

    /**
     * ReviewComment has no builder (Moshi-only model), so tests use a Mockito
     * mock. withReactions() is stubbed to return the mock itself so
     * TimelineComment.setReactions() does not null out the comment.
     */
    public static ReviewComment reviewComment(Long id, User user, String bodyHtml,
            Date createdAt, Date updatedAt, AuthorAssociation association, Integer position,
            String path, String diffChunk, String pullRequestUrl, String originalCommitId,
            Integer originalPosition) {
        ReviewComment rc = mock(ReviewComment.class);
        when(rc.id()).thenReturn(id);
        when(rc.user()).thenReturn(user);
        when(rc.bodyHtml()).thenReturn(bodyHtml);
        when(rc.createdAt()).thenReturn(createdAt);
        when(rc.updatedAt()).thenReturn(updatedAt);
        when(rc.authorAssociation()).thenReturn(association);
        when(rc.position()).thenReturn(position);
        when(rc.path()).thenReturn(path);
        when(rc.diffChunk()).thenReturn(diffChunk);
        when(rc.pullRequestUrl()).thenReturn(pullRequestUrl);
        when(rc.originalCommitId()).thenReturn(originalCommitId);
        when(rc.originalPosition()).thenReturn(originalPosition);
        when(rc.withReactions(Mockito.any(
                com.meisolsson.githubsdk.model.Reactions.class)))
                .thenAnswer(inv -> rc);
        return rc;
    }

    public static LayoutInflater inflater(Context context) {
        return LayoutInflater.from(context);
    }

    public static ViewGroup parent(Context context) {
        return new FrameLayout(context);
    }

    /** Instantiate a package-private constructor via reflection (test-only). */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz, Class<?>[] paramTypes, Object... args) {
        try {
            java.lang.reflect.Constructor<T> c = clazz.getDeclaredConstructor(paramTypes);
            c.setAccessible(true);
            return c.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("cannot instantiate " + clazz.getName(), e);
        }
    }

    /** Set a private field via reflection (test-only, e.g. Feed's SimpleXML fields). */
    public static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("cannot set field " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getField(Object target, String name) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fake an authorized login (login + token). addAccount writes the prefs
     * first and only then reschedules workers; the worker call may fail
     * under Robolectric (WorkManager not initialized), which is harmless
     * here — auth state is already stored. Call {@link #clearAuth} in
     * @After so other tests in the class stay unauthorized.
     */
    public static void authorize(String login) {
        try {
            Gh4Application.get().addAccount(
                    User.builder().login(login).id(1L).build(), "test-token");
        } catch (RuntimeException e) {
            // worker rescheduling only; auth prefs are already written
        }
        if (!Gh4Application.get().isAuthorized()) {
            throw new AssertionError("authorize() did not authorize " + login);
        }
    }

    public static void clearAuth() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove("active_login").remove("logins").apply();
        android.content.SharedPreferences secure =
                Gh4Application.securePrefs(ctx);
        android.content.SharedPreferences.Editor ed = secure.edit();
        for (String key : secure.getAll().keySet()) {
            if (key.startsWith("token_")) {
                ed.remove(key);
            }
        }
        ed.apply();
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name)
            throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
