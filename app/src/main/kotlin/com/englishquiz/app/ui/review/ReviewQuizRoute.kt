package com.englishquiz.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.mongleScreenInsets

/** How many of a long review list one practice quiz asks; the most overdue come first. */
const val PRACTICE_MAX_QUESTIONS = 20

/**
 * A practice quiz over the review box's current list (백로그 052). A wrong answer is recorded, so
 * a missed expression comes back sooner and shows under "자주 틀리는"; a right one changes nothing,
 * so no schedule, mastery or badge can be farmed by practising. No points, no learning session;
 * a never-asked expression shows its card, which is read but not recorded, so the daily quiz
 * still meets it first. [expressionIds] are the rows the list showed; [onDone] returns to the
 * review box.
 */
@Composable
fun ReviewQuizRoute(
    repository: LearningRepository,
    expressionIds: Set<Long>,
    onDone: () -> Unit,
) {
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val finished = result
    if (finished == null) {
        QuizRoute(
            repository = repository,
            todayContent = null,
            onFinished = { result = "${it.quizCorrectCount}/${it.quizQuestionCount}" },
            onBack = onDone,
            enrolExpressions = false,
            questionSource = { repo, _ -> repo.listSavedExpressions().filter { it.id in expressionIds } },
            maxQuestions = PRACTICE_MAX_QUESTIONS,
            practice = true,
        )
    } else {
        val (correct, total) = finished.split("/").map { it.toIntOrNull() ?: 0 }
        PracticeResult(correct, total, onDone)
    }
}

@Composable
private fun PracticeResult(correct: Int, total: Int, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Column(
        modifier = Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("연습 끝", style = MaterialTheme.typography.displayMedium)
        Text(
            if (total == 0) "문제로 낼 표현이 없었어요." else "${total}문제 중 ${correct}개를 맞혔어요.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "틀린 표현은 더 자주 나오게 돼요. 맞힌 표현의 복습 일정과 점수·스트릭은 그대로예요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MongleColor.InkMuted,
            textAlign = TextAlign.Center,
        )
        MongleButton("복습함으로", onDone)
    }
}
