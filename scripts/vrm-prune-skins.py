#!/usr/bin/env python3
"""
vrm-prune-skins.py — make a VRM/GLB loadable by Filament's gltfio by pruning each skin's joint list
to the joints its mesh actually uses.

Why: VRoid / UniVRM 0.x exports bind EVERY mesh to the WHOLE skeleton (e.g. 101 skins × 262 joints),
and Filament's skinning refuses skins with more than 256 bones (`PreconditionPanic: bone count > 256`
in gltfio's AssetLoader::createAsset). Most meshes reference only a handful of joints, so dropping the
unused ones is lossless: the same vertices keep the same bones and weights.

What it changes (in place, no buffer growth):
  * skin.joints            → only the joints referenced with weight > 0 by the skin's meshes (order kept)
  * skin.inverseBindMatrices → the matching subset, rewritten over the first N matrices; count = N
  * JOINTS_0 accessors      → indices remapped to the new joint order (same component type)
  * the JSON chunk is re-serialized and re-padded; the GLB header lengths are fixed up.
Nodes, meshes, materials, textures, morph targets and the VRM extensions are untouched.

Usage: scripts/vrm-prune-skins.py <in.vrm> <out.vrm>
Exit 1 if any skin still needs more than 256 joints (that mesh would have to be split).
"""
import json
import struct
import sys

MAX_BONES = 256
CTYPE = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def main(src: str, dst: str) -> int:
    data = bytearray(open(src, "rb").read())
    magic, version, total = struct.unpack_from("<4sII", data, 0)
    assert magic == b"glTF" and version == 2, "not a GLB 2.0 file"
    json_len, json_type = struct.unpack_from("<I4s", data, 12)
    assert json_type == b"JSON"
    gltf = json.loads(bytes(data[20:20 + json_len]).decode("utf-8"))
    bin_header = 20 + json_len
    bin_len, bin_type = struct.unpack_from("<I4s", data, bin_header)
    assert bin_type == b"BIN\x00"
    assert bin_len % 4 == 0 and bin_header + 8 + bin_len <= total, "BIN chunk must be 4-byte aligned and inside the file"
    if bin_header + 8 + bin_len < total:
        print(f"note: {total - (bin_header + 8 + bin_len)} trailing bytes after the BIN chunk are dropped", file=sys.stderr)
    bin_off = bin_header + 8

    accessors, views = gltf["accessors"], gltf["bufferViews"]

    def layout(ai):
        a = accessors[ai]
        assert "sparse" not in a and "bufferView" in a, f"accessor {ai}: sparse / view-less accessors unsupported"
        v = views[a["bufferView"]]
        assert v.get("buffer", 0) == 0, f"accessor {ai}: only the embedded BIN buffer (0) is supported"
        fmt, size = CTYPE[a["componentType"]]
        n = NCOMP[a["type"]]
        stride = v.get("byteStride") or size * n
        base = bin_off + v.get("byteOffset", 0) + a.get("byteOffset", 0)
        return a, fmt, n, stride, base

    def read(ai):
        a, fmt, n, stride, base = layout(ai)
        return [struct.unpack_from("<" + fmt * n, data, base + i * stride) for i in range(a["count"])]

    def write(ai, rows):
        a, fmt, n, stride, base = layout(ai)
        for i, row in enumerate(rows):
            struct.pack_into("<" + fmt * n, data, base + i * stride, *row)

    # skin → the JOINTS_0 / WEIGHTS_0 accessors of every primitive skinned by it.
    skin_prims = {}
    for node in gltf["nodes"]:
        if "mesh" in node and "skin" in node:
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                attrs = prim["attributes"]
                if "JOINTS_0" in attrs:
                    skin_prims.setdefault(node["skin"], set()).add((attrs["JOINTS_0"], attrs["WEIGHTS_0"]))

    # Every JOINTS_0 / inverseBindMatrices accessor must belong to exactly one skin: they are rewritten in
    # place with that skin's joint map, so sharing would corrupt the other skin.
    joint_acc_owner = {}
    for si, prims in skin_prims.items():
        for ja, _ in prims:
            assert joint_acc_owner.setdefault(ja, si) == si, f"JOINTS_0 accessor {ja} shared by skins {joint_acc_owner[ja]} and {si}"
    ibms = [s.get("inverseBindMatrices") for s in gltf["skins"] if "inverseBindMatrices" in s]
    assert len(ibms) == len(set(ibms)), "inverseBindMatrices accessor shared between skins"

    remapped_joint_accessors = set()
    worst = 0
    for si, skin in enumerate(gltf["skins"]):
        prims = skin_prims.get(si, set())
        used = set()
        for ja, wa in prims:
            for joints, weights in zip(read(ja), read(wa)):
                used.update(joints[k] for k in range(4) if weights[k] > 0)
        old_joints = skin["joints"]
        keep = [i for i in range(len(old_joints)) if i in used] or [0]
        worst = max(worst, len(keep))
        if len(keep) == len(old_joints):
            continue
        old_to_new = {old: new for new, old in enumerate(keep)}
        for ja, _ in prims:
            if ja in remapped_joint_accessors:
                continue
            remapped_joint_accessors.add(ja)
            rows = read(ja)
            for row in rows:
                assert all(j < len(old_joints) for j in row), f"JOINTS_0 accessor {ja} references a joint outside skin {si}"
            write(ja, [tuple(old_to_new.get(j, 0) for j in row) for row in rows])  # zero-weight slots → joint 0
            accessors[ja].pop("min", None)  # stale after remapping; gltfio ignores them, validators don't
            accessors[ja].pop("max", None)
        ibm = skin.get("inverseBindMatrices")
        if ibm is not None:
            mats = read(ibm)
            write(ibm, [mats[i] for i in keep])
            accessors[ibm]["count"] = len(keep)
            accessors[ibm].pop("min", None)
            accessors[ibm].pop("max", None)
        skin["joints"] = [old_joints[i] for i in keep]
        print(f"skin {si:3d} {skin.get('name', '')!s:24.24}: {len(old_joints)} → {len(keep)} joints")

    if worst > MAX_BONES:
        print(f"ERROR: a skin still needs {worst} joints (> {MAX_BONES}); split that mesh", file=sys.stderr)
        return 1

    # Re-serialize the JSON chunk (compact, space-padded to 4 bytes) and fix both length fields.
    new_json = json.dumps(gltf, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    new_json += b" " * (-len(new_json) % 4)
    out = bytearray()
    out += struct.pack("<4sII", b"glTF", 2, 12 + 8 + len(new_json) + 8 + bin_len)
    out += struct.pack("<I4s", len(new_json), b"JSON") + new_json
    out += data[bin_header:bin_header + 8 + bin_len]
    open(dst, "wb").write(out)
    print(f"wrote {dst}: {len(out)} bytes (was {total}); max joints per skin now {worst}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
