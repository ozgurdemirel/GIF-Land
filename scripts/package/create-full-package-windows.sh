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

RESOURCE_DIR="composeApp/src/jvmMain/resources/native/windows"
FFMPEG_EXE="$RESOURCE_DIR/ffmpeg.exe"

mkdir -p "$RESOURCE_DIR"

if [ ! -f "$FFMPEG_EXE" ]; then
  echo "🎬 Fetching prebuilt FFmpeg for Windows..."

  tmp_dir="$(mktemp -d)"
  zip_path="$tmp_dir/ffmpeg.zip"
  extract_dir="$tmp_dir/extracted"
  mkdir -p "$extract_dir"

  # Try known mirrors in order (stable public builds)
  urls=(
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
    "https://github.com/GyanD/codexffmpeg/releases/latest/download/ffmpeg-essentials_build.zip"
    "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-n6.1.1-essentials_build.zip"
  )

  download_ok=false
  for u in "${urls[@]}"; do
    echo "Attempting download: $u"
    if curl -L --fail -o "$zip_path" "$u"; then
      download_ok=true
      break
    fi
  done

  if [ "$download_ok" != true ]; then
    echo "❌ Failed to download FFmpeg from known mirrors" >&2
    exit 1
  fi

  echo "📦 Extracting FFmpeg archive..."
  # Prefer unzip, fallback to PowerShell Expand-Archive
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip_path" -d "$extract_dir"
  else
    # Use PowerShell to extract zip
    powershell -NoProfile -Command "Expand-Archive -Path '$zip_path' -DestinationPath '$extract_dir' -Force" || {
      echo "❌ Failed to extract FFmpeg archive" >&2
      exit 1
    }
  fi

  echo "🔎 Locating ffmpeg.exe..."
  ffmpeg_found=""
  while IFS= read -r -d '' f; do
    ffmpeg_found="$f"
    break
  done < <(find "$extract_dir" -type f -iname ffmpeg.exe -print0)

  if [ -z "$ffmpeg_found" ]; then
    echo "❌ ffmpeg.exe not found after extraction" >&2
    exit 1
  fi

  echo "📋 Copying ffmpeg.exe to resources: $FFMPEG_EXE"
  cp "$ffmpeg_found" "$FFMPEG_EXE"
fi

if [ -f "$FFMPEG_EXE" ]; then
  echo "✅ FFmpeg ready at: $FFMPEG_EXE (size: $(ls -lh "$FFMPEG_EXE" | awk '{print $5}'))"
else
  echo "❌ FFmpeg setup failed" >&2
  exit 1
fi

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
