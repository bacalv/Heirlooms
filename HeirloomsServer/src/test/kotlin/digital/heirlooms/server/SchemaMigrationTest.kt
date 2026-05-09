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

    // ---- 1. After migration, all existing uploads are legacy_plaintext ----

    @Test
    fun `all uploads after migration have storage_class legacy_plaintext`() {
        val n = count("SELECT COUNT(*) FROM uploads WHERE storage_class != 'legacy_plaintext'")
        assertEquals(0, n, "expected all uploads to be legacy_plaintext after V13 migration")
    }

    // ---- 2. Public storage class can be inserted directly at the DB level ----
    // The architecture admits 'public'; the API rejects it (tested in E2).
    // This test proves the schema itself does not block it.

    @Test
    fun `public storage class row can be inserted at the DB level`() {
        val id = UUID.randomUUID()
        exec("""
            INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class)
            VALUES ('$id', 'test/public-$id.jpg', 'image/jpeg', 1024, 'public')
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
                INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, wrapped_dek, dek_format)
                VALUES ('$id', 'test/enc-$id.jpg', 'image/jpeg', 1024, 'encrypted', NULL, NULL)
            """.trimIndent())
        }
    }

    // ---- 4. Legacy_plaintext row with non-null wrapped_dek is rejected by the constraint ----

    @Test
    fun `legacy_plaintext storage class with non-null wrapped_dek violates constraint`() {
        val id = UUID.randomUUID()
        assertThrows<SQLException> {
            exec("""
                INSERT INTO uploads (id, storage_key, mime_type, file_size, storage_class, wrapped_dek, dek_format)
                VALUES ('$id', 'test/leg-$id.jpg', 'image/jpeg', 1024, 'legacy_plaintext',
                        decode('deadbeef', 'hex'), 'aes256gcm-v1')
            """.trimIndent())
        }
    }
}
