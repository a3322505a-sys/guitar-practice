#!/usr/bin/env python3
"""Build or inspect a three-file .gpractice package. No review/provenance stage."""
import argparse
import json
import sys
from pathlib import Path
from song_package.protocol import (CORE_FILES, Blocked, read_container, strict_json,
                                   validate_payloads, dumps, write_zip)

def load(path):
    if path.is_file():
        raw=read_container(path)
    else:
        raw={name:(path/name).read_bytes() for name in CORE_FILES}
    # The runtime's only input is the three musical documents.
    raw={name:raw[name] for name in CORE_FILES}
    manifest=strict_json(raw["manifest.json"])
    if manifest.get("formatVersion",{}).get("major")==1:
        manifest={key:manifest[key] for key in
                  ("format","scoreSchemaVersion","packageId","revision","display")}
        manifest["formatVersion"]={"major":2,"minor":0}
        raw["manifest.json"]=dumps(manifest)
    return raw

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest="command",required=True)
    build=sub.add_parser("build")
    build.add_argument("--input",required=True,type=Path)
    build.add_argument("--output",required=True,type=Path)
    validate=sub.add_parser("validate")
    validate.add_argument("--input",required=True,type=Path)
    args=parser.parse_args()
    try:
        payloads=load(args.input)
        info=[]
        roots=validate_payloads(payloads,summary=info)
        if args.command=="build":
            normalized={name:dumps(roots[name]) for name in CORE_FILES}
            digest=write_zip(normalized,args.output,overwrite=True)
        else:
            digest=None
        print(json.dumps({"status":"ok","packageId":roots["manifest.json"]["packageId"],
                          "sha256":digest,"normalized":info},ensure_ascii=False,indent=2))
        return 0
    except (Blocked,OSError,ValueError,KeyError,IndexError) as error:
        diagnostic=error.diagnostic if isinstance(error,Blocked) else {"reason":str(error)}
        print(json.dumps({"status":"error","diagnostic":diagnostic},ensure_ascii=False,indent=2))
        return 2

if __name__=="__main__":
    sys.exit(main())
