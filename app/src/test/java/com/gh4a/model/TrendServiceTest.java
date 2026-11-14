package com.gh4a.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.meisolsson.githubsdk.core.ServiceGenerator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

/**
 * TrendService: the Retrofit interface behind the trending page
 * (static JSON, no api.github.com endpoint). Exercised against a stubbed
 * OkHttp interceptor (no real network); the list-parsing cases live in
 * ModelServicesTest, this file covers the interface itself: path
 * substitution, error responses and malformed payloads.
 */
@RunWith(RobolectricTestRunner.class)
public class TrendServiceTest {

    private static OkHttpClient stubClient(final int code, final String body) {
        Interceptor stub = chain -> new okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build();
        return new OkHttpClient.Builder().addInterceptor(stub).build();
    }

    private static TrendService service(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/Unpublished/GithubTrending/")
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(ServiceGenerator.moshi))
                .build()
                .create(TrendService.class);
    }

    @Test
    public void getTrends_monthly_substitutesTypeInPath() {
        AtomicReference<String> path = new AtomicReference<>();
        Interceptor capturing = chain -> {
            path.set(chain.request().url().encodedPath());
            return new okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(
                            MediaType.parse("application/json"), "[]"))
                    .build();
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(capturing).build();
        Response<List<Trend>> response =
                service(client).getTrends("monthly").blockingGet();
        assertTrue(response.isSuccessful());
        assertEquals("/Unpublished/GithubTrending/trends/trending_monthly-all.json",
                path.get());
    }

    @Test
    public void getTrends_daily_parsesOneEntry() {
        String json = "[{\"owner\":\"octocat\",\"repo\":\"hello\",\"description\":\"d\","
                + "\"language\":\"Kotlin\",\"stars\":42,\"new_stars\":7,\"forks\":3}]";
        Response<List<Trend>> response =
                service(stubClient(200, json)).getTrends("daily").blockingGet();
        assertTrue(response.isSuccessful());
        assertEquals(1, response.body().size());
        assertEquals("octocat", response.body().get(0).getRepoOwner());
        assertEquals("hello", response.body().get(0).getRepoName());
        assertEquals("Kotlin", response.body().get(0).getLanguage());
        assertEquals(42, response.body().get(0).getStars());
        assertEquals(7, response.body().get(0).getNewStars());
        assertEquals(3, response.body().get(0).getForks());
    }

    @Test
    public void getTrends_notFound_returnsUnsuccessfulResponse() {
        Response<List<Trend>> response =
                service(stubClient(404, "Not Found")).getTrends("daily").blockingGet();
        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
    }

    @Test
    public void getTrends_malformedJson_fails() {
        try {
            service(stubClient(200, "[{not json")).getTrends("daily").blockingGet();
            org.junit.Assert.fail("expected RuntimeException for malformed JSON");
        } catch (RuntimeException expected) {
            // RxJava wraps the Moshi parse failure.
        }
    }
}
