package com.gh4a.fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gh4a.utils.Iso8601;

/**
 * 软件商店的一行数据（扁平字段，不再持有 githubsdk 模型对象）：
 * 后端 JSON 与本地实时接口（提交预览、旧列表页）都汇成这一种。
 * tag 为空表示暂无 Release；platforms 为空表示无安装包。
 */
public class StoreRepo {
    public final String fullName;
    public final String owner;
    public final String name;
    public final String description;
    public final String language;
    public final String avatarUrl;
    public final int stars;
    public final int forks;
    public final long downloadCount;
    public final List<String> topics;
    public final Set<String> topicCodes;
    public final String tag;
    public final long publishedAt;
    public final EnumSet<StoreClassifier.Platform> platforms;
    public final int assetCount;
    /**
     * 后端公式字段（0.1.57）：三者均为可空——缺字段即“没有”，不是 0。
     * trendingScore 仅镜像条目有；popularityScore 后端补算（仅作数据保留，
     * 榜单排序一律按星标，不用此分）；starsPerDay 仅生成器条目有。
     * 旧缓存／实时接口三者均为 null，排序回落到星标。
     */
    public final Double trendingScore;
    public final Double popularityScore;
    public final Double starsPerDay;
    /**
     * 后端给的榜单归属（0.1.66）：null = 字段缺失（旧数据），回落本地静态表；
     * 空列表 = 后端确认无榜，信后端、不回落。元素为榜单中文名。
     */
    public final List<String> ranks;
    /** hiwiki 大类原值（ai／development／game／media／network／productivity／tools），缺失为 null。 */
    public final String category;

    public StoreRepo(String fullName, String owner, String name,
            String description, String language, String avatarUrl,
            int stars, int forks, long downloadCount,
            List<String> topics, Set<String> topicCodes,
            String tag, long publishedAt,
            EnumSet<StoreClassifier.Platform> platforms, int assetCount,
            Double trendingScore, Double popularityScore, Double starsPerDay,
            List<String> ranks, String category) {
        this.fullName = fullName != null ? fullName : "";
        this.owner = owner != null ? owner : "";
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.language = language;
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.stars = stars;
        this.forks = forks;
        this.downloadCount = downloadCount;
        this.topics = topics != null ? topics : new ArrayList<>();
        this.topicCodes = topicCodes != null ? topicCodes : new HashSet<>();
        this.tag = tag != null ? tag : "";
        this.publishedAt = publishedAt;
        this.platforms = platforms != null ? platforms
                : EnumSet.noneOf(StoreClassifier.Platform.class);
        this.assetCount = assetCount;
        this.trendingScore = trendingScore;
        this.popularityScore = popularityScore;
        this.starsPerDay = starsPerDay;
        this.ranks = ranks;
        this.category = category;
    }

    /** 用途分类：后端 category 为空（旧数据／实时接口）时一律归“其他”。 */
    public String categoryOrOther() {
        return category != null && !category.isEmpty() ? category : "other";
    }

    /**
     * 热门排序比较器（0.1.57 公式）：有 trendingScore 的镜像条目按该分
     * 降序在前；无该字段的生成器条目按 stars_per_day 降序在其后；两者
     * 都缺（旧缓存／实时接口）回落按星标，不得崩溃；同组内平局按星标。
     */
    public static int compareHot(StoreRepo a, StoreRepo b) {
        boolean aTrend = a.trendingScore != null;
        boolean bTrend = b.trendingScore != null;
        if (aTrend && bTrend) {
            int byTrend = Double.compare(b.trendingScore, a.trendingScore);
            return byTrend != 0 ? byTrend : Integer.compare(b.stars(), a.stars());
        }
        if (aTrend) {
            return -1;
        }
        if (bTrend) {
            return 1;
        }
        boolean aSpd = a.starsPerDay != null;
        boolean bSpd = b.starsPerDay != null;
        if (aSpd && bSpd) {
            int bySpd = Double.compare(b.starsPerDay, a.starsPerDay);
            return bySpd != 0 ? bySpd : Integer.compare(b.stars(), a.stars());
        }
        if (aSpd) {
            return -1;
        }
        if (bSpd) {
            return 1;
        }
        return Integer.compare(b.stars(), a.stars());
    }

    public boolean hasAssets() {
        return !platforms.isEmpty();
    }

    public String fullName() {
        return fullName;
    }

    public String owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public int stars() {
        return stars;
    }

    /** 最新 Release 的 tag；为空表示暂无 Release。 */
    public String version() {
        return tag;
    }

    /** 后端 JSON 一条转 StoreRepo；单条解析失败由调用方跳过。 */
    public static StoreRepo fromJson(JSONObject obj) throws Exception {
        EnumSet<StoreClassifier.Platform> platforms =
                EnumSet.noneOf(StoreClassifier.Platform.class);
        JSONArray platArr = obj.optJSONArray("platforms");
        if (platArr != null) {
            for (int i = 0; i < platArr.length(); i++) {
                StoreClassifier.Platform platform =
                        platformFromKey(platArr.optString(i, ""));
                if (platform != null) {
                    platforms.add(platform);
                }
            }
        }
        List<String> topics = new ArrayList<>();
        JSONArray topicArr = obj.optJSONArray("topics");
        if (topicArr != null) {
            for (int i = 0; i < topicArr.length(); i++) {
                topics.add(topicArr.optString(i, ""));
            }
        }
        Set<String> codes = new HashSet<>();
        JSONArray codeArr = obj.optJSONArray("topicCodes");
        if (codeArr != null) {
            for (int i = 0; i < codeArr.length(); i++) {
                codes.add(codeArr.optString(i, ""));
            }
        }
        // 后端 JSON 一度不带 name：name 为空时从 fullName 截取（斜杠后一段），
        // 已发布数据与缓存不用动；生成器侧同时补 name（治本）。
        // owner 同理：newest.json 等不带 owner 的条目从 fullName 截取。
        String fullName = obj.optString("fullName", "");
        String name = obj.optString("name", "");
        if (name.isEmpty() && fullName.contains("/")) {
            name = fullName.substring(fullName.lastIndexOf('/') + 1);
        }
        String owner = obj.optString("owner", "");
        if (owner.isEmpty() && fullName.contains("/")) {
            owner = fullName.substring(0, fullName.indexOf('/'));
        }
        // 0.1.66：后端 ranks（null = 旧数据缺失，[] = 后端确认无榜）与 category。
        List<String> ranks = null;
        JSONArray ranksArr = obj.optJSONArray("ranks");
        if (ranksArr != null) {
            ranks = new ArrayList<>();
            for (int i = 0; i < ranksArr.length(); i++) {
                String rank = ranksArr.optString(i, "");
                if (!rank.isEmpty()) {
                    ranks.add(rank);
                }
            }
        }
        String category = obj.optString("category", null);
        if (category != null && category.isEmpty()) {
            category = null;
        }
        // 发版时间：feed 用 latestDate；newest.json 用 lastReleaseTime，
        // 都没有时退到 pushedAt（hiwiki 的日期只有年月日）。
        long publishedAt = parseDate(obj.optString("latestDate", ""));
        if (publishedAt == 0) {
            publishedAt = parseDate(obj.optString("lastReleaseTime", ""));
        }
        if (publishedAt == 0) {
            publishedAt = parseDate(obj.optString("pushedAt", ""));
        }
        return new StoreRepo(
                fullName,
                owner,
                name,
                obj.optString("description", ""),
                obj.optString("language", null),
                obj.optString("avatarUrl", ""),
                obj.optInt("stars", 0),
                obj.optInt("forks", 0),
                obj.optLong("downloadCount", 0),
                topics, codes,
                obj.optString("latestTag", ""),
                publishedAt,
                platforms, obj.optInt("assetCount", 0),
                optDoubleOrNull(obj, "trendingScore"),
                optDoubleOrNull(obj, "popularityScore"),
                optDoubleOrNull(obj, "stars_per_day"),
                ranks, category);
    }

    /** 数字字段可空读取：字段缺失／null／非数字一律返回 null（不是 0），调用方靠 null 区分条目来源。 */
    private static Double optDoubleOrNull(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static StoreClassifier.Platform platformFromKey(String key) {
        switch (key) {
            case "android":
                return StoreClassifier.Platform.ANDROID;
            case "ios":
                return StoreClassifier.Platform.IOS;
            case "windows":
                return StoreClassifier.Platform.WINDOWS;
            case "macos":
                return StoreClassifier.Platform.MACOS;
            case "linux":
                return StoreClassifier.Platform.LINUX;
            case "harmony":
                return StoreClassifier.Platform.HARMONY;
            case "extension":
                return StoreClassifier.Platform.EXTENSION;
            case "archive":
                return StoreClassifier.Platform.ARCHIVE;
            default:
                return null;
        }
    }

    private static long parseDate(String value) {
        // 61+72：解析走统一入口（ThreadLocal + UTC）。
        return Iso8601.parseOrZero(value);
    }
}
