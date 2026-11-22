package com.gh4a.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ApiRequestException;
import com.gh4a.R;
import com.google.android.material.snackbar.Snackbar;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.SearchPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.SingleSubject;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * B 组试点：RxUtils。纯 Single 变换走纯 JVM 测试（不加 Robolectric）。
 * doInBackground / wrapForBackgroundTask / wrapWithProgressDialog /
 * wrapWithErrorSnackbar 需 Activity、主线程调度与 Snackbar，
 * 待 Mockito（Activity mock）后补。
 */
@RunWith(RobolectricTestRunner.class)
public class RxUtilsTest {

    private static ApiRequestException apiError(int status) {
        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"), "{\"message\":\"err\"}");
        return new ApiRequestException(Response.error(status, body));
    }

    private static SearchPage<String> searchPage(List<String> items) {
        return new SearchPage<String>() {
            @Override
            public Integer next() {
                return 2;
            }

            @Override
            public Integer last() {
                return 5;
            }

            @Override
            public Integer first() {
                return 1;
            }

            @Override
            public Integer prev() {
                return null;
            }

            @Override
            public Long totalCount() {
                return (long) items.size();
            }

            @Override
            public Boolean incompleteResults() {
                return false;
            }

            @Override
            public List<String> items() {
                return items;
            }
        };
    }

    @Test
    public void filter_keepsMatchingOnly() {
        List<Integer> result = Single.just(Arrays.asList(1, 2, 3, 4))
                .compose(RxUtils.filter(x -> x % 2 == 0))
                .blockingGet();
        assertEquals(Arrays.asList(2, 4), result);
    }

    @Test
    public void filter_noneMatchAndEmpty() {
        assertEquals(new ArrayList<>(), Single.just(Arrays.asList(1, 3))
                .compose(RxUtils.filter(x -> x % 2 == 0))
                .blockingGet());
        assertEquals(new ArrayList<>(), Single.just(new ArrayList<Integer>())
                .compose(RxUtils.filter(x -> true))
                .blockingGet());
    }

    @Test
    public void filterAndMapToFirst_foundAndMissing() {
        assertEquals(Optional.of(4), Single.just(Arrays.asList(1, 3, 4, 6))
                .compose(RxUtils.filterAndMapToFirst(x -> x % 2 == 0))
                .blockingGet());
        assertEquals(Optional.empty(), Single.just(Arrays.asList(1, 3))
                .compose(RxUtils.filterAndMapToFirst(x -> x % 2 == 0))
                .blockingGet());
        assertEquals(Optional.empty(), Single.just(new ArrayList<Integer>())
                .compose(RxUtils.filterAndMapToFirst(x -> true))
                .blockingGet());
    }

    @Test
    public void mapList_transformsEach() {
        assertEquals(Arrays.asList("1", "2"), Single.just(Arrays.asList(1, 2))
                .compose(RxUtils.mapList(String::valueOf))
                .blockingGet());
        assertEquals(new ArrayList<>(), Single.just(new ArrayList<Integer>())
                .compose(RxUtils.mapList(String::valueOf))
                .blockingGet());
    }

    @Test
    public void sortList_sortsAndDoesNotMutateInput() {
        List<Integer> input = new ArrayList<>(Arrays.asList(3, 1, 2));
        List<Integer> result = Single.just(input)
                .compose(RxUtils.sortList(Integer::compareTo))
                .blockingGet();
        assertEquals(Arrays.asList(1, 2, 3), result);
        assertEquals(Arrays.asList(3, 1, 2), input);
    }

    @Test
    public void sortList_customComparator() {
        assertEquals(Arrays.asList("ccc", "bb", "a"),
                Single.just(Arrays.asList("a", "ccc", "bb"))
                        .compose(RxUtils.sortList(
                                (x, y) -> Integer.compare(y.length(), x.length())))
                        .blockingGet());
    }

    @Test
    public void mapFailureToValue_matchingStatusMapsToValue() {
        assertEquals(Boolean.FALSE, Single.<Boolean>error(apiError(403))
                .compose(RxUtils.mapFailureToValue(403, false))
                .blockingGet());
    }

    @Test
    public void mapFailureToValue_otherStatusPropagates() {
        try {
            Single.<Boolean>error(apiError(404))
                    .compose(RxUtils.mapFailureToValue(403, false))
                    .blockingGet();
            fail("expected ApiRequestException");
        } catch (ApiRequestException e) {
            assertEquals(404, e.getStatus());
        }
    }

    @Test
    public void mapFailureToValue_nonApiErrorPropagates() {
        IOException io = new IOException("net");
        try {
            Single.<Boolean>error(io)
                    .compose(RxUtils.mapFailureToValue(403, false))
                    .blockingGet();
            fail("expected IOException");
        } catch (RuntimeException e) {
            // RxJava2 blockingGet 把受检异常包进 RuntimeException
            assertEquals(io, e.getCause());
        }
    }

    @Test
    public void mapFailureToValue_successPassesThrough() {
        assertEquals(Boolean.TRUE, Single.just(true)
                .compose(RxUtils.mapFailureToValue(403, false))
                .blockingGet());
    }

    @Test
    public void toSingleOrFallback_presentSkipsFallback() {
        AtomicInteger calls = new AtomicInteger();
        Optional<String> result = RxUtils.toSingleOrFallback(Optional.of("x"),
                () -> {
                    calls.incrementAndGet();
                    return Single.just(Optional.of("y"));
                }).blockingGet();
        assertEquals(Optional.of("x"), result);
        assertEquals(0, calls.get());
    }

    @Test
    public void toSingleOrFallback_absentUsesFallback() {
        Optional<String> result = RxUtils.toSingleOrFallback(Optional.empty(),
                () -> Single.just(Optional.of("y"))).blockingGet();
        assertEquals(Optional.of("y"), result);
    }

    @Test
    public void mapToSingle_presentMapsAndAbsentSkips() {
        AtomicInteger calls = new AtomicInteger();
        Optional<String> mapped = RxUtils.mapToSingle(Optional.of(3),
                v -> {
                    calls.incrementAndGet();
                    return Single.just("n" + v);
                }).blockingGet();
        assertEquals(Optional.of("n3"), mapped);
        assertEquals(1, calls.get());

        Optional<String> empty = RxUtils.mapToSingle(Optional.empty(),
                v -> {
                    calls.incrementAndGet();
                    return Single.just("n" + v);
                }).blockingGet();
        assertEquals(Optional.empty(), empty);
        assertEquals(1, calls.get());
    }

    @Test
    public void searchPageAdapter_successMapsItems() {
        Response<SearchPage<String>> upstream =
                Response.success(searchPage(Arrays.asList("a", "b")));
        Response<Page<String>> result = RxUtils.searchPageAdapter(
                Single.just(upstream), String::toUpperCase).blockingGet();
        assertTrue(result.isSuccessful());
        assertEquals(Arrays.asList("A", "B"), result.body().items());
        assertEquals(Integer.valueOf(2), result.body().next());
        assertEquals(Integer.valueOf(5), result.body().last());
        // Page 只暴露 next/last/first/prev/items；totalCount 与
        // incompleteResults 是 SearchPage 的字段，不经 Page 暴露。
    }

    @Test
    public void searchPageAdapter_identityMapperKeepsItems() {
        Response<SearchPage<String>> upstream =
                Response.success(searchPage(Arrays.asList("x")));
        Response<Page<String>> result = RxUtils.searchPageAdapter(
                Single.just(upstream)).blockingGet();
        assertEquals(Arrays.asList("x"), result.body().items());
    }

    @Test
    public void searchPageAdapter_errorPassesThrough() {
        okhttp3.Response raw = new okhttp3.Response.Builder()
                .request(new Request.Builder().url("https://api.github.com/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .build();
        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"), "{\"message\":\"Not Found\"}");
        Response<SearchPage<String>> upstream = Response.error(body, raw);
        Response<Page<String>> result = RxUtils.searchPageAdapter(
                Single.just(upstream), String::toUpperCase).blockingGet();
        assertFalse(result.isSuccessful());
        assertEquals(404, result.code());
        assertTrue(result.errorBody() != null);
    }
    // ---- wrapWithProgressDialog ----

    @Test
    public void classCanBeInstantiated() {
        // L44：工具类默认构造。
        assertNotNull(new RxUtils());
    }

    @Test
    public void progressDialog_finishingActivitySkipsDialog() {
        // L151-152：isFinishing() 为真时 showDialog 直接返回，不建 fragment；
        // hideDialog 里 mFragment 为 null。
        FragmentActivity activity =
                Robolectric.buildActivity(FragmentActivity.class).setup().get();
        activity.finish();
        Single.just(1)
                .compose(RxUtils.wrapWithProgressDialog(activity, R.string.app_name))
                .test()
                .assertValue(1)
                .assertComplete();
        assertNull(activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog"));
    }

    @Test
    public void progressDialog_destroyedActivitySkipsDialog() {
        // L151-152：isDestroyed() 为真时同样跳过。
        org.robolectric.android.controller.ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        activity.finish();
        controller.destroy();
        assertTrue(activity.isDestroyed());
        Single.just(1)
                .compose(RxUtils.wrapWithProgressDialog(activity, R.string.app_name))
                .test()
                .assertValue(1)
                .assertComplete();
        assertNull(activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog"));
    }

    @Test
    public void progressDialog_showsAndDismissesDialog() {
        // L165：dialog 正常弹出并挂到 activity 后，成功时 hideDialog 走
        // dismissAllowingStateLoss 分支。
        FragmentActivity activity =
                Robolectric.buildActivity(FragmentActivity.class).setup().get();
        SingleSubject<Integer> subject = SingleSubject.create();
        TestObserver<Integer> to = subject
                .compose(RxUtils.wrapWithProgressDialog(activity, R.string.app_name))
                .test();
        ShadowLooper.idleMainLooper();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog"));
        subject.onSuccess(1);
        ShadowLooper.idleMainLooper();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNull(activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog"));
        to.assertValue(1).assertComplete();
    }

    @Test
    public void progressDialog_destroyedWithoutFinishSkipsDialog() {
        // L151：isDestroyed() 为真、isFinishing() 为假时，|| 第二侧为真，
        // showDialog 同样直接返回。第 1 轮的 destroyed 测试是 finish() +
        // destroy()（isFinishing() 先为真，只走了第一侧）；此处只 destroy()
        // 不 finish()，覆盖 isDestroyed() 单独为真的边。
        org.robolectric.android.controller.ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        controller.destroy();
        assertTrue(activity.isDestroyed());
        assertFalse(activity.isFinishing());
        Single.just(1)
                .compose(RxUtils.wrapWithProgressDialog(activity, R.string.app_name))
                .test()
                .assertValue(1)
                .assertComplete();
        assertNull(activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog"));
    }

    @Test
    public void progressDialog_detachedFragmentSkipsDismiss() {
        // L165：dialog 已弹出、mFragment 非 null，但 activity 已销毁导致
        // mFragment.getActivity() 为 null 时，hideDialog 跳过 dismiss。
        org.robolectric.android.controller.ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        SingleSubject<Integer> subject = SingleSubject.create();
        TestObserver<Integer> to = subject
                .compose(RxUtils.wrapWithProgressDialog(activity, R.string.app_name))
                .test();
        ShadowLooper.idleMainLooper();
        activity.getSupportFragmentManager().executePendingTransactions();
        androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentByTag("progressdialog");
        assertNotNull(fragment);
        controller.destroy();
        assertTrue(activity.isDestroyed());
        assertNull(fragment.getActivity());
        subject.onSuccess(1);
        ShadowLooper.idleMainLooper();
        to.assertValue(1).assertComplete();
    }

    // ---- wrapWithErrorSnackbar：Snackbar.make 是静态方法，第 1 轮用的
    // mockConstruction 拦不到它——真实 make() 在 Robolectric 下 inflate
    // design_layout_snackbar_include 时抛 InflateException（当前主题解不出
    // TextView 的 textAppearance 属性），6 个测试全在订阅时就收到该异常
    // 而终止。改用 mockStatic(Snackbar.class) 直接 stub 住 make() 返回
    // RETURNS_SELF 的 mock，彻底绕开 inflate；生产回调逻辑（addCallback /
    // setAction 的监听器）照常用 ArgumentCaptor 抓出来跑。 ----

    /** mockStatic(Snackbar.make) + RETURNS_SELF mock 的组合夹具。 */
    private static final class SnackbarFixture implements AutoCloseable {
        final MockedStatic<Snackbar> mockedStatic;
        final Snackbar snackbar;

        SnackbarFixture() {
            snackbar = mock(Snackbar.class, withSettings().defaultAnswer(RETURNS_SELF));
            mockedStatic = mockStatic(Snackbar.class);
            mockedStatic.when(() -> Snackbar.make(any(View.class),
                            any(CharSequence.class), eq(Snackbar.LENGTH_LONG)))
                    .thenReturn(snackbar);
        }

        @Override
        public void close() {
            mockedStatic.close();
        }
    }

    private static TestObserver<Integer> subscribeErrorWithMockedSnackbar(
            Context context, Throwable error) {
        CoordinatorLayout root = new CoordinatorLayout(context);
        TestObserver<Integer> to = Single.<Integer>error(error)
                .compose(RxUtils.wrapWithErrorSnackbar(context, root, "load failed"))
                .test();
        to.assertNotTerminated();
        return to;
    }

    @Test
    public void errorSnackbar_dismissSwipePropagatesError() {
        Context context = RuntimeEnvironment.getApplication();
        TestObserver<Integer> to;
        Snackbar.BaseCallback<Snackbar> callback;
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            to = subscribeErrorWithMockedSnackbar(context, new IOException("net down"));
            callback = captureCallbackFrom(fixture.snackbar);
        }
        // L197 SWIPE 分支：retryProcessor.onError，Single 传回原错误。
        callback.onDismissed(null, Snackbar.BaseCallback.DISMISS_EVENT_SWIPE);
        to.assertError(IOException.class);
    }

    @Test
    public void errorSnackbar_dismissTimeoutPropagatesError() {
        Context context = RuntimeEnvironment.getApplication();
        TestObserver<Integer> to;
        Snackbar.BaseCallback<Snackbar> callback;
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            to = subscribeErrorWithMockedSnackbar(context, new IOException("net down"));
            callback = captureCallbackFrom(fixture.snackbar);
        }
        // L197 TIMEOUT 分支。
        callback.onDismissed(null, Snackbar.BaseCallback.DISMISS_EVENT_TIMEOUT);
        to.assertError(IOException.class);
    }

    @Test
    public void errorSnackbar_dismissActionPropagatesError() {
        Context context = RuntimeEnvironment.getApplication();
        TestObserver<Integer> to;
        Snackbar.BaseCallback<Snackbar> callback;
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            to = subscribeErrorWithMockedSnackbar(context, new IOException("net down"));
            callback = captureCallbackFrom(fixture.snackbar);
        }
        // L197 ACTION 分支（点“复制”后 snackbar 以 ACTION 消失，同样传回错误）。
        callback.onDismissed(null, Snackbar.BaseCallback.DISMISS_EVENT_ACTION);
        to.assertError(IOException.class);
    }

    @Test
    public void errorSnackbar_dismissManualKeepsPending() {
        Context context = RuntimeEnvironment.getApplication();
        TestObserver<Integer> to;
        Snackbar.BaseCallback<Snackbar> callback;
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            to = subscribeErrorWithMockedSnackbar(context, new IOException("net down"));
            callback = captureCallbackFrom(fixture.snackbar);
        }
        // L197 条件全假：不触发 retryProcessor，Single 保持挂起。
        callback.onDismissed(null, Snackbar.BaseCallback.DISMISS_EVENT_MANUAL);
        to.assertNotTerminated();
    }

    private static Snackbar.BaseCallback<Snackbar> captureCallbackFrom(
            Snackbar mockSnackbar) {
        ArgumentCaptor<Snackbar.BaseCallback> captor =
                ArgumentCaptor.forClass(Snackbar.BaseCallback.class);
        verify(mockSnackbar).addCallback(captor.capture());
        return captor.getValue();
    }

    @Test
    public void errorSnackbar_apiErrorCopyActionCopiesMessage() {
        // L221：ApiRequestException 的“复制”动作把错误信息写进剪贴板。
        Context context = RuntimeEnvironment.getApplication();
        CoordinatorLayout root = new CoordinatorLayout(context);
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            subscribeErrorWithMockedSnackbar(context, apiError(404));
            Snackbar mockSnackbar = fixture.snackbar;
            ArgumentCaptor<View.OnClickListener> actionCaptor =
                    ArgumentCaptor.forClass(View.OnClickListener.class);
            verify(mockSnackbar).setAction(
                    eq(R.string.snackbar_copy_error_msg_action), actionCaptor.capture());
            actionCaptor.getValue().onClick(mock(View.class));
        }
        ClipboardManager cm = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("GitHub error", clip.getDescription().getLabel().toString());
        assertNotNull(clip.getItemAt(0).getText());
    }

    @Test
    public void errorSnackbar_otherErrorCopyActionCopiesStackTrace() {
        // L229：非网络/非 API 错误的“复制”动作把堆栈写进剪贴板。
        Context context = RuntimeEnvironment.getApplication();
        CoordinatorLayout root = new CoordinatorLayout(context);
        try (SnackbarFixture fixture = new SnackbarFixture()) {
            subscribeErrorWithMockedSnackbar(context, new RuntimeException("boom"));
            Snackbar mockSnackbar = fixture.snackbar;
            ArgumentCaptor<View.OnClickListener> actionCaptor =
                    ArgumentCaptor.forClass(View.OnClickListener.class);
            verify(mockSnackbar).setAction(
                    eq(R.string.snackbar_copy_error_log_action), actionCaptor.capture());
            actionCaptor.getValue().onClick(mock(View.class));
        }
        ClipboardManager cm = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("OctoDroid error", clip.getDescription().getLabel().toString());
        assertTrue(clip.getItemAt(0).getText().toString().contains("boom"));
    }

}
