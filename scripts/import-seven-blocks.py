"""Import the reviewed desktop GP, keeping all seven blocks and unfolding its repeats.

Uses the shared GPIF graph/Fraction rhythm reader. The pinned source is deliberate:
new GP versions require another content review, not an expanded metadata allowlist.
"""
import argparse
import re
from pathlib import Path
from song_assets.common import fingerprint, gp_graph, rhythm_ticks, positions, write_report

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = 'b9f5e0ae40d99035978764cf6c192b44e392877803d5b4e1958b14a1fe438081'
NUMBERS = [1,2,3,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20,21,22,24,25,26,27,29,30,31,32,33,35,36,37,38,40,41,42,43,44]
NAMES = ['交替拨弦热身','换弦练习','手指组合练习','节奏型练习','跳弦练习','时值切换练习','音阶练习']
GOALS = ['交替拨弦保持均匀，换品时保持拍点。','换弦时保持上下拨交替与每拍四音。','手指配对保持独立，按原拍号完成整组。','保留实音与 X 死音的区别，准确切换细分。','跨过未弹的弦，保持拨弦方向与节拍。','切换直拍与三连音时保持每拍总时长。','保持七音分组与三连音上下行的连续性。']


def read(source):
    assert fingerprint(source) == SOURCE_SHA, 'Unreviewed GP: source fingerprint differs'
    root, tables, masters, tracks, order = gp_graph(source)
    assert order == ['0'] and len(tracks) == 1 and len(masters) == 38
    track = tracks['0']
    assert track.findtext('./Staves/Staff/Properties/Property[@name="Tuning"]/Pitches') == '40 45 50 55 59 64'
    assert track.findtext('./Staves/Staff/Properties/Property[@name="CapoFret"]/Fret') == '0'
    bars = []
    block = 0
    for number, master in zip(NUMBERS, masters):
        section = master.findtext('Section/Text')
        if section:
            block = int(re.search(r'Block (\d)', section).group(1))
        meter, denominator = map(int, master.findtext('Time').split('/'))
        assert denominator == 4 and meter in (3,4,5,6)
        bar = tables['Bars'][master.findtext('Bars')]
        voices = [v for v in bar.findtext('Voices').split() if v != '-1']
        assert len(voices) == 1
        at = 0
        events = []
        tuplets = []
        group = []
        for ref in tables['Voices'][voices[0]].findtext('Beats').split():
            beat = tables['Beats'][ref]
            duration, triplet = rhythm_ticks(tables['Rhythms'][beat.find('Rhythm').get('ref')])
            duration *= 2  # 24 ticks/quarter: exact 32nds and 32nd triplets.
            assert duration.denominator == 1
            duration = int(duration)
            notes = []
            dead = []
            for note_ref in (beat.findtext('Notes') or '').split():
                note = tables['Notes'][note_ref]
                assert {c.tag for c in note} <= {'InstrumentArticulation','Properties'}
                props = note.find('Properties')
                string = 6 - int(props.findtext('Property[@name="String"]/String'))
                fret = int(props.findtext('Property[@name="Fret"]/Fret'))
                midi = int(props.findtext('Property[@name="Midi"]/Number'))
                if props.find('Property[@name="Muted"]') is not None:
                    dead.append(string)
                else:
                    assert midi == {6:40,5:45,4:50,3:55,2:59,1:64}[string] + fret
                    notes.append([string,fret])
            pick = (beat.findtext('./Properties/Property[@name="PickStroke"]/Direction') or 'Default').upper()
            assert pick in ('DEFAULT','DOWN','UP')
            dynamic = beat.findtext('Dynamic')
            assert dynamic in ('F','MF')
            events.append(dict(tick=at,duration=duration,positions=notes,deadStrings=dead,pick=pick,triplet=triplet,dynamic=dynamic))
            if triplet:
                group.append((at,duration))
                if len(group) == 3:
                    assert all(d == duration for _,d in group)
                    assert [t for t,_ in group] == [group[0][0]+i*duration for i in range(3)]
                    tuplets.append([group[0][0],duration]);group = []
            else:
                assert not group
            at += duration
        assert at == meter*24 and not group
        repeat = master.find('Repeat')
        bars.append(dict(number=number,block=block,meter=meter,events=events,tuplets=tuplets,
                         repeatStart=repeat is not None and repeat.get('start')=='true',
                         repeatEnd=repeat is not None and repeat.get('end')=='true',
                         repeatCount=int(repeat.get('count')) if repeat is not None else 0,
                         repeatCountVerified=number not in (14,17,19)))
    return bars


def route(bars):
    result = []
    start = None
    for i, bar in enumerate(bars):
        if bar['repeatStart']:
            assert start is None
            start = i
        result.append(bar['number'])
        if bar['repeatEnd']:
            assert start is not None and bar['repeatCount'] in (2,4,8)
            result += [b['number'] for b in bars[start:i+1]]*(bar['repeatCount']-1)
            start = None
    assert start is None
    return result


def generate(source, output):
    assert not output.exists() or not any(output.iterdir()), 'Use a new empty candidate directory'
    bars = read(source)
    output.mkdir(parents=True,exist_ok=True)
    by_number = {b['number']:b for b in bars}
    code = ['package com.a3322505a.guitarlearning.practice','',
            'import com.a3322505a.guitarlearning.music.Position','',
            '// Generated from the reviewed desktop GP by scripts/import-seven-blocks.py.',
            '// Written bars stay separate; only the performance route unfolds repeats.',
            'object SevenBlocks {',
            '    private data class WrittenBar(val number: Int,val meter: Int,val events: List<Event>,val tuplets: List<Tuplet>)',
            '    private val written = listOf('+','.join(f'written{i}()' for i in range(1,8))+').flatten().associateBy { it.number }']
    previous=0
    for b in bars:
        if b['block']!=previous:
            if previous: code.append('    )')
            code.append(f"    private fun written{b['block']}() = listOf(")
            previous=b['block']
        code.append(f"        WrittenBar({b['number']},{b['meter']},listOf(")
        for e in b['events']:
            extra = f",pick=Pick.{e['pick']}"
            if e['deadStrings']: extra += ',deadStrings=setOf('+','.join(map(str,e['deadStrings']))+')'
            if e['dynamic']=='MF': extra += ',velocity=.8f'
            code.append(f"            Event({e['tick']},{e['duration']},{positions(e['positions'])}{extra}),")
        ts = ','.join(f'Tuplet({t},{u})' for t,u in b['tuplets'])
        code.append(f'        ),listOf({ts})),')
    code += ['    )','',
             '    private fun block(id: Int, title: String, goal: String, numbers: List<Int>, pending: String = ""): Phrase {',
             '        var tick=0',
             '        val events=mutableListOf<Event>()',
             '        val tuplets=mutableListOf<Tuplet>()',
             '        numbers.forEach { number ->',
             '            val bar=written.getValue(number)',
             '            events += bar.events.map { it.copy(tick=it.tick+tick) }',
             '            tuplets += bar.tuplets.map { it.copy(tick=it.tick+tick) }',
             '            tick += bar.meter*24',
             '        }',
             '        val source=numbers.distinct()',
             '        val explanation="完整保留 Block $id 原谱 ${source.first()}–${source.last()} 的 ${source.size} 小节。已按 GP 反复展开为 ${numbers.size} 小节；谱面主号为原谱号，括号为展开顺序。已知反复4遍，原38小节8遍。$pending 每轮完整经过展开谱一次；示范播放一轮。60 BPM仅为练习速度，原谱未标固定速度。X表示死音；保留上下拨、混合拍号、三十二分与三连音。标准调弦，无变调夹。"',
             '        return Phrase(id="SB-$id",title="七个Block · $id $title",category="技巧练习",',
             '            passage="Block $id · 原谱 ${source.first()}–${source.last()} 小节",source="七个Block-吉他技巧练习-校对版-v1.gp · 吉他 TAB",',
             '            sourceBpm=60,tempoLabel="练习参考",difficulty="由慢到快",technique=title,conditions="标准调弦 · 无变调夹",',
             '            goal=goal,suggestedBpm=40,use="七个Block",',
             '            sequence=Sequence(events,tick,explanation,beatsPerBar=written.getValue(numbers.first()).meter,tuplets=tuplets,',
             '                originalBar=numbers.first()-1,barMeters=numbers.map { written.getValue(it).meter },ticksPerQuarter=24,sourceBars=numbers))',
             '    }','', '    val phrases = listOf(']
    routes = []
    for i, name in enumerate(NAMES,1):
        selected = [b for b in bars if b['block']==i]
        numbers = route(selected)
        pending = {2:'原11–14反复次数待核，暂沿用GP播放2遍。',3:'原16–17、18–19反复次数待核，暂沿用GP各2遍。'}.get(i,'')
        code.append(f'        block({i},"{name}","{GOALS[i-1]}",listOf('+','.join(map(str,numbers))+f'),"{pending}"),')
        routes.append(dict(id=f'SB-{i}',writtenBars=[b['number'] for b in selected],performanceBars=numbers,
                           length=sum(by_number[n]['meter']*24 for n in numbers)))
    code += ['    )','}','']
    target = output/'SevenBlocks.kt'
    target.write_text('\n'.join(code),encoding='utf8')
    write_report(output/'source-practice.json',dict(sourceSha256=SOURCE_SHA,ticksPerQuarter=24,writtenBars=bars,practice=routes))
    report = dict(sourceSha256=SOURCE_SHA,writtenBars=len(bars),events=sum(len(b['events']) for b in bars),
                  noteheads=sum(len(e['positions'])+len(e['deadStrings']) for b in bars for e in b['events']),
                  deadNoteheads=sum(len(e['deadStrings']) for b in bars for e in b['events']),
                  rests=sum(not e['positions'] and not e['deadStrings'] for b in bars for e in b['events']),
                  tripletEvents=sum(e['triplet'] for b in bars for e in b['events']),skippedEvents=0,
                  routes=routes,kotlinSha256=fingerprint(target),
                  diagnostics=[dict(category='review-needed',field='repeatCount',bars=[14,17,19],value='GP default 2, not verified original count'),
                               dict(category='review-needed',field='sourceCoverage',value='GP contains 38 bars; original 4,15,23,28,34,39 are absent; no invented rests'),
                               dict(category='approximation',field='tempo',value='60 BPM practice reference, original unspecified'),
                               dict(category='approximation',field='sound',value='Existing App samples; GP RSE/effects/pickup automation not imported'),
                               dict(category='supported',field='rhythm',value='3/4,4/4,5/4,6/4; straight 32nds and 16th/32nd triplets; exact 24 ticks/quarter')])
    write_report(output/'report.json',report)
    return report


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    report=generate(args.source,args.output)
    print({k:report[k] for k in ('writtenBars','events','noteheads','rests','tripletEvents','skippedEvents')})
