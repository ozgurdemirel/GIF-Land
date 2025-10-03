#!/bin/bash
# =====================================================
# Purpose: Build and package the app for Intel Macs (x86_64)
# Output: composeApp/build/compose/binaries/main/dmg/*.dmg
# Requirements: Gradle Wrapper, JDK 17+, curl, unzip
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🔨 Building complete package for Intel Mac (x86_64)..."

# Check if Intel FFmpeg exists, if not download it
RESOURCE_DIR="composeApp/src/jvmMain/resources/native/macos-intel"
FFMPEG_INTEL="$RESOURCE_DIR/ffmpeg"

mkdir -p "$RESOURCE_DIR"

if [ ! -f "$FFMPEG_INTEL" ]; then
    echo "🎬 Downloading FFmpeg for Intel Mac..."

    tmp_dir="$(mktemp -d)"
    zip_path="$tmp_dir/ffmpeg-intel.zip"

    # Download Intel FFmpeg from evermeet.cx
    urls=(
        "https://evermeet.cx/ffmpeg/ffmpeg-121256-g0fdb5829e3.zip"
        "https://evermeet.cx/ffmpeg/ffmpeg-7.1.zip"
        "https://evermeet.cx/ffmpeg/ffmpeg-latest.zip"
    )

    download_ok=false
    for url in "${urls[@]}"; do
        echo "Attempting download: $url"
        if curl -L --fail -o "$zip_path" "$url"; then
            download_ok=true
            break
        fi
    done

    if [ "$download_ok" != true ]; then
        echo "❌ Failed to download Intel FFmpeg" >&2
        exit 1
    fi

    echo "📦 Extracting FFmpeg..."
    unzip -q "$zip_path" -d "$tmp_dir"

    # Find the ffmpeg binary
    ffmpeg_found=""
    if [ -f "$tmp_dir/ffmpeg" ]; then
        ffmpeg_found="$tmp_dir/ffmpeg"
    else
        # Search in subdirectories
        ffmpeg_found=$(find "$tmp_dir" -type f -name "ffmpeg" -not -path "*/.*" | head -n 1)
    fi

    if [ -z "$ffmpeg_found" ]; then
        echo "❌ ffmpeg binary not found after extraction" >&2
        exit 1
    fi

    echo "📋 Copying ffmpeg to resources..."
    cp "$ffmpeg_found" "$FFMPEG_INTEL"
    chmod +x "$FFMPEG_INTEL"

    # Verify it's an Intel binary
    if file "$FFMPEG_INTEL" | grep -q "x86_64"; then
        echo "✅ Intel FFmpeg binary verified"
    else
        echo "⚠️  Warning: Binary may not be x86_64 architecture"
    fi
fi

# Check FFmpeg
if [ -f "$FFMPEG_INTEL" ]; then
    FFMPEG_SIZE=$(ls -lh "$FFMPEG_INTEL" | awk '{print $5}')
    echo "✅ Intel FFmpeg bundled: $FFMPEG_SIZE"

    # Copy to standard location for the build
    STANDARD_FFMPEG="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
    mkdir -p "$(dirname "$STANDARD_FFMPEG")"
    cp "$FFMPEG_INTEL" "$STANDARD_FFMPEG"
else
    echo "❌ Intel FFmpeg not found in resources" >&2
    exit 1
fi

# Clean and build the application
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building DMG package for Intel Mac..."
./gradlew :composeApp:packageDmg

# Check DMG size and copy with canonical name
DMG_DIR="composeApp/build/compose/binaries/main/dmg"
DMG_PATH=$(find "$DMG_DIR" -name "*.dmg" -type f 2>/dev/null | head -n 1 || true)
if [ -n "$DMG_PATH" ] && [ -f "$DMG_PATH" ]; then
    DMG_SIZE=$(ls -lh "$DMG_PATH" | awk '{print $5}')

    # Produce a clearly named artifact for CI
    TARGET_DMG="$DMG_DIR/webp-recorder-mac-intel.dmg"
    cp "$DMG_PATH" "$TARGET_DMG"

    echo ""
    echo "✅ Build complete!"
    echo "📦 DMG created: $TARGET_DMG"
    echo "📏 DMG size: $DMG_SIZE"
    echo ""
    echo "🎯 Package includes:"
    echo "   - Kotlin/Compose Desktop application (pure JVM)"
    echo "   - Bundled FFmpeg for Intel Mac (x86_64)"
    echo "   - No native process required - all encoding in JVM"
else
    echo "❌ DMG build failed!"
    exit 1
fi