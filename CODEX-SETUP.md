# Codex 프로젝트 설정

이 저장소는 Claude 설정 대신 Codex의 프로젝트 지침과 custom agent 형식을 사용합니다.

- `AGENTS.md`: 제품 범위, 작업 절차, 품질 및 Git 규칙
- `.codex/agents/backlog-guide.toml`: backlog 업무 설명과 관련 파일 조사
- `.codex/agents/code-critic.toml`: 파일 변경마다 적대적 코드 리뷰
- `.codex/rules/project.rules`: backlog 경로의 일반 shell 직접 조회 금지 등 명령 정책
- `.codex/hooks.json` 및 `.codex/hooks/`: 세션 브랜치 안내, backlog CLI 가드/자동 commit, 줄 수 검사, lint/build gate

빠른 backlog 조사는 `gpt-5.6-luna` + `low`, 코드 리뷰는 `gpt-5.6-sol` + `high`를 사용합니다. 기존 리뷰 모델 `gpt-5.6`은 이 계정에서 실행 오류가 나서 현재 세션에 노출된 Sol 모델명으로 수정했습니다.

## 병렬 작업과 토큰 사용

- 가이드는 기존 결정과 파일 목록을 재사용하며 필요한 부분만 읽습니다.
- 리뷰는 변경 파일을 한 번에 받고, 후속 리뷰는 수정 사항과 영향 범위만 확인합니다. 리뷰 에이전트는 빌드와 추가 위임을 하지 않습니다.
- 메인은 작업 파일 소유권을 나누고 독립 구현만 병렬로 맡깁니다. 새 작업에는 전체 대화 대신 필요한 요구사항과 파일 경로를 전달합니다.
- 공통 Gradle 검사는 메인이 변경을 모아서 실행합니다. 동일 코드와 동일 환경의 실패를 반복 시도하지 않습니다.
- 설정 변경은 이미 실행 중인 에이전트에 소급 적용됐다고 가정하지 않습니다. 이후 생성하는 에이전트에서 적용 여부를 확인합니다.

## 시작

1. Codex에서 프로젝트를 열고 새 세션을 시작합니다.
2. 프로젝트 `.codex/` 설정과 hooks를 신뢰합니다. hook이 새로 추가되거나 바뀐 경우 Codex에서 `/hooks`를 열어 검토/승인해야 실제 실행됩니다.
3. `py .\backlog_cli.py list`로 CLI를 확인합니다.

## 현재 환경의 한계

현재 workspace는 Git 저장소로 인식되지 않아 자동 commit/push가 불가능합니다. Android Gradle 프로젝트와 SDK는 존재합니다. SDK 경로는 `C:/Users/dasin/AppData/Local/Android/Sdk`이며 필요시 `ANDROID_HOME`으로 지정합니다. 2026-09-20 adb에 연결된 기기는 없었습니다. Python 변경에는 표준 라이브러리의 syntax compile 검사를 실행합니다.

Codex hooks는 프로젝트 도구 호출에 적용되는 guardrail입니다. Codex가 hook 실행에 동의(trust)하기 전에는 로드되어도 동작하지 않습니다.
