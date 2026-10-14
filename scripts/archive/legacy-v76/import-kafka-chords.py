"""Register exact simultaneous grips and stable let-ring accompaniment shapes.
No GP chord symbols exist. Analysis names describe played tones, never add strings.
"""
import importlib.util, json, collections
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('kafka_import',Path(__file__).with_name('import-kafka.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
parts,_,_,_=module.read(module.SOURCE)
grips=collections.defaultdict(list)
for p in parts:
 for e in p['events']:
  if len(e['ps'])>1:
   grips[tuple(tuple(x) for x in e['ps'])].append((p['id'],e['tick']))
 if p['id']=='KAF-B-9-G2':
  for at in range(0,p['length'],24):
   es=[e for e in p['events'] if at<=e['tick']<at+24]
   ps=tuple(sorted(set(tuple(x) for e in es for x in e['ps'])))
   if len({s for s,f in ps})==len(ps): grips[ps].append((p['id'],at))
   else:
    # Db–Ab → Eb–Ab bass move: two genuinely held dyads, not a three-note fictitious grip.
    for start in (at,at+12):
     pair=tuple(sorted(tuple(x) for e in es if start<=e['tick']<start+12 for x in e['ps']))
     grips[pair].append((p['id'],start))

N=['C','D♭','D','E♭','E','F','G♭','G','A♭','A','B♭','B']
def analyze(ps):
 midis=[([0,64,59,55,50,45,40][s]+f) for s,f in ps]
 pcs={m%12 for m in midis};bass=min(midis)%12
 # Explicit harmonic interpretations where the root is not the bass.
 overrides={frozenset((1,4,7,10)):(10,'dim7',3),frozenset((1,7,10)):(7,'dim',3)}
 if frozenset(pcs) in overrides: root,suffix,third=overrides[frozenset(pcs)]
 else:
  templates=[({0,4,7,10},'7',4),({0,3,7,10},'m7',3),({0,4,7,11},'maj7',4),({0,3,7,11},'m(maj7)',3),
   ({0,4,7},'',4),({0,3,7},'m',3),({0,2,7},'sus2',None),({0,5,7},'sus4',None),
   ({0,7},'5',None),({0},'八度双音',None),({0,10},'7(no3,no5)',None)]
  matches=[(root,suffix,third) for intervals,suffix,third in templates for root in pcs if {(x-root)%12 for x in pcs}==intervals]
  assert matches,(ps,pcs)
  root,suffix,third=sorted(matches,key=lambda x:(x[0]!=bass,x[0]))[0]
 name=N[root]+suffix
 if root!=bass: name+='/'+N[bass]
 assert {(m-root)%12 for m in midis}=={(x-root)%12 for x in pcs}
 return name,root,None if third is None else (root+third)%12,pcs

lines=['package com.a3322505a.guitarlearning.music','','/** Exact source grips; chord symbols are absent, so these are played-tone analysis names. */','object KafkaChords {','    val all = listOf(']
records=[]
for i,(ps,occ) in enumerate(grips.items()):
 name,root,third,pcs=analyze(ps);frets=[dict(ps).get(s) for s in range(6,0,-1)]
 base=min(f for f in frets if f is not None and f>0)
 fingers=[0 if f is None or f==0 else f-base+1 for f in frets]
 assert all(0<=x<=4 for x in fingers),(ps,fingers)
 source_bars=collections.defaultdict(set)
 for ident,tick in occ:
  p=next(p for p in parts if p['id']==ident);starts=[0]
  for meter in p['meters']: starts.append(starts[-1]+meter*12)
  bar=max(b for b,v in enumerate(starts[:-1]) if v<=tick)
  for a,b in p['occurrences']: source_bars[p['track']].add(a+bar)
 source='；'.join('G'+str(t)+' '+','.join(map(str,sorted(bs)))+'小节' for t,bs in source_bars.items())
 label=f'卡夫卡 · {name} · {"-".join("X" if f is None else str(f) for f in frets)}'
 group='卡夫卡 · 双音 riff' if len(ps)==2 and source_bars.keys()=={2} and all(not ident.startswith('KAF-B-') for ident,_ in occ) else '卡夫卡 · 分解伴奏指法' if source_bars.keys()=={2} else '卡夫卡 · 伴奏指法'
 description=f'始発とカブカ.gp · {source}；源谱无和弦标记，按实际组成音分析；用指为参考。'
 if len(pcs)==2 and name.find('7(')>=0: description+='双音不能唯一确定完整和声，此卡以七和弦省略音作候选分析名。'
 if '5' in name and 'no' not in name: description+='力量和弦，不含三音。'
 if len(pcs)==1: description+='仅根音与八度，不表示大／小性质。'
 if 'dim7' in name: description+='对称减七可等音重命名；本卡选B♭为分析根音，E是低音。'
 spellings=['C','C♯','D','D♯','E','F','F♯','G','G♯','A','A♯','B'] if min(min(bs) for bs in source_bars.values())>=117 else N
 for old,new in zip(N,spellings): name=name.replace(old,new) if old!=new else name
 label=f'卡夫卡 · {name} · {"-".join("X" if f is None else str(f) for f in frets)}'
 if '7(no3,no5)' in name: label+=' · 候选分析'
 roles='mapOf('+','.join(f'{k} to "{v}"' for k,v in ({6:'减五音',9:'减七音'} if 'dim7' in name else {6:'减五音'} if 'dim' in name else {}).items())+')'
 tone_names='mapOf('+','.join(f'{pc} to "{spellings[pc]}"' for pc in sorted(pcs))+')'
 lines += [f'        Chord("{label}",{root},{"null" if third is None else third},listOf('+','.join('null' if f is None else str(f) for f in frets)+'),listOf('+','.join(map(str,fingers))+'),',
  f'            soundingName="{name}",group="{group}",source="{description}",intervalRoles={roles},toneNames={tone_names}),']
 records.append(dict(id=i,name=label,soundingName=name,root=root,third=third,frets=frets,fingers=fingers,pcs=sorted(pcs),group=group,source=description,occurrences=occ))
lines+=['    )','}','']
(ROOT/'app/src/main/java/com/a3322505a/guitarlearning/music/KafkaChords.kt').write_text('\n'.join(lines),encoding='utf-8')
(ROOT/'work/kafka-v70/chords.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'count':len(records),'groups':dict(collections.Counter(x['group'] for x in records)),'names':[r['soundingName'] for r in records]},ensure_ascii=False))
