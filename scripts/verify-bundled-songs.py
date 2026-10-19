"""Independent v77 content audit against retained source CSV and reviewed PDF facts.

This does not import the migration converter or round-trip its output as source evidence.
"""
import csv
import json
from pathlib import Path
from fractions import Fraction
from song_package.protocol import read_container, validate_payloads, q, digest

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets/song-packages"
RESOURCES=ROOT/"app/src/test/resources"


def audit():
    results=[]
    for path in sorted(ASSETS.glob("*.gpractice")):
        roots=validate_payloads(read_container(path),capabilities=json.loads((ROOT/"contracts/song-package/v1/capabilities.json").read_text()))
        score=roots["score.json"];practices=[p for p in roots["practices.json"]["practices"] if p.get("visibility","core")=="core"]
        title=roots["manifest.json"]["display"]["title"]
        if roots["manifest.json"]["packageId"].startswith("local.teaching-"):
            continue
        if path.name.startswith("usotsuki"):
            original=validate_payloads(read_container(ROOT/"scripts/archive/teaching-v31/packages/usotsuki-v1.gpractice"))
            assert score==original["score.json"]
            assert len(practices)==1 and len(score["voicings"])==4
            results.append(dict(song=title,practices=1,voicings=4,verified="v76 accepted musical score unchanged; reviewed teaching classification migrated"))
            continue
        measures={m["id"]:m for m in score["measures"]}
        events=score["voices"][0]["events"]
        if path.name.startswith("mozart"):
            expected=list(csv.reader((RESOURCES/"mozart-k265-v2.csv").open(encoding="utf-8")))[1:]
            actual=[]
            for e in events:
                n=e["notes"][0] if e["notes"] else None
                actual.append([int(measures[e["measureId"]]["label"]),int(q(e["onset"])*12),int(q(e["duration"])*12),
                    n["string"] if n else 0,n["fret"] if n else 0,n["midi"] if n else 0])
            assert actual==[list(map(int,r)) for r in expected]
            assert len(practices)==3 and len(score["measures"])==24 and sum(len(e["notes"]) for e in events)==165
            assert [p["legacyIds"] for p in practices]==[["K265-A","K265-1","K265-2"],["K265-B","K265-3","K265-4"],["K265-C","K265-5","K265-6"]]
        else:
            shayou=path.name.startswith("shayou")
            rows=list(csv.DictReader((RESOURCES/("shayou-gp.csv" if shayou else "your-trip-pdf.csv")).open(encoding="utf-8")))
            pdf=json.loads((ROOT/"scripts/score-data/your-trip.json").read_text(encoding="utf-8")) if not shayou else None
            tied={r["toNoteId"] for r in score["relations"] if r["kind"]=="tie"}
            for p in practices:
                selected=[e for e in events if e["id"].startswith(p["id"]+"-e-")]
                by_at={(int(measures[e["measureId"]]["label"]),int(q(e["onset"])*12)):e for e in selected}
                part=next((part for part in pdf["parts"] if part["id"]==p["id"]),None) if pdf else None
                if shayou:
                    grouped={}
                    for row in rows:
                        if row["phrase"]==p["id"]:grouped.setdefault((int(row["bar"]),int(row["tick"])),[]).append(row)
                    assert set(by_at)==set(grouped)
                    for at,expected_rows in grouped.items():
                        assert {n["string"] for n in by_at[at]["notes"]}=={int(r["string"]) for r in expected_rows if int(r["string"])}
                for row in rows:
                    bar,tick=int(row["bar"]),int(row["tick"])
                    if shayou and row["phrase"]!=p["id"]:continue
                    if part and not ((bar,tick)>=(part["first"],part["start"]) and (bar,tick)<(part["last"],part["end"])):continue
                    e=by_at[(bar,tick)]
                    assert q(e["duration"])*12==int(row["duration"]),(p["id"],bar,tick,"duration")
                    string=int(row["string"])
                    if string==0:
                        assert not e["notes"],(p["id"],bar,tick,"rest")
                    else:
                        n=next(n for n in e["notes"] if n["string"]==string)
                        assert bool(n.get("dead"))==bool(int(row["dead"])),(p["id"],bar,tick,"dead")
                        if shayou:
                            assert n["fret"]==int(row["fret"]),(p["id"],bar,tick,"fret including dead head")
                            assert n["midi"]==int(row["midi"]),(p["id"],bar,tick,"stored MIDI including dead head")
                        if not n.get("dead"):
                            assert n["fret"]==int(row["fret"]),(p["id"],bar,tick,"fret")
                            if shayou:assert n["midi"]==int(row["midi"])
                            assert (n["id"] in tied)==bool(int(row["tie"])),(p["id"],bar,tick,"tie")
                if pdf:
                    for bar in pdf["bars"]:
                        for source in bar["events"]:
                            at=(bar["number"],source["tick"])
                            if not (at>=(part["first"],part["start"]) and at<(part["last"],part["end"])):continue
                            e=by_at[at]
                            assert {(n["string"],n["fret"]) for n in e["notes"] if not n.get("dead")}=={tuple(n) for n in source["notes"]}
                            if source.get("triplet"):assert e["written"]["tupletGroupIds"]
                            for n in e["notes"]:
                                if n["id"] in tied:continue
                                art=n["articulation"]
                                if source.get("vibrato"):assert art.get("vibrato",{}).get("depth",0)>0
                                if source.get("ringToBarEnd"):assert art.get("letRing")
                                if source.get("grace",{}).get("string")==n["string"]:
                                    assert art["ornament"]["notes"][0]["fret"]==source["grace"]["fret"]
                                for bend in source.get("bends",[])+([source["bend"]] if source.get("bend") else []):
                                    if bend["string"]==n["string"]:
                                        assert [(q(point["at"])*12,point["semitones"]) for point in art["bend"]]==[(Fraction(str(t)),v) for t,v in bend["points"]]
            assert len(practices)==(5 if shayou else 6)
            assert len(score["voicings"])==(16 if shayou else 0)
            if shayou:
                source_grips=set()
                groups={}
                for row in rows:groups.setdefault((row["phrase"],row["bar"],row["tick"]),[]).append(row)
                for group in groups.values():
                    grip=frozenset((int(r["string"]),int(r["fret"])) for r in group if int(r["string"]) and not int(r["dead"]))
                    if len(grip)>1:source_grips.add(grip)
                assert {frozenset((i+1,fret) for i,fret in enumerate(v["frets"]) if fret>=0) for v in score["voicings"]}==source_grips
            assert all(p["visibility"]=="core" and not p.get("variantOf") for p in practices)
        results.append(dict(song=title,practices=len(practices),voicings=len(score["voicings"]),coverage=roots["review.json"]["coverage"],verified="independent retained source rows"))
    import importlib.util
    spec=importlib.util.spec_from_file_location("teaching_audit",ROOT/"scripts/audit-teaching-v31.py")
    module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    results.append(dict(teaching=module.audit()))
    return results


if __name__=="__main__":
    result=audit()
    print(json.dumps(result,ensure_ascii=False,indent=2))
