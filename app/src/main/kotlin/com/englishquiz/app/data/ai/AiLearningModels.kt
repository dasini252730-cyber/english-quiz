package com.englishquiz.app.data.ai

data class ContentGenerationRequest(
    val mode: ContentMode,
    val difficulty: Int,
    val reviewExpressions: List<String> = emptyList(),
)

enum class ContentMode(val wireValue: String) {
    CONVERSATION("conversation"),
    STORY("story"),
}

data class MeaningRequest(
    val expression: String,
    val context: String,
)

data class LearningContent(
    val title: String,
    val mode: ContentMode,
    val segments: List<ContentSegment>,
    val expressions: List<ContentExpression>,
    /** Words of the passage with their meanings in this context, so a tap needs no call (백로그 024). */
    val glossary: List<GlossaryEntry> = emptyList(),
    /** Questions about the passage itself for the quiz (백로그 042); empty for a passage stored before it. */
    val comprehension: List<ComprehensionQuestion> = emptyList(),
)

/** One multiple-choice question about the situation of the passage, written by the model. */
data class ComprehensionQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

data class GlossaryEntry(
    val word: String,
    val meaning: String,
)

data class ContentSegment(
    val speaker: String,
    val text: String,
)

data class ContentExpression(
    val text: String,
    val meaning: String,
    val segmentIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
)

data class ContextualMeaning(
    val expression: String,
    val meaning: String,
)

class AiLearningException(
    val errorCode: String,
    val httpStatus: Int? = null,
) : Exception(errorCode)
