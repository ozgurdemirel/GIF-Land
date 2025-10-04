#!/usr/bin/env bash
# =====================================================
# Purpose: Prepare Windows build by fetching prebuilt FFmpeg and packaging MSI
# Output: composeApp/build/compose/binaries/main/msi/*.msi
# Requirements (CI): Git Bash (on Windows), curl, PowerShell (for Expand-Archive)
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🔨 Building complete package for Windows..."

# JAVE2 provides signed FFmpeg binaries - no need to download or bundle
echo "✅ Using JAVE2 with signed FFmpeg binaries for Windows"
echo "✅ No FFmpeg downloading or bundling required"

# Build Windows installer (MSI)
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building MSI package..."
./gradlew :composeApp:packageMsi

MSI_DIR="composeApp/build/compose/binaries/main/msi"
if compgen -G "$MSI_DIR/*.msi" > /dev/null; then
  MSI_PATH=$(ls "$MSI_DIR"/*.msi | head -n 1)
  TARGET_PATH="$MSI_DIR/webp-recorder-windows.msi"
  cp "$MSI_PATH" "$TARGET_PATH"
  echo "✅ MSI created: $TARGET_PATH (size: $(ls -lh "$TARGET_PATH" | awk '{print $5}'))"
else
  echo "❌ MSI not found in $MSI_DIR" >&2
  exit 1
fi
