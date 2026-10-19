"""Independent audit of filled teaching data and retained original performance events."""
import json
from pathlib import Path
from song_package.protocol import read_container,validate_payloads,compile_summary,q

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/song-packages'

def retained(original,current):
    # Original performance data must remain byte-semantically identical on its own tracks.
    for field in ('documentId','measures','tempos','swing','form','relations','tuplets'):
        if field in ('relations','tuplets'):
            by_id={x['id']:x for x in current[field]}
            assert all(x==by_id[x['id']] for x in original[field]),field
        else:assert original[field]==current[field],field
    for field in ('tracks','voices'):
        by_id={x['id']:x for x in current[field]}
        assert all(x==by_id[x['id']] for x in original[field]),field
    assert current['dynamics'][:len(original['dynamics'])]==original['dynamics']
    for before in original['voicings']:
        after=next(v for v in current['voicings'] if v['id']==before['id'])
        for key,value in before.items():
            if key not in ('barres','quality','teaching'):assert after[key]==value,(before['id'],key)
    for before in original['harmonies']:
        after=next(h for h in current['harmonies'] if h['id']==before['id'])
        assert all(after[k]==v for k,v in before.items()),before['id']

def audit(directory=ASSETS):
    result=[]
    for path in sorted(directory.glob('*.gpractice')):
        roots=validate_payloads(read_container(path));s=roots['score.json'];ps=roots['practices.json']['practices']
        assert '未提供' not in json.dumps(roots,ensure_ascii=False),path.name
        plans={p['id']:compile_summary(s,p) for p in ps};by_id={p['id']:p for p in ps}
        fingers=0
        for p in ps:
            plan=plans[p['id']];assigned={f['sourceNoteId']:f['finger'] for f in p.get('noteFingerings',[])}
            needed={n['note']['id'] for n in plan['notes'] if n['note']['fret']>0}
            assert all(assigned.get(id) in (1,2,3,4) for id in needed),(path.name,p['id'],'missing finger')
            assert p['lesson'].get('key') and p['lesson'].get('referenceChordName'),p['id']
            assert p.get('teaching',{}).get('explanation'),p['id']
            fingers+=len(needed)
        for v in s['voicings']:
            assert len(v['frets'])==len(v['fingers'])==6 and v.get('quality') and v.get('teaching'),v['id']
            assert all(f>=-1 and (finger in (1,2,3,4) if f>0 else finger==0) for f,finger in zip(v['frets'],v['fingers'])),v['id']
            assert 'barres' in v,v['id']
            for finger in (1,2,3,4):
                frets={f for f,n in zip(v['frets'],v['fingers']) if f>0 and n==finger}
                for fret in frets:
                    strings=[i+1 for i,(f,n) in enumerate(zip(v['frets'],v['fingers'])) if f==fret and n==finger]
                    if len(strings)>1 and all(v['frets'][i-1]>=fret for i in range(min(strings),max(strings)+1)):
                        assert any(b['finger']==finger and b['fret']==fret and b['fromString']==max(strings) and b['toString']==min(strings) for b in v['barres']),v['id']
        assert all('validUntil' in h for h in s['harmonies']),path.name
        at={};time=q({'n':0,'d':1})
        for m in s['measures']:at[m['id']]=time;time+=q(m['actualDuration'])
        vs={v['id']:v for v in s['voicings']}
        for voice in s['voices']:
            for e in voice['events']:
                if len(e['notes'])<2 or any(n.get('dead') or n.get('harmonic') for n in e['notes']):continue
                grip={(n['string'],n['fret']) for n in e['notes']}
                matching={v['id'] for v in vs.values() if v['trackId']==voice['trackId'] and grip=={(i+1,f) for i,f in enumerate(v['frets']) if f>=0}}
                if not matching:continue
                start=at[e['measureId']]+q(e['onset']);end=start+q(e['duration'])
                assert any(h.get('voicingId') in matching and at[h['measureId']]+q(h['onset'])<=start and q(h['validUntil'])>=end for h in s['harmonies']),(path.name,e['id'],'chord interval ends before its repeated grip')
        core=sorted((p for p in ps if p.get('visibility','core')=='core' and not p.get('variantOf')),key=lambda p:p.get('order',0))
        joins=0
        for a,b in zip(core,core[1:]):
            refs=a['lesson'].get('transitionIds',[]);assert refs,(a['id'],b['id'])
            join=by_id[refs[0]];v=plans[join['id']]['visits'];av=plans[a['id']]['visits'][-1];bv=plans[b['id']]['visits'][0]
            assert len(v)==2 and (v[0]['sourceStart'],v[0]['sourceEnd'])==(av['sourceStart'],av['sourceEnd']) and (v[1]['sourceStart'],v[1]['sourceEnd'])==(bv['sourceStart'],bv['sourceEnd']),join['id']
            assert all(seg['entry']=='reattack' and seg['exit']=='truncate' for seg in join['segments']),join['id']
            joins+=1
        original=next((p for p in (ROOT/'scripts/archive/teaching-v31/packages').glob('*.gpractice') if validate_payloads(read_container(p))['manifest.json']['packageId']==roots['manifest.json']['packageId']),None)
        if original:retained(validate_payloads(read_container(original))['score.json'],s)
        leads=[]
        for p in ps:
            ids=p['lesson'].get('leadTrackIds',[])
            if ids:
                voices={v['id']:v['trackId'] for v in s['voices']}
                tracks={voices[n['event']['voice']] for n in plans[p['id']]['notes']}
                assert set(ids)<tracks and len(p['lesson']['chainStarts'])>=2,p['id']
                leads.append(p['id'])
        if roots['manifest.json']['packageId']=='local.teaching-v31':
            assert {'LEAD-1','JR-2'}<=set(leads)
            simple=by_id['SONG-1-beginner'];assert simple['variantOf']=='SONG-1'
            assert 'Fmaj7' in simple['teaching']['version'] and 'Gsus2' in simple['teaching']['version']
            assert by_id['CH-Bm-A-beginner']['variantOf']=='CH-Bm-A'
        result.append(dict(file=path.name,revision=roots['manifest.json']['revision'],practices=len(ps),explicitFrettedAssociations=fingers,missing=0,voicings=len(s['voicings']),boundedHarmonies=len(s['harmonies']),adjacentJoins=joins,independentLeadGroups=leads,
            sourceAudit='archived performance retained' if original else 'legacy export audited separately' if roots['manifest.json']['packageId']=='local.teaching-legacy' else 'original teaching recipes'))
    return result

if __name__=='__main__':print(json.dumps(audit(),ensure_ascii=False,indent=2))
