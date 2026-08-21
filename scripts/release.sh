#!/usr/bin/env bash
#
# release.sh — release console for this project's GitHub-Releases distribution.
#
# Adapted from Khatmah's release.sh: same shape (pick the next version
# interactively, dispatch the cloud pipeline, stream it), simplified to match
# this project's version storage - two plain lines in gradle.properties
# (VERSION_NAME, VERSION_CODE), no pre-release track/type and no separate
# build component to prompt for.
#
# Nothing is built, signed or pushed locally: once the run is dispatched this
# script is a viewer, and closing the terminal (or losing power) has no effect
# on the release.
#
# Run from ANY branch, on ANY device.
#   1. Read the current version from origin/main — the authoritative copy
#   2. Prompt for bump kind and release notes
#   3. Preview + confirm, then dispatch the cloud pipeline
#   4. Stream the run (Ctrl-C is safe — it detaches, it does not cancel)
#
# Usage:
#   ./scripts/release.sh
#
# Requirements: gh (authenticated) and jq. No keystore, no Android SDK.

set -euo pipefail

# ── Bootstrap ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

source "${SCRIPT_DIR}/lib/version.sh"
source "${SCRIPT_DIR}/lib/git.sh"

GRADLE_PROPERTIES="gradle.properties"
MAIN_BRANCH="main"
WORKFLOW="release-github.yml"

# ── Preconditions ───────────────────────────────────────────────────────────────
for cmd in gh jq; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }

REPO="$(git::repo_slug)"
SOURCE_BRANCH="$(git::current_branch)"

# The pipeline builds from origin, so local-only work would silently be left out.
git::ensure_clean
git::ensure_pushed "$SOURCE_BRANCH"

# ── Step 1: Read current version from origin/main ───────────────────────────────
git fetch origin "$MAIN_BRANCH" --quiet
MAIN_PROPERTIES="$(mktemp)"
trap 'rm -f "$MAIN_PROPERTIES"' EXIT
git show "origin/${MAIN_BRANCH}:${GRADLE_PROPERTIES}" > "$MAIN_PROPERTIES"

version::read "$MAIN_PROPERTIES"
CURRENT_NAME="$(version::name "$V_MAJOR" "$V_MINOR" "$V_PATCH")"

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│           Release Pipeline                   │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Current version : ${CURRENT_NAME}  (code ${V_CODE})"
echo "  Source branch   : ${SOURCE_BRANCH}"
echo "  Target branch   : ${MAIN_BRANCH}"
echo "  Runs on         : GitHub Actions"
echo ""

# ── Step 2: Choose bump kind ────────────────────────────────────────────────────
echo "  Version bump:"
echo "    1) patch  — bug fixes          (x.y.Z)"
echo "    2) minor  — new features       (x.Y.0)"
echo "    3) major  — breaking changes   (X.0.0)"
echo ""
read -rp "  Select [1-3, default 1]: " BUMP_CHOICE
BUMP_CHOICE="${BUMP_CHOICE:-1}"

case "$BUMP_CHOICE" in
    1) BUMP_KIND="patch" ;;
    2) BUMP_KIND="minor" ;;
    3) BUMP_KIND="major" ;;
    *) echo "✗ Invalid choice"; exit 1 ;;
esac

version::bump "$BUMP_KIND"
NEW_NAME="$(version::name "$V_MAJOR" "$V_MINOR" "$V_PATCH")"
TAG="v${NEW_NAME}"

# ── Step 3: Release notes ────────────────────────────────────────────────────────
echo ""
read -rp "  Release notes [Bug fixes and improvements.]: " NOTES
NOTES="${NOTES:-Bug fixes and improvements.}"

# ── Step 4: Preview + confirm ────────────────────────────────────────────────────
echo ""
echo "  ┌── Release Preview ────────────────────────────┐"
echo "  │  ${CURRENT_NAME}  →  ${NEW_NAME}  (code ${V_CODE})"
echo "  │  Tag    : ${TAG}"
echo "  │  Notes  : ${NOTES}"
echo "  │  Repo   : ${REPO}"
echo "  └───────────────────────────────────────────────┘"
echo ""

if git::release_published "$TAG"; then
    echo "✗ Release ${TAG} is already published — bump to a different version."; exit 1
fi

read -rp "  Proceed? [y/N]: " CONFIRM
[[ "${CONFIRM,,}" == "y" ]] || { echo "  Aborted."; exit 0; }

# ── Step 5: Dispatch the cloud pipeline ──────────────────────────────────────────
# Remember the newest run id first, so we can identify the one we just created —
# `gh workflow run` does not return it.
echo ""
echo "▶ Dispatching ${WORKFLOW}…"
PREV_RUN="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"

# --ref picks which branch's *workflow definition* runs, not which code is built:
# the pipeline always checks out main and merges the source branch itself. Using
# the source branch keeps "the workflow I can see on my branch is the workflow
# that runs" true — with --ref main, pipeline edits made on dev could never take
# effect until a release had already shipped them.
gh workflow run "$WORKFLOW" \
    --ref "$SOURCE_BRANCH" \
    -f bump="$BUMP_KIND" \
    -f notes="$NOTES" \
    -f source_branch="$SOURCE_BRANCH"

RUN_ID=""
for _ in $(seq 1 20); do
    sleep 2
    RUN_ID="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
    [[ "$RUN_ID" != "$PREV_RUN" && "$RUN_ID" != "0" ]] && break
    RUN_ID=""
done

if [[ -z "$RUN_ID" ]]; then
    echo "  Dispatched, but the run id did not appear in time."
    echo "  Follow it at: https://github.com/${REPO}/actions/workflows/${WORKFLOW}"
    exit 0
fi

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│        ✓ Release running in the cloud        │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Version : ${NEW_NAME}"
echo "  Run     : https://github.com/${REPO}/actions/runs/${RUN_ID}"
echo ""
echo "  Streaming below — Ctrl-C only detaches this terminal,"
echo "  the release finishes on GitHub either way."
echo ""

gh run watch "$RUN_ID" --exit-status || true
