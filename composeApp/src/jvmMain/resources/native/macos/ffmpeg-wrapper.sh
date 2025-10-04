#!/bin/bash
# FFmpeg wrapper to bypass signature issues
# This script runs FFmpeg without triggering macOS security checks

# Get the actual FFmpeg path from first argument
FFMPEG_PATH="$1"
shift

# Remove quarantine attribute if exists
xattr -d com.apple.quarantine "$FFMPEG_PATH" 2>/dev/null || true

# Execute FFmpeg with all remaining arguments
exec "$FFMPEG_PATH" "$@"