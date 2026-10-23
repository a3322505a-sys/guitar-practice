"""Adapt the supplied Hana ni Bourei package without changing score bytes."""
import argparse
from copy import deepcopy
from pathlib import Path
from song_package.protocol import (
    compile_summary, coverage, digest, dumps, read_container, strict_json,
    validate_payloads, write_zip,
)

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/song-packages/hana-bourei-lead-v2.gpractice"
SOURCE_SHA256 = "6a42204db4cd95092977d34337a1aacecbd6cab9a06a670c882bc5696ce54c52"
TITLES = {
    "prechorus-regular": "预副歌 · 常规扫弦",
    "prechorus-muted-fill": "预副歌 · 闷音加花",
    "chorus-complete": "副歌 · 完整",
    "chorus-final": "副歌 · 末段双音引入",
    "interlude-riff-complete": "间奏 riff · 完整",
    "harmonics-complete": "泛音桥段 · 完整",
    "solo-complete": "间奏 Solo · 完整",
    "outro-riff-complete": "尾奏 riff · 完整变奏",
}


def adapt(source):
    assert digest(source.read_bytes()) == SOURCE_SHA256, "Use the reviewed original v1 input"
    payloads = read_container(source)
    caps = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    roots = validate_payloads(payloads, capabilities=caps)
    score = roots["score.json"]
    catalogue = roots["practices.json"]
    originals = catalogue["practices"]
    assert len(originals) == 18 and not catalogue["aliases"]
    plans = {p["id"]: compile_summary(score, p) for p in originals}
    byid = {p["id"]: p for p in originals}
    removed = [p for p in originals if p["id"] not in TITLES]
    assert len(removed) == 10
    aliases = []
    for p in removed:
        parent = byid[p["variantOf"]]
        assert parent["id"] in TITLES
        assert all(p.get(k) == parent.get(k) for k in (
            "mainTrackId", "mixTrackIds", "suggestedTempo", "tempoMode", "padding", "noteFingerings"))
        events = {e["event"]["id"] for e in plans[p["id"]]["events"]}
        parent_events = {e["event"]["id"] for e in plans[parent["id"]]["events"]}
        assert events and events <= parent_events, p["id"]
        aliases.append({"from": p["id"], "to": parent["id"]})
    kept = [deepcopy(p) for p in originals if p["id"] in TITLES]
    for order, p in enumerate(kept):
        p.update(title=TITLES[p["id"]], order=order)
    catalogue["practices"] = kept
    catalogue["aliases"] = aliases
    original_notes = {n["note"]["id"] for p in originals for n in plans[p["id"]]["notes"]}
    kept_notes = {n["note"]["id"] for p in kept for n in plans[p["id"]]["notes"]}
    assert original_notes == kept_notes

    review = roots["review.json"]
    prior_method = review["method"]
    review.update(reviewer="Codex / 既有源谱审核与新版目录适配", tool="adapt-hana-bourei.py",
        method="原包审核记录：" + prior_method + " 本次仅核对原包指纹、逐字节保留 score/provenance、"
        "删除10个完全被完整段覆盖的目录拆分项，并区分8个保留项名称。未重新取得GP/PDF，未作真机试听。",
        coverage=coverage(score, kept))
    for diagnostic in review["diagnostics"]:
        diagnostic["practiceIds"] = [pid for pid in diagnostic["practiceIds"] if pid in TITLES]
    manifest = roots["manifest.json"]
    manifest["revision"] = 2
    manifest["display"]["summary"] = (
        "6组、8项原谱练习；去除10个完全重叠的拆分项；标准调弦 Capo 2，原速90。"
        "装饰音和震音沿用原包的明示展开。")
    out = {"score.json": payloads["score.json"], "provenance.json": payloads["provenance.json"],
        "practices.json": dumps(catalogue)}
    review["hashes"] = {name: digest(data) for name, data in out.items()}
    out["review.json"] = dumps(review)
    manifest["files"] = {name: dict(bytes=len(data), sha256=digest(data)) for name, data in out.items()}
    out["manifest.json"] = dumps(manifest)
    validate_payloads(out, capabilities=caps)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ASSET)
    args = parser.parse_args()
    write_zip(adapt(args.source), args.output, overwrite=True)
    print("Hana ni Bourei: 8 practices, 10 covered splits removed; score/provenance bytes unchanged.")


if __name__ == "__main__":
    main()
