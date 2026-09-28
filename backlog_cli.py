#!/usr/bin/env python3
"""CLI to inspect and maintain this project's backlog.json."""

from __future__ import annotations

import argparse
from html import escape
import json
import os
import re
import sys
import tempfile
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parent
BACKLOG_PATH = ROOT / "backlog.json"
ID_RE = re.compile(r"^\d{3,}$")


class BacklogError(Exception):
    pass


def load_backlog() -> dict:
    try:
        data = json.loads(BACKLOG_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise BacklogError(f"Backlog file not found: {BACKLOG_PATH}") from exc
    except json.JSONDecodeError as exc:
        raise BacklogError(f"Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}") from exc
    if not isinstance(data.get("tasks"), list) or not isinstance(data.get("statuses"), dict):
        raise BacklogError("backlog.json must contain 'tasks' and 'statuses'.")
    return data


def validate(data: dict) -> None:
    ids: set[str] = set()
    status_names = set(data["statuses"])
    for task in data["tasks"]:
        task_id = str(task.get("id", ""))
        if not ID_RE.fullmatch(task_id):
            raise BacklogError(f"Invalid task ID: {task_id!r} (use at least 3 digits).")
        if task_id in ids:
            raise BacklogError(f"Duplicate task ID: {task_id}")
        ids.add(task_id)
        if task.get("status") not in status_names:
            raise BacklogError(f"Task {task_id} has unknown status: {task.get('status')!r}")
        if not task.get("title") or not task.get("description") or not task.get("details"):
            raise BacklogError(f"Task {task_id} needs title, description, and details.")
    for task in data["tasks"]:
        unknown = set(task.get("depends_on", [])) - ids
        if unknown:
            raise BacklogError(f"Task {task['id']} has unknown dependencies: {', '.join(sorted(unknown))}")


def save_backlog(data: dict) -> None:
    validate(data)
    data["updated_at"] = date.today().isoformat()
    content = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    fd, temp_name = tempfile.mkstemp(prefix="backlog-", suffix=".json.tmp", dir=ROOT)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(content)
        os.replace(temp_name, BACKLOG_PATH)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
    # The JSON is already committed; a derived-view failure must not roll it back.
    try:
        write_dashboard(data, ROOT / "backlog-dashboard.html")
    except (OSError, UnicodeError) as exc:
        print(f"백로그는 저장됐지만 대시보드 갱신에 실패했습니다: {exc}. dashboard 명령으로 재시도하세요.", file=sys.stderr)


def find_task(data: dict, task_id: str) -> dict:
    for task in data["tasks"]:
        if str(task["id"]) == task_id:
            return task
    raise BacklogError(f"Task not found: {task_id}")


def csv_values(value: str | None) -> list[str] | None:
    if value is None:
        return None
    return [part.strip() for part in value.split(",") if part.strip()]


def command_list(args: argparse.Namespace) -> None:
    data = load_backlog()
    tasks = data["tasks"]
    if args.status:
        tasks = [task for task in tasks if task["status"] == args.status]
    if args.priority:
        tasks = [task for task in tasks if task.get("priority") == args.priority]
    if not tasks:
        print("조건에 맞는 작업이 없습니다.")
        return
    print(f"{'ID':<5} {'상태':<18} {'우선순위':<8} 제목")
    print("-" * 76)
    for task in tasks:
        status_label = data["statuses"].get(task["status"], task["status"])
        print(f"{task['id']:<5} {status_label:<18} {task.get('priority', '-'):<8} {task['title']}")


def command_show(args: argparse.Namespace) -> None:
    data = load_backlog()
    task = find_task(data, args.id)
    print(f"#{task['id']} {task['title']}")
    print(f"상태: {task['status']} ({data['statuses'][task['status']]})")
    print(f"우선순위: {task.get('priority', '-')}")
    print(f"예상 규모: {task.get('estimate', '-')}")
    print(f"설명: {task['description']}")
    print(f"상세 문서: {task['details']}")
    print(f"선행 작업: {', '.join(task.get('depends_on', [])) or '-'}")
    print(f"라벨: {', '.join(task.get('labels', [])) or '-'}")
    detail_path = (ROOT / task["details"]).resolve()
    if detail_path.is_relative_to(ROOT) and detail_path.is_file():
        print("\n--- 상세 문서 ---\n")
        print(detail_path.read_text(encoding="utf-8"), end="")


def command_add(args: argparse.Namespace) -> None:
    data = load_backlog()
    current_ids = {int(task["id"]) for task in data["tasks"] if str(task["id"]).isdigit()}
    task_id = str(max(current_ids, default=0) + 1).zfill(3)
    details = args.details or f"backlog/{task_id}.md"
    detail_path = (ROOT / details).resolve()
    if not detail_path.is_relative_to(ROOT):
        raise BacklogError("상세 문서 경로는 프로젝트 폴더 안이어야 합니다.")
    if detail_path.exists():
        raise BacklogError(f"상세 문서가 이미 있습니다: {details}")
    task = {
        "id": task_id,
        "title": args.title,
        "description": args.description,
        "details": details.replace("\\", "/"),
        "status": args.status,
        "priority": args.priority,
        "estimate": args.estimate,
        "depends_on": csv_values(args.depends_on) or [],
        "labels": csv_values(args.labels) or [],
    }
    data["tasks"].append(task)
    validate(data)
    detail_path.parent.mkdir(parents=True, exist_ok=True)
    detail_path.write_text(f"# {task_id} {args.title}\n\n## 목적\n{args.description}\n\n## 작업 범위\n- TODO\n\n## 완료 기준\n- TODO\n\n## 선행 작업\n{', '.join(task['depends_on']) or '없음'}\n", encoding="utf-8")
    try:
        save_backlog(data)
    except Exception:
        detail_path.unlink(missing_ok=True)
        raise
    print(f"작업을 추가했습니다: {task_id} ({details})")


def command_update(args: argparse.Namespace) -> None:
    data = load_backlog()
    task = find_task(data, args.id)
    changes = {
        "title": args.title,
        "description": args.description,
        "status": args.status,
        "priority": args.priority,
        "estimate": args.estimate,
        "details": args.details,
    }
    for key, value in changes.items():
        if value is not None:
            task[key] = value
    labels = csv_values(args.labels)
    dependencies = csv_values(args.depends_on)
    if labels is not None:
        task["labels"] = labels
    if dependencies is not None:
        task["depends_on"] = dependencies
    save_backlog(data)
    print(f"작업을 수정했습니다: {args.id}")


def command_dashboard(args: argparse.Namespace) -> None:
    data = load_backlog()
    validate(data)
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(ROOT):
        raise BacklogError("대시보드 파일은 프로젝트 폴더 안에 저장해야 합니다.")
    write_dashboard(data, output)
    print(f"대시보드를 생성했습니다: {output.relative_to(ROOT)}")


def write_dashboard(data: dict, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    template = (ROOT / "backlog-dashboard.template.html").read_text(encoding="utf-8")
    html = template.replace("__DATA__", payload)
    fd, temp_name = tempfile.mkstemp(prefix="dashboard-", suffix=".html.tmp", dir=output.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(html)
        os.replace(temp_name, output)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def make_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="backlog.json 조회 및 편집 도구")
    subparsers = parser.add_subparsers(dest="command", required=True)

    listing = subparsers.add_parser("list", aliases=["ls"], help="작업 목록 조회")
    listing.add_argument("--status", choices=None, help="상태 코드로 필터링")
    listing.add_argument("--priority", choices=["P0", "P1", "P2"], help="우선순위로 필터링")
    listing.set_defaults(func=command_list)

    showing = subparsers.add_parser("show", help="작업과 상세 문서 조회")
    showing.add_argument("id", help="작업 ID (예: 001)")
    showing.set_defaults(func=command_show)

    adding = subparsers.add_parser("add", help="작업과 상세 문서 추가")
    adding.add_argument("--title", required=True, help="작업 제목")
    adding.add_argument("--description", required=True, help="간단한 설명")
    adding.add_argument("--status", default="todo", help="상태 코드 (기본값: todo)")
    adding.add_argument("--priority", choices=["P0", "P1", "P2"], default="P1")
    adding.add_argument("--estimate", choices=["S", "M", "L", "XL"], default="M")
    adding.add_argument("--depends-on", default="", help="선행 작업 ID, 쉼표 구분")
    adding.add_argument("--labels", default="", help="라벨, 쉼표 구분")
    adding.add_argument("--details", help="상세 문서 경로 (기본값: backlog/<id>.md)")
    adding.set_defaults(func=command_add)

    updating = subparsers.add_parser("update", aliases=["edit"], help="기존 작업 수정")
    updating.add_argument("id", help="작업 ID")
    updating.add_argument("--title")
    updating.add_argument("--description")
    updating.add_argument("--status")
    updating.add_argument("--priority", choices=["P0", "P1", "P2"])
    updating.add_argument("--estimate", choices=["S", "M", "L", "XL"])
    updating.add_argument("--depends-on", help="선행 작업 ID, 쉼표 구분")
    updating.add_argument("--labels", help="라벨, 쉼표 구분 (빈 문자열이면 초기화)")
    updating.add_argument("--details", help="상세 문서 링크 경로")
    updating.set_defaults(func=command_update)

    dashboard = subparsers.add_parser("dashboard", help="현재 backlog로 단일 HTML 대시보드 생성")
    dashboard.add_argument("--output", default="backlog-dashboard.html", help="출력 경로 (프로젝트 안, 기본값: backlog-dashboard.html)")
    dashboard.set_defaults(func=command_dashboard)
    return parser


def main() -> int:
    parser = make_parser()
    args = parser.parse_args()
    try:
        if getattr(args, "status", None) and args.status not in load_backlog()["statuses"]:
            raise BacklogError(f"알 수 없는 상태: {args.status}. 허용값: {', '.join(load_backlog()['statuses'])}")
        args.func(args)
        return 0
    except BacklogError as exc:
        print(f"오류: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
