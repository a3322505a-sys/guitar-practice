"""Native V3.1 recipes. No source migration or automatic teaching enrichment."""
import json,re
from fractions import Fraction as F
from song_package.native_teaching import PracticePack,ROOT,key,note,movement,practice_key
from song_package.teaching_data import CHORDS,BARRES,OPEN
from song_package.teaching import QUALITIES
LIBRARY='local.teaching-native'
def slug(s):return re.sub('[^A-Za-z0-9._-]','-',s)
ROUTES={'C':[(5,3),(4,0),(4,2),(4,3),(3,0),(3,2),(2,0),(2,1)],
 'Am':[(5,0),(5,2),(5,3),(4,0),(4,2),(4,3),(3,0),(3,2)],
 'G':[(6,3),(5,0),(5,2),(5,3),(4,0),(4,2),(4,4),(3,0)],
 'Em':[(6,0),(6,2),(6,3),(5,0),(5,2),(5,3),(4,0),(4,2)],
 'D':[(4,0),(4,2),(4,4),(3,0),(3,2),(2,0),(2,2),(2,3)],
 'Bm':[(5,2),(5,4),(4,0),(4,2),(4,4),(3,0),(3,2),(2,0)]}
BOX_ROWS={'C':{5:[[5,7,8],[5,6,8],[4,5,7],[5,7],[5,7,8],[5,7,8]],7:[[7,8,10],[8,10],[7,9,10],[7,9,10],[7,8,10],[7,8,10]]},
 'G':{5:[[5,7,8],[5,7,8],[4,5,7],[4,5,7],[5,7],[5,7,8]],7:[[7,8,10],[7,8,10],[7,9],[7,9,10],[7,9,10],[7,8,10]]},
 'D':{5:[[5,7],[5,7,8],[4,6,7],[4,5,7],[4,5,7],[5,7]],7:[[7,9,10],[7,8,10],[6,7,9],[7,9],[7,9,10],[7,9,10]]}}
FAMILY={'Am':'C','Em':'G','Bm':'D'}
CONTEXTS={n:key(n[:-1] if n.endswith('m') else n,n.endswith('m')) for n in ROUTES}
def line(ps,base=1):return [note(s,f,0 if f==0 else f-base+1) for s,f in ps]
def loop(r):
    r=r+r[-2:0:-1];return r+[None]*((-len(r))%8)
def kotlin_data():
    folder=ROOT/'app/src/main/java/com/a3322505a/guitarlearning/music'
    li=lambda xs:'listOf('+','.join('null' if x==-1 else str(x) for x in xs)+')'
    head=['// Generated from native V3.1 recipes.','package com.a3322505a.guitarlearning.music']
    lines=head+['internal object TeachingVoicings {','    val all = listOf(']
    for n,(quality,root,frets,fingers) in CHORDS.items():
        third=next((str((root+i)%12) for i in QUALITIES[quality] if i in (3,4)),'null')
        bars='listOf('+','.join('Barre({finger},{fret},{fromString},{toString})'.format(**b) for b in BARRES.get(n,[]))+')'
        group='强力和弦' if quality=='POWER' else 'C / Am 家族' if n in ('C','Am','F','Fmaj7','Cadd9','Dm') else 'G / Em 家族' if n in ('G','Em','E','Gsus2','D/F♯') else 'D / Bm 家族'
        lines.append(f'        Chord("{n}",{root},{third},{li(frets)},{li(fingers)},group="{group}",source="V3.1 原生教材",barres={bars},quality=ChordQuality.{quality}),')
    (folder/'TeachingVoicings.kt').write_text('\n'.join(lines+['    )','}'])+'\n',encoding='utf8')
    lines=head+['internal object NativeScalePatterns {','    val byKey = mapOf(']
    for n in ROUTES:
        enum={'C':'C_MAJOR','Am':'A_MINOR','G':'G_MAJOR','Em':'E_MINOR','D':'D_MAJOR','Bm':'B_MINOR'}[n]
        root=CHORDS[n][1];pcs={(root+i)%12 for i in ([0,2,3,5,7,8,10] if n.endswith('m') else [0,2,4,5,7,9,11])}
        rows=[[f for f in range(5) if (op+f)%12 in pcs] for op in OPEN]
        lines.append('        Tonality.'+enum+' to listOf(')
        for base,rr in [(1,rows)]+list(BOX_ROWS[FAMILY.get(n,n)].items()):
            title='开放把位' if base==1 else str(base)+'品封闭 Box'
            fingers='mapOf('+','.join(f'Position({s+1},{f}) to {0 if f==0 else f-(1 if base==1 else min(base,min(row)))+1}' for s,row in enumerate(rr) for f in row)+')'
            lines.append(f'            Pattern("{title}","",listOf('+','.join(li(row) for row in rr)+f'),{fingers}),')
        lines.append('        ),')
    (folder/'NativeScalePatterns.kt').write_text('\n'.join(lines+['    )','}'])+'\n',encoding='utf8')
def library():
    b=PracticePack(LIBRARY,'V3.1 原生标准练习库',60)
    b.phrase('FB-1','FB-1 · 单弦交替拨弦',line([(3,f) for f in (0,1,2,3,4,3,2,0)]*2),'FOUNDATIONS',key('C'),'下上交替，1–4 指逐音落在品丝后；空弦 O 不落指。',F(1,2))
    b.phrase('FB-2','FB-2 · 爬格子保留指专项',line([(s,f) for s in (4,3) for f in (1,2,3,4,4,3,2,1)]),'FOUNDATIONS',key('C'),
        '仅本专项上行暂留前指；下行按4→3→2→1逐指释放，换弦全部放松。',F(1,2),actions=[dict(eventId='FB-2-e-'+str(i),kind='position',text=t) for i,t in [(0,'食指落四弦一品，上行暂留前指'),(4,'下行逐指释放'),(8,'换三弦，四弦全部放松')]])
    r=line(ROUTES['C']);motif=[r[i] for i in (0,1,2,1,2,3,2,3,4,3,4,5,4,5,6,5,6,7,6,5,4,5,4,3,4,3,2,1,2,1,0)]+[None]
    b.phrase('FB-3','FB-3 · 三音模进与问答句',motif,'FOUNDATIONS',key('C'),'三音动机逐级模进；问句上行，答句回 C，句尾留呼吸。',F(1,2))
    for n in CHORDS:
        b.phrase(slug('CHORD-'+n),'配法 · '+n,[n]*4,'CHORDS',CONTEXTS.get(n,key('C')),'低音起逐弦检查 O/X；按标号落指，仅按图示横按范围发力。',warmups=['FB-2'])
    for a,c in [('C','Am'),('C','Fmaj7'),('C','G'),('G','Em'),('Em','Am'),('G','D'),('D','Bm'),('Bm','A'),('C','F'),('D','A'),('G','Gsus2'),('Bm','Bm7(no5)')]:
        id=slug('CHANGE-'+a+'-'+c);b.track('guitar');actions=[]
        for i,x,y in [(4,a,c),(8,c,a),(12,a,c)]:
            text,shared=movement(x,y);actions.append(dict(eventId=id+'-e-'+str(i),kind='retained' if shared else 'position',text=text,fromVoicingId=b.voicing(x,'guitar'),voicingId=b.voicing(y,'guitar')))
        b.phrase(id,a+' ↔ '+c,[a]*4+[c]*4+[a]*4+[c]*4,'CHORDS',CONTEXTS.get(a,key('D')),actions[0]['text'],actions=actions,warmups=['FB-2'])
        subs={'F':'Fmaj7','Bm':'Bm7(no5)','G':'Gsus2'};aa=subs.get(a,a);cc=subs.get(c,c)
        if (aa,cc)!=(a,c):
            text,_=movement(aa,cc)
            b.phrase(id+'-easy',aa+' ↔ '+cc,[aa]*4+[cc]*4+[aa]*4+[cc]*4,'CHORDS',CONTEXTS.get(a,key('D')),text,variant=id,version='新手简化版',warmups=['FB-2'])
    for n,route in ROUTES.items():
        context=CONTEXTS[n];root=CHORDS[n][1];pent={0,3,5,7,10} if n.endswith('m') else {0,2,4,7,9}
        b.phrase('S-'+n+'-open',n+' · 开放单八度',loop(line(route)),'SCALES',context,'主音起止，八度往返；读调内音级，对照参照和弦音。',F(1,2),warmups=['FB-3'])
        b.phrase('S-'+n+'-pent',n+' · 开放五声骨架',loop(line([(s,f) for s,f in route if (OPEN[s-1]+f-root)%12 in pent])),'SCALES',context,'五声骨干音；突出主音，对照和弦音靶心。',F(1,2),warmups=['FB-3'])
        for base,rows in BOX_ROWS[FAMILY.get(n,n)].items():
            ps=[(s,f) for s in range(6,0,-1) for f in rows[s-1]]
            for pent_only in (False,True):
                selected=[p for p in ps if not pent_only or (OPEN[p[0]-1]+p[1]-root)%12 in pent]
                b.phrase('S-'+n+'-box'+str(base)+('-pent' if pent_only else ''),n+' · '+str(base)+'品封闭 Box'+(' 五声' if pent_only else ' 七声'),loop([note(st,f,f-min(base,min(rows[st-1]))+1) for st,f in selected]),'SCALES',context,
                    '以 '+str(base)+' 品为中心；'+ '；'.join(str(st+1)+'弦'+str(min(row))+'品用1指局部调整' for st,row in enumerate(rows) if min(row)<base)+'；按各弦标号落指，交替拨弦。',F(1,2),warmups=['FB-1'])
    for id,title,route,k,goal in [('ADD9','C→Cadd9→C',['C','Cadd9','C','C'],key('C'),'1指从二弦一品抬起，4指落二弦三品；3指五弦三品与2指四弦二品保留，再还原。'),
        ('BASS','G→D/F♯→Em',['G']*4+['D/F♯']*4+['Em']*4,key('G'),'低音 G→F♯→E；D/F♯只弹6、4、3弦。'),
        ('JR-POWER','J-Rock · 强力和弦',['E5',None,'E5',None,'G5',None,'A5',None],key('E',True),'只弹图示低音弦；休止放松左手，闷住全部弦。')]:
        b.phrase(id,title,route,'APPLICATION',k,goal,warmups=['FB-1'])
    b.phrase('CAPO-2','Capo 2 · 按法与实音',['C']*4+['G']*4+['Am']*4+['Fmaj7']*4,'APPLICATION',key('D'),'按法 C–G–Am–Fmaj7，实音 D–A–Bm–Gmaj7；弦品相对变调夹。',track='capo2',capo=2,warmups=['CHANGE-C-Am'])
    riff=[note(6,f,{0:0,3:2,5:4}[f]) for f in (0,0,3,0,0,3,5,3)]
    b.phrase('JR-PM','J-Rock · 掌闷音驱动',[dict(notes=n,duration=F(1,2),articulation=dict(palmMute=i<6)) for i,n in enumerate(riff)]*2,'APPLICATION',key('E',True),'前六个八分音轻掌闷，句尾开放；五品用4指伸展，回第一把位。',warmups=['FB-1'])
    pent=line([(6,5),(6,8),(5,5),(5,7),(4,5),(4,7),(3,5),(3,7)],5)
    b.phrase('JR-RIFF','J-Rock · A小调五声 Riff',pent+pent[::-1],'APPLICATION',key('A',True),'五品食指基准，5/7/8品用1/3/4指；两小节问答，突出 A。',F(1,2),warmups=['S-Am-box5-pent'])
    return b
def demo(pid,title,k,chords,easy,melody,warmup,capo=0):
    b=PracticePack(pid,title,80);ms=b.measures('song',8)
    for version,names in [('original',chords),('beginner',easy)]:
        lead=b.track(version+'-lead','独立领奏 · '+version,capo);back=b.track(version+'-backing','独立伴奏 · '+version,capo);route=[]
        for bar in range(8):
            ns=melody[bar%len(melody)];ns=[ns[0],ns[2],ns[4],ns[-1]] if version=='beginner' else ns
            route += [dict(notes=n,duration=F(4,len(ns)),articulation=dict(palmMute=pid.endswith('rock') and bar<4)) for n in ns]
        b.events(version,ms,lead,route);b.events(version+'-back',ms,back,[n for n in names for _ in range(2)],F(2),palm=pid.endswith('rock'))
        # The lead staff carries the backing harmony as its explicit teaching context.
        for h in list(b.score['harmonies']):
            v=next(v for v in b.score['voicings'] if v['id']==h['voicingId'])
            if v['trackId']==back:b.score['harmonies'].append(dict(h,id=h['id']+'-lead',voicingId=b.voicing(h['name'],lead)))
        for section,selected in [('A',ms[:4]),('SOLO',ms[4:])]:
            actions=[dict(eventId=next(e['id'] for v in b.score['voices'] if v['trackId']==lead for e in v['events'] if e['measureId']==selected[0]),kind='position',text=('Capo 2，按法与实音相差全音；' if capo else '')+('五品食指定位，5/7/8品用1/3/4指。' if pid.endswith('rock') else '第一把位按标号落指，句尾主音稍放松。'))]
            start=0 if section=='A' else 4
            for i in range(start+1,start+4):
                text,shared=movement(names[i-1],names[i]);actions.append(dict(eventId=version+'-e-'+str(i*(4 if version=='beginner' else 8)),kind='retained' if shared else 'position',text=text,fromVoicingId=b.voicing(names[i-1],back),voicingId=b.voicing(names[i],back)))
            b.practice(section+'-'+version,'A段 · 主题' if section=='A' else 'B段 · Solo问答',selected,lead,'APPLICATION',k,'两小节一短句；渐隐只作用于领奏；倒序接龙按两小节边界扩展。',mix=[lead,back],lead=[lead],warmups=[practice_key(LIBRARY,warmup)],actions=actions,variant=section+'-original' if version=='beginner' else None,version='新手简化版' if version=='beginner' else '原谱版',boundaries=[selected[0],selected[2]])
        join=b.practice('JOIN-'+version,'A段末一小节 → B段首一小节',ms[3:5],lead,'APPLICATION',k,'推荐第4小节接第5小节，完整两小节循环；均为自然断句。',mix=[lead,back],lead=[lead],warmups=[practice_key(LIBRARY,warmup)],actions=[dict(eventId=version+'-e-'+str(12 if version=='beginner' else 24),kind='strum',text='A段末保持拍点，下一小节首拍进入 Solo。')],boundaries=[ms[3],ms[4]],hidden=True)
        next(p for p in b.practices if p['id']=='A-'+version)['lesson']['transitionIds']=[join['id']]
    return b
def main():
    kotlin_data();melody=[line([(3,0),(3,2),(2,1),(2,3),(1,0),(2,3),(2,1),(2,1)]),line([(4,3),(3,2),(2,1),(1,0),(1,1),(1,0),(2,1),(2,1)])]
    rock=[line([(6,5),(6,5),(6,8),(5,5),(5,7),(4,5),(5,7),(6,5)],5),line([(3,5),(3,7),(2,5),(2,8),(1,5),(2,8),(3,7),(3,5)],5)]
    packs=[library(),demo('local.demo-c','晨光问答 · C/Am 满配示范',key('C'),['C','Am','F','G','C','F','G','C'],['C','Am','Fmaj7','Gsus2','C','Fmaj7','Gsus2','C'],melody,'CHANGE-C-Fmaj7'),
        demo('local.demo-capo','远行 · Capo 2 实音示范',key('D'),['C','G','Am','Fmaj7']*2,['C','Gsus2','Am','Fmaj7']*2,melody,'CAPO-2',2),
        demo('local.demo-rock','夜行驱动 · J-Rock 五声示范',key('A',True),['A5','C5','D5','E5']*2,['A5','C5','D5','E5']*2,rock,'JR-RIFF')]
    result=[b.finish(n+'.gpractice') for b,n in zip(packs,['teaching-native','demo-c','demo-capo','demo-rock'])]
    out=ROOT/'docs/validation/v106-native-data.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
