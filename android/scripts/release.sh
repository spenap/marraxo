#!/bin/sh
# Tag main, build the APK signed with the release key, and publish it as a
# GitHub Release (installed on the S25 through Obtainium).
# Needs: a clean main equal to origin/main, CI green on it, `gh` logged in,
# and the key in ~/.config/makofi (release.jks, release.pass).
# Usage: scripts/release.sh v0.1.0
set -eu
cd "$(dirname "$0")/.."
v=${1:-}
key=${MAKOFI_KEY_DIR:-$HOME/.config/makofi}

echo "$v" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$' || { echo "Usage: $0 vX.Y.Z"; exit 1; }
[ -r "$key/release.jks" ] && [ -r "$key/release.pass" ] || { echo "No release key in $key"; exit 1; }
[ "$(git branch --show-current)" = main ] || { echo "Not on main"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Uncommitted changes"; exit 1; }
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "main differs from origin/main"; exit 1; }
git rev-parse -q --verify "refs/tags/$v" >/dev/null && { echo "$v already exists"; exit 1; }
ci=$(gh run list --branch main --limit 1 --json status,conclusion --jq '.[0].status + " " + .[0].conclusion')
[ "$ci" = "completed success" ] || { echo "Latest CI run on main: $ci"; exit 1; }

git tag -a "$v" -m "$v"
trap 'git tag -d "$v" >/dev/null' EXIT # until the tag is pushed
MAKOFI_KEYSTORE="$key/release.jks" MAKOFI_KEYSTORE_PASSWORD="$(cat "$key/release.pass")" \
    ./gradlew assembleRelease -q --console=plain
apk=app/build/outputs/apk/release/makofi-$v.apk
cp app/build/outputs/apk/release/app-release.apk "$apk"
apksigner=$(ls -d "$HOME"/Android/Sdk/build-tools/*/apksigner | tail -1)
"$apksigner" verify --print-certs "$apk" | grep -q "O=Makofi" || { echo "$apk isn't signed with the release key"; exit 1; }

git push -q origin "$v"
trap - EXIT
gh release create "$v" "$apk" --title "$v" --generate-notes
