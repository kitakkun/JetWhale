#!/usr/bin/env bash
# generate icon for macOS

set -euo pipefail

SVG_INPUT="${1:-icon.svg}"
ICON_NAME="${2:-icon}"

ICONSET_DIR="${ICON_NAME}.iconset"
OUTPUT_ICNS="${ICON_NAME}.icns"

command -v rsvg-convert >/dev/null || {
  echo "rsvg-convert not found. Install with: brew install librsvg"
  exit 1
}

command -v iconutil >/dev/null || {
  echo "iconutil not found (macOS only)"
  exit 1
}

[ -f "$SVG_INPUT" ] || {
  echo "SVG not found: $SVG_INPUT"
  exit 1
}

rm -rf "$ICONSET_DIR"
mkdir "$ICONSET_DIR"

SIZES=(16 32 128 256 512)

for size in "${SIZES[@]}"; do
  rsvg-convert -w "$size" -h "$size" "$SVG_INPUT" \
    -o "$ICONSET_DIR/icon_${size}x${size}.png"

  rsvg-convert -w "$((size * 2))" -h "$((size * 2))" "$SVG_INPUT" \
    -o "$ICONSET_DIR/icon_${size}x${size}@2x.png"
done

iconutil -c icns "$ICONSET_DIR" -o "$OUTPUT_ICNS"

rm -rrf "$ICONSET_DIR"

echo "Done → $OUTPUT_ICNS"
