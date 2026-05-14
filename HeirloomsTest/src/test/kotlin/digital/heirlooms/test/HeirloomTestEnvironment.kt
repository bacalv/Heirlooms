package digital.heirlooms.test

import digital.heirlooms.server.AppConfig
import digital.heirlooms.server.Database
import digital.heirlooms.server.S3FileStore
import digital.heirlooms.server.StorageBackend
import digital.heirlooms.server.buildApp
import digital.heirlooms.server.corsFilter
import digital.heirlooms.server.sessionAuthFilter
import okhttp3.OkHttpClient
import org.http4k.core.then
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit

class HeirloomsTestEnvironment : BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    companion object {

        init {
            configureDockerEnvironment()
        }

        @Volatile private var started = false

        /** When true, starts Postgres + MinIO via Testcontainers and runs the server in-process. */
        private val inProcessMode = System.getProperty("heirloom.test.mode") == "inprocess"

        @Volatile
        lateinit var baseUrl: String
            private set

        @Volatile
        lateinit var minioBaseUrl: String
            private set

        lateinit var httpClient: OkHttpClient
            private set

        lateinit var minioS3Client: S3AsyncClient
            private set

        fun putToMinio(bucket: String, key: String, bytes: ByteArray) {
            val req = PutObjectRequest.builder().bucket(bucket).key(key).contentLength(bytes.size.toLong()).build()
            minioS3Client.putObject(req, AsyncRequestBody.fromBytes(bytes)).get()
        }

        // Docker Compose instance (used in default mode)
        private var composeInstance: Any? = null

        // In-process server handle and containers (used in in-process mode)
        private var inProcessServer: Http4kServer? = null
        private var inProcessPostgres: GenericContainer<*>? = null
        private var inProcessMinio: GenericContainer<*>? = null

        private const val SERVICE_NAME = "heirloom-server"
        private const val SERVICE_PORT = 8080
        private const val MINIO_SERVICE_NAME = "minio"
        private const val MINIO_PORT = 9000

        private fun findDockerSocket(): String {
            val home = System.getProperty("user.home")
            val candidates = listOf(
                "$home/Library/Containers/com.docker.docker/Data/docker.raw.sock",
                "$home/.docker/run/docker.sock",
                "$home/.docker/desktop/docker.sock",
                "/var/run/docker.sock",
            )
            return candidates.firstOrNull { Files.exists(Paths.get(it)) }
                ?: error("Could not find Docker socket. Is Docker Desktop running?\nTried: $candidates")
        }

        private fun configureDockerEnvironment() {
            val socket = findDockerSocket()
            val host = "unix://$socket"
            System.setProperty("DOCKER_HOST", host)
            System.setProperty("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", socket)
            injectEnv("DOCKER_HOST", host)
            injectEnv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", socket)
            injectEnv("TESTCONTAINERS_RYUK_DISABLED", "true")
            println("[HeirloomsTestEnvironment] DOCKER_HOST=$host")
        }

        @Suppress("UNCHECKED_CAST")
        private fun injectEnv(key: String, value: String) {
            try {
                val env = System.getenv()
                val field = env.javaClass.getDeclaredField("m")
                field.isAccessible = true
                (field.get(env) as MutableMap<String, String>)[key] = value
            } catch (_: Exception) { }
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        if (started) return
        started = true

        if (inProcessMode) {
            startInProcess()
        } else {
            startDockerCompose()
        }

        context.root.getStore(ExtensionContext.Namespace.GLOBAL)
            .put(HeirloomsTestEnvironment::class.qualifiedName, this)
    }

    // -------------------------------------------------------------------------
    // In-process mode: Testcontainers Postgres + MinIO, server runs in the JVM
    // -------------------------------------------------------------------------

    private fun startInProcess() {
        println("[HeirloomsTestEnvironment] Starting in-process mode")

        // --- Postgres ---
        @Suppress("UNCHECKED_CAST")
        val postgres = GenericContainer<Nothing>("postgres:16-alpine").apply {
            withEnv("POSTGRES_DB", "heirloom")
            withEnv("POSTGRES_USER", "heirloom")
            withEnv("POSTGRES_PASSWORD", "heirloom_test")
            withExposedPorts(5432)
            waitingFor(Wait.forListeningPort())
        }
        postgres.start()
        inProcessPostgres = postgres

        // --- MinIO ---
        @Suppress("UNCHECKED_CAST")
        val minio = GenericContainer<Nothing>("minio/minio:latest").apply {
            withCommand("server /data --console-address :9001")
            withEnv("MINIO_ROOT_USER", "minioadmin")
            withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            withExposedPorts(9000)
            waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).allowInsecure())
        }
        minio.start()
        inProcessMinio = minio

        val minioPort = minio.getMappedPort(9000)
        minioBaseUrl = "http://localhost:$minioPort"
        println("[HeirloomsTestEnvironment] minioBaseUrl=$minioBaseUrl")

        // --- S3 client for tests (also used to create the bucket) ---
        minioS3Client = S3AsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("minioadmin", "minioadmin")
            ))
            .endpointOverride(URI.create(minioBaseUrl))
            .forcePathStyle(true)
            .build()

        // Create the S3 bucket that the server will use
        minioS3Client.createBucket(
            CreateBucketRequest.builder().bucket("heirloom-bucket").build()
        ).get()

        // --- AppConfig ---
        val config = AppConfig(
            serverPort = 18080,
            storageBackend = StorageBackend.S3,
            s3Bucket = "heirloom-bucket",
            s3Region = "us-east-1",
            s3AccessKey = "minioadmin",
            s3SecretKey = "minioadmin",
            s3EndpointOverride = "http://localhost:$minioPort",
            dbUrl = "jdbc:postgresql://localhost:${postgres.getMappedPort(5432)}/heirloom",
            dbUser = "heirloom",
            dbPassword = "heirloom_test",
            apiKey = "heirloom-integration-test-key",
            storageDir = "",
            gcsBucket = "",
            gcsCredentialsJson = "",
        )

        // --- Database ---
        val database = Database.create(config)
        database.runMigrations()

        // --- Storage ---
        val storage = S3FileStore.create(
            config.s3Bucket, config.s3Region,
            config.s3AccessKey, config.s3SecretKey,
            config.s3EndpointOverride,
        )

        // --- In-process server ---
        val server = corsFilter()
            .then(sessionAuthFilter(database, config.apiKey)
            .then(buildApp(storage, database, previewDurationSeconds = config.previewDurationSeconds)))
            .asServer(Netty(config.serverPort))
        server.start()
        inProcessServer = server

        baseUrl = "http://localhost:${config.serverPort}"
        println("[HeirloomsTestEnvironment] In-process server running at $baseUrl")

        // --- HTTP client ---
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Api-Key", config.apiKey).build())
            }
            .build()
    }

    // -------------------------------------------------------------------------
    // Docker Compose mode (default): full stack in containers
    // -------------------------------------------------------------------------

    private fun startDockerCompose() {
        val composeFile = File(
            HeirloomsTestEnvironment::class.java
                .getResource("/docker-compose.yml")!!.toURI()
        )

        val image = System.getProperty("heirloom-server.image", "heirloom-server:latest")

        val container = ComposeContainer(composeFile)
            .withEnv("HEIRLOOM_SERVER_IMAGE", image)
            .withExposedService(
                SERVICE_NAME, SERVICE_PORT,
                Wait.forHttp("/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(120))
            )
            .withExposedService(MINIO_SERVICE_NAME, MINIO_PORT)

        composeInstance = container
        container.start()

        val host = container.getServiceHost(SERVICE_NAME, SERVICE_PORT)
        val port = container.getServicePort(SERVICE_NAME, SERVICE_PORT)
        baseUrl = "http://$host:$port"

        val minioHost = container.getServiceHost(MINIO_SERVICE_NAME, MINIO_PORT)
        val minioPort = container.getServicePort(MINIO_SERVICE_NAME, MINIO_PORT)
        minioBaseUrl = "http://$minioHost:$minioPort"
        println("[HeirloomsTestEnvironment] minioBaseUrl=$minioBaseUrl")

        minioS3Client = S3AsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("minioadmin", "minioadmin")
            ))
            .endpointOverride(URI.create(minioBaseUrl))
            .forcePathStyle(true)
            .build()

        val testApiKey = System.getenv("API_KEY")?.takeIf { it.isNotEmpty() }
            ?: "heirloom-integration-test-key"
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Api-Key", testApiKey).build())
            }
            .build()
    }

    override fun close() {
        if (inProcessMode) {
            inProcessServer?.stop()
            inProcessMinio?.stop()
            inProcessPostgres?.stop()
        } else {
            composeInstance?.let { (it as ComposeContainer).stop() }
        }
    }
}
