package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.CriteriaEvaluator
import digital.heirlooms.server.CriteriaValidationException
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.plot.PlotRecord
import digital.heirlooms.server.domain.upload.UploadRecord
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class PlotItemRepository(private val dataSource: DataSource) {

    // ── Staging operations ────────────────────────────────────────────────────

    fun getStagingItems(
        flowId: UUID,
        flow: digital.heirlooms.server.domain.plot.FlowRecord,
        userId: UUID = FOUNDING_USER_ID,
    ): List<UploadRecord> {
        val plotId = flow.targetPlotId

        dataSource.connection.use { conn ->
            val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
            val sql = buildString {
                append("""SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                                  thumbnail_key, taken_at, latitude, longitude, altitude,
                                  device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                                  last_viewed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                                  encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                                  wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                                  wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds,
                                  shared_from_upload_id, shared_from_user_id
                           FROM uploads
                           WHERE user_id = ?
                             AND composted_at IS NULL
                             AND (""")
                append(fragment.sql)
                append(""")
                             AND NOT EXISTS (SELECT 1 FROM plot_staging_decisions psd
                                            WHERE psd.plot_id = ? AND psd.upload_id = uploads.id)
                             AND NOT EXISTS (SELECT 1 FROM plot_items pi
                                            WHERE pi.plot_id = ? AND pi.upload_id = uploads.id)
                           ORDER BY uploaded_at DESC, id DESC""")
            }
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, userId)
                for (setter in fragment.setters) idx = setter(stmt, idx)
                stmt.setObject(idx++, plotId)
                stmt.setObject(idx, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    fun getStagingItemsForPlot(
        plotId: UUID,
        flows: List<digital.heirlooms.server.domain.plot.FlowRecord>,
        userId: UUID = FOUNDING_USER_ID,
    ): List<UploadRecord> {
        if (flows.isEmpty()) return emptyList()

        dataSource.connection.use { conn ->
            val allItems = mutableMapOf<UUID, UploadRecord>()
            for (flow in flows) {
                try {
                    val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
                    val sql = buildString {
                        append("""SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                                          thumbnail_key, taken_at, latitude, longitude, altitude,
                                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                                          last_viewed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds,
                                          shared_from_upload_id, shared_from_user_id
                                   FROM uploads
                                   WHERE user_id = ?
                                     AND composted_at IS NULL
                                     AND (""")
                        append(fragment.sql)
                        append(""")
                                     AND NOT EXISTS (SELECT 1 FROM plot_staging_decisions psd
                                                    WHERE psd.plot_id = ? AND psd.upload_id = uploads.id)
                                     AND NOT EXISTS (SELECT 1 FROM plot_items pi
                                                    WHERE pi.plot_id = ? AND pi.upload_id = uploads.id)
                                   ORDER BY uploaded_at DESC, id DESC""")
                    }
                    conn.prepareStatement(sql).use { stmt ->
                        var idx = 1
                        stmt.setObject(idx++, userId)
                        for (setter in fragment.setters) idx = setter(stmt, idx)
                        stmt.setObject(idx++, plotId)
                        stmt.setObject(idx, plotId)
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            val u = rs.toUploadRecord()
                            allItems[u.id] = u
                        }
                    }
                } catch (_: CriteriaValidationException) { /* skip invalid flow */ }
            }
            return allItems.values.sortedByDescending { it.uploadedAt }
        }
    }

    sealed class ApproveResult {
        object Success : ApproveResult()
        object NotFound : ApproveResult()
        object AlreadyApproved : ApproveResult()
        object PlotNotOwned : ApproveResult()
        object PlotClosed : ApproveResult()
        object DuplicateContent : ApproveResult()
    }

    fun approveStagingItem(
        plot: PlotRecord,
        uploadId: UUID,
        sourceFlowId: UUID?,
        userId: UUID = FOUNDING_USER_ID,
        wrappedItemDekBytes: ByteArray? = null,
        itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null,
        thumbnailDekFormat: String? = null,
    ): ApproveResult {
        val plotId = plot.id
        val isOwner = plot.ownerUserId == userId
        val isMember = plot.visibility == "shared" && isMember(plotId, userId)
        if (!isOwner && !isMember) return ApproveResult.PlotNotOwned
        if (plot.plotStatus == "closed") return ApproveResult.PlotClosed

        withTransaction { conn ->
            val exists = conn.prepareStatement(
                "SELECT 1 FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.executeQuery().next()
            }
            if (exists) return ApproveResult.AlreadyApproved

            if (plot.visibility == "shared") {
                val incomingHash = conn.prepareStatement(
                    "SELECT content_hash FROM uploads WHERE id = ?"
                ).use { stmt ->
                    stmt.setObject(1, uploadId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getString(1) else null
                }
                if (incomingHash != null) {
                    val isDuplicate = conn.prepareStatement(
                        """SELECT 1 FROM plot_items pi
                           JOIN uploads u ON u.id = pi.upload_id
                           WHERE pi.plot_id = ? AND u.content_hash = ?
                           LIMIT 1"""
                    ).use { stmt ->
                        stmt.setObject(1, plotId); stmt.setString(2, incomingHash)
                        stmt.executeQuery().next()
                    }
                    if (isDuplicate) {
                        conn.prepareStatement(
                            """INSERT INTO plot_staging_decisions (plot_id, upload_id, decision, source_flow_id)
                               VALUES (?, ?, 'approved', ?)
                               ON CONFLICT (plot_id, upload_id) DO UPDATE SET decision = 'approved', decided_at = NOW()"""
                        ).use { stmt ->
                            stmt.setObject(1, plotId); stmt.setObject(2, uploadId); stmt.setObject(3, sourceFlowId)
                            stmt.executeUpdate()
                        }
                        return ApproveResult.DuplicateContent
                    }
                }
            }

            conn.prepareStatement(
                """INSERT INTO plot_items (plot_id, upload_id, added_by, source_flow_id,
                   wrapped_item_dek, item_dek_format, wrapped_thumbnail_dek, thumbnail_dek_format, added_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.setObject(3, userId); stmt.setObject(4, sourceFlowId)
                stmt.setBytes(5, wrappedItemDekBytes); stmt.setString(6, itemDekFormat)
                stmt.setBytes(7, wrappedThumbnailDekBytes); stmt.setString(8, thumbnailDekFormat)
                stmt.executeUpdate()
            }

            conn.prepareStatement(
                """INSERT INTO plot_staging_decisions (plot_id, upload_id, decision, source_flow_id)
                   VALUES (?, ?, 'approved', ?)
                   ON CONFLICT (plot_id, upload_id) DO UPDATE SET decision = 'approved', decided_at = NOW()"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId); stmt.setObject(3, sourceFlowId)
                stmt.executeUpdate()
            }
        }
        return ApproveResult.Success
    }

    sealed class RejectResult {
        object Success : RejectResult()
        object NotFound : RejectResult()
        object AlreadyApproved : RejectResult()
        object PlotNotOwned : RejectResult()
    }

    fun rejectStagingItem(plot: PlotRecord, uploadId: UUID, sourceFlowId: UUID?, userId: UUID = FOUNDING_USER_ID): RejectResult {
        if (plot.ownerUserId != userId) return RejectResult.PlotNotOwned

        dataSource.connection.use { conn ->
            val approved = conn.prepareStatement(
                "SELECT 1 FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plot.id); stmt.setObject(2, uploadId)
                stmt.executeQuery().next()
            }
            if (approved) return RejectResult.AlreadyApproved

            conn.prepareStatement(
                """INSERT INTO plot_staging_decisions (plot_id, upload_id, decision, source_flow_id)
                   VALUES (?, ?, 'rejected', ?)
                   ON CONFLICT (plot_id, upload_id) DO UPDATE SET decision = 'rejected', decided_at = NOW()"""
            ).use { stmt ->
                stmt.setObject(1, plot.id); stmt.setObject(2, uploadId); stmt.setObject(3, sourceFlowId)
                stmt.executeUpdate()
            }
        }
        return RejectResult.Success
    }

    fun deleteDecision(plot: PlotRecord, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        if (plot.ownerUserId != userId) return false
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM plot_staging_decisions WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plot.id); stmt.setObject(2, uploadId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun getRejectedItems(plot: PlotRecord, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        if (plot.ownerUserId != userId) return emptyList()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                          u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                          u.device_make, u.device_model, u.rotation, u.tags, u.composted_at, u.exif_processed_at,
                          u.last_viewed_at, u.storage_class, u.envelope_version, u.wrapped_dek, u.dek_format,
                          u.encrypted_metadata, u.encrypted_metadata_format, u.thumbnail_storage_key,
                          u.wrapped_thumbnail_dek, u.thumbnail_dek_format, u.preview_storage_key,
                          u.wrapped_preview_dek, u.preview_dek_format, u.plain_chunk_size, u.duration_seconds,
                          u.shared_from_upload_id, u.shared_from_user_id
                   FROM uploads u
                   JOIN plot_staging_decisions psd ON psd.upload_id = u.id
                   WHERE psd.plot_id = ? AND psd.decision = 'rejected'
                   ORDER BY psd.decided_at DESC"""
            ).use { stmt ->
                stmt.setObject(1, plot.id)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    // ── Collection plot item operations ───────────────────────────────────────

    fun getPlotItems(plotId: UUID, userId: UUID = FOUNDING_USER_ID, plot: PlotRecord?): List<PlotItemWithUpload> {
        if (plot == null) return emptyList()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                          u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                          u.device_make, u.device_model, u.rotation, u.tags, u.composted_at, u.exif_processed_at,
                          u.last_viewed_at, u.storage_class, u.envelope_version, u.wrapped_dek, u.dek_format,
                          u.encrypted_metadata, u.encrypted_metadata_format, u.thumbnail_storage_key,
                          u.wrapped_thumbnail_dek, u.thumbnail_dek_format, u.preview_storage_key,
                          u.wrapped_preview_dek, u.preview_dek_format, u.plain_chunk_size, u.duration_seconds,
                          u.shared_from_upload_id, u.shared_from_user_id,
                          pi.added_by, pi.wrapped_item_dek, pi.item_dek_format,
                          pi.wrapped_thumbnail_dek AS pi_wrapped_thumbnail_dek,
                          pi.thumbnail_dek_format AS pi_thumbnail_dek_format
                   FROM uploads u
                   JOIN plot_items pi ON pi.upload_id = u.id
                   WHERE pi.plot_id = ?
                   ORDER BY pi.added_at DESC"""
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotItemWithUpload>()
                while (rs.next()) {
                    results.add(PlotItemWithUpload(
                        upload = rs.toUploadRecord(),
                        addedBy = rs.getObject("added_by", UUID::class.java),
                        wrappedItemDek = rs.getBytes("wrapped_item_dek"),
                        itemDekFormat = rs.getString("item_dek_format"),
                        wrappedThumbnailDek = rs.getBytes("pi_wrapped_thumbnail_dek"),
                        thumbnailDekFormat = rs.getString("pi_thumbnail_dek_format"),
                    ))
                }
                return results
            }
        }
    }

    sealed class AddItemResult {
        object Success : AddItemResult()
        object AlreadyPresent : AddItemResult()
        object PlotNotOwned : AddItemResult()
        object UploadNotOwned : AddItemResult()
        object PlotClosed : AddItemResult()
        data class Error(val message: String) : AddItemResult()
    }

    fun addPlotItem(
        plot: PlotRecord?,
        uploadId: UUID,
        userId: UUID = FOUNDING_USER_ID,
        uploadExists: Boolean,
        wrappedItemDekBytes: ByteArray? = null,
        itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null,
        thumbnailDekFormat: String? = null,
    ): AddItemResult {
        if (plot == null) return AddItemResult.PlotNotOwned
        val isOwner = plot.ownerUserId == userId
        val isMember = plot.visibility == "shared" && isMember(plot.id, userId)
        if (!isOwner && !isMember) return AddItemResult.PlotNotOwned
        if (plot.tombstonedAt != null) return AddItemResult.PlotNotOwned
        if (plot.plotStatus == "closed") return AddItemResult.PlotClosed
        if (plot.criteria != null) return AddItemResult.Error("Plot is a query plot, not a collection plot")
        if (!uploadExists) return AddItemResult.UploadNotOwned

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """INSERT INTO plot_items (plot_id, upload_id, added_by, wrapped_item_dek, item_dek_format,
                       wrapped_thumbnail_dek, thumbnail_dek_format) VALUES (?, ?, ?, ?, ?, ?, ?)"""
                ).use { stmt ->
                    stmt.setObject(1, plot.id); stmt.setObject(2, uploadId); stmt.setObject(3, userId)
                    stmt.setBytes(4, wrappedItemDekBytes); stmt.setString(5, itemDekFormat)
                    stmt.setBytes(6, wrappedThumbnailDekBytes); stmt.setString(7, thumbnailDekFormat)
                    stmt.executeUpdate()
                }
            }
            AddItemResult.Success
        } catch (_: java.sql.SQLIntegrityConstraintViolationException) {
            AddItemResult.AlreadyPresent
        } catch (e: java.sql.SQLException) {
            if (e.sqlState?.startsWith("23") == true) AddItemResult.AlreadyPresent
            else AddItemResult.Error(e.message ?: "DB error")
        }
    }

    sealed class RemoveItemResult {
        object Success : RemoveItemResult()
        object NotFound : RemoveItemResult()
        object Forbidden : RemoveItemResult()
    }

    fun removePlotItem(plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): RemoveItemResult {
        dataSource.connection.use { conn ->
            val row = conn.prepareStatement(
                """SELECT pi.added_by, p.owner_user_id
                   FROM plot_items pi
                   JOIN plots p ON p.id = pi.plot_id
                   WHERE pi.plot_id = ? AND pi.upload_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RemoveItemResult.NotFound
                Pair(
                    rs.getObject("added_by", UUID::class.java),
                    rs.getObject("owner_user_id", UUID::class.java)
                )
            }
            val (addedBy, ownerUserId) = row
            if (ownerUserId != userId && addedBy != userId) return RemoveItemResult.Forbidden

            conn.prepareStatement(
                "DELETE FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.executeUpdate()
            }
        }
        return RemoveItemResult.Success
    }

    private fun isMember(plotId: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM plot_members WHERE plot_id = ? AND user_id = ? AND status = 'joined'"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                return stmt.executeQuery().next()
            }
        }
    }

    private inline fun <T> withTransaction(block: (Connection) -> T): T {
        val conn = dataSource.connection
        conn.autoCommit = false
        var committed = false
        try {
            val result = block(conn)
            conn.commit()
            committed = true
            return result
        } catch (e: Exception) {
            try { conn.rollback() } catch (_: Exception) {}
            throw e
        } finally {
            if (!committed) try { conn.rollback() } catch (_: Exception) {}
            conn.autoCommit = true
            conn.close()
        }
    }

    private fun ResultSet.toUploadRecord() = UploadRecord(
        id = getObject("id", UUID::class.java),
        storageKey = getString("storage_key"),
        mimeType = getString("mime_type"),
        fileSize = getLong("file_size"),
        uploadedAt = getTimestamp("uploaded_at").toInstant(),
        contentHash = getString("content_hash"),
        thumbnailKey = getString("thumbnail_key"),
        takenAt = getTimestamp("taken_at")?.toInstant(),
        latitude = getDouble("latitude").takeUnless { wasNull() },
        longitude = getDouble("longitude").takeUnless { wasNull() },
        altitude = getDouble("altitude").takeUnless { wasNull() },
        deviceMake = getString("device_make"),
        deviceModel = getString("device_model"),
        rotation = getInt("rotation"),
        tags = getArray("tags")?.let { arr -> (arr.array as? Array<*>)?.filterIsInstance<String>() } ?: emptyList(),
        compostedAt = getTimestamp("composted_at")?.toInstant(),
        exifProcessedAt = try { getTimestamp("exif_processed_at")?.toInstant() } catch (_: Exception) { null },
        lastViewedAt = try { getTimestamp("last_viewed_at")?.toInstant() } catch (_: Exception) { null },
        storageClass = try { getString("storage_class") ?: "public" } catch (_: Exception) { "public" },
        envelopeVersion = try { getObject("envelope_version") as? Int } catch (_: Exception) { null },
        wrappedDek = try { getBytes("wrapped_dek") } catch (_: Exception) { null },
        dekFormat = try { getString("dek_format") } catch (_: Exception) { null },
        encryptedMetadata = try { getBytes("encrypted_metadata") } catch (_: Exception) { null },
        encryptedMetadataFormat = try { getString("encrypted_metadata_format") } catch (_: Exception) { null },
        thumbnailStorageKey = try { getString("thumbnail_storage_key") } catch (_: Exception) { null },
        wrappedThumbnailDek = try { getBytes("wrapped_thumbnail_dek") } catch (_: Exception) { null },
        thumbnailDekFormat = try { getString("thumbnail_dek_format") } catch (_: Exception) { null },
        previewStorageKey = try { getString("preview_storage_key") } catch (_: Exception) { null },
        wrappedPreviewDek = try { getBytes("wrapped_preview_dek") } catch (_: Exception) { null },
        previewDekFormat = try { getString("preview_dek_format") } catch (_: Exception) { null },
        plainChunkSize = try { getObject("plain_chunk_size") as? Int } catch (_: Exception) { null },
        durationSeconds = try { getObject("duration_seconds") as? Int } catch (_: Exception) { null },
        sharedFromUploadId = try { getObject("shared_from_upload_id", UUID::class.java) } catch (_: Exception) { null },
        sharedFromUserId = try { getObject("shared_from_user_id", UUID::class.java) } catch (_: Exception) { null },
    )
}
