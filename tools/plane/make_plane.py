"""
Builds the airliner shown in flight mode: app/src/main/assets/plane/plane.glb.

The model is made here from simple shapes (no outside asset), so it is covered by the project's
MIT license. Run it again after changing a dimension:

    python3 tools/plane/make_plane.py

Units are metres. The nose points to +z, up is +y, so the left wing is at +x. Material names are
what the app looks for: "Livery" takes the chosen colour, "NavRed", "NavGreen", "Strobe" and
"Beacon" are the lights it switches, and the node "Gear" folds away once the plane is in the air.
"""

import json
import math
import os
import struct

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "plane", "plane.glb")

R = 2.0          # fuselage radius
NOSE = 19.0      # nose tip z
TAIL = -19.0     # tail end z
GROUND = -4.3    # wheel bottoms

MATERIALS = {
    # name: (base colour, metallic, roughness, emissive)
    "Body": ((0.93, 0.94, 0.95), 0.15, 0.38, None),
    "Belly": ((0.62, 0.66, 0.71), 0.2, 0.45, None),
    "Window": ((0.03, 0.04, 0.06), 0.0, 0.12, None),
    "Glass": ((0.02, 0.03, 0.05), 0.0, 0.08, None),
    "Livery": ((0.18, 0.42, 1.0), 0.1, 0.32, None),
    "Engine": ((0.80, 0.82, 0.85), 0.55, 0.3, None),
    "Metal": ((0.62, 0.64, 0.66), 0.9, 0.25, None),
    "Dark": ((0.035, 0.035, 0.04), 0.2, 0.6, None),
    "Tire": ((0.03, 0.03, 0.03), 0.0, 0.85, None),
    "NavRed": ((1.0, 0.1, 0.08), 0.0, 0.4, (1.0, 0.05, 0.03)),
    "NavGreen": ((0.1, 1.0, 0.3), 0.0, 0.4, (0.05, 1.0, 0.2)),
    "Strobe": ((1.0, 1.0, 1.0), 0.0, 0.4, (0.2, 0.2, 0.2)),
    "Beacon": ((1.0, 0.15, 0.1), 0.0, 0.4, (0.4, 0.02, 0.01)),
}


class Mesh:
    """Triangles grouped by material, each with its own flat or smooth normals."""

    def __init__(self):
        self.parts = {}

    def _part(self, mat):
        return self.parts.setdefault(mat, {"pos": [], "nor": [], "idx": []})

    def tri(self, mat, a, b, c, outward, normals=None):
        """One triangle, turned so its front faces `outward`."""
        a, b, c = (np.asarray(v, float) for v in (a, b, c))
        n = np.cross(b - a, c - a)
        if np.linalg.norm(n) < 1e-9:
            return
        if np.dot(n, outward) < 0:
            b, c = c, b
            n = -n
            if normals is not None:
                normals = [normals[0], normals[2], normals[1]]
        n = n / np.linalg.norm(n)
        p = self._part(mat)
        base = len(p["pos"])
        p["pos"] += [a, b, c]
        p["nor"] += normals if normals is not None else [n, n, n]
        p["idx"] += [base, base + 1, base + 2]

    def quad(self, mat, a, b, c, d, outward, normals=None):
        if normals is None:
            self.tri(mat, a, b, c, outward)
            self.tri(mat, a, c, d, outward)
        else:
            self.tri(mat, a, b, c, outward, [normals[0], normals[1], normals[2]])
            self.tri(mat, a, c, d, outward, [normals[0], normals[2], normals[3]])


def unit(v):
    v = np.asarray(v, float)
    return v / max(np.linalg.norm(v), 1e-9)


def fuselage_profile(z):
    """Radius and centre height of the fuselage at z: rounded nose, straight cabin, upswept tail."""
    if z >= 12.0:
        t = (z - 12.0) / (NOSE - 12.0)
        r = R * math.sqrt(max(0.0, 1.0 - t ** 2.2))
        return r, -0.35 * t * R
    if z >= -8.0:
        return R, 0.0
    t = (-8.0 - z) / (-8.0 - TAIL)
    r = R * (1.0 - 0.84 * t ** 1.25)
    # The top line stays level and the underside sweeps up towards the tail.
    return r, R - r


def fuselage(m):
    zs = list(np.linspace(TAIL, -8.0, 14)) + list(np.linspace(-7.0, 11.0, 10)) + list(np.linspace(12.0, NOSE, 16))
    seg = 36
    rings = []
    for z in zs:
        r, cy = fuselage_profile(z)
        ring = []
        for j in range(seg):
            th = 2 * math.pi * j / seg  # 0 = top
            ring.append(np.array([r * math.sin(th), cy + r * 1.04 * math.cos(th), z]))
        rings.append((z, r, cy, ring))
    # Smooth normals from the neighbouring points along and around the body.
    grid = np.array([ring for _, _, _, ring in rings])
    nz, nseg = grid.shape[0], grid.shape[1]
    normals = np.zeros_like(grid)
    for i in range(nz):
        for j in range(nseg):
            d_around = grid[i, (j + 1) % nseg] - grid[i, (j - 1) % nseg]
            d_along = grid[min(i + 1, nz - 1), j] - grid[max(i - 1, 0), j]
            n = np.cross(d_around, d_along)
            centre = np.array([0.0, rings[i][2], rings[i][0]])
            if np.dot(n, grid[i, j] - centre) < 0:
                n = -n
            normals[i, j] = unit(n) if np.linalg.norm(n) > 1e-9 else np.array([0, 0, 1.0])
    normals[-1, :] = [0, 0, 1.0]
    for i in range(nz - 1):
        z = (rings[i][0] + rings[i + 1][0]) / 2
        for j in range(nseg):
            th = math.degrees(2 * math.pi * (j + 0.5) / nseg) % 360
            side = min(th, 360 - th)  # 0 top … 180 bottom
            mat = "Body"
            if side > 118:
                mat = "Belly"
            if -9.5 < z < 10.5 and 61 < side < 71:
                mat = "Window"
            if 14.0 < z < 15.6 and 48 < side < 82:
                mat = "Glass"
            a, b = grid[i, j], grid[i, (j + 1) % nseg]
            c, d = grid[i + 1, (j + 1) % nseg], grid[i + 1, j]
            centre = np.array([0.0, rings[i][2], z])
            out = (a + b + c + d) / 4 - centre
            m.quad(mat, a, b, c, d, out, [normals[i, j], normals[i, (j + 1) % nseg], normals[i + 1, (j + 1) % nseg], normals[i + 1, j]])
    # Close the tail with a small flat cap.
    z0, _, cy0, ring0 = rings[0]
    centre = np.array([0.0, cy0, z0])
    for j in range(nseg):
        m.tri("Body", centre, ring0[j], ring0[(j + 1) % nseg], np.array([0, 0, -1.0]))


AIRFOIL = [(0.0, 0.0), (0.06, 0.55), (0.18, 0.92), (0.4, 1.0), (0.7, 0.62), (1.0, 0.0), (0.7, -0.22), (0.4, -0.38), (0.15, -0.33), (0.05, -0.2)]
SYMMETRIC = [(0.0, 0.0), (0.06, 0.6), (0.25, 1.0), (0.6, 0.75), (1.0, 0.0), (0.6, -0.75), (0.25, -1.0), (0.06, -0.6)]


def wing(m, mat, root_le, chord, tip_chord, span, span_dir, up, sweep, lift, thickness, profile=AIRFOIL):
    """A tapered, swept surface from root_le along span_dir; thickness is along `up`."""
    root_le = np.asarray(root_le, float)
    span_dir = unit(span_dir)
    up = unit(up)
    back = np.array([0.0, 0.0, -1.0])
    stations = []
    for f in (0.0, 1.0):
        c = chord + (tip_chord - chord) * f
        le = root_le + span_dir * span * f + back * span * f * math.tan(math.radians(sweep)) + up * span * f * math.tan(math.radians(lift))
        stations.append([le + back * c * x + up * c * thickness * y for x, y in profile])
    n = len(profile)
    for k in range(n):
        a, b = stations[0][k], stations[0][(k + 1) % n]
        c, d = stations[1][(k + 1) % n], stations[1][k]
        mid = (a + b + c + d) / 4
        # Outward: away from the chord line at this point.
        axis = (stations[0][0] + stations[0][n // 2] + stations[1][0] + stations[1][n // 2]) / 4
        out = mid - axis
        out = out - span_dir * np.dot(out, span_dir)
        m.quad(mat, a, b, c, d, out)
    tip = stations[1]
    centre = sum(tip) / n
    for k in range(n):
        m.tri(mat, centre, tip[k], tip[(k + 1) % n], span_dir)
    return stations


def lathe(m, mats, z_profile, centre_xy, seg=24, inward_cap=None):
    """A round body along z from (z, radius) pairs; mats picks a material per band."""
    cx, cy = centre_xy
    rings = []
    for z, r in z_profile:
        rings.append([np.array([cx + r * math.sin(2 * math.pi * j / seg), cy + r * math.cos(2 * math.pi * j / seg), z]) for j in range(seg)])
    for i in range(len(rings) - 1):
        for j in range(seg):
            a, b = rings[i][j], rings[i][(j + 1) % seg]
            c, d = rings[i + 1][(j + 1) % seg], rings[i + 1][j]
            mid = (a + b + c + d) / 4
            out = mid - np.array([cx, cy, mid[2]])
            na, nb, nc, nd = (unit(p - np.array([cx, cy, p[2]])) for p in (a, b, c, d))
            m.quad(mats(i), a, b, c, d, out, [na, nb, nc, nd])
    return rings


def disc(m, mat, z, r, centre_xy, facing, seg=24):
    cx, cy = centre_xy
    c = np.array([cx, cy, z])
    pts = [np.array([cx + r * math.sin(2 * math.pi * j / seg), cy + r * math.cos(2 * math.pi * j / seg), z]) for j in range(seg)]
    for j in range(seg):
        m.tri(mat, c, pts[j], pts[(j + 1) % seg], np.array([0, 0, facing]))


def engine(m, x, y, z_front):
    length = 5.0
    prof = [(z_front, 1.05), (z_front - 0.15, 1.22), (z_front - 0.8, 1.3), (z_front - 3.0, 1.22), (z_front - length, 0.82)]
    lathe(m, lambda i: "Metal" if i == 0 else "Engine", prof, (x, y))
    disc(m, "Dark", z_front - 0.35, 1.05, (x, y), 1.0)
    # Spinner in the intake.
    lathe(m, lambda i: "Metal", [(z_front - 0.0, 0.0), (z_front - 0.35, 0.32), (z_front - 0.5, 0.36)], (x, y), 16)
    # Exhaust cone.
    lathe(m, lambda i: "Dark", [(z_front - length, 0.78), (z_front - length - 0.9, 0.2)], (x, y), 16)


def box(m, mat, lo, hi):
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    c = (lo + hi) / 2
    corners = lambda xs, ys, zs: [np.array([x, y, z]) for x in xs for y in ys for z in zs]
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    faces = [
        ([x0, y0, z0], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1]),
        ([x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0]),
        ([x0, y0, z0], [x0, y0, z1], [x1, y0, z1], [x1, y0, z0]),
        ([x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]),
        ([x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]),
        ([x0, y0, z1], [x0, y1, z1], [x1, y1, z1], [x1, y0, z1]),
    ]
    for f in faces:
        pts = [np.array(p, float) for p in f]
        m.quad(mat, *pts, sum(pts) / 4 - c)


def wheel(m, x, y, z, r=0.62, w=0.42):
    seg = 18
    rings = []
    for xx in (x - w / 2, x + w / 2):
        rings.append([np.array([xx, y + r * math.cos(2 * math.pi * j / seg), z + r * math.sin(2 * math.pi * j / seg)]) for j in range(seg)])
    for j in range(seg):
        a, b = rings[0][j], rings[0][(j + 1) % seg]
        c, d = rings[1][(j + 1) % seg], rings[1][j]
        mid = (a + b + c + d) / 4
        m.quad("Tire", a, b, c, d, mid - np.array([mid[0], y, z]))
    for k, side in ((0, -1.0), (1, 1.0)):
        c = np.array([rings[k][0][0], y, z])
        for j in range(seg):
            m.tri("Metal", c, rings[k][j], rings[k][(j + 1) % seg], np.array([side, 0, 0]))


def light(m, mat, p, r=0.28):
    p = np.asarray(p, float)
    tips = [p + np.array(d) * r for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))]
    for a in (0, 1):
        for b in (2, 3):
            for c in (4, 5):
                m.tri(mat, tips[a], tips[b], tips[c], (tips[a] + tips[b] + tips[c]) / 3 - p)


def build():
    body = Mesh()
    fuselage(body)
    # Main wings: low on the body, swept back, with a little dihedral.
    wing_root_y = -1.15
    for side in (1, -1):
        st = wing(body, "Body", [side * 1.2, wing_root_y, 3.2], 8.0, 2.0, 16.6, [side, 0, 0], [0, 1, 0], 27, 5.5, 0.1)
        tip_le = st[1][0]
        # Winglet.
        wing(body, "Livery", tip_le + np.array([0, 0.05, 0.0]), 2.0, 0.9, 2.2, [side * 0.25, 1, 0], [side, -0.25, 0], 35, 0, 0.08, SYMMETRIC)
        # Navigation light just behind the wingtip: red on the left (+x), green on the right.
        light(body, "NavRed" if side > 0 else "NavGreen", tip_le + np.array([side * 0.1, 0.1, -1.0]))
        light(body, "Strobe", tip_le + np.array([side * 0.1, 0.1, -1.9]), 0.2)
        # Engine under the wing on a pylon.
        ex = side * 6.4
        le_at = 3.2 - (abs(ex) - 1.2) * math.tan(math.radians(27))
        ey = wing_root_y + (abs(ex) - 1.2) * math.tan(math.radians(5.5)) - 1.75
        engine(body, ex, ey, le_at + 2.4)
        box(body, "Body", [ex - 0.18, ey + 0.9, le_at - 3.2], [ex + 0.18, ey + 2.0, le_at + 1.4])
        # Tailplane.
        r_t, cy_t = fuselage_profile(-15.0)
        wing(body, "Body", [side * r_t * 0.7, cy_t + 0.25, -13.4], 4.4, 1.7, 6.3, [side, 0, 0], [0, 1, 0], 33, 7, 0.09, SYMMETRIC)
    # Tail fin in the livery colour.
    r_f, cy_f = fuselage_profile(-12.5)
    wing(body, "Livery", [0, cy_f + r_f * 0.9, -10.6], 7.0, 2.7, 6.5, [0, 1, 0], [1, 0, 0], 40, 0, 0.1, SYMMETRIC)
    light(body, "Strobe", [0, fuselage_profile(TAIL)[1], TAIL - 0.15], 0.22)
    light(body, "Beacon", [0, R * 1.04 + 0.05, 1.0], 0.3)
    light(body, "Beacon", [0, -R * 1.04 - 0.05, -2.0], 0.3)

    gear = Mesh()
    # Nose gear.
    nose_z = 13.4
    _, ncy = fuselage_profile(nose_z)
    box(gear, "Metal", [-0.14, GROUND + 0.6, nose_z - 0.14], [0.14, ncy - R * 0.9, nose_z + 0.14])
    for x in (-0.3, 0.3):
        wheel(gear, x, GROUND + 0.55, nose_z, 0.55, 0.32)
    # Main gear under the wing roots, two wheels each.
    for side in (1, -1):
        x = side * 3.0
        box(gear, "Metal", [x - 0.18, GROUND + 0.62, -1.4], [x + 0.18, wing_root_y - 0.3, -1.0])
        for dx in (-0.36, 0.36):
            for z in (-0.6, -1.8):
                wheel(gear, x + dx, GROUND + 0.62, z)
    return body, gear


def write_glb(meshes, path):
    """Minimal glTF 2.0 binary: one buffer, one node per mesh under a root node."""
    names = list(MATERIALS)
    materials = []
    for name in names:
        col, metal, rough, emis = MATERIALS[name]
        mat = {
            "name": name,
            "pbrMetallicRoughness": {"baseColorFactor": [*col, 1.0], "metallicFactor": metal, "roughnessFactor": rough},
            "doubleSided": True,
        }
        if emis:
            mat["emissiveFactor"] = list(emis)
        materials.append(mat)
    blob = bytearray()
    views, accessors, gltf_meshes = [], [], []

    def add_view(data, target):
        while len(blob) % 4:
            blob.append(0)
        views.append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data), "target": target})
        blob.extend(data)
        return len(views) - 1

    for mesh_name, mesh in meshes:
        prims = []
        for mat, p in mesh.parts.items():
            pos = np.asarray(p["pos"], np.float32)
            nor = np.asarray(p["nor"], np.float32)
            idx = np.asarray(p["idx"], np.uint32)
            pv = add_view(pos.tobytes(), 34962)
            accessors.append({"bufferView": pv, "componentType": 5126, "count": len(pos), "type": "VEC3",
                              "min": pos.min(0).tolist(), "max": pos.max(0).tolist()})
            pa = len(accessors) - 1
            nv = add_view(nor.tobytes(), 34962)
            accessors.append({"bufferView": nv, "componentType": 5126, "count": len(nor), "type": "VEC3"})
            na = len(accessors) - 1
            iv = add_view(idx.tobytes(), 34963)
            accessors.append({"bufferView": iv, "componentType": 5125, "count": len(idx), "type": "SCALAR"})
            ia = len(accessors) - 1
            prims.append({"attributes": {"POSITION": pa, "NORMAL": na}, "indices": ia, "material": names.index(mat)})
        gltf_meshes.append({"name": mesh_name, "primitives": prims})
    while len(blob) % 4:
        blob.append(0)
    doc = {
        "asset": {"version": "2.0", "generator": "Selawi Dash tools/plane/make_plane.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": "Plane", "children": [1, 2]}, {"name": "Airframe", "mesh": 0}, {"name": "Gear", "mesh": 1}],
        "meshes": gltf_meshes,
        "materials": materials,
        "buffers": [{"byteLength": len(blob)}],
        "bufferViews": views,
        "accessors": accessors,
    }
    js = json.dumps(doc, separators=(",", ":")).encode()
    while len(js) % 4:
        js += b" "
    out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
    out += struct.pack("<II", len(js), 0x4E4F534A) + js
    out += struct.pack("<II", len(blob), 0x004E4942) + bytes(blob)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(out)
    return len(out)


if __name__ == "__main__":
    body, gear = build()
    size = write_glb([("Airframe", body), ("Gear", gear)], os.path.normpath(OUT))
    print(f"wrote {os.path.normpath(OUT)} ({size // 1024} KB)")
