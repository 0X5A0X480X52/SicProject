"""Rehearse five-schema backup/restore into fresh, uniquely named schemas; preserve the source."""
from __future__ import annotations
import argparse
import json
import re
import secrets
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAINS = ("iam", "project", "workflow", "material", "notification")
BUSINESS = {"gateway-service", "iam-service", "project-service", "workflow-service", "material-service", "notification-service"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name", required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--compose-file", action="append", type=Path)
    parser.add_argument("--backup-path", type=Path, default=ROOT / "target/restore-rehearsal.sql")
    parser.add_argument("--report-path", type=Path, default=ROOT / "verification/runs/backup-restore.json")
    args = parser.parse_args()
    command = ["docker", "compose", "-p", args.project_name, "--env-file", str(args.env_file.resolve())]
    for file in args.compose_file or [ROOT / "docker-compose.yml"]:
        command += ["-f", str(file.resolve())]
    run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + secrets.token_hex(3)
    mapping = {"sic_" + domain: "sic_restore_" + run_id + "_" + domain for domain in DOMAINS}
    report = {"passed": False, "runId": run_id, "project": args.project_name,
        "restoreSchemas": mapping, "tableCount": 0, "differences": []}

    def mysql(sql: str) -> str:
        result = subprocess.run(command + ["exec", "-T", "mysql", "sh", "-lc",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'],
            input=sql, text=True, encoding="utf-8", capture_output=True, timeout=120, check=True)
        return result.stdout

    try:
        if args.backup_path.exists():
            raise FileExistsError("Backup destination already exists")
        state = subprocess.run(command + ["ps", "--all", "--format", "json"], capture_output=True,
            text=True, encoding="utf-8", timeout=30, check=True).stdout.strip()
        containers = json.loads(state) if state.startswith("[") else [json.loads(line) for line in state.splitlines()]
        if any(row.get("Service") in BUSINESS and row.get("State") == "running" for row in containers):
            raise RuntimeError("Stop this project's business services before a consistent restore rehearsal")
        originals = tuple(mapping)
        names = ",".join("'" + schema + "'" for schema in originals)
        tables = [tuple(line.split("\t")) for line in mysql(
            "SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN (" + names + ") "
            "AND table_type='BASE TABLE' ORDER BY table_schema,table_name;").splitlines()]
        if not tables or {schema for schema, _ in tables} != set(originals):
            raise RuntimeError("Five complete source schemas are required")
        if mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN (" +
                ",".join("'" + schema + "'" for schema in mapping.values()) + ");").strip() != "0":
            raise RuntimeError("Restore namespace already exists")
        backup = subprocess.run(command + ["exec", "-T", "mysql", "sh", "-lc",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --events '
            '--hex-blob --set-gtid-purged=OFF --skip-add-drop-table --databases ' + " ".join(originals)],
            capture_output=True, timeout=120, check=True).stdout
        args.backup_path.parent.mkdir(parents=True, exist_ok=True)
        args.backup_path.write_bytes(backup)
        args.backup_path.chmod(0o600)
        decoded = backup.decode("utf-8")
        # Rewrite database directives only; never replace names inside business row values.
        restored = re.sub(r"(?m)^(CREATE DATABASE[^\n]*|USE `[^`]+`;)$",
            lambda match: re.sub(r"`(sic_(?:iam|project|workflow|material|notification))`",
                lambda name: "`" + mapping[name[1]] + "`", match[0]), decoded)
        if re.search(r"(?im)^\s*DROP\s+DATABASE", restored):
            raise RuntimeError("Unexpected destructive database directive")
        mysql(restored)
        queries=[]
        for schema,table in tables:
            if not re.fullmatch(r"[a-z0-9_]+",table):
                raise RuntimeError("Unexpected table identifier")
            queries += [f"SELECT CONCAT('COUNT|{schema}|{table}|',(SELECT COUNT(*) FROM `{schema}`.`{table}`),'|',"
                f"(SELECT COUNT(*) FROM `{mapping[schema]}`.`{table}`));",
                f"CHECKSUM TABLE `{schema}`.`{table}`,`{mapping[schema]}`.`{table}` EXTENDED;"]
        counts,checksums={},{}
        for line in mysql("\n".join(queries)).splitlines():
            if line.startswith("COUNT|"):
                _,schema,table,source,target=line.split("|")
                counts[(schema,table)]=[source,target]
            else:
                name,checksum=line.rsplit("\t",1)
                checksums[name]=checksum
        for schema,table in tables:
            values=[checksums.get(schema+"."+table),checksums.get(mapping[schema]+"."+table)]
            rows=counts[(schema,table)]
            if rows[0]!=rows[1] or values[0]!=values[1] or any(value in (None,"NULL") for value in values):
                report["differences"].append({"schema":schema,"table":table,"counts":rows,"checksums":values})
        import hashlib
        report.update(backupBytes=len(backup), backupSha256=hashlib.sha256(backup).hexdigest(),
            tableCount=len(tables), passed=not report["differences"])
    except (OSError, subprocess.SubprocessError, RuntimeError, ValueError) as exc:
        report["error"] = type(exc).__name__
    args.report_path.parent.mkdir(parents=True, exist_ok=True)
    args.report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Backup/restore {'PASS' if report['passed'] else 'FAIL'}: {report['tableCount']} tables; {args.report_path}")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
