package digital.heirlooms.tools.reimport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun main() {
    val config = ReimportConfig.fromEnv()

    println("[reimport] starting — bucket=${config.gcsBucket}")
    println("[reimport] credentials=${if (config.gcsCredentialsJson != null) "service account JSON" else "ADC"}")
    println("[reimport] db=${config.dbUrl}")

    val reader = GcsBucketReader.create(config)

    val hikari = HikariConfig().apply {
        jdbcUrl = config.dbUrl
        username = config.dbUser
        password = config.dbPassword
        maximumPoolSize = 3
        minimumIdle = 1
        connectionTimeout = 30_000
    }
    val dataSource = HikariDataSource(hikari)

    try {
        val importer = Importer(reader, dataSource)

        println("[reimport] --- import phase ---")
        val importSummary = importer.runImport()

        println("[reimport] --- verify phase ---")
        val verifySummary = importer.runVerify()

        println("[reimport] === done ===")
        println("[reimport] import:  scanned=${importSummary.scanned}  imported=${importSummary.imported}  skippedExists=${importSummary.skippedExists}  skippedContentType=${importSummary.skippedContentType}  errored=${importSummary.errored}")
        println("[reimport] verify:  gcsCount=${verifySummary.gcsCount}  dbCount=${verifySummary.dbCount}  parityOk=${verifySummary.countParityOk}  sampleChecked=${verifySummary.sampleChecked}  samplePassed=${verifySummary.samplePassed}")

        if (!verifySummary.countParityOk) {
            println("[reimport] WARNING: count parity check failed — review the output above")
        }
        if (verifySummary.samplePassed < verifySummary.sampleChecked) {
            println("[reimport] WARNING: ${verifySummary.sampleChecked - verifySummary.samplePassed} sample integrity checks failed — review the output above")
        }
    } finally {
        dataSource.close()
    }
}
