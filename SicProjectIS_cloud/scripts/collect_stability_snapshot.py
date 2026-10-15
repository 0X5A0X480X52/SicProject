"""Read SQL waits, Redis and queue sizes, plus per-service pool/SQL/RPC metrics without request payloads."""
from __future__ import annotations
import argparse
import concurrent.futures
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from startup_common import read_env

ROOT=Path(__file__).resolve().parents[1]
SERVICES=("iam-service","project-service","workflow-service","material-service","notification-service","gateway-service")
PREFIXES=("hikaricp_","jdbc_connections_","sic_","resilience4j_","http_client_","spring_data_redis_")


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-name",required=True)
    parser.add_argument("--env-file",required=True,type=Path)
    parser.add_argument("--compose-file",action="append",type=Path)
    parser.add_argument("--report-path",type=Path,default=ROOT/"verification/runs/stability-snapshot.json")
    args=parser.parse_args()
    secrets=[value for key,value in read_env(args.env_file).items() if any(word in key for word in ("PASSWORD","SECRET","PRIVATE_KEY"))]
    command=["docker","compose","-p",args.project_name,"--env-file",str(args.env_file.resolve())]
    for path in args.compose_file or [ROOT/"docker-compose.yml"]:command += ["-f",str(path.resolve())]
    report={"capturedAt":datetime.now(timezone.utc).isoformat(),"project":args.project_name,"complete":False,"services":{},"errors":[]}

    def execute(service,script,input=None):
        return subprocess.run(command+["exec","-T",service,"sh","-lc",script],input=input,
            capture_output=True,text=True,encoding="utf-8",timeout=30,check=True).stdout

    def metrics(service):
        content=execute(service,'wget -qO- "http://localhost:$SERVER_PORT/actuator/prometheus"')
        return [line for line in content.splitlines() if line.startswith(PREFIXES)]

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as workers:
        futures={workers.submit(metrics,service):service for service in SERVICES}
        for future,service in futures.items():
            try:
                lines=future.result();report["services"][service]=lines
                if service!="gateway-service" and not any(line.startswith("hikaricp_connections_active") for line in lines):
                    report["errors"].append(service+": Hikari metrics missing")
            except (OSError,subprocess.SubprocessError) as exc:report["errors"].append(service+": "+type(exc).__name__)
    sql="""
SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_running','Threads_connected','Slow_queries','Innodb_deadlocks',
  'Innodb_row_lock_current_waits','Innodb_row_lock_time');
SELECT 'INNODB_DEADLOCKS',COUNT FROM information_schema.innodb_metrics WHERE NAME='lock_deadlocks';
SELECT 'LOCK_WAITS';
SELECT r.OBJECT_SCHEMA,r.OBJECT_NAME,r.INDEX_NAME,r.LOCK_TYPE,r.LOCK_MODE,
 COALESCE(REGEXP_SUBSTR(s.SQL_TEXT,'sic_trace=[A-Za-z0-9-]+'),'unavailable')
FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks r
 ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
LEFT JOIN performance_schema.events_statements_current s ON s.THREAD_ID=r.THREAD_ID;
SELECT 'TRANSACTIONS';
SELECT trx_id,TIMESTAMPDIFF(SECOND,trx_started,NOW()),trx_state,trx_rows_locked,trx_rows_modified,
 COALESCE(REGEXP_SUBSTR(trx_query,'sic_trace=[A-Za-z0-9-]+'),'unavailable') FROM information_schema.innodb_trx;
"""
    for name,service,script,query in (
        ("mysql","mysql",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names',sql),
        ("redis","redis",'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO stats',None),
        ("rabbitmq","rabbitmq",'rabbitmqctl list_queues --quiet name messages_ready messages_unacknowledged consumers',None)):
        try:
            content=execute(service,script,query)
            if name=="redis":content="\n".join(line for line in content.splitlines() if line.startswith(("total_commands_processed:","rejected_connections:","evicted_keys:","keyspace_hits:","keyspace_misses:","total_error_replies:")))
            report[name]=content.splitlines()
        except (OSError,subprocess.SubprocessError) as exc:report["errors"].append(name+": "+type(exc).__name__)
    report["complete"]=not report["errors"]
    text=json.dumps(report,indent=2)
    for value in sorted(secrets,key=len,reverse=True):
        if value:text=text.replace(value,"<redacted>")
    args.report_path.parent.mkdir(parents=True,exist_ok=True);args.report_path.write_text(text+"\n",encoding="utf-8")
    print(f"Stability snapshot {'PASS' if report['complete'] else 'INCOMPLETE'}: {args.report_path}")
    return 0 if report["complete"] else 1


if __name__=="__main__":raise SystemExit(main())
