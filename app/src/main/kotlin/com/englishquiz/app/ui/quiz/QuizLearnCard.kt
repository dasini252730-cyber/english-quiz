package com.englishquiz.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.englishquiz.app.ui.reader.ReaderSpeech
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons

/**
 * The first meeting with an expression (백로그 045): shown, not tested. The learner reads it
 * with its gloss and sentence, can hear it, and moves on with one button (백로그 055): whether
 * they know it is what tomorrow's first graded question finds out, not a self-assessment here.
 */
@Composable
internal fun QuizLearnCard(
    question: com.englishquiz.app.domain.quiz.QuizQuestion,
    speechReady: Boolean,
    onPlay: (String) -> Unit,
    onSeen: () -> Unit,
) {
    MongleCard(contentPadding = 20.dp, face = MongleColor.MeaningPanel, border = MongleColor.HighlightBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.expression, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            MongleIconButton(
                icon = MongleIcons.Speaker,
                contentDescription = "${question.expression} 발음 듣기",
                onClick = { onPlay(question.expression) },
                enabled = speechReady,
                tint = MongleColor.BlueIcon,
                background = MongleColor.BlueSoft,
                circleSize = 36.dp,
                iconSize = 18.dp,
            )
        }
        if (question.shortMeaning.isNotEmpty()) {
            Text(question.shortMeaning, style = MaterialTheme.typography.headlineSmall, color = MongleColor.AmberLabel)
        }
        // The explanation carries the full meaning on its own line after the heading.
        Text(question.explanation.substringAfter('\n'), style = MaterialTheme.typography.bodyLarge)
        if (question.questionText.isNotBlank()) {
            Text(question.questionText, style = MaterialTheme.typography.bodyMedium, color = MongleColor.InkMuted)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MongleButton("뜻 확인 완료", onSeen, colors = MongleButtonColors.Positive)
    }
}

/** A speech engine for the quiz, stopped when the screen stops and closed when it leaves. */
@Composable
internal fun rememberQuizSpeech(): ReaderSpeech {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val speech = remember(context, owner) { ReaderSpeech(context) }
    DisposableEffect(speech, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) speech.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            speech.close()
        }
    }
    return speech
}
