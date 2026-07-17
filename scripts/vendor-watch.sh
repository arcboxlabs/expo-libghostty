#!/bin/bash
# Compares the pins in vendor-manifest.json against their upstreams and keeps
# a single "vendor drift" issue in sync: opened/updated while any pin is
# behind, closed when everything is current. Dependabot cannot do this —
# the manifest is custom and a bump involves sync-vendor.sh (Swift sources +
# XCFramework checksum) or a build-android-libs.yml run (libghostty-vt).
#
# Needs: gh (authenticated), jq. Set DRY_RUN=1 to print instead of touching
# issues. GH_REPO overrides the issue target (defaults to the current repo).

set -euo pipefail

cd "$(dirname "$0")/.."

# ghostty is tip-based and very active; only nag once the pin is this many
# commits behind (the vt C ABI rarely moves, so distance is just a heuristic).
GHOSTTY_AHEAD_THRESHOLD=${GHOSTTY_AHEAD_THRESHOLD:-150}
LABEL="vendor-watch"

json() { jq -r "$1" vendor-manifest.json; }

spm_pin=$(json '.["libghostty-spm"].tag')
msdl_pin=$(json '.MSDisplayLink.tag')
vt_pin=$(json '.["libghostty-vt"].commit')

spm_latest=$(gh api repos/Lakr233/libghostty-spm/releases --paginate \
  --jq '[.[].tag_name | select(startswith("storage."))] | .[]' |
  sed 's/^storage\.//' | sort -V | tail -1)
msdl_latest=$(gh api repos/Lakr233/MSDisplayLink/tags --jq '.[].name' | sort -V | tail -1)
vt_ahead=$(gh api "repos/ghostty-org/ghostty/compare/${vt_pin}...HEAD" --jq '.ahead_by')

drift=false
lines=()

if [[ "$spm_pin" != "$spm_latest" ]]; then
  drift=true
  lines+=("| libghostty-spm | \`$spm_pin\` | \`$spm_latest\` | \`pnpm sync-vendor\` after bumping the tag, then update the \`storage.$spm_latest\` XCFramework url + sha256 |")
fi
if [[ "$msdl_pin" != "$msdl_latest" ]]; then
  drift=true
  lines+=("| MSDisplayLink | \`$msdl_pin\` | \`$msdl_latest\` | \`pnpm sync-vendor\` after bumping the tag |")
fi
if (( vt_ahead >= GHOSTTY_AHEAD_THRESHOLD )); then
  drift=true
  lines+=("| libghostty-vt | \`${vt_pin:0:12}\` | $vt_ahead commits behind | run \`build-android-libs.yml\` for the new commit, then update the release url + sha256 |")
fi

existing=$(gh issue list --label "$LABEL" --state open --json number --jq '.[0].number // empty')

if [[ "$drift" == false ]]; then
  echo "All vendor pins current (libghostty-spm $spm_pin, MSDisplayLink $msdl_pin, ghostty +$vt_ahead)."
  if [[ -n "$existing" && -z "${DRY_RUN:-}" ]]; then
    gh issue close "$existing" --comment "All vendor pins are current again."
  fi
  exit 0
fi

title="Vendor drift: upstream pins are behind"
body=$(cat <<EOF
The daily vendor watch found pins behind their upstreams.

| Component | Pinned | Latest | Bump path |
| --- | --- | --- | --- |
$(printf '%s\n' "${lines[@]}")

ghostty (libghostty-vt) is $vt_ahead commits ahead of the pin overall.

Vendoring notes live in \`vendor-manifest.json\` and \`ios/vendor/README.md\`;
after any bump, CI must pass (iOS simulator build + android R8 release +
unit tests) and the example app should be eyeballed on both platforms.

_Maintained automatically by \`.github/workflows/vendor-watch.yml\`._
EOF
)

if [[ -n "${DRY_RUN:-}" ]]; then
  echo "--- would file issue ---"
  echo "$title"
  echo "$body"
  exit 0
fi

if [[ -n "$existing" ]]; then
  gh issue edit "$existing" --title "$title" --body "$body"
  echo "Updated issue #$existing."
else
  gh issue create --title "$title" --body "$body" --label "$LABEL"
fi
