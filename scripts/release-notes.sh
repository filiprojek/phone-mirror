#!/usr/bin/env bash
# Print Markdown release notes for TAG: the Conventional Commits since the
# previous v* tag (or the whole history for the first release), grouped into
# Features, Fixes and Other. Release bump commits are left out.
set -euo pipefail

[[ $# -eq 1 ]] || {
  echo "Usage: scripts/release-notes.sh <tag>" >&2
  exit 2
}
tag=$1
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || {
  echo "release-notes: tag $tag does not exist" >&2
  exit 1
}

previous=$(git describe --tags --abbrev=0 --match 'v[0-9]*' "$tag^" 2>/dev/null || true)
range=${previous:+$previous..}$tag

features=() fixes=() other=()
pattern='^([a-z]+)(\(([^)]+)\))?!?: (.+)$'
while IFS= read -r subject; do
  if [[ "$subject" =~ $pattern ]]; then
    type=${BASH_REMATCH[1]} scope=${BASH_REMATCH[3]} text=${BASH_REMATCH[4]}
    [[ "$type" == chore && "$scope" == release ]] && continue
    entry="- ${scope:+**$scope:** }$text"
    case "$type" in
      feat) features+=("$entry") ;;
      fix) fixes+=("$entry") ;;
      *) other+=("- $subject") ;;
    esac
  else
    other+=("- $subject")
  fi
done < <(git log --reverse --no-merges --format=%s "$range")

section() { # title entries...
  local title=$1
  shift
  (($# > 0)) || return 0
  printf '### %s\n\n' "$title"
  printf '%s\n' "$@"
  printf '\n'
}

section Features "${features[@]}"
section Fixes "${fixes[@]}"
section Other "${other[@]}"

if [[ -n "$previous" && -n "${GITHUB_REPOSITORY:-}" ]]; then
  printf '**Full changelog:** %s/%s/compare/%s...%s\n' \
    "${GITHUB_SERVER_URL:-https://github.com}" "$GITHUB_REPOSITORY" "$previous" "$tag"
fi
