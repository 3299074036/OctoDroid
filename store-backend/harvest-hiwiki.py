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
  hiwiki-ranks.json  {lower fullName: subgroup} for the 96-rank
                     subgroups only (mapping; RANK_NAME_SET is imported
                     from generate.py in the same directory -- one
                     source of truth for the 96 names)
  hiwiki-newest.json {"generatedAt", "buildId", "count", "items": [...]}
                     最新入库 60 条（hiwiki 入库顺序原样；行字段见
                     parse_newest）。平台值是 hiwiki 原始值（mac /
                     other 等），feed 词表映射由 generate.py 在拼
                     feed/newest.json 时做（feed 优先，映射只兜底）。
  harvest-log.json   {generatedAt, buildId, poolSize, ok, failed,
                      failRate, mappingRows, durationSec,
                      newestStatus, newestCount}

Failure contract (plan section 6.3): exit non-zero when the sitemap
cannot be fetched / is empty, when RANK_NAME_SET cannot be imported,
or when the per-repo failure rate exceeds 5% (build-id problems surface
here too: a stale/unparseable build id makes payload fetches fail).
The workflow then falls back to the previous round's artifacts
(level 2) or the committed hiwiki-ranks.json (level 3) and never fails
the whole run on a hiwiki outage. Exit 0 means the outputs are fresh
and usable.
The newest fetch is exempt from all of that: it runs after the pool
outputs are written, and any failure (fetch / parse / empty rows)
only logs a WARNING, writes no hiwiki-newest.json, and never changes
the exit code -- generate.py then falls back to the previous round's
newest.json, or omits feed/newest.json, and the feed is unaffected.
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


def hyd(L, v):
    if isinstance(v, dict):
        return {k: hyd_ref(L, x) for k, x in v.items()}
    if isinstance(v, list):
        return [hyd_ref(L, x) for x in v]
    return v


def hyd_ref(L, x):
    if isinstance(x, int):
        if x == -1:
            return None
        if 0 <= x < len(L):
            return hyd(L, L[x])
        return x
    if isinstance(x, (dict, list)):
        return hyd(L, x)
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
    for el in L:
        if isinstance(el, dict) and "owner" in el and "repo" in el:
            r = hyd(L, el)
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
            first = releases[0] if releases else {}
            return {
                "fullName": "%s/%s" % (r.get("owner"), r.get("repo")),
                "category": r.get("category"),
                "subgroup": r.get("subgroup"),
                "tags": r.get("tags") or [],
                "stars": r.get("starCount"),
                "language": r.get("language"),
                "latestTag": first.get("tag"),
                "lastReleaseTime": first.get("publishAt")
                or r.get("lastReleaseTime"),
                "pushedAt": r.get("pushedAt"),
                "ownerAvatar": r.get("ownerAvatar"),
                "description": r.get("description"),
                "releases": releases,
            }
    return {"fullName": full, "error": "no-record"}


def load_rank_names():
    """96 榜名集合：从同目录 generate.py 导入，单一事实来源。"""
    here = os.path.dirname(os.path.abspath(__file__))
    if here not in sys.path:
        sys.path.insert(0, here)
    try:
        from generate import RANK_NAME_SET
    except Exception as e:
        print("ERROR: 无法从 generate.py 导入 RANK_NAME_SET：%s" % e)
        return None
    return RANK_NAME_SET


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default=".")
    ap.add_argument("--sitemap", default=SITEMAP)
    ap.add_argument("--workers", type=int, default=WORKERS)
    args = ap.parse_args()
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    t0 = time.time()

    rank_names = load_rank_names()
    if rank_names is None:
        return 2

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

    # 3. 产出池子 + 对照表。
    generated_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    duration = round(time.time() - t0, 1)
    mapping = {}
    for r in ok:
        sub = r.get("subgroup")
        if sub in rank_names:
            mapping[r["fullName"].lower()] = sub
    pool = {"generatedAt": generated_at, "buildId": build[0],
            "poolSize": len(ok), "ok": len(ok), "failed": failed,
            "durationSec": duration, "records": ok}
    with open(out_dir / "hiwiki-pool.json", "w", encoding="utf-8") as f:
        json.dump(pool, f, ensure_ascii=False, separators=(",", ":"))
    with open(out_dir / "hiwiki-ranks.json", "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, separators=(",", ":"))
    # 4. 最新入库（失败不影响整轮：无文件 + 日志记 failed 即可）。
    newest_status, newest_count = harvest_newest(out_dir, generated_at)
    log = {"generatedAt": generated_at, "buildId": build[0],
           "poolSize": len(ok), "ok": len(ok), "failed": failed,
           "failRate": round(fail_rate, 4), "mappingRows": len(mapping),
           "durationSec": duration,
           "newestStatus": newest_status, "newestCount": newest_count}
    with open(out_dir / "harvest-log.json", "w", encoding="utf-8") as f:
        json.dump(log, f, ensure_ascii=False, separators=(",", ":"))
    print("DONE pool=%d ok=%d failed=%d failRate=%.2f%% mapping=%d rows "
          "newest=%s/%d build=%s %.1fs"
          % (len(ok), len(ok), failed, fail_rate * 100, len(mapping),
             newest_status, newest_count, build[0], duration))
    if fail_rate > MAX_FAILURE_RATE:
        print("ERROR: 失败率 %.2f%% > 5%%，按失败处理（工作流走回落）"
              % (fail_rate * 100))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
