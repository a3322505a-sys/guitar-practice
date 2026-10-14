"""One-song migration of the v75 S01 arrangement; no source-score inference or App installation."""
from pathlib import Path
from fractions import Fraction
import re
import subprocess
import json
from song_package.protocol import dumps, digest, coverage, package_files, validate_payloads, write_zip

ROOT = Path(__file__).resolve().parents[1]
BASE = "9794d266697083aaeeea975f1b23b9bca3f6946b"  # v75 main; retained in Git, outside runtime registration.
PHRASES = "app/src/main/java/com/a3322505a/guitarlearning/practice/Phrases.kt"
MUSIC = "app/src/main/java/com/a3322505a/guitarlearning/music/Music.kt"


def baseline(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT)


def q(value):
    value = Fraction(value)
    return dict(n=value.numerator, d=value.denominator)


def convert():
    source_bytes = baseline(PHRASES)
    source = source_bytes.decode("utf-8")
    start = source.index("    // Printed bars 10")
    end = source.index("    val singleNote", start)
    old = source[start:end]
    curation = re.search(r'usotsuki.copy\((.*?)\),singleNote', source).group(1)
    def text(key):
        return re.search(r'\b' + key + r'="([^"]+)"', old + curation).group(1)
    positions = [(int(s), int(f)) for s, f in re.findall(r"Position\((\d+),(\d+)\)", old)]
    names = re.search(r'val chord=listOf\((.*?)\)\[index/4\]', old).group(1)
    names = re.findall(r'"([^"]+)"', names)
    assert len(positions) == 16 and len(names) == 4
    assert "Event(index*6,6,listOf(position),chord,ring=true)" in old and "length=2*BAR" in old
    music = baseline(MUSIC).decode("utf-8")
    voicings = []
    for i, name in enumerate(names):
        match = re.search(r'Chord\("' + re.escape(name) + r'", (\d+), (?:null|\d+), listOf\(([^)]*)\), listOf\(([^)]*)\)\)', music)
        assert match, name
        frets = [-1 if f.strip() == "null" else int(f) for f in match.group(2).split(",")][::-1]
        fingers = [int(f) for f in match.group(3).split(",")][::-1]
        voicings.append(dict(id=f"grip-{i}", name=name, trackId="guitar", frets=frets, fingers=fingers))
    events = []
    mappings = []
    tuning = [64, 59, 55, 50, 45, 40]
    for i, (string, fret) in enumerate(positions):
        tick = i * 6
        grip_end = (i // 4 + 1) * 24
        string_end = next((j * 6 for j in range(i + 1, 16) if positions[j][0] == string), 96)
        ring_end = min(grip_end, string_end, 96)
        note = dict(id=f"note-{i}", string=string, fret=fret, midi=tuning[string - 1] + fret,
                    articulation=dict(velocity=1, pick="default", palmMute=False, short=False, letRing=True,
                                      ringUntil=q(Fraction(ring_end, 12)), strumDelayMs=0, slide="none"),
                    sourceRef=f"v75/S01/event/{tick}/string/{string}")
        events.append(dict(id=f"event-{i}", measureId=f"bar-{i // 8}", onset=q(Fraction(tick % 48, 12)),
                           duration=q(Fraction(1, 2)), written=dict(baseDenominator=8, dots=0, tupletGroupIds=[]), notes=[note]))
        mappings.append(dict(noteId=note["id"], sourceId="v75-kotlin", location=note["sourceRef"] + f"; GP master index {10 + i // 8}"))
    measures = [dict(id=f"bar-{i}", label=str(i + 1), meter=dict(numerator=4, denominator=4, beatGroups=[1] * 4),
                     actualDuration=q(4), sourceRef=f"嘘月.gp/Rhythm Guitar/master index {10+i}") for i in range(2)]
    score = dict(documentId="usotsuki-v75", measures=measures,
                 tracks=[dict(id="guitar", name="Rhythm Guitar", openStringMidi=tuning, capo=0, fretCount=36)],
                 voices=[dict(id="voice", trackId="guitar", events=events)], tuplets=[], relations=[],
                 tempos=[dict(at=q(0), beatUnit=q(1), bpm=80)], dynamics=[], swing=[], form={}, voicings=voicings,
                 harmonies=[dict(id=f"harmony-{i}", measureId=f"bar-{i // 2}", onset=q((i % 2) * 2), name=name,
                                 voicingId=f"grip-{i}") for i, name in enumerate(names)])
    practice = dict(id="bass-treble", title=text("title"), goal=text("goal"), mainTrackId="guitar", mixTrackIds=["guitar"],
                    segments=[dict(start=dict(measureId="bar-0"), end=dict(measureId="bar-1", offset=q(4)))],
                    suggestedTempo=40, difficulty=text("difficulty"), tags=[text("technique"), text("passage"), text("category"), text("use")],
                    tempoMode="fixed", visibility="core", order=0)
    provenance = dict(tool="migrate-usotsuki-v76", exclusions=[], sources=[dict(id="v75-kotlin", name="嘘月 · v75 S01 两小节伴奏改编", sha256=digest(source_bytes),
                                   format="Kotlin", version="v75 / " + BASE, license="既有私人练习素材；原曲权利归原作者",
                                   origin=text("source") + "；本次只转换既有改编练习，不补录原曲")], mappings=mappings)
    review = dict(status="approved", reviewer="Codex / v76 migration audit", tool="migrate-usotsuki-v76",
                  method="独立保留v75 Kotlin练习并核对16音头、弦品、四套用指、延响截止与教学文字；JVM对比同一内核的计划和PCM。审核范围仅既有S01改编。",
                  assumptions=[], diagnostics=[], coverage=coverage(score, [practice]))
    payloads = package_files("local.usotsuki", 1, "嘘月", "ヨルシカ", score, [practice], provenance, review)
    manifest = json.loads(payloads["manifest.json"])
    manifest["display"]["summary"] = text("explanation")
    payloads["manifest.json"] = dumps(manifest)
    validate_payloads(payloads)
    return old, payloads


if __name__ == "__main__":
    old, payloads = convert()
    output = ROOT / "output/usotsuki-v1.gpractice"
    sha = write_zip(payloads, output)
    print(json.dumps(dict(file=str(output), bytes=output.stat().st_size, sha256=sha), ensure_ascii=False))
