package com.gh4a.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gh4a.utils.Iso8601;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.RegistryMatcher;
import org.simpleframework.xml.transform.Transform;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

/**
 * GitHubFeedService: the Retrofit Atom-feed interface. Exercised against
 * a stubbed OkHttp interceptor (no real network); the entry-parsing cases
 * live in ModelServicesTest, this file covers the interface itself:
 * path handling, error responses and malformed payloads.
 */
@RunWith(RobolectricTestRunner.class)
public class GitHubFeedServiceTest {

    private static OkHttpClient stubClient(final int code, final String body,
            final String contentType) {
        Interceptor stub = chain -> new okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
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

    private static GitHubFeedService service(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl("https://github.com/")
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(SimpleXmlConverterFactory.create(feedPersister()))
                .build()
                .create(GitHubFeedService.class);
    }

    @Test
    public void getFeed_parsesSingleEntry() {
        String xml = "<feed><entry><id>9</id><title>Only</title>"
                + "<content>body text</content></entry></feed>";
        Response<GitHubFeed> response = service(
                stubClient(200, xml, "application/atom+xml"))
                .getFeed("octocat.atom").blockingGet();
        assertTrue(response.isSuccessful());
        assertEquals(1, response.body().feed.size());
        assertEquals("Only", response.body().feed.get(0).getTitle());
        assertEquals("body text", response.body().feed.get(0).getPreview());
    }

    @Test
    public void getFeed_nestedPath_keepsSlashEncoded() {
        // @Path(encoded = true): "a/b.atom" must reach the server as two
        // path segments, not as "a%2Fb.atom".
        AtomicReference<String> path = new AtomicReference<>();
        Interceptor capturing = chain -> {
            path.set(chain.request().url().encodedPath());
            return new okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(
                            MediaType.parse("application/atom+xml"), "<feed></feed>"))
                    .build();
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(capturing).build();
        try {
            service(client).getFeed("a/b.atom").blockingGet();
        } catch (RuntimeException ignored) {
            // Empty feed fails parsing; the path assertion below is the point.
        }
        assertEquals("/a/b.atom", path.get());
    }

    @Test
    public void getFeed_notFound_returnsUnsuccessfulResponse() {
        Response<GitHubFeed> response = service(
                stubClient(404, "Not Found", "text/plain"))
                .getFeed("missing.atom").blockingGet();
        assertFalse(response.isSuccessful());
        assertEquals(404, response.code());
    }

    @Test
    public void getFeed_malformedXml_fails() {
        try {
            service(stubClient(200, "<feed><entry><title>oops",
                    "application/atom+xml"))
                    .getFeed("bad.atom").blockingGet();
            org.junit.Assert.fail("expected RuntimeException for malformed XML");
        } catch (RuntimeException expected) {
            // RxJava wraps the SimpleXML parse failure.
        }
    }
}
