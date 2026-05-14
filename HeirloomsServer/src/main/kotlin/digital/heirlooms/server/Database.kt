package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.util.UUID
import javax.sql.DataSource

// Re-export FOUNDING_USER_ID from domain.auth for backwards compatibility
val FOUNDING_USER_ID: UUID get() = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID

class Database(val dataSource: DataSource) {

    fun runMigrations() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    companion object {
        fun create(config: AppConfig): Database {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
                minimumIdle = 2
                connectionTimeout = 30_000
                idleTimeout = 600_000
            }
            return Database(HikariDataSource(hikari))
        }
    }
}
