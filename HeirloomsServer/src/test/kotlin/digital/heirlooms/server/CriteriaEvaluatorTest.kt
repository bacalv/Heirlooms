package digital.heirlooms.server

import digital.heirlooms.server.service.plot.CriteriaCycleException
import digital.heirlooms.server.service.plot.CriteriaEvaluator
import digital.heirlooms.server.service.plot.CriteriaFragment
import digital.heirlooms.server.service.plot.CriteriaValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

class CriteriaEvaluatorTest {

    private val mapper = ObjectMapper()
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noopConn = mockk<Connection>(relaxed = true)

    private fun eval(json: String): CriteriaFragment =
        CriteriaEvaluator.evaluate(mapper.readTree(json), userId, noopConn)

    private fun sql(json: String) = eval(json).sql

    // ---- Atom: tag -----------------------------------------------------------

    @Test
    fun `tag atom produces correct SQL`() {
        val f = eval("""{"type":"tag","tag":"family"}""")
        assertEquals("tags @> ARRAY[?]::text[]", f.sql)
        assertEquals(1, f.setters.size)
    }

    @Test
    fun `tag atom with empty tag throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"tag","tag":""}""")
        }
    }

    // ---- Atom: media_type ----------------------------------------------------

    @Test
    fun `media_type image produces correct SQL`() {
        assertEquals("mime_type LIKE 'image/%'", sql("""{"type":"media_type","value":"image"}"""))
    }

    @Test
    fun `media_type video produces correct SQL`() {
        assertEquals("mime_type LIKE 'video/%'", sql("""{"type":"media_type","value":"video"}"""))
    }

    @Test
    fun `media_type unknown value throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"media_type","value":"audio"}""")
        }
    }

    // ---- Atom: date range ----------------------------------------------------

    @Test
    fun `taken_after produces correct SQL`() {
        val f = eval("""{"type":"taken_after","date":"2024-01-01"}""")
        assertEquals("taken_at >= ?::date", f.sql)
        assertEquals(1, f.setters.size)
    }

    @Test
    fun `taken_before produces correct SQL`() {
        val f = eval("""{"type":"taken_before","date":"2024-12-31"}""")
        assertEquals("taken_at < (?::date + INTERVAL '1 day')", f.sql)
        assertEquals(1, f.setters.size)
    }

    @Test
    fun `uploaded_after produces correct SQL`() {
        val f = eval("""{"type":"uploaded_after","date":"2024-01-01"}""")
        assertEquals("uploaded_at >= ?::date", f.sql)
    }

    @Test
    fun `uploaded_before produces correct SQL`() {
        val f = eval("""{"type":"uploaded_before","date":"2024-12-31"}""")
        assertEquals("uploaded_at < (?::date + INTERVAL '1 day')", f.sql)
    }

    // ---- Atom: has_location --------------------------------------------------

    @Test
    fun `has_location produces correct SQL`() {
        assertEquals("latitude IS NOT NULL AND longitude IS NOT NULL",
            sql("""{"type":"has_location"}"""))
    }

    // ---- Atom: device --------------------------------------------------------

    @Test
    fun `device_make produces case-insensitive SQL`() {
        val f = eval("""{"type":"device_make","value":"Apple"}""")
        assertEquals("device_make ILIKE ?", f.sql)
        assertEquals(1, f.setters.size)
    }

    @Test
    fun `device_model produces case-insensitive SQL`() {
        val f = eval("""{"type":"device_model","value":"SM-T517D"}""")
        assertEquals("device_model ILIKE ?", f.sql)
    }

    // ---- Atom: received ------------------------------------------------------

    @Test
    fun `is_received produces correct SQL`() {
        assertEquals("shared_from_user_id IS NOT NULL",
            sql("""{"type":"is_received"}"""))
    }

    @Test
    fun `received_from produces correct SQL`() {
        val friendId = UUID.randomUUID()
        val f = eval("""{"type":"received_from","user_id":"$friendId"}""")
        assertEquals("shared_from_user_id = ?", f.sql)
        assertEquals(1, f.setters.size)
    }

    // ---- Atom: in_capsule ----------------------------------------------------

    @Test
    fun `in_capsule produces subquery SQL`() {
        val f = eval("""{"type":"in_capsule"}""")
        assertTrue(f.sql.contains("capsule_contents"))
        assertTrue(f.sql.startsWith("EXISTS"))
    }

    // ---- Atom: just_arrived --------------------------------------------------

    @Test
    fun `just_arrived produces full predicate SQL`() {
        val f = eval("""{"type":"just_arrived"}""")
        assertTrue(f.sql.contains("last_viewed_at IS NULL"))
        assertTrue(f.sql.contains("tags = '{}'"))
        assertTrue(f.sql.contains("composted_at IS NULL"))
        assertTrue(f.sql.contains("capsule_contents"))
    }

    // ---- Atom: composted -----------------------------------------------------

    @Test
    fun `composted produces correct SQL`() {
        assertEquals("composted_at IS NOT NULL", sql("""{"type":"composted"}"""))
    }

    // ---- Composition: and ----------------------------------------------------

    @Test
    fun `and of two atoms produces AND SQL`() {
        val f = eval("""{"type":"and","operands":[{"type":"media_type","value":"image"},{"type":"composted"}]}""")
        assertEquals("(mime_type LIKE 'image/%') AND (composted_at IS NOT NULL)", f.sql)
        assertEquals(0, f.setters.size)
    }

    @Test
    fun `and with empty operands throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"and","operands":[]}""")
        }
    }

    // ---- Composition: or -----------------------------------------------------

    @Test
    fun `or of two atoms produces OR SQL`() {
        val f = eval("""{"type":"or","operands":[{"type":"media_type","value":"image"},{"type":"media_type","value":"video"}]}""")
        assertEquals("(mime_type LIKE 'image/%') OR (mime_type LIKE 'video/%')", f.sql)
    }

    @Test
    fun `or with empty operands throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"or","operands":[]}""")
        }
    }

    // ---- Composition: not ----------------------------------------------------

    @Test
    fun `not wrapping an atom produces NOT SQL`() {
        val f = eval("""{"type":"not","operand":{"type":"composted"}}""")
        assertEquals("NOT (composted_at IS NOT NULL)", f.sql)
    }

    @Test
    fun `not missing operand throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"not"}""")
        }
    }

    // ---- Nested composition --------------------------------------------------

    @Test
    fun `nested and(tag, not(tag)) produces correct SQL`() {
        val f = eval("""{"type":"and","operands":[{"type":"tag","tag":"family"},{"type":"not","operand":{"type":"tag","tag":"trip"}}]}""")
        assertEquals("(tags @> ARRAY[?]::text[]) AND (NOT (tags @> ARRAY[?]::text[]))", f.sql)
        assertEquals(2, f.setters.size)
    }

    // ---- Atom: plot_ref ------------------------------------------------------

    @Test
    fun `plot_ref inlines referenced plot criteria`() {
        val refId = UUID.randomUUID()
        val stmt = mockk<PreparedStatement>(relaxed = true)
        val rs = mockk<ResultSet>()
        every { noopConn.prepareStatement(any()) } returns stmt
        every { stmt.executeQuery() } returns rs
        every { rs.next() } returns true
        every { rs.getString("criteria") } returns """{"type":"composted"}"""

        val f = CriteriaEvaluator.evaluate(
            mapper.readTree("""{"type":"plot_ref","plot_id":"$refId"}"""),
            userId, noopConn
        )
        assertEquals("composted_at IS NOT NULL", f.sql)
    }

    @Test
    fun `plot_ref cycle A to A throws CriteriaCycleException`() {
        val plotId = UUID.randomUUID()
        assertThrows<CriteriaCycleException> {
            CriteriaEvaluator.evaluate(
                mapper.readTree("""{"type":"plot_ref","plot_id":"$plotId"}"""),
                userId, noopConn,
                visited = setOf(plotId)
            )
        }
    }

    @Test
    fun `plot_ref to non-existent plot throws validation error`() {
        val refId = UUID.randomUUID()
        val stmt = mockk<PreparedStatement>(relaxed = true)
        val rs = mockk<ResultSet>()
        every { noopConn.prepareStatement(any()) } returns stmt
        every { stmt.executeQuery() } returns rs
        every { rs.next() } returns false

        assertThrows<CriteriaValidationException> {
            CriteriaEvaluator.evaluate(
                mapper.readTree("""{"type":"plot_ref","plot_id":"$refId"}"""),
                userId, noopConn
            )
        }
    }

    // ---- Unsupported / unknown -----------------------------------------------

    @Test
    fun `near atom throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"near","lat":51.5,"lng":-0.1,"radius_metres":500}""")
        }
    }

    @Test
    fun `unknown atom type throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"type":"unknown_atom"}""")
        }
    }

    @Test
    fun `missing type field throws validation error`() {
        assertThrows<CriteriaValidationException> {
            eval("""{"tag":"family"}""")
        }
    }
}
