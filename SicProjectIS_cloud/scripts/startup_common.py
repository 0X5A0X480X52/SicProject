from __future__ import annotations

import json
import os
import re
import shlex
import socket
import subprocess
import sys
import time
from collections import deque
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

SECRET_NAME = re.compile(r"PASSWORD|SECRET|KEY|TOKEN", re.I)
JWT = re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+")
QUERY_TOKEN = re.compile(r"([?&]token=)[^&\s]+", re.I)


class StartupError(RuntimeError):
    pass


class Redactor:
    def __init__(self, values: dict[str, str] | None = None):
        self.secrets = sorted(
            {v for k, v in (values or {}).items() if SECRET_NAME.search(k) and v},
            key=len, reverse=True,
        )

    def redact(self, value: object) -> str:
        text = str(value)
        for secret in self.secrets:
            text = text.replace(secret, "[REDACTED]")
        text = JWT.sub("[REDACTED-JWT]", text)
        return QUERY_TOKEN.sub(r"\1[REDACTED]", text)


class RunLogger:
    def __init__(self, root: Path, redactor: Redactor, prefix: str = "startup"):
        logs = root / "logs"
        logs.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        self.path = logs / f"{prefix}-{stamp}.log"
        self.latest = logs / ("latest.log" if prefix == "startup" else f"{prefix}-latest.log")
        self.redactor = redactor
        self._files = [self.path.open("w", encoding="utf-8"), self.latest.open("w", encoding="utf-8")]
        self._rotate(logs, prefix)

    def _rotate(self, logs: Path, prefix: str) -> None:
        old = sorted((path for path in logs.glob(f"{prefix}-*.log") if path != self.latest),
                     key=lambda p: p.stat().st_mtime, reverse=True)
        for path in old[20:]:
            try:
                path.unlink()
            except OSError:
                pass

    def write(self, message: object = "", level: str = "INFO") -> None:
        clean = self.redactor.redact(message)
        line = f"{datetime.now().astimezone().isoformat(timespec='seconds')} [{level}] {clean}"
        print(line, flush=True)
        for stream in self._files:
            stream.write(line + "\n")
            stream.flush()

    def raw(self, message: object) -> None:
        clean = self.redactor.redact(message)
        print(clean, flush=True)
        for stream in self._files:
            stream.write(clean + "\n")
            stream.flush()

    def close(self) -> None:
        for stream in self._files:
            stream.close()


def display_command(argv: Iterable[object]) -> str:
    args = [str(x) for x in argv]
    return subprocess.list2cmdline(args) if os.name == "nt" else shlex.join(args)


class CommandRunner:
    def __init__(self, logger: RunLogger, cwd: Path, env: dict[str, str]):
        self.logger, self.cwd, self.env = logger, cwd, env
        self.counter = 0
        self.last_command: list[str] | None = None
        self.last_output: list[str] = []

    def _begin(self, argv: list[str], cwd: Path) -> tuple[str, float]:
        self.counter += 1
        command_id = f"CMD {self.counter:03d}"
        self.last_command = argv
        self.logger.write(f"[{command_id}] cwd={cwd}", "CMD")
        self.logger.raw("$ " + self.logger.redactor.redact(display_command(argv)))
        return command_id, time.monotonic()

    def capture(self, argv: list[str], *, check: bool = True, cwd: Path | None = None) -> bytes:
        actual_cwd = cwd or self.cwd
        command_id, started = self._begin(argv, actual_cwd)
        completed = subprocess.run(argv, cwd=actual_cwd, env=self.env, capture_output=True, shell=False)
        duration = time.monotonic() - started
        out = completed.stdout + completed.stderr
        text = out.decode("utf-8", errors="replace")
        self.last_output = text.splitlines()[-100:]
        self.logger.write(f"[{command_id}] exit={completed.returncode} duration={duration:.1f}s", "CMD")
        if completed.returncode and check:
            for line in self.last_output:
                self.logger.raw(line)
            raise StartupError(
                f"Command failed: {display_command(argv)} (exit={completed.returncode}, duration={duration:.1f}s)"
            )
        return completed.stdout

    def stream(self, argv: list[str], *, cwd: Path | None = None) -> None:
        actual_cwd = cwd or self.cwd
        command_id, started = self._begin(argv, actual_cwd)
        process = subprocess.Popen(
            argv, cwd=actual_cwd, env=self.env, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, shell=False,
        )
        tail: deque[str] = deque(maxlen=100)
        buildkit_http2_warning = False
        assert process.stdout is not None
        for raw in iter(process.stdout.readline, b""):
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if "http2" in line.lower() and ("preface" in line.lower() or "server" in line.lower()):
                buildkit_http2_warning = True
            tail.append(line)
            self.logger.raw(line)
        code = process.wait()
        duration = time.monotonic() - started
        self.last_output = list(tail)
        self.logger.write(f"[{command_id}] exit={code} duration={duration:.1f}s", "CMD")
        if code == 0 and buildkit_http2_warning:
            self.logger.write("BuildKit emitted an HTTP/2 transport warning but returned exit code 0; continuing", "WARN")
        if code:
            raise StartupError(f"Command failed: {display_command(argv)} (exit={code}, duration={duration:.1f}s)")


def decode_utf8(data: bytes, source: str, redactor: Redactor) -> str:
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        snippet = redactor.redact(data[max(0, exc.start - 40):exc.start + 40].decode("utf-8", "replace"))
        raise StartupError(
            f"{source} is not UTF-8: byte={exc.start}, bytes={len(data)}, context={snippet!r}"
        ) from exc


def parse_json_bytes(data: bytes, source: str, redactor: Redactor) -> Any:
    text = decode_utf8(data, source, redactor).strip()
    if not text:
        raise StartupError(f"{source} returned empty JSON output")
    try:
        return json.loads(text)
    except json.JSONDecodeError as whole_error:
        try:
            return [json.loads(line) for line in text.splitlines() if line.strip()]
        except json.JSONDecodeError as line_error:
            pos = whole_error.pos
            snippet = redactor.redact(text[max(0, pos - 100):pos + 100])
            raise StartupError(
                f"Invalid JSON from {source}: line={whole_error.lineno}, column={whole_error.colno}, "
                f"char={pos}, bytes={len(data)}, context={snippet!r}; NDJSON error line={line_error.lineno}"
            ) from whole_error


def read_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise StartupError(f"EnvFile does not exist: {path}")
    values: dict[str, str] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)\s*=(.*)", line)
        if not match:
            raise StartupError(f"EnvFile line {number} must be KEY=value")
        key, value = match.group(1), match.group(2).strip()
        if key.startswith(("COMPOSE_", "DOCKER_")):
            raise StartupError(f"EnvFile cannot override Docker/Compose controls through {key}")
        if key in values:
            raise StartupError(f"EnvFile contains duplicate key {key}")
        if value.startswith("'"):
            quoted = re.fullmatch(r"'([^']*)'(?:\s+#.*)?", value)
            if not quoted:
                raise StartupError(f"Invalid single-quoted value for {key}")
            value = quoted.group(1)
        elif value.startswith('"'):
            quoted = re.fullmatch(r'"([^"\\$]*)"(?:\s+#.*)?', value)
            if not quoted:
                raise StartupError(f"Unsupported double-quoted value for {key}")
            value = quoted.group(1)
        else:
            value = re.sub(r"\s+#.*$", "", value).strip()
            if any(char in value for char in ("$", "'", '"')):
                raise StartupError(f"Use a literal single-quoted value for {key}")
        if "CHANGE_ME" in value:
            raise StartupError(f"Replace the CHANGE_ME placeholder for {key}")
        values[key] = value
    return values


def validate_env(values: dict[str, str], mode: str) -> None:
    required = {
        "MYSQL_ROOT_PASSWORD", "IAM_DB_PASSWORD", "PROJECT_DB_PASSWORD", "WORKFLOW_DB_PASSWORD",
        "MATERIAL_DB_PASSWORD", "NOTIFICATION_DB_PASSWORD", "RABBITMQ_USERNAME",
        "RABBITMQ_PASSWORD", "REDIS_PASSWORD", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY",
        "SERVICE_TOKEN_BOOTSTRAP_SECRET", "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY",
    }
    if mode == "fresh":
        required |= {"BOOTSTRAP_ADMIN_USERNAME", "BOOTSTRAP_ADMIN_PASSWORD"}
    missing = sorted(key for key in required if not values.get(key, "").strip())
    if missing:
        raise StartupError("EnvFile missing required values: " + ", ".join(missing))
    if mode == "fresh" and len(values["BOOTSTRAP_ADMIN_PASSWORD"]) < 10:
        raise StartupError("BOOTSTRAP_ADMIN_PASSWORD must contain at least 10 characters")
    if len(values["MINIO_ACCESS_KEY"]) < 3 or len(values["MINIO_SECRET_KEY"]) < 8:
        raise StartupError("MinIO access/secret keys must contain at least 3/8 characters")
    for key in ("IAM_DB_PASSWORD", "PROJECT_DB_PASSWORD", "WORKFLOW_DB_PASSWORD",
                "MATERIAL_DB_PASSWORD", "NOTIFICATION_DB_PASSWORD"):
        if re.search(r"['\\\r\n]", values[key]):
            raise StartupError(f"{key} cannot contain quotes or backslashes")


def compose_base(root: Path, env_file: Path, project: str) -> list[str]:
    return ["docker", "compose", "--project-directory", str(root), "--env-file", str(env_file),
            "-f", str(root / "docker-compose.yml"), "-p", project]


def expected_volumes(project: str) -> dict[str, str]:
    return {logical: f"{project}_{logical}" for logical in
            ("mysql-data", "rabbitmq-data", "redis-data", "minio-data")}


def label(item: dict[str, Any], name: str) -> str:
    labels = item.get("Labels") or (item.get("Config") or {}).get("Labels") or {}
    return str(labels.get(name, ""))


def inspect_state(runner: CommandRunner) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    ids = decode_utf8(runner.capture(["docker", "ps", "-aq"]), "docker ps", runner.logger.redactor).split()
    containers = parse_json_bytes(runner.capture(["docker", "inspect", *ids]), "docker inspect", runner.logger.redactor) if ids else []
    names = decode_utf8(runner.capture(["docker", "volume", "ls", "-q"]), "docker volume ls", runner.logger.redactor).split()
    volumes = parse_json_bytes(runner.capture(["docker", "volume", "inspect", *names]), "docker volume inspect", runner.logger.redactor) if names else []
    return containers, volumes


def validate_storage(project: str, containers: list[dict[str, Any]], volumes: list[dict[str, Any]], history: bool) -> None:
    expected = expected_volumes(project)
    valid_services = {"mysql", "rabbitmq", "redis", "minio", "discovery-server", "discovery-peer", "config-server",
                      "iam-service", "project-service", "workflow-service", "material-service",
                      "notification-service", "gateway-service"}
    found = {volume.get("Name") for volume in volumes}
    for volume in volumes:
        name, owner = volume.get("Name", ""), label(volume, "com.docker.compose.project")
        if name in expected.values():
            logical = label(volume, "com.docker.compose.volume")
            if owner != project or expected.get(logical) != name:
                raise StartupError(f"Volume {name} has mismatched Compose ownership labels")
        elif owner == project:
            raise StartupError(f"Unexpected project volume {name}")
    for container in containers:
        owner = label(container, "com.docker.compose.project")
        service = label(container, "com.docker.compose.service")
        if owner == project and service not in valid_services:
            raise StartupError(f"Unexpected project container {container.get('Name')}")
        for mount in container.get("Mounts") or []:
            if mount.get("Type") != "volume":
                continue
            name = mount.get("Name")
            if name in expected.values() and owner != project:
                raise StartupError(f"Volume {name} is attached to another container {container.get('Name')}")
            if owner == project and name not in expected.values():
                raise StartupError(f"Project container {container.get('Name')} has unexpected volume {name}")
    if history:
        missing = [name for name in expected.values() if name not in found]
        if missing:
            raise StartupError("History requires all four volumes; missing: " + ", ".join(missing))


def validate_ports(project: str, containers: list[dict[str, Any]]) -> None:
    required = {8080, 8761, 8762, 8888, 9000, 9006, 15672}
    own: set[int] = set()
    for container in containers:
        if not (container.get("State") or {}).get("Running"):
            continue
        for bindings in ((container.get("NetworkSettings") or {}).get("Ports") or {}).values():
            for binding in bindings or []:
                port = int(binding["HostPort"])
                if port in required:
                    if label(container, "com.docker.compose.project") != project:
                        raise StartupError(f"Port {port} is used by {container.get('Name')}")
                    own.add(port)
    for port in required - own:
        sock = socket.socket()
        try:
            sock.bind(("0.0.0.0", port))
        except OSError as exc:
            raise StartupError(f"Host port {port} cannot be bound: {exc}") from exc
        finally:
            sock.close()
