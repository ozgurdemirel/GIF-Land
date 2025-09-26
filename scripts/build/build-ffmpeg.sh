#!/bin/bash
# =====================================================
# Purpose: Build FFmpeg from source (lean, mostly static) for macOS
# Output: composeApp/src/jvmMain/resources/native/macos/ffmpeg
# Requirements: git, make, pkg-config, Apple CLT, Internet
# Notes: Uses sources under others/ffmpeg-source if present
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🔧 FFmpeg Static Build Script for macOS"

# Configuration
FFMPEG_DIR="others/ffmpeg-source"
OUTPUT_DIR="composeApp/src/jvmMain/resources/native/macos"
FFMPEG_BINARY="$OUTPUT_DIR/ffmpeg"

# Check if FFmpeg binary already exists
if [ -f "$FFMPEG_BINARY" ]; then
    echo "✅ FFmpeg binary already exists at: $FFMPEG_BINARY"
    echo "   Size: $(ls -lh $FFMPEG_BINARY | awk '{print $5}')"
    echo "   To rebuild, delete the file and run this script again."
    exit 0
fi

echo "📦 FFmpeg binary not found, building from source..."

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Check required tools
for tool in git make pkg-config; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "❌ Required tool '$tool' not found. Please install it and retry." >&2
        exit 1
    fi
done

# Clone or update FFmpeg repository
if [ -d "$FFMPEG_DIR" ]; then
    echo "📥 Updating existing FFmpeg source..."
    cd "$FFMPEG_DIR"
    git pull
    cd ..
else
    echo "📥 Cloning FFmpeg source..."
    git clone --depth 1 --branch release/7.1 https://git.ffmpeg.org/ffmpeg.git "$FFMPEG_DIR"
fi

cd "$FFMPEG_DIR"

# Detect architecture
ARCH=$(uname -m)
echo "🖥️  Building for architecture: $ARCH"
NUM_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo 4)

# Configure FFmpeg for static build with minimal dependencies
echo "⚙️  Configuring FFmpeg..."
export PKG_CONFIG_PATH="/opt/homebrew/lib/pkgconfig:$PKG_CONFIG_PATH"
./configure \
    --prefix="$(pwd)/build" \
    --enable-static \
    --disable-shared \
    --disable-doc \
    --disable-htmlpages \
    --disable-manpages \
    --disable-podpages \
    --disable-txtpages \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-debug \
    --enable-gpl \
    --enable-version3 \
    --enable-libx264 \
    --enable-libwebp \
    --extra-cflags="-I/opt/homebrew/include" \
    --extra-ldflags="-L/opt/homebrew/lib" \
    --disable-programs \
    --enable-ffmpeg \
    --disable-avdevice \
    --disable-swresample \
    --disable-postproc \
    --disable-network \
    --enable-small \
    --disable-encoders \
    --enable-encoder=png \
    --enable-encoder=mjpeg \
    --enable-encoder=libx264 \
    --enable-encoder=libwebp \
    --disable-decoders \
    --enable-decoder=png \
    --enable-decoder=mjpeg \
    --disable-muxers \
    --enable-muxer=mp4 \
    --enable-muxer=webp \
    --enable-muxer=image2 \
    --disable-demuxers \
    --enable-demuxer=image2 \
    --disable-parsers \
    --disable-bsfs \
    --disable-protocols \
    --enable-protocol=file \
    --disable-filters \
    --enable-filter=scale \
    --arch=$ARCH

if [ $? -ne 0 ]; then
    echo "❌ Configuration failed"
    exit 1
fi

# Build FFmpeg
echo "🔨 Building FFmpeg (this may take a few minutes)..."
make -j"$NUM_CORES"

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

# Copy the binary to resources
echo "📋 Copying FFmpeg binary to resources..."
cp ffmpeg "../$FFMPEG_BINARY"
chmod +x "../$FFMPEG_BINARY"

# Check the result
cd ..
FINAL_SIZE=$(ls -lh "$FFMPEG_BINARY" | awk '{print $5}')
echo ""
echo "✅ FFmpeg successfully built!"
echo "📍 Location: $FFMPEG_BINARY"
echo "📏 Size: $FINAL_SIZE"
echo ""
echo "🎉 FFmpeg is ready to be bundled with the application!"