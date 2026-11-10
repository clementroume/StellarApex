"""Removes the aponeuroses of the abdominal wall from Z-Anatomy.

The oblique and transverse meshes include their aponeurosis, up to the midline: drawn as muscle, it would hide the
rectus abdominis. Their fibres stop at its lateral edge: the faces medial to that edge are deleted, nothing is moved.
"""
import bmesh

LAYERS = ("External abdominal oblique muscle", "Internal abdominal oblique muscle", "Transversus abdominis muscle")
RECTUS = "Rectus abdominis muscle"
STEP = 0.01


def _edge_of_rectus(objects_by_name):
    rectus = [objects_by_name[f"{RECTUS}.{s}"] for s in "lr" if f"{RECTUS}.{s}" in objects_by_name]
    edge = {}
    depth = []
    for obj in rectus:
        for vertex in obj.data.vertices:
            world = obj.matrix_world @ vertex.co
            key = round(world.z / STEP)
            edge[key] = max(edge.get(key, 0.0), abs(world.x))
            depth.append(world.y)
    return edge, sum(depth) / len(depth) + 0.05


def _delete_faces(data, indices):
    mesh = bmesh.new()
    mesh.from_mesh(data)
    mesh.faces.ensure_lookup_table()
    bmesh.ops.delete(mesh, geom=[mesh.faces[i] for i in indices], context='FACES')
    mesh.to_mesh(data)
    mesh.free()
    data.update()


def trim_abdominals(objects_by_name):
    edge, front_limit = _edge_of_rectus(objects_by_name)
    low, high = min(edge), max(edge)

    def lateral_edge(z):
        key = round(z / STEP)
        if key < low or key > high:
            return None
        for offset in range(0, 6):
            for candidate in (key - offset, key + offset):
                if candidate in edge:
                    return edge[candidate]
        return None

    for name in LAYERS:
        for side in "lr":
            obj = objects_by_name.get(f"{name}.{side}")
            if obj is None:
                continue
            obj.data = obj.data.copy()  # the two sides may share one mesh
            medial = []
            for polygon in obj.data.polygons:
                center = obj.matrix_world @ polygon.center
                bound = lateral_edge(center.z)
                if bound is not None and center.y < front_limit and abs(center.x) < bound + 0.002:
                    medial.append(polygon.index)
            _delete_faces(obj.data, medial)
            print("TRIMMED", obj.name, len(medial))
