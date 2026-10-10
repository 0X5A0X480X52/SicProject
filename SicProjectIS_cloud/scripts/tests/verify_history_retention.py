from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

import requests

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from startup_common import read_env

CONTENT = b"startup-history-file-retained-python"
USER = "history-retained-user"
PROJECT_CODE = "STARTUP-HISTORY-PY"
MATERIAL_CODE = "STARTUP_TEST"


def api(response: requests.Response, status: int = 200):
    if response.status_code != status:
        raise RuntimeError(f"{response.request.method} {response.url}: expected {status}, got {response.status_code}: {response.text[:500]}")
    body = response.json()
    if body.get("success") != (status < 400):
        raise RuntimeError(f"Invalid API wrapper from {response.url}: {body}")
    return body.get("data")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Fresh-to-History API and file retention through the frontend proxy")
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:5179")
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--create", action="store_true")
    args = parser.parse_args()
    values, base = read_env(Path(args.env_file).resolve()), args.base_url.rstrip("/")
    user_password = values["BOOTSTRAP_ADMIN_PASSWORD"] + "-history-user"
    session = requests.Session()
    admin = api(session.post(base + "/api/auth/login", json={
        "username": values["BOOTSTRAP_ADMIN_USERNAME"], "password": values["BOOTSTRAP_ADMIN_PASSWORD"]}))
    headers = {"Authorization": "Bearer " + admin["token"]}
    baseline_path = Path(args.baseline).resolve()
    if args.create:
        existing_user = session.post(base + "/api/auth/login", json={"username": USER, "password": user_password})
        if existing_user.status_code == 200:
            registered = api(existing_user)
        else:
            registered = api(session.post(base + "/api/auth/register", json={
                "username": USER, "password": user_password, "realName": "History retained user", "deptId": 1}))
        projects = api(session.get(base + "/api/projects", headers=headers))
        project = next((item for item in projects if item.get("projectCode") == PROJECT_CODE), None)
        if project:
            project_id = project["projectId"]
        else:
            started = api(session.post(base + "/api/project-applications/start", headers=headers, json={
                "projectCode": PROJECT_CODE, "projectName": "Startup history retention test",
                "applicationTitle": "Startup test application", "isLimitedProject": False}))
            project_id = started["projectId"]
        materials = api(session.get(f"{base}/api/projects/{project_id}/materials", headers=headers))
        if not any(item.get("materialTypeCode") == MATERIAL_CODE for item in materials):
            api(session.post(f"{base}/api/projects/{project_id}/materials/{MATERIAL_CODE}/versions",
                             headers=headers, files={"file": ("startup-retained.txt", CONTENT, "text/plain")}))
        baseline = {"userId": registered["user"]["userId"], "projectId": project_id,
                    "contentSha256": hashlib.sha256(CONTENT).hexdigest()}
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(json.dumps(baseline, indent=2), encoding="utf-8")
    else:
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    retained_user = api(session.post(base + "/api/auth/login", json={"username": USER, "password": user_password}))
    projects = api(session.get(base + "/api/projects", headers=headers))
    project = next((item for item in projects if item.get("projectCode") == PROJECT_CODE), None)
    if not project or project["projectId"] != baseline["projectId"] or retained_user["user"]["userId"] != baseline["userId"]:
        raise RuntimeError("User or project identity changed after restart")
    materials = api(session.get(f"{base}/api/projects/{project['projectId']}/materials", headers=headers))
    material = next((item for item in materials if item.get("materialTypeCode") == MATERIAL_CODE), None)
    if not material:
        raise RuntimeError("Retained material is missing")
    download = session.get(f"{base}/api/material-versions/{material['materialVersionId']}/download", headers=headers)
    if download.status_code != 200 or hashlib.sha256(download.content).hexdigest() != baseline["contentSha256"]:
        raise RuntimeError("Retained file content or SHA-256 changed")
    result = {**baseline, "materialVersionId": material["materialVersionId"],
              "fileHash": material.get("fileHash"), "download": "passed", "proxy": base}
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
