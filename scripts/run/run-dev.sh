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

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
CLEAN_BUILD=false
BUILD_FFMPEG=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --build-ffmpeg)
            BUILD_FFMPEG=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --clean        Clean build (removes build directories)"
            echo "  --build-ffmpeg Build FFmpeg before running"
            echo "  --help         Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Build FFmpeg if requested or if not exists
FFMPEG_PATH="composeApp/src/jvmMain/resources/native/macos/ffmpeg"
if [ "$BUILD_FFMPEG" = true ] || [ ! -f "$FFMPEG_PATH" ]; then
    echo -e "${YELLOW}🔨 Building FFmpeg...${NC}"
    if [ -f "scripts/build/build-ffmpeg-static.sh" ]; then
        ./scripts/build/build-ffmpeg-static.sh
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ FFmpeg build failed${NC}"
            exit 1
        fi
        echo -e "${GREEN}✅ FFmpeg built successfully${NC}"
    else
        echo -e "${YELLOW}⚠️  FFmpeg build script not found, continuing without it${NC}"
    fi
fi

# Clean if requested
if [ "$CLEAN_BUILD" = true ]; then
    echo -e "${YELLOW}🧹 Cleaning build directories...${NC}"
    ./gradlew clean
    rm -rf composeApp/build
    rm -rf build
    rm -rf .gradle/caches
    echo -e "${GREEN}✅ Clean completed${NC}"
fi

echo -e "${GREEN}🚀 Starting WebP Recorder in development mode...${NC}"
echo -e "${YELLOW}📋 Build Configuration:${NC}"
echo "   • Clean Build: $CLEAN_BUILD"
echo "   • FFmpeg: $(if [ -f "$FFMPEG_PATH" ]; then echo "✅ Available"; else echo "❌ Missing"; fi)"
echo "   • Memory: -Xmx4G -Xms1G"
echo ""

# Run the Compose Desktop application
./gradlew :composeApp:run \
    -Dorg.gradle.jvmargs="-Xmx4G -Xms1G -XX:+UseG1GC" \
    -Dkotlin.daemon.jvmargs="-Xmx4G" \
    --parallel \
    --console=plain \
    $(if [ "$CLEAN_BUILD" = true ]; then echo "--rerun-tasks"; fi)

# Alternative for running distributable:
# ./gradlew :composeApp:runDistributable --no-daemon