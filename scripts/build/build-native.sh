#!/bin/bash
# =====================================================
# Purpose: Build WebP Recorder app (DMG)
# Output: composeApp/build/compose/binaries/main/dmg/*.dmg
# Requirements: JDK 17+, Gradle Wrapper
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "Building WebP Recorder (pure Kotlin/JVM implementation)..."

echo "Building DMG package..."
./gradlew :composeApp:packageDmg

echo ""
echo "✅ Build complete! DMG location:"
ls -lh composeApp/build/compose/binaries/main/dmg/*.dmg 2>/dev/null || echo "No DMG found"