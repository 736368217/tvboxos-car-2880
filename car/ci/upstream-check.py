#!/usr/bin/env python3
"""上游跟进体检：检测 TVBoxOS 新提交，预演与本仓库的合并，输出机器可读报告。

只读脚本，不修改工作区：`git merge-tree --write-tree` 在临时对象库里做合并预演，
不碰 index、不碰工作区。退出码：

    0  已是最新
    10 有上游更新，可自动合并（无内容冲突）
    11 有上游更新，且存在需要人工解决的冲突
    2  运行环境/仓库状态异常

用法：
    python car/ci/upstream-check.py            # 人类可读摘要
    python car/ci/upstream-check.py --json     # 机器可读（CI / 报告用）
    python car/ci/upstream-check.py --mark     # 同时把状态写进 .git/upstream-state.json
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UPSTREAM_REMOTE = "upstream"
UPSTREAM_BRANCH = "main"
BASE_BRANCH = "main"
STATE_FILE = ROOT / ".git" / "upstream-state.json"

# 这些路径是我们改动最多、冲突概率最高的区域，报告里单独点名。
HOT_PATHS = (
    "app/src/main/java/com/github/tvbox/osc/player/ExoPlayer.java",
    "app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java",
    "app/src/main/java/com/github/tvbox/osc/ui/activity/DetailActivity.java",
    "app/src/main/java/com/github/tvbox/osc/ui/activity/HomeActivity.java",
    "app/src/python/java/com/github/catvod/crawler/pyLoader.java",
    "app/src/main/java/com/github/catvod/crawler/ProtectedInitJar.java",
)


def git(*args, check=True):
    result = subprocess.run(
        ["git", *args], cwd=str(ROOT), capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result


def rev(ref):
    out = git("rev-parse", "--verify", ref, check=False)
    return out.stdout.strip() if out.returncode == 0 else None


def fetch_upstream():
    # CI 里网络可能被墙，失败不致命：用已有远端引用继续体检。
    result = subprocess.run(
        ["git", "fetch", UPSTREAM_REMOTE, "--tags", "--quiet"],
        cwd=str(ROOT), capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    return result.returncode == 0, (result.stderr or "").strip()


def merge_preview(head, upstream):
    """用 merge-tree 预演合并：返回 (可合并?, 冲突文件列表)。

    `merge-tree --write-tree --name-only <ours> <theirs>` 在 stdout 输出树 OID 与冲突
    文件名，并把 `CONFLICT (content): Merge conflict in <path>` 写到 stderr。
    两种来源都解析，避免依赖单一格式。
    """
    result = git("merge-tree", "--write-tree", "--name-only", head, upstream, check=False)
    if result.returncode == 0:
        return True, []
    conflicts = set()
    for line in (result.stderr or "").splitlines():
        marker = re.search(r"Merge conflict in (.+?)\s*$", line)
        if marker:
            conflicts.add(marker.group(1).strip())
    for line in (result.stdout or "").splitlines():
        name = line.strip()
        if not name or re.fullmatch(r"[0-9a-f]{40}", name):
            continue
        if "/" in name and not name.startswith(("Auto-merging", "CONFLICT", "Merge")):
            conflicts.add(name)
    return False, sorted(conflicts)


def changed_files(a, b):
    out = git("diff", "--name-only", a, b, check=False)
    return [line for line in out.stdout.splitlines() if line.strip()]


def commits_behind(a, b):
    out = git("log", "--oneline", "--no-decorate", f"{a}..{b}", check=False)
    lines = [line for line in out.stdout.splitlines() if line.strip()]
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="输出机器可读 JSON")
    parser.add_argument("--mark", action="store_true", help="把结果写入 .git/upstream-state.json")
    parser.add_argument("--no-fetch", action="store_true", help="不访问网络，只用本地引用")
    parser.add_argument("--base", default=None, help="用指定基线代替本地 main（回溯/自检用）")
    args = parser.parse_args()

    head = rev(args.base or BASE_BRANCH)
    if not head:
        print("ERROR: 找不到本地 main 分支", file=sys.stderr)
        return 2

    fetched, fetch_error = (True, "")
    if not args.no_fetch:
        fetched, fetch_error = fetch_upstream()

    upstream = rev(f"{UPSTREAM_REMOTE}/{UPSTREAM_BRANCH}")
    if not upstream:
        print(f"ERROR: 找不到 {UPSTREAM_REMOTE}/{UPSTREAM_BRANCH}，先配置 upstream 远端", file=sys.stderr)
        return 2

    behind = commits_behind(head, upstream)
    if not behind:
        report = {
            "status": "up-to-date",
            "head": head,
            "upstream": upstream,
            "fetched": fetched,
            "fetch_error": fetch_error,
            "behind_count": 0,
        }
        emit(report, args)
        return 0

    mergeable, conflicts = merge_preview(head, upstream)
    files = changed_files(head, upstream)
    hot = [f for f in files if f in HOT_PATHS]
    report = {
        "status": "mergeable" if mergeable else "conflicts",
        "head": head,
        "upstream": upstream,
        "fetched": fetched,
        "fetch_error": fetch_error,
        "behind_count": len(behind),
        "behind_commits": behind,
        "upstream_changed_files": len(files),
        "conflict_files": conflicts,
        "hot_paths_touched": hot,
    }
    emit(report, args)
    return 10 if mergeable else 11


def emit(report, args):
    if args.mark:
        try:
            STATE_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        except OSError as error:
            print(f"WARN: 无法写 {STATE_FILE}: {error}", file=sys.stderr)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return
    status = report["status"]
    if status == "up-to-date":
        print(f"上游已是最新（{report['upstream'][:8]}），无需合并。")
        return
    print(f"上游有 {report['behind_count']} 个新提交：{report['head'][:8]} -> {report['upstream'][:8]}")
    if report.get("fetch_error"):
        print(f"  注意：fetch 失败，用的是本地已有引用（{report['fetch_error'][:120]}）")
    for line in report["behind_commits"][:40]:
        print(f"    {line}")
    if status == "mergeable":
        print(f"  预演结果：可自动合并，无内容冲突（上游改动 {report['upstream_changed_files']} 个文件）。")
    else:
        print(f"  预演结果：{len(report['conflict_files'])} 个文件需要人工解决：")
        for path in report["conflict_files"]:
            print(f"    {path}")
    if report["hot_paths_touched"]:
        print("  触及我们改动密集的文件（合并后必须重点回归）：")
        for path in report["hot_paths_touched"]:
            print(f"    {path}")


if __name__ == "__main__":
    sys.exit(main())
