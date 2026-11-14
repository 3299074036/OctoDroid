package com.gh4a.fragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** StoreRepo：商店一行数据（构造归一化、热门比较、fromJson，Robolectric 供 org.json）。 */
@RunWith(RobolectricTestRunner.class)
public class StoreRepoTest {

    private static StoreRepo repo(String fullName, Double trending, Double spd, int stars) {
        return new StoreRepo(fullName, "o", "n", "d", "Java", "http://x/a.png",
                stars, 3, 100L, Arrays.asList("ai"), new HashSet<>(Arrays.asList("ai")),
                "v1", 1000L, EnumSet.of(StoreClassifier.Platform.ANDROID), 1,
                trending, 1.5, spd, null, "ai");
    }

    @Test
    public void ctor_nullStringsNormalizeToEmpty() {
        StoreRepo r = new StoreRepo(null, null, null, null, null, null,
                1, 2, 3L, null, null, null, 0L, null, 0, null, null, null, null, null);
        assertEquals("", r.fullName);
        assertEquals("", r.owner);
        assertEquals("", r.name);
        assertEquals("", r.description);
        assertNull(r.language);
        assertEquals("", r.avatarUrl);
        assertEquals("", r.tag);
        assertTrue(r.topics.isEmpty());
        assertTrue(r.topicCodes.isEmpty());
        assertTrue(r.platforms.isEmpty());
        assertNull(r.ranks);
        assertNull(r.category);
        assertNull(r.trendingScore);
    }

    @Test
    public void ctor_keepsGivenValues() {
        StoreRepo r = repo("o/n", 9.5, 2.0, 42);
        assertEquals("o/n", r.fullName);
        assertEquals("o", r.owner);
        assertEquals("n", r.name);
        assertEquals("d", r.description);
        assertEquals("Java", r.language);
        assertEquals(42, r.stars);
        assertEquals(3, r.forks);
        assertEquals(100L, r.downloadCount);
        assertEquals(1000L, r.publishedAt);
        assertEquals(1, r.assetCount);
        assertEquals(Double.valueOf(9.5), r.trendingScore);
        assertEquals(Double.valueOf(1.5), r.popularityScore);
        assertEquals(Double.valueOf(2.0), r.starsPerDay);
        assertEquals("ai", r.category);
    }

    @Test
    public void categoryOrOther_returnsCategoryOrOther() {
        assertEquals("ai", repo("o/n", null, null, 1).categoryOrOther());
        StoreRepo noCat = new StoreRepo("o/n", "o", "n", "", null, "", 0, 0, 0L,
                null, null, "", 0L, null, 0, null, null, null, null, null);
        assertEquals("other", noCat.categoryOrOther());
        StoreRepo emptyCat = new StoreRepo("o/n", "o", "n", "", null, "", 0, 0, 0L,
                null, null, "", 0L, null, 0, null, null, null, null, "");
        assertEquals("other", emptyCat.categoryOrOther());
    }

    @Test
    public void hasAssets_dependsOnPlatforms() {
        assertTrue(repo("o/n", null, null, 1).hasAssets());
        StoreRepo noPlatforms = new StoreRepo("o/n", "o", "n", "", null, "", 0, 0, 0L,
                null, null, "", 0L, EnumSet.noneOf(StoreClassifier.Platform.class),
                0, null, null, null, null, null);
        assertFalse(noPlatforms.hasAssets());
    }

    @Test
    public void accessors_fullNameOwnerNameStarsVersion() {
        StoreRepo r = repo("o/n", null, null, 7);
        assertEquals("o/n", r.fullName());
        assertEquals("o", r.owner());
        assertEquals("n", r.name());
        assertEquals(7, r.stars());
        assertEquals("v1", r.version());
    }

    @Test
    public void compareHot_bothTrendingOrdersByScoreDesc() {
        assertTrue(StoreRepo.compareHot(repo("a", 9.0, null, 1), repo("b", 5.0, null, 100)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", 5.0, null, 100), repo("b", 9.0, null, 1)) > 0);
        assertEquals(0, StoreRepo.compareHot(repo("a", 5.0, null, 10), repo("b", 5.0, null, 10)));
    }

    @Test
    public void compareHot_trendingTiesBreakByStars() {
        assertTrue(StoreRepo.compareHot(repo("a", 5.0, null, 50), repo("b", 5.0, null, 10)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", 5.0, null, 10), repo("b", 5.0, null, 50)) > 0);
    }

    @Test
    public void compareHot_trendingAlwaysBeforeNonTrending() {
        assertTrue(StoreRepo.compareHot(repo("a", 0.1, 99.0, 1), repo("b", null, 99.0, 100)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", null, 99.0, 100), repo("b", 0.1, 99.0, 1)) > 0);
    }

    @Test
    public void compareHot_bothSpdOrdersBySpdDescThenStars() {
        assertTrue(StoreRepo.compareHot(repo("a", null, 3.0, 1), repo("b", null, 1.0, 100)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", null, 1.0, 100), repo("b", null, 3.0, 1)) > 0);
        assertTrue(StoreRepo.compareHot(repo("a", null, 2.0, 50), repo("b", null, 2.0, 10)) < 0);
        assertEquals(0, StoreRepo.compareHot(repo("a", null, 2.0, 10), repo("b", null, 2.0, 10)));
    }

    @Test
    public void compareHot_spdBeforeNeither() {
        assertTrue(StoreRepo.compareHot(repo("a", null, 0.5, 1), repo("b", null, null, 100)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", null, null, 100), repo("b", null, 0.5, 1)) > 0);
    }

    @Test
    public void compareHot_neitherFallsBackToStars() {
        assertTrue(StoreRepo.compareHot(repo("a", null, null, 100), repo("b", null, null, 10)) < 0);
        assertTrue(StoreRepo.compareHot(repo("a", null, null, 10), repo("b", null, null, 100)) > 0);
        assertEquals(0, StoreRepo.compareHot(repo("a", null, null, 10), repo("b", null, null, 10)));
    }

    @Test
    public void fromJson_fullObject() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "octo/cat")
                .put("owner", "octo")
                .put("name", "cat")
                .put("description", "d")
                .put("language", "Kotlin")
                .put("avatarUrl", "http://x/a.png")
                .put("stars", 42)
                .put("forks", 7)
                .put("downloadCount", 1234)
                .put("topics", new JSONArray(Arrays.asList("ai", "vpn")))
                .put("topicCodes", new JSONArray(Arrays.asList("ai", "privacy")))
                .put("latestTag", "v3.0")
                .put("latestDate", "2026-01-02T03:04:05Z")
                .put("platforms", new JSONArray(Arrays.asList("android", "ios", "bogus")))
                .put("assetCount", 3)
                .put("trendingScore", 8.5)
                .put("popularityScore", 7.25)
                .put("stars_per_day", 1.75)
                .put("ranks", new JSONArray(Arrays.asList("热门榜", "新锐榜")))
                .put("category", "ai");
        StoreRepo r = StoreRepo.fromJson(obj);
        assertEquals("octo/cat", r.fullName);
        assertEquals("octo", r.owner);
        assertEquals("cat", r.name);
        assertEquals("d", r.description);
        assertEquals("Kotlin", r.language);
        assertEquals(42, r.stars);
        assertEquals(7, r.forks);
        assertEquals(1234L, r.downloadCount);
        assertEquals(Arrays.asList("ai", "vpn"), r.topics);
        assertEquals(new HashSet<>(Arrays.asList("ai", "privacy")), r.topicCodes);
        assertEquals("v3.0", r.version());
        assertTrue(r.publishedAt > 0);
        assertEquals(EnumSet.of(StoreClassifier.Platform.ANDROID,
                StoreClassifier.Platform.IOS), r.platforms);
        assertEquals(3, r.assetCount);
        assertEquals(Double.valueOf(8.5), r.trendingScore);
        assertEquals(Double.valueOf(7.25), r.popularityScore);
        assertEquals(Double.valueOf(1.75), r.starsPerDay);
        assertEquals(Arrays.asList("热门榜", "新锐榜"), r.ranks);
        assertEquals("ai", r.category);
        assertTrue(r.hasAssets());
    }

    @Test
    public void fromJson_minimalObjectDerivesNameAndOwner() throws Exception {
        JSONObject obj = new JSONObject().put("fullName", "octo/cat");
        StoreRepo r = StoreRepo.fromJson(obj);
        assertEquals("octo/cat", r.fullName);
        assertEquals("octo", r.owner);
        assertEquals("cat", r.name);
        assertEquals("", r.description);
        assertNull(r.language);
        assertEquals("", r.version());
        assertEquals(0, r.stars);
        assertEquals(0L, r.publishedAt);
        assertTrue(r.platforms.isEmpty());
        assertNull(r.trendingScore);
        assertNull(r.popularityScore);
        assertNull(r.starsPerDay);
        assertNull(r.ranks);
        assertNull(r.category);
        assertEquals("other", r.categoryOrOther());
        assertFalse(r.hasAssets());
    }

    @Test
    public void fromJson_nameWithoutSlashStaysEmpty() throws Exception {
        JSONObject obj = new JSONObject().put("fullName", "lonelyname");
        StoreRepo r = StoreRepo.fromJson(obj);
        assertEquals("", r.name);
        assertEquals("", r.owner);
    }

    @Test
    public void fromJson_emptyRanksIsEmptyListNotNull() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n")
                .put("ranks", new JSONArray());
        assertEquals(Collections.emptyList(), StoreRepo.fromJson(obj).ranks);
    }

    @Test
    public void fromJson_emptyCategoryBecomesNull() throws Exception {
        JSONObject obj = new JSONObject().put("fullName", "o/n").put("category", "");
        assertNull(StoreRepo.fromJson(obj).category);
    }

    @Test
    public void fromJson_dateFallbackChain() throws Exception {
        JSONObject onlyPushed = new JSONObject()
                .put("fullName", "o/n").put("pushedAt", "2026-01-02T03:04:05Z");
        assertTrue(StoreRepo.fromJson(onlyPushed).publishedAt > 0);
        JSONObject lastRelease = new JSONObject()
                .put("fullName", "o/n")
                .put("lastReleaseTime", "2026-01-03T00:00:00Z")
                .put("pushedAt", "2020-01-01T00:00:00Z");
        JSONObject latestDate = new JSONObject()
                .put("fullName", "o/n")
                .put("latestDate", "2026-01-04T00:00:00Z")
                .put("lastReleaseTime", "2026-01-03T00:00:00Z");
        long byPushed = StoreRepo.fromJson(onlyPushed).publishedAt;
        long byLastRelease = StoreRepo.fromJson(lastRelease).publishedAt;
        long byLatest = StoreRepo.fromJson(latestDate).publishedAt;
        assertTrue(byLastRelease > byPushed);
        assertTrue(byLatest > byLastRelease);
        JSONObject noDate = new JSONObject().put("fullName", "o/n");
        assertEquals(0L, StoreRepo.fromJson(noDate).publishedAt);
    }

    @Test
    public void fromJson_dateOnlyValueParses() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n").put("pushedAt", "2026-01-02");
        assertTrue(StoreRepo.fromJson(obj).publishedAt > 0);
    }

    @Test
    public void fromJson_nonNumericScoreFieldsBecomeNull() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n")
                .put("trendingScore", "high")
                .put("popularityScore", JSONObject.NULL)
                .put("stars_per_day", 2);
        StoreRepo r = StoreRepo.fromJson(obj);
        assertNull(r.trendingScore);
        assertNull(r.popularityScore);
        assertEquals(Double.valueOf(2.0), r.starsPerDay);
    }

    @Test
    public void fromJson_allPlatformKeysMapped() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n")
                .put("platforms", new JSONArray(Arrays.asList(
                        "android", "ios", "windows", "macos", "linux",
                        "harmony", "extension", "archive", "unknown")));
        assertEquals(EnumSet.allOf(StoreClassifier.Platform.class),
                StoreRepo.fromJson(obj).platforms);
    }

    @Test
    public void fromJson_ranksSkipsEmptyStrings() throws Exception {
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n")
                .put("ranks", new JSONArray(Arrays.asList("榜一", "", "榜二")));
        assertEquals(Arrays.asList("榜一", "榜二"), StoreRepo.fromJson(obj).ranks);
    }

    @Test
    public void fromJson_emptyObjectProducesDefaults() throws Exception {
        StoreRepo r = StoreRepo.fromJson(new JSONObject());
        assertEquals("", r.fullName);
        assertEquals("", r.owner);
        assertEquals("", r.name);
        assertEquals(0, r.stars);
        assertTrue(r.topics.isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void fromJson_nullObjectThrows() throws Exception {
        StoreRepo.fromJson(null);
    }

    @Test
    public void fromJson_topicsPreserveEmptyStrings() throws Exception {
        // topics 数组原样保留（含空串），与 topicCodes 的去重逻辑不同。
        JSONObject obj = new JSONObject()
                .put("fullName", "o/n")
                .put("topics", new JSONArray(Arrays.asList("ai", "")));
        List<String> topics = StoreRepo.fromJson(obj).topics;
        assertEquals(Arrays.asList("ai", ""), topics);
    }
}
