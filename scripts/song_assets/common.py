"""Shared GP graph, exact rhythm, selection windows and reproducible reports."""
from fractions import Fraction
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile


class ImportBlocked(ValueError):
    def __init__(self, location, field, value, reason):
        self.diagnostic = dict(category='unsupported-blocker', location=location,
                               field=field, value=str(value), reason=reason)
        super().__init__(f'{location}: {field}={value}: {reason}')


def fingerprint(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def positions(ps):
    return 'listOf('+','.join(f'Position({s},{f})' for s,f in ps)+')' if ps else 'emptyList()'


def gp_graph(path):
    with zipfile.ZipFile(path) as archive:
        root = ET.fromstring(archive.read('Content/score.gpif'))
    tables = {name: {e.get('id'): e for e in root.find(name)}
              for name in ('Bars','Voices','Beats','Notes','Rhythms')}
    return root, tables, list(root.find('MasterBars')), {
        t.get('id'): t for t in root.find('Tracks')}, root.findtext('MasterTrack/Tracks').split()


def rhythm_ticks(rhythm):
    values={'Whole':48,'Half':24,'Quarter':12,'Eighth':6,'16th':3,'32nd':Fraction(3,2)}
    name=rhythm.findtext('NoteValue')
    if name not in values: raise ImportBlocked('Rhythm#'+str(rhythm.get('id')),'NoteValue',name,'unsupported subdivision')
    ticks=Fraction(values[name]); dots=rhythm.find('AugmentationDot'); tup=rhythm.find('PrimaryTuplet')
    if dots is not None: ticks *= sum(Fraction(1,2)**i for i in range(int(dots.get('count'))+1))
    if tup is not None: ticks *= Fraction(int(tup.get('den')),int(tup.get('num')))
    return ticks,tup is not None


def window_offsets(meters, first_bar, start, end):
    """Source bar numbers, 12 ticks/quarter, [start,end); actual meters, no 48 assumption."""
    starts=[0]
    for meter in meters: starts.append(starts[-1]+meter*12)
    def point(p):
        bar,tick=p['bar'],p.get('tick',0); index=bar-first_bar
        if index==len(meters) and tick==0: return starts[-1]
        if not 0<=index<len(meters) or not 0<=tick<=meters[index]*12:
            raise ImportBlocked(f'bar {bar}','tick',tick,'outside source meter')
        return starts[index]+tick
    a,b=point(start),point(end)
    if a>=b: raise ImportBlocked('segment','window',[start,end],'empty or reversed half-open window')
    return a,b


def validate_window(events, links, a, b, location):
    for e in events:
        t=e.get('tick',e.get('at')); stop=t+e['duration']
        if any(t<cut<stop for cut in (a,b)):
            raise ImportBlocked(location+f' event@{t}','window',[a,b],'boundary bisects a written event')
        ring=e.get('ringEndTick',e.get('ringEnd'))
        if a<=t<b and ring is not None and ring>b:
            raise ImportBlocked(location+f' event@{t}','ringEndTick',ring,'sustain crosses selection; explicit reviewed policy required')
    for link in links:
        source,target=link[:2]
        if (a<=source<b)!=(a<=target<b):
            raise ImportBlocked(location,'Legato',link,'source/target separated by selection')


def write_report(path, report):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
