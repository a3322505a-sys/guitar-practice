"""Conservative GPIF coverage gate, scoped to selected guitar voices.

Recognized music has controlled fields/enums. Non-rendered GP sound settings and
annotations require an exact, reviewed metadata fingerprint; they are reported
as approximations/review-needed, never silently called layout.
"""
import hashlib,json
from pathlib import Path
from .common import ImportBlocked,gp_graph,rhythm_ticks

APPROVALS=Path(__file__).with_name('gp-reviewed-metadata.json')
LAYOUT={
    'Bar':{'Clef','XProperties'},
    'Beat':{'TransposedPitchStemOrientation','ConcertPitchStemOrientation','XProperties'},
    'MasterBar':{'DoubleBar','XProperties'},
    'Track':{'ShortName','Color','SystemsDefautLayout','SystemsLayout','IconId','Lyrics'},
}
MUSIC={
    'MasterBar':{'Time','TripletFeel','Bars','Section'},'Bar':{'Voices'},'Voice':{'Beats'},
    'Beat':{'Dynamic','Rhythm','Notes','Arpeggio','GraceNotes'},
    'Note':{'Tie','LetRing','Vibrato','Accent','InstrumentArticulation'},
    'Rhythm':{'NoteValue','AugmentationDot','PrimaryTuplet'},
    'Track':{'Name','Staves','Automations','Transpose'},
}
PROPS={
    'Note':{'String':{'String'},'Fret':{'Fret'},'Midi':{'Number'},
            'ConcertPitch':{'Pitch'},'TransposedPitch':{'Pitch'},
            **{k:{'Enable'} for k in ('Muted','PalmMuted','HopoOrigin','HopoDestination','Bended')},
            'Slide':{'Flags'},**{k:{'Float'} for k in ('BendOriginOffset','BendOriginValue','BendMiddleOffset1','BendMiddleOffset2','BendMiddleValue','BendDestinationOffset','BendDestinationValue')}},
    'Beat':{'Brush':{'Direction'}},
    'Staff':{'CapoFret':{'Fret'},'PartialCapoFret':{'Fret'},'PartialCapoStringFlags':{'Bitset'},
             'FretCount':{'Number'},'Tuning':{'Pitches','Flat','Instrument','Label','LabelVisible'}},
}
ENUMS={'Dynamic':{'PPP','PP','P','MP','MF','F','FF','FFF'},'Time':{'4/4','2/4'},
       'TripletFeel':{'NoTripletFeel','Triplet8th','Triplet16th'},'GraceNotes':{'OnBeat'},
       'Vibrato':{'Slight'},'Accent':{'1','4','8'},'InstrumentArticulation':{'0'}}

def metadata_hash(element):
    # Strip serialization whitespace without losing nested tags, attributes or values.
    def value(e): return [e.tag,sorted(e.attrib.items()),(e.text or '').strip(),[value(c) for c in e]]
    return hashlib.sha256(json.dumps(value(element),ensure_ascii=False).encode()).hexdigest()

def audit_gp(path,profile,track_ids):
    root,tables,masters,tracks,order=gp_graph(path)
    if len(order)!=len(set(order)) or len(track_ids)!=len(set(track_ids)):
        raise ImportBlocked('GPIF','track order',order,'duplicate track reference')
    for name in ('Tracks','Bars','Voices','Beats','Notes','Rhythms'):
        ids=[e.get('id') for e in root.find(name)]
        if len(ids)!=len(set(ids)) or None in ids:
            raise ImportBlocked('GPIF',name,ids,'missing or duplicate object ID')
    approvals=json.loads(APPROVALS.read_text(encoding='utf8'))[profile]
    diagnostics=[];seen=set()
    def diagnostic(category,loc,field,value,reason):
        key=(category,loc,field,str(value))
        if key not in seen:
            diagnostics.append(dict(category=category,location=loc,field=field,value=str(value),reason=reason));seen.add(key)
    def block(loc,field,value,reason): raise ImportBlocked(loc,field,value,reason)
    def approved(e,loc,kind):
        key=kind+'/'+e.tag;digest=metadata_hash(e)
        if digest not in approvals.get(key,[]): block(loc,key,digest,'unparsed musical/metadata field or value requires review')
        category='approximation' if kind=='Track' or e.tag in ('SyncPoint','Sound') else 'review-needed'
        diagnostic(category,loc,key,digest,'reviewed v72 annotation/GP sound setting retained as source evidence; app uses existing samples/events')
    def check(e,kind,loc):
        if set(e.attrib)-{'id'}: block(loc,kind+' attributes',e.attrib,'unknown object attributes')
        for c in e:
            if c.tag=='Properties':
                if c.attrib: block(loc,'Properties',c.attrib,'unknown property container attributes')
                names=[p.get('name') for p in c if p.tag=='Property']
                if len(names)!=len(set(names)): block(loc,'Properties',names,'duplicate musical property')
                for p in c:
                    name=p.get('name'); fields=PROPS.get(kind,{})
                    if name not in fields:
                        approved(p,loc,kind+':Property:'+str(name));continue
                    if set(p.attrib)!={'name'} or any(x.tag not in fields[name] for x in p): block(loc,'Property:'+str(name),'nested fields','unknown property structure')
                    if len({x.tag for x in p})!=len(p): block(loc,'Property:'+name,'duplicate child','ambiguous value')
                    for x in p:
                        if x.tag=='Pitch':
                            if x.attrib or any(y.tag not in {'Step','Accidental','Octave'} or y.attrib or len(y) for y in x): block(loc,name,ET_text(x),'unknown pitch field')
                            if x.findtext('Step') not in set('ABCDEFG') or x.findtext('Accidental') not in ('','#','b','##','bb'): block(loc,name,ET_text(x),'unknown pitch spelling')
                        elif len(x) or x.attrib: block(loc,name,ET_text(x),'unexpected nested value')
                    if name=='Slide' and p.findtext('Flags') not in ({'2'} if profile=='shayou-v72' else {'2','4','8','16'}): block(loc,'Slide',p.findtext('Flags'),'unsupported slide flags')
                    if 'Enable' in fields[name] and p.findtext('Enable') not in ('',None): block(loc,name,ET_text(p),'unknown boolean property encoding')
                    if name=='Brush' and p.findtext('Direction') not in ('Down','Up'): block(loc,'Brush',ET_text(p),'unknown direction')
                    if name=='Tuning' and p.findtext('Pitches')!='40 45 50 55 59 64': block(loc,'Tuning',p.findtext('Pitches'),'nonstandard tuning')
                    if name=='PartialCapoFret' and p.findtext('Fret')!='0' or name=='PartialCapoStringFlags' and p.findtext('Bitset')!='000000': block(loc,name,ET_text(p),'partial capo unsupported')
                    diagnostic('supported',loc,'Property:'+name,ET_text(p),'parsed or validated source fact')
            elif c.tag in LAYOUT.get(kind,set()):
                # Layout whitelist is schema controlled, including nested values.
                approved_key=kind+'/'+c.tag
                if metadata_hash(c) not in approvals.get(approved_key,[]): block(loc,approved_key,ET_text(c),'layout shape/value outside reviewed schema')
                diagnostic('ignored-layout',loc,c.tag,ET_text(c),'explicit v72 layout whitelist')
            elif c.tag not in MUSIC.get(kind,set()): approved(c,loc,kind)
            else:
                if c.tag in ENUMS and ET_text(c) not in ENUMS[c.tag]: block(loc,c.tag,ET_text(c),'unknown enum')
                if kind=='Beat' and profile=='shayou-v72' and c.tag=='Dynamic' and ET_text(c) not in ('MF','F'): block(loc,'Dynamic',ET_text(c),'outside adapter dynamics')
                if kind=='MasterBar' and profile=='shayou-v72' and c.tag=='TripletFeel' and ET_text(c)!='NoTripletFeel': block(loc,c.tag,ET_text(c),'this adapter has no swing')
                attrs={'Rhythm':{'ref'},'Tie':{'origin','destination'},'AugmentationDot':{'count'},'PrimaryTuplet':{'num','den'},'Arpeggio':{'direction'}}.get(c.tag,set())
                if set(c.attrib)-attrs: block(loc,c.tag,c.attrib,'unknown attributes')
                if c.tag=='Section' and (any(x.tag not in ('Letter','Text') for x in c) or any(x.attrib or len(x) for x in c)):
                    block(loc,'Section',ET_text(c),'unknown form/section fields')
                if len(c) and not (kind=='Track' and c.tag in ('Staves','Automations','Transpose') or kind=='MasterBar' and c.tag=='Section'):
                    block(loc,c.tag,ET_text(c),'unknown nested musical fields')
                if c.tag in ('Tie','LetRing') and len(c): block(loc,c.tag,ET_text(c),'unexpected sustain children')
                if c.tag=='Tie' and any(v not in ('true','false') for v in c.attrib.values()): block(loc,'Tie',c.attrib,'unknown tie flags')
                if c.tag=='Arpeggio' and c.attrib.get('direction') not in (None,'Down'): block(loc,'Arpeggio',c.attrib,'unhandled arpeggio direction')
                if kind=='Rhythm':
                    if len(c): block(loc,c.tag,ET_text(c),'unexpected rhythm children')
                    ticks,triplet=rhythm_ticks(e)
                    if triplet and (e.find('PrimaryTuplet').get('num'),e.find('PrimaryTuplet').get('den'))!=('3','2'): block(loc,'PrimaryTuplet',ET_text(e),'only triplets supported')
                diagnostic('supported',loc,c.tag,ET_text(c),'handled source music')
    root_known={'GPVersion','GPRevision','Encoding','EncodingDescription','Score','MasterTrack','Tracks','MasterBars','Bars','Voices','Beats','Notes','Rhythms'}
    for c in root:
        if c.tag not in root_known: approved(c,'GPIF','Root')
        elif c.tag=='Score': approved(c,'GPIF','Root')
    mastertrack=root.find('MasterTrack')
    for c in mastertrack:
        if c.tag not in ('Tracks','Anacrusis','Automations'): approved(c,'MasterTrack','Track')
    def automation(a,loc,master):
        if any(c.tag not in {'Type','Linear','Bar','Position','Visible','Value'} for c in a): block(loc,'Automation',ET_text(a),'unknown fields')
        typ=a.findtext('Type')
        if master and typ=='Tempo':
            if a.findtext('Linear')!='false' or float(a.findtext('Position'))!=0: block(loc,'Tempo',ET_text(a),'ramp or intra-bar tempo unsupported')
            if profile=='shayou-v72' and (a.findtext('Bar')!='0' or a.findtext('Value')!='124 2'):
                block(loc,'Tempo',ET_text(a),'this protected adapter assumes 124 BPM without tempo changes')
            if profile=='kafka-v72':
                bar=int(a.findtext('Bar'));sections=[i for i,m in enumerate(masters) if m.find('Section') is not None]+[len(masters)]
                if any(first<bar<stop for first,stop in zip(sections,sections[1:])):
                    block(loc,'Tempo',ET_text(a),'intra-segment tempo unsupported')
            diagnostic('supported',loc,'Tempo',a.findtext('Value'),'constant tempo at source bar boundary')
        else: approved(a,loc,'Track:Automation:'+str(typ))
    for a in mastertrack.findall('Automations/Automation'): automation(a,'master bar '+str(a.findtext('Bar')),True)
    for tid in track_ids:
        track=tracks.get(str(tid))
        if track is None: block('Track',tid,'missing','explicit guitar track required')
        loc='track '+str(tid);check(track,'Track',loc)
        staffs=track.findall('Staves/Staff')
        if len(staffs)!=1: block(loc,'Staves',len(staffs),'exactly one staff required')
        staff=staffs[0]
        if any(c.tag!='Properties' for c in staff): block(loc,'Staff',ET_text(staff),'unknown staff object')
        check(staff,'Staff',loc)
        transpose=track.find('Transpose')
        if transpose is not None and any(c.tag not in ('Chromatic','Octave') for c in transpose): block(loc,'Transpose',ET_text(transpose),'unknown transpose')
        for a in track.findall('Automations/Automation'): automation(a,loc+' bar '+str(a.findtext('Bar')),False)
        for index,m in enumerate(masters):
            where=loc+' master bar '+str(index)+' (GP index)';check(m,'MasterBar',where)
            bar=tables['Bars'][m.findtext('Bars').split()[order.index(str(tid))]];check(bar,'Bar',where)
            voices=[v for v in bar.findtext('Voices').split() if v!='-1']
            if len(voices)>1: block(where,'Voices',voices,'multiple active voices unsupported')
            for vid in voices:
                voice=tables['Voices'][vid];check(voice,'Voice',where+' voice#'+vid)
                for bid in voice.findtext('Beats').split():
                    beat=tables['Beats'][bid];wherebeat=where+' beat#'+bid;check(beat,'Beat',wherebeat)
                    check(tables['Rhythms'][beat.find('Rhythm').get('ref')],'Rhythm',wherebeat)
                    for nid in (beat.findtext('Notes') or '').split(): check(tables['Notes'][nid],'Note',wherebeat+' note#'+nid)
    return diagnostics

def ET_text(e):
    return ''.join(e.itertext()).strip()
