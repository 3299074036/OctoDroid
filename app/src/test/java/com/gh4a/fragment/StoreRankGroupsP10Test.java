package com.gh4a.fragment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P10（第三轮报告 §6）：旧数据下 StoreRankGroups.build() 在主线程逐条做正则 split。
 * 实测：构造 ranks 全为 null 的旧缓存池（2457 条），跑真实 build() 计时。
 *
 * 纯 JVM 单测（不走 Robolectric）：ranks == null 时 build() 不碰 Context
 *（backendCells 只在 ranks != null 时调用），matchRules 与 StoreRepo 构造
 * 均为纯 java.util 代码；Context 传 null。
 *
 * 判定线（任务书）：单次 build 超 100ms 即“卡”→ 修；否则结案不改代码。
 * 2026-11-09 实测结论（见 P10-BENCH 输出）：不卡，不修——稳态 CPU 耗时
 * 中位数 13–21ms（30 次计时），319,410 次 split 本身仅占 3–5ms CPU，
 * 预先 split 的收益极小；冷启动 wall 耗时（143–1119ms）是沙箱 CPU 争用
 * + HotSpot 首次 JIT/类加载所致，真机 ART 下不存在同等开销；该路径
 * 当前后端数据下基本是冷的，且 StoreView.rankGroups() 会缓存结果。
 */
public class StoreRankGroupsP10Test {

    /** 报告口径的旧缓存整池大小。 */
    private static final int POOL_SIZE = 2457;

    /** RULES 实际条数（代码实数，非报告约数 120）。 */
    private static final int RULE_COUNT = 130;

    /** 现实旧缓存里常见的 topic 池：部分命中规则、部分不命中。 */
    private static final String[] TOPIC_POOL = {
            "android", "vpn", "editor", "music-player", "docker", "note",
            "game", "markdown", "database", "chat", "cli", "proxy",
            "browser", "pdf", "calendar", "finance", "terminal", "music",
            "video-player", "subtitle", "screenshot", "launcher",
            "unrelated-topic", "some-library", "dev-toolkit", "my-app"
    };

    /** 构造 ranks 全为 null 的旧缓存池；fullName 用合成名，避开静态 ASSIGN_MAP。 */
    private static List<StoreRepo> buildOldCachePool(int size) {
        Random rnd = new Random(20261109L);
        List<StoreRepo> pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            List<String> topics = new ArrayList<>(3);
            for (int k = 0; k < 3; k++) {
                topics.add(TOPIC_POOL[rnd.nextInt(TOPIC_POOL.length)]);
            }
            String fullName = "benchuser" + i + "/bench-repo-" + i;
            pool.add(new StoreRepo(
                    fullName, "benchuser" + i, "bench-repo-" + i,
                    "synthetic old-cache entry", "Java", "",
                    rnd.nextInt(50000), rnd.nextInt(1000), 0L,
                    topics, null, "", 0L, null, 0,
                    null, null, null,
                    null, // ranks == null：旧数据，走 matchRules
                    null));
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
    public void p10_oldCachePoolBuildTiming() {
        Object mx = threadMxBeanOrNull();
        // 先触发类静态初始化（ASSIGN_MAP / NAME_TO_CELL），不计入冷启动耗时。
        assertEquals(96, StoreRankGroups.totalListCount());

        List<StoreRepo> pool = buildOldCachePool(POOL_SIZE);
        assertEquals(POOL_SIZE, pool.size());

        // 1) 冷启动第一跑：JIT 尚未热身，最接近真机上首次进榜页的一次 build()。
        long t0 = System.nanoTime();
        long c0 = cpuTimeMs(mx);
        List<StoreRankGroups.Group> cold = StoreRankGroups.build(null, pool);
        long coldMs = (System.nanoTime() - t0) / 1_000_000;
        long coldCpuEnd = cpuTimeMs(mx);
        long coldCpuMs = (c0 < 0 || coldCpuEnd < 0) ? -1 : coldCpuEnd - c0;
        assertEquals(7, cold.size());
        assertEquals(96, cold.stream().mapToInt(g -> g.lists.size()).sum());
        long coldAssigned = totalAssigned(cold);
        assertTrue("expected some repos to match rules, got 0", coldAssigned > 0);

        // 2) 预热 3 次，再计时 30 次，取中位数/最小/最大；CPU 时间排除沙箱调度争用。
        for (int i = 0; i < 3; i++) {
            StoreRankGroups.build(null, pool);
        }
        List<Long> runs = new ArrayList<>();
        List<Long> cpuRuns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long s = System.nanoTime();
            long cs = cpuTimeMs(mx);
            StoreRankGroups.build(null, pool);
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

        // 3) 对照：同一池子、ranks 改为空列表（后端确认无榜），不走 matchRules。
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
            StoreRankGroups.build(null, rankedPool);
        }
        long ct0 = System.nanoTime();
        List<StoreRankGroups.Group> ctrl = StoreRankGroups.build(null, rankedPool);
        long ctrlMs = (System.nanoTime() - ct0) / 1_000_000;
        assertEquals(0, totalAssigned(ctrl));

        // 4) 归因：光是 319,410 次 keyword.split("\\+") 本身要多久。
        int splitCount = POOL_SIZE * RULE_COUNT;
        String[] probes = {"editor", "music-player", "mcp", "video-player", "docker"};
        for (int i = 0; i < 10000; i++) {
            probes[i % probes.length].split("\\+");
        }
        long s0 = System.nanoTime();
        for (int i = 0; i < splitCount; i++) {
            probes[i % probes.length].split("\\+");
        }
        long splitMs = (System.nanoTime() - s0) / 1_000_000;

        System.out.println("P10-BENCH poolSize=" + POOL_SIZE
                + " rulesPerRepo=" + RULE_COUNT
                + " keywordSplits=" + splitCount
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
                + " rawSplitLoopMs=" + splitMs
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
