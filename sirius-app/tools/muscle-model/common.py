"""Helpers shared by the Blender scripts of the muscle model pipeline (see README.md)."""
import json
import os
import re
import sys

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
SIRIUS = os.path.dirname(os.path.dirname(HERE))
PUBLIC = os.path.join(SIRIUS, "public", "muscle-model")
CATALOG = os.path.join(SIRIUS, "src", "app", "features", "muscles", "muscle-model", "catalog.json")
VIEWS = {
    "FRONT": (0, -1, 0.1), "BACK": (0, 1, 0.1), "SIDE_LEFT": (-1, 0, 0.1),
    "FRONT_LEFT": (-0.65, -0.75, 0.15), "BACK_LEFT": (-0.65, 0.75, 0.15),
}
THEMES = ("dark", "light")
# Atlas objects that are labels or landmarks rather than volumes.
SKIP_SUFFIX = (".el", ".er", ".ol", ".or", ".t", ".j", ".g")
SKIP_BONE_WORDS = ("sulcus", "line", "foramen", "fossa", "tubercle")
MIN_FRAMED_SIZE = 0.25
# Regions of the catalog home (Muscle.region): view and zoom of their image.
REGION_FRAMES = {"THORAX": ("FRONT_LEFT", 2.2), "BACK": ("BACK_LEFT", 2.1), "ABDOMEN_PELVIS": ("FRONT", 2.1),
                 "SHOULDER": ("FRONT_LEFT", 2.3), "ARM_FOREARM": ("FRONT_LEFT", 1.8), "HIP": ("BACK_LEFT", 2.0),
                 "THIGH_LEG": ("FRONT_LEFT", 1.75)}


def slug(name):
    """Same rule as Muscle.codeOf in Aldebaran: also names the parts of the model, so they never renumber."""
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def load_table():
    with open(os.path.join(HERE, "muscles.json"), encoding="utf-8") as handle:
        table = json.load(handle)
    codes = [entry["code"] for entry in table]
    duplicates = sorted({code for code in codes if codes.count(code) > 1})
    if duplicates:
        sys.exit(f"Duplicate codes in muscles.json: {duplicates}")
    for entry in table:
        if entry["code"] != slug(entry["code"]):
            sys.exit(f"Malformed code in muscles.json: {entry['code']}")
        if entry["view"] not in VIEWS:
            sys.exit(f"Unknown view for {entry['code']}: {entry['view']} (one of {sorted(VIEWS)})")
        if entry.get("side") not in (None, "l", "r"):
            sys.exit(f"Unknown side for {entry['code']}: {entry['side']}")
        if "region" in entry and entry["region"] not in REGION_FRAMES:
            sys.exit(f"Unknown region for {entry['code']}: {entry['region']} (one of {sorted(REGION_FRAMES)})")
    return table


def regions_of(table):
    """The regions of the catalog home: each is the union of the rows carrying that region."""
    regions = {}
    for entry in table:
        if "region" not in entry:
            continue
        view, zoom = REGION_FRAMES[entry["region"]]
        region = regions.setdefault(entry["region"], {"code": entry["region"].lower(), "meshes": [], "codes": set(),
                                                      "view": view, "zoom": zoom})
        region["meshes"].extend(m for m in entry["meshes"] if m not in region["meshes"])
        region["codes"].add(entry["code"])
    return list(regions.values())


def collect(top_name):
    found = []

    def walk(collection):
        found.extend(collection.objects)
        for child in collection.children:
            walk(child)

    walk(next(c for c in bpy.context.scene.collection.children if c.name.endswith(top_name)))
    return [obj for obj in found if obj.type == 'MESH' and not obj.name.endswith(SKIP_SUFFIX)]


def load_atlas():
    """The muscle and bone objects of the open atlas, without the aponeuroses of the abdominal wall."""
    muscles = collect("Muscular system")
    bones = [b for b in collect("Skeletal system") if not any(w in b.name.lower() for w in SKIP_BONE_WORDS)]
    by_name = {m.name: m for m in muscles}
    sys.path.insert(0, HERE)
    from trim_abdominals import trim_abdominals
    trim_abdominals(by_name)
    return muscles, bones, by_name


def objects_of(name, by_name):
    """The objects of an atlas name: its left and right sides, or the single one of a median muscle (diaphragm)."""
    sided = [by_name[f"{name}.{s}"] for s in "lr" if f"{name}.{s}" in by_name]
    return sided or ([by_name[name]] if name in by_name else [])


def targets_of(entry, by_name):
    """The objects of a muscle. The atlas lacks a few one-sided meshes, but a name found on no side is an error."""
    unknown = [m for m in entry["meshes"] if not objects_of(m, by_name)]
    if unknown:
        sys.exit(f"Unknown atlas meshes for {entry['code']}: {unknown} (see .work/atlas-muscles.txt)")
    missing = [f"{m}.{s}" for m in entry["meshes"] for s in "lr" if m not in by_name and f"{m}.{s}" not in by_name]
    if missing:
        print("ONE-SIDED", entry["code"], missing)
    return [obj for m in entry["meshes"] for obj in objects_of(m, by_name)]


def framing(entry, targets):
    """Centre (Blender axes) and size of what the camera frames: the same for the image and the 3D scene."""
    side = entry.get("side")
    framed = [t for t in targets if not side or t.name.endswith("." + side)] or targets
    corners = [obj.matrix_world @ Vector(c) for obj in framed for c in obj.bound_box]
    low = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    high = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    return (low + high) / 2, max((high - low).length, MIN_FRAMED_SIZE)


def remove_orphan_images(table):
    """Drops the images of muscles and regions that left the table."""
    codes = {entry["code"] for entry in table}
    for kind, known in (("images", codes), ("thumbs", codes), ("regions", {r["code"] for r in regions_of(table)})):
        for theme in THEMES:
            folder = os.path.join(PUBLIC, kind, theme)
            if not os.path.isdir(folder):
                continue
            for name in os.listdir(folder):
                if name.endswith(".webp") and name[:-5] not in known:
                    os.remove(os.path.join(folder, name))
                    print("REMOVED", kind, theme, name)
