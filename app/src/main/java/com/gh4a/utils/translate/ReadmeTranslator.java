package com.gh4a.utils.translate;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.gh4a.utils.Background;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>|[^<]+|<");
    private static final Pattern TAG_NAME_PATTERN =
            Pattern.compile("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)");
    // 240：待解码的实体（decodeEntities 用）；单遍替换，不重复解码。
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "&(#\\d+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);");
    // Tags whose content should not be translated
    private static final String[] SKIP_TAGS = {"pre", "code", "script", "style"};
    // Probe timeout: fail fast on a dead provider instead of burning the
    // full network timeout on every segment (looks like a freeze).
    private static final int PROBE_TIMEOUT_SECONDS = 10;

    // P13：probe 结果按 provider 缓存短 TTL——整 thread 批量翻译时每条
    // 评论都新建 ReadmeTranslator，每次 doTranslate 都先跑一次 probe
    //（Baidu 下还先睡满 1.1s 节流），N 条即 N 次计费调用 + N 倍耗时。
    // 成功与失败都缓存 60s：批量内只探一次；死提供方时后续条目直接
    // fail-fast，不再重复烧 probe 调用。键为 provider 类 + 语言对。
    private static final long PROBE_CACHE_TTL_MS = 60_000;
    private static final Map<String, Long> sProbeOkAt = new HashMap<>();
    private static final Map<String, Long> sProbeFailAt = new HashMap<>();

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
        Background.run("readme-translate", () -> {
            try {
                doTranslate(html, callback);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
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
        probeProviderIfNeeded();
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
     * P13：批量内只探一次的缓存版 probe。60s 内同一 provider + 语言对
     * 探活成功过即跳过；60s 内探活失败过即直接 fail-fast，不再重复发
     * probe 翻译调用。
     */
    private void probeProviderIfNeeded() throws Exception {
        String key = mTranslator.getClass().getName()
                + "|" + mSourceLang + "|" + mTargetLang;
        synchronized (ReadmeTranslator.class) {
            Long okAt = sProbeOkAt.get(key);
            if (okAt != null
                    && System.currentTimeMillis() - okAt < PROBE_CACHE_TTL_MS) {
                return;
            }
            Long failAt = sProbeFailAt.get(key);
            if (failAt != null
                    && System.currentTimeMillis() - failAt < PROBE_CACHE_TTL_MS) {
                throw new IOException(
                        "translation provider check failed (recent probe failed)");
            }
        }
        try {
            probeProvider();
        } catch (Exception e) {
            synchronized (ReadmeTranslator.class) {
                sProbeFailAt.put(key, System.currentTimeMillis());
            }
            throw e;
        }
        synchronized (ReadmeTranslator.class) {
            sProbeOkAt.put(key, System.currentTimeMillis());
            sProbeFailAt.remove(key);
        }
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
            // 240：只有带标签名的 <... 才是标签；getTagName 为空的
            // <... token（裸 < 比较、x < 3 and y > z）按文本段处理
            //（原先或被当成标签跳过翻译、或裸 < 直接丢弃）。
            String tagName = token.startsWith("<") ? getTagName(token) : null;
            if (tagName != null && !tagName.isEmpty()) {
                s.isTag = true;
                s.text = token;
                s.translatable = false;
                if (isSkipTag(tagName)) {
                    if (token.startsWith("</")) {
                        skipDepth = Math.max(0, skipDepth - 1);
                    } else if (!token.endsWith("/>")) {
                        skipDepth++;
                    }
                }
            } else {
                s.isTag = false;
                // 按注释承诺先解码实体再翻译，拼回前重编码（见 buildHtml）。
                s.text = decodeEntities(token);
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

    /** 240：解码常见实体再翻译（单遍替换，不重复解码）；未知/非法实体原样保留。 */
    private static String decodeEntities(String text) {
        Matcher m = ENTITY_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String entity = m.group(1);
            String replacement;
            switch (entity) {
                case "amp": replacement = "&"; break;
                case "lt": replacement = "<"; break;
                case "gt": replacement = ">"; break;
                case "quot": replacement = "\""; break;
                case "apos": replacement = "'"; break;
                case "nbsp": replacement = "\u00A0"; break;
                default: {
                    // 数字实体：&#60; / &#x3c;
                    int codePoint;
                    try {
                        if (entity.charAt(1) == 'x' || entity.charAt(1) == 'X') {
                            codePoint = Integer.parseInt(entity.substring(2), 16);
                        } else {
                            codePoint = Integer.parseInt(entity.substring(1));
                        }
                    } catch (NumberFormatException e) {
                        codePoint = -1;
                    }
                    replacement = codePoint >= 0
                            && Character.isValidCodePoint(codePoint)
                            ? new String(Character.toChars(codePoint))
                            : m.group();
                    break;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String buildHtml(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            // 240：文本段（含译文）拼回前 HTML 转义，否则译文里的 </& 会
            // 破坏渲染结构；标签段原样拼回。
            sb.append(s.isTag ? s.text : TextUtils.htmlEncode(s.text));
        }
        return sb.toString();
    }
}
