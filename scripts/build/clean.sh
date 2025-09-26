#!/bin/bash
# =====================================================
# Purpose: Clean build artifacts for Kotlin modules
# Removes: Gradle build dirs, ffmpeg build cache
# Output: Fresh workspace without generated artifacts
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🧹 Cleaning build artifacts..."

paths=(
  "build"
  "composeApp/build"
  "composeApp/.kotlin"
  ".gradle"
  "ffmpeg-build"
  "others/ffmpeg-source/build"
)

for p in "${paths[@]}"; do
  if [ -e "$p" ]; then
    echo " - Removing $p"
    rm -rf "$p"
  fi
done

echo "✅ Clean complete."


