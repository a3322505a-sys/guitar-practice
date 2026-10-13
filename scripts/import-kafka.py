"""Import the user's GP7 graph. Formal A–M sections, never internal riffs, are the boundaries.

Run with the untouched source path. No recording-authenticity claims are made.
GPIF slide flags/bend units follow alphaTab's GpifParser; sound is sampled approximation.
"""
import argparse, collections, csv, hashlib, json, zipfile
from fractions import Fraction
from pathlib import Path
import xml.etree.ElementTree as ET

from song_assets.common import gp_graph, rhythm_ticks, positions
from song_assets.gp import audit_gp

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path(r'C:\Users\b3322\Desktop\谱子\卡夫卡\始発とカブカ.gp')
OUT=ROOT/'work/kafka-v70'
NAMES=['C','D♭','D','E♭','E','F','F♯','G','A♭','A','B♭','B']
def prop(n,name): return n.find(f"Properties/Property[@name='{name}']")
def number(n,name,child): return prop(n,name).findtext(child)
def floats(x): return str(float(x))
def expression_value(kind,value):
 if kind in ('ring','palm','accent','short'): return '1'
 if kind=='bend': return '|'.join(f'{float(at)}/{float(v)}' for at,v in value)
 if kind=='grace': return f'{value[0]}/{float(value[1])}'
 return str(value)

def read(source):
 audit_gp(source,'kafka-v72',[1,2,3])
 root,tables,masters,tracks,order=gp_graph(source)
 changes={int(a.findtext('Bar')):int(a.findtext('Value').split()[0]) for a in root.findall('MasterTrack/Automations/Automation') if a.findtext('Type')=='Tempo'}
 assert root.find('MasterTrack/Anacrusis') is not None
 assert len(masters)==149
 assert all(not m.findall('Repeat') for m in masters)
 sections=[(i,m.findtext('Section/Letter').strip()) for i,m in enumerate(masters) if m.find('Section') is not None]+[(len(masters),'END')]
 def rhythm(b):
  r=tables['Rhythms'][b.find('Rhythm').get('ref')]
  return rhythm_ticks(r)
 parts=[];raw=[];expr=[];coverage=[]
 for track_id in (1,2,3):
  track=tracks[str(track_id)]
  staff=track.find('Staves/Staff')
  assert list(map(int,number(staff,'Tuning','Pitches').split()))==[40,45,50,55,59,64]
  assert number(staff,'CapoFret','Fret')=='0'
  assert number(staff,'PartialCapoFret','Fret')=='0'
  allbars=[]
  for i,m in enumerate(masters):
   assert m.findtext('Time') in ('4/4','2/4')
   bar=tables['Bars'][m.findtext('Bars').split()[order.index(str(track_id))]]
   voices=[v for v in bar.findtext('Voices').split() if v!='-1'];assert len(voices)<=1
   beats=[tables['Beats'][bid] for bid in tables['Voices'][voices[0]].findtext('Beats').split()] if voices else []
   allbars.append(beats)
  # The unnumbered source pickup is a sixteenth REST in all three guitars.
  assert len(allbars[0])==1 and not allbars[0][0].findtext('Notes') and rhythm(allbars[0][0])[0]==3
  for (first,letter),(stop,_) in zip(sections,sections[1:]):
   last=stop-1;ident=f'KAF-{letter}-{first}-G{track_id}'
   events=[];links=[];tuplets=[];latest={};pending_grace={};tick=0;part_raw=[];part_expr=[]
   meters=[int(masters[b].findtext('Time').split('/')[0]) for b in range(first,stop)]
   swing=masters[first].findtext('TripletFeel');assert all(masters[b].findtext('TripletFeel')==swing for b in range(first,stop))
   tempo=changes[max(i for i in changes if i<=first)];assert not any(first<i<stop for i in changes)
   for bar_no in range(first,stop):
    bar_start=tick;expected=int(masters[bar_no].findtext('Time').split('/')[0])*12
    for b in allbars[bar_no]:
     d,tup=rhythm(b);grace=b.find('GraceNotes') is not None
     if not grace: assert d.denominator==1;d=int(d)
     notes=[tables['Notes'][nid] for nid in (b.findtext('Notes') or '').split()]
     ps=[];dead=[];incoming=[];xp={};target_links=[]
     velocity={'PPP':.25,'PP':.35,'P':.45,'MP':.65,'MF':.8,'F':1.0,'FF':1.1,'FFF':1.2}[b.findtext('Dynamic')]
     for n in notes:
      s=6-int(number(n,'String','String'));f=int(number(n,'Fret','Fret'));midi=int(number(n,'Midi','Number'))
      assert midi==[0,64,59,55,50,45,40][s]+f,(track_id,bar_no,s,f,midi)
      muted=prop(n,'Muted') is not None;tied=n.find('Tie') is not None and n.find('Tie').get('destination')=='true'
      if grace:
       assert b.findtext('GraceNotes')=='OnBeat' and prop(n,'Slide').findtext('Flags')=='2'
       pending_grace[s]=(f,float(d));part_expr.append((ident,bar_no,tick-bar_start,s,'grace',f'{f}/{float(d)}'));continue
      (dead if muted else ps).append(s if muted else (s,f));incoming.append(tied)
      part_raw.append((ident,bar_no,tick-bar_start,d,s,f,midi,int(muted),int(tied)))
      x={}
      if n.find('LetRing') is not None: x['ring']=True
      if prop(n,'PalmMuted') is not None: x['palm']=True
      if n.find('Accent') is not None:
       flags=int(n.findtext('Accent'));assert flags in (1,4,8)
       if flags&1: x['short']=True
       if flags&12: x['accent']=True
      if prop(n,'Bended') is not None:
       points=[(float(number(n,'BendOriginOffset','Float'))/100*d,float(number(n,'BendOriginValue','Float'))/50)]
       for field in ('BendMiddleOffset1','BendMiddleOffset2'):
        at=float(number(n,field,'Float'))/100*d
        if at: points.append((at,float(number(n,'BendMiddleValue','Float'))/50))
       points.append((float(number(n,'BendDestinationOffset','Float'))/100*d,float(number(n,'BendDestinationValue','Float'))/50))
       x['bend']=sorted(set(points))
      slide=int(number(n,'Slide','Flags')) if prop(n,'Slide') is not None else 0
      if slide in (4,8,16): x['slide']=slide
      elif slide: assert slide==2
      if s in pending_grace:
       x['grace']=pending_grace.pop(s)
      if prop(n,'HopoDestination') is not None:
       prior=latest[s];target_links.append((prior[0],tick,s,'HAMMER' if f>prior[1] else 'PULL'))
      elif s in latest and latest[s][2]: target_links.append((latest[s][0],tick,s,'SLIDE'))
      if x: xp[s]=x
      if not tied and not muted: latest[s]=(tick,f,slide==2)
      for k,v in x.items():
       if k!='grace': part_expr.append((ident,bar_no,tick-bar_start,s,k,expression_value(k,v)))
     if grace: continue
     if not incoming: part_raw.append((ident,bar_no,tick-bar_start,d,0,0,0,0,0))
     ps.sort();dead.sort()
     if any(incoming):
      assert all(incoming) and events and not dead
      previous=events[-1];assert previous['ps']==ps and previous['tick']+previous['duration']==tick
      offset=previous['duration']
      for s,x in xp.items():
       assert set(x)<= {'bend','ring'}
       old=previous['xp'].setdefault(s,{})
       if 'bend' in x: old.setdefault('bend',[]).extend((at+offset,value) for at,value in x['bend'])
      previous['duration']+=d
     else:
      if target_links: assert len(target_links)==len(ps)
      links.extend(target_links)
      events.append(dict(tick=tick,duration=d,ps=ps,dead=dead,xp=xp,velocity=velocity,pick='NONE' if target_links else 'DEFAULT'))
     if tup and tick%6==0: tuplets.append((tick,d))
     tick+=d
    if tick==bar_start:
     events.append(dict(tick=tick,duration=expected,ps=[],dead=[],xp={},velocity=.8,pick='DEFAULT'))
     part_raw.append((ident,bar_no,0,expected,0,0,0,0,0));tick+=expected
    assert tick==bar_start+expected,(track_id,bar_no,tick,bar_start,expected)
   assert not pending_grace and all(not x[2] for x in latest.values())
   if not any(e['ps'] or e['dead'] for e in events):
    coverage.append(dict(track=track_id,first=first,last=last,letter=letter,rest=True));continue
   role='节奏吉他' if track_id==1 or track_id==2 and letter=='B' else '主音吉他' if track_id==2 else '主音／填句吉他'
   part=dict(id=ident,first=first,last=last,letter=letter,track=track_id,trackName=track.findtext('Name'),role=role,tempo=tempo,
     meters=meters,swing=swing,events=events,links=links,tuplets=tuplets,length=tick,occurrences=[(first,last)])
   signature=json.dumps({k:part[k] for k in ('track','tempo','meters','swing','events','links','tuplets')},sort_keys=True)
   prior=next((p for p in parts if p['signature']==signature),None)
   if prior:
    prior['occurrences'].append((first,last));coverage.append(dict(track=track_id,first=first,last=last,letter=letter,reuse=prior['id']))
   else:
    part['signature']=signature;parts.append(part);raw+=part_raw;expr+=part_expr;coverage.append(dict(track=track_id,first=first,last=last,letter=letter,id=ident))
 return parts,raw,expr,coverage

def emit(parts,raw,expr,coverage,source):
 parts=sorted(parts,key=lambda p:(p['letter'],p['first'],p['track']))
 lines=['package com.a3322505a.guitarlearning.practice','','import com.a3322505a.guitarlearning.music.Position','',
  '/** Source GP formal sections A–M; three guitars kept separate. Generated by scripts/import-kafka.py. */','internal object Kafka {',
  '    val phrases = listOf('+','.join(f'p{i}()' for i in range(len(parts)))+')']
 for i,p in enumerate(parts):
  relation='；'.join(f'{a}–{b}' for a,b in p['occurrences'])
  variation=' · 变体' if any(q['letter']==p['letter'] and q['track']==p['track'] for q in parts[:i]) else ''
  title=f'卡夫卡 · {p["letter"]}{variation} · G{p["track"]} {p["role"]}'
  technique='扫弦／力量和弦／和弦变化' if p['track']==1 else '分解伴奏' if p['role']=='节奏吉他' else '完整旋律／双音／填句'
  explanation=f'源谱{p["letter"]}段，完整{p["first"]}–{p["last"]}小节；同段内部重复、回应、填句与结尾全部保留。'
  if len(p['occurrences'])>1: explanation+=f'完全相同的{relation}小节复用。'
  if p['first']==1: explanation+='未编号弱起在三个吉他轨均为十六分休止，不作为独立练习。'
  if 81 in range(p['first'],p['last']+1): explanation+='第81小节改为2/4，完整经过该收尾才计一轮。'
  explanation+='推弦、滑入／滑出、击勾弦与装饰滑音为采样近似；开放滑音无指定目标，示范用约3半音轮廓。'
  lines += [f'    private fun p{i}() = Phrase(',f'        id="{p["id"]}",title="{title}",category="{p["letter"]}类 · {p["role"]}",',
   f'        passage="{relation} 小节 · 完整乐段",source="始発とカブカ.gp · {p["trackName"]}（{p["role"]}）",',
   f'        sourceBpm={p["tempo"]},difficulty="进阶",technique="{technique}",conditions="标准调弦 · 无变调夹",',
   f'        use="卡夫卡 · {p["letter"]}类完整乐段",suggestedBpm={40 if p["tempo"]==80 else 80},goal="完整弹完本声部的整段及休止，保持{p["swing"]}摇摆。",',
   '        sequence=Sequence(events=listOf(']
  for e in p['events']:
   args=[str(e['tick']),str(e['duration']),positions(e['ps'])]
   if e['dead']: args+=['deadStrings=setOf('+','.join(map(str,e['dead']))+')']
   if e['velocity']!=1.: args+=[f'velocity={e["velocity"]}f']
   if e['pick']!='DEFAULT': args+=['pick=Pick.'+e['pick']]
   xp=[]
   for s,x in e['xp'].items():
    fields=[]
    for k,v in x.items():
     if k in ('ring','palm','accent','short'): fields.append(('palmMute' if k=='palm' else k)+'=true')
     elif k=='slide': fields.append(f'slide={v}')
     elif k=='grace': fields.append(f'grace=Grace({v[0]},{floats(v[1])})')
     elif k=='bend': fields.append('bend=listOf('+','.join(f'PitchPoint({floats(a)},{floats(b)})' for a,b in v)+')')
    xp.append(f'{s} to StringExpression('+','.join(fields)+')')
   if xp: args+=['stringExpression=mapOf('+','.join(xp)+')']
   lines+=['            Event('+','.join(args)+'),']
  lines += [f'        ),length={p["length"]},originalBar={p["first"]-1},explanation="{explanation}",',
   '        barMeters=listOf('+','.join(map(str,p['meters']))+'),swing=Swing.'+('EIGHTH' if p['swing']=='Triplet8th' else 'SIXTEENTH')+',',
   '        tuplets=listOf('+','.join(f'Tuplet({a},{b})' for a,b in p['tuplets'])+'),',
   '        links=listOf('+','.join(f'Legato({a},{b},{s},Technique.{k})' for a,b,s,k in p['links'])+'))', '    )']
 lines+=['}','']
 (ROOT/'app/src/main/java/com/a3322505a/guitarlearning/practice/Kafka.kt').write_text('\n'.join(lines),encoding='utf-8')
 for name,header,rows in [('kafka-gp.csv','phrase,bar,tick,duration,string,fret,midi,dead,tie',raw),('kafka-expressions.csv','phrase,bar,tick,string,kind,value',expr)]:
  with (ROOT/'app/src/test/resources'/name).open('w',encoding='utf-8',newline='') as f:
   writer=csv.writer(f);writer.writerow(header.split(','));writer.writerows(rows)
 OUT.mkdir(parents=True,exist_ok=True)
 manifest={'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sourceTitle':'始発とカブカ','pickup':'unnumbered sixteenth rest in all guitar tracks','coverage':coverage,'phrases':[{k:v for k,v in p.items() if k!='signature'} for p in parts]}
 (OUT/'import.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({'phrases':[(p['id'],p['occurrences'],len(p['events'])) for p in parts],'rawRows':len(raw)},ensure_ascii=False))
 return manifest

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('source',nargs='?',type=Path,default=SOURCE)
 source=parser.parse_args().source
 emit(*read(source),source)
