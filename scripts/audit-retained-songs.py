"""Independent preservation and playability checks; never imports the adapter."""
from copy import deepcopy
from pathlib import Path
import json

from song_package.protocol import (
    compile_summary, digest, dumps, read_container, strict_json, validate_payloads,
)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/song-packages"


def audit(directory=ASSETS):
    facts = json.loads((ROOT / "scripts/score-data/retained-song-facts.json").read_text(encoding="utf8"))["packages"]
    assert len(facts) == 5 and len({f["packageId"] for f in facts}) == 5
    capabilities = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    report = []
    for fact in facts:
        path = directory / fact["outputFile"]
        payloads = read_container(path)
        roots = validate_payloads(payloads, capabilities=capabilities)
        score = roots["score.json"]
        practices = roots["practices.json"]["practices"]
        assert roots["manifest.json"]["packageId"] == fact["packageId"]
        variants = [p for p in practices if p["id"].endswith("-easy")]
        original = deepcopy(score)
        original["tracks"] = [t for t in original["tracks"] if t["id"] != "easy-guitar"]
        original["voices"] = [v for v in original["voices"] if v["id"] != "easy-voice"]
        for key in ("tuplets", "relations"):
            original[key] = [x for x in original[key] if not x["id"].startswith("easy-")]
        assert digest(dumps(original)) == fact["originalScoreHash"], (path.name, "original music changed")
        byid = {p["id"]: p for p in practices}
        assert set(byid) == set(fact["originalPracticeHashes"]) | {p["id"] for p in variants}
        for pid, expected in fact["originalPracticeHashes"].items():
            assert digest(dumps({k: v for k, v in byid[pid].items() if k not in ("order", "legacyIds")})) == expected
        if not variants:
            assert digest(path.read_bytes()) == fact["originalContainerHash"], (path.name, "unchanged package bytes differ")
        else:
            assert len(variants) == len(fact["originalPracticeHashes"])
            assert roots["manifest.json"]["revision"] == fact["originalRevision"] + 1
            track = next(t for t in score["tracks"] if t["id"] == "easy-guitar")
            assert track["capo"] == original["tracks"][0]["capo"]
            assert track["openStringMidi"] == original["tracks"][0]["openStringMidi"]
            events = next(v for v in score["voices"] if v["id"] == "easy-voice")["events"]
            source_events = original["voices"][0]["events"]
            assert len(events) == len(source_events)
            for source, event in zip(source_events, events):
                assert event["id"] == "easy-" + source["id"]
                for key in ("measureId", "onset", "duration"):
                    assert event[key] == source[key]
                assert event.get("articulation", {}) == {} and len(event["notes"]) <= 1
                real = [n for n in source["notes"] if not n.get("dead")]
                if not real:
                    assert not event["notes"]
                else:
                    note = event["notes"][0]
                    expected = max(real, key=lambda n: (n["midi"], -n["string"]))
                    assert (note["string"], note["fret"], note["midi"]) == (expected["string"], expected["fret"], expected["midi"])
                    assert note["midi"] == track["openStringMidi"][note["string"] - 1] + track["capo"] + note["fret"]
                    assert set(note["articulation"]) <= {"velocity", "pick"}
                    assert not note.get("dead") and not note.get("harmonic")
            assert all(r["kind"] == "tie" for r in score["relations"] if r["id"].startswith("easy-"))
            ordered = sorted(practices, key=lambda p: p["order"])
            assert [p["order"] for p in ordered] == list(range(len(ordered)))
            for p in variants:
                base = byid[p["variantOf"]]
                assert ordered.index(p) == ordered.index(base) + 1
                assert p["mainTrackId"] == "easy-guitar" and p["mixTrackIds"] == ["easy-guitar"]
                assert base["mainTrackId"] != "easy-guitar" and "easy-guitar" not in base["mixTrackIds"]
                plan, source_plan = compile_summary(score, p), compile_summary(score, base)
                assert plan["length"] == source_plan["length"]
                assert [v["measureIndex"] for v in plan["visits"]] == [v["measureIndex"] for v in source_plan["visits"]]
                assert plan["notes"] and len(plan["notes"]) <= len(source_plan["notes"])
        # All five packages and every segment must compile in addition to schema checks.
        for p in practices:
            assert compile_summary(score, p)["notes"], (path.name, p["id"])
        report.append(dict(file=path.name, packageId=fact["packageId"], revision=roots["manifest.json"]["revision"],
            originalPractices=len(fact["originalPracticeHashes"]), singleNoteVariants=len(variants),
            measures=len(score["measures"]), originalMusicUnchanged=True))
    assert sum(r["singleNoteVariants"] for r in report) == 11
    return report


if __name__ == "__main__":
    print(json.dumps(audit(), ensure_ascii=False, indent=2))
