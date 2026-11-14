package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.model.TimelineItem;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import android.view.MenuItem;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.widget.PopupMenu;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class DiffViewHolderTest {
    private Context context;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
    }

    private DiffViewHolder holder() {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.row_timeline_diff, null, false);
        return new DiffViewHolder(view, "owner", "repo", 5);
    }

    private TimelineItem.Diff diffItem(String diffChunk, Integer position, String path) {
        ReviewComment rc = AdapterTestSupport.reviewComment(11L,
                User.builder().login("octocat").build(), "<p>hi</p>", null, null, null, position,
                path, diffChunk,
                "https://api.github.com/repos/owner/repo/pulls/5", null, null);
        return new TimelineItem.Diff(new TimelineItem.TimelineComment(rc));
    }

    private TimelineItem.Diff diffItemWithFile(String diffChunk, Integer position, String path,
            String patch) {
        ReviewComment rc = AdapterTestSupport.reviewComment(11L,
                User.builder().login("octocat").build(), "<p>hi</p>", null, null, null, position,
                path, diffChunk,
                "https://api.github.com/repos/owner/repo/pulls/5", null, null);
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn(patch);
        return new TimelineItem.Diff(new TimelineItem.TimelineComment(rc, file));
    }

    private TextView fileView(DiffViewHolder h) {
        return h.itemView.findViewById(R.id.tv_file);
    }

    private TextView hunkView(DiffViewHolder h) {
        return h.itemView.findViewById(R.id.diff_hunk);
    }

    @Test
    public void bind_nullDiffChunk_hidesHunk() {
        DiffViewHolder h = holder();
        h.bind(diffItem(null, 3, "a.java"));
        assertEquals(View.GONE, hunkView(h).getVisibility());
        assertEquals("a.java", fileView(h).getText().toString());
    }

    @Test
    public void bind_emptyDiffChunk_hidesHunk() {
        DiffViewHolder h = holder();
        h.bind(diffItem("", 3, "a.java"));
        assertEquals(View.GONE, hunkView(h).getVisibility());
    }

    @Test
    public void bind_withHunk_showsFormattedDiff() {
        DiffViewHolder h = holder();
        String chunk = "@@ -10,2 +20,5 @@ func()\n-old line\n+new line\n context";
        h.bind(diffItem(chunk, 3, "a.java"));
        assertEquals(View.VISIBLE, hunkView(h).getVisibility());
        String text = hunkView(h).getText().toString();
        assertTrue(text.contains("old line"));
        assertTrue(text.contains("new line"));
        assertTrue(text.contains("context"));
    }

    @Test
    public void bind_hunkWithoutHeaderLineNumbers_stillRenders() {
        DiffViewHolder h = holder();
        h.bind(diffItem("not a hunk\n+added", 3, "a.java"));
        assertEquals(View.VISIBLE, hunkView(h).getVisibility());
        assertTrue(hunkView(h).getText().toString().contains("added"));
    }

    @Test
    public void bind_hunkWithOnlyHeader_noLines() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -1,1 +1,1 @@", 3, "a.java"));
        assertEquals(View.VISIBLE, hunkView(h).getVisibility());
    }

    @Test
    public void bind_outdatedPosition_strikesThroughFileName() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -1,1 +1,1 @@\n+x", null, "a.java"));
        assertTrue((fileView(h).getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0);
        assertFalse(fileView(h).isClickable());
    }

    @Test
    public void bind_currentPosition_noStrikethrough() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        assertEquals(0, fileView(h).getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG);
        // clickable only when a file patch exists; none here
        assertFalse(fileView(h).isClickable());
    }

    @Test
    public void bind_withFilePatch_clickableFile() {
        DiffViewHolder h = holder();
        h.bind(diffItemWithFile("@@ -1,1 +1,1 @@\n+x", 3, "a.java", "@@ -1 +1 @@"));
        assertTrue(fileView(h).isClickable());
    }

    @Test
    public void onFileNameClick_withoutFile_noCrashNoActivity() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        fileView(h).performClick();
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onFileNameClick_withFile_startsDiffViewer() {
        DiffViewHolder h = holder();
        h.bind(diffItemWithFile("@@ -1,1 +1,1 @@\n+x", 3, "a.java", "@@ -1 +1 @@"));
        fileView(h).performClick();
        assertTrue(AdapterTestSupport.shadowApp(context).getNextStartedActivity() != null);
    }

    @Test
    public void bind_diffSizeMultiplier_prefsDriveTextSize() {
        // default pref (2) -> 1.0x
        DiffViewHolder h = holder();
        float initial = hunkView(h).getTextSize();
        h.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        assertEquals(initial, hunkView(h).getTextSize(), 0.01f);

        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 0).apply();
        DiffViewHolder h0 = holder();
        float initial0 = hunkView(h0).getTextSize();
        h0.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        assertEquals(initial0 * 0.8f, hunkView(h0).getTextSize(), 0.01f);

        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 4).apply();
        DiffViewHolder h4 = holder();
        float initial4 = hunkView(h4).getTextSize();
        h4.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        assertEquals(initial4 * 1.5f, hunkView(h4).getTextSize(), 0.01f);

        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 99).apply();
        DiffViewHolder h99 = holder();
        float initial99 = hunkView(h99).getTextSize();
        h99.bind(diffItem("@@ -1,1 +1,1 @@\n+x", 3, "a.java"));
        assertEquals(initial99, hunkView(h99).getTextSize(), 0.01f);

        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove(SettingsFragment.KEY_TEXT_SIZE).apply();
    }

    @Test
    public void diffLineSpan_drawBackground_paintsBothRects() throws Exception {
        DiffViewHolder h = holder();
        String chunk = "@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx";
        h.bind(diffItem(chunk, 3, "a.java"));
        // TextView stores the text as SpannedString, not the builder that was set
        android.text.Spanned text = (android.text.Spanned) hunkView(h).getText();
        Object span = null;
        for (Object s : text.getSpans(0, text.length(), Object.class)) {
            if (s.getClass().getName().endsWith("DiffLineSpan")) {
                span = s;
                break;
            }
        }
        assertTrue(span != null);
        android.graphics.Canvas canvas = mock(android.graphics.Canvas.class);
        Paint paint = new Paint();
        paint.setColor(0xFF000000);
        span.getClass().getMethod("drawBackground", android.graphics.Canvas.class,
                Paint.class, int.class, int.class, int.class, int.class, int.class,
                CharSequence.class, int.class, int.class, int.class)
                .invoke(span, canvas, paint, 0, 100, 0, 10, 20, text, 0, 10, 0);
        // paint color restored after drawing
        assertEquals(0xFF000000, paint.getColor());
        org.mockito.Mockito.verify(canvas, org.mockito.Mockito.atLeast(2))
                .drawRect(org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.any(Paint.class));
    }

    private void clickFirstLineNumberSpan(DiffViewHolder h) {
        android.text.Spanned text = (android.text.Spanned) hunkView(h).getText();
        android.text.style.ClickableSpan[] spans = text.getSpans(0, text.length(),
                android.text.style.ClickableSpan.class);
        assertTrue(spans.length >= 1);
        spans[0].onClick(hunkView(h));
    }

    // showPopupMenu() builds an androidx PopupMenu, which Robolectric's
    // ShadowPopupMenu (android.widget.PopupMenu only) cannot see. Capture
    // the constructed popup with mockConstruction and invoke its real
    // OnMenuItemClickListener lambda with mock MenuItems.
    private android.view.Menu menuWithViewInFileItem(android.view.MenuItem viewInFileItem) {
        android.view.Menu menu = mock(android.view.Menu.class);
        when(menu.findItem(R.id.view_in_file)).thenReturn(viewInFileItem);
        return menu;
    }

    private MockedConstruction.MockInitializer<PopupMenu> menuInitializer(
            android.view.Menu menu) {
        SupportMenuInflater inflater = mock(SupportMenuInflater.class);
        return (mock, ctx) -> {
            when(mock.getMenu()).thenReturn(menu);
            when(mock.getMenuInflater()).thenReturn(inflater);
        };
    }

    private PopupMenu.OnMenuItemClickListener captureListener(
            MockedConstruction<PopupMenu> mocked) {
        assertEquals(1, mocked.constructed().size());
        ArgumentCaptor<PopupMenu.OnMenuItemClickListener> captor =
                ArgumentCaptor.forClass(PopupMenu.OnMenuItemClickListener.class);
        verify(mocked.constructed().get(0)).setOnMenuItemClickListener(captor.capture());
        return captor.getValue();
    }

    @Test
    public void bind_longHunk_skipsLinesBeforeStart() {
        DiffViewHolder h = holder();
        String chunk = "@@ -10,7 +20,7 @@ func()\n l1\n l2\n l3\n l4\n l5\n l6\n l7";
        h.bind(diffItem(chunk, 3, "a.java"));
        String text = hunkView(h).getText().toString();
        // start = max(1, 8-4) = 4: lines 1-3 are skipped
        assertFalse(text.contains("l1"));
        assertFalse(text.contains("l3"));
        assertTrue(text.contains("l4"));
        assertTrue(text.contains("l7"));
    }

    @Test
    public void bind_textSizeOutOfRange_usesDefaultMultiplier() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        float initial = hunkView(holder()).getTextSize();
        prefs.edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 99).apply();
        try {
            DiffViewHolder h = holder();
            h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java"));
            assertEquals(initial, hunkView(h).getTextSize(), 0.001f);
        } finally {
            prefs.edit().remove(SettingsFragment.KEY_TEXT_SIZE).apply();
        }
    }

    @Test
    public void lineNumberSpan_click_showsPopupMenu() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).show();
        }
    }

    @Test
    public void lineNumberSpan_updateDrawState_setsSecondaryColor() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java"));
        android.text.Spanned text = (android.text.Spanned) hunkView(h).getText();
        android.text.style.ClickableSpan[] spans = text.getSpans(0, text.length(),
                android.text.style.ClickableSpan.class);
        assertTrue(spans.length >= 1);
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setColor(0xFF123456);
        spans[0].updateDrawState(paint);
        assertTrue(paint.getColor() != 0xFF123456);
    }

    @Test
    public void popupMenu_share_startsChooser() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            PopupMenu.OnMenuItemClickListener listener = captureListener(mocked);
            MenuItem share = mock(MenuItem.class);
            when(share.getItemId()).thenReturn(R.id.share);
            assertTrue(listener.onMenuItemClick(share));
        }
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(android.content.Intent.ACTION_CHOOSER, started.getAction());
    }

    @Test
    public void popupMenu_viewInFile_startsDiffViewer() {
        DiffViewHolder h = holder();
        h.bind(diffItemWithFile("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java",
                "@@ -1,2 +1,2 @@\n-old\n+new"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            PopupMenu.OnMenuItemClickListener listener = captureListener(mocked);
            MenuItem viewInFile = mock(MenuItem.class);
            when(viewInFile.getItemId()).thenReturn(R.id.view_in_file);
            assertTrue(listener.onMenuItemClick(viewInFile));
        }
        android.content.Intent started =
                AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.PullRequestDiffViewerActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void popupMenu_unknownItem_returnsFalse() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", 3, "a.java"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            PopupMenu.OnMenuItemClickListener listener = captureListener(mocked);
            MenuItem unknown = mock(MenuItem.class);
            when(unknown.getItemId()).thenReturn(R.id.browser);
            assertFalse(listener.onMenuItemClick(unknown));
        }
    }

    @Test
    public void popupMenu_viewInFileHidden_whenPositionNull() {
        DiffViewHolder h = holder();
        h.bind(diffItemWithFile("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", null, "a.java",
                "@@ -1,2 +1,2 @@\n-old\n+new"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            verify(viewInFileItem).setVisible(false);
        }
    }

    @Test
    public void popupMenu_viewInFileHidden_whenNoFilePatch() {
        DiffViewHolder h = holder();
        h.bind(diffItem("@@ -10,2 +20,5 @@ func()\n-old\n+new\n ctx", null, "a.java"));
        android.view.MenuItem viewInFileItem = mock(android.view.MenuItem.class);
        try (MockedConstruction<PopupMenu> mocked = mockConstruction(PopupMenu.class,
                menuInitializer(menuWithViewInFileItem(viewInFileItem)))) {
            clickFirstLineNumberSpan(h);
            verify(viewInFileItem).setVisible(false);
        }
    }

    @Test
    public void diffLineSpan_drawBackground_allLines() throws Exception {
        DiffViewHolder h = holder();
        String chunk = "@@ -10,3 +20,3 @@ func()\n-old\n+new\n ctx\n+more";
        h.bind(diffItem(chunk, 3, "a.java"));
        android.text.Spanned text = (android.text.Spanned) hunkView(h).getText();
        java.util.List<Object> spans = new java.util.ArrayList<>();
        for (Object s : text.getSpans(0, text.length(), Object.class)) {
            if (s.getClass().getName().endsWith("DiffLineSpan")) {
                spans.add(s);
            }
        }
        // first (isFirstLine), middle (neither), last (isLastLine)
        assertTrue(spans.size() >= 3);
        for (Object span : spans) {
            android.graphics.Canvas canvas = mock(android.graphics.Canvas.class);
            Paint paint = new Paint();
            paint.setColor(0xFF000000);
            span.getClass().getMethod("drawBackground", android.graphics.Canvas.class,
                    Paint.class, int.class, int.class, int.class, int.class, int.class,
                    CharSequence.class, int.class, int.class, int.class)
                    .invoke(span, canvas, paint, 0, 100, 0, 10, 20, text, 0, 10, 0);
            assertEquals(0xFF000000, paint.getColor());
        }
    }
}
