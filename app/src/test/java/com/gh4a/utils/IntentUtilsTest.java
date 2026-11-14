package com.gh4a.utils;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：IntentUtils。只覆盖不依赖 Activity/PackageManager 解析的
 * 公共方法；openLinkInternallyOrExternally / openInCustomTabOrBrowser 需
 * 真实 Activity 与 CustomTabs 解析，待 Mockito 后补。
 */
@RunWith(RobolectricTestRunner.class)
public class IntentUtilsTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void createBaseUriForUser_exact() {
        assertEquals("https://github.com/octocat",
                IntentUtils.createBaseUriForUser("octocat").build().toString());
    }

    @Test
    public void createBaseUriForRepo_exact() {
        assertEquals("https://github.com/octocat/Hello-World",
                IntentUtils.createBaseUriForRepo("octocat", "Hello-World").build().toString());
    }

    @Test
    public void createRawFileUrl_exact() {
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/README.md",
                IntentUtils.createRawFileUrl("octocat", "Hello-World", "main", "README.md"));
        assertEquals("https://raw.githubusercontent.com/o/r/v1.0/docs/a/b.txt",
                IntentUtils.createRawFileUrl("o", "r", "v1.0", "docs/a/b.txt"));
    }

    @Test
    public void isNewTaskIntent_nullAndPlain() {
        assertFalse(IntentUtils.isNewTaskIntent(null));
        assertFalse(IntentUtils.isNewTaskIntent(new Intent()));
    }

    @Test
    public void startNewTask_setsFlagsAndExtra() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"));
        IntentUtils.startNewTask(mContext, intent);
        assertTrue(IntentUtils.isNewTaskIntent(intent));
        int flags = intent.getFlags();
        assertTrue((flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        // Robolectric 下 startActivity 被 shadow 接住：发出的是同一 intent
        Intent started = Shadows.shadowOf((Application) mContext).getNextStartedActivity();
        assertEquals(intent, started);
    }

    @Test
    public void putAndGetCompressedExtra_stringRoundTrip() {
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "k", "hello world");
        assertEquals("hello world", IntentUtils.<String>getCompressedExtra(intent, "k"));
    }

    @Test
    public void putAndGetCompressedExtra_intAndLongRoundTrip() {
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "i", 42);
        IntentUtils.putCompressedExtra(intent, "l", 1234567890123L);
        assertEquals(Integer.valueOf(42), IntentUtils.<Integer>getCompressedExtra(intent, "i"));
        assertEquals(Long.valueOf(1234567890123L),
                IntentUtils.<Long>getCompressedExtra(intent, "l"));
    }

    @Test
    public void putAndGetCompressedExtra_listRoundTrip() {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "list", list);
        assertEquals(list, IntentUtils.<ArrayList<String>>getCompressedExtra(intent, "list"));
    }

    @Test
    public void getCompressedExtra_missingKeyAndNoExtras() {
        assertNull(IntentUtils.<String>getCompressedExtra(new Intent(), "absent"));
        Intent withOther = new Intent();
        IntentUtils.putCompressedExtra(withOther, "a", "x");
        assertNull(IntentUtils.<String>getCompressedExtra(withOther, "b"));
    }

    @Test
    public void putCompressedValueToBundle_directRoundTrip() {
        android.os.Bundle bundle = new android.os.Bundle();
        IntentUtils.putCompressedValueToBundle(bundle, "k", "bundle-value");
        assertEquals("bundle-value",
                IntentUtils.<String>readCompressedValueFromBundle(bundle, "k"));
        assertNull(IntentUtils.<String>readCompressedValueFromBundle(bundle, "nope"));
    }

    @Test
    public void copyToClipboard_setsPrimaryClip() {
        IntentUtils.copyToClipboard(mContext, "label", "copied text");
        ClipboardManager cm = (ClipboardManager) mContext.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertEquals("label", clip.getDescription().getLabel().toString());
        assertEquals("copied text", clip.getItemAt(0).getText().toString());
    }

    @Test
    public void launchBrowser_nullUriReturnsSilently() {
        IntentUtils.launchBrowser(mContext, null);
        IntentUtils.launchBrowser(mContext, null, 0);
    }

    @Test
    public void createViewerOrBrowserIntent_noResolverReturnsNull() {
        // Robolectric 下 PackageManager 查不到任何浏览器/查看器，返回 null。
        assertNull(IntentUtils.createViewerOrBrowserIntent(mContext,
                Uri.parse("https://example.com/x.png"), "image/png"));
    }

    @Test
    public void initialCommentMarker_matchesById() {
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(123L);
        assertTrue(marker.matches(123L, null));
        assertFalse(marker.matches(124L, null));
        assertFalse(marker.matches(-1, null));
        assertEquals(0, marker.describeContents());
    }

    @Test
    public void initialCommentMarker_matchesByDateWindow() {
        Date markerDate = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(markerDate);
        // 通知时间戳比事件早 ~10 秒：marker 日期在后 10 秒内 → 匹配
        assertTrue(marker.matches(-1, new Date(1793872117000L - 10_000)));
        assertTrue(marker.matches(-1, new Date(1793872117000L)));
        // 29 秒内仍匹配，30 秒整不匹配（dateDiff < 30_000）
        assertTrue(marker.matches(-1, new Date(1793872117000L - 29_000)));
        assertFalse(marker.matches(-1, new Date(1793872117000L - 30_000)));
        // marker 日期在待匹配日期之前 → 不匹配
        assertFalse(marker.matches(-1, new Date(1793872117000L + 1_000)));
        assertFalse(marker.matches(-1, null));
    }

    @Test
    public void initialCommentMarker_idTakesPrecedenceOverDate() {
        Date markerDate = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(7L);
        assertTrue(marker.matches(7L, new Date(0)));
        assertFalse(marker.matches(8L, markerDate));
    }

    @Test
    public void initialCommentMarker_parcelRoundTrip() {
        Date date = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(99L);
        Parcel parcel = Parcel.obtain();
        marker.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IntentUtils.InitialCommentMarker restored =
                IntentUtils.InitialCommentMarker.CREATOR.createFromParcel(parcel);
        assertEquals(99L, restored.commentId);
        assertNull(restored.date);
        parcel.recycle();

        IntentUtils.InitialCommentMarker dateMarker =
                new IntentUtils.InitialCommentMarker(date);
        Parcel p2 = Parcel.obtain();
        dateMarker.writeToParcel(p2, 0);
        p2.setDataPosition(0);
        IntentUtils.InitialCommentMarker restored2 =
                IntentUtils.InitialCommentMarker.CREATOR.createFromParcel(p2);
        assertEquals(-1L, restored2.commentId);
        assertEquals(date, restored2.date);
        p2.recycle();

        assertEquals(2, IntentUtils.InitialCommentMarker.CREATOR.newArray(2).length);
    }
}
