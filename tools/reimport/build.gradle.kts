plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "digital.heirlooms"
version = "0.1.0"

repositories {
    mavenCentral()
}

val gcsVersion = "2.36.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.cloud:google-cloud-storage:$gcsVersion")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("digital.heirlooms.tools.reimport.MainKt")
}

tasks.test {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

kotlin {
    jvmToolchain(21)
}
