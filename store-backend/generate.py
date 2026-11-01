#!/usr/bin/env python3
"""OctoDroid 商店后端生成器：Komi 式预计算 + 静态 JSON。

约 6 小时由 GitHub Actions 跑一遍（对齐 Komi 离线镜像刷新节奏），把结果
推到 gh-pages 分支，App 直接取现成 JSON（feed 3 小时缓存、榜单 24 小时
缓存），不再走实时全量搜索。

数据口径（对标 Komi 的 GitHub 回退查询，2026-11-01 沙箱实测可用）：
  trending     近 30 天有提交、星标 50 以上，按星标排
  new-releases 近 14 天有提交、星标 10 以上，按更新时间排
  most-popular 半年前创建、一年内有提交、星标 1000 以上，按星标排
收录规则：只留最新 Release（第 1 页首个非草稿）带可安装附件的仓库，
附件后缀认平台（与 App 内 StoreClassifier 同表）；平台文件按精确匹配，
纯压缩包仓库只进 all（与 v4 原型行为一致）。

分类（FeedCategory，Komi 同款）：按仓库 topics 命中，见 TOPIC_TO_CODE。

用法：
  GITHUB_TOKEN=... python3 generate.py --max-candidates 100 \
      --out store-backend/output \
      --snapshot-dir app/src/main/assets/store-snapshot
无 token 时请用小 --max-candidates（未认证限速 60 次/小时）；
Actions 里用 GITHUB_TOKEN（每小时 1000 次，完整跑约 300 次）。

输出（out 下）：
  feed/{all,android,macos,windows,linux,ios}.json
  categories/{trending,new-releases,most-popular}/{platform}.json
  meta.json
快照（snapshot-dir 下，仅默认 all 视图，供 App 内置离线兜底）：
  feed_all.json, chart_trending_all.json, chart_new-releases_all.json,
  chart_most-popular_all.json
每个文件：{"generatedAt": ISO, "items": [...]}；单条含 fullName / owner /
avatarUrl / description / stars / forks / language / topics / topicCodes /
latestTag / latestDate / downloadCount / platforms。
只打数量日志，不打仓库名以外的任何内容。
"""

import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.github.com"

PLATFORMS = ["all", "android", "macos", "windows", "linux", "ios"]

# 发现页每页条数：Komi 的 BackendApiClient.getFeed limit 默认值。
PAGE_SIZE = 20

# 附件后缀 -> 平台（与 StoreClassifier.platformsForAsset 同表）。
SUFFIX_PLATFORM = [
    (".apk", "android"),
    (".ipa", "ios"),
    (".exe", "windows"),
    (".msi", "windows"),
    (".dmg", "macos"),
    (".pkg", "macos"),
    (".appimage", "linux"),
    (".deb", "linux"),
    (".rpm", "linux"),
    (".hap", "harmony"),
    (".crx", "extension"),
    (".xpi", "extension"),
    (".zip", "archive"),
    (".tar.gz", "archive"),
    (".tgz", "archive"),
    (".7z", "archive"),
]

# topics -> 分类码（Komi FeedCategory 同款集合，命中即归入，可多归）。
TOPIC_TO_CODE = {
    "ai": {"ai", "llm", "gpt", "machine-learning", "deep-learning",
           "chatgpt", "agent", "agents", "ai-agent", "generative-ai"},
    "privacy": {"privacy", "security", "encryption", "password", "vpn",
                "adblock", "ad-block", "adblocker"},
    "networking": {"networking", "network", "browser", "messaging", "chat",
                   "email", "rss", "proxy", "dns", "downloader"},
    "media": {"audio", "video", "photo", "music", "media", "player",
              "podcast", "image", "streaming", "tv"},
    "social": {"social", "mastodon", "fediverse", "forum", "community"},
    "reading": {"reader", "reading", "notes", "note", "ebook", "novel"},
    "tools": {"launcher", "backup", "self-hosted", "tool", "tools",
              "utility", "utilities", "automation"},
}


def platforms_for_asset(name):
    out = set()
    if not name:
        return out
    low = name.lower()
    for suffix, platform in SUFFIX_PLATFORM:
        if low.endswith(suffix):
            out.add(platform)
    return out


def topic_codes(topics):
    codes = set()
    for topic in topics or []:
        t = topic.lower()
        for code, members in TOPIC_TO_CODE.items():
            if t in members:
                codes.add(code)
    return sorted(codes)


class Api:
    def __init__(self, token):
        self.token = token
        self.calls = 0

    def get(self, path):
        url = API + path
        req = urllib.request.Request(url, headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "OctoDroid-Store-Backend",
            **({"Authorization": "Bearer " + self.token} if self.token else {}),
        })
        self.calls += 1
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 403:
                try:
                    body = e.read().decode("utf-8", "ignore")
                except Exception:
                    body = ""
                if "rate limit" in body.lower():
                    raise SystemExit(
                        "GitHub API 限速（已用 %d 次），请用 token 或等重置后再跑"
                        % self.calls)
            raise SystemExit("GitHub API %s 失败：%s" % (path, e))

    def search(self, query, sort, max_candidates, offset=0):
        data = self.get("/search/repositories?q=%s&sort=%s&order=desc"
                        "&per_page=100&page=1"
                        % (urllib.parse.quote(query), sort))
        items = (data or {}).get("items", [])
        return items[offset:offset + max_candidates]

    def latest_release(self, full_name):
        data = self.get("/repos/%s/releases?per_page=5" % full_name)
        if not data:
            return None
        for rel in data:
            if rel and not rel.get("draft"):
                return rel
        return None


def build_item(api, repo):
    full = repo.get("full_name", "")
    rel = api.latest_release(full)
    if rel is None:
        return None
    platforms = set()
    downloads = 0
    installable = 0
    for asset in rel.get("assets") or []:
        plats = platforms_for_asset(asset.get("name", ""))
        if plats:
            platforms.update(plats)
            downloads += asset.get("download_count") or 0
            installable += 1
    if not platforms:
        return None
    owner = repo.get("owner") or {}
    return {
        "fullName": full,
        "owner": owner.get("login", ""),
        "name": repo.get("name", ""),
        "avatarUrl": owner.get("avatar_url", ""),
        "description": repo.get("description") or "",
        "stars": repo.get("stargazers_count") or 0,
        "forks": repo.get("forks_count") or 0,
        "language": repo.get("language") or "",
        "topics": repo.get("topics") or [],
        "topicCodes": topic_codes(repo.get("topics") or []),
        "latestTag": rel.get("tag_name") or "",
        "latestDate": rel.get("published_at") or "",
        "downloadCount": downloads,
        "assetCount": installable,
        "platforms": sorted(platforms),
    }


def chart_queries(today):
    def d(days):
        return (today - datetime.timedelta(days=days)).strftime("%Y-%m-%d")
    return {
        "trending": ("stars:>50 archived:false pushed:>%s" % d(30), "stars"),
        "new-releases": ("stars:>10 archived:false pushed:>%s" % d(14), "updated"),
        "most-popular": ("stars:>1000 archived:false created:<%s pushed:>%s"
                         % (d(182), d(365)), "stars"),
    }


def sort_chart(chart, items):
    if chart == "new-releases":
        return sorted(items, key=lambda i: i["latestDate"], reverse=True)
    return sorted(items, key=lambda i: i["stars"], reverse=True)


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print("wrote %s (%d items)" % (path, len(payload.get("items", []))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-candidates", type=int, default=100)
    ap.add_argument("--offset", type=int, default=0,
                    help="跳过搜索结果前 N 个（头部仓库大多无安装包时用）")
    ap.add_argument("--max-items", type=int, default=100)
    ap.add_argument("--out", default="store-backend/output")
    ap.add_argument("--snapshot-dir",
                    default="app/src/main/assets/store-snapshot")
    args = ap.parse_args()

    api = Api(os.environ.get("GITHUB_TOKEN", ""))
    today = datetime.date.today()
    generated_at = (datetime.datetime.now(datetime.timezone.utc)
                    .isoformat().replace("+00:00", "Z"))

    charts = {}
    for chart, (query, sort) in chart_queries(today).items():
        repos = api.search(query, sort, args.max_candidates, args.offset)
        items = []
        for repo in repos:
            item = build_item(api, repo)
            if item is not None:
                items.append(item)
        items = sort_chart(chart, items)[:args.max_items]
        charts[chart] = items
        print("chart %s: %d candidates -> %d installable (api calls %d)"
              % (chart, len(repos), len(items), api.calls))

    # 发现 = 三榜合并去重，按星标排。
    merged = {}
    for items in charts.values():
        for item in items:
            merged.setdefault(item["fullName"], item)
    feed = sorted(merged.values(), key=lambda i: i["stars"],
                  reverse=True)[:args.max_items]
    print("feed: %d items (api calls %d)" % (len(feed), api.calls))

    def platform_items(items, platform):
        if platform == "all":
            return items
        return [i for i in items if platform in i["platforms"]]

    for platform in PLATFORMS:
        write_json(os.path.join(args.out, "feed", platform + ".json"),
                   {"generatedAt": generated_at,
                    "items": platform_items(feed, platform)})
        for chart, items in charts.items():
            write_json(os.path.join(args.out, "categories", chart,
                                    platform + ".json"),
                       {"generatedAt": generated_at,
                        "items": platform_items(items, platform)})

    # 发现分页（Komi 同款：每页 20 条，hasMore 翻页）：App 按页取。
    # 整包文件保留（旧版 App 与 Komi 离线镜像快照同款，不打破 0.1.53）。
    feed_pages = {}
    for platform in PLATFORMS:
        pfeed = platform_items(feed, platform)
        pages = [pfeed[i:i + PAGE_SIZE]
                 for i in range(0, len(pfeed), PAGE_SIZE)] or [[]]
        feed_pages[platform] = len(pages)
        for n, chunk in enumerate(pages, start=1):
            write_json(os.path.join(args.out, "feed", platform,
                                    "page%d.json" % n),
                       {"generatedAt": generated_at, "page": n,
                        "hasMore": n < len(pages), "items": chunk})

    meta = {"generatedAt": generated_at,
            "feed": len(feed),
            "feedPages": feed_pages,
            "charts": {c: len(v) for c, v in charts.items()},
            "apiCalls": api.calls}
    write_json(os.path.join(args.out, "meta.json"), meta)

    if args.snapshot_dir and args.snapshot_dir != "/dev/null":
        write_json(os.path.join(args.snapshot_dir, "feed_all.json"),
                   {"generatedAt": generated_at, "items": feed})
        for chart, items in charts.items():
            write_json(os.path.join(args.snapshot_dir,
                                    "chart_%s_all.json" % chart),
                       {"generatedAt": generated_at, "items": items})
    print("done, total api calls %d" % api.calls)


if __name__ == "__main__":
    main()
