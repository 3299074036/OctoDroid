package com.gh4a.fragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.util.List;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.json.JSONObject;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/** RadarGraphQL：GraphQL 解析（反射测私有 parse/parseTime）+ fetch 懒加载。 */
@RunWith(RobolectricTestRunner.class)
public class RadarGraphQLTest {

    @SuppressWarnings("unchecked")
    private static List<ReleaseRadarFragment.RadarItem> parse(String json) throws Exception {
        Method m = RadarGraphQL.class.getDeclaredMethod("parse", String.class);
        m.setAccessible(true);
        return (List<ReleaseRadarFragment.RadarItem>) m.invoke(null, json);
    }

    private static long parseTime(String iso) throws Exception {
        Method m = RadarGraphQL.class.getDeclaredMethod("parseTime", String.class);
        m.setAccessible(true);
        return (Long) m.invoke(null, iso);
    }

    private static String itemJson(String owner, String repo, long id, String tag,
            String publishedAt, String createdAt, boolean draft) {
        return "{\"nameWithOwner\":\"" + owner + "/" + repo + "\",\"releases\":{\"nodes\":["
                + "{\"databaseId\":" + id + ",\"tagName\":\"" + tag + "\",\"name\":\"" + tag + "\","
                + "\"publishedAt\":" + (publishedAt == null ? "null" : "\"" + publishedAt + "\"")
                + ",\"createdAt\":" + (createdAt == null ? "null" : "\"" + createdAt + "\"")
                + ",\"isDraft\":" + draft + "}]}}";
    }

    private static String envelope(String nodesJson) {
        return "{\"data\":{\"user\":{\"starredRepositories\":{\"nodes\":[" + nodesJson + "]}}}}";
    }

    @Test
    public void parse_singleValidRelease() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                itemJson("octo", "cat", 11, "v1.0", "2026-01-02T03:04:05Z",
                        "2026-01-01T00:00:00Z", false)));
        assertEquals(1, items.size());
        assertEquals("octo", items.get(0).owner);
        assertEquals("cat", items.get(0).repo);
        assertEquals(11L, items.get(0).releaseId);
        assertEquals("v1.0", items.get(0).tagName);
        assertEquals("v1.0", items.get(0).releaseName);
        assertTrue(items.get(0).publishedAt > 0);
        assertTrue(items.get(0).createdAt > 0);
        assertTrue(items.get(0).publishedAt > items.get(0).createdAt);
    }

    @Test
    public void parse_draftIsSkippedAndNextReleaseTaken() throws Exception {
        String repoJson = "{\"nameWithOwner\":\"o/r\",\"releases\":{\"nodes\":["
                + "{\"databaseId\":1,\"tagName\":\"draft\",\"name\":\"d\","
                + "\"publishedAt\":\"2026-01-05T00:00:00Z\",\"createdAt\":\"2026-01-05T00:00:00Z\","
                + "\"isDraft\":true},"
                + "{\"databaseId\":2,\"tagName\":\"v2\",\"name\":\"v2\","
                + "\"publishedAt\":\"2026-01-04T00:00:00Z\",\"createdAt\":\"2026-01-04T00:00:00Z\","
                + "\"isDraft\":false}]}}";
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(repoJson));
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).releaseId);
        assertEquals("v2", items.get(0).tagName);
    }

    @Test
    public void parse_draftOnlyRepoProducesNoItem() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                itemJson("o", "r", 1, "d", "2026-01-05T00:00:00Z", "2026-01-05T00:00:00Z", true)));
        assertTrue(items.isEmpty());
    }

    @Test
    public void parse_sortedNewestFirstByPublishedThenCreated() throws Exception {
        String nodes = itemJson("o", "old", 1, "v1", "2026-01-01T00:00:00Z",
                "2026-01-01T00:00:00Z", false)
                + "," + itemJson("o", "new", 2, "v2", "2026-01-03T00:00:00Z",
                "2026-01-03T00:00:00Z", false)
                + "," + itemJson("o", "nodate", 3, "v3", null, "2026-01-02T00:00:00Z", false);
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(nodes));
        assertEquals(3, items.size());
        assertEquals("new", items.get(0).repo);
        assertEquals("nodate", items.get(1).repo); // 无 publishedAt 时用 createdAt 排序
        assertEquals("old", items.get(2).repo);
    }

    @Test
    public void parse_errorsArrayThrows() {
        try {
            parse("{\"errors\":[{\"message\":\"NOT_FOUND\"}]}");
            fail("expected RuntimeException");
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException
                    ? e.getCause() : e;
            assertTrue(cause instanceof RuntimeException);
            assertTrue(cause.getMessage().contains("NOT_FOUND"));
        }
    }

    @Test
    public void parse_emptyErrorsArrayIsIgnored() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items =
                parse("{\"errors\":[],\"data\":{\"user\":null}}");
        assertTrue(items.isEmpty());
    }

    @Test
    public void parse_missingDataOrUserOrStarredReturnsEmpty() throws Exception {
        assertTrue(parse("{}").isEmpty());
        assertTrue(parse("{\"data\":{}}").isEmpty());
        assertTrue(parse("{\"data\":{\"user\":{}}}").isEmpty());
        assertTrue(parse("{\"data\":{\"user\":{\"starredRepositories\":{}}}}").isEmpty());
        assertTrue(parse("{\"data\":{\"user\":{\"starredRepositories\":{\"nodes\":[]}}}}").isEmpty());
    }

    @Test
    public void parse_repoWithoutSlashSkipped() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                itemJson("noslash", "", 1, "v1", "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z", false).replace("noslash/", "noslash")));
        assertTrue(items.isEmpty());
    }

    @Test
    public void parse_repoWithoutReleasesSkipped() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                "{\"nameWithOwner\":\"o/r\"}"));
        assertTrue(items.isEmpty());
        List<ReleaseRadarFragment.RadarItem> nullNodes = parse(envelope(
                "{\"nameWithOwner\":\"o/r\",\"releases\":{\"nodes\":null}}"));
        assertTrue(nullNodes.isEmpty());
    }

    @Test
    public void parse_nullReleaseNodeSkipped() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                "{\"nameWithOwner\":\"o/r\",\"releases\":{\"nodes\":[null]}}"));
        assertTrue(items.isEmpty());
    }

    @Test
    public void parse_missingDatesBecomeZero() throws Exception {
        List<ReleaseRadarFragment.RadarItem> items = parse(envelope(
                itemJson("o", "r", 1, "v1", null, null, false)));
        assertEquals(1, items.size());
        assertEquals(0L, items.get(0).publishedAt);
        assertEquals(0L, items.get(0).createdAt);
    }

    @Test
    public void parseTime_nullEmptyInvalidReturnZero() throws Exception {
        assertEquals(0L, parseTime(null));
        assertEquals(0L, parseTime(""));
        assertEquals(0L, parseTime("not-a-date"));
    }

    @Test
    public void parseTime_validIsoReturnsMillis() throws Exception {
        assertTrue(parseTime("2026-01-02T03:04:05Z") > 0);
        assertTrue(parseTime("2026-01-02T03:04:05.123Z") > 0);
        assertTrue(parseTime("2026-01-02") > 0);
    }

    @Test
    public void unauthorizedException_keepsMessage() {
        RadarGraphQL.UnauthorizedException e =
                new RadarGraphQL.UnauthorizedException("bad token");
        assertEquals("bad token", e.getMessage());
        assertTrue(e instanceof GraphQLClient.UnauthorizedException);
    }

    @Test
    public void fetch_returnsLazySingleWithoutNetwork() {
        // Single.fromCallable 是懒的：只建不订阅，不发真实请求。
        Single<List<ReleaseRadarFragment.RadarItem>> single = RadarGraphQL.fetch("alice");
        assertNotNull(single);
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<RadarGraphQL> c = RadarGraphQL.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }


    @Test
    public void fetch_delegatesToGraphQLClientAndParsesResponse() throws Exception {
        try (MockedStatic<GraphQLClient> mocked = mockStatic(GraphQLClient.class)) {
            mocked.when(() -> GraphQLClient.execute(anyString(), any(JSONObject.class),
                    any(GraphQLClient.CallHandle.class)))
                    .thenReturn("{\"data\":{\"user\":{\"starredRepositories\":{\"nodes\":[]}}}}");
            List<ReleaseRadarFragment.RadarItem> items =
                    RadarGraphQL.fetch("alice").blockingGet();
            assertNotNull(items);
            assertTrue(items.isEmpty());
        }
    }

    @Test
    public void parse_errorsWithNonObjectFirstElementThrowsEmptyMessage() throws Exception {
        try {
            parse("{\"errors\":[\"oops\"]}");
            fail("expected RuntimeException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertEquals("GraphQL: ", e.getCause().getMessage());
        }
    }

    @Test
    public void parse_emptyErrorsArrayWithoutDataReturnsEmpty() throws Exception {
        assertTrue(parse("{\"errors\":[]}").isEmpty());
    }

    @Test
    public void parse_nullRepoElementIsSkipped() throws Exception {
        String json = envelope("null," + itemJson("o", "r", 1L, "v1",
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", false));
        List<ReleaseRadarFragment.RadarItem> items = parse(json);
        assertEquals(1, items.size());
        assertEquals("o", items.get(0).owner);
        assertEquals("r", items.get(0).repo);
    }

    @Test
    public void parse_moreThanMaxReposCapsAtThirty() throws Exception {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            if (i > 0) {
                nodes.append(',');
            }
            nodes.append(itemJson("o", "r" + i, i, "v" + i,
                    "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", false));
        }
        assertEquals(30, parse(envelope(nodes.toString())).size());
    }

}
