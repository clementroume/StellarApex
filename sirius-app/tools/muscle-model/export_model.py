"""Exports the 3D model and its catalog: one mesh per muscle part, the rest merged as see-through context.

Runs inside Blender (see build.sh). Part names come from the atlas names, so they survive a re-export.
Model and catalog are published under CC BY-SA 4.0 (public/muscle-model/LICENSE.txt).
"""
import glob
import hashlib
import json
import os
import sys

import addon_utils
import bmesh
import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import CATALOG, HERE, PUBLIC, framing, load_atlas, load_table, objects_of, regions_of, remove_orphan_images, slug, targets_of  # noqa: E402

# Share of the polygons kept: muscle parts are shown in relief, the see-through context needs very few.
RATIO_PART, RATIO_CONTEXT, RATIO_BONES = 0.25, 0.04, 0.05
CONTEXT_EXCLUDE = ("fascia", "aponeurosis", "retinaculum", "bursa", "sheath", "tendon")
RESERVED = {"context", "bones"}


def merged(name, objects, ratio):
    """Simplifies each object, then merges them in world coordinates into one new object."""
    target = bmesh.new()
    for obj in objects:
        obj.modifiers.clear()
        obj.modifiers.new("simplify", 'DECIMATE').ratio = ratio
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for obj in objects:
        evaluated = obj.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh().copy()
        mesh.transform(obj.matrix_world)
        target.from_mesh(mesh)
        bpy.data.meshes.remove(mesh)
        evaluated.to_mesh_clear()
    data = bpy.data.meshes.new(name)
    target.to_mesh(data)
    target.free()
    data.polygons.foreach_set("use_smooth", [True] * len(data.polygons))
    data.update()
    result = bpy.data.objects.new(name, data)
    if result.name != name:
        sys.exit(f"Part name taken in the atlas: {name}")
    return result


def to_gltf(vector):
    """Blender is Z-up, the model is exported Y-up."""
    return [round(vector.x, 4), round(vector.z, 4), round(-vector.y, 4)]


def catalog_rows(entries, part_of, frames):
    rows = []
    for entry in entries:
        center, size = frames[entry["code"]]
        row = {"parts": [part_of[m] for m in entry["meshes"]], "view": entry["view"], "zoom": entry["zoom"],
               "center": to_gltf(center), "size": round(size, 4)}
        rows.append(f"    {json.dumps(entry['code'])}: {json.dumps(row, separators=(',', ':'))}")
    return ",\n".join(rows)


def write_catalog(table, regions, part_of, frames, model, triangles):
    text = (f'{{\n  "model": {json.dumps(model)},\n  "triangles": {triangles},\n'
            f'  "muscles": {{\n{catalog_rows(table, part_of, frames)}\n  }},\n'
            f'  "regions": {{\n{catalog_rows(regions, part_of, frames)}\n  }}\n}}\n')
    os.makedirs(os.path.dirname(CATALOG), exist_ok=True)
    with open(CATALOG, "w", encoding="utf-8") as handle:
        handle.write(text)


def export(built):
    keep = {obj.name for obj in built}
    for obj in list(bpy.data.objects):
        if obj.name not in keep:
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in built:
        bpy.context.scene.collection.objects.link(obj)
    work = os.path.join(HERE, ".work", "model.glb")
    bpy.ops.export_scene.gltf(
        filepath=work, export_format='GLB', export_materials='NONE', export_cameras=False, export_lights=False,
        export_animations=False, export_skins=False, export_morph=False, export_texcoords=False, export_normals=True,
        export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=6,
        export_draco_position_quantization=14, export_draco_normal_quantization=10)
    with open(work, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()[:8]
    # The name changes with the content: browsers can cache it for good (see nginx.conf).
    name = f"model-{digest}.glb"
    os.makedirs(PUBLIC, exist_ok=True)
    for old in glob.glob(os.path.join(PUBLIC, "model-*.glb")):
        os.remove(old)
    os.replace(work, os.path.join(PUBLIC, name))
    print("MODEL", name, round(os.path.getsize(os.path.join(PUBLIC, name)) / 1e6, 2), "MB")
    return name


def main():
    addon_utils.enable("io_scene_gltf2", default_set=False)
    table = load_table()
    muscles, bones, by_name = load_atlas()

    # Framing is measured before any simplification, as for the images.
    regions = regions_of(table)
    frames = {entry["code"]: framing(entry, targets_of(entry, by_name)) for entry in table + regions}

    bases = sorted({mesh for entry in table for mesh in entry["meshes"]})
    part_of = {base: slug(base) for base in bases}
    if len(set(part_of.values())) != len(part_of) or RESERVED & set(part_of.values()):
        sys.exit("Two atlas meshes give the same part name")
    in_parts = {obj.name for base in bases for obj in objects_of(base, by_name)}
    context = [m for m in muscles if m.name not in in_parts and not any(w in m.name.lower() for w in CONTEXT_EXCLUDE)]
    print("PARTS", len(bases), "CONTEXT", len(context), "BONES", len(bones))

    built = [merged(part_of[b], objects_of(b, by_name), RATIO_PART) for b in bases]
    built.append(merged("context", context, RATIO_CONTEXT))
    built.append(merged("bones", bones, RATIO_BONES))
    triangles = sum(sum(len(p.vertices) - 2 for p in obj.data.polygons) for obj in built)
    print("TRIANGLES", triangles, "OBJECTS", len(built))

    write_catalog(table, regions, part_of, frames, export(built), triangles)
    remove_orphan_images(table)
    print("CATALOG", len(table), "muscles")


main()
