"""Read-only release/adoption gate against the actual MySQL information_schema."""
from __future__ import annotations
import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def verify(args: argparse.Namespace) -> int:
    expected = json.loads((ROOT / "infra/mysql/schema-signature.json").read_text(encoding="utf-8"))
    queries = ["SELECT CONCAT('TABLE|',table_schema,'|',table_name) FROM information_schema.tables "
        "WHERE table_schema IN ('sic_iam','sic_project','sic_workflow','sic_material','sic_notification') "
        "AND table_name<>'flyway_schema_history' ORDER BY table_schema,table_name;"]
    definitions = {
        "columns": ("columns", "column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,COALESCE(collation_name,'<NULL>')", "ordinal_position"),
        "indices": ("statistics", "index_name,non_unique,seq_in_index,column_name,COALESCE(sub_part,-1),index_type", "index_name,seq_in_index"),
        "constraints": ("table_constraints", "constraint_name,constraint_type", "constraint_name"),
        "metadata": ("tables", "engine,table_collation,row_format", "table_name"),
    }
    for domain, tables in expected.items():
        for table in tables:
            for kind, (source, fields, order) in definitions.items():
                queries += [f"SELECT 'SECTION|{domain}|{table}|{kind}';",
                    f"SELECT CONCAT_WS('|',{fields}) FROM information_schema.{source} "
                    f"WHERE table_schema='sic_{domain}' AND table_name='{table}' ORDER BY {order};"]
    command = ["docker", "compose", "-p", args.project_name]
    if args.env_file:
        command += ["--env-file", str(args.env_file.resolve())]
    for file in args.compose_file or [ROOT / "docker-compose.yml"]:
        command += ["-f", str(file.resolve())]
    command += ["exec", "-T", "mysql", "sh", "-lc",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names']
    report = {"schemaVersion": 1, "release": "1.0.0-baseline", "project": args.project_name,
        "checkedAt": datetime.now(timezone.utc).isoformat(), "passed": False, "tableCount": 0, "differences": []}
    try:
        result = subprocess.run(command, cwd=ROOT, input="\n".join(queries), text=True, encoding="utf-8",
            capture_output=True, timeout=60, check=True)
        actual, listed, current = {}, {}, None
        for line in result.stdout.splitlines():
            if line.startswith("TABLE|"):
                _, schema, table = line.split("|")
                listed.setdefault(schema.removeprefix("sic_"), []).append(table)
            elif line.startswith("SECTION|"):
                _, domain, table, kind = line.split("|")
                current = actual.setdefault(domain, {}).setdefault(table, {}).setdefault(kind, [])
            elif current is not None:
                current.append(line)
            else:
                raise ValueError("Unexpected MySQL metadata output")
        for domain, tables in expected.items():
            if sorted(tables) != sorted(listed.get(domain, [])):
                report["differences"].append({"domain": domain, "kind": "table names",
                    "missing": sorted(set(tables) - set(listed.get(domain, []))),
                    "extra": sorted(set(listed.get(domain, [])) - set(tables))})
            for table, sections in tables.items():
                for kind, rows in sections.items():
                    found = actual.get(domain, {}).get(table, {}).get(kind, [])
                    if found != rows:
                        report["differences"].append({"domain": domain, "table": table, "kind": kind,
                            "expected": rows, "actual": found})
        report["tableCount"] = sum(len(tables) for tables in listed.values())
        report["passed"] = not report["differences"] and report["tableCount"] == 52
    except (OSError, subprocess.SubprocessError, ValueError) as exc:
        # Do not dump a subprocess environment or command credentials in the report.
        report["error"] = type(exc).__name__
    args.report_path.parent.mkdir(parents=True, exist_ok=True)
    args.report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Schema {'PASS' if report['passed'] else 'FAIL'}: {report['tableCount']} tables; {args.report_path}")
    return 0 if report["passed"] else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name", default="sic-cloud")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--compose-file", type=Path, action="append")
    parser.add_argument("--report-path", type=Path, default=ROOT / "verification/runs/schema-verification.json")
    return verify(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
