from pathlib import Path
import json, collections, hashlib
import pdfplumber

SOURCE=Path(r'C:\Users\b3322\Desktop\谱子\your_trip.pdf')
OUT=Path(__file__).resolve().parents[2]/'work/your-trip-v72'
OUT.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()=='3293c583569de637fa69396bd3280a483e3d1cef42a3645c7c31e03349406bc7'
pdf=pdfplumber.open(SOURCE)
bars=[]
for pi,page in enumerate(pdf.pages):
    staffys=sorted(set(round(r['top'],3) for r in page.rects if 31<r['height']<33 and r['width']<1))
    for sy in staffys:
        bounds=[28.346]+sorted(set(round(r['x1'],3) for r in page.rects if abs(r['top']-sy)<.01 and 31<r['height']<33 and r['width']<1))
        labels=sorted([c for c in page.chars if 'TimesNewRoman-0-50' in c['fontname'] and abs(c['size']-6)<.01 and abs(c['top']-(sy-6.674))<.12],key=lambda c:c['x0'])
        numbers=[]
        for c in labels:
            if numbers and c['x0']<numbers[-1]['end']+1: numbers[-1]['text']+=c['text'];numbers[-1]['end']=c['x1']
            else: numbers.append(dict(text=c['text'],end=c['x1']))
        assert len(numbers)==len(bounds)-1,(pi,sy,numbers,bounds)
        for label,(lo,hi) in zip(numbers,zip(bounds,bounds[1:])):
            num=int(label['text'])
            chars=[c for c in page.chars if 'Arial' in c['fontname'] and abs(c['size']-7)<.01 and sy-4<c['top']<sy+33 and lo<c['x0']<hi]
            words=[]
            for string in range(1,7):
                cs=sorted([c for c in chars if abs(c['top']-(sy+(string-1)*6.3779526-2.94))<.13],key=lambda c:c['x0'])
                for c in cs:
                    if words and words[-1]['s']==string and c['x0']<words[-1]['x1']+.7:
                        words[-1]['text']+=c['text'];words[-1]['x1']=c['x1']
                    else: words.append(dict(s=string,text=c['text'],x0=c['x0'],x1=c['x1']))
            stems=[l for l in page.lines if l['width']<.01 and l['height']>6 and lo+1<l['x0']<hi-1 and sy+4<l['top']<sy+43 and sy+31<l['bottom']<sy+53]
            events=[]
            for st in sorted(stems,key=lambda l:l['x0']):
                x=st['x0']; ns=[w for w in words if abs((w['x0']+w['x1'])/2-x)<1.5]
                beams=[r for r in page.rects if 1.5<r['height']<2.5 and r['width']>2 and r['x0']-.5<=x<=r['x1']+.5 and abs(r['bottom']-st['bottom'])<6]
                curves=[c for c in page.curves if lo<c['x0']<hi and sy-5<c['top']<sy+60]
                flags=[c for c in curves if abs(c['x0']-x)<.6 and abs(c['bottom']-st['bottom'])<1 and c['height']>8]
                dots=[c for c in curves if c['width']<2 and c['height']<2 and x+1<c['x0']<x+8 and sy+30<c['top']<sy+53]
                d=12/(2**len(beams)) if beams else 6 if flags else 24 if st['top']>sy+40 else 12
                if dots: d*=1.5
                events.append(dict(x=round(x,3),notes=[f"{w['s']}:{w['text']}" for w in ns],d=d,beams=len(beams),flag=len(flags),dots=len(dots),stem=[round(st['top']-sy,2),round(st['bottom']-sy,2)]))
            curves=[c for c in page.curves if lo<c['x0']<hi and sy-5<c['top']<sy+60]
            for c in curves:
                if abs(c['width']-4.575)<.015 and abs(c['height']-12.722)<.015:
                    events.append(dict(x=round(c['x0']+c['width']/2,3),notes=[],d=12,rest=True))
                elif abs(c['width']-4.201)<.015 and abs(c['height']-7.228)<.015:
                    events.append(dict(x=round(c['x0']+c['width']/2,3),notes=[],d=6,rest=True))
            events.sort(key=lambda e:e['x'])
            for i,e in enumerate(events):
                if not e['notes'] and not e.get('rest'):
                    assert i and events[i-1]['notes'],(num,e)
                    e['notes']=[n.split(':')[0]+':('+n.split(':')[1].strip('()')+')' for n in events[i-1]['notes']]
                    e['implicitTie']=True
            if not events and words:
                events=[dict(x=round((words[0]['x0']+words[0]['x1'])/2,3),notes=[f"{w['s']}:{w['text']}" for w in words],d=48,whole=True)]
            unattached=[w for w in words if not any(abs((w['x0']+w['x1'])/2-e['x'])<1.5 for e in events)]
            bars.append(dict(bar=num,page=pi+1,box=[lo,sy,hi,sy+60],events=events,unattached=unattached,curves=[dict(x=round(c['x0'],3),y=round(c['top']-sy,3),w=round(c['width'],3),h=round(c['height'],3),path=c['path']) for c in curves]))
(OUT/'scan.json').write_text(json.dumps(bars,ensure_ascii=False,indent=2),encoding='utf-8')
for b in bars:
    print(b['bar'], ' '.join('/'.join(e['notes'])+'@'+str(e['d']) for e in b['events']), 'SUM',sum(e['d'] for e in b['events']), 'unattached',b['unattached'])
print('bars',len(bars),'hash',hashlib.sha256(SOURCE.read_bytes()).hexdigest())
