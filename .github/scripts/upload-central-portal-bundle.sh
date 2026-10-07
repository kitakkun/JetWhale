#!/usr/bin/env bash
# Zips a Maven repository that holds a release's signed artifacts, uploads it to the Central Portal
# as one deployment, and waits until the Portal has validated it. The deployment is USER_MANAGED:
# it then waits at https://central.sonatype.com/publishing/deployments for a maintainer to publish
# it. A failed validation fails the script with the Portal's errors.
#
# Usage: upload-central-portal-bundle.sh <maven-repository> <deployment-name>
# Environment:
#   MAVEN_CENTRAL_USERNAME, MAVEN_CENTRAL_PASSWORD  the Portal user token
#   CENTRAL_PORTAL_URL                              https://central.sonatype.com
#
# API: https://central.sonatype.org/publish/publish-portal-api/
set -euo pipefail

repository="$1"
deployment_name="$2"
: "${MAVEN_CENTRAL_USERNAME:?}" "${MAVEN_CENTRAL_PASSWORD:?}" "${CENTRAL_PORTAL_URL:?}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
bundle="$work/bundle.zip"
response="$work/response"

# Central needs each file's .asc, .md5 and .sha1. Gradle also writes maven-metadata.xml, .sha256 and
# .sha512, and checksums of the signatures; Central reads none of them, and every file counts against
# the namespace's monthly file limit.
files="$(cd "$repository" && find . -type f \
  ! -name 'maven-metadata*' ! -name '*.sha256' ! -name '*.sha512' ! -name '*.asc.md5' ! -name '*.asc.sha1' |
  sed 's|^\./||' | sort)"
if [ -z "$files" ]; then
  echo "::error::No artifacts under $repository"
  exit 1
fi
(cd "$repository" && zip -q -X "$bundle" -@ <<<"$files")
echo "Bundle: $(wc -l <<<"$files" | tr -d ' ') files, $(wc -c <"$bundle" | tr -d ' ') bytes zipped"

bearer_token="$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"

call_portal() { # <curl arguments>; leaves the response body in $response
  rm -f "$response"
  # The token goes through a config file so that it stays out of the process list. Without the
  # Accept header the Portal answers in XML.
  if ! curl --silent --show-error --fail-with-body --output "$response" \
    --config <(printf 'header = "Authorization: Bearer %s"\n' "$bearer_token") \
    --header 'Accept: application/json' "$@"; then
    if [ -s "$response" ]; then
      cat "$response"
      echo
    fi
    exit 1
  fi
}

# Not retried: a retry after a lost response would create a second deployment.
call_portal --connect-timeout 30 --max-time 900 --request POST \
  --url-query "name=$deployment_name" --url-query "publishingType=USER_MANAGED" \
  --form "bundle=@$bundle;type=application/octet-stream" \
  "$CENTRAL_PORTAL_URL/api/v1/publisher/upload"
deployment_id="$(tr -d '[:space:]' <"$response")"
echo "Uploaded $deployment_name as deployment $deployment_id"

deadline=$((SECONDS + 30 * 60))
last_state=""
while :; do
  call_portal --connect-timeout 10 --max-time 30 --retry 3 --request POST --url-query "id=$deployment_id" "$CENTRAL_PORTAL_URL/api/v1/publisher/status"
  state="$(jq -r '.deploymentState' "$response")"
  if [ "$state" != "$last_state" ]; then
    echo "Deployment $deployment_id: $state"
    last_state="$state"
  fi
  case "$state" in
    PENDING | VALIDATING) ;;
    VALIDATED)
      echo "Validated. Publish it at $CENTRAL_PORTAL_URL/publishing/deployments."
      exit 0
      ;;
    PUBLISHING | PUBLISHED) exit 0 ;;
    FAILED)
      echo "::error::The Central Portal rejected deployment $deployment_id"
      jq '.errors' "$response"
      exit 1
      ;;
    *)
      echo "::error::Unexpected deployment state '$state'"
      exit 1
      ;;
  esac
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "::error::Deployment $deployment_id is still $state after 30 minutes; check the Central Portal"
    exit 1
  fi
  sleep 5
done
