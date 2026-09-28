import json
import os
import re
import subprocess
import sys
from pathlib import Path


CLI_CALL = re.compile(r"backlog_cli\.py\s+(add|update|edit)\b", re.IGNORECASE)
DONE_CALL = re.compile(r"--status\s+['\"]?done\b", re.IGNORECASE)


def git(root, *args, check=True):
    env = os.environ.copy()
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GCM_INTERACTIVE"] = "Never"
    return subprocess.run(["git", "-C", str(root), *args], text=True, capture_output=True, check=check, env=env)


def main():
    event = json.load(sys.stdin)
    if event.get("tool_name") != "Bash":
        return
    command = (event.get("tool_input") or {}).get("command", "")
    match = CLI_CALL.search(command)
    if not match:
        return
    cwd = Path(event.get("cwd") or ".").resolve()
    root = next((path for path in (cwd, *cwd.parents) if (path / "backlog.json").exists()), cwd)
    try:
        git(root, "rev-parse", "--is-inside-work-tree")
    except (OSError, subprocess.CalledProcessError):
        print(json.dumps({"systemMessage": "Backlog는 수정됐지만 Git 저장소가 없어 자동 commit을 건너뛰었습니다."}, ensure_ascii=False))
        return

    done = match.group(1).lower() in {"update", "edit"} and DONE_CALL.search(command)
    paths = ["-A"] if done else ["backlog.json"]
    if not done and match.group(1).lower() == "add":
        try:
            data = json.loads((root / "backlog.json").read_text(encoding="utf-8"))
            task = data["tasks"][-1]
            paths.append(task["details"])
            title = task["title"]
            task_id = task["id"]
        except (OSError, ValueError, KeyError, IndexError):
            title, task_id = "새 작업", ""
    else:
        title, task_id = "", ""

    git(root, "add", "--", *paths)
    staged = git(root, "diff", "--cached", "--quiet", check=False)
    if staged.returncode == 0:
        return
    if done:
        changed = git(root, "diff", "--cached", "--name-only").stdout.splitlines()
        summary = "\n".join(f"- {path}" for path in changed) or "- backlog 완료 처리"
        message = f"chore(backlog): complete {task_id}\n\n작업 변경 내역 요약\n{summary}\n"
        (root / ".codex" / "hooks" / ".commit-message.tmp").write_text(message, encoding="utf-8")
        git(root, "commit", "-F", str(root / ".codex" / "hooks" / ".commit-message.tmp"))
        (root / ".codex" / "hooks" / ".commit-message.tmp").unlink(missing_ok=True)
        pushed = git(root, "push", check=False)
        if pushed.returncode:
            print(json.dumps({"systemMessage": f"완료 작업은 commit했지만 push에 실패했습니다: {pushed.stderr.strip()}"}, ensure_ascii=False))
        else:
            print(json.dumps({"systemMessage": "완료 상태로 바뀐 작업과 변경 내역을 요약해 commit하고 현재 브랜치에 push했습니다."}, ensure_ascii=False))
    else:
        suffix = f" {task_id} {title}".strip()
        git(root, "commit", "-m", f"chore(backlog): update{suffix}")
        print(json.dumps({"systemMessage": "Backlog 변경을 자동 commit했습니다."}, ensure_ascii=False))


if __name__ == "__main__":
    main()
