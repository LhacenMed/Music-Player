#!/usr/bin/env bash
# lib/version.sh — version read/bump helpers for gradle.properties.
# Source this file; do not execute directly.
#
# Adapted from Khatmah's version.sh, which parses a `Version` sealed class in
# build.gradle.kts (multiple pre-release tracks, a versionBuild component).
# This project keeps its version as two plain lines in gradle.properties -
# VERSION_NAME (semver) and VERSION_CODE (int, bumped by one each release,
# unrelated to VERSION_NAME's own numbers) - so there is no track/type or
# build-component concept to carry over; only major/minor/patch remain.

# version::read <gradle_properties_file>
# Sets: V_NAME, V_CODE, V_MAJOR, V_MINOR, V_PATCH
version::read() {
    local file="$1"

    V_NAME="$(grep -m1 -E '^VERSION_NAME=' "$file" | cut -d= -f2 | tr -d '[:space:]')"
    V_CODE="$(grep -m1 -E '^VERSION_CODE=' "$file" | cut -d= -f2 | tr -d '[:space:]')"

    if [[ -z "$V_NAME" || -z "$V_CODE" ]]; then
        echo "✗ version::read — could not find VERSION_NAME/VERSION_CODE in ${file}" >&2
        exit 1
    fi

    IFS='.' read -r V_MAJOR V_MINOR V_PATCH <<< "$V_NAME"
    if [[ -z "$V_MAJOR" || -z "$V_MINOR" || -z "$V_PATCH" ]]; then
        echo "✗ version::read — VERSION_NAME '${V_NAME}' is not major.minor.patch" >&2
        exit 1
    fi
}

# version::name <major> <minor> <patch> -> prints "major.minor.patch"
version::name() {
    echo "${1}.${2}.${3}"
}

# version::bump <bump_kind>
# bump_kind: major | minor | patch
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_CODE (in place)
version::bump() {
    local kind="$1"
    case "$kind" in
        major) V_MAJOR=$(( V_MAJOR + 1 )); V_MINOR=0; V_PATCH=0 ;;
        minor) V_MINOR=$(( V_MINOR + 1 )); V_PATCH=0             ;;
        patch) V_PATCH=$(( V_PATCH + 1 ))                        ;;
        *)     echo "✗ version::bump — unknown bump kind: ${kind}" >&2; exit 1 ;;
    esac
    V_CODE=$(( V_CODE + 1 ))
}

# version::write <gradle_properties_file> <name> <code>
# Rewrites VERSION_NAME and VERSION_CODE in place.
version::write() {
    local file="$1" name="$2" code="$3"
    sed -i.bak -E \
        -e "s/^VERSION_NAME=.*/VERSION_NAME=${name}/" \
        -e "s/^VERSION_CODE=.*/VERSION_CODE=${code}/" \
        "$file" \
        || { echo "✗ version::write — sed substitution failed on ${file}" >&2; exit 1; }
    rm -f "${file}.bak"
}
