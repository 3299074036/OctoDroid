package com.gh4a.model;

import com.gh4a.utils.Iso8601;
import com.meisolsson.githubsdk.core.ServiceGenerator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.RegistryMatcher;
import org.simpleframework.xml.transform.Transform;

import java.util.Date;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The two Retrofit service interfaces: exercised end-to-end against a
 * stubbed OkHttp interceptor (no real network), so the annotations,
 * converters and model mapping are all covered.
 */
@RunWith(RobolectricTestRunner.class)
public class ModelServicesTest {

    private static OkHttpClient stubClient(final String body, final String contentType) {
        Interceptor stub = chain -> new okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse(contentType), body))
                .build();
        return new OkHttpClient.Builder().addInterceptor(stub).build();
    }

    private static Persister feedPersister() {
        RegistryMatcher matcher = new RegistryMatcher();
        matcher.bind(Date.class, new Transform<Date>() {
            @Override
            public Date read(String value) {
                return Iso8601.parseOrNull(value);
            }

            @Override
            public String write(Date value) {
                return Iso8601.formatFeed(value);
            }
        });
        return new Persister(matcher);
    }

    @Test
    public void gitHubFeedService_getFeed_parsesEntries() {
        String xml = "<feed>"
                + "<entry><id>1</id><title>First</title><content>hello</content></entry>"
                + "<entry><id>2</id><title>Second</title><content>world</content></entry>"
                + "</feed>";
        GitHubFeedService service = new Retrofit.Builder()
                .baseUrl("https://github.com/")
                .client(stubClient(xml, "application/atom+xml"))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(SimpleXmlConverterFactory.create(feedPersister()))
                .build()
                .create(GitHubFeedService.class);

        Response<GitHubFeed> response = service.getFeed("octocat.atom").blockingGet();
        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().feed.size());
        assertEquals("First", response.body().feed.get(0).getTitle());
        assertEquals("hello", response.body().feed.get(0).getPreview());
        assertEquals("Second", response.body().feed.get(1).getTitle());
    }

    @Test
    public void gitHubFeedService_getFeed_emptyFeed_failsParse() {
        // Same required-@ElementList behaviour as GitHubFeedTest: zero
        // entries fail the whole parse (RxJava wraps it in RuntimeException).
        GitHubFeedService service = new Retrofit.Builder()
                .baseUrl("https://github.com/")
                .client(stubClient("<feed></feed>", "application/atom+xml"))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(SimpleXmlConverterFactory.create(feedPersister()))
                .build()
                .create(GitHubFeedService.class);

        try {
            service.getFeed("empty.atom").blockingGet();
            org.junit.Assert.fail("expected RuntimeException for empty feed");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause()
                    instanceof org.simpleframework.xml.core.ValueRequiredException);
        }
    }

    @Test
    public void trendService_getTrends_parsesList() {
        String json = "[{\"owner\":\"octocat\",\"repo\":\"hello\",\"description\":\"d\","
                + "\"language\":\"Java\",\"stars\":10,\"new_stars\":5,\"forks\":2},"
                + "{\"owner\":\"a\",\"repo\":\"b\",\"description\":null,"
                + "\"language\":null,\"stars\":0,\"new_stars\":0,\"forks\":0}]";
        TrendService service = new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .client(stubClient(json, "application/json"))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(ServiceGenerator.moshi))
                .build()
                .create(TrendService.class);

        Response<List<Trend>> response = service.getTrends("daily").blockingGet();
        assertTrue(response.isSuccessful());
        List<Trend> trends = response.body();
        assertEquals(2, trends.size());
        assertEquals("octocat", trends.get(0).getRepoOwner());
        assertEquals("hello", trends.get(0).getRepoName());
        assertEquals("d", trends.get(0).getDescription());
        assertEquals("Java", trends.get(0).getLanguage());
        assertEquals(10, trends.get(0).getStars());
        assertEquals(5, trends.get(0).getNewStars());
        assertEquals(2, trends.get(0).getForks());
        assertEquals("a", trends.get(1).getRepoOwner());
        assertEquals(0, trends.get(1).getStars());
    }

    @Test
    public void trendService_getTrends_emptyList() {
        TrendService service = new Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .client(stubClient("[]", "application/json"))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(ServiceGenerator.moshi))
                .build()
                .create(TrendService.class);

        Response<List<Trend>> response = service.getTrends("weekly").blockingGet();
        assertTrue(response.isSuccessful());
        assertTrue(response.body().isEmpty());
    }
}
