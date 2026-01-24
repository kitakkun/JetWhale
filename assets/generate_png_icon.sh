#!/usr/bin/env bash
# generate PNG icon for Linux

set -euo pipefail

SVG_INPUT="${1:-icon.svg}"
OUTPUT_PNG_ICON="${2:-icon.png}"

command -v rsvg-convert >/dev/null || {
  echo "rsvg-convert not found. Install: brew install librsvg"
  exit 1
}

rsvg-convert -w "256" -h "256" "$SVG_INPUT" -o $OUTPUT_PNG_ICON

echo "Done → $OUTPUT_PNG_ICON"
