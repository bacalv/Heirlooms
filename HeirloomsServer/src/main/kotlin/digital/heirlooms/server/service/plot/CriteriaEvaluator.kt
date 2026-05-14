package digital.heirlooms.server.service.plot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate
import java.util.UUID

class CriteriaValidationException(message: String) : Exception(message)
class CriteriaCycleException(message: String) : Exception(message)

private val criteriaMapper = ObjectMapper()

typealias ParamSetter = (PreparedStatement, Int) -> Int

data class CriteriaFragment(val sql: String, val setters: List<ParamSetter>)

object CriteriaEvaluator {

    private const val MAX_DEPTH = 10
    private const val MAX_DATE_LENGTH = 20

    /**
     * Validates that [date] is a valid ISO-8601 date string (YYYY-MM-DD) and does not
     * exceed [MAX_DATE_LENGTH] characters. Throws [CriteriaValidationException] on failure.
     * This prevents PSQLException from propagating as a 500 when a malformed date is passed
     * directly to PostgreSQL's `::date` cast.
     */
    private fun validateDate(date: String, atomType: String) {
        if (date.length > MAX_DATE_LENGTH)
            throw CriteriaValidationException(
                "'$atomType' invalid date format: expected YYYY-MM-DD (value too long)"
            )
        try {
            LocalDate.parse(date)
        } catch (_: Exception) {
            throw CriteriaValidationException(
                "'$atomType' invalid date format: expected YYYY-MM-DD, got '$date'"
            )
        }
    }

    fun evaluate(
        criteriaJson: String,
        userId: UUID,
        conn: Connection,
        visited: Set<UUID> = emptySet(),
        depth: Int = 0,
    ): CriteriaFragment {
        val node = criteriaMapper.readTree(criteriaJson)
        return evalNode(node, userId, conn, visited, depth)
    }

    fun evaluate(
        node: JsonNode,
        userId: UUID,
        conn: Connection,
        visited: Set<UUID> = emptySet(),
        depth: Int = 0,
    ): CriteriaFragment = evalNode(node, userId, conn, visited, depth)

    fun validate(node: JsonNode, userId: UUID, conn: Connection) {
        evalNode(node, userId, conn, emptySet(), 0)
    }

    private fun evalNode(
        node: JsonNode,
        userId: UUID,
        conn: Connection,
        visited: Set<UUID>,
        depth: Int,
    ): CriteriaFragment {
        if (depth > MAX_DEPTH) throw CriteriaValidationException("Criteria expression exceeds maximum nesting depth of $MAX_DEPTH")

        val type = node.get("type")?.asText()
            ?: throw CriteriaValidationException("Criteria node missing 'type' field")

        return when (type) {
            "tag" -> {
                val tag = node.get("tag")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'tag' atom requires a non-empty 'tag' field")
                CriteriaFragment(
                    "tags @> ARRAY[?]::text[]",
                    listOf { stmt, idx -> stmt.setString(idx, tag); idx + 1 }
                )
            }

            "media_type" -> {
                val value = node.get("value")?.asText()
                    ?: throw CriteriaValidationException("'media_type' atom requires a 'value' field")
                when (value) {
                    "image" -> CriteriaFragment("mime_type LIKE 'image/%'", emptyList())
                    "video" -> CriteriaFragment("mime_type LIKE 'video/%'", emptyList())
                    else    -> throw CriteriaValidationException("'media_type' value must be 'image' or 'video', got '$value'")
                }
            }

            "taken_after" -> {
                val date = node.get("date")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'taken_after' atom requires a non-empty 'date' field")
                validateDate(date, "taken_after")
                CriteriaFragment(
                    "taken_at >= ?::date",
                    listOf { stmt, idx -> stmt.setString(idx, date); idx + 1 }
                )
            }

            "taken_before" -> {
                val date = node.get("date")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'taken_before' atom requires a non-empty 'date' field")
                validateDate(date, "taken_before")
                CriteriaFragment(
                    "taken_at < (?::date + INTERVAL '1 day')",
                    listOf { stmt, idx -> stmt.setString(idx, date); idx + 1 }
                )
            }

            "uploaded_after" -> {
                val date = node.get("date")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'uploaded_after' atom requires a non-empty 'date' field")
                validateDate(date, "uploaded_after")
                CriteriaFragment(
                    "uploaded_at >= ?::date",
                    listOf { stmt, idx -> stmt.setString(idx, date); idx + 1 }
                )
            }

            "uploaded_before" -> {
                val date = node.get("date")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'uploaded_before' atom requires a non-empty 'date' field")
                validateDate(date, "uploaded_before")
                CriteriaFragment(
                    "uploaded_at < (?::date + INTERVAL '1 day')",
                    listOf { stmt, idx -> stmt.setString(idx, date); idx + 1 }
                )
            }

            "has_location" -> CriteriaFragment(
                "latitude IS NOT NULL AND longitude IS NOT NULL",
                emptyList()
            )

            "device_make" -> {
                val value = node.get("value")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'device_make' atom requires a non-empty 'value' field")
                CriteriaFragment(
                    "device_make ILIKE ?",
                    listOf { stmt, idx -> stmt.setString(idx, value); idx + 1 }
                )
            }

            "device_model" -> {
                val value = node.get("value")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'device_model' atom requires a non-empty 'value' field")
                CriteriaFragment(
                    "device_model ILIKE ?",
                    listOf { stmt, idx -> stmt.setString(idx, value); idx + 1 }
                )
            }

            "is_received" -> CriteriaFragment(
                "shared_from_user_id IS NOT NULL",
                emptyList()
            )

            "received_from" -> {
                val friendUserIdStr = node.get("user_id")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'received_from' atom requires a non-empty 'user_id' field")
                val friendUserId = try { UUID.fromString(friendUserIdStr) }
                    catch (_: Exception) { throw CriteriaValidationException("'received_from' user_id is not a valid UUID") }
                CriteriaFragment(
                    "shared_from_user_id = ?",
                    listOf { stmt, idx -> stmt.setObject(idx, friendUserId); idx + 1 }
                )
            }

            "in_capsule" -> CriteriaFragment(
                """EXISTS (
                    SELECT 1 FROM capsule_contents cc
                    JOIN capsules c ON c.id = cc.capsule_id
                    WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                )""",
                emptyList()
            )

            "just_arrived" -> CriteriaFragment(
                """last_viewed_at IS NULL
                   AND tags = '{}'::text[]
                   AND composted_at IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM capsule_contents cc
                       JOIN capsules c ON c.id = cc.capsule_id
                       WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                   )""",
                emptyList()
            )

            "composted" -> CriteriaFragment(
                "composted_at IS NOT NULL",
                emptyList()
            )

            "near" -> throw CriteriaValidationException(
                "'near' predicate is not yet implemented (GPS coordinates are not available for encrypted uploads)"
            )

            "plot_ref" -> {
                val plotIdStr = node.get("plot_id")?.asText()?.takeIf { it.isNotBlank() }
                    ?: throw CriteriaValidationException("'plot_ref' atom requires a non-empty 'plot_id' field")
                val refId = try { UUID.fromString(plotIdStr) }
                    catch (_: Exception) { throw CriteriaValidationException("'plot_ref' plot_id is not a valid UUID") }

                if (refId in visited) throw CriteriaCycleException(
                    "Circular plot_ref detected: plot $refId is already in the evaluation chain"
                )

                val refCriteriaJson = conn.prepareStatement(
                    "SELECT criteria FROM plots WHERE id = ? AND owner_user_id = ?"
                ).use { stmt ->
                    stmt.setObject(1, refId)
                    stmt.setObject(2, userId)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) throw CriteriaValidationException(
                        "plot_ref references plot $refId which does not exist or belongs to another user"
                    )
                    rs.getString("criteria")
                        ?: throw CriteriaValidationException("plot_ref references plot $refId which has no criteria")
                }

                val refNode = criteriaMapper.readTree(refCriteriaJson)
                evalNode(refNode, userId, conn, visited + refId, depth + 1)
            }

            "and" -> {
                val operands = node.get("operands")
                if (operands == null || !operands.isArray || operands.size() == 0)
                    throw CriteriaValidationException("'and' node requires at least one operand")
                val parts = operands.map { evalNode(it, userId, conn, visited, depth + 1) }
                CriteriaFragment(
                    parts.joinToString(" AND ") { "(${it.sql})" },
                    parts.flatMap { it.setters }
                )
            }

            "or" -> {
                val operands = node.get("operands")
                if (operands == null || !operands.isArray || operands.size() == 0)
                    throw CriteriaValidationException("'or' node requires at least one operand")
                val parts = operands.map { evalNode(it, userId, conn, visited, depth + 1) }
                CriteriaFragment(
                    parts.joinToString(" OR ") { "(${it.sql})" },
                    parts.flatMap { it.setters }
                )
            }

            "not" -> {
                val operand = node.get("operand")
                    ?: throw CriteriaValidationException("'not' node requires an 'operand'")
                val inner = evalNode(operand, userId, conn, visited, depth + 1)
                CriteriaFragment("NOT (${inner.sql})", inner.setters)
            }

            else -> throw CriteriaValidationException("Unknown criteria node type: '$type'")
        }
    }
}
