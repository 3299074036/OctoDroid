package com.gh4a.worker;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Robolectric runs at SDK 35 here, so the TIRAMISU branch is exercised. */
@RunWith(RobolectricTestRunner.class)
public class NotificationPermissionTest {
    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private void grant() {
        Shadows.shadowOf((Application) context().getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void deny() {
        Shadows.shadowOf((Application) context().getApplicationContext())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Test
    public void canPostNotifications_defaultRobolectric_isDenied() {
        // Robolectric grants no runtime permissions by default.
        assertFalse(NotificationPermission.canPostNotifications(context()));
    }

    @Test
    public void canPostNotifications_granted_returnsTrue() {
        grant();
        assertTrue(NotificationPermission.canPostNotifications(context()));
    }

    @Test
    public void canPostNotifications_deniedAfterGrant_returnsFalse() {
        grant();
        assertTrue(NotificationPermission.canPostNotifications(context()));
        deny();
        assertFalse(NotificationPermission.canPostNotifications(context()));
    }

    @Test
    @Config(sdk = 32)
    public void canPostNotifications_belowTiramisu_alwaysTrue() {
        deny();
        assertTrue(NotificationPermission.canPostNotifications(context()));
    }

    @Test
    public void requestIfNeeded_nullActivity_returnsTrueWithoutRequest() {
        deny();
        assertTrue(NotificationPermission.requestIfNeeded(null));
    }

    @Test
    public void requestIfNeeded_granted_returnsTrue() {
        grant();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertTrue(NotificationPermission.requestIfNeeded(activity));
    }

    @Test
    public void requestIfNeeded_denied_requestsPermissionAndReturnsFalse() {
        deny();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertFalse(NotificationPermission.requestIfNeeded(activity));
    }

    @Test
    @Config(sdk = 32)
    public void requestIfNeeded_belowTiramisu_returnsTrueWithoutRequest() {
        deny();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertTrue(NotificationPermission.requestIfNeeded(activity));
    }
}
