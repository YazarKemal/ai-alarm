package com.kemalcetin.aialarm.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the in-app language model: English is the default, the twelve
 * required tags are supported, tags map correctly, and invalid/null persisted
 * values fall back to English.
 */
class AppLanguageTest {

    @Test
    fun `english is the default language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.default)
        assertEquals("en", AppLanguage.DEFAULT_TAG)
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null))
    }

    @Test
    fun `all twelve required languages are supported`() {
        val expected = setOf(
            "en", "tr", "es", "pt-BR", "de", "fr",
            "it", "id", "hi", "ja", "ko", "ar"
        )
        val actual = AppLanguage.entries.map { it.tag }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `fromTag maps every supported tag`() {
        val byTag = AppLanguage.entries.associateBy { it.tag }
        byTag.forEach { (tag, lang) ->
            assertEquals(lang, AppLanguage.fromTag(tag))
        }
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.fromTag("pt-BR"))
    }

    @Test
    fun `fromTag is case-insensitive`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN"))
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.fromTag("pt-br"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("AR"))
    }

    @Test
    fun `unknown tag returns null`() {
        assertNull(AppLanguage.fromTag("xx"))
        assertNull(AppLanguage.fromTag("en-US"))
    }

    @Test
    fun `invalid persisted tag falls back to english`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve("zz"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve("pt")) // not an exact supported tag
    }

    @Test
    fun `persisted tag resolves to the matching language`() {
        assertEquals(AppLanguage.TURKISH, AppLanguage.resolve("tr"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.resolve("de"))
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.resolve("pt-BR"))
    }

    @Test
    fun `aiTag uses the selected app language`() {
        assertEquals("en-US", AppLanguage.ENGLISH.aiTag)
        assertEquals("tr-TR", AppLanguage.TURKISH.aiTag)
        assertEquals("pt-BR", AppLanguage.PORTUGUESE_BRAZIL.aiTag)
        assertEquals("es", AppLanguage.SPANISH.aiTag)
        assertEquals("ar", AppLanguage.ARABIC.aiTag)
    }

    @Test
    fun `locale is built from the bcp-47 tag`() {
        assertEquals("en", AppLanguage.ENGLISH.locale.toLanguageTag())
        assertEquals("pt-BR", AppLanguage.PORTUGUESE_BRAZIL.locale.toLanguageTag())
        assertEquals("ar", AppLanguage.ARABIC.locale.toLanguageTag())
    }

    @Test
    fun `every language has a non-blank native name`() {
        assertTrue(AppLanguage.entries.all { it.nativeName.isNotBlank() })
    }
}
