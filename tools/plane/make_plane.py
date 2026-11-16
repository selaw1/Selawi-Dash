"""
Builds the jet shown in flight mode: app/src/main/assets/plane/plane.glb.

The model is made here from simple shapes (no outside asset), so it is covered by the project's
MIT license. Run it again after changing a dimension:

    python3 tools/plane/make_plane.py

Units are metres. The nose points to +z, up is +y, so the left wing is at +x. Material names are
what the app looks for: "Paint" takes the plane colour and "Livery" the accent colour, "NavRed",
"NavGreen", "Strobe" and "Beacon" are the lights it switches, and the node "Gear" folds away once
the plane is in the air.
"""

import json
import math
import os
import struct

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "plane", "plane.glb")

# A sleek business jet: slim body, long pointed nose, swept wings, engines at the back, T-tail.
R = 1.35         # fuselage radius
NOSE = 16.0      # nose tip z
TAIL = -16.0     # tail end z
CABIN_FRONT = 8.5
CABIN_BACK = -5.5
GROUND = -2.95   # wheel bottoms

MATERIALS = {
    # name: (base colour, metallic, roughness, emissive)
    "Paint": ((0.93, 0.94, 0.95), 0.15, 0.38, None),
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
    """Radius and centre height of the fuselage at z: long pointed nose, straight cabin, upswept tail."""
    if z >= CABIN_FRONT:
        t = (z - CABIN_FRONT) / (NOSE - CABIN_FRONT)
        r = R * max(0.0, 1.0 - t ** 1.7) ** 0.6
        return r, -0.3 * t * R
    if z >= CABIN_BACK:
        return R, 0.0
    t = (CABIN_BACK - z) / (CABIN_BACK - TAIL)
    r = R * (1.0 - 0.86 * t ** 1.2)
    # The top line stays level and the underside sweeps up towards the tail.
    return r, R - r


def fuselage(m):
    zs = list(np.linspace(TAIL, CABIN_BACK, 16))[:-1] + list(np.linspace(CABIN_BACK, CABIN_FRONT, 24))[:-1] + list(np.linspace(CABIN_FRONT, NOSE, 20))
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
            mat = "Paint"
            if side > 118:
                mat = "Belly"
            # A stripe in the livery colour along each side, sweeping up towards the tail.
            rise = max(0.0, -z - 4.0) * 3.2
            if -14.5 < z < 11.0 and 80 - rise < side < 89 - rise:
                mat = "Livery"
            # A row of separate windows: every other band of the cabin.
            if -3.4 < z < 6.6 and 62 < side < 72 and i % 2 == 0:
                mat = "Window"
            # Wraparound windscreen.
            if 10.6 < z < 12.4 and 44 < side < 84:
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
        m.tri("Paint", centre, ring0[j], ring0[(j + 1) % nseg], np.array([0, 0, -1.0]))


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


def engine(m, x, y, z_front, k=1.0):
    """A nacelle with an intake lip, a dark fan face with a spinner and an exhaust cone; k scales it."""
    length = 5.0 * k
    prof = [(z_front, 1.05 * k), (z_front - 0.15 * k, 1.22 * k), (z_front - 0.8 * k, 1.3 * k), (z_front - 3.0 * k, 1.22 * k), (z_front - length, 0.82 * k)]
    lathe(m, lambda i: "Metal" if i == 0 else "Engine", prof, (x, y))
    disc(m, "Dark", z_front - 0.35 * k, 1.05 * k, (x, y), 1.0)
    lathe(m, lambda i: "Metal", [(z_front, 0.0), (z_front - 0.35 * k, 0.32 * k), (z_front - 0.5 * k, 0.36 * k)], (x, y), 16)
    lathe(m, lambda i: "Dark", [(z_front - length, 0.78 * k), (z_front - length - 0.9 * k, 0.2 * k)], (x, y), 16)


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
    # Wings: low on the body, strongly swept, with raked winglets in the livery colour.
    wing_root_y = -0.75
    for side in (1, -1):
        st = wing(body, "Paint", [side * 0.95, wing_root_y, 1.8], 5.4, 1.3, 9.8, [side, 0, 0], [0, 1, 0], 35, 4, 0.1)
        tip_le = st[1][0]
        wing(body, "Livery", tip_le + np.array([0, 0.04, 0.0]), 1.3, 0.45, 2.0, [side * 0.38, 1, 0], [side, -0.38, 0], 52, 0, 0.08, SYMMETRIC)
        # Navigation light at the wingtip: red on the left (+x), green on the right.
        light(body, "NavRed" if side > 0 else "NavGreen", tip_le + np.array([side * 0.08, 0.06, -0.6]), 0.2)
        light(body, "Strobe", tip_le + np.array([side * 0.08, 0.06, -1.15]), 0.15)
        # Engines mounted on the rear body, each on a short stub pylon.
        ex = side * (R + 1.05)
        ey = 0.55
        engine(body, ex, ey, -5.6, 0.62)
        box(body, "Paint", [min(side * 0.9, ex - side * 0.2), ey - 0.12, -8.2], [max(side * 0.9, ex - side * 0.2), ey + 0.12, -6.6])
    # T-tail: a tall swept fin in the livery colour with the tailplane on top.
    r_f, cy_f = fuselage_profile(-11.0)
    fin = wing(body, "Livery", [0, cy_f + r_f * 0.85, -9.4], 5.6, 2.3, 4.8, [0, 1, 0], [1, 0, 0], 47, 0, 0.1, SYMMETRIC)
    fin_tip = fin[1][0]
    for side in (1, -1):
        wing(body, "Paint", fin_tip + np.array([side * 0.12, -0.05, -0.2]), 2.3, 1.0, 4.3, [side, 0, 0], [0, 1, 0], 30, -2, 0.09, SYMMETRIC)
    light(body, "Strobe", [0, fuselage_profile(TAIL)[1], TAIL - 0.12], 0.17)
    light(body, "Beacon", [0, R * 1.04 + 0.04, 0.5], 0.22)
    light(body, "Beacon", [0, -R * 1.04 - 0.04, -1.5], 0.22)

    gear = Mesh()
    # Nose gear.
    nose_z = 11.0
    _, ncy = fuselage_profile(nose_z)
    box(gear, "Metal", [-0.1, GROUND + 0.42, nose_z - 0.1], [0.1, ncy - R * 0.85, nose_z + 0.1])
    for x in (-0.2, 0.2):
        wheel(gear, x, GROUND + 0.4, nose_z, 0.4, 0.24)
    # Main gear under the wings, a pair of wheels each.
    for side in (1, -1):
        x = side * 2.3
        box(gear, "Metal", [x - 0.12, GROUND + 0.48, -1.75], [x + 0.12, wing_root_y - 0.2, -1.45])
        for dx in (-0.24, 0.24):
            wheel(gear, x + dx, GROUND + 0.48, -1.6, 0.48, 0.3)
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
