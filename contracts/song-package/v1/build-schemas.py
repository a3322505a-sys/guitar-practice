"""Regenerate the five closed JSON Schemas. This is contract tooling, not a song emitter."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
def obj(properties, required=()):
    return dict(type="object", properties=properties, required=list(required), additionalProperties=False)
def arr(item, maximum=200000, minimum=0):
    return dict(type="array", items=item, minItems=minimum, maxItems=maximum)
def ref(name): return {"$ref": "#/$defs/"+name}
def enum(*values): return dict(enum=list(values))
def integer(low, high): return dict(type="integer", minimum=low, maximum=high)
def number(low, high): return dict(type="number", minimum=low, maximum=high)
ID = dict(type="string", pattern="^[A-Za-z][A-Za-z0-9._-]{0,95}$")
TEXT = dict(type="string", maxLength=4096)
HASH = dict(type="string", pattern="^[0-9a-f]{64}$")
BOOL = dict(type="boolean")
defs = {}
defs["q"] = obj(dict(n=integer(-10**12,10**12), d=integer(1,10**12)), ["n","d"])
defs["version"] = obj(dict(major=enum(1), minor=enum(0)), ["major","minor"])
defs["meter"] = obj(dict(numerator=integer(1,64),denominator=enum(1,2,4,8,16,32,64),
                        beatGroups=arr(integer(1,64),64,1)), ["numerator","denominator","beatGroups"])
defs["curvePoint"] = obj(dict(at=ref("q"),semitones=number(-24,24)),["at","semitones"])
defs["oscillation"] = obj(dict(depth=number(0,4),rateHz=number(.1,20)),["depth","rateHz"])
defs["grace"] = obj(dict(fret=integer(0,36),duration=ref("q"),kind=enum("pick","hammer","pull","slide","tap")),["fret","duration","kind"])
defs["ornament"] = obj(dict(timing=enum("before","on"),notes=arr(ref("grace"),16,1)),["timing","notes"])
defs["repluck"] = obj(dict(kind=enum("trill","tremolo"),unit=ref("q"),alternateFret=integer(0,36)),["kind","unit"])
defs["articulation"] = obj(dict(
    velocity=number(.01,2),accent=BOOL,pick=enum("default","down","up","none"),
    palmMute=BOOL,short=BOOL,letRing=BOOL,ringUntil=ref("q"),cut=BOOL,
    strumDelayMs=number(0,100),bend=arr(ref("curvePoint"),64),
    vibrato=ref("oscillation"),tremoloBar=arr(ref("curvePoint"),64),
    slide=enum("none","in-up","in-down","out-up","out-down"),tapping=BOOL,
    ornament=ref("ornament"),repluck=ref("repluck")))
defs["harmonic"] = obj(dict(kind=enum("natural","artificial","pinch"),partial=integer(2,16),
    touchFret=integer(0,48),soundMidi=integer(0,127)),["kind","partial","touchFret","soundMidi"])
defs["note"] = obj(dict(id=ID,string=integer(1,6),fret=integer(0,36),midi=integer(0,127),
    dead=BOOL,harmonic=ref("harmonic"),articulation=ref("articulation"),sourceRef=TEXT,sharedSoundId=ID),
    ["id","string","fret","midi"])
defs["written"] = obj(dict(baseDenominator=enum(1,2,4,8,16,32,64),dots=integer(0,3),
    tupletGroupIds=arr(ID,4)),["baseDenominator"])
defs["event"] = obj(dict(id=ID,measureId=ID,onset=ref("q"),duration=ref("q"),written=ref("written"),
    notes=arr(ref("note"),6),articulation=ref("articulation"),measureRest=BOOL,practicePadding=TEXT,sourceRef=TEXT),
    ["id","measureId","onset","duration","written","notes"])
defs["measure"] = obj(dict(id=ID,label=TEXT,meter=ref("meter"),actualDuration=ref("q"),leadIn=ref("q"),
    incompleteReason=TEXT,sourceRef=TEXT),["id","label","meter","actualDuration"])
defs["track"] = obj(dict(id=ID,name=TEXT,openStringMidi=arr(integer(0,127),6,6),capo=integer(0,12),
    fretCount=integer(1,36),instrument=enum("guitar")),["id","name","openStringMidi"])
defs["voice"] = obj(dict(id=ID,trackId=ID,events=arr(ref("event"),200000,1)),["id","trackId","events"])
defs["tuplet"] = obj(dict(id=ID,voiceId=ID,eventIds=arr(ID,1024,1),actual=integer(2,32),
    normal=integer(1,32),parentId=ID),["id","voiceId","eventIds","actual","normal"])
defs["relation"] = obj(dict(id=ID,kind=enum("tie","slur","hammer","pull","slide","slide-pick","tap"),
    fromNoteId=ID,toNoteId=ID),["id","kind","fromNoteId","toNoteId"])
defs["condition"] = obj(dict(occurrence=integer(1,50000),repeatPasses=dict(type="object",
    patternProperties={"^[A-Za-z][A-Za-z0-9._-]{0,95}$":arr(integer(1,32),32,1)},additionalProperties=False,maxProperties=4)))
defs["tempo"] = obj(dict(at=ref("q"),beatUnit=ref("q"),bpm=number(1,600),condition=ref("condition"),
    rampEnd=ref("q"),endBpm=number(1,600)),["at","beatUnit","bpm"])
defs["dynamic"] = obj(dict(at=ref("q"),velocity=number(.01,2),trackId=ID,condition=ref("condition"),
    rampEnd=ref("q"),endVelocity=number(.01,2)),["at","velocity"])
defs["swing"] = obj(dict(start=ref("q"),end=ref("q"),unit=ref("q"),ratio=arr(integer(1,16),2,2),
    trackIds=arr(ID,8),voiceIds=arr(ID,32)),["start","end","unit","ratio"])
defs["repeat"] = obj(dict(id=ID,start=ID,end=ID,times=integer(2,32)),["id","start","end","times"])
defs["ending"] = obj(dict(repeatId=ID,start=ID,end=ID,passes=arr(integer(1,32),32,1)),
    ["repeatId","start","end","passes"])
defs["marker"] = obj(dict(id=ID,measureId=ID,kind=enum("segno","coda","fine")),["id","measureId","kind"])
defs["jump"] = obj(dict(id=ID,at=ID,target=ID,kind=enum("dc","ds","to-coda"),occurrence=integer(1,32),condition=ref("condition")),
    ["id","at","target","kind"])
defs["form"] = obj(dict(repeats=arr(ref("repeat"),1000),endings=arr(ref("ending"),1000),
    markers=arr(ref("marker"),1000),jumps=arr(ref("jump"),1000),repeatsAfterJump=BOOL))
tone_labels = dict(type="object",patternProperties={"^(?:[0-9]|1[01])$":TEXT},additionalProperties=False,maxProperties=12)
defs["voicingTeaching"] = obj(dict(root=integer(-1,11),third=integer(0,11),soundingName=TEXT,
    group=TEXT,source=TEXT,intervalRoles=tone_labels,toneNames=tone_labels),["root","soundingName","group","source"])
defs["voicing"] = obj(dict(id=ID,name=TEXT,trackId=ID,frets=arr(integer(-1,36),6,6),
    fingers=arr(integer(0,4),6,6),teaching=ref("voicingTeaching")),["id","name","trackId","frets","fingers"])
defs["harmony"] = obj(dict(id=ID,measureId=ID,onset=ref("q"),name=TEXT,voicingId=ID,condition=ref("condition")),
    ["id","measureId","onset","name"])
defs["coordinate"] = obj(dict(measureId=ID,offset=ref("q"),occurrence=integer(1,50000)),["measureId"])
defs["segment"] = obj(dict(start=ref("coordinate"),end=ref("coordinate"),formPolicy=enum("written","performed"),
    entry=enum("reject","carryIn","reattack","truncate"),exit=enum("reject","truncate"),
    affectedNoteIds=arr(ID)),["start","end"])
defs["practiceTeaching"] = obj(dict(category=TEXT,passage=TEXT,source=TEXT,technique=TEXT,
    use=TEXT,version=TEXT,tempoLabel=TEXT,explanation=TEXT))
defs["noteFingering"] = obj(dict(sourceNoteId=ID,finger={"anyOf":[integer(1,4),dict(type="null")]},
    source=dict(type="string",minLength=1,maxLength=4096),occurrence=integer(1,50000)),["sourceNoteId","source"])

defs["practice"] = obj(dict(id=ID,title=TEXT,goal=TEXT,mainTrackId=ID,mixTrackIds=arr(ID,8,1),
    segments=arr(ref("segment"),256,1),suggestedTempo=integer(30,180),familyId=ID,variantOf=ID,
    relatedIds=arr(ID,256),visibility=enum("core","variant","source","hidden"),order=integer(0,100000),
    tags=arr(TEXT,64),padding=ref("q"),paddingReason=TEXT,tempoMode=enum("source","fixed"),difficulty=TEXT,
    teaching=ref("practiceTeaching"),legacyIds=arr(ID,32),noteFingerings=arr(ref("noteFingering"))),
    ["id","title","goal","mainTrackId","mixTrackIds","segments"])
defs["source"] = obj(dict(id=ID,name=TEXT,sha256=HASH,format=TEXT,version=TEXT,license=TEXT,origin=TEXT),
    ["id","name","sha256","format","license","origin"])
defs["sourceMap"] = obj(dict(noteId=ID,sourceId=ID,location=TEXT),["noteId","sourceId","location"])
defs["diagnostic"] = obj(dict(category=enum("supported","approximation","out-of-scope","review-needed","unsupported-blocker"),
    pointer=TEXT,sourceRef=TEXT,featureId=TEXT,reason=TEXT,practiceIds=arr(ID),handling=TEXT),
    ["category","pointer","featureId","reason","practiceIds","handling"])
extensions = dict(type="object",patternProperties={"^[a-z][a-z0-9.-]{0,63}$":TEXT},additionalProperties=False,maxProperties=32)
payloads = {}
payloads["score"] = obj(dict(documentId=ID,measures=arr(ref("measure"),10000,1),tracks=arr(ref("track"),8,1),
    voices=arr(ref("voice"),32,1),tuplets=arr(ref("tuplet")),relations=arr(ref("relation")),
    tempos=arr(ref("tempo"),20000,1),dynamics=arr(ref("dynamic"),20000),swing=arr(ref("swing"),10000),
    form=ref("form"),voicings=arr(ref("voicing"),10000),harmonies=arr(ref("harmony"),50000),extensions=extensions),
    ["documentId","measures","tracks","voices","tuplets","relations","tempos","dynamics","swing","form","voicings","harmonies"])
payloads["practices"] = obj(dict(practices=arr(ref("practice"),1000,1),aliases=arr(obj({"from":ID,"to":ID},["from","to"]),1000),
    extensions=extensions),["practices","aliases"])
payloads["provenance"] = obj(dict(sources=arr(ref("source"),256,1),tool=TEXT,mappings=arr(ref("sourceMap")),
    exclusions=arr(TEXT,1000),extensions=extensions),["sources","tool","mappings","exclusions"])
coverage_keys = ["tracks","voices","measures","events","notes","rests","relations","techniques","visits","onsets"]
payloads["review"] = obj(dict(status=enum("approved","conditional"),reviewer=TEXT,method=TEXT,tool=TEXT,
    hashes=obj({k+".json":HASH for k in ["score","practices","provenance"]},[k+".json" for k in ["score","practices","provenance"]]),
    coverage=obj({k:integer(0,1000000) for k in coverage_keys},coverage_keys),
    diagnostics=arr(ref("diagnostic"),10000),assumptions=arr(TEXT,1000),extensions=extensions),
    ["status","reviewer","method","tool","hashes","coverage","diagnostics","assumptions"])
payloads["manifest"] = obj(dict(format=enum("guitar-practice-song"),formatVersion=ref("version"),
    scoreSchemaVersion=ref("version"),packageId=dict(type="string",pattern="^[a-z][a-z0-9]*(\\.[a-z0-9][a-z0-9-]*)+$",maxLength=96),
    revision=integer(1,2147483647),display=obj(dict(title=TEXT,author=TEXT,summary=TEXT),["title","author","summary"]),
    requires=arr(dict(type="string",pattern="^[a-z][a-zA-Z0-9.-]*\\.v1$",maxLength=96),256),
    files=obj({k+".json":obj(dict(bytes=integer(1,33554432),sha256=HASH),["bytes","sha256"])
        for k in ["score","practices","provenance","review"]},[k+".json" for k in ["score","practices","provenance","review"]]),
    extensions=extensions),["format","formatVersion","scoreSchemaVersion","packageId","revision","display","requires","files"])
for name, schema in payloads.items():
    schema.update({"$schema":"https://json-schema.org/draft/2020-12/schema",
                   "$id":"https://guitar-practice.invalid/contracts/v1/"+name+".schema.json","$defs":defs})
    (ROOT/(name+".schema.json")).write_text(json.dumps(schema,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    assets = ROOT.parents[2]/"app"/"src"/"main"/"assets"/"song-contract"
    assets.mkdir(parents=True,exist_ok=True)
    (assets/(name+".schema.json")).write_text(json.dumps(schema,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
