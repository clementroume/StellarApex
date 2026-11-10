# Muscle model pipeline

Regenerates the 3D muscle model of the muscle pages and its images from one table, `muscles.json`.
macOS arm64 only (`fetch.sh` downloads that Blender build).

| Input | Exact version |
|---|---|
| Blender | 5.2.3 (`blender-5.2.3-macos-arm64.dmg`, sha256 pinned in `fetch.sh`) |
| Z-Anatomy atlas | `Z-Anatomy.zip`, commit `bb293be456f6d2245191f37752d14ec76894740d` of [Models-of-human-anatomy](https://github.com/Z-Anatomy/Models-of-human-anatomy), sha256 `e029688545627bd0214b269e1063143abb580aad72b2c2445d6d8a9a0d9da736` |

Neither enters the repository: `fetch.sh` puts them in the git-ignored `.work/` (about 1.6 GB once unpacked).
Only the muscles and bones of the atlas are used; it also holds non-commercial models (inner ear, kidney).

## Regenerate everything

```bash
cd sirius-app/tools/muscle-model && ./build.sh
```

About 25 minutes, most of it for the images. It writes:

| Output | Content |
|---|---|
| `public/muscle-model/model-<hash>.glb` | One mesh per muscle part, the other muscles and the bones merged. Draco compressed, about 1 MB. The hash changes with the content |
| `public/muscle-model/images/{dark,light}/<code>.webp` | 480 px, transparent background (image before the 3D takes over, fallback) |
| `public/muscle-model/thumbs/{dark,light}/<code>.webp` | 160 px (lists, tiles of a muscle group) |
| `public/muscle-model/regions/{dark,light}/<region>.webp` | 480 px, every muscle of a region in red (image before the 3D takes over on the catalog home, fallback) |
| `src/app/features/muscles/muscle-model/catalog.json` | Muscle `code` and region to parts, view, zoom and framing |

Commit these outputs. `public/muscle-model/LICENSE.txt` is written by hand: keep it next to them (CC BY-SA 4.0,
everything generated here stays under that licence). The Draco decoder is not generated: `angular.json` copies the one
of the pinned three.js into `muscle-model/draco`.

Other commands:

```bash
./build.sh --model-only        # model and catalog only; deletes the images of muscles gone from the table
./build.sh pectineus deltoids  # model, catalog and the images of these muscles
./build.sh --list              # writes every usable atlas name to .work/atlas-muscles.txt
```

## The table

`muscles.json` is the single source, one line per muscle:

```json
{"code": "biceps-brachii", "meshes": ["Long head of biceps brachii", "Short head of biceps brachii"], "view": "FRONT_LEFT", "zoom": 1.9, "side": "r"}
```

| Field | Meaning |
|---|---|
| `code` | `Muscle.code` of Aldebaran: lower case, dashes, derived once from the medical name when the muscle is created, never changed. Renaming a muscle does not touch it |
| `meshes` | Exact atlas names without `.l` / `.r` (a median muscle such as the diaphragm has no side). A muscle group is the union of its parts |
| `view` | `FRONT`, `BACK`, `SIDE_LEFT`, `FRONT_LEFT` or `BACK_LEFT` |
| `zoom` | Camera distance, as a multiple of the size of the framed muscle. Usually 1.8 to 2.4 |
| `side` | Optional, `"r"` or `"l"`: frames one side only (close-up on a limb) |
| `region` | Top-level rows only (groups, standalone muscles): `THORAX`, `BACK`, `ABDOMEN_PELVIS`, `SHOULDER`, `ARM_FOREARM`, `HIP` or `THIGH_LEG`, the `region` of the catalog. Puts the row in that region of the catalog home |

Part names in the model come from the atlas names (`Long head of biceps brachii` becomes
`long-head-of-biceps-brachii`), so an export never renumbers them.

Atlas quirks the table already handles: the mesh named "Ascending part of trapezius" is the neck portion and
"Descending" the lowest one; the left iliocostalis colli is missing (the build logs `ONE-SIDED`, which is fine). The
oblique and transverse meshes include their aponeurosis, which would hide the rectus abdominis: `trim_abdominals.py`
deletes those faces and moves nothing.

## Adding a muscle

A muscle can be created in the catalog before this procedure: until the model is regenerated, its page shows its
`image_url` or the "no image" icon, without any error.

1. Create the muscle in Sirius (admin) and note its code in the response of `GET /aldebaran/muscles`. It is the medical
   name in lower case with dashes: `Pectineus` gives `pectineus`.
2. Run `./build.sh --list` and search `.work/atlas-muscles.txt` for its name.
3. Add one line to `muscles.json` with that code, the atlas names, a view, a zoom and, for a top-level muscle, its region.
4. Run `./build.sh <code>` (its region image is rendered again too). An unknown code or atlas name stops the build with a message.
5. Check the new image in `public/muscle-model/images/`, then the page with `npm start`.
6. Commit `muscles.json`, the catalog, the new `model-<hash>.glb` (the old one is deleted) and the images.

Removing a muscle: delete its line, run `./build.sh --model-only`, then `./build.sh <another code of its region>` for a
top-level muscle, so that its region image no longer shows it.

Changing the look (materials, sizes): run `./build.sh` in full, so that every image matches.
