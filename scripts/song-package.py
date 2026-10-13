#!/usr/bin/env python
"""inspect / normalize / validate / pack. Pure data; never imports or emits Android source."""
import argparse
from pathlib import Path
import sys
import json
import zipfile
from song_package.protocol import *
from song_package import adapters

def new_directory(path):
    path=Path(path);check(not path.exists(),"OUTPUT","/output","Candidate output must be a new directory")
    path.mkdir(parents=True)
    return path
def candidate_payloads(path):
    path=Path(path);payloads={name:(path/name).read_bytes() for name in FILES if name!="manifest.json"}
    manifest=strict_json((path/"manifest.json").read_bytes())
    manifest["files"]={name:dict(bytes=len(data),sha256=digest(data)) for name,data in payloads.items()}
    payloads["manifest.json"]=dumps(manifest)
    return payloads
def inspect_source(path):
    path=Path(path);result=dict(name=path.name,sha256=digest(path.read_bytes()),bytes=path.stat().st_size)
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            result["containerEntries"]=archive.namelist()
            if "Content/score.gpif" in archive.namelist():
                import xml.etree.ElementTree as ET
                root=ET.fromstring(archive.read("Content/score.gpif"))
                result.update(format="gpif",tracks=[dict(id=t.get("id"),name=t.findtext("Name")) for t in root.find("Tracks")],
                    measures=len(root.find("MasterBars")),notes=len(root.find("Notes")),voices=len(root.find("Voices")))
            else: result["format"]="zip"
    elif path.suffix.lower()==".pdf":
        result.update(format="pdf",requiredWorkflow="Reviewed transcript with original PDF hash, page/region locators and independent review")
    elif path.suffix.lower()==".json":
        result.update(format="canonical-json",keys=list(strict_json(path.read_bytes())))
    else: result.update(format="unsupported",requiredWorkflow="Export older GP to GP7/8 with Guitar Pro and audit both sources")
    return result
def normalize(source,selection_path,output):
    selection=strict_json(Path(selection_path).read_bytes());source=Path(source)
    check(selection.get("sourceSha256")==digest(source.read_bytes()),"SOURCE_HASH","/selection/sourceSha256","Source fingerprint differs")
    if source.suffix.lower()==".pdf": score,provenance=adapters.reviewed_pdf(source,selection)
    elif zipfile.is_zipfile(source): score,provenance=adapters.gpif(source,selection)
    else:
        raw=strict_json(source.read_bytes());score=raw.get("score",raw)
        provenance=raw.get("provenance") or dict(sources=[dict(id="source",name=source.name,sha256=digest(source.read_bytes()),
            format="canonical-json",license=selection["license"],origin=selection["origin"])],tool="song-package-v1/canonical",
            mappings=[dict(noteId=n["id"],sourceId="source",location="/voices/"+str(vi)+"/events/"+str(ei)+"/notes/"+str(ni))
                for vi,v in enumerate(score["voices"]) for ei,e in enumerate(v["events"]) for ni,n in enumerate(e["notes"])],exclusions=[])
    practices=selection.get("practices")
    if not practices:
        practices=[dict(id="complete",title=selection["title"],goal="完整来源路线",
            mainTrackId=score["tracks"][0]["id"],mixTrackIds=[t["id"] for t in score["tracks"]],
            segments=[dict(start=dict(measureId=score["measures"][0]["id"]),
                end=dict(measureId=score["measures"][-1]["id"],offset=score["measures"][-1]["actualDuration"]),
                formPolicy="written")])]
    caps=validate_music(score,practices)
    counts=coverage(score,practices)
    payloads={"score.json":dumps(score),"practices.json":dumps(dict(practices=practices,aliases=selection.get("aliases",[]))),
        "provenance.json":dumps(provenance)}
    review=dict(status="approved",reviewer="TODO",method="TODO 独立源谱/来源CSV对照",tool="song-package-v1",
        hashes={k:digest(v) for k,v in payloads.items()},coverage=counts,diagnostics=[],assumptions=[])
    manifest=dict(format="guitar-practice-song",formatVersion=dict(major=1,minor=0),scoreSchemaVersion=dict(major=1,minor=0),
        packageId=selection["packageId"],revision=selection.get("revision",1),
        display=dict(title=selection["title"],author=selection["author"],summary=selection.get("summary","经过审核的本地练习")),
        requires=sorted(caps),files={})
    target=new_directory(output)
    for name,data in payloads.items(): (target/name).write_bytes(data)
    (target/"manifest.json").write_bytes(dumps(manifest))
    (target/"review.template.json").write_bytes(dumps(review))
    return dict(status="review-needed",candidate=str(target),coverage=counts,requires=sorted(caps),
        next="Independent review must create review.json; pack cannot approve a candidate.")
def main():
    parser=argparse.ArgumentParser(description=__doc__);commands=parser.add_subparsers(dest="command",required=True)
    p=commands.add_parser("inspect");p.add_argument("--source",required=True,type=Path);p.add_argument("--output",required=True,type=Path)
    p=commands.add_parser("normalize");p.add_argument("--source",required=True,type=Path);p.add_argument("--manifest",required=True,type=Path);p.add_argument("--output",required=True,type=Path)
    p=commands.add_parser("validate");p.add_argument("--candidate",required=True,type=Path);p.add_argument("--capabilities",type=Path)
    p=commands.add_parser("pack");p.add_argument("--candidate",required=True,type=Path);p.add_argument("--output",required=True,type=Path);p.add_argument("--capabilities",type=Path)
    args=parser.parse_args()
    try:
        if args.command=="inspect":
            target=new_directory(args.output);result=inspect_source(args.source);(target/"inspection.json").write_bytes(dumps(result))
        elif args.command=="normalize": result=normalize(args.source,args.manifest,args.output)
        else:
            payloads=read_container(args.candidate) if args.candidate.is_file() else candidate_payloads(args.candidate)
            caps=strict_json(args.capabilities.read_bytes()) if args.capabilities else None
            roots=validate_payloads(payloads,capabilities=caps)
            result=dict(status="compatible",packageId=roots["manifest.json"]["packageId"],revision=roots["manifest.json"]["revision"],
                coverage=roots["review.json"]["coverage"],requires=roots["manifest.json"]["requires"])
            if args.command=="pack": result.update(output=str(args.output),sha256=write_zip(payloads,args.output))
        print(json.dumps(result,ensure_ascii=False,indent=2));return 0
    except (Blocked,OSError,ValueError,KeyError,IndexError) as e:
        diagnostic=e.diagnostic if isinstance(e,Blocked) else dict(code="INPUT",pointer="",reason=str(e),category="unsupported-blocker")
        print(json.dumps(dict(status="blocked",diagnostics=[diagnostic]),ensure_ascii=False,indent=2));return 2
if __name__=="__main__": sys.exit(main())
