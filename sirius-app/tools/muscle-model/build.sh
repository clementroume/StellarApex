#!/usr/bin/env bash
# Regenerates the muscle model from muscles.json. See README.md.
#   ./build.sh                everything: model, catalog, images of both themes
#   ./build.sh code [code..]  model, catalog, and the images of these muscles only
#   ./build.sh --model-only   model and catalog (images of removed muscles are deleted)
#   ./build.sh --list         writes the atlas muscle names to .work/atlas-muscles.txt
set -euo pipefail

cd "$(dirname "$0")"
./fetch.sh

# Isolated from the user's own Blender settings and add-ons.
export BLENDER_USER_RESOURCES="$PWD/.work/blender-user"
BLENDER="$PWD/.work/Blender.app/Contents/MacOS/Blender"
ATLAS="$(find "$PWD/.work/atlas" -name Startup.blend -print -quit)"

run() { # script, arguments
  local script=$1
  shift
  "$BLENDER" -b --disable-autoexec "$ATLAS" --python-exit-code 1 --python "$PWD/$script" -- "$@"
}

if [ "${1:-}" = --list ]; then
  run list_meshes.py
  exit 0
fi

run export_model.py

if [ "${1:-}" = --model-only ]; then
  exit 0
fi

CODES=$(IFS=,; echo "$*")
for THEME in dark light; do
  run render_images.py "$THEME" ${CODES:+"$CODES"}
done
