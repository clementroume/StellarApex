#!/usr/bin/env bash
# Downloads Blender and the Z-Anatomy atlas into .work/ (git-ignored), checking their checksums.
# Safe to run twice: whatever is already there is kept.
set -euo pipefail

cd "$(dirname "$0")"

BLENDER_VERSION=5.2.3
BLENDER_DMG="blender-${BLENDER_VERSION}-macos-arm64.dmg"
BLENDER_URL="https://download.blender.org/release/Blender5.2/${BLENDER_DMG}"
BLENDER_SHA256=acc05374485a272342d4b323c15ac01953e7f697da2f1bad8af9c3bf087267a4

# Last commit that changed Z-Anatomy.zip (2024-06-07): the URL never moves.
ATLAS_COMMIT=bb293be456f6d2245191f37752d14ec76894740d
ATLAS_URL="https://github.com/Z-Anatomy/Models-of-human-anatomy/raw/${ATLAS_COMMIT}/Z-Anatomy.zip"
ATLAS_SHA256=e029688545627bd0214b269e1063143abb580aad72b2c2445d6d8a9a0d9da736

WORK="$PWD/.work"
mkdir -p "$WORK/download" "$WORK/atlas"

check() { # file, expected sha256
  local actual
  actual=$(shasum -a 256 "$1" | cut -d' ' -f1)
  if [ "$actual" != "$2" ]; then
    echo "Checksum mismatch for $1: $actual" >&2
    exit 1
  fi
}

if [ ! -x "$WORK/Blender.app/Contents/MacOS/Blender" ]; then
  [ -f "$WORK/download/$BLENDER_DMG" ] || curl -fL --retry 3 -o "$WORK/download/$BLENDER_DMG" "$BLENDER_URL"
  check "$WORK/download/$BLENDER_DMG" "$BLENDER_SHA256"
  MOUNT=$(mktemp -d)
  hdiutil attach -quiet -nobrowse -readonly -mountpoint "$MOUNT" "$WORK/download/$BLENDER_DMG"
  ditto "$MOUNT/Blender.app" "$WORK/Blender.app"
  hdiutil detach -quiet "$MOUNT"
fi

if [ -z "$(find "$WORK/atlas" -name Startup.blend -print -quit)" ]; then
  [ -f "$WORK/download/Z-Anatomy.zip" ] || curl -fL --retry 3 -o "$WORK/download/Z-Anatomy.zip" "$ATLAS_URL"
  check "$WORK/download/Z-Anatomy.zip" "$ATLAS_SHA256"
  unzip -q -o "$WORK/download/Z-Anatomy.zip" -d "$WORK/atlas"
fi

echo "Blender: $("$WORK/Blender.app/Contents/MacOS/Blender" --version | head -1)"
echo "Atlas:   $(find "$WORK/atlas" -name Startup.blend -print -quit)"
