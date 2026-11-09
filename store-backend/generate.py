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
单条含 fullName / owner /
avatarUrl / description / stars / forks / language / topics / topicCodes /
latestTag / latestDate / pushedAt / downloadCount / platforms / source /
ranks（createdAt 仅后端复用时写，App 不读）。只打数量日志，不打仓库名
以外的任何内容。
"""

import argparse
import datetime
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
HIWIKI_NEWEST_PLATFORM_MAP = {
    "android": "android", "ios": "ios", "windows": "windows",
    "mac": "macos", "linux": "linux", "extension": "extension",
    "harmony": "harmony", "other": "archive",
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
# 排行榜分组（96 榜）：静态表 + hiwiki 覆盖（2026-11-08）。
#
# 来源：App 端 StoreRankGroups.java（v4，0.1.60；该文件只在工作树，未进
# master），本段由脚本从该文件逐字转写，未手抄。静态表 155 条、关键词
# 规则 131 条；榜单结构 7 大类 96 榜与之一致。
# 规则：静态表优先于关键词规则（与 App 的 build() 一致）；一个仓库可进
# 多个榜（静态表 44 条是多榜，关键词规则 3 条是多榜）。
# ---------------------------------------------------------------------------
RANK_GROUP_NAMES = ["AI", "媒体", "网络", "工具", "效率", "开发", "游戏"]

RANK_LIST_NAMES = [["AI Agent框架", "AI聊天客户端/助手", "LLM推理/微调工具", "AI第二大脑/知识库", "AI图像工具", "AI语音工具", "MCP连接器/工具"], ["视频播放器", "音乐播放器", "影视/追剧应用", "B站客户端", "IPTV/电视直播", "媒体库/流媒体服务器", "音乐流媒体服务", "电台/播客/有声"], ["代理/VPN客户端", "下载管理器", "即时通讯软件", "网络监控/抓包工具", "内网穿透工具", "网络爬虫工具", "网络隧道工具"], ["弹幕/直播工具", "电子书阅读器", "浏览器", "浏览器扩展", "漫画阅读器", "命令行工具", "视频下载工具", "数据库管理工具", "投屏/手机连接工具", "文件传输工具", "文件管理器", "系统增强工具", "小说阅读器", "语音转文字工具", "终端模拟器", "屏幕录制工具", "系统监控工具", "远程桌面/控制工具", "壁纸工具", "截图工具", "图像处理工具", "系统清理工具", "网页翻译工具", "输入法", "自动化工具", "PDF工具", "云存储同步工具", "音频编辑工具", "视频转码工具", "压缩解压工具", "密码管理工具", "书签工具", "图片查看器", "虚拟机/容器工具", "剪贴板管理", "资源抓取工具", "字幕工具", "窗口管理器", "调试/分析工具", "番茄钟/专注工具", "广告拦截工具", "视频编辑工具", "文档工具", "Docker工具"], ["笔记工具", "任务/待办管理", "效率启动器", "记账/财务管理", "日历工具", "时间追踪工具", "知识库工具"], ["后端开发工具", "开发库/框架", "AI编程助手", "代码编辑器", "代码质量工具", "编程语言", "机器学习框架", "Markdown编辑器", "数据库系统", "跨平台应用框架", "逆向工程工具", "CI/CD工具", "包管理器", "构建工具", "语言工具链", "API开发工具", "Kubernetes工具"], ["Minecraft启动器/工具", "游戏模拟器", "游戏", "游戏辅助工具", "游戏启动器", "游戏引擎"]]

# 静态覆写表：小写 fullName -> [(组下标, 榜下标), ...]。
RANK_ASSIGN_MAP = {"1jehuang/jcode": [[5, 3]], "2dust/v2rayn": [[2, 0]], "3150481853/molly-qq-whale": [[0, 1], [0, 6]], "3proxy/3proxy": [[2, 0]], "alishahryar1/free-claude-code": [[5, 2]], "arthursonzogni/ftxui": [[5, 1]], "astrbotdevs/astrbot": [[0, 1]], "burntsushi/ripgrep": [[3, 5]], "cherryhq/cherry-studio": [[0, 1], [0, 2]], "clementtsang/bottom": [[3, 16]], "colorlibhq/adminlte": [[5, 1]], "comfy-org/comfyui": [[0, 0], [0, 4]], "fcl-team/foldcraftlauncher": [[6, 0], [6, 4]], "genymobile/scrcpy": [[3, 8]], "graphify-labs/graphify": [[0, 3], [4, 6]], "ipf-sinon/dsh-folk": [[0, 6], [5, 2]], "icewhaletech/casaos": [[3, 11]], "incarnamind/incarnamind": [[0, 3], [4, 6]], "josefnemec/playnite": [[6, 4]], "juliusbrussee/caveman": [[0, 6], [5, 2]], "kong/insomnia": [[5, 15]], "kriscris/palworld-pal-editor": [[6, 3]], "lizardbyte/sunshine": [[6, 3]], "maaassistantarknights/maaassistantarknights": [[6, 3]], "mariocatuogno/panzaui": [[6, 3]], "metacubex/metacubexd": [[2, 0]], "nickemanarin/screentogif": [[3, 15]], "opencut-app/opencut": [[3, 41]], "pcl-community/pcl-ce": [[6, 0], [6, 4]], "panniantong/agent-reach": [[0, 0]], "parricidium/mwcoop": [[6, 2], [6, 3]], "prismlauncher/prismlauncher": [[6, 0], [6, 4]], "pumpkin-mc/pumpkin": [[6, 0]], "qwenlm/qwen-code": [[0, 6], [5, 2]], "raven-scout/scout": [[0, 3], [4, 6]], "routstr/routstrd": [[2, 6]], "sermilion/skillbill": [[0, 6], [5, 2]], "stirling-tools/stirling-pdf": [[3, 25]], "tauricresearch/tradingagents": [[0, 0]], "the-vibe-company/quivr": [[0, 3], [4, 6]], "thinkflowlab/flybot": [[0, 1]], "ajeetdsouza/zoxide": [[3, 5]], "alexpasmantier/television": [[3, 5]], "alibaba/arthas": [[3, 38]], "alibaba/nacos": [[5, 0]], "anomalyco/opencode": [[0, 6], [5, 2]], "ant-design/ant-design": [[5, 1]], "anthropics/claude-code": [[0, 6], [5, 2]], "aquasecurity/trivy": [[3, 38]], "audacity/audacity": [[3, 27]], "axisvela/praxo-core": [[0, 0], [0, 6]], "b-editor/beutl": [[3, 41]], "bee-san/rustscan": [[2, 3]], "binary-husky/gpt_academic": [[0, 1]], "budcribar/remoteblazorwebview": [[5, 1]], "ccch1mneyyy/dsh-go": [[0, 1], [5, 2]], "clash-verge-rev/clash-verge-rev": [[2, 0]], "cli/cli": [[3, 5]], "cline/cline": [[5, 2]], "cloudfoundry/stratos": [[5, 16]], "code-yeongyu/oh-my-openagent": [[0, 2]], "coder/code-server": [[5, 3]], "cr0hn/dockerscan": [[3, 38], [3, 43]], "cyanide-burnout/turbojedi": [[3, 5], [3, 16]], "davidkarlsen/flyway-operator": [[5, 8], [5, 16]], "donetick/donetick": [[4, 1]], "dorakyuraduang/whitealbum2-android": [[6, 2]], "ducaale/xh": [[3, 5]], "earendil-works/pi": [[0, 0], [5, 2]], "electron/electron": [[5, 1], [5, 9]], "farion1231/cc-switch": [[5, 2]], "fastfetch-cli/fastfetch": [[3, 16]], "fatedier/frp": [[2, 4]], "fish-shell/fish-shell": [[3, 5]], "fleetdm/fleet": [[3, 16]], "fluentci-io/fluentci": [[5, 11]], "gadievron/raptor": [[0, 0], [3, 38]], "ggml-org/llama.cpp": [[0, 2], [5, 6]], "godotengine/godot": [[6, 5]], "gogs/gogs": [[5, 0]], "google-gemini/gemini-cli": [[5, 2]], "h3pdesign/neon-vision-editor": [[3, 41]], "h5bp/html5-boilerplate": [[5, 1]], "hacan359/xerabora": [[6, 3]], "harry0703/moneyprinterturbo": [[0, 4], [3, 41]], "headroomlabs-ai/headroom": [[5, 2]], "hydralauncher/hydra": [[6, 4]], "immich-app/immich": [[1, 5]], "infiniflow/ragflow": [[0, 3]], "janhq/jan": [[0, 1], [0, 2]], "kjk-engine/kjk": [[6, 1], [6, 5]], "labring/sealos": [[5, 16]], "langchain-ai/langchain": [[0, 0], [0, 2], [5, 6]], "langflow-ai/langflow": [[0, 2]], "langgenius/dify": [[0, 0]], "lobehub/lobehub": [[0, 1]], "lollipopkit/flutter_server_box": [[3, 16]], "louislam/uptime-kuma": [[3, 16]], "magenta-maple/wubi-dict-editor": [[3, 23]], "mapeditor/tiled": [[6, 3]], "marcinz606/negpy": [[3, 20]], "matthart1983/netwatch": [[2, 3]], "mayneyao/eidos": [[4, 0], [4, 6]], "mbrock/filnix": [[5, 13], [5, 14]], "microsoft/powertoys": [[3, 11]], "mifi/lossless-cut": [[3, 41]], "mirceanton/kubectl-switch": [[3, 5], [5, 16]], "movieclaw/movieclaw": [[1, 2]], "mpvnet-player/mpv.net": [[1, 0]], "neovim/neovim": [[5, 3]], "netdata/netdata": [[3, 16]], "niakw/prosperoeden-encore": [[6, 1]], "odin-lang/odin": [[5, 5]], "ollama/ollama": [[0, 1], [0, 2]], "openai/codex": [[0, 2], [5, 2]], "openclaw/openclaw": [[0, 0]], "oven-sh/bun": [[5, 12]], "paytaca/paytaca-app": [[4, 3]], "pbek/qownnotes": [[4, 0]], "pierre-emmanuelj/iptv-proxy": [[1, 4]], "protocolbuffers/protobuf": [[5, 1]], "react/react-native": [[5, 1], [5, 9]], "rustdesk/rustdesk": [[3, 17]], "seyed-ali-002/dana-mcp-server": [[0, 6]], "sharkdp/bat": [[3, 5]], "sharkdp/fd": [[3, 5]], "sharkdp/hyperfine": [[3, 5]], "sharkdp/pastel": [[3, 5]], "siyuan-note/siyuan": [[4, 0], [4, 6]], "songquanpeng/one-api": [[5, 15]], "sostenesfreitas/ocarina-recomp": [[6, 2]], "stablyai/orca": [[5, 2]], "storytold/craft-launcher": [[6, 0], [6, 4]], "suhang12332/swift-craft-launcher": [[6, 0], [6, 4]], "sxyazi/yazi": [[3, 10]], "tldr-pages/tldr": [[3, 5]], "topjohnwu/magisk": [[3, 11]], "tw93/pake": [[5, 9]], "twbs/bootstrap": [[5, 1]], "ultraworkers/claw-code": [[0, 0]], "unslothai/unsloth": [[0, 2], [5, 6]], "usememos/memos": [[4, 0]], "vale-cli/vale": [[3, 5], [5, 4]], "ventoy/ventoy": [[3, 11]], "vercel-labs/agent-browser": [[0, 0]], "vicanso/cyberapi": [[5, 15]], "vnotex/vnote": [[4, 0]], "weifuwan/yak-ops": [[5, 8]], "wlejon/bro": [[5, 9], [6, 5]], "woodpecker-ci/woodpecker": [[5, 11]], "x6nux-oss/zed-globalization": [[5, 3]], "xopcai/xopc": [[0, 1]], "yt-dlp/yt-dlp": [[2, 1], [3, 5], [3, 6]], "ytdl-org/youtube-dl": [[2, 1], [3, 6]], "zarf-dev/zarf": [[5, 16]]}

# 关键词规则：[(关键字, [(组下标, 榜下标), ...]), ...]。仅对静态表里
# 没有的仓库生效；关键字命中 = topics 里有同名 topic，或仓库名小写后
# 包含该关键字；"a+b" 表示两部分都要命中（简介不再参与匹配）。
RANK_RULES = [["mcp", [[0, 6]]], ["llm", [[0, 2]]], ["chat", [[0, 1]]], ["chatbot", [[0, 1]]], ["knowledge-base", [[0, 3], [4, 6]]], ["second-brain", [[0, 3], [4, 6]]], ["image-generation", [[0, 4]]], ["text-to-image", [[0, 4]]], ["stable-diffusion", [[0, 4]]], ["text-to-speech", [[0, 5]]], ["tts", [[0, 5]]], ["voice", [[0, 5]]], ["music", [[1, 1]]], ["music-player", [[1, 1]]], ["audio-player", [[1, 1]]], ["music-streaming", [[1, 6]]], ["video+player", [[1, 0]]], ["video-player", [[1, 0]]], ["movie", [[1, 2]]], ["anime", [[1, 2]]], ["bilibili", [[1, 3]]], ["iptv", [[1, 4]]], ["podcast", [[1, 7]]], ["vpn", [[2, 0]]], ["proxy", [[2, 0]]], ["tunnel", [[2, 4]]], ["downloader", [[2, 1], [3, 6]]], ["crawler", [[2, 5]]], ["spider", [[2, 5]]], ["scraper", [[2, 5]]], ["web-scraping", [[2, 5]]], ["instant-messaging", [[2, 2]]], ["messenger", [[2, 2]]], ["packet-capture", [[2, 3]]], ["network-monitoring", [[2, 3]]], ["remote-desktop", [[3, 17]]], ["docker", [[3, 43]]], ["password", [[3, 30]]], ["password-manager", [[3, 30]]], ["editor", [[5, 3]]], ["code-editor", [[5, 3]]], ["text-editor", [[5, 3]]], ["browser", [[3, 2]]], ["browser-extension", [[3, 3]]], ["extension", [[3, 3]]], ["ebook", [[3, 1]]], ["reader", [[3, 1]]], ["manga", [[3, 4]]], ["comic", [[3, 4]]], ["novel", [[3, 12]]], ["terminal", [[3, 14]]], ["shell", [[3, 5]]], ["screenshot", [[3, 19]]], ["wallpaper", [[3, 18]]], ["clipboard", [[3, 34]]], ["pdf", [[3, 25]]], ["translation", [[3, 22]]], ["translate", [[3, 22]]], ["input-method", [[3, 23]]], ["automation", [[3, 24]]], ["subtitle", [[3, 36]]], ["compress", [[3, 29]]], ["bookmark", [[3, 31]]], ["image-viewer", [[3, 32]]], ["image-editor", [[3, 20]]], ["virtual-machine", [[3, 33]]], ["container", [[3, 33]]], ["adblock", [[3, 40]]], ["video-editor", [[3, 41]]], ["audio-editor", [[3, 27]]], ["transcode", [[3, 28]]], ["video-converter", [[3, 28]]], ["speech-to-text", [[3, 13]]], ["file-manager", [[3, 10]]], ["file-transfer", [[3, 9]]], ["file-sharing", [[3, 9]]], ["screen-recording", [[3, 15]]], ["screencast", [[3, 15]]], ["system-monitor", [[3, 16]]], ["monitoring", [[3, 16]]], ["database-management", [[3, 7]]], ["cleaner", [[3, 21]]], ["system-cleaner", [[3, 21]]], ["cloud-storage", [[3, 26]]], ["synchronization", [[3, 26]]], ["window-manager", [[3, 37]]], ["pomodoro", [[3, 39]]], ["documentation", [[3, 42]]], ["diagnosis", [[3, 38]]], ["diagnostic", [[3, 38]]], ["danmaku", [[3, 0]]], ["live-streaming", [[3, 0]]], ["emulator", [[6, 1]]], ["minecraft", [[6, 0]]], ["game", [[6, 2]]], ["game-launcher", [[6, 4]]], ["game-engine", [[6, 5]]], ["note", [[4, 0]]], ["note-taking", [[4, 0]]], ["notes", [[4, 0]]], ["todo", [[4, 1]]], ["task-manager", [[4, 1]]], ["launcher", [[4, 2]]], ["calendar", [[4, 4]]], ["time-tracking", [[4, 5]]], ["finance", [[4, 3]]], ["accounting", [[4, 3]]], ["markdown", [[5, 7]]], ["kubernetes", [[5, 16]]], ["database", [[5, 8]]], ["machine-learning", [[5, 6]]], ["computer-vision", [[5, 6]]], ["data-science", [[5, 1]]], ["backend", [[5, 0]]], ["web-server", [[5, 0]]], ["web-framework", [[5, 1]]], ["css-framework", [[5, 1]]], ["code-quality", [[5, 4]]], ["static-analysis", [[5, 4]]], ["linter", [[5, 4]]], ["programming-language", [[5, 5]]], ["reverse-engineering", [[5, 10]]], ["cicd", [[5, 11]]], ["continuous-integration", [[5, 11]]], ["package-manager", [[5, 12]]], ["packaging", [[5, 12]]], ["build-tool", [[5, 13]]], ["build-system", [[5, 13]]], ["language-server", [[5, 14]]], ["compiler", [[5, 14]]], ["http-client", [[5, 15]]]]

# 96 个榜单名集合：hiwiki 覆盖只认这些名字（subgroup 须恰好是其中之一）。
RANK_NAME_SET = {name for lists in RANK_LIST_NAMES for name in lists}


def load_hiwiki_ranks(path=None):
    """读 hiwiki 分类对照表 {小写 fullName: 细分榜中文名}。

    默认找 generate.py 同目录下的 hiwiki-ranks.json（即本任务交付的
    hiwiki-merge-mapping.json 改名后）；文件缺失或损坏时返回空表并只记
    一行日志，整轮不失败（退化为纯静态表行为）。
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
    table = {}
    for full, sub in data.items():
        if isinstance(full, str) and isinstance(sub, str) and sub in RANK_NAME_SET:
            table[full.lower()] = sub
    print("hiwiki ranks: %s 条（文件 %d 条，%d 条不在 96 榜内已丢弃）"
          % (len(table), len(data), len(data) - len(table)))
    return table


def match_rank_rules(item):
    """关键词规则（App StoreRankGroups.matchRules 的逐字移植）。"""
    out = set()
    topics = set()
    for topic in item.get("topics") or []:
        if topic:
            topics.add(str(topic).lower())
    haystack = (item.get("fullName") or "").lower()
    for keyword, dests in RANK_RULES:
        hit = True
        for part in keyword.split("+"):
            if part not in topics and part not in haystack:
                hit = False
                break
        if hit:
            out.update(tuple(d) for d in dests)  # dests 是 JSON 转来的 list，需转 tuple 才能进 set
    return out


def assign_ranks(item, hiwiki_map):
    """给一条 feed 条目定 96 榜归属，返回榜单中文名列表（可为空）。

    优先级（用户 2026-11-08 定）：hiwiki 覆盖 > 静态表 > 关键词规则。
    hiwiki 覆盖：仓库在对照表里且 subgroup 恰好是 96 榜之一时，只进
    该榜（hiwiki 一仓一榜），静态表（含多榜）整体让路；否则回落到
    静态表（命中则可能多榜），静态表没有时再走关键词规则。
    """
    full = (item.get("fullName") or "").lower()
    # 全对上（严格版）：hiwiki 来源的条目只信对照表——在表里进该榜，
    # 不在表里就是 hiwiki 的权威结论“不给榜”，返回 []，不回落静态
    # 表/关键词规则（回落会凭空造出 hiwiki 没有的归属）。非 hiwiki
    # 来源（严格版下不存在，留作防线）才走老逻辑。
    if item.get("source") == "hiwiki":
        sub = (hiwiki_map or {}).get(full)
        return [sub] if sub else []
    sub = (hiwiki_map or {}).get(full)
    if sub:
        return [sub]
    cells = RANK_ASSIGN_MAP.get(full)
    if cells is None:
        cells = match_rank_rules(item)
    names = []
    for gi, li in sorted(cells):
        if 0 <= gi < len(RANK_LIST_NAMES) and 0 <= li < len(RANK_LIST_NAMES[gi]):
            names.append(RANK_LIST_NAMES[gi][li])
    return names


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
                if e.code == 403 and "rate limit" in body.lower():
                    raise SystemExit(
                        "GitHub API 限速（已用 %d 次），请用 token 或等重置后再跑"
                        % self.calls)
                raise SystemExit("GitHub API %s 失败：%s" % (path, e))
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
                # 60：瞬时网络错误（DNS/连接重置/超时）或响应解析错误只降级
                # 该仓（返回 None，调用方 fetch_repo_meta 按 404 同款处理），
                # 不 abort 整轮。限速有 3 次重试 + 降级，瞬时错误此前零容错。
                # 注意 HTTPError 是 URLError 的子类，本分支在它之后才匹配。
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
    """Release 选择（补丁规则，方案 §2.1；2026-11-08 修正）：按 releases[]
    最新在前扫描，取首个带 ≥1 可安装附件的 release，不论 prerelease
    标记；没有则返回 None（调用方丢弃该仓并记日志）。
    不跳 prerelease 的原因：hiwiki 载荷里没有草稿（草稿在 GitHub 上不
    公开），载荷里的每个 release 都是公开版；旧生成器 latest_release
    也只跳草稿、不跳 prerelease。2026-11-08 run #30 的教训：58 个仓
    的全部 release 都是 prerelease（preview/beta/rc/dev），“首个非
    prerelease”规则把它们全丢了，feed 少了 58 条。
    hiwiki 只保证“某版本”能装（连续多版无安装包才下架），不保证最
    新版有——只取最新版会漏掉最新版无安装包、旧版有的仓。
    返回 (release, platforms, downloads, installable) 或 None。
    """
    for rel in releases or []:
        if not isinstance(rel, dict):
            continue
        platforms = set()
        downloads = 0
        installable = 0
        for asset in rel.get("assets") or []:
            if not isinstance(asset, dict):
                continue
            plats = platforms_for_asset(asset.get("fileName") or "")
            if plats:
                platforms.update(plats)
                downloads += asset.get("downloadCount") or 0
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
            "stars": raw.get("stars") or 0,
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
        downloads = (prev_item or {}).get("downloadCount") or 0
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

    stars = record.get("stars") or 0
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
    ap.add_argument("--hiwiki-newest", default=None,
                    help="harvest 产出的 hiwiki-newest.json 路径；不传或文件缺失/损坏时沿用 --prev-newest，两者都没有则本轮不写 feed/newest.json，feed 不受影响")
    ap.add_argument("--prev-newest", default=None,
                    help="上一轮 feed/newest.json 路径；新鲜文件缺失时沿用（stale），平台按本轮 feed 重算")
    ap.add_argument("--prev-feed", default=None,
                    help="上一轮 feed/all.json 路径；供元数据复用与回落池子")
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

    hiwiki_map = load_hiwiki_ranks(args.hiwiki_ranks)
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
        write_json(os.path.join(args.out, "feed", platform + ".json"),
                   {"items": platform_items(feed, platform)})
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

    meta = {"generatedAt": generated_at,
            "feed": len(feed),
            "feedPages": feed_pages,
            "charts": {c: len(v) for c, v in charts.items()},
            "apiCalls": api.calls if api else 0,
            "poolSize": len(pool_records),
            "hiwikiItems": len(feed),
            "ranked": ranked,
            "unranked": len(feed) - ranked,
            "mappingDate": mapping_date,
            "harvestStatus": harvest_status,
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
    write_json(os.path.join(args.out, "meta.json"), meta)

    if args.snapshot_dir and args.snapshot_dir != "/dev/null":
        write_json(os.path.join(args.snapshot_dir, "feed_all.json"),
                   {"items": feed})
        for chart, items in charts.items():
            write_json(os.path.join(args.snapshot_dir,
                                    "chart_%s_all.json" % chart),
                       {"items": items})
    print("done, total api calls %d" % (api.calls if api else 0))


if __name__ == "__main__":
    main()
