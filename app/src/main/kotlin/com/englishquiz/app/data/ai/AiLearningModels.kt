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
    /** Who each speaker is (백로그 050), keyed by the segment speaker name; empty before it or when the model gave none. */
    val speakers: Map<String, SpeakerGender> = emptyMap(),
)

/** What the model says about a speaker, so a voice can be chosen to match (백로그 050). */
enum class SpeakerGender(val wireValue: String) {
    FEMALE("female"),
    MALE("male"),
    NARRATOR("narrator"),
    ;

    companion object {
        fun fromWire(value: String): SpeakerGender? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * True when the passage itself is written in Korean (백로그 051): a reply the server accepted
 * before it learnt to refuse one, or a row stored from it. Such a passage is not learning
 * material and is regenerated rather than shown. Names and a quoted word do not trip this.
 */
val LearningContent.isKoreanPassage: Boolean
    get() {
        val text = segments.joinToString(" ") { it.text }
        val letters = text.count { !it.isWhitespace() }
        if (letters == 0) return false
        val hangul = text.count { it in 'ㄱ'..'ㆎ' || it in '가'..'힣' }
        return hangul > letters * MAX_HANGUL_SHARE
    }

private const val MAX_HANGUL_SHARE = 0.2

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
    /** A few words for a quiz option (백로그 046); "" for a passage from before it or when the model gave none. */
    val shortMeaning: String = "",
)

data class ContextualMeaning(
    val expression: String,
    val meaning: String,
)

class AiLearningException(
    val errorCode: String,
    val httpStatus: Int? = null,
) : Exception(errorCode)
