package digital.heirlooms.server.repository.capsule

import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for Wave 3 recipient→connection linking.
 *
 * Operates on capsule_recipients.connection_id (added by V33 migration).
 */
interface RecipientLinkRepository {

    sealed class LinkResult {
        /**
         * Success: returns the linked recipient row fields.
         */
        data class Linked(
            val recipientId: UUID,
            val capsuleId: UUID,
            val recipient: String,
            val connectionId: UUID,
        ) : LinkResult()

        /** The capsule does not exist or does not belong to the caller. */
        object CapsuleNotFound : LinkResult()

        /** The capsule_recipients row does not exist or does not belong to the capsule. */
        object RecipientNotFound : LinkResult()

        /** The connection does not exist or does not belong to the caller. */
        object ConnectionNotFound : LinkResult()

        /** The connection_id is already linked to another recipient row on this capsule. */
        object DuplicateConnection : LinkResult()
    }

    fun linkRecipient(
        capsuleId: UUID,
        recipientId: UUID,
        connectionId: UUID,
        callerUserId: UUID,
    ): LinkResult
}

class PostgresRecipientLinkRepository(private val dataSource: DataSource) : RecipientLinkRepository {

    override fun linkRecipient(
        capsuleId: UUID,
        recipientId: UUID,
        connectionId: UUID,
        callerUserId: UUID,
    ): RecipientLinkRepository.LinkResult {
        dataSource.connection.use { conn ->
            // 1. Verify capsule exists and belongs to the caller
            val capsuleExists = conn.prepareStatement(
                "SELECT 1 FROM capsules WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                stmt.executeQuery().next()
            }
            if (!capsuleExists) return RecipientLinkRepository.LinkResult.CapsuleNotFound

            // 2. Verify the recipient row belongs to this capsule
            val recipientExists = conn.prepareStatement(
                "SELECT 1 FROM capsule_recipients WHERE id = ? AND capsule_id = ?"
            ).use { stmt ->
                stmt.setObject(1, recipientId)
                stmt.setObject(2, capsuleId)
                stmt.executeQuery().next()
            }
            if (!recipientExists) return RecipientLinkRepository.LinkResult.RecipientNotFound

            // 3. Verify the connection belongs to the caller
            val connectionExists = conn.prepareStatement(
                "SELECT 1 FROM connections WHERE id = ? AND owner_user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                stmt.setObject(2, callerUserId)
                stmt.executeQuery().next()
            }
            if (!connectionExists) return RecipientLinkRepository.LinkResult.ConnectionNotFound

            // 4. Check uniqueness: connection_id already linked to another recipient on this capsule
            val alreadyLinked = conn.prepareStatement(
                """SELECT 1 FROM capsule_recipients
                   WHERE capsule_id = ? AND connection_id = ? AND id != ?"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, connectionId)
                stmt.setObject(3, recipientId)
                stmt.executeQuery().next()
            }
            if (alreadyLinked) return RecipientLinkRepository.LinkResult.DuplicateConnection

            // 5. Perform the link
            conn.prepareStatement(
                "UPDATE capsule_recipients SET connection_id = ? WHERE id = ? AND capsule_id = ?"
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                stmt.setObject(2, recipientId)
                stmt.setObject(3, capsuleId)
                stmt.executeUpdate()
            }

            // 6. Return the updated row
            val row = conn.prepareStatement(
                "SELECT id, capsule_id, recipient FROM capsule_recipients WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, recipientId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RecipientLinkRepository.LinkResult.RecipientNotFound
                Triple(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("capsule_id", UUID::class.java),
                    rs.getString("recipient"),
                )
            }

            return RecipientLinkRepository.LinkResult.Linked(
                recipientId = row.first,
                capsuleId = row.second,
                recipient = row.third,
                connectionId = connectionId,
            )
        }
    }
}
