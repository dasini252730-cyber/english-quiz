package com.englishquiz.app.ui.session

import com.englishquiz.app.data.ai.AiLearningException

/** The line the session shows for a failed generation, by the Edge Function's error code. */
internal fun generationErrorMessage(error: Throwable): String = when {
    error !is AiLearningException -> "학습 내용을 불러오지 못했어요."
    // "timeout" is this client's own socket timeout; "provider_timeout" is the Edge Function
    // giving up on the provider first. The read timeout sits above the function's, so in
    // practice it is the latter that arrives - both mean the same thing to the learner.
    error.errorCode == "timeout" || error.errorCode == "provider_timeout" ->
        "응답이 너무 오래 걸렸어요. 잠시 후 다시 시도해 주세요."
    error.errorCode == "network_error" -> "네트워크 연결을 확인해 주세요."
    error.errorCode == "invalid_response" -> "학습 내용을 이해하지 못했어요. 다시 시도해 주세요."
    error.errorCode == "provider_busy" -> "지금 요청이 몰려 있어요. 잠시 후 다시 시도해 주세요."
    // 백로그 062: the two refusals the function makes on its own, told apart for the learner.
    error.errorCode == "unauthorized" -> "앱 인증에 실패했어요. 최신 버전으로 업데이트해 주세요."
    error.errorCode == "daily_limit_reached" -> "오늘 AI 호출 한도에 도달했어요. 내일 다시 이용할 수 있어요."
    else -> "학습 내용을 불러오지 못했어요. 다시 시도해 주세요."
}
