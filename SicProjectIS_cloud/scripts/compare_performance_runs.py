"""Reject incomparable mixed-load runs and compare latency against an explicit frozen baseline."""
from __future__ import annotations
import argparse
import json
from pathlib import Path


def read(path):return json.loads(path.read_text(encoding="utf-8"))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("baseline","candidate"):
        for kind in ("environment","load","stability"):
            parser.add_argument(f"--{name}-{kind}",type=Path,required=True)
    parser.add_argument("--latency-noise-ms",type=float,default=5,help="Absolute local measurement allowance; reported explicitly")
    parser.add_argument("--report-path",type=Path,required=True)
    args=parser.parse_args()
    if args.latency_noise_ms<0:raise ValueError("Noise allowance cannot be negative")
    report={"passed":False,"comparable":False,"checks":{},"latencyNoiseAllowanceMs":args.latency_noise_ms,
        "scope":"One local fixed workload; does not establish production capacity or compare against the original unmodified release"}
    environments=[read(getattr(args,name+"_environment")) for name in ("baseline","candidate")]
    loads=[read(getattr(args,name+"_load")) for name in ("baseline","candidate")]
    series=[read(getattr(args,name+"_stability")) for name in ("baseline","candidate")]
    b,c=environments;bl,cl=loads
    report["checks"]["sameResources"]=b["resources"]==c["resources"]
    report["checks"]["sameInitialRows"]=b["initialRows"]==c["initialRows"]
    report["checks"]["sameSchema"]=b["release"]["schemaManifestSha256"]==c["release"]["schemaManifestSha256"]
    report["checks"]["sameLoadProfile"]=all(bl["load"][key]==cl["load"][key] for key in ("workers","batchMembers","requestsPerSecondLimit")) and bl["caseId"]==cl["caseId"]
    report["checks"]["sameDuration"]=abs(bl["load"]["durationSeconds"]-cl["load"]["durationSeconds"])<=5
    measurements=[load.get("latencyMeasurement",{}) for load in loads]
    report["checks"]["sameHighResolutionMeasurement"]=all(
        measurement.get("clock")=="perf_counter" and 0<measurement.get("resolutionSeconds",1)<=.001
        for measurement in measurements) and measurements[0]==measurements[1]
    report["latencyMeasurement"]=measurements
    report["checks"]["bothBusinessFlowsPassed"]=all(load["overallPassed"] and load["businessDataPassed"] for load in loads)
    report["checks"]["completeInLoadSampling"]=all(s["complete"] and not s["errors"] and len(s["samples"])>=2 for s in series)
    deadlocks=[]
    for s in series:
        values=[int(line.split("\t")[1]) for sample in s["samples"] for line in sample["mysql"] if line.startswith("INNODB_DEADLOCKS\t")]
        deadlocks.append(len(values)==len(s["samples"]) and bool(values) and values[-1]==values[0])
    report["checks"]["noNewDeadlocks"]=all(deadlocks)
    report["comparable"]=all(report["checks"].values())
    report["releaseLabels"]=[environment["release"]["label"] for environment in environments]
    report["runIds"]=[load["runId"] for load in loads]
    report["latency"]={}
    for metric in ("mean","p95"):
        before=bl["pressureLatencyMs"][metric];after=cl["pressureLatencyMs"][metric]
        report["latency"][metric]={"baselineMs":before,"candidateMs":after,
            "ratio":round(after/before,4) if before else None,"passed":after<=before+args.latency_noise_ms}
    report["passed"]=report["comparable"] and all(value["passed"] for value in report["latency"].values())
    args.report_path.parent.mkdir(parents=True,exist_ok=True)
    args.report_path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("Performance comparison "+("PASS" if report["passed"] else "FAIL")+": "+str(args.report_path))
    return 0 if report["passed"] else 1


if __name__=="__main__":raise SystemExit(main())
