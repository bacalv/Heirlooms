package digital.heirlooms.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TagValidatorTest {

    @Test
    fun `valid simple tag accepted`() {
        assertEquals(TagValidationResult.Valid, validateTags(listOf("family")))
    }

    @Test
    fun `valid kebab-case with numbers accepted`() {
        assertEquals(TagValidationResult.Valid, validateTags(listOf("2026-summer")))
    }

    @Test
    fun `valid multi-segment tag accepted`() {
        assertEquals(TagValidationResult.Valid, validateTags(listOf("my-children-photos")))
    }

    @Test
    fun `empty list is valid`() {
        assertEquals(TagValidationResult.Valid, validateTags(emptyList()))
    }

    @Test
    fun `single character tags accepted`() {
        assertEquals(TagValidationResult.Valid, validateTags(listOf("a")))
        assertEquals(TagValidationResult.Valid, validateTags(listOf("1")))
    }

    @Test
    fun `tag with uppercase rejected with kebab-case reason`() {
        val result = validateTags(listOf("Family")) as? TagValidationResult.Invalid
        assertEquals("Family", result?.tag)
        assertTrue(result?.reason?.contains("kebab-case") == true)
    }

    @Test
    fun `tag with space rejected`() {
        val result = validateTags(listOf("my tag")) as? TagValidationResult.Invalid
        assertEquals("my tag", result?.tag)
    }

    @Test
    fun `tag with underscore rejected`() {
        val result = validateTags(listOf("my_tag")) as? TagValidationResult.Invalid
        assertEquals("my_tag", result?.tag)
    }

    @Test
    fun `tag with leading or trailing hyphen rejected`() {
        assertTrue(validateTags(listOf("-tag")) is TagValidationResult.Invalid)
        assertTrue(validateTags(listOf("tag-")) is TagValidationResult.Invalid)
    }

    @Test
    fun `tag with consecutive hyphens rejected`() {
        assertTrue(validateTags(listOf("my--tag")) is TagValidationResult.Invalid)
    }

    @Test
    fun `tag at exactly 50 chars accepted`() {
        assertEquals(TagValidationResult.Valid, validateTags(listOf("a".repeat(50))))
    }

    @Test
    fun `tag at 51 chars rejected with too long reason`() {
        val result = validateTags(listOf("a".repeat(51))) as? TagValidationResult.Invalid
        assertTrue(result?.reason?.contains("too long") == true)
    }

    @Test
    fun `empty string rejected with empty reason`() {
        val result = validateTags(listOf("")) as? TagValidationResult.Invalid
        assertEquals("empty", result?.reason)
    }

    @Test
    fun `error identifies the offending tag among multiple`() {
        val result = validateTags(listOf("valid-tag", "InvalidTag", "another-valid")) as? TagValidationResult.Invalid
        assertEquals("InvalidTag", result?.tag)
    }
}
