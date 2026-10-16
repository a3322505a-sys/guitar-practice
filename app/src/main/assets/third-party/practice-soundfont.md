# Practice SoundFont (v92)

`../sonivox.sf2` overrides the Android alphaTab 1.8.4 AAR's bundled asset of the
same name. Its existing native loader, synthesizer and timeline are unchanged.

- Base: `net.alphatab:alphaTab:1.8.4`, `assets/sonivox.sf2`, SHA-256
  `4978cf37e7164206f2d22dc4fdf4020ffe040787210bf781e31bdc79961f59ef`.
  Apache-2.0; see `Apache-2.0.txt` and `Sonivox-NOTICE.txt`. Original copyright
  metadata is retained and modifications are identified in the SF2 comment.
- Bank 0, zero-based program 24 (GM #25): 22 existing `guitar-nylon/*.pcm`
  samples. FreePats Spanish classical guitar, Roberto, 2019-06-18, source commit
  `6f4eb1b092acc88f5448cea1a0001bd07b971af8`, CC0-1.0. Full license, source and
  sample hashes: `../guitar-nylon/LICENSE.txt`, `README.md`, `manifest.json`.
  <https://freepats.zenvoid.org/Guitar/acoustic-guitar.html>
  Same 360 ms RMS/peak calibration, nearest-root mapping and 1.5 ms attack /
  90 ms release as the legacy bank; no loops or added effects. All MIDI keys
  0–127 are mapped, including transposed notes outside the sampled range.
- Bank 128, program 0, key 33: original generated 45 ms damped wood click,
  dedicated to CC0-1.0. This is alphaTab 1.8.4's actual metronome mapping,
  shared by its count-in and main playback. Other percussion keys are retained.
- All other GM presets, including steel/electric guitars 25–31, retain their
  original samples and mappings. There is no instrument remapping of GP files.

Reproduce with Python's standard library:

```text
python scripts/build-practice-soundfont.py path/to/alphaTab-1.8.4.aar
python scripts/build-practice-soundfont.py path/to/alphaTab-1.8.4.aar --check
```

The PCM assets are inputs to this offline conversion only; the application does
not create the legacy PCM player. The final sound on a phone requires listening.
