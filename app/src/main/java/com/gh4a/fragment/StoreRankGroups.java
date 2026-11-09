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
 * 排行榜 96 榜分组（hiwiki 三页签，0.1.56 起，客户端现算；0.1.66 起后端给榜）。
 *
 * 归属来源（0.1.66）：条目带后端 ranks 时信后端——非空用后端给的榜名，
 * 空列表即后端确认无榜；只有 ranks 缺失（旧数据）才回落本地静态表＋
 * 关键词规则。榜内排序为纯星标降序，平局按 fullName 升序（与 hiwiki 一致）。
 *
 * 榜单结构（7 大类、96 个细分榜单、每榜前 3 名＋完整榜单）与静态映射
 * 以设计稿 v1 store-hiwiki-3tabs.html 的 RANKMAP 为准（本文件由脚本从
 * 该稿解析生成，禁止手抄）；集成方案 §2.3 人工映射表里有、设计稿
 * RANKMAP 漏掉的归属见 PLAN_ADDITIONS（逐条可追溯）。
 *
 * 规则层（RULES）只对静态表里没有的仓库生效：topics／仓库名的
 * 关键词命中即归入对应榜单（简介不再参与匹配，避免 "rapid" 含 "api"
 * 之类误伤）；一个仓库可进多个榜（与 hiwiki 一致）。
 * 静态表与规则都命中不了的仓库不进任何榜。
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

    private static final String[] GROUP_NAMES = {
            "AI",
            "媒体",
            "网络",
            "工具",
            "效率",
            "开发",
            "游戏",
    };

    private static final String[][] LIST_NAMES = {
            { "AI Agent框架", "AI聊天客户端/助手", "LLM推理/微调工具", "AI第二大脑/知识库", "AI图像工具", "AI语音工具", "MCP连接器/工具" },
            { "视频播放器", "音乐播放器", "影视/追剧应用", "B站客户端", "IPTV/电视直播", "媒体库/流媒体服务器", "音乐流媒体服务", "电台/播客/有声" },
            { "代理/VPN客户端", "下载管理器", "即时通讯软件", "网络监控/抓包工具", "内网穿透工具", "网络爬虫工具", "网络隧道工具" },
            { "弹幕/直播工具", "电子书阅读器", "浏览器", "浏览器扩展", "漫画阅读器", "命令行工具", "视频下载工具", "数据库管理工具", "投屏/手机连接工具", "文件传输工具", "文件管理器", "系统增强工具", "小说阅读器", "语音转文字工具", "终端模拟器", "屏幕录制工具", "系统监控工具", "远程桌面/控制工具", "壁纸工具", "截图工具", "图像处理工具", "系统清理工具", "网页翻译工具", "输入法", "自动化工具", "PDF工具", "云存储同步工具", "音频编辑工具", "视频转码工具", "压缩解压工具", "密码管理工具", "书签工具", "图片查看器", "虚拟机/容器工具", "剪贴板管理", "资源抓取工具", "字幕工具", "窗口管理器", "调试/分析工具", "番茄钟/专注工具", "广告拦截工具", "视频编辑工具", "文档工具", "Docker工具" },
            { "笔记工具", "任务/待办管理", "效率启动器", "记账/财务管理", "日历工具", "时间追踪工具", "知识库工具" },
            { "后端开发工具", "开发库/框架", "AI编程助手", "代码编辑器", "代码质量工具", "编程语言", "机器学习框架", "Markdown编辑器", "数据库系统", "跨平台应用框架", "逆向工程工具", "CI/CD工具", "包管理器", "构建工具", "语言工具链", "API开发工具", "Kubernetes工具" },
            { "Minecraft启动器/工具", "游戏模拟器", "游戏", "游戏辅助工具", "游戏启动器", "游戏引擎" },
    };

    /** 细分榜单总数（hiwiki 实测 96）。 */
    public static int totalListCount() {
        int total = 0;
        for (String[] lists : LIST_NAMES) {
            total += lists.length;
        }
        return total;
    }

    /**
     * 静态覆写表：仓库 fullName → 它所属的榜单（"组:榜"，逗号分隔）。
     * 来源：设计稿 v1 的 RANKMAP（脚本解析）＋ PLAN_ADDITIONS。
     */
    private static final String[][] ASSIGNMENTS = {
            { "1jehuang/jcode", "5:3" },
            { "2dust/v2rayN", "2:0" },
            { "3150481853/molly-qq-whale", "0:1,0:6" },
            { "3proxy/3proxy", "2:0" },
            { "Alishahryar1/free-claude-code", "5:2" },
            { "ArthurSonzogni/FTXUI", "5:1" },
            { "AstrBotDevs/AstrBot", "0:1" },
            { "BurntSushi/ripgrep", "3:5" },
            { "CherryHQ/cherry-studio", "0:1,0:2" },
            { "ClementTsang/bottom", "3:16" },
            { "ColorlibHQ/AdminLTE", "5:1" },
            { "Comfy-Org/ComfyUI", "0:0,0:4" },
            { "FCL-Team/FoldCraftLauncher", "6:0,6:4" },
            { "Genymobile/scrcpy", "3:8" },
            { "Graphify-Labs/graphify", "0:3,4:6" },
            { "IPF-Sinon/DSH-Folk", "0:6,5:2" },
            { "IceWhaleTech/CasaOS", "3:11" },
            { "IncarnaMind/IncarnaMind", "0:3,4:6" },
            { "JosefNemec/Playnite", "6:4" },
            { "JuliusBrussee/caveman", "0:6,5:2" },
            { "Kong/insomnia", "5:15" },
            { "KrisCris/Palworld-Pal-Editor", "6:3" },
            { "LizardByte/Sunshine", "6:3" },
            { "MaaAssistantArknights/MaaAssistantArknights", "6:3" },
            { "MarioCatuogno/PanzaUI", "6:3" },
            { "MetaCubeX/metacubexd", "2:0" },
            { "NickeManarin/ScreenToGif", "3:15" },
            { "OpenCut-app/OpenCut", "3:41" },
            { "PCL-Community/PCL-CE", "6:0,6:4" },
            { "Panniantong/Agent-Reach", "0:0" },
            { "Parricidium/MWCoop", "6:2,6:3" },
            { "PrismLauncher/PrismLauncher", "6:0,6:4" },
            { "Pumpkin-MC/Pumpkin", "6:0" },
            { "QwenLM/qwen-code", "0:6,5:2" },
            { "Raven-Scout/Scout", "0:3,4:6" },
            { "Routstr/routstrd", "2:6" },
            { "Sermilion/SkillBill", "0:6,5:2" },
            { "Stirling-Tools/Stirling-PDF", "3:25" },
            { "TauricResearch/TradingAgents", "0:0" },
            { "The-Vibe-Company/quivr", "0:3,4:6" },
            { "ThinkFlowLab/FlyBot", "0:1" },
            { "ajeetdsouza/zoxide", "3:5" },
            { "alexpasmantier/television", "3:5" },
            { "alibaba/arthas", "3:38" },
            { "alibaba/nacos", "5:0" },
            { "anomalyco/opencode", "0:6,5:2" },
            { "ant-design/ant-design", "5:1" },
            { "anthropics/claude-code", "0:6,5:2" },
            { "aquasecurity/trivy", "3:38" },
            { "audacity/audacity", "3:27" },
            { "axisvela/praxo-core", "0:0,0:6" },
            { "b-editor/beutl", "3:41" },
            { "bee-san/RustScan", "2:3" },
            { "binary-husky/gpt_academic", "0:1" },
            { "budcribar/RemoteBlazorWebView", "5:1" },
            { "ccch1mneyyy/dsh-Go", "0:1,5:2" },
            { "clash-verge-rev/clash-verge-rev", "2:0" },
            { "cli/cli", "3:5" },
            { "cline/cline", "5:2" },
            { "cloudfoundry/stratos", "5:16" },
            { "code-yeongyu/oh-my-openagent", "0:2" },
            { "coder/code-server", "5:3" },
            { "cr0hn/dockerscan", "3:38,3:43" },
            { "cyanide-burnout/TurboJedi", "3:5,3:16" },
            { "davidkarlsen/flyway-operator", "5:8,5:16" },
            { "donetick/donetick", "4:1" },
            { "dorakyuraduang/whitealbum2-Android", "6:2" },
            { "ducaale/xh", "3:5" },
            { "earendil-works/pi", "0:0,5:2" },
            { "electron/electron", "5:1,5:9" },
            { "farion1231/cc-switch", "5:2" },
            { "fastfetch-cli/fastfetch", "3:16" },
            { "fatedier/frp", "2:4" },
            { "fish-shell/fish-shell", "3:5" },
            { "fleetdm/fleet", "3:16" },
            { "fluentci-io/fluentci", "5:11" },
            { "gadievron/raptor", "0:0,3:38" },
            { "ggml-org/llama.cpp", "0:2,5:6" },
            { "godotengine/godot", "6:5" },
            { "gogs/gogs", "5:0" },
            { "google-gemini/gemini-cli", "5:2" },
            { "h3pdesign/Neon-Vision-Editor", "3:41" },
            { "h5bp/html5-boilerplate", "5:1" },
            { "hacan359/xerabora", "6:3" },
            { "harry0703/MoneyPrinterTurbo", "0:4,3:41" },
            { "headroomlabs-ai/headroom", "5:2" },
            { "hydralauncher/hydra", "6:4" },
            { "immich-app/immich", "1:5" },
            { "infiniflow/ragflow", "0:3" },
            { "janhq/jan", "0:1,0:2" },
            { "kjk-engine/kjk", "6:1,6:5" },
            { "labring/sealos", "5:16" },
            { "langchain-ai/langchain", "0:0,0:2,5:6" },
            { "langflow-ai/langflow", "0:2" },
            { "langgenius/dify", "0:0" },
            { "lobehub/lobehub", "0:1" },
            { "lollipopkit/flutter_server_box", "3:16" },
            { "louislam/uptime-kuma", "3:16" },
            { "magenta-maple/wubi-dict-editor", "3:23" },
            { "mapeditor/tiled", "6:3" },
            { "marcinz606/NegPy", "3:20" },
            { "matthart1983/netwatch", "2:3" },
            { "mayneyao/eidos", "4:0,4:6" },
            { "mbrock/filnix", "5:13,5:14" },
            { "microsoft/PowerToys", "3:11" },
            { "mifi/lossless-cut", "3:41" },
            { "mirceanton/kubectl-switch", "3:5,5:16" },
            { "movieclaw/MovieClaw", "1:2" },
            { "mpvnet-player/mpv.net", "1:0" },
            { "neovim/neovim", "5:3" },
            { "netdata/netdata", "3:16" },
            { "niakw/ProsperoEden-Encore", "6:1" },
            { "odin-lang/Odin", "5:5" },
            { "ollama/ollama", "0:1,0:2" },
            { "openai/codex", "0:2,5:2" },
            { "openclaw/openclaw", "0:0" },
            { "oven-sh/bun", "5:12" },
            { "paytaca/paytaca-app", "4:3" },
            { "pbek/QOwnNotes", "4:0" },
            { "pierre-emmanuelJ/iptv-proxy", "1:4" },
            { "protocolbuffers/protobuf", "5:1" },
            { "react/react-native", "5:1,5:9" },
            { "rustdesk/rustdesk", "3:17" },
            { "seyed-ali-002/Dana-MCP-Server", "0:6" },
            { "sharkdp/bat", "3:5" },
            { "sharkdp/fd", "3:5" },
            { "sharkdp/hyperfine", "3:5" },
            { "sharkdp/pastel", "3:5" },
            { "siyuan-note/siyuan", "4:0,4:6" },
            { "songquanpeng/one-api", "5:15" },
            { "sostenesfreitas/ocarina-recomp", "6:2" },
            { "stablyai/orca", "5:2" },
            { "storytold/craft-launcher", "6:0,6:4" },
            { "suhang12332/Swift-Craft-Launcher", "6:0,6:4" },
            { "sxyazi/yazi", "3:10" },
            { "tldr-pages/tldr", "3:5" },
            { "topjohnwu/Magisk", "3:11" },
            { "tw93/Pake", "5:9" },
            { "twbs/bootstrap", "5:1" },
            { "ultraworkers/claw-code", "0:0" },
            { "unslothai/unsloth", "0:2,5:6" },
            { "usememos/memos", "4:0" },
            { "vale-cli/vale", "3:5,5:4" },
            { "ventoy/Ventoy", "3:11" },
            { "vercel-labs/agent-browser", "0:0" },
            { "vicanso/cyberapi", "5:15" },
            { "vnotex/vnote", "4:0" },
            { "weifuwan/yak-ops", "5:8" },
            { "wlejon/bro", "5:9,6:5" },
            { "woodpecker-ci/woodpecker", "5:11" },
            { "x6nux-oss/zed-globalization", "5:3" },
            { "xopcai/xopc", "0:1" },
            { "yt-dlp/yt-dlp", "2:1,3:5,3:6" },
            { "ytdl-org/youtube-dl", "2:1,3:6" },
            { "zarf-dev/zarf", "5:16" },
    };

    /**
     * 集成方案 §2.3 人工映射表里有、设计稿 RANKMAP 漏掉的归属（逐条来源）：
     * claw-code／langchain／pi／raptor／praxo-core → AI Agent框架；
     * cc-switch／caveman／dsh-Go／DSH-Folk／SkillBill → AI编程助手；
     * xopc／FlyBot → AI聊天客户端/助手；MoneyPrinterTurbo → AI图像工具；
     * routstrd → 网络隧道工具；paytaca-app → 记账/财务管理；
     * fleet／TurboJedi → 系统监控工具；yak-ops／flyway-operator → 数据库系统；
     * filnix → 语言工具链；RemoteBlazorWebView → 开发库/框架；
     * stratos → Kubernetes工具；kjk → 游戏引擎；MWCoop → 游戏；
     * Scout → 知识库工具。已并入上面的 ASSIGNMENTS，此处仅作溯源记录。
     */
    @SuppressWarnings("unused")
    private static final String[][] PLAN_ADDITIONS = {
            { "ultraworkers/claw-code", "AI", "AI Agent框架" }, // §2.3
            { "farion1231/cc-switch", "开发", "AI编程助手" }, // §2.3
            { "Routstr/routstrd", "网络", "网络隧道工具" }, // §2.3
            { "paytaca/paytaca-app", "效率", "记账/财务管理" }, // §2.3
            { "fleetdm/fleet", "工具", "系统监控工具" }, // §2.3
            { "weifuwan/yak-ops", "开发", "数据库系统" }, // §2.3
            { "xopcai/xopc", "AI", "AI聊天客户端/助手" }, // §2.3
            { "ThinkFlowLab/FlyBot", "AI", "AI聊天客户端/助手" }, // §2.3
            { "3150481853/molly-qq-whale", "AI", "AI聊天客户端/助手" }, // §2.3
            { "budcribar/RemoteBlazorWebView", "开发", "开发库/框架" }, // §2.3
            { "cloudfoundry/stratos", "开发", "Kubernetes工具" }, // §2.3
            { "cyanide-burnout/TurboJedi", "工具", "系统监控工具" }, // §2.3
            { "Raven-Scout/Scout", "效率", "知识库工具" }, // §2.3
            { "langchain-ai/langchain", "AI", "AI Agent框架" }, // §2.3
            { "earendil-works/pi", "AI", "AI Agent框架" }, // §2.3
            { "gadievron/raptor", "AI", "AI Agent框架" }, // §2.3
            { "axisvela/praxo-core", "AI", "AI Agent框架" }, // §2.3
            { "harry0703/MoneyPrinterTurbo", "AI", "AI图像工具" }, // §2.3
            { "davidkarlsen/flyway-operator", "开发", "数据库系统" }, // §2.3
            { "mbrock/filnix", "开发", "语言工具链" }, // §2.3
            { "kjk-engine/kjk", "游戏", "游戏引擎" }, // §2.3
            { "Parricidium/MWCoop", "游戏", "游戏" }, // §2.3
            { "JuliusBrussee/caveman", "开发", "AI编程助手" }, // §2.3
            { "ccch1mneyyy/dsh-Go", "开发", "AI编程助手" }, // §2.3
            { "IPF-Sinon/DSH-Folk", "开发", "AI编程助手" }, // §2.3
            { "Sermilion/SkillBill", "开发", "AI编程助手" }, // §2.3
    };

    /**
     * 关键词规则：仅对静态表里没有的仓库生效。关键字命中任一即归入
     * 对应榜单（"组:榜"，逗号分隔）。匹配方式：topics 里有同名 topic，
     * 或仓库名小写后包含该关键字（简介不再参与匹配）；"a+b" 表示两部分都要命中。
     */
    private static final String[][] RULES = {
            { "mcp", "0:6" },
            { "llm", "0:2" },
            { "chat", "0:1" },
            { "chatbot", "0:1" },
            { "knowledge-base", "0:3,4:6" },
            { "second-brain", "0:3,4:6" },
            { "image-generation", "0:4" },
            { "text-to-image", "0:4" },
            { "stable-diffusion", "0:4" },
            { "text-to-speech", "0:5" },
            { "tts", "0:5" },
            { "voice", "0:5" },
            { "music", "1:1" },
            { "music-player", "1:1" },
            { "audio-player", "1:1" },
            { "music-streaming", "1:6" },
            // 0.2.2：删 "video+player"——matchRules 要求每个 + 分段都是精确
            // topic 或 fullName 子串，topic "video-player" 含连字符、拆不开，
            // 这条永远命中不了；单关键词 "video-player" 已覆盖。
            { "video-player", "1:0" },
            { "movie", "1:2" },
            { "anime", "1:2" },
            { "bilibili", "1:3" },
            { "iptv", "1:4" },
            { "podcast", "1:7" },
            { "vpn", "2:0" },
            { "proxy", "2:0" },
            { "tunnel", "2:4" },
            { "downloader", "2:1,3:6" },
            { "crawler", "2:5" },
            { "spider", "2:5" },
            { "scraper", "2:5" },
            { "web-scraping", "2:5" },
            { "instant-messaging", "2:2" },
            { "messenger", "2:2" },
            { "packet-capture", "2:3" },
            { "network-monitoring", "2:3" },
            { "remote-desktop", "3:17" },
            { "docker", "3:43" },
            { "password", "3:30" },
            { "password-manager", "3:30" },
            { "editor", "5:3" },
            { "code-editor", "5:3" },
            { "text-editor", "5:3" },
            { "browser", "3:2" },
            { "browser-extension", "3:3" },
            { "extension", "3:3" },
            { "ebook", "3:1" },
            { "reader", "3:1" },
            { "manga", "3:4" },
            { "comic", "3:4" },
            { "novel", "3:12" },
            { "terminal", "3:14" },
            { "shell", "3:5" },
            { "screenshot", "3:19" },
            { "wallpaper", "3:18" },
            { "clipboard", "3:34" },
            { "pdf", "3:25" },
            { "translation", "3:22" },
            { "translate", "3:22" },
            { "input-method", "3:23" },
            { "automation", "3:24" },
            { "subtitle", "3:36" },
            { "compress", "3:29" },
            { "bookmark", "3:31" },
            { "image-viewer", "3:32" },
            { "image-editor", "3:20" },
            { "virtual-machine", "3:33" },
            { "container", "3:33" },
            { "adblock", "3:40" },
            { "video-editor", "3:41" },
            { "audio-editor", "3:27" },
            { "transcode", "3:28" },
            { "video-converter", "3:28" },
            { "speech-to-text", "3:13" },
            { "file-manager", "3:10" },
            { "file-transfer", "3:9" },
            { "file-sharing", "3:9" },
            { "screen-recording", "3:15" },
            { "screencast", "3:15" },
            { "system-monitor", "3:16" },
            { "monitoring", "3:16" },
            { "database-management", "3:7" },
            { "cleaner", "3:21" },
            { "system-cleaner", "3:21" },
            { "cloud-storage", "3:26" },
            { "synchronization", "3:26" },
            { "window-manager", "3:37" },
            { "pomodoro", "3:39" },
            { "documentation", "3:42" },
            { "diagnosis", "3:38" },
            { "diagnostic", "3:38" },
            { "danmaku", "3:0" },
            { "live-streaming", "3:0" },
            { "emulator", "6:1" },
            { "minecraft", "6:0" },
            { "game", "6:2" },
            { "game-launcher", "6:4" },
            { "game-engine", "6:5" },
            { "note", "4:0" },
            { "note-taking", "4:0" },
            { "notes", "4:0" },
            { "todo", "4:1" },
            { "task-manager", "4:1" },
            { "launcher", "4:2" },
            { "calendar", "4:4" },
            { "time-tracking", "4:5" },
            { "finance", "4:3" },
            { "accounting", "4:3" },
            { "markdown", "5:7" },
            { "kubernetes", "5:16" },
            { "database", "5:8" },
            { "machine-learning", "5:6" },
            { "computer-vision", "5:6" },
            { "data-science", "5:1" },
            { "backend", "5:0" },
            { "web-server", "5:0" },
            { "web-framework", "5:1" },
            { "css-framework", "5:1" },
            { "code-quality", "5:4" },
            { "static-analysis", "5:4" },
            { "linter", "5:4" },
            { "programming-language", "5:5" },
            { "reverse-engineering", "5:10" },
            { "cicd", "5:11" },
            { "continuous-integration", "5:11" },
            { "package-manager", "5:12" },
            { "packaging", "5:12" },
            { "build-tool", "5:13" },
            { "build-system", "5:13" },
            { "language-server", "5:14" },
            { "compiler", "5:14" },
            { "http-client", "5:15" },
    };

    private static final Map<String, Set<String>> ASSIGN_MAP = buildAssignMap();

    /** 榜单中文名 → "组:榜"（后端 ranks 是榜名，在此转成本地格子）。 */
    private static final Map<String, String> NAME_TO_CELL = buildNameToCell();

    private static Map<String, String> buildNameToCell() {
        Map<String, String> map = new HashMap<>();
        for (int gi = 0; gi < LIST_NAMES.length; gi++) {
            for (int li = 0; li < LIST_NAMES[gi].length; li++) {
                map.put(LIST_NAMES[gi][li], gi + ":" + li);
            }
        }
        return map;
    }

    /** 后端给的榜名转格子：榜名不在 96 榜里的跳过（后端新榜，App 未跟进），
     * 丢弃时记一条诊断（只记榜名，不记仓库名）。 */
    private static Set<String> backendCells(Context context, StoreRepo item) {
        Set<String> out = new HashSet<>();
        if (item.ranks != null) {
            for (String rank : item.ranks) {
                String cell = NAME_TO_CELL.get(rank);
                if (cell != null) {
                    out.add(cell);
                } else {
                    // 98：后端新榜名静默丢弃，记榜名以便跟进
                    DiagTrace.log(context, "STORE", "unknown rank dropped: " + rank);
                }
            }
        }
        return out;
    }

    private static Map<String, Set<String>> buildAssignMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (String[] entry : ASSIGNMENTS) {
            Set<String> cells = new HashSet<>();
            for (String cell : entry[1].split(",")) {
                cells.add(cell.trim());
            }
            map.put(entry[0], cells);
        }
        return map;
    }

    /**
     * 对已加载的 feed 池子现算 96 榜：ranks 非空用后端归属，ranks 为空列表
     * 信后端为无榜，只有 ranks 缺失（旧数据）才回落本地静态表＋规则；
     * 每榜成员按星标倒序，平局按 fullName 升序；空榜保留空列表。
     */
    public static List<Group> build(Context context, List<StoreRepo> pool) {
        List<Group> groups = new ArrayList<>();
        for (int gi = 0; gi < GROUP_NAMES.length; gi++) {
            Group group = new Group(GROUP_NAMES[gi]);
            for (String listName : LIST_NAMES[gi]) {
                group.lists.add(new RankList(listName));
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
                cells = backendCells(context, item);
            } else {
                cells = ASSIGN_MAP.get(item.fullName());
                if (cells == null) {
                    cells = matchRules(item);
                }
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
                // 0.1.66：榜内纯星标降序（hiwiki 同款），平局按 fullName 升序。
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

    private static Set<String> matchRules(StoreRepo item) {
        Set<String> out = new HashSet<>();
        Set<String> topics = new HashSet<>();
        if (item.topics != null) {
            for (String topic : item.topics) {
                if (topic != null) {
                    topics.add(topic.toLowerCase(Locale.US));
                }
            }
        }
        String haystack = (item.fullName() != null ? item.fullName() : "")
                .toLowerCase(Locale.US);
        for (String[] rule : RULES) {
            String keyword = rule[0];
            boolean hit = true;
            for (String part : keyword.split("\\+")) {
                if (!topics.contains(part) && !haystack.contains(part)) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                for (String cell : rule[1].split(",")) {
                    out.add(cell.trim());
                }
            }
        }
        return out;
    }
}
