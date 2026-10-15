"""Build final CREATE TABLE definitions from the audited migration vocabulary.

Unknown SQL/ALTER operations fail closed. --check compares without writing.
Migration sources remain the comparison baseline until MySQL equivalence passes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAINS = ("iam", "project", "workflow", "material", "notification")


def split_sql(text: str, separator: str = ";") -> list[str]:
    result, start, depth, quote, index = [], 0, 0, None, 0
    while index < len(text):
        char = text[index]
        if quote:
            if char == "\\":
                index += 2
                continue
            if char == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 2
                    continue
                quote = None
        elif char in "'\"`":
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == separator and depth == 0:
            if text[start:index].strip():
                result.append(text[start:index].strip())
            start = index + 1
        index += 1
    if quote or depth:
        raise ValueError("Unbalanced SQL input")
    if text[start:].strip():
        result.append(text[start:].strip())
    return result


def compile_domain(domain: str) -> tuple[str, str, dict]:
    common = list((ROOT / "platform/cloud-messaging/src/main/resources/db/common").glob("V*.sql"))
    local = list((ROOT / f"services/{domain}-service/src/main/resources/db/migration").glob("V*.sql"))
    sources = sorted(common + local, key=lambda p: tuple(int(n) for n in re.match(r"V([\d_]+)__", p.name)[1].split("_")))
    tables: OrderedDict[str, list[str]] = OrderedDict()
    seed, hashes = [], {}
    for source in sources:
        content = source.read_text(encoding="utf-8")
        hashes[source.relative_to(ROOT).as_posix()] = hashlib.sha256(source.read_bytes()).hexdigest()
        # Sources have only standalone -- comments; don't strip literals inside statements.
        content = re.sub(r"(?m)^\s*--[^\n]*", "", content)
        for sql in split_sql(content):
            create = re.fullmatch(r"CREATE TABLE (?:IF NOT EXISTS )?(\w+)\s*\((.*)\)", sql, re.S)
            alter = re.fullmatch(r"ALTER TABLE (\w+)\s+(.*)", sql, re.S)
            index = re.fullmatch(r"CREATE (UNIQUE )?INDEX (\w+)\s+ON (\w+)\s*(\(.*\))", sql, re.S)
            if create:
                table, body = create.groups()
                if table in tables:
                    raise ValueError("Duplicate table: " + table)
                tables[table] = split_sql(body, ",")
            elif alter:
                table, operations = alter.groups()
                for operation in split_sql(operations, ","):
                    column = re.fullmatch(r"ADD COLUMN (\w+)\s+(.*?)(?:\s+AFTER (\w+))?", operation, re.S)
                    constraint = re.fullmatch(r"ADD ((?:UNIQUE KEY|INDEX)\s+.*)", operation, re.S)
                    if column:
                        name, definition, after = column.groups()
                        existing = [entry.split()[0] for entry in tables[table]]
                        if name in existing:
                            raise ValueError("Duplicate column: " + name)
                        position = existing.index(after) + 1 if after else next((i for i, e in enumerate(existing)
                            if e in ("INDEX", "UNIQUE", "PRIMARY", "CONSTRAINT", "KEY")), len(existing))
                        tables[table].insert(position, name + " " + definition)
                    elif constraint:
                        tables[table].append(constraint[1])
                    else:
                        raise ValueError("Unsupported ALTER: " + operation)
            elif index:
                unique, name, table, columns = index.groups()
                tables[table].append(("UNIQUE KEY " if unique else "INDEX ") + name + columns)
            elif sql.startswith("INSERT INTO "):
                # Replace placeholder workflow definitions using the real Flowable exporter.
                if domain != "workflow":
                    seed.append(sql + ";")
            elif sql == "UPDATE workflow_definition SET status=CASE WHEN published=TRUE THEN 'ACTIVE' ELSE 'DRAFT' END":
                pass # The new real workflow seed explicitly sets its final status.
            else:
                raise ValueError(f"Unsupported statement in {source.name}: {sql[:100]}")
    ddl = "-- Final MySQL 8.4 schema. Empty database only; contains no DROP/TRUNCATE.\nSET NAMES utf8mb4;\n\n"
    for table, entries in tables.items():
        ddl += f"CREATE TABLE {table} (\n    " + ",\n    ".join(entries) + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;\n\n"
    return ddl, "-- Release seed; credentials are configured through IAM bootstrap.\nSET NAMES utf8mb4;\n" + "\n\n".join(seed) + "\n", {
        "tableCount": len(tables), "tables": list(tables), "migrationSourceSha256": hashes}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    manifest = {"schemaVersion": 1, "release": "1.0.0-baseline", "mysqlVersion": "8.4", "domains": {}}
    outputs = {}
    for domain in DOMAINS:
        ddl, seed, inventory = compile_domain(domain)
        outputs[f"schema/{domain}.sql"] = ddl
        if domain == "iam":
            outputs[f"seed/{domain}.sql"] = seed
        inventory["schemaSha256"] = hashlib.sha256(ddl.encode()).hexdigest()
        manifest["domains"][domain] = inventory
    assert sum(d["tableCount"] for d in manifest["domains"].values()) == 52
    manifest["initializationMode"] = "full-sql"
    manifest["requiredEquivalenceGate"] = "FullSchemaEquivalenceTest"
    manifest["seedSha256"] = {name: hashlib.sha256((ROOT / "infra/mysql/seed" / name).read_bytes()).hexdigest()
        for name in ("workflow-assets.sql", "workflow-missing-assets.sql")}
    outputs["schema-manifest.json"] = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    destination = ROOT / "infra/mysql"
    for name, content in outputs.items():
        path = destination / name
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                raise ValueError("Generated schema differs: " + name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8", newline="\n")
    print("52 tables generated/checked; MySQL equivalence still requires the integration gate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
