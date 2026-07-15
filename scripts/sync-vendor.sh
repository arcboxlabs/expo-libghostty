#!/bin/bash
# Sync vendored Swift sources from the upstream tags pinned in vendor-manifest.json.
#
# CocoaPods cannot consume Swift Package Manager dependencies, so the pure-Swift
# layers of libghostty-spm (GhosttyKit re-export, GhosttyTerminal) and
# MSDisplayLink are vendored here as separate pods that mirror the upstream SPM
# product structure. The pre-built libghostty XCFramework is NOT vendored in
# git — `scripts/download-xcframework.mjs` fetches it at install time.
#
# This vendor layer is a bridge: once React Native / Expo modules can declare
# SPM dependencies, it disappears in favor of the upstream Swift package.

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
VENDOR="$ROOT/ios/vendor"

json() { node -p "JSON.parse(require('fs').readFileSync('vendor-manifest.json','utf8'))$1"; }

GHOSTTY_REPO=$(json "['libghostty-spm'].repo")
GHOSTTY_TAG=$(json "['libghostty-spm'].tag")
MSDL_REPO=$(json "['MSDisplayLink'].repo")
MSDL_TAG=$(json "['MSDisplayLink'].tag")

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "[*] cloning libghostty-spm @ $GHOSTTY_TAG"
git clone --quiet --depth 1 --branch "$GHOSTTY_TAG" "$GHOSTTY_REPO" "$TMP/libghostty-spm"

echo "[*] cloning MSDisplayLink @ $MSDL_TAG"
git clone --quiet --depth 1 --branch "$MSDL_TAG" "$MSDL_REPO" "$TMP/MSDisplayLink"

rm -rf "$VENDOR/GhosttyKit" "$VENDOR/GhosttyTerminal" "$VENDOR/MSDisplayLink"
mkdir -p "$VENDOR"

cp -R "$TMP/libghostty-spm/Sources/GhosttyKit" "$VENDOR/GhosttyKit"
cp -R "$TMP/libghostty-spm/Sources/GhosttyTerminal" "$VENDOR/GhosttyTerminal"
cp -R "$TMP/MSDisplayLink/Sources/MSDisplayLink" "$VENDOR/MSDisplayLink"

# CocoaPods only accepts license files with no extension or .txt/.md/.markdown.
cp "$TMP/libghostty-spm/LICENSE" "$VENDOR/LICENSE-libghostty-spm.txt"
cp "$TMP/MSDisplayLink/LICENSE" "$VENDOR/LICENSE-MSDisplayLink.txt"

cat > "$VENDOR/README.md" << EOF
# Vendored sources — do not edit

Synced by \`scripts/sync-vendor.sh\` from the tags pinned in
\`vendor-manifest.json\`. Edit upstream and re-sync instead.

| Directory | Upstream | Tag |
| --- | --- | --- |
| \`GhosttyKit/\`, \`GhosttyTerminal/\` | ${GHOSTTY_REPO} | ${GHOSTTY_TAG} |
| \`MSDisplayLink/\` | ${MSDL_REPO} | ${MSDL_TAG} |

Licenses: \`LICENSE-libghostty-spm.txt\`, \`LICENSE-MSDisplayLink.txt\` (both MIT).
The \`Frameworks/\` directory is populated at install time by
\`scripts/download-xcframework.mjs\` and is not committed.
EOF

echo "[*] vendored:"
find "$VENDOR" -name '*.swift' | wc -l | xargs echo "    swift files:"
