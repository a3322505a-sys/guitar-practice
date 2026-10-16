from pathlib import Path
import sys, urllib.request, json, concurrent.futures, hashlib
project=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(project/'.local/audio-deps'))
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly, butter, sosfilt
roots=[40,43,45,47,48,50,52,53,55,57,59,60,62,64,65,67,69,71,72,74,76,77]
names=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']
sha='6f4eb1b092acc88f5448cea1a0001bd07b971af8'
base='https://raw.githubusercontent.com/freepats/spanish-classical-guitar/'+sha+'/'
cache=project/'.local/nylon-source'; cache.mkdir(parents=True,exist_ok=True)
out=project/'assets/soundfont-source/guitar-nylon'; out.mkdir(parents=True,exist_ok=True)
# Preserve original electric bank for development-side comparison, not runtime switching.
def fetch(path):
    p=cache/Path(path).name
    if not p.exists():
        data=urllib.request.urlopen(base+urllib.parse.quote(path,safe='/'),timeout=30).read()
        p.write_bytes(data)
    return p
paths=['samples/'+names[r%12]+str(r//12-1)+'.flac' for r in roots]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: downloaded=list(pool.map(fetch,paths))
license_path=fetch('LICENSE'); (out/'LICENSE.txt').write_bytes(license_path.read_bytes())
fetch('README.md'); fetch('SpanishClassicalGuitar-20190618.sfz')
manifest=[]
for root,p in zip(roots,downloaded):
    wave,rate=sf.read(p,dtype='float64',always_2d=True)
    wave=wave.mean(axis=1)
    # Trim only leading acquisition silence; retain the real pluck transient.
    threshold=max(np.max(np.abs(wave))*.003, .00002)
    onset=np.flatnonzero(np.abs(wave)>threshold)
    start=max(0,int(onset[0])-int(rate*.002)) if len(onset) else 0
    wave=wave[start:start+int(rate*8)]
    wave=sosfilt(butter(2,25,fs=rate,btype='highpass',output='sos'),wave)
    from math import gcd
    d=gcd(int(rate),22050)
    wave=resample_poly(wave,22050//d,int(rate)//d)
    # End taper avoids a truncated resource discontinuity. Runtime handles actual note release.
    tail=min(len(wave),1102); wave[-tail:]*=np.linspace(1,0,tail)
    data=np.round(np.clip(wave,-.98,.98)*32767).astype('<i2').tobytes()
    target=out/f'{root}.pcm'; target.write_bytes(data)
    manifest.append({'midi':root,'source':p.name,'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'rate':int(rate),'trim_start':start,'seconds':len(wave)/22050,'pcm_bytes':len(data),'pcm_sha256':hashlib.sha256(data).hexdigest()})
(out/'manifest.json').write_text(json.dumps({'source_commit':sha,'license':'CC0-1.0','sample_rate':22050,'samples':manifest},indent=2),encoding='utf-8')
(out/'README.md').write_text('''# Nylon-string guitar samples

FreePats Spanish classical guitar, version 2019-06-18, recorded by Roberto in 2008.
https://github.com/freepats/spanish-classical-guitar/tree/'''+sha+'''
https://freepats.zenvoid.org/Guitar/acoustic-guitar.html

CC0 1.0; full publisher license in LICENSE.txt. No mandatory attribution; origin retained.
22 key centers covering MIDI 40-77. Derived mono PCM16 LE at 22050 Hz; acquisition
silence trimmed with 2 ms pre-onset retained, 25 Hz high-pass, at most 8 seconds,
50 ms asset-end taper. No distortion or reverb. Exact source and derived hashes
are recorded in manifest.json; original FLAC files retained in .local/nylon-source.
Runtime calibrates first-360-ms energy with peak headroom; 1.5 ms attack and
90 ms bounded release preserve the pluck and avoid hard cuts. Practice timing
and voice compensation are independent of the bank. Device listening is pending.
''',encoding='utf-8')
print(json.dumps({'samples':len(manifest),'pcm_bytes':sum(m['pcm_bytes'] for m in manifest),'source_commit':sha}))
