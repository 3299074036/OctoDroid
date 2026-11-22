#!/usr/bin/env python3
"""OctoDroid 商店后端生成器：hiwiki 全对上（严格版）。

每天 0 点、12 点（北京时间）由 GitHub Actions 跑一遍，把结果推到
gh-pages 分支，App 直接取现成 JSON（feed 3 小时缓存、榜单 24 小时
缓存），不再走实时全量搜索。

严格版（2026-11-08，用户定：全对上）：
  池子   feed = hiwiki 当次重抓的可安装池（2,457 条，2026-11-08 数）；
         我方独有的 1,408 条已删。搜索管线与 Komi 镜像整段已删。
  榜单   每条 item 带 ranks：hiwiki subgroup 在 96 榜内时只进该榜；
         不在对照表里就是 hiwiki 的权威结论“不给榜”，返回 []，不回
         落静态表/关键词规则（静态表与规则只对非 hiwiki 来源生效，
         严格版下不存在，仅作防线）。
  排序   榜内顺序由 App 按纯星标排（后端不管）；三榜（热门／最新发布
         ／最热门）由同一份池子数据重算，零新增 API 调用。
  新鲜度 对照表与池子每轮都重抓（harvest-hiwiki.py）；重抓失败不拦整
         轮——回落到上一轮留档或提交的兜底对照表。

数据口径：
  收录规则：只留 release 里带可安装附件的仓库。release 取最新非
    prerelease 且带 ≥1 可安装附件的那一个（按 releases[] 最新在前
    扫描；全部 release 都没有可安装附件时丢弃该仓并记日志）。附件
    后缀认平台（与 App 内 StoreClassifier 同表）；纯压缩包仓库只进
    all。
  三榜规则（现行口径平移）：热门 星标>50 且 pushedAt 在 30 天内；
    最新发布 星标>10 且 14 天内真正发过版（按条目 latestDate 判断，
    即所取 release 的发布时间）；最热门 星标>1000 且 pushedAt 在
    1 年内。
  排序公式：最热门 popularityScore = 星标 + 2×fork；热门 有
    trendingScore 的按分降序在前，没有的按 stars_per_day 降序排其
    后，缺 stars_per_day 的按星标顶替；最新发布按发版时间降序。

hiwiki 字段（payload 实测）：星标／语言／简介／头像／推送时间／
release（含附件文件名与下载量）全部信 hiwiki，0 次 GitHub API 调
用；只补三个 hiwiki 没有的字段——forks／topics／created_at：上一
轮 feed 里有的直接沿用，没有的调 1 次 GET /repos/{full}，硬上限
--metadata-cap（默认 4500）触顶后剩余新仓以残缺字段进 feed
（forks=0、topics=[]、无 created_at），整轮不失败。

历史快照（7 天冷启动，零新增 API 调用）：每轮把输出仓库的当前星标数
写成 history/<当天日期>.json（--history-dir 指定目录）；下轮读 7 天前
的快照，星标差 ÷ 7 即近 7 日均涨星。快照与数据一起放 gh-pages 的
store/history/，git 拉取不占 API 额度；攒够 7 天前生成器热门条目切到
真·近期日均，之前用 stars_per_day 顶替，新库无历史时同样顶替。

用法（工作流）：
  STORE_PAT=<STORE_PAT> python3 generate.py \
      --hiwiki-pool hiwiki-data/hiwiki-pool.json \
      --hiwiki-ranks hiwiki-data/hiwiki-ranks.json \
      --prev-feed prev-feed.json \
      --metadata-cap 4500 --harvest-status ok \
      --history-dir store-history \
      --out store-output --snapshot-dir /dev/null
离线模拟（核数用，不调 api.github.com）：
  python3 generate.py --simulate \
      --hiwiki-pool <分类 JSON> --hiwiki-ranks <对照表> \
      --prev-feed <上一轮 feed/all.json> \
      --out /tmp/sim-out --snapshot-dir /dev/null
  模拟下新仓元数据一律残缺（forks=0、topics=[]、无 created_at），
  无 release 数据的记录降级拼条目（平台沿用上一轮 feed、没有则空）；
  计数（feed／ranked／unranked／非空榜）与榜单归属仍可核对。

输出（out 下）：
  feed/{all,android,macos,windows,linux,ios}.json
  feed/{all,android,macos,windows,linux,ios}/page<n>.json（20 条一页）
  feed/newest.json（最新入库：harvest 的 hiwiki-newest.json 按入库
    顺序原样出，每条含 fullName / description / category /
    language / stars / latestTag / lastReleaseTime / platforms；
    平台以 feed 为准，feed 里没有该仓时把 hiwiki 原始平台值映射
    兜底。新鲜文件缺失时沿用上一轮的（meta newestStatus=stale），
    两者都没有则不写本文件——三种情况都不拦 feed，整轮不失败）
  categories/{trending,new-releases,most-popular}/{platform}.json
  meta.json（含 poolSize / hiwikiItems / ranked / unranked /
  mappingDate / harvestStatus / apiCalls / droppedNoInstallable /
  metadataDegraded / newestCount / newestStatus / newestInFeed /
  newestDate）
历史快照（history-dir 下，文件名 <YYYY-MM-DD>.json）：
  {"date": ..., "generatedAt": ..., "stars": {"owner/repo": 星标数}}
  当天快照只写、不读；读的是 7 天前的那一份。
快照（snapshot-dir 下，仅默认 all 视图，供 App 内置离线兜底）：
  feed_all.json, chart_trending_all.json, chart_new-releases_all.json,
  chart_most-popular_all.json
每个文件：{"items": [...]}（80：generatedAt 只写 meta.json，内容不变
时文件字节不变，gh-pages 不空提交；App 不读 feed 的 generatedAt）；
meta.json 本身：数据字段与上一轮逐项相等时沿用上一轮原样写出
（见 main 末尾 80 注释，generatedAt/mappingDate/apiCalls 为运行期
量、不参与比较），否则写新 meta。
单条含 fullName / owner /
avatarUrl / description / stars / forks / language / topics / topicCodes /
latestTag / latestDate / pushedAt / downloadCount / platforms / source /
ranks（createdAt 仅后端复用时写，App 不读）。只打数量日志，不打仓库名
以外的任何内容。
"""

import argparse
import datetime
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"

# 三榜顺序（输出文件与 meta.json 的榜序与现行一致）。
CHARTS = ["trending", "new-releases", "most-popular"]

# 81：harmony/extension 已补（App 端 PLATFORM_KEYS 有这两档）；archive
# 不出分包——App 无 archive 平台档，archive 条目仍走 all.json 客户端筛。
PLATFORMS = ["all", "android", "macos", "windows", "linux", "ios",
             "harmony", "extension"]

# 82：用途 category 白名单（与 App 端 StoreView.FEED_CATS 一致）。
# hiwiki 若新增/改名大类（如大小写变化），未知值归 "other" 并记日志
# 告警，避免该类仓库进不了用途网格（仍在"全部"里）。
CATEGORIES = {"ai", "media", "network", "tools", "productivity",
              "development", "game", "other"}


def normalize_category(value):
    c = (value or "").strip().lower()
    if c in CATEGORIES:
        return c
    if c:
        print("category 未知，归 other：%s" % value)
    return "other"


def as_int(value, field, full=""):
    """145：数值字段安全取整。hiwiki 字段类型一变（如 stars 变字符串），
    直接参与排序/比较会抛 TypeError 中断整轮；数字字符串正常转换，
    非法值按 0 计并记日志（与 forks/topics/createdAt 的 isinstance
    检查同款口径）。"""
    if isinstance(value, bool):
        return 0
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        try:
            return int(value.strip())
        except ValueError:
            pass
    print("WARN %s: %s=%r 非数值，按 0 计" % (full, field, value))
    return 0

# 发现页每页条数：Komi 的 BackendApiClient.getFeed limit 默认值。
PAGE_SIZE = 20

# 历史快照窗口（天）与 trendingScore 换算系数（Komi 反推：20×日均涨星）。
HISTORY_WINDOW_DAYS = 7
TRENDING_FACTOR = 20
# 最新发布：只留该天数内真正发过版的。
NEW_RELEASES_DAYS = 14

# 403/429 限速重试：最多 3 次，按 Retry-After / X-RateLimit-Reset 退避，
# 单次等待上限 300 秒。
RATE_LIMIT_RETRIES = 3
RATE_LIMIT_WAIT_CAP = 300

# hiwiki 最新入库原始平台值 -> feed 平台词表。只在 feed 里找不到
# 该仓时兜底用（feed 的平台由附件后缀推导，权威；实测 hiwiki 原始
# 值与 feed 有出入：hiwiki 没有 archive 概念、mac 写作 mac、部分
# 平台是宣称值而非附件实测值，11/60 条直接映射会对不上 feed）。
# 词表外的值丢弃（例如 hiwiki 某天新增的平台名）。
# 143：stale 路径传的是上一轮 feed/newest.json 的条目，其 platforms
# 已是 feed 词表值；"macos"/"archive" 须直通，否则在 else 分支被静默
# 丢弃（新鲜路径传 hiwiki 原始值 "mac"/"other"，映射不变）。
HIWIKI_NEWEST_PLATFORM_MAP = {
    "android": "android", "ios": "ios", "windows": "windows",
    "mac": "macos", "linux": "linux", "extension": "extension",
    "harmony": "harmony", "other": "archive",
    "macos": "macos", "archive": "archive",
}

# 附件后缀 -> 平台（与 StoreClassifier.platformsForAsset 同表）。
SUFFIX_PLATFORM = [
    (".apk", "android"),
    (".ipa", "ios"),
    (".exe", "windows"),
    (".msi", "windows"),
    (".msix", "windows"),
    (".appx", "windows"),
    (".ps1", "windows"),
    (".dmg", "macos"),
    (".pkg", "macos"),
    (".appimage", "linux"),
    (".deb", "linux"),
    (".rpm", "linux"),
    (".sh", "linux"),
    (".hap", "harmony"),
    (".crx", "extension"),
    (".xpi", "extension"),
    (".zip", "archive"),
    (".tar.gz", "archive"),
    (".tgz", "archive"),
    (".7z", "archive"),
    (".whl", "archive"),
    (".jar", "archive"),
    (".war", "archive"),
    (".iso", "archive"),
    (".img", "archive"),
    (".xz", "archive"),
    (".bz2", "archive"),
    (".rar", "archive"),
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


# ---------------------------------------------------------------------------
# 排行榜分组：动态，榜单全集每轮现取自 hiwiki（2026-11-22 起，
# 不再写死 96 榜——hiwiki 新增榜单曾被静态表过滤掉）。
#
# 事实来源：harvest 每轮拉 hiwiki 榜单页（/zh-CN/rank）出的
# hiwiki-boards.json：[{"label": 榜单中文名, "category": hiwiki 大类}]。
# 榜单页失败时 harvest 按池子回落（subgroup 仓数 ≥5 即为榜，组取
# 多数 category），上一轮榜单文件还在时先保组归属。
# 归属规则（2026-11-08 定，全对上严格版）：只信 hiwiki 对照表——
# 在表里进该榜，不在表里就是 hiwiki 的权威结论“不给榜”，不回落
# 任何本地规则。静态表与关键词规则已随 96 榜静态表一并删除。
# ---------------------------------------------------------------------------
# hiwiki 大类 -> App 大类显示名（组标题）。这是显示标签映射，不是榜单
# 表：新榜的 category 总是这 7 个之一；万一 hiwiki 出了新大类，该榜
# 自成一组、组名即 category 原值，永不丢弃。
CATEGORY_GROUP_NAMES = {"ai": "AI", "media": "媒体", "network": "网络",
                        "tools": "工具", "productivity": "效率",
                        "development": "开发", "game": "游戏"}
GROUP_ORDER = ["AI", "媒体", "网络", "工具", "效率", "开发", "游戏"]


def load_hiwiki_boards(path=None):
    """读榜单全集 [{label, category}]（harvest 的 hiwiki-boards.json，
    榜单页顺序原样）。文件缺失或损坏返回 [] 并只记一行日志——
    调用方按无榜单处理（对照表不过滤、feed 不带 boards 结构，
    App 按 feed 里实际出现的榜名计数回落），整轮不失败。"""
    if path is None:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "hiwiki-boards.json")
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("hiwiki boards %s 读不到（按无榜单处理）：%s" % (path, e))
        return []
    boards = data.get("boards") if isinstance(data, dict) else None
    if not isinstance(boards, list):
        print("hiwiki boards %s 结构异常（按无榜单处理）" % path)
        return []
    out = []
    for b in boards:
        if (isinstance(b, dict) and isinstance(b.get("label"), str)
                and b.get("label") and isinstance(b.get("category"), str)
                and b.get("category")):
            out.append({"label": b["label"], "category": b["category"]})
    return out


def load_hiwiki_ranks(path=None, boards=None):
    """读 hiwiki 分类对照表 {小写 fullName: 细分榜中文名}。

    默认找 generate.py 同目录下的 hiwiki-ranks.json（即本任务交付的
    hiwiki-merge-mapping.json 改名后）；文件缺失或损坏时返回空表并只记
    一行日志，整轮不失败（退化为无榜行为）。
    boards 非空时只保留在榜单里的 subgroup（榜单集合由 harvest
    每轮现取，见 load_hiwiki_boards）；boards 为空（榜单文件缺失）
    时不过滤——对照表本身已由 harvest 按榜单过滤过。
    """
    if path is None:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "hiwiki-ranks.json")
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("hiwiki ranks %s 读不到（按无覆盖处理）：%s" % (path, e))
        return {}
    if not isinstance(data, dict):
        print("hiwiki ranks %s 结构异常（按无覆盖处理）" % path)
        return {}
    board_labels = {b["label"] for b in boards} if boards else None
    table = {}
    dropped = 0
    for full, sub in data.items():
        if not (isinstance(full, str) and isinstance(sub, str)):
            continue
        if board_labels is not None and sub not in board_labels:
            dropped += 1
            continue
        table[full.lower()] = sub
    print("hiwiki ranks: %s 条（文件 %d 条，%d 条不在榜单内已丢弃）"
          % (len(table), len(data), dropped))
    return table


def assign_ranks(item, hiwiki_map):
    """给一条 feed 条目定榜归属，返回榜单中文名列表（可为空）。

    只信 hiwiki 对照表：在表里进该榜，不在表里就是 hiwiki 的权威
    结论“不给榜”，返回 []。hiwiki 一仓一榜。"""
    full = (item.get("fullName") or "").lower()
    sub = (hiwiki_map or {}).get(full)
    return [sub] if sub else []


def build_board_structure(boards):
    """榜单全集 -> App 排行榜结构 [{"name": 组名, "lists": [榜单名]}]。

    组按 GROUP_ORDER 排，组内榜单保持榜单页顺序；category 不在
    CATEGORY_GROUP_NAMES 里的榜自成一组、组名即 category 原值，
    排在已知组之后（先出现先排），永不丢弃。空组不出现。"""
    groups = {}
    group_order = []
    for b in boards:
        group = CATEGORY_GROUP_NAMES.get(b["category"], b["category"])
        if group not in groups:
            groups[group] = []
            group_order.append(group)
        if b["label"] not in groups[group]:
            groups[group].append(b["label"])
    known = [g for g in GROUP_ORDER if g in groups]
    unknown = [g for g in group_order if g not in CATEGORY_GROUP_NAMES.values()]
    return [{"name": g, "lists": groups[g]} for g in known + unknown]


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
                if e.code == 401:
                    # 鉴权失败：token 无效/被吊销，重试无意义，整轮失败。
                    raise SystemExit(
                        "GitHub API 鉴权失败（401），请检查 STORE_PAT：%s"
                        % path)
                if is_rate:
                    # 限速重试已耗尽：整轮失败（调用方据此置 meta_dead）。
                    raise SystemExit(
                        "GitHub API 限速（已用 %d 次），请用 token 或等重置后再跑"
                        % self.calls)
                # 139：5xx / 非限速 403（如 DMCA 屏蔽仓）只降级该仓
                # （返回 None，调用方按 404 同款处理），不再抛
                # SystemExit——此前单个 5xx/403 会置 meta_dead，毒化
                # 整轮剩余新仓的元数据（forks=0、无 created_at）。
                print("GitHub API %s 单仓失败（%s），降级该仓：%s"
                      % (path, e.code, e))
                return None
            except (urllib.error.URLError, TimeoutError, OSError,
                    ValueError, http.client.HTTPException) as e:
                # 60 + 142：瞬时网络错误（DNS/连接重置/超时）、响应解析
                # 错误或 http.client 层错误（BadStatusLine /
                # IncompleteRead，其基类只有 HTTPException，不是
                # OSError/URLError 的子类）只降级该仓（返回 None，
                # 调用方 fetch_repo_meta 按 404 同款处理），不 abort
                # 整轮。注意 HTTPError 是 URLError 的子类，本分支在
                # 它之后才匹配。
                print("GitHub API %s 瞬时失败，降级该仓：%s" % (path, e))
                return None
        raise SystemExit("GitHub API %s 失败：%s" % (path, last_error))


def sort_chart(chart, items):
    # 80：同分（同星标/同分值/同日期）按 fullName 升序作二级键，避免顺序
    # 依赖输入原序、输入一变就换位（与 feed 排序同款）。
    if chart == "new-releases":
        by_name = sorted(items, key=lambda i: i["fullName"].lower())
        return sorted(by_name, key=lambda i: i["latestDate"], reverse=True)
    if chart == "most-popular":
        return sorted(items,
                      key=lambda i: (-(i.get("popularityScore")
                                       if isinstance(i.get("popularityScore"),
                                                    (int, float))
                                       else i["stars"]),
                                     i["fullName"].lower()))
    if chart == "trending":
        # 有 trendingScore 的（镜像条目；攒够 7 天历史后的生成器条目）
        # 按分降序在前；没有的（无历史的生成器条目）按 stars_per_day
        # 降序排在其后，缺 stars_per_day 的镜像条目按星标顶替。
        with_score = [i for i in items
                      if isinstance(i.get("trendingScore"), (int, float))]
        without = [i for i in items
                   if not isinstance(i.get("trendingScore"), (int, float))]
        with_score.sort(key=lambda i: (-i["trendingScore"],
                                       i["fullName"].lower()))
        without.sort(key=lambda i: (-(i.get("stars_per_day")
                                      if isinstance(i.get("stars_per_day"),
                                                   (int, float))
                                      else i["stars"]),
                                    i["fullName"].lower()))
        return with_score + without
    return sorted(items, key=lambda i: (-i["stars"], i["fullName"].lower()))


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print("wrote %s (%d items)" % (path, len(payload.get("items", []))))


def select_installable_release(releases):
    """Release 选择（补丁规则，方案 §2.1；2026-11-08 修正）：按 publishAt
    降序扫描，取首个带 ≥1 可安装附件的 release，不论 prerelease
    标记；没有则返回 None（调用方丢弃该仓并记日志）。
    271：hiwiki 不保证 releases[] 按最新在前排列，顺序一变就会取到旧版
    当最新版——先按 publishAt 降序排过再选（normalize_date 归一化后
    按串比较即按时间比较；缺失/非法日期按最旧处理，排最后；稳定排序，
    同日期保持原顺序）。
    不跳 prerelease 的原因：hiwiki 载荷里没有草稿（草稿在 GitHub 上不
    公开），载荷里的每个 release 都是公开版；旧生成器 latest_release
    也只跳草稿、不跳 prerelease。2026-11-08 run #30 的教训：58 个仓
    的全部 release 都是 prerelease（preview/beta/rc/dev），“首个非
    prerelease”规则把它们全丢了，feed 少了 58 条。
    hiwiki 只保证“某版本”能装（连续多版无安装包才下架），不保证最
    新版有——只取最新版会漏掉最新版无安装包、旧版有的仓。
    返回 (release, platforms, downloads, installable) 或 None。
    """
    ordered = sorted((rel for rel in releases or [] if isinstance(rel, dict)),
                     key=lambda rel: normalize_date(rel.get("publishAt") or ""),
                     reverse=True)
    for rel in ordered:
        platforms = set()
        downloads = 0
        installable = 0
        for asset in rel.get("assets") or []:
            if not isinstance(asset, dict):
                continue
            plats = platforms_for_asset(asset.get("fileName") or "")
            if plats:
                platforms.update(plats)
                downloads += as_int(asset.get("downloadCount"), "downloadCount")
                installable += 1
        if platforms:
            return rel, platforms, downloads, installable
    return None


def load_newest(path):
    """读最新入库文件，返回 (items, generatedAt)。

    接受两种文件：harvest 产出的 hiwiki-newest.json（带 generatedAt）与
    上一轮的 feed/newest.json（80 起不再带 generatedAt，日期返回 ""）。
    文件缺失 / 损坏 / 无有效条目返回 ([], "")。顺序原样保留，绝不重排。
    """
    if not path or not os.path.isfile(path):
        return [], ""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("newest %s 读不到（按无最新入库处理）：%s" % (path, e))
        return [], ""
    items = (data or {}).get("items") or []
    items = [i for i in items
             if isinstance(i, dict) and i.get("fullName")]
    print("newest: %s %d 条（%s）"
          % (path, len(items), (data or {}).get("generatedAt") or "?"))
    return items, (data or {}).get("generatedAt") or ""


def build_newest_items(raw_items, feed_by_name):
    """把最新入库行拼成 feed/newest.json 的条目，顺序原样。

    平台以 feed 为准（附件后缀推导，权威）；feed 里没有该仓时才把
    hiwiki 原始平台值按 HIWIKI_NEWEST_PLATFORM_MAP 映射兜底。其余
    字段一律取 hiwiki 行的新鲜值。返回 (items, in_feed_count)。
    """
    out = []
    seen = set()
    in_feed = 0
    for raw in raw_items:
        full = (raw.get("fullName") or "").strip()
        if not full:
            continue
        # 86：按 fullName 去重保序（hiwiki newest 页若出现重复行，
        # feed/newest.json 不应原样带重）。
        key = full.lower()
        if key in seen:
            print("newest 去重：%s" % full)
            continue
        seen.add(key)
        feed_item = feed_by_name.get(key)
        if feed_item is not None:
            platforms = sorted(feed_item.get("platforms") or [])
            in_feed += 1
        else:
            platforms = sorted(
                {HIWIKI_NEWEST_PLATFORM_MAP[p]
                 for p in (raw.get("platforms") or [])
                 if p in HIWIKI_NEWEST_PLATFORM_MAP})
        out.append({
            "fullName": full,
            "description": raw.get("description") or "",
            "category": normalize_category(raw.get("category")),
            "language": raw.get("language") or "",
            "stars": as_int(raw.get("stars"), "stars", full),
            "latestTag": raw.get("latestTag") or "",
            "lastReleaseTime": raw.get("lastReleaseTime") or "",
            "platforms": platforms,
        })
    return out, in_feed


def load_hiwiki_pool(path):
    """读 harvest 产出的全量分类 JSON，返回 (records, meta)。

    文件格式 {"generatedAt", "buildId", "poolSize", "records": [...]}；
    为方便离线模拟也接受裸 list。文件缺失/损坏返回 (None, {})——调用
    方走回落（上一轮 feed 的 hiwiki 条目），不直接失败。记录按小写
    fullName 去重（保留首次出现）。
    """
    if not path or not os.path.isfile(path):
        return None, {}
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("hiwiki pool %s 读不到（走回落）：%s" % (path, e))
        return None, {}
    if isinstance(data, dict):
        records = data.get("records")
        meta = {k: data.get(k) for k in
                ("generatedAt", "buildId", "poolSize", "ok", "failed")}
    elif isinstance(data, list):
        records = data
        meta = {}
    else:
        print("hiwiki pool %s 结构异常（走回落）" % path)
        return None, {}
    if not isinstance(records, list):
        print("hiwiki pool %s 无 records（走回落）" % path)
        return None, {}
    seen = set()
    deduped = []
    for r in records:
        if not isinstance(r, dict):
            continue
        key = (r.get("fullName") or "").lower()
        if not key or key in seen:
            continue
        seen.add(key)
        deduped.append(r)
    if len(deduped) != len(records):
        print("hiwiki pool: 去重 %d 条重复记录" % (len(records) - len(deduped)))
    print("hiwiki pool: %d 条记录（文件 %s）" % (len(deduped), path))
    return deduped, meta


def load_prev_meta(path):
    """读上一轮 meta.json（工作流从 gh-pages 取回），返回 dict；文件
    缺失/损坏返回 {}（调用方按“无上一轮”处理，正常写新 meta）。"""
    if not path or not os.path.isfile(path):
        return {}
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("prev meta %s 读不到（按无上一轮处理）：%s" % (path, e))
        return {}
    if not isinstance(data, dict):
        print("prev meta %s 结构异常（按无上一轮处理）" % path)
        return {}
    return data


def load_prev_feed(path):
    """读上一轮 feed/all.json，返回 ({小写 fullName: item}, generatedAt)。
    文件缺失/损坏返回 ({}, "")。"""
    if not path or not os.path.isfile(path):
        return {}, ""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("prev feed %s 读不到（按无上一轮处理）：%s" % (path, e))
        return {}, ""
    items = (data or {}).get("items") or []
    prev = {}
    for item in items:
        if isinstance(item, dict) and item.get("fullName"):
            prev[item["fullName"].lower()] = item
    print("prev feed: %d 条（%s）"
          % (len(prev), (data or {}).get("generatedAt") or "?"))
    return prev, (data or {}).get("generatedAt") or ""


def fetch_repo_meta(api, full):
    """1 次 GET /repos/{full} 补 forks/topics/created_at；404 返回 None。"""
    data = api.get("/repos/%s" % full)
    if not isinstance(data, dict):
        return None
    return data


def build_hiwiki_item(record, prev, api, metadata_cap, stats,
                      now=None, history_old=None, simulate=False):
    """由一条 hiwiki 池记录拼 feed 条目；无可用 release 时返回 None
    （调用方丢弃并计数，见 select_installable_release）。

    forks/topics/created_at：上一轮 feed 里有的直接沿用（0 次调用），
    没有的调 1 次 GET /repos/{full}；api.calls 触及 metadata_cap 或
    遇上限速后，剩余新仓以残缺字段进 feed（forks=0、topics=[]、无
    created_at，记入 stats["degraded"]），整轮不失败。simulate=True
    时不调任何 API、无 release 数据的记录降级拼条目（离线核数用）。
    stats: {"degraded": 需补字段仍缺失的条目数, "dropped": 丢弃数}。
    """
    full = (record.get("fullName") or "").strip()
    key = full.lower()
    if not full or "/" not in full:
        stats["dropped"] += 1
        print("drop: 无 fullName 的记录")
        return None
    owner, name = full.split("/", 1)
    prev_item = (prev or {}).get(key)

    releases = record.get("releases")
    if isinstance(releases, list) and releases:
        selected = select_installable_release(releases)
        if selected is None:
            stats["dropped"] += 1
            print("drop %s: 无可安装 release" % full)
            return None
        rel, platforms, downloads, installable = selected
        latest_tag = rel.get("tag") or ""
        latest_date = normalize_date(rel.get("publishAt") or "")
    elif simulate:
        # 离线模拟：2026-11-08 抓数没有 release 数据，降级拼条目。
        platforms = set((prev_item or {}).get("platforms") or [])
        downloads = as_int((prev_item or {}).get("downloadCount"),
                           "downloadCount", full)
        installable = (prev_item or {}).get("assetCount") or 0
        latest_tag = record.get("latestTag") or ""
        latest_date = normalize_date(record.get("lastReleaseTime") or "")
    else:
        stats["dropped"] += 1
        print("drop %s: 记录无 releases 数据" % full)
        return None

    forks = None
    topics = None
    created_at = None
    if prev_item is not None:
        if isinstance(prev_item.get("forks"), (int, float)):
            forks = prev_item["forks"]
        if isinstance(prev_item.get("topics"), list):
            topics = prev_item["topics"]
        if prev_item.get("createdAt"):
            created_at = prev_item["createdAt"]
    need_meta = forks is None or topics is None or created_at is None
    if need_meta and not simulate:
        if api is not None and not stats.get("meta_dead") \
                and api.calls < metadata_cap:
            try:
                meta = fetch_repo_meta(api, full)
            except SystemExit:
                # 限速（通常是 token 不是 5000 次的 STORE_PAT）：剩余
                # 新仓全部降级，整轮不失败。
                stats["meta_dead"] = True
                meta = None
            if meta is not None:
                if forks is None:
                    forks = meta.get("forks_count") or 0
                if topics is None:
                    topics = meta.get("topics") or []
                if created_at is None:
                    created_at = meta.get("created_at") or None
    if need_meta and (forks is None or topics is None
                      or created_at is None):
        stats["degraded"] += 1
    if forks is None:
        forks = 0
    if topics is None:
        topics = []

    stars = as_int(record.get("stars"), "stars", full)
    description = record.get("description")
    if description is None and prev_item is not None:
        description = prev_item.get("description") or ""
    avatar = record.get("ownerAvatar") or ""
    if not avatar and prev_item is not None:
        avatar = prev_item.get("avatarUrl") or ""
    pushed_at = (record.get("pushedAt") or "").strip()
    if len(pushed_at) == 10:
        pushed_at += "T00:00:00Z"

    item = {
        "fullName": full,
        "owner": owner,
        "name": name,
        "avatarUrl": avatar,
        "description": description or "",
        "stars": stars,
        "forks": forks,
        "language": record.get("language") or "",
        "category": normalize_category(record.get("category")),
        "topics": topics,
        "topicCodes": topic_codes(topics),
        "latestTag": latest_tag,
        "latestDate": latest_date,
        "pushedAt": normalize_date(pushed_at) if pushed_at else "",
        "downloadCount": downloads,
        "assetCount": installable,
        "platforms": sorted(platforms),
        "popularityScore": popularity_score(stars, forks),
        "source": "hiwiki",
    }
    if created_at:
        # 存进条目供下轮复用（App 不读该字段，org.json 会忽略）。
        item["createdAt"] = created_at
        item["stars_per_day"] = round(stars_per_day(
            stars, created_at,
            now or datetime.datetime.now(datetime.timezone.utc)), 4)
    if history_old is not None:
        old = history_old.get(key)
        if isinstance(old, (int, float)):
            recent = max(0.0, (stars - old) / HISTORY_WINDOW_DAYS)
            item["trendingScore"] = round(TRENDING_FACTOR * recent, 2)
    return item


def compute_charts(feed, now_utc):
    """三榜由同一份池子数据重算（方案 §5），零新增 API 调用。

    热门：星标>50 且 pushedAt 在 30 天内；最新发布：星标>10 且 14 天
    内真正发过版（按条目 latestDate，即所取 release 的发布时间）；
    最热门：星标>1000 且 pushedAt 在 1 年内。排序沿用 sort_chart。
    """
    trending_cutoff = now_utc - datetime.timedelta(days=30)
    popular_cutoff = now_utc - datetime.timedelta(days=365)
    release_cutoff = now_utc - datetime.timedelta(days=NEW_RELEASES_DAYS)

    def pushed_after(item, cutoff):
        d = parse_iso_datetime(item.get("pushedAt"))
        return d is not None and d >= cutoff

    trending = [i for i in feed
                if (i.get("stars") or 0) > 50
                and pushed_after(i, trending_cutoff)]
    new_releases = [i for i in feed
                    if (i.get("stars") or 0) > 10
                    and is_recent_release(i, release_cutoff)]
    most_popular = [i for i in feed
                    if (i.get("stars") or 0) > 1000
                    and pushed_after(i, popular_cutoff)]
    return {
        "trending": sort_chart("trending", trending),
        "new-releases": sort_chart("new-releases", new_releases),
        "most-popular": sort_chart("most-popular", most_popular),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--hiwiki-pool", default=None,
                    help="hiwiki 全量分类 JSON 路径（harvest 产物）；不传或文件缺失/损坏时走回落：用上一轮 feed 里 source==hiwiki 的条目")
    ap.add_argument("--hiwiki-ranks", default=None,
                    help="hiwiki 分类对照表 JSON 路径；不传则用 generate.py 同目录的 hiwiki-ranks.json")
    ap.add_argument("--hiwiki-boards", default=None,
                    help="hiwiki 榜单全集 JSON 路径（harvest 的 hiwiki-boards.json）；不传则用 generate.py 同目录的 hiwiki-boards.json；文件缺失时对照表不过滤、feed 不带 boards 结构")
    ap.add_argument("--hiwiki-newest", default=None,
                    help="harvest 产出的 hiwiki-newest.json 路径；不传或文件缺失/损坏时沿用 --prev-newest，两者都没有则本轮不写 feed/newest.json，feed 不受影响")
    ap.add_argument("--prev-newest", default=None,
                    help="上一轮 feed/newest.json 路径；新鲜文件缺失时沿用（stale），平台按本轮 feed 重算")
    ap.add_argument("--prev-feed", default=None,
                    help="上一轮 feed/all.json 路径；供元数据复用与回落池子")
    ap.add_argument("--prev-meta", default=None,
                    help="上一轮 meta.json 路径（工作流从 gh-pages 取回）；"
                    "80：数据内容不变时沿用上一轮 meta 原样写出，不产生 diff")
    ap.add_argument("--metadata-cap", type=int, default=4500,
                    help="GitHub API 元数据调用硬上限；触顶后剩余新仓以残缺字段进 feed，整轮不失败")
    ap.add_argument("--harvest-status", default="ok",
                    help="ok / fallback / simulate，写入 meta.json")
    ap.add_argument("--simulate", action="store_true",
                    help="离线模拟：不调 api.github.com；无 release 数据的记录降级拼条目（核数用，不用于生产）")
    ap.add_argument("--history-dir", default=None,
                    help="历史快照目录：读 7 天前的星标快照，写当天的；不传则不读写快照，生成器条目不写 trendingScore")
    ap.add_argument("--out", default="store-backend/output")
    ap.add_argument("--snapshot-dir",
                    default="app/src/main/assets/store-snapshot")
    args = ap.parse_args()

    api = None if args.simulate else Api(os.environ.get("STORE_PAT", ""))
    today = datetime.date.today()
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    generated_at = now_utc.isoformat().replace("+00:00", "Z")

    # 历史快照：只读 7 天前那一份（没有则本轮条目不写 trendingScore，
    # 用 stars_per_day 顶替排序）。
    history_old_date = today - datetime.timedelta(days=HISTORY_WINDOW_DAYS)
    history_old = load_history_snapshot(args.history_dir, history_old_date)
    print("history: %s %s (%d repos)"
          % (history_old_date.isoformat(),
             "found" if history_old is not None else "missing",
             len(history_old) if history_old is not None else 0))

    # 上一轮 feed：元数据复用（forks/topics/createdAt）与回落池子。
    prev_items, prev_generated_at = load_prev_feed(args.prev_feed)

    # 池子：harvest 产物优先；没有则回落到上一轮 feed 的 hiwiki 条目。
    pool_records, pool_meta = load_hiwiki_pool(args.hiwiki_pool)
    fallback_pool = False
    if pool_records is None:
        fallback_pool = True
        pool_records = [item for item in prev_items.values()
                        if item.get("source") == "hiwiki"]
        print("hiwiki pool: harvest 产物缺失，回落到上一轮 feed 的 "
              "hiwiki 条目 %d 条" % len(pool_records))
    else:
        print("hiwiki pool: %d 条（harvest %s）"
              % (len(pool_records), pool_meta.get("generatedAt") or "?"))

    hiwiki_boards = load_hiwiki_boards(args.hiwiki_boards)
    hiwiki_map = load_hiwiki_ranks(args.hiwiki_ranks, hiwiki_boards)
    board_structure = build_board_structure(hiwiki_boards)
    print("boards: %d 个榜单，%d 组（%s）"
          % (len(hiwiki_boards), len(board_structure),
             "、".join("%s %d" % (g["name"], len(g["lists"]))
                       for g in board_structure)))
    mapping_date = pool_meta.get("generatedAt") or prev_generated_at or ""
    harvest_status = args.harvest_status
    if fallback_pool:
        harvest_status = "fallback"

    stats = {"degraded": 0, "dropped": 0}
    feed = []
    if fallback_pool:
        # 回落：上一轮条目原样复用，只按回落对照表重算 ranks。
        for item in pool_records:
            item["ranks"] = assign_ranks(item, hiwiki_map)
            feed.append(item)
    else:
        for record in pool_records:
            item = build_hiwiki_item(record, prev_items, api,
                                     args.metadata_cap, stats, now=now_utc,
                                     history_old=history_old,
                                     simulate=args.simulate)
            if item is None:
                continue
            item["ranks"] = assign_ranks(item, hiwiki_map)
            feed.append(item)
    # 80：同星标按 fullName 升序作二级键（输入顺序一变就换位的问题）。
    feed = sorted(feed, key=lambda i: (-i["stars"], i["fullName"].lower()))
    if not feed:
        print("ERROR: feed 为空（池子 0 条或全部被丢弃），拒绝写出空商店")
        sys.exit(1)
    ranked = sum(1 for i in feed if i.get("ranks"))
    print("feed: %d items (pool %d, dropped %d, degraded metadata %d, "
          "api calls %d)"
          % (len(feed), len(pool_records), stats["dropped"],
             stats["degraded"], api.calls if api else 0))
    print("ranks: %d/%d items ranked (hiwiki override %d)"
          % (ranked, len(feed),
             sum(1 for i in feed
                 if hiwiki_map.get((i.get("fullName") or "").lower()))))

    # 最新入库：harvest 新鲜文件优先；没有则沿用上一轮的（stale，
    # 平台按本轮 feed 重算）；两者都没有则本轮不写 newest.json，
    # 但 feed 照常出，整轮不失败。
    feed_by_name = {i["fullName"].lower(): i for i in feed}
    newest_raw, newest_date = load_newest(args.hiwiki_newest)
    if newest_raw:
        newest_status = "ok"
        newest_items, newest_in_feed = build_newest_items(
            newest_raw, feed_by_name)
    else:
        prev_newest, prev_newest_date = load_newest(args.prev_newest)
        if prev_newest:
            newest_status = "stale"
            newest_items, newest_in_feed = build_newest_items(
                prev_newest, feed_by_name)
            newest_date = prev_newest_date
        else:
            newest_status = "missing"
            newest_items, newest_in_feed = [], 0
    print("newest: status=%s count=%d inFeed=%d"
          % (newest_status, len(newest_items), newest_in_feed))

    # 三榜由同一份池子数据重算（App 不读这三个文件，零 UI 影响）。
    charts = compute_charts(feed, now_utc)
    for chart in CHARTS:
        print("chart %s: %d items" % (chart, len(charts[chart])))

    # 写当天星标快照（供 7 天后算近 7 日均涨星）；只写当天、不读当天。
    history_stars = {i["fullName"].lower(): i["stars"] for i in feed}
    write_history_snapshot(args.history_dir, today, generated_at,
                           history_stars)

    def platform_items(items, platform):
        if platform == "all":
            return items
        return [i for i in items if platform in i["platforms"]]

    for platform in PLATFORMS:
        payload = {"items": platform_items(feed, platform)}
        if platform == "all":
            # 排行榜结构随全量 feed 一起下发（App 首页统计与排行榜页
            # 都从这里取，与本轮 feed 同源、永不错位；分包 feed 不带）。
            payload["boards"] = board_structure
        write_json(os.path.join(args.out, "feed", platform + ".json"),
                   payload)
        for chart, items in charts.items():
            write_json(os.path.join(args.out, "categories", chart,
                                    platform + ".json"),
                       {"items": platform_items(items, platform)})

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
                       {"page": n,
                        "hasMore": n < len(pages), "items": chunk})

    # 最新入库（单文件，不分页；缺失时不写，feed 不受影响）。
    if newest_items:
        write_json(os.path.join(args.out, "feed", "newest.json"),
                   {"items": newest_items})
    else:
        print("newest: 无数据，本轮不写 feed/newest.json（feed 不受影响）")

    # 144：meta.json 补 harvest 完整度信号。池子丢 ≤5% 时 harvestStatus 仍为
    # ok，之前与干净轮次无法区分；ok/failed 直接取自池子文件（与
    # harvest-log.json 的 failRate 同数，load_hiwiki_pool 已抽进 pool_meta）。
    harvest_ok = pool_meta.get("ok")
    harvest_failed = pool_meta.get("failed")
    harvest_total = (harvest_ok if isinstance(harvest_ok, int) else 0) \
        + (harvest_failed if isinstance(harvest_failed, int) else 0)
    harvest_fail_rate = (round(harvest_failed / harvest_total, 4)
                         if isinstance(harvest_failed, int) and harvest_total > 0
                         else None)

    meta = {"generatedAt": generated_at,
            "feed": len(feed),
            "feedPages": feed_pages,
            "charts": {c: len(v) for c, v in charts.items()},
            "apiCalls": api.calls if api else 0,
            "poolSize": len(pool_records),
            "hiwikiItems": len(feed),
            "ranked": ranked,
            "unranked": len(feed) - ranked,
            "boardCount": len(hiwiki_boards),
            "mappingDate": mapping_date,
            "harvestStatus": harvest_status,
            "harvestFailed": harvest_failed if isinstance(harvest_failed, int) else None,
            "harvestFailRate": harvest_fail_rate,
            "droppedNoInstallable": stats["dropped"],
            "metadataDegraded": stats["degraded"],
            "newestCount": len(newest_items),
            "newestStatus": newest_status,
            "newestInFeed": newest_in_feed,
            "newestDate": newest_date,
            "historyDate": today.isoformat(),
            "historyOldDate": history_old_date.isoformat(),
            "historyOldFound": history_old is not None,
            "historyItems": len(history_stars)}
    # 80：数据零变化时不空提交。上一轮 meta 存在且全部“数据字段”
    # （除运行期时间戳/诊断字段外）逐项相等时，沿用上一轮 meta 原样
    # 写出（字节与上一轮一致，gh-pages 无 diff、不空提交）；否则写
    # 新 meta。排除的三个字段是运行期量，不是发布数据：generatedAt
    # 为本轮时间；mappingDate 为 harvest 时间（池子内容相同时每轮
    # 都变）；apiCalls 为本轮调用数（稳态为 0）。注意只比“内容真的
    # 不变”才跳过：键集合不同或任一数据字段不同都走新写。
    prev_meta = load_prev_meta(args.prev_meta)
    meta_volatile = ("generatedAt", "mappingDate", "apiCalls")
    if (prev_meta and set(prev_meta) == set(meta)
            and all(prev_meta.get(k) == meta[k] for k in meta
                    if k not in meta_volatile)):
        print("meta: 数据内容与上一轮一致，沿用上一轮 meta.json"
              "（generatedAt/mappingDate/apiCalls 不变），不产生 diff")
        meta = prev_meta
    write_json(os.path.join(args.out, "meta.json"), meta)

    if args.snapshot_dir and args.snapshot_dir != "/dev/null":
        write_json(os.path.join(args.snapshot_dir, "feed_all.json"),
                   {"items": feed, "boards": board_structure})
        for chart, items in charts.items():
            write_json(os.path.join(args.snapshot_dir,
                                    "chart_%s_all.json" % chart),
                       {"items": items})
    print("done, total api calls %d" % (api.calls if api else 0))


if __name__ == "__main__":
    main()
