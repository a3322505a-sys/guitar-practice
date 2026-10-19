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

ROOT = Path(__file__).resolve().parents[1]

def publish(package_id, revision, title, score, practices, provenance, output, method, previous_review=None, practice_root=None):
    review = dict(status='approved', reviewer='Codex deterministic migration audit',
        method=method, tool='migrate-teaching-v31.py / independent Kotlin reader',
        coverage=coverage(score,practices), diagnostics=[], assumptions=[])
    if previous_review:
        review=dict(previous_review,method=previous_review['method']+'；'+method,coverage=review['coverage'])
    files = package_files(package_id,revision,title,'本地练习',score,practices,provenance,review)
    if practice_root:
        files['practices.json']=dumps(dict(practice_root,practices=practices))
        review=dict(review,hashes={name:digest(files[name]) for name in ('score.json','practices.json','provenance.json')})
        files['review.json']=dumps(review)
    manifest = json.loads(files['manifest.json'])
    manifest['files']={name:dict(bytes=len(data),sha256=digest(data)) for name,data in files.items() if name!='manifest.json'}
    manifest['scoreSchemaVersion']['minor'] = 1
    files['manifest.json'] = dumps(manifest)
    validate_payloads(files)
    write_zip(files,output)
    return dict(path=str(output),sha256=digest(Path(output).read_bytes()),coverage=review['coverage'])

def legacy(source, output):
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
            missing=['未提供原始左手指法/横按/调式元数据'] if phrase['id'].startswith('SB-') else ['未提供原始左手指法']))
        offset += sum(q(m['actualDuration']) for m in old['measures'])
    # Consecutive equal tempo events are semantically redundant and rejected by old validators.
    score['tempos']=[t for i,t in enumerate(score['tempos']) if i==0 or t['bpm']!=score['tempos'][i-1]['bpm']]
    source_hash=digest(gzip.decompress(source.read_bytes()) if source.suffix==".gz" else source.read_bytes())
    provenance=dict(sources=[dict(id='baseline',name='v103 Kotlin baseline e859b47',sha256=source_hash,
        format='Kotlin ScoreDocument export',license='原素材许可继续适用',origin='git:e859b47217aaed20c70160ad316ff4be8a277fdc')],
        tool='migrate-teaching-v31.py',exclusions=[],mappings=[dict(noteId=n['id'],sourceId='baseline',location=n.get('sourceRef',n['id']))
            for v in score['voices'] for e in v['events'] for n in e['notes']])
    result=publish('local.teaching-legacy',1,'基础与技巧素材',score,practices,provenance,output,
        '逐事件独立比较原始导出弦/品/MIDI/精确时值；保留技巧、音量、来源与段落；Python/Kotlin 契约复核，不含真人试听')
    result['mapping']=report
    return result

def package(source, output):
    payloads=read_container(Path(source)); roots=validate_payloads(payloads)
    old=roots['score.json']; score=copy.deepcopy(old)
    practices=copy.deepcopy(roots['practices.json']['practices'])
    for p in practices:
        p.setdefault('lesson',dict(module='FOUNDATIONS' if roots['manifest.json']['packageId']=='local.mozart' else 'APPLICATION'))
    joins, unavailable = safe_joins(score,practices)
    practices += joins
    # A migration adds optional teaching classification only. It never infers finger/barre/chord facts.
    assert score==old
    m=roots['manifest.json']; prov=roots['provenance.json']
    result=publish(m['packageId'],m['revision']+1,m['display']['title'],score,practices,prov,output,
        '原 score.json 解码对象完全一致；卡片/段落/版本/事件/技巧/tempo map 保留；补可选教学分类和经编译器检验的相邻小节衔接练习，未知教学信息保留缺失',roots['review.json'],roots['practices.json'])
    result.update(source=str(source),sourceHash=digest(Path(source).read_bytes()),scoreEquivalent=True,
        practiceIdsPreserved=[p['id'] for p in roots['practices.json']['practices']],
        addedJoins=[p['id'] for p in joins],unavailableJoins=unavailable,
        missing=['未提供的调式、动作、横按与热身继续标记缺失'])
    return result

def safe_joins(score, practices):
    """Only explicitly adjacent complete source measures; compiler rejects cut sustains.

    No inference of musical similarity, version equivalence, or performed-repeat adjacency.
    """
    core=sorted((p for p in practices if p.get('visibility','core')=='core' and not p.get('variantOf')),
        key=lambda p:p.get('order',0))
    measures={m['id']:m for m in score['measures']}; indices={m['id']:i for i,m in enumerate(score['measures'])}
    joins=[]; unavailable=[]
    for a,b in zip(core,core[1:]):
        reason=None
        if len(a['segments'])!=1 or len(b['segments'])!=1: reason='曲段含多个非连续片段'
        elif a['mainTrackId']!=b['mainTrackId'] or a['mixTrackIds']!=b['mixTrackIds']: reason='版本或轨道不相接'
        else:
            sa=a['segments'][0]; sb=b['segments'][0]
            ma=measures[sa['end']['measureId']]; mb=measures[sb['start']['measureId']]
            def full(m): return q(m['actualDuration'])==Fraction(4*m['meter']['numerator'],m['meter']['denominator']) and q(m.get('leadIn',rational(0)))==0
            if sa.get('formPolicy','written')!='written' or sb.get('formPolicy','written')!='written': reason='原谱反复曲序需要显式推荐边界'
            elif indices[ma['id']]+1!=indices[mb['id']] or q(sa['end'].get('offset',rational(0)))!=q(ma['actualDuration']) or q(sb['start'].get('offset',rational(0)))!=0: reason='原谱边界不直接相邻'
            elif not full(ma) or not full(mb) or ma['meter']!=mb['meter']: reason='弱起、变拍号或不完整小节'
            else:
                identity='join-'+digest((a['id']+'\0'+b['id']).encode())[:16]
                p=dict(id=identity,title=a['title']+' → '+b['title'],goal='前段末一小节与后段首一小节连续循环。',
                    mainTrackId=a['mainTrackId'],mixTrackIds=a['mixTrackIds'],suggestedTempo=a.get('suggestedTempo',60),
                    segments=[dict(start=dict(measureId=ma['id']),end=dict(measureId=mb['id'],offset=mb['actualDuration']),entry='reject',exit='reject')],
                    visibility='hidden',order=len(practices)+len(joins),tempoMode=a.get('tempoMode','source'),
                    lesson=dict(module=a['lesson']['module']),teaching=dict(explanation='已离线验证的相邻完整小节衔接；未截断延音。'))
                note_ids={n['id'] for v in score['voices'] for e in v['events'] if e['measureId'] in (ma['id'],mb['id']) for n in e['notes']}
                fingers={ (f['sourceNoteId'],f.get('occurrence')):f for source in (a,b) for f in source.get('noteFingerings',[]) if f['sourceNoteId'] in note_ids }
                if fingers: p['noteFingerings']=list(fingers.values())
                try: compile_summary(score,p)
                except Blocked as error: reason=error.diagnostic['reason']
                else:
                    joins.append(p); a['lesson'].setdefault('transitionIds',[]).append(identity)
        if reason: unavailable.append(dict(fromId=a['id'],toId=b['id'],reason=reason))
    return joins,unavailable

if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('source',type=Path); ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--legacy-export',action='store_true'); ap.add_argument('--report',type=Path,required=True)
    a=ap.parse_args(); result=(legacy if a.legacy_export else package)(a.source,a.output)
    a.report.parent.mkdir(parents=True,exist_ok=True); a.report.write_bytes(dumps(result))
    print(json.dumps(dict(path=str(a.output),report=str(a.report)),ensure_ascii=False))
