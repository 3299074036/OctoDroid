package com.gh4a.utils.translate;

import android.content.Context;
import android.widget.Toast;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-comment translation for issue/PR timelines.
 *
 * Owns the translated-HTML cache (keyed by comment id) and the in-flight
 * translation state. Fragments call {@link #toggleTranslation} from the
 * comment menu and {@link #getTranslatedHtml} when binding views.
 */
public class CommentTranslator {
    public interface Callback {
        /** Called on the main thread when a comment's display HTML changed. */
        void onTranslationChanged();
    }

    /** One translatable unit of a thread: the issue/PR body, a comment or a review. */
    public static class TranslatableItem {
        public final long id;
        public final String html;

        public TranslatableItem(long id, String html) {
            this.id = id;
            this.html = html;
        }
    }

    /** Progress reporting for {@link #translateAll}. All calls are on the main thread. */
    public interface BatchCallback {
        void onProgress(int done, int total);
        void onComplete();
        void onCancelled();
        void onError(Exception e);
    }

    private interface SingleCallback {
        void onSuccess();
        void onError();
    }

    private final Map<Long, String> mTranslated = new HashMap<>();
    private final Set<Long> mTranslating = new HashSet<>();
    private final Map<Long, ReadmeTranslator> mActiveTranslators = new HashMap<>();

    // Batch (whole-thread) translation state. Items are translated one after
    // another to stay kind to rate-limited providers (e.g. Baidu's 1 QPS).
    private List<TranslatableItem> mBatchItems;
    private int mBatchDone;
    private int mBatchTotal;
    private BatchCallback mBatchCallback;
    private String mBatchProvider;
    private boolean mBatchCancelled;
    private boolean mDestroyed;

    /**
     * Returns the translated HTML for a comment, or null when the original
     * should be shown.
     */
    public String getTranslatedHtml(long commentId) {
        return mTranslated.get(commentId);
    }

    /**
     * Returns true while any translation (single or batch) is in flight.
     */
    public boolean isTranslating() {
        return !mTranslating.isEmpty();
    }

    /**
     * Returns true while a whole-thread batch is running.
     */
    public boolean isBatchRunning() {
        return mBatchCallback != null;
    }

    /**
     * Returns true when at least one item has a cached translation.
     */
    public boolean hasTranslations() {
        return !mTranslated.isEmpty();
    }

    /**
     * Translate a batch of items one after another, reporting overall progress.
     * Items that are already translated are skipped. If a batch is already
     * running, the new items are appended to it.
     */
    public void translateAll(Context context, List<TranslatableItem> items, BatchCallback callback) {
        if (mDestroyed) {
            return;
        }
        TranslationUiHelper.pickProviderAndRun(context,
                provider -> translateAllWithProvider(context, provider, items, callback));
    }

    /**
     * Same as {@link #translateAll}, but with an explicitly chosen provider
     * (so follow-up batches don't ask again).
     */
    public void translateAllWithProvider(Context context, String provider,
            List<TranslatableItem> items, BatchCallback callback) {
        if (mDestroyed) {
            return;
        }
        List<TranslatableItem> pending = new ArrayList<>();
        for (TranslatableItem item : items) {
            if (item.html != null && !item.html.isEmpty() && !mTranslated.containsKey(item.id)) {
                pending.add(item);
            }
        }
        if (pending.isEmpty()) {
            callback.onComplete();
            return;
        }
        if (mBatchCallback != null) {
            // A batch is already running: append the new items to it.
            mBatchItems.addAll(pending);
            mBatchTotal = mBatchItems.size();
            callback.onProgress(mBatchDone, mBatchTotal);
            return;
        }
        startBatch(context, provider, pending, callback);
    }

    /**
     * Cancel a running batch. Already translated items stay cached.
     */
    public void cancelBatch() {
        cancelBatchInternal(true);
    }

    /**
     * Drop every cached translation, e.g. to switch a whole thread back to
     * the original language.
     */
    public void clearTranslations() {
        cancelBatchInternal(false);
        mTranslated.clear();
    }

    private void startBatch(Context context, String provider, List<TranslatableItem> items,
            BatchCallback callback) {
        if (mDestroyed) {
            return;
        }
        mBatchItems = new ArrayList<>(items);
        mBatchTotal = items.size();
        mBatchDone = 0;
        mBatchCallback = callback;
        mBatchProvider = provider;
        mBatchCancelled = false;
        translateNextBatchItem(context.getApplicationContext());
    }

    private void translateNextBatchItem(Context appContext) {
        if (mDestroyed || mBatchCancelled) {
            return;
        }
        if (mBatchDone >= mBatchItems.size()) {
            BatchCallback callback = mBatchCallback;
            mBatchCallback = null;
            mBatchItems = null;
            mBatchProvider = null;
            if (callback != null) {
                callback.onComplete();
            }
            return;
        }
        final TranslatableItem item = mBatchItems.get(mBatchDone);
        final BatchCallback callback = mBatchCallback;
        startSingleTranslation(appContext, mBatchProvider, item.id, item.html,
                new SingleCallback() {
                    @Override
                    public void onSuccess() {
                        if (mBatchCancelled || mDestroyed) {
                            return;
                        }
                        mBatchDone++;
                        if (callback != null) {
                            callback.onProgress(mBatchDone, mBatchTotal);
                        }
                        translateNextBatchItem(appContext);
                    }

                    @Override
                    public void onError() {
                        if (mBatchCancelled || mDestroyed) {
                            return;
                        }
                        // The error toast was already shown; skip the failed
                        // item and continue with the rest.
                        mBatchDone++;
                        if (callback != null) {
                            callback.onProgress(mBatchDone, mBatchTotal);
                        }
                        translateNextBatchItem(appContext);
                    }
                });
    }

    private void cancelBatchInternal(boolean notify) {
        mBatchCancelled = true;
        for (ReadmeTranslator translator : mActiveTranslators.values()) {
            translator.cancel();
        }
        mActiveTranslators.clear();
        mTranslating.clear();
        BatchCallback callback = mBatchCallback;
        mBatchCallback = null;
        mBatchItems = null;
        mBatchProvider = null;
        if (notify && callback != null) {
            callback.onCancelled();
        }
    }

    /**
     * Toggle translation for one comment: tap once to translate, tap again
     * to switch back to the original. Re-tapping while a translation is
     * in flight is ignored.
     */
    public void toggleTranslation(Context context, long commentId, String originalHtml,
            Callback callback) {
        if (mDestroyed) {
            return;
        }
        if (mTranslated.containsKey(commentId)) {
            mTranslated.remove(commentId);
            callback.onTranslationChanged();
            return;
        }
        if (mTranslating.contains(commentId)) {
            Toast.makeText(context, R.string.translating, Toast.LENGTH_SHORT).show();
            return;
        }
        TranslationUiHelper.pickProviderAndRun(context,
                provider -> startSingleTranslation(context, provider, commentId, originalHtml,
                        new SingleCallback() {
                            @Override
                            public void onSuccess() {
                                callback.onTranslationChanged();
                            }

                            @Override
                            public void onError() {
                                // Error toast already shown; nothing to refresh.
                            }
                        }));
    }

    private void startSingleTranslation(Context context, String provider, long commentId,
            String originalHtml, SingleCallback callback) {
        if (mDestroyed) {
            return;
        }
        Translator translator;
        try {
            translator = TranslationManager.createTranslator(context, provider);
        } catch (Exception e) {
            Toast.makeText(context, R.string.translate_no_provider, Toast.LENGTH_SHORT).show();
            callback.onError();
            return;
        }
        String targetLang = TranslationManager.getTargetLanguage(context);
        mTranslating.add(commentId);

        final Context appContext = context.getApplicationContext();
        final ReadmeTranslator readmeTranslator = new ReadmeTranslator(translator, "auto", targetLang);
        mActiveTranslators.put(commentId, readmeTranslator);
        readmeTranslator.translate(originalHtml, new ReadmeTranslator.Callback() {
            @Override
            public void onProgress(int done, int total) {
                // Per-comment translation is quick; no progress UI needed.
            }

            @Override
            public void onComplete(String translatedHtml) {
                mTranslating.remove(commentId);
                mActiveTranslators.remove(commentId);
                if (mDestroyed) {
                    return;
                }
                mTranslated.put(commentId, translatedHtml);
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                mTranslating.remove(commentId);
                mActiveTranslators.remove(commentId);
                if (mDestroyed) {
                    return;
                }
                String msg = e.getMessage() != null ? e.getMessage() : "";
                Toast.makeText(appContext,
                        appContext.getString(R.string.translate_failed)
                                + (msg.isEmpty() ? "" : ": " + msg),
                        Toast.LENGTH_LONG).show();
                callback.onError();
            }
        });
    }

    public void destroy() {
        mDestroyed = true;
        cancelBatchInternal(false);
    }
}
