package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.foldTypography
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
    val language = content.language
    val local = content.localMeaningFor(token) ?: savedMeaningOrNull(language, token.lookup, contextSentence, repository)
    // 백로그 066: a Chinese word nothing local knows also gets its pinyin from the call.
    val fetched = if (local == null) explainMeaning(MeaningRequest(expression = token.lookup, context = contextSentence, language = language)) else null
    val meaning = local ?: checkNotNull(fetched).meaning
    val pinyin = content.localPinyinFor(token).ifEmpty { fetched?.pinyin.orEmpty() }
    val status = saveMeaning(
        language, token.lookup, meaning, contextSentence, repository, nowEpochMillis,
        shortMeaning = content.localShortMeaningFor(token), kind = content.localKindFor(token), pinyin = pinyin,
    )
    MeaningUiState.Success(token.lookup, meaning, status, pinyin)
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
    language: Language,
    expression: String,
    contextSentence: String,
    repository: LearningRepository,
): String? =
    try {
        repository.findSavedMeaning(language, expression, contextSentence)
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
    language: Language = Language.DEFAULT,
): MeaningUiState.Success {
    val status = saveMeaning(language, state.expression, state.meaning, contextSentence, repository, nowEpochMillis, countTap = false)
    return state.copy(saveStatus = status)
}

/**
 * Persists the expression under [NonCancellable] so that a screen rotation cancelling the
 * caller's coroutine scope cannot roll back a save whose (paid) lookup already succeeded.
 */
private suspend fun saveMeaning(
    language: Language,
    expression: String,
    meaning: String,
    contextSentence: String,
    repository: LearningRepository,
    nowEpochMillis: () -> Long,
    countTap: Boolean = true,
    shortMeaning: String = "",
    kind: String = "",
    pinyin: String = "",
): SaveStatus = try {
    withContext(NonCancellable) {
        repository.saveExpression(
            language = language,
            displayExpression = expression,
            contextMeaning = meaning,
            savedAtEpochMillis = nowEpochMillis(),
            contextSentence = contextSentence,
            countTap = countTap,
            shortMeaning = shortMeaning,
            kind = kind,
            pinyin = pinyin,
        )
    }
    SaveStatus.SAVED
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    SaveStatus.FAILED
}

/**
 * The pinyin the passage carries for an annotated token (백로그 066): the expression at the token's
 * place first, then any expression with the same text; "" for English or when the model gave none.
 */
internal fun LearningContent.localPinyinFor(token: ReaderToken): String {
    val key = token.lookup.foldTypography().trimEdgePunctuation()
    return expressions.firstOrNull { it.segmentIndex == token.segmentIndex && it.startIndex == token.startIndex }?.pinyin?.ifEmpty { null }
        ?: expressions.firstOrNull { it.text.foldTypography().trimEdgePunctuation() == key }?.pinyin?.ifEmpty { null }
        // A glossary word is exactly what a Chinese line is cut along, so its pinyin travels with it.
        ?: glossary.firstOrNull { it.word.foldTypography().trimEdgePunctuation() == key }?.pinyin
        ?: ""
}

internal fun meaningErrorMessage(error: AiLearningException): String = when (error.errorCode) {
    // "timeout" is this client's own socket timeout; "provider_timeout" is the Edge Function
    // giving up on the provider first. The read timeout sits above the function's, so in practice
    // it is the latter that arrives — both mean the same thing to the learner.
    "timeout", "provider_timeout" -> "시간이 초과됐어요. 다시 시도해 주세요."
    "network_error" -> "네트워크 연결을 확인해 주세요."
    "invalid_response" -> "서버 응답을 이해하지 못했어요. 다시 시도해 주세요."
    "provider_busy" -> "지금 요청이 몰려 있어요. 잠시 후 다시 시도해 주세요."
    "unauthorized" -> "앱 인증에 실패했어요. 최신 버전으로 업데이트해 주세요."
    "daily_limit_reached" -> "오늘 AI 호출 한도에 도달했어요. 내일 다시 이용할 수 있어요."
    else -> "뜻을 가져오지 못했어요. 다시 시도해 주세요."
}
