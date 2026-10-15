"""Verify peer replication, fresh-client failover and recovery in an isolated SIC test deployment."""
from __future__ import annotations

import argparse
import json
import os
import re
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, Thread

import requests

from startup_common import CommandRunner, Redactor, RunLogger, StartupError, read_env, parse_json_bytes
from replica_common import (DISCOVERY, BUSINESS, add_deployment_arguments, child_environment,
                            compose_files, load_deployment, inspect_project, instance_rows,
                            owned_container, internal_json, registry_rows, wait_instances,
                            check_container_health, check_artifacts)


class HAVerifier:
    def __init__(self, args, deployment, runner, report):
        self.args, self.deployment, self.runner, self.report = args, deployment, runner, report
        self.probes, self.stopped = set(), set()
        self.session = requests.Session()
        self.session.trust_env = False

    def wait(self, action, description):
        deadline, last = time.monotonic() + self.args.timeout, "not checked"
        while time.monotonic() < deadline:
            try:
                return action()
            except (StartupError, requests.RequestException, ValueError, KeyError) as exc:
                last = str(exc)
                time.sleep(2)
        raise StartupError(f"{description} timed out: {last}")

    def node_rows(self, node):
        return registry_rows(internal_json(self.runner, self.deployment, node, "/eureka/apps", 8761))

    def baseline(self):
        if not self.args.registry_only:
            return wait_instances(self.runner, self.deployment, self.args.timeout, stable_seconds=0)
        containers = inspect_project(self.runner, self.deployment)
        check_container_health(containers, {node: 1 for node in DISCOVERY})
        registries = {node: self.node_rows(node) for node in DISCOVERY}
        required = {f"{node}:8761" for node in DISCOVERY}
        for node, rows in registries.items():
            actual = {row["instanceId"] for row in rows if row["service"] == "discovery-server" and row["status"] == "UP"}
            if not required <= actual:
                raise StartupError(f"{node}: peer registrations have not converged")
        return {"registries": registries}

    def start_probe(self, urls):
        # Reuse the release's Eureka module as a real Netflix DiscoveryClient. Its
        # server registry is populated by syncUp from the freshly fetched client
        # registry, allowing a cold registry query without an extra tool image.
        name = f"{self.deployment.project}-ha-probe-{uuid.uuid4().hex[:8]}"
        self.probes.add(name)
        containers = inspect_project(self.runner, self.deployment)
        source = next(item for item in containers if item.get("Config", {}).get("Labels", {}).get(
            "com.docker.compose.service") == "discovery-server")
        networks = list(source["NetworkSettings"]["Networks"])
        if len(networks) != 1:
            raise StartupError("HA probe requires one unambiguous Compose network")
        # Do not inherit the service hostname/alias: /etc/hosts would resolve the
        # discovery-server peer URL back to the probe itself. A separate Docker
        # service label also prevents compose exec from selecting a one-off probe.
        command = ["docker", "run", "-d", "--name", name, "--hostname", name, "--network", networks[0],
            "--publish", "127.0.0.1::8761", "--label", "sic.eureka-ha-probe=true",
            "--label", f"com.docker.compose.project={self.deployment.project}",
            "--label", "com.docker.compose.service=sic-ha-probe",
            "--label", f"com.docker.compose.project.working_dir={self.deployment.root}",
            "-e", "SERVER_PORT=8761", "-e", "SPRING_PROFILES_ACTIVE=ha",
            "-e", "SPRING_APPLICATION_NAME=sic-ha-probe", "-e", f"EUREKA_HOSTNAME={name}",
            "-e", f"EUREKA_PEER_URL={urls}"]
        artifact = self.deployment.summary()["artifacts"]["discovery-server"]
        if artifact["jar"]:
            command += ["--volume", f"{artifact['jar']}:/application/application.jar:ro"]
        entrypoint = source["Config"]["Entrypoint"]
        command += ["--entrypoint", entrypoint[0], source["Config"]["Image"],
                    *entrypoint[1:], *(source["Config"].get("Cmd") or [])]
        self.runner.capture(command)
        container = parse_json_bytes(self.runner.capture(["docker", "inspect", name]), "probe", self.runner.logger.redactor)[0]
        bindings = container["NetworkSettings"]["Ports"]["8761/tcp"]
        if len(bindings) != 1 or bindings[0]["HostIp"] != "127.0.0.1":
            raise StartupError("HA probe must publish only a loopback port")
        return name, f"http://127.0.0.1:{bindings[0]['HostPort']}"

    def probe_ready(self, base):
        response = self.session.get(base + "/actuator/health/readiness", timeout=5)
        response.raise_for_status()
        if response.json().get("status") != "UP":
            raise StartupError("Fresh client is not ready")

    def registered(self, node, name):
        rows = self.node_rows(node)
        match = [row for row in rows if row["service"] == "sic-ha-probe" and row["instanceId"] == name + ":8761" and row["status"] == "UP"]
        if len(match) != 1:
            raise StartupError(f"{node}: fresh probe registration not present")
        return match[0]

    def remove_probe(self, name):
        # Verify exact ownership before removing only the temporary probe.
        raw = self.runner.capture(["docker", "inspect", name])
        container = parse_json_bytes(raw, "probe cleanup", self.runner.logger.redactor)[0]
        if (not owned_container(container, self.deployment)
                or container.get("Config", {}).get("Labels", {}).get("sic.eureka-ha-probe") != "true"):
            raise StartupError(f"Refusing to remove an unowned probe: {name}")
        self.runner.stream(["docker", "stop", "--time", "20", name])
        self.runner.stream(["docker", "rm", name])
        self.probes.discard(name)

    def replication(self, source, target):
        started = time.monotonic()
        name, base = self.start_probe(f"http://{source}:8761/eureka/")
        try:
            self.wait(lambda: self.probe_ready(base), "replication probe startup")
            self.wait(lambda: self.registered(source, name), "source registration")
            registration = self.wait(lambda: self.registered(target, name), "peer replication")
            self.report["checks"].append({"name": "peer-replication", "source": source, "target": target,
                "passed": True, "registration": registration, "elapsedMs": round((time.monotonic() - started) * 1000)})
        finally:
            self.remove_probe(name)

    def login(self, values):
        username = self.args.admin_username or values.get("BOOTSTRAP_ADMIN_USERNAME", "")
        password = os.environ.get("SIC_STARTUP_ADMIN_PASSWORD") or values.get("BOOTSTRAP_ADMIN_PASSWORD", "")
        if not username or not password:
            raise StartupError("Business checks require current administrator credentials")
        self.runner.logger.redactor.secrets.insert(0, password)
        response = self.session.post(self.deployment.gateway + "/api/auth/login",
            json={"username": username, "password": password}, timeout=10)
        response.raise_for_status()
        body = response.json()
        token = (body.get("data") or {}).get("token")
        if not body.get("success") or not token:
            raise StartupError("HA verification login failed")
        self.runner.logger.redactor.secrets.insert(0, token)
        self.token = token

    def sample_reads(self, stop, samples, started):
        with requests.Session() as session:
            session.trust_env = False
            session.headers["Authorization"] = "Bearer " + self.token
            paths = ["/api/auth/me", "/api/projects", "/api/workflow-definitions"]
            while not stop.is_set():
                path = paths[len(samples) % len(paths)]
                sample = {"path": path, "elapsedMs": round((time.monotonic() - started) * 1000), "passed": False}
                try:
                    response = session.get(self.deployment.gateway + path, timeout=5)
                    sample.update(status=response.status_code, traceId=response.headers.get("X-Trace-Id"),
                                  passed=response.status_code == 200 and response.json().get("success") is True)
                except (requests.RequestException, ValueError) as exc:
                    sample["error"] = type(exc).__name__
                samples.append(sample)
                stop.wait(1)

    def fault(self, failed, survivor):
        containers = inspect_project(self.runner, self.deployment)
        targets = [row for row in instance_rows(containers) if row["service"] == failed and row["running"]]
        if len(targets) != 1:
            raise StartupError(f"Expected exactly one running {failed}")
        container_id = targets[0]["containerId"]
        timeline = {"failedNode": failed, "survivor": survivor, "passed": False, "requests": []}
        self.report["faults"].append(timeline)
        started, stop, sampler, name = time.monotonic(), Event(), None, None
        self.stopped.add(container_id)
        try:
            # docker stop keeps the node down despite unless-stopped; abrupt Java
            # termination is provided by --time 0 without disabling restart policy.
            self.runner.stream(["docker", "stop", "--time", "0", container_id])
            timeline["stoppedAt"] = datetime.now(timezone.utc).isoformat()
            if not self.args.registry_only:
                sampler = Thread(target=self.sample_reads, args=(stop, timeline["requests"], started), daemon=True)
                sampler.start()
            urls = f"http://{failed}:8761/eureka/,http://{survivor}:8761/eureka/"
            name, base = self.start_probe(urls)
            self.wait(lambda: self.probe_ready(base), "fresh-client failover startup")
            registration = self.wait(lambda: self.registered(survivor, name), "new registration on survivor")
            response = self.session.get(base + "/eureka/apps", headers={"Accept": "application/json"}, timeout=5)
            response.raise_for_status()
            fetched = registry_rows(response.json())
            required = {"discovery-server"} if self.args.registry_only else set([*BUSINESS, "gateway-service"])
            if not required <= {row["service"] for row in fetched if row["status"] == "UP"}:
                raise StartupError("Fresh client failed its initial registry fetch from the survivor")
            target = next(row for row in fetched if row["status"] == "UP" and (
                row["instanceId"] == survivor + ":8761" if self.args.registry_only else row["service"] == "project-service"))
            called = parse_json_bytes(self.runner.capture(["docker", "exec", name, "wget", "-T", "5", "-qO-",
                f"http://{target['ip']}:{target['port']}/actuator/health/readiness"]),
                "fresh discovered instance call", self.runner.logger.redactor)
            if called.get("status") != "UP":
                raise StartupError("Fresh client could not call the instance from its fetched registry")
            timeline.update(freshRegistration=registration, freshRegistry=fetched,
                            discoveredInstanceCall={"instanceId": target["instanceId"], "health": called["status"]},
                            freshClientReadyMs=round((time.monotonic() - started) * 1000))
            # Keep the outage long enough for existing clients' default 30s fetch
            # and heartbeat tasks to exercise the surviving node too.
            while time.monotonic() - started < self.args.observation_seconds:
                time.sleep(1)
            if not self.args.registry_only:
                samples = timeline["requests"]
                if len(samples) < 3 or not all(item["passed"] for item in samples[-3:]):
                    raise StartupError("Business read probes did not recover during the outage")
                timeline["readSuccessRate"] = sum(item["passed"] for item in samples) / len(samples)
        finally:
            stop.set()
            if sampler:
                sampler.join(timeout=10)
            # Restore the peer while the fault-period probe is still registered,
            # then verify that its new registration reaches the recovered peer.
            self.runner.stream(["docker", "start", container_id])
            self.stopped.discard(container_id)
        try:
            if name:
                timeline["recoveredRegistration"] = self.wait(lambda: self.registered(failed, name), "recovery synchronization")
            self.wait(self.baseline, "peer recovery")
            timeline.update(passed=True, recoveredMs=round((time.monotonic() - started) * 1000))
        finally:
            if name:
                self.remove_probe(name)

    def execute(self, values):
        try:
            containers = inspect_project(self.runner, self.deployment)
            if any(not owned_container(item, self.deployment) for item in containers):
                raise StartupError("HA verification project ownership mismatch")
            check_artifacts(self.deployment, self.runner)
            self.report["initial"] = self.wait(self.baseline, "initial HA readiness")
            if not self.args.registry_only:
                self.login(values)
            self.replication(*DISCOVERY)
            self.replication(*reversed(DISCOVERY))
            self.fault(*DISCOVERY)
            self.fault(*reversed(DISCOVERY))
            self.report["final"] = self.wait(self.baseline, "final HA readiness")
            self.report["passed"] = True
        finally:
            errors = []
            for container_id in list(self.stopped):
                try:
                    self.runner.stream(["docker", "start", container_id])
                    self.stopped.discard(container_id)
                except Exception as exc:
                    errors.append(type(exc).__name__)
            for name in list(self.probes):
                try:
                    self.remove_probe(name)
                except Exception as exc:
                    errors.append(type(exc).__name__)
            self.session.close()
            self.report["cleanupErrors"] = errors
            if errors:
                self.report["passed"] = False


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name", required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--admin-username")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--observation-seconds", type=int, default=65)
    parser.add_argument("--registry-only", action="store_true", help="Only registry/client checks; business continuity is explicitly skipped")
    parser.add_argument("--report-path", type=Path)
    add_deployment_arguments(parser)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", args.project_name) or args.timeout < 30 or args.observation_seconds < 30:
        parser.error("Use a valid project name and timeout/observation >=30 seconds")
    report = {"passed": False, "startedAt": datetime.now(timezone.utc).isoformat(), "checks": [], "faults": [],
              "businessContinuityChecked": not args.registry_only}
    logger = RunLogger(root, Redactor(), "eureka-ha")
    try:
        values = read_env(args.env_file)
        logger.redactor = Redactor(values)
        runner = CommandRunner(logger, root, child_environment(values, compose_files(root, args.compose_file), "history"))
        deployment = load_deployment(root, args, values, runner)
        report.update(deployment.summary())
        HAVerifier(args, deployment, runner, report).execute(values)
    except (Exception, KeyboardInterrupt) as exc:
        report.update(passed=False, error=logger.redactor.redact(f"{type(exc).__name__}: {exc}"))
        logger.write(report["error"], "ERROR")
    finally:
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        path = args.report_path or root / "verification/runs" / f"eureka-ha-{args.project_name}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(logger.redactor.redact(json.dumps(report, ensure_ascii=False, indent=2)) + "\n", encoding="utf-8")
        logger.write(f"Eureka HA {'PASS' if report['passed'] else 'FAIL'}: {path}")
        logger.close()
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
