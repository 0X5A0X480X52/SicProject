"""Wait for registry propagation and verify public read routes before creating business fixtures."""
from __future__ import annotations
import argparse
import json
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
import requests
from startup_common import read_env

ROOT=Path(__file__).resolve().parents[1]
SERVICES={"IAM-SERVICE","PROJECT-SERVICE","WORKFLOW-SERVICE","MATERIAL-SERVICE","NOTIFICATION-SERVICE","GATEWAY-SERVICE"}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",required=True,type=Path)
    parser.add_argument("--compose-file",action="append",type=Path)
    parser.add_argument("--gateway-base-url",default="http://127.0.0.1:18080")
    parser.add_argument("--timeout",type=float,default=180)
    parser.add_argument("--registry-stable-seconds",type=float,default=65,
        help="Default covers Eureka's 30 second fetch interval plus LoadBalancer's 35 second cache TTL")
    parser.add_argument("--report-path",type=Path,default=ROOT/"verification/runs/readiness.json")
    args=parser.parse_args()
    if args.timeout<=args.registry_stable_seconds or args.registry_stable_seconds<0:raise ValueError("Invalid readiness budget")
    command=["docker","compose","-p",args.project_name,"--env-file",str(args.env_file.resolve())]
    for path in args.compose_file or [ROOT/"docker-compose.yml"]:command += ["-f",str(path.resolve())]
    report={"passed":False,"startedAt":datetime.now(timezone.utc).isoformat(),"project":args.project_name,"checks":[],
        "registryStableSeconds":args.registry_stable_seconds}
    deadline=time.monotonic()+args.timeout
    stable=None
    try:
        while time.monotonic()<deadline:
            try:
                result=subprocess.run(command+["exec","-T","project-service","sh","-lc",
                    "wget -qO- --header='Accept: application/json' http://discovery-server:8761/eureka/apps"],
                    capture_output=True,text=True,encoding="utf-8",timeout=10,check=True)
                apps=json.loads(result.stdout)["applications"].get("application",[])
                if isinstance(apps,dict):apps=[apps]
                ready=set()
                for app in apps:
                    instances=app.get("instance",[])
                    if isinstance(instances,dict):instances=[instances]
                    if any(item.get("status")=="UP" for item in instances):ready.add(app["name"].upper())
                if SERVICES<=ready:
                    if stable is None:stable=time.monotonic()
                    if time.monotonic()-stable>=args.registry_stable_seconds:break
                else:stable=None
            except (OSError,subprocess.SubprocessError,ValueError,KeyError):stable=None
            time.sleep(1)
        else:raise RuntimeError("Service registry did not stabilize within the readiness budget")
        report["checks"].append({"name":"registry propagation","passed":True,"services":sorted(SERVICES)})
        credentials=read_env(args.env_file)
        base=args.gateway_base_url.rstrip("/")
        with requests.Session() as session:
            response=session.post(base+"/api/auth/login",json={"username":credentials["BOOTSTRAP_ADMIN_USERNAME"],
                "password":credentials["BOOTSTRAP_ADMIN_PASSWORD"]},timeout=10)
            if response.status_code!=200:raise RuntimeError(f"Readiness login returned HTTP {response.status_code}")
            session.headers["Authorization"]="Bearer "+response.json()["data"]["token"]
            for path,expected in (("/api/auth/me",{200}),("/api/projects",{200}),
                    ("/api/workflow-definitions",{200}),("/api/projects/0/materials",{403,404})):
                while True:
                    response=session.get(base+path,timeout=10)
                    if response.status_code in expected:break
                    if response.status_code not in {502,503,504} or time.monotonic()>=deadline:
                        raise RuntimeError(f"Readiness read {path} returned HTTP {response.status_code}")
                    time.sleep(1)
                report["checks"].append({"name":path,"passed":True,"status":response.status_code,"traceId":response.headers.get("X-Trace-Id")})
        report["passed"]=True
    except Exception as exc:
        report["error"]=str(exc) if isinstance(exc,RuntimeError) else type(exc).__name__
    finally:
        report["finishedAt"]=datetime.now(timezone.utc).isoformat()
        args.report_path.parent.mkdir(parents=True,exist_ok=True)
        args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(f"Readiness {'PASS' if report['passed'] else 'FAIL'}: {args.report_path}")
    return 0 if report["passed"] else 1


if __name__=="__main__":raise SystemExit(main())
