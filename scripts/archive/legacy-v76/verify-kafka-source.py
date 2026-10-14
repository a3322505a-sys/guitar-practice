"""Independent source reread: do not import the generating script or its manifest events."""
import csv, hashlib, json, sys, zipfile
from pathlib import Path
from fractions import Fraction
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]) if len(sys.argv)>1 else Path(r'C:\Users\b3322\Desktop\谱子\卡夫卡\始発とカブカ.gp')
with zipfile.ZipFile(source) as z: r=E.fromstring(z.read('Content/score.gpif'))
masters=list(r.find('MasterBars'))
lookup={e.tag:{x.get('id'):x for x in e} for e in r if e.tag in ('Bars','Voices','Beats','Notes','Rhythms')}
rows=list(csv.DictReader((ROOT/'app/src/test/resources/kafka-gp.csv').open(encoding='utf-8')))
def p(n,key): return n.find(f"Properties/Property[@name='{key}']")
expected=[];grace=[];expressions=[];gp5_errors=[];note_count=0;rest_count=0;ties=0
for phrase,group in __import__('itertools').groupby(rows,key=lambda row:row['phrase']):
 group=list(group);track=int(phrase.rsplit('-G',1)[1])
 for barno in sorted({int(row['bar']) for row in group}):
  master=masters[barno];refs=master.findtext('Bars').split()
  assert track<len(refs),(phrase,barno,track,refs)
  bar=lookup['Bars'][refs[track]]
  voice=next((v for v in bar.findtext('Voices').split() if v!='-1'),None)
  ids=lookup['Voices'][voice].findtext('Beats').split() if voice is not None else []
  tick=Fraction(0)
  for bid in ids:
   beat=lookup['Beats'][bid];rh=lookup['Rhythms'][beat.find('Rhythm').get('ref')]
   value={'Whole':1,'Half':2,'Quarter':4,'Eighth':8,'16th':16,'32nd':32}[rh.findtext('NoteValue')]
   duration=Fraction(48,value)
   dot=rh.find('AugmentationDot')
   if dot is not None: duration*=2-Fraction(1,2)**int(dot.get('count'))
   tuplet=rh.find('PrimaryTuplet')
   if tuplet is not None: duration*=Fraction(tuplet.get('den'))/Fraction(tuplet.get('num'))
   ns=[lookup['Notes'][nid] for nid in (beat.findtext('Notes') or '').split()]
   if beat.find('GraceNotes') is not None:
    grace.append((phrase,barno,int(tick),[(int(p(n,'Fret').findtext('Fret')),float(duration)) for n in ns]))
    for n in ns: expressions.append((phrase,barno,int(tick),6-int(p(n,'String').findtext('String')),'grace',f'{int(p(n,"Fret").findtext("Fret"))}/{float(duration)}'))
    continue
   assert duration.denominator==1
   if not ns: expected.append((phrase,barno,int(tick),int(duration),0,0,0,0,0));rest_count+=1
   for n in ns:
    s=6-int(p(n,'String').findtext('String'));f=int(p(n,'Fret').findtext('Fret'));midi=int(p(n,'Midi').findtext('Number'))
    tied=int(n.find('Tie') is not None and n.find('Tie').get('destination')=='true')
    dead=int(p(n,'Muted') is not None)
    expected.append((phrase,barno,int(tick),int(duration),s,f,midi,dead,tied));note_count+=1;ties+=tied
    for kind,node in [('ring',n.find('LetRing')),('palm',p(n,'PalmMuted'))]:
     if node is not None: expressions.append((phrase,barno,int(tick),s,kind,'1'))
    accent=n.find('Accent')
    if accent is not None:
     flags=int(accent.text)
     if flags&1: expressions.append((phrase,barno,int(tick),s,'short','1'))
     if flags&12: expressions.append((phrase,barno,int(tick),s,'accent','1'))
    slide=p(n,'Slide')
    if slide is not None and slide.findtext('Flags') in ('4','8','16'): expressions.append((phrase,barno,int(tick),s,'slide',slide.findtext('Flags')))
    if p(n,'Bended') is not None:
     points=[]
     for at,val in [('BendOriginOffset','BendOriginValue'),('BendMiddleOffset1','BendMiddleValue'),('BendMiddleOffset2','BendMiddleValue'),('BendDestinationOffset','BendDestinationValue')]:
      pos=float(p(n,at).findtext('Float'))/100*int(duration)
      if 'Middle' in at and pos==0: continue
      points.append((pos,float(p(n,val).findtext('Float'))/50))
     expressions.append((phrase,barno,int(tick),s,'bend','|'.join(f'{a}/{b}' for a,b in sorted(set(points)))))
   tick+=duration
  if not ids: expected.append((phrase,barno,0,int(master.findtext('Time').split('/')[0])*12,0,0,0,0,0));rest_count+=1
actual=[(row['phrase'],*(int(row[k]) for k in ('bar','tick','duration','string','fret','midi','dead','tie'))) for row in rows]
assert sorted(actual)==sorted(expected),'CSV differs from original GP graph'
actual_expr=[(x['phrase'],int(x['bar']),int(x['tick']),int(x['string']),x['kind'],x['value']) for x in csv.DictReader((ROOT/'app/src/test/resources/kafka-expressions.csv').open(encoding='utf-8'))]
assert sorted(actual_expr)==sorted(expressions),'Expression fixture differs from original note properties'
# GP5 is secondary evidence only; latin1 preserves byte values in mixed-encoded metadata.
gp5_path=source.with_suffix('.gp5')
deps=Path(r'C:\Users\b3322\Documents\ChatGPT\吉他谱子\work\七个Block练习\GuitarPro转换-v1-20261010\deps')
gp5_compared=0
if gp5_path.exists() and deps.exists():
 sys.path.insert(0,str(deps));import guitarpro
 gp=guitarpro.parse(str(gp5_path),encoding='latin1')
 for phrase,barno,tick,d,s,f,midi,dead,tie in expected:
  track=int(phrase.rsplit('-G',1)[1]);measure=gp.tracks[track].measures[barno]
  at=0;match=None
  for beat in measure.voices[0].beats:
   if at==tick: match=beat;break
   at+=beat.duration.time*12/960
  if s==0 or match is None: continue
  note=next((n for n in match.notes if n.string==s),None)
  if note is None or not dead and note.value!=f or note and (note.type.name=='tie')!=bool(tie): gp5_errors.append((phrase,barno,tick,s,f))
  gp5_compared+=1
result={'source':str(source),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'gpGraphRows':len(expected),'pitchedOrDeadNoteRows':note_count,'restBeats':rest_count,'tiedNoteRows':ties,'expressionRows':len(expressions),'graces':grace,'gp5ComparedNotes':gp5_compared,'gp5Differences':gp5_errors,'originalGpMatched':True}
out=ROOT/'work/kafka-v70/source-verification.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='graces'},ensure_ascii=False))
