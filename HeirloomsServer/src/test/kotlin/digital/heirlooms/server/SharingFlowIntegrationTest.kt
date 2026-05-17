package digital.heirlooms.server

import digital.heirlooms.server.domain.auth.UserRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.service.auth.AuthService
import digital.heirlooms.server.storage.LocalFileStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val sharingMapper = ObjectMapper()
private val sharingUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val sharingStdEnc = Base64.getEncoder()

private fun sha256Sharing(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration test for the complete server-side sharing pipeline.
 *
 * Exercises: two real user accounts, friend connection, upload, direct share,
 * and retrieval — all in-process against a real Postgres database via Testcontainers.
 *
 * Uses LocalFileStore so bytes are actually stored and retrievable.
 */
class SharingFlowIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_sharing")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var authRepo: PostgresAuthRepository
        private lateinit var keyRepo: PostgresKeyRepository
        private lateinit var app: org.http4k.core.HttpHandler

        private val serverSecret = ByteArray(32) { it.toByte() }

        @BeforeAll
        @JvmStatic
        fun setup() {
            System.setProperty("ryuk.disabled", "true")
            System.setProperty(
                "docker.host",
                System.getenv("DOCKER_HOST")
                    ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
            )
            postgres.start()
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_sharing"
            dataSource = HikariDataSource(HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = "test"
                password = "test"
                maximumPoolSize = 5
            })
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            database = Database(dataSource)
            authRepo = PostgresAuthRepository(dataSource)
            keyRepo = PostgresKeyRepository(dataSource)

            val localFileStore = LocalFileStore(Files.createTempDirectory("heirlooms-test-"))
            val rawApp = buildApp(localFileStore, database, authSecret = serverSecret)
            app = sessionAuthFilter(authRepo).then(rawApp)
        }

        // ---- HTTP helpers -------------------------------------------------------

        fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path)
                .header("Content-Type", "application/json")
                .body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun get(path: String, token: String? = null): Response {
            val req = Request(GET, path)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun postBytes(path: String, body: ByteArray, contentType: String, token: String? = null): Response {
            val req = Request(POST, path)
                .header("Content-Type", contentType)
                .body(String(body, Charsets.ISO_8859_1))
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        // ---- Auth helpers -------------------------------------------------------

        /**
         * Registers a new user via invite and returns Pair(sessionToken, userId).
         */
        fun registerUser(username: String, inviteToken: String, deviceId: String = UUID.randomUUID().toString()): Pair<String, String> {
            val authKey = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val authVerifier = sha256Signing(authKey)
            val wrappedMasterKey = ByteArray(64) { 5 }
            val pubkey = ByteArray(65) { 6 }
            val body = """
                {
                  "invite_token": "$inviteToken",
                  "username": "$username",
                  "display_name": "${username.replaceFirstChar { it.uppercaseChar() }}",
                  "auth_salt": "${sharingUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${sharingUrlEnc.encodeToString(authVerifier)}",
                  "wrapped_master_key": "${sharingStdEnc.encodeToString(wrappedMasterKey)}",
                  "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
                  "pubkey_format": "p256-spki",
                  "pubkey": "${sharingStdEnc.encodeToString(pubkey)}",
                  "device_id": "$deviceId",
                  "device_label": "${username.replaceFirstChar { it.uppercaseChar() }}'s Phone",
                  "device_kind": "android"
                }
            """.trimIndent()
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "Expected 201 for register [$username]: ${resp.bodyString()}")
            val node = sharingMapper.readTree(resp.bodyString())
            val sessionToken = node.get("session_token").asText()
            val userId = node.get("user_id").asText()
            return Pair(sessionToken, userId)
        }

        /** Generates an invite token via the given session. */
        fun generateInvite(sessionToken: String): String {
            val resp = get("/api/auth/invites", sessionToken)
            assertEquals(OK, resp.status, "Expected 200 for invite: ${resp.bodyString()}")
            return sharingMapper.readTree(resp.bodyString()).get("token").asText()
        }

        /**
         * Creates a session for the founding user via setup-existing.
         * Resets the founding user's auth state first for test isolation.
         */
        fun setupInviterSession(deviceId: String = "inviter-sharing-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            val authVerifier = sha256Signing(authKey)
            val pubkey = ByteArray(65) { 7 }
            val wrappedMasterKey = ByteArray(64) { 8 }

            keyRepo.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(),
                    deviceId = deviceId,
                    deviceLabel = "Owner's Phone",
                    deviceKind = "android",
                    pubkeyFormat = "p256-spki",
                    pubkey = pubkey,
                    wrappedMasterKey = wrappedMasterKey,
                    wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
                    createdAt = Instant.now(),
                    lastUsedAt = Instant.now(),
                    retiredAt = null,
                ),
                userId = FOUNDING_USER_ID,
            )

            val body = """
                {
                  "username": "bret",
                  "device_id": "$deviceId",
                  "auth_salt": "${sharingUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${sharingUrlEnc.encodeToString(authVerifier)}"
                }
            """.trimIndent()
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return sharingMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun sha256Signing(b: ByteArray): ByteArray = sha256Sharing(b)
    }

    // -------------------------------------------------------------------------
    // Happy-path end-to-end sharing flow
    // -------------------------------------------------------------------------

    /**
     * Full sharing pipeline:
     *  1. Founding user session
     *  2. Register Alice and Bob (both friends of founding user)
     *  3. Alice generates an invite; Bob connects (Alice ↔ Bob are now friends)
     *  4. Alice uploads a file
     *  5. Alice shares to Bob
     *  6. Bob retrieves the shared file by bytes
     *  7. Bob's received-uploads list contains the shared item
     */
    @Test
    fun `sharingFlowEndToEnd`() {
        // Step 1 — founding session
        val foundingSession = setupInviterSession()

        // Step 2 — register Alice
        val invite1 = generateInvite(foundingSession)
        val aliceUsername = "alice_share_${UUID.randomUUID().toString().take(6)}"
        val (sessionA, _) = registerUser(aliceUsername, invite1)
        assertNotNull(sessionA)

        // Step 3 — register Bob
        val invite2 = generateInvite(foundingSession)
        val bobUsername = "bob_share_${UUID.randomUUID().toString().take(6)}"
        val (sessionB, userIdB) = registerUser(bobUsername, invite2)
        assertNotNull(sessionB)

        // Step 4 — Alice generates a friend invite; Bob connects (Alice ↔ Bob are now friends)
        val invite3 = generateInvite(sessionA)
        val connectResp = post("/api/auth/invites/$invite3/connect", "", sessionB)
        assertEquals(OK, connectResp.status, "Friend connect failed: ${connectResp.bodyString()}")

        // Step 5 — Alice uploads a binary file
        val fileBytes = "hello heirlooms".toByteArray()
        val uploadResp = postBytes("/api/content/upload", fileBytes, "application/octet-stream", sessionA)
        assertEquals(CREATED, uploadResp.status, "Upload failed: ${uploadResp.bodyString()}")
        val uploadId = sharingMapper.readTree(uploadResp.bodyString()).get("id").asText()
        assertNotNull(uploadId)

        // Step 6 — Alice shares to Bob with fake wrapped DEK
        val fakeWrappedDek = sharingStdEnc.encodeToString(ByteArray(32) { it.toByte() })
        val shareBody = """{"toUserId":"$userIdB","wrappedDek":"$fakeWrappedDek","dekFormat":"test-v1"}"""
        val shareResp = post("/api/content/uploads/$uploadId/share", shareBody, sessionA)
        assertEquals(CREATED, shareResp.status, "Share failed: ${shareResp.bodyString()}")
        val recipientUploadId = sharingMapper.readTree(shareResp.bodyString()).get("id").asText()
        assertNotNull(recipientUploadId)

        // Step 7 — Bob retrieves the shared file
        val fileResp = get("/api/content/uploads/$recipientUploadId/file", sessionB)
        assertEquals(OK, fileResp.status, "File retrieval failed: ${fileResp.bodyString()}")
        assertTrue(
            fileResp.body.payload.array().contentEquals(fileBytes),
            "Retrieved bytes should match uploaded bytes"
        )

        // Step 8 — Bob's received list contains at least one item
        val receivedResp = get("/api/content/uploads?is_received=true", sessionB)
        assertEquals(OK, receivedResp.status, "Received list failed: ${receivedResp.bodyString()}")
        val items = sharingMapper.readTree(receivedResp.bodyString()).get("items")
        assertNotNull(items)
        assertTrue(items.size() >= 1, "Expected at least one received upload; got ${items.size()}")
    }

    // -------------------------------------------------------------------------
    // Negative test 1: B cannot access A's original upload
    // -------------------------------------------------------------------------

    @Test
    fun `non-recipient cannot access uploader original file`() {
        val foundingSession = setupInviterSession()

        val invite1 = generateInvite(foundingSession)
        val aliceUsername = "alice_neg1_${UUID.randomUUID().toString().take(6)}"
        val (sessionA, _) = registerUser(aliceUsername, invite1)

        val invite2 = generateInvite(foundingSession)
        val bobUsername = "bob_neg1_${UUID.randomUUID().toString().take(6)}"
        val (sessionB, userIdB) = registerUser(bobUsername, invite2)

        // Make Alice and Bob friends
        val invite3 = generateInvite(sessionA)
        post("/api/auth/invites/$invite3/connect", "", sessionB)

        // Alice uploads
        val fileBytes = "secret for alice".toByteArray()
        val uploadResp = postBytes("/api/content/upload", fileBytes, "application/octet-stream", sessionA)
        assertEquals(CREATED, uploadResp.status)
        val uploadId = sharingMapper.readTree(uploadResp.bodyString()).get("id").asText()

        // Alice shares to Bob — creates recipientUploadId
        val fakeWrappedDek = sharingStdEnc.encodeToString(ByteArray(32) { 0x11 })
        val shareBody = """{"toUserId":"$userIdB","wrappedDek":"$fakeWrappedDek","dekFormat":"test-v1"}"""
        val shareResp = post("/api/content/uploads/$uploadId/share", shareBody, sessionA)
        assertEquals(CREATED, shareResp.status)

        // Bob CANNOT access Alice's ORIGINAL upload (only the shared recipient copy)
        val fileResp = get("/api/content/uploads/$uploadId/file", sessionB)
        assertEquals(NOT_FOUND, fileResp.status, "Bob should not be able to access Alice's original upload")
    }

    // -------------------------------------------------------------------------
    // Negative test 2: Share requires friendship
    // -------------------------------------------------------------------------

    @Test
    fun `share to non-friend returns 403`() {
        val foundingSession = setupInviterSession()

        val invite1 = generateInvite(foundingSession)
        val aliceUsername = "alice_neg2_${UUID.randomUUID().toString().take(6)}"
        val (sessionA, _) = registerUser(aliceUsername, invite1)

        val invite2 = generateInvite(foundingSession)
        val charlieUsername = "charlie_neg2_${UUID.randomUUID().toString().take(6)}"
        val (_, userIdC) = registerUser(charlieUsername, invite2)
        // NOTE: Alice and Charlie are NOT friends — they were each invited by founding user
        // but did not connect with each other

        // Alice uploads
        val fileBytes = "alice file".toByteArray()
        val uploadResp = postBytes("/api/content/upload", fileBytes, "application/octet-stream", sessionA)
        assertEquals(CREATED, uploadResp.status)
        val uploadId = sharingMapper.readTree(uploadResp.bodyString()).get("id").asText()

        // Alice tries to share to Charlie (not a friend) — should return 403
        val fakeWrappedDek = sharingStdEnc.encodeToString(ByteArray(32) { 0x22 })
        val shareBody = """{"toUserId":"$userIdC","wrappedDek":"$fakeWrappedDek","dekFormat":"test-v1"}"""
        val shareResp = post("/api/content/uploads/$uploadId/share", shareBody, sessionA)
        assertEquals(FORBIDDEN, shareResp.status, "Expected 403 when sharing to a non-friend: ${shareResp.bodyString()}")
    }

    // -------------------------------------------------------------------------
    // Negative test 3: Non-member cannot retrieve a shared file
    // -------------------------------------------------------------------------

    @Test
    fun `non-member cannot retrieve shared file`() {
        val foundingSession = setupInviterSession()

        val invite1 = generateInvite(foundingSession)
        val aliceUsername = "alice_neg3_${UUID.randomUUID().toString().take(6)}"
        val (sessionA, _) = registerUser(aliceUsername, invite1)

        val invite2 = generateInvite(foundingSession)
        val bobUsername = "bob_neg3_${UUID.randomUUID().toString().take(6)}"
        val (sessionB, userIdB) = registerUser(bobUsername, invite2)

        // Make Alice and Bob friends
        val invite3 = generateInvite(sessionA)
        post("/api/auth/invites/$invite3/connect", "", sessionB)

        // Register an unrelated user D
        val invite4 = generateInvite(foundingSession)
        val daveUsername = "dave_neg3_${UUID.randomUUID().toString().take(6)}"
        val (sessionD, _) = registerUser(daveUsername, invite4)

        // Alice uploads and shares to Bob
        val fileBytes = "alice to bob only".toByteArray()
        val uploadResp = postBytes("/api/content/upload", fileBytes, "application/octet-stream", sessionA)
        assertEquals(CREATED, uploadResp.status)
        val uploadId = sharingMapper.readTree(uploadResp.bodyString()).get("id").asText()

        val fakeWrappedDek = sharingStdEnc.encodeToString(ByteArray(32) { 0x33 })
        val shareBody = """{"toUserId":"$userIdB","wrappedDek":"$fakeWrappedDek","dekFormat":"test-v1"}"""
        val shareResp = post("/api/content/uploads/$uploadId/share", shareBody, sessionA)
        assertEquals(CREATED, shareResp.status)
        val recipientUploadId = sharingMapper.readTree(shareResp.bodyString()).get("id").asText()

        // Dave (non-member) cannot retrieve the shared file
        val fileResp = get("/api/content/uploads/$recipientUploadId/file", sessionD)
        assertEquals(NOT_FOUND, fileResp.status, "Dave should not be able to retrieve Bob's shared file")
    }

    // -------------------------------------------------------------------------
    // Unit test: sha256 auth contract invariant
    // -------------------------------------------------------------------------

    /**
     * Validates the sha256 auth contract directly against AuthService with mocked repos.
     * This documents the login invariant the integration tests rely on:
     *   login succeeds when sha256(authKey) == stored authVerifier.
     */
    @Test
    fun `login succeeds when authKey sha256 matches stored authVerifier`() {
        val mockAuthRepo = mockk<AuthRepository>(relaxed = true)
        val mockKeyRepo = mockk<KeyRepository>(relaxed = true)
        val mockSocialRepo = mockk<SocialRepository>(relaxed = true)
        val mockPlotRepo = mockk<PlotRepository>(relaxed = true)

        val knownKey = ByteArray(32) { (it + 7).toByte() }
        val userId = UUID.randomUUID()
        val storedVerifier = sha256Sharing(knownKey)

        every { mockAuthRepo.findUserByUsername("testuser") } returns UserRecord(
            id = userId,
            username = "testuser",
            displayName = "Test User",
            authVerifier = storedVerifier,
            authSalt = ByteArray(16),
            createdAt = Instant.now(),
            requireBiometric = false,
        )
        every { mockAuthRepo.createSession(any(), any(), any()) } returns
            digital.heirlooms.server.domain.auth.UserSessionRecord(
                id = UUID.randomUUID(),
                userId = userId,
                tokenHash = ByteArray(32),
                deviceKind = "android",
                createdAt = Instant.now(),
                lastUsedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(86400),
            )

        val svc = AuthService(
            mockAuthRepo, mockKeyRepo, mockSocialRepo, mockPlotRepo,
            serverSecret
        )

        val result = svc.login("testuser", knownKey)

        assertTrue(
            result is AuthService.LoginResult.Success,
            "Expected LoginResult.Success but got $result"
        )
        assertEquals(userId, (result as AuthService.LoginResult.Success).userId)
    }

    /**
     * Validates that login fails when the authKey does NOT match the stored authVerifier.
     */
    @Test
    fun `login fails when authKey sha256 does not match stored authVerifier`() {
        val mockAuthRepo = mockk<AuthRepository>(relaxed = true)
        val mockKeyRepo = mockk<KeyRepository>(relaxed = true)
        val mockSocialRepo = mockk<SocialRepository>(relaxed = true)
        val mockPlotRepo = mockk<PlotRepository>(relaxed = true)

        val correctKey = ByteArray(32) { (it + 7).toByte() }
        val wrongKey = ByteArray(32) { (it + 42).toByte() }
        val userId = UUID.randomUUID()
        val storedVerifier = sha256Sharing(correctKey)

        every { mockAuthRepo.findUserByUsername("testuser") } returns UserRecord(
            id = userId,
            username = "testuser",
            displayName = "Test User",
            authVerifier = storedVerifier,
            authSalt = ByteArray(16),
            createdAt = Instant.now(),
            requireBiometric = false,
        )

        val svc = AuthService(
            mockAuthRepo, mockKeyRepo, mockSocialRepo, mockPlotRepo,
            serverSecret
        )

        val result = svc.login("testuser", wrongKey)

        assertTrue(
            result is AuthService.LoginResult.InvalidCredentials,
            "Expected InvalidCredentials but got $result"
        )
    }
}
