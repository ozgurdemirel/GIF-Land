#!/bin/bash
# =====================================================
# Purpose: Build FFmpeg with fully static deps from source
# Output: composeApp/src/jvmMain/resources/native/macos/ffmpeg
# Requirements: git, make, pkg-config, strip, otool (macOS), Internet
# Notes: Builds x264, libwebp, libvpx and links statically
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🎬 Building FFmpeg with static libraries..."

# Setup directories
BUILD_DIR="ffmpeg-build"
PREFIX="$(pwd)/$BUILD_DIR/output"
SOURCES_DIR="$BUILD_DIR/sources"

# Clean and create directories
rm -rf "$BUILD_DIR"
mkdir -p "$PREFIX/lib" "$PREFIX/include" "$SOURCES_DIR"

# Set build flags
export CFLAGS="-I$PREFIX/include"
export LDFLAGS="-L$PREFIX/lib"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"

# Tool checks
for tool in git make strip otool pkg-config; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "❌ Required tool '$tool' not found. Please install it and retry." >&2
        exit 1
    fi
done

# Detect architecture
ARCH=$(uname -m)
NUM_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo 4)
if [ "$ARCH" = "arm64" ]; then
    echo "🎯 Building for Apple Silicon (arm64)"
    ARCH_FLAGS="--enable-cross-compile --arch=arm64"
    # Extra flags for ARM64
    export CFLAGS="$CFLAGS -arch arm64"
    export LDFLAGS="$LDFLAGS -arch arm64"
else
    echo "🎯 Building for Intel (x86_64)"
    ARCH_FLAGS="--arch=x86_64"
    export CFLAGS="$CFLAGS -arch x86_64"
    export LDFLAGS="$LDFLAGS -arch x86_64"
fi

echo "Build environment:"
echo "  Architecture: $ARCH"
echo "  CPU cores: $NUM_CORES"
echo "  CFLAGS: $CFLAGS"
echo "  LDFLAGS: $LDFLAGS"

cd "$SOURCES_DIR"

# Build libx264 (H.264 encoder)
echo "📦 Building libx264..."
git clone --depth 1 https://github.com/mirror/x264.git
cd x264
./configure --prefix="$PREFIX" --enable-static --disable-shared --disable-cli
make -j"$NUM_CORES"
make install
cd ..

# Build libwebp (WebP support)
echo "📦 Building libwebp..."
git clone --depth 1 https://github.com/webmproject/libwebp.git
cd libwebp
./autogen.sh
./configure --prefix="$PREFIX" --enable-static --disable-shared
make -j"$NUM_CORES"
make install
cd ..

# Build libvpx (VP8/VP9 support)
echo "📦 Building libvpx..."
git clone --depth 1 https://github.com/webmproject/libvpx.git
cd libvpx
./configure --prefix="$PREFIX" --enable-static --disable-shared --disable-examples --disable-docs
make -j"$NUM_CORES"
make install
cd ..

# Build FFmpeg with static linking
echo "📦 Building FFmpeg..."
git clone --depth 1 https://github.com/FFmpeg/FFmpeg.git ffmpeg
cd ffmpeg

./configure \
    --prefix="$PREFIX" \
    --pkg-config-flags="--static" \
    --extra-cflags="-I$PREFIX/include" \
    --extra-ldflags="-L$PREFIX/lib" \
    --extra-libs="-lpthread -lm" \
    --enable-static \
    --disable-shared \
    --enable-gpl \
    --enable-libx264 \
    --enable-libwebp \
    --enable-libvpx \
    --enable-filters \
    --enable-filter=palettegen \
    --enable-filter=paletteuse \
    --enable-filter=scale \
    --enable-filter=fps \
    --enable-filter=format \
    --enable-swscale \
    --disable-doc \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-debug \
    --enable-videotoolbox \
    $ARCH_FLAGS

make -j"$NUM_CORES"
make install

cd "$repo_root"

# Copy the static ffmpeg binary to resources
echo "📋 Copying static FFmpeg to resources..."
mkdir -p composeApp/src/jvmMain/resources/native/macos
cp "$PREFIX/bin/ffmpeg" composeApp/src/jvmMain/resources/native/macos/ffmpeg
chmod +x composeApp/src/jvmMain/resources/native/macos/ffmpeg

# Strip debug symbols to reduce size
strip composeApp/src/jvmMain/resources/native/macos/ffmpeg

# Check the result
echo "✅ Static FFmpeg built successfully!"
echo "📏 Size: $(ls -lh composeApp/src/jvmMain/resources/native/macos/ffmpeg | awk '{print $5}')"
echo "🏗️ Architecture check:"
file composeApp/src/jvmMain/resources/native/macos/ffmpeg
lipo -info composeApp/src/jvmMain/resources/native/macos/ffmpeg 2>/dev/null || true
echo "🔍 Dependencies check:"
otool -L composeApp/src/jvmMain/resources/native/macos/ffmpeg | grep -v "/usr/lib\|/System"

# Verify it's for the correct architecture
BUILT_ARCH=$(lipo -archs composeApp/src/jvmMain/resources/native/macos/ffmpeg 2>/dev/null || echo "unknown")
if [ "$ARCH" = "arm64" ] && [ "$BUILT_ARCH" != "arm64" ]; then
    echo "❌ ERROR: Built for $BUILT_ARCH but expected arm64!"
    exit 1
elif [ "$ARCH" = "x86_64" ] && [ "$BUILT_ARCH" != "x86_64" ]; then
    echo "❌ ERROR: Built for $BUILT_ARCH but expected x86_64!"
    exit 1
fi

# Clean up build directory (optional)
echo "🧹 Cleaning build directory..."
rm -rf "$BUILD_DIR"

echo "✨ Done! FFmpeg is now completely standalone with no external dependencies!"