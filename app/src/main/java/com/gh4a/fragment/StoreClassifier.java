package com.gh4a.fragment;

import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.Repository;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 软件商店的收录分类：产物平台识别 + 发现分类码。
 *
 * 平台识别（Komi Store 同款思路：按最新 Release 附件后缀认平台）：
 * apk→Android、ipa→iOS、exe/msi/msix/appx/ps1→Windows、dmg/pkg→macOS、
 * AppImage/deb/rpm/sh→Linux、hap→鸿蒙、crx/xpi→浏览器扩展；
 * zip/tar.gz/tgz/7z/whl/jar/war/iso/img/xz/bz2/rar 视为通用压缩包。
 * 与 store-backend/generate.py 的 SUFFIX_PLATFORM 保持一致。
 * 附件后缀不在上表（如 sha256、sig、txt）不计为安装包。
 *
 * 发现分类码（FeedCategory，Komi 同款）：topics 命中即归入，可多归；
 * 与 store-backend/generate.py 的 TOPIC_TO_CODE 保持一致，后端是
 * 发现页的权威来源，本函数只供本地实时接口（提交预览、旧列表页）
 * 生成的扁平对象使用。
 */
public class StoreClassifier {
    public enum Platform {
        ANDROID, IOS, WINDOWS, MACOS, LINUX, HARMONY, EXTENSION, ARCHIVE
    }

    public static final String CAT_ALL = "all";
    public static final String CAT_AI = "ai";
    public static final String CAT_PRIVACY = "privacy";
    public static final String CAT_NETWORKING = "networking";
    public static final String CAT_MEDIA = "media";
    public static final String CAT_SOCIAL = "social";
    public static final String CAT_READING = "reading";
    public static final String CAT_TOOLS = "tools";

    private static final String[][] TOPIC_CODES = {
        { CAT_AI, "ai,llm,gpt,machine-learning,deep-learning,chatgpt,"
                + "agent,agents,ai-agent,generative-ai" },
        { CAT_PRIVACY, "privacy,security,encryption,password,vpn,"
                + "adblock,ad-block,adblocker" },
        { CAT_NETWORKING, "networking,network,browser,messaging,chat,"
                + "email,rss,proxy,dns,downloader" },
        { CAT_MEDIA, "audio,video,photo,music,media,player,podcast,"
                + "image,streaming,tv" },
        { CAT_SOCIAL, "social,mastodon,fediverse,forum,community" },
        { CAT_READING, "reader,reading,notes,note,ebook,novel" },
        { CAT_TOOLS, "launcher,backup,self-hosted,tool,tools,utility,"
                + "utilities,automation" },
    };

    /** 单个附件对应的平台集合；空集表示不是安装包。 */
    public static EnumSet<Platform> platformsForAsset(String fileName) {
        EnumSet<Platform> out = EnumSet.noneOf(Platform.class);
        if (fileName == null) {
            return out;
        }
        String name = fileName.toLowerCase(Locale.US);
        if (name.endsWith(".apk")) {
            out.add(Platform.ANDROID);
        } else if (name.endsWith(".ipa")) {
            out.add(Platform.IOS);
        } else if (name.endsWith(".exe") || name.endsWith(".msi")
                || name.endsWith(".msix") || name.endsWith(".appx")
                || name.endsWith(".ps1")) {
            out.add(Platform.WINDOWS);
        } else if (name.endsWith(".dmg") || name.endsWith(".pkg")) {
            out.add(Platform.MACOS);
        } else if (name.endsWith(".appimage") || name.endsWith(".deb")
                || name.endsWith(".rpm") || name.endsWith(".sh")) {
            out.add(Platform.LINUX);
        } else if (name.endsWith(".hap")) {
            out.add(Platform.HARMONY);
        } else if (name.endsWith(".crx") || name.endsWith(".xpi")) {
            out.add(Platform.EXTENSION);
        } else if (name.endsWith(".zip") || name.endsWith(".tar.gz")
                || name.endsWith(".tgz") || name.endsWith(".7z")
                || name.endsWith(".whl") || name.endsWith(".jar")
                || name.endsWith(".war") || name.endsWith(".iso")
                || name.endsWith(".img") || name.endsWith(".xz")
                || name.endsWith(".bz2") || name.endsWith(".rar")) {
            out.add(Platform.ARCHIVE);
        }
        return out;
    }

    /** 附件是否可安装（平台集合非空）。 */
    public static boolean isInstallable(ReleaseAsset asset) {
        return asset != null && !platformsForAsset(asset.name()).isEmpty();
    }

    /** 仓库 topics 命中的发现分类码集合。 */
    public static Set<String> topicCodesFor(List<String> topics) {
        Set<String> out = new HashSet<>();
        if (topics == null) {
            return out;
        }
        for (String topic : topics) {
            if (topic == null) {
                continue;
            }
            String low = topic.toLowerCase(Locale.US);
            for (String[] entry : TOPIC_CODES) {
                for (String member : entry[1].split(",")) {
                    if (low.equals(member)) {
                        out.add(entry[0]);
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** 仓库 topics 命中的发现分类码集合（仓库对象版）。 */
    public static Set<String> topicCodesFor(Repository repo) {
        return topicCodesFor(repo != null ? repo.topics() : null);
    }
}
