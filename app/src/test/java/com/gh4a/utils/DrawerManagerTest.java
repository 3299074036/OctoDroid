package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 组：DrawerManager 全是 SharedPreferences 读写，走 Robolectric。
 * 每个测试先 resetToDefault，保证 prefs 干净。
 */
@RunWith(RobolectricTestRunner.class)
public class DrawerManagerTest {

    private Context context;

    private static final List<String> DEFAULT_KEYS = Arrays.asList(
            "search", "feed_hub", "notifications", "discover_hub",
            "my_repos", "star_hub", "my_items", "topic_discovery");

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DrawerManager.resetToDefault(context);
    }

    private SharedPreferences rawPrefs() {
        return context.getSharedPreferences("drawer_config", Context.MODE_PRIVATE);
    }

    @Test
    public void getDefaultItems_returnsEightInDefaultOrder() {
        List<DrawerManager.DrawerItemDef> items = DrawerManager.getDefaultItems();
        assertEquals(8, items.size());
        for (int i = 0; i < DEFAULT_KEYS.size(); i++) {
            assertEquals(DEFAULT_KEYS.get(i), items.get(i).key);
        }
    }

    @Test
    public void getDefaultItems_returnsCopy() {
        List<DrawerManager.DrawerItemDef> first = DrawerManager.getDefaultItems();
        first.clear();
        assertEquals(8, DrawerManager.getDefaultItems().size());
    }

    @Test
    public void getOrderedKeys_defaultIsDefaultOrder() {
        assertEquals(DEFAULT_KEYS, DrawerManager.getOrderedKeys(context));
    }

    @Test
    public void saveOrder_roundTripPreservesOrderAndAppendsMissing() {
        DrawerManager.saveOrder(context, Arrays.asList("star_hub", "search"));
        assertEquals(Arrays.asList("star_hub", "search", "feed_hub", "notifications",
                "discover_hub", "my_repos", "my_items", "topic_discovery"),
                DrawerManager.getOrderedKeys(context));
    }

    @Test
    public void getOrderedKeys_unknownKeysDropped() {
        DrawerManager.saveOrder(context,
                Arrays.asList("search", "no_such_key", "star_hub"));
        assertEquals(Arrays.asList("search", "star_hub", "feed_hub", "notifications",
                "discover_hub", "my_repos", "my_items", "topic_discovery"),
                DrawerManager.getOrderedKeys(context));
    }

    @Test
    public void getOrderedKeys_duplicateKeysDedupedKeepingFirstPosition() {
        DrawerManager.saveOrder(context,
                Arrays.asList("star_hub", "search", "star_hub"));
        List<String> keys = DrawerManager.getOrderedKeys(context);
        assertEquals("star_hub", keys.get(0));
        assertEquals("search", keys.get(1));
        assertEquals(8, keys.size());
    }

    @Test
    public void getOrderedKeys_mergedOldKeyMapsToNewKey() {
        DrawerManager.saveOrder(context, Arrays.asList("news_feed", "search"));
        List<String> keys = DrawerManager.getOrderedKeys(context);
        assertEquals("feed_hub", keys.get(0));
        assertEquals("search", keys.get(1));
        assertFalse(keys.contains("news_feed"));
    }

    @Test
    public void getOrderedKeys_twoOldKeysMergingToSameNewKeyCollapseToOne() {
        DrawerManager.saveOrder(context, Arrays.asList("my_issues", "my_prs", "search"));
        List<String> keys = DrawerManager.getOrderedKeys(context);
        assertEquals("my_items", keys.get(0));
        assertEquals(8, keys.size());
        assertFalse(keys.contains("my_issues"));
        assertFalse(keys.contains("my_prs"));
    }

    @Test
    public void getOrderedKeys_corruptJsonFallsBackToDefault() {
        rawPrefs().edit().putString("order", "not-json{{{").apply();
        assertEquals(DEFAULT_KEYS, DrawerManager.getOrderedKeys(context));
    }

    @Test
    public void getHiddenKeys_defaultEmpty() {
        assertTrue(DrawerManager.getHiddenKeys(context).isEmpty());
    }

    @Test
    public void setVisible_hideAndShowRoundTrip() {
        assertTrue(DrawerManager.isVisible(context, "star_hub"));
        DrawerManager.setVisible(context, "star_hub", false);
        assertFalse(DrawerManager.isVisible(context, "star_hub"));
        assertTrue(DrawerManager.getHiddenKeys(context).contains("star_hub"));
        DrawerManager.setVisible(context, "star_hub", true);
        assertTrue(DrawerManager.isVisible(context, "star_hub"));
        assertFalse(DrawerManager.getHiddenKeys(context).contains("star_hub"));
    }

    @Test
    public void getHiddenKeys_allOldKeysOfGroupHidden_migratesToNewKey() {
        rawPrefs().edit()
                .putString("hidden", "[\"news_feed\",\"pub_timeline\"]")
                .apply();
        assertEquals(new HashSet<>(Arrays.asList("feed_hub")),
                DrawerManager.getHiddenKeys(context));
    }

    @Test
    public void getHiddenKeys_onlyOneOldKeyOfGroupHidden_doesNotHideNewKey() {
        rawPrefs().edit().putString("hidden", "[\"news_feed\"]").apply();
        assertTrue(DrawerManager.getHiddenKeys(context).isEmpty());
        assertTrue(DrawerManager.isVisible(context, "feed_hub"));
    }

    @Test
    public void getHiddenKeys_corruptJsonReturnsEmpty() {
        rawPrefs().edit().putString("hidden", "xxx").apply();
        assertTrue(DrawerManager.getHiddenKeys(context).isEmpty());
    }

    @Test
    public void getVisibleOrderedItems_hidesHiddenKeepsOrder() {
        DrawerManager.setVisible(context, "star_hub", false);
        DrawerManager.setVisible(context, "search", false);
        List<DrawerManager.DrawerItemDef> items =
                DrawerManager.getVisibleOrderedItems(context);
        assertEquals(6, items.size());
        assertEquals("feed_hub", items.get(0).key);
        for (DrawerManager.DrawerItemDef item : items) {
            assertFalse(item.key.equals("star_hub"));
            assertFalse(item.key.equals("search"));
        }
    }

    @Test
    public void resetToDefault_restoresOrderAndVisibility() {
        DrawerManager.saveOrder(context, Arrays.asList("star_hub", "search"));
        DrawerManager.setVisible(context, "star_hub", false);
        DrawerManager.resetToDefault(context);
        assertEquals(DEFAULT_KEYS, DrawerManager.getOrderedKeys(context));
        assertTrue(DrawerManager.getHiddenKeys(context).isEmpty());
        assertEquals(8, DrawerManager.getVisibleOrderedItems(context).size());
    }
}
