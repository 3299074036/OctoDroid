package com.gh4a.utils.translate;

import android.content.Context;
import android.os.Looper;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * CommentTranslator with TranslationUiHelper.pickProviderAndRun and
 * TranslationManager.createTranslator statically stubbed, so the real
 * provider is never built and no network is involved. The underlying
 * ReadmeTranslator runs against a hand-written fake Translator.
 */
@RunWith(RobolectricTestRunner.class)
public class CommentTranslatorTest {

    /** Fake that prefixes segments with "TR:"; probe ("Hello") passes. */
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

    /** Fake that always fails (own class: its probe-failure cache entry is
     * separate from PrefixTranslator's). */
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
                return "ok";
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

    /** Fake that blocks segment translation until released and signals
     * when translate() has returned (so the test knows the completion
     * callback has been posted to the main looper). */
    private static class SignallingGatedTranslator implements Translator {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch returned = new CountDownLatch(1);

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
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                returned.countDown();
            }
            return "TR:" + text;
        }

        @Override
        public String getName() {
            return "SignallingGated";
        }
    }

    /** Fake whose probe passes but every segment fails, signalling when
     * the failure was thrown. */
    private static class SignallingFailTranslator implements Translator {
        final CountDownLatch thrown = new CountDownLatch(1);

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok";
            }
            thrown.countDown();
            throw new RuntimeException("segment down");
        }

        @Override
        public String getName() {
            return "SignallingFail";
        }
    }

    /** Fake whose probe passes but every segment fails with a
     * message-less exception. */
    private static class NoMessageFailTranslator implements Translator {
        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            if ("Hello".equals(text)) {
                return "ok";
            }
            throw new RuntimeException();
        }

        @Override
        public String getName() {
            return "NoMessageFail";
        }
    }

    private static final class BatchOutcome implements CommentTranslator.BatchCallback {
        final CountDownLatch done = new CountDownLatch(1);
        final List<int[]> progress = Collections.synchronizedList(new ArrayList<>());
        boolean completed;
        boolean cancelled;
        Exception error;

        @Override
        public void onProgress(int doneCount, int total) {
            progress.add(new int[]{doneCount, total});
        }

        @Override
        public void onComplete() {
            completed = true;
            done.countDown();
        }

        @Override
        public void onCancelled() {
            cancelled = true;
            done.countDown();
        }

        @Override
        public void onError(Exception e) {
            error = e;
            done.countDown();
        }
    }

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static boolean await(CountDownLatch latch) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(50, TimeUnit.MILLISECONDS)) {
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

    /**
     * Runs body with pickProviderAndRun -> "google" and createTranslator ->
     * fakeRef.get(). The fake can be swapped mid-test via the reference
     * (Mockito forbids nesting two static mocks of the same class).
     */
    private void withStubs(AtomicReference<Translator> fakeRef, RunnableWithMocks body)
            throws Exception {
        try (MockedStatic<TranslationUiHelper> ui =
                     Mockito.mockStatic(TranslationUiHelper.class);
             MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            ui.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("google");
                        return null;
                    });
            mgr.when(() -> TranslationManager.createTranslator(any(), eq("google")))
                    .thenAnswer(inv -> fakeRef.get());
            mgr.when(() -> TranslationManager.getTargetLanguage(any())).thenReturn("zh-CN");
            body.run();
        }
    }

    private void withStubs(Translator fake, RunnableWithMocks body) throws Exception {
        withStubs(new AtomicReference<>(fake), body);
    }

    private interface RunnableWithMocks {
        void run() throws Exception;
    }

    // ---------- TranslatableItem ----------

    @Test
    public void translatableItem_keyIsTypePlusId() {
        CommentTranslator.TranslatableItem item =
                new CommentTranslator.TranslatableItem("issue", 5, "<p>x</p>");
        assertEquals("issue:5", item.key());
        assertEquals(5, item.id);
        assertEquals("issue", item.type);
    }

    @Test
    public void translatableItem_defaultTypeIsComment() {
        CommentTranslator.TranslatableItem item =
                new CommentTranslator.TranslatableItem(7, "<p>x</p>");
        assertEquals("comment:7", item.key());
    }

    @Test
    public void translatableItem_sameNumericIdDifferentTypeDifferentKey() {
        assertFalse(new CommentTranslator.TranslatableItem("issue", 5, "a").key()
                .equals(new CommentTranslator.TranslatableItem("comment", 5, "a").key()));
    }

    // ---------- initial state ----------

    @Test
    public void initialState_empty() {
        CommentTranslator ct = new CommentTranslator();
        assertNull(ct.getTranslatedHtml("comment", 1));
        assertFalse(ct.hasTranslations());
        assertFalse(ct.isBatchRunning());
    }

    @Test
    public void translateAllWithProvider_emptyListCompletesImmediately() {
        CommentTranslator ct = new CommentTranslator();
        BatchOutcome out = new BatchOutcome();
        ct.translateAllWithProvider(context(), "google",
                Collections.emptyList(), out);
        assertTrue(out.completed);
        assertFalse(ct.isBatchRunning());
    }

    @Test
    public void translateAllWithProvider_nullOrEmptyHtmlSkipped() {
        CommentTranslator ct = new CommentTranslator();
        BatchOutcome out = new BatchOutcome();
        List<CommentTranslator.TranslatableItem> items = Arrays.asList(
                new CommentTranslator.TranslatableItem(1, null),
                new CommentTranslator.TranslatableItem(2, ""));
        ct.translateAllWithProvider(context(), "google", items, out);
        assertTrue(out.completed);
        assertFalse(ct.hasTranslations());
    }

    // ---------- batch translation ----------

    @Test
    public void translateAllWithProvider_translatesAndCaches() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>Hello</p>")),
                    out);
            assertTrue("timed out", await(out.done));
            assertTrue(out.completed);
            assertEquals(1, out.progress.size());
            assertEquals(1, out.progress.get(0)[0]);
            assertEquals(1, out.progress.get(0)[1]);
            assertEquals("<p>TR:Hello</p>", ct.getTranslatedHtml("comment", 1));
            assertTrue(ct.hasTranslations());
            assertFalse(ct.isBatchRunning());
        });
    }

    @Test
    public void translateAllWithProvider_batchOfTwoReportsProgress() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(1, "<p>one</p>"),
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("timed out", await(out.done));
            assertTrue(out.completed);
            assertEquals(2, out.progress.size());
            assertEquals(2, out.progress.get(1)[0]);
            assertEquals(2, out.progress.get(1)[1]);
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));
            assertEquals("<p>TR:two</p>", ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void translateAllWithProvider_alreadyTranslatedSkipped() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            List<CommentTranslator.TranslatableItem> items = Collections.singletonList(
                    new CommentTranslator.TranslatableItem(1, "<p>Hello</p>"));
            BatchOutcome first = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", items, first);
            assertTrue("timed out", await(first.done));
            int callsAfterFirst = fake.seen.size();
            assertTrue(callsAfterFirst > 0);
            BatchOutcome second = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", items, second);
            assertTrue(second.completed);
            assertEquals("already-translated item must not re-translate",
                    callsAfterFirst, fake.seen.size());
        });
    }

    @Test
    public void translateAll_goesThroughProviderPicker() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAll(context(), Collections.singletonList(
                    new CommentTranslator.TranslatableItem(3, "<p>hi</p>")), out);
            assertTrue("timed out", await(out.done));
            assertTrue(out.completed);
            assertEquals("<p>TR:hi</p>", ct.getTranslatedHtml("comment", 3));
        });
    }

    @Test
    public void translateAllWithProvider_failingItemsContinueAndToastOnce() throws Exception {
        AlwaysFailTranslator fake = new AlwaysFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(1, "<p>one</p>"),
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("timed out", await(out.done));
            // Batch survives per-item failures and still completes.
            assertTrue(out.completed);
            assertEquals(2, out.progress.size());
            assertEquals(2, out.progress.get(1)[0]);
            assertFalse(ct.hasTranslations());
            // P13: exactly one failure toast for the whole batch.
            assertEquals(1, ShadowToast.shownToastCount());
        });
    }

    @Test
    public void cancelBatch_notifiesCallbackAndKeepsCachedItems() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        AtomicReference<Translator> fakeRef = new AtomicReference<>(fake);
        withStubs(fakeRef, () -> {
            // Translate one item first so the cache is non-empty.
            BatchOutcome first = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>one</p>")),
                    first);
            assertTrue("timed out", await(first.done));
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));

            // Start a second batch with a gated translator and cancel it.
            GatedTranslator gated = new GatedTranslator();
            fakeRef.set(gated);
            BatchOutcome second = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>"),
                    new CommentTranslator.TranslatableItem(3, "<p>three</p>")),
                    second);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            assertTrue(ct.isBatchRunning());
            ct.cancelBatch();
            gated.release.countDown();
            assertTrue("timed out", await(second.done));
            assertTrue(second.cancelled);
            assertFalse(ct.isBatchRunning());
            // Already-translated items stay cached after cancellation.
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));
            assertNull(ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void clearTranslations_dropsCache() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>hi</p>")),
                    out);
            assertTrue("timed out", await(out.done));
            assertTrue(ct.hasTranslations());
            ct.clearTranslations();
            assertFalse(ct.hasTranslations());
            assertNull(ct.getTranslatedHtml("comment", 1));
        });
    }

    // ---------- single toggle ----------

    @Test
    public void toggleTranslation_translatesThenTogglesBack() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            AtomicInteger changed = new AtomicInteger();
            CountDownLatch first = new CountDownLatch(1);
            ct.toggleTranslation(context(), "comment", 9, "<p>Hello</p>", () -> {
                changed.incrementAndGet();
                first.countDown();
            });
            assertTrue("timed out", await(first));
            assertEquals(1, changed.get());
            assertEquals("<p>TR:Hello</p>", ct.getTranslatedHtml("comment", 9));
            // Second tap switches back to the original.
            ct.toggleTranslation(context(), "comment", 9, "<p>Hello</p>",
                    changed::incrementAndGet);
            assertEquals(2, changed.get());
            assertNull(ct.getTranslatedHtml("comment", 9));
            assertFalse(ct.hasTranslations());
        });
    }

    @Test
    public void toggleTranslation_inFlightRetapIsIgnored() throws Exception {
        GatedTranslator gated = new GatedTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(gated, () -> {
            AtomicInteger changed = new AtomicInteger();
            // Segment text must differ from the probe text ("Hello"),
            // otherwise the gate lets the segment straight through.
            ct.toggleTranslation(context(), "comment", 4, "<p>Hello world</p>",
                    changed::incrementAndGet);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            // Re-tap while translating: toast, no second translation, no callback.
            ct.toggleTranslation(context(), "comment", 4, "<p>Hello world</p>",
                    changed::incrementAndGet);
            assertEquals(context().getString(R.string.translating),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(0, changed.get());
            gated.release.countDown();
            long deadline = System.currentTimeMillis() + 10000;
            while (changed.get() == 0 && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(50);
            }
            assertEquals(1, changed.get());
            assertEquals("<p>TR:Hello world</p>", ct.getTranslatedHtml("comment", 4));
        });
    }

    @Test
    public void toggleTranslation_providerFailureShowsToastAndNoCallback() throws Exception {
        AlwaysFailTranslator fake = new AlwaysFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            AtomicInteger changed = new AtomicInteger();
            ct.toggleTranslation(context(), "comment", 5, "<p>Hello</p>",
                    changed::incrementAndGet);
            pumpQuietly(2000);
            assertEquals(0, changed.get());
            assertFalse(ct.hasTranslations());
            assertTrue(ShadowToast.shownToastCount() >= 1);
            assertTrue(ShadowToast.getTextOfLatestToast()
                    .contains(context().getString(R.string.translate_failed)));
        });
    }

    // ---------- destroy ----------

    @Test
    public void destroy_suppressesLaterBatches() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        ct.destroy();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>hi</p>")),
                    out);
            pumpQuietly(800);
            assertFalse(out.completed);
            assertEquals(1, out.done.getCount());
            AtomicInteger changed = new AtomicInteger();
            ct.toggleTranslation(context(), "comment", 1, "<p>hi</p>",
                    changed::incrementAndGet);
            pumpQuietly(500);
            assertEquals(0, changed.get());
        });
    }

    // ---------- newly covered residuals ----------

    @Test
    public void translateAll_destroyed_returnsWithoutPicker() throws Exception {
        AtomicInteger pickerCalls = new AtomicInteger();
        try (MockedStatic<TranslationUiHelper> ui =
                     Mockito.mockStatic(TranslationUiHelper.class);
             MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            ui.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        pickerCalls.incrementAndGet();
                        return null;
                    });
            CommentTranslator ct = new CommentTranslator();
            ct.destroy();
            BatchOutcome out = new BatchOutcome();
            ct.translateAll(context(), Collections.singletonList(
                    new CommentTranslator.TranslatableItem(1, "<p>hi</p>")), out);
            pumpQuietly(500);
            assertEquals("destroyed translator must not open the picker",
                    0, pickerCalls.get());
            assertEquals(1, out.done.getCount());
        }
    }

    @Test
    public void translateAllWithProvider_appendsToRunningBatch() throws Exception {
        GatedTranslator gated = new GatedTranslator();
        CommentTranslator ct = new CommentTranslator();
        AtomicReference<Translator> fakeRef = new AtomicReference<>(gated);
        withStubs(fakeRef, () -> {
            BatchOutcome first = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(1, "<p>one</p>"),
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>")), first);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            // Append a third item while the batch is running.
            BatchOutcome second = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(3, "<p>three</p>")),
                    second);
            assertEquals(1, second.progress.size());
            assertEquals(0, second.progress.get(0)[0]);
            assertEquals(3, second.progress.get(0)[1]);
            assertEquals("appended batch never completes on its own",
                    1, second.done.getCount());
            gated.release.countDown();
            assertTrue("timed out", await(first.done));
            assertTrue(first.completed);
            assertEquals(3, first.progress.size());
            assertEquals(3, first.progress.get(2)[0]);
            assertEquals(3, first.progress.get(2)[1]);
            assertEquals("<p>TR:three</p>", ct.getTranslatedHtml("comment", 3));
            assertEquals(1, second.done.getCount());
        });
    }

    @Test
    public void startBatch_destroyed_returnsWithoutStarting() throws Exception {
        // The guard is only reachable if destroy() lands between the
        // checks in translateAllWithProvider and the startBatch call
        // (same main-thread call stack, no yield point), so it is
        // invoked reflectively to prove it drops the batch.
        CommentTranslator ct = new CommentTranslator();
        ct.destroy();
        BatchOutcome out = new BatchOutcome();
        Method startBatch = CommentTranslator.class.getDeclaredMethod("startBatch",
                Context.class, String.class, List.class,
                CommentTranslator.BatchCallback.class);
        startBatch.setAccessible(true);
        startBatch.invoke(ct, context(), "google",
                Collections.singletonList(
                        new CommentTranslator.TranslatableItem(1, "<p>hi</p>")), out);
        assertFalse(ct.isBatchRunning());
        assertEquals(1, out.done.getCount());
    }

    @Test
    public void batchOnSuccess_afterCancel_returnsWithoutProgress() throws Exception {
        // Cancel after the completion callback was posted to the main
        // looper but before it is delivered: SingleCallback.onSuccess
        // sees mBatchCancelled and returns. The item is still cached
        // (caching happens in onComplete, before the guard).
        SignallingGatedTranslator gated = new SignallingGatedTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(gated, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            gated.release.countDown();
            assertTrue("segment never returned",
                    gated.returned.await(10, TimeUnit.SECONDS));
            // Let the background thread post the completion callback
            // to the (paused) main looper; it stays queued until pumped.
            Thread.sleep(500);
            ct.cancelBatch();
            pumpQuietly(1000);
            assertTrue(out.cancelled);
            assertFalse(out.completed);
            assertTrue(out.progress.isEmpty());
            assertFalse(ct.isBatchRunning());
            assertEquals("<p>TR:two</p>", ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void batchOnError_afterCancel_returnsWithoutProgress() throws Exception {
        SignallingFailTranslator fake = new SignallingFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("segment never failed",
                    fake.thrown.await(10, TimeUnit.SECONDS));
            // Let the background thread post the error callback to the
            // (paused) main looper; it stays queued until pumped.
            Thread.sleep(500);
            ct.cancelBatch();
            pumpQuietly(1000);
            assertTrue(out.cancelled);
            assertFalse(out.completed);
            assertTrue(out.progress.isEmpty());
            assertFalse(ct.isBatchRunning());
            assertTrue(ShadowToast.getTextOfLatestToast().contains("segment down"));
            assertNull(ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void startSingleTranslation_createTranslatorThrows_showsToast() throws Exception {
        CommentTranslator ct = new CommentTranslator();
        try (MockedStatic<TranslationUiHelper> ui =
                     Mockito.mockStatic(TranslationUiHelper.class);
             MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            ui.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("google");
                        return null;
                    });
            mgr.when(() -> TranslationManager.createTranslator(any(), eq("google")))
                    .thenThrow(new RuntimeException("no provider"));
            AtomicInteger changed = new AtomicInteger();
            ct.toggleTranslation(context(), "comment", 5, "<p>Hello</p>",
                    changed::incrementAndGet);
            assertEquals(0, changed.get());
            assertEquals(context().getString(R.string.translate_no_provider),
                    ShadowToast.getTextOfLatestToast());
            assertFalse(ct.hasTranslations());
        }
    }

    @Test
    public void startSingleTranslation_destroyedDuringPicker_returnsSilently()
            throws Exception {
        // Production race: the fragment is destroyed while the provider
        // picker dialog is open; the picker callback then fires after
        // destroy. createTranslator must never be built.
        PrefixTranslator fake = new PrefixTranslator();
        AtomicInteger createCalls = new AtomicInteger();
        CommentTranslator ct = new CommentTranslator();
        try (MockedStatic<TranslationUiHelper> ui =
                     Mockito.mockStatic(TranslationUiHelper.class);
             MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            ui.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        ct.destroy();
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("google");
                        return null;
                    });
            mgr.when(() -> TranslationManager.createTranslator(any(), eq("google")))
                    .thenAnswer(inv -> {
                        createCalls.incrementAndGet();
                        return fake;
                    });
            mgr.when(() -> TranslationManager.getTargetLanguage(any()))
                    .thenReturn("zh-CN");
            AtomicInteger changed = new AtomicInteger();
            ct.toggleTranslation(context(), "comment", 1, "<p>hi</p>",
                    changed::incrementAndGet);
            pumpQuietly(500);
            assertEquals(0, createCalls.get());
            assertEquals(0, changed.get());
            assertEquals(0, ShadowToast.shownToastCount());
        }
    }

    @Test
    public void readmeOnComplete_afterDestroy_dropsTranslation() throws Exception {
        // Destroy after the completion callback was posted but before
        // delivery: onComplete returns before caching the translation.
        SignallingGatedTranslator gated = new SignallingGatedTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(gated, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            gated.release.countDown();
            assertTrue("segment never returned",
                    gated.returned.await(10, TimeUnit.SECONDS));
            // Let the background thread post the completion callback
            // to the (paused) main looper; it stays queued until pumped.
            Thread.sleep(500);
            ct.destroy();
            pumpQuietly(1000);
            assertFalse(out.completed);
            assertEquals(1, out.done.getCount());
            assertNull(ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void readmeOnError_afterDestroy_dropsErrorSilently() throws Exception {
        SignallingFailTranslator fake = new SignallingFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("segment never failed",
                    fake.thrown.await(10, TimeUnit.SECONDS));
            // Let the background thread post the error callback to the
            // (paused) main looper; it stays queued until pumped.
            Thread.sleep(500);
            ct.destroy();
            pumpQuietly(1000);
            assertFalse(out.completed);
            assertEquals(1, out.done.getCount());
            assertEquals(0, ShadowToast.shownToastCount());
            assertNull(ct.getTranslatedHtml("comment", 2));
        });
    }

    @Test
    public void readmeOnError_nullMessage_toastHasNoSuffix() throws Exception {
        NoMessageFailTranslator fake = new NoMessageFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            BatchOutcome out = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    out);
            assertTrue("timed out", await(out.done));
            assertTrue(out.completed);
            assertEquals(1, out.progress.size());
            assertEquals(context().getString(R.string.translate_failed),
                    ShadowToast.getTextOfLatestToast());
        });
    }

    @Test
    public void cancelBatch_noBatchRunning_doesNothing() {
        CommentTranslator ct = new CommentTranslator();
        ct.cancelBatch();
        assertFalse(ct.isBatchRunning());
    }

    @Test
    public void clearTranslations_duringRunningBatch_cancelsSilently() throws Exception {
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        AtomicReference<Translator> fakeRef = new AtomicReference<>(fake);
        withStubs(fakeRef, () -> {
            BatchOutcome first = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>one</p>")),
                    first);
            assertTrue("timed out", await(first.done));
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));
            GatedTranslator gated = new GatedTranslator();
            fakeRef.set(gated);
            BatchOutcome second = new BatchOutcome();
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(2, "<p>two</p>")),
                    second);
            assertTrue("segment never started",
                    gated.entered.await(10, TimeUnit.SECONDS));
            ct.clearTranslations();
            assertFalse(ct.hasTranslations());
            assertNull(ct.getTranslatedHtml("comment", 1));
            assertFalse(ct.isBatchRunning());
            assertEquals("clear must not notify the batch callback",
                    1, second.done.getCount());
            gated.release.countDown();
            pumpQuietly(1000);
            assertEquals(1, second.done.getCount());
            assertFalse(second.completed);
            assertFalse(second.cancelled);
        });
    }
    @Test
    public void translateAllWithProvider_cancelFromProgressStopsBatch() throws Exception {
        // L183：第一条跑完、onProgress 里 cancelBatch() 后，第二条不再翻译；
        // 已翻译的第一条保留在缓存里，且收到 onCancelled、没有 onComplete。
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            CommentTranslator.BatchCallback cb = new CommentTranslator.BatchCallback() {
                @Override
                public void onProgress(int doneCount, int total) {
                    if (doneCount == 1) {
                        ct.cancelBatch();
                    }
                }

                @Override
                public void onComplete() {
                    completed.set(true);
                    done.countDown();
                }

                @Override
                public void onCancelled() {
                    cancelled.set(true);
                    done.countDown();
                }

                @Override
                public void onError(Exception e) {
                    done.countDown();
                }
            };
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(1, "<p>one</p>"),
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>")), cb);
            assertTrue("timed out", await(done));
            assertTrue(cancelled.get());
            assertFalse(completed.get());
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));
            assertNull(ct.getTranslatedHtml("comment", 2));
            assertFalse(fake.seen.contains("two"));
            assertFalse(ct.isBatchRunning());
        });
    }

    @Test
    public void translateAllWithProvider_destroyFromProgressStopsBatch() throws Exception {
        // L183：第一条跑完、onProgress 里 destroy() 后，第二条不再翻译；
        // destroy 不发 onCancelled，用 pump 等主线程跑完再断言状态。
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            CommentTranslator.BatchCallback cb = new CommentTranslator.BatchCallback() {
                @Override
                public void onProgress(int doneCount, int total) {
                    if (doneCount == 1) {
                        ct.destroy();
                    }
                }

                @Override
                public void onComplete() {
                }

                @Override
                public void onCancelled() {
                }

                @Override
                public void onError(Exception e) {
                }
            };
            ct.translateAllWithProvider(context(), "google", Arrays.asList(
                    new CommentTranslator.TranslatableItem(1, "<p>one</p>"),
                    new CommentTranslator.TranslatableItem(2, "<p>two</p>")), cb);
            pumpQuietly(2000);
            assertEquals("<p>TR:one</p>", ct.getTranslatedHtml("comment", 1));
            assertNull(ct.getTranslatedHtml("comment", 2));
            assertFalse(fake.seen.contains("two"));
            assertFalse(ct.isBatchRunning());
        });
    }

    @Test
    public void translateAllWithProvider_nullCallbackCompletesSilently() throws Exception {
        // L191/L206/L220：callback 为 null 时进度与完成都不回调，批量照常跑完。
        PrefixTranslator fake = new PrefixTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>Hello</p>")),
                    null);
            pumpQuietly(2000);
            assertEquals("<p>TR:Hello</p>", ct.getTranslatedHtml("comment", 1));
            assertFalse(ct.isBatchRunning());
        });
    }

    @Test
    public void translateAllWithProvider_nullCallbackFailingItemCompletesSilently()
            throws Exception {
        // L220：onError 里 callback 为 null 时不回调进度，批量照常跑完。第 1 轮
        // 的 null 回调测试只走了成功项的 onSuccess（L206），onError 的 null
        // 检查没跑到；这里用 SignallingFailTranslator 让唯一项失败。
        SignallingFailTranslator fake = new SignallingFailTranslator();
        CommentTranslator ct = new CommentTranslator();
        withStubs(fake, () -> {
            ct.translateAllWithProvider(context(), "google",
                    Collections.singletonList(
                            new CommentTranslator.TranslatableItem(1, "<p>one</p>")),
                    null);
            assertTrue(await(fake.thrown));
            pumpQuietly(2000);
            assertNull(ct.getTranslatedHtml("comment", 1));
            assertFalse(ct.isBatchRunning());
        });
    }

}
