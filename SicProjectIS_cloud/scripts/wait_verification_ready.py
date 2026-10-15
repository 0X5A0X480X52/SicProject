"""Verify exact replica health, both Eureka registries, connection budget and public read routes."""
from __future__ import annotations

import argparse
import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

from deployment_settings import check_ui_bindings, check_running_ui_bindings
from replica_common import (add_deployment_arguments, compose_files, child_environment, load_deployment,
                            wait_instances, connection_budget, check_artifacts, inspect_project, owned_container)
from startup_common import CommandRunner, Redactor, RunLogger, StartupError, read_env

ROOT = Path(__file__).resolve().parents[1]


def read_routes(session, base, deadline, report):
    for path, expected in (("/api/auth/me", {200}), ("/api/projects", {200}),
                           ("/api/workflow-definitions", {200}), ("/api/projects/0/materials", {403, 404})):
        while True:
            try:
                response = session.get(base + path, timeout=10)
                if response.status_code in expected:
                    if response.status_code == 200 and response.json().get("success") is not True:
                        raise StartupError(f"Readiness read {path} returned an unsuccessful response")
                    report["checks"].append({"name": path, "passed": True, "status": response.status_code,
                                             "traceId": response.headers.get("X-Trace-Id")})
                    break
                if response.status_code not in {502, 503, 504}:
                    raise StartupError(f"Readiness read {path} returned HTTP {response.status_code}")
            except requests.RequestException:
                pass
            if time.monotonic() >= deadline:
                raise StartupError(f"Readiness route {path} did not become available within the budget")
            time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name", required=True)
    parser.add_argument("--env-file", required=True, type=Path)
    add_deployment_arguments(parser)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--registry-stable-seconds", type=float, default=65,
                        help="Stable period for all replicas and peer registry mappings (default: 65 seconds)")
    parser.add_argument("--admin-username", help="Current administrator, instead of the original bootstrap username")
    parser.add_argument("--report-path", type=Path, default=ROOT / "verification/runs/readiness.json")
    args = parser.parse_args()
    if args.timeout <= args.registry_stable_seconds or args.registry_stable_seconds < 0:
        parser.error("Invalid readiness budget")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", args.project_name):
        parser.error("Invalid Compose project name")
    report = {"passed": False, "startedAt": datetime.now(timezone.utc).isoformat(),
              "project": args.project_name, "checks": [], "registryStableSeconds": args.registry_stable_seconds}
    logger = RunLogger(ROOT, Redactor(), "readiness")
    deadline = time.monotonic() + args.timeout
    try:
        values = read_env(args.env_file)
        password = os.environ.get("SIC_STARTUP_ADMIN_PASSWORD") or values.get("BOOTSTRAP_ADMIN_PASSWORD", "")
        username = args.admin_username or values.get("BOOTSTRAP_ADMIN_USERNAME", "")
        logger.redactor = Redactor({**values, "ADMIN_PASSWORD": password})
        runner = CommandRunner(logger, ROOT, child_environment(values, compose_files(ROOT, args.compose_file), "history"))
        deployment = load_deployment(ROOT, args, values, runner)
        report.update(deployment.summary())
        check_artifacts(deployment, runner)
        ui = check_ui_bindings(deployment.config, values)
        report["eurekaUI"] = ui.summary()
        containers = inspect_project(runner, deployment)
        if any(not owned_container(item, deployment) for item in containers):
            raise StartupError("Readiness project ownership mismatch")
        report["instances"] = wait_instances(runner, deployment, args.timeout,
                                             stable_seconds=args.registry_stable_seconds)
        report["eurekaUI"]["actualBindings"] = check_running_ui_bindings(inspect_project(runner, deployment), ui)
        report["checks"].append({"name": "registry propagation", "passed": True,
                                 "expectedReplicas": deployment.replicas,
                                 "nodes": list(report["instances"]["registries"])})
        report["connectionBudget"] = connection_budget(deployment, runner)
        report["checks"].append({"name": "database connection budget", "passed": True})
        if not username or not password:
            raise StartupError("Readiness requires current administrator credentials")
        with requests.Session() as session:
            session.trust_env = False
            response = session.post(deployment.gateway + "/api/auth/login", json={"username": username, "password": password}, timeout=10)
            if response.status_code != 200:
                raise StartupError(f"Readiness login returned HTTP {response.status_code}")
            payload = response.json()
            token = (payload.get("data") or {}).get("token")
            if payload.get("success") is not True or not token:
                raise StartupError("Readiness login returned no usable token")
            logger.redactor.secrets.insert(0, token)
            session.headers["Authorization"] = "Bearer " + token
            read_routes(session, deployment.gateway, deadline, report)
        report["passed"] = True
    except Exception as exc:
        report["error"] = logger.redactor.redact(f"{type(exc).__name__}: {exc}")
        logger.write(report["error"], "ERROR")
    finally:
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        args.report_path.parent.mkdir(parents=True, exist_ok=True)
        args.report_path.write_text(logger.redactor.redact(json.dumps(report, ensure_ascii=False, indent=2)) + "\n", encoding="utf-8")
        logger.write(f"Readiness {'PASS' if report['passed'] else 'FAIL'}: {args.report_path}")
        logger.close()
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
