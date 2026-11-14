package com.gh4a.utils.translate;

import android.content.Context;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ThreadTranslateController with TranslationUiHelper statically stubbed
 * (runWithDefaultProvider / pickProviderAndRun invoke the callback by
 * hand) and the CommentTranslator mocked, so no real translation or
 * network is involved. Only the button state machine is exercised.
 */
@RunWith(RobolectricTestRunner.class)
public class ThreadTranslateControllerTest {

    private static class FakeHost implements ThreadTranslateController.Host {
        Context context = RuntimeEnvironment.getApplication();
        CommentTranslator translator = mock(CommentTranslator.class);
        List<CommentTranslator.TranslatableItem> items = new ArrayList<>();
        int translationsChanged;
        int stateChanged;

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public CommentTranslator getTranslator() {
            return translator;
        }

        @Override
        public List<CommentTranslator.TranslatableItem> collectThreadItems() {
            return items;
        }

        @Override
        public void onTranslationsChanged() {
            translationsChanged++;
        }

        @Override
        public void onTranslateStateChanged() {
            stateChanged++;
        }
    }

    private static CommentTranslator.TranslatableItem item(long id, String html) {
        return new CommentTranslator.TranslatableItem(id, html);
    }

    private MockedStatic<TranslationUiHelper> stubDefaultProvider(String provider) {
        MockedStatic<TranslationUiHelper> mocked =
                Mockito.mockStatic(TranslationUiHelper.class);
        mocked.when(() -> TranslationUiHelper.runWithDefaultProvider(any(), any()))
                .thenAnswer(inv -> {
                    TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                    cb.onProviderSelected(provider);
                    return true;
                });
        return mocked;
    }

    @Test
    public void initialState_isIdleWithZeroProgress() {
        FakeHost host = new FakeHost();
        ThreadTranslateController c = new ThreadTranslateController(host);
        assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
        assertEquals(0, c.getDone());
        assertEquals(0, c.getTotal());
    }

    @Test
    public void getState_nullTranslatorIsIdle() {
        FakeHost host = new FakeHost();
        host.translator = null;
        ThreadTranslateController c = new ThreadTranslateController(host);
        assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
    }

    @Test
    public void toggle_nullContextDoesNothing() {
        FakeHost host = new FakeHost();
        host.context = null;
        ThreadTranslateController c = new ThreadTranslateController(host);
        c.toggle();
        verify(host.translator, never()).translateAllWithProvider(
                any(), any(), any(), any());
        assertEquals(0, host.stateChanged);
    }

    @Test
    public void toggle_nullTranslatorDoesNothing() {
        FakeHost host = new FakeHost();
        host.translator = null;
        ThreadTranslateController c = new ThreadTranslateController(host);
        c.toggle();
        assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
        assertEquals(0, host.stateChanged);
    }

    @Test
    public void toggle_emptyItemsShowsToastAndStaysIdle() {
        FakeHost host = new FakeHost();
        host.items = Collections.emptyList();
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            assertEquals(host.context.getString(R.string.translate_nothing_to_translate),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
            verify(host.translator, never()).translateAllWithProvider(
                    any(), any(), any(), any());
        }
    }

    @Test
    public void toggle_startsBatchWithDefaultProvider() {
        FakeHost host = new FakeHost();
        host.items = Arrays.asList(item(1, "<p>one</p>"), item(2, "<p>two</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            assertEquals(2, c.getTotal());
            assertEquals(0, c.getDone());
            assertEquals(1, host.stateChanged);
            verify(host.translator).translateAllWithProvider(
                    eq(host.context), eq("google"), eq(host.items), any());
        }
    }

    @Test
    public void toggle_batchProgressUpdatesDoneAndTotal() {
        FakeHost host = new FakeHost();
        host.items = Arrays.asList(item(1, "<p>one</p>"), item(2, "<p>two</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            ArgumentCaptor<CommentTranslator.BatchCallback> captor =
                    ArgumentCaptor.forClass(CommentTranslator.BatchCallback.class);
            c.toggle();
            verify(host.translator).translateAllWithProvider(
                    any(), any(), any(), captor.capture());
            captor.getValue().onProgress(1, 2);
            assertEquals(1, c.getDone());
            assertEquals(2, c.getTotal());
            assertEquals(1, host.translationsChanged);
            captor.getValue().onComplete();
            assertEquals(2, host.translationsChanged);
        }
    }

    @Test
    public void getState_translatingWhileBatchRunning() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            when(host.translator.isBatchRunning()).thenReturn(true);
            assertEquals(ThreadTranslateController.STATE_TRANSLATING, c.getState());
        }
    }

    @Test
    public void getState_doneWhenThreadModeAndTranslationsExist() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            when(host.translator.isBatchRunning()).thenReturn(false);
            when(host.translator.hasTranslations()).thenReturn(true);
            assertEquals(ThreadTranslateController.STATE_DONE, c.getState());
        }
    }

    @Test
    public void toggle_whileBatchRunningCancels() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            when(host.translator.isBatchRunning()).thenReturn(true);
            c.toggle();
            verify(host.translator).cancelBatch();
            assertEquals(host.context.getString(R.string.translate_cancelled),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
        }
    }

    @Test
    public void toggle_whenDoneClearsTranslations() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            when(host.translator.isBatchRunning()).thenReturn(false);
            when(host.translator.hasTranslations()).thenReturn(true);
            assertEquals(ThreadTranslateController.STATE_DONE, c.getState());
            c.toggle();
            verify(host.translator).clearTranslations();
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
            assertEquals(0, c.getDone());
            assertEquals(0, c.getTotal());
            assertTrue(host.translationsChanged > 0);
        }
    }

    @Test
    public void toggle_noProviderResetsStateAndToasts() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked =
                     Mockito.mockStatic(TranslationUiHelper.class)) {
            mocked.when(() -> TranslationUiHelper.runWithDefaultProvider(any(), any()))
                    .thenReturn(false);
            c.toggle();
            assertEquals(host.context.getString(R.string.translate_no_provider),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
            assertEquals(0, c.getDone());
            assertEquals(0, c.getTotal());
            verify(host.translator, never()).translateAllWithProvider(
                    any(), any(), any(), any());
        }
    }

    @Test
    public void batchCallback_onCancelledResetsThreadMode() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            ArgumentCaptor<CommentTranslator.BatchCallback> captor =
                    ArgumentCaptor.forClass(CommentTranslator.BatchCallback.class);
            c.toggle();
            verify(host.translator).translateAllWithProvider(
                    any(), any(), any(), captor.capture());
            captor.getValue().onCancelled();
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
            // A later onNewItems must not restart translation.
            c.onNewItems(Collections.singletonList(item(2, "<p>two</p>")));
            verify(host.translator).translateAllWithProvider(
                    any(), any(), any(), any());
        }
    }

    @Test
    public void batchCallback_onErrorResetsThreadMode() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            ArgumentCaptor<CommentTranslator.BatchCallback> captor =
                    ArgumentCaptor.forClass(CommentTranslator.BatchCallback.class);
            c.toggle();
            verify(host.translator).translateAllWithProvider(
                    any(), any(), any(), captor.capture());
            captor.getValue().onError(new RuntimeException("test"));
            assertEquals(ThreadTranslateController.STATE_IDLE, c.getState());
        }
    }

    @Test
    public void onNewItems_ignoredWhenThreadModeOff() {
        FakeHost host = new FakeHost();
        ThreadTranslateController c = new ThreadTranslateController(host);
        c.onNewItems(Collections.singletonList(item(1, "<p>one</p>")));
        verify(host.translator, never()).translateAllWithProvider(
                any(), any(), any(), any());
    }

    @Test
    public void onNewItems_emptyListIgnored() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            Mockito.clearInvocations(host.translator);
            c.onNewItems(Collections.emptyList());
            verify(host.translator, never()).translateAllWithProvider(
                    any(), any(), any(), any());
        }
    }

    @Test
    public void onNewItems_translatesWithInitialProvider() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked = stubDefaultProvider("google")) {
            c.toggle();
            List<CommentTranslator.TranslatableItem> fresh =
                    Collections.singletonList(item(2, "<p>two</p>"));
            c.onNewItems(fresh);
            verify(host.translator).translateAllWithProvider(
                    eq(host.context), eq("google"), eq(fresh), any());
        }
    }

    @Test
    public void onLongPress_nullContextOrTranslatorDoesNothing() {
        FakeHost host = new FakeHost();
        ThreadTranslateController c = new ThreadTranslateController(host);
        host.context = null;
        c.onLongPress();
        host.context = RuntimeEnvironment.getApplication();
        host.translator = null;
        c.onLongPress();
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onLongPress_switchesProviderWithoutRetranslatingWhenIdle() {
        FakeHost host = new FakeHost();
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked =
                     Mockito.mockStatic(TranslationUiHelper.class)) {
            mocked.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("deepl");
                        return null;
                    });
            c.onLongPress();
            assertTrue(ShadowToast.getTextOfLatestToast()
                    .contains("DeepL"));
            verify(host.translator, never()).translateAllWithProvider(
                    any(), any(), any(), any());
            verify(host.translator, never()).clearTranslations();
        }
    }

    @Test
    public void onLongPress_retranslatesThreadWithNewProvider() {
        FakeHost host = new FakeHost();
        host.items = Collections.singletonList(item(1, "<p>one</p>"));
        ThreadTranslateController c = new ThreadTranslateController(host);
        try (MockedStatic<TranslationUiHelper> mocked =
                     Mockito.mockStatic(TranslationUiHelper.class)) {
            mocked.when(() -> TranslationUiHelper.runWithDefaultProvider(any(), any()))
                    .thenAnswer(inv -> {
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("google");
                        return true;
                    });
            mocked.when(() -> TranslationUiHelper.pickProviderAndRun(any(), any()))
                    .thenAnswer(inv -> {
                        TranslationUiHelper.ProviderCallback cb = inv.getArgument(1);
                        cb.onProviderSelected("deepl");
                        return null;
                    });
            c.toggle();
            Mockito.clearInvocations(host.translator);
            c.onLongPress();
            verify(host.translator).clearTranslations();
            verify(host.translator).translateAllWithProvider(
                    eq(host.context), eq("deepl"), eq(host.items), any());
        }
    }
}
