"""Run the complete public business flow while independent projects receive batch authorization writes."""
from __future__ import annotations
import argparse
import concurrent.futures
import json
import os
import statistics
import subprocess
import sys
import threading
import time
from pathlib import Path
import requests
from startup_common import read_env
from verify_business_lifecycle import LifecycleRunner, ROOT, require


class Limiter:
    def __init__(self, rate: float):
        self.interval = 1 / rate
        self.next = 0.0
        self.lock = threading.Lock()

    def wait(self) -> None:
        with self.lock:
            scheduled = max(time.monotonic(), self.next)
            self.next = scheduled + self.interval
        delay = scheduled - time.monotonic()
        if delay > 0:
            time.sleep(delay)


class MixedRunner(LifecycleRunner):
    def __init__(self, args):
        super().__init__(args)
        self.limiter = Limiter(args.requests_per_second)
        self.stop = threading.Event()
        self.workers = concurrent.futures.ThreadPoolExecutor(max_workers=args.workers)
        self.futures = []
        self.pressure_requests = []
        self.pressure_lock = threading.Lock()
        self.started = 0.0
        self.monitor = None
        self.snapshots = []
        self.monitor_errors = []

    def request(self, *args, **kwargs):
        self.limiter.wait()
        return super().request(*args, **kwargs)

    def start_project(self):
        super().start_project()
        member_ids = []
        for index in range(self.args.extra_members):
            account = self.request("leader", "POST", "/api/auth/register", token="", body={
                "username": f"load_{self.run_id}_{index}", "password": self.password,
                "realName": f"Load {self.run_id} {index}", "deptId": self.dept_id})
            member_ids.append(account["user"]["userId"])
        if not member_ids:
            member_ids = [self.accounts["finance"]["userId"]]
        targets = []
        for index in range(self.args.workers):
            body = {key: value.replace("<runId>", self.run_id + f"-pressure-{index}")
                if isinstance(value, str) else value for key, value in self.case["projectStart"]["body"].items()}
            targets.append(self.request("leader", "POST", "/api/project-applications/start", body=body)["projectId"])
        self.report["pressureProjectIds"] = targets
        self.started = time.monotonic()
        if self.args.stability_project_name:
            self.monitor = threading.Thread(target=self.observe,name="stability-sampler")
            self.monitor.start()
        for target in targets:
            self.futures.append(self.workers.submit(self.pressure, target, member_ids))

    def observe(self):
        index=0
        directory=ROOT/"target"/"stability"/self.run_id
        directory.mkdir(parents=True,exist_ok=True)
        while not self.stop.is_set():
            path=directory/f"sample-{index}.json"
            command=[sys.executable,str(ROOT/"scripts/collect_stability_snapshot.py"),"--project-name",self.args.stability_project_name,
                "--env-file",str(self.args.env_file.resolve()),"--report-path",str(path)]
            for file in self.args.stability_compose_file or [ROOT/"docker-compose.yml"]:command.extend(["--compose-file",str(file.resolve())])
            try:
                result=subprocess.run(command,capture_output=True,text=True,encoding="utf-8",timeout=45)
                snapshot=json.loads(path.read_text(encoding="utf-8"))
                snapshot["loadOffsetSeconds"]=round(time.monotonic()-self.started,2)
                self.snapshots.append(snapshot)
                if result.returncode or not snapshot["complete"]:self.monitor_errors.append(f"Incomplete sample {index}")
            except (OSError,subprocess.SubprocessError,ValueError):self.monitor_errors.append(f"Sample {index} failed")
            index+=1
            self.stop.wait(self.args.stability_sample_interval)

    def pressure(self, project: int, members: list[int]):
        with requests.Session() as session:
            headers = {"Authorization": "Bearer " + self.tokens["science"], "Accept": "application/json"}

            def send(method, suffix, body):
                self.limiter.wait()
                path = f"/api/projects/{project}/" + suffix
                entry = {"method": method, "path": path, "status": None, "traceId": None}
                started = time.perf_counter()
                try:
                    response = session.request(method, self.base + path, headers=headers, json=body,
                        timeout=self.args.request_timeout)
                    entry.update(status=response.status_code, traceId=response.headers.get("X-Trace-Id"))
                    require(response.status_code == 200, "Pressure write failed with HTTP " + str(response.status_code))
                    payload = response.json()
                    require(payload.get("success") is True, "Pressure API success wrapper differs")
                    return payload["data"]
                except Exception as exc:
                    entry["error"] = self.redact(type(exc).__name__)
                    self.stop.set()
                    raise
                finally:
                    entry["durationMs"] = round((time.perf_counter() - started) * 1000, 3)
                    with self.pressure_lock:
                        self.pressure_requests.append(entry)

            while not self.stop.is_set():
                result = send("PUT", "members/batch", {"userIds": members, "responsibility": "Load regression"})
                require(result["affectedCount"] == len(members), "Member batch did not report the whole command")
                grant = send("POST", "expert-grants/batch", {"userIds": [self.accounts[a]["userId"] for a in ("expert1", "expert2")],
                    "moduleType": "APPLICATION", "roundNo": 1, "taskNodeId": "ScienceExpertReviewTask", "reason": "Load regression"})
                detail = grant["detail"]
                ids = [row["projectRoleGrantId"] for row in detail["expertGrants"] if row["status"] == "ACTIVE"]
                require(len(ids) == 2, "Expert batch did not create exactly two active grants")
                revoked = send("POST", "grants/revoke-batch", {"grantIds": ids, "reason": "Load regression"})
                require(revoked["affectedCount"] == 2, "Revocation batch result differs")

    def final_assertions(self):
        super().final_assertions()
        while time.monotonic() - self.started < self.args.minimum_load_seconds and not self.stop.is_set():
            time.sleep(.2)
        self.stop.set()
        for future in self.futures:
            future.result(timeout=self.args.request_timeout + 10)
        if self.monitor:
            self.monitor.join(timeout=50)
            require(not self.monitor.is_alive(),"Stability sampler did not finish")
            require(len(self.snapshots)>=2 and not self.monitor_errors,"Mandatory stability sampling was incomplete")
            deadlocks=[int(line.split("\t")[1]) for sample in self.snapshots for line in sample["mysql"] if line.startswith("INNODB_DEADLOCKS\t")]
            require(len(deadlocks)==len(self.snapshots) and deadlocks[-1]==deadlocks[0],"New MySQL deadlocks occurred during the mixed load")
        require(len(self.pressure_requests) >= self.args.workers * 3, "Pressure workload was not exercised")

    def execute(self):
        try:
            code = super().execute()
        finally:
            self.stop.set()
            self.workers.shutdown(wait=True, cancel_futures=True)
            if self.monitor:self.monitor.join(timeout=50)
            self.report["pressureRequests"] = self.pressure_requests
            self.report["load"] = {"workers": self.args.workers, "batchMembers": self.args.extra_members,
                "requestsPerSecondLimit": self.args.requests_per_second,
                "durationSeconds": round(time.monotonic() - self.started, 2) if self.started else 0}
            values = sorted(row["durationMs"] for row in self.pressure_requests)
            self.report["pressureLatencyMs"] = {"count": len(values), "mean": round(statistics.mean(values), 2) if values else None,
                "p95": values[min(len(values)-1, int(len(values)*.95))] if values else None,
                "max": max(values) if values else None}
            self.report["latencyMeasurement"] = {"clock": "perf_counter",
                "resolutionSeconds": time.get_clock_info("perf_counter").resolution,
                "timedRegion": "HTTP request, success validation and JSON decoding"}
            self.args.report_path.write_text(json.dumps(self.safe_report(self.report), ensure_ascii=False, indent=2), encoding="utf-8")
            if self.args.stability_project_name:
                path=self.args.stability_report_path or self.args.report_path.with_name(self.args.report_path.stem+"-stability.json")
                series={"runId":self.run_id,"complete":len(self.snapshots)>=2 and not self.monitor_errors,
                    "errors":self.monitor_errors,"samples":self.snapshots}
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(json.dumps(self.safe_report(series),indent=2)+"\n",encoding="utf-8")
        return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--env-file", required=True, type=Path)
    parser.add_argument("--case", type=Path, default=ROOT / "verification/scenarios/full-business-lifecycle.case.json")
    parser.add_argument("--fixture-pdf", type=Path, default=ROOT / "output/pdf/blank-upload.pdf")
    parser.add_argument("--report-path", type=Path, default=ROOT / "verification/runs/mixed-load.json")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--extra-members", type=int, default=24)
    parser.add_argument("--requests-per-second", type=float, default=25)
    parser.add_argument("--minimum-load-seconds", type=float, default=60)
    parser.add_argument("--stability-project-name",help="Collect mandatory MySQL/pool/Feign/Redis/queue samples during this load")
    parser.add_argument("--stability-compose-file",type=Path,action="append")
    parser.add_argument("--stability-report-path",type=Path)
    parser.add_argument("--stability-sample-interval",type=float,default=5)
    parser.add_argument("--request-timeout", type=float, default=15)
    parser.add_argument("--poll-timeout", type=float, default=60)
    parser.add_argument("--poll-interval", type=float, default=.5)
    parser.add_argument("--request-interval", type=float, default=0)
    args = parser.parse_args()
    args.negative_cases = False
    require(1 <= args.workers <= 32 and 0 <= args.extra_members <= 500, "Load fixture limits differ")
    require(0 < args.requests_per_second <= 30 and args.minimum_load_seconds > 0, "Load limits must be positive and leave Gateway headroom")
    require(args.stability_sample_interval>0,"Stability sampling interval must be positive")
    values = read_env(args.env_file)
    for name in ("USERNAME", "PASSWORD"):
        os.environ.setdefault("SIC_E2E_ADMIN_" + name, values.get("BOOTSTRAP_ADMIN_" + name, ""))
    return MixedRunner(args).execute()


if __name__ == "__main__":
    raise SystemExit(main())
