package com.gh4a.widget;

import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class FindActionModeCallbackTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private FindActionModeCallback callback(FragmentActivity activity) {
        return new FindActionModeCallback(activity);
    }

    private EditText editText(FindActionModeCallback cb) throws Exception {
        Field f = FindActionModeCallback.class.getDeclaredField("mEditText");
        f.setAccessible(true);
        return (EditText) f.get(cb);
    }

    private TextView matchesView(FindActionModeCallback cb) throws Exception {
        Field f = FindActionModeCallback.class.getDeclaredField("mMatches");
        f.setAccessible(true);
        return (TextView) f.get(cb);
    }

    private androidx.appcompat.view.ActionMode mockMode(FragmentActivity activity) {
        androidx.appcompat.view.ActionMode mode = mock(androidx.appcompat.view.ActionMode.class);
        when(mode.getMenuInflater()).thenReturn(new SupportMenuInflater(activity));
        return mode;
    }

    private Menu menu(FragmentActivity activity) {
        return new MenuBuilder(activity);
    }

    private FindActionModeCallback callbackWithMode(FragmentActivity activity) {
        FindActionModeCallback cb = callback(activity);
        cb.onCreateActionMode(mockMode(activity), menu(activity));
        return cb;
    }

    @Test
    public void constructor_initialTextEmpty() throws Exception {
        FindActionModeCallback cb = callback(activity());
        assertEquals("", editText(cb).getText().toString());
        assertEquals(0, editText(cb).getSelectionStart());
    }

    @Test
    public void setText_setsTextAndMovesSelectionToEnd() throws Exception {
        FindActionModeCallback cb = callback(activity());
        cb.setText("hello");
        assertEquals("hello", editText(cb).getText().toString());
        assertEquals(5, editText(cb).getSelectionStart());
        assertEquals(5, editText(cb).getSelectionEnd());
    }

    @Test
    public void setText_empty_clearsText() throws Exception {
        FindActionModeCallback cb = callback(activity());
        cb.setText("hello");
        cb.setText("");
        assertEquals("", editText(cb).getText().toString());
    }

    @Test(expected = AssertionError.class)
    public void setWebView_null_throwsAssertionError() {
        callback(activity()).setWebView(null);
    }

    @Test
    public void setWebView_valid_doesNotThrow() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        cb.setWebView(new WebView(activity));
    }

    @Test
    public void setWebView_twice_replacesListenerWithoutCrash() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        cb.setWebView(new WebView(activity));
        cb.setWebView(new WebView(activity));
    }

    @Test(expected = AssertionError.class)
    public void findAll_noWebView_throwsAssertionError() {
        callback(activity()).findAll();
    }

    @Test
    public void findAll_emptyText_clearsMatchesAndHidesCounter() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("");

        cb.findAll();

        assertEquals(View.GONE, matchesView(cb).getVisibility());
    }

    @Test
    public void findAll_withText_marksSearchStartedAndHidesCounter() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("foo");

        cb.findAll();

        assertEquals(View.INVISIBLE, matchesView(cb).getVisibility());
    }

    @Test(expected = AssertionError.class)
    public void findNext_noWebView_throwsAssertionError() throws Exception {
        // findNext is private; reach it through onActionItemClicked(find_next)
        // with a webview-less callback.
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        MenuItem next = menu(activity).findItem(R.id.find_next);
        cb.onActionItemClicked(mockMode(activity), next);
    }

    @Test
    public void onFindResultReceived_notDone_doesNotUpdateCounter() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));

        cb.onFindResultReceived(1, 5, false);

        assertEquals(View.GONE, matchesView(cb).getVisibility());
    }

    @Test
    public void onFindResultReceived_done_showsActiveOverTotal() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));

        cb.onFindResultReceived(2, 5, true);

        assertEquals("3/5", matchesView(cb).getText().toString());
        assertEquals(View.VISIBLE, matchesView(cb).getVisibility());
        assertFalse(matchesView(cb).isSelected());
    }

    @Test
    public void onFindResultReceived_doneZeroMatches_showsZeroAndSelects() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));

        cb.onFindResultReceived(0, 0, true);

        assertEquals("0/0", matchesView(cb).getText().toString());
        assertTrue(matchesView(cb).isSelected());
    }

    @Test
    public void onFindResultReceived_done_enablesPrevNextItems() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        Menu menu = menu(activity);
        cb.onCreateActionMode(mockMode(activity), menu);

        cb.onFindResultReceived(0, 3, true);

        assertTrue(menu.findItem(R.id.find_prev).isEnabled());
        assertTrue(menu.findItem(R.id.find_next).isEnabled());
    }

    @Test
    public void onFindResultReceived_doneZeroMatches_disablesPrevNextItems() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        Menu menu = menu(activity);
        cb.onCreateActionMode(mockMode(activity), menu);

        cb.onFindResultReceived(0, 0, true);

        assertFalse(menu.findItem(R.id.find_prev).isEnabled());
        assertFalse(menu.findItem(R.id.find_next).isEnabled());
    }

    @Test
    public void onCreateActionMode_returnsTrueAndHidesCounter() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        Menu menu = menu(activity);

        boolean result = cb.onCreateActionMode(mockMode(activity), menu);

        assertTrue(result);
        assertEquals(View.GONE, matchesView(cb).getVisibility());
        assertNotNull(menu.findItem(R.id.find_prev));
        assertNotNull(menu.findItem(R.id.find_next));
    }

    @Test
    public void onPrepareActionMode_returnsFalse() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        assertFalse(cb.onPrepareActionMode(mockMode(activity), menu(activity)));
    }

    @Test
    public void onActionItemClicked_findNext_startsSearchThenMovesNext() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("foo");
        Menu menu = menu(activity);
        cb.onCreateActionMode(mockMode(activity), menu);

        assertTrue(cb.onActionItemClicked(mockMode(activity), menu.findItem(R.id.find_next)));
        // Second click: search already started, matches unknown (0 in
        // Robolectric) so no-op, still handled.
        assertTrue(cb.onActionItemClicked(mockMode(activity), menu.findItem(R.id.find_next)));
    }

    @Test
    public void onActionItemClicked_findPrev_movesToPrevious() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("foo");
        Menu menu = menu(activity);
        cb.onCreateActionMode(mockMode(activity), menu);

        assertTrue(cb.onActionItemClicked(mockMode(activity), menu.findItem(R.id.find_prev)));
    }

    @Test
    public void onActionItemClicked_withMatches_movesToNextMatch() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("foo");
        cb.findAll();
        cb.onFindResultReceived(0, 4, true);
        // NOTE: no second onCreateActionMode here: it would reset
        // mHasStartedSearch and the findNext branch (mWebView.findNext)
        // would not be reached.
        Menu menu = menu(activity);
        new SupportMenuInflater(activity).inflate(R.menu.webview_find, menu);

        assertTrue(cb.onActionItemClicked(mockMode(activity), menu.findItem(R.id.find_next)));
        assertTrue(cb.onActionItemClicked(mockMode(activity), menu.findItem(R.id.find_prev)));
    }

    @Test
    public void onActionItemClicked_unknownItem_returnsFalse() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        MenuItem unknown = mock(MenuItem.class);
        when(unknown.getItemId()).thenReturn(999999);

        assertFalse(cb.onActionItemClicked(mockMode(activity), unknown));
    }

    @Test
    public void onDestroyActionMode_withWebView_doesNotThrow() {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        cb.setWebView(new WebView(activity));
        cb.onDestroyActionMode(mockMode(activity));
    }

    @Test(expected = NullPointerException.class)
    public void onDestroyActionMode_withoutWebView_throwsNpe() {
        // Observed behavior (not covered by production callers, which always
        // set a WebView first): mWebView.clearMatches() is unconditional.
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        cb.onDestroyActionMode(mockMode(activity));
    }

    @Test
    public void afterTextChanged_withWebView_triggersFindAll() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callbackWithMode(activity);
        cb.setWebView(new WebView(activity));
        cb.setText("foo");

        cb.afterTextChanged(mock(Editable.class));

        assertEquals(View.INVISIBLE, matchesView(cb).getVisibility());
    }

    @Test(expected = AssertionError.class)
    public void afterTextChanged_withoutWebView_throwsAssertionError() {
        FindActionModeCallback cb = callback(activity());
        cb.afterTextChanged(mock(Editable.class));
    }

    @Test
    public void beforeAndOnTextChanged_doNothing() {
        FindActionModeCallback cb = callback(activity());
        cb.beforeTextChanged("x", 0, 1, 1);
        cb.onTextChanged("x", 0, 0, 1);
    }

    @Test
    public void showSoftInput_detachedEditText_doesNotThrow() {
        FindActionModeCallback cb = callback(activity());
        cb.showSoftInput();
    }

    @Test
    public void findNext_noWebView_directCall_throwsAssertionError() throws Exception {
        // The public path (onActionItemClicked) throws at its own null check
        // first, so findNext's own guard is only reachable directly.
        FindActionModeCallback cb = callback(activity());
        Method findNext = FindActionModeCallback.class.getDeclaredMethod("findNext", boolean.class);
        findNext.setAccessible(true);
        try {
            findNext.invoke(cb, true);
            fail("expected AssertionError");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof AssertionError);
        }
    }

    @Test
    public void showSoftInput_attachedEditText_showsImeWithoutThrow() throws Exception {
        FragmentActivity activity = activity();
        FindActionModeCallback cb = callback(activity);
        // requestFocus() only succeeds on an attached view: attach the
        // callback's custom view (inflated with no parent) to the activity.
        Field customViewField = FindActionModeCallback.class.getDeclaredField("mCustomView");
        customViewField.setAccessible(true);
        activity.setContentView((View) customViewField.get(cb));
        EditText editText = editText(cb);
        assertTrue(editText.requestFocus());
        cb.showSoftInput();
    }
}
