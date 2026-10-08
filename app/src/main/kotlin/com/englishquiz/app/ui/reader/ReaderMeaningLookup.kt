package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.MeaningRequest
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Finds [token]'s meaning and, on success, saves it to [repository].
 *
 * The passage already carries most meanings — the annotated expressions and, since 백로그 024,
 * a glossary — and a phrase saved earlier has one too, so [explainMeaning] is the last resort
 * rather than the first: a tap costs a call only for a word nothing local knows.
 *
 * The AI response is used only for its meaning text (requirements 22: no AI call decides
 * duplicates). The expression that is saved -- and therefore the duplicate-detection key -- is
 * always the tapped [ReaderToken.lookup] text, never the string the AI happens to echo back.
 */
suspend fun lookupMeaning(
    token: ReaderToken,
    contextSentence: String,
    content: LearningContent,
    repository: LearningRepository,
    nowEpochMillis: () -> Long,
    explainMeaning: suspend (MeaningRequest) -> ContextualMeaning,
): MeaningUiState = try {
    val local = content.localMeaningFor(token) ?: savedMeaningOrNull(token.lookup, contextSentence, repository)
    val meaning = local
        ?: explainMeaning(MeaningRequest(expression = token.lookup, context = contextSentence)).meaning
    val status = saveMeaning(token.lookup, meaning, contextSentence, repository, nowEpochMillis, shortMeaning = content.localShortMeaningFor(token))
    MeaningUiState.Success(token.lookup, meaning, status)
} catch (error: CancellationException) {
    throw error
} catch (error: AiLearningException) {
    MeaningUiState.Error(token.display, meaningErrorMessage(error))
} catch (_: Exception) {
    MeaningUiState.Error(token.display, "뜻을 가져오지 못했어요. 다시 시도해 주세요.")
}

/**
 * A store that cannot be read must not block the lookup: the call still goes out, and the save
 * step reports the store's trouble on its own, as it always did.
 */
private suspend fun savedMeaningOrNull(
    expression: String,
    contextSentence: String,
    repository: LearningRepository,
): String? =
    try {
        repository.findSavedMeaning(expression, contextSentence)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

/**
 * Retries only the save step for a meaning that was already fetched. Never calls the AI again,
 * since a successful lookup is a paid call that must not be repeated just to retry persistence.
 */
suspend fun retrySaveMeaning(
    state: MeaningUiState.Success,
    contextSentence: String,
    repository: LearningRepository,
    nowEpochMillis: () -> Long,
): MeaningUiState.Success {
    val status = saveMeaning(state.expression, state.meaning, contextSentence, repository, nowEpochMillis, countTap = false)
    return state.copy(saveStatus = status)
}

/**
 * Persists the expression under [NonCancellable] so that a screen rotation cancelling the
 * caller's coroutine scope cannot roll back a save whose (paid) lookup already succeeded.
 */
private suspend fun saveMeaning(
    expression: String,
    meaning: String,
    contextSentence: String,
    repository: LearningRepository,
    nowEpochMillis: () -> Long,
    countTap: Boolean = true,
    shortMeaning: String = "",
): SaveStatus = try {
    withContext(NonCancellable) {
        repository.saveExpression(
            displayExpression = expression,
            contextMeaning = meaning,
            savedAtEpochMillis = nowEpochMillis(),
            contextSentence = contextSentence,
            countTap = countTap,
            shortMeaning = shortMeaning,
        )
    }
    SaveStatus.SAVED
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    SaveStatus.FAILED
}

private fun meaningErrorMessage(error: AiLearningException): String = when (error.errorCode) {
    // "timeout" is this client's own socket timeout; "provider_timeout" is the Edge Function
    // giving up on the provider first. The read timeout sits above the function's, so in practice
    // it is the latter that arrives — both mean the same thing to the learner.
    "timeout", "provider_timeout" -> "시간이 초과됐어요. 다시 시도해 주세요."
    "network_error" -> "네트워크 연결을 확인해 주세요."
    "invalid_response" -> "서버 응답을 이해하지 못했어요. 다시 시도해 주세요."
    "provider_busy" -> "지금 요청이 몰려 있어요. 잠시 후 다시 시도해 주세요."
    else -> "뜻을 가져오지 못했어요. 다시 시도해 주세요."
}
