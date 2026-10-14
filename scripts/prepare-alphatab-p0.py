"""Prepare private desktop GP fixtures and an independent GPIF reference, never tracked in Git."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

PROJECT = Path(__file__).resolve().parents[1]


def gpif_reference(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        name = next(n for n in archive.namelist() if n.lower().endswith("score.gpif"))
        root = ET.fromstring(archive.read(name))
    tables = {name: {n.attrib["id"]: n for n in root.findall(f"./{name}/*")} for name in ("Bars", "Voices", "Beats", "Notes", "Rhythms")}
    masterbars = root.findall("./MasterBars/MasterBar")
    tracks = root.findall("./Tracks/Track")
    staff_offset = 0
    all_tracks = []
    note_lines = []
    guitar_indices = []
    for track_index, track in enumerate(tracks):
        staves = track.findall("./Staves/Staff") or [track]
        instrument = track.findtext("./InstrumentSet/Type", "")
        percussion = instrument.lower() in {"drumkit", "percussion"} or track.findtext("./MidiConnection/PrimaryChannel") == "9"
        guitar = not percussion and "guitar" in instrument.lower()
        if guitar:
            guitar_indices.append(track_index)
        stat = {"index": track_index, "name": track.findtext("Name", ""), "instrumentType": instrument,
                "percussion": percussion, "staves": []}
        for staff_index, staff in enumerate(staves):
            def prop(name: str, child: str) -> str:
                value = staff.findtext(f"./Properties/Property[@name='{name}']/{child}")
                return value or track.findtext(f"./Properties/Property[@name='{name}']/{child}", "")
            tuning = [int(n) for n in prop("Tuning", "Pitches").split()]
            capo = int(prop("CapoFret", "Fret") or 0)
            poly_bars = 0
            note_count = 0
            max_voices = 0
            poly_indices = []
            for bar_index, master in enumerate(masterbars):
                bar_ids = master.findtext("Bars", "").split()
                if staff_offset >= len(bar_ids) or bar_ids[staff_offset] == "-1":
                    continue
                bar = tables["Bars"][bar_ids[staff_offset]]
                active_voices = 0
                for voice_index, voice_id in enumerate(bar.findtext("Voices", "").split()):
                    if voice_id == "-1":
                        continue
                    voice = tables["Voices"][voice_id]
                    voice_notes = 0
                    for beat_index, beat_id in enumerate(voice.findtext("Beats", "").split()):
                        beat = tables["Beats"][beat_id]
                        beat_notes = []
                        for note_id in beat.findtext("Notes", "").split():
                            note = tables["Notes"][note_id]
                            string = note.findtext("./Properties/Property[@name='String']/String")
                            fret = note.findtext("./Properties/Property[@name='Fret']/Fret")
                            if string is not None and fret is not None and tuning:
                                beat_notes.append((int(string)+1, int(fret)))
                                note_count += 1
                                voice_notes += 1
                        if guitar:
                            for string, fret in sorted(beat_notes):
                                note_lines.append(f"{track_index},{staff_index},{bar_index},{voice_index},{beat_index},{string},{fret};")
                    active_voices += voice_notes > 0
                max_voices = max(max_voices, active_voices)
                poly_bars += active_voices > 1
                if active_voices > 1:
                    poly_indices.append(bar_index + 1)
            stat["staves"].append({"tuningLowToHigh": tuning, "capo": capo, "notes": note_count,
                                   "polyphonicBars": poly_bars, "polyphonicBarNumbers": poly_indices,
                                   "maxSoundingVoices": max_voices})
            staff_offset += 1
        all_tracks.append(stat)
    return {"gpVersion": root.findtext("GPVersion"), "encoding": root.findtext("./Encoding/EncodingDescription"),
            "bars": len(masterbars), "tracks": all_tracks, "guitarTrackIndices": guitar_indices,
            "writtenStringFretSha256": hashlib.sha256("".join(note_lines).encode()).hexdigest(),
            "writtenStringFretCount": len(note_lines), "_referenceRows": "\n".join(note_lines)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scores", type=Path, default=Path.home() / "Desktop" / "谱子")
    args = parser.parse_args()
    files = sorted(p for p in args.scores.rglob("*") if p.suffix.lower() in {".gp", ".gp3", ".gp4", ".gp5", ".gpx"})
    if not files:
        raise SystemExit(f"No GP files under {args.scores}")
    evidence = PROJECT / "work/evidence"
    assets = PROJECT / "work/p0-assets/p0"
    evidence.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    inventory = []
    for path in files:
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        item = {"path": str(path), "relativePath": str(path.relative_to(args.scores)), "bytes": path.stat().st_size,
                "sha256": digest, "extension": path.suffix.lower()}
        if path.suffix.lower() == ".gp":
            try:
                item["reference"] = gpif_reference(path)
                refs = evidence / "gpif-reference"
                refs.mkdir(exist_ok=True)
                rows = refs / (digest + ".txt")
                rows.write_text(item["reference"].pop("_referenceRows"), encoding="utf-8")
                item["referenceRowsPath"] = str(rows)
            except (zipfile.BadZipFile, StopIteration, ET.ParseError, KeyError) as error:
                item["referenceError"] = str(error)
        inventory.append(item)
    # Prioritize a genuinely polyphonic guitar staff, not merely a multi-track score.
    candidates = []
    for item in inventory:
        for track in item.get("reference", {}).get("tracks", []):
            for staff in track["staves"]:
                tuning = staff["tuningLowToHigh"]
                if not track["percussion"] and "guitar" in track["instrumentType"].lower() and len(tuning) >= 6 and min(tuning) >= 20:
                    candidates.append((staff["polyphonicBars"], item, track["index"]))
    # Prefer altered tuning when polyphonic coverage is tied.
    poly_bars, complex_item, complex_track = max(candidates, key=lambda x: (x[0],
        x[1]["reference"]["tracks"][x[2]]["staves"][0]["tuningLowToHigh"] != [40, 45, 50, 55, 59, 64]))
    if poly_bars == 0:
        raise SystemExit("No independently confirmed polyphonic guitar fixture found")
    # Keep an original, small polyphonic GP for slow software emulators as well.
    _, light_item, light_track = min((c for c in candidates if c[0] > 0), key=lambda c: c[1]["bytes"])
    simple = args.scores / "海底谭/花水电车版主音吉他/ウミユリ海底譚 ver.2 guitar 2.gp5"
    if not simple.exists():
        simple = min((p for p in files if p.suffix.lower() == ".gp5"), key=lambda p: p.stat().st_size)
    gpx = simple.with_suffix(".gpx")
    chosen = {"simple.gp5": simple, "complex.gp": Path(complex_item["path"]), "sample.gpx": gpx,
              "polyphonic.gp": Path(light_item["path"])}
    fixtures = []
    for target, source in chosen.items():
        shutil.copyfile(source, assets / target)
        item = next(x for x in inventory if x["path"] == str(source))
        track_index = complex_track if target == "complex.gp" else light_track if target == "polyphonic.gp" else 0
        fixtures.append({"asset": target, **item, "selectedTrack": track_index})
    output = {"sourceRoot": str(args.scores), "files": inventory, "fixtures": fixtures}
    (evidence / "desktop-gp-manifest.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    # Only fixture identities and reference facts need to be inside the private P0 APK.
    (assets / "manifest.json").write_text(json.dumps({"fixtures": fixtures}, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"files": len(files), "fixtures": [{"asset": f["asset"], "source": f["relativePath"],
          "selectedTrack": f["selectedTrack"]} for f in fixtures], "polyphonicBars": poly_bars}, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
