"""Portable positive and rejection samples; no private score required."""
import copy,importlib.util,json,tempfile,unittest,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from song_assets.common import ImportBlocked,window_offsets,validate_window,fingerprint
from song_assets.gp import audit_gp
from song_assets.pdf import validate_reviewed_pdf

ROOT=Path(__file__).resolve().parents[1]

class SongAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.path=Path(self.temp.name)
        self.data=json.loads((ROOT/'scripts/score-data/your-trip.json').read_text(encoding='utf8'))

    def test_actual_reviewed_pdf_windows_and_source_padding(self):
        result=validate_reviewed_pdf(self.data)
        self.assertEqual([r['value'] for r in result[:4]],[[0,444],[18,768],[0,360],[24,816]])
        self.assertEqual(window_offsets([4,2,4],80,{'bar':81,'tick':12},{'bar':82,'tick':0}),(60,72))
        with self.assertRaisesRegex(ImportBlocked,'outside source meter'):
            window_offsets([4,2,4],80,{'bar':81,'tick':30},{'bar':82,'tick':0})

    def test_reject_unknown_pdf_technique_wrong_boundary_and_sustain(self):
        self.data['bars'][0]['events'][0]['trill']=True
        with self.assertRaisesRegex(ImportBlocked,'PDF page 1 bar 1 tick 0'):validate_reviewed_pdf(self.data)
        self.data['bars'][0]['events'][0].pop('trill');self.data['parts'][0]['end']=11
        with self.assertRaisesRegex(ImportBlocked,'bisects'):validate_reviewed_pdf(self.data)
        with self.assertRaisesRegex(ImportBlocked,'sustain crosses'):
            validate_window([dict(tick=0,duration=12,ringEndTick=48)],[],0,24,'ring sample')
        with self.assertRaisesRegex(ImportBlocked,'source/target separated'):
            validate_window([dict(tick=0,duration=12),dict(tick=12,duration=12)],[(0,12,1,'HAMMER')],12,24,'legato sample')

    def gp(self):
        return ET.fromstring('''<GPIF><MasterTrack><Tracks>1</Tracks></MasterTrack><Tracks><Track id="1"><Name>Guitar</Name><Staves><Staff><Properties><Property name="Tuning"><Pitches>40 45 50 55 59 64</Pitches></Property></Properties></Staff></Staves></Track></Tracks><MasterBars><MasterBar><Time>4/4</Time><Bars>0</Bars></MasterBar></MasterBars><Bars><Bar id="0"><Voices>0</Voices></Bar></Bars><Voices><Voice id="0"><Beats>0</Beats></Voice></Voices><Beats><Beat id="0"><Dynamic>F</Dynamic><Rhythm ref="0"/><Notes>0</Notes></Beat></Beats><Notes><Note id="0"><Properties><Property name="String"><String>0</String></Property><Property name="Fret"><Fret>3</Fret></Property><Property name="Midi"><Number>67</Number></Property></Properties></Note></Notes><Rhythms><Rhythm id="0"><NoteValue>Whole</NoteValue></Rhythm></Rhythms></GPIF>''')

    def audit(self,root):
        dest=self.path/'sample.gp'
        with zipfile.ZipFile(dest,'w') as z:z.writestr('Content/score.gpif',ET.tostring(root))
        return audit_gp(dest,'kafka-v72',[1])

    def test_gp_success_and_unknown_music_field_enum_or_nested_attribute(self):
        self.assertTrue(self.audit(self.gp()))
        root=self.gp();ET.SubElement(root.find('Notes/Note/Properties'),'Property',name='Trill')
        with self.assertRaisesRegex(ImportBlocked,'track 1 master bar 0.*note#0'):self.audit(root)
        root=self.gp();root.find('Beats/Beat/Dynamic').text='fffffff'
        with self.assertRaisesRegex(ImportBlocked,'unknown enum'):self.audit(root)
        root=self.gp();root.find('Beats/Beat/Rhythm').set('newTiming','true')
        with self.assertRaisesRegex(ImportBlocked,'unknown attributes'):self.audit(root)

    def test_gp_reject_repeat_tempo_ramp_multivoice_tuning_change(self):
        mutations=[(lambda r:ET.SubElement(r.find('MasterBars/MasterBar'),'Repeat',count='2'),'Repeat'),
                   (lambda r:setattr(r.find('Bars/Bar/Voices'),'text','0 1'),'Voices'),
                   (lambda r:setattr(r.find('Tracks/Track/Staves/Staff/Properties/Property/Pitches'),'text','38 45 50 55 59 64'),'Tuning')]
        for mutate,message in mutations:
            root=self.gp();mutate(root)
            with self.subTest(message=message),self.assertRaisesRegex(ImportBlocked,message):self.audit(root)
        root=self.gp();autos=ET.SubElement(root.find('MasterTrack'),'Automations')
        autos.append(ET.fromstring('<Automation><Type>Tempo</Type><Linear>true</Linear><Position>0.5</Position><Bar>0</Bar><Value>120 2</Value></Automation>'))
        with self.assertRaisesRegex(ImportBlocked,'ramp or intra-bar'):self.audit(root)

if __name__=='__main__': unittest.main()
