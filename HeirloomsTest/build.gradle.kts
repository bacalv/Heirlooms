plugins {
    kotlin("jvm") version "1.9.22"
    jacoco
}

group = "digital.heirlooms"
version = "0.2.0"

repositories {
    mavenCentral()
}

val testcontainersVersion = "2.0.5"
val http4kVersion = "4.46.0.0"

dependencies {
    // HeirloomsServer is included via composite build in settings.gradle.kts;
    // Gradle substitutes this GAV with the included build's output.
    testImplementation("digital.heirlooms:HeirloomsServer:0.50.4")

    // http4k — needed to call corsFilter/sessionAuthFilter/buildApp and start Netty in-process
    testImplementation("org.http4k:http4k-core:$http4kVersion")
    testImplementation("org.http4k:http4k-server-netty:$http4kVersion")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("software.amazon.awssdk:s3:2.25.11")
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

// ---------------------------------------------------------------------------
// Coverage tasks — in-process mode (Netty + Testcontainers Postgres + MinIO)
// ---------------------------------------------------------------------------

// The HeirloomsServer compiled classes live in the included build's output directory.
// We reference them via a file path so JaCoCo can find them regardless of composite-build
// project-reference limitations.
val serverBuildDir = file("${rootDir}/../HeirloomsServer/build/classes/kotlin/main")

// coverageTest: same as test but starts the server in-process for JaCoCo instrumentation
tasks.register<Test>("coverageTest") {
    description = "Runs integration tests in-process with JaCoCo coverage"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("heirloom.test.mode", "inprocess")
    useJUnitPlatform()
    // Testcontainers is still needed for Postgres and MinIO containers
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    val dockerHost = System.getenv("DOCKER_HOST")
        ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    val dockerSocket = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
        ?: dockerHost.removePrefix("unix://")
    environment("DOCKER_HOST", dockerHost)
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerSocket)
    outputs.upToDateWhen { false }
    finalizedBy("jacocoCoverageReport")
}

// jacocoCoverageReport: generates HTML + XML reports from coverageTest execution data
tasks.register<JacocoReport>("jacocoCoverageReport") {
    description = "Generates JaCoCo coverage report from coverageTest"
    group = "reporting"
    dependsOn("coverageTest")
    executionData(tasks.named<Test>("coverageTest").get())

    // Source files for the report (links back to source in IDE / HTML report)
    sourceDirectories.from(file("${rootDir}/../HeirloomsServer/src/main/kotlin"))

    // Class directories: the compiled HeirloomsServer classes, excluding startup/config glue
    classDirectories.from(
        fileTree(serverBuildDir) {
            exclude("**/Main*", "**/AppConfig*")
        }
    )

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/coverage.xml"))
        csv.required.set(false)
    }
}

// jacocoCoverageVerify: enforces 90% instruction coverage (reports but does not fail yet)
tasks.register<JacocoCoverageVerification>("jacocoCoverageVerify") {
    description = "Verifies HeirloomsServer coverage is at least 90%"
    group = "verification"
    dependsOn("coverageTest")
    executionData(tasks.named<Test>("coverageTest").get())
    classDirectories.from(fileTree(serverBuildDir))
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
