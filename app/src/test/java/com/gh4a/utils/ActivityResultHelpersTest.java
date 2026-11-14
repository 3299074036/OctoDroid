package com.gh4a.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResult;

import com.gh4a.activities.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 组：ActivityResultHelpers 只有两个公开嵌套类，createIntent 需 Context，
 * 走 Robolectric。
 */
@RunWith(RobolectricTestRunner.class)
public class ActivityResultHelpersTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void startSettingsContract_createIntent_targetsSettingsActivity() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        Intent intent = contract.createIntent(context(), null);
        assertEquals(SettingsActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void startSettingsContract_parseResult_nullIntentIsFalse() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        assertFalse(contract.parseResult(Activity.RESULT_OK, null));
    }

    @Test
    public void startSettingsContract_parseResult_noExtrasIsFalse() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        assertFalse(contract.parseResult(Activity.RESULT_OK, new Intent()));
    }

    @Test
    public void startSettingsContract_parseResult_themeChangedTrue() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.RESULT_EXTRA_THEME_CHANGED, true);
        assertTrue(contract.parseResult(Activity.RESULT_OK, intent));
    }

    @Test
    public void startSettingsContract_parseResult_fontScaleChangedTrue() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.RESULT_EXTRA_FONT_SCALE_CHANGED, true);
        assertTrue(contract.parseResult(Activity.RESULT_OK, intent));
    }

    @Test
    public void startSettingsContract_parseResult_bothFlagsFalseIsFalse() {
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.RESULT_EXTRA_THEME_CHANGED, false);
        intent.putExtra(SettingsActivity.RESULT_EXTRA_FONT_SCALE_CHANGED, false);
        assertFalse(contract.parseResult(Activity.RESULT_OK, intent));
    }

    @Test
    public void startSettingsContract_parseResult_resultCodeIgnored() {
        // parseResult 只看 intent 里的 flag，不看 resultCode
        ActivityResultHelpers.StartSettingsContract contract =
                new ActivityResultHelpers.StartSettingsContract();
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.RESULT_EXTRA_THEME_CHANGED, true);
        assertTrue(contract.parseResult(Activity.RESULT_CANCELED, intent));
    }

    @Test
    public void successCallback_resultOkInvokesCallback() {
        AtomicBoolean called = new AtomicBoolean(false);
        ActivityResultHelpers.ActivityResultSuccessCallback cb =
                new ActivityResultHelpers.ActivityResultSuccessCallback(
                        () -> called.set(true));
        cb.onActivityResult(new ActivityResult(Activity.RESULT_OK, null));
        assertTrue(called.get());
    }

    @Test
    public void successCallback_resultCanceledDoesNotInvoke() {
        AtomicBoolean called = new AtomicBoolean(false);
        ActivityResultHelpers.ActivityResultSuccessCallback cb =
                new ActivityResultHelpers.ActivityResultSuccessCallback(
                        () -> called.set(true));
        cb.onActivityResult(new ActivityResult(Activity.RESULT_CANCELED, null));
        assertFalse(called.get());
    }

    @Test
    public void successCallback_resultFirstUserDoesNotInvoke() {
        AtomicBoolean called = new AtomicBoolean(false);
        ActivityResultHelpers.ActivityResultSuccessCallback cb =
                new ActivityResultHelpers.ActivityResultSuccessCallback(
                        () -> called.set(true));
        cb.onActivityResult(new ActivityResult(Activity.RESULT_FIRST_USER, null));
        assertFalse(called.get());
    }

    @Test
    public void successCallback_resultOkWithIntentDataStillInvokes() {
        AtomicBoolean called = new AtomicBoolean(false);
        ActivityResultHelpers.ActivityResultSuccessCallback cb =
                new ActivityResultHelpers.ActivityResultSuccessCallback(
                        () -> called.set(true));
        cb.onActivityResult(new ActivityResult(Activity.RESULT_OK, new Intent()));
        assertTrue(called.get());
    }
}
