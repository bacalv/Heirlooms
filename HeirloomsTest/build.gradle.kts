plugins {
    kotlin("jvm") version "1.9.22"
}

group = "digital.heirlooms"
version = "0.2.0"

repositories {
    mavenCentral()
}

val testcontainersVersion = "2.0.5"

dependencies {
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()

    systemProperty("heirloom-server.image", project.findProperty("heirloom-serverImage") ?: "heirloom-server:latest")

    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // Forward the Docker socket into the Gradle test worker JVM.
    // The socket location is read from DOCKER_HOST if set by run-tests.sh,
    // otherwise falls back to the docker.raw.sock used on macOS with Docker Desktop.
    val dockerHost = System.getenv("DOCKER_HOST")
        ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    val dockerSocket = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
        ?: dockerHost.removePrefix("unix://")
    environment("DOCKER_HOST", dockerHost)
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerSocket)

    outputs.upToDateWhen { false }

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/heirloom-test"))
        junitXml.required.set(true)
    }
}
