#!/bin/sh
# check-tdd.sh — Ensures a test file exists before editing production code.
#
# Usage:
#   check-tdd.sh <file-path>
#
# Production source files (non-test, non-config) must have a corresponding
# test file before they can be edited. Exits with code 0 (allowed) if a test
# exists, or code 2 (blocked) with a message if no test is found.
#
# Test location conventions:
#   Frontend: colocated .spec.ts (e.g. foo.service.ts → foo.service.spec.ts)
#             or __test__/app/... mirror
#   Backend:  src/test/kotlin/... mirroring src/main/kotlin/...

set -e

file="$1"

# Allow non-source files (configs, generated, assets, etc.)
case "$file" in
  */.git/*|node_modules/*|__test__/*|*.spec.*|*Test.kt|build/*|dist/*|.gradle/*)
    exit 0 ;;
esac

# Allow frontend non-src files
case "$file" in
  frontend/src/app/*) ;;
  backend/src/main/kotlin/*) ;;
  *) exit 0 ;;  # Not a source file we need to check
esac

# Determine the expected test path
test_file=""

if echo "$file" | grep -q '^frontend/'; then
  # Frontend: colocated .spec.ts
  base="${file%.ts}"
  test_file="${base}.spec.ts"
  if [ ! -f "$test_file" ]; then
    # Try __test__/ mirror
    mirror="$(echo "$file" | sed 's|^frontend/src/app|__test__/app|' | sed 's|\.ts$|.spec.ts|')"
    test_file="$mirror"
  fi
elif echo "$file" | grep -q '^backend/src/main/kotlin/'; then
  # Backend: src/test/kotlin/ mirror
  test_file="$(echo "$file" | sed 's|^backend/src/main/kotlin|backend/src/test/kotlin|' | sed 's|\.kt$|Test.kt|')"
fi

if [ -z "$test_file" ] || [ ! -f "$test_file" ]; then
  echo "TDD BLOCKED: No test found for $file" >&2
  echo "Write a failing test first (red phase), then write the production code." >&2
  echo "Expected test: ${test_file:-<unknown>}" >&2
  exit 2
fi

exit 0
