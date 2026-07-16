#!/usr/bin/env bash
# Verifies which consumer Kotlin versions can compile & run against JetWhale artifacts.
#
# Usage:
#   ./run-matrix.sh <jetwhaleVersion> [kotlinVersion...]
#
# Examples:
#   ./run-matrix.sh 1.0.0-alpha07                       # default Kotlin matrix
#   ./run-matrix.sh 1.0.0-alpha07 2.2.21 2.3.0          # explicit versions
#   (cd .. && ./gradlew publishToMavenLocal) && ./run-matrix.sh <version>   # pre-release check
set -u
cd "$(dirname "$0")"

JETWHALE_VERSION="${1:?usage: ./run-matrix.sh <jetwhaleVersion> [kotlinVersion...]}"
shift
KOTLIN_VERSIONS=("${@:-}")
if [ -z "${KOTLIN_VERSIONS[0]:-}" ]; then
    KOTLIN_VERSIONS=(2.0.21 2.1.21 2.2.21 2.3.0)
fi

GRADLEW=../gradlew
FAILED=0

run_case() {
    local kotlin="$1" extra="${2:-}" label="$3"
    if "$GRADLEW" run --console=plain -q \
        -PkotlinVersion="$kotlin" -PjetwhaleVersion="$JETWHALE_VERSION" $extra \
        2>/dev/null | grep -q RUNTIME_OK; then
        echo "✅ $label"
    else
        echo "❌ $label"
        return 1
    fi
}

echo "JetWhale $JETWHALE_VERSION vs Kotlin: ${KOTLIN_VERSIONS[*]}"
for kotlin in "${KOTLIN_VERSIONS[@]}"; do
    if ! run_case "$kotlin" "" "Kotlin $kotlin"; then
        # Expected to fail below the minimum version; check the documented workaround still works.
        run_case "$kotlin" "-PskipMetadataCheck=true" "Kotlin $kotlin + -Xskip-metadata-version-check" \
            || FAILED=1
    fi
done

exit "$FAILED"
