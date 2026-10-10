"""Verify persisted business/score/material relationships for one completed Gateway run."""
from __future__ import annotations
import argparse
import json
import re
import subprocess
import time
from pathlib import Path
from datetime import datetime, timezone
from verify_schema import ROOT


def positive(value: object) -> int:
    if not isinstance(value,int) or value<=0:
        raise ValueError("Fixture IDs must be positive integers")
    return value


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--business-report",type=Path,required=True)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",type=Path)
    parser.add_argument("--compose-file",type=Path,action="append")
    parser.add_argument("--report-path",type=Path,default=ROOT/"verification/runs/business-database.json")
    parser.add_argument("--poll-timeout",type=float,default=60)
    args=parser.parse_args()
    business=json.loads(args.business_report.read_text(encoding="utf-8"))
    if not business["overallPassed"]:raise ValueError("Gateway lifecycle run must pass first")
    f=business["fixtures"]
    rejected=business.get("caseId")=="E2E-BIZ-RETURN-REJECT-002"
    project=positive(f["projectId"])
    modules={kind:positive(f["modules"][kind]) for kind in ("APPLICATION","CONTRACT","ACCEPTANCE")}
    module_ids=",".join(map(str,modules.values()))
    versions={code:positive(value) for code,value in f["materialVersions"].items()}
    if len(versions)!=17 or any(not re.fullmatch(r"[A-Z0-9_]{1,64}",code) for code in versions):
        raise ValueError("Unexpected material fixture inventory")
    version_ids=",".join(map(str,versions.values()))
    batches=list(f["batchIds"])+list(f.get("historicalBatchIds",[]))
    batch_ids=",".join(str(positive(value)) for value in batches)
    stage="ACCEPTANCE_REJECTED" if rejected else "PROJECT_FINISHED"
    checks={
        "projectFinished": (f"SELECT COUNT(*) FROM sic_project.project WHERE id={project} AND business_status='{stage}' AND lifecycle_status='ACTIVE'",1),
        "modulesFinished": (f"SELECT COUNT(*) FROM sic_workflow.module_instance WHERE id IN ({module_ids}) AND project_id={project} AND finished=TRUE",3),
        "openTasks": (f"SELECT COUNT(*) FROM sic_workflow.workflow_task WHERE module_instance_id IN ({module_ids}) AND status='OPEN'",0),
        "reviewBatches": (f"SELECT COUNT(*) FROM sic_workflow.expert_review_batch WHERE id IN ({batch_ids}) AND status='COMPLETED' AND submitted_expert_count=2 AND valid_expert_count=2 AND final_score=85 AND final_result='RECOMMENDED' AND access_ended_at IS NOT NULL",3),
        "submittedAssignments": (f"SELECT COUNT(*) FROM sic_workflow.expert_review_assignment WHERE batch_id IN ({batch_ids}) AND review_status='SUBMITTED' AND valid=TRUE",len(batches)*2),
        "scoreRows": (f"SELECT COUNT(*) FROM sic_workflow.expert_review_score s JOIN sic_workflow.expert_review_assignment a ON a.id=s.assignment_id WHERE a.batch_id IN ({batch_ids}) AND s.score_value IN (30,40,80,90)",len(batches)*2),
        "applicationDraft": (f"SELECT COUNT(*) FROM sic_workflow.project_application WHERE module_instance_id={modules['APPLICATION']} AND project_id={project} AND limited_project=TRUE AND JSON_EXTRACT(payload_json,'$.application.applicationTitle') IS NOT NULL",1),
        "contractDraft": (f"SELECT COUNT(*) FROM sic_workflow.contract_form WHERE module_instance_id={modules['CONTRACT']} AND JSON_EXTRACT(payload_json,'$.contract.contractNo') IS NOT NULL",1),
        "acceptanceDraft": (f"SELECT COUNT(*) FROM sic_workflow.acceptance_form WHERE module_instance_id={modules['ACCEPTANCE']} AND JSON_EXTRACT(payload_json,'$.acceptance.certificateNo') IS NOT NULL",1),
        "financialRecord": (f"SELECT COUNT(*) FROM sic_workflow.acceptance_financial_settlement WHERE module_instance_id={modules['ACCEPTANCE']} AND received_amount=1000 AND spent_amount=800 AND surplus_amount=200 AND execution_rate=80 AND state_record_id IS NOT NULL",1),
        "uploadedVersions": (f"SELECT COUNT(*) FROM sic_material.material_version v JOIN sic_material.material m ON m.id=v.material_id WHERE v.id IN ({version_ids}) AND m.project_id={project} AND v.file_status<>'DELETED'",17),
        "unconfirmedReferences": (f"SELECT COUNT(*) FROM sic_workflow.workflow_material_reference w LEFT JOIN sic_material.material_reservation r ON r.module_instance_id=w.module_instance_id AND r.expected_seq=w.seq-1 AND r.status='CONFIRMED' LEFT JOIN sic_material.material_reservation_item i ON i.reservation_id=r.reservation_id AND i.material_version_id=w.material_version_id AND i.material_type_code=w.material_type_code WHERE w.module_instance_id IN ({module_ids}) AND i.material_version_id IS NULL",0),
    }
    if rejected:
        checks["rejectedReview"]=(f"SELECT COUNT(*) FROM sic_workflow.expert_review_batch WHERE id IN ({batch_ids}) AND status='COMPLETED' AND submitted_expert_count=2 AND final_score=35 AND final_result='REJECTED' AND access_ended_at IS NOT NULL",1)
        checks["applicationRoundThree"]=(f"SELECT COUNT(*) FROM sic_workflow.module_instance WHERE id={modules['APPLICATION']} AND current_round=3",1)
        checks["acceptanceRejected"]=(f"SELECT COUNT(*) FROM sic_workflow.module_instance WHERE id={modules['ACCEPTANCE']} AND finished=TRUE AND current_state='ACCEPTANCE_REJECTED'",1)
    for code,version in versions.items():
        checks["reference_"+code]=(f"SELECT EXISTS(SELECT 1 FROM sic_workflow.workflow_material_reference WHERE module_instance_id IN ({module_ids}) AND material_version_id={version} AND material_type_code='{code}')",1)
    sql="\n".join(query.replace("SELECT ",f"SELECT '{name}',",1)+";" for name,(query,_) in checks.items())
    command=["docker","compose","-p",args.project_name]
    if args.env_file:command += ["--env-file",str(args.env_file.resolve())]
    for file in args.compose_file or [ROOT/"docker-compose.yml"]:command += ["-f",str(file.resolve())]
    command += ["exec","-T","mysql","sh","-lc",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names']
    report={"schemaVersion":1,"runId":business["runId"],"projectId":project,"passed":False,"checks":{}}
    deadline=time.monotonic()+args.poll_timeout
    while True:
        try:
            result=subprocess.run(command,input=sql,cwd=ROOT,text=True,encoding="utf-8",capture_output=True,timeout=30,check=True)
            actual=dict(line.split("\t") for line in result.stdout.splitlines())
            report["checks"]={name:{"expected":expected,"actual":int(actual[name]),"passed":int(actual[name])==expected}
                for name,(_,expected) in checks.items()}
            report["passed"]=all(check["passed"] for check in report["checks"].values())
        except (OSError,subprocess.SubprocessError,ValueError,KeyError) as exc:
            report["error"]=type(exc).__name__
            break
        if report["passed"] or time.monotonic()>=deadline:break
        time.sleep(1)
    report["checkedAt"]=datetime.now(timezone.utc).isoformat()
    args.report_path.parent.mkdir(parents=True,exist_ok=True)
    args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(f"Business DB {'PASS' if report['passed'] else 'FAIL'}: {args.report_path}")
    return 0 if report["passed"] else 1


if __name__=="__main__":raise SystemExit(main())
