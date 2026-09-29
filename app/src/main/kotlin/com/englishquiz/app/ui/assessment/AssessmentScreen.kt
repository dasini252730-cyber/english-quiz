package com.englishquiz.app.ui.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.assessment.Assessment
import com.englishquiz.app.assessment.AssessmentLevel
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleOptionButton
import com.englishquiz.app.ui.theme.MongleProgressBar
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AssessmentScreen(
    onSaveResult: suspend (AssessmentLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var answers by rememberSaveable { mutableStateOf(List(Assessment.questions.size) { -1 }) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val renderedQuestionIndex = questionIndex
    val question = Assessment.questions[renderedQuestionIndex]
    val scope = rememberCoroutineScope()

    fun submit(finalAnswers: List<Int>) {
        if (isSaving) return
        isSaving = true
        saveFailed = false
        scope.launch {
            try {
                val level = Assessment.levelFor(Assessment.score(finalAnswers))
                onSaveResult(level)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                saveFailed = true
            } finally {
                isSaving = false
            }
        }
    }

    fun answer(optionIndex: Int) {
        if (isSaving || saveFailed || questionIndex != renderedQuestionIndex) return
        val updatedAnswers = answers.toMutableList().also { it[renderedQuestionIndex] = optionIndex }
        answers = updatedAnswers
        if (renderedQuestionIndex == Assessment.questions.lastIndex) {
            submit(updatedAnswers)
        } else {
            questionIndex = renderedQuestionIndex + 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MongleColor.Cream)
            .mongleScreenInsets()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("레벨 진단", style = MaterialTheme.typography.titleLarge, color = MongleColor.Purple)
                Text(
                    "${questionIndex + 1} / ${Assessment.questions.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            MongleProgressBar(
                progress = (questionIndex + 1).toFloat() / Assessment.questions.size,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "가장 자연스러운 표현을 고르세요",
                style = MaterialTheme.typography.bodyLarge,
                color = MongleColor.InkMuted,
            )
            MongleCard(contentPadding = 20.dp) {
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            question.options.forEachIndexed { optionIndex, option ->
                MongleOptionButton(
                    text = option,
                    onClick = { answer(optionIndex) },
                    enabled = !isSaving && !saveFailed,
                )
            }
        }

        if (isSaving) {
            Text("진단 결과를 저장하고 있어요…", style = MaterialTheme.typography.bodyLarge)
        }
        if (saveFailed) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "결과를 저장하지 못했어요. 다시 시도해 주세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MongleColor.Danger,
                )
                MongleButton("다시 시도", { submit(answers) })
            }
        }
    }
}
