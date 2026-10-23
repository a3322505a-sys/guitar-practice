"""Adapt retained songs with explicit passage edits, preserving original music."""
import argparse
from copy import deepcopy
from fractions import Fraction as F
from pathlib import Path
from song_package.protocol import (
    coverage, digest, dumps, q, rational, read_container, strict_json,
    validate_music, validate_payloads, write_zip,
)
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/song-packages"
SOURCES = (
    ("shayou-v2.gpractice", "shayou-v4.gpractice"),
    ("usotsuki-v1.gpractice", "usotsuki-v1.gpractice"),
    ("your-trip-v1.gpractice", "your-trip-v3.gpractice"),
    ("卡农_C调_清音拨片_v1.gpractice", "canon-c-clean-pick-v1.gpractice"),
    ("Doraemon_no_Uta_r1.gpractice", "Doraemon_no_Uta_r1.gpractice"),
)
# No automatic variant for basic chord passages or the intro.
EDITS = {
    "SHA-RIFF": ("lead", "斜阳 · 间奏 riff · 技巧简化"),
    "SHA-VERSE": ("chords", "斜阳 · 主歌 · 保留和弦简化"),
    "SHA-CHORUS": ("eighths", "斜阳 · 副歌 · 八分基础节奏"),
    "YT-GROOVE": ("eighths", "Your Trip · 双音 · 八分基础节奏"),
    "YT-LEGATO": ("lead", "Your Trip · 连奏段 · 拨奏简化"),
    "YT-RIFF": ("eighths", "Your Trip · 主题 riff · 八分基础节奏"),
    "YT-END": ("eighths", "Your Trip · 尾奏八度 · 八分基础节奏"),
}
REVISION = {"local.shayou": 4, "local.yourtrip": 3}
EXPLANATIONS = {
    "lead": "只将连奏、滑音等难技巧改为普通拨奏；保留全部原音型、末尾和弦、原休止和延音线。",
    "chords": "完整保留和弦的多音形态、弦品及原节奏；只将 P 勾弦改为普通拨奏，并把三连音加花合为一个等总时长的八分骨干音。",
    "eighths": "每小节连续八次八分拨奏；沿用本小节原有实音双音/和弦/八度，原 x 扫弦位置改为当前音型的正常拨奏，不生成碎休止符。",
}


def source_facts(payloads, source_name, output_name):
    roots = {k: strict_json(v) for k, v in payloads.items()}
    return dict(sourceFile=source_name, outputFile=output_name,
        packageId=roots["manifest.json"]["packageId"],
        originalRevision=roots["manifest.json"]["revision"],
        originalScoreHash=digest(dumps(roots["score.json"])),
        originalPracticeHashes={p["id"]: digest(dumps({
            k: v for k, v in p.items() if k not in ("order", "legacyIds")
        })) for p in roots["practices.json"]["practices"]},
        originalPayloadHashes={k: digest(v) for k, v in payloads.items()})


def adapt(payloads, source_path):
    roots = {k: strict_json(v) for k, v in payloads.items()}
    score, practice_root = roots["score.json"], roots["practices.json"]
    original_voice, original_track = score["voices"][0], score["tracks"][0]
    assert len(score["voices"]) == len(score["tracks"]) == 1
    originals = list(practice_root["practices"])
    measures = score["measures"]
    indices = {m["id"]: i for i, m in enumerate(measures)}
    absolute = {}; at = F(0)
    for m in measures:
        absolute[m["id"]] = at; at += q(m["actualDuration"])
    events_by_measure = {m["id"]: [] for m in measures}
    source_locations = {}
    for ei, event in enumerate(original_voice["events"]):
        events_by_measure[event["measureId"]].append(event)
        for ni, note in enumerate(event["notes"]):
            source_locations[note["id"]] = f"score.json/voices/0/events/{ei}/notes/{ni}"
    modes = {}
    for p in originals:
        if p["id"] not in EDITS: continue
        for seg in p["segments"]:
            a, b = indices[seg["start"]["measureId"]], indices[seg["end"]["measureId"]]
            for m in measures[a:b + 1]: modes[m["id"]] = EDITS[p["id"]][0]
    new_voice = dict(id="easy-voice", trackId="easy-guitar", events=[])
    mappings = []; chosen = {}; note_modes = {}

    def map_note(note, new_id, mode, clean=False):
        result = deepcopy(note)
        result["id"] = new_id
        result["sourceRef"] = "保留曲包定点派生 / " + note["id"]
        if clean:
            assert not note.get("dead") and not note.get("harmonic")
            result["articulation"] = dict(velocity=note.get("articulation", {}).get("velocity", .8), pick="default")
        chosen[note["id"]] = result; note_modes[note["id"]] = mode
        mappings.append(dict(noteId=new_id, sourceId="retained-original",
            location=source_locations[note["id"]] + "; sourceNoteId=" + note["id"]))
        return result

    groups = {g["id"]: g for g in score["tuplets"]}
    source_events = {e["id"]: e for e in original_voice["events"]}
    removed = set()
    for measure in measures:
        mid = measure["id"]; mode = modes.get(mid)
        events = events_by_measure[mid]
        if mode is None:
            new_voice["events"].append(dict(id="easy-silent-" + mid, measureId=mid,
                onset=rational(0), duration=measure["actualDuration"],
                written=dict(baseDenominator=1, dots=0, tupletGroupIds=[]), notes=[], measureRest=True))
            continue
        if mode == "eighths":
            live = [e for e in events if any(not n.get("dead") for n in e["notes"])]
            if not live:
                # Remove the all-muted pickup only in the derived basic rhythm.
                nearest = min((e for e in original_voice["events"] if any(not n.get("dead") for n in e["notes"])),
                    key=lambda e: abs(absolute[e["measureId"]] + q(e["onset"]) - absolute[mid]))
                live = [nearest]
            multi = [e for e in live if sum(not n.get("dead") for n in e["notes"]) > 1]
            grips = multi or live
            count = q(measure["actualDuration"]) / F(1, 2)
            assert count.denominator == 1
            for k in range(int(count)):
                onset = F(k, 2)
                eligible = [e for e in grips if e["measureId"] == mid and q(e["onset"]) <= onset]
                source = eligible[-1] if eligible else grips[0]
                eid = f"easy-grid-{mid}-{k}"
                notes = []
                for ni, note in enumerate(n for n in source["notes"] if not n.get("dead")):
                    copy = map_note(note, f"{eid}-n-{ni}", mode, clean=True)
                    copy["articulation"]["pick"] = "down" if k % 2 == 0 else "up"
                    notes.append(copy)
                new_voice["events"].append(dict(id=eid, measureId=mid, onset=rational(onset),
                    duration=rational(F(1, 2)), written=dict(baseDenominator=8, dots=0, tupletGroupIds=[]),
                    notes=notes, sourceRef="八分基础节奏 / " + source["id"]))
            continue
        for event in events:
            if event["id"] in removed: continue
            copy = deepcopy(event); copy["id"] = "easy-" + event["id"]
            if mode == "chords" and event["written"].get("tupletGroupIds"):
                ids = event["written"]["tupletGroupIds"]
                assert len(ids) == 1
                members = [source_events[eid] for eid in groups[ids[0]]["eventIds"]]
                assert all(e["measureId"] == mid for e in members)
                assert event == members[0]
                total = sum((q(e["duration"]) for e in members), F(0))
                assert total == F(1, 2)
                copy["duration"] = rational(total)
                copy["written"] = dict(baseDenominator=8, dots=0, tupletGroupIds=[])
                removed.update(e["id"] for e in members[1:])
            copy["notes"] = [map_note(n, "easy-" + n["id"], mode, clean=mode == "lead") for n in event["notes"]]
            if mode == "lead": copy["articulation"] = {}
            if copy["duration"] != event["duration"]:
                end = absolute[mid] + q(copy["onset"]) + q(copy["duration"])
                for note in copy["notes"]:
                    note.setdefault("articulation", {})["ringUntil"] = rational(end)
            new_voice["events"].append(copy)
    derived_relations = []
    for r in score["relations"]:
        a, b = r["fromNoteId"], r["toNoteId"]
        if a not in chosen or b not in chosen: continue
        mode = note_modes[a]
        keep = mode == note_modes[b] and (mode == "chords" and r["kind"] != "pull" or mode == "lead" and r["kind"] == "tie")
        if keep:
            derived_relations.append(dict(id="easy-" + r["id"], kind=r["kind"],
                fromNoteId=chosen[a]["id"], toNoteId=chosen[b]["id"]))
        elif mode == "chords" and r["kind"] == "pull":
            chosen[b].setdefault("articulation", {})["pick"] = "default"
    for r in derived_relations:
        if r["kind"] == "tie":
            next(n for e in new_voice["events"] for n in e["notes"] if n["id"] == r["toNoteId"])["articulation"]["pick"] = "none"
    score["tracks"].append(dict(deepcopy(original_track), id="easy-guitar", name="定点简化 · 保留原音型"))
    score["voices"].append(new_voice)
    score["relations"].extend(derived_relations)
    practices = []
    for source in originals:
        source.pop("legacyIds", None); source["order"] = len(practices); practices.append(source)
        if source["id"] not in EDITS: continue
        mode, title = EDITS[source["id"]]
        p = deepcopy(source)
        p.update(id=source["id"] + "-easy", title=title, goal=EXPLANATIONS[mode],
            mainTrackId="easy-guitar", mixTrackIds=["easy-guitar"], suggestedTempo=min(50, source.get("suggestedTempo", 50)),
            tempoMode="fixed", difficulty="八分基础节奏" if mode == "eighths" else "局部技巧简化 · 保留原音型",
            visibility="variant", order=len(practices), variantOf=source["id"], familyId=source["id"],
            tags=["定点简化", "保留和弦/双音/八度", mode])
        p.pop("noteFingerings", None)
        p["teaching"] = dict(category="歌曲分段练习", passage=source.get("teaching", {}).get("passage", ""),
            source=source_path.name + "；源段 " + source["id"], technique=p["difficulty"], use="自由选原段或定点简化",
            version="targeted-20261023-r2", tempoLabel="建议 40–50 BPM 起练", explanation=EXPLANATIONS[mode])
        practices.append(p)
    practice_root["practices"] = practices
    prov = roots["provenance.json"]
    prov["sources"].append(dict(id="retained-original", name=source_path.name, sha256=digest(source_path.read_bytes()),
        format="gpractice", license="既有私人练习素材；原曲权利归原作者", origin="按用户纠偏定点简化；原谱实体保留。"))
    prov["mappings"].extend(mappings)
    review = roots["review.json"]
    review.update(status="approved", reviewer="Codex / 用户纠偏后的定点简化审阅", tool="retained-targeted-v2",
        method="对照已审核原包指纹；逐事件核对完整和弦未被抽单音、主歌仅勾弦/三连音变化、副歌八分节奏无休止和死音；独立协议及音乐审计。未作真机试弹。",
        coverage=coverage(score, practices))
    review["diagnostics"].append(dict(category="approximation", pointer="/score/voices/1", featureId="practice.targeted.v1",
        reason="仅为明确难段提供定点简化：主歌保留和弦，节奏型连续八分，基础前奏无简化项。",
        practiceIds=[p["id"] for p in practices if p["id"].endswith("-easy")],
        handling="独立派生轨与原轨分别参与练习；原段和原小节总时长不变。"))
    manifest = roots["manifest.json"]
    manifest["revision"] = REVISION[manifest["packageId"]]
    manifest["display"]["summary"] = "保留原谱；仅难段提供和弦保留、技巧简化或八分基础节奏。"
    manifest["requires"] = sorted(validate_music(score, practices))
    out = {k: dumps(roots[k]) for k in ("score.json", "practices.json", "provenance.json")}
    review["hashes"] = {k: digest(v) for k, v in out.items()}; out["review.json"] = dumps(review)
    manifest["files"] = {k: dict(bytes=len(v), sha256=digest(v)) for k, v in out.items()}
    out["manifest.json"] = dumps(manifest)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ASSETS)
    args = parser.parse_args(); facts = []
    caps = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    for source_name, output_name in SOURCES:
        path = args.source_dir / source_name
        payloads = read_container(path); validate_payloads(payloads, capabilities=caps)
        fact = source_facts(payloads, source_name, output_name)
        fact["originalContainerHash"] = digest(path.read_bytes())
        if source_name in ("shayou-v2.gpractice", "your-trip-v1.gpractice"):
            payloads = adapt(payloads, path); validate_payloads(payloads, capabilities=caps)
            write_zip(payloads, args.output / output_name, overwrite=True)
        else:
            args.output.mkdir(parents=True, exist_ok=True); (args.output / output_name).write_bytes(path.read_bytes())
        facts.append(fact)
    (ROOT / "scripts/score-data/retained-song-facts.json").write_bytes(dumps(dict(packages=facts)))
    print("Five retained packages; seven targeted variants. Intro/chords preserved; chorus is continuous eighths.")


if __name__ == "__main__": main()
