"""Original, hand-specified musical contract fixtures. Expected routes/times are separate constants."""
from .protocol import *
from copy import deepcopy

def meter(n,d=4,groups=None): return dict(numerator=n,denominator=d,beatGroups=groups or [1]*n)
def measure(id,n,d=4,groups=None,duration=None):
    result=dict(id=id,label=id,meter=meter(n,d,groups),actualDuration=rational(F(4*n,d) if duration is None else duration))
    if duration is not None: result["incompleteReason"]="原创不完整小节"
    return result
def track(id="guitar",tuning=None,capo=0):
    return dict(id=id,name=id,openStringMidi=tuning or [64,59,55,50,45,40],capo=capo,fretCount=24)
def note(id,string,fret,t=None,art=None,**extra):
    t=t or track()
    result=dict(id=id,string=string,fret=fret,midi=t["openStringMidi"][string-1]+t.get("capo",0)+fret,**extra)
    if art: result["articulation"]=art
    return result
def event(id,m,onset,duration,base,notes,dots=0,groups=None,**extra):
    return dict(id=id,measureId=m,onset=rational(onset),duration=rational(duration),
        written=dict(baseDenominator=base,dots=dots,tupletGroupIds=groups or []),notes=notes,**extra)
def score(measures,voices,tracks=None,tempos=None,**extra):
    return dict(documentId="original",measures=measures,tracks=tracks or [track()],voices=voices,tuplets=[],
        relations=[],tempos=tempos or [dict(at=rational(0),beatUnit=rational(1),bpm=60)],
        dynamics=[],swing=[],form={},voicings=[],harmonies=[],**extra)
def practice(s,id="practice",policy="written"):
    return dict(id=id,title="原创 "+id,goal="按完整乐句练习",mainTrackId=s["tracks"][0]["id"],
        mixTrackIds=[t["id"] for t in s["tracks"]],segments=[dict(start=dict(measureId=s["measures"][0]["id"],occurrence=1),
        end=dict(measureId=s["measures"][-1]["id"],offset=s["measures"][-1]["actualDuration"],
                 occurrence=2 if policy=="performed" and s["form"].get("repeats") else 1),formPolicy=policy)])

def fixtures():
    simple=score([measure("bar1",4)],[dict(id="upper",trackId="guitar",events=[
        event("simple-e"+str(i),"bar1",i,1,4,[note("simple-n"+str(i),1,i)]) for i in range(4)])])
    t=track(tuning=[64,59,55,50,45,38],capo=1)
    measures=[measure("triple",3),measure("compound",6,8,[3,3]),measure("irregular",7,8,[2,2,3]),measure("five",5)]
    upper=[];bass=[]
    for mi,m in enumerate(measures):
        duration=q(m["actualDuration"])
        if mi==0:
            upper += [event("quint-e"+str(i),m["id"],F(i*2,5),F(2,5),8,
                [note("quint-n"+str(i),1,i,t)],groups=["quint"]) for i in range(5)]
            upper += [event("quint-rest",m["id"],2,1,4,[])]
        elif mi==1:
            upper += [event("compound-e"+str(i),m["id"],F(i,2),F(1,2),8,[note("compound-n"+str(i),1,i%3,t)]) for i in range(6)]
        elif mi==2:
            upper += [event("irregular-e"+str(i),m["id"],F(i,2),F(1,2),8,[note("irregular-n"+str(i),1,i%4,t)]) for i in range(7)]
        else:
            upper += [event("five-e"+str(i),m["id"],i,1,4,[note("five-n"+str(i),1,i%4,t)]) for i in range(5)]
        bass += [event("bass-e"+str(mi),m["id"],0,duration,1,[note("bass-n"+str(mi),6,0,t)],measureRest=False)]
        # A dotted value or explicit whole-bar rest keeps the lower voice independent.
        base_dots={F(3):(2,1),F(7,2):(2,2),F(5):(1,0)}.get(duration,(1,0))
        bass[-1]["written"]=dict(baseDenominator=base_dots[0],dots=base_dots[1],tupletGroupIds=[])
        if duration==5:
            bass[-1]["duration"]=rational(4)
            bass += [event("bass-tail","five",4,1,4,[])]
    complex_score=score(measures,[dict(id="upper",trackId="guitar",events=upper),dict(id="lower",trackId="guitar",events=bass)],
        [t],[dict(at=rational(0),beatUnit=rational(1),bpm=60,rampEnd=rational(3),endBpm=120),
             dict(at=rational(3),beatUnit=rational(F(3,2)),bpm=60),dict(at=rational(6),beatUnit=rational(1),bpm=90)])
    complex_score["tuplets"]=[dict(id="quint",voiceId="upper",eventIds=["quint-e"+str(i) for i in range(5)],actual=5,normal=4)]
    complex_score["form"]=dict(repeats=[dict(id="repeat",start="triple",end="five",times=2)])
    nested_events=[event("nested-e"+str(i),"nested",F(i*2,35),F(2,35),32,
        [note("nested-n"+str(i),1,i%5)],groups=["outer","inner"]) for i in range(7)]
    nested_events += [event("outer-e"+str(i),"nested",F(2,5)+F(i*2,5),F(2,5),8,
        [note("outer-n"+str(i),1,i)],groups=["outer"]) for i in range(4)]
    nested_events += [event("nested-rest","nested",2,2,2,[])]
    nested=score([measure("nested",4)],[dict(id="upper",trackId="guitar",events=nested_events)])
    nested["tuplets"]=[dict(id="outer",voiceId="upper",eventIds=[e["id"] for e in nested_events[:-1]],actual=5,normal=4),
        dict(id="inner",voiceId="upper",eventIds=[e["id"] for e in nested_events[:7]],actual=7,normal=4,parentId="outer")]
    technique_events=[];tech_measures=[measure("tech"+str(i),4) for i in range(4)]
    for i in range(16):
        art={}
        if i==0: art=dict(bend=[dict(at=rational(0),semitones=0),dict(at=rational(1),semitones=2)],vibrato=dict(depth=.15,rateHz=5),
            tremoloBar=[dict(at=rational(0),semitones=0),dict(at=rational(1),semitones=-1)])
        if i in (1,2,3): art=dict(pick="none")
        if i==4: art=dict(palmMute=True,accent=True)
        if i==5: art=dict(tapping=True)
        if i==6: art=dict(ornament=dict(timing="on",notes=[dict(fret=0,duration=rational(F(1,8)),kind="hammer"),
            dict(fret=1,duration=rational(F(1,8)),kind="pick")]))
        if i==7: art=dict(ornament=dict(timing="before",notes=[dict(fret=0,duration=rational(F(1,8)),kind="slide")]))
        if i==8: art=dict(repluck=dict(kind="trill",unit=rational(F(1,4)),alternateFret=5))
        if i==9: art=dict(repluck=dict(kind="tremolo",unit=rational(F(1,4))))
        if i==10: art=dict(slide="in-up")
        if i==11: art=dict(slide="out-down",short=True)
        if i==12: art=dict(letRing=True)
        if i==13: art=dict(cut=True)
        n=note("tech-n"+str(i),2,[1,3,1,5][i%4],art=art)
        if i==14:
            n=note("tech-n14",1,0,harmonic=dict(kind="natural",partial=2,touchFret=12,soundMidi=76));n["midi"]=76
        if i==15: n=note("tech-n15",3,0,dead=True)
        technique_events.append(event("tech-e"+str(i),"tech"+str(i//4),i%4,1,4,[n]))
    techniques=score(tech_measures,[dict(id="upper",trackId="guitar",events=technique_events)])
    techniques["relations"]=[dict(id="legato"+str(i),kind=kind,fromNoteId="tech-n"+str(i),toNoteId="tech-n"+str(i+1)) for i,kind in enumerate(["hammer","pull","slide"])]
    techniques["dynamics"]=[dict(at=rational(0),velocity=.4,rampEnd=rational(4),endVelocity=1)]
    result={"simple":(simple,[practice(simple)]),"complex":(complex_score,[practice(complex_score,policy="performed")]),
        "nested":(nested,[practice(nested)]),"techniques":(techniques,[practice(techniques)])}
    tied=score([measure("tie-a",4),measure("tie-b",4)],[dict(id="upper",trackId="guitar",events=[
        event("tie-first","tie-a",0,4,1,[note("tie-origin",1,0,art=dict(bend=[dict(at=rational(0),semitones=0),
            dict(at=rational(4),semitones=2)],vibrato=dict(depth=.1,rateHz=5)))]),
        event("tie-second","tie-b",0,4,1,[note("tie-target",1,0,art=dict(pick="none"))])])])
    tied["relations"]=[dict(id="tie-relation",kind="tie",fromNoteId="tie-origin",toNoteId="tie-target")]
    result["ties"]=(tied,[practice(tied)])
    harmonic_notes=[
        dict(note("natural",1,0),midi=76,harmonic=dict(kind="natural",partial=2,touchFret=12,soundMidi=76)),
        dict(note("artificial",2,3),midi=81,harmonic=dict(kind="artificial",partial=3,touchFret=10,soundMidi=81)),
        dict(note("pinch",3,5),midi=84,harmonic=dict(kind="pinch",partial=4,touchFret=10,soundMidi=84))]
    harmonics=score([measure("harmonics",4)],[dict(id="upper",trackId="guitar",events=[
        event("harmonic-e"+str(i),"harmonics",i,1,4,[n]) for i,n in enumerate(harmonic_notes)]+[
        event("harmonic-rest","harmonics",3,1,4,[])])])
    result["harmonics"]=(harmonics,[practice(harmonics)])
    swung=score([measure("swing",4)],[dict(id="upper",trackId="guitar",events=[
        event("swing-e"+str(i),"swing",F(i,2),F(1,2),8,[note("swing-n"+str(i),1,i%3)])
        for i in range(8)])])
    swung["swing"]=[dict(start=rational(0),end=rational(4),unit=rational(F(1,2)),ratio=[2,1])]
    result["swing"]=(swung,[practice(swung)])
    strummed=score([measure("strum",4)],[dict(id="upper",trackId="guitar",events=[
        event("strum-e","strum",0,4,1,[note("strum-low",6,0),note("strum-high",1,0)],
            articulation=dict(pick="up",strumDelayMs=12,letRing=True))])])
    strummed["voicings"]=[dict(id="local-grip",name="包内双音按法",trackId="guitar",frets=[0,-1,-1,-1,-1,0],fingers=[0]*6)]
    strummed["harmonies"]=[dict(id="local-harmony",measureId="strum",onset=rational(0),name="原创双音",voicingId="local-grip")]
    result["strum"]=(strummed,[practice(strummed)])
    pickup=score([dict(measure("pickup",4,duration=F(1,4)),leadIn=rational(F(15,4))),
        measure("partial-tail",4,duration=F(15,4))],[dict(id="upper",trackId="guitar",events=[
            event("pickup-e","pickup",0,F(1,4),16,[note("pickup-n",1,0)]),
            event("tail-e","partial-tail",0,F(15,4),2,[note("tail-n",1,3)],dots=3)])])
    result["pickup"]=(pickup,[practice(pickup)])
    conditional=score([measure("condition-a",4),measure("condition-b",4)],[dict(id="upper",trackId="guitar",events=[
        event("condition-e"+str(i),"condition-"+("a" if i<4 else "b"),i%4,1,4,[note("condition-n"+str(i),1,i%3)])
        for i in range(8)])])
    conditional["form"]=dict(repeats=[dict(id="condition-repeat",start="condition-a",end="condition-b",times=2)])
    conditional["tempos"].append(dict(at=rational(0),beatUnit=rational(1),bpm=120,condition=dict(occurrence=2)))
    result["conditional"]=(conditional,[practice(conditional,policy="performed")])
    cross=score([measure("cross-a",3,duration=F(12,5)),measure("cross-b",2,duration=F(8,5))],
        [dict(id="upper",trackId="guitar",events=[
            event("cross-e"+str(i),"cross-a" if i<3 else "cross-b",F((i if i<3 else i-3)*4,5),F(4,5),4,
                [note("cross-n"+str(i),1,i)],groups=["cross-group"]) for i in range(5)])])
    cross["tuplets"]=[dict(id="cross-group",voiceId="upper",eventIds=["cross-e"+str(i) for i in range(5)],actual=5,normal=4)]
    result["cross-tuplet"]=(cross,[practice(cross)])
    multitrack=deepcopy(simple);other=deepcopy(multitrack["voices"][0]);other["id"]="second-voice";other["trackId"]="second-guitar"
    second=track("second-guitar",tuning=[64,59,55,50,45,38],capo=1)
    for e in other["events"]:
        e["id"]="second-"+e["id"]
        for n in e["notes"]:
            n["id"]="second-"+n["id"];n["midi"]+=1
    multitrack["tracks"].append(second);multitrack["voices"].append(other)
    result["multi-track"]=(multitrack,[practice(multitrack)])
    shared=deepcopy(simple);second_voice=deepcopy(shared["voices"][0]);second_voice["id"]="shared-lower"
    for first,other in zip(shared["voices"][0]["events"],second_voice["events"]):
        other["id"]="shared-"+other["id"]
        first["notes"][0]["sharedSoundId"]=other["notes"][0]["sharedSoundId"]="sound-"+first["id"]
        other["notes"][0]["id"]="shared-"+other["notes"][0]["id"]
        other["notes"][0]["articulation"]=dict(pick="none")
    shared["voices"].append(second_voice)
    result["shared"]=(shared,[practice(shared)])
    return result

def produce(directory):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=False)
    for name,(s,ps) in fixtures().items():
        source_hash=digest(dumps(s))
        prov=dict(sources=[dict(id="original-source",name=name+" 原创手工数据",sha256=source_hash,format="original",
            license="CC0",origin="项目原创契约样本")],tool="song-package-v1",exclusions=[],
            mappings=[dict(noteId=n["id"],sourceId="original-source",location=e["measureId"]+"/"+e["id"]+"/"+n["id"])
                for v in s["voices"] for e in v["events"] for n in e["notes"]])
        review=dict(status="approved",reviewer="original-fixture-author",method="手工指定节奏与曲序；测试使用独立解析期望",
            tool="song-package-v1",coverage=coverage(s,ps),diagnostics=[],assumptions=[])
        payloads=package_files("example."+name,1,"原创 "+name,"项目原创",s,ps,prov,review)
        validate_payloads(payloads)
        target=directory/name;target.mkdir()
        for filename,data in payloads.items(): (target/filename).write_bytes(data)
        write_zip(payloads,directory/(name+".gpractice"))
    (directory/"expectations.json").write_bytes(dumps(dict(
        simple=dict(visits=["bar1"],length=dict(n=4,d=1),seconds=4),
        complex=dict(visits=["triple","compound","irregular","five"]*2,length=dict(n=29,d=1),
            firstRampSeconds=3*math.log(2),compoundSeconds=2),
        nested=dict(length=dict(n=4,d=1),innerDurations=[dict(n=2,d=35)]*7),
        techniques=dict(length=dict(n=16,d=1)))))
