"""Review annotations on top of vector-extracted note/stem/beam evidence, not OCR guesses."""
import json,copy,csv
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
scan=json.loads((ROOT/'work/your-trip-v72/scan.json').read_text(encoding='utf8'))
selected=set(range(1,50))|set(range(98,117))|set(range(136,142))
bars=[]
for b in scan:
    if b['bar'] not in selected: continue
    n=b['bar'];events=[]
    for raw in b['events']:
        e=dict(tick=sum(x['duration'] for x in events),duration=int(raw['d']),notes=[],dead=[],tied=[],pdfX=raw['x'])
        for word in raw['notes']:
            string,note=word.split(':');s=int(string)
            if note=='X': e['dead'].append(s)
            else:
                e['notes'].append([s,int(note.strip('()'))])
                if '(' in note: e['tied'].append(s)
        if n in (37,45) and raw['beams']==2 and e['tick']>=30 and e['tick']<36:
            e['duration']=2;e['triplet']=True
        events.append(e)
    assert sum(e['duration'] for e in events)==48,(n,events)
    bars.append(dict(number=n,page=b['page'],events=events))
lookup={b['number']:b for b in bars}
def event(bar,tick): return next(e for e in lookup[bar]['events'] if e['tick']==tick)
def link(bar,source,target,string,kind):
    event(bar,target).setdefault('legato',[]).append(dict(source=source,string=string,kind=kind))
def hp(bar,start,string):
    step=2 if bar in (37,45) else 3
    link(bar,start,start+step,string,'HAMMER');link(bar,start+step,start+2*step,string,'PULL')
link(5,0,3,4,'HAMMER')
for n in (26,28,30):
    hp(n,0,1);hp(n,18,2);hp(n,36,2)
for n in (27,29,31):
    hp(n,6,3);hp(n,24,4)
hp(32,0,1);hp(32,18,2)
link(33,24,27,3,'SLIDE')
hp(37,30,2);hp(45,30,1)
for n in (3,9):
    event(n,24)['grace']=dict(string=3,fret=9,kind='SLIDE')
    event(n,36)['grace']=dict(string=3,fret=11,kind='SLIDE')
for n in (13,21): event(n,18)['grace']=dict(string=2,fret=2,kind='HAMMER')
event(5,36)['bend']=dict(string=3,points=[[0,0],[12,2]])
event(10,0)['vibrato']=True
event(114,0)['strum']=True
for b in bars:
    if 98<=b['number']<=113:
        stop=next(x for x in (101,105,109,113) if x>=b['number'])
        for e in b['events']:
            if e['notes']: e['ringToBarEnd']=stop
    if b['number']==115:
        sounding=[e for e in b['events'] if e['notes']]
        for i,e in enumerate(sounding): e['velocity']=round(.65+.35*i/(len(sounding)-1),4)

parts=[
 dict(id='YT-GROOVE',title='Your Trip · 双音切分与完整收尾',first=1,last=10,start=0,end=12,use='Your Trip · 节奏与伴奏',goal='保持死音与双音的十六分切分，接住推弦和滑音后的句尾。',technique='双音切分、死音、击弦、推弦、装饰滑音',bpm=60,description='第1小节末死音弱起，2–9完整主题，保留第10小节第一拍的揉弦落点；随后三拍留空供循环。'),
 dict(id='YT-ARPEGGIO',title='Your Trip · 低音与高音分解',first=10,last=25,start=18,end=48,use='Your Trip · 节奏与伴奏',goal='完整弹完低音与高音的问答，保留内部休止和最后和弦的延音。',technique='低音交替、分解、装饰击弦、跨小节延音',bpm=60,description='从第10小节第2拍后半进入，前一主题的揉弦音不重复；前一拍半留空，11–25的展开、变化和收束完整保留。'),
 dict(id='YT-LEGATO',title='Your Trip · 连续击勾弦推进',first=26,last=33,start=0,end=24,use='Your Trip · 旋律与连奏',goal='每组只拨起音，连续击弦、勾弦后保持休止；弹到最后和弦延音结束。',technique='连续击勾弦、跨弦、句尾和弦',bpm=50,description='保留26–32的推进与第33小节前两拍的收尾和弦；后两拍属于下一主题的进入音，此练习留空，不把句尾截断。'),
 dict(id='YT-RIFF',title='Your Trip · 切分主题与高把位回应',first=33,last=49,start=24,end=48,use='Your Trip · 旋律与连奏',goal='从滑音进入，连续完成中把位主题、高把位回应和结尾双音。',technique='切分、死音、三连音击勾弦、换把',bpm=50,description='第33小节前两拍留空，从第3拍滑音进入；34–49的主题、回应与结尾连续练习，内部重复不拆成多个入口。'),
 dict(id='YT-RING',title='Your Trip · 延响伴奏与渐强收束',first=98,last=116,start=0,end=48,use='Your Trip · 节奏与伴奏',goal='保留和弦长音与低音移动，让分解音在标记范围内延响，再渐强收束。',technique='let ring、附点节奏、长延音、扫弦、渐强',bpm=60,description='98–116完整慢密度伴奏段：开头和弦、分解发展、长音和114–116扫弦收束一起保留。原谱let ring以每组虚线终点收音，长音小节不是空白。'),
 dict(id='YT-END',title='Your Trip · 跨弦八度与尾奏收束',first=136,last=141,start=0,end=48,use='Your Trip · 节奏与伴奏',goal='完整弹完尾奏准备、跨弦八度移动和最后下行收束，保持中间弦消音。',technique='跨弦八度、死音、连续移位、尾奏',bpm=55,description='保留136–137的尾奏准备及138–141跨弦八度下行收束；130–135与前面主题高度相似，不再重复收录；142空终止小节不另练。'),
]
data=dict(source='your_trip.pdf',sha256='3293c583569de637fa69396bd3280a483e3d1cef42a3645c7c31e03349406bc7',bpm=142,capo=2,meter='4/4',tickPerQuarter=12,bars=bars,parts=parts,
 reviewNotes=['原PDF为单个od.guit.声部；不声称原录音真实性。','无字音符的符干是延音续写；括号音为跨小节延音目标。','37与45的H/P组为十六分三连音，每音2 tick，不能按普通十六分3 tick处理。','装饰音的弦品和技巧取原谱；微小时值1.5 tick与推弦线性轮廓为示范近似。','115渐强用逐次力度0.65到1.0近似；原谱没有数值力度。','选择窗外的拍位是练习留空，独立标记padding，不冒充原谱休止。'])
dest=ROOT/'scripts/score-data/your-trip.json';dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
fixture=ROOT/'app/src/test/resources/your-trip-pdf.csv'
with fixture.open('w',newline='',encoding='utf8') as f:
    w=csv.writer(f);w.writerow(['bar','tick','duration','string','fret','dead','tie'])
    for b in bars:
        for e in b['events']:
            for s,fret in e['notes']: w.writerow([b['number'],e['tick'],e['duration'],s,fret,0,int(s in e['tied'])])
            for s in e['dead']: w.writerow([b['number'],e['tick'],e['duration'],s,0,1,0])
            if not e['notes'] and not e['dead']: w.writerow([b['number'],e['tick'],e['duration'],0,0,0,0])
print(len(bars),'source bars',len(parts),'curated phrases')
