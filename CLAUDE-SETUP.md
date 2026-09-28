# Claude Code 프로젝트 설정

이 저장소는 원래 Codex의 프로젝트 지침과 custom agent 형식으로 운영했습니다.
Claude Code로 옮기면서 역할 구성은 그대로 두고 모델만 교체했습니다.
Codex 설정(`AGENTS.md`, `.codex/`)은 참고용으로 남겨 둡니다.

## 역할과 모델 매핑

| 역할 | 하는 일 | Codex | Claude Code |
| --- | --- | --- | --- |
| Terra | 메인 구현, 작업 관리, 공유 파일 소유권, 통합, 빌드/lint 게이트 | `gpt-5.6-terra` · medium | **Opus 5** (메인 세션) |
| Luna | 백로그 조사, `backlog/<id>.md` 정리 | `gpt-5.6-luna` · low | **Haiku 4.5** (`backlog-guide`) |
| Sol | 변경 파일 적대적 코드 리뷰 | `gpt-5.6-sol` · high | **Opus 5** (`code-critic`) |
| Astra | 해결이 어려운 통합 결함, 설계 재검토 (필요할 때만) | 필요시 호출 | **Opus 5** (`deep-debug`) |
| 병렬 구현 워커 | 메인이 나눠 준 독립 구현 | Terra가 분배 | **Sonnet 5** (`general-purpose`) |

에이전트 정의는 `.claude/agents/` 아래에 있습니다.

- `.claude/agents/backlog-guide.md` — Luna
- `.claude/agents/code-critic.md` — Sol
- `.claude/agents/deep-debug.md` — Astra

에이전트 파일을 새로 추가하거나 고친 경우, 이미 실행 중인 세션에는 등록되지 않습니다.
새 세션에서 이름으로 호출되는지 확인하세요.

## 병렬 작업과 토큰 사용

- 가이드는 기존 결정과 파일 목록을 재사용하며 필요한 부분만 읽습니다.
- 리뷰는 변경 파일을 한 번에 받고, 후속 리뷰는 수정 사항과 영향 범위만 확인합니다. 리뷰 에이전트는 빌드와 추가 위임을 하지 않습니다.
- 메인은 작업 파일 소유권을 나누고 독립 구현만 병렬로 맡깁니다. 공유 파일(`LearningDao.kt`, `LearningRepository.kt`, `LearningEntities.kt`, `EnglishQuizApp.kt`)은 메인이 직접 고칩니다.
- 새 작업에는 전체 대화 대신 필요한 요구사항과 파일 경로를 전달합니다.
- 공통 Gradle 검사는 메인이 변경을 모아서 한 번에 실행합니다. 동일 코드와 동일 환경의 실패를 반복 시도하지 않습니다.

## Codex 대비 빠진 것

Codex의 `.codex/hooks/`는 Claude Code에서 실행되지 않습니다. 아직 포팅하지 않은 guardrail은 다음과 같고, 그동안은 메인 에이전트가 직접 수행합니다.

- `session_start.py` — 세션 시작 시 브랜치 안내
- `pre_tool_use.py` — backlog 파일 직접 접근 차단 (현재는 `CLAUDE.md` 규칙으로만 강제)
- `post_tool_use.py` — backlog CLI 변경 자동 commit (이 폴더는 Git 저장소가 아니라 어차피 동작 불가)
- `stop_checks.py` — 줄 수 검사와 lint/build gate (메인이 `gradlew.bat lint assembleDebug`와 `.codex/line-limits.json` 기준을 직접 확인)

## 현재 환경의 한계

- 이 workspace는 Git 저장소로 인식되지 않아 자동 commit/push가 불가능합니다.
- Android SDK 경로는 `C:/Users/dasin/AppData/Local/Android/Sdk`이며 필요시 `ANDROID_HOME`으로 지정합니다.
- 2026-09-20 기준 adb에 연결된 기기가 없습니다. 실기기/에뮬레이터 증거가 필요한 작업은 `waiting`으로 둡니다.
- Python 변경에는 표준 라이브러리의 syntax compile 검사를 실행합니다.
