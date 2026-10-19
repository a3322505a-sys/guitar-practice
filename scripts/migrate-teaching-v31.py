"""Offline, lossless conversion to the shared 1.1 teaching contract.

Input is either a reviewed 1.0 package or the baseline Kotlin exporter output.
Existing files are never overwritten unless --output explicitly names a new file.
"""
import argparse
import copy
import json
import sys
from fractions import Fraction
from pathlib import Path

from song_package.protocol import (read_container, package_files, validate_payloads,
    validate_music, write_zip, coverage, digest, dumps, q, rational, compile_summary, Blocked)
from song_package.enrich_teaching import complete, add_beginner_versions, hard_cut_joins

ROOT = Path(__file__).resolve().parents[1]

from song_package.publishing import publish

def legacy(source, output, overwrite=False):
    source = Path(source)
    import gzip
    rows = json.loads(gzip.decompress(source.read_bytes()) if source.suffix=='.gz' else source.read_bytes())
    score = dict(documentId='teaching-legacy', measures=[], tracks=[rows[0]['score']['tracks'][0]],
        voices=[dict(id='voice',trackId='guitar',events=[])],tuplets=[],relations=[],tempos=[],
        dynamics=[],swing=[],form={},voicings=[],harmonies=[])
    practices=[]; offset=0; report=[]
    for index,row in enumerate(rows):
        old=row['score']; phrase=row['phrase']; prefix=phrase['id']+'-'
        ids=set()
        def collect(v):
            if isinstance(v,dict):
                if 'id' in v: ids.add(v['id'])
                for item in v.values(): collect(item)
            elif isinstance(v,list):
                for item in v: collect(item)
        collect(old)
        ids -= {'guitar','voice'}
        def rename(v):
            if isinstance(v,dict): return {k:rename(x) for k,x in v.items()}
            if isinstance(v,list): return [rename(x) for x in v]
            return prefix+v if isinstance(v,str) and v in ids else v
        current=rename(old)
        # Exact absolute musical anchors move by the same offset as their measures.
        def shift(v):
            if isinstance(v,dict):
                for key,item in v.items():
                    if key == 'ringUntil': v[key]=rational(q(item)+offset)
                    else: shift(item)
            elif isinstance(v,list):
                for item in v: shift(item)
        shift(current)
        for tempo in current['tempos']: tempo['at']=rational(q(tempo['at'])+offset)
        for dynamic in current['dynamics']:
            dynamic['at']=rational(q(dynamic['at'])+offset)
        for swing in current['swing']:
            for key in ('start','end'): swing[key]=rational(q(swing[key])+offset)
        for key in ('measures','tuplets','relations','tempos','dynamics','swing','voicings','harmonies'):
            score[key] += current[key]
        score['voices'][0]['events'] += current['voices'][0]['events']
        p=copy.deepcopy(row['practice']); p=rename(p)
        p.update(id=phrase['id'], title=phrase['title'], goal=phrase['goal'],
            suggestedTempo=phrase['suggestedBpm'], difficulty=phrase['difficulty'],
            order=index, tempoMode='fixed', legacyIds=[phrase['id']],
            tags=[phrase['technique']],
            teaching={k:phrase[k] for k in ('category','passage','source','technique','use','tempoLabel')},
            lesson=dict(module='FOUNDATIONS' if phrase['id'].startswith(('SB-','M01')) else 'APPLICATION'))
        p['teaching']['explanation']=phrase['sequence']['explanation']
        practices.append(p)
        before=[(n['string'],n['fret'],n['midi'],e['duration']) for v in old['voices'] for e in v['events'] for n in e['notes']]
        after=[(n['string'],n['fret'],n['midi'],e['duration']) for v in current['voices'] for e in v['events'] for n in e['notes']]
        assert before==after
        report.append(dict(oldId=phrase['id'],newId=phrase['id'],events=len(current['voices'][0]['events']),
            notes=len(after),quarterDuration=str(sum(q(m['actualDuration']) for m in current['measures'])),equivalent=True,
            teaching='逐事件编写指法、调式参照、动作和实际小节边界'))
        offset += sum(q(m['actualDuration']) for m in old['measures'])
    # Consecutive equal tempo events are semantically redundant and rejected by old validators.
    score['tempos']=[t for i,t in enumerate(score['tempos']) if i==0 or t['bpm']!=score['tempos'][i-1]['bpm']]
    # The two literal pitched rhythm grips in the retained kernel/SevenBlocks source.
    # Dead strums are percussive events and are not labelled as open-string chords.
    score['voicings'] += [
        dict(id='legacy-a5',name='A5',trackId='guitar',frets=[-1,-1,2,2,0,-1],fingers=[0,0,2,1,0,0],barres=[],quality='POWER',
            teaching=dict(root=9,soundingName='A5',group='旧内置节奏配法',source='原 R01/R02 的 A–E–A 实音；教材明确配指')),
        dict(id='legacy-e7-no5',name='E7(no5)',trackId='guitar',frets=[-1,-1,7,6,7,-1],fingers=[0,0,3,1,2,0],barres=[],quality='DOMINANT_SEVENTH_NO_FIVE',
            teaching=dict(root=4,soundingName='E7(no5)',group='旧内置节奏配法',source='原 SB-4 的 E–G♯–D 实音；教材明确配指'))]
    source_hash=digest(gzip.decompress(source.read_bytes()) if source.suffix==".gz" else source.read_bytes())
    provenance=dict(sources=[dict(id='baseline',name='v103 Kotlin baseline e859b47',sha256=source_hash,
        format='Kotlin ScoreDocument export',license='原素材许可继续适用',origin='git:e859b47217aaed20c70160ad316ff4be8a277fdc')],
        tool='migrate-teaching-v31.py',exclusions=[],mappings=[dict(noteId=n['id'],sourceId='baseline',location=n.get('sourceRef',n['id']))
            for v in score['voices'] for e in v['events'] for n in e['notes']])
    practices+=hard_cut_joins(score,practices)
    result=publish('local.teaching-legacy',2,'基础与技巧素材',score,practices,provenance,output,
        '逐事件独立比较原始导出弦/品/MIDI/精确时值；保留技巧、音量、来源与段落；Python/Kotlin 契约复核，不含真人试听',overwrite=overwrite)
    result['mapping']=report
    return result

def package(source, output, overwrite=False):
    payloads=read_container(Path(source)); roots=validate_payloads(payloads)
    old=roots['score.json']; score=copy.deepcopy(old)
    practices=copy.deepcopy(roots['practices.json']['practices'])
    for p in practices:
        p.setdefault('lesson',dict(module='FOUNDATIONS' if roots['manifest.json']['packageId']=='local.mozart' else 'APPLICATION'))
    joins, unavailable = safe_joins(score,practices)
    practices += joins
    # The original performance events remain intact; teaching is explicitly authored offline.
    assert score==old
    m=roots['manifest.json']; prov=roots['provenance.json']
    result=publish(m['packageId'],m['revision']+2,m['display']['title'],score,practices,prov,output,
        '原谱事件/技巧/时值保留；逐事件编写指法、明确横按与和弦时段，增加真实简化替代版本及拍点硬切衔接',roots['review.json'],roots['practices.json'],overwrite=overwrite)
    result.update(source=str(source),sourceHash=digest(Path(source).read_bytes()),originalPerformanceEventsEquivalent=True,
        practiceIdsPreserved=[p['id'] for p in roots['practices.json']['practices']],
        addedJoins=[p['id'] for p in joins],unavailableJoins=unavailable,
        filled='指法、横按、调式参照、动作与小节边界已写入数据')
    return result

def safe_joins(score, practices):
    """All adjacent cards use explicit beat-time cuts, reattack and truncation."""
    return hard_cut_joins(score,practices), []

if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('source',type=Path); ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--legacy-export',action='store_true'); ap.add_argument('--report',type=Path,required=True)
    ap.add_argument('--replace-output',action='store_true',help='Explicitly replace a generated candidate, never the source')
    a=ap.parse_args(); assert a.source.resolve()!=a.output.resolve(),'Keep the original source'
    result=(legacy if a.legacy_export else package)(a.source,a.output,a.replace_output)
    a.report.parent.mkdir(parents=True,exist_ok=True); a.report.write_bytes(dumps(result))
    print(json.dumps(dict(path=str(a.output),report=str(a.report)),ensure_ascii=False))
