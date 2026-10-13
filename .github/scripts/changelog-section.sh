#!/usr/bin/env bash
# Prints the body of CHANGELOG.md's section for a version: the lines after its `## [<version>]`
# heading, up to the next `## [` heading or the link references at the bottom of the file. Fails
# when the section is missing or empty.
set -euo pipefail

version="$1"
changelog="${2:-CHANGELOG.md}"

section="$(awk -v heading="## [${version}]" '
  index($0, "## [") == 1 { if (inside) exit; inside = (index($0, heading) == 1); next }
  inside && /^\[[^]]+\]: / { exit }
  inside
' "$changelog")"

if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
  echo "::error::${changelog} has no entry for ${version}. Add a '## [${version}]' section before tagging." >&2
  exit 1
fi

printf '%s\n' "$section"
