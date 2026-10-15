"""Rehearse Redis/session and Project/Feign failures in an explicitly isolated Compose project."""
from __future__ import annotations
import argparse
import json
import re
import subprocess
import time
from pathlib import Path
import requests
from startup_common import read_env
from verify_business_lifecycle import form_payload

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",required=True,type=Path)
    parser.add_argument("--compose-file",action="append",type=Path)
    parser.add_argument("--gateway-base-url",default="http://127.0.0.1:8080")
    parser.add_argument("--business-report",required=True,type=Path)
    parser.add_argument("--confirm-isolated-faults",action="store_true",required=True)
    parser.add_argument("--require-write-fault",action="store_true",help="Require an unused pressure project and verify write rollback/recovery")
    parser.add_argument("--report-path",type=Path,default=ROOT/"verification/runs/dependency-faults.json")
    args=parser.parse_args()
    if not args.project_name.startswith("sic-"):raise ValueError("Use a dedicated sic-* test project")
    command=["docker","compose","-p",args.project_name,"--env-file",str(args.env_file.resolve())]
    for path in args.compose_file or [ROOT/"docker-compose.yml"]:command += ["-f",str(path.resolve())]
    credentials=read_env(args.env_file)
    business=json.loads(args.business_report.read_text(encoding="utf-8"))
    module=business["fixtures"]["modules"]["APPLICATION"]
    report={"passed":False,"project":args.project_name,"checks":[],"requests":[]}
    stopped=set()

    def compose(*arguments,input=None):
        return subprocess.run(command+list(arguments),input=input,capture_output=True,text=True,encoding="utf-8",timeout=60,check=True).stdout

    def sql(statement):
        return compose("exec","-T","mysql","sh","-lc",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names',input=statement).strip()

    def persisted(module):
        return sql(f"""SET SESSION group_concat_max_len=16777216;
SELECT current_seq,current_round,current_node_id,current_state FROM sic_workflow.module_instance WHERE id={module};
SELECT COUNT(*) FROM sic_workflow.state_record WHERE module_instance_id={module};
SELECT COUNT(*),COALESCE(SUM(status='OPEN'),0) FROM sic_workflow.workflow_task WHERE module_instance_id={module};
SELECT COUNT(*),COALESCE(SHA2(GROUP_CONCAT(form_json ORDER BY id SEPARATOR '|'),256),'empty') FROM sic_workflow.node_form WHERE module_instance_id={module};
SELECT COUNT(*) FROM sic_workflow.workflow_material_reference WHERE module_instance_id={module};
SELECT COUNT(*) FROM sic_workflow.outbox_event WHERE JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.payload.moduleInstanceId'))='{module}';""")

    def call(method,path,expected,**kwargs):
        start=time.monotonic()
        response=requests.request(method,args.gateway_base_url.rstrip("/")+path,timeout=12,**kwargs)
        elapsed=round((time.monotonic()-start)*1000)
        report["requests"].append({"method":method,"path":path,"status":response.status_code,
            "durationMs":elapsed,"traceId":response.headers.get("X-Trace-Id")})
        if response.status_code!=expected:raise RuntimeError(f"Expected HTTP {expected}, received {response.status_code}")
        if elapsed>8000:raise RuntimeError("Dependency response exceeded the eight second test budget")
        payload=response.json()
        if payload.get("success")!=(expected<400):raise RuntimeError("API success wrapper differs")
        return payload.get("data")

    def health(service):
        deadline=time.monotonic()+120
        while time.monotonic()<deadline:
            container=compose("ps","-q",service).strip()
            if container:
                state=subprocess.run(["docker","inspect","--format","{{.State.Health.Status}}",container],
                    capture_output=True,text=True,timeout=15,check=True).stdout.strip()
                if state=="healthy":return
            time.sleep(1)
        raise RuntimeError("Dependency health did not recover")

    def metrics():
        return compose("exec","-T","workflow-service","sh","-lc",'wget -qO- "http://localhost:$SERVER_PORT/actuator/prometheus"')

    try:
        login=call("POST","/api/auth/login",200,json={"username":credentials["BOOTSTRAP_ADMIN_USERNAME"],
            "password":credentials["BOOTSTRAP_ADMIN_PASSWORD"]})
        headers={"Authorization":"Bearer "+login["token"]}
        call("GET","/api/auth/me",200,headers=headers)
        stopped.add("redis");compose("stop","redis")
        call("GET","/api/auth/me",503,headers=headers)
        report["checks"].append("Redis outage returns bounded 503")
        compose("start","redis");health("redis");stopped.remove("redis")
        call("GET","/api/auth/me",200,headers=headers)
        report["checks"].append("Same JWT works after persistent Redis recovery")
        write=None
        if args.require_write_fault:
            projects=business.get("pressureProjectIds",[])
            if not projects or any(type(value)!=int or value<=0 for value in projects):
                raise ValueError("A passing mixed-load report with unused pressure projects is required for write faults")
            project=projects[0]
            workbench=call("GET","/api/workflow-workbench/items",200,headers=headers)
            candidates=[row for row in workbench if row["projectId"]==project and row["moduleType"]=="APPLICATION"]
            if len(candidates)!=1:raise RuntimeError("Unused pressure application not found")
            write_module=candidates[0]["moduleInstanceId"]
            view=call("GET",f"/api/module-instances/{write_module}/runtime-view",200,headers=headers)
            if view["context"]["currentNodeId"]!="SubmitApplicationTask":raise RuntimeError("Write fixture was already advanced")
            case=json.loads((ROOT/"verification/scenarios/full-business-lifecycle.case.json").read_text(encoding="utf-8"))
            initial=case["modules"][0]["steps"][0]
            with (ROOT/"output/pdf/blank-upload.pdf").open("rb") as pdf:
                uploaded=call("POST",f"/api/projects/{project}/materials/PROJECT_APPLICATION_FORM/versions",200,headers=headers,
                    files={"file":("blank.pdf",pdf,"application/pdf")})
            version=uploaded["context"]["materialVersionId"]
            form=form_payload(initial["form"]["dataKind"],initial["form"]["formCode"],project,write_module,"dependency-fault")
            call("POST",f"/api/module-instances/{write_module}/transitions",200,headers=headers,json={
                "eventType":initial["eventType"],"expectedSeq":view["context"]["currentSeq"],"result":"APPROVED",
                "remark":"Fault setup","materialVersionIds":[version],"formCode":initial["form"]["formCode"],"nodeFormData":form})
            current=call("GET",f"/api/module-instances/{write_module}/runtime-view",200,headers=headers)["context"]
            if current["currentNodeId"]!="DeptReviewTask":raise RuntimeError("Write fixture did not reach department review")
            step=case["modules"][0]["steps"][1]
            write={"module":write_module,"seq":current["currentSeq"],"before":persisted(write_module),
                "body":{"eventType":step["eventType"],"expectedSeq":current["currentSeq"],"result":"RETURNED",
                    "remark":"Dependency failure must roll back","materialVersionIds":[version],"formCode":step["form"]["formCode"],
                    "nodeFormData":form_payload(step["form"]["dataKind"],step["form"]["formCode"],project,write_module,"dependency-fault")}}
        path=f"/api/module-instances/{module}/runtime-view"
        call("GET",path,200,headers=headers)
        stopped.add("project-service");compose("stop","project-service")
        for index in range(22):
            call("GET",path,503,headers=headers);time.sleep(.1)
        state=metrics()
        report["circuitStates"]=[line for line in state.splitlines() if line.startswith("resilience4j_circuitbreaker_state")]
        if not any('state="open"' in line and line.rstrip().endswith("1.0") for line in report["circuitStates"]):
            raise RuntimeError("No circuit opened after repeated Project failures")
        report["checks"].append("Project outage opens circuit and returns bounded 503")
        if write:
            call("POST",f"/api/module-instances/{write['module']}/transitions",503,headers=headers,json=write["body"])
            if persisted(write["module"])!=write["before"]:raise RuntimeError("Failed write changed workflow business data")
            report["checks"].append("Dependency write failure rolls back state, tasks, forms, material references and outbox")
        deadline=time.monotonic()+10
        while True:
            state=metrics()
            active=[float(line.rsplit(" ",1)[-1]) for line in state.splitlines() if line.startswith("hikaricp_connections_active{")]
            if active and all(value==0 for value in active):break
            if time.monotonic()>deadline:raise RuntimeError("Workflow connections were not released after failures")
            time.sleep(.5)
        report["checks"].append("Workflow pool releases all connections after dependency failures")
        compose("start","project-service");health("project-service");stopped.remove("project-service")
        # Eureka refresh (30s), load-balancer cache (35s), and the circuit's
        # half-open probe can overlap after the container health check succeeds.
        recovery_start=time.monotonic()
        deadline=recovery_start+120
        while True:
            try:call("GET",path,200,headers=headers);break
            except RuntimeError:
                if time.monotonic()>deadline:raise
                time.sleep(1)
        report["checks"].append("Project and public runtime recover through circuit half-open")
        report["projectRecoveryMs"]=round((time.monotonic()-recovery_start)*1000)
        if write:
            # One explicit resubmission after recovery, never an automatic write retry.
            call("POST",f"/api/module-instances/{write['module']}/transitions",200,headers=headers,json=write["body"])
            current=call("GET",f"/api/module-instances/{write['module']}/runtime-view",200,headers=headers)["context"]
            if current["currentSeq"]!=write["seq"]+1 or current["currentNodeId"]!="SubmitApplicationTask":
                raise RuntimeError("Recovered write did not advance exactly once")
            deadline=time.monotonic()+30
            while sql(f"SELECT COUNT(*) FROM sic_material.material_reservation WHERE module_instance_id={write['module']} AND expected_seq={write['seq']} AND status='CONFIRMED';")!="1":
                if time.monotonic()>deadline:raise RuntimeError("Recovered write reservation was not confirmed")
                time.sleep(.5)
            if sql(f"SELECT COUNT(*) FROM sic_material.material_reservation WHERE module_instance_id={write['module']} AND expected_seq={write['seq']} AND status='RELEASED';")!="1":
                raise RuntimeError("Failed request did not release its independent reservation")
            report["checks"].append("Explicit recovered write commits once; winning lease confirms and failed lease remains released")
        report["passed"]=True
    except (OSError,subprocess.SubprocessError,requests.RequestException,RuntimeError,ValueError) as exc:
        report["error"]=type(exc).__name__
    finally:
        for service in stopped:
            try:compose("start",service)
            except (OSError,subprocess.SubprocessError):report.setdefault("recoveryErrors",[]).append(service)
        args.report_path.parent.mkdir(parents=True,exist_ok=True)
        args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(f"Dependency faults {'PASS' if report['passed'] else 'FAIL'}: {args.report_path}")
    return 0 if report["passed"] else 1


if __name__=="__main__":raise SystemExit(main())
