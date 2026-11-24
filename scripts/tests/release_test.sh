#!/usr/bin/env bash
# Tests for scripts/release.sh and scripts/release-notes.sh.
# Each test runs in a throwaway git repo with a bare "origin" and a stub gradlew.
set -uo pipefail

SCRIPTS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
WORK_ROOT=$(mktemp -d)
trap 'rm -rf "$WORK_ROOT"' EXIT
PASSED=0
FAILED=0

# --- harness -----------------------------------------------------------------

fail() {
  printf '    %s\n' "$*" >&2
  return 1
}

assert_eq() { # expected actual [message]
  [[ "$1" == "$2" ]] || fail "${3:-assert_eq}: expected '$1', got '$2'"
}

assert_contains() { # haystack needle [message]
  [[ "$1" == *"$2"* ]] || fail "${3:-assert_contains}: '$2' not found in: $1"
}

assert_not_contains() { # haystack needle [message]
  [[ "$1" != *"$2"* ]] || fail "${3:-assert_not_contains}: '$2' unexpectedly found in: $1"
}

# setup_repo VERSION — creates $T/work (cwd) tracking $T/remote.git
setup_repo() {
  T=$(mktemp -d "$WORK_ROOT/t.XXXXXX")
  export GRADLEW_LOG="$T/gradlew.log"
  git init -q --bare -b master "$T/remote.git"
  git init -q -b master "$T/work"
  cd "$T/work" || exit 1
  git config user.email test@example.com
  git config user.name Test
  git config commit.gpgsign false
  git config tag.gpgsign false
  mkdir scripts
  cp "$SCRIPTS_DIR"/*.sh scripts/
  printf 'org.gradle.caching=true\nVERSION_NAME=%s\n' "$1" >gradle.properties
  cat >gradlew <<'EOF'
#!/bin/sh
echo "$@" >>"${GRADLEW_LOG:?}"
exit "${GRADLEW_EXIT:-0}"
EOF
  chmod +x gradlew
  git add -A
  git commit -qm "feat: initial"
  git remote add origin "$T/remote.git"
  git push -q origin master
}

commit_file() { # name subject
  echo "$RANDOM" >>"$1"
  git add "$1"
  git commit -qm "$2"
}

# run_release ARGS... — sets RC and OUT
run_release() {
  OUT=$(./scripts/release.sh "$@" 2>&1)
  RC=$?
}

remote_tags() { git --git-dir="$T/remote.git" tag -l | tr '\n' ' '; }
remote_file() { git --git-dir="$T/remote.git" show "master:$1"; }
remote_subject() { git --git-dir="$T/remote.git" log -1 --format=%s master; }

# --- release.sh ----------------------------------------------------------------

test_patch_bump_commits_tags_and_pushes() {
  setup_repo 0.1.0
  run_release patch
  assert_eq 0 "$RC" "exit code ($OUT)" &&
    assert_eq "v0.1.1 " "$(remote_tags)" "remote tags" &&
    assert_eq "chore(release): v0.1.1" "$(remote_subject)" "release commit" &&
    assert_contains "$(remote_file gradle.properties)" "VERSION_NAME=0.1.1" &&
    assert_contains "$(remote_file gradle.properties)" "org.gradle.caching=true" "other properties kept" &&
    assert_eq tag "$(git --git-dir="$T/remote.git" cat-file -t v0.1.1)" "annotated tag" &&
    assert_contains "$(cat "$GRADLEW_LOG")" ":core:test :app:testDebugUnitTest" "tests ran"
}

test_minor_bump_resets_patch() {
  setup_repo 0.1.5
  run_release minor
  assert_eq 0 "$RC" "exit code ($OUT)" && assert_eq "v0.2.0 " "$(remote_tags)"
}

test_major_bump_resets_minor_and_patch() {
  setup_repo 1.2.3
  run_release major
  assert_eq 0 "$RC" "exit code ($OUT)" && assert_eq "v2.0.0 " "$(remote_tags)"
}

test_explicit_version() {
  setup_repo 0.1.0
  run_release 0.3.7
  assert_eq 0 "$RC" "exit code ($OUT)" &&
    assert_eq "v0.3.7 " "$(remote_tags)" &&
    assert_contains "$(remote_file gradle.properties)" "VERSION_NAME=0.3.7"
}

test_first_release_of_current_version_only_tags() {
  setup_repo 0.1.0
  run_release 0.1.0
  assert_eq 0 "$RC" "exit code ($OUT)" &&
    assert_eq "v0.1.0 " "$(remote_tags)" &&
    assert_eq "feat: initial" "$(remote_subject)" "no bump commit"
}

test_rejects_existing_tag() {
  setup_repo 0.1.0
  git tag -a v0.1.0 -m v0.1.0 && git push -q origin v0.1.0
  run_release 0.1.0
  assert_eq 1 "$RC" "exit code" && assert_contains "$OUT" "already exists"
}

test_rejects_version_not_above_latest_tag() {
  setup_repo 0.1.0
  git tag -a v0.5.0 -m v0.5.0 && git push -q origin v0.5.0
  run_release 0.3.0
  assert_eq 1 "$RC" "exit code" && assert_contains "$OUT" "v0.5.0"
}

test_rejects_dirty_tree() {
  setup_repo 0.1.0
  echo dirty >>gradle.properties
  run_release patch
  assert_eq 1 "$RC" "exit code" && assert_contains "$OUT" "not clean" && assert_eq "" "$(remote_tags)"
}

test_rejects_other_branch() {
  setup_repo 0.1.0
  git switch -q -c feature
  run_release patch
  assert_eq 1 "$RC" "exit code" && assert_contains "$OUT" "master"
}

test_rejects_branch_behind_origin() {
  setup_repo 0.1.0
  git clone -q "$T/remote.git" "$T/other"
  (
    cd "$T/other" && git config user.email o@example.com && git config user.name O &&
      git config commit.gpgsign false && commit_file x "fix: elsewhere" && git push -q origin master
  )
  run_release patch
  assert_eq 1 "$RC" "exit code" && assert_contains "$OUT" "origin/master"
}

test_rejects_invalid_versions() {
  setup_repo 0.1.0
  local v
  for v in 1.2 v1.2.3 1.2.3-rc1 0.100.0 0.1.100 banana; do
    run_release "$v"
    assert_eq 1 "$RC" "exit code for '$v'" || return 1
  done
  assert_eq "" "$(remote_tags)"
}

test_usage_without_arguments() {
  setup_repo 0.1.0
  run_release
  assert_eq 2 "$RC" "exit code" && assert_contains "$OUT" "Usage"
}

test_dry_run_changes_nothing() {
  setup_repo 0.1.0
  local head
  head=$(git rev-parse HEAD)
  run_release minor --dry-run
  assert_eq 0 "$RC" "exit code ($OUT)" &&
    assert_contains "$OUT" "v0.2.0" &&
    assert_eq "$head" "$(git rev-parse HEAD)" "HEAD unchanged" &&
    assert_eq "" "$(git tag -l)" "no local tag" &&
    assert_eq "" "$(remote_tags)" "no remote tag" &&
    assert_contains "$(cat gradle.properties)" "VERSION_NAME=0.1.0" &&
    assert_eq false "$([[ -e $GRADLEW_LOG ]] && echo true || echo false)" "gradlew not run"
}

test_failing_tests_abort_release() {
  setup_repo 0.1.0
  local head
  head=$(git rev-parse HEAD)
  GRADLEW_EXIT=1 run_release patch
  assert_eq 1 "$RC" "exit code" &&
    assert_eq "$head" "$(git rev-parse HEAD)" "HEAD unchanged" &&
    assert_eq "" "$(git tag -l)" "no tag" &&
    assert_contains "$(cat gradle.properties)" "VERSION_NAME=0.1.0"
}

# --- runner --------------------------------------------------------------------

filter=${1:-}
for t in $(declare -F | awk '{print $3}' | grep '^test_'); do
  [[ -n "$filter" && "$t" != *"$filter"* ]] && continue
  if ("$t") 2>"$WORK_ROOT/err"; then
    PASSED=$((PASSED + 1))
    printf 'PASS %s\n' "$t"
  else
    FAILED=$((FAILED + 1))
    printf 'FAIL %s\n' "$t"
    cat "$WORK_ROOT/err"
  fi
done
printf '\n%d passed, %d failed\n' "$PASSED" "$FAILED"
[[ $FAILED -eq 0 ]]
