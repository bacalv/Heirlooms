package digital.heirlooms.tools.apiclient

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.system.exitProcess

/**
 * Entry point for the Heirlooms API client CLI.
 *
 * Runs a full capsule lifecycle round-trip against the configured environment:
 *   1. Authenticate
 *   2. Upload a test file
 *   3. Create a capsule (with the upload)
 *   4. Add upload to capsule (demonstrates the PATCH path)
 *   5. Seal the capsule
 *   6. List capsules
 *   7. Retrieve the sealed capsule by ID
 *
 * Exits 0 on full success, 1 on any failure.
 */
fun main(args: Array<String>) {
    val config = try {
        ClientConfig.load(args)
    } catch (e: IllegalStateException) {
        System.err.println("ERROR: ${e.message}")
        exitProcess(1)
    }

    println("=" .repeat(60))
    println("[api-client] Heirlooms API Client — Phase 1 Capsule Lifecycle")
    println("[api-client] base_url=${config.baseUrl}")
    println("[api-client] username=${config.username}")
    println("=".repeat(60))

    val client = HeirloomsClient(config)

    try {
        // ---- Step 1: Authenticate -----------------------------------------------
        println("\n[api-client] --- Step 1: Authenticate ---")
        client.authenticate()

        // ---- Step 2: Upload a test file -----------------------------------------
        println("\n[api-client] --- Step 2: Upload a file ---")
        val testBytes = generateTestFileBytes()
        println("[api-client] [uploadFile] generated ${testBytes.size}-byte test payload")
        val uploadId = client.uploadFile(testBytes, "application/octet-stream")

        // ---- Step 3: Create a capsule -------------------------------------------
        println("\n[api-client] --- Step 3: Create a capsule ---")
        val unlockAt = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1)
            .toString()  // ISO-8601 with timezone
        val capsuleId = client.createCapsule(
            recipients = listOf("Phase 1 Test Recipient"),
            uploadIds = listOf(uploadId),
            message = "Hello from the Heirlooms API client — Phase 1 round-trip test.",
            unlockAtIso = unlockAt,
        )

        // ---- Step 4: Add upload to capsule (PATCH path) -------------------------
        println("\n[api-client] --- Step 4: Add upload to capsule ---")
        // This demonstrates the PATCH path; the upload is already in the capsule
        // from Step 3 — addUploadToCapsule is idempotent and includes it again.
        client.addUploadToCapsule(capsuleId, uploadId)

        // ---- Step 5: Seal the capsule -------------------------------------------
        println("\n[api-client] --- Step 5: Seal the capsule ---")
        val sealedDetail = client.sealCapsule(capsuleId)
        val sealedState = sealedDetail.get("state")?.asText()
        require(sealedState == "sealed") {
            "Expected state=sealed after seal, got state=$sealedState"
        }

        // ---- Step 6: List capsules ----------------------------------------------
        println("\n[api-client] --- Step 6: List capsules ---")
        val capsuleList = client.listCapsules(state = "open,sealed")
        val capsules = capsuleList.get("capsules")
        val listedIds = capsules?.map { it.get("id")?.asText() } ?: emptyList()
        val found = listedIds.contains(capsuleId)
        println("[api-client] [listCapsules] sealed capsule in list: $found (capsule_id=$capsuleId)")
        require(found) { "Sealed capsule $capsuleId not found in list response" }

        // ---- Step 7: Retrieve the capsule ---------------------------------------
        println("\n[api-client] --- Step 7: Retrieve the capsule ---")
        val retrieved = client.getCapsule(capsuleId)
        val retrievedState = retrieved.get("state")?.asText()
        val retrievedUploads = retrieved.get("uploads")?.size() ?: 0
        println("[api-client] [getCapsule] state=$retrievedState uploads=$retrievedUploads")
        require(retrievedState == "sealed") {
            "Expected retrieved capsule state=sealed, got state=$retrievedState"
        }
        require(retrievedUploads >= 1) {
            "Expected at least 1 upload in retrieved capsule, got $retrievedUploads"
        }

        // ---- Summary ------------------------------------------------------------
        println("\n" + "=".repeat(60))
        println("[api-client] SUCCESS — full capsule lifecycle round-trip complete")
        println("[api-client] capsule_id=$capsuleId  state=$retrievedState  uploads=$retrievedUploads")
        println("=".repeat(60))
        exitProcess(0)

    } catch (e: Exception) {
        System.err.println("\n[api-client] FAILURE: ${e.message}")
        e.printStackTrace(System.err)
        exitProcess(1)
    }
}

/**
 * Generates a small unique test file payload.
 * Uses random bytes so each run produces a different content hash,
 * avoiding duplicate-upload conflicts on the server.
 */
private fun generateTestFileBytes(): ByteArray {
    val prefix = "Heirlooms API client Phase 1 test — ".toByteArray()
    val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return prefix + random
}
