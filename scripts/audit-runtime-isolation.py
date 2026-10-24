"""Inspect actual APK DEX/assets for retired playback/probe classes and PCM. Required in CI.

python scripts/audit-runtime-isolation.py --apk output/app.apk --debug-apk app/build/outputs/apk/debug/app-debug.apk --output output/isolation-audit.json
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "Lcom/a3322505a/guitarlearning/"


def debug_type_roots():
    """Frozen retired roots: deleting their sources must not empty the APK denylist."""
    return set(json.loads((ROOT / "scripts/runtime-retired-types.json").read_text()))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def dex_index(data):
    assert data[:4] == b"dex\n", "Expected uncompressed standard DEX"
    u32 = lambda at: struct.unpack_from("<I", data, at)[0]
    string_count, string_offset = u32(56), u32(60)
    strings = []
    for i in range(string_count):
        at = u32(string_offset + i * 4)
        while data[at] & 128:
            at += 1
        at += 1
        strings.append(data[at:data.index(0, at)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    defined = {types[u32(u32(100) + i * 32)] for i in range(u32(96))}
    methods = set()
    for i in range(u32(88)):
        owner, _, name = struct.unpack_from("<HHI", data, u32(92) + i * 8)
        methods.add((types[owner], strings[name]))
    return set(types), defined, methods


def inspect(apk):
    types, defined, methods = set(), set(), set()
    with zipfile.ZipFile(apk) as archive:
        assets = {name: sha(archive.read(name)) for name in archive.namelist() if name.startswith("assets/") and not name.endswith("/")}
        resources = {name: sha(archive.read(name)) for name in archive.namelist()
                     if name.startswith(("res/", "lib/")) or name == "resources.arsc"}
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                t, d, m = dex_index(archive.read(name))
                types.update(t)
                defined.update(d)
                methods.update(m)
    return types, defined, methods, assets, resources


def audit(args):
    types, defined, methods, assets, resources = inspect(args.apk)
    debug_roots = debug_type_roots()
    legacy = sorted(t for t in types if t.split("$")[0].removesuffix(";") in debug_roots)
    pcm = sorted(name for name in assets if name.startswith(("assets/guitar/", "assets/guitar-nylon/")) or name.endswith(".pcm"))
    assert not legacy, f"APK still references legacy DEX types: {legacy}"
    assert not pcm, f"APK contains comparison PCM assets: {pcm}"
    for name in ["alphatab/AlphaPlayback", "alphatab/EngineProjection", "audio/Progress", "practice/Session", "practice/Cursor"]:
        assert PACKAGE + name + ";" in defined, f"Missing active type: {name}"
    assert "LalphaTab/AlphaTabView;" in defined, "Missing actual native alphaTab view"
    old_session_methods = {"getClock", "getTotalFrames", "frame", "tick", "sourceFrames", "loopAt", "localTick", "resumeTick", "loopStartFrame", "noteFrame", "activeNotes", "movingPositions"}
    assert not {name for owner, name in methods if owner == PACKAGE + "practice/Session;"} & old_session_methods
    result = {
        "apk": str(args.apk.resolve()), "apk_sha256": sha(args.apk.read_bytes()),
        "defined_classes": len(defined), "legacy_type_references": legacy,
        "comparison_pcm_assets": pcm, "soundfont_sha256": assets["assets/sonivox.sf2"],
        "minification": False, "resource_shrinking": False,
    }
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert "isMinifyEnabled = false" in gradle
    assert "isShrinkResources = false" in gradle
    if args.debug_apk:
        _, debug_classes, _, debug_assets, _ = inspect(args.debug_apk)
        legacy_debug = sorted(t for t in debug_classes if t.split("$")[0].removesuffix(";") in debug_roots)
        debug_pcm = sorted(name for name in debug_assets if name.endswith(".pcm"))
        assert not legacy_debug, f"Debug references retired implementations: {legacy_debug}"
        assert not debug_pcm, f"Debug contains retired samples: {debug_pcm}"
        assert "LalphaTab/AlphaTabView;" in debug_classes, "Debug must use the native score host"
        result["debug_legacy_classes"] = legacy_debug
        result["debug_pcm_count"] = len(debug_pcm)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--debug-apk", type=Path)
    parser.add_argument("--output", type=Path)
    options = parser.parse_args()
    report = audit(options)
    serialized = json.dumps(report, ensure_ascii=False, indent=2)
    if options.output:
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(serialized + "\n", encoding="utf-8")
    print(serialized)
