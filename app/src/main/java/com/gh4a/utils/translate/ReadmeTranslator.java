package com.gh4a.utils.translate;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates README HTML segment by segment, preserving HTML structure.
 * Text inside pre/code/script/style blocks is skipped.
 * Translation runs on a background thread with progressive callbacks
 * on the main thread.
 */
public class ReadmeTranslator {
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>|[^<]+");
    private static final Pattern TAG_NAME_PATTERN =
            Pattern.compile("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)");
    // Tags whose content should not be translated
    private static final String[] SKIP_TAGS = {"pre", "code", "script", "style"};
    // Probe timeout: fail fast on a dead provider instead of burning the
    // full network timeout on every segment (looks like a freeze).
    private static final int PROBE_TIMEOUT_SECONDS = 10;

    public interface Callback {
        /** Called on main thread as segments complete. */
        void onProgress(int done, int total);
        /** Called on main thread when all done. */
        void onComplete(String translatedHtml);
        /** Called on main thread on failure. */
        void onError(Exception e);
    }

    private final Translator mTranslator;
    private final String mSourceLang;
    private final String mTargetLang;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mCancelled;
    private volatile ExecutorService mExecutor;

    public ReadmeTranslator(Translator translator, String sourceLang, String targetLang) {
        mTranslator = translator;
        mSourceLang = sourceLang;
        mTargetLang = targetLang;
    }

    public void cancel() {
        mCancelled = true;
        ExecutorService executor = mExecutor;
        if (executor != null) {
            // drop queued segments; in-flight requests finish but their
            // results are discarded via the mCancelled checks
            executor.shutdownNow();
        }
    }

    /** Start translation on a background thread. */
    public void translate(final String html, final Callback callback) {
        new Thread(() -> {
            try {
                doTranslate(html, callback);
            } catch (Exception e) {
                postError(callback, e);
            }
        }).start();
    }

    private void doTranslate(String html, Callback callback) throws Exception {
        List<Segment> segments = parse(html);
        // collect translatable segments
        List<Segment> toTranslate = new ArrayList<>();
        for (Segment s : segments) {
            if (s.translatable && !TextUtils.isEmpty(s.text.trim())
                    && containsTranslatableChar(s.text)) {
                toTranslate.add(s);
            }
        }
        int total = toTranslate.size();
        if (total == 0) {
            final String empty = buildHtml(segments);
            mMainHandler.post(() -> callback.onComplete(empty));
            return;
        }
        probeProvider();
        // Translate segments in parallel: sequential translation of a long
        // README takes minutes, parallel cuts it to a fraction of that.
        // Each provider declares its own safe concurrency (Baidu: 1 QPS).
        int parallelism = Math.max(1,
                Math.min(mTranslator.getMaxParallelRequests(), total));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        mExecutor = executor;
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicReference<Exception> lastError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(total);
        try {
            for (Segment s : toTranslate) {
                executor.execute(() -> {
                    try {
                        if (mCancelled) {
                            return;
                        }
                        try {
                            String translated =
                                    mTranslator.translate(s.text, mSourceLang, mTargetLang);
                            if (!mCancelled && !TextUtils.isEmpty(translated)) {
                                s.text = translated;
                            }
                        } catch (Exception e) {
                            // keep original text for this segment, continue with others
                            failed.incrementAndGet();
                            lastError.set(e);
                        }
                        int fDone = done.incrementAndGet();
                        mMainHandler.post(() -> callback.onProgress(fDone, total));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } finally {
            executor.shutdown();
            mExecutor = null;
        }
        if (mCancelled) {
            return;
        }
        if (failed.get() == total) {
            // every segment failed: surface the real cause instead of
            // silently showing unchanged content
            Exception error = lastError.get() != null
                    ? lastError.get() : new Exception("all translation requests failed");
            mMainHandler.post(() -> callback.onError(error));
            return;
        }
        final String result = buildHtml(segments);
        mMainHandler.post(() -> callback.onComplete(result));
    }

    private void postError(Callback callback, Exception e) {
        mMainHandler.post(() -> callback.onError(e));
    }

    /**
     * Translate a tiny probe text with a short timeout before starting the
     * real work. A dead/unreachable provider would otherwise burn the full
     * network timeout on every single segment, making the app look frozen
     * for many minutes with no way to tell what is wrong.
     */
    private void probeProvider() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> probe = executor.submit(
                () -> mTranslator.translate("Hello", mSourceLang, mTargetLang));
        try {
            probe.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            probe.cancel(true);
            throw new IOException("translation provider not responding "
                    + "(timeout after " + PROBE_TIMEOUT_SECONDS + "s)");
        } catch (Exception e) {
            probe.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
            throw new IOException("translation provider check failed: " + detail);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Rough check: skip pure punctuation/numbers/whitespace. */
    private static boolean containsTranslatableChar(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                return true;
            }
            // CJK counts as letter in most cases, but be explicit
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return true;
            }
        }
        return false;
    }

    private static class Segment {
        String text;          // raw text or full tag
        boolean isTag;
        boolean translatable; // false for tags and skipped content
    }

    private static List<Segment> parse(String html) {
        List<Segment> segments = new ArrayList<>();
        Matcher m = TAG_PATTERN.matcher(html);
        int skipDepth = 0;
        while (m.find()) {
            String token = m.group();
            Segment s = new Segment();
            if (token.startsWith("<")) {
                s.isTag = true;
                s.text = token;
                s.translatable = false;
                String tagName = getTagName(token);
                if (isSkipTag(tagName)) {
                    if (token.startsWith("</")) {
                        skipDepth = Math.max(0, skipDepth - 1);
                    } else if (!token.endsWith("/>")) {
                        skipDepth++;
                    }
                }
            } else {
                s.isTag = false;
                s.text = token;
                // decode common entities for translation, re-encode after
                s.translatable = skipDepth == 0;
            }
            segments.add(s);
        }
        return segments;
    }

    private static String getTagName(String tag) {
        Matcher m = TAG_NAME_PATTERN.matcher(tag);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        return "";
    }

    private static boolean isSkipTag(String tagName) {
        for (String t : SKIP_TAGS) {
            if (t.equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    private static String buildHtml(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            sb.append(s.text);
        }
        return sb.toString();
    }
}
