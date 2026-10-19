"""Native authoring primitives. No legacy input, inferred fingering, or data repair."""
import copy
import re
from fractions import Fraction as F
from pathlib import Path
from .protocol import rational as rat, q, coverage, package_files, validate_payloads, write_zip, digest, dumps
from .teaching_data import OPEN, CHORDS, BARRES, NAMES
from .teaching import QUALITIES

ROOT = Path(__file__).resolve().parents[2]
MODULES = dict(FOUNDATIONS='基础启蒙', CHORDS='和弦教学', SCALES='音阶教学', APPLICATION='综合应用')
SUFFIX = dict(MAJOR='', MINOR='m', MAJOR_SEVENTH='maj7', ADD_NINE='add9', POWER='5', SUS_TWO='sus2',
              SUS_FOUR_NO_FIVE='sus4(no5)', MINOR_SEVENTH_NO_FIVE='m7(no5)',MINOR_SEVENTH='m7',SUS_FOUR='sus4',
              SIXTH='6',MINOR_SEVENTH_ADD_ELEVEN='m7(add11)',DOMINANT_SEVENTH='7',OCTAVE='8')

def tone_names(root,name,intervals):
    letter=re.match('[A-G]',name)[0];letters='CDEFGAB';natural=[0,2,4,5,7,9,11]
    result={}
    for interval in intervals:
        degree={0:0,2:1,3:2,4:2,5:3,7:4,9:5,10:6,11:6}[interval]
        index=(letters.index(letter)+degree)%7;pc=(root+interval)%12;accidental=(pc-natural[index]+6)%12-6
        result[str(pc)]=letters[index]+('♯'*accidental if accidental>=0 else '♭'*(-accidental))
    return result

def key(tonic, minor=False):
    return dict(tonic=tonic, scale='NATURAL_MINOR' if minor else 'MAJOR')

def reference(context):
    return context['tonic'] + ('m' if context['scale']=='NATURAL_MINOR' else '')

def practice_key(package, practice):
    return str(len(package))+':'+package+practice

def note(string, fret, finger):
    assert 1<=string<=6 and fret>=0 and (finger in (1,2,3,4) if fret else finger==0)
    return (string, fret, finger)

def movement(a, b):
    """Compile literal routes for precisely these two authored grips, outside the App."""
    af,ai=CHORDS[a][2:];bf,bi=CHORDS[b][2:]
    retained=[]; moves=[]
    for finger in range(1,5):
        old=[(6-i,f) for i,(f,n) in enumerate(zip(af,ai)) if f>0 and n==finger]
        new=[(6-i,f) for i,(f,n) in enumerate(zip(bf,bi)) if f>0 and n==finger]
        fmt=lambda ps:'、'.join(f'{s}弦{f}品' for s,f in ps)
        same=set(old)&set(new)
        if same: retained.append(f'{finger}指保留'+fmt(sorted(same)))
        if old!=new:
            moves.append(f'{finger}指'+((fmt(old)+' → '+fmt(new)) if old and new else ('落到'+fmt(new)) if new else ('从'+fmt(old)+'抬起')))
    return '；'.join((retained or ['无同弦同品同指的保留指'])+moves)+'；下一拍落稳，X 弦保持消音。', bool(retained)

class PracticePack:
    def __init__(self, package_id, title, bpm=72):
        self.id=package_id;self.title=title;self.bpm=bpm;self.practices=[];self.fingers={}
        self.score=dict(documentId=package_id,measures=[],tracks=[],voices=[],tuplets=[],relations=[],
            tempos=[dict(at=rat(0),beatUnit=rat(1),bpm=bpm)],dynamics=[],swing=[],form={},voicings=[],harmonies=[])

    def track(self, id, title='吉他', capo=0):
        if not any(t['id']==id for t in self.score['tracks']):
            self.score['tracks'].append(dict(id=id,name=title,openStringMidi=OPEN,capo=capo))
            self.score['voices'].append(dict(id=id+'-voice',trackId=id,events=[]))
        return id

    def measures(self, id, count):
        ids=[id+'-m-'+str(i) for i in range(count)]
        self.score['measures'] += [dict(id=m,label=str(len(self.score['measures'])+i+1),
            meter=dict(numerator=4,denominator=4,beatGroups=[1]*4),actualDuration=rat(4)) for i,m in enumerate(ids)]
        return ids

    def voicing(self, name, track):
        id=track+'-v-'+str(list(CHORDS).index(name))
        if any(v['id']==id for v in self.score['voicings']):return id
        quality,root,frets,fingers=CHORDS[name];capo=next(t for t in self.score['tracks'] if t['id']==track)['capo']
        sounding=(name.split('/')[0] if capo==0 and name!='G-open' else NAMES[(root+capo)%12]+SUFFIX[quality])
        if '/' in name:sounding+='/'+NAMES[(NAMES.index(name.split('/')[1])+capo)%12]
        teaching=dict(root=(root+capo)%12,soundingName=sounding,group='V3.1 原生配法',source='逐弦编写 · 标准调弦',
            intervalRoles={str(i):{0:'根音',2:'九音' if quality=='ADD_NINE' else '二音',3:'小三音',4:'大三音',5:'十一音' if quality=='MINOR_SEVENTH_ADD_ELEVEN' else '四音',7:'五音',9:'六音',10:'小七音',11:'大七音'}[i] for i in QUALITIES[quality]},
            toneNames=tone_names((root+capo)%12,sounding,QUALITIES[quality]))
        third=next(((root+capo+i)%12 for i in QUALITIES[quality] if i in (3,4)),None)
        if third is not None:teaching['third']=third
        self.score['voicings'].append(dict(id=id,name='G' if name=='G-open' else name,trackId=track,frets=list(reversed(frets)),
            fingers=list(reversed(fingers)),barres=copy.deepcopy(BARRES.get(name,[])),quality=quality,teaching=teaching))
        return id

    def events(self, id, measures, track, route, step=F(1), palm=False):
        at=F(0);events=[];last_harmony=None;capo=next(t for t in self.score['tracks'] if t['id']==track)['capo']
        starts={m['id']:i*4 for i,m in enumerate(self.score['measures'])}
        for i,item in enumerate(route):
            duration=step;art=dict(pick='down' if i%2==0 else 'up',palmMute=palm)
            harmony=None
            if isinstance(item,dict):
                harmony=item.get('harmony');duration=F(item['duration']);art.update(item.get('articulation',{}));item=item['notes']
            mid=measures[int(at//4)];eid=id+'-e-'+str(i);positions=[]
            if isinstance(item,str):
                vid=self.voicing(item,track);v=next(v for v in self.score['voicings'] if v['id']==vid)
                positions=[note(s+1,f,v['fingers'][s]) for s,f in enumerate(v['frets']) if f>=0]
                art.setdefault('strumDelayMs',12)
                harmony=item
            elif item is not None: positions=[item] if isinstance(item,tuple) else item
            if harmony:
                vid=self.voicing(harmony,track)
                start=starts[mid]+at%4;end=start+duration
                if last_harmony and last_harmony['voicingId']==vid and q(last_harmony['validUntil'])==start:
                    last_harmony['validUntil']=rat(end)
                else:
                    last_harmony=dict(id=eid+'-h',measureId=mid,onset=rat(at%4),name='G' if harmony=='G-open' else harmony,voicingId=vid,validUntil=rat(end))
                    self.score['harmonies'].append(last_harmony)
            assert duration>0 and at%4+duration<=4,(id,i)
            written=next(dict(baseDenominator=d,dots=dots) for d in (1,2,4,8,16,32,64) for dots in range(3)
                if F(4,d)*sum(F(1,2**j) for j in range(dots+1))==duration)
            ns=[]
            for s,f,finger in positions:
                nid=eid+'-s-'+str(s);ns.append(dict(id=nid,string=s,fret=f,midi=OPEN[s-1]+capo+f))
                if f>0:self.fingers[nid]=finger
            events.append(dict(id=eid,measureId=mid,onset=rat(at%4),duration=rat(duration),written=written,notes=ns,articulation=art))
            at+=duration
        assert at==len(measures)*4,(id,at,len(measures))
        next(v for v in self.score['voices'] if v['trackId']==track)['events']+=events
        return events

    def practice(self,id,title,measures,track,module,context,goal,*,mix=None,lead=None,warmups=None,actions=None,
                 variant=None,version='标准版',boundaries=None,segments=None,hidden=False):
        assert actions,'Author explicit anticipatory instructions for every practice'
        p=dict(id=id,title=title,goal=goal,mainTrackId=track,mixTrackIds=mix or [track],
            segments=segments or [dict(start=dict(measureId=measures[0]),end=dict(measureId=measures[-1],offset=rat(4)))],
            suggestedTempo=self.bpm,tempoMode='fixed',difficulty='循序练习',order=len(self.practices),tags=[MODULES[module]],
            visibility='hidden' if hidden else 'variant' if variant else 'core',
            teaching=dict(category=MODULES[module],passage=str(len(measures))+' 小节',source='原创 V3.1 原生教材',
                technique=goal,use=MODULES[module],explanation=goal,version=version,tempoLabel='BPM'),
            lesson=dict(module=module,key=context,referenceChordName=reference(context),warmupIds=warmups or [],
                transitionIds=[],leadTrackIds=lead or [],chainStarts=boundaries or [measures[0]],actions=actions))
        if variant:p.update(variantOf=variant,familyId=variant)
        self.practices.append(p);return p

    def phrase(self,id,title,route,module,context,goal,step=F(1),track='guitar',capo=0,**kwargs):
        self.track(track,capo=capo);duration=sum(F(n['duration']) if isinstance(n,dict) else step for n in route)
        assert duration%4==0,(id,duration)
        measures=self.measures(id,int(duration/4));self.events(id,measures,track,route,step)
        kwargs.setdefault('actions',[dict(eventId=id+'-e-0',kind='position',text=goal)])
        return self.practice(id,title,measures,track,module,context,goal,**kwargs)

    def finish(self, filename):
        indices={m['id']:i for i,m in enumerate(self.score['measures'])}
        for voice in self.score['voices']:
            occupied={e['measureId'] for e in voice['events']}
            voice['events'] += [dict(id=voice['id']+'-rest-'+m,measureId=m,onset=rat(0),duration=rat(4),
                written=dict(baseDenominator=1,dots=0),notes=[],measureRest=True) for m in indices if m not in occupied]
            voice['events'].sort(key=lambda e:(indices[e['measureId']],q(e['onset'])))
        for p in self.practices:
            selected=set()
            for seg in p['segments']:
                selected.update(list(indices)[indices[seg['start']['measureId']]:indices[seg['end']['measureId']]+1])
            ns=[n for v in self.score['voices'] if v['trackId'] in p['mixTrackIds'] for e in v['events'] if e['measureId'] in selected for n in e['notes']]
            p['noteFingerings']=[dict(sourceNoteId=n['id'],finger=self.fingers[n['id']],source='教材逐音显式配指') for n in ns if n['fret']>0]
        sources=['scripts/build-teaching-v31.py','scripts/song_package/native_teaching.py','scripts/song_package/teaching_data.py','scripts/song_package/daily_practice.py']
        provenance=dict(sources=[dict(id='native' if i==0 else 'recipe-'+str(i),name=self.title+' · '+Path(source).name,sha256=digest((ROOT/source).read_bytes()),format='Native V3.1 authored score',
            license='项目原创教材',origin=source) for i,source in enumerate(sources)],tool='native-teaching',exclusions=[],
            mappings=[dict(noteId=n['id'],sourceId='native',location=e['id']) for v in self.score['voices'] for e in v['events'] for n in e['notes']])
        review=dict(status='approved',reviewer='Codex structural audit',method='独立校验弦品音高、和弦集合、配指与横按、事件时值及教学引用；未作真人试听/实弹验收',
            tool='audit-native-teaching.py / Kotlin SongReader',coverage=coverage(self.score,self.practices),diagnostics=[],assumptions=[])
        files=package_files(self.id,2,self.title,'原创教学',self.score,self.practices,provenance,review)
        manifest=__import__('json').loads(files['manifest.json']);manifest['scoreSchemaVersion']['minor']=1
        files['manifest.json']=dumps(manifest);validate_payloads(files)
        out=ROOT/'app/src/main/assets/song-packages'/filename;write_zip(files,out,overwrite=True)
        return dict(file=filename,packageId=self.id,practices=len(self.practices),**review['coverage'])
