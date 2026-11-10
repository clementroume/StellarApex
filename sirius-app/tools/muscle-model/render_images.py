"""Renders the muscle images: target muscle in relief, the rest as glass, transparent background.

Runs inside Blender (see build.sh): render_images.py -- <dark|light> [code,code]. Without codes, every muscle.
Images are published under CC BY-SA 4.0 (public/muscle-model/LICENSE.txt).
"""
import os
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import PUBLIC, VIEWS, framing, load_atlas, load_table, regions_of, targets_of  # noqa: E402

ARGS = sys.argv[sys.argv.index("--") + 1:]
THEME = ARGS[0]
ONLY = set(ARGS[1].split(",")) if len(ARGS) > 1 else None
IMAGE_SIZE, THUMB_SIZE, WEBP_QUALITY = 480, 160, 85
GHOST = {"dark": ((0.42, 0.52, 0.68), 0.55), "light": ((0.20, 0.27, 0.37), 0.72)}
BONE = {"dark": ((0.72, 0.74, 0.76), 0.80), "light": ((0.34, 0.37, 0.42), 0.72)}
# Key light direction in camera space: from the upper left, slightly in front of the muscle.
LIGHT = Vector((-0.62, 0.60, -0.50)).normalized()  # shader camera space looks along +Z
RED = ((0.02, (0.16, 0.008, 0.016)), (0.30, (0.38, 0.025, 0.035)), (0.60, (0.74, 0.075, 0.065)),
       (0.84, (0.95, 0.20, 0.13)), (0.98, (1.0, 0.36, 0.26)))


def glass(name, color, opacity):
    """Only the contours stay visible: 'see-through' on any background."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.surface_render_method = 'BLENDED'
    mat.use_backface_culling = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    weight = nodes.new('ShaderNodeLayerWeight')
    weight.inputs[0].default_value = 0.2
    power = nodes.new('ShaderNodeMath')
    power.operation = 'POWER'
    power.inputs[1].default_value = 2.5
    scale = nodes.new('ShaderNodeMath')
    scale.operation = 'MULTIPLY'
    scale.inputs[1].default_value = opacity
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    emission = nodes.new('ShaderNodeEmission')
    emission.inputs['Color'].default_value = (*color, 1)
    mix = nodes.new('ShaderNodeMixShader')
    out = nodes.new('ShaderNodeOutputMaterial')
    links.new(weight.outputs['Facing'], power.inputs[0])
    links.new(power.outputs[0], scale.inputs[0])
    links.new(scale.outputs[0], mix.inputs[0])
    links.new(transparent.outputs[0], mix.inputs[1])
    links.new(emission.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], out.inputs[0])
    return mat


def relief(name, shades):
    """Opaque muscle: colour from a light direction in camera space, so that every view is lit alike.

    Strong contrast brings out the fibres sculpted in the mesh; the grazing edges are darkened on top.
    """
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    geometry = nodes.new('ShaderNodeNewGeometry')
    to_camera = nodes.new('ShaderNodeVectorTransform')
    to_camera.vector_type = 'NORMAL'
    to_camera.convert_from = 'WORLD'
    to_camera.convert_to = 'CAMERA'
    dot = nodes.new('ShaderNodeVectorMath')
    dot.operation = 'DOT_PRODUCT'
    dot.inputs[1].default_value = LIGHT
    ramp = nodes.new('ShaderNodeValToRGB')
    colors = ramp.color_ramp
    colors.elements[0].position, colors.elements[0].color = shades[0][0], (*shades[0][1], 1)
    colors.elements[1].position, colors.elements[1].color = shades[-1][0], (*shades[-1][1], 1)
    for position, color in shades[1:-1]:
        colors.elements.new(position).color = (*color, 1)
    weight = nodes.new('ShaderNodeLayerWeight')
    weight.inputs[0].default_value = 0.3
    edge = nodes.new('ShaderNodeMath')
    edge.operation = 'SUBTRACT'
    edge.inputs[0].default_value = 1.0
    edge_scale = nodes.new('ShaderNodeMath')
    edge_scale.operation = 'MULTIPLY'
    edge_scale.inputs[1].default_value = 0.55
    multiply = nodes.new('ShaderNodeVectorMath')
    multiply.operation = 'SCALE'
    emission = nodes.new('ShaderNodeEmission')
    out = nodes.new('ShaderNodeOutputMaterial')
    links.new(geometry.outputs['Normal'], to_camera.inputs[0])
    links.new(to_camera.outputs[0], dot.inputs[0])
    gain = nodes.new('ShaderNodeMath')
    gain.operation = 'MULTIPLY'
    gain.inputs[1].default_value = 2.8
    offset = nodes.new('ShaderNodeMath')
    offset.operation = 'SUBTRACT'
    offset.inputs[1].default_value = 0.92
    offset.use_clamp = True
    links.new(dot.outputs['Value'], gain.inputs[0])
    links.new(gain.outputs[0], offset.inputs[0])
    links.new(offset.outputs[0], ramp.inputs[0])
    links.new(weight.outputs['Facing'], edge_scale.inputs[0])
    links.new(edge_scale.outputs[0], edge.inputs[1])
    # Screen-space occlusion digs the grooves between the fibre bundles.
    occlusion = nodes.new('ShaderNodeAmbientOcclusion')
    occlusion.inputs['Distance'].default_value = 0.012
    occlusion.samples = 16
    cavity = nodes.new('ShaderNodeMapRange')
    cavity.inputs['To Min'].default_value = 0.30
    cavity.inputs['To Max'].default_value = 1.0
    deepen = nodes.new('ShaderNodeVectorMath')
    deepen.operation = 'SCALE'
    links.new(occlusion.outputs['AO'], cavity.inputs['Value'])
    links.new(ramp.outputs[0], deepen.inputs[0])
    links.new(cavity.outputs[0], deepen.inputs['Scale'])
    links.new(deepen.outputs[0], multiply.inputs[0])
    links.new(edge.outputs[0], multiply.inputs['Scale'])
    links.new(multiply.outputs[0], emission.inputs[0])
    links.new(emission.outputs[0], out.inputs[0])
    return mat


def setup():
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_EEVEE'
    scene.render.film_transparent = True
    scene.render.resolution_x = scene.render.resolution_y = IMAGE_SIZE
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'WEBP'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.image_settings.quality = WEBP_QUALITY
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.render.use_freestyle = False
    scene.render.use_compositing = False
    scene.render.use_sequencer = False
    data = bpy.data.cameras.new("cam")
    data.lens = 65  # vertical field of view of 31 degrees, as in the 3D scene
    data.clip_start = 0.01
    data.clip_end = 100
    camera = bpy.data.objects.new("cam", data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    return scene, camera


def place(camera, entry, targets):
    center, size = framing(entry, targets)
    camera.location = center + Vector(VIEWS[entry["view"]]).normalized() * (size * entry["zoom"])
    camera.rotation_euler = (center - camera.location).to_track_quat('-Z', 'Y').to_euler()


def write_thumb(image_path, thumb_path):
    image = bpy.data.images.load(image_path)
    image.scale(THUMB_SIZE, THUMB_SIZE)
    image.file_format = 'WEBP'
    image.save(filepath=thumb_path, quality=WEBP_QUALITY)
    bpy.data.images.remove(image)


def main():
    table = load_table()
    unknown = (ONLY or set()) - {entry["code"] for entry in table}
    if unknown:
        sys.exit(f"Codes not in muscles.json: {sorted(unknown)}")
    muscles, bones, by_name = load_atlas()
    keep = {obj.name for obj in muscles + bones}
    for obj in bpy.context.scene.objects:
        obj.hide_render = obj.name not in keep
    scene, camera = setup()
    active, ghost, bone = relief("relief", RED), glass("ghost", *GHOST[THEME]), glass("bone", *BONE[THEME])
    for obj in bones:
        obj.data.materials.clear()
        obj.data.materials.append(bone)
    image_dir, thumb_dir = os.path.join(PUBLIC, "images", THEME), os.path.join(PUBLIC, "thumbs", THEME)
    os.makedirs(image_dir, exist_ok=True)
    os.makedirs(thumb_dir, exist_ok=True)
    def shoot(entry, path):
        targets = targets_of(entry, by_name)
        chosen = {t.name for t in targets}
        for muscle in muscles:
            muscle.data.materials.clear()
            muscle.data.materials.append(active if muscle.name in chosen else ghost)
        place(camera, entry, targets)
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        print("RENDERED", THEME, entry["code"])

    for entry in table:
        if not ONLY or entry["code"] in ONLY:
            shoot(entry, os.path.join(image_dir, f"{entry['code']}.webp"))
            write_thumb(scene.render.filepath, os.path.join(thumb_dir, f"{entry['code']}.webp"))
    region_dir = os.path.join(PUBLIC, "regions", THEME)
    os.makedirs(region_dir, exist_ok=True)
    for region in regions_of(table):
        if not ONLY or region["codes"] & ONLY:
            shoot(region, os.path.join(region_dir, f"{region['code']}.webp"))


main()
