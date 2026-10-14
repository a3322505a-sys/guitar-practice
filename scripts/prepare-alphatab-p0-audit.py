"""Extract independent written-rhythm and pitch facts from the private P0 GPIF."""
from pathlib import Path
from fractions import Fraction
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile

project = Path(__file__).resolve().parents[1]
manifest = json.loads((project / 'work/evidence/desktop-gp-manifest.json').read_text('utf8'))
fixture = next(f for f in manifest['fixtures'] if f['asset'] == 'complex.gp')
source = Path(fixture['path'])
assert hashlib.sha256(source.read_bytes()).hexdigest() == fixture['sha256']
with zipfile.ZipFile(source) as archive:
    name = next(n for n in archive.namelist() if n.lower().endswith('score.gpif'))
    root = ET.fromstring(archive.read(name))
tables = {name: {n.attrib['id']: n for n in root.findall(f'./{name}/*')}
          for name in ['Bars', 'Voices', 'Beats', 'Notes', 'Rhythms']}
tracks = root.findall('./Tracks/Track')
track_index = fixture['selectedTrack']
offset = sum(len(t.findall('./Staves/Staff') or [t]) for t in tracks[:track_index])
staves = tracks[track_index].findall('./Staves/Staff')
assert len(staves) == 1, 'This independent probe supports the selected single-staff fixture'
staff = staves[0]
tuning = [int(p) for p in staff.findtext("./Properties/Property[@name='Tuning']/Pitches").split()]
capo = int(staff.findtext("./Properties/Property[@name='CapoFret']/Fret", '0'))
partial = int(staff.findtext("./Properties/Property[@name='PartialCapoFret']/Fret", '0'))
assert partial == 0, 'Partial capo needs a separate reference rule'
values = {'Whole': 1, 'Half': 2, 'Quarter': 4, 'Eighth': 8, '16th': 16, '32nd': 32,
          '64th': 64, '128th': 128, '256th': 256}
bars, beats, notes = [], [], []
for bar_index, master in enumerate(root.findall('./MasterBars/MasterBar')):
    numerator, denominator = map(int, master.findtext('Time').split('/'))
    bars.append({'bar': bar_index, 'numerator': numerator, 'denominator': denominator,
                 'tripletFeel': master.findtext('TripletFeel', 'NoTripletFeel')})
    bar = tables['Bars'][master.findtext('Bars').split()[offset]]
    for voice_index, voice_id in enumerate(bar.findtext('Voices').split()):
        if voice_id == '-1':
            continue
        voice = tables['Voices'][voice_id]
        for beat_index, beat_id in enumerate(voice.findtext('Beats').split()):
            beat = tables['Beats'][beat_id]
            rhythm = tables['Rhythms'][beat.find('Rhythm').attrib['ref']]
            assert rhythm.find('SecondaryTuplet') is None, 'Secondary tuplets need a separate reference rule'
            duration = values[rhythm.findtext('NoteValue')]
            dot = rhythm.find('AugmentationDot')
            dots = int(dot.attrib['count']) if dot is not None else 0
            tuplet = rhythm.find('PrimaryTuplet')
            num, den = (int(tuplet.attrib['num']), int(tuplet.attrib['den'])) if tuplet is not None else (1, 1)
            tick_duration = Fraction(960 * 4, duration) * sum(Fraction(1, 2 ** d) for d in range(dots + 1)) * Fraction(den, num)
            note_ids = beat.findtext('Notes', '').split()
            position = {'bar': bar_index, 'voice': voice_index, 'beat': beat_index}
            grace = beat.findtext('GraceNotes', 'None')
            assert grace in ['None', 'OnBeat', 'BeforeBeat'], 'Unknown grace timing'
            beats.append({**position, 'duration': duration, 'dots': dots, 'grace': grace, 'tupletNum': num,
                          'tupletDen': den, 'writtenTicks': float(tick_duration), 'rest': not note_ids})
            for note_id in note_ids:
                note = tables['Notes'][note_id]
                properties = note.find('Properties')
                string = properties.findtext("Property[@name='String']/String")
                fret = properties.findtext("Property[@name='Fret']/Fret")
                assert string is not None and fret is not None, 'Selected guitar note lacks string/fret'
                assert properties.find("Property[@name='Harmonic']") is None, 'Harmonics need a separate pitch reference'
                string, fret = int(string), int(fret)
                assert 0 <= string < len(tuning) and fret >= 0
                tie = note.find('Tie')
                notes.append({**position, 'string': string + 1, 'fret': fret,
                              'pitch': tuning[string] + capo + fret,
                              'tieDestination': tie is not None and tie.attrib.get('destination') == 'true',
                              'letRing': note.find('LetRing') is not None})
output = {'sourceSha256': fixture['sha256'], 'path': str(source), 'track': track_index,
          'tuningLowToHigh': tuning, 'capo': capo, 'bars': bars, 'beats': beats, 'notes': notes,
          'boundary': 'Written musical facts only; does not prove device output or repeat/swing playback'}
destination = project / 'work/evidence/complex-gpif-musical-reference.json'
destination.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf8')
print(json.dumps({'bars': len(bars), 'beats': len(beats), 'notes': len(notes),
                  'rests': sum(b['rest'] for b in beats), 'dottedBeats': sum(b['dots'] > 0 for b in beats),
                  'tuplets': sum(b['tupletNum'] != b['tupletDen'] for b in beats),
                  'graceBeats': sum(b['grace'] != 'None' for b in beats),
                  'tieDestinations': sum(n['tieDestination'] for n in notes)}, indent=2))
