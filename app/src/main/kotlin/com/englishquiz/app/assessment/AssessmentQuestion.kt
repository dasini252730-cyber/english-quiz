package com.englishquiz.app.assessment

enum class QuestionType {
    MEANING,
    NATURAL_EXPRESSION,
    FILL_IN_THE_BLANK,
}

data class AssessmentQuestion(
    val prompt: String,
    val type: QuestionType,
    val options: List<String>,
    val correctOption: Int,
)

enum class AssessmentLevel(val storedValue: Int, val label: String) {
    BEGINNER(1, "초급"),
    INTERMEDIATE(2, "중급"),
    ADVANCED(3, "고급"),
}

object Assessment {
    val questions = listOf(
        AssessmentQuestion("'I could use a break.'의 뜻은 무엇인가요?", QuestionType.MEANING,
            listOf("휴식이 필요 없어요", "일을 더 하고 싶어요", "잠깐 쉬고 싶어요", "집에 가야 해요"), 2),
        AssessmentQuestion("자연스러운 응답을 고르세요. 'Want to grab coffee?'", QuestionType.NATURAL_EXPRESSION,
            listOf("I am agree.", "Sounds good!", "Yes, I want coffee is.", "Coffee grabs me."), 1),
        AssessmentQuestion("어려운 일을 마침내 해냈다는 뜻이 되도록 빈칸을 채우세요. 'We finally ___ it off.'", QuestionType.FILL_IN_THE_BLANK,
            listOf("put", "took", "made", "pulled"), 3),
        AssessmentQuestion("'That rings a bell.'의 뜻은 무엇인가요?", QuestionType.MEANING,
            listOf("종이 울려요", "어디서 들어본 것 같아요", "전혀 모르겠어요", "기억하고 싶어요"), 1),
        AssessmentQuestion("자연스러운 응답을 고르세요. 'Sorry I'm running late.'", QuestionType.NATURAL_EXPRESSION,
            listOf("You run very late.", "I am late sorry too.", "No worries, take your time.", "Run to me."), 2),
        AssessmentQuestion("빈칸에 알맞은 표현을 고르세요. 'I'm not really ___ spicy food.'", QuestionType.FILL_IN_THE_BLANK,
            listOf("on", "into", "at", "by"), 1),
        AssessmentQuestion("'It slipped my mind.'의 뜻은 무엇인가요?", QuestionType.MEANING,
            listOf("생각이 떠올랐어요", "마음이 바뀌었어요", "미끄러져 넘어졌어요", "깜빡 잊었어요"), 3),
        AssessmentQuestion("자연스러운 응답을 고르세요. 'How did the presentation go?'", QuestionType.NATURAL_EXPRESSION,
            listOf("It went presentation.", "Better than I expected.", "I was go there.", "How did it went?"), 1),
    )

    fun score(answers: List<Int?>): Int = questions.indices.count { index ->
        answers.getOrNull(index) == questions[index].correctOption
    }

    fun levelFor(score: Int): AssessmentLevel = when (score) {
        in 0..3 -> AssessmentLevel.BEGINNER
        in 4..6 -> AssessmentLevel.INTERMEDIATE
        in 7..8 -> AssessmentLevel.ADVANCED
        else -> throw IllegalArgumentException("Assessment score must be between 0 and 8")
    }
}
