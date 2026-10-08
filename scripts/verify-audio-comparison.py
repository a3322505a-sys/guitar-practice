from pathlib import Path
import sys, json, hashlib
project=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(project/'.local/audio-deps'))
import numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.signal import resample_poly
folder=project/'output/audio-comparison'
report=[]; playlist=[]
for name in ['single-notes','open-chords','arpeggio']:
    entries=[]
    for bank in ['old-electric','new-nylon']:
        path=folder/f'{name}-{bank}.wav'
        wave,rate=sf.read(path)
        meter=pyln.Meter(rate)
        loudness=float(meter.integrated_loudness(wave)); peak=float(20*np.log10(max(np.max(np.abs(wave)),1e-9)))
        entries.append((bank,wave,rate,loudness,peak))
    target=min([-22.0]+[e[3]-1-e[4] for e in entries])
    for bank,wave,rate,level,peak in entries:
        gain=10**((target-level)/20)
        matched=wave*gain
        destination=folder/f'{name}-{bank}-matched.wav'
        sf.write(destination,matched,rate,subtype='PCM_16')
        check,_=sf.read(destination)
        measured=float(pyln.Meter(rate).integrated_loudness(check))
        assert abs(measured-target)<.02
        assert np.max(np.abs(check))<.9
        # Oversampled peak is measured independently of the App's PCM clip limit.
        true_peak=float(20*np.log10(np.max(np.abs(resample_poly(check,4,1)))))
        report.append({'example':name,'bank':bank,'seconds':len(check)/rate,'lufs':measured,'target_lufs':target,'sample_peak_dbfs':float(20*np.log10(np.max(np.abs(check)))),'oversampled_peak_dbfs':true_peak,'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()})
        playlist.extend([check,np.zeros(int(rate*.8))])
sf.write(folder/'preview-old-then-new.wav',np.concatenate(playlist),44100,subtype='PCM_16')
(folder/'loudness-verification.json').write_text(json.dumps({'meter':'pyloudnorm 0.2.0 / BS.1770','order':'single old,new; chord old,new; arpeggio old,new','results':report},indent=2),encoding='utf-8')
(folder/'README.md').write_text('''# 音色试听对照

先听 preview-old-then-new.wav，顺序为：单音旧→新、开放和弦旧→新、分解旧→新；每段之间 0.8 秒空白。
旧：原 FreePats FSBS clean 电吉他；新：FreePats Spanish classical guitar 尼龙弦实录。
三个练习使用完全相同事件、90 BPM、同一 App Pcm 渲染器，无节拍声；不是另外合成的示意音。
独立 Python 检查并匹配 BS.1770 综合响度。精确 LUFS、峰值及 SHA-256 见 loudness-verification.json。
带 -matched.wav 的文件用于听感比较；未带 matched 的 WAV 是 App 渲染器的 RMS 校准中间输出，保留用于复核。
本机未进行主观试听，不宣称实测听感更好。App 已接入新素材；手机扬声器、耳机和音画同步待用户实际试听。

来源与许可：https://github.com/freepats/spanish-classical-guitar (CC0-1.0)。
响度测量实现：https://github.com/csteinmetz1/pyloudnorm 。
''',encoding='utf-8')
print(json.dumps(report,indent=2))
