package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * StoreRankGroups (F1, added 2026-11-10; 2026-11-22 动态化重写):
 * pure-JVM tests, no Android needed. build() only touches Context inside
 * backendCells (DiagTrace.log), and DiagTrace.log(null, ...) returns early,
 * so a null Context is safe here.
 *
 * 榜单结构不再是代码里的静态表：测试自带一个小结构（2 组 4 榜，含新榜
 * 「AI音视频工具」）传给 build()，验证动态结构能原样建榜、新榜无需
 * 改代码即可流通（96→98 的回归）。
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

    private static StoreRepo repoWithRanks(String fullName, int stars, List<String> ranks) {
        return repo(fullName, stars, null, ranks, null);
    }

    /** 测试用小结构：2 组 4 榜（AI 2、工具 2），含新榜「AI音视频工具」。 */
    private static List<StoreRankGroups.Group> structure() {
        StoreRankGroups.Group ai = new StoreRankGroups.Group("AI");
        ai.lists.add(new StoreRankGroups.RankList("AI Agent框架"));
        ai.lists.add(new StoreRankGroups.RankList("AI音视频工具"));
        StoreRankGroups.Group tools = new StoreRankGroups.Group("工具");
        tools.lists.add(new StoreRankGroups.RankList("代理/VPN客户端"));
        tools.lists.add(new StoreRankGroups.RankList("浏览器"));
        return Arrays.asList(ai, tools);
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
    public void totalListCount_structure_countsLists() {
        assertEquals(4, StoreRankGroups.totalListCount(structure(), null));
    }

    @Test
    public void totalListCount_nullStructure_fallsBackToDistinctRanks() {
        List<StoreRepo> pool = Arrays.asList(
                repoWithRanks("a/x", 10, Arrays.asList("榜一", "榜二")),
                repoWithRanks("b/x", 10, Arrays.asList("榜一")),
                repo("c/x", 10));
        assertEquals(2, StoreRankGroups.totalListCount(null, pool));
        assertEquals(0, StoreRankGroups.totalListCount(null, new ArrayList<>()));
        assertEquals(0, StoreRankGroups.totalListCount(null, null));
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
    public void build_nullStructure_returnsEmpty() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("2dust/v2rayN", 5000,
                                Arrays.asList("代理/VPN客户端"))),
                null);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void build_nullPool_returnsStructureSkeleton() {
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, null, structure());
        assertEquals(2, groups.size());
        assertEquals("AI", groups.get(0).name);
        assertEquals("工具", groups.get(1).name);
        assertEquals(2, groups.get(0).lists.size());
        assertEquals(2, groups.get(1).lists.size());
        assertEquals("AI Agent框架", groups.get(0).lists.get(0).name);
        assertEquals("浏览器", groups.get(1).lists.get(1).name);
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_emptyPool_returnsStructureSkeleton() {
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, new ArrayList<>(), structure());
        assertEquals(2, groups.size());
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_backendRanks_placesItem() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("2dust/v2rayN", 5000,
                                Arrays.asList("代理/VPN客户端"))),
                structure());
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 1, 0, "2dust/v2rayN"));
        assertEquals("代理/VPN客户端", listAt(groups, 1, 0).name);
    }

    @Test
    public void build_backendRanks_emptyList_meansNoLists() {
        // An empty ranks list is the backend confirming "no list",
        // not "unknown": the item goes nowhere.
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("2dust/v2rayN", 5000, new ArrayList<>())),
                structure());
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_backendRanks_unknownNameDropped() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("someone/x", 10,
                                Arrays.asList("不存在的榜", "AI Agent框架"))),
                structure());
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 0, 0, "someone/x"));
    }

    @Test
    public void build_backendRanks_multiCell() {
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("someone/x", 10,
                                Arrays.asList("浏览器", "AI Agent框架"))),
                structure());
        assertEquals(2, totalItems(groups));
        assertTrue(contains(groups, 1, 1, "someone/x"));
        assertTrue(contains(groups, 0, 0, "someone/x"));
    }

    @Test
    public void build_ranksNull_skipped_noLocalFallback() {
        // ranks 缺失（旧数据）无法归属：不再回落本地静态表与关键词规则。
        // 该仓库的 topic 与名字就算命中旧规则也不进任何榜。
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repo("someone/my-vpn", 10, Arrays.asList("vpn"), null, null)),
                structure());
        assertEquals(0, totalItems(groups));
    }

    @Test
    public void build_newBoard_flowsThroughWithoutCodeChange() {
        // 96→98 的回归：结构里多一个新榜名，后端 ranks 指向它即流通，
        // 不需要改 StoreRankGroups 的代码。
        List<StoreRankGroups.Group> groups = StoreRankGroups.build(null,
                Collections.singletonList(
                        repoWithRanks("Zackriya-Solutions/meetily", 10,
                                Arrays.asList("AI音视频工具"))),
                structure());
        assertEquals(1, totalItems(groups));
        assertTrue(contains(groups, 0, 1, "Zackriya-Solutions/meetily"));
    }

    @Test
    public void build_sorting_starsDescTieBreakByName() {
        List<StoreRepo> pool = Arrays.asList(
                repoWithRanks("zzz/x", 100, Arrays.asList("代理/VPN客户端")),
                repoWithRanks("aaa/x", 100, Arrays.asList("代理/VPN客户端")),
                repoWithRanks("mmm/x", 300, Arrays.asList("代理/VPN客户端")));
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, pool, structure());
        List<StoreRepo> items = listAt(groups, 1, 0).items;
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
                repoWithRanks("b/x", 100, Arrays.asList("代理/VPN客户端")),
                repoWithRanks(null, 100, Arrays.asList("代理/VPN客户端")));
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, pool, structure());
        List<StoreRepo> items = listAt(groups, 1, 0).items;
        assertEquals(2, items.size());
        assertEquals("", items.get(0).fullName());
        assertEquals("b/x", items.get(1).fullName());
    }

    @Test
    public void build_sorting_tieBreakIsCaseInsensitive() {
        List<StoreRepo> pool = Arrays.asList(
                repoWithRanks("Zebra/x", 50, Arrays.asList("代理/VPN客户端")),
                repoWithRanks("apple/x", 50, Arrays.asList("代理/VPN客户端")));
        List<StoreRankGroups.Group> groups =
                StoreRankGroups.build(null, pool, structure());
        List<StoreRepo> items = listAt(groups, 1, 0).items;
        assertEquals("apple/x", items.get(0).fullName());
        assertEquals("Zebra/x", items.get(1).fullName());
    }
}
