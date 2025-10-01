#!/bin/bash
# =====================================================
# Purpose: Build and package the app with all native components
# Output: composeApp/build/compose/binaries/main/dmg/*.dmg
# Requirements: Gradle Wrapper, JDK 17+
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🔨 Building complete package..."

# Check if static FFmpeg exists, if not build it
FFMPEG_STATIC="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
if [ ! -f "$FFMPEG_STATIC" ]; then
    echo "🎬 Building static FFmpeg (this will take a while on first run)..."
    ./scripts/build/build-ffmpeg-static.sh
else
    echo "✅ Static FFmpeg already exists"
fi

# Pure Kotlin/JVM implementation - no native encoder needed
echo "✅ Using pure Kotlin/JVM encoder - no native compilation required"

# Check FFmpeg
FFMPEG_PATH="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
if [ -f "$FFMPEG_PATH" ]; then
    FFMPEG_SIZE=$(ls -lh "$FFMPEG_PATH" | awk '{print $5}')
    echo "✅ FFmpeg bundled: $FFMPEG_SIZE"
else
    echo "⚠️  FFmpeg not found in resources"
fi

# Clean and build the application
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building DMG package..."
./gradlew :composeApp:packageDmg

# Check DMG size
DMG_DIR="composeApp/build/compose/binaries/main/dmg"
DMG_PATH=$(find "$DMG_DIR" -name "*.dmg" -type f 2>/dev/null | head -n 1 || true)
if [ -n "$DMG_PATH" ] && [ -f "$DMG_PATH" ]; then
    DMG_SIZE=$(ls -lh "$DMG_PATH" | awk '{print $5}')
    echo ""
    echo "✅ Build complete!"
    echo "📦 DMG created: $DMG_PATH"
    echo "📏 DMG size: $DMG_SIZE"
    echo ""
    echo "🎯 Package includes:"
    echo "   - Kotlin/Compose Desktop application (pure JVM)"
    echo "   - Bundled FFmpeg - no external dependencies"
    echo "   - No native process required - all encoding in JVM"
else
    echo "❌ DMG build failed!"
    exit 1
fi