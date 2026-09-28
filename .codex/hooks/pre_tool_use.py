import json
import re
import sys


BACKLOG_NAME = re.compile(r"(?:^|[\\/\s'\"]|--)(?:\.\/|\.\\)?backlog\.json(?:$|[\\/\s'\"])", re.IGNORECASE)
def patch_targets(command):
    lines = command.splitlines()
    targets = []
    for index, line in enumerate(lines):
        if line.startswith(("*** Add File:", "*** Update File:", "*** Delete File:", "*** Move to:")):
            targets.append(line.split(":", 1)[1].strip())
    return targets


def deny(reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }}, ensure_ascii=False))
    raise SystemExit(0)


def main():
    event = json.load(sys.stdin)
    name = event.get("tool_name", "")
    args = event.get("tool_input", {})
    command = args.get("command", "") if isinstance(args, dict) else ""
    if name == "apply_patch":
        targets = patch_targets(command)
        if any(target.replace("\\", "/").split("/")[-1].lower() == "backlog.json" for target in targets):
            deny("backlog.json은 직접 수정할 수 없습니다. backlog CLI의 add/update 명령을 사용하세요.")
    elif name == "Bash" and BACKLOG_NAME.search(command):
        deny("backlog.json은 직접 접근할 수 없습니다. `py .\\backlog_cli.py list/show`로 조회하고 `update/add`로 수정하세요.")
    elif name not in {"Bash", "apply_patch"}:
        encoded = json.dumps(args, ensure_ascii=False)
        if re.search(r"(?:^|[\\/\s'\"])(?:\.\/|\.\\)?backlog\.json(?:$|[\\/\s'\"])", encoded, re.IGNORECASE):
            deny("backlog.json은 직접 접근할 수 없습니다. backlog CLI를 사용하세요.")


if __name__ == "__main__":
    main()
