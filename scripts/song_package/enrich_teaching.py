"""Offline teaching authoring. Results are explicit package data, never runtime guesses."""
import copy
import itertools
from fractions import Fraction as F
from .protocol import q, rational as rat, compile_summary, digest
from .teaching_data import CHORDS, BARRES, OPEN, NAMES, SUBSTITUTIONS
from .teaching import QUALITIES

def starts(score):
    result={};at=F(0)
    for m in score['measures']:result[m['id']]=at;at+=q(m['actualDuration'])
    return result,at

def explicit_barres(v):
    result=[]
    for finger in (1,2,3,4):
        for fret in sorted(set(v['frets'][i] for i,f in enumerate(v['fingers']) if f==finger and v['frets'][i]>0)):
            strings=[i+1 for i,f in enumerate(v['fingers']) if f==finger and v['frets'][i]==fret]
            if len(strings)>1 and all(v['frets'][s-1]>=fret for s in range(min(strings),max(strings)+1)):
                result.append(dict(finger=finger,fret=fret,fromString=max(strings),toString=min(strings)))
    return result

def finger_event(event,base):
    frets=sorted(set(n['fret'] for n in event['notes'] if n['fret']>0))
    if not frets:return {},base
    if frets[0]<base:base=frets[0]
    if frets[-1]>base+3:base=max(1,frets[-1]-3)
    if frets[0]<base:base=frets[0]
    # A wide simultaneous grip uses its authored low/high anchors; single lines shift.
    mapping={f:min(4,max(1,f-base+1)) for f in frets}
    if len(frets)>1 and frets[-1]-base>3:
        mapping={f:(1 if i==0 else 4 if i==len(frets)-1 else min(3,i+1)) for i,f in enumerate(frets)}
    return {n['id']:mapping[n['fret']] for n in event['notes'] if n['fret']>0},base

def complete(score,practices,package_id):
    at,total=starts(score)
    tracks={t['id']:t for t in score['tracks']}
    # Keep each beginner grip beside its primary grip, including capo-specific cards.
    for v in list(score['voicings']):
        shape=v['name'].split(' · ')[-1];track=tracks[v['trackId']]
        pair=next(((a,b) for a,b in SUBSTITUTIONS.items() if shape in (a,b)),None)
        if not pair or track['openStringMidi']!=OPEN:continue
        for name in pair:
            if any(x['trackId']==v['trackId'] and x['name'].split(' · ')[-1]==name for x in score['voicings']):continue
            quality,root,frets,fingers=CHORDS[name];root=(root+track.get('capo',0))%12
            sound=NAMES[root]+{'MAJOR':'','MINOR':'m','MAJOR_SEVENTH':'maj7','MINOR_SEVENTH_NO_FIVE':'m7(no5)','SUS_TWO':'sus2'}[quality]
            score['voicings'].append(dict(id='slot-'+v['trackId']+'-'+name.replace('(','-').replace(')',''),name=name,trackId=v['trackId'],
                frets=list(reversed(frets)),fingers=list(reversed(fingers)),barres=[],quality=quality,
                teaching=dict(root=root,soundingName=sound,group='原配与新手替代',source='教材同入口配法；名称与实音独立显示')))
    for v in score['voicings']:
        if any(f>0 and v['fingers'][i] not in (1,2,3,4) for i,f in enumerate(v['frets'])):
            frets=sorted(set(f for f in v['frets'] if f>0))
            v['fingers']=[min(4,frets.index(f)+1) if f>0 else 0 for f in v['frets']]
        v['barres']=explicit_barres(v)
        if not v.get('teaching'):
            pitches={(tracks[v['trackId']]['openStringMidi'][i]+tracks[v['trackId']].get('capo',0)+f)%12 for i,f in enumerate(v['frets']) if f>=0}
            shape=v['name'].split(' · ')[-1]
            root=(CHORDS[shape][1]+tracks[v['trackId']].get('capo',0))%12 if shape in CHORDS else next((pc for pc in pitches if {(pc+x)%12 for x in (0,4,7)}<=pitches),min(pitches))
            v['teaching']=dict(root=root,soundingName=v['name'],group='教材配法',source='逐弦实音与配指编写',toneNames={str(pc):NAMES[pc] for pc in pitches})
        root=v['teaching']['root']
        intervals={(tracks[v['trackId']]['openStringMidi'][i]+tracks[v['trackId']].get('capo',0)+f-root)%12 for i,f in enumerate(v['frets']) if f>=0}
        quality=next((name for name,values in QUALITIES.items() if set(values)==intervals),None)
        if quality:v['quality']=quality
    # Bind literal strummed grips in older scores to their authored chord-card data.
    for voice in score['voices']:
        candidates=[v for v in score['voicings'] if v['trackId']==voice['trackId']]
        prior=None;authored_interval=None
        for e in voice['events']:
            positions={(n['string'],n['fret']) for n in e['notes']}
            pitched=all(not n.get('dead') and not n.get('harmonic') for n in e['notes'])
            grip=next((v for v in candidates if pitched and len(positions)>1 and positions=={(i+1,f) for i,f in enumerate(v['frets']) if f>=0}),None)
            if grip:
                source_at=at[e['measureId']]+q(e['onset'])
                if grip['id']==prior and authored_interval is not None:
                    authored_interval['validUntil']=rat(source_at+q(e['duration']))
                elif grip['id']!=prior:
                    existing=next((h for h in score['harmonies'] if h['measureId']==e['measureId'] and q(h['onset'])==q(e['onset']) and next((v for v in candidates if v['id']==h.get('voicingId')),None)),None)
                    if existing is None:
                        existing=dict(id='teach-h-'+e['id'],measureId=e['measureId'],onset=e['onset'],name=grip['name'],voicingId=grip['id'],validUntil=rat(source_at+q(e['duration'])))
                        score['harmonies'].append(existing)
                    authored_interval=existing if existing['id'].startswith('teach-h-') else None
            else:authored_interval=None
            prior=grip['id'] if grip else None
    # Bind every actual chord interval, including the final symbol in a source passage.
    by_track={}
    voices={v['id']:v for v in score['voicings']}
    for h in score['harmonies']:
        by_track.setdefault(voices[h['voicingId']]['trackId'] if h.get('voicingId') in voices else '',[]).append(h)
    for hs in by_track.values():
        hs.sort(key=lambda h:at[h['measureId']]+q(h['onset']))
        for i,h in enumerate(hs):
            end=at[hs[i+1]['measureId']]+q(hs[i+1]['onset']) if i+1<len(hs) else total
            h['validUntil']=rat(min(q(h.get('validUntil',rat(end))),end))
    assignments={};position_bases={}
    for voice in score['voices']:
        base=1
        for e in voice['events']:
            values,base=finger_event(e,base);assignments.update(values);position_bases[e['id']]=base
            positions={(n['string'],n['fret']) for n in e['notes']}
            grip=next((v for v in score['voicings'] if v['trackId']==voice['trackId'] and len(positions)>1 and
                positions=={(i+1,f) for i,f in enumerate(v['frets']) if f>=0}),None)
            if grip:
                assignments.update({n['id']:grip['fingers'][n['string']-1] for n in e['notes'] if n['fret']>0})
    keys={'local.mozart':('C','MAJOR'),'local.usotsuki':('D','MAJOR'),'local.shayou':('D','NATURAL_MINOR'),'local.yourtrip':('E','MAJOR'),
        'personal.hana-bourei-lead':('E','MAJOR')}
    report=[]
    for p in practices:
        lesson=p.setdefault('lesson',dict(module='APPLICATION'))
        tonic,scale=keys.get(package_id,('C','MAJOR'))
        lesson.setdefault('key',dict(tonic=tonic,scale=scale))
        key=lesson['key'];name=key['tonic']+('m' if key['scale'] in ('NATURAL_MINOR','MINOR_PENTATONIC') else '')
        lesson['referenceChordName']=name if name in CHORDS else 'C'
        existing={(f['sourceNoteId'],f.get('occurrence')):f for f in p.get('noteFingerings',[]) if f.get('finger') in (1,2,3,4)}
        p.pop('noteFingerings',None)
        plan=compile_summary(score,p)
        eligible={n['note']['id']:n['note'] for n in plan['notes'] if n['note']['fret']>0}
        p['noteFingerings']=[existing.get((id,None),dict(sourceNoteId=id,finger=assignments[id],source='V3.1 教材配指：按弦位置与换把逐事件编写')) for id in sorted(eligible)]
        lesson['chainStarts']=list(dict.fromkeys(score['measures'][v['measureIndex']]['id'] for v in plan['visits'] if v['measureIndex']>=0))
        teaching=p.setdefault('teaching',{})
        teaching.setdefault('explanation',p.get('goal') or p['title'])
        teaching.setdefault('technique','按谱面指法弹奏；换把时释放左手压力。')
        if not lesson.get('actions'):
            first=next((n for n in plan['notes'] if n['note']['fret']>0),None)
            if first:lesson['actions']=[dict(eventId=first['event']['event']['id'],kind='position',text='食指基准位 '+str(position_bases[first['event']['event']['id']])+' 品；按标号落指，换把时放松。')]
        report.append(dict(id=p['id'],frettedNotes=len(eligible),explicitFingers=len(p['noteFingerings']),barBoundaries=len(lesson['chainStarts'])))
    return report

def beginner_voicing(original,track):
    shape=original['name'].split(' · ')[-1]
    actual=original['teaching']['soundingName'].split('/')[0]
    target=shape if shape in SUBSTITUTIONS else actual if actual in SUBSTITUTIONS else None
    if target is None or original.get('quality')!=('MINOR' if target=='Bm' else 'MAJOR'):return None
    alternative=SUBSTITUTIONS[target];quality=CHORDS[alternative][0];root=original['teaching']['root']
    capo=track.get('capo',0);opens=track['openStringMidi']
    if shape in SUBSTITUTIONS and opens==OPEN:
        frets=list(reversed(CHORDS[alternative][2]));fingers=list(reversed(CHORDS[alternative][3]))
    else:
        tones={(root+i)%12 for i in QUALITIES[quality]};best=None
        for strings in itertools.combinations(range(6),len(tones)):
            options=[[(f,(opens[s]+capo+f)%12) for f in range(10) if (opens[s]+capo+f)%12 in tones] for s in strings]
            for values in itertools.product(*options):
                if {pc for f,pc in values}!=tones:continue
                positive=[f for f,pc in values if f>0]
                if len(positive)>4 or positive and max(positive)-min(positive)>3:continue
                sounding=[opens[s]+capo+f for s,(f,pc) in zip(strings,values)]
                if min(sounding)%12!=root:continue
                cost=(len(positive),max(positive,default=0),sum(positive),max(strings)-min(strings))
                if best is None or cost<best[0]:best=(cost,strings,values)
        if best is None:raise ValueError(('Author a beginner grip',original['name'],track['id']))
        _,strings,values=best;frets=[-1]*6;fingers=[0]*6
        for s,(f,pc) in zip(strings,values):frets[s]=f
        for finger,s in enumerate(sorted((s for s in strings if frets[s]>0),key=lambda s:(frets[s],-s)),1):fingers[s]=finger
    suffix=alternative.removeprefix(target.rstrip('m'))
    sound=NAMES[root]+('maj7' if quality=='MAJOR_SEVENTH' else 'm7(no5)' if quality=='MINOR_SEVENTH_NO_FIVE' else 'sus2')
    shape_name=alternative if shape in SUBSTITUTIONS else NAMES[(root-capo)%12]+('maj7' if quality=='MAJOR_SEVENTH' else 'm7(no5)' if quality=='MINOR_SEVENTH_NO_FIVE' else 'sus2')
    v=dict(id='simple-'+original['id'],name=shape_name,trackId='simple-'+track['id'],frets=frets,fingers=fingers,barres=[],quality=quality,
        teaching=dict(root=root,soundingName=sound,group='新手友好替代',source='教材替代配法；保留原配法入口与真实和弦性质'))
    return v

def add_beginner_versions(score,practices):
    tracks={t['id']:t for t in score['tracks']};replacements={}
    for v in list(score['voicings']):
        alt=beginner_voicing(v,tracks[v['trackId']])
        if alt:replacements[v['id']]=alt
    if not replacements:return []
    original_voicings={v['id']:v for v in score['voicings']};changed_events={};changed_names={};new_voicings=[]
    selected_events={}
    for p in practices:
        if p.get('variantOf') or p.get('visibility','core')!='core':continue
        plan=compile_summary(score,p)
        chosen={e['event']['id'] for e in plan['events']}
        candidate_ids={v['id'] for v in original_voicings.values() if v['trackId']==p['mainTrackId'] and v['id'] in replacements}
        changes=any({(n['string'],n['fret']) for n in e['event']['notes']}=={(i+1,f) for i,f in enumerate(original_voicings[id]['frets']) if f>=0}
            for e in plan['events'] for id in candidate_ids)
        if changes:selected_events.setdefault(p['mainTrackId'],set()).update(chosen)
    for track_id in dict.fromkeys(original_voicings[id]['trackId'] for id in replacements):
        if track_id not in selected_events:continue
        track=tracks[track_id];new_track='simple-'+track_id
        score['tracks'].append(dict(track,id=new_track,name=track['name']+' · 新手替代'))
        mapping={}
        for v in list(original_voicings.values()):
            if v['trackId']!=track_id:continue
            alt=replacements.get(v['id'],dict(copy.deepcopy(v),id='simple-'+v['id'],trackId=new_track))
            mapping[v['id']]=alt;new_voicings.append(alt)
        for h in list(score['harmonies']):
            if h.get('voicingId') in mapping:score['harmonies'].append(dict(h,id='simple-'+h['id'],name=mapping[h['voicingId']]['name'],voicingId=mapping[h['voicingId']]['id']))
        for voice in list(score['voices']):
            if voice['trackId']!=track_id:continue
            clone=copy.deepcopy(voice);clone.update(id='simple-'+voice['id'],trackId=new_track)
            id_map={};changed=set()
            for old,e in zip(voice['events'],clone['events']):
                e['id']='simple-'+old['id'];positions={(n['string'],n['fret']) for n in old['notes']}
                if e.get('written',{}).get('tupletGroupIds'):
                    e['written']['tupletGroupIds']=['simple-'+id for id in e['written']['tupletGroupIds']]
                if old['id'] not in selected_events.get(track_id,set()):
                    e['notes']=[];e.pop('articulation',None);continue
                grip=next((v for v in original_voicings.values() if v['trackId']==track_id and v['id'] in replacements and positions=={(i+1,f) for i,f in enumerate(v['frets']) if f>=0}),None)
                if grip:
                    alt=replacements[grip['id']];changed.add(old['id'])
                    changed_names[old['id']]=alt['name']
                    e['notes']=[dict(id=e['id']+'-s-'+str(i+1),string=i+1,fret=f,midi=track['openStringMidi'][i]+track.get('capo',0)+f,sourceRef='教材替代 '+old['id']) for i,f in enumerate(alt['frets']) if f>=0]
                else:
                    for old_note,n in zip(old['notes'],e['notes']):n['id']='simple-'+old_note['id'];id_map[old_note['id']]=n['id']
            for relation in list(score['relations']):
                if relation['fromNoteId'] in id_map and relation['toNoteId'] in id_map:score['relations'].append(dict(relation,id='simple-'+relation['id'],fromNoteId=id_map[relation['fromNoteId']],toNoteId=id_map[relation['toNoteId']]))
            for group in list(score['tuplets']):
                if all(any(e['id']==id for e in voice['events']) for id in group['eventIds']):
                    g=copy.deepcopy(group);g['id']='simple-'+g['id'];g['voiceId']=clone['id'];g['eventIds']=['simple-'+id for id in g['eventIds']]
                    if 'parentId' in g:g['parentId']='simple-'+g['parentId']
                    score['tuplets'].append(g)
            score['voices'].append(clone);changed_events[track_id]=changed_events.get(track_id,set())|changed
        score['dynamics'] += [dict(d,trackId=new_track) for d in list(score['dynamics']) if d.get('trackId')==track_id]
    score['voicings']+=new_voicings
    variants=[]
    for p in list(practices):
        main=p['mainTrackId']
        if main not in changed_events or p.get('variantOf') or p.get('visibility','core')!='core':continue
        plan=compile_summary(score,p)
        if not any(e['event']['id'] in changed_events[main] for e in plan['events']):continue
        v=copy.deepcopy(p);v.pop('noteFingerings',None);v.pop('legacyIds',None)
        v.update(id=p['id']+'-beginner',title=p['title']+' · 简化替代',mainTrackId='simple-'+main,mixTrackIds=['simple-'+t if t==main else t for t in p['mixTrackIds']],variantOf=p['id'],familyId=p.get('familyId',p['id']),visibility='variant',order=len(practices)+len(variants))
        v['lesson']['actions']=[];v['lesson']['transitionIds']=[]
        v['lesson']['leadTrackIds']=['simple-'+t if t==main else t for t in v['lesson'].get('leadTrackIds',[])]
        v['teaching']['version']='简化替代 · '+ ' / '.join(dict.fromkeys(changed_names[e['event']['id']] for e in plan['events'] if e['event']['id'] in changed_names))
        v['teaching']['explanation']='新手友好替代：'+v['teaching']['version']+'。名称与构成音独立显示，原谱版保留。'
        variants.append(v)
    practices+=variants
    return [v['id'] for v in variants]

def hard_cut_joins(score,practices):
    core=sorted((p for p in practices if p.get('visibility','core')=='core' and not p.get('variantOf')),key=lambda p:p.get('order',0))
    at,_=starts(score);all_notes={n['id'] for v in score['voices'] for e in v['events'] for n in e['notes']}
    joins=[]
    for a,b in zip(core,core[1:]):
        pa=compile_summary(score,a);pb=compile_summary(score,b)
        va=next(v for v in reversed(pa['visits']) if v['measureIndex']>=0);vb=next(v for v in pb['visits'] if v['measureIndex']>=0)
        segments=[]
        for v in (va,vb):
            m=score['measures'][v['measureIndex']]
            selected={n['id'] for voice in score['voices'] for e in voice['events'] if at[e['measureId']]+q(e['onset'])<v['sourceEnd'] and at[e['measureId']]+q(e['onset'])+q(e['duration'])>v['sourceStart'] for n in e['notes']}
            affected=set(selected)
            for r in score['relations']:
                if r['fromNoteId'] in selected or r['toNoteId'] in selected:affected.update((r['fromNoteId'],r['toNoteId']))
            for group in score['tuplets']:
                es=[e for voice in score['voices'] for e in voice['events'] if e['id'] in group['eventIds']]
                if any(e['measureId']==m['id'] for e in es):affected.update(n['id'] for e in es for n in e['notes'])
            segments.append(dict(start=dict(measureId=m['id'],offset=rat(v['sourceStart']-at[m['id']])),end=dict(measureId=m['id'],offset=rat(v['sourceEnd']-at[m['id']])),entry='reattack',exit='truncate',affectedNoteIds=sorted(affected)))
        identity='JOIN-C-Am' if (a['id'],b['id'])==('FLOW-C','FLOW-Am') else 'join-'+digest((a['id']+'\0'+b['id']).encode())[:16]
        p=dict(id=identity,title=a['title']+' → '+b['title'],goal='前段末小节接后段首小节；边界截断并重触发，连续循环。',
            mainTrackId=a['mainTrackId'],mixTrackIds=list(dict.fromkeys(a['mixTrackIds']+b['mixTrackIds'])),suggestedTempo=a.get('suggestedTempo',60),segments=segments,
            visibility='hidden',order=len(practices)+len(joins),tempoMode=a.get('tempoMode','source'),lesson=dict(module=a['lesson']['module']),teaching=dict(explanation='按实际小节与拍点硬切；弱起、跨界延音及变拍号均可练。'))
        compile_summary(score,p)
        joins.append(p);a['lesson']['transitionIds']=[identity]
    return joins
