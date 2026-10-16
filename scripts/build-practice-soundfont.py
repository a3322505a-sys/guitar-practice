"""Adapt the existing CC0 nylon PCM for alphaTab 1.8.4; retain its GM fallback.

python scripts/build-practice-soundfont.py path/to/alphaTab-1.8.4.aar [--check]
Only asset production: no application playback, timing or MIDI changes.
"""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
PCM_ASSETS = ROOT / "assets/soundfont-source"
BASE_SHA256 = "4978cf37e7164206f2d22dc4fdf4020ffe040787210bf781e31bdc79961f59ef"


def chunks(data, start, end):
    while start < end:
        tag, size = struct.unpack_from("<4sI", data, start)
        at = start + 8
        yield tag, data[at:at + size]
        start = at + size + size % 2


def chunk(tag, data):
    return struct.pack("<4sI", tag, len(data)) + data + b"\0" * (len(data) % 2)


def records(fmt, data):
    return list(struct.iter_unpack("<" + fmt, data))


def packed(fmt, rows):
    return b"".join(struct.pack("<" + fmt, *r) for r in rows)


def pcm_bytes(values):
    pcm = array("h", (round(max(-1, min(1, v)) * 32767) for v in values))
    if sys.byteorder != "little":
        pcm.byteswap()
    return pcm.tobytes()


def build(base):
    assert hashlib.sha256(base).hexdigest() == BASE_SHA256, "Use the pinned 1.8.4 font"
    lists = {data[:4]: dict(chunks(data, 4, len(data)))
             for tag, data in chunks(base, 12, len(base)) if tag == b"LIST"}
    info, sdta, pdta = (lists[k] for k in (b"INFO", b"sdta", b"pdta"))
    phdr = records("20sHHHIII", pdta[b"phdr"])
    pbag, pgen = records("HH", pdta[b"pbag"]), records("HH", pdta[b"pgen"])
    inst = records("20sH", pdta[b"inst"])
    ibag, igen = records("HH", pdta[b"ibag"]), records("HH", pdta[b"igen"])
    instruments = [(name, [igen[ibag[z][0]:ibag[z + 1][0]]
                          for z in range(start, inst[i + 1][1])])
                   for i, (name, start) in enumerate(inst[:-1])]
    shdr = records("20sIIIIIBbHH", pdta[b"shdr"])[:-1]
    samples = bytearray(sdta[b"smpl"])

    def sample(name, pcm, root):
        start = len(samples) // 2
        samples.extend(pcm)
        end = len(samples) // 2
        samples.extend(b"\0" * 92)  # SF2's 46-sample interpolation guard.
        index = len(shdr)
        shdr.append((name.encode(), start, end, start, end, 22050, root, 0, 0, 1))
        return index

    # Same head-RMS/peak calibration and nearest-root tie policy as the old bank.
    manifest = json.loads((PCM_ASSETS / "guitar-nylon/manifest.json").read_text())
    roots = [s["midi"] for s in manifest["samples"]]
    zones = []
    for i, source in enumerate(manifest["samples"]):
        root = source["midi"]
        raw = (PCM_ASSETS / f"guitar-nylon/{root}.pcm").read_bytes()
        assert hashlib.sha256(raw).hexdigest() == source["pcm_sha256"]
        wave = [v[0] / 32768 for v in struct.iter_unpack("<h", raw)]
        head = wave[:22050 * 360 // 1000]
        rms = math.sqrt(sum(v * v for v in head) / len(head))
        gain = min(.23 / max(rms, .00001), .88 / max(max(map(abs, wave)), .00001))
        sid = sample(f"Nylon {root}", pcm_bytes(v * gain for v in wave), root)
        low = 0 if i == 0 else (roots[i - 1] + root) // 2 + 1
        high = 127 if i == len(roots) - 1 else (root + roots[i + 1]) // 2
        # No loop, natural recorded decay; 1.5 ms attack, 90 ms note-off release.
        zones.append([(43, low | high << 8), (34, round(1200 * math.log2(.0015)) & 65535),
                      (38, round(1200 * math.log2(.09)) & 65535), (53, sid)])
    nylon_id = len(instruments)
    instruments.append((b"CC0 Nylon PCM", zones))
    guitar = next(i for i, p in enumerate(phdr) if p[1:3] == (24, 0))
    assert pgen[pbag[phdr[guitar][3]][0]] == (41, 24)
    pgen[pbag[phdr[guitar][3]][0]] = (41, nylon_id)

    # Original, deterministic 45 ms damped wooden click. Rounded onset, no noise,
    # no square wave, no long tail. Both engine count-in and main use this key.
    click = []
    for i in range(round(22050 * .045)):
        t = i / 22050
        envelope = (1 - math.exp(-t / .0012)) * math.exp(-t / .008)
        tail = min(1, (.045 - t) / .005)
        click.append(envelope * tail * (math.sin(2 * math.pi * 1050 * t)
                     + .32 * math.sin(2 * math.pi * 1750 * t)
                     + .12 * math.sin(2 * math.pi * 2450 * t)))
    peak = max(map(abs, click))
    sid = sample("Soft wood click", pcm_bytes(v * .88 / peak for v in click), 33)
    standard = next(p for p in phdr if p[1:3] == (0, 128))
    drum_id = dict(pgen[pbag[standard[3]][0]:pbag[standard[3] + 1][0]])[41]
    drum_zones = instruments[drum_id][1]
    matches = [i for i, z in enumerate(drum_zones) if dict(z).get(43) == (33 | 33 << 8)]
    assert len(matches) == 1, "Expected isolated metronome key in the pinned font"
    drum_zones[matches[0]] = [(43, 33 | 33 << 8), (34, -12000 & 65535),
                             (38, -1200 & 65535), (58, 33), (53, sid)]

    new_inst, new_ibag, new_igen = [], [], []
    for name, zones in instruments:
        new_inst.append((name, len(new_ibag)))
        for zone in zones:
            new_ibag.append((len(new_igen), 0))
            new_igen.extend(zone)
    new_inst.append((b"EOI", len(new_ibag)))
    new_ibag.append((len(new_igen), 0))
    new_igen.append((0, 0))
    shdr.append((b"EOS", len(samples) // 2, len(samples) // 2, 0, 0, 22050, 0, 0, 0, 1))
    pdta.update({b"pgen": packed("HH", pgen), b"inst": packed("20sH", new_inst),
                 b"ibag": packed("HH", new_ibag), b"igen": packed("HH", new_igen),
                 b"shdr": packed("20sIIIIIBbHH", shdr)})
    sdta[b"smpl"] = bytes(samples)
    info[b"INAM"] = b"Practice Nylon + Soft Click\0"
    info[b"ICMT"] = info[b"ICMT"].rstrip(b"\0") + b"\r\nModified: CC0 nylon program 24; original soft click bank 128 program 0 key 33.\0"
    body = b"sfbk" + b"".join(chunk(b"LIST", kind + b"".join(chunk(k, v) for k, v in data.items()))
                                for kind, data in ((b"INFO", info), (b"sdta", sdta), (b"pdta", pdta)))
    return chunk(b"RIFF", body)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aar", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    with zipfile.ZipFile(args.aar) as archive:
        result = build(archive.read("assets/sonivox.sf2"))
    target = ASSETS / "sonivox.sf2"
    if args.check:
        assert result == target.read_bytes(), "SoundFont is not reproducible"
    else:
        target.write_bytes(result)
    # Preserve the same license/source evidence after moving raw inputs out of
    # release assets. Generation and --check cover the complete production output.
    notice = ASSETS / "third-party/nylon"
    for name in ("LICENSE.txt", "README.md", "manifest.json"):
        original = (PCM_ASSETS / "guitar-nylon" / name).read_bytes()
        if args.check:
            assert original == (notice / name).read_bytes(), f"Stale nylon notice: {name}"
        else:
            notice.mkdir(parents=True, exist_ok=True)
            (notice / name).write_bytes(original)
    print(f"{len(result)} bytes; SHA-256 {hashlib.sha256(result).hexdigest()}")
