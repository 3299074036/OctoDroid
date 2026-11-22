package com.gh4a.utils.translate;

import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ReadmeTranslator with hand-written fake Translators: no network.
 * Translation runs on a background thread and reports on the main thread,
 * so tests pump the paused main looper while waiting on a latch.
 * Each test uses its own target-language tag so the static 60s probe cache
 * (keyed by translator class + language pair) cannot leak between tests.
 */
@RunWith(RobolectricTestRunner.class)
public class ReadmeTranslatorTest {

    /** Fake that prefixes every segment with "TR:" and records inputs. */
    private static class PrefixTranslator implements Translator {
        final List<String> seen = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            seen.add(text);
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "Prefix";
        }
    }

    /** Fake that fails for one exact segment, translates the rest. */
    private static class PartialFailTranslator extends PrefixTranslator {
        private final String mBadSegment;

        PartialFailTranslator(String badSegment) {
            mBadSegment = badSegment;
        }

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            seen.add(text);
            if (mBadSegment.equals(text)) {
                throw new RuntimeException("fail:" + text);
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "PartialFail";
        }
    }

    /** Fake that always fails (its own class, so its probe failure cannot
     * poison the probe cache entry of the other fakes). */
    private static class AlwaysFailTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            throw new RuntimeException("provider down");
        }

        @Override
        public String getName() {
            return "AlwaysFail";
        }
    }

    /** Fake that blocks segment translation until released. */
    private static class GatedTranslator implements Translator {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok"; // probe passes straight through
            }
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("gate timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "Gated";
        }
    }

    /** Fake with maxParallelRequests == 1 that tracks peak concurrency. */
    private static class SerialTranslator implements Translator {
        final AtomicInteger live = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();

        @Override
        public String translate(String text, String sourceLang, String targetLang)
                throws Exception {
            int n = live.incrementAndGet();
            peak.accumulateAndGet(n, Math::max);
            try {
                Thread.sleep(80);
            } finally {
                live.decrementAndGet();
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "Serial";
        }

        @Override
        public int getMaxParallelRequests() {
            return 1;
        }
    }

    /** Fake whose probe succeeds but every segment fails. */
    private static class ProbeOkAllFailTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok";
            }
            throw new RuntimeException("segment down");
        }

        @Override
        public String getName() {
            return "ProbeOkAllFail";
        }
    }

    /** Fake that returns an empty translation for segments. */
    private static class EmptyResultTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok";
            }
            return "";
        }

        @Override
        public String getName() {
            return "EmptyResult";
        }
    }

    /** Fake that swallows the cancel interrupt and returns normally, so the
     * worker reaches the post-translate mCancelled check instead of the
     * segment catch. */
    private static class InterruptSwallowingTranslator implements Translator {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok";
            }
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("gate timeout");
                }
            } catch (InterruptedException e) {
                // cancel() called shutdownNow() on the segment executor;
                // swallow it and return normally on purpose.
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "InterruptSwallowing";
        }
    }

    /** Fake whose probe blocks forever (until interrupted by the probe
     * timeout path). */
    private static class HangingTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang)
                throws Exception {
            if ("Hello".equals(text)) {
                Thread.sleep(30_000);
                return "ok";
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "Hanging";
        }
    }

    /** Fake that interrupts the Background thread running doTranslate while
     * the probe is in flight, so Future.get throws InterruptedException
     * (null cause) instead of ExecutionException. The live thread is
     * identified by name plus a ReadmeTranslator frame on its stack, so
     * idle pool threads with a stale name are never touched. */
    private static class InterruptingProbeTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                outer:
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (!t.getName().startsWith("Background-readme-translate")) {
                        continue;
                    }
                    for (StackTraceElement e : t.getStackTrace()) {
                        if (e.getClassName().startsWith(
                                "com.gh4a.utils.translate.ReadmeTranslator")) {
                            t.interrupt();
                            break outer;
                        }
                    }
                }
                // Give the Background thread time to throw out of probe.get
                // before this Future completes, so the probe fails with the
                // wrapped IOException instead of returning "ok" and tripping
                // the sticky interrupt flag in latch.await() (raw
                // InterruptedException, null message).
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    // probe.cancel(true) fired after get() threw; fine.
                }
                return "ok";
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "InterruptingProbe";
        }
    }

    /** Fake that always fails and counts invocations. */
    private static class CountingFailTranslator implements Translator {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            calls.incrementAndGet();
            throw new RuntimeException("provider down");
        }

        @Override
        public String getName() {
            return "CountingFail";
        }
    }

    private static final class Outcome {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<int[]> progress = Collections.synchronizedList(new ArrayList<>());
        String result;
        Exception error;
        boolean completed;
        boolean errored;

        ReadmeTranslator.Callback callback() {
            return new ReadmeTranslator.Callback() {
                @Override
                public void onProgress(int done, int total) {
                    progress.add(new int[]{done, total});
                }

                @Override
                public void onComplete(String translatedHtml) {
                    result = translatedHtml;
                    completed = true;
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    error = e;
                    errored = true;
                    latch.countDown();
                }
            };
        }
    }

    private static boolean await(Outcome outcome) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (outcome.latch.await(50, TimeUnit.MILLISECONDS)) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                return true;
            }
        }
        return false;
    }

    private static boolean awaitLong(Outcome outcome, long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (outcome.latch.await(50, TimeUnit.MILLISECONDS)) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                return true;
            }
        }
        return false;
    }

    private static void pumpQuietly(long millis) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
    }

    @Test
    public void translate_plainTextIsTranslated() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-plain").translate("Hello world", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertEquals("TR:Hello world", out.result);
        // One probe call ("Hello") plus the one segment.
        assertEquals(2, fake.seen.size());
        assertEquals("Hello", fake.seen.get(0));
        assertEquals("Hello world", fake.seen.get(1));
        assertEquals(1, out.progress.size());
        assertEquals(1, out.progress.get(0)[0]);
        assertEquals(1, out.progress.get(0)[1]);
    }

    @Test
    public void translate_tagsPreservedAroundTranslatedText() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-tags")
                .translate("<p>Hello</p><b>World</b>", out.callback());
        assertTrue("timed out", await(out));
        assertEquals("<p>TR:Hello</p><b>TR:World</b>", out.result);
    }

    @Test
    public void translate_preAndCodeContentSkipped() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-skip")
                .translate("<pre>code here</pre><p>text</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.result.contains("code here"));
        assertTrue(out.result.contains("TR:text"));
        assertFalse("code must never be sent to the translator",
                fake.seen.contains("code here"));
        assertTrue(fake.seen.contains("text"));
    }

    @Test
    public void translate_scriptAndStyleContentSkipped() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-script")
                .translate("<script>var x = 1;</script><style>.a{}</style><p>hi</p>",
                        out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.result.contains("var x = 1;"));
        assertTrue(out.result.contains("TR:hi"));
        assertFalse(fake.seen.contains("var x = 1;"));
    }

    @Test
    public void translate_emptyHtmlCompletesWithoutCallingTranslator() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-empty").translate("", out.callback());
        assertTrue("timed out", await(out));
        assertEquals("", out.result);
        assertTrue(fake.seen.isEmpty());
    }

    @Test
    public void translate_punctuationOnlyCompletesUnchanged() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-punct").translate("123 !!!", out.callback());
        assertTrue("timed out", await(out));
        assertEquals("123 !!!", out.result);
        assertTrue(fake.seen.isEmpty());
    }

    @Test
    public void translate_entitiesDecodedBeforeTranslateAndReEncoded() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-entity")
                .translate("a &amp; b", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("a & b"));
        assertEquals("TR:a &amp; b", out.result);
    }

    @Test
    public void translate_numericEntityDecoded() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-nument")
                .translate("&#60;tag&#62;", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("<tag>"));
        assertEquals("TR:&lt;tag&gt;", out.result);
    }

    @Test
    public void translate_bareLessThanIsTextNotDropped() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-barelt")
                .translate("x < 3 and y > z", out.callback());
        assertTrue("timed out", await(out));
        assertTrue("bare < text must reach the translator, got " + fake.seen,
                fake.seen.contains("< 3 and y >"));
        assertTrue(out.result.contains("&lt;"));
        assertTrue(out.result.contains("&gt;"));
        assertFalse(out.result.contains("< 3"));
    }

    @Test
    public void translate_partialFailureKeepsOriginalSegment() throws Exception {
        PartialFailTranslator fake = new PartialFailTranslator("bad");
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-partial")
                .translate("<p>good</p><p>bad</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertEquals("<p>TR:good</p><p>bad</p>", out.result);
    }

    @Test
    public void translate_allSegmentsFailReportsError() throws Exception {
        AlwaysFailTranslator fake = new AlwaysFailTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-allfail")
                .translate("<p>one</p><p>two</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.errored);
        assertTrue(out.error.getMessage().contains("provider down"));
    }

    @Test
    public void translate_probeFailureReportsErrorFast() throws Exception {
        AlwaysFailTranslator fake = new AlwaysFailTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-probefail")
                .translate("<p>hello</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.errored);
        assertTrue(out.error.getMessage().contains("provider check failed"));
    }

    @Test
    public void translate_progressCountsAllSegments() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-progress")
                .translate("<p>one</p><p>two</p><p>three</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertEquals(3, out.progress.size());
        // Progress fires per segment completion; with parallel translation
        // the order is not the segment order, only the counts are fixed.
        List<Integer> doneCounts = new ArrayList<>();
        for (int[] p : out.progress) {
            assertEquals(3, p[1]);
            doneCounts.add(p[0]);
        }
        Collections.sort(doneCounts);
        assertEquals(Arrays.asList(1, 2, 3), doneCounts);
    }

    @Test
    public void translate_respectsMaxParallelRequestsOne() throws Exception {
        SerialTranslator fake = new SerialTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-serial")
                .translate("<p>one</p><p>two</p><p>three</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertEquals("peak concurrency must stay 1 for a 1-QPS provider",
                1, fake.peak.get());
    }

    @Test
    public void cancel_suppressesCompletion() throws Exception {
        GatedTranslator fake = new GatedTranslator();
        Outcome out = new Outcome();
        ReadmeTranslator rt = new ReadmeTranslator(fake, "auto", "zz-cancel");
        rt.translate("<p>hello</p>", out.callback());
        assertTrue("segment never started", fake.entered.await(10, TimeUnit.SECONDS));
        rt.cancel();
        fake.release.countDown();
        pumpQuietly(1500);
        assertFalse("cancelled translation must not complete", out.completed);
        assertFalse("cancelled translation must not error", out.errored);
        assertEquals("no callback must have fired", 1, out.latch.getCount());
    }

    @Test
    public void cancel_beforeTranslate_suppressesEverything() throws Exception {
        // cancel() with no executor yet (78 false half); workers see
        // mCancelled at entry (128 true half, 129 return); no callback.
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        ReadmeTranslator rt = new ReadmeTranslator(fake, "auto", "zz-precancel");
        rt.cancel();
        rt.translate("<p>hello</p>", out.callback());
        pumpQuietly(1500);
        assertFalse(out.completed);
        assertFalse(out.errored);
        assertEquals(1, out.latch.getCount());
        // Only the probe ran; no segment ever reached the translator.
        assertEquals(Collections.singletonList("Hello"), fake.seen);
    }

    @Test
    public void translate_whitespaceOnlySegmentSkipped() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-wsseg")
                .translate("<p>a</p> <p>b</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        // The whitespace-only text segment is filtered (trim is empty),
        // never sent to the translator.
        assertFalse(fake.seen.contains(" "));
        assertTrue(fake.seen.contains("a"));
        assertTrue(fake.seen.contains("b"));
        assertEquals("<p>TR:a</p> <p>TR:b</p>", out.result);
    }

    @Test
    public void translate_allSegmentsFailReportsRealError() throws Exception {
        // Probe passes, every segment fails: the all-failed branch
        // (failed == total) surfaces the real cause via onError.
        ProbeOkAllFailTranslator fake = new ProbeOkAllFailTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-allsegfail")
                .translate("<p>one</p><p>two</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.errored);
        assertTrue(out.error.getMessage().contains("segment down"));
    }

    @Test
    public void translate_emptyTranslationKeepsOriginal() throws Exception {
        EmptyResultTranslator fake = new EmptyResultTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-emptyres")
                .translate("<p>hello</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertEquals("<p>hello</p>", out.result);
    }

    @Test
    public void cancel_duringSegmentTranslation_skipsAssignment() throws Exception {
        // The segment translator swallows the shutdownNow interrupt and
        // returns normally, so the worker reaches the post-translate
        // mCancelled check (134) instead of the segment catch.
        InterruptSwallowingTranslator fake = new InterruptSwallowingTranslator();
        Outcome out = new Outcome();
        ReadmeTranslator rt = new ReadmeTranslator(fake, "auto", "zz-cancelmid");
        rt.translate("<p>hello</p>", out.callback());
        assertTrue("segment never started", fake.entered.await(10, TimeUnit.SECONDS));
        rt.cancel();
        fake.release.countDown();
        pumpQuietly(1500);
        assertFalse("cancelled translation must not complete", out.completed);
        assertFalse("cancelled translation must not error", out.errored);
        assertEquals("no callback must have fired", 1, out.latch.getCount());
    }

    @Test
    public void translate_probeOkCachedAcrossTranslates() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        ReadmeTranslator rt = new ReadmeTranslator(fake, "auto", "zz-cacheok");
        Outcome first = new Outcome();
        rt.translate("<p>one</p>", first.callback());
        assertTrue("timed out", await(first));
        assertTrue(first.completed);
        Outcome second = new Outcome();
        rt.translate("<p>two</p>", second.callback());
        assertTrue("timed out", await(second));
        assertTrue(second.completed);
        // The probe ("Hello") ran once for both translates: the second
        // translate hit the 60s probe-ok cache and skipped it.
        assertEquals(Arrays.asList("Hello", "one", "two"), fake.seen);
        assertEquals("<p>TR:two</p>", second.result);
    }

    @Test
    public void translate_probeFailCachedFailsFast() throws Exception {
        CountingFailTranslator fake = new CountingFailTranslator();
        ReadmeTranslator rt = new ReadmeTranslator(fake, "auto", "zz-failcache");
        Outcome first = new Outcome();
        rt.translate("<p>hello</p>", first.callback());
        assertTrue("timed out", await(first));
        assertTrue(first.errored);
        Outcome second = new Outcome();
        rt.translate("<p>hello</p>", second.callback());
        assertTrue("timed out", await(second));
        assertTrue(second.errored);
        assertTrue(second.error.getMessage().contains("recent probe failed"));
        // The second translate never called the provider again.
        assertEquals(1, fake.calls.get());
    }

    @Test
    public void translate_probeTimeoutReportsError() throws Exception {
        HangingTranslator fake = new HangingTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-timeout")
                .translate("<p>hello</p>", out.callback());
        assertTrue("timed out", awaitLong(out, 20_000));
        assertTrue(out.errored);
        assertTrue(out.error.getMessage().contains("not responding (timeout after 10s)"));
    }

    @Test
    public void translate_probeInterruptedReportsError() throws Exception {
        InterruptingProbeTranslator fake = new InterruptingProbeTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-interrupt")
                .translate("<p>hello</p>", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.errored);
        assertTrue(out.error.getMessage().contains("InterruptedException"));
    }

    @Test
    public void translate_namedEntitiesDecoded() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-namedent")
                .translate("a &lt; b &gt; c &quot;d&quot; e &apos;f&apos; g &nbsp; h",
                        out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("a < b > c \"d\" e 'f' g \u00A0 h"));
    }

    @Test
    public void translate_hexEntitiesDecoded() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-hexent")
                .translate("&#x3c;tag&#X3e;", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("<tag>"));
        assertEquals("TR:&lt;tag&gt;", out.result);
    }

    @Test
    public void translate_hugeNumericEntityPreserved() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-hugeent")
                .translate("a &#99999999999; b", out.callback());
        assertTrue("timed out", await(out));
        // parseInt overflows -> NumberFormatException -> codePoint -1 ->
        // the original entity text is kept, not decoded.
        assertTrue(fake.seen.contains("a &#99999999999; b"));
    }

    @Test
    public void translate_invalidCodePointEntityPreserved() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-badcp")
                .translate("a &#x110000; b", out.callback());
        assertTrue("timed out", await(out));
        // 0x110000 is above MAX_CODE_POINT: isValidCodePoint is false,
        // so the original entity text is kept, not decoded.
        assertTrue(fake.seen.contains("a &#x110000; b"));
    }

    @Test
    public void translate_surrogateEntityPreservedVerbatim() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-surrdec")
                .translate("a &#xD800; b", out.callback());
        assertTrue("timed out", await(out));
        // T-128 (fixed): surrogate code points are excluded from entity
        // decoding, so the entity is preserved verbatim instead of
        // decoding to a lone surrogate char.
        assertTrue(fake.seen.contains("a &#xD800; b"));
    }

    @Test
    public void translate_hiraganaNonLetterCountsAsTranslatable() throws Exception {
        // U+3097 is unassigned: not a letter, but in the HIRAGANA block,
        // so the explicit block check (not isLetter) marks it translatable.
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-hira")
                .translate("\u3097", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("\u3097"));
    }

    @Test
    public void translate_katakanaNonLetterCountsAsTranslatable() throws Exception {
        // U+30FB middle dot: not a letter, in the KATAKANA block.
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-kata")
                .translate("\u30FB", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("\u30FB"));
    }

    @Test
    public void translate_selfClosingSkipTagDoesNotSkipFollowingText() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-selfclose")
                .translate("<code/>hello", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(out.completed);
        assertTrue(fake.seen.contains("hello"));
        assertEquals("<code/>TR:hello", out.result);
    }

    /** Seed a stale probe-cache entry so the TTL check takes its (T,F) arm. */
    @SuppressWarnings("unchecked")
    private static void seedStaleProbeCache(String field, String key) throws Exception {
        Field f = ReadmeTranslator.class.getDeclaredField(field);
        f.setAccessible(true);
        Map<String, Long> map = (Map<String, Long>) f.get(null);
        synchronized (ReadmeTranslator.class) {
            map.put(key, System.currentTimeMillis() - 61_000);
        }
    }

    @Test
    public void translate_staleProbeOkCacheReprobes() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out1 = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-staleok")
                .translate("<p>hello</p>", out1.callback());
        assertTrue("timed out", await(out1));
        assertTrue(out1.completed);
        assertEquals(1, Collections.frequency(fake.seen, "Hello"));
        seedStaleProbeCache("sProbeOkAt",
                PrefixTranslator.class.getName() + "|auto|zz-staleok");
        Outcome out2 = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-staleok")
                .translate("<p>hello</p>", out2.callback());
        assertTrue("timed out", await(out2));
        assertTrue(out2.completed);
        // Stale ok entry: (T,F) arm, probe runs again instead of cache-hit.
        assertEquals(2, Collections.frequency(fake.seen, "Hello"));
    }

    @Test
    public void translate_staleProbeFailCacheReprobes() throws Exception {
        CountingFailTranslator fake = new CountingFailTranslator();
        Outcome out1 = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-stalefail")
                .translate("<p>hello</p>", out1.callback());
        assertTrue("timed out", await(out1));
        assertTrue(out1.errored);
        assertEquals(1, fake.calls.get());
        seedStaleProbeCache("sProbeFailAt",
                CountingFailTranslator.class.getName() + "|auto|zz-stalefail");
        Outcome out2 = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-stalefail")
                .translate("<p>hello</p>", out2.callback());
        assertTrue("timed out", await(out2));
        assertTrue(out2.errored);
        // Stale fail entry: (T,F) arm, probe runs again instead of fail-fast.
        assertEquals(2, fake.calls.get());
    }
    @Test
    public void translate_hangulNonLetterCountsAsTranslatable() throws Exception {
        // U+D7A4：HANGUL_SYLLABLES 块内但不是 letter（isLetter 为假），
        // 显式块检查把它标为可翻译；CJK/扩展 A 的同款分支不可达（块内字符
        // 全是 letter，isLetter 先返回 true）。
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-hangul")
                .translate("\uD7A4", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("\uD7A4"));
    }

    @Test
    public void translate_emojiEntityDecoded() throws Exception {
        // &#128512;（U+1F600）：有效码位且大于 0xDFFF，解成 emoji 字符再翻译。
        PrefixTranslator fake = new PrefixTranslator();
        Outcome out = new Outcome();
        new ReadmeTranslator(fake, "auto", "zz-emoji")
                .translate("a &#128512; b", out.callback());
        assertTrue("timed out", await(out));
        assertTrue(fake.seen.contains("a \uD83D\uDE00 b"));
    }

}
