"""Validate reviewed PDF facts before padding, tie merging or Kotlin output."""
import re
from .common import ImportBlocked,window_offsets,validate_window

def validate_reviewed_pdf(data):
    def fields(value,allowed,loc):
        unknown=set(value)-set(allowed)
        if unknown: raise ImportBlocked(loc,'fields',sorted(unknown),'unknown reviewed transcription fields')
    fields(data,'source sha256 bpm capo meter tickPerQuarter bars parts reviewNotes'.split(),'PDF transcription')
    if data['meter']!='4/4' or data['tickPerQuarter']!=12:
        raise ImportBlocked('PDF transcription','meter/ticks',[data['meter'],data['tickPerQuarter']],'this reviewed-PDF adapter uses 4/4 and 12 ticks/quarter')
    if not 0<=data['capo']<=12 or not 30<=data['bpm']<=180:
        raise ImportBlocked('PDF transcription','capo/bpm',[data['capo'],data['bpm']],'outside current runtime bounds')
    bars={b['number']:b for b in data['bars']}
    if len(bars)!=len(data['bars']): raise ImportBlocked('PDF transcription','bars','duplicate','ambiguous source position')
    for bar in data['bars']:
        loc='PDF page '+str(bar['page'])+' bar '+str(bar['number'])
        fields(bar,['number','page','events'],loc)
        at=0
        for e in bar['events']:
            here=loc+' tick '+str(e['tick'])
            fields(e,'tick duration notes dead tied pdfX legato triplet grace bend ringToBarEnd strum velocity vibrato'.split(),here)
            if type(e['tick']) is not int or type(e['duration']) is not int:
                raise ImportBlocked(here,'tick/duration',[e['tick'],e['duration']],'integer written tick required')
            if e['tick']!=at or e['duration']<=0: raise ImportBlocked(here,'duration',e['duration'],'source events must cover bar exactly')
            if e.get('triplet'):
                if e['duration']!=2 or e['tick']%2: raise ImportBlocked(here,'triplet',e['duration'],'this PDF adapter supports explicit sixteenth triplets')
            elif e['duration']%3 or e['tick']%3:
                raise ImportBlocked(here,'subdivision',e['duration'],'unsupported written subdivision without explicit triplet')
            at+=e['duration']
            strings=[s for s,f in e['notes']]+e['dead']
            if len(strings)!=len(set(strings)) or any(s not in range(1,7) for s in strings) or any(f not in range(25) for s,f in e['notes']): raise ImportBlocked(here,'notes/dead',strings,'invalid guitar position')
            if not set(e['tied'])<=set(s for s,f in e['notes']): raise ImportBlocked(here,'tied',e['tied'],'tie for absent pitched string')
            for link in e.get('legato',[]):
                fields(link,['source','string','kind'],here)
                if link['kind'] not in ('HAMMER','PULL','SLIDE') or link['string'] not in strings: raise ImportBlocked(here,'legato',link,'unknown technique or absent string')
            if 'grace' in e:
                g=e['grace'];fields(g,['string','fret','kind'],here)
                if g['kind'] not in ('HAMMER','PULL','SLIDE') or g['string'] not in strings or not 0<=g['fret']<=24: raise ImportBlocked(here,'grace',g,'unknown technique or invalid note')
            if 'bend' in e:
                b=e['bend'];fields(b,['string','points'],here)
                if b['string'] not in strings or any(not 0<=t<=e['duration'] or not 0<=v<=4 for t,v in b['points']) or any(a[0]>c[0] for a,c in zip(b['points'],b['points'][1:])): raise ImportBlocked(here,'bend',b,'invalid source curve')
            if 'velocity' in e and not .1<=e['velocity']<=1.2: raise ImportBlocked(here,'velocity',e['velocity'],'outside current runtime bounds')
        if at!=48: raise ImportBlocked(loc,'duration',at,'incomplete source bar')
        for e in bar['events']:
            if e.get('triplet'):
                start=e['tick']//6*6;members=[x for x in bar['events'] if start<=x['tick']<start+6]
                if [x['tick'] for x in members]!=[start,start+2,start+4] or any(not x.get('triplet') or x['duration']!=2 for x in members):
                    raise ImportBlocked(loc,'triplet',start,'three explicit equal source members required')
    if len({p['id'] for p in data['parts']})!=len(data['parts']): raise ImportBlocked('PDF parts','id','duplicate','stable practice ID required')
    diagnostics=[]
    for p in data['parts']:
        fields(p,'id title first last start end use goal technique bpm description'.split(),p['id'])
        if not re.fullmatch(r'[A-Z][A-Z0-9]*-[A-Z][A-Z0-9-]*',p['id']) or p['id'].split('-',1)[1].lower() in ('class','fun','object','val','when','return'):
            raise ImportBlocked('PDF parts','id',p['id'],'unsafe generated Kotlin function identifier')
        first,last=p['first'],p['last'];a,b=window_offsets([4]*(last-first+1),first,{'bar':first,'tick':p['start']},{'bar':last,'tick':p['end']})
        events=[];links=[]
        for number in range(first,last+1):
            if number not in bars: raise ImportBlocked(p['id'],'bar',number,'missing reviewed source bar')
            for e in bars[number]['events']:
                at=(number-first)*48+e['tick'];events.append(dict(tick=at,duration=e['duration']))
                if a<=at<b:
                    for link in e.get('legato',[]): links.append([(number-first)*48+link['source'],at,link['string'],link['kind']])
                    if e.get('ringToBarEnd'): events[-1]['ringEndTick']=(e['ringToBarEnd']-first+1)*48
                    if e['tied'] and at==a: raise ImportBlocked(p['id']+f' event@{at}','tie',e['tied'],'selection starts on tie destination')
        validate_window(events,links,a,b,p['id'])
        diagnostics.append(dict(category='supported',location=p['id'],field='half-open-window',value=[a,b],reason='written source events retained; outside window becomes practicePadding'))
    return diagnostics
