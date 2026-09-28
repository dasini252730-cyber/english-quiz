# Backlog CLI

Python 3 표준 라이브러리만 사용합니다. 프로젝트 루트에서 실행하세요.

```powershell
py .\backlog_cli.py list
py .\backlog_cli.py list --status todo
py .\backlog_cli.py show 001
py .\backlog_cli.py add --title "새 작업" --description "작업 설명" --priority P1 --estimate M --depends-on 001,002 --labels ui,polish
py .\backlog_cli.py update 001 --status in_progress
py .\backlog_cli.py update 001 --title "제목 변경" --labels ""
py .\backlog_cli.py dashboard
```

## 명령

- `list` (`ls`): 작업을 표시하며 상태와 우선순위로 필터링할 수 있습니다.
- `show <id>`: 작업 필드와 연결된 상세 문서를 표시합니다.
- `add`: 새 ID를 자동으로 발급하고 `backlog/<id>.md` 상세 문서 초안을 만듭니다.
- `update <id>` (`edit`): 지정한 필드만 변경합니다. 쉼표로 구분하는 `--labels`, `--depends-on`은 값 생략 시 유지되고 빈 문자열이면 비워집니다.
- `dashboard`: 최신 백로그를 읽어 `backlog-dashboard.html` 단일 파일을 생성합니다. 브라우저에서 열어 상태별 개수, 완료율, 검색/필터와 각 상세 문서 링크를 확인할 수 있습니다.

상태값은 `backlog.json`의 `statuses` 키를 사용합니다: `todo`, `in_progress`, `review_needed`, `needs_human`, `done`, `canceled`, `waiting`.
수정 내용은 검증 후 임시 파일을 통해 원자적으로 `backlog.json`에 저장하며 `updated_at`을 갱신합니다.
성공한 `add`/`update`는 기본 `backlog-dashboard.html`도 자동으로 갱신합니다. 열어 둔 브라우저는 새로고침하세요. HTML 갱신만 실패하면 저장된 백로그는 유지하며 경고를 표시합니다. `dashboard` 명령으로 다시 생성할 수 있습니다.
