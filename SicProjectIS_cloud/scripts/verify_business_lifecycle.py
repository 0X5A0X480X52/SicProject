"""Exercise the public frontend HTTP contract; never retries a write request."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

import requests

ROOT = Path(__file__).resolve().parents[1]
FORM_KEYS = {"APPLICATION_DRAFT": "applicationDraft", "CONTRACT_DRAFT": "contractDraft",
             "ACCEPTANCE_DRAFT": "acceptanceDraft", "NOTICE": "notices", "CHECK_ITEM": "checkItems",
             "EXTERNAL_RESULT": "externalResults", "SEAL": "sealRecords", "SUBMISSION": "submissionRecords",
             "ARCHIVE": "archiveRecords", "PUBLICITY": "publicities", "FINANCIAL_SETTLEMENT": "financialSettlements"}


class BusinessFailure(RuntimeError):
    pass


def require(condition: Any, message: str) -> None:
    if not condition:
        raise BusinessFailure(message)


def form_payload(kind: str, code: str, project: int, module: int, run_id: str) -> dict:
    """Matches useNodeFormHelpers.buildRequestFromFlatForm's public nesting."""
    remark = f"E2E {run_id} {code}"
    payload = {"projectId": project, "moduleInstanceId": module, "remark": remark}
    if kind == "APPLICATION_DRAFT":
        payload["applicationDraft"] = {"application": {"applicationTitle": remark, "isLimitedProject": True,
            "applicationSummary": remark}, "extension": {"requestedAmount": 1000}, "detail": {"researchContent": remark}}
    elif kind == "CONTRACT_DRAFT":
        payload["contractDraft"] = {"contract": {"contractNo": f"E2E-{run_id}", "contractName": remark,
            "contractAmount": 1000, "partyA": "E2E authority", "partyB": "E2E school"}, "extension": {"remark": remark}}
    elif kind == "ACCEPTANCE_DRAFT":
        payload["acceptanceDraft"] = {"acceptance": {"certificateNo": f"E2E-{run_id}", "acceptanceConclusion": "APPROVED",
            "acceptanceType": "PROVINCIAL", "completionRate": 100, "acceptanceBatchNo": run_id}, "extension": {"remark": remark}}
    else:
        record = {"moduleInstanceId": module, "remark": remark}
        mapping = {
            "CHECK_ITEM": ("runtimeRecord", "checkItem", {"itemCode": code, "itemName": remark, "itemType": "REVIEW_RESULT",
                "itemValue": "true", "itemResult": "APPROVED", "required": True, "passed": True, "sortNo": 1}),
            "EXTERNAL_RESULT": ("runtimeRecord", "externalResult", {"externalUnit": "E2E authority",
                "externalResult": "APPROVED", "approvalNo": run_id}),
            "SEAL": ("runtimeRecord", "sealRecord", {"sealMatter": remark, "sealStatus": "COMPLETED", "copies": 1}),
            "SUBMISSION": ("runtimeRecord", "submissionRecord", {"submissionNo": run_id, "receiptNo": run_id}),
            "ARCHIVE": ("runtimeRecord", "archiveRecord", {"archiveNo": run_id, "archiveLocation": "E2E"}),
            "PUBLICITY": ("projectRecord", "publicity", {"publicityTitle": remark, "publicityScope": "SCHOOL",
                "hasObjection": False, "publicityResult": "APPROVED"}),
            "FINANCIAL_SETTLEMENT": ("projectRecord", "financialSettlement", {"receivedAmount": 1000,
                "spentAmount": 800, "settlementResult": "APPROVED", "financeReviewComment": remark}),
        }
        if kind in mapping:
            parent, child, values = mapping[kind]
            record.update(values)
            payload[parent] = {child: record}
    return payload


class LifecycleRunner:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.case = json.loads(args.case.read_text(encoding="utf-8"))
        self.run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + secrets.token_hex(3)
        self.base = args.gateway_base_url.rstrip("/")
        self.sessions: dict[str, requests.Session] = {}
        self.tokens: dict[str, str] = {}
        self.accounts: dict[str, dict] = {}
        self.password = secrets.token_urlsafe(24)
        self.secrets = [self.password]
        self.project = 0
        self.modules: dict[str, int] = {}
        self.materials: dict[str, int] = {}
        self.batches: dict[str, dict] = {}
        self.last_request = 0.0
        self.report = {"schemaVersion": 1, "caseId": self.case["caseId"], "runId": self.run_id,
            "startedAt": datetime.now(timezone.utc).isoformat(), "gateway": self.base, "overallPassed": False,
            "workflowTraversalPassed": False, "businessDataPassed": False, "requests": [], "checks": [],
            "fixtures": {}, "error": None}

    def redact(self, value: Any) -> str:
        text = str(value)
        for secret in sorted(self.secrets, key=len, reverse=True):
            if secret:
                text = text.replace(secret, "<redacted>")
        return text

    def safe_report(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {key: self.safe_report(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self.safe_report(item) for item in value]
        return self.redact(value) if isinstance(value, str) else value

    def request(self, actor: str, method: str, path: str, *, body: Any = None,
                files: Any = None, status: int | tuple[int, ...] = 200, raw: bool = False, token: str | None = None) -> Any:
        require(path.startswith("/api/"), "Business test may only call public /api endpoints")
        # Leave headroom below the default 40 requests/s/IP Gateway limiter.
        delay = self.args.request_interval - (time.monotonic() - self.last_request)
        if delay > 0:
            time.sleep(delay)
        self.last_request = time.monotonic()
        session = self.sessions.setdefault(actor, requests.Session())
        bearer = self.tokens.get(actor, "") if token is None else token
        headers = {"Accept": "application/json"}
        if bearer:
            headers["Authorization"] = f"Bearer {bearer}"
        started = time.monotonic()
        accepted = (status,) if isinstance(status,int) else status
        entry = {"actor": actor, "method": method, "path": path, "status": None, "traceId": None,
            "expectedStatuses": list(accepted)}
        self.report["requests"].append(entry)
        try:
            response = session.request(method, self.base + path, headers=headers,
                json=body if files is None else None, files=files, timeout=self.args.request_timeout)
            entry.update(status=response.status_code, traceId=response.headers.get("X-Trace-Id"))
            if response.status_code not in accepted:
                try:
                    error = response.json()
                    entry["error"] = self.redact(error.get("message", error.get("error", "")))
                except ValueError:
                    entry["error"] = "Non-JSON error response"
            require(response.status_code in accepted, f"{method} {path}: expected {accepted}, got {response.status_code}")
            if raw:
                return response.content
            payload = response.json()
            require(payload.get("success") == (response.status_code < 400), f"{method} {path}: invalid API success wrapper")
            return payload.get("data")
        except requests.RequestException as exc:
            # A timed-out POST may have committed. Preserve the scene; do not replay.
            raise BusinessFailure(f"{method} {path}: transport failure ({type(exc).__name__}); write was not retried") from exc
        finally:
            entry["durationMs"] = round((time.monotonic() - started) * 1000)

    def check(self, name: str, action: Callable[[], Any]) -> Any:
        entry = {"name": name, "passed": False}
        self.report["checks"].append(entry)
        try:
            result = action()
            entry["passed"] = True
            print(f"PASS {name}", flush=True)
            return result
        except Exception as exc:
            entry["error"] = self.redact(exc)
            raise

    def poll(self, name: str, read: Callable[[], Any], predicate: Callable[[Any], bool]) -> Any:
        deadline = time.monotonic() + self.args.poll_timeout
        while True:
            data = read()
            if predicate(data):
                return data
            require(time.monotonic() < deadline, f"Timed out waiting for {name}")
            time.sleep(self.args.poll_interval)

    def login(self, actor: str) -> None:
        account = self.accounts[actor]
        result = self.request(actor, "POST", "/api/auth/login", body={"username": account["username"],
            "password": account["password"]}, token="")
        self.tokens[actor] = result["token"]
        self.secrets.append(result["token"])
        account["userId"] = result["user"]["userId"]

    def bootstrap(self) -> None:
        username = os.environ.get("SIC_E2E_ADMIN_USERNAME", "")
        password = os.environ.get("SIC_E2E_ADMIN_PASSWORD", "")
        require(username and password, "SIC_E2E_ADMIN_USERNAME/PASSWORD are required")
        self.secrets.extend([username, password])
        self.accounts["administrator"] = {"username": username, "password": password}
        self.login("administrator")
        me = self.request("administrator", "GET", "/api/auth/me")
        require("SYSTEM_ADMIN" in me["roleCodes"], "Administrator lacks SYSTEM_ADMIN")
        ui_cases = getattr(self.args, "ui_regression_cases", False)
        if ui_cases:
            self.check("Chinese seed labels and initial department page", self.ui_initial_department_view)
        department = self.request("administrator", "POST", "/api/admin/departments", body={
            "deptCode": "" if ui_cases else "E2E_" + self.run_id,
            "deptName": ("自动化测试部门 " if ui_cases else "E2E ") + self.run_id,
            "parentDeptId": None, "enabled": True})
        if ui_cases:
            require(department["deptCode"].startswith("DEPT_"), "Blank department code was not generated")
        self.dept_id = department["deptId"]
        for spec in self.case["accounts"]:
            actor = spec["alias"]
            username = f"e2e_{self.run_id}_{actor}"
            self.secrets.append(username)
            self.accounts[actor] = {"username": username, "password": self.password}
            registered = self.request(actor, "POST", "/api/auth/register", token="", body={
                "username": username, "password": self.password, "realName": "E2E " + actor,
                "deptId": None if ui_cases else self.dept_id, "phone": "", "email": actor + "@example.invalid"})
            self.accounts[actor]["userId"] = registered["user"]["userId"]
            old_token = registered["token"]
            self.secrets.append(old_token)
            roles = [] if getattr(self.args, "iteration_regression_cases", False) and actor == "finance" else spec["roleCodesBeforeQualification"]
            self.accounts[actor]["initialRoles"] = roles
            self.request("administrator", "PUT", f"/api/admin/users/{registered['user']['userId']}/roles",
                body={"roleCodes": roles})
            self.request(actor, "GET", "/api/auth/me", token=old_token, status=401)
            self.login(actor)
            current = self.request(actor, "GET", "/api/auth/me")
            require(set(current["roleCodes"]) == set(roles), f"{actor}: roles differ")
        if ui_cases:
            self.check("Departmentless applicant rejected before project creation", self.ui_departmentless_project)
            for spec in self.case["accounts"]:
                actor = spec["alias"]
                self.ui_assign_department(actor, self.accounts[actor]["initialRoles"])
            view = self.request("administrator", "GET", f"/api/admin/departments/members?deptId={self.dept_id}")
            require(len(view["members"]) == len(self.case["accounts"]), "Department assignments are missing")
            self.request("department", "GET", "/api/admin/departments/members?deptId=1", status=403)
        for actor in self.case["qualification"]["applicants"]:
            application = self.request(actor, "POST", "/api/expert-qualification/applications", body={
                "specialty": "科研项目评审", "professionalTitle": "副教授", "applicationReason": "E2E " + self.run_id})
            require(application["status"] == "PENDING_DEPT_REVIEW", "Qualification initial status differs")
            aid = application["applicationId"]
            for reviewer, route, expected in [("department", "dept-review", "PENDING_SCIENCE_REVIEW"),
                                               ("science", "science-review", "APPROVED")]:
                result = self.request(reviewer, "POST", f"/api/admin/expert-qualification/applications/{aid}/{route}",
                    body={"approved": True, "opinion": "同意", "remark": "E2E " + self.run_id})
                require(result["status"] == expected, "Qualification review status differs")
            old_token = self.tokens[actor]
            self.request(actor, "GET", "/api/auth/me", token=old_token, status=401)
            self.login(actor)
            require("EXPERT" in self.request(actor, "GET", "/api/auth/me")["roleCodes"], "Expert role missing")
            qualification = self.request(actor, "GET", "/api/expert-qualification/my")
            require(qualification["expert"] and not qualification["hasActiveApplication"], "Qualification not active")
            if ui_cases:
                self.ui_assign_department(actor, ["EXPERT"])
        self.report["fixtures"]["departmentId"] = self.dept_id
        self.report["fixtures"]["actors"] = {a: {"userId": v["userId"]} for a, v in self.accounts.items()}

    def ui_initial_department_view(self) -> None:
        matrix = self.request("administrator", "GET", "/api/admin/roles/permissions")
        seed = (ROOT / "infra/mysql/seed/iam.sql").read_text(encoding="utf-8")
        labels = dict(re.findall(r"\('([^']+)', '([^']+)'", seed))
        for permission in matrix["permissions"]:
            code = permission["permissionCode"]
            if code in labels:
                require(permission["permissionName"] == labels[code], "Permission seed label differs: " + code)
        for role in matrix["roles"]:
            require(role["roleName"] == labels[role["roleCode"]], "Role seed label differs")
        view = self.request("administrator", "GET", "/api/admin/departments/members")
        require(view["canManageDepartments"] and not view["memberManagementDisabled"], "Initial department controls unavailable")
        system = next((d for d in view["departments"] if d["deptId"] == 1), None)
        require(system and system["deptName"] == "系统管理部门", "Default department label differs")

    def ui_departmentless_project(self) -> None:
        disabled = self.request("department", "GET", "/api/admin/departments/members")
        require(disabled["memberManagementDisabled"] and not disabled["members"] and not disabled["departments"],
                "Unassigned department administrator was not safely disabled")
        body = {k: v.replace("<runId>", self.run_id + "_UI") if isinstance(v, str) else v
                for k, v in self.case["projectStart"]["body"].items()}
        before = self.request("leader", "GET", "/api/projects")
        self.request("leader", "POST", "/api/project-applications/start", body=body, status=409)
        require(self.request("leader", "GET", "/api/projects") == before, "Rejected application created a project")

    def ui_definition_display(self, definition: int) -> None:
        require(self.request("leader", "GET", f"/api/workflow-definitions/{definition}/nodes"), "Definition nodes missing")
        xml = self.request("leader", "GET", f"/api/workflow-definitions/{definition}/bpmn")
        require("definitions" in xml["bpmnXml"], "Definition BPMN missing")
        require(self.request("leader", "GET", f"/api/workflow-definitions/{definition}/transitions"), "Definition transitions missing")

    def ui_assign_department(self, actor: str, roles: list[str]) -> None:
        old_token = self.tokens[actor]
        user = self.request("administrator", "PUT", f"/api/admin/departments/{self.dept_id}/members/{self.accounts[actor]['userId']}", body={})
        require(set(user["roleCodes"]) == set(roles), "Department-only assignment changed roles: " + actor)
        self.request(actor, "GET", "/api/auth/me", token=old_token, status=401)
        self.login(actor)
        me = self.request(actor, "GET", "/api/auth/me")
        require(me["deptId"] == self.dept_id and set(me["roleCodes"]) == set(roles), "Assigned identity differs: " + actor)

    def start_project(self) -> None:
        body = dict(self.case["projectStart"]["body"])
        for key, value in body.items():
            if isinstance(value, str):
                body[key] = value.replace("<runId>", self.run_id)
        started = self.request("leader", "POST", "/api/project-applications/start", body=body)
        self.project = started["projectId"]
        self.modules["APPLICATION"] = started["moduleInstanceId"]
        if getattr(self.args, "ui_regression_cases", False):
            module = self.request("leader", "GET", f"/api/module-instances/{started['moduleInstanceId']}/runtime-view")
            self.check("Assigned owner definition display", lambda: self.ui_definition_display(module["context"]["workflowDefinitionId"]))
            definition = module["context"]["workflowDefinitionId"]
            self.request("finance", "GET", f"/api/workflow-definitions/{definition}/nodes", status=403)
            self.request("leader", "GET", "/api/workflow-definitions", status=403)
            self.request("leader", "PUT", f"/api/projects/{self.project}/department", body={"deptId": self.dept_id, "reason": "Forbidden repair"}, status=403)
            self.request("administrator", "PUT", f"/api/projects/{self.project}/department", body={"deptId": self.dept_id, "reason": "Cannot overwrite existing department"}, status=409)
            self.report["uiRegressionCasesPassed"] = True
        grant = dict(self.case["financeGrant"]["body"], userId=self.accounts["finance"]["userId"])
        if not getattr(self.args, "iteration_regression_cases", False):
            self.request("administrator", "POST", f"/api/projects/{self.project}/finance-grants", body=grant)
        experts = {self.accounts[a]["userId"] for a in ("expert1", "expert2")}
        self.poll("qualified expert snapshots", lambda: self.request("science", "GET", f"/api/projects/{self.project}/authorization"),
            lambda data: experts <= {u["userId"] for u in data["users"] if "EXPERT" in u["roleCodes"]})
        self.report["fixtures"]["projectId"] = self.project

    def runtime(self, module: int, actor: str = "science") -> dict:
        return self.request(actor, "GET", f"/api/module-instances/{module}/runtime-view")

    def successor(self, module_type: str) -> int:
        rows = self.poll(module_type + " successor", lambda: self.request("science", "GET", "/api/workflow-workbench/items"),
            lambda rows: any(r["projectId"] == self.project and r["moduleType"] == module_type for r in rows))
        matches = [r for r in rows if r["projectId"] == self.project and r["moduleType"] == module_type]
        require(len(matches) == 1, "Successor module is not unique")
        return matches[0]["moduleInstanceId"]

    def material(self, code: str, actor: str) -> int:
        if code not in self.materials:
            with self.args.fixture_pdf.open("rb") as file:
                data = self.request(actor, "POST", f"/api/projects/{self.project}/materials/{code}/versions",
                    files={"file": ("blank-upload.pdf", file, "application/pdf")})
            self.materials[code] = data["context"]["materialVersionId"]
        return self.materials[code]

    def create_batch(self, module: int, step: dict, view: dict) -> None:
        actor = step["actor"]
        body = dict(self.case["expertBatch"]["createBodyDefaults"], moduleInstanceId=module,
            workflowNodeId=view["context"]["currentWorkflowNodeId"], reviewType=view["context"]["moduleType"],
            reviewTitle="E2E " + step["nodeId"], removeHighestLowest=False)
        batch = self.request(actor, "POST", "/api/expert-reviews/batches", body=body)
        batch_id = batch["batch"]["batchId"]
        for expert in self.case["expertBatch"]["assignBeforeEnteringReview"]:
            batch = self.request(actor, "POST", f"/api/expert-reviews/batches/{batch_id}/assignments", body={
                "expertUserId": self.accounts[expert]["userId"], "expertName": "E2E " + expert,
                "expertOrg": "E2E", "expertTitle": "副教授"})
        self.batches[f"{module}:{step['nextNodeId']}"] = batch

    def score_batch(self, module: int, step: dict) -> None:
        batch = self.batches[f"{module}:{step['nodeId']}"]
        batch_id = batch["batch"]["batchId"]
        assignments = {a["assignment"]["expertUserId"]: a["assignment"]["assignmentId"] for a in batch["assignments"]}
        require(len(assignments) == 2, "Review requires two distinct experts")
        for index, expert in enumerate(("expert1", "expert2")):
            score = self.case["expertBatch"]["scores"][expert]
            response = self.request(expert, "POST", f"/api/expert-reviews/assignments/{assignments[self.accounts[expert]['userId']]}/scores",
                body={"conflictOfInterest": False, "valid": True, "reviewResult": "APPROVED", "reviewComment": "E2E",
                    "scores": [{"itemCode": "OVERALL", "itemName": "综合评分", "weight": 1, "maxScore": 100,
                                "scoreValue": score, "comment": "E2E"}]})
            require(len(response["assignments"]) == 1 and response["assignments"][0]["assignment"]["expertUserId"] == self.accounts[expert]["userId"], "Score response exposed another expert")
            require("finalScore" not in response["batch"] and "highestScore" not in response["batch"], "Score response exposed batch scores")
            batch = self.request("administrator", "GET", f"/api/expert-reviews/batches/{batch_id}")
            if getattr(self.args, "iteration_regression_cases", False):
                personal = self.request(expert, "GET", f"/api/expert-reviews/my-assignments/{assignments[self.accounts[expert]['userId']]}")
                require(not personal["canReview"] and float(personal["totalScore"]) == score, "Submitted personal record is missing")
                other = "expert2" if expert == "expert1" else "expert1"
                self.request(expert, "GET", f"/api/expert-reviews/my-assignments/{assignments[self.accounts[other]['userId']]}", status=404)
            if index == 0:
                require(batch["batch"]["submittedExpertCount"] == 1 and batch["batch"]["status"] != "COMPLETED",
                    "One score prematurely completed review")
                require(self.runtime(module)["context"]["currentNodeId"] == step["nodeId"], "One score advanced workflow")
        result = batch["batch"]
        require(result["submittedExpertCount"] == 2 and result["validExpertCount"] == 2, "Review counts differ")
        require(float(result["finalScore"]) == 85 and result["finalResult"] == "RECOMMENDED", "Review result differs")
        self.poll("expert automatic transition", lambda: self.runtime(module),
            lambda v: v["context"]["currentNodeId"] == step["nextNodeId"])
        if getattr(self.args, "iteration_regression_cases", False):
            for expert in ("expert1", "expert2"):
                self.poll("expert access revoked", lambda a=expert: self.request(a, "GET", f"/api/projects/{self.project}/authorization", status=(200,403)),
                    lambda result: result is None)
                personal = self.request(expert, "GET", f"/api/expert-reviews/my-assignments/{assignments[self.accounts[expert]['userId']]}")
                require(personal["status"] == "COMPLETED" and not personal["canReview"], "Review history did not survive access revocation")

    def late_finance_configuration(self, module: int) -> None:
        view = self.runtime(module)
        context = view["context"]
        require(view["taskConfiguration"]["ready"] is False, "Unconfigured finance task not reported")
        path = "/api/workflow-workbench/query?view=TODO"
        require(not self.request("finance", "GET", path)["items"], "Unqualified user obtained finance todo")
        old = self.tokens["finance"]
        self.request("administrator", "PUT", f"/api/admin/users/{self.accounts['finance']['userId']}/roles", body={"roleCodes": ["FINANCE_ADMIN"]})
        self.request("finance", "GET", "/api/auth/me", token=old, status=401)
        self.login("finance")
        require(not self.request("finance", "GET", path)["items"], "Finance role without project grant obtained todo")
        self.poll("late finance user snapshot", lambda: self.request("administrator", "GET", f"/api/projects/{self.project}/authorization"),
            lambda data: any(u["userId"] == self.accounts["finance"]["userId"] and "FINANCE_ADMIN" in u["roleCodes"] for u in data["users"]))
        wrong = self.request("administrator", "POST", f"/api/projects/{self.project}/finance-grants", body={
            "userId": self.accounts["finance"]["userId"], "moduleType": context["moduleType"], "roundNo": context["currentRoundNo"] + 1,
            "taskNodeId": context["currentNodeId"], "reason": "E2E wrong round"})
        require(not self.request("finance", "GET", path)["items"], "Wrong round grant obtained todo")
        self.request("administrator", "POST", f"/api/projects/{self.project}/grants/{wrong['financeGrants'][-1]['projectRoleGrantId']}/revoke", body={"reason": "E2E cleanup wrong scope"})
        self.request("administrator", "POST", f"/api/projects/{self.project}/finance-grants", body=dict(self.case["financeGrant"]["body"], userId=self.accounts["finance"]["userId"]))
        rows = self.request("finance", "GET", path)["items"]
        require(any(row["moduleInstanceId"] == module and row["todo"] for row in rows), "Late finance grant did not expose the existing task")
        current = self.runtime(module, "finance")
        require(current["taskConfiguration"]["ready"] and current["context"]["currentSeq"] == context["currentSeq"], "Configuration recreated or advanced the task")
        self.report["lateFinanceConfigurationPassed"] = True

    def verify_form(self, module: int, step: dict, sent: dict) -> None:
        kind = step["form"]["dataKind"]
        if kind not in FORM_KEYS:
            return
        code = step["form"]["formCode"]
        data = self.request("science", "GET", f"/api/node-forms/{code}?projectId={self.project}&moduleInstanceId={module}")
        value = data[FORM_KEYS[kind]]
        require(value is not None and value != [] and value != {}, f"{code}: submitted form not readable")
        if kind.endswith("_DRAFT"):
            require(value == sent[FORM_KEYS[kind]], f"{code}: draft readback differs")
            business = self.request("science", "GET", f"/api/module-instances/{module}/business-data")
            require(all(business.get(k) == v for k, v in value.items()), f"{code}: business-data not persisted")
        else:
            parent = "projectRecord" if kind in ("PUBLICITY", "FINANCIAL_SETTLEMENT") else "runtimeRecord"
            if parent in sent:
                expected = next(iter(sent[parent].values()))
                require(any(all(record.get(k) == v for k, v in expected.items()) for record in value), f"{code}: history readback differs")
                if kind == "FINANCIAL_SETTLEMENT":
                    settlements = self.request("science", "GET", f"/api/projects/{self.project}/acceptance-financial-settlements")
                    require(any(row["moduleInstanceId"] == module and row["receivedAmount"] == 1000
                        and row["spentAmount"] == 800 and row["surplusAmount"] == 200
                        and row["executionRate"] == 80 for row in settlements), "Financial business record missing or totals differ")

    def traverse(self) -> None:
        for specification in self.case["modules"]:
            module_type = specification["moduleType"]
            module = self.modules.get(module_type) or self.successor(module_type)
            self.modules[module_type] = module
            for step in specification["steps"]:
                self.check(f"{module_type}/{step['nodeId']}", lambda s=step, m=module: self.step(m, s))
            final = self.runtime(module)
            require(final["context"]["currentState"] == specification["finalState"], "Module final state differs")
            require(not final["openTasks"], "Finished module retains open tasks")
            sequences = [record["seq"] for record in final["history"]]
            require(sequences == list(range(1, len(sequences) + 1)), "State sequence is not continuous")
        self.report["workflowTraversalPassed"] = True

    def step(self, module: int, step: dict) -> None:
        if step["action"] == "SCORE_BOTH_AND_WAIT_AUTO_TRANSITION":
            self.score_batch(module, step)
            return
        actor = step["actor"]
        if actor == "finance" and getattr(self.args, "iteration_regression_cases", False) and not self.report.get("lateFinanceConfigurationPassed"):
            self.check("Late finance role and project grant expose an existing task", lambda: self.late_finance_configuration(module))
        view = self.runtime(module, actor)
        context = view["context"]
        require(context["currentNodeId"] == step["nodeId"] and context["currentState"] == step["state"], "Unexpected current node/state")
        require(view["canOperate"], f"{actor} cannot operate {step['nodeId']}")
        require(any(t["eventType"] == step["eventType"] for t in view["availableTransitions"]), "Expected transition unavailable")
        if step["nodeId"].endswith("ExpertAssignTask"):
            self.create_batch(module, step, view)
        form = form_payload(step["form"]["dataKind"], step["form"]["formCode"], self.project, module, self.run_id)
        materials = [self.material(code, actor) for code in step["requiredMaterials"]]
        body = {"eventType": step["eventType"], "expectedSeq": context["currentSeq"], "result": step["result"],
            "remark": form["remark"], "materialVersionIds": materials, "formCode": step["form"]["formCode"], "nodeFormData": form}
        if self.args.negative_cases:
            path = f"/api/module-instances/{module}/transitions"
            self.request(actor,"POST",path,body=dict(body,expectedSeq=context["currentSeq"]-1),status=409)
            self.request("expert1","POST",path,body=body,status=403)
            if materials:
                self.request(actor,"POST",path,body=dict(body,materialVersionIds=[]),status=400)
            unchanged = self.runtime(module)["context"]
            require(unchanged["currentSeq"] == context["currentSeq"] and unchanged["currentNodeId"] == context["currentNodeId"],
                "Rejected requests changed the workflow")
        self.request(actor, "POST", f"/api/module-instances/{module}/transitions", body=body)
        self.poll("next node", lambda: self.runtime(module), lambda v: v["context"]["currentNodeId"] == step["nextNodeId"])
        self.verify_form(module, step, form)
        if self.args.negative_cases:
            before = self.runtime(module)["context"]
            self.request(actor,"POST",f"/api/module-instances/{module}/transitions",body=body,status=(403,409))
            after = self.runtime(module)["context"]
            require((after["currentSeq"],after["currentNodeId"]) == (before["currentSeq"],before["currentNodeId"]),
                "Duplicate submission changed the workflow")

    def final_assertions(self) -> None:
        require(len(self.batches) == 3 and len(self.materials) == 17, "Batch/material fixture counts differ")
        self.poll("project finished", lambda: self.request("science", "GET", f"/api/projects/{self.project}/authorization"),
            lambda p: p["project"]["lifecycleStage"] == "PROJECT_FINISHED")
        if getattr(self.args, "iteration_regression_cases", False):
            for actor in ("leader", "science"):
                history = self.request(actor, "GET", "/api/workflow-workbench/query?view=FINISHED&pageSize=100")["items"]
                own = [row for row in history if row["projectId"] == self.project]
                require(len(own) == 3 and all(row["finished"] and not row["canOperate"] for row in own), "Finished modules disappeared from history")
            personal = self.request("expert1", "GET", "/api/expert-reviews/my-assignments?status=COMPLETED")["items"]
            require(len([row for row in personal if row["projectId"] == self.project]) == 3, "Expert history is incomplete")
            require(not self.request("expert1", "GET", "/api/workflow-workbench/query?view=FINISHED")["items"], "Expert history restored full project access")
            self.report["iterationRegressionCasesPassed"] = True
        expected = hashlib.sha256(self.args.fixture_pdf.read_bytes()).hexdigest()
        for code, version in self.materials.items():
            content = self.request("science", "GET", f"/api/material-versions/{version}/download", raw=True)
            require(hashlib.sha256(content).hexdigest() == expected, f"{code}: download hash differs")
            if self.args.negative_cases:
                self.request("science","DELETE",f"/api/material-versions/{version}",status=409)
        self.report["businessDataPassed"] = True
        self.report["fixtures"].update(modules=self.modules, materialVersions=self.materials,
            batchIds=[b["batch"]["batchId"] for b in self.batches.values()], fixtureSha256=expected)

    def execute(self) -> int:
        try:
            require(self.args.fixture_pdf.is_file(), "PDF fixture missing")
            require(hashlib.sha256(self.args.fixture_pdf.read_bytes()).hexdigest() == self.case["uploadFixture"]["sha256"], "PDF fixture hash differs")
            self.check("accounts, roles and qualifications", self.bootstrap)
            self.check("project and finance authorization", self.start_project)
            self.traverse()
            self.check("final business assertions", self.final_assertions)
            require(self.report["workflowTraversalPassed"] and self.report["businessDataPassed"],
                    "Workflow or business assertions did not complete")
            for enabled, flag in ((self.args.ui_regression_cases, "uiRegressionCasesPassed"),
                                  (self.args.iteration_regression_cases, "iterationRegressionCasesPassed")):
                if enabled:
                    require(self.report.get(flag) is True, f"Required regression did not complete: {flag}")
            self.report["overallPassed"] = True
            return 0
        except Exception as exc:
            self.report["error"] = self.redact(exc)
            print("FAIL " + self.report["error"], file=sys.stderr)
            return 1
        finally:
            self.report["finishedAt"] = datetime.now(timezone.utc).isoformat()
            self.report["fixtures"].update(projectId=self.project, modules=self.modules, materialVersions=self.materials)
            self.report["fixtures"]["batchIds"] = [b["batch"]["batchId"] for b in self.batches.values()]
            path = self.args.report_path or ROOT / "verification" / "runs" / f"{self.run_id}.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(self.safe_report(self.report), ensure_ascii=False, indent=2), encoding="utf-8")
            for session in self.sessions.values():
                session.close()
            print(f"Report: {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-base-url", default=os.environ.get("SIC_E2E_GATEWAY_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--case", type=Path, default=ROOT / "verification/scenarios/full-business-lifecycle.case.json")
    parser.add_argument("--fixture-pdf", type=Path, default=ROOT / "output/pdf/blank-upload.pdf")
    parser.add_argument("--report-path", type=Path)
    parser.add_argument("--env-file", type=Path, help="Optional Compose credentials file; explicit SIC_E2E_* environment values take precedence")
    parser.add_argument("--negative-cases", action="store_true", help="Also verify stale sequences, missing files, unauthorized operators, duplicate submits and reserved-file deletion")
    parser.add_argument("--ui-regression-cases", action="store_true", help="Verify initial department UI, Chinese seeds, role-preserving assignment and owner BPMN/node reads")
    parser.add_argument("--iteration-regression-cases", action="store_true", help="Verify late finance assignment, finished workflow history and private expert history")
    parser.add_argument("--request-timeout", type=float, default=15)
    parser.add_argument("--poll-timeout", type=float, default=30)
    parser.add_argument("--poll-interval", type=float, default=.5)
    parser.add_argument("--request-interval", type=float, default=.05)
    args = parser.parse_args()
    if args.env_file:
        from startup_common import read_env
        values = read_env(args.env_file)
        for name in ("USERNAME", "PASSWORD"):
            os.environ.setdefault("SIC_E2E_ADMIN_" + name, values.get("BOOTSTRAP_ADMIN_" + name, ""))
    require(args.request_timeout > 0 and args.poll_timeout > 0 and args.poll_interval > 0
        and args.request_interval >= 0, "Timeouts and polling interval must be positive")
    return LifecycleRunner(args).execute()


if __name__ == "__main__":
    raise SystemExit(main())
