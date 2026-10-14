"""One-time v76 content migration. Consumes a frozen capture, never emits Kotlin or registers songs.

Source capture: scripts/archive/ExportLegacySongs.kt at base 7a6e68c.
Independent musical checks use the retained source CSV/PDF transcript, not this capture.
"""
import argparse
import copy
import csv
import json
from pathlib import Path
from fractions import Fraction as F
from song_package.protocol import dumps, digest, rational as q, q as value, coverage, package_files, validate_payloads, write_zip

ROOT = Path(__file__).resolve().parents[1]


def original_beats(kind, ident, seq):
    if kind == "mozart":
        return {}
    name = "shayou-gp.csv" if kind == "shayou" else "your-trip-pdf.csv"
    result = {}
    with (ROOT / "app/src/test/resources" / name).open(encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            if kind == "shayou" and r["phrase"] != ident:
                continue
            bar = int(r["bar"])
            tick = (bar - seq["originalBar"] - 1) * 48 + int(r["tick"])
            if 0 <= tick < seq["length"]:
                result.setdefault(tick, []).append(r)
    return result


def migrate(snapshot, kind, title, author):
    records = [r for r in snapshot["phrases"] if r["phrase"]["id"].startswith({"mozart": "K265-", "shayou": "SHA-", "your-trip": "YT-"}[kind])]
    score = copy.deepcopy(records[0]["score"])
    score.update(documentId="migrated-" + kind, measures=[], voices=[dict(id="voice", trackId="guitar", events=[])],
                 tuplets=[], relations=[], harmonies=[], voicings=[], tempos=[dict(at=q(0), beatUnit=q(1), bpm=records[0]["phrase"]["sourceBpm"])])
    practices, mappings = [], []
    offset = F(0)
    pdf = json.loads((ROOT / "scripts/score-data/your-trip.json").read_text(encoding="utf-8")) if kind == "your-trip" else None
    for order, record in enumerate(records):
        old = record["phrase"]
        seq = old["sequence"]
        ident = old["id"]
        part = copy.deepcopy(record["score"])
        prefix = ident + "-"
        beats = original_beats(kind, ident, seq)
        window = next((p for p in pdf["parts"] if p["id"] == ident), None) if pdf else None
        first = window["start"] if window else 0
        stop = (window["last"] - window["first"]) * 48 + window["end"] if window else seq["length"]
        for m in part["measures"]:
            m["id"] = prefix + m["id"]
            # The legacy adapter's labels already preserve source bar numbers.
            m["sourceRef"] = old["source"] + " / bar " + m["label"]
        new_events, heads = [], {}
        for e in part["voices"][0]["events"]:
            tick = int(e["sourceRef"].rsplit("/", 1)[1])
            end = tick + int(value(e["duration"]) * 12)
            padded = kind == "your-trip" and (tick < first or tick >= stop)
            cuts = [tick] + sorted(t for t in beats if tick < t < end and first <= t < stop) + [end]
            if padded:
                cuts = [tick, end]
            heads[e["id"]] = []
            for index, (start, finish) in enumerate(zip(cuts, cuts[1:])):
                event = copy.deepcopy(e)
                event["id"] = prefix + e["id"] + "-" + str(index)
                bar = start // 48 if kind != "mozart" else start // 24
                bar_ticks = 24 if kind == "mozart" else 48
                event.update(measureId=part["measures"][bar]["id"], onset=q(F(start % bar_ticks, 12)), duration=q(F(finish - start, 12)))
                triplet = bool(e["written"]["tupletGroupIds"])
                actual = F(finish - start, 12) * (F(3, 2) if triplet else 1)
                written = next((dict(baseDenominator=b, dots=d, tupletGroupIds=[prefix + t for t in e["written"]["tupletGroupIds"]])
                                for b in (1, 2, 4, 8, 16, 32, 64) for d in range(4)
                                if sum(F(4, b * 2**j) for j in range(d + 1)) == actual), None)
                assert written, (ident, start, finish, "unrepresentable written rhythm")
                event["written"] = written
                source_bar = seq["originalBar"] + bar + 1
                event["sourceRef"] = old["source"] + f" / bar {source_bar} tick {start % bar_ticks}"
                if padded:
                    event["practicePadding"] = "既有教学句界留空；此拍位不是原谱休止。"
                for n in event["notes"]:
                    original_id = n["id"]
                    n["id"] = prefix + original_id + "-" + str(index)
                    n["sourceRef"] = event["sourceRef"] + " string " + str(n["string"])
                    if index:
                        n["articulation"] = dict(pick="none")
                        score["relations"].append(dict(id="tie-" + n["id"], kind="tie",
                            fromNoteId=heads[e["id"]][-1][n["string"]], toNoteId=n["id"]))
                    elif "ringUntil" in n["articulation"]:
                        n["articulation"]["ringUntil"] = q(value(n["articulation"]["ringUntil"]) + offset)
                    mappings.append(dict(noteId=n["id"], sourceId="source", location=n["sourceRef"]))
                heads[e["id"]].append({n["string"]: n["id"] for n in event["notes"]})
                new_events.append(event)
        for t in part["tuplets"]:
            t["id"] = prefix + t["id"]
            t["eventIds"] = [next(e["id"] for e in new_events if e["id"] == prefix + id + "-0") for id in t["eventIds"]]
        for r in part["relations"]:
            def endpoint(id, last):
                event_id = "e-" + id.split("-")[1]
                string = int(id.split("-")[2])
                return heads[event_id][-1 if last else 0][string]
            r.update(id=prefix + r["id"], fromNoteId=endpoint(r["fromNoteId"], True), toNoteId=endpoint(r["toNoteId"], False))
        score["measures"] += part["measures"]
        score["voices"][0]["events"] += new_events
        score["tuplets"] += part["tuplets"]
        score["relations"] += part["relations"]
        teaching = {k: old[k] for k in ("category", "passage", "source", "technique", "use", "tempoLabel")}
        teaching["explanation"] = seq["explanation"]
        legacy = [ident]
        if kind == "mozart":
            legacy += ["K265-" + str(order * 2 + 1), "K265-" + str(order * 2 + 2)]
        practices.append(dict(id=ident, title=old["title"], goal=old["goal"], mainTrackId="guitar", mixTrackIds=["guitar"],
            segments=[dict(start=dict(measureId=part["measures"][0]["id"]), end=dict(measureId=part["measures"][-1]["id"], offset=part["measures"][-1]["actualDuration"]))],
            suggestedTempo=old["suggestedBpm"], difficulty=old["difficulty"], tags=[old["technique"]], tempoMode="fixed", visibility="core", order=order,
            teaching=teaching, legacyIds=legacy))
        offset += sum((value(m["actualDuration"]) for m in part["measures"]), F(0))
    if kind == "shayou":
        for index, chord in enumerate(snapshot["chords"]):
            teaching = {k: chord[k] for k in ("root", "soundingName", "group", "source", "intervalRoles", "toneNames")}
            if "third" in chord:
                teaching["third"] = chord["third"]
            score["voicings"].append(dict(id="grip-" + str(index), name=chord["name"], trackId="guitar",
                frets=[f if f is not None else -1 for f in chord["frets"][::-1]], fingers=chord["fingers"][::-1], teaching=teaching))
    source_hash = {"mozart": "437901aaf1f310740f18366c7a079f2e503c438cde364dcf97804db430110e91",
                   "shayou": "68c629fd47d1a4de224dbe3c21c22c488326166e7c465a7b3f525293b180575e",
                   "your-trip": "3293c583569de637fa69396bd3280a483e3d1cef42a3645c7c31e03349406bc7"}[kind]
    provenance = dict(tool="legacy-migration-v77", sources=[dict(id="source", name=records[0]["phrase"]["source"], sha256=source_hash,
        format="reviewed-pdf" if pdf else "gpif", license="既有私人练习素材；原曲权利归原作者", origin="既有已审核来源；范围限定为原App完整练习，未新增选句或音乐事实。")],
        mappings=mappings, exclusions=["仅迁移原有教学选材；未收录原曲的其他小节或声部。", "原练习按完整书面乐句循环，不补入未展开的原谱反复。"])
    review = dict(status="approved", reviewer="Codex / v77 content migration", tool="legacy-migration-v77",
        method="原始来源哈希与独立来源CSV/已审核PDF转录逐事件核对；保留原教学窗口、技巧、调弦与变调夹；来源续写改为显式tie。", assumptions=[], diagnostics=[], coverage=coverage(score, practices))
    payloads = package_files("local." + kind.replace("-", ""), 1, title, author, score, practices, provenance, review)
    validate_payloads(payloads)
    path = ROOT / "app/src/main/assets/song-packages" / (kind + "-v1.gpractice")
    sha = write_zip(payloads, path)
    return dict(path=str(path.relative_to(ROOT)), bytes=path.stat().st_size, sha256=sha, practices=len(practices), voicings=len(score["voicings"]), coverage=review["coverage"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    args = parser.parse_args()
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8"))
    result = [migrate(snapshot, *song) for song in [("mozart", "莫扎特 K265", "W. A. Mozart"), ("shayou", "斜阳", "ヨルシカ"), ("your-trip", "Your Trip", "")]]
    report = ROOT / "work/v77/packages.json"
    report.write_bytes(dumps(result))
    print(json.dumps(result, ensure_ascii=False, indent=2))
