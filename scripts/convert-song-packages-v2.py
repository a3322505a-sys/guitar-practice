#!/usr/bin/env python3
"""Convert old five-file .gpractice packages to the current three-file format.

Keeps score.json and practices.json byte-for-byte. A conversion only removes
obsolete author-maintained review/provenance/hash/capability metadata.
"""
import argparse
import json
import zipfile
from pathlib import Path

CORE = ("manifest.json", "score.json", "practices.json")


def convert(source: Path, target: Path) -> dict:
    with zipfile.ZipFile(source) as archive:
        if archive.testzip():
            raise ValueError(f"Broken ZIP: {source}")
        raw = {name: archive.read(name) for name in CORE}
    manifest = json.loads(raw["manifest.json"])
    if manifest["format"] != "guitar-practice-song":
        raise ValueError(f"Unknown format: {source}")
    if manifest["formatVersion"]["major"] == 2:
        return {"source": str(source), "status": "already-v2"}
    new_manifest = {
        "format": "guitar-practice-song",
        "formatVersion": {"major": 2, "minor": 0},
        "scoreSchemaVersion": manifest["scoreSchemaVersion"],
        "packageId": manifest["packageId"],
        "revision": int(manifest["revision"]) + 1,
        "display": manifest["display"],
    }
    if "extensions" in manifest:
        new_manifest["extensions"] = manifest["extensions"]
    raw["manifest.json"] = json.dumps(new_manifest, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=8) as archive:
        for name in CORE:
            archive.writestr(name, raw[name])
    with zipfile.ZipFile(target) as check:
        assert check.testzip() is None and set(check.namelist()) == set(CORE)
        assert all(check.read(n) == raw[n] for n in CORE)
    return {"source": str(source), "target": str(target), "packageId": new_manifest["packageId"],
            "revision": new_manifest["revision"], "status": "converted"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="A .gpractice or directory of packages")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    inputs = sorted(args.source.glob("*.gpractice")) if args.source.is_dir() else [args.source]
    for path in inputs:
        dst = args.output_dir / (path.stem + "_简化协议.gpractice")
        result = convert(path, dst)
        print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
