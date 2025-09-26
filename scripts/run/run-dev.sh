#!/bin/bash
# =====================================================
# Purpose: Run the app in development mode with optimized settings
# Output: Runs Compose app with hot reload (if configured)
# Requirements: JDK 17+, Gradle Wrapper
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🚀 Starting WebP Recorder with optimized memory settings..."

# Run the Compose Desktop application
./gradlew :composeApp:run \
    -Dorg.gradle.jvmargs="-Xmx4G -Xms1G" \
    -Dkotlin.daemon.jvmargs="-Xmx4G" \
    --parallel

# Alternative for running distributable:
# ./gradlew :composeApp:runDistributable --no-daemon