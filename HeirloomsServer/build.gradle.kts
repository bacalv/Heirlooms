plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "digital.heirlooms"
version = "0.3.0"

repositories {
    mavenCentral()
}

val http4kVersion = "4.46.0.0"
val awsSdkVersion = "2.25.11"
val swaggerUiVersion = "5.11.8"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-netty:$http4kVersion")
    implementation("org.http4k:http4k-contract:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.webjars:swagger-ui:$swaggerUiVersion")

    // AWS SDK v2 — S3 async client
    implementation("software.amazon.awssdk:s3:$awsSdkVersion")

    // Database
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    testImplementation(kotlin("test"))
    testImplementation("org.http4k:http4k-testing-hamkrest:$http4kVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
}

application {
    mainClass.set("digital.heirlooms.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    manifest { attributes["Main-Class"] = "digital.heirlooms.server.MainKt" }
    mergeServiceFiles()
}