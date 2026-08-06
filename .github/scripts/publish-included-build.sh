#!/usr/bin/env bash
# Publishes an included build to Maven Central, but only when this release actually moves one of its
# artifacts. The root build's `publishChangedToMavenCentral` cannot reach a separate build, so the
# decision is made here from the same recorded versions.
#
# Usage: publish-included-build.sh <buildDir> <artifactId>...
set -euo pipefail

build_dir="${1:?usage: publish-included-build.sh <buildDir> <artifactId>...}"
shift

train="$(grep -m1 '^jetwhale = ' gradle/libs.versions.toml | cut -d'"' -f2)"

due=""
for artifact_id in "$@"; do
    # A never-published artifact has no entry, which reads as "not at the train version" — but it is
    # exactly what a release must publish, so treat a missing entry as due.
    recorded="$(grep -m1 "^artifact.${artifact_id}=" gradle/published-versions.properties | cut -d= -f2 || true)"
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
