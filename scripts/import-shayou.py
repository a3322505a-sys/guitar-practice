"""Read the GP ID graph; retain five complete phrases, relative frets and real capo pitch."""
import argparse
import hashlib
import json
from fractions import Fraction
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = Path(r"C:\Users\b3322\Desktop\谱子\斜阳TV版\斜阳TV版.gp")
PARTS = [
    ("SHA-INTRO", "斜阳 · 前奏", 2, 9, 2, "长时值和弦伴奏", "保持完整和弦推进，最后一小节逐渐加密。"),
    ("SHA-RIFF", "斜阳 · 间奏 riff", 9, 17, 1, "单音 riff／滑音／击勾弦", "带第9小节末弱起，完整弹完两轮回应与长音收尾。"),
    ("SHA-VERSE", "斜阳 · 主歌", 18, 25, 1, "和弦／空拍／三连音填句", "保留空拍，三连音填句与转出和弦都在完整主歌内。"),
    ("SHA-PRE", "斜阳 · 预副歌", 26, 31, 1, "和弦推进／延音／单音过门", "跨小节延音不重拨，末尾过门连入完整段尾。"),
    ("SHA-CHORUS", "斜阳 · 副歌", 32, 39, 1, "双音／十六分闷音／滑音", "区分有音高双音与死音，连续弹完副歌及连接尾句。"),
]


def read_gp(path):
    with zipfile.ZipFile(path) as archive:
        root = ET.fromstring(archive.read("Content/score.gpif"))
    tables = {name: {e.get("id"): e for e in root.find(name)}
              for name in ("Bars", "Voices", "Beats", "Notes", "Rhythms")}
    masters = list(root.find("MasterBars"))
    tracks = {t.get("id"): t for t in root.find("Tracks")}
    order = root.findtext("MasterTrack/Tracks").split()

    def prop(element, name):
        return element.find(f"Properties/Property[@name='{name}']")

    def duration(beat):
        rhythm = tables["Rhythms"][beat.find("Rhythm").get("ref")]
        ticks = {"Whole": 48, "Half": 24, "Quarter": 12, "Eighth": 6, "16th": 3}[rhythm.findtext("NoteValue")]
        dots = rhythm.find("AugmentationDot")
        if dots is not None:
            ticks *= sum(Fraction(1, 2) ** i for i in range(int(dots.get("count")) + 1))
        tuplet = rhythm.find("PrimaryTuplet")
        if tuplet is not None:
            ticks *= Fraction(int(tuplet.get("den")), int(tuplet.get("num")))
        assert Fraction(ticks).denominator == 1
        return int(ticks), tuplet is not None

    parts = []
    for ident, title, first, last, track_id, technique, goal in PARTS:
        track = tracks[str(track_id)]
        assert len(track.findall("Staves/Staff")) == 1
        capo = int(track.findtext("Staves/Staff/Properties/Property[@name='CapoFret']/Fret"))
        tuning = list(map(int, track.findtext("Staves/Staff/Properties/Property[@name='Tuning']/Pitches").split()))
        assert tuning == [40, 45, 50, 55, 59, 64] and capo == 3
        events, links, tuplets, raw = [], [], [], []
        latest = {}
        for bar_no in range(first, last + 1):
            master = masters[bar_no - 1]
            assert master.findtext("Time") == "4/4"
            bar = tables["Bars"][master.findtext("Bars").split()[order.index(str(track_id))]]
            voices = [v for v in bar.findtext("Voices").split() if v != "-1"]
            assert len(voices) == 1
            tick = (bar_no - first) * 48
            for beat_id in tables["Voices"][voices[0]].findtext("Beats").split():
                beat = tables["Beats"][beat_id]
                dur, triplet = duration(beat)
                velocity = {"MF": .8, "F": 1.0}[beat.findtext("Dynamic")]
                positions, dead, incoming, targets = [], [], [], []
                ring = False
                palm = []
                vibrato = False
                for note_id in (beat.findtext("Notes") or "").split():
                    note = tables["Notes"][note_id]
                    string_index = int(prop(note, "String").findtext("String"))
                    string = 6 - string_index
                    fret = int(prop(note, "Fret").findtext("Fret"))
                    midi = int(prop(note, "Midi").findtext("Number"))
                    assert midi == tuning[string_index] + fret + capo
                    muted = prop(note, "Muted") is not None
                    tied = note.find("Tie") is not None and note.find("Tie").get("destination") == "true"
                    raw.append((ident, bar_no, tick % 48, dur, string, fret, midi, int(muted), int(tied)))
                    if muted:
                        dead.append(string)
                    else:
                        positions.append((string, fret))
                    incoming.append(tied)
                    ring |= note.find("LetRing") is not None
                    vibrato |= note.find("Vibrato") is not None
                    if prop(note, "PalmMuted") is not None:
                        palm.append(string)
                    if prop(note, "HopoDestination") is not None:
                        prior = latest.get(string)
                        assert prior is not None and prior[1] != fret
                        targets.append((prior[0], tick, string, "HAMMER" if fret > prior[1] else "PULL"))
                    # Flags=2 is a legato slide to the next pitched note on this string.
                    slide = prop(note, "Slide")
                    if slide is not None:
                        assert slide.findtext("Flags") == "2"
                positions.sort()
                dead.sort()
                if not incoming:
                    raw.append((ident, bar_no, tick % 48, dur, 0, 0, 0, 0, 0))
                if any(incoming):
                    assert all(incoming) and events and not dead
                    previous = events[-1]
                    assert previous["positions"] == positions and previous["tick"] + previous["duration"] == tick
                    previous["duration"] += dur
                    previous["vibrato"] |= vibrato
                else:
                    brush = prop(beat, "Brush")
                    arp = beat.find("Arpeggio")
                    pick = "NONE" if targets else (brush.findtext("Direction").upper() if brush is not None else "DOWN" if arp is not None else "DEFAULT")
                    if targets:
                        assert len(targets) == len(positions)
                        links.extend(targets)
                    events.append(dict(tick=tick, duration=dur, positions=positions, dead=dead, ring=ring,
                                       pick=pick, strum=brush is not None or arp is not None,
                                       palm=palm, vibrato=vibrato, velocity=velocity))
                    for string, fret in positions:
                        prior = latest.get(string)
                        if prior is not None and prior[2]:
                            assert not targets
                            events[-1]["pick"] = "NONE"
                            links.append((prior[0], tick, string, "SLIDE"))
                        note = next(n for n in (tables["Notes"][n] for n in beat.findtext("Notes").split())
                                    if 6 - int(prop(n, "String").findtext("String")) == string)
                        latest[string] = (tick, fret, prop(note, "Slide") is not None)
                if triplet and tick % 6 == 0:
                    tuplets.append((tick, 2))
                tick += dur
            assert tick == (bar_no - first + 1) * 48
        assert all(not value[2] for value in latest.values())
        parts.append(dict(id=ident, title=title, first=first, last=last, track=track.findtext("Name"),
                          technique=technique, goal=goal, events=events, links=links, tuplets=tuplets, raw=raw))
    return parts


def emit(parts, source):
    def positions(ps):
        return "listOf(" + ",".join(f"Position({s},{f})" for s, f in ps) + ")" if ps else "emptyList()"

    lines = ["package com.a3322505a.guitarlearning.practice", "", "import com.a3322505a.guitarlearning.music.Position", "",
             "/** Five complete GP passages; fret numbers are relative to capo 3. Generated by scripts/import-shayou.py. */",
             "internal object Shayou {", "    val phrases = listOf("]
    for part in parts:
        first, last = part["first"], part["last"]
        lines += ["        Phrase(", f'            id="{part["id"]}", title="{part["title"]}", category="{part["technique"]}",',
                  f'            passage="{first}–{last} 小节 · 完整乐段", source="斜阳TV版.gp · {part["track"]}",',
                  f'            sourceBpm=124, difficulty="进阶", technique="{part["technique"]}",',
                  '            conditions="标准调弦 · 变调夹3品", use="斜阳 · 5类完整乐句", suggestedBpm=60,',
                  f'            goal="{part["goal"]}", sequence=Sequence(events=listOf(']
        for e in part["events"]:
            args = [str(e["tick"]), str(e["duration"]), positions(e["positions"])]
            if e["dead"]:
                args += ["deadStrings=setOf(" + ",".join(map(str, e["dead"])) + ")"]
            for flag in ("ring", "strum", "vibrato"):
                if e[flag]:
                    args += [flag + "=true"]
            if e["velocity"] != 1.0:
                args += [f'velocity={e["velocity"]}f']
            if e["pick"] != "DEFAULT":
                args += ["pick=Pick." + e["pick"]]
            if e["palm"]:
                assert set(e["palm"]) == {s for s, _ in e["positions"]}
                args += ["palmMute=true"]
            lines += ["                Event(" + ",".join(args) + "),"]
        explanation = "整段弹完再循环；内部重复、填句与收尾不另拆。品位相对变调夹，发声与点音为实际音高。"
        if part["id"] == "SHA-RIFF":
            explanation += "第9小节前三拍休止，第四拍弱起；保留完整10–17小节。尾奏40–45复用此riff，不另列小句。"
        explanation += "滑音、击勾弦及揉弦声音为采样近似，按谱实际演奏。"
        lines += [f'            ),length={(last-first+1)*48}, explanation="{explanation}", originalBar={first-1}, capoFret=3,',
                  "            tuplets=listOf(" + ",".join(f"Tuplet({tick},{unit})" for tick, unit in part["tuplets"]) + "),",
                  "            links=listOf(" + ",".join(f"Legato({a},{b},{s},Technique.{kind})" for a, b, s, kind in part["links"]) + ")),", "        ),"]
    lines += ["    )", "}", ""]
    (ROOT / "app/src/main/java/com/a3322505a/guitarlearning/practice/Shayou.kt").write_text("\n".join(lines), encoding="utf-8")
    fixture = "phrase,bar,tick,duration,string,fret,midi,dead,tie\n" + "\n".join(
        ",".join(map(str, row)) for part in parts for row in part["raw"]) + "\n"
    (ROOT / "app/src/test/resources/shayou-gp.csv").write_text(fixture, encoding="utf-8")
    out = ROOT / "work/shayou-v68"
    out.mkdir(parents=True, exist_ok=True)
    (out / "import.json").write_text(json.dumps({"source": str(source), "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
         "phrases": [{k: v for k, v in p.items() if k != "raw"} for p in parts]}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"phrases": [(p["id"], p["first"], p["last"], len(p["events"]), len(p["links"])) for p in parts]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", nargs="?", type=Path, default=DEFAULT)
    source = parser.parse_args().source
    emit(read_gp(source), source)
