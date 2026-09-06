#!/usr/bin/env bash
# Publishes the PNGs in a directory as the single, orphan commit of a companion branch, replacing
# whatever the branch held before. The branch carries only the images, at its root, so it stays
# tiny and never diverges from anything.
#
#   publish-screenshots.sh <branch> <directory>
#
# Needs GITHUB_TOKEN, GITHUB_REPOSITORY and GITHUB_SHA, as GitHub Actions sets them.
set -euo pipefail

branch=$1
dir=$2

if ! ls "$dir"/*.png >/dev/null 2>&1; then
  echo "nothing to publish from $dir"
  exit 0
fi

work=$(mktemp -d)
cp "$dir"/*.png "$work"/
cd "$work"
git init -q -b "$branch"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add .
git commit -q -m "Screenshots for $branch at ${GITHUB_SHA:0:8}"
git push -q --force "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "HEAD:refs/heads/$branch"
echo "published $(ls *.png | wc -l | tr -d ' ') image(s) to $branch"
