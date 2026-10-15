"""Switch changed applications to a frozen compatible release and back; preserve databases and sessions."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import subprocess
import time
from pathlib import Path
import requests
from prepare_verification_env import ROOT,APPLICATIONS
from startup_common import read_env
from release_integrity import jar_payload_sha256


def release(directory):
    directory=directory.resolve()
    manifest=json.loads((directory/"release-manifest.json").read_text(encoding="utf-8"))
    if set(manifest["applications"])!=set(APPLICATIONS):raise ValueError("A full frozen release is required")
    paths={}
    for name,entry in manifest["applications"].items():
        path=(directory/entry["file"]).resolve()
        if not path.is_relative_to(directory) or hashlib.sha256(path.read_bytes()).hexdigest()!=entry["sha256"]:
            raise ValueError("Release integrity check failed: "+name)
        paths[name]=path
    return manifest,paths


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",type=Path,required=True)
    parser.add_argument("--compose-file",type=Path,action="append",required=True)
    parser.add_argument("--gateway-base-url",required=True)
    parser.add_argument("--baseline-release",type=Path,required=True)
    parser.add_argument("--candidate-release",type=Path,required=True)
    parser.add_argument("--business-report",type=Path,required=True)
    parser.add_argument("--report-path",type=Path,required=True)
    parser.add_argument("--confirm-isolated-rollback",action="store_true",required=True)
    args=parser.parse_args()
    if not re.fullmatch(r"sic-[a-z0-9-]+",args.project_name):raise ValueError("Use an isolated sic-* project")
    baseline,bpaths=release(args.baseline_release);candidate,cpaths=release(args.candidate_release)
    if baseline["schemaManifestSha256"]!=candidate["schemaManifestSha256"]:
        raise ValueError("This rehearsal requires identical database schemas; separately validate SQL patch compatibility")
    changed=[name for name in APPLICATIONS if jar_payload_sha256(bpaths[name])!=jar_payload_sha256(cpaths[name])]
    if not changed:raise ValueError("Rehearsal must switch at least one changed application")
    # Shared configuration and discovery changes need a separate deployment sequence.
    if any(name in changed for name in ("config-server","discovery-server")):
        raise ValueError("Shared infrastructure changes require a dedicated rollback rehearsal")
    business=json.loads(args.business_report.read_text(encoding="utf-8"))
    if not business["overallPassed"]:raise ValueError("A passing business run is required")
    fixtures=business["fixtures"]
    project=fixtures["projectId"];modules=fixtures["modules"]
    if not isinstance(project,int) or project<=0:raise ValueError("Invalid fixture project ID")
    command=["docker","compose","-p",args.project_name,"--env-file",str(args.env_file.resolve())]
    for path in args.compose_file:command += ["-f",str(path.resolve())]
    override=ROOT/"target"/f"{args.project_name}-rollback.compose.yaml"
    report={"passed":False,"project":args.project_name,"changedApplications":changed,"checks":[],"requests":[]}
    report["checksumPolicy"]={"excludedMessagingTables":["outbox_event","consumed_event"],
        "notificationPayloadIncludes":"All pending_notification columns except scheduled retry_count",
        "operationalCounters":["sic_notification.pending_notification.retry_count"]}
    credentials=read_env(args.env_file)
    stage="initial verification"
    headers={}

    def request(method,path,**kwargs):
        response=requests.request(method,args.gateway_base_url.rstrip("/")+path,headers=headers,timeout=12,**kwargs)
        report["requests"].append({"method":method,"path":path,"status":response.status_code,"traceId":response.headers.get("X-Trace-Id")})
        if response.status_code!=200:raise RuntimeError("Public verification returned HTTP "+str(response.status_code))
        return response

    def state():
        authorization=request("GET",f"/api/projects/{project}/authorization").json()["data"]
        result={"lifecycle":authorization["project"]["lifecycleStage"],"modules":{}}
        for kind,module in modules.items():
            context=request("GET",f"/api/module-instances/{module}/runtime-view").json()["data"]["context"]
            result["modules"][kind]={key:context[key] for key in ("currentSeq","currentRoundNo","currentNodeId","currentState")}
        for version in fixtures["materialVersions"].values():
            downloaded=request("GET",f"/api/material-versions/{version}/download").content
            if hashlib.sha256(downloaded).hexdigest()!=fixtures["fixtureSha256"]:raise RuntimeError("Material content changed")
        return result

    def checksums():
        shell='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'
        sql="SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ('sic_iam','sic_project','sic_workflow','sic_material','sic_notification') AND table_name NOT IN ('outbox_event','consumed_event','pending_notification') ORDER BY table_schema,table_name;"
        mysql=command+["exec","-T","mysql","sh","-lc",shell]
        tables=subprocess.run(mysql,input=sql,text=True,capture_output=True,check=True,timeout=30).stdout.splitlines()
        query="CHECKSUM TABLE "+",".join("`"+database+"`.`"+table+"`" for database,table in (row.split("\t") for row in tables))+" EXTENDED;"
        rows=subprocess.run(mysql,input=query,text=True,capture_output=True,check=True,timeout=30).stdout.splitlines()
        # No SSE client is attached in this rehearsal, so Notification retries are
        # expected to continue. Preserve recipient, event, payload and delivery state.
        notification="""SET SESSION group_concat_max_len=16777216;
SELECT 'sic_notification.pending_notification',SHA2(COALESCE(GROUP_CONCAT(SHA2(CAST(JSON_OBJECT(
 'id',id,'eventId',event_id,'userId',user_id,'eventName',event_name,'payload',payload_json,
 'status',status,'createdAt',CAST(created_at AS CHAR),'deliveredAt',CAST(delivered_at AS CHAR)) AS CHAR),256)
 ORDER BY id SEPARATOR ''),''),256) FROM sic_notification.pending_notification;
SELECT COALESCE(SUM(retry_count),0) FROM sic_notification.pending_notification;"""
        result=subprocess.run(mysql,input=notification,text=True,capture_output=True,check=True,timeout=30).stdout.splitlines()
        rows.append(result[0]);report.setdefault("notificationRetryCounters",[]).append(int(result[1]))
        return rows

    def deploy(paths):
        lines=["services:"]
        for name in changed:
            lines += [f"  {name}:","    volumes: !override "+json.dumps([{"type":"bind","source":str(paths[name]),"target":"/application/application.jar","read_only":True}])]
        override.write_text("\n".join(lines)+"\n",encoding="utf-8")
        subprocess.run(command+["-f",str(override),"up","-d","--no-build","--no-deps","--force-recreate","--wait","--wait-timeout","180"]+changed,
            capture_output=True,text=True,check=True,timeout=210)

    def verify_checksums(before):
        after=checksums()
        if after!=before:
            old=dict(row.split("\t") for row in before);new=dict(row.split("\t") for row in after)
            report["checksumDifferences"]=[{"table":name,"before":old.get(name),"after":new.get(name)}
                for name in sorted(set(old)|set(new)) if old.get(name)!=new.get(name)]
            raise ValueError("Business table checksums changed across application deployment")

    def quiescent_checkpoint():
        # A deployment must not be blamed for projections of writes still in transit.
        # Drain both publishers and broker deliveries, then require a stable checkpoint.
        deadline=time.monotonic()+180
        previous=None
        pending_sql="SELECT SUM(pending) FROM ("+" UNION ALL ".join(
            f"SELECT COUNT(*) AS pending FROM sic_{domain}.outbox_event WHERE published_at IS NULL"
            for domain in ("iam","project","workflow","material","notification"))+") AS pending_events;"
        mysql=command+["exec","-T","mysql","sh","-lc",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names']
        while True:
            pending=int(subprocess.run(mysql,input=pending_sql,text=True,capture_output=True,
                check=True,timeout=30).stdout.strip())
            output=subprocess.run(command+["exec","-T","rabbitmq","rabbitmqctl","list_queues",
                "--quiet","name","messages_ready","messages_unacknowledged"],
                text=True,capture_output=True,check=True,timeout=30).stdout
            queues=[row.split() for row in output.splitlines() if len(row.split())==3
                and row.split()[-1].isdigit() and row.split()[-2].isdigit()]
            if not queues:raise ValueError("Broker queue inventory is missing")
            deliveries=sum(int(row[-1])+int(row[-2]) for row in queues)
            report.setdefault("quiescenceSamples",[]).append({"pendingOutbox":pending,"brokerDeliveries":deliveries})
            if not pending and not deliveries:
                current=checksums()
                if current==previous:
                    report["checks"].append("Publishers and broker drained; business checkpoint stable before deployment")
                    return current
                previous=current
            else:previous=None
            if time.monotonic()>deadline:raise RuntimeError("Business messages did not reach a stable checkpoint")
            time.sleep(5)

    def recovered(expected):
        deadline=time.monotonic()+180
        while True:
            try:
                current=state()
                if current!=expected:raise ValueError("Persisted public state changed across deployment")
                return
            except (RuntimeError,requests.RequestException):
                if time.monotonic()>deadline:raise
                time.sleep(1)

    try:
        login=request("POST","/api/auth/login",json={"username":credentials["BOOTSTRAP_ADMIN_USERNAME"],"password":credentials["BOOTSTRAP_ADMIN_PASSWORD"]}).json()["data"]
        headers["Authorization"]="Bearer "+login["token"]
        before=quiescent_checkpoint();expected=state()
        stage="baseline rollback";deploy(bpaths);recovered(expected)
        verify_checksums(before)
        report["checks"].append("Frozen baseline runs against unchanged databases; same JWT, module sequences and all material bytes pass")
        stage="candidate roll forward";deploy(cpaths);recovered(expected)
        verify_checksums(before)
        if report["notificationRetryCounters"]!=sorted(report["notificationRetryCounters"]):
            raise ValueError("Notification retry counter regressed across deployment")
        report["checks"].append("Candidate restored; business table checksums and public state preserved")
        report["businessTableCount"]=len(before);report["passed"]=True
    except (OSError,ValueError,RuntimeError,subprocess.SubprocessError,requests.RequestException) as exc:
        report["error"]={"stage":stage,"type":type(exc).__name__}
    finally:
        if not report["passed"] and stage!="initial verification":
            try:deploy(cpaths)
            except (OSError,subprocess.SubprocessError):report["candidateRecoveryFailed"]=True
        args.report_path.parent.mkdir(parents=True,exist_ok=True)
        args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("Application rollback "+("PASS" if report["passed"] else "FAIL")+": "+str(args.report_path))
    return 0 if report["passed"] else 1


if __name__=="__main__":raise SystemExit(main())
