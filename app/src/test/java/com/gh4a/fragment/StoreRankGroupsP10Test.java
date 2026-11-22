package com.gh4a.fragment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P10（第三轮报告 §6；2026-11-22 动态化重写）：StoreRankGroups.build()
 * 在主线程的耗时。旧版测的是 ranks 为 null 的旧缓存池走本地关键词规则
 * （逐条正则 split）的耗时；静态表与规则已随 96 榜删除，旧数据现在
 * 直接跳过、不再匹配，本文件改测新稳态路径——带后端 ranks 的整池
 * （2457 条）在 98 榜结构（程序生成，非写死榜名）上的 build() 计时。
 *
 * 纯 JVM 单测（不走 Robolectric）：build() 只在 ranks != null 时碰
 * Context（backendCells 里的 DiagTrace.log），且 DiagTrace.log(null, ...)
 * 直接返回，Context 传 null 安全。
 *
 * 判定线（任务书）：单次 build 超 100ms 即“卡”→ 修；否则结案不改代码。
 * 结构不断言耗时（机器差异大）；结论由打印的数字定夺，见汇报。
 */
public class StoreRankGroupsP10Test {

    /** 报告口径的旧缓存整池大小。 */
    private static final int POOL_SIZE = 2457;

    /** 结构规模：7 组 × 14 榜 = 98 榜（与生产规模同量级，名字程序生成）。 */
    private static final int GROUP_COUNT = 7;
    private static final int LISTS_PER_GROUP = 14;

    /** 程序生成 98 榜结构：组名「组0」…，榜名「榜0」…「榜97」。 */
    private static List<StoreRankGroups.Group> structure98() {
        List<StoreRankGroups.Group> groups = new ArrayList<>(GROUP_COUNT);
        int n = 0;
        for (int gi = 0; gi < GROUP_COUNT; gi++) {
            StoreRankGroups.Group g =
                    new StoreRankGroups.Group("组" + gi);
            for (int li = 0; li < LISTS_PER_GROUP; li++) {
                g.lists.add(new StoreRankGroups.RankList("榜" + (n++)));
            }
            groups.add(g);
        }
        return groups;
    }

    private static StoreRepo repo(String fullName, int stars, List<String> ranks) {
        int slash = fullName.indexOf('/');
        String owner = slash >= 0 ? fullName.substring(0, slash) : "";
        String name = slash >= 0 ? fullName.substring(slash + 1) : fullName;
        return new StoreRepo(fullName, owner, name,
                "synthetic bench entry", "Java", "",
                stars, 0, 0L, null, null, "", 0L, null, 0,
                null, null, null, ranks, null);
    }

    /** 构造带后端 ranks 的整池；榜名轮询 98 榜。 */
    private static List<StoreRepo> buildRankedPool(int size) {
        Random rnd = new Random(20261109L);
        List<StoreRepo> pool = new ArrayList<>(size);
        int totalLists = GROUP_COUNT * LISTS_PER_GROUP;
        for (int i = 0; i < size; i++) {
            String fullName = "benchuser" + i + "/bench-repo-" + i;
            pool.add(repo(fullName, rnd.nextInt(50000),
                    Collections.singletonList("榜" + (i % totalLists))));
        }
        return pool;
    }

    /** 构造 ranks 全为 null 的旧缓存池（与 buildRankedPool 同批仓库）。 */
    private static List<StoreRepo> buildOldCachePool(List<StoreRepo> rankedPool) {
        List<StoreRepo> pool = new ArrayList<>(rankedPool.size());
        for (StoreRepo r : rankedPool) {
            pool.add(new StoreRepo(
                    r.fullName, r.owner, r.name, r.description, r.language,
                    r.avatarUrl, r.stars, r.forks, r.downloadCount,
                    r.topics, r.topicCodes, r.tag, r.publishedAt,
                    r.platforms, r.assetCount,
                    r.trendingScore, r.popularityScore, r.starsPerDay,
                    null, // ranks == null：旧数据，无法归属，直接跳过
                    r.category));
        }
        return pool;
    }

    /**
     * 当前线程 CPU 时间（毫秒）。android.jar 编译时不含 java.lang.management，
     * 故用反射取；取不到（如真机）返回 -1，wall 时间仍可用。
     * 单元测试跑在宿主 JDK 上，反射可取到，用于排除沙箱 CPU 争用。
     */
    private static long cpuTimeMs(Object mxBean) {
        if (mxBean == null) {
            return -1;
        }
        try {
            // 必须在公开接口上取方法：运行时类 sun.management.ThreadImpl
            // 所在包不对未命名模块导出，直接在其上 invoke 会报 IllegalAccessException。
            Class<?> iface = Class.forName("java.lang.management.ThreadMXBean");
            Object ns = iface.getMethod("getCurrentThreadCpuTime").invoke(mxBean);
            return ((Long) ns).longValue() / 1_000_000;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static Object threadMxBeanOrNull() {
        try {
            Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
            return factory.getMethod("getThreadMXBean").invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long totalAssigned(List<StoreRankGroups.Group> groups) {
        long total = 0;
        for (StoreRankGroups.Group g : groups) {
            for (StoreRankGroups.RankList l : g.lists) {
                total += l.items.size();
            }
        }
        return total;
    }

    @Test
    public void p10_rankedPoolBuildTiming() {
        Object mx = threadMxBeanOrNull();
        List<StoreRankGroups.Group> structure = structure98();
        assertEquals(98, StoreRankGroups.totalListCount(structure, null));

        List<StoreRepo> pool = buildRankedPool(POOL_SIZE);
        assertEquals(POOL_SIZE, pool.size());

        // 1) 冷启动第一跑：JIT 尚未热身，最接近真机上首次进榜页的一次 build()。
        long t0 = System.nanoTime();
        long c0 = cpuTimeMs(mx);
        List<StoreRankGroups.Group> cold = StoreRankGroups.build(null, pool, structure);
        long coldMs = (System.nanoTime() - t0) / 1_000_000;
        long coldCpuEnd = cpuTimeMs(mx);
        long coldCpuMs = (c0 < 0 || coldCpuEnd < 0) ? -1 : coldCpuEnd - c0;
        assertEquals(GROUP_COUNT, cold.size());
        assertEquals(98, cold.stream().mapToInt(g -> g.lists.size()).sum());
        long coldAssigned = totalAssigned(cold);
        assertEquals(POOL_SIZE, coldAssigned);

        // 2) 预热 3 次，再计时 30 次，取中位数/最小/最大；CPU 时间排除沙箱调度争用。
        for (int i = 0; i < 3; i++) {
            StoreRankGroups.build(null, pool, structure);
        }
        List<Long> runs = new ArrayList<>();
        List<Long> cpuRuns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long s = System.nanoTime();
            long cs = cpuTimeMs(mx);
            StoreRankGroups.build(null, pool, structure);
            runs.add((System.nanoTime() - s) / 1_000_000);
            long cpuEnd = cpuTimeMs(mx);
            cpuRuns.add((cs < 0 || cpuEnd < 0) ? -1 : cpuEnd - cs);
        }
        Collections.sort(runs);
        Collections.sort(cpuRuns);
        long medianMs = runs.get(15);
        long minMs = runs.get(0);
        long maxMs = runs.get(29);
        double meanMs = runs.stream().mapToLong(Long::longValue).average().orElse(-1);
        long cpuMedianMs = cpuRuns.get(15);
        long cpuMinMs = cpuRuns.get(0);
        long cpuMaxMs = cpuRuns.get(29);

        // 3) 对照 A：同一池子、ranks 改为空列表（后端确认无榜），0 条归属。
        List<StoreRepo> rankedPool = new ArrayList<>(pool.size());
        for (StoreRepo r : pool) {
            rankedPool.add(new StoreRepo(
                    r.fullName, r.owner, r.name, r.description, r.language,
                    r.avatarUrl, r.stars, r.forks, r.downloadCount,
                    r.topics, r.topicCodes, r.tag, r.publishedAt,
                    r.platforms, r.assetCount,
                    r.trendingScore, r.popularityScore, r.starsPerDay,
                    Collections.emptyList(), r.category));
        }
        for (int i = 0; i < 3; i++) {
            StoreRankGroups.build(null, rankedPool, structure);
        }
        long ct0 = System.nanoTime();
        List<StoreRankGroups.Group> ctrl = StoreRankGroups.build(null, rankedPool, structure);
        long ctrlMs = (System.nanoTime() - ct0) / 1_000_000;
        assertEquals(0, totalAssigned(ctrl));

        // 4) 对照 B：ranks 全为 null 的旧缓存池——动态化后直接跳过，不再匹配规则。
        List<StoreRepo> oldPool = buildOldCachePool(pool);
        long ot0 = System.nanoTime();
        List<StoreRankGroups.Group> oldGroups =
                StoreRankGroups.build(null, oldPool, structure);
        long oldMs = (System.nanoTime() - ot0) / 1_000_000;
        assertEquals(0, totalAssigned(oldGroups));

        System.out.println("P10-BENCH poolSize=" + POOL_SIZE
                + " lists=98"
                + " coldFirstBuildMs=" + coldMs
                + " coldFirstBuildCpuMs=" + coldCpuMs
                + " steadyMedianMs=" + medianMs
                + " steadyMinMs=" + minMs
                + " steadyMaxMs=" + maxMs
                + " steadyMeanMs=" + String.format("%.1f", meanMs)
                + " cpuMedianMs=" + cpuMedianMs
                + " cpuMinMs=" + cpuMinMs
                + " cpuMaxMs=" + cpuMaxMs
                + " perRepoMicros=" + String.format("%.1f", medianMs * 1000.0 / POOL_SIZE)
                + " assigned=" + coldAssigned
                + " controlRankedEmptyMs=" + ctrlMs
                + " oldCacheNullRanksMs=" + oldMs
                + " runsMs=" + runs);
        // 判定口径：以 CPU 时间中位数为准（排除沙箱调度争用）。冷启动 wall 耗时
        // 含 HotSpot 首次 JIT/类加载 + 沙箱争用，不代表真机主线程耗时，故只打印不入判定。
        long decideMs = cpuMedianMs >= 0 ? cpuMedianMs : medianMs;
        System.out.println("P10-BENCH verdict: cold=" + coldMs + "ms coldCpu=" + coldCpuMs
                + "ms median=" + medianMs + "ms cpuMedian=" + cpuMedianMs
                + "ms threshold=100ms decideOn=" + (cpuMedianMs >= 0 ? "cpuMedian" : "wallMedian")
                + " -> " + (decideMs > 100 ? "STUCK" : "NOT-STUCK"));

        // 结构不断言耗时（机器差异大）；结论由打印的数字定夺，见汇报。
    }
}
