"""Inspect actual APK DEX/assets and optional frozen baseline comparisons. No CI gate.

python scripts/audit-runtime-isolation.py --apk output/app.apk --debug-apk app/build/outputs/apk/debug/app-debug.apk --baseline .local/baseline --output output/isolation-audit.json
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
PROFILE_ASSETS = {"assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"}


def debug_type_roots():
    """Derive the denylist from the whole physically isolated source set."""
    names = set()
    for path in (ROOT / "app/src/debug/java").rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        package = re.search(r"^package (\S+)", source, re.MULTILINE).group(1).replace(".", "/")
        declarations = re.findall(r"^\s*(?:(?:internal|private|public|data|enum|sealed|open|abstract|value|annotation)\s+)*(?:class|interface|object)\s+(\w+)", source, re.MULTILINE)
        declarations.append(path.stem + "Kt")
        names.update("L" + package + "/" + name for name in declarations)
    return names


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
    assert not legacy, f"Release still references legacy DEX types: {legacy}"
    assert not pcm, f"Release contains comparison PCM assets: {pcm}"
    for name in ["alphatab/AlphaPlayback", "alphatab/EngineProjection", "alphatab/GpPracticeActivity", "audio/Progress", "practice/Session", "practice/Cursor"]:
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
        expected = ["audio/Player", "audio/Pcm", "audio/Sampler", "practice/compile/TempoMap", "ui/TablatureKt", "LegacyWorkbenchTestActivity"]
        assert all(PACKAGE + name + ";" in debug_classes for name in expected), "Debug comparison implementation is missing"
        assert any(name.endswith(".pcm") for name in debug_assets), "Debug comparison samples are missing"
        result["debug_legacy_classes"] = sorted(t for t in debug_classes if t.split("$")[0].removesuffix(";") in debug_roots)
        result["debug_pcm_count"] = sum(name.endswith(".pcm") for name in debug_assets)
    if args.baseline:
        _, _, _, old_assets, old_resources = inspect(args.baseline / "app-release-unsigned.apk")
        retained = {name: digest for name, digest in old_assets.items()
                    if not name.startswith(("assets/guitar/", "assets/guitar-nylon/"))
                    and name not in PROFILE_ASSETS
                    and name != "assets/third-party/practice-soundfont.md"}
        assert all(assets.get(name) == digest for name, digest in retained.items()), "An active asset changed"
        assert all(name in assets for name in PROFILE_ASSETS if name in old_assets), "Missing rebuilt DEX optimization profile"
        assert resources == old_resources, "A packaged Android resource or native library changed"
        frozen = json.loads((args.baseline / "inventory.json").read_text(encoding="utf-8"))
        prefix = "com/a3322505a/guitarlearning/alphatab/"
        classes = {name: digest for name, digest in frozen["release_classes"].items() if name.startswith(prefix)}
        class_root = ROOT / "app/build/tmp/kotlin-classes/release"
        differences = [name for name, digest in classes.items()
                       if not (class_root / name).is_file() or sha((class_root / name).read_bytes()) != digest]
        # Require original v95 source and render comparisons for Compose UI hosts;
        # retain strict byte equality for playback, projection and adapter classes.
        ui_hosts = {prefix + name for name in ("GpPracticeActivity.class", "GpPracticeActivityKt.class", "NativeScoreKt.class")}
        assert not set(differences) - ui_hosts, f"alphaTab core bytecode changed: {differences}"
        source_prefix = "main/java/com/a3322505a/guitarlearning/alphatab/"
        integration_sources = {name: digest for name, digest in frozen["sources"].items() if name.startswith(source_prefix)}
        assert all(sha((ROOT / "app/src" / name).read_bytes()) == digest for name, digest in integration_sources.items()), "A v95 alphaTab integration source changed"
        pcm_baseline = json.loads((args.baseline / "pcm.json").read_text(encoding="utf-8"))
        assert all(sha((ROOT / "output" / name).read_bytes()) == digest for name, digest in pcm_baseline.items()), "Debug comparison PCM changed"
        ui_images = frozen.get("ui_images", {})
        assert all(sha((ROOT / name).read_bytes()) == digest for name, digest in ui_images.items()), "Baseline UI render changed"
        synthesis = frozen.get("synthesis_outputs", {})
        assert all(sha((ROOT / name).read_bytes()) == digest for name, digest in synthesis.items()), "alphaTab synthesis output changed"
        result.update(retained_assets_identical=len(retained), android_resources_and_libraries_identical=len(resources),
                      dexopt_profiles_rebuilt=sorted(name for name in PROFILE_ASSETS if old_assets.get(name) != assets.get(name)),
                      alphatab_classfiles_identical=len(classes) - len(differences),
                      alphatab_ui_host_classfiles_recompiled=differences,
                      alphatab_source_files_identical=len(integration_sources), debug_pcm_outputs_identical=len(pcm_baseline),
                      ui_renders_identical=len(ui_images), alphatab_synthesis_outputs_identical=len(synthesis))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--debug-apk", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path)
    options = parser.parse_args()
    report = audit(options)
    serialized = json.dumps(report, ensure_ascii=False, indent=2)
    if options.output:
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(serialized + "\n", encoding="utf-8")
    print(serialized)
