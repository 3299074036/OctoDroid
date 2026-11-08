package com.gh4a.fragment;

import android.content.Context;

import com.gh4a.ApiRequestException;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.repositories.RepositoryReleaseService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;
import com.meisolsson.githubsdk.service.search.SearchService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 软件商店的数据层，分两路：
 *
 * 1. 后端预计算（商店页签唯一来源）：每小时生成的静态 JSON，见
 *    store-backend/generate.py；App 取现成文件，带缓存与离线兜底。
 * 2. GitHub 实时接口（只剩提交预览与旧列表页在用）：提交页取单仓
 *    最新 Release 预览，旧列表页沿用原三口径搜索。
 *
 * 收录规则（两路一致）：只展示最新 Release 带可安装附件的仓库。
 */
public class StoreApi {
    /** 后端静态站（gh-pages）：https://3299074036.github.io/OctoDroid/store/ */
    private static final String BACKEND_BASE = "https://3299074036.github.io/OctoDroid/store/";
    /**
     * Komi 式离线镜像：同一批后端文件换 raw 域名取，不用新建仓库、不用改工作流。
     * 对应 Komi 的 OFFLINE_MIRROR_BASE（komi-store-backend-data 经 raw 取）。
     */
    private static final String MIRROR_BASE =
            "https://raw.githubusercontent.com/3299074036/OctoDroid/gh-pages/store/";
    private static final long FEED_TTL_MS = 3L * 60 * 60 * 1000;
    private static final long CHART_TTL_MS = 24L * 60 * 60 * 1000;
    private static final String CACHE_DIR = "store-cache";

    /**
     * 后端取回的一组数据：offline=true 表示来自过期缓存或离线镜像；
     * mirror=true 表示来自离线镜像（Komi 的 isOffline 同款，不写缓存）；
     * hasMore 只对发现分页有意义（Komi 的 BackendFeedResponse 同款）。
     */
    public static class StoreData {
        public final List<StoreRepo> items;
        public final boolean offline;
        public final boolean mirror;
        public final String generatedAt;
        public final boolean hasMore;

        public StoreData(List<StoreRepo> items, boolean offline, String generatedAt) {
            this(items, offline, false, generatedAt, false);
        }

        public StoreData(List<StoreRepo> items, boolean offline, String generatedAt,
                boolean hasMore) {
            this(items, offline, false, generatedAt, hasMore);
        }

        public StoreData(List<StoreRepo> items, boolean offline, boolean mirror,
                String generatedAt) {
            this(items, offline, mirror, generatedAt, false);
        }

        public StoreData(List<StoreRepo> items, boolean offline, boolean mirror,
                String generatedAt, boolean hasMore) {
            this.items = items;
            this.offline = offline;
            this.mirror = mirror;
            this.generatedAt = generatedAt;
            this.hasMore = hasMore;
        }
    }

    // ---------- 后端预计算 ----------

    /**
     * 取后端列表：kind 为 feed 或 trending / new-releases / most-popular，
     * platform 为 all / android / macos / windows / linux / ios。
     * 链路（Komi 同款）：新鲜缓存 → 主站（成功后写缓存）→ 过期缓存 →
     * 离线镜像（标离线、不写缓存）→ 失败（无内置快照）。
     */
    public static Single<StoreData> loadBackend(Context context, String kind, String platform) {
        return Single.fromCallable(() -> loadBackendSync(context, kind, platform))
                .compose(RxUtils::doInBackground);
    }

    private static StoreData loadBackendSync(Context context, String kind, String platform)
            throws IOException {
        String key = kind + "_" + platform;
        long ttl = "feed".equals(kind) ? FEED_TTL_MS : CHART_TTL_MS;
        String path = "feed".equals(kind)
                ? "feed/" + platform + ".json"
                : "categories/" + kind + "/" + platform + ".json";
        return loadBackendFileSync(context, key, ttl, path, false);
    }

    /**
     * 最新入库（0.1.66）：store/feed/newest.json，24h 缓存，链路与 feed
     * 一致（新鲜缓存 → 主站 → 过期缓存 → 离线镜像 → 失败）。文件尚未
     * 发布（404）时整组失败，调用方显示空态／重试，绝不崩溃。
     * 文件格式：60 条的 JSON 列表，或 {"items": [...]} 包裹（见 parseNewest）。
     */
    public static Single<StoreData> loadNewest(Context context) {
        return Single.fromCallable(() -> loadBackendFileSync(context,
                "newest", CHART_TTL_MS, "feed/newest.json", true))
                .compose(RxUtils::doInBackground);
    }

    /**
     * 发现分页（Komi 同款：每页 20 条，hasMore 翻页）：取
     * feed/{platform}/page{n}.json，缓存与离线链路与整包一致
     * （新鲜缓存 3 小时 → 主站 → 过期缓存 → 镜像不写缓存 → 失败）。
     */
    public static Single<StoreData> loadBackendFeedPage(Context context, String platform, int page) {
        return Single.fromCallable(() -> loadBackendFileSync(context,
                "feed_" + platform + "_page" + page, FEED_TTL_MS,
                "feed/" + platform + "/page" + page + ".json", false))
                .compose(RxUtils::doInBackground);
    }

    private static StoreData loadBackendFileSync(Context context, String key, long ttl, String path,
            boolean newest) throws IOException {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        File cacheFile = new File(dir, key + ".json");
        File metaFile = new File(dir, key + ".meta");
        long savedAt = readSavedAt(metaFile);
        boolean fresh = savedAt > 0 && System.currentTimeMillis() - savedAt < ttl;
        if (fresh && cacheFile.exists()) {
            StoreData data = parseFile(readFile(cacheFile), false, newest);
            if (data != null) {
                return data;
            }
        }
        try {
            String body = fetchUrl(BACKEND_BASE + path);
            dir.mkdirs();
            writeFile(cacheFile, body);
            writeFile(metaFile, String.valueOf(System.currentTimeMillis()));
            StoreData data = parseFile(body, false, newest);
            if (data != null) {
                return data;
            }
            throw new IOException("bad backend payload");
        } catch (IOException e) {
            if (cacheFile.exists()) {
                StoreData stale = parseFile(readFile(cacheFile), true, newest);
                if (stale != null) {
                    return stale;
                }
            }
            // Komi 式离线镜像：主站与过期缓存都失败后走 raw 镜像，
            // 与 Komi 一致：标离线、不写缓存；镜像失败则整组失败（无快照）。
            try {
                String mirrorBody = fetchUrl(MIRROR_BASE + path);
                StoreData mirrorData = parseFile(mirrorBody, true, newest);
                if (mirrorData != null) {
                    return new StoreData(mirrorData.items, true, true,
                            mirrorData.generatedAt, mirrorData.hasMore);
                }
            } catch (IOException ignored) {
                // 镜像失败 → 整组失败
            }
            throw e;
        }
    }

    private static StoreData parseFile(String json, boolean offline, boolean newest) {
        return newest ? parseNewest(json, offline) : parseBackend(json, offline);
    }

    private static String fetchUrl(String url) throws IOException {
        OkHttpClient client = ServiceFactory.getHttpClientBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        // 后端拉取强制走网络，不读 OkHttp 磁盘缓存（Komi 的镜像客户端本身不走缓存）。
        Request request = new Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("http " + response.code());
            }
            return response.body().string();
        }
    }

    private static StoreData parseBackend(String json, boolean offline) {
        try {
            JSONObject root = new JSONObject(json);
            String generatedAt = root.optString("generatedAt", "");
            boolean hasMore = root.optBoolean("hasMore", false);
            JSONArray arr = root.optJSONArray("items");
            List<StoreRepo> items = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        items.add(StoreRepo.fromJson(arr.getJSONObject(i)));
                    } catch (Exception ignored) {
                        // 单条坏数据只跳过该条，不让整组垮掉
                    }
                }
            }
            return new StoreData(items, offline, generatedAt, hasMore);
        } catch (Exception e) {
            return null;
        }
    }

    /** 最新入库文件解析：裸 JSON 列表，或 {"items": [...]} 包裹；单条坏数据只跳过该条。 */
    private static StoreData parseNewest(String json, boolean offline) {
        try {
            String text = json.trim();
            JSONArray arr;
            String generatedAt = "";
            if (text.startsWith("[")) {
                arr = new JSONArray(text);
            } else {
                JSONObject root = new JSONObject(text);
                generatedAt = root.optString("generatedAt", "");
                arr = root.optJSONArray("items");
            }
            List<StoreRepo> items = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        items.add(StoreRepo.fromJson(arr.getJSONObject(i)));
                    } catch (Exception ignored) {
                        // 单条坏数据只跳过该条，不让整组垮掉
                    }
                }
            }
            return new StoreData(items, offline, generatedAt);
        } catch (Exception e) {
            return null;
        }
    }

    private static long readSavedAt(File metaFile) {
        try {
            String text = readFile(metaFile).trim();
            return Long.parseLong(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------- GitHub 实时接口（提交预览、旧列表页） ----------

    /** 仓库搜索：沿用 SearchFragment 的热门预取同款链路。 */
    public static Single<List<Repository>> searchRepos(String query, String sort, String order) {
        SearchService service = ServiceFactory.get(SearchService.class, false);
        return service.searchRepositories(query, sort, order, 1)
                .compose(result -> RxUtils.<Repository, Object>searchPageAdapter(result, item -> item))
                .map(ApiHelpers::throwOnFailure)
                .map(page -> {
                    List<Repository> out = new ArrayList<>();
                    if (page != null && page.items() != null) {
                        for (Object item : page.items()) {
                            if (item instanceof Repository) {
                                out.add((Repository) item);
                            }
                        }
                    }
                    return out;
                })
                .compose(RxUtils::doInBackground);
    }

    /**
     * 给仓库列表逐个挂最新 Release 并过滤：无 Release 或无安装包的仓库
     * 在 dropEmpty 时直接去掉；单仓失败（404 等）只丢该仓，不让整组垮掉。
     */
    public static Single<List<StoreRepo>> enrich(List<Repository> repos, boolean dropEmpty) {
        if (repos == null || repos.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        List<Single<StoreRepo>> singles = new ArrayList<>();
        for (Repository repo : repos) {
            // RxJava2 的 Single 不能发 null：onErrorReturnItem(null) 会在调用时
            // 即 requireNonNull 抛 NPE（0.1.45 全军加载失败的根因）。兜底必须
            // 是非空哨兵（无 Release 的空行，dropEmpty 时照常被过滤掉）。
            singles.add(latestStoreRepo(repo).onErrorReturnItem(buildStoreRepo(repo, null)));
        }
        return Single.zip(singles, results -> {
            List<StoreRepo> out = new ArrayList<>();
            for (Object result : results) {
                if (!(result instanceof StoreRepo)) {
                    continue;
                }
                StoreRepo item = (StoreRepo) result;
                if (dropEmpty && !item.hasAssets()) {
                    continue;
                }
                out.add(item);
            }
            return out;
        }).compose(RxUtils::doInBackground);
    }

    /** 单仓的最新 Release（第 1 页首个非草稿；无 Release 返回空产物行）。 */
    public static Single<StoreRepo> latestStoreRepo(Repository repo) {
        RepositoryReleaseService service =
                ServiceFactory.get(RepositoryReleaseService.class, false);
        return service.getReleases(repo.owner().login(), repo.name(), 1)
                .map(response -> {
                    Release latest = null;
                    if (response.isSuccessful() && response.body() != null
                            && response.body().items() != null) {
                        for (Release release : response.body().items()) {
                            if (release != null && !Boolean.TRUE.equals(release.draft())) {
                                latest = release;
                                break;
                            }
                        }
                    }
                    return buildStoreRepo(repo, latest);
                });
    }

    /** 取仓库对象（提交预览用）。 */
    public static Single<Repository> fetchRepo(String owner, String repo) {
        RepositoryService service = ServiceFactory.get(RepositoryService.class, false);
        return service.getRepository(owner, repo)
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils::doInBackground);
    }

    /** 最新入库列表（旧列表页，沿用原口径）。 */
    public static Single<List<StoreRepo>> loadListNewest() {
        return searchRepos(newestQuery(), "stars", "desc")
                .flatMap(repos -> enrich(repos, true));
    }

    /** 最近更新列表（旧列表页，沿用原口径）。 */
    public static Single<List<StoreRepo>> loadListRecent() {
        return searchRepos("stars:>100", "updated", "desc")
                .flatMap(repos -> enrich(repos, true));
    }

    /** 失败日志用：只记异常简单名与 HTTP 状态，不记仓库名与任何内容。 */
    public static String describeError(Throwable error) {
        if (error == null) {
            return "null";
        }
        String name = error.getClass().getSimpleName();
        if (error instanceof ApiRequestException) {
            name += " status=" + ((ApiRequestException) error).getStatus();
        }
        return name;
    }

    public static String newestQuery() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        return "created:>" + date + " stars:>50";
    }

    private static StoreRepo buildStoreRepo(Repository repo, Release release) {
        List<ReleaseAsset> assets = new ArrayList<>();
        EnumSet<StoreClassifier.Platform> platforms =
                EnumSet.noneOf(StoreClassifier.Platform.class);
        long downloads = 0;
        String tag = "";
        long publishedAt = 0;
        if (release != null) {
            tag = release.tagName() != null ? release.tagName() : "";
            publishedAt = release.publishedAt() != null
                    ? release.publishedAt().getTime() : 0;
            if (release.assets() != null) {
                for (ReleaseAsset asset : release.assets()) {
                    if (StoreClassifier.isInstallable(asset)) {
                        assets.add(asset);
                        platforms.addAll(StoreClassifier.platformsForAsset(asset.name()));
                        downloads += asset.downloadCount() != null
                                ? asset.downloadCount() : 0;
                    }
                }
            }
        }
        String owner = repo.owner() != null ? repo.owner().login() : "";
        return new StoreRepo(
                repo.fullName(), owner, repo.name(),
                repo.description(), repo.language(), "",
                repo.stargazersCount() != null ? repo.stargazersCount() : 0,
                repo.forksCount() != null ? repo.forksCount() : 0,
                downloads,
                repo.topics(), StoreClassifier.topicCodesFor(repo),
                tag, publishedAt, platforms, assets.size(),
                // 实时接口无后端公式字段：最热门分由 popularityScoreOrComputed()
                // 用星标 + 2×fork 现算；热门排序回落到星标。
                // 实时接口也无后端 ranks 与 category：ranks 为 null（本地回落），
                // category 为 null（用途筛时归“其他”）。
                null, null, null, null, null);
    }
}
