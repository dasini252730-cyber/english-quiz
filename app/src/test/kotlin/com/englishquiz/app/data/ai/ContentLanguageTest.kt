package com.englishquiz.app.data.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 066: the server never echoes the language, so a passage is labelled by the request it was
 * made for, or by the row it was stored in; the JSON key is only the fallback for an older row.
 */
class ContentLanguageTest {
    private val reply = JSONObject(
        """{"data":{"title":"雨伞","mode":"story","segments":[{"speaker":"Narrator","text":"她把雨伞还给了老板。","pinyin":"tā bǎ yǔsǎn huán gěi le lǎobǎn."}],
        "expressions":[{"text":"还给","meaning":"돌려주다","shortMeaning":"돌려주다","kind":"verb","segmentIndex":0,"pinyin":"huán gěi"}]}}""",
    )

    @Test
    fun aLiveReplyIsLabelledByTheLanguageItWasAskedFor() {
        val content = parseContentResponse(reply, ContentMode.STORY, Language.CHINESE)
        assertEquals(Language.CHINESE, content.language)
        assertEquals("huán gěi", content.expressions.single().pinyin)
        assertEquals("tā bǎ yǔsǎn huán gěi le lǎobǎn.", content.segments.single().pinyin)
    }

    @Test
    fun theLanguageSurvivesTheStoredRoundTripAndAnOldRowReadsAsEnglish() {
        val stored = parseContentResponse(reply, ContentMode.STORY, Language.CHINESE).toResponseJson()
        assertEquals("zh", stored.getJSONObject("data").getString("language"))
        assertEquals(Language.CHINESE, parseContentResponse(stored, ContentMode.STORY).language)
        // A row from before 066 carries no key and no column worth more than English.
        assertEquals(Language.ENGLISH, parseContentResponse(reply, ContentMode.STORY).language)
        // The row's own column wins over whatever its JSON says.
        assertEquals(Language.CHINESE, parseContentResponse(stored, ContentMode.STORY, Language.CHINESE).language)
    }
}
