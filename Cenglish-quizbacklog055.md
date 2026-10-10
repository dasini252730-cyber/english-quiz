# 055 표현 학습 상태 4단계 분리 (새 표현 대기열 · 첫 복습 대기 · 복습 예정 · 숙달)

## 목적
저장됨 ≠ 첫 학습 완료 ≠ 복습 시점 도래를 코드로 명시하여, 표현의 학습 생명주기를 네 가지 상태로 관리한다.
- ① 새 표현 대기열(카드도 못 본 표현, 하루 5개만 첫 학습 투입)
- ② 첫 학습 완료·첫 복습 대기(카드 본 표현, 다음 날 첫 채점)
- ③ 복습 예정(날짜 도래, 가장 밀린 것부터)
- ④ 숙달(열매, 30일 주기 유지)

지문에서 12개가 등록되면 그날 카드는 5개, 나머지는 대기열에 남아 차례로 진행한다.

## 작업 범위

### 도메인 모델: ExpressionState
`SavedExpressionEntity` + 현재 시각(`now`)에서 파생되는 학습 상태를 명시화한다.

```kotlin
enum class ExpressionState {
    NEW,                    // lastReviewedAtEpochMillis == null
    FIRST_REVIEW_PENDING,   // 카드 본 후 첫 복습 대기
    DUE,                    // nextReviewAtEpochMillis <= now && !isMastered
    SCHEDULED,              // nextReviewAtEpochMillis > now && !isMastered
    MASTERED                // isMastered == true
}

fun SavedExpressionEntity.stateAt(nowEpochMillis: Long): ExpressionState
```

**위치**: `app/src/main/kotlin/com/englishquiz/app/domain/review/ExpressionState.kt` (신규)

### UI 변경: 학습 카드 버튼
현재: "알아요" + "모르겠어요, 내일 다시" (두 버튼)
→ 변경: "뜻 확인 완료" (단일 버튼, 자기평가 없음)

- 버튼 동작은 동일: `markSeen()` → `next()`로 진행
- 내일 첫 복습 대기 상태로 전환 (`ReviewPolicy.seenAgainAt()` 사용)

**위치**: `app/src/main/kotlin/com/englishquiz/app/ui/quiz/QuizLearnCard.kt` (수정)

## 관련 파일

### 핵심 변경 대상
1. **app/src/main/kotlin/com/englishquiz/app/domain/review/ReviewPolicy.kt**
   - `ExpressionState` enum 추가 또는 별도 파일로 분리
   - `stateAt(SavedExpressionEntity, nowEpochMillis)` 함수 제공

2. **app/src/main/kotlin/com/englishquiz/app/domain/quiz/QuizBuilder.kt**
   - 기존 로직 확인: 이미 fresh vs. reviews 분할 (line 73)
   - `isFirstMeetingShown()` 함수로 "첫 복습 대기" 상태 판정 (line 90–94)
   - 필요시 `ExpressionState`를 사용하여 로직 명확화

3. **app/src/main/kotlin/com/englishquiz/app/ui/quiz/QuizLearnCard.kt**
   - "알아요" / "모르겠어요" 두 버튼 → "뜻 확인 완료" 단일 버튼으로 변경
   - 두 콜백 (`onKnown`, `onUnknown`)을 하나로 통합

4. **app/src/main/kotlin/com/englishquiz/app/ui/quiz/QuizRoute.kt**
   - `card()` 함수 호출 시 전달하는 버튼 콜백 변경
   - `onKnown`과 `onUnknown` 분리 → 통합 콜백 사용

5. **app/src/main/kotlin/com/englishquiz/app/ui/quiz/QuizLoader.kt**
   - 선택사항: 상태 분류 시 `ExpressionState`를 사용하여 코드 가독성 향상
   - 현재 `QuizBuilder.isFirstMeetingShown()` 호출 유지 가능

### 참고 파일 (읽기 전용)
6. **app/src/main/kotlin/com/englishquiz/app/data/local/LearningEntities.kt**
   - `SavedExpressionEntity` 구조 확인
   - `lastReviewedAtEpochMillis`, `nextReviewAtEpochMillis`, `isMastered` 필드

7. **app/src/main/kotlin/com/englishquiz/app/data/local/LearningDao.kt**
   - `findDueExpressions()`: 현재 시각 기준 DUE 상태 표현 조회
   - 쿼리는 `nextReviewAtEpochMillis <= :nowEpochMillis` 사용 중

8. **app/src/test/kotlin/com/englishquiz/app/domain/quiz/QuizBuilderTest.kt**
   - 기존 테스트: fresh/review 분할, NEW_PER_DAY = 5 검증
   - 신규 테스트 작성 대상 (ExpressionState 도입 시)

9. **app/src/androidTest/kotlin/com/englishquiz/app/ui/QuizRouteTest.kt**
   - UI 테스트: 카드 버튼 탭 동작 확인
   - "뜻 확인 완료" 버튼으로 변경 후 테스트 갱신

10. **app/src/main/kotlin/com/englishquiz/app/domain/game/GrowthStage.kt**
    - 참고: 성장 단계(씨앗·새싹·잎·꽃·열매)와 ExpressionState는 별개 개념
    - GrowthStage는 consecutiveCorrectCount 기반, ExpressionState는 review 타이밍 기반

## 구현 분석

### 현 상태
- `QuizBuilder` 라인 73: `fresh` / `reviews` 분할 이미 구현됨
- `isFirstMeetingShown()` (라인 90–94): "첫 복습 대기" 판정 로직 있음
- `QuizLearnCard`: 두 버튼 (`onKnown`, `onUnknown`) 제공 중

### 변경 전략

#### 1단계: ExpressionState 도메인 모델 추가
- 위치: `domain/review/ExpressionState.kt` (신규)
- 함수: `SavedExpressionEntity.stateAt(nowEpochMillis: Long): ExpressionState`
- 상태 판정:
  ```kotlin
  enum class ExpressionState {
      NEW,                 // lastReviewedAtEpochMillis == null
      FIRST_REVIEW_PENDING,// lastReviewedAtEpochMillis != null && 점수/오답 없음
      DUE,                 // nextReviewAtEpochMillis <= now && !isMastered
      SCHEDULED,           // nextReviewAtEpochMillis > now && !isMastered
      MASTERED             // isMastered == true
  }
  ```

#### 2단계: QuizBuilder에서 ExpressionState 활용
- NEW 상태 표현만 NEW_PER_DAY = 5 제약 적용
- 기존 `isFirstMeetingShown()` 로직 유지 또는 ExpressionState.FIRST_REVIEW_PENDING으로 통합

#### 3단계: 학습 카드 UI 변경
- `QuizLearnCard` 버튼: "뜻 확인 완료" 단일 버튼
- 콜백 통합: `onKnown`과 `onUnknown` → `onConfirmed` (또는 단일 함수)
- `QuizRoute.card()` 함수 호출 방식 통일

#### 4단계: 테스트 갱신
- 기존 `QuizBuilderTest` 검증 유지
- 신규 `ExpressionStateTest`: 상태 판정 로직 단위 테스트
- `QuizRouteTest`: UI 버튼 탭 동작 검증

## 완료 기준

- [ ] ExpressionState enum 추가 및 `stateAt()` 함수 구현
- [ ] QuizBuilder에서 ExpressionState를 사용하여 fresh/review 상태 명확화
- [ ] QuizLearnCard: 두 버튼 → "뜻 확인 완료" 단일 버튼 변경
- [ ] QuizRoute: 통합된 콜백 사용하도록 수정
- [ ] 기존 테스트 통과 (QuizBuilderTest, QuizRouteTest 포함)
- [ ] 신규 테스트 작성: ExpressionState 상태 판정 테스트
- [ ] 코드 린트 및 빌드 검사 통과

## 선행 작업
없음

## 기술 결정 사항

### ExpressionState 위치
**결정**: `app/src/main/kotlin/com/englishquiz/app/domain/review/ExpressionState.kt`
- 이유: 표현의 복습 상태를 타이밍 기준으로 분류하므로 `review` 도메인에 속함
- 대안: `domain/quiz/` (검토됨, 기각)—ExpressionState는 퀴즈뿐 아니라 복습함 등 전역 범위에서 사용

### 버튼 통합 방식
**결정**: 단일 콜백 함수 `onConfirmed()` 또는 통합
- 이유: 두 선택지의 동작이 동일 (`markSeen()` → `next()`)하므로 조건 분기 불필요
- `ReviewPolicy.seenAgainAt()` 로직으로 자동 내일 재설정
