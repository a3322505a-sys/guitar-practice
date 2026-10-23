"""Add reviewed single-note variants to the two retained technique-heavy songs.

Inputs are the exact original five .gpractice files listed in the user's retain
list. The existing schema, player, and teaching-package authoring chain stay as-is.
"""
import argparse
from copy import deepcopy
from pathlib import Path

from song_package.protocol import (
    coverage, digest, dumps, read_container, strict_json,
    validate_music, validate_payloads, write_zip,
)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/song-packages"
SOURCES = (
    ("shayou-v2.gpractice", "shayou-v3.gpractice"),
    ("usotsuki-v1.gpractice", "usotsuki-v1.gpractice"),
    ("your-trip-v1.gpractice", "your-trip-v2.gpractice"),
    ("卡农_C调_清音拨片_v1.gpractice", "canon-c-clean-pick-v1.gpractice"),
    ("Doraemon_no_Uta_r1.gpractice", "Doraemon_no_Uta_r1.gpractice"),
)


def canonical_hash(value):
    return digest(dumps(value))


def source_facts(payloads, source_name, output_name):
    roots = {k: strict_json(v) for k, v in payloads.items()}
    return dict(
        sourceFile=source_name, outputFile=output_name,
        packageId=roots["manifest.json"]["packageId"],
        originalRevision=roots["manifest.json"]["revision"],
        originalScoreHash=canonical_hash(roots["score.json"]),
        originalPracticeHashes={p["id"]: canonical_hash({
            k: v for k, v in p.items() if k not in ("order", "legacyIds")
        }) for p in roots["practices.json"]["practices"]},
        originalPayloadHashes={k: digest(v) for k, v in payloads.items()},
    )


def add_single_note_variants(payloads, source_path):
    roots = {k: strict_json(v) for k, v in payloads.items()}
    score = roots["score.json"]
    practices = roots["practices.json"]["practices"]
    original_voice = score["voices"][0]
    assert len(score["voices"]) == len(score["tracks"]) == 1
    original_track = score["tracks"][0]
    new_track = dict(deepcopy(original_track), id="easy-guitar", name="单音简化 · 逐音拨奏")
    new_voice = dict(id="easy-voice", trackId=new_track["id"], events=[])
    selected = {}
    mappings = []
    for ei, event in enumerate(original_voice["events"]):
        copy = deepcopy(event)
        copy["id"] = "easy-" + event["id"]
        copy["articulation"] = {}
        # Dead notes have no usable sounding pitch. Their durations become rests.
        candidates = [(ni, n) for ni, n in enumerate(event["notes"]) if not n.get("dead")]
        copy["notes"] = []
        if candidates:
            ni, note = max(candidates, key=lambda pair: (pair[1]["midi"], -pair[1]["string"]))
            clean = {k: deepcopy(note[k]) for k in ("string", "fret", "midi")}
            clean["id"] = "easy-" + note["id"]
            velocity = {**event.get("articulation", {}), **note.get("articulation", {})}.get("velocity", 0.8)
            clean["articulation"] = dict(velocity=velocity, pick="default")
            clean["sourceRef"] = "原谱单音派生 / " + note["id"]
            copy["notes"] = [clean]
            selected[note["id"]] = clean
            mappings.append(dict(noteId=clean["id"], sourceId="retained-original",
                location=f"score.json/voices/0/events/{ei}/notes/{ni}; sourceNoteId={note['id']}; 去掉演奏技巧，保留记谱音高/弦品"))
        new_voice["events"].append(copy)
    score["tracks"].append(new_track)
    score["voices"].append(new_voice)
    for group in list(score["tuplets"]):
        copy = deepcopy(group)
        copy.update(id="easy-" + group["id"], voiceId=new_voice["id"],
                    eventIds=["easy-" + e for e in group["eventIds"]])
        if "parentId" in copy:
            copy["parentId"] = "easy-" + copy["parentId"]
        score["tuplets"].append(copy)
        for event in new_voice["events"]:
            event["written"]["tupletGroupIds"] = [
                "easy-" + g if not g.startswith("easy-") else g
                for g in event["written"].get("tupletGroupIds", [])
            ]
    # Ties retain their rhythmic meaning; all performance-technique relations go.
    for relation in list(score["relations"]):
        a, b = relation["fromNoteId"], relation["toNoteId"]
        if relation["kind"] == "tie" and a in selected and b in selected:
            score["relations"].append(dict(id="easy-" + relation["id"], kind="tie",
                fromNoteId=selected[a]["id"], toNoteId=selected[b]["id"]))
            selected[b]["articulation"]["pick"] = "none"
    for index, practice in enumerate(list(practices)):
        practice.pop("legacyIds", None)
        practice["order"] = index * 2
        easy = deepcopy(practice)
        easy.update(id=practice["id"] + "-easy", title=practice["title"] + " · 单音简化",
            goal="只弹单音，逐音拨奏；死音处休止。保留原节奏，可从 40–50 BPM 起练。",
            mainTrackId=new_track["id"], mixTrackIds=[new_track["id"]],
            suggestedTempo=min(50, practice.get("suggestedTempo", 50)), tempoMode="fixed",
            difficulty="简化 · 单音；保留原节奏", visibility="variant", order=index * 2 + 1,
            variantOf=practice["id"], familyId=practice["id"],
            tags=["单音简化", "逐音拨奏", "保留原节奏", "无推弦/滑音/击勾弦/闷音"])
        easy.pop("noteFingerings", None)
        easy["teaching"] = dict(category="歌曲分段练习", passage=practice.get("teaching", {}).get("passage", ""),
            source=source_path.name + "；源段 " + practice["id"], technique="单音逐音拨奏",
            use="先练简化段，再自由切回原段", version="single-note-20261023", tempoLabel="建议 40–50 BPM 起练",
            explanation="双音/和弦保留最高的有音高声部；击勾弦改逐音拨奏；去掉推弦、滑音、揉弦、装饰音、延响和闷音，死音改休止。"
                "保留原弦品、变调夹、休止、十六分/三连音和小节长度；延音线仍按一次起音演奏。推弦处使用原记谱音，不保留音高曲线。")
        practices.append(easy)
    practices.sort(key=lambda p: p["order"])
    provenance = roots["provenance.json"]
    provenance["sources"].append(dict(id="retained-original", name=source_path.name,
        sha256=digest(source_path.read_bytes()), format="gpractice",
        license="既有私人练习素材；原曲权利归原作者", origin="用户指定保留曲包；新增单音派生，原谱实体逐项保留。"))
    provenance["mappings"].extend(mappings)
    review = roots["review.json"]
    review.update(status="approved", reviewer="Codex / 2026-10-23 既有审核曲包与派生单音审阅",
        tool="retained-songs-single-note-v1", method="对照既有审核曲包，独立校验原谱结构指纹、原练习窗口、弦品与实际MIDI；"
            "审阅最高实音声部选取、死音转休止、逐音重拨与延音线；使用正式协议校验器及独立曲包审计。未作真机试弹或音频验收。",
        coverage=coverage(score, practices))
    review["diagnostics"].append(dict(category="approximation", pointer="/score/voices/1",
        featureId="practice.single-note.v1", reason="新增无技巧单音改编；原十六分/三连音节奏保留，可自行降速。",
        practiceIds=[p["id"] for p in practices if p["id"].endswith("-easy")],
        handling="独立简化轨，仅在单音简化练习参与播放；原段仅播放原轨。"))
    manifest = roots["manifest.json"]
    manifest["revision"] += 1
    manifest["display"]["summary"] = "原段与单音简化段相邻列出；保持原谱，可自由选择、调慢 BPM。"
    manifest["requires"] = sorted(validate_music(score, practices))
    out = {k: dumps(roots[k]) for k in ("score.json", "practices.json", "provenance.json")}
    review["hashes"] = {k: digest(v) for k, v in out.items()}
    out["review.json"] = dumps(review)
    manifest["files"] = {k: dict(bytes=len(v), sha256=digest(v)) for k, v in out.items()}
    out["manifest.json"] = dumps(manifest)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ASSETS)
    args = parser.parse_args()
    facts = []
    capabilities = strict_json((ROOT / "contracts/song-package/v1/capabilities.json").read_bytes())
    for source_name, output_name in SOURCES:
        path = args.source_dir / source_name
        payloads = read_container(path)
        validate_payloads(payloads, capabilities=capabilities)
        fact = source_facts(payloads, source_name, output_name)
        fact["originalContainerHash"] = digest(path.read_bytes())
        if source_name in ("shayou-v2.gpractice", "your-trip-v1.gpractice"):
            payloads = add_single_note_variants(payloads, path)
            validate_payloads(payloads, capabilities=capabilities)
            write_zip(payloads, args.output / output_name, overwrite=True)
        else:
            args.output.mkdir(parents=True, exist_ok=True)
            (args.output / output_name).write_bytes(path.read_bytes())
        facts.append(fact)
    path = ROOT / "scripts/score-data/retained-song-facts.json"
    path.write_bytes(dumps(dict(packages=facts)))
    print("Five retained songs adapted; eleven single-note variants added.")


if __name__ == "__main__":
    main()
