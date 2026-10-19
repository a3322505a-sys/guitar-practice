"""Source audit, intentionally does not import the teaching generator/converter."""
import gzip
import json
from pathlib import Path
from fractions import Fraction
from song_package.protocol import read_container, validate_payloads, q, compile_summary

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/song-packages'

def audit():
    result=[]
    legacy=validate_payloads(read_container(ASSETS/'teaching-legacy-v1.gpractice'))
    score=legacy['score.json']; events=score['voices'][0]['events']
    source=json.loads(gzip.decompress((ROOT/'scripts/archive/teaching-v31/legacy-export.json.gz').read_bytes()))
    for row in source:
        id=row['phrase']['id']; old=row['score']; current=[e for e in events if e['id'].startswith(id+'-e-')]
        before=old['voices'][0]['events']
        assert len(current)==len(before),(id,'event count')
        for a,b in zip(before,current):
            assert a['onset']==b['onset'] and a['duration']==b['duration'] and a['written']==b['written'] or (
                a['onset']==b['onset'] and a['duration']==b['duration'] and
                a['written']['baseDenominator']==b['written']['baseDenominator'] and a['written']['dots']==b['written']['dots']),id
            assert [(n['string'],n['fret'],n['midi'],n.get('dead',False)) for n in a['notes']]==[(n['string'],n['fret'],n['midi'],n.get('dead',False)) for n in b['notes']],id
            # Source technique values must survive except absolute ring endpoints, shifted with measures.
            for an,bn in zip(a['notes'],b['notes']):
                aa=dict(an.get('articulation',{})); ba=dict(bn.get('articulation',{})); aa.pop('ringUntil',None); ba.pop('ringUntil',None)
                assert aa==ba,(id,'technique')
        p=next(p for p in legacy['practices.json']['practices'] if p['id']==id)
        assert p['legacyIds']==[id]
        result.append(dict(id=id,events=len(current),notes=sum(len(e['notes']) for e in current),equivalent=True))
    new=validate_payloads(read_container(ASSETS/'teaching-v31.gpractice'))
    practices=new['practices.json']['practices']; s=new['score.json']; by_id={p['id']:p for p in practices}
    assert len(practices)==45
    for id,midi in [('S-C-major',[48,50,52,53,55,57,59,60]),('S-G-major',[43,45,47,48,50,52,54,55]),('S-D-major',[50,52,54,55,57,59,61,62]),('S-A-minor',[45,47,48,50,52,53,55,57])]:
        events=[e for v in s['voices'] if v['trackId']=='guitar' for e in v['events'] if e['id'].startswith(id+'-e-')]
        # Prefixes overlap the pentatonic IDs, so bind the exact practice measure IDs.
        measure_ids={m['id'] for m in s['measures'] if m['id'].startswith(id+'-m-')}
        notes=[n['midi'] for e in events if e['measureId'] in measure_ids for n in e['notes']]
        assert notes[:8]==midi,(id,notes[:8])
    for p in practices:
        compile_summary(s,p)
        refs=set(p['lesson'].get('warmupIds',[]))
        assert refs<=by_id.keys(),(p['id'],'warmup')
        assignments={f['sourceNoteId'] for f in p.get('noteFingerings',[])}
        # Explicit fingerings are checked against source notes by both contract validators.
        assert all(f.get('finger') in (1,2,3,4) for f in p.get('noteFingerings',[]))
    capo=[v for v in s['voicings'] if v['trackId']=='guitar-capo2']
    assert [v['teaching']['soundingName'] for v in capo]==['D','A','Bm','Gmaj7']
    result.append(dict(newPractices=len(practices),status='source and semantic checks passed',listening='not performed'))
    return result

if __name__=='__main__': print(json.dumps(audit(),ensure_ascii=False,indent=2))
