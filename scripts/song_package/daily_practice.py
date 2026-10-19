"""The authored 36-bar daily suite. All timings are quarter-note rational values."""
from fractions import Fraction as F
from .native_teaching import PracticePack,key,note,practice_key,movement
from .teaching_data import CHORDS
from .protocol import rational as rat,q

LIBRARY='local.teaching-native'

def event(notes,duration=F(1,2),harmony=None,**art):
    d=dict(notes=notes,duration=duration,articulation=art)
    if harmony:d['harmony']=harmony
    return d

def finger(string,fret):return note(string,fret,0 if fret==0 else fret if fret<=4 else {5:1,7:3,8:4}[fret])

def picked(chord,string):
    f=CHORDS[chord][2][6-string];n=CHORDS[chord][3][6-string]
    assert f>=0,(chord,string,'cannot pluck an X string')
    return note(string,f,n)

def daily():
    b=PracticePack('local.daily-practice','每日练习 · 同调渐进小套曲',82)
    cards=[b.measures('DP-%02d'%i,n) for i,n in enumerate((8,8,8,12),1)]
    for ms in cards:
        for i,m in enumerate(ms):next(v for v in b.score['measures'] if v['id']==m)['label']=str(i+1)
    for name,root,fret in [('F8',5,8),('G8',7,10),('A8',9,12),('E8',4,7)]:
        CHORDS[name]=('OCTAVE',root,[-1,fret,-1,fret+2,-1,-1],[0,1,0,3,0,0])
    modules=['FOUNDATIONS','CHORDS','SCALES','APPLICATION']
    titles=['指尖唤醒：三音模进与跨弦问答','黄金四和弦：低音连线与色彩加花','五声会说话：和弦音靶心 Solo','J-Rock 引擎：闷音推进与副歌爆发']
    goals=['六个八分加一个四分组成每小节；末小节五弦三品 C3 全音符。仅不妨碍下一音时暂留前指。',
        '前三和弦低音 C3→B2→A2 级进下行，再以 F3 的 Fmaj7 收束；G/B 必须释放二弦一品。',
        '独立伴奏 C–G/B–Am7–Fmaj7 ×2；重拍落 E、D、C。和弦音靶心包含 F，不受五声骨架限制。',
        '四小节收紧、四小节展开、四小节八度收尾；掌闷保留音高，X 弦不发声，八度中间四弦消音。']
    warmups=['FB-3','CHANGE-C-Am','S-Am-box5-pent','JR-POWER']
    motifs=[[(5,3),(4,0),(4,2)],[(4,0),(4,2),(4,3)],[(4,2),(4,3),(3,0)],[(3,0),(3,2),(3,0)],
        [(3,2),(3,0),(4,3)],[(3,0),(4,3),(4,2)],[(4,3),(4,2),(4,0)]]
    harmony=['C','G/B','Am7','Fmaj7']*2
    for version in ('basic','advanced'):
        lead=b.track(version+'-lead','领奏／主练 · '+('基础版' if version=='basic' else '进阶版'))
        back=b.track(version+'-backing','独立伴奏 · '+version)
        routes=[]
        r=[]
        for bar,motif in enumerate(motifs):
            ns=motif*2+[motif[0]]
            if version=='advanced' and bar in (2,3,4):
                ns=[(4,5) if p==(3,0) else p for p in ns]
            r.extend(event(note(s,f,1 if f==5 else f),F(1,2) if i<6 else F(1),accent=i==0) for i,(s,f) in enumerate(ns))
        r.append(event(note(5,3,3),F(4)));routes.append(r)
        r=[]
        for bar in range(8):
            base=['C','G/B','Am7','Fmaj7','C','G-open','Am','Fmaj7'][bar]
            if version=='basic':
                r.extend(event(base,d,pick=p) for d,p in [(1,'down'),(1,'down'),(F(1,2),'down'),(F(1,2),'up'),(F(1,2),'down'),(F(1,2),'up')])
            else:
                names=[base]*8
                if bar in (4,5,6):names[4:6]=[{4:'Cadd9',5:'Gsus4',6:'Asus2'}[bar]]*2
                strings=([4] if base=='Fmaj7' else [6] if base=='G-open' else [5])+[3,2,3,1,3,2,3]
                # Colour tones must actually be heard on beat 3, rather than merely relabelled.
                if bar==4:strings[4]=2
                if bar==5:strings[4]=2
                if bar==6:strings[4]=2
                r.extend(event(picked(n,s),harmony=n,pick='down' if i%2==0 else 'up') for i,(n,s) in enumerate(zip(names,strings)))
        routes.append(r)
        open_melodies=[[(1,0),(2,3),(2,1),(3,2),(3,0),(3,2),(2,1),(1,0)],
            [(2,3),(2,1),(3,2),(3,0),(3,2),(2,1),(2,3),(2,3)],
            [(2,1),(3,2),(3,0),(4,2),(3,0),(3,2),(2,1),(2,1)],
            [(3,2),(2,1),(1,0),(2,1),(3,2),(3,0),(3,2),(3,2)]]
        box=[[(2,5),(2,8),(2,5),(3,7),(3,5),(3,7),(2,5),(2,5)],
            [(3,7),(2,5),(2,8),(1,5),(2,8),(2,5),(3,7),(3,7)],
            [(1,8),(1,5),(2,8),(2,5),(3,7),(3,5),(3,7),(2,5)],
            [(3,5),(3,7),(2,5),(2,8),(1,5),(2,8),(1,5),(1,5)]]
        r=[]
        for bar in range(8):
            positions=open_melodies[bar%4] if version=='basic' or bar<4 else box[bar-4]
            if version=='advanced' and bar==3:
                r.extend([event(note(3,2,3),1),event(note(3,5,3),1,pick='none'),event(note(3,5,1),1),event(note(2,5,1),1)])
            else:r.extend(event(finger(s,f)) for s,f in positions)
        routes.append(r)
        r=[]
        for name in ('F5','G5','A5','C5'):
            count=8 if version=='advanced' else 4
            r.extend(event(name,F(4,count),palmMute=i<(5 if count==8 else 2),pick='down') for i in range(count))
        for name in ('Fmaj7','G6','Em7','Am'):
            r.extend(event(name,d,palmMute=False,pick=p) for d,p in [(1,'down'),(1,'down'),(F(1,2),'down'),(F(1,2),'up'),(F(1,2),'down'),(F(1,2),'up')])
        r.extend(event(n,d,palmMute=False,short=i==8) for i,(n,d) in enumerate([
            ('F8',2),('G8',2),('A8',2),('E8',2),('F8',1),('G8',1),('A8',2),('E8',1),('A8',2),(None,1)]))
        routes.append(r)
        for i,(ms,route) in enumerate(zip(cards,routes)):
            eid='DP-%02d-%s'%(i+1,version)
            events=b.events(eid,ms,lead,route)
            actions=[dict(eventId=events[0]['id'],kind='position',text=goals[i])]
            if i==1:
                for bar in range(1,4):
                    text,shared=movement(harmony[bar-1],harmony[bar])
                    actions.append(dict(eventId=next(e['id'] for e in events if e['measureId']==ms[bar]),kind='retained' if shared else 'position',text=text,fromVoicingId=b.voicing(harmony[bar-1],lead),voicingId=b.voicing(harmony[bar],lead)))
            if i==2:
                be=b.events(eid+'-back',ms,back,[event(n,4) for n in harmony])
                starts={m['id']:j*4 for j,m in enumerate(b.score['measures'])}
                for j,name in enumerate(harmony):
                    b.score['harmonies'].append(dict(id=eid+'-context-'+str(j),measureId=ms[j],onset=rat(0),name=name,voicingId=b.voicing(name,lead),validUntil=rat(starts[ms[j]]+4)))
                if version=='advanced':
                    es=[e for e in events if e['measureId']==ms[3]]
                    b.score['relations'].append(dict(id=eid+'-slide',kind='slide',fromNoteId=es[0]['notes'][0]['id'],toNoteId=es[1]['notes'][0]['id']))
                    es=[e for e in events if e['measureId']==ms[4]]
                    for a,c,kind in [(0,1,'hammer'),(1,2,'pull')]:
                        es[c]['articulation']['pick']='none'
                        b.score['relations'].append(dict(id=eid+'-'+kind,kind=kind,fromNoteId=es[a]['notes'][0]['id'],toNoteId=es[c]['notes'][0]['id']))
                    actions.append(dict(eventId=next(e['id'] for e in events if e['measureId']==ms[3]),kind='position',text='无名指由三弦二品 A3 滑到五品 C4（Fmaj7 五音）；下一拍重整为五品食指 Box 1。'))
            p=b.practice(eid,'DP-%02d · %s'%(i+1,titles[i]),ms,lead,modules[i],key('A',True) if i in (2,3) else key('C'),goals[i],
                mix=[lead,back] if i==2 else [lead],lead=[lead] if i==2 else [],warmups=[practice_key(LIBRARY,warmups[i])],actions=actions,
                variant='DP-%02d-basic'%(i+1) if version=='advanced' else None,version='进阶版' if version=='advanced' else '基础版',boundaries=ms[::2])
            p['suggestedTempo']=110 if i==3 and version=='advanced' else [82,80,85,85][i]
            if i==3:
                join=b.practice('DP-04-join-'+version,'DP-04 · 第4→5小节衔接',ms[3:5],lead,modules[i],key('A',True),'第四小节末接第五小节首，解除 PM，从 C5 转 Fmaj7。',actions=[dict(eventId=next(e['id'] for e in events if e['measureId']==ms[3]),kind='position',text='前五个八分掌闷，后三个开放；保持拍点切入 Fmaj7。' if version=='advanced' else '保持慢速四分拍点，切入开放和弦。')],hidden=True)
                p['lesson']['transitionIds']=[join['id']]
        # Whole-suite playback has one explicit fixed tempo; no card tempo is inserted into the score.
        all_ms=sum(cards,[])
        b.practice('SUITE-'+version,'四卡连练 · 36小节',all_ms,lead,'APPLICATION',key('C'),'统一 82 BPM，可手动调速；依次问答、和弦、Solo 与 J-Rock，末尾切音收束。',mix=[lead,back],lead=[lead],warmups=[],actions=[dict(eventId='DP-01-'+version+'-e-0',kind='position',text='选统一速度后连练；每张卡片仍可单独练习。')],variant='SUITE-basic' if version=='advanced' else None,version='进阶连练' if version=='advanced' else '基础连练',boundaries=[c[0] for c in cards])
    return b
