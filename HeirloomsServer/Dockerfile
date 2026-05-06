# Build the JAR locally first: ./gradlew shadowJar
# Then: docker build ...
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

RUN groupadd -r heirloom && useradd -r -g heirloom heirloom
USER heirloom

COPY build/libs/HeirloomsServer-*-all.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Dorg.gradle.native=false", "-jar", "app.jar"]
