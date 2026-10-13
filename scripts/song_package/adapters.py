"""Format readers. Selection is manifest data; no song name/hash dispatch or Kotlin emitter."""
from .protocol import *
import xml.etree.ElementTree as ET
import re

DYNAMICS={"PPP":.25,"PP":.35,"P":.5,"MP":.65,"MF":.8,"F":1.0,"FF":1.1,"FFF":1.2}
NOTE_VALUES={"Whole":1,"Half":2,"Quarter":4,"Eighth":8,"16th":16,"32nd":32,"64th":64}

def gpif(path,selection):
    try:
        with zipfile.ZipFile(path) as archive:
            check("Content/score.gpif" in archive.namelist(),"FORMAT","/source","Use GP7/8 ZIP GPIF; export older GP with Guitar Pro first")
            xml=archive.read("Content/score.gpif")
    except zipfile.BadZipFile as e:
        raise Blocked("FORMAT","/source","This reader supports GP7/8 ZIP GPIF. Convert GP3–6 in Guitar Pro, then audit original/conversion.") from e
    check(b"<!DOCTYPE" not in xml and b"<!ENTITY" not in xml,"FORMAT","/source","XML entities not accepted")
    root=ET.fromstring(xml)
    tables={name:{e.get("id"):e for e in root.find(name)} for name in ("Bars","Voices","Beats","Notes","Rhythms")}
    order=root.findtext("MasterTrack/Tracks").split();raw_tracks={t.get("id"):t for t in root.find("Tracks")}
    all_masters=list(root.find("MasterBars"))
    first,last=selection.get("measureRange",[1,len(all_masters)])
    check(1<=first<=last<=len(all_masters),"SOURCE_RANGE","/selection/measureRange","Invalid GP source range")
    masters=all_masters[first-1:last];chosen=[str(t) for t in selection.get("tracks",order)]
    check(set(chosen)<=raw_tracks.keys(),"SOURCE_REF","/selection/tracks","Missing GP track")
    def props(e):
        values={p.get("name"):p for p in e.findall("Properties/Property")}
        check(len(values)==len(e.findall("Properties/Property")),"SOURCE","/source","Duplicate property")
        return values
    def property_value(e,name,child,default=None):
        p=props(e).get(name)
        return p.findtext(child,default) if p is not None else default
    def fields(e,allowed,location):
        unknown={c.tag for c in e}-set(allowed)
        check(not unknown,"SOURCE_FIELD",location,"Unmapped music fields: "+", ".join(sorted(unknown)))
    measures=[];starts=[F(0)];repeats=[];stack=[];diagnostics=[];measure_uses={}
    for i,m in enumerate(masters):
        fields(m,["Key","Time","Bars","Repeat","AlternateEndings","Section","DoubleBar","XProperties","TripletFeel"],"GP MasterBar "+str(i))
        n,d=map(int,m.findtext("Time").split("/"));groups=selection.get("beatGroups",{}).get(str(i+1))
        if groups is None: groups=[3]*(n//3) if d==8 and n>3 and n%3==0 else [1]*n
        native=m.findtext("Bars").split()[0]
        measure_uses[native]=measure_uses.get(native,0)+1
        measures.append(dict(id="gp-measure-"+native+"-"+str(measure_uses[native]),label=str(first+i),meter=dict(numerator=n,denominator=d,beatGroups=groups),
            actualDuration=rational(F(4*n,d)),sourceRef="GP MasterBar "+str(first+i-1)))
        starts.append(starts[-1]+F(4*n,d))
        repeat=m.find("Repeat")
        if repeat is not None:
            if repeat.get("start")=="true": stack.append(i)
            if repeat.get("end")=="true":
                a=stack.pop() if stack else 0
                count=int(repeat.get("count","2"))
                repeats.append(dict(id="repeat-"+native,start=measures[a]["id"],end=measures[i]["id"],times=count))
        check(m.find("AlternateEndings") is None,"SOURCE_FIELD","GP MasterBar "+str(i),"Normalize volta from independently reviewed canonical data")
    check(not stack,"SOURCE_FORM","/source","Unclosed GP repeat")
    tracks=[];voices=[];tuplets=[];relations=[];mappings=[];pending=[];note_context={}
    for track_id in chosen:
        raw=raw_tracks[track_id];staff=raw.find("Staves/Staff")
        tuning=property_value(staff,"Tuning","Pitches")
        check(tuning is not None and len(tuning.split())==6,"SOURCE_TRACK","GP Track "+track_id,"Six-string tuning required")
        check(property_value(staff,"PartialCapoFret","Fret","0")=="0" and property_value(staff,"PartialCapoStringFlags","Bitset","000000")=="000000",
            "SOURCE_TRACK","GP Track "+track_id,"Partial capo unsupported")
        track=dict(id="track-"+track_id,name=raw.findtext("Name") or "吉他",openStringMidi=list(reversed(list(map(int,tuning.split())))),
            capo=int(property_value(staff,"CapoFret","Fret","0")),fretCount=int(property_value(staff,"FretCount","Number","24")))
        tracks.append(track)
        voice_numbers=selection.get("voices",{}).get(track_id,[0])
        for voice_index in voice_numbers:
            voice_id="voice-"+track_id+"-"+str(voice_index);events=[];group=None;slur_origin=None
            for mi,master in enumerate(masters):
                bar_ids=master.findtext("Bars").split()
                bar=tables["Bars"][bar_ids[order.index(track_id)]]
                fields(bar,["Clef","Ottavia","Voices","XProperties","SimileMark"],"GP Bar "+bar.get("id"))
                check(bar.find("SimileMark") is None,"SOURCE_FIELD","GP Bar "+bar.get("id"),"Simile requires reviewed expansion")
                voice_ref=bar.findtext("Voices").split()[voice_index]
                duration=starts[mi+1]-starts[mi]
                if voice_ref=="-1":
                    check(group is None,"SOURCE_TUPLET","GP Bar "+bar.get("id"),"Incomplete tuplet")
                    events.append(dict(id=voice_id+"-rest-"+str(mi),measureId=measures[mi]["id"],onset=rational(0),
                        duration=rational(duration),written=dict(baseDenominator=1),notes=[],measureRest=True,
                        sourceRef="GP Bar "+bar.get("id")+" Voice "+str(voice_index)+" explicit empty"))
                    continue
                fields(tables["Voices"][voice_ref],["Beats"],"GP Voice "+voice_ref)
                beats=tables["Voices"][voice_ref].findtext("Beats").split();at=F(0);beat_uses={}
                for bi,beat_id in enumerate(beats):
                    beat=tables["Beats"][beat_id];loc="GP track "+track_id+" bar "+str(first+mi)+" voice "+str(voice_index)+" beat "+str(bi)+" ref "+beat_id
                    fields(beat,["Dynamic","Rhythm","Notes","Arpeggio","GraceNotes","Properties","FreeText","Lyrics",
                        "TransposedPitchStemOrientation","ConcertPitchStemOrientation","UserTransposedPitchStemOrientation",
                        "UserConcertPitchStemOrientation","XProperties","Legato"],loc)
                    annotation=beat.findtext("FreeText","")
                    check(not annotation or re.fullmatch("[IVX]+",annotation) is not None,
                        "SOURCE_ANNOTATION",loc,"Non-position free text requires explicit canonical mapping/review")
                    check(beat.find("GraceNotes") is None and beat.find("Arpeggio") is None,"SOURCE_FIELD",loc,"Normalize GP grace/arpeggio from reviewed canonical data")
                    allowed_props={"Brush","PrimaryPickupVolume","PrimaryPickupTone"}
                    check(set(props(beat))<=allowed_props,"SOURCE_FIELD",loc,"Unknown beat property")
                    rhythm=tables["Rhythms"][beat.find("Rhythm").get("ref")]
                    fields(rhythm,["NoteValue","AugmentationDot","PrimaryTuplet"],loc+"/Rhythm")
                    base=NOTE_VALUES.get(rhythm.findtext("NoteValue"));check(base is not None,"SOURCE_RHYTHM",loc,"Unknown value")
                    dots=int(rhythm.find("AugmentationDot").get("count")) if rhythm.find("AugmentationDot") is not None else 0
                    written=dict(baseDenominator=base,dots=dots,tupletGroupIds=[])
                    actual=sum((F(4,base*2**i) for i in range(dots+1)),F(0))
                    tuplet=rhythm.find("PrimaryTuplet")
                    beat_uses[beat_id]=beat_uses.get(beat_id,0)+1
                    event_id=voice_id+"-bar-"+bar.get("id")+"-beat-"+beat_id+"-"+str(beat_uses[beat_id])
                    if tuplet is not None:
                        ratio=(int(tuplet.get("num")),int(tuplet.get("den")))
                        if group is None:
                            group=dict(id="tuplet-"+str(len(tuplets)),voiceId=voice_id,eventIds=[],actual=ratio[0],normal=ratio[1])
                            tuplets.append(group)
                        check((group["actual"],group["normal"])==ratio,"SOURCE_TUPLET",loc,"Unclosed unequal GP tuplet")
                        group["eventIds"].append(event_id);written["tupletGroupIds"]=[group["id"]];actual*=F(ratio[1],ratio[0])
                        if len(group["eventIds"])==group["actual"]: group=None
                    else: check(group is None,"SOURCE_TUPLET",loc,"Incomplete GP tuplet")
                    brush=property_value(beat,"Brush","Direction");event_art={}
                    if brush: event_art.update(pick=brush.lower(),strumDelayMs=12)
                    dynamic=beat.findtext("Dynamic","MF")
                    check(dynamic in DYNAMICS,"SOURCE_DYNAMIC",loc,"Unknown dynamic")
                    event_art["velocity"]=DYNAMICS[dynamic]
                    notes=[]
                    for ni,ref in enumerate((beat.findtext("Notes") or "").split()):
                        raw_note=tables["Notes"][ref];nloc=loc+" note "+str(ni)+" ref "+ref
                        fields(raw_note,["Properties","Tie","LetRing","Vibrato","Accent","InstrumentArticulation","XProperties"],nloc)
                        check(raw_note.findtext("InstrumentArticulation","0")=="0","SOURCE_ARTICULATION",nloc,"Unmapped instrument articulation")
                        p=props(raw_note)
                        known={"String","Fret","Midi","ConcertPitch","TransposedPitch","Muted","PalmMuted","HopoOrigin","HopoDestination",
                            "Bended","Slide","BendOriginOffset","BendOriginValue","BendMiddleOffset1","BendMiddleOffset2","BendMiddleValue","BendDestinationOffset","BendDestinationValue"}
                        check(set(p)<=known,"SOURCE_FIELD",nloc,"Unknown note property: "+", ".join(sorted(set(p)-known)))
                        string=6-int(property_value(raw_note,"String","String"));fret=int(property_value(raw_note,"Fret","Fret"))
                        midi=track["openStringMidi"][string-1]+track["capo"]+fret
                        stored=property_value(raw_note,"Midi","Number")
                        check(stored is None or int(stored)==midi,"SOURCE_PITCH",nloc,"GP sound MIDI disagrees with tuning/capo")
                        art={}
                        if "PalmMuted" in p: art["palmMute"]=True
                        if raw_note.find("LetRing") is not None: art["letRing"]=True
                        if raw_note.find("Vibrato") is not None:
                            check(raw_note.findtext("Vibrato")=="Slight","SOURCE_VIBRATO",nloc,"Unknown vibrato depth")
                            art["vibrato"]=dict(depth=.15,rateHz=5)
                        accent=raw_note.findtext("Accent")
                        if accent:
                            check(accent in ("1","4","8"),"SOURCE_ACCENT",nloc,"Unknown accent flags")
                            if accent=="1": art["short"]=True
                            else: art["accent"]=True
                        slide=int(property_value(raw_note,"Slide","Flags","0"))
                        check(slide in (0,1,2,4,8,16,32),"SOURCE_SLIDE",nloc,"Unknown/combined GP slide")
                        if slide in (4,8,16,32): art["slide"]={4:"out-down",8:"out-up",16:"in-up",32:"in-down"}[slide]
                        if "Bended" in p:
                            values=[("BendOriginOffset","BendOriginValue",0),("BendMiddleOffset1","BendMiddleValue",50),
                                ("BendMiddleOffset2","BendMiddleValue",50),("BendDestinationOffset","BendDestinationValue",100)]
                            curve=[]
                            for offset_name,value_name,default in values:
                                amount=property_value(raw_note,value_name,"Float")
                                position=property_value(raw_note,offset_name,"Float")
                                if amount is not None and position is not None:
                                    curve.append(dict(at=rational(actual*F(position)/100),semitones=float(amount)/50))
                            curve=sorted({q(p["at"]):p for p in curve}.values(),key=lambda p:q(p["at"]))
                            check(curve,"SOURCE_BEND",nloc,"Missing bend curve")
                            art["bend"]=curve
                        id=event_id+"-note-"+ref
                        n=dict(id=id,string=string,fret=fret,midi=midi,dead="Muted" in p,articulation=art,sourceRef=nloc)
                        notes.append(n);mappings.append(dict(noteId=id,sourceId="source",location=nloc))
                        note_context[id]=(voice_id,string,fret,starts[mi]+at,actual)
                        tie=raw_note.find("Tie")
                        if tie is not None and tie.get("destination")=="true": pending.append(("tie",id))
                        if "HopoDestination" in p: pending.append(("hopo",id))
                        if slide in (1,2): pending.append(("slide-pick" if slide==1 else "slide",id,"forward"))
                    events.append(dict(id=event_id,measureId=measures[mi]["id"],onset=rational(at),duration=rational(actual),
                        written=written,notes=notes,articulation=event_art,sourceRef=loc))
                    slur=beat.find("Legato")
                    if slur is not None:
                        check(set(slur.attrib)<=set(["origin","destination"]),"SOURCE_SLUR",loc,"Unknown legato attributes")
                        if slur.get("destination")=="true":
                            check(slur_origin is not None and len(notes)==1,"SOURCE_SLUR",loc,"Ambiguous beat slur; use explicit note relations")
                            relations.append(dict(id="relation-"+str(len(relations)),kind="slur",fromNoteId=slur_origin,toNoteId=notes[0]["id"]))
                            slur_origin=None
                        if slur.get("origin")=="true":
                            check(slur_origin is None and len(notes)==1,"SOURCE_SLUR",loc,"Ambiguous beat slur origin")
                            slur_origin=notes[0]["id"]
                    at+=actual
                check(at==duration,"SOURCE_COVERAGE",loc,"GP voice does not fill meter; use reviewed actualDuration instead of inventing rests")
            check(group is None,"SOURCE_TUPLET","/source","Unclosed GP tuplet")
            check(slur_origin is None,"SOURCE_SLUR","/source","Source window cuts slur; use reviewed canonical boundary policy")
            voices.append(dict(id=voice_id,trackId=track["id"],events=events))
    note_objects={n["id"]:n for v in voices for e in v["events"] for n in e["notes"]}
    for relation in pending:
        kind,id,*forward=relation;v,s,f,at,duration=note_context[id]
        candidates=[(nid,c) for nid,c in note_context.items() if c[0]==v and c[1]==s and
            (c[3]==at+duration if forward else c[3]+c[4]==at)]
        check(len(candidates)==1,"SOURCE_RELATION",id,"Missing/ambiguous adjacent target")
        other,context=candidates[0]
        a,b=(id,other) if forward else (other,id)
        if kind=="hopo": kind="hammer" if note_context[a][2]<note_context[b][2] else "pull"
        relations.append(dict(id="relation-"+str(len(relations)),kind=kind,fromNoteId=a,toNoteId=b))
        if kind!="slide-pick": note_objects[b]["articulation"]["pick"]="none"
    tempos=[]
    unit_map={2:F(1)}
    for automation in root.findall("MasterTrack/Automations/Automation"):
        check(automation.findtext("Type")=="Tempo","SOURCE_AUTOMATION","/source","Unknown master automation")
        source_bar=int(automation.findtext("Bar"));bar=source_bar-first+1
        if source_bar>=last: continue
        position=F(automation.findtext("Position","0"));values=automation.findtext("Value").split()
        check(int(values[1]) in unit_map and automation.findtext("Linear","false")=="false","SOURCE_TEMPO","/source","Review GP tempo unit/ramp explicitly")
        if bar<0:
            tempos=[dict(at=rational(0),beatUnit=rational(unit_map[int(values[1])]),bpm=float(values[0]))]
        else: tempos.append(dict(at=rational(starts[bar]+position*(starts[bar+1]-starts[bar])),beatUnit=rational(unit_map[int(values[1])]),bpm=float(values[0])))
    check(tempos and q(tempos[0]["at"])==0,"SOURCE_TEMPO","/source","Missing source tempo; supply reviewed canonical fact")
    swing=[]
    check(all(m.findtext("TripletFeel","NoTripletFeel")=="NoTripletFeel" for m in masters),"SOURCE_SWING","/source","Normalize scoped triplet feel using reviewed canonical facts")
    document=dict(documentId="gp-document",measures=measures,tracks=tracks,voices=voices,tuplets=tuplets,relations=relations,
        tempos=tempos,dynamics=[],swing=swing,form=dict(repeats=repeats),voicings=[],harmonies=[])
    provenance=dict(sources=[dict(id="source",name=Path(path).name,sha256=digest(Path(path).read_bytes()),format="gpif",
        version=root.findtext("GPVersion","GP7/8"),license=selection["license"],origin=selection["origin"])],
        tool="song-package-v1/gpif",mappings=mappings,exclusions=["GP RSE/音色/拾音器与排版元数据保留在原文件；App使用既有采样"]+
            ["excluded track "+id for id in order if id not in chosen]+
            ["explicit source measure range "+str(first)+"–"+str(last)+"; other written measures excluded"])
    return document,provenance

def reviewed_pdf(path,selection):
    data_path=Path(selection["reviewedData"])
    data=strict_json(data_path.read_bytes())
    check(digest(data_path.read_bytes())==selection["reviewedDataSha256"],"REVIEW_HASH","/selection/reviewedData","Reviewed transcript changed")
    source_hash=digest(Path(path).read_bytes())
    check(data.get("sha256",data.get("sourceSha256"))==source_hash,"SOURCE_HASH","/source","PDF differs from reviewed transcript")
    if "score" in data:
        score=data["score"];mappings=data["mappings"]
    else:
        # Compatibility input format only. New transcript schema uses the complete ScoreDocument.
        check(data.get("meter")=="4/4" and data.get("tickPerQuarter")==12,"PDF_SCHEMA","/source","Use canonical v1 transcript for new meters/voices")
        measures=[];events=[];mappings=[];tuplets=[];relations=[];previous={};locators={}
        first,last=selection.get("measureRange",[data["bars"][0]["number"],data["bars"][-1]["number"]])
        selected=[b for b in data["bars"] if first<=b["number"]<=last]
        check(selected and [b["number"] for b in selected]==list(range(first,last+1)),"PDF_COVERAGE","/source","Missing reviewed source bars")
        for bar in selected:
            id="measure-"+str(bar["number"]);measures.append(dict(id=id,label=str(bar["number"]),
                meter=dict(numerator=4,denominator=4,beatGroups=[1,1,1,1]),actualDuration=rational(4),sourceRef="PDF page "+str(bar["page"])))
            group=None
            for ei,e in enumerate(bar["events"]):
                event_id="pdf-event-"+str(bar["number"])+"-"+str(ei);actual=F(e["duration"],12)
                base_dots=next(((base,dots) for base in [1,2,4,8,16,32,64] for dots in range(4)
                    if sum(F(4,base*2**j) for j in range(dots+1))==actual*(F(3,2) if e.get("triplet") else 1)),None)
                check(base_dots is not None,"PDF_RHYTHM",event_id,"Unrepresentable reviewed value")
                written=dict(baseDenominator=base_dots[0],dots=base_dots[1],tupletGroupIds=[])
                if e.get("triplet"):
                    if group is None:
                        group=dict(id="tuplet-"+str(len(tuplets)),voiceId="pdf-voice",eventIds=[],actual=3,normal=2);tuplets.append(group)
                    group["eventIds"].append(event_id);written["tupletGroupIds"]=[group["id"]]
                    if len(group["eventIds"])==3: group=None
                else: check(group is None,"PDF_TUPLET",event_id,"Incomplete triplet")
                notes=[]
                pairs=e["notes"]+[[s,0] for s in e.get("dead",[])]
                for ni,(string,fret) in enumerate(pairs):
                    note_id=event_id+"-note-"+str(ni);art={}
                    if string in e.get("tied",[]): art["pick"]="none"
                    for key in ("palmMute","vibrato","short"):
                        if e.get(key): art[key]=dict(depth=.15,rateHz=5) if key=="vibrato" else True
                    for bend in e.get("bends",[])+([e["bend"]] if e.get("bend") else []):
                        if bend["string"]==string: art["bend"]=[dict(at=rational(F(str(p[0]))/12),semitones=p[1]) for p in bend["points"]]
                    if e.get("grace",{}).get("string")==string:
                        g=e["grace"]
                        art["ornament"]=dict(timing="on",notes=[dict(fret=g["fret"],duration=rational(F(1,8)),
                            kind=g["kind"].lower())])
                    if e.get("ringToBarEnd"):
                        target=int(e["ringToBarEnd"])
                        check(first<=target<=last,"PDF_RING",note_id,"Ring cutoff leaves selected reviewed source")
                        art.update(letRing=True,ringUntil=rational(F((target-first+1)*4)))
                    n=dict(id=note_id,string=string,fret=fret,midi=[64,59,55,50,45,40][string-1]+data.get("capo",0)+fret,
                        dead=string in e.get("dead",[]),articulation=art)
                    notes.append(n);mappings.append(dict(noteId=note_id,sourceId="source",location="PDF page "+str(bar["page"])+" bar "+str(bar["number"])+
                        " tick "+str(e["tick"])+" x "+str(e.get("pdfX","reviewed"))))
                    if string in e.get("tied",[]):
                        check(string in previous,"PDF_TIE",note_id,"Missing tie origin")
                        relations.append(dict(id="tie-"+str(len(relations)),kind="tie",fromNoteId=previous[string],toNoteId=note_id))
                    for link in e.get("legato",[]):
                        if link["string"]==string:
                            origin=locators.get((bar["number"],link["source"],string))
                            check(origin is not None,"PDF_LEGATO",note_id,"Explicit source locator missing")
                            relations.append(dict(id="legato-"+str(len(relations)),kind=link["kind"].lower(),fromNoteId=origin,toNoteId=note_id))
                            art["pick"]="none"
                    previous[string]=note_id
                    locators[(bar["number"],e["tick"],string)]=note_id
                event_art={}
                if e.get("velocity"): event_art["velocity"]=e["velocity"]
                if e.get("strum"): event_art.update(pick="down",strumDelayMs=12)
                events.append(dict(id=event_id,measureId=id,onset=rational(F(e["tick"],12)),duration=rational(actual),written=written,notes=notes,
                    articulation=event_art))
        score=dict(documentId="reviewed-pdf",measures=measures,tracks=[dict(id="pdf-guitar",name="PDF 吉他",
            openStringMidi=[64,59,55,50,45,40],capo=data.get("capo",0),fretCount=24)],
            voices=[dict(id="pdf-voice",trackId="pdf-guitar",events=events)],tuplets=tuplets,relations=relations,
            tempos=[dict(at=rational(0),beatUnit=rational(1),bpm=data["bpm"])],dynamics=[],swing=[],form={},voicings=[],harmonies=[])
    provenance=dict(sources=[dict(id="source",name=Path(path).name,sha256=source_hash,format="reviewed-pdf",
        license=selection["license"],origin=selection["origin"])],tool="song-package-v1/reviewed-pdf",mappings=mappings,
        exclusions=["PDF转录依据独立审核数据；不承诺任意PDF自动扒谱"])
    return score,provenance
