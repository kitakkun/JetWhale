#!/usr/bin/env bash
# Checks the artifacts `publishToMavenLocal` produced for one version against the build's lint
# plugin (Kotrail) leaking into them:
# - a POM or Gradle module file that depends on it fails the check;
# - an Android artifact whose classes reference its annotations fails unless the artifact ships the
#   consumer rule that lets R8 ignore them (gradle/consumer-rules/kotrail.pro);
# - JVM jars and klibs that reference them are listed as warnings.
#
# Usage: check-published-artifacts.sh <version> [maven-repository]
set -euo pipefail

version="$1"
repository="${2:-$HOME/.m2/repository}/com/kitakkun/jetwhale"
failures=0
checked=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

references_kotrail() { # <jar or klib>
  # Counted rather than `grep -q`: an early exit would break unzip's pipe and fail under pipefail.
  [ "$(unzip -p "$1" 2>/dev/null | grep -ac "com/kitakkun/kotrail/")" -gt 0 ]
}

for dir in "$repository"/*/"$version"; do
  [ -d "$dir" ] || continue
  artifact="$(basename "$(dirname "$dir")")"
  checked=$((checked + 1))

  for descriptor in "$dir"/*.pom "$dir"/*.module; do
    [ -f "$descriptor" ] || continue
    if grep -qi "kotrail" "$descriptor"; then
      echo "::error::$artifact: $(basename "$descriptor") depends on Kotrail"
      failures=$((failures + 1))
    fi
  done

  for aar in "$dir"/*.aar; do
    [ -f "$aar" ] || continue
    unzip -q -o "$aar" classes.jar proguard.txt -d "$work/$artifact" 2>/dev/null || true
    if [ -f "$work/$artifact/classes.jar" ] && references_kotrail "$work/$artifact/classes.jar"; then
      if grep -q -- "-dontwarn com.kitakkun.kotrail" "$work/$artifact/proguard.txt" 2>/dev/null; then
        echo "$artifact: references Kotrail annotations, covered by its consumer rule"
      else
        echo "::error::$artifact: references Kotrail annotations without the consumer rule; R8 in an app will fail"
        failures=$((failures + 1))
      fi
    fi
  done

  for binary in "$dir"/*.jar "$dir"/*.klib; do
    [ -f "$binary" ] || continue
    case "$binary" in *-sources.jar|*-javadoc.jar) continue ;; esac
    if references_kotrail "$binary"; then
      echo "::warning::$artifact: $(basename "$binary") references Kotrail annotations"
    fi
  done
done

if [ "$checked" -eq 0 ]; then
  echo "::error::No artifacts published for $version under $repository"
  exit 1
fi
echo "Checked $checked artifacts for $version; $failures problem(s)."
[ "$failures" -eq 0 ]
