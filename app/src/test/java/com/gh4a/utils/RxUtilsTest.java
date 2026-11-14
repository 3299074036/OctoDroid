package com.gh4a.utils;

import com.gh4a.ApiRequestException;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.SearchPage;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * B 组试点：RxUtils。纯 Single 变换走纯 JVM 测试（不加 Robolectric）。
 * doInBackground / wrapForBackgroundTask / wrapWithProgressDialog /
 * wrapWithErrorSnackbar 需 Activity、主线程调度与 Snackbar，
 * 待 Mockito（Activity mock）后补。
 */
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
}
