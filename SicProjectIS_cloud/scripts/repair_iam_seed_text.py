"""Preview or repair exact IAM seed corruption, with a private backup and access-state assertions."""
import argparse
import hashlib
import json
import secrets
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from build_iam_text_repair import ROOT, PATCH, generate, repair_fields


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project-name',required=True)
    parser.add_argument('--env-file',type=Path,required=True)
    parser.add_argument('--compose-file',type=Path,action='append')
    parser.add_argument('--apply',action='store_true',help='Back up IAM then apply the exact-pattern patch')
    parser.add_argument('--report-path',type=Path,default=ROOT/'verification/runs/iam-text-repair.json')
    args=parser.parse_args()
    if PATCH.read_text(encoding='utf-8')!=generate():raise ValueError('Repair patch differs from audited generator')
    command=['docker','compose','-p',args.project_name,'--env-file',str(args.env_file.resolve())]
    for path in args.compose_file or [ROOT/'docker-compose.yml']:command+=['-f',str(path.resolve())]
    command+=['exec','-T','mysql','sh','-c']
    def run(shell,sql=None):
        return subprocess.run(command+[shell],input=sql,capture_output=True,check=True,timeout=90,cwd=ROOT).stdout
    def query(sql):
        return run('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names sic_iam',sql.encode('utf-8')).decode('utf-8')
    fields=repair_fields()
    def texts():
        sql=[]
        for table,key,code,column,value in fields:
            literal='0x'+value.encode('utf-8').hex()
            extra=' AND id=1' if table=='department' else ''
            sql.append(f"SELECT '{table}.{code}.{column}',HEX({column}),HEX(CONVERT(CONVERT({literal} USING latin1) USING utf8mb4)) FROM {table} WHERE {key}='{code}'{extra};")
        result={parts[0]:parts[1:] for parts in (line.split('\t') for line in query('\n'.join(sql)).splitlines())}
        if len(result)!=len(fields):raise ValueError('Missing seed reference rows; review target database')
        return result
    def access_state():
        sql="""SELECT JSON_ARRAY('user',id,username,password_hash,real_name,email,phone,dept_id,enabled,auth_version) FROM app_user ORDER BY id;
SELECT JSON_ARRAY('user_role',user_id,role_id) FROM user_role ORDER BY user_id,role_id;
SELECT JSON_ARRAY('role_permission',role_id,permission_id) FROM role_permission ORDER BY role_id,permission_id;
SELECT JSON_ARRAY('role',id,code,enabled,built_in) FROM app_role ORDER BY id;
SELECT JSON_ARRAY('permission',id,code,permission_group,description) FROM app_permission ORDER BY id;
SELECT JSON_ARRAY('department',id,code,parent_id,enabled) FROM department ORDER BY id;"""
        return hashlib.sha256(query(sql).encode('utf-8')).hexdigest()
    before=texts();access_before=access_state()
    corrupt=[key for key,(actual,wrong) in before.items() if actual==wrong]
    report={'project':args.project_name,'applied':False,'passed':False,'eligibleFields':len(corrupt),'changedFields':0}
    if args.apply:
        directory=ROOT/'target/iam-text-repair';directory.mkdir(parents=True,exist_ok=True)
        backup=directory/(datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')+'-'+secrets.token_hex(3)+'.sql')
        dump=run('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF sic_iam')
        with backup.open('xb') as handle:handle.write(dump)
        backup.chmod(0o600)
        report.update(backupPath=str(backup),backupSha256=hashlib.sha256(dump).hexdigest())
        query(PATCH.read_text(encoding='utf-8'));report['applied']=True
        after=texts()
        expected={f'{table}.{code}.{column}':value.encode('utf-8').hex().upper() for table,key,code,column,value in fields}
        report['changedFields']=sum(before[key][0]!=after[key][0] for key in before)
        report['textVerified']=all(after[key][0]==(expected[key] if key in corrupt else before[key][0]) for key in before)
        report['accessStateUnchanged']=access_state()==access_before
        report['passed']=report['textVerified'] and report['accessStateUnchanged']
    else:
        report['passed']=True
    args.report_path.parent.mkdir(parents=True,exist_ok=True)
    args.report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f"{'Apply' if args.apply else 'Preview'}: {len(corrupt)} eligible fields; {'PASS' if report['passed'] else 'FAIL'}; report={args.report_path}")
    return 0 if report['passed'] else 1


if __name__=='__main__':raise SystemExit(main())
