"""Independent source-preservation and user-requested simplification checks."""
from copy import deepcopy
from fractions import Fraction as F
from pathlib import Path
import json
from song_package.protocol import compile_summary, digest, dumps, q, read_container, strict_json, validate_payloads

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/song-packages"
EXPECTED = {
    "local.shayou": (4, {"SHA-RIFF", "SHA-VERSE", "SHA-CHORUS"}),
    "local.yourtrip": (3, {"YT-GROOVE", "YT-LEGATO", "YT-RIFF", "YT-END"}),
}
RHYTHMS = {"SHA-CHORUS", "YT-GROOVE", "YT-RIFF", "YT-END"}
LEADS = {"SHA-RIFF", "YT-LEGATO"}


def positions(event):
    return [(n["string"], n["fret"], n["midi"]) for n in event["notes"]]


def audit(directory=ASSETS):
    facts = json.loads((ROOT / "scripts/score-data/retained-song-facts.json").read_text(encoding="utf8"))["packages"]
    assert len(facts) == 5 and len({f["packageId"] for f in facts}) == 5
    caps = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    report = []
    for fact in facts:
        path = directory / fact["outputFile"]
        roots = validate_payloads(read_container(path), capabilities=caps)
        score, ps = roots["score.json"], roots["practices.json"]["practices"]
        pid = roots["manifest.json"]["packageId"]; assert pid == fact["packageId"]
        byid = {p["id"]: p for p in ps}
        variants = [p for p in ps if p["id"].endswith("-easy")]
        original = deepcopy(score)
        original["tracks"] = [t for t in original["tracks"] if t["id"] != "easy-guitar"]
        original["voices"] = [v for v in original["voices"] if v["id"] != "easy-voice"]
        for key in ("tuplets", "relations"):
            original[key] = [x for x in original[key] if not x["id"].startswith("easy-")]
        assert digest(dumps(original)) == fact["originalScoreHash"], (path.name, "original music changed")
        assert set(byid) == set(fact["originalPracticeHashes"]) | {p["id"] for p in variants}
        for practice_id, expected in fact["originalPracticeHashes"].items():
            assert digest(dumps({k: v for k, v in byid[practice_id].items() if k not in ("order", "legacyIds")})) == expected
        details = {}
        if not variants:
            assert digest(path.read_bytes()) == fact["originalContainerHash"]
        else:
            revision, expected = EXPECTED[pid]
            assert roots["manifest.json"]["revision"] == revision
            assert {p["variantOf"] for p in variants} == expected
            assert "SHA-INTRO-easy" not in byid
            track = next(t for t in score["tracks"] if t["id"] == "easy-guitar")
            assert track["openStringMidi"] == original["tracks"][0]["openStringMidi"]
            assert track["capo"] == original["tracks"][0]["capo"]
            ordered = sorted(ps, key=lambda p: p["order"])
            assert [p["order"] for p in ordered] == list(range(len(ps)))
            for p in variants:
                base = byid[p["variantOf"]]
                plan, source = compile_summary(score, p), compile_summary(score, base)
                assert p["mixTrackIds"] == ["easy-guitar"] and "easy-guitar" not in base["mixTrackIds"]
                assert ordered.index(p) == ordered.index(base) + 1
                assert plan["length"] == source["length"]
                assert [v["measureIndex"] for v in plan["visits"]] == [v["measureIndex"] for v in source["visits"]]
                es = [e["event"] for e in plan["events"]]
                src = [e["event"] for e in source["events"]]
                for e in es:
                    for n in e["notes"]:
                        assert n["midi"] == track["openStringMidi"][n["string"] - 1] + track["capo"] + n["fret"]
                if base["id"] in RHYTHMS:
                    assert len(es) == len(plan["visits"]) * 8
                    assert all(q(e["duration"]) == F(1, 2) and e["notes"] for e in es)
                    assert all(e["written"] == dict(baseDenominator=8, dots=0, tupletGroupIds=[]) for e in es)
                    assert not any(n.get("dead") for e in es for n in e["notes"])
                    allowed = {(n["string"], n["fret"], n["midi"]) for e in src for n in e["notes"] if not n.get("dead")}
                    assert all(set(positions(e)) <= allowed for e in es)
                    for visit in plan["visits"]:
                        mid = score["measures"][visit["measureIndex"]]["id"]
                        bar = [e for e in es if e["measureId"] == mid]
                        assert [q(e["onset"]) for e in bar] == [F(k, 2) for k in range(8)]
                        if any(sum(not n.get("dead") for n in e["notes"]) > 1 for e in src if e["measureId"] == mid):
                            assert all(len(e["notes"]) > 1 for e in bar), (base["id"], mid, "chords collapsed")
                    details[base["id"]] = dict(measures=len(plan["visits"]), eighths=len(es), rests=0, deadNotes=0)
                elif base["id"] in LEADS:
                    assert len(es) == len(src)
                    for a, b in zip(src, es):
                        assert (a["measureId"], a["onset"], a["duration"]) == (b["measureId"], b["onset"], b["duration"])
                        assert positions(a) == positions(b), "lead ending chord collapsed"
                        assert b.get("articulation", {}) == {}
                        assert all(set(n["articulation"]) <= {"velocity", "pick"} for n in b["notes"])
                else:
                    assert base["id"] == "SHA-VERSE"
                    assert len(es) == len(src) - 2
                    simple = {e["id"]: e for e in es}
                    chords = [e for e in src if len(e["notes"]) > 1]
                    for chord in chords:
                        copy = simple["easy-" + chord["id"]]
                        assert positions(copy) == positions(chord)
                        assert (copy["onset"], copy["duration"]) == (chord["onset"], chord["duration"])
                        assert [n.get("articulation", {}) for n in copy["notes"]] == [n.get("articulation", {}) for n in chord["notes"]]
                    assert [(e["measureId"], e["onset"], e["duration"]) for e in es if not e["notes"]] == [
                        (e["measureId"], e["onset"], e["duration"]) for e in src if not e["notes"]]
                    assert not any(e["written"].get("tupletGroupIds") for e in es)
                    assert not any(r["kind"] == "pull" and r["fromNoteId"].startswith("easy-SHA-VERSE") for r in score["relations"])
                    anchor = simple["easy-SHA-VERSE-e-174-0"]
                    assert q(anchor["duration"]) == F(1, 2)
                    assert positions(anchor) == positions(next(e for e in src if e["id"] == "SHA-VERSE-e-174-0"))
                    details["SHA-VERSE"] = dict(originalChordAttacks=len(chords), retainedChordAttacks=len(chords),
                        removedPulls=4, tripletFillsReplaced=1, newRests=0)
        for p in ps:
            assert compile_summary(score, p)["notes"], (pid, p["id"])
        report.append(dict(file=path.name, packageId=pid, revision=roots["manifest.json"]["revision"],
            originalPractices=len(fact["originalPracticeHashes"]), targetedVariants=len(variants),
            measures=len(score["measures"]), originalMusicUnchanged=True, details=details))
    assert sum(r["targetedVariants"] for r in report) == 7
    assert sum(r["targetedVariants"] + r["originalPractices"] for r in report) == 31
    return report


if __name__ == "__main__": print(json.dumps(audit(), ensure_ascii=False, indent=2))
