from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

if sys.version_info < (3, 11):
    print("Python 3.11 or newer is required.", file=sys.stderr)
    raise SystemExit(2)

try:
    import requests
except ImportError:
    print(f"Missing dependency 'requests'. Run: {sys.executable} -m pip install -r "
          f"{Path(__file__).with_name('requirements-startup.txt')}", file=sys.stderr)
    raise SystemExit(2)

from startup_common import CommandRunner, Redactor, RunLogger, StartupError, compose_base, parse_json_bytes
from replica_common import (add_deployment_arguments, load_deployment, inspect_project, check_container_health,
                            internal_json, check_registry, wait_instances, compose_files, child_environment)

EXPECTED_SERVICES = ["mysql", "rabbitmq", "redis", "minio", "discovery-server", "config-server",
                     "iam-service", "project-service", "workflow-service", "material-service",
                     "notification-service", "gateway-service"]


class Verifier:
    def __init__(self, args: argparse.Namespace, runner: CommandRunner, logger: RunLogger,
                 username: str, password: str):
        self.args, self.runner, self.logger = args, runner, logger
        self.username, self.password = username, password
        self.gateway = args.gateway_base_url.rstrip("/")
        self.config = args.config_server_base_url.rstrip("/") if args.config_server_base_url else None
        self.discovery = args.discovery_base_url.rstrip("/") if args.discovery_base_url else None
        self.session = requests.Session()
        self.results: list[dict[str, Any]] = []
        self.token = ""

    def request(self, method: str, url: str, *, token: str = "", body: Any = None,
                stream: bool = False) -> requests.Response:
        safe_url = self.logger.redactor.redact(url)
        self.logger.write(f"HTTP {method} {safe_url}", "HTTP")
        headers = {"Accept": "text/event-stream" if stream else "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        response = self.session.request(method, url, headers=headers, json=body, stream=stream,
                                        timeout=self.args.request_timeout)
        self.logger.write(f"HTTP {method} {safe_url} -> {response.status_code}", "HTTP")
        return response

    def gateway_get(self, path: str, token: str = "") -> requests.Response:
        deadline = time.monotonic() + self.args.start_timeout
        while True:
            response = self.request("GET", self.gateway + path, token=token)
            if response.status_code != 503 or time.monotonic() >= deadline:
                return response
            response.close()
            time.sleep(2)

    def api(self, response: requests.Response, status: int, success: bool) -> dict[str, Any]:
        body = response.text
        if response.status_code != status:
            raise StartupError(f"Expected HTTP {status}, received {response.status_code}: {body}")
        try:
            payload = response.json()
        except ValueError as exc:
            raise StartupError(f"Response is not JSON: {body[:500]}") from exc
        if bool(payload.get("success")) != success:
            raise StartupError(f"Expected success={success}: {body}")
        trace = response.headers.get("X-Trace-Id", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{8,80}", trace):
            raise StartupError("X-Trace-Id is missing or invalid")
        return payload

    def check(self, name: str, action: Callable[[], None]) -> None:
        started = time.monotonic()
        try:
            action()
            duration = int((time.monotonic() - started) * 1000)
            self.results.append({"name": name, "passed": True, "durationMs": duration, "error": None})
            self.logger.write(f"[PASS] {name} ({duration}ms)", "CHECK")
        except Exception as exc:
            duration = int((time.monotonic() - started) * 1000)
            error = self.logger.redactor.redact(exc)
            self.results.append({"name": name, "passed": False, "durationMs": duration, "error": error})
            self.logger.write(f"[FAIL] {name}: {error}", "ERROR")
            raise

    def readiness(self) -> None:
        deadline = time.monotonic() + self.args.start_timeout
        while time.monotonic() < deadline:
            try:
                response = self.request("GET", self.gateway + "/actuator/health/readiness")
                if response.status_code == 200 and response.json().get("status") == "UP":
                    return
            except requests.RequestException:
                pass
            time.sleep(2)
        raise StartupError("Gateway readiness timeout")

    def container_health(self) -> None:
        deployment = self.args.deployment
        self.instance_evidence = check_container_health(inspect_project(self.runner, deployment), deployment.replicas)

    def config_discovery(self) -> None:
        if self.config:
            response = self.request("GET", self.config + "/application/default")
            if response.status_code != 200:
                raise StartupError("Config Server response failed")
            config = response.json()
        else:
            config = internal_json(self.runner, self.args.deployment, "config-server", "/application/default", 8888)
        if not config.get("propertySources"):
            raise StartupError("Config Server returned no property sources")
        if self.discovery:
            response = self.request("GET", self.discovery + "/eureka/apps")
            if response.status_code != 200:
                raise StartupError("Discovery response failed")
            check_registry(inspect_project(self.runner, self.args.deployment), response.json(), self.args.deployment.replicas)
        self.registry_evidence = wait_instances(self.runner, self.args.deployment, self.args.start_timeout, stable_seconds=0)
        response = self.gateway_get("/api/auth/login")
        if response.status_code == 503:
            raise StartupError("Gateway IAM route did not become available")

    def unauthenticated(self) -> None:
        payload = self.api(self.gateway_get("/api/projects"), 401, False)
        if payload.get("data") is not None or not payload.get("message"):
            raise StartupError("401 response wrapper is invalid")

    @staticmethod
    def jwt_payload(token: str) -> dict[str, Any]:
        parts = token.split(".")
        if len(parts) != 3:
            raise StartupError("Login token is not a compact JWT")
        return json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))

    def login(self) -> None:
        payload = self.api(self.request("POST", self.gateway + "/api/auth/login",
                                       body={"username": self.username, "password": self.password}), 200, True)
        data = payload.get("data") or {}
        self.token = str(data.get("token") or "")
        self.logger.redactor.secrets.insert(0, self.token)
        if not self.token or not data.get("expiresAt") or (data.get("user") or {}).get("username") != self.username:
            raise StartupError("Login response is incomplete")
        claims = self.jwt_payload(self.token)
        audience = claims.get("aud", [])
        audience = [audience] if isinstance(audience, str) else audience
        if claims.get("iss") != self.args.expected_jwt_issuer or self.args.expected_jwt_audience not in audience:
            raise StartupError("JWT issuer/audience mismatch")
        if not claims.get("jti") or claims.get("authVersion") is None or "SYSTEM_ADMIN" not in claims.get("roles", []):
            raise StartupError("JWT required claims or SYSTEM_ADMIN role missing")

    def service_routes(self) -> None:
        checks = [("/api/auth/me", 200), ("/api/projects", 200),
                  ("/api/workflow-workbench/items", 200), ("/api/admin/overview", 200),
                  ("/api/material-versions/9223372036854775807/download", 404)]
        payloads = [self.api(self.gateway_get(path, self.token), status, status == 200) for path, status in checks]
        if payloads[0]["data"].get("username") != self.username:
            raise StartupError("Current-user route returned another user")
        if payloads[1].get("data") is None or payloads[2].get("data") is None:
            raise StartupError("Project/workbench data is null")
        if int(payloads[3]["data"].get("totalUsers", 0)) < 1:
            raise StartupError("IAM overview did not include an administrator")

    def sse(self) -> None:
        url = self.gateway + "/api/sse/subscribe?token=" + requests.utils.quote(self.token, safe="")
        response = self.request("GET", url, stream=True)
        try:
            if response.status_code != 200 or "text/event-stream" not in response.headers.get("Content-Type", ""):
                raise StartupError(f"SSE returned HTTP {response.status_code}")
            lines: list[str] = []
            for line in response.iter_lines(decode_unicode=True):
                lines.append(line or "")
                if not line and len(lines) > 1:
                    break
                if len(lines) >= 8:
                    break
            if "event:CONNECTED" not in lines or not any('"connected":true' in line.replace(" ", "") for line in lines):
                raise StartupError("SSE CONNECTED event/payload missing")
        finally:
            response.close()

    def logout(self) -> None:
        payload = self.api(self.request("POST", self.gateway + "/api/auth/logout", token=self.token), 200, True)
        if payload.get("data") is not None:
            raise StartupError("Logout data must be null")
        self.api(self.request("GET", self.gateway + "/api/auth/me", token=self.token), 401, False)

    def run(self) -> dict[str, Any]:
        actions = [("gateway-readiness", self.readiness), ("compose-container-health", self.container_health),
                   ("config-and-discovery", self.config_discovery),
                   ("gateway-unauthenticated-wrapper", self.unauthenticated),
                   ("admin-login-and-jwt", self.login), ("authenticated-service-routes", self.service_routes),
                   ("sse-query-token-relay", self.sse), ("logout-jti-blacklist", self.logout)]
        failure = None
        try:
            for name, action in actions:
                self.check(name, action)
        except Exception as exc:
            failure = self.logger.redactor.redact(exc)
        finally:
            self.session.close()
        return {"passed": failure is None, "projectName": self.args.project_name,
                "checkedAt": datetime.now(timezone.utc).isoformat(), "checks": self.results, "error": failure,
                "expectedReplicas": self.args.deployment.replicas,
                "instances": getattr(self, "registry_evidence", None)}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Verify the complete SIC Cloud Compose stack")
    parser.add_argument("--project-name", default="sic-cloud")
    parser.add_argument("--env-file")
    add_deployment_arguments(parser)
    parser.add_argument("--config-server-base-url")
    parser.add_argument("--discovery-base-url")
    parser.add_argument("--expected-jwt-issuer", default="sic-iam")
    parser.add_argument("--expected-jwt-audience", default="sic-api")
    parser.add_argument("--admin-username", required=True)
    parser.add_argument("--start-timeout", type=int, default=300)
    parser.add_argument("--request-timeout", type=int, default=20)
    parser.add_argument("--report-path")
    return parser


def run_verification(args: argparse.Namespace, runner: CommandRunner, logger: RunLogger,
                     username: str, password: str) -> dict[str, Any]:
    if not getattr(args, "deployment", None):
        from startup_common import read_env
        args.deployment = load_deployment(runner.cwd, args, read_env(Path(args.env_file)), runner)
    args.compose = args.deployment.compose
    args.gateway_base_url = args.deployment.gateway
    report = Verifier(args, runner, logger, username, password).run()
    if args.report_path:
        path = Path(args.report_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def main() -> int:
    args = build_parser().parse_args()
    root = Path(__file__).resolve().parent.parent
    args.env_file = args.env_file or str(root / ".env")
    password = os.environ.pop("SIC_STARTUP_ADMIN_PASSWORD", "")
    if not password:
        import getpass
        password = getpass.getpass("Administrator password: ")
    logger = RunLogger(root, Redactor({"ADMIN_PASSWORD": password}), "verification")
    from startup_common import read_env
    values = read_env(Path(args.env_file))
    logger.redactor = Redactor({**values, "ADMIN_PASSWORD": password})
    runner = CommandRunner(logger, root, child_environment(values, compose_files(root, args.compose_file), "history"))
    try:
        report = run_verification(args, runner, logger, args.admin_username, password)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["passed"] else 1
    except Exception as exc:
        logger.write(str(exc), "ERROR")
        return 1
    finally:
        logger.close()


if __name__ == "__main__":
    raise SystemExit(main())
