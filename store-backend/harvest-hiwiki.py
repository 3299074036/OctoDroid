#!/usr/bin/env python3
"""Harvest hiwiki installable pool + classification, per workflow run.

Method (verified 2026-11-08): every hiwiki page embeds its data in
/<path>/_payload.json?_b=<build-id> (Nuxt devalue format: flat JSON list,
ints in value positions are indices into that list; -1 = null).
Repo payloads carry: category (big), subgroup (96-rank name), tags,
starCount, language, pushedAt, ownerAvatar, description, releases[]
(tag / publishAt / prerelease / assets[fileName, downloadCount]).

Pool source: the sitemap (https://git.hiwiki.org/__sitemap__/zh-CN.xml)
is fetched fresh every run -- no static pool file. The router is
case-sensitive: sitemap-exact casing must be used
(TeamNewPipe/NewPipe 404s, teamnewpipe/newpipe works).

Outputs (--out-dir):
  hiwiki-pool.json   {"generatedAt", "buildId", "poolSize", "ok",
                      "failed", "durationSec", "records": [...]}
  hiwiki-ranks.json  {lower fullName: subgroup}，只保留榜单页公布的
                     榜单（boards，见下）——榜单集合每轮现取自 hiwiki，
                     不再写死在代码里。
  hiwiki-boards.json {"generatedAt", "boards": [{"label", "category"}]}
                     榜单页（/zh-CN/rank）公布的榜单全集，榜单页顺序
                     原样；榜单页拉取失败时按池子回落：subgroup 仓数
                     ≥5 即为榜（2026-11-22 实测恰好 98 个，与榜单页
                     一致），组取该榜下仓库的多数 category，平票取
                     星标最高仓的 category。
  hiwiki-newest.json {"generatedAt", "buildId", "count", "items": [...]}
                     最新入库 60 条（hiwiki 入库顺序原样；行字段见
                     parse_newest）。平台值是 hiwiki 原始值（mac /
                     other 等），feed 词表映射由 generate.py 在拼
                     feed/newest.json 时做（feed 优先，映射只兜底）。
  harvest-log.json   {generatedAt, buildId, poolSize, ok, failed,
                      failRate, mappingRows, boards, durationSec,
                      newestStatus, newestCount}

Failure contract (plan section 6.3): exit non-zero when the sitemap
cannot be fetched / is empty, or when the per-repo failure rate
exceeds 5% (build-id problems surface here too: a stale/unparseable
build id makes payload fetches fail).
The workflow then falls back to the previous round's artifacts
(level 2) or the committed hiwiki-ranks.json (level 3) and never fails
the whole run on a hiwiki outage. Exit 0 means the outputs are fresh
and usable.
The newest fetch is exempt from all of that: it runs after the pool
outputs are written, and any failure (fetch / parse / empty rows)
only logs a WARNING, writes no hiwiki-newest.json, and never changes
the exit code -- generate.py then falls back to the previous round's
newest.json, or omits feed/newest.json, and the feed is unaffected.
The boards fetch is likewise exempt: on failure the boards are
derived from the pool (see derive_boards_from_pool), so a rank-page
outage never blocks the run.
"""
import argparse
import json
import os
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import unquote
import urllib.request

BASE = "https://git.hiwiki.org"
SITEMAP = "https://git.hiwiki.org/__sitemap__/zh-CN.xml"
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"}
# Fallback only; auto-refreshed from a repo page on failure (see
# fetch_payload). Kept in sync with the last known good value.
DEFAULT_BUILD = "f75b32fb-5f71-4330-8b78-1f0cd6db8b70"
WORKERS = 6
MAX_FAILURE_RATE = 0.05


def hyd(L, v, _seen=None):
    if isinstance(v, dict):
        return {k: hyd_ref(L, x, _seen) for k, x in v.items()}
    if isinstance(v, list):
        return [hyd_ref(L, x, _seen) for x in v]
    return v


def hyd_ref(L, x, _seen=None):
    if isinstance(x, int):
        if x == -1:
            return None
        if 0 <= x < len(L):
            # 210：索引自引用/环（如 L[i] 指回自身）会使 hyd/hyd_ref
            # 无限递归直至 RecursionError；_seen 只记当前展开路径上
            # 的索引（兄弟分支仍可正常展开，DAG 不受影响），环上
            # 重复出现的索引按原值返回，不再展开。
            if _seen is None:
                _seen = set()
            if x in _seen:
                return x
            _seen.add(x)
            try:
                return hyd(L, L[x], _seen)
            finally:
                _seen.discard(x)
        return x
    if isinstance(x, (dict, list)):
        return hyd(L, x, _seen)
    return x


def fetch(url, timeout=30):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


build = [DEFAULT_BUILD]
build_lock = threading.Lock()


def fresh_build():
    html = fetch("%s/zh-CN/repo/alibaba/nacos" % BASE)
    m = re.search(rb"_payload\.json\?_b=([A-Za-z0-9-]+)", html)
    return m.group(1).decode() if m else None


def fetch_with_build(url_template):
    """按模板拉一个 _payload.json（模板里留一个 %s 给 build id）。

    与原 fetch_payload 同一套重试：3 次尝试，失败时从仓库页 HTML
    刷新 build id 再试。返回原始 bytes，3 次都失败返回 None。
    """
    for _ in range(3):
        url = url_template % build[0]
        try:
            raw = fetch(url)
            if raw[:1] == b"[":
                return raw
        except Exception:
            pass
        try:
            nb = fresh_build()
        except Exception:
            nb = None
        if nb and nb != build[0]:
            with build_lock:
                build[0] = nb
        time.sleep(0.5)
    return None


def fetch_payload(full):
    return fetch_with_build(
        BASE + "/zh-CN/repo/" + full + "/_payload.json?_b=%s")


def fetch_newest_raw():
    return fetch_with_build(BASE + "/zh-CN/newest/_payload.json?_b=%s")


def parse_newest(raw):
    """解码 /zh-CN/newest 的 Nuxt payload，返回行列表（hiwiki 入库
    顺序原样）或 None（结构异常）。

    形状（2026-11-08 实测）：flat list 里某 dict 为 {"rows": <idx>,
    "meta": ...}，该 idx 处是 60 个行下标的 list；每行含 owner /
    repo / category / language / starCount / latestTag / platforms /
    distribution / lastReleaseTime / ownerUid / description。没有单仓
    入库时间戳——只有顺序。platforms 是 hiwiki 原始值（mac / other
    等），不做映射，映射由 generate.py 做。
    """
    try:
        L = json.loads(raw)
    except Exception:
        return None
    if not isinstance(L, list):
        return None
    rows_idx = None
    for el in L:
        if isinstance(el, dict) and isinstance(el.get("rows"), int):
            rows_idx = el["rows"]
            break
    if rows_idx is None or not (0 <= rows_idx < len(L)):
        return None
    refs = L[rows_idx]
    if not isinstance(refs, list) or not refs:
        return None
    rows = []
    for ref in refs:
        r = hyd_ref(L, ref)
        if not isinstance(r, dict):
            continue
        owner = r.get("owner")
        repo = r.get("repo")
        if not owner or not repo:
            continue
        rows.append({
            "fullName": "%s/%s" % (owner, repo),
            "description": r.get("description") or "",
            "category": r.get("category") or "other",
            "language": r.get("language") or "",
            "stars": r.get("starCount") or 0,
            "latestTag": r.get("latestTag") or "",
            "lastReleaseTime": r.get("lastReleaseTime") or "",
            "platforms": [p for p in (r.get("platforms") or [])
                          if isinstance(p, str)],
        })
    return rows or None


def parse_boards(raw):
    """解码 /zh-CN/rank 的 Nuxt payload，返回榜单列表
    [{"label": 榜单中文名, "category": hiwiki 大类}]（榜单页顺序原样）
    或 None（结构异常）。

    形状（2026-11-22 实测）：flat list 里某 dict 为 {"boards": <idx>,
    ...}，该 idx 处是榜单 dict 列表；每榜含 id / label / icon /
    category / count / top，只取 label 与 category。
    """
    try:
        L = json.loads(raw)
    except Exception:
        return None
    if not isinstance(L, list):
        return None
    boards_idx = None
    for el in L:
        if isinstance(el, dict) and isinstance(el.get("boards"), int):
            boards_idx = el["boards"]
            break
    if boards_idx is None or not (0 <= boards_idx < len(L)):
        return None
    refs = L[boards_idx]
    if not isinstance(refs, list) or not refs:
        return None
    boards = []
    for ref in refs:
        b = hyd_ref(L, ref)
        if not isinstance(b, dict):
            continue
        label = b.get("label")
        category = b.get("category")
        if isinstance(label, str) and label and isinstance(category, str) and category:
            boards.append({"label": label, "category": category})
    return boards or None


def fetch_boards():
    """拉榜单页，返回榜单列表或 None（任何失败只记 WARNING，由
    调用方按池子回落，永不抛错）。"""
    try:
        raw = fetch_with_build(BASE + "/zh-CN/rank/_payload.json?_b=%s")
        if raw is None:
            print("WARNING: 榜单页 payload 拉取失败（按池子回落）")
            return None
        boards = parse_boards(raw)
        if not boards:
            print("WARNING: 榜单页 payload 结构异常（按池子回落）")
            return None
        return boards
    except Exception as e:
        print("WARNING: 榜单页收割异常（按池子回落）：%s" % e)
        return None


def load_prev_boards(path):
    """读上一轮榜单文件（工作流传 --prev-boards），返回榜单列表或 []。
    文件缺失/损坏只记一行日志、不拦整轮。"""
    if not path:
        return []
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        boards = data.get("boards") if isinstance(data, dict) else None
        if not isinstance(boards, list):
            print("prev boards %s 结构异常（不用）" % path)
            return []
        out = []
        for b in boards:
            if (isinstance(b, dict) and isinstance(b.get("label"), str)
                    and b.get("label") and isinstance(b.get("category"), str)
                    and b.get("category")):
                out.append({"label": b["label"], "category": b["category"]})
        return out
    except Exception as e:
        print("prev boards %s 读不到（不用）：%s" % (path, e))
        return []


def derive_boards_from_pool(ok):
    """榜单页不可用时的回落：池子里 subgroup 仓数 ≥5 即为榜
    （2026-11-22 实测恰好 98 个，与榜单页一致）；组取该榜下仓库的
    多数 category，平票取星标最高仓的 category。顺序按仓数降序、
    榜名升序（确定性）。"""
    from collections import Counter, defaultdict
    sub_repos = defaultdict(list)
    for r in ok:
        sub = r.get("subgroup")
        if sub:
            sub_repos[sub].append(r)
    boards = []
    for sub, repos in sub_repos.items():
        if len(repos) < 5:
            continue
        cats = Counter(r.get("category") or "other" for r in repos)
        top_count = max(cats.values())
        tied = [c for c, n in cats.items() if n == top_count]
        if len(tied) == 1:
            category = tied[0]
        else:
            best = max(repos, key=lambda r: r.get("stars") or 0)
            category = best.get("category") or "other"
        boards.append({"label": sub, "category": category,
                       "_count": len(repos)})
    boards.sort(key=lambda b: (-b["_count"], b["label"]))
    for b in boards:
        del b["_count"]
    return boards


def harvest_newest(out_dir, generated_at):
    """拉最新入库并写 hiwiki-newest.json。

    永不抛错、永不影响整轮退出码：任何失败只打 WARNING、不写文件，
    返回 ("failed", 0)。调用方（generate.py）据此走上一轮回落或
    省略 newest.json。
    """
    try:
        raw = fetch_newest_raw()
        if raw is None:
            print("WARNING: 最新入库 payload 拉取失败（本轮无 newest.json）")
            return "failed", 0
        rows = parse_newest(raw)
        if not rows:
            print("WARNING: 最新入库 payload 结构异常（本轮无 newest.json）")
            return "failed", 0
        payload = {"generatedAt": generated_at, "buildId": build[0],
                   "count": len(rows), "items": rows}
        with open(out_dir / "hiwiki-newest.json", "w",
                  encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        print("newest: %d 条（%s .. %s）"
              % (len(rows), rows[0]["fullName"], rows[-1]["fullName"]))
        return "ok", len(rows)
    except Exception as e:
        print("WARNING: 最新入库收割异常（本轮无 newest.json）：%s" % e)
        return "failed", 0


def fetch_sitemap(url):
    """拉 sitemap，返回 /repo/ 列表（sitemap 原样大小写，去重保序）。"""
    raw = fetch(url, timeout=60)
    text = raw.decode("utf-8", "ignore")
    names = re.findall(
        r"<loc>https://git\.hiwiki\.org/zh-CN/repo/([^<]+)</loc>", text)
    seen = set()
    out = []
    for n in names:
        n = unquote(n.strip().rstrip("/"))
        if n and n.lower() not in seen:
            seen.add(n.lower())
            out.append(n)
    return out


def process(full):
    raw = fetch_payload(full)
    if raw is None:
        return {"fullName": full, "error": "fetch-failed"}
    try:
        L = json.loads(raw)
    except Exception:
        return {"fullName": full, "error": "bad-json"}
    # 85：主记录不能只看 owner+repo——payload 扁平列表里可能先出现
    # 只有这两个键的引用桩/相关条目。要求同时带主记录特征字段
    # （releases/subgroup/starCount 至少其一）；任一即可，避免
    # 无 release 的仓库因缺 releases 键被误判为无记录。
    for el in L:
        if (isinstance(el, dict) and "owner" in el and "repo" in el
                and ("releases" in el or "subgroup" in el
                     or "starCount" in el)):
            # 210：环保护之外的兜底——极深嵌套仍可能 RecursionError，
            # 按坏载荷计入失败仓库，不让单个仓库杀死整批 worker。
            try:
                r = hyd(L, el)
            except RecursionError:
                return {"fullName": full, "error": "bad-json"}
            releases = []
            for rel in r.get("releases") or []:
                if not isinstance(rel, dict):
                    continue
                releases.append({
                    "tag": rel.get("tag"),
                    "publishAt": rel.get("publishAt"),
                    "prerelease": bool(rel.get("prerelease")),
                    "assets": [{"fileName": a.get("fileName"),
                                "downloadCount": a.get("downloadCount") or 0}
                               for a in (rel.get("assets") or [])
                               if isinstance(a, dict)],
                })
            # 271：不假设 releases[] 按最新在前——取 publishAt 最大者
            # 当最新版（与 generate.py 的 select_installable_release
            # 同款口径）；publishAt 缺失按最空处理。hiwiki 载荷的
            # publishAt 为统一 ISO 格式，按串比较即按时间比较。
            latest = max(releases, key=lambda r: r.get("publishAt") or "") \
                if releases else {}
            return {
                "fullName": "%s/%s" % (r.get("owner"), r.get("repo")),
                "category": r.get("category"),
                "subgroup": r.get("subgroup"),
                "tags": r.get("tags") or [],
                "stars": r.get("starCount"),
                "language": r.get("language"),
                "latestTag": latest.get("tag"),
                "lastReleaseTime": latest.get("publishAt")
                or r.get("lastReleaseTime"),
                "pushedAt": r.get("pushedAt"),
                "ownerAvatar": r.get("ownerAvatar"),
                "description": r.get("description"),
                "releases": releases,
            }
    return {"fullName": full, "error": "no-record"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default=".")
    ap.add_argument("--sitemap", default=SITEMAP)
    ap.add_argument("--workers", type=int, default=WORKERS)
    ap.add_argument("--prev-boards", default=None,
                    help="上一轮 hiwiki-boards.json 路径；榜单页拉取失败时"
                         "先用它保组归属，再补池子回落的新榜")
    args = ap.parse_args()
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    t0 = time.time()

    # 1. sitemap 取池子（失败即整步失败，工作流走回落）。
    try:
        names = fetch_sitemap(args.sitemap)
    except Exception as e:
        print("ERROR: sitemap 拉取失败：%s" % e)
        return 1
    if not names:
        print("ERROR: sitemap 无仓库条目")
        return 1
    print("sitemap: %d 个仓库" % len(names), flush=True)

    # 2. 逐仓拉 payload（6 线程；build id 失败时自动刷新）。
    lock = threading.Lock()
    count = [0]

    def run_one(full):
        res = process(full)
        with lock:
            count[0] += 1
            if count[0] % 200 == 0:
                print("%d/%d" % (count[0], len(names)), flush=True)
        return res

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        results = list(ex.map(run_one, names))
    ok = [r for r in results if "error" not in r]
    failed = len(results) - len(ok)
    fail_rate = failed / len(results) if results else 1.0

    # 3. 榜单全集（榜单页优先，失败按池子回落）+ 产出池子 + 对照表。
    # 榜单集合每轮现取自 hiwiki，不再写死在代码里；对照表只保留
    # 在榜单里的 subgroup（池子里 168 个 subgroup，榜单 98 个）。
    # 榜单页失败时：先用上一轮榜单文件保组归属（hiwiki 的分组不是
    # 多数 category 能推出来的——2026-11-22 实测 3 个榜会错组），
    # 再把池子回落里多出来的新榜按推导组补上；上一轮文件也没有
    # 才全按池子回落。
    boards = fetch_boards()
    if boards:
        print("boards: 榜单页 %d 个" % len(boards))
    else:
        prev_boards = load_prev_boards(args.prev_boards)
        derived = derive_boards_from_pool(ok)
        if prev_boards:
            known = {b["label"] for b in prev_boards}
            extra = [b for b in derived if b["label"] not in known]
            boards = prev_boards + extra
            print("boards: 榜单页不可用，上一轮 %d 个 + 池子回落新榜 %d 个"
                  % (len(prev_boards), len(extra)))
        else:
            boards = derived
            print("boards: 榜单页不可用，按池子回落 %d 个" % len(boards))
    board_labels = {b["label"] for b in boards}
    generated_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    duration = round(time.time() - t0, 1)
    mapping = {}
    dropped = 0
    for r in ok:
        sub = r.get("subgroup")
        if sub in board_labels:
            mapping[r["fullName"].lower()] = sub
        elif sub:
            dropped += 1
    print("boards: 对照表 %d 条，%d 条不在榜单内已丢弃"
          % (len(mapping), dropped))
    pool = {"generatedAt": generated_at, "buildId": build[0],
            "poolSize": len(ok), "ok": len(ok), "failed": failed,
            "durationSec": duration, "records": ok}
    with open(out_dir / "hiwiki-pool.json", "w", encoding="utf-8") as f:
        json.dump(pool, f, ensure_ascii=False, separators=(",", ":"))
    with open(out_dir / "hiwiki-ranks.json", "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, separators=(",", ":"))
    with open(out_dir / "hiwiki-boards.json", "w", encoding="utf-8") as f:
        json.dump({"generatedAt": generated_at, "boards": boards},
                  f, ensure_ascii=False, separators=(",", ":"))
    # 4. 最新入库（失败不影响整轮：无文件 + 日志记 failed 即可）。
    newest_status, newest_count = harvest_newest(out_dir, generated_at)
    log = {"generatedAt": generated_at, "buildId": build[0],
           "poolSize": len(ok), "ok": len(ok), "failed": failed,
           "failRate": round(fail_rate, 4), "mappingRows": len(mapping),
           "boards": len(boards),
           "durationSec": duration,
           "newestStatus": newest_status, "newestCount": newest_count}
    with open(out_dir / "harvest-log.json", "w", encoding="utf-8") as f:
        json.dump(log, f, ensure_ascii=False, separators=(",", ":"))
    print("DONE pool=%d ok=%d failed=%d failRate=%.2f%% mapping=%d rows "
          "boards=%d newest=%s/%d build=%s %.1fs"
          % (len(ok), len(ok), failed, fail_rate * 100, len(mapping),
             len(boards), newest_status, newest_count, build[0], duration))
    if fail_rate > MAX_FAILURE_RATE:
        print("ERROR: 失败率 %.2f%% > 5%%，按失败处理（工作流走回落）"
              % (fail_rate * 100))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
