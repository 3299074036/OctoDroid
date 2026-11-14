package com.gh4a.utils;

import android.content.Context;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

/**
 * B 组试点：HttpImageGetter。
 *
 * 可测部分：构造、encode/bind（无图 HTML，不触发网络加载）、pause/
 * resume/clearHtmlCache/destroy/rebind 的状态安全。真实图片加载
 *（loadImageForUrl，私有方法）需 OkHttp mock 注入 mClient，
 * mClient 是 final 且构造时取自 ServiceFactory，无法从外部替换，
 * 待 Mockito（mock ServiceFactory / OkHttpClient）后补。
 */
@RunWith(RobolectricTestRunner.class)
public class HttpImageGetterTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void constructor_doesNotThrow() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.destroy();
    }

    @Test
    public void encode_plainHtmlSetsNoImageSpans() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.encode(mContext, "id1", "<b>hello</b> world");
        getter.destroy();
    }

    @Test
    public void bind_plainHtmlSetsTextOnView() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "hello world", "id1");
        assertEquals("hello world", view.getText().toString());
        assertEquals(android.view.View.VISIBLE, view.getVisibility());
        getter.destroy();
    }

    @Test
    public void bind_emptyHtmlHidesView() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "", "id1");
        assertEquals(android.view.View.GONE, view.getVisibility());
        getter.destroy();
    }

    @Test
    public void bind_boldHtmlKeepsText() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<b>hi</b> there", "id1");
        assertEquals("hi there", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void bind_sameIdReusesCachedHtml() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "first", "id1");
        // 同一 id 再次 bind：ObjectInfo 已有 mHtml，不重新 encode，
        // 仍显示首次绑定的内容
        getter.bind(view, "second", "id1");
        assertEquals("first", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void rebind_replacesCachedHtmlForSameId() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "first", "id1");
        getter.rebind(mContext, view, "second", "id1");
        assertEquals("second", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void rebind_differentViewWorks() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view1 = new TextView(mContext);
        TextView view2 = new TextView(mContext);
        getter.bind(view1, "first", "id1");
        getter.rebind(mContext, view2, "second", "id1");
        assertEquals("second", view2.getText().toString());
        getter.destroy();
    }

    @Test
    public void lifecycle_pauseResumeClearDestroyAreSafe() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "hello", "id1");
        getter.pause();
        getter.resume();
        getter.clearHtmlCache();
        // 清缓存后再次 bind 会重新 encode
        getter.bind(view, "hello", "id1");
        assertEquals("hello", view.getText().toString());
        getter.destroy();
        // destroy 后再调生命周期方法不抛异常
        getter.pause();
        getter.resume();
        getter.clearHtmlCache();
        getter.destroy();
    }

    @Test
    public void destroy_beforeBindIsSafe() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.destroy();
        getter.encode(mContext, "id1", "late");
        getter.destroy();
    }
}
