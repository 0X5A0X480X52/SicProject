"""Freeze packaged application jars and their hashes for comparison and rollback rehearsals."""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from prepare_verification_env import ROOT, APPLICATIONS
from release_integrity import jar_payload_sha256


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir",type=Path,required=True)
    parser.add_argument("--label",required=True)
    args=parser.parse_args()
    directory=args.release_dir.resolve()
    if directory.exists():raise FileExistsError("A frozen release must not be overwritten")
    sources={name:ROOT/domain/name/"target"/(name+"-1.0.0-SNAPSHOT-exec.jar") for name,domain in APPLICATIONS.items()}
    if any(not source.is_file() for source in sources.values()):raise FileNotFoundError("Package all eight applications first")
    directory.mkdir(parents=True)
    manifest={"schemaVersion":1,"label":args.label,"createdAt":datetime.now(timezone.utc).isoformat(),
        "schemaManifestSha256":hashlib.sha256((ROOT/"infra/mysql/schema-manifest.json").read_bytes()).hexdigest(),"applications":{}}
    for name,source in sources.items():
        target=directory/name/"application.jar"
        target.parent.mkdir();shutil.copyfile(source,target)
        manifest["applications"][name]={"file":target.relative_to(directory).as_posix(),
            "sha256":hashlib.sha256(target.read_bytes()).hexdigest(),"sizeBytes":target.stat().st_size,
            "payloadSha256":jar_payload_sha256(target)}
    (directory/"release-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    print("Frozen eight application jars with SHA-256 manifest: "+str(directory))
    return 0


if __name__=="__main__":raise SystemExit(main())
