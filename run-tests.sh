#!/usr/bin/env bash
# run-tests.sh (Heirlooms root — single command entry point)
set -euo pipefail

IMAGE_TAG="${1:-latest}"
IMAGE="heirloom-server:${IMAGE_TAG}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/HeirloomsServer"
TEST_DIR="$SCRIPT_DIR/HeirloomsTest"

if [[ ! -f "$SERVER_DIR/Dockerfile" ]]; then
    echo "ERROR: Dockerfile not found at $SERVER_DIR/Dockerfile"
    exit 1
fi

# --- Locate the Docker socket ---
find_docker_socket() {
    local candidates=(
        "$HOME/Library/Containers/com.docker.docker/Data/docker.raw.sock"
        "$HOME/.docker/run/docker.sock"
        "$HOME/.docker/desktop/docker.sock"
        "/var/run/docker.sock"
        "/run/user/$(id -u)/docker.sock"
    )
    for s in "${candidates[@]}"; do
        if [[ -S "$s" ]]; then
            echo "$s"
            return
        fi
    done
}

DOCKER_SOCKET=$(find_docker_socket)
if [[ -z "$DOCKER_SOCKET" ]]; then
    echo "ERROR: Could not find the Docker socket. Is Docker Desktop running?"
    exit 1
fi

export DOCKER_HOST="unix://$DOCKER_SOCKET"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$DOCKER_SOCKET"
echo "Using Docker socket: $DOCKER_SOCKET"

# --- Ensure gradle-wrapper.jar is present ---
ensure_wrapper_jar() {
    local dir="$1"
    local jar="$dir/gradle/wrapper/gradle-wrapper.jar"
    if [[ ! -f "$jar" ]]; then
        echo "--- Downloading gradle-wrapper.jar for $(basename "$dir") ---"
        mkdir -p "$(dirname "$jar")"
        curl -sL \
            "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar" \
            -o "$jar" \
            || { echo "ERROR: Failed to download gradle-wrapper.jar"; exit 1; }
    fi
}

ensure_wrapper_jar "$SERVER_DIR"
ensure_wrapper_jar "$TEST_DIR"

echo "=== Building HeirloomsServer Docker image ($IMAGE) ==="
cd "$SERVER_DIR"
GRADLE_OPTS="-Dorg.gradle.native=false" ./gradlew shadowJar --no-daemon -q
docker build -t "$IMAGE" .

echo ""
echo "=== Running end-to-end tests against $IMAGE ==="
cd "$TEST_DIR"
GRADLE_OPTS="-Dorg.gradle.native=false" \
DOCKER_HOST="$DOCKER_HOST" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$DOCKER_SOCKET" \
    ./gradlew test -Pheirloom-serverImage="$IMAGE" --no-daemon

REPORT_DIR="$TEST_DIR/build/reports/heirloom-test"
echo ""
echo "=== Test complete ==="
echo "HTML report: $REPORT_DIR/index.html"

if [[ "$OSTYPE" == "darwin"* ]]; then
    open "$REPORT_DIR/index.html"
fi
