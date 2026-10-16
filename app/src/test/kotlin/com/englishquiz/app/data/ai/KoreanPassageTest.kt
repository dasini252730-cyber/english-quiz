package com.englishquiz.app.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 051: a passage written in Korean is not learning material and must not be shown. */
class KoreanPassageTest {
    @Test
    fun aKoreanPassageIsRecognised() {
        val content = passage("지은", "어? 민지? 정말 오랜만이다!", "민지", "맞다, 얼마만이야? 5년은 되는 것 같아.")

        assertTrue(content.isKoreanPassage)
    }

    @Test
    fun anEnglishPassageWithAKoreanWordOrNameIsNot() {
        val content = passage("Emma", "She said 안녕 and left without her coffee.", "Tom", "That sounds sketchy, honestly.")

        assertFalse(content.isKoreanPassage)
        assertFalse(passage("Narrator", "", "Narrator", "   ").isKoreanPassage)
    }

    private fun passage(speakerA: String, textA: String, speakerB: String, textB: String) = LearningContent(
        title = "t",
        mode = ContentMode.CONVERSATION,
        segments = listOf(ContentSegment(speakerA, textA), ContentSegment(speakerB, textB)),
        expressions = emptyList(),
    )
}
