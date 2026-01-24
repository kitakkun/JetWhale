#!/usr/bin/env bash
# generate icon for Windows (.ico)

set -euo pipefail

SVG_INPUT="${1:-icon.svg}"
OUTPUT_ICO="${2:-icon.ico}"

command -v rsvg-convert >/dev/null || {
  echo "rsvg-convert not found. Install: brew install librsvg"
  exit 1
}
command -v magick >/dev/null || {
  echo "ImageMagick not found. Install: brew install imagemagick"
  exit 1
}

[ -f "$SVG_INPUT" ] || { echo "SVG not found: $SVG_INPUT"; exit 1; }

SIZES=(256 128 64 48 32 16)
TMP_DIR="$(mktemp -d)"

for size in "${SIZES[@]}"; do
  rsvg-convert -w "$size" -h "$size" "$SVG_INPUT" \
    -o "$TMP_DIR/icon_${size}.png"
done

magick "$TMP_DIR"/icon_*.png "$OUTPUT_ICO"
rm -rf "$TMP_DIR"

echo "Done → $OUTPUT_ICO"
