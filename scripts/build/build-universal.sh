#!/bin/bash
# =====================================================
# Purpose: Build universal binary for Intel + Apple Silicon
# Output: Universal DMG supporting both architectures
# Requirements: JDK 17+, Gradle Wrapper
# Status: TODO - Currently only ARM64 is supported
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "⚠️  Universal Binary Build - Coming Soon!"
echo ""
echo "Current status:"
echo "  ✅ ARM64 (Apple Silicon) - Supported"
echo "  ❌ x86_64 (Intel) - Not yet supported"
echo ""
echo "To add Intel support:"
echo "  1. Build FFmpeg for x86_64"
echo "  2. Build FFmpeg for arm64"
echo "  3. Combine with: lipo -create ffmpeg-x86_64 ffmpeg-arm64 -output ffmpeg"
echo "  4. Replace bundled FFmpeg with universal binary"
echo ""
echo "For now, building ARM64-only version..."
echo ""

./scripts/package/create-full-package.sh