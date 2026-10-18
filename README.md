# StoryEnglish

개인용 영어·중국어 학습 Android 앱(Galaxy S26). 홈에서 English/中文을 고르고, 매일 대화/스토리 지문 하나를 읽고, 표현의 뜻을 보며 저장하고,
퀴즈와 간격 반복으로 복습한다. Kotlin + Jetpack Compose, Room, DataStore. AI 호출은 Supabase Edge
Function `ai-learning`이 맡고 앱에는 키가 없다. 구현된 기능 전체는 `구현기능 명세서.md`, 제품 요구는
`요구사항/요구사항.md`, 작업 상태는 `py .\backlog_cli.py list|show|add|update`.

## 폰에 설치

푸시할 때마다 GitHub Actions가 APK를 빌드해 [Releases](../../releases)에 올린다. 폰 브라우저에서 최신
`StoryEnglish-buildN.apk`를 내려받아 설치하면 기존 데이터를 유지한 채 업데이트된다. 저장소 시크릿
`AI_ENDPOINT`, `AI_APP_TOKEN`, `DEBUG_KEYSTORE_BASE64`가 있어야 쓸 수 있는 APK가 된다
(`.github/workflows/android-apk.yml` 참고).

## 로컬 빌드

```
./gradlew :app:assembleDebug -PaiEndpoint=https://<project>.supabase.co/functions/v1/ai-learning -PaiAppToken=<앱 토큰>
```

토큰은 소스나 `gradle.properties`(저장소에 올라가는 파일)에 적지 않는다. 로컬에서는 명령줄 인자나
`~/.gradle/gradle.properties`에 둔다.

## 서버: 배포와 시크릿 (백로그 062)

Edge Function은 요청마다 `X-App-Token` 헤더를 확인하고(없거나 틀리면 401), 하루 호출 상한을 넘기면
429를 돌려주며, 호출마다 사용량을 `ai_usage` 테이블에 남긴다. 토큰을 켜는 순서가 중요하다. 순서를
바꾸면 폰에 깔린 기존 APK가 토큰 없이 호출해 401을 받는다.

1. **토큰 하나 만들기.** 예: `openssl rand -base64 32 | tr -d '=+/'` (따옴표·공백 없는 한 토막).
2. **GitHub 저장소 시크릿 `AI_APP_TOKEN`에 그 값 넣기**, 그리고 dev에 푸시해 새 APK를 빌드한다.
3. **새 APK를 폰에 설치한다.** 이 시점까지 함수는 아직 토큰을 요구하지 않는다.
4. **테이블 만들기**(최초 1회). 이 저장소의 프로젝트에는 2026-10-10에 이미 적용했다(아래 명령으로). 새 프로젝트라면:
   ```
   supabase db query --linked -f supabase/migrations/20261010150000_ai_usage.sql
   supabase migration repair --status applied 20261010150000
   ```
   `ai_usage` 테이블과 `ai_usage_monthly` 뷰가 생긴다. `supabase db push`는 원격에 2026-09의 옛 마이그레이션 12건이
   로컬에 없다고 거부하므로, push로 가려면 먼저 `supabase db pull`로 로컬 기록을 맞춰야 한다.
5. **함수 시크릿 설정**:
   ```
   supabase secrets set AI_APP_TOKEN=<1에서 만든 값>
   supabase secrets set AI_DAILY_LIMIT=40        # 생략 가능, 기본 40
   ```
   `ANTHROPIC_API_KEY`(필수)와 `ANTHROPIC_MODEL`(생략 시 `claude-sonnet-5`)은 전과 같다. `SUPABASE_URL`과
   `SUPABASE_SERVICE_ROLE_KEY`는 Supabase가 함수에 자동으로 넣어 준다.
6. **함수 배포**:
   ```
   supabase functions deploy ai-learning
   ```
   배포 뒤 토큰 없는 호출은 401, `AI_APP_TOKEN` 시크릿이 없으면 모든 호출이 503이다.

토큰을 바꿀 때는 2 → 3 → 5 순서로 한다(새 APK 먼저, 함수 시크릿 나중).

### 상한과 기록이 세는 것

- 하루는 Asia/Seoul 기준이다. 상한은 공급자 호출 시도 수를 센다. 지문 하나가 계약 위반으로 한 번 재시도되면 2회로 센다.
- 기록 한 줄 = 시도 한 번: `day, action(content|meaning), model, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, duration_ms, ok, error_code`.
- 기록이나 카운트가 실패해도(테이블 없음, PostgREST 장애) 호출은 통과한다. 함수 로그에 `usage count failed` / `usage record failed`가 남는다.

### 월간 비용 보기

Supabase SQL 편집기에서:

```sql
select * from ai_usage_monthly;
```

비용은 모델 단가를 곱해 계산한다. 예를 들어 claude-sonnet-5(입력 $2/백만, 출력 $10/백만, 캐시 읽기 $0.20/백만,
캐시 생성 $2.50/백만):

```sql
select month, model,
       round((input_tokens * 2 + output_tokens * 10 + cache_read_tokens * 0.2 + cache_write_tokens * 2.5) / 1e6, 4) as usd
from ai_usage_monthly;
```

## 서버 테스트

```
cd supabase/functions/ai-learning
node --experimental-strip-types --test index.test.mjs
```

## 앱 검사

```
./gradlew lint assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<테스트 클래스>
```

이 PC는 에뮬레이터와 Gradle을 함께 띄우면 메모리가 빠듯하다. 계측 테스트는 클래스 하나씩, Gradle 힙은
`-Dorg.gradle.jvmargs=-Xmx1024m`으로 돌리는 편이 안전하다.
