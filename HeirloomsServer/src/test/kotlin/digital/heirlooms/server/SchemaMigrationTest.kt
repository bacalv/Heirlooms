package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.sql.SQLException
import java.sql.Statement
import java.util.UUID
import javax.sql.DataSource

class SchemaMigrationTest {

    companion object {

        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_test")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource

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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_test"
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
        }

        private fun exec(sql: String) {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt: Statement -> stmt.execute(sql) }
            }
        }

        private fun count(sql: String): Int {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(sql)
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    // ---- 1. After V16 rename migration, all existing uploads are public ----

    @Test
    fun `all uploads after migration have storage_class public`() {
        val n = count("SELECT COUNT(*) FROM uploads WHERE storage_class != 'public'")
        assertEquals(0, n, "expected all uploads to be public after V16 rename migration")
    }

    private val FOUNDING_USER_UUID = "00000000-0000-0000-0000-000000000001"

    // ---- 2. Public storage class can be inserted directly at the DB level ----
    // The architecture admits 'public'; the API rejects it (tested in E2).
    // This test proves the schema itself does not block it.

    @Test
    fun `public storage class row can be inserted at the DB level`() {
        val id = UUID.randomUUID()
        exec("""
            INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, user_id)
            VALUES ('$id', 'test/public-$id.jpg', 'image/jpeg', 1024, 'public', '$FOUNDING_USER_UUID')
        """.trimIndent())
        val n = count("SELECT COUNT(*) FROM uploads WHERE id = '$id' AND storage_class = 'public'")
        assertEquals(1, n)
        exec("DELETE FROM uploads WHERE id = '$id'")
    }

    // ---- 3. Encrypted row with NULL wrapped_dek is rejected by the constraint ----

    @Test
    fun `encrypted storage class with null wrapped_dek violates constraint`() {
        val id = UUID.randomUUID()
        assertThrows<SQLException> {
            exec("""
                INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, wrapped_dek, dek_format, user_id)
                VALUES ('$id', 'test/enc-$id.jpg', 'image/jpeg', 1024, 'encrypted', NULL, NULL, '$FOUNDING_USER_UUID')
            """.trimIndent())
        }
    }

    // ---- 4. Public row with non-null wrapped_dek is rejected by the constraint ----

    @Test
    fun `public storage class with non-null wrapped_dek violates constraint`() {
        val id = UUID.randomUUID()
        assertThrows<SQLException> {
            exec("""
                INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, wrapped_dek, dek_format, user_id)
                VALUES ('$id', 'test/leg-$id.jpg', 'image/jpeg', 1024, 'public',
                        decode('deadbeef', 'hex'), 'aes256gcm-v1', '$FOUNDING_USER_UUID')
            """.trimIndent())
        }
    }

    // ---- M8 V20/V21 schema canary tests ----

    @Test
    fun `V20 V21 users table exists with founding user row`() {
        val n = count("SELECT COUNT(*) FROM users WHERE id = '$FOUNDING_USER_UUID'")
        assertEquals(1, n, "Founding user row should exist after V21 backfill")
    }

    @Test
    fun `V21 all uploads have founding user_id after backfill`() {
        val n = count("SELECT COUNT(*) FROM uploads WHERE user_id IS NULL")
        assertEquals(0, n, "No upload should have NULL user_id after V21 backfill")
    }

    @Test
    fun `V21 all capsules have founding user_id after backfill`() {
        val n = count("SELECT COUNT(*) FROM capsules WHERE user_id IS NULL")
        assertEquals(0, n, "No capsule should have NULL user_id after V21 backfill")
    }

    @Test
    fun `V21 all plots have owner_user_id after backfill`() {
        val n = count("SELECT COUNT(*) FROM plots WHERE owner_user_id IS NULL")
        assertEquals(0, n, "No plot should have NULL owner_user_id after V21 backfill")
    }

    @Test
    fun `V21 upload with null user_id violates not null constraint`() {
        val id = UUID.randomUUID()
        assertThrows<SQLException> {
            exec("""
                INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, user_id)
                VALUES ('$id', 'test/nfk-$id.jpg', 'image/jpeg', 100, 'public', NULL)
            """.trimIndent())
        }
    }

    @Test
    fun `V21 recovery_passphrase has no id column and user_id is pk`() {
        assertThrows<SQLException> {
            exec("SELECT id FROM recovery_passphrase LIMIT 1")
        }
    }

    @Test
    fun `V21 two recovery_passphrase rows for same user_id violates pk`() {
        assertThrows<SQLException> {
            val userId2 = UUID.randomUUID()
            exec("INSERT INTO users (id, username, display_name) VALUES ('$userId2', 'testpk2', 'Test')")
            exec("""
                INSERT INTO recovery_passphrase (user_id, wrapped_master_key, wrap_format, argon2_params, salt)
                VALUES ('$userId2', decode('aabb', 'hex'), 'test', '{}', decode('ccdd', 'hex'))
            """.trimIndent())
            exec("""
                INSERT INTO recovery_passphrase (user_id, wrapped_master_key, wrap_format, argon2_params, salt)
                VALUES ('$userId2', decode('eeff', 'hex'), 'test', '{}', decode('1122', 'hex'))
            """.trimIndent())
        }
    }

    @Test
    fun `V21 pending_device_links has user_id and web_session_id columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'pending_device_links'
              AND column_name IN ('user_id', 'web_session_id', 'raw_session_token')
        """.trimIndent())
        assertEquals(3, n, "pending_device_links should have user_id, web_session_id, raw_session_token columns")
    }

    // ---- V24 predicate/criteria system canary tests -------------------------

    @Test
    fun `V24 plots has criteria show_in_garden and visibility columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'plots'
              AND column_name IN ('criteria', 'show_in_garden', 'visibility')
        """.trimIndent())
        assertEquals(3, n, "plots should have criteria, show_in_garden, visibility after V24")
    }

    @Test
    fun `V24 plot_tag_criteria table no longer exists`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'plot_tag_criteria'
        """.trimIndent())
        assertEquals(0, n, "plot_tag_criteria should be dropped after V24")
    }

    @Test
    fun `V24 system just_arrived plot has criteria set`() {
        val n = count("""
            SELECT COUNT(*) FROM plots
            WHERE is_system_defined = TRUE
              AND name = '__just_arrived__'
              AND criteria = '{"type": "just_arrived"}'::jsonb
        """.trimIndent())
        assertEquals(1, n, "system __just_arrived__ plot should have criteria after V24")
    }

    @Test
    fun `V24 all plots have show_in_garden true by default`() {
        val n = count("SELECT COUNT(*) FROM plots WHERE show_in_garden = FALSE")
        assertEquals(0, n, "all plots should have show_in_garden=true after V24 (no user plots in test DB)")
    }

    @Test
    fun `V24 all plots have visibility private by default`() {
        val n = count("SELECT COUNT(*) FROM plots WHERE visibility != 'private'")
        assertEquals(0, n, "all plots should have visibility='private' after V24")
    }

    // ---- V25 flows/staging canary tests (updated for V30 rename) -----------

    @Test
    fun `V25+V30 trellises table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'trellises'
              AND column_name IN ('id','user_id','name','criteria','target_plot_id','requires_staging','created_at','updated_at')
        """.trimIndent())
        assertEquals(8, n, "trellises table should have 8 columns after V25+V30")
    }

    @Test
    fun `V25+V30 plot_items table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'plot_items'
              AND column_name IN ('id','plot_id','upload_id','added_by','source_trellis_id','added_at')
        """.trimIndent())
        assertEquals(6, n, "plot_items should have expected columns after V25+V30")
    }

    @Test
    fun `V25 plot_staging_decisions table exists`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'plot_staging_decisions'
        """.trimIndent())
        assertEquals(1, n, "plot_staging_decisions should exist after V25")
    }

    // ---- V26 plot_members / plot_invites canary tests ----------------------

    @Test
    fun `V26 plot_members table exists with correct columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'plot_members'
              AND column_name IN ('plot_id','user_id','role','wrapped_plot_key','plot_key_format','joined_at')
        """.trimIndent())
        assertEquals(6, n, "plot_members should have 6 expected columns after V26")
    }

    @Test
    fun `V26 plot_invites table exists with correct columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'plot_invites'
              AND column_name IN ('id','plot_id','created_by','token','recipient_user_id','recipient_pubkey','used_by','used_at','expires_at','created_at')
        """.trimIndent())
        assertEquals(10, n, "plot_invites should have 10 expected columns after V26")
    }

    @Test
    fun `V26 plot_members cascade delete removes members when plot deleted`() {
        val userId = FOUNDING_USER_UUID
        val plotId = java.util.UUID.randomUUID()
        exec("INSERT INTO plots (id, owner_user_id, name, show_in_garden, visibility) VALUES ('$plotId', '$userId', 'v25b-cascade', true, 'shared')")
        exec("INSERT INTO plot_members (plot_id, user_id, role) VALUES ('$plotId', '$userId', 'owner')")
        exec("DELETE FROM plots WHERE id = '$plotId'")
        val n = count("SELECT COUNT(*) FROM plot_members WHERE plot_id = '$plotId'")
        assertEquals(0, n, "plot_members should cascade-delete when plot is deleted")
    }

    // ---- V31 BUG-018: shared-plot trellises must have requires_staging = true ----
    // Flyway has already run the migration. We verify the fix SQL logic is correct by
    // inserting a legacy-style row (requires_staging=false on a shared plot), running the
    // V31 UPDATE statement manually, and confirming it flips to true.

    @Test
    fun `V31 migration SQL fixes shared-plot trellis with requires_staging false`() {
        val userId = FOUNDING_USER_UUID
        val plotId = java.util.UUID.randomUUID()
        exec("INSERT INTO plots (id, owner_user_id, name, show_in_garden, visibility) VALUES ('$plotId', '$userId', 'v31-shared-plot', true, 'shared')")

        val trellisId = java.util.UUID.randomUUID()
        exec("""INSERT INTO trellises (id, user_id, name, criteria, target_plot_id, requires_staging)
                VALUES ('$trellisId', '$userId', 'v31-trellis', '{"type":"tag","tag":"family"}'::jsonb, '$plotId', false)""")

        // Simulate the V31 migration SQL (idempotent — safe to re-run)
        exec("""
            UPDATE trellises t
            SET requires_staging = true
            FROM plots p
            WHERE t.target_plot_id = p.id
              AND p.visibility = 'shared'
              AND t.requires_staging = false
              AND t.id = '$trellisId'
        """.trimIndent())

        val n = count("SELECT COUNT(*) FROM trellises WHERE id = '$trellisId' AND requires_staging = true")
        assertEquals(1, n, "V31 migration SQL must set requires_staging=true for shared-plot trellises")

        // Cleanup
        exec("DELETE FROM trellises WHERE id = '$trellisId'")
        exec("DELETE FROM plots WHERE id = '$plotId'")
    }

    @Test
    fun `V25+V30 deleting a trellis sets source_trellis_id to NULL on plot_items`() {
        val userId = FOUNDING_USER_UUID
        // Create a collection plot (criteria IS NULL, use show_in_garden/visibility defaults)
        val plotId = java.util.UUID.randomUUID()
        exec("INSERT INTO plots (id, owner_user_id, name, show_in_garden, visibility) VALUES ('$plotId', '$userId', 'v25a-test-plot', true, 'private')")

        // Create a trellis targeting that plot
        val trellisId = java.util.UUID.randomUUID()
        exec("""INSERT INTO trellises (id, user_id, name, criteria, target_plot_id, requires_staging)
                VALUES ('$trellisId', '$userId', 'v25a-trellis', '{"type":"composted"}'::jsonb, '$plotId', true)""")

        // Create a plot_items row referencing the trellis
        val uploadId = java.util.UUID.randomUUID()
        exec("""INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, user_id)
                VALUES ('$uploadId', 'v25a/$uploadId.jpg', 'image/jpeg', 1024, 'public', '$userId')""")
        exec("""INSERT INTO plot_items (plot_id, upload_id, added_by, source_trellis_id)
                VALUES ('$plotId', '$uploadId', '$userId', '$trellisId')""")

        // Deleting the trellis should SET NULL the source_trellis_id
        exec("DELETE FROM trellises WHERE id = '$trellisId'")
        val n = count("SELECT COUNT(*) FROM plot_items WHERE source_trellis_id IS NULL AND upload_id = '$uploadId'")
        assertEquals(1, n, "source_trellis_id should be NULL after trellis deletion")

        // Cleanup
        exec("DELETE FROM plot_items WHERE upload_id = '$uploadId'")
        exec("DELETE FROM uploads WHERE id = '$uploadId'")
        exec("DELETE FROM plots WHERE id = '$plotId'")
    }

    // ---- V33 M11 connections schema canary tests ----------------------------

    @Test
    fun `V33 connections table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'connections'
              AND column_name IN ('id','owner_user_id','contact_user_id','display_name','email',
                                  'sharing_pubkey','roles','created_at','updated_at')
        """.trimIndent())
        assertEquals(9, n, "connections table should have all 9 expected columns after V33")
    }

    @Test
    fun `V33 executor_nominations table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'executor_nominations'
              AND column_name IN ('id','owner_user_id','connection_id','status',
                                  'offered_at','responded_at','revoked_at','message')
        """.trimIndent())
        assertEquals(8, n, "executor_nominations table should have all 8 expected columns after V33")
    }

    @Test
    fun `V33 capsule_recipients has connection_id column`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'capsule_recipients'
              AND column_name = 'connection_id'
        """.trimIndent())
        assertEquals(1, n, "capsule_recipients should have connection_id column after V33")
    }

    @Test
    fun `V33 backfill produces one connection per direction for each friendship`() {
        // Seed two users and a friendship, then verify two connections are produced.
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        // friendships.user_id_1 < user_id_2 (UUID ordering)
        val (uid1, uid2) = if (userA.toString() < userB.toString()) Pair(userA, userB) else Pair(userB, userA)

        exec("INSERT INTO users (id, username, display_name) VALUES ('$uid1', 'conn-test-a-${uid1}', 'User A')")
        exec("INSERT INTO users (id, username, display_name) VALUES ('$uid2', 'conn-test-b-${uid2}', 'User B')")
        exec("INSERT INTO friendships (user_id_1, user_id_2) VALUES ('$uid1', '$uid2')")

        // Run the V33 backfill SQL again for just this friendship pair (idempotent via ON CONFLICT DO NOTHING).
        exec("""
            INSERT INTO connections (id, owner_user_id, contact_user_id, display_name, sharing_pubkey, roles, created_at)
            SELECT
                gen_random_uuid(),
                u1.id AS owner_user_id,
                u2.id AS contact_user_id,
                u2.display_name,
                ask.pubkey,
                ARRAY['recipient'],
                f.created_at
            FROM friendships f
            JOIN users u1 ON u1.id = f.user_id_1
            JOIN users u2 ON u2.id = f.user_id_2
            LEFT JOIN account_sharing_keys ask ON ask.user_id = u2.id
            WHERE f.user_id_1 = '$uid1' AND f.user_id_2 = '$uid2'
            UNION ALL
            SELECT
                gen_random_uuid(),
                u2.id AS owner_user_id,
                u1.id AS contact_user_id,
                u1.display_name,
                ask.pubkey,
                ARRAY['recipient'],
                f.created_at
            FROM friendships f
            JOIN users u1 ON u1.id = f.user_id_1
            JOIN users u2 ON u2.id = f.user_id_2
            LEFT JOIN account_sharing_keys ask ON ask.user_id = u1.id
            WHERE f.user_id_1 = '$uid1' AND f.user_id_2 = '$uid2'
            ON CONFLICT DO NOTHING
        """.trimIndent())

        val n = count("""
            SELECT COUNT(*) FROM connections
            WHERE (owner_user_id = '$uid1' AND contact_user_id = '$uid2')
               OR (owner_user_id = '$uid2' AND contact_user_id = '$uid1')
        """.trimIndent())
        assertEquals(2, n, "backfill should produce exactly one connection per direction for each friendship pair")

        // Cleanup
        exec("DELETE FROM connections WHERE owner_user_id = '$uid1' OR owner_user_id = '$uid2'")
        exec("DELETE FROM friendships WHERE user_id_1 = '$uid1' AND user_id_2 = '$uid2'")
        exec("DELETE FROM users WHERE id = '$uid1' OR id = '$uid2'")
    }

    @Test
    fun `V33 connections check constraint rejects row with no contact_user_id and no email`() {
        val userId = FOUNDING_USER_UUID
        assertThrows<SQLException> {
            exec("""
                INSERT INTO connections (id, owner_user_id, contact_user_id, display_name, email)
                VALUES (gen_random_uuid(), '$userId', NULL, 'No Contact', NULL)
            """.trimIndent())
        }
    }

    @Test
    fun `V33 executor_nominations status check constraint rejects invalid status`() {
        val userId = FOUNDING_USER_UUID
        // Create a minimal connection to reference
        val connId = UUID.randomUUID()
        exec("""
            INSERT INTO connections (id, owner_user_id, contact_user_id, display_name)
            VALUES ('$connId', '$userId', '$userId', 'Self Connection')
        """.trimIndent())
        assertThrows<SQLException> {
            exec("""
                INSERT INTO executor_nominations (owner_user_id, connection_id, status)
                VALUES ('$userId', '$connId', 'invalid_status')
            """.trimIndent())
        }
        exec("DELETE FROM connections WHERE id = '$connId'")
    }

    // ---- V34 M11 capsule crypto schema canary tests -------------------------

    @Test
    fun `V34 capsules has all nine new crypto columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'capsules'
              AND column_name IN ('wrapped_capsule_key','capsule_key_format','tlock_round',
                                  'tlock_chain_id','tlock_wrapped_key','tlock_dek_tlock',
                                  'tlock_key_digest','shamir_threshold','shamir_total_shares')
        """.trimIndent())
        assertEquals(9, n, "capsules table should have all 9 new crypto columns after V34")
    }

    @Test
    fun `V34 capsules has sealed_at column`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'capsules'
              AND column_name = 'sealed_at'
        """.trimIndent())
        assertEquals(1, n, "capsules table should have sealed_at column after V34")
    }

    @Test
    fun `V34 capsule_recipient_keys table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'capsule_recipient_keys'
              AND column_name IN ('capsule_id','connection_id','wrapped_capsule_key',
                                  'capsule_key_format','wrapped_blinding_mask','created_at')
        """.trimIndent())
        assertEquals(6, n, "capsule_recipient_keys should have all 6 expected columns after V34")
    }

    @Test
    fun `V34 executor_shares table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'executor_shares'
              AND column_name IN ('id','capsule_id','nomination_id','share_index',
                                  'wrapped_share','share_format','distributed_at')
        """.trimIndent())
        assertEquals(7, n, "executor_shares should have all 7 expected columns after V34")
    }

    @Test
    fun `V34 capsule_key_format check constraint rejects unknown format`() {
        val userId = FOUNDING_USER_UUID
        val capsuleId = UUID.randomUUID()
        exec("""
            INSERT INTO capsules (id, created_at, updated_at, created_by_user, shape, state, unlock_at, user_id)
            VALUES ('$capsuleId', NOW(), NOW(), 'test', 'open', 'open', NOW() + INTERVAL '1 year', '$userId')
        """.trimIndent())
        assertThrows<SQLException> {
            exec("""
                UPDATE capsules SET capsule_key_format = 'unknown-format-v99' WHERE id = '$capsuleId'
            """.trimIndent())
        }
        exec("DELETE FROM capsules WHERE id = '$capsuleId'")
    }

    @Test
    fun `V34 shamir threshold lte total check constraint rejects threshold greater than total`() {
        val userId = FOUNDING_USER_UUID
        val capsuleId = UUID.randomUUID()
        exec("""
            INSERT INTO capsules (id, created_at, updated_at, created_by_user, shape, state, unlock_at, user_id)
            VALUES ('$capsuleId', NOW(), NOW(), 'test', 'open', 'open', NOW() + INTERVAL '1 year', '$userId')
        """.trimIndent())
        assertThrows<SQLException> {
            exec("""
                UPDATE capsules SET shamir_threshold = 5, shamir_total_shares = 3 WHERE id = '$capsuleId'
            """.trimIndent())
        }
        exec("DELETE FROM capsules WHERE id = '$capsuleId'")
    }
}
