"""Record immutable release hashes, fresh database row counts, and Docker resources without secrets."""
from __future__ import annotations
import argparse
import json
import subprocess
from datetime import datetime,timezone
from pathlib import Path
from prepare_verification_env import ROOT


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",type=Path,required=True)
    parser.add_argument("--compose-file",type=Path,action="append",required=True)
    parser.add_argument("--release-dir",type=Path,required=True)
    parser.add_argument("--report-path",type=Path,required=True)
    args=parser.parse_args()
    compose=["docker","compose","-p",args.project_name,"--env-file",str(args.env_file.resolve())]
    for path in args.compose_file:compose += ["-f",str(path.resolve())]
    manifest=json.loads((args.release_dir/"release-manifest.json").read_text(encoding="utf-8"))
    # Use actual metadata; do not trust an approximate InnoDB table_rows value.
    command=compose+["exec","-T","mysql","sh","-lc",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names']
    listing="SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ('sic_iam','sic_project','sic_workflow','sic_material','sic_notification') AND table_type='BASE TABLE' ORDER BY table_schema,table_name;"
    tables=subprocess.run(command,input=listing,text=True,capture_output=True,check=True,timeout=30).stdout.splitlines()
    if len(tables)!=52:raise ValueError("Fresh performance databases must have 52 tables")
    sql="\n".join(f"SELECT '{database}.{table}',COUNT(*) FROM `{database}`.`{table}`;" for database,table in (line.split("\t") for line in tables))
    counts=dict(line.split("\t") for line in subprocess.run(command,input=sql,text=True,capture_output=True,check=True,timeout=30).stdout.splitlines())
    raw=json.loads(subprocess.run(["docker","info","--format","{{json .}}"],text=True,capture_output=True,check=True,timeout=30).stdout)
    versions=subprocess.run(compose+["exec","-T","mysql","sh","-lc",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "SELECT VERSION()"'],text=True,capture_output=True,check=True,timeout=30).stdout.strip()
    report={"recordedAt":datetime.now(timezone.utc).isoformat(),"project":args.project_name,
        "release":manifest,"initialRows":{name:int(count) for name,count in counts.items()},
        "resources":{"dockerServerVersion":raw["ServerVersion"],"cpus":raw["NCPU"],"memoryBytes":raw["MemTotal"],"mysqlVersion":versions,
            "javaHeap":"-Xms64m -Xmx384m","poolSize":10},"schemaTableCount":len(tables)}
    args.report_path.parent.mkdir(parents=True,exist_ok=True)
    args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("Recorded performance release, resources and 52 exact table counts: "+str(args.report_path))
    return 0


if __name__=="__main__":raise SystemExit(main())
