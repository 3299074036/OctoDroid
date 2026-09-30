package com.gh4a.utils.translate;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;

import java.util.List;

/**
 * Shared "pick a translation provider" dialog, used by README translation
 * and issue/PR comment translation alike.
 *
 * Skips the dialog when there is only one usable provider; the user's
 * choice becomes the default provider in settings.
 */
public class TranslationUiHelper {
    public interface ProviderCallback {
        void onProviderSelected(String provider);
    }

    /**
     * Run with the saved default provider directly, without showing any
     * dialog. Used for the tap gesture ("just translate").
     *
     * @return false when no provider is usable at all; the caller should
     *         then prompt the user to configure one in settings.
     */
    public static boolean runWithDefaultProvider(Context context, ProviderCallback callback) {
        List<String> providers = TranslationManager.getConfiguredProviders(context);
        if (providers.isEmpty()) {
            return false;
        }
        String def = TranslationManager.getProvider(context);
        if (!providers.contains(def)) {
            def = providers.get(0);
        }
        callback.onProviderSelected(def);
        return true;
    }

    public static void pickProviderAndRun(Context context, ProviderCallback callback) {
        List<String> providers = TranslationManager.getConfiguredProviders(context);
        if (providers.isEmpty()) {
            return;
        }
        if (providers.size() == 1) {
            callback.onProviderSelected(providers.get(0));
            return;
        }
        String current = TranslationManager.getProvider(context);
        String[] names = new String[providers.size()];
        int checked = 0;
        for (int i = 0; i < providers.size(); i++) {
            names[i] = TranslationManager.getProviderDisplayName(context, providers.get(i));
            if (providers.get(i).equals(current)) {
                checked = i;
            }
        }
        final int[] selected = { checked };
        new AlertDialog.Builder(context)
                .setTitle(R.string.translate_choose_provider)
                .setSingleChoiceItems(names, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.translate, (dialog, which) -> {
                    String provider = providers.get(selected[0]);
                    TranslationManager.setProvider(context.getApplicationContext(), provider);
                    callback.onProviderSelected(provider);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
