#!/usr/bin/env python3
"""OctoDroid 商店后端生成器：Komi 式预计算 + 静态 JSON。

每小时由 GitHub Actions 跑一遍，把结果推到 gh-pages 分支，App 直接取
现成 JSON（feed 3 小时缓存、榜单 24 小时缓存），不再走实时全量搜索。

数据口径（对标 Komi 的 GitHub 回退查询，2026-11-01 沙箱实测可用）：
  trending     两条查询各取一半（总数仍 300）：(a) 近 30 天有提交、星标
               50 以上，按星标排；(b) 90 天内创建、星标 50 以上，按星标排
  new-releases 近 14 天有提交、星标 10 以上，按更新时间排；验 release 后
               只留 14 天内真正发过版的，按发版时间排
  most-popular 一年内有提交、星标 1000 以上，按星标排（2026-11-08 起
               去掉“半年前创建”限制，年轻大库也可进榜）
收录规则：只留最新 Release（第 1 页首个非草稿）带可安装附件的仓库，
附件后缀认平台（与 App 内 StoreClassifier 同表）；平台文件按精确匹配，
纯压缩包仓库只进 all（与 v4 原型行为一致）。

排序公式（2026-11-08 完整版，见 docs/research/OctoDroid/
store-formula-complete.html）：
  最热门 popularityScore = 星标 + 2×fork，降序（照抄 Komi）
  热门   镜像条目按自带 trendingScore 降序在前；生成器条目无 7 天历史时
         按 stars_per_day（星标 ÷ 建库天数）排在其后，有 7 天历史时按
         trendingScore = 20 × 近 7 日均涨星，与镜像同场按分排
  最新发布 只留 14 天内发过版的，按发版时间降序
镜像字段原样保留：热门镜像的 trendingScore、最热门镜像的
popularityScore、发现 feed 文件里两者皆有则皆留；镜像缺
popularityScore 时后端按星标 + 2×fork 补算，不留给 App 算。
生成器条目必带 stars_per_day 与 popularityScore；无 7 天历史时不写
trendingScore（App 靠“有没有这个字段”区分镜像/生成器条目）。

历史快照（7 天冷启动，零新增 API 调用）：每轮把输出仓库的当前星标数
写成 history/<当天日期>.json（--history-dir 指定目录）；下轮读 7 天前
的快照，星标差 ÷ 7 即近 7 日均涨星。快照与数据一起放 gh-pages 的
store/history/，git 拉取不占 API 额度；攒够 7 天前生成器热门条目切到
真·近期日均，之前用 stars_per_day 顶替，新库无历史时同样顶替。

扩量（2026-11-07）：每轮另并 Komi 公开离线镜像
（komi-store/komi-store-backend-data/cached-data/，raw 直链、无需鉴权、
不占 GitHub API 额度）17 个文件：feed 的 all/android/linux/macos/
windows.json，三榜 trending/most-popular/new-releases 各 android/linux/
macos/windows.json。按 fullName（小写）去重，生成器实时验过的优先；
镜像拉不到时只出生成器数据，整轮不失败。

分类（FeedCategory，Komi 同款）：按仓库 topics 命中，见 TOPIC_TO_CODE。

用法：
  GITHUB_TOKEN=... python3 generate.py --max-candidates 300 \
      --max-items 300 \
      --out store-backend/output \
      --history-dir store-backend/history \
      --snapshot-dir app/src/main/assets/store-snapshot
无 token 时请用小 --max-candidates（未认证限速 60 次/小时）；
Actions 里用 GITHUB_TOKEN（每小时 1000 次；300 候选 × 3 榜约 900 次
release 检查＋约 10 次搜索分页，逼近上限，故候选数不设 1000）。
--max-candidates 是每榜总数：热门两条查询平分（如 300 -> 150+150）。
--max-items 只 cap 生成器自产的每榜条数；并入镜像后的三榜与发现不
再 cap（镜像三榜去重后已有 490/580/460 条，cap 住就白并了），
发现 = 三榜并集去重＋镜像 feed 文件的独有条目，按星标排。

输出（out 下）：
  feed/{all,android,macos,windows,linux,ios}.json
  feed/{all,android,macos,windows,linux,ios}/page<n>.json（20 条一页）
  categories/{trending,new-releases,most-popular}/{platform}.json
  meta.json
历史快照（history-dir 下，文件名 <YYYY-MM-DD>.json）：
  {"date": ..., "generatedAt": ..., "stars": {"owner/repo": 星标数}}
  当天快照只写、不读；读的是 7 天前的那一份。
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
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.github.com"

# Komi 公开离线镜像根地址（raw 直链，无需鉴权，不占 API 额度）。
KOMI_BASE = ("https://raw.githubusercontent.com/komi-store/"
             "komi-store-backend-data/main/cached-data/")
KOMI_CHARTS = ["trending", "most-popular", "new-releases"]
KOMI_PLATFORMS = ["android", "linux", "macos", "windows"]
KOMI_FEED_PLATFORMS = ["all", "android", "linux", "macos", "windows"]
# 镜像里只有这四个分平台文件；iOS 只从生成器自产数据里出。
MIRROR_PLATFORMS = ["android", "windows", "macos", "linux"]

PLATFORMS = ["all", "android", "macos", "windows", "linux", "ios"]

# 发现页每页条数：Komi 的 BackendApiClient.getFeed limit 默认值。
PAGE_SIZE = 20

# 历史快照窗口（天）与 trendingScore 换算系数（Komi 反推：20×日均涨星）。
HISTORY_WINDOW_DAYS = 7
TRENDING_FACTOR = 20
# 最新发布：只留该天数内真正发过版的。
NEW_RELEASES_DAYS = 14

# release 检查之间的延迟（秒）：平滑调用节奏，避开次级限速。
RELEASE_DELAY = 0.05
# 403/429 限速重试：最多 3 次，按 Retry-After / X-RateLimit-Reset 退避，
# 单次等待上限 300 秒。
RATE_LIMIT_RETRIES = 3
RATE_LIMIT_WAIT_CAP = 300

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


def normalize_date(value):
    """把 Komi 镜像的日期统一成 App 能解析的 ISO Z 格式。

    镜像里两种写法："2026-08-22 14:05:19+00"（feed 文件）与
    "2026-11-05T09:48:37Z"（榜单文件）；App 的 StoreRepo.parseDate
    只认 yyyy-MM-dd'T'HH:mm:ss'Z'（可带 .SSS），故必须归一化。
    解析失败返回 ""，不让单条坏日期拖垮整轮。
    """
    if not value:
        return ""
    s = str(value).strip()
    if not s:
        return ""
    # 统一后缀：Z / +00 / +00:00 -> +0000；空格分隔 -> T 分隔。
    s = re.sub(r"([+-]\d{2}):(\d{2})$", r"\1\2", s)
    s = re.sub(r"([+-]\d{2})$", r"\g<1>00", s)
    if s.endswith("Z"):
        s = s[:-1] + "+0000"
    s = s.replace(" ", "T", 1)
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z",
                "%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S"):
        try:
            dt = datetime.datetime.strptime(s, fmt)
            break
        except ValueError:
            continue
    else:
        return ""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=datetime.timezone.utc)
    return dt.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_iso_datetime(value):
    """把 ISO 时间解析成带时区的 datetime；失败返回 None。"""
    if not value:
        return None
    s = str(value).strip()
    if not s:
        return None
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        dt = datetime.datetime.fromisoformat(s)
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=datetime.timezone.utc)
    return dt


def stars_per_day(stars, created_at, now):
    """终身日均涨星：星标 ÷ 建库天数（浮点天数，下限 1 天防除零）。

    创建时间解析失败时按 1 天计，不让单条坏数据拖垮整轮。
    """
    created = parse_iso_datetime(created_at)
    if created is None:
        days = 1.0
    else:
        days = max(1.0, (now - created).total_seconds() / 86400.0)
    return (stars or 0) / days


def popularity_score(stars, forks):
    """Komi 最热门公式（实测精确成立）：星标 + 2×fork。"""
    return (stars or 0) + 2 * (forks or 0)


def is_recent_release(item, cutoff):
    """最新 release 发布时间是否在 cutoff 之后；时间缺失或解析失败
    视为不在（无法证明 14 天内发过版）。"""
    published = parse_iso_datetime(item.get("latestDate"))
    return published is not None and published >= cutoff


def load_history_snapshot(history_dir, day):
    """读某天的星标快照，返回 {小写 fullName: 星标数}；文件缺失或损坏
    返回 None（调用方按“无历史”处理，不写 trendingScore）。"""
    if not history_dir:
        return None
    path = os.path.join(history_dir, day.isoformat() + ".json")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("history %s 损坏（按无历史处理）：%s" % (path, e))
        return None
    stars = (data or {}).get("stars") or {}
    return {str(k).lower(): v for k, v in stars.items()
            if isinstance(v, (int, float))}


def write_history_snapshot(history_dir, day, generated_at, stars):
    """写当天星标快照；history_dir 为空时不写。"""
    if not history_dir:
        return
    os.makedirs(history_dir, exist_ok=True)
    path = os.path.join(history_dir, day.isoformat() + ".json")
    payload = {"date": day.isoformat(), "generatedAt": generated_at,
               "stars": {k: stars[k] for k in sorted(stars)}}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print("wrote %s (%d repos)" % (path, len(stars)))


class Api:
    def __init__(self, token):
        self.token = token
        self.calls = 0
        self.release_cache = {}

    def get(self, path):
        url = API + path
        last_error = None
        for attempt in range(RATE_LIMIT_RETRIES + 1):
            req = urllib.request.Request(url, headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "OctoDroid-Store-Backend",
                **({"Authorization": "Bearer " + self.token}
                   if self.token else {}),
            })
            self.calls += 1
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    return json.load(resp)
            except urllib.error.HTTPError as e:
                last_error = e
                if e.code == 404:
                    return None
                body = ""
                try:
                    body = e.read().decode("utf-8", "ignore")
                except Exception:
                    body = ""
                retry_after = e.headers.get("Retry-After") if e.headers else None
                reset = (e.headers.get("X-RateLimit-Reset")
                         if e.headers else None)
                is_rate = (e.code in (403, 429)
                           and ("rate limit" in body.lower()
                                or "secondary" in body.lower()
                                or e.code == 429
                                or retry_after is not None))
                if is_rate and attempt < RATE_LIMIT_RETRIES:
                    wait = None
                    if retry_after:
                        try:
                            wait = int(retry_after)
                        except ValueError:
                            wait = None
                    if wait is None and reset:
                        try:
                            wait = int(reset) - int(time.time())
                        except ValueError:
                            wait = None
                    if wait is None:
                        wait = 30 * (2 ** attempt)
                    wait = max(5, min(wait, RATE_LIMIT_WAIT_CAP))
                    print("GitHub API 限速（已用 %d 次），%d 秒后重试 "
                          "(%d/%d)：%s"
                          % (self.calls, wait, attempt + 1,
                             RATE_LIMIT_RETRIES, path))
                    time.sleep(wait)
                    continue
                if e.code == 403 and "rate limit" in body.lower():
                    raise SystemExit(
                        "GitHub API 限速（已用 %d 次），请用 token 或等重置后再跑"
                        % self.calls)
                raise SystemExit("GitHub API %s 失败：%s" % (path, e))
        raise SystemExit("GitHub API %s 失败：%s" % (path, last_error))

    def search(self, query, sort, max_candidates, offset=0):
        """搜索翻页取满：per_page=100，翻到 max_candidates 或 10 页为止。

        GitHub 搜索每查询最多返回 1000 条，故最多 10 页；offset 在
        跨页拼接后的流上跳过。
        """
        collected = []
        want = offset + max_candidates
        for page in range(1, 11):
            if len(collected) >= want:
                break
            data = self.get("/search/repositories?q=%s&sort=%s&order=desc"
                            "&per_page=100&page=%d"
                            % (urllib.parse.quote(query), sort, page))
            items = (data or {}).get("items", [])
            if not items:
                break
            collected.extend(items)
            if len(items) < 100:
                break
        return collected[offset:offset + max_candidates]

    def latest_release(self, full_name):
        key = full_name.lower()
        if key in self.release_cache:
            return self.release_cache[key]
        time.sleep(RELEASE_DELAY)
        data = self.get("/repos/%s/releases?per_page=5" % full_name)
        rel = None
        if data:
            for r in data:
                if r and not r.get("draft"):
                    rel = r
                    break
        self.release_cache[key] = rel
        return rel


def build_item(api, repo, now=None, history_old=None):
    """生成器自产条目：验最新 release、可安装才留。

    必带 stars_per_day（终身日均涨星）与 popularityScore（星标+2×fork，
    数据现成、零新增调用）；7 天历史快照里有该仓库时才写 trendingScore
    = 20 × 近 7 日均涨星，无历史时不写该字段（App 靠有无此字段区分
    镜像/生成器条目），新库无历史时用 stars_per_day 顶替排序。
    """
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
    stars = repo.get("stargazers_count") or 0
    forks = repo.get("forks_count") or 0
    item = {
        "fullName": full,
        "owner": owner.get("login", ""),
        "name": repo.get("name", ""),
        "avatarUrl": owner.get("avatar_url", ""),
        "description": repo.get("description") or "",
        "stars": stars,
        "forks": forks,
        "language": repo.get("language") or "",
        "topics": repo.get("topics") or [],
        "topicCodes": topic_codes(repo.get("topics") or []),
        "latestTag": rel.get("tag_name") or "",
        "latestDate": rel.get("published_at") or "",
        "downloadCount": downloads,
        "assetCount": installable,
        "platforms": sorted(platforms),
        "stars_per_day": round(stars_per_day(
            stars, repo.get("created_at"), now or datetime.datetime.now(
                datetime.timezone.utc)), 4),
        "popularityScore": popularity_score(stars, forks),
    }
    if history_old is not None:
        old = history_old.get(full.lower())
        if isinstance(old, (int, float)):
            recent = max(0.0, (stars - old) / HISTORY_WINDOW_DAYS)
            item["trendingScore"] = round(TRENDING_FACTOR * recent, 2)
    return item


def komi_item(repo):
    """把一条 Komi 镜像仓库映射到咱们的 item 结构；无安装包则返回 None。

    字段映射：fullName / stargazersCount->stars / forksCount->forks /
    latestReleaseTag->latestTag（榜单文件里没有，从 platformReleases
    里取发布时间最晚的 tag）/ latestReleaseDate->latestDate（归一化
    成 ISO Z）/ downloadCount 原样 / owner 头像->avatarUrl /
    topics/topicCodes 原样（榜单文件里没有 topicCodes 时按 topics
    用咱们的 TOPIC_TO_CODE 现算）/ hasInstallers* 与 platformReleases
    并集 -> platforms（只取 android/windows/macos/linux 四值）/
    assetCount：镜像不给附件数，按平台数计。
    注意 downloadCount 口径与生成器不同：镜像是累计量，生成器是
    最新 Release 可安装附件量之和。
    """
    full = repo.get("fullName", "")
    if not full:
        return None
    platforms = set()
    for p in MIRROR_PLATFORMS:
        if repo.get("hasInstallers" + p.capitalize()):
            platforms.add(p)
    releases = repo.get("platformReleases") or {}
    for p in MIRROR_PLATFORMS:
        if p in releases:
            platforms.add(p)
    if not platforms:
        return None
    tag = repo.get("latestReleaseTag") or ""
    if not tag and releases:
        latest = max(releases.values(),
                     key=lambda r: (r or {}).get("publishedAt") or "")
        tag = (latest or {}).get("tag") or ""
    topics = repo.get("topics") or []
    codes = repo.get("topicCodes")
    if not isinstance(codes, list):
        codes = topic_codes(topics)
    owner = repo.get("owner") or {}
    stars = repo.get("stargazersCount") or 0
    forks = repo.get("forksCount") or 0
    item = {
        "fullName": full,
        "owner": owner.get("login", ""),
        "name": repo.get("name") or full.split("/")[-1],
        "avatarUrl": owner.get("avatarUrl", ""),
        "description": repo.get("description") or "",
        "stars": stars,
        "forks": forks,
        "language": repo.get("language") or "",
        "topics": topics,
        "topicCodes": codes,
        "latestTag": tag,
        "latestDate": normalize_date(repo.get("latestReleaseDate")),
        "downloadCount": repo.get("downloadCount") or 0,
        "assetCount": len(platforms),
        "platforms": sorted(platforms),
    }
    # 镜像分数原样保留，有什么留什么，绝不丢字段（实测：热门文件带
    # trendingScore、最热门文件带 popularityScore、发现 feed 文件两者
    # 皆有、最新发布文件两者皆无）。镜像缺 popularityScore 时后端按
    # 星标 + 2×fork 补算，不留给 App 算。
    if isinstance(repo.get("trendingScore"), (int, float)):
        item["trendingScore"] = repo["trendingScore"]
    if isinstance(repo.get("popularityScore"), (int, float)):
        item["popularityScore"] = repo["popularityScore"]
    else:
        item["popularityScore"] = popularity_score(stars, forks)
    return item


def fetch_komi_file(base, path):
    """拉一个镜像文件；失败只记日志、返回空列表，绝不抛错。"""
    url = base + path
    req = urllib.request.Request(url, headers={
        "User-Agent": "OctoDroid-Store-Backend",
    })
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.load(resp)
    except Exception as e:
        print("komi mirror %s 拉取失败（跳过此文件）：%s" % (path, e))
        return None
    if not isinstance(data, dict):
        print("komi mirror %s 结构异常（跳过此文件）" % path)
        return None
    return data.get("repositories") or []


def fetch_komi(base):
    """拉 17 个镜像文件，返回（每榜去重字典，每榜一个；feed 去重字典；
    拉到的文件数；文件总数）。任一文件失败只跳过该文件，整轮不失败。"""
    mirror_charts = {c: {} for c in KOMI_CHARTS}
    mirror_feed = {}
    total_files = 0
    ok_files = 0
    if not base:
        print("komi mirror 已禁用（--komi-base 为空），只出生成器数据")
        return mirror_charts, mirror_feed, ok_files, total_files
    if not base.endswith("/"):
        base += "/"
    for chart in KOMI_CHARTS:
        for platform in KOMI_PLATFORMS:
            total_files += 1
            repos = fetch_komi_file(base, "%s/%s.json" % (chart, platform))
            if repos is None:
                continue
            ok_files += 1
            for repo in repos:
                item = komi_item(repo)
                if item is not None:
                    mirror_charts[chart].setdefault(
                        item["fullName"].lower(), item)
    for platform in KOMI_FEED_PLATFORMS:
        total_files += 1
        repos = fetch_komi_file(base, "feed/%s.json" % platform)
        if repos is None:
            continue
        ok_files += 1
        for repo in repos:
            item = komi_item(repo)
            if item is not None:
                mirror_feed.setdefault(item["fullName"].lower(), item)
    print("komi mirror: %d/%d 文件拉到" % (ok_files, total_files))
    return mirror_charts, mirror_feed, ok_files, total_files


def chart_queries(today):
    """每榜一条或多条（查询，排序）。

    热门两条查询平分候选数：(a) 原查询（30 天内有更新）抓稳态热门，
    (b) 90 天内创建抓年轻快涨的库；最热门去掉 created 半年限制。
    """
    def d(days):
        return (today - datetime.timedelta(days=days)).strftime("%Y-%m-%d")
    return {
        "trending": [
            ("stars:>50 archived:false pushed:>%s" % d(30), "stars"),
            ("stars:>50 archived:false created:>%s" % d(90), "stars"),
        ],
        "new-releases": [
            ("stars:>10 archived:false pushed:>%s" % d(14), "updated"),
        ],
        "most-popular": [
            ("stars:>1000 archived:false pushed:>%s" % d(365), "stars"),
        ],
    }


def sort_chart(chart, items):
    if chart == "new-releases":
        return sorted(items, key=lambda i: i["latestDate"], reverse=True)
    if chart == "most-popular":
        return sorted(items,
                      key=lambda i: i.get("popularityScore")
                      if isinstance(i.get("popularityScore"), (int, float))
                      else i["stars"],
                      reverse=True)
    if chart == "trending":
        # 有 trendingScore 的（镜像条目；攒够 7 天历史后的生成器条目）
        # 按分降序在前；没有的（无历史的生成器条目）按 stars_per_day
        # 降序排在其后，缺 stars_per_day 的镜像条目按星标顶替。
        with_score = [i for i in items
                      if isinstance(i.get("trendingScore"), (int, float))]
        without = [i for i in items
                   if not isinstance(i.get("trendingScore"), (int, float))]
        with_score.sort(key=lambda i: i["trendingScore"], reverse=True)
        without.sort(key=lambda i: i.get("stars_per_day")
                     if isinstance(i.get("stars_per_day"), (int, float))
                     else i["stars"],
                     reverse=True)
        return with_score + without
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
    ap.add_argument("--max-items", type=int, default=100,
                    help="只 cap 生成器自产的每榜条数；并入镜像后的三榜与"
                         "发现不再 cap")
    ap.add_argument("--komi-base", default=KOMI_BASE,
                    help="Komi 镜像根地址；传空字符串表示不并镜像")
    ap.add_argument("--history-dir", default=None,
                    help="历史快照目录：读 7 天前的星标快照，写当天的；"
                         "不传则不读写快照，生成器条目不写 trendingScore")
    ap.add_argument("--out", default="store-backend/output")
    ap.add_argument("--snapshot-dir",
                    default="app/src/main/assets/store-snapshot")
    args = ap.parse_args()

    api = Api(os.environ.get("GITHUB_TOKEN", ""))
    today = datetime.date.today()
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    generated_at = now_utc.isoformat().replace("+00:00", "Z")

    # 历史快照：只读 7 天前那一份（没有则本轮生成器条目不写
    # trendingScore，用 stars_per_day 顶替排序）。
    history_old_date = today - datetime.timedelta(days=HISTORY_WINDOW_DAYS)
    history_old = load_history_snapshot(args.history_dir, history_old_date)
    print("history: %s %s (%d repos)"
          % (history_old_date.isoformat(),
             "found" if history_old is not None else "missing",
             len(history_old) if history_old is not None else 0))

    # 先并镜像（公开 raw 直链，不占 API 额度；失败只出生成器数据）。
    mirror_charts, mirror_feed, mirror_ok, mirror_total = \
        fetch_komi(args.komi_base)
    mirror_union = set()
    for chart in KOMI_CHARTS:
        mirror_union |= set(mirror_charts[chart])
    mirror_union |= set(mirror_feed)

    charts = {}
    gen_union = set()
    for chart, queries in chart_queries(today).items():
        # --max-candidates 是每榜总数，多条查询平分（余数给前面的查询；
        # 热门 300 -> 150+150，试跑 15 -> 8+7）。
        nq = len(queries)
        per_query = [args.max_candidates // nq] * nq
        for i in range(args.max_candidates % nq):
            per_query[i] += 1
        repos_all = []
        for (query, sort), limit in zip(queries, per_query):
            got = api.search(query, sort, limit, args.offset)
            print("chart %s query %s -> %d candidates"
                  % (chart, query, len(got)))
            repos_all.extend(got)
        # 两条查询去重后验 release（release 缓存按 fullName 去重，
        # 调用数不翻倍）。
        seen = set()
        repos = []
        for repo in repos_all:
            key = (repo.get("full_name") or "").lower()
            if key and key not in seen:
                seen.add(key)
                repos.append(repo)
        items = []
        for repo in repos:
            item = build_item(api, repo, now=now_utc,
                              history_old=history_old)
            if item is not None:
                items.append(item)
        if chart == "new-releases":
            # 只留 14 天内真正发过版的（用最新 release 发布时间判断；
            # “有更新”不等于“发了新版”）。
            cutoff = now_utc - datetime.timedelta(days=NEW_RELEASES_DAYS)
            before = len(items)
            items = [i for i in items if is_recent_release(i, cutoff)]
            print("chart new-releases: 14-day release filter dropped %d"
                  % (before - len(items)))
        items = sort_chart(chart, items)[:args.max_items]
        for item in items:
            gen_union.add(item["fullName"].lower())
        # 并镜像：生成器实时验过的优先（同名覆盖镜像条目），但镜像的
        # trendingScore 必须保留：Komi 分与生成器自算分窗口不同（Komi
        # 窗口未知，生成器是 20×近 7 日均涨星），不能混用；镜像有分
        # 的以镜像为准，生成器自算分只留给镜像里没有的仓库。
        merged = dict(mirror_charts[chart])
        for item in items:
            key = item["fullName"].lower()
            mirror_item = merged.get(key)
            if (mirror_item is not None
                    and isinstance(mirror_item.get("trendingScore"),
                                   (int, float))):
                item["trendingScore"] = mirror_item["trendingScore"]
            merged[key] = item
        merged_items = list(merged.values())
        if chart == "new-releases":
            # 合并后的输出同样只留 14 天内发过版的，保证输出里没有
            # 14 天前发版的条目。
            cutoff = now_utc - datetime.timedelta(days=NEW_RELEASES_DAYS)
            before = len(merged_items)
            merged_items = [i for i in merged_items
                            if is_recent_release(i, cutoff)]
            print("chart new-releases merged: 14-day filter dropped %d"
                  % (before - len(merged_items)))
        merged_items = sort_chart(chart, merged_items)
        charts[chart] = merged_items
        print("chart %s: %d candidates (%d unique) -> %d installable "
              "(generator) + %d mirror -> %d merged (api calls %d)"
              % (chart, len(repos_all), len(repos), len(items),
                 len(mirror_charts[chart]), len(merged_items), api.calls))

    # 发现 = 三榜合并去重，按星标排；再补镜像 feed 文件里的独有条目。
    merged = {}
    for items in charts.values():
        for item in items:
            merged.setdefault(item["fullName"].lower(), item)
    for key, item in mirror_feed.items():
        merged.setdefault(key, item)
    feed = sorted(merged.values(), key=lambda i: i["stars"], reverse=True)
    print("feed: %d items (generator %d unique, mirror %d unique, "
          "api calls %d)"
          % (len(feed), len(gen_union), len(mirror_union), api.calls))

    # 写当天星标快照（供 7 天后算近 7 日均涨星）；只写当天、不读当天。
    history_stars = {i["fullName"].lower(): i["stars"] for i in feed}
    write_history_snapshot(args.history_dir, today, generated_at,
                           history_stars)

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
            "apiCalls": api.calls,
            "mirrorFilesOk": "%d/%d" % (mirror_ok, mirror_total),
            "mirrorItems": len(mirror_union),
            "generatorItems": len(gen_union),
            "historyDate": today.isoformat(),
            "historyOldDate": history_old_date.isoformat(),
            "historyOldFound": history_old is not None,
            "historyItems": len(history_stars)}
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
