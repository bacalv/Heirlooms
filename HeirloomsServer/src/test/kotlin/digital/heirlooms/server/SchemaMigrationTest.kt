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

    // ---- V25 flows/staging canary tests ------------------------------------

    @Test
    fun `V25 flows table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'flows'
              AND column_name IN ('id','user_id','name','criteria','target_plot_id','requires_staging','created_at','updated_at')
        """.trimIndent())
        assertEquals(8, n, "flows table should have 8 columns after V25")
    }

    @Test
    fun `V25 plot_items table exists with expected columns`() {
        val n = count("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'plot_items'
              AND column_name IN ('id','plot_id','upload_id','added_by','source_flow_id','added_at')
        """.trimIndent())
        assertEquals(6, n, "plot_items should have expected columns after V25")
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

    @Test
    fun `V25 deleting a flow sets source_flow_id to NULL on plot_items`() {
        val userId = FOUNDING_USER_UUID
        // Create a collection plot (criteria IS NULL, use show_in_garden/visibility defaults)
        val plotId = java.util.UUID.randomUUID()
        exec("INSERT INTO plots (id, owner_user_id, name, show_in_garden, visibility) VALUES ('$plotId', '$userId', 'v25a-test-plot', true, 'private')")

        // Create a flow targeting that plot
        val flowId = java.util.UUID.randomUUID()
        exec("""INSERT INTO flows (id, user_id, name, criteria, target_plot_id, requires_staging)
                VALUES ('$flowId', '$userId', 'v25a-flow', '{"type":"composted"}'::jsonb, '$plotId', true)""")

        // Create a plot_items row referencing the flow
        val uploadId = java.util.UUID.randomUUID()
        exec("""INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, user_id)
                VALUES ('$uploadId', 'v25a/$uploadId.jpg', 'image/jpeg', 1024, 'public', '$userId')""")
        exec("""INSERT INTO plot_items (plot_id, upload_id, added_by, source_flow_id)
                VALUES ('$plotId', '$uploadId', '$userId', '$flowId')""")

        // Deleting the flow should SET NULL the source_flow_id
        exec("DELETE FROM flows WHERE id = '$flowId'")
        val n = count("SELECT COUNT(*) FROM plot_items WHERE source_flow_id IS NULL AND upload_id = '$uploadId'")
        assertEquals(1, n, "source_flow_id should be NULL after flow deletion")

        // Cleanup
        exec("DELETE FROM plot_items WHERE upload_id = '$uploadId'")
        exec("DELETE FROM uploads WHERE id = '$uploadId'")
        exec("DELETE FROM plots WHERE id = '$plotId'")
    }
}
