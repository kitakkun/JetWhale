#!/usr/bin/env bash
# Publishes an included build to Maven Central, but only when this release actually moves one of its
# artifacts. The root build's `publishChangedToMavenCentral` cannot reach a separate build, so the
# decision is made here from the same recorded versions.
#
# Usage: publish-included-build.sh <buildDir> <artifactId>...
set -euo pipefail

build_dir="${1:?usage: publish-included-build.sh <buildDir> <artifactId>...}"
shift

# Whitespace-tolerant, and a hard failure rather than an empty value: an empty train version would
# match nothing and silently skip publishing this build.
train="$(sed -n 's/^jetwhale[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' gradle/libs.versions.toml | head -1)"
if [ -z "$train" ]; then
    echo "Could not read the train version (jetwhale) from gradle/libs.versions.toml." >&2
    exit 1
fi

due=""
for artifact_id in "$@"; do
    # A never-published artifact has no entry, which reads as "not at the train version" — but it is
    # exactly what a release must publish, so treat a missing entry as due.
    recorded="$(sed -n "s/^artifact\\.${artifact_id}=//p" gradle/published-versions.properties | head -1)"
    if [ -z "$recorded" ] || [ "$recorded" = "$train" ]; then
        due="$due $artifact_id"
    fi
done

if [ -z "$due" ]; then
    echo "No artifact of $build_dir moves in $train; nothing to publish."
    exit 0
fi

echo "Due at $train:$due — publishing $build_dir."
./gradlew -p "$build_dir" publishToMavenCentral --no-configuration-cache
