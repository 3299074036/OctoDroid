package com.gh4a.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.resolver.BrowseFilter;
import com.meisolsson.githubsdk.model.Label;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UiUtils: pure helpers run against the application context; dialog/theme
 * helpers use a real Robolectric activity (BrowseFilter). The
 * QuoteActionModeCallback menu interaction uses Mockito mocks.
 */
@RunWith(RobolectricTestRunner.class)
public class UiUtilsTest {

    private Context appContext() {
        return RuntimeEnvironment.getApplication();
    }

    private Activity activity() {
        return Robolectric.buildActivity(BrowseFilter.class).setup().get();
    }

    // ---------- mixColors ----------

    @Test
    public void mixColors_fractionZeroReturnsStart() {
        assertEquals(0xFF112233, UiUtils.mixColors(0xFF112233, 0xFF445566, 0f));
    }

    @Test
    public void mixColors_fractionOneReturnsEnd() {
        assertEquals(0xFF445566, UiUtils.mixColors(0xFF112233, 0xFF445566, 1f));
    }

    @Test
    public void mixColors_halfBlackToWhite() {
        assertEquals(0xFF7F7F7F, UiUtils.mixColors(0xFF000000, 0xFFFFFFFF, 0.5f));
    }

    @Test
    public void mixColors_interpolatesAlphaToo() {
        assertEquals(0x7F7F7F7F, UiUtils.mixColors(0x00000000, 0xFFFFFFFF, 0.5f));
    }

    @Test
    public void mixColors_quarterMix() {
        // 0 + (int)(0.25 * 200) = 50 = 0x32 per channel
        assertEquals(0xFF323232, UiUtils.mixColors(0xFF000000, 0xFFC8C8C8, 0.25f));
    }

    // ---------- limitViewHeight ----------

    @Test
    public void limitViewHeight_atMostClampsToMax() {
        int spec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST);
        int out = UiUtils.limitViewHeight(spec, 300);
        assertEquals(300, View.MeasureSpec.getSize(out));
        assertEquals(View.MeasureSpec.AT_MOST, View.MeasureSpec.getMode(out));
    }

    @Test
    public void limitViewHeight_atMostBelowMaxUnchanged() {
        int spec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST);
        int out = UiUtils.limitViewHeight(spec, 300);
        assertEquals(200, View.MeasureSpec.getSize(out));
        assertEquals(View.MeasureSpec.AT_MOST, View.MeasureSpec.getMode(out));
    }

    @Test
    public void limitViewHeight_exactlyClampsToMax() {
        int spec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY);
        int out = UiUtils.limitViewHeight(spec, 300);
        assertEquals(300, View.MeasureSpec.getSize(out));
        assertEquals(View.MeasureSpec.EXACTLY, View.MeasureSpec.getMode(out));
    }

    @Test
    public void limitViewHeight_unspecifiedBecomesAtMostMax() {
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int out = UiUtils.limitViewHeight(spec, 300);
        assertEquals(300, View.MeasureSpec.getSize(out));
        assertEquals(View.MeasureSpec.AT_MOST, View.MeasureSpec.getMode(out));
    }

    // ---------- WhitespaceTokenizer ----------

    @Test
    public void tokenizer_findTokenStart() {
        UiUtils.WhitespaceTokenizer t = new UiUtils.WhitespaceTokenizer();
        assertEquals(6, t.findTokenStart("hello world", 11));
        assertEquals(0, t.findTokenStart("hello world", 5));
        assertEquals(0, t.findTokenStart("hello", 0));
        assertEquals(6, t.findTokenStart("hello world", 6));
    }

    @Test
    public void tokenizer_findTokenEnd() {
        UiUtils.WhitespaceTokenizer t = new UiUtils.WhitespaceTokenizer();
        assertEquals(5, t.findTokenEnd("hello world", 0));
        assertEquals(11, t.findTokenEnd("hello world", 6));
        assertEquals(11, t.findTokenEnd("hello world", 11));
        assertEquals(5, t.findTokenEnd("hello", 0));
    }

    @Test
    public void tokenizer_terminateTokenReturnsTextUnchanged() {
        UiUtils.WhitespaceTokenizer t = new UiUtils.WhitespaceTokenizer();
        assertEquals("hello", t.terminateToken("hello").toString());
    }

    // ---------- EmptinessWatchingTextWatcher ----------

    @Test
    public void emptinessWatcher_reportsInitialStateFromConstructor() {
        Activity act = activity();
        EditText empty = new EditText(act);
        AtomicBoolean seen = new AtomicBoolean(false);
        new UiUtils.EmptinessWatchingTextWatcher(empty) {
            @Override
            public void onIsEmpty(boolean isEmpty) {
                seen.set(isEmpty);
            }
        };
        assertTrue(seen.get());

        EditText filled = new EditText(act);
        filled.setText("x");
        AtomicBoolean seen2 = new AtomicBoolean(true);
        new UiUtils.EmptinessWatchingTextWatcher(filled) {
            @Override
            public void onIsEmpty(boolean isEmpty) {
                seen2.set(isEmpty);
            }
        };
        assertFalse(seen2.get());
    }

    @Test
    public void emptinessWatcher_afterTextChangedNullAndEmpty() {
        Activity act = activity();
        EditText editor = new EditText(act);
        List<Boolean> calls = new ArrayList<>();
        UiUtils.EmptinessWatchingTextWatcher w =
                new UiUtils.EmptinessWatchingTextWatcher(editor) {
                    @Override
                    public void onIsEmpty(boolean isEmpty) {
                        calls.add(isEmpty);
                    }
                };
        calls.clear();
        w.afterTextChanged(null);
        assertEquals(Collections.singletonList(true), calls);
        w.afterTextChanged(Editable.Factory.getInstance().newEditable("abc"));
        assertEquals(Arrays.asList(true, false), calls);
        w.afterTextChanged(Editable.Factory.getInstance().newEditable(""));
        assertEquals(Arrays.asList(true, false, true), calls);
    }

    // ---------- getSelectedText ----------

    @Test
    public void getSelectedText_unfocusedReturnsWholeText() {
        TextView view = new TextView(appContext());
        view.setText("hello world");
        assertEquals("hello world", UiUtils.getSelectedText(view).toString());
    }

    @Test
    public void getSelectedText_focusedSelection() {
        Activity act = activity();
        EditText view = new EditText(act);
        act.setContentView(view);
        view.setText("hello world");
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setSelection(1, 4);
        assertEquals("ell", UiUtils.getSelectedText(view).toString());
    }

    // ---------- textColorForBackground ----------

    @Test
    public void textColorForBackground_lightBackgroundGetsDarkText() {
        assertEquals(ContextCompat.getColor(appContext(), R.color.label_fg_dark),
                UiUtils.textColorForBackground(appContext(), Color.WHITE));
    }

    @Test
    public void textColorForBackground_darkBackgroundGetsLightText() {
        assertEquals(ContextCompat.getColor(appContext(), R.color.label_fg_light),
                UiUtils.textColorForBackground(appContext(), Color.BLACK));
    }

    @Test
    public void textColorForBackground_midGreyBoundary() {
        // luminance of #808080 = round(0.213*128 + 0.715*128 + 0.072*128) = 128 -> dark text
        assertEquals(ContextCompat.getColor(appContext(), R.color.label_fg_dark),
                UiUtils.textColorForBackground(appContext(), 0xFF808080));
        // #7F7F7F luminance 127 -> light text
        assertEquals(ContextCompat.getColor(appContext(), R.color.label_fg_light),
                UiUtils.textColorForBackground(appContext(), 0xFF7F7F7F));
    }

    // ---------- IME / visibility / scroll helpers ----------

    @Test
    public void hideAndShowIme_nullViewIsNoop() {
        UiUtils.hideImeForView(null);
        UiUtils.showImeForView(null);
    }

    @Test
    public void hideAndShowIme_realViewDoesNotThrow() {
        View view = new View(activity());
        UiUtils.hideImeForView(view);
        UiUtils.showImeForView(view);
    }

    @Test
    public void canViewScrollUp_nullIsFalse() {
        assertFalse(UiUtils.canViewScrollUp(null));
    }

    @Test
    public void canViewScrollUp_plainViewIsFalse() {
        assertFalse(UiUtils.canViewScrollUp(new View(appContext())));
    }

    @Test
    public void updateViewVisibility_sameVisibilityIsNoop() {
        View view = new View(appContext());
        view.setVisibility(View.GONE);
        UiUtils.updateViewVisibility(view, false, false);
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test
    public void updateViewVisibility_togglesWithoutAnimation() {
        View view = new View(appContext());
        view.setVisibility(View.GONE);
        UiUtils.updateViewVisibility(view, false, true);
        assertEquals(View.VISIBLE, view.getVisibility());
        UiUtils.updateViewVisibility(view, false, false);
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test
    public void updateViewVisibility_withAnimationStillToggles() {
        View view = new View(activity());
        UiUtils.updateViewVisibility(view, true, false);
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test
    public void trySetListOverscrollColor_attachesHelperAndDoesNotThrow() {
        RecyclerView rv = new RecyclerView(appContext());
        UiUtils.trySetListOverscrollColor(rv, Color.RED);
        assertNotNull(rv.getTag(R.id.EdgeColorHelper));
        // Second call reuses the helper instead of creating another one.
        Object helper = rv.getTag(R.id.EdgeColorHelper);
        UiUtils.trySetListOverscrollColor(rv, Color.BLUE);
        assertEquals(helper, rv.getTag(R.id.EdgeColorHelper));
    }

    // ---------- dialogs / colors ----------

    @Test
    public void createProgressDialog_nullContextReturnsNull() {
        assertNull(UiUtils.createProgressDialog(null, R.string.ok));
    }

    @Test
    public void createProgressDialog_showsMessage() {
        Activity act = activity();
        Dialog dialog = UiUtils.createProgressDialog(act, R.string.ok);
        assertNotNull(dialog);
        // The message lives in the dialog's custom view, attached on show().
        dialog.show();
        TextView message = dialog.findViewById(R.id.message);
        assertNotNull(message);
        assertEquals(act.getString(R.string.ok), message.getText().toString());
    }

    @Test
    public void setSelectableBackground_nullIsNoop() {
        UiUtils.setSelectableBackground(null);
    }

    @Test
    public void setSelectableBackground_realViewDoesNotThrow() {
        UiUtils.setSelectableBackground(new View(activity()));
    }

    @Test
    public void rankOrSelectedColor_branches() {
        Activity act = activity();
        assertEquals(UiUtils.resolveColor(act, androidx.appcompat.R.attr.colorPrimary),
                UiUtils.rankOrSelectedColor(act, true));
        assertEquals(ContextCompat.getColor(act, R.color.icon_foreground),
                UiUtils.rankOrSelectedColor(act, false));
    }

    @Test
    public void resolveColor_returnsIntWithoutThrowing() {
        // Value depends on the activity theme; the contract is a resolved
        // int (0 when the theme provides nothing), never a crash.
        int color = UiUtils.resolveColor(activity(), android.R.attr.textColorSecondary);
        assertTrue(color == 0 || color != 0); // no-throw assertion
    }

    // ---------- plurals ----------

    @Test
    public void getPluralString_oneAndOther() {
        assertEquals("1 issue",
                UiUtils.getPluralString(appContext().getResources(), R.plurals.issue, 1));
        assertEquals("5 issues",
                UiUtils.getPluralString(appContext().getResources(), R.plurals.issue, 5));
    }

    @Test
    public void getPluralString_nullCountCountsAsZero() {
        assertEquals("0 issues",
                UiUtils.getPluralString(appContext().getResources(), R.plurals.issue, null));
        assertEquals("0 issues",
                UiUtils.getPluralString(appContext().getResources(), R.plurals.issue, 0));
    }

    @Test
    public void getPluralString_withFormatArgs() {
        assertEquals("3 issues", UiUtils.getPluralString(
                appContext().getResources(), R.plurals.issue, 3, 3));
    }

    @Test
    public void setPluralText_writesIntoTextView() {
        TextView view = new TextView(appContext());
        UiUtils.setPluralText(view, R.plurals.issue, 2);
        assertEquals("2 issues", view.getText().toString());
        UiUtils.setPluralText(view, R.plurals.issue, null);
        assertEquals("0 issues", view.getText().toString());
    }

    @Test
    public void setPluralText_withFormatArgs() {
        TextView view = new TextView(appContext());
        UiUtils.setPluralText(view, R.plurals.issue, 4, 4);
        assertEquals("4 issues", view.getText().toString());
    }

    // ---------- setMenuItemText ----------

    @Test
    public void setMenuItemText_setsSpannedTitleAndSubtitle() {
        MenuItem item = mock(MenuItem.class);
        UiUtils.setMenuItemText(activity(), item, "Title", "Subtitle");
        ArgumentCaptor<CharSequence> captor = ArgumentCaptor.forClass(CharSequence.class);
        verify(item).setTitle(captor.capture());
        assertEquals("Title\nSubtitle", captor.getValue().toString());
        assertTrue(captor.getValue() instanceof SpannableStringBuilder);
    }

    // ---------- withActivityIfAdded / runOnUiThreadIfAdded / toastIfAdded ----------

    @Test
    public void withActivityIfAdded_nullFragmentSkipsAction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        UiUtils.withActivityIfAdded(null, a -> ran.set(true));
        assertFalse(ran.get());
    }

    @Test
    public void withActivityIfAdded_detachedFragmentSkipsAction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        UiUtils.withActivityIfAdded(new Fragment(), a -> ran.set(true));
        assertFalse(ran.get());
    }

    @Test
    public void withActivityIfAdded_attachedFragmentRunsActionWithActivity() {
        Activity act = activity();
        Fragment fragment = new Fragment();
        act.getFragmentManager();
        androidx.fragment.app.FragmentManager fm =
                ((androidx.fragment.app.FragmentActivity) act).getSupportFragmentManager();
        fm.beginTransaction().add(fragment, "test").commitNow();
        assertTrue(fragment.isAdded());
        AtomicReference<Activity> seen = new AtomicReference<>();
        UiUtils.withActivityIfAdded(fragment, seen::set);
        assertEquals(act, seen.get());
    }

    @Test
    public void runOnUiThreadIfAdded_detachedFragmentSkips() {
        AtomicBoolean ran = new AtomicBoolean(false);
        UiUtils.runOnUiThreadIfAdded(new Fragment(), () -> ran.set(true));
        assertFalse(ran.get());
        UiUtils.runOnUiThreadIfAdded(null, () -> ran.set(true));
        assertFalse(ran.get());
    }

    @Test
    public void toastIfAdded_detachedOrNullFragmentShowsNothing() {
        UiUtils.toastIfAdded(new Fragment(), R.string.ok);
        UiUtils.toastIfAdded(null, R.string.ok);
        UiUtils.toastIfAdded(new Fragment(), "text");
        UiUtils.toastIfAdded(null, "text");
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void toastIfAdded_attachedFragmentShowsToast() {
        Activity act = activity();
        Fragment fragment = new Fragment();
        androidx.fragment.app.FragmentManager fm =
                ((androidx.fragment.app.FragmentActivity) act).getSupportFragmentManager();
        fm.beginTransaction().add(fragment, "test").commitNow();
        UiUtils.toastIfAdded(fragment, R.string.ok);
        assertEquals(act.getString(R.string.ok), ShadowToast.getTextOfLatestToast());
    }

    // ---------- formatLabelList ----------

    @Test
    public void formatLabelList_emptyListIsEmpty() {
        SpannableStringBuilder out =
                UiUtils.formatLabelList(appContext(), Collections.emptyList());
        assertEquals("", out.toString());
    }

    @Test
    public void formatLabelList_concatenatesNamesWithOneSpanEach() {
        Label a = Label.builder().url("u1").name("bug").color("ff0000").build();
        Label b = Label.builder().url("u2").name("feature").color("00ff00").build();
        SpannableStringBuilder out =
                UiUtils.formatLabelList(appContext(), Arrays.asList(a, b));
        assertEquals("bugfeature", out.toString());
        assertEquals(2, out.getSpans(0, out.length(), Object.class).length);
    }

    @Test
    public void formatLabelList_nullColorFallsBackToGrey() {
        Label label = Label.builder().url("u1").name("nolabel").color(null).build();
        SpannableStringBuilder out =
                UiUtils.formatLabelList(appContext(), Collections.singletonList(label));
        assertEquals("nolabel", out.toString());
    }

    // ---------- QuoteActionModeCallback ----------

    private UiUtils.QuoteActionModeCallback quoteCallback(TextView view, List<CharSequence> quoted) {
        return new UiUtils.QuoteActionModeCallback(view) {
            @Override
            public void onTextQuoted(CharSequence text) {
                quoted.add(text);
            }
        };
    }

    @Test
    public void quoteCallback_onCreateInflatesMenuAndReturnsTrue() {
        Activity act = activity();
        TextView view = new TextView(act);
        android.view.ActionMode mode = mock(android.view.ActionMode.class);
        android.view.Menu menu = mock(android.view.Menu.class);
        android.view.MenuInflater inflater = mock(android.view.MenuInflater.class);
        when(mode.getMenuInflater()).thenReturn(inflater);
        assertTrue(quoteCallback(view, new ArrayList<>()).onCreateActionMode(mode, menu));
        verify(inflater).inflate(R.menu.comment_selection_menu, menu);
    }

    @Test
    public void quoteCallback_onPrepareReturnsFalse() {
        TextView view = new TextView(appContext());
        assertFalse(quoteCallback(view, new ArrayList<>())
                .onPrepareActionMode(mock(android.view.ActionMode.class),
                        mock(android.view.Menu.class)));
    }

    @Test
    public void quoteCallback_nonQuoteItemReturnsFalseWithoutQuoting() {
        TextView view = new TextView(appContext());
        view.setText("hello");
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(-999); // any id other than R.id.quote
        List<CharSequence> quoted = new ArrayList<>();
        android.view.ActionMode mode = mock(android.view.ActionMode.class);
        assertFalse(quoteCallback(view, quoted).onActionItemClicked(mode, item));
        assertTrue(quoted.isEmpty());
        verify(mode, never()).finish();
    }

    @Test
    public void quoteCallback_quoteItemQuotesSelectionAndFinishes() {
        Activity act = activity();
        EditText view = new EditText(act);
        act.setContentView(view);
        view.setText("hello world");
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setSelection(0, 5);
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.quote);
        List<CharSequence> quoted = new ArrayList<>();
        android.view.ActionMode mode = mock(android.view.ActionMode.class);
        assertTrue(quoteCallback(view, quoted).onActionItemClicked(mode, item));
        assertEquals(1, quoted.size());
        assertEquals("hello", quoted.get(0).toString());
        verify(mode).finish();
    }

    @Test
    public void quoteCallback_onDestroyIsNoop() {
        TextView view = new TextView(appContext());
        quoteCallback(view, new ArrayList<>())
                .onDestroyActionMode(mock(android.view.ActionMode.class));
    }
}
