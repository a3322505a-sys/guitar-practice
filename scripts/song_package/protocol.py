"""Strict container, schema, musical semantics and bounded form, independently checked by Kotlin."""
from fractions import Fraction as F
from pathlib import Path
import hashlib
import io
import json
import math
import zipfile
import sys
from bisect import bisect_right
from decimal import Decimal

try:
    import jsonschema
except ModuleNotFoundError:
    # Local optional dependency install; standalone installations use requirements.txt.
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]/"work/music-package/python-deps"))
    import jsonschema

CONTRACT = Path(__file__).resolve().parents[2]/"contracts/song-package/v1"
FILES = ["manifest.json","score.json","practices.json","provenance.json","review.json"]
LIMIT_INPUT=16*1024*1024
LIMIT_JSON=32*1024*1024
LIMIT_TOTAL=64*1024*1024

class Blocked(ValueError):
    def __init__(self, code, pointer, reason, source_ref=None):
        self.diagnostic=dict(code=code,pointer=pointer,reason=reason,category="unsupported-blocker",sourceRef=source_ref)
        super().__init__(code+" "+pointer+": "+reason)

def check(condition,code,pointer,reason):
    if not condition: raise Blocked(code,pointer,reason)
def digest(data): return hashlib.sha256(data).hexdigest()
def dumps(data): return (json.dumps(data,ensure_ascii=False,indent=2,allow_nan=False)+"\n").encode("utf-8")
def q(value):
    check(isinstance(value,dict) and set(value)=={"n","d"},"Q","","Expected n,d")
    n,d=value["n"],value["d"]
    check(type(n) is int and type(d) is int and abs(n)<=10**12 and 0<d<=10**12,"Q","","Rational limit")
    f=F(n,d)
    check(f.numerator==n and f.denominator==d,"Q","","Rational must be canonical")
    return f
def bounded(value,pointer="/score/time"):
    check(abs(value.numerator)<=10**12 and value.denominator<=10**12,"Q",pointer,"Rational arithmetic exceeds 10^12")
    return value
def rational(value):
    value=F(value)
    return dict(n=value.numerator,d=value.denominator)
def strict_json(data):
    check(len(data)<=LIMIT_JSON,"LIMIT","/json","Single JSON byte limit")
    def pairs(items):
        result={}
        for key,value in items:
            check(key not in result,"JSON","","Duplicate key: "+key)
            result[key]=value
        return result
    def finite(token): raise Blocked("JSON","","Non-finite number: "+token)
    def numeric(token):
        check(len(token)<=64,"JSON","","Numeric token limit")
        value=Decimal(token)
        check(abs(value.as_tuple().exponent)<=1000 and math.isfinite(float(value)),"JSON","","Numeric exponent/finite limit")
        return int(value) if value==value.to_integral_value() else float(value)
    try: result=json.loads(data.decode("utf-8",errors="strict"),object_pairs_hook=pairs,parse_constant=finite,parse_int=numeric,parse_float=numeric)
    except (ValueError,UnicodeError,RecursionError) as e:
        if isinstance(e,Blocked): raise
        raise Blocked("JSON","",str(e)) from e
    nodes=0
    def visit(v,depth,pointer):
        nonlocal nodes
        nodes+=1
        check(depth<=48 and nodes<=2000000,"LIMIT",pointer,"JSON depth/node budget")
        if isinstance(v,dict):
            if set(v)=={"n","d"} and not isinstance(v["n"],dict): q(v)
            for k,x in v.items(): visit(x,depth+1,pointer+"/"+k.replace("~","~0").replace("/","~1"))
        elif isinstance(v,list):
            for i,x in enumerate(v): visit(x,depth+1,pointer+"/"+str(i))
        elif isinstance(v,str):
            check(len(v)<=262144 and not any(0xd800<=ord(c)<=0xdfff for c in v),"JSON",pointer,"Invalid Unicode/string budget")
        elif isinstance(v,float): check(math.isfinite(v),"JSON",pointer,"Non-finite number")
    visit(result,0,"")
    return result

def schemas_validate(roots,contract=CONTRACT):
    for name,data in roots.items():
        schema=strict_json((contract/(name.replace(".json",".schema.json"))).read_bytes())
        errors=sorted(jsonschema.Draft202012Validator(schema).iter_errors(data),key=lambda e:list(map(str,e.path)))
        if errors:
            e=errors[0]; pointer="/"+name.removesuffix(".json")+"/"+"/".join(str(x) for x in e.absolute_path)
            raise Blocked("SCHEMA",pointer,e.message)

def read_container(path):
    data=Path(path).read_bytes()
    check(len(data)<=LIMIT_INPUT,"LIMIT","/container","Compressed input exceeds 16 MiB")
    payloads={};total=0
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries=archive.infolist()
            check(len(entries)==5,"ZIP_FILES","/container","Exactly five entries required")
            for entry in entries:
                name=entry.filename
                check(name in FILES and name not in payloads and not entry.is_dir() and
                    not entry.flag_bits&1 and entry.compress_type in (0,8) and
                    (entry.external_attr>>16)&0xf000!=0xa000,"ZIP_ENTRY","/container/"+name,
                    "Unexpected/duplicate/encrypted/symlink/path entry")
                out=bytearray()
                with archive.open(entry) as source:
                    while chunk:=source.read(8192):
                        out.extend(chunk);total+=len(chunk)
                        check(len(out)<=LIMIT_JSON and total<=LIMIT_TOTAL,"LIMIT","/container/"+name,"Inflated byte limit")
                payloads[name]=bytes(out)
    except zipfile.BadZipFile as e: raise Blocked("ZIP","/container",str(e)) from e
    check(set(payloads)==set(FILES),"ZIP_FILES","/container","Exactly five files required")
    return payloads

def form_visits(score):
    indices={m["id"]:i for i,m in enumerate(score["measures"])}
    form=score["form"]
    def index(id):
        check(id in indices,"FORM_REF","/score/form","Missing measure: "+id)
        return indices[id]
    repeats=[(r,index(r["start"]),index(r["end"])) for r in form.get("repeats",[])]
    check(len({r["id"] for r,a,b in repeats})==len(repeats),"FORM_ID","/score/form","Duplicate repeat")
    for i,(r,a,b) in enumerate(repeats):
        check(a<=b,"FORM_RANGE","/score/form","Reversed repeat")
        for other,c,d in repeats[i+1:]:
            check(b<c or d<a or a<=c<=d<=b or c<=a<=b<=d,"FORM_CROSSING","/score/form","Crossing repeats")
            check((a,b)!=(c,d),"FORM_DUPLICATE","/score/form","Identical repeats")
    check(all(sum(a<=i<=b for r,a,b in repeats)<=4 for i in indices.values()),"FORM_LIMIT","/score/form","Repeat nesting")
    endings=[]
    for e in form.get("endings",[]):
        parent=next((r for r,a,b in repeats if r["id"]==e["repeatId"]),None)
        check(parent is not None,"FORM_REF","/score/form/endings","Missing repeat")
        a,b=index(e["start"]),index(e["end"])
        check(a<=b and a>=index(parent["start"]) and all(1<=p<=parent["times"] for p in e["passes"]),
            "FORM_ENDING","/score/form/endings","Invalid ending")
        endings.append((e,a,b))
    markers={m["id"]:m for m in form.get("markers",[])}
    check(len(markers)==len(form.get("markers",[])),"FORM_ID","/score/form/markers","Duplicate marker")
    for m in markers.values(): index(m["measureId"])
    jumps=form.get("jumps",[])
    check(len({j["id"] for j in jumps})==len(jumps),"FORM_ID","/score/form/jumps","Duplicate jump")
    for j in jumps:
        index(j["at"])
        check(j["kind"]=="dc" and j["target"]==score["measures"][0]["id"] or j["target"] in markers and
            (j["kind"]!="ds" or markers[j["target"]]["kind"]=="segno") and
            (j["kind"]!="to-coda" or markers[j["target"]]["kind"]=="coda"),"FORM_REF","/score/form/jumps","Wrong jump target")
    visits=[];passes={};counts={i:0 for i in indices.values()};used=set();at=0;after=False;steps=0
    while at<len(indices):
        steps+=1
        check(steps<=200000 and len(visits)<50000,"FORM_LIMIT","/score/form","Visit/step budget")
        active=[] if after and not form.get("repeatsAfterJump",False) else [(r,a,b) for r,a,b in repeats if a<=at<=b]
        for r,a,b in active: passes.setdefault(r["id"],1)
        skipped=next(((e,a,b) for e,a,b in endings if a<=at<=b and passes.get(e["repeatId"],1) not in e["passes"]),None)
        if skipped:
            e,first,last=skipped
            parent=max(((r,a,b) for r,a,b in active if at<=b<=last),key=lambda item:item[1],default=None)
            if parent and passes[parent[0]["id"]]<parent[0]["times"]:
                r,a,b=parent;passes[r["id"]]+=1
                for child,c,d in repeats:
                    if a<=c<=d<=b and child!=r: passes.pop(child["id"],None)
                at=a
            else: at=last+1
            continue
        counts[at]+=1
        relevant={r["id"] for r,a,b in active}|{e["repeatId"] for e,a,b in endings if a<=at<=b}
        visits.append(dict(measureIndex=at,occurrence=counts[at],repeatContext={id:p for id,p in passes.items() if id in relevant},afterJump=after))
        id=score["measures"][at]["id"]
        if after and any(m["kind"]=="fine" and m["measureId"]==id for m in markers.values()): break
        jump=next((j for j in jumps if j["at"]==id and j["id"] not in used and counts[at]>=j.get("occurrence",1) and
            (j["kind"]!="to-coda" or after) and condition_matches(j.get("condition"),counts[at],passes)),None)
        if jump:
            used.add(jump["id"]);after=True;passes.clear()
            at=0 if jump["kind"]=="dc" else index(markers[jump["target"]]["measureId"])
            continue
        parent=max(((r,a,b) for r,a,b in active if b==at and passes[r["id"]]<r["times"]),key=lambda item:item[1],default=None)
        if parent:
            r,a,b=parent;passes[r["id"]]+=1
            for child,c,d in repeats:
                if a<=c<=d<=b and child!=r: passes.pop(child["id"],None)
            at=a
        else: at+=1
    return visits

def condition_matches(condition,occurrence,passes):
    return not condition or ("occurrence" not in condition or condition["occurrence"]==occurrence) and all(
        passes.get(id) in allowed for id,allowed in condition.get("repeatPasses",{}).items())

def validate_music(score,practices):
    teaching = any("teaching" in v for v in score["voicings"]) or any("teaching" in p or p.get("legacyIds") for p in practices)
    caps={"score.rational.v1","guitar.six-string.v1"};ids=set()
    def add(id,path):
        check(id not in ids,"MUSIC",path,"Duplicate ID: "+id);ids.add(id)
    starts=[F(0)];measure_indices={}
    for i,m in enumerate(score["measures"]):
        path="/score/measures/"+str(i);add(m["id"],path);measure_indices[m["id"]]=i
        meter=m["meter"];nominal=F(4*meter["numerator"],meter["denominator"]);duration=q(m["actualDuration"])
        check(sum(meter["beatGroups"])==meter["numerator"],"MUSIC",path,"Group sum mismatch")
        check(0<duration<=nominal and (duration==nominal or m.get("incompleteReason")),"MUSIC",path,"Incomplete duration/reason")
        lead=q(m.get("leadIn",rational(0)))
        check(0<=lead and bounded(lead+duration,path)<=nominal,"MUSIC",path,"Lead-in outside nominal meter")
        if meter["denominator"]!=4: caps.add("meter.compound.v1")
        if len(set(meter["beatGroups"]))>1: caps.add("meter.irregular.v1")
        starts.append(bounded(starts[-1]+duration,path))
    check(starts[-1]<=100000,"MUSIC","/score/measures","Score duration budget")
    tracks={}
    for t in score["tracks"]:
        add(t["id"],"/score/tracks");tracks[t["id"]]=t
        if t["openStringMidi"]!=[64,59,55,50,45,40] or t.get("capo",0): caps.add("guitar.tuning-capo.v1")
    groups={g["id"]:g for g in score["tuplets"]}
    events={};notes={};voices={v["id"]:v for v in score["voices"]};ordinals={}
    if len(voices)>1: caps.add("score.polyphony.v1")
    check(all(sum(v["trackId"]==id for v in voices.values())<=4 for id in tracks),"MUSIC","/score/voices","Voice budget")
    def expr(a,duration,absolute,path):
        for key,feature in [("bend","bend"),("tremoloBar","tremolo-bar")]:
            points=a.get(key,[])
            check(all(0<=q(p["at"])<=duration for p in points) and all(q(x["at"])<q(y["at"]) for x,y in zip(points,points[1:])),
                "EXPRESSION",path,"Curve out of range/order")
            if points: caps.add("technique."+feature+".v1")
        if a.get("vibrato",{}).get("depth",0)>0: caps.add("technique.vibrato.v1")
        if a.get("slide","none")!="none": caps.add("technique.legato.v1")
        if a.get("tapping"): caps.add("technique.tapping.v1")
        if a.get("palmMute") or a.get("short") or a.get("cut"): caps.add("technique.mute.v1")
        if a.get("letRing"): caps.add("technique.sustain.v1")
        if a.get("strumDelayMs",0)>0: caps.add("technique.strum.v1")
        if "ringUntil" in a: check(absolute<q(a["ringUntil"])<=starts[-1],"EXPRESSION",path,"Invalid ring cutoff")
        if o:=a.get("ornament"):
            total=bounded(sum((q(g["duration"]) for g in o["notes"]),F(0)),path)
            check(all(q(g["duration"])>0 for g in o["notes"]) and total<duration and
                (o["timing"]!="before" or absolute>=total),"EXPRESSION",path,"Grace interval")
            check(not a.get("repluck") and not a.get("bend") and a.get("slide","none")=="none","EXPRESSION",path,"Grace combination")
            caps.add("technique.grace.v1")
        if r:=a.get("repluck"):
            unit=q(r["unit"]); count=bounded(duration/unit,path) if unit else F(0)
            check(unit>0 and count<=1024 and count.denominator==1 and (r["kind"]!="trill" or "alternateFret" in r),
                "EXPRESSION",path,"Repluck interval")
            check(not a.get("letRing") and not a.get("ornament") and a.get("slide","none")=="none","EXPRESSION",path,"Repluck combination")
            caps.add("technique.repluck.v1")
    for vi,v in enumerate(score["voices"]):
        path="/score/voices/"+str(vi);add(v["id"],path)
        check(v["trackId"] in tracks,"MUSIC",path,"Missing track");track=tracks[v["trackId"]];covered=F(0)
        for ei,e in enumerate(v["events"]):
            ep=path+"/events/"+str(ei);add(e["id"],ep)
            check(e["measureId"] in measure_indices,"MUSIC",ep,"Missing measure")
            mi=measure_indices[e["measureId"]];onset=q(e["onset"]);absolute=bounded(starts[mi]+onset,ep);duration=q(e["duration"])
            check(0<=onset<starts[mi+1]-starts[mi] and absolute==covered and 0<duration and bounded(absolute+duration,ep)<=starts[-1],
                "MUSIC",ep,"Voice coverage/onset/duration")
            covered=absolute+duration
            written=e["written"]; actual=sum((F(4,written["baseDenominator"]*2**i) for i in range(written.get("dots",0)+1)),F(0))
            group_ids=written.get("tupletGroupIds",[])
            check(len(group_ids)==len(set(group_ids)),"MUSIC",ep,"Duplicate tuplet")
            for id in group_ids:
                check(id in groups and groups[id]["voiceId"]==v["id"] and e["id"] in groups[id]["eventIds"],"MUSIC",ep,"Tuplet membership")
                actual=bounded(actual*F(groups[id]["normal"],groups[id]["actual"]),ep)
            check(e.get("measureRest",False) and not e["notes"] and onset==0 and duration==starts[mi+1]-starts[mi] or
                not e.get("measureRest",False) and actual==duration,"MUSIC",ep,"Written/actual duration mismatch")
            check(len({n["string"] for n in e["notes"]})==len(e["notes"]),"MUSIC",ep,"Same string in event")
            events[e["id"]]=(v,e,absolute)
            ordinals[e["id"]]=ei
            for ni,n in enumerate(e["notes"]):
                np=ep+"/notes/"+str(ni);add(n["id"],np)
                check(n["fret"]<=track.get("fretCount",24),"MUSIC",np,"Fret outside track")
                expected=track["openStringMidi"][n["string"]-1]+track.get("capo",0)+n["fret"]
                if h:=n.get("harmonic"):
                    check(n["midi"]==h["soundMidi"]==expected+int(math.floor(12*math.log2(h["partial"])+.5)) and
                        h["touchFret"]>n["fret"] and (h["kind"]!="natural" or n["fret"]==0),"MUSIC",np,"Harmonic pitch")
                    caps.add("technique.harmonic.v1")
                    touches={n["fret"]+int(math.floor(12*math.log2(h["partial"]/(h["partial"]-k))+.5))
                        for k in range(1,h["partial"]) if math.gcd(k,h["partial"])==1}
                    check(h["touchFret"] in touches,"MUSIC",np,"Harmonic touch node/partial mismatch")
                else: check(n["midi"]==expected,"MUSIC",np,"Tuning/capo pitch")
                art={**e.get("articulation",{}),**n.get("articulation",{})}
                expr(art,duration,absolute,np)
                if n.get("dead"): caps.add("technique.mute.v1")
                notes[n["id"]]=(v,e,n,absolute)
            if not e["notes"]: expr(e.get("articulation",{}),duration,absolute,ep)
        check(covered==starts[-1],"MUSIC",path,"Voice coverage end")
    check(len(notes)<=200000,"MUSIC","/score/voices","Note budget")
    for g in score["tuplets"]:
        add(g["id"],"/score/tuplets");members=g["eventIds"]
        check(len(members)==len(set(members)) and all(id in events and events[id][0]["id"]==g["voiceId"] and
            g["id"] in events[id][1]["written"].get("tupletGroupIds",[]) for id in members),"MUSIC","/score/tuplets","Invalid tuplet members")
        order=[ordinals[id] for id in members]
        check(all(b==a+1 for a,b in zip(order,order[1:])),"MUSIC","/score/tuplets","Member order")
        parent=g.get("parentId");seen={g["id"]}
        while parent:
            check(parent not in seen and parent in groups and len(seen)<4,"MUSIC","/score/tuplets","Parent/cycle/nesting")
            seen.add(parent);p=groups[parent]
            check(p["voiceId"]==g["voiceId"] and set(members)<=set(p["eventIds"]),"MUSIC","/score/tuplets","Parent containment")
            parent=p.get("parentId")
        caps.add("rhythm.tuplet.v1")
        if g.get("parentId"): caps.add("rhythm.nested-tuplet.v1")
    for voice in voices:
        def depth(g): return 0 if "parentId" not in g else 1+depth(groups[g["parentId"]])
        stack=[]
        for g in sorted([g for g in groups.values() if g["voiceId"]==voice],key=lambda g:
            (ordinals[g["eventIds"][0]],-ordinals[g["eventIds"][-1]],depth(g))):
            first,last=ordinals[g["eventIds"][0]],ordinals[g["eventIds"][-1]]
            while stack and ordinals[stack[-1]["eventIds"][-1]]<first: stack.pop()
            parent=stack[-1] if stack else None
            check(parent is None or last<=ordinals[parent["eventIds"][-1]],"MUSIC","/score/tuplets","Crossing groups")
            check(g.get("parentId")== (parent["id"] if parent else None),"MUSIC","/score/tuplets","Immediate parent required")
            stack.append(g)
    incoming=set();outgoing=set()
    for r in score["relations"]:
        add(r["id"],"/score/relations")
        check(r["fromNoteId"] in notes and r["toNoteId"] in notes,"MUSIC","/score/relations","Missing note")
        a,b=notes[r["fromNoteId"]],notes[r["toNoteId"]]
        check(a[0]["trackId"]==b[0]["trackId"] and b[3]>a[3],"MUSIC","/score/relations","Relation track/time")
        kind=r["kind"]
        if kind!="slur":
            check(a[2]["string"]==b[2]["string"] and a[3]+q(a[1]["duration"])==b[3] and
                not a[2].get("dead") and not b[2].get("dead"),"MUSIC","/score/relations","Same-string adjacent relation")
            check(r["toNoteId"] not in incoming and r["fromNoteId"] not in outgoing,"MUSIC","/score/relations","Ambiguous relation")
            incoming.add(r["toNoteId"]);outgoing.add(r["fromNoteId"])
            if kind=="tie": check(a[2]["midi"]==b[2]["midi"] and b[2].get("articulation",{}).get("pick",b[1].get("articulation",{}).get("pick"))=="none",
                "MUSIC","/score/relations","Tie pitch/attack")
            if kind=="tie": check(a[2]["fret"]==b[2]["fret"] and a[2].get("harmonic")==b[2].get("harmonic"),"MUSIC","/score/relations","Tie position/harmonic")
            if kind=="hammer": check(a[2]["fret"]<b[2]["fret"],"MUSIC","/score/relations","Hammer direction")
            if kind=="pull": check(a[2]["fret"]>b[2]["fret"],"MUSIC","/score/relations","Pull direction")
            source_art={**a[1].get("articulation",{}),**a[2].get("articulation",{})}
            target_art={**b[1].get("articulation",{}),**b[2].get("articulation",{})}
            check(target_art.get("pick","default")!="none" if kind=="slide-pick" else target_art.get("pick","default") in ("none","default"),
                "MUSIC","/score/relations","Relation/picking conflict")
            check(not source_art.get("repluck") and not target_art.get("repluck") and not target_art.get("ornament"),
                "MUSIC","/score/relations","Relation conflicts with derived target attacks")
        caps.add("technique.tie.v1" if kind=="tie" else "technique.legato.v1")
    for v,e,n,at in notes.values():
        art={**e.get("articulation",{}),**n.get("articulation",{})}
        check(art.get("pick")!="none" or n["id"] in incoming or n.get("sharedSoundId"),"MUSIC","/score/relations","Unpicked note needs relation/shared sound")
        check(not n.get("harmonic") or art.get("repluck",{}).get("kind")!="trill","MUSIC","/score/voices","Harmonic trill needs adapted sound pitches")
    shared={}
    for value in notes.values():
        if id:=value[2].get("sharedSoundId"): shared.setdefault(id,[]).append(value)
    defaults=dict(velocity=1,accent=False,palmMute=False,short=False,letRing=False,cut=False,strumDelayMs=0,
        bend=[],vibrato=dict(depth=0,rateHz=5),tremoloBar=[],slide="none",tapping=False)
    for heads in shared.values():
        first=heads[0]
        def state(head): return {k:v for k,v in {**defaults,**head[1].get("articulation",{}),**head[2].get("articulation",{})}.items() if k!="pick"}
        check(len(heads)>=2 and all(
            h[0]["trackId"]==first[0]["trackId"] and h[3]==first[3] and h[1]["duration"]==first[1]["duration"] and
            all(h[2].get(k)==first[2].get(k) for k in ("string","fret","midi","harmonic")) and
            h[2].get("dead",False)==first[2].get("dead",False) and state(h)==state(first) and
            not any(r["fromNoteId"]==h[2]["id"] or r["toNoteId"]==h[2]["id"] for r in score["relations"])
            for h in heads) and any(h[2].get("articulation",{}).get("pick",h[1].get("articulation",{}).get("pick","default"))!="none" for h in heads),
            "MUSIC","/score/voices","Shared sound requires matching simultaneous heads, one attack and no acoustic relations")
    occupancy={}
    for value in notes.values(): occupancy.setdefault((value[0]["trackId"],value[2]["string"]),[]).append(value)
    for values in occupancy.values():
        active=None;until=F(0)
        for n in sorted(values,key=lambda v:v[3]):
            if active and n[3]<until:
                check(active[2].get("sharedSoundId") and active[2].get("sharedSoundId")==n[2].get("sharedSoundId") and
                    active[2]["midi"]==n[2]["midi"] and active[3]==n[3] and active[1]["duration"]==n[1]["duration"],
                    "MUSIC","/score/voices","Same-track/string voice conflict")
            end=n[3]+q(n[1]["duration"])
            if end>until: active=n;until=end
    tempos=score["tempos"]
    check(q(tempos[0]["at"])==0 and "condition" not in tempos[0],"MUSIC","/score/tempos","Unconditional initial tempo")
    for i,t in enumerate(tempos):
        at=q(t["at"])
        check(0<=at<starts[-1] and q(t["beatUnit"])>0 and (i==0 or q(tempos[i-1]["at"])<=at),"MUSIC","/score/tempos","Invalid/order tempo")
        if i and q(tempos[i-1]["at"])==at: check("condition" in t,"MUSIC","/score/tempos","Conditional override after default")
        check(("rampEnd" in t)==("endBpm" in t),"MUSIC","/score/tempos","Ramp endpoints")
        if "rampEnd" in t: check(at<q(t["rampEnd"])<=starts[-1],"MUSIC","/score/tempos","Invalid ramp")
    if len(tempos)>1 or any("rampEnd" in t for t in tempos): caps.add("tempo.map.v1")
    for d in score["dynamics"]:
        check(0<=q(d["at"])<starts[-1] and ("trackId" not in d or d["trackId"] in tracks) and
            ("rampEnd" in d)==("endVelocity" in d) and ("rampEnd" not in d or q(d["at"])<q(d["rampEnd"])<=starts[-1]),
            "MUSIC","/score/dynamics","Invalid dynamics")
    if score["dynamics"]: caps.add("technique.dynamics.v1")
    repeats={r["id"]:r for r in score["form"].get("repeats",[])}
    conditions=[item["condition"] for collection in [score["tempos"],score["dynamics"],score["harmonies"],score["form"].get("jumps",[])] for item in collection if "condition" in item]
    for condition in conditions:
        check(all(id in repeats and all(1<=p<=repeats[id]["times"] for p in passes)
            for id,passes in condition.get("repeatPasses",{}).items()),"MUSIC","/score/condition","Repeat/pass reference")
    if conditions: caps.add("form.condition.v1")
    for collection in [score["form"].get("repeats",[]),score["form"].get("markers",[]),score["form"].get("jumps",[])]:
        for item in collection: add(item["id"],"/score/form")
    for i,s in enumerate(score["swing"]):
        a,b,u=q(s["start"]),q(s["end"]),q(s["unit"])
        check(0<=a<b<=starts[-1] and u>0 and ((b-a)/(2*u)).denominator==1 and
            set(s.get("trackIds",[]))<=tracks.keys() and set(s.get("voiceIds",[]))<=voices.keys(),"MUSIC","/score/swing","Range/scope/pairs")
        for other in score["swing"][:i]:
            check(not (a<q(other["end"]) and q(other["start"])<b and
                (not s.get("trackIds") and not s.get("voiceIds") or not other.get("trackIds") and not other.get("voiceIds") or
                    set(s.get("trackIds",[]))&set(other.get("trackIds",[])) or set(s.get("voiceIds",[]))&set(other.get("voiceIds",[])))),
                "MUSIC","/score/swing","Overlapping scopes")
    if score["swing"]: caps.add("rhythm.swing.v1")
    for v in score["voicings"]:
        add(v["id"],"/score/voicings")
        check(v["trackId"] in tracks and any(f>=0 for f in v["frets"]) and all(-1<=f<=tracks[v["trackId"]].get("fretCount",24) for f in v["frets"]),"MUSIC","/score/voicings","Invalid voicing")
    for h in score["harmonies"]:
        add(h["id"],"/score/harmonies")
        check(h["measureId"] in measure_indices and 0<=q(h["onset"])<q(score["measures"][measure_indices[h["measureId"]]]["actualDuration"]) and
            ("voicingId" not in h or any(v["id"]==h["voicingId"] for v in score["voicings"])),"MUSIC","/score/harmonies","Harmony reference")
    practice_ids={p["id"] for p in practices}
    for p in practices:
        add(p["id"],"/practices")
        check(p["mainTrackId"] in p["mixTrackIds"] and set(p["mixTrackIds"])<=tracks.keys() and len(set(p["mixTrackIds"]))==len(p["mixTrackIds"]),
            "MUSIC","/practices","Invalid participating tracks")
        check(q(p.get("padding",rational(0)))>=0 and (not q(p.get("padding",rational(0))) or p.get("paddingReason")),"MUSIC","/practices","Padding reason")
        for segment in p["segments"]:
            for c in [segment["start"],segment["end"]]:
                check(c["measureId"] in measure_indices and 0<=q(c.get("offset",rational(0)))<=q(score["measures"][measure_indices[c["measureId"]]]["actualDuration"]),
                    "MUSIC","/practices","Invalid coordinate")
            check(set(segment.get("affectedNoteIds",[]))<=notes.keys(),"MUSIC","/practices","Unknown boundary note")
        check(set(p.get("relatedIds",[])+[p[k] for k in ("familyId","variantOf") if k in p])<=practice_ids,"MUSIC","/practices","Family/variant reference")
        parent=p.get("variantOf");seen={p["id"]}
        while parent:
            check(parent not in seen,"MUSIC","/practices","Variant cycle");seen.add(parent)
            parent=next(x for x in practices if x["id"]==parent).get("variantOf")
    form_visits(score)
    if score["form"].get("repeats"): caps.add("form.repeat.v1")
    if score["form"].get("endings"): caps.add("form.volta.v1")
    if score["form"].get("jumps"): caps.add("form.jump.v1")
    if teaching: caps.add("catalogue.teaching.v1")
    legacy_ids=[id for p in practices for id in p.get("legacyIds",[])]
    check(len(set(legacy_ids))==len(legacy_ids),"MUSIC","/practices/legacyIds","Duplicate legacy ID")
    return caps

def compile_summary(score,practice,routes=None):
    starts=[F(0)]
    for m in score["measures"]: starts.append(bounded(starts[-1]+q(m["actualDuration"])))
    indices={m["id"]:i for i,m in enumerate(score["measures"])}
    spans=[(v,e,starts[indices[e["measureId"]]]+q(e["onset"])) for v in score["voices"] if v["trackId"] in practice["mixTrackIds"] for e in v["events"]]
    by_voice={}
    for span in spans: by_voice.setdefault(span[0]["id"],[]).append(span)
    voice_starts={id:[span[2] for span in values] for id,values in by_voice.items()}
    note_spans={n["id"]:span for span in spans for n in span[1]["notes"]}
    event_spans={span[1]["id"]:span for span in spans}
    acoustic_incoming={r["toNoteId"]:r["fromNoteId"] for r in score["relations"] if r["kind"] not in ("slur","slide-pick")}
    def overlapping(a,b):
        for id,values in by_voice.items():
            index=max(0,bisect_right(voice_starts[id],a)-1)
            while index<len(values) and values[index][2]<b:
                span=values[index]
                if span[2]+q(span[1]["duration"])>a: yield span
                index+=1
    visits=[];events=[];at=F(0)
    for si,segment in enumerate(practice["segments"]):
        if routes is None: routes=dict(performed=form_visits(score),written=[
            dict(measureIndex=i,occurrence=1,repeatContext={},afterJump=False) for i in range(len(indices))])
        route=routes[segment.get("formPolicy","written")]
        def point(c):
            values=[i for i,v in enumerate(route) if score["measures"][v["measureIndex"]]["id"]==c["measureId"] and
                ("occurrence" not in c or v["occurrence"]==c["occurrence"])]
            check(values and (len(values)==1 or "occurrence" in c),"PRACTICE_AMBIGUOUS","/practices","Coordinate requires unique occurrence")
            return values[0],q(c.get("offset",rational(0)))
        first,offset_a=point(segment["start"]);last,offset_b=point(segment["end"])
        check(first<last or first==last and offset_a<offset_b,"PRACTICE_RANGE","/practices","Empty/reversed segment")
        initial=len(visits);event_initial=len(events);segment_at=at
        source_entry=starts[route[first]["measureIndex"]]+offset_a
        for ri in range(first,last+1):
            source=route[ri];mi=source["measureIndex"]
            a=starts[mi]+(offset_a if ri==first else 0);b=starts[mi]+(offset_b if ri==last else starts[mi+1]-starts[mi])
            if a==b: continue
            for swing in score["swing"]:
                sa,sb,u=q(swing["start"]),q(swing["end"]),q(swing["unit"])
                if sa<b and a<sb:
                    check(((max(sa,a)-sa)/(2*u)).denominator==1 and ((min(sb,b)-sa)/(2*u)).denominator==1,
                        "SWING_BOUNDARY","/practices","Selection cuts swing pair")
            continuous=visits and visits[-1]["segment"]==si and visits[-1]["sourceEnd"]==a
            vi=len(visits)
            visits.append(dict(measureIndex=mi,occurrence=source["occurrence"],segment=si,start=at,sourceStart=a,sourceEnd=b))
            check(len(visits)<=50000,"PLAN_LIMIT","/practices","Visit budget")
            check(len(visits)*(len(score["tempos"])+len(score["dynamics"])+len(score["swing"])+len(score["harmonies"]))<=5000000,
                "PLAN_LIMIT","/practices","State reconstruction work budget")
            for v,e,start in overlapping(a,b):
                if start<a and continuous: continue
                if start<a and not continuous:
                    check(segment.get("entry","reject")!="reject" and all(n["id"] in segment.get("affectedNoteIds",[]) for n in e["notes"]),
                        "BOUNDARY","/practices","Entry cuts event")
                new_start=at+start-a
                clipped=start<a and not continuous
                if clipped and segment.get("entry") in ("reattack","truncate"): new_start=at
                events.append(dict(voice=v["id"],event=e,start=new_start,end=at+start+q(e["duration"])-a,sourceStart=start,visit=vi,
                    suppressed={n["id"] for n in e["notes"]} if clipped and segment.get("entry")=="truncate" else set()))
            at=bounded(at+b-a,"/practices")
        crossing_events=[e for e in events[event_initial:] if e["end"]>at]
        crossing=[n for e in crossing_events for n in e["event"]["notes"]]
        check(segment.get("exit","reject")!="reject" or not crossing_events,"BOUNDARY","/practices","Exit cuts event")
        check(all(n["id"] in segment.get("affectedNoteIds",[]) for n in crossing),"BOUNDARY","/practices","Explicit affected notes required")
        selected={n["id"] for e in events[event_initial:] for n in e["event"]["notes"]}
        suppress=set();affected=set(segment.get("affectedNoteIds",[]))
        for relation in score["relations"]:
            source_selected=relation["fromNoteId"] in selected
            target_selected=relation["toNoteId"] in selected
            if source_selected==target_selected: continue
            policy=segment.get("entry" if target_selected else "exit","reject")
            check(policy!="reject","BOUNDARY","/practices","Selection cuts relation "+relation["id"])
            check({relation["fromNoteId"],relation["toNoteId"]}<=affected,"BOUNDARY","/practices","List affected relation endpoints")
            if target_selected and relation["kind"] not in ("slur","slide-pick"):
                if policy=="carryIn":
                    origin=relation["fromNoteId"];seen=set()
                    while origin not in seen:
                        seen.add(origin);v,e,start=note_spans[origin]
                        if not any(item["event"]["id"]==e["id"] for item in events[event_initial:]):
                            begin=segment_at+start-source_entry
                            events.append(dict(voice=v["id"],event=e,start=begin,end=begin+q(e["duration"]),
                                sourceStart=start,visit=initial,suppressed=set()))
                        if origin not in acoustic_incoming: break
                        origin=acoustic_incoming[origin]
                elif policy=="truncate": suppress.add(relation["toNoteId"])
        pending=list(suppress)
        acoustic_outgoing={r["fromNoteId"]:r["toNoteId"] for r in score["relations"] if r["kind"] not in ("slur","slide-pick")}
        while pending:
            target=acoustic_outgoing.get(pending.pop())
            if target in selected and target not in suppress: suppress.add(target);pending.append(target)
        check(suppress<=affected,"BOUNDARY","/practices","List truncated descendants")
        for e in events[event_initial:]: e["suppressed"].update(suppress)
        chosen={e["event"]["id"] for e in events[event_initial:]}
        for group in score["tuplets"]:
            members=set(group["eventIds"])
            if chosen & members and not members<=chosen:
                start=min(event_spans[id][2] for id in members)
                policy=segment.get("entry" if source_entry>start else "exit","reject")
                check(policy!="reject","BOUNDARY","/practices","Selection cuts tuplet "+group["id"])
                check({n["id"] for id in members for n in event_spans[id][1]["notes"]}<=affected,
                    "BOUNDARY","/practices","List affected tuplet notes")
        check(sum(len(e["event"]["notes"]) for e in events)<=100000 and len(events)<=200000,"PLAN_LIMIT","/practices","Practice note/event budget")
    padding=q(practice.get("padding",rational(0)))
    if padding:
        visits.append(dict(measureIndex=-1,occurrence=1,segment=len(practice["segments"]),start=at,sourceStart=starts[-1],sourceEnd=starts[-1]+padding));at+=padding
    check(len(visits)<=50000,"PLAN_LIMIT","/practices","Visit budget including padding")
    notes=[dict(note=n,event=e) for e in events for n in e["event"]["notes"] if n["id"] not in e["suppressed"]]
    targets={(n["note"]["id"],n["event"]["start"],n["event"]["sourceStart"],visits[n["event"]["visit"]]["segment"]):i for i,n in enumerate(notes)}
    source_notes={}
    for n in notes: source_notes.setdefault(n["note"]["id"],[]).append(n)
    incoming=set()
    for r in score["relations"]:
        if r["kind"] in ("slur","slide-pick"): continue
        for a in source_notes.get(r["fromNoteId"],[]):
            target=targets.get((r["toNoteId"],a["event"]["end"],a["event"]["sourceStart"]+q(a["event"]["event"]["duration"]),
                visits[a["event"]["visit"]]["segment"]))
            if target is not None: incoming.add(target)
    onsets=0;shared_attacks=set()
    for i,n in enumerate(notes):
        if i in incoming: continue
        if id:=n["note"].get("sharedSoundId"):
            key=(n["event"]["start"],id)
            if key in shared_attacks: continue
            shared_attacks.add(key)
        art={**n["event"]["event"].get("articulation",{}),**n["note"].get("articulation",{})}
        if r:=art.get("repluck"): onsets+=int((n["event"]["end"]-n["event"]["start"])/q(r["unit"]))
        else: onsets+=1+sum(g["kind"]=="pick" for g in art.get("ornament",{}).get("notes",[]))
    return dict(visits=visits,events=events,notes=notes,onsets=onsets,length=at)

def coverage(score,practices):
    events=[e for v in score["voices"] for e in v["events"]]
    notes=[n for e in events for n in e["notes"]]
    planned_notes=planned_visits=onsets=0
    routes=dict(performed=form_visits(score),written=[dict(measureIndex=i,occurrence=1,repeatContext={},afterJump=False)
        for i in range(len(score["measures"]))])
    for practice in practices:
        plan=compile_summary(score,practice,routes)
        planned_notes+=len(plan["notes"]);planned_visits+=len(plan["visits"]);onsets+=plan["onsets"]
        check(planned_notes<=1000000 and planned_visits<=50000,"PLAN_LIMIT","/practices","Aggregate planned note/visit budget")
    return dict(tracks=len(score["tracks"]),voices=len(score["voices"]),measures=len(score["measures"]),
        events=len(events),notes=len(notes),rests=sum(not e["notes"] for e in events),relations=len(score["relations"]),
        techniques=sum(bool(n.get("dead") or n.get("harmonic") or n.get("articulation") or e.get("articulation")) for e in events for n in e["notes"]),
        visits=planned_visits,onsets=onsets)

def validate_payloads(payloads,contract=CONTRACT,builtin=False,capabilities=None):
    check(set(payloads)==set(FILES),"ZIP_FILES","/container","Exactly five files required")
    roots={k:strict_json(v) for k,v in payloads.items()}
    schemas_validate(roots,contract)
    manifest=roots["manifest.json"];score=roots["score.json"];practices=roots["practices.json"]["practices"];review=roots["review.json"]
    check(builtin or not manifest["packageId"].startswith("builtin."),"NAMESPACE","/manifest/packageId","Reserved namespace")
    for name,meta in manifest["files"].items():
        check(meta["bytes"]==len(payloads[name]) and meta["sha256"]==digest(payloads[name]),"HASH","/manifest/files/"+name,"Bytes/hash mismatch")
    for name,expected in review["hashes"].items():
        check(expected==digest(payloads[name]),"REVIEW_HASH","/review/hashes/"+name,"Review binds different bytes")
    derived=validate_music(score,practices)
    check(set(manifest["requires"])==derived,"CAPABILITY_DECLARATION","/manifest/requires","Expected "+", ".join(sorted(derived)))
    if capabilities is not None:
        supported=set(capabilities["capabilities"])
        check(derived<=supported,"CAPABILITY","/manifest/requires","App upgrade required")
    ids={p["id"] for p in practices}
    check(review["method"].strip() and review["reviewer"].strip() and "TODO" not in review["method"],
        "REVIEW_NEEDED","/review/method","Independent review not completed")
    for i,d in enumerate(review["diagnostics"]):
        path="/review/diagnostics/"+str(i)
        check(set(d["practiceIds"])<=ids,"REVIEW_REF",path,"Unknown practice")
        check(d["category"]!="unsupported-blocker","REVIEW_BLOCKER",path,d["reason"])
        if d["category"]=="review-needed":
            check(review["status"]=="conditional" and review["assumptions"] and d["handling"]=="assumption" and
                d["featureId"] in ("source.repeat-count.v1","source.coverage.v1") and d["practiceIds"],
                "REVIEW_NEEDED",path,"Unresolved music fact")
    check(review["status"]!="conditional" or review["assumptions"],"CONDITIONAL","/review","Concrete assumptions required")
    prov=roots["provenance.json"];sources={s["id"] for s in prov["sources"]}
    check(len(sources)==len(prov["sources"]),"SOURCE_ID","/provenance/sources","Duplicate source ID")
    note_ids={n["id"] for v in score["voices"] for e in v["events"] for n in e["notes"]}
    check(all(m["noteId"] in note_ids and m["sourceId"] in sources for m in prov["mappings"]) and
        {m["noteId"] for m in prov["mappings"]}==note_ids,"SOURCE_MAPPING","/provenance/mappings","Every note requires a source locator")
    check(review["coverage"]==coverage(score,practices),"COVERAGE","/review/coverage","Coverage differs from recomputed source/plan counts")
    aliases={a["from"]:a["to"] for a in roots["practices.json"]["aliases"]}
    check(len(aliases)==len(roots["practices.json"]["aliases"]),"ALIAS","/practices/aliases","Duplicate alias")
    for start,target in aliases.items():
        check(start not in ids and target in ids|aliases.keys(),"ALIAS","/practices/aliases","Missing/shadowed alias")
        seen={start}
        while target in aliases:
            check(target not in seen,"ALIAS_CYCLE","/practices/aliases","Alias cycle");seen.add(target);target=aliases[target]
    return roots

def package_files(package_id,revision,title,author,score,practices,provenance,review):
    """Bind already-reviewed bytes; caller must supply a real review, never infer one from parsing."""
    payloads={"score.json":dumps(score),"practices.json":dumps(dict(practices=practices,aliases=[])),"provenance.json":dumps(provenance)}
    review=dict(review,hashes={k:digest(v) for k,v in payloads.items()})
    payloads["review.json"]=dumps(review)
    payloads["manifest.json"]=dumps(dict(format="guitar-practice-song",formatVersion=dict(major=1,minor=0),
        scoreSchemaVersion=dict(major=1,minor=0),packageId=package_id,revision=revision,
        display=dict(title=title,author=author,summary="经过审核的本地练习"),requires=sorted(validate_music(score,practices)),
        files={k:dict(bytes=len(v),sha256=digest(v)) for k,v in payloads.items()}))
    return payloads

def write_zip(payloads,path,overwrite=False):
    path=Path(path);check(overwrite or not path.exists(),"OUTPUT","/output","Output already exists")
    path.parent.mkdir(parents=True,exist_ok=True)
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,"w") as archive:
        for name in FILES:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            info.create_system=3;info.external_attr=0o100644<<16
            archive.writestr(info,payloads[name],compresslevel=9)
    data=buffer.getvalue()
    check(len(data)<=LIMIT_INPUT,"LIMIT","/container","Packed input too large")
    with path.open("wb" if overwrite else "xb") as stream: stream.write(data)
    return digest(data)
