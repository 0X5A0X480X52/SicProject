"""Shared deployment configuration and per-instance inspection for SIC lifecycle tools."""
from __future__ import annotations

import hashlib
import errno
import json
import os
import re
import socket
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from startup_common import StartupError, label, parse_json_bytes

DISCOVERY = ["discovery-server", "discovery-peer"]
SERVICES = ["mysql", "rabbitmq", "redis", "minio", *DISCOVERY, "config-server",
            "iam-service", "project-service", "workflow-service", "material-service",
            "notification-service", "gateway-service"]
BUSINESS = ["iam-service", "project-service", "workflow-service", "material-service", "notification-service"]
APPLICATIONS = [*DISCOVERY, "config-server", *BUSINESS, "gateway-service"]
ARTIFACT_NAMES = {"discovery-peer": "discovery-server"}
INFRASTRUCTURE = [name for name in SERVICES if name not in [*BUSINESS, "gateway-service"]]
REPLICA_ENV = {service: "SIC_" + service.removesuffix("-service").upper() + "_REPLICAS" for service in BUSINESS}


def add_deployment_arguments(parser):
    parser.add_argument("--compose-file", action="append", type=Path, help="Additional Compose overrides, in order; base Compose is always included")
    parser.add_argument("--replica-config", type=Path, help="JSON service-to-positive-integer replica map")
    parser.add_argument("--replicas", action="append", default=[], metavar="SERVICE=N")
    parser.add_argument("--gateway-base-url", help="Override the public gateway URL inferred from its published port")


def compose_files(root, additional):
    base = (root / "docker-compose.yml").resolve()
    result = [base]
    for value in additional or []:
        path = Path(value).resolve()
        if not path.is_file():
            raise StartupError(f"Compose file does not exist: {path}")
        if path not in result:
            result.append(path)
    return result


def compose_command(root, env_file, project, files):
    result = ["docker", "compose", "--project-directory", str(root), "--env-file", str(env_file), "-p", project]
    for path in files:
        result += ["-f", str(path)]
    return result


def child_environment(values, files, mode=None):
    result = os.environ.copy()
    for key in ("COMPOSE_PROFILES", "COMPOSE_FILE", "COMPOSE_ENV_FILES", "COMPOSE_PROJECT_NAME"):
        result.pop(key, None)
    # An explicit credentials file is authoritative for Compose interpolation.
    for path in files:
        for key in set(re.findall(r"(?<!\$)\$\{([A-Z][A-Z0-9_]*)", path.read_text(encoding="utf-8-sig"))):
            if key in values:
                result[key] = values[key]
            elif re.search(r"PASSWORD|SECRET|KEY|TOKEN", key):
                result.pop(key, None)
    if mode == "history":
        result.update(BOOTSTRAP_ADMIN_USERNAME=" ", BOOTSTRAP_ADMIN_PASSWORD=" ")
    return result


def positive_count(value, context):
    if isinstance(value, bool) or not isinstance(value, (int, str)) or not re.fullmatch(r"[1-9][0-9]*", str(value)):
        raise StartupError(f"{context} must be a positive integer")
    return int(value)


def strict_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise StartupError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=pairs)


def resolve_replicas(config, values, process_env, replica_file=None, cli=None):
    replicas, sources = {name: 1 for name in SERVICES}, {name: "default" for name in SERVICES}

    def apply(mapping, source):
        if not isinstance(mapping, dict):
            raise StartupError(f"{source} must be a service-to-count object")
        for service, value in mapping.items():
            if service not in SERVICES:
                raise StartupError(f"Unknown replica service: {service}")
            count = positive_count(value, f"{source}:{service}")
            if service not in BUSINESS and count != 1:
                raise StartupError(f"{service} must remain single-instance; only {', '.join(BUSINESS)} can scale")
            replicas[service], sources[service] = count, source

    declared = {}
    for service, definition in config.get("services", {}).items():
        scale, deployed = definition.get("scale"), definition.get("deploy", {}).get("replicas")
        if scale is not None and deployed is not None and scale != deployed:
            raise StartupError(f"Conflicting Compose scale/deploy.replicas for {service}")
        if scale is not None or deployed is not None:
            declared[service] = scale if scale is not None else deployed
    apply(declared, "compose")
    for env, source in ((process_env, "process-environment"), (values, "env-file")):
        unknown = [key for key in env if key.startswith("SIC_") and key.endswith("_REPLICAS") and key not in REPLICA_ENV.values()]
        if unknown:
            raise StartupError("Unknown replica variables: " + ", ".join(sorted(unknown)))
        apply({service: env[key] for service, key in REPLICA_ENV.items() if key in env}, source)
    if replica_file:
        path = Path(replica_file).resolve()
        try:
            apply(strict_json(path.read_text(encoding="utf-8-sig")), f"replica-config:{path}")
        except (ValueError, OSError) as exc:
            raise StartupError(f"Invalid replica config: {path}: {type(exc).__name__}") from exc
    overrides = {}
    for item in cli or []:
        if "=" not in item:
            raise StartupError("--replicas requires SERVICE=N")
        service, value = item.split("=", 1)
        if service in overrides:
            raise StartupError(f"Duplicate --replicas service: {service}")
        overrides[service] = value
    apply(overrides, "command-line")
    return replicas, sources


@dataclass
class Deployment:
    root: Path
    project: str
    env_file: Path
    files: list[Path]
    compose: list[str]
    config: dict[str, Any]
    replicas: dict[str, int]
    sources: dict[str, str]
    gateway: str

    def scale_arguments(self, single=False):
        return [arg for service in SERVICES for arg in ("--scale", f"{service}={1 if single else self.replicas[service]}")]

    def summary(self):
        artifacts = {}
        for service in APPLICATIONS:
            definition = self.config["services"][service]
            jar = next((volume.get("source") for volume in definition.get("volumes", [])
                        if volume.get("type") == "bind" and volume.get("target") == "/application/application.jar"), None)
            artifacts[service] = {"image": definition.get("image", f"{self.project}-{service}"), "jar": jar}
            if jar and Path(jar).is_file():
                artifacts[service]["sha256"] = hashlib.sha256(Path(jar).read_bytes()).hexdigest()
        return {"projectName": self.project, "envFile": str(self.env_file), "composeFiles": [str(p) for p in self.files],
                "replicas": self.replicas, "replicaSources": self.sources, "gatewayBaseUrl": self.gateway,
                "artifacts": artifacts, "deploymentMode": "jar" if any(a["jar"] for a in artifacts.values()) else "image"}


def load_deployment(root, args, values, runner, process_env=None):
    files = compose_files(root, getattr(args, "compose_file", None))
    env_file = Path(args.env_file).resolve()
    command = compose_command(root, env_file, args.project_name, files)
    config = parse_json_bytes(runner.capture(command + ["config", "--format", "json"]), "Compose definition", runner.logger.redactor)
    if set(config.get("services", {})) != set(SERVICES):
        raise StartupError("Compose must define the thirteen HA services without operation/migration profiles")
    if set(config.get("volumes", {})) != {"mysql-data", "rabbitmq-data", "redis-data", "minio-data"}:
        raise StartupError("Compose must retain the existing four managed data volumes")
    for service in BUSINESS:
        definition = config["services"][service]
        if definition.get("container_name"):
            raise StartupError(f"Remove fixed container_name from scalable service {service}")
        if definition.get("network_mode") == "host" or definition.get("ports"):
            raise StartupError(f"Scalable service {service} must use the Compose network without published host ports")
        environment = definition.get("environment", {})
        for key in (service.removesuffix("-service").upper() + "_DB_URL", "SPRING_DATASOURCE_URL"):
            url = environment.get(key, "")
            if url and not re.fullmatch(r"jdbc:mysql://mysql:3306/sic_" + service.removesuffix("-service") + r"(?:\?.*)?", str(url)):
                raise StartupError(f"{service} must use its existing schema on Docker-managed MySQL in this iteration")
    replicas, sources = resolve_replicas(config, values, process_env if process_env is not None else os.environ,
                                        getattr(args, "replica_config", None), getattr(args, "replicas", []))
    ports = config["services"]["gateway-service"].get("ports", [])
    gateway = getattr(args, "gateway_base_url", None)
    if not gateway:
        port = next((item for item in ports if int(item["target"]) == 8080 and item.get("published")), None)
        if not port:
            raise StartupError("Gateway must publish port 8080, or provide --gateway-base-url")
        host = port.get("host_ip", "127.0.0.1")
        host = "127.0.0.1" if host in ("0.0.0.0", "::", "") else host
        gateway = f"http://{'[' + host + ']' if ':' in host else host}:{port['published']}"
    return Deployment(root, args.project_name, env_file, files, command, config, replicas, sources, gateway.rstrip("/"))


def inspect_project(runner, deployment, include_stopped=True):
    command = ["docker", "ps", "-aq" if include_stopped else "-q", "--filter", f"label=com.docker.compose.project={deployment.project}"]
    ids = runner.capture(command).decode().split()
    if not ids:
        return []
    rows = parse_json_bytes(runner.capture(["docker", "inspect", *ids]), "project containers", runner.logger.redactor)
    return rows if isinstance(rows, list) else [rows]


def owned_container(container, deployment):
    if label(container, "com.docker.compose.project") != deployment.project:
        return False
    workdir = label(container, "com.docker.compose.project.working_dir")
    files = label(container, "com.docker.compose.project.config_files").split(",")
    return (workdir and Path(workdir).resolve() == deployment.root.resolve()) or any(
        value and Path(value).resolve() in deployment.files for value in files)


def instance_rows(containers):
    result = []
    for item in containers:
        state = item.get("State", {})
        result.append({"containerId": item["Id"], "containerName": item.get("Name", "").lstrip("/"),
                       "hostname": item.get("Config", {}).get("Hostname", ""),
                       "image": item.get("Config", {}).get("Image"), "imageId": item.get("Image"),
                       "service": label(item, "com.docker.compose.service"), "running": state.get("Running", False),
                       "health": state.get("Health", {}).get("Status", "none"),
                       "addresses": [network["IPAddress"] for network in item.get("NetworkSettings", {}).get("Networks", {}).values()
                                     if network.get("IPAddress")]})
    return sorted(result, key=lambda row: (row["service"], row["containerName"]))


def check_container_health(containers, expected):
    rows = instance_rows(containers)
    for service, count in expected.items():
        selected = [row for row in rows if row["service"] == service and row["running"]]
        if len(selected) != count or any(row["health"] != "healthy" for row in selected):
            raise StartupError(f"{service}: expected {count} healthy running replicas; actual {[(r['containerName'], r['health']) for r in selected]}")
    return rows


def internal_json(runner, deployment, service, path, port):
    body = runner.capture(deployment.compose + ["exec", "-T", "--index", "1", service, "wget", "-T", "5", "-qO-",
                          "--header=Accept: application/json", f"http://127.0.0.1:{port}{path}"])
    return parse_json_bytes(body, f"{service} internal HTTP", runner.logger.redactor)


def registry_rows(registry):
    apps = registry.get("applications", {}).get("application", [])
    apps = [apps] if isinstance(apps, dict) else apps
    rows = []
    for app in apps:
        instances = app.get("instance", [])
        instances = [instances] if isinstance(instances, dict) else instances
        for item in instances:
            port = item.get("port", {})
            rows.append({"service": app["name"].lower(), "instanceId": item.get("instanceId"), "ip": item.get("ipAddr"),
                         "port": int(port.get("$", 0) if isinstance(port, dict) else port), "status": item.get("status")})
    return rows


def check_registries(runner, deployment, containers, expected=None):
    """Check every expected peer, including one-to-one business instance mappings."""
    expected = expected if expected is not None else deployment.replicas
    result = {}
    for node in DISCOVERY:
        if expected.get(node, 0) > 0:
            registry = internal_json(runner, deployment, node, "/eureka/apps", 8761)
            result[node] = check_registry(containers, registry, expected)
            peer_ids = {row["instanceId"] for row in result[node] if row["service"] == "discovery-server" and row["status"] == "UP"}
            required = {f"{peer}:8761" for peer in DISCOVERY if expected.get(peer, 0) > 0}
            if not required <= peer_ids:
                raise StartupError(f"{node}: missing UP Eureka peers: {sorted(required - peer_ids)}")
    if not result:
        raise StartupError("At least one Eureka peer must be available")
    signatures = [sorted((row["service"], row["instanceId"], row["ip"], row["port"], row["status"])
                         for row in rows if row["service"] in [*BUSINESS, "gateway-service"])
                  for rows in result.values()]
    if any(signature != signatures[0] for signature in signatures[1:]):
        raise StartupError("Eureka peers have not converged on the same business registrations")
    return result


def check_registry(containers, registry, expected):
    running = instance_rows(containers)
    registrations = registry_rows(registry)
    ids = [row["instanceId"] for row in registrations]
    if len(ids) != len(set(ids)):
        raise StartupError("Duplicate Eureka instance IDs")
    for service in [*BUSINESS, "gateway-service"]:
        selected = [row for row in registrations if row["service"] == service]
        if len(selected) != expected[service] or any(row["status"] != "UP" or not row["instanceId"] for row in selected):
            raise StartupError(f"Eureka {service}: expected {expected[service]} UP instances, actual {selected}")
        if len({row["instanceId"] for row in selected}) != len(selected):
            raise StartupError(f"Duplicate Eureka instance IDs for {service}")
        candidates = [row for row in running if row["service"] == service and row["running"]]
        matched_ids = set()
        for row in selected:
            matches = [candidate for candidate in candidates if row["ip"] in candidate["addresses"]]
            configured_port = {"iam-service": 9001, "project-service": 9002, "workflow-service": 9003,
                               "material-service": 9004, "notification-service": 9005, "gateway-service": 8080}[service]
            if len(matches) != 1 or row["port"] != configured_port:
                raise StartupError(f"Eureka instance address/port does not match an actual container: {row}")
            row["containerId"] = matches[0]["containerId"]
            matched_ids.add(matches[0]["containerId"])
        if matched_ids != {row["containerId"] for row in candidates}:
            raise StartupError(f"Eureka {service}: registrations must map one-to-one to every running replica")
    return registrations


def wait_instances(runner, deployment, timeout=600, expected=None, stable_seconds=65):
    expected = expected or deployment.replicas
    deadline, stable, last_error = time.monotonic() + timeout, None, "not checked"
    last_log = 0.0
    while time.monotonic() < deadline:
        try:
            containers = inspect_project(runner, deployment)
            check_container_health(containers, expected)
            registries = check_registries(runner, deployment, containers, expected)
            if stable is None:
                stable = time.monotonic()
            if time.monotonic() - stable >= stable_seconds:
                return {"containers": instance_rows(containers), "registrations": next(iter(registries.values())),
                        "registries": registries}
        except (StartupError, ValueError, KeyError) as exc:
            stable, last_error = None, str(exc)
        if time.monotonic() - last_log >= 10:
            runner.logger.write(f"Waiting for every replica and registry propagation: {last_error}")
            last_log = time.monotonic()
        time.sleep(2)
    raise StartupError(f"Per-instance readiness timed out: {last_error}")


def validate_deployment_ports(project, containers, config):
    desired = {(port.get("host_ip", "0.0.0.0"), int(port["published"])) for definition in config["services"].values()
               for port in definition.get("ports", []) if port.get("published")}
    own = set()
    stopped_ports = {int(binding["HostPort"]) for container in containers
        if label(container, "com.docker.compose.project") == project and not container.get("State", {}).get("Running")
        for bindings in container.get("HostConfig", {}).get("PortBindings", {}).values()
        for binding in bindings or [] if binding.get("HostPort")}
    for container in containers:
        if not container.get("State", {}).get("Running"):
            continue
        for bindings in container.get("NetworkSettings", {}).get("Ports", {}).values():
            for binding in bindings or []:
                pair = (binding.get("HostIp", "0.0.0.0"), int(binding["HostPort"]))
                if any(pair[1] == p[1] for p in desired):
                    if label(container, "com.docker.compose.project") != project:
                        raise StartupError(f"Published port {pair[1]} belongs to another project")
                    own.add(pair[1])
    for host, port in desired:
        if port in own:
            continue
        # Docker Desktop 的端口转发可能晚于 stop 返回释放；只为本项目已停止的绑定有限等待。
        deadline = time.monotonic() + (20 if port in stopped_ports else 0)
        while True:
            sock = socket.socket(socket.AF_INET6 if ":" in host else socket.AF_INET)
            try:
                sock.bind((host, port))
                break
            except OSError as exc:
                if exc.errno not in (errno.EADDRINUSE, 10048) or time.monotonic() >= deadline:
                    raise StartupError(f"Published port {host}:{port} cannot be bound") from exc
            finally:
                sock.close()
            time.sleep(.5)


def connection_budget(deployment, runner):
    total = 0
    for service in BUSINESS:
        environment = deployment.config["services"][service].get("environment", {})
        value = environment.get("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", environment.get("DB_POOL_SIZE", "10"))
        total += positive_count(value, f"DB_POOL_SIZE:{service}") * deployment.replicas[service]
    limit = int(runner.capture(deployment.compose + ["exec", "-T", "mysql", "sh", "-lc",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "SELECT @@max_connections"']).decode().strip())
    runner.logger.write(f"MySQL connection budget: application={total}, reserve=20, max_connections={limit}")
    if total + 20 > limit:
        raise StartupError("Replica connection pools exceed MySQL max_connections; reduce DB_POOL_SIZE or explicitly configure MySQL")
    return {"applicationMaximum": total, "operationsReserve": 20, "mysqlMaxConnections": limit}


def check_artifacts(deployment, runner, skip_build=False):
    for service, artifact in deployment.summary()["artifacts"].items():
        jar = artifact["jar"]
        if jar:
            path = Path(jar).resolve()
            if not path.is_file():
                raise StartupError(f"Missing mounted JAR for {service}: {path}")
            manifest_path = path.parent.parent / "release-manifest.json"
            if manifest_path.is_file():
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                entry = manifest.get("applications", {}).get(ARTIFACT_NAMES.get(service, service))
                if not entry or (manifest_path.parent / entry["file"]).resolve() != path or entry["sha256"] != artifact["sha256"]:
                    raise StartupError(f"Frozen release manifest/hash mismatch for {service}")
        elif skip_build:
            runner.capture(["docker", "image", "inspect", artifact["image"]])
