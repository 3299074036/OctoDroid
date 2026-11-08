# 回退文档：严格版全对上 → 回到旧方案

> 用途：如果严格版全对上（hiwiki 池子＋榜单＋纯星标排序）上线后出问题，按本文档回退到 2026-11-08 改动前的旧方案。本文档写于 2026-11-08 17:43，改动实施前。
>
> 相关方案文档：`hiwiki-full-parity-plan.md`（新方案）、`hiwiki-merge-app-plan.md`（已被取代的局部方案）、`hiwiki-merge-simulation.md`（合并模拟）。

## 一、回退前先确认：旧方案长什么样

### 后端（改动前的线上状态）

- 远程 master 提交：**`7964dd1`**（`store-backend: 12-hourly schedule (00:00/12:00 Beijing time)`），这是回退的基准提交，旧版两个关键文件都在这里。
- 工作流 `.github/workflows/store-backend.yml`：cron `0 4,16 * * *`（UTC，即北京时间 0 点／12 点）；API 走仓库 secret `STORE_PAT`；Generate 步参数 `--max-candidates 1000 --max-items 1000`；Verify 步断言 `feed >= 1500 and apiCalls >= 1000`。
- 生成器 `store-backend/generate.py`：Komi 镜像（17 个文件）＋ 自有搜索管线——三榜（热门／最新发布／最热门）各取 1,000 候选、逐个验最新非草稿 release 有可安装附件才留；公式 popularityScore＝星标＋2×fork，trendingScore＝20×近期日均涨星（7 天历史快照，存 gh-pages `store/history/`）。
- 线上数据（改动前最新一轮，2026-11-08T02:05Z）：feed **1,798** 条，三榜 trending 773／new-releases 635／most-popular 797，apiCalls 约 2,450。
- 商店取数链路不变：新鲜缓存 → github.io → 过期缓存 → raw 离线镜像 → 失败；发现缓存 3h、榜单 24h。

### App（改动前的手机状态）

- 版本 **0.1.65／165**。96 榜在 App 里现算：`StoreRankGroups.java`（静态表 155 条＋关键词规则 131 条），榜内按最热门分（星标＋2×fork）降序、平局按星标。
- 回退基准数（0.1.65 在旧数据上）：非空榜 92/96，AI Agent 框架 9，开发库/框架 18，LLM推理 101，MCP 85，无榜约 52%。
- 三个 App 文件的 0.1.65 原样备份就在本目录（SHA-256 见下，恢复时核对）：
  - `rollback-0.1.65-StoreRepo.java` — `6ce617d15fba7ce0c38fdf8a1e77edc05a426bf09bac7b57db4c8906e841c3c7`
  - `rollback-0.1.65-StoreApi.java` — `184af84ff936294f84f8828406811fa82fbd876e33e4d38fef02a199a0feab02`
  - `rollback-0.1.65-StoreRankGroups.java` — `54dd1ca0bb24ec2ffd39a76b3d7004fe94f35d09453853472eb64dc286c4740b`

### 严格版改了什么（回退时要 undo 的东西）

- 后端：`store-backend/generate.py`（池子改 hiwiki、搜索／镜像管线删除）、`.github/workflows/store-backend.yml`（新增 Harvest hiwiki 步、Verify 重写）、新增 `store-backend/harvest-hiwiki.py` 与 `store-backend/hiwiki-ranks.json`。
- App：`StoreRepo.java`（解析 `ranks` 字段）、`StoreApi.java`（实时构造传 null）、`StoreRankGroups.java`（先读后端 ranks、榜内改纯星标排序），版本 0.1.66／166。
- 数据：feed 从 1,798 变成 hiwiki 池 2,457，1,408 个旧仓下架。

## 二、回退步骤

### 第 1 步：后端回退（先做后端，App 后做）

1. 从基准提交取出旧文件（本地仓库里就有，不用联网找）：
   - `git show 7964dd1:store-backend/generate.py > store-backend/generate.py`
   - `git show 7964dd1:.github/workflows/store-backend.yml > .github/workflows/store-backend.yml`
2. 删掉严格版新增的两个文件：`store-backend/harvest-hiwiki.py`、`store-backend/hiwiki-ranks.json`（留着也不影响旧生成器，但干净回退就删）。
3. 浏览器补推（连接器写操作仍 403，只能走浏览器上传），commit 信息：`store backend: revert to GitHub search pipeline (rollback from hiwiki parity)`。
4. 手动跑一轮工作流（Actions → store-backend → Run workflow）。Verify 步须打印 `ok feed=... apiCalls=...`，且 feed ≥ 1500、apiCalls ≥ 1000；预期 feed 回到约 1,798，三榜约 773／635／797。
5. 注意：回退后的头 7 天，热门榜的 trendingScore 会退化成 stars_per_day 顶替（1,408 个仓的历史快照要重新攒 7 天），这是正常的，不是故障。

### 第 2 步：App 回退

1. 把本目录三个 `rollback-0.1.65-*.java` 备份拷回源码位置（`app/src/main/java/com/gh4a/fragment/`），核对 SHA-256 与上面一致。
2. 重新构建，版本号 **0.1.67／167**（不能用 0.1.66：严格版已经是 166，同号重出容易和已装的 0.1.66 混淆）。
3. 真机验证：排行榜回到 92/96 非空，AI Agent 框架 9，开发库/框架 18；发现页回到 1,798 条量级。

## 三、不回退的东西

- 本目录所有研究文件（hiwiki 分类、对照表、模拟报告、两个方案、本文档）全部保留，只作参考，不删。
- gh-pages 上的 history 快照不用动；回退跑数后会自动续上。
- 与本次改动无关的待办不受影响：一次性试跑 PAT `octodroid-trial-1000` 仍待用户在 GitHub 设置里删除；0.1.65 的真机确认项仍待确认。
