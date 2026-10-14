"""Explicit Gateway/Feign distribution, full business and one-replica recovery verification."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import secrets
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from threading import Event

import requests
from startup_common import CommandRunner, Redactor, RunLogger, StartupError, read_env
from replica_common import (BUSINESS, add_deployment_arguments, compose_files, child_environment,
                            load_deployment, inspect_project, owned_container, wait_instances, instance_rows,
                            internal_json)
from verify_business_lifecycle import LifecycleRunner

EVIDENCE = re.compile(r"SIC_REQUEST_EVIDENCE service=(\S+) instance=(\S+) trace=(\S+) method=(\S+) path=(\S+) status=(\d+) durationMs=(\d+)")


class ReplicaVerifier:
    def __init__(self, args, runner, deployment, values):
        self.args, self.runner, self.deployment, self.values = args, runner, deployment, values
        self.run_id = "lb-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + secrets.token_hex(3)
        self.started = datetime.now(timezone.utc).isoformat()
        self.report = {"schemaVersion": 1, "runId": self.run_id, "mode": args.mode, "passed": False,
                       "startedAt": self.started, "deployment": deployment.summary(), "checks": [], "requests": [],
                       "businessReports": [], "faults": [], "recoveryErrors": [], "error": None}
        self.directory = deployment.root / "verification/runs" / self.run_id
        self.directory.mkdir(parents=True, exist_ok=True)
        self.session = requests.Session()
        self.token = ""
        self.fixture = None
        self.stopped = set()
        self.initial_targets = None

    def call(self, path, method="GET", body=None, stream=False, trace=None, raw=False):
        trace = trace or f"{self.run_id}-{len(self.report['requests']):06d}"
        entry = {"traceId": trace, "method": method, "path": path.split("?")[0], "status": None,
                 "requestedAt": datetime.now(timezone.utc).isoformat()}
        self.report["requests"].append(entry)
        headers = {"X-Trace-Id": trace, "Accept": "text/event-stream" if stream else "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        started = time.monotonic()
        try:
            response = self.session.request(method, self.deployment.gateway + path, headers=headers, json=body,
                                            stream=stream, timeout=self.args.request_timeout)
        except requests.RequestException as exc:
            entry.update(durationMs=round((time.monotonic() - started) * 1000), errorType=type(exc).__name__)
            raise
        entry["durationMs"] = round((time.monotonic() - started) * 1000)
        entry["status"] = response.status_code
        if response.status_code != 200:
            response.close()
            raise StartupError(f"{method} {entry['path']} returned {entry['status']}")
        if stream:
            return response
        try:
            if raw:
                return response.content
            result = response.json()
            if not result.get("success"):
                raise StartupError(f"{method} {entry['path']} returned business failure")
            return result["data"]
        finally:
            response.close()

    def login(self):
        password = os.environ.get("SIC_E2E_ADMIN_PASSWORD") or os.environ.get("SIC_STARTUP_ADMIN_PASSWORD") or self.values.get("BOOTSTRAP_ADMIN_PASSWORD")
        username = self.args.admin_username or os.environ.get("SIC_E2E_ADMIN_USERNAME") or self.values.get("BOOTSTRAP_ADMIN_USERNAME")
        if not username or not password:
            raise StartupError("Supply administrator credentials through SIC_E2E_ADMIN_USERNAME/PASSWORD or the original env file")
        self.runner.logger.redactor.secrets.append(password)
        data = self.call("/api/auth/login", "POST", {"username": username, "password": password})
        self.token = data["token"]
        self.runner.logger.redactor.secrets.append(self.token)
        os.environ["SIC_E2E_ADMIN_USERNAME"], os.environ["SIC_E2E_ADMIN_PASSWORD"] = username, password

    def lifecycle_args(self, attempt):
        return argparse.Namespace(gateway_base_url=self.deployment.gateway,
            case=self.deployment.root / "verification/scenarios/full-business-lifecycle.case.json",
            fixture_pdf=self.deployment.root / "output/pdf/blank-upload.pdf", report_path=self.directory / f"business-{attempt}.json",
            negative_cases=True, ui_regression_cases=True, iteration_regression_cases=True,
            request_timeout=self.args.request_timeout, poll_timeout=90, poll_interval=.5, request_interval=.1,
            trace_prefix=f"{self.run_id}-biz{attempt}")

    def ensure_fixture(self):
        if self.fixture is None:
            lifecycle = LifecycleRunner(self.lifecycle_args(0))
            lifecycle.bootstrap()
            lifecycle.start_project()
            self.fixture = lifecycle
            self.report["fixtures"] = {"runId": lifecycle.run_id, "projectId": lifecycle.project,
                                       "moduleId": lifecycle.modules.get("APPLICATION"),
                                       "actors": {key: {"userId": value["userId"]} for key, value in lifecycle.accounts.items()}}
        return self.fixture

    def evidence(self, extra_traces=()):
        containers = inspect_project(self.runner, self.deployment)
        traces = {row["traceId"] for row in self.report["requests"]} | set(extra_traces)
        rows = []
        for item in containers:
            if item.get("Config", {}).get("Labels", {}).get("com.docker.compose.service") not in BUSINESS:
                continue
            text = self.runner.capture(["docker", "logs", "--since", self.started, item["Id"]]).decode("utf-8", "replace")
            # Java logs normally go to stdout; do not store unfiltered process output.
            for match in EVIDENCE.finditer(text):
                service, instance, trace, method, path, status, duration = match.groups()
                if trace not in traces:
                    continue
                rows.append({"service": service, "instance": instance, "containerId": item["Id"], "traceId": trace,
                             "method": method, "path": path, "status": int(status), "durationMs": int(duration)})
        return rows

    def targets(self, service):
        if self.initial_targets is not None:
            return self.initial_targets[service]
        return {row["containerId"] for row in instance_rows(inspect_project(self.runner, self.deployment))
                if row["service"] == service and row["running"]}

    def sse(self):
        response = self.call("/api/sse/subscribe", stream=True)
        try:
            self.connected_lines(response)
        finally:
            response.close()

    def connected_lines(self, response):
        deadline = time.monotonic() + self.args.request_timeout
        lines = response.iter_lines(chunk_size=1, decode_unicode=True)
        for line in lines:
            if time.monotonic() >= deadline:
                raise StartupError("SSE CONNECTED deadline exceeded")
            if line == "event:CONNECTED":
                return lines
        raise StartupError("SSE CONNECTED event missing")

    def distribution(self):
        fixture = self.ensure_fixture()
        probes = {
            "iam-service": "/api/auth/me", "project-service": "/api/projects",
            "workflow-service": f"/api/module-instances/{fixture.modules['APPLICATION']}/runtime-view",
            "material-service": f"/api/projects/{fixture.project}/materials", "notification-service": "/api/sse/subscribe"}
        for service, path in probes.items():
            target = self.targets(service)
            for _ in range(20 * self.deployment.replicas[service]):
                self.sse() if service == "notification-service" else self.call(path)
                time.sleep(.1)
            seen = {row["containerId"] for row in self.evidence() if row["service"] == service and row["path"] == path and row["status"] == 200}
            if not target <= seen:
                raise StartupError(f"Gateway did not hit every {service} replica")
            self.report["checks"].append({"name": f"gateway:{service}", "passed": True, "containers": sorted(seen)})
        for caller, target, path, upstream in [
            ("project-service", "iam-service", "/internal/admin/overview-stats", "/api/admin/overview"),
            ("workflow-service", "project-service", "/internal/projects/access-decisions", probes["workflow-service"])]:
            for _ in range(20 * self.deployment.replicas[target]):
                self.call(upstream)
                time.sleep(.1)
            evidence = self.evidence()
            origin_traces = {row["traceId"] for row in evidence if row["service"] == caller and row["path"] == upstream}
            seen = {row["containerId"] for row in evidence if row["service"] == target and row["path"] == path
                    and row["traceId"] in origin_traces and row["status"] == 200}
            if not self.targets(target) <= seen:
                raise StartupError(f"Natural Feign calls {caller}->{target} did not hit every replica")
            self.report["checks"].append({"name": f"feign:{caller}->{target}", "passed": True, "containers": sorted(seen)})
        self.report["balancePassed"] = True

    def business(self):
        self.sse_fanout()
        pairs = [("project-service", "workflow-service", "/internal/application-sagas"),
                 ("workflow-service", "iam-service", "/internal/users/"),
                 ("workflow-service", "material-service", "/internal/material-reservations")]
        all_traces = set()
        for attempt in range(1, max(3, max(self.deployment.replicas.values())) + 1):
            lifecycle = LifecycleRunner(self.lifecycle_args(attempt))
            code = lifecycle.execute()
            self.report["businessReports"].append(str(lifecycle.args.report_path))
            if code:
                raise StartupError("Full business lifecycle failed; fixtures retained")
            command = [sys.executable, str(self.deployment.root / "scripts/verify_business_db.py"),
                       "--business-report", str(lifecycle.args.report_path), "--project-name", self.deployment.project,
                       "--env-file", str(self.deployment.env_file), "--report-path", str(self.directory / f"database-{attempt}.json")]
            for file in self.deployment.files:
                command += ["--compose-file", str(file)]
            self.runner.stream(command)
            all_traces.update(row["traceId"] for row in lifecycle.report["requests"] if row.get("traceId"))
            evidence = self.evidence(all_traces)
            self.verify_material_access(lifecycle, evidence, all_traces)
            evidence = self.evidence(all_traces)
            checks = []
            for service in BUSINESS:
                seen = {row["containerId"] for row in evidence if row["service"] == service and row["status"] == 200}
                checks.append({"name": f"business-instances:{service}", "passed": self.targets(service) <= seen,
                               "containers": sorted(seen)})
            for caller, target, path in pairs:
                origin = {row["traceId"] for row in evidence if row["service"] == caller and row["path"].startswith("/api/")}
                seen = {row["containerId"] for row in evidence if row["service"] == target and row["path"].startswith(path)
                        and row["traceId"] in origin and row["status"] == 200}
                checks.append({"name": f"business-feign:{caller}->{target}", "passed": self.targets(target) <= seen,
                               "containers": sorted(seen)})
            if all(check["passed"] for check in checks):
                self.report["checks"].extend(checks)
                self.report["businessPassed"] = True
                self.report["businessTraceIds"] = sorted(all_traces)
                return
        self.report["businessTraceIds"] = sorted(all_traces)
        raise StartupError("Business completed but natural Feign distribution remains unverified")

    def verify_material_access(self, lifecycle, evidence, traces):
        """Read every uploaded version through a different replica and verify the fixture bytes."""
        fixtures = lifecycle.report["fixtures"]
        versions = fixtures["materialVersions"]
        uploads = {code: {row["containerId"] for row in evidence if row["service"] == "material-service"
            and row["method"] == "POST" and row["status"] == 200
            and row["path"] == f"/api/projects/{fixtures['projectId']}/materials/{code}/versions"} for code in versions}
        if any(not ids for ids in uploads.values()):
            raise StartupError("Missing material upload instance evidence")
        if self.deployment.replicas["material-service"] == 1:
            self.report["checks"].append({"name": "material-download-integrity", "passed": True,
                "crossReplicaRequired": False, "versions": len(versions)})
            return
        downloaded = {code: set() for code in versions}
        for _ in range(20 * self.deployment.replicas["material-service"]):
            for code, version in versions.items():
                if downloaded[code] - uploads[code]:
                    continue
                content = self.call(f"/api/material-versions/{version}/download", raw=True)
                if hashlib.sha256(content).hexdigest() != fixtures["fixtureSha256"]:
                    raise StartupError(f"Cross-replica material download hash differs: {code}")
            evidence = self.evidence(traces)
            for code, version in versions.items():
                downloaded[code].update(row["containerId"] for row in evidence if row["service"] == "material-service"
                    and row["method"] == "GET" and row["status"] == 200
                    and row["path"] == f"/api/material-versions/{version}/download")
            if all(downloaded[code] - uploads[code] for code in versions):
                self.report["checks"].append({"name": "cross-replica-material-downloads", "passed": True,
                    "versions": {code: {"versionId": versions[code], "uploadContainers": sorted(uploads[code]),
                        "downloadContainers": sorted(downloaded[code])} for code in versions}})
                return
        raise StartupError("Some uploaded versions were never read through another material replica")

    def sse_fanout(self):
        """Keep one public Gateway stream on every notification replica and require a live event on each."""
        streams = {}
        try:
            for _ in range(20 * self.deployment.replicas["notification-service"]):
                response = self.call("/api/sse/subscribe", stream=True)
                trace = self.report["requests"][-1]["traceId"]
                try:
                    lines = self.connected_lines(response)
                except Exception:
                    response.close()
                    raise
                matches = [row for row in self.evidence() if row["traceId"] == trace and row["service"] == "notification-service"]
                if not matches:
                    response.close()
                    raise StartupError("Cannot correlate SSE connection to its instance")
                instance = matches[0]["containerId"]
                if instance in streams:
                    response.close()
                else:
                    streams[instance] = (response, lines)
                if self.targets("notification-service") <= streams.keys():
                    break
            if not self.targets("notification-service") <= streams.keys():
                raise StartupError("Could not establish SSE streams on every replica")
            expected, project_ready = {}, Event()
            def next_update(lines):
                if not project_ready.wait(self.args.request_timeout):
                    raise StartupError("Timed out waiting for the SSE trigger request")
                event_name, data = "", []
                deadline = time.monotonic() + self.args.request_timeout
                for line in lines:
                    if time.monotonic() >= deadline:
                        raise StartupError("SSE project event deadline exceeded")
                    if line.startswith("event:"):
                        event_name = line[6:].strip()
                    elif line.startswith("data:"):
                        data.append(line[5:].strip())
                    elif not line:
                        if event_name == "MODULE_STATE_CHANGED" and data:
                            payload = json.loads("\n".join(data))
                            if payload.get("type") == "MODULE_STATE_CHANGED" and payload.get("projectId") == expected.get("projectId"):
                                return payload
                        event_name, data = "", []
                raise StartupError("SSE ended before the matching project event")
            with ThreadPoolExecutor(max_workers=len(streams)) as executor:
                pending = [executor.submit(next_update, lines) for _, lines in streams.values()]
                try:
                    project = self.call("/api/project-applications/start", "POST", {"projectName": self.run_id + "-sse"})
                    expected["projectId"] = project["projectId"]
                    self.report["sseProjectId"] = project["projectId"]
                finally:
                    project_ready.set()
                received = [task.result(timeout=self.args.request_timeout + 10) for task in pending]
                if any(payload != received[0] for payload in received[1:]):
                    raise StartupError("Notification replicas delivered different project events")
            self.report["checks"].append({"name": "sse-cross-replica-fanout", "passed": True,
                "containers": sorted(streams), "event": received[0]})
        finally:
            for response, _ in streams.values():
                response.close()

    def fault(self):
        fixture = self.ensure_fixture()
        probes = {"iam-service": "/api/auth/me", "project-service": "/api/projects",
                  "workflow-service": "/api/workflow-workbench/query", "material-service": f"/api/projects/{fixture.project}/materials",
                  "notification-service": "/api/sse/subscribe"}
        selected = [service for service in BUSINESS if self.deployment.replicas[service] > 1]
        if not selected:
            raise StartupError("Fault verification requires at least one multi-instance business service")
        for service in selected:
            container_id = sorted(self.targets(service))[0]
            timeline = {"service": service, "containerId": container_id, "errors": [], "passed": False}
            self.report["faults"].append(timeline)
            started = time.monotonic()
            self.stopped.add(container_id)
            try:
                self.runner.stream(["docker", "stop", "--time", "15", container_id])
                expected = dict(self.deployment.replicas)
                expected[service] -= 1
                # Observe requests during registry removal too; only these read probes are retried.
                deadline, successes = time.monotonic() + self.args.recovery_timeout, 0
                while time.monotonic() < deadline and successes < 10:
                    try:
                        self.sse() if service == "notification-service" else self.call(probes[service])
                        successes += 1
                    except (StartupError, requests.RequestException) as exc:
                        successes = 0
                        timeline["errors"].append({"elapsedMs": round((time.monotonic() - started) * 1000),
                                                   "error": type(exc).__name__, "traceId": self.report["requests"][-1]["traceId"],
                                                   "status": self.report["requests"][-1]["status"]})
                    if "registryRemovalMs" not in timeline:
                        try:
                            from replica_common import check_registry, check_container_health
                            current = inspect_project(self.runner, self.deployment)
                            check_container_health(current, expected)
                            registry = internal_json(self.runner, self.deployment, "discovery-server", "/eureka/apps", 8761)
                            check_registry(current, registry, expected)
                            timeline["registryRemovalMs"] = round((time.monotonic() - started) * 1000)
                        except (StartupError, ValueError, KeyError):
                            successes = 0
                    if "registryRemovalMs" not in timeline:
                        successes = 0
                    time.sleep(.2)
                if successes != 10:
                    raise StartupError(f"Remaining {service} replicas did not sustain read probes")
                timeline["remainingServiceMs"] = round((time.monotonic() - started) * 1000)
            finally:
                self.runner.stream(["docker", "start", container_id])
                self.stopped.discard(container_id)
            wait_instances(self.runner, self.deployment, self.args.recovery_timeout, stable_seconds=65)
            recovery_traces = set()
            for _ in range(20 * self.deployment.replicas[service]):
                self.sse() if service == "notification-service" else self.call(probes[service])
                recovery_traces.add(self.report["requests"][-1]["traceId"])
                time.sleep(.1)
            seen = {row["containerId"] for row in self.evidence() if row["service"] == service and row["traceId"] in recovery_traces and row["status"] == 200}
            if not self.targets(service) <= seen:
                raise StartupError(f"Restarted {service} instance did not rejoin distribution")
            timeline.update(passed=True, recoveredAt=datetime.now(timezone.utc).isoformat(), containers=sorted(seen))
        self.report["faultPassed"] = True

    def execute(self):
        try:
            containers = inspect_project(self.runner, self.deployment)
            if any(not owned_container(item, self.deployment) for item in containers):
                raise StartupError("Verification project ownership mismatch")
            for service in BUSINESS:
                definitions = self.deployment.config["services"][service].get("environment", {})
                if str(definitions.get("SIC_LB_EVIDENCE_ENABLED", "false")).lower() != "true":
                    raise StartupError("Set SIC_LB_EVIDENCE_ENABLED=true in the test env and recreate applications before distribution verification")
            self.report["instances"] = wait_instances(self.runner, self.deployment, 600, stable_seconds=0)
            self.initial_targets = {service: {row["containerId"] for row in self.report["instances"]["containers"]
                if row["service"] == service and row["running"]} for service in BUSINESS}
            self.login()
            getattr(self, {"balance": "distribution", "business": "business", "fault": "fault"}[self.args.mode])()
            from replica_common import check_container_health, check_registry
            final = inspect_project(self.runner, self.deployment)
            check_container_health(final, self.deployment.replicas)
            check_registry(final, internal_json(self.runner, self.deployment, "discovery-server", "/eureka/apps", 8761),
                           self.deployment.replicas)
            self.report["finalInstances"] = instance_rows(final)
            self.report["passed"] = True
        except Exception as exc:
            self.report["error"] = self.runner.logger.redactor.redact(f"{type(exc).__name__}: {exc}")
            self.runner.logger.write(self.report["error"], "ERROR")
        finally:
            for container_id in list(self.stopped):
                try:
                    self.runner.stream(["docker", "start", container_id])
                    self.stopped.discard(container_id)
                except Exception as exc:
                    self.report["recoveryErrors"].append({"containerId": container_id, "error": type(exc).__name__})
            if self.report["recoveryErrors"]:
                self.report["passed"] = False
            try:
                self.report["evidence"] = self.evidence(self.report.get("businessTraceIds", []))
            except Exception as exc:
                self.report["evidenceError"] = type(exc).__name__
                self.report["passed"] = False
            self.report["finishedAt"] = datetime.now(timezone.utc).isoformat()
            path = self.args.report_path or self.directory / "report.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(self.runner.logger.redactor.redact(json.dumps(self.report, ensure_ascii=False, indent=2)), encoding="utf-8")
            self.session.close()
            if self.fixture:
                for session in self.fixture.sessions.values():
                    session.close()
            print(f"Load balancing {self.args.mode}: {'PASS' if self.report['passed'] else 'FAIL'}; report={path}")
        return 0 if self.report["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("balance", "business", "fault"), required=True)
    parser.add_argument("--project-name", default="sic-cloud")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--admin-username")
    parser.add_argument("--report-path", type=Path)
    parser.add_argument("--request-timeout", type=float, default=20)
    parser.add_argument("--recovery-timeout", type=float, default=180)
    add_deployment_arguments(parser)
    args = parser.parse_args()
    if args.request_timeout <= 0 or args.recovery_timeout <= 65:
        parser.error("Timeouts must be positive; recovery timeout must exceed 65 seconds")
    root = Path(__file__).resolve().parents[1]
    args.env_file = (args.env_file or root / ".env").resolve()
    logger = RunLogger(root, Redactor(), "load-balancing")
    try:
        values = read_env(args.env_file)
        logger.redactor = Redactor(values)
        runner = CommandRunner(logger, root, child_environment(values, compose_files(root, args.compose_file), "history"))
        deployment = load_deployment(root, args, values, runner)
        return ReplicaVerifier(args, runner, deployment, values).execute()
    except Exception as exc:
        logger.write(exc, "ERROR")
        run_id = "lb-preflight-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + secrets.token_hex(3)
        path = args.report_path or root / "verification/runs" / run_id / "report.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(logger.redactor.redact(json.dumps({"runId": run_id, "mode": args.mode, "passed": False,
            "phase": "preflight", "error": str(exc), "projectName": args.project_name,
            "envFile": str(args.env_file)}, ensure_ascii=False, indent=2)), encoding="utf-8")
        print(f"Load balancing {args.mode}: FAIL (not accepted); report={path}")
        return 1
    finally:
        logger.close()


if __name__ == "__main__":
    raise SystemExit(main())
