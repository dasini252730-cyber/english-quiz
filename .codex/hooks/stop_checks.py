import json
import subprocess
import sys
from pathlib import Path


CODE_EXTENSIONS = {".kt", ".java", ".kts", ".py", ".xml"}


def git_files(root):
    tracked = subprocess.run(["git", "-C", str(root), "diff", "--name-only", "HEAD"], text=True, capture_output=True)
    untracked = subprocess.run(
        ["git", "-C", str(root), "ls-files", "--others", "--exclude-standard"], text=True, capture_output=True
    )
    if tracked.returncode or untracked.returncode:
        return set()
    return set(tracked.stdout.splitlines()) | set(untracked.stdout.splitlines())


def project_sources(root):
    ignored = {".git", ".gradle", ".codex", ".agents", "build", "__pycache__"}
    result = []
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in CODE_EXTENSIONS:
            if not ignored.intersection(path.relative_to(root).parts):
                result.append(path.relative_to(root).as_posix())
    return set(result)


def run(command, root):
    print(f"실행: {' '.join(command)}", file=sys.stderr)
    return subprocess.run(command, cwd=root).returncode


def main():
    event = json.load(sys.stdin)
    cwd = Path(event.get("cwd") or ".").resolve()
    root = next((path for path in (cwd, *cwd.parents) if (path / "backlog.json").exists()), cwd)
    changed = git_files(root)
    if not changed:
        changed = project_sources(root)
    source = [path for path in changed if Path(path).suffix.lower() in CODE_EXTENSIONS]
    if not source:
        return

    limits = json.loads((root / ".codex" / "line-limits.json").read_text(encoding="utf-8"))
    warnings, over = [], []
    for relative in sorted(source):
        path = root / relative
        if not path.is_file():
            continue
        limit = limits["limits"].get(path.suffix.lower())
        if not limit:
            continue
        lines = len(path.read_text(encoding="utf-8").splitlines())
        if lines > limit:
            over.append(f"{relative}: {lines}/{limit} lines; refactor before finishing")
        elif lines >= limit * limits["warn_ratio"]:
            warnings.append(f"{relative}: {lines}/{limit} lines (85%+; consider splitting)")

    if over:
        print(json.dumps({"decision": "block", "reason": "파일 줄 수 상한을 넘었습니다. 다시 나누거나 정리하세요: " + "; ".join(over)}, ensure_ascii=False))
        return

    python_changed = any(Path(name).suffix.lower() == ".py" for name in source)
    android_changed = any(Path(name).suffix.lower() in {".kt", ".java", ".kts", ".xml"} for name in source)
    failures = []
    if python_changed:
        py = "py" if sys.platform == "win32" else "python3"
        targets = [name for name in source if Path(name).suffix.lower() == ".py"]
        if run([py, "-m", "py_compile", *targets], root):
            failures.append("Python lint/syntax check failed")
        if run([py, "-m", "compileall", "-q", *targets], root):
            failures.append("Python build/bytecode check failed")
    if android_changed:
        wrapper = root / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
        if not wrapper.is_file():
            failures.append("Android source changed but Gradle wrapper is missing; configure/build the Android project before completion")
        else:
            gradle = ["cmd", "/c", str(wrapper)] if sys.platform == "win32" else [str(wrapper)]
            if run([*gradle, "lint"], root):
                failures.append("Android lint failed")
            if run([*gradle, "assembleDebug"], root):
                failures.append("Android build failed")

    messages = warnings + failures
    if failures:
        print(json.dumps({"decision": "block", "reason": "필수 lint/build gate 실패: " + "; ".join(messages)}, ensure_ascii=False))
    elif messages:
        print(json.dumps({"systemMessage": "줄 수 경고: " + "; ".join(messages)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
