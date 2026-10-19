"""Independent audit of shipped native data; does not import its authoring code."""
import json
from pathlib import Path
from fractions import Fraction as F
from song_package.protocol import read_container,validate_payloads,compile_summary,q
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/song-packages'
EXPECTED={'S-C-open':[48,50,52,53,55,57,59,60],'S-Am-open':[45,47,48,50,52,53,55,57],
 'S-G-open':[43,45,47,48,50,52,54,55],'S-Em-open':[40,42,43,45,47,48,50,52],
 'S-D-open':[50,52,54,55,57,59,61,62],'S-Bm-open':[47,49,50,52,54,55,57,59]}
SCALES={'MAJOR':[0,2,4,5,7,9,11],'NATURAL_MINOR':[0,2,3,5,7,8,10]}
PC=dict(C=0,D=2,E=4,F=5,G=7,A=9,B=11)
def no_null(value):
    assert value is not None,'null in native package'
    if isinstance(value,dict):
        assert 'legacyIds' not in value and not value.get('aliases')
        for v in value.values():no_null(v)
    elif isinstance(value,list):
        for v in value:no_null(v)
def audit(directory=ASSETS):
    result=[];packages={};warmups=[]
    for path in sorted(directory.glob('*.gpractice')):
        roots=validate_payloads(read_container(path));no_null(roots)
        assert '未提供' not in json.dumps(roots,ensure_ascii=False)
        score=roots['score.json'];ps=roots['practices.json']['practices'];pid=roots['manifest.json']['packageId']
        assert pid in {'local.teaching-native','local.demo-c','local.demo-capo','local.demo-rock'}
        packages[pid]={p['id'] for p in ps};byid={p['id']:p for p in ps};fingers=0;joins=0;lead_groups=0
        voicings={v['id']:v for v in score['voicings']};tracks={t['id']:t for t in score['tracks']};voice_tracks={v['id']:v['trackId'] for v in score['voices']}
        at={};end=F(0)
        for m in score['measures']:at[m['id']]=end;end+=q(m['actualDuration'])
        for v in voicings.values():
            assert len(v['frets'])==len(v['fingers'])==6 and v['quality'] and v['teaching']['soundingName']
            assert 'barres' in v
            assert all(f>=-1 and (n in range(1,5) if f>0 else n==0) for f,n in zip(v['frets'],v['fingers']))
            for finger in range(1,5):
                strings=[s+1 for s,(f,n) in enumerate(zip(v['frets'],v['fingers'])) if f>0 and n==finger]
                if len(strings)>1:
                    assert any(b['finger']==finger and b['fromString']==max(strings) and b['toString']==min(strings) for b in v['barres']),v['id']
        for h in score['harmonies']:
            assert h['voicingId'] in voicings and at[h['measureId']]+q(h['onset'])<q(h['validUntil'])<=end
        plans={p['id']:compile_summary(score,p) for p in ps}
        for p in ps:
            plan=plans[p['id']];l=p['lesson'];assert all(k in l for k in ('key','referenceChordName','warmupIds','transitionIds','leadTrackIds','chainStarts','actions'))
            assert l['actions'] and p['teaching']['version'] and p['teaching']['explanation']
            chosen={e['event']['id'] for e in plan['events']}
            assert all(a['eventId'] in chosen and a['text'] for a in l['actions']),(pid,p['id'],'action outside practice')
            assigned={f['sourceNoteId']:f['finger'] for f in p['noteFingerings']};needed={n['note']['id'] for n in plan['notes'] if n['note']['fret']>0}
            assert set(assigned)==needed and all(f in range(1,5) for f in assigned.values()),p['id'];fingers+=len(needed)
            warmups.extend(l['warmupIds'])
            for n in plan['notes']:
                t=tracks[voice_tracks[n['event']['voice']]];nn=n['note']
                assert nn['midi']==t['openStringMidi'][nn['string']-1]+t.get('capo',0)+nn['fret']
            if p['id']=='FB-3':assert {n['note']['midi'] for n in plan['notes']}==set(EXPECTED['S-C-open'])
            if p['id'] in EXPECTED:assert [n['note']['midi'] for n in plan['notes']][:8]==EXPECTED[p['id']]
            if l['module']=='SCALES':
                tonic=PC[l['key']['tonic']];minor=l['key']['scale']=='NATURAL_MINOR'
                pitches={n['note']['midi']%12 for n in plan['notes']};assert pitches<={(tonic+i)%12 for i in SCALES[l['key']['scale']]}
                pent={(tonic+i)%12 for i in ([0,3,5,7,10] if minor else [0,2,4,7,9])}
                assert tonic in pitches and pitches&pent and pitches&{tonic,(tonic+(3 if minor else 4))%12,(tonic+7)%12}
                midi={n['note']['midi'] for n in plan['notes']}
                collection=pent if 'pent' in p['id'] else {(tonic+i)%12 for i in SCALES[l['key']['scale']]}
                assert midi=={n for n in range(min(midi),max(midi)+1) if n%12 in collection},(p['id'],'missing scale degree inside register')
                if 'box' in p['id']:assert all(n['note']['fret']>0 for n in plan['notes'])
            if l['leadTrackIds']:
                used={voice_tracks[n['event']['voice']] for n in plan['notes']};assert set(l['leadTrackIds'])<used
                assert len(l['chainStarts'])>=2;lead_groups+=1
            for join in l['transitionIds']:
                jp=plans[join];target=plans['SOLO-'+p['id'].split('-')[-1]]
                assert len(jp['visits'])==2
                assert jp['visits'][0]['sourceStart']==plan['visits'][-1]['sourceStart']
                assert jp['visits'][1]['sourceStart']==target['visits'][0]['sourceStart'];joins+=1
        if pid.startswith('local.demo-'):
            assert len(ps)==6 and joins==2 and lead_groups==6
            for part in ('A','SOLO'):
                base=byid[part+'-original'];alt=byid[part+'-beginner'];assert alt['variantOf']==base['id']
                assert base['mixTrackIds']!=alt['mixTrackIds']
                assert len(plans[alt['id']]['notes'])<len(plans[base['id']]['notes'])
        result.append(dict(file=path.name,packageId=pid,practices=len(ps),explicitFrettedAssociations=fingers,missing=0,adjacentJoins=joins,independentLeadGroups=lead_groups))
    assert len(packages)==4
    for ref in warmups:
        if ':' in ref:
            length=int(ref.split(':')[0]);start=ref.index(':')+1;pid=ref[start:start+length];id=ref[start+length:]
        else:pid='local.teaching-native';id=ref
        assert id in packages[pid],ref
    return result
if __name__=='__main__':
    report=audit();out=ROOT/'docs/validation/v106-native-audit.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(report,ensure_ascii=False,indent=2))
