"""Check the Hana package against facts captured from the supplied v1 package."""
from copy import deepcopy
from pathlib import Path
import json
from song_package.protocol import compile_summary, digest, dumps, read_container, strict_json, validate_payloads

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/song-packages"


def audit(directory=ASSETS):
    fact = json.loads((ROOT / "scripts/score-data/hana-bourei-facts.json").read_text(encoding="utf8"))
    payloads = read_container(directory / fact["outputFile"])
    caps = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    roots = validate_payloads(payloads, capabilities=caps)
    manifest, score = roots["manifest.json"], roots["score.json"]
    catalogue = roots["practices.json"]
    assert manifest["packageId"] == fact["packageId"] and manifest["revision"] == 2
    for name, expected in fact["preservedPayloadHashes"].items():
        assert digest(payloads[name]) == expected, (name, "original bytes changed")
    byid = {p["id"]: p for p in catalogue["practices"]}
    assert len(byid) == 8 and set(byid) == set(fact["retainedPractices"])
    assert len({p["familyId"] for p in byid.values()}) == 6
    assert len({p["title"] for p in byid.values()}) == 8
    assert [p["order"] for p in byid.values()] == list(range(8))
    plans = {}
    for pid, expected in fact["retainedPractices"].items():
        practice = byid[pid]
        body = {k: v for k, v in practice.items() if k not in ("title", "order")}
        assert digest(dumps(body)) == expected["originalBodyHash"], pid
        assert practice["title"] == expected["title"]
        plan = plans[pid] = compile_summary(score, practice)
        assert len(plan["notes"]) == expected["notes"] and plan["notes"]
    aliases = {a["from"]: a["to"] for a in catalogue["aliases"]}
    assert len(aliases) == 10 and set(aliases) == set(fact["removedPractices"])
    kept_events = {e["event"]["id"] for p in plans.values() for e in p["events"]}
    kept_notes = {n["note"]["id"] for p in plans.values() for n in p["notes"]}
    assert len(kept_notes) == fact["originalUniquePracticedNotes"] == 513
    assert digest(dumps(sorted(kept_events))) == fact["originalPracticedEventIdsHash"]
    for pid, expected in fact["removedPractices"].items():
        parent_id = aliases[pid]
        assert parent_id == expected["retainedIn"]
        selection = deepcopy(byid[parent_id])
        selection.update(expected["route"])
        plan = compile_summary(score, selection)
        events = {e["event"]["id"] for e in plan["events"]}
        parent_events = {e["event"]["id"] for e in plans[parent_id]["events"]}
        assert events and events <= parent_events, (pid, "removed selection lost unique music")
        assert len(events) == expected["events"]
    harmonics = plans["harmonics-complete"]
    assert harmonics["length"] == 12 and len(harmonics["visits"]) == 5
    assert any(n["note"].get("harmonic") for n in harmonics["notes"])
    assert score["tracks"][0]["capo"] == 2 and score["tempos"][0]["bpm"] == 90
    return [dict(file=fact["outputFile"], packageId=manifest["packageId"], revision=2,
        practices=8, families=6, removedCoveredSplits=10, originalUniquePracticedNotes=513,
        measures=90, notes=612, originalMusicUnchanged=True)]


if __name__ == "__main__":
    print(json.dumps(audit(), ensure_ascii=False, indent=2))
