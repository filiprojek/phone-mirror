#!/usr/bin/env bash
# Cut a PhoneMirror release: bump VERSION_NAME, run the unit tests, commit,
# tag vX.Y.Z and push. The pushed tag triggers .github/workflows/release.yml,
# which builds the signed APK and publishes the GitHub Release.
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: scripts/release.sh <X.Y.Z|patch|minor|major> [--dry-run]

  X.Y.Z      release exactly this version (minor and patch at most 99)
  patch      bump the patch number of VERSION_NAME in gradle.properties
  minor      bump the minor number, reset patch
  major      bump the major number, reset minor and patch
  --dry-run  show what would happen without changing anything
EOF
  exit 2
}

die() {
  echo "release: $*" >&2
  exit 1
}

# version_code X.Y.Z -> integer ordering key (matches versionCode in app/build.gradle.kts)
version_code() {
  local major minor patch
  IFS=. read -r major minor patch <<<"$1"
  echo $((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
}

valid_version() {
  [[ "$1" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$ ]]
}

next_version() { # current bump
  local major minor patch
  IFS=. read -r major minor patch <<<"$1"
  case "$2" in
    patch) echo "$major.$minor.$((patch + 1))" ;;
    minor) echo "$major.$((minor + 1)).0" ;;
    major) echo "$((major + 1)).0.0" ;;
    *) echo "$2" ;;
  esac
}

target_arg=""
dry_run=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=true ;;
    -h | --help) usage ;;
    *) [[ -z "$target_arg" ]] || usage; target_arg=$arg ;;
  esac
done
[[ -n "$target_arg" ]] || usage

cd "$(git rev-parse --show-toplevel)"
props=gradle.properties

current=$(sed -n 's/^VERSION_NAME=//p' "$props")
valid_version "$current" || die "VERSION_NAME in $props is missing or invalid: '$current'"

version=$(next_version "$current" "$target_arg")
valid_version "$version" || die "invalid version '$version' (expected X.Y.Z, minor and patch 0-99)"
tag="v$version"

branch=$(git symbolic-ref --short -q HEAD || true)
[[ "$branch" == master ]] || die "releases are cut from master (on '${branch:-detached HEAD}')"
[[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"

git fetch -q --tags origin master
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/master)" ]] ||
  die "master and origin/master differ; pull or push first"
! git rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "tag $tag already exists"

latest=$(git tag -l 'v[0-9]*' --sort=-v:refname | head -n 1)
if [[ -n "$latest" ]] && valid_version "${latest#v}"; then
  (($(version_code "$version") > $(version_code "${latest#v}"))) ||
    die "$tag must be newer than the latest release $latest"
fi

echo "Releasing $tag (VERSION_NAME $current -> $version)"
if $dry_run; then
  echo "dry run: would run unit tests, commit gradle.properties, tag $tag and push to origin"
  exit 0
fi

./gradlew :core:test :app:testDebugUnitTest

if [[ "$version" != "$current" ]]; then
  sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$version/" "$props"
  git add "$props"
  git commit -q -m "chore(release): $tag"
fi
git tag -a "$tag" -m "PhoneMirror $tag"
git push -q --atomic origin master "$tag"

echo "Pushed $tag. Follow the release build with: gh run watch"
