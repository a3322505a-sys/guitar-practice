"""Independent pitch/barre/teaching validation for the 1.1 contract."""
from .protocol import check, q
import re

QUALITIES = {
    'MAJOR': [0,4,7], 'MINOR': [0,3,7], 'MAJOR_SEVENTH': [0,4,7,11],
    'ADD_NINE': [0,2,4,7], 'SUS_TWO': [0,2,7], 'SUS_FOUR_NO_FIVE': [0,5],
    'MINOR_SEVENTH_NO_FIVE': [0,3,10], 'POWER': [0,7],
}

def validate_teaching(score, practices):
    used = False
    tracks = {t['id']: t for t in score['tracks']}
    voicings = {v['id']: v for v in score['voicings']}
    for v in voicings.values():
        path = '/score/voicings/' + v['id']
        used |= bool(v.get('barres') or v.get('quality'))
        for b in v.get('barres', []):
            lo, hi = b['toString'], b['fromString']
            check(1 <= lo < hi <= 6, 'TEACHING', path, 'Barre strings must be ordered 6 to 1')
            check(all(v['frets'][s-1] >= b['fret'] and (v['frets'][s-1] != b['fret'] or v['fingers'][s-1] == b['finger']) for s in range(lo,hi+1)), 'TEACHING', path, 'Barre crosses muted/open/lower string or conflicts with finger')
            check(all(v['frets'][s-1] == b['fret'] and v['fingers'][s-1] == b['finger'] for s in (lo,hi)), 'TEACHING', path, 'Barre endpoints must be explicitly fretted')
        if v.get('quality'):
            t = tracks[v['trackId']]; root = v.get('teaching',{}).get('root')
            check(type(root) is int and 0 <= root < 12, 'TEACHING', path, 'Explicit chord quality needs a sounding root')
            actual = {(t['openStringMidi'][i] + t.get('capo',0) + f) % 12 for i,f in enumerate(v['frets']) if f >= 0}
            check(actual == {(root+i) % 12 for i in QUALITIES[v['quality']]}, 'TEACHING', path, 'Sounding pitches conflict with chord quality')
            name=v.get('teaching',{}).get('soundingName','')
            m=re.fullmatch(r'([A-G][#♯b♭]?)([^/]*)(?:/([A-G][#♯b♭]?))?',name)
            if m:
                def pitch(text): return (dict(C=0,D=2,E=4,F=5,G=7,A=9,B=11)[text[0]]+sum(1 if c in '#♯' else -1 for c in text[1:]))%12
                suffix={'MAJOR':'','MINOR':'m','MAJOR_SEVENTH':'maj7','ADD_NINE':'add9','SUS_TWO':'sus2','SUS_FOUR_NO_FIVE':'sus4(no5)','MINOR_SEVENTH_NO_FIVE':'m7(no5)','POWER':'5'}[v['quality']]
                check(pitch(m[1])==root and m[2]==suffix,'TEACHING',path,'Sounding name conflicts with root/quality')
                if m[3]:
                    lowest=min(t['openStringMidi'][i]+t.get('capo',0)+f for i,f in enumerate(v['frets']) if f>=0)%12
                    check(lowest==pitch(m[3]),'TEACHING',path,'Slash bass conflicts with lowest sound')
    starts={}; at=0
    for m in score['measures']: starts[m['id']]=at; at+=q(m['actualDuration'])
    for h in score['harmonies']:
        if 'validUntil' in h:
            used=True
            check(starts[h['measureId']]+q(h['onset']) < q(h['validUntil']) <= at,'TEACHING','/score/harmonies/'+h['id'],'Invalid harmony interval')
    ids = {p['id'] for p in practices}
    events = {e['id'] for voice in score['voices'] for e in voice['events']}
    measures = {m['id'] for m in score['measures']}
    for p in practices:
        lesson = p.get('lesson')
        if lesson is None: continue
        used = True; path = '/practices/' + p['id'] + '/lesson'
        check(set(lesson.get('transitionIds',[])) <= ids - {p['id']}, 'TEACHING', path, 'Missing transition practice')
        lead = set(lesson.get('leadTrackIds',[]))
        check(not lead or lead < set(p['mixTrackIds']), 'TEACHING', path, 'Fading requires independent lead and accompaniment')
        chain = lesson.get('chainStarts',[])
        check(len(chain) == len(set(chain)) and set(chain) <= measures, 'TEACHING', path, 'Invalid chain boundaries')
        for a in lesson.get('actions',[]):
            check(a['eventId'] in events and a['text'].strip(), 'TEACHING', path, 'Action has no real event/instruction')
            check('voicingId' not in a or a['voicingId'] in voicings, 'TEACHING', path, 'Unknown action voicing')
            if a['kind']=='retained':
                f=voicings.get(a.get('fromVoicingId')); t=voicings.get(a.get('voicingId'))
                check(f is not None and t is not None and f['trackId']==t['trackId'] and any(f['frets'][i]>0 and f['fingers'][i] in (1,2,3,4) and f['frets'][i]==t['frets'][i] and f['fingers'][i]==t['fingers'][i] for i in range(6)),'TEACHING',path,'Retained fingers need two exact compatible voicings')
            if a['kind'] == 'barre': check(voicings.get(a.get('voicingId'),{}).get('barres'), 'TEACHING', path, 'Unknown barre')
    return used
