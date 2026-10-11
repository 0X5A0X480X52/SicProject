"""Reject missing, skipped or failed mandatory tests; emit a credential-free summary."""
from __future__ import annotations
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_MYSQL = {"FullSchemaEquivalenceTest", "InfrastructureIntegrationTest",
    "MysqlMapperIntegrationTest", "SessionRedisIntegrationTest", "MysqlInitializationEncodingTest", "MysqlWorkbenchRegressionTest"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gate", choices=("smoke", "mysql"), required=True)
    parser.add_argument("--started-at", type=float, default=0)
    parser.add_argument("--report-path", type=Path, default=ROOT / "verification/runs/test-gate.json")
    args = parser.parse_args()
    report = {"gate": args.gate, "passed": False, "tests": 0, "skipped": 0, "failures": [], "suites": []}
    seen = set()
    canonical=set()
    counts={}
    for path in ROOT.glob("**/target/surefire-reports/TEST-*.xml"):
        if path.stat().st_mtime < args.started_at:
            continue
        suite = ET.parse(path).getroot()
        name = suite.get("name", "").rsplit(".", 1)[-1]
        if args.gate == "smoke" and name in REQUIRED_MYSQL:
            continue
        seen.add(name)
        canonical.add(suite.get("name", "").split("$",1)[0])
        tests = int(suite.get("tests", 0))
        key=suite.get("name", "").split("$",1)[0]
        counts[key]=counts.get(key,0)+tests
        skipped = int(suite.get("skipped", 0))
        failures = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        report["suites"].append({"name": name, "tests": tests, "skipped": skipped, "failures": failures})
        report["tests"] += tests
        report["skipped"] += skipped
        if failures or skipped or not tests:
            report["failures"].append(name)
    if args.gate == "mysql":
        report["failures"] += ["Missing " + name for name in sorted(REQUIRED_MYSQL - seen)]
    expected={}
    for source in ROOT.glob("**/src/test/java/**/*.java"):
        if "target" in source.relative_to(ROOT).parts:continue
        if args.gate=="smoke" and source.is_relative_to(ROOT/"platform/integration-tests"):continue
        name=source.stem
        if not (name.startswith("Test") or name.endswith(("Test","Tests","TestCase"))):continue
        content=source.read_text(encoding="utf-8")
        if not re.search(r"@(Test|ParameterizedTest)\b",content) or re.search(r"abstract\s+class\s+"+re.escape(name)+r"\b",content):continue
        package=re.search(r"\bpackage\s+([\w.]+)\s*;",content)
        if package:expected[package.group(1)+"."+name]=len(re.findall(r"@(Test|ParameterizedTest)\b",content))
    report["expectedSourceSuites"]=len(expected)
    for source in ROOT.glob("**/target/generated-test-sources/contracts/**/*.java"):
        content=source.read_text(encoding="utf-8")
        package=re.search(r"\bpackage\s+([\w.]+)\s*;",content)
        if package:expected[package.group(1)+"."+source.stem]=len(re.findall(r"@Test\b",content))
    report["expectedGeneratedContractSuites"]=sum(1 for name in expected if name.endswith(".ContractVerifierTest"))
    report["failures"] += ["Missing source suite "+name for name in sorted(set(expected)-canonical)]
    report["failures"] += ["Incomplete source suite "+name for name,minimum in expected.items()
        if name in canonical and counts.get(name,0)<minimum]
    if not report["tests"]:
        report["failures"].append("No fresh test results")
    report["passed"] = not report["failures"]
    args.report_path.parent.mkdir(parents=True, exist_ok=True)
    args.report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{args.gate}: {report['tests']} tests, {report['skipped']} skipped; {'PASS' if report['passed'] else 'FAIL'}")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
