package digital.heirlooms.ui.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagValidationTest {

    @Test
    fun valid_tags_pass() {
        assertTrue(isValidTag("family"))
        assertTrue(isValidTag("summer-2024"))
        assertTrue(isValidTag("a"))
        assertTrue(isValidTag("a-b-c-d"))
    }

    @Test
    fun invalid_tags_fail() {
        assertFalse(isValidTag(""))
        assertFalse(isValidTag(" family"))
        assertFalse(isValidTag("Family"))
        assertFalse(isValidTag("family!"))
        assertFalse(isValidTag("-leading"))
        assertFalse(isValidTag("trailing-"))
        assertFalse(isValidTag("double--dash"))
        assertFalse(isValidTag("a".repeat(51)))
    }
}
