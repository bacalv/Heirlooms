FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

RUN GRADLE_OPTS="-Dorg.gradle.native=false" ./gradlew dependencies --no-daemon -q 2>/dev/null || true

COPY src/ src/
RUN GRADLE_OPTS="-Dorg.gradle.native=false" ./gradlew shadowJar --no-daemon -q

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r heirloom && useradd -r -g heirloom heirloom
USER heirloom

COPY --from=builder /build/build/libs/HeirloomsServer-*-all.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Dorg.gradle.native=false", "-jar", "app.jar"]