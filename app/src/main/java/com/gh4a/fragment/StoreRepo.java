package com.gh4a.fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

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

    public StoreRepo(String fullName, String owner, String name,
            String description, String language, String avatarUrl,
            int stars, int forks, long downloadCount,
            List<String> topics, Set<String> topicCodes,
            String tag, long publishedAt,
            EnumSet<StoreClassifier.Platform> platforms, int assetCount) {
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
        String fullName = obj.optString("fullName", "");
        String name = obj.optString("name", "");
        if (name.isEmpty() && fullName.contains("/")) {
            name = fullName.substring(fullName.lastIndexOf('/') + 1);
        }
        return new StoreRepo(
                fullName,
                obj.optString("owner", ""),
                name,
                obj.optString("description", ""),
                obj.optString("language", null),
                obj.optString("avatarUrl", ""),
                obj.optInt("stars", 0),
                obj.optInt("forks", 0),
                obj.optLong("downloadCount", 0),
                topics, codes,
                obj.optString("latestTag", ""),
                parseDate(obj.optString("latestDate", "")),
                platforms, obj.optInt("assetCount", 0));
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
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fmt.parse(value).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
