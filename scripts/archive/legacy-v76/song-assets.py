"""Produce reviewable candidates from the three protected v72 adapters.

Never registers app catalogue entries or modifies source scores. Output must be
outside tracked application files; existing-song candidates must match v72 bytes.
"""
import argparse,collections,importlib.util,json,re,sys
from pathlib import Path
from song_assets.common import ImportBlocked,fingerprint,window_offsets,validate_window,write_report
from song_assets.gp import audit_gp
from song_assets.pdf import validate_reviewed_pdf

ROOT=Path(__file__).resolve().parents[1]

def adapter(filename):
    spec=importlib.util.spec_from_file_location(filename.replace('-','_'),ROOT/'scripts'/filename)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module

def normalized(part):
    return [dict(tick=e['tick'],duration=e['duration'],positions=e.get('positions',e.get('ps',[])),
                 deadStrings=e['dead'],pick=e['pick'],velocity=e['velocity'],
                 expression={k:v for k,v in e.items() if k not in ('tick','duration','positions','ps','dead','pick','velocity')})
            for e in part['events']]

def run(manifest, source, output, reviewed_data=None):
    output=Path(output).resolve()
    # Existing tracked output paths may never be used as a candidate directory.
    if output==ROOT or output.is_relative_to(ROOT/'app') or output.is_relative_to(ROOT/'scripts') or output.is_relative_to(ROOT/'docs'):
        raise ImportBlocked('output','path',output.name,'use a separate candidate directory, e.g. work/song-assets')
    if output.exists() and any(output.iterdir()):
        raise ImportBlocked('output','path',output.name,'nonempty candidate directory; do not overwrite reviewed results')
    output.mkdir(parents=True,exist_ok=True)
    m=json.loads(Path(manifest).read_text(encoding='utf8'))
    report=dict(songId=m['songId'],source=dict(format=m['source']['format'],name=Path(source).name,sha256=fingerprint(source)),
                status='blocked',diagnostics=[],outputs=[],command=f'python scripts/song-assets.py --manifest scripts/song-manifests/{Path(manifest).name} --source <local-score> --output <new-candidate-dir>')
    try:
        allowed={'songId','profile','source','tracks','segments','protectedOutputs','catalogue','teaching','reviewedData','reviewedDataSha256','objectName'}
        if set(m)-allowed: raise ImportBlocked('manifest','fields',sorted(set(m)-allowed),'unknown selection manifest fields')
        if not re.fullmatch(r'[A-Z][A-Z0-9-]*',m['songId']): raise ImportBlocked('manifest','songId',m['songId'],'stable ASCII song ID required')
        for segment in m['segments']:
            if set(segment)-{'id','track','start','end','outsideWindow'}: raise ImportBlocked(segment['id'],'fields',list(segment),'unknown selection fields')
            for point in (segment['start'],segment['end']):
                if set(point)-{'bar','tick'} or type(point['bar']) is not int or type(point.get('tick',0)) is not int:
                    raise ImportBlocked(segment['id'],'boundary',point,'source bar and integer tick required')
            if segment.get('outsideWindow','practicePadding')!='practicePadding': raise ImportBlocked(segment['id'],'outsideWindow',segment['outsideWindow'],'unknown padding policy')
        if report['source']['sha256']!=m['source']['sha256']:
            raise ImportBlocked('source','sha256',report['source']['sha256'],'source fingerprint differs from reviewed manifest')
        profile=m['profile'];practice=[]
        if m['source']['format']!=('reviewed-pdf' if profile=='reviewed-pdf' else 'gpif'):
            raise ImportBlocked('manifest','source.format',m['source']['format'],'format and adapter disagree')
        path=output/'app/src/main/java/com/a3322505a/guitarlearning/practice';path.mkdir(parents=True)
        (output/'app/src/test/resources').mkdir(parents=True)
        if profile in ('shayou-v72','kafka-v72'):
            ids=m['tracks'];report['diagnostics']=audit_gp(source,profile,ids)
            module=adapter('import-shayou.py' if profile=='shayou-v72' else 'import-kafka.py')
            module.ROOT=output
            if profile=='shayou-v72':
                parts=module.read_gp(Path(source));module.emit(parts,Path(source))
                raw=[r for p in parts for r in p['raw']];expressions=[]
            else:
                module.OUT=output/'adapter-report';parts,raw,expressions,coverage=module.read(Path(source));module.emit(parts,raw,expressions,coverage,Path(source))
            by_id={p['id']:p for p in parts}
            if {p['id'] for p in m['segments']}!=set(by_id): raise ImportBlocked('manifest','segments',list(by_id),'source ID set differs from protected adapter')
            for segment in m['segments']:
                p=by_id[segment['id']];meters=p.get('meters',[4]*(p['last']-p['first']+1));events=normalized(p)
                a,b=window_offsets(meters,p['first'],segment['start'],segment['end'])
                validate_window(events,p['links'],a,b,p['id'])
                if a!=0 or b!=sum(meters)*12 or segment['track']!=p['track']:
                    raise ImportBlocked(p['id'],'selection',segment,'existing GP adapters preserve complete protected passages')
                practice.append(dict(id=p['id'],track=segment['track'],sourceWindow=dict(start=segment['start'],end=segment['end']),
                                     barMeters=meters,length=sum(meters)*12,events=events,links=p['links'],tuplets=p['tuplets'],
                                     swing=p.get('swing','NONE'),catalogue='external curated registration; never auto-register source'))
            written={}
            for ident,bar,tick,duration,string,fret,midi,dead,tie in raw:
                e=written.setdefault((ident,bar,tick),dict(id=ident,sourceBar=bar,tick=tick,duration=duration,positions=[],deadStrings=[],tiedStrings=[],midi=[]))
                if string:
                    if dead: e['deadStrings'].append(string)
                    else: e['positions'].append([string,fret]);e['midi'].append([string,midi])
                    if tie: e['tiedStrings'].append(string)
            facts=dict(format='gp-written-events',events=list(written.values()),rows=raw,expressions=expressions)
        elif profile=='reviewed-pdf':
            data_path=Path(reviewed_data or ROOT/m['reviewedData']);data=json.loads(data_path.read_text(encoding='utf8'))
            if data['sha256']!=report['source']['sha256']: raise ImportBlocked('PDF data','sha256',data['sha256'],'transcription and PDF fingerprint differ')
            report['diagnostics']=validate_reviewed_pdf(data)
            if m.get('reviewedDataSha256') and fingerprint(data_path)!=m['reviewedDataSha256']:
                raise ImportBlocked('PDF data','sha256',fingerprint(data_path),'reviewed event data changed; review before generation')
            if {s['id'] for s in m['segments']}!={p['id'] for p in data['parts']}: raise ImportBlocked('manifest','segments','ID set','differs from reviewed selection')
            for p,s in zip(data['parts'],m['segments']):
                if s['id']!=p['id'] or s['start']!={'bar':p['first'],'tick':p['start']} or s['end']!={'bar':p['last'],'tick':p['end']}:
                    raise ImportBlocked(p['id'],'selection',s,'manifest and legacy first/last/start/end disagree')
            if len(m['tracks'])!=1: raise ImportBlocked('PDF manifest','tracks',m['tracks'],'one explicitly reviewed voice required')
            module=adapter('generate-your-trip.py');summary=module.generate(data_path,output,m.get('objectName','YourTrip'),m['tracks'][0],True)
            facts=dict(format='reviewed-pdf-events',bars=data['bars'],reviewNotes=data.get('reviewNotes',[]),
                events=[dict(sourceBar=b['number'],page=b['page'],tick=e['tick'],duration=e['duration'],positions=e['notes'],deadStrings=e['dead'],tiedStrings=e['tied'],
                    expression={k:v for k,v in e.items() if k not in ('tick','duration','notes','dead','tied','pdfX')},pdfX=e['pdfX']) for b in data['bars'] for e in b['events']])
            for i,s in enumerate(m['segments']):
                item=summary[i].copy();events=item.pop('eventsData');links=item.pop('linksData');tuplets=item.pop('tupletsData')
                item['eventCount']=item.pop('events');item['linkCount']=item.pop('links');item['tupletCount']=item.pop('triplets')
                practice.append(dict(s,**item,events=events,links=links,tuplets=tuplets,length=(data['parts'][i]['last']-data['parts'][i]['first']+1)*48,
                    outsideWindow='practicePadding',catalogue='manual curated registration'))
            report['reviewedDataSha256']=fingerprint(data_path)
            report['diagnostics'].append(dict(category='approximation',location=m['songId'],field='sound',value='existing samples',reason='bend/grace/legato/vibrato demonstration, not original recording timbre'))
        else: raise ImportBlocked('manifest','profile',profile,'no reviewed adapter')
        ir=dict(schemaVersion=1,ticksPerQuarter=12,songId=m['songId'],source=report['source'],sourceFacts=facts,
                practice=practice,catalogue=m.get('catalogue',[]),teaching=m.get('teaching',[]))
        write_report(output/'source-practice.json',ir)
        for relative,expected in m.get('protectedOutputs',{}).items():
            actual=fingerprint(output/relative)
            report['outputs'].append(dict(path=relative,sha256=actual,expectedSha256=expected,equal=actual==expected))
            if actual!=expected: raise ImportBlocked(relative,'sha256',actual,'candidate differs from protected output')
        if not m.get('protectedOutputs'):
            report['outputs']=[dict(path=str(f.relative_to(output)),sha256=fingerprint(f)) for f in path.glob('*.kt')]
        report['processedEvents']=len(facts.get('rows',[])) if 'rows' in facts else sum(len(b['events']) for b in facts['bars'])
        report['skippedEvents']=0
        report['durationTicks']=sum(p['length'] if 'length' in p else (p['sourceWindow'][2]-p['sourceWindow'][0]+1)*48 for p in practice)
        report['status']='compatible' if m.get('protectedOutputs') else 'review-needed'
    except ImportBlocked as error:
        report['diagnostics'].append(error.diagnostic);write_report(output/'report.json',report);raise
    except (AssertionError,KeyError,ValueError,TypeError) as error:
        diagnostic=ImportBlocked('adapter','validation',str(error),'unsupported source or inconsistent reviewed facts; nothing registered')
        report['diagnostics'].append(diagnostic.diagnostic);write_report(output/'report.json',report);raise diagnostic from error
    report['categories']=dict(collections.Counter(d['category'] for d in report['diagnostics']))
    write_report(output/'report.json',report)
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--manifest',required=True,type=Path);parser.add_argument('--source',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);parser.add_argument('--reviewed-data',type=Path)
    args=parser.parse_args()
    try:
        report=run(args.manifest,args.source,args.output,args.reviewed_data)
        print(json.dumps({k:report[k] for k in ('songId','status','processedEvents','durationTicks','categories')},ensure_ascii=False))
    except ImportBlocked as error:
        print(str(error),file=sys.stderr);sys.exit(2)
