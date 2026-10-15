from __future__ import annotations

import argparse, base64, getpass, os, re, shutil, sys, tempfile, json
from datetime import datetime, timezone
from pathlib import Path

if sys.version_info < (3, 11):
    print("Python 3.11 or newer is required.", file=sys.stderr)
    raise SystemExit(2)

try:
    import requests  # noqa: F401
except ImportError:
    print(f"Missing dependency 'requests'. Run: {sys.executable} -m pip install -r {Path(__file__).with_name('requirements-startup.txt')}", file=sys.stderr)
    raise SystemExit(2)

from startup_common import (CommandRunner, Redactor, RunLogger, StartupError, compose_base,
                            decode_utf8, expected_volumes, inspect_state, label, parse_json_bytes,
                            read_env, validate_env, validate_storage)
from verify_compose_e2e import run_verification
from replica_common import (add_deployment_arguments, compose_files, child_environment, load_deployment,
                            check_artifacts, validate_deployment_ports, connection_budget, wait_instances, SERVICES, INFRASTRUCTURE, owned_container)


def build_command(compose):
    return compose + ["--progress", "plain", "build", *SERVICES]


def validate_reset_confirmation(value, project):
    if value != f"RESET:{project}":
        raise StartupError("Reset confirmation did not match; no data was deleted")


def validate_rsa(values, runner):
    try:
        private = base64.b64decode(values["JWT_PRIVATE_KEY"], validate=True)
        public = base64.b64decode(values["JWT_PUBLIC_KEY"], validate=True)
    except ValueError as exc:
        raise StartupError("JWT keys must be single-line Base64 DER values") from exc
    if bytes.fromhex("300d06092a864886f70d0101010500") not in private:
        raise StartupError("JWT_PRIVATE_KEY must be a PKCS#8 RSA key")
    with tempfile.TemporaryDirectory(prefix="sic-jwt-check-") as directory:
        path = Path(directory) / "private.der"
        path.write_bytes(private)
        derived = runner.capture(["openssl", "pkey", "-inform", "DER", "-in", str(path), "-pubout", "-outform", "DER"])
        if derived != public:
            raise StartupError("JWT_PUBLIC_KEY does not match JWT_PRIVATE_KEY")


def validate_compose_definition(runner, compose):
    runner.capture(compose + ["config", "--quiet"])
    actual = set(decode_utf8(runner.capture(compose + ["config", "--services"]), "compose services", runner.logger.redactor).split())
    if actual != set(SERVICES):
        raise StartupError(f"Compose services mismatch: expected={sorted(SERVICES)}, actual={sorted(actual)}")
    volumes = set(decode_utf8(runner.capture(compose + ["config", "--volumes"]), "compose volumes", runner.logger.redactor).split())
    expected = {"mysql-data", "rabbitmq-data", "redis-data", "minio-data"}
    if volumes != expected:
        raise StartupError(f"Compose volumes mismatch: expected={sorted(expected)}, actual={sorted(volumes)}")


def expected_container_environment(service, values):
    common = {key: values.get(key, "") for key in ("RABBITMQ_USERNAME", "RABBITMQ_PASSWORD", "REDIS_PASSWORD", "JWT_ISSUER", "JWT_AUDIENCE", "SERVICE_TOKEN_BOOTSTRAP_SECRET")}
    mapping = {
        "mysql": {key: values.get(key, "") for key in ("MYSQL_ROOT_PASSWORD", "IAM_DB_PASSWORD", "PROJECT_DB_PASSWORD", "WORKFLOW_DB_PASSWORD", "MATERIAL_DB_PASSWORD", "NOTIFICATION_DB_PASSWORD")},
        "rabbitmq": {"RABBITMQ_DEFAULT_USER": values.get("RABBITMQ_USERNAME", ""), "RABBITMQ_DEFAULT_PASS": values.get("RABBITMQ_PASSWORD", "")},
        "redis": {"REDIS_PASSWORD": values.get("REDIS_PASSWORD", "")},
        "minio": {"MINIO_ROOT_USER": values.get("MINIO_ACCESS_KEY", ""), "MINIO_ROOT_PASSWORD": values.get("MINIO_SECRET_KEY", "")},
        "iam-service": {**common, "IAM_DB_PASSWORD": values.get("IAM_DB_PASSWORD", ""), "JWT_PRIVATE_KEY": values.get("JWT_PRIVATE_KEY", ""), "JWT_PUBLIC_KEY": values.get("JWT_PUBLIC_KEY", "")},
        "project-service": {**common, "PROJECT_DB_PASSWORD": values.get("PROJECT_DB_PASSWORD", "")},
        "workflow-service": {**common, "WORKFLOW_DB_PASSWORD": values.get("WORKFLOW_DB_PASSWORD", "")},
        "material-service": {**common, "MATERIAL_DB_PASSWORD": values.get("MATERIAL_DB_PASSWORD", ""), "MINIO_ACCESS_KEY": values.get("MINIO_ACCESS_KEY", ""), "MINIO_SECRET_KEY": values.get("MINIO_SECRET_KEY", "")},
        "notification-service": {**common, "NOTIFICATION_DB_PASSWORD": values.get("NOTIFICATION_DB_PASSWORD", "")},
        "gateway-service": common,
    }
    return mapping.get(service, {})


def validate_history_environment(project, containers, values):
    for container in containers:
        if label(container, "com.docker.compose.project") != project:
            continue
        service = label(container, "com.docker.compose.service")
        actual = dict(item.split("=", 1) for item in (container.get("Config") or {}).get("Env", []) if "=" in item)
        for key, expected in expected_container_environment(service, values).items():
            if key in actual and actual[key] != expected:
                raise StartupError(f"History configuration differs from existing {service}: {key}. Restore the original EnvFile")


def diagnose(runner, compose):
    runner.logger.write("Collecting automatic failure diagnostics", "DIAG")
    try:
        data = runner.capture(compose + ["ps", "-a", "--format", "json"], check=False)
        containers = parse_json_bytes(data, "compose ps diagnostics", runner.logger.redactor)
        containers = [containers] if isinstance(containers, dict) else containers
        for item in containers:
            service, state, health = item.get("Service", ""), item.get("State", ""), item.get("Health", "")
            runner.logger.write(f"service={service} state={state} health={health} status={item.get('Status', '')}", "DIAG")
            if state != "running" or health not in ("", "healthy"):
                if item.get("ID"):
                    raw = runner.capture(["docker", "inspect", "--format", "{{json .State}}", str(item["ID"])], check=False)
                    for line in decode_utf8(raw, "docker inspect diagnostics", runner.logger.redactor).splitlines()[-100:]:
                        runner.logger.raw(line)
                if service:
                    raw = runner.capture(compose + ["logs", "--tail", "100", service], check=False)
                    for line in decode_utf8(raw, "compose logs diagnostics", runner.logger.redactor).splitlines()[-100:]:
                        runner.logger.raw(line)
    except Exception as exc:
        runner.logger.write(f"Diagnostics failed without replacing the original error: {exc}", "WARN")
    runner.logger.write("Copyable status command:", "DIAG")
    runner.logger.raw("$ " + runner.logger.redactor.redact(" ".join(compose + ["ps", "-a"])))


def parser():
    value = argparse.ArgumentParser(description="Start SIC Cloud backend in Fresh or History mode")
    value.add_argument("--mode", choices=("fresh", "history"))
    value.add_argument("--project-name", default="sic-cloud")
    value.add_argument("--env-file")
    value.add_argument("--admin-username")
    value.add_argument("--reset-confirmation")
    value.add_argument("--skip-build", action="store_true")
    value.add_argument("--startup-timeout", type=int, default=600)
    value.add_argument("--dry-run", action="store_true", help="Show resolved counts and commands without changing Docker resources")
    add_deployment_arguments(value)
    return value


def main():
    args = parser().parse_args()
    root = Path(__file__).resolve().parent.parent
    env_file = Path(args.env_file).resolve() if args.env_file else root / ".env"
    mode = args.mode
    if not mode and args.dry_run:
        mode = "history"
    if not mode:
        mode = {"1": "fresh", "2": "history"}.get(input("1. Fresh\n2. History\nSelect 1 or 2: ").strip())
    if not mode or not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", args.project_name) or not 30 <= args.startup_timeout <= 7200:
        print("Invalid mode, project name, or startup timeout", file=sys.stderr); return 2
    try:
        values = read_env(env_file); validate_env(values, mode)
    except Exception as exc:
        print(f"Startup failed during configuration: {exc}", file=sys.stderr); return 1
    password_from_wrapper = os.environ.pop("SIC_STARTUP_ADMIN_PASSWORD", "")
    username_from_wrapper = os.environ.pop("SIC_STARTUP_ADMIN_USERNAME", "")
    redactor = Redactor({**values, "ADMIN_PASSWORD": password_from_wrapper})
    logger = RunLogger(root, redactor)
    args.env_file = str(env_file)
    runner = CommandRunner(logger, root, os.environ.copy())
    compose = compose_base(root, env_file, args.project_name)
    phase, reset_completed = "preflight", False
    manifest = {"passed": False, "mode": mode, "startedAt": datetime.now(timezone.utc).isoformat()}
    manifest_path = root / "verification/runs" / f"startup-{args.project_name}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    try:
        files = compose_files(root, args.compose_file)
        runner.env = child_environment(values, files, mode)
        logger.write(f"Starting mode={mode} project={args.project_name} env={env_file}")
        for executable in ("docker", "openssl"):
            if not shutil.which(executable): raise StartupError(f"Required executable not found: {executable}")
        version = decode_utf8(runner.capture(["docker", "compose", "version", "--short"]), "compose version", redactor).strip()
        if not re.match(r"v?2\.", version): raise StartupError(f"Docker Compose v2 required; detected {version}")
        engine = decode_utf8(runner.capture(["docker", "version", "--format", "{{.Server.Os}}"]), "docker version", redactor).strip().lower()
        if engine != "linux": raise StartupError(f"Docker Linux containers required; detected {engine!r}")
        deployment = load_deployment(root, args, values, runner)
        compose = deployment.compose
        manifest.update(deployment.summary())
        check_artifacts(deployment, runner)
        containers, volumes = inspect_state(runner)
        if any(label(item, "com.docker.compose.project") == args.project_name and not owned_container(item, deployment)
               for item in containers):
            raise StartupError("Project name is owned by containers outside this repository/configuration")
        if args.dry_run:
            from replica_common import instance_rows
            own = [item for item in containers if label(item, 'com.docker.compose.project') == args.project_name]
            counts = {service: sum(row['service'] == service and row['running'] for row in instance_rows(own)) for service in SERVICES}
            manifest["currentReplicas"] = counts
            manifest["replicaChanges"] = {service: {"from": counts[service], "to": deployment.replicas[service]}
                                          for service in SERVICES if counts[service] != deployment.replicas[service]}
            manifest["plannedCommand"] = compose + ["up", "-d", "--no-build", *deployment.scale_arguments(), *SERVICES]
            manifest["plannedCommands"] = ([] if args.skip_build else [build_command(compose)])
            if mode == "fresh":
                manifest["plannedCommands"].extend([compose + ["down", "--volumes", "--remove-orphans"],
                    compose + ["up", "-d", "--no-build", *INFRASTRUCTURE],
                    compose + ["up", "-d", "--no-build", *deployment.scale_arguments(single=True), *SERVICES]])
            manifest["plannedCommands"].append(manifest["plannedCommand"])
            print(json.dumps(manifest, ensure_ascii=False, indent=2))
            return 0
        validate_rsa(values, runner)
        validate_storage(args.project_name, containers, volumes, mode == "history")
        validate_deployment_ports(args.project_name, containers, deployment.config)
        if mode == "history":
            validate_history_environment(args.project_name, containers, values)
            username = args.admin_username or username_from_wrapper or input("Administrator username: ").strip()
            password = password_from_wrapper or getpass.getpass("Administrator password: ")
            if not username or not password: raise StartupError("History verification requires administrator credentials")
            redactor.secrets.insert(0, password)
        else:
            username, password = values["BOOTSTRAP_ADMIN_USERNAME"], values["BOOTSTRAP_ADMIN_PASSWORD"]
            logger.write("Fresh target volumes: " + ", ".join(expected_volumes(args.project_name).values()))
            logger.write("Fresh permanently deletes this project's MySQL, RabbitMQ, Redis and MinIO data", "WARN")
            confirmation = args.reset_confirmation or input(f"Type RESET:{args.project_name} to continue: ")
            validate_reset_confirmation(confirmation, args.project_name)
        phase = "build"
        if args.skip_build:
            check_artifacts(deployment, runner, skip_build=True)
        else:
            runner.stream(build_command(compose))
            check_artifacts(deployment, runner, skip_build=True)
        containers, volumes = inspect_state(runner)
        validate_storage(args.project_name, containers, volumes, mode == "history")
        validate_deployment_ports(args.project_name, containers, deployment.config)
        if mode == "fresh":
            phase = "fresh-reset"; runner.stream(compose + ["down", "--volumes", "--remove-orphans"])
            remaining = set(decode_utf8(runner.capture(["docker", "volume", "ls", "-q"]), "volume list", redactor).split())
            leftover = set(expected_volumes(args.project_name).values()) & remaining
            if leftover: raise StartupError("Fresh reset left volumes: " + ", ".join(sorted(leftover)))
            reset_completed = True
        phase = "startup"
        up = compose + ["up", "-d", "--no-build", "--remove-orphans", "--wait", "--wait-timeout", str(args.startup_timeout)]
        runner.stream(up + INFRASTRUCTURE)
        manifest["connectionBudget"] = connection_budget(deployment, runner)
        if mode == "fresh":
            runner.stream(up + deployment.scale_arguments(single=True) + SERVICES)
            manifest["initialInstances"] = wait_instances(runner, deployment, args.startup_timeout, {service: 1 for service in SERVICES})
        if mode != "fresh" or any(count != 1 for count in deployment.replicas.values()):
            runner.stream(up + deployment.scale_arguments() + SERVICES)
            manifest["instances"] = wait_instances(runner, deployment, args.startup_timeout)
        else:
            manifest["instances"] = manifest["initialInstances"]
        phase = "verification"; report_path = root / "verification" / "compose-e2e.json"
        verify_args = argparse.Namespace(project_name=args.project_name, env_file=str(env_file), deployment=deployment,
            compose_file=args.compose_file, gateway_base_url=deployment.gateway, config_server_base_url=None, discovery_base_url=None,
            expected_jwt_issuer=values.get("JWT_ISSUER", "sic-iam"), expected_jwt_audience=values.get("JWT_AUDIENCE", "sic-api"),
            start_timeout=args.startup_timeout, request_timeout=20, report_path=str(report_path), admin_username=username)
        report = run_verification(verify_args, runner, logger, username, password)
        if not report["passed"]: raise StartupError(f"Compose E2E failed: {report['error']}")
        manifest.update(passed=True, smoke=report)
        logger.write(f"Backend ready: {deployment.gateway} ({mode}, {args.project_name})")
        logger.write(f"E2E: {len(report['checks'])} checks passed; report={report_path}")
        logger.write(f"Frontend proxy: set SIC_GATEWAY_PROXY_TARGET={deployment.gateway} in the frontend .env.development.local, then restart npm run dev")
        logger.write("Frontend: cd ..\\SicProjectIS_fronted; npm install; npm run dev"); return 0
    except KeyboardInterrupt:
        logger.write(f"Interrupted during phase={phase}; freshResetCompleted={reset_completed}", "ERROR"); return 130
    except Exception as exc:
        manifest["error"] = redactor.redact(exc)
        logger.write(f"Startup failed: phase={phase}; freshResetCompleted={reset_completed}; error={exc}", "ERROR")
        diagnose(runner, compose); logger.write(f"Detailed log: {logger.path}", "ERROR"); return 1
    finally:
        if not args.dry_run:
            manifest.update(phase=phase, freshResetCompleted=reset_completed, finishedAt=datetime.now(timezone.utc).isoformat())
            manifest_path.parent.mkdir(parents=True, exist_ok=True)
            manifest_path.write_text(redactor.redact(json.dumps(manifest, ensure_ascii=False, indent=2)), encoding="utf-8")
            logger.write(f"Startup manifest: {manifest_path}")
        logger.close()


if __name__ == "__main__": raise SystemExit(main())
