package digital.heirlooms.test

import okhttp3.OkHttpClient
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
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

        lateinit var httpClient: OkHttpClient
            private set

        private var composeInstance: Any? = null

        private const val SERVICE_NAME = "heirloom-server"
        private const val SERVICE_PORT = 8080

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

        composeInstance = container
        container.start()

        val host = container.getServiceHost(SERVICE_NAME, SERVICE_PORT)
        val port = container.getServicePort(SERVICE_NAME, SERVICE_PORT)
        baseUrl = "http://$host:$port"

        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        context.root.getStore(ExtensionContext.Namespace.GLOBAL)
            .put(HeirloomsTestEnvironment::class.qualifiedName, this)
    }

    override fun close() {
        composeInstance?.let { (it as ComposeContainer).stop() }
    }
}
