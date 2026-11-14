package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;

@RunWith(RobolectricTestRunner.class)
public class TranslationSettingsActivityTest {

    @Test
    public void launches_titleIsTranslationSettings() {
        try (ActivityController<TranslationSettingsActivity> c =
                Robolectric.buildActivity(TranslationSettingsActivity.class)) {
            TranslationSettingsActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.translation_settings),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void canSwipeToRefresh_isFalse() {
        try (ActivityController<TranslationSettingsActivity> c =
                Robolectric.buildActivity(TranslationSettingsActivity.class)) {
            TranslationSettingsActivity activity = c.setup().get();
            assertEquals(false, activity.canSwipeToRefresh());
        }
    }

    @Test
    public void fragmentAdded_isTranslationSettingsFragment() {
        try (ActivityController<TranslationSettingsActivity> c =
                Robolectric.buildActivity(TranslationSettingsActivity.class)) {
            TranslationSettingsActivity activity = c.setup().get();
            assertNotNull(activity.getSupportFragmentManager()
                    .findFragmentById(R.id.content_container));
            assertTrue(activity.getSupportFragmentManager()
                    .findFragmentById(R.id.content_container)
                    instanceof com.gh4a.fragment.TranslationSettingsFragment);
        }
    }

    @Test
    public void recreate_restoresFragment() {
        try (ActivityController<TranslationSettingsActivity> c =
                Robolectric.buildActivity(TranslationSettingsActivity.class)) {
            TranslationSettingsActivity activity = c.setup().get();
            assertNotNull(activity);
            c.recreate();
            TranslationSettingsActivity recreated = c.get();
            assertTrue(recreated.getSupportFragmentManager()
                    .findFragmentById(R.id.content_container)
                    instanceof com.gh4a.fragment.TranslationSettingsFragment);
        }
    }

    // ---- A2 第二轮补缺口：TranslationSettingsActivity ----

    @Test
    public void start_startsTranslationSettingsActivity() {
        try (ActivityController<TranslationSettingsActivity> c =
                Robolectric.buildActivity(TranslationSettingsActivity.class)) {
            TranslationSettingsActivity activity = c.setup().get();
            TranslationSettingsActivity.start(activity);
            assertEquals(TranslationSettingsActivity.class.getName(),
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity()
                            .getComponent().getClassName());
        }
    }

}
