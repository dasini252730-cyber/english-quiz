package com.englishquiz.app.ui.session

import androidx.compose.runtime.saveable.Saver
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.domain.session.LearningSessionSummary
import org.json.JSONObject

/**
 * Keeps a learning session across activity recreation (rotation, font-size change, process death).
 *
 * The generated passage is the expensive part: regenerating it costs a paid provider call and
 * hands the learner a different text than the one they were reading, so it has to survive. The
 * content is stored as the very JSON the Edge Function returns and restored through
 * [parseContentResponse], which means the restore path goes through the same validation as a live
 * reply rather than a second decoder that could drift away from it.
 *
 * A step that cannot be read back returns `null`, and Compose then falls back to the initial
 * value — the session restarts, which is the same outcome as before this saver existed.
 */
internal val SessionStepSaver: Saver<SessionStep, String> = Saver(
    save = { step ->
        when (step) {
            SessionStep.Preparing -> JSONObject().put(KEY_STEP, STEP_PREPARING)
            is SessionStep.Failed -> JSONObject()
                .put(KEY_STEP, STEP_FAILED)
                .put(KEY_MESSAGE, step.message)

            is SessionStep.Reading -> JSONObject()
                .put(KEY_STEP, STEP_READING)
                .put(KEY_CONTENT, step.content.toResponseJson())

            is SessionStep.Quiz -> JSONObject()
                .put(KEY_STEP, STEP_QUIZ)
                .put(KEY_CONTENT, step.content.toResponseJson())
                .put(KEY_SUMMARY, step.base.toJson())

            is SessionStep.Finished -> JSONObject()
                .put(KEY_STEP, STEP_FINISHED)
                .put(KEY_SUMMARY, step.summary.toJson())
        }.toString()
    },
    restore = { saved ->
        runCatching {
            val json = JSONObject(saved)
            when (json.getString(KEY_STEP)) {
                STEP_PREPARING -> SessionStep.Preparing
                STEP_FAILED -> SessionStep.Failed(json.getString(KEY_MESSAGE))
                STEP_READING -> SessionStep.Reading(json.readContent())
                STEP_QUIZ -> SessionStep.Quiz(
                    content = json.readContent(),
                    base = json.getJSONObject(KEY_SUMMARY).toSummary(),
                )

                STEP_FINISHED -> SessionStep.Finished(json.getJSONObject(KEY_SUMMARY).toSummary())
                else -> null
            }
        }.getOrNull()
    },
)

private fun JSONObject.readContent(): LearningContent {
    val content = getJSONObject(KEY_CONTENT)
    val mode = when (content.getJSONObject("data").getString("mode")) {
        ContentMode.STORY.wireValue -> ContentMode.STORY
        else -> ContentMode.CONVERSATION
    }
    return parseContentResponse(content, mode)
}

private fun LearningSessionSummary.toJson(): JSONObject = JSONObject()
    .put("learned", learnedExpressionCount)
    .put("newlySaved", newlySavedExpressionCount)
    .put("correct", quizCorrectCount)
    .put("questions", quizQuestionCount)
    .put("mastered", masteredExpressionCount)

private fun JSONObject.toSummary(): LearningSessionSummary = LearningSessionSummary(
    learnedExpressionCount = getInt("learned"),
    newlySavedExpressionCount = getInt("newlySaved"),
    quizCorrectCount = getInt("correct"),
    quizQuestionCount = getInt("questions"),
    masteredExpressionCount = getInt("mastered"),
)

private const val KEY_STEP = "step"
private const val KEY_MESSAGE = "message"
private const val KEY_CONTENT = "content"
private const val KEY_SUMMARY = "summary"
private const val STEP_PREPARING = "preparing"
private const val STEP_FAILED = "failed"
private const val STEP_READING = "reading"
private const val STEP_QUIZ = "quiz"
private const val STEP_FINISHED = "finished"
