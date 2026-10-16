package com.englishquiz.app.ui

import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.ui.reader.meaningErrorMessage
import com.englishquiz.app.ui.session.generationErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 백로그 062: the function's own refusals reach the learner as two different sentences, in both places. */
class AiErrorMessageTest {
    @Test
    fun unauthorizedAndTheDailyLimitAreToldApartInTheSessionAndTheReader() {
        val unauthorized = AiLearningException("unauthorized", 401)
        val limit = AiLearningException("daily_limit_reached", 429)
        assertEquals("앱 인증에 실패했어요. 최신 버전으로 업데이트해 주세요.", generationErrorMessage(unauthorized))
        assertEquals("오늘 AI 호출 한도에 도달했어요. 내일 다시 이용할 수 있어요.", generationErrorMessage(limit))
        assertEquals("앱 인증에 실패했어요. 최신 버전으로 업데이트해 주세요.", meaningErrorMessage(unauthorized))
        assertEquals("오늘 AI 호출 한도에 도달했어요. 내일 다시 이용할 수 있어요.", meaningErrorMessage(limit))
        // Neither falls into the generic line, which is what a learner cannot act on.
        assertNotEquals(generationErrorMessage(AiLearningException("request_failed", 500)), generationErrorMessage(limit))
        assertNotEquals(meaningErrorMessage(AiLearningException("request_failed", 500)), meaningErrorMessage(unauthorized))
    }
}
