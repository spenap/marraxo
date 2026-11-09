#!/bin/sh
# Tag main, build the APK signed with the release key, and publish it as a
# GitHub Release (installed on the S25 through Obtainium).
# Needs: a clean main equal to origin/main, CI green on it, `gh` logged in,
# and the key in ~/.config/marraxo (release.jks, release.pass).
# The version comes from the Conventional Commits since the last tag
# (git-cliff, cliff.toml): a feat bumps the minor, a fix the patch.
# Usage: scripts/release.sh
set -eu
cd "$(dirname "$0")/.."
[ $# = 0 ] || { echo "Usage: $0 (the version comes from the commits)"; exit 1; }
key=${MARRAXO_KEY_DIR:-$HOME/.config/marraxo}
cliff=$(command -v git-cliff || echo "${CARGO_HOME:-$HOME/.cargo}/bin/git-cliff")
[ -x "$cliff" ] || { echo "No git-cliff: cargo install --locked git-cliff"; exit 1; }
[ -r "$key/release.jks" ] && [ -r "$key/release.pass" ] || { echo "No release key in $key"; exit 1; }
[ "$(git branch --show-current)" = main ] || { echo "Not on main"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Uncommitted changes"; exit 1; }
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "main differs from origin/main"; exit 1; }
ci=$(gh run list --branch main --limit 1 --json status,conclusion --jq '.[0].status + " " + .[0].conclusion')
[ "$ci" = "completed success" ] || { echo "Latest CI run on main: $ci"; exit 1; }
# From the top: in android/, git-cliff counts only the commits that touch it.
v=$(cd .. && "$cliff" --config android/cliff.toml --bumped-version 2>/dev/null)
echo "$v" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$' || { echo "No version from git-cliff: '$v'"; exit 1; }
git rev-parse -q --verify "refs/tags/$v" >/dev/null && { echo "Nothing to release since $v"; exit 1; }
echo "Releasing $v"

git tag -a "$v" -m "$v"
work=$(mktemp -d)
trap 'git tag -d "$v" >/dev/null; rm -rf "$work"' EXIT # until the tag is pushed
# Built from a fresh clone of the tag, as scripts/repro-check.sh rebuilds
# it: nothing from earlier builds in this checkout can leak in.
git clone -q "$(git rev-parse --show-toplevel)" "$work/src"
git -C "$work/src" -c advice.detachedHead=false checkout -q "$v"
(cd "$work/src/android" && MARRAXO_KEYSTORE="$key/release.jks" MARRAXO_KEYSTORE_PASSWORD="$(cat "$key/release.pass")" \
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew assembleRelease -q --no-daemon --console=plain)
apk=app/build/outputs/apk/release/marraxo-$v.apk
mkdir -p "$(dirname "$apk")"
cp "$work/src/android/app/build/outputs/apk/release/app-release.apk" "$apk"
apksigner=$(ls -d "$HOME"/Android/Sdk/build-tools/*/apksigner | tail -1)
"$apksigner" verify --print-certs "$apk" | grep -q "O=Marraxo" || { echo "$apk isn't signed with the release key"; exit 1; }

git push -q origin "$v"
trap 'rm -rf "$work"' EXIT
gh release create "$v" "$apk" --title "$v" --generate-notes
