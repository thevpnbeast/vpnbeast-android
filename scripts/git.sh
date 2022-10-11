#!/bin/bash

function git_global_settings() {
    git config --global user.name "${USERNAME}"
    git config --global user.email "${EMAIL}"
}

function git_commit_and_push() {
    git --no-pager diff
    git add --all
    git commit -am "[ci-skip] version v${RELEASE_VERSION} released"
    git tag -a "v${RELEASE_VERSION}" -m "v${RELEASE_VERSION} tagged"
    git status
    git push --follow-tags "${PUSH_URL}" HEAD:"${BRANCH}"
}

function get_release_version() {
    local version_major, version_minor, version_patch
    version_major=$(grep 'VERSION_MAJOR' version.properties | awk -F '=' '{ print $2 }')
    version_minor=$(grep 'VERSION_MINOR' version.properties | awk -F '=' '{ print $2 }')
    version_patch=$(grep 'VERSION_PATCH' version.properties | awk -F '=' '{ print $2 }')
    echo "${version_major}.${version_minor}.${version_patch}"
}

set -ex
PROJECT_NAME=vpnbeast-android
USERNAME=vpnbeast-ci
GIT_ACCESS_TOKEN=${1}
EMAIL=info@thevpnbeast.com
BRANCH=master
PUSH_URL=https://${USERNAME}:${GIT_ACCESS_TOKEN}@github.com/vpnbeast/${PROJECT_NAME}.git
RELEASE_VERSION=$(get_release_version)

git_global_settings
git_commit_and_push