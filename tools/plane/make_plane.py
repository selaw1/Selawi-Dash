"""
Builds the jet shown in flight mode: app/src/main/assets/plane/plane.glb.

The model is made here from simple shapes (no outside asset), so it is covered by the project's
MIT license. Run it again after changing a dimension:

    python3 tools/plane/make_plane.py

Units are metres. The nose points to +z, up is +y, so the left wing is at +x. Material names are
what the app looks for: "Paint" takes the plane colour and "Livery" the accent colour, "NavRed",
"NavGreen", "Strobe" and "Afterburner" are the lights it switches, and the node "Gear" folds away
once the plane is in the air.
"""

import json
import math
import os
import struct

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "plane", "plane.glb")

# A fighter-style jet: pointed nose, bubble canopy, side intakes, delta wings, twin angled tails and
# one glowing exhaust.
NOSE = 9.6       # nose tip z
TAIL = -9.2      # exhaust z
GROUND = -1.95   # wheel bottoms

MATERIALS = {
    # name: (base colour, metallic, roughness, emissive)
    "Paint": ((0.72, 0.75, 0.78), 0.45, 0.32, None),
    "Livery": ((0.18, 0.42, 1.0), 0.25, 0.3, None),
    "Trim": ((0.16, 0.17, 0.19), 0.3, 0.45, None),
    "Glass": ((0.03, 0.035, 0.05), 0.6, 0.05, None),
    "Frame": ((0.12, 0.12, 0.13), 0.5, 0.4, None),
    "Metal": ((0.55, 0.56, 0.58), 0.95, 0.28, None),
    "Dark": ((0.02, 0.02, 0.025), 0.2, 0.7, None),
    "Tire": ((0.03, 0.03, 0.03), 0.0, 0.85, None),
    "Afterburner": ((1.0, 0.55, 0.2), 0.0, 0.5, (1.0, 0.35, 0.08)),
    "NavRed": ((1.0, 0.1, 0.08), 0.0, 0.4, (1.0, 0.05, 0.03)),
    "NavGreen": ((0.1, 1.0, 0.3), 0.0, 0.4, (0.05, 1.0, 0.2)),
    "Strobe": ((1.0, 1.0, 1.0), 0.0, 0.4, (0.2, 0.2, 0.2)),
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
    """Half-width, half-height and centre height at z: needle nose, wide flat middle, tapering tail."""
    if z >= 4.2:
        t = (z - 4.2) / (NOSE - 4.2)
        f = max(0.0, 1.0 - t ** 1.6) ** 0.75
        return 0.78 * f, 0.72 * f, -0.12 * t
    if z >= 0.5:
        t = (4.2 - z) / 3.7
        return 0.78 + 0.32 * t, 0.72 + 0.05 * t, 0.0
    if z >= -5.6:
        return 1.1, 0.77, 0.0
    t = (-5.6 - z) / (-5.6 - TAIL)
    return 1.1 - 0.5 * t, 0.77 - 0.2 * t, 0.0


def fuselage(m):
    zs = list(np.linspace(TAIL, -5.6, 8))[:-1] + list(np.linspace(-5.6, 4.2, 12))[:-1] + list(np.linspace(4.2, NOSE, 22))
    seg = 36
    rings = []
    for z in zs:
        rx, ry, cy = fuselage_profile(z)
        ring = []
        for j in range(seg):
            th = 2 * math.pi * j / seg  # 0 = top
            # A slightly squarer section in the middle, like a fighter's body.
            sx, cx_ = math.sin(th), math.cos(th)
            k = 0.82
            ring.append(np.array([rx * math.copysign(abs(sx) ** k, sx), cy + ry * math.copysign(abs(cx_) ** k, cx_), z]))
        rings.append((z, rx, cy, ring))
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
            mat = "Paint"
            # Dark radome tip, and an accent stripe down each side from behind the canopy.
            if z > 8.3:
                mat = "Trim"
            if -7.5 < z < 1.8 and 84 < side < 96:
                mat = "Livery"
            a, b = grid[i, j], grid[i, (j + 1) % nseg]
            c, d = grid[i + 1, (j + 1) % nseg], grid[i + 1, j]
            centre = np.array([0.0, rings[i][2], z])
            out = (a + b + c + d) / 4 - centre
            m.quad(mat, a, b, c, d, out, [normals[i, j], normals[i, (j + 1) % nseg], normals[i + 1, (j + 1) % nseg], normals[i + 1, j]])
    return rings


def canopy(m):
    """A bubble canopy: half an ellipsoid on the spine, with a dark frame where it meets the body."""
    z0, z1 = 1.2, 6.4
    seg_u, seg_v = 20, 10
    pts = []
    for i in range(seg_u + 1):
        t = i / seg_u
        z = z1 - (z1 - z0) * t
        # Pointed at the front, rounded at the back.
        prof = math.sin(math.pi * t ** 0.85)
        rx, ry, cy = fuselage_profile(z)
        top = cy + ry * 0.92
        row = []
        for j in range(seg_v + 1):
            a = math.pi * j / seg_v  # 0 left … pi right
            w = rx * 0.62 * prof
            h = 0.62 * prof
            row.append(np.array([w * math.cos(a), top + h * math.sin(a), z]))
        pts.append(row)
    centre_y = fuselage_profile(3.5)[2] + 0.5
    for i in range(seg_u):
        for j in range(seg_v):
            a, b, c, d = pts[i][j], pts[i][j + 1], pts[i + 1][j + 1], pts[i + 1][j]
            mid = (a + b + c + d) / 4
            out = mid - np.array([0.0, centre_y, mid[2]])
            na, nb, nc, nd = (unit(p - np.array([0.0, centre_y, p[2]])) for p in (a, b, c, d))
            m.quad("Frame" if i in (seg_u // 2,) else "Glass", a, b, c, d, out, [na, nb, nc, nd])


def intake(m, side):
    """A boxy air intake on the side of the body, under the wing root, dark inside."""
    x0 = side * 0.75
    x1 = side * 1.38
    y0, y1 = -0.62, 0.12
    z_front, z_back = 2.2, -1.6
    box(m, "Paint", [min(x0, x1), y0, z_back], [max(x0, x1), y1, z_front - 0.15])
    # The opening: a dark face set back behind a thin lip.
    box(m, "Trim", [min(x0, x1) - 0.02, y0 - 0.02, z_front - 0.3], [max(x0, x1) + 0.02, y1 + 0.02, z_front])
    box(m, "Dark", [min(x0, x1) + 0.08, y0 + 0.08, z_front - 0.02], [max(x0, x1) - 0.08, y1 - 0.08, z_front + 0.01])


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


def nozzle(m):
    """The exhaust: a metal nozzle around a glowing core that brightens with throttle."""
    rx, ry, cy = fuselage_profile(TAIL)
    r = (rx + ry) / 2
    lathe(m, lambda i: "Metal", [(TAIL + 0.3, r * 0.98), (TAIL, r * 0.95), (TAIL - 0.9, r * 0.82)], (0.0, cy), 28)
    lathe(m, lambda i: "Dark", [(TAIL - 0.9, r * 0.82), (TAIL - 0.75, r * 0.7)], (0.0, cy), 28)
    disc(m, "Afterburner", TAIL - 0.6, r * 0.7, (0.0, cy), -1.0, 28)


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
    rings = fuselage(body)
    # Close the tail end behind the nozzle.
    canopy(body)
    for side in (1, -1):
        intake(body, side)
        # Delta wings, thin and swept hard, a touch of droop.
        st = wing(body, "Paint", [side * 0.9, -0.15, 1.6], 7.4, 1.0, 4.7, [side, 0, 0], [0, 1, 0], 52, -2, 0.045, SYMMETRIC)
        tip_le = st[1][0]
        # Wingtip rail in the accent colour, with the navigation light at its end.
        box(body, "Livery", [tip_le[0] - 0.07, tip_le[1] - 0.06, tip_le[2] - 2.6], [tip_le[0] + 0.07, tip_le[1] + 0.06, tip_le[2] + 0.4])
        light(body, "NavRed" if side > 0 else "NavGreen", tip_le + np.array([0, 0, 0.5]), 0.12)
        # All-moving tailplanes.
        wing(body, "Paint", [side * 0.85, -0.1, -5.9], 2.9, 0.9, 2.5, [side, 0, 0], [0, 1, 0], 45, -4, 0.05, SYMMETRIC)
        # Twin tails, leaning outwards, in the accent colour.
        lean = math.radians(22)
        wing(body, "Livery", [side * 0.7, 0.6, -4.2], 3.6, 1.1, 3.0, [side * math.sin(lean), math.cos(lean), 0], [math.cos(lean), -side * math.sin(lean), 0], 50, 0, 0.06, SYMMETRIC)
    nozzle(body)
    light(body, "Strobe", [0, 0.95, -6.5], 0.13)

    gear = Mesh()
    nose_z = 6.0
    _, nry, ncy = fuselage_profile(nose_z)
    box(gear, "Metal", [-0.07, GROUND + 0.3, nose_z - 0.07], [0.07, ncy - nry * 0.8, nose_z + 0.07])
    wheel(gear, 0.0, GROUND + 0.28, nose_z, 0.28, 0.16)
    for side in (1, -1):
        x = side * 1.45
        box(gear, "Metal", [x - 0.08, GROUND + 0.36, -2.6], [x + 0.08, -0.35, -2.35])
        wheel(gear, x, GROUND + 0.36, -2.5, 0.36, 0.24)
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
