package com.gh4a.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.widget.ReactionBar.AddReactionMenuHelper;
import com.gh4a.widget.ReactionBar.ReactionDetailsCache;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ReactionBarTest {

    private static class TestItem implements ReactionBar.Item {
        private final String key;

        TestItem(String key) {
            this.key = key;
        }

        @Override
        public Object getCacheKey() {
            return key;
        }
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ReactionBar bar(FragmentActivity activity) {
        ReactionBar bar = new ReactionBar(activity);
        activity.setContentView(bar);
        bar.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, 800, 200);
        return bar;
    }

    private void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private Reaction reaction(long id, String content, String login, Date createdAt) {
        return Reaction.builder()
                .id(id)
                .content(content)
                .user(User.builder().login(login).id(0L).build())
                .createdAt(createdAt)
                .build();
    }

// AutoValue's Reactions builder requires all 8 counts; use this helper
    // instead of partial builder() chains.
    private Reactions reactions(int plusOne, int heart) {
        return Reactions.builder().plusOne(plusOne).minusOne(0).laugh(0).hooray(0)
                .confused(0).heart(heart).rocket(0).eyes(0).build();
    }

    private ReactionBar.Callback callbackReturning(List<Reaction> details) {
        ReactionBar.Callback cb = mock(ReactionBar.Callback.class);
        when(cb.canAddReaction()).thenReturn(true);
        when(cb.loadReactionDetails(any(), anyBoolean()))
                .thenReturn(Single.just(details));
        when(cb.addReaction(any(), anyString()))
                .thenReturn(Single.just(reaction(99L, Reaction.CONTENT_PLUS_ONE, "me",
                        new Date())));
        when(cb.deleteReaction(any(), anyLong())).thenReturn(Single.just(true));
        return cb;
    }

    private ReactionDetailsCache cache() {
        return new ReactionDetailsCache(mock(ReactionDetailsCache.Listener.class));
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private Method accessibleMethod(Object target, String name, Class<?>... types)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    // ---------- setReactions ----------

    @Test
    public void setReactions_null_hidesBar() {
        ReactionBar bar = bar(activity());
        assertEquals(View.GONE, bar.getVisibility());
    }

    @Test
    public void setReactions_allNullCounts_hidesBar() {
        ReactionBar bar = bar(activity());
        // The builder cannot express null counts (its setters reject null),
        // so use a mock: all getters default to null, which production
        // must treat as zero via ApiHelpers.orZero.
        bar.setReactions(mock(Reactions.class));
        assertEquals(View.GONE, bar.getVisibility());
    }

    @Test
    public void setReactions_zeroTotal_hidesBar() {
        ReactionBar bar = bar(activity());
        bar.setReactions(Reactions.builder().plusOne(0).minusOne(0).laugh(0).hooray(0)
                .confused(0).heart(0).rocket(0).eyes(0).build());
        assertEquals(View.GONE, bar.getVisibility());
    }

    @Test
    public void setReactions_withCounts_showsBarAndCounts() {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        bar.setReactions(Reactions.builder().plusOne(2).minusOne(0).laugh(1).hooray(0)
                .confused(0).heart(3).rocket(0).eyes(0).build());
        assertEquals(View.VISIBLE, bar.getVisibility());
        TextView plusOne = bar.findViewById(R.id.plus_one);
        assertEquals(View.VISIBLE, plusOne.getVisibility());
        assertEquals("2", plusOne.getText().toString());
        assertEquals(View.GONE, bar.findViewById(R.id.minus_one).getVisibility());
        assertEquals("1", ((TextView) bar.findViewById(R.id.laugh)).getText().toString());
        assertEquals("3", ((TextView) bar.findViewById(R.id.heart)).getText().toString());
    }

    @Test
    public void setReactions_thenNull_hidesBarAgain() {
        ReactionBar bar = bar(activity());
        bar.setReactions(reactions(1, 0));
        assertEquals(View.VISIBLE, bar.getVisibility());
        bar.setReactions(null);
        assertEquals(View.GONE, bar.getVisibility());
    }

    @Test
    public void setReactions_partialNullCounts_treatedAsZero() {
        ReactionBar bar = bar(activity());
        // heart() is null here (mock default); production must treat it as
        // zero via ApiHelpers.orZero instead of unboxing it.
        Reactions partial = mock(Reactions.class);
        when(partial.plusOne()).thenReturn(1);
        when(partial.totalCount()).thenReturn(1);
        bar.setReactions(partial);
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertEquals(View.GONE, bar.findViewById(R.id.heart).getVisibility());
    }

    // ---------- setCallback ----------

    @Test
    public void setCallback_null_reactButtonGone() {
        ReactionBar bar = bar(activity());
        bar.setCallback(null, new TestItem("k"));
        assertEquals(View.GONE, bar.findViewById(R.id.react).getVisibility());
    }

    @Test
    public void setCallback_nonNull_notAuthorized_reactButtonGone() {
        // No account is logged in under Robolectric, so isAuthorized() is
        // false and the react button stays hidden even with a callback.
        ReactionBar bar = bar(activity());
        bar.setCallback(callbackReturning(Collections.emptyList()), new TestItem("k"));
        assertEquals(View.GONE, bar.findViewById(R.id.react).getVisibility());
    }

    // ---------- getReactionContentByViewId ----------

    @Test
    public void getReactionContentByViewId_mapsAllViews() throws Exception {
        Method method = accessibleMethod(new ReactionBar(activity()),
                "getReactionContentByViewId", int.class);
        assertEquals(Reaction.CONTENT_PLUS_ONE, method.invoke(null, R.id.plus_one));
        assertEquals(Reaction.CONTENT_MINUS_ONE, method.invoke(null, R.id.minus_one));
        assertEquals(Reaction.CONTENT_LAUGH, method.invoke(null, R.id.laugh));
        assertEquals(Reaction.CONTENT_HOORAY, method.invoke(null, R.id.hooray));
        assertEquals(Reaction.CONTENT_HEART, method.invoke(null, R.id.heart));
        assertEquals(Reaction.CONTENT_CONFUSED, method.invoke(null, R.id.confused));
        assertEquals(Reaction.CONTENT_ROCKET, method.invoke(null, R.id.rocket));
        assertEquals(Reaction.CONTENT_EYES, method.invoke(null, R.id.eyes));
    }

    @Test
    public void getReactionContentByViewId_unknownId_returnsNull() throws Exception {
        Method method = accessibleMethod(new ReactionBar(activity()),
                "getReactionContentByViewId", int.class);
        assertNull(method.invoke(null, 0x12345678));
    }

    // ---------- compareNullable ----------

    @Test
    @SuppressWarnings("unchecked")
    public void compareNullable_nullsLast() throws Exception {
        Method method = accessibleMethod(new ReactionBar(activity()),
                "compareNullable", Comparable.class, Comparable.class);
        assertEquals(0, method.invoke(null, null, null));
        assertEquals(1, method.invoke(null, null, "x"));
        assertEquals(-1, method.invoke(null, "x", null));
        assertTrue((int) method.invoke(null, "a", "b") < 0);
        assertTrue((int) method.invoke(null, "b", "a") > 0);
        assertEquals(0, method.invoke(null, "a", "a"));
    }

    // ---------- fetchReactions / toggleReaction ----------

    @Test
    @SuppressWarnings("unchecked")
    public void fetchReactions_sortsByContentThenCreatedAtDesc() throws Exception {
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        Date early = new Date(1000L);
        Date late = new Date(2000L);
        List<Reaction> unsorted = Arrays.asList(
                reaction(3L, "heart", "c", late),
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", early),
                reaction(2L, Reaction.CONTENT_PLUS_ONE, "b", late),
                reaction(4L, null, "d", late));
        when(cb.loadReactionDetails(item, false)).thenReturn(Single.just(unsorted));
        Method fetch = accessibleMethod(new ReactionBar(activity()),
                "fetchReactions", ReactionBar.Callback.class, ReactionBar.Item.class,
                ReactionDetailsCache.class);
        Single<List<Reaction>> single =
                (Single<List<Reaction>>) fetch.invoke(null, cb, item, cache);
        AtomicReference<List<Reaction>> result = new AtomicReference<>();
        single.subscribe(result::set, e -> { throw new RuntimeException(e); });
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertNotNull(result.get());
        List<Reaction> sorted = result.get();
        assertEquals(4, sorted.size());
        assertEquals(2L, (long) sorted.get(0).id());
        assertEquals(1L, (long) sorted.get(1).id());
        assertEquals(3L, (long) sorted.get(2).id());
        assertEquals(4L, (long) sorted.get(3).id());
        // Sorted list is also written to the cache.
        assertEquals(sorted, cache.getReactions(item));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toggleReaction_add_appendsAndCaches() throws Exception {
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        List<Reaction> existing = new ArrayList<>();
        Reaction added = reaction(7L, Reaction.CONTENT_HEART, "me", new Date());
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.addReaction(item, Reaction.CONTENT_HEART)).thenReturn(Single.just(added));
        Method toggle = accessibleMethod(new ReactionBar(activity()),
                "toggleReaction", String.class, Long.class, List.class,
                ReactionBar.Callback.class, ReactionBar.Item.class,
                ReactionDetailsCache.class);
        Single<Optional<Reaction>> single = (Single<Optional<Reaction>>) toggle.invoke(
                null, Reaction.CONTENT_HEART, null, existing, cb, item, cache);
        AtomicReference<Optional<Reaction>> result = new AtomicReference<>();
        single.subscribe(result::set, e -> { throw new RuntimeException(e); });
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertNotNull(result.get());
        assertTrue(result.get().isPresent());
        assertEquals(7L, (long) result.get().get().id());
        assertEquals(1, existing.size());
        assertEquals(1, cache.getReactions(item).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toggleReaction_delete_removesAndCaches() throws Exception {
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        List<Reaction> existing = new ArrayList<>(Arrays.asList(
                reaction(7L, Reaction.CONTENT_HEART, "me", new Date()),
                reaction(8L, Reaction.CONTENT_PLUS_ONE, "other", new Date())));
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.deleteReaction(item, 7L)).thenReturn(Single.just(true));
        Method toggle = accessibleMethod(new ReactionBar(activity()),
                "toggleReaction", String.class, Long.class, List.class,
                ReactionBar.Callback.class, ReactionBar.Item.class,
                ReactionDetailsCache.class);
        Single<Optional<Reaction>> single = (Single<Optional<Reaction>>) toggle.invoke(
                null, Reaction.CONTENT_HEART, 7L, existing, cb, item, cache);
        AtomicReference<Optional<Reaction>> result = new AtomicReference<>();
        single.subscribe(result::set, e -> { throw new RuntimeException(e); });
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertNotNull(result.get());
        assertFalse(result.get().isPresent());
        assertEquals(1, existing.size());
        assertEquals(8L, (long) existing.get(0).id());
        assertEquals(1, cache.getReactions(item).size());
    }

    // ---------- ReactionDetailsCache ----------

    @Test
    public void cache_initiallyEmpty() {
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        assertFalse(cache.hasEntryFor(item));
        assertNull(cache.getReactions(item));
    }

    @Test
    public void cache_putAndGet_returnsCopy() {
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        List<Reaction> list = new ArrayList<>(Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date())));
        cache.putReactions(item, list);
        assertTrue(cache.hasEntryFor(item));
        assertEquals(1, cache.getReactions(item).size());
        list.clear();
        assertEquals(1, cache.getReactions(item).size());
    }

    @Test
    public void cache_putTwice_notifiesListenerWithCounts() {
        ReactionDetailsCache.Listener listener = mock(ReactionDetailsCache.Listener.class);
        ReactionDetailsCache cache = new ReactionDetailsCache(listener);
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date())));
        cache.putReactions(item, Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date()),
                reaction(2L, Reaction.CONTENT_HEART, "b", new Date()),
                reaction(3L, null, "c", new Date()),
                null));
        AtomicReference<Reactions> built = new AtomicReference<>();
        org.mockito.ArgumentCaptor<Reactions> captor =
                org.mockito.ArgumentCaptor.forClass(Reactions.class);
        verify(listener).onReactionsUpdated(any(), captor.capture());
        built.set(captor.getValue());
        assertEquals(1, (int) built.get().plusOne());
        assertEquals(1, (int) built.get().heart());
        assertEquals(0, (int) built.get().laugh());
    }

    @Test
    public void cache_putFirstTime_doesNotNotifyListener() {
        ReactionDetailsCache.Listener listener = mock(ReactionDetailsCache.Listener.class);
        ReactionDetailsCache cache = new ReactionDetailsCache(listener);
        cache.putReactions(new TestItem("k"), Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date())));
        verify(listener, never()).onReactionsUpdated(any(), any());
    }

    @Test
    public void cache_destroyed_doesNotNotifyListener() {
        ReactionDetailsCache.Listener listener = mock(ReactionDetailsCache.Listener.class);
        ReactionDetailsCache cache = new ReactionDetailsCache(listener);
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.emptyList());
        cache.destroy();
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date())));
        verify(listener, never()).onReactionsUpdated(any(), any());
        assertEquals(1, cache.getReactions(item).size());
    }

    @Test
    public void cache_clear_removesEntries() {
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", new Date())));
        cache.clear();
        assertFalse(cache.hasEntryFor(item));
        assertNull(cache.getReactions(item));
    }

    @Test
    public void cache_differentKeysAreIndependent() {
        ReactionDetailsCache cache = cache();
        TestItem a = new TestItem("a");
        TestItem b = new TestItem("b");
        cache.putReactions(a, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "x", new Date())));
        assertFalse(cache.hasEntryFor(b));
        assertNull(cache.getReactions(b));
    }

    // ---------- AddReactionMenuHelper ----------

    private Menu inflatedReactionMenu(FragmentActivity activity) {
        Menu menu = new MenuBuilder(activity);
        new SupportMenuInflater(activity).inflate(R.menu.reaction_menu, menu);
        return menu;
    }

    @Test
    public void menuHelper_onItemClick_unknownItem_returnsFalse() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), new TestItem("k"), cache());
        MenuItem loading = menu.findItem(R.id.loading);
        assertFalse(helper.onItemClick(loading));
    }

    @Test
    public void menuHelper_onItemClick_reactionItem_callsAddReaction() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        TestItem item = new TestItem("k");
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache());
        assertTrue(helper.onItemClick(menu.findItem(R.id.plus_one)));
        verify(cb).addReaction(item, Reaction.CONTENT_PLUS_ONE);
    }

    @Test
    public void menuHelper_updateMenuItems_noCacheEntry_hidesReactionItems() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), new TestItem("k"), cache());
        helper.updateMenuItems();
        assertTrue(menu.findItem(R.id.loading).isVisible());
        assertFalse(menu.findItem(R.id.plus_one).isVisible());
        assertFalse(menu.findItem(R.id.eyes).isVisible());
    }

    @Test
    public void menuHelper_updateMenuItems_withCacheEntry_showsReactionItems() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "someone", new Date())));
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), item, cache);
        helper.updateMenuItems();
        assertFalse(menu.findItem(R.id.loading).isVisible());
        assertTrue(menu.findItem(R.id.plus_one).isVisible());
    }

    @Test
    public void menuHelper_startLoadingIfNeeded_withEntry_updatesSynchronously() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "someone", new Date())));
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), item, cache);
        helper.startLoadingIfNeeded();
        assertFalse(menu.findItem(R.id.loading).isVisible());
        assertTrue(menu.findItem(R.id.plus_one).isVisible());
    }

    @Test
    public void menuHelper_startLoadingIfNeeded_withoutEntry_fetchesDetails() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        TestItem item = new TestItem("k");
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache());
        helper.startLoadingIfNeeded();
        verify(cb).loadReactionDetails(item, false);
    }

    // ---------- popup flows through the bar ----------

    @Test
    public void onClick_reactionView_loadsDetailsForContent() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(2L, Reaction.CONTENT_HEART, "bob", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 1));

        bar.findViewById(R.id.plus_one).performClick();

        verify(cb).loadReactionDetails(item, false);
        // The popup was created and is showing.
        Object popup = field(bar, "mPopup");
        assertNotNull(popup);
        assertTrue(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
    }

    @Test
    public void onClick_reactionViewTwice_dismissesPopupFirst() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        assertTrue(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
        bar.findViewById(R.id.plus_one).performClick();
        assertFalse(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
    }

    @Test
    public void popupAdapter_populatedWithMatchingReactions() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(2L, Reaction.CONTENT_HEART, "bob", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 1));

        bar.findViewById(R.id.plus_one).performClick();

        Object popup = field(bar, "mPopup");
        Object adapter = field(popup, "mAdapter");
        BaseAdapter baseAdapter = (BaseAdapter) adapter;
        long deadline = System.currentTimeMillis() + 5000;
        while (baseAdapter.getItemViewType(0) != 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertEquals(1, baseAdapter.getCount());
        assertEquals(0, baseAdapter.getItemViewType(0));
        assertEquals("alice", ((User) baseAdapter.getItem(0)).login());
    }

    @Test
    public void popupAdapter_loadingState_showsProgressRow() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();

        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        // Before the fetch completes, the adapter has no users: a single
        // progress row.
        assertEquals(1, adapter.getCount());
        assertEquals(1, adapter.getItemViewType(0));
        assertNull(adapter.getItem(0));
    }

    @Test
    public void popupAdapter_rowClick_opensUserActivity() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();

        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        long deadline = System.currentTimeMillis() + 5000;
        while ((adapter.getCount() != 1 || adapter.getItemViewType(0) != 0)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(0, null, parent);
        assertNotNull(row.findViewById(R.id.avatar));
        assertEquals("alice", ((TextView) row.findViewById(R.id.name)).getText().toString());

        row.performClick();

        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onDetachedFromWindow_dismissesPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setReactions(reactions(1, 0));
        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        assertTrue(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());

        bar.onDetachedFromWindow();

        assertFalse(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
    }

    @Test
    public void onSaveInstanceState_dismissesPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setReactions(reactions(1, 0));
        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        assertTrue(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());

        bar.onSaveInstanceState();

        assertFalse(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
    }

    @Test
    public void setReactions_withPopup_updatesPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        bar.setDetailsCache(cache);
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setReactions(reactions(1, 0));
        bar.findViewById(R.id.plus_one).performClick();

        bar.setReactions(reactions(2, 0));

        assertEquals("2", ((TextView) bar.findViewById(R.id.plus_one)).getText().toString());
    }

    @Test
    public void setAddReactionPopupMenu_storesMenuResId() {
        ReactionBar bar = bar(activity());
        bar.setAddReactionPopupMenu(R.menu.reaction_menu);
    }

    // ---------- gap-closing tests ----------

    private void setAuthLogin(FragmentActivity activity, String login, long userId) {
        // getAuthLogin()/getCurrentAccountInfoForAvatar() read the active
        // login + user id from the plain prefs ("Gh4a-pref").
        activity.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("active_login", login)
                .putLong("user_id_" + login, userId)
                .commit();
    }

    private void setAuthorizedUser(FragmentActivity activity, String login, long userId) {
        setAuthLogin(activity, login, userId);
        // isAuthorized() additionally needs a token in the secure prefs,
        // which fall back to plaintext SharedPreferences under Robolectric.
        // The value below is a fake test token, not a real credential.
        Gh4Application.securePrefs(activity)
                .edit()
                .putString("token_" + login, "test-token")
                .commit();
    }

    private void waitForAdapterLoaded(BaseAdapter adapter) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (adapter.getItemViewType(0) != 0
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
    }

    @Test
    public void cache_buildReactions_countsAllContents() {
        ReactionDetailsCache.Listener listener = mock(ReactionDetailsCache.Listener.class);
        ReactionDetailsCache cache = new ReactionDetailsCache(listener);
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.emptyList());
        Date now = new Date();
        cache.putReactions(item, Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "a", now),
                reaction(2L, Reaction.CONTENT_MINUS_ONE, "b", now),
                reaction(3L, Reaction.CONTENT_CONFUSED, "c", now),
                reaction(4L, Reaction.CONTENT_HEART, "d", now),
                reaction(5L, Reaction.CONTENT_HOORAY, "e", now),
                reaction(6L, Reaction.CONTENT_LAUGH, "f", now),
                reaction(7L, Reaction.CONTENT_ROCKET, "g", now),
                reaction(8L, Reaction.CONTENT_EYES, "h", now),
                reaction(9L, "weird", "i", now)));
        org.mockito.ArgumentCaptor<Reactions> captor =
                org.mockito.ArgumentCaptor.forClass(Reactions.class);
        verify(listener).onReactionsUpdated(any(), captor.capture());
        Reactions built = captor.getValue();
        assertEquals(1, (int) built.plusOne());
        assertEquals(1, (int) built.minusOne());
        assertEquals(1, (int) built.confused());
        assertEquals(1, (int) built.heart());
        assertEquals(1, (int) built.hooray());
        assertEquals(1, (int) built.laugh());
        assertEquals(1, (int) built.rocket());
        assertEquals(1, (int) built.eyes());
    }

    @Test
    public void setCallback_authorizedAndCanAdd_reactButtonVisible() {
        FragmentActivity activity = activity();
        setAuthorizedUser(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        bar.setCallback(callbackReturning(Collections.emptyList()), new TestItem("k"));
        assertEquals(View.VISIBLE, bar.findViewById(R.id.react).getVisibility());
    }

    @Test
    public void setCallback_authorizedCannotAdd_reactButtonGone() {
        FragmentActivity activity = activity();
        setAuthorizedUser(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.canAddReaction()).thenReturn(false);
        bar.setCallback(cb, new TestItem("k"));
        assertEquals(View.GONE, bar.findViewById(R.id.react).getVisibility());
    }

    @Test
    public void onClick_reactButton_createsAndShowsAddReactionPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setAddReactionPopupMenu(R.menu.reaction_menu);
        bar.setReactions(reactions(1, 0));

        bar.onClick(bar.findViewById(R.id.react));

        Object popup = field(bar, "mAddReactionPopup");
        assertNotNull(popup);
        assertNotNull(field(bar, "mAddReactionMenuHelper"));
        // Second click: the popup already exists, so creation is skipped.
        bar.onClick(bar.findViewById(R.id.react));
        assertSame(popup, field(bar, "mAddReactionPopup"));
    }

    @Test
    public void setReactions_withAddReactionHelper_updatesMenuItems() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        bar.setDetailsCache(cache());
        bar.setCallback(callbackReturning(Collections.emptyList()), item);
        bar.setAddReactionPopupMenu(R.menu.reaction_menu);
        bar.setReactions(reactions(1, 0));
        bar.onClick(bar.findViewById(R.id.react));

        bar.setReactions(reactions(2, 0));

        assertEquals("2", ((TextView) bar.findViewById(R.id.plus_one)).getText().toString());
    }

    @Test
    public void onClick_reactionView_differentContentAfterLoad_usesCache() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(2L, Reaction.CONTENT_HEART, "bob", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 1));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);
        assertEquals(1, adapter.getCount());

        // Dismiss, then click a different reaction: the popup is reused
        // (not recreated) and the cached details populate it directly.
        ((androidx.appcompat.widget.ListPopupWindow) popup).dismiss();
        bar.findViewById(R.id.heart).performClick();
        assertSame(popup, field(bar, "mPopup"));
        assertEquals(1, adapter.getCount());
        assertEquals("bob", ((User) adapter.getItem(0)).login());
    }

    @Test
    public void onClick_reactionView_fetchError_dismissesPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.loadReactionDetails(any(), anyBoolean()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");

        long deadline = System.currentTimeMillis() + 5000;
        while (((androidx.appcompat.widget.ListPopupWindow) popup).isShowing()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertFalse(((androidx.appcompat.widget.ListPopupWindow) popup).isShowing());
    }

    @Test
    public void setReactions_withPopupAndLoadedDetails_updatesPopup() throws Exception {
        FragmentActivity activity = activity();
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(2L, Reaction.CONTENT_HEART, "bob", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 1));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        bar.setReactions(reactions(2, 0));

        assertEquals("2", ((TextView) bar.findViewById(R.id.plus_one)).getText().toString());
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void onDetachedFromWindow_noPopup_doesNotThrow() {
        ReactionBar bar = bar(activity());
        bar.onDetachedFromWindow();
    }

    @Test
    public void onSaveInstanceState_noPopup_doesNotThrow() {
        ReactionBar bar = bar(activity());
        bar.onSaveInstanceState();
    }

    @Test
    public void popupAdapter_ownUserRow_addReaction() throws Exception {
        FragmentActivity activity = activity();
        // id 0: AvatarHandler.assignAvatar does not fetch an avatar for it.
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        // [alice, null, me]: divider at 1, own row at 2.
        assertEquals(3, adapter.getCount());
        assertEquals(2, adapter.getItemViewType(1));
        assertEquals(0, adapter.getItemViewType(2));
        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(2, null, parent);
        assertEquals(activity.getString(R.string.add_reaction),
                ((TextView) row.findViewById(R.id.name)).getText().toString());
        assertEquals(0.4f, ((ImageView) row.findViewById(R.id.avatar)).getAlpha(), 0.001f);
        // Reusing the row as convertView covers the non-null convertView path.
        assertSame(row, adapter.getView(2, row, parent));
    }

    @Test
    public void popupAdapter_ownUserRow_removeReaction() throws Exception {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(7L, Reaction.CONTENT_PLUS_ONE, "me", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(2, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        assertEquals(3, adapter.getCount());
        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(2, null, parent);
        assertEquals(activity.getString(R.string.remove_reaction),
                ((TextView) row.findViewById(R.id.name)).getText().toString());
        assertEquals(1.0f, ((ImageView) row.findViewById(R.id.avatar)).getAlpha(), 0.001f);
    }

    @Test
    public void popupAdapter_ownUserRow_cannotAdd_showsAsPlainUser() throws Exception {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(7L, Reaction.CONTENT_PLUS_ONE, "me", new Date())));
        when(cb.canAddReaction()).thenReturn(false);
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(2, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        // Own reaction present but adding disabled: own user prepended,
        // no divider, and the row takes the plain-user branch.
        assertEquals(2, adapter.getCount());
        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(0, null, parent);
        assertEquals("me", ((TextView) row.findViewById(R.id.name)).getText().toString());
    }

    @Test
    public void popupAdapter_ownUserNotReacted_cannotAdd_noOwnRow() throws Exception {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date())));
        when(cb.canAddReaction()).thenReturn(false);
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        assertEquals(1, adapter.getCount());
        assertEquals("alice", ((User) adapter.getItem(0)).login());
    }

    @Test
    public void popupAdapter_ownRowClick_addsOwnReaction() throws Exception {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(1, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(2, null, parent);
        row.performClick();
        verify(cb).addReaction(item, Reaction.CONTENT_PLUS_ONE);
    }

    @Test
    public void popupAdapter_ownRowClick_removesOwnReaction() throws Exception {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        ReactionBar bar = bar(activity);
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        ReactionBar.Callback cb = callbackReturning(Arrays.asList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "alice", new Date()),
                reaction(7L, Reaction.CONTENT_PLUS_ONE, "me", new Date())));
        bar.setDetailsCache(cache);
        bar.setCallback(cb, item);
        bar.setReactions(reactions(2, 0));

        bar.findViewById(R.id.plus_one).performClick();
        Object popup = field(bar, "mPopup");
        BaseAdapter adapter = (BaseAdapter) field(popup, "mAdapter");
        waitForAdapterLoaded(adapter);

        ViewGroup parent = new android.widget.FrameLayout(activity);
        View row = adapter.getView(2, null, parent);
        row.performClick();
        verify(cb).deleteReaction(item, 7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toggleReaction_delete_idNotInList_loopsToEnd() throws Exception {
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        List<Reaction> existing = new ArrayList<>(Collections.singletonList(
                reaction(8L, Reaction.CONTENT_PLUS_ONE, "other", new Date())));
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.deleteReaction(item, 7L)).thenReturn(Single.just(true));
        Method toggle = accessibleMethod(new ReactionBar(activity()),
                "toggleReaction", String.class, Long.class, List.class,
                ReactionBar.Callback.class, ReactionBar.Item.class,
                ReactionDetailsCache.class);
        Single<Optional<Reaction>> single = (Single<Optional<Reaction>>) toggle.invoke(
                null, Reaction.CONTENT_HEART, 7L, existing, cb, item, cache);
        AtomicReference<Optional<Reaction>> result = new AtomicReference<>();
        single.subscribe(result::set, e -> { throw new RuntimeException(e); });
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertNotNull(result.get());
        assertFalse(result.get().isPresent());
        assertEquals(1, existing.size());
        assertEquals(1, cache.getReactions(item).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void toggleReaction_delete_nonMatchingFirst_skipsToMatch() throws Exception {
        TestItem item = new TestItem("k");
        ReactionDetailsCache cache = cache();
        List<Reaction> existing = new ArrayList<>(Arrays.asList(
                reaction(8L, Reaction.CONTENT_PLUS_ONE, "other", new Date()),
                reaction(7L, Reaction.CONTENT_HEART, "me", new Date())));
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.deleteReaction(item, 7L)).thenReturn(Single.just(true));
        Method toggle = accessibleMethod(new ReactionBar(activity()),
                "toggleReaction", String.class, Long.class, List.class,
                ReactionBar.Callback.class, ReactionBar.Item.class,
                ReactionDetailsCache.class);
        Single<Optional<Reaction>> single = (Single<Optional<Reaction>>) toggle.invoke(
                null, Reaction.CONTENT_HEART, 7L, existing, cb, item, cache);
        AtomicReference<Optional<Reaction>> result = new AtomicReference<>();
        single.subscribe(result::set, e -> { throw new RuntimeException(e); });
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertNotNull(result.get());
        assertFalse(result.get().isPresent());
        assertEquals(1, existing.size());
        assertEquals(8L, (long) existing.get(0).id());
    }

    @Test
    public void menuHelper_constructor_nullMenuItem_skipsEntry() {
        FragmentActivity activity = activity();
        Menu menu = mock(Menu.class);
        MenuItem loading = mock(MenuItem.class);
        when(menu.findItem(R.id.loading)).thenReturn(loading);
        // Every reaction item except plus_one is present; plus_one is
        // deliberately left unstubbed so findItem returns null for it.
        for (int id : new int[]{R.id.minus_one, R.id.laugh, R.id.hooray, R.id.heart,
                R.id.confused, R.id.rocket, R.id.eyes}) {
            MenuItem item = mock(MenuItem.class);
            Drawable icon = mock(Drawable.class);
            when(icon.mutate()).thenReturn(icon);
            when(item.getIcon()).thenReturn(icon);
            when(menu.findItem(id)).thenReturn(item);
        }
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), new TestItem("k"), cache());
        assertFalse(helper.onItemClick(loading));
    }

    @Test
    public void menuHelper_startLoadingIfNeeded_alreadyLoading_skipsFetch() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.loadReactionDetails(any(), anyBoolean())).thenReturn(Single.never());
        TestItem item = new TestItem("k");
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache());
        helper.startLoadingIfNeeded();
        helper.startLoadingIfNeeded();
        verify(cb, times(1)).loadReactionDetails(item, false);
    }

    @Test
    public void menuHelper_startLoadingIfNeeded_error_updatesMenuItems() throws Exception {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        when(cb.loadReactionDetails(any(), anyBoolean()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        TestItem item = new TestItem("k");
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache());
        helper.startLoadingIfNeeded();
        // The error path updates the menu: with no cached reactions the
        // reaction items are hidden again.
        long deadline = System.currentTimeMillis() + 5000;
        while (menu.findItem(R.id.plus_one).isVisible()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            idleMainLooper();
        }
        assertFalse(menu.findItem(R.id.plus_one).isVisible());
    }

    @Test
    public void menuHelper_updateMenuItems_ownReaction_checksItem() {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        Menu menu = inflatedReactionMenu(activity);
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(1L, Reaction.CONTENT_PLUS_ONE, "me", new Date())));
        AddReactionMenuHelper helper = new AddReactionMenuHelper(activity, menu,
                callbackReturning(Collections.emptyList()), item, cache);
        helper.updateMenuItems();
        assertTrue(menu.findItem(R.id.plus_one).isChecked());
        assertFalse(menu.findItem(R.id.heart).isChecked());
    }

    @Test
    public void menuHelper_onItemClick_noCachedEntry_addsReaction() {
        FragmentActivity activity = activity();
        Menu menu = inflatedReactionMenu(activity);
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        TestItem item = new TestItem("k");
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache());
        assertTrue(helper.onItemClick(menu.findItem(R.id.plus_one)));
        verify(cb).addReaction(item, Reaction.CONTENT_PLUS_ONE);
    }

    @Test
    public void menuHelper_onItemClick_cachedOwnReaction_removesReaction() {
        FragmentActivity activity = activity();
        setAuthLogin(activity, "me", 0L);
        Menu menu = inflatedReactionMenu(activity);
        ReactionDetailsCache cache = cache();
        TestItem item = new TestItem("k");
        cache.putReactions(item, Collections.singletonList(
                reaction(7L, Reaction.CONTENT_PLUS_ONE, "me", new Date())));
        ReactionBar.Callback cb = callbackReturning(Collections.emptyList());
        AddReactionMenuHelper helper =
                new AddReactionMenuHelper(activity, menu, cb, item, cache);
        helper.updateMenuItems();
        assertTrue(helper.onItemClick(menu.findItem(R.id.plus_one)));
        verify(cb).deleteReaction(item, 7L);
    }
}
