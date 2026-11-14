package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * StoreRankGroups (F1, added 2026-11-10): pure-JVM tests, no Android needed.
 * build() only touches Context inside backendCells (DiagTrace.log), and
 * DiagTrace.log(null, ...) returns early, so a null Context is safe here.
 */
public class StoreRankGroupsTest {

    /** Minimal repo: only fullName/stars/topics/ranks/description matter to build(). */
    private static StoreRepo repo(String fullName, int stars,
            List<String> topics, List<String> ranks, String description) {
        String owner = "";
        String name = "";
        if (fullName != null) {
            int slash = fullName.indexOf('/');
            if (slash >= 0) {
                owner = fullName.substring(0, slash);
                name = fullName.substring(slash + 1);
            } else {
                name = fullName;
            }
        }
        return new StoreRepo(fullName, owner, name,
                description != null ? description : "", "Java", "",
                stars, 0, 0L, topics, null, "", 0L, null, 0,
                null, null, null, ranks, null);
    }

    private static StoreRepo repo(String fullName, int stars) {
        return repo(fullName, stars, null, null, null);
    }

    private static StoreRepo repo(String fullName, int stars, List<String> topics) {
        return repo(fullName, stars, topics, null, null);
    }

    private static StoreRepo repoWithRanks(String fullName, int stars, List<String> ranks) {
        return repo(fullName, stars, null, ranks, null);
    }

    private static StoreRankGroups.RankList listAt(
            List<StoreRankGroups.Group> groups, int gi, int li) {
        return groups.get(gi).lists.get(li);
    }

    private static boolean contains(
            List<StoreRankGroups.Group> groups, int gi, int li, String fullName) {
        for (StoreRepo r : listAt(groups, gi, li).items) {
            if (fullName.equals(r.fullName())) {
                return true;
            }
        }
        return false;
    }

    private static int totalItems(List<StoreRankGroups.Group> groups) {
        int n = 0;
        for (StoreRankGroups.Group g : groups) {
            for (StoreRankGroups.RankList l : g.lists) {
                n += l.items.size();
            }
        }
        return n;
    }

    @Test
    public void totalListCount_is96() {
        assertEquals(96, StoreRankGroups.totalListCount());
    }

    @Test
    public void rankList_constructor_storesNameAndEmptyItems() {
        StoreRankGroups.RankList list = new StoreRankGroups.RankList("AI Agent框架");
        assertEquals("AI Agent框架", list.name);
        assertTrue(list.items.isEmpty());
    }

    @Test
    public void group_constructor_storesNameAndEmptyLists() {
        StoreRankGroups.Group group = new StoreRankGroups.Group("AI");
        assertEquals("AI", group.name);
        assertTrue(group.lists.isEmpty());
    }

    @Test
    public void build_nullPool_returnsSevenEmptyGroups() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null, null);
        assertEquals(7, groups.size());
        assertEquals("AI", groups.get(0).name);
        assertEquals("媒体", groups.get(1).name);
        assertEquals("网络", groups.get(2).name);
        assertEquals("工具", groups.get(3).name);
        assertEquals("效率", groups.get(4).name);
        assertEquals("开发", groups.get(5).name);
        assertEquals("游戏", groups.get(6).name);
        // Per-group list counts pin the 96-list structure.
        assertEquals(7, groups.get(0).lists.size());
        assertEquals(8, groups.get(1).lists.size());
        assertEquals(7, groups.get(2).lists.size());
        assertEquals(44, groups.get(3).lists.size());
        assertEquals(7, groups.get(4).lists.size());
        assertEquals(17, groups.get(5).lists.size());
        assertEquals(6, groups.get(6).lists.size());
        assertEquals("AI Agent框架", groups.get(0).lists.get(0).name);
        assertEquals("游戏引擎", groups.get(6).lists.get(5).name);
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_emptyPool_returnsEmptyGroups() {
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, new ArrayList<>());
        assertEquals(7, groups.size());
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_staticAssignment_singleCell() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(repo("2dust/v2rayN", 5000)));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 2, 0, "2dust/v2rayN"));
        assertEquals("代理/VPN客户端", listAt(groups, 2, 0).name);
    }

    @Test
    public void build_staticAssignment_multiCell() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(repo("langchain-ai/langchain", 100000)));
        assertEquals(3, totalItems(groups));
        assertTrue(contains(groups, 0, 0, "langchain-ai/langchain"));
        assertTrue(contains(groups, 0, 2, "langchain-ai/langchain"));
        assertTrue(contains(groups, 5, 6, "langchain-ai/langchain"));
    }

    @Test
    public void build_staticAssignment_beatsMatchingRule() {
        // immich-app/immich is statically assigned to 1:5, but its topic
        // "music" would also match the 1:1 rule: the static table wins.
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("immich-app/immich", 80000, Arrays.asList("music"))));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 1, 5, "immich-app/immich"));
        assertEquals(false, contains(groups, 1, 1, "immich-app/immich"));
    }

    @Test
    public void build_rule_topicHit() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/my-server", 10, Arrays.asList("mcp"))));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 0, 6, "someone/my-server"));
    }

    @Test
    public void build_rule_topicHit_caseInsensitiveAndNullTopicSkipped() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/my-vpn", 10, Arrays.asList(null, "VPN"))));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 2, 0, "someone/my-vpn"));
    }

    @Test
    public void build_rule_nameSubstringHit() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(repo("someone/awesome-chatbot", 10)));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 0, 1, "someone/awesome-chatbot"));
    }

    @Test
    public void build_rule_multiCell() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/kb", 10, Arrays.asList("knowledge-base"))));
        assertEquals(2, totalItems(groups));
        assertTrue(contains(groups, 0, 3, "someone/kb"));
        assertTrue(contains(groups, 4, 6, "someone/kb"));
    }

    @Test
    public void build_rule_noMatch_goesNowhere() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/boring-lib", 10, Arrays.asList("unrelated"))));
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_rule_descriptionIsNotMatched() {
        // The description mentions "vpn" but name and topics do not:
        // descriptions no longer participate in matching.
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/plain-lib", 10, null, null,
                                "best vpn client ever")));
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_backendRanks_useBackendNames() {
        // Backend ranks override the static table: v2rayN is statically
        // 2:0, but the backend says it belongs to "游戏" (6:2).
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("2dust/v2rayN", 5000,
                                Arrays.asList("游戏"))));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 6, 2, "2dust/v2rayN"));
        assertEquals(false, contains(groups, 2, 0, "2dust/v2rayN"));
    }

    @Test
    public void build_backendRanks_emptyList_meansNoLists() {
        // An empty ranks list is the backend confirming "no list",
        // not "unknown": the static assignment must not apply.
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("2dust/v2rayN", 5000, new ArrayList<>())));
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_backendRanks_unknownNameDropped() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("someone/x", 10,
                                Arrays.asList("不存在的榜", "AI Agent框架"))));
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 0, 0, "someone/x"));
    }

    @Test
    public void build_backendRanks_multiCell() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("someone/x", 10,
                                Arrays.asList("视频播放器", "AI Agent框架"))));
        assertEquals(2, totalItems(groups));
        assertTrue(contains(groups, 1, 0, "someone/x"));
        assertTrue(contains(groups, 0, 0, "someone/x"));
    }

    @Test
    public void build_sorting_starsDescTieBreakByName() {
        List<StoreRepo> pool = Arrays.asList(
                repo("zzz/x", 100, Arrays.asList("vpn")),
                repo("aaa/x", 100, Arrays.asList("vpn")),
                repo("mmm/x", 300, Arrays.asList("vpn")));
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null, pool);
        List<StoreRepo> items = listAt(groups, 2, 0).items;
        assertEquals(3, items.size());
        assertEquals("mmm/x", items.get(0).fullName());
        assertEquals("aaa/x", items.get(1).fullName());
        assertEquals("zzz/x", items.get(2).fullName());
    }

    @Test
    public void build_sorting_nullFullNameSortsAsEmpty() {
        // StoreRepo coerces a null fullName to "": on a stars tie it sorts
        // before any non-empty name.
        List<StoreRepo> pool = Arrays.asList(
                repo("b/x", 100, Arrays.asList("vpn")),
                repo(null, 100, Arrays.asList("vpn")));
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null, pool);
        List<StoreRepo> items = listAt(groups, 2, 0).items;
        assertEquals(2, items.size());
        assertEquals("", items.get(0).fullName());
        assertEquals("b/x", items.get(1).fullName());
    }

    @Test
    public void build_sorting_tieBreakIsCaseInsensitive() {
        List<StoreRepo> pool = Arrays.asList(
                repo("Zebra/x", 50, Arrays.asList("vpn")),
                repo("apple/x", 50, Arrays.asList("vpn")));
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null, pool);
        List<StoreRepo> items = listAt(groups, 2, 0).items;
        assertEquals("apple/x", items.get(0).fullName());
        assertEquals("Zebra/x", items.get(1).fullName());
    }
}
