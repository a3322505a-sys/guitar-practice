"""Contract and transaction-input regression; the App runs the same fixtures independently."""
import unittest
from copy import deepcopy
import tempfile
import subprocess
import io
import zipfile
from song_package.protocol import *
from song_package.examples import fixtures

class PackageTests(unittest.TestCase):
    def setUp(self):
        self.payloads=read_container(CONTRACT/"fixtures/simple.gpractice")
    def rebound(self,filename,mutate):
        payloads=dict(self.payloads)
        value=strict_json(payloads[filename]);mutate(value);payloads[filename]=dumps(value)
        review=strict_json(payloads["review.json"])
        if filename!="review.json": review["hashes"][filename]=digest(payloads[filename])
        payloads["review.json"]=dumps(review)
        manifest=strict_json(payloads["manifest.json"])
        manifest["files"]={k:dict(bytes=len(v),sha256=digest(v)) for k,v in payloads.items() if k!="manifest.json"}
        payloads["manifest.json"]=dumps(manifest)
        return payloads
    def rejects(self,payloads,code):
        with self.assertRaises(Blocked) as caught: validate_payloads(payloads)
        self.assertEqual(code,caught.exception.diagnostic["code"])
    def test_all_original_shared_fixtures(self):
        for name in fixtures():
            roots=validate_payloads(read_container(CONTRACT/("fixtures/"+name+".gpractice")))
            self.assertEqual("example."+name,roots["manifest.json"]["packageId"])
    def test_same_positive_negative_corpus_as_android(self):
        cases=strict_json((CONTRACT/"fixtures/cases.json").read_bytes())
        for case in cases:
            if case["valid"]: validate_payloads(read_container(CONTRACT/"fixtures"/case["file"]))
            else:
                with self.assertRaises(Blocked) as caught: validate_payloads(read_container(CONTRACT/"fixtures"/case["file"]))
                self.assertEqual(case["code"],caught.exception.diagnostic["code"],case["file"])
    def test_raw_hash_does_not_exempt_schema_music_or_review(self):
        self.rejects(self.rebound("score.json",lambda s:s.update(unknownMusic=True)),"SCHEMA")
        self.rejects(self.rebound("score.json",lambda s:s["voices"][0]["events"][0]["notes"][0].update(midi=3)),"MUSIC")
        self.rejects(self.rebound("score.json",lambda s:s["voices"][0]["events"][1].update(onset=rational(F(1,2)))),"MUSIC")
        self.rejects(self.rebound("score.json",lambda s:s["tracks"][0].update(capo=2)),"MUSIC")
        self.rejects(self.rebound("review.json",lambda r:r.update(method="TODO independent review")),"REVIEW_NEEDED")
        self.rejects(self.rebound("review.json",lambda r:r["coverage"].update(notes=0)),"COVERAGE")
    def test_no_conditional_bypass_unknown_technique(self):
        payloads=self.rebound("review.json",lambda r:r.update(status="conditional",assumptions=["source assumption"],
            diagnostics=[dict(category="review-needed",pointer="/score",featureId="unknown.bend.v1",reason="unknown technique",
                practiceIds=["practice"],handling="assumption")]))
        self.rejects(payloads,"REVIEW_NEEDED")
    def test_duplicate_unicode_nonfinite_and_q_limits(self):
        for value in [b'{"x":1,"x":2}',b'[NaN]',b'[1e9999]',b'["\\ud800"]',b'{"n":2,"d":2}',b'{"n":0,"d":9}']:
            with self.assertRaises(Blocked): strict_json(value)
    def test_capability_omission_and_unknown_requirements(self):
        for requires in [[],["unknown.v1"]]:
            payloads=dict(self.payloads);m=strict_json(payloads["manifest.json"]);m["requires"]=requires;payloads["manifest.json"]=dumps(m)
            self.rejects(payloads,"CAPABILITY_DECLARATION")
    def test_form_expected_visits_are_handwritten(self):
        s=deepcopy(fixtures()["simple"][0])
        s["measures"]=[dict(s["measures"][0],id="m"+str(i)) for i in range(4)]
        s["form"]=dict(repeats=[dict(id="repeat",start="m0",end="m1",times=2)],
            endings=[dict(repeatId="repeat",start="m1",end="m1",passes=[1]),dict(repeatId="repeat",start="m2",end="m2",passes=[2])])
        self.assertEqual([0,1,0,2,3],[v["measureIndex"] for v in form_visits(s)])
        s["form"]=dict(markers=[dict(id="fine",measureId="m1",kind="fine")],jumps=[dict(id="dc",at="m3",target="m0",kind="dc")])
        self.assertEqual([0,1,2,3,0,1],[v["measureIndex"] for v in form_visits(s)])
    def test_polyphonic_same_string_conflict(self):
        s,ps=deepcopy(fixtures()["simple"])
        other=deepcopy(s["voices"][0]);other["id"]="lower"
        for e in other["events"]:
            e["id"]="lower-"+e["id"]
            for n in e["notes"]:n["id"]="lower-"+n["id"];n["fret"]+=1;n["midi"]+=1
        s["voices"].append(other)
        with self.assertRaisesRegex(Blocked,"voice conflict"): validate_music(s,ps)
    def test_partial_relations_and_tuplets_require_explicit_boundary_policies(self):
        score,ps=deepcopy(fixtures()["ties"])
        ps[0]["segments"]=[dict(start=dict(measureId="tie-b"),end=dict(measureId="tie-b",offset=rational(4)))]
        with self.assertRaises(Blocked) as caught: compile_summary(score,ps[0])
        self.assertEqual("BOUNDARY",caught.exception.diagnostic["code"])
        boundary=ps[0]["segments"][0];boundary["affectedNoteIds"]=["tie-origin","tie-target"]
        for entry,count,onsets in [("carryIn",2,1),("reattack",1,1),("truncate",0,0)]:
            boundary["entry"]=entry;plan=compile_summary(score,ps[0])
            self.assertEqual((count,onsets),(len(plan["notes"]),plan["onsets"]))
        score,ps=deepcopy(fixtures()["cross-tuplet"])
        ps[0]["segments"][0]["end"]=dict(measureId="cross-a",offset=score["measures"][0]["actualDuration"])
        with self.assertRaises(Blocked) as caught: compile_summary(score,ps[0])
        self.assertEqual("BOUNDARY",caught.exception.diagnostic["code"])
    def test_shared_voice_heads_have_one_attack_and_cannot_hide_different_sound(self):
        score,ps=deepcopy(fixtures()["shared"])
        validate_music(score,ps)
        self.assertEqual(4,coverage(score,ps)["onsets"])
        score["voices"][1]["events"][0]["notes"][0]["articulation"]["velocity"]=.5
        with self.assertRaises(Blocked): validate_music(score,ps)
    def test_zip_path_duplicate_symlink_extra_and_byte_budget(self):
        with tempfile.TemporaryDirectory() as temporary:
            for name,mode in [("../score.json",0o100644),("score.json",0o120777),("/absolute.json",0o100644),("evil.py",0o100644)]:
                path=Path(temporary)/"invalid.gpractice"
                with zipfile.ZipFile(path,"w") as archive:
                    for original,data in self.payloads.items():
                        info=zipfile.ZipInfo(name if original=="score.json" else original)
                        info.create_system=3;info.external_attr=mode<<16;archive.writestr(info,data)
                with self.assertRaises(Blocked): read_container(path)
            path=Path(temporary)/"large.gpractice";path.write_bytes(bytes(LIMIT_INPUT+1))
            with self.assertRaises(Blocked): read_container(path)
    def test_pack_is_reproducible_and_cannot_overwrite_candidate(self):
        with tempfile.TemporaryDirectory() as temporary:
            a,b=Path(temporary)/"a.gpractice",Path(temporary)/"b.gpractice"
            self.assertEqual(write_zip(self.payloads,a),write_zip(self.payloads,b))
            with self.assertRaises(Blocked): write_zip(self.payloads,a)
    def test_bundled_content_matches_independent_source_facts(self):
        import importlib.util
        spec=importlib.util.spec_from_file_location("bundled_audit",CONTRACT.parents[2]/"scripts/verify-bundled-songs.py")
        audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
        self.assertEqual(4,len(audit.audit()))

if __name__=="__main__": unittest.main(verbosity=2)
