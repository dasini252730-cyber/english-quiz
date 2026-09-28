import json
import subprocess
import sys
from pathlib import Path


def main():
    event = json.load(sys.stdin)
    cwd = Path(event.get("cwd") or ".").resolve()
    root = next((path for path in (cwd, *cwd.parents) if (path / "backlog.json").exists()), cwd)
    try:
        branch = subprocess.check_output(
            ["git", "-C", str(root), "branch", "--show-current"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        branch = "Git 저장소 없음"
    message = f"현재 프로젝트 브랜치: {branch}."
    if branch == "main":
        message += " 작업 전 `dev` 브랜치로 전환하세요."
    print(json.dumps({"systemMessage": message}, ensure_ascii=False))


if __name__ == "__main__":
    main()
