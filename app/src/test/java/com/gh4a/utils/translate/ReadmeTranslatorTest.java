package com.gh4a.utils.translate;

import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
}
