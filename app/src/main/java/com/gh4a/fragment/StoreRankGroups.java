package com.gh4a.fragment;

import android.content.Context;

import com.gh4a.utils.DiagTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 排行榜分组（hiwiki 三页签；2026-11-22 起动态化，不再写死榜单）。
 *
 * 榜单结构（大类 → 细分榜单）由后端随 feed/all.json 的 "boards" 字段
 * 下发（harvest 每轮现取 hiwiki 榜单页，见 store-backend），与本轮
 * feed 同源、永不错位；hiwiki 新增榜单（如 2026-11-22 的「AI音视频
 * 工具」「游戏服务器工具」）无需改代码即可出现。
 *
 * 归属来源：条目带后端 ranks 时信后端——非空用后端给的榜名，空列表
 * 即后端确认无榜；ranks 缺失（旧数据）无法归属，不再回落本地静态
 * 表与关键词规则（已随 96 榜静态表一并删除）。榜内排序为纯星标降序，
 * 平局按 fullName 升序（与 hiwiki 一致）。
 *
 * 空榜单默认如实保留；勾选「只看有收录的榜单」时隐藏空卡片（0.1.60 起）。
 */
public final class StoreRankGroups {
    private StoreRankGroups() {
    }

    /** 一个细分榜单：名＋按星标排好的成员。 */
    public static final class RankList {
        public final String name;
        public final List<StoreRepo> items = new ArrayList<>();

        public RankList(String name) {
            this.name = name;
        }
    }

    /** 一个大类：名＋其下全部细分榜单（含空榜）。 */
    public static final class Group {
        public final String name;
        public final List<RankList> lists = new ArrayList<>();

        public Group(String name) {
            this.name = name;
        }
    }

    /**
     * 细分榜单总数。structure 为 feed 的 boards 结构时按结构计数；
     * structure 为 null（旧 feed、无 boards 字段）时按池子里实际
     * 出现的榜名数回落——两者都由数据定，不写死。
     */
    public static int totalListCount(List<Group> structure, List<StoreRepo> pool) {
        if (structure != null) {
            int total = 0;
            for (Group group : structure) {
                total += group.lists.size();
            }
            return total;
        }
        Set<String> names = new HashSet<>();
        if (pool != null) {
            for (StoreRepo item : pool) {
                if (item.ranks != null) {
                    names.addAll(item.ranks);
                }
            }
        }
        return names.size();
    }

    /** 榜单中文名 → "组:榜"（后端 ranks 是榜名，在此转成本地格子）。 */
    private static Map<String, String> buildNameToCell(List<Group> structure) {
        Map<String, String> map = new HashMap<>();
        for (int gi = 0; gi < structure.size(); gi++) {
            List<RankList> lists = structure.get(gi).lists;
            for (int li = 0; li < lists.size(); li++) {
                map.put(lists.get(li).name, gi + ":" + li);
            }
        }
        return map;
    }

    /** 后端给的榜名转格子：榜名不在结构里的跳过（feed 与结构本应同源，
     * 出现即数据不一致），丢弃时记一条诊断（只记榜名，不记仓库名）。 */
    private static Set<String> backendCells(Context context, StoreRepo item,
            Map<String, String> nameToCell) {
        Set<String> out = new HashSet<>();
        if (item.ranks != null) {
            for (String rank : item.ranks) {
                String cell = nameToCell.get(rank);
                if (cell != null) {
                    out.add(cell);
                } else {
                    DiagTrace.log(context, "STORE", "unknown rank dropped: " + rank);
                }
            }
        }
        return out;
    }

    /**
     * 对已加载的 feed 池子现算榜单。structure 为 feed 的 boards 结构
     * （null 时返回空列表——无结构建不出榜）；ranks 非空用后端归属，
     * ranks 为空列表信后端为无榜，ranks 缺失（旧数据）无法归属；
     * 每榜成员按星标倒序，平局按 fullName 升序；空榜保留空列表。
     */
    public static List<Group> build(Context context, List<StoreRepo> pool,
            List<Group> structure) {
        List<Group> groups = new ArrayList<>();
        if (structure == null) {
            return groups;
        }
        Map<String, String> nameToCell = buildNameToCell(structure);
        for (Group skeleton : structure) {
            Group group = new Group(skeleton.name);
            for (RankList skeletonList : skeleton.lists) {
                group.lists.add(new RankList(skeletonList.name));
            }
            groups.add(group);
        }
        if (pool == null) {
            return groups;
        }
        for (StoreRepo item : pool) {
            Set<String> cells;
            if (item.ranks != null) {
                // 后端给榜：非空用后端归属，空列表即后端确认无榜。
                cells = backendCells(context, item, nameToCell);
            } else {
                // ranks 缺失（旧数据）：无法归属，不再回落本地规则。
                continue;
            }
            for (String cell : cells) {
                String[] parts = cell.split(":");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int gi = Integer.parseInt(parts[0]);
                    int li = Integer.parseInt(parts[1]);
                    if (gi >= 0 && gi < groups.size()
                            && li >= 0 && li < groups.get(gi).lists.size()) {
                        groups.get(gi).lists.get(li).items.add(item);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Group group : groups) {
            for (RankList list : group.lists) {
                // 榜内纯星标降序（hiwiki 同款），平局按 fullName 升序。
                Collections.sort(list.items, (a, b) -> {
                    int byStars = Integer.compare(b.stars(), a.stars());
                    if (byStars != 0) {
                        return byStars;
                    }
                    String aName = a.fullName() != null
                            ? a.fullName().toLowerCase(Locale.US) : "";
                    String bName = b.fullName() != null
                            ? b.fullName().toLowerCase(Locale.US) : "";
                    return aName.compareTo(bName);
                });
            }
        }
        return groups;
    }
}
