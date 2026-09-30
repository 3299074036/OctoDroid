package com.gh4a.utils.translate;

import android.content.Context;
import android.widget.Toast;

import com.gh4a.R;

import java.util.List;

/**
 * Drives the "translate whole thread" toolbar button shared by the issue/PR
 * and review pages: start / progress / cancel / switch back to original,
 * plus auto-translating newly loaded pages while the mode is on.
 *
 * The per-item cache and translation engine live in {@link CommentTranslator};
 * this class only owns the button state machine.
 */
public class ThreadTranslateController {
    public interface Host {
        Context getContext();
        CommentTranslator getTranslator();
        /** All currently loaded translatable items (body + comments/reviews). */
        List<CommentTranslator.TranslatableItem> collectThreadItems();
        /** Refresh visible content after translations changed. */
        void onTranslationsChanged();
        /** Refresh the toolbar button title. */
        void onTranslateStateChanged();
    }

    public static final int STATE_IDLE = 0;
    public static final int STATE_TRANSLATING = 1;
    public static final int STATE_DONE = 2;

    private final Host mHost;
    private boolean mThreadMode;
    private int mDone;
    private int mTotal;
    private String mBatchProvider;

    public ThreadTranslateController(Host host) {
        mHost = host;
    }

    public int getState() {
        CommentTranslator translator = mHost.getTranslator();
        if (translator == null) {
            return STATE_IDLE;
        }
        if (mThreadMode && translator.isBatchRunning()) {
            return STATE_TRANSLATING;
        }
        if (mThreadMode && translator.hasTranslations()) {
            return STATE_DONE;
        }
        return STATE_IDLE;
    }

    public int getDone() {
        return mDone;
    }

    public int getTotal() {
        return mTotal;
    }

    public void toggle() {
        Host host = mHost;
        Context context = host.getContext();
        CommentTranslator translator = host.getTranslator();
        if (context == null || translator == null) {
            return;
        }
        if (translator.isBatchRunning()) {
            translator.cancelBatch();
            mThreadMode = false;
            mBatchProvider = null;
            Toast.makeText(context, R.string.translate_cancelled, Toast.LENGTH_SHORT).show();
            host.onTranslateStateChanged();
            return;
        }
        if (mThreadMode) {
            // Everything is translated: switch the whole thread back to original.
            translator.clearTranslations();
            mThreadMode = false;
            mBatchProvider = null;
            mDone = 0;
            mTotal = 0;
            host.onTranslationsChanged();
            host.onTranslateStateChanged();
            return;
        }
        List<CommentTranslator.TranslatableItem> items = host.collectThreadItems();
        if (items.isEmpty()) {
            Toast.makeText(context, R.string.translate_nothing_to_translate,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mThreadMode = true;
        mDone = 0;
        mTotal = items.size();
        host.onTranslateStateChanged();
        // Tap: translate directly with the saved default provider, no dialog.
        boolean started = TranslationUiHelper.runWithDefaultProvider(context, provider -> {
            mBatchProvider = provider;
            translator.translateAllWithProvider(context, provider, items, mBatchCallback);
        });
        if (!started) {
            mThreadMode = false;
            mDone = 0;
            mTotal = 0;
            Toast.makeText(context, R.string.translate_no_provider, Toast.LENGTH_LONG).show();
            host.onTranslateStateChanged();
        }
    }

    /**
     * Long-press on the toolbar translate button: pop the provider picker,
     * remember the choice as the default provider, and re-translate the
     * whole thread with it when a translation is currently shown.
     */
    public void onLongPress() {
        Host host = mHost;
        Context context = host.getContext();
        CommentTranslator translator = host.getTranslator();
        if (context == null || translator == null) {
            return;
        }
        TranslationUiHelper.pickProviderAndRun(context, provider -> {
            String name = TranslationManager.getProviderDisplayName(context, provider);
            Toast.makeText(context,
                    context.getString(R.string.translate_provider_switched, name),
                    Toast.LENGTH_SHORT).show();
            if (!mThreadMode) {
                return;
            }
            // Re-translate the whole thread with the newly chosen provider.
            translator.clearTranslations();
            mBatchProvider = provider;
            List<CommentTranslator.TranslatableItem> items = host.collectThreadItems();
            mDone = 0;
            mTotal = items.size();
            host.onTranslationsChanged();
            host.onTranslateStateChanged();
            translator.translateAllWithProvider(context, provider, items, mBatchCallback);
        });
    }

    /**
     * Feed newly loaded timeline items in; they are translated automatically
     * while the thread mode is on. Reuses the initially chosen provider so the
     * user is not asked again.
     */
    public void onNewItems(List<CommentTranslator.TranslatableItem> newItems) {
        if (!mThreadMode || newItems.isEmpty() || mBatchProvider == null) {
            return;
        }
        Host host = mHost;
        Context context = host.getContext();
        CommentTranslator translator = host.getTranslator();
        if (context == null || translator == null) {
            return;
        }
        translator.translateAllWithProvider(context, mBatchProvider, newItems, mBatchCallback);
    }

    private final CommentTranslator.BatchCallback mBatchCallback =
            new CommentTranslator.BatchCallback() {
                @Override
                public void onProgress(int done, int total) {
                    mDone = done;
                    mTotal = total;
                    mHost.onTranslationsChanged();
                    mHost.onTranslateStateChanged();
                }

                @Override
                public void onComplete() {
                    mHost.onTranslationsChanged();
                    mHost.onTranslateStateChanged();
                }

                @Override
                public void onCancelled() {
                    mThreadMode = false;
                    mBatchProvider = null;
                    mHost.onTranslateStateChanged();
                }

                @Override
                public void onError(Exception e) {
                    mThreadMode = false;
                    mBatchProvider = null;
                    mHost.onTranslateStateChanged();
                }
            };
}
