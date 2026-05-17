plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "digital.heirlooms"
version = "0.1.0"

repositories {
    mavenCentral()
}

val okHttpVersion = "4.12.0"
val jacksonVersion = "2.17.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set("digital.heirlooms.tools.apiclient.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
