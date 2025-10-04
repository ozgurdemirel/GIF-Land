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

# Check for skip-ffmpeg-build flag
SKIP_FFMPEG_BUILD=false
if [[ "$1" == "--skip-ffmpeg-build" ]]; then
    SKIP_FFMPEG_BUILD=true
    echo "ℹ️  Skipping FFmpeg build (using existing binary)"
fi

# Check if static FFmpeg exists, if not build it
FFMPEG_STATIC="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
if [ ! -f "$FFMPEG_STATIC" ] && [ "$SKIP_FFMPEG_BUILD" != "true" ]; then
    echo "🎬 Building static FFmpeg (this will take a while on first run)..."
    ./scripts/build/build-ffmpeg-static.sh
elif [ -f "$FFMPEG_STATIC" ]; then
    echo "✅ Static FFmpeg already exists"
    # Make sure it's executable
    chmod +x "$FFMPEG_STATIC"
elif [ "$SKIP_FFMPEG_BUILD" == "true" ] && [ ! -f "$FFMPEG_STATIC" ]; then
    echo "❌ FFmpeg not found and build skipped!" >&2
    exit 1
fi

# Pure Kotlin/JVM implementation - no native encoder needed
echo "✅ Using pure Kotlin/JVM encoder - no native compilation required"

# Check and prepare FFmpeg
FFMPEG_PATH="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
if [ -f "$FFMPEG_PATH" ]; then
    FFMPEG_SIZE=$(ls -lh "$FFMPEG_PATH" | awk '{print $5}')
    echo "✅ FFmpeg bundled: $FFMPEG_SIZE"

    # Make executable and clean
    chmod +x "$FFMPEG_PATH"
    xattr -cr "$FFMPEG_PATH" 2>/dev/null || true

    # Check architecture
    echo "🏗️ FFmpeg architecture:"
    file "$FFMPEG_PATH"
    lipo -info "$FFMPEG_PATH" 2>/dev/null || true
else
    echo "⚠️  FFmpeg not found in resources"
fi

# Clean and build the application
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building DMG package..."
./gradlew :composeApp:packageDmg

# Check DMG size and copy with canonical name
DMG_DIR="composeApp/build/compose/binaries/main/dmg"
DMG_PATH=$(find "$DMG_DIR" -name "*.dmg" -type f 2>/dev/null | head -n 1 || true)
if [ -n "$DMG_PATH" ] && [ -f "$DMG_PATH" ]; then
    DMG_SIZE=$(ls -lh "$DMG_PATH" | awk '{print $5}')

    # Produce a clearly named artifact for CI
    TARGET_DMG="$DMG_DIR/webp-recorder-mac-silicon.dmg"
    cp "$DMG_PATH" "$TARGET_DMG"

    echo ""
    echo "✅ Build complete!"
    echo "📦 DMG created: $TARGET_DMG"
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