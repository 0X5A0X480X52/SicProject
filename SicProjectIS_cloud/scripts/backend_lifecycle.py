"""Project-scoped replica status and stop commands; never remove containers or volumes."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
from startup_common import CommandRunner, Redactor, RunLogger, StartupError, read_env
from replica_common import (BUSINESS, add_deployment_arguments, compose_files, child_environment, load_deployment,
                            inspect_project, instance_rows, owned_container, internal_json, check_registry)


def main(action):
    parser = argparse.ArgumentParser(description=f"{action.title()} SIC containers owned by one Compose project")
    parser.add_argument("--project-name", default="sic-cloud")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--timeout", type=int, default=30)
    add_deployment_arguments(parser)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    args.env_file = (args.env_file or root / ".env").resolve()
    logger = RunLogger(root, Redactor(), action)
    report = {"passed": False, "action": action}
    try:
        values = read_env(args.env_file)
        logger.redactor = Redactor(values)
        runner = CommandRunner(logger, root, child_environment(values, compose_files(root, args.compose_file), "history"))
        if not 1 <= args.timeout <= 60:
            raise StartupError("Timeout must be between 1 and 60 seconds")
        deployment = load_deployment(root, args, values, runner)
        report.update(deployment.summary())
        containers = inspect_project(runner, deployment)
        if any(not owned_container(item, deployment) for item in containers):
            raise StartupError("Project name is also used by containers outside this repository/configuration")
        report["containers"] = instance_rows(containers)
        if action == "stop":
            targets = [item["Id"] for item in containers if item.get("State", {}).get("Running")]
            report["targetContainerIds"] = targets
            running = [row for row in report["containers"] if row["running"]]
            groups = [[row["containerId"] for row in running if row["service"] == "gateway-service"],
                      [row["containerId"] for row in running if row["service"] in BUSINESS],
                      [row["containerId"] for row in running if row["service"] not in [*BUSINESS, "gateway-service"]]]
            report["stopCommands"] = [["docker", "stop", "--time", str(args.timeout), *group] for group in groups if group]
            if targets and not args.dry_run:
                # 先关闭入口和业务，再停止注册及基础设施，给注销、连接关闭及端口释放留出顺序。
                for command in report["stopCommands"]:
                    runner.stream(command)
                report["containers"] = instance_rows(inspect_project(runner, deployment))
            report["passed"] = True
        else:
            try:
                registry = internal_json(runner, deployment, "discovery-server", "/eureka/apps", 8761)
                report["registrations"] = check_registry(containers, registry, deployment.replicas)
                from replica_common import check_container_health
                check_container_health(containers, deployment.replicas)
                report["passed"] = True
            except (StartupError, ValueError, KeyError) as exc:
                report["registryError"] = logger.redactor.redact(exc)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["passed"] else 1
    except Exception as exc:
        logger.write(exc, "ERROR")
        return 1
    finally:
        logger.close()
