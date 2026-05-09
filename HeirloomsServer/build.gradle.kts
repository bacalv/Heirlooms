plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "digital.heirlooms"
version = "0.26.0"

repositories {
    mavenCentral()
}

val http4kVersion = "4.46.0.0"
val awsSdkVersion = "2.25.11"
val swaggerUiVersion = "5.11.8"
val gcsVersion = "2.36.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-netty:$http4kVersion")
    implementation("org.http4k:http4k-contract:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.webjars:swagger-ui:$swaggerUiVersion")

    // EXIF / video metadata extraction
    implementation("com.drewnoakes:metadata-extractor:2.19.0")

    // AWS SDK v2 — S3 async client
    implementation("software.amazon.awssdk:s3:$awsSdkVersion")

    // Google Cloud Storage
    implementation("com.google.cloud:google-cloud-storage:$gcsVersion")

    // Cloud SQL socket factory — required for Unix socket connections on Cloud Run
    implementation("com.google.cloud.sql:postgres-socket-factory:1.19.0")

    // Coroutines — background EXIF recovery service
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    testImplementation(kotlin("test"))
    testImplementation("org.http4k:http4k-testing-hamkrest:$http4kVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("digital.heirlooms.server.MainKt")
}

tasks.test {
    useJUnitPlatform()

    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    val dockerHost = System.getenv("DOCKER_HOST")
        ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    val dockerSocket = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
        ?: dockerHost.removePrefix("unix://")
    environment("DOCKER_HOST", dockerHost)
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerSocket)
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    manifest { attributes["Main-Class"] = "digital.heirlooms.server.MainKt" }
    mergeServiceFiles()
}