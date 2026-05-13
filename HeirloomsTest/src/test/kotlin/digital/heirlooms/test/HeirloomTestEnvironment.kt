package digital.heirlooms.test

import okhttp3.OkHttpClient
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
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

        private var composeInstance: Any? = null

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

        context.root.getStore(ExtensionContext.Namespace.GLOBAL)
            .put(HeirloomsTestEnvironment::class.qualifiedName, this)
    }

    override fun close() {
        composeInstance?.let { (it as ComposeContainer).stop() }
    }
}
