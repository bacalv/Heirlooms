package digital.heirlooms.server

import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleRecord
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.representation.capsule.toDetailJson
import digital.heirlooms.server.representation.capsule.toReverseLookupJson
import digital.heirlooms.server.representation.capsule.toSummaryJson
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class CapsuleHandlerTest {

    private val mapper = ObjectMapper()

    private val fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val fixedInstant = Instant.parse("2026-05-01T10:00:00Z")
    private val fixedUnlockAt = OffsetDateTime.parse("2042-05-14T08:00:00+00:00")

    private fun record(
        state: CapsuleState = CapsuleState.OPEN,
        shape: CapsuleShape = CapsuleShape.OPEN,
        cancelledAt: Instant? = null,
        deliveredAt: Instant? = null,
    ) = CapsuleRecord(
        id = fixedId,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
        createdByUser = "api-user",
        shape = shape,
        state = state,
        unlockAt = fixedUnlockAt,
        cancelledAt = cancelledAt,
        deliveredAt = deliveredAt,
    )

    private fun summary(
        state: CapsuleState = CapsuleState.OPEN,
        shape: CapsuleShape = CapsuleShape.OPEN,
        recipients: List<String> = listOf("Sophie"),
        uploadCount: Int = 0,
        hasMessage: Boolean = false,
        cancelledAt: Instant? = null,
        deliveredAt: Instant? = null,
    ) = CapsuleSummary(
        record = record(state, shape, cancelledAt, deliveredAt),
        recipients = recipients,
        uploadCount = uploadCount,
        hasMessage = hasMessage,
    )

    // ---- toSummaryJson -------------------------------------------------------

    @Test
    fun `toSummaryJson produces parseable JSON`() {
        // Any parse exception here is a test failure — strict by default in Jackson
        mapper.readTree(summary().toSummaryJson())
    }

    @Test
    fun `toSummaryJson state field is a bare string value`() {
        val node = mapper.readTree(summary(state = CapsuleState.OPEN).toSummaryJson())
        assertEquals("open", node.get("state").asText())
    }

    @Test
    fun `toSummaryJson sealed state serialises correctly`() {
        val node = mapper.readTree(summary(state = CapsuleState.SEALED, shape = CapsuleShape.SEALED).toSummaryJson())
        assertEquals("sealed", node.get("state").asText())
        assertEquals("sealed", node.get("shape").asText())
    }

    @Test
    fun `toSummaryJson cancelled state serialises correctly`() {
        val cancelledAt = Instant.parse("2026-05-12T10:00:00Z")
        val node = mapper.readTree(summary(state = CapsuleState.CANCELLED, cancelledAt = cancelledAt).toSummaryJson())
        assertEquals("cancelled", node.get("state").asText())
        assertEquals(cancelledAt.toString(), node.get("cancelled_at").asText())
        assertTrue(node.get("delivered_at").isNull)
    }

    @Test
    fun `toSummaryJson recipients array contains all values`() {
        val node = mapper.readTree(summary(recipients = listOf("Sophie", "James")).toSummaryJson())
        val arr = node.get("recipients")
        assertTrue(arr.isArray)
        assertEquals(2, arr.size())
        assertEquals("Sophie", arr.get(0).asText())
        assertEquals("James", arr.get(1).asText())
    }

    @Test
    fun `toSummaryJson upload_count and has_message are correct types`() {
        val node = mapper.readTree(summary(uploadCount = 7, hasMessage = true).toSummaryJson())
        assertEquals(7, node.get("upload_count").asInt())
        assertTrue(node.get("has_message").asBoolean())
    }

    // ---- toDetailJson -------------------------------------------------------

    @Test
    fun `toDetailJson produces parseable JSON`() {
        val detail = CapsuleDetail(record = record(), recipients = listOf("Sophie"), uploads = emptyList(), message = "Hello")
        mapper.readTree(detail.toDetailJson())
    }

    @Test
    fun `toDetailJson state field is a bare string value`() {
        val detail = CapsuleDetail(record = record(), recipients = listOf("Sophie"), uploads = emptyList(), message = "")
        val node = mapper.readTree(detail.toDetailJson())
        assertEquals("open", node.get("state").asText())
    }

    @Test
    fun `toDetailJson message is correctly escaped`() {
        val detail = CapsuleDetail(
            record = record(),
            recipients = listOf("Sophie"),
            uploads = emptyList(),
            message = "Hello \"world\"\nand a newline",
        )
        val node = mapper.readTree(detail.toDetailJson())
        assertEquals("Hello \"world\"\nand a newline", node.get("message").asText())
    }

    @Test
    fun `toDetailJson uploads array is present and empty when no uploads`() {
        val detail = CapsuleDetail(record = record(), recipients = listOf("Sophie"), uploads = emptyList(), message = "")
        val node = mapper.readTree(detail.toDetailJson())
        assertTrue(node.get("uploads").isArray)
        assertEquals(0, node.get("uploads").size())
    }

    // ---- toReverseLookupJson ------------------------------------------------

    @Test
    fun `toReverseLookupJson produces parseable JSON`() {
        mapper.readTree(summary().toReverseLookupJson())
    }

    @Test
    fun `toReverseLookupJson state field is a bare string value`() {
        val node = mapper.readTree(summary(state = CapsuleState.SEALED, shape = CapsuleShape.SEALED).toReverseLookupJson())
        assertEquals("sealed", node.get("state").asText())
    }

    @Test
    fun `toReverseLookupJson contains only the expected fields`() {
        val node = mapper.readTree(summary().toReverseLookupJson())
        assertTrue(node.has("id"))
        assertTrue(node.has("shape"))
        assertTrue(node.has("state"))
        assertTrue(node.has("unlock_at"))
        assertTrue(node.has("recipients"))
        // Summary-only fields must not leak into the reverse-lookup shape
        assertTrue(!node.has("upload_count"))
        assertTrue(!node.has("has_message"))
    }
}
